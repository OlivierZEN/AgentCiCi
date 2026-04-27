# CC CiCi Assistant

企业内多组织 AI 助手平台项目骨架。

## Workspace

- `backend/`: Java 21 + Spring Boot 3 后端基础工程
- `frontend/`: React + Vite 前端基础工程
- `.claw/`: 按 `cloudcc-aidev-guidelines-common` 维护的项目状态文件
- `docs/project-overview.md`: 当前项目总览、模块地图与开发优先级入口
- `AI助手实现设计方案.md`: 当前设计方案（**权限、双入口、接口与表结构**以该文档与代码为准；本 README 为快速索引）

**文档同步**：变更前端路由、鉴权、`bootstrap-admin-mobiles`、E2E 默认账号或管理 API 时，请同时更新 `AI助手实现设计方案.md`、本 README 与 `.claw/current-status.md`。

## Quick Start

### Backend

```bash
cd backend
mvn spring-boot:run
```

使用本地 PostgreSQL + Redis + RabbitMQ + Qdrant 运行：

```bash
docker compose up -d
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

本地基础设施端口：

- PostgreSQL: `5432`
- Redis: `6379`
- RabbitMQ: `5672` (管理台 `15672`)
- Qdrant: `6333`（HTTP API）

### Frontend

```bash
cd frontend
npm install
npm run dev
```

**双入口（助手 / 管理后台）**

- **助手**：http://127.0.0.1:5173/ — 面向员工；仅对话与只读知识库列表（勾选参与 RAG）。登录态保存在 `localStorage` 键 `cici_assistant_token`。
- **管理后台**：http://127.0.0.1:5173/admin/login — 需账号角色为 **ORG_ADMIN**；登录后访问 `/admin/...`（知识库维护、模型、工具、运维、**用户管理**）。登录态保存在 `cici_admin_token`。
- 开发代理除 `/auth`、`/kb`、`/ai` 等外，已包含 **`/admin`**（用户列表与改角色接口）。

在某组织下**首次注册**的用户：若手机号在 `app.auth.bootstrap-admin-mobiles`（见 `application-local.yml`）中，则为组织管理员，否则为普通用户。若某手机号已在名单内但库里仍是普通用户，**下次短信登录成功时会自动升为** `ORG_ADMIN`（不会自动降级）。

## Quality Gate

```bash
./scripts/quality-check.sh
```

（第三步会在本机 `6333` 有 Qdrant 时执行 `scripts/verify-qdrant-stack.sh`；可先 `docker compose up -d qdrant`。）

## 本地验收与测试账号

### 一键演示（Docker + 后端 + E2E + 前端）

```bash
./scripts/run-full-demo.sh
```

- **API**：http://127.0.0.1:8080  
- **前端**：http://127.0.0.1:5173（Vite 开发服；已代理 `/auth`、`/kb`、`/ai`、`/admin` 等至 8080）

后端日志默认：`/tmp/cici-backend-demo.log`；前端：`/tmp/cici-frontend-demo.log`。

### 手动测试账号（本地）

| 项 | 值 |
|----|-----|
| 组织 ID | `demo-org`（启动时自动创建） |
| 手机号 | 助手端默认 `13800138111`；后台管理默认 `13900009999`（与 `application-local.yml` 中 bootstrap 管理员列表示例一致）。若提示发送过频，换一个号码或等待约 1 分钟（Redis 限流） |
| 验证码 | 点击「发送验证码」后，接口与界面会展示 **`devCode`**，将其填入验证码即可登录 |
| 知识库聊天 | 上传并发布文档需在 **管理后台** 完成；助手端勾选知识库后再提问，以便带上正确的 `knowledgeBaseIds` |

仅 API 验收（后端已运行在 8080；默认使用 `13900009999` 以保证新建用户为管理员，与本地 `bootstrap-admin-mobiles` 对齐）：

```bash
./scripts/e2e-local-business.sh
```

### 安全提示

请勿将生产用模型 Key、短信密钥等提交仓库；`application-local.yml` 中的密钥仅用于本机联调，提交前请替换为占位符或改用环境变量。

## Core APIs (MVP)

- Auth: `/auth/sms/send`, `/auth/sms/login`, `/auth/me`（响应含 `roles`）
- Orchestrator: `/ai/chat`（JSON 一次性返回）、`/ai/chat/stream`（**SSE** 流式，`event:delta` 携带 `{"text":"..."}`，结束 `event:done`）、`/ai/sessions`
- Knowledge（读，任意登录用户）: `GET /kb`, `GET /kb/{kbId}/documents`
- Knowledge（写，**ORG_ADMIN**）: `POST/PUT/DELETE /kb`, `POST /kb/documents/upload`, `POST /kb/documents/{id}/publish`, `DELETE /kb/documents/{id}`, `POST /kb/{kbId}/chunks`
- Models（**ORG_ADMIN**，控制器级）: `GET/POST/DELETE /models`
- Tools（**ORG_ADMIN**）: `GET/POST/DELETE /tools`
- Ops（**ORG_ADMIN**）: `/ops/audit/logs`, `/ops/metrics/cost`
- Admin 用户管理（**ORG_ADMIN**）: `GET /admin/users`，`PUT /admin/users/{userId}/role`（body 字段 `roleCode` 为 `ORG_ADMIN` 或 `ORG_USER`）
