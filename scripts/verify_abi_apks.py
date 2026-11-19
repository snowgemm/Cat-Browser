#!/usr/bin/env python3
"""Validate and normalize Cat Browser ABI-specific APK outputs."""

from __future__ import annotations

import argparse
import hashlib
import shutil
import sys
import zipfile
from pathlib import Path, PurePosixPath

EXPECTED_ABIS = ("arm64-v8a", "armeabi-v7a", "x86_64")
KNOWN_ANDROID_ABIS = frozenset(
    ("armeabi", "armeabi-v7a", "arm64-v8a", "x86", "x86_64")
)
REQUIRED_GECKOVIEW_LIBRARIES = frozenset(
    ("libmozglue.so", "libxul.so", "libgkcodecs.so")
)
REQUIRED_GECKOVIEW_ASSETS = frozenset(("assets/omni.ja",))


def inspect_apk(apk_path: Path) -> str:
    """Return the sole ABI in an APK after validating its GeckoView engine."""
    try:
        with zipfile.ZipFile(apk_path) as archive:
            corrupt_entry = archive.testzip()
            if corrupt_entry is not None:
                raise ValueError(f"corrupt ZIP entry: {corrupt_entry}")

            archive_entries = set(archive.namelist())
            missing_assets = REQUIRED_GECKOVIEW_ASSETS - archive_entries
            if missing_assets:
                raise ValueError(
                    "APK is missing required GeckoView assets: "
                    + ", ".join(sorted(missing_assets))
                )

            native_entries: set[tuple[str, str]] = set()
            for entry in archive_entries:
                parts = PurePosixPath(entry).parts
                if len(parts) < 3 or parts[0] != "lib" or not parts[-1].endswith(".so"):
                    continue

                abi = parts[1]
                if abi not in KNOWN_ANDROID_ABIS:
                    raise ValueError(f"unrecognized native ABI directory: {abi}")
                native_entries.add((abi, parts[-1]))
    except (OSError, zipfile.BadZipFile) as error:
        raise ValueError(f"cannot read APK: {error}") from error

    actual_abis = {abi for abi, _ in native_entries}
    if len(actual_abis) != 1:
        raise ValueError(
            f"expected native libraries for exactly one ABI, found: "
            f"{', '.join(sorted(actual_abis)) or 'none'}"
        )

    abi = next(iter(actual_abis))
    if abi not in EXPECTED_ABIS:
        raise ValueError(f"unsupported ABI split: {abi}")

    library_names = {name for _, name in native_entries}
    missing = REQUIRED_GECKOVIEW_LIBRARIES - library_names
    if missing:
        raise ValueError(
            f"{abi} APK is missing key GeckoView libraries: "
            f"{', '.join(sorted(missing))}"
        )

    return abi


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Validate GeckoView ABI splits and create stable artifact names."
    )
    parser.add_argument("--input", required=True, type=Path, help="Gradle APK output directory")
    parser.add_argument("--output", required=True, type=Path, help="Normalized artifact directory")
    parser.add_argument(
        "--variant",
        choices=("debug", "release"),
        default="debug",
        help="APK build variant used in normalized artifact filenames (default: debug)",
    )
    args = parser.parse_args()

    if not args.input.is_dir():
        parser.error(f"input directory does not exist: {args.input}")

    source_apks = sorted(args.input.glob("*.apk"))
    if not source_apks:
        print(f"No APKs found in {args.input}", file=sys.stderr)
        return 1

    apks_by_abi: dict[str, Path] = {}
    for apk_path in source_apks:
        try:
            abi = inspect_apk(apk_path)
        except ValueError as error:
            print(f"FAIL {apk_path.name}: {error}", file=sys.stderr)
            return 1

        if abi in apks_by_abi:
            print(
                f"Duplicate APKs for {abi}: {apks_by_abi[abi].name} and {apk_path.name}",
                file=sys.stderr,
            )
            return 1
        apks_by_abi[abi] = apk_path

    missing_abis = set(EXPECTED_ABIS) - set(apks_by_abi)
    unexpected_abis = set(apks_by_abi) - set(EXPECTED_ABIS)
    if missing_abis or unexpected_abis:
        print(
            "ABI set mismatch; "
            f"missing={sorted(missing_abis)}, unexpected={sorted(unexpected_abis)}",
            file=sys.stderr,
        )
        return 1

    args.output.mkdir(parents=True, exist_ok=True)
    checksum_path = args.output / "SHA256SUMS"
    output_apks = [
        args.output / f"cat-browser-{abi}-{args.variant}.apk"
        for abi in EXPECTED_ABIS
    ]
    for stale_file in [*output_apks, checksum_path]:
        if stale_file.exists():
            stale_file.unlink()

    checksum_lines: list[str] = []
    for abi, destination in zip(EXPECTED_ABIS, output_apks, strict=True):
        source = apks_by_abi[abi]
        shutil.copy2(source, destination)
        checksum_lines.append(f"{sha256(destination)}  {destination.name}")
        size_mib = destination.stat().st_size / (1024 * 1024)
        print(f"PASS {abi}: {destination.name} ({size_mib:.1f} MiB)")

    checksum_path.write_text(
        "\n".join(checksum_lines) + "\n",
        encoding="utf-8",
    )
    print(f"Validated {len(EXPECTED_ABIS)} ABI APKs; checksums written to SHA256SUMS.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
