---
kind: task-status
task_id: TASK-338
feature_id: FEAT-188
status: review
updated_at: 2026-08-28T10:54:15Z
updated_by: codex
owner_role: release-agent
spec_path: docs/specs/FEAT-188-conversation-image-paste-attachments.md
---

# TASK-338 - 图片识别修复 UAT 2.8.67-beta.1 技术发布

## 范围

- 从冻结提交 `2970bea75208` 构建并发布 UAT `2.8.67-beta.1`。
- 备份并验证 Compose、受管环境、旧镜像、PostgreSQL、KB 与 Qdrant。
- 只重建 backend/frontend，保留四个状态服务及其数据。
- 回读 tag、digest、运行版本、迁移、健康、匿名鉴权与稳定日志。

## 完成证据

- 远程 `main`、annotated tag 和运行 commit 均包含图片修复并指向冻结候选。
- backend/frontend 不可变 linux/amd64 digest、完整备份和 `2.8.66-beta.3` 回滚点均已记录。
- 六容器 healthy/restart=0，四个状态服务 ID 不变；health UP、V125、Nginx、公开 smoke、JSON 401 和稳定窗口通过。
- 本候选未新增、启用或切换跨项目契约；生产未修改。

## HUMAN 验收

- 待已登录 UAT 用户粘贴图片并确认实际识别结果；技术健康和匿名边界不替代该业务接受。
