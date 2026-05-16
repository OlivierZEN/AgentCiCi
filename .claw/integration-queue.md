---
kind: integration-queue
version: 3
updated_at: 2026-05-16T02:50:42Z
updated_by: ai
queue_id: QUEUE-001
status: not_started
integration_owner: unassigned
---

# 集成队列

`integration-queue.md` 用于异步多开发者并行开发的最终合并协调。简单单线项目可以保持空白或只保留模板。

推荐状态值：`not_started` / `collecting` / `merging` / `verifying` / `ready` / `blocked` / `completed`

## Active Integration Queues

### QUEUE-001 - First parallel delivery queue

- status: `not_started`
- feature_id: `FEAT-xxx`
- integration_branch: `integration/FEAT-xxx`
- integration_owner: `unassigned`
- related_tasks: `TASK-xxx`
- related_prs: `none`
- merge_order: `TASK-xxx`
- validation_gates: `state validation, project tests, smoke check`
- blocked_by: `none`

#### Merge Notes

- 先合并共享契约或基础迁移，再合并依赖它们的实现任务。
- 每合并一个任务分支后运行相关验证，最终合并前运行完整验证。

#### Rollback Notes

- 如果集成验证失败，保留在 integration 分支处理，不直接污染主分支。

## Completed Integration Queues

- 暂无已完成集成队列。

## 维护规则

- 只有存在多个开发者分支或多个并行任务需要合并时才维护本文件。
- 合并顺序、集成分支和验证门禁以本文件为准。
- 真实测试结果仍记录到 `.claw/test-report.md`。
