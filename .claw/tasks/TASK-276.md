---
kind: task-status
task_id: TASK-276
title: 新租户 Owner 统一身份开通修复与 UAT 发布
status: in_progress
priority: critical
owner_role: backend-agent
claimed_by: codex
spec_path: docs/specs/FEAT-165-new-tenant-owner-oidc-provisioning.md
updated_at: 2026-08-10T08:05:58Z
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
- [ ] UAT 不可变版本发布、备份、健康、版本和匿名边界验收通过。
- [ ] 隔离 UAT 租户完成受管回读，测试数据有可审计清理方案。

## 下一步

提交已验证修复，发布新的 UAT beta，并通过官方 API 与受管存储回读真实 Owner 激活状态。
