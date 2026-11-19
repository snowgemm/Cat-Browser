# Contributing to Cat Browser

Thank you for helping improve Cat Browser.

Cat Browser is an Android application built with Kotlin, Jetpack Compose, Material 3 and Mozilla GeckoView. Keep changes focused on the Android product and its documented compatibility boundaries.

## Before changing code

1. Read [README.md](README.md), [Architecture](docs/ARCHITECTURE.md), and the relevant feature contract or test matrix.
2. Check the current GeckoView version in `app/build.gradle.kts` before relying on an embedder API.
3. Confirm that the proposed behavior is supported by the pinned GeckoView version and Android API level.
4. For security-sensitive changes, follow [SECURITY.md](SECURITY.md) and do not put credentials or private user data in commits, issues or logs.

## Code organization

- Keep Kotlin package paths aligned with their `package` declarations.
- Put feature code beside the feature it serves; use descriptive file names.
- Keep declarations that share one responsibility together. Avoid growing broad UI or controller files when a focused component is clearer.
- Prefer the official [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html).
- Keep visibility as narrow as practical and avoid exposing implementation details unnecessarily.
- Do not claim Firefox WebExtensions or Chrome extension compatibility unless the required APIs and behavior are verified.

## Local verification

Run the relevant checks from the repository root:

```bash
# Android JVM tests and debug APK build
./gradlew :app:testDebugUnitTest :app:assembleDebug

# APK validator tests and ABI packaging checks
python3 -m unittest discover -s scripts -p 'test_*.py'
python3 scripts/verify_abi_apks.py \
  --input app/build/outputs/apk/debug \
  --output dist/abi-apks \
  --variant debug

# Compile the production-like benchmark targets
./gradlew :app:assembleBenchmark :macrobenchmark:assembleBenchmark
```

The physical-device Macrobenchmark suite clears Cat Browser's app data when it prepares its repeatable tab scenario. Read [macrobenchmark/README.md](macrobenchmark/README.md) before running it; do not run it against data you need to keep.

## Pull requests and issue reports

For external contributions, open an issue first for substantial changes so scope and compatibility can be discussed. Keep a proposed change small and explain:

- The problem and expected behavior.
- The implementation approach and affected components.
- Tests and builds run, including any device and Android versions.
- Remaining limitations or unverified behavior.

Do not attach APK signing material, API keys, access tokens, cookies, private URLs or unredacted crash logs.

## Scope and licensing

The repository currently does not declare a license for Cat Browser's own source code. Do not assume that the license of GeckoView or another dependency grants permission to reuse Cat Browser code. See [Third-party notices](THIRD_PARTY_NOTICES.md).
