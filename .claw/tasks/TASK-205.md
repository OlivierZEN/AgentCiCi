---
kind: task-status
task_id: TASK-205
status: in_progress
updated_at: 2026-07-14T10:30:00Z
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

- 用户已批准“通用助手 + CRM 经营分析 Skill + 高阶确定性工具”的架构。
- CloudCC `standard-catalog` 已确认目标租户存在产品、订单、订单产品、合同、业务机会产品及关键字段，无需元数据变更。
- FEAT-111 已定义指标语义、对象关系、工具合同、演示数据规模、权限和验收标准。
- 实施计划已固化到 `docs/superpowers/plans/2026-07-14-crm-business-analysis.md`，当前按 TDD 执行。

## Next Action

- 先扩展文件型标准 Skill 的运行时策略契约，再实现产品销售排行服务与高阶工具。

## Constraints

- CRM 经营分析数据使用独立 `scripts/seed-crm-analytics-demo.py`，不得修改 TASK-203 独占的 `scripts/seed-demo-environment.py`。
- 任何 CloudCC 写入必须先 dry-run；只操作 `TASK-205-CRM-ANALYTICS-DEMO-V1` 样例数据。
- 真实凭据不得进入 prompt、trace、日志、提交或测试夹具。
- 不修改 CloudCC 元数据；若扫描后发现不可避免的缺口，先暂停并补 MetadataService 计划。
- 不回退 TASK-203、TASK-204 或用户的 `diagrams/` 改动。
