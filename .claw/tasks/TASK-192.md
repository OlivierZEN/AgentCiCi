---
kind: task-status
task_id: TASK-192
title: 大数据量 CRM 组织异步初始化与 504 修复
status: ready
owner_role: fullstack-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-100-large-crm-organization-async-sync.md
assignment_path: .claw/assignments/TASK-192.yaml
updated_at: 2026-07-12T01:18:00Z
updated_by: MANAGER-001
---

# TASK-192 - 大数据量 CRM 组织异步初始化与 504 修复

## 目标

将客户互动工作台的 CRM 数据集初始化改为异步单飞和旧数据可继续读取，消除大组织首次加载 504 与 HTML 错误泄漏。

## 计划

1. 实现后端同步状态机、异步加载和 stale-while-revalidate。
2. 调整首页接口与前端加载顺序、同步轮询及错误规范化。
3. 对大组织和演示组织完成真实生产验证并发布。

