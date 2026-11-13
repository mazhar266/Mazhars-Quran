# Plan: voice-driven Quran reader (Kotlin Multiplatform)

A simple Quran app: Arabic surahs for reading, no translation. A small on-device LLM listens through the microphone and rearranges the UI at runtime, e.g. "I recite Yasin, Mulk and Waqiah daily" puts those surahs first in the list.

## The core design decision

The LLM never generates UI or touches Quran text. It only turns speech into a small set of **typed actions**, and the UI is rendered from a persisted **layout state** that those actions modify.

```
mic → speech-to-text → small LLM (JSON-constrained) → Action → reducer → LayoutState → Compose UI
```

That gives "UI changes at runtime" while guaranteeing that a hallucination can at worst reorder a list, never alter an ayah.

## Stack

| Layer | Choice | Why |
|---|---|---|
| UI | Compose Multiplatform (Android + iOS, shared) | One RTL-capable UI codebase |
| Quran text | Tanzil Uthmani text, bundled, with a checksum test | Verified text; licence requires verbatim use and attribution |
| Font | KFGQPC Uthmanic Hafs (or Amiri Quran) | Correct rendering of Uthmani marks |
| Storage | SQLDelight for text, DataStore for layout state | Both multiplatform |
| Speech-to-text | whisper.cpp (multilingual, small/base quantized) behind an `expect/actual` interface | Works offline, same behaviour on both platforms |
| LLM | llama.cpp with a ~1B instruct model in GGUF (Gemma 3 1B or Qwen2.5 1.5B), output constrained by grammar | Grammar constraint guarantees valid JSON actions |
| Model delivery | Downloaded on first launch (~1–1.5 GB total), not bundled | Store size limits; the reader works before the download finishes |

## Action schema (v1)

- `PinSurahs(ids, sectionName)` — "I recite Yasin, Mulk and Waqiah daily" creates a "Daily" section at the top
- `UnpinSurahs(ids)` / `ReorderSection(ids)`
- `OpenSurah(id)` — "open Kahf"
- `SetTextSize(level)` / `SetTheme(light|dark|sepia)`
- `ResetLayout`
- `NoOp` — anything unrecognised

Each applied action shows a brief "Pinned 3 surahs · Undo" snackbar, so a mishearing is one tap to reverse.

## The hard part: surah names

Speech recognition will mangle Arabic names inside English/Urdu/Bangla sentences ("yaseen", "ya sin", "wakia"). Two defences:

1. The LLM only classifies the intent and copies out the surah names **as heard**; it does not map them to numbers. (Changed from the original plan of putting all 114 surahs in the prompt: small models copy text far more reliably than they look things up, and the prompt stays short.)
2. A deterministic phonetic matcher with an alias table (`SurahMatcher`) turns those names into surah numbers. Approximate or unresolved matches ask the user to confirm.
3. The same matcher plus keyword rules is the no-LLM fallback. It scores 77/83 on the eval set in `shared/src/commonTest/.../voice/EvalSet.kt`; the rules were written with that set in view, so treat the number as a regression floor, not real-world accuracy.

## How a command is interpreted (as built)

1. Keyword rules + `SurahMatcher` run first. If they find a cue word and exact surah names, that answer is used and the model is not called.
2. Otherwise the model (Qwen2.5 1.5B Instruct, Q4_K_M GGUF, via llama.cpp tag `b12360`) classifies the intent under a GBNF grammar. Surah names it did not copy from the transcript are discarded.
3. If rules and model disagree, or a name only matched approximately, the app asks "Did you mean …?" before changing anything.

Eval set (83 transcripts): rules alone 77, model alone 72, combined 79, with the model called for 21 of 83. Re-run with `LlmEvalTest` (see its header comment).

Local setup that is not in the repository (all under the git-ignored `.local/`):

- `.local/llama.cpp` — `git clone --depth 1 --branch b12360 https://github.com/ggml-org/llama.cpp.git .local/llama.cpp` (needed to build `:llm-android`)
- `.local/models/qwen2.5-1.5b-instruct-q4_k_m.gguf` — from `Qwen/Qwen2.5-1.5B-Instruct-GGUF` on Hugging Face
- `.local/llama-bin` — llama.cpp Windows binaries, only for running the eval on a PC
- On a device, the app looks for a `.gguf` in its private `files/models/` folder; without one it uses the rules only. There is no in-app downloader yet.

## Voice input (as built)

Tap the mic, speak, tap again. Audio is recorded only between the two taps (15 s maximum), transcribed on-device by whisper.cpp (tag `v1.9.5`), and discarded. Silence is detected before the speech model runs.

Extra local setup, also under `.local/`:

- `.local/whisper.cpp` - `git clone --depth 1 --branch v1.9.5 https://github.com/ggml-org/whisper.cpp.git .local/whisper.cpp` (needed to build `:stt-android`)
- `.local/models/ggml-base-q5_1.bin` (57 MB) and `ggml-small-q5_1.bin` (181 MB) from `ggerganov/whisper.cpp` on Hugging Face
- `.local/stt-clips` - six commands spoken by Windows text-to-speech, for `WhisperModelTest` (see its header comment)
- On a device, the app uses any `ggml-*.bin` in its private `files/models/` folder; without one the mic button reports that voice is unavailable.

Measured on the x86_64 emulator with the synthetic clips: base transcribes a command in about 2.8 s and got 5 of 6 right (it wrote "Waqiah" as "Waichir"); small got 6 of 6 but takes about 11 s.

## Milestones

1. **Reader** — KMP project, bundled text, surah list, reading screen, RTL and font polish.
2. **Layout engine** — `LayoutState`, reducer, persistence, undo; drive it from a debug text box.
3. **LLM** — llama.cpp bindings (JNI on Android, cinterop on iOS), grammar-constrained actions, eval set of ~100 sample utterances.
4. **Voice** — mic capture, whisper.cpp, alias matcher, confirmation UI.
5. **Polish** — model download manager, low-RAM fallback (matcher only, no LLM), battery and latency tuning.

Milestone 3 is the riskiest: native bindings for two platforms plus getting a 1B model to be reliable. The eval set is what tells us whether the model is good enough or a larger one is needed.

## Assumed defaults

- **Platforms:** Android and iOS, Android first for development (iOS builds need a Mac).
- **Mic:** tap-to-talk, not always-listening. Always-on drains battery, needs a foreground service, and would constantly hear recitation itself and try to interpret it as commands.
- **Spoken language:** multilingual model, so English, Urdu, Bangla or Arabic commands all work; English-only would allow a smaller, faster model.
- **Fully offline:** no audio or text leaves the device.
