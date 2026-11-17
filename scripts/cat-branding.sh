#!/usr/bin/env bash
# Apply Cat Browser's independent Android package identity to an Iceraven checkout.
# Usage: bash scripts/cat-branding.sh /path/to/iceraven-source

set -euo pipefail

ICERAVEN_DIR="${1:-}"
if [[ -z "$ICERAVEN_DIR" || ! -d "$ICERAVEN_DIR" ]]; then
  echo "Usage: $0 /path/to/iceraven" >&2
  exit 2
fi

BUILD_FILE="$ICERAVEN_DIR/app/build.gradle"
if [[ ! -f "$BUILD_FILE" ]]; then
  echo "Unsupported Iceraven source layout: missing app/build.gradle" >&2
  exit 1
fi

echo "=== Cat Browser branding ==="

# Fail closed if upstream changes its Gradle configuration. A partial rebrand
# could leave deep links or package identity pointing at the upstream app.
python3 - "$BUILD_FILE" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
content = path.read_text(encoding="utf-8")
replacements = (
    ('applicationId "io.github.forkmaintainers"', 'applicationId "io.github.snowgemm"', 1),
    ('applicationIdSuffix ".iceraven.debug"', 'applicationIdSuffix ".catbrowser.debug"', 1),
    ('applicationIdSuffix ".iceraven"', 'applicationIdSuffix ".catbrowser"', 1),
    ('"sharedUserId": "io.github.forkmaintainers.iceraven.sharedID"', '"sharedUserId": "io.github.snowgemm.catbrowser.sharedID"', 2),
    ('def deepLinkSchemeValue = "iceraven-debug"', 'def deepLinkSchemeValue = "catbrowser-debug"', 1),
    ('def deepLinkSchemeValue = "iceraven"', 'def deepLinkSchemeValue = "catbrowser"', 1),
)

for old, new, expected_count in replacements:
    actual_count = content.count(old)
    if actual_count != expected_count:
        raise SystemExit(
            f"Unexpected upstream Gradle layout: expected {expected_count} occurrence(s) "
            f"of {old!r}, found {actual_count}. No branding changes were written."
        )
    content = content.replace(old, new)

path.write_text(content, encoding="utf-8")
PY

# Rebrand user-visible XML strings without touching source code or binary assets.
while IFS= read -r -d '' file; do
  if grep -q "Iceraven" "$file"; then
    sed -i 's/Iceraven Browser/Cat Browser/g; s/Iceraven/Cat Browser/g' "$file"
    echo "Updated strings in: $file"
  fi
done < <(find "$ICERAVEN_DIR" -type f -name '*.xml' -not -path '*/.git/*' -print0)

# Verify the final values used by the forkRelease and forkDebug variants.
grep -Fq 'applicationId "io.github.snowgemm"' "$BUILD_FILE"
grep -Fq 'applicationIdSuffix ".catbrowser"' "$BUILD_FILE"
grep -Fq 'applicationIdSuffix ".catbrowser.debug"' "$BUILD_FILE"
grep -Fq '"sharedUserId": "io.github.snowgemm.catbrowser.sharedID"' "$BUILD_FILE"
grep -Fq 'def deepLinkSchemeValue = "catbrowser"' "$BUILD_FILE"
grep -Fq 'def deepLinkSchemeValue = "catbrowser-debug"' "$BUILD_FILE"

echo "=== Branding finished ==="
echo "Release application ID: io.github.snowgemm.catbrowser"
echo "Debug application ID:   io.github.snowgemm.catbrowser.debug"
echo "Build with: ./gradlew :app:assembleForkRelease"
