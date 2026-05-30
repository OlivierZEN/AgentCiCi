---
kind: feature-spec
feature_id: FEAT-062
title: Platform Model Provider Governance
status: implemented
owner_role: project-manager
task_ids: TASK-145
related_decisions: FEAT-003, FEAT-022, FEAT-037
related_issues: none
updated_at: 2026-05-30T09:21:24Z
updated_by: MANAGER-001
---

# FEAT-062 平台统一模型厂商治理

## 背景

计费策略已确认：`Work Credits` 是组织可理解的智能体工作量口径，模型 token、平台代付资源、客户自有本地资源和第三方服务需要被平台统一归因和治理。若组织管理员自行配置模型厂商、API Key、base URL 和模型目录，平台无法稳定判断资源付款责任、模型档位、credit 折算和供应商可用性，也容易造成客户自有资源与平台代付资源混淆。

因此，模型厂商配置从组织后台上收至运营管理平台，由平台运营方统一配置和启停。组织管理员不再维护模型厂商凭据或模型目录，只在计费用量页查看当前版本、credits 余额、消耗和明细。

## 目标

- 关闭组织后台的模型厂商配置入口。
- 组织管理员默认只通过 `/admin/billing` 查看当前 credits 消耗、余额、quota 和 ledger。
- 平台运营在 `/platform/models` 统一管理模型厂商启停、API 地址、API Key、可用模型和模型目录。
- 运行时模型、Agent Builder 基础模型候选和知识库 embedding 模型候选均来自平台统一模型厂商配置。
- 组织用户和组织管理员不能通过组织 API 修改模型厂商配置。

## 非目标

- 本任务不实现完整模型价格表、模型档位 rating 或 token 到 credits 的最终财务折算。
- 本任务不迁移历史组织级模型配置数据，只改变后续读写事实源。
- 本任务不改变 Agent、Skill、知识库的业务权限模型。
- 本任务不新增移动端布局或移动端测试。

## 权限与事实源

- 平台事实源：`model_provider_config` 继续复用现有表结构，但写入平台治理组织 `app.auth.bootstrap-platform-account.governance-org-id`，默认 `demo-org`。
- 组织后台：
  - 不展示 `/admin/models` 导航。
  - `/admin/models` 路由重定向到 `/admin/billing`。
  - `/models/providers/**` 组织侧写接口返回 403。
- 平台后台：
  - 新增 `/platform/models` 页面。
  - 新增 `/platform/models/providers`、`/platform/models/providers/{providerCode}`、检测、拉取模型和已选模型接口。
  - 所有接口必须使用 `@RequirePlatformRole`。

## 运行时规则

- `ModelProviderService.credentialsForProvider(orgId, providerCode)` 改为读取平台统一厂商配置，不读取组织自己的 provider 配置。
- `agentBaseModels(orgId)` 和 `embeddingModelOptions(orgId)` 改为读取平台统一可用模型。
- `providerCode`、`modelName` 仍可作为 Agent 或知识库配置字段保存，但候选列表由平台控制。
- 私有化部署下，平台可配置本地模型或客户自有模型作为 `customer_paid` 资源；本任务只建立治理入口，不做强扣费。

## UI 设计

本次属于产品 register 的平台治理页面。视觉延续 `鎏金账房`：暖象牙底、墨色文字、紧凑密度、香槟金结构线；不引入新品牌视觉、深色命令中心或营销页式 hero。

平台模型页面采用左侧厂商列表 + 右侧配置面板 + 已选模型列表的结构，保持与现有平台工具治理页一致的扫描节奏。组织后台不提供替代配置说明页，避免给组织管理员造成“仍可配置”的暗示。

## 验收标准

- 组织后台导航不再出现“模型”。
- 访问 `/admin/models` 会进入 `/admin/billing`。
- 组织 token 调用 `/models/providers/{providerCode}`、`/models/providers/{providerCode}/check`、`/models/providers/{providerCode}/models/fetch`、`/models/providers/{providerCode}/selected-models` 均被拒绝。
- 平台 token 可以在 `/platform/models` 查看、更新、检测、拉取并维护已选模型。
- Agent Builder 和知识库 embedding 候选模型来自平台统一配置。
- 后端 focused tests 和前端 build/unit checks 通过。
