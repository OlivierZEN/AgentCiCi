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

## 历史数据纠正与字段级回执

- 已确认草稿、用户确认指令和 Semattice 成功回执必须继续保存在同一租户会话中；历史纠正只能从这组受信消息恢复，不能接受调用方提交任意业务字段。
- 租户 `ORG_ADMIN` 可针对明确的 `session_id + record_id` 发起纠正。服务端必须确认：会话属于当前租户、会话中存在该记录 ID 的成功回执、草稿在回执之前、目标记录的 `intake.conversation_id` 与会话一致、标题与分类一致。
- 纠正通过产品经理 SERVICE 身份调用 Semattice 官方 `runtime.record.update`，使用 `expected_revision` 乐观锁；禁止 AgentCiCi 直接写 Semattice 数据库。
- 原确认人、确认时间和 correlation ID 保持不变；只以已确认草稿恢复专业摘要、验收/影响/复现字段与 `intake` 语义字段，并追加纠正时间、纠正人和来源审计。
- 写后必须再次调用 `runtime.record.get`，逐字段比对 patch；回执除 `record_id/revision` 外还必须返回内容摘要 `content_digest`，只有逐字段一致才允许标记 `readback_verified=true`。
- 重复纠正必须幂等：字段已经一致时不增加 revision，返回 `UNCHANGED`。

## 本次故障验收样本

- 历史记录 `REQ-6F34ECF3` 必须从其会话草稿恢复 4 条产品经理分析、5 条验收标准和 4 条开发者验证项。
- DevAutopilot 详情抽屉回读恢复后的 Semattice 记录时，专业摘要、验收标准、产品经理分析和待开发者验证内容必须与确认前草稿一致。

## 发布边界

仅更新本地 `main` 与本地开发环境；UAT 和生产需另行授权。
