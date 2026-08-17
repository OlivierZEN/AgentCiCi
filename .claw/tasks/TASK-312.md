---
kind: task-status
task_id: TASK-312
status: review
updated_at: 2026-08-17T09:35:00Z
updated_by: codex
assignee: codex
owner_role: frontend-agent
spec_path: docs/specs/FEAT-148-app-auto-oidc-redirect.md
---

# TASK-312 - 登录中转页移除手动触发区

## Current State

- Status: `review`
- Next action: 用户确认本地效果后，另行决定是否冻结并发布下一 UAT beta；本任务未修改 UAT 或生产。
- Blocked: none

## Scope

- 仅修改 AgentCiCi 前端登录中转页结构、局部样式和定向测试。
- 复用既有同源 OIDC 入口和一次性自动跳转决策。
- 不修改后端、Keycloak、环境地址、数据库、UAT 或生产环境。

## Acceptance

- 正常未登录中转态只显示现有主视觉，不出现说明、按钮、退出状态或联系管理员文案。
- `/app` 无有效会话时无需点击即可进入统一身份登录。
- 回调票据继续由专用流程消费，失败时显示无手动按钮的最小错误提示。
- 定向测试、前端全量测试、生产构建、本地主线和 `cici.localhost` 桌面路由验证通过。

## Evidence

- 本地主线代码提交：`745ee145f53a15d76aecebf5ff3cf056d54d6b7f`。
- 定向测试 7/7、前端全量 51 文件/282 项和 production build 通过。
- `cici-frontend` 从本地 `main` 构建为 `2.8.61-dev.745ee14`，镜像 revision `745ee145f53a`，healthy、restart=0。
- `https://cici.localhost/app` 返回 200；全新未登录浏览器会话未点击即进入 `sso.localhost` OIDC 登录页。
- 受控回调态桌面检查：主视觉存在，旧表单容器 0、按钮 0、页面正文为空，控制台 0 error / 0 warning。
