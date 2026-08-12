---
kind: feature-spec
feature_id: FEAT-175
title: 平台代码解释器集成与内置工具
status: implemented
owner_role: fullstack-agent
task_ids: TASK-291
related_decisions: none
related_issues: none
updated_at: 2026-08-12T11:45:43Z
updated_by: codex
---

# FEAT-175 - 平台代码解释器集成与内置工具

## 背景与目标

- 阿里云百炼的 OpenAI 兼容 Responses API 可通过 `tools: [{"type":"code_interpreter"}]` 调用受管 Python 沙箱，适合精确计算、数据分析和代码验证。
- 平台运营人员需要像 Tavily、讯飞一样统一维护凭证、服务地址、模型与启停状态；智能体作者需要在现有工具目录中发现并授权一个真正可执行的内置工具。
- 本功能只封装厂商受管沙箱，不在 AgentCiCi 宿主机执行任意代码。

## 范围

### In Scope

- 在运营端“平台集成配置”新增“代码解释器”平台托管配置，保存 API Key、API Host、模型、超时和输入上限。
- API Key 使用现有 `SecretCipherService` 加密保存，HTTP 视图只返回固定掩码。
- 提供配置检测入口，使用最小计算请求验证 API、模型和沙箱调用。
- 新增 `sandbox_code_interpreter` 内置工具，接收自然语言任务和可选文本上下文，调用 OpenAI 兼容 Responses API。
- 将工具接入现有工具目录、平台风险/启停治理、Agent/Skill 工具授权和运行时安全网关。
- 返回最终答案、代码解释器调用次数、Token 用量、延迟和有限的代码执行摘要；不返回模型思考过程。

### Out Of Scope

- 不在 AgentCiCi 容器或宿主机执行 Python、Shell 或其他任意代码。
- 不支持上传本地文件、外部网络访问保证、包安装保证或长会话容器复用。
- 不将代码解释器与同一次上游模型请求中的 Function Calling 混用；官方协议声明二者互斥。
- 不修改模型厂商配置、场景模型路由、平台集成之外的现有页面结构。

## 用户场景

- 平台运营人员在“平台集成”打开代码解释器配置，填入百炼 API Key、API Host 和兼容模型，测试后启用。
- 智能体作者在工具目录中选择“受管代码解释器”，用于精确计算、数据分析、文本数据转换和 Python 代码验证。
- 未配置、已停用、模型不兼容、上游超时或返回过大时，工具返回结构化失败，不伪造成功结果。

## 现状与约束

- 平台集成配置使用 `integration_app` 的平台治理作用域，Tavily 与讯飞已验证该模式。
- 内置工具通过 `BuiltinToolCatalog`、`PlatformGovernanceService` 和 `ToolOrchestratorService` 完成目录、治理与执行。
- 官方文档：<https://help.aliyun.com/zh/model-studio/qwen-code-interpreter>。Responses API 通过 `code_interpreter` tool 启用；代码解释器会增加多轮推理 Token 消耗。
- 服务地址因地域和业务空间而异，必须由运营配置；仅允许 HTTPS 的阿里云 `aliyuncs.com` API Host。

## 方案设计

1. `IntegrationAppService` 注册平台托管集成 `code_interpreter`，加密 `apiKey`，提供默认模型、超时和输入限制。
2. `SandboxCodeInterpreterClient` 只调用配置的 `/responses`，请求固定启用思考模式和 `code_interpreter`，但投影时丢弃 reasoning。
3. `SandboxCodeInterpreterService` 负责配置解析、输入长度、URL 白名单、错误归一化、响应限长、工具 Schema 与检测。
4. `ToolOrchestratorService` 仅把它作为 AgentCiCi 的普通内置函数入口；该函数内部完成独立 Responses 调用，避免把代码解释器和上游 Function Calling 同时发送给百炼。
5. 运营 UI 复用现有集成卡片和编辑弹窗，工具目录复用既有列表与独立治理详情页。

## 接口与数据影响

- `GET /api/platform/integrations` 增加 `appCode=code_interpreter`。
- `PUT /api/platform/integrations/code_interpreter` 更新平台托管配置。
- `POST /api/platform/integrations/code-interpreter/test` 检测未保存或已保存配置。
- 内置工具名：`sandbox_code_interpreter`。
- 不新增数据表；已有 `integration_app.config_json` 保存配置，加密信封沿用当前格式。

## 验收标准

- 平台集成列表稳定显示 Tavily、讯飞和代码解释器三项，加载/空态/错误态不回归。
- 密钥不会出现在 API 响应、日志、测试输出或 Git 中；掩码保存不会覆盖现有密文。
- 工具目录存在“受管代码解释器”，默认受平台治理，停用后运行时不可注入或执行。
- 请求体精确包含 Responses API 的 `code_interpreter` 声明，不携带 Function Calling 定义。
- 成功结果包含最终答案和计量；reasoning 不进入工具结果。
- 后端定向测试、前端定向测试、完整前端测试与生产构建通过。
- 提交合并本地 `main` 后，从该提交重建本地 backend/frontend，并回读 `cici.localhost` 路由、容器健康、重启次数和版本指纹。

## 风险与回滚

- 风险：多轮推理增加 Token；通过中风险标识、运行次数/Token 回传、输入上限和超时控制。
- 风险：可配置 URL 形成 SSRF；仅接受无用户信息、无查询参数的 HTTPS `aliyuncs.com` Host。
- 风险：上游模型或协议变化；失败关闭并返回稳定错误码，不回退到本地代码执行。
- 回滚：停用平台集成或工具治理即可立即停止注入；代码回滚不涉及迁移。

## 实现进展

- 平台托管配置、密钥加密/掩码、草稿校验、连接检测、Responses API 客户端、运行时工具、工具目录和运营 UI 已实现。
- 单元/执行器、完整前端与生产构建通过；共享测试库的既有 V81 checksum 漂移继续阻断 Spring 集成用例，未修改历史迁移或 repair。
- 功能提交 `0c58cfb` 已合并本地 `main@8f76e39`，backend/frontend 已从该主线提交更新本地开发环境并完成健康、版本、路由和匿名鉴权边界回读。
- 平台页面当前无受权登录态且未配置真实 API Key；真实厂商连接和 Agent 会话业务验收由平台管理员后续完成，不影响默认关闭的安全交付边界。

## 交接说明

- 先读本规格与 `.claw/tasks/TASK-291.md`。
- 不得用真实 API Key 写测试或提交仓库；真实连接检测由运营人员在受权页面完成。
