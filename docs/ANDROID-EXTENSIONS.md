# Android extension support contract

## Current implementation

Cat uses GeckoView's `WebExtensionController` and the Mozilla Add-ons (AMO) v5 search API, filtered to add-ons marked as available for Android.

Implemented in the Android app:
- Search the Mozilla Add-ons catalog.
- List installed WebExtensions from GeckoView.
- Install from an HTTPS AMO package URL using GeckoView's add-on manager installation method.
- Let GeckoView validate the add-on package, manifest and Mozilla signature.
- Show requested permissions, host origins and data-collection permissions before installation or permission changes.
- Keep private-browsing access disabled because Cat does not yet provide a private browsing mode.
- Enable, disable, check for updates and uninstall installed add-ons.
- Refresh the installed list from GeckoView rather than treating a separate local list as authoritative.

## Compatibility contract

The target is Firefox WebExtensions through GeckoView. This does not imply full parity with every Firefox add-on, Chrome extension or the Chrome Web Store.

- **Supported:** installed and the required user-visible behavior passes Cat's tests on the pinned GeckoView version and Android.
- **Limited:** installs, but documented APIs or features are unavailable or behave differently.
- **Unsupported:** a required manifest feature or browser API cannot be provided by GeckoView.
- **Not checked:** there is insufficient evidence. Never show this state as supported.

The AMO Android filter is only a catalog compatibility signal. It is not proof that an add-on works in Cat. Static manifest analysis is not runtime verification.

## Installation and permission constraints

- Normal extension installation uses `WebExtensionController.install` and requires a Mozilla-signed add-on.
- `installBuiltIn` is for extensions packaged inside Cat's APK; it is not a general-purpose installer for arbitrary downloaded XPI files.
- GeckoView validates signature and manifest during installation.
- Required and newly requested permissions must be presented to the user.
- Cat does not silently grant optional, private-browsing or data-collection permissions.
- Private-browsing access remains disabled until Cat implements an isolated private mode and an explicit opt-in flow.
- The current catalog accepts HTTPS download URLs hosted by `addons.mozilla.org`; it does not accept arbitrary external packages.

## Current gaps

- Extension action icons and popup UI integrated into the browser toolbar.
- Extension options-page routing.
- Full Chrome API compatibility analysis and reporting.
- File-based XPI import workflow.
- A compatibility test matrix on physical Android devices.
- Tests confirming persistence, enable/disable, updates and uninstall on the pinned GeckoView version.

## Acceptance tests before launch

1. Install a Mozilla-signed Android add-on and inspect its permission prompt.
2. Confirm the extension remains installed after app restart.
3. Enable, disable, update and uninstall an extension.
4. Verify optional permissions require explicit approval.
5. Reject invalid, unsigned or untrusted packages with a useful error.
6. Verify private-browsing access remains disabled.
7. Test each add-on marked Supported against the pinned GeckoView version on a real Android device.
8. Report unsupported Chrome APIs accurately.

## Official references

- [GeckoView WebExtensions guide](https://mozilla.github.io/geckoview/consumer/docs/web-extensions)
- [GeckoView WebExtensionController API](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/WebExtensionController.html)
- [Mozilla Add-ons API](https://mozilla.github.io/addons-server/topics/api/addons.html)
