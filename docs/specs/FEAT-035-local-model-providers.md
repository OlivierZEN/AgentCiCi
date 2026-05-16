---
kind: feature-spec
feature_id: FEAT-035
title: Local model providers
status: completed
owner_role: backend-agent-runtime
task_ids: TASK-103
related_decisions: none
related_issues: none
updated_at: 2026-05-15T13:09:03Z
updated_by: ai
---

# FEAT-035 - Local model providers

## 背景与目标

- 组织管理员需要在 `/admin/models` 直接配置本机模型服务，用于开发联调、离线演示和低成本本地推理。
- 当前已有本地 Ollama 厂商入口，但聊天调用路径仍要求 API Key，导致本地无密钥服务无法作为真实聊天模型使用。
- 本次目标是让本机 Ollama 和 LM Studio 都能在模型厂商页检测、拉取模型、加入已选模型，并能作为 `chat` 场景模型完成真实联调。

## 范围

### In Scope

- 补齐 LM Studio 作为内置模型厂商，默认地址为 `http://127.0.0.1:1234/v1`。
- 允许本地 Ollama 与 LM Studio 使用无 API Key 的 OpenAI-compatible chat 调用。
- Ollama 模型列表继续使用 `/api/tags`，LM Studio 模型列表使用 `/v1/models`。
- 管理端模型厂商列表展示新厂商，并保持现有 `鎏金账房` 管理页密度与交互。
- 本机联调验证 Ollama / LM Studio 的模型列表与至少一个可用本地模型调用。

### Out Of Scope

- 不新增模型下载、模型启动、GPU 资源管理或后台守护进程管理。
- 不把本地模型配置发布到线上默认环境。
- 不调整 Agent 发布、评测和计费口径。

## 用户场景

- 组织管理员打开 `/admin/models`，选择“本地 Ollama”或“本地 LM Studio”，检测本机服务并拉取模型列表。
- 管理员把一个本地模型加入已选模型，将 `chat` 场景映射到该模型，然后在助手工作台或 Open API 中试问。
- 如果本机模型服务未启动、模型损坏或未加载，检测/调用应返回可读错误，不破坏其他云厂商配置。

## 现状与约束

- 后端模型路由统一走 `ModelRouterService` 和 `ModelProviderService.credentialsForProvider`。
- 现有 `AliyunBailianClient` 实际是 OpenAI-compatible chat client，但当前空 API Key 会直接短路。
- `model_provider_config.api_key` 数据库字段允许空字符串，适合本地 provider。
- 管理页已有厂商列表、全部模型弹窗、已选模型和场景映射，不需要新增页面结构。

## 方案设计

- 在 `ModelProviderService` 增加 `lmstudio-local` 内置厂商，fetch 类型复用 OpenAI-compatible，但标记为本地无鉴权。
- 把 OpenAI-compatible 模型列表拉取拆成可选鉴权：云厂商仍要求 API Key，本地 LM Studio 不要求。
- 把 OpenAI-compatible chat 调用改为可选 Bearer header：本地 provider 只依赖 `apiBaseUrl`，云 provider 保持 API Key 校验。
- 前端增加 LM Studio 的排序与图标映射；阿里云百炼与 LM Studio 使用来自官方站点/控制台的对应图标，不复用其他厂商图形。

## 接口与数据影响

- `/models/providers` 会为每个组织自动补一行 `lmstudio-local` 内置 provider。
- `/models/providers/{providerCode}/models/fetch` 对 `lmstudio-local` 返回 LM Studio `/v1/models` 中的模型 ID。
- 既有 `model_provider_config` 表结构不变。

## 任务拆分

- `TASK-103`: 实现并验证本地 Ollama 与 LM Studio 模型厂商联调。

## 验收标准

- `GET /models/providers` 包含“本地 Ollama”和“本地 LM Studio”。
- Ollama `/api/tags` 和 LM Studio `/v1/models` 可通过管理端 API 拉取并显示。
- 本地无 API Key provider 可完成 OpenAI-compatible 非流式或流式 chat 调用。
- `frontend npm run build` 和相关后端测试通过。
- 对本机真实服务执行 smoke，记录 Ollama 模型文件错误或调用成功状态，LM Studio 至少一个模型调用成功。

## 风险与回滚

- 本地模型可能未加载、模型文件损坏或首 token 很慢；联调结果需区分“代码不支持”和“本机模型服务状态”。
- 回滚方式：删除 `lmstudio-local` provider 定义和前端映射，恢复 API Key 必填短路逻辑。

## 实现进展

- 2026-05-15T12:55:55Z：规格创建，进入实现。
- 2026-05-15T13:09:03Z：实现完成。新增 `lmstudio-local`，本地 provider 支持无 API Key 模型列表和 chat 调用；管理端展示本地 LM Studio；Playwright 已验证桌面/移动 `/admin/models`。
- 2026-05-15T13:09:03Z：真实联调结果：Ollama `/api/tags` 经后端返回 2 个模型，但本机 `qwen3.6:27b-q8_0` 直接 chat 报模型文件 tensor size 错误；LM Studio `/v1/models` 经后端返回 4 个模型，`/ai/meeting-minutes/summary` 经 `lmstudio-local/qwen3.5-35b-a3b` 生成摘要成功。
- 2026-05-15T13:09:03Z：常规 `/ai/chat` 已确认会使用组织级 LM Studio 模型名，不再被 Agent 的阿里云 qwen 模型覆盖；当前 LM Studio 加载的模型上下文为 4096，常规助手完整提示超过该限制，需用户在 LM Studio 增大 context length 或换更大上下文模型后继续 smoke。
- 2026-05-15T13:29:42Z：按用户反馈修正管理端模型厂商图标错配：阿里云百炼切到百炼控制台官方 SVG，LM Studio 切到官网 app logo；`/admin/models` 桌面与移动截图验证通过，图片均加载成功。
- 2026-05-15T15:49:48Z：按用户产品判断调整信息架构：场景模型映射不再放在某个模型厂商详情内，改为厂商配置区下方的独立“模型路由”面板；保存映射时显式选择场景码、厂商和已选模型，映射列表展示全部场景而不是只过滤当前厂商。

## 交接说明

- 优先查看 `ModelProviderService`、`AliyunBailianClient`、`ChatOrchestratorService` 和 `AdminModelsPage.tsx`。
- 测试时先用本机 `curl http://127.0.0.1:11434/api/tags` 与 `curl http://127.0.0.1:1234/v1/models` 确认服务可达。
- 当前本地 `demo-org` 的 `chat` 场景映射为 `lmstudio-local/qwen3.5-35b-a3b`，这是本轮联调留下的可见配置。
