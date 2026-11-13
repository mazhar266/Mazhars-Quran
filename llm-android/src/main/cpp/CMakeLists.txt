cmake_minimum_required(VERSION 3.22.1)
project(quranllm LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT EXISTS "${LLAMA_CPP_DIR}/CMakeLists.txt")
    message(FATAL_ERROR
        "llama.cpp not found at '${LLAMA_CPP_DIR}'. Clone it once with:\n"
        "  git clone --depth 1 --branch b12360 https://github.com/ggml-org/llama.cpp.git .local/llama.cpp")
endif()

# Only the core library, linked statically into one .so. CPU only.
set(BUILD_SHARED_LIBS     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES  OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP       OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL         OFF CACHE BOOL "" FORCE)
set(LLAMA_ALL_WARNINGS    OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE           OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP           OFF CACHE BOOL "" FORCE)
set(GGML_CCACHE           OFF CACHE BOOL "" FORCE)

add_subdirectory("${LLAMA_CPP_DIR}" llama-build EXCLUDE_FROM_ALL)

add_library(quranllm SHARED llm_jni.cpp)
target_link_libraries(quranllm PRIVATE llama log)
# This library and libquranstt.so each carry their own copy of ggml:
# keep those symbols private so the two can never be mixed up at load time.
target_link_options(quranllm PRIVATE "-Wl,--exclude-libs,ALL")
