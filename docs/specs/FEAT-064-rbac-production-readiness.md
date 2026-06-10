---
kind: feature-spec
feature_id: FEAT-064
title: RBAC and audit production readiness hardening
status: review
owner_role: fullstack-agent
task_ids: TASK-151
related_decisions: DEC-008, DEC-022
related_issues: none
updated_at: 2026-06-08T12:06:00+08:00
updated_by: MANAGER-001
---

# FEAT-064 - RBAC 与审计生产就绪收口

## 背景与目标

当前项目已经具备组织角色、平台角色、Agent 级权限模型和基础审计日志，但还没有达到生产就绪 RBAC 与审计追踪。主要缺口是后端仍可通过 `X-Org-Id` / `X-User-Id` 请求头建立租户上下文，业务接口缺少默认鉴权兜底，平台写接口没有按平台角色细分；同时平台模型治理写操作缺少平台审计事件，平台审计查询缺少筛选、稳定 DTO 与脱敏契约。

本次目标是把权限边界从“受控内测可用”收口到“生产默认安全”：

- 公网请求不能通过伪造租户/用户 header 获得业务上下文。
- 除公开白名单、OpenAPI Key、embed token 等明确例外外，业务接口默认要求有效登录态。
- 平台读写操作按角色最小权限收口，审计角色保持只读。
- 平台模型供应商、已选模型和模型路由变更必须写平台审计，审计详情不记录 API Key 或完整敏感配置。
- 组织审计和平台审计查询需要支持数据库侧过滤、最近 7 天窗口、稳定 DTO、敏感信息脱敏和常用查询索引。
- 建立 RBAC 专项回归测试，覆盖无 token、伪造 header、组织/平台 token 混用、平台角色越权、Agent/OpenAPI 权限边界。

## 范围

### In Scope

- 后端 `TenantContextFilter` 改为生产默认不信任 `X-Org-Id` / `X-User-Id`。
- 新增应用级认证白名单，未认证业务请求默认返回 `401`。
- 保留 OpenAPI `cici_ak_`、embed token、认证入口、`/system/health`、`/actuator/health`、企业微信回调和公开资源入口。
- 平台治理、计费配置、模型配置等写操作按角色细分。
- 平台模型治理写操作补平台审计。
- `/platform/audit/logs` 补 `from`、`to`、`eventType`、`resourceType`、`q`、`limit` 查询参数，返回 `{items, from, to, hasMore, nextCursor}`。
- `/ops/audit/logs` 改为数据库侧过滤，减少固定拉取窗口导致的漏查风险。
- 新增审计查询索引。
- 平台审计页补筛选 UI，并兼容旧数组响应和新版 `{items}` 响应。
- 补充 RBAC 生产就绪集成测试。
- 更新任务状态、测试报告和当前项目快照。

### Out Of Scope

- 不引入完整 Spring Security 迁移。
- 不实现部门、用户组、自定义业务角色的完整授权匹配。
- 不做审计导出、长期归档、归档清理任务和完整合规报表。
- 不改生产 Nginx 配置或线上部署。

## 用户场景

- 未登录访客访问业务 API，应明确收到认证错误，而不是靠缺失上下文产生不稳定异常。
- 普通组织成员不能访问组织管理接口，也不能通过 header 伪造管理员或其他成员。
- 组织管理员不能访问平台后台。
- 平台审计员可以看平台只读信息，但不能修改模型、计费、技能、策略或工具配置。
- 平台运营人员在 `/platform/audit` 可以按事件、资源和关键词排查最近审计记录，且不会看到 API Key、token、password 或完整手机号。
- OpenAPI Key 调用仍按 Key 状态、Agent 发布状态、渠道启用和 run-as `RUN` 权限执行。

## 现状与约束

- 认证上下文由 `TenantContextFilter` 解析 JWT 并写入 `TenantContext`。
- 项目尚未使用 Spring Security，权限主要通过 AOP 注解和服务层手写校验实现。
- 平台 token 已有 `typ=platform`，组织 token 与平台 token 已有隔离基础。
- Agent ACL 第一阶段只实现 `ORG`、`USER`、`SYSTEM_ROLE`，部门和用户组保留为后续扩展。

## 方案设计

1. 在 `TenantContextFilter` 中建立白名单和默认认证策略。
2. 只有配置显式允许时，才接受 header 上下文；默认关闭。
3. 对需要登录态的接口，如果既没有有效 JWT，也没有允许的外部认证例外，直接返回 `401`。
4. 对平台 Controller 的写接口补充方法级 `@RequirePlatformRole(...)`。
5. `PlatformModelProviderController` 在成功完成供应商、已选模型、路由设置和路由删除后写 `platform_audit_log`，detail 只记录非秘密摘要。
6. `PlatformAuditService` 与 `AuditService` 使用数据库侧时间窗、事件和关键词过滤，返回脱敏 DTO。
7. `V62__audit_log_query_indexes.sql` 增加 `audit_log` 与 `platform_audit_log` 的查询索引。
8. 补充集成测试固定上述边界，避免后续新增接口绕开规则。

## 接口与数据影响

- 新增 `V62__audit_log_query_indexes.sql`，只增加索引，不改变现有数据结构。
- 默认行为变化：直接带 `X-Org-Id` / `X-User-Id` 访问业务接口不再被接受。
- 如本地或测试环境确需 header 上下文，可通过显式配置开关启用；生产默认关闭。
- `/platform/audit/logs` 响应从数组升级为对象；前端已兼容旧数组，降低滚动发布错版风险。

## 任务拆分

- `TASK-151`: RBAC production readiness hardening。

## 验收标准

- 无 token 访问普通业务接口返回 `401`。
- 伪造 `X-Org-Id` / `X-User-Id` 访问用户态接口返回 `401`。
- 平台 token 访问组织管理接口返回 `403`。
- 组织 token 访问平台接口返回 `403`。
- `PLATFORM_AUDITOR` 可读平台基础信息，但不能执行平台写操作。
- `ORG_USER` 不能访问组织管理接口。
- OpenAPI run-as 权限撤销后调用返回 `403`。
- 平台模型治理写动作进入平台审计，且响应和审计查询不泄露 API Key。
- 平台审计查询支持事件类型、资源类型和关键词筛选，并对 token、api key、secret、password、cookie、手机号做脱敏。
- 组织审计查询使用数据库侧过滤并返回 `hasMore`。
- `mvn -q -Dmaven.repo.local=.m2 -Dtest=RbacProductionReadinessIntegrationTest,PlatformAuthIntegrationTest,AuthFlowIntegrationTest,AgentOpenApiIntegrationTest test` 通过。
- `mvn -q -Dtest=AgentRunTraceIntegrationTest,RbacProductionReadinessIntegrationTest,PlatformGovernanceIntegrationTest,PlatformModelProviderIntegrationTest test` 通过。
- `npm run build` 通过。
- `git diff --check` 通过。

## 风险与回滚

- 风险：历史本地脚本或测试若依赖 `X-Org-Id` / `X-User-Id`，需要改为登录后传 JWT，或显式打开测试配置。
- 风险：白名单漏掉公开回调入口会导致第三方回调失败；本次保留企业微信回调、公开头像、认证入口和系统/Actuator 健康检查。
- 风险：`/platform/audit/logs` 响应形态变化影响旧前端；前端兼容旧数组和新 `{items}`，后端进入正式发布前仍需确认部署顺序。
- 回滚：恢复 `TenantContextFilter` 的 header 上下文默认行为，并撤销平台角色方法级限制。

## 实现进展

- 2026-06-08: 创建规格与任务，开始 RBAC 生产就绪收口。
- 2026-06-08: 已完成后端硬化与回归验证。`TenantContextFilter` 默认要求认证上下文并禁用外部 header 上下文；平台写接口补充方法级角色限制；新增 `RbacProductionReadinessIntegrationTest`，并覆盖 `/actuator/health` 不被 RBAC 拦截。
- 2026-06-08: 已完成审计追踪生产就绪补强。平台模型治理写动作进入平台审计；平台审计和组织审计查询支持数据库侧过滤、DTO 和脱敏；新增审计查询索引；平台审计页补筛选。
- 验证通过：focused RBAC integration test、auth/platform/governance/billing/model/OpenAPI 回归套件、审计/RBAC/model/platform 窄回归、frontend build、assignment scope check、`git diff --check`。

## 交接说明

接手者先看本文件、`TenantContextFilter`、`PlatformRoleAuthorizationAspect`、平台 Controller 写接口、`AuditService`、`PlatformAuditService`、`PlatformModelProviderController` 和对应集成测试。剩余更高阶合规项是审计导出、归档/清理任务和按套餐保留策略自动执行。
