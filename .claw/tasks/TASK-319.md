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
- 远程 `main@39424a98` 已同步；UAT `2.8.61-beta.30 / 39424a982068` 的不可变镜像、annotated tag、完整备份、仅应用容器切换、健康/版本/鉴权/稳定日志技术门禁均通过，生产未修改。
- 平台用户登录 UAT 后打开组织切换弹层，复核长组织名称完整显示、hover/focus 和桌面视口边界；登录态视觉验收仍待补。
