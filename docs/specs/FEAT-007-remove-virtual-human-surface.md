---
kind: feature-spec
feature_id: FEAT-007
title: Remove virtual human surface
status: completed
owner_role: fullstack-assistant-experience
task_ids: TASK-017
related_decisions: none
related_issues: none
updated_at: 2026-04-29T07:00:00Z
updated_by: ai
---

# FEAT-007 - Remove virtual human surface

## 背景与目标

- 用户已明确要求取消助手端“虚拟人”功能，不再保留入口、页面和相关流协议。
- 本次交付目标是把“虚拟人”从当前系统的可见产品面与实现面中移除，避免形成残留菜单、死代码和误导文档。

## 范围

### In Scope

- 移除助手端左侧菜单中的“虚拟人”入口。
- 移除 `AssistantApp` 内的 `scene` 页面状态、交互和渲染逻辑。
- 回收仅为 `scene` 页面引入的前端 SSE 未知事件透传能力。
- 回收后端 `/ai/chat/stream` 中仅服务于虚拟人页面的 `avatar_state` 与 `task_*` 事件发送逻辑。
- 移除与虚拟人展示直接相关的静态封面页。
- 更新项目状态文件与特性文档，明确该能力已下线。

### Out Of Scope

- 不调整现有工作台、监控、客户会话、CRM 等其他助手端入口。
- 不改动通用语音输入能力 `useAsrVoiceInput`，其仍服务于工作台等现有功能。
- 不处理与“avatar”一词无关的普通用户头像、Agent 首字母头像等 UI 元素。

## 用户场景

- 助手端用户进入主界面时，不再看到“虚拟人”菜单，也无法进入对应沉浸式页面。
- 开发与维护人员阅读仓库文档时，能明确知道 FEAT-006 只保留历史记录，当前产品面已不再提供虚拟人能力。

## 现状与约束

- 现有虚拟人能力主要集中在 `frontend/src/assistant/AssistantApp.tsx`、`frontend/src/assistant/cici-ui.css` 和 `backend/.../ChatOrchestratorService.java`。
- `scene` 页面专用流事件没有被其他前端页面消费，因此可以成组移除。
- 仓库当前是 brownfield 项目，`.claw/` 文件已有进行中的用户改动，本次更新必须增量修改，不能覆盖既有状态记录。

## 方案设计

- 直接移除前端 `scene` 页签和整段渲染逻辑，而不是只做隐藏开关。
- 直接回收 `streamAiChat` 的未知事件透传参数，避免保留不再使用的协议分支。
- 后端保留既有 `delta/tool_call/tool_result/phase/done/error` 事件，删除 `avatar_state/task_created/task_status/task_delta/task_done` 发送逻辑。
- 文档层新增专门的下线 spec，并在状态文件中把虚拟人标记为已移除而非“暂停”。

## 接口与数据影响

- `/ai/chat/stream` 不再额外发送以下事件：
  - `avatar_state`
  - `task_created`
  - `task_status`
  - `task_delta`
  - `task_done`
- 前端 `streamAiChat(...)` 不再暴露 `onUnknownEvent` 回调。
- 无数据库迁移，无新增配置。

## 任务拆分

- `TASK-017`
  - 责任角色：`fullstack-assistant-experience`
  - 内容：前端入口/页面移除、后端流协议回收、静态资产清理、文档与状态同步

## 验收标准

- 助手端左侧菜单不再展示“虚拟人”入口。
- `AssistantApp` 中不再存在 `scene` 模式和相关状态逻辑。
- `/ai/chat/stream` 仍能支持现有工作台主链路，但不再发送虚拟人专属事件。
- 相关状态文件和 spec 已说明该能力下线。
- 至少完成一次前端构建和一次后端编译验证。

## 风险与回滚

- 风险：若仍有隐藏调用依赖 `onUnknownEvent` 或 `task_*` 事件，编译或运行时会暴露出来。
- 风险：若有仓库外部文档或手工流程仍引用静态虚拟人封面页，需要另行同步。
- 回滚方式：恢复本次删除的前端 scene 分支、流协议扩展和静态页面文件。

## 实现进展

- 当前状态：已完成
- 已完成项：
  - 已移除助手端虚拟人菜单和 `scene` 页面代码。
  - 已移除前端未知 SSE 事件透传分支。
  - 已移除后端虚拟人专属 SSE 事件发送逻辑。
  - 已删除虚拟人静态封面页并更新状态文档。
- 未完成项：
  - 无

## 交接说明

- 若后续需要重启该方向，不要直接恢复旧 `scene` 页面；应先重新确认产品目标，再基于当前工作台架构重新设计。
- 历史背景参考 `docs/specs/FEAT-006-virtual-human-multitask-workbench.md`，当前下线事实以本 spec 与 `.claw/current-status.md` 为准。
