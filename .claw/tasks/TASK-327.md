---
kind: task-status
task_id: TASK-327
feature_id: FEAT-199
status: review
priority: critical
owner_role: fullstack-agent
claimed_by: codex
updated_at: 2026-08-21T06:44:54Z
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

- 功能已包含在 UAT `2.8.66-beta.2 / 525f0f610926`：V123、移动页 200、带合法 pageUrl 的匿名 context 401、不存在入口 UUID 400、运行版本/镜像/容器健康与 restart=0 通过。
- 在获授权的真实微信客服账号配置自建应用 AgentId/Secret、可信域名和正式接待人员后，由 HUMAN 在 UAT 企业微信手机端完成 OAuth、客户消息、状态 3 接管、人工回复无 AI 双发和原生会话跳转验收。生产保持不变。
