#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"

echo "[1/3] Backend tests (default profile; H2 + in-memory SMS, stable in CI)"
(cd "$BACKEND_DIR" && mvn -Dmaven.repo.local=.m2 test)

echo "[2/3] Frontend build"
(cd "$FRONTEND_DIR" && npm run build)

echo "[3/3] Qdrant REST smoke (skipped if nothing listens on 6333)"
if curl -sf --connect-timeout 1 http://127.0.0.1:6333/ >/dev/null 2>&1; then
  "$ROOT_DIR/scripts/verify-qdrant-stack.sh"
else
  echo "Skip: start Qdrant with \`docker compose up -d qdrant\` to run this step."
fi

echo "Quality check completed."
