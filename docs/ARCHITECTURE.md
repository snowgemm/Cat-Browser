# Cat Browser architecture

## Product boundary

Cat Browser is the Android application. It owns the browsing experience and is responsible for tab/session management, navigation UI, local data, permissions, downloads, extension management and privacy controls. GeckoView is an embedded browser engine, not a complete browser application.

## Current foundation

- `CatBrowserApplication` creates one process-wide `GeckoRuntime`.
- `BrowserScreen` owns the initial `GeckoSession` and navigation controls.
- `BrowserUrlResolver` recognizes HTTP(S) URLs, common hostnames and local addresses; plain text becomes a DuckDuckGo search.
- `GeckoSession.Loader` is used for navigation.
- The initial UI uses a restrained dark palette and a single green accent; no gradient or blur dependency is introduced.

## Lifecycle rules

1. Create one `GeckoRuntime` per application process.
2. Each future browser tab owns one `GeckoSession`.
3. Open a session before attaching it to a `GeckoView`.
4. Clear delegates and close a session when its owning tab is permanently removed.
5. Save/restore session state before claiming reliable tab recovery.
6. Keep browsing history, search text and session state local unless a separately reviewed feature explicitly requires otherwise.

## Planned component boundaries

| Component | Responsibility |
|---|---|
| Browser UI | Address input, tab switcher, navigation controls and menus |
| Runtime | GeckoRuntime and process lifecycle |
| Tab manager | GeckoSession collection, active tab and session restore |
| Site permissions | Explicit prompts and persisted per-origin decisions |
| Extension manager | WebExtensionController operations and permission review |
| Local data | Room for structured records; DataStore for preferences |
| Privacy controls | GeckoView tracking protection and per-site controls |

## Current limitations

- One session only; there is no tab manager.
- No private-browsing mode or persistent session recovery.
- No history, bookmarks or downloads UI.
- No site permission prompt UI has been added yet.
- No extension catalog or extension management UI.
- No separate ad-blocking engine or Cat privacy dashboard.
- No analytics, account backend or browsing-history telemetry is introduced by this foundation.
