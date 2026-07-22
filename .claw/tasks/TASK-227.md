---
kind: task-status
task_id: TASK-227
status: done
updated_at: 2026-07-22T15:51:44Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-227.yaml
spec_path: docs/specs/FEAT-131-agent-memory-platform.md
---

# TASK-227 - 通用记忆候选、证据与时效治理

## Scope

- 为 FEAT-131 Phase 2 增加通用记忆候选和证据的关系型权威模型；
- 支持候选的创建、审核通过、拒绝、撤销和过期；审核通过才可转为有效记忆；
- 为每一条候选或有效记忆保留来源类型、来源引用、置信度、敏感级别、有效期和审核理由；
- 用定向测试锁定跨组织、跨应用和未审核候选不可读取的边界。

## Non-goals

- 不接入任一外部应用、渠道、页面、领域工具或真实业务数据；
- 不实现由模型直接写入长期记忆、向量索引、自动语义检索、治理 UI、生产发布或移动端适配；
- 不保存对话原文、凭据、未脱敏工具结果或外部应用领域对象。

## Acceptance

- 候选在人工或确定性审核通过前不会进入 `ExternalMemoryContextService` 的读取结果；
- 审核通过的候选只能在原组织、原应用、原主体与有效 scope 中生成有效记忆；
- 证据、TTL、状态变更与审核理由可审计，过期或撤销记录不会被召回；
- 定向测试、后端编译、全新库迁移验证和 `git diff --check` 通过。

## Progress

- 已新增 V86 的候选与证据表、候选仓储和审核服务；候选始终以 `PENDING` 创建，只有审批操作会生成 `ACTIVE` 记忆。
- 定向测试覆盖禁止提交可读记忆、审批转化与重复审核拒绝；新建并删除的 PostgreSQL 16 临时库已从 V1 迁移到 V86 并断言五张记忆表存在。
