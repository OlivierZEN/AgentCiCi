---
kind: feature-spec
feature_id: FEAT-083
title: 客户互动工作台真实智能体助理
status: implemented
owner_role: fullstack-agent
task_ids: TASK-173
related_decisions: FEAT-081, FEAT-082
updated_at: 2026-07-10T00:15:08+08:00
updated_by: MANAGER-001
---

# FEAT-083 - 客户互动工作台真实智能体助理

## 背景与目标

客户互动工作台右侧 AI 客户助理当前不能继续停留在规则字符串回复或浏览器本地语音识别层面。面向演示环境和后续生产使用，它必须与 AgentCiCi 的真实智能体运行链路一致：用户输入进入智能体编排，模型路由调用阿里云百炼大模型，运行过程进入会话、Trace、计量和治理链路；语音输入复用现有智能体语音录入能力，通过 `/ws/asr` 接入服务端实时 ASR。

本次同时按用户截图清理右侧助理中被划掉的界面元素，保持工作台紧凑、可信、生产产品语气。

## 范围

- 移除右侧 AI 客户助理顶部的大语音条。
- 移除右侧 AI 客户助理中的四个快捷按钮区域。
- 保留底部输入框、麦克风和发送按钮。
- 底部麦克风复用 `useAsrVoiceInput`，走 `/ws/asr`，默认 provider 为 `aliyun`。
- `/customer-workbench/assistant` 改为调用 `ChatOrchestratorService.chat(...)`，绑定 `cici-system` 智能体与 `customer-interaction-workbench` 技能。
- 输入给智能体的问题必须包含当前客户上下文、互动摘要、AI 建议和 CRM 连接状态，避免大模型缺少业务事实。
- 返回 payload 必须保留前端现有 `reply/action/actionPayload` 兼容字段，并补充 `agentId/sessionId/model/runId/resolvedSkills` 等可审计信息。

## 非范围

- 不新增移动端布局适配或移动端截图验收。
- 不在本任务里实现微信真实同步接入，微信记录来源仍遵循 FEAT-081/FEAT-082 的互动事实模型。
- 不绕过现有智能体权限、模型路由、运行 trace、计费或安全治理。

## 设计与交互要求

- 右侧助理区域删除划掉的大按钮和快捷操作后，聊天区直接承接标题区，底部 composer 是唯一输入入口。
- 语音录入状态通过底部麦克风按钮和简短 notice 反馈，不新增装饰性卡片。
- 麦克风录入完成后只把转写文本写入输入框，由用户确认发送，避免语音误识别直接触发 CRM 相关操作。
- 保持 `鎏金账房` 产品 register：暖象牙底、墨色文字、香槟金结构线、紧凑密度。

## 后端设计

- `CustomerWorkbenchService` 注入 `ChatOrchestratorService` 和 `AgentDefinitionService`。
- `assistant(...)` 在确保工作台数据后：
  - 确保内置智能体与技能默认值存在。
  - 解析当前客户快照、时间线和建议。
  - 构造中文业务上下文提示，要求智能体基于事实回答并区分事实、推断、风险/机会、下一步行动和待确认项。
  - 调用 `chat(orgId, userId, sessionId, prompt, List.of(), "cici-system", "customer-interaction-workbench", metadataFilters)`。
  - 从返回结果中取 `answer` 作为 `reply`，并返回模型、runId、sessionId、resolvedSkills 等运行信息。
- 首版不做规则式 action 推断；需要切换客户或聚焦建议时由智能体用自然语言指导用户，后续再通过工具调用落地。

## 前端设计

- `CustomerWorkbenchApp` 导入并使用 `useAsrVoiceInput`。
- 移除浏览器 `SpeechRecognition` 逻辑。
- 麦克风按钮：
  - 未录音时点击启动实时 ASR。
  - 正在录音时点击停止。
  - 不支持录音时禁用并给出 notice。
  - 录音过程实时更新 textarea，完成后聚焦输入框。
- `CustomerWorkbenchApi.CustomerAssistantResult` 补充可选审计字段，兼容后端增强 payload。
- CSS 删除废弃大语音条和快捷按钮样式，补充录音态按钮与紧凑提示样式。

## 验收标准

- 截图中划掉的大语音条和快捷按钮不再出现。
- 右侧 AI 客户助理提问会真实进入智能体运行链路，后端返回包含 `agentId=cici-system`、`activeSkillCode=customer-interaction-workbench` 或对应 `resolvedSkills`。
- 后端回答来自 `ChatOrchestratorService`，可触发阿里云百炼模型路由、运行 Trace 和计量逻辑。
- 底部麦克风复用 `/ws/asr`，不再使用浏览器 `SpeechRecognition`。
- 前端构建、后端编译和相关单元测试通过。
- 桌面端浏览器验证工作台页面无明显重叠、溢出或被删除控件残留。

## 实施记录

- 已移除右侧助理顶部 `.customer-workbench__voice` 大语音条和 `.customer-workbench__quick` 快捷按钮区域。
- 已将底部麦克风接入 `useAsrVoiceInput`，默认 `provider=aliyun`，走 `/ws/asr` 实时语音链路；浏览器 `SpeechRecognition` 逻辑已删除。
- `/customer-workbench/assistant` 已改为构造客户上下文 prompt 后调用 `ChatOrchestratorService.chat(...)`，指定 `agentId=cici-system`、`activeSkillCode=customer-interaction-workbench`，并返回 `agentId/sessionId/runId/model/resolvedSkills/activeSkillCode`。
- 新增 `CustomerWorkbenchServiceTest` 锁定 assistant 必须调用真实智能体编排，并确认 prompt 包含客户快照、最近互动、用户问题等上下文。
- 本次完成本地编译、构建、聚焦测试和桌面端视觉验证；尚未执行生产发布。
