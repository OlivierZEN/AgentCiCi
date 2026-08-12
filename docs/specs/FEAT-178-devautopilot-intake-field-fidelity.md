---
feature_id: FEAT-178
status: implementation
primary_project: agentcici
integration_id: INT-015
---

# FEAT-178 - DevAutopilot 受理草稿字段保真

## 问题

产品经理生成的可见草稿包含专业分析、验收标准和开发者验证项，但智能体遗漏不可见结构化标记时，服务端可见草稿兜底只恢复标题、项目和优先级，并用通用占位句生成 `pm_assessment`、`acceptance_criteria` 和 `assumptions`。Semattice 正确保存了收到的结构化数据，但该数据已在写入前丢失。

## 契约

- 可见草稿与 `DEV_AUTOPILOT_INTAKE_V1` 必须来自同一份结构化事实。
- 可见分析要点完整进入 `pm_assessment`；可见验收标准逐条进入 `acceptance_criteria`；可见影响分析逐条进入 `impact_analysis`；开发者验证表逐条进入 `assumptions`。
- `original_report` 和 `user_supplements` 继续要求逐字匹配真实用户消息。
- 若隐藏标记缺失，可见草稿兜底必须忠实解析已经展示的专业内容；只有草稿本身未提供具体内容时才允许最小安全默认值。
- Semattice 原生字段承载标题、摘要、优先级、验收标准等查询事实，`intake` 保存完整受理来源与审计包，不得互相错位。

## 验收标准

- 截图同构需求草稿恢复后，4 条分析、5 条验收标准、4 条开发者验证项完整保留。
- `create_requirement` 的 `summary` 与 `acceptance` 与可见草稿一致。
- 既有缺陷/变更/隐藏标记路径回归通过。
- 不改变 Semattice 对象 API 或跨仓编译依赖。

## 发布边界

仅更新本地 `main` 与本地开发环境；UAT 和生产需另行授权。
