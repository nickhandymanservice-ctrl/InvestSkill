#!/usr/bin/env bash
set -euo pipefail

# InvestPro - Build & Deploy Script
# Builds the Android APK, containerizes backend, and uploads for distribution.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
APK_NAME="InvestPro-v1.0.0.apk"

echo "============================================"
echo "  InvestPro - Build & Deploy Pipeline"
echo "============================================"

# ─── Phase 1: Backend Docker Build ────────────────────────────────────────────

echo ""
echo "[1/4] Building backend Docker containers..."
cd "$PROJECT_ROOT"

docker compose build --parallel 2>/dev/null || {
    echo "  Docker compose v2 not found, trying v1..."
    docker-compose build --parallel
}

echo "  ✓ Backend containers built successfully"

# ─── Phase 2: Android APK Build ──────────────────────────────────────────────

echo ""
echo "[2/4] Building Android APK..."
cd "$PROJECT_ROOT/android-app"

if command -v gradle &>/dev/null || [ -f "./gradlew" ]; then
    if [ -f "./gradlew" ]; then
        chmod +x ./gradlew
        ./gradlew assembleRelease --no-daemon
    else
        gradle assembleRelease --no-daemon
    fi

    APK_PATH=$(find . -name "*.apk" -path "*/release/*" | head -1)
    if [ -n "$APK_PATH" ]; then
        cp "$APK_PATH" "$PROJECT_ROOT/deployment/$APK_NAME"
        echo "  ✓ APK built: $APK_NAME"
    else
        echo "  ⚠ APK not found in release output, checking debug..."
        APK_PATH=$(find . -name "*.apk" | head -1)
        if [ -n "$APK_PATH" ]; then
            cp "$APK_PATH" "$PROJECT_ROOT/deployment/$APK_NAME"
        fi
    fi
else
    echo "  ⚠ Gradle not found. APK must be built on a machine with Android SDK."
    echo "    To build manually:"
    echo "      cd android-app && ./gradlew assembleRelease"
fi

# ─── Phase 3: Upload to Cloud Storage ────────────────────────────────────────

echo ""
echo "[3/4] Uploading APK to cloud storage..."

CLOUD_PROVIDER="${CLOUD_PROVIDER:-gcs}"  # gcs, s3, or azure
BUCKET_NAME="${BUCKET_NAME:-investpro-releases}"

upload_to_gcs() {
    if command -v gsutil &>/dev/null; then
        gsutil cp "$PROJECT_ROOT/deployment/$APK_NAME" "gs://$BUCKET_NAME/releases/$APK_NAME"
        gsutil acl ch -u AllUsers:R "gs://$BUCKET_NAME/releases/$APK_NAME"
        echo "  Download URL: https://storage.googleapis.com/$BUCKET_NAME/releases/$APK_NAME"
    else
        echo "  ⚠ gsutil not found. Install Google Cloud SDK to upload."
    fi
}

upload_to_s3() {
    if command -v aws &>/dev/null; then
        aws s3 cp "$PROJECT_ROOT/deployment/$APK_NAME" "s3://$BUCKET_NAME/releases/$APK_NAME" --acl public-read
        REGION="${AWS_REGION:-us-east-1}"
        echo "  Download URL: https://$BUCKET_NAME.s3.$REGION.amazonaws.com/releases/$APK_NAME"
    else
        echo "  ⚠ AWS CLI not found. Install AWS CLI to upload."
    fi
}

upload_to_azure() {
    if command -v az &>/dev/null; then
        CONTAINER_NAME="${AZURE_CONTAINER:-releases}"
        az storage blob upload \
            --account-name "$BUCKET_NAME" \
            --container-name "$CONTAINER_NAME" \
            --name "$APK_NAME" \
            --file "$PROJECT_ROOT/deployment/$APK_NAME" \
            --overwrite
        echo "  Download URL: https://$BUCKET_NAME.blob.core.windows.net/$CONTAINER_NAME/$APK_NAME"
    else
        echo "  ⚠ Azure CLI not found. Install Azure CLI to upload."
    fi
}

if [ -f "$PROJECT_ROOT/deployment/$APK_NAME" ]; then
    case "$CLOUD_PROVIDER" in
        gcs) upload_to_gcs ;;
        s3) upload_to_s3 ;;
        azure) upload_to_azure ;;
        *) echo "  ⚠ Unknown CLOUD_PROVIDER: $CLOUD_PROVIDER. Set to gcs, s3, or azure." ;;
    esac
else
    echo "  ⚠ No APK to upload. Build the APK first."
fi

# ─── Phase 4: Start Services ─────────────────────────────────────────────────

echo ""
echo "[4/4] Starting backend services..."
cd "$PROJECT_ROOT"

docker compose up -d 2>/dev/null || docker-compose up -d

echo ""
echo "============================================"
echo "  ✓ Deployment Complete!"
echo "============================================"
echo ""
echo "  Backend API: http://localhost:8000"
echo "  TradingView MCP: http://localhost:8001"
echo "  Octagon MCP: http://localhost:8002"
echo "  Redis: localhost:6379"
echo ""
echo "  API Docs: http://localhost:8000/docs"
echo ""
echo "  To configure Webull trading credentials:"
echo "    cp backend/.env.example backend/.env"
echo "    # Edit .env with your Webull credentials"
echo "    docker compose restart api"
echo ""
