---
kind: feature-spec
feature_id: FEAT-134
title: AgentCiCi and Semattice controlled provisioning
status: in_implementation
updated_at: 2026-07-23T08:15:00Z
updated_by: MANAGER-001
owner_role: integration-agent
---

# FEAT-134 - AgentCiCi 与 Semattice 受控开户绑定

## 目标

AgentCiCi 是组织身份和 Semattice 订阅绑定的唯一事实源。任何受信系统可向 Semattice 请求开户，但 `company_id` 必须等于已存在的 AgentCiCi `org_id`；Semattice 不得自行接受任意公司标识或重复开户。

## 生命周期

`eligible -> reserving -> provisioned`，失败进入 `failed` 并可用同一幂等键安全重试。

1. Semattice 接收已认证调用方的开户请求：`company_id`、显示名、服务档位和幂等键。
2. Semattice 调用 AgentCiCi 原子 `reserve`；AgentCiCi 仅接受存在且 `ACTIVE` 的组织，并以唯一约束创建或重放 reservation。
3. Semattice 创建本地 tenant projection，`company_id` 和本地租户身份均唯一。
4. Semattice 调用 AgentCiCi `complete`，回写 tenant ID、结果和操作 ID；失败同样回写，但不泄漏内部错误。

## 接口与认证

### AgentCiCi inbound（仅 Semattice）

- `POST /internal/semattice/provisioning/reservations`
- `POST /internal/semattice/provisioning/reservations/{reservationId}/complete`

### Semattice inbound（受信调用方）

- `POST /internal/v1/company-provisionings`

调用方可为 AgentCiCi 或其他受信外部系统。授权由部署配置的 `service_id -> HMAC key` 映射决定，AgentCiCi 不是唯一发起方。

所有服务调用采用 HMAC-SHA-256，签名覆盖 `service_id`、HTTP method、path、timestamp、nonce 与 SHA-256 body hash；接收端限制五分钟时钟窗口、恒时比较并拒绝 nonce 重放。密钥只能来自环境变量或密钥管理系统，绝不写入仓库、数据库、审计明细、接口响应或日志。

生产环境使用两个独立的单向密钥，避免任一方向的密钥泄漏扩大为双向伪造：

- `APP_NATIVE_AGENTCICI_INTERNAL_HMAC_KEY` = Semattice `AI_NATIVE_AGENTCICI_HMAC_KEY`（`semattice -> AgentCiCi`）。
- `APP_SEMATTICE_INTERNAL_HMAC_KEY` = Semattice `AI_NATIVE_PROVISIONING_CALLER_KEYS` 中的 `agentcici=<key>`（`AgentCiCi -> Semattice`）。
- AgentCiCi 还设置 `APP_SEMATTICE_BASE_URL=https://semattice.agentcici.com`；Semattice 设置 `AI_NATIVE_AGENTCICI_BASE_URL=https://onechat.agentcici.com`。

## 安全与可靠性

- AgentCiCi reservation 表按 `org_id` 和调用幂等键约束；Semattice `company_id` 唯一约束和自身 idempotency 共同消除并发重复开户。
- 不存在、暂停、待清理或已成功绑定的组织均拒绝；相同键不同输入、无效服务、过期 timestamp、nonce 重放与 body 篡改均拒绝。
- 网络或对端 5xx 不得将 reservation 标为成功；completion 重试必须幂等。
- 审计只记录 service ID、company ID、reservation/operation/request ID、结果码与时间。

## 验收

- AgentCiCi、其他允许调用方均可触发；未登记调用方不能触发。
- 并发请求不会创建重复 binding 或 tenant；失败恢复、完成回调重试和跨端状态一致均有测试。
- 两仓库定向测试、构建、迁移、发布 dry-run 和线上 smoke 均通过后才分别发布。
