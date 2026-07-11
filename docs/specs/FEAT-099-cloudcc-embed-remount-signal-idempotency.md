---
kind: feature-spec
feature_id: FEAT-099
title: CloudCC 嵌入页重复刷新稳定性
status: ready
owner_role: fullstack-agent
task_ids: TASK-191
related_decisions: FEAT-081,FEAT-098
related_issues: none
updated_at: 2026-07-11T15:26:48Z
updated_by: MANAGER-001
---

# FEAT-099 - CloudCC 嵌入页重复刷新稳定性

## 问题

- CRM 注入页首次刷新可以加载工作台，再次刷新可能白屏；白屏时 AgentCiCi 未收到新的 iframe 页面请求，说明 CloudCC pagecomponent 没有重新挂载。
- 工作台已加载时仍可能显示 `Unexpected server error`；生产日志证实同一客户的并发详情请求同时插入相同客户信号 `public_id`，触发唯一键冲突。

## 设计

- pagecomponent UMD 通过 DOM 变更观察器持续发现延迟创建或被 CloudCC 复用的组件节点。
- 已标记挂载但内部 iframe 被宿主清空时，销毁旧 Vue 实例并重新挂载；fallback 的 SSO、resize 状态也同步重置。
- 客户信号通过 PostgreSQL `INSERT ... ON CONFLICT DO UPDATE` 原子写入，消除跨请求和跨实例的查询后插入竞态；单条 UPSERT 在仓储层建立短事务，避免把 CRM 网络读取纳入长事务；稳定 ID 语义保持不变。
- 所有 CloudCC 发布、customPage 绑定和注入验证继续通过 `cc-customization-expert-msapi` 完成。

## 验收标准

- 组件节点晚于脚本 500ms 出现仍会挂载 iframe。
- 宿主清空并复用同一组件节点后能够重新挂载。
- 同一客户多路并发信号持久化不触发唯一键异常。
- 真实 CRM 页面连续刷新至少三次均显示工作台，且页面不再出现 `Unexpected server error`。
