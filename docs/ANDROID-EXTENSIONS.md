# Android extension support contract

## 1. Meaning of “Hybrid”

For the Android phase, Hybrid means:

- Keep GeckoView as the browser engine.
- Support Firefox WebExtensions through the GeckoView runtime and upstream add-on manager.
- Evaluate Chrome-origin extensions for Gecko API/manifest compatibility.
- Never claim full Chrome Web Store parity or silently treat an incompatible extension as installed.

It does **not** mean embedding Chromium as a second engine or translating arbitrary Chrome extension packages into Firefox add-ons.

## 2. GeckoView is the source of truth

GeckoView's `WebExtensionController` exposes operations to install, list, enable, disable, update and uninstall WebExtensions. Use these APIs through the application layer rather than implementing a second extension runtime.

Important constraints from the current GeckoView API:

- `install(uri)` accepts a local or remote XPI URI, validates the manifest and signature, and requires the installed add-on to be signed by Mozilla.
- `INSTALLATION_METHOD_FROM_FILE` records the installation source. It does not disable signature validation.
- `installBuiltIn` is for extensions packaged inside the Cat Browser APK; it is not a general-purpose external XPI installer.
- Installation/permission prompts must be handled deliberately. Do not automatically grant requested permissions.
- Extension state should be queried from GeckoView, not kept only in a separate UI database.

Official references:
- [GeckoView WebExtensions guide](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/web-extensions.html)
- [WebExtensionController API](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/WebExtensionController.html)

## 3. Compatibility policy

A Chrome-origin extension is only a candidate for support after checking:

1. Manifest version and keys.
2. Required permissions and host permissions.
3. Browser APIs used by its background scripts and content scripts.
4. Background execution model (event pages/service workers).
5. Browser-specific UI and APIs, including side panel/sidebar, web request/declarative network rules, downloads, tabs and native messaging.
6. External native dependencies or platform assumptions.

Compatibility results must use explicit states:

- **Supported:** the tested package installs and its required behavior passes the Cat Browser test suite.
- **Limited:** it installs, but a documented feature/API is unavailable or behaves differently.
- **Unsupported:** a required manifest field or API cannot be provided by GeckoView.
- **Not checked:** the analyzer has insufficient information; this must never be displayed as “supported”.

A static manifest/API scan can provide warnings, but it cannot prove runtime compatibility. Every extension advertised as supported needs an actual install-and-behavior test against the target GeckoView version.

## 4. Permission and privacy requirements

- Show the extension name, publisher/source and requested permissions before installation.
- Do not grant optional permissions unless the user explicitly approves them.
- Keep private-browsing access disabled by default unless the user enables it for that extension.
- Provide clear enable, disable, update and uninstall controls.
- Report installation/signature/API errors without claiming success.
- Do not proxy extension traffic through a Cat Browser backend by default; keep browsing and extension state local unless a feature requires otherwise.

## 5. Acceptance tests before claiming support

- Install a Mozilla-signed Firefox add-on and confirm its permissions are presented.
- Confirm installed extensions remain listed after closing and reopening the browser.
- Enable, disable, update and uninstall an extension.
- Confirm permission changes require explicit user approval.
- Reject an invalid or untrusted package with a useful error.
- Test private-browsing access behavior.
- For each Chrome-origin extension marked Supported, test its actual user-visible feature on the target Android/GeckoView version.
- Verify that unsupported Chrome APIs are reported accurately and no UI claims universal Chrome Web Store compatibility.
