---
kind: task-status
task_id: TASK-274
status: done
updated_at: 2026-08-05T16:02:00Z
updated_by: codex
assignee: codex
owner_role: integration-agent
assignment_path: n/a
spec_path: docs/specs/FEAT-163-service-principal-scope-governance.md
---

# TASK-274 - 为产品经理 SERVICE 补齐删除权限

## Scope

- 实现受 ORG_ADMIN 保护的机器主体 scope 完整替换 API 与审计。
- 分离 HUMAN 默认 scope 和 SERVICE 最大许可清单。
- 增加管理页明确确认交互。
- 发布后仅为企业 `org5nszpgj99jaysxv6y` 的 `dev-autopilot-product-manager` 新增 `runtime.record.delete`。

## Done When

- 定向后端、前端测试和构建通过。
- 生产发布、备份、健康和匿名负例通过。
- 目标 SERVICE 新 OACT 包含删除 scope，其他主体未扩权。
- 不读取或轮换任何机器密钥，不执行业务记录删除。

## Next Action

已完成；后续授权变更继续使用同一 ORG_ADMIN 管理入口和完整替换语义，不直接更新数据库。

## Evidence

- 实现提交和 release：`750fb71ab47d / 2.8.57`；backend/frontend ACR index digest 为 `sha256:4a3c552bc498fa9e4bef823b3e2c071d4b1e34a05b9e2a2ec590d1a2aa46c13b` / `sha256:1ad603f8e395c340b38f61616242be4076611c40fbb5309d31cc76ff171a2d02`。
- 生产备份：`/opt/cici/backups/20260805-235439-before-2.8.57-task274-scope-governance`；仅重建 backend/frontend，四个状态服务保持运行。
- 大乔最终 scope 为 `identity.principal.sync`、`runtime.record.create`、`runtime.record.delete`、`runtime.record.read`、`runtime.record.update`；悟空、后羿、哪吒均未取得删除 scope。
- 平台审计 `id=77 / service_principal.scopes_updated` 记录了目标 Principal 的旧/新集合；Client ID、Client Secret、负责人和生命周期均未改变。
- 新 OACT 与 Semattice 安全探测通过；未提供业务记录 ID，未发生删除。
