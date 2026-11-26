"""Mutation-style tests for Cat Browser's architecture guard."""
from pathlib import Path
import tempfile
import unittest

from verify_architecture import check_architecture


class CatBrowserArchitectureGuardTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.source = self.root / "app/src/main/java/io/github/snowgemm/catbrowser"
        self.source.mkdir(parents=True)
        (self.source / "browser").mkdir()
        tests = self.root / "app/src/test/java/io/github/snowgemm/catbrowser/browser"
        tests.mkdir(parents=True)
        for name in ("BrowserTabPrivacyTest.kt", "BrowserMemoryTrimPolicyTest.kt"):
            (tests / name).write_text("// fixture", encoding="utf-8")

        manifest = self.root / "app/src/main/AndroidManifest.xml"
        manifest.parent.mkdir(parents=True)
        permissions = (
            "INTERNET", "REQUEST_INSTALL_PACKAGES", "ACCESS_COARSE_LOCATION",
            "ACCESS_FINE_LOCATION", "CAMERA", "RECORD_AUDIO", "POST_NOTIFICATIONS",
            "FOREGROUND_SERVICE", "FOREGROUND_SERVICE_MEDIA_PLAYBACK",
        )
        manifest_items = "".join(
            f'<uses-permission android:name="android.permission.{name}" />'
            for name in permissions
        )
        manifest.write_text(
            f'<manifest xmlns:android="http://schemas.android.com/apk/res/android">{manifest_items}</manifest>',
            encoding="utf-8",
        )

        (self.source / "CatBrowserApplication.kt").write_text(
            "GeckoRuntime.create(this)\nBrowserController(this, geckoRuntime)", encoding="utf-8"
        )
        (self.source / "browser/BrowserController.kt").write_text(
            "private val runtime: GeckoRuntime\nGeckoSessionSettings\n.usePrivateMode(tab.isPrivate)",
            encoding="utf-8",
        )
        workflows = self.root / ".github/workflows"
        workflows.mkdir(parents=True)
        (workflows / "android-ci.yml").write_text(
            ":app:lintDebug :app:ktlintCheck :app:testDebugUnitTest "
            ":app:assembleDebug verify_abi_apks.py apksigner verify --verbose",
            encoding="utf-8",
        )

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def test_rejects_unreviewed_android_permission(self) -> None:
        manifest = self.root / "app/src/main/AndroidManifest.xml"
        content = manifest.read_text(encoding="utf-8").replace(
            "</manifest>",
            '<uses-permission android:name="android.permission.READ_CONTACTS" /></manifest>',
        )
        manifest.write_text(content, encoding="utf-8")
        self.assertTrue(any("outside the reviewed allowlist" in item for item in check_architecture(self.root)))

    def test_accepts_valid_architecture_fixture(self) -> None:
        self.assertEqual(check_architecture(self.root), [])

    def test_rejects_second_gecko_runtime_creation_site(self) -> None:
        (self.source / "browser/UnexpectedRuntime.kt").write_text(
            "GeckoRuntime.create(context)", encoding="utf-8"
        )
        self.assertTrue(any("created only" in item for item in check_architecture(self.root)))

    def test_rejects_removal_of_private_session_binding(self) -> None:
        controller = self.source / "browser/BrowserController.kt"
        controller.write_text(
            controller.read_text(encoding="utf-8").replace(
                ".usePrivateMode(tab.isPrivate)", ".usePrivateMode(false)"
            ),
            encoding="utf-8",
        )
        self.assertTrue(any("usePrivateMode(tab.isPrivate)" in item for item in check_architecture(self.root)))

    def test_rejects_missing_ci_quality_gate(self) -> None:
        (self.root / ".github/workflows/android-ci.yml").write_text(
            ":app:assembleDebug", encoding="utf-8"
        )
        self.assertTrue(any("quality gate" in item for item in check_architecture(self.root)))


if __name__ == "__main__":
    unittest.main()
