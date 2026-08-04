---
kind: test-report
version: 3
updated_at: 2026-08-04T15:18:04Z
updated_by: MANAGER-001
status: active
last_run_at: 2026-08-04T15:18:04Z
last_run_status: passed
---

# Test Report

## TASK-265 - DEV Autopilot 研发交付评审 Tool 生产闭环

- `formal-bindings`：生产数据库回读确认 query/create/review 三个 Tool 均 enabled，Skill `semattice-project-delivery-management` 为 always-on，产品经理 SERVICE Principal 以 `PRIMARY_OWNER` 委托执行。
- `delivery-e2e`：正式任务 `019fcc18-756f-7782-a9e7-bf34e9c94670` 完成领取、设计 v1 提交、产品经理驳回、设计 v2 提交与批准、40%/90% 进度、1.5h 工时、阻塞上报/解除、commit/test_report 产物、完成申请与批准；最终 `已完成 / 100% / revision 13`。
- `state-gates`：设计批准前进度上报返回 `DESIGN_REQUIRED`；存在开放阻塞时完成申请返回 `OPEN_BLOCKERS_EXIST`。
- `identity-boundaries`：哪吒休息态投影无法使用开发者 CLI；产品经理凭据使用开发者 CLI 返回 403。
- `post-release-smoke`：完成审批后开发者 CLI 仍可回读任务最终状态，AgentCiCi、DEV Autopilot 与 Semattice 生产健康检查通过。

## TASK-255 - 应用未登录态自动跳转 SSO（合并验证）

- `frontend-focused`：`npm --prefix frontend test -- --run src/assistant/oidcAutoRedirect.test.ts` 通过（1 file / 5 tests）；普通 guest 触发跳转，OIDC/CloudCC 回调票据、已有会话、登录提交和重复尝试均不触发。
- `frontend-build`：`npm --prefix frontend run build` 通过；仅保留既有 bundle 大小提示。
- `diff-check`：`git diff --check` 通过。

## TASK-252 - 公司切换会话隔离（发布前）

- `frontend-focused`：`npm test -- --run src/assistant/workbenchSessions.test.ts src/assistant/AssistantApp.test.ts` 通过（2 files / 4 tests）；缓存键测试确认同一工作台 API session 在不同公司形成不同浏览器内存键。
- `frontend-build`：`npm run build` 通过（TypeScript + Vite）；仅保留既有 bundle 大小提示。
- `isolation-contract`：认证 `companyId` 切换会递增作用域版本、同步清空公司级内存状态；会话/消息、工作台、知识库、智能体、技能、快捷指令与监控的异步响应在作用域不一致时不回写，工作台流式回调也会丢弃。服务器 API session ID 保持不变，未删除或迁移任何历史会话。
- `production-2.8.43`：Git tag/commit 为 `2.8.43 / 45b942c06b86`；backend/frontend ACR index digest 为 `sha256:9fcfa8f2c72a5cb80ea6f5cdc68f7dd3a384bb590aed5fbbecb3c5a576e14610` / `sha256:8ad594eea01883e1e87901158c58bc3423d49bcb65738c2d65cf2f505f24d2f5`。发布前备份 `/opt/cici/backups/20260804-213816-before-2.8.43-company-switch-isolation` 的 env、PostgreSQL、KB、Qdrant 均非空；仅重建 backend/frontend，六容器 healthy，backend health=UP、版本接口为 `2.8.43 / 45b942c06b86`、Nginx 配置通过、线上工件含公司缓存键标记、`https://x.agentcici.com/`=200、匿名 `/auth/me`=401。受权用户 A→B→A 实际界面复核待完成，未伪造登录会话。
- `cache-and-realtime-hardening`：截图显示浏览器仍在执行 `2.8.42`，生产容器则为 `2.8.44`，确认为旧入口缓存。工作台初始消息/运行态已按公司键初始化；会话 SSE、轮询、延迟审批与语音回调均在作用域失效后静默退出。定向 Vitest 通过（2 files / 5 tests），生产构建通过；仅保留既有 bundle-size 提示。
- `production-2.8.45`：Git tag/commit 为 `2.8.45 / 435ee0af6e2d`；backend/frontend ACR index digest 为 `sha256:6f5c077947c8d2e51f7b6549affea0764166f7ab0d3aa876c4600b1f5d0c3a5b` / `sha256:856c1df5b9e521ea56ce98e86e8c725d8d88c4e83d670e5111d9b84abfb434bd`。发布前备份 `/opt/cici/backups/20260804-225342-before-2.8.45-tenant-isolation` 四项均非空；仅重建 backend/frontend，六容器 healthy，health=UP、版本正确、实际 SSL Nginx `-t` 通过。`/app` 明确返回 `no-store`，当前哈希 JS 返回 immutable，旧 JS 返回 404，根路径=200、匿名 `/auth/me`=401。真实受权 A→B→A 页面回归待用户刷新后复核。

## TASK-252 - CloudCC CRM orgId 契约（发布前）

- `external-contract`：受权参数按 CloudCC 文档字段 `username/safetyMark/clientId/secretKey/orgId/grant_type=password` 调用已返回 HTTP 200、`result=true` 且有 accessToken；未输出、存储或写入 token/SecretKey。
- `backend-focused`：`mvn -q -f backend/pom.xml -Dmaven.repo.local=backend/.m2 -Dtest=CloudccAccessTokenServiceTest test` 通过（3 tests）；覆盖当前用户 session 校验、并发 token 合并，以及 Token JSON 使用 `orgId` 且不含旧 `companyId`。
- `backend-compile`：`mvn -q -f backend/pom.xml -Dmaven.repo.local=backend/.m2 -DskipTests compile` 通过。
- `frontend`：`npm --prefix frontend test -- --run src/assistant/AssistantApp.test.ts` 与 `npm --prefix frontend run build` 通过；仅保留既有 bundle-size 提示。
- `production-2.8.44`：Git tag/commit 为 `2.8.44 / 4690e58cc154`；backend/frontend ACR index digest 为 `sha256:28fe55de36010179b92a4203eabca6998030e9fbefc40f0da660cad5bf9a6b68` / `sha256:0c73ece9d1c2846bd2d616323bdf633f49643fefbfa52e54b8caee3b8afd7996`。发布前备份 `/opt/cici/backups/20260804-220929-before-2.8.44-cloudcc-orgid` 的 env、PostgreSQL、KB、Qdrant 均非空；仅重建 backend/frontend，六容器 healthy，health=UP、版本正确、Nginx 通过、`x.agentcici.com`=200、匿名 `/auth/me`=401。V104=true，CloudCC 集成 5/6 已有 `orgId`；未配置的香港大学保留为未配置状态，未伪造凭据或连通结果。

## TASK-265 - DEV Autopilot 研发交付评审 Tool（发布前）

- `backend-focused`：`SematticeProjectDeliveryToolServiceTest`、`SematticeProjectDeliveryWriteToolServiceTest`、`SematticeProjectDeliveryReviewToolServiceTest`、`ToolOrchestratorServiceTest`、`SkillResolverServiceTest` 全部通过；覆盖 6 个已发布对象、已决提交不再误列待评审、产品经理 SERVICE read/create/update OACT、稳定幂等键、禁止身份/令牌/目标覆写、Tool 编排及 Skill 显式绑定。
- `backend-package/static`：`mvn -q -f backend/pom.xml -DskipTests package` 与 `git diff --check` 通过。
- `state-validation-limit`：全仓状态 validator 仍包含早于本任务的 hot-index、旧任务状态/时间格式和历史规格 frontmatter 债务；本任务保持既有边界，未将历史债务误报为本次实现失败。
- `production-pending`：V103、正式 Tool/Skill 回读及真实设计/验收评审闭环将在 2.8.42 发布后补录。

## TASK-252 - Keycloak 邀请开通闭环修复（发布前）

- `identity-gate`：MANAGER-001 的 SSH 持钥、Git 身份、`TASK-252/main` 与实现、迁移、测试、规格、状态文件范围均由 `dev-login.py` / `check-assignment.py` 验证为 `allowed`。
- `backend-focused`：`mvn -q -Dmaven.repo.local=.m2 -Dtest=KeycloakIdentityProvisioningServiceTest,PrincipalIdentityGovernanceIntegrationTest test` 通过；覆盖新建用户、失效 remote `sub` 重建并重绑、已存在待激活 User 的邮件重发、同名但 account 属性冲突拒绝，以及已激活用户不发送重置邮件。
- `member-governance`：`AdminUserServiceTest` 通过，覆盖已停用成员的重复邀请不会恢复成员状态、不会触发 Keycloak 账户处理或发送凭据设置邮件。
- `backend-compile/static`：`mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` 与 `git diff --check` 通过。
- `fresh-postgresql-v102`：本机 PostgreSQL 15 临时库从 V1 正向迁移至 V102（98 项）通过；`PrincipalIdentityGovernanceIntegrationTest` 验证新增 HUMAN Principal、legacy Keycloak mirror 与更新同一 `account_external_identity.id` 后的 subject 重绑。测试库和临时登录角色已删除，未接触业务库或生产库。
- `full-integration-limit`：默认完整 Spring 鉴权测试仍依赖未配置的 `127.0.0.1:5432/agentcici_test`，启动阶段连接超时；已停止该环境重试，不将其误报为本次代码失败。发布前将以生产 Flyway 与受权邀请路径完成实际验收。
- `production-2.8.41`：Git commit/tag 为 `3320ed77515d / 2.8.41`；backend/frontend ACR manifest 已 inspect。发布前备份 `/opt/cici/backups/20260804-113909-before-2.8.41-invitation-lifecycle` 的环境、PostgreSQL、KB、Qdrant 均非空；仅 backend/frontend 重建，六容器 healthy，backend health `UP`、版本接口为 `2.8.41 / 3320ed77515d`、Flyway V102=true、Nginx 校验通过。`https://x.agentcici.com/` 为 200，匿名 `/auth/me` 与缺 Bearer 的 service-token exchange 为预期 401。Realm SMTP 已脱敏回读为 SSL/465，未输出秘密。

## TASK-264 - 研发身份花名与新增开发者生产验收

- `identity-authority`：AgentCiCi 权威库事务回读为 Oliver / 大乔 / 悟空 / 后羿，四名 Principal 均 active；产品总监继续绑定全局用户 `18611892001`，三名 SERVICE 的 PRIMARY owner 均为 Oliver。
- `machine-provisioning`：通过既有受治理管理 API 创建后羿 SERVICE `2678bbfb-a234-4912-bfef-47d912ce9e34`，public ID `S2026XS877MF3`，client `dev-autopilot-developer-houyi`；一次性 secret 原子写入 `/opt/devautopilot/secrets/developer-houyi.env`，保持 `root:root 0600`，未输出到终端、日志或 Git。
- `approval-and-projection`：独立审批 `9e5783ea-7713-462f-8388-24b763eca4a0` 由不同于申请人的组织管理员批准；四名 Principal 均经短时 OACT 同步 Semattice，后羿绑定现有开发者角色与研发交付部 primary membership。
- `console-api`：使用真实短时控制台 Session 调用 Semattice members/overview，精确返回 Oliver、大乔、悟空、后羿以及 4 members / 3 roles / 1 organization / 5 objects / 42 fields。
- `cli-e2e`：悟空和后羿各自机器凭据执行 DEV Autopilot `tasks list --human` 成功；大乔产品经理凭据返回退出码 3、`FORBIDDEN`。公网 DEV Autopilot health 为 HTTP 200、`mode=integrated`。
- `state-validation`：TASK-264 新增文件与写入范围有效，`git diff --check` 通过；全仓状态 validator 仍因早于本任务的 hot-index 超长、旧任务状态/时间格式和历史规格 frontmatter 债务退出 1，本任务未越界修复无关历史。

## TASK-263 - 显式 Tool/Skill 与 SERVICE 执行生产验收

- `focused/backend`：`ChatOrchestratorServiceModelIdentityTest`、Agent SERVICE 授权、Semattice 读写 Tool、Tool 编排、OACT、Skill Resolver 等聚焦测试通过；编译、test-compile、package 与 `git diff --check` 通过。
- `migration`：独立 PostgreSQL 16 从 V1 正向迁移至 V101，共 97 项迁移成功；身份治理集成测试通过。
- `full-suite-diagnostic`：325 项测试中 2 项既有非本任务失败（并发 workspace create 返回 500/200 而非 200/409；旧 Skill governance fixture 预期 200 实得 401），0 error、3 skipped；TASK-263 聚焦与迁移回归均通过，未虚报全量绿色。
- `state-validation`：TASK-263/FEAT-155 与 assignment 无新增 finding；全仓校验仍因既有 hot-index 超长、历史时间格式、旧完成任务仍位于 Active Tasks 和旧规格状态/frontmatter 债务退出 1，本任务未越界改写无关历史记录。
- `explicit-bindings`：生产 API 回读产品经理 Agent 的 2 个 Tool、1 个 always-on Skill 和 SERVICE 执行主体；未输出 client secret、JWT 或 OACT。
- `query-e2e`：对“现在有哪些项目在执行”真实调用查询 Tool，返回 4 个项目、1 个执行中项目、2 个活跃任务和 8.0 小时；Trace 完成且 AgentCiCi 委托审计指向产品经理 SERVICE。
- `write-e2e`：未确认消息由大模型生成完整草案，Trace 工具数 0；精确确认后工具数 1，创建 `DAS-941C43CF`。Semattice `runtime.record.create` actor 为 `742daca1-ce58-49cc-9e53-530444ba1c47`，记录 owner 为“DEV Autopilot 产品经理”。
- `release/public`：最终版本 `2.8.40 / f4011a8a3b79`；backend/frontend healthy，状态服务容器 ID 不变，health `UP`、Nginx 有效；AgentCiCi 根路径、DEV Autopilot 和 Semattice health 均为 HTTP 200。

## TASK-262 - DEV Autopilot 受治理机器身份生产验收

- `backend-focused`：机器主体管理服务定向测试与发布基线随 `2.8.38` 通过；生产 backend/frontend 均为 `2.8.38` 且 healthy，Flyway 主体治理表已可回读。
- `identity-readback`：产品总监全局 mobile 精确为 `18611892001`，account/member、OWNER/ACTIVE 与两台 SERVICE 的 PRIMARY owner 一致；两台 SERVICE 均为 ACTIVE，public ID 与 client ID 稳定。
- `lifecycle-e2e`：审批 `f1591286-71bb-49ed-b874-80a7c7640fa9` 下执行开发者 Semattice 投影暂停/恢复与 AgentCiCi 主体暂停/恢复；两次暂停后的 CLI 均失败，两次恢复后的 `tasks get` 均成功。
- `credential-rotation`：生产轮换开发者 client secret；旧 secret 无法取得 Keycloak token，新 secret 可取得 token、交换 OACT 并读取任务；`/opt/devautopilot/secrets/developer.env` 保持 `root:root 0600`，未输出 secret/token。
- `negative-authorization`：开发者创建项目与读取主体目录均为 HTTP 403；永久撤销能力由定向测试覆盖，未对当前生产开发者执行不可逆操作。
- `edge-regression`：发现 frontend 只用基础 Compose 重建后未监听 443；恢复 `docker-compose.acr.ssl.yml` 后 80/443 均监听，版本化 Nginx 配置 `nginx -t` 通过，公网 `/devautopilot/` 与 `/devautopilot/api/health` 均为 HTTP 200，OACT JWKS 为 200。
- `state-validator`：TASK-262/FEAT-154 与完成任务上限未产生错误；仓库级校验仍因大量既有历史规格 frontmatter/status、旧 active/done 卡片和 README/AGENTS 技能块缺失而失败，本任务未扩散修复这些无关历史状态。

## TASK-261 - 创建意图改由大模型语义理解

- `identity/assignment`：MANAGER-001 的 SSH 持钥、Git 身份、任务分支和三份实现/测试路径经 `dev-login.py` 与 `check-assignment.py` 返回 `allowed`。
- `backend-focused`：`mvn -q -Dtest=SematticeProjectDeliveryWriteToolServiceTest,ChatOrchestratorServiceModelIdentityTest test` 通过。创建候选覆盖截图原句和“名称叫”表达，确认消息不进入草案路由；模型指令断言完整语义理解、不得正则抽取、零工具/零写入及精确确认格式。
- `backend-package/static`：`mvn -q -DskipTests package` 与 `git diff --check` 通过。
- `release-2.8.34`：dry-run 与正式发布成功；Git tag/commit 为 `2.8.34 / 84c814b19fe0`。backend/frontend ACR index digest 分别为 `sha256:57fe1b7207af855c42e07607c7e8b1433871b9b53b4d13fef82ae3611c5e3320`、`sha256:b91a5b46391f74d24551fd8d2c667dd4440951486cdc885548f8bcbe94fce5d3`。
- `production-backup/deploy`：发布前备份 `/opt/cici/backups/20260731-223300-before-2.8.34-task261-model-intent` 的 env、PostgreSQL、KB、Qdrant 均非空；只强制重建 backend/frontend，四个状态服务容器 ID 保持不变。六容器健康，health `UP`，版本为 `2.8.34 / 84c814b19fe0`，Nginx 校验通过，近期启动错误为 0。
- `public-smoke`：`https://x.agentcici.com/` 与 `/devautopilot/` 为 200，匿名 `/auth/me` 为预期 401。
- `live-model-understanding`：目标租户受权会话用截图原句“帮我创建一个新项目：AgentCiCi企业级智能体平台”得到完整项目名和精确确认文本；响应模型为 `onekeytoken/auto`。生产 Trace 为 `model_call_count=1`、`tool_call_count=0`、`WAITING_CONFIRMATION`，证明答复来自模型且未执行工具。
- `semattice-zero-write`：使用当前成员短期 OACT 调用线上 `runtime.record.query` 成功，`dev_project` 共 2 条，目标名称 `AgentCiCi企业级智能体平台` 为 0 条；未输出或保存可复用令牌。

## TASK-260 - 研发项目名称自然语言提取修复

- `backend-focused`：`mvn -q -Dtest=SematticeProjectDeliveryWriteToolServiceTest test` 通过，新增断言覆盖“现在创建一个研发项目名称叫：AgentCiCi企业级智能体平台”完整提取及确认指令。
- `backend-compile/static`：`mvn -q -DskipTests compile` 与 `git diff --check` 通过。
- `production-2.8.33`：发布前四类备份非空；backend/frontend 切换为 2.8.33 后六容器健康，health `UP`、版本接口为 `2.8.33 / b680c961b8f6`。线上原句返回完整项目名草案，未执行写入。

## TASK-259 - 研发交付产品经理确认式创建项目、需求与任务

- `identity/assignment`：MANAGER-001 的 SSH 持钥、Git 身份、任务分支和全部实现/测试/状态路径均由 `dev-login.py` 与 `check-assignment.py` 返回 `allowed`。
- `backend-focused`：`mvn -q -Dtest=SematticeProjectDeliveryToolServiceTest,SematticeProjectDeliveryWriteToolServiceTest,ToolOrchestratorServiceTest test` 通过。覆盖无确认仅返回项目草案、精确确认调用 `runtime.record.create`、OACT Bearer 传递、租户参数拒绝和既有原生工具回归。
- `backend-compile/static`：`mvn -q -DskipTests compile` 与 `git diff --check` 通过。
- `state-validation`：本任务前端和任务状态格式已通过任务范围检查；全量 `validate-state.py` 仍报告既有历史规格/任务板格式债务，未改写不在本任务范围内的历史文档。
- `production-2.8.32`：backend/frontend 不可变镜像均已 inspect；发布前备份的 `acr.env`、PostgreSQL、KB、Qdrant 均非空。仅重建 backend/frontend，六容器健康，`/actuator/health` 为 `UP`，版本接口为 `2.8.32 / 2e42ed3ec926`。
- `live-confirmation-gate`：线上受权会话对“现在创建一个棕榈地的研发项目”仅返回草案与精确确认指令；“确认创建项目：棕榈地”创建 `DAS-00B30667`。继续确认创建 `REQ-02F5F798 / 项目启动工作台` 与“搭建项目启动页”均成功。智能体实时查询显示 1 个需求、1 个任务；Semattice 回读确认需求 `project_id`、任务 `project_id` / `requirement_id` 均指向刚创建记录。未输出或保存可复用令牌。
- `agent-configuration`：目标租户 `dev-autopilot-pm` 的持久化系统提示词已更新为确认式创建规则；更新结果为单条命中，不含凭据或令牌。

## TASK-257 - DEV Autopilot 启动器入口

- `identity/assignment`：MANAGER-001 的 SSH 持钥、Git 身份、`codex/TASK-257-dev-autopilot-launcher-entry` 分支以及菜单、测试、规格与状态文件范围均经 `dev-login.py` / `check-assignment.py` 返回 `allowed`。
- `frontend-focused`：`npm test -- --run src/assistant/AssistantApp.test.ts` 通过（1 文件 / 1 test），覆盖“DEV Autopilot / 研发交付 / 研”与固定独立应用 URL。
- `frontend-regression/build`：`npm test` 通过（32 文件 / 199 tests）；`npm run build` 和 `git diff --check` 通过。构建仅输出既有 Vite 大 bundle 提示。
- `production-2.8.28`：主线提交 `f2814efc3a07` 与 annotated tag `2.8.28` 已推送；backend/frontend ACR index digest 分别为 `sha256:18c2794a28050552c0797e48cec507637b4de72d1036f3529f18b16a291ef31a` / `sha256:9f20dd10b58b467c6265aeab0037eb1c272cdf4791139ea81387ea4188d3b0a1`。发布前备份 `/opt/cici/backups/20260731-090547-before-2.8.28-task257` 的 env、PostgreSQL、KB、Qdrant 均非空；只重建 backend/frontend，六容器健康，backend `health=UP`、版本为 `2.8.28 / f2814efc3a07`、Nginx 校验成功。新版前端静态资源含 DEV Autopilot；`https://x.agentcici.com/` 和 `https://x.agentcici.com/devautopilot/` 均为 200，匿名 `/auth/me` 为预期 401。生产浏览器无会话时正确显示统一登录边界；没有伪造用户凭据，已登录菜单的最终视觉点击验收交由正常业务会话完成。

## TASK-254 - company_id 迁移完整性审计与遗留修复

- `identity/assignment`：`check-assignment.py` 与 `dev-login.py` 均返回 `allowed`；MANAGER-001 的 SSH 持钥、Git 身份、任务分支及账单/脚本/状态文件范围已验证。
- `static-audit`：账单 Java、账单测试、E2E、Qdrant smoke 和演示 SQL 的定向扫描未发现 `member.org`、`org_id`、`orgId`、`AGENT_ORG_ID` 或 `ORG_ID` 遗留。全仓非迁移、非测试、非前端运行路径扫描仅剩历史设计文档与 CloudCC 内置技能文档的外部 `orgId` 契约，未作为 AgentCiCi 顶层企业字段修改。
- `syntax-and-compile`：`bash -n scripts/e2e-local-business.sh scripts/verify-qdrant-stack.sh`、Python AST 解析 `scripts/seed-demo-environment.py`、`mvn -q -Dmaven.repo.local=../.m2 -DskipTests compile` 和 `git diff --check` 通过。
- `integration-limit`：`nc -z 127.0.0.1 5432` 返回不可达；为避免将环境连接失败误报为功能结果，未执行会在 Flyway/Hikari 初始化阶段阻塞的 `AdminBillingIntegrationTest`。数据库恢复后应补跑该定向测试，验证组织管理员计费用量页面。
- `production-2.8.25`：主线合并提交 `105cc666a958` 与 Git annotated tag `2.8.25` 已推送；发布前备份 `/opt/cici/backups/20260729-202816-before-2.8.25-task254` 的 `acr.env`、PostgreSQL、KB、Qdrant 均非空。仅重建 backend/frontend，六服务健康，backend `health=UP`、版本为 `2.8.25 / 105cc666a958`、Nginx 校验成功、`https://x.agentcici.com/` 为 200；匿名 `/auth/me` 与 `/admin/billing/overview` 均为预期 401。`onechat.agentcici.com` 仍无法 DNS 解析，作为既有入口风险保留。

## TASK-252 - FEAT-145 统一 Principal 身份与治理

- `identity/assignment`：MANAGER-001 的 SSH 持钥、Git 身份、`feature/TASK-252-unified-principal` 分支和授权范围经 `dev-login.py` 返回 `allowed`。
- `backend-compile`：`mvn -q -DskipTests compile` 通过。
- `fresh-postgresql`：一次性 PostgreSQL 16 容器执行 V1→V96、插入历史账户、再迁移 V97/V98；`UserAccountPublicIdIntegrationTest` 通过。该过程发现并修复了 `user_account → principal` 外键早于 AFTER trigger 检查的问题，最终采用 `DEFERRABLE INITIALLY DEFERRED`，确保新账户与其 HUMAN Principal 在同一事务内一致提交。
- `principal-mapping`：`PrincipalIdentityGovernanceIntegrationTest` 通过，验证新 `user_account` 自动创建 `HUMAN:ACTIVE` Principal，且后续 `account_external_identity` 写入会镜像为 `principal_identity/HUMAN_USER`。
- `oidc-regression`：`KeycloakOidcLoginServiceTest` 3/3 通过。`AuthFlowIntegrationTest` 未通过：共享测试库连接阶段连续超时，Spring Context 未创建，17 项均为同一环境错误；未改写历史迁移、未对共享库执行 repair，使用隔离 PostgreSQL 完成 V98 迁移验证。
- `production-2.8.20`：backend/frontend ACR index digest 分别为 `sha256:9b6493264ce20ab256ad0dd3f2ca0a4fb434d2307e6a0121ecafc08165bb27bc`、`sha256:f51cbde8f06d0ca6933d5a6b747a4b4762c013ec060db6b5fad7790dcd44b429`；发布前备份 `/opt/cici/backups/20260727-225300-before-2.8.20-feat145` 的 env、PostgreSQL、KB、Qdrant 均非空。六服务 healthy，版本为 `2.8.20 / 8db900e4efc2`，Flyway V98 成功，生产 Principal/HUMAN/Identity 计数均为 24；`x.agentcici.com` 为 200、匿名 `/auth/me` 为 401、OIDC start 302 到 Keycloak。`onechat.agentcici.com` DNS 不可解析，未作为当前发布成功依据。Keycloak Realm 尚无 SMTP，自动开户 feature flag 保持 false。
- `machine-oact-contract`：mvn compile、OfficialAccessTokenServiceTest 和 KeycloakOidcLoginServiceTest 通过。后者以本地 JWKS 服务器签发 RS256 client-credentials token，验证交换边界必须解析受信 Keycloak iss、sub 与 azp；SERVICE OACT 覆盖 service principal、PRIMARY owner、client ID 与 scope claim，未输出 bearer 或 secret。
- `machine-provisioning-decoupling`：`KeycloakIdentityProvisioningServiceTest`、`OfficialAccessTokenServiceTest`、`KeycloakOidcLoginServiceTest` 与 backend compile 通过。验证机器开户不要求人类邀请 redirect URI、仅人类开户要求该 URI、机器开关关闭时 `createServiceClient` fail closed。生产只检测到 provisioner Client ID，未检测到其 secret，且 Keycloak host 无 SMTP listener/MTA；因此未启用任一自动开户开关。
- `production-2.8.23`：backend/frontend ACR index digest 分别为 `sha256:82d4278d215ae1ac9adbcace14b9121c7bd9c84c520a2ca17712b560327928b0`、`sha256:0f6e22ebce5cf7e7fb3703ca568152dad4f12e27068b6cf7c70bb83faa3b451a`；发布前备份 `/opt/cici/backups/20260727-233807-before-2.8.23` 的 env、PostgreSQL、KB、Qdrant 均非空。backend/frontend 已按 `--force-recreate --no-deps` 切换，六容器健康，`/system/version` 为 `2.8.23 / a7cd78f88543`，Flyway V98/V99 为 true；`x.agentcici.com` 200、匿名 `/auth/me` 401、交换端点无 Bearer 401 / 伪造 Bearer 403、Keycloak discovery 与 OACT JWKS 200。机器、人类 provisioning 与交换开关继续 fail closed。
- `production-2.8.24`：Compose flag contract 以 `docker compose config` 验证，并发布 backend/frontend index digest `sha256:d2a1dcad568e3167e327e713c977ad2fc83a40cf1348ac4f46be1174a4f0043e` / `sha256:710971cde48ce1fdc59af837331a79d0eb1a42d428a87fa90bace2a496a49ca8`。备份 `/opt/cici/backups/20260727-234415-before-2.8.24` 四项均非空；线上实际运行 `2.8.24 / 58a96d618207`，六容器健康，V98/V99=true，machine-provisioning、service-token-exchange、人类 provisioning 均为 false。匿名边界与交换 401/403 继续通过。
- `machine-provisioning-live`：Keycloak provisioner secret 经轮换后通过受限 stdin 写入部署环境，配置备份 `/opt/cici/backups/20260727-234937-before-machine-provisioning-enable` 存在。backend 重建后健康；以部署环境中的 confidential client 进行 client_credentials 得到有效 300 秒令牌，未打印 token 或 secret。机器开关为 true；人类 provisioning 与服务交换开关均仍为 false。
- `fresh-postgresql-v99`：一次性 PostgreSQL 16 执行 V1 至 V99 后 PrincipalIdentityGovernanceIntegrationTest 通过；确认 service_principal_scope 已创建，新增 HUMAN Principal 和 legacy Keycloak binding mirror 均保持兼容。临时容器 cici-feat145-pg 已删除。
- `semattice-principal-projection`：Semattice Go 全量测试、vet、module verify、Linux amd64 CGO-free 构建和 diff check 通过；不可变 release `/opt/semattice/releases/20260727T151437Z-console` 后服务 active、edge health 为 200、匿名 console API 与 capability invoke 均为预期 401、Nginx 校验通过。
- `exchange-route-correction`：2.8.21 发布后发现生产 Nginx 未代理 /public 前缀，公网 POST 落入前端为 405，而 backend loopback 为预期 401。未越权修改不在 TASK-252 范围内的 Nginx 配置；端点改为既有安全代理前缀 /openapi/v1/official/service-token，前置 token-isolation filter 与 controller 同步更新，compile 与定向安全测试再次通过。
- `production-2.8.22`：backend/frontend ACR index digest 分别为 `sha256:b30ee3a7045668e810f4bc02f8d84097c869d667620f7ae834aef419c9787928`、`sha256:5be82d0316eb08a7104b3d6c79ae07e02c6d52075a7c9c1fb20e9d1c26f8da96`；发布前备份为 `/opt/cici/backups/20260727-232515-before-2.8.22`。六容器 healthy，backend health 为 UP，版本为 `2.8.22 / 645b53f6ea58`，Flyway V98/V99 均为 true；匿名 auth/me 为 401，服务交换端点缺 Bearer 为 401、带任意 Bearer 在 feature flag 关闭时为 403，证明公网代理与 fail-closed 边界生效。Keycloak discovery 继续为 200；未配置 SMTP、OACT signing 或受权 service client，未开启 provisioner/机器交换。
- `frontend`：`npm run build` 通过；仅有既有 Vite 大 chunk 提示。

## TASK-251 - 全局用户公共编号

- `identity/assignment`：MANAGER-001 的 SSH 持钥、Git 身份、`codex/TASK-251-global-user-public-id` 分支和迁移/账户/平台目录/测试/状态代表路径经 `dev-login.py` 返回 `allowed`。
- `backend-focused`：`mvn -q -Dtest=PlatformRegisteredUserServiceTest,UserAccountPublicIdIntegrationTest test` 通过；目录投影测试覆盖 `publicId` 返回，未配置临时数据库时迁移集成用例按设计跳过。
- `fresh-postgresql`：新建后删除 PostgreSQL 16 容器，从 V1 迁移至 V96 后插入 `created_at=2024` 的历史账户，再迁移 V97；断言回填 `U2024[A-Z0-9]{8}`。随后插入 2026 账户，断言触发器生成 `U2026[A-Z0-9]{8}`、两者不重复，直接更新 `public_id` 被不可变触发器拒绝。命令：`USER_ACCOUNT_PUBLIC_ID_MIGRATION_TEST_URL=... mvn -q -Dtest=UserAccountPublicIdIntegrationTest test`。
- `frontend`：`npm test -- --run src/platform/pages/PlatformRegisteredUsersPage.test.ts` 通过（2 tests）；`npm run build` 通过，仅有既有 Vite 大 chunk 提示。
- `browser-desktop`：本地 Vite 页面以 Playwright mock 的平台角色和目录响应在 1280px 桌面态验证。用户行显示“用户编号：U2026A7K29MXQ”，未破坏既有五列表格、搜索、分页或主题，控制台 error 为 0。截图为 `frontend/.playwright-cli/page-2026-07-26T13-00-48-305Z.png`，属于本地未提交验收产物。
- `production-2.8.19`：用户授权发布后，后端/前端 ACR index digest 分别为 `sha256:b9db2c4974aeebb63c38223189bd41eb9f17b8d875faa87de19d4c3ea9303b82`、`sha256:a44c54c6a8d7a0eaea547c3a557712fe881e641a4f1466d6fc98f781dbc7cab7`；发布前备份 `/opt/cici/backups/20260726-220110-before-2.8.19` 的 env、PostgreSQL、KB、Qdrant 均非空。六服务健康，内网 health 为 `UP`、版本为 `2.8.19 / 99d4cc3cb206`、V97 成功，公共编号空值和格式不匹配均为 0；生产 IP/SNI 的 onechat/x HTTPS 均为 200，匿名 `/auth/me` 为 401。无受权平台账号，真实目录展示保留为人工验收项。
- `main-regression`：合入后 `mvn -q -Dtest=PlatformRegisteredUserServiceTest,UserAccountPublicIdIntegrationTest test`、`mvn -q -DskipTests compile`、前端目录定向测试（1 文件 / 3 tests）与 `npm run build` 均通过；前端构建仅有既有 chunk-size warning。

## TASK-250 - MCP HTTP 会话复用修复

- `identity/assignment`：`MANAGER-001` 的 SSH 持钥、Git 身份、`codex/TASK-250-mcp-session-propagation` 分支及 MCP 源码/测试/状态代表路径经 `dev-login.py` 与 `check-assignment.py` 返回 `allowed`。
- `backend-focused`：`mvn -q -Dmaven.repo.local=../.m2 -Dtest=McpClientTest test` 通过（1 test）。本地 HTTP 伪 MCP 服务以 SSE 返回 `initialize` JSON-RPC 结果与 `Mcp-Session-Id`；断言请求顺序为 initialize、initialized 通知、tools/list、tools/call，后三步复用同一会话，所有请求使用 `MCP-Protocol-Version: 2025-03-26`，tools/call 保留 `Bearer user-jwt`。测试也确认 MCP Server 配置中的陈旧会话/协议头不会污染新初始化。
- `backend-compile/static`：`mvn -q -Dmaven.repo.local=../.m2 -DskipTests compile` 与 `git diff --check` 通过。
- `production-limit`：未改动 Semattice、MCP Server 配置或生产环境；真实 `cc-semattic-mcp` 刷新和工具调用留待生产发布后以受权会话复核。
- `main-merge`：用户于 2026-07-25 授权后，`4958bc1 fix(mcp): reuse streamable HTTP session` 已以快进方式推送至 `origin/main`；未构建镜像、未部署生产。

## TASK-248 - 平台注册用户目录展示已加入组织

- `identity/assignment`：MANAGER-001 的 SSH challenge-response、任务分支及代表性后端、前端、测试和状态文件均经 `dev-login.py` 与 `check-assignment.py` 验证为 `allowed`。
- `backend-focused`：`mvn -q -Dtest=PlatformRegisteredUserServiceTest test` 通过；覆盖账户一行、零/一/多组织、按组织 ID 去重、无效成员关系过滤，以及一次批量成员查询。
- `backend-compile`：`mvn -q -DskipTests compile` 通过；成员批量读取使用单条 `join fetch` 查询预加载账户与组织，避免组织名称读取产生 N+1 查询。
- `frontend-focused`：`npm test -- --run src/platform/pages/PlatformRegisteredUsersPage.test.ts` 通过（1 file / 2 tests）；覆盖全量账户目录文案和多组织名称/无组织文案格式化。
- `frontend-build/static`：`npm run build` 与 `git diff --check` 通过；构建仅有既有 Vite chunk-size warning。
- `browser-limit`：本地桌面端访问 `/platform/registered-users` 按预期跳转 `/platform/login`，控制台 error 为 0，并保存登录边界截图 `output/playwright/task248-platform-registered-users-auth-boundary.png`。本会话没有受权平台账号，未使用或伪造凭据，因此真实受保护目录内容和列宽待后续受权会话复核。
- `production-2.8.19`：与 TASK-251 同版发布；后端/前端镜像、备份、六服务健康、Nginx 与公网匿名边界均已复核。无受权平台账号，真实目录中的“已加入组织”列仍待人工复核。
- `main-regression`：合入后的 `mvn -q -Dtest=PlatformRegisteredUserServiceTest test` 与 `npm test -- --run src/platform/pages/PlatformRegisteredUsersPage.test.ts` 均通过（前端 1 文件 / 2 tests）。

## TASK-247 - 平台全量个人用户目录

- `backend-focused`：`mvn -q -Dtest=PlatformRegisteredUserServiceTest test` 通过（2 tests）；覆盖全局账户目录查询、关键词裁剪和分页参数，服务仅调用不联结成员表的 `searchRegisteredAccounts`。
- `frontend-focused`：`npm test -- --run src/platform/pages/PlatformRegisteredUsersPage.test.ts src/platform/pages/platformTenantsShared.test.ts` 通过（2 files / 4 tests）；覆盖全平台目录文案与既有租户路由回归。
- `frontend-build/static`：`npm run build` 与 `git diff --check` 通过；构建仅有既有 Vite chunk-size warning。
- `browser-limit`：本地路由会按预期进入平台登录边界；本会话没有受权平台账号，未使用或伪造凭据，因此真实受保护目录桌面交互待后续复核。
- `main-merge/release`：主线合并提交 `38cb22e3a587`，annotated tag `2.8.15` 已推送。合并后 `mvn -q -Dmaven.repo.local=.m2 test`、前端定向测试、`npm run build`、Compose 配置和 `git diff --check` 均通过。
- `production-2.8.15`：backend/frontend ACR index digest 分别为 `sha256:8e4fc950102a0c1173c8e97c545358b28533d5fea0c98a0aca533ee7c1ffd81d`、`sha256:7e0bf4f0ed12ecd644630ead048953a5428395e32da9abdd1ddd73a55c2ff080`。备份 `/opt/cici/backups/20260724-222041-before-2.8.15-task247` 的 env、PostgreSQL、KB、Qdrant 均非空；仅重建 backend/frontend，六服务健康，health `UP`、版本 `2.8.15 / 38cb22e3a587`、Nginx 有效；`agentcici.com`、`agentcici.com/platform/registered-users` 与 `x.agentcici.com` 均 HTTP 200，匿名平台注册用户接口为预期 401。

## TASK-245 - 前台会话内置组织管理入口

- `identity/assignment`：MANAGER-001 的 SSH 身份、TASK-245 分支以及实现、设计、状态文件代表路径均返回 `allowed`；扩展 README、设计方案与 DESIGN.json 范围后已先提交并推送授权变更。
- `frontend-focused`：`npm test -- --run src/admin/adminSession.test.ts src/admin/adminNavigationGuard.test.ts src/theme/theme.test.ts` 通过（3 files / 18 tests），覆盖助手管理员与 OWNER 会话接管、普通成员拒绝及后台导航守卫/主题既有回归。
- `frontend-build/static`：`npm run build` 与 `git diff --check` 通过；帮助中心和官网导航已移除旧的独立后台登录链接；构建仅有既有 Vite 大 chunk 警告。
- `browser-desktop`：1280×900 本地浏览器访问 `/admin/login` 直接重定向到 `/app` 的统一登录界面，控制台 error/warning 为 0。
- `main-merge-frontend`：合并 `origin/main` 后重新执行同一组 18 项定向测试与生产构建，均通过；仅保留既有 Vite 大 chunk 警告。
- `manual-acceptance-limit`：本会话没有真实组织管理员凭据，未伪造“管理后台”菜单的同组织进入、跨组织 `/auth/switch-company`、普通成员无入口/直达拒绝，以及后台返回后助手会话保留的端到端结果；这些为 review 阶段的真实会话验收项。
- `semattice-switch/frontend`：`npm test -- --run src/admin/adminAuthScope.test.ts src/admin/adminSession.test.ts` 通过（2 files / 6 tests）；覆盖产品下拉的 Semattice 端点、固定 fragment 跳转、菜单文案以及 OACT 不写入浏览器存储的静态边界。
- `semattice-switch/backend`：`mvn -q -Dtest=OfficialAccessTokenServiceTest test` 通过；覆盖 RS256 OACT 的 scope 投影、管理员最小 `audit.read` 补充及缺失统一身份绑定时 fail closed。
- `semattice-switch/build`：`npm run build` 通过；仅保留既有 Vite 大 chunk 警告。`git diff --check` 通过。
- `semattice-switch/manual-acceptance-limit`：没有使用或请求真实组织管理员凭据，未伪造 OACT 签发、Semattice 实际进入、普通成员 403 或浏览器返回 AgentCiCi 的端到端结果；这些保留为 review 阶段的真实会话验收项。
- `main-merge/release`：`ac598745e588` 已合并并推送 `main`，annotated tag `2.8.16` 已推送。合并后 OACT 定向测试、后端编译、前端生产构建、Compose 配置与 `git diff --check` 通过；前端仅有既有 Vite chunk-size warning。
- `production-2.8.16`：backend/frontend ACR index digest 分别为 `sha256:1b965955e81130e37f4001ab27bf33299219669f11f310cb0f8f425cafd5fcd8`、`sha256:a179fa0c7376f5849f4d46736e4527d7ec8031328b8d9027ffbc40b06a68f85e`。备份 `/opt/cici/backups/20260725-092810-before-2.8.16-task245` 的 env、PostgreSQL、KB、Qdrant 均非空；仅重建 backend/frontend，六服务健康，health `UP`、版本 `2.8.16 / ac598745e588`、Nginx 有效；`x.agentcici.com` 与 `agentcici.com` 均 HTTP 200，匿名 `/auth/me` 与 `/auth/semattice/console` 均为预期 401。
- `menu-fix/production-2.8.17`：`adminAuthScope.test.ts` 3 项通过，新增断言确保产品弹层使用 `left: 0` 且不再以 `right: 0` 锚定；前端生产构建和 diff 检查通过。backend/frontend ACR index digest 分别为 `sha256:e214c1f8f27f2a08832b35f8b7a17328e8179bb63653ce706009aeabee5f4cbd`、`sha256:17adbe6607c7b04d89201f2a3fd72b75853e0a421d7bd354f2dbb78d49a10f75`。备份 `/opt/cici/backups/20260725-104037-before-2.8.17-task245-menu-fix` 的 env、PostgreSQL、KB、Qdrant 均非空；仅重建 backend/frontend，六服务健康，health `UP`、Nginx 有效、`x.agentcici.com` 为 200，匿名 `/auth/me` 与 `/auth/semattice/console` 均为预期 401。

## TASK-246 - 租户详情路由标识兼容修复

- `identity/assignment`：`dev-login.py .claw --task TASK-246 --branch codex/TASK-246-tenant-detail-route ...` 返回 `allowed`，SSH 身份、分支与四个前端实现/测试文件及任务文档范围均已验证。
- `frontend-focused`：`npm test -- --run src/platform/pages/platformTenantsShared.test.ts` 通过（3/3），覆盖租户目录和开户结果的旧 `orgId` 归一，以及 `undefined`/空标识不生成详情路由。
- `frontend-build`：`npm run build` 通过；仅有既有 Vite chunk-size warning。
- `static`：`git diff --check` 通过。
- `browser-limit`：本任务未使用或伪造平台运营账号；无效参数的请求前阻断由组件实现与路由标识定向测试覆盖，受登录保护的真实页面交互待合并后以受权账号复验。
- `main-merge/release`：`6cee975539e4` 已合并并推送 `main`，annotated tag `2.8.14` 已推送。合并后 `npm test -- --run src/platform/pages/platformTenantsShared.test.ts src/admin/adminSession.test.ts src/admin/adminNavigationGuard.test.ts src/theme/theme.test.ts` 通过（4 files / 21 tests）；`npm run build`、`docker compose --env-file deploy/acr.env.example -f deploy/docker-compose.acr.yml config` 与 `git diff --check` 通过。
- `production-2.8.14`：backend/frontend ACR index digest 分别为 `sha256:25e051c4bfb7f6f843bf595fec2163f3fc2c8790630be43474773c0cd7f06a0d`、`sha256:d118476d5b9967ee214336f115a987ca2b7d980fcdb1df28527bfe30ee41964d`。备份 `/opt/cici/backups/20260724-212057-before-2.8.14-task246` 的 env、PostgreSQL、KB、Qdrant 均非空；仅重建 backend/frontend，六服务 healthy，`/actuator/health` 为 `UP`、`/system/version` 为 `2.8.14 / 6cee975539e4`，Nginx 有效；`agentcici.com`、`agentcici.com/platform/tenants` 和 `x.agentcici.com` 均 HTTP 200。

## TASK-244 - OIDC 统一入口 state 修复

- `identity/assignment`：`dev-login.py .claw --ssh-key ... --developer MANAGER-001 --task TASK-244 --branch codex/TASK-244-oidc-canonical-entrypoint ...` 返回 `allowed`；SSH 私钥持有、Git 身份、分支与三份实现/测试文件范围均已验证。
- `backend-focused`：`mvn -q -Dmaven.repo.local=.m2 -Dtest=KeycloakOidcLoginServiceTest test` 通过（3/3）；覆盖主站跳转至 callback 规范 host、规范/相似/畸形 host 判断，以及 callback state 不匹配仍 fail closed。
- `backend-compile/static`：`mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` 与 `git diff --check` 通过。
- `user-acceptance-limit`：未使用或请求真实用户凭据；须由用户从 `agentcici.com` 发起真实 SSO，验证 Keycloak callback 生成一次性 ticket 并进入应用。
- `production-2.8.13`：`scripts/release-acr.sh --dry-run` 与正式构建/推送通过；backend/frontend ACR index digest 分别为 `sha256:66e929c6aaee94e2ed13aa09a643f6aef2bb44c3e42c256891091d566f11ff0e`、`sha256:c77614e4c6216fc329962f8c23c971b354caeedf69074f999534a4653c3a6591`。备份 `/opt/cici/backups/20260724-201945-before-2.8.13-oidc-canonical-entrypoint` 的 env、PostgreSQL、KB、Qdrant 均非空；仅重建 backend/frontend，六服务健康。后端返回 `2.8.13 / 877337078ea8`，Nginx 配置有效，`x` HTTPS 为 200；`agentcici.com/auth/oidc/login` 已 302 至 `x.agentcici.com/auth/oidc/login`，仅后者设置 `CICI_OIDC_STATE` 并跳转 Keycloak。

## TASK-243 - Keycloak 统一身份与官方应用访问

- `identity/assignment`：`dev-login.py .claw --developer MANAGER-001 --task TASK-243 --branch codex/TASK-243-keycloak-unified-auth ...` 返回 `allowed`；SSH 私钥持有、Git 身份、任务分支和认证/迁移/部署代表路径均已校验。
- `backend-focused`：`mvn -q -Dmaven.repo.local=.m2 -Dtest=OfficialAccessTokenServiceTest test` 通过（2/2）；覆盖 RS256 OACT 的 issuer/audience/公司/租户/scope/membership claims、JWKS 公钥投影以及无 Keycloak 外部身份绑定时 fail closed。
- `backend-compile/static`：`mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` 与 `git diff --check` 通过。
- `frontend`：安装锁定依赖后 `npm run build` 通过；仅保留既有 Vite 大 chunk 警告。
- `tenant-applications-status`：`SematticeProvisioningServiceTest` 通过；覆盖已持久化 `PROVISIONED` binding 的读取和无 binding 时显式 `NOT_PROVISIONED`。前端生产构建通过，页面会在加载时请求受平台角色保护的状态接口，并将 `RESERVED` 渲染为“开通中”。
- `production-2.8.12`：`scripts/release-acr.sh --dry-run --version 2.8.12` 和正式发布成功；backend/frontend ACR index digest 分别为 `sha256:5bd8801e66e93bb8628c2e725f56bb8b1f9d1cda2b98df23dff2dc7fb31e9c4b`、`sha256:3126c5115587ef36e9eb82012a014166a8760877695c31b5e9a90c466d31ccea`。生产备份 `/opt/cici/backups/20260724-194153-before-2.8.12-semattice-status-fix` 的 env、PostgreSQL、KB、Qdrant 文件均非空；backend/frontend 均已切换 `2.8.12`，健康检查 `UP`，`/system/version` 返回 `2.8.12 / 6574f168234e`。真实 binding 读取为 `PROVISIONED|93ff0c87-a626-529e-b8cf-195825df2488`；新状态接口匿名访问为预期 `401`，公网首页为 `200`。
- `shared-environment-limit`：`mvn -q -Dmaven.repo.local=.m2 -Dtest=OfficialAccessTokenServiceTest,AuthFlowIntegrationTest test` 的 17 个 AuthFlow 集成用例未启动，根因是共享测试库已有 Flyway V81 checksum 不一致（已应用 `2112500543`，本地 `379982424`）；TASK-243 未修改 V81、未执行 repair，独立 OACT 单元与编译结果不等同于完整认证集成通过。

## 主线合并验证 - TASK-240 / TASK-241

- `conflict-resolution`：TASK-240 是 TASK-241 的祖先，只合并后者即可包含两项功能；冲突均以主线 `company_id` 契约为准，未恢复 `org_id` 字段或接口。
- `focused-backend`：`AgentPlanExecCanaryServiceTest`、`AgentRuntimeModeRouterTest`、`AgentTaskReflectServiceTest`、`AgentRuntimeOperationsMetricsTest`、`SematticeProvisioningServiceTest`、`InternalHmacVerifierTest`、`TenantContextFilterTest` 与 `ChatOrchestratorServiceModelIdentityTest` 通过。
- `fresh-postgresql`：临时 PostgreSQL 16 从空库完整迁移 V1→V95；`AgentMemoryFlywayMigrationTest` 与 `AgentTaskRuntimeIntegrationTest` 5/5 通过，覆盖公司隔离的 Plan-Exec/Reflect、跨公司拒绝及 V94/V95 身份迁移。
- `environment-limit`：默认共享测试库仍因历史 Flyway V81 checksum 漂移无法启动；未修改 V81 或执行 repair，使用隔离库完成验证。

## Production Release 2.8.9 - TASK-242 / FEAT-135

- `migration`：新建后删除的 PostgreSQL 16 临时库先迁移 V1→V93，插入真实形状的 `agent_access_grant(principal_type='ORG')`，再迁移 V94/V95；断言 principal 为 `COMPANY`、`company_profile.company_size` 存在且无旧字段。
- `application-startup`：同一类全新 V1→V95 数据库以完整 AgentCiCi 应用启动，Hibernate schema validation 通过，`/actuator/health` 返回 `UP`。
- `production`：`2.8.9 / 0194706ffc7b` 后端和前端均运行健康；日志确认 V95 成功，版本与 health 正确。x HTTP 301、x HTTPS 200、生产 IP/SNI onechat HTTPS 200，匿名 `/auth/me` 401；90 秒 backend `ERROR|Exception|Application run failed` 为 0。

## TASK-242 - 顶层租户 `company_id` 统一

- `identity/assignment`：`check-assignment.py .claw --developer MANAGER-001 --task TASK-242 --branch agent/TASK-242-company-id-unification --git-username OwenZheng-Cloud --require-developer-scopes` 返回 `allowed`。
- `backend/package`：`mvn -q -DskipTests package` 通过；包含 `company_id` 全量字段、Company 根实体/成员关系、JWT、平台生命周期和 Semattice reservation binding 的编译验证。
- `frontend/build`：`npm run build` 通过；仅有既有 Vite 大 chunk 警告。
- `fresh-postgresql`：新建后删除的 PostgreSQL 16 临时库执行 V1→V94，`AgentMemoryFlywayMigrationTest`、`TenantContextFilterTest`、`PlatformTenantLifecycleIntegrationTest`、`SematticeProvisioningServiceTest`、`AuthFlowIntegrationTest` 与 `RbacProductionReadinessIntegrationTest` 全部通过。断言 0 个 `org_id` 列、至少 131 个 `company_id` 列、根表为 `company` / `company_member`，并验证只带旧 `org_id` 的 JWT 被 401 fail closed。
- `static`：`git diff --check` 通过；迁移只新增 V94，未修改 V1–V93。临时数据库容器已删除。
- `state-limit`：`validate-state.py .claw` 仍被仓库既有历史状态文件格式、过期 Active Tasks、旧规格状态和值班索引超长阻断；本任务新增 FEAT-135 已使用合法 `verified` 状态及 UTC 时间，不修改无关历史档案。

## TASK-239 - 混合智能体运行时 P5：Trace 运行执行投影与多主题界面

- `identity/assignment`：MANAGER-001 的任务级 SSH 身份、任务分支及后端/前端/主题/状态代表性文件授权检查均返回 `allowed`。
- `backend-focused`：`AgentRunTraceServiceTest`、`AgentTaskReflectServiceTest`、`AgentRuntimeModeRouterTest`、`AgentPlanExecCanaryServiceTest` 与 `ChatOrchestratorServiceModelIdentityTest` 通过；覆盖精确运行关联、同组织回读、最小脱敏步骤证据、空态、P2–P4 与 Chat 回归。
- `fresh-postgresql-integration`：新建后删除 PostgreSQL 16 临时库完整迁移 V1→V92，`AgentTaskRuntimeIntegrationTest` 通过；临时数据库已删除。
- `frontend`：`AdminAgentRunMonitor.test.tsx` 3/3 通过，`npm run build` 通过；仅保留既有 Vite 大 chunk 提示。
- `browser-desktop`：使用受权组织管理员在新建后删除的 V1→V92 最小事实库登录 `/admin/ops`，完成关联 Trace 的运行总览、两步时间线、`gilded` 与 `galaxy` 同构主题、证据展开和“已复制脱敏后的详情内容”状态验收；两个主题均测得 `scrollWidth=1280`、`innerWidth=1280`、`overflow=false`。证据截图为 `output/playwright/task239-gilded-runtime-execution.png`、`task239-galaxy-runtime-execution.png`，均为忽略的本地验收产物。
- `browser-independent-observation`：同一最小库的既有审计日志面板调用 `/ops/audit/logs?limit=80` 返回 500，产生 2 条 console error；Trace 运行执行读取、展开与复制不产生错误。该接口不在 TASK-239 授权修改范围内，未掩盖或归因给 P5。
- `environment-limit`：默认共享 `agentcici_test` 的 Flyway V81 checksum 漂移未修复、未执行 repair；隔离库验证不等同于全量套件通过。

## TASK-238 - 混合智能体运行时 P4：受控 Reflect 与评测门禁

- `identity/assignment`：TASK-238 的任务级 SSH 身份门禁、分支与代表性实现、迁移、测试和治理文件范围检查均返回 `allowed`。
- `backend-focused`：`AgentTaskReflectServiceTest`、`AgentEvaluationAssertionEngineTest`、`AgentRuntimeModeRouterTest`、`AgentPlanExecCanaryServiceTest` 与 `ChatOrchestratorServiceModelIdentityTest` 共 57 项通过。覆盖默认关闭/精确白名单、组织与 Agent 一致、成功计划/步骤、审查轮次、确认阻断、稳定模式/审查/零写入断言及既有 Chat 回归。
- `fresh-postgresql-integration`：新建后删除的 PostgreSQL 16 临时库完整迁移 V1→V92；`AgentTaskRuntimeIntegrationTest` 5/5 通过，确认 V92 审查记录、`REFLECT_GATE` 运行事件与跨组织拒绝。
- `backend-compile/static`：`mvn -q -Dmaven.repo.local=.m2 -DskipTests compile`、`test-compile` 与 `git diff --check` 通过。
- `environment-limit`：默认共享 `agentcici_test` 的既有 Flyway V81 checksum 漂移（数据库 `2112500543`，本地 `379982424`）未修复、未 repair；隔离库验证不等同于全量套件通过。

## TASK-237 - 混合智能体运行时 P3：规则优先模式路由

- `identity/assignment`：TASK-237 的任务级 SSH 身份门禁、分支与代表性实现、测试和治理文件范围检查均返回 `allowed`。
- `backend-focused`：`AgentRuntimeModeRouterTest`、`AgentPlanExecCanaryServiceTest` 与 `ChatOrchestratorServiceModelIdentityTest` 共 48 项通过。覆盖默认关闭和精确白名单回退、Direct/ReAct/Plan-Exec 的稳定原因码、确认续执行保留、敏感意图仅标记确认/风险、P2 未启动回退既有 ReAct，以及聊天模型身份回归。
- `fresh-postgresql-integration`：新建后删除的 PostgreSQL 16 临时库完整迁移 V1→V91；`AgentTaskRuntimeIntegrationTest` 4/4 通过，验证 Spring 完整装配与既有 P1/P2 运行事实。
- `backend-compile/static`：`mvn -q -Dmaven.repo.local=.m2 -DskipTests compile`、`test-compile` 与 `git diff --check` 通过。
- `environment-limit`：默认共享 `agentcici_test` 的既有 Flyway V81 checksum 漂移（数据库 `2112500543`，本地 `379982424`）未修复、未 repair；隔离库验证不等同于全量套件通过。

## TASK-236 - 混合智能体运行时 P2：Chat/OpenAPI 受限灰度

- `identity/assignment`：TASK-236 的任务级 SSH 身份门禁与代表性实现、迁移状态、测试和治理文件范围检查均返回 `allowed`。
- `backend-focused`：`AgentPlanExecCanaryServiceTest` 与 `ChatOrchestratorServiceModelIdentityTest` 合计 44 项通过。覆盖默认关闭不创建运行、精确 Agent 匹配、固定 `RETRIEVE → SYNTHESIZE` 无工具计划、既有 Web 聊天回归。
- `fresh-postgresql-integration`：新建后删除的 PostgreSQL 16 临时库完整迁移 V1→V91；`AgentTaskRuntimeIntegrationTest` 4/4 通过，新增用例确认 P2 canary 的两步任务均成功、运行终态为 `SUCCEEDED` 且存在真实事件。
- `backend-compile/static`：`mvn -q -DskipTests test-compile`、`mvn -q -DskipTests compile` 与 `git diff --check` 通过。
- `environment-limit`：默认共享 `agentcici_test` 仍在应用初始化前因既有 Flyway V81 checksum 漂移失败（数据库 `2112500543`，本地 `379982424`）；本任务未修改历史迁移或执行 repair，隔离库验证不等同于全量套件通过。

## TASK-235 - 混合智能体运行时 P1：计划状态机基础

- `identity/assignment`：MANAGER-001 的任务级身份门禁、分支与实体、服务、迁移、测试、状态文件代表路径授权均返回 `allowed`。
- `backend-compile`：`JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.11/libexec/openjdk.jdk/Contents/Home mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` 通过。
- `fresh-postgresql-integration`：新建后删除的 PostgreSQL 16 临时库从 V1 全量迁移至 V91；`TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/agentcici_task235_test ... -Dtest=AgentTaskRuntimeIntegrationTest test` 通过，3 个测试、0 failure、0 error、0 skipped，覆盖依赖推进/事件、非法计划与过期版本、失效租约恢复。
- `shared-environment-limit`：默认 `agentcici_test` 在应用初始化前因既有 Flyway V81 checksum 不一致失败（数据库 `2112500543`，本地 `379982424`）；本任务未修改 V81、未执行 Flyway repair，隔离库验证不等同于全量套件通过。
- `static`：`git diff --check` 通过；临时数据库已强制断开连接并删除。

## TASK-234 - 发布修订版本号上限调整为365

- `versioning`：`bash scripts/test-release-versioning.sh` 通过，在临时 Git 远端验证 `2.8.364 → 2.8.365`、`2.8.365 → 2.9.1`、`2.12.365 → 3.0.1`，并拒绝 `2.8.366`。
- `dry-run`：`./scripts/release-acr.sh --dry-run --version 2.8.365 --production` 成功生成对应发布计划；`2.8.366` 被校验拒绝且错误提示声明修订段范围为 `1-365`。
- `static`：两个脚本 `bash -n` 与 `git diff --check` 通过；未执行生产发布。

## Production Release 2.8.5 - FEAT-131

- `release`：`scripts/release-acr.sh --dry-run` 与 `--version 2.8.5` 成功；backend/frontend ACR index digest 分别为 `sha256:0936e7b4d0e3040cf907284b7edc41dc891b1091b73d247e1be734e6c5870e30` 与 `sha256:abc3417bcb95f42897abe6ba32a00df7244e20aef3892f9e84875a8c776619ce`，Git annotated tag `2.8.5` 已推送。
- `backup`：线上备份 `/opt/cici/backups/20260723-115248-before-2.8.5-feat131-memory` 的 `acr.env.before-release`、`postgres.dump`、`kb-files.tgz` 与 `qdrant.tgz` 均非空。
- `production`：仅重建 backend/frontend，四个状态服务保持运行；六服务健康。后端 `/system/version` 返回 `2.8.5 / 02d380d10508`，Flyway V85–V90 均成功，Nginx 配置校验通过。
- `smoke`：`x` HTTP 301、`x` HTTPS 200、生产 IP/SNI 的 onechat HTTPS 200，匿名 `/auth/me` 为预期 401；稳定观察窗口 backend error 0、真实 Nginx 5xx 0。未持有受权生产测试账号或 API Key，因此未创建真实主体或调用受保护 OpenAPI 记忆路径。

## TASK-233 - 通用记忆人工管理与生产就绪审计

- `backend-focused`：11 个通用记忆定向回归、后端编译与 `git diff --check` 通过；覆盖可信上下文、受控语义检索、候选审核、OpenAPI 阻塞/流式、Trace/评测状态、两份独立适配契约、撤销/主体删除/过期和跨 Agent 拒绝。生命周期删除同时脱敏证据引用。
- `fresh-flyway`：新建后删除的 PostgreSQL 16 临时库从 V1 成功迁移至 V90，并验证候选与已生效记录各自存在 `agent_id` 归属列。
- `fresh-platform-integration`：另一个新建后删除的 PostgreSQL 16 临时库执行 `PlatformTenantLifecycleIntegrationTest`，6/6 通过。实际写入 API 记忆绑定后，dry-run 计数、脱敏导出、real purge 与残留行校验均通过；绑定通过所属凭据的组织关系处理，不假定其存在 `org_id`。
- `environment-limit`：默认共享测试库的历史 V81 checksum 漂移仍未修复、未执行 repair；全新库验证用于隔离该既有环境问题，未将其表述为全量套件通过。

## TASK-232 - 通用记忆审核 API 与质量门禁

- `backend-focused`：候选审核、可信运行时 Trace、记忆 Flyway 与 `MEMORY_CONTEXT_STATE` 评测断言测试通过；V89 验证 `memory_candidate.agent_id` 存在，重复审核仍安全拒绝。
- `backend-compile/static`：后端编译与 `git diff --check` 通过。
- `adapter-contract`：`GenericExternalMemoryAdapterContractTest` 使用两份独立的通用凭据绑定验证应用、主体类型、命名空间和内部会话不串读；禁用绑定不进入可信记忆作用域。

## TASK-231 - 通用记忆生命周期与组织清理闭环

- `identity/assignment`：MANAGER-001 已通过 TASK-231 身份门禁与 memory/platform/迁移/测试/状态代表路径授权检查。
- `backend-focused`：`mvn -q -Dtest=MemoryLifecycleServiceTest,MemorySemanticRetrievalServiceTest,ExternalMemoryContextServiceTest,AgentMemoryFlywayMigrationTest test` 通过。覆盖主体删除立即撤销并脱敏、向量删除失败仍不可读取、过期清理、legal hold 阻断及既有授权回读边界。
- `fresh-flyway`：新建后删除的 PostgreSQL 16 临时库从 V1 成功迁移至 V88，并断言通用记忆与凭据绑定表存在。
- `backend-compile/static`：`mvn -q -DskipTests compile` 与 `git diff --check` 通过。
- `platform-integration-baseline`：`mvn -q -Dtest=PlatformTenantLifecycleIntegrationTest test` 未进入用例：共享测试库已有 Flyway V81 checksum `2112500543` 与当前文件 `379982424` 不一致，Spring Context 启动即失败。该既有环境基线未被本任务改动；未修复历史迁移或执行 repair。

## TASK-230 - 受认证凭据记忆上下文绑定

- `backend-focused`：绑定测试锁定可信应用、主体类型、身份等级、命名空间和内部会话 ID 都来自服务端；客户端只能提供外部主体标识。空会话、绑定缺失或禁用均不进入记忆作用域；OpenAPI 阻塞/流式和可信作用域回归通过。
- `binding-governance`：受控配置服务验证凭据必须属于当前组织及 Agent，应用代码、主体类型、身份等级和命名空间逐项校验且规范化；重复配置更新同一绑定，禁用保留审计链并令运行时安全降级。读取、配置和禁用 API 与既有 OPENAPI 权限边界一致。
- `memory-isolation`：`ExternalMemoryContextServiceTest` 验证同主体不同应用、scope、时效和敏感级别的过滤；外部运行时只返回 `NORMAL` 记忆，`INTERNAL`/`SENSITIVE` 不会进入提示词。
- `fresh-flyway`：新建后删除的 PostgreSQL 16 临时库从 V1 成功迁移至 V88，并断言凭据绑定表与六张通用记忆表存在。
- `backend-compile/static`：`mvn -q -DskipTests compile` 与 `git diff --check` 通过。

## TASK-229 - 通用可信运行时记忆上下文

- `backend-focused`：可信上下文只在显式作用域内、组织与最终 Agent 同时匹配时组装提示词；作用域关闭后不残留。Trace 元数据只记录注入/数量/截断状态。`ChatOrchestratorServiceModelIdentityTest` 与语义检索回归共同通过。
- `backend-compile/static`：后端编译与 `git diff --check` 通过。

## TASK-228 - 通用记忆受控语义检索

- `backend-focused`：语义检索测试验证向量命中只有在关系库上下文已授权时才返回；未授权命中不会回读记录；邮件与令牌文本在 embedding 前脱敏，索引失败不向调用方传播。候选审核测试验证审核成功会触发最佳努力索引。定向测试与编译通过。
- `fresh-flyway`：新建后删除的 PostgreSQL 16 临时库从 V1 成功迁移至 V87，并验证 `memory_vector_fragment`。

## TASK-226 - 通用主体记忆 Phase 1 核心

- `identity/assignment`：MANAGER-001 的 SSH challenge-response、任务分支 `codex/TASK-226-agent-memory-core` 和 memory/迁移/测试/状态代表路径经 `dev-login.py` 与 `check-assignment.py` 验证为 `allowed`。
- `backend-focused`：`mvn -q -Dtest=ExternalMemoryContextServiceTest,AgentMemoryFlywayMigrationTest test` 通过；覆盖跨 `applicationCode` 主体隔离、`SUBJECT_SHARED`/`CONVERSATION`/`AGENT_PRIVATE`/`DOMAIN_NAMESPACE` scope 过滤、已过期记录排除、只读上下文不隐式创建外部主体，以及提示词预算边界。
- `fresh-flyway`：使用仅由环境变量提供连接信息的新建 PostgreSQL 16 临时库，执行 `AGENT_MEMORY_MIGRATION_TEST_URL=... AGENT_MEMORY_MIGRATION_TEST_USERNAME=... AGENT_MEMORY_MIGRATION_TEST_PASSWORD=... mvn -q -Dtest=AgentMemoryFlywayMigrationTest test`；成功从 V1 迁移至 V85，并断言 `memory_subject`、`memory_record`、`memory_conversation_snapshot` 存在。验证后临时库已删除。为兼容既有 V81 非事务并发索引，测试显式关闭 PostgreSQL transactional lock，与项目集成测试配置一致。
- `backend-compile`：`mvn -q -DskipTests compile` 通过。
- `static`：通用核心、V85 和定向测试未出现外部应用或领域耦合标识；`git diff --check` 通过。尚未接入外部应用、Chat 编排器、向量索引或生产发布。

## TASK-227 - 通用记忆候选、证据与时效治理

- `identity/assignment`：MANAGER-001 的 TASK-227 身份门禁、任务分支与 memory/迁移/测试/状态代表路径通过 `dev-login.py` 和 `check-assignment.py`。
- `backend-focused`：`mvn -q -Dtest=ExternalMemoryContextServiceTest,MemoryCandidateGovernanceServiceTest,AgentMemoryFlywayMigrationTest test` 通过；候选不能以可读取状态提交，显式审核才会创建 `ACTIVE` 记录，重复审核被拒绝。
- `fresh-flyway`：新建且验证后删除的 PostgreSQL 16 临时库成功从 V1 全量迁移至 V86；断言主体、记录、会话快照、候选与证据五张通用记忆表存在。
- `backend-compile/static`：`mvn -q -DskipTests compile` 和 `git diff --check` 通过；未接入外部应用、自动长期写入、向量索引或生产发布。

## FEAT-131 - 通用外部应用智能体记忆平台（设计规格）

- `identity`：`MANAGER-001` 的 SSH challenge-response、Git 身份和本次规格/状态/验证记录路径经技能包 `dev-login.py` 验证为 `allowed`。
- `static`：已核对规格前置元数据、19 个设计章节、范围与交接说明；设计已更正为 Agent CC 面向任意外部应用的通用平台能力，FollowUp 仅为参考接入方。已跟踪文件及新增规格的 `git diff --check` 均通过。`validate-state.py .claw` 仍被仓库既有热状态超长、历史时区格式、终态任务仍在 Active Tasks、旧规格状态枚举和旧任务/assignment 格式等问题阻断；本次新增 FEAT-131 使用校验器要求的 `draft` 状态和 UTC 时间格式。该变更仅新增设计基线与项目状态，没有执行运行时代码、数据库迁移、前端构建、真实渠道或生产验证。

## TASK-225 - 对话技能选择的强制执行上下文与可观测性

- `identity/assignment`：`MANAGER-001` 的 TASK-225 SSH challenge-response、Git 身份、签名指纹和全部实现/测试/状态代表路径经 `dev-login.py` 验证为 `allowed`。
- `backend-focused`：`mvn -q -Dtest=AgentRunTraceServiceTest,SkillPromptAssemblerTest test` 通过；覆盖所选技能只注入自身业务流程与输出契约、其他业务技能不进入提示词，以及 Trace 保存请求/有效技能码、`FORCED` 状态、强制上下文原因和实际激活结果。
- `backend-compile`：`mvn -q -DskipTests compile` 通过。
- `frontend`：`npm test` 28 个文件、187 项断言全部通过；`npm run build` 通过，仅保留既有 Vite 大 chunk 提示。
- `browser/static`：本地桌面浏览器可加载应用且 console error 为 0；当前无组织用户授权会话，未冒充完成受保护的工作台/Trace 交互验收。`git diff --check` 通过。
- `release`：主线 `2f2f1a013ec2` 已合并并推送；`scripts/release-acr.sh --dry-run`、ACR backend/frontend `2.8.4` 构建/推送/inspect 和 Git annotated tag 均成功。backend/frontend index digest 分别为 `sha256:a173a2479309636f27f13fa5a0a2907f3b0893165f94a053c45dc19b50028002` 与 `sha256:0d94dc8d08d771a1297d09eb86f9d85834d68611a38b1a867cef7cd9e734e068`。
- `production`：备份 `/opt/cici/backups/20260722-102713-before-2.8.4-task225-forced-skill-context` 的 env/PostgreSQL/KB/Qdrant 均非空；仅拉取并强制重建 backend/frontend，四个状态服务容器 ID 未变化。六服务健康，health `UP`，`/system/version` 为 `2.8.4 / 2f2f1a013ec2`，Nginx 校验通过；x HTTP 301/HTTPS 200、生产 IP/SNI onechat HTTPS 200，匿名 `/auth/me` 为预期 401，稳定窗口无 backend ERROR。

## TASK-224 - 生产发布构造器注入启动热修

- `production-failure/rollback`：`2.8.2` 已推送且 V84 成功应用，但 `AuditService` 与 `PlatformAuditService` 存在两个未标注构造器，Spring 无法选择注入入口，backend 重启；已立即将 `acr.env` 与 backend/frontend 回滚到健康的 `2.8.1`，六服务 health 正常。
- `backend-focused`：`mvn -q -Dtest=AuditServiceSecurityTest,PlatformAuditServiceTest test` 通过。新增 `AnnotationConfigApplicationContext` 回归，验证两个审计服务均由 Spring 注入 `SecurityRedactionService`。
- `backend-package`：`mvn -q -DskipTests package` 通过。
- `frontend`：热修未改前端；main 前端树 `npm run build` 通过，保留既有 Vite 大 chunk 警告。
- `compose/static`：Compose 配置和 `git diff --check` 通过。
- `release`：`2.8.3` tag 指向 `651bc2294bee`；backend/frontend ACR index digest 分别为 `sha256:382e10658dd3d066e0add5cd98804cab8d48877bd1eec51342ea02a1bb08b46a` 与 `sha256:9dd889eb547d0dac2a2feabe05678fa22c634652e40f4380bd3b2372cdef43b0`。
- `backup/deploy`：备份 `/opt/cici/backups/20260722-095910-before-2.8.3-task224-startup-hotfix` 的 env/PostgreSQL/KB/Qdrant 均非空；仅拉取并重建 backend/frontend，四个状态服务保持运行。
- `production`：六服务均 healthy；`/actuator/health` 为 `UP`，`/system/version` 返回 `2.8.3 / 651bc2294bee`，V84 为 success，Nginx 配置通过。`x` HTTP 301、HTTPS 200；显式生产 IP/SNI 的 onechat HTTPS 200，匿名 `/auth/me` 为预期 401。切换窗口出现 3 条 upstream 未就绪日志，稳定后 backend 无 ERROR/构造器异常。

## TASK-223 - 定时任务周期解析越界修复

- `identity/assignment`：`MANAGER-001` 的 SSH challenge-response、TASK-223 分支和实现/测试/状态代表路径均通过 `dev-login.py` 与 `check-assignment.py`，0 finding。
- `backend-focused`：`mvn -q -Dtest=UserWorkflowServiceTest test` 通过，覆盖“每天 09:00”生成 `0 0 9 * * *` 且计算非空下一次执行时间，以及“每天下午 3点30分”生成 `0 30 15 * * *`。
- `backend-compile`：`mvn -q -DskipTests compile` 通过。
- `static`：`git diff --check` 通过；未执行生产发布或用户会话写入。

## TASK-222 - 本地遗留分支审查与主线整合

- `identity/assignment`：`MANAGER-001` 的 SSH challenge-response 与 TASK-222 代表文件/代码范围均通过 `dev-login.py` 和 `check-assignment.py`。
- `merge`：TASK-160、TASK-203、TASK-204、TASK-210 均在专用整合分支完成合并；冲突保留当前 `main` 时间线，历史 `.claw` 快照未回填。
- `frontend`：`npm test -- AgentBuilderShell.test.ts` 25/25 通过；`npm run build` 通过，保留既有 Vite 大 chunk 警告。
- `backend`：`mvn -Dtest=GlobalExceptionHandlerTest test` 2/2 通过，覆盖 `ResponseStatusException` 的 404/403 状态与消息映射；`MultitenantIsolationIntegrationTest` 主、测试代码编译成功，但启动 Spring 上下文时被共享测试库的 Flyway V81 checksum 不匹配阻断（数据库 `2112500543`，本地 `379982424`），未执行 repair。
- `script/static`：`python3 -m py_compile scripts/seed-demo-environment.py` 与 `git diff --check` 通过。
- `TASK-170 source`：安全规则分支的 `SecurityRedactionServiceTest`、`SafetyGatewayServiceTest`、`SecurityRulesServiceTest`、`AuditServiceSecurityTest`、`PlatformAuditServiceTest`、`ChatOrchestratorServiceModelIdentityTest` 定向测试及 `mvn -q -DskipTests package` 通过；前端生产构建通过。
- `TASK-219 source`：`theme`、`PlatformBillingPage`、`PlatformSkillsPage` 共 20 项前端定向测试与前端生产构建通过。
- `integrated regression`：TASK-170/TASK-219 合并后，同一组 7 个后端测试类共 56 项通过，`mvn -q -DskipTests package` 通过；上述 3 个前端测试文件共 20 项及 `npm run build` 通过。安全迁移由 V71 重编号为 V84，以匹配已到 V83 的主线迁移时间线；`git diff --check` 通过。

## TASK-218 - 厂商模型目录能力边界

- 授权：`MANAGER-001` 的 SSH 持钥、TASK-218 分支和后端、前端、测试、规格、状态文件范围均通过 `dev-login.py` 与 `check-assignment.py`。
- 后端：`mvn -q -DskipTests compile` 通过；全新临时 PostgreSQL 16 从空库成功应用 79 个迁移至 V83，`PlatformModelProviderIntegrationTest` 通过。覆盖 OneKeyToken 检测不回填样例模型、`models/fetch` 返回 `count=0`、空 `models/modelDetails`、`catalogSource=unavailable` 与 `remoteFetchSupported=false`。
- 前端：`npm test -- --run PlatformModelsPage.test.tsx` 2/2 通过，覆盖未开放远程枚举时的明确空态；`npm run build` 成功转换 1,948 个模块，仅保留既有大 chunk 提示。
- 静态检查：`git diff --check` 通过。未执行生产发布或远程凭据调用。

## TASK-221 - 组织管理端全页面主题一致性治理（本地验收）

- `identity/assignment`: `check-assignment.py` 从仓库根目录返回 `allowed`，确认 Admin 工具页、共享主题层和主题契约测试都在 TASK-221 授权范围内。
- `static-audit`: 覆盖 `/admin/*` 路由清单与共享浮层选择器。主题层将共享模态与遮罩、组织/用户弹窗、技能二级页/发布框/行菜单、业务本体工作台、运维与观测、嵌入应用和计费面板映射到当前 `--theme-*`；工具卡片不再存在内联类别渐变或固定色。
- `frontend-focused`: `npm run test -- --run src/theme/theme.test.ts` 通过，1 个测试文件 / 11 项测试；新增契约锁定 Admin 弹窗、折叠行菜单、二级页和工具卡的主题继承。
- `frontend-build`: `npm run build` 通过，转换 1,949 个模块；仅保留既有 Vite 大 chunk 警告。
- `static`: `git diff --check` 通过。
- `browser`: 当前本地 Browser 无可用管理员认证态，无法进入 `/admin/*` 查看当前蓝色主题，未伪造截图或视觉结果。已登录管理员应按 FEAT-126 路由清单复核页面主体、弹窗、轻量菜单和折叠详情。

## TASK-220 - 用户会话工作台浮层与操作面主题收敛（本地验收）

- `identity/assignment`: `dev-login.py` 的 SSH challenge-response 以及 TASK-220 的 `check-assignment.py` 均返回 `allowed`，0 finding。
- `frontend-focused`: `npm run test -- --run src/theme/theme.test.ts` 通过，1 个测试文件 / 9 项测试；新增契约确认快捷指令菜单、弹窗、当前会话项和遮罩只走当前主题 token。
- `frontend-build`: `npm run build` 通过，转换 1,949 个模块；仅保留既有 Vite 大 chunk 警告。
- `static`: `git diff --check` 通过；快捷指令与技能菜单、快捷指令弹窗、输入区操作、会话选中行和会话操作菜单均由 `--theme-*` token 覆盖，蓝色主题不再读取鎏金账房固定颜色。

## TASK-219 - 运营管理端信息架构与独立主题重构（租户应用中心）

- `frontend-focused`: `npm test -- --run src/theme/theme.test.ts src/platform/pages/PlatformBillingPage.test.ts src/platform/pages/PlatformSkillsPage.test.ts` 通过，3 个文件、20 项断言全部通过。
- `frontend-build`: `npm run build` 通过；Vite 保留既有大 chunk 警告，无 TypeScript 错误。
- `identity/assignment`: MANAGER-001 的 SSH challenge-response、GitHub 身份、TASK-219 分支与租户页面、样式、规格、任务状态和测试报告路径均经 `dev-login.py` 验证为 `allowed`。
- `frontend-build`: `npm run build` 通过，转换 1,949 个模块；TypeScript 无错误，仅保留既有 Vite 大 chunk 警告。
- `static`: `git diff --check` 通过。
- `browser`: 使用仅本机的脱敏 fixture 响应进入受保护 `/platform/tenants/org5nszpgj99jaysxv6y`，没有读取或写入生产。Playwright 在 `1920 × 1080`、`crm-blue` 主题下完成全页视觉检查，确认页面只保留租户身份与 AgentCiCi、Semattice 两张应用卡片，正文不存在“保留策略”“组织导出”或“预演与销毁记录”，且无横向溢出；点击 Semattice 开通后成功提示、运行中状态、已开通汇总 1→2 及已开通禁用态均正确。截图是本机临时证据，不纳入版本控制。
- `routing`: 点击 AgentCiCi 卡片进入 `/platform/tenants/org5nszpgj99jaysxv6y/applications/agentcici`，展示“AgentCiCi 应用生命周期”与原有保留、导出、预演、销毁治理；页面无横向溢出，返回按钮指向租户应用页。

## TASK-214 - OneKeyToken 实时凭据检测修复（生产发布）

- 授权：`MANAGER-001` 的本地身份门禁和 TASK-214 文件范围检查通过。
- 后端：`mvn -q -DskipTests compile` 通过；在全新临时 PostgreSQL 16 中运行 `PlatformModelProviderIntegrationTest#onekeyTokenCheckUsesUnsavedDraftCredentialsForLiveChatCompletionsValidation` 通过，覆盖草稿凭据、Chat Completions 契约、401 拒绝、非持久化和不回显密钥。
- 前端：`npm test -- PlatformModelsPage.test.tsx`（1/1）和 `npm run build` 通过；保留既有的大 chunk 警告。
- 编排与发布门禁：`docker compose --env-file deploy/acr.env.example -f deploy/docker-compose.acr.yml config`、`git diff --check`、`./scripts/release-acr.sh --dry-run --production` 均通过；`2.7.12` 已被并发 tag 占用，脚本按规则生成 `2.8.1`。
- 镜像与标签：生产 `2.8.1 / 9bc8510cbede` 已推送。backend index `sha256:2a4526e84b7cff51e2b374c49012e5d8bc9cc4d4aef4e767a25e022aa65e6b0b`；frontend index `sha256:22b8c092ab27d4a51ccb65267758dcba0dd99a8448af1dc94e295cbc0b0f2c82`。
- 生产备份与部署：备份 `/opt/cici/backups/20260721-190903-before-2.8.1-task214-onekeytoken` 的环境文件、PostgreSQL、知识库和 Qdrant 归档均非空；只重建 backend/frontend。数据库、Redis、RabbitMQ、Qdrant 容器 ID 保持不变。
- 生产验收：`/actuator/health` 返回 `UP`，`/system/version` 返回 `2.8.1 / 9bc8510cbede`，Nginx 配置检查通过。首次仅使用基础 Compose 重建时未加载生产机既有 TLS 覆盖，443 映射短暂缺失；已用 `docker-compose.acr.ssl.yml` 与现有证书重新创建前端并恢复。frontend healthy，`x.agentcici.com` 与显式生产 IP 的 `onechat.agentcici.com` HTTPS 均返回 200；`POST /api/platform/models/providers/onekeytoken/check` 在未认证状态返回 401，证明新受保护路由已上线。

## TASK-217 - 智能体定时任务真实创建与链路事实纠偏（已发布）

- `backend`: `mvn -q -DskipTests compile` 通过。
- `backend`: `mvn -q -Dtest=ToolOrchestratorServiceTest,ChatOrchestratorServiceModelIdentityTest,AgentRunTraceServiceTest,AgentWorkflowRuntimeSkillGovernanceTest test` 通过，覆盖当前 Agent 上下文的定时任务工具暴露/分发、缺少周期时不触发模型或工具、原 CRM 调用链兼容、Trace 节点和 Skill 治理。
- `backend-full-diagnostic`: `mvn -q test` 未通过；共享本地测试库的 Flyway V81 checksum 与仓库不一致（数据库 `2112500543`，本地 `379982424`），导致 Spring 集成上下文无法启动。未执行 repair，聚焦回归不受影响。
- `static`: `git diff --check` 通过。
- `release`: `./scripts/release-acr.sh --dry-run` 通过；`2.7.12 / b20261d8b89b` 的 ACR backend/frontend 镜像可 inspect，Git annotated tag 已推送。backend index/amd64 为 `sha256:b2d1e4a053a6edadd6cdcefd481615a89258cd1821e02f3745f74031dd175b23` / `sha256:9b819a1b9949dd98d3db700bd36bacdeeef655be200f42288edb662ae089496b`，frontend 为 `sha256:a3a6ff9734bb3f7da648a2003159289d26b704f6927fd48b06f665b7e205b616` / `sha256:52a0228d143371ac9e6da0570e047d387ac227656af12bdcfbe8cbf644b5ea8b`。
- `production`: 备份 `/opt/cici/backups/20260721-190058-before-2.7.12-task217-runtime-trace` 的 env/PostgreSQL/KB/Qdrant 分别为 1,648 / 3,263,430 / 511,201 / 1,584,517 bytes，均非空。仅重建 backend/frontend，四个状态服务容器 ID 不变；六服务健康，health `UP`，版本 `2.7.12 / b20261d8b89b`，Nginx 有效，`x` HTTPS 200、显式生产-IP onechat HTTPS 200，发布窗口 backend/frontend error 和 Nginx 5xx 均为 0。
- `business-path`: 当前无组织用户授权登录态，未代用户创建测试任务；首次真实用户创建应核验非空 trigger 和 nextFireAt，并在下一次调度后核验 Tavily execution。

## TASK-214 OneKeyToken 实时凭据检测本地验收（2026-07-21）

- `identity/assignment`: MANAGER-001 的 SSH 持钥、签名指纹、GitHub 身份、TASK-214 分支与后端、前端、测试、规格和状态代表文件均经 `dev-login.py`、`check-assignment.py` 验证为 `allowed`，0 finding。
- `backend-focused/fresh-db`: 独立临时 PostgreSQL 16 从空库成功应用 79 个迁移至 V83；`PlatformModelProviderIntegrationTest#onekeyTokenCheckUsesUnsavedDraftCredentialsForLiveChatCompletionsValidation` 通过。用例验证草稿 Key 而非已保存 Key 用于 `POST /v1/chat/completions`、Bearer 鉴权、唯一 `x-request-id`、`onekeytoken/auto`、`stream=false`、401 拒绝与 Key 不回显；测试容器已删除。
- `backend-compile`: `mvn -q -DskipTests compile` 通过。默认共享测试库因既有 Flyway V81 checksum 不一致无法启动，未 repair 或修改共享库，改用上述隔离库作为真实测试证据。
- `frontend`: `npm test -- PlatformModelsPage.test.tsx` 1/1 通过，覆盖检测请求使用修剪后的未保存表单草稿；`npm run build` 成功转换 1,948 个模块。仅保留既有 Vite 大 chunk 提示。
- `browser`: 本地 Vite 与 Playwright CLI 在桌面浏览器打开 `/platform/models`，未认证状态正确重定向到平台登录页并完成可访问性快照；模型配置的认证态由 MockMvc 集成测试覆盖。无移动端范围。
- `static`: `git diff --check` 通过；无 Key、Authorization 值、完整上游响应或可复用凭据进入规格、测试断言、审计或状态文件。

## TASK-215 链路追踪全文查看与复制（2026-07-21）

- `backend`: `mvn -q -Dtest=AgentRunTraceServiceTest test` 通过；新 Trace 将 220 字节点摘要与最多 12,000 字的脱敏管理员详情分离，测试确认密码和手机号不进入可复制文本。
- `frontend`: `npm test` 17 个测试文件、88 项通过；`npm run build` 通过，仅保留既有大 chunk 提示。
- `browser`: 本地 `1280 × 720` 管理员 Trace 页面以受控响应验证默认摘要、原位展开/收起、详情滚动区、复制成功反馈和 keyboard 可访问名称；最终 console error/warning 为 0，未见横向溢出。截图：`.playwright-cli/page-2026-07-21T09-35-01-950Z.png`。
- `release`: `./scripts/release-acr.sh --dry-run` 与 `2.7.11` ACR 镜像构建/inspect 成功；annotated tag `2.7.11` 指向 `281f35b2cb2f` 并已推送。backend index/amd64 为 `sha256:65bf3b101a9ee915fddf656ea5ebe53bc29bf3d27b01504b2321f77f6fce4290` / `sha256:dc156302579d7b35730aadc883bf7fdd7491d87d5cf1d079fd3ad1fc78eeb33f`；frontend 为 `sha256:27c38b70972f9ba1436285ac6eead35fbf3b936facfdf703ca09bba3aa29d902` / `sha256:8e4ce653bb3c251e73be79a6446f79b0d35aa8a36db2d52a65b0a94c1bb7616f`。
- `production`: `/opt/cici/backups/20260721-181143-before-2.7.11-main-integration` 的 env/PostgreSQL/KB/Qdrant 分别为 1,648 / 3,264,738 / 511,201 / 1,584,517 bytes，均非空；backend/frontend 更新至 `2.7.11`，四个状态服务保持 `2.6.12`。六服务 healthy，health `UP`，版本 `2.7.11 / 281f35b2cb2f`，Nginx 有效，x HTTP 301/HTTPS 200，生产-IP-resolved onechat HTTPS 200。
- `production-browser`: 管理路由正确重定向到独立管理员登录页，console error/warning 为 0；当前会话没有管理员凭据，未把受保护 Trace 交互冒充为已在线重复验收。

## TASK-213 通用本体 V1 本地与生产验收（2026-07-17）

- `identity/assignment`: MANAGER-001 SSH 持钥、签名指纹、GitHub 身份、TASK-213 分支及本次 provenance 增量涉及的 18 个源码、V83、测试、规格和状态路径经 `dev-login.py` 与 `check-assignment.py` 校验均返回 `allowed`，验证项包括 developer record、持钥证明和 assignment scope，0 finding。
- `tdd`: 既有发布阻塞修复的 RED/GREEN 证据保持有效。终局 provenance 加固先让前端同一管理员、完全同元数据但 `MANUAL` 的工作区错误命中，并让后端因缺少指纹/来源字段编译失败；最小实现后聚焦测试转绿。数据库随后新增 MANUAL 携带包字段、REFERENCE_PACKAGE 空包 ID、63 位短指纹和 64 位大写指纹反例；旧 CHECK 如预期仅在空包 ID 用例失败，V83 增加非空包 ID 条件后四组反例全部转绿。指纹测试独立读取实际 classpath JSON 原始 bytes 计算 SHA-256，确认摘要、加载结果、安装落库和管理 API 使用同一 64 位小写值。V82 未修改。
- `frontend-full`: `npm test` 为 26 个文件 / 177 项全部通过；`npm run build` 成功转换 1,948 个模块，仅保留既有大 chunk 提示。
- `backend-focused/fresh-db`: 最终全新专用 PostgreSQL 从空库成功应用 79 个迁移至 V83；`flyway_schema_history` 中 V82/V83 均 `success=true`，ontology 表仍为 13 张。`OntologyPersistenceIntegrationTest` 16/16、`OntologyPlatformIntegrationTest` 14/14、`OntologyManagementServiceTest` 6/6、`OntologyReferencePackageServiceTest` 3/3，合计 39/39，0 failure / 0 error / 0 skipped。`mvn -q -DskipTests package` 通过。调试库、旧候选库和最终库均强制删除，`pg_database` 回读 `task213_provenance%` 为 0；未 repair 或复用共享测试库。
- `backend-expanded-final`: 签名提交 `d589ad1` 后另建 `task213_verify_d589ad1`，从零应用到 V83 并运行 10 个相关类：本体持久化 16、校验 9、编译 5、AI 提案 36、语义查询 22、CloudCC 适配 10、本体平台 14、租户生命周期 6、管理服务 6、参考包 3，合计 127/127，0 failure / 0 error / 0 skipped。测试后删库并回读该库计数为 0；同一 HEAD 再次通过前端 177/177、1,948 模块生产构建和后端 package。
- `browser-auth-timing`: 真实浏览器延迟组织资料响应 5 秒，在响应返回前退出；等待旧响应结束后仍位于 `/admin/login`，`cici_admin_token` 为 `null`，旧组织信息未重新出现。
- `browser-unmount-timing`: 真实浏览器延迟创建工作区响应，在 POST 已发出后确认侧栏离开；旧响应结束后仍位于 `/admin/data-quality`，工作区未重新挂载，记录到的 `/admin/ontologies/{id}/**` 后续请求为 0。
- `browser-compile-a11y`: 真实浏览器进入草稿修订 7 的技术预览，实际 POST body 为 `{"expectedRevision":7}`，响应 `sourceDraftRevision=7` 后展示只读契约；6 个工作区 tab 与 3 个技术 tab 的 `aria-controls` 均命中真实面板，非活动面板保留 `hidden` IDREF。1600×1000 截图完成，当前验证会话 console error/warning 为 0。
- `browser-mapping-galaxy`: 在 Galaxy 主题复现“技术预览 → 数据映射 → 删除映射形成脏状态 → AI 提案”：技术预览只读取 mappings（1 次 / catalog 0 次）；首次进入映射页继续读取完整 catalog（累计 mappings 2 次 / catalog 1 次）；删除后切 AI 页请求计数不再增加，页面继续显示“有未保存修改”，生成和应用提案均禁用并给出先保存映射提示。差异只显示 `业务对象“项目”`，不显示 `concept:project`。Galaxy 实际计算 warning 为 `rgb(230, 183, 95)`，1600×1000 的 document/body 横向溢出均为 0，console error/warning 为 0。
- `browser-final-d589ad1`: 全新 1600×1000 会话验证列表、领域向导、三节点两关系画布与检查器；删除映射后跨页签保留脏状态，校验/发布/AI 生成/应用均禁用；侧栏离开取消后仍留在 `/admin/ontology` 且脏状态保留，确认后才离开；技术预览 POST 为 `{"expectedRevision":4}` 且响应 `sourceDraftRevision=4` 被接受，全部 tab `aria-controls` 都命中真实面板；校验绿灯后才启用人工发布，发布弹窗默认焦点为“取消”，版本 1 不可变详情可读。验收会话 console error/warning、document/body 横向溢出均为 0。截图为 `output/playwright/ontology-v1/ontology-final-{list,wizard,workbench,technical,publish-confirm,versions}.png`。
- `static`: `git diff --check`、`jq empty DESIGN.json` 通过；暖色主题 warning `#7a4b00` 达到普通文本 4.5:1 门槛，Galaxy 使用主题 warning `#e6b75f`，在 canvas/surface/muted/strong/warning-soft 五类暗色背景上的对比度依次为 9.84 / 8.98 / 8.14 / 6.78 / 6.40:1。
- `state-validator`: `validate-state.py .claw` 仍因 130 条既有历史规格/任务基线 finding 退出 1；输出中 `TASK-213.md`、`FEAT-118` 与 V83 命中为 0，本轮未越界修复历史状态。
- `independent-review`: 最终安全与规格两路只读复审对 `d589ad1` 均返回 Approved，Critical 0 / Important 0。规格侧仅保留 mounted RouterProvider + deferred Promise 测试债，安全侧仅建议将修改/目录/发布扩展为参数化跨租户 404 测试；两项均为 Minor，不阻塞合并与发布。
- `merge/release`: PR #13 已合并为 `f922b86f1884ec5f7b7e1d97d3d0558202d0180f`；`./scripts/release-acr.sh --dry-run --version 2.7.10 --production` 与正式发布均成功，annotated tag `2.7.10` 已推送。backend index/amd64 digest 为 `sha256:096f480677944eb8e0f263e562155c771f4e72d0bee6731a82a3b162937c3644` / `sha256:cdaeb804cd645afe6fa2498b9f06f14c24b6a4b33d4f8d9a8f538e66e79056d5`；frontend 为 `sha256:0f96d20bdf1727fc8cf6da57c0b49af7f9a8c213a91709fe8183bef7ef66ed3b` / `sha256:4cfae678067c31d9794fe8e1bf5b8739d6b95dfb3fba5aaec8dd921aa3a7a2df`。
- `backup/deploy`: `/opt/cici/backups/20260717-154253-before-2.7.10-task213-ontology` 的 env/PostgreSQL/KB/Qdrant 分别为 1,646 / 3,010,000 / 511,135 / 1,584,517 bytes；Nginx、Compose、状态与 SHA-256 清单也非空且校验通过。仅 pull/force-recreate backend/frontend；database、Redis、RabbitMQ、Qdrant ID 逐项与发布前完全一致并继续运行 `2.6.12`。
- `production-migration/runtime`: 生产 V82/V83 均 `success=true`，checksum 分别为 `-1084439350` / `-147714050`；ontology 表数 13，provenance 列、CHECK 与 `uq_ontology_workspace_org_key` 均正确。六服务 healthy，health `UP`，版本为 `2.7.10 / f922b86f1884 / 2.7.10`，Nginx 配置有效。
- `production-project-delivery`: 真实生产完成对象/字段发现、15/15 映射验证、候选编译、人工发布和重复发布幂等校验；不可变 v1 绑定草稿修订 6，发布详情不回显数据源配置/示例数据。explain 生成 `projects + contains-task` 计划；execute 返回 1 个“语义平台一期”项目与 2 条任务，证据版本/总数为 1/1。另一组织返回 404 `ONTOLOGY_NOT_FOUND`；查询审计包含 `REDACTED` 且不包含过滤值明文。
- `production-cloudcc-boundary`: `customer-operations` 在 `demo-org` 与真实 CRM 演示组织均以有效 package ID/64 位小写指纹安装为草稿；两名密码登录用户当前都不能取得有效 CloudCC 当前用户会话，对象发现各返回一次 `502 DATA_SOURCE_UNAVAILABLE`。草稿保持修订 1、未校验、未发布，失败未修改 CloudCC 或影响 INLINE_SAMPLE/手工建模能力。
- `browser-production`: Playwright CLI 在生产 1600×1000 验证列表、3 节点/2 关系画布、15 条已验证映射、候选 v2 技术预览、线上 v1/来源修订 6 版本历史和全部工作区/技术 tab IDREF；document/body 横向溢出与 console error/warning 均为 0。截图位于 `output/playwright/ontology-prod-2.7.10/ontology-{list,workbench-canvas,workbench-mapping,workbench-technical-json-schema,workbench-technical-graphql,workbench-versions}.png`。
- `stable-window`: 2026-07-17T08:11:52Z 至 08:19:52Z 共 480 秒、17 次 30 秒采样；六容器始终 healthy、restart 0、OOM=false，全部容器 ID不变，health/version 固定，backend 生命周期 `ERROR|Exception` 为 0。Nginx 最终恰好为上述两次预期 CRM discover 502，其他 5xx 为 0；08:20:47Z 最终语义查询仍为 HTTP 200、rows 1、关联任务 2、证据版本/总数 1/1。
- `dev-proxy-followup`: 合并后复核发现被跟踪的生成态 `frontend/vite.config.js` 未随 TypeScript 事实源提交；生产镜像构建时 `tsc -b` 已生成正确配置，线上不受影响。assignment 先在签名提交 `ef50ecc` 中扩展并推送授权，再机械同步生成文件；`npm run build` 转换 1,948 个模块成功，直接 `npm run dev` 请求 `/admin/ontologies` 命中代理并因本机 8080 未启动返回预期 proxy 500，而不是 SPA 200。
- `public/rollback`: `x.agentcici.com` HTTP 301 / HTTPS 200，匿名本体/语义查询返回 401；本机仍无法解析 `onechat.agentcici.com`，显式生产 IP vhost HTTPS 200。应用即时回滚点为 `2.7.9 / c04e992b3840`，V82/V83 可安全保留。

## TASK-201 智能体构建页右栏说明移除与双栏对齐增量验收（2026-07-16）

- `identity/assignment`: MANAGER-001 SSH challenge-response 登录、GitHub 身份、TASK-201 分支与前端源码/测试/样式/规格/状态代表路径均返回 `allowed`。
- `tdd`: 新增“右栏只承载系统提示词且不显示模型治理说明”的布局契约测试；旧实现先因仍包含说明节点与样式按预期失败，移除说明后聚焦测试 22/22 通过。
- `frontend-full`: `npm test` 为 18 个文件 / 110 项全部通过；`npm run build` 成功转换 1,938 个模块，仅保留既有大 chunk 提示；`git diff --check` 通过。
- `browser-local`: 应用内 Browser 在 1600×1000 验证左右编辑列均为 745.5px × 682.5px，top 均为 220.6953125、bottom 均为 903.1953125；系统提示词与左侧发布备注输入底边均为 897.1953125。模型治理说明节点为 0，document/body 横向溢出均为 0，console error/warning 为 0。
- `merge/release`: PR #11 合并为 `c04e992b38407097db448d52ea5c5e8b6473f7fc`；`scripts/release-acr.sh --dry-run` 与正式 `2.7.9` 发布均通过，annotated tag 已推送。backend index/amd64 digest 为 `sha256:420477ea503cb3f1bb6eb357b426d7e139d947427b5ba5cff46d168e02b9a3c5` / `sha256:74b3f03701058f07cace1504e20deaa5101dc16c88d6dd7ea549d24308e07c3a`；frontend 为 `sha256:7f5dddaad2846d83cfb102a4519860ea11dc571d21a0d79d0a83d227f185ae5a` / `sha256:86ba71f4985b2c880fdf90a81ddcfbc13803eab9585f77361df57cd4aad71b2e`。
- `backup/deploy`: `/opt/cici/backups/20260716-161644-before-2.7.9-task201-alignment` 中 env/PostgreSQL/KB/Qdrant 分别为 1,646 / 3,009,740 / 511,135 / 1,584,517 bytes，全部非空。仅 pull/force-recreate backend/frontend；database、Redis、RabbitMQ、Qdrant 容器 ID 与发布前完全一致并继续运行 `2.6.12`。
- `production-runtime`: 六服务 healthy，health `UP`，`/system/version` 返回 `2.7.9 / c04e992b3840`，Nginx 配置有效；发布后窗口 backend ERROR/Exception 0、frontend 精确 5xx 0。
- `browser-production`: 线上 1600×1000 “客户成功” Agent 编辑页显示版本 2.7.9；左右编辑列均为 612.5px × 604px，top/bottom 分别同为 227.1953125 / 831.1953125，系统提示词与发布备注输入底边同为 825.1953125。模型治理说明节点与文案均为 0，document/body 横向溢出均为 0，当前生产 console error/warning 为 0；截图为 `/Users/owenmacbook/.codex/visualizations/2026/07/14/019f5df9-6f52-7dd0-975c-cb6ad90d6d69/agent-builder-production-2.7.9/agent-builder-alignment.png`。
- `public/rollback`: `x.agentcici.com` HTTP 301 / HTTPS 200；本机仍无法解析 `onechat.agentcici.com`，显式生产 IP vhost 为 HTTPS 200。即时应用回滚点为 `2.7.8 / 4814d2b9534d`。
- `scope`: 未改变 `draft.model`、模型路由、API、数据库、主题 token、生命周期页签或移动端范围。

## TASK-212 Skill DAG Phase 1 本地与生产验收（2026-07-16）

- `tdd/backend-focused`: 先以缺失/脏版本引用、current 指针错配、标准边语义、平台 token 越权、运行时治理快照、Skill 版本变更指纹、历史显式/缺失版本、下游当前 KB/移交边界污染、1,001 条影响上限和 V81 重试安全形成红灯，再完成实现；9 个测试类共 22 项通过，0 failure / 0 error / 0 skipped。真实 `SkillResolverService` 与调试 Runtime 均断言 pinned runtime 不包含当前可变 Skill 边界。
- `http-security`: 真实 Spring/MockMvc 和本地 API smoke 均验证匿名 Agent 图 401、组织 token 读取 Agent 图 200、平台 token 读取平台图 200、平台 token 访问 Agent 图 403、组织 token 访问平台图 403；显式 `versionNo=1` 返回 200。示例 Agent 图为 5 节点 / 5 边 / 0 warning。
- `migration/performance`: 独立干净 PostgreSQL 从空库应用 77 个迁移至 V81；Flyway 明确以 `[non-transactional]` 执行。随后在两个索引已存在时重执行迁移 SQL，`DROP INDEX CONCURRENTLY` / `CREATE INDEX CONCURRENTLY` 全部成功，工作流引用与当前绑定索引最终均 `indisvalid=true / indisready=true`。两类影响查询最多读取 1,001 条并只展示 1,000 条，SkillVersion 使用组织内批量加载。
- `frontend`: `npm test -- --run` 为 18 个文件 / 110 项全部通过；`npm run build` 成功，仅保留既有大 chunk 提示。覆盖分层布局、关系详情、空态 warning、缩放适配、Agent/Skill 选择加载写门禁、目标操作进行中选择锁定、异步回写序号校验、请求快照和调试解析链。
- `backend-package`: `mvn -DskipTests package` 通过并生成可执行 JAR。
- `backend-full-diagnostic`: 独立数据库完整 Maven 诊断汇总为 341 项、3 failure / 7 error；失败位于既有 AutoService 平台身份、PlatformBilling 审计夹具、SkillGovernance 鉴权、AdminOrganizationProfile 非空字段、MeetingMinutes 模型配置及连接池耗尽后的 ChatSession/ModelProvider 上下文，不包含 TASK-212 聚焦测试，未作为全绿门禁。
- `browser-local`: 应用内 Browser 在 `1600 x 1000` 验证平台 Skill 影响图与 Agent Builder 的 Agent → Workflow Version → Skill → Skill Version → Tool 关系、`COMPILED_AS` / `PINS_SKILL_VERSION` / `USES_SKILL` 节点详情、缩放控制和调试 Skill 解析链；console error/warning 为 0，页面外层横向溢出为 0。
- `independent-review`: 三轮只读复审发现并推动修复历史显式缺失版本、影响查询与索引、前端正反向选择竞态及 V81 重试问题；最终复核 Critical / Important / Minor 均为 0，`Ready to merge: Yes`。
- `merge/release`: PR #10 合并为 `4814d2b9534d8ba70d560b1a8a9b9a3dbe390717`；`scripts/release-acr.sh --dry-run --version 2.7.8` 与正式发布均通过，Git tag `2.7.8` 已推送。backend index/amd64 digest 为 `sha256:4bbc96d6857236ade2122d98c038d70f15cb0148c852553f472631af93eca38e` / `sha256:f15bde1851cb45ee217147e1ce419a5c4d78c2b2390903f578c025c6c88d13b2`；frontend 为 `sha256:ceff96941ae9402a25cf0a28ec9b7c69a2bb4d4da44c9b6848db2934addc30cf` / `sha256:1ebecff3346837c879c041d7f9559f5ac9526791d82fb08ea18e5fd47f3ce056`。
- `backup/deploy`: `/opt/cici/backups/20260716-011129-before-2.7.8-task212-skill-dag` 中 env/PostgreSQL/KB/Qdrant 分别为 1,646 / 3,007,782 / 511,135 / 1,584,517 bytes，全部非空。仅 pull/force-recreate backend/frontend；database `ce48f99872d8`、Redis `3c3879593463`、RabbitMQ `246a0aa352df`、Qdrant `96bf6c3cad9c` 与发布前 ID 一致。
- `production-runtime`: 六服务 healthy，health `UP`，`/system/version` 返回 `2.7.8 / 4814d2b9534d`；Flyway V81 成功，两条影响索引均 `indisvalid=true / indisready=true`，Nginx 配置有效。8 分钟稳定窗口 backend ERROR 0、frontend 精确 5xx 0。
- `production-api`: 真实生产 API 返回匿名 Agent 图 401、组织 token Agent 图 200、显式 `versionNo=50` 200、平台 token Agent 图 403、组织 token 平台图 403、平台 token 平台图 200；Agent 图为 24 节点 / 32 边 / 3 warning，平台图为 6 节点 / 9 边 / 4 warning，请求时延为 0.16-0.21 秒。
- `browser-production`: 应用内 Browser 在 `1600 x 1000` 验证 Agent Builder 与平台标准技能真实 DAG、缩放和节点详情。两页 `document.scrollWidth == clientWidth == 1600`，平台 main 无横向溢出，console warning/error 为 0；截图为 `output/playwright/task212-prod-agent-skill-dag-2.7.8.png` 与 `output/playwright/task212-prod-platform-skill-dag-2.7.8.png`。
- `public`: `x.agentcici.com` HTTP 301 / HTTPS 200；公共解析器仍无法解析 `onechat.agentcici.com`，与既有 DNS 风险一致，显式生产 IP vhost 为 HTTP 301 / HTTPS 200。
- `gates`: MANAGER-001 SSH 身份门禁、assignment 代表路径、Flyway V81、`git diff --check`、签名提交、PR 合并、生产备份、不可变发布、线上 API/browser smoke 与回滚点记录均完成；应用即时回滚点为 `2.7.7 / e47979167af8`，V81 索引可安全保留。
- `state-validator`: 全仓 `validate-state.py` 仍因 129 条既有历史状态/规格基线退出 1，但输出中 `TASK-212`、`FEAT-117` 与 V81 命中为 0；未在本任务中越界清理旧记录。

## TASK-211 2.7.6 失败回滚与 2.7.7 生产协议验收（2026-07-15）

- `2.7.6-go/no-go`: `2.7.6 / 2055947aae07` 的 SalesA 5 次内部 SSE 均为 133 个 delta、最大 18 字符、唯一 done 且与持久化精确一致；但 OpenAPI streaming 只有 2,342 字，blocking 为 2,383 字，41 个分片边界空格/换行丢失。临时 Key 撤销后返回 401，bindings 精确恢复，随后只重建 backend/frontend 回滚到健康 `2.7.5`，四个状态服务 ID 未变化。
- `openapi-tdd-hotfix`: 旧实现面对“尾随空格 + 纯空白 + 前导换行”片段稳定红灯；`deltaText` 改为 null-to-empty 且转发条件改为非空后转绿。两类聚焦测试 44 项、独立干净数据库 8 类 CRM 测试 135 项、前端 16 文件 89 项、1,936 模块生产构建、Compose、身份/assignment 与 diff 门禁全部通过；独立审查 Critical / Important / Minor 均为 0。
- `merge/release`: PR #7 合并为 `e47979167af8`，签名实现提交为 `eb5e1f7e4dc05f53943094e09289c54cd08d0056`；`scripts/release-acr.sh --dry-run --version 2.7.7` 与真实 release 均成功，Git tag `2.7.7` 已推送。
- `images`: backend index `sha256:315623e0ea90f087cf332acfc5b981efca91d493c814a0b8a2023a7b6433a475`、amd64 `sha256:9c6b10448df2a7f1bda6b37dfdaf09ec2eacc28bd050055afbf6150279af4ddc`；frontend index `sha256:515c760bc654c8e491a8914cf48a37397fe4c3200529b0df972d397e6b3f9f24`、amd64 `sha256:96d176f71a276962ba87be12f788ecf73c3d68009d7a9804077af12fa4a082ab`。
- `backup/deploy`: `/opt/cici/backups/20260715-091243-before-2.7.7-task211-openapi-whitespace` 中 env 1,646、PostgreSQL 2,925,720、KB 511,065、Qdrant 1,584,517 bytes，全部非空。只 pull/force-recreate backend/frontend；database、Redis、RabbitMQ、Qdrant 容器身份逐项与部署前一致。
- `runtime`: 六服务健康，health `UP`，`/system/version` 返回 `2.7.7 / e47979167af8`，Flyway 最新成功版本为 V80，Nginx 配置有效；`x` HTTP 301、HTTPS 200，生产 IP 显式解析的 `onechat` HTTPS 200。
- `salesa-stream`: 5 个 fresh SalesA 会话均为 `run → model → generating`、133 个非空 delta、最大 18 UTF-16 单元、唯一尾部 done；首末 delta 到达跨度依次为 2,398.082 / 2,395.707 / 2,398.149 / 2,395.634 / 2,394.114 ms，证明不是最终突发。每次 SSE 拼接与自身两条持久化消息逐字一致，五次正文仅归一化秒级“数据截止”后哈希唯一。
- `blocking/salesb/isolation`: SalesA blocking 与自身历史逐字一致；fresh SalesB SSE 同为 133 个 delta、最大 18 UTF-16 单元并与自身历史一致，和 SalesA 截止时间归一化后相同。SalesB 读取 SalesA 会话没有返回数据；因既有 `ResponseStatusException` 映射缺口响应为通用 500，已登记独立 issue，不作为数据泄漏或 TASK-211 流式回归。
- `openapi`: fresh 临时 Key 下 blocking 与 streaming 正文均为 2,383 字；streaming 为 3 个脱敏 `agent_thought`、133 个 `message`、最大 18 UTF-16 单元、唯一尾部 `message_end`。仅归一化动态截止时间后，streaming、blocking、各自 OpenAPI history 和内部协议正文完全一致，空格与换行无丢失。
- `cleanup/security`: 临时 Key 已撤销，复用该 Key 返回 401 `agent_api_key_invalid`；初始 ACTIVE Key 数与结束时一致为 0，临时 Key 无 ACTIVE 残留，channels/toolIds/knowledgeBaseIds 与 fresh 初始绑定逐字规范化一致。9 份用户答案及脱敏 thought 通过工具名、原始 JSON、内部字段、疑似 CloudCC ID 和敏感信息扫描。
- `business-depth`: 9 份答案都包含 Top 5 `X1 130 / G5 110 / S2 95 / MP 75 / PA 65`，金额冠军 MP 2,850,000，以及数量/金额贡献、环比、订单/客户覆盖、商机、合同、退货、建议动作、数据覆盖和“订单销售额不等同于财务确认收入”声明。
- `clean-logs`: 在另一个 fresh 133-delta、约 2.43 秒且持久化精确一致的成功会话窗口内，backend ERROR 0、CRM failure 0、异常断连 0、Nginx 精确 5xx 0；窗口含 179 条 Nginx 请求日志。
- `browser-evidence`: 应用内 Browser 恢复后，以 fresh SalesA 登录、fresh 工作台会话和 `CRM 经营分析` Skill 询问“嗯，销量最好的产品有哪些？”。正确的 partial 判据为“`直接结论` 已出现且 composer 仍 disabled”：此时同一 assistant 气泡可见文本 50 字；完成后同一气泡为 2,100 字、增长 2,050 字且 composer enabled。partial/final 截图已固化为权限 `0600` 的安全证据；console error/warning 为 0，html/body/workbench/layout/main/chat-panel/chat-thread 的 `scrollWidth` 均不大于 `clientWidth`。
- `browser-content/security`: 最终可见正文包含直接结论、产品 Top 5、经营诊断、前瞻信号、建议动作、口径与覆盖，Top 5 为 X1 130 / G5 110 / S2 95 / MP 75 / PA 65，金额冠军 MP 2,850,000，并包含收入声明；工具名、`tool_call/tool_result`、原始 JSON、内部字段、凭据和“等待确认”均未出现。最初尝试以气泡 `role=status` 与正文标题同时存在捕获 partial 属于无效探针，因为该 status 只在正文为空时渲染；源码只读复核和 `chatMessageState` 7/7 证明无需前端生产代码修改。
- `governance-gates`: TASK-211 SSH 持钥登录与 9 个变更文件的 assignment 检查均为 allowed，`git diff --check` 通过；将 TASK-211 从 Active Tasks 移入 Completed Tasks 后，全仓 `validate-state.py` 仍因 129 行既有历史状态/规格基线问题退出 1，但不再包含 TASK-211、FEAT-114 或本计划的 finding，未在本任务中越界修复。
- `final-recheck`: 完成前使用新建且验收后删除的独立 PostgreSQL 测试库重新执行 8 类 CRM/流式/OpenAPI 回归，合计 135/135 通过；`chatMessageState` 7/7 通过，TypeScript/Vite 生产构建成功并转换 1,936 个模块。生产六服务继续 healthy，health `UP`、版本 `2.7.7 / e47979167af8`；最终 10 分钟窗口为 backend ERROR 0、CRM failure 0、异常断连 0、frontend 5xx 0。较早宽口径窗口中的 500 均命中已独立登记的不可见会话状态映射问题，不属于 CRM 回答链路。

## TASK-211 CRM 确定性回答真实流式输出本地验收（2026-07-15）

- `tdd-red`: 新增多分片断言后，原实现的单方法回归按预期失败：1 项运行、1 failure，实际 `delta` 分片数为 1。
- `minimal-fix`: 生产代码只把 CRM 确定性分支的 `safeSendDelta(emitter, finalText)` 替换为现有 `safeSendDeltaInChunks(emitter, finalText)`；18 字/18ms 参数、blocking、持久化、格式化器、最终 LLM 和通用模型流式路径均未改变。
- `focused-green`: `ChatOrchestratorServiceModelIdentityTest,AgentOpenApiConversationServiceTest` 共 44 项通过；覆盖多 `delta`、单片上限、精确拼接、唯一尾部 `done`、多 `message`、唯一尾部 `message_end`、精确持久化、脱敏状态和最终 LLM 零调用。
- `crm-clean-db`: 独立干净 PostgreSQL 数据库上的 8 类 CRM 回归共 135 项通过，0 failure / 0 error / 0 skipped；默认共享测试库的既有 Skill v3 checksum 污染未被修改，也未作为绿色证据。
- `frontend`: Vitest 16 个文件、89 项通过；TypeScript/Vite 生产构建成功，转换 1,936 个模块，仅保留既有大 chunk 提示；无前端生产代码变更。
- `backend-full-diagnostic`: 新建数据库的完整后端诊断到达 Surefire 汇总 326 项，重现 5 failure / 2 error，来自既有平台身份夹具、审计字段、客户洞察、模型厂商/模型清单和旧非空字段夹具；随后在 Hikari 重试窗口人工终止，未作为通过门禁。TASK-211 两个测试类没有失败，定向 135 项保持全绿。
- `static/gates`: Compose config、`git diff --check`、TASK-211 身份登录和 assignment 检查均通过；签名实现提交为 `1e7fcc7a6228c19bad193bb46787fb8fb3bd5b2d`。
- `reviews`: 任务级审查同时批准规格符合性和代码质量；整分支最终审查为 `Ready to merge: Yes`，Critical / Important / Minor 均为 0。生产 `2.7.6` 的空白保真失败、回滚、TDD 热修、`2.7.7` 发布、协议验收与应用内 Browser 视觉证据均已完成，详见上节。

## TASK-208 生产发布与真实验收（2026-07-15）

- `ancestry`: 整合提交同时包含 TASK-209 `2.7.2 / ddcda0ef6111`、TASK-208 `2.7.3 / 85b92c2d1f63` 与当前生产 TASK-210 `2.7.4 / 3206fdbc196f` 三条不可变发布线；三次 `git merge-base --is-ancestor` 均通过。
- `content-preservation`: CRM 后端、内置 `crm-business-analysis` Skill、CRM 测试和受控迁移脚本与 TASK-208 `2.7.3` 树一致；完整 `frontend/` 与当前生产 `2.7.4` 树一致，TASK-209 原图登录资产保留。
- `backend-focused`: 8 个 Surefire 报告共 143 项通过，0 failure / 0 error；覆盖路由、五层经营分析、格式化、高阶工具、阻塞/SSE/OpenAPI 防泄漏和 CRM 数据契约。
- `frontend-full`: 合并后的锁文件执行 `npm ci` 后，Vitest 16 个文件、89 项通过；TypeScript/Vite 生产构建成功，共转换 1,936 个模块，仅保留既有大 chunk 提示。
- `identity/assignment`: MANAGER-001 SSH 持钥、GitHub 身份、TASK-208 当前集成分支与状态文件代表路径均为 `allowed`；TASK-209 前端与设计事实源由其已完成 assignment 覆盖。
- `release-guard`: `2.7.3` 从未部署；并发 TASK-210 以 `2.7.4` 上线并有意排除 TASK-208。最终分支将 `2.7.4` 作为生产父线并正向撤销其 TASK-208 revert，下一次只能发布新的不可变版本 `2.7.5`。
- `release`: PR #4 合入 `origin/main`，发布 `2.7.5 / be80eea665c0`。backend index/amd64 为 `sha256:0a79c77e5c9db8f4db00a7dc310264815de461c4caf9172d29cca062b29c1b1e` / `sha256:c99ec42f67abd451de6d2e6d371166b28850bfded128f687ccfd2d7c95ecd132`；frontend index/amd64 为 `sha256:056e4fd4a064134f3bacce6827a3dbd3206ef6a442d93b50c104e05dbc6c86f4` / `sha256:cd7477395e25d58cca96b2d08f86a7a30c579cb927ab98e94c918d9f34ec69c7`。
- `backup/deploy`: 发布前备份 `/opt/cici/backups/20260715-005545-before-2.7.5-task208-crm-analysis` 的 env/PostgreSQL/KB/Qdrant 分别为 1,646 / 2,862,193 / 510,994 / 1,584,517 bytes。只重建 backend/frontend；四个状态服务容器 ID 不变并保持 `2.6.12` healthy，应用健康 `UP`、版本提交一致、Nginx 通过。
- `crm-plan`: 写前 live dry-run 精确发现 12 产品、16 客户、24 商机、72 商机产品、16 合同、48 订单和 144 明细；计划 316 条更新、316 处 owner 变化、88 处 Account 重连、404 个字段变化，创建与重复均为 0，四项数据质量检查均通过。
- `crm-execute/readback`: 先生成 316 条受保护回滚清单，再对六类对象执行 update-only；执行后结构回读 12/24/72/16/48/144，第二次 live dry-run 为待更新 0、owner 变化 0、Account 变化 0、字段变化 0、创建 0、重复 0。
- `ranking`: SalesA 最近 30 天数量 Top 5 连续为 `智能巡检终端 X1 130`、`边缘采集网关 G5 110`、`安全监测传感器 S2 95`、`制造运营分析平台 MP 75`、`预测性维护应用 PA 65`；对应销售额 884000、1408000、304000、2850000、1690000，金额冠军为 MP。
- `salesa-sse/persistence`: Owen/SalesA 登录、组织和 CloudCC 连接均成功，`crm-business-analysis` 启用。5 个全新 SSE 会话均只有 `phase/delta/done`，Top 5 与关键数值一致；5 组持久化消息均为 user + assistant，落库正文与 SSE 正文逐字一致。差异仅为每次真实查询的数据截止时间。
- `blocking/openapi`: 内部 blocking 正文通过；OpenAPI 临时启用 api channel 并创建 SalesA run-as 标准 Key 后，blocking 与 streaming 正文均一致。`agent_thought` 仅保留 `AgentCiCi runtime completed/completed` 或“运行阶段已更新”的脱敏状态，不含工具、参数、记录 ID 或原始 observation。验收后 Key 已撤销，原 `wechat/dingtalk/feishu/web` 渠道精确恢复。
- `salesb`: CCAdmin/SalesB 管理员对照查询返回同一 Top 5；当前演示组织不存在第三个普通销售 persona，因此未制造或冒用额外身份，也未扩大 role/profile/sharingRule。
- `browser-production`: 生产桌面真实新会话输入同一问题，页面显示直接结论、产品 Top 5、经营诊断、前瞻信号、建议动作和口径覆盖；数量/金额冠军、贡献率、环比、订单/客户、商机与合同信号均可见。DOM 中工具名、`tool_call/tool_result`、原始 JSON 和“等待确认”均为 false；状态为“已完成本轮处理”，console error 为 0。
- `runtime`: 最终独立 blocking smoke 后干净窗口 backend error=0、精确 Nginx 5xx=0、CRM 分析 error=0。较早的全局日志包含客户端关闭 SSE 产生的 broken pipe、客户工作台既有 `customer_signal` 并发死锁一次，以及空白新会话 404 被全局处理器记为 500；均未影响 CRM 分析结果，且不属于 TASK-208 变更路径。
- `known-baseline`: 全仓状态校验器仍报告 `origin/main` 已存在的历史治理债务，例如旧完成任务仍位于 Active Tasks 与旧规格 front matter 漂移；TASK-208 代表文件、assignment、JSON、`git diff --check` 和最终交付状态单独通过，未宣称全仓历史基线转绿。

## TASK-209 运营平台登录页原图像素锁定（2026-07-15）

- `release`: `2.7.2 / ddcda0ef6111` 已上线；backend index `sha256:f4ec61fc0532be5593a4cc6c3646906d026770ee56e55b5aebdea936c1d29979`、amd64 `sha256:3403aad868f7f06d08c6b6ac685fafd8b4f39ef3a0f5ab36dcfe35deac8e562f`；frontend index `sha256:2ae803bf615cbb84bf7ddf451716b0f94df452c2d94e6936e01eacf59a18e918`、amd64 `sha256:21ef8d647026f1ffb361c82cfb3230770da8b8cf1098fa314e4cef5cd9538eda`。
- `production`: 备份 `/opt/cici/backups/20260715-001809-before-2.7.2-task209-reference-login`；backend/frontend 与四个状态服务均 healthy，运行版本和 Git 提交一致。
- `browser`: 生产 `1672 × 941` 默认态使用无损原图整页背景，透明交互层坐标对齐；无横向溢出，控制台 error/warning 为 0，输入后真实按钮可用且未提交假凭据。

## TASK-210 客户互动工作台标准渠道图标本地验收（2026-07-14）

- `identity/assignment`: MANAGER-001 通用与 TASK-210 SSH challenge 均为 `allowed`；客户工作台源码、样式、依赖、规格、任务状态和测试报告代表文件通过 assignment 检查。
- `tdd`: 新增来源语义测试先以缺少 `timelineSourceKind`/`lifecycleSourceLabel` 失败，再实现转绿；重复 CRM 事件键测试先以缺少 `timelineItemKey` 失败，再实现转绿。
- `frontend`: Vitest 16 个文件、89 项通过；TypeScript/Vite 生产构建通过，仅保留既有大 chunk 提示；`git diff --check` 通过。
- `production-baseline`: ECS 回读确认真实线上为 `2.7.2 / ddcda0ef6111`，不是仅存在 Git 标签的 `2.7.3`；发布分支已撤销 `2.7.3` 合并并合入 `2.7.2`，避免把未部署 CRM 分析改动捎带上线。
- `backend-baseline`: 在误合并 `2.7.3` 时完整后端套件暴露共享数据库重复账号夹具、内置 `crm-business-analysis` 版本漂移、历史非空字段夹具和连接池耗尽，共运行 325 项并出现 58 failure / 5 error；该合并已撤销。本次最终基线的后端 `-DskipTests package` 与 Compose config 通过，TASK-210 不改后端代码。
- `icon-source`: 微信渠道使用 Simple Icons 公开维护的规范路径和 `#07C160` 品牌色；电话、会议、邮件、CRM 任务、CRM 日程、客户反馈使用项目既有 Lucide 标准图标，不含自绘 SVG 路径。
- `browser-local`: 真实演示组织 CRM 数据在 `1600 × 1000` 桌面端加载；CRM 任务显示清单图标、CRM 日程显示日历图标；以只读请求拦截将一条现有记录标记为微信后，规范双气泡图标与实际轴线、日期和内容列共同通过视觉检查。证据：`output/playwright/task210-local-standard-icons.png`、`output/playwright/task210-local-wechat-standard-icon-detail.png`。
- `browser-console`: 完整时间线曾暴露重复 CRM event id 的 React key 错误；加入事件 ID、发生时间和行号组合键后，重新加载并展开完整时间线只有 React DevTools info，新增 error/warning 为 0。
- `release`: 统一版本 `2.7.4 / 3206fdbc196f` 已在生产运行，backend/frontend 与四个状态服务均 healthy；TASK-210 最终生产视觉证据仍由其任务持有人补录。

## TASK-207 前台主题一致性与视觉对齐（2026-07-14）

- `identity/assignment`: MANAGER-001 通用与 TASK-207 SSH challenge 均为 `allowed`；前端、设计事实源、规格、任务状态和测试报告代表文件通过 assignment 检查。
- `tdd-focused`: 先验证组织首字符函数、四套主题序列色、数据图 class 和智能体身份色旧内联样式的失败状态，再实现转绿；最终聚焦测试 10 项通过。
- `frontend-full`: Vitest 15 个文件、85 项通过；TypeScript/Vite 生产构建通过，仅保留既有大 chunk 提示。
- `static`: `DESIGN.json` JSON parse、`git diff --check` 均通过。
- `browser-eight-themes`: 真实本地账号逐一应用 `gilded`、`crm-blue`、`ocean`、`sakura`、`lavender`、`avocado`、`wine`、`galaxy`；根主题、画布、设置面板和轨道表面均正确切换，验收后恢复 `gilded`。
- `browser-pages`: 在 `1600 × 1000` 下验证助手工作台、智能体/会话层级、AI 应用菜单、客户互动工作台、互动整理弹窗、数据洞察、客户洞察、知微画像、个人设置、专属记忆和 CRM 外层壳；`gilded`、`sakura`、`galaxy` 重点表面均读取对应主题 token。
- `browser-layout`: 数据看板四列闭合，同一行卡片同顶同高；AI 应用菜单五行均为 44px 且左锚点一致；互动弹窗左右栏同顶同高；`document/body scrollWidth == clientWidth == 1600`。
- `browser-org`: 当前组织 `CloudCC 智能体应用DEMO` 的左下角入口显示 `C`，不再显示固定 `CB`。
- `browser-console`: error/warning 为 0。截图证据见 `output/playwright/task207-*.png`，设计结论见 `design-qa.md`。

## TASK-206 CloudCC 嵌入身份同步自动恢复（2026-07-14）

- `identity/assignment`: MANAGER-001 通过 TASK-206 assignment 检查；pagecomponent 源码、预构建 bundle、配置、测试与任务状态均在授权范围内。
- `backend-focused`: `CloudccAccessTokenServiceTest,CloudccOpenApiServiceTest` 共 6 项通过；覆盖 `/api/user/getUserInfo` GET、`accessToken` 会话头、actor/org 提取和失效会话拒绝。
- `backend-full-baseline`: 全量测试完成但存在既有非 TASK-206 失败，集中在停用模型厂商、`onekeytoken` 历史配置、非空 `source_type` 旧夹具和平台技能/租户生命周期预期漂移；TASK-206 聚焦测试全部通过。
- `frontend-focused`: `CloudccEmbedSso.test.ts` 通过 7 项；明确锁定源码和发布 bundle 只能调用 `getToken()`，不得调用需要 clientId/secretKey 的 `getOpenApiToken`。
- `frontend-full`: `npm test` 通过，14 个文件、80 项；`npm run build` 成功，仅保留既有 Vite 大 chunk 提示；UMD `node --check` 与 `git diff --check` 通过。
- `cloudcc-package`: `cloudcc package pagecomponent customer-workbench . --dry-run` 通过，确认使用 `frontend/build/customer-workbench.umd.min.js` 且不打包本地凭据与状态文件。
- `cloudcc-publish`: 通过 `cc-customization-expert-msapi 2.1.279-msapi` 发布 pagecomponent V15，ID `6a5628cee4b0a577cbba2088`；customPage dry-run 后更新为 V9，精确引用该组件、`embedded=true` 和生产工作台 URL。
- `cloudcc-readback`: `verify injectionPage` 回读的组件 ID、名称、customPage V9、嵌入标记和 URL 均精确匹配；因接口未返回 `actualVersions` 保留已知 `stale_component_reference` warning，真实 CRM 三轮 HTTP 200 SSO 验收作为运行时版本证据。
- `release`: `2.6.11 / c540988655cb`；backend index `sha256:9be1120bc9a26e507068d75fbd5c9eb6db0e61ef24dc3785be9e9f8330bb5f4b`、amd64 `sha256:3694fa2545aeb136c234e9cc2ab7df64f684720f21b2ea25c424ed120eb82e69`；frontend index `sha256:ba57516fe20e08574f6b029e75f191cfb812caae29f8029454d1d981439822c5`、amd64 `sha256:4752c464acca6c864afda592e6769345173b4497ce9f0634a7f0e62168ba1079`。
- `production`: 备份 `/opt/cici/backups/20260714-202718-before-2.6.11-task206-cloudcc-session-sso` 四类文件非空；六服务 healthy，健康 `UP`，运行版本与 Git 提交一致，Nginx 配置有效，`x` 工作台和 `onechat` 生产 IP smoke 均为 200。
- `browser-production`: 真实 CloudCC CRM 注入页首次加载和连续两次刷新均显示“CloudCC CRM 已连接”、`CCAdmin / 组织管理员`、客户队列和详情数据；三次 `/ticket` 与三次 `/consume` 全部 HTTP 200，账号映射失败提示为 false，后端同期无会话验证拒绝或 ERROR。

## TASK-205 CRM 经营分析与高仿真销售数据生产验收（2026-07-14）

- `identity/assignment`: MANAGER-001 的 TASK-205 代表文件授权检查为 `allowed`；`git diff --check`、离线种子契约和后端打包通过，未包含用户 `diagrams/` 或任何凭据。
- `backend-focused`: `CrmProductSalesAnalysisServiceTest,CrmProductSalesAnalysisToolServiceTest,CrmProductSalesIntentRouterTest,ToolOrchestratorServiceTest,CrmAnalyticsDemoDatasetContractTest,FileBackedBuiltinSkillIntegrationTest` 共 17 项通过，0 failure / 0 error；路由测试按 TDD 先观察到缺类编译失败再转绿。
- `backend-full-baseline`: 全量 274 项运行完成，15 failure / 3 error；均来自既有认证夹具、平台模型/技能配置漂移、停用模型厂商、历史非空字段夹具和 PostgreSQL 连接数耗尽，与 TASK-205 定向测试无关。
- `crm-write/readback`: 默认 dry-run 后显式 execute 写入批次 `TASK-205-CRM-ANALYTICS-DEMO-V1`；第二次 execute 创建数均为 0。最终回读为产品 12、客户 16、商机 24、商机产品 72、合同 16、订单 48、订单产品 144，4 张当前草稿高销量订单被排除，关联完整性和销量/销售额排行差异检查均通过。
- `crm-ranking`: 最近 30 天有效订单销量 Top 5 为 `DEMO-X1 130`、`DEMO-G5 110`、`DEMO-S2 95`、`DEMO-MP 75`、`DEMO-PA 65`；对应销售额为 884000、1408000、304000、2850000、1690000。
- `runtime-correction`: `2.6.7` 首轮真实会话暴露发布版 `cici-system` 未锁定新 Skill，模型仍探测原子对象；新增确定性意图门并将 `crm-business-analysis` 纳入 `cici-system` 发布版本 3 后，以不可变版本 `2.6.8` 修正。`2.6.7` 不作为回滚目标。
- `production-chat`: 5 个全新会话同问“嗯，销量最好的产品有哪些？”均包含同一 Top 5、最近 30 天、销售数量口径、截止时间和 `product/cloudccorder/cloudccorderitem` 来源；服务器日志恰好 5 次 `crm_product_sales_rank` 且均为 `skill_scoped`，无原子 CRM 工具调用。
- `release`: `2.6.8 / 095094300a25`；backend index `sha256:27c985366695339a298ad3f6a333cd03827fc08fc334f9f1161242f584b7f2aa`、amd64 `sha256:ea08a7a86b8c64aa565ceef1ce768b0af367550e081a3ad6781d078b23811265`；frontend index `sha256:784504e1a57a5463d722a74941b0a15085ebf04bf2be08cef276cdb8eadfca0c`、amd64 `sha256:277a476b3cf0c1b495ab8202f3380674af0119794e7898172ce4dcda2964ed4f`。
- `production`: 最终备份 `/opt/cici/backups/20260714-184006-before-2.6.8-task205-deterministic-routing` 四类数据非空；六服务 healthy、健康 `UP`、版本一致、V80 无迁移、Nginx 有效，稳定窗口 backend error 与精确 Nginx 5xx 均为 0；`x` HTTPS 200/HTTP 301，`onechat` 生产 IP smoke 200。

## TASK-202 用户级产品主题偏好本地验收（2026-07-14）

- `identity/assignment`: MANAGER-001 通用与 TASK-202 SSH challenge 均为 allowed；后端认证、V80、前端主题模块、设计事实源、规格和状态代表文件通过 assignment 检查。
- `backend`: 在独立 PostgreSQL schema 上从空库执行 76 个 Flyway 迁移并到达 V80；`AuthFlowIntegrationTest,PlatformAuthIntegrationTest` 共 22 项，0 failure / 0 error，覆盖普通账号与平台账号的默认主题、保存、刷新/切换组织持久化和非法代码拒绝。共享测试库存在历史固定手机号夹具污染，未作为最终证据源。
- `frontend`: 主题视觉修复后 Vitest 13 个文件、73 项通过；TypeScript/Vite production build 通过，仅保留既有大 chunk 提示；`git diff --check` 与 `jq empty DESIGN.json` 通过。
- `browser-settings`: 真实桌面浏览器的“界面主题”展示八个标准选项，逐项即时预览并显示同步状态；浅色主题保持可读性，星河是唯一深色主题，布局、控件尺寸和交互结构不随主题变化。
- `browser-cross-shell`: 主应用、管理端、运营平台和客户互动工作台均读取共享语义令牌；星河运营平台最初发现硬编码浅色卡片导致低对比，改为公共 `platform-*` 令牌后复验通过。平台账号刷新后仍恢复星河，普通账号跨管理端读取红酒主题成功；验收结束后本地演示账号恢复鎏金默认。
- `browser-visual-hotfix`: 2048×1152 真实浏览器逐项检查八主题。智能体栏、会话面板、右侧栏、指标组和当前状态泳道均透明、无阴影、无变换；智能体头像固定 42×42、无阴影/缩放；八主题外层横向溢出均为 0，控制台 error/warning 为 0，验收后恢复鎏金主题。
- `design-qa`: 原版、问题版与修复版完成同图全景和聚焦对比，`design-qa.md` 最终结果为 `passed`。
- `release`: `2.6.6 / 4caaa4800b3d` 已上线；backend index `sha256:040c77eb89d4ee06b4e7ac615fa1e9bb44a4aecaf3f34a9453aa323c6351b20c`、amd64 `sha256:a57d540cab963a8c108b40471ef0a7cb025dc95aa8cdcc2f06db327ed0caa399`；frontend index `sha256:b8bed46b93bbcba24e9ad3e5face8ede291cb013a28f28de323579c1c6857982`、amd64 `sha256:efb42859509f6ebfe2bf58daa93d2af9bf8aa7ad25568e7915816b347892638d`。
- `production`: 备份 `/opt/cici/backups/20260714-142848-before-2.6.6-task202-theme-visual-hotfix` 四类数据非空；六服务 healthy、健康 UP、Flyway V80 成功、Nginx 有效、稳定窗口错误扫描为 0。生产浏览器显示版本 2.6.6，头像悬停前后均为 42×42、无阴影/变换，结构层透明、外层溢出 0、控制台错误 0。

## TASK-201 智能体构建页布局与模型治理本地验收（2026-07-14）

- `identity/assignment`: MANAGER-001 通用与 TASK-201 SSH challenge 均为 allowed；Builder 源码、样式、测试、规格、任务状态和测试报告代表文件通过 assignment 检查。
- `frontend-focused`: `npm test -- src/assistant/AgentBuilderShell.test.ts` -> 1 个文件、14 项通过，覆盖生命周期页签顺序、评测/渠道语义隔离、平台模型治理提示和既有模型默认解析。
- `frontend-full`: `npm test` -> 12 个文件、68 项通过；`npm run build` -> success，仅保留既有 Vite 大 chunk 提示；`git diff --check` -> success。
- `browser-definition`: 本地真实 1280x720 管理后台打开“客户成功” Agent；定义区左右栏均为 452.5px × 687px，起止边界一致；头像 58px、上传/清除与四个 56px 策略按钮处于同一视觉行；Builder 与主区域 `scrollWidth == clientWidth`，无横向溢出。
- `browser-model`: Agent 定义区 `基础模型` 文本不存在、`selectCount=0`；只读说明明确运行模型由平台统一策略自动选择，内部 `draft.model` 和新建 Agent 默认模型解析未删除。
- `browser-lifecycle`: 下方“版本控制与交付”依次包含流程图预览、触发与调度、试运行、评测、版本历史、发布渠道、执行记录、编译摘要、流程代码、Manifest；评测内容和企微/钉钉/飞书/Web/Open API 渠道内容分别打开，active/focus 使用文本与金色下划线，无按钮框、阴影或横向溢出。
- `browser-console`: error/warning 为 0；本地后端以 local profile 启动，Flyway V79 up to date，登录和 Agent 数据均来自真实本地 API。
- `release`: 未执行生产发布；当前证据仅覆盖本地实现与桌面端验收。

## TASK-200 多租户智能体评测控制面生产验收（2026-07-14）

- `identity/assignment`: MANAGER-001 通用和 TASK-200 SSH challenge 均为 allowed；状态、规格、后端/前端、V79 与两个 Nginx 配置均通过 assignment 检查。
- `backend`: `AgentProductionReadinessIntegrationTest,AgentEvaluationControlPlaneIntegrationTest,AgentEvaluationAssertionEngineTest,RbacProductionReadinessIntegrationTest,PlatformAuthIntegrationTest,PlatformGovernanceIntegrationTest,AgentRunTraceIntegrationTest` 共 20 项，0 failure / 0 error。
- `frontend`: 12 个 Vitest 文件、67 项通过；TypeScript/Vite 生产构建通过，仅保留既有大 chunk 提示；Compose config 与 `git diff --check` 通过。
- `migration/runtime`: 生产从 V78 正向迁移到 V79 `agent evaluation control plane`，`success=true`；六服务 healthy，后端 `/actuator/health=UP`，`/system/version=2.6.4 / d88f4293759f`，Nginx 配置有效。
- `production-api`: demo-org 的 `/evaluation/overview|suites|runs|issues` 均返回 JSON success；平台 `/platform/evaluation/overview|suites|runs` 均成功且平台标准套件为 1；租户访问平台接口与平台账号访问租户接口均返回 403。
- `production-browser`: 1280x720 下租户“AI 质量”、Builder 独立“评测”Tab、仅含飞书/钉钉/企微等入口的“发布渠道”、平台“智能体质量”均通过；页面显示 `2.6.4`，无横向溢出，console error/warning 为 0。
- `release`: 最终 `2.6.4 / d88f4293759f`；backend index `sha256:58983c43796896d05dc4a07059dedf1d10d26cdb6413567e7056e771a77b0388`、amd64 `sha256:fe378b7652eb52a3c2b58e3d43dfc68c00bbe16d3fa44d4011eea3aec0e5c846`；frontend index `sha256:0ffa36646860570eabe0f21cfe28514d2450608a11e981f04184971689fd2f90`、amd64 `sha256:187b2b7c3a13b518cea186187cc8e7e2a09dd7fc24a8b6b9b71cef4d54f33582`。
- `release-correction`: `2.6.3` 首次 smoke 发现评测 API 被生产 Nginx 当作 SPA HTML，补齐只代理带尾部子路径的 `/evaluation/*` 与 `/platform/evaluation/*` 后以不可变新版本 `2.6.4` 替代；`/platform/evaluation` 页面继续返回 HTML。`2.6.3` 不作为回滚目标。
- `backup/ops`: 最终备份 `/opt/cici/backups/20260714-075215-before-2.6.4-task200-nginx-hotfix` 六项非空；初始迁移前备份 `/opt/cici/backups/20260714-074613-before-2.6.3-task200-agent-evaluation` 四项非空。稳定窗口 backend error=0、frontend 5xx=0；x 域名 HTTP 301/HTTPS 200，onechat 生产 IP 解析 smoke 200，本机 DNS 空结果风险保留。

## TASK-197 客户互动档案、动态记忆与按需检索生产验收（2026-07-12）

- `backend-focused`: `CustomerMemoryServiceTest,CustomerInteractionIngestionServiceTest,CustomerWorkbenchServiceTest,CustomerCrmProjectionServiceTest` 通过，覆盖 90 天窗口、历史扩展、指定档案优先、结构化记忆生成、确认幂等和 CRM 投影兼容。
- `frontend`: 12 个测试文件、64 项测试通过；生产构建通过，仅保留既有 Vite chunk-size 提示。
- `full-backend-baseline`: V76 类型契约修正后 Spring/Flyway/JPA 启动通过；共享测试库全量套件仍有 16 个失败、2 个错误，来自既有账号/夹具污染、平台模型 `onekeytoken` 配置漂移、停用模型厂商和历史技能名称差异，与 TASK-197 定向套件无关。
- `production-api`: 演示组织真实客户“北京智造科技有限公司”返回 10 条时间线、1 条可追溯档案和 `crmConnection.ready=true`；档案包含 59 字确认稿、1 个原件、完整结构化分析和 10 条 ACTIVE 客户记忆。
- `assistant-context`: 普通风险问题返回 7 条证据、10 条近期互动、8 条 ACTIVE 记忆，`historyRequested=false`，其中 1 条证据可直接打开互动档案；回复正文使用 `[E1]` 等编号引用事实。
- `production-browser`: 版本 `2.5.11`，全量客户搜索命中北京智造；时间线“查看档案”打开确认记录、AI 分析、原始材料三个页签，原件在独立 Blob 标签页显示，最终控制台 `0 errors / 0 warnings`。截图：`output/playwright/task197-prod-archive-final-2.5.11.png`。
- `release`: `2.5.11 / d0ed7e4129cf`；backend index `sha256:d4ba55523711a534ce7ef37c676d8eb8505c27a6497b1a4363f675f59d0aeec9`、amd64 `sha256:a8793ab297a0a74cbde806ad29f739673a802ec069027d34355b8702d0b6fecb`；frontend index `sha256:9e154f5c605ccfbb999297f9e9f3a1935af86893781bfe15947069a4c78e2a89`、amd64 `sha256:a03f195ed0397a0484567b8fe7f403632c1d0e5a1e6b38224f87bb6ab373ca32`。
- `operations`: 最终备份 `/opt/cici/backups/20260712-143215-before-release` 四类文件非空；backend/frontend `2.5.11` healthy，健康检查 `UP`，V76 已在前一发布成功执行且重启无迁移错误。

## TASK-196 客户互动整理上下文稳定性生产验收（2026-07-12）

- `root-cause`: 互动确认调用 `queue?refresh=true` 触发 10,000 Account 全量投影；队列回读把不在当前页的已选客户替换为首条；弹窗使用动态 Account；助手又把普通“老客户经营”分析误判为模式切换。
- `automated`: 前端 12 个测试文件、64 项测试和生产构建通过；`CustomerWorkbenchServiceTest,CustomerCrmProjectionServiceTest` 共 12 项通过，覆盖选择保持、同模式幂等和明确导航命令。
- `release`: 生产 `2.5.9 / 6c7e27181fbb`；backend index `sha256:e72350e9b5a92c811649f260791c63bd2120a11a25455b672c60648303716b7f`、amd64 `sha256:d83a6892a1d46cc8aafa130ccb8831f9eead29ad8d5abd7251cad171a051addd`；frontend index `sha256:5bb6554e4202e88fadec1eb7f0870bcf1766933da076eb1851706c0632bac45a`、amd64 `sha256:ee3e34ba4eec966e3080e6dfe225d313b0bd59f42e5f0d9b8bac6491a147521d`。
- `production-browser`: 大数据组织搜索“奔驰”返回 4 条，目标客户完成 `TASK-196 稳定性验收` 受控互动归集；确认后及 35 秒轮询后，搜索词、结果数、当前客户和 CRM 连接均保持。截图：`output/playwright/task196-prod-customer-context-stable-2.5.9.png`。
- `network-logs`: 归集轨迹只有普通 `queue?mode=existing&query=奔驰`，无 `refresh=true`；确认后没有新 CRM 全量同步，浏览器控制台 0 错误，Nginx 5xx 和后端目标错误为空。
- `operations`: 备份 `/opt/cici/backups/20260712-124820-before-2.5.9-task196-context-stability` 四类文件非空；backend/frontend `2.5.9` healthy，状态服务保持 `2.3.4`；健康 `UP`、Nginx 有效、公网页面 200。

## TASK-195 客户互动时间线完整年份生产验收（2026-07-12）

- `root-cause`: 时间线复用通用 `shortDate()`，非今天/昨天只输出 `MM-DD HH:mm`，跨年记录无法区分；首次年份实现使用 `pre-line` 时浏览器仍会在连字符处折行。
- `frontend`: 12 个测试文件、62 项测试通过；生产构建通过，仅保留既有 Vite chunk-size 提示。单元测试覆盖 2024/2026 同月同日仍保留四位年份及非法来源值回退。
- `release`: 最终生产 `2.5.8 / a016c165fd95`；backend index `sha256:fa59e23ec070d06708c07324895333fd33be60b2b94035152c25a728cacdd21b`、amd64 `sha256:93a6bd67479c9d51f96f7b7f2c53732bd11c89fce7ba1627b454c2f66c8ab6d5`；frontend index `sha256:580f5167a4c3cfe71488eb51f81478a5efa10dae7a1d370d1861849755440bc6`、amd64 `sha256:07246081a4c74f7daed5d4f2e0867474523de39dc11148f15e0de2fddab2ebe5`。
- `production-browser`: 真实客户“梅赛德斯-奔驰汽车金融有限公司”的完整时间线共 22 条，概览显示前 5 条；2026 与 2023 记录均为 `YYYY-MM-DD` + `HH:mm` 两行，日期内部 `white-space=pre` 不折行。图标中心与垂直轴偏差 `0px`，页面无外层溢出、无业务错误。截图：`output/playwright/task195-prod-timeline-full-year-2.5.8.png`。
- `operations`: 最终备份 `/opt/cici/backups/20260712-120506-before-2.5.8-task195-no-wrap` 四类文件非空；backend/frontend `2.5.8` healthy，状态服务保持 `2.3.4`；健康 `UP`、版本/commit 一致，发布后后端目标错误和 Nginx 5xx 扫描为空。

## TASK-194 全量客户名称搜索与输入焦点治理生产验收（2026-07-12）

- `root-cause`: 原搜索只过滤当前内存投影，并在名称匹配前应用新/老客户模式和队列筛选；投影又有 10,000 Account 上限，因此 CRM 中可见的客户可能无法命中。全局 `input:focus` 阴影叠加组件边框，形成双层焦点框。
- `backend`: `CustomerCrmProjectionServiceTest,CustomerWorkbenchServiceTest` 共 11 项通过；覆盖权限范围 Account 名称查询、单引号转义、模式/筛选旁路、缓存外客户详情加载和既有队列行为。
- `frontend`: 12 个测试文件、60 项通过；生产构建通过，仅保留既有 Vite chunk-size 提示。覆盖搜索态文案、客户真实模式自动对齐和现有工作台交互。
- `release`: 生产 `2.5.6 / 12c766bed77d`；backend index `sha256:bfa4ad2932c037000716213cc6df224483d863cce4a0332252fea5de77cfd59b`、amd64 `sha256:8c6918de52589c95bdac2cc7c83d9138484276454278a98a3693407bc2cd645d`；frontend index `sha256:661037ba5a6d1a7543122871f713b360e4e3ad9f3fa1311878d2598b498e56b6`、amd64 `sha256:e063dbc5fdd2adbe8d37ea271c2c2a0f855bdc3c353449ac0ee630ef7339f7ad`。
- `production-api`: 真实组织 `org5nszpgj99jaysxv6y` 在 `mode=new/filter=focus` 下查询“青岛海信商用显示”，HTTP 200，`source=CLOUDCC_SEARCH`、`searchScope=ALL_VISIBLE_ACCOUNTS`、`totalElements=1`，命中“青岛海信商用显示股份有限公司”；复测搜索 0.76 秒、详情 0.22 秒，客户分类 `EXISTING`。
- `production-browser`: 版本 `2.5.6`、CloudCC 已连接；搜索结果显示“全部客户搜索结果 1 条”，页面自动切换“老客户经营队列”并展示服务与关系预警。无过期令牌或通用服务器错误；input 为零边框/零阴影/透明背景，wrapper 为单一 1px 金色边框且无阴影。截图：`output/playwright/task194-prod-global-search-existing-mode-2.5.6.png`。
- `operations`: 备份 `/opt/cici/backups/20260712-112702-before-2.5.6-task194-global-search` 四类文件非空；backend/frontend `2.5.6` healthy，状态服务保持 `2.3.4` healthy；健康 `UP`、版本/commit 一致、Nginx 有效，公开根路由与工作台均 HTTP 200，发布后后端目标错误和 Nginx 5xx 扫描为空。

## TASK-193 客户队列最近互动倒序生产验收（2026-07-12）

- `backend`: `CustomerCrmProjectionServiceTest,CustomerWorkbenchServiceTest` 共 10 项通过；覆盖新客户和老客户按最近互动倒序、暂无互动置后、10,000 客户规模和既有工作台行为。
- `frontend`: 12 个测试文件、59 项测试通过；生产构建通过，仅保留既有 Vite chunk-size 提示；页面默认排序选项为“最近互动”。
- `release`: `2.5.3 / c7af96a48092` 已发布；后端 index `sha256:2be33ef3be924aed10865cd273d44db4dbb3d2e71a0948fb46ec908a6971eb11`，前端 index `sha256:c128d28bcd58917714d8bf8e8911bd2d566bb91cc48258b140a1c321eb9e8758`。
- `production-data`: 真实组织 `org5nszpgj99jaysxv6y` 使用现有 Owen 身份调用未传 `sort` 的默认接口；新客户、老客户首屏各 12 条均 `descending=true`、`emptyLast=true`。
- `operations`: 发布前备份四类产物非空；六服务健康，版本和 Git commit 一致，工作台 HTTP 200，后端目标错误和 Nginx 5xx 扫描为空。

## TASK-192 大数据量 CRM 异步初始化生产验收（2026-07-12）

- `root-cause`: 组织 `org5nszpgj99jaysxv6y` 首屏四个接口同步等待 Account、Contact、Opportunity、Task、Event、Case、Contract 全量分页读取；受控后端直读耗时 94.99 秒，超过 Nginx 60 秒读取超时。真实账号会话有效，问题不是凭据或身份映射失败。
- `before`: 2026-07-12 08:58:38，`integration-status`、`notifications`、`supervisor-summary`、`queue` 四路请求同时返回 504，页面直接展示 Nginx HTML；可见 Account 达到现有 10,000 条读取上限。
- `backend-focused`: `CustomerCrmProjectionServiceTest`、`CustomerWorkbenchServiceTest`、`CustomerSignalRepositoryIntegrationTest` 共 10 项通过；覆盖异步立即返回、并发单飞、10,000 客户五秒规模门、批量建议读取和零逐客户计数。
- `frontend`: 12 个测试文件、58 项测试通过；生产构建通过，仅保留既有 Vite chunk-size 提示。非 JSON 504 被规范化为中文业务消息，同步状态自动轮询。
- `release`: `2.5.2 / 1c2084b5746c` 已发布；后端 index `sha256:287f46e2e748bee6b49db68d1001a6136770ec8adc8ae6508a002c73a9426aea`，前端 index `sha256:ae284daf247695759e7e1961dd74db2aa3ecd8d1274cedcb28175aa8aae46b25`。
- `cold-cache`: 生产 HTTPS 四路并发请求分别在 0.996-1.013 秒返回 HTTP 200；queue 为 `CLOUDCC_SYNCING` 且 `syncStatus=SYNCING`，integration 显示“正在同步 CRM 数据”。
- `ready`: 后台同步 46.21 秒完成，Account=10,000、`recordLimitReached=true`；integration 转为 `READY`，队列 0.68 秒返回 12 条首屏记录，重点推进筛选总数 37。
- `logs`: 发布后无 504、`upstream timed out`、异常堆栈或通用服务器错误。临时浏览器诊断会话在验收收尾时过期，未重复注入凭据；API、Nginx 与后台日志证据完整。
- `state-validation`: TASK-192 自身状态、spec、assignment 和完成区归档均有效；全库校验仍被 TASK-191 及更早任务留在 Active Tasks、旧 spec 状态/时间格式和旧 task 热文件预算等既有治理债务阻塞，本任务未扩散修改这些无关历史文件。
- `boundary`: 本任务解决超时、并发阻塞和错误展示；每对象 10,000 条 OpenAPI 上限仍存在并已显式提示，完整增量投影需另立架构任务。

## TASK-191 CloudCC 重复刷新稳定性生产验收（2026-07-12）

- `root-cause`: 白屏发生在 AgentCiCi iframe 请求之前，CloudCC 重用并清空已标记 mounted 的 pagecomponent 宿主节点；`Unexpected server error` 先后暴露确定性信号 ID 并发插入冲突，以及原子 UPSERT 缺少实际事务边界。
- `component`: UMD 延迟节点 fixture 在 900ms 插入组件、1800ms 清空同一节点，1300ms/3000ms 均为一个 iframe；`node --check` 和技能 package dry-run 通过。通过 `cc-customization-expert-msapi` 发布 pagecomponent V11、绑定 customPage V5。
- `backend`: `CustomerSignalRepositoryIntegrationTest,CustomerCrmProjectionServiceTest,CustomerWorkbenchServiceTest` 共 8 项通过；真实 PostgreSQL 验证同一 ID 两次 UPSERT 仅保留一行并更新最新内容。
- `release`: dry-run、前后端构建、ACR 推送和 Git tag `2.4.12` 通过；运行版本 `2.4.12 / 4d00d417dcf3`。backend index `sha256:b60f4bead39d06831a846c3efbcf3368aba21e0b23d80fb3f6a7020cceede51c`，frontend index `sha256:ba02632b8b61f812ca9b2244b89f319f0b6b4e9e3986af7a32016be8f089649e`。
- `backup/deploy`: `/opt/cici/backups/20260712-001641-before-2.4.12-task191-transaction` 四类文件非空；backend/frontend `2.4.12` healthy，状态服务保持 `2.3.4` healthy；健康 `UP`，Nginx 配置有效。
- `production-browser`: 真实 CloudCC Web 登录并进入客户互动工作台，连续三次刷新均重新加载 iframe、真实客户数据、`CloudCC CRM 已连接` 和助理历史；未出现白屏或 `Unexpected server error`。截图：`output/playwright/task191-prod-cloudcc-refresh-stable.png`。
- `production-logs`: 发布后目标请求未再出现 duplicate key、`TransactionRequiredException`、连接池超时或通用服务器错误；部署切换期间旧 SSE 会话产生的两条 `Session not found` 404 与本修复无关，16:23Z 后错误扫描为空。

## TASK-190 CloudCC 嵌入端会话恢复生产验收（2026-07-11）

- `root-cause`: 真实 CRM 嵌入页的 `CCAdmin` 调用 CloudCC Account 查询时，CloudCC 以 HTTP 200 返回 `result=false` 和“登录失败，请再次尝试重新登录”；旧实现只刷新 HTTP 401，且同一用户缓存未命中时可能并发申请 Token。
- `backend-focused-tests`: `CloudccAccessTokenServiceTest,CloudccOpenApiServiceTest,CustomerWorkbenchServiceTest` -> **success**。8 路并发只触发 1 次 Token 请求；HTTP-200 登录失效刷新后重试成功；普通业务错误不误判。
- `release`: dry-run、前后端构建、ACR 推送、镜像 inspect 和 Git tag `2.4.9` -> **success**；运行版本 `2.4.9 / 052bf118fc1e`。
- `backup/deploy`: `/opt/cici/backups/20260711-224930-before-2.4.9-task190-cloudcc-session` 四类文件非空；backend/frontend 健康，四个状态服务保持 `2.3.4` 健康；Nginx 与公开三入口通过。
- `production-concurrency`: 真实映射成员 `CCAdmin` 的 integration、queue、notifications、supervisor 六路并发请求全部 HTTP 200。
- `production-readback`: integration `CONNECTED / ready=true / visibleAccounts=110`；老客户队列 `totalElements=48 / firstPage=12`；发布后未发现 CloudCC 登录失败、Token 获取失败或通用服务器错误。
- `cloudcc-skill`: pagecomponent V10 与 customPage V4 的组件 ID 均为 `6a503defe4b0a577cbba1f8a`；`actualVersions=[]` 仍触发既有 stale warning，属于已记录技能误报警。

## TASK-189 客户互动多模态采集生产验收（2026-07-11）

- `release`: dry-run、ACR 推送与 tag `2.4.8` 成功；运行版本为 `2.4.8 / 530ba01263b9`，V75 成功，六服务健康，Nginx 与公开三入口通过，稳定期错误扫描为空。
- `backup`: `/opt/cici/backups/20260711-161034-before-2.4.8-task189-multimodal-interaction` 的环境、PostgreSQL、KB 与 Qdrant 备份均非空。
- `production-api`: 真实截图批次 `cib_0a109c387d2e48f1afab2f864fdbc6e6` 达到 `READY`，图片资产达到 `READY`，OCR 提取 87 字符，鉴权原件请求返回 200。
- `production-confirm`: 首次确认创建事件 `cwi_e3e4cfaa8671f3cd14799c097512977c283137b9`，时间线回读命中 1 条；再次确认返回同一事件且 `deduplicated=true`。该事件作为一条有意保留的生产验收互动记录。
- `agent-browser`: 生产 AgentCiCi 组织“智能体平台演示环境”显示版本 `2.4.8`、CloudCC 已连接，多模态工作区打开正常；document/body X/Y overflow 均为 false，console error/warn 均为 0。截图：`output/playwright/task189-prod-platform-multimodal-2.4.8.png`。
- `cloudcc-browser`: 使用 `cc-customization-expert-msapi` 核验 pagecomponent V10/customPage V4 后，在真实 CloudCC Web 菜单打开客户互动工作台及多模态工作区；iframe docu…130621 tokens truncated…essionId}` and session cleanup logic for messages + session state.

- Conversation grouping and new-dialog entry verification (2026-04-27):
  - Commands:
    - `frontend`: `npm run build` -> **success**
  - Notes:
    - Added top-level "新对话" action in conversation list panel.
    - New draft sessions now keep a stable session id and are reused across multi-turn messages.
    - Session list refresh preserves unsent local drafts until first persisted turn is created.
    - Loading messages for newly created unsent sessions now gracefully handles `404` as empty history.

- Workbench session history alignment verification (2026-04-27):
  - Commands:
    - `frontend`: `npm run test -- src/assistant/workbenchSessions.test.ts` -> **success**
    - `frontend`: `npm run build` -> **success**
  - Notes:
    - Added `workbenchSessions.test.ts` to verify per-agent workbench session id generation and chronological history extraction.
    - `AssistantApp` workbench now fetches persisted session messages via `/ai/sessions/{sessionId}/messages` and refreshes after send completion.
    - Workbench right-side history uses the same message source as the main chat stream to avoid mismatch.

- FEAT-006 backend stream protocol verification (2026-04-25):
  - Commands:
    - `backend`: `mvn -q -DskipTests compile` -> **success**
    - `frontend`: `npm run build` -> **success**
  - Notes:
    - `ChatOrchestratorService.chatStream` now emits FEAT-006 scene events:
      - `avatar_state` (`thinking` / `speaking` / `idle`)
      - `task_created`
      - `task_status`
      - `task_delta`
      - `task_done`
    - Tool phase now updates task status and emits `waiting_user` when approval tool path is hit.
    - Existing stream events remain compatible (`delta` / `tool_call` / `tool_result` / `phase` / `done` / `error`).

- FEAT-006 virtual human scene MVP frontend verification (2026-04-25):
  - Commands:
    - `frontend`: `npm run build` -> **success**
  - Notes:
    - Extended `streamAiChat` with unknown-event passthrough callback to support task/avatar SSE event handling.
    - `AssistantApp` scene page now consumes stream events and drives:
      - avatar states (`idle` / `listening` / `thinking` / `speaking`)
      - task cards (`task_created` / `task_status` / `task_delta` / `task_done`)
      - text and voice input linked to the same stream runtime path.
    - Updated immersive scene CSS for avatar motion states, task card stack, mic active state, and send/notice controls.

- MCP admin scope + cache snapshot smoke closure (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=McpServerIntegrationTest,OrchestratorIntegrationTest,ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - Added `McpServerIntegrationTest.shouldRejectOrgUserAndAllowOrgAdminForMcpServerApis`:
      - verifies `/mcp-servers` rejects `ORG_USER`
      - verifies `/mcp-servers` allows `ORG_ADMIN`
    - Added `McpServerIntegrationTest.shouldKeepCachedSnapshotWhenDiscoverRefreshFails`:
      - verifies cache-miss path can discover tools
      - verifies discover failure response is returned
      - verifies previously discovered snapshot remains readable after failure

- FEAT-002 session reducer precision + no-repeat regression (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> **BUILD SUCCESS**
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - `ChatSessionStateService` now enriches session state from user turns with stable fields:
      - `current_object_type`
      - `current_object_name`
      - `target_segment_summary`
      - `missing_fields`
      - `next_action`
      - `no_repeat_questions`
    - Reducer now sets deterministic no-repeat constraint marker when user says “不要再重复问”.
    - Added integration coverage `shouldCaptureSessionFieldsAndNoRepeatConstraintAcrossTurns` to verify:
      - second-turn continuity under same `sessionId`
      - no-repeat constraint state persistence
      - `missing_fields` does not regress to `target_segment` when segment already present.

- Structured ioPayload + fallback/invalid replay assertions (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> **BUILD SUCCESS**
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - `nodeMetrics` now includes structured I/O payload:
      - `ioPayload.input`
      - `ioPayload.output`
    - Added fallback replay coverage:
      - `shouldExposeFallbackReplayMetadataInDebugRuntime`
    - Added invalid runtime replay coverage:
      - `shouldExposeInvalidReplayMetadataInDebugRuntime`
    - Existing published chat/debug assertions now verify both `ioSummary` and `ioPayload`, plus replay hints.

- Replayable node io-summary protocol (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> **BUILD SUCCESS**
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - `contextSnapshot.nodeMetrics` now includes per-node `ioSummary`:
      - `ioSummary.input`
      - `ioSummary.output`
    - `contextSnapshot` now includes `replayHint` for ordered replay guidance.
    - Both runtime paths expose the same replay-oriented fields:
      - `/agents/{agentId}/debug` -> `contextSnapshot`
      - `/ai/chat` -> `runtimeExecution.contextSnapshot`
    - Integration assertions updated to verify `replayHint` and `ioSummary` presence in:
      - `shouldUsePublishedWorkflowInDebugRuntime`
      - `shouldExposePublishedRuntimePolicyInChatResponse`

- Runtime execution metrics snapshot (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> **BUILD SUCCESS**
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - `contextSnapshot` now includes execution-time metrics and error protocol fields:
      - `branchHit`
      - `nodeMetrics` (`nodeId`, `costMs`, `status`)
      - `errorNode`
      - `errorType`
    - Metrics are exposed in both runtime paths:
      - `/agents/{agentId}/debug` -> `contextSnapshot`
      - `/ai/chat` -> `runtimeExecution.contextSnapshot`
    - Integration assertions updated to verify metrics snapshot presence in:
      - `shouldUsePublishedWorkflowInDebugRuntime`
      - `shouldExposePublishedRuntimePolicyInChatResponse`

- Runtime context snapshot projection (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> **BUILD SUCCESS**
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - Execution results now include `contextSnapshot` for both debug and chat runtime paths.
    - `/agents/{agentId}/debug` response now contains `contextSnapshot`.
    - `/ai/chat` response now contains `runtimeExecution.contextSnapshot`.
    - Snapshot fields include minimal runtime state projection:
      - `runtimeSource`
      - `inputRoute`
      - `toolScopeSize`
      - `intent`
      - `parsedNodes`
      - `knowledgeUsed`
      - `toolInvoked`
      - `responsePlanned`
    - Integration assertions updated:
      - `shouldUsePublishedWorkflowInDebugRuntime`
      - `shouldExposePublishedRuntimePolicyInChatResponse`

- Debug runtime minimal executor output (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> **BUILD SUCCESS**
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - `AgentWorkflowRuntimeService` now executes a minimal controlled runtime path in debug mode (published/fallback/invalid branches) instead of fixed `simulated-runtime`.
    - `/agents/{agentId}/debug` now returns structured execution fields:
      - `executionStatus`
      - `executionOutput`
    - `OrchestratorIntegrationTest.shouldUsePublishedWorkflowInDebugRuntime` now also verifies `executionStatus=published-executed` and non-empty published execution output.

- Chat runtime execution visibility (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> **BUILD SUCCESS**
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - `/ai/chat` now includes `runtimeExecution` payload with:
      - `status`
      - `output`
      - `publishedVersionId`
    - `OrchestratorIntegrationTest.shouldExposePublishedRuntimePolicyInChatResponse` now additionally verifies chat response reports `runtimeExecution.status=published-executed` with published execution output text.

- Node-level runtime trace projection (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> **BUILD SUCCESS**
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - `AgentWorkflowRuntimeService` now emits standardized node-level trace steps in minimal executor path:
      - `workflow-node:start`
      - `workflow-node:route-input:*`
      - `workflow-node:tool-scope:size=*`
      - `workflow-node:end:*`
    - `/agents/{agentId}/debug` now returns `executionTrace`.
    - `/ai/chat` now returns `runtimeExecution.trace`.
    - Integration assertions updated:
      - `shouldUsePublishedWorkflowInDebugRuntime`
      - `shouldExposePublishedRuntimePolicyInChatResponse`

- Workflow-code-driven node extraction (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> **BUILD SUCCESS**
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - Runtime now parses published `workflow_code` (`runAgent` body) to project code-level nodes into execution trace.
    - Extracted node markers include:
      - `intent-classify`
      - `knowledge-search`
      - `handoff-request`
      - `tool-invoke-best`
      - `response-generate`
    - Integration assertions updated to verify trace now includes parsed code node marker `workflow-node:code:intent-classify`.

- CiCi session continuity + state layer phase-1 implementation (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> **BUILD SUCCESS**
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - Flyway migrated to `v22` (`V22__chat_session_state.sql`) in integration test context.
    - Verified `GET /ai/sessions/{sessionId}/state` returns persisted session state after a user intent turn (`先添加名单，先不要发邮件`) via `OrchestratorIntegrationTest.shouldPersistSessionStateAfterUserIntentHint`.
    - Verified chat realtime stream integration still passes after `ChatOrchestratorService` message assembly changes.

- Runtime binding to published workflow dependencies (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> **BUILD SUCCESS**
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - `SkillResolverService` now reads `agent_definition.published_version_id` and prefers `workflow_manifest.dependencies` as runtime capability boundaries when publish status is `PUBLISHED`.
    - Added integration coverage `shouldPreferPublishedWorkflowDependenciesAtRuntime` to confirm runtime uses published dependency boundaries.
    - Regression confirms realtime chat stream path is still green after runtime resolver change.

- Session continuity 2-turn + published-version three-state regression (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> **BUILD SUCCESS**
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - Added `shouldKeepSessionStateAcrossSecondTurn`: verifies same `sessionId` two-turn conversation keeps session state (`hold_action` + `continue_current_plan`) and persists full turn history.
    - Added `shouldSwitchRuntimeDependenciesAcrossPublishStates`: verifies runtime dependency boundary transitions across three states:
      - publish V1 (`skillRefs=sales-copilot`) -> runtime tools contain CloudCC path,
      - publish V2 (`skillRefs=web-search`) -> runtime tools switch to Tavily path,
      - rollback to V1 -> runtime tools revert to CloudCC path.

- Invalid published-manifest runtime resilience (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> **BUILD SUCCESS**
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - Added integration coverage `shouldGracefullyHandleInvalidPublishedManifest`.
    - Test forces a published version with invalid `workflow_manifest` JSON and verifies chat runtime still responds successfully without crashing.

- Published runtime policy injection (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> **BUILD SUCCESS**
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - `SkillResolverService` now parses `workflow_manifest.policies.maxToolCalls` and `publishedVersionId` into runtime context.
    - `ChatOrchestratorService` now applies published `maxToolCalls` (bounded) to tool-loop rounds and returns `runtimePolicy` in `/ai/chat` response.
    - Added integration coverage `shouldExposePublishedRuntimePolicyInChatResponse` to verify published policy values are visible and effective in runtime payload.

- Debug runtime uses published workflow version (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> **BUILD SUCCESS**
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - Added `AgentWorkflowRuntimeService` as debug runtime entry for agent workflow execution context.
    - `/agents/{agentId}/debug` now returns runtime metadata:
      - `runtimeSource` (`published_version` / `capability_fallback`)
      - `publishedVersionId`
      - `workflowCodePreview`
    - Added integration coverage `shouldUsePublishedWorkflowInDebugRuntime` to verify debug runtime prioritizes published workflow versions.

- Skill authoring fallback alignment for campaign workflow (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillAuthoringIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - Verified the no-model fallback path no longer relies on approval/CRM/contract built-in templates as the primary generation strategy.
    - New integration coverage confirms:
      - 通用审批需求会保留原始风险事实与输出要求，而不是强绑定某个内置模板编码。
      - 营销活动需求会保留自定义工具名、编号步骤和 `email_send`，且不会被误导成 `CRM 线索分诊`。
    - Logs confirm local default path still has no configured `skill-authoring` model (`Aliyun API key is not configured.`), so this verification specifically proves the generic fallback path is working.

- Admin console SMS login bootstrap admins (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=AuthFlowIntegrationTest,ManagementConsoleIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - Default `application.yml` now sets `app.auth.bootstrap-admin-mobiles` to include `13900009999`, matching README / `application-local.yml` demo behavior when the process runs without `spring.profiles.active=local`.

- MCP chat tool exposure fix + H2 migration compatibility (2026-04-23):
  - Commands:
    - `backend`: `mvn -q -DskipTests compile` -> **success**
    - `frontend`: `npm run build` -> **success**
    - `backend`: `mvn -q -Dtest=OrchestratorIntegrationTest test` -> **BUILD SUCCESS**
    - `backend`: `mvn -q -Dtest=ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - Verified legacy tool ids no longer cause the approval-agent tool whitelist to collapse to empty; `/agents/approval-agent/debug` now resolves `get_pending_approvals` instead of the stale alias `approval-fetch`.
    - `V21__mcp_server_tool_cache_fields.sql` now runs successfully in H2 test context, restoring SpringBoot integration test startup.
    - `McpServerService.getTools(...)` now attempts a one-shot cache refresh when both memory cache and database snapshot are absent, reducing first-chat MCP empty-catalog failures.

- System MCP server cache implementation (2026-04-23):
  - Commands:
    - `backend`: `mvn -q -DskipTests compile` -> **success**
    - `frontend`: `npm run build` -> **success**
  - Notes:
    - Added persistent MCP tool snapshot fields on `mcp_server` via `V21__mcp_server_tool_cache_fields.sql`.
    - `McpServerService` now separates cache read (`getTools`/`getToolCacheSnapshot`) from forced refresh (`refreshToolCache` / `/discover`) and keeps old snapshots on refresh failures.
    - Admin tools page now shows MCP cache summary (`工具数 + 更新于 + 缓存状态`) in both list and detail tabs, and detail tab reads `GET /mcp-servers/{id}/tools` by default.

- System MCP cache runtime smoke attempt (2026-04-23, superseded by 2026-04-30 runtime closure):
  - Commands:
    - `POST /auth/sms/send` with `mobile=13900009999` -> `SMS request too frequent, please retry later`
    - `POST /auth/sms/send` + `POST /auth/sms/login` with `mobile=13800138111` -> login success (`roles=["ORG_ADMIN"]`)
    - `GET /mcp-servers` with `13800138111` token -> `{"success":false,"message":"需要组织管理员权限"}`
    - `POST /mcp-servers` with `13800138111` token -> `{"success":false,"message":"需要组织管理员权限"}`
  - Result:
    - Real runtime smoke for MCP cache flow is **blocked** in current local auth/session state.
  - Notes:
    - This blocker is tracked in `.claw/issue-list.md` as `ISSUE-2026-04-23-mcp-smoke-blocked-by-admin-auth-scope`.
    - Build-level verification remains green (`backend compile`, `frontend build`).

- Skill Creator model-driven authoring compiler (2026-04-23):
  - Commands:
    - `backend`: `mvn -q -DskipTests compile` -> **success**
    - `backend`: `mvn -q -Dtest=SkillAuthoringIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - `BuiltinSkillCreatorService` now attempts model-driven structured draft generation first and falls back to heuristic generation when model output is unavailable/invalid.
    - Structured output is still normalized by `SkillSpecSchemaValidator` and org candidate whitelist checks to keep compatibility and safety boundaries.

- Skill Authoring Phase 2 (authoring session + clarification loop) (2026-04-23):
  - Commands:
    - `backend`: `mvn -q -Dtest=SkillAuthoringIntegrationTest test` -> **BUILD SUCCESS**
    - `frontend`: `npm run build` -> **success**
  - Notes:
    - Flyway migrated to `v20` (`V20__skill_authoring_session.sql`) in test context.
    - New integration coverage: `shouldMergeClarificationAnswersWithinAuthoringSession` (UTF-8 response parsing for MockMvc).
    - Regression coverage still includes generate/refine/create + hidden creator visibility checks.

- Skill Authoring design gap implementation verification (2026-04-23):
  - Commands:
    - `backend`: `mvn -q -Dtest=SkillAuthoringIntegrationTest test` -> **BUILD SUCCESS**
    - `frontend`: `npm run build` -> **success** (`tsc -b && vite build`)
  - Notes:
    - Flyway successfully migrated to `v19` (`V19__skill_authoring_source_fields.sql`) in integration test context.
    - Verified new persistence fields are schema-valid with existing test suite and do not break authoring generate/refine/create flow.

- V18 migration + full backend test suite restoration (2026-04-22):
  - Commands:
    - `backend`: `mvn test` → **Tests run: 21, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS**
  - Breakdown:
    - `AuthFlowIntegrationTest` 6/6
    - `ManagementConsoleIntegrationTest` 1/1
    - `ChatRealtimeIntegrationTest` 1/1 (after fixing `event:connected` assertion typo)
    - `OrchestratorIntegrationTest` 2/2 (after using distinct admin mobiles + `callCount >= 1` to tolerate shared Spring test context)
    - `TavilyToolServiceTest` 10/10
    - `TavilyCatalogIntegrationTest` 1/1 — verifies `/tools` catalog exposes `tavily_search` + `tavily_extract` as builtins, `/skills` exposes `web-search` with Tavily `toolWhitelist`, and `/skills/agents/cici-system/bindings` auto-binds `web-search` with `activationMode=intent-route`
  - Notes:
    - Fix lands as a V18 rewrite (cross-DB `TIMESTAMP` + entity-aligned `user_id VARCHAR(64)` + regular `UNIQUE INDEX` instead of partial) plus two cascading preexisting test-design fixes. All recorded in `.claw/issue-list.md` → ISSUE-2026-04-22-v18-migration-blocks-h2-integration-tests (resolved).
    - Previously the entire `@SpringBootTest` layer was unreachable because Flyway aborted on V18; today every integration test that existed before Tavily work is green again, and the new Tavily integration test is online.

- Tavily Search + Extract built-in skill integration (Phase 1):
  - Commands:
    - `backend`: `mvn test -Dtest=TavilyToolServiceTest` → **Tests run: 10, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS**
    - `backend`: `mvn test -Dtest=TavilyCatalogIntegrationTest` → **Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS**
    - `backend`: `mvn -q test-compile` → success (whole project compiles clean)
    - `frontend`: `npm run build` → success (321 modules, no TS errors)
  - Covered by `TavilyToolServiceTest`:
    1. `toolDefinitions()` returns OpenAI-style `tavily_search` + `tavily_extract` with required params (`query` / `urls`) and enum constraints.
    2. Dispatching `tavily_search` without an `integration_app('tavily')` row returns `TAVILY_NOT_CONFIGURED`.
    3. Dispatching `tavily_extract` without config returns `TAVILY_NOT_CONFIGURED`.
    4. `max_results` is clamped to `[1, 20]` and missing values fall back to `properties.defaultMaxResults()`.
    5. `search_depth` / `topic` / `include_answer` / `include_raw_content` enums fall back to defaults on invalid input.
    6. Upstream non-2xx from Tavily is surfaced as `TAVILY_UPSTREAM_ERROR` with truncated body.
    7. `urls` with >20 entries is trimmed to exactly 20 before hitting Tavily.
    8. Queries >400 chars are truncated (no upstream rejection).
    9. `tavily_extract` successful shaping truncates `raw_content` to `properties.maxExtractChars()` and records original length.
    10. `IntegrationAppService.update("tavily", apiKey=...)` encrypts the key via `SecretCipherService` and masks it as `tvly-****` in the view, while preserving the stored cipher when the client resubmits the masked sentinel.
  - Notes:
    - Unit tests use hand-rolled fakes for `TavilyClient` + `IntegrationAppRepository` instead of Mockito to avoid JDK 25 inline-mock instrumentation issues.
    - Integration test `TavilyCatalogIntegrationTest` verifies the full wiring through HTTP: `/tools`, `/skills`, `/skills/agents/cici-system/bindings`.


- Frontend build (login preview conversation demo):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - 登录页左上欢迎区已从静态 `boot-lines` 文案升级为动态演示卡片，包含访客提问、思考摘要、流式回答三个阶段。
    - 新增 `BootLoginConversationDemo` 组件，文案动画由前端本地状态驱动，不依赖登录接口或后端实时数据。
    - 已补充独立播放轮次状态，单条场景在播完后也会自动重启，持续循环展示对话。
    - 已删除对话文案中的“通过 Slack”，并修正登录框定位逻辑：从全屏 flex 对齐改为右侧固定浮层，避免与左侧演示区视觉重叠。
    - `frontend/src/styles.css` 已补充该区域的终端式赛博视觉与移动端单列布局。
    - 登录页副标题文案已改为更贴近当前系统能力的产品表达，突出专属数字员工、企业知识库、工作流与工具、审批推进等能力。
    - 登录页副标题已进一步压缩，并改为“7x24 小时在线协作”表述，避免产生“登录后才唤醒”的语义偏差。
    - 登录页副标题已补充“记忆系统”和“自定义 Skill”能力点，使描述更贴近当前产品能力边界。
    - Vite chunk-size warning remains informational.

- Agent/Skill entry refactor Phase 1 compile/build verification:
  - Command:
    - `backend`: `mvn -q -DskipTests compile`
    - `frontend`: `npm run build`
  - Result: success
  - Notes:
    - 后端新增 `/agents/{agentId}/skills` 读写接口，并在 agent 详情返回中携带 `skillBindings`。
    - 前端 Agent Builder 已显式传递 `skillRefs`，并把 skills 与其他 draft 一起保存。
    - AdminSkillsPage 已移除 agent 绑定管理区块，收口为 skill 资产中心。

- Agent/Skill capability unification + debug trace verification:
  - Command:
    - `backend`: `mvn -q -DskipTests compile`
    - `frontend`: `npm run build`
  - Result: success
  - Notes:
    - 新增 `AgentCapabilityResolverService`，统一计算 effective skills/tools/kbs/handoff/outputContract。
    - `AgentCompileService` 已改为复用统一 resolver，并在 warning 中输出 skill-agent 边界冲突提示。
    - 新增 `POST /agents/{agentId}/debug`，返回 active skills、effective scope、trace steps 与 warnings。
    - Agent Builder 调试面板已接入 debug 接口并展示 active skills。

- User Workflow page/load + compile/publish regression fix:
  - Command:
    - `backend`: `mvn -q -DskipTests compile`
    - restart backend on `8080` with `mvn -Dmaven.repo.local=.m2 spring-boot:run -Dspring-boot.run.profiles=local`
    - `GET /me/agents/cici-system/workflow`
    - `POST /me/agents/cici-system/workflow/compile`
    - `POST /me/agents/cici-system/workflow/publish`
  - Result: success
  - Notes:
    - Verified the page-load 500 root cause was a null-bearing `Map.of(...)` in `UserWorkflowController.get(...)`; after switching to `LinkedHashMap`, `GET /me/agents/cici-system/workflow` succeeds again.
    - Verified the publish failure root cause was false-positive time parsing: text containing `8080` was previously compiled into invalid hour `80`; after tightening `inferTrigger(...)` and guarding `computeNextFire(...)`, the same text compiles as `MANUAL` and publishes successfully.
    - Final state check:
      - API `GET /me/agents/cici-system/workflow/versions` shows `v4 -> PUBLISHED`
      - DB `user_workflow_version` shows `version_no=4, publish_status=PUBLISHED`

- User Workflow Phase 1.5 Feishu DM end-to-end smoke:
  - Command:
    - `PUT /me/agents/cici-system/workflow/profile` with `notificationTarget={"type":"feishu_dm","value":""}`
    - `POST /me/agents/cici-system/workflow/compile` with `sourceText="测试飞书私信送达 smoke"`
    - `POST /me/agents/cici-system/workflow/publish` with `versionNo=1`
    - `POST /me/agents/cici-system/workflow/run-now` with `routineKey="routine-1"`
  - Result: success
  - Notes:
    - Test user `18611892001` had an active Feishu binding (`open_id=ou_efc396f23aec3375205d2fc72a5bcf54`), and `demo-org` already had an enabled `feishu_bot` integration config.
    - `run-now` returned execution `status=SUCCESS`; trace notification node returned `status=SENT`, `targetType=feishu_dm`, and message `已通过飞书私信主动发送执行结果。`
    - This validates the local runtime path: execution complete -> resolve bound `open_id` -> proactive Feishu DM send.

- Feishu DM progress review recheck:
  - Command:
    - `backend`: `mvn -q -DskipTests compile`
    - `frontend`: `npm run build`
  - Result: success
  - Notes:
    - 代码复核确认：`FeishuBotMessenger.sendTextToOpenId(...)` 与 `UserWorkflowService.deliverNotification(...)` 已接入个人工作流执行完成后的主动私信发送链路。
    - 前后端在当前仓库状态下仍可通过编译/构建，说明这条链路至少通过了静态集成层面的验证。
    - 当时发现的状态口径滞后项已在后续会话中修正并完成真实 `run-now` smoke。

- Feishu pairing entry frontend build:
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Added a user-facing Feishu pairing section to `MyWorkflowStudio`.
    - The settings page now supports:
      - reading current pairing status
      - generating a one-time pairing code
      - copying the pairing command
      - unbinding the current Feishu account
    - Build completed successfully; Vite chunk-size warning remains informational.

- User Workflow Phase 1.5 backend compile:
  - Command: `mvn -q -DskipTests compile`
  - Result: success
  - Notes:
    - `FeishuBotMessenger` now supports proactive text send by `open_id`, in addition to reply-by-message-id.
    - `UserWorkflowService` now attempts real Feishu DM delivery when `notificationTarget.type = feishu_dm`.
    - When the profile has no explicit target value, runtime falls back to the current user's active Feishu binding if present.
    - Delivery failures are captured into execution trace/output instead of being swallowed.

- User Workflow Phase 1.5 frontend build:
  - Command: `npm run build`
  - Result: success
  - Notes:
    - `MyWorkflowStudio` now explains that the Feishu target may be left blank and the system will try to reuse the current user's bound Feishu `open_id`.
    - Build completed successfully; Vite chunk-size warning remains informational.

- User Workflow Phase 1.5 runtime smoke:
  - Command: pending
  - Result: not yet executed
  - Notes:
    - Real end-to-end validation still needs an org with a working Feishu bot config plus a user who already has an active Feishu binding.
    - Recommended next smoke: publish a personal workflow with `notificationTarget.type=feishu_dm`, leave target empty, then call `/me/agents/cici-system/workflow/run-now` and confirm message delivery.

- User Workflow Phase 1 backend compile:
  - Command: `mvn -q -DskipTests compile`
  - Result: success
  - Notes:
    - Added `V17__user_workflow_tables.sql` and five user-scoped workflow tables:
      - `user_agent_profile`
      - `user_workflow_spec`
      - `user_workflow_version`
      - `user_workflow_trigger`
      - `user_workflow_execution`
    - Added user-side workflow APIs under `/me/agents/{agentId}/workflow/**` for:
      - profile/spec update
      - compile / versions / publish / rollback
      - trigger list/update
      - run-now / debug / executions
    - Added `@EnableScheduling` and `UserWorkflowScheduler` for due-trigger scanning.
    - Build verifies source compatibility of the new user workflow domain/service/controller layer.

- User Workflow Phase 1 frontend build:
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Avatar entry now opens a broader personal-settings modal instead of a mailbox-only view.
    - Added `MyWorkflowStudio` with:
      - personal workflow settings
      - natural-language Spec editor
      - compile / publish / rollback actions
      - trigger list and manual run
      - recent execution records
    - Existing mailbox management remains available as a sibling tab.
    - Vite chunk-size warning remains informational.

- User Workflow Phase 1 runtime scope note:
  - Command: N/A
  - Result: partial by design
  - Notes:
    - This phase now records notification targets and execution summaries, but proactive Feishu direct-message delivery is not yet wired to a real active-send API.
    - Routines backed by unavailable tools (for example news aggregation or meeting invitation) are preserved and executed as tracked skeletons with notes in execution output rather than silently failing.

- Frontend build (workbench viewport lock / no page-scroll structure pass):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Added viewport-level width/height/max-width/overflow constraints from `.cici-app` down through the workbench canvas/layout chain.
    - Replaced some fixed-width and `calc(100vh - padding)` style constraints with parent-height-based layout sizing to reduce whole-page overflow risk.
    - Tightened workbench top-bar and sidebar column limits to reduce horizontal spillover.
    - Build completed successfully; Vite chunk-size warning remains informational.

- Frontend build (workbench compact styling + no page scrollbar pass):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Workbench palette was pulled back toward the main system's light gray/white surface tokens instead of the earlier beige-tinted prototype look.
    - Typography, avatar sizes, paddings, and card spacing were reduced to produce a denser workbench layout.
    - Workbench container heights and overflow handling were adjusted to avoid page-level scrollbars, and the top bar columns were constrained so the state machine card no longer covers the agent list.
    - Build completed successfully; Vite chunk-size warning remains informational.

- Frontend build (workbench layout aligned 1:1 to prototype):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Workbench layout was reworked to mirror `frontend/public/agent-workbench-prototype.html` structure: top dock strip, top-right state card, left chat panel, right overview/history sidebar.
    - Workbench dock now uses local UI keys with `runtimeAgentId` mapping so visual dock expansion does not break existing chat runtime.
    - Existing workbench stream chat path remains active; only the page structure and workbench-local state model changed.
    - Build completed successfully; Vite chunk-size warning remains informational.

- Frontend build (workbench state-machine Phase 1 refactor):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - `AssistantApp.tsx` workbench view was refactored from hero layout into `Dock + 状态机条 + 主对话区 + 右侧概览/历史`.
    - Workbench messages are now stored per agent, so switching the active dock keeps each agent's workbench dialogue state intact.
    - Existing stream chat path remains active in workbench mode, and `agentId` still follows the selected agent during request submission.
    - Build completed successfully; Vite chunk-size warning remains informational.

- Email tool module (2026-04-19):
  - `mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` -> success
  - `mvn -q -Dmaven.repo.local=.m2 -DskipTests test-compile` -> success
  - `npm run build` (frontend) -> success
  - Scope verified by compilation only:
    - `V16__email_account_table.sql` migration is present.
    - `EmailAccountEntity/Repository`, `SecretCipherService`, `EmailProviderRegistry`, `EmailAccountService`, `EmailToolService`, `EmailAccountController`, `ToolOrchestratorService` wiring, `ToolController.list()` merge all compile.
    - Frontend `MyEmailAccountsModal`, `AgentBuilderShell.TOOL_CATALOG` additions, Vite proxy for `/me`.
  - Runtime smoke (POP3 login / SMTP send / `GET /tools` response / Agent Builder selecting `email_*`) still pending: needs a user mailbox with valid credentials and a running backend.

- Backend compile (Agent Builder publish/rollback + compile-version persistence):
  - Command: `mvn -q -DskipTests compile`
  - Result: success
  - Notes:
    - `AgentCompileService` now persists draft versions into `agent_workflow_version` on `/agents/{agentId}/compile`.
    - Added agent version governance APIs (`/agents/{agentId}/versions`, `/publish`, `/rollback`).
    - Added publish config API (`PUT /agents/{agentId}/publish-configs`) and persistence wiring.
- Frontend build (Agent Builder real save/publish/rollback wiring):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Agent Builder now attempts backend-first loading for `/agents` and details.
    - Save action now calls real backend persistence endpoints for definition/spec/bindings/publish configs.
    - Added publish/rollback actions in the builder header and compile success notice with `draftVersionNo`.
- Runtime smoke (Agent Builder persistence APIs):
  - Command: start backend and call health + `/agents` related CRUD/compile/publish endpoints with real JWT
  - Result: blocked in current command sandbox
  - Notes:
    - Backend startup attempts in this environment repeatedly failed before serving HTTP due PostgreSQL connection errors (`SQL State 08001`, message: `尝试连线已失败。`).
    - Because app context could not fully initialize, this run did not produce valid endpoint smoke evidence.
    - Compile/build evidence above is valid; runtime smoke needs rerun in a stable DB-connectable environment.
- Backend compile (Agent Builder Phase1 persistence skeleton):
  - Command: `mvn -q -DskipTests compile`
  - Result: success
  - Notes:
    - Added `V15__agent_builder_persistence_phase1.sql` with agent persistence tables:
      - `agent_definition`, `agent_spec`, `agent_workflow_version`
      - `agent_kb_binding`, `agent_tool_binding`, `agent_channel_binding`, `agent_publish_config`
    - Added `AgentDefinitionController` admin APIs for persistence:
      - `POST /agents`, `GET /agents`, `GET /agents/{agentId}`, `PUT /agents/{agentId}`
      - `PUT /agents/{agentId}/spec`, `GET /agents/{agentId}/bindings`, `PUT /agents/{agentId}/bindings`
    - Added `AgentDefinitionService` and related repositories/entities for definition/spec/bindings write/read path.
    - Existing compile APIs (`/agents/compile`, `/agents/{agentId}/compile`) remain intact.
- Frontend build (Skill Studio v2 redesign + CRM template actions):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - `AdminSkillsPage` upgraded to a new console layout with hero metrics, CRM template strip, searchable skill cards, split editor, and binding workspace.
    - Added CRM template quick actions: apply-to-form and one-click create.
    - Build completed successfully; Vite chunk-size warning remains informational.
- Backend compile (built-in CRM skill seeds):
  - Command: `mvn -q -DskipTests compile`
  - Result: success
  - Notes:
    - Added 4 built-in CRM skills in `SkillDefinitionService` defaults:
      - `crm-lead-intake`
      - `crm-opportunity-health`
      - `crm-followup-orchestrator`
      - `crm-renewal-guard`
    - Skills are created lazily by existing `ensurePhaseOneDefaults(...)` flow when missing.
- Backend compile (SpecCompiler + SkillVersion phase A):
  - Command: `mvn -q -DskipTests compile`
  - Result: success
  - Notes:
    - Added `V14__skill_spec_compiler_phaseA.sql` migration (`draft_spec_text` + `skill_version`).
    - `SkillDefinitionService` now writes draft `skill_version` snapshots on create/update.
    - Added shared `SpecCompilerService` and connected both `AgentCompileService` and `SkillDefinitionService.previewCompile`.
    - `AgentCompileService` compile payload now includes `resolvedSkillRefs`.
- Backend test compile (phase A source compatibility):
  - Command: `mvn -q -DskipTests test-compile`
  - Result: success
  - Notes:
    - Confirms current test sources remain compilable after compile API and skill domain model expansion.
    - This run validates source compatibility only; it does not execute runtime assertions.
- Frontend build (skill draftSpecText form wiring):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - `AdminSkillsPage` now supports `draftSpecText` editing and preview/save request passthrough.
    - Build completed successfully; Vite chunk-size warning remains informational.

- Runtime smoke (Skill Phase 2 API end-to-end):
  - Command: `curl` against `/skills`, `/skills/{id}`, `/skills/preview`, `/skills/agents/{agentId}/bindings` with real JWT
  - Result: success (after one bugfix)
  - Notes:
    - Verified `ORG_USER` token is rejected on `/skills` with permission error (expected).
    - Verified `ORG_ADMIN` token can complete create/update/preview/binding/list/disable full flow.
    - Initial binding update returned `500` due unique constraint conflict on `agent_skill_binding`.
    - Root cause fixed by adding `agentSkillBindingRepository.flush()` after delete-before-insert in `replaceBindings(...)`.
    - Re-run verified binding update and readback succeeded (`count=5`, smoke skill present).
- Frontend build (Admin Skills page integration):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Added `/admin/skills` route and nav entry.
    - Added admin page for skill list/create/update/disable, compile preview, and agent binding updates.
    - Vite still reports chunk-size warning; build completed successfully.
- Backend compile (Skill Phase 2 backend APIs):
  - Command: `mvn -q -DskipTests compile`
  - Result: success
  - Notes:
    - Phase 2 core backend APIs compile successfully: skill CRUD, skill preview compile, and agent-skill binding management.
    - `SkillDefinitionService` / `SkillController` / `skill` repositories and entities compile with the new contracts.
- Backend test compile (Skill Phase 2):
  - Command: `mvn -q -DskipTests test-compile`
  - Result: success
  - Notes:
    - Existing test sources remain compilable after Phase 2 API/service expansion.
    - This run only verifies compilation; no runtime/integration assertions were executed.

- Runtime smoke (Skill Phase 1 agent routing + tool allowlist):
  - Command:
    - `curl -X POST /auth/sms/send` + `curl -X POST /auth/sms/login` to obtain JWT
    - `curl -X POST /ai/chat` with `agentId=cici-system`
    - `curl -X POST /ai/chat` with `agentId=sales-agent`
    - `curl -X POST /ai/chat` with `agentId=approval-agent`
  - Result: success
  - Notes:
    - `cici-system` returned `resolvedSkills=[conversation-core,knowledge-first,safe-handoff,general-assistant]`, `effectiveToolNames=[]`.
    - `sales-agent` returned `resolvedSkills=[conversation-core,knowledge-first,safe-handoff,sales-copilot]`, `effectiveToolNames=[cloudcc_getStandardObjects,cloudcc_getCustomObjects,cloudcc_getObjectFields,cloudcc_pageQuery]`.
    - `approval-agent` returned `resolvedSkills=[conversation-core,knowledge-first,safe-handoff,approval-assistant]`, `effectiveToolNames=[get_pending_approvals]`.
    - Confirms Phase 1 runtime behavior is effective in real API responses.
- Backend local startup (current source with local PostgreSQL):
  - Command: `mvn -Dmaven.repo.local=.m2 spring-boot:run -Dspring-boot.run.profiles=local`
  - Result: failed (default startup)
  - Notes:
    - Flyway validation failed: migration checksum mismatch on version `12`.
    - Error indicates local DB stored checksum differs from current `V12` file.
- Backend local startup (temporary workaround for smoke):
  - Command: `mvn -Dmaven.repo.local=.m2 spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments=--spring.flyway.validate-on-migrate=false`
  - Result: success
  - Notes:
    - Service started and applied `V13__skill_registry_phase1.sql` on the local DB.
    - Used only to complete runtime smoke; migration checksum issue remains open for proper fix.

- Backend compile (skill phase 1 implementation):
  - Command: `mvn -q -DskipTests compile`
  - Result: success
  - Notes:
    - Added `skill_definition` / `agent_skill_binding` phase 1 schema and `chat_session.agent_id`.
    - Added skill runtime services, prompt assembly, default built-in skill seeds, and agent-skill default bindings.
    - Chat orchestration now resolves skills at runtime and filters tool exposure by skill allowlist.
- Backend test compile (skill phase 1 integration test sources):
  - Command: `mvn -q -DskipTests test-compile`
  - Result: success
  - Notes:
    - Added `OrchestratorIntegrationTest` coverage for `agentId=sales-agent`, checking resolved skill metadata and effective tool allowlist.
    - Confirms new backend test sources compile with the current codebase.
- Frontend build (agentId passthrough for skill runtime):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - `streamAiChat` request body now supports optional `agentId`.
    - Assistant workbench now forwards the currently selected `activeAgent.id` into `/ai/chat/stream`.
- Backend targeted test execution (skill phase 1):
  - Command: `mvn -q -Dtest=OrchestratorIntegrationTest test`
  - Result: failed in this environment
  - Notes:
    - Fixed one real issue during this run: `V12__feishu_binding_profile_columns.sql` was not H2-compatible and blocked Spring Boot test startup; migration was rewritten as two `ALTER TABLE` statements and the application context then started successfully.
    - Remaining failure is environment-specific and occurs after context startup: Mockito inline Byte Buddy self-attach still cannot initialize on the current local JDK 25 runtime, so test execution aborts in the Spring Boot mock reset listener.

- Frontend build (fix stale message panel under session polling/cache):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - `AssistantApp.tsx` now force-refreshes conversation messages when active conversation changes.
    - 60-second fallback polling now refreshes both session list and current active conversation messages, preventing list/panel divergence when SSE reconnects or misses events.
- Runtime diagnosis (Feishu user profile sync):
  - Command: inspect backend runtime logs and `feishu_bot_binding` rows after receiving real Feishu message
  - Result: partial (root cause confirmed; feature gated by platform permission)
  - Notes:
    - Backend log shows Feishu contact API error: `code=41050`, `msg=no user authority error`.
    - `feishu_bot_binding.display_name` / `avatar_url` remain empty due missing Feishu-side user profile read permission, not due data pipeline failure.
- Backend compile (Feishu auto-binding without pairing code):
  - Command: `mvn -q -DskipTests compile`
  - Result: success
  - Notes:
    - Updated Feishu bridge path: when no active binding exists for `(orgId, tenantKey, openId)`, backend now auto-creates binding and continues conversation flow instead of returning pairing-code prompt.
    - Added fallback user resolution for auto-binding (prefer `ORG_ADMIN`, else latest available org user).
- Runtime verification (real Feishu -> agent -> Web realtime display, manual UAT):
  - Command: real Feishu single-chat with opened web workbench (manual user acceptance run)
  - Result: success
  - Notes:
    - Acceptance owner confirmed target achieved in-session.
    - Verified behavior: external Feishu message can be bridged to agent conversation and reflected in web workbench in realtime without manual refresh.
    - This closes the product acceptance gap previously tracked for live Feishu end-to-end confirmation.
- Runtime verification (session SSE stream + dual update events):
  - Command: restart backend on latest local code, then `GET /ai/sessions/stream` with valid JWT while calling `POST /ai/chat`
  - Result: success
  - Notes:
    - Initially observed `GET /ai/sessions/stream` -> `404` on old running backend process; after restart, endpoint returned `200` with `text/event-stream`.
    - SSE stream emitted `connected` first, then emitted `session_updated` for the same `sessionId` with both `trigger=user_message` and `trigger=assistant_message`.
    - This verifies the realtime event transport and server-side emit points in local runtime.
    - Remaining gap for product acceptance: one real Feishu single-chat to open Web workbench end-to-end verification.
- Backend compile (session realtime SSE sync):
  - Command: `mvn -q -DskipTests compile`
  - Result: success
  - Notes:
    - Added backend session realtime event hub and `GET /ai/sessions/stream`.
    - `ChatOrchestratorService` now commits and broadcasts user-message and assistant-message updates separately, so external-channel sessions can surface in the web workbench before and after CiCi replies.
- Backend test compile (realtime sync regression test source):
  - Command: `mvn -q -DskipTests test-compile`
  - Result: success
  - Notes:
    - Added `ChatRealtimeIntegrationTest` source covering `/ai/sessions/stream` subscription and `session_updated` event expectations.
    - Confirms the new test source compiles under the current project setup.
- Backend targeted test execution (realtime sync regression):
  - Command: `mvn -q -Dtest=ChatRealtimeIntegrationTest test`
  - Result: failed in this environment
  - Notes:
    - Failure occurs before the test body runs.
    - Root cause is environment-specific: Mockito inline Byte Buddy self-attach cannot initialize on the current local JDK 25 runtime, so Spring Boot test startup aborts.
- Frontend build (session realtime subscription):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Replaced the 10-second conversation refresh loop with a long-lived `/ai/sessions/stream` subscription plus automatic reconnect.
    - Workbench now force-refreshes the active conversation when `session_updated` arrives, while retaining a 60-second polling fallback.
    - Vite still reports a chunk-size warning for the production bundle, but the build completed successfully.
- Runtime verification (external-channel sessions visible to ordinary system users):
  - Command: log in as `13900009996` (`ORG_USER`), then call `GET /ai/sessions` and `GET /ai/sessions/{sessionId}/messages` against restarted backend
  - Result: success
  - Notes:
    - Verified the Feishu conversation is now visible without depending on the pairing user identity or admin role.
    - Confirms the new visibility model: external-channel sessions are org/agent-scoped, while personal workbench sessions remain user-scoped.
- Runtime verification (ORG_ADMIN visibility for Feishu sessions):
  - Command: query `chat_session` / `feishu_bot_binding` in local PostgreSQL, log in as `18611892001`, then call `GET /ai/sessions` and `GET /ai/sessions/{sessionId}/messages` against restarted backend
  - Result: success
  - Notes:
    - Verified the Feishu thread was already persisted under pairing user `13900009999`, not lost.
    - Verified the previous empty web list was caused by user-scoped session filtering while the current web login used another admin account (`18611892001 / Owen`).
    - After changing `ORG_ADMIN` visibility to org scope and restarting backend, `GET /ai/sessions` returned the Feishu session and the history endpoint returned the full “在吗” conversation.
- Backend compile (real conversation list + history APIs):
  - Command: `mvn -q -DskipTests compile`
  - Result: success
  - Notes:
    - Extended `/ai/sessions` to return richer conversation summaries required by the assistant workspace.
    - Added `/ai/sessions/{sessionId}/messages` for conversation history loading.
    - Verified backend compiles after wiring session summary parsing, latest-message lookup, and session ownership checks.
- Frontend build (real conversation list wiring):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Assistant workbench conversation list no longer depends on hardcoded `CONVERSATION_THREADS`; it now loads real `/ai/sessions` data after login.
    - Conversation detail pane now loads real history from `/ai/sessions/{sessionId}/messages`.
    - Added periodic conversation refresh so newly bridged external sessions, including Feishu sessions, can appear in the list without manual page reload.
    - Vite still reports a chunk-size warning for the production bundle, but the build completed successfully.
- Runtime verification (project status check + local startup):
  - Command: `docker compose up -d`, `mvn -Dmaven.repo.local=.m2 spring-boot:run -Dspring-boot.run.profiles=local`, `npm run dev`, then call `/actuator/health` and `/`
  - Result: success
  - Notes:
    - `docker compose ps` shows `cici-postgres`, `cici-redis`, `cici-rabbitmq`, `cici-qdrant` all up; postgres/redis/rabbitmq report healthy.
    - `GET http://127.0.0.1:8080/actuator/health` returned `{"status":"UP"}`.
    - `HEAD http://127.0.0.1:5173/` returned `HTTP/1.1 200 OK`.
    - Backend started with local profile and completed Flyway validation against PostgreSQL schema version 11.
- Backend compile (Feishu bot bridge status audit):
  - Command: `mvn -q -DskipTests compile`
  - Result: success
  - Notes:
    - Backend compiles with the Feishu SDK dependency and the current Feishu bot bridge classes in place.
    - This verifies the code-level integration path is buildable, but does **not** prove real Feishu runtime connectivity.
- Frontend build (Feishu pairing UI status audit):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Assistant workbench pairing UI and admin integration configuration UI both compile into the production bundle.
    - Vite still reports a chunk-size warning for the production bundle, but the build completed successfully.
- Frontend build (workbench chat alignment fix):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Changed the workbench message list from CSS grid to a vertical flex column so message rows no longer stretch across leftover height.
    - User messages are now explicitly right-aligned inside the workbench, with a bounded message width better suited to the wider dashboard layout.
    - Tightened the workbench composer action group so the voice and send buttons sit closer together and read as a single control cluster.
- Frontend build (send-code proxy target correction):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - `frontend/vite.config.ts` and `frontend/vite.config.js` now default the dev proxy target to `http://127.0.0.1:8080`.
    - Added `VITE_BACKEND_TARGET` override support so remote backend targets can still be used without hardcoding a LAN IP.
    - `vite.config.ts` switched to Vite `loadEnv(...)` so the TypeScript config can read `VITE_BACKEND_TARGET` without relying on Node typings.
    - Restarted the Vite dev server after the config change so the new proxy target took effect immediately.
- Runtime verification (send-code chain recovery):
  - Command: start backend with `mvn -Dmaven.repo.local=.m2 spring-boot:run -Dspring-boot.run.profiles=local`, then call `/actuator/health`, `/auth/sms/send` via both `8080` and `5173`
  - Result: success
  - Notes:
    - `GET http://127.0.0.1:8080/actuator/health` returned `{"status":"UP"}`.
    - `POST http://127.0.0.1:8080/auth/sms/send` returned `200 OK` with `devCode`.
    - `POST http://127.0.0.1:5173/auth/sms/send` returned `200 OK` with `devCode` after restarting the Vite dev server.
    - Repeating the request for the same mobile now returns `400 SMS request too frequent, please retry later`, confirming the request reaches backend rate limiting instead of failing in the dev proxy layer.
- Frontend build (workbench voice/chat/tool reconnect):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Workbench now reuses the original `streamAiChat` submit path and `/ws/asr` speech-input path instead of behaving like a static dashboard.
    - Task cards, approval cards, and quick actions can now trigger the same conversation pipeline that the original CiCi page used.
    - Added an approval drawer with `iframe` rendering so `get_pending_approvals` tool results can surface inside the workbench.
    - Attempted runtime verification against local backend, but `127.0.0.1:8080` was not listening in this session, so real end-to-end verification could not be completed.
- Frontend build (assistant hierarchy realignment):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Assistant workspace is now organized around `Agent -> Conversation -> Message` instead of a flat mixed session list.
    - Added an agent directory, agent-scoped conversation thread list, richer chat header context, and a right-side structure summary.
    - Added `docs/agent-conversation-hierarchy-design.md` and synced long-lived project docs/state files to this new IA direction.
    - Vite still reports a chunk-size warning for the production bundle, but the build completed successfully.
- Frontend build (Dify-style workflow preview canvas + minimap/zoom + channel merge):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Replaced the old Mermaid-style workflow preview surface with a Dify-inspired read-only canvas built from preview `nodes / edges`.
    - Added zoom controls, fit-to-canvas behavior, and a clickable minimap with viewport box.
    - Moved `发布渠道` into the `Agent 定义` section so it no longer occupies its own standalone card.
    - Vite still reports chunk-size warnings for production build output; the build completed successfully.
- Frontend build (compact layout + tabbed compiler workspace):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Removed the right-side builder info column and converted the page into a tighter single-main-workspace layout.
    - Replaced the stacked compile result layout with tabs; default active tab is the workflow preview graph.
    - Condensed the header from large KPI cards into compact meta chips and a shorter status notice.
    - Vite still reports chunk-size warnings for production build output; the build completed successfully.
- Backend runtime verification (after restart to latest code):
  - Command: stop old 8080 Java process, restart backend with `mvn -Dmaven.repo.local=.m2 spring-boot:run -Dspring-boot.run.profiles=local`, then call health/auth/compile APIs
  - Result: success
  - Notes:
    - `/actuator/health` returned `UP` after restart.
    - SMS login for `demo-org` succeeded and issued a valid assistant token.
    - `POST /agents/compile` returned `workflowCode`, `workflowManifest`, and `workflowPreview`, confirming the newly added compile API is live in the running backend.
- Frontend build (debug path highlight on workflow preview):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Added a read-only debug panel that accepts test input, simulates a path, and highlights matched nodes on the Mermaid workflow preview.
    - This iteration keeps debug execution on the frontend side, but the display layer is ready for a future real debug API.
    - Vite still reports chunk-size warnings for production build output; the build completed successfully.
- Backend compile (agent compile API skeleton):
  - Command: `mvn -DskipTests compile`
  - Result: success
  - Notes:
    - Added `AgentCompileController` with `POST /agents/compile` and `POST /agents/{agentId}/compile`.
    - Added `AgentCompileService` to generate `workflowCode`, `workflowManifest`, `workflowPreview`, `compileSummary`, `warnings`, and `dependencies`.
- Frontend build (real compile API wiring with workflow preview fallback):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Agent Builder now passes assistant token into the builder shell and prefers the real `/agents/{id}/compile` API when compiling.
    - If the compile API is unavailable, the UI falls back to the existing frontend simulated compiler so the workflow preview experience remains usable.
    - Vite still reports chunk-size warnings for production build output, and the Mermaid chunk remains large but lazily loaded.
- Frontend build (workflow preview graph in Agent Builder):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Agent Builder compile output now includes a read-only workflow preview graph rendered from simulated compile artifacts (`workflowPreview`) alongside `workflow.ts` and `workflow.manifest.json`.
    - Mermaid was added as the graph rendering dependency and loaded lazily from the preview panel instead of the main bundle.
    - Vite still reports chunk-size warnings for production build output, and the Mermaid chunk is large, but the build completed successfully.
- Frontend build (checklist layout fix):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Fixed the Agent Builder checklist layout for knowledge/tool cards by switching item content to a more stable grid layout.
    - Prevented Chinese titles from collapsing into one-character-per-line wrapping.
    - Vite still reports a chunk-size warning for the production bundle, but build completed successfully.
- Frontend build (Spec editor + compile output refactor):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - `AgentBuilderShell` was refactored from a field-based builder skeleton into a text-first Spec editor with compile result panels.
    - Current compile action is a frontend simulated compiler that generates placeholder workflow code, manifest, dependency list, and risk warnings.
    - Vite still reports a chunk-size warning for the production bundle, but build completed successfully.
- Frontend build (no-code Agent Builder framework):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - New assistant-side Agent Builder workspace compiles successfully after adding:
      - left title-area workspace switch (`会话 / Agent 构建`)
      - `AgentBuilderShell.tsx` framework UI
      - Agent definition sections for identity, prompt, knowledge, tools, workflow, and release governance
    - Vite emitted a chunk-size warning for the production bundle, but the build completed successfully.
- Backend build (model provider center implementation):
  - Command: `PATH=/opt/homebrew/bin:$PATH /opt/homebrew/bin/mvn -Dmaven.repo.local=.m2 -DskipTests package`
  - Result: success
  - Notes: added provider-config migration/table + service/controller for 4 providers (`aliyun-bailian`, `ollama-local`, `anthropic`, `openai`), and provider model list fetch endpoints.
- Backend runtime verification (local profile):
  - Command: restart backend jar with local profile and call APIs via auth token
  - Result: success
  - Notes:
    - `GET /models/providers` returns exactly 4 providers.
    - `POST /models/providers/aliyun-bailian/models/fetch` returned `count=222` with live model ids from DashScope compatible endpoint.
- Frontend page verification (`/admin/models`):
  - Command: browser automation snapshot on running Vite dev server
  - Result: success
  - Notes: page now shows Cherry-style provider center: left provider list + right panel (`API Key` / `API 地址` / `检测` / `获取模型列表` / model list / scene mapping).
  - Enhancements (2026-04-14):
    - Added model search bar with filter icon and clear button
    - Models grouped by series prefix (e.g., `qwen`, `gpt`, `claude`) with group headers and counts
    - Filtered count badge shows `X / total` for search results
    - Switching provider resets search and reloads grouped models
- Frontend full build status (existing baseline issue):
  - Command: `PATH=/opt/homebrew/bin:$PATH /opt/homebrew/bin/npm run build`
  - Result: failed (pre-existing)
  - Notes: failure remains in `src/assistant/AssistantApp.tsx` (TS2774 / TS2322 / TS18048) and is unrelated to this model-center change-set.
- Frontend full build — FIXED (2026-04-14 12:30):
  - Command: `PATH=/opt/homebrew/bin:$PATH /opt/homebrew/bin/npm run build`
  - Result: success ✅
  - Notes: fixed 6 TS errors in `AssistantApp.tsx` (speech input feature):
    - TS2774: replaced `typeof X !== "undefined"` with `"X" in window` / optional chaining for `MediaRecorder`, `WebSocket`, `AudioContext`, `getUserMedia`
    - TS2322: changed `AudioContext | undefined` cast to `AudioContext` + added runtime null check
    - TS18048: `ctx` possibly undefined — resolved by narrowing type after constructor
  - Build output: `tsc -b && vite build` → 316 modules, 83KB CSS + 480KB JS, 2.92s

- Frontend (auth parse hardening to prevent login crash):
  - Command: `npm run build`
  - Result: success
  - Notes: assistant/admin auth flows now use safe JSON parsing; empty or non-JSON response bodies no longer throw `Unexpected end of JSON input` and crash the page.
- Frontend-to-backend proxy runtime verification:
  - Command: `curl -i -X POST http://127.0.0.1:5173/auth/sms/send -H 'Content-Type: application/json' -d '{"orgId":"demo-org","mobile":"18611892001"}'`
  - Result: success (`HTTP/1.1 200 OK`)
  - Notes: fixed Vite proxy target mismatch (`8081` -> `8080`) in both `vite.config.ts` and `vite.config.js`; send-code no longer returns proxy-side 500.
- Frontend (after white-screen hook-order fix):
  - Command: `npm run build`
  - Result: success
  - Notes: fixed `Rendered fewer hooks than expected` by ensuring hooks are declared before conditional auth return in `AssistantApp`.
- Backend:
  - Command: `mvn -Dmaven.repo.local=.m2 test`
  - Result: failed in this environment
  - Notes: Mockito inline mock maker cannot attach agent on current JDK 25 runtime in sandbox (`Could not initialize plugin: org.mockito.plugins.MockMaker`).
- Backend compile/package:
  - Command: `mvn -Dmaven.repo.local=.m2 -DskipTests package`
  - Result: success
  - Notes: confirms backend code compiles and packages with latest changes.
- Backend compile/package after RabbitMQ+vector integrations:
  - Command: `mvn -Dmaven.repo.local=.m2 -DskipTests package`
  - Result: success
  - Notes: verified compilation after introducing AMQP queue/worker, vector-store abstractions, Qdrant adapter, and Flyway V4 migration.
- Full quality gate (post-Qdrant migration):
  - Command: `./scripts/quality-check.sh` (with Qdrant on `6333`)
  - Result: success
  - Notes: 7 backend integration tests (default profile); `OrgModelConfigRepository.deleteByOrgIdAndSceneCode` uses `@Modifying` JPQL; `OrchestratorIntegrationTest` uses `@TestPropertySource` so Maven `local` profile does not bleed in; Qdrant smoke script passes against live container.
- Full business E2E (local profile + Docker stack):
  - Command: `./scripts/run-full-demo.sh` (or `./scripts/e2e-local-business.sh` if API already up)
  - Result: success on 2026-04-02
  - Notes: SMS login (**default mobile `13900009999`** aligned with `bootstrap-admin-mobiles` for ORG_ADMIN on new user) → KB → upload → publish → MQ worker → PUBLISHED → chat RAG hit unique marker; `ragContext` size ≥ 1 with Qdrant.
- Frontend:
  - Command: `npm run build`
  - Result: success
  - Notes: production bundle was generated under `frontend/dist`.
- Frontend (after KB polling + selected-KB retrieval update):
  - Command: `npm run build`
  - Result: success
  - Notes: confirms UI changes for async indexing visibility compile and bundle correctly.

## Scope Verified In This Iteration

- New backend APIs:
  - `PUT /kb/{id}` update knowledge base
  - `DELETE /kb/{id}` delete knowledge base
  - `DELETE /kb/documents/{id}` delete document
  - `DELETE /models?sceneCode=...` delete model config
  - `DELETE /tools?toolName=...` disable tool
- Frontend management views expanded and build-verified:
  - **Admin app** (`/admin/*`): model/tool/ops/KB CRUD flows; **user role** management calling `/admin/users`
  - **Assistant app** (`/`): chat + read-only KB multi-select for RAG (no management tabs)
  - React Router + split `localStorage` keys (`cici_assistant_token` / `cici_admin_token`)
- New backend integration scope (compile-verified):
  - MQ indexing enqueue/consume flow classes added
  - Vector recall path integrated into RAG service
  - Qdrant + RabbitMQ local runtime config in `docker-compose.yml` (Qdrant on host `6333`)
- Runtime environment startup:
  - Command: `docker compose up -d && docker compose ps`
  - Result: verify with `scripts/verify-qdrant-stack.sh` when Qdrant is up
  - Notes: `cici-qdrant` exposes HTTP API on `6333`.

- End-to-end API verification (local profile, MQ indexing enabled):
  - Command: backend run on `8081` with `--app.kb.vector-store=memory`, then scripted API flow:
    - `/auth/sms/send` -> `/auth/sms/login`
    - `POST /kb` -> `POST /kb/documents/upload` -> `POST /kb/documents/{id}/publish`
    - poll `GET /kb/{kbId}/documents` until indexed
    - `POST /ai/chat` with `knowledgeBaseIds=[kbId]`
  - Result: success
  - Evidence: document reached `PUBLISHED`; chat returned `rag_count=1` and policy summary answer.

## Planned Verification

- Re-run `mvn test` under JDK 21 runtime (or with adjusted Mockito setup) to restore test gate.
- Add backend integration tests for the newly added delete/update management APIs.
- Add frontend E2E checks for new model/tool/ops management flows.
- Add end-to-end local verification for publish-document -> MQ task -> chunk indexing -> vector recall path with live RabbitMQ/Qdrant (`app.kb.vector-store=qdrant` in local profile).

## 2026-05-21 TASK-118 Current-Branch Usage Metric Cards Restore

- Frontend build:
  - Command: `npm run build` in `frontend/`
  - Result: success
  - Notes: Vite still reports the existing large chunk warning after build.
- Visual verification:
  - Command: Playwright with mocked `/auth/me` and `/admin/organization/profile`, route `/admin/organization`, viewport 1440x900
  - Result: success
  - Evidence: screenshot `output/playwright/admin-organization-cards-current-branch.png`.
  - Notes: usage summary outer panel has `0px none` border, transparent background, and `0px` radius; all six usage metrics render as standalone cards with `1px solid` border, warm ivory background, `14px` radius, 12px grid gap, and no horizontal overflow.

## 2026-05-21 TASK-118 Usage Summary Data Restore

- Backend compile:
  - Command: `mvn -q -Dmaven.repo.local=../.m2 -DskipTests compile` in `backend/`
  - Result: success
  - Notes: confirms the restored `usageSummary` API aggregation compiles.
- Backend integration test:
  - Command: `mvn -q -Dmaven.repo.local=../.m2 -Dtest=AdminOrganizationProfileIntegrationTest test` in `backend/`
  - Result: blocked before test assertions
  - Notes: Spring context startup is blocked by existing duplicate Flyway migration version `58`: `V58__platform_account.sql` and `V58__agent_open_api_cloudcc_key_type.sql`.
- Static diff check:
  - Command: `git diff --check`
  - Result: success

## 2026-05-21 TASK-127 Integrated Branch Backend Verification Unblock

- Authorization:
  - Command: `python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/dev-login.py .claw --task TASK-127 --branch codex/TASK-124-feat-046-platform-tenant-provisioning --files backend/src/main/resources/db/migration/V58__platform_account.sql docs/specs/FEAT-041-platform-accountless-login.md --json`
  - Result: success
  - Notes: `MANAGER-001` passed SSH-key possession and task-scope authorization for the merge-follow-up migration/spec fix.
- Migration collision fix:
  - Change: renamed `backend/src/main/resources/db/migration/V58__platform_account.sql` to `backend/src/main/resources/db/migration/V59__platform_account.sql`
  - Result: success
  - Notes: also synced `docs/specs/FEAT-041-platform-accountless-login.md` so the documented migration version matches the integrated branch.
- First rerun:
  - Command: `mvn -Dtest=AuthFlowIntegrationTest,PlatformTenantLifecycleIntegrationTest test` in `backend/`
  - Result: blocked by stale build output
  - Notes: Flyway still saw deleted `backend/target/classes/db/migration/V58__platform_account.sql`; a clean rebuild was required.
- Test-database reset:
  - Command: `docker exec cici-postgres sh -lc "dropdb -U cici agentcici_test && createdb -U cici agentcici_test"`
  - Result: success
  - Notes: reset the local PostgreSQL integration database after Flyway reported a checksum mismatch for previously applied version `58`.
- Focused backend integration gate:
  - Command: `mvn clean -Dtest=AuthFlowIntegrationTest,PlatformTenantLifecycleIntegrationTest test` in `backend/`
  - Result: success
  - Notes: `AuthFlowIntegrationTest` 16/16 passed, `PlatformTenantLifecycleIntegrationTest` 6/6 passed, total 22/22 green on local `agentcici_test`.

## 2026-05-27 TASK-137 Custom Agent Delete

- Authorization:
  - Command: `python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/dev-login.py .claw --developer MANAGER-001 --task TASK-137 --branch codex/TASK-137-custom-agent-delete --files ... --json`
  - Result: success after assignment roots were corrected from bare directories to recursive globs.
  - Notes: `check-assignment.py` also passed for implementation files and status/test-report files.
- Frontend focused test:
  - Command: `npm test -- AgentBuilderShell.test.ts` in `frontend/`
  - Result: success
  - Notes: 9 tests passed, including Agent delete fallback helper coverage.
- Frontend build:
  - Command: `npm run build` in `frontend/`
  - Result: success
  - Notes: existing Vite large chunk warning remains.
- Backend compile:
  - Command: `mvn -Dmaven.repo.local=../.m2 -DskipTests compile` in `backend/`
  - Result: success
- Backend focused integration test:
  - Command: `mvn -Dmaven.repo.local=../.m2 -Dtest=AgentDefinitionDeleteIntegrationTest test` in `backend/`
  - Result: blocked before assertions
  - Notes: Spring context startup could not obtain a PostgreSQL connection (`SQLState 08001`), so the new integration tests compiled but did not execute assertions.
- Desktop browser smoke:
  - Command: Vite dev server + in-app browser route open for `/admin/agent-builder`
  - Result: partial
  - Notes: unauthenticated route rendered the admin login page; authenticated Agent Builder smoke was blocked because `/auth/me` requires the same unavailable backend database.
- Static diff check:
  - Command: `git diff --check`
  - Result: success

## 2026-05-27 Assistant Root Auth Guard

- Authorization:
  - Command: `python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/dev-login.py .claw --json`
  - Result: success
  - Notes: `MANAGER-001` local identity verified before editing the assistant route auth behavior.
- Frontend build:
  - Command: `npm run build` in `frontend/`
  - Result: success
  - Notes: existing Vite large chunk warning remains.
- Browser auth smoke:
  - Command: Playwright route `/`, then set `localStorage.cici_assistant_token` to an invalid token and reload.
  - Result: success
  - Evidence: route rendered the assistant login form, displayed `登录状态已过期，请重新登录。`, and `localStorage` no longer contained `cici_assistant_token`.
  - Notes: expected `401 Unauthorized` was observed for `/auth/me` during invalid-token validation.

## 2026-06-02 Local Main Integration

- Scope:
  - Merged local task branches `codex/TASK-146-ops-observability-audit` and `codex/TASK-147-wecom-kf-connection-test` into `main`; `TASK-146` fast-forward also included `TASK-143`.
- Conflict resolution:
  - Files: `.claw/current-status.md`, `.claw/task-board.md`, `.claw/test-report.md`
  - Result: success
  - Notes: reconciled TASK-146 ops observability with TASK-147/TASK-148 Enterprise WeChat customer-service/domain status and preserved both validation histories.
- Merge follow-up fix:
  - File: `backend/src/main/java/com/codehouse/ciciassistant/wecom/service/WecomKfClient.java`
  - Result: success
  - Notes: first backend rerun failed Spring context startup because `WecomKfClient` had multiple constructors and no explicitly selected autowired constructor; added explicit constructor injection annotation.
- Conflict marker check:
  - Command: `rg -n "^(<<<<<<<|=======|>>>>>>>)" .claw . || true`
  - Result: success
- Static diff check:
  - Command: `git diff --check --cached`
  - Result: success
- Frontend production build:
  - Command: `npm run build` in `frontend/`
  - Result: success
  - Notes: existing Vite large chunk warning remains.
- Backend focused integration gate:
  - Command: `mvn -q -Dtest='AgentRunTraceIntegrationTest,ModelProviderServiceIntegrationTest,PlatformModelProviderIntegrationTest,com.codehouse.ciciassistant.wecom.**.*Test' test` in `backend/`
  - Result: success after the constructor injection merge fix.
  - Notes: covers ops trace visibility, platform model-provider governance, and WeCom customer-service client/config behavior on the local `agentcici_test` database.

## 2026-07-09 TASK-171 CRM Clean Embed

- CRM 纯嵌入模式本地验证:
  - Command: `npm run build` in `frontend/`
  - Result: success
  - Notes: verifies the AgentCiCi `embed=crm` route compile; existing Vite large chunk warning remains.
- UMD/static checks:
  - Command: `node --check frontend/build/customer-workbench.umd.min.js && git diff --check`
  - Result: success
  - Notes: verifies the CloudCC runtime bundle remains syntactically valid and the patch has no whitespace errors.
- CloudCC pagecomponent dry-run through `cc-customization-expert-msapi`:
  - Command: `cloudcc package pagecomponent customer-workbench --dry-run`
  - Result: success
  - Notes: recognizes `frontend/build/customer-workbench.umd.min.js` and safe pagecomponent files; no direct CRM write was attempted in this local gate.
- Browser DOM validation:
  - Command: Vite dev server plus Playwright at `http://127.0.0.1:5173/app?aiApp=customer-workbench&embed=crm` with mocked authenticated APIs.
  - Result: success
  - Evidence: `.playwright-cli/page-2026-07-09T07-11-39-914Z.png`.
  - Notes: DOM assertion returned `hasRail=false`, `hasAiApps=false`, `hasEmbedded=true`; rendered text starts from the customer queue and workbench content, not AgentCiCi platform navigation.

## 2026-07-08 TASK-171 Customer Interaction Workbench

- Authorization:
  - Command: `dev-login.py .claw --task TASK-171 ... --json`
  - Result: success
  - Notes: `MANAGER-001` passed identity and task-scope authorization for representative backend, frontend, spec, and CloudCC page component files; `check-assignment.py --task TASK-171 ...` also passed.
- CloudCC connectivity:
  - Command: CloudCC OpenAPI token flow plus MetadataService capabilities/standard-catalog checks; OpenAPI queries for standard `Task`, `Event`, and `Opportunity`.
  - Result: success
  - Notes: CRM standard objects returned real rows; MetadataService remained reachable with the OpenAPI token. Secrets and tokens are intentionally omitted from this report.
- Backend compile:
  - Command: `mvn -q -DskipTests compile` in `backend/`
  - Result: success
  - Notes: covers new customer workbench JPA entities, repositories, service/controller, migration reference, and skill definition changes.
- Frontend build:
  - Command: `npm run build` in `frontend/`
  - Result: success
  - Notes: existing Vite large chunk warning remains.
- Desktop browser validation:
  - Command: Vite dev server plus Playwright at 1440x900 with mocked authenticated APIs.
  - Result: success
  - Evidence: `output/playwright/task171-customer-workbench-desktop.png`.
  - Notes: AI 应用入口, 客户互动工作台, 老客户经营 tab, AI 快捷指令, CRM 落地建议, and `置信度 92%` render correctly; no horizontal overflow; console shows 0 errors and 0 warnings.
- CloudCC page component local validation:
  - Command: `cloudcc detail pagecomponent customer-workbench "" .`
  - Result: success
  - Notes: local component config and `prebuiltBundlePath` are recognized.
- CloudCC page component publish safety check:
  - Command: `cloudcc publish pagecomponent customer-workbench .`, followed immediately by `cloudcc delete pagecomponent <published-id> .`
  - Result: publish API returned success, then deletion returned success.
  - Notes: publish was not accepted as a valid release because the CLI packed root project config into the uploaded source payload. The cloud component record was deleted immediately, and a follow-up `cloudcc get pagecomponent .` did not show the component.
- CloudCC page component safe publish:
  - Command: create a temporary minimal CloudCC project under `/tmp` containing only `package.json.devConsoleConfig`, `frontend/pagecomponents/customer-workbench/customer-workbench.vue`, `frontend/pagecomponents/customer-workbench/config.json`, and `frontend/build/customer-workbench.umd.min.js`; then run `cloudcc publish pagecomponent customer-workbench <tmpProject>`.
  - Result: success
  - Evidence: final active component id `6a4d348fe4b0a577cbba1ebf`, apiName `custc_202607Hdhm60zo`; publish/update responses used only the minimal pagecomponent payload, unsafe config/token pattern count was `0`, and temporary credential directories were deleted.
- CloudCC page component remote verification:
  - Command: `cloudcc detail pagecomponent "" 6a4d348fe4b0a577cbba1ebf .` and `cloudcc scan msapi . online-highcode`
  - Result: success
  - Notes: remote component shows `component-customer-workbench`, `客户互动`, `isDeleted=0`, `loadModel=lazy`, and default URL `https://x.agentcici.com/app?aiApp=customer-workbench`.
- CloudCC CRM menu placement exploration:
  - Command: `cloudcc plan msapi . menus ... create`
  - Result: planned but not applied
  - Notes: generated script-menu plans did not include app/profile binding steps (`appCount=0`, `profileCount=0`), so applying was intentionally skipped to avoid an invisible or incomplete CRM menu.
- CloudCC HTML component publish:
  - Command: direct devconsole API `POST /devconsole/htmlComponent/saveHtmlComponent` with `accessToken` header and local `html/customer_interaction_workbench/{config.json,index.html}`.
  - Result: success
  - Evidence: HTML component id `6a4d37ece4b0a577cbba1ec0`, apiName `customer_interaction_workbench`, accessPath `/oss/html/org0720f814430017229/customer_interaction_workbench-v1.html`.
  - Notes: `cloudcc publish html customer_interaction_workbench .` currently fails because the CLI sends `pluginToken` as the `accessToken` header for this endpoint; direct call with the OpenAPI `accessToken` succeeds.
- CloudCC online high-code scan:
  - Command: `cloudcc scan msapi . online-highcode`
  - Result: partial success
  - Evidence: `pagecomponent` count `1` with id `6a4d348fe4b0a577cbba1ebf`, apiName `custc_202607Hdhm60zo`, component `component-customer-workbench`; `html` count `1` with id `6a4d37ece4b0a577cbba1ec0`, apiName `customer_interaction_workbench`; `customPage` count `1` with id `6a4d3b831b8c6d0ec6dd22ef`.
  - Notes: script endpoint returned an unrelated CloudCC server-side 500 during scan, so the scan is recorded as partial success even though the workbench assets are present.
- CloudCC MetadataService menu apply:
  - Command: `cloudcc apply msapi . pla2026E964195FlLpjf`
  - Result: blocked
  - Notes: MetadataService returned HTTP 403 `insufficient_scope` because the token is missing `metadata:apply`; no menu write was applied.
- CloudCC token scope probe:
  - Command: request `/api/cauth/token` with the standard body and with `scope=metadata:apply`, `metadata:read metadata:write metadata:apply`, `scopes:["metadata:apply"]`, and grant-type variants.
  - Result: blocked for apply
  - Notes: every successful response returned a JWT with payload keys `ClientId/aud/binding/exp/loginName/orgId` and no scope claim, so this developer key cannot self-request `metadata:apply`.
- CloudCC customPage write probe:
  - Command: direct devconsole API `/devconsole/custom/pc/1.0/post/insertCustomPage` using the legacy CloudCC CLI customPage payload contract.
  - Result: success
  - Evidence: customPage id `6a4d3b831b8c6d0ec6dd22ef`, pageLabel `客户互动工作台`, pageApi `customer_interaction_workbench`; `pageCustomPage` readback returned total `1`.
- CloudCC page menu and Sales Cloud binding:
  - Command: setup service `/api/customTab/tabSetDone`, then `/api/customTab/queryTabList` and `/api/appProgram/queryModifyPage`.
  - Result: success
  - Evidence: tab id `acf2026C53BE54B9R1Iu`, label `客户互动工作台`, lightning page `customer_interaction_workbench#lightning`, profile authorization count `6`; Sales Cloud app `ace20220322Salesloud` selected menu count `17`, with `客户互动工作台*` present in `selectedTabList`.
- Static diff check:
  - Command: `git diff --check`
  - Result: success
## 2026-07-12 TASK-198 AI 动态客户信号与可解释评分

- Authorization:
  - Result: success.
  - Notes: `MANAGER-001` passed task login and assignment checks for backend customer code, V77, workbench UI, specs and task state.
- Focused backend tests:
  - Command: `mvn -f backend/pom.xml -Dtest=CustomerCrmProjectionServiceTest,CustomerDynamicScoringServiceTest,CustomerInteractionIngestionServiceTest,CustomerWorkbenchServiceTest test`.
  - Result: success, 17 tests.
  - Notes: covers AI signal normalization, low-confidence pending state, idempotence, batch snapshot use, interaction confirmation and existing workbench behavior.
- Full backend baseline:
  - Command: `mvn -q -f backend/pom.xml test`.
  - Result: baseline not green, 251 tests with 16 failures and 3 errors.
  - Notes: TASK-198 focused suites are green; remaining failures/errors are unrelated existing billing/auth/skill fixture drift and connection-sensitive suites, consistent with the repository's known 19-test baseline gap.
- Frontend tests and build:
  - Command: `npm test -- --run && npm run build` in `frontend/`.
  - Result: success, 64 tests and Vite production build; existing large-chunk warning remains.
- Migration and local runtime:
  - Command: local Spring Boot startup against PostgreSQL.
  - Result: success; Flyway applied V76 and V77 and reached schema version 77, backend health became ready.
- Desktop browser validation:
  - Target: local AgentCiCi customer workbench, old-customer mode.
  - Result: success; score drawer rendered at 720px width and full viewport height, used an internal auto-scroll region, and document overflow was `x=0/y=0`.
  - Notes: verified five dimensions, 50-point insufficient-evidence baseline, 65% confidence note, filters and close action. Demo/detail score-source mismatch found during QA was fixed before release.
- Release dry run:
  - Command: `./scripts/release-acr.sh --dry-run`.
  - Result: success; generated production version `2.5.12` with canonical backend/frontend image and Git tag plan.
- Production release:
  - Result: success; Git commit/tag `4adbd3bf2d3a` / `2.5.12` and backend/frontend ACR images were pushed.
  - Images: backend index `sha256:58efb89a6c48505d8e94d797724a2207bab7f6acdeb5df21e8e9b1b74d705086`, amd64 `sha256:68ae75f21b77bd63e7e4ea6edc4b1d83ffd792f147018b568546c36175c1bafc`; frontend index `sha256:9fd8215c87319cf0b1b2259b7f0b99351cf993673fa174b603604b48ef70b53b`, amd64 `sha256:77d138450accd03c99314b5cb8459aabc003e6798167ca68b72d8db989228585`.
  - Backup: `/opt/cici/backups/20260712-192621-before-2.5.12-task198-dynamic-scoring`; env, PostgreSQL, KB files and Qdrant archives were non-empty.
  - Runtime: backend/frontend healthy on `2.5.12`; state services stayed healthy on `2.3.4`; health `UP`, version `2.5.12 / 4adbd3bf2d3a`, V77 `success=true`, Nginx valid, public root/workbench HTTP 200.
- Production dual-entry browser:
  - Result: success in AgentCiCi and real CloudCC CRM injection page using the same organization and user context.
  - Notes: CRM reached READY, existing-customer queue showed the dynamic neutral baseline, score drawer rendered all five dimensions and no-evidence state, host outer overflow was zero, and browser error/warning logs were empty.
  - Evidence: `output/playwright/task198-prod-cloudcc-score-drawer-2.5.12.png`.
- CloudCC implementation expert verification:
  - Skill: `cc-customization-expert-msapi 2.1.276-msapi`.
  - Result: component/customPage readback matched component id `6a526349e4b0a577cbba1fba`, name `component-customer-workbench`, version `11` and the production embed URL.
  - Notes: `verify injectionPage` returned the known warning `stale_component_reference` only because no runtime version snapshot was supplied; component ID/name/reference were exact, and the real CRM browser runtime rendered successfully.
- Historical signal backfill hotfix:
  - Command: `mvn -q -Dmaven.repo.local=.m2/repository -Dtest=CustomerDynamicScoringServiceTest,CustomerCrmProjectionServiceTest,CustomerInteractionIngestionServiceTest,CustomerWorkbenchServiceTest test` in `backend/`.
  - Result: success, 18 tests, zero failures/errors.
  - Notes: verifies old analysis becomes pending-only evidence at a neutral 50 baseline, repeated reads are idempotent, and current scoring/projection/ingestion behavior remains green.
- Historical signal backfill production release:
  - Result: success; Git commit/tag `ae6643c109a8` / `2.6.1`, backend index `sha256:36efd141a73d5650810e9f3d25c742385f26012b112a5845a811aa758399ec84`, frontend index `sha256:f88f747357c8126d9bd403dd208437f940145d39205112358d57a42ab3492ab1`.
  - Backup: `/opt/cici/backups/20260712-195131-before-2.6.1-task198-history-backfill`; all four artifacts are non-empty.
  - Runtime: backend/frontend healthy on `2.6.1`, state services healthy on `2.3.4`, health `UP`, version `2.6.1 / ae6643c109a8`, Nginx valid and public workbench HTTP 200.
  - Real data: organization `org2sva14i4udjmi2t4s` produced 2 pending signals and organization `org5nszpgj99jaysxv6y` produced 8; both snapshots remained `healthScore=50`, `activeSignalCount=0`.
  - Idempotence: two consecutive production explanation reads returned `50/0/2/2`; persisted signal count remained 2.
  - Stable-window logs: backend scoring errors `0`, Nginx 5xx `0`. Restart-window stream 502 responses ended when the backend became healthy and did not recur.

## 2026-07-12 TASK-199 互动驱动的客户经营动作

- Authorization:
  - Result: success.
  - Notes: `MANAGER-001` passed TASK-199 assignment checks for backend customer code, V78, workbench UI, specs and task state.
- Focused backend tests:
  - Command: `mvn -q -Dmaven.repo.local=.m2/repository -Dtest=CustomerInteractionActionServiceTest,CustomerInteractionIngestionServiceTest,CustomerWorkbenchServiceTest,CustomerDynamicScoringServiceTest,CustomerCrmProjectionServiceTest test` in `backend/`.
  - Result: success, 24 tests with zero failures/errors.
  - Notes: covers evidence-backed action creation, pending refresh without stacking, low-confidence rejection, missing target rejection, seven-day cooldown, historical-expiry rejection, hallucinated-evidence rejection, confirmation idempotence and existing workbench/scoring/projection behavior.
- Frontend tests and build:
  - Command: `npm test -- --run && npm run build` in `frontend/`.
  - Result: success, 66 tests and Vite production build; existing large-chunk warning remains.
- Desktop browser validation:
  - Target: local AgentCiCi customer workbench.
  - Result: success; historical recommendations are labeled `历史建议`, customer/timeline/action structure renders normally, and browser console has zero errors and zero warnings.
  - Evidence: `output/playwright/task199-local-dynamic-actions.png`.
- Static checks:
  - Result: success; `git diff --check` passed and fixed first-open recommendation/seed symbols are absent from production customer service code.
- Release dry run and images:
  - Result: success; release version `2.6.2`, Git `b87bbe43dd0d`, backend index `sha256:e0f275c02d910b392c708cf8940da9ca30fe1eabc2b19e2469fb42259638ae60`, frontend index `sha256:73f5b0b427d1707ee8d4de5a6819169b0df755408a0747d7387ed8917731dc12`.
- Production backup and deployment:
  - Result: success; backup `/opt/cici/backups/20260712-232657-before-2.6.2-task199-interaction-actions` contains non-empty env, PostgreSQL, KB and Qdrant artifacts. Backend/frontend are healthy on `2.6.2`; state services remain healthy on `2.3.4`.
  - Notes: health `UP`, version `2.6.2 / b87bbe43dd0d`, V78 `success=true`, Nginx valid, public root/workbench HTTP 200.
- Real interaction/action acceptance:
  - Result: success in organization `org2sva14i4udjmi2t4s`, existing customer `0012022D9CDF1CBPQGwJ`.
  - Evidence: confirmed batch `cib_554a1a6cc47e44d0afde91e1bbbd638e` produced event `cwi_f39777961d5df638a255caf7edd9308ffed0ed5c` and recommendation `cwr_0d4d4e3ddf5064c191e84b562a5f3dffc6aec10e` with key `expansion:mobile-inspection`, 100% confidence, exact source sentence, source event/batch and `2027-01-08` validity.
  - Idempotence: repeating confirmation returned `deduplicated=true`; matching action count remained `1`. The pending action was not accepted or written to CRM.
- Production browser and stable window:
  - Result: success; old-customer operations showed the new timeline event and `互动识别` action beside retained `历史建议`, with one evidence item and validity. Browser console had zero errors/warnings; task-related backend errors, migration errors and workbench Nginx 5xx were zero after warmup.
  - Evidence: `output/playwright/task199-prod-interaction-driven-action-2.6.2.png`.
  - Note: three login-shell `Session not found` responses for stale `workbench:cici-system` were observed before the stable window; they are unrelated to customer interaction/action endpoints.

## 2026-07-14 TASK-200 多租户智能体评测控制面生产落地

- Authorization and assignment:
  - Result: success.
  - Notes: `MANAGER-001` passed the generic and TASK-200 SSH challenge gates; assignment checks passed for V79, Agent/AI/Skill/Common backend code, platform/admin/Builder frontend code, Vite proxy and project-state files.
- Flyway and backend compilation:
  - Commands: clean PostgreSQL test startup, local Spring Boot startup, and `mvn -q -DskipTests compile` in `backend/`.
  - Result: success; 75 migrations validated and schema reached V79 in test and local runtime databases; backend compilation passed.
- Focused evaluation tests:
  - Command: `mvn -q -Dtest=AgentProductionReadinessIntegrationTest,AgentEvaluationControlPlaneIntegrationTest,AgentEvaluationAssertionEngineTest test` in `backend/`.
  - Result: success, 7 tests with zero failures/errors after the final redaction-order correction.
  - Coverage: platform suite draft/publish/immutability, sealed hidden-case redaction, tenant asset isolation and review lifecycle, platform auditor/read boundary, billing-role rejection, cross-Agent issue-reference rejection, Trace-to-DRAFT regression capture, mobile/email/ID-card/credential redaction, compound assertions, invalid assertion fail-closed, real evaluation model failure handling, stale/publish gates and publish readiness.
- Related security and observability regression:
  - Command: `mvn -q -Dtest=RbacProductionReadinessIntegrationTest,PlatformAuthIntegrationTest,PlatformGovernanceIntegrationTest,AgentRunTraceIntegrationTest test` in `backend/`.
  - Result: success with zero failures/errors.
- Full backend baseline:
  - Command: `mvn -q test` in `backend/` against a clean test schema during TASK-200 validation.
  - Result: baseline not green outside TASK-200.
  - Notes: unrelated existing failures include stale `skill_definition.source_type` fixtures, disabled meeting-minutes model provider assumptions, AutoService platform-auth expectation drift, billing/context/audit assumptions, OneKeyToken model-list expectation drift, customer-insight success mismatch and legacy skill-governance authorization assumptions. TASK-200 focused and adjacent RBAC/platform/Trace suites are green.
- Frontend tests and production build:
  - Commands: `npm test` and `npm run build` in `frontend/`.
  - Result: success; 12 files / 67 tests passed and Vite production build completed. Existing large-chunk warning remains.
- Local browser desktop validation:
  - Targets: `/admin/evaluation`, `/admin/agent-builder`, and `/platform/evaluation` on local runtime.
  - Result: success; tenant AI quality overview and evaluation-set maintenance rendered real API data, platform governance and standard-asset maintenance rendered correctly, and all checked pages had zero horizontal overflow and zero browser console errors/warnings.
  - Product-boundary evidence: Builder “评测” showed version quality, production gate and evaluation actions with no channel content; Builder “发布渠道” showed only 企微、钉钉、飞书、Web 浮窗、开放 API channel controls and no evaluation/quality headings.
  - Defect found and fixed: stale generated `vite.config.js` lacked `/evaluation` proxy although `vite.config.ts` contained it; both configs are now aligned and the page was reloaded successfully with no JSON parse alert.
- Compose and static checks:
  - Commands: `docker compose --env-file deploy/acr.env.example -f deploy/docker-compose.acr.yml config`, assignment check and `git diff --check`.
  - Result: success; rendered Compose output contained 232 lines and no validation error.
- Release dry run:
  - Command: `./scripts/release-acr.sh --dry-run`.
  - Result: success; generated canonical production candidate `2.6.3` for backend/frontend images, `CICI_APP_VERSION`, `VITE_CICI_APP_VERSION`, `CICI_IMAGE_TAG` and Git tag.
  - Notes: dry-run only; no image, Git tag, production data or deployment state was changed.

## 2026-07-14 TASK-203 客户互动工作台全场景演示数据

- Authorization and static validation:
  - Result: success. `MANAGER-001` passed TASK-203 SSH identity and assignment scope checks; `python3 -m py_compile scripts/seed-demo-environment.py`, `git diff --check` and the no-write `--dry-run` passed.
- CloudCC CRM V2:
  - Result: success. Batch `TASK-203-DEMO-V2` created/reused 16 Accounts, 30 Contacts, 8 Leads, 21 Opportunities, 30 Tasks, 45 Events, 8 Contracts and 8 Cases; script owner readback confirmed SalesA for every V2 record.
  - Idempotence: a second CRM upsert returned the same object counts with no duplicate-name growth.
- Minimum CRM permission:
  - Result: success. SalesA's sales profile lacked Contract/Case read access; permission set `cac203DemoVis01` adds read only. MetadataService plan `pla202604C39466BxSzs` and operation `ope202682B741D7w0fRu` reached `VERIFIED`.
  - Rollback: `rollback-plan` returned executable plan `rbp2026D899C178B8m63`; it was not applied. An earlier overlength permission-set ID plan failed before mutation and was superseded.
- AgentCiCi V2 transaction:
  - Result: success and idempotent. Latest backup `/opt/cici/backups/20260714-065319-before-task203-demo-v2`; transaction produced 16 workbench snapshots, 30 confirmed batches/events, 30 memories, 30 dynamic signals, 16 core score snapshots and 12 evidence-backed pending actions.
  - Coverage: seven source types, seven memory types, five score dimensions and ACTIVE/PENDING/EXPIRED/SUPERSEDED states. Action types are evenly split: 4 CREATE_TASK, 4 CREATE_OPPORTUNITY and 4 UPDATE_OPPORTUNITY; all 12 carry event, batch, action key, trigger and validity, and none was written to CRM.
  - Legacy cleanup: TASK-172 pending static recommendations are zero; accepted/applied historical acceptance records remain intact.
- Owen/SalesA production API acceptance:
  - Result: success. Integration returned `ready=true`, `visibleAccounts=16`, `syncStatus=READY`; queues returned new/all 8 and existing/all 8.
  - Filter totals: new focus/follow/risk/recommendations = `4/8/1/7`; existing renewal/health/service/expansion = `4/5/5/8`.
  - Scenario details: OPPORTUNITY_GAP, RELATION_GAP, NEXT_STEP_GAP, OVERDUE_TASK, SERVICE_RISK, RENEWAL_WINDOW, VALUE_STABLE and INTERACTION_GAP all appeared on their designated customers; 25-day and 80-day renewal examples, service issues, expansion opportunities and the zero-timeline silent customer were verified.
  - Archive/score: archive detail returned confirmation text, analysis and memory; seven sources and all five score dimensions were visible through authenticated APIs.
- Browser limitation:
  - The in-app browser could not resolve `onechat.agentcici.com` (`ERR_NAME_NOT_RESOLVED`). No safety interstitial was bypassed; the same Owen/SalesA identity was verified over IP-resolved HTTPS API instead. This is the existing workstation DNS risk, not an application or data failure.
## TASK-258 - 研发交付产品经理 Semattice 实时检索

- 定向后端测试：`SematticeProjectDeliveryToolServiceTest` 与 `ToolOrchestratorServiceTest` 通过；后端编译和 `git diff --check` 通过。
- 线上能力验收：生产 OACT 调用 `runtime.record.query` 返回 `DAS-DEMO:星轨移动销售助手:执行中`。
- 线上对话验收：生产 `dev-autopilot-pm` 对“现在有哪些项目在执行”返回项目 35% 进度、2 项进行中任务、5.5 小时工时和 2 项变更；通用无法访问提示为 false。
- 发布：`2.8.31 / 5c8953a3284d`；发布前备份四项均非空，backend/frontend healthy，后端 health `UP`、Nginx 校验及 `https://x.agentcici.com/` 200 通过。
