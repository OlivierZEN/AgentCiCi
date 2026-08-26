---
kind: task-status
task_id: TASK-328
feature_id: FEAT-200
status: done
priority: high
owner_role: frontend-agent
claimed_by: codex
updated_at: 2026-08-26T04:18:24Z
updated_by: codex
---

# TASK-328 - 运维中心部署安装在线指南

## 范围

- 新增“运维中心 → 部署安装”导航与独立路由。
- 编写 AgentCiCi、Semattice、Keycloak 的在线部署安装文档。
- 提供稳定、脱敏、无需 JavaScript 的 Agent Markdown 版本。
- 补齐导航、章节一致性、安全扫描、全量前端测试、构建与本地环境验证。

## 完成条件

- FEAT-200 的导航、页面、Markdown 和自动化测试全部实现。
- 不在前端源码或 Markdown 中写入真实环境地址、IP、凭据或私钥路径。
- 实现提交进入 AgentCiCi 本地 `main`，并从该 commit 构建本地前端。
- `cici.localhost` 路由、Markdown、容器、版本与桌面视觉证据通过。

## 完成证据

- 功能已随 UAT `2.8.66-beta.2 / 525f0f610926` 发布；两项不可变镜像、完整备份、V123、六容器 healthy/restart=0、页面/Markdown/匿名 401 和稳定日志门禁通过。
- 用户已确认 UAT HUMAN 登录态导航、章节锚点和 Markdown 新窗口验收通过；冻结候选随后以 `2.8.66 / e805c0ef7142` 晋级生产，在线路由、Markdown MIME/nosniff、Nginx 和稳定窗口门禁通过。
