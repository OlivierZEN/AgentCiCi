---
kind: feature-spec
feature_id: FEAT-025
title: AgentCiCi market positioning and product roadmap
status: draft
owner_role: product-strategy
task_ids: TASK-070
related_decisions: FEAT-010, FEAT-019, FEAT-021, FEAT-022, FEAT-023, FEAT-024
related_issues: none
updated_at: 2026-05-09T02:08:30Z
updated_by: ai
---

# FEAT-025 - AgentCiCi 市场定位与产品路线

## 背景与目标

AgentCiCi 已从早期 AI 助手演进为独立品牌的企业智能体运行与治理平台。当前能力已经覆盖多组织账号、员工工作台、组织管理后台、平台治理控制面、Agent Builder、Skill、Tool/MCP、RAG、Open API、运行 trace、企业微信微信客服渠道、CloudCC 集成雏形和工作量计费设计。

截至 2026-05-09 的市场调研显示，“通用 Agent Builder / 工作流 / 聊天机器人平台”已经非常拥挤。Salesforce Agentforce、Microsoft Copilot Studio / Agent 365、ServiceNow AI Platform、Zendesk AI Agents、Intercom Fin、Sierra、Decagon、Dify、Coze、n8n、Flowise 和国内模型厂商智能体平台都在争夺构建、部署和客服自动化心智。

本规格的目标是把 AgentCiCi 的产品路线从“泛 AI 助手平台”收敛为更有竞争力的方向：

```text
面向 CRM、售后和企业业务系统的智能体运行与治理平台
```

AgentCiCi 不应正面硬拼通用 Builder，而应围绕业务系统集成、售后/CRM 场景、企业级运行观测、评测回归、发布治理和工作量计费形成差异化。

## 市场格局

### 调研参考

- Salesforce Agentforce: https://www.salesforce.com/agentforce/
- Microsoft Copilot Studio: https://www.microsoft.com/en-us/microsoft-copilot/microsoft-copilot-studio
- ServiceNow AI Platform: https://www.servicenow.com/platform/artificial-intelligence.html
- Zendesk AI Agents / Resolution Platform: https://www.zendesk.com/ai/
- Intercom Fin: https://www.intercom.com/fin
- Sierra: https://sierra.ai/
- Decagon: https://decagon.ai/
- Ada: https://www.ada.cx/
- Dify: https://dify.ai/
- n8n AI Agents: https://n8n.io/ai-agents/
- Flowise: https://flowiseai.com/
- 阿里云百炼智能体应用: https://help.aliyun.com/zh/model-studio/single-agent-application
- 腾讯云智能体开发平台 ADP: https://cloud.tencent.com/product/adp

### 1. 企业巨头平台

- Salesforce Agentforce：围绕 CRM 和 Agentforce 生态强调企业 Agent 的构建、部署、可见性和控制。
- Microsoft Copilot Studio / Agent 365：围绕 Microsoft 365、企业身份、安全和 IT 管控提供 Agent 构建与管理。
- ServiceNow AI Platform：围绕企业流程、ITSM、服务管理和跨系统工作流提供 AI Agent 能力。

这些平台的优势是生态、客户基础、身份权限和企业采购信任。AgentCiCi 不能与其做同质化平台竞争，应作为更轻、更可嵌入、更懂中国企业集成环境和 CRM/售后场景的 Agent 层。

### 2. 客服 / 售后 AI Agent 产品

- Intercom Fin、Zendesk AI Agents、Sierra、Decagon、Ada 等产品把“自动解决客户问题”作为核心价值。
- 这类产品证明售后/客服是可付费、可量化、ROI 清晰的 Agent 场景。
- 它们通常强在客服体验、知识问答、工单与人工接管，但对本地 CRM、私有业务系统、企微/飞书、中国企业交付环境的适配仍给 AgentCiCi 留出空间。

### 3. 通用 Agent / Workflow Builder

- Dify、Coze、n8n、Flowise、阿里云百炼、腾讯云智能体开发平台等已经覆盖可视化或低代码 Agent 构建、知识库、工具调用和工作流。
- 如果 AgentCiCi 只表达为“也能搭建 Agent”，会落入功能对比和价格对比，不利于建立独立竞争力。
- Agent Builder 仍然重要，但应服务业务模板、治理运行和交付闭环，而不是作为唯一卖点。

## 竞争判断

AgentCiCi 有竞争力，但竞争力来自组合拳：

- 已有多组织、角色、账号生命周期和组织隔离基础。
- 已有 Agent Builder、Skill、Tool/MCP、RAG、Open API 和 trace 雏形。
- 已开始落地企业微信微信客服渠道，具备售后 Agent 首个客户侧入口。
- 已有 CloudCC 集成经验，未来接 Salesforce 可以形成双 CRM 连接器能力。
- 已有 FEAT-022 工作量计费模型，适合从“token 成本”升级到“业务工作量 credits”。
- 设计语言和控制台方向偏企业产品，不是玩具式 Bot 平台。

主要风险：

- 定位过宽会被 Dify、Coze、n8n、百炼、Agentforce 等拉入通用平台比较。
- 只做 Builder 容易被低代码和模型厂商快速覆盖。
- 只做客服问答又会进入 Zendesk、Intercom、Sierra、Decagon 等成熟客服 AI 产品的战场。

因此，AgentCiCi 应选择“业务系统 Agent 运行层 + 售后/CRM 垂直场景 + 企业治理控制台”作为差异化。

## 产品定位

### 外部一句话

AgentCiCi 是面向 CRM、售后和企业业务系统的智能体运行与治理平台。

### 中文定位语

企业智能体运行与治理平台。

### 英文定位语

Enterprise Agent Runtime & Governance Platform.

### 不建议使用的主定位

- 通用 AI 助手平台。
- AI 聊天机器人。
- 低代码 Agent Builder。
- Dify/Coze/n8n 替代品。
- 完整客服系统或 CRM。

## 产品路线

### Phase 1: 售后 Agent 闭环

目标：做出第一个可演示、可交付、可计量的商业闭环。

重点能力：

- 售后服务 Agent 模板。
- 企业微信「微信客服」客户侧入口。
- 售后知识库约定。
- 只读业务查询：客户、订单、物流、工单、产品、设备、保修。
- 人工接管摘要：客户问题、已确认事实、已查系统、建议下一步、风险等级。
- 售后运行观测：RAG、工具、失败原因、耗时、外部用户、trace。
- 基础验收题集：常见售后问题、订单查询、保修查询、退款/退货边界。

不做：

- 完整 helpdesk、坐席排班、IVR、电话客服、质检报表。
- 自动退款、赔付、关单、改地址、发券等高风险写动作。

### Phase 2: Salesforce / CloudCC 双 CRM 连接器

目标：把 AgentCiCi 做成 CRM 上方的 Agent 层，而不是另一个 CRM。

重点能力：

- CloudCC 连接器稳定化：认证、对象发现、字段发现、只读查询、错误归因。
- Salesforce 连接器：OAuth/凭证管理、对象/字段元数据、SOQL 查询、常见 CRM 对象模板。
- 连接器能力抽象：CRM customer/order/case/contract/product 等标准语义层。
- run-as 用户、权限边界和密钥审计。
- 连接器健康检查、诊断和配置向导。

### Phase 3: 运行观测与评测回归

目标：把“能运行”升级为“能管理质量、成本和风险”。

重点能力：

- Agent Command Center：按组织、Agent、渠道、版本、API Key、外部用户查看运行状态。
- Trace 质量评分：回答是否完成、工具是否命中、是否需要人工跟进。
- 失败归因：模型、工具、权限、知识库、策略、超时、外部系统错误。
- 发布前评测集：标准问题、期望工具调用、期望答案要点、拒答策略、敏感信息检查。
- 回归报告：准确率、工具调用正确率、人工接管率、成本、延迟。

### Phase 4: 发布治理与工作量计费

目标：让企业能放心发布 Agent，并能按业务工作量付费。

重点能力：

- Agent/Skill/Policy 发布审批。
- 版本回滚和灰度发布。
- 高风险动作人工确认。
- 统一 `usage_meter_event`。
- Work Credits 账本、套餐额度、超额策略、预算预警。
- 按组织、Agent、渠道、API Key、用户和连接器做成本归因。

### Phase 5: 垂直模板与生态

目标：把单个售后样板扩展为可复制的业务模板。

首批模板：

- 售后服务 Agent。
- 销售跟进 Agent。
- 客户成功 Agent。
- 知识库运营 Agent。
- 订单 / 合同查询 Agent。

模板必须包含：

- 默认 Agent Spec。
- 需要的 Skill / Tool / Connector。
- 知识库结构建议。
- 评测题集。
- 发布检查清单。
- 运行观测指标。

## 近期优先级

1. 优先完成 FEAT-023 售后 Agent 闭环，补企业微信配置管理、售后模板、只读查询 Skill、人工接管摘要和售后 trace 筛选。
2. 规划 Salesforce 连接器，同时稳定 CloudCC 连接器，把两者抽象到 CRM 连接器能力层。
3. 在 FEAT-019 基础上强化 Agent 运行观测，新增失败归因和质量判断字段。
4. 新增 Agent 发布前评测 / 回归系统，支持样例集、期望工具调用和发布门禁。
5. 把 FEAT-022 工作量计费从设计推进到 `usage_meter_event` 与 Work Credits ledger。

## 接口与数据影响

本规格本身不直接新增 API 或数据库表，但后续会触发以下设计工作：

- 售后 Agent 模板与种子数据。
- CRM connector 配置表、凭证表、健康检查与元数据缓存。
- Agent evaluation dataset / run / result 数据模型。
- Trace quality / failure reason / handoff reason 字段扩展。
- `usage_meter_event` 和 Work Credits ledger。
- 模板市场或模板库的数据模型。

## 验收标准

- `PRODUCT.md` 明确 AgentCiCi 的市场定位、差异化和近期路线。
- `.claw/goals.md` 明确不把 AgentCiCi 做成泛通用 Builder，而是优先售后/CRM/业务系统 Agent 运行与治理。
- `.claw/task-board.md` 有对应任务卡，后续路线可以引用本规格。
- 后续 FEAT-023、CRM 连接器、观测评测、计费和模板市场工作应引用本规格作为产品优先级依据。

## 风险与回滚

- 风险：路线仍然过宽，导致售后、CRM、Builder、治理、计费同时推进而无法形成商业闭环。
- 缓解：每个阶段必须有可演示、可验收、可交付结果；Phase 1 售后 Agent 闭环优先级最高。
- 风险：Salesforce/CloudCC 集成复杂度高，拖慢售后场景验证。
- 缓解：先做只读查询和元数据发现，不在首期承诺写动作。
- 风险：评测和治理建设过重。
- 缓解：先围绕售后 Agent 的真实题集和 trace 增量建设，不先做抽象大平台。

## 实现进展

- 2026-05-09: 根据用户要求，将市场调研结论沉淀为产品路线规格。
- 已同步 `PRODUCT.md` 和 `.claw/goals.md`。
- 尚未启动具体实现。

## 交接说明

- 继续做售后 Agent、CRM 连接器、观测评测、发布治理、计费或模板市场时，先读本规格。
- 任何“通用 Builder 优先”的新需求，都应先对照本规格判断是否会削弱售后/CRM/企业治理主线。
