---
kind: task-status
task_id: TASK-219
status: ready
updated_at: 2026-07-21T21:10:00+08:00
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: frontend-agent
assignment_path: .claw/assignments/TASK-219.yaml
spec_path: docs/specs/FEAT-124-platform-operations-information-architecture.md
---

# TASK-219 - 运营管理端信息架构与独立主题重构

## Scope

- 按 FEAT-124 重构 `/platform/*` 的二级信息架构、页面职责、独立详情/编辑流和平台独立主题设置。
- 保留当前 API、数据模型、权限和高风险确认，不新增移动端或后端业务改动。

## Current State

- 用户选定“运营中枢”视觉方向，并明确二级菜单必须按照功能将页面拆开。
- 平台账号已有独立服务端 `themeCode`，但主题入口隐藏在侧栏 details 中，本地预览缓存仍与其他认证端共用。
- TASK-218 正在独占处理厂商模型目录能力；模型页拆分等待该任务完成后合并。

## Next Steps

1. 完成任务分配提交并在专用分支通过 task-scoped 身份门禁。
2. 先落地 Shell、导航、总览、主题隔离和不与 TASK-218 重叠的页面拆分。
3. 在 TASK-218 合并后再处理模型子路由，随后完成全量桌面视觉和交互验证。
