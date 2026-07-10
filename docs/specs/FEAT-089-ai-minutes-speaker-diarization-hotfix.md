---
kind: feature-spec
id: FEAT-089
title: AI 听记实时发言人分离热修
status: review
owner: MANAGER-001
created_at: 2026-07-10T11:24:00+08:00
updated_at: 2026-07-10T11:31:43+08:00
---

# FEAT-089 - AI 听记实时发言人分离热修

## 背景与根因

用户反馈 AI 听记无法区分发言人。代码审计确认这是实时听记链路的回归：

- `AssistantApp` 与 `EmbedMeetingMinutesPage` 都把会议实时 ASR 固定为 `provider: "aliyun"`、`speakerDiarization: false`。
- 当前阿里云 `fun-asr-realtime` 实时模型不支持说话人分离，服务端事件也不返回 `speakerId`。
- 项目已有讯飞实时转写链路，支持 `role_type=2`，并已实现角色字段解析、`speakerId/speakerName` 事件和前端分段展示。
- 普通聊天语音输入只需要转文字，不应被本热修切换 provider。

## 目标与范围

- AI 听记的实时录音在组织已启用并配置讯飞实时转写时，自动选择讯飞并开启说话人分离。
- 未配置讯飞的组织继续使用阿里云实时 ASR，避免恢复此前“本地环境无法开始听记”的缺陷，同时向前端返回明确的说话人分离降级提示。
- 文件导入继续使用现有百炼非实时 Fun-ASR 说话人分离，不改变接口。
- 普通聊天、客户工作台输入框语音输入继续使用阿里云实时 ASR，不开启说话人分离。

## 方案设计

1. 为 `/ws/asr` 增加会议场景的 `provider=auto` 解析：
   - `speakerDiarization=true` 且当前组织讯飞配置可用时，选择 `iflytek`。
   - 否则选择 `aliyun`，并先返回 `status` 降级事件，说明实时发言人分离未启用。
2. AI 听记两个实时入口统一发送 `provider: "auto"`、`speakerDiarization: true`。
3. `useAsrVoiceInput` 接收非 started 的 status message，并通过现有 notice 回调展示降级原因；started 后不覆盖已收到的降级说明。
4. provider 显式指定为 `iflytek` 或 `aliyun` 时保持原有语义，避免影响其他调用方。

## 验收标准

- 已配置讯飞的组织启动 AI 听记时，服务端选择 `iflytek` 并携带 `role_type=2`。
- 讯飞返回角色编号时，前端按不同 `speakerId` 分段显示“发言人 1/2/...”。
- 未配置讯飞时仍能开始阿里云实时转写，界面明确提示“当前实时转写不支持发言人分离”，而不是假装已分离。
- 普通语音输入调用不发生 provider 或行为变化。
- 后端 provider 选择测试、讯飞 parser 测试、前端 ASR hook/会议 transcript 测试、前后端构建通过。
- 仅做桌面端产品质量门，不新增移动端适配或移动端自动化测试。

## 约束与例外

- 不新增数据库或迁移。
- 不做实名声纹识别；角色只映射为可编辑的“发言人 N”。
- 不在未配置讯飞时静默承诺说话人分离。
- 不输出、提交或记录任何凭证、token、secret、cookie 或可复用会话信息。

## 来源

- 用户直接反馈：AI 听记无法区分发言人；未提供外部设计文档。
- 既有设计事实源：`docs/specs/FEAT-029-meeting-minutes-live-transcription.md`。
- 阿里云官方语音模型能力表：实时 Fun-ASR 不支持说话人分离，非实时 Fun-ASR 支持。
- 讯飞官方实时语音转写文档：`role_type=2` 开启实时角色分离。

## 实现进展

- 已实现会议实时 ASR `auto` provider 选择，显式 provider 语义保持不变。
- 已将助手工作台和嵌入式 AI 听记入口切换为 `auto + speakerDiarization=true`。
- 已实现未配置讯飞时的阿里云降级状态事件和前端可见提示。
- 本地定向测试、前端生产构建、真实工作台触发/停止听记和桌面视觉检查通过；待完成生产发布与生产 smoke。
