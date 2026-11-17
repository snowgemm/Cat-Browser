# Cat Browser

Android-first browser experiment built from [Iceraven](https://github.com/fork-maintainers/iceraven-browser), which is based on Mozilla Fenix, GeckoView and Android Components.

## Current state

This repository is currently a **build-and-branding layer**, not a complete standalone Android codebase. The workflow checks out an upstream Iceraven release, applies Cat Browser's package/name branding, and builds that Android app. Until Cat-owned application code is added, the resulting APK largely retains upstream behavior and UI.

The project is Android-only for the current phase. Desktop work is deferred.

## Extension support: precise scope

The target is a hybrid **compatibility goal**, not a promise that every Chrome Web Store extension can be installed on GeckoView.

- **Firefox WebExtensions:** use the WebExtensions runtime and add-on management inherited from the GeckoView/Fenix base.
- **Chrome extensions:** only consider extensions whose manifest and API requirements are compatible with Gecko. Chrome Web Store packages are not directly interchangeable with Firefox add-ons.
- **Local .xpi files:** arbitrary unsigned XPI sideloading is not claimed. GeckoView's normal install API validates the package and requires a Mozilla signature. The `FROM_FILE` installation method describes where installation came from; it does not remove signature validation.
- **Unsupported APIs:** must be identified and shown as unsupported/limited rather than silently accepted.

See [Android extension support contract](docs/ANDROID-EXTENSIONS.md).

## Build an APK

1. Open the repository's **Actions** tab.
2. Run **Build Cat Browser APK**.
3. Keep the default Iceraven tag (`iceraven-2.53.0`) or select another release tag in the expected `iceraven-x.y.z` format.
4. Download the `cat-browser-<tag>` artifact after the workflow succeeds.

The workflow validates the branding script, checks the selected tag, prepares the upstream Android build, applies Cat Browser branding, builds `forkRelease`, and records APK SHA-256 hashes.

### Signing and release status

The current workflow uses the Android debug key for a development APK. It is **not** a production signing setup and cannot be treated as a stable update channel. A dedicated release-signing and update strategy is still required before distributing production builds.

## Upstream security and availability warning

Iceraven's own README states that it provides no guarantees of security, updates or stability and cautions users against relying on it as their primary browser. Its maintainers also state that the app may stop working on certified Android devices when the announced developer-identity requirements take effect.

Cat Browser inherits the current upstream base and its limitations. Treat these builds as experimental; do not rely on them as the only browser or for sensitive browsing until the upstream foundation, security-update process and signing model have been independently reviewed. This project does not attempt to bypass Android platform or developer-identity requirements.

## Near-term development order

1. Keep the upstream build reproducible and fail fast when its source layout changes.
2. Audit whether Iceraven is a suitable long-term base or whether Cat Browser should move to a directly maintained Fenix/GeckoView integration.
3. Build Cat-owned Android features on top of supported GeckoView APIs.
4. Add extension compatibility diagnostics and test supported add-ons before advertising compatibility.
5. Add Rust only for a concrete native component where it offers a measurable benefit.

## License

Iceraven / Firefox for Android is distributed under the Mozilla Public License 2.0. Cat Browser's own code and any later modifications must preserve applicable upstream notices and license obligations.
