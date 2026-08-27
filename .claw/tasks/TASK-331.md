---
kind: task-status
task_id: TASK-331
feature_id: FEAT-201
status: in_progress
priority: critical
owner_role: fullstack-agent
claimed_by: codex
updated_at: 2026-08-27T03:18:15Z
updated_by: codex
---

# TASK-331 - CloudCC 客户互动工作台 SSO 字段契约修复

## 范围

- 将 CloudCC pagecomponent Vue 路径和 UMD DOM fallback 的请求字段统一为 `agentCompanyId`。
- 保留已发布组件/页面属性中的 `agentOrgId` 读取兼容，但不再把旧字段作为规范请求字段发送。
- 后端 DTO 在过渡期接受旧 JSON 字段 `agentOrgId`，内部继续只使用 `agentCompanyId` 语义。
- 补充前后端契约测试，覆盖规范字段、旧字段兼容和禁止只断言成功 URL 而漏验请求体的问题。
- 按 AgentCiCi `2.8.67` 单发布线完成 UAT、生产和目标 CloudCC 页面组件回读。

## 完成条件

- 新 pagecomponent/UMD 请求只发送 `agentCompanyId`，旧页面属性仍能解析到同一租户。
- 旧 pagecomponent 发送 `agentOrgId` 时，后端能完成反序列化和必填校验，不再返回字段缺失 400。
- 定向前后端测试、生产构建、pagecomponent dry-run 和环境域名门禁通过。
- UAT 与生产运行版本、commit、镜像 digest、备份和回滚点可追溯，应用容器健康且状态服务未被重建。
- 目标生产 CloudCC 登录态回读 `/ticket=200`、`/consume=200`，客户互动工作台进入真实 CRM 连接态；HUMAN 业务验收单独记录。

## 当前证据

- 生产真实请求在 2026-08-27 10:52:52 CST 返回 HTTP 400；响应契约探针为 `agentCompanyId must not be blank`。
- 目标租户和匹配成员存在，成员 ACTIVE，CloudCC 用户名和安全标记字段非空；本轮未读取安全标记值。
- 生产 AgentCiCi `2.8.66 / e805c0ef7142` 健康、backend/frontend restart=0，说明不是整体服务故障。
- Keycloak 拒绝 iframe 是 ticket 失败后的次生安全边界，不调整其 `frame-ancestors` 或 `X-Frame-Options`。
- Vue pagecomponent 与 UMD fallback 已改发 `agentCompanyId`，仍读取旧 `agentOrgId` 属性；后端通过 `JsonAlias` 兼容旧请求。
- 前端定向 8 项、全量 56 文件/309 项、production build、UMD 语法检查、后端 `AuthControllerTest` 3 项和 package 均通过。
- CloudCC provider doctor 与 pagecomponent dry-run 通过；当前 customPage V9 引用 pagecomponent V15 id `6a5628cee4b0a577cbba2088`，可作为组件级回滚点。
- 受管环境域名门禁与 `git diff --check` 通过；全局状态校验仅剩既有历史债务，未报告 TASK-331/FEAT-201 新错误。
