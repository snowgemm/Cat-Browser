## Summary

Describe the user problem, expected behavior, and why this change is needed.

## Scope and risk

- [ ] Browser UI / navigation
- [ ] GeckoView integration or lifecycle
- [ ] Tabs, sessions, private browsing, or stored browsing data
- [ ] Extensions and WebExtensions compatibility
- [ ] Downloads, permissions, or site security
- [ ] Cat AI / gateway / tool approval
- [ ] Updates, packaging, or release pipeline
- [ ] Documentation / CI only

**Risk and affected boundaries:** Describe sensitive state, external APIs, permissions, dependencies, and compatibility impact.

## Validation evidence

- [ ] Architecture guard and mutation tests pass.
- [ ] Android Lint and KtLint pass.
- [ ] Relevant JVM tests pass; new behavior has regression coverage.
- [ ] Debug APK builds and ABI validation/signature checks pass where applicable.
- [ ] Benchmark or performance checks run where relevant.
- [ ] Physical-device tests are recorded separately; untested device behavior is identified.

**Commands and results:** Include exact commands, pass/fail results, and links to CI runs.

## Security and privacy review

- [ ] Private tabs remain isolated from regular browsing data and AI context.
- [ ] No new permission, telemetry, storage, or network endpoint was added without review.
- [ ] AI browser actions retain explicit user approval.
- [ ] GeckoView and extension capabilities are not overstated.
- [ ] No credentials, cookies, private URLs, or unredacted browsing data appear in logs or fixtures.

## Independent review

- [ ] The complete diff was reviewed independently of the implementing agent.
- [ ] No unrelated changes, weakened tests, or unexplained generated files remain.
- [ ] Remaining limitations and unverified behavior are documented.

## Screenshots / evidence

Attach sanitized screenshots, recordings, benchmark output, or test artifacts when useful.
