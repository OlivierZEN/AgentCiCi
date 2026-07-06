---
kind: feature-spec
feature_id: FEAT-080
title: 安全规则平台与输入输出安全网关
status: planned
owner_role: fullstack-agent
task_ids: TASK-170
related_decisions: DEC-008, DEC-022, FEAT-067, FEAT-079
updated_at: 2026-07-06T16:20:00+08:00
updated_by: MANAGER-001
---

# FEAT-080 - 安全规则平台与输入输出安全网关

## 背景与目标

客户要求补齐安全规则平台能力。AgentCiCi 现有基础包括：

- RBAC 与 `@RequireOrgAdmin` / 平台角色边界。
- 组织级审计、平台审计、Agent 权限审计。
- 工具注册与工具白名单。
- 部分安全 prompt、工具边界 prompt、伪工具标签防护与评测门禁。

当前缺口集中在内容层和运行时安全层：

- 缺少敏感信息识别与脱敏。
- 缺少敏感词库维护。
- 缺少内容审核分类。
- 缺少 prompt injection 检测。
- 缺少统一输入输出安全网关。

本特性目标是把这些能力补成可配置、可审计、可测试、可灰度扩展的生产级安全规则平台，而不是只补几段 prompt。

## 产品范围

新增独立管理端能力 `/admin/security-rules`，覆盖：

- 安全概览：风险事件、命中规则、拦截/脱敏/复核统计。
- 敏感词库：组织级词库维护、分类、严重级别、动作、启停。
- 敏感信息类型：内置 PII/API Key/Token/银行卡/邮箱/手机号等检测。
- 内容审核策略：分类、阈值、动作矩阵。
- Prompt Injection 规则：检测规则、测试样本、命中事件。
- 安全事件：按用户、Agent、渠道、规则、动作、时间查询。

首版管理端必须服从既有后台视觉语言：`鎏金账房` 的暖象牙底、墨色文字、紧凑密度、香槟金结构线和克制企业工作台语气。

## 后端能力

### 1. 统一安全决策模型

统一输出：

- `ALLOW`：放行。
- `MASK`：脱敏后放行。
- `WARN`：放行但提示或记录。
- `BLOCK`：阻断。
- `REVIEW`：进入人工复核。
- `ESCALATE`：高风险事件通知管理员或平台。

安全检测结果必须包含：

- `category`
- `severity`
- `confidence`
- `action`
- `matchedRules`
- `redactedText`
- `eventId`
- `policyVersion`

### 2. 敏感信息识别与脱敏

首版内置检测类型：

- 手机号。
- 邮箱。
- 身份证号。
- 银行卡号。
- JWT。
- API Key / Access Token。
- PEM 私钥片段。
- IP 地址。

脱敏策略：

- 审计与日志默认强脱敏。
- 模型输入默认占位符脱敏。
- 前端展示按管理员角色展示脱敏文本。
- 工具调用参数若命中高风险敏感信息，默认 `REVIEW` 或 `BLOCK`，除非工具被显式标为需要该字段。

### 3. 敏感词库维护

组织管理员可维护组织级词条：

- `category`：政治、违法、色情、暴力、辱骂、赌博、毒品、商业违规、内部禁词、合规表达等。
- `matchType`：`EXACT`、`CONTAINS`、`REGEX`。
- `severity`：`LOW`、`MEDIUM`、`HIGH`、`CRITICAL`。
- `action`：`ALLOW`、`WARN`、`MASK`、`BLOCK`、`REVIEW`。
- `enabled`。

匹配要求：

- 词条必须组织隔离。
- 正则需编译校验，错误正则不能保存。
- 命中事件必须记录词条 id、词库版本和片段摘要。

### 4. 内容审核分类

首版内容审核分类不依赖外部云服务，采用本地规则 + 敏感词 + PII + prompt injection 分类器组合：

- `ILLEGAL`
- `SEXUAL`
- `VIOLENCE`
- `HATE`
- `GAMBLING`
- `FRAUD`
- `SELF_HARM`
- `PRIVACY`
- `SECRET`
- `PROMPT_INJECTION`
- `BUSINESS_COMPLIANCE`

预留 provider contract，后续可接入云端 moderation 模型，但首版必须在离线本地环境可运行。

### 5. Prompt Injection 检测

检测对象：

- 用户输入。
- RAG 检索内容。
- 上传文件解析文本。
- 工具返回内容。
- 模型输出中的伪工具标签或指令泄露。

首版规则覆盖：

- “忽略之前/以上指令”。
- “泄露 system prompt / hidden prompt / developer message”。
- “你现在是/扮演另一个系统”。
- “调用未授权工具/伪造 tool result”。
- “把文档内容中的指令当系统指令执行”。

处理方式：

- 用户输入疑似注入：`WARN` 或 `BLOCK`，根据严重级别配置。
- RAG/tool 内容疑似注入：从上下文中过滤或降权。
- 模型输出疑似泄露系统指令：阻断或替换为安全说明。

### 6. 输入输出安全网关

新增 `SafetyGatewayService`：

- `checkInput(...)`：用户输入进入编排前。
- `checkModelContext(...)`：RAG/tool 上下文进入模型前。
- `checkToolCall(...)`：工具调用参数执行前。
- `checkOutput(...)`：模型输出返回用户前。
- `redactForAudit(...)`：审计写入前。

接入点：

- `ChatOrchestratorService`：输入前、模型输出后、RAG/tool 上下文拼接前。
- `ToolOrchestratorService`：工具调用前、工具结果回填前。
- `KnowledgeBaseService`：文档入库和切片入向量前。
- `AuditService` / `PlatformAuditService`：payload 写入前。

## 数据模型

新增 Flyway 迁移 `V71__security_rules_platform.sql`。

表：

- `security_rule`
  - 规则类型：`SENSITIVE_TERM`、`PII_PATTERN`、`MODERATION_CATEGORY`、`PROMPT_INJECTION`。
  - 规则配置、分类、严重级别、动作、启停、版本。
- `security_detection_event`
  - 每次命中记录：org、actor、surface、channel、agent、category、severity、action、matched_rule_ids、原文 hash、脱敏摘要。
- `security_review_item`
  - `REVIEW` 动作产生人工复核项。
- `security_policy_snapshot`
  - 记录当前策略版本与统计，便于灰度和追溯。

所有表必须包含 `org_id`，平台级规则使用 `org_id='__platform__'` 或空组织约定时必须在服务层明确隔离。

## API 设计

管理端：

- `GET /security-rules/overview`
- `GET /security-rules/rules`
- `POST /security-rules/rules`
- `PUT /security-rules/rules/{id}`
- `POST /security-rules/test`
- `GET /security-rules/events`
- `POST /security-rules/events/{id}/review`

运行时内部服务不直接暴露给普通用户。所有管理 API 必须 `@RequireOrgAdmin`。

## 前端设计

新增 `/admin/security-rules` 一级导航。

页面布局：

- 左侧策略分区：概览、敏感词、Prompt Injection、内容审核、安全事件。
- 右侧工作区：
  - 顶部紧凑指标带。
  - 规则表格。
  - 内联规则编辑区。
  - 测试文本区，展示命中规则、动作和脱敏结果。
  - 事件表格，支持复核动作。

不新增移动端适配，不做营销式 hero。

## 生产就绪验收

- 输入命中手机号、邮箱、身份证、JWT、API Key、银行卡时能识别并脱敏。
- 审计写入不保存明文敏感信息。
- 组织管理员可创建敏感词/正则规则；错误正则被拒绝。
- `POST /security-rules/test` 能返回命中规则、动作和脱敏文本。
- `ChatOrchestratorService` 用户输入和输出走安全网关；阻断内容不进入模型。
- 工具调用参数走安全网关，高风险参数阻断或进入复核。
- RAG/tool 上下文中的 prompt injection 片段不会作为指令进入模型。
- 管理端 `/admin/security-rules` 可完成规则维护、测试和事件查看。
- 后端测试覆盖 detector、gateway、audit redaction、admin API、chat/tool integration。
- 前端 `npm run build` 通过。
- Playwright 桌面端验证 `/admin/security-rules` 无控制台错误、无横向溢出。

## 非目标

- 不接入外部商业内容审核云服务；只保留 provider contract。
- 不做图片/音视频多模态审核。
- 不做复杂审批流，仅提供复核项状态流转。
- 不替换现有 RBAC 和工具白名单，而是在其前后增加内容安全网关。

## 风险与回滚

- 风险：误杀正常业务内容。缓解：首版默认 `WARN/MASK` 优先，`BLOCK` 只用于高置信高风险规则。
- 风险：脱敏影响工具执行。缓解：工具调用前只阻断高风险，低风险字段按工具声明决定。
- 风险：性能开销。缓解：规则编译缓存、长度限制、短路决策、事件摘要入库。
- 回滚：可通过禁用组织规则和关闭 gateway enforce mode 降级为 audit-only。
