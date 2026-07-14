---
kind: task-status
task_id: TASK-208
status: in_progress
updated_at: 2026-07-14T16:51:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: project-manager
assignment_path: .claw/assignments/TASK-208.yaml
spec_path: docs/specs/FEAT-114-crm-product-sales-analysis-hardening.md
---

# TASK-208 - CRM 产品销售经营分析稳定性与深度治理

## Scope

- 修复 SalesA 可见订单为 0、订单明细为 1,888 时被误报为无销售的问题。
- 消除 CRM 原始工具结果、通用 fallback JSON 和错误“等待确认”状态的用户侧泄漏。
- 建设流式、阻塞式和 Agent OpenAPI 共用的确定性深度经营分析回答。
- 把 TASK-205 批次迁移到 SalesA，并重连 TASK-203 的 16 个 V2 客户后完成生产验收。

## Current State

- 方案 A 已由用户明确批准，根因、能力路径、数据边界和验收矩阵已写入 FEAT-114。
- TASK-208 的确定性五层经营分析、权限不完整状态、防工具结果泄漏、意图路由和受控 CRM 迁移脚本已合入 `origin/main`；143 项定向后端测试通过。
- 最终生产集成分支同时包含 TASK-209 `2.7.2`、TASK-208 `2.7.3` 和当前生产 TASK-210 `2.7.4` 三条发布线；CRM 路径与 TASK-208 一致，完整前端与 `2.7.4` 一致。
- 组合回归通过 CRM 定向后端 143 项、完整前端 89 项和 TypeScript/Vite 生产构建。
- 生产仍运行健康的 `2.7.4`，尚未切换到 TASK-208，也尚未执行 CRM 数据迁移。

## Next Action

- 完成三线生产集成审查并合入 `origin/main`，发布新的不可变版本 `2.7.5`。
- 应用健康验收后执行受控 CRM 数据迁移，再以 SalesA 连续五个新会话和 SalesB 对照完成结构、Top 5、深度分析与防泄漏验收。

## Constraints

- 不触碰 TASK-207 前端分支和用户的 `diagrams/` 改动。
- 不修改 TASK-203 独占的 `scripts/seed-demo-environment.py`。
- CloudCC 写入仅限 `TASK-205-CRM-ANALYTICS-DEMO-V1` 批次，必须先 dry-run 并生成回滚清单。
- 不修改 CloudCC 元数据；如发现必须修改，先暂停并另建 MetadataService 计划。
- 真实凭据、token、cookie、数据库秘密和客户隐私不得进入 prompt、trace、日志、提交或测试夹具。
