---
kind: task-status
task_id: TASK-198
title: AI 动态客户信号与可解释评分升级
status: review
owner_role: fullstack-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-104-ai-dynamic-customer-scoring.md
assignment_path: .claw/assignments/TASK-198.yaml
updated_at: 2026-07-12T11:22:43Z
updated_by: MANAGER-001
---

# TASK-198 - AI 动态客户信号与可解释评分升级

## 目标

用可追溯、可衰减、可管理的 AI 动态业务信号替换固定客户记录计数评分。

## 计划

1. 建立动态信号与评分快照模型。
2. 扩展互动分析契约并实现增量信号归集。
3. 实现冲突、衰减、生命周期和多维评分聚合。
4. 实现评分解释 API、页面明细和证据跳转。
5. 完成自动化、真实数据和生产发布验收。

## 当前结果

- V77 已新增可审计动态信号和评分快照。
- 新确认互动通过 AI JSON 契约输出证据、影响、置信度和有效期，仅增量重算当前客户。
- 列表、筛选、排序、详情和解释接口统一使用批量评分快照；证据不足时显示 50 分中性基线，不伪造判断。
- 评分抽屉支持全部、加分、减分、待确认和互动档案跳转；本地桌面浏览器无外层滚动或布局溢出。
- 等待 `2.5.12` 生产发布及真实 CRM 验收后关闭任务。
