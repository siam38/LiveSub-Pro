# LiveSub Pro

**Live captions and translation for anything playing on your phone.**

Modified and redesigned by **SiAM**, based on LiveCaptionN by Charles Hartmann.

[![Latest release](https://img.shields.io/github/v/release/siam38/LiveSub-Pro?color=2e4bd7)](https://github.com/siam38/LiveSub-Pro/releases/latest)
[![License](https://img.shields.io/github/license/siam38/LiveSub-Pro?color=6366f1)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%2010%2B-1a1330)](#requirements)

Watch a drama, video or live stream in another language and get real subtitles, floating over the app you're using. Speech is recognized **on your device** with Sherpa-ONNX streaming models, translated on-device with Google ML Kit, and shown in a clean subtitle dock — no account, no subscription, no ads.

## Features

- **Real subtitles, not a transcript** — one cue at a time in a bottom dock, bold and readable, in translated-only or bilingual (original + translation) mode
- **Two audio sources** — microphone, or system audio (videos, dramas, streams) via screen-share capture
- **On-device speech recognition** — streaming Zipformer models via [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx); words appear as they are spoken, and everything works offline after setup
- **Models download in-app** — nothing is bundled, so the APK stays lean; download only the languages you use and delete them any time
- **On-device translation** — Google ML Kit (~59 languages, offline after a one-time model download), or your own LibreTranslate server if you prefer
- **Made for watching** — drag the subtitle dock anywhere, resize it from the corner, tap for controls; text size, opacity, themes and fonts included
- **Useful extras** — caption history, saved presets, a glossary for names and terms, and an in-app update checker
- **No garbage** — no Pro tier, no paywalls, no ads, no subscription screens. Everything is unlocked

## Speech languages

Speech models are downloaded in-app from HuggingFace (one download per model):

| Model | Languages | Size |
|---|---|---|
| Chinese + English (bilingual) | Chinese, English | ~190 MB |
| Chinese (2025) | Chinese | ~167 MB |
| Korean | Korean | ~132 MB |
| French | French | ~128 MB |
| Russian | Russian | ~144 MB |
| Cantonese + Chinese + English | Cantonese, Chinese, English | ~239 MB |
| Multilingual | Japanese, Arabic, Indonesian, Thai, Vietnamese, Russian | ~339 MB |

For other languages, the built-in Android recognizer and the remote Whisper option remain available from the engine settings.

## First run

1. **Permissions** — grant microphone and overlay ("display over other apps"). On Android 13+, if the overlay switch is blocked, the app shows the "Allow restricted settings" fix right on the setup screen.
2. **Languages** — pick the spoken language and the language to translate into.
3. **Speech model** — download the model for your language (required once). The translation model downloads by itself the first time you caption.

Then press **Start**, open your video, and read.

## How it works

```
Audio (mic / system) → Sherpa-ONNX streaming Zipformer → live text
                     → ML Kit translation → floating subtitle dock
```

Recognition runs in small chunks with endpoint detection, so a cue finalizes on a natural pause instead of piling up into a paragraph. Finals skip the translation debounce and a small cache keeps repeated lines instant.

## Requirements

- Android 10 (API 29) or newer, arm64 device
- ~200 MB free space per speech model you download

## Building

The APK is built by GitHub Actions (`.github/workflows/build.yml`) and published to [Releases](https://github.com/siam38/LiveSub-Pro/releases). The workflow fetches the sherpa-onnx runtime AAR at build time — no model or runtime binaries are committed to the repo.

Locally: `./gradlew :app:assembleGithubDebug` (JDK 17, Android SDK 36).

## Credits

LiveSub Pro is modified and redesigned by **SiAM** — the Sherpa engine and in-app model downloads, the new subtitle dock, the redesigned interface and setup flow, and the removal of the original paywall/ads.

Based on [LiveCaptionN / LiveTranscribe-Android](https://github.com/chartmann1590/LiveTranscribe-Android) by **Charles Hartmann (chartmann1590)**. Thank you for open-sourcing the original app.

Speech models come from the [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) project (k2-fsa) and its model contributors on HuggingFace.

## License

MIT — see [LICENSE](LICENSE). Original copyright: Charles Hartmann (chartmann1590).
