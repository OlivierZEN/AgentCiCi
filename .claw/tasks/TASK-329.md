---
kind: task-status
task_id: TASK-329
feature_id: FEAT-014
status: review
priority: high
owner_role: fullstack-agent
claimed_by: codex
updated_at: 2026-08-25T09:31:01Z
updated_by: codex
---

# TASK-329 - 修复管理后台技能导出

## 范围

- 修复租户自定义已发布技能导出时，模型返回的 `manifest.json` 格式字段漂移导致服务端校验失败、无法下载的问题。
- 由服务端规范化技能包固定身份与格式字段，继续对最终八文件技能包执行 JSON、格式和敏感信息校验。
- 为技能列表导出补齐进行中状态以及网络、非 JSON 和下载异常反馈，避免点击后静默无结果。
- 补充后端模型 manifest 规范化与前端响应处理回归测试。

## 完成条件

- 模型输出把 `format` 写成非标准值时，服务端仍生成固定 `universal-skill-package@1.0` manifest，且不允许模型改写 `packageId`、技能版本或导出身份字段。
- 模型输出的其他 JSON、文件结构或敏感信息不合规时仍失败关闭，不能绕过最终包校验。
- 技能列表导出期间显示明确状态；创建任务、解析响应和下载任一阶段失败时均显示可读错误。
- 后端聚焦测试、前端聚焦测试、前端生产构建、后端 package、diff check 和状态校验完成。
- 实现提交进入 AgentCiCi 本地 `main`；本地全栈只能从该 `main` 提交构建并回读版本/健康/下载证据。

## 当前证据

- UAT `2.8.66-beta.2 / 525f0f610926` 公开 smoke 全部通过；本次只读浏览器复现没有修改 UAT 配置或部署。
- `POST /skills/137/exports` 到达 UAT backend 并返回 HTTP 400；页面捕获的业务错误为 `Export package validation failed: manifest format mismatch`，没有下载事件。
- 当前 `tryStandardizeByModel` 只检查模型字段非空，直到最终 `validateExportPackage` 才验证固定格式；因此可解析但格式漂移的模型 manifest 不会被规范化。
- 服务端已覆盖模型 manifest 的固定格式、包身份、技能版本、发布状态和导出身份字段；非对象 manifest 仍失败关闭，最终包校验保持不变。
- `SkillPackageServiceTest` 2 项、前端全量 56 文件/308 项、前端 production build、backend package 和 `git diff --check` 通过。
- `SkillGovernanceIntegrationTest` 首次被本机 `localhost:5432` 连接拒绝阻断；改用一次性 PostgreSQL 后已能完成 119 项迁移到 V123 和 JPA 初始化，但既有测试 OACT 配置/登录前置仍分别在 Context 或登录断言前失败，导出断言未执行，未报告为通过。
- 实现提交 `fada2e5f0b07` 已进入 AgentCiCi 本地 `main`；backend/frontend 镜像分别为 `sha256:eb27e47a6a16...`、`sha256:f2be0fc6cc88...`，版本与 revision 均回读为 `2.8.66-dev.fada2e5 / fada2e5f0b07`。
- 本地仅重建 backend/frontend；两容器 healthy/restart=0，`/actuator/health=UP`、`/system/version` 与镜像 label 一致，frontend Nginx 有效，运行 bundle 包含导出进行中文案，正式 `/admin/skills=200`、匿名 `/auth/me` 与 `/skills` 均为 JSON 401。
- DevAutopilot、Semattice、PostgreSQL、Redis、RabbitMQ、Qdrant、Keycloak 和 Nginx 的容器 ID 保持不变；UAT/生产未部署或改配置，远程 `main` 未推送。

## 下一步

- HUMAN 使用本地组织管理员账号登录 `https://cici.localhost/admin/skills`，对自定义已发布技能执行一次真实导出，确认 zip 下载、文件名与八文件内容；完成后再将任务由 `review` 置为 `done`。UAT 仍运行旧候选，不将其失败误报为修复后验收。
