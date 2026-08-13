---
kind: task-status
task_id: TASK-299
feature_id: FEAT-180
status: in_progress
priority: critical
owner_role: integration-agent
claimed_by: codex
depends_on: INT-017
---

# TASK-299 - 签发内部应用统一用户令牌

## 范围

- 将公司成员登录从私有 HS256 Token 切换为 AgentCiCi RS256 生态用户令牌。
- AgentCiCi 业务 API 验证 `agentcici-api` audience；DevAutopilot handoff 返回同类令牌。
- 保留平台管理员 Token 和 SERVICE OACT 的独立主体边界，不兼容旧公司 HS256 Token。

## 验收

- 登录令牌包含统一主体、成员、租户、角色、scope 与受控多 audience，默认有效期 7200 秒。
- AgentCiCi `/ai/chat` 可验证该令牌；错误 audience、类型或过期令牌返回 401。
- handoff 只通过一次性票据传递，不把令牌放入 URL。
