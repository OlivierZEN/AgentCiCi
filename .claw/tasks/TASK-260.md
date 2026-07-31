---
kind: task-status
task_id: TASK-260
status: done
updated_at: 2026-07-31T14:20:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: backend-agent
assignment_path: .claw/assignments/TASK-260.yaml
spec_path: n/a
---

# TASK-260 - 研发项目名称自然语言提取修复

## Current State

- 用户反馈“现在创建一个研发项目名称叫：AgentCiCi企业级智能体平台”被错误解析为项目名“研发”。
- Scope：修复项目草案名称解析并增加定向回归测试；不改变确认门禁和 Semattice 写入范围。
- Blocked: none

## Evidence

- 已发布 `2.8.33 / b680c961b8f6`，六容器健康，backend health `UP`。
- 线上以原句“现在创建一个研发项目名称叫：AgentCiCi企业级智能体平台”验证，草案和确认指令均完整使用 `AgentCiCi企业级智能体平台`，未写入记录。
