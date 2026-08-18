---
task_id: TASK-319
status: blocked
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
- 远程 `main@e1ecaeec` 已同步，UAT 公开 smoke 通过；`2.8.61-beta.30` 仅完成 dry-run，未创建 tag/镜像或改动环境。
- 当前缺少可读 `CICI_SAAS_SSH_IDENTITY_FILE`，待运维负责人注入后重新冻结候选并完成备份、发布和技术验收；登录态视觉验收仍待补。
