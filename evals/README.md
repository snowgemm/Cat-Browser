# Cat Browser Agent Evaluation Pack

This pack evaluates the repository artifact produced by a coding agent against explicit Cat Browser acceptance scenarios.

## Run

```bash
python3 -m unittest discover -s scripts -p 'test_*.py'
python3 scripts/verify_architecture.py
python3 scripts/run_agent_evals.py
```

The runner writes `build/agent-evals/report.json` and appends a compact table to the GitHub Actions job summary. CI uploads the JSON report as an artifact.

## Coverage

Scenario prompts cover GeckoView runtime ownership, private browsing isolation, the reviewed Android permission boundary, system motion preferences, and CI security. Each case checks source structure, regression-test presence, the Android manifest, or workflow policy.

Kotlin/JVM tests, Android Lint, KtLint, APK/ABI/signature checks, benchmark compilation, and physical-device verification remain separate evidence gates.

## Scope boundary

This is an artifact-based acceptance evaluator. It does **not** invoke a language model, inspect hidden reasoning, or claim to measure model intelligence. Model-behavior evaluation requires a real agent adapter and held-out tasks with independent graders. A passing static assertion is not proof that an agent reasoned correctly.
