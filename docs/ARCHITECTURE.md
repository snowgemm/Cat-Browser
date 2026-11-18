# Cat Browser architecture

## Product boundary

Cat Browser is the Android application. It owns the browsing experience and is responsible for tab/session management, navigation UI, local data, permissions, downloads, extension management and privacy controls. GeckoView is an embedded browser engine, not a complete browser application.

## Current implementation

- `CatBrowserApplication` creates one process-wide `GeckoRuntime` and one application-scoped `BrowserController`.
- New tabs and the default startup page use `about:blank`; a custom HTTP(S) home page remains configurable. Existing installs that still have the old DuckDuckGo default are migrated to `about:blank` without overwriting a user-defined home page.
- `BrowserController` owns tab metadata, GeckoSession instances, tab selection, normal-mode history and bookmarks.
- Regular and private tabs use separate GeckoSession settings. Private sessions are created with `GeckoSessionSettings.Builder.usePrivateMode(true)`; the setting is fixed before the session is opened.
- GeckoSession instances are created lazily when a tab becomes active. Inactive sessions are marked inactive so GeckoView can reduce their memory footprint.
- Each tab has a separate GeckoSession. The controller handles new-session requests from web content and retains the returned session.
- `BrowserScreen` renders the active tab, the find-in-page bar, download manager, and GeckoView permission prompts.
- `BrowserDownloadManager` consumes GeckoView's original `WebResponse.body` stream, writes files to MediaStore Downloads on Android 10+ or app-scoped Downloads on Android 8/9, and tracks progress/cancel/open/share/delete in memory.
- `BrowserController` owns the GeckoView `StorageController` workflow for site permission inspection/reset and browsing-data clearing; storage clearing closes all sessions before clearing and recreates the selected tab afterward.
- `BrowserUrlResolver` validates HTTP(S) addresses, hostnames, IPv4/IPv6 and ports; plain text becomes a blank start page.
- HTTP(S) external intents are accepted by `MainActivity`.

## Lifecycle rules

1. Create one `GeckoRuntime` per application process.
2. Each open browser tab owns one `GeckoSession`.
3. Set `usePrivateMode` when constructing private sessions; this setting is read-only after the session is opened.
4. Open a session before attaching it to a `GeckoView`.
5. Set background sessions inactive and unfocused; keep the selected session active and focused.
6. Detach a session from `GeckoView` when its AndroidView is released. Do not close a session merely because the screen recomposes or the Activity is recreated.
7. Clear delegates and close a session when its tab is permanently removed.
8. Never advertise full session restoration unless GeckoSession state is serialized and restored successfully.

## Local persistence

The app stores regular-tab IDs, titles and last HTTP(S) URLs, plus bookmarks and recent history, in app-private SharedPreferences. Regular tabs are reopened at their last URL when the application process starts again.

Private tabs are excluded from tab persistence. Their IDs, titles and URLs are not written to the app's saved-tab record; private visits are not added to app history, and private tabs cannot add bookmarks. Their Gecko sessions use GeckoView private mode. Private mode does not hide a user's activity from visited websites or the network they use.

This is intentionally URL-level restoration only. Full GeckoSession state (back/forward stack, scroll position, form state and in-page state) is not currently serialized. Browser data remains on-device; no browser-history backend or browsing telemetry is added by this implementation.

## ABI packaging and validation

GeckoView ships a large native engine. The Android module creates separate APK outputs for `arm64-v8a`, `armeabi-v7a` and `x86_64`; it does not create a universal APK or an `x86` 32-bit APK.

The CI validation script inspects every generated APK archive and requires:

- Native libraries for exactly one supported ABI per APK.
- Key GeckoView engine libraries: `libmozglue.so`, `libxul.so` and `libgkcodecs.so`.
- Exactly one APK for each configured ABI, with no missing or unexpected architecture.
- A valid APK ZIP archive.

CI then verifies the APK signatures, normalizes artifact filenames and writes SHA-256 checksums. Each APK is uploaded as a separate unarchived GitHub Actions artifact, so users can download the standalone APK without a ZIP wrapper. Checksums are shown in the workflow run summary. These checks validate packaging and signing; they do not replace installation and runtime testing on physical devices for each ABI.

## Component responsibilities

| Component | Responsibility |
|---|---|
| Browser UI | Address input, tab switcher, navigation controls, bookmark/history dialogs, download manager, find-in-page and permission prompts |
| Runtime | GeckoRuntime lifetime and Gecko engine process |
| BrowserController | GeckoSession collection, active tab, navigation, normal/private data boundaries, permission callbacks, find-in-page, storage clearing and local state persistence |
| BrowserTab | Per-tab UI state, privacy mode and GeckoSession reference |
| Site permissions | GeckoView content/media permission prompts, Android runtime permission bridge and saved permission inspection/reset for supported types; device validation pending |
| Extension manager | GeckoView WebExtensionController, AMO Android catalog, install prompts and lifecycle actions |
| Downloads | GeckoView response-stream interception, safe filename handling, MediaStore/app-scoped destination, progress, cancellation, open/share/delete; task list remains in-memory |
| Privacy controls | Private-mode sessions, private-data exclusion, site permissions and selective browsing-data clearing; tracking-protection UI is not fully implemented |

## Current limitations

- Download tasks are not persisted across process death; the downloaded files remain in the selected Android/app Downloads destination. Pause/resume and retry are not implemented.
- Site permission handling covers GeckoView content/media requests and Android location/camera/microphone/notification runtime permissions; uncommon permission types and real-device behavior still need validation.
- The clear-data flow clears history, cookies, DOM storage, auth sessions, caches and stored site permissions by separate selections. Full coverage of every GeckoView storage type, especially IndexedDB, has not been verified on device.
- The browser does not yet provide a full website-permission dashboard with search, bulk editing or granular control for every permission type.
- Extension action/popup toolbar integration, options-page routing, local XPI import, private-browsing extension permissions and full compatibility reporting are not implemented.
- No complete GeckoSession state restore.
- Find-in-page is implemented but requires real-device validation; desktop-site toggle, reader mode and built-in translation UI are not implemented.
- No built-in password manager/autofill UI or bookmark import/export.
- Physical-device smoke testing is still required; CI build success does not prove real-device runtime behavior.
