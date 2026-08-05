---
kind: task-status
task_id: TASK-271
status: done
updated_at: 2026-08-05T07:59:27Z
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
- 已发布生产 `2.8.54 / 9a0fe88bf59f`，前后端容器均健康，`x.agentcici.com` 返回新的前端静态包。
- Blocked: none

## Scope

- 侧栏当前组织入口和组织切换菜单名称悬浮时展示完整组织名称。
- 保持当前鼠标进入菜单、键盘焦点、组织切换和管理后台进入逻辑不变。
- 不修改主题、布局尺寸、组织数据、权限或后端 API。

## Next Action

- 已完成。用户可在当前组织入口或组织切换菜单名称上悬停查看完整组织名称。

## Verification

- `npm test -- src/assistant/AssistantApp.test.ts` 通过（1 test）。
- `npm run build` 通过（TypeScript + Vite）；仅保留既有 bundle-size 提示。
- 生产：发布前环境、PostgreSQL、KB、Qdrant 备份均非空；六容器 healthy、backend health=UP、Nginx 有效、版本为 `2.8.54 / 9a0fe88bf59f`、x HTTPS=200、HTTP=301，线上静态包含“当前组织：”提示文本。无受权会话，未伪造菜单实测或组织数据。
