#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

VALID_DIR="$TMP_DIR/valid"
mkdir -p "$VALID_DIR/app/src/main/res/values"

cat > "$VALID_DIR/app/build.gradle" <<'GRADLE'
android {
    defaultConfig {
        applicationId "io.github.forkmaintainers"
    }
    buildTypes {
        forkDebug {
            applicationIdSuffix ".iceraven.debug"
            def deepLinkSchemeValue = "iceraven-debug"
            manifestPlaceholders = [
                "sharedUserId": "io.github.forkmaintainers.iceraven.sharedID",
                "deepLinkScheme": deepLinkSchemeValue,
            ]
        }
        forkRelease {
            applicationIdSuffix ".iceraven"
            def deepLinkSchemeValue = "iceraven"
            manifestPlaceholders = [
                "sharedUserId": "io.github.forkmaintainers.iceraven.sharedID",
                "deepLinkScheme": deepLinkSchemeValue,
            ]
        }
    }
}
GRADLE

cat > "$VALID_DIR/app/src/main/res/values/strings.xml" <<'XML'
<resources>
    <string name="app_name">Iceraven Browser</string>
    <string name="about_label">Iceraven</string>
</resources>
XML

bash "$ROOT_DIR/scripts/cat-branding.sh" "$VALID_DIR" >/dev/null

BUILD_FILE="$VALID_DIR/app/build.gradle"
STRINGS_FILE="$VALID_DIR/app/src/main/res/values/strings.xml"

grep -Fq 'applicationId "io.github.snowgemm"' "$BUILD_FILE"
grep -Fq 'applicationIdSuffix ".catbrowser"' "$BUILD_FILE"
grep -Fq 'applicationIdSuffix ".catbrowser.debug"' "$BUILD_FILE"
grep -Fq '"sharedUserId": "io.github.snowgemm.catbrowser.sharedID"' "$BUILD_FILE"
grep -Fq 'def deepLinkSchemeValue = "catbrowser"' "$BUILD_FILE"
grep -Fq 'def deepLinkSchemeValue = "catbrowser-debug"' "$BUILD_FILE"
grep -Fq '<string name="app_name">Cat Browser</string>' "$STRINGS_FILE"
grep -Fq '<string name="about_label">Cat Browser</string>' "$STRINGS_FILE"

# Unsupported upstream layouts must fail instead of silently producing a
# partially branded APK.
INVALID_DIR="$TMP_DIR/invalid"
mkdir -p "$INVALID_DIR/app"
printf '%s\n' 'android { defaultConfig { applicationId "example.invalid" } }' > "$INVALID_DIR/app/build.gradle"
if bash "$ROOT_DIR/scripts/cat-branding.sh" "$INVALID_DIR" >/dev/null 2>&1; then
  echo "Expected unsupported upstream layout to fail" >&2
  exit 1
fi

echo "Cat Browser branding tests passed."
