---
kind: feature-spec
feature_id: FEAT-203
title: 应用中心 DEMO 单页单对象完整配置示例
status: implemented
owner_role: fullstack-agent
task_ids: TASK-333
related_decisions: FEAT-191, FEAT-193
related_issues: none
updated_at: 2026-08-27T11:21:49Z
updated_by: codex
---

# FEAT-203 - 应用中心 DEMO 单页单对象完整配置示例

## 背景与目标

运营平台应用中心已有应用、运行连接、不可变版本、依赖与发布控制面，但真实使用中出现两个草稿均停在 `0 个版本 / 0 个运行连接` 的状态。现有空态要求先接入 Provider，导致只需要一个平台页面、无需租户初始化的简单应用也被迫理解服务地址、鉴权、生命周期接口和重试策略。

本功能交付一个名称固定为 `DEMO示例应用`、代码固定为 `demo-example` 的已发布参考应用。它只有一个页面和一个“应用配置”对象，通过读取自身受治理目录记录展示所有实际生效参数；Provider 专属参数作为明确标注的非生效参考值展示，不写入运行连接，也不伪造连接测试成功。

## 用户与使用场景

- 平台管理员：从应用中心直接查看一个验证通过的最小配置，理解哪些字段是简单应用必填项。
- 应用开发者：对照完整参数参考决定是否需要 Provider、Secret 引用和生命周期接口。
- 联调负责人：区分“零初始化平台页面”与“带 Provider 的租户生命周期应用”，不再用无效地址制造失败草稿。

## 范围

### In Scope

- seed `DEMO示例应用 / demo-example / 1.0.0` 为已发布目录记录。
- 应用使用 `PLATFORM_BASE + PLATFORM_ROUTE + demo-example.page`，版本使用 `initializationEngine=NONE`。
- 示例版本声明一个 Semattice 可选依赖，展示版本约束、依赖类型和开通策略，但不构成开通阻断。
- 平台基础应用统一投影为当前租户已启用、初始化完成；有受控入口的应用返回 `OPEN` 动作。
- 新增一个认证后的平台示例页，只读取 `demo-example` 目录详情并渲染一个“应用配置”对象。
- 示例页分开显示实际生效参数和 Provider 连接参考参数；示例 URL 使用保留测试域名，Secret 仅展示引用名。
- 应用详情提供明确的“打开示例页”入口，租户应用卡也可按服务端返回的安全相对路由打开。

### Out Of Scope

- 不创建真实 Provider 运行连接，不测试或启用外部回调。
- 不创建 OAuth Client、Client Secret、Token、私钥或生产域名配置。
- 不把示例 URL 写入业务源码的运行配置、应用版本或数据库连接表。
- 不修改 Semattice、DevAutopilot 或父工作区源码。
- 不删除现有 `BimoApp1`、`测试应用1` 草稿。
- 不自动发布 UAT 或生产；本地验证完成后另行冻结候选并取得明确授权。

## 配置设计

### 应用实际生效值

| 参数 | 值 |
|---|---|
| `appCode` | `demo-example` |
| `displayName` | `DEMO示例应用` |
| `summary` | `单页单对象的应用中心完整配置参考` |
| `iconKey` | `application` |
| `ownerTeam` | `AgentCiCi` |
| `tenantMode` | `PLATFORM_BASE` |
| `trustedAppCode` | `null`，无需独立受信 Client |
| `launchMode` | `PLATFORM_ROUTE` |
| `launchRouteKey` | `demo-example.page` |
| `version` | `1.0.0` |
| `manifestSchemaVersion` | `tenant-application/v1` |
| `initializationEngine` | `NONE` |
| `providerBindingKey` | `null` |
| `steps` | `[]` |
| dependency | `semattice >=1.0.0 / OPTIONAL / AUTO_PROVISION_ALLOWED` |

### 页面与对象

- 页面：`DEMO配置总览`，固定认证路由 `/platform/internal-applications/demo-example/example`。
- 对象：`ApplicationConfiguration`，唯一记录由 `GET /platform/internal-applications/demo-example` 的应用、版本和依赖事实组合而成。
- 页面不维护第二份配置事实；字段说明和 Provider 参考值是展示常量，目录实际值始终来自后端回读。

### Provider 参考值

参考区覆盖连接名称、逻辑连接键、环境标识、网络范围、Base URL、契约版本、五类生命周期路径、三类鉴权方式、Secret 引用、超时和最大尝试次数。该区域必须显示“未写入本应用”的状态，不允许使用成功徽标或暗示连接已测试。

## 安全与契约

- `PLATFORM_ROUTE` 只允许服务端返回预定义同源相对路径；前端不从 `launchRouteKey` 拼接 URL。
- `SERVER_HANDOFF` 仍需后端短时交接结果；本功能不实现或绕过其鉴权。
- 示例 Base URL 固定为保留测试域名 `https://service.example.test`，只出现在明确标注的参考展示与测试断言中。
- 示例 Secret 仅使用 `demo-example.lifecycle-key` 引用名，不保存或展示 Secret 原文。

## 验收标准

1. 本地应用中心显示 `DEMO示例应用`，状态为已发布，默认版本为 `1.0.0`。
2. 详情回读应用、版本、清单摘要、可选依赖与 digest；不存在运行连接。
3. “打开示例页”进入一个页面，页面只展示一个 `ApplicationConfiguration` 对象。
4. 实际生效参数与后端回读一致；Provider 参数明确标为参考、未写入、未测试。
5. 任一平台基础应用按通用规则投影为已启用；有受控相对入口时返回 `OPEN`，前端不再只允许 `agentcici` 显示打开动作。
6. 匿名访问示例页或目录 API仍进入平台登录/返回 JSON 401；不能绕过平台管理员边界。
7. 后端聚焦测试、前端聚焦测试与全量测试、production build、域名扫描和 `git diff --check` 通过。
8. 从本地 `main` 构建 backend/frontend 并更新 `https://cici.localhost/`；回读两端版本/commit/image、健康、重启次数、正式路由和浏览器页面。
9. UAT 与生产保持不变，除非用户后续明确授权冻结与发布。

## 回滚

- 代码回滚恢复旧租户应用投影和前端路由；幂等 repeatable seed 数据可保留，旧代码会把它作为普通已发布目录读取但不会提供打开动作。
- 如需业务侧停用样例，可把目录状态改为 `SUSPENDED` 或 `RETIRED`；不删除历史版本与审计。
- UAT/生产若未来发布，按 AgentCiCi 单项目不可变镜像和完整备份回滚，不影响 Semattice、DevAutopilot 与四个状态服务。
