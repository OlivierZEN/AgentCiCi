---
kind: feature-spec
feature_id: FEAT-100
title: 大数据量 CRM 组织异步初始化与稳定读取
status: in_implementation
owner_role: fullstack-agent
task_ids: TASK-192
related_decisions: FEAT-081,FEAT-098,FEAT-099
related_issues: none
updated_at: 2026-07-12T01:30:00Z
updated_by: MANAGER-001
---

# FEAT-100 - 大数据量 CRM 组织异步初始化与稳定读取

## 生产问题与证据

- 组织 `org5nszpgj99jaysxv6y` 的客户互动工作台首次加载在 2026-07-12 08:58:38 同时触发 `integration-status`、`notifications`、`supervisor-summary` 和 `queue`，四个请求均在 Nginx 默认 60 秒读取超时后返回 504。
- 受控直连后端复测耗时 94.99 秒后成功，Owner 用户可见 Account 返回 10,000 条，等于现实现 `50 页 x 200 条` 的安全上限。
- 当前四个入口都会触发同一 `dataset()`；单飞锁只避免重复拉取，却让其余请求同步等待。数据集还会顺序全量读取 Account、Contact、Opportunity、Task、Event、Case 和 Contract，45 秒 TTL 对大组织过短。
- 前端将非 JSON 的 504 HTML 原文作为错误提示展示，造成页面污染。

## 目标

- 首次进入页面的 HTTP 请求在数秒内返回连接或同步状态，不因 CRM 全量初始化超过网关超时。
- 每个组织成员同一时刻最多存在一个 CRM 数据集加载任务；已有数据过期时继续服务旧数据并后台刷新。
- UI 明确显示“正在同步 CRM 数据”，自动轮询直至可用；同步失败展示稳定的中文业务消息。
- 保持当前用户身份和 CloudCC 记录权限边界，不共享不同用户的数据集。

## 设计

### 后端同步状态机

- `EMPTY -> SYNCING -> READY`；失败进入 `FAILED`，再次读取可触发重试。
- 数据集缓存键继续使用 `orgId:userId`，确保同一 CRM 用户权限一致。
- 缓存有效期提升到适合 CRM 工作台的分钟级；到期后采用 stale-while-revalidate，返回最后一次成功数据并异步刷新。
- 首次无数据时，`integration-status`、`queue`、`notifications` 和 `supervisor-summary` 立即返回同步状态或空结果，不阻塞等待远端全量分页。
- 后台加载使用独立有界执行器和单飞 future；任务完成后原子替换数据集并记录加载时间、记录数和耗时，失败保留明确错误。

### API 语义

- `integration-status` 增加 `syncStatus`、`syncing`、`lastSuccessfulSyncAt`、`syncMessage`。
- `queue` 增加同样的同步元数据；首次同步返回空分页和 `source=CLOUDCC_SYNCING`，不是异常。
- `notifications` 与 `supervisor-summary` 在数据未就绪时返回稳定空结构，不触发阻塞式初始化。
- 手工刷新触发后台刷新；有旧数据时继续展示旧数据。

### 前端行为

- 首屏先读取连接/同步状态；同步中显示非错误状态，并按退避间隔轮询。
- 队列响应为同步中时不选中客户、不请求详情，完成后自动重载队列。
- 通知和主管摘要延后到数据 READY 后加载，消除首页四路全量初始化。
- HTTP 非 JSON 错误统一映射为简洁中文提示，禁止展示 HTML 响应正文。

## 约束与后续

- 本任务解决 504、并发阻塞和首次加载体验；不通过单纯放大 Nginx 超时掩盖问题。
- 10,000 条上限意味着超大型组织的数据仍可能被截断。本任务必须在状态中暴露截断/上限提示；完整增量同步到本地投影库作为后续独立架构任务，不在本次热修中伪装为已解决。

## 验收标准

- 大组织首次 `integration-status` 和 `queue` 均在 5 秒内返回 `SYNCING`，无 504。
- 同一用户并发四个首页请求只启动一个后台同步任务。
- 同步完成后自动出现真实客户数据；缓存过期时旧数据仍可读取。
- 前端不显示任何 Nginx HTML；同步中、失败、截断均有明确中文状态。
- 小数据演示组织现有新客户推进、老客户经营、详情和 CRM 权限行为不回归。
