---
kind: task-status
task_id: TASK-278
status: done
updated_at: 2026-08-11T02:34:13Z
updated_by: codex
assignee: codex
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-278.yaml
spec_path: docs/specs/FEAT-158-semattice-business-object-list-preview.md
---

# TASK-278 - AI表格 UAT Semattice 元数据授权回归修复

## Current State

- 用户在 UAT 已登录租户的 AI表格中看到“无法读取业务对象 / 业务数据服务暂时不可用”。
- UAT `cici-backend:2.8.60-beta.1` 实际环境只签发 `identity.principal.sync,runtime.record.read,runtime.record.create,runtime.record.update`，缺少 `metadata.read`。
- AI表格目录固定调用 Semattice `metadata.version.get-current`；提供方契约明确要求 `metadata.read`，因此失败发生在目录授权阶段，不代表租户没有对象或记录。
- 根因位于 AgentCiCi UAT 版本化 Compose 覆盖层；Semattice 契约和 AgentCiCi AI表格请求路径不需要修改。
- 已发布并完成真实租户回读，Blocked: none

## Scope

- 恢复 UAT HUMAN OACT 默认范围中的 `metadata.read` 与 `runtime.record.read`。
- HUMAN 默认范围不再混入仅供服务器投影使用的 `identity.principal.sync`；SERVICE allowlist 保持独立。
- 测试发布入口必须在构建前拒绝缺少 AI表格最低只读 scope 的 UAT 覆盖配置。
- UAT 发布 Runbook 必须在重建容器前回读最终 Compose 渲染的 HUMAN/SERVICE scopes，避免受管环境覆盖默认值后再次回归。
- 使用下一生产目标的 `-beta.N` 发布 UAT，只重建 backend/frontend，并以受权租户会话回读对象目录和记录。

## Next Action

- 已完成。后续 UAT 发布由测试发布门禁和最终 Compose scope 回读持续保护；生产保持 `2.8.60`，待本候选按正常验收推进。

## Verification

- UAT 运行态只读回读：`cici-backend:2.8.60-beta.1` 的 HUMAN scopes 精确缺少 `metadata.read`；仓库 Compose 原默认值与运行值一致，服务器未以受管 env 覆盖该项。
- Semattice 提供方只读契约回读：`metadata.version.get-current` 使用 `metadata.read`。
- `bash -n scripts/release-acr.sh scripts/test-release-versioning.sh` 通过。
- `bash scripts/test-release-versioning.sh` 通过，包含缺少 `metadata.read` 时测试发布失败关闭的负向用例。
- Compose 渲染确认 HUMAN 为 `metadata.read,runtime.record.read,runtime.record.create,runtime.record.update`，SERVICE 保持独立 allowlist。
- `AiTableDataServiceTest` 与 `OfficialAccessTokenServiceTest` 通过；`2.8.61-beta.1` dry-run、`git diff --check` 通过。
- UAT `2.8.61-beta.1 / d4b273af39c2` 已发布；backend/frontend ACR index digest 为 `sha256:be29c222ba8b6212a6d916d89c94e2301145f3196abeb866afe0d96048e59c57` / `sha256:d28768f068aba1644de93fec3ecf4ecdfcb356a0456f06da2057bc3768acdb4d`。
- 备份 `backups/20260811T021914Z-before-2.8.61-beta.1` 九项均非空且为 `0600`；只重建 backend/frontend，四个状态服务 ID 哈希前后保持 `b5dca5759af2a9cfb0ed4285fdb3b01c9af02db33eb2bfbabfa347fe728de2bc`。
- 运行态 health=`UP`、版本/镜像/Git SHA 一致、Nginx 有效、首页 200、匿名 AI表格 401、启动 ERROR 计数 0。
- 受权租户 `TASK-276 UAT Owner OIDC 20260810` 的 AI表格回读 6 个已发布 DevAutopilot 对象；当前对象记录为 0 时显示真实空状态，不再出现服务不可用，浏览器 console error/warning 为 0。
