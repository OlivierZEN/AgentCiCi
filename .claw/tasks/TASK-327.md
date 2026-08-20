---
kind: task-status
task_id: TASK-327
feature_id: FEAT-199
status: in_progress
priority: critical
owner_role: fullstack-agent
claimed_by: codex
updated_at: 2026-08-20T16:42:00Z
updated_by: codex
---

# TASK-327 - 微信客服企业微信手机端监控与强制接管

## 范围

- 实现企业微信权威会话状态、消息来源分流、人工接管状态机和 AI 发送 fence。
- 实现接待人员 OAuth、移动服务端会话、待接管列表、强制接管可信回执和企业微信原生客服会话跳转。
- 扩展微信客服账号配置、Flyway 数据模型、后端与前端定向测试。
- 从本地 main 构建并更新 `cc-local-stack` AgentCiCi backend/frontend，完成技术门禁。

## 完成条件

- FEAT-199 的确定性测试、安全负例、迁移、backend package、frontend 全量测试和 build 通过。
- 实现提交独立进入 AgentCiCi 本地 main，不夹带其他任务或仓库变更。
- 本地 backend/frontend 同一 main commit 构建，版本、镜像、页面制品、容器健康和 restart count 回读一致。
- 没有真实企业微信账号时明确保留真实手机 OAuth、接管、原生跳转和客户消息业务验收为 HUMAN pending，不以 mock 替代。

## 下一步

- 将已通过代码、22 项聚焦回归、前端 297 项和全新 V123/JPA 启动门禁的实现提交并合并到本地 `main`；随后只从该 main 构建 backend/frontend `:local` 镜像，更新 `cc-local-stack` 并回读运行指纹。真实企业微信渠道验收继续保留为 HUMAN pending。
