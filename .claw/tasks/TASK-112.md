---
kind: task-status
task_id: TASK-112
assignee: DEV-fengchu
status: ready
branch: codex/TASK-112-agent-openapi-dify-parity
pr_url: n/a
updated_at: 2026-05-18T04:04:58Z
updated_by: MANAGER-001
---

# TASK-112 - Agent Open API Dify parity enhancement

## 当前状态

- 状态：`ready`
- 分配人：`MANAGER-001`
- 负责人：`DEV-fengchu`
- 授权文件：`.claw/assignments/TASK-112.yaml`
- 规格文件：`docs/specs/FEAT-036-agent-open-api-dify-parity.md`
- 写入范围：见授权文件 `scope_files`

## 目标

- 对标 Dify Service API 的常用 Agent/OpenAPI 能力，增强当前 FEAT-021 Agent Open API。
- 保持既有 `/health`、`/chat`、`/chat/stream` 兼容，不破坏当前外部调用方。
- 新增参数发现、Dify 风格 chat-messages、停止生成、会话/消息列表、反馈、建议问题、文件上传和必要 scopes/文档。

## 已完成

- 2026-05-18T04:04:58Z：`MANAGER-001` 已创建规格和 assignment，将任务分配给 `DEV-fengchu`。

## 修改范围

- 待 `DEV-fengchu` 接手后记录。

## 验证记录

- 状态：`not_run`
- 命令：`not_run`
- 结果：`not_run`

## 阻塞点

- `DEV-fengchu` 开始前必须按 assignment 运行本地 SSH challenge-response 身份验证。

## 交接说明

- 先读 `docs/specs/FEAT-036-agent-open-api-dify-parity.md` 和 `docs/specs/FEAT-021-agent-open-api.md`。
- 先补安全边界和兼容层测试，再铺开文件/反馈/会话能力。
- 任意 UI 改动必须继续遵守 `DESIGN.md`、`DESIGN.json` 和 `AGENTS.md` 中的 `鎏金账房` 产品页规则。
