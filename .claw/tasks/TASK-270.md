---
kind: task-status
task_id: TASK-270
status: in_progress
updated_at: 2026-08-05T15:15:00Z
updated_by: ai
assignee: ai
owner_role: integration-agent
assignment_path: n/a
spec_path: docs/specs/FEAT-156-dev-autopilot-identity-roster.md
---

# TASK-270 - 悟空开发者 SERVICE Client ID 规范化改名

## Scope

- 悟空 AgentCiCi SERVICE principal `9aab6f76-5f2f-482b-84a1-871d8a0f7030` 的 Keycloak Client ID：
  `dev-autopilot-developer` → `dev-autopilot-developer-wukong`。
- 保持 SERVICE principal、service-account subject、PRIMARY owner、Secret、最小权限和生命周期不变。
- 同步 DevAutopilot 生产 allowlist 与悟空 root-only CLI 凭据的 Client ID；不输出或保存 Secret。

## Acceptance

- 改名请求仅允许同组织 ORG_ADMIN 执行，目标属于当前公司且新 ID 格式、平台唯一性和 Keycloak 唯一性均已验证。
- AgentCiCi 本地持久化失败时补偿恢复 Keycloak 原 Client ID；审计仅记录改名结果，不记录 Secret。
- 线上改名后，旧 ID 不可换取 token；新 ID 使用原有受管 Secret 可完成 OACT 交换与 DevAutopilot 任务读取。

## Next Action

- 提交并发布后端实现，创建生产备份后通过受保护治理 API 执行改名和完整回归。
