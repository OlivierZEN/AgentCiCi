---
kind: task-status
task_id: TASK-340
feature_id: FEAT-188
status: review
updated_at: 2026-08-28T13:12:49Z
updated_by: codex
owner_role: release-agent
spec_path: docs/specs/FEAT-188-conversation-image-paste-attachments.md
---

# TASK-340 - 图片识别修复生产 2.8.67 晋级

## 范围

- 在用户确认 UAT HUMAN 验收通过后，将冻结 `2.8.67-beta.1 / 2970bea75208` 原样晋级生产 `2.8.67`。
- 构建并发布 linux/amd64 不可变正式制品，保留 `latest`。
- 完整备份并验证 Compose、受管环境、旧镜像、PostgreSQL、KB 与 Qdrant。
- 只重建 backend/frontend，回读 tag、digest、运行版本、迁移、健康、匿名鉴权、数据守恒与稳定日志。

## 完成证据

- UAT tag、正式 tag 与生产运行 commit 均为 `2970bea75208`，远程 `main` 包含冻结提交。
- backend/frontend digest 为 `sha256:2b6be2564f0eef09f064e4ce345d585cc4bc1f3c00408d0f358ab8f82bfac615` / `sha256:6ec9501ec3cdfdf1118ab1ec9f647223ecfb843440603d83e054577c539dd6a4`，未更新 `latest`。
- 完整备份 `/opt/cici/backups/20260828T130242Z-before-2.8.67` 共 14 项、351,001,019 bytes 且校验通过；应用回滚目标为 `2.8.66`。
- 六容器 healthy/restart=0，四状态服务 ID 不变；health UP、V125、Nginx、公开 smoke、JSON 401、数据计数不变和累计 100 秒稳定窗口通过。

## HUMAN 验收

- 用户已明确确认 UAT 图片识别验收通过。
- 生产真实图片上传与模型识别仍待已登录 HUMAN 执行；技术健康与匿名边界不替代生产业务接受。
