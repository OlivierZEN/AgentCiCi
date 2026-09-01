---
kind: task-status
task_id: TASK-350
assignee: codex
owner_role: fullstack-agent
status: in_progress
branch: codex/task-350-oidc-logout
pr_url: n/a
spec_path: docs/specs/FEAT-208-oidc-rp-initiated-logout.md
updated_at: 2026-09-01T11:32:06Z
updated_by: codex
---

# TASK-350 - 门户统一身份完整注销

## Goal

修复门户左下角退出后因 Keycloak SSO 仍有效而自动重新进入系统的问题，完成 AgentCiCi 与统一身份的完整浏览器注销。

## Scope

- OIDC callback 服务端登录会话与安全 Cookie。
- Keycloak RP-Initiated Logout 同源入口和固定回跳。
- 门户退出时序、聚焦测试、全量构建和本地真实浏览器回归。

## Done When

- [x] 根因和安全边界写入 FEAT-208。
- [x] 前后端完整注销链及聚焦测试通过。
- [x] 聚焦后端 12 项、前端全量 62 文件/339 项、backend package、frontend build、差异与域名门禁通过。
- [ ] 提交归并本地 main 并从该 commit 更新本地 backend/frontend。
- [ ] `cici.localhost` 真实登录后退出不再静默重登。

## Handoff

- 当前主工作树的 `.claw/current-status.md`、`.claw/task-board.md` 有其他任务未提交改动；本任务在隔离 worktree 实现，不回退或夹带这些改动。
- 默认后端全量测试在既有 `KnowledgeBaseLifecycleIntegrationTest` 尝试连接 `localhost:5432` 时失败；聚焦测试和 `mvn -q -DskipTests package` 均通过，不把该环境失败记为代码通过。
- 本地 Keycloak `agentcici-bff` 的 `post.logout.redirect.uris` 当前为空；完整浏览器回归前需要父仓 `cc-local-stack` 持久登记对应 `/app`，本任务未越权修改父仓。
- UAT、生产和它们的 Keycloak Client 配置不在本次授权范围。
