#!/usr/bin/env python3
"""Validate Cat Browser's high-risk architecture and CI invariants."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def check_architecture(root: Path) -> list[str]:
    failures: list[str] = []
    manifest_path = root / "app/src/main/AndroidManifest.xml"
    android_ns = "{http://schemas.android.com/apk/res/android}"
    allowed_permissions = {
        "android.permission.INTERNET",
        "android.permission.REQUEST_INSTALL_PACKAGES",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.FOREGROUND_SERVICE",
        "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
    }
    try:
        manifest = ET.parse(manifest_path).getroot()
        requested_permissions = {
            node.attrib.get(android_ns + "name", "<unnamed>")
            for node in manifest
            if node.tag.rsplit("}", 1)[-1].startswith("uses-permission")
        }
        unexpected_permissions = requested_permissions - allowed_permissions
        if unexpected_permissions:
            failures.append(
                "Android manifest requests permissions outside the reviewed allowlist: "
                f"{sorted(unexpected_permissions)}"
            )
    except (ET.ParseError, OSError) as error:
        failures.append(f"Android manifest cannot be parsed: {error}")

    source = root / "app/src/main/java/io/github/snowgemm/catbrowser"
    kotlin_files = sorted(source.rglob("*.kt")) if source.exists() else []
    if not kotlin_files:
        return ["no Kotlin production sources found"]

    creation_sites = [
        path.relative_to(root).as_posix()
        for path in kotlin_files
        if "GeckoRuntime.create(" in path.read_text(encoding="utf-8")
    ]
    expected_owner = "app/src/main/java/io/github/snowgemm/catbrowser/CatBrowserApplication.kt"
    if creation_sites != [expected_owner]:
        failures.append(
            "GeckoRuntime must be created only by CatBrowserApplication; "
            f"found {creation_sites}"
        )

    application_path = source / "CatBrowserApplication.kt"
    application = application_path.read_text(encoding="utf-8") if application_path.is_file() else ""
    if "BrowserController(this, geckoRuntime)" not in application:
        failures.append("CatBrowserApplication must inject its process-wide GeckoRuntime into BrowserController")

    controller_path = source / "browser/BrowserController.kt"
    controller = controller_path.read_text(encoding="utf-8") if controller_path.is_file() else ""
    for contract in (
        "private val runtime: GeckoRuntime",
        "GeckoSessionSettings",
        ".usePrivateMode(tab.isPrivate)",
    ):
        if contract not in controller:
            failures.append(f"BrowserController is missing required session contract: {contract}")

    tests = root / "app/src/test/java/io/github/snowgemm/catbrowser/browser"
    for test_name in ("BrowserTabPrivacyTest.kt", "BrowserMemoryTrimPolicyTest.kt"):
        if not (tests / test_name).is_file():
            failures.append(f"required privacy/lifecycle regression test is missing: {test_name}")

    workflow_path = root / ".github/workflows/android-ci.yml"
    workflow = workflow_path.read_text(encoding="utf-8") if workflow_path.is_file() else ""
    for gate in (
        ":app:lintDebug",
        ":app:ktlintCheck",
        ":app:testDebugUnitTest",
        ":app:assembleDebug",
        "verify_abi_apks.py",
        "apksigner",
        "verify --verbose",
    ):
        if gate not in workflow:
            failures.append(f"Android CI is missing required quality gate: {gate}")

    return failures


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    failures = check_architecture(root)
    if failures:
        for failure in failures:
            print(f"ARCHITECTURE VIOLATION: {failure}", file=sys.stderr)
        return 1
    print("Cat Browser architecture guard passed: runtime ownership, session privacy, regression tests, and CI gates.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
