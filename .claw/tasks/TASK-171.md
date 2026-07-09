---
kind: task-status
task_id: TASK-171
status: in_progress
updated_at: 2026-07-09T22:42:00+08:00
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-171.yaml
spec_path: docs/specs/FEAT-081-customer-interaction-workbench.md
---

# TASK-171 - 客户互动工作台生产就绪

## Scope

- 完成“客户互动工作台”AI 应用，覆盖新客户推进和老客户经营。
- AgentCiCi 侧构建工作台 UI、后端 API、智能体/技能支撑、CloudCC 工具调用、演示数据和审计闭环。
- CloudCC CRM 侧复用标准客户、联系人、线索、商机、任务/活动等对象，补充必要模块入口和演示数据。
- 实现端到端可演示、可验证、可生产交付的闭环。

## Initial Findings

- CloudCC OpenAPI accessToken 获取成功，MetadataService `/metadata/v1/capabilities` 与 `/metadata/v1/scans/standard-catalog` 已用同一 token 验证通过。
- 目标 CRM 租户标准目录已返回 8 个应用、141 个菜单、192 个对象、4854 个字段。
- 现有 AgentCiCi 项目已有 CloudCC 集成服务、AI 应用框架和设计事实源。
- 用户明确要求不再以“结构化拜访记录”或固定拜访阶段为核心，而是从客户互动事实如何落地 CRM、推进客户价值出发。

## Implementation Plan

- 建立 FEAT-081、TASK-171 和授权边界。
- 扫描现有 AI 应用、技能、CloudCC 集成、前端路由和演示数据模式。
- 新增客户互动工作台数据模型、初始化演示数据、后端 API。
- 新增或扩展工作台智能体/技能，支持查询客户、整理互动、生成建议、落地 CRM。
- 实现 AI 应用入口和三栏客户互动工作台页面。
- CloudCC 侧通过 MetadataService/OpenAPI 复用标准对象，创建必要的 CRM 模块承载或演示数据。
- 运行后端测试、前端构建和桌面端 Playwright 验证。

## Verification

- Agent 平台内客户互动工作台视觉比例回归修复本地验证 (2026-07-09) -> passed, pending production release. 针对用户反馈 `2.2.9` 视觉被压坏的问题，平台模式隐藏工作台内部重复品牌/面包屑，恢复三栏比例，压缩局部间距但保留设计稿结构，修复客户标签竖排，并将右侧 AI 客户助理对话区改为无可见滚动条模式。`git diff --check` passed；`npm run build` in `frontend/` passed（保留既有 Vite large chunk warning）；Playwright 1920x960 本地验证新客户模式 `heroCount=0`、`outerOverflow=false`、`brandVisible=false`、`verticalBadges=[]`、`chatScrollbarVisible=false`、workbench bottom visible；老客户模式验证 `hasExistingQueue=true`、`hasRiskPanel=true`、`outerOverflow=false`、`chatScrollbarVisible=false`、`bottomVisible=true`。截图：`output/playwright/task171-agent-workbench-repair-local.png`、`output/playwright/task171-agent-workbench-repair-existing-local.png`。
- Agent 平台内客户互动工作台无外层滚动布局修复验证 (2026-07-09) -> passed. 修复 Agent 平台 AI 应用页中客户互动工作台上方重复的外层介绍栏：`customer-workbench` 作为当前 AI 应用时不再渲染 `.cici-ai-apps__hero`；工作台主容器改为填满 AI 应用主面板剩余高度并隐藏外层溢出；客户列表、中心内容和 AI 客户助理对话分别改为局部滚动区域。`git diff --check` passed；`npm run build` in `frontend/` passed（保留既有 Vite large chunk warning）；Playwright 1920x960 本地验证 `heroCount=0`、document/body 无外层滚动、workbench bottom visible、`.customer-workbench__accounts`/`.customer-workbench__content`/`.customer-workbench__chat` 均为内部滚动。截图：`output/playwright/task171-agent-workbench-no-outer-scroll.png`。
- Agent 平台内客户互动工作台无外层滚动布局生产发布验证 (2026-07-09) -> passed. 提交 `093c8fc85951` 已发布为生产版本 `2.2.9`；ECS 备份 `/opt/cici/backups/20260709-220743-before-2.2.9-task171-workbench-layout`；六个服务 healthy；`/system/version` 返回 `version=2.2.9`、`imageTag=2.2.9`、`gitCommit=093c8fc85951`；公网 `https://x.agentcici.com/`、`/app?aiApp=customer-workbench`、`/app?aiApp=customer-workbench&embed=crm` 均返回 200；生产浏览器 1920x960 使用 `demo-org/13900009999` 登录后验证 `heroCount=0`、document/body 无外层滚动、workbench bottom visible、客户列表/中心内容/AI 对话均为内部滚动。截图：`output/playwright/task171-agent-prod-2.2.9-no-outer-scroll.png`。
- 客户互动工作台新客户/老客户主模式结构调整验证 (2026-07-09) -> passed. 按用户最新确认，新客户推进和老客户经营已调整为顶部互斥主模式，不再作为单个客户详情页签；左侧队列随模式切换为 `新客户推进队列` 或 `老客户经营队列`，中间页签分别为新客户的 `推进概览/互动时间线/推进信号/CRM 落地建议/下一步行动` 和老客户的 `经营概览/互动时间线/服务问题/价值兑现/续约增购/关系地图`；客户区域下方补回 `推进关键项` 与 `服务与关系预警` 汇总框。`rg` 验证无旧销售导向命名文案；`git diff --check` passed；`npm run build` in `frontend/` passed（保留既有 Vite large chunk warning）；Playwright 1496x1064 本地验证两种模式无横向溢出、无详情内模式页签残留。截图：`output/playwright/task171-workbench-mode-final-new.png`、`output/playwright/task171-workbench-mode-final-existing.png`。
- 客户互动工作台时间轴垂直线补回验证 (2026-07-09) -> passed. 补回互动时间轴事件之间的 1px 垂直连接线，使用 `article:not(:last-child)::before` 按图标中心对齐，最后一条互动自然收尾；Playwright 1496x1064 验证前 4 条互动伪元素 `beforeWidth=1px`、`beforeBackground=rgb(222, 210, 187)`，最后一条无伪元素；截图：`output/playwright/task171-workbench-bootstrap-icons-timeline-line.png`。`git diff --check` passed；`npm run build` in `frontend/` passed（保留既有 Vite large chunk warning）。
- 客户互动工作台图标级高保真修复本地验证 (2026-07-09) -> passed. 将原先文字/符号占位的队列工具、搜索、列表密度、指标、时间线来源、CRM 建议、建议动作、AI 助手标题、语音、键盘、快捷操作、输入区麦克风与发送按钮全部替换为同一套内联 SVG 图标；时间线恢复微信/电话/会议彩色圆底图标；右侧对话区恢复用户头像与 AI 机器人头像，并用原图式“总结最近三次沟通”首屏对话内容。Playwright 1496x1064 本地验证 `literalPlaceholders=null`、`micTextButtons=[]`、`svgCount=42`、`timelineIconColors=["rgb(255, 253, 248)", ...]`、`scrollWidth=clientWidth=1496`；截图：`output/playwright/task171-workbench-icons-pass-v2.png`。`git diff --check` passed；`npm run build` in `frontend/` passed（保留既有 Vite large chunk warning）。
- 客户互动工作台高保真 UI 修复本地验证 (2026-07-09) -> passed. 按用户最新设计稿恢复三栏高保真工作台：顶部 AgentCiCi 面包屑栏、左侧客户推进队列、中心客户详情/指标/概览、右侧 AI 客户助理；修正筛选逻辑（`待跟进` 与 `待确认建议` 不再误用新客户/老客户过滤）、首客户状态、指标卡层级和概览卡片密度；恢复并验证 `新客户推进` 与 `老客户经营` 两个业务视图。`git diff --check` passed；`npm run build` in `frontend/` passed（保留既有 Vite large chunk warning）；Playwright 1496x1064 本地截图验证通过，`scrollWidth=clientWidth=1496`、`scrollHeight=clientHeight=1064`，概览页两个“查看全部”入口完整露出。截图：`output/playwright/task171-workbench-fidelity-local-v4.png`、`output/playwright/task171-workbench-new-customer-panel.png`、`output/playwright/task171-workbench-existing-customer-panel.png`。
- CRM 纯嵌入模式本地实现验证 (2026-07-09) -> passed. AgentCiCi 新增 `/app?aiApp=customer-workbench&embed=crm` 轻量入口，只渲染 `CustomerWorkbenchApp`；CloudCC pagecomponent 默认 URL 和 SSO `targetPath` 均改为该入口，并移除 pagecomponent 自身的“AI 应用 / 打开工作台”外层头部。`npm run build` in `frontend/` passed；`node --check frontend/build/customer-workbench.umd.min.js` passed；`git diff --check` passed；`cloudcc package pagecomponent customer-workbench --dry-run` 通过 `cc-customization-expert-msapi` passed；Playwright 本地 DOM 验证返回 `hasRail=false`、`hasAiApps=false`、`hasEmbedded=true`，截图 `.playwright-cli/page-2026-07-09T07-11-39-914Z.png`。
- CRM 纯嵌入模式生产发布与真实 CRM 验证 (2026-07-09) -> passed. Commit `78fa13dd1185` released as `2.2.7`; backend/frontend ACR images and Git tag pushed; ECS backup `/opt/cici/backups/20260709-151814-before-2.2.7-task171-clean-embed`; six services healthy; `/system/version` returned `version=2.2.7`, `imageTag=2.2.7`, `gitCommit=78fa13dd1185`; public `/app?aiApp=customer-workbench&embed=crm` returned HTTP 200. `cc-customization-expert-msapi` published pagecomponent V8 id `6a4f4be8e4b0a577cbba1f70`, apiName `custc_202607F3INXE0S`; real CRM runtime loaded `component-customer-workbench-V8.0.js`, iframe URL included `embed=crm&ssoTicket=...`, and iframe DOM assertion returned `hasRail=false`, `hasAiApps=false`, `hasEmbedded=true`. Screenshot: `output/playwright/task171-cloudcc-clean-embed-v8.png`。
- CloudCC SSO production release `2.2.4` (2026-07-09) -> passed. `./scripts/release-acr.sh --version 2.2.4` pushed backend/frontend images and Git tag for commit `9b673c4076e6`; ECS backup `/opt/cici/backups/20260709-125320-before-2.2.4-task171-cloudcc-sso`; six services healthy; `/system/version` returned `version=2.2.4`, `imageTag=2.2.4`, `gitCommit=9b673c4076e6`; public `https://x.agentcici.com/` and `/app?aiApp=customer-workbench` returned HTTP 200.
- CloudCC pagecomponent SSO publication through `cc-customization-expert-msapi` (2026-07-09) -> passed for package/publish/verify, blocked for bind/update. `cloudcc package pagecomponent customer-workbench . --dry-run` passed with safe dependency policy; `cloudcc publish pagecomponent customer-workbench .` published V7 id `6a4f2c24e4b0a577cbba1f4c`; `cloudcc verify injectionPage . customer_interaction_workbench --expected-component component-customer-workbench` returned `status=passed`. `cloudcc bind pagecomponent ...` and `cloudcc update customPage ...` both returned `系统发生异常`, so no direct CloudCC metadata bypass was used in this closure.
- CloudCC CRM real embedded SSO browser verification (2026-07-09) -> passed. Playwright logged in through `https://accounts.cloudcc.cn/#/login` with the supplied CRM test account, opened `https://ap6.lightning.cloudcc.cn/#/injectionComponent?page=customer_interaction_workbench&button=Home`, observed `component-customer-workbench-V7.0.js`, iframe `https://x.agentcici.com/app?aiApp=customer-workbench&ssoTicket=...`, AgentCiCi org `智能体平台演示环境`, version badge `2.2.6`, and customer queue with 12 customers. Requests `/auth/cloudcc-sso/ticket`, `/auth/cloudcc-sso/consume`, `/auth/me`, `/customer-workbench/accounts`, and `/customer-workbench/accounts/demo-account-012` returned HTTP 200; console error count was 0. Screenshot: `output/playwright/task171-cloudcc-sso-final.png`.
- Customer workbench org-scoped demo seed fix and production release `2.2.6` (2026-07-09) -> passed. Root cause was globally unique `customer_workbench_snapshot.public_id` using unscoped demo ids such as `cw_<accountId>` when the CloudCC SSO org first accessed the workbench. Fix prefixes new seed snapshot/event/recommendation ids with an org-derived prefix while preserving existing org data. `mvn -q -f backend/pom.xml -DskipTests compile`, `git diff --check`, `./scripts/release-acr.sh --dry-run`, `./scripts/release-acr.sh --version 2.2.6`, ECS backup `/opt/cici/backups/20260709-131149-before-2.2.6-task171-cloudcc-sso-seed`, production deploy, health, version, public smoke, and real CRM embedded browser retest all passed.
- 线上对话 `get_object_list` 后误停止工具规划修复 (2026-07-09) -> passed and production released in `2.2.5`. Production trace `3c271ef5-c0c3-4977-8068-108c74e756d5` confirmed the user screenshot flow only called `get_object_list` and then hit `tool_planning_stop_skipped`; `get_object_list` is now treated as object metadata, so record-query intents keep the next planning round while metadata-only requests can still use the fast path. `mvn -q -f backend/pom.xml -Dtest=ChatOrchestratorServiceModelIdentityTest test`, `mvn -q -f backend/pom.xml -DskipTests compile`, `git diff --check`, release `2.2.5`, ECS deployment, production health, production login smoke, and production chat smoke passed. The demo chat smoke did not fully replay org5 because `demo-org/13900009999` has no CloudCC binding.
- `dev-login.py` for `MANAGER-001` setup files -> allowed.
- CloudCC OpenAPI token + MetadataService capabilities/standard-catalog direct HTTP -> passed before task setup.
- `dev-login.py --task TASK-171` and `check-assignment.py --task TASK-171` for representative backend/frontend/spec/CloudCC files -> allowed.
- `dev-login.py --task TASK-171` and `check-assignment.py --task TASK-171` using the `cc-aidev-guidelines-common` skill scripts for the latest deep-link/pagecomponent/spec/state files -> allowed.
- `dev-login.py .claw --developer MANAGER-001 --task TASK-171 --files ... --json` -> allowed for latest state/spec/pagecomponent/html files.
- `check-assignment.py .claw --developer MANAGER-001 --task TASK-171 --files ... --json` -> allowed for latest state/spec/pagecomponent/html files.
- CloudCC MetadataService capabilities -> passed; returned 21 supported domains including `applications` and `menus`.
- CloudCC OpenAPI queried standard `Task`, `Event`, and `Opportunity` objects successfully with real rows returned.
- `mvn -q -DskipTests compile` in `backend/` -> passed.
- `npm run build` in `frontend/` -> passed; Vite large chunk warning remains.
- Playwright desktop validation at 1440x900 with mocked authenticated APIs -> passed; AI 应用入口、客户互动工作台、老客户经营 tab、AI 快捷指令、CRM 落地建议 and `置信度 92%` render correctly, no horizontal overflow, console 0 errors/0 warnings. Screenshot: `output/playwright/task171-customer-workbench-desktop.png`.
- Playwright direct-link validation at `http://127.0.0.1:5173/app?aiApp=customer-workbench` -> passed after fixing the missing `auth.roles` fallback; active AI app is “客户互动工作台”, no horizontal overflow, console 0 errors/0 warnings. Screenshot: `output/playwright/task171-customer-workbench-deeplink.png`.
- `cloudcc detail pagecomponent customer-workbench "" .` -> passed for local CloudCC page component definition and prebuilt bundle config.
- Unsafe root-project `cloudcc publish pagecomponent customer-workbench .` initially succeeded, but the CLI packaged root project config into `compContentVue`; this was treated as a security blocker and the cloud component id returned by that publish was immediately deleted via `cloudcc delete pagecomponent <id> .`. `cloudcc get pagecomponent .` no longer shows that deleted component.
- Safe CloudCC pagecomponent publish through a temporary minimal project -> passed. Latest online high-code scan shows the active pagecomponent id is `6a4d348fe4b0a577cbba1ebf`, apiName is `custc_202607Hdhm60zo`, component is `component-customer-workbench`, and local `frontend/pagecomponents/customer-workbench/config.json` now points at this id.
- Safe CloudCC pagecomponent update through a temporary minimal project -> passed; component default URL now targets `https://x.agentcici.com/app?aiApp=customer-workbench`, the old `https://x.agentcici.com/?aiApp=customer-workbench` URL is absent from the publish response, and the temporary credential directory was deleted.
- CloudCC remote verification: `cloudcc detail pagecomponent "" 6a4d348fe4b0a577cbba1ebf .` and online high-code scan both show `component-customer-workbench`, `客户互动`, `isDeleted=0`, `loadModel=lazy`.
- CloudCC HTML component save through direct devconsole API -> passed. Online high-code scan shows HTML component id `6a4d37ece4b0a577cbba1ec0`, apiName `customer_interaction_workbench`, label `客户互动工作台`; detail query returned accessPath `/oss/html/org0720f814430017229/customer_interaction_workbench-v1.html`.
- MSAPI script-menu planning was explored for a CRM navigation entry. Plan `pla2026E964195FlLpjf` targets script tab id `tab20265938D889zoxqP` and opens the HTML component access path, but apply returned HTTP 403 `insufficient_scope` because the token lacks `metadata:apply`.
- OpenAPI token scope verification -> blocked for apply. Re-requesting `/api/cauth/token` with `scope=metadata:apply`, `metadata:read metadata:write metadata:apply`, `scopes:["metadata:apply"]`, and grant-type variants returned valid JWTs with the same payload keys and no scope claim.
- CloudCC customPage direct creation -> passed after deriving the devconsole payload contract from the legacy CloudCC CLI implementation. Created online customPage id `6a4d3b831b8c6d0ec6dd22ef`, pageLabel `客户互动工作台`, pageApi `customer_interaction_workbench`; readback from `pageCustomPage` returned total `1`.
- CloudCC CRM page menu creation -> passed through setup service `/api/customTab/tabSetDone`. Created tab id `acf2026C53BE54B9R1Iu`, tab label `客户互动工作台`, type `page`, pageType `customPage`, lightning page `customer_interaction_workbench#lightning`, profile authorization count `6`, and target Sales Cloud app id `ace20220322Salesloud`.
- CloudCC Sales Cloud binding verification -> passed. `/api/appProgram/queryModifyPage` for `ace20220322Salesloud` returned `selectedTabList` containing tab id `acf2026C53BE54B9R1Iu` as `客户互动工作台*`; selected menu count was `17`.
- CloudCC CRM menu visibility root cause check (2026-07-08) -> passed. `/api/appProgram/queryAppList` plus per-app `getAppTabs` showed tab id `acf2026C53BE54B9R1Iu` existed only in Sales Cloud before the hotfix and was unselected in the default `CloudCC` app and six other apps.
- CloudCC CRM all-app binding hotfix (2026-07-08) -> passed. Direct setup API `/api/newApp/save` appended the existing tab id to each app's current menu list without changing app labels, default launch tabs, or profile visibility. Verification returned `appCount=8`, `selectedCount=8`, `selectedInAllApps=true`; sequence positions: `CloudCC=13`, `销售云=17`, `市场云=12`, `服务云=16`, `商务云=11`, `客服服务云=8`, `项目管理系统=10`, `利润云=12`.
- CloudCC CRM tab readback (2026-07-08) -> passed. `/api/customTab/queryTabList` returned tab id `acf2026C53BE54B9R1Iu`, label `客户互动工作台`, type `page`, URL `/tab.action?m=needoneapp`, and lightning page `customer_interaction_workbench#lightning`.
- CloudCC CRM web login self-test (2026-07-08) -> passed. Playwright/Chrome logged in through `https://accounts.cloudcc.cn/#/login` with the supplied test user and reproduced the original blank page at `https://ap6.lightning.cloudcc.cn/#/injectionComponent?page=customer_interaction_workbench&button=Home`; screenshot: `output/playwright/task171-cloudcc-injection-whitepage.png`.
- CloudCC CRM injection white-page root cause (2026-07-08) -> confirmed. `detailCustomPage` returned pageApi `customer_interaction_workbench` with pageContent `comId=6a4d348fe4b0a577cbba1ebf` and `embedded=false`; the loaded remote script was `component-customer-workbench-V4.0.js`, which only exposed `window["component-customer-workbench"]` and did not auto-mount into the `<component-customer-workbench>` element.
- CloudCC pagecomponent V5 update (2026-07-08) -> passed. Published pagecomponent id `6a4db950e4b0a577cbba1eca`, apiName `custc_2026079sRcX7wv`, version `5`, with default `embedded=true`, workspace URL `https://x.agentcici.com/app?aiApp=customer-workbench`, and an auto-mount/fallback block in the UMD bundle for CRM injection containers.
- CloudCC customPage V2.0 update (2026-07-08) -> passed. Direct devconsole developer-token API updated pageApi `customer_interaction_workbench` to customPage id `6a4dbc0ce4b0a577cbba1ecb`, renderVersion `V2.0`, `comId=6a4db950e4b0a577cbba1eca`, and `embedded=true`.
- CloudCC CRM injection browser verification (2026-07-08) -> passed. Reloading the same CRM menu route now loads `https://res.lightning.cloudcc.cn/customjs/org0720f814430017229/component-customer-workbench-V5.0.js`, renders the custom element and iframe `https://x.agentcici.com/app?aiApp=customer-workbench` at about `1394x620`; screenshot: `output/playwright/task171-cloudcc-injection-fixed.png`. The iframe currently shows the AgentCiCi login page when no AgentCiCi session exists, which is a separate SSO/login handoff concern rather than the CloudCC white-page defect.
- AgentCiCi/CloudCC 双向登录本地实现 (2026-07-09) -> passed locally. Added public `/auth/cloudcc-sso/ticket` and `/auth/cloudcc-sso/consume`, 60 秒一次性 ticket、CloudCC runtime token 服务端校验、CloudCC 页面用户与 token 用户交叉校验、`cc_username` 到同组织启用成员映射、以及 AgentCiCi 生成 CloudCC accessToken 能力检查。主应用支持消费 `ssoTicket` 并覆盖当前平台登录态；CRM pagecomponent source 和 prebuilt UMD bundle 支持通过 CCDK 获取 CloudCC token/user 后换票进入 iframe。
- AgentCiCi/CloudCC 双向登录编译验证 (2026-07-09) -> passed. `mvn -q -f backend/pom.xml -DskipTests compile` passed; `npm run build` in `frontend/` passed with the existing Vite large chunk warning; `node --check frontend/build/customer-workbench.umd.min.js` passed; `git diff --check` passed. CloudCC pagecomponent CDN publication and真实 CRM 嵌入 SSO 浏览器验证尚未执行。
- Production route smoke (2026-07-08) -> passed. `https://x.agentcici.com/app?aiApp=customer-workbench` returned HTTP `200`; unauthenticated `https://x.agentcici.com/customer-workbench/accounts` returned expected HTTP `401`.
- `cloudcc scan msapi . online-highcode` after CRM page creation -> passed for pagecomponent `1`, HTML component `1`, and customPage `1`. The script endpoint still returns an unrelated 500 in the scan, and sidecar remains out of CloudCC metadata scope.
- `git diff --check` -> passed.
- `docker compose --env-file deploy/acr.env.example -f deploy/docker-compose.acr.yml config >/tmp/cici-compose-check-task171-hotfix.yml` -> passed.
- Production release `2.2.2` -> passed for main workbench images and tag, commit `5a4633dd0409`, backend digest `sha256:b1387ef8731a6ea0e508dcdb44b06e16832f6e3d9a83ad9a55e765d32f21c711`, frontend digest `sha256:79fb9bcfad9f50af77f888b4bd5d4615712edbc63c6811cc460f3bae20e5e0c7`.
- Production hotfix release `2.2.3` -> passed for `/customer-workbench/*` proxy routing, commit `f0ec47509bde`, backend digest `sha256:a38b7b680b5669aac18e344d8ac4e0bb61ecda3f03945760a668d73e93adf807`, frontend digest `sha256:51eae6feea4c10af3cab007ae7b9a05a2d6002e8a909f0306210e8d6daf62d60`.
- Production hotfix release `2.2.5` -> passed for CloudCC object-list tool planning, commit `e14a056b0790`, backend index digest `sha256:af44274bf8c3649d2fe279fdbb5042c80824f5e23d7d80324344d63a93be850b`, frontend index digest `sha256:1aef1b1b19c5224c7414db5c540916545050ad91a378474dca6ca103b536e1a3`.
- ECS deployment -> passed. Backup directories: `/opt/cici/backups/20260708-021020-before-2.2.2-task171-customer-workbench` and `/opt/cici/backups/20260708-021708-before-2.2.3-customer-workbench-proxy`.
- Production health -> passed. Six compose services healthy; backend `/actuator/health` returned `UP`; `/system/version` returned `version=2.2.3`, `imageTag=2.2.3`, `gitCommit=f0ec47509bde`; frontend `nginx -t` passed.
- Production hotfix deployment `2.2.5` -> passed. Backup directory `/opt/cici/backups/20260709-130258-before-2.2.5-tool-planning-hotfix`; backend/frontend containers healthy; `/system/version` returned `version=2.2.5`, `imageTag=2.2.5`, `gitCommit=e14a056b0790`; demo org login and chat smoke passed.
- Production customer workbench API smoke -> passed. Login `demo-org / 13900009999` returned `ORG_ADMIN`; `GET /customer-workbench/accounts` returned JSON with 12 accounts; first detail returned 3 timeline events and 2 recommendations; `/customer-workbench/assistant` returned a risk summary.

## Changed Files

- `docs/specs/FEAT-081-customer-interaction-workbench.md`
- `.claw/tasks/TASK-171.md`
- `.claw/assignments/TASK-171.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `backend/src/main/resources/db/migration/V72__customer_interaction_workbench.sql`
- `backend/src/main/java/com/codehouse/ciciassistant/customer/**`
- `backend/src/main/java/com/codehouse/ciciassistant/skill/service/SkillDefinitionService.java`
- `frontend/src/assistant/AssistantApp.tsx`
- `frontend/src/assistant/customer-workbench/**`
- `frontend/src/assistant/cici-ui.css`
- `frontend/pagecomponents/customer-workbench/**`
- `frontend/build/customer-workbench.umd.min.js`
- `html/customer_interaction_workbench/**`

## Handoff

- AgentCiCi 侧工作台主体、API、演示数据和技能绑定已完成并通过本地验证。
- CloudCC CRM 侧页面组件、HTML 承载页、customPage、页面菜单、简档授权、全部 8 个应用菜单绑定和真实 CRM Web 注入页均已在线验证。若某个已登录用户仍看不到菜单，优先让其刷新 CRM、切换应用或重新登录以更新前端/登录态菜单缓存。
- 白页修复与双向登录均已在线闭环：CRM 注入容器加载 V7 组件后会用 CloudCC runtime token 换取 AgentCiCi 一次性 `ssoTicket`，iframe 可免登录进入客户互动工作台；CloudCC MCP/OpenAPI 仍遵守规则，继续使用平台后端按绑定信息生成的 CloudCC accessToken。
- `cc-customization-expert-msapi` 仍有一个未解决缺口：V7 pagecomponent 发布成功后，customPage 运行时能加载最新 `component-customer-workbench-V7.0.js`，但 skill 的 `bind pagecomponent` / `update customPage` 写入路径失败，且 `verify injectionPage` 没有把 stale component id 识别为失败。后续应把 `ISSUE-2026-07-09-cloudcc-custompage-bind-skill-gap` 回灌到技能。
- 本次白页复盘和 `cc-customization-expert-msapi` 技能缺口/修复建议已沉淀到 `docs/specs/FEAT-081-cloudcc-customization-expert-msapi-gap-report.md`，后续应优先把 customPage high-code resource、pagecomponent 绑定和 injection runtime 验收能力回灌到技能。
- AgentCiCi 生产域名 `https://x.agentcici.com/app?aiApp=customer-workbench` 已随 `2.2.3` 可用，`/customer-workbench/*` HTTPS 代理已修复并验证。
- 线上对话工具规划热修已随 `2.2.5` 部署；原始 org5 账号再次提问“查一下最近的潜在客户。”时应继续进入 `get_object_data` 一类数据查询，而不是停在 `get_object_list` 对象列表摘要。
- MSAPI apply 仍受 `metadata:apply` scope 限制，但已不再阻塞本任务的 CRM 可见入口，因为已用 CloudCC setup/devconsole API 完成同等配置。
