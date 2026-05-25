#!/usr/bin/env bash
set -euo pipefail

# Quick local start - runs API without Docker for development

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "Starting InvestPro API locally..."

cd "$PROJECT_ROOT/backend"

# Create venv if needed
if [ ! -d ".venv" ]; then
    python3 -m venv .venv
    .venv/bin/pip install -r requirements.txt
fi

# Load env
if [ -f ".env" ]; then
    set -a
    source .env
    set +a
fi

echo "API starting at http://localhost:8000"
echo "Docs at http://localhost:8000/docs"
.venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
