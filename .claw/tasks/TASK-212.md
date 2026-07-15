---
kind: task-status
task_id: TASK-212
status: in_progress
updated_at: 2026-07-15T13:48:03Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-212.yaml
spec_path: docs/specs/FEAT-117-skill-dag-governance-phase1.md
---

# TASK-212 - Skill DAG 只读治理闭环 Phase 1

## Scope

- 从现有工作流 Skill 版本钉住、Agent 绑定和资源白名单派生统一只读 DAG。
- 为 Agent Builder、调试解析链路和平台 Skill 影响分析提供生产级查询与桌面端视图。
- 完成后端与前端测试、桌面视觉/交互验收和生产发布。
- 不新增 DAG 编辑、Skill-to-Skill 调用、数据库迁移或移动端范围。

## Current State

- 用户已批准 FEAT-117 Phase 1 范围并要求生产就绪后上线。
- 已确认现有依赖治理底座可复用；当前正在完成任务分配、授权检查和实现计划。
- 实现尚未开始，生产仍运行 `2.7.7 / e47979167af8`。

## Next Action

- 验证 assignment 对代表性后端、前端、测试和治理文件的授权，提交并推送分配事实到 `origin/main`。
- 按 TDD 实现统一图服务、API、共用图组件和两处产品接入。

## Changed Files

- `docs/specs/FEAT-117-skill-dag-governance-phase1.md`
- `.claw/tasks/TASK-212.md`
- `.claw/assignments/TASK-212.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`

## Handoff

- 分支：`codex/TASK-212-skill-dag-governance`。
- 必须通过本机 SSH challenge-response 门禁后编辑。
- 生产发布严格使用 `docs/production-release-runbook.md` 和 `scripts/release-acr.sh`。
