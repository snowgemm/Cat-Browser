# Cat Browser

**A native Android browser built with Kotlin, Jetpack Compose and Mozilla GeckoView.**

[![Android CI](https://github.com/snowgemm/Cat-Browser/actions/workflows/android-ci.yml/badge.svg?branch=main)](https://github.com/snowgemm/Cat-Browser/actions/workflows/android-ci.yml)
[![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Language](https://img.shields.io/badge/language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Browser engine](https://img.shields.io/badge/engine-Mozilla%20GeckoView-FF7139?logo=firefoxbrowser&logoColor=white)](https://mozilla.github.io/geckoview/)

Cat Browser is an independent Android browser application. Cat owns the browser UI, navigation, tabs, permissions, downloads, local data and extension integration; **GeckoView supplies the web engine**. Cat is not a Chromium browser, Firefox for Android, or a Mozilla product.

> **Project status:** Android development is active. CI validates source tests and APK packaging, but it does not replace installation, media playback, extension and long-duration testing on physical devices.

## Contents

- [Features](#features)
- [Download and install](#download-and-install)
- [Supported architectures](#supported-architectures)
- [Architecture](#architecture)
- [Build from source](#build-from-source)
- [Tests and performance benchmarks](#tests-and-performance-benchmarks)
- [Privacy and security](#privacy-and-security)
- [Known limitations](#known-limitations)
- [Project documentation](#project-documentation)
- [Third-party components and licensing](#third-party-components-and-licensing)
- [Reporting an issue](#reporting-an-issue)

## Features

### Browsing

- GeckoView Stable embedded as the browser engine; version is pinned in `app/build.gradle.kts`.
- Native Compose browser interface and a Cat New Tab dashboard.
- Up to 20 regular and private tabs, tab search, tab previews, drag reordering and recently closed regular tabs.
- Address and search input, local suggestions, adjacent-tab gestures, page loading feedback and find-in-page.
- Local bookmarks, recent history and downloads.
- HTTP(S) links can be opened from other Android applications.
- Configurable home page, search provider, theme and local New Tab wallpaper.
- Fullscreen media handling through GeckoView delegates.

### Privacy and permissions

- Separate regular and private GeckoSession configurations.
- Private tabs are excluded from persisted tab records, browsing history, bookmarks and persistent download history.
- GeckoView Tracking Protection controls and local aggregate privacy statistics.
- Website content/media permission prompts bridged to Android runtime permissions.
- Inactive Gecko sessions are marked inactive; tab previews are transient and released under Android memory pressure.

Private browsing limits what Cat stores locally. It does not make activity invisible to websites, internet providers or the network being used.

### Extensions

Cat targets **Firefox WebExtensions through GeckoView**. The current integration includes AMO discovery, signed XPI import, extension management, action buttons, popup sessions, options-page routing and selected tab/download bridges.

Compatibility is API-by-API and remains partial. Installing an add-on does not prove that every API it uses works in Cat. Chrome Web Store/CRX support and complete Firefox Desktop API parity are not provided.

See the [Android extension support contract](docs/ANDROID-EXTENSIONS.md) and [physical-device test matrix](docs/EXTENSION-TEST-MATRIX.md).

### Cat AI

The optional assistant uses a Supabase Edge Function and the Gemini Interactions API. Browser actions are restricted to a fixed allowlist and require in-app approval. Page contents and private-tab metadata are not automatically sent to the assistant.

User messages and approved tool results are transmitted to Google Gemini for processing. Review the [AI integration and data-handling notes](docs/AI-APP-CONTROL.md) before using the feature.

### Updates

Stable updates are checked by default; Beta updates are optional. Release builds are distributed as ABI-specific APKs through GitHub Releases. APKs downloaded by the in-app updater are removed from the app cache after Android confirms a successful package replacement.

## Download and install

Download a signed release from [GitHub Releases](https://github.com/snowgemm/Cat-Browser/releases). Select **one** APK matching the device architecture; do not install multiple ABI variants.

1. Open the [latest Stable release](https://github.com/snowgemm/Cat-Browser/releases/latest), or choose a Beta from the [pre-release list](https://github.com/snowgemm/Cat-Browser/releases?q=prerelease%3Atrue).
2. Download the APK for the device ABI listed below.
3. Compare the APK's SHA-256 hash with the matching entry in the release's `SHA256SUMS`.
4. Open the APK and complete Android's package installation prompt.

| APK suffix | Intended device |
|---|---|
| `arm64-v8a-release.apk` | Most modern 64-bit ARM Android phones and tablets |
| `armeabi-v7a-release.apk` | 32-bit ARM Android devices |
| `x86_64-release.apk` | x86-64 Android devices and compatible emulators |

The in-app updater downloads only the APK matching the first supported ABI reported by Android.

## Supported architectures

| ABI | Build | Notes |
|---|---:|---|
| `arm64-v8a` | Yes | Primary target for modern Android phones |
| `armeabi-v7a` | Yes | Legacy 32-bit ARM devices |
| `x86_64` | Yes | Compatible emulators and x86-64 Android devices |
| `x86` | No | The pinned GeckoView dependency does not provide the required engine libraries for this ABI |

Each APK must contain native libraries for exactly one ABI and the required GeckoView engine libraries: `libmozglue.so`, `libxul.so` and `libgkcodecs.so`. The packaging validator enforces these checks before artifacts are published.

Development builds are also uploaded by [Android CI](https://github.com/snowgemm/Cat-Browser/actions/workflows/android-ci.yml). They are debug-signed artifacts, retained for 14 days, and are not production releases.

## Architecture

The repository is an Android application with a separate instrumentation-test module for Macrobenchmark:

```text
app/
  src/main/
    java/io/github/snowgemm/catbrowser/
      ai/          Cat AI client and Supabase session handling
      browser/     GeckoView controller, sessions, downloads, extensions and URL rules
      ui/          Compose screens, browser shell, tab overview and shared browser chrome
      update/      Stable/Beta release updater and APK cleanup
    res/           Android resources and manifest
  src/test/        JVM unit tests

macrobenchmark/    Physical-device startup, frame and memory benchmarks
docs/              Architecture, extension contract, test matrix and product roadmap
scripts/           APK ABI validation and normalization
supabase/          Cat AI Edge Function and database migrations
ai-gateway/        Deprecated legacy gateway retained temporarily for rollback tests
.github/workflows/ Android CI and signed release automation
```

Key ownership boundaries:

- `CatBrowserApplication` owns the process-wide `GeckoRuntime`.
- `BrowserController` coordinates tabs, `GeckoSession` lifecycle, local browser state, permissions and privacy controls.
- `BrowserDownloadManager` and `BrowserExtensionsManager` isolate download and WebExtension integration.
- `BrowserScreen`, `BrowserMainScreen`, `BrowserChrome` and `TabsOverview` keep the Compose shell, main browser UI, reusable controls and tab overview separate.
- `macrobenchmark` measures application journeys; it does not change the production browser code.

For the detailed design and session lifecycle rules, see [Architecture](docs/ARCHITECTURE.md).

## Build from source

### Requirements

- JDK 17.
- Android SDK Platform 37.2.
- Android SDK Build-Tools 36.0.0.
- Gradle 9.6.0 (provided by the Gradle Wrapper).
- Node.js 22 for the deprecated legacy AI gateway's CI checks.

Open the project in Android Studio or build from a terminal.

### Clone and build

```bash
git clone https://github.com/snowgemm/Cat-Browser.git
cd Cat-Browser

./gradlew :app:testDebugUnitTest :app:assembleDebug
python3 -m unittest discover -s scripts -p 'test_*.py'

python3 scripts/verify_abi_apks.py \
  --input app/build/outputs/apk/debug \
  --output dist/abi-apks \
  --variant debug
```

The Gradle build creates separate debug APK outputs for the configured ABIs. The validator checks each APK archive, its ABI split and required GeckoView libraries, then copies normalized APKs and writes `SHA256SUMS`.

### Production release builds

Release APKs are built by the [Android Release workflow](.github/workflows/android-release.yml) from version tags:

- Stable: `vMAJOR.MINOR.PATCH`
- Beta: `vMAJOR.MINOR.PATCH-beta.N`

The workflow requires the release keystore and passwords to be configured as GitHub Actions secrets. Never commit signing material or put credentials in Gradle files. All ABI APKs are checked for valid signatures and a shared signing certificate before being uploaded to the GitHub Release.

## Tests and performance benchmarks

### CI checks

[Android CI](.github/workflows/android-ci.yml) runs:

- Legacy AI gateway JavaScript syntax and mocked request tests.
- Python unit tests for APK archive validation and output normalization.
- Android JVM unit tests.
- ABI-specific debug APK builds.
- Benchmark APK compilation.
- Native ABI and GeckoView library validation.
- APK signature verification and SHA-256 generation.
- Separate uploads for ARM64, ARMv7 and x86-64 debug APKs.

A successful CI run verifies compilation, tests and packaging. It does **not** certify browser behavior on every Android device.

### Run Macrobenchmark on a physical device

The benchmark module measures cold startup, Tab Switcher scrolling, frame timing and peak main-app-process memory.

```bash
./gradlew :app:assembleBenchmark :macrobenchmark:assembleBenchmark
./gradlew :macrobenchmark:connectedCheck
```

Requirements:

- Android 10 (API 29) or newer.
- A physical Android device connected with USB debugging enabled.
- Device unlocked, awake and not thermally constrained during measurement.

**Important:** the Tab Switcher benchmark clears Cat Browser's application data while preparing a repeatable eight-tab scenario. Do not run it against an installation containing data you need to keep.

Results and Perfetto traces are written under `macrobenchmark/build/outputs/connected_android_test_additional_output/`. Compare repeated runs on the same device, Android version, refresh-rate setting and thermal state. Do not infer a universal FPS target from one run.

See the [Macrobenchmark guide](macrobenchmark/README.md).

## Privacy and security

- Browser tabs, bookmarks and recent history are stored locally in app-private storage. The current implementation restores regular tabs by URL; it does not serialize the complete GeckoSession navigation state.
- Private tabs are not added to the normal persisted tab/history/bookmark/download records.
- Cat AI does not automatically include page contents or private-tab metadata. Tab metadata is only returned through an explicitly approved tab tool.
- AI messages and approved browser-tool results are sent to Google Gemini through Supabase. Consult [AI data handling](docs/AI-APP-CONTROL.md) for retention, quotas and provider data-use details.
- GeckoView security fixes reach Cat when the engine dependency is reviewed, updated, tested and shipped in a new app release.
- Do not submit passwords, tokens, private browsing data or other sensitive material in public issue reports.
- Report security vulnerabilities privately; see the [Security policy](SECURITY.md).

## Known limitations

- Full GeckoSession restoration (back/forward stack, scroll position, form and in-page state) is not implemented.
- Recently closed tabs restore only the last URL and are kept in memory.
- Download transfer resumption after process death is not implemented; interrupted tasks are restored as metadata.
- Firefox WebExtensions compatibility is partial and must be checked against the pinned GeckoView version. Chrome Web Store/CRX support is unsupported.
- Physical-device compatibility testing remains necessary for video/media playback, Android permissions, extensions, downloads, private-session boundaries and long-duration memory/thermal behavior.
- Macrobenchmark reports main-app-process memory; Gecko content subprocesses are not fully represented by that process metric.

## Project documentation

| Document | Description |
|---|---|
| [Architecture](docs/ARCHITECTURE.md) | Component ownership, GeckoRuntime/GeckoSession lifecycle and persistence boundaries |
| [Android extension support](docs/ANDROID-EXTENSIONS.md) | Implemented WebExtension APIs, limitations and compatibility rules |
| [Extension test matrix](docs/EXTENSION-TEST-MATRIX.md) | Candidate add-ons and physical-device validation status |
| [Feature roadmap](docs/FEATURE-ROADMAP.md) | Feature status and acceptance requirements |
| [Cat AI integration](docs/AI-APP-CONTROL.md) | Gemini/Supabase architecture, user approval and data handling |
| [Third-party notices](THIRD_PARTY_NOTICES.md) | Key external components and their licenses |
| [Macrobenchmark guide](macrobenchmark/README.md) | Device setup, benchmark commands and output locations |
| [Legacy AI gateway](ai-gateway/README.md) | Deprecated rollback component and its CI-only tests |
| [Contributing](CONTRIBUTING.md) | Kotlin code organization, local verification and contribution expectations |
| [Security policy](SECURITY.md) | Supported versions and private vulnerability reporting |

## Third-party components and licensing

Cat Browser uses Mozilla GeckoView under the Mozilla Public License 2.0 (MPL-2.0). The Gradle distribution is licensed under Apache License 2.0. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the pinned GeckoView version and the principal external components.

**The repository does not currently declare a license for Cat Browser's own source code.** Do not assume that third-party component licenses grant permission to reuse Cat Browser code.

Cat Browser is an independent project and is not affiliated with Mozilla.

## Reporting an issue

Open a [GitHub issue](https://github.com/snowgemm/Cat-Browser/issues) with:

- Cat Browser version and, when possible, the commit SHA.
- Device model, Android version and ABI.
- Steps to reproduce and the expected versus actual behavior.
- Relevant non-sensitive Logcat output for crashes.
- For extension issues, the add-on name/version and exact API behavior.
- For performance issues, the benchmark device, run conditions and generated metrics.

Remove account identifiers, cookies, authorization headers, passwords, private URLs and other sensitive data before attaching logs.
