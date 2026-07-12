---
kind: feature-spec
feature_id: FEAT-102
title: 客户互动整理期间上下文与队列稳定性
status: verified
owner_role: fullstack-agent
task_ids: TASK-196
related_decisions: FEAT-081,FEAT-100,FEAT-101
related_issues: none
updated_at: 2026-07-12T04:58:00Z
updated_by: MANAGER-001
---

# FEAT-102 - 客户互动整理期间上下文与队列稳定性

## 问题与证据

- 互动批次确认后，前端 `reloadDetail()` 额外请求 `queue?refresh=true`，触发大组织 Account、Contact、Opportunity、Task、Event、Case、Contract 全量后台同步。
- 同步完成后队列重新排序；现有选择规则在当前客户不属于当前页、筛选或搜索结果时，自动切换到第一页第一位客户。
- 整理弹窗直接接收动态 `activeAccountId`，父页面改选客户后，弹窗会加载另一个客户的历史批次，但仍保留原批次草稿，形成上下文漂移。
- 助手把任何包含“老客户”或“客户经营”的语句解析为 `SWITCH_MODE`；即使已在老客户经营页，也会清空当前客户、页码、筛选和详情。
- 生产访问日志在互动确认后记录 `queue?...refresh=true`，随后同一弹窗会话从原 Account 请求切换到另一 Account，与用户截图和描述一致；这属于组件状态重置，不是浏览器整页刷新。

## 设计

- 本地互动、建议状态和反馈操作完成后，只读取当前客户详情并按原条件普通读取队列，不触发 CRM 全量刷新。只有用户明确点击“刷新 CRM 数据”才允许 `refresh=true`。
- 队列请求返回时，若已有选中客户，保持该客户；仅在当前没有选择时自动选择队列第一条。分页、筛选、搜索和后台同步不得擅自替换当前客户。
- 打开“整理互动记录”时冻结 `accountId` 和客户名称；弹窗创建批次、历史查询、确认及完成回读始终使用该上下文。确认后恢复并刷新该客户详情。
- `SWITCH_MODE` 只接受明确命令句，例如“切换到老客户经营”或“打开新客户推进”；分析性自然语言不产生导航动作。前端对切换到当前模式的动作做幂等忽略。
- 保留用户明确切换模式、点击下一个客户和点击队列客户的现有行为。

## 验收标准

- 搜索并选择一个不在默认队列第一页的老客户，完成互动整理后，搜索词、模式、筛选、页码和当前客户均保持不变。
- 互动确认不再产生 `queue?...refresh=true`，也不启动 CRM 全量同步。
- 整理过程中即使队列重载，弹窗 Account 上下文与批次 Account 一致。
- “分析这个老客户经营情况”返回 `NONE`；“切换到老客户经营”才返回 `SWITCH_MODE`。
- 自动化覆盖状态协调、模式命令识别和真实生产浏览器回归。

## 生产验收

- 发布版本：`2.5.9 / 6c7e27181fbb`。
- 真实组织 `org5nszpgj99jaysxv6y` 搜索“奔驰”得到 4 条全量客户结果，选中“梅赛德斯-奔驰汽车金融有限公司”完成受控文本互动归集。
- 确认后立即及 35 秒轮询后，搜索词、结果数、工作台模式和当前客户均保持；新记录进入该客户时间线。
- 浏览器网络轨迹只有普通 `queue?mode=existing&query=奔驰` 请求，`refresh=true` 为 0；确认后未启动新的 10,000 Account CRM 投影同步。
- 前端 64 项测试和生产构建通过；后端客户工作台/CRM 投影 12 项定向测试通过；浏览器控制台 0 错误，发布后 Nginx 5xx 和后端目标错误为空。
- 截图：`output/playwright/task196-prod-customer-context-stable-2.5.9.png`。
