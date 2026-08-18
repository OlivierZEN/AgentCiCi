---
kind: feature-spec
feature_id: FEAT-196
title: DevAutopilot 产品经理初始化模板分层
status: in_implementation
owner_role: backend-agent
task_ids: TASK-321
related_decisions: none
related_issues: none
updated_at: 2026-08-18T14:35:00Z
updated_by: codex
---

# FEAT-196 DevAutopilot 产品经理初始化模板分层

## 背景与目标

DevAutopilot 标准租户应用初始化会由 AgentCiCi 创建并发布产品经理 Agent。旧模板把一句角色约束写入 `systemPrompt`，却把大量稳定治理政策写入自然语言 `specText`，而具体工具规程又存在于 `semattice-project-delivery-management` Skill，三层职责混叠。

本功能重新建立四层边界：系统提示词负责稳定身份与治理原则；自然语言 Spec 负责业务流程和分支；Skill 负责 Semattice 工具规程；租户、SERVICE 身份、确认与写后回读继续由后端确定性门禁保障。

## 范围

### In Scope

- 定义平台签名的产品经理系统提示词，包含领域、事实来源、租户边界、人类确认和输出分层。
- 将自然语言 Spec 固定为 8 个可编译流程步骤，覆盖查询、受理、规划、变更、评审、验收、回读和失败关闭。
- 初始化和显式补偿同时校准系统提示词、Spec、Skill binding、web channel 与发布版本。
- Spec IR 与生成的 workflow code 使用 Agent 加已绑定 Skill 的有效工具集合。
- 补充模板分层、幂等补偿、意图识别与 Skill 工具边界回归。

### Out Of Scope

- 不修改 DevAutopilot 或 Semattice 源码、数据库与跨项目契约。
- 不改变工具鉴权、确认协议、SERVICE Principal、状态机或写入语义。
- 不替用户执行真实租户初始化、创建业务记录、评审或验收。
- 不发布 UAT 或生产。

## 分层设计

### 系统提示词

- 声明当前租户 DevAutopilot 研发产品经理身份和职责。
- 将“项目”限定为 Semattice 管理的研发交付项目，而非 CRM 项目。
- 只允许依据当前租户已绑定工具的实时结果陈述业务事实。
- 要求遵循已发布 Skill，但明确 Skill 不扩大工具、知识库、身份或租户权限。
- 高影响动作必须取得人类明确确认；没有可信回执不得报告完成。
- 输出区分已验证事实、产品判断、待验证假设和下一步。

### 自然语言流程 Spec

固定 8 步：输入识别、事实查询、事项受理、任务规划、记录变更、设计评审/交付验收、写后回读、失败关闭。每步表达可观察的输入、分支或结果，不复制工具参数实现。

### Skill 与确定性门禁

`semattice-project-delivery-management` 保持 Tool 选择、字段约束、确认协议和输出契约的唯一业务规程；后端继续保障工具白名单、租户隔离、SERVICE 身份、状态机和可信回执。

## 验收标准

- 新建命令分别写入标准系统提示词和 8 步 Spec。
- 显式补偿可同步旧系统提示词或旧 Spec，并保持相同模板重放幂等。
- `specIr.steps` 精确为 8，意图覆盖 query、intake、planning、create、update、delete、transfer、review、acceptance 与 handoff。
- `specIr.toolRefs` 和 workflow code 包含 Skill 有效工具，但不把 Skill 工具写回 Agent 静态绑定。
- 定向测试、后端 package、diff check 和本地 `cici.localhost` 运行门禁通过。

## 风险与回滚

- 新系统提示词会改变后续显式发布版本的模型上下文；以常量、编译指纹和聚焦测试控制漂移。
- 新意图标签只用于编译摘要与 Spec IR，不授予工具或直接执行写入。
- 代码可回滚；既有工作流版本不得删除，可由现有版本机制显式恢复。
