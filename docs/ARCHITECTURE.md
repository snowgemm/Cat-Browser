# Cat Browser architecture

## Product boundary

Cat Browser is the Android application. It owns the browsing experience and is responsible for tab/session management, navigation UI, local data, permissions, downloads, extension management and privacy controls. GeckoView is an embedded browser engine, not a complete browser application.

## Current implementation

- `CatBrowserApplication` creates one process-wide `GeckoRuntime` and one application-scoped `BrowserController`.
- New tabs and the default startup page use a native Compose New Tab Page while retaining `about:blank` as the internal start-page marker; no GeckoSession is created until the user navigates. The page shows bookmark shortcuts and recent normal-mode visits. A custom HTTP(S) home page remains configurable.
- `BrowserController` coordinates in-memory tab metadata, GeckoSession instances, tab selection, navigation, desktop-site settings, engine-reported TLS state, PDF export, print and safe long-press context-menu routing.
- `BrowserLocalStore` persists regular-tab metadata (including the desktop-site preference), normal-mode history and bookmarks in the existing app-private SharedPreferences store; private tabs are excluded before writes.
- Regular and private tabs use separate GeckoSession settings. Private sessions are created with `GeckoSessionSettings.Builder.usePrivateMode(true)`; the setting is fixed before the session is opened.
- GeckoSession instances are created lazily when a tab becomes active. Inactive sessions are marked inactive so GeckoView can reduce their memory footprint.
- Each tab has a separate GeckoSession. The controller handles new-session requests from web content and retains the returned session.
- `BrowserScreen` renders the active tab, find-in-page, download manager, GeckoView permission prompts and the browser-owned long-press context menu. The active GeckoView receives the current Activity through `ActivityContextDelegate` for native printing.
- `BrowserDownloadManager` consumes GeckoView's original `WebResponse.body` stream, writes files to MediaStore Downloads on Android 10+ or app-scoped Downloads on Android 8/9, and tracks progress/cancel/open/share/delete in memory.
- `BrowserController` owns the GeckoView `StorageController` workflow for site permission inspection/reset and browsing-data clearing; storage clearing closes all sessions before clearing and recreates the selected tab afterward.
- `BrowserUrlResolver` validates HTTP(S) addresses, hostnames, IPv4/IPv6 and ports; plain text becomes a blank start page.
- HTTP(S) external intents are accepted by `MainActivity`.

## Lifecycle rules

1. Create one `GeckoRuntime` per application process.
2. Each open browser tab owns one `GeckoSession`.
3. Set `usePrivateMode` when constructing private sessions; this setting is read-only after the session is opened.
4. Open a session before attaching it to a `GeckoView`.
5. Set background sessions inactive and unfocused; keep the selected, attached session active and focused only while the app is foregrounded.
6. Treat an extension popup as a separate short-lived GeckoSession: transfer focus to it while open, make it inactive/unfocused in the background, and restore focus to the selected browser tab when it closes.
7. Detach a session from `GeckoView` when its AndroidView is released. Do not close a tab session merely because the screen recomposes or the Activity is recreated.
8. Clear delegates and close a session when its tab is permanently removed; close extension popup sessions when dismissed or when their extension is disabled/uninstalled.
9. Never advertise full session restoration unless GeckoSession state is serialized and restored successfully.

## Background work and process lifetime

- BrowserController, BrowserDownloadManager, BrowserExtensionsManager and BrowserUpdateManager are owned by the application-scoped controller/runtime, not by an Activity instance. Activity recreation detaches and reattaches the view; it must not recreate the process-wide GeckoRuntime or close retained tab sessions.
- Browser metadata/history work uses a single-thread executor; storage and privacy-stat refreshes use dedicated HandlerThreads. These are process-scoped workers and are not independently restarted after process death.
- Browser downloads use two concurrent worker threads and persist non-private task metadata. They are not a foreground service or resumable WorkManager job; Android may terminate the process while the app is backgrounded. On the next launch, queued/downloading records are marked interrupted and partial output is cleaned up.
- Stable/Beta update checks and APK downloads use a single-thread process executor. Per-channel generation tokens prevent stale check responses from publishing after a newer check or Beta disablement. Partial updater APKs are removed on startup.
- Extension catalog lookup and XPI import use a single-thread process executor; extension download requests use GeckoWebExecutor. These operations can finish while the UI is not visible, but their UI state is delivered on the main thread.
- A GeckoSession becoming inactive is intentional: GeckoView flushes session state and reduces memory use. It is not equivalent to closing the session or serializing its full navigation state.

## Local persistence

`BrowserLocalStore` stores regular-tab IDs, titles and last HTTP(S) URLs, plus bookmarks and recent history, in the existing app-private SharedPreferences file. Regular tabs are reopened at their last URL when the application process starts again. The established preference keys and JSON shape are retained across upgrades.

Private tabs are excluded from tab persistence. Their IDs, titles and URLs are not written to the app's saved-tab record; private visits are not added to app history, and private tabs cannot add bookmarks. Their Gecko sessions use GeckoView private mode. Private mode does not hide a user's activity from visited websites or the network they use.

This is intentionally URL-level restoration only. Full GeckoSession state (back/forward stack, scroll position, form state and in-page state) is not currently serialized because session snapshots can contain form data. Desktop-site mode is a low-sensitivity per-tab preference persisted only for regular tabs; private-tab preferences remain in memory. Browser data remains on-device; no browser-history backend or browsing telemetry is added by this implementation.

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
| Browser UI | `BrowserScreen` navigation shell, `BrowserMainScreen`, shared `BrowserChrome` controls, `TabsOverview`, native New Tab Page, address suggestions, bookmark/history dialogs, downloads, find-in-page, permission prompts and context-menu sheet |
| FaviconRepository | Fetches only same-host HTTPS /favicon.ico, rejects local/private resolved addresses, bounds icon size and memory caches, and uses monogram fallback; no third-party proxy |
| Cat AI | Optional Gemini assistant, Supabase anonymous Auth, Android Keystore session storage, stateless Gemini Interactions client, explicit approval queue for every browser tool call; private tabs and page contents excluded |
| AI gateway | Supabase Edge Function with JWT verification, server-only Gemini API key, fixed function-tool allowlist, bounded request parsing and database-backed per-user/global quotas |
| Runtime | GeckoRuntime lifetime and Gecko engine process |
| BrowserController | In-memory tab/session coordination, active tab, navigation, transient downscaled tab previews, tab order, normal/private data boundaries, permission callbacks, desktop-site settings, TLS state, PDF export, print, find-in-page, context-menu routing and storage clearing |
| BrowserLocalStore | Reads and writes saved regular tabs (including desktop-site preference), normal-mode history and bookmarks using the existing SharedPreferences keys and compatible JSON schema; excludes private tabs |
| BrowserTab | Per-tab UI state, transient preview bitmap, privacy mode, desktop-site preference, current security snapshot, GeckoSession reference and temporary same-mode navigation referrer |
| Context-menu policy | Restricts page-provided targets to normalized, bounded HTTP(S) URLs before the controller can open them. |
| Site permissions | GeckoView content/media permission prompts, Android runtime permission bridge and saved permission inspection/reset for supported types; device validation pending |
| Extension manager | GeckoView WebExtensionController, AMO v5 Android discovery, official listing links, signed-XPI installation, permission prompts and lifecycle actions |
| Downloads | GeckoView response-stream interception, safe filename handling, MediaStore/app-scoped/SAF destination, progress, app cancellation, open/share/delete and persisted non-private task metadata. Transfers interrupted by process death are not resumed. |
| Privacy controls | Private-mode sessions, private-data exclusion, site permissions and selective browsing-data clearing; tracking-protection UI is not fully implemented |

## Current limitations

- Download metadata for up to 100 non-private records is persisted; active transfers interrupted by process death restore as interrupted records but cannot resume. Extension-driven pause/resume/cancel are not exposed by the public GeckoView Download API.
- Site permission handling covers GeckoView content/media requests and Android location/camera/microphone/notification runtime permissions; uncommon permission types and real-device behavior still need validation.
- The clear-data flow clears history, cookies, DOM storage, auth sessions, caches and stored site permissions by separate selections. Full coverage of every GeckoView storage type, especially IndexedDB, has not been verified on device.
- The browser does not yet provide a full website-permission dashboard with search, bulk editing or granular control for every permission type.
- Extension action/popup toolbar integration, options-page routing, signed XPI import, private-browsing permissions and selected tab/download bridges are implemented; complete Firefox API parity, Chrome Web Store support and physical-device compatibility certification are not.
- No complete GeckoSession state restore; recently closed tabs restore their last URL only and are held in memory for the current app process.
- Desktop-site mode now sets both GeckoView's desktop User-Agent and desktop viewport per tab; its preference is restored for regular tabs only. Save-as-PDF uses `GeckoSession.saveAsPdf()` and Android's Storage Access Framework; printing uses GeckoView's PrintDelegate and Android PrintManager. These flows still require physical-device validation.
- The address-bar secure indicator is based on GeckoView's `ProgressDelegate.onSecurityChange`, including security exceptions and loaded mixed content, rather than inferring security from an `https://` URL.
- Find-in-page requires real-device validation; reader mode and built-in translation UI are not implemented.
- No built-in password manager/autofill UI or bookmark import/export.
- Cat AI depends on Supabase Anonymous Auth and the server-side GEMINI_API_KEY secret. Physical-device end-to-end tests remain required; the in-app assistant is not a ChatGPT Android app connector.
- Physical-device smoke testing is still required; CI build success does not prove real-device runtime behavior.
