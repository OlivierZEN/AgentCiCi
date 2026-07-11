---
kind: task-status
task_id: TASK-187
title: AI 应用壳层导航稳定性治理
status: in_progress
owner_role: frontend-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-095-ai-app-shell-navigation-stability.md
assignment_path: .claw/assignments/TASK-187.yaml
updated_at: 2026-07-11T05:42:34Z
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
- 57 项前端测试、Vite production build、DESIGN.json 解析与 `git diff --check` 通过；待生产浏览器和发布验收。
