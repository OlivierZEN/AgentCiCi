---
kind: feature-spec
feature_id: FEAT-201
title: CloudCC 客户互动工作台 SSO company ID 契约兼容
status: verified
owner_role: fullstack-agent
task_ids: TASK-331
related_decisions: FEAT-135
related_issues: ISSUE-2026-08-27-cloudcc-sso-company-id-contract-drift
updated_at: 2026-08-27T03:25:54Z
updated_by: codex
---

# FEAT-201 - CloudCC 客户互动工作台 SSO company ID 契约兼容

## 背景与目标

生产 CloudCC 页面组件在获取 AgentCiCi 一次性 SSO ticket 时发送 `agentOrgId`。AgentCiCi 完成顶层 `org` 到 `company` 术语统一后，后端请求 DTO 改为必填 `agentCompanyId`，但 CloudCC pagecomponent、UMD fallback 和正式契约测试没有同步，导致请求在 DTO 校验阶段返回 HTTP 400。

目标是在不放宽 token、页面用户、成员绑定和 CloudCC accessToken 一致性门禁的前提下恢复工作台，并为已发布或缓存的旧组件提供受控兼容窗口。

## 范围

### In Scope

- Vue pagecomponent 和 UMD fallback 发送 `agentCompanyId`。
- 页面属性优先读取 `agentCompanyId`，兼容旧 `agentOrgId` 配置。
- 后端 `CloudccSsoTicketRequest.agentCompanyId` 使用 JSON alias 接受旧字段。
- 自动化覆盖两条前端运行路径的真实请求体和后端新旧字段反序列化。
- CloudCC 高代码 pagecomponent/customPage 发布回读与真实租户验证；后端 alias 进入 `main`，随下一次 AgentCiCi 标准候选发布。

### Out Of Scope

- 不修改 Keycloak iframe 安全响应头。
- 不改变 CloudCC runtime token、actor、当前页面用户与 AgentCiCi 成员必须一致的规则。
- 不读取、展示或复制 CloudCC 安全标记；截图已暴露的凭据由管理员独立轮换。
- 不修改 Semattice、DevAutopilot 或父工作区源码。

## 方案设计

规范请求体字段固定为 `agentCompanyId`。组件配置使用同名属性，但读取时允许旧 `agentOrgId` 作为回退；DOM fallback 同时接受 `agent-company-id`/`agentCompanyId` 和旧属性。后端通过 `@JsonAlias("agentOrgId")` 把旧 JSON 映射到同一个 `agentCompanyId` record component，服务层不引入双字段或模糊优先级。

该兼容只覆盖传输字段名，不允许请求覆盖可信租户，也不跳过 `CloudccSsoService` 的 runtime token、actor、成员和会话凭据校验。

## 接口与发布影响

- `POST /auth/cloudcc-sso/ticket` 的规范字段为 `agentCompanyId`；旧 `agentOrgId` 暂时兼容。
- 无数据库 migration。
- CloudCC pagecomponent 是独立高代码资源，发布后必须回读组件 ID、customPage 引用与真实运行状态。本次无需为恢复现有生产链路提前发布 AgentCiCi `2.8.67`。
- pagecomponent 同 ID 升版时，从修复前 Git 提交重新发布旧 Vue/UMD；未来 AgentCiCi 发布若需回滚，则恢复上一不可变镜像。Keycloak 与其他产品不参与回滚。

## 验收标准

- 前端源码和已发布 UMD 都只发送 `agentCompanyId`，不发送 `agentOrgId`。
- 旧字段和新字段都能被后端反序列化到同一 `agentCompanyId`。
- 生产故障同构请求不再得到 `agentCompanyId must not be blank`。
- `/ticket` 与 `/consume` 均成功后 iframe 保持在 AgentCiCi 工作台，不再跳转 Keycloak iframe。
- 真实 CloudCC 当前用户映射、CRM 连接状态和客户数据读取成功；该项必须使用受权 HUMAN 登录态验证。

## 风险与回滚

- CloudCC customPage 可能缓存旧组件；通过后端 alias 保证旧组件仍可用，再以新组件回读消除依赖。
- 若 runtime token 或页面用户与已配置账号不一致，系统继续返回 401，不能以字段兼容掩盖真实身份问题。
- 生产发布前必须有 AgentCiCi 完整备份和 pagecomponent/customPage 旧 ID；任一技术门禁失败只回滚对应变更面。

## 实现进展

- 已完成生产只读根因、运行健康和账号绑定存在性核对。
- 已完成规范字段、旧属性/旧请求兼容和前后端契约测试；自动化、构建、pagecomponent dry-run 与静态门禁通过。
- 修复提交 `ebea2febe1d8a15f3c802f48a7ab7dee480bedbd` 已进入本地和远程 `main`。
- CloudCC pagecomponent 同一 ID `6a5628cee4b0a577cbba2088` 已由 V15 升为 V16；customPage 保持 V9 并继续引用该 ID，注入验证 `issues=[]`。
- 生产登录态重载后工作台显示“CloudCC CRM 已连接”，真实客户队列、客户详情和 AI 助理已加载，浏览器错误日志为 0。未代用户执行业务写操作；HUMAN 业务验收仍独立于技术回读。
- AgentCiCi 生产保持 `2.8.66 / e805c0ef7142`，未重建应用或状态服务；后端 `JsonAlias` 等待 `2.8.67` 标准发布。
