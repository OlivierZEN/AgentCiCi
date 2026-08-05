---
kind: task-status
task_id: TASK-271
status: in_progress
updated_at: 2026-08-05T07:55:12Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: frontend-agent
assignment_path: .claw/assignments/TASK-271.yaml
spec_path: docs/specs/FEAT-161-organization-switch-full-name-tooltip.md
---

# TASK-271 - 组织切换全称悬浮提示

## Current State

- 用户反馈侧栏组织首字符和组织切换菜单中的名称截断后，无法准确识别当前或待切换的组织。
- 当前 `companyName` 已提供完整名称，菜单仅因紧凑布局而视觉截断。
- 已为左侧当前组织入口和菜单名称元素补充完整名称的原生悬浮提示，未变更组织切换或管理后台逻辑。
- Blocked: none

## Scope

- 侧栏当前组织入口和组织切换菜单名称悬浮时展示完整组织名称。
- 保持当前鼠标进入菜单、键盘焦点、组织切换和管理后台进入逻辑不变。
- 不修改主题、布局尺寸、组织数据、权限或后端 API。

## Next Action

- 提交并发布该前端微调，完成线上静态包与入口 smoke 验证。

## Verification

- `npm test -- src/assistant/AssistantApp.test.ts` 通过（1 test）。
- `npm run build` 通过（TypeScript + Vite）；仅保留既有 bundle-size 提示。
