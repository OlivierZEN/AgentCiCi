---
kind: feature-spec
feature_id: FEAT-017
title: Workbench user quick commands
status: implemented
owner_role: frontend-backend-assistant-workbench
task_ids: TASK-043
related_decisions: none
related_issues: none
updated_at: 2026-05-05T09:08:06Z
updated_by: ai
---

# FEAT-017 - Workbench user quick commands

## 背景与目标

- 会话工作台 composer 已有“快捷指令”按钮，但当前行为复用了技能选择器，无法展示用户自己为当前智能体沉淀的常用提问或任务模板。
- 目标是在会话工作台对话框内，按当前登录用户和当前智能体列出个人快捷指令；用户没有快捷指令时，可直接添加一条自定义快捷指令。
- 功能应服务高频对话输入，保持 `鎏金账房` 产品页的紧凑菜单尺度，不把轻量输入变成大弹框。

## 范围

### In Scope

- 新增当前用户、当前智能体维度的快捷指令持久化表。
- 新增登录用户接口：列出快捷指令、添加自定义快捷指令。
- 会话工作台 composer 中，“快捷指令”按钮打开快捷指令菜单，而不是技能选择器。
- 点击快捷指令将指令内容填入输入框并聚焦，用户可继续编辑或直接发送。
- 当列表为空时，在同一轻量菜单内展示新增自定义快捷指令表单。

### Out Of Scope

- 本轮不做快捷指令编辑、删除、排序拖拽或共享给组织成员。
- 本轮不把快捷指令直接绑定技能、知识库或工作流发布。
- 本轮不改变现有技能按钮和 `activeSkillCode` 发送链路。

## 用户场景

- 员工在会话工作台切到某个智能体，点击“快捷指令”，看到自己给这个智能体保存的常用任务。
- 员工第一次使用某智能体时列表为空，可输入名称和指令内容保存，随后出现在该智能体的快捷指令菜单中。
- 员工点击一条快捷指令后，输入框被填入对应指令，仍可手动修改，避免误触即发送。

## 现状与约束

- 当前工作台已按当前智能体加载技能绑定，技能按钮独立展示技能菜单。
- `/me/agents/{agentId}/workflow` 已承载用户与智能体维度的个人工作流能力，本轮快捷指令归入该用户态工作台域。
- 产品 UI 必须遵守 `DESIGN.md` 的轻量浮层菜单规则：12px 主文字、10-11px 元信息、26-30px 行高、暖象牙底、浅金边。

## 方案设计

- 数据模型新增 `user_quick_command`，以 `org_id + user_id + agent_id` 作为查询维度。
- 接口放在 `/me/agents/{agentId}/workflow/quick-commands`，复用 `UserWorkflowController` 的用户上下文和 Agent 校验。
- 前端新增快捷指令本地状态：按 `agentId` 缓存列表、加载态、打开态和新增表单。
- 菜单只展示现有快捷指令、空态和“添加快捷指令”动作；点击添加后打开独立阻塞式 modal，保存成功后关闭窗口并刷新当前智能体快捷指令列表。

## 接口与数据影响

- `GET /me/agents/{agentId}/workflow/quick-commands`
  - 返回当前用户当前智能体的启用快捷指令列表。
- `POST /me/agents/{agentId}/workflow/quick-commands`
  - 请求：`{ "title": string, "promptText": string }`
  - 返回：新建快捷指令。
- 数据库新增 Flyway 迁移 `V38__user_quick_command.sql`。
- 回滚方式：前端隐藏入口即可降级；数据库表保留不会影响原有聊天、技能或个人 workflow。

## 任务拆分

- `TASK-043`: 实现会话工作台用户快捷指令菜单、接口和持久化。

## 验收标准

- 点击会话工作台 composer 的“快捷指令”会列出当前用户给当前智能体设置的快捷指令。
- 切换智能体后，快捷指令列表按新智能体重新加载，不混用其他智能体数据。
- 当前智能体没有快捷指令时，可直接添加自定义快捷指令。
- 点击快捷指令只填入输入框，不自动发送。
- `frontend npm run build` 成功；后端相关编译或测试成功。

## 风险与回滚

- 风险：快捷指令菜单与技能菜单同时打开造成浮层重叠。
  - 缓解：打开任一菜单时关闭另一个菜单。
- 风险：用户输入过长导致菜单变形。
  - 缓解：后端限制标题和内容长度，前端菜单文本截断。

## 实现进展

- 状态：implemented。
- 已完成项：需求和方案已落文；后端新增 `user_quick_command` 持久化、列表/新增接口；前端会话工作台 composer 新增独立快捷指令菜单；添加快捷指令改为独立 modal。
- 未完成项：真实登录态人工点击验收；快捷指令编辑、删除、排序属于后续范围。

## 交接说明

- 先看 `frontend/src/assistant/AssistantApp.tsx` 的 composer 区域和 `backend/src/main/java/com/codehouse/ciciassistant/userworkflow` 包。
- 本功能与技能选择器并列，不应复用 `activeSkillCode` 语义。
