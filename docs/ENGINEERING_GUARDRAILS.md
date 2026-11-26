# Engineering guardrails

These checks turn the architecture's highest-risk invariants into executable CI rules. They complement—not replace—code review, unit tests, CodeQL, Android Lint, KtLint, ABI validation, and physical-device testing.

## Enforced invariants

1. Exactly one Kotlin source location creates GeckoRuntime, in CatBrowserApplication.
2. CatBrowserApplication injects that runtime into BrowserController.
3. BrowserController creates GeckoSession settings from the tab's privacy state, rather than relying on prompt instructions or caller discipline.
4. CI continues to run Android Lint, KtLint, JVM tests, APK build, ABI validation, and APK signature checks.
5. Privacy/lifecycle regression tests remain present.

The executable checks intentionally enforce narrow architectural invariants. They do not ban useful language constructs globally or treat every static rule as proof of correctness.

## Agent evaluation cases

For every change, the implementing agent must be able to demonstrate:

- **Scope fidelity:** only requested behavior and necessary supporting files changed.
- **Architecture:** runtime/session ownership and private-mode boundaries remain intact.
- **Regression:** relevant existing tests pass and new behavior has deterministic tests.
- **Security/privacy:** permissions, private browsing, AI approval, and data boundaries are unchanged unless explicitly reviewed.
- **Evidence:** build/test/lint results are reported accurately; device-only behavior is marked unverified until tested on hardware.

The guard is mutation-tested: valid architecture fixtures must pass, while fixtures that introduce a second GeckoRuntime, remove private-session binding, or drop a CI gate must fail. These tests run with the existing Python validator suite.

## Required verification

```bash
python3 scripts/verify_architecture.py
./gradlew --no-daemon :app:lintDebug :app:ktlintCheck :macrobenchmark:ktlintCheck
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug
python3 -m unittest discover -s scripts -p 'test_*.py'
./gradlew --no-daemon :app:assembleBenchmark :macrobenchmark:assembleBenchmark
```

CI remains read-only. A failed gate must be fixed at its source; do not bypass it by weakening the check or refreshing a baseline.
