---
kind: feature-spec
feature_id: FEAT-163
title: 机器主体授权范围治理
status: verified
owner_role: integration-agent
task_ids: TASK-274
related_decisions: "AgentCiCi 是 SERVICE Principal scope 权威；Semattice 只消费受信 OACT"
related_issues: none
updated_at: 2026-08-05T16:02:00Z
updated_by: codex
---

# FEAT-163 - 机器主体授权范围治理

## 背景

现有机器主体管理页可以查看 scope，但既有 API 只支持创建、生命周期、负责人、Client ID 和密钥操作，不能受治理地调整 scope。直接更新 `service_principal_scope` 会绕过组织管理员授权、allowlist 校验和审计。

## 目标

- 为 ORG_ADMIN 提供当前企业机器主体 scope 的完整替换接口。
- 将 HUMAN Semattice 默认 scope 与 SERVICE 最大许可 scope 分离，避免为了给单一 SERVICE 增权而扩大所有 HUMAN 权限。
- 在管理页展示服务最大许可范围，通过明确确认后提交完整目标集合。
- 本次上线后只给 `dev-autopilot-product-manager` 新增 `runtime.record.delete`。

## API 与数据设计

- `PUT /admin/service-principals/{principalId}/scopes`
- 浏览器经 `/api/admin/service-principals/{principalId}/scopes` 代理调用。
- 请求：`{"scopes":["..."]}`，使用完整替换语义；空集合、重复归一化后的空值、allowlist 外 scope 均拒绝。
- 服务先验证当前企业有效 HUMAN 管理成员和目标受治理 SERVICE；已撤销主体拒绝。
- 在同一事务中删除旧 scope、写入按字典序归一化的新集合，并记录 `service_principal.scopes_updated` 审计；审计只保存 scope 差异，不含令牌或密钥。
- 返回最新主体视图；Client ID、密钥、负责人和生命周期保持不变。

## 配置

- 保留 `app.auth.official-access.semattice-scopes` 作为 HUMAN 控制台默认 scope。
- 新增 `app.auth.official-access.semattice-service-scopes` 作为 SERVICE 最大许可清单；缺省时兼容回退到 HUMAN 配置。
- SERVICE OACT 只校验持久授权是否属于 SERVICE allowlist；HUMAN OACT 不自动取得新增 SERVICE-only scope。

## 管理界面

- 有效或暂停主体显示“调整授权范围”；已撤销主体不可调整。
- 对话框列出服务最大许可范围并预选当前集合。
- 提交前展示主体名称和完整目标集合，必须点击“确认执行”。
- 不展示、读取、轮换或持久化 Client Secret。

## 验收标准

- 后端测试覆盖最小增量、allowlist 外拒绝、跨企业拒绝、已撤销拒绝、审计不含秘密。
- OACT 测试证明 SERVICE allowlist 可包含 HUMAN 默认集合外的 `runtime.record.delete`，而 HUMAN token 不包含该 scope。
- 前端测试覆盖 scope 归一化、差异判断和已撤销门禁；生产构建通过。
- 生产仅目标产品经理新增 `runtime.record.delete`；重新签发 OACT 后回读一致，其他机器主体 scope 不变。

## 回滚

- 使用同一接口提交发布前 scope 集合。
- 生产环境移除 SERVICE allowlist 中新增项并重建后端；无需修改 HUMAN scope、Keycloak client 或 Client Secret。

## 验证结果

- `2.8.57 / 750fb71ab47d` 已发布生产，目标产品经理 SERVICE 已通过 ORG_ADMIN 明确确认入口取得新增删除 scope。
- 其他三名开发者 SERVICE 回读保持原 scope；生产 HUMAN 默认配置明确不含 `runtime.record.delete`。
- 新签发 OACT 绑定正确 company、tenant、SERVICE Principal 和 owner 证据；Semattice 对删除能力的空输入探测进入参数校验并生成审计，证明不是 scope 拒绝且没有业务写入。
