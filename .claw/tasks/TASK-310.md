---
kind: task-status
task_id: TASK-310
feature_id: FEAT-189
status: review
updated_at: 2026-08-17T09:11:54Z
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

- [x] 已有全局账号可成为新租户 Owner，不新增账号或 Keycloak 用户。
- [x] 新账号、单标识命中、双标识同账号、双账号冲突、停用账号均有明确处理。
- [x] 同键同请求幂等返回，同键异请求失败关闭。
- [x] 前后端定向测试、前端全量回归、构建和差异检查通过；后端全量的既有共享测试库阻塞已单独记录。
- [x] 代码提交合并本地 main，本地开发环境从该提交重建并回读。
- [ ] 受权桌面页面完成主路径验收，或明确记录登录态阻塞。

## 当前状态

- 实现提交 `4e11acc1` 已包含于 UAT `2.8.61-beta.25 / cc0e8078f5f5`；V118、镜像 revision、备份、健康、重启次数、匿名身份解析 API JSON 401 与公开 smoke 均通过。
- 未伪造平台管理员登录态或创建测试租户；授权态的已有用户复用、新用户预检、冲突提示与最终开通仍待运营人员复核。生产未修改。
