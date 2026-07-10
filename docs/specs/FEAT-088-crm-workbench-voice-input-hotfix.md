---
kind: feature-spec
id: FEAT-088
title: CRM 嵌入客户互动工作台语音输入热修
status: done
owner: MANAGER-001
created_at: 2026-07-10T08:35:00+08:00
updated_at: 2026-07-10T08:55:00+08:00
---

# FEAT-088 - CRM 嵌入客户互动工作台语音输入热修

## 背景

用户反馈 CloudCC CRM 端嵌入的客户互动工作台中，右侧 AI 助手语音输入不好用；点击语音按钮后提示“未识别到有效的语音内容”。

当前语音能力已在 TASK-173 接入 `/ws/asr` 和阿里云百炼实时 ASR，但 CRM 嵌入场景还存在两个风险：

- CloudCC 页面组件通过跨域 iframe 打开 `https://x.agentcici.com/app?aiApp=customer-workbench&embed=crm`，iframe 未显式声明 `allow="microphone"`，浏览器可能阻止子页面获取麦克风。
- `useAsrVoiceInput` 在启动失败后调用 `abort()`，WebSocket `onclose` 仍会触发 `onFinished`，导致真实的权限/启动错误被空转写结果覆盖为“未识别到有效语音内容”。

## 范围

- CloudCC `component-customer-workbench` pagecomponent iframe 增加麦克风权限声明。
- 同步更新可发布 UMD bundle。
- 修复 ASR hook 的启动失败和主动 abort 状态处理，避免启动失败后触发空的完成回调。
- 保持正常“开始录音、停止录音、无语音自动停止、收到最终转写后填入输入框”的行为不变。

## 非目标

- 不更换 ASR provider，不改模型和智能体编排。
- 不改 CloudCC SSO、演示数据或客户工作台布局。
- 不改 TASK-177 数据洞察相关文件。

## 验收标准

- CRM pagecomponent iframe DOM 包含 `allow="microphone"`。
- 启动失败时保留“实时语音启动失败：...”类提示，不再被“未识别到有效语音内容”覆盖。
- 正常停止录音仍会触发完成回调，能把已有 ASR 文本写入输入框。
- `npm --prefix frontend run build` 通过。
- `npm --prefix frontend test -- useAsrVoiceInput.test.ts` 通过。
- `cloudcc package pagecomponent customer-workbench . --dry-run` 通过。
- 发布后 CloudCC pagecomponent/customPage 回读仍指向当前客户互动组件，CRM 端语音权限具备生产可用条件。
