---
kind: task-status
task_id: TASK-346
feature_id: FEAT-206
status: review
priority: critical
primary_project: agentcici
owner_role: backend-agent
claimed_by: codex
spec_path: docs/specs/FEAT-206-openapi-file-attachment-runtime.md
updated_at: 2026-08-31T05:04:00Z
updated_by: codex
---

# TASK-346 - OpenAPI 文件附件统一运行时

## 范围

- 修复 OpenAPI 文件上传只存 metadata、不存二进制且不进入模型的根因。
- 把 `upload_file_id` 和受控 HTTPS URL 归一化到现有 `ChatAttachmentService` / `ChatOrchestratorService`。
- 补齐四层作用域、明确错误、参数发现、blocking/streaming、多轮复用和安全 URL 抓取测试。
- 从本地 `main` 构建并更新 `cici.localhost` backend，完成真实 OpenAPI 图片对话回归。

## 完成条件

- [x] multipart 与 HTTPS URL 图片都形成 READY 私有文件记录并进入共享模型视觉 content block。
- [x] 文档复用现有受控文本提取；不支持类型和模型能力明确失败，模型调用为零。
- [x] API Key、Agent、external user、conversation 任一越权均不可枚举。
- [x] blocking、streaming、幂等与同会话多轮复用使用同一路径。
- [x] 聚焦测试、backend package、迁移验证和 `git diff --check` 通过。
- [x] 变更提交并合并本地 `main`；`cici.localhost` 从包含该 commit 的最新本地 `main` 构建并完成技术运行门禁。
- [x] 远程 `main` 同步并冻结不可变 UAT 候选；完整备份后仅替换 backend/frontend，完成技术验收和回滚回读。
- [ ] 使用真实 OpenAPI Key 和真实视觉模型完成固定图片对话 HUMAN 验收；当前没有可读取的明文 Key，未擅自新建凭据。

## 当前证据

- 后端聚焦测试、package、前端 60 文件/332 项与 production build 通过。
- 空 PostgreSQL 16.9 成功迁移至 V128；multipart 与 URL 导入两条控制器附件链路通过。
- 完整 OpenAPI 集成类为 16/17，唯一失败是任务外既有 placeholder 模型路由断言；未误报全类通过。
- 功能提交 `3b34e3198938` 已进入本地 `main`；并行主线推进后，backend/frontend 从包含该提交的最新代码主线 `40a27a2b2983` 构建并运行 `2.8.68-dev.40a27a2`。
- backend/frontend healthy/restart=0，backend health=`UP`，Flyway V128 success，首页 200，匿名 `/openapi/v1/parameters` 为 JSON 401；近 5 分钟 severe=0，四个状态服务 ID 未变化。
- 本地与远程 `main` 在冻结时均为 `a5bbb1140864`；annotated tag `2.8.68-beta.3^{}` 指向同一提交，backend/frontend digest 为 `sha256:b81e8c5aeb96d4710fa15349d5bceeb1c88aef2b7a2e65bdab3c49acdbc2c4d9` / `sha256:2412ab89ad5c6717db544aca05d2c8fe7483b3292c25c5cb792c32ed4846ae26`，未更新 `latest`。
- UAT 完整备份 `/data/apps/agentcici/backups/20260831T045841Z-before-2.8.68-beta.3` 共 10 项、317,119,013 bytes，数据库 catalog、归档、旧镜像、权限与清单通过；应用回滚目标为 `2.8.68-beta.2`。
- UAT 只替换 backend/frontend；V127/V128 与 repeatable migration success，六容器 healthy/restart=0，首页 200、HTTP 301、匿名 OpenAPI JSON 401、依赖健康和 30 秒错误日志稳定窗口通过。四个状态服务 ID 不变；有效应用 MCP 绑定数为 0，本候选未启用跨项目契约。
- 真实 OpenAPI Key 下的真实模型图片识别尚未执行：浏览器已有登录态，但新建临时 Key 属凭据写操作，未在无明确授权时执行；因此任务进入 `review`，不把技术门禁替代 HUMAN 验收。

## 回滚

- 关闭 `openapi.attachment-runtime-v2` 后携带附件的请求返回明确暂不可用错误。
- 应用可回滚到前一 commit；新迁移字段保持向后兼容，已存文件不在回滚时删除。
