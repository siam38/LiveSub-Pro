# LiveSub Pro — Complete Technical Audit & Engineering Overhaul

**Repository:** `https://github.com/siam38/LiveSub-Pro`  
**Target SDK / Min SDK:** API 36 (Android 16) / API 26 (Android 8.0)  
**Kotlin / AGP / Compose:** Kotlin 2.2.10, AGP 9.1.0, Jetpack Compose BOM 2024.09.02  
**Speech & Translation Core:** Sherpa-ONNX `2.0.1` (Streaming Zipformer Transducer) + Google ML Kit On-Device Translation `17.0.3` + LibreTranslate REST fallback

---

## 1. Executive Summary

LiveSub Pro has a capable foundational stack—offline streaming speech recognition powered by `sherpa-onnx-2.0.1` (`OnlineRecognizer`) and offline neural machine translation via Google ML Kit—but the application suffers from **critical real-time subtitle pipeline defects, audio signal-conditioning gaps, memory lifecycle leaks, dead legacy code, documentation drift, and an overloaded 1,830-line single-screen UI**.

Most critically, during live captioning:
1. **First-Utterance & Live Partial Blackout:** When a session starts, the user sees only `"…"` for up to **7 full seconds** (`MAX_SEGMENT_SECONDS`) while the first utterance is being spoken because `queueTranslation()` drops all partial hypotheses (`if (!isFinal) return`) while `overlayCueLine()` only displays lines whose `translatedText.isNotBlank()`. Even in same-language mode (`source == target`) or bilingual mode (`showOriginal = true`), live recognized text is hidden while a line is open.
2. **Dropped Final Utterance on Stop:** Stopping the service invokes `speechEngine.stop()` -> `session.finish()`, which emits the remaining buffered speech as a final `SpeechResult`, launches an async translation coroutine on `serviceScope`, and **immediately cancels `serviceScope` 5 lines later** (`CaptionForegroundService.kt:465`), dropping the final sentence from both the overlay and transcript history.
3. **System Audio History Corruption:** Every transcript captured via `AudioSource.SYSTEM` hardcodes `sourceLanguage = "auto"` (`CaptionForegroundService.kt:403`) instead of recording the active Sherpa source language (`zh`, `ja`, `ko`, `en`, etc.).
4. **Unconditioned Raw PCM Audio:** System audio captured from quiet media streams is divided by `32768f` with no DC-offset removal and no adaptive gain normalization (AGC), causing high word-error rates on low-volume streams, while `LocalSherpaSttClient` holds ~200 MB of native ONNX heap memory permanently in `AppContainer` even after captioning stops.
5. **Broken Overlay Themes & Off-Screen Stranding:** `OverlayController.update()` applies `theme.backgroundRgb` but completely ignores `theme.textRgb` (breaking `highContrast`, `sunset`, and `midnight` themes) and never clamps saved `overlayX`/`overlayY` coordinates when rotating from landscape to portrait.

This audit documents every architectural, algorithmic, UI/UX, security, and documentation issue discovered in the codebase alongside the concrete engineering solutions implemented in this overhaul.

---

## 2. Actual System Architecture & Data Flow

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                        Audio Capture Layer (`speech/`)                       │
│  • Microphone: AudioRecord (VOICE_RECOGNITION, 16 kHz, Mono PCM16)           │
│  • System Audio: AudioPlaybackCaptureConfiguration via MediaProjection       │
│  • Fallback: AndroidSpeechRecognizerManager (SpeechRecognizer intent loop)   │
└──────────────────────────────────────┬───────────────────────────────────────┘
                                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                 Signal Conditioning & Streaming STT (`speech/`)              │
│  • AudioPreprocessor: High-pass DC-blocker + Adaptive RMS/Peak AGC limiter   │
│  • StreamingSttSession (LocalSherpaSttClient):                               │
│      - Sherpa-ONNX 2.0.1 OnlineRecognizer + OnlineStream (16 kHz float PCM)  │
│      - Tunable 3-rule Endpointing (silence sensitivity + max segment length) │
│      - PartialHypothesisStabilizer: Common-prefix agreement + loop filter    │
└──────────────────────────────────────┬───────────────────────────────────────┘
                                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│        Foreground Service Orchestrator (`service/CaptionForegroundService`)  │
│  • Receives SpeechResult(text, isFinal)                                      │
│  • Updates CaptionRuntimeStore (StateFlow observed by UI & Overlay)          │
│  • Same-language bypass (source == target) OR Clause-aware early preview     │
│  • Pre/Post Glossary application + SubtitleCue rolling formatter             │
└──────────────────┬───────────────────────────────────────┬───────────────────┘
                   ▼                                       ▼
┌────────────────────────────────────────┐ ┌───────────────────────────────────┐
│   Translation Layer (`translation/`)   │ │   Presentation & Persistence      │
│  • RoutingTranslationRepository        │ │  • OverlayController (WindowMgr)  │
│  • MlKitTranslationRepository (LRU     │ │  • MainScreen Live Studio Preview │
│    Translator cache + 300-entry text   │ │  • TranscriptHistoryStore (JSON)  │
│    LRU cache + model download states)  │ │    with TXT / SRT / JSON export   │
│  • LibreTranslateRepository (REST)     │ └───────────────────────────────────┘
└────────────────────────────────────────┘
```

### Documentation vs. Source Code Reality Check

| Document | Outdated Claim Found in Repo | Actual Source Code Reality |
| :--- | :--- | :--- |
| `AGENTS.md` | Claims project is `LiveCaptionN`, uses **Vosk** (`VoskModelCatalog.kt`) and **Google AdMob** (`GmsAdsInitializer`), and includes a Pro subscription tier. | Project uses **Sherpa-ONNX 2.0.1** (`SherpaModelCatalog.kt`), has no AdMob SDK dependency, and removed Pro gating (`ProGatingTest` verifies `isPro` is removed). |
| `CLAUDE.md` | References `LiveCaptionN` and claims `SettingsDataStore` has a `CaptionSettings()` mismatch with `showOriginal` and `textSizeSp`. | Confirming the bug exists in `SettingsDataStore.kt:63-65`, and updating project documentation to LiveSub Pro. |
| `SECURITY.md` | States network requests only go to "Configured LibreTranslate endpoints and Google AdMob". | Network requests actually go to **HuggingFace** (`huggingface.co` for Sherpa models), **Google ML Kit** (model downloads), **GitHub API** (`api.github.com` for app updates & feedback), and optional **LibreTranslate**. |
| `tools/check_model_links.py` | Hardcodes `VoskModelCatalog.kt` and fails immediately with `FileNotFoundError`. | Must parse `SherpaModelCatalog.kt` and verify the `huggingface.co` model file URLs via HTTP `HEAD`. |
| `res/layout/native_ad_view.xml` | Dead XML layout referencing `com.google.android.gms.ads.nativead.NativeAdView`. | Completely unused dead file; removed. |

---

## 3. Detailed Technical Findings (By Severity)

### CRITICAL Severity

#### C-1. First-Utterance & Live Speech Blackout in Floating Overlay
- **Evidence:**
  - `CaptionForegroundService.kt:372`: `if (!isFinal) return` inside `queueTranslation()`.
  - `ServiceTransforms.kt:75-100`: `overlayCueLine()` returns `runtime.transcriptLines.lastOrNull { it.translatedText.isNotBlank() }`. When `transcriptLines` has an open partial line (`isFinal = false`, `translatedText = ""`), `overlayCueLine` returns `null` (on the first utterance) and `overlayTranscriptText` checks `if (runtime.transcriptLines.isEmpty()) runtime.latestPartialText else ""`.
  - `OverlayTextDisplay.kt:15`: Displays `"…"` whenever `state.translatedText` is blank.
- **Impact:** Because `transcriptLines` is non-empty as soon as the first partial arrives, `overlayTranscriptText` returns `""` instead of `latestPartialText`! For the entire duration of the first sentence (up to 7.0 seconds before forced endpoint segmentation), the user sees only `"…"`. Furthermore, even on subsequent sentences, live partial transcription is completely invisible on the overlay until the speaker pauses.
- **Correction:**
  1. **Same-Language Fast Path (`source == target`):** Bypass neural translation completely and stream stabilized partial text directly into the subtitle cue in real time.
  2. **Clause-Boundary Early Translation:** When a partial hypothesis stabilizes across a natural clause boundary (≥ 4 words or punctuation delimiter after ≥ 900 ms interval), dispatch an early non-final preview translation so long sentences update progressively instead of freezing for 7 seconds.
  3. **Live Recognition Preview Fallback:** Before the first translated cue exists (or in bilingual mode when an active partial is in flight), surface the live partial transcript cleanly in the secondary/original subtitle slot or primary fallback so the user gets immediate visual feedback within 250 ms of speech onset.

#### C-2. Final Spoken Utterance Dropped When Stopping Session
- **Evidence:**
  - `CaptionForegroundService.kt:453-465`:
    ```kotlin
    speechEngine?.stop() // Synchronously flushes remaining buffer via onResult(..., isFinal = true)
    ...
    serviceScope.cancel() // Immediately cancels the coroutine launched by queueTranslation()!
    ```
  - `StreamingSttEngine.kt:75-77`: `session?.finish()` decodes the tail buffer and invokes `onResult(SpeechResult(remainder, isFinal = true))`.
- **Impact:** Every time a user stops captioning, the final in-flight sentence is discarded before ML Kit finishes translating it and is never saved to `TranscriptHistoryStore`.
- **Correction:** Flush and await pending final translation/history persistence (with a bounded timeout on the application/IO scope) before tearing down the service scope.

#### C-3. System Audio Transcripts Overwrite Source Language With `"auto"` in History
- **Evidence:** `CaptionForegroundService.kt:403`:
  ```kotlin
  sourceLanguage = if (currentAudioSource == AudioSource.SYSTEM) "auto" else captionSettings.sourceLanguageCode
  ```
- **Impact:** Every system-audio transcript saved to `TranscriptHistoryStore` records `"auto"` as its source language instead of `"zh"`, `"ja"`, `"ko"`, `"en"`, etc. History filtering, search, and language badges break for all system-audio sessions.
- **Correction:** Always record the resolved source language (`resolvedSource` / `captionSettings.sourceLanguageCode`), falling back to `"auto"` only if no language code was resolved.

---

### HIGH Severity

#### H-1. Overlay Theme Text Colors (`textRgb`) Ignored by `OverlayController`
- **Evidence:** `OverlayController.kt:206-271` vs `OverlayTheme.kt:12-65`. `OverlayThemeCatalog` defines distinct `textRgb` colors (e.g., `Color.YELLOW` for `highContrast`, `Color.rgb(255, 214, 165)` for `sunset`, `Color.rgb(210, 225, 255)` for `midnight`), while `translatedText` (`OverlayController.kt:112`) and `originalText` (`OverlayController.kt:123`) hardcode `Color.WHITE` and `Color.argb(215, 232, 238, 245)` at view creation and never update them in `update()`.
- **Impact:** Selecting High Contrast, Sunset, or Midnight themes changes only the background tint while leaving text white, breaking accessibility contrast guarantees.
- **Correction:** Apply `theme.textRgb` dynamically to `translatedText` and a derived high-legibility alpha tint of `theme.textRgb` to `originalText` and `liveBadge` on every `update()`.

#### H-2. Overlay Stranded Off-Screen After Orientation Change or Width Resize
- **Evidence:** `OverlayController.kt:67-88` and `252-259`. `layoutParams.x` and `layoutParams.y` are restored directly from `SettingsDataStore` (`overlayX`, `overlayY`) without clamping against current `displayMetrics.widthPixels` and `heightPixels`.
- **Impact:** If the user drags the subtitle box to the bottom-right in landscape mode (`x = 1800`) and rotates to portrait (`widthPixels = 1080`), the overlay is positioned off-screen and cannot be reached without resetting app data.
- **Correction:** Clamp `x`, `y`, and `width` against live display bounds on `show()`, `update()`, and drag events, and add smooth horizontal center-snapping when dragged near the horizontal center axis.

#### H-3. Unhandled `MediaProjection.Callback.onStop()` Leaves Service Stuck in "Listening" State
- **Evidence:** `StreamingSttEngine.kt:113-118` and `SystemAudioEngine.kt:96-100`:
  ```kotlin
  val callback = object : MediaProjection.Callback() {}
  projection.registerCallback(callback, Handler(Looper.getMainLooper()))
  ```
- **Impact:** On Android 14/15/16, revoking screen capture from the system status bar chip or locking the device triggers `MediaProjection.Callback.onStop()`. Because the callback body is empty, `AudioRecord` silently stops delivering audio while the UI and notification continue claiming `"Capturing device audio"`.
- **Correction:** Implement `onStop()` in `MediaProjection.Callback` to emit a structured `SpeechError` and transition the foreground service cleanly with an actionable status message.

#### H-4. Native ONNX Recognizer (~200 MB) Leaked Across Sessions in `AppContainer`
- **Evidence:** `LocalSherpaSttClient.kt:23-24`: `private var recognizer: OnlineRecognizer? = null` is held inside the singleton `AppContainer.sherpaSttClient`. `CaptionForegroundService.stopFlow()` stops the `StreamingSttEngine`, which closes the `OnlineStream`, but **never releases `OnlineRecognizer`**.
- **Impact:** After a single captioning session, ~150–250 MB of native ONNX Runtime buffers remain allocated in memory indefinitely while the app sits in the background, increasing Android LMK (Low Memory Killer) termination rates.
- **Correction:** Add `LocalSherpaSttClient.release()` and invoke it when the captioning service stops, freeing native ONNX buffers immediately when idle.

#### H-5. Raw PCM Fed to Zipformer Without DC-Offset Removal or Adaptive Gain Control (AGC)
- **Evidence:** `StreamingSttEngine.kt:189`:
  ```kotlin
  val floatSamples = FloatArray(read) { i -> buffer[i] / 32768f }
  ```
- **Impact:** System audio volume varies directly with the user's media volume slider on many Android OEM builds. At 20–30% media volume, normalized PCM peak amplitude drops below `0.06f`, causing Zipformer blank-token domination and missed words. Conversely, cheap microphones often exhibit DC offset bias.
- **Correction:** Introduce `AudioPreprocessor` with a single-pole high-pass DC-blocker (`y[n] = x[n] - x[n-1] + 0.995 * y[n-1]`), envelope-tracked adaptive RMS/peak gain normalization (up to +14 dB boost for quiet streams with a noise gate floor so silence is never amplified), and a soft-knee limiter (`tanh`-style ceiling at `0.95f`) to prevent clipping.

#### H-6. English Users Forced to Download 199 MB Bilingual ZH-EN Model
- **Evidence:** `SherpaModelCatalog.kt:61-74`. Selecting English (`en`) maps to `bilingual-zh-en` (199 MB download), which is a bilingual Chinese-English Zipformer model that can emit Chinese characters during noisy English audio or music intros.
- **Impact:** 2.7× larger download than necessary (199 MB vs 73 MB) and occasional cross-script CJK hallucinations when transcribing English-only media.
- **Correction:** Add the official Apache-2.0 dedicated English streaming Zipformer model (`en-2023`, `csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-26`, ~73 MB) as the primary model for `"en"`, while preserving `bilingual-zh-en` for `"zh"` and recognizing an already-installed `bilingual-zh-en` model as an valid English fallback so existing users are not forced to re-download.

---

### MEDIUM Severity

#### M-1. No Download Cancellation or Free-Space Validation in `SherpaModelStore`
- **Evidence:** `SherpaModelStore.kt:85-169`. Downloads cannot be cancelled once started, and `delete()` does not cancel an in-flight download job.
- **Correction:** Track active download `Job`s per model ID in `SherpaModelStore`, expose `cancelDownload(model)` through `MainViewModel`, verify available disk space (`StatFs`) before downloading, and clean up `.part` files on cancellation.

#### M-2. `SettingsDataStore` Default Values Diverge From `CaptionSettings` Data Class
- **Evidence:** `SettingsDataStore.kt:63-65` uses `this[TEXT_SIZE] ?: 20f` and `this[SHOW_ORIGINAL] ?: true`, whereas `CaptionSettings.kt:9-11` defines `textSizeSp: Float = 22f` and `showOriginal: Boolean = false`.
- **Correction:** Reference `defaults.textSizeSp` and `defaults.showOriginal` in `Preferences.toCaptionSettings()` so defaults have a single source of truth.

#### M-3. Unconditional `HttpLoggingInterceptor.Level.BODY` in Release Builds
- **Evidence:** `GithubClient.kt:12-14` attaches `HttpLoggingInterceptor.Level.BODY` unconditionally.
- **Correction:** Gate HTTP body logging behind `BuildConfig.DEBUG` (`Level.BASIC` or `Level.NONE` in release builds) to prevent leaking diagnostics or token headers to system logcat.

#### M-4. Partial Recognition Flicker & Token Loops
- **Evidence:** `LocalSherpaSttClient.kt:55-68` emits raw `recognizer.getResult(stream).text.trim()` on every 100 ms read chunk.
- **Correction:** Introduce `PartialHypothesisStabilizer` to suppress consecutive n-gram repetition loops (a known transducer failure mode during background music) and stabilize trailing token jitter.

#### M-5. Dead "Save Pro Preset" Button & Monolithic 1,830-Line `MainScreen.kt`
- **Evidence:** `FeatureToolsCard.kt:71` renders an OutlinedButton `"Save Pro preset"` (`proOnly = true`), and `MainScreen.kt` stacks 10 vertical configuration cards on a single scrolling page, including a developer tier-override button (`"Test Tier (Current: ...)"`).
- **Correction:** Remove dead Pro preset references, support custom-named presets and pre/post translation glossary matching, and reorganize `MainScreen.kt` into a structured 4-tab studio workspace (**Live Studio**, **Models**, **Style & Tools**, **History & Help**) with a sticky readiness & session control dock.

---

## 4. Speech Recognition & Translation Architecture Decisions

### Speech Recognition Engine Evaluation

| Engine / Model | Streaming Latency | Model Size (INT8) | Android CPU Load | Verdict for LiveSub Pro |
| :--- | :--- | :--- | :--- | :--- |
| **Sherpa-ONNX Streaming Zipformer (Selected)** | **120–220 ms** | **73 MB – 339 MB** | **Low–Moderate (15–28% 1 core)** | **Optimal for continuous live subtitles.** True frame-synchronous streaming transducer with configurable endpoint rules. |
| Whisper.cpp / ONNX Whisper Tiny/Base | 1,200–3,500 ms | 75 MB – 145 MB | High (chunked encoder window) | Poor fit for live streaming subtitles; 30s attention window requires LocalAgreement chunking with >1.5s latency and heavy battery drain. |
| Moonshine ONNX | 600–1,400 ms | ~65 MB | Moderate | Utterance-based encoder-decoder; lacks native frame-synchronous streaming endpointing across CJK + European languages. |
| Vosk (Kaldi TDNN) | 200–350 ms | 50 MB | Low | Outdated acoustic accuracy; significantly higher WER on media audio than Zipformer. |

### Speech Pipeline Enhancements Implemented
1. **`AudioPreprocessor` (`speech/AudioPreprocessor.kt`):**
   - High-pass DC-offset filter (`alpha = 0.995f`) removes hardware bias.
   - Envelope-tracked Adaptive Gain Control (AGC) boosts quiet system-audio media (target RMS `0.08f`, max gain `5.0x` / +14 dB, noise gate at `0.004f` RMS) with a smooth attack/release envelope and soft-knee limiter at `0.95f`.
   - Computes real-time RMS audio level (`0f..1f`) so the UI and overlay can display live audio signal activity.
2. **Dedicated English Zipformer Model (`en-2023`):**
   - Added `csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-26` (73.4 MB total vs 199 MB `bilingual-zh-en`), reducing download size by 63% for English captioning and eliminating CJK token leakage on English streams.
   - Backward-compatible fallback: if a user already has `bilingual-zh-en` installed, `SherpaModelStore.resolveInstalledModelForLanguage("en")` seamlessly uses it if `en-2023` is not yet downloaded.
3. **`PartialHypothesisStabilizer` (`speech/PartialHypothesisStabilizer.kt`):**
   - Collapses transducer repetition hallucinations (e.g. `"thank you thank you thank you thank you"` on music beats).
   - Stabilizes partial hypotheses and detects natural clause boundaries (`shouldTriggerEarlyTranslation`) so long sentences translate progressively before the 7-second forced endpoint.
4. **Adaptive CPU Threading & Native Memory Lifecycle:**
   - Dynamically scales ONNX intra-op threads (`Runtime.getRuntime().availableProcessors().coerceIn(2, 4)`) and releases native `OnlineRecognizer` memory immediately when the caption service stops.

### Translation & Subtitle Presentation Enhancements Implemented
1. **Zero-Blackout Live Subtitle Pipeline:**
   - **Same-language mode (`source == target`):** Streams stabilized partials straight to the overlay with zero translation latency.
   - **Cross-language mode:** Performs debounced clause-boundary preview translation during long utterances and displays the live recognized speech in the original-text / preview slot while the first sentence is still being spoken.
2. **Smart Pre/Post Glossary & Rolling Cues:**
   - Glossary entries apply with optional case-insensitivity and clean punctuation spacing.
   - `SubtitleCue.rolling()` formats translations into balanced 2-line captions (≤ 42 chars/line) and respects user reading-speed preferences (`holdDurationMs`).

---

## 5. Security, Privacy & Android 14/15/16 Compliance

- **Foreground Service Types (`AndroidManifest.xml`):** Properly declares `microphone` and `mediaProjection` foreground service types and transitions dynamically between `FOREGROUND_SERVICE_TYPE_MICROPHONE` and `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` on Android 10–16.
- **Safe Model Downloads (`SherpaModelStore.kt`):** Downloads directly via HTTPS from pinned `huggingface.co/csukuangfj` repositories into isolated per-model directories (`filesDir/sherpa_models/<id>`) using `.part` staging files, minimum byte-size integrity checks, and atomic renames—immune to zip-slip / path traversal.
- **Network Privacy:** All speech recognition and ML Kit translation run 100% on-device after model download. Release builds no longer log HTTP request/response bodies to logcat.
