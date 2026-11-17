#!/usr/bin/env bash
# Branding script for Cat Browser
# Usage: bash scripts/cat-branding.sh /path/to/iceraven-source

set -euo pipefail

ICERAVEN_DIR="${1:-}"
if [[ -z "$ICERAVEN_DIR" || ! -d "$ICERAVEN_DIR" ]]; then
  echo "Usage: $0 /path/to/iceraven"
  exit 1
fi

cd "$ICERAVEN_DIR"

echo "=== Cat Browser branding ==="

# 1. Change base applicationId
#    io.github.forkmaintainers → io.github.snowgemm
if [[ -f app/build.gradle ]]; then
  sed -i 's/applicationId "io.github.forkmaintainers"/applicationId "io.github.snowgemm"/g' app/build.gradle
  sed -i 's/applicationIdSuffix ".iceraven"/applicationIdSuffix ".catbrowser"/g' app/build.gradle
  sed -i 's/applicationIdSuffix ".iceraven.debug"/applicationIdSuffix ".catbrowser.debug"/g' app/build.gradle
  sed -i 's/io.github.forkmaintainers.iceraven.sharedID/io.github.snowgemm.catbrowser.sharedID/g' app/build.gradle
  sed -i 's/"iceraven"/"catbrowser"/g' app/build.gradle
  sed -i 's/"iceraven-debug"/"catbrowser-debug"/g' app/build.gradle
  echo "Updated app/build.gradle (applicationId + suffixes + deep links)"
fi

# 2. Replace visible app name strings (best-effort)
find . -name "*.xml" -type f -print0 | while IFS= read -r -d '' file; do
  if grep -q "Iceraven" "$file" 2>/dev/null; then
    sed -i 's/Iceraven Browser/Cat Browser/g' "$file"
    sed -i 's/Iceraven/Cat Browser/g' "$file"
    echo "Updated strings in: $file"
  fi
done

# 3. Optional: change some comments / identifiers
if [[ -f app/build.gradle ]]; then
  sed -i 's/forkRelease/catRelease/g' app/build.gradle || true
  sed -i 's/forkDebug/catDebug/g' app/build.gradle || true
fi

echo "=== Branding finished ==="
echo "Application ID will be approximately: io.github.snowgemm.catbrowser"
echo "You can now build with: ./gradlew assembleRelease"
