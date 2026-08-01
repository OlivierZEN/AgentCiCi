---
kind: task-status
task_id: TASK-262
status: done
updated_at: 2026-08-01T15:46:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: backend-agent
assignment_path: .claw/assignments/TASK-262.yaml
spec_path: docs/specs/FEAT-154-dev-autopilot-governed-service-identities.md
---

# TASK-262 - DEV Autopilot 受治理机器身份生命周期

## Current State

- AgentCiCi `2.8.38` 已在生产运行，机器主体查询、密钥轮换、暂停、恢复、永久撤销、负责人移交和脱敏审计均已实现。
- 产品总监 HUMAN 精确绑定全局用户 `18611892001`；产品经理和开发者 SERVICE 的 PRIMARY owner 均为该产品总监。
- 开发者暂停会阻断 Keycloak/OACT/CLI，恢复后重新可用；密钥轮换后旧 secret 失败、新 secret 成功，受管凭据文件保持 `0600`。
- `deploy/nginx.cici.conf` 与 `deploy/nginx.cici.ssl.conf` 均固化独立应用 `/devautopilot/` 入口；生产恢复 SSL Compose override 后 80/443 均监听，公网健康检查通过。
- Blocked: none

## Next Action

- 进入常规监控；永久撤销只用于离职/失信机器主体，不对当前生产开发者执行破坏性演练。

## Evidence

- 产品总监 account/member：`25deaf62-73c7-40cc-a107-99c56cff2ec9` / `0cf12a0a-a01d-441d-9fad-d7bffe0b3f2e`，生产回读 mobile 为 `18611892001`、角色 OWNER、状态 ACTIVE。
- 产品经理 principal/client：`742daca1-ce58-49cc-9e53-530444ba1c47` / `dev-autopilot-product-manager`。
- 开发者 principal/client：`9aab6f76-5f2f-482b-84a1-871d8a0f7030` / `dev-autopilot-developer`。
- 生命周期演练审批：`f1591286-71bb-49ed-b874-80a7c7640fa9`；AgentCiCi 审计各记录一次 `suspended`、`activated`、`credential_rotated`。
- 生产入口：`https://x.agentcici.com/devautopilot/api/health` 返回 integrated/ok；TLS 恢复备份 `/opt/cici/backups/20260801T154249Z-before-restore-ssl-edge`，版本化代理配置备份 `/opt/cici/backups/20260801T154516Z-before-versioned-devautopilot-edge`。
