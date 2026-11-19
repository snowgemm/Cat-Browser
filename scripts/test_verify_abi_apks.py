from __future__ import annotations

import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

from verify_abi_apks import EXPECTED_ABIS, REQUIRED_GECKOVIEW_LIBRARIES, inspect_apk, main


class VerifyAbiApksTest(unittest.TestCase):
    def create_apk(self, path: Path, abis: tuple[str, ...], missing_library: str | None = None) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("assets/omni.ja", b"geckoview")
            for abi in abis:
                for library in REQUIRED_GECKOVIEW_LIBRARIES:
                    if library != missing_library:
                        archive.writestr(f"lib/{abi}/{library}", b"native-library")

    def test_accepts_one_supported_abi_with_required_engine_libraries(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "arm64.apk"
            self.create_apk(apk, ("arm64-v8a",))
            self.assertEqual("arm64-v8a", inspect_apk(apk))

    def test_rejects_multiple_abis_in_one_apk(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "universal.apk"
            self.create_apk(apk, ("arm64-v8a", "armeabi-v7a"))
            with self.assertRaisesRegex(ValueError, "exactly one ABI"):
                inspect_apk(apk)

    def test_rejects_missing_geckoview_engine_library(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "incomplete.apk"
            self.create_apk(apk, ("arm64-v8a",), missing_library="libxul.so")
            with self.assertRaisesRegex(ValueError, "missing key GeckoView libraries"):
                inspect_apk(apk)

    def test_rejects_missing_geckoview_assets(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "missing-assets.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("lib/arm64-v8a/libmozglue.so", b"native")
            with self.assertRaisesRegex(ValueError, "missing required GeckoView assets"):
                inspect_apk(apk)

    def test_normalizes_all_abis_using_requested_variant(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "input"
            output = root / "output"
            for abi in EXPECTED_ABIS:
                self.create_apk(source / f"app-{abi}-release.apk", (abi,))

            with patch.object(
                sys,
                "argv",
                [
                    "verify_abi_apks.py",
                    "--input",
                    str(source),
                    "--output",
                    str(output),
                    "--variant",
                    "release",
                ],
            ):
                self.assertEqual(0, main())

            expected = {f"cat-browser-{abi}-release.apk" for abi in EXPECTED_ABIS}
            self.assertEqual(expected, {path.name for path in output.glob("*.apk")})
            self.assertTrue((output / "SHA256SUMS").is_file())
            self.assertEqual(3, len((output / "SHA256SUMS").read_text(encoding="utf-8").splitlines()))

    def test_debug_is_the_default_variant(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "input"
            output = root / "output"
            for abi in EXPECTED_ABIS:
                self.create_apk(source / f"app-{abi}-debug.apk", (abi,))

            with patch.object(
                sys,
                "argv",
                ["verify_abi_apks.py", "--input", str(source), "--output", str(output)],
            ):
                self.assertEqual(0, main())

            self.assertEqual(
                {f"cat-browser-{abi}-debug.apk" for abi in EXPECTED_ABIS},
                {path.name for path in output.glob("*.apk")},
            )


if __name__ == "__main__":
    unittest.main()
