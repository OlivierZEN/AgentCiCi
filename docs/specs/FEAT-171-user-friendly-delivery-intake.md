---
kind: feature-spec
feature_id: FEAT-171
title: 面向普通用户的研发事项识别、整理与交接
status: implemented
owner_role: integration-agent
task_ids: TASK-283
related_decisions: none
related_issues: none
updated_at: 2026-08-11T07:19:00Z
updated_by: codex
---

# FEAT-171 - 面向普通用户的研发事项识别、整理与交接

## 背景与目标

现有产品经理对话把 Semattice 专业字段直接变成普通用户的问卷。用户按正常使用方式描述问题后，仍被要求判断 P0/P1、critical/high、技术环境和完整复现步骤，职责边界错误并阻断研发流转。

本功能建立“用户自然描述 → 产品经理主动分类与专业整理 → 必要时聚焦澄清 → 用户确认 → Semattice 可信写入 → 研发交接”的统一受理链路。

## 主动分类

产品经理必须结合完整对话主动判断事项类型，不要求普通用户先选择专业对象：

- `requirement`：用户希望新增当前不存在的能力或业务结果。
- `defect`：已有能力的实际行为偏离正常预期。
- `change`：对已确认需求、范围、规则或交付方案提出调整。

“希望新增”不能误判为缺陷；“原本应该可以但现在不行”不能误判为需求；已经存在明确需求且要求调整范围时优先判为变更。分类、父级归属或用户真正期望确实无法判断时，只提出一个业务语言的聚焦问题。

## 角色边界

- 普通用户只需描述目标、操作和异常，不负责专业分级、根因定位、测试设计或部署判断。
- 产品经理逐字保留首次原始描述，结合上下文生成标题、分类依据、优先级、验收标准或影响分析；缺陷额外生成初始严重度、环境、复现线索、预期/实际结果和待验证假设。
- 产品经理不得把严重度、优先级、环境、复现步骤、预期结果等清单重新抛给用户。只有影响分类或核心语义的歧义才允许追问。
- 用户补充后，产品经理合并原始描述与补充内容，重新展示完整草案，并以简短确认完成授权。
- 开发者统一为全栈工程师智能体，不再拆分开发、测试、运维角色。开发者按受治理任务访问源码、开发环境和测试环境，负责复现/可行性验证、技术设计、实现、自动化测试、交付证据和回滚说明。
- 人类负责人继续承担治理、优先级决策和问责，不等于唯一可调用人或唯一执行者。

## 会话草案协议

产品经理面向用户输出自然语言草案，同时携带渲染不可见的 `DEV_AUTOPILOT_INTAKE_V1` JSON 标记。标记至少包含：

- 通用：`classification`、`project`、`title`、`original_report`、`pm_assessment`、`priority`、`user_supplements`、`assumptions`、`clarification_question`、`ready_for_confirmation`、`cancelled`。
- 需求：`acceptance_criteria`。
- 缺陷：`severity`、`environment`、`reproduction_steps`、`expected_result`、`actual_result`。
- 变更：`requirement`、`impact_analysis`。

服务端只接受最近一份语法有效、未取消、无待澄清问题且 `ready_for_confirmation=true` 的标记。`original_report` 和每条 `user_supplements` 必须逐字对应会话中的真实用户消息，防止模型改写原话。用户发送“确认提交需求/缺陷/变更”或“确认提交”后，服务端恢复草案并执行受控 Tool；没有有效草案时短确认不写入。

## Semattice 映射

- `dev_requirement`、`dev_change`、`dev_defect` 增加向后兼容 JSON 字段 `intake`，统一保存 intake 版本、原始描述、分类依据、产品经理分析、用户补充、假设、确认人、确认时间、来源会话和 correlation ID。
- 需求业务字段保存产品经理整理后的标题、摘要、优先级和验收标准。
- 变更业务字段保存目标需求、摘要和影响分析。
- 缺陷 `description` 继续保存用户原始描述；专业字段保存产品经理初评，并明确 `developer_verification_pending=true`。
- 可用开发者存在时，缺陷按当前租户 active 开发者池稳定分派并写入 `assignee_principal_id`；没有可用开发者时仍登记并进入待分配队列。
- intake 写入使用由完整受理包确定性派生的 correlation/idempotency key；网络失败后重试同一确认不得重复创建记录。
- 只有 `record_id`、revision、correlation ID 和写后回读同时有效才允许声明已登记。

## 交互要求

- 草案依次表达“我理解的事项类型”“原始描述”“产品经理整理”“待确认/待开发者验证”。
- 不显示专业字段问卷，不要求用户复制完整机器协议。
- 信息充分时只提示简短确认；有关键歧义时只问一个问题。
- 成功回执包含真实对象类型、业务编号（如有）、记录 ID、revision、correlation ID 和交接结果。

## 验收

- 自然语言中的需求、缺陷和变更分别生成正确草案；模糊边界只问一个业务问题。
- 截图中的报障不再追问严重度、优先级、环境、复现步骤或预期结果。
- 原始描述逐字写入 intake；产品经理分析、用户补充、假设、确认和来源会话均可从 Semattice 回读。
- 简短确认在无草案、已取消、仍有歧义或原话校验失败时不写入。
- active 开发者可接收缺陷；停用/暂停主体不进入候选池。无 active 开发者时报告不丢失。
- 流式与非流式链路使用同一恢复、写入和可信回执规则；既有项目、需求、任务完整确认协议保持兼容。

## 回滚

可停止短确认恢复并恢复旧完整确认格式；新增 `intake` 为可选 JSON，旧记录和消费者保持兼容。可信回执门禁不得回滚。
