---
kind: devops
version: 3
updated_at: 2026-05-07T11:23:00+08:00
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

- Compose responsibility boundary:
  - Root `docker-compose.yml` is local development infrastructure only. It starts PostgreSQL, Redis, RabbitMQ, and Qdrant for host-run Maven/Vite development.
  - Root `docker-compose.yml` is intentionally incomplete and must not be treated as the one-click application deployment file.
  - Complete server deployment uses `deploy/docker-compose.acr.yml` through `./scripts/deploy-acr.sh`, with the six ACR images under `op-registry.cloudcc.cn/cloudcc-ai-native`.
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
- ACR one-click deployment:
  - `cp deploy/acr.env.example deploy/acr.env`
  - Edit `deploy/acr.env` for ACR credentials, production passwords, JWT secret, model API key, and ports.
  - `./scripts/deploy-acr.sh`
  - Manual equivalent:
    - `docker compose --env-file deploy/acr.env -f deploy/docker-compose.acr.yml pull`
    - `docker compose --env-file deploy/acr.env -f deploy/docker-compose.acr.yml up -d`
    - `docker compose --env-file deploy/acr.env -f deploy/docker-compose.acr.yml ps`
  - Verified on 2026-05-07: compose config, deploy script syntax, mounted frontend Nginx config syntax, and target diff whitespace checks passed.
  - Note: backend/frontend ACR images currently require `CICI_PLATFORM=linux/amd64` on arm64 hosts.
- ACR backend/frontend image build and push:
  - `cd backend && mvn -q -Dmaven.repo.local=.m2 -DskipTests package`
  - `cd frontend && npm run build`
  - `docker buildx build --platform linux/amd64 -f deploy/Dockerfile.backend -t op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:latest --push .`
  - `docker buildx build --platform linux/amd64 -f deploy/Dockerfile.frontend -t op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:latest --push .`
  - Last pushed on 2026-05-07:
    - backend digest `sha256:82732586c707a9f0083fcc02191b16ed7b7345c8c0ad59988b65052ce7e00863`
    - frontend digest `sha256:a70521fa3f651bec5fe32e1eaf5c698e5587a2e5de84f1acfb9e4a00ac33b9be`
- ECS deployment `cici.cloudcc.cn`:
  - Host: `root@47.97.119.160`, key `/Volumes/Addison/workspace/datafiles/cc-cici-ecs.pem`
  - Remote root: `/opt/cici`
  - Compose:
    - `/opt/cici/deploy/docker-compose.acr.yml`
    - `/opt/cici/deploy/docker-compose.acr.ssl.yml`
  - Env: `/opt/cici/deploy/acr.env` (`600`, not in repo)
  - Certs:
    - `/opt/cici/deploy/certs/cloudcc.cn.pem`
    - `/opt/cici/deploy/certs/cloudcc.cn.key`
  - Public verification:
    - `http://cici.cloudcc.cn/` -> `301`
    - `https://cici.cloudcc.cn/` -> `200`
    - `POST /auth/password/login` fixed-password smoke -> `200`
  - Operational note:
    - ACR infra tags were rebuilt as linux/amd64 because the ECS is x86_64 and previous infra tags were arm64-only.

## Pending Verification

- Qdrant container + `scripts/verify-qdrant-stack.sh`; full app E2E with `app.kb.vector-store=qdrant` (default in `application-local.yml`).
