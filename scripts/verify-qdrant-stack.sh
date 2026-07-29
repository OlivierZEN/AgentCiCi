#!/usr/bin/env bash
# Exercises Qdrant REST the same way as QdrantVectorStoreClient (collection, upsert, filtered search).
set -euo pipefail

BASE="${QDRANT_URL:-http://127.0.0.1:6333}"
COL="${QDRANT_SMOKE_COLLECTION:-cici_qdrant_smoke}"
DIM=16

echo "Waiting for Qdrant at ${BASE} ..."
for _ in $(seq 1 45); do
  if curl -sf "${BASE}/" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
curl -sf "${BASE}/" >/dev/null

echo "Recreating collection ${COL} (dim=${DIM}) ..."
curl -sf -X DELETE "${BASE}/collections/${COL}" >/dev/null 2>&1 || true
curl -sf -X PUT "${BASE}/collections/${COL}" \
  -H 'Content-Type: application/json' \
  -d "{\"vectors\":{\"size\":${DIM},\"distance\":\"Cosine\"}}"

PID="$(uuidgen 2>/dev/null | tr '[:upper:]' '[:lower:]')"
if [[ -z "${PID}" ]]; then
  PID="$(python3 -c 'import uuid; print(uuid.uuid4())')"
fi

VEC_JSON="$(python3 -c "print('[' + ','.join(['0.01']*${DIM}) + ']')")"

echo "Upserting smoke point ${PID} ..."
curl -sf -X PUT "${BASE}/collections/${COL}/points?wait=true" \
  -H 'Content-Type: application/json' \
  -d "{\"points\":[{\"id\":\"${PID}\",\"vector\":${VEC_JSON},\"payload\":{\"company_id\":\"company-smoke\",\"knowledge_base_id\":\"kb-a\",\"content\":\"smoke test chunk qdrant\"}}]}"

echo "Searching with company + knowledge_base_id match any ..."
RES="$(curl -sf -X POST "${BASE}/collections/${COL}/points/search" \
  -H 'Content-Type: application/json' \
  -d "{\"vector\":${VEC_JSON},\"limit\":3,\"filter\":{\"must\":[{\"key\":\"company_id\",\"match\":{\"value\":\"company-smoke\"}},{\"key\":\"knowledge_base_id\",\"match\":{\"any\":[\"kb-a\",\"kb-b\"]}}]},\"with_payload\":true}")"

if ! echo "${RES}" | grep -q "smoke test chunk qdrant"; then
  echo "Unexpected search response:"
  echo "${RES}"
  exit 1
fi

echo "Qdrant stack verification OK."
