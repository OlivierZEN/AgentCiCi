---
task_id: TASK-296
feature_id: FEAT-178
integration_id: INT-015
status: completed
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

## 完成证据

- AgentCiCi 历史纠正、平台维护弹窗与语义解析提交已进入本地 `main@78ebeae`；DevAutopilot 抽屉补充提交独立进入其本地 `main@32e95a9`。
- 平台管理员通过受控接口把 `REQ-6F34ECF3` 纠正为 revision 3；内容摘要为 `04b27d83078eb929a75f734c50d3f82de1736eaffc240bca1c268d203dfeaea4`，平台审计记录原确认人、会话、revision 和摘要。
- Semattice 只读回读确认：分类理由独立、4 条产品经理分析、5 条验收标准和 4 条开发者验证项与确认前草稿一致；Markdown 分隔线未落库。
- 再次执行相同纠正返回 `UNCHANGED`，revision 保持 3；不存在重复写入。
- AgentCiCi 定向测试与 package、DevAutopilot 37/37 Node 测试与语法检查、两次完整 `./stack verify` 均通过。
- 本地运行 AgentCiCi `2.8.61-dev.78ebeae`、DevAutopilot `1.0.4-dev.32e95a9`；相关容器 healthy、restart=0。UAT 和生产未修改。
