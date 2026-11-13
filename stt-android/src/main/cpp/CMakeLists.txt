cmake_minimum_required(VERSION 3.22.1)
project(quranstt LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT EXISTS "${WHISPER_CPP_DIR}/CMakeLists.txt")
    message(FATAL_ERROR
        "whisper.cpp not found at '${WHISPER_CPP_DIR}'. Clone it once with:\n"
        "  git clone --depth 1 --branch v1.9.5 https://github.com/ggml-org/whisper.cpp.git .local/whisper.cpp")
endif()

# Only the core library, linked statically into one .so. CPU only.
set(BUILD_SHARED_LIBS        OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS      OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES   OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER     OFF CACHE BOOL "" FORCE)
set(WHISPER_ALL_WARNINGS     OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE              OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP              OFF CACHE BOOL "" FORCE)
set(GGML_CCACHE              OFF CACHE BOOL "" FORCE)

add_subdirectory("${WHISPER_CPP_DIR}" whisper-build EXCLUDE_FROM_ALL)

add_library(quranstt SHARED stt_jni.cpp)
target_link_libraries(quranstt PRIVATE whisper log)
# This library and libquranllm.so each carry their own copy of ggml:
# keep those symbols private so the two can never be mixed up at load time.
target_link_options(quranstt PRIVATE "-Wl,--exclude-libs,ALL")
