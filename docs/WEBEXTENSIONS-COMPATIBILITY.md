# Firefox WebExtensions compatibility ledger — GeckoView Android

**Audit date:** 2026-11-22  
**Cat Browser branch:** `main`  
**Reviewed baseline:** `3de44c79996383da8be5d9204f1516a6a1b3e1bf`  
**Pinned engine:** GeckoView `160.0.20261104181552`  
**Target:** Firefox WebExtensions on Android, embedded through GeckoView

This is an evidence ledger, not a promise of complete Firefox Desktop parity. A WebExtensions API in Firefox does not by itself prove that the pinned GeckoView build exposes every behavior to an Android embedder. **Not verified** is the default when Cat has no API-level or end-to-end evidence.

## Status vocabulary

- **Supported:** end-to-end behavior passes on the pinned GeckoView build and a supported Android device.
- **Limited:** a documented subset works; unsupported options or behavior are explicitly rejected or described.
- **Unsupported:** the required behavior cannot be provided within this GeckoView/Android product boundary.
- **Not verified:** evidence is insufficient; never present it as supported.

Ownership vocabulary:
- **Gecko-native:** the engine owns execution; Cat should verify behavior rather than reimplement it.
- **Embedder delegate:** GeckoView expects the Android app to provide a callback/delegate.
- **Cat integration:** Cat maps engine requests to Android tabs, UI, storage, services or user consent.
- **Android-limited:** Android lifecycle, storage, windowing or background execution affects parity.
- **Unavailable:** the pinned public API cannot provide the required bridge.

## API inventory

| Firefox WebExtensions area | Owner | Current Cat implementation / evidence | Status | Verification still required |
|---|---|---|---|---|
| XPI install and signature | Gecko-native + Cat install flow | Mozilla-signed XPI installation through GeckoView; local import is staged privately and GeckoView validates package/signature. | Limited | EXT-00 / EXT-01 on physical devices; signature acceptance is not API compatibility. |
| Manifest keys / permissions | Gecko-native + Cat prompts | Install/update and optional permission prompts exist; there is no full fixture suite for every manifest key or permission combination. | Limited | Manifest and permission fixture suite for MV2/MV3 variants. |
| MV3 background service worker | Firefox engine boundary | Firefox does not support `background.service_worker`; compatible fallback extensions may provide `background.scripts` or `background.page`. | Unsupported for service-worker-only add-ons | Test fallback manifests; never infer support from Chrome Web Store packaging. |
| `runtime` | Gecko-native + selected Cat route | `runtime.openOptionsPage()` is routed to a Cat tab. Other runtime behavior has no comprehensive compatibility suite. | Not verified | Messaging, lifecycle and error-path fixtures. |
| `storage` | Gecko-native | No duplicate Cat storage implementation is intended; persistence and quota semantics are not certified in Cat's pinned build. | Not verified | Local/sync storage fixtures, persistence and quota tests. |
| `tabs.create` | Embedder delegate + Cat tab/session integration | Regular/private sessions and `active=false` are supported; unsupported create options are rejected. | Limited | EXT-08 for active/background tabs, limits and privacy modes. |
| `tabs.update` / `tabs.remove` | Session embedder delegate + Cat tab integration | URL, active/highlighted selection and close are handled; pin/mute/auto-discard options are rejected. | Limited | EXT-08, event ordering and rejection semantics. |
| `windows` | Android-limited | Cat owns one browser Activity and does not claim Firefox Desktop multi-window semantics. | Not verified | Define an Android window mapping before any bridge is added. |
| `webNavigation` | Gecko-native | Cat does not have a complete API fixture suite for frame, redirect and navigation-event order. | Not verified | Redirect, frame, history and process-restart fixtures. |
| `browserAction` / `pageAction` | Gecko delegate + Cat toolbar | Toolbar actions, badges and popup GeckoSessions are implemented; action-icon and device behavior remains unverified. | Limited | EXT-04 / EXT-05 on physical devices. |
| `contextMenus` | Gecko extension API / Cat UI integration | Cat's page long-press menu is its own UI and is not evidence of `browser.contextMenus` parity. | Not verified | Verify exact pinned GeckoView exposure and menu lifecycle before integrating. |
| `notifications` | Gecko extension API / Android notification integration | Android media notifications and GeckoView website-notification callbacks are not proof of extension `browser.notifications` support. | Not verified | Permission, channels, click actions and dismissal tests for extension notifications. |
| `downloads.download()` | Cat download bridge | Uses GeckoWebExecutor, Cat DownloadManager and `WebExtension.Download.update()`; query/control behavior is not complete. | Limited | EXT-10 with redirects, authentication, failures, save-as and private fetch. |
| Extension `downloads.pause/resume/cancel/search` | Cat download bridge / pinned public API boundary | Cat's current bridge does not expose a separate command path to control its download tasks from extension API calls. GeckoView's pinned `WebExtension.Download` type exposes `update(Info)` for state reporting. | Not verified | Confirm exact JS runtime behavior with fixtures; do not claim support or platform impossibility without a pinned-build test. |
| `bookmarks` | Cat storage integration | Cat's native bookmark manager does not imply `browser.bookmarks` is connected to the same store. | Not verified | Read/write, folders, events, duplicates and private-mode tests. |
| `history` | Cat storage integration | Cat registers GeckoSession.HistoryDelegate for visited-link lookups backed by normal-mode Cat history. This is separate from the extension API and does not bridge `browser.history.search/onVisited/deleteUrl`. | Not verified | EXT-14 verifies GeckoSession visited-link callbacks; separately probe extension history query, events, deletion and private-mode behavior. |
| `cookies` | Gecko-native / privacy boundary | No Cat-owned compatibility test for cookies, partitioning or private browsing is recorded. | Not verified | Cookie partitioning, private-session isolation and permission tests. |
| `sessions` | Gecko-native / Android-limited | Recently closed Cat tabs restore the last URL only; this is not evidence of extension `browser.sessions` support. | Not verified | Restore, session identity and event tests. |
| `permissions` | Cat permission-prompt integration | Required install/update and optional prompts are queued; private access is a separate opt-in. | Limited | EXT-02 across install, update, optional, data-collection and private access. |
| Content scripts | Gecko-native | Gecko executes content scripts where the extension and engine support them; Cat lacks comprehensive certification on real devices. | Not verified | EXT-06 across representative add-ons and origin boundaries. |
| `scripting` | Gecko-native / embedder compatibility | No Cat API fixture or pinned-version certification is recorded. | Not verified | Verify API exposure and execution constraints on GeckoView 160. |
| `webRequest` | Gecko-native/network engine | Cat does not have a full extension request-block/redirect fixture suite. | Not verified | Block, redirect, auth and performance/privacy tests. |
| `declarativeNetRequest` | Gecko extension engine | No Cat-side evidence establishes its API support or parity in the pinned build. | Not verified | Verify exact Gecko engine support before designing Cat code. |
| `identity` | Gecko extension API / Android app-link boundary | No Cat OAuth redirect/identity integration is recorded. | Not verified | Verify allowed redirect and account-data boundaries. |
| `browsingData` | Gecko-native + embedder delegate + Cat storage | Cat registers `WebExtension.BrowsingDataDelegate` per extension. The settings delegate exposes history and downloads; history is removed by Unix timestamp and recent completed regular-mode download records are removed without deleting downloaded files. Active downloads and private-mode download records are preserved. Form-data and password deletion requests are explicitly rejected because Cat does not own those stores. | Limited | EXT-13 on a physical device; verify Gecko-owned cache/cookie/local-storage requests, timestamp boundaries, private-mode behavior and that downloaded files/bookmarks remain intact. |
| Runtime messaging | Gecko-native | No comprehensive test suite certifies extension messaging, sender identity or lifecycle in Cat. | Not verified | Message routing, sender and background-lifecycle fixtures. |
| Native messaging | Privileged built-in extension only | GeckoView native messaging requires privileged built-in extensions and `geckoViewAddons`; content scripts additionally require `nativeMessagingFromContent`. Cat does not expose arbitrary native commands to third-party XPI packages. | Unsupported for ordinary third-party XPI | A future built-in bridge needs an allowlist, capability model and separate threat model. |
| Chrome Web Store / CRX | Unavailable in current engine architecture | Cat uses GeckoView and Firefox XPI packages; it has no Chromium engine or CRX installer. | Unsupported | Out of scope for the selected GeckoView architecture. |

## Test mapping

- **Install/signature:** EXT-00 / EXT-01. XPI acceptance proves package validation, not API compatibility.
- **Permissions/privacy:** EXT-02 / EXT-11.
- **Actions and options:** EXT-04 / EXT-05.
- **Tabs:** EXT-08.
- **Downloads:** EXT-10.
- **Content scripts, storage, network and lifecycle:** EXT-06 / EXT-07 / EXT-09 / EXT-12.
- **Browsing data:** EXT-13 verifies the supported time-scoped history/download metadata bridge.
- **GeckoSession history:** EXT-14 verifies app-owned visited-link lookup and private-mode isolation; it does not certify the WebExtensions `browser.history` API.
- The physical-device matrix remains **PENDING** until logged with Cat commit, GeckoView version, add-on version/hash, Android version and device.

## Official references

- [GeckoView WebExtensions integration](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/web-extensions.html)
- [WebExtension API](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/WebExtension.html)
- [WebExtension.Download API (GeckoView 160)](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/WebExtension.Download.html)
- [WebExtensionController API](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/WebExtensionController.html)
- [Firefox background manifest support](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/manifest.json/background)
- [GeckoView API changelog](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/doc-files/CHANGELOG)

The pinned dependency remains `160.0.20261104181552`. The currently surfaced upstream Javadoc identifies build `163.0.20261121093549`; that does not alone establish a published Maven artifact or a safe Cat upgrade. No engine update is implied.
