---
kind: task-status
task_id: TASK-208
status: done
updated_at: 2026-07-14T17:28:00Z
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
- TASK-208 的确定性五层经营分析、权限不完整状态、防工具结果泄漏、意图路由和受控 CRM 迁移脚本已合入 `origin/main`；最终生产集成同时保留 TASK-209 与 TASK-210。
- 生产已发布 `2.7.5 / be80eea665c0`；backend/frontend healthy，PostgreSQL、Redis、RabbitMQ、Qdrant 保持原容器和 `2.6.12` 镜像，Nginx 与公网 smoke 通过。
- CloudCC 受控批次已完成 316 条既有记录更新、316 处 owner 修正和 88 处客户重连；二次 dry-run 为待更新 0、创建 0、重复 0，结构回读为 12/16/24/72/16/48/144。
- SalesA 连续 5 个新 SSE 会话、5 组持久化消息、阻塞式、OpenAPI blocking/streaming、生产桌面页面和 SalesB 管理员对照均返回同一 Top 5；页面与协议中无内部工具名、原始 JSON 或错误“等待确认”。
- 组合回归通过 CRM 定向后端 143 项、完整前端 89 项和 TypeScript/Vite 生产构建；最终干净窗口 backend error、Nginx 5xx 与 CRM 分析错误均为 0。

## Next Action

- 无；生产发布、CRM 迁移、权限回读、多通道对话和桌面端验收均已完成。

## Constraints

- 不触碰 TASK-207 前端分支和用户的 `diagrams/` 改动。
- 不修改 TASK-203 独占的 `scripts/seed-demo-environment.py`。
- CloudCC 写入仅限 `TASK-205-CRM-ANALYTICS-DEMO-V1` 批次，必须先 dry-run 并生成回滚清单。
- 不修改 CloudCC 元数据；如发现必须修改，先暂停并另建 MetadataService 计划。
- 真实凭据、token、cookie、数据库秘密和客户隐私不得进入 prompt、trace、日志、提交或测试夹具。
