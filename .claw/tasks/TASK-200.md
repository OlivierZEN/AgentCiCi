---
kind: task-status
task_id: TASK-200
status: done
updated_at: 2026-07-14T01:22:40+08:00
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-200.yaml
spec_path: docs/specs/FEAT-106-multi-tenant-agent-evaluation-control-plane.md
---

# TASK-200 - 多租户智能体评测控制面生产落地

## Scope

- 按 FEAT-106 完成平台、租户、Agent Builder 与 Ops 四个表面的评测体系。
- 升级现有 V67 数据模型、运行结果、断言、版本快照、发布门禁和 Trace 回流。
- 将评测从 Agent Builder“发布渠道”页面移出，新增独立“评测”Tab。
- 完成自动化测试、桌面端浏览器、生产构建与发布就绪验证。

## Initial Findings

- 现有评测骨架已具备 Suite、Case、Run、Result 和 blocking gate，可做兼容升级。
- 当前 Builder 基础评测卡片位于 `activeEditorTab=publish`，与 IM/API 发布渠道语义混淆。
- 当前断言和运行快照不足以支撑平台标准、行业包、租户私有资产和生产问题闭环。
- 平台 `/platform/*`、租户 `/admin/*`、Agent Builder 和 Ops Trace 已有可复用壳层与权限边界。

## Implementation Plan

- 建立 FEAT-106、TASK-200、DEC-027 和任务授权。
- 实现 V79 兼容迁移与多租户评测领域服务/API。
- 实现多断言、运行快照、结果失效、版本对比、发布门禁和问题闭环。
- 实现平台智能体质量、租户 AI 质量、Builder 独立评测 Tab 和 Trace 回流页面。
- 补齐后端集成测试、前端测试/构建、浏览器视觉检查和发布 dry-run。

## Verification

- `MANAGER-001` 通用 SSH challenge 登录：allowed。
- TASK-200 任务范围 SSH challenge 登录：allowed。
- `check-assignment.py` 对规格、状态、V79、后端 Agent/Platform、前端 App/Admin/Platform/Builder 代表文件：allowed。
- `git diff --check`：success。
- V79 在干净 PostgreSQL 测试库及本地运行库迁移成功，schema version 为 79。
- `AgentProductionReadinessIntegrationTest`、`AgentEvaluationControlPlaneIntegrationTest`、`AgentEvaluationAssertionEngineTest`：7 项通过。
- `RbacProductionReadinessIntegrationTest`、`PlatformAuthIntegrationTest`、`PlatformGovernanceIntegrationTest`、`AgentRunTraceIntegrationTest`：相关回归通过。
- 前端 Vitest：12 个文件、67 项通过；`npm run build` 成功，保留已有大 chunk 警告。
- `mvn -q -DskipTests compile`、Compose config 和 `git diff --check` 成功。
- 本地桌面浏览器验证 `/admin/evaluation`、Builder“评测/发布渠道”隔离和 `/platform/evaluation`；无页面错误、无横向溢出、控制台 error/warning 为 0。
- `./scripts/release-acr.sh --dry-run` 成功，生成统一候选版本 `2.6.3`，未构建、推送或创建 tag。
- 完整后端基线仍存在仓库既有的无关 fixture/auth/model 测试漂移；TASK-200 聚焦及相关安全回归均为绿色，明细见 `.claw/test-report.md`。

## Changed Files

- `docs/specs/FEAT-106-multi-tenant-agent-evaluation-control-plane.md`
- `.claw/tasks/TASK-200.md`
- `.claw/assignments/TASK-200.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/goals.md`
- `.claw/decisions.md`

## Handoff

- 目标分支：`codex/TASK-200-agent-evaluation-control-plane`。
- 保留未跟踪 `diagrams/`，本任务不读取、不修改、不提交。
- 功能已达到生产发布就绪；合并后按 `docs/production-release-runbook.md` 使用统一版本 `2.6.3` 或重新 dry-run 得到的下一版本发布。
