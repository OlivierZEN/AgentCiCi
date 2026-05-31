---
kind: task-status
task_id: TASK-146
assignee: MANAGER-001
owner_role: fullstack-agent
status: review
branch: codex/TASK-146-ops-observability-audit
pr_url: n/a
spec_path: docs/specs/FEAT-019-agent-observability-monitoring.md
assignment_path: .claw/assignments/TASK-146.yaml
updated_at: 2026-05-31T13:47:59Z
updated_by: MANAGER-001
---

# TASK-146 观测与运维生产就绪收口

## Scope

- 分析 `/admin/ops`、智能体运行日志和审计日志现状，列出上线缺口并在 FEAT-019 中固化。
- 补齐组织级智能体运行快照 API，让管理端不再只靠前端用智能体列表和运行日志推断状态。
- 强化审计日志为可查询、可脱敏、可展示的组织级事实源，避免直接输出 JPA 实体或原始敏感 detail。
- 完善 `/admin/ops` 的智能体运行与审计日志界面，保持 `鎏金账房` product register。
- 增加后端集成测试、前端构建和桌面端本地验证记录。

## Out Of Scope

- 主动告警、短信/飞书通知、长期归档检索和跨租户平台全局监控大盘。
- 新增移动端适配、移动端截图或移动端自动化测试。
- 生产发布、ACR 镜像推送或线上部署。

## Preflight

- `python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/dev-login.py .claw --developer MANAGER-001 --branch codex/TASK-146-ops-observability-audit --files docs/specs/FEAT-019-agent-observability-monitoring.md .claw/task-board.md .claw/tasks/TASK-146.md .claw/assignments/TASK-146.yaml .claw/current-status.md .claw/test-report.md --json` -> allowed。
- `impeccable` context loaded: `PRODUCT.md` and `DESIGN.md` are present; register is `product`。

## Implementation Notes

- 新增组织级 `GET /admin/agents/runtime-snapshots`，从 `agent_run_trace` 聚合每个智能体近 7 天会话数、失败数、平均耗时、最后活动和当前状态。
- `/admin/agents/run-logs` 与详情 DTO 增加 `errorReason`；失败原因优先来自失败工具，其次来自工作流失败输出和失败 trace 节点摘要。
- 工具 trace payload 增加 `status` 与 `errorMessage`，失败节点摘要改为 `工具失败：...`，便于管理员直接定位工具失败原因和日志报错。
- `/ops/audit/logs` 改为稳定 DTO 查询接口，支持 `from`、`to`、`eventType`、`q`、`limit`，查询窗口限制在最近 7 天。
- 审计日志 detail 统一脱敏 `Authorization`、token、api key、secret、password、cookie、手机号等敏感内容。
- `/admin/ops` 智能体运行页改读后端 runtime snapshots；日志 tab 增加工具调用、知识库检索入口；失败日志在列表中显示报错摘要。
- `/admin/ops` 审计页从 JSON dump 改为可扫描列表，支持关键词和事件类型筛选，并兼容旧数组响应与新版 `{items}` 响应，降低滚动发布错版风险。

## Verification

- `python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/dev-login.py .claw --developer MANAGER-001 --task TASK-146 --branch codex/TASK-146-ops-observability-audit --files ... --json` -> allowed。
- `python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/check-assignment.py .claw --developer MANAGER-001 --task TASK-146 --branch codex/TASK-146-ops-observability-audit --files ... --json` -> allowed。
- `mvn -q -Dtest=AgentRunTraceIntegrationTest test` in `backend/` -> success；覆盖组织级 run logs、runtime snapshots、工具失败 `errorReason`、审计日志筛选/脱敏和非管理员 403。
- `npm run build` in `frontend/` -> success；保留既有 Vite large chunk warning。
- `git diff --check` -> success。
- Browser desktop smoke for `http://127.0.0.1:5173/admin/ops` -> success；验证智能体运行 tab、工具调用/知识库检索 tab、审计筛选 UI、兼容旧后端审计数组响应、无新增可见布局问题。截图：`output/playwright/task146-admin-ops-agents.png`、`output/playwright/task146-admin-ops-audit.png`。
- `validate-state.py .claw` -> blocked by existing out-of-scope `.claw/tasks/TASK-143.md` 121-line hot-file budget issue; TASK-146 files themselves passed the earlier format issues after spec status/time normalization.
