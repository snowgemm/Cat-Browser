# GeckoView capability audit

Last reviewed: 2026-11-20  
Pinned application dependency: `org.mozilla.geckoview:geckoview:160.0.20261104181552`

This document separates APIs provided by GeckoView from complete browser features owned by Cat Browser. An API being available in the engine does not mean the browser already exposes a safe, tested workflow for it.

## Integrated

| Capability | GeckoView API | Cat Browser integration |
|---|---|---|
| Runtime and sessions | `GeckoRuntime`, `GeckoSession`, `GeckoView` | One process-wide runtime; one lazy session per tab; visible/focused/priority state follows tab and app lifecycle. |
| Navigation | `NavigationDelegate`, session load/back/forward/reload/stop | Browser navigation, tab creation for web popups and page-load progress. |
| Private browsing | `GeckoSessionSettings.Builder.usePrivateMode` | Private sessions are configured before opening; private tabs, URLs, history and desktop-site preferences are excluded from app-owned persistence. |
| Tracking/content blocking | Session tracking-protection setting and runtime content-blocking controller | Tracking protection is applied to sessions; the New Tab privacy panel reads GeckoView blocking events. |
| Site permissions | `PermissionDelegate`, Android runtime permission callbacks | Content/media prompts, Android permission bridge, stored permission review and supported reset actions. |
| Downloads | `ContentDelegate.onExternalResponse`, `WebResponse.body` | Response stream is handed to the download manager, with progress, cancellation, safe filename handling and Android Downloads storage. |
| WebExtensions | `WebExtensionController` | Signed XPI installation and lifecycle operations, AMO catalogue discovery and a deliberately partial compatibility surface. AMO listing does not guarantee GeckoView compatibility. |
| Find in page | `GeckoSession.getFinder()` | Debounced search, highlight-all, previous/next match and result count where GeckoView reports it. |
| Desktop site | `GeckoSessionSettings.USER_AGENT_MODE_DESKTOP`, `VIEWPORT_MODE_DESKTOP` | Per-tab toggle changes both the User-Agent and viewport. The preference is persisted for regular tabs only. |
| TLS and mixed content | `ProgressDelegate.onSecurityChange`, `SecurityInformation` | Address-bar lock depends on GeckoView security state, certificate exception state and whether active/passive mixed content was loaded. |
| Save as PDF | `GeckoSession.saveAsPdf()` | The user chooses a destination through Android Storage Access Framework; PDF bytes are streamed to that URI on a worker executor. |
| Print | `GeckoSession.printPageContent()`, `GeckoView.ActivityContextDelegate` | GeckoView's print delegate uses Android PrintManager with the current Activity context. |

## Available, not yet integrated

| Capability | Relevant GeckoView API | Why it is deferred |
|---|---|---|
| Full tab/session restoration | `ProgressDelegate.onSessionStateChange`, `SessionState`, `restoreState()` | Session snapshots can include history, scroll, zoom and form data. Persisting them requires encrypted storage, schema/version handling, corruption recovery and explicit privacy controls. Private-tab state must never be persisted. Cat Browser remains URL-level restore for now. |
| Page translation | `GeckoSession.getSessionTranslation()`, runtime translation/model management | Requires language selection, model-size/download consent, progress/error handling, storage management and a clear network/data notice. It should not silently download large language models. |
| Android Autofill | `GeckoView.setAutofillEnabled()`, `GeckoSession.getAutofillSession()`, `Autofill.Delegate` | These APIs expose form structure and Android Autofill integration points; they do not provide a secure credential vault or finished password manager. A user-consented Autofill workflow and safeguards are needed. |
| Picture-in-picture | `PictureInPictureController.SessionPictureInPicture.Delegate`, `GeckoSessionSettings.Builder.pictureInPictureToken`, `CompositorController.onPipModeChanged` plus Android Activity PiP APIs | Experimental integration added: Gecko creates a video clone session from its one-time placement token; a dedicated, non-exported PiP Activity hosts the clone while the browser Activity and tab remain intact. Physical-device/vendor validation is still required. |
| Complete media controls | `MediaSession.Delegate`, `MediaDelegate` | Cat Browser currently handles media fullscreen state; notification, lock-screen, headset and route controls need a separate media-session implementation. |
| WebExtension native messaging | WebExtension native-messaging delegates and extension messaging APIs | A host bridge must be allowlisted, capability-scoped and permission-gated. Arbitrary native commands must not be exposed to content scripts. |
| Accessibility/page extraction | Accessibility-tree and page-extraction APIs | Needs a concrete user-facing feature, bounded data flow and a privacy review before page content is exposed to other app components. |
| Context menus and selection actions | `ContentDelegate.onContextMenu`, `SelectionActionDelegate` | Requires a browser-owned action sheet with correct link/image/text handling, clipboard permissions and safe external-intent routing. |
| Scroll/zoom controls | `PanZoomController`, `ScrollDelegate` and session settings | Could support per-site zoom and browser toolbar controls, but needs UX decisions and device testing before adding controls. |

## Integration constraints

- GeckoView is the web engine, not a complete browser application. Cat Browser still owns tabs, browser chrome, saved data, permissions, downloads and user consent.
- Desktop mode must configure both the User-Agent and viewport. Changing only the User-Agent may leave a site laid out for a mobile viewport.
- PDF export uses Android's Storage Access Framework and does not request broad shared-storage write permission.
- `saveAsPdf()` is annotated `@HandlerThread`; the implementation calls it on the existing storage HandlerThread and copies the returned stream on a dedicated single-thread writer.
- Native print requires GeckoView's Activity context delegate so its print implementation can obtain Android's PrintManager. The Activity is supplied by the UI layer and is not retained by the application-scoped controller.
- PiP uses GeckoView 160's experimental placement-token flow. A dedicated, non-exported Activity renders the cloned session and enters Android PiP; the source session stays active while its clone plays. Expanding PiP reselects the original source tab. The controller stores no Activity reference: the browser UI registers the launch callback and the PiP Activity registers its close callback for their respective lifetimes.
- A lock symbol is not inferred from an `https://` prefix. GeckoView's security callback is the source of truth; the indicator is withheld for security exceptions and loaded mixed content.
- Session snapshots are intentionally not written to disk until form-data privacy and encrypted storage are designed.
- Translation model downloads, Autofill credential storage and native messaging are not enabled without explicit user-facing consent and capability controls.
- No full Firefox WebExtensions API parity or general Chrome Web Store CRX support is claimed.

## Official references

- [GeckoView overview](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/index.html)
- [GeckoSession API](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/GeckoSession.html)
- [GeckoSessionSettings.Builder API](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/GeckoSessionSettings.Builder.html)
- [GeckoSession ProgressDelegate API](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/GeckoSession.ProgressDelegate.html)
- [GeckoSession SecurityInformation API](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/GeckoSession.ProgressDelegate.SecurityInformation.html)
- [GeckoView ActivityContextDelegate API](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/GeckoView.ActivityContextDelegate.html)
- [GeckoView WebExtensions guide](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/web-extensions.html)
- [GeckoView Save to PDF design](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/design/save-to-pdf.html)
- [GeckoView API changelog](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/doc-files/CHANGELOG)

The PiP integration uses experimental APIs introduced in GeckoView 160.0; the other listed capabilities are evaluated against the pinned 160.0 dependency. Android CI verifies source compatibility with the exact dependency resolved by the project.
