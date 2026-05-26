#!/usr/bin/env bash
set -euo pipefail

# InvestPro — Local build + deploy helper.
#
# Most of the time you don't need to run this. Pushing to `main` triggers
# `.github/workflows/android-build.yml` which builds, signs, releases, and
# uploads to Cloudflare R2 automatically.
#
# Use this script only when:
#   - You want a local APK for sideloading to your own dev phone.
#   - You're testing the backend Docker container locally.
#
# For first-time end-to-end setup see ./SETUP-INSTRUCTIONS.md.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "============================================"
echo "  InvestPro — Local Build Helper"
echo "============================================"

# ─── Phase 1: Backend container (optional) ────────────────────────────────────

if command -v docker &>/dev/null; then
    echo
    echo "[1/2] Building backend Docker image..."
    cd "$PROJECT_ROOT"

    if docker compose version &>/dev/null; then
        docker compose build api
    elif command -v docker-compose &>/dev/null; then
        docker-compose build api
    else
        echo "  Skipping — neither 'docker compose' nor 'docker-compose' available."
    fi
    echo "  ✓ Backend image built (run 'docker compose up -d' to start it locally)."
else
    echo
    echo "[1/2] Docker not installed — skipping backend build."
fi

# ─── Phase 2: Android APK ─────────────────────────────────────────────────────

echo
echo "[2/2] Building Android APK..."
cd "$PROJECT_ROOT/android-app"

if [ ! -f "./gradlew" ]; then
    echo "  ✗ gradlew not found at $(pwd)/gradlew"
    exit 1
fi

chmod +x ./gradlew
./gradlew :app:assembleRelease --no-daemon

APK_PATH="$(find app/build/outputs/apk/release -name '*.apk' | head -1 || true)"
if [ -z "${APK_PATH:-}" ]; then
    echo "  ✗ No APK produced."
    exit 1
fi

OUT="$PROJECT_ROOT/deployment/InvestPro-local.apk"
cp "$APK_PATH" "$OUT"
SIZE=$(du -h "$OUT" | cut -f1)

echo
echo "============================================"
echo "  ✓ APK ready: $OUT ($SIZE)"
echo "============================================"
echo
echo "  Sideload to a connected phone:"
echo "    adb install -r '$OUT'"
echo
echo "  To publish a real release to Cloudflare R2 + GitHub Releases,"
echo "  push to main (CI handles it) or run:"
echo "    gh workflow run android-build.yml -f release_tag=v1.0.x"
echo
