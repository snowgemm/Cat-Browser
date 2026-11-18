# Cat Browser feature roadmap

Last reviewed: 2026-11-18

This is an implementation-status audit of the Android app, not a promise that every item is available today. A feature is marked implemented only when the repository contains its user-facing flow and the corresponding app behavior.

## Available today

- GeckoView-based HTTP(S) browsing with back, forward, reload and stop.
- Multiple tabs, tab overview, tab closing and URL-level restore for regular tabs.
- Local browsing history and bookmarks.
- Configurable home page, startup behavior, four search engines and light/dark/system themes.
- Early WebExtension management through the Mozilla Add-ons catalog: search, install, enable/disable, update and uninstall. Full browser toolbar actions and extension compatibility are not complete.
- Stable and Beta (GitHub pre-release) update channels with ABI selection and SHA-256 verification.
- Private tabs backed by GeckoView private-mode sessions. Private tabs are not persisted, private visits are not added to app history, and private tabs cannot add bookmarks.
- GeckoView download interception with a download list, progress, cancellation, filename sanitization, open/share/delete, and Android Downloads storage on Android 10+.
- Website permission prompts for GeckoView content permissions, camera/microphone media requests, and Android runtime permissions for location, camera, microphone and notifications.
- Settings screen for reviewing stored website permissions and resetting supported permissions.
- Clear browsing data controls for history, cookies/DOM storage/auth sessions, cache and site permissions. Open GeckoSessions are closed before the GeckoView storage clear call and the selected tab is recreated afterward.
- Find in Page with debounced search, highlight-all, previous/next match, match count when available and clear/close behavior.

## Priority 0 — Browser essentials

| Feature | Current status | Acceptance requirements |
|---|---|---|
| Download manager | Basic; in-memory task list | GeckoView response stream, progress, cancel, filename sanitization, Android Downloads on Android 10+, app-scoped downloads on Android 8/9, open/share/delete. No pause/resume, retry or persistent download-task restoration yet. |
| Site permissions | Basic; physical-device validation pending | GeckoView content/media prompts, Android runtime permission bridge, stored permission list and reset for supported permission types. Notification/site permission combinations and less-common permission types still need device testing. Some persistent-storage permissions are not revocable. |
| Clear browsing data | Basic; physical-device validation pending | Selectable history, cookies/DOM storage/auth sessions, cache and stored site permissions. All sessions close during GeckoView storage clearing, then the selected tab is recreated. IndexedDB/site-data coverage and device behavior need validation. |
| Private-tab lifecycle | Implemented; device verification pending | Verify cookies and web storage remain in GeckoView private mode, popups inherit private mode, and all private sessions close cleanly. |
| Browser stability | In progress | Smoke-test cold start, background/foreground, low-memory conditions, tab switching, process recreation and Android 13 installation. |

## Priority 1 — Everyday browser features

| Feature | Current status | Acceptance requirements |
|---|---|---|
| Find in page | Implemented; physical-device validation pending | Debounced search, highlight all matches, previous/next, match count when GeckoView has it, clear and close. |
| Desktop-site mode | Missing | Per-tab toggle, correct viewport configuration and persisted preference for regular tabs only. |
| Full tab/session restore | Partial | Restore back/forward history and scroll state safely, with recovery for corrupted or stale state. |
| Bookmark manager | Basic | Edit titles, folders or collections, sort, import/export and duplicate handling. |
| Extension toolbar | Partial | Extension action buttons, popup routing, options pages, compatibility status and explicit private-browsing access controls. |
| Autofill/password management | Missing | Secure storage, Android autofill integration, user consent and biometric/device-authentication safeguards where appropriate. |

## Priority 2 — Reading and advanced controls

| Feature | Current status | Acceptance requirements |
|---|---|---|
| Reader mode | Missing | Readability extraction, font/spacing controls and a clear exit action. |
| Translation | Missing | User-triggered translation with language selection and a clear privacy/data notice. |
| Media and picture-in-picture | Partial | Consistent media controls, fullscreen handling and Android PiP integration where supported. |
| Page actions | Partial | Share link, copy URL, print/PDF, open externally and QR display with correct Android intents. |
| Per-site privacy controls | Missing | Tracking protection controls, site data inspection and a visible explanation of protection limits. |

## Release rule

A feature is not considered complete solely because a button or screen exists. Its underlying behavior must be implemented, tested, and validated on supported Android devices. GeckoView API availability does not by itself mean Cat Browser has integrated a complete browser workflow.
