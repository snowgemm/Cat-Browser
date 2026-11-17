# Cat Browser architecture

## Product boundary

Cat Browser is the Android application. It owns the browsing experience and is responsible for tab/session management, navigation UI, local data, permissions, downloads, extension management and privacy controls. GeckoView is an embedded browser engine, not a complete browser application.

## Current implementation

- `CatBrowserApplication` creates one process-wide `GeckoRuntime` and one application-scoped `BrowserController`.
- `BrowserController` owns tab metadata, GeckoSession instances, tab selection, local history and bookmarks.
- GeckoSession instances are created lazily when a tab becomes active. Inactive sessions are marked inactive so GeckoView can reduce their memory footprint.
- Each tab has a separate GeckoSession. The controller handles new-session requests from web content and retains the returned session.
- `BrowserScreen` renders the active tab and sends user actions to the controller.
- `BrowserUrlResolver` validates HTTP(S) addresses, hostnames, IPv4/IPv6 and ports; plain text becomes a DuckDuckGo search.
- HTTP(S) external intents are accepted by `MainActivity`.

## Lifecycle rules

1. Create one `GeckoRuntime` per application process.
2. Each open browser tab owns one `GeckoSession`.
3. Open a session before attaching it to a `GeckoView`.
4. Set background sessions inactive and unfocused; keep the selected session active and focused.
5. Detach a session from `GeckoView` when its AndroidView is released. Do not close a session merely because the screen recomposes or the Activity is recreated.
6. Clear delegates and close a session when its tab is permanently removed.
7. Never advertise full session restoration unless GeckoSession state is serialized and restored successfully.

## Local persistence

The current app stores tab IDs, titles, last HTTP(S) URLs, bookmarks and recent history in app-private SharedPreferences. The saved tabs are reopened at their last URL when the application process starts again.

This is intentionally URL-level restoration only. Full GeckoSession state (back/forward stack, scroll position, form state and in-page state) is not currently serialized. Browser data remains on-device; no browser-history backend or browsing telemetry is added by this implementation.

## ABI packaging and validation

GeckoView ships a large native engine. The Android module creates separate APK outputs for `arm64-v8a`, `armeabi-v7a` and `x86_64`; it does not create a universal APK or an `x86` 32-bit APK.

The CI validation script inspects every generated APK archive and requires:

- Native libraries for exactly one supported ABI per APK.
- Key GeckoView engine libraries: `libmozglue.so`, `libxul.so` and `libgkcodecs.so`.
- Exactly one APK for each configured ABI, with no missing or unexpected architecture.
- A valid APK ZIP archive.

CI then verifies the APK signatures, normalizes artifact filenames and writes SHA-256 checksums. These checks validate packaging and signing; they do not replace installation and runtime testing on physical devices for each ABI.

## Component responsibilities

| Component | Responsibility |
|---|---|
| Browser UI | Address input, tab switcher, navigation controls, bookmark/history dialogs |
| Runtime | GeckoRuntime lifetime and Gecko engine process |
| BrowserController | GeckoSession collection, active tab, navigation, local state persistence |
| BrowserTab | Per-tab UI state and GeckoSession reference |
| Site permissions | Explicit prompts and persisted per-origin decisions; not implemented |
| Extension manager | WebExtensionController operations and permission review; not implemented |
| Downloads | Safe download destination, progress and file handling; not implemented |
| Privacy controls | Tracking protection and per-site controls; not fully implemented |

## Current limitations

- No private-browsing mode or isolated private storage.
- No complete website permission prompt or per-site permission manager.
- No downloads UI or file management flow.
- No extension installation/management interface.
- No complete GeckoSession state restore.
- No find-in-page or browser settings screen.
- Physical-device smoke testing is still required; CI build success does not prove real-device runtime behavior.
