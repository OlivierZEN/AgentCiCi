---
kind: task-status
task_id: TASK-228
status: ready
updated_at: 2026-07-23T00:10:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-228.yaml
spec_path: docs/specs/FEAT-131-agent-memory-platform.md
---

# TASK-228 - 通用记忆受控语义检索

## Scope

- 仅为已审核、非敏感、当前有效的通用记忆建立可重建向量片段登记；
- 使用已有向量存储和 embedding 服务执行语义候选召回；命中后必须由关系库按组织、主体、scope、状态和有效期二次过滤；
- 索引失败不阻断关系型上下文读取，且不得把向量库当作权限或业务事实源。

## Non-goals

- 不接入外部应用、页面、渠道、领域工具、自动候选提炼或生产发布；
- 不向量化敏感内容、原始会话或未审核候选；不修改现有知识库的权限模型。

## Acceptance

- 非法主体、过期、撤销、敏感或 scope 不匹配的关系库记录，即使被向量召回也不会返回；
- 向量索引仅保存最小必要的脱敏内容与定位信息；
- 定向检索/隔离测试、编译、全新库迁移和 diff 检查通过。
