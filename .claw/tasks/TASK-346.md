---
kind: task-status
task_id: TASK-346
feature_id: FEAT-206
status: in_progress
priority: critical
primary_project: agentcici
owner_role: backend-agent
claimed_by: codex
spec_path: docs/specs/FEAT-206-openapi-file-attachment-runtime.md
updated_at: 2026-08-31T04:15:00Z
updated_by: codex
---

# TASK-346 - OpenAPI 文件附件统一运行时

## 范围

- 修复 OpenAPI 文件上传只存 metadata、不存二进制且不进入模型的根因。
- 把 `upload_file_id` 和受控 HTTPS URL 归一化到现有 `ChatAttachmentService` / `ChatOrchestratorService`。
- 补齐四层作用域、明确错误、参数发现、blocking/streaming、多轮复用和安全 URL 抓取测试。
- 从本地 `main` 构建并更新 `cici.localhost` backend，完成真实 OpenAPI 图片对话回归。

## 完成条件

- [x] multipart 与 HTTPS URL 图片都形成 READY 私有文件记录并进入共享模型视觉 content block；真实模型运行态待部署回归。
- [x] 文档复用现有受控文本提取；不支持类型和模型能力明确失败，模型调用为零。
- [x] API Key、Agent、external user、conversation 任一越权均不可枚举。
- [x] blocking、streaming、幂等与同会话多轮复用使用同一路径。
- [x] 聚焦测试、backend package、迁移验证和 `git diff --check` 通过。
- [ ] 变更提交并合并本地 `main`；`cici.localhost` 从该 commit 构建、健康并完成真实图片回归。

## 当前证据

- 后端聚焦测试、package、前端 60 文件/332 项与 production build 通过。
- 空 PostgreSQL 16.9 成功迁移至 V128；multipart 与 URL 导入两条控制器附件链路通过。
- 完整 OpenAPI 集成类为 16/17，唯一失败是任务外既有 placeholder 模型路由断言；未误报全类通过。
- 下一步只从本地 `main` 明确提交构建和替换 AgentCiCi backend/frontend，不修改远程、UAT 或生产。

## 回滚

- 关闭 `openapi.attachment-runtime-v2` 后携带附件的请求返回明确暂不可用错误。
- 应用可回滚到前一 commit；新迁移字段保持向后兼容，已存文件不在回滚时删除。
