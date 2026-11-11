# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| latest  | :white_check_mark: |
| older   | :x:                |

Only the most recent release receives security fixes. Update to the latest version from the [releases page](https://github.com/siam38/LiveSub-Pro/releases/latest).

## Reporting a Vulnerability

**Do not open a public issue for security vulnerabilities.**

Instead, please use GitHub's private security advisory feature:

1. Go to the [Security tab](https://github.com/siam38/LiveSub-Pro/security) of this repository.
2. Click **Report a vulnerability**.
3. Fill in the details and submit.

## Scope

The following are in scope for security reports:

- Vulnerabilities in the LiveSub Pro Android application code
- Issues with how audio, transcripts, models, or settings are stored or transmitted
- Permission handling or unintended data exposure
- Dependencies with known CVEs that affect this project

The following are **out of scope**:

- Vulnerabilities in upstream third-party libraries or model hosts (Sherpa-ONNX, HuggingFace, Google ML Kit, LibreTranslate) — report those to their respective maintainers
- Self-hosted servers configured by the user (Whisper ASR, LibreTranslate)

## Security & Privacy Architecture

LiveSub Pro is engineered for local-first privacy:

- **No accounts, no ads, no analytics** — the `github` flavor only queries the public GitHub Releases API to check for new APK versions (and the `play` flavor disables self-update checks entirely).
- **100% On-device speech & translation by default** — Sherpa-ONNX streaming recognition and Google ML Kit neural translation run locally on the device after model download.
- **Safe direct model downloads** — Sherpa-ONNX model files are fetched directly over HTTPS from pinned HuggingFace repositories into app-private storage (`filesDir/sherpa/<id>`) using `.part` staging files and exact byte-size verification, avoiding archive extraction path-traversal (`zip-slip`) risks.
- **Release log sanitization** — HTTP body logging for the feedback relay is strictly gated to `BuildConfig.DEBUG` (`Level.NONE` in release builds).
- **Minimal permissions** — `RECORD_AUDIO`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE` (`microphone` and `mediaProjection`), `POST_NOTIFICATIONS`, and `INTERNET`.
