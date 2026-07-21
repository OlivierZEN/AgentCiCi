---
kind: task-status
task_id: TASK-219
status: in_progress
updated_at: 2026-07-22T00:18:00+08:00
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

1. 已完成 Shell、导航、总览、主题隔离及技能、工具、套餐、评测的非模型路由拆分。
2. 在 TASK-218 合并后处理模型子路由，保持不修改其独占 `PlatformModelsPage.tsx`。
3. 取得平台账号测试会话后补充受保护运营页的桌面截图、抽屉与八主题持久化验收。

## 本轮验证

- `frontend` 聚焦 Vitest：`theme`、`PlatformBillingPage`、`PlatformSkillsPage` 共 17 项通过。
- `frontend` production build 通过；保留既有 Vite 大 chunk 提示。
- 本地应用内浏览器可访问平台登录页；当前无可用的平台账号会话，因此没有将受保护页面视觉交互伪造为已验收。
