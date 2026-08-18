---
kind: task-status
task_id: TASK-321
feature_id: FEAT-196
status: in_progress
priority: high
owner_role: backend-agent
claimed_by: codex
updated_at: 2026-08-18T14:35:00Z
updated_by: codex
---

# TASK-321 - DevAutopilot 产品经理初始化模板分层

## 范围

- 重构标准系统提示词与 8 步自然语言流程 Spec。
- 初始化补偿同时校准系统提示词、Spec、Skill binding 和发布版本。
- Spec IR 与 workflow code 使用有效工具集合并识别研发交付流程意图。
- 增加定向回归，提交本地 `main` 并更新 `cici.localhost` backend。

## 完成条件

- 系统提示词、流程 Spec、Skill 与确定性代码的职责边界有代码和测试证据。
- 新建与既有 Agent 的显式发布路径使用同一平台签名模板。
- 定向测试、package、diff check 和本地运行门禁通过。
- 不执行真实租户初始化，不修改 UAT、生产、DevAutopilot 或 Semattice。

## 当前证据

- 旧初始化把一句角色约束写入 `systemPrompt`，把十行运行政策写入 `specText`；Skill 另有完整工具规程。
- 旧 Spec 编译只使用 Agent 直接工具，不能表达已绑定 Skill 的有效工具能力。
- 用户已确认按正确分层重新构建。
- 6 个聚焦测试类共 18 项通过，后端 package 与 diff check 通过。
- 扩展 `OrchestratorIntegrationTest` 受本机共享 PostgreSQL 不可达阻断，未进入目标断言；未修改共享测试数据库。
- 待提交本地 `main` 并更新 `cici.localhost` backend。

## 接口与数据影响

- 无新增 API、数据库表或迁移。
- 新租户初始化直接使用新模板。
- 既有租户只在平台管理员显式执行既有 `initializations` 时补偿；普通读取和部署不隐式改写租户 Agent。

## 回滚

- 回滚本任务代码提交并从本地 `main` 重建 backend。
- 不删除 Agent、Skill 版本或业务数据；已发布工作流使用现有版本机制显式恢复。
