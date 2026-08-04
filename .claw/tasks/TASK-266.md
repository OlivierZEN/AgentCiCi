---
kind: task-status
task_id: TASK-266
status: in_progress
updated_at: 2026-08-05T00:00:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-266.yaml
spec_path: docs/specs/FEAT-158-semattice-business-object-list-preview.md
---

# TASK-266 - AI表格业务对象实时列表

## Current State

- 用户已确认高保真 UI 形态，并授权生产实现及线上发布。
- 已确认数据平台提供已发布元数据读取与带对象/字段/记录范围授权的记录查询能力。
- 已完成真实只读对象目录、记录查询、游标分页、已索引文本前缀查询、成员隔离列偏好和同源 API 代理实现。
- 已复现用户截图中的 `Authentication required`，并确认不是 Semattice 或 OACT 拒绝。
- 已确认前端 `AiTableBusinessObjectList` 直接使用 `fetch(..., { credentials: "same-origin" })`，而工作台认证 API 使用 `authFetch(LS_ASSISTANT_TOKEN, ...)` 追加 Bearer Token；目录请求因此缺少 `Authorization` 并被 AgentCiCi 鉴权层拒绝。
- Blocked: none

## Scope

- 在 AgentCiCi 用户端实现真实、只读的 AI表格对象目录和记录列表。
- 后端以当前用户短期 OACT 调用数据平台，前端通过既有 `authFetch` 调用同源 AgentCiCi API；Vite/Nginx 必须将 `/ai-table` 精确代理至后端。
- 完成服务端游标分页、受索引约束的查询、列配置持久化、详情以及完整异常状态。
- 维持桌面端多主题高保真结构；不实现写入、批量、导出或移动端。

## Next Action

- 将目录和记录查询接入现有 Bearer 会话请求器，补充定向回归，发布新的不可变生产版本并做鉴权边界与线上健康验收。

## Verification

- 聚焦后端 2 项测试、后端编译、前端构建、前端全量 33 文件/206 项测试、Compose config 与 diff 检查通过。
- 本机完整后端套件受未配置 PostgreSQL 的既有集成测试阻塞；Playwright CLI 本机 Chromium 对 Vite 地址返回工具环境 500，但 curl 同地址为 200。两项限制均不伪造结果，生产按受权会话继续验收。
- 生产：发布前四类备份非空；backend/frontend ACR digest 已 inspect；Nginx `-t`、六容器、backend health/version、x HTTPS、HTTP 跳转与 AI表格匿名 401 JSON 均通过。生产 OACT 已启用、数据平台地址已配置，逗号分隔 scopes 包含 `metadata.read` 与 `runtime.record.read`。本会话无受权成员登录 Cookie/测试账号，未伪造真实记录回读。
