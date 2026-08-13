---
kind: task-status
task_id: TASK-299
feature_id: FEAT-180
status: review
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

## 交付证据

- 实现提交：`fa1f875`，已归并本地 `main`；当前运行主线 `1df52ac` 包含该提交。
- `mvn -q -DskipTests compile`、目标单元测试和 `mvn -q -DskipTests package` 通过。
- 本地环境前后端均回读为 `2.8.61-dev.1df52ac`；旧登录令牌按无兼容窗口策略要求重新登录。
- 真实租户用户端到端复验待本地 IdP 中可用的测试身份完成；现有 UAT DEMO 凭据在本地 IdP 返回无效，未执行密码重置或认证绕过。
