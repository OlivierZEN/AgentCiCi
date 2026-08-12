---
kind: task-status
task_id: TASK-287
status: review
updated_at: 2026-08-11T15:32:00Z
updated_by: codex
assignee: codex
owner_role: frontend-agent
spec_path: docs/specs/FEAT-172-platform-capability-governance-workbench.md
depends_on: TASK-285
---

# TASK-287 - 技能治理 V5 正式 React 落地

- 已合并三个技能菜单，并保留模型配置、平台集成、工具目录原页面。
- 已完成技能/策略包首页、880px 技能速览抽屉、独立技能编辑/预览和策略编辑页。
- 现有技能、策略、版本、依赖 API 与发布/回滚逻辑未改；规划策略包不提供业务动作。
- 前端 44 文件/244 项、生产构建、diff check、本地 health/HTTP 通过。
- 当前无受权平台会话，未绕过登录门禁；待登录后完成正式桌面截图复核。
