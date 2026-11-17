# Android extension support contract

## Current status

**Not implemented yet.** Cat currently embeds GeckoView but does not expose an extension catalog, install screen or extension-management UI.

## Target

Cat targets Firefox WebExtensions through GeckoView. This does not imply full parity with every Firefox add-on, Chrome extension or the Chrome Web Store.

Official references:

- [GeckoView WebExtensions guide](https://mozilla.github.io/geckoview/consumer/docs/web-extensions)
- [WebExtensionController API](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/WebExtensionController.html)

## Installation constraints

- Normal extension installation uses GeckoView's `WebExtensionController.install` API and requires a Mozilla-signed add-on.
- `installBuiltIn` is for an extension packaged inside Cat's APK. It is not a general-purpose installer for arbitrary downloaded XPI files.
- Extension permissions must be displayed and approved deliberately. Cat must not grant optional or sensitive permissions automatically.
- Extension state should be queried from GeckoView, not maintained only in a separate UI database.
- Private-browsing access must remain disabled unless the user explicitly enables it for that extension.

## Compatibility states

- **Supported:** installs and required user-visible behavior passes Cat's tests on the pinned GeckoView version.
- **Limited:** installs, but documented APIs or features are unavailable or behave differently.
- **Unsupported:** a required manifest feature or browser API cannot be provided by GeckoView.
- **Not checked:** information is insufficient. This state must never be shown as supported.

Static manifest analysis can identify potential incompatibilities, but it cannot prove runtime compatibility. Every extension advertised as supported requires an installation and behavior test against the pinned GeckoView version on Android.

## Acceptance tests before launch

1. Install a Mozilla-signed add-on and display its requested permissions.
2. Confirm the extension remains installed after app restart.
3. Enable, disable, update and uninstall an extension.
4. Verify optional permissions require explicit approval.
5. Reject invalid or untrusted packages with a useful error.
6. Verify private-browsing access behavior.
7. Test every extension marked Supported against the pinned GeckoView version.
8. Report unsupported Chrome APIs accurately.
