---
kind: task-status
task_id: TASK-333
feature_id: FEAT-203
status: in_progress
priority: high
owner_role: fullstack-agent
claimed_by: codex
updated_at: 2026-08-27T09:21:25Z
updated_by: codex
---

# TASK-333 - 创建 DEMO 单页单对象完整配置示例

## 范围

- seed 已发布 `demo-example / 1.0.0` 目录、版本和可选依赖。
- 将平台基础应用的租户投影和受控 `OPEN` 动作从硬编码改为目录驱动。
- 新增只读单页单对象示例页，展示实际配置和未写入的 Provider 全参数参考。
- 补齐后端、前端、迁移、浏览器和本地全栈验证。

## 完成条件

- `DEMO示例应用` 在本地应用中心已发布并可进入示例页。
- 示例页只有一个 `ApplicationConfiguration` 对象，实际值全部来自目录 API。
- Provider 参考值不进入运行连接，不伪造测试或启用状态。
- 代码提交并合并到 AgentCiCi 本地 `main`；本地 backend/frontend 从同一 `main` 提交构建和回读。
- UAT/生产未修改；后续发布必须另行授权。

## 当前证据

- UAT 只读检查确认总计 5 个应用，其中 `BimoApp1`、`测试应用1` 均为草稿且 0 个版本；`BimoApp1` 详情显示 0 个运行连接。
- 当前通用目录只对 `agentcici` 投影平台基础状态，并在前端隐藏除 `agentcici` 外的 `OPEN`；`launchRouteKey` 尚未驱动通用安全路由。
- 当前新应用版本若声明 Provider 回调，必须先创建、测试并启用连接；零初始化版本可发布，但通用租户投影不支持打开。
- 用户已确认目标名称、单页、单对象和全参数示例形态；截图作为现有页面视觉与信息架构参考。

## 下一步

- 实现目录 seed、通用平台基础投影、安全相对路由、示例页及聚焦测试。
