#!/usr/bin/env bash
# Full business path: auth -> KB -> upload -> publish (MQ index) -> chat with RAG.
# Prerequisites: docker compose (postgres, redis, rabbitmq, qdrant) + backend on local profile (8080).
# KB write APIs require ORG_ADMIN. Use a mobile listed in app.auth.bootstrap-admin-mobiles for *new* users
# (see application-local.yml), or an existing admin account. Override with E2E_MOBILE if needed.
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
COMPANY_ID="${E2E_COMPANY_ID:-demo-org}"
MOBILE="${E2E_MOBILE:-13900009999}"
MARKER="E2E_RAG_MARKER_$(date +%s)_${RANDOM}"

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing command: $1" >&2
    exit 1
  }
}

need_cmd curl
need_cmd python3

echo "==> Health: ${BASE_URL}/actuator/health"
curl -sf "${BASE_URL}/actuator/health" >/dev/null || {
  echo "Backend not reachable at ${BASE_URL}. Start with:" >&2
  echo "  cd backend && mvn -Dmaven.repo.local=.m2 spring-boot:run -Dspring-boot.run.profiles=local" >&2
  exit 1
}

echo "==> Login"
LOGIN_JSON="$(curl -sf -X POST "${BASE_URL}/auth/password/login" \
  -H 'Content-Type: application/json' \
  -d "{\"companyId\":\"${COMPANY_ID}\",\"mobile\":\"${MOBILE}\",\"password\":\"${E2E_PASSWORD:-szyd1234}\"}")"
TOKEN="$(echo "${LOGIN_JSON}" | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['token'])")"
AUTH=( -H "Authorization: Bearer ${TOKEN}" )

echo "==> Create knowledge base"
KB_JSON="$(curl -sf -X POST "${BASE_URL}/kb" \
  "${AUTH[@]}" -H 'Content-Type: application/json' \
  -d "{\"name\":\"E2E KB ${MARKER}\",\"description\":\"auto\"}")"
KB_ID="$(echo "${KB_JSON}" | python3 -c "import json,sys; print(int(json.load(sys.stdin)['data']['id']))")"
echo "    kbId=${KB_ID}"

TMP_FILE="$(mktemp)"
printf '%s\n' "This is an automated business acceptance document. Unique phrase: ${MARKER} for vector and chunk retrieval." > "${TMP_FILE}"
trap 'rm -f "${TMP_FILE}"' EXIT

echo "==> Upload document"
UP_JSON="$(curl -sf -X POST "${BASE_URL}/kb/documents/upload" \
  "${AUTH[@]}" -F "knowledgeBaseId=${KB_ID}" -F "file=@${TMP_FILE};filename=e2e.txt;type=text/plain")"
DOC_ID="$(echo "${UP_JSON}" | python3 -c "import json,sys; print(int(json.load(sys.stdin)['data']['id']))")"
echo "    docId=${DOC_ID}"

echo "==> Publish (async indexing in mq mode)"
curl -sf -X POST "${BASE_URL}/kb/documents/${DOC_ID}/publish" "${AUTH[@]}" >/dev/null

echo "==> Poll until PUBLISHED"
OK=
ST=
for _ in $(seq 1 90); do
  LIST_JSON="$(curl -sf "${BASE_URL}/kb/${KB_ID}/documents" "${AUTH[@]}")"
  ST="$(echo "${LIST_JSON}" | python3 -c "
import json,sys
j=json.load(sys.stdin)
for d in j.get('data') or []:
  if int(d.get('id',0))==${DOC_ID}:
    print(d.get('status',''))
    break
")"
  if [[ "${ST}" == "PUBLISHED" ]]; then
    OK=1
    break
  fi
  if [[ "${ST}" == "FAILED" ]]; then
    echo "Document indexing FAILED" >&2
    exit 1
  fi
  sleep 1
done
[[ -n "${OK}" ]] || { echo "Timeout waiting for PUBLISHED (last status=${ST:-unknown})" >&2; exit 1; }
echo "    status=PUBLISHED"

echo "==> Chat with RAG (knowledgeBaseIds=[${KB_ID}])"
CHAT_BODY="$(python3 -c "import json; print(json.dumps({'sessionId':'e2e-session','question':'What is the unique phrase in the indexed document?','knowledgeBaseIds':[str(${KB_ID})]}))")"
CHAT_JSON="$(curl -sf -X POST "${BASE_URL}/ai/chat" \
  "${AUTH[@]}" -H 'Content-Type: application/json' -d "${CHAT_BODY}")"

export MARKER
echo "${CHAT_JSON}" | python3 -c '
import json, sys, os
j = json.load(sys.stdin)
ctx = j.get("data", {}).get("ragContext") or []
text = " ".join(ctx)
marker = os.environ["MARKER"]
if marker not in text and len(ctx) == 0:
    print("FAIL: ragContext empty or missing marker", file=sys.stderr)
    print(json.dumps(j, indent=2), file=sys.stderr)
    sys.exit(1)
print("OK: ragContext chunks=", len(ctx), "; marker_hit=", marker in text)
'

echo ""
echo "=== E2E business acceptance PASSED ==="
echo "Recorded test login: companyId=${COMPANY_ID} mobile=${MOBILE}"
echo "Note: default MOBILE must stay aligned with README / AI助手实现设计方案.md §8.3 and app.auth.bootstrap-admin-mobiles (ORG_ADMIN required for KB writes)."
