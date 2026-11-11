# AGENTS.md — Developer & Agent Architecture Guide for LiveSub Pro

## Project Overview

**LiveSub Pro** (`com.charles.livecaptionn`) is a real-time Android speech-to-text, live translation, and floating subtitle overlay application targeting Android 16 (API 36, minSdk 26).

Key characteristics:
- **No ads, no paywalls, no Pro gating:** Every theme, font, language, and preset feature is unlocked.
- **On-device streaming ASR:** Powered by `sherpa-onnx-2.0.1` (`LocalSherpaSttClient`, `SherpaModelCatalog`, `SherpaModelStore`) with real-time PCM conditioning (`AudioPreprocessor`) and hypothesis stabilization (`PartialHypothesisStabilizer`).
- **On-device neural translation:** Powered by Google ML Kit (`MlKitTranslationRepository`, ~59 languages) with optional self-hosted `LibreTranslateRepository` fallback.
- **GitHub-First CI Build Pipeline:** All Gradle builds (`./gradlew testGithubDebugUnitTest`, `./gradlew assembleGithubDebug`) run remotely on GitHub Actions (`.github/workflows/build.yml`), which fetches `sherpa-onnx-2.0.1.aar` at build time.

---

## Package Structure (`app/src/main/java/com/charles/livecaptionn/`)

- `di/AppContainer.kt` — Manual dependency injection container holding singletons (`SettingsRepository`, `CaptionRuntimeStore`, `SherpaModelStore`, `LocalSherpaSttClient`, `RoutingTranslationRepository`, `TranscriptHistoryStore`, `FileCaptionProfileRepository`, `FileGlossaryRepository`, `UiLocalizationRepository`).
- `speech/` — Audio capture and speech recognition:
  - `AudioPreprocessor.kt` — High-pass DC-offset blocker, envelope-tracked Adaptive Gain Control (AGC), and soft-knee peak limiter for 16 kHz mono PCM16.
  - `PartialHypothesisStabilizer.kt` — Suppresses transducer n-gram/CJK repetition loops, stabilizes trailing partial flicker, and triggers debounced clause-boundary preview translations.
  - `SherpaModelCatalog.kt` & `SherpaModelStore.kt` — Defines and manages the 8 on-device Sherpa-ONNX models (`en-2023`, `zh-2025`, `bilingual-zh-en`, `ko-2024`, `fr-2023`, `ru-tone`, `yue-paraformer`, `multi-2025`) with HTTP Range resume, disk-space checks, and download cancellation.
  - `LocalSherpaSttClient.kt` — Manages `OnlineRecognizer` + `OnlineStream`, scales thread count to CPU cores, and exposes `release()` to free native ONNX memory when idle.
  - `StreamingSttEngine.kt` — Captures 16 kHz PCM from Microphone or System Audio (`AudioPlaybackCaptureConfiguration` via `MediaProjection`) and feeds `StreamingSttSession`.
  - `SystemAudioEngine.kt` & `AndroidSpeechRecognizerManager.kt` — Remote Whisper batch fallback and Android built-in `SpeechRecognizer` fallback.
- `translation/` — Translation engines and routing:
  - `MlKitTranslationRepository.kt` — Bounded LRU of ML Kit `Translator` clients + 300-entry text cache + Cantonese (`yue -> zh`) language tag normalization.
  - `LibreTranslateRepository.kt` & `LanguageCatalogStore.kt` — Self-hosted LibreTranslate REST client.
  - `RoutingTranslationRepository.kt` — Dispatches to ML Kit or LibreTranslate based on user settings.
- `service/` — Foreground service and state transforms:
  - `CaptionForegroundService.kt` — Orchestrates audio capture, recognition, pre/post glossary application, same-language fast path, clause-boundary early translation, overlay updates, and history persistence.
  - `ServiceTransforms.kt` — Pure functions tested by `ServiceTransformsTest` (`recordTranscriptResult`, `updateTranscriptTranslation`, `overlayTranscriptText`, `overlayOriginalText`, `overlayDisplayTranscriptText`, `overlayDisplayOriginalText`, `overlayActivePartialText`).
- `overlay/` — Floating window presentation:
  - `OverlayController.kt` — `WindowManager` floating subtitle dock with full `OverlayTheme` background + `textRgb` support, screen-bounds clamping across orientation changes, and horizontal center snapping.
  - `OverlayTextDisplay.kt`, `OverlayTheme.kt`, `OverlayUiState.kt`, `SubtitleCue.kt`.
- `ui/` — Jetpack Compose UI:
  - `MainScreen.kt` — 4-tab studio interface (`Live`, `Overlay`, `Models`, `Tools`) + `SherpaModelSheet`.
  - `OnboardingScreen.kt`, `HistoryScreen.kt`, `FeatureToolsCard.kt`, `TutorialScreen.kt`, `feedback/`, `l10n/`, `theme/Theme.kt`.

---

## Verification & Testing Guidelines

1. **Unit Tests:** Located in `app/src/test/java/com/charles/livecaptionn/`. Always keep `ServiceTransformsTest`, `OverlayTextDisplayTest`, `MainUiStateFilterTest`, `SherpaEndpointTuningTest`, `CaptionSettingsTest`, `UiStringCatalogTest`, `AudioPreprocessorTest`, `PartialHypothesisStabilizerTest`, and `SherpaModelCatalogTest` passing.
2. **Model Catalog Verification:** Run `python tools/check_model_links.py` whenever modifying `SherpaModelCatalog.kt`.
3. **Localization Catalog:** Any new user-facing string looked up via `t["..."]` or `t.format("...", ...)` should be compatible with `UiStringCatalog`.
