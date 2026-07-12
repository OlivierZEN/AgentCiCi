---
kind: task-status
task_id: TASK-192
title: 大数据量 CRM 组织异步初始化与 504 修复
status: in_progress
owner_role: fullstack-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-100-large-crm-organization-async-sync.md
assignment_path: .claw/assignments/TASK-192.yaml
updated_at: 2026-07-12T01:30:00Z
updated_by: MANAGER-001
---

# TASK-192 - 大数据量 CRM 组织异步初始化与 504 修复

## 目标

将客户互动工作台的 CRM 数据集初始化改为异步单飞和旧数据可继续读取，消除大组织首次加载 504 与 HTML 错误泄漏。

## 计划

1. 实现后端同步状态机、异步加载和 stale-while-revalidate。
2. 调整首页接口与前端加载顺序、同步轮询及错误规范化。
3. 对大组织和演示组织完成真实生产验证并发布。

## 当前进展

- 已完成异步单飞状态机、10 分钟缓存与 stale-while-revalidate。
- 已完成关联对象预分组、建议批量读取和主管摘要单次计算。
- 已完成前端同步轮询、延迟加载提醒/摘要和 HTML 错误规范化。
- 后端 10 项相关测试、10,000 客户规模测试、前端 58 项测试及前后端构建通过；待生产发布验收。
