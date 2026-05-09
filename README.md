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

- 本仓库所有页面设计与 UI 改版默认都要遵循 `impeccable` 技能，不再接受“先写页面、再补风格说明”的做法。
- 设计事实源固定为根目录 `PRODUCT.md`、`DESIGN.md`、`DESIGN.json`。如果页面改动改变了视觉语言、token 或组件表达，需要同会话一起更新。
- `/`、`/admin/*`、`/platform/*` 全部按 `product` register 处理，默认共享 `鎏金账房` 基线：暖白底、墨色字、紧凑密度、香槟金结构线。
- 页面实现必须遵循 `DESIGN.md` 的 `Page Implementation Quality Workflow`：先搭建可运行最小版本，再本地运行并截取桌面端与移动端完整页面截图，用设计师与 QA 视角检查视觉层级、间距、对比度、文本适配、响应式布局、导航路径和关键交互反馈；修复后重新截图复测，确认后才能收尾。
- 关键 UI 模块存在有意义的替代方案时，需要做聚焦 A/B 对比，按任务清晰度、可读性、可访问性、响应式稳定性和 `鎏金账房` 一致性择优，并删除未采用的实验代码。
- 只有页面确实需要插图、图标、空态视觉或品牌素材时，才使用 imagegen 或明确的素材流程；所有生成素材必须贴合 product register，服务理解和导航，不得制造装饰噪音。
- 新增产品 UI 必须遵循 `DESIGN.md` 的 `Product UI Scale`：默认控件和菜单文字为 13px，辅助信息为 11-12px，紧凑工具按钮和图标按钮高度为 32-34px、图标为 15-16px；不要为单个功能新增局部大字号、厚按钮或卡片化的大号选择列表。
- 产品页最严格 UI 规范：已被外层面板框定的区域内部，不得再加背景框。行、tab、搜索框内部、状态文字、链路详情、指标组和摘要块只能使用文字层级与必要的 1px 分隔线；严禁框套框、逐行背景块、选中背景、hover 背景、chip 背景、行阴影和内层 box-shadow 焦点框。
- 产品页选中态硬规则：面板内部的 selected、active、hover、pressed、focus、focus-visible 状态绝对不要加 `box-shadow`、发光、行阴影、内阴影、浮起卡片感或浏览器式焦点阴影。优先用文字颜色、字重或 tab 下划线表达；文字层级或已有分隔线能表达时，不要为选中态新增边框。
- 产品面板内部的 tab、行操作、筛选标签、状态操作和内联文字命令禁止使用带弧形边框的背景按钮样式：不得出现圆角白底、胶囊/小卡背景、边框按钮壳、hover 背景填充或阴影。此类控件只能用纯文本、文字颜色、字重、下划线或 1px 分隔线表达。
- 用原生 `button` 实现的产品 tab、范围筛选和筛选标签，必须在默认、hover、active、selected、focus、focus-visible 全状态显式重置为透明背景、0 圆角、无阴影、无 transform，避免全局按钮样式漏成白底伪按钮。
- 输入框工具、图标按钮或行操作触发的轻量浮层菜单统一使用更小规格：12px 主文字、仅在必要时使用 10-11px 辅助信息、13-14px 图标、26-30px 行高、168-220px 宽度、不透明暖象牙表面、浅金边，并仅在必要时使用克制阴影；紧凑技能/指令/选择器行默认不得使用 hover 背景、选中背景、逐行背景块、行阴影，也不得显示实现代码或 slug。
- 产品页按钮必须统一使用 `鎏金账房` 的主/次/危险按钮语汇：取消和次级操作为暖白底 + 金色系边框，确认/保存/发布等主操作为香槟金实心按钮，避免旧蓝色、青绿色、黑色或渐变按钮混入。
- 产品页页签必须遵循 `DESIGN.md` 的 `Product Tabs`：默认使用文本 tab，未选中为暖棕文字，选中为深金文字 + 2px 金色下划线；不要做成胶囊、chip、分段控件、带框小卡或带弧形边框背景按钮。
- 产品 tab 的激活和焦点态不得出现白色矩形背景、选中填充、焦点小卡、阴影、发光、浮起 transform 或圆角胶囊下划线；下划线必须是 2px 直线。
- 所有弹出框、选择器、确认框、编辑框、导入预览和发布弹框默认都做成模式窗口；只有规格明确说明时，才使用 popover、drawer、toast 或 inline disclosure。
- 所有弹出框/模式窗口右上角关闭 `×` 必须是无边框的纯图标/字形，外层不得出现可见方框、圆框或按钮边框；hover/focus 只能使用克制的浅色背景。
- 管理端列表页必须额外遵循 `DESIGN.md` 的 `Admin CRUD Lists`：表格列对齐优先，禁止直接改 `td/th/tr` 的 display；搜索框、筛选、空态不能撑开页面；默认不出现横向滚动；行操作统一使用三点 hover/focus 菜单，新表格优先使用共享 `admin-row-menu` 类族。
- 禁止装饰性渐变文字、厚侧边强调线、默认玻璃拟态、产品页大号 hero 指标模板、无差别重复卡片网格，以及把 modal 当作第一反应。
- 若新增品牌页、活动页或需要显著偏离当前基线，必须先完成 `impeccable shape` 并把例外写入 `docs/specs/`。

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

**双入口（助手 / 管理后台）**

- **助手**：http://127.0.0.1:5173/ — 面向员工；仅对话与只读知识库列表（勾选参与 RAG）。登录态保存在 `localStorage` 键 `cici_assistant_token`。
- **管理后台**：http://127.0.0.1:5173/admin/login — 需账号角色为 **ORG_ADMIN**；登录后访问 `/admin/...`（知识库维护、模型、工具、观测运维、**用户管理**）。登录态保存在 `cici_admin_token`。
- 开发代理除 `/auth`、`/kb`、`/ai` 等外，已包含 `/admin/users` 与 `/admin/agents`（用户管理与组织级智能体运行日志接口）。

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
| 手机号 | 助手端默认 `18611892001`；后台管理默认 `13900009999`；平台默认 `13800138111`（管理员与平台角色仍由 `application-local.yml` 中手机号白名单决定） |
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
