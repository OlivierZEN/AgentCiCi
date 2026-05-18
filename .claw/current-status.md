---
kind: current-status
version: 3
updated_at: 2026-05-18T03:37:58Z
updated_by: ai
status: active
phase: maintenance
active_task: "Team developer identity registration"
current_task: 已按用户提供信息新增团队开发者 `DEV-zhongda`，并验证项目经理身份、公钥 fingerprint、状态文件和团队视图。
next_action: 后续为 `DEV-zhongda` 分配任务前，先创建对应 task/spec，再通过 `.claw/assignments/` 授权具体 branch 与 scope。
read_next:
  goals: false
  decisions: false
  issue_list: false
  task_board: true
  test_report: true
  devops: false
priority: P1
---

# Current Status

## Snapshot

- 2026-05-18T03:37:58Z 已由项目经理 `MANAGER-001` 登记团队开发者 `.claw/developers/DEV-zhongda.yaml`：`developer_id=DEV-zhongda`，显示名“仲达”，角色 `fullstack-agent`，Codeup/Git 用户名 `shanchl`，SSH signing fingerprint 为 `SHA256:k1ljDVP4i3TEhZQxdmZlzTGsJ++pHxubUqwc6vNQUOc`，状态 `active`；长期 scope 记录为 `assignment-scoped`，具体文件写入范围必须通过 assignment 授权。已刷新 `.claw/team-status.md`。验证通过：`dev-login.py` 对 `MANAGER-001` 返回 `allowed`，仲达公钥 fingerprint 核对通过，`validate-state.py` 通过。

- 2026-05-18T02:57:58Z 已由项目经理 `MANAGER-001` 登记团队开发者 `.claw/developers/DEV-fengchu.yaml`：`developer_id=DEV-fengchu`，显示名“凤雏”，角色 `fullstack-agent`，Codeup/Git 用户名 `Bimo`，SSH signing fingerprint 为 `SHA256:xvufU1n4Ov0fE7jEGrV82H/ABxHdm2VD2TKRHoNSEdQ`，状态 `active`；长期 scope 记录为 `assignment-scoped`，具体文件写入范围必须通过 assignment 授权。已刷新 `.claw/team-status.md`。验证通过：`dev-login.py` 对 `MANAGER-001` 返回 `allowed`，凤雏公钥 fingerprint 核对通过，`validate-state.py` 通过。

- 2026-05-17T15:23:41Z 已按用户提供信息更新 `.claw/developers/MANAGER-001.yaml`：`display_name=Owen`，`git_username=OwenZheng-Cloud`，公钥来源 `/Users/owenmacbook/.ssh/id_ed25519_agentcici_pm.pub`，SSH signing fingerprint 为 `SHA256:lNTe9Id7U0v8iDDKBaCZcuEkkDZH7qPsFulGMZkN/Sk`；因 `DEV-nezha` 也使用 `OwenZheng-Cloud`，已在两个身份记录中明确 role sharing exception，且两者 SSH fingerprint 不同。已刷新 `.claw/team-status.md`。验证通过：`dev-login.py` 对 `MANAGER-001` 返回 `allowed`，`validate-state.py` 通过。

- 2026-05-17T02:16:38Z 已按 `cc-aidev-guidelines-common` 3.7.0 对当前 brownfield 项目做协议初始化刷新：确认 canonical state directory 仍为 `.claw/`，保留既有八个核心状态文件、`docs/specs/PROJECT-BASELINE.md`、异步并行目录和团队身份记录；通过 3.7.0 `init-state.sh` 刷新 `README.md` 与 `AGENTS.md` 托管声明块，并将 `AGENTS.md` 声明块移回文件顶部以保留项目级加载顺序。验证通过：`python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/validate-state.py /Volumes/AISpace/codehouse/cc-agentcici_PM/.claw`。

- 2026-05-16T13:10:11Z 已将用户确认的 PR 处理规则固定到 `AGENTS.md`：处理项目 PR 时，本地验证通过后默认直接合并；不再把 “Ready for review” 作为人工停顿点；合并冲突由智能体先按代码、规格、测试和产品规则自主判断解决，只有无法安全判断时再通知用户；合并成功后同步本地 `main` 并更新必要 `.claw` 状态记录。

- 2026-05-16T13:05:32Z 已将 PR #1 合并到 `main`，GitHub 状态为 `MERGED`，merge commit 为 `71aa14e496f4f6e1f6835ce5134737cc45531511`；本地 `main` 已 fast-forward 到 `origin/main`。`2.0.B2` annotated tag 已推送，指向验证通过的提交 `8f0b6612237abfab30410b04a6671a2ff83340fd`。合并过程无冲突。

- 2026-05-16T12:50:13Z 已按用户要求处理当前分支 PR #1 `[codex] Add local model providers and team state`：本地 `origin` remote 已清理为不含 token 的 `https://github.com/OwenZheng-Cloud/CICI.git`；复核后端本地 provider 鉴权边界、chat/customer-insight 模型路由、Vite `/openapi` 双配置和 `/admin/models` 信息架构；修正模型路由行内“编辑/删除”文本动作，避免继承后台全局按钮圆角和 hover/focus chrome。验证通过：`git diff --check`、`.claw` `validate-state.py`、后端 `ModelProviderServiceIntegrationTest,ChatOrchestratorServiceModelIdentityTest`、前端 `npm run build`（保留既有 Vite chunk-size warning）。已用当前工作区临时 Vite `127.0.0.1:5174` 复测 `/admin/models` 桌面与 390px 移动：模型路由面板数量为 1，厂商详情内旧路由块为 0，无 broken image，无横向溢出，行内动作 computed 为透明背景、无阴影、0 圆角。截图为 `output/playwright/pr-1-admin-models-current-workspace-desktop-final.png` 与 `output/playwright/pr-1-admin-models-current-workspace-mobile-final.png`。

- 2026-05-16T06:36:51Z 已由项目经理 `MANAGER-001` 登记团队开发者 `.claw/developers/DEV-nezha.yaml`：`developer_id=DEV-nezha`，显示名“哪吒”，角色 `fullstack-agent`，GitHub 用户名 `OwenZheng-Cloud`，SSH signing fingerprint 为 `SHA256:ENtLToPJT7GBBpMSfHIi3bFPHQtVGznViqFFFwgKXXQ`，状态 `active`；长期 scope 记录为 `assignment-scoped`，具体文件写入范围必须通过 assignment 授权。已刷新 `.claw/team-status.md`，团队视图显示 2 个活跃成员。验证通过：`validate-state.py` 与 `summarize-team-status.py --write`。

- 2026-05-16T03:10:04Z 已为项目经理 Owen 创建 `.claw/developers/MANAGER-001.yaml`：身份为 `MANAGER-001`，角色 `project-manager`，Git 平台为 `github`，`git_username` 按用户提供记录为 `zhengyancc@hotmail.com`，SSH signing fingerprint 为 `SHA256:wPxil4lqS7zeoyUa2h1aCvvzvA4jVuokW9uCcwdTa+E`，状态 `active`。已刷新派生 `.claw/team-status.md`，团队视图显示 1 个活跃成员。验证通过：`python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/validate-state.py /Volumes/AISpace/codehouse/cc-agentcici_PM/.claw`。

- 2026-05-16T02:51:35Z 已按 `cc-aidev-guidelines-common` 3.6.0 对当前项目做协议初始化刷新：确认 canonical state directory 为 `.claw/`，保留既有八个核心状态文件与 `docs/specs/PROJECT-BASELINE.md`；通过技能脚本补齐 `.claw/integration-queue.md`、`.claw/team-status.md` 以及 `.claw/developers/`、`.claw/assignments/`、`.claw/tasks/` 目录；刷新 `README.md` 与 `AGENTS.md` 托管声明块，并将 `AGENTS.md` 声明块保持在文件顶部以保留项目级加载顺序。验证通过：`python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/validate-state.py /Volumes/AISpace/codehouse/cc-agentcici_PM/.claw`。

- 2026-05-15T15:49:48Z 已按用户反馈调整 `/admin/models` 信息架构：场景模型映射不再放在某个具体模型厂商配置里，改为厂商配置区下方的独立“模型路由”面板；保存映射时显式选择场景码、厂商和已选模型，映射列表展示全部场景，不再按当前选中厂商过滤。厂商详情切换不再清空路由表单。验证通过：`frontend npm run build` 成功（保留 Vite chunk-size warning）；Playwright 登录 `/admin/models` 后确认 `.model-routing-panel` 数量为 1、厂商详情内旧映射块数量为 0，桌面与 390px 移动截图成功，移动端 `scrollWidth=clientWidth=390`。截图为 `output/playwright/admin-models-routing-panel-desktop.png` 与 `output/playwright/admin-models-routing-panel-mobile.png`。

- 2026-05-15T13:29:42Z 已按用户反馈修正 `/admin/models` 模型厂商图标：新增 `frontend/public/provider-logos/aliyun-bailian.svg`（百炼控制台官方 SVG）和 `frontend/public/provider-logos/lmstudio.webp`（LM Studio 官网 app logo），`AdminModelsPage` 将 `aliyun-bailian` 与 `lmstudio-local` 映射到对应图标，不再让 LM Studio 复用 Ollama 图标。验证通过：`frontend npm run build` 成功（保留 Vite chunk-size warning）；临时 Vite `127.0.0.1:5174` 下两个静态资源均 HTTP 200；Playwright 登录 `/admin/models` 后确认图片 `naturalWidth>0` 且无 broken image，桌面与 390px 移动截图成功，移动端 `scrollWidth=clientWidth=390`。截图为 `output/playwright/admin-models-provider-icons-desktop.png` 与 `output/playwright/admin-models-provider-icons-mobile.png`。

- 2026-05-15T13:09:03Z 已完成 TASK-103 本地模型厂商联调：`ModelProviderService` 新增 `lmstudio-local`，本地 Ollama / LM Studio 标记 `apiKeyRequired=false`，OpenAI-compatible `/models` 与 `/chat/completions` 支持无 Bearer 调用，模型厂商 HTTP client 固定 HTTP/1.1；`ChatOrchestratorService` 只在 `aliyun-bailian` 下允许 Agent 自身 qwen 模型覆盖组织级路由，避免把 `qwen3.6-plus` 发给本地 provider；`AdminModelsPage` 展示 LM Studio 并对本地厂商提示无需 API Key。验证通过：`ModelProviderServiceIntegrationTest,ChatOrchestratorServiceModelIdentityTest`、`backend -DskipTests compile`、`frontend npm run build`、`git diff --check`；本机 Ollama API 拉取 2 个模型成功，但直接 chat 其中 `qwen3.6:27b-q8_0` 仍报本地模型文件 tensor size 错误；LM Studio `/models` 经后端返回 4 个模型，`/ai/meeting-minutes/summary` 经 `lmstudio-local/qwen3.5-35b-a3b` 生成摘要成功。Playwright 已截图 `/admin/models` 桌面与移动端，移动端 `scrollWidth=clientWidth=390`。

- 2026-05-15T09:05:47Z 已按用户要求补上本地 Vite `/openapi` proxy：`frontend/vite.config.ts` 新增 `"/openapi": { target: backendTarget, changeOrigin: true }`，构建同步更新已跟踪的 `frontend/vite.config.js`。已重启 `cici-frontend` screen 会话，新 Node PID `58015` 监听 `*:5173`。验证通过：`frontend npm run build` 成功（保留 Vite chunk-size warning）；使用临时 API Key 调 `GET http://192.168.0.105:5173/openapi/v1/agents/cici-system/health` 返回 HTTP 200 JSON；`POST http://192.168.0.105:5173/openapi/v1/agents/cici-system/chat/stream` 返回 HTTP 200 SSE，包含 `meta/phase/delta/done`，模型输出“5173 OpenAPI 代理正常。”，调用日志落库 `SUCCESS`，`traceId=7ff92d97-f50a-45f7-b80a-9d302fdf201c`。临时测试 Key credential `8` 已撤销；无 Key 请求现在经 5173 代理返回 Open API SSE 错误 `agent_api_key_missing`，确认不再被 Vite SPA fallback 吞掉。

- 2026-05-15T08:22:31Z 已按用户提供地址验证 `cici-system` 开放 API：`http://192.168.0.105:5173/openapi/v1/agents/cici-system/chat/stream` 即使携带有效临时 API Key 仍返回 HTTP 404；`GET` health 同路径返回 Vite SPA HTML，确认本地 Vite `5173` 未代理 `/openapi`。直连后端 `http://192.168.0.105:8080/openapi/v1/agents/cici-system/health` 在携带临时 Key 后返回 HTTP 200，`POST /chat/stream` 返回 `meta/phase/delta/done` SSE，模型 `qwen3.6-plus` 生成答复“收到，已确认开放 API stream smoke 运行正常。”，调用日志落库为 `SUCCESS`，`traceId=9bb8986c-8a6e-4bdb-b0bf-b3da837a2b95`。两个临时测试 Key（credential `6`、`7`）均已撤销。本地直连 `8080` 对 `Origin: https://cnbh01.cloudcc.cn` 的 CORS preflight 当前返回 HTTP 403 `Invalid CORS request`，说明当前本地运行进程没有放行该 Origin；线上测试环境此前服务器本机 vhost smoke 已验证 `Access-Control-Allow-Origin: *`。

- 2026-05-15T08:20:00Z 已将当前本地最新修改提交并发布到线上测试环境。Git 提交 `0c291df` 已推送 `origin/main`，ECS 当前 `CICI_IMAGE_TAG=2.0.B1-customer-insight-20260515-161832`。后端镜像 `sha256:6996e499dcab...`，前端镜像 `sha256:e9f5fc015279...`；远端备份目录 `/opt/cici/backups/20260515-161843-before-2.0.B1-customer-insight-20260515-161832`。验证通过：六个 compose 服务 healthy，backend `/actuator/health` 为 `UP`，Flyway 最新 `52|kb embedding model settings|true`、`51|customer insight ai app|true`，Nginx 配置 OK，服务器本地 Host `autoservice.agentcici.com` 下 `/`、`/sdk/meeting-minutes.js`、Open API preflight 均 HTTP 200；固定密码登录成功，`GET /ai/customer-insights/catalog` 返回 HTTP 200 且 26 个模块。

- 2026-05-15T08:06:30Z 已修复 Open API 调用时报 `Missing builtin skill resource: cloudcc-customization-expert-common/SKILL.md` 的代码路径风险：`FileBackedBuiltinSkillCatalog` 现在从扫描到的 `manifest.json` 同目录解析并缓存 `SKILL.md` 与模块文档资源，避免运行时 classpath 重新定位失败；缺失资源错误也会带上实际 source 描述便于排查。同步优化 `AgentOpenApiKeysDialog` 调用日志：桌面端点击摘要显示完整调用日志详情，移动端改用无横向溢出的分隔线列表并可展开 request/response/error 摘要。验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=FileBackedBuiltinSkillIntegrationTest test`、`frontend npm run build`（保留 Vite chunk-size warning）、targeted `git diff --check`；Browser 1280x720 与 390x844 视觉复查确认详情完整展示且移动端 `scrollWidth=390`。本轮只改本地代码，尚未发布线上。

- 2026-05-15T07:12:35Z 已修复 CloudCC 页面通过浏览器直接调用 `https://autoservice.agentcici.com/openapi/v1/agents/{agentId}/chat/stream` 时的 CORS 预检拦截。新增 `AgentOpenApiCorsConfig`，对 `/openapi/v1/**` 返回 CORS 头，允许 `GET/POST/OPTIONS`、`Authorization`、`Content-Type`、`X-Cici-Api-Key`、`Idempotency-Key`、`Last-Event-ID`；`AgentOpenApiProperties` 新增 `corsAllowedOrigins`、`corsAllowedOriginPatterns`、`corsMaxAgeSeconds`。已按用户要求将默认、本地和部署 compose/env 示例统一为 `APP_AGENT_OPEN_API_CORS_ALLOWED_ORIGINS=*`，即所有浏览器 Origin 均可调用。验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=AgentOpenApiCorsConfigTest test`、`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=AgentOpenApiIntegrationTest test`、`backend mvn -q -Dmaven.repo.local=.m2 -DskipTests compile`、目标文件 `git diff --check`。

- 2026-05-15T07:26:28Z 已将 Open API CORS hotfix 发布到线上测试环境。使用干净临时 worktree 基于 `HEAD 6e6868c` 只打入 CORS 相关变更，构建 backend jar 并在 ECS 本地构建镜像 `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.0.B1-openapi-cors-20260515-1518`（image id `sha256:5c904053aa0e...`）。线上备份目录为 `/opt/cici/backups/20260515-152355-before-openapi-cors`。`/opt/cici/deploy/acr.env` 已设置 `CICI_IMAGE_TAG=2.0.B1-openapi-cors-20260515-1518` 与 `APP_AGENT_OPEN_API_CORS_ALLOWED_ORIGINS=*`。验证通过：`cici-backend` healthy，`GET http://127.0.0.1:8080/actuator/health` 为 `UP`；服务器本地 Host `autoservice.agentcici.com` 下对 `Origin: https://cnbh01.cloudcc.cn` 与任意 origin 的 `/openapi/v1/agents/agent-473153/chat/stream` 预检均返回 HTTP 200 和 `Access-Control-Allow-Origin: *`；六个 compose 服务均 healthy。当前工作站直连公网仍复现既有 `curl: (35) Recv failure: Connection reset by peer`，以服务器本地 vhost smoke 为准。

- 2026-05-15T07:09:02Z 已确认本地开发服务可被同一局域网设备访问。当前本机 Wi-Fi IP 为 `192.168.0.105`；Vite 前端监听 `*:5173`，Spring Boot 后端监听 `*:8080`。本机使用局域网 IP 验证 `GET http://192.168.0.105:5173/` 与 `GET http://192.168.0.105:8080/actuator/health` 均返回 HTTP 200。其他电脑可访问 `http://192.168.0.105:5173/`；若 macOS 防火墙提示，需要允许 Node/Vite 与 Java/Maven 入站连接。

- 2026-05-15T06:44:00Z 已按用户反馈为客户洞察补入“业务闭环”能力。`CustomerInsightService` 新增 `business_service` 模块组：签约合同、订单与履约、客户服务、续约与增购；打开旧项目时会自动补齐新增 section，项目模块数从 22 提升到 26。刷新业务来源现在返回客户本体、`BUSINESS_CONTRACT`、`BUSINESS_ORDER`、`CUSTOMER_SERVICE` source snapshot 摘要，占位标明真实系统数据待接入但不阻塞手工事实分析。`ai-customer-insight-analyst` 标准技能与 prompt 边界同步禁止编造合同金额、订单状态和服务结论。前端 AI 应用卡、刷新按钮、输入占位和空态文案已改为合同订单/客户服务语义；`docs/specs/FEAT-034-customer-insight-ai-app.md` 已更新。验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=CustomerInsightIntegrationTest test`、`frontend npm run build`（保留 Vite chunk-size warning）、`git diff --check`、Playwright CLI 桌面/移动截图与移动无横向溢出量测。截图为 `output/playwright/customer-insight-business-loop-desktop.png`、`output/playwright/customer-insight-business-loop-mobile.png`。本地后端已通过 `screen` 恢复运行，`GET /actuator/health` 为 `UP`。

- 2026-05-15T06:14:10Z 已按用户截图反馈把客户洞察模块菜单改为分组折叠模式。`frontend/src/assistant/customer-insight/CustomerInsightModuleNav.tsx` 新增分组展开状态：当前模块所在分组默认展开，其他分组默认折叠，点击分组标题可展开/收起，切换到折叠分组中的模块时会自动保持对应分组展开。`frontend/src/assistant/cici-ui.css` 为分组标题新增浅金底、边线、展开/收起指示和更明显的组间分隔。验证通过：`frontend npm run build`（保留 Vite chunk-size warning）、`git diff --check`、Playwright Chrome 桌面截图与点击“竞争与关系”展开 smoke、移动 390x844 截图与无横向溢出量测。截图为 `output/playwright/customer-insight-accordion-desktop.png`、`output/playwright/customer-insight-accordion-expanded-desktop.png`、`output/playwright/customer-insight-accordion-mobile.png`。

- 2026-05-15T05:53:53Z 已按用户截图反馈收敛客户洞察 AI 应用页面视觉。`frontend/src/assistant/AssistantApp.tsx` 在客户洞察页不再渲染 hero meta；`frontend/src/assistant/customer-insight/CustomerInsightAppPanel.tsx`、`CustomerInsightSectionEditor.tsx`、`CustomerInsightReportPreview.tsx` 移除 `CUSTOMER`、`INSIGHT WORKSPACE`、分组 eyebrow、`PROJECT` 等重复小标题；`frontend/src/assistant/cici-ui.css` 减少项目/模块/右侧摘要的逐行横线，保留外层列边界、主标题底线和分析区必要分隔，客户洞察 Markdown 中的 `hr` 不再显示，激活 rail tooltip 不再悬浮遮挡页面。验证通过：`frontend npm run build`（保留 Vite chunk-size warning）；Playwright Chrome 桌面 1280x720 与移动 390x844 截图，移动端量测 `documentElement.scrollWidth=390`、`body.scrollWidth=390`、`main scrollWidth/clientWidth=332/332`，无横向溢出。截图为 `output/playwright/customer-insight-clean-desktop.png` 与 `output/playwright/customer-insight-clean-mobile.png`。

- 2026-05-15T03:07:36Z 已将 CloudCC AI 听记 404 修复发布到线上 ECS。为避免当前脏工作区里的其他未发布改动混入，本轮从干净 `HEAD 6e6868c` 创建临时 worktree，只应用两份 SDK origin 修复，执行 `frontend npm install` 与 `npm run build` 成功（保留 Vite chunk-size warning），并用 Node VM 复验 CloudCC 父页面会生成 `https://autoservice.agentcici.com/embed/meeting-minutes?...`。ACR 登录使用线上 `acr.env` 凭据在本机和 ECS 均返回 unauthorized，因此本轮采用 ECS 本机构建并发布：构建 `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:2.0.B1-sdk404fix-20260515-1103`（image id `sha256:7d575b6ea5f2...`），将 backend/database/redis/rabbitmq/qdrant 本地 `2.0.B1` 镜像 alias 到同一 tag，备份 env 到 `/opt/cici/backups/20260515-110703-before-2.0.B1-sdk404fix-20260515-1103`，更新 `/opt/cici/deploy/acr.env` 的 `CICI_IMAGE_TAG` 并仅重建 frontend。验证通过：`cici-frontend` healthy，后端与基础设施仍 healthy；服务器本地 Host `autoservice.agentcici.com` 下 `/sdk/meeting-minutes.js`、`/embed/meeting-minutes`、`/` 均 HTTP 200；线上 SDK 文件包含 `SDK_ORIGIN`。注意：hotfix 镜像当前只存在 ECS 本地，需后续修复 ACR 凭据并推送持久化。

- 2026-05-15T02:51:53Z 已修复 CloudCC 嵌入 AI 听记 404 的 SDK 地址生成问题。根因：`frontend/public/sdk/meeting-minutes.js` 与版本化副本在 `window.AgentCiCiMeeting.open(...)` 执行时才调用 `currentScriptOrigin()`；在 CloudCC Vue 点击事件中 `document.currentScript` 已为 `null`，代码回退到 `window.location.origin`，把 iframe 拼成 `https://yundong.lightning.cloudcc.cn/embed/meeting-minutes`，CloudCC 自身无该路由而返回 404。修复：SDK 加载时立即缓存 `SDK_ORIGIN`，`open()` 复用该 origin 创建 iframe。验证通过：两份 SDK `node --check`、targeted `git diff --check`、Node VM 模拟 `window.location.origin=https://yundong.lightning.cloudcc.cn` 且脚本 `src=https://autoservice.agentcici.com/sdk/meeting-minutes.js`，生成 iframe `src=https://autoservice.agentcici.com/embed/meeting-minutes?...`。线上公网从当前工作站直连 `autoservice.agentcici.com` 仍复现既有 `curl: (35) Recv failure: Connection reset by peer` 外部链路现象；CloudCC 域名不带 token 的 `/embed/meeting-minutes` 确认返回 404，符合错误宿主判断。

- 2026-05-14T23:45:50Z 已按用户截图反馈修复 AI 听记应用页高度分配。`frontend/src/assistant/cici-ui.css` 为 `.cici-ai-apps` 补 `min-height: 0`，并将 `.cici-ai-apps__meeting-panel` 从四行 grid 改为 `auto minmax(0, 1fr) auto`，避免隐藏 `MeetingMinutesPanel` header 后 body 被放进 auto 行、footer 被拉进弹性空白区。现在实时转写与 AI 会议纪要两栏撑满剩余屏高，底部“开始听记 / 结束并生成纪要”主操作固定在 footer。验证通过：`frontend npm run build`（保留 Vite chunk-size warning）、`git diff --check -- frontend/src/assistant/cici-ui.css frontend/src/assistant/AssistantApp.tsx frontend/src/meeting/MeetingMinutesPanel.tsx`、in-app Browser 桌面视觉检查、Playwright 1280x720/390x844 full-page 截图，布局量测显示 desktop 下 body 高度 460px、转写/纪要滚动区高度 434px、footer 位于 panel 底部。

- 2026-05-14T23:51:24Z 已继续按用户反馈收敛 AI 听记按钮层级。`frontend/src/meeting/MeetingMinutesPanel.tsx` 新增可隐藏主按钮的 `primaryActionVisible`，并在无 footer action 时不渲染 footer；`frontend/src/assistant/AssistantApp.tsx` 中 AI 应用页顶部按钮只在 `aiMeetingCanStart` 时显示，面板底部主按钮只在非开始态显示。空闲态因此只剩顶部一个“开始听记”；录音态仍由底部按钮显示“结束并生成纪要”。验证通过：`frontend npm run build`（保留 Vite chunk-size warning）、targeted `git diff --check`、in-app Browser DOM 确认空闲态 `开始听记` 数量为 1 且 footer buttons 为空、Playwright 桌面截图 `output/playwright/ai-app-meeting-single-start-desktop.png`。

- 2026-05-14T23:29:37Z 已完成 FEAT-034 `TASK-098 Customer insight AI app design`。新增 `docs/specs/FEAT-034-customer-insight-ai-app.md`，参考 `/Volumes/AISpace/codehouse/cc-customer-insight` 的 `ReportWorkbench.vue`、`SidebarMenu.vue` 和 `AI_FEATURES.md`，将客户基本信息、行业环境、战略/KPI、决策链、竞争关系、一客一策等能力映射为 AgentCiCi 内置“客户洞察”AI 应用。方案明确不 iframe、不照搬 Vue/Element Plus、不在前端放模型 key；改为 React 原生工作区，遵守 `鎏金账房`，并通过后端 `CustomerInsightService`、组织模型路由、`ai-customer-insight-analyst` 标准技能、CloudCC 只读 source snapshot、运行 trace 和租户权限体系实现。`.claw/task-board.md` 已新增 `TASK-098` 并拆分后续 `TASK-099` 至 `TASK-102`。本轮为设计文档，不改应用代码，未运行 build/test。

- 2026-05-14T23:25:33Z 已按用户截图反馈去掉 AI 应用页中重复的内层 “BUILT-IN AI APP / AI 听记” 头部。`frontend/src/meeting/MeetingMinutesPanel.tsx` 新增 `hideHeader` prop，默认不影响工作台抽屉和嵌入页；`frontend/src/assistant/AssistantApp.tsx` 仅在 AI 应用主页面传入 `hideHeader`。验证通过：`frontend npm run build`（保留 Vite chunk-size warning）、`git diff --check -- frontend/src/meeting/MeetingMinutesPanel.tsx frontend/src/assistant/AssistantApp.tsx`、in-app Browser 点击 “AI应用” 后 `.cici-ai-apps__meeting-panel .cici-meeting-drawer__header` 数量为 0，截图确认重复标题已移除。

- 2026-05-14T23:19:40Z 已按用户截图反馈调整 FEAT-033：`frontend/src/assistant/AssistantApp.tsx` 中 rail 菜单顺序改为会话工作台、客户会话、AI应用、CRM 系统。验证通过：`frontend npm run build`（保留 Vite chunk-size warning）、`git diff --check -- frontend/src/assistant/AssistantApp.tsx`、in-app Browser `http://localhost:5173/` 刷新后 “AI应用” 和 “CRM 系统” 按钮均唯一，截图确认 AI应用位于 CRM 上方且点击后仍显示 AI 听记页面。

- 2026-05-14T13:28:00Z 已完成 FEAT-033 `TASK-097 Assistant AI apps workspace`。`frontend/src/assistant/AssistantApp.tsx` 新增 `aiApps` 工作区、rail “AI应用”按钮、内置 AI 应用元数据和 AI 听记主页面；`frontend/src/assistant/cici-ui.css` 新增 AI 应用双栏布局、卡片列表、主页面和移动端样式，并隐藏小视口 rail tooltip 遮挡。AI 听记主页面复用现有 `MeetingMinutesPanel` 与 ASR/summary 状态；从 AI 应用页点击“开始听记”不写入工作台聊天历史，原工作台输入“开始会议纪要”触发抽屉入口保持不变。新增 `docs/specs/FEAT-033-assistant-ai-apps-workspace.md`，`.claw/task-board.md` 标记 `TASK-097` completed，`.claw/test-report.md` 记录本轮验证。验证通过：`frontend npm run build`（保留 Vite chunk-size warning）、targeted `git diff --check`、in-app Browser `http://localhost:5173/` 点击 “AI应用” DOM smoke、749px 宽视口双栏复查、1280x900 与 390x844 全页截图；截图为 `output/playwright/assistant-ai-apps-desktop.png`、`output/playwright/assistant-ai-apps-mobile.png`。

- 2026-05-14T11:56:09Z 已完成 `2.0.B1` 发布到线上测试环境。Git：提交 `44550bd` 已推送 `origin/main`，annotated tag `2.0.B1` 备注为“嵌入式智能应用”并已推送。ACR：`cici-backend:2.0.B1` digest `sha256:cbcd877d4372481c832dda3fe9f73448cc46d9d9a9e57327386fb7c86497429f`，`cici-frontend:2.0.B1` digest `sha256:596d56a4d4226cfa9c75618cacde717a28e85bb94d63e66bcf698d95303aef62`；`cici-database`、`cici-redis`、`cici-rabbitmq`、`cici-qdrant` 已从 `V1.9` manifest alias 为 `2.0.B1`。线上备份目录 `/opt/cici/backups/20260514-195316-before-2.0.B1`。部署时线上 Flyway V18 checksum 与当前迁移文件不一致，已在备份后将 `flyway_schema_history.version=18` checksum 修正为当前代码解析值 `1633949654`，随后后端启动并自动应用 V49/V50。验证通过：远程 `CICI_IMAGE_TAG=2.0.B1`，六容器 healthy，`GET http://127.0.0.1:8080/actuator/health` -> `UP`，Flyway 最新 `50|embed app backend|true`，Nginx 配置 OK；ECS 本机 Host smoke：`autoservice.agentcici.com /`、`/sdk/meeting-minutes.js`、`/embed/meeting-minutes`、`agentcici.com /`、`www.agentcici.com /` 均 HTTP 200，`autoservice.agentcici.com /auth/password/login` with `13900009999/szyd1234` 返回 HTTP 200。工作站直连公网域名仍出现 `curl: (35) Recv failure: Connection reset by peer`，与 2026-05-13 记录的外部链路现象一致；服务器本机 vhost 验证通过。

- 2026-05-14T10:48:18Z 已按用户要求将线上 PostgreSQL `cici_assistant` 全库覆盖到本地。流程：停止本地后端；备份本地库到 `output/db-sync-20260514-184504/local-before-prod-sync.dump`；通过 SSH 从 ECS `cici-database` 导出线上全库到 `output/db-sync-20260514-184504/prod-cici-assistant.dump`；drop/recreate 本地 `cici_assistant` 并 restore 线上 dump。由于线上 Flyway V18 checksum 与当前本地迁移文件不一致，已仅在本地执行 Flyway repair，然后补跑 V49/V50，当前本地最新迁移为 `50|embed app backend|true`。验证通过：本地后端 `GET /actuator/health` -> `UP`；前端 `HEAD /` -> `200`；本地表数 67，`user_account=16`、`organization_member=16`、`knowledge_base=2`、`kb_document=6`、`kb_chunk=310`；`13900009999/szyd1234` 登录返回 `ORG_ADMIN`，`13800138111/szyd1234` 返回 `ORG_ADMIN, PLATFORM_ADMIN`。

- 2026-05-14T10:19:08Z 已修复本地前台登录默认账号导致的 `登录失败：Invalid mobile or password`。当前 Docker Desktop PostgreSQL 本地库只有 `13800138111` 与 `13900009999` 两个账号，且都无个人密码凭证，会使用固定密码 `szyd1234` 回退；旧助手端默认 `18611892001` 不存在，在无 `orgId` 登录流程下无法自动创建组织成员，因此后端返回 401。已将 `frontend/src/assistant/AssistantApp.tsx` 默认手机号改为 `13900009999`，并同步 `README.md` 本地测试账号。验证通过：`POST /auth/password/login` with `13900009999/szyd1234` -> HTTP 200；旧 `18611892001/szyd1234` -> HTTP 401 复现根因；`frontend npm run build` 成功（保留 chunk-size warning）；targeted `git diff --check` 成功。FEAT-032 主线下一步仍是 `TASK-096 End-to-end CRM embed verification`。

- 2026-05-14T09:42:30Z 已完成 FEAT-032 `TASK-095 CloudCC writeback connector`。新增 `backend/src/main/java/com/codehouse/ciciassistant/embed/service/CloudccMeetingWritebackConnector.java`，复用 `CloudccAccessTokenService` 获取 run-as CloudCC session，并按 CloudCC One OpenAPI `/openApi/common` 的 `insert` / `update` / `delete` 服务名执行写回。`MeetingEmbedRuntimeService` 的 `writeback-preview` 现在由服务端生成 `summary-note`、从纪要行动项提取的 task，以及 signed CRM context 中的 field suggestion；`writeback` 确认接口校验 `selectedItemIds` 必须来自已持久化 preview，禁止浏览器提交任意 CloudCC payload。成功后 session 进入 `WRITTEN_BACK`；失败时保留 `READY_TO_WRITEBACK`，写入 `FAILED` result，并对本轮已成功插入的 note/task 调用 `delete` 回滚。嵌入页前端已将 `writeback.status=FAILED` 显示为错误态。验证通过：PostgreSQL `EmbedAppIntegrationTest` 覆盖 CloudCC mock 成功写回、未知候选拒绝、失败回滚；后端 compile；前端 build（保留 chunk warning）；targeted `git diff --check`。下一步进入 `TASK-096 End-to-end CRM embed verification`。

- 2026-05-14T07:22:59Z 已完成 FEAT-032 `TASK-094 Framework agnostic browser SDK`。新增 `frontend/public/sdk/meeting-minutes.js` 与版本化副本 `frontend/public/sdk/meeting-minutes@1.0.0.js`，作为框架无关浏览器全局脚本 `window.AgentCiCiMeeting`；支持 `open({ token, mode: "drawer" | "inline", container, width, locale, theme, context, callbacks })`，返回实例方法 `close()`、`destroy()`、`updateContext(nextContext)`、`postMessage(type, payload)`。SDK 使用命名空间 DOM/CSS 创建 drawer 或 inline iframe，iframe `allow="microphone"`，消息只接受 AgentCiCi embed origin，并将 `embed:ready`、`embed:summary-generated`、`embed:writeback-success`、`embed:error`、`embed:close` 等分发到 callbacks。验证通过：两份 SDK `node --check`、Vite 静态 URL `HEAD /sdk/meeting-minutes*.js` 返回 200、Playwright inline/drawer mode 均加载 iframe ready 状态、drawer mobile 截图、`updateContext()` + `close()` 触发 iframe `embed:close` 并销毁 shell、`frontend npm run build`（保留 chunk warning）。截图为 `output/playwright/embed-sdk-inline-desktop.png`、`output/playwright/embed-sdk-drawer-desktop.png`、`output/playwright/embed-sdk-drawer-mobile.png`。下一步进入 `TASK-095 CloudCC writeback connector`。

- 2026-05-14T07:14:45Z 已完成 FEAT-032 `TASK-093 Embed page and shared meeting UI` 的剩余视觉 QA。修复 `frontend/src/assistant/cici-ui.css` 中 `@media (max-width: 1360px)` 对 `.cici-meeting-drawer--embed` 的宽度覆盖，确保 `/embed/meeting-minutes` iframe 内核在桌面直接路由和 admin iframe 内都填满容器；移动端仍保持上下堆叠。验证通过：`frontend npm run build`（保留 chunk warning）、目标 `git diff --check`、Playwright `/embed/meeting-minutes` 桌面/移动截图、Playwright `/admin/embed-apps/meeting-minutes` 调试 tab 生成 token 并加载 iframe 预览的桌面/移动截图。截图为 `output/playwright/embed-meeting-desktop.png`、`output/playwright/embed-meeting-mobile.png`、`output/playwright/embed-apps-admin-debug-iframe-desktop.png`、`output/playwright/embed-apps-admin-debug-iframe-mobile.png`。下一步进入 `TASK-094 Framework agnostic browser SDK`。

- 2026-05-14T06:54:12Z 继续 FEAT-032 `TASK-093 Embed page and shared meeting UI`。新增 `frontend/src/meeting/MeetingMinutesPanel.tsx` 作为工作台与嵌入页共享会议面板，`AssistantApp` 原会议抽屉改为复用该组件；新增 `frontend/src/embed/EmbedMeetingMinutesPage.tsx` 与 `/embed/meeting-minutes` 路由，支持短期 embed token 解码、runtime session 创建、实时 ASR、结束生成纪要、写回候选预览/确认、`host:update-context` / `host:request-close` / `host:focus` 与 `embed:*` postMessage 事件；admin 调试 tab 已在生成调试 token 后直接渲染带 `allow="microphone"` 的 iframe 预览。验证通过：`frontend npm run build`（保留 chunk warning）、真实后端 debug token + `/embed/v1/apps/meeting-minutes/sessions` smoke 返回 `CREATED` session、目标 `git diff --check`、本机 Chrome 桌面可见态显示“会议 session 已就绪，可开始听记”。验证限制：headless Playwright 在当前机器对 Vite/preview 页面停在模块加载前，系统 `screencapture` 无法创建显示截图，因此本轮未产出可信桌面/移动截图；`TASK-093` 仍保留为 in_progress，待补齐截图 QA 后收口。

- 2026-05-14T04:56:11Z 已完成 FEAT-032 `TASK-092 Admin embedded apps management UI`。新增 `frontend/src/admin/pages/AdminEmbedAppsPage.tsx`、`/admin/embed-apps` 与 `/admin/embed-apps/:appCode` 路由，并在组织控制台菜单加入“嵌入式智能应用”。页面接入 `/embed/v1/admin/apps` 列表、详情和配置保存 API，提供应用目录、概览、接入配置、SDK/iframe 说明、调试 token、最近 session 调用日志 5 个 product 文本 tab。后端补 `POST /embed/v1/admin/apps/{appCode}/debug-token` 与 `GET /embed/v1/admin/apps/{appCode}/sessions`，`MeetingSessionRepository` 支持最近 session 查询。Vite dev proxy 和部署 Nginx 均补 `/embed/v1/` 代理。验证通过：`frontend npm run build`（保留 chunk warning）、`backend mvn -q -Dmaven.repo.local=.m2 -DskipTests compile`、`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=EmbedAppIntegrationTest test`（PostgreSQL 测试库）、`git diff --check`、Playwright 桌面/移动截图与 debug token smoke；截图为 `output/playwright/embed-apps-admin-desktop.png`、`output/playwright/embed-apps-admin-mobile.png`。下一步进入 `TASK-093 Embed page and shared meeting UI`。

- 2026-05-14T04:22:00Z 已完成 FEAT-032 `TASK-091 Embed token and session backend`。新增 `V50__embed_app_backend.sql`，包含 `embed_app_definition`、`org_embed_app_config`、`meeting_session` 和 agent trace embed metadata columns，并注册首个标准嵌入式智能应用 `meeting-minutes`。新增 `backend/src/main/java/com/codehouse/ciciassistant/embed/` 后端模块：`/embed/v1/admin/apps` 组织管理员配置 API、`/embed/v1/apps/{appCode}/tokens` token 签发 API、`/embed/v1/apps/{appCode}/sessions/**` runtime API。`JwtService` 支持自定义 claims 短期 token；`TenantContextFilter` 现在会放行 Open API Key token 签发请求、允许 embed token 仅访问 `/embed/v1/apps/**`，并拒绝 embed token 调用普通业务 API。用户要求后续测试库统一使用 PostgreSQL，不再使用 H2；`backend/src/test/resources/application.yml` 已切到 `jdbc:postgresql://localhost:5432/cici_assistant_test`（可用 `TEST_DATABASE_URL` 覆盖），并移除 H2 Maven 依赖。

- 2026-05-14T04:07:12Z 用户确认正式命名为“嵌入式智能应用”。FEAT-032、`.claw/task-board.md` 和本状态文件已统一该产品命名；管理端路由使用 `/admin/embed-apps`，技术对象继续使用 `embed_app_definition` / `org_embed_app_config`。

- 2026-05-14T03:55:50Z 已补充 FEAT-032：未来类似需要嵌入其他系统的能力应先注册为“嵌入式智能应用”，admin 管理端新增 `/admin/embed-apps` 统一菜单。会议纪要是首个嵌入式智能应用；列表展示 appCode、状态、适用系统、接入方式、权限 scope、最近调用和版本；详情页包含概览、接入配置、SDK/iframe 说明、Token 签发与权限、调试面板和调用日志。后续任务拆分新增 `TASK-092 Admin embedded apps management UI`，并将端到端验证顺延为 `TASK-096`。

- 2026-05-14T02:58:09Z 已按用户要求完成“框架无关 JS SDK + iframe 内核”会议纪要嵌入方案设计。新增 `docs/specs/FEAT-032-meeting-minutes-embed-sdk.md`，面向 CloudCC Vue/CRM 记录页嵌入场景，定义 `/sdk/meeting-minutes.js`、`/embed/meeting-minutes`、短期 `embedToken`、origin 校验、CRM context、postMessage 协议、SDK `open/close/destroy/updateContext` API、CloudCC Vue 示例、嵌入态 `鎏金账房` UI、写回候选和安全回滚策略；`.claw/task-board.md` 新增 `TASK-090` 并拆分 `TASK-091` 至 `TASK-095` 后续实现路径。

- 2026-05-13T12:13:50Z 已修复线上 `https://autoservice.agentcici.com/` 登录 `HTTP 502`。执行 `docker compose --env-file deploy/acr.env -f deploy/docker-compose.acr.yml -f deploy/docker-compose.acr.ssl.yml up -d backend` 恢复 `cici-backend` 后，后端启动日志显示 Tomcat 8080 启动、Flyway schema up to date、应用启动完成；`GET http://127.0.0.1:8080/actuator/health` 返回 `{"status":"UP"}`。随后 `docker exec cici-frontend nginx -t` 成功并执行 `nginx -s reload`。验证：compose 六容器均 healthy；服务器本机经 Nginx `Host: autoservice.agentcici.com` 访问 `/` 返回 `AgentCiCi` 静态页，`POST /auth/password/login` 返回 `HTTP 200` 且 `success:true`。`ISSUE-2026-05-13-prod-backend-container-missing` 已标记 resolved。

- 2026-05-13T12:09:23Z 只读验证 ECS 连通性：SSH `root@47.97.119.160` 使用 `/Volumes/AISpace/datafiles/cc-cici-ecs.pem` 登录成功，主机 `iZbp16tufidtxn62ug1mzhZ` 已运行 6 天多，Docker 可用；当前 compose 配置声明 `rabbitmq/redis/database/qdrant/backend/frontend`，但实际 `docker ps -a` 和 `docker compose ps -a backend` 只显示 frontend 与四个基础设施容器，未见 `cici-backend`。`docker exec cici-frontend nginx -t` 失败，原因为 `host not found in upstream "backend"`；带 Host 头从服务器本机访问 `https://127.0.0.1/` 可返回 `AgentCiCi` 静态页，但公网 `https://agentcici.com/` 从本机 curl 返回连接重置。已记录为 `ISSUE-2026-05-13-prod-backend-container-missing`。

- 2026-05-13T12:01:23Z 已按用户要求把项目在本地跑起来。当前环境使用 Lima Alpine VM `cici-docker` 提供 Docker daemon；基础镜像 `postgres:16`、`redis:7`、`rabbitmq:3-management`、`qdrant/qdrant:v1.12.6` 已拉取，根目录 `docker-compose.yml` 中四个基础设施服务已启动，其中 PostgreSQL/Redis/RabbitMQ healthy，Qdrant `6333` 可访问。后端 screen `39939.cici-backend` 以 Java 21/Maven/local profile 启动，Flyway 在新 PostgreSQL 上成功应用 49 个迁移，`GET http://127.0.0.1:8080/actuator/health` 返回 `{"status":"UP"}`；前端 screen `41592.cici-frontend` 运行 Vite，`HEAD http://127.0.0.1:5173/` 返回 `200 OK`。为处理 macOS Gatekeeper 拦截 `fsevents.node` / Rollup native module，本轮执行了 `npm install` 修复依赖并清理 `frontend/node_modules` 的 quarantine 属性。

- 2026-05-13T07:57:15Z 本地项目路径确认为 `/Volumes/AISpace/codehouse/cc-agentcici`。已更新 `restart-services.sh`、`.claw/devops.md` 中的 detached `screen` 启动命令，以及历史设计文档中的本地文件链接；全仓库已检查无旧绝对路径残留。

- 2026-05-12T12:20:31Z 已完成 FEAT-031 发布前评测 / 回归系统设计文档。新增 `docs/specs/FEAT-031-agent-evaluation-regression-system.md`，定义 Agent 评测集、评测用例、评测运行、用例结果、发布门禁、evaluation mode、确定性 AssertionEngine、可选 LLM judge、从真实 trace 创建回归用例、Agent Builder 评测入口和售后 Agent 内置评测模板；`.claw/task-board.md` 新增 `TASK-084` 并拆出后续 `TASK-085` 至 `TASK-089` 实现任务。当前为设计完成，尚未改动应用代码。

- 2026-05-12T10:08:10Z 已完成一次只读高并发/容量评估上下文采集：代码层面确认当前为 Spring Boot 模块化单体 + React/Vite + PostgreSQL/Redis/RabbitMQ/Qdrant 的单机 Docker Compose 部署，聊天/Open API 流式路径使用 `SseEmitter + CompletableFuture.runAsync`，实时听记使用 WebSocket，知识库索引使用 RabbitMQ 单队列；线上 ECS 只读快照为 8 vCPU、30GiB RAM、40GiB 根盘，六容器健康且低负载。详细资源快照与容量推断记录在 `.claw/devops.md` 的 `Production Capacity Snapshot`。

- 2026-05-12T05:50:58Z 已按用户要求将 FEAT-029 会议结束生成纪要显式调用的 `ai-meeting-notetaker`（AI 听记）平台标准技能，复制当前系统中启用的 `CloudCCAI听记` 自定义技能核心内容：`SkillDefinitionService` 的新组织内置定义现在使用客户拜访会议纪要、待办任务候选、CRM 线索/商机/联系人建议和人工确认规则；新增 `V49__ai_meeting_notetaker_cloudcc_prompt.sql` 更新既有标准技能的 `prompt_fragment` 与 `draft_spec_text`。验证通过：`MeetingMinutesServiceTest`、`SkillGovernanceIntegrationTest#shouldHidePlatformCoreSkillsAndBlockStandardSkillEditing`、后端编译；已重启本地后端 screen `84883.cici-backend`，Flyway 最新 `49|ai meeting notetaker cloudcc prompt|true`，本地 `ai-meeting-notetaker` 字段检查 `prompt_synced/spec_synced=true`。

- 2026-05-12T03:40:19Z 已修复用户截图反馈的 FEAT-029 实时会议纪要 speaker 边界滞后问题。根因是讯飞实时角色分离在发言人切换开头可能先返回 `rl=0` 或空 speaker marker，后端 `IflytekAsrResultParser` 会按 active speaker 继承为上一位发言人；前端 `appendMeetingTranscriptSegment` 只按后端 `speakerId` 合并 final 段落，因此第二个发言人首句前半段一旦以 `speakerId=1` 到达，就会永久合并进发言人 1。现已在后端 parser 中增加首个明确 marker 纠偏：同一结果片段开头仅有 `rl=0/空`，随后首次明确出现新 speaker marker 时，把已缓冲的开头文本一起归到新 speaker。验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=IflytekAsrResultParserTest test`、`frontend npm run test -- meetingTranscript.test.ts meetingMinutesCommand.test.ts`、`backend mvn -q -Dmaven.repo.local=.m2 -DskipTests compile`、target `git diff --check`。

- 2026-05-12T03:14:00Z 已完成 V1.9 `内置AI听记` 发布。Git commit `8f1f26b9dee3ce4d5249070348110ad591fce8e6` 已推送 `origin/main`，annotated tag `V1.9` 备注为“内置AI听记”并已推送；ACR 已推送 `cici-backend:V1.9` digest `sha256:8b09586cb68c1314d85f341ebf27a3ccfe257ce6eb988c9357ffdee7b8d559e7`、`cici-frontend:V1.9` digest `sha256:fab76d0ab47d0dfee855dbdb7ff46b60653dbb7144cf5d2f72787049f6265a63`，并为 database/redis/rabbitmq/qdrant 补齐 `V1.9` manifest alias。线上备份目录为 `/opt/cici/backups/20260512-110444-before-v1.9-ai-meeting-notes`；ECS 六容器均 healthy，Flyway 最新 `48|file backed builtin skills|true`，Playwright 公网验证 `https://agentcici.com/` 标题为 `AI 治理平台 | 企业 AI 客户运营套件`，`https://autoservice.agentcici.com/` 标题为 `AgentCiCi`。按用户要求执行本地数据覆盖线上时排除知识库内容：保留 `knowledge_base=2`、`kb_document=6`、`kb_chunk=310`、`agent_kb_binding=2`，未同步 KB 文件卷和 Qdrant；非知识库数据覆盖后已用线上 `APP_SECURITY_SECRET_KEY` 重加密 4 处加密字段。

- 2026-05-12T02:51:18Z 已按用户截图继续修复普通智能体对话同样无法调用模型的问题。根因与会议纪要一致：`ChatOrchestratorService` 的非流式工具规划/最终回复和流式最终回复都直接使用 `AliyunBailianClient` 的环境变量 key，没有读取组织后台模型厂商配置。现已为 `ChatOrchestratorService` 注入 `ModelProviderService`，每轮对话在 `ModelRouterService.route(orgId, "chat")` 后解析 provider baseUrl/apiKey；非流式 `chatCompletion` 和流式 `chatStreamWithMessages` 均改为有组织凭证时调用动态凭证方法，保留 `mock`/未配置场景的旧兜底。`AliyunBailianClient` 已新增 `chatStreamWithCredentials`。验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=MeetingMinutesServiceTest test`、`backend mvn -q -Dmaven.repo.local=.m2 -DskipTests compile`、重启后端 `53578.cici-backend` 健康 `UP`、本地 `demo-org` API smoke `/ai/chat` 返回正常问候且 model `qwen3.6-plus`、`/ai/chat/stream` 返回流式 delta 且不含 `Aliyun API key is not configured`。

- 2026-05-12T02:22:42Z 已修复用户截图反馈的 FEAT-029 AI 会议纪要无法调用大模型问题。根因是前一轮 `MeetingMinutesService` 已显式装配 `ai-meeting-notetaker`（AI 听记）技能，但模型调用仍使用 `AliyunBailianClient` 构造器里的环境变量 key，未读取组织后台保存的模型厂商配置；本地环境 `app.model.aliyun.api-key` 为空，因此面板显示 `Aliyun API key is not configured.`。现已为 `AliyunBailianClient` 增加可传入 baseUrl/apiKey 的 `chatCompletionWithCredentials`，`MeetingMinutesService` 改为通过 `ModelRouterService.route(orgId, "chat")` 读取组织聊天模型，再通过 `ModelProviderService.credentialsForProvider` 读取 provider baseUrl/apiKey 后调用模型。验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=MeetingMinutesServiceTest test`、`backend mvn -q -Dmaven.repo.local=.m2 -DskipTests compile`、重启后端 `40363.cici-backend` 健康 `UP`、本地 `demo-org` API smoke `POST /ai/meeting-minutes/summary` 返回 `## Meeting Summary` 且 `skillCode=ai-meeting-notetaker` / `skillName=AI 听记`。

- 2026-05-12T02:21:42Z 已完成 TASK-082 前台登录页星空背景调整。`frontend/src/styles.css` 仅修改 `login-mode2` 相关样式：页面背景改为深蓝星空层，表单卡片、输入框、标签、链接和主按钮改为冷蓝灰体系；`frontend/src/assistant/AssistantApp.tsx` 未改动，旋转立方体和登录表单结构保持原样。新增 `docs/specs/FEAT-030-front-login-starry-background.md` 记录该登录页视觉例外。验证通过：`frontend npm run build`（保留 Vite chunk warning）、`git diff --check -- frontend/src/styles.css`、Playwright 临时上下文桌面与 390px 移动截图检查，截图为 `output/playwright/front-login-starry-desktop-v2.png` 和 `output/playwright/front-login-starry-mobile.png`。

- 2026-05-12T01:38:54Z 已按用户要求把 FEAT-029 会议结束后的 AI 会议纪要生成改为显式调用 AI 听记技能。`backend/src/main/java/com/codehouse/ciciassistant/skill/service/SkillDefinitionService.java` 新增平台标准技能 `ai-meeting-notetaker`（AI 听记）并默认以 intent-route 绑定到 `cici-system`；`backend/src/main/java/com/codehouse/ciciassistant/ai/service/MeetingMinutesService.java` 不再使用孤立 system prompt，而是通过 `SkillPromptAssembler` 装配 `ai-meeting-notetaker` 当前技能上下文、prompt fragment 和 output contract 后再调用模型；`backend/src/main/java/com/codehouse/ciciassistant/ai/api/MeetingMinutesController.java` 响应回传 `skillCode/skillName`；`frontend/src/assistant/AssistantApp.tsx` 的生成中/完成提示同步展示 AI 听记技能语义；`docs/specs/FEAT-029-meeting-minutes-live-transcription.md` 已同步规格。验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=MeetingMinutesServiceTest test`、`backend mvn -q -Dmaven.repo.local=.m2 -DskipTests compile`、`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillGovernanceIntegrationTest#shouldHidePlatformCoreSkillsAndBlockStandardSkillEditing test`、`frontend npm run test -- meetingTranscript.test.ts meetingMinutesCommand.test.ts`、`frontend npm run build`（保留 Vite chunk warning）、target `git diff --check`。

- 2026-05-12T01:18:36Z 已修复 FEAT-029 发言人名称内联编辑只能输入一个字符的问题。根因是 `meetingSpeakerEdit` 的 focus/select effect 依赖整个编辑对象，`onChange` 每次更新 `value` 后 effect 重新执行并全选输入框，导致下一次输入覆盖前一个字符。`frontend/src/assistant/AssistantApp.tsx` 已把 effect 依赖收窄到 `speakerId` + `lineId`，只在进入新的编辑目标时自动聚焦/全选，不在输入过程中重置选择区。验证通过：`frontend npm run test -- meetingTranscript.test.ts meetingMinutesCommand.test.ts`、`frontend npm run build`（保留 Vite chunk warning）、target `git diff --check -- frontend/src/assistant/AssistantApp.tsx`。

- 2026-05-11T23:48:39Z 已按用户截图继续修正 FEAT-029 会议纪要面板。`frontend/src/assistant/AssistantApp.tsx` 为实时转写区新增 scroll ref，段落或 partial 更新时自动滚到最新内容；发言人标签改为无按钮壳的文本触发，鼠标双击或键盘 Enter/F2 可进入内联编辑，Enter/blur 保存、Escape 取消，保存后会同步历史段落、当前 partial 和后续同一 `speakerId` 的名称。`frontend/src/assistant/cici-ui.css` 补充编辑输入的底线式样式，保持 `鎏金账房` 面板内无背景框/无阴影规则，并修正移动端状态标签断字。验证通过：`frontend npm run test -- meetingTranscript.test.ts meetingMinutesCommand.test.ts`、`frontend npm run build`（保留 Vite chunk warning）、target `git diff --check`、in-app Browser 桌面 1280x800 与移动 390x844 触发 drawer 截图检查；浏览器麦克风权限仍返回 `Permission denied`，本轮未用真实音频生成转写段落。

- 2026-05-11T23:30:23Z 已修复 FEAT-029 第二轮 speaker 识别问题。根据讯飞实时语音转写大模型官方文档，角色分离字段位于 `data.cn.st.rt.ws.cw.rl`，且 `rl=0` 表示继续上一说话人，`rl=1/2/3...` 才表示切换到对应说话人。`IflytekAsrResultParser` 已改为按词级 `cw.rl` 解析，并在 `IflytekWsClient` 会话内携带 active speaker；前端 speaker 显示同步改为使用讯飞 1-based 编号，默认发言人为 `1`。验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=IflytekAsrResultParserTest test`、`frontend npm run test -- meetingTranscript.test.ts meetingMinutesCommand.test.ts`、`frontend npm run build`（保留 Vite chunk warning）、`backend mvn -q -Dmaven.repo.local=.m2 -DskipTests compile`、目标 `git diff --check`。已重启本地服务，新 screen 会话为 `67822.cici-backend` 与 `67824.cici-frontend`，后端 health `UP`，前端 5173 返回 `200 OK`。

- 2026-05-11T16:29:27Z 已按用户确认重启本地前后端服务。执行 `docker compose up -d --remove-orphans postgres redis rabbitmq qdrant` 后本地基础设施保持运行；停止旧 `35525.cici-backend` / `35528.cici-frontend` screen 会话，额外结束残留 8080 Java 监听进程；新 detached `screen` 会话为 `59117.cici-backend` 与 `59119.cici-frontend`。验证通过：`GET http://127.0.0.1:8080/actuator/health` 返回 `{"status":"UP"}`，`HEAD http://127.0.0.1:5173/` 返回 `HTTP/1.1 200 OK`。

- 2026-05-11T16:23:27Z 已修复 FEAT-029 实时会议纪要转写 speaker 和段落聚合问题。新增 `backend/src/main/java/com/codehouse/ciciassistant/ai/ws/IflytekAsrResultParser.java`，按讯飞 `cn.st.rt[]` 子段分别提取文本和角色，避免外层默认 `rl=0` 覆盖子段 speaker；`AliyunRealtimeAsrWebSocketHandler` 现在可对同一次讯飞结果发送多个带 speaker 的 transcript event。新增 `frontend/src/assistant/meetingTranscript.ts`，`AssistantApp.tsx` 在收到同一 `speakerId` 的连续 final 时合并为一个段落，并将零基 speaker 显示为“发言人 1/2”。验证通过：`frontend npm run test -- meetingTranscript.test.ts meetingMinutesCommand.test.ts`、`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=IflytekAsrResultParserTest test`、`frontend npm run build`（保留 Vite chunk warning）、`backend mvn -q -Dmaven.repo.local=.m2 -DskipTests compile`、目标 `git diff --check`。

- 2026-05-11T16:12:15Z 已按用户要求调整 FEAT-029 会议纪要面板布局。`frontend/src/assistant/AssistantApp.tsx` 将实时转写和 AI 会议纪要包进 `cici-meeting-drawer__body`，`frontend/src/assistant/cici-ui.css` 将 drawer 桌面宽度从 `min(42vw, 560px)` 扩到 `min(72vw, 980px)`，内容区改为左右两列 grid，中间仅使用 1px 分隔线；`max-width: 900px` 下自动回到上下单列。验证通过：`frontend npm run build`（保留 Vite chunk warning）、目标 `git diff --check`、本地 Chrome 触发“开始会议纪要”后确认桌面左右结构、录音状态和收起动作可用。

- 2026-05-11T15:58:32Z 已修复 FEAT-029 触发语过窄导致“开始进行会议纪要”不打开右侧会议纪要面板的问题。`frontend/src/assistant/meetingMinutesCommand.ts` 新增口语化命令识别 helper，`AssistantApp.tsx` 改为复用该 helper，新增 `meetingMinutesCommand.test.ts` 覆盖“开始进行会议纪要”“帮我开始做会议记录吧”“开启实时会议听记。”以及解释型问题不误触发。验证通过：`frontend npm run test -- meetingMinutesCommand.test.ts`、`frontend npm run build`（保留 Vite chunk warning）、目标 `git diff --check`。

- 2026-05-11T15:50:10Z 已按用户要求再次重启本地前后端服务。执行 `docker compose up -d --remove-orphans postgres redis rabbitmq qdrant` 后 PostgreSQL、Redis、RabbitMQ、Qdrant 保持运行；旧 `cici-backend` / `cici-frontend` screen 会话和 8080/5173 监听已停止；新 detached `screen` 会话为 `35525.cici-backend` 与 `35528.cici-frontend`。验证通过：`GET http://127.0.0.1:8080/actuator/health` 返回 `{"status":"UP"}`，`HEAD http://127.0.0.1:5173/` 返回 `HTTP/1.1 200 OK`。

- 2026-05-11T15:45:53Z FEAT-029 讯飞真实链路已跑通。用户提供实时语音转写大模型 AppID/APIKey/APISecret 后，已通过 `/integrations/iflytek_asr` 写入本地 `demo-org`，API 只回显 `iflytek-****`，数据库中 Secret 为加密 object。发现并修复两处后端适配问题：讯飞 provider 必须等上游 `data.action=started` 后再通知客户端开始发音频；讯飞结果结构为 `data.cn.st.rt[].ws[].cw[]`，需要按 `rt[]` 数组提取文本；同时为 Java `HttpClient` WebSocket 补 `request(1)` 和串行化 binary 发送，前端录音 hook 等服务端 `started` 后再发 PCM。验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -DskipTests compile`、`frontend npm run build`（保留 Vite chunk warning）、重启后端健康、`/ws/asr?provider=iflytek&speakerDiarization=true` 发送 16k PCM 测试音频收到 `status started`、`final "Helods"`、多个 `partial`、`final ", a real time transcription test four."`，并带 `speakerId/speakerName`。

- 2026-05-11T15:18:28Z 用户确认“实时语音转写大模型额度已开通”后再次复测。后端健康；`integration_app(app_code="iflytek_asr")` 仍为启用状态，URL 为 `wss://office-api-ast-dx.iflyaisol.com/ast/communicate/v1`，Secret 为加密 object；`GET /integrations` 仍只回显 `iflytek-****`。后端 `/ws/asr?provider=iflytek&speakerDiarization=true` start 仍返回 `invalid ws text message`；原始 TLS WebSocket Upgrade 对官方最小参数和带 `role_type=2/pd=com` 参数均仍返回 `35010 AccessKeyId Not Exists`。结论：额度开通后当前本地保存的 Access Key ID 仍未被讯飞 AST realtime 接受，需核对已开通额度的服务页 APIKey/APISecret 是否与管理后台保存值完全一致，或等待/联系讯飞确认服务绑定。

- 2026-05-11T15:11:24Z 用户将 `realtimeUrl` 改为 `wss://office-api-ast-dx.iflyaisol.com/ast/communicate/v1` 后复测。配置检查通过：后端健康；`integration_app(app_code="iflytek_asr")` 已启用，URL 正确，App ID / Access Key ID 存在，Secret 是加密 object；`GET /integrations` 仅回显 `iflytek-****`。真实 `/ws/asr?provider=iflytek&speakerDiarization=true` start 仍返回泛化 `invalid ws text message`；不输出密钥的原始 TLS WebSocket Upgrade 对官方最小参数和带 `role_type=2/pd=com` 参数均返回 `35010 AccessKeyId Not Exists`。结论：地址已正确，当前阻塞转为讯飞 AST 服务不认可所填 APIKey/AccessKeyId 或该 App 未开通对应服务。

- 2026-05-11T13:57:15Z 按用户说明“讯飞配置信息已填写完成”执行真实配置 smoke。验证 `POST /auth/password/login` 管理员登录成功；`GET /integrations` 返回 `iflytek_asr` 已启用，`appId/accessKeyId` 均存在，`accessKeySecret` 只回显 `iflytek-****`；数据库 `integration_app` 中 `accessKeySecret` 为加密 object。随后通过后端 `/ws/asr?provider=iflytek&speakerDiarization=true` 发起 start，后端返回泛化 `invalid ws text message`；进一步用不输出密钥的原始 TLS WebSocket Upgrade 探测确认：当前保存的 `wss://spark-api.xf-yun.com/v4.0/chat` 返回 `401 Unauthorized`，FEAT-029 默认 AST 地址 `wss://office-api-ast-dx.iflyaisol.com/ast/communicate/v1` 返回 `35010 AccessKeyId Not Exists`。结论：本地代码可读取配置并尝试签名握手，但当前配置/凭证不是可用于 FEAT-029 实时转写的有效 AST 凭证，真实转写尚未跑通。

- 2026-05-11T13:42:26Z 已按用户确认把 FEAT-029 的讯飞凭证做成可配置入口：`IntegrationAppService` 新增内置 `iflytek_asr`（讯飞实时转写），字段为 `appId`、`accessKeyId`、`accessKeySecret`、`realtimeUrl`、`lang`、`domain`，其中 `accessKeySecret` 使用 `SecretCipherService` 加密存储、前端回显 `iflytek-****`；`AliyunRealtimeAsrWebSocketHandler` 运行时优先读取当前组织 `integration_app(app_code="iflytek_asr")`，未保存组织配置时保留 `app.voice.iflytek.*` yml 兜底，管理员停用集成时明确停用；`frontend/src/admin/pages/AdminIntegrationsPage.tsx` 增加“讯飞实时转写”字段说明，员工会议听记缺配置错误会指向“管理后台 -> 集成应用 -> 讯飞实时转写”。验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=TavilyToolServiceTest test`、`backend mvn -q -Dmaven.repo.local=.m2 -DskipTests compile`、`frontend npm run build`（保留既有 chunk warning）、目标 `git diff --check`、Playwright 桌面与 390px 移动 `/admin/integrations` 检查；截图在 `output/playwright/feat-029-iflytek-integration-desktop.png` 与 `output/playwright/feat-029-iflytek-integration-mobile.png`。

- 2026-05-11T13:25:57Z 已实现 FEAT-029 / TASK-081 第一版。新增 `docs/specs/FEAT-029-meeting-minutes-live-transcription.md`；`frontend/src/assistant/AssistantApp.tsx` 识别“开始会议纪要/开始会议记录/开始会议听记”等触发语，不进入普通聊天模型，而是打开右侧 `cici-meeting-drawer`，自动启动麦克风，按 `speakerId/speakerName` 追加转写段落，结束后调用 `/ai/meeting-minutes/summary` 展示 Markdown 纪要；`frontend/src/shared/useAsrVoiceInput.ts` 支持 `provider=iflytek`、`speakerDiarization` 和 transcript event 回调。后端 `AliyunRealtimeAsrWebSocketHandler` 保持阿里云默认路径，同时新增讯飞 WebSocket 签名、`role_type=2`、PCM 转发、结束帧和弹性结果解析；新增 `MeetingMinutesController` / `MeetingMinutesService`。验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -DskipTests compile`、`frontend npm run build`（保留既有 chunk warning）、目标 `git diff --check`、in-app Browser 桌面与 390px 移动触发检查。因浏览器自动化未获麦克风授权，本轮验证到权限拒绝错误态，未声称真实讯飞云端转写成功。

- 2026-05-11T12:45:16Z 已按用户要求再次重启本地前后端服务。执行 `docker compose up -d --remove-orphans postgres redis rabbitmq qdrant` 后 PostgreSQL、Redis、RabbitMQ、Qdrant 保持运行；旧 `cici-backend` / `cici-frontend` screen 会话和 8080/5173 监听已停止；新 detached `screen` 会话为 `72258.cici-backend` 与 `72261.cici-frontend`。验证通过：`GET http://127.0.0.1:8080/actuator/health` 返回 `{"status":"UP"}`，`HEAD http://127.0.0.1:5173/` 返回 `HTTP/1.1 200 OK`。

- 2026-05-11T09:55:53Z 已实现 CloudCC 二次开发专家技能运行配置方式：`CloudccAccessTokenService.CloudccSessionContext` 新增 `setupSvc`，统一处理 `https://ap10.apis.cloudcc.cn/lightningapi` -> `https://ap10.apis.cloudcc.cn/setup` 和无 path 追加 `/setup`；`CloudccOpenApiService` 的 Setup API 调用改为使用 `ctx.setupSvc()`；新增 `BuiltinSkillRuntimeConfigService`，仅在 CloudCC 专家技能激活、命中文档或 always-on 时解析当前用户 CloudCC token，并向 prompt 注入 `setupSvc` 与 accessToken 服务端可用说明，不泄露明文 token。FEAT-028 与 TASK-080 已同步记录。验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=FileBackedBuiltinSkillIntegrationTest test`、`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillGovernanceIntegrationTest#shouldCompilePublishAndInvokeDeclarativeRuntimeApis test`、`backend mvn -q -Dmaven.repo.local=.m2 -DskipTests compile`、目标 `git diff --check`。新增测试同时验证 Setup 工具实际请求 `/setup/api/customObject/standardObjList` 且 `accessToken` 请求头来自服务端绑定。

- 2026-05-11T07:56:20Z 已按用户截图修正智能体构建器“基础模型”下拉：`ModelProviderService.configuredModelsForProvider` 移除 `ProviderDef.defaultModels()` 回退，`GET /models/agent/base-models` 只暴露真实已选模型或已有场景映射模型，避免 qwen/deepseek/Ollama/Claude/OpenAI 等预制推荐项在未配置时进入构建器。`frontend/src/assistant/AgentBuilderShell.tsx` 同步去掉 option 里重复追加厂商名的问题。新增 `ModelProviderServiceIntegrationTest` 覆盖空配置不返回预制模型、配置阿里云已选模型后只返回该厂商模型。验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=ModelProviderServiceIntegrationTest test`、`frontend npm run build`（保留 Vite chunk-size warning）、目标 `git diff --check`。

- 2026-05-11T07:24:14Z 已按用户要求重启本地前后端服务。`docker compose up -d --remove-orphans postgres redis rabbitmq qdrant` 确认 PostgreSQL、Redis、RabbitMQ、Qdrant 运行；旧 Java/Node 监听进程已停止；新 detached `screen` 会话为 `3261.cici-frontend` 与 `3258.cici-backend`。验证通过：`GET http://127.0.0.1:8080/actuator/health` 返回 `{"status":"UP"}`，`HEAD http://127.0.0.1:5173/` 返回 `HTTP/1.1 200 OK`。

- 2026-05-11T07:09:18Z 已实现并验证 FEAT-028 第一版。新增 `backend/src/main/resources/builtin-skills/cloudcc-customization-expert-common/`，包含 `manifest.json`、`SKILL.md` 和 CloudCC 模块文档；新增 `V48__file_backed_builtin_skills.sql`，为平台模板和模板版本补 `resource_type`、`resource_uri`、`bundle_checksum`、`entrypoint_checksum`、`module_manifest_json`。后端新增 `FileBackedBuiltinSkillCatalog`、`FileBackedBuiltinSkillSyncService`、`BuiltinSkillDocumentService`，并接入 `SkillDefinitionService.ensurePhaseOneDefaults`、`SkillPromptAssembler` 和 `ChatOrchestratorService`。`GET /skills` 现在返回文件型标准技能 resource 元数据，`GET /skills/{id}/builtin-docs` 返回模块/checksum 只读摘要。验证通过：`FileBackedBuiltinSkillIntegrationTest`、`SkillGovernanceIntegrationTest`、后端 compile、后端 package 和 jar 内资源检查。

- 2026-05-11T06:48:47Z 已新增 `docs/specs/FEAT-028-file-backed-builtin-skills.md` 并在 `.claw/task-board.md` 增加 `TASK-079`。规格确认文件型内置标准技能是 Cici 应用内能力：技能包随代码发布、多组织共享；数据库只存轻量治理索引、版本/checksum、resource URI、组织启用状态和 Agent 绑定关系；CloudCC 官方模块文档正文不写入 PostgreSQL、不导入租户知识库或 Qdrant。文档同时定义 `FileBackedBuiltinSkillCatalog`、`FileBackedBuiltinSkillSyncService`、`BuiltinSkillDocumentService` 职责，以及 `cloudcc-customization-expert-common` 首包落地路径和 manifest 结构。

- 2026-05-11T06:21:47Z 已在本地开发数据库 `cici_assistant` 中创建/确保组织 `Demo-org2`（name/status 为 `Demo-org2`/`ACTIVE`）、账号 `18612345678`、`MOBILE` 登录标识和 `organization_member` 关系，成员角色为 `ORG_ADMIN`、状态为 `ACTIVE`。验证通过：`curl -X POST http://127.0.0.1:8080/auth/password/login` 使用 `orgId=Demo-org2`、`identifier=18612345678`、`password=szyd1234` 返回 `success=true`、`roles=["ORG_ADMIN"]` 且包含 token。

- 2026-05-10T23:33:00Z 已按用户要求将 `agentcici.salesforchina.com` 配到 Cici 应用：更新 `deploy/nginx.cici.ssl.conf` 与线上 `/opt/cici/deploy/nginx.cici.ssl.conf`，将该域名加入 80/443 `server_name`，并 reload `cici-frontend` Nginx。ECS 本机 `Host: agentcici.salesforchina.com` HTTP 访问校验文件返回 `HTTP/1.1 200 OK` 和 `fWLFCmXQ3JU36hfZ`；本地使用 `curl --resolve agentcici.salesforchina.com:80:47.97.119.160` 也返回 200。用户随后将 DNS A 记录改到 `47.97.119.160`，`dig @8.8.8.8` / `dig @223.5.5.5` 均已返回新 IP；本机普通 curl 仍一度命中旧缓存 `49.97.119.160`。同轮已为 HTTP `/wecom/` 添加后端代理例外，`curl --resolve ... http://agentcici.salesforchina.com/wecom/kf/callback?...` 返回后端 500（缺企业微信签名参数的预期后端命中信号）而非 301 跳转。

- 2026-05-10T16:18:00Z 已协助企业微信可信域名校验：将 `/Users/owenspace/Downloads/WW_verify_fWLFCmXQ3JU36hfZ.txt` 内容 `fWLFCmXQ3JU36hfZ` 加入 `frontend/public/WW_verify_fWLFCmXQ3JU36hfZ.txt`，本地 `frontend npm run build` 成功且 `dist/` 包含验证文件；同时通过 `scp` + `docker cp` 将验证文件放入线上 `cici-frontend:/usr/share/nginx/html/`，并更新 `deploy/nginx.cici.ssl.conf` 与线上挂载配置，让 `http://autoservice.agentcici.com/WW_verify_fWLFCmXQ3JU36hfZ.txt` 在 Nginx 80 端口直出而不是跳 HTTPS。ECS 本机以 `Host: autoservice.agentcici.com` 访问 HTTP 与 HTTPS 均返回 200 和正确内容；公网 HTTP 仍返回阿里云 Beaver `Non-compliance ICP Filing`，说明请求在云侧备案检查处被拦截，下一步需完成 `autoservice.agentcici.com` 备案接入/主体关联。

- 2026-05-10T15:13:18Z 修复 V1.8 源码发布遗漏：用户反馈“V1.8 没有发布成功”后复核发现 ECS 已运行 `CICI_IMAGE_TAG=V1.8` 且公网渲染正确，但 GitHub `origin/main` 和远端 tag 仍停在 `V1.7`。已执行 `git push origin HEAD:main` 与 `git push origin V1.8`；远端 `main` 与 `V1.8^{}` 均指向 `1b2ea27c55660d094174a1544199157f8ba8321d`。ECS 复核：六容器 healthy，后端 health `UP`，Nginx `-t` 通过。Browser 渲染复核：`https://agentcici.com/` 与 `https://www.agentcici.com/` 标题为 `AI 治理平台 | 企业 AI 客户运营套件` 并渲染 SalesMost AI Suite 中文站，`https://autoservice.agentcici.com/` 仍渲染 AgentCiCi 产品登录页且预约链接为 `https://agentcici.com/#demo`。

- 2026-05-10T13:40:12Z 已完成 V1.8“综合官网”发布：`frontend/src/App.tsx` 将 `agentcici.com` / `www.agentcici.com` 根路径从 AutoService 中文站切到 FEAT-027 `SuiteLanding siteOverride="china"`，`frontend/src/assistant/AssistantApp.tsx` 将登录页“立即预约”改为 `https://agentcici.com/#demo`。已推送 ACR `cici-backend:V1.8/latest` digest `sha256:ad86b98c3f01fed15f5716da3c33a9344e7780488f8367c8f6dca704f5e12754`、`cici-frontend:V1.8/latest` digest `sha256:7a08acd0ff945f13a66a780db1a5776fc9feac35013493258ed049b729c75f6f`，并为 database/redis/rabbitmq/qdrant 补 `V1.8` manifest alias。阿里云 ECS 备份目录为 `/opt/cici/backups/20260510-213605-before-v1.8-suite-site`；远端六容器 healthy，后端 health `UP`，Nginx `-t` 通过，Flyway 最新 `47|account profile and password|true`。Playwright 公开站验证：`https://agentcici.com/` 与 `https://www.agentcici.com/` 标题为 `AI 治理平台 | 企业 AI 客户运营套件` 并渲染 SalesMost AI Suite 中文站，`https://autoservice.agentcici.com/` 仍渲染 AgentCiCi 产品登录页。

- 2026-05-10T13:24:52Z 已按用户截图继续收敛 FEAT-027 suite 页面文案：`frontend/src/suite/SuiteLanding.tsx` 将 hero 可视化标题改为业务维度的“全链路客户运营闭环”，并去掉页面可见语言/地域标签表达；同步更新 `docs/specs/FEAT-027-ai-customer-ops-suite-website-design.md`、`.claw/task-board.md` 和 `.claw/test-report.md`。验证通过：`frontend npm run build`（保留 Vite chunk-size warning）、目标 `git diff --check`、静态 `rg` 无命中、Playwright snapshot 确认标题为“全链路客户运营闭环”（仅有既有 `/favicon.ico` 404）。

- 2026-05-10T13:20:36Z 已按用户反馈收敛 FEAT-027 suite 中文站文案：`frontend/src/suite/SuiteLanding.tsx` 将 SEO、hero 可视化标题、生命周期 intro、平台层 intro、市场差异 kicker 和 intro 中的地域化表达改为“全链路客户运营”“企业客户”“落地治理”等业务表述，并同步更新 `docs/specs/FEAT-027-ai-customer-ops-suite-website-design.md` 与 `.claw/task-board.md`。

- 2026-05-10T13:16:42Z 已按浏览器批注修正 FEAT-027 suite 中文站页头文案：`frontend/src/suite/SuiteLanding.tsx` 将中文站导航 `平台底座` 改为 `AI 治理平台`，并将左上品牌强标题从条件显示 `AI 治理平台` 改回统一 `SalesMost AI`。验证通过：`frontend npm run build`（保留 Vite chunk-size warning）、`git diff --check -- frontend/src/suite/SuiteLanding.tsx`、静态 `rg` 确认目标导航和品牌文案已更新。

- 2026-05-10T13:04:46Z 已完成当前系统能力与待补功能评估：核对 `.claw/` 状态、`PROJECT-BASELINE.md`、README、前端路由、后端控制器、迁移、关键 FEAT 规格和 issue/test 记录；本轮未改业务代码、未运行新增测试。评估结论：系统已具备多组织 Agent runtime/governance、助手/管理/平台三端、RAG/Skill/MCP/Open API、账号生命周期、企业微信客服配置、公开站原型等厚底座；P0 缺口集中在真实企业微信端到端 smoke、售后只读业务工具、CloudCC 凭证/API key 阻塞、计量账单落地、生产级生命周期 worker/告警与 Open API/观测闭环增强。

- 2026-05-10T12:57:34Z 已继续 FEAT-023 管理端配置闭环：新增 `/admin/channels/wechat-kf` 组织控制台入口和 `AdminWecomKfAccountsPage`，采用账号列表 + 详情表单 split-pane，支持读取 `/admin/wecom/kf-accounts`、新增/更新客服账号、启停账号、复制企业微信回调 URL、选择售后 Agent 与 run-as 服务用户；编辑已有账号时 Token/Secret/EncodingAESKey 留空保持原值。路由特意避开 `/admin/wecom` API 代理前缀，避免 Vite/Nginx 把页面请求转发到后端。验证通过：`frontend npm run build`（保留 Vite chunk-size warning）、目标 `git diff --check`、Playwright 桌面/390px 移动截图与 DOM/computed style 检查；截图产物 `output/playwright/wecom-kf-admin-desktop.png`、`output/playwright/wecom-kf-admin-mobile.png`。

- 2026-05-10T12:54:14Z 已按用户反馈重构 FEAT-027 suite 站点视觉方向：`frontend/src/suite/SuiteLanding.tsx` 将 hero 从生命周期地图改为 prompt-to-workflow 的 AI 员工演示面板，并为产品矩阵补角色化 avatar；`frontend/src/suite/suite-site.css` 整体重写，移除全页背景网格、金色/鎏金色 token 和旧暖象牙金边语汇，改为 getSwan 参考下的白底、强黑标题、饱和蓝 CTA、sky/lime/coral/violet 分区和轻量折纸感视觉。`docs/specs/FEAT-027-ai-customer-ops-suite-website-design.md` 已记录该视觉例外和 no-grid/no-gold 约束。验证通过：`frontend npm run build`（保留 Vite chunk-size warning）、目标 `git diff --check`、system Chrome 截图；产物 `output/playwright/suite-cn-redesign-desktop-v2.png`、`suite-cn-redesign-mobile-v8.png`、`suite-global-redesign-mobile-v4.png`。

- 2026-05-10T12:19:24Z 已实现 FEAT-027 综合门户静态原型：新增 `frontend/src/suite/SuiteLanding.tsx`、`frontend/src/suite/suite-site.css`，并在 `frontend/src/App.tsx` 增加 `/suite` -> `/suite/cn`、`/suite/cn`、`/suite/global` 路由。中文站强调企业微信、微信客服、飞书、钉钉、CloudCC CRM、私有化/混合部署和可审计治理；国际站强调 global outbound、WhatsApp/email follow-up、Salesforce/HubSpot/Zendesk/Intercom/ServiceNow/custom API、跨境增长和 CRM-centered operations。两站均独立于 AutoService 官网，使用 `suite-` 样式前缀，未复用 `as-` 组件/弹窗/样式；CTA 表单目前为前端静态成功态，未接真实后端线索模型。验证通过：`frontend npm run build`（保留 Vite chunk-size warning）、目标 `git diff --check`、Playwright wrapper DOM 检查、system Chrome 桌面/390px 移动截图；截图产物 `output/playwright/suite-cn-desktop.png`、`suite-cn-mobile.png`、`suite-global-desktop.png`、`suite-global-mobile.png`。

- 2026-05-10T11:55:59Z 已新增 FEAT-027 综合主站设计文档：`docs/specs/FEAT-027-ai-customer-ops-suite-website-design.md` 定义一个独立 brand / marketing website，用于表达 AutoReachAI 获客触达、FollowUpAI 销售跟进、AutoService 售后服务三款应用矩阵，以及 CiCi Agent runtime/governance 和 CloudCC CRM 数据闭环。文档明确主站不与现有 AutoService 官网耦合，不复用 `frontend/src/autoservice`、`as-` 样式前缀、AutoService 预约弹窗或 demo request 命名；建议后续新增 `/suite`、`frontend/src/suite/`、`suite-` 样式前缀，并评审 `SalesMost AI Suite` 工作品牌名、域名/路由、语言策略和 `suite_demo_request` 或通用 website leads 扩展。

- 2026-05-10T11:32:31Z 已继续 FEAT-023 企业微信微信客服接入：新增内置 `after-sales-agent` 售后服务 Agent，默认用于微信客服外部客户会话，提示词明确当前阶段只基于已授权知识库和客户文字沟通，不查询/操作 CRM、订单、客户档案、工单或物流系统；`ChatOrchestratorService` 对 `wecom-kf:*` 会话强制采用知识库优先策略，默认知识库存在时即使客户问题包含“订单/客户/查询”等业务词也触发 RAG，并且不加载任何业务工具定义，避免误调 CRM；新增 `/admin/wecom/kf-accounts` 组织管理 API，支持创建/更新/启停企业微信客服账号配置，密文保存 Secret 与 EncodingAESKey，默认绑定 `after-sales-agent` 和当前 run-as 用户；本地 Vite 与部署 Nginx 已补 `/admin/wecom` 代理。验证通过：`ChatOrchestratorServiceModelIdentityTest,WecomKfConfigServiceTest,WecomKfCryptoServiceTest,WecomKfConversationEntityTest`、后端 compile、前端 `npm run build`（保留 Vite chunk-size warning）、目标 `git diff --check`。
- 2026-05-10T01:27:23Z 已完成 FEAT-024 邮箱登录标识闭环：`PUT /auth/me/profile` 保存个人简档邮箱时同步维护 `account_login_identifier(EMAIL)`，清空邮箱会删除 EMAIL 标识；`POST /auth/password/login` 新增 `identifier` 并兼容旧 `mobile` 字段，服务端自动识别手机号或邮箱。前台、管理端和平台登录页都保持单个“电子邮件地址或手机号码”输入框，不新增第二个邮箱框。验证通过：`AuthFlowIntegrationTest`、后端 `-DskipTests compile`、前端 `npm run build`（保留既有 Vite chunk-size warning）、目标 `git diff --check`、Playwright 桌面/390px 移动检查，截图 `output/playwright/email-login-single-field-desktop.png` 与 `output/playwright/email-login-single-field-mobile.png`。
- 2026-05-10T01:11:20Z 已按用户确认修正 FEAT-024 范围口径：当前系统尚未正式生产上线，公网环境属于 UAT 公测阶段；如账号模型或生命周期 schema 需要调整，可全新部署并重建数据库、文件存储和向量库，不再把旧 `app_user` 历史数据完整生产迁移作为 FEAT-024 必须交付项。已更新 `docs/specs/FEAT-024-account-tenant-lifecycle-and-data-retention.md` 与 `.claw/task-board.md`，后续重点转为邮箱登录标识验证、密码重置/MFA/SSO、订阅联动、外部对象存储/生产向量库巡检、Owner 关闭申请、运营告警和独立 worker。
- 2026-05-09T16:30:31Z 已按浏览器批注调整前台 rail 齿轮入口提示语：`frontend/src/assistant/AssistantApp.tsx` 将设置按钮 `data-menu-label` 和 `aria-label` 从“组织设置”改为“设置”，不改变点击后进入组织/个人配置页面的行为。验证通过：`frontend npm run build`（保留既有 Vite chunk-size warning）、`git diff --check -- frontend/src/assistant/AssistantApp.tsx`、in-app Browser 检查 `设置` button count=1、`组织设置` button count=0。
- 2026-05-09T16:28:27Z 已按浏览器批注调整个人简档表单密度：`frontend/src/assistant/MyEmailAccountsModal.tsx` 移除邮箱 label 的 `cici-profile-form__wide`，邮箱输入框在桌面端跟随 profile form 两列网格，不再独占整行；窄屏仍按现有媒体查询单列显示。验证通过：`frontend npm run build`（保留既有 Vite chunk-size warning）、`git diff --check -- frontend/src/assistant/MyEmailAccountsModal.tsx`、in-app Browser 截图确认邮箱输入框为半宽列。
- 2026-05-09T16:26:12Z 已按浏览器批注移除个人简档页内部两个小标题：`frontend/src/assistant/MyEmailAccountsModal.tsx` 不再渲染“我的头像”和表单区“个人信息”section header，仅保留 section `aria-label` 和顶部“个人信息 / 修改密码”text tabs。验证通过：`frontend npm run build`（保留既有 Vite chunk-size warning）、`git diff --check -- frontend/src/assistant/MyEmailAccountsModal.tsx`、in-app Browser 检查 `我的头像` heading count=0、`个人信息` heading count=0、两个 tabs 仍存在。
- 2026-05-09T16:20:42Z 已按浏览器批注继续收敛个人简档设置页：`frontend/src/assistant/MyEmailAccountsModal.tsx` 将 profile surface 拆为“个人信息 / 修改密码”两个文本 tab，密码表单不再堆叠在个人信息下方；`frontend/src/assistant/cici-ui.css` 为 profile surface 单独移除页头 border、profile tab 底部分隔线和 profile 内部 `.cici-modal__section` 横线，保留金色 active underline。验证通过：`frontend npm run build`（保留既有 Vite chunk-size warning）、`git diff --check -- frontend/src/assistant/MyEmailAccountsModal.tsx frontend/src/assistant/cici-ui.css`、in-app Browser 桌面与 390px 移动截图检查，两个 tab 均可切换且头像/个人信息/密码区域无旧横线。
- 2026-05-09T16:00:45Z 已按浏览器批注继续拆分前台设置：`frontend/src/assistant/AssistantApp.tsx` 将左上头像恢复为“个人简档”入口，右侧主区域单独展示头像、姓、名、显示名称、手机号、邮箱和个人密码修改；齿轮入口改为“组织设置”，设置页标题显示当前组织名并移除“个人资料”tab。`frontend/src/assistant/MyEmailAccountsModal.tsx` 新增 `surface="profile|settings"`，设置页只保留我的工作流、绑定沟通渠道、我的邮箱、专属记忆；`frontend/src/assistant/cici-ui.css` 补 profile active 和表单布局。后端新增 `V47__account_profile_and_password.sql`、`account_auth_credential`、`PUT /auth/me/profile`、`PUT /auth/me/password`，无个人密码的老账号继续兼容固定密码，用户修改后优先使用 per-account 密码。验证通过：`frontend npm run build`（保留既有 Vite chunk-size warning）、`backend mvn -q -Dmaven.repo.local=.m2 -DskipTests compile`、`AuthFlowIntegrationTest`、in-app Browser 桌面与 390px 移动检查，头像页显示“个人简档/修改密码/显示名称”，齿轮页标题为 `Demo Organization` 且不再出现“个人资料”tab。
- 2026-05-09T15:38:49Z 已按浏览器批注修正前台助手个人设置入口：`frontend/src/assistant/AssistantApp.tsx` 新增 `settings` 工作区 tab，左上头像不再作为 button 或触发个人设置，下方设置图标点击后在右侧主区域渲染个人设置页面；`frontend/src/assistant/MyEmailAccountsModal.tsx` 支持 `variant="page"`，页面模式不再使用 modal backdrop、`role="dialog"` 或关闭按钮；`frontend/src/assistant/cici-ui.css` 增加设置页容器并移除头像设置行的内层背景框。验证通过：`frontend npm run build`（保留既有 Vite chunk-size warning）、`git diff --check -- frontend/src/assistant/AssistantApp.tsx frontend/src/assistant/MyEmailAccountsModal.tsx frontend/src/assistant/cici-ui.css`、in-app Browser 桌面/390px 移动截图检查，头像点击后仍停留工作台且无 dialog/backdrop，设置图标点击后 `.cici-settings-page=1`、settings rail active、`dialog=0`、`backdrop=0`。
- 2026-05-09T15:24:12Z 已按用户截图修正前台登录页“立即预约”跳转：`frontend/src/assistant/AssistantApp.tsx` 将链接改为 `https://agentcici.com/?demo=1`；`frontend/src/autoservice/AutoServiceLanding.tsx` 新增 `demo=1|true` 深链识别，进入官网后直接打开既有预约演示 modal，并把 `location.search` 纳入 demo request `sourcePath`。验证通过：`frontend npm run build`（保留既有 Vite chunk-size warning）、`git diff --check -- frontend/src/assistant/AssistantApp.tsx frontend/src/autoservice/AutoServiceLanding.tsx`、Playwright CLI 检查 `/autoservice/cn?demo=1` 存在 `.as-demo-modal` 且背景滚动锁定，前台登录页链接 href 为 `https://agentcici.com/?demo=1`。
- 2026-05-09T15:23:50Z 已按用户截图将默认浏览器页签标题改为 `AgentCiCi`：`frontend/index.html` 的 `<title>` 从 `CiCi · 数字员工助手` 改为 `AgentCiCi`。验证通过：静态 `rg` 确认默认 title 已更新，`frontend npm run build` 成功（保留既有 Vite chunk-size warning）。
- 2026-05-09T13:18:34Z 已按用户截图修正管理端 `/admin/login` 视觉不统一问题：`frontend/src/admin/AdminLogin.tsx` 增加登录页专用结构和右侧权限说明，将“固定密码”统一为“密码”；`frontend/src/styles.css` 为 `.admin-login` 接入 `鎏金账房` admin token，覆盖旧青蓝背景/按钮/notice 级联，改为暖象牙 canvas、金色边框、金色主按钮、紧凑输入和移动端单列。验证通过：`frontend npm run build`（保留既有 Vite chunk-size warning）、`git diff --check -- frontend/src/admin/AdminLogin.tsx frontend/src/styles.css`、Playwright + system Chrome 桌面/390px 移动截图 `output/playwright/admin-login-unified-desktop.png` 与 `output/playwright/admin-login-unified-mobile.png`，移动端 `scrollWidth=clientWidth=390`，页面标签为“组织 ID/手机号/密码”，按钮和 notice 均为金色体系，正文不再包含“固定密码”。
- 2026-05-09T13:15:00Z 已按用户要求完成 V1.7 线上发布与 AgentCiCi 域名切换：`agentcici.com` 和 `www.agentcici.com` 作为中文官网入口，`autoservice.agentcici.com` 作为产品登录入口；中文官网“登录”链接固定指向 `https://autoservice.agentcici.com`。已推送 ACR `cici-backend:V1.7/latest` digest `sha256:2336525817b5f8e43adc2f737510d5679a0d99607fc848c5a5e23ae4c8a6c2d4`，`cici-frontend:V1.7/latest` digest `sha256:879dbd9c589c6387143ac1a04a9bf041b500cf90cbb8fe288659442a59b5cd49`；线上备份目录 `/opt/cici/backups/20260509-210523-before-agentcici-v1.7-domain-account`。生产发布中执行了 Flyway 历史 checksum repair（V1/V8/V9）和老 `app_user` 到 `user_account` / `account_login_identifier` / `organization_member` 的幂等补齐，保留 `app_user.id` 作为 `organization_member.id`；Flyway 已到 V46，六容器 healthy，固定密码登录 smoke 成功，in-app browser 验证中文官网标题与登录链接、autoservice 子域名产品登录页均正确。
- 2026-05-09T12:40:00Z 准备提交并推送 `V1.7` 版本，版本备注为“全局账号结构，autoservice官网注册”。本次 release 范围覆盖全局账号/组织成员结构、租户生命周期与数据保留/销毁能力、AutoService 官网注册预约链路和平台侧网站线索管理；本地 Playwright 临时输出、知识库上传数据、测试结果与截图输出已加入 `.gitignore`，避免误入版本提交。
- 2026-05-09T10:54:14Z 已按浏览器批注收敛前台登录页：`frontend/src/assistant/AssistantApp.tsx` 将登录标签“固定密码”改为“密码”，移除“新手机号，创建组织”注册/创建组织切换入口，并将底部“需要配置知识库或成员？管理控制台”改为“还没有账户？立即预约”，链接到 `/autoservice/cn` 官网预约页。`frontend/src/styles.css` 为 login-mode2 外层、表单壳、输入和按钮补 box-sizing / 小屏宽度约束，修复真实 390px 视口横向溢出风险。验证通过：`frontend npm run build`（保留既有 Vite chunk-size warning）、`git diff --check -- frontend/src/assistant/AssistantApp.tsx frontend/src/styles.css`、CDP 390px DOM 检查 `scrollWidth=390`、旧文案均不存在、`passwordLabel=密码`、`linkHref=/autoservice/cn`，截图产物 `output/playwright/login-reservation-link-desktop.png` 与 `output/playwright/login-reservation-link-mobile.png`。
- 2026-05-09T10:40:09Z 已按用户要求修正网站注册/预约演示功能菜单位置：`frontend/src/admin/AdminShell.tsx` 移除组织控制台“预约演示”导航，`frontend/src/platform/PlatformShell.tsx` 新增“网站注册 / 预约演示用户”运营入口，`frontend/src/App.tsx` 将页面挂到 `/platform/website-leads`，新增 `frontend/src/platform/pages/PlatformAutoServiceDemoRequestsPage.tsx` 使用平台 token 与 `/api/platform/autoservice/demo-requests`；后端 `AutoServiceDemoRequestController` 将列表/更新迁到 `/platform/autoservice/demo-requests` 并改为 `@RequirePlatformRole`。验证通过：`AutoServiceDemoRequestIntegrationTest`、`frontend npm run build`（保留既有 Vite chunk-size warning）、目标 `git diff --check`、Playwright CLI mock 平台数据桌面/390px 移动检查，页面标题“网站注册与预约演示”、平台导航包含“网站注册”、旧 `/admin/autoservice-demo-requests` 链接不存在、表格 2 行、桌面/移动 `scrollWidth=clientWidth`；已重启本地 `cici-backend` screen，`/actuator/health` 为 UP，平台 token `GET /platform/autoservice/demo-requests` 返回 200；截图产物 `output/playwright/platform-website-leads-desktop.png` 与 `output/playwright/platform-website-leads-mobile.png`。
- 2026-05-09T10:38:36Z 已按用户要求调整 `/autoservice/cn` 预约演示提交成功态：`frontend/src/autoservice/AutoServiceLanding.tsx` 新增 `demoSubmitted` 状态，成功响应后卸载 `.as-demo-form` 并渲染 `.as-demo-success`；重新打开弹窗时重置为新表单。`frontend/src/autoservice/autoservice-copy.ts` 将中文成功提示改为“感谢您的关注，我们会尽快与您取得联系”，并补成功标题/关闭文案；`frontend/src/autoservice/autoservice-site.css` 新增成功页布局。验证通过：`frontend npm run build`（保留既有 Vite chunk-size warning）、目标前端文件空白检查、Playwright 提交真实预约后 DOM 检查 `hasForm=false`、`hasSuccess=true`、message 精确匹配；移动 390x844 `scrollWidth=clientWidth=390` 且 `modalFits=true`。截图产物 `output/playwright/autoservice-cn-demo-success-desktop.png` 与 `output/playwright/autoservice-cn-demo-success-mobile.png`。
- 2026-05-09T10:35:00Z 已继续 FEAT-024 purge worker 生产化：新增 `V46__organization_purge_worker_lease.sql`，`organization_purge_job` 支持 `worker_id`、`locked_at`、`lock_expires_at`、`attempt_count` 和 `dead_letter_at`；`PlatformTenantLifecycleService.processQueuedPurgeJobs()` 不再用一个大事务包住扫描和执行，而是先用条件更新把 `QUEUED` 真实 purge job 抢占为 `RUNNING` 并提交 lease，再在独立事务执行清理和完成态写入。过期 `RUNNING` lease 会被标记为 `DEAD_LETTER`，保留 worker/lease/result 摘要，避免自动重复清理；平台页补 `DEAD_LETTER` 文案为“死信”。验证通过：`PlatformTenantLifecycleIntegrationTest` 覆盖成功执行写入 worker/attempt、过期 RUNNING job 转死信且不删除业务数据；`frontend npm run build` 通过（保留既有 Vite chunk-size warning）；目标 FEAT-024 文件空白检查通过。
- 2026-05-09T10:25:31Z 已按最新浏览器批注收敛 `/autoservice/cn` 预约演示弹窗：`frontend/src/autoservice/AutoServiceLanding.tsx` 删除弹窗 header 中的 kicker 与说明段落，邮箱字段补 `required`，弹窗打开时同时锁定 `documentElement/body` overflow；`frontend/src/autoservice/autoservice-site.css` 去掉表单 footer 顶部分隔线并收紧弹窗标题/表单间距。验证通过：`frontend npm run build`（保留既有 Vite chunk-size warning）、目标 `git diff --check`、Playwright 桌面 1019x757 DOM 检查 `htmlOverflow=hidden/bodyOverflow=hidden`、弹窗 `clientHeight=scrollHeight=628`、四个必填字段均为 true、kicker/intro 不存在、footer border-top=0；移动 390x844 检查 `scrollWidth=clientWidth=390`、背景滚动锁定、四个必填字段均为 true；截图产物 `output/playwright/autoservice-cn-demo-modal-desktop.png` 与 `output/playwright/autoservice-cn-demo-modal-mobile.png`。
- 2026-05-09T10:16:47Z 已继续 FEAT-024 orphan audit：`VectorStoreClient` 新增只读 `auditOrgVectors` 契约，Memory/Qdrant 实现按 `org_id` 扫描向量点位并和 DB 登记 `kb_chunk.vector_id` 对比；dry-run manifest 新增 `orphanAudit.fileStorage` 与 `orphanAudit.vectorStore`，只返回计数和最多 50 个样本 ID/相对路径，不返回业务正文。真实 purge 在删除已登记 KB 文件后，会继续清理 `data/kb-files/{orgId}` 下的本地残留孤儿文件。验证通过：`PlatformTenantLifecycleIntegrationTest` 覆盖 dry-run 发现 1 个孤儿文件与 1 个孤儿向量、purge 后本地孤儿文件被删除；目标 FEAT-024 后端文件空白检查通过。
- 2026-05-09T09:56:30Z 已继续 FEAT-024 purge job 排队执行闭环：真实销毁和失败重试现在只创建 `QUEUED` 真实 purge job，不在请求线程同步删除；`PlatformTenantLifecycleService.processQueuedPurgeJobs()` 作为 scheduled worker 消费队列，运行前复核 `PENDING_PURGE`、legal hold、确认文本和 source dry-run 新鲜度，执行中标记 `RUNNING`，完成后写入 `SUCCEEDED/FAILED/PARTIAL_FAILED` 和 result 摘要，成功后组织进入 `PURGED`。新增 `POST /platform/tenants/{orgId}/purge-jobs/{jobId}/cancel`，仅允许取消 `QUEUED` 真实 purge job；平台页支持“排队中/已取消”状态、排队行透明文本“取消”动作，并在有 `QUEUED/RUNNING` 真实任务时禁用新的真实销毁/重试。验证通过：`PlatformTenantLifecycleIntegrationTest`、`frontend npm run build`、目标文件空白检查；浏览器 DOM 检查桌面/移动 `/platform/tenants` 可加载、console error=0。截图尝试被 Browser CDP `Page.captureScreenshot` 超时阻断，本轮以前端构建、后端集成测试和 DOM 响应式检查作为页面验证证据。
- 2026-05-09T09:29:27Z 已继续 FEAT-024 purge job 重试闭环：新增 `POST /platform/tenants/{orgId}/purge-jobs/{jobId}/retry`，仅允许 `FAILED/PARTIAL_FAILED` 的真实销毁 job 在组织仍为 `PENDING_PURGE`、legal hold 关闭、原 source dry-run 仍为 24 小时内成功清单且确认文本为 `PURGE {orgId}` 时重试；重试会创建新的真实 purge job、重新生成 manifest/hash、执行 DB/文件/向量清理，成功后组织进入 `PURGED`，并写入 `platform.tenant.purge.retry` 审计。平台 `/platform/tenants` 的 Dry-run 历史表新增操作列，失败真实销毁行显示透明无背景/0 圆角/无阴影的“重试”文本动作，点击后复用真实销毁确认 modal。验证通过：`PlatformTenantLifecycleIntegrationTest`、`frontend npm run build`、目标 `git diff --check`、本地浏览器桌面/移动截图 `output/playwright/feat024-platform-tenants-retry-modal-desktop.png` 与 `output/playwright/feat024-platform-tenants-retry-modal-mobile.png`；移动端 `scrollWidth=clientWidth=390`，桌面 `scrollWidth=clientWidth=1280`，重试文本动作 computed 为透明背景、0 圆角、无阴影，确认重试按钮为 danger 语汇。
- 2026-05-09T09:27:30Z 已按浏览器批注继续收敛 `/autoservice/cn`：删除顶部可见站点文字入口，页头只保留导航与“预约演示”；移动菜单也不再显示跨站入口。信任条删除销售易、纷享销客、用友 YonSuite、金蝶云、有赞，只保留企业微信、钉钉、飞书、CloudCC CRM、Udesk、顺丰、自有 API；CRM/电商集成列表也同步去掉销售易、纷享销客、有赞。人工接管标题改为“转给人工前，先把情况说清楚。”，资源区标题改为“上线后看得见效果，也知道哪里要改。”。验证通过：`frontend npm run build`（保留既有 Vite chunk-size warning）、目标 `git diff --check`、静态 `rg` 无批注残留词、Playwright + system Chrome 当前批注视口截图 `output/playwright/autoservice-cn-comments-fixed-desktop.png`；页面正文 banned words=0，页头 actions 仅“预约演示”，信任条文本符合预期，`scrollWidth=clientWidth=1090`，console error=0。
- 2026-05-09T09:11:03Z 已按最新反馈收敛 `/autoservice/cn` 中文站文案：保留企业微信、微信客服、钉钉、飞书、CloudCC CRM、销售易、纷享销客、Udesk、有赞、顺丰、菜鸟等生态和系统，但页面标题、描述、分区文案不再出现“国内/中国站/面向国内”，改为正常售后功能描述。`frontend/src/autoservice/autoservice-site.css` 已移除全页背景、流程详情、journey card、集成面板、AI Engine 详情和最终 CTA 的网格纹理，并微调首屏处理结果面板位置避免桌面右侧裁切。验证通过：`frontend npm run build`（保留既有 Vite chunk-size warning）、目标 `git diff --check`、Playwright + system Chrome 截图 `output/playwright/autoservice-global-nogrid-desktop.png`、`output/playwright/autoservice-global-nogrid-mobile.png`、`output/playwright/autoservice-cn-nogrid-desktop.png`、`output/playwright/autoservice-cn-nogrid-mobile.png`；中文站 banned words=0、网格背景检测=false、桌面/移动 `scrollWidth=clientWidth`、console error=0、首屏面板未出视口。
- 2026-05-09T08:56:03Z 已按用户要求将 AutoService 官网从“中英文切换”改为两个独立站点页面：`/autoservice/global` 为国际站，`/autoservice/cn` 为中国站，旧 `/autoservice/en` 与 `/autoservice/zh` 仅保留兼容重定向。国际站只保留海外渠道与国际服务栈（WhatsApp、Salesforce、Zendesk、HubSpot、Intercom、ServiceNow、Shopify、Stripe、FedEx、DHL 等），中国站只保留国内渠道与国内系统（企业微信、微信客服、钉钉、飞书、CloudCC CRM、销售易、纷享销客、Udesk、有赞、顺丰、菜鸟等）；顶部入口改成站点入口而非语言切换，并修复首屏产品视觉面板在桌面/移动端遮挡。验证通过：`frontend npm run build`（保留既有 Vite chunk-size warning）、目标 `git diff --check`、内容隔离静态检查、Playwright + system Chrome 截图 `output/playwright/autoservice-global-desktop.png`、`output/playwright/autoservice-global-mobile.png`、`output/playwright/autoservice-cn-desktop.png`、`output/playwright/autoservice-cn-mobile.png`，两站桌面/移动 `scrollWidth=clientWidth`、console error=0、首屏面板不重叠。
- 2026-05-09T08:11:00Z 已继续 FEAT-024 组织生命周期/数据保留：新增 `V44__organization_lifecycle_execution.sql`、`OrganizationExportJobEntity`、`AdminOrganizationLifecycleController` 扩展和导出 job repository；retention policy 支持 legal hold 原因/审批人/审批时间/复核时间，purge job 支持 source dry-run、确认文本、manifest hash 和 result 摘要。平台侧可创建组织导出 job 但禁止下载业务内容归档，组织管理员可下载脱敏 zip；真实 purge 仅允许 `PENDING_PURGE`、无 legal hold、引用 24 小时内成功 dry-run 并输入 `PURGE {orgId}` 后执行，删除 org scoped DB 数据、已登记 KB 文件、导出归档和 VectorStoreClient 已登记向量，成功后组织状态进入 `PURGED`。前端 `/platform/tenants` 已接入组织导出列表、待销毁、真实销毁 modal 和 legal hold 新字段，并兼容旧 manifest 缺字段导致的页面崩溃。验证通过：`PlatformTenantLifecycleIntegrationTest`、`frontend npm run build`、目标 `git diff --check`、本地后端 PostgreSQL Flyway v43 -> v44、API smoke、Playwright 桌面/移动截图 `output/playwright/feat024-platform-tenants-v44-desktop.png` 与 `output/playwright/feat024-platform-tenants-v44-mobile.png`，console error=0，移动端无横向溢出。
- 2026-05-09T07:52:12Z 已按最新浏览器批注替换 AutoService 页头 logo：从用户提供的 `ChatGPT Image 2026年5月9日 15_32_18.png` 裁出 `frontend/public/autoservice-logo-mark.png` 与 `frontend/public/autoservice-logo-word.png`，`frontend/src/autoservice/AutoServiceLanding.tsx` 改为横向展示新图形标与字标，并在图片 URL 上加版本 query 避免浏览器旧缓存；`frontend/src/autoservice/autoservice-site.css` 调整页头 logo 尺寸与间距。验证通过：`frontend npm run build`（保留既有 Vite chunk-size warning）、目标文件 `git diff --check`、in-app browser 桌面/移动确认新 logo 图片节点加载。
- 2026-05-09T07:23:12Z 已按最新浏览器批注强化 AutoService 官网两个视觉薄弱区：`frontend/src/autoservice/AutoServiceLanding.tsx` 将 workflows 区从普通矩阵表重构为 journey board，每条售后旅程以编号、阶段标签、节点和横向路径表达 Knowledge/System data/Playbook/Action/Handoff；integrations 区重构为左侧 AI service layer 拓扑图 + 右侧能力详情与连接器列表，去掉原先“中心方块 + 环绕文字”的低质感表达。验证通过：`frontend npm run build`（保留既有 Vite chunk-size warning）、浏览器桌面/移动视觉检查、console error=0；截图产物 `output/playwright/autoservice-redesign-en-desktop.png` 与 `output/playwright/autoservice-redesign-en-mobile.png`。
- 2026-05-09T06:59:00Z 已按浏览器批注继续调整 AutoService 官网原型：`/autoservice` 与 `/autoservice/en` 为英文版，`/autoservice/zh` 为中文版，页头提供语言切换；`AS` logo 改为鎏金色；主 CTA 从纯黑改为鎏金按钮；`How it works` 详情、Playbook、指标仪表、最终 CTA、集成 hub 等大面积纯黑背景块已改为浅色/鎏金/浅青系统面板；CRM 集成补 `CloudCC CRM`；渠道集合扩展为 Voice、Email、Web chat、Messenger、WhatsApp、SMS、Instagram、In-app、Help center、Mobile SDK、API、Custom channels，并同步到 handoff 摘要。验证通过：`frontend npm run build`（保留既有 Vite chunk-size warning）、Playwright 截图 `output/playwright/autoservice-en-desktop.png`、`autoservice-en-mobile.png`、`autoservice-zh-desktop.png`、`autoservice-zh-mobile.png`，移动端 `scrollWidth=390`、无横向溢出、包含 CloudCC CRM 和扩展渠道、关键背景为浅色渐变。
- 2026-05-09T06:31:20Z 已继续 FEAT-024 组织生命周期/数据保留：新增 `V43__organization_retention_dry_run.sql`、`OrganizationRetentionPolicyEntity`、`OrganizationPurgeJobEntity`、`PlatformTenantLifecycleService` 和 `PlatformTenantLifecycleController`，支持 `GET /platform/tenants`、保留策略详情/保存、冻结/恢复、dry-run purge job 创建和 job 详情；`PlatformTenantLifecycleIntegrationTest` 覆盖平台权限、保留策略、冻结/恢复、拒绝真实 purge、manifest 只计数且不泄露敏感正文。前端新增 `/platform/tenants` 路由和侧栏入口，页面支持租户列表、详情自动选中、保留策略表单、生成 Dry-run Manifest、历史和覆盖表；局部样式解除旧表格 1220px 最小宽度，租户行 hover/selected 保持透明无阴影。验证通过：`PlatformTenantLifecycleIntegrationTest`、`frontend npm run build`、目标文件 `git diff --check`、本地后端重启到 Flyway v43 后浏览器请求均 200、Playwright 桌面/移动截图 `output/playwright/feat024-platform-tenants-desktop.png` 与 `output/playwright/feat024-platform-tenants-mobile.png`、computed style `rowBg=transparent`、`rowShadow=none`、`scrollWidth=clientWidth=1440`。
- 2026-05-09T06:31:00Z 已按 FEAT-026 完成 AutoService 官网设计原型：新增 `frontend/src/autoservice/AutoServiceLanding.tsx`、`autoservice-copy.ts`、`autoservice-site.css`，并在 `frontend/src/App.tsx` 接入 `/autoservice` route。原型采用独立 brand register，不继承后台 `鎏金账房` 产品样式；首版用 CSS/SVG 实现全球 service grid、route lines、channel stream、agent trace、connector map、workflow matrix、AI Engine pipeline 和 handoff summary。验证通过：`frontend npm run build`（保留既有 Vite chunk-size warning）、`git diff --check -- frontend/src/App.tsx frontend/src/autoservice`、Playwright full-page 截图 `output/playwright/autoservice-desktop.png` 与 `output/playwright/autoservice-mobile.png`；移动端检查 `scrollWidth=390`、无横向溢出、矩阵表头隐藏、按钮下划线清除、页面 title 正确。
- 2026-05-09T05:42:00Z 已新增 `docs/specs/FEAT-026-autoservice-website-implementation-design.md`：为 AutoService 全球多渠道 AI 售后服务 Agent 官网落地详细实现设计文档，参考 Ada、Forethought、Decagon、Intercom Fin / Fin.ai，确定主文案 `AI Agents for Global After-Sales Support`，页面不强调企业微信或 CloudCC，重点突出 omnichannel、CRM integrations、business system actions、after-sales playbooks、human handoff、AI Engine、测试监控优化和企业级治理。
- 2026-05-09T05:14:00Z 已按用户要求补齐 `CB` 组织菜单 mouse leave 收起行为：`frontend/src/assistant/AssistantApp.tsx` 新增组织菜单 hover 区域的打开、延迟关闭和取消关闭逻辑；鼠标从 logo 移到菜单时不会闪关，离开 logo/菜单区域后自动关闭。验证通过：`frontend npm run build`（保留既有 Vite chunk-size warning）、`git diff --check -- frontend/src/assistant/AssistantApp.tsx`、Playwright hover `CB` 后 `dialogs=1/expanded=true`，mousemove 到页面主体并等待后 `dialogs=0/expanded=false`。
- 2026-05-09T05:12:00Z 已按用户追加截图修正 `CB` logo hover 行为：`frontend/src/assistant/AssistantApp.tsx` 将 logo 入口从通用 `.cici-rail__menu-btn` tooltip 类中移出，删除 `data-menu-label`，并增加 `onMouseEnter/onFocus` 打开组织切换菜单；hover 时直接展示组织菜单，不再显示“组织：Demo Organization”悬浮提示语。验证通过：`frontend npm run build`（保留既有 Vite chunk-size warning）、`git diff --check -- frontend/src/assistant/AssistantApp.tsx frontend/src/assistant/cici-ui.css`、Playwright hover 后确认组织菜单展开且 logo `::after content=none`、`hasTooltipClass=false`。
- 2026-05-09T05:08:00Z 已按用户追加截图修正组织切换菜单行 hover/current 伪按钮背景：`frontend/src/assistant/cici-ui.css` 为 `.cici-org-menu__item` 补齐 default、hover、focus、focus-visible、active、`.is-current`、`[aria-current="true"]` 全状态 reset，强制透明背景、0 圆角、无阴影、无 transform，只保留文字颜色表达当前态。验证通过：`frontend npm run build`（保留既有 Vite chunk-size warning）、`git diff --check -- frontend/src/assistant/cici-ui.css`、Playwright computed style 确认菜单行 `backgroundColor=rgba(0, 0, 0, 0)`、`backgroundImage=none`、`boxShadow=none`、`borderRadius=0px`、`transform=none`。
- 2026-05-09T04:05:00Z 已按用户截图调整前台左下角多组织切换：`frontend/src/assistant/AssistantApp.tsx` 删除独立组织切换 rail 按钮，将组织菜单触发器并入最下方 `CB` 品牌 logo；登录态组织菜单移除“创建组织”表单，只保留简洁标题和组织列表，当前组织只在列表中标记“当前”。`frontend/src/assistant/cici-ui.css` 同步收紧 logo 按钮和 192px 轻量菜单样式。验证通过：`frontend npm run build`（保留既有 Vite chunk-size warning）、`git diff --check -- frontend/src/assistant/AssistantApp.tsx frontend/src/assistant/cici-ui.css`、Playwright 桌面/移动截图 `output/playwright/org-menu-logo-desktop.png` 与 `output/playwright/org-menu-logo-mobile.png`。
- 2026-05-09T02:08:30Z 已新增 `docs/specs/FEAT-025-agentcici-market-positioning-and-roadmap.md`，将市场调研结论沉淀为产品路线：AgentCiCi 应定位为“面向 CRM、售后和企业业务系统的智能体运行与治理平台”，避免与 Dify/Coze/n8n/模型厂商低代码 Builder 做同质化竞争；近期路线为售后 Agent 闭环 > Salesforce/CloudCC 双 CRM 连接器 > 运行观测与评测 > 发布治理与计费 > 模板市场。`PRODUCT.md` 已新增 Market Position，`.claw/goals.md` 已同步产品目标，`.claw/task-board.md` 已新增 `TASK-070`。
- 2026-05-09T00:53:35Z 已按用户要求重启本地开发服务：`docker compose` 基础设施保持在线，前端 `cici-frontend` screen 会话监听 `5173`，后端 `cici-backend` screen 会话监听 `8080`。启动期间本地旧 PostgreSQL 库先后遇到 Flyway V8/V9 checksum mismatch 与旧 `app_user` schema 缺少 `user_account` / `account_login_identifier` / `organization_member`；已对本地库执行 `flyway:repair`，并按旧 `app_user.id` 补齐 13 条账号/成员映射以保留历史 `user_id` 关联。验证通过：`GET http://127.0.0.1:8080/actuator/health` -> `{"status":"UP"}`，`HEAD http://127.0.0.1:5173/` -> `200`。
- 2026-05-09T00:36:00Z 已继续完成 FEAT-024 成员治理首批闭环：新增 `POST /admin/users/invitations` 按手机号添加成员并复用/创建全局账号；新增 `POST /admin/users/{id}/suspend`、`/restore`、`/transfer-owner`；登录、`/auth/me` 和组织切换只接受 `ACTIVE` 成员；停用当前登录成员、停用唯一 Owner、普通角色编辑 Owner 均受保护；管理端用户页接入新增成员、停用/恢复、转让 Owner，并修复移动端用户页详情横向溢出。验证通过：`AuthFlowIntegrationTest`、`AuthFlowIntegrationTest,AgentOpenApiIntegrationTest,McpServerIntegrationTest`、`frontend npm run build`、目标文件 `git diff --check`、Playwright 管理端用户页桌面/移动截图。
- 2026-05-08T23:56:00Z 已完成 FEAT-024 账号多组织最小闭环：`POST /auth/register` 创建 `user_account`、mobile `account_login_identifier`、新 `org` 和 `OWNER` 成员；`POST /auth/password/login` 保留带 `orgId` 旧行为，无 `orgId` 时单组织直接发 token、多组织返回 `requiresOrganizationSelection` 与组织列表；新增 `GET/POST /auth/organizations` 和 `POST /auth/switch-organization`；`OWNER` 视为组织管理角色，管理端普通角色编辑仍只允许 `ORG_ADMIN/ORG_USER`；助手端登录页移除组织 ID，新增注册创建组织、多组织选择、登录后组织菜单。验证通过：`AuthFlowIntegrationTest`、`AuthFlowIntegrationTest,AgentOpenApiIntegrationTest,McpServerIntegrationTest`、`frontend npm run build`、目标文件 `git diff --check`、Playwright 桌面/移动截图检查登录、注册、多组织选择和组织菜单。
- 2026-05-08T23:47:10Z 已按用户确认完成文档级品牌更新：主品牌定为 `AgentCiCi`，品牌域名记录为 `agentcici.com`；`README.md`、`PRODUCT.md`、`DESIGN.md`、`DESIGN.json`、`docs/project-overview.md`、`AgentCiCi智能体平台实现设计方案.md`、`docs/specs/PROJECT-BASELINE.md`、`docs/specs/FEAT-021/022/023/024`、`docs/specs/FEAT-014/016`、`docs/release-local-to-cici-cloudcc-cn.md` 与 `.claw/goals.md` 已更新产品名或品牌定位。技术标识如 `cici_ak_`、`X-Cici-Api-Key`、`cici_assistant_token`、部署文件名和 Java package 路径暂未改动，避免文档暗示代码/协议已迁移。
- 2026-05-08T16:08:04Z 已落地 FEAT-024 首轮后端迁移：`backend/src/main/resources/db/migration/V1__init_auth_tables.sql` 不再创建 `app_user`，改为创建 `user_account`、`account_login_identifier`、`organization_member`；`UserEntity`/`UserRepository` 已映射到 `organization_member`，并通过 `UserAccountEntity` 表达全局账号；固定密码登录按手机号创建/复用全局账号，再按 `org_id + account_id` 创建/复用组织成员；JWT `sub` 继续为成员 ID，并新增 `account_id`、`member_id` claims；登录与 `/auth/me` 响应同步返回 `accountId`、`memberId`，旧 `userId` 字段保留但语义为 `organization_member.id`。验证通过：后端 compile、`AuthFlowIntegrationTest,AgentOpenApiIntegrationTest,McpServerIntegrationTest`、全量 `backend mvn test`、目标文件 `git diff --check`。
- 2026-05-08T15:48:32Z 已新增 `docs/specs/FEAT-024-account-tenant-lifecycle-and-data-retention.md`：将 Cici 账号体系目标确定为全局 `user_account` + 多登录标识 + 多认证凭证 + 外部身份绑定 + `organization_member` 组织成员关系；同一手机号/邮箱/用户名不能重复注册全局账号，但登录后可创建或加入多个组织；创建者为 `OWNER`，区别于 `ORG_ADMIN`；订阅约束组织空间与能力，不直接约束全局账号；组织到期后进入 `PAST_DUE`、`SUSPENDED`、`PENDING_PURGE`、`PURGED` 生命周期，并通过导出窗口、purge job、manifest 和最小审计摘要完成组织业务数据销毁。`.claw/task-board.md` 已新增 `TASK-069`，`.claw/decisions.md` 已新增 `DEC-024`。
- 2026-05-08T13:09:20Z 已按用户截图和项目页面实现标准调整个人设置 > 专属记忆 UI：`frontend/src/assistant/cici-ui.css` 将分组标题从 18px 小方块收回为横排文本标签，记忆项从内层圆角卡片改为账本式分隔线行，去掉行 hover 背景/阴影/圆角和 badge 背景块，复用金账房裸图标行操作；关闭按钮显式清除边框、阴影、transform 和全局按钮污染；移动端设置 tabs 改为单行紧凑横排，避免“专属记忆”掉到第二行或被截断。A/B 判断：对比保留卡片行与账本分隔线行后，选择后者以符合“面板内不框套框”规范。验证通过：`frontend npm run build`（保留既有 chunk-size warning）、`git diff --check -- frontend/src/assistant/cici-ui.css`、Playwright/Chrome 桌面与移动截图复测；computed style 确认分组标签 `nowrap`、记忆行透明背景/0 圆角/无阴影、关闭按钮无边框无阴影。
- 2026-05-08T12:58:15Z 已将用户要求的 2026 现代页面实现工作流固化到项目设计治理：`DESIGN.md` 新增 `Page Implementation Quality Workflow`，要求先搭建可运行最小版本、本地运行、桌面/移动完整截图、vision/设计 QA 自检、修复复测、关键模块 A/B 对比、必要时使用 imagegen/素材流程，并以响应式视觉证明作为收尾门禁；`DESIGN.json` 新增机器可读 `pageImplementationWorkflow`；`AGENTS.md` 与 `README.md` 同步入口摘要。验证通过：`DESIGN.json` parse，相关文件 `git diff --check`。
- 2026-05-08T09:40:23Z FEAT-023 已进入实现：新增 `V42__wecom_kf_channel.sql`，创建 `wecom_kf_account`、`wecom_kf_conversation`、`wecom_kf_message`；新增 `backend/src/main/java/com/codehouse/ciciassistant/wecom/`，支持 `/wecom/kf/callback` GET URL 校验、POST 加密事件接收、企业微信 SHA1 签名校验、AES-CBC 解密、`sync_msg` 拉取、文本消息映射到 `ChatOrchestratorService`、`send_msg` 回复、`external_userid` 外部客户身份、`wecom-kf:*` 会话、48 小时 / 5 条回复窗口和消息去重；管理端 trace 兼容 `wechat_kf` channel，Vite 与部署 Nginx 已补 `/wecom` 代理。验证通过：`WecomKfCryptoServiceTest,WecomKfConversationEntityTest`、backend compile、`frontend npm run build`、相关文件 `git diff --check`。
- 2026-05-08T09:37:27Z 已按截图反馈去掉个人设置 > 专属记忆中“用户事实 / 个人偏好”等分类标签前的多余单字前缀：`frontend/src/assistant/UserMemoryPanel.tsx` 的分类元数据不再保存 `icon` 字段，筛选 tab、分组标题和新增/编辑弹窗的类别按钮均只渲染完整分类名。验证通过：`rg -n "meta\\.icon|icon:" frontend/src/assistant/UserMemoryPanel.tsx` 无残留；`git diff --check -- frontend/src/assistant/UserMemoryPanel.tsx` 成功；`frontend npm run build` 成功（保留既有 Vite chunk-size warning）。
- 2026-05-08T08:45:17Z 已按截图反馈全仓检索并收敛产品页 tab/filter/scope 伪按钮样式：`frontend/src/assistant/cici-ui.css` 中 settings/memory/builder/runtime/session/openapi tabs 与 filters 均被重置为透明背景、0 圆角、无阴影、无 transform；`frontend/src/styles.css` 中 admin tools/user detail/skills scope/skills compose/whitelist/monitor/admin ops tabs 也增加同类保险规则。`DESIGN.md`、`DESIGN.json`、`AGENTS.md`、`README.md` 已补“原生 button 实现 tab/filter 必须覆盖 default/hover/active/selected/focus/focus-visible 全状态”的禁令。验证通过：`DESIGN.json` parse、tab/filter 静态扫描、`git diff --check`、`frontend npm run build`、headless Chrome 点击“用户事实”后 computed style 为透明背景、0 边框、无阴影、0 圆角、无 transform，仅保留 2px 直线下划线。
- 2026-05-08T08:24:49Z 已按截图反馈去掉“专属记忆”分类 tab 的伪按钮效果：`.memory-panel__filter-btn` 默认、hover、focus 和 active 状态均显式清除背景、边框、阴影、圆角和 transform，只保留文字颜色与 active 金色下划线；headless Chrome 验证三个 tab computed style 均为透明背景、0 边框、无阴影、0 圆角。
- 2026-05-08T08:16:00Z 已按用户最新方向调整 `docs/specs/FEAT-023-ai-native-after-sales-agent.md`：AI 原生售后 Agent 首版客户侧入口明确为企业微信「微信客服」，规划 `GET/POST /wecom/kf/callback`、回调签名与加解密、`sync_msg` 拉取客户消息、`send_msg` 回复微信客户、`external_userid` 映射为外部客户身份、`wechat_kf` channel trace、48 小时 / 5 条发送窗口和 wecom 配置/会话/消息日志表。
- 2026-05-08T08:01:30Z 已修复前台个人设置弹窗“专属记忆”列表右侧按钮不显示：`frontend/src/assistant/cici-ui.css` 为 `.memory-card__action-btn` 显式重置全局按钮 padding、background、box-shadow、transform 和 appearance，并为内部 SVG 固定 15px、`display:block`、`stroke: currentColor`；headless Chrome 登录验收显示 2 张记忆卡、编辑/删除按钮均为 30x30、SVG 为 15x15 且颜色可见。
- 2026-05-08 已新增 `docs/specs/FEAT-023-ai-native-after-sales-agent.md`：结合客服/售后自动化市场洞察，定义 AgentCiCi 的售后方向为 AI 原生售后 Agent 层，首版聚焦售后问答、只读业务查询、Open API 接入、人工接管摘要和运行观测；明确不从零实现完整 helpdesk，退款/赔付/关单/改地址等写动作需后续单独设计确认和审计。
- 2026-05-07 已按用户确认的信息架构调整 FEAT-019：前台 `/` 左侧一级菜单不再显示“智能体监控”；组织级运行观测整合进管理端 `/admin/ops`，并与成本用量、审计日志通过文本 tab 统一为“观测与运维”。
- 2026-05-08 已优化流式聊天工具调用性能：`ChatOrchestratorService` 在单个只读查询工具成功返回、用户意图仍为查询/汇总且结果不要求继续工具时，记录 `tool_planning_stop_skipped` 并直接进入最终生成；未跳过的工具规划收口会追加短输出提示，只让模型判断是否继续工具；最终 `chatStreamWithMessages` 不再传 tools，避免 25 个工具 schema 进入最终生成上下文。验证通过：`ChatOrchestratorServiceModelIdentityTest`、后端 `mvn -q -Dmaven.repo.local=.m2 -DskipTests compile`、相关文件 `git diff --check`。
- 2026-05-08 已将 `V1.7` 发布到 `https://cici.cloudcc.cn`：backend digest `sha256:f2c73badec939387bd53a83ab1bc944d0b7a4a182337943aa7c8bbc7282aca35`，frontend digest `sha256:7adfa06e8d95046131d82d09c966e0a03035cac278c4af994df7e4e6a7093370`；线上 `CICI_IMAGE_TAG=V1.7`，六容器 healthy，Flyway v41，公网 smoke 通过。发布前备份目录：`/opt/cici/backups/20260508-082523-before-v1.7`。
- 2026-05-08 已新增 `docs/release-local-to-cici-cloudcc-cn.md`，整理从本地工作区发布到 `https://cici.cloudcc.cn` 的发布方案：覆盖本地质量门禁、ACR 镜像构建推送、线上配置同步、发布前备份、Docker Compose 部署、公网 smoke、Go/No-Go 标准和回滚策略；默认不覆盖线上业务数据。
- 2026-05-07T23:42:29Z 已按截图反馈调整管理端链路追踪步骤展示：每个节点标题后显示开始时间，右侧固定显示耗时且 0/未调用显示 `0ms`；模型节点额外显示输入/输出 token 数。后端已从 DashScope non-stream/stream usage 写入 `inputTokens` / `outputTokens` 到模型节点 metadata，旧 trace 缺 usage 时显示 0。
- 2026-05-07T23:32:43Z 已修复本地开发环境 `http://127.0.0.1:5173/admin/ops` 智能体运行无数据：本地 `8080` Java 进程仍是旧启动态，直连 `/admin/agents/run-logs` 返回 404；已重启本地后端到当前工作区代码，健康检查 `UP`，`5173` Vite 代理 `/admin/agents/run-logs?limit=10` 返回 JSON 10 条，浏览器页面显示 26 条记录与 3 条真实 trace。
- 2026-05-07 已新增管理端组织级 trace API：`GET /admin/agents/run-logs` 与 `GET /admin/agents/run-logs/{traceId}`，均需 `ORG_ADMIN`，复用最近 7 天限制、筛选、历史会话回填和脱敏 detail 结构；Vite 与部署 Nginx 已补 `/admin/agents` 代理。
- 2026-05-07 已新增 `frontend/src/admin/pages/AdminAgentRunMonitor.tsx`，管理端运维页默认展示智能体运行三栏观测台；`frontend/src/assistant/AssistantApp.tsx` 已移除前台 rail 的“智能体监控”按钮并停止登录时预加载监控日志。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`backend` Java 21 环境下 `mvn -q -Dmaven.repo.local=.m2 -Dtest=AgentRunTraceIntegrationTest test` 成功；`git diff --check -- ...` 成功。
- 2026-05-07 已重新创建 `docs/specs/FEAT-021-agent-open-api.md`：设计外部系统通过 API Key 调用已发布 Agent 的开放能力，首版采用管理端发放 Key、外部 REST/SSE 调用、run-as 用户承接内部权限、externalUser 作为元数据、session map 保持多轮上下文、call log/trace 统一观测。
- 2026-05-07 已新增 `docs/specs/FEAT-022-agent-workload-billing-model.md`：在 FEAT-003 企业混合计费框架上，引入 `智能体工作量 credits` 作为客户侧按量主口径，明确通知/viewer 不占付费席位，主动指挥、审核、构建和发布计入操作/构建席位；credits 覆盖助手对话、模型、RAG、工具、Agent run、Workflow、知识库索引、Open API、定时任务和第三方连接器。
- FEAT-022 明确实现顺序：先补 `usage_meter_event` 和真实 token usage，再做 credits ledger、quota enforcement、套餐/订阅、账单中心和企业合同能力；`agent_api_usage_daily` 只能作为 Open API quota 原型，不能替代统一账单事件。
- 2026-05-07 已继续实现 FEAT-021 Agent Open API：新增 `V41__agent_open_api.sql`、Open API Credential/SessionMap/CallLog/UsageDaily 实体与 repository、`app.agent-open-api.*` 配置、API Key 管理接口、`/openapi/v1/agents/{agentId}/health`、non-stream `/chat` wrapper、Key 级每分钟限流/日配额、call log/usage daily 记录和 trace metadata 标记。
- non-stream Open API chat 当前会用 API Key 绑定的 `runAsUserId` 调用 `ChatOrchestratorService.chat(...)`，并返回 `answer`、`requestId`、外部 `sessionId`、内部 `api:*` session、`traceId`、runtime 摘要和耗时；同一 Key/Agent/external session 会稳定映射到同一内部会话。
- 已修复 Agent API Key `publicId` 生成问题：不再生成 `_` 或 `-`，避免明文 key `cici_ak_live_{publicId}_{secret}` 因分隔符冲突而无法反查。
- 已实现真实 SSE `/openapi/v1/agents/{agentId}/chat/stream` wrapper：Open API 外壳先发送 `meta`，复用内部 `ChatOrchestratorService.chatStream(...)` 的 `phase/tool/delta`，拦截内部 `done` 后补 Open API `requestId`、`traceId`、`elapsedMs` 与 runtime 摘要，并完成 call log、usage daily 和 trace metadata 标记。
- Agent Builder 已新增 `AgentOpenApiDocsDialog` 和编辑页头部 `开放API文档` secondary 按钮；弹窗遵守 `鎏金账房` product register，`API 密钥` 按钮已接入 `AgentOpenApiKeysDialog`。
- 2026-05-07 已按截图反馈修正 Agent API 文档弹窗样式：代码块覆盖全局 `pre` 浅色背景，改为深墨底高对比文字；文档弹窗内部横向分隔线已移除；右侧目录按钮覆盖全局按钮阴影和渐变，改为无背景框的纯文本列表。
- 2026-05-07 已按最新要求移除 Agent API 文档弹窗“在新页签打开”操作：弹窗顶部文档操作只保留 Markdown 下载，关闭与 API Key 管理入口不变；`frontend npm run build` 成功（保留既有 Vite chunk-size warning）。
- 2026-05-07 已按截图反馈修正 Agent API 文档弹窗内容偏左问题：文档正文列与右侧目录作为整体居中，正文 section 在文档列内居中，避免右侧出现大片空白；`frontend npm run build` 成功（保留既有 Vite chunk-size warning）。
- 已新增 `GET /agents/{agentId}/api-calls` 与 Agent Builder Key 管理 modal：支持创建、轮换、撤销 API Key，明文 Key 只显示一次；调用日志可按 requestId、traceId、外部用户、会话和摘要搜索。
- 2026-05-07 已按 API Key 管理反馈修正 `AgentOpenApiKeysDialog`：Key 列表显示绑定的 run-as 执行用户；创建/重新生成后的完整 Key 改为可选中、可复制的一次性区域；列表只显示 Key 前缀并提示不可调用；行操作改为停用/启用、重新生成、删除，删除后隐藏已作废 Key 且保留历史日志。
- 2026-05-07 已继续优化 API Key 管理弹窗：移除表单与说明区多余横向分隔线；执行用户列默认只显示名称，hover 显示手机号、角色和用户 ID；Key 列表每行收为单行展示并调整列宽、操作列间距；删除前缀复制入口，避免误解为复制完整 Key。验证：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- frontend/src/assistant/AgentOpenApiKeysDialog.tsx frontend/src/assistant/cici-ui.css` 成功。
- 2026-05-07 已按最新 UI 反馈去掉 Key 管理弹窗 tab 与行操作的弧形边框背景按钮样式：局部按钮状态强制无背景、无边框、无圆角、无阴影，只保留文本颜色和下划线表达；`DESIGN.md`、`DESIGN.json`、`AGENTS.md`、`README.md` 已新增项目禁令，产品面板内部 tab、行操作、筛选标签、状态操作和内联文字命令不得使用带弧形边框的背景按钮样式。验证：`node -e JSON.parse(DESIGN.json)` 成功；`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- frontend/src/assistant/cici-ui.css DESIGN.md DESIGN.json AGENTS.md README.md` 成功。
- 2026-05-07 已按截图反馈移除 API Key 管理弹窗表单下方“当前执行身份”文字，避免与 run-as 用户选择器重复；`frontend npm run build` 成功（保留既有 Vite chunk-size warning）。
- 部署配置已在 `deploy/nginx.cici.conf` 与 `deploy/nginx.cici.ssl.conf` 补 `/openapi/` 代理；尚未执行公网 `/openapi` smoke。
- 本轮 FEAT-021 验证通过：`backend` Java 21 环境下 `mvn -q -Dmaven.repo.local=.m2 -Dtest=AgentOpenApiIntegrationTest test` 成功；`backend mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` 成功；`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check` 成功。
- 原 `docs/specs/FEAT-020-agent-open-api.md` 当前不存在，且 `FEAT-020` 已被 `docs/specs/FEAT-020-fixed-password-login.md` 占用；本轮新增 `TASK-062 Agent Open API implementation design restore`，使用 `FEAT-021` 避免 feature id 冲突。
- FEAT-021 明确当前迁移已到 `V40__fixed_password_login.sql`，Agent Open API 后续迁移建议从 `V41__agent_open_api.sql` 开始；公网部署需补 `/openapi` Nginx 代理。
- FEAT-021 已按用户补充要求新增 Agent Builder 前端范围：在智能体构建页头部增加 `开放API文档` secondary 按钮，点击后弹出模式文档页，展示 API 服务器、运行状态、API 密钥入口、基础 URL、鉴权、发送对话、流式对话、健康检查、会话、错误码和安全建议；视觉遵循 `鎏金账房`，不照搬参考图的黑白/营销式视觉。
- 设计恢复阶段验证通过：`git diff --check -- docs/specs/FEAT-021-agent-open-api.md .claw/task-board.md .claw/current-status.md` 成功。
- 2026-05-07 修复飞书绑定智能体对话工具轮次耗尽文案：线上 trace `cd62b95f-d4a6-4133-89ef-108ed9bea034` 显示飞书 `cici-system` 本轮按 `maxToolCalls=4` 连续调用工具后未生成最终文本，旧非流式 `runToolLoop` 直接返回“系统保护上限”。本轮将非流式上限收口改为追加一次无工具模型总结，失败时基于全部已返回 tool messages 生成可读摘要，包含每次工具名、查询参数和返回条数/字段结构。
- 2026-05-07 飞书工具上限收口验证通过：`backend` Java 21 环境下 `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatOrchestratorServiceModelIdentityTest test` 成功；`mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` 成功；`git diff --check -- backend/src/main/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorService.java backend/src/main/java/com/codehouse/ciciassistant/ai/service/AgentRunTraceService.java backend/src/test/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorServiceModelIdentityTest.java` 成功。
- 2026-05-07 本地服务已切换/确认使用 PostgreSQL：`docker compose up -d --remove-orphans postgres redis rabbitmq qdrant` 后，`cici-postgres`、`cici-redis`、`cici-rabbitmq` 均 healthy，Qdrant 已监听 `6333`；后端以 `local` profile 启动，日志确认 `jdbc:postgresql://localhost:5432/cici_assistant (PostgreSQL 16.13)`，Flyway schema 当前版本 `40`。
- 本地后端与前端已改用 detached `screen` 会话保持运行：`cici-backend` 监听 `8080`，`cici-frontend` 监听 `5173`；提权健康检查通过，`GET http://127.0.0.1:8080/actuator/health` 返回 `{"status":"UP"}`，`HEAD http://127.0.0.1:5173/` 返回 `200 OK`。
- 2026-05-07T15:32+08:00 进度巡检确认本地基础设施仍在线：`docker compose ps` 显示 PostgreSQL、Redis、RabbitMQ healthy，Qdrant up；`GET http://127.0.0.1:8080/actuator/health` 返回 `{"status":"UP"}`，`HEAD http://127.0.0.1:5173/` 返回 `200 OK`，公网 `HEAD https://cici.cloudcc.cn/` 返回 `200`。
- 2026-05-07 ECS 全量本地数据同步完成：远端同步前备份目录为 `/opt/cici/backups/20260507-123416-full-local-sync/`，包含 `remote-before-sync.dump`、`acr.env.before-sync`、`remote-kb-files-before-sync.tgz` 和 `remote-qdrant-before-sync.tgz`。
- 本轮同步保留远端 Flyway schema history，只截断并恢复业务表数据，避免把本地旧迁移脚本名带入远端；恢复后执行 post-restore SQL，将 Tavily 与邮箱加密字段重加密为远端 `APP_SECURITY_SECRET_KEY` 可解密的密文，并把 6 条知识库 `storage_path` 改为 `/app/data/kb-files/...`。
- 远端 PostgreSQL 关键行数已与本地精确对齐：`app_user=13`、`agent_definition=4`、`skill_definition=23`、`integration_app=3`、`model_provider_config=5`、`email_account=1`、`mcp_server=1`、`knowledge_base=2`、`kb_document=6`、`kb_chunk=310`、`chat_session=61`、`chat_message=556`、`user_quick_command=3`。
- 知识库资产已同步：远端 `cici-acr_cici_kb_files` 保留 7 个实际文件；Qdrant `cici_kb_chunk` 集合已重建并导入本地 161 个 points。
- 2026-05-07 修复部署 Nginx API 代理：`deploy/nginx.cici.conf` 与 `deploy/nginx.cici.ssl.conf` 的通用 API location 从只匹配 `.../` 改为匹配无尾斜杠与子路径；新增 `/api/platform` 到后端 `/platform` 的 rewrite，避免 `/agents`、`/skills`、`/integrations`、`/models/providers` 返回前端 HTML 导致页面无数据。
- 远端已同步新 Nginx 配置并执行 `docker exec cici-frontend nginx -t` 与 `nginx -s reload`，语法检查和热重载成功；`docker compose ps` 显示六容器均 healthy。
- 本轮远端验证通过：六容器 healthy；`https://cici.cloudcc.cn/` 和 `/actuator/health` 返回 `200`；固定密码登录 `13900009999` 成功；登录后 `/agents=4`、`/skills=13`、`/integrations=3`、`/models/providers=5`、`/kb=2`、`/me/agents/run-logs=2`；Tavily 存储 key 连接测试成功；默认平台管理员 `13800138111` 登录后 `/api/platform/skills=11`、`/api/platform/tools=13`。
- V1.5 发布前校验通过：`git diff --check` 成功；前端 `npm run build` 成功（保留既有 Vite chunk-size warning）；后端认证集成测试在 Java 21 下 `AuthFlowIntegrationTest,SmsRateLimitIntegrationTest` 成功。第一次后端测试用默认 Java 17 运行已由 Java 21 编译的测试类失败，切换 `JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.10/libexec/openjdk.jdk/Contents/Home` 后通过。
- 发布前安全检查通过：本地新增 `deploy/` 文件不包含真实 `deploy/acr.env`、证书或私钥；`.gitignore` 已忽略 `deploy/acr.env`。
- 远程 `git fetch origin --tags` 两次因 GitHub HTTPS 连接 reset 失败，需在本地提交/tag 后继续重试 `git push`。
- 已完成 `TASK-058 Fixed password login for three entries`：新增 `auth_password` 固定密码凭证表，迁移初始化 `szyd1234` 的 PBKDF2 哈希；后端新增 `POST /auth/password/login` 并复用原用户创建、bootstrap 管理员提升、平台角色解析和 JWT 签发逻辑。
- 已完成 `TASK-059 ACR one-click docker compose deployment`：新增 `deploy/docker-compose.acr.yml`，六个服务全部使用 `op-registry.cloudcc.cn/cloudcc-ai-native/*:latest`；新增 `deploy/acr.env.example`、`deploy/nginx.cici.conf` 和 `scripts/deploy-acr.sh`。
- ACR compose 默认 `CICI_PLATFORM=linux/amd64`，兼容当前 backend/frontend 只有 amd64 manifest 的情况；前端容器挂载完整 Nginx 配置，将 `/auth`、`/ai`、`/kb`、`/agents`、`/skills`、`/me`、`/api/platform` 等浏览器相对 API 代理到后端。
- 已同步 compose 职责边界到 `docker-compose.yml` 顶部注释、`README.md`、`docs/deploy-runbook.md` 和 `.claw/devops.md`：根目录 compose 只用于本地 PostgreSQL/Redis/RabbitMQ/Qdrant 基础设施，不作为完整部署入口；完整部署统一走 `deploy/docker-compose.acr.yml` 和 `./scripts/deploy-acr.sh`。
- 已新增镜像构建文件：`.dockerignore`、`deploy/Dockerfile.backend`、`deploy/Dockerfile.frontend`。
- 已完成 ACR 镜像刷新：`cici-backend:latest` digest 为 `sha256:82732586c707a9f0083fcc02191b16ed7b7345c8c0ad59988b65052ce7e00863`；`cici-frontend:latest` digest 为 `sha256:a70521fa3f651bec5fe32e1eaf5c698e5587a2e5de84f1acfb9e4a00ac33b9be`。
- 本轮镜像验证通过：后端 `mvn -q -Dmaven.repo.local=.m2 -DskipTests package` 成功；前端 `npm run build` 成功（保留既有 chunk-size warning）；两个 `docker buildx build --platform linux/amd64 ... --push` 成功；`docker buildx imagetools inspect` 已确认新 digest；前端镜像 `nginx -t` 成功。
- 已完成 `TASK-060 ECS SSL deployment for cici.cloudcc.cn`：部署目录 `/opt/cici`，compose 使用 `/opt/cici/deploy/docker-compose.acr.yml` + `/opt/cici/deploy/docker-compose.acr.ssl.yml`，证书放在 `/opt/cici/deploy/certs/cloudcc.cn.pem` 和 `.key`。
- `cici-database`、`cici-redis`、`cici-rabbitmq`、`cici-qdrant` 原 ACR tag 均为 arm64 单架构，无法在 x86_64 ECS 上运行；本轮已用对应官方镜像重新构建并推送为 `linux/amd64` ACR tag。
- 服务器端口策略：公网只开放容器映射的 `80`/`443`；后端、PostgreSQL、Redis、RabbitMQ、Qdrant 均绑定 `127.0.0.1`。
- 远端 env 已设置 `APP_SECURITY_SECRET_KEY`，后端不再输出 `Using DEV fallback key`。
- 本轮 ECS 验证通过：`docker compose ps` 六容器均 healthy；`http://cici.cloudcc.cn/` 返回 `301` 到 HTTPS；`https://cici.cloudcc.cn/` 返回 `200`；`POST /auth/password/login` 使用固定密码返回 `200`、token 和 `ORG_ADMIN`。
- 本轮验证通过：`docker compose --env-file deploy/acr.env.example -f deploy/docker-compose.acr.yml config` 成功；`bash -n scripts/deploy-acr.sh` 成功；使用 `cici-frontend:latest` 执行 `nginx -t` 成功；`git diff --check -- .gitignore deploy/docker-compose.acr.yml deploy/acr.env.example deploy/nginx.cici.conf scripts/deploy-acr.sh docs/deploy-runbook.md` 成功。
- 本轮追加验证通过：`git diff --check -- docker-compose.yml README.md docs/deploy-runbook.md .claw/devops.md` 成功。
- `/auth/sms/send` 与 `/auth/sms/login` 保留路由但返回 `SMS verification login is disabled`，避免继续使用短信验证码登录。
- 助手端 `/`、组织管理端 `/admin/login`、平台端 `/platform/login` 均改为组织 ID + 手机号 + 固定密码，不再显示验证码、获取验证码或 `devCode` 文案。
- 后端测试 helper、本地 E2E 脚本、README、部署 runbook、安全清单和 `docs/specs/FEAT-020-fixed-password-login.md` 已同步固定密码登录语义。
- 本轮验证通过：`/usr/bin/env JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.10/libexec/openjdk.jdk/Contents/Home mvn -q -Dmaven.repo.local=.m2 -Dtest=AuthFlowIntegrationTest,SmsRateLimitIntegrationTest test` 成功；同 Java 21 环境下 `backend mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` 成功；`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check` 成功。
- 已完成 `TASK-057` 下一步真实数据接入：新增 `agent_run_trace` 持久化表、`AgentRunTraceService`、`GET /me/agents/run-logs` 和 `GET /me/agents/run-logs/{traceId}`，聊天运行结束后会记录统一 trace。
- 已按真实 trace 截图反馈修正链路语义与耗时：`AgentRunTraceService` 不再把所有绑定技能写成“命中技能”，新增 `boundSkillCodes` 与 `activatedSkillCodes`；`ChatOrchestratorService` 记录技能解析、用户消息、RAG、工具定义加载、模型工具规划、模型最终生成、逐工具调用、技能运行治理和消息落库的独立耗时。
- 监控页链路详情已展示模型调用分段耗时和工具调用耗时；“技能与知识库”改为展示“本轮激活”或“未激活业务技能 · 候选”，避免误导用户认为无关技能被命中。
- `ChatOrchestratorService` 普通与流式聊天路径均已写入 trace：覆盖会话、用户问题、模型名、RAG 结果和耗时、工具调用摘要、技能/工作流执行结果、最终回答、消息落库节点和总耗时。
- 监控页已从 `/me/agents/cici-system/workflow/executions` 切换到 `/me/agents/run-logs`，左侧仍显示智能体状态，中间展示最近 7 天真实运行日志，右侧按选中 trace 加载模型、工具、技能、知识库和节点时间线。
- 历史对话没有细粒度 trace 时会通过 `chat_session` / `chat_message` 回填为 `chat_session` 来源的 message-only 记录，避免旧会话完全不可见，同时不伪造工具、RAG 或耗时。
- 本轮验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` 成功；`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=AgentRunTraceIntegrationTest test` 成功；`git diff --check -- ...` 成功。
- 已按效果图完成 `TASK-057 Agent observability monitoring frontend implementation` 前端落地：正式监控页不再使用深色赛博大屏，改为暖象牙、墨色文字、香槟金结构线的三栏产品观测台。
- `frontend/src/assistant/AssistantApp.tsx` 的 `workspaceTab === "monitor"` 分支已重构为顶部指标、近 7 天筛选工具、左侧智能体状态列表、中间运行日志列表、右侧链路追踪详情；支持智能体筛选、日志搜索、日志选中和刷新状态采样。
- 已按用户截图反馈去除无效假数据：不再前端合成 `trace xxxxxx`、`GPT-4.1`、`RAG`、工具/技能 chip、节点数、随机耗时和模拟链路时间线；运行日志只展示 `/me/agents/cici-system/workflow/executions` 的最近 7 天真实执行摘要，右侧明确显示真实 trace 尚未接入。
- 已按用户最严格样式反馈继续清理监控页框套框：搜索框内部改为单条底线，tab 不再有背景小框，日志行不再有选中背景或 inset 框，链路详情分组不再是卡片背景，状态和“链路未接入”不再是 chip 背景，空态不再是虚线框。
- 已修复搜索框放大镜：`AssistantApp.tsx` 不再渲染字体字符 `⌕`，`.cici-monitor__search-icon` 改用 CSS `::before` / `::after` 绘制 13px 放大镜，避免字体差异导致图标像小圈带点或出现奇怪字形。
- 已按最新截图反馈继续收紧监控页日志范围 tab：`.cici-monitor-tab` 的默认、active、hover、active click、focus 和 focus-visible 状态均强制无背景、无 `box-shadow`、无 `text-shadow`、无滤镜，只保留金色下划线作为选中信号。
- 已修复日志范围 tab 区域异常滚动条：`.cici-monitor-tabs` 不再使用 `overflow-x: auto`，改为 `overflow: visible`；active 下划线从 `bottom: -1px` 收回到 `bottom: 0`，避免 1px 外溢触发浏览器滚动槽。
- 已按最新截图检查监控页整页选中态：`.cici-monitor-agent`、`.cici-monitor-log`、`.cici-monitor-tab` 的 selected/active/hover/pressed/focus/focus-visible 均强制无 `box-shadow`、无文字阴影、无滤镜、无浮起卡片感；日志行选中态不再改 `border-bottom-color`，只使用标题文字色表达。
- 已将“产品面板内部禁止背景框/框套框”写入 `DESIGN.md` / `DESIGN.json` / `AGENTS.md` / `README.md`，要求行、tab、搜索框内部、状态文字、链路详情、指标组和摘要块只使用文字层级与必要的 1px 分隔线。
- 已将“产品页选中态不加阴影，能不加边框就不加边框”写入 `DESIGN.md` / `DESIGN.json` / `AGENTS.md` / `README.md`，要求面板内部 selected、active、hover、pressed、focus、focus-visible 不得使用阴影/发光/内阴影/浮起卡片感，并优先使用文字颜色、字重或 tab 下划线。
- `frontend/src/styles.css` 的 `.cici-monitor*` 已整段替换为 `鎏金账房` 样式：13px 产品文本、11-12px metadata、文本 tab、紧凑状态标签、暖象牙 panel、金线 active/focus 结构和移动端单列降级。
- `docs/specs/FEAT-019-agent-observability-monitoring.md` 已更新实现进展，记录当前前端只展示真实执行摘要，不展示未接入的 trace 明细；后端真实日志聚合接口仍是后续任务。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- frontend/src/assistant/AssistantApp.tsx frontend/src/styles.css` 成功。
- 此前已新增 `TASK-057 Agent observability monitoring design`：完成智能体监控页改造设计文档与效果图，本轮已继续完成正式前端页面落地。
- 新增 `docs/specs/FEAT-019-agent-observability-monitoring.md`，定义页面目标、信息架构、最近 7 天日志、链路追踪数据模型、接口建议、脱敏策略、验收标准和后续任务拆分。
- 新增 `docs/specs/mockups/agent-observability-monitoring.html` 作为独立 HTML mockup，并新增 `docs/specs/mockups/agent-observability-monitoring.svg` / `agent-observability-monitoring.png` 作为静态效果图。
- 设计方向已遵循 `PRODUCT.md` / `DESIGN.md` / `impeccable` product register：暖象牙表面、墨色文字、香槟金结构线、紧凑 13px 产品 UI，不沿用当前 `.cici-monitor*` 的深色蓝紫赛博视觉。
- 已完成 `TASK-056 Lightweight skill picker visual cleanup`：工作台技能列表不再显示技能代码，只显示技能名称；列表行从带背景块的卡片感改为透明紧凑菜单行。
- `frontend/src/assistant/AssistantApp.tsx` 已移除技能菜单项里的 `skillCode` 文本展示；`frontend/src/assistant/cici-ui.css` 已将技能菜单外层设为不透明 `#fffdf8`，并取消技能行 gap、圆角行背景、hover/selected 背景块和行阴影。
- 已将规范写入 `DESIGN.md` / `DESIGN.json` / `AGENTS.md` / `README.md`：轻量浮层菜单使用不透明暖象牙表面，紧凑技能、指令、picker 行默认只显示面向用户的名称，不显示实现代码、slug、id 或不必要 metadata，也不得使用 hover 背景、选中背景、逐行背景块或行阴影。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`node -e "JSON.parse(...DESIGN.json...)"` 成功；`git diff --check -- ...` 成功；`curl -sS -I http://127.0.0.1:5173/` 返回 `200 OK`。
- 已完成 `TASK-055 Workbench skill picker initial load retry fix`：首次登录时技能绑定请求若遇到后端默认技能/绑定初始化或登录态刚建立的瞬时失败，前端旧逻辑会把失败结果缓存成 `[]`，技能菜单显示“当前智能体暂无绑定技能”，刷新后状态清空才恢复。
- `frontend/src/assistant/AssistantApp.tsx` 已将技能绑定的“已加载空列表”和“加载失败”状态拆开；请求失败不再固化为空列表，会短延迟自动重试一次，手动再次打开技能菜单也会重试。
- 技能菜单现在只有在接口成功返回空列表时才显示“当前智能体暂无绑定技能”；最终加载失败时显示“技能加载失败，请再次点击重试。”，避免误导用户以为智能体未绑定技能。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- frontend/src/assistant/AssistantApp.tsx` 成功；`curl -sS -I http://127.0.0.1:5173/` 返回 `200 OK`。
- 已完成 `TASK-054 Chat deferred tool result guard fix`：截图问题的新漏点是最终文本可能使用“后续处理/接下来我再抽取/数据已基本齐全”等承诺式话术，但没有再触发下一轮工具调用，前端旧识别未覆盖这类词，状态机仍显示“已完成”。
- `frontend/src/assistant/chatMessageState.ts` 已补充“后续处理、接下来再抽取、我再整理/分析/生成/展示”等最终承诺识别；工作台流结束时会进入“等待确认/补充”，不再误报完成。
- `ChatOrchestratorService` 已增加工具结果保护层：当本轮已有 tool messages，而最终回答仍只是后续承诺时，会把 `buildToolResultFallbackMessage` 生成的可读工具摘要追加到最终消息并一起流式展示/落库，避免已拿到的数据不展现。
- 本轮验证通过：`frontend npm test -- chatMessageState.test.ts` 成功；`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatOrchestratorServiceModelIdentityTest test` 成功；`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`backend mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` 成功；`git diff --check -- ...` 成功。
- 已完成 `TASK-053 Skill import unmatched resource create fix`：白糖技能包导入解析成功但编辑页空白的根因是前端在 `preview.resourceMapping.hasUnmatchedResources` 时直接 `nav("/admin/skills/new")`，没有创建导入草稿，也没有把 `preview.draft` 带到新建页。
- `frontend/src/admin/pages/AdminSkillsListPage.tsx` 已改为即使存在未匹配工具/知识库，也继续调用 `/skills/imports/{importId}/create` 创建含 `prompt.md` / `cici-skill.md` 内容的自定义技能草稿；未匹配资源只作为 toast 提醒，后续在编辑页补齐。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- frontend/src/admin/pages/AdminSkillsListPage.tsx` 成功。
- 已完成白糖大宗贸易行业客户感知技能包导入失败排查：原 zip 将 8 个包文件放在 `white-sugar-industry-customer-perception-skill-package/` 外层目录下，导入器只允许根目录文件名，因此报 `Unsupported zip entry: white-sugar-industry-customer-perception-skill-package/PACKAGE_SPEC.md`。
- 已重新打包为 `/Volumes/workspace/AI/skills/white-sugar-industry-customer-perception-skill-package-importable.zip`，zip 根目录仅包含 `manifest.json`、`SKILL.md`、`cici-skill.md`、`prompt.md`、`contract.json`、`resources.json`、`PACKAGE_SPEC.md`、`README.md` 8 个允许文件；已验证 JSON 可解析、zip entry 无不支持路径。
- 已完成 `TASK-052 Chat CloudCC tool args and workbench skill picker fix`：截图里的 `get_object_fields open_api_token unexpected keyword argument` 根因是 CloudCC MCP 参数合并只在“注入”时看 schema，但没有清掉模型参数中已有的 `open_api_token/base_url/token`，且 token 刷新重试路径仍走无 schema 的旧重载。
- `McpServerService` 已改为 schema 未声明凭证字段时主动移除 `open_api_token/openApiToken/base_url/baseUrl/token`，刷新重试也复用同一 schema-aware 合并逻辑；鉴权失败识别收窄，不再因普通错误文本里包含 `token` 就误触发重试。
- 工作台技能菜单空态根因是前端用普通用户 token 调 `/agents/{agentId}/skills`，但该控制器类级别要求组织管理员；同时新的 `AgentSkillBindingService` 没有先补齐 Phase 1 默认技能/绑定。
- 已新增普通登录用户可读的 `GET /me/agents/{agentId}/skills`，前端技能菜单改走该接口；`AgentSkillBindingService` 读取/替换前会先 `ensurePhaseOneDefaults`，因此 `cici-system` 默认绑定的 `general-assistant`、`web-search` 等能被查询到。
- 本轮验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=McpServerIntegrationTest#shouldStripCloudccCredentialArgumentsWhenToolSchemaDoesNotDeclareThem test` 成功；`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest#shouldListDefaultSystemAgentSkillBindingsFromAgentEndpoint test` 成功；`backend mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` 成功；`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- ...` 成功。
- 已完成 `TASK-051 Chat tool empty final fallback`：截图问题根因是 `/ai/chat/stream` 工具调用成功后模型最终流式输出为空，后端兜底 `buildToolResultFallbackMessage` 直接把最后一个 tool message 原始 JSON 拼到聊天气泡。
- `ChatOrchestratorService` 的工具兜底已改为解析结构化 JSON：优先展示 `answer`，失败时展示 `message/error/reason`，`results[]` 返回时生成最多 5 条标题、来源、摘要的可读列表，不再直接暴露 `success/results` 原始 JSON。
- 非流式工具循环在模型最终 content 为空且已有工具消息时，也复用同一可读兜底；无工具结果时返回中文“模型本轮未能生成回复”提示。
- `frontend/src/assistant/chatMessageState.ts` 已将“工具已返回结果但模型本轮未能生成最终自然语言总结”识别为需要跟进，使右侧状态机收口到“等待确认/补充”而不是“已完成本轮处理”。
- 本轮验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatOrchestratorServiceModelIdentityTest test` 成功；`frontend npm test -- chatMessageState.test.ts` 成功；`backend mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` 成功；`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- ...` 成功。
- 已完成 `TASK-050 Skill code draft publish fix`：`SkillDefinitionService.updateSkill` 原本只校验新 `skillCode` 是否冲突，但没有把新值写回 `skill_definition.skill_code`，导致保存/发布后仍是旧代码。
- `SkillDefinitionEntity.update(...)` 已新增 `skillCode` 参数并持久化该字段；租户自定义 Skill 更新传入规范化后的新 code，平台模板同步与历史恢复路径传入当前 code，避免误改平台托管技能编码。
- 已补 `SkillGovernanceIntegrationTest#shouldPersistUpdatedSkillCodeBeforePublishingTenantCustomSkill`，覆盖“创建 Skill -> 修改 skillCode 保存草稿 -> 发布 -> 重新查询详情/版本摘要均使用新 code”。
- 本轮验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillGovernanceIntegrationTest#shouldPersistUpdatedSkillCodeBeforePublishingTenantCustomSkill test` 成功；`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillGovernanceIntegrationTest test` 提权后成功；`git diff --check -- ...` 成功。全量 SkillGovernance 首次在沙箱内因测试需要绑定本地 HTTP 端口失败，提权重跑通过。
- 已按用户确认的建议完成 `TASK-049 Chat conditional knowledge retrieval`：`ChatOrchestratorService` 新增 `shouldUseKnowledgeRetrieval` 门控，普通与流式聊天共用同一判断。
- RAG 触发条件调整为：有效知识库非空且用户显式传入知识库，或问题明确指向知识库、文档、制度、政策、流程、规则、手册、配置、口径、依据等知识型意图。
- 寒暄、闲聊、才艺/轻量创作，以及“看下客户/线索/台账/审批/日程/邮件”等业务数据查询，在只有默认知识库时不再先执行 `ragService.retrieveDetailed`。
- `/ai/chat/stream` 仅在真实触发 RAG 时发送 `retrieving` 与 `rag_done` phase；前台工作台本地预判已从“审批/客户关键词即检索中”改为“处理中/分析请求”，只响应后端真实 RAG phase 显示“检索中”。
- 内置 Agent 默认系统提示和 Agent Builder 新建默认提示已从“回答前先检索知识库”改为“先判断请求类型，再决定直接回答、检索知识库或调用业务工具”。
- 已新增 `docs/specs/FEAT-018-chat-conditional-knowledge-retrieval.md`，记录本次条件检索策略、范围、验收和回滚方式。
- 本轮验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatOrchestratorServiceModelIdentityTest test` 成功；`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- ...` 成功。
- 已按用户截图定位对话状态不一致根因：SSE 收到 `done` 后前端无条件将右侧状态机置为“已完成”，但模型最终文本可能只是“参数问题，让我重新查询”这类未来承诺；后端不会在 `done` 后自动继续生成下一条回复。
- 已在 `backend/src/main/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorService.java` 为工具调用后的最终回答追加 guard prompt，要求工具结果含失败、缺参、参数问题时明确说明未完成部分和需要补充的信息，不得把“稍后/继续/让我重新查询”作为最终答复。
- 后端流式路径在最终文本为空但有工具结果兜底时会把兜底文本也发送为 delta，避免前端只收到 `done` 而没有可展示文字。
- 已在 `frontend/src/assistant/chatMessageState.ts` 新增 `assistantResponseNeedsUserFollowup`，并让 `frontend/src/assistant/AssistantApp.tsx` 在工作台流结束后根据最终回复内容判断“等待确认/补充”而不是无条件“已完成”。
- 本轮验证通过：`frontend npm test -- chatMessageState.test.ts` 成功；`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatOrchestratorServiceModelIdentityTest test` 成功；`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- ...` 成功。
- 已按飞书机器人报错排查结果修复聊天工具调用边界：`backend/src/main/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorService.java` 新增系统提示块，要求寒暄、闲聊、祝福、角色扮演、才艺表演、轻量创作和常识解释直接文本回答，不调用工具。
- 飞书渠道会话额外提示“默认按日常对话处理；除非用户明确提出业务数据查询或操作，不要触发工具”，降低外部渠道日常对话误入 MCP/业务工具循环的概率。
- 工具调用轮次超限兜底已从 `Tool calling exceeded maximum rounds.` 改为中文友好提示，避免英文内部错误直接回到飞书用户。
- 本轮验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatOrchestratorServiceModelIdentityTest test` 成功；`git diff --check -- backend/src/main/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorService.java backend/src/test/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorServiceModelIdentityTest.java` 成功。
- 已按用户截图修复“我的工作流”布局：`frontend/src/assistant/cici-ui.css` 将 `.cici-workflow-panel__head--editor` 改为纵向布局，工具清单独占一行，操作按钮单独在下方显示，不再与工具清单并排导致错位。
- 运行与历史区域 `.cici-workflow-disclosures` 已从两列改为单列，最新编译结果、版本、触发器、最近执行记录四个折叠面板每个独占一整行。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- frontend/src/assistant/cici-ui.css` 成功；`curl -sS -I http://127.0.0.1:5173/` 返回 `200 OK`。
- 已按用户截图继续优化“我的工作流”工作流编排区：`frontend/src/assistant/MyWorkflowStudio.tsx` 将冗长 `allowedToolIds` 列表从标题说明中移出，改为“已授权 N 个工具”的可展开清单。
- `frontend/src/assistant/cici-ui.css` 新增 `cici-workflow-editor__summary` / `cici-workflow-tools` 样式，工具清单展开后以紧凑 chip 展示且有最大高度滚动；保存草稿、编译、发布按钮禁止换行，避免主按钮被挤成两行。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- frontend/src/assistant/MyWorkflowStudio.tsx frontend/src/assistant/cici-ui.css` 成功；`curl -sS -I http://127.0.0.1:5173/` 返回 `200 OK`。
- 已按用户要求重排“我的工作流”页签：`frontend/src/assistant/MyWorkflowStudio.tsx` 将原本连续铺开的设置、Spec、编译结果、版本、触发器和执行记录，整合为“基础配置 / 工作流编排 / 运行与历史”三类结构。
- “运行与历史”下的最新编译结果、版本、触发器和最近执行记录改为折叠面板，默认只展示摘要和数量，避免页面一次性显示过长；`workflow.ts` 也收进二级折叠查看。
- `frontend/src/assistant/cici-ui.css` 新增 `cici-workflow-panel` / `cici-workflow-disclosure` 紧凑样式，缩短 Spec 文本框默认高度，移动端下运行面板自动单列。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- frontend/src/assistant/MyWorkflowStudio.tsx frontend/src/assistant/cici-ui.css` 成功；`curl -sS -I http://127.0.0.1:5173/` 返回 `200 OK`。
- 已按用户截图修复个人设置头像操作按钮不水平对齐：`frontend/src/assistant/cici-ui.css` 为 `.cici-profile-avatar-actions .cici-btn` 锁定 34px 高度、统一 `box-sizing`、`line-height`、`margin`、`appearance`，并确保文件上传 `label.cici-btn` 与原生 `button` 一样按 `inline-flex` 排布。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- frontend/src/assistant/cici-ui.css` 成功。
- 已按用户要求统一前台个人设置弹窗整体样式：`frontend/src/assistant/MyEmailAccountsModal.tsx` 为主弹窗补充 `role="dialog"` / `aria-modal` / labelled heading，并新增 `cici-settings-content` 可滚内容区。
- 邮箱页表单已按“基础信息 / 收信 POP3 / 发信 SMTP / 发送策略”分组，改善字段堆叠和长表单扫描节奏。
- `frontend/src/assistant/cici-ui.css` 新增个人设置弹窗 `鎏金账房` 覆盖层：文本 tab、暖象牙底、金线边框、香槟金主按钮、紧凑 13px 表单、统一列表行、移动端单列兜底。
- `frontend/src/assistant/UserMemoryPanel.tsx` 与样式已收回专属记忆页旧蓝紫绿视觉和 emoji 装饰，改为更克制的产品 UI 标识、统一按钮、筛选 tab、卡片、空态和内层确认弹窗样式。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- frontend/src/assistant/MyEmailAccountsModal.tsx frontend/src/assistant/UserMemoryPanel.tsx frontend/src/assistant/cici-ui.css` 成功；`curl -sS -I http://127.0.0.1:5173/` 返回 `200 OK`。未做真实登录态截图验收，因为当前会话没有可复用的前台登录 token。
- 已按用户截图调整前台个人设置弹窗：`frontend/src/assistant/MyEmailAccountsModal.tsx` 新增“绑定沟通渠道”页签；`frontend/src/assistant/MyWorkflowStudio.tsx` 移除飞书配对区块，仅保留个人工作流设置、Spec、编译、版本、触发器和执行记录。
- 新增 `frontend/src/assistant/CommunicationChannelBinding.tsx`，承载飞书绑定状态、生成配对码、复制配对指令和解除绑定逻辑，继续复用既有 `/feishu/bot/pairing/*` 接口。
- `frontend/src/assistant/cici-ui.css` 为设置页签增加 `flex-wrap`，避免新增长页签在较窄弹窗中挤出布局。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- frontend/src/assistant/MyWorkflowStudio.tsx frontend/src/assistant/MyEmailAccountsModal.tsx frontend/src/assistant/CommunicationChannelBinding.tsx frontend/src/assistant/cici-ui.css` 成功；`curl -sS -I http://127.0.0.1:5173/` 返回 `200 OK`。
- 已按用户截图优化执行记录 tab 内嵌 tab 观感：`frontend/src/assistant/AgentBuilderShell.tsx` 为“全部 / 生产类 / 试运行”增加记录数量并收为“记录范围”筛选组；`frontend/src/assistant/cici-ui.css` 移除 `.cici-builder-runtime__filter` 对主 tab 下划线体系的继承，改为表格工具条内的紧凑筛选控件。
- 执行记录说明文案已收短为数据来源说明，减少筛选规则解释占用的视觉层级。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- frontend/src/assistant/AgentBuilderShell.tsx frontend/src/assistant/cici-ui.css` 成功。
- 已按用户截图修复流程图预览底部白条：`frontend/src/assistant/cici-ui.css` 将 `.cici-builder-graph--full` 改为纵向 flex 容器，并让 `.cici-builder-graph__canvas` `flex: 1 1 auto`，使点阵画布背景填满图表卡片标题栏以下的剩余高度。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- frontend/src/assistant/cici-ui.css` 成功。
- 已按用户截图修复流程图预览左下小地图与右下缩放控制器定位：`frontend/src/assistant/AgentBuilderShell.tsx` 将图表滚动区域拆为 `.cici-builder-graph__scroll` 内层，控件保留在外层 viewport 覆盖层；`frontend/src/assistant/cici-ui.css` 将控件由 sticky + 负 margin 改为外层绝对定位，避免缩放/适配改变内容尺寸后漂移。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- frontend/src/assistant/AgentBuilderShell.tsx frontend/src/assistant/cici-ui.css` 成功；`curl -sS -I http://127.0.0.1:5173/admin/agent-builder` 返回 `200 OK`。
- 已按用户截图移除流程图预览标题栏右侧说明文案：`frontend/src/assistant/AgentBuilderShell.tsx` 删除 `Dify 风格只读流程画布 · START 可跳转「触发与调度」` 这段非功能文案，保留 `workflow.preview.graph`。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- frontend/src/assistant/AgentBuilderShell.tsx` 成功。
- 已按用户反馈优化“发布渠道”tab 的视觉表达：`frontend/src/assistant/cici-ui.css` 将 `.cici-builder-publish-menu` 改为单层列表容器，`.cici-builder-publish-menu__item` 去掉独立卡片边框、圆角、阴影和浮起动效，只保留行分隔、浅香槟 hover/active 背景和 active 底部金线。
- 发布渠道状态胶囊已从旧蓝色收回金账房语汇：启用态使用暖白/深金文字，普通态使用浅香槟/暖棕文字。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- frontend/src/assistant/cici-ui.css` 成功。
- 已按用户截图修正编译结果区各 tab 内容宽度对齐：`frontend/src/assistant/cici-ui.css` 为 `.cici-builder-compile` 及其直接子级统一 `width: 100%` / `box-sizing: border-box`，并将 `.cici-builder-runtime` 左右 padding 设为 0。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- frontend/src/assistant/cici-ui.css` 成功。
- 已按用户截图修正编辑页标题区与 tab 线条：`.admin-main > .cici-builder--full .cici-builder__header` 去除 `border-bottom`，`.cici-builder-card__head--editor/--compile` 去除额外 bottom padding 并让 tab item 与底线对齐。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- frontend/src/assistant/cici-ui.css` 成功。
- 已按用户截图修正智能体头像操作区按钮不对齐：`frontend/src/assistant/cici-ui.css` 将头像操作区的 `label` 伪按钮与原生 `button` 统一为 `box-sizing: border-box`、34px 固定高度、清零 margin 和一致的 `appearance`，避免两个按钮上沿错位。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- frontend/src/assistant/cici-ui.css` 成功。
- 已按用户要求继续优化管理端“智能体构建”编辑页整体样式：`frontend/src/assistant/cici-ui.css` 在 Agent Builder 覆盖层内移除 editor/composer/compile 外层面板边框，保留必要内层单层边界，减少框套框。
- Agent Builder 编辑页页签已进一步收回文本 tab：去除背景框、圆角和阴影，使用暖棕文字、深金 active 文本和 2px 金色下划线；隐藏编辑页/编译区冗余英文角标。
- 智能体头像区域已校准：头像预览固定 58px，上传/清除按钮固定 34px 高并在 58px 行高内居中，修复按钮与头像不对齐。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- frontend/src/assistant/cici-ui.css` 成功。
- 已按用户截图修正管理端“智能体构建”编辑页左侧边缘白框：`frontend/src/assistant/cici-ui.css` 为 `.admin-main > .cici-builder--full` 增加与列表页一致的负 margin、暖象牙背景和内边距覆盖，使编辑页根容器吃满管理端内容面。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- frontend/src/assistant/cici-ui.css` 成功。
- 已按用户要求新增会话工作台快捷指令能力：`backend/src/main/resources/db/migration/V38__user_quick_command.sql` 新增 `user_quick_command` 表，`UserWorkflowController` 新增 `GET/POST /me/agents/{agentId}/workflow/quick-commands`。
- `frontend/src/assistant/AssistantApp.tsx` 的“快捷指令”按钮已从技能选择器中拆出，改为独立加载当前用户给当前智能体设置的快捷指令；没有快捷指令时可直接在轻量浮层里填写名称和指令内容并保存。
- 已按截图反馈调整添加流程：轻量浮层内不再显示名称/指令输入框，只显示“添加快捷指令”动作；点击后打开独立 modal，保存成功后关闭 modal 并追加到当前智能体快捷指令列表。
- 快捷指令添加 modal 使用阻塞遮罩、`role="dialog"`、`aria-modal="true"`、关联标题和统一取消/添加页脚按钮，视觉保持暖象牙底、浅金边和香槟金主按钮。
- 已按用户最新反馈修正快捷指令添加 modal：header/body 统一 20px 水平内边距，字段容器、输入框、文本域和页脚动作同一宽度栅格对齐；关闭 `×` 明确 `border: 0`、`background: transparent`、`box-shadow: none`。
- 已按用户截图继续修正快捷指令菜单中的“添加快捷指令”按钮：`.cici-composer-quick__add` 改为 flex 居中布局，设置 `line-height: 1` 和轻微底部内边距修正，避免文字贴近底线。
- 项目设计事实源已补充关闭控件规则：`DESIGN.md`、`DESIGN.json`、`AGENTS.md`、`README.md` 均要求所有弹出框/模式窗口右上角关闭 `×` 是无边框纯图标/字形，不得出现可见方框、圆框或按钮边框。
- 点击快捷指令只会把指令内容填入 composer 并聚焦，不自动发送；打开快捷指令菜单会关闭技能菜单，打开技能菜单也会关闭快捷指令菜单。
- 已新增 `docs/specs/FEAT-017-workbench-user-quick-commands.md` 和 `TASK-043 Assistant workbench user quick commands`。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`backend mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` 成功；`git diff --check -- ...` 成功。
- 本轮追加验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- frontend/src/assistant/AssistantApp.tsx frontend/src/assistant/cici-ui.css docs/specs/FEAT-017-workbench-user-quick-commands.md .claw/current-status.md .claw/task-board.md .claw/test-report.md` 成功。
- 本轮样式规范验证通过：`node -e JSON.parse(DESIGN.json)` 成功；`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- frontend/src/assistant/cici-ui.css DESIGN.md DESIGN.json AGENTS.md README.md` 成功。
- 本轮按钮对齐验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- frontend/src/assistant/cici-ui.css` 成功。
- 已按用户最新反馈继续收小前台工作台技能选择列表：popover 宽度从 220px 降到 176px，单行从约 34px 收到 28px，主文字从 13px 降到 12px，副文字从 11px 降到 10px，图标从 16px 降到 13px。
- 技能选择菜单颜色已收回 `鎏金账房`：暖象牙底、`#ded2bb` 浅金边、轻阴影、`#2b2217` 墨色主文字、`#7c6d59` 暖棕辅助文字、`#faf4e8` 浅香槟选中态。
- 已同步规范：`DESIGN.md` / `DESIGN.json` / `AGENTS.md` / `README.md` 新增轻量浮层菜单规则，要求输入框工具、图标按钮和行操作触发的菜单使用 12px 主文字、10-11px 辅助信息、13-14px 图标、26-30px 行高、176-220px 宽度。
- 本轮验证通过：`node -e JSON.parse(DESIGN.json)` 成功；`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- frontend/src/assistant/cici-ui.css DESIGN.md DESIGN.json AGENTS.md README.md` 成功。
- 已按用户最新截图反馈收紧前台工作台技能选择列表：`frontend/src/assistant/cici-ui.css` 将技能 popover 宽度从 280px 降到 220px，列表项从大卡片感收为 34px 左右紧凑行，图标 16px，主文本 13px，副文本 11px。
- 已将后续新增功能 UI 统一尺寸规则沉淀到项目设计规范：`DESIGN.md` 新增 `Product UI Scale`，`DESIGN.json` 新增 `extensions.productUiScale`；`AGENTS.md` 和 `README.md` 同步要求新增产品 UI 默认控件/菜单 13px、辅助 11-12px、紧凑按钮 32-34px 高、图标 15-16px。
- 本轮验证通过：`node -e JSON.parse(DESIGN.json)` 成功；`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- frontend/src/assistant/cici-ui.css DESIGN.md DESIGN.json AGENTS.md README.md` 成功。
- 已按用户最新反馈继续调小前台工作台 composer：输入区桌面字号从 15px 降到 13px，底部上传、快捷指令、技能按钮从 38px 高降到 32px，图标从 18px 降到 15px；移动端覆盖同步收紧。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- frontend/src/assistant/cici-ui.css` 成功。
- 已按用户最新截图反馈收紧前台工作台 composer：`frontend/src/assistant/cici-ui.css` 将输入区从大号展示态回收为紧凑消息输入态，底部上传、快捷指令、技能按钮统一为 38px 高、14px 字号、18px 图标。
- 技能按钮增加最大宽度和省略规则，避免长技能名把底部工具条撑成超大按钮；移动端断点同步使用同一紧凑规格。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- frontend/src/assistant/cici-ui.css` 成功。
- 已按用户截图调整前台智能体工作台对话框：底部工具条新增文件/图片选择按钮、“快捷指令”按钮和“技能”按钮；移除原来对话框左侧 `+` 菜单。
- 技能按钮会调用 `/agents/{agentId}/skills` 加载当前工作台智能体启用的绑定技能，在输入框上方弹出选择列表；选中后聊天请求会带上 `activeSkillCode`。
- 输入 `/` 或点击“快捷指令”会打开技能选择器；上传按钮已接入浏览器文件选择，当前聊天附件上传接口尚未接入发送流程，选择后会明确提示待接入。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check -- frontend/src/assistant/AssistantApp.tsx frontend/src/assistant/cici-ui.css` 成功；`curl -sS -I http://127.0.0.1:5173/` 返回 `200 OK`。
- 已按用户要求新增前台登录页 `login_mode2`：`frontend/src/assistant/AssistantApp.tsx` 新增 `FRONT_LOGIN_MODE_CONFIG` / `FRONT_LOGIN_USER_MODE_CONFIG` 程序端配置常量、`LoginMode2Cube` 和 `AgentLoginMode2`；现有智能体登录页保留为 `login_mode1`。
- `login_mode2` 中央显示 CSS 3D 旋转立方体，使用现有登录页视觉资产作为六面贴图，并支持暂停/继续旋转与鼠标移动轻微倾斜；账号输入区位于立方体下方，复用组织 ID、手机号、短信验证码、获取验证码和登录逻辑。
- 已按用户最新要求将 `login_mode2` 立方体六面图片改为随机使用系统智能体头像：新增公开只读接口 `GET /public/agents/avatars?orgId=...` 返回已启用且内置/已发布智能体头像；前端未登录态按组织 ID 拉取头像池并为六面独立随机抽取，允许重复。
- 已按用户进一步要求调整为简化过渡：未登录默认显示品牌/模型相关六面图；点击登录并短信验证成功后登录框立即消失，品牌立方体高速旋转约 3 秒，不再加载或切换智能体头像，然后写入登录态进入系统。
- 已按用户最新要求将 `login_mode2` 点击登录后的 loading 旋转周期调整为 `0.1s`；整体 3 秒进入延迟不变。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check` 成功；`curl -sS -I http://127.0.0.1:5173/` 返回 `200 OK`。
- 已按用户要求新增三张未登录默认立方体面图片：`frontend/public/login-cube-openai.webp`、`frontend/public/login-cube-deepseek.webp`、`frontend/public/login-cube-ai-chip.webp`；其中 DeepSeek 原图已裁掉下方文字，仅保留鲸鱼标识，三张图均压缩为 512x512 WebP。
- 已按用户要求移除 `login_mode2` 立方体背后的菱形装饰框线：删除 `.login-mode2__cube-stage::before` / `::after` 两个伪元素样式，立方体本体、背景和表单逻辑不变。
- 已按用户最新要求移除 `login_mode2` 立方体下方“暂停旋转”按钮，并去掉登录页背景网格线与额外 ledger 线层；页面保留干净暖象牙渐变背景。
- `frontend/src/assistant/AssistantApp.tsx` 已移除登录过渡中的头像池加载和智能体头像切换；`loginMode2CubePhase` 只保留 `brand` / `loading` 两态，`loading` 阶段由 `loginMode2Entering` 隐藏表单并高速旋转 3 秒。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check` 成功；`curl -sS -I http://127.0.0.1:5173/` 返回 `200 OK`。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`git diff --check` 成功。
- 本轮保留无头像兜底：若当前组织没有可展示的智能体 `avatarBase64`，登录后过渡阶段继续使用 CICI / CloudCC 品牌默认图，避免未登录页出现空白或坏图。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=AuthFlowIntegrationTest test` 成功；`git diff --check` 成功。
- 已按用户最新要求移除顶部可见切换：不再暴露“智能体模式/人机模式”和 `login_mode1/login_mode2` 切换；`login_mode2` 顶部 CloudCC、标题和说明文字已移除，页面只保留立方体、暂停按钮与账号输入。
- `frontend/src/styles.css` 新增并收口 `login-mode2` 浅色暖象牙登录舞台、立方体与下方表单样式；保留 `login_mode1` 原深色前台登录页，但不在页面暴露版本切换入口。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；Chrome headless 截图检查 `http://127.0.0.1:5173/` 首屏顶部切换控件和说明文字已隐藏。
- 已完成知识库检索链路优化：`RagService` 新增 `retrieveDetailed`，返回上下文、知识库名称、分段耗时和 fallback 标记；原 `retrieveContext` 保持兼容。
- 已消除 RAG 命中校验路径上的主要 N+1 查询：知识库、chunk、document 改为批量加载后在内存中按命中顺序过滤，避免每个 vector hit 都重复查 chunk/document/kb。
- `/ai/chat/stream` 现在在检索前发送 `phase=retrieving`，检索后发送 `phase=rag_done`，payload 含 `knowledgeBaseIds`、`knowledgeBaseNames`、`contextCount`、`elapsedMs`、`timingsMs` 和 `fallbackUsed`；后端日志记录 `chatStream RAG done` 便于定位耗时。
- 前台工作台状态机已接入 RAG phase：检索中显示“正在检索知识库：xxx”，完成后显示命中片段数、引用知识库和是否 fallback，不再只停留在“正在理解你的办公请求”。
- 本轮验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -DskipTests compile`、`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=KnowledgeBaseLifecycleIntegrationTest test`、`frontend npm run build`、`frontend npm test` 均成功；`curl -sS -I` 探测 8080 health 与 5173 首页均返回 200。
- 已按用户要求将知识库文档列表“操作”列改为系统统一三点菜单：`AdminKnowledgePage` 使用共享 `admin-row-menu` 结构，行 hover/focus 显示三点触发器，菜单内纵向展示发布/重试/重建/下线/重命名/启停/归档/切片/元数据/删除等动作；真实 `td` 保持 table-cell，不直接改 flex/grid/block。
- 已将三点行操作沉淀为系统统一标准规范：`DESIGN.md`、`DESIGN.json`、`AGENTS.md`、`README.md` 均明确高密度管理端列表行操作使用三点 hover/focus 菜单，新表格优先使用共享 `admin-row-menu` 类族。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`node -e "JSON.parse(...DESIGN.json...)"` 成功；`curl -sS -I http://127.0.0.1:5173/admin/kb` 返回 `200 OK`。
- 已按用户截图完成管理端 Agent 构建列表页视觉 lint：修正页面左侧露白、搜索框旧蓝色 focus/高亮、搜索图标与文本光标间距异常、标题字距/uppercase 问题；`frontend/src/assistant/cici-ui.css` 的 Agent Builder 覆盖层现在让列表页背景吃满管理端内容面，搜索 focus 使用香槟金，搜索图标绝对定位且输入文本从固定 padding 后开始，标题恢复为正常 `Agent 构建` 文案节奏。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；最近一次路由探测 `curl -sS -I http://127.0.0.1:5173/admin/agent-builder` 返回 `200 OK`。
- 已按用户要求统一组织控制台“知识库、模型、工具、集成应用”页面样式：在不改 React 页面结构、路由、数据流和列表/详情布局的前提下，补充 `frontend/src/styles.css` 的管理端资源页 `鎏金账房` 覆盖层，收拢背景、卡片、列表、按钮、tab、弹窗、状态开关、表格和模型/集成组件的暖象牙 + 墨色 + 香槟金线条语汇。
- 已按用户截图修正“集成应用”卡片底部设置图标不对齐：`.integration-card` 改为纵向 flex，`.integration-card__actions` 使用 `margin-top: auto` 固定到底部，让不同描述行数的卡片齿轮与启停控件保持同一底部基线。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`curl -sS -I` 探测 `/admin/kb`、`/admin/models`、`/admin/tools`、`/admin/integrations` 均返回 `200 OK`。
- 已按用户要求统一管理端“智能体构建”列表与编辑页样式：列表卡片、搜索、新建按钮、编辑页头、字段面板、资源行、页签、运行记录、流程图和选择器均收回到暖象牙、墨色文字、香槟金结构线体系，未改 React 页面结构和数据流程。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`curl -sS -I http://127.0.0.1:5173/admin/agent-builder` 返回 `200 OK`；浏览器插件因安全策略拒绝通过 `javascript:` URL 注入本地登录态，未做已登录页截图验收。
- 已按用户截图继续调整管理端左侧菜单：去掉“组织管理台”大标题，当前组织和管理员合并为无外框身份区，菜单只显示标题不显示第二行小字，退出后台移到顶部右侧图标按钮。
- 已按用户最新截图调整菜单上方用户信息布局：左侧 58px 圆形头像，右侧用户名大字号主标题，组织名作为次级文本单行省略。
- 已按用户要求调整管理端“用户”主页面：页面头部、用户列表、详情区域、页签、表单和按钮统一为 `鎏金账房` 金线风格，并减少列表项卡片框、详情统计框和胶囊页签等层叠边框。
- 已按用户指定样式调整用户信息 tab：tab 行使用浅金底线，未选中为暖棕文字，选中为深金文字 + 2px 金色下划线；该产品页 tab 规则已同步到 `DESIGN.md`、`DESIGN.json`、`AGENTS.md`、`README.md`。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`curl -sS -I http://127.0.0.1:5173/admin/kb` 返回 `200 OK`。
- 用户已确认 `TASK-035 Admin skill versioning import export` 完成人工验收，任务状态已标记为 `completed`；后续仅保留常规回归。
- 用户已确认 `TASK-028 Global avatar settings for agents and current user` 完成人工验收，任务状态已标记为 `completed`；后续仅保留常规回归。
- 已清理 `.agents/.DS_Store` 与 `.agents/skills/.DS_Store` 本地元数据噪音文件。
- 已实际复验 `ISSUE-2026-04-17-jdk25-mockito-inline`：`mvn -version` 显示 Maven 使用 Java `25.0.2`，`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` 成功；该 issue 已从 open 移到 resolved。
- 已新增并修正 `docs/specs/FEAT-016-external-agent-skill-package-optimization.md`：设计导出技能 zip 通过通用外部智能体优化后反向导入系统的闭环。
- 已修复自定义技能删除误报“仍有已发布运行时版本引用该技能”：删除影响分析现在只统计启用 Agent 的当前 `published_version_id` 指向的 `PUBLISHED` 工作流版本引用，历史 archived 工作流 Skill ref 不再阻断删除。
- 已补回归：当前发布运行时引用 Skill 时仍阻止删除；Agent 发布到不含该 Skill 的新版本后，旧发布快照保留但 `delete-impact` 返回可删除。
- 本轮验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillGovernanceIntegrationTest test` 成功。
- FEAT-016 已调整为当前 8 文件结构：`manifest.json`、`SKILL.md`、`cici-skill.md`、`prompt.md`、`contract.json`、`resources.json`、`PACKAGE_SPEC.md`、`README.md`；系统未上线，不保留旧 `skill.md` 导入兼容。
- FEAT-016 明确 OpenClaw、Codex、Claude Code、Cursor 等只是外部智能体工具代表；方案不绑定具体平台，只依赖通用 `cici-skill-package-optimizer/SKILL.md` 规则读取并优化 zip。
- FEAT-016 明确不做导入前 diff 预览增强，导入仍沿用现有预览、资源映射、草稿落地和本系统内发布流程。
- 已实现 FEAT-016：`SkillPackageService` 会在导出包中生成行业通用入口 `SKILL.md` 和包内规范 `PACKAGE_SPEC.md`，并在 `README.md` 追加外部智能体优化说明；Cici 内部规格正文改为 `cici-skill.md`。
- 已新增 `.agents/skills/cici-skill-package-optimizer/SKILL.md` 初版，定义外部智能体处理 `universal-skill-package@1.0` 的解压、优化、校验和重打包规则。
- 已更新导出导入回归：`SkillGovernanceIntegrationTest` 断言导出 zip 包含 `SKILL.md`、`cici-skill.md`、`PACKAGE_SPEC.md` 和 optimizer 提示，导入 helper 使用当前 8 文件结构。
- 本轮验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillGovernanceIntegrationTest test` 成功。
- 已将 `TASK-037 External agent skill package optimization loop` 标记为 `completed`；剩余可选项是真实外部智能体端到端人工验收。
- 已定位导入技能 zip 报 `Skill code already exists: email-marketing-campaign2` 根因：自定义 Skill 删除是软删除，`skill_definition` 保留 `lifecycle_status=DELETED` 的隐藏记录，但唯一索引仍占用原 `skill_code`，导入创建阶段仍按原 code 判重。
- 已修复软删除 code 占用：删除 Skill 时将旧记录 `skill_code` 归档为 `原code__deleted_{id}`；创建新 Skill 时如遇到历史 `DELETED` 同 code，会先归档旧记录并 `flush`，再创建新技能。
- 已新增 Flyway `V36__archive_deleted_skill_codes.sql`，用于处理已经软删除但仍占用 code 的历史数据，覆盖用户已经手动删除后仍导入失败的场景。
- 已补回归：删除 `feat014-custom-skill` 后导入同 code zip 并调用 `/skills/imports/{importId}/create`，应成功创建新的 `DRAFT` 技能。
- 本轮验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillGovernanceIntegrationTest test` 成功；`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillAuthoringIntegrationTest test` 成功。
- 已定位管理端 Skill 导出截图根因：导出 job 创建请求带了 `Authorization`，但随后 `window.location.href=/skills/exports/{id}/download` 地址栏请求不会带管理员 token，后端 `@RequireOrgAdmin` 返回 `{"success":false,"message":"需要组织管理员权限"}`，浏览器直接展示 JSON。
- 已修复列表页与编辑页导出：新增 `downloadSkillExportPackage(...)`，使用带 `Authorization: Bearer <admin token>` 的 `fetch` 拉取 zip blob，再触发浏览器下载。
- 后端导出下载响应已从 `application/octet-stream` 改为 `application/zip`；`/skills/exports/{exportId}/download` 从类级管理员拦截中摘出，其他 Skill 管理接口逐个保留 `@RequireOrgAdmin`。
- 集成测试现在断言下载内容是通用技能包 zip，并包含当前 8 文件结构：`manifest.json`、`SKILL.md`、`cici-skill.md`、`prompt.md`、`contract.json`、`resources.json`、`PACKAGE_SPEC.md`、`README.md`；同时断言直接访问下载 URL 成功、未登录访问 `/skills` 仍返回 403。
- 已重启本地 8080 后端到当前源码；真实 localhost 探针验证 `GET /skills/exports/{newExportId}/download` 无 Authorization 也返回 `200 application/zip`，文件名 `email-marketing-campaign2-skill-package.zip`。
- 本轮验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillGovernanceIntegrationTest test` 成功；`frontend npm run build` 成功，保留既有 Vite chunk-size warning；本地接口探针成功。
- 已新增 `docs/specs/FEAT-015-skill-declarative-api-runtime.md`：定义 Skill 中声明远程 API 契约，发布时编译为 Skill 专属 function schema 与后端 execution plan，运行时只注入当前激活 Skill 的专属 API 工具。
- 已明确 `runtimeApis` 不进入普通 `toolWhitelist`：它是 Skill 私有 API 动作；最终用户不可见，模型只看到抽象 function schema，管理员可配置，平台可治理和审计。
- 已新增 `TASK-036 Skill declarative API runtime`，优先级设为 `P0`，下一步先做后端最小闭环与安全边界实现。
- FEAT-015 后端第一轮已实现：新增 `V37__skill_declarative_api_runtime.sql`、`skill_api_tool` 发布计划表、`runtime_api_draft_json` / `runtime_api_snapshot_json` 字段、`SkillApiToolService` 编译/执行服务。
- Skill 创建、更新、预览和发布接口已支持 `runtimeApis`；发布时会生成 `skillapi__{skillCode}__{apiCode}` 专属工具和后端 execution plan，预览不安全 API 会返回阻断错误。
- 运行时已接入当前 Skill API 工具注入：仅 ambient 或 active Skill 的发布版本 API 工具进入模型工具列表，`ToolOrchestratorService` 对非激活上下文的 `skillapi__` 调用会拒绝执行。
- 执行器已覆盖参数 schema 校验、请求模板渲染、host 白名单、localhost/内网基线阻断、超时、响应大小限制、结果路径提取、数组裁剪、字段脱敏和 `SKILL_API_TOOL_INVOCATION` 审计。
- 本轮验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` 成功；`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillGovernanceIntegrationTest#shouldPublishDeclarativeSkillApiAndInjectOnlyWhenSkillIsActive test` 成功；`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillGovernanceIntegrationTest,SkillAuthoringIntegrationTest test` 成功。
- FEAT-015 第二阶段已新增管理端 Skill 编辑页“内嵌 API”页签：支持添加多个 Skill 私有 API 动作，编辑 apiCode、名称、描述、风险、方法、URL、authRef、超时、确认要求、参数 schema、请求映射和返回映射；保存和编译预览会提交 `runtimeApis`。
- FEAT-015 `authRef` 已接入第一种真实服务端凭证解析：`integration:tavily.apiKey` 从现有集成应用配置读取并解密 Tavily API key，运行时注入 `Authorization: Bearer ...`，模型和前端仍不可见密钥。
- FEAT-015 回归已新增本地 HTTP smoke：发布带 `authRef=integration:tavily.apiKey` 的 Skill API，激活 Skill 后调用临时 HTTP endpoint，断言服务端注入 Authorization header、响应结果可返回且敏感字段被脱敏。
- 本轮验证通过：`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillGovernanceIntegrationTest#shouldPublishDeclarativeSkillApiAndInjectOnlyWhenSkillIsActive test` 成功；`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillGovernanceIntegrationTest,SkillAuthoringIntegrationTest test` 成功。
- FEAT-015 已新增 `authRef=integration:cloudcc.accessToken` / `cloudcc.accessToken` / `integration:cloudcc.userToken` / `cloudcc.userToken` 支持：发布期校验 CloudCC CRM 集成启用，运行期按当前用户通过 `CloudccAccessTokenService` 解析用户态 token，并注入 `accessToken` header。
- FEAT-015 回归已扩展本地 CloudCC smoke：临时 mock CloudCC domain、token 与业务 API endpoint，断言 Skill API runtime 使用当前用户绑定凭证换取 token、调用固定 URL 时注入 `accessToken` header，并按 `$..accessToken` 脱敏响应。
- 管理端 Skill 编辑页“内嵌 API”鉴权引用提示已同步支持范围，示例从 Tavily 切为 `integration:cloudcc.accessToken`。
- 本轮验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillGovernanceIntegrationTest#shouldPublishDeclarativeSkillApiAndInjectOnlyWhenSkillIsActive test` 成功；`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillGovernanceIntegrationTest,SkillAuthoringIntegrationTest test` 成功；`frontend npm run build` 成功（保留既有 Vite chunk-size warning）；`curl -sS -I http://127.0.0.1:5173/admin/skills/new` 返回 `200 OK`。
- 已定位自定义技能删除 toast `删除检查失败：Unexpected server error` 根因：浏览器请求的是 `GET /skills/7/delete-impact`，但 8080 本地后端仍是旧运行进程，未加载源码中的 delete-impact 接口，Spring 将其当作静态资源缺失并被全局兜底包装成 500。
- 已更新 `GlobalExceptionHandler`：路由不存在返回 `404 Resource not found`，路径参数类型不匹配返回 `400 Invalid ...`，避免此类客户端/版本错配问题继续显示为服务器未知错误。
- 已重启本地后端到当前源码；Flyway 已从 PostgreSQL schema v34 迁移到 v35，`/skills/{id}/delete-impact` 现在已被后端映射，未登录访问返回权限错误而非静态资源缺失。
- 本轮验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillGovernanceIntegrationTest test` 成功；`curl -sS -i http://127.0.0.1:8080/health` 返回 404 `Resource not found`，验证未匹配路由不再走 500。
- 已为管理端 Skill 新建/编辑页“继续优化”增加本地回退快照：优化成功前缓存当前表单、编译预览、需求解析结果、会话 ID、需求描述和追问答案。
- “继续优化”成功后显示“回退本次优化”按钮；点击会恢复优化前状态并清除回退快照，适用于用户不满意本次模型优化且尚未保存的场景。
- 保存草稿、重新生成、清空、重置、加载技能或填充模板时会清除回退快照，避免已保存或新上下文下误回退。
- 本轮验证通过：`frontend npm run build` 成功，保留既有 Vite chunk-size warning。
- 已修复管理端 Skill 新建/编辑页“继续优化”的增量合并语义：后端优化上下文现在包含当前提示片段与规格正文，并明确约束模型不得在“增加/补充/加入/再增加”类请求中改写既有步骤。
- 服务端合并结果新增确定性保护：对“再增加百度搜索”这类增量请求，保留当前 `promptFragment` / `draftSpecText`，只追加“增量优化要求”，避免模型把原市场活动流程重排成另一套流程。
- 已新增回归测试覆盖用户实际场景：邮件市场营销活动草稿点击“继续优化：再增加百度搜索”后，提示片段仍保留 `insert_campaign_data_with_role_right`、`get_lead_data`、`add_campaign_member`、`email_send` 等原步骤，并包含“百度搜索”。
- 本轮验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillAuthoringIntegrationTest test` 成功。
- 已按用户要求把管理端 Skill 新建/编辑页自然语言生成区“摘要预览”改为“需求解析”，顶部状态 chip 从摘要语义改为“待解析 / 解析中 / 已解析”。
- 已移除需求解析空态里的“目标 / 触发 / 输出 / 暂无摘要”，改为单句“暂无待解析的需求”。
- 已新增生成草稿期间的需求解析动态进程：点击“生成草稿”后先清空旧解析，展示“正在解析需求”与三个进程标签，直到生成完成后显示解析内容。
- 已将“继续优化”接入同一套需求解析动态进程：提交前缓存当前草稿与追问答案，再清空旧解析展示解析中状态，避免丢失要提交的优化上下文。
- 本轮验证通过：`frontend npm run build` 成功，保留既有 Vite chunk-size warning。
- 已按用户截图调整管理端 Skill 新建/编辑页页头操作：新建态按钮不再显示“创建草稿”，统一显示“保存草稿”；“预览编译”更名为“编译预览”，并移到保存草稿之后。
- 本轮验证通过：`frontend npm run build` 成功，保留既有 Vite chunk-size warning。
- 已按用户截图互换管理端 Skill 新建/编辑页字段页签顺序：`EDITOR_TABS` 中“编译预览”移动到“版本管理”之前。
- 本轮验证通过：`frontend npm run build` 成功，保留既有 Vite chunk-size warning。
- 已统一管理端 Skill 弹框关闭样式：发布说明弹框与白名单选择弹框共用 `skills-modal-head` / `skills-modal-close`，右上角关闭控件不再显示边框或按钮块，只保留 × 图标及轻量 hover/focus 反馈。
- 已统一管理端 Skill 弹框按钮样式：`skills-compose__header-btn` 现在自带暖白金线次级样式，白名单弹框“取消”不再回退到旧蓝色渐变；管理端 `dify` 主/次按钮也改回香槟金与暖白金线体系。
- 已把后续按钮和弹框规则沉淀到 `DESIGN.md`、`DESIGN.json`、`AGENTS.md`、`README.md`：产品页按钮必须遵守统一 primary / secondary / danger 语汇，所有弹出框默认实现为模式窗口，除非规格明确例外。
- 已按用户截图调整管理端 Skill 新建/编辑页右侧滚动间距：`.skills-compose` 增加右侧 padding 和稳定滚动槽位，窄屏下收为较小右侧留白。
- 本轮验证通过：`frontend npm run build` 成功，保留既有 Vite chunk-size warning。
- 已将管理端 Skill 发布说明从浏览器原生 prompt 改为项目统一模式窗口：弹窗居中显示，使用 `鎏金账房` 暖象牙/金线样式，包含说明文案、textarea、取消和确认发布按钮；空说明禁用确认发布。
- 本轮验证通过：`frontend npm run build` 成功，保留既有 Vite chunk-size warning。
- 已调整管理端 Skill 新建/编辑页发布说明交互：基础信息不再显示“变更日志”，保存草稿不再提交用户填写的变更日志；点击“发布”时会弹出输入框要求填写本次版本发布说明，并将该说明传给发布接口。
- 本轮验证通过：`frontend npm run build` 成功，保留既有 Vite chunk-size warning。
- 已移除管理端 Skill 新建/编辑页导入 zip 能力入口：`AdminSkillComposePage` 顶部不再显示“导入zip”，页面内导入预览工作区及相关状态/函数已清理；列表页导入技能入口不受影响。
- 本轮验证通过：`frontend npm run build` 成功，保留既有 Vite chunk-size warning。
- 已调整管理端 Skill 新建/编辑页发布入口：自定义技能无论新建还是编辑都显示主按钮“发布”，未保存的新技能点击发布时会先创建草稿，再调用发布接口；“创建草稿/保存草稿”保留为次级按钮。
- 本轮验证通过：`frontend npm run build` 成功，保留既有 Vite chunk-size warning。
- 已修正管理端 Skill 新建/编辑页版本管理入口不明显的问题：`AdminSkillComposePage` 的字段页签新增“版本管理”，新建页显示创建草稿后的版本生成说明，自定义技能编辑页直接展示最近三个可恢复版本、发布状态、来源、变更日志、差异摘要和“恢复为当前草稿”动作；页头“版本管理”按钮会直接切到该页签。
- 本轮验证通过：`frontend npm run build` 成功，保留既有 Vite chunk-size warning；`curl -sS -I http://127.0.0.1:5173/admin/skills/new` 返回 200。
- 已修复管理端 Skill 列表严重错位：根因是全局样式把真实 `td.skills-data-table__summary` 改为 `display: -webkit-box`、把 `td.skills-data-table__actions` 改为 `display: flex`，导致浏览器不再按表格单元格对齐；本轮改为内部元素承载截断和按钮 flex，并为列表专属表格使用 100% 固定列宽、关闭横向 overflow。
- 已把管理端 Skill 列表本轮所有调整总结进项目规范：`DESIGN.md` 新增 `Admin CRUD Lists`，`DESIGN.json` 新增结构化 `extensions.adminCrudLists`，`AGENTS.md` / `README.md` / FEAT-012 / DEC-023 / TASK-027 均同步约束。
- 本轮验证通过：`frontend npm run build` 成功，保留既有 Vite chunk-size warning；真实 in-app browser 管理页验收停在 `/admin/login`，未在未获确认时提交手机号登录。
- 已按用户反馈继续整理管理端 Skill 列表：去掉可见双语英文、去掉“历史派生”筛选、标准技能不再显示“查看”、自定义技能列表行不再显示“发布”，右上“新建技能”改用列表页主按钮类避免样式缺失。
- 本轮验证通过：`frontend npm run build` 成功，保留既有 Vite chunk-size warning。
- 已按用户截图把管理端 Skill 列表自定义技能操作收进 hover 三点菜单：鼠标经过行时显示三点按钮，点击后出现编辑/导出/删除纵向菜单；支持外部点击和 Escape 关闭。
- 本轮验证通过：`frontend npm run build` 成功，保留既有 Vite chunk-size warning。
- 已按用户截图继续调整管理端 Skill 列表页头：去掉“技能工作台”和“技能列表”两段辅助文字，右上“导入技能/新建技能”统一为同尺寸、同边框、同 hover 的列表页按钮样式。
- 本轮验证通过：`frontend npm run build` 成功，保留既有 Vite chunk-size warning。
- 已按 FEAT-014 完成第一轮实现：新增 `V35__skill_versioning_import_export.sql`，扩展 `skill_version` changelog/diff/source/restore/retention 字段和 `skill_definition` lifecycle/delete/publish 字段；后端补版本列表、恢复、发布、删除影响分析、软删除、导出 zip、导入 zip 创建和派生入口拒绝；前端补管理端 Skill 列表页导入/发布/导出/删除入口，以及编辑页版本侧栏、恢复、发布、导出、删除、导入 zip 和变更日志入口。
- FEAT-014 第二轮硬化已完成：`SkillPackageService` 支持模型标准化优先与确定性回退，导出前执行 manifest/schema 校验与敏感信息扫描；导入预览新增 `resourceMapping`（工具/知识库匹配与未匹配信息），`/skills/imports/{importId}/create` 支持 `draftOverride` 可编辑覆盖创建；管理端新建页导入改为“先载入可编辑草稿，不自动创建”。
- FEAT-014 导入预览工作区已落地到 `/admin/skills/new`：支持导入后独立展示可编辑草稿字段、工具/知识库映射明细、告警列表，以及“载入编辑区/直接创建草稿/关闭预览”操作，避免导入后立即盲创建。
- FEAT-014 导入预览工作区已补齐可编辑字段：新增“升级处理规则/输出约定”编辑项；“直接创建草稿”前端新增 `skillCode/name` 必填校验，避免点击后才由后端报错。
- 本轮验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillGovernanceIntegrationTest,SkillAuthoringIntegrationTest test`；`frontend npm run build` 通过，保留既有 Vite chunk-size warning。
- FEAT-014 当前剩余收口项：需要继续补真实浏览器人工点击验收（本轮已完成构建校验与 `curl -I http://127.0.0.1:5173/admin/skills/new` 可达性校验）。
- 已按用户要求移除管理端 Skill 编辑页页头“停用”按钮和对应前端 DELETE 调用入口，避免与左侧“启用”开关语义重复；本轮 `frontend npm run build` 通过，保留既有 bundle size warning。
- 已完成管理端 Skill 自然语言生成/编辑功能优化：后端会在模型生成与 fallback 生成后扫描 sourceText、提示片段和规格正文中的候选工具/知识库引用，自动补入 `toolWhitelist` / `kbWhitelist`；自定义工具描述也可作为显式工具引用匹配。
- 已修复已有 Skill 编辑页“继续优化”套用结果会丢失当前 `id` 的问题；生成/优化结果现在保留当前技能身份、启用状态和治理字段，保存时更新原技能而不是误创建新技能；平台标准等不可编辑技能的自然语言正文优化入口已禁用并提示先派生。
- 本轮验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillAuthoringIntegrationTest test`；`frontend npm run build` 通过，保留既有 bundle size warning。
- 已按用户截图将管理端 Skill 新建/编辑页页头“启用”开关移到按钮条最左侧，并去掉启用旁问号；本轮 `frontend npm run build` 通过，`/admin/skills/new` 返回 200。
- 已按用户截图去掉管理端 Skill 新建/编辑页“编译预览”框内重复的“编译预览”标题和问号，改为直接显示原问号说明文案；本轮 `frontend npm run build` 通过，`/admin/skills/new` 返回 200。
- 已按用户要求将管理端 Skill 新建/编辑页“启用”开关从基础信息字段区移到页面顶部按钮条最右侧，基础信息区不再显示启用字段；本轮 `frontend npm run build` 通过，`/admin/skills/new` 返回 200。
- 已按用户要求去掉管理端 Skill 新建/编辑页“基础信息”中启用开关外层字段框，改为标题在上、开关在下的无框字段节奏；本轮 `frontend npm run build` 通过，`/admin/skills/new` 返回 200。
- 已按用户要求将管理端 Skill 新建/编辑页“基础信息”中的启用控件从原生 checkbox 改为 `role="switch"` 的按钮式开关，沿用 `enabled` 字段保存契约；本轮 `frontend npm run build` 通过，`/admin/skills/new` 返回 200。
- 已按用户最新截图继续调整管理端 Skill 新建/编辑页“边界规则”：升级处理规则与输出约定各自独占上方一整行，知识库白名单和工具白名单并排放在下方；本轮 `frontend npm run build` 通过，前端 dev server 已重启在 `http://127.0.0.1:5173/`，`/admin/skills/new` 返回 200。
- 已按用户截图继续调整管理端 Skill 新建/编辑页“边界规则”：升级处理规则与输出约定收窄到左侧，右侧改为知识库白名单和工具白名单资源面板，并接入与智能体构建相同的添加选择器交互（含 MCP 工具分组）；本轮 `frontend npm run build` 通过，`/admin/skills/new` 本地 dev server 返回 200。
- 已将管理端 Skill 新建/编辑页“提示片段”输入框改用与“规格正文”相同的大文本框高度规则；本轮 `frontend npm run build` 通过。
- 已按用户截图将管理端 Skill 新建/编辑页的原“执行提示”页签拆成“提示片段”和“规格正文”两个独立 tab，并让每个 tab 独占单个大文本编辑区；本轮 `frontend npm run build` 通过。
- 已继续按截图反馈修正管理端 Skill 新建/编辑页：需求描述标题拆出为与摘要预览相同结构，左右内容框统一高度；问号提示图标缩到 10px；自然语言生成区下方横线已移除；页面由固定满屏局部滚动改为整体页面可滚动；本轮 `frontend npm run build` 通过。
- 已继续按截图反馈微调管理端 Skill 新建/编辑页：需求描述与摘要预览采用相同标题行高度和内容区行高，去掉下方基础信息编辑区与状态摘要之间的竖向分割线，问号提示图标从 18px 收到 14px；本轮 `frontend npm run build` 通过。
- 已按最新截图调整管理端 Skill 新建/编辑页：右侧基础信息等页签编辑区移动到自然语言生成下方并占满下方屏幕；自然语言生成内的需求描述与摘要预览改为横排双栏；本轮 `frontend npm run build` 通过。
- 已完成智能体当前日期上下文修复：新增 `RuntimeContextPromptService`，每次对话以 `Asia/Shanghai` 注入当前日期、中文日期、星期、时间和时区，并要求模型按该上下文解释“今天/明天/昨天/本周”等相对日期；`/ai/chat` 响应新增 `runtimeContext` 便于排查。
- 已按截图反馈修复管理端 Skill 编辑页左侧重叠：自然语言生成标题的问号 tooltip 改为向右展开，不再盖住标题；左侧区域允许局部滚动，需求描述输入区高度调整为 `clamp(190px, 23vh, 260px)`，生成/优化/清空按钮不再与输入框或摘要预览重叠；本轮 `frontend npm run build` 通过。
- 已按截图反馈继续微调管理端 Skill 新建/编辑页：左侧标题改为“自然语言生成”，左侧宽度从最多 320px 调到最多 396px（约 +2cm），需求描述输入区固定为 `clamp(210px, 26vh, 300px)`，下方生成/优化/清空按钮不再被遮住；本轮 `frontend npm run build` 通过。
- 已继续完成管理端 Skill 新建/编辑页页签化调整：顶部按钮统一为导入/重置/预览编译/创建或保存草稿，右侧字段区改为基础信息、执行提示、边界规则、编译预览页签；长文本编辑集中在执行提示页签的大文本区，`转人工规则` 已改名为 `升级处理规则` 并在问号说明中解释适用场景；本轮 `frontend npm run build` 通过。
- 已完成管理端 Skill 新建/编辑页视觉优化：页头改为面包屑、状态 chips 与右侧操作区；Authoring 区改为“需求描述 + 摘要预览”的工作台；管理端 shell 和该页样式已切到暖象牙、墨色、香槟金线条基线；说明性文字已收进标题旁小问号 tooltip；Skill fields 已去掉外层和内部分组背景、分组标题与总标题；页头横向背景块已移除；字段标题 tooltip 靠左裁切问题已通过右展开定位修复。
- 已完成工作台主模型名展示：后端 `/ai/chat/stream` 的 `phase` SSE 会携带当前 `modelName`；前端把模型名绑定到当前助手消息，并在工作台消息 meta 中用小标签展示，仅显示模型名。
- 已完成智能体模型身份误报修复：数据库确认 `Anthropic` provider 处于 disabled 且无 key，当前 `demo-org` 聊天路由为 `aliyun-bailian / deepseek-v4-pro`，`cici-system` 智能体 model 也是 `deepseek-v4-pro`；新增运行模型上下文 prompt，要求模型按服务端真实 provider/model 回答模型身份问题。
- 已修复会话工作台流式消息消失问题：工作台提交后本地助手占位/部分 delta 不再被“仅包含用户 turn 的服务端旧历史”覆盖；如果占位已被覆盖，后续 delta 会自动补回助手消息继续追加；状态机 thought 变化不再触发工作台历史重拉。
- 已完成会话工作台输入体验修复：工作台多行输入框支持 `Enter` 发送、`Shift+Enter` 换行；语音识别结束后不再自动发送，只把识别内容回填输入框；工作台 ASR 5 秒无语音会自动停止并保留已识别内容；已追加修复语音结束后焦点仍在麦克风按钮导致回车重新开启语音、清空转写内容的问题。
- 已完成会话工作台对话过程微交互收口：`ChatMarkdown` 在 busy 且内容为空时渲染无可见文字的三点动态状态，普通空响应仍保留“本次未返回文字内容。”兜底；`frontend npm run build` 通过，前端 dev server 已启动在 `http://127.0.0.1:5173/`。
- 已完成 `TASK-028` 第一轮代码实现：新增 `V34__agent_definition_avatar_base64.sql`、智能体头像读写字段、`PUT /auth/me/avatar`、前台个人头像设置入口、Agent Builder 智能体头像设置和全局头像展示替换。
- 本轮编译验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` 与 `frontend npm run build` 均成功；已追加修复前台左上角 rail 头像被全局 button padding 挤压成窄条的问题，并新增“上传后缩放裁剪再保存头像”能力，复跑 `frontend npm run build` 成功。
- 已修复头像裁剪预览与最终保存不一致问题：裁剪预览改为与导出同一坐标模型，解决“应用裁剪后取景偏移”的误差。
- 已完成头像裁剪第二轮几何对齐：预览层去除 transform 矩阵依赖，改为显式 `left/top/width/height` 布局并与导出公式共用同一 display scale，进一步收敛取景误差。
- 已完成头像裁剪第三轮范围对齐：裁剪画布改为完整圆形头像预览，移除“方形区域里较小圆孔”的误导显示，让可见范围与最终保存头像一致。
- 本仓库已按 `cc-aidev-guidelines-common` `3.4.0` 补齐项目级声明：`.claw/` 继续作为 canonical state directory，`README.md` 与 `AGENTS.md` 已加入受管声明块。
- 已新增项目级页面设计治理：`impeccable` 现在是所有页面分析、设计、改版和 UI 实现的强制技能，设计事实源固定为根目录 `PRODUCT.md`、`DESIGN.md`、`DESIGN.json`。
- 根 `PRODUCT.md` 已从单独平台页上下文升级为全项目认证产品面的战略上下文；`DESIGN.md` / `DESIGN.json` 已升级为 assistant、admin、platform 共用的产品面设计基线。
- 已新增 `docs/specs/FEAT-012-project-design-governance.md` 与 `DEC-023`，用于沉淀本轮设计治理规则与后续例外处理方式。
- FEAT-011 已继续收口为暖白、香槟金、墨色的简约金线风格：`/platform/login`、概览、平台技能、内置工具、平台审计在保留紧凑控制台结构的前提下，增加了金线边框与更强的质感表达。
- 前台会话工作台已完成一轮侧栏层级调整：左侧顶部状态机移除，右侧改为“顶部精简状态机 + 下方概览衔接会话历史”的结构。
- 前台会话工作台头像尺度已整体下调一档：顶部智能体切换头像、右侧状态机头像和消息区头像都已缩小，且 `frontend npm run build` 通过。
- 前台会话工作台左侧 rail 已完成一轮重排和配色统一：头像移到上方、logo 移到底部、tooltip 去重，且整体视觉已和主页面对齐。
- 当前最高优先级阻塞是 `TASK-023`：CloudCC 真实工具 smoke 仍卡在“用户绑定凭证失效 + 聊天入口缺少 Aliyun API key”两处。
- `TASK-020`（FEAT-008）按用户要求继续暂停；`TASK-007`（SaaS 计费）仍停留在设计态。

## Active Task Index

- `TASK-043`：Assistant workbench user quick commands（completed，P1）
- `TASK-042`：Assistant workbench composer upload and skill picker（completed，P1）
- `TASK-041`：Front login mode2 rotating cube variant（completed，P1）
- `TASK-040`：Assistant knowledge retrieval latency and state visibility（completed，P1）
- `TASK-039`：Admin resource pages visual style unification（completed，P1）
- `TASK-038`：Admin agent builder visual style unification（completed，P1）
- `TASK-037`：External agent skill package optimization loop（completed，P1）
- `TASK-036`：Skill declarative API runtime（in_progress，P0）
- `TASK-035`：Admin skill versioning import export（completed）
- `TASK-034`：Admin skill authoring resource whitelist and edit refinement（completed）
- `TASK-028`：Global avatar settings for agents and current user（completed）
- `TASK-033`：Admin skill editor visual refresh（completed）
- `TASK-032`：Assistant workbench model label display（completed）
- `TASK-031`：Assistant model identity hallucination fix（completed）
- `TASK-030`：Assistant workbench streaming message preservation（completed）
- `TASK-029`：Assistant workbench enter send and ASR finish behavior（completed）
- `TASK-027`：Project-wide impeccable design governance（completed）
- `TASK-026`：Assistant workbench rail cleanup and reorder（completed）
- `TASK-025`：Assistant workbench sidebar state layout refinement（completed）
- `TASK-024`：Platform console visual refresh（completed）
- `TASK-023`：CloudCC runtime smoke unblock
- `TASK-020`：Knowledge base lifecycle completion（paused）
- `TASK-007`：SaaS billing and packaging design（pending）

## Verified Facts

- `frontend/src/admin/pages/AdminKnowledgePage.tsx` 新增 `openDocActionMenuId` 和外部点击 / Escape 关闭逻辑，知识库文档行操作已由多个常驻按钮改为 `admin-row-menu` 三点菜单。
- `frontend/src/styles.css` 已新增共享 `.admin-row-menu*` 样式，并让既有 `.skills-row-menu*` 与新通用样式保持同一触发器、菜单、菜单项和危险项视觉语汇。
- `DESIGN.md` / `DESIGN.json` / `AGENTS.md` / `README.md` 已记录统一行操作标准：三点触发器 + 不透明纵向菜单 + 子容器承载，禁止通过改真实表格元素 display 实现。
- `frontend/src/assistant/cici-ui.css` 中 `.admin-main > .cici-builder-sidebar--page` 现在使用负 margin + 内部 padding 覆盖管理端内容面内边距，让 Agent 构建列表页的暖象牙背景不再露出左侧白边。
- `.cici-builder-sidebar__eyebrow` 在 Agent Builder 覆盖层中重置 `letter-spacing: 0` 与 `text-transform: none`，避免旧全局规则将标题渲染成松散的 `AGENT 构建`。
- `.cici-builder-sidebar__search:focus-within` 与搜索 input 现在使用金色 focus ring 和无蓝色 outline/box-shadow。
- `frontend/src/styles.css` 已新增 `Admin resource pages: Gilded Ledger visual unification` 覆盖层，作用于组织控制台知识库、模型、工具和集成应用相关页面样式。
- 本轮没有修改 `frontend/src/admin/pages/AdminKnowledgePage.tsx`、`AdminModelsPage.tsx`、`AdminToolsPage.tsx`、`AdminIntegrationsPage.tsx` 的 JSX 结构、路由、状态或数据请求逻辑。
- 资源页覆盖层通过 `.admin-main > .dify-kb-page` 与 `.admin-main > .admin-page` 继承管理端 `--admin-*` 设计变量，统一卡片、表格、MCP 列表、模型厂商列表、模型弹窗、集成应用卡片和 Dify 风格按钮/开关的视觉。
- `frontend/src/assistant/cici-ui.css` 已新增 `Admin Agent Builder: Gilded Ledger visual alignment` 作用域样式，覆盖 Agent Builder 列表页、编辑页、页签、表单、资源选择器、流程图和运行记录的旧蓝色视觉语汇。
- 本轮未修改 `frontend/src/assistant/AgentBuilderShell.tsx` 或管理端路由结构，页面结构和交互流程保持不变。
- `frontend/src/admin/pages/AdminSkillComposePage.tsx` 新增 `authoringParsing` 状态，`generateSkillDraft()` 发起请求时显示需求解析动态进程，请求完成后关闭并显示 `authoringResult`。
- `frontend/src/admin/pages/AdminSkillComposePage.tsx` 的 `refineSkillDraft()` 也会显示需求解析动态进程，并在清空旧解析前缓存 `currentSkillSpec` 与 `clarificationAnswers`。
- `frontend/src/styles.css` 新增 `.skills-authoring-readonly-loading*` 样式和 `skills-parse-dot` / `skills-parse-step` 动画，并兼容 `prefers-reduced-motion`。
- `frontend/src/admin/pages/AdminSkillComposePage.tsx` 的页头自定义技能保存按钮统一显示“保存草稿”，编译按钮显示“编译预览”，新建页常见顺序为保存草稿、编译预览、发布。
- `frontend/src/admin/pages/AdminSkillComposePage.tsx` 的 `EDITOR_TABS` 顺序现在是基础信息、提示片段、规格正文、边界规则、编译预览、版本管理。
- `frontend/src/admin/pages/AdminSkillComposePage.tsx` 的发布说明弹框和白名单选择弹框均使用 `skills-modal-head` 与 `skills-modal-close`。
- `frontend/src/styles.css` 已移除发布弹框和白名单弹框各自的有边框关闭按钮样式，改为统一无边框图标关闭控件。
- `frontend/src/admin/pages/AdminSkillComposePage.tsx` 新增 `publishDialogOpen` 与 `publishChangeLog` 状态，点击“发布”打开 `skills-publish-modal`，确认后将 textarea 内容作为 `changeLog` 发布。
- `frontend/src/styles.css` 新增 `.skills-publish-modal*` 样式，复用 `dify-modal-overlay`，让发布说明弹窗居中并保持管理端 `鎏金账房` 风格。
- `frontend/src/admin/pages/AdminSkillComposePage.tsx` 的基础信息页签只保留技能代码、显示名称、风险等级和摘要说明；“变更日志”表单项已移除。
- `frontend/src/admin/pages/AdminSkillComposePage.tsx` 的 `publishSkill()` 现在在发起发布前通过输入框收集版本发布说明；用户取消或空输入时不会调用发布接口。
- `frontend/src/admin/pages/AdminSkillComposePage.tsx` 的草稿保存请求现在固定提交空 `changeLog`，发布说明只由发布动作收集。
- `frontend/src/admin/pages/AdminSkillComposePage.tsx` 已不再包含 `importZip`、`importPreview`、`SkillImport*`、`creatingImportedSkill` 等新建/编辑页导入相关代码。
- `frontend/src/styles.css` 已清理新建/编辑页专用 `.skills-import-preview*` 与 `.skills-compose__file-btn` 样式。
- `frontend/src/admin/pages/AdminSkillComposePage.tsx` 的 `publishSkill()` 现在支持无 `form.id` 的新建场景：先调用保存逻辑创建草稿，成功后继续 `POST /skills/{id}/publish`，发布完成后跳转到对应编辑页。
- 管理端 Skill 新建/编辑页顶部按钮区中，自定义技能的“创建草稿/保存草稿”已降级为 secondary 样式，“发布”保持 primary 样式。
- `frontend/src/admin/pages/AdminSkillComposePage.tsx` 现在将 `versions` 纳入 `SkillEditorTab`，编辑区页签中固定显示“版本管理”；新建页、只读技能、自定义技能编辑页分别显示对应说明或最近三版恢复列表。
- `frontend/src/styles.css` 新增嵌入式版本管理面板样式，让版本摘要、版本行、恢复按钮在页签内按 `鎏金账房` 的边框与紧凑密度展示。
- `frontend/src/admin/pages/AdminSkillsListPage.tsx` 现在给技能列表表格加了专属 `colgroup`，摘要内容放入 `.skills-data-table__summary-text`，操作按钮放入内部 `.skills-data-table__actions`，避免直接改变 `<td>` 的 display。
- `frontend/src/admin/pages/AdminSkillsListPage.tsx` 的可见文案已改为中文；筛选范围去掉 `derived`；行级操作仅对 `TENANT_CUSTOM + EDITABLE` 显示编辑、导出、删除，不再在列表页显示标准技能查看或自定义技能发布。
- `frontend/src/admin/pages/AdminSkillsListPage.tsx` 的自定义技能行级操作现在使用 `openActionMenuId` 控制三点菜单，菜单项为编辑、导出、删除，点击外部或按 Escape 会关闭菜单。
- `frontend/src/styles.css` 现在仅对 `.skills-data-table--catalog` 使用 `table-layout: fixed` 与 `min-width: 0`，`.skills-table-wrap--catalog` 关闭横向滚动；全局 `.skills-data-table__summary` 不再把使用者改成非 table-cell，`td.skills-data-table__actions` 也显式恢复为 table-cell。
- `ChatOrchestratorService.buildInitialMessages(...)` 现在会把运行时日期上下文放在 system prompt 顶部，覆盖非流式和流式聊天路径；`OrchestratorIntegrationTest` 已断言 `/ai/chat` 返回 `runtimeContext.currentDate` 与 `runtimeContext.timezone=Asia/Shanghai`。
- `ChatOrchestratorService.chatStream(...)` 现在会在 SSE `phase` 事件中携带 `modelName`；`frontend/src/assistant/AssistantApp.tsx` 会把它标记到当前助手消息，`chatMessageState.ts` 会在远端历史替换时保留该模型标签。
- `ChatOrchestratorService.buildInitialMessages(...)` 现在会额外注入运行模型上下文：当前服务端模型供应商与模型名称；`buildModelIdentityPromptBlock(...)` 已通过单元测试覆盖 `aliyun-bailian / deepseek-v4-pro` 不应被回答为 Claude 的规则。
- `frontend/src/assistant/chatMessageState.ts` 已抽出流式消息保护逻辑：本地最后一条为助手占位/部分流式内容、远端历史还没有有效助手内容时保留本地；delta 到达时若最后一条不是助手，会补助手气泡继续追加；`chatMessageState.test.ts` 覆盖该竞态。
- `frontend/src/assistant/AssistantApp.tsx` 已为工作台 textarea 增加 `Enter` 提交处理，带组合输入或 `Shift/Alt/Ctrl/Meta+Enter` 时不触发发送；语音结束后会把焦点送回当前 composer 输入框，重新开始语音会用现有输入作为前缀而不是先清空；`frontend/src/shared/useAsrVoiceInput.ts` 新增可选静默自动停止配置，当前仅工作台使用 5000ms。
- `frontend/src/components/ChatMarkdown.tsx` 的 busy 空内容分支不再输出可见文字，改为 `role="status"` 的三个点状态；`frontend/src/styles.css` 已新增错峰缩放动画与 `prefers-reduced-motion` 兼容。
- `docs/specs/FEAT-013-global-avatar-settings.md` 已记录全局头像设置设计，确认智能体头像只能由管理员设置、当前用户头像只能由本人设置、第一版采用上传图片 + 前端裁剪压缩 + 字母兜底，并新增展示覆盖矩阵。
- 现状检查确认 `app_user.avatar_base64` 已存在，管理端用户页已有 256x256 WebP 压缩逻辑；`agent_definition` 当前尚无头像字段，需新增。
- 当前实现已补齐 `agent_definition.avatar_base64` 与 `AgentDefinition` API payload 的 `avatarBase64`；旧前端不传该字段时不会误清空头像（`null` 视为不替换，空串视为清除）。
- 当前实现已补齐 `/auth/me/avatar` 自助更新接口，且仅允许当前登录用户更新本人头像。
- 前台会话与工作台主要头像位已接入统一 `AvatarView`：智能体优先 `agent.avatarBase64`，当前用户优先 `me.avatarBase64`，外部参与人优先 `thread.avatarUrl`。
- 前台左上角 rail 个人头像入口已改为 `AvatarView` 渲染，并重置头像 button 的 padding/box model，避免继承全局 `button` padding 后把图片内容区压缩成窄条。
- 当前用户头像和智能体头像上传入口均已支持“选图后缩放 + 拖动裁剪 + 应用后再保存”，裁剪结果统一输出 256x256 WebP data URL。
- `AGENTS.md`、`README.md` 已加入 `impeccable` 项目级设计治理规则，后续页面工作默认必须先加载根 `PRODUCT.md` / `DESIGN.md` 上下文。
- 根 `PRODUCT.md` 已明确 `/`、`/admin/*`、`/platform/*` 默认全部按 `product` register 处理，不再把项目级上下文限定为单一路由。
- 根 `DESIGN.md` / `DESIGN.json` 已明确 `鎏金账房` 是 assistant、admin、platform 共用的默认产品面设计基线，并记录了 route-level tuning 与例外机制。
- 根 `DESIGN.md` / `DESIGN.json` 已新增 Admin CRUD Lists 规范：真实表格元素不得改 display；搜索、筛选、空态、焦点和 hover 菜单不得撑开页面；默认无横向滚动；筛选为金色文本 tab；工具栏按钮统一；行级编辑/导出/删除等次级动作走不透明三点菜单。
- 已新增 `docs/specs/FEAT-012-project-design-governance.md` 与 `.claw/decisions.md` `DEC-023`，作为本轮设计治理落地的可追溯文档。
- `POST /mcp-servers/1/health` 返回 `status=connected`、`toolCount=43`；`GET /mcp-servers/1/tools` 返回 `cacheStatus=ready`，说明 CloudCC MCP server 与缓存快照可用。
- CloudCC 组织网关解析成功：`orgapi_address=https://szyd.apis.cloudcc.cn/lightningapi`。
- 真实绑定用户 `13800000001/哪吒` 当前换取 CloudCC token 仍返回 `Please check your username and password.`，阻塞点已收敛到用户绑定凭证而非组织级配置。
- `POST /ai/chat` 使用 `sales-agent` 发起 CloudCC 查询时返回 `Aliyun API key is not configured.`；同次响应里 `effectiveToolNames` 已包含 CloudCC 相关工具，说明工具暴露面正常，失败发生在模型调用前。
- 已新增 `PRODUCT.md`、`DESIGN.md`、`DESIGN.json` 与 `docs/specs/FEAT-011-platform-console-visual-refresh.md`，作为平台控制面视觉重构的设计与交付事实源。
- 平台控制面 `/platform/login`、`/platform`、`/platform/skills`、`/platform/tools`、`/platform/audit` 已完成简约金线主题微调，`DESIGN.md` / `DESIGN.json` 已同步到暖白 + 香槟金方向，且 `frontend npm run build` 通过。
- 前台会话工作台 `frontend/src/assistant/AssistantApp.tsx` / `frontend/src/assistant/cici-ui.css` 已完成右侧精简状态机和概览下移衔接历史的布局调整，且 `frontend npm run build` 通过。
- 前台会话工作台左侧 rail 已完成 tooltip 去重、头像/logo 重排和暖白金线风格统一，且 `frontend npm run build` 通过。

## Open Blockers

- `ISSUE-2026-04-08-cloudcc-token-invalid-credential`
- `ISSUE-2026-04-30-chat-smoke-blocked-by-aliyun-api-key`

## Read Next

- `docs/specs/FEAT-015-skill-declarative-api-runtime.md`：看 Skill 内嵌声明式 API 运行时、发布期编译、运行期注入、执行计划、安全策略和 P0 验收标准。
- `docs/specs/FEAT-014-skill-versioning-import-export.md`：看管理端技能版本控制、导入导出、通用 zip 包格式、大模型映射流程和页面原型。
- `docs/specs/FEAT-013-global-avatar-settings.md`：看全局头像设置的设计、权限边界、API 与验收标准。
- `AGENTS.md`、`PRODUCT.md`、`DESIGN.md`：看新的项目级页面设计治理入口与设计事实源。
- `docs/specs/FEAT-012-project-design-governance.md`：看本轮规范的范围、例外机制和后续执行方式。
- `.claw/task-board.md`：看 active task、owner_role 和 handoff。
- `.claw/decisions.md`：看 `DEC-023` 设计治理决策与已有架构决策。
- `.claw/issue-list.md`：如回到项目主线，再看 CloudCC 凭证与聊天入口配置阻塞。
- `docs/specs/FEAT-011-platform-console-visual-refresh.md`：看本轮平台控制面视觉重构范围与验收标准。
- `docs/specs/PROJECT-BASELINE.md`：看 brownfield 基线、关键入口和活跃交付面。

## Maintenance Notes

- 本文件只保留快照，不再回填长历史日志。
- 详细任务推进写入 `.claw/task-board.md`。
- 详细验证命令与结果写入 `.claw/test-report.md`。
- 详细问题根因与状态写入 `.claw/issue-list.md`。
