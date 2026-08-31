---
kind: task-status
task_id: TASK-333
feature_id: FEAT-203
status: review
priority: high
owner_role: fullstack-agent
claimed_by: codex
updated_at: 2026-08-31T04:28:01Z
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

- 实现提交 `40a27a2b2983` 已进入本地 `main`，远程 `main` 未推送；最终运行代码制品对应同一提交。
- 后端连接/目录聚焦测试与 package、前端聚焦 2 文件/7 项、全量 60 文件/332 项、production build、域名门禁和 `git diff --check` 通过；build 仅保留既有大 chunk 警告。
- PostgreSQL 16.9 空库成功校验并执行 125 个迁移至 V128；专用迁移测试回读唯一 `demo-example.lifecycle / DRAFT / activeRevisionId=null / r1 / NOT_TESTED` 连接。
- 本地 repeatable migration 成功重跑 1 次；真实数据库回读为 `demo-example / PUBLISHED / 1.0.0 / NONE / providerBindingKey=null`，连接 Base URL 为保留测试域名，Secret 仅为引用名。
- backend/frontend 运行 `2.8.68-dev.40a27a2 / 40a27a2b2983`，镜像分别为 `sha256:d55b923c4c56c7d21585595b89f88d304605b5d0c7a8b755d0d84bd6085081e4` 与 `sha256:1c56768632094ad37978e9bcfffb8fadf5a88828ae634954aff794f25cd89ef1`；两容器 healthy/restart=0，其他八个容器 ID 未变化。
- `https://cici.localhost/platform/internal-applications/demo-example/example=200`；运行 bundle 包含连接实际配置字段，匿名连接 API 返回 JSON 401，最近 Nginx 5xx 为 0。
- 应用内浏览器和 Chrome 均没有本地平台登录态，受保护页面停在“运营平台登录”；登录后详情计数 1 和示例页视觉待 HUMAN 确认。UAT 与生产未修改。

## 下一步

- 由 HUMAN 登录本地运营平台，确认详情“运行连接”计数为 1，并打开示例页核对 20 项连接参数及 `草稿 / 未测试 / 未启用` 状态。UAT/生产发布需另行授权。
