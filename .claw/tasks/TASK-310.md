---
kind: task-status
task_id: TASK-310
feature_id: FEAT-189
status: in_progress
updated_at: 2026-08-17T02:27:24Z
updated_by: codex
owner_role: fullstack-agent
spec_path: docs/specs/FEAT-189-reusable-tenant-owner-identity.md
---

# TASK-310 - 新租户 Owner 全局身份复用

## 范围

- Owner 手机号、邮箱、公共编号统一解析与结构化状态。
- 租户开通 `EXISTING / NEW / AUTO` 模式、幂等记录和冲突失败关闭。
- 平台开通弹窗渐进式身份选择、新用户预检与确认摘要。
- 定向/全量测试、桌面浏览器、本地 main 和 `cici.localhost` 验收。

## 完成条件

- [ ] 已有全局账号可成为新租户 Owner，不新增账号或 Keycloak 用户。
- [ ] 新账号、单标识命中、双标识同账号、双账号冲突、停用账号均有明确处理。
- [ ] 同键同请求幂等返回，同键异请求失败关闭。
- [ ] 前后端测试、构建和差异检查通过。
- [ ] 代码提交合并本地 main，本地开发环境从该提交重建并回读。
- [ ] 受权桌面页面完成主路径验收，或明确记录登录态阻塞。

## 当前状态

- 用户已确认 FEAT-189 设计，正在实现。
