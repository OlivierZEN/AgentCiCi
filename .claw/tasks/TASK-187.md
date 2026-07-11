---
kind: task-status
task_id: TASK-187
title: AI 应用壳层导航稳定性治理
status: done
owner_role: frontend-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-095-ai-app-shell-navigation-stability.md
assignment_path: .claw/assignments/TASK-187.yaml
updated_at: 2026-07-11T05:53:07Z
updated_by: MANAGER-001
---

# TASK-187 - AI 应用壳层导航稳定性治理

## 目标

消除 AI 应用画布外侧间距和侧栏 hover 抖动，解耦应用菜单与画布切换，并统一关闭按钮与筛选箭头。

## 计划

1. 调整壳层布局和侧栏固定尺寸状态矩阵。
2. 解耦 AI 应用菜单开关与 workspace 切换。
3. 统一菜单关闭和概览筛选图标。
4. 完成浏览器交互、尺寸、computed style 与生产发布验收。

## 当前进展

- 已确认画布缝隙来自 `.cici-ai-apps` 外层 padding。
- 已确认抖动来自全局 button hover 位移与头像局部 scale。
- 已确认 AI 应用一级按钮当前直接调用 `setWorkspaceTab("aiApps")`。
- 已完成菜单开关与 workspace 切换解耦，具体应用项成为唯一画布切换入口。
- 已完成侧栏固定几何状态、AI 应用画布去间距、Lucide 关闭/下拉图标和设计事实源更新。
- 57 项前端测试、Vite production build、DESIGN.json 解析与 `git diff --check` 通过。
- 已发布生产版本 `2.4.6`（提交 `f7f0e829b9cd`）；前后端镜像、Git tag、备份、六服务健康、Nginx、公网三入口和稳定期日志复查通过。
- 生产 1920x960 浏览器验收确认：侧栏与 AI 应用画布间距 `0`、画布 padding `0`、侧栏 hover 前后尺寸不变且 `transform/box-shadow=none`；点击一级“AI应用”只开关菜单，点击具体应用才切换主区域；菜单关闭按钮默认/hover 均无套框；两个筛选按钮使用居中的 Lucide `ChevronDown`；document/body 无外层溢出且控制台错误为 `0`。
- 截图：`output/playwright/task187-prod-shell-stable.png`、`output/playwright/task187-prod-ai-app-menu.png`。
