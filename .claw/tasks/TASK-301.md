---
kind: task-status
task_id: TASK-301
status: in_progress
updated_at: 2026-08-13T11:40:00Z
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
- 补齐选择文件后的持久上传状态、异常反馈和异步索引最终状态，避免 Promise 异常或短时 toast 导致“无任何反馈”。
- 对上传网关 `HTTP 413` 返回明确处置建议，并将上传失败反馈强化为深红高对比错误态。
- 与 `cc-local-stack` 对齐边缘请求体限制，确保页面声明的 25 MB 文件能到达 AgentCiCi 业务门禁。

## 完成条件

- 前后端定向测试与前端生产构建通过。
- 本地 `main` 包含任务提交。
- `cici.localhost` 真实文本型 PDF 上传成功，容器健康和运行指纹可追溯。
- 大于 1 MB 且不超过 25 MB 的真实 PDF 不再被本地边缘 Nginx 返回 413。

## 已有证据与复开原因

- 前端定向测试 3/3 与 production build 通过。
- 后端 `mvn -q -DskipTests package` 通过。
- 共享测试库 V81 checksum 漂移未 repair；隔离 PostgreSQL V1→V114 成功，但现有用例缺少平台可用模型前置，未进入 PDF 断言。
- 修复提交 `cabaebc7c641` 已进入 AgentCiCi 本地 `main`；backend/frontend 从该提交构建为 `2.8.61-dev.cabaebc`。
- `cici.localhost` 运行资源回读 backend/frontend 均 `healthy`、`restart=0`，后端 `/system/version` 回读相同版本与提交，前端资源文件名包含相同版本。
- 受控文本型 PDF 通过正式上传与发布 API，最终 `PUBLISHED`、1 个切片、无解析错误；测试文档随后通过正式删除 API 清理，知识库恢复 0 个有效文档和 0 个有效切片。
- 自动化浏览器没有已登录 HUMAN 会话，页面访问按设计进入 SSO；未读取、猜测或重置用户凭据，受权页面点击级复核不作为本任务完成的虚假证据。
- 用户在已登录 Chrome 实测 PDF 后仍无反馈且无新 PDF 记录；Nginx 访问日志也没有对应 PDF 上传请求。现有 UI 仅有 3 秒 toast，且上传/发布的 `fetch`、JSON 解析和异步索引没有完整异常与最终状态反馈，因此任务复开。
- 复开修复提交 `d3cf1b7` 已进入本地 `main`：页面持久显示文件检查、上传、发布、索引及最终成功/失败，网络和非 JSON 响应返回可操作中文错误，处理中禁止重复选择文件。
- 本地前端已从该提交重建为 `2.8.61-dev.d3cf1b7`。已登录 Chrome 真实选择文本型 PDF 后依次回读“处理中”“正在解析并建立索引”“文档上传成功”，最终 `PUBLISHED`、1 个切片、状态“可用”；测试文档随后通过正式删除 API 清理。
- 用户随后上传约 2.1 MB PDF 得到 `HTTP 413`；本地边缘 Nginx 日志回读请求体 `2199033` 字节并显示 `client intended to send too large body`，证明请求被 `cc-local-stack` 默认约 1 MB 上限拦截，未进入 AgentCiCi。
