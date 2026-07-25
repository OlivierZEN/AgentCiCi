---
kind: task-status
task_id: TASK-245
status: review
updated_at: 2026-07-25T03:05:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-245.yaml
spec_path: docs/specs/FEAT-138-assistant-admin-session-entrypoint.md
---

# TASK-245 - 前台会话内置组织管理入口

## Current State

- Status: `review`
- Next action: 使用真实组织管理员会话验收 Semattice 跳转和浏览器返回 AgentCiCi；不伪造受保护交互。
- Blocked: none
- Spec: `docs/specs/FEAT-138-assistant-admin-session-entrypoint.md`
- Assignment: `.claw/assignments/TASK-245.yaml`

## Progress

- 用户已确认组织菜单内的“管理后台”形态和独立 Admin 登录入口关闭规则。
- 已实现：管理员组织行显示轻量“管理后台”命令；跨组织先调用 `/auth/switch-company`，随后复用返回会话进入 `/admin`。
- 已关闭独立后台表单：`/admin/login`、无前台管理员会话的 `/admin/*` 均回到 `/app`；后台返回前台只清除 `cici_admin_token` 镜像。
- 用户进一步确认：在组织控制台标题右侧放置产品下拉入口，列表可在 AgentCiCi 管理端与 Semattice 管理端之间切换。
- 已实现：标题右侧下拉明确标识当前 AgentCiCi 管理端，并为 Semattice 管理端提供键盘可达、Esc/点外关闭和进入中禁用状态。
- 已实现：受保护的 `/auth/semattice/console` 仅对当前 `OWNER` / `ORG_ADMIN` 按 TenantContext 重新核验后签发短时 OACT；前端只校验固定 HTTPS 主机并立即以 fragment 跳转，不持久化或记录 token。
- 用户已于 2026-07-25 明确授权本任务合并主线并进行一次受控生产发布。
- 已合并 `main`（`ac598745e588`）并发布 `2.8.16`；仅 backend/frontend 重建，四个状态服务保持运行。

## Changed Files

- `docs/specs/FEAT-138-assistant-admin-session-entrypoint.md`
- `.claw/tasks/TASK-245.md`
- `.claw/assignments/TASK-245.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `frontend/src/assistant/AssistantApp.tsx`
- `frontend/src/assistant/cici-ui.css`
- `frontend/src/help/helpContent.ts`
- `frontend/src/suite/AgentCiciWebsite.tsx`
- `frontend/src/admin/adminSession.ts`
- `frontend/src/admin/adminSession.test.ts`
- `frontend/src/admin/AdminGuard.tsx`
- `frontend/src/admin/AdminShell.tsx`
- `frontend/src/App.tsx`
- `frontend/src/admin/AdminLogin.tsx`（删除）
- `README.md`
- `AgentCiCi智能体平台实现设计方案.md`
- `DESIGN.json`
- `backend/src/main/java/com/codehouse/ciciassistant/auth/api/AuthController.java`
- `backend/src/main/java/com/codehouse/ciciassistant/auth/service/AuthService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/auth/service/OfficialAccessTokenService.java`
- `backend/src/test/java/com/codehouse/ciciassistant/auth/OfficialAccessTokenServiceTest.java`
- `frontend/src/admin/adminAuthScope.test.ts`
- `frontend/src/styles.css`

## Verification

- Status: `passed_with_manual_acceptance_pending`
- Evidence: 合并后 OACT 定向测试、后端编译、前端生产构建、Compose 解析及差异检查通过。`2.8.16` backend/frontend manifests 已核验；生产备份四项均非空，六服务健康，`/actuator/health` 为 `UP`、版本为 `2.8.16 / ac598745e588`、Nginx 校验通过；`x.agentcici.com` 与 `agentcici.com` 均为 200，匿名 `/auth/me` 和 `/auth/semattice/console` 均为预期 401。当前会话没有真实组织管理员凭据，产品菜单签发/跳转与浏览器返回未伪造验收。

## Handoff

- 仅复用现有用户会话和组织切换 API；OACT 只能经短时响应内存与 URL fragment 传递，禁止写入 localStorage、sessionStorage、Cookie、日志或 query string；角色校验必须继续 fail closed。真实验收应覆盖当前组织进入、跨组织进入、普通成员无入口/直达拒绝、Semattice 跳转以及浏览器返回 AgentCiCi 后助手会话仍有效。
