---
kind: task-status
task_id: TASK-336
feature_id: FEAT-188
status: review
updated_at: 2026-08-28T09:27:08Z
updated_by: codex
owner_role: backend-agent
spec_path: docs/specs/FEAT-188-conversation-image-paste-attachments.md
---

# TASK-336 - 普通租户图片识别视觉能力误判修复

## 范围

- 修复聊天视觉门禁错误使用业务组织 ID 查询平台级模型能力目录的问题。
- 保留受信能力确认与 `VISION_MODEL_REQUIRED` 失败关闭，不按模型名猜测视觉能力。
- 增加普通租户读取平台治理能力事实源的回归测试，并更新本地 `main` 开发环境。

## 完成条件

- [x] 回归测试先复现普通租户被误判为无 `vision` 能力。
- [x] 视觉能力检查与运行模型路由使用同一平台治理组织事实源。
- [x] 模型路由/附件聚焦测试与 backend package 通过。
- [x] 变更提交进入本地 `main`，backend/frontend 从同一明确提交构建。
- [x] `https://cici.localhost/` 图片会话不再返回错误 409，并回读模型、消息与容器指纹。

## 当前验证

- 本地运行数据只读回读：平台 `chat=aliyun-bailian/qwen3.7-plus`，该模型已由运营确认 `text/tool/reasoning/web-extractor/web-search/code-interpreter/vision`；业务组织没有自己的模型能力目录。
- 新回归测试修复前断言 `false`、修复后通过；一次性 PostgreSQL 16.9 测试库完成 V1-V125 与 repeatable migration，测试后已删除。
- `ChatAttachmentServiceTest,ChatOrchestratorServiceModelIdentityTest` 共 57 项通过；`mvn -q -DskipTests package` 与 `git diff --check` 通过。
- 完整 `ModelProviderServiceIntegrationTest` 受该测试类既有共享平台配置顺序污染影响，6 项中 1 项失败；失败是前序测试残留 `platform-chat-model`，与本次视觉能力断言无关，未把整类宣称为通过。
- 实现 `036c12a0d006` 已进入本地 `main`；backend/frontend 镜像和运行环境同为 `2.8.67-dev.036c12a`，版本 API 与前端资源指纹一致，两容器 healthy/restart=0。
- 已登录普通租户把用户原截图以剪贴板重新粘贴给思思（`qwen3.7-plus`）：两轮分别返回 `409` 和 `VISION_MODEL_REQUIRED`，证明模型实际读取图片且不再被能力门禁错误拒绝；浏览器 warning/error=0，后端无新增冲突或 severe 日志。
- PostgreSQL、Redis、RabbitMQ、Qdrant、Keycloak、Nginx、Semattice、DevAutopilot 未替换且 restart=0；远程、UAT、生产未修改，等待用户目视确认。
