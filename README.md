# Cat Browser

An independent Android browser whose application code, interface and browsing experience are maintained in this repository. Cat owns browser UI, navigation and local browser state; Mozilla GeckoView is embedded as the browser engine.

## Current implementation

- Kotlin + Jetpack Compose + Material 3.
- Mozilla GeckoView Stable, pinned to `160.0.20261104181552`.
- Multiple tabs (up to 20), with tab switching and closing.
- Links requesting a new browsing context (`target="_blank"` / `window.open`) create a new Cat tab.
- Tab metadata is stored locally. On app restart, tabs reopen at their last HTTP(S) URL.
- Address/search field with DuckDuckGo search for plain text.
- Back, forward, home, stop/reload and Android system-back navigation.
- Page-load progress and basic load/process error feedback.
- Local bookmarks and recent browsing history, stored on-device.
- HTTP(S) links can be opened from other Android apps.
- Address resolution validates hostnames, IPv4, IPv6 and ports; internationalized domain names are converted to ASCII.
- ABI-specific APKs are built for `arm64-v8a`, `armeabi-v7a` and `x86_64`; CI checks native-library contents, APK signatures and SHA-256 checksums.

## ABI-specific APK distribution

GeckoView includes large native engine libraries. The app therefore produces separate APKs for three supported Android ABIs rather than shipping all native libraries in one universal APK:

| APK | Intended devices |
|---|---|
| `cat-browser-arm64-v8a-debug.apk` | Most modern 64-bit ARM Android phones |
| `cat-browser-armeabi-v7a-debug.apk` | 32-bit ARM Android devices |
| `cat-browser-x86_64-debug.apk` | x86-64 Android devices and compatible emulators |

There is deliberately no `x86` 32-bit APK: the pinned GeckoView dependency does not currently provide the key engine libraries for that ABI in the inspected dependency artifact. Each generated APK must contain native libraries for exactly one ABI and key GeckoView libraries (`libmozglue.so`, `libxul.so` and `libgkcodecs.so`). CI fails if those checks do not pass.

GitHub Actions uploads **three separate, unarchived artifacts**: one ARM64 APK, one ARMv7 APK and one x86_64 APK. Each artifact is a standalone `.apk` file; there is no combined ZIP. SHA-256 checksums are printed in the workflow run summary. The artifacts are retained for 14 days.

These are development/debug builds, not production-signed stable releases. The APKs use the standard debug signing configuration.

## Restoration and local data

Cat stores tab IDs, titles, last URLs, bookmarks and recent history in app-private local preferences. This data is not sent to a Cat account or backend.

After a process restart, each saved tab is reopened at its last URL. The current implementation does **not** persist GeckoSession's full state: per-tab back/forward stacks, scroll position, form state and in-page state are not restored. Clearing app data removes this local data.

## Current limitations

This is a usable early browser foundation, not yet a complete daily-use browser. The following are not implemented:

- Private browsing and independent private-session storage.
- A complete website permission prompt and per-site permission manager.
- A downloads manager and file picker workflow.
- Firefox WebExtensions catalog and management UI (initial GeckoView-backed implementation; requires physical-device validation).
- Full GeckoSession state restoration, including scroll/form/navigation history.
- Find-in-page, browser settings and a complete privacy dashboard.

The app has passed its current CI compile, unit-test and APK-build checks. A physical-device installation and browsing smoke test is still required before claiming runtime readiness.

## Technology

| Area | Choice |
|---|---|
| Platform | Android |
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Browser engine | GeckoView Stable |
| Search provider | DuckDuckGo |
| Local storage | App-private SharedPreferences |
| Extensions | Firefox WebExtensions via GeckoView; Android-compatible catalog and manager are implemented, compatibility remains API-dependent |

GeckoView is packaged inside each APK. Engine security fixes reach Cat only after the dependency is reviewed, updated, tested and shipped in a new app build.

## Build

Requirements: JDK 17, Android SDK Platform 37.2, Android SDK Build-Tools 36.0.0 and the Gradle 9.6.0 Wrapper.

The app compiles against Android API 37.2 to satisfy the published minimum compile SDK requirements of GeckoView and the selected Compose libraries. It currently targets API 36; compile SDK and target SDK are independent settings.

Run from the repository root:

```bash
./gradlew :app:clean
./gradlew :app:testDebugUnitTest :app:assembleDebug
python3 scripts/verify_abi_apks.py \
  --input app/build/outputs/apk/debug \
  --output dist/abi-apks
```

Gradle writes the ABI-specific APKs to `app/build/outputs/apk/debug/`. The verification script checks the native ABI split, key GeckoView libraries and archive integrity, then copies the APKs to `dist/abi-apks/` using the stable filenames listed above and writes SHA-256 checksums. GitHub Actions additionally verifies each APK signature before uploading each APK as a separate unarchived artifact.

The debug APKs are not production-signed and must not be treated as stable releases or an update channel.

## Extension support

The target is Firefox WebExtensions through GeckoView. This does not imply compatibility with every Firefox add-on, every Chrome extension or the Chrome Web Store. The extension manager is not implemented yet. Read [Android extension support](docs/ANDROID-EXTENSIONS.md) before advertising compatibility.

## Architecture

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for component ownership, session lifecycle and current limits.

## Third-party components

- GeckoView: Mozilla Public License 2.0 (MPL-2.0).
- Gradle Wrapper: Gradle 9.6.0, Apache License 2.0.

Cat Browser is an independent project and is not affiliated with Mozilla.
