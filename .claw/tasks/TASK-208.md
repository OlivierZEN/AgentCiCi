---
kind: task-status
task_id: TASK-208
status: ready
updated_at: 2026-07-14T13:16:37Z
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
- TASK-205 五次成功验收使用 SalesB；实际页面用户 SalesA 看不到该批次订单主表。
- 当前只完成分配准备，尚未修改产品代码或生产 CRM 数据。

## Next Action

- 验证 assignment 并把分配提交单独推送到 `origin/main`。
- 从更新后的主线创建独立实现 worktree，先运行基线测试，再按 FEAT-114 的 Task 2 进入 TDD。

## Constraints

- 不触碰 TASK-207 前端分支和用户的 `diagrams/` 改动。
- 不修改 TASK-203 独占的 `scripts/seed-demo-environment.py`。
- CloudCC 写入仅限 `TASK-205-CRM-ANALYTICS-DEMO-V1` 批次，必须先 dry-run 并生成回滚清单。
- 不修改 CloudCC 元数据；如发现必须修改，先暂停并另建 MetadataService 计划。
- 真实凭据、token、cookie、数据库秘密和客户隐私不得进入 prompt、trace、日志、提交或测试夹具。
