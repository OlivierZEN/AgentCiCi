---
kind: devops
version: 3
updated_at: 2026-04-19T08:20:00Z
updated_by: ai
status: active
---

# DevOps

## Verified Local Environment

- Verified on 2026-04-01T13:14:19Z:
  - Java: OpenJDK 21.0.10 available
  - Maven: 3.9.14 available
  - Node.js: v25.8.0 available
  - npm: 11.11.0 available

## Verified Commands

- Backend build/test:
  - `cd backend && mvn -Dmaven.repo.local=.m2 test`
  - Verified on 2026-04-01T13:44:22Z
- Backend build/test with local profile:
  - `cd backend && mvn -Dmaven.repo.local=.m2 -Dspring.profiles.active=local test`
  - Verified on 2026-04-01T13:44:22Z
- Frontend install/build:
  - `cd frontend && npm run build`
  - Verified on 2026-04-01T13:52:54Z
- Local infrastructure:
  - `docker compose up -d`
  - `docker compose ps` shows `postgres` and `redis` healthy
  - Verified on 2026-04-01T13:33:38Z
- Full quality gate:
  - `./scripts/quality-check.sh` — backend `mvn test` (default profile), frontend build, optional Qdrant smoke if `localhost:6333` is up
  - Verified on 2026-04-02T12:00:00Z
- Backend package (skip tests):
  - `cd backend && mvn -Dmaven.repo.local=.m2 -DskipTests package`
  - Verified on 2026-04-01T15:07:00Z
- Local backend runtime (alternate port):
  - `cd backend && mvn -Dmaven.repo.local=.m2 spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments="--server.port=8081 --app.kb.vector-store=memory"`
  - Verified on 2026-04-01T15:23:34Z
- Full local demo (Docker + backend + E2E + Vite):
  - `./scripts/run-full-demo.sh`
  - Verified on 2026-04-02 (E2E PASSED; assistant UI `/`, admin UI `/admin/login` on port 5173)
- Local backend runtime (current verified command):
  - `cd backend && mvn -Dmaven.repo.local=.m2 spring-boot:run -Dspring-boot.run.profiles=local`
  - Verified on 2026-04-17T03:30:04Z
  - Smoke result:
    - `GET http://127.0.0.1:8080/actuator/health` -> `{"status":"UP"}`
- Local frontend dev server (current verified command):
  - `cd frontend && npm run dev`
  - Verified on 2026-04-17T03:30:04Z
  - Smoke result:
    - `HEAD http://127.0.0.1:5173/` -> `HTTP/1.1 200 OK`
  - Dev proxy (2026-04-19): `vite.config.ts` / `vite.config.js` forward `/agents`, `/skills`, `/feishu` to `VITE_BACKEND_TARGET` or `http://127.0.0.1:8080` so Agent Builder and related APIs are not answered by Vite as static 404.
- Local infra status:
  - `docker compose ps`
  - Vector retrieval uses **Qdrant** on host `6333` only; legacy `cici-milvus` container removed (2026-04-19).

## Pending Verification

- Qdrant container + `scripts/verify-qdrant-stack.sh`; full app E2E with `app.kb.vector-store=qdrant` (default in `application-local.yml`).
