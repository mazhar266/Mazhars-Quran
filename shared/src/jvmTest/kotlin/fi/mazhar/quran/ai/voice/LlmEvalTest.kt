package fi.mazhar.quran.ai.voice

import fi.mazhar.quran.ai.layout.LayoutState
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test

/**
 * Scores a real model on [EvalSet], using exactly the prompt, grammar and
 * interpreter the app uses. Skipped unless a llama.cpp server is running:
 *
 *   llama-server -m model.gguf --port 8089
 *   LLAMA_SERVER_URL=http://127.0.0.1:8089 LLM_EVAL_NAME=qwen2.5-1.5b \
 *     ./gradlew :shared:jvmTest --tests "*LlmEvalTest*" --rerun
 *
 * The report is written to shared/build/llm-eval-<name>.txt.
 */
class LlmEvalTest {
    @Test
    fun scoreModelOnEvalSet() {
        val url = System.getenv("LLAMA_SERVER_URL") ?: return
        val name = System.getenv("LLM_EVAL_NAME") ?: "model"
        val engine = ServerEngine(url)
        val interpreter = CommandInterpreter(engine)
        val state = LayoutState()

        val lines = mutableListOf<String>()
        var passed = 0
        var needsConfirmation = 0
        for (case in EvalSet.cases) {
            engine.lastOutput = "(not called: the rules were sure)"
            val result = runBlocking { interpreter.interpret(case.utterance, state) }
            if (!result.confident) needsConfirmation++
            if (result.action == case.expected) {
                passed++
            } else {
                lines += "\"${case.utterance}\"\n    expected ${case.expected}\n    actual   ${result.action}\n    model    ${engine.lastOutput}"
            }
        }
        val millis = engine.totalMillis / EvalSet.cases.size
        val header = "$name: $passed/${EvalSet.cases.size} correct, $needsConfirmation would ask to confirm, " +
            "model called for ${engine.calls} of them, $millis ms per command on this machine, " +
            "${engine.failures} unusable outputs"
        File("build").mkdirs()
        File("build/llm-eval-$name.txt").writeText((listOf(header, "") + lines).joinToString("\n"))
        println(header)
    }

    /** [LlmEngine] backed by llama-server's OpenAI-compatible endpoint. */
    private class ServerEngine(private val baseUrl: String) : LlmEngine {
        private val client = HttpClient.newHttpClient()
        var lastOutput = ""
        var totalMillis = 0L
        var failures = 0
        var calls = 0

        override suspend fun complete(system: String, user: String, grammar: String): String {
            val body = buildJsonObject {
                put("messages", buildJsonArray {
                    add(buildJsonObject { put("role", "system"); put("content", system) })
                    add(buildJsonObject { put("role", "user"); put("content", user) })
                })
                put("grammar", grammar)
                put("temperature", 0)
                put("max_tokens", 120)
                put("cache_prompt", true)
            }
            val request = HttpRequest.newBuilder(URI("$baseUrl/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build()
            calls++
            val start = System.nanoTime()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            totalMillis += (System.nanoTime() - start) / 1_000_000
            check(response.statusCode() == 200) { "Server returned ${response.statusCode()}: ${response.body()}" }
            lastOutput = Json.parseToJsonElement(response.body()).jsonObject["choices"]!!.jsonArray[0]
                .jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
            if (LlmCommand.parse(lastOutput) == null) failures++
            return lastOutput
        }
    }
}
