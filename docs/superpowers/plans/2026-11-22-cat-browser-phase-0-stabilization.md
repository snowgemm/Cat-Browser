# Cat Browser Phase 0 — Stabilization and source-of-truth plan

**Goal:** Restore a trustworthy CI baseline and make the browser's architecture, security posture, GeckoView version decision, and Firefox WebExtensions claims consistent.

**Architecture:** Keep Kotlin + Jetpack Compose + GeckoView. GeckoView remains the rendering engine and WebExtensions runtime; Cat owns Android browser UX and embedder integration. Fix only proven problems; no engine migration or second extension runtime.

**Baseline:** `main` at `3de44c79996383da8be5d9204f1516a6a1b3e1bf`; GeckoView `160.0.20261104181552`.

## Constraints

- Keep Android-only scope, current engine and dependency until candidate release compatibility is verified.
- Do not weaken KtLint or replace its baseline to make CI green.
- Preserve private-mode boundaries, XPI Mozilla signature validation and existing data formats.
- Do not claim API support without an end-to-end test or exact public API evidence.
- Do not copy Mozilla/Fenix code or assets while Cat's own source license remains undeclared.
- Use the user's established direct-main, single-final-commit workflow.

## Tasks

1. **Restore KtLint.** Restore the formatted extension permission-dialog block in `ExtensionsScreen.kt` without changing permission semantics or the baseline archive; rerun full KtLint.
2. **Reconcile status docs.** Align GeckoView capabilities, architecture, feature roadmap and extension process recovery with actual source code. Preserve physical-device validation as pending.
3. **Review cleartext policy.** Verify native service endpoints are HTTPS and assess `usesCleartextTraffic=true`. Do not change it until a physical-device test proves GeckoView HTTP navigation remains functional.
4. **Audit GeckoView pin.** Compare the pinned build with current Mozilla Javadocs and changelog. Do not upgrade without artifact, API, ABI and regression verification.
5. **Create the WebExtensions API evidence ledger.** Classify APIs by Gecko-native, embedder delegate, Cat integration, Android limitation, unavailable or unknown evidence; map each to tests.
6. **Run CI/security gates.** Android Lint, KtLint, JVM tests, Python tests, debug/benchmark builds, ABI validation, APK signatures and AIScan must run on the final tree. Record physical-device checks as pending where not executed.
7. **License gate.** Keep Cat's license status explicit; do not copy third-party source/assets until the owner selects a license and reuse conditions are verified.

## Completion criteria

- KtLint is green without weakening configuration or changing its baseline.
- Android Lint, KtLint, JVM/Python tests, debug/benchmark builds, ABI validation and signing pass.
- Docs agree with code and separate integrated behavior from physical-device verification.
- Cleartext and GeckoView pin decisions are evidence-based.
- Relevant WebExtensions APIs have conservative evidence statuses and test mappings.
- No unlicensed upstream code/assets are copied; Cat's own license remains an explicit owner decision.
