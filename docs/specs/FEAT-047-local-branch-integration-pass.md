---
kind: feature-spec
feature_id: FEAT-047
title: Local branch integration pass
status: implemented
owner_role: project-manager
task_ids: TASK-127
related_decisions: none
related_issues: none
updated_at: 2026-05-21T10:49:05Z
updated_by: MANAGER-001
---

# FEAT-047 - Local branch integration pass

## Background

当前工作分支 `codex/TASK-124-feat-046-platform-tenant-provisioning` 已经承载了多个并行任务的在地改动，但仓库里仍有一组本地分支尚未合并进来。继续在未整合的状态下推进，会让后续验证、冲突判断和任务边界越来越模糊。

## Goal

- 在不丢失当前 dirty worktree 的前提下，把剩余未合并的本地分支按受控方式整合到当前分支。
- 为每个被整合分支留下可追踪的合并结果、冲突处理结论和验证证据。
- 保持当前分支作为后续主线工作分支，不额外切换到新的长期集成分支。

## In Scope

- 盘点当前分支尚未包含的本地分支。
- 暂存当前未提交工作，执行逐个 merge。
- 处理冲突并记录跳过或保留的原因。
- 在合并完成后恢复 stash，并执行一轮聚焦验证。

## Out Of Scope

- 推送远端、开 PR / MR、发布到测试环境。
- 因合并顺手扩写无关功能。
- 用 reset、rebase 覆写用户现有本地工作。

## Merge Target Set

- `codex/TASK-118-admin-organization-profile`
- `codex/TASK-120-platform-accountless-login`
- `codex/TASK-121-db-rename-agentcici`
- `codex/TASK-122-platform-console-production-polish`
- `codex/TASK-124-platform-tenant-manual-provisioning`
- `codex/recover-task119-122`

## Acceptance Criteria

- 合并前的 dirty worktree 已安全保存并可恢复。
- 上述目标分支全部被处理：成功合并、显式跳过、或因冲突中止并记录原因。
- 不使用破坏性 Git 命令覆盖本地未提交工作。
- 合并后的当前分支至少完成一轮聚焦验证，并把证据记录到 `TASK-127`。

## Verification Plan

- `git branch --no-merged` 复核剩余分支集合。
- `git stash` 保存当前工作树。
- 逐个 `git merge --no-ff <branch>` 或在必要时记录跳过原因。
- 冲突解决后执行至少一轮 `git diff --check` 与针对性构建/测试。
- `git stash pop` 或等效恢复本地工作树并检查恢复结果。

## Risks

- 当前分支本身已是 dirty worktree，若 stash 和恢复步骤不严谨，容易造成局部覆盖或冲突叠加。
- 目标分支跨越 state、平台、认证与 UI 多个区域，冲突密度可能较高。
- 某些历史救援分支可能只适合挑选性吸收，不适合机械式整支合并。

## Implementation Result

- `codex/TASK-118-admin-organization-profile` 已并入当前分支；冲突集中在 `.claw/` 状态文件，按当前分支事实源优先原则解决。
- `codex/TASK-120-platform-accountless-login`、`codex/TASK-121-db-rename-agentcici`、`codex/TASK-122-platform-console-production-polish`、`codex/TASK-124-platform-tenant-manual-provisioning` 在合并执行时均已被当前分支祖先覆盖，因此结果为 `Already up to date`。
- `codex/recover-task119-122` 已并入当前分支，带回了本地恢复分支里的 TASK-119~122 / 124~128 状态与平台账号相关实现快照。
- 预先 stash 的 dirty worktree 已恢复；对同名未跟踪文件，改为从 stash 手动回填到工作树，避免本地未提交任务卡和规格被恢复分支版本覆盖。

## Verification Outcome

- `git branch --no-merged` 结果为 0，说明本轮目标本地分支已全部处理完成。
- `git diff --check` 通过。
- `frontend npm run build` 通过，保留既有 Vite chunk-size warning。
- `backend mvn -q -Dmaven.repo.local=../.m2 -DskipTests compile` 在本轮观察窗口内未返回最终结果，需后续补跑。
