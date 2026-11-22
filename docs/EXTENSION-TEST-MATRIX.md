# Android WebExtension compatibility test matrix

See [WEBEXTENSIONS-COMPATIBILITY.md](WEBEXTENSIONS-COMPATIBILITY.md) for API-by-API implementation ownership and evidence status.

Last audit: 2026-11-22  
Target engine: GeckoView `160.0.20261104181552` (see `app/build.gradle.kts`).  
Current code state: tab preview/reorder and Android `saveAs` are implemented; physical-device results below are intentionally **PENDING** until a test is run and recorded.

## Test candidates

These are real Mozilla Add-ons with Firefox for Android listings. Their listing is evidence that the add-on is offered for Firefox Android, **not** evidence that it works in Cat.

| Add-on | What this candidate exercises | Cat result |
|---|---|---|
| [uBlock Origin](https://addons.mozilla.org/en-US/android/addon/ublock-origin/) | Content filtering, network request interception, navigation events, tab access, extension storage, popup/action and filter-list updates | **PENDING — not tested in Cat** |
| [Dark Reader](https://addons.mozilla.org/en-US/android/addon/darkreader/) | Content scripts, CSS injection, storage, dynamic DOM updates, popup, options page and per-site settings | **PENDING — not tested in Cat** |
| [SponsorBlock](https://addons.mozilla.org/en-US/android/addon/sponsorblock/) | YouTube content scripts, background/runtime messaging, storage, external network requests, popup and permission scoping | **PENDING — not tested in Cat** |
| [Bitwarden Password Manager](https://addons.mozilla.org/en-US/android/addon/bitwarden-password-manager/) | Larger extension package, storage, tabs/navigation, clipboard and form interaction; native integration and autofill require separate verification | **PENDING — not tested in Cat** |

At test time, record the exact AMO version and XPI checksum. Do not pin an old add-on build solely because it passed earlier: re-run the matrix after a relevant GeckoView or add-on update.

## Device matrix

| Device class | Android | ABI | Purpose | Result |
|---|---|---|---|---|
| Physical phone (primary) | 13 | `arm64-v8a` | Baseline installation, permissions, navigation, popups, private tabs, memory and downloads | **PENDING** |
| Physical phone | 14 or 15 | `arm64-v8a` | OS permission changes, SAF picker, lifecycle/background behavior | **PENDING** |
| Physical phone | 10–12 | `armeabi-v7a` or `arm64-v8a` | Legacy storage / runtime permission compatibility where supported by the APK | **PENDING** |
| Android emulator | Current supported image | `x86_64` | Repeatable smoke tests and crash reproduction; does not replace physical-device checks | **PENDING** |

## Test protocol

Run each candidate against the same Cat build and record the commit SHA and bundled GeckoView version. Use non-sensitive test websites and synthetic test data; never enter a real password or personal account into an unverified browser build.

| ID | Test | Pass condition |
|---|---|---|
| EXT-00 | AMO catalog discovery, search, official listing links and unavailable-XPI state | Default catalog loads; latest search wins; listing opens only on addons.mozilla.org; entries without an approved XPI remain visible but cannot be installed |
| EXT-01 | Install from AMO catalog and signed local XPI import | Install completes; signature/permission prompt is correct; invalid package is rejected |
| EXT-02 | Required and optional permissions | Denial is respected; optional permission is not silently granted; permission changes are visible |
| EXT-03 | Enable / disable / update / uninstall | Toolbar actions and delegates attach/detach correctly; no stale popup or callback remains |
| EXT-04 | Browser/page action | Icon, title, badge, enabled state and click behavior match the extension's manifest/runtime updates |
| EXT-05 | Popup and options page | Popup loads in its own GeckoSession, closes cleanly and options page opens in a Cat tab |
| EXT-06 | Content scripts | Script runs only on granted origins, observes dynamic page changes and does not leak into unrelated pages |
| EXT-07 | Storage and background lifecycle | State persists according to the extension API; background/service-worker restart does not corrupt state |
| EXT-08 | Tab API | Supported create/update/remove calls map to Cat tabs; unsupported options reject explicitly rather than succeeding silently |
| EXT-09 | Network APIs | Blocking/filtering/redirect behavior matches expected GeckoView semantics; inspect page load and console errors |
| EXT-10 | Downloads | `downloads.download()` progress/terminal events are correct; `saveAs`/`PROMPT` open Android picker; `UNIQUIFY` produces a free name; `OVERWRITE` preserves the existing file and falls back to a free name; cancellation rejects cleanly |
| EXT-11 | Private browsing | Extension access is denied by default; after opt-in, private tabs remain excluded from persistent Cat history/download metadata |
| EXT-12 | Lifecycle and stress | Switch 1/5/10/20 tabs, background/foreground, rotate, process-restart, low-memory and network loss without crashes or leaked views |
| EXT-13 | Extension browsing data | History removal respects the Unix-second cutoff; only recent completed regular-mode download metadata is removed, not output files; active/private downloads and bookmarks remain untouched; form-data/password requests reject explicitly; verify Gecko-owned data types and private-mode behavior |

## Results log

Add one row per add-on × device × Cat commit. A pass for one row does not imply general compatibility.

| Date | Cat commit | GeckoView | Add-on + version | Device / Android / ABI | Install | Core APIs | Popup/options | Private | Downloads | Outcome / issue |
|---|---|---|---|---|---|---|---|---|---|---|
| — | — | — | uBlock Origin — pending version | — | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | PENDING |
| — | — | — | Dark Reader — pending version | — | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | PENDING |
| — | — | — | SponsorBlock — pending version | — | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | PENDING |
| — | — | — | Bitwarden — pending version | — | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | PENDING |

## Acceptance rule

An add-on can be labelled **Verified on a device** only after its exact version passes the applicable protocol on a recorded physical device. Use **Installable only** when signature installation succeeds but behavior is incomplete or untested. Use **Partial** with explicit failed API areas, and **Unsupported** when a required browser API is unavailable. Never infer compatibility from AMO listing or XPI installation alone.

## References

- [GeckoView WebExtensions guide](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/web-extensions.html)
- [GeckoView WebExtension API](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/WebExtension.html)
- [GeckoView Download API](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/WebExtension.Download.html)
- [GeckoView GeckoView.capturePixels API](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/GeckoView.html)
- [uBlock Origin on AMO for Android](https://addons.mozilla.org/en-US/android/addon/ublock-origin/)
- [Dark Reader on AMO for Android](https://addons.mozilla.org/en-US/android/addon/darkreader/)
- [SponsorBlock on AMO for Android](https://addons.mozilla.org/en-US/android/addon/sponsorblock/)
- [Bitwarden on AMO for Android](https://addons.mozilla.org/en-US/android/addon/bitwarden-password-manager/)
