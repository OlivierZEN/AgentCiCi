---
kind: task-status
task_id: TASK-309
feature_id: FEAT-188
status: testing
updated_at: 2026-08-14T10:52:54Z
updated_by: codex
owner_role: fullstack-agent
spec_path: docs/specs/FEAT-188-conversation-image-paste-attachments.md
---

# TASK-309 - 对话框连续粘贴图片附件

## 范围

- 后端会话图片附件 API、V116 数据迁移、20 MiB/10 张服务端门禁、租户与会话隔离、消息关联和 vision 能力门禁。
- 前端附件队列、Ctrl+V / Command+V 连续粘贴、缩略图、状态、删除/重试、额度提示与流式发送接线。
- 定向及全量测试、构建、桌面浏览器验收、本地 main 提交与 `cici.localhost` 更新。

## 外部交付任务

- DevAutopilot task：`019ffeb0-88a0-739f-afcb-6e667e9d2572`。
- 当前状态：设计已批准，哪吒已重新认领，`开发中 / revision 9`，实例 1/1。

## 完成条件

- [x] 服务端实际大小、类型、10 张额度及数据库唯一槽位门禁实现，定向测试通过。
- [x] 消息关联与 vision 模型门禁实现，纯文本定向回归通过。
- [x] 连续粘贴队列与错误恢复前端测试、50 文件/278 项全量测试和 production build 通过。
- [ ] 桌面真实浏览器验证上传、删除、重试、额度与发送状态。
- [ ] 代码提交进入本地 main，并从该提交更新本地开发测试环境。
- [ ] DevAutopilot 登记 commit/test_report，按阶段门禁推进至等待 UAT 发布授权。

## 当前验证

- `mvn -q -Dtest=ChatAttachmentServiceTest,ChatOrchestratorServiceModelIdentityTest test`：47 项通过。
- `mvn -q -DskipTests package`：通过。
- `npm test`：50 个文件、278 项通过；`npm run build`：通过，仅保留既有 chunk-size warning。
- `git diff --check`：通过。
- `mvn -q test`：810 项运行后 24 failures / 201 errors / 3 skipped；主要 Spring 集成上下文被共享 `agentcici_test` 的既有 Flyway V81 checksum 漂移阻断，本轮未 repair。另有既有模型调用断言失败，不能作为本任务通过证据。
