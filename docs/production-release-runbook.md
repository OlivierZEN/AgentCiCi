# AgentCiCi 生产发布运行手册

## 1. 发布目标

将当前本地工作区的后端、前端和部署配置发布到线上环境：

- 产品品牌：AgentCiCi
- 品牌域名：`agentcici.com`
- 当前公网地址：`https://onechat.agentcici.com`、`https://x.agentcici.com`
- 服务器：`root@47.97.119.160`
- 线上目录：`/opt/cici`
- 部署方式：Docker Compose + 阿里云 ACR 镜像
- 线上 Compose：`/opt/cici/deploy/docker-compose.acr.yml` + `/opt/cici/deploy/docker-compose.acr.ssl.yml`
- 对外端口：`80` 跳转 HTTPS，`443` 提供前端和 API 代理

本方案默认发布代码、镜像和必要的 Nginx/Compose 配置，不默认用本地数据覆盖线上业务数据。全量数据同步必须作为单独变更审批。

## 2. 发布原则

1. 先验收本地代码，再构建镜像。
2. 镜像使用不可变版本号发布，避免只依赖 `latest`。
3. ACR 镜像 tag、Git tag、后端运行版本和登录后左下角程序版本必须使用同一个版本号。
4. 线上发布前必须备份 PostgreSQL、环境变量、知识库文件和 Qdrant 数据。
5. 数据库迁移只允许 Flyway 正向迁移，不修改已上线 migration。
6. 发布后先做容器健康检查，再做公网业务 smoke。
7. 回滚优先回滚镜像和配置；已执行的数据库迁移不做“反向改历史”，必要时用正向修复迁移或整库恢复。

## 3. 发布前准备

### 3.1 本地确认

在仓库根目录执行：

```bash
git status --short
git rev-parse --short HEAD
git diff --check
```

确认事项：

- 本次发布范围已经明确。
- 不包含真实 `deploy/acr.env`、证书、私钥、模型 Key 或其他生产密钥。
- 新增数据库 migration 文件只追加，不改已发布 migration。
- 如有前端视觉改动，已同步 `DESIGN.md` / `DESIGN.json` / `README.md` / `AGENTS.md` 中对应设计事实源。

### 3.2 本地质量门禁

建议发布前至少执行：

```bash
cd backend
mvn -q -Dmaven.repo.local=.m2 test

cd ../frontend
npm run build

cd ..
docker compose --env-file deploy/acr.env.example -f deploy/docker-compose.acr.yml config >/tmp/cici-compose-check.yml
```

如果时间紧张，后端可退为重点集成测试 + 编译，但发布记录中必须写明未跑完整测试：

```bash
cd backend
mvn -q -Dmaven.repo.local=.m2 -DskipTests compile
```

### 3.3 发布变量与版本号

统一发布入口是 `scripts/release-acr.sh`。它会在每次 ACR 推送前生成一个规范版本号，并把同一个值用于：

- backend 镜像 tag：`cici-backend:<version>`
- frontend 镜像 tag：`cici-frontend:<version>`
- Git annotated tag：`<version>`
- 前端程序版本：`VITE_CICI_APP_VERSION=<version>`
- 后端运行版本：`CICI_APP_VERSION=<version>`
- 线上部署变量：`CICI_IMAGE_TAG=<version>` 与 `CICI_APP_VERSION=<version>`

发布版本分为生产版本和测试版本：

- 生产版本由三段纯数字组成，不带字母，例如 `2.0.1`。
- 生产版本由当前最新生产 Git tag 递增一个版本。每个数字段最大值为 `12`；第三段达到 `12` 后向第二段进位，并把第三段重置为 `1`。例如 `2.0.12` 的下一版是 `2.1.1`；`2.12.12` 的下一版是 `3.0.1`。
- 测试版本以当前最新生产版本为基础追加 `-beta.<n>`。例如当前最新生产版本为 `2.0.1` 时，第一次测试发布是 `2.0.1-beta.1`，后续同一生产基线的测试发布依次为 `2.0.1-beta.2`、`2.0.1-beta.3`。
- 如果没有任何生产 Git tag，脚本从 `2.0.1` 开始生成；可通过 `INITIAL_PRODUCTION_VERSION` 覆盖首个生产基线。

```bash
export ACR_IMAGE_PREFIX=op-registry.cloudcc.cn/cloudcc-ai-native
export SSH_KEY=/Volumes/workspace/datafiles/cc-cici-ecs.pem
export REMOTE=root@47.97.119.160

# 只查看下一版生产号和将执行的动作，不推送镜像、不创建 tag
./scripts/release-acr.sh --dry-run

# 只查看下一版测试号
./scripts/release-acr.sh --dry-run --channel test
```

如需指定版本号，可以显式传入：

```bash
./scripts/release-acr.sh --version 2.0.7
./scripts/release-acr.sh --version 2.0.6-beta.2
```

建议同时推送 `<version>` 和 `latest`，线上 `deploy/acr.env` 必须使用 `CICI_IMAGE_TAG=<version>` 发布。这样回滚时可以直接切回上一版 tag。

## 4. 构建并推送镜像

### 4.1 一键构建并推送

推荐使用统一脚本完成后端打包、前端构建、镜像构建、ACR 推送、镜像 inspect 和 Git tag 创建：

```bash
./scripts/release-acr.sh
export RELEASE_VERSION=<script-output-version>
```

脚本默认拒绝脏工作区，避免 Git tag 指向的代码和实际镜像内容不一致。确需从脏工作区做临时热修复时，必须显式声明并在发布记录写清楚：

```bash
ALLOW_DIRTY_RELEASE=true ./scripts/release-acr.sh --version <hotfix-version>
```

### 4.2 手动等价命令

只有在脚本不可用时才使用手动等价命令。手动流程也必须先确定同一个版本号：

```bash
export RELEASE_VERSION=2.0.7
export CICI_APP_VERSION="$RELEASE_VERSION"
export GIT_COMMIT="$(git rev-parse --short=12 HEAD)"

cd backend
mvn -q -Dmaven.repo.local=../.m2 -DskipTests package
cd ../frontend
VITE_CICI_APP_VERSION="$RELEASE_VERSION" npm run build
cd ..

docker buildx build \
  --platform linux/amd64 \
  -f deploy/Dockerfile.backend \
  --build-arg CICI_APP_VERSION="$RELEASE_VERSION" \
  --build-arg CICI_GIT_COMMIT="$GIT_COMMIT" \
  -t "$ACR_IMAGE_PREFIX/cici-backend:$RELEASE_VERSION" \
  -t "$ACR_IMAGE_PREFIX/cici-backend:latest" \
  --push .

docker buildx build \
  --platform linux/amd64 \
  -f deploy/Dockerfile.frontend \
  --build-arg CICI_APP_VERSION="$RELEASE_VERSION" \
  --build-arg CICI_GIT_COMMIT="$GIT_COMMIT" \
  -t "$ACR_IMAGE_PREFIX/cici-frontend:$RELEASE_VERSION" \
  -t "$ACR_IMAGE_PREFIX/cici-frontend:latest" \
  --push .

docker buildx imagetools inspect "$ACR_IMAGE_PREFIX/cici-backend:$RELEASE_VERSION"
docker buildx imagetools inspect "$ACR_IMAGE_PREFIX/cici-frontend:$RELEASE_VERSION"
git tag -a "$RELEASE_VERSION" -m "Release $RELEASE_VERSION"
git push origin "$RELEASE_VERSION"
```

## 5. 同步部署配置

如果本次修改了 Compose 或 Nginx 配置，先同步到服务器：

```bash
rsync -av -e "ssh -i $SSH_KEY" \
  deploy/docker-compose.acr.yml \
  deploy/docker-compose.acr.ssl.yml \
  deploy/nginx.cici.conf \
  deploy/nginx.cici.ssl.conf \
  "$REMOTE:/opt/cici/deploy/"
```

证书文件不从仓库同步。线上证书应继续保留在：

- `/opt/cici/deploy/certs/agentcici.com.pem`
- `/opt/cici/deploy/certs/agentcici.com.key`

域名切换要求：

- `onechat.agentcici.com` 与 `x.agentcici.com` 已解析到生产 ECS 或公网入口。
- 上述证书文件必须包含 `onechat.agentcici.com` 与 `x.agentcici.com` 的 SAN，或使用覆盖这两个子域的 `*.agentcici.com` 证书。
- `agentcici.com`、`www.agentcici.com`、`autoservice.agentcici.com` 已从生产 Nginx `server_name` 移除，不再作为当前线上入口。

## 6. 线上备份

发布前在服务器执行一次快照备份：

```bash
ssh -i "$SSH_KEY" "$REMOTE" '
set -euo pipefail
cd /opt/cici
TS=$(date +%Y%m%d-%H%M%S)
BACKUP_DIR=/opt/cici/backups/$TS-before-release
mkdir -p "$BACKUP_DIR"

set -a
. /opt/cici/deploy/acr.env
set +a

cp deploy/acr.env "$BACKUP_DIR/acr.env.before-release"

docker exec cici-database pg_dump \
  -U "${POSTGRES_USER:-cici}" \
  -d "${POSTGRES_DB:-agentcici}" \
  -Fc \
  -f /tmp/cici-before-release.dump
docker cp cici-database:/tmp/cici-before-release.dump "$BACKUP_DIR/postgres.dump"
docker exec cici-database rm -f /tmp/cici-before-release.dump

docker run --rm \
  -v cici-acr_cici_kb_files:/data:ro \
  -v "$BACKUP_DIR":/backup \
  alpine tar -czf /backup/kb-files.tgz -C /data .

docker run --rm \
  -v cici-acr_cici_qdrant_data:/data:ro \
  -v "$BACKUP_DIR":/backup \
  alpine tar -czf /backup/qdrant.tgz -C /data .

echo "$BACKUP_DIR"
'
```

备份完成后记录输出目录。没有备份目录不得继续生产发布。

## 7. 线上发布

### 7.1 更新线上镜像 tag

登录服务器，确认 `/opt/cici/deploy/acr.env`：

```bash
ssh -i "$SSH_KEY" "$REMOTE"
cd /opt/cici/deploy
grep -E '^(CICI_IMAGE_TAG|SSL_ENABLED|CICI_PLATFORM|ACR_IMAGE_PREFIX)=' acr.env
```

将镜像版本和程序版本改为本次发布 tag：

```bash
ssh -i "$SSH_KEY" "$REMOTE" "
set -euo pipefail
cd /opt/cici/deploy
if grep -q '^CICI_IMAGE_TAG=' acr.env; then
  sed -i.bak 's/^CICI_IMAGE_TAG=.*/CICI_IMAGE_TAG=$RELEASE_VERSION/' acr.env
else
  printf '\nCICI_IMAGE_TAG=$RELEASE_VERSION\n' >> acr.env
fi
if grep -q '^CICI_APP_VERSION=' acr.env; then
  sed -i.bak 's/^CICI_APP_VERSION=.*/CICI_APP_VERSION=$RELEASE_VERSION/' acr.env
else
  printf '\nCICI_APP_VERSION=$RELEASE_VERSION\n' >> acr.env
fi
grep -E '^(CICI_IMAGE_TAG|CICI_APP_VERSION)=' acr.env
"
```

确认生产关键配置：

- `SSL_ENABLED=true`
- `CICI_PLATFORM=linux/amd64`
- `APP_AUTH_JWT_SECRET` 已设置为生产值
- `APP_SECURITY_SECRET_KEY` 已设置为生产值
- `APP_MODEL_ALIYUN_API_KEY` 已设置，模型调用需要它
- `FRONTEND_PORT=80`
- `HTTPS_PORT=443`

### 7.2 拉取并启动

```bash
ssh -i "$SSH_KEY" "$REMOTE" '
set -euo pipefail
cd /opt/cici/deploy
docker compose --env-file acr.env \
  -f docker-compose.acr.yml \
  -f docker-compose.acr.ssl.yml \
  pull backend frontend

docker compose --env-file acr.env \
  -f docker-compose.acr.yml \
  -f docker-compose.acr.ssl.yml \
  up -d

docker compose --env-file acr.env \
  -f docker-compose.acr.yml \
  -f docker-compose.acr.ssl.yml \
  ps
'
```

如果本次只发布前端 Nginx 配置，可在容器启动后验证并热重载：

```bash
ssh -i "$SSH_KEY" "$REMOTE" '
docker exec cici-frontend nginx -t
docker exec cici-frontend nginx -s reload
'
```

## 8. 发布后验收

### 8.1 容器和健康检查

```bash
ssh -i "$SSH_KEY" "$REMOTE" '
cd /opt/cici/deploy
docker compose --env-file acr.env \
  -f docker-compose.acr.yml \
  -f docker-compose.acr.ssl.yml \
  ps

curl -fsS http://127.0.0.1:8080/actuator/health
docker logs --tail=200 cici-backend
docker logs --tail=100 cici-frontend
'
```

期望：

- `cici-database`、`cici-redis`、`cici-rabbitmq`、`cici-qdrant`、`cici-backend`、`cici-frontend` 均健康或正常运行。
- 后端健康检查返回 `{"status":"UP"}`。
- 后端 `GET http://127.0.0.1:8080/system/version` 返回本次 `version`。
- 后端日志没有 Flyway、数据源、Redis、RabbitMQ、Qdrant、模型配置启动错误。

### 8.2 公网 smoke

```bash
curl -I http://onechat.agentcici.com/
curl -I https://onechat.agentcici.com/
curl -I https://x.agentcici.com/
```

期望：

- HTTP 返回 `301` 到 HTTPS。
- HTTPS 首页返回 `200`。
- 后端健康状态以 8.1 中服务器内网 `http://127.0.0.1:8080/actuator/health` 为准，避免前端 Nginx SPA fallback 把未代理路径返回成首页。

### 8.3 登录和核心接口

使用发布负责人掌握的测试账号执行：

```bash
curl -sS https://onechat.agentcici.com/auth/password/login \
  -H 'Content-Type: application/json' \
  -d '{"orgId":"demo-org","mobile":"<mobile>","password":"<password>"}'
```

拿到 token 后验证：

```bash
export TOKEN='<jwt>'

curl -fsS https://onechat.agentcici.com/auth/me \
  -H "Authorization: Bearer $TOKEN"

curl -fsS https://onechat.agentcici.com/agents \
  -H "Authorization: Bearer $TOKEN"

curl -fsS https://onechat.agentcici.com/skills \
  -H "Authorization: Bearer $TOKEN"

curl -fsS https://onechat.agentcici.com/me/agents/run-logs \
  -H "Authorization: Bearer $TOKEN"
```

如果本次涉及管理端或开放 API，还需要补充：

```bash
curl -fsS https://onechat.agentcici.com/admin/agents/run-logs?limit=10 \
  -H "Authorization: Bearer $TOKEN"

curl -fsS https://onechat.agentcici.com/api/platform/skills \
  -H "Authorization: Bearer $TOKEN"
```

Open API 需要使用真实 API Key 验证：

```bash
curl -fsS https://onechat.agentcici.com/openapi/v1/agents/<agentId>/health \
  -H "Authorization: Bearer <api-key>"
```

## 9. Go / No-Go 标准

满足以下条件才算发布成功：

- ACR backend/frontend 镜像已推送并能 inspect。
- Git annotated tag、backend/frontend 镜像 tag、`/system/version` 和登录后左下角程序版本一致。
- 线上备份目录已生成，包含 `postgres.dump`、`acr.env.before-release`、`kb-files.tgz`、`qdrant.tgz`。
- 六个容器状态正常。
- `https://onechat.agentcici.com/` 和 `https://x.agentcici.com/` 返回 `200`。
- 登录、`/auth/me`、核心列表接口、管理端接口按本次发布范围通过 smoke。
- 本次涉及的重点用户路径已在浏览器人工点验。
- 发布 tag、镜像 digest、备份目录、验收结果已记录到发布记录。

任一关键项失败，进入回滚或暂停观察，不继续叠加新变更。

## 10. 回滚方案

### 10.1 镜像回滚

如果后端或前端启动失败，但数据库没有不可逆问题：

```bash
ssh -i "$SSH_KEY" "$REMOTE"
cd /opt/cici/deploy
sed -i.bak "s/^CICI_IMAGE_TAG=.*/CICI_IMAGE_TAG=<previous-release-tag>/" acr.env
docker compose --env-file acr.env \
  -f docker-compose.acr.yml \
  -f docker-compose.acr.ssl.yml \
  pull backend frontend
docker compose --env-file acr.env \
  -f docker-compose.acr.yml \
  -f docker-compose.acr.ssl.yml \
  up -d
docker compose --env-file acr.env \
  -f docker-compose.acr.yml \
  -f docker-compose.acr.ssl.yml \
  ps
```

### 10.2 配置回滚

如果 Nginx 或 Compose 配置导致问题：

```bash
ssh -i "$SSH_KEY" "$REMOTE"
cd /opt/cici
cp backups/<backup-dir>/acr.env.before-release deploy/acr.env
cd /opt/cici/deploy
docker compose --env-file acr.env \
  -f docker-compose.acr.yml \
  -f docker-compose.acr.ssl.yml \
  up -d
docker exec cici-frontend nginx -t
docker exec cici-frontend nginx -s reload
```

### 10.3 数据库处理

如果 Flyway migration 已执行：

- 首选：写新的正向 migration 修复数据或结构。
- 禁止：修改已经在线执行过的 migration 文件再重新发布。
- 最后手段：经业务确认允许丢弃发布后数据时，使用发布前 `postgres.dump` 做整库恢复。

整库恢复必须单独审批，恢复前再次备份当前故障态数据库，避免丢失排障证据。

## 11. 发布记录模板

```markdown
## Release YYYY-MM-DD HH:mm

- Release tag:
- Git commit:
- Backend image digest:
- Frontend image digest:
- Backup directory:
- Migration range:
- Changed deployment files:
- Quality gate:
- Production smoke:
- Known risks:
- Rollback tag:
- Released by:
- Verified by:
```
