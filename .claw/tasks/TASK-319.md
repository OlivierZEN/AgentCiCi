---
task_id: TASK-319
status: review
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

- 实现 `1ad25d39` 已进入本地 `main`；前端全量 53 文件/294 项、production build 和本地运行门禁通过。
- 用户重新登录本地员工工作台后打开组织切换弹层，补充真实桌面截图与视觉验收；不绕过统一登录。
