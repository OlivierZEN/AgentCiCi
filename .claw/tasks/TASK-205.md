---
kind: task-status
task_id: TASK-205
status: done
updated_at: 2026-07-14T10:46:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: project-manager
assignment_path: .claw/assignments/TASK-205.yaml
spec_path: docs/specs/FEAT-111-crm-business-analysis-skill.md
---

# TASK-205 - CRM 经营分析 Skill 与产品销售演示数据

## Scope

- 实现平台标准 CRM 经营分析 Skill 和确定性 `crm_product_sales_rank` 只读工具。
- 建设目标 CloudCC 演示租户高仿真产品、订单、订单明细、合同和商机产品数据。
- 用真实 CRM 回读和 5 个新会话重复问答验证“销量最好的产品有哪些”。

## Current State

- 平台标准 `CRM 经营分析` Skill、`crm_product_sales_rank` 高阶只读工具和确定性产品销售意图门已交付生产 `2.6.8 / 095094300a25`。
- `cici-system` 已编译并发布版本 3，运行快照包含 `crm-business-analysis`；产品销量/销售额排行命中后强制走唯一高阶工具，不再由通用模型编排原子 CRM 工具。
- CloudCC 批次 `TASK-205-CRM-ANALYTICS-DEMO-V1` 已真实写入并幂等回读：12 产品、16 客户、24 商机、72 商机产品、16 合同、48 订单、144 订单产品。
- 5 个新生产会话全部只调用 `crm_product_sales_rank`，Top 5 稳定为 `DEMO-X1 130`、`DEMO-G5 110`、`DEMO-S2 95`、`DEMO-MP 75`、`DEMO-PA 65`。

## Next Action

- 已完成；后续可按同一高阶工具合同扩展客户、区域、产品线和销售漏斗分析，不回退到模型侧跨对象聚合。

## Constraints

- CRM 经营分析数据使用独立 `scripts/seed-crm-analytics-demo.py`，不得修改 TASK-203 独占的 `scripts/seed-demo-environment.py`。
- 任何 CloudCC 写入必须先 dry-run；只操作 `TASK-205-CRM-ANALYTICS-DEMO-V1` 样例数据。
- 真实凭据不得进入 prompt、trace、日志、提交或测试夹具。
- 不修改 CloudCC 元数据；若扫描后发现不可避免的缺口，先暂停并补 MetadataService 计划。
- 不回退 TASK-203、TASK-204 或用户的 `diagrams/` 改动。
