# Android extension support contract

## Current implementation

Cat uses GeckoView's `WebExtensionController` and the Mozilla Add-ons (AMO) v5 search API, filtered to add-ons marked as available for Android.

Implemented in the Android app:

- Search the Mozilla Add-ons catalog and list installed extensions from GeckoView.
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
- The AMO discovery catalog accepts HTTPS download URLs hosted by `addons.mozilla.org`; local file import is a separate user-selected flow.

## Known limitations

- Chrome Web Store installation and blanket Chrome-extension compatibility are not supported.
- Cat does not provide complete API-by-API compatibility reporting.
- `tabs.create` currently rejects requests requiring pinned tabs, tab containers, discarded tabs, explicit insertion indices or Reader Mode.
- `tabs.update` rejects requests to change pinned state, mute state or auto-discard behavior. GeckoView handles URL navigation after Cat accepts the request; `active` and `highlighted` requests select the corresponding Cat tab.
- The downloads API is partial: `saveAs=true` is rejected because Cat does not yet provide an extension-owned save-location picker. Conflict-action flags are not fully implemented; Cat sanitizes the requested filename and writes through its normal download destination. Extension-driven cancel, pause, resume and persistent task restoration are not wired to Cat's current download manager.
- Browser action popup sizing, extension lifecycle edge cases and permission behavior still require physical-device testing against representative signed extensions on the pinned GeckoView build.
- Browser APIs outside the integrated and verified GeckoView surface must be treated as limited or not checked.

## Acceptance tests before release

1. Install a Mozilla-signed Android add-on from AMO and inspect its permission prompt.
2. Import a signed local XPI; reject non-XPI files, empty files, files above 50 MB and packages GeckoView does not accept.
3. Confirm installed extensions remain available after app restart.
4. Enable, disable, update and uninstall an extension.
5. Verify optional permissions require explicit approval.
6. Verify private access starts disabled and works only after explicit opt-in.
7. Test browser/page action visibility, icons, badges and popup close behavior.
8. Test `runtime.openOptionsPage()`, `tabs.create`, background tab creation, `tabs.update`, `tabs.remove` and active-tab notifications.
9. Test `downloads.download()` using authenticated, redirected, private-mode and HTTP-error responses; verify progress and terminal `downloads.onChanged` updates.
10. Confirm `saveAs` requests fail clearly and unsupported conflict/cancel/pause/resume behavior is not advertised as implemented.
11. Test each add-on labelled Supported on a real Android device and record GeckoView, device and Android versions.
12. Report unsupported Chrome and Firefox APIs accurately.

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
