---
kind: task-status
task_id: TASK-301
status: review
updated_at: 2026-08-13T10:55:00Z
updated_by: codex
owner_role: fullstack-agent
spec_path: docs/specs/FEAT-182-kb-pdf-upload-admission.md
---

# TASK-301 - 知识库 PDF 上传门禁修复

## 范围

- 修复管理端无条件阻断 PDF 上传。
- 统一 PDF 能力文案并补齐前端定向测试。
- 验证后端文本型 PDF 索引回归。
- 提交到 AgentCiCi 本地 `main`，再更新 `cc-local-stack` 的本地开发环境。

## 完成条件

- 前后端定向测试与前端生产构建通过。
- 本地 `main` 包含任务提交。
- `cici.localhost` 真实文本型 PDF 上传成功，容器健康和运行指纹可追溯。

## 当前证据

- 前端定向测试 3/3 与 production build 通过。
- 后端 `mvn -q -DskipTests package` 通过。
- 共享测试库 V81 checksum 漂移未 repair；隔离 PostgreSQL V1→V114 成功，但现有用例缺少平台可用模型前置，未进入 PDF 断言。
- 待提交本地 `main` 并完成 `cici.localhost` 真实上传验收。
