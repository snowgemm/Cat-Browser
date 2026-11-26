#!/usr/bin/env python3
"""Fail CI when Cat Browser's core runtime/session boundaries drift."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/io/github/snowgemm/catbrowser"
WORKFLOW = ROOT / ".github/workflows/android-ci.yml"


def fail(message: str) -> None:
    print(f"ARCHITECTURE VIOLATION: {message}", file=sys.stderr)
    raise SystemExit(1)


def main() -> None:
    kotlin_files = sorted(SOURCE.rglob("*.kt"))
    if not kotlin_files:
        fail("no Kotlin production sources found")

    creation_sites = [
        path.relative_to(ROOT).as_posix()
        for path in kotlin_files
        if "GeckoRuntime.create(" in path.read_text(encoding="utf-8")
    ]
    expected_owner = "app/src/main/java/io/github/snowgemm/catbrowser/CatBrowserApplication.kt"
    if creation_sites != [expected_owner]:
        fail(f"GeckoRuntime must be created only by CatBrowserApplication; found {creation_sites}")

    application = (SOURCE / "CatBrowserApplication.kt").read_text(encoding="utf-8")
    if "BrowserController(this, geckoRuntime)" not in application:
        fail("CatBrowserApplication must inject its process-wide GeckoRuntime into BrowserController")

    controller = (SOURCE / "browser/BrowserController.kt").read_text(encoding="utf-8")
    required_controller_contract = (
        "private val runtime: GeckoRuntime",
        "GeckoSessionSettings",
        ".usePrivateMode(tab.isPrivate)",
    )
    for contract in required_controller_contract:
        if contract not in controller:
            fail(f"BrowserController is missing required session contract: {contract}")

    tests = ROOT / "app/src/test/java/io/github/snowgemm/catbrowser/browser"
    for test_name in ("BrowserTabPrivacyTest.kt", "BrowserMemoryTrimPolicyTest.kt"):
        if not (tests / test_name).is_file():
            fail(f"required privacy/lifecycle regression test is missing: {test_name}")

    workflow = WORKFLOW.read_text(encoding="utf-8")
    required_gates = (
        ":app:lintDebug",
        ":app:ktlintCheck",
        ":app:testDebugUnitTest",
        ":app:assembleDebug",
        "verify_abi_apks.py",
        "apksigner",
        "verify --verbose",
    )
    for gate in required_gates:
        if gate not in workflow:
            fail(f"Android CI is missing required quality gate: {gate}")

    print("Cat Browser architecture guard passed: runtime ownership, session privacy, regression tests, and CI gates.")


if __name__ == "__main__":
    main()
