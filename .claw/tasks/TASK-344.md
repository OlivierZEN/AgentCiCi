---
kind: task-status
task_id: TASK-344
feature_id: FEAT-204
status: in_progress
priority: critical
owner_role: release-agent
claimed_by: codex
spec_path: docs/specs/FEAT-204-web-widget-publish-channel.md
updated_at: 2026-08-31T03:07:48Z
updated_by: codex
---

# TASK-344 - Web 浮窗 UAT 发布与站点首页嵌入

## 范围

- 将 AgentCiCi 本地 `main` 的 `2.8.68` 单发布线冻结为下一不可变 UAT 候选，并只替换 UAT backend/frontend。
- 在 UAT 租户 `orgickjr6icm6l2zitpn` 通过官方产品链路创建、编译、发布售前跟进智能体并启用 Web 浮窗渠道。
- 以受管运行配置把该 Web 浮窗设为 UAT 站点首页默认入口；不把租户 ID、运行成员、Token 或环境域名固化进业务源码/前端制品。
- 完成备份、回滚、版本/制品、迁移、健康、匿名边界、公开配置、浏览器视觉和真实会话证据。

## 完成条件

- [ ] 本地/远程 `main` 一致，候选 tag、两项不可变镜像和冻结 commit 一致且未更新 `latest`。
- [ ] 发布前完整备份非空、权限受控、校验通过，回滚目标和命令明确。
- [ ] UAT 只替换 backend/frontend，四个状态服务 ID 不变，六容器 healthy/restart=0。
- [ ] 目标租户售前跟进智能体通过官方 API/UI 创建、编译、发布；使用 ACTIVE 且具备 RUN 权限的成员，不执行数据库直写。
- [ ] Web 渠道精确允许 UAT Origin，公开 Token 仅含 chat 权限，首页默认入口通过受管运行配置启用。
- [ ] UAT 首页、公开配置、Token 正负例、浮窗头像/主题/按钮和一轮非空模型回复通过。
- [ ] 技术验收与已登录 HUMAN 业务接受分别记录；生产保持不变。

## 当前证据

- UAT 发布前只读巡检通过：首页 200、匿名 `/auth/me` 401、Keycloak discovery、Semattice health/version 和 DevAutopilot integrated health 均为 200/正常。
- 当前 UAT 为 `2.8.67-beta.1 / 2970bea75208`，backend/frontend healthy/restart=0，四状态服务哈希已记录，最新完整备份为 `20260828T104754Z-before-2.8.67-beta.1`。
- 目标租户 `orgickjr6icm6l2zitpn / CloudCC Agentic Test` 为 ACTIVE，但当前 `agent_definition` 为 0；必须走官方产品链路创建，不能直接写数据库。
- 当前本地 `main@fc9ac373362c` 比远程 `main@6424ae2bc65c` 领先 13 个提交；发布前需完成全量门禁、推送远程并冻结候选。

