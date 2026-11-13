# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

"Mazhar's Quran" (`fi.mazhar.quran.ai`): an Arabic-only Quran reader in Kotlin Multiplatform + Compose Multiplatform whose layout is changed by typed or spoken commands, using an on-device LLM and on-device speech recognition. Android is the only working target. `docs/plan.md` records the design as built, with measurements; keep it in step when behaviour changes.

## Commands

Gradle needs JDK 17+. If `java` is not on PATH, set `JAVA_HOME` to Android Studio's bundled JBR (e.g. `C:\Program Files\Android\Android Studio\jbr`). `local.properties` (git-ignored) must contain `sdk.dir`.

```bash
./gradlew :shared:jvmTest                                   # all unit tests; no emulator needed
./gradlew :shared:jvmTest --tests "*CommandInterpreterTest*"  # one test class
./gradlew :shared:jvmTest --tests "*ReducerTest.pinCreatesSectionAtTop*"  # one test
./gradlew :androidApp:assembleDebug                         # build the APK (compiles native code)
./gradlew :androidApp:installDebug                          # build and install on a device/emulator
./gradlew :androidApp:assembleRelease                       # what CI builds; signed with the debug key
./gradlew :stt-android:assembleDebugAndroidTest             # on-device speech test APK
```

There is no lint or formatter task configured.

Native prerequisites for anything that builds `:llm-android` or `:stt-android` (so also `:androidApp`): NDK `28.2.13676358`, CMake `3.22.1`, and the two source trees cloned into the git-ignored `.local/` folder at pinned tags. CMake fails with the clone command if they are missing:

```bash
git clone --depth 1 --branch b12360 https://github.com/ggml-org/llama.cpp.git .local/llama.cpp
git clone --depth 1 --branch v1.9.5 https://github.com/ggml-org/whisper.cpp.git .local/whisper.cpp
```

`:shared:jvmTest` does not need them.

Scoring a real model on the eval set (optional): run a llama.cpp `llama-server` with the GGUF model, then

```bash
LLAMA_SERVER_URL=http://127.0.0.1:8089 LLM_EVAL_NAME=some-name ./gradlew :shared:jvmTest --tests "*LlmEvalTest*" --rerun
```

The report is written to `shared/build/llm-eval-<name>.txt`. Without `LLAMA_SERVER_URL` that test silently does nothing.

## Architecture

### The core rule

The LLM never generates UI and never touches Quran text. The UI is a pure function of `LayoutState`, and the only way to change it is a typed `Action` passed through `reduce()` (`shared/.../layout/`). Buttons, typed commands and voice all go through the same `dispatch` in `ui/App.kt`. Adding a capability means: add an `Action`, handle it in `Reducer.kt`, describe it in `CommandInterpreter.describe`, and (if it should be speakable) teach both the rules and the model about it.

### Command pipeline (`shared/.../voice/`)

```
mic → WhisperModel → CommandInterpreter → Action → reduce → LayoutState → Compose
```

`CommandInterpreter.interpret` is deliberately layered:

1. `interpretWithRules` (keyword rules + `SurahMatcher`) runs first. If it finds a cue word and exact surah names, that answer is returned and the model is never called.
2. Otherwise the `LlmEngine` is asked, with output constrained by the GBNF grammar in `LlmCommand.GRAMMAR`. The model only classifies intent and copies surah names as heard; `SurahMatcher.resolve` turns names into numbers. Names the model did not copy from the utterance are discarded, and the pin section name is derived from the utterance, not the model.
3. If rules and model disagree, or a name matched only approximately, `Interpretation.confident` is false and the UI asks before applying.

Three things must stay in sync when the command vocabulary changes: the grammar and prompt in `LlmCommand.kt`, the mapping in `CommandInterpreter` (`fromLlm`, `sized`, the rule keywords), and the expectations in `commonTest/.../voice/EvalSet.kt`. `CommandInterpreterTest` enforces a regression floor (`RULE_FLOOR`) on the rules' eval score; raise it when the rules improve.

### Quran text integrity

- The text is the Tanzil Uthmani file at `shared/src/commonMain/composeResources/files/quran-uthmani.txt`, stored verbatim (its licence forbids changes). `QuranTextTest` pins its SHA-256 and `.gitattributes` marks it `-text`; never edit, reformat or re-encode it, and never type Quran text into source or tests.
- `QuranText.parse` returns null unless all 114 surahs have the ayah counts in `SurahCatalog`; the UI then shows a notice instead of text.
- The KFGQPC Hafs font encodes two marks differently from Tanzil. `ui/QuranFont.kt` (`KfgqpcHafs.glyphs`) remaps them at display time only (U+06DF→U+0652, U+0652→U+06E1), and draws verse numbers as Arabic-Indic digits, which this font renders as the ornament. Any other font would need this mapping removed or replaced.
- Long surahs are laid out as several paragraphs (`SurahDisplay.paragraphs`) for layout speed; ayahs are never split.

### Optional features and models (Android)

`Feature.AI` and `Feature.VOICE` are off by default; with both off there is no command bar. The shared UI talks to a `FeatureController` (`shared/.../features/Features.kt`); the Android implementation is `ModelFeatures` in `androidApp`, a process-wide singleton that:

- downloads model files with the system `DownloadManager` into the app's external files dir (`models/`), each pinned to an exact Hugging Face revision, size and SHA-256 (the `specs` map — update all three together when changing a model);
- only uses mobile data for the large AI download after explicit consent (`wouldUseMobileData` / `allowMobileData`);
- loads and unloads `LlamaEngine` and `WhisperModel`, and exposes them as `llm` (fails fast when off, which makes the interpreter fall back to rules) and `speechModel()`.

### Native modules

`llm-android` (llama.cpp) and `stt-android` (whisper.cpp) are separate modules because each statically links its own copy of ggml; both hide those symbols with `--exclude-libs,ALL`. They are always built with `CMAKE_BUILD_TYPE=Release`, even in debug app builds, because unoptimised inference is unusably slow.

- JNI entry points are named after the Kotlin package (`Java_fi_mazhar_quran_ai_llm_LlamaEngine_*`, `..._stt_WhisperModel_*`). Renaming a package or class means renaming them in the `.cpp` files too.
- Strings cross JNI as UTF-8 byte arrays, not `jstring`, to avoid modified-UTF-8 problems with model output.
- `llm_jni.cpp` keeps the tokens of the previous prompt and reuses the shared prefix in the KV cache, so after the one-off `warmUp()` only the user's sentence is evaluated. All native LLM calls are serialised on one dispatcher in `LlamaEngine`.
- `stt_jni.cpp` sets `audio_ctx` to 768 (half the default window). Tightening it to the clip length was faster but produced garbled output and 20–30 s retries; do not lower it without re-running `WhisperModelTest` on a device.

### Reader navigation

`LayoutState.openAyah` is the ayah to show and `jump` is a counter bumped on every `OpenSurah`/`GoToAyah`, so the scroll effect in `Ayahs` (`ui/App.kt`) re-runs even for a repeated request. Scrolling needs the paragraph's `TextLayoutResult` to find the ayah's line, which is why paragraph items have no top padding. `rejection()` in `Reducer.kt` supplies the user-facing reason when an action cannot be applied as asked; the reducer itself stays total. Reader position is deliberately not persisted.

### Versioning and release

The app version is `app` in `gradle/libs.versions.toml`; CI overrides it with `-PappVersionName` (from a `v*` tag) and `-PappVersionCode` (run number). `.github/workflows/release.yml` runs only when a `v*` tag is pushed (never on ordinary commits or PRs): it runs the unit tests, builds the release APK and publishes a GitHub Release. It clones llama.cpp and whisper.cpp at the tags in its `env` block: keep those in step with the tags named in the two native `build.gradle.kts`/`CMakeLists.txt` files. The About section in Settings (`ui/AppInfo.kt`) shows the version passed in from `MainActivity`.

### Platform notes

- `shared` targets Android (via `com.android.kotlin.multiplatform.library`), JVM (only so tests run on the host) and iOS (entry point and `UserDefaultsLayoutStorage` exist but have never been compiled; there is no Xcode project and no iOS `FeatureController`).
- Versions are in `gradle/libs.versions.toml` (Kotlin 2.4.20, AGP 9.1.1, Compose Multiplatform 1.12.1, Gradle 9.5.1). With AGP 9 the app and Android library modules use built-in Kotlin; Compose 1.12 requires `compileSdk` 37.
- The app namespace and application ID are both `fi.mazhar.quran.ai`; `MainActivity` lives in the `.android` subpackage, so the manifest refers to `.android.MainActivity`.
