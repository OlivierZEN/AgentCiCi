---
kind: task-status
task_id: TASK-309
feature_id: FEAT-188
status: review
updated_at: 2026-08-14T11:17:51Z
updated_by: codex
owner_role: fullstack-agent
spec_path: docs/specs/FEAT-188-conversation-image-paste-attachments.md
---

# TASK-309 - 对话框连续粘贴图片附件

## 范围

- 后端会话图片附件 API、V116/V117 数据迁移、20 MiB/10 张服务端门禁、租户与会话隔离、消息关联和 vision 能力门禁。
- 前端附件队列、Ctrl+V / Command+V 连续粘贴、缩略图、状态、删除/替换、额度提示与流式发送接线。
- 定向及全量测试、构建、桌面浏览器验收、本地 main 提交与 `cici.localhost` 更新。

## 外部交付任务

- DevAutopilot task：`019ffeb0-88a0-739f-afcb-6e667e9d2572`。
- 当前状态：设计已批准；失效租约已通过正式 claim 恢复；代码与测试证据已登记，任务为 `UAT待发布 / revision 14`。

## 完成条件

- [x] 服务端实际大小、类型、10 张额度及数据库唯一槽位门禁实现，定向测试通过。
- [x] 消息关联与 vision 模型门禁实现，纯文本定向回归通过。
- [x] 连续粘贴队列与错误恢复前端测试、50 文件/278 项全量测试和 production build 通过。
- [x] 桌面真实浏览器验证多图上传、缩略图、删除、替换、发送门禁、失败保留与刷新未落库。
- [x] 代码提交进入本地 main，并从明确提交更新本地开发测试环境。
- [x] DevAutopilot 登记 commit/test_report，并按阶段门禁推进至等待 UAT 发布授权。

## 当前验证

- `mvn -q -Dtest=ChatAttachmentServiceTest,ChatOrchestratorServiceModelIdentityTest test`：47 项通过。
- `mvn -q -DskipTests package`：通过。
- `npm test`：50 个文件、278 项通过；`npm run build`：通过，仅保留既有 chunk-size warning。
- `git diff --check`：通过。
- `mvn -q test`：810 项运行后 24 failures / 201 errors / 3 skipped；主要 Spring 集成上下文被共享 `agentcici_test` 的既有 Flyway V81 checksum 漂移阻断，本轮未 repair。另有既有模型调用断言失败，不能作为本任务通过证据。
- 本地主线提交：`a9d838b6`（端到端附件实现）、`b7e03a56`（V117 类型兼容迁移）、`aaf9706b`（删除/新会话提示同步）。
- 本地运行：页面 `2.8.61-dev.aaf9706`；V116/V117 均成功；backend/frontend healthy、restart=0；完整 `./stack verify` 通过。
- 已登录桌面浏览器：两图上传与缩略图、删除、替换均通过；非 vision 模型返回 `409 VISION_MODEL_REQUIRED`，当前页保留输入和附件，刷新后失败消息未落库。系统剪贴板图片注入受浏览器自动化边界限制，paste 入口由前端定向测试覆盖。
- DevAutopilot：已登记 commit 与 test_report、实际消耗 1.0 agent-hour；`implementation_completed`、`local_test_passed` 已依次完成，当前停在 `UAT待发布`。
