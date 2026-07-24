# AgentCiCi

企业级智能体运行与治理平台。AgentCiCi 是独立产品品牌，CloudCC、Salesforce、企业微信、飞书等均作为外部系统或渠道集成。

## Workspace

- `backend/`: Java 21 + Spring Boot 3 后端基础工程
- `frontend/`: React + Vite 前端基础工程
- `.claw/`: 按 `cloudcc-aidev-guidelines-common` 维护的项目状态文件
- `docs/project-overview.md`: 当前项目总览、模块地图与开发优先级入口
- `AgentCiCi智能体平台实现设计方案.md`: 当前设计方案（**权限、双入口、接口与表结构**以该文档与代码为准；本 README 为快速索引）
- 品牌域名：`agentcici.com`（当前代码、部署文件、token key、API key 前缀等技术标识仍可能沿用历史 `cici` 命名，后续如需迁移需单独设计兼容方案）
- `docs/specs/FEAT-025-agentcici-market-positioning-and-roadmap.md`: 市场定位与产品路线，明确 AgentCiCi 优先聚焦 CRM、售后和企业业务系统的智能体运行与治理，而不是泛通用 Agent Builder。

**文档同步**：变更前端路由、鉴权、`bootstrap-admin-mobiles`、E2E 默认账号或管理 API 时，请同时更新 `AgentCiCi智能体平台实现设计方案.md`、本 README 与 `.claw/current-status.md`。

## UI Design Governance

- 页面设计、改版、评审、润色或 UI 实现默认遵循 `impeccable` 工作流。
- 所有前端页面、组件、弹窗和可视化 UI 的设计与实现必须按 `frontend-design` 技能要求先明确目的、用户、语气和差异化视觉方向，再落实到生产级布局、层级、动效、状态和细节。
- `frontend-design` 的“鲜明设计方向”必须收敛到 AgentCiCi 的 `鎏金账房` 产品风格内：暖象牙底、墨色文字、紧凑密度、香槟金结构线是默认边界，不能为了显眼而改成营销页、紫色渐变、玻璃拟态或与产品 register 冲突的视觉语言。
- 设计事实源固定为 `PRODUCT.md`、`DESIGN.md`、`DESIGN.json`：产品定位看 `PRODUCT.md`，人工摘要看 `DESIGN.md`，token、组件细则和页面质量流程看 `DESIGN.json`。
- `/`、`/admin/*`、`/platform/*` 全部按 `product` register 处理，默认共享 `鎏金账房` 基线：暖象牙底、墨色文字、紧凑密度、香槟金结构线。
- 功能设计、实现和测试默认不新增移动端兼容实现、移动端布局适配、移动端截图或移动端自动化测试；除非用户明确单独要求，验收只做桌面端产品质量门。
- 页面实现必须本地运行、检查桌面端截图、验证关键交互状态，并按截图复测问题；细则见 `DESIGN.json.extensions.pageImplementationWorkflow`。
- 如果页面改动改变视觉语言、token、组件语汇或跨页交互模式，同会话更新 `DESIGN.json`，再按需更新 `DESIGN.md` 和相关 `docs/specs/`。
- 新品牌页、营销页或路线级视觉偏离必须先完成 shape、得到用户确认，并写入 `docs/specs/` 后再实现。

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

根目录 `docker-compose.yml` 只用于**本地开发基础设施**，故意只启动 PostgreSQL、Redis、RabbitMQ 和 Qdrant。它不包含后端、前端、ACR 镜像、前端 Nginx 代理或生产环境变量，不作为完整部署入口。

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

**登录与组织管理入口（助手 / 管理后台）**

- **助手**：http://127.0.0.1:5173/ — 面向员工；仅对话与只读知识库列表（勾选参与 RAG）。登录态保存在 `localStorage` 键 `cici_assistant_token`。拥有目标组织 **OWNER** 或 **ORG_ADMIN** 权限的用户，可在“切换组织”菜单从该组织行的“管理后台”直接进入。
- **管理后台**：`/admin/...` — 仅 **OWNER** / **ORG_ADMIN** 可访问（知识库维护、模型、工具、观测运维、**用户管理**与 `/admin/evaluation` 组织 AI 质量中心）。不再提供 `/admin/login` 独立表单；它会回到 `/app`。进入后台时 `cici_admin_token` 只作为当前助手会话的临时本地镜像，退出后台仅清除该镜像并回到前台，不退出助手会话。
- **平台运营端**：http://127.0.0.1:5173/platform/login — 使用平台账号；`/platform/evaluation` 维护平台核心、标准应用和行业评测资产。平台登录态与组织后台隔离。
- 开发代理除 `/auth`、`/kb`、`/ai` 等外，已包含 `/admin/users`、`/admin/agents`、`/evaluation` 与 `/api/platform`（用户、运行日志、租户评测和平台治理接口）。

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
| 手机号 | 助手端默认 `13900009999`；组织管理员在前台“切换组织”中进入管理后台；平台默认 `13800138111`（管理员与平台角色仍由 `application-local.yml` 中手机号白名单决定） |
| 固定密码 | `szyd1234`（由数据库迁移初始化到 `auth_password` 表；三端登录统一使用该密码） |
| 知识库聊天 | 上传并发布文档需在 **管理后台** 完成；助手端勾选知识库后再提问，以便带上正确的 `knowledgeBaseIds` |

仅 API 验收（后端已运行在 8080；默认使用 `13900009999` 以保证新建用户为管理员，与本地 `bootstrap-admin-mobiles` 对齐）：

```bash
./scripts/e2e-local-business.sh
```

### 安全提示

请勿将生产用模型 Key、短信密钥等提交仓库；`application-local.yml` 中的密钥仅用于本机联调，提交前请替换为占位符或改用环境变量。

## Deployment

完整应用交付统一使用 ACR 一键部署脚本，而不是根目录 `docker-compose.yml`：

```bash
cp deploy/acr.env.example deploy/acr.env
# 编辑 deploy/acr.env：ACR 凭据、生产密码、JWT secret、模型 API key 和端口
./scripts/deploy-acr.sh
```

部署入口：

- `deploy/docker-compose.acr.yml`：完整应用栈，使用 `op-registry.cloudcc.cn/cloudcc-ai-native/*:latest` 六个镜像。
- `deploy/acr.env.example`：部署环境变量示例；真实 `deploy/acr.env` 已被 `.gitignore` 忽略。
- `deploy/nginx.cici.conf`：前端容器挂载的 Nginx 代理配置，负责把浏览器相对 API 转发到后端。
- `scripts/deploy-acr.sh`：一键登录、拉取、启动和查看状态。

本地开发和部署职责边界：

- 本地开发：`docker compose up -d` + 本机 Maven 后端 + 本机 Vite 前端。
- 完整部署：`./scripts/deploy-acr.sh` + ACR 六镜像 + 前端 Nginx 代理 + 后端服务。

## Core APIs (MVP)

- Auth: `/auth/password/login`, `/auth/me`（响应含 `roles`）
- Orchestrator: `/ai/chat`（JSON 一次性返回）、`/ai/chat/stream`（**SSE** 流式，`event:delta` 携带 `{"text":"..."}`，结束 `event:done`）、`/ai/sessions`
- Knowledge（读，任意登录用户）: `GET /kb`, `GET /kb/{kbId}/documents`
- Knowledge（写，**ORG_ADMIN**）: `POST/PUT/DELETE /kb`, `POST /kb/documents/upload`, `POST /kb/documents/{id}/publish`, `DELETE /kb/documents/{id}`, `POST /kb/{kbId}/chunks`
- Models（**ORG_ADMIN**，控制器级）: `GET/POST/DELETE /models`
- Tools（**ORG_ADMIN**）: `GET/POST/DELETE /tools`
- Ops（**ORG_ADMIN**）: `/ops/audit/logs`, `/ops/metrics/cost`
- Admin 智能体运行观测（**ORG_ADMIN**）: `GET /admin/agents/run-logs`，`GET /admin/agents/run-logs/{traceId}`
- Admin 用户管理（**ORG_ADMIN**）: `GET /admin/users`，`PUT /admin/users/{userId}/role`（body 字段 `roleCode` 为 `ORG_ADMIN` 或 `ORG_USER`）
- 租户 AI 质量（**ORG_ADMIN**）: `/evaluation/overview`、`/evaluation/suites`、`/evaluation/runs`、`/evaluation/issues`、`/evaluation/cases/from-trace`
- 平台智能体质量（平台角色）: `/platform/evaluation/overview`、`/platform/evaluation/suites`、`/platform/evaluation/runs`；平台资产写操作仅 `PLATFORM_ADMIN` / `PLATFORM_OPERATOR`

Agent Builder 的“评测”与“发布渠道”是两个独立一级 Tab：前者负责评测集、版本质量和发布门禁；后者只负责企微、钉钉、飞书、Web、Open API 等运行入口。

<!-- cc-aidev-guidelines-common:begin -->
## AI Development Protocol

This project follows the `cc-aidev-guidelines-common` skill.

- All AI agents must automatically use this skill before project analysis, planning, implementation, testing, or maintenance.
- If the current agent does not have this skill installed, install it first from GitHub: https://github.com/CloudCCAI/cloudcc-aidev-guidelines-common
- When the environment supports `AGENTS.md`, treat both this README section and `AGENTS.md` as mandatory project instructions.

本项目遵循 `cc-aidev-guidelines-common` 技能规范。

- 所有 AI 智能体在进行项目分析、规划、编码、测试和维护前，必须自动使用此技能。
- 如果当前智能体尚未安装此技能，必须先从 GitHub 安装：https://github.com/CloudCCAI/cloudcc-aidev-guidelines-common
- 如果当前环境支持 `AGENTS.md`，应将 README 中的本节和 `AGENTS.md` 同时视为必须遵守的项目级指令。
<!-- cc-aidev-guidelines-common:end -->
