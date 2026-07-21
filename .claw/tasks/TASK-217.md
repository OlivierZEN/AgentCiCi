---
kind: task-status
task_id: TASK-217
status: done
updated_at: 2026-07-21T11:03:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-217.yaml
spec_path: docs/specs/FEAT-122-runtime-execution-trace-correction.md
---

# TASK-217 - 智能体定时任务真实创建与链路事实纠偏

## Scope

- 实现当前用户/当前智能体范围内的真实个人定时任务创建与调度执行。
- 修正工作流解析和实际执行在 Trace 中混淆的问题，并校正 always-on 技能计数。
- 完成后端、前端和桌面端回归，按发布 Runbook 进行线上验证。

## Current State

- 生产记录 `df5e12f4` 已确认未创建 trigger，未调用工具；当前只返回定时获客参数 JSON。
- 已实现当前用户/当前 Agent 的 `workflow_schedule_create` 内置工具：它追加个人 workflow routine、发布版本并物化真实 trigger；周期无效或缺失时拒绝写入。
- 已让个人 workflow 在已授权时实际执行 Tavily 搜索；Trace 的工作流阶段改为“工作流定义检查”，并把 always-on Skill 计入已应用技能。
- 已在阻塞与流式会话入口加入定时任务周期追问保护：未提供周期不调用模型或写工具；提供明确周期时才向模型暴露创建工具。

## Completion

- 已发布生产 `2.7.12 / b20261d8b89b`。前后端、四个状态服务均健康；四项发布备份非空，Nginx 有效，`x.agentcici.com` 与显式生产 IP 的 onechat HTTPS smoke 均为 200，发布窗口后端/前端错误和 Nginx 5xx 均为 0。
- 当前会话没有该组织用户的授权登录态，因此没有为了验收而替用户创建实际定时任务。用户以“每天 09:00 搜索美国 K12 教育机构”发起后，将由真实创建工具返回 trigger 和下次执行时间；无周期输入会只要求补充周期。

## Verification

- `mvn -q -DskipTests compile` -> passed.
- `mvn -q -Dtest=ToolOrchestratorServiceTest,ChatOrchestratorServiceModelIdentityTest,AgentRunTraceServiceTest,AgentWorkflowRuntimeSkillGovernanceTest test` -> passed.
- `mvn -q test` -> blocked by existing shared test database Flyway V81 checksum mismatch; no repair was applied.
- `release`: `./scripts/release-acr.sh --dry-run` 与 `2.7.12` 构建、ACR inspect、Git tag 均通过；backend index/amd64 为 `sha256:b2d1e4a053a6edadd6cdcefd481615a89258cd1821e02f3745f74031dd175b23` / `sha256:9b819a1b9949dd98d3db700bd36bacdeeef655be200f42288edb662ae089496b`，frontend 为 `sha256:a3a6ff9734bb3f7da648a2003159289d26b704f6927fd48b06f665b7e205b616` / `sha256:52a0228d143371ac9e6da0570e047d387ac227656af12bdcfbe8cbf644b5ea8b`。
