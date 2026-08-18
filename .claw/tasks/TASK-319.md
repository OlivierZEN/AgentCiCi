---
task_id: TASK-319
status: in_progress
priority: high
owner_role: frontend-agent
claimed_by: codex
---

# TASK-319 - 组织切换弹层完整显示组织名称

## 范围

- 移除组织名称的省略号截断，让弹层按最长组织名称自适应宽度。
- 保留既有最小宽度、主题、当前状态和“管理后台”动作；接近桌面视口边缘时安全换行并继续显示全称。
- 增加聚焦回归测试，并从 AgentCiCi 本地 `main` 构建前端完成登录态桌面视觉验证。

## 下一步

- 完成测试、构建、本地主线提交、本地前端更新与真实弹层截图检查。
