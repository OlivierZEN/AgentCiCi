---
kind: task-status
task_id: TASK-333
feature_id: FEAT-203
status: in_progress
priority: high
owner_role: fullstack-agent
claimed_by: codex
updated_at: 2026-08-31T04:15:09Z
updated_by: codex
---

# TASK-333 - 创建 DEMO 单页单对象完整配置示例

## 范围

- seed 已发布 `demo-example / 1.0.0` 目录、版本和可选依赖。
- 将平台基础应用的租户投影和受控 `OPEN` 动作从硬编码改为目录驱动。
- 新增只读单页单对象示例页，展示实际配置和真实登记的 Provider 连接修订。
- seed 一条属于 `demo-example` 的 `DRAFT / NOT_TESTED` 运行连接；使用保留测试域名和 Secret 引用名，不外呼、不伪造测试或启用状态。
- 补齐后端、前端、迁移、浏览器和本地全栈验证。

## 完成条件

- `DEMO示例应用` 在本地应用中心已发布并可进入示例页。
- 示例页只有一个 `ApplicationConfiguration` 对象，实际值全部来自目录 API。
- 应用详情显示 1 条运行连接；示例页从连接 API 回读全部参数，并明确显示草稿、未测试、未启用。
- 已发布零初始化版本不引用未测试连接，不伪造测试或启用状态。
- 代码提交并合并到 AgentCiCi 本地 `main`；本地 backend/frontend 从同一 `main` 提交构建和回读。
- UAT/生产未修改；后续发布必须另行授权。

## 当前证据

- UAT 只读检查确认总计 5 个应用，其中 `BimoApp1`、`测试应用1` 均为草稿且 0 个版本；`BimoApp1` 详情显示 0 个运行连接。
- 本地 `main@5f6ce44a` 已包含目录 seed、通用平台基础投影、服务端安全相对路由、详情入口、租户 `OPEN` 动作和单页单对象示例页；远程 `main` 未推送。
- 后端聚焦测试 2 项、前端聚焦 3 文件/15 项、前端全量 57 文件/312 项、后端 package、production build 和 `git diff --check` 通过；production build 仅保留既有大 chunk 警告。
- repeatable migration `demo example application` 已在本地 PostgreSQL 成功执行；数据库回读为 `demo-example / DEMO示例应用 / PUBLISHED / 1.0.0 / NONE`，并有唯一可选依赖 `semattice >=1.0.0 / OPTIONAL / AUTO_PROVISION_ALLOWED`。
- 本地 backend/frontend 运行 `2.8.67-dev.5f6ce44 / 5f6ce44a`，镜像 ID 分别为 `sha256:34c05e8f04e6a6f524f3d287115db168fd5910f737eeff0acce6139954bfbbf1` 和 `sha256:c97c5ec31c447473b86d2185484a9a3c9e2cfadd9cce1e022595164fcccce186`；两容器 healthy/restart=0，15 分钟 backend severe、frontend HTTP 5xx/nginx severe 均为 0。
- `https://cici.localhost/platform/internal-applications/demo-example/example=200`，运行 bundle 包含 `DEMO示例应用`；匿名目录 API 为 JSON 401，浏览器访问受保护示例路由回到运营平台安全登录。
- 当前本地浏览器没有平台登录态；应用中心列表、详情和示例页的登录态视觉/交互待 HUMAN 登录后确认。UAT 与生产未修改。

## 下一步

- 扩展 repeatable seed 和示例页回读，运行迁移与前后端门禁，从最终本地 `main` 更新 `https://cici.localhost/`，再由 HUMAN 确认详情运行连接计数为 1、参数完整且状态诚实。UAT/生产发布需另行授权。
