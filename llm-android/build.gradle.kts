import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
}

// llama.cpp is not vendored. Clone the pinned tag once:
//   git clone --depth 1 --branch b12360 https://github.com/ggml-org/llama.cpp.git .local/llama.cpp
val llamaCppDir = rootDir.resolve(".local/llama.cpp").absolutePath.replace('\\', '/')

android {
    namespace = "fi.mazhar.quran.ai.llm"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    ndkVersion = "28.2.13676358"

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DLLAMA_CPP_DIR=$llamaCppDir",
                    // Always optimised: an unoptimised build is unusably slow for inference.
                    "-DCMAKE_BUILD_TYPE=Release",
                    "-DANDROID_STL=c++_static",
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.kotlinx.coroutines.core)
}
