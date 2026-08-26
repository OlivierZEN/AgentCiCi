---
kind: task-status
task_id: TASK-330
status: done
priority: critical
owner_role: release-agent
claimed_by: codex
updated_at: 2026-08-26T04:18:24Z
updated_by: codex
---

# TASK-330 - AgentCiCi 2.8.66 生产晋级

## 范围

- 将 UAT `2.8.66-beta.3 / e805c0ef7142` 按同一冻结提交晋级为生产 `2.8.66`。
- 生产发布只允许替换 AgentCiCi backend/frontend，保留 PostgreSQL、Redis、RabbitMQ、Qdrant、Semattice、DevAutopilot、Keycloak 和 Nginx 的独立发布边界。
- 冻结正式 tag、不可变镜像 digest、完整备份、回滚点、迁移、健康、匿名授权边界和稳定窗口证据。

## 当前证据

- 用户确认 TASK-326/327/328 与 TASK-329 UAT HUMAN 验收通过，并明确授权先独立发布 Semattice `1.0.7`，再发布 AgentCiCi `2.8.66`。
- Semattice 先从冻结 `1.0.7-beta.5 / 54f2ab93558f` 晋级正式 `1.0.7`；AgentCiCi 生产 SERVICE 身份签名探测返回 7 对象、87 字段、state=applied，提供方门禁闭合后才开始消费方发布。
- AgentCiCi 正式 tag `2.8.66^{}`、UAT tag `2.8.66-beta.3^{}` 和运行 commit 均为 `e805c0ef7142b7446aef019c786107528cde34a1`。backend/frontend ACR index digest 为 `sha256:d892ff3b60c39bc690a48c71176005f6c2a12299288e16fd8606260375652557` / `sha256:289434e93eab541bdb96cb0a383443cb6280a67e72af1a9a17d206a0b6fcdab4`，均含 linux/amd64 manifest，未更新 `latest`。
- 完整回滚点 `/opt/cici/backups/20260826T041149Z-before-2.8.66` 含受管配置、PostgreSQL custom dump、KB/Qdrant 归档、Qdrant 原生 snapshot、旧应用镜像、容器基线、回滚说明和 SHA-256 清单；13 项文件全部非空且 `0600`，格式与清单校验通过。应用回滚目标为 `2.8.65`；数据恢复仍需单独批准。
- 只 pull/force-recreate backend/frontend；database、Redis、RabbitMQ、Qdrant ID 保持不变。六容器 healthy/restart=0，backend health UP，运行 version/commit/image label/digest 一致，Flyway V123，frontend Nginx 有效。
- 公开首页、`/app`、`/admin/skills`、DevAutopilot integrated health、Semattice `1.0.7` 与 Keycloak discovery 通过；匿名 `/auth/me`、`/skills`、导出 POST 均为 JSON 401。生产 bundle 包含导出进行中、非 JSON 响应和任务未就绪处理；100 秒稳定窗口 backend severe、frontend 5xx/upstream 均为 0。
- 知识库计数保持 9/35/661，Qdrant 保持 549 points；四个状态服务未重建。生产发布技术门禁完成。
