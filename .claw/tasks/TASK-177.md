---
kind: task-status
task_id: TASK-177
title: 数据洞察仪表盘 UI 热修复
status: completed
owner_role: project-manager
assignee: MANAGER-001
spec_path: docs/specs/FEAT-087-data-insight-dashboard-ui-hotfix.md
assignment_path: .claw/assignments/TASK-177.yaml
updated_at: 2026-07-10T08:43:19+08:00
updated_by: MANAGER-001
---

# TASK-177 - 数据洞察仪表盘 UI 热修复

## 当前目标

删除数据洞察页面顶部无效旧系统信息条，修复下方仪表盘卡片内容越界，并发布新生产版本。

## 当前进展

- 已确认缺陷来源：`DataInsightAppPanel` 渲染了旧系统 context bar；CSS 允许 table/risk card 按内容撑高。
- 已创建 FEAT-087，限定本次只修数据洞察 UI 和发布记录。
- 已删除顶部旧系统信息条和旧 source/context 展示。
- 已固定仪表盘网格行高，并约束长表格/风险列表不再撑破卡片。
- 已完成前端构建、静态扫描、`git diff --check` 和本地桌面端 Playwright 四分类截图检查。
- 已发布生产版本 `2.3.6`，生产数据洞察页面和接口验收通过。

## 计划

1. 删除数据洞察顶部旧系统信息条。`completed`
2. 收紧仪表盘卡片高度和内部滚动/截断。`completed`
3. 跑前端构建和桌面端浏览器视觉检查。`completed`
4. 提交、推送并发布新版本。`completed`

## 验证记录

- `identity-gate`: allowed.
- `assignment-check`: allowed.
- `frontend-build`: `npm run build` -> success.
- `static-check`: `git diff --check` -> success.
- `static-copy-scan`: old data insight context/source labels absent from data insight UI code -> success.
- `local-browser-check`: Playwright verified all four data insight categories with no old top bar, no invalid text, no customer insight panel, and no horizontal overflow -> success.
- `release-2.3.6`: clean worktree release from `aac3080c103c` -> success; Git tag and backend/frontend ACR images pushed.
- `production-deploy-2.3.6`: ECS backup `/opt/cici/backups/20260710-083717-before-2.3.6-task177-data-insight-ui-hotfix`, backend/frontend recreated on `2.3.6`, `/actuator/health=UP`, `/system/version` returned `version=2.3.6`, `imageTag=2.3.6`, `gitCommit=aac3080c103c`.
- `production-browser-check`: production Playwright at `2048x1000` switched `销售业绩` / `客户` / `商机` / `订单回款`; no legacy top bar, no invalid CRM context text, no customer insight DOM, no horizontal overflow, and no card visual leakage -> success. Screenshot: `output/playwright/task177-prod-data-insight-2.3.6.png`.
- `production-api-check`: `/ai/data-insights/dashboard` for demo org returned `sourceMode=REAL_CRM_DEMO`, customers `10`, leads `6`, open opportunities `8`, orders `18`, risk rows `8` -> success.
