# Cat Browser

An independent Android browser whose application code, interface and browsing experience are maintained in this repository. Cat owns browser UI, navigation and local browser state; Mozilla GeckoView is embedded as the browser engine.

## Current implementation

- Kotlin + Jetpack Compose + Material 3.
- Mozilla GeckoView Stable, pinned to `160.0.20261104181552`.
- Multiple tabs (up to 20), with tab switching and closing.
- Links requesting a new browsing context (`target="_blank"` / `window.open`) create a new Cat tab.
- Tab metadata is stored locally. On app restart, tabs reopen at their last HTTP(S) URL.
- Native New Tab Page with bookmark/recent website shortcuts, same-origin HTTPS favicons and no third-party favicon proxy.
- Optional Cat AI assistant with a fixed browser-tool allowlist. Every tool call requires in-app approval; private tabs and page contents are excluded.
- Gemini 3.8 Flash assistant through a Supabase Edge Function. Android creates an anonymous Supabase session automatically; Gemini and Supabase secret keys remain server-side.
- Address/search field with DuckDuckGo search for plain text and local suggestions from same-mode tabs, bookmarks and normal-mode history.
- Swipe left/right on the unfocused address bar to switch adjacent tabs; Back/Forward actions are in the overflow menu.
- Tab overview search and restoration of recently closed normal tabs (last URL only, in-memory).
- Page-load progress and basic load/process error feedback.
- Local bookmarks and recent browsing history, stored on-device.
- HTTP(S) links can be opened from other Android apps.
- Address resolution validates hostnames, IPv4, IPv6 and ports; internationalized domain names are converted to ASCII.
- ABI-specific APKs are built for `arm64-v8a`, `armeabi-v7a` and `x86_64`; CI checks native-library contents, APK signatures and SHA-256 checksums.

## ABI-specific APK distribution

GeckoView includes large native engine libraries. The app therefore produces separate APKs for three supported Android ABIs rather than shipping all native libraries in one universal APK:

| APK | Intended devices |
|---|---|
| `cat-browser-arm64-v8a-debug.apk` | Most modern 64-bit ARM Android phones |
| `cat-browser-armeabi-v7a-debug.apk` | 32-bit ARM Android devices |
| `cat-browser-x86_64-debug.apk` | x86-64 Android devices and compatible emulators |

There is deliberately no `x86` 32-bit APK: the pinned GeckoView dependency does not currently provide the key engine libraries for that ABI in the inspected dependency artifact. Each generated APK must contain native libraries for exactly one ABI and key GeckoView libraries (`libmozglue.so`, `libxul.so` and `libgkcodecs.so`). CI fails if those checks do not pass.

GitHub Actions uploads **three separate, unarchived artifacts**: one ARM64 APK, one ARMv7 APK and one x86_64 APK. Each artifact is a standalone `.apk` file; there is no combined ZIP. SHA-256 checksums are printed in the workflow run summary. The artifacts are retained for 14 days.

These are development/debug builds, not production-signed stable releases. The APKs use the standard debug signing configuration.

## Restoration and local data

Cat stores tab IDs, titles, last URLs, bookmarks and recent history in app-private local preferences. This data is not sent to a Cat account or backend.

After a process restart, each saved tab is reopened at its last URL. The current implementation does **not** persist GeckoSession's full state: per-tab back/forward stacks, scroll position, form state and in-page state are not restored. Clearing app data removes this local data.

## Current limitations

The browser foundation is implemented, but these areas remain incomplete or require validation:

- Complete GeckoSession state restoration, including back/forward stacks, scroll position, form state and in-page state.
- Recently closed tabs restore the last URL only and are held in memory; they do not restore a complete session.
- Tab overview captures a downscaled preview from the active `GeckoView` surface after a successful load. Previews are in-memory only and may be blank until a tab has been displayed; they are not saved across process restarts.
- Drag-and-drop reordering is available in the unfiltered tab overview. Filtered/search results intentionally disable reorder to avoid ambiguous index changes.
- The app download manager persists up to 100 non-private task records. Active tasks interrupted by process death are restored as interrupted metadata; byte-stream transfer is not resumed.
- `downloads.download()` supports progress, terminal updates, Android `saveAs` document picking and safe duplicate-name handling. `OVERWRITE` currently falls back to a unique filename; `PROMPT` uses Android's document picker, so full Firefox Desktop conflict semantics are not claimed. Extension-driven pause/resume/cancel are not available through the public GeckoView API surface.
- Firefox WebExtensions support is API-by-API and partial. Chrome Web Store/CRX support and complete Firefox Desktop API parity are not provided. See [Android extension support](docs/ANDROID-EXTENSIONS.md) and the [physical-device test matrix](docs/EXTENSION-TEST-MATRIX.md).
- Physical-device smoke tests for permissions, private-session boundaries, extension popup behavior, downloads and tab previews/reordering remain required.
- Cat AI uses the Supabase Edge Function in supabase/functions/cat-ai/ and Gemini Interactions API with store=false. The app does not require a user-entered gateway URL or access token. End-to-end runtime testing on physical Android devices is still required. The embedded assistant is not the ChatGPT Android app remotely controlling the browser.

CI build success does not replace installation and runtime smoke testing on supported physical Android devices.

## Technology

| Area | Choice |
|---|---|
| Platform | Android |
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Browser engine | GeckoView Stable |
| Search provider | DuckDuckGo |
| Local storage | App-private SharedPreferences |
| Cat AI session | Supabase anonymous Auth; refresh token encrypted with Android Keystore AES-GCM; Gemini key stays in Supabase secrets |
| Extensions | Firefox WebExtensions via GeckoView; Android-compatible catalog and manager are implemented, compatibility remains API-dependent |

GeckoView is packaged inside each APK. Engine security fixes reach Cat only after the dependency is reviewed, updated, tested and shipped in a new app build.

## Build

Requirements: JDK 17, Android SDK Platform 37.2, Android SDK Build-Tools 36.0.0 and the Gradle 9.6.0 Wrapper.

The app compiles against Android API 37.2 to satisfy the published minimum compile SDK requirements of GeckoView and the selected Compose libraries. It currently targets API 36; compile SDK and target SDK are independent settings.

Run from the repository root:

```bash
./gradlew :app:clean
./gradlew :app:testDebugUnitTest :app:assembleDebug
python3 scripts/verify_abi_apks.py \
  --input app/build/outputs/apk/debug \
  --output dist/abi-apks
```

Gradle writes the ABI-specific APKs to `app/build/outputs/apk/debug/`. The verification script checks the native ABI split, key GeckoView libraries and archive integrity, then copies the APKs to `dist/abi-apks/` using the stable filenames listed above and writes SHA-256 checksums. GitHub Actions additionally verifies each APK signature before uploading each APK as a separate unarchived artifact.

The debug APKs are not production-signed and must not be treated as stable releases or an update channel.

## Extension support

The target is Firefox WebExtensions through GeckoView. Cat integrates action buttons, popup sessions, options pages, signed XPI import and selected tab-management callbacks. This does not imply compatibility with every Firefox add-on, arbitrary Chrome extensions or the Chrome Web Store. Read [Android extension support](docs/ANDROID-EXTENSIONS.md) for the supported API surface and known limitations before advertising compatibility.

## Architecture

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for component ownership, session lifecycle and current limits.

## Third-party components

- GeckoView: Mozilla Public License 2.0 (MPL-2.0).
- Gradle Wrapper: Gradle 9.6.0, Apache License 2.0.

Cat Browser is an independent project and is not affiliated with Mozilla.
