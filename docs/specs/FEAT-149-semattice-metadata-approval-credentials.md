---
kind: feature-spec
feature_id: FEAT-149
title: Semattice 元数据独立审批凭据
status: completed
owner_role: backend-agent
task_ids: TASK-256
updated_at: 2026-07-30T15:00:00Z
updated_by: ai
---

# FEAT-149 - Semattice 元数据独立审批凭据

## 目标

为官方应用的 Semattice 元数据发布提供持久、可审计、双人分离的审批事实。它用于初始化 DEV Autopilot 的 `dev_project`、`dev_requirement`、`dev_task`、`dev_worklog` 和 `dev_change` 对象，不存储任何项目业务数据。

## 契约

- `OWNER` 或 `ORG_ADMIN` 可创建 `METADATA_VERSION` 或 `CHANGESET` 审批请求，目标必须为 Semattice UUID。
- 只有同一公司且 member ID 不同的有效组织管理员可批准；批准有效期为 15 分钟。
- 仅原发起人在请求 `/auth/semattice/console` 时获得仍有效的 `approvals` OACT claim。
- Semattice 继续只依据 AgentCiCi JWKS 验签；不回调 AgentCiCi，也不接受客户端自报 approval ID。
- 审批表只保存审批控制事实、发起/审批成员和目标，不保存 OACT 或密钥。

## 接口

- `GET /admin/semattice/metadata-approvals`
- `POST /admin/semattice/metadata-approvals`
- `POST /admin/semattice/metadata-approvals/{approvalId}/approve`

## 验收

1. 同一人不能批准自己的请求。
2. 已批准请求只出现在原发起人的短期 OACT 中。
3. Semattice 用 OACT 中的批准 ID 完成 metadata version / changeset 发布。
4. 过期、跨公司或非管理员身份不能获得或使用批准凭据。

## 生产验收证据

- 生产版本：AgentCiCi `2.8.27 / fa9a843dd143`，Flyway `V100` 已执行。
- 提交人与审批人使用同一租户的两位不同有效 `ORG_ADMIN`；自审返回 HTTP 403，审批记录状态为 `APPROVED`。
- 批准后 OACT 中出现相应 `approvals` 声明，Semattice 成功发布初始研发交付元数据版本 `019fb380-7d61-79f7-8d5d-082466d6750d`。
- 发布版本包含 `dev_project`、`dev_requirement`、`dev_task`、`dev_worklog`、`dev_change` 共 5 个对象和 37 个字段；DEV Autopilot 已使用用户委派身份完成真实需求/任务和变更写入。
