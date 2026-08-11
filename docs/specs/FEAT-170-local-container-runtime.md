---
kind: feature-spec
feature_id: FEAT-170
title: AgentCiCi 本地全容器运行入口
status: verified
owner_role: integration-agent
task_ids: TASK-282
related_decisions: none
related_issues: none
updated_at: 2026-08-11T06:38:01Z
updated_by: codex
---

# FEAT-170 - AgentCiCi 本地全容器运行入口

## 背景与目标

使 AgentCiCi 的 backend/frontend 正式镜像可以被独立本地编排仓复用，并保持与 UAT/生产相同的 Java + Nginx 运行方式。

## 范围

### In Scope

- 运行时 Secret file 装载与本地 CA trust。
- backend 健康检查依赖；frontend 继续使用正式 Nginx 配置。
- 记录编排层所需稳定配置契约。

### Out Of Scope

- Keycloak、PostgreSQL 和其他产品的编排。
- ACR 推送、UAT/生产发布和旧本地数据自动迁移。

## 方案设计

- 入口脚本仅把 `/run/secrets` 中允许的文件读取为既有环境变量后 `exec java`，不改变应用配置模型。
- Java truststore 由编排仓生成并只读挂载，通过 `JAVA_TOOL_OPTIONS` 使用。
- 产品仓不引用兄弟仓文件；build context 和 mount 由编排仓负责。

## 验收标准

- 本地镜像构建成功，Secret 不进入 image history。
- `/actuator/health`、版本端点和前端入口经统一 Nginx 可访问。
- OIDC issuer 使用 `https://sso.localhost/realms/agentcici` 且证书验证开启。

## 风险与回滚

- 入口脚本失败时容器应失败关闭并指出缺失 Secret 名称但不输出内容。
- 可独立回退 Dockerfile/entrypoint，不影响数据库或其他产品仓。
