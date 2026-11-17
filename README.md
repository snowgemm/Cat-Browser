# Cat Browser

An independent Android browser whose application code, interface and browsing experience are maintained in this repository. Cat owns browser UI, navigation and local browser state; Mozilla GeckoView is embedded as the browser engine.

## Current implementation

The first application foundation contains:

- Kotlin + Jetpack Compose + Material 3.
- Mozilla GeckoView Stable, pinned to `160.0.20261104181552`.
- One active GeckoSession rendered inside GeckoView.
- Address/search field with DuckDuckGo search for plain text.
- Back, forward, home, reload and Android system-back navigation.
- HTTP(S)-only schemes from the address field; other schemes are treated as search text.
- A unit-test suite for address resolution.
- GitHub Actions builds and uploads a development debug APK.

This is an early browser foundation, not a complete daily-use browser. Tab management, private browsing, history, bookmarks, downloads, site-permission prompts, extension management and Cat privacy controls are not implemented yet.

## Technology

| Area | Choice |
|---|---|
| Platform | Android |
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Browser engine | GeckoView Stable |
| Search provider | DuckDuckGo |
| Local persistence | Not introduced until browser state requires it |
| Extensions | Firefox WebExtensions via GeckoView, planned |

GeckoView is packaged inside the APK. Engine security fixes reach Cat only after the dependency is reviewed, updated, tested and shipped in a new app build.

## Build

Requirements: JDK 17, Android SDK Platform 37 and Gradle 9.6.0.

Run the following commands from the repository root:

```bash
gradle --no-daemon :app:testDebugUnitTest
gradle --no-daemon :app:assembleDebug
```

The development APK is written to `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions runs the same test and build tasks on pushes to `main`.

The debug APK is not production-signed and must not be treated as a stable release or an update channel.

## Extension support

The target is Firefox WebExtensions through GeckoView. This does not imply compatibility with every Firefox add-on, every Chrome extension or the Chrome Web Store. The extension manager is not implemented yet. Read [Android extension support](docs/ANDROID-EXTENSIONS.md) before advertising compatibility.

## Architecture

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for component ownership and lifecycle constraints.

## Third-party components

- GeckoView: Mozilla Public License 2.0 (MPL-2.0).
- Gradle tooling in CI: Gradle 9.6.0, distributed under the Apache License 2.0.

Cat Browser is an independent project and is not affiliated with Mozilla.
