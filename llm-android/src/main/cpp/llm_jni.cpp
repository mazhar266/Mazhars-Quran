// JNI bridge between fi.mazhar.quran.ai.llm.LlamaEngine and llama.cpp (tag b12360).
//
// One model, one context, one sequence. Each call renders the chat template,
// reuses whatever prefix of the prompt is already in the context (the long
// system prompt is identical every time, so only the short user turn is
// evaluated), then generates greedily under a GBNF grammar.

#include <jni.h>
#include <android/log.h>

#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

#define TAG "QuranLlm"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

struct Engine {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    const llama_vocab *vocab = nullptr;
    std::vector<llama_token> cached; // tokens currently held in the context
    std::mutex mutex;
};

std::string toString(JNIEnv *env, jbyteArray bytes) {
    const jsize length = env->GetArrayLength(bytes);
    std::string out(static_cast<size_t>(length), '\0');
    if (length > 0) env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte *>(out.data()));
    return out;
}

jbyteArray toBytes(JNIEnv *env, const std::string &text) {
    jbyteArray out = env->NewByteArray(static_cast<jsize>(text.size()));
    if (out != nullptr && !text.empty()) {
        env->SetByteArrayRegion(out, 0, static_cast<jsize>(text.size()),
                                reinterpret_cast<const jbyte *>(text.data()));
    }
    return out;
}

void logCallback(ggml_log_level level, const char *text, void *) {
    if (level == GGML_LOG_LEVEL_ERROR) __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", text);
}

bool renderPrompt(const Engine &engine, const std::string &system, const std::string &user,
                  std::string &prompt) {
    const char *tmpl = llama_model_chat_template(engine.model, nullptr);
    const llama_chat_message messages[] = {{"system", system.c_str()}, {"user", user.c_str()}};
    prompt.resize(system.size() + user.size() + 512);
    int32_t written = llama_chat_apply_template(tmpl, messages, 2, true, prompt.data(),
                                                static_cast<int32_t>(prompt.size()));
    if (written > static_cast<int32_t>(prompt.size())) {
        prompt.resize(static_cast<size_t>(written));
        written = llama_chat_apply_template(tmpl, messages, 2, true, prompt.data(),
                                            static_cast<int32_t>(prompt.size()));
    }
    if (written < 0) return false;
    prompt.resize(static_cast<size_t>(written));
    return true;
}

bool tokenize(const Engine &engine, const std::string &text, std::vector<llama_token> &tokens) {
    tokens.resize(text.size() + 16);
    int32_t count = llama_tokenize(engine.vocab, text.data(), static_cast<int32_t>(text.size()),
                                   tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    if (count < 0) {
        tokens.resize(static_cast<size_t>(-count));
        count = llama_tokenize(engine.vocab, text.data(), static_cast<int32_t>(text.size()),
                               tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    }
    if (count < 0) return false;
    tokens.resize(static_cast<size_t>(count));
    return true;
}

std::string complete(Engine &engine, const std::string &system, const std::string &user,
                     const std::string &grammar, int maxTokens) {
    std::string prompt;
    std::vector<llama_token> tokens;
    if (!renderPrompt(engine, system, user, prompt) || !tokenize(engine, prompt, tokens) || tokens.empty()) {
        LOGE("could not build the prompt");
        return "";
    }
    const size_t contextSize = llama_n_ctx(engine.ctx);
    if (tokens.size() + static_cast<size_t>(maxTokens) > contextSize) {
        LOGE("prompt too long: %zu tokens", tokens.size());
        return "";
    }

    // Keep the shared prefix, drop the rest of the context. At least one
    // token must be evaluated so there are fresh logits to sample from.
    size_t common = 0;
    while (common < tokens.size() && common < engine.cached.size() && tokens[common] == engine.cached[common]) {
        common++;
    }
    if (common == tokens.size()) common--;
    llama_memory_t memory = llama_get_memory(engine.ctx);
    if (!llama_memory_seq_rm(memory, 0, static_cast<llama_pos>(common), -1)) {
        llama_memory_clear(memory, true);
        common = 0;
    }
    engine.cached.resize(common);

    const size_t batchSize = llama_n_batch(engine.ctx);
    for (size_t i = common; i < tokens.size(); i += batchSize) {
        const size_t count = std::min(batchSize, tokens.size() - i);
        if (llama_decode(engine.ctx, llama_batch_get_one(tokens.data() + i, static_cast<int32_t>(count))) != 0) {
            LOGE("llama_decode failed while reading the prompt");
            llama_memory_clear(memory, true);
            engine.cached.clear();
            return "";
        }
        engine.cached.insert(engine.cached.end(), tokens.begin() + static_cast<long>(i),
                             tokens.begin() + static_cast<long>(i + count));
    }

    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (!grammar.empty()) {
        llama_sampler *constrained = llama_sampler_init_grammar(engine.vocab, grammar.c_str(), "root");
        if (constrained == nullptr) {
            LOGE("grammar could not be parsed");
            llama_sampler_free(sampler);
            return "";
        }
        llama_sampler_chain_add(sampler, constrained);
    }
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    std::string output;
    for (int i = 0; i < maxTokens; i++) {
        llama_token token = llama_sampler_sample(sampler, engine.ctx, -1);
        if (llama_vocab_is_eog(engine.vocab, token)) break;
        char piece[256];
        const int32_t length = llama_token_to_piece(engine.vocab, token, piece, sizeof(piece), 0, false);
        if (length > 0) output.append(piece, static_cast<size_t>(length));
        if (llama_decode(engine.ctx, llama_batch_get_one(&token, 1)) != 0) {
            LOGE("llama_decode failed while generating");
            break;
        }
        engine.cached.push_back(token);
    }
    llama_sampler_free(sampler);
    LOGI("prompt %zu tokens (%zu reused), output %zu bytes", tokens.size(), common, output.size());
    return output;
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_fi_mazhar_quran_ai_llm_LlamaEngine_nativeLoad(JNIEnv *env, jclass, jbyteArray path, jint threads,
                                                  jint contextSize) {
    static std::once_flag once;
    std::call_once(once, [] {
        llama_log_set(logCallback, nullptr);
        llama_backend_init();
    });

    auto *engine = new Engine();
    llama_model_params modelParams = llama_model_default_params();
    modelParams.n_gpu_layers = 0;
    engine->model = llama_model_load_from_file(toString(env, path).c_str(), modelParams);
    if (engine->model == nullptr) {
        LOGE("model could not be loaded");
        delete engine;
        return 0;
    }
    llama_context_params contextParams = llama_context_default_params();
    contextParams.n_ctx = static_cast<uint32_t>(contextSize);
    contextParams.n_batch = 512;
    contextParams.n_threads = threads;
    contextParams.n_threads_batch = threads;
    engine->ctx = llama_init_from_model(engine->model, contextParams);
    if (engine->ctx == nullptr) {
        LOGE("context could not be created");
        llama_model_free(engine->model);
        delete engine;
        return 0;
    }
    engine->vocab = llama_model_get_vocab(engine->model);
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT jbyteArray JNICALL
Java_fi_mazhar_quran_ai_llm_LlamaEngine_nativeComplete(JNIEnv *env, jclass, jlong handle, jbyteArray system,
                                                      jbyteArray user, jbyteArray grammar, jint maxTokens) {
    auto *engine = reinterpret_cast<Engine *>(handle);
    if (engine == nullptr) return toBytes(env, "");
    std::lock_guard<std::mutex> lock(engine->mutex);
    return toBytes(env, complete(*engine, toString(env, system), toString(env, user),
                                 toString(env, grammar), maxTokens));
}

JNIEXPORT void JNICALL
Java_fi_mazhar_quran_ai_llm_LlamaEngine_nativeFree(JNIEnv *, jclass, jlong handle) {
    auto *engine = reinterpret_cast<Engine *>(handle);
    if (engine == nullptr) return;
    llama_free(engine->ctx);
    llama_model_free(engine->model);
    delete engine;
}

} // extern "C"
