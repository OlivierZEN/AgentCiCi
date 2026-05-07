# Deployment Runbook (Step 3)

## Local Environment

Root `docker-compose.yml` is **local development infrastructure only**. It intentionally starts PostgreSQL, Redis, RabbitMQ, and Qdrant, then developers run backend/frontend on the host through Maven and Vite.

Do not use root `docker-compose.yml` as the complete deployment entry. It does not include backend, frontend, ACR images, frontend Nginx proxy rules, or production environment variables.

1. Start infrastructure:
   - `docker compose up -d`
2. Verify health:
   - `docker compose ps`
3. Start backend with local profile:
   - `cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local`
4. Start frontend:
   - `cd frontend && npm run dev`

## Pre-Release Checklist

- Run `scripts/quality-check.sh`
- Ensure `/system/health` returns `UP`
- Verify auth flow:
  - `/auth/password/login`
  - `/auth/me`
- Verify orchestrator flow:
  - `/ai/chat`
  - `/ai/sessions`
  - `/ops/metrics/cost`

## Rollback Notes

- Backend rollback: redeploy previous artifact and restart.
- DB rollback: apply Flyway rollback strategy by new forward migration (never mutate applied migration files).
- Password login no longer depends on Redis-backed SMS codes.

## ACR One-Click Deployment

Use this path when deploying the prebuilt images from Alibaba Cloud ACR namespace `cloudcc-ai-native`.

This is the complete Docker Compose deployment path for backend, frontend, database, Redis, RabbitMQ, and Qdrant. It replaces root `docker-compose.yml` for delivery and server deployment.

Images:

- `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:latest`
- `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:latest`
- `op-registry.cloudcc.cn/cloudcc-ai-native/cici-database:latest`
- `op-registry.cloudcc.cn/cloudcc-ai-native/cici-redis:latest`
- `op-registry.cloudcc.cn/cloudcc-ai-native/cici-rabbitmq:latest`
- `op-registry.cloudcc.cn/cloudcc-ai-native/cici-qdrant:latest`

The compose file mounts `deploy/nginx.cici.conf` into the frontend container so browser-relative API calls such as `/auth`, `/ai`, `/kb`, `/agents`, `/skills`, `/me`, and `/api/platform` are proxied to the backend service.

Build and push backend/frontend images from the current workspace:

```bash
cd backend
mvn -q -Dmaven.repo.local=.m2 -DskipTests package
cd ../frontend
npm run build
cd ..
docker buildx build --platform linux/amd64 -f deploy/Dockerfile.backend -t op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:latest --push .
docker buildx build --platform linux/amd64 -f deploy/Dockerfile.frontend -t op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:latest --push .
```

Last pushed from this workspace on 2026-05-07:

- Backend digest: `sha256:82732586c707a9f0083fcc02191b16ed7b7345c8c0ad59988b65052ce7e00863`
- Frontend digest: `sha256:a70521fa3f651bec5fe32e1eaf5c698e5587a2e5de84f1acfb9e4a00ac33b9be`

First run:

```bash
cp deploy/acr.env.example deploy/acr.env
```

Edit `deploy/acr.env` before production use:

- Set `ACR_USERNAME` / `ACR_PASSWORD`, or run `docker login op-registry.cloudcc.cn` beforehand.
- Change `POSTGRES_PASSWORD`, `RABBITMQ_DEFAULT_PASS`, `APP_AUTH_JWT_SECRET`, and `APP_SECURITY_SECRET_KEY` (`app.security.secret-key`, base64-encoded 32 bytes).
- Set `APP_MODEL_ALIYUN_API_KEY` if model calls should work.
- Adjust `FRONTEND_PORT`, `BACKEND_PORT`, and infrastructure ports if the host already uses defaults.
- For HTTPS with the bundled SSL override, set `SSL_ENABLED=true`, copy cert files into `deploy/certs/`, and ensure the cert/key names match `deploy/nginx.cici.ssl.conf`.

Start or update the stack:

```bash
./scripts/deploy-acr.sh
```

Manual equivalent:

```bash
docker compose --env-file deploy/acr.env -f deploy/docker-compose.acr.yml pull
docker compose --env-file deploy/acr.env -f deploy/docker-compose.acr.yml up -d
docker compose --env-file deploy/acr.env -f deploy/docker-compose.acr.yml ps
```

SSL manual equivalent:

```bash
docker compose --env-file deploy/acr.env -f deploy/docker-compose.acr.yml -f deploy/docker-compose.acr.ssl.yml pull
docker compose --env-file deploy/acr.env -f deploy/docker-compose.acr.yml -f deploy/docker-compose.acr.ssl.yml up -d
docker compose --env-file deploy/acr.env -f deploy/docker-compose.acr.yml -f deploy/docker-compose.acr.ssl.yml ps
```

Default endpoints:

- Frontend: `http://127.0.0.1:80`
- Backend health: `http://127.0.0.1:8080/actuator/health`
- RabbitMQ management: `http://127.0.0.1:15672`
- Qdrant: `http://127.0.0.1:6333`

Stop the stack:

```bash
docker compose --env-file deploy/acr.env -f deploy/docker-compose.acr.yml down
```

## cici.cloudcc.cn Deployment Record

Verified on 2026-05-07:

- Host: `47.97.119.160`
- Domain: `https://cici.cloudcc.cn`
- Remote path: `/opt/cici`
- Compose files: `/opt/cici/deploy/docker-compose.acr.yml` + `/opt/cici/deploy/docker-compose.acr.ssl.yml`
- SSL certs: `/opt/cici/deploy/certs/cloudcc.cn.pem` and `/opt/cici/deploy/certs/cloudcc.cn.key`
- Public ports: `80` redirects to HTTPS, `443` serves the frontend.
- Internal host-bound ports: backend `127.0.0.1:8080`, PostgreSQL `127.0.0.1:5432`, Redis `127.0.0.1:6379`, RabbitMQ `127.0.0.1:5672` / `15672`, Qdrant `127.0.0.1:6333`.
- ACR infra images `cici-database`, `cici-redis`, `cici-rabbitmq`, and `cici-qdrant` were refreshed as `linux/amd64` images because the prior tags were arm64-only and could not run on the x86_64 ECS.
- Verification:
  - `docker compose ps` showed all six containers healthy.
  - `http://cici.cloudcc.cn/` returned `301` to HTTPS.
  - `https://cici.cloudcc.cn/` returned `200`.
  - `POST https://cici.cloudcc.cn/auth/password/login` with the fixed password returned `200`, a token, and `ORG_ADMIN`.
