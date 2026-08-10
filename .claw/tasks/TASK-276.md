---
kind: task-status
task_id: TASK-276
title: 新租户 Owner 统一身份开通修复与 UAT 发布
status: review
priority: critical
owner_role: backend-agent
claimed_by: codex
spec_path: docs/specs/FEAT-165-new-tenant-owner-oidc-provisioning.md
updated_at: 2026-08-10T08:27:30Z
updated_by: codex
---

# TASK-276 - 新租户 Owner 统一身份开通修复与 UAT 发布

## 范围

- 修复平台开通租户 Owner 未进入 Keycloak OIDC 的缺口。
- 覆盖统一认证与本地兼容两种配置。
- 发布新的 AgentCiCi UAT beta，不修改生产。

## 完成条件

- [x] 定向自动化测试和后端编译通过。
- [x] 自动化测试证明统一认证 Owner 建立 identity/binding 语义与正确成员状态。
- [x] 本地初始密码不再作为统一认证 Owner 的凭据。
- [x] UAT 不可变版本发布、备份、健康、版本和匿名边界验收通过。
- [x] 隔离 UAT 租户完成受管回读，测试数据保留至 Owner 首次登录验收后再走正式租户生命周期清理。

## 下一步

Owner 查收 UAT 激活邮件并完成邮箱验证/设置密码；随后回读首次 OIDC 登录和成员 `ACTIVE`，再决定保留或按正式生命周期清理测试租户。
