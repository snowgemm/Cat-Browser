# Cat Browser Phase 1 — WebExtensions browsing-data bridge

**Goal:** Implement the first missing embedder-owned Firefox WebExtensions API against the existing GeckoView 160 runtime, without claiming broad Firefox parity.

**Architecture:** Keep GeckoView as the WebExtensions engine. Cat owns its persisted visit history and download metadata; GeckoView remains responsible for engine-owned browser data.

## Scope and safety boundaries

- Register WebExtension.BrowsingDataDelegate for installed extensions and detach it when an extension is disabled or removed.
- Expose only Cat-owned history and download metadata in browsingData.settings.get().
- Apply the API's Unix-second cutoff safely to Cat's millisecond timestamps.
- Remove download-history records only. Never delete the downloaded files as a side effect of clearing extension browsing data.
- Preserve bookmarks and active downloads.
- Reject form-data and password cleanup requests because Cat does not own the Android Autofill provider or a password vault.
- Keep private-mode storage boundaries unchanged.

## Tasks

1. Add unit-tested timestamp and history filtering policy.
2. Implement time-scoped history removal with protection against an in-flight history load reintroducing removed entries.
3. Add download metadata cleanup for terminal tasks only, leaving output files untouched.
4. Register/detach the GeckoView delegate per extension.
5. Update the API ledger and add EXT-13 physical-device coverage.

## Verification

- Android Lint, KtLint, JVM tests, ABI-split APK builds, benchmark builds, ABI checks, APK signature verification and CodeQL must pass on the final commit.
- Physical-device verification remains pending until an actual Cat build and test add-on are run on a recorded device.
- The ledger remains **Limited** until EXT-13 and Gecko-owned data-type behavior are verified on device.

## Next API areas

After this slice, evaluate the exact GeckoView 160 exposure and Cat storage ownership for bookmarks/history, context-menu support, notifications and runtime/storage lifecycle. Implement each as a separate tested slice; do not infer support from the corresponding Firefox API existing in Gecko itself.
