---
kind: task-status
task_id: TASK-219
status: review
updated_at: 2026-07-23T14:06:52Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: frontend-agent
assignment_path: .claw/assignments/TASK-219.yaml
spec_path: docs/specs/FEAT-124-platform-operations-information-architecture.md
---

# TASK-219 - 运营管理端信息架构与独立主题重构

## Scope

- 按 FEAT-124 重构 `/platform/*` 的二级信息架构、页面职责、独立详情/编辑流和平台独立主题设置。
- 保留当前 API、数据模型、权限和高风险确认，不新增移动端或后端业务改动。

## Current State

- 用户已明确租户应用中心属于 AgentCiCi 运营端 `/platform/*`，不是 `/admin/*` 管理端；并确认以高保真桌面原型作为本页结构与样式合同。
- `/platform/tenants/:orgId` 已收敛为只含租户身份与应用中心卡片的租户应用页。AgentCiCi 卡使用真实租户成员、状态与开通方式；原先保留、导出、预演、销毁等 AgentCiCi 生命周期区块已移除，不能再被误解为所有应用共享能力。Semattice 卡准确呈现业务数据与语义运行底座定位、`company_id`、受控身份校验和 API/MCP/CLI 接入面，不展示知识库或文档等错误信息。
- AgentCiCi 卡片整体可点击并进入 `/platform/tenants/:orgId/applications/agentcici`；原有生命周期页及其保留、导出、预演、销毁能力已恢复到这个应用级路由，且可返回租户应用页。
- Semattice 开通按钮调用 FEAT-134 的受控运营端路由，并覆盖开通中、成功、失败和重复点击禁用状态。当前页面尚没有独立的应用状态读取投影，刷新后的持久状态回读需与 FEAT-134 的绑定记录查询一并补齐，不能将本次本地成功态误作生产持久事实。

## Next Steps

1. 在集成 FEAT-134 的持久绑定读取契约后，以真实应用状态替代页面初始的 Semattice“未开通”默认态。
2. 评审并集成本分支的前端、规格和状态变更；不修改 TASK-218 已收敛的模型页。

## 本轮验证（2026-07-23）

- 身份与范围：MANAGER-001 的 SSH challenge-response、TASK-219 分支与页面、样式、规格、状态代表路径均由 `dev-login.py` 验证为 `allowed`。
- 前端：`npm run build` 通过，TypeScript 无错误；仅保留既有 Vite 大 chunk 警告。`git diff --check` 通过。
- 浏览器：本机临时 fixture 仅提供脱敏租户详情与受控开户成功响应，不访问生产。Playwright 在 `1920 × 1080`、`crm-blue` 主题下确认页面只保留租户身份与两张应用卡片，正文不存在“保留策略”“组织导出”或“预演与销毁记录”，无外层横向溢出。点击 Semattice 开通后成功提示、运行中状态、已开通汇总 1→2 及已开通禁用态均正确。
- 路由：Playwright 点击 AgentCiCi 卡片后进入 `/platform/tenants/org5nszpgj99jaysxv6y/applications/agentcici`，显示“AgentCiCi 应用生命周期”及原有治理区块，无外层横向溢出。
