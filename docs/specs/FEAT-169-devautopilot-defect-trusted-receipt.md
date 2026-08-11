---
kind: feature-spec
feature_id: FEAT-169
title: DevAutopilot defect tool and trusted write receipt
status: in_implementation
owner_role: integration-agent
task_ids: TASK-281
related_decisions: none
related_issues: none
updated_at: 2026-08-11T09:15:00Z
updated_by: codex
---

# FEAT-169 - DevAutopilot 缺陷 Tool 与可信写入回执

## 背景与目标

产品经理智能体曾在没有对应 Semattice 对象和成功工具调用的情况下生成看似真实的 Bug 编号与对象 ID。提示词只能表达行为期望，不能作为数据真实性边界。本功能将写入真实性下沉到服务端，并补齐缺陷的正式 Tool。

## 范围

### In Scope

- 所有研发交付写入回复执行服务端成功声明门禁。
- 缺陷创建采用草稿确认，确认后由租户专属 PM SERVICE 调用 Semattice。
- 成功写入后按 `record_id` 回读；回复和 SSE 结构化回执包含对象、记录、业务编号、revision、correlation ID。
- 新增缺陷查询、创建、更新/流转 Tool，并绑定到标准研发产品经理 Agent/Workflow。
- Tool trace 分别记录治理负责人、实际调用人、机器执行主体和 Semattice 审计关联号。

### Out Of Scope

- 在 AgentCiCi 数据库复制缺陷记录。
- 由模型自行生成记录 ID、编号或成功状态。
- 将人类负责人限制为唯一调用人。

## 方案设计

写入请求先由确定性路由识别。未确认时仅返回草稿；确认后调用 Tool。草稿可说明“确认后将成功提交”，但不得包含已生成编号或已完成事实；成功声明门禁只拦截完成态陈述，不能把条件/将来时误判为已写入。工具结果只有同时满足 `status=SUCCESS`、`source=SEMATTICE_LIVE`、存在 `object_api_name`、`record_id`、正 revision、correlation ID 和 `readback_verified=true`，且通过 `runtime.record.get` 回读到同租户同记录时，才生成成功回执。任一条件失败时统一返回未写入或结果待核验，不允许模型改写为成功。

当请求涉及尚未发布的对象类型时，服务端明确说明能力未开通并列出支持对象；不得退回自由生成。流式和非流式路径使用同一门禁。

## 接口与兼容

- 消费 Semattice `dev_defect` runtime record 契约；`company_id` 和 tenant 只来自可信 OACT。
- 新 Tool 结果保持 JSON；旧项目/需求/任务 Tool 继续兼容，但同样受回执门禁。
- Agent 发布补偿必须幂等，不重建租户 Agent 或 SERVICE。
- 既有租户执行标准初始化补偿时，必须以独立 shape revision 幂等键重新应用 Semattice 模板，并只在回读 7 对象/83 字段后更新 activation 的 metadata version/digest；初始化完成后仍保留“同步标准模板”入口，用于幂等补齐后续模板演进。

## 验收标准

- 构造“记录一个 Bug”但 Tool 不可用、Tool 失败、回读失败三类场景，回复均不得声称成功。
- 正常确认创建后，页面展示结构化回执，且可用记录 ID 在 Semattice 回读。
- 普通已授权应用调用者可发起，审计 actor 为实际调用人；负责人仍保留治理责任。
- 暂停应用、暂停 PM SERVICE、越权调用和跨租户记录 ID 均失败关闭。

## 风险与回滚

- 先独立发布硬门禁，可在缺陷对象未就绪时阻断错误承诺。
- 缺陷 Tool 可单独解除绑定回滚；硬门禁不能随功能回滚移除。
- 不记录 Token、Client Secret 或完整 OACT。
