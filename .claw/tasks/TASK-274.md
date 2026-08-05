---
kind: task-status
task_id: TASK-274
status: in_progress
updated_at: 2026-08-05T15:55:00Z
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

实现接口、配置和管理页并运行验证。
