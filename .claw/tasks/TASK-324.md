---
kind: task-status
task_id: TASK-324
feature_id: FEAT-192
status: in_progress
priority: high
owner_role: backend-agent
claimed_by: codex
updated_at: 2026-08-19T13:12:22Z
updated_by: codex
---

# TASK-324 - 产品经理首轮澄清保留具体需求上下文

## 范围

- 修复明确 UI 需求因结构化字段不完整而退化成泛化“业务结果”追问的问题。
- 为结构化判定补充需求专业整理和上下文澄清约束，以及关键字段的 JSON Schema 描述。
- 不改变确认协议、Semattice 写入权限、Tool allowlist、SERVICE 身份和可信回执门禁。
- 增加截图场景的服务端回退与提示契约回归，完成定向测试、package、本地 main 和 `cici.localhost` 验证。

## 完成条件

- 已给出产品/项目、页面位置、界面元素和修改方向时，不再让用户重复说明抽象业务结果。
- 仍需澄清时，先复述已理解的改动，只询问图标来源/样式、文字保留方式等影响验收的产品选择，并提供遵循现有设计系统的默认方案。
- 结构化判定字段仍不完整时，固定回退保留用户原始描述，不执行数据查询或写入。
- 定向回归、后端 package、状态校验和本地全栈运行指纹通过。

## 当前证据

- 截图首轮答复精确命中 `DevAutopilotDialogueDecisionService.intakeDraft` 的缺少 `title/pm_assessment` 固定分支；第二轮带模型标识的普通对话才利用上下文提出具体问题。
- 现有 `decisionPrompt` 只要求“完整专业草案字段”，`decisionTool` 的文本字段没有语义描述，不完整草案回退会丢弃 `original_report` 并输出固定泛化句子。
- 已加固结构化判定提示和关键字段 Schema，聚焦问题优先于低置信度通用分流；字段仍不完整时固定回显原需求，并询问样式/来源、文字保留和可见效果，可接受“按现有产品设计规范处理”的默认选择。
- 8 个相关测试类共 82 项、后端 package 与 `git diff --check` 通过；状态校验只报告既有历史债务，未报告 TASK-324 或 FEAT-192 新错误。
- 待从本地 `main` 构建并更新 `cici.localhost` backend 后补充运行指纹。
