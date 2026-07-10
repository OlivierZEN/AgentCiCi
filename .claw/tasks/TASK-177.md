---
kind: task-status
task_id: TASK-177
title: 数据洞察仪表盘 UI 热修复
status: in_progress
owner_role: project-manager
assignee: MANAGER-001
spec_path: docs/specs/FEAT-087-data-insight-dashboard-ui-hotfix.md
assignment_path: .claw/assignments/TASK-177.yaml
updated_at: 2026-07-10T08:25:49+08:00
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

## 计划

1. 删除数据洞察顶部旧系统信息条。`completed`
2. 收紧仪表盘卡片高度和内部滚动/截断。`completed`
3. 跑前端构建和桌面端浏览器视觉检查。`completed`
4. 提交、推送并发布新版本。`in_progress`

## 验证记录

- `identity-gate`: allowed.
- `assignment-check`: allowed.
- `frontend-build`: `npm run build` -> success.
- `static-check`: `git diff --check` -> success.
- `static-copy-scan`: old data insight context/source labels absent from data insight UI code -> success.
- `local-browser-check`: Playwright verified all four data insight categories with no old top bar, no invalid text, no customer insight panel, and no horizontal overflow -> success.
