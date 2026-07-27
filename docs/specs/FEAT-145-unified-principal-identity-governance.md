---
kind: feature-spec
feature_id: FEAT-145
title: 统一 Principal 身份、机器账户与官方应用治理
status: in_implementation
owner_role: project-manager
task_ids: TASK-252
related_decisions: FEAT-136 Keycloak 统一身份与官方应用访问令牌, FEAT-144 全局用户公共编号
related_issues: none
updated_at: 2026-07-27T00:00:00Z
updated_by: MANAGER-001
---

# FEAT-145 - 统一 Principal 身份、机器账户与官方应用治理

## 背景与目标

- 用户已确认：AgentCiCi 全局账户与 Keycloak 人类用户必须一对一绑定；创建公司成员时应确保其全局账户与统一身份存在。
- 用户已确认：Semattice、FollowUp 等官方应用不得各自创造人类身份；它们必须以 AgentCiCi 有效公司成员为前置条件。
- 用户已确认：机器账户可独立以 service account 认证，但必须由有效人类账户与公司成员承担所有权、维护和可撤销责任。

## 实现进展

- 本规格正在形成详细实施设计；当前仅设计，不授权任何运行时代码、数据库、Keycloak 或生产变更。
