---
task_id: TASK-296
feature_id: FEAT-178
integration_id: INT-015
status: in_progress
priority: critical
owner_role: backend-agent
claimed_by: codex
---

# TASK-296 - DevAutopilot 历史受理记录字段纠正

## 范围

- 从同租户持久化会话恢复已确认草稿，禁止调用方自带纠正字段。
- 使用产品经理 SERVICE 和 Semattice `runtime.record.update/get` 完成乐观锁纠正与逐字段回读。
- 提供租户 `ORG_ADMIN` 与平台 `PLATFORM_ADMIN` 两个受控触发入口，二者均沿用原确认人的 SERVICE 委托链。
- 在平台租户应用页以独立弹窗触发维护，避免读取浏览器 Token 或使用同页混合编辑方式。
- 为 `REQ-6F34ECF3` 执行正式纠正，并在 DevAutopilot 页面复核。

## 验收

- 4 条分析、5 条验收、4 条开发者验证项完整进入 Semattice。
- 错误会话、错误记录、跨租户或字段回读不一致均失败关闭。
- 重复执行不增加 revision；回执包含内容摘要与审计信息。
- 合并本地 `main`，从主线构建并更新 `https://cici.localhost/` 开发环境。
