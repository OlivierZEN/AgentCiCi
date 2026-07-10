---
kind: task-status
task_id: TASK-181
title: 客户互动工作台客户列表排版修复
status: done
owner_role: fullstack-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-091-customer-workbench-account-list-alignment.md
assignment_path: .claw/assignments/TASK-181.yaml
updated_at: 2026-07-10T14:18:00+08:00
updated_by: MANAGER-001
---

# TASK-181 - 客户互动工作台客户列表排版修复

## 当前目标

修复生产截图反馈的客户互动工作台左侧客户列表排版混乱问题，让客户名称、关注状态、负责人/阶段、时间、标签和最近互动摘要在窄队列中稳定对齐。

## 当前进展

- 已读取项目产品和设计事实源。
- 已确认根因集中在客户列表行的 grid/flex-wrap 截断和 AI 应用页窄队列覆盖规则。
- 已完成任务授权检查和前端 CSS 热修。
- 已完成本地构建和桌面 Chrome 截图验证。
- 已发布生产 `2.3.9`，并完成生产健康、公网、浏览器截图和客户工作台接口 smoke。

## 计划

1. 创建 FEAT-091、TASK-181 和授权边界。`done`
2. 调整客户列表行结构与 CSS 截断、行高、标签约束。`done`
3. 本地构建与桌面端浏览器截图验证。`done`
4. 提交推送，并按需要发布生产与回写发布证据。`done`

## 验证记录

- `identity-gate`: generic MANAGER-001 login and task-scoped `dev-login.py .claw --developer MANAGER-001 --task TASK-181 --branch main --files ... --json` -> **allowed**。
- `assignment-check`: `check-assignment.py .claw --developer MANAGER-001 --task TASK-181 --branch main --files ... --json` -> **allowed**。
- `frontend-build`: `npm --prefix frontend run build` -> **success**；保留既有 Vite large chunk warning。
- `local-browser-account-list`: Chrome at `2048x1000`, mocked authenticated local APIs, `/app?aiApp=customer-workbench` -> **success**；客户行 `4` 条，高度均为 `104px`，无相邻重叠、无行级横向/纵向溢出、客户名称未被队列标题字号规则误伤，document/body 无外层滚动条，console errors `0`。
- 截图证据：`output/playwright/task181-account-list-local.png`。
- `compose-config`: `docker compose --env-file deploy/acr.env.example -f deploy/docker-compose.acr.yml config` -> **success**。
- `release-dry-run-2.3.9`: `./scripts/release-acr.sh --dry-run` -> **success**，版本 `2.3.9`，commit `0c8f66e94d15`。
- `release-2.3.9`: `./scripts/release-acr.sh --version 2.3.9` -> **success**；backend/frontend linux/amd64 镜像已推送，Git tag `2.3.9` 已推送。
- `production-backup-2.3.9`: `/opt/cici/backups/20260710-141251-before-2.3.9-task181-account-list-alignment` -> **success**，包含 `acr.env.before-2.3.9`、`postgres.dump`、`kb-files.tgz`、`qdrant.tgz`。
- `production-deploy-2.3.9`: backend/frontend recreated and healthy；infra services remained healthy on `2.3.4`；`/actuator/health=UP`；`/system/version` returned `version=imageTag=2.3.9`, `gitCommit=0c8f66e94d15`；Nginx config passed；recent backend error scan empty。
- `public-smoke`: `https://x.agentcici.com/`、`/app?aiApp=customer-workbench` -> HTTP `200`；`http://x.agentcici.com/` -> HTTPS `301`。
- `production-browser-account-list`: authenticated production browser at `2048x1000` for org `org2sva14i4udjmi2t4s` -> **success**；客户行 `6` 条，高度均为 `104px`，无相邻重叠、无行级横向/纵向溢出，页面外层无滚动条，version `2.3.9` visible，console errors `0`。
- `production-api-workbench`: org `org2sva14i4udjmi2t4s` customer workbench -> **success**；accounts `10`，first detail timeline `3`，recommendations `2`，`crmConnection.ready=true`。
- 生产截图证据：`output/playwright/task181-prod-account-list-2.3.9.png`。

## 变更文件

- `docs/specs/FEAT-091-customer-workbench-account-list-alignment.md`
- `.claw/tasks/TASK-181.md`
- `.claw/assignments/TASK-181.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/test-report.md`
- `.claw/devops.md`
- `frontend/src/assistant/customer-workbench/CustomerWorkbenchApp.tsx`
- `frontend/src/assistant/cici-ui.css`

## 交接约束

- 不改变客户互动工作台业务 API、CRM 绑定、智能体调用链路或语音 ASR 行为。
- 不新增移动端适配或移动端自动化测试。
- 不输出、提交或记录任何凭证、token、cookie 或可复用会话信息。
