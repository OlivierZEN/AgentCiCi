---
feature_id: FEAT-151
title: 研发交付产品经理 Semattice 实时检索
status: in_progress
task_ids: TASK-258
---

# FEAT-151 - 研发交付产品经理 Semattice 实时检索

## 目标

使 AgentCiCi 中的“研发交付产品经理”在回答项目、需求、研发任务、工时和变更相关问题前，使用当前登录成员的短期官方访问令牌读取同一公司的 Semattice 研发交付模型，并依据返回记录进行汇总回答。

## 设计

- 增加只读内置工具 `semattice_project_delivery_query`，仅查询已发布的五个研发交付对象。
- 工具由 AgentCiCi 服务端签发当前成员的短期 OACT；不接受模型或浏览器传入的租户、账号或令牌。
- 工具调用 Semattice 已发布能力 API，返回经过字段白名单和长度限制处理的结构化摘要。
- 预置“研发交付产品经理”定义绑定此工具，并在系统提示中要求涉及交付事实时先调用工具；访问失败时明确说明 Semattice 检索不可用，禁止退回“无法访问项目管理系统”的虚假能力说明。
- 只读查询不写入 AgentCiCi 或 Semattice 业务对象；所有现有工具权限、平台运行开关与安全网关继续生效。

## 验收标准

1. 该智能体的运行时工具列表包含 `semattice_project_delivery_query`。
2. 询问“现在有哪些项目在执行”时，工具实际通过 OACT 调用在线 Semattice，并返回当前租户的 `DAS-DEMO` 等执行中项目事实。
3. 最终回复基于工具结果总结项目状态，不出现“无法直接访问在线项目管理系统”。
4. 非研发交付智能体不自动获得该工具；跨租户、用户自带令牌与写入动作均不可用。
5. 定向单元测试、后端构建和线上部署后的真实接口/对话链路验证通过。

## 发布

- 目标版本：AgentCiCi `2.8.29`。
- 按 `docs/production-release-runbook.md` 完成 ACR 构建、备份、发布和线上验证。
