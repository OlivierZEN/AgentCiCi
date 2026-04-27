#!/usr/bin/env bash
# Brings up Docker deps, starts backend (local profile) in background, optionally frontend, runs business E2E.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

echo "==> docker compose up"
docker compose up -d --remove-orphans postgres redis rabbitmq qdrant

echo "==> Wait for PostgreSQL"
for _ in $(seq 1 40); do
  if docker compose exec -T postgres pg_isready -U cici -d cici_assistant >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
docker compose exec -T postgres pg_isready -U cici -d cici_assistant

BACKEND_LOG="${BACKEND_LOG:-/tmp/cici-backend-demo.log}"
echo "==> Start backend (logs: ${BACKEND_LOG})"
if curl -sf http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
  echo "    Port 8080 already in use; assuming backend is running."
else
  (cd "${ROOT_DIR}/backend" && nohup mvn -Dmaven.repo.local=.m2 spring-boot:run -Dspring-boot.run.profiles=local >"${BACKEND_LOG}" 2>&1) &
  echo "    mvn pid=$!"
  for _ in $(seq 1 180); do
    if curl -sf http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
      break
    fi
    sleep 1
  done
  curl -sf http://127.0.0.1:8080/actuator/health >/dev/null || {
    echo "Backend failed to become healthy. Tail log:" >&2
    tail -50 "${BACKEND_LOG}" >&2 || true
    exit 1
  }
fi

echo "==> Business E2E"
"${ROOT_DIR}/scripts/e2e-local-business.sh"

if [[ "${START_FRONTEND:-1}" == "1" ]]; then
  FE_LOG="${FE_LOG:-/tmp/cici-frontend-demo.log}"
  if curl -sf http://127.0.0.1:5173/ >/dev/null 2>&1; then
    echo "==> Frontend already responding on 5173"
  else
    echo "==> Start frontend (logs: ${FE_LOG})"
    (cd "${ROOT_DIR}/frontend" && nohup npm run dev -- --host 127.0.0.1 --port 5173 >"${FE_LOG}" 2>&1) &
    echo "    npm pid=$!"
    sleep 4
  fi
fi

echo ""
echo "=== Demo ready ==="
echo "  API:        http://127.0.0.1:8080"
echo "  助手端:     http://127.0.0.1:5173/"
echo "  管理后台:   http://127.0.0.1:5173/admin/login"
echo "  测试说明与账号见 README「本地验收与测试账号」；设计与权限见 AI助手实现设计方案.md"
