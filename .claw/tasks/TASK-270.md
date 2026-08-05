---
kind: task-status
task_id: TASK-270
status: in_progress
updated_at: 2026-08-05T16:05:00Z
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

## Progress

- 受保护改名 API 已随 AgentCiCi `2.8.53` 发布；改名会保留既有 Client Secret，并在 AgentCiCi 权威记录、Keycloak client 与 identity mirror 上原子化处理。
- 管理端“机器主体”页的显式“变更 Client ID”确认入口已随生产 `2.8.55 / 9796b475d7d5` 发布。该入口仅由当前组织的 ORG_ADMIN 会话提交，界面会明确提示旧 ID 将失效、Secret 不变及调用方配置同步要求。
- 线上当前仍为旧 Client ID 和旧 DevAutopilot 配置；未伪造 Oliver 会话、未直接改库、未输出 Secret。

## Next Action

- 使用 Oliver 的有效 ORG_ADMIN 会话，在“机器主体 → 悟空 → 变更 Client ID”中输入 `dev-autopilot-developer-wukong` 并确认；随后立即在生产同步 DevAutopilot allowlist 与 root-only CLI 凭据，并验证新 ID 使用原 Secret 的 OACT/任务读取，旧 ID 被拒绝。
