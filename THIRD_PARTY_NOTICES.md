# Third-party notices

This project bundles, builds against or downloads the following. Each keeps its own licence; check the linked source for the authoritative terms.

## Bundled in the repository and the app

| Item | Source | Licence | Notes |
|---|---|---|---|
| Quran text (Uthmani, v1.1) | [Tanzil Project](https://tanzil.net) | Creative Commons Attribution 3.0, with Tanzil's terms of use | Verbatim copy at `shared/src/commonMain/composeResources/files/quran-uthmani.txt`, copyright block included. The text may not be changed, and the source must be credited with a link to tanzil.net. |
| KFGQPC HAFS Uthmanic Script v0.18 | King Fahd Glorious Quran Printing Complex | KFGQPC end-user licence: free to use, copy and distribute; may not be sold or modified | Unmodified at `shared/src/commonMain/composeResources/font/kfgqpc_hafs.ttf`. Full text in [docs/licenses/KFGQPC-Hafs-LICENSE.txt](docs/licenses/KFGQPC-Hafs-LICENSE.txt). |

## Compiled into the Android app (not stored in this repository)

| Item | Source | Licence |
|---|---|---|
| llama.cpp, tag `b12360` | [ggml-org/llama.cpp](https://github.com/ggml-org/llama.cpp) | MIT |
| whisper.cpp, tag `v1.9.5` | [ggml-org/whisper.cpp](https://github.com/ggml-org/whisper.cpp) | MIT |
| Kotlin, kotlinx.coroutines, kotlinx.serialization | [JetBrains](https://github.com/JetBrains/kotlin) | Apache 2.0 |
| Compose Multiplatform | [JetBrains](https://github.com/JetBrains/compose-multiplatform) | Apache 2.0 |
| AndroidX libraries | [Android Open Source Project](https://developer.android.com/jetpack/androidx) | Apache 2.0 |

## Downloaded by the app when a feature is switched on

| Item | Source | Licence |
|---|---|---|
| Qwen2.5 1.5B Instruct, GGUF Q4_K_M (AI commands) | [Qwen/Qwen2.5-1.5B-Instruct-GGUF](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF) | Apache 2.0 |
| Whisper base, ggml q5_1 (Voice input) | [ggerganov/whisper.cpp](https://huggingface.co/ggerganov/whisper.cpp), from OpenAI Whisper | MIT |

The licences in the last two tables are stated as published by those projects at the time of writing; they were not re-verified file by file for this notice.
