---
kind: task-status
task_id: TASK-266
status: review
updated_at: 2026-08-04T15:30:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: frontend-agent
assignment_path: .claw/assignments/TASK-266.yaml
spec_path: docs/specs/FEAT-158-semattice-business-object-list-preview.md
---

# TASK-266 - AI表格业务对象列表高保真预览

## Current State

- 已完成 FEAT-158 设计登记和写入范围授权。
- 已将 AI表格菜单从外部 CRM 登录 iframe 替换为 AgentCiCi 内置业务对象列表预览。
- 已加入对象切换、关键词查询、表头设置、刷新反馈、分页、主题切换和记录详情抽屉。
- Blocked: none

## Scope

- 仅实现 AgentCiCi 用户端桌面 AI表格菜单对应的业务对象列表高保真预览。
- 使用前端演示数据完成查询、对象切换、表头设置、分页和详情抽屉交互。
- 不改动后端、Semattice API、鉴权和移动端。

## Next Action

- 等待用户确认高保真 UI 的视觉方向和字段结构，再决定真实 Semattice API 接入范围。

## Verification

- `npm run build`：通过，TypeScript 与 Vite 生产构建成功；仅保留既有 bundle 大小提示。
- `npm test -- --run`：通过，33 个测试文件 / 206 项测试。
- `git diff --check`：通过。
- Browser desktop：以受控演示登录响应访问 `/app`，点击“AI表格”后验证默认鎏金账房、主题菜单 8 项主题、切换“星河幻境”、对象切换到商机、表头显示隐藏、记录详情抽屉；默认 1200px 截图无横向滚动条。截图：`.playwright-cli/page-2026-08-04T15-24-34-870Z.png`、`.playwright-cli/page-2026-08-04T15-25-10-245Z.png`。
- Browser limit：本地 backend 未启动，浏览器控制台存在 API 连接失败日志；页面验证使用 Playwright 受控 `/auth/me` 演示响应，未伪造生产凭据或真实 Semattice 数据。
