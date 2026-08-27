---
kind: task-status
task_id: TASK-333
feature_id: FEAT-203
status: review
priority: high
owner_role: fullstack-agent
claimed_by: codex
updated_at: 2026-08-27T11:21:49Z
updated_by: codex
---

# TASK-333 - 创建 DEMO 单页单对象完整配置示例

## 范围

- seed 已发布 `demo-example / 1.0.0` 目录、版本和可选依赖。
- 将平台基础应用的租户投影和受控 `OPEN` 动作从硬编码改为目录驱动。
- 新增只读单页单对象示例页，展示实际配置和未写入的 Provider 全参数参考。
- 补齐后端、前端、迁移、浏览器和本地全栈验证。

## 完成条件

- `DEMO示例应用` 在本地应用中心已发布并可进入示例页。
- 示例页只有一个 `ApplicationConfiguration` 对象，实际值全部来自目录 API。
- Provider 参考值不进入运行连接，不伪造测试或启用状态。
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

- HUMAN 使用本地平台账号登录 `https://cici.localhost/platform/login`，确认应用中心显示已发布样例、详情可打开示例页，且页面只有一个 `ApplicationConfiguration` 对象；通过后可将任务从 `review` 转为 `done`。UAT/生产发布需另行授权。
