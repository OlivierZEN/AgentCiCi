---
kind: task-status
task_id: TASK-276
title: 新租户 Owner 统一身份开通修复与 UAT 发布
status: in_progress
priority: critical
owner_role: backend-agent
claimed_by: codex
spec_path: docs/specs/FEAT-165-new-tenant-owner-oidc-provisioning.md
updated_at: 2026-08-10T11:18:25Z
updated_by: codex
---

# TASK-276 - 新租户 Owner 统一身份开通修复与 UAT 发布

## 范围

- 修复平台开通租户 Owner 未进入 Keycloak OIDC 的缺口。
- 覆盖统一认证与本地兼容两种配置。
- 发布新的 AgentCiCi UAT beta，不修改生产。
- 为无人可完成邮件激活的测试/交付租户补充平台管理员受控 Owner 恢复，不设置或暴露密码。

## 完成条件

- [x] 定向自动化测试和后端编译通过。
- [x] 自动化测试证明统一认证 Owner 建立 identity/binding 语义与正确成员状态。
- [x] 本地初始密码不再作为统一认证 Owner 的凭据。
- [x] UAT 不可变版本发布、备份、健康、版本和匿名边界验收通过。
- [x] 隔离 UAT 租户完成受管回读，测试数据保留至 Owner 首次登录验收后再走正式租户生命周期清理。
- [ ] 平台 Owner 恢复只接受已激活统一账号，并具备无有效 Owner、幂等、审计和旧 Owner 保留测试。
- [ ] 发布下一 UAT beta，通过正式接口恢复第二租户 Owner，并完成首次组织选择登录。

## 下一步

实现并发布受控 Owner 恢复；复用已提供且已激活的 Demo 测试账号接管第二租户，再继续 DevAutopilot 开通与双租户隔离验收。
