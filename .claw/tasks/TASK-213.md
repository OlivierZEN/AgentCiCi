---
kind: task-status
task_id: TASK-213
status: in_progress
updated_at: 2026-07-17T06:38:56Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-213.yaml
spec_path: docs/specs/FEAT-118-general-ontology-modeling-platform.md
---

# TASK-213 - 通用本体建模与语义查询平台 V1

## Scope

- 交付领域无关本体内核、业务可视化画布、AI 草稿副驾驶、映射目录、确定性契约编译和受限只读语义查询。
- 用项目交付 `INLINE_SAMPLE` 与 CloudCC CRM 两个领域/适配器验证通用性。
- 完成租户隔离、版本治理、自动化测试、桌面产品验收和生产发布。

## Current State

- 用户已批准 FEAT-118 的推荐设计、AI/人工权限边界与只读 V1 范围，并明确要求无需再次确认，直接实现和发布生产。
- 通用本体 V1 已进入发布前终局安全审查；V82 保持不变。
- 安全审查要求区分“参考包安装”与“同一管理员手工创建的完全同元数据工作区”；已批准新增正向 V83，仅保存参考包来源和内容指纹。

## Next Action

- 验证 V83 精确路径授权，提交并推送本次分配到 `origin/main`，再继续 TDD 实现和终局复审。

## Changed Files

- `docs/specs/FEAT-118-general-ontology-modeling-platform.md`
- `docs/specs/FEAT-118-general-ontology-modeling-platform-plan.md`
- `.claw/assignments/TASK-213.yaml`
- `.claw/tasks/TASK-213.md`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `backend/src/main/resources/db/migration/V83__ontology_workspace_provenance.sql`

## Handoff

- 分支：`codex/TASK-213-general-ontology-v1`。
- 严格遵循 `docs/production-release-runbook.md`，未完成真实验证不得标记 done 或声称已上线。
