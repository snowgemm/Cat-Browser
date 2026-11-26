# Agent instructions

## Required workflow
1. Read `README.md`, `CONTRIBUTING.md`, `docs/ARCHITECTURE.md`, and the relevant feature contract before editing.
2. Inspect the current implementation, tests, Gradle configuration, GeckoView version, and recent CI results. Verify GeckoView APIs against the pinned version and official documentation.
3. State the intended behavior, affected files, risks, and verification plan before a non-trivial change.
4. Prefer the smallest change that preserves existing architecture and user-visible behavior.
5. Add or update deterministic tests for every changed policy or behavior. Do not weaken or delete tests to make a change pass.
6. Run the architecture guard, Android Lint, KtLint, JVM tests, debug build, ABI validation, and relevant benchmark compilation.
7. Re-read the complete diff and surrounding code. Report commands and results; distinguish local, CI, and physical-device verification.

## Enforced architecture boundaries
- `CatBrowserApplication` owns the single process-wide `GeckoRuntime`; inject it into `BrowserController`.
- `BrowserController` owns browser tab/session coordination. Each tab owns its own `GeckoSession`.
- Set private mode when constructing a session from the tab's privacy state. Never persist private-tab identifiers, URLs, history, or bookmarks.
- Keep GeckoView APIs compatible with the exact dependency pinned in `app/build.gradle.kts`.
- Keep AI tool calls behind the existing explicit approval flow. Never send private-tab content or credentials to the AI service.
- Preserve ABI packaging checks, APK signature verification, and the existing KtLint baseline policy. Never refresh a baseline merely to hide newly introduced violations.

## Change safety
- Do not add permissions, network endpoints, dependencies, or telemetry without a documented need and security/privacy review.
- Do not create a new GeckoRuntime or bypass BrowserController ownership.
- Do not claim browser-extension compatibility beyond the verified compatibility matrix.
- Never claim a device behavior is verified unless it was exercised on a physical device.


## Agent evaluation protocol

- Select the matching scenario in `evals/agent-evals.json` before changing a feature.
- After implementation, run `python3 scripts/run_agent_evals.py` and the relevant Kotlin/JVM, lint, build, ABI, and benchmark checks.
- Do not weaken an evaluation assertion or remove its regression test merely to obtain a pass. Update a scenario only when the product contract changes and the change is reviewed.
- Report the score, failed cases, CI run, and physical-device limitations.
- The pack grades repository artifacts; it does not score an LLM's hidden reasoning.
