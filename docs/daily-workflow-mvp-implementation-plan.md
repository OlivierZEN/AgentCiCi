# 每日流程自动化 MVP 实施方案（可落地版）

## 1. 背景与目标

目标是让系统每天自动执行以下 3 个任务，并将结果主动发送到飞书：

1. 每天 09:00 检查邮箱，生成今日邮件总结，发送到我的飞书
2. 每天 09:30 检查系统待审批记录，发送到我的飞书
3. 每天 10:00 搜索全球前 10 条热门 AI 新闻，发送到我的飞书

本方案聚焦 **MVP（2 周内可上线）**，强调最小功能闭环、可靠执行和可观测。

---

## 2. MVP 范围与非目标

### 2.1 MVP 范围（必须完成）

- 定时触发（按用户时区、cron）
- 任务配置落库（用户维度）
- 调度执行引擎（触发 -> 调用 Agent/工具 -> 生成内容 -> 飞书推送）
- 3 类任务模板：
  - `EMAIL_DAILY_SUMMARY`
  - `PENDING_APPROVALS_DIGEST`
  - `AI_NEWS_TOP10`
- 飞书主动推送（非 reply 场景）
- 执行日志、失败重试、手动重跑
- 前端最小配置页（开关/时间/接收人/最近执行状态）

### 2.2 非目标（MVP 不做）

- 通用 DAG 可视化编排器
- 多租户复杂优先级调度
- 多渠道推送（仅飞书）
- 复杂审批动作回写（仅拉取并汇总）

---

## 3. 总体架构

### 3.1 核心流程

1. 用户在“我的每日流程”中配置任务（时间、时区、接收飞书 openId/chatId、启用状态）
2. 调度器按 cron 触发任务，写入执行记录
3. 执行器按任务类型执行：
   - 邮件：调用 `email_list_inbox/email_search/email_get_message` + LLM 总结
   - 审批：调用 `get_pending_approvals`（需补齐真实实现）+ LLM 总结
   - 新闻：调用 `news_search`（新增）+ LLM 摘要
4. 通过飞书主动发送接口推送结果
5. 更新执行记录状态与错误信息

### 3.2 组件划分（后端）

- `DailyWorkflowConfigService`：配置管理
- `DailyWorkflowScheduler`：定时扫描与触发
- `DailyWorkflowDispatchService`：分发执行逻辑
- `DailyWorkflowExecutionService`：单次执行生命周期管理
- `DailyWorkflowTemplateExecutor`（按模板实现）
- `FeishuPushService`：主动发送文本消息

---

## 4. 数据库设计（Flyway 新增）

## 4.1 表 1：`daily_workflow_config`

用途：保存用户任务配置

建议字段：

- `id` BIGSERIAL PK
- `org_id` VARCHAR(64) NOT NULL
- `user_id` VARCHAR(64) NOT NULL
- `workflow_code` VARCHAR(64) NOT NULL  
  值：`EMAIL_DAILY_SUMMARY` / `PENDING_APPROVALS_DIGEST` / `AI_NEWS_TOP10`
- `enabled` BOOLEAN NOT NULL DEFAULT TRUE
- `cron_expr` VARCHAR(64) NOT NULL  
  示例：`0 0 9 * * *`
- `timezone` VARCHAR(64) NOT NULL DEFAULT `Asia/Shanghai`
- `target_type` VARCHAR(32) NOT NULL DEFAULT `feishu_open_id`  
  （MVP 支持 `feishu_open_id`，预留 chat_id）
- `target_value` VARCHAR(128) NOT NULL
- `payload_json` TEXT NOT NULL DEFAULT '{}'  
  用于存模板参数，如新闻条数、邮件扫描上限
- `last_triggered_at` TIMESTAMP NULL
- `created_at` TIMESTAMP NOT NULL
- `updated_at` TIMESTAMP NOT NULL

唯一键：

- `uk_workflow_user` (`org_id`, `user_id`, `workflow_code`)

索引：

- `idx_workflow_enabled` (`enabled`)
- `idx_workflow_org_user` (`org_id`, `user_id`)

## 4.2 表 2：`daily_workflow_execution`

用途：保存每次执行记录，支撑重试/审计/排障

建议字段：

- `id` BIGSERIAL PK
- `config_id` BIGINT NOT NULL
- `org_id` VARCHAR(64) NOT NULL
- `user_id` VARCHAR(64) NOT NULL
- `workflow_code` VARCHAR(64) NOT NULL
- `scheduled_time` TIMESTAMP NOT NULL
- `started_at` TIMESTAMP NULL
- `finished_at` TIMESTAMP NULL
- `status` VARCHAR(32) NOT NULL  
  值：`QUEUED` / `RUNNING` / `SUCCESS` / `FAILED` / `SKIPPED`
- `attempt_no` INT NOT NULL DEFAULT 1
- `error_code` VARCHAR(64) NULL
- `error_message` TEXT NULL
- `output_summary` TEXT NULL
- `raw_result_json` TEXT NULL
- `created_at` TIMESTAMP NOT NULL

索引：

- `idx_exec_config_time` (`config_id`, `scheduled_time` DESC)
- `idx_exec_status` (`status`)
- `idx_exec_org_user` (`org_id`, `user_id`)

---

## 5. 后端 API 设计（MVP）

前缀建议：`/me/daily-workflows`

### 5.1 配置接口

- `GET /me/daily-workflows`
  - 返回当前用户 3 个模板的配置与最近执行状态

- `PUT /me/daily-workflows/{workflowCode}`
  - 入参：
    - `enabled`
    - `cronExpr`
    - `timezone`
    - `targetType`
    - `targetValue`
    - `payload`（模板参数）

- `POST /me/daily-workflows/{workflowCode}/run-now`
  - 手动触发一次，返回 executionId

### 5.2 执行日志接口

- `GET /me/daily-workflows/executions?workflowCode=...&page=...`
- `GET /me/daily-workflows/executions/{executionId}`

---

## 6. 核心执行逻辑设计

### 6.1 调度器

- 启用 `@EnableScheduling`
- 每 30 秒扫描一次“未来 1 分钟窗口”内应触发的任务
- 去重策略：
  - `(config_id, scheduled_time)` 唯一约束（或逻辑去重）
  - 避免多实例重复触发（MVP 单实例可先不做分布式锁）

### 6.2 执行器通用流程

1. 创建执行记录 `QUEUED`
2. 进入 `RUNNING`（写 started_at）
3. 根据 `workflow_code` 执行模板逻辑
4. 调用 `FeishuPushService.sendText(...)`
5. 成功写 `SUCCESS`，失败写 `FAILED`
6. 失败重试（最多 2 次，指数退避 1m/3m）

### 6.3 模板执行细节

#### A. `EMAIL_DAILY_SUMMARY`

- 前置校验：用户存在启用邮箱账号
- 调用：
  - `email_list_inbox(limit=20)` 或 `email_search(...)`
- 用模型生成摘要：
  - 今日重点邮件
  - 待处理事项
  - 风险提醒
- 输出格式（固定模板）后推送飞书

#### B. `PENDING_APPROVALS_DIGEST`

- 前置校验：`get_pending_approvals` 工具可真实执行
- 调用审批工具拉取记录
- 生成摘要：
  - 总数
  - 紧急项（按截止时间）
  - 建议优先顺序
- 推送飞书

> 注意：该工具当前仅在目录暴露，需补齐 `ToolOrchestratorService` 的 native 分发实现。

#### C. `AI_NEWS_TOP10`

- 新增工具：`news_search`
  - 入参：`query`, `limit=10`, `language`, `timeRange=24h`
- 输出：
  - 标题、来源、发布时间、链接
- 模型生成“10 条快报 + 3 条重点解读”
- 推送飞书

---

## 7. 飞书主动推送改造

当前 `FeishuBotMessenger` 以 `reply(messageId)` 为主，MVP 需要新增主动发送：

- 新增 `sendTextToOpenId(appId, appSecret, openId, text)`
- 可选新增 `sendTextToChatId(...)`
- 消息长度保护（如 > 6000 截断并附“查看详情链接/任务ID”）
- 失败错误码透传到执行记录

---

## 8. 前端 MVP 页面

建议新增：`MyDailyWorkflowsPanel.tsx`（放“个人信息”）

页面字段：

- 任务名（3 个固定卡片）
- 启用开关
- 执行时间（时:分）
- 时区
- 接收目标（飞书 openId）
- 最近一次执行状态、执行时间、错误摘要
- 按钮：`立即执行`

交互要求：

- 保存后即时 toast 反馈
- 执行中状态轮询
- 错误信息支持展开查看

---

## 9. 安全与治理

- 权限：仅当前登录用户可管理自己的 daily workflow
- 审计：记录配置变更、手动触发、执行结果
- 限流：`run-now` 每用户每小时上限（如 20 次）
- 数据脱敏：日志中不记录邮件正文全量，仅摘要

---

## 10. 验收标准（Definition of Done）

### 功能验收

- 可配置并保存 3 个任务，刷新后不丢失
- 到点自动触发并收到飞书消息
- 手动执行可立即收到消息
- 执行失败可在日志中看到错误原因

### 技术验收

- 单元测试覆盖核心服务（配置/调度/执行）
- 集成测试覆盖 3 个模板 happy-path
- 本地压测 100 用户 x 3 任务/日，无重复触发

### 运营验收

- 提供 runbook：任务不执行、飞书推送失败、工具超时的排障步骤

---

## 11. 里程碑排期（建议 10 个工作日）

### D1-D2：基础设施

- 表结构 + Flyway
- 配置 API + 执行记录 API

### D3-D4：调度与执行框架

- Scheduler + ExecutionService + Retry
- run-now 能跑通 dummy 模板

### D5-D6：模板能力接入

- 邮件总结模板
- 审批汇总模板（补齐 `get_pending_approvals` 分发）

### D7：新闻能力

- `news_search` 工具接入
- AI 新闻模板完成

### D8：飞书主动推送

- `sendToOpenId` 打通 + 错误码治理

### D9：前端配置页

- 个人配置页面 + 执行日志 + 立即执行

### D10：联调与验收

- 端到端演练
- 补齐测试与 runbook

---

## 12. 风险与应对

- 风险：外部源波动（邮件/新闻/飞书 API）
  - 应对：重试 + 熔断 + 失败降级消息

- 风险：重复触发
  - 应对：执行幂等键 `(config_id, scheduled_time)`

- 风险：新闻质量不稳定
  - 应对：来源白名单 + 发布时间窗口 + 去重策略

---

## 13. 建议的首批实现清单（可直接建任务）

1. `V17__daily_workflow_tables.sql`
2. `DailyWorkflowConfigEntity/Repository`
3. `DailyWorkflowExecutionEntity/Repository`
4. `DailyWorkflowController (/me/daily-workflows)`
5. `DailyWorkflowScheduler`
6. `DailyWorkflowDispatchService`
7. `FeishuPushService`（主动消息）
8. `PendingApprovalsToolExecutor`（补齐 get_pending_approvals）
9. `NewsToolService`（新增 news_search）
10. `frontend: MyDailyWorkflowsPanel.tsx`

---

如果你确认这个方案，我下一步可以直接给你拆成「按文件粒度」的实施清单（每个任务对应修改文件、接口契约、测试用例），你团队可以按清单并行开工。

