# Cat Browser feature roadmap

Last reviewed: 2026-11-22

This is an implementation-status audit of the Android app, not a promise that every item is available today. A feature is marked implemented only when the repository contains its user-facing flow and the corresponding app behavior.

## Available today

- GeckoView-based HTTP(S) browsing with back, forward, reload and stop.
- Multiple tabs, tab overview, persistent groups for regular tabs, tab closing and URL-level restore for regular tabs.
- Local browsing history and bookmarks; bookmark titles can be edited, individual entries can be deleted, and the list can be sorted by name or date.
- Configurable home page, startup behavior, four search engines and light/dark/system themes.
- Android system Autofill on regular tabs; private tabs opt out. Cat does not store credentials or include its own password manager.
- Background web-media controls through Android notifications and lock-screen media actions; physical-device and OEM validation remains pending.
- Firefox WebExtension manager with AMO search/install, local signed XPI import, enable/disable/update/uninstall, action buttons and badges, popup sessions, options-page routing, supported tab lifecycle callbacks, basic `downloads.download()` and explicit per-extension private-browsing permission. Full API compatibility reporting and physical-device validation are not complete.
- Stable and Beta (GitHub pre-release) update channels with ABI selection and SHA-256 verification.
- Private tabs backed by GeckoView private-mode sessions. Private tabs are not persisted, private visits are not added to app history, and private tabs cannot add bookmarks.
- GeckoView download interception with a download list, progress, cancellation, filename sanitization, open/share/delete, and Android Downloads storage on Android 10+.
- Website permission prompts for GeckoView content permissions, camera/microphone media requests, and Android runtime permissions for location, camera, microphone and notifications.
- Settings screen for reviewing stored website permissions and resetting supported permissions.
- Clear browsing data controls for history, cookies/DOM storage/auth sessions, cache and site permissions. Open GeckoSessions are closed before the GeckoView storage clear call and the selected tab is recreated afterward.
- Find in Page with debounced search, highlight-all, previous/next match, match count when available and clear/close behavior.
- Per-tab desktop-site mode that sets both GeckoView's desktop User-Agent and viewport, with the preference persisted for regular tabs only.
- Save current page as PDF through GeckoView's PDF renderer and Android Storage Access Framework; print through GeckoView's Android print delegate.
- Address-bar secure indicator based on GeckoView's TLS/mixed-content callback rather than URL scheme alone.
- Native New Tab Page with bookmark shortcuts, recent normal-mode visits, and user-added website shortcuts.
- Local address-bar suggestions from same-mode open tabs, bookmarks and (normal mode only) history; no remote search suggestions are sent while typing.
- Swipe left/right on the unfocused address bar to switch adjacent tabs; Back/Forward navigation actions live in the overflow menu.
- Tab overview search, regular-tab groups and in-memory restoration of recently closed normal tabs (last URL only).
- GeckoView long-press context menus for HTTP(S) links and image/video/audio resources: open in a new tab with the source privacy mode and group, or copy the validated address. Text-selection controls remain GeckoView-native.

## Priority 0 — Browser essentials

| Feature | Current status | Acceptance requirements |
|---|---|---|
| Download manager | Persistent task metadata; transfer resume not available | GeckoView response stream, progress/cancel, filename sanitization, Android Downloads/SAF destination, open/share/delete and up to 100 non-private task records restored after restart. Active transfers restore as interrupted; no byte-stream resume/retry. |
| Site permissions | Basic; physical-device validation pending | GeckoView content/media prompts, Android runtime permission bridge, stored permission list and reset for supported permission types. Notification/site permission combinations and less-common permission types still need device testing. Some persistent-storage permissions are not revocable. |
| Clear browsing data | Basic; physical-device validation pending | Selectable history, cookies/DOM storage/auth sessions, cache and stored site permissions. All sessions close during GeckoView storage clearing, then the selected tab is recreated. IndexedDB/site-data coverage and device behavior need validation. |
| Private-tab lifecycle | Implemented; device verification pending | Verify cookies and web storage remain in GeckoView private mode, popups inherit private mode, and all private sessions close cleanly. |
| Browser stability | In progress | Smoke-test cold start, background/foreground, low-memory conditions, tab switching, process recreation and Android 13 installation. |

## Priority 1 — Everyday browser features

| Feature | Current status | Acceptance requirements |
|---|---|---|
| Find in page | Implemented; physical-device validation pending | Debounced search, highlight all matches, previous/next, match count when GeckoView has it, clear and close. |
| New Tab Page and address suggestions | Implemented; physical-device UX validation pending | Native start page with shortcuts and recent visits; suggestions use local tabs, bookmarks and normal-mode history only. |
| Address-bar tab switching | Implemented; physical-device gesture validation pending | Horizontal swipes switch adjacent tabs only while the address field is not focused. |
| Tab search, preview and reorder | Implemented in overview | Search current titles/URLs; capture a scaled GeckoView preview after successful page load; long-press drag reorder in the unfiltered list; recently closed normal tabs restore last URL only. Preview images are memory-only. |
| Desktop-site mode | Implemented; physical-device validation pending | Per-tab desktop User-Agent and viewport settings; preference is persisted for regular tabs only. |
| Full tab/session restore | Partial | Restore back/forward history and scroll state safely, with recovery for corrupted or stale state. |
| Bookmark manager | Implemented for local management and versioned JSON backup | Edit/delete/sort, up to 30 folders, move bookmarks, import/export through Android's system file picker, URL validation, duplicate handling and merge-without-overwrite. Duplicate URLs are collapsed to the newest saved timestamp when local bookmarks load. Import caps files at 5 MB and 10,000 bookmarks; imports reuse same-named folders and never replace an existing URL. Physical-device file-picker validation remains pending. |
| Extension toolbar, tabs, popup and downloads | Partial; matrix and physical-device validation pending | Browser/page action icons and badges, popup GeckoSessions, options pages, signed XPI import, private-browsing opt-in, selected tab callbacks, downloads progress/terminal updates and Android `saveAs` picker are integrated. Public GeckoView does not expose extension pause/resume/cancel callbacks; `UNIQUIFY` is implemented, `PROMPT` delegates to Android's document picker and `OVERWRITE` conservatively falls back to a unique name. Full Firefox API coverage and Chrome Store compatibility remain unsupported or unverified. |
| Android Autofill / password management | Partial | Android's configured Autofill service is integrated on regular tabs and disabled in private tabs. A Cat-owned password manager or credential vault is not implemented and requires a separate security design. |

## Priority 2 — Reading and advanced controls

| Feature | Current status | Acceptance requirements |
|---|---|---|
| Reader mode | Missing | Readability extraction, font/spacing controls and a clear exit action. |
| Translation | Missing | User-triggered translation with language selection and a clear privacy/data notice. |
| Media and picture-in-picture | Partial | Android media notification/lock-screen controls, audio focus and playback actions are integrated. GeckoView 160 experimental PiP is integrated but still needs physical-device/vendor validation. |
| Page actions | Partial; print/PDF/share/copy/open externally implemented | Save a page as PDF through SAF, print through Android PrintManager, share the current HTTP(S) URL through Android Sharesheet, copy it to the clipboard, or choose another installed app to open it. QR remains future work; physical-device validation is pending. |
| Per-site privacy controls | Missing | Tracking protection controls, site data inspection and a visible explanation of protection limits. |

## Release rule

A feature is not considered complete solely because a button or screen exists. Its underlying behavior must be implemented, tested, and validated on supported Android devices. GeckoView API availability does not by itself mean Cat Browser has integrated a complete browser workflow.
