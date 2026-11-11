# CLAUDE.md — Engineering Context for LiveSub Pro

## Build & CI Workflow

- **Remote CI Pipeline:** `.github/workflows/build.yml` runs on `ubuntu-latest` with JDK 17 (`temurin`), downloads `sherpa-onnx-2.0.1.aar` into `app/libs/sherpa-onnx.aar`, runs `./gradlew testGithubDebugUnitTest`, and builds `./gradlew assembleGithubDebug`.
- **Product Flavors:** `github` (self-update checker enabled via GitHub Releases API) and `play` (Play Store compliant build with `GITHUB_SELF_UPDATE_ENABLED = false`).

## Key Architectural Invariants

1. **Settings Defaults (`CaptionSettings.kt` & `SettingsDataStore.kt`):**
   - `SettingsDataStore.toCaptionSettings()` must always fall back to `val defaults = CaptionSettings()` so default values (`textSizeSp = 22f`, `showOriginal = false`, `audioSource = AudioSource.SYSTEM`, `sttBackend = SttBackend.LOCAL_SHERPA`) have a single source of truth.
2. **Speech Model Catalog (`SherpaModelCatalog.kt`):**
   - `SherpaModelCatalog.LANGUAGES` defines the 11 base languages (`zh`, `en`, `ko`, `ja`, `fr`, `ru`, `yue`, `ar`, `id`, `th`, `vi`) supported by on-device streaming Sherpa models.
   - `OnlineModelConfig.modelType` must remain `""` (auto-detect from ONNX metadata) because hardcoding `"zipformer"` crashes on `zipformer2` models.
3. **Subtitle Pipeline (`ServiceTransforms.kt` & `CaptionForegroundService.kt`):**
   - Completed cues remain steady while the next utterance is being spoken (`overlayTranscriptText`).
   - During the opening utterance before any cue is finalized, `overlayDisplayOriginalText` and `overlayDisplayTranscriptText` surface live partial speech and clause-boundary preview translations so the user never experiences dead air.
   - Final utterances flushed on `stopFlow()` are translated and persisted on `persistenceScope` before `serviceScope` is cancelled, and `localSherpaClient.release()` frees native ONNX memory when the session stops.
