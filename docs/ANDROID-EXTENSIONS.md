# Android extension support contract

## Current implementation

Cat uses GeckoView's `WebExtensionController` and the Mozilla Add-ons (AMO) v5 search API, filtered to add-ons marked as available for Android.

Implemented in the Android app:

- Discover public extensions through the official [Mozilla Add-ons for Android catalog](https://addons.mozilla.org/android/) using the AMO v5 search API (`/api/v5/addons/search/`).
- Open the official AMO listing for each catalog result; entries without an approved, trusted XPI download remain discoverable but cannot be installed from the in-app button.
- Keep the AMO Android compatibility filter distinct from GeckoView compatibility: AMO availability is not a Cat certification.
- Parse catalog responses defensively and preserve entries when no installable file exists. Only explicitly `public` AMO files with a trusted Mozilla download URL are eligible; explicitly unsigned files are rejected, and explicitly Mozilla-signed candidates are preferred when multiple files are returned. GeckoView remains the final XPI manifest/signature validator.
- List installed extensions from GeckoView.
- Install AMO packages over HTTPS.
- Import a local `.xpi` from Android's document picker. Cat stages the selected file in its private cache, limits it to 50 MB, and passes a local `file:` URI to GeckoView.
- Rely on GeckoView to validate the XPI manifest and Mozilla signature. Cat does not bypass signature validation or install arbitrary unsigned packages.
- Show requested permissions, host origins and data-collection permissions before install or permission changes.
- Enable, disable, update and uninstall installed extensions.
- Register browser actions and page actions; display action icons when GeckoView can decode them, with a monogram fallback, and display badges.
- Open extension popup pages in a separate GeckoSession hosted in a Cat dialog.
- Route `runtime.openOptionsPage()` to a Cat tab when GeckoView delegates options-page handling to the app.
- Handle `tabs.create`, supported `tabs.update` requests and `tabs.remove` through GeckoView runtime and per-session delegates. Background tab creation is honored when `active=false`; Cat also notifies extensions when the selected tab changes.
- Handle basic `downloads.download()` calls through GeckoView's `DownloadDelegate` and `GeckoWebExecutor`. Cat executes the supplied `WebRequest` with its fetch flags, streams the response into Cat's download manager, sanitizes the requested filename, and publishes progress/terminal metadata through `WebExtension.Download.update()`.
- Preserve GeckoView's private-fetch flag for downloads requested from private browsing.
- Keep extension private-browsing access disabled by default. A separate confirmation is required before enabling it for an individual extension.

Action buttons appear beside the address field; the strip can scroll horizontally when there are several actions. Action visibility and values may differ between tabs.

## Compatibility contract

The target is Firefox WebExtensions through GeckoView. This does not imply full parity with every Firefox add-on, every Chrome extension or the Chrome Web Store.

- **Supported:** the required user-visible behavior passes Cat's tests on the pinned GeckoView version and Android.
- **Limited:** the extension installs, but one or more APIs, permissions or UX behaviors are unavailable or differ.
- **Unsupported:** a required manifest feature or browser API cannot be provided by GeckoView or Cat.
- **Not checked:** there is insufficient evidence. Never show this state as supported.

The AMO Android filter is only a catalog compatibility signal. It is not proof that an add-on works in Cat. Static manifest analysis is not runtime verification. Do not label an extension fully compatible solely because installation succeeds.

## Installation and permission constraints

- GeckoView's `WebExtensionController.install` validates the package and requires a Mozilla-accepted signed extension.
- Local XPI import accepts only a filename ending in `.xpi`, stages at most 50 MB in app-private cache and relies on GeckoView for manifest and signature validation.
- `installBuiltIn` is for extensions packaged inside Cat's APK; it is not a general-purpose installer for arbitrary XPI files.
- Required and newly requested permissions are presented to the user. Optional permissions are not silently granted.
- Private-browsing access is disabled by default. Enabling it requires a separate confirmation explaining that the extension may access pages inside private tabs.
- The AMO discovery catalog accepts HTTPS download URLs hosted by `addons.mozilla.org`; listing links are also restricted to the official AMO host. Local file import is a separate user-selected flow.
- Search requests are latest-query-wins: a newer query disconnects the previous HTTP request, and stale responses cannot replace the current result list.

## Current compatibility boundary

Cat targets Firefox WebExtensions inside GeckoView. It is not Firefox Desktop, and it is not Chromium. An API being part of the Firefox WebExtensions specification does not by itself mean that GeckoView exposes the browser-side callback Cat needs to implement it.

| API / capability | Cat integration | Status |
|---|---|---|
| `browserAction` / `pageAction` | Toolbar icons, titles, badges, enable state and popup sessions | Partial; device validation pending |
| `runtime.openOptionsPage()` | Routes the extension options page into a Cat tab | Implemented; device validation pending |
| `tabs.create` | Creates regular/private Cat sessions where supported | Partial; pinned/container/discarded/index/Reader Mode options are rejected |
| `tabs.update` / `tabs.remove` | URL navigation, active/highlighted selection and session close | Partial; mute/pin/auto-discard changes are not supported |
| `downloads.download()` | Uses GeckoWebExecutor, reports progress and terminal metadata, handles `saveAs` through Android's Storage Access Framework, and applies safe filename conflict handling | Partial; `OVERWRITE` is conservatively uniquified and query/control APIs are not complete |
| Download task history in Cat UI | Persists up to 100 non-private task records and output references | Implemented for local display; interrupted transfers are restored as interrupted records, not resumed |
| `downloads.pause()` / `resume()` / `cancel()` from extensions | GeckoView's public `WebExtension.Download` surface only exposes `update(Info)`; no public app callback is available for these commands | Not implementable through the pinned public API |
| `storage`, content scripts and other engine APIs | Executed by Gecko where supported by the bundled GeckoView build | Not comprehensively certified by Cat |
| `contextMenus`, `notifications`, `identity`, `sessions`, `cookies`, `history`, `bookmarks`, `scripting`, `declarativeNetRequest`, native messaging | No complete Cat-side integration and compatibility test suite | Not verified; do not advertise as supported |
| Chrome Web Store / CRX | Cat installs Firefox-format signed XPI packages through GeckoView | Unsupported; no Chromium engine or CRX installer |

### Downloads behavior

- `saveAs=true` requests now open Android's Create Document picker. Canceling the picker rejects the extension download request instead of silently choosing another destination.
- Cat persists metadata for up to 100 non-private downloads. This includes filename, source, progress, state, output URI/path and extension download identifiers.
- If Android kills Cat while a transfer is active, the record is restored as interrupted and its partial artifact is cleaned where access permits. The transfer itself is **not** resumed; request headers, POST bodies and server range support cannot be reconstructed safely from the stored metadata alone.
- Private-mode download records are excluded from persistent history.
- `UNIQUIFY` checks Cat's default destination and adds a numbered suffix. `PROMPT` opens Android's Create Document picker; exact behavior after choosing a name depends on the document provider. `OVERWRITE` currently falls back to a unique filename instead of deleting an existing file before the new transfer succeeds. These are safe fallbacks, not full Firefox Desktop conflict semantics.
- The app's own Cancel action is separate from extension calls to `downloads.cancel()`; the latter is not bridged through the public GeckoView API.

### Remaining platform limits

- Complete Firefox WebExtensions API parity is not a realistic promise for an embedded GeckoView browser. Cat must implement and test each embedder-facing bridge separately.
- Chrome Web Store packages are not compatible by default with GeckoView. No blanket Chrome add-on compatibility is claimed.
- A signed XPI being accepted by GeckoView proves package/signature acceptance only. It does not prove that every API used by the extension is available or that its UI works in Cat.
- The physical-device matrix is tracked in [EXTENSION-TEST-MATRIX.md](EXTENSION-TEST-MATRIX.md). All real-device results remain pending until the test is actually run and logged.

## Acceptance tests before release

1. Open the in-app AMO source link, load the default Android catalog, search for a known add-on, and open its official listing.
2. Verify that catalog results without an approved XPI remain visible but cannot be installed; verify explicitly unsigned and untrusted download URLs are never offered.
3. Install a Mozilla-signed Android add-on from AMO and inspect its permission prompt.
4. Import a signed local XPI; reject non-XPI files, empty files, files above 50 MB and packages GeckoView does not accept.
5. Confirm installed extensions remain available after app restart.
6. Enable, disable, update and uninstall an extension; verify action/tab delegates detach on disable and removal.
7. Verify optional permissions require explicit approval and private access stays disabled until explicit opt-in.
8. Verify browser/page action icons, badges, popup sessions and `runtime.openOptionsPage()`.
9. Test supported `tabs.create`, `tabs.update`, `tabs.remove` and active-tab notifications; verify rejected options fail clearly.
10. Test `downloads.download()` with authenticated, redirected, private-mode and HTTP-error responses; verify progress and terminal `downloads.onChanged` updates.
11. Test `saveAs` and conflict flags: select a provider/location, cancel the picker, test duplicate names with `UNIQUIFY`, `PROMPT` and the safe `OVERWRITE` fallback, check filename sanitization and reopen a completed file.
12. Kill/restart Cat during a transfer; verify the task appears as interrupted, no private metadata returns, and incomplete artifacts are cleaned.
13. Run every candidate in the physical-device matrix and record exact Cat commit, GeckoView version, add-on version, device, Android version and result.
14. Report unsupported Chrome and Firefox APIs accurately; do not mark an add-on compatible solely because installation succeeded.

## Official references

- [GeckoView WebExtensions guide](https://mozilla.github.io/geckoview/consumer/docs/web-extensions)
- [GeckoView WebExtension API](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/WebExtension.html)
- [GeckoView WebExtensionController API](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/WebExtensionController.html)
- [GeckoView ActionDelegate API](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/WebExtension.ActionDelegate.html)
- [GeckoView DownloadDelegate API](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/WebExtension.DownloadDelegate.html)
- [GeckoView DownloadRequest API](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/WebExtension.DownloadRequest.html)
- [GeckoView Download.Info API](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/WebExtension.Download.Info.html)
- [GeckoWebExecutor API](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/GeckoWebExecutor.html)
- [Mozilla Add-ons API](https://mozilla.github.io/addons-server/topics/api/addons.html)
