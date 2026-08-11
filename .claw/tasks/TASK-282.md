---
kind: task-status
task_id: TASK-282
status: done
updated_at: 2026-08-11T07:44:58Z
updated_by: codex
assignee: codex
owner_role: integration-agent
spec_path: docs/specs/FEAT-170-local-container-runtime.md
integration_id: INT-012
---

# TASK-282 - AgentCiCi 本地全容器入口

## 范围

- 保持 backend/frontend 为可独立发布的正式应用镜像。
- 支持由 Secret file 注入本地 OIDC、数据库、应用 JWT/加密和 OACT 私钥。
- 支持信任统一本地 CA，并提供容器健康探针所需最小运行依赖。
- 不在本仓编排 Keycloak、数据库或其他产品。

## 完成条件

- backend/frontend 镜像可由干净工作树构建。
- backend 连接共享 PostgreSQL/Redis/Rabbit/Qdrant 和 Keycloak 后健康。
- frontend 经本仓 Nginx 配置代理 backend，edge HTTPS 健康和版本探针通过。
- Secret 不进入镜像层、Git 或日志。

## 完成证据

- backend/frontend镜像由`./stack up`重新构建成功；backend完成105项Flyway migration校验并连接共享PostgreSQL、Redis、RabbitMQ和Qdrant。
- backend `/actuator/health`、frontend健康、公开OACT JWKS及匿名`/me` 401/403边界通过。
- frontend Nginx配置`nginx -t`通过；外层HTTPS协议头可正确透传至backend。
- 本任务未执行完整AgentCiCi测试套件；该项和前端依赖漏洞处置仍是形成UAT候选前的产品质量门。
