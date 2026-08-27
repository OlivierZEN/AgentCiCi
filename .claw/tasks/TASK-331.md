---
kind: task-status
task_id: TASK-331
feature_id: FEAT-201
status: done
priority: critical
owner_role: fullstack-agent
claimed_by: codex
updated_at: 2026-08-27T03:25:54Z
updated_by: codex
---

# TASK-331 - CloudCC 客户互动工作台 SSO 字段契约修复

## 范围

- 将 CloudCC pagecomponent Vue 路径和 UMD DOM fallback 的请求字段统一为 `agentCompanyId`。
- 保留已发布组件/页面属性中的 `agentOrgId` 读取兼容，但不再把旧字段作为规范请求字段发送。
- 后端 DTO 在过渡期接受旧 JSON 字段 `agentOrgId`，内部继续只使用 `agentCompanyId` 语义。
- 补充前后端契约测试，覆盖规范字段、旧字段兼容和禁止只断言成功 URL 而漏验请求体的问题。
- 将兼容代码提交并推送 `main`；以最小影响方式发布目标 CloudCC 页面组件并完成生产登录态回读。AgentCiCi 后端 alias 随后续 `2.8.67` 标准发布线交付，不为本次组件热修复扩大生产变更面。

## 完成条件

- 新 pagecomponent/UMD 请求只发送 `agentCompanyId`，旧页面属性仍能解析到同一租户。
- 旧 pagecomponent 发送 `agentOrgId` 时，后端能完成反序列化和必填校验，不再返回字段缺失 400。
- 定向前后端测试、生产构建、pagecomponent dry-run 和环境域名门禁通过。
- 修复提交进入本地与远程 `main`；CloudCC 组件发布版本、组件 ID、自定义页引用和源代码回滚提交可追溯。
- 目标生产 CloudCC 登录态回读客户互动工作台进入真实 CRM 连接态；HUMAN 业务验收单独记录。

## 当前证据

- 生产真实请求在 2026-08-27 10:52:52 CST 返回 HTTP 400；响应契约探针为 `agentCompanyId must not be blank`。
- 目标租户和匹配成员存在，成员 ACTIVE，CloudCC 用户名和安全标记字段非空；本轮未读取安全标记值。
- 生产 AgentCiCi `2.8.66 / e805c0ef7142` 健康、backend/frontend restart=0，说明不是整体服务故障。
- Keycloak 拒绝 iframe 是 ticket 失败后的次生安全边界，不调整其 `frame-ancestors` 或 `X-Frame-Options`。
- Vue pagecomponent 与 UMD fallback 已改发 `agentCompanyId`，仍读取旧 `agentOrgId` 属性；后端通过 `JsonAlias` 兼容旧请求。
- 前端定向 8 项、全量 56 文件/309 项、production build、UMD 语法检查、后端 `AuthControllerTest` 3 项和 package 均通过。
- CloudCC provider doctor 与 pagecomponent dry-run 通过；发布前 customPage V9 引用 pagecomponent V15 id `6a5628cee4b0a577cbba2088`，该快照与修复前 Git 提交共同构成回滚依据。
- 受管环境域名门禁与 `git diff --check` 通过；全局状态校验仅剩既有历史债务，未报告 TASK-331/FEAT-201 新错误。
- 修复提交 `ebea2febe1d8a15f3c802f48a7ab7dee480bedbd` 已进入本地和远程 `main`。
- CloudCC pagecomponent 在同一组件 ID `6a5628cee4b0a577cbba2088` 上由 V15 发布为 V16；customPage 保持 V9 且继续引用该 ID，`verify injectionPage` 返回 `status=passed, issues=[]`，无需额外 bind。
- 生产登录态重载后显示“CloudCC CRM 已连接”，加载当前用户、客户队列、客户详情与 AI 助理，浏览器错误日志为 0；未执行写业务数据的交互。
- 本次未发布或重建 AgentCiCi backend/frontend，生产仍为 `2.8.66 / e805c0ef7142`；后端旧字段 alias 已在 `main`，等待 `2.8.67` 常规发布。组件回滚为从修复前提交 `e8e3080987c0d0256b79658deacd4f0867ffe069` 重新发布旧 Vue/UMD 到同一组件的新版本。
