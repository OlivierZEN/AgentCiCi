---
kind: task-status
task_id: TASK-214
status: review
updated_at: 2026-07-21T09:35:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-214.yaml
spec_path: docs/specs/FEAT-119-onekeytoken-live-validation.md
---

# TASK-214 - OneKeyToken 实时凭据检测修复

## Scope

- 修复运营端 OneKeyToken “检测”只读取静态目录、错误 Key 仍显示成功的问题。
- 用当前表单草稿调用 OneKeyToken Chat Completions，并提供无泄露的成功与失败反馈。

## Current State

- 已修复：OneKeyToken 检测使用当前表单草稿向 `{baseUrl}/chat/completions` 发起非流式请求，携带 Bearer 鉴权和唯一 `x-request-id`；静态目录仅保留给“全部模型”。
- 错误 Key 的 401/403 会返回脱敏的可操作错误，草稿 Key 和地址不保存、不写入审计或响应。
- 全新临时 PostgreSQL 下的后端集成测试通过，覆盖真实请求契约、错误 Key、草稿不持久化和无 Key 回显；前端草稿请求单测和生产构建通过。

## Next Action

- 在 `codex/TASK-214-onekeytoken-live-validation` 审阅并合并；不含生产发布。

## Changed Files

- `backend/src/main/java/com/codehouse/ciciassistant/model/api/PlatformModelProviderController.java`
- `backend/src/main/java/com/codehouse/ciciassistant/model/service/ModelProviderService.java`
- `backend/src/test/java/com/codehouse/ciciassistant/model/PlatformModelProviderIntegrationTest.java`
- `frontend/src/platform/pages/PlatformModelsPage.tsx`
- `frontend/src/platform/pages/PlatformModelsPage.test.tsx`
