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
- GitHub Actions runs JVM unit tests and builds a development debug APK.

## Restoration and local data

Cat stores tab IDs, titles, last URLs, bookmarks and recent history in app-private local preferences. This data is not sent to a Cat account or backend.

After a process restart, each saved tab is reopened at its last URL. The current implementation does **not** persist GeckoSession's full state: per-tab back/forward stacks, scroll position, form state and in-page state are not restored. Clearing app data removes this local data.

## Current limitations

This is a usable early browser foundation, not yet a complete daily-use browser. The following are not implemented:

- Private browsing and independent private-session storage.
- A complete website permission prompt and per-site permission manager.
- A downloads manager and file picker workflow.
- Firefox WebExtensions installation and management UI.
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
| Extensions | Firefox WebExtensions via GeckoView, planned |

GeckoView is packaged inside the APK. Engine security fixes reach Cat only after the dependency is reviewed, updated, tested and shipped in a new app build.

## Build

Requirements: JDK 17, Android SDK Platform 37.2, Android SDK Build-Tools 36.0.0 and the Gradle 9.6.0 Wrapper.

The app compiles against Android API 37.2 to satisfy the published minimum compile SDK requirements of GeckoView and the selected Compose libraries. It currently targets API 36; compile SDK and target SDK are independent settings.

Run from the repository root:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

The development APK is written to `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions installs the pinned SDK platform, runs the same test and build tasks, verifies that the APK exists, and uploads it as an artifact.

The debug APK is not production-signed and must not be treated as a stable release or an update channel.

## Extension support

The target is Firefox WebExtensions through GeckoView. This does not imply compatibility with every Firefox add-on, every Chrome extension or the Chrome Web Store. The extension manager is not implemented yet. Read [Android extension support](docs/ANDROID-EXTENSIONS.md) before advertising compatibility.

## Architecture

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for component ownership, session lifecycle and current limits.

## Third-party components

- GeckoView: Mozilla Public License 2.0 (MPL-2.0).
- Gradle Wrapper: Gradle 9.6.0, Apache License 2.0.

Cat Browser is an independent project and is not affiliated with Mozilla.
