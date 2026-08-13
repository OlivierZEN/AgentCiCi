---
kind: feature-spec
feature_id: FEAT-182
title: 知识库 PDF 上传门禁修复
status: implemented
owner_role: fullstack-agent
task_ids: TASK-301
related_decisions: none
related_issues: none
updated_at: 2026-08-13T11:20:00Z
updated_by: codex
---

# FEAT-182 - 知识库 PDF 上传门禁修复

## 背景与目标

用户在本地知识库选择 PDF 后，页面把上传策略中的英文 `pdfPolicy` 说明作为错误提示显示并直接终止，实际请求没有到达 `/kb/documents/upload`。后端已经支持文本型 PDF，并对加密、扫描型、损坏或无文本 PDF 返回解析失败。

本次让 PDF 与 TXT、Markdown、CSV、JSON、DOCX 使用同一上传门禁和自动发布流程，同时统一清晰的中文能力说明。

## 范围

- 前端不再按 `.pdf` 扩展名无条件阻断上传。
- 文件大小与后端声明的允许扩展名仍在客户端预检。
- 后端上传策略使用中文说明文本型 PDF 能力与不支持的类型。
- 页面上传说明和运行状态使用同一策略事实，不再显示“PDF 不进入索引流水线”。
- 加密、扫描型、损坏或无文本 PDF 继续由后端失败关闭，不引入 OCR。

## 验收标准

1. `pdf` 在允许扩展名中时，客户端校验放行并发起上传。
2. 超限或不支持扩展名仍被客户端拒绝并返回明确中文原因。
3. 文本型 PDF 上传后自动发布并进入文档列表；后端既有 PDF 索引集成测试保持通过。
4. 页面不再出现英文策略 toast 或“PDF 不进入索引流水线”的矛盾文案。
5. 本地开发环境必须从 AgentCiCi 本地 `main` 的提交构建 `:local` 镜像，回读页面、容器健康、重启次数与版本/提交指纹。
6. 选择文件后立即显示文件名和当前阶段；上传、发布、索引成功或失败都必须在页面内持续可见，网络异常和非 JSON 响应不得静默。
7. 索引异步执行时持续刷新目标文档，直到 `PUBLISHED` 或 `FAILED`；处理中禁止重复选择文件。

## 回滚

- 回滚本任务提交并从回滚后的本地 `main` 重建 AgentCiCi backend/frontend。
- 不涉及数据库迁移；已上传文档不因回滚自动删除。
