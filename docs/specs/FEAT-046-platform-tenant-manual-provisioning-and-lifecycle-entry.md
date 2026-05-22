---
kind: feature-spec
feature_id: FEAT-046
title: Platform tenant manual provisioning and lifecycle entry split
status: in_implementation
owner_role: platform-product-lifecycle
task_ids: TASK-124
related_decisions: none
related_issues: none
updated_at: 2026-05-21T08:57:18Z
updated_by: MANAGER-001
---

# FEAT-046 - Platform tenant manual provisioning and lifecycle entry split

## 背景与目标

- 当前 `/platform/tenants` 已具备租户列表、保留策略、导出、销毁预演和真实销毁流程，但页面仍是“同屏列表 + 同屏详情”的治理工作台结构。
- 新需求要求平台端先展示租户列表，再从列表点击进入单个租户的生命周期管理页。
- 同时需要在租户生命周期列表页右上角增加“开通新租户”按钮，允许平台运营手动填写必要租户信息并直接开通。
- 新增租户 ID 规则也需要在这一轮一起收口，避免后续不同入口继续生成不同格式的 `orgId`。

本规格先定义页面入口结构、手动开通流程、共享 ID 规则和实现边界，作为后续前后端改造的事实源。

## 范围

### In Scope

- `/platform/tenants` 调整为租户列表入口页。
- 新增 `/platform/tenants/:orgId` 单租户生命周期管理页。
- 列表页右上角新增“开通新租户”主按钮。
- 平台手动开通租户的 modal 表单、接口和成功跳转流程。
- 新租户 ID 统一为 20 位 `org` 前缀规则。
- 平台审计补充手动开通租户事件。

### Out Of Scope

- 套餐、订阅、合同、发票、计费联动。
- 历史租户 ID 迁移或批量修复。
- 组织管理员侧 `/admin/*` 的组织资料页重构。
- SSO、MFA、密码重置或完整租户自助注册重写。
- 既有导出、预演、真实销毁规则的业务重构。

## 用户场景

- 平台运营进入租户生命周期模块，先快速浏览全部租户列表，按状态、成员规模和最近动作判断后续处理对象。
- 平台运营点击某条租户记录后，进入该租户独立的生命周期管理页，继续处理冻结、恢复、导出、预演或销毁动作。
- 平台运营需要为新客户手动开通租户时，在列表页点击“开通新租户”，填写必要租户信息后直接创建。
- 如果首位 Owner 手机号已经对应现有全局账号，系统应复用该账号并新增该租户的 `OWNER` 成员关系，而不是重复造账号。

## 现状与约束

- 当前前端 `frontend/src/platform/pages/PlatformTenantsPage.tsx` 是双栏单页：左侧列表，右侧详情。
- 当前后端 `PlatformTenantLifecycleController` 仅支持列表、保留策略、冻结/恢复、待销毁、导出和 purge job，不支持平台手动创建租户。
- 当前 `AuthService.createOrg(...)` 的组织 ID 规则仍是 `org-` + 12 位 UUID 子串，与本次需求不一致。
- 当前 `org` 表只持有 `id`、`name`、`status`；首位 Owner 等信息分别位于 `user_account` 与 `organization_member`。
- FEAT-024 已确立“全局账号 + 组织成员关系”模型，本次必须复用该模型，不能引入新的租户内账号体系。
- 历史租户和测试/演示租户的 `orgId` 不能因新规则被强制重写。

## 方案设计

### 1. 页面与路由结构

#### 1.1 租户列表页

- 路由：`/platform/tenants`
- 职责：只承担“查看列表、筛选检索、进入详情、开通新租户”。
- 页面头部：
  - 保留当前“租户生命周期”标题。
  - 保留现有租户数、冻结数等紧凑摘要。
  - 在右上角新增主操作按钮“开通新租户”。
- 列表区：
  - 延续当前紧凑表格基线。
  - 默认展示：租户名称、租户 ID、状态、成员数、最近生命周期记录。
  - 点击整行或按 Enter 进入 `/platform/tenants/:orgId`。

#### 1.2 单租户生命周期管理页

- 路由：`/platform/tenants/:orgId`
- 职责：承接当前右侧详情面板中的全部生命周期治理能力。
- 页面顶部：
  - 展示租户名称、租户 ID、状态。
  - 提供返回列表入口。
  - 延续当前冻结、恢复、标记待销毁、导出资料、生成销毁预演、真实销毁等动作。
- 内容区：
  - 复用当前详情面板已有的信息架构：租户摘要、保留策略、导出记录、销毁记录、预演 manifest。
  - 不再与其他租户列表同屏并列。

### 2. 平台手动开通新租户

#### 2.1 交互形态

- 列表页右上角“开通新租户”点击后，打开 modal。
- modal 必须符合当前仓库 product register 规则：
  - `role="dialog"`
  - `aria-modal="true"`
  - 有遮罩
  - 有标题
  - 页脚统一使用取消 / 确认按钮

#### 2.2 表单字段

Phase 1 以“能直接开通并兼容现有账号模型”为目标：

- 必填：
  - `tenantName`：租户名称，对应 `org.name`
  - `ownerMobile`：首位 Owner 手机号，沿用现有中国大陆 11 位手机号规则
  - `initialPassword`：仅当手机号尚未绑定全局账号时使用；若手机号已存在，复用账号且不覆盖原密码
- 选填：
  - `ownerDisplayName`：首位 Owner 显示名称
  - `ownerEmail`：首位 Owner 邮箱
  - `provisionNote`：平台开通备注，仅用于平台审计摘要

#### 2.3 成功结果

- 创建成功后：
  - modal 关闭
  - 列表刷新
  - 自动跳转到 `/platform/tenants/:orgId`
  - 顶部提示“新租户已开通”
- 返回结果至少包含：
  - `orgId`
  - `orgName`
  - `status`
  - `ownerMemberId`
  - `ownerAccountId`
  - `reusedExistingAccount`

### 3. 新租户 ID 规则

#### 3.1 规则定义

- 新租户 ID 总长度固定为 20。
- 前 3 位固定为小写 `org`。
- 后 17 位为字母数字混合随机串。
- Phase 1 明确字符集使用 `[a-z0-9]`。
- 正则约束：`^org[a-z0-9]{17}$`

#### 3.2 适用范围

新规则适用于所有“后续新增租户”入口，包括但不限于：

- 平台手动开通新租户
- `/auth/register` 首次注册自动创建组织
- 登录态 `/auth/organizations` 创建新组织

不适用于：

- 历史已有租户 ID 回写
- `demo-org` 或既有测试数据重命名

#### 3.3 实现边界

- 将现有 `AuthService.createOrg(...)` 中的 ID 生成逻辑抽到共享生成器，例如 `OrganizationIdGenerator`。
- 平台手动开通接口与现有注册/创建组织逻辑必须共用同一生成器。
- 碰撞时重试生成，唯一性仍由数据库主键和 `existsById` 兜底。

### 4. 后端接口与服务边界

#### 4.1 新增接口

新增：

```http
POST /platform/tenants
```

请求示例：

```json
{
  "tenantName": "华东售后中心",
  "ownerMobile": "13800138000",
  "ownerDisplayName": "张三",
  "ownerEmail": "zhangsan@example.com",
  "initialPassword": "szyd1234",
  "provisionNote": "平台人工开通"
}
```

#### 4.2 服务职责

- `PlatformTenantLifecycleService` 新增 `createTenant(...)`，或引入独立 `PlatformTenantProvisioningService`。
- `AuthService.createOrg(...)` 收口成共享组织创建能力，不再让不同入口各写一套 `orgId` 逻辑。
- “查找/创建全局账号 + 创建 OWNER 成员 + 创建组织”应尽量复用统一服务。

#### 4.3 账号复用规则

- 若 `ownerMobile` 已绑定现有 `user_account`：
  - 复用该账号
  - 不覆盖既有密码
  - 为新租户新增一条 `organization_member(role=OWNER)`
- 若 `ownerMobile` 未绑定现有账号：
  - 创建 `user_account`
  - 创建 `MOBILE` 登录标识
  - 使用 `initialPassword` 建立首个密码凭证
  - 创建 `organization_member(role=OWNER)`

### 5. 默认值与审计

- 新租户默认状态为 `ACTIVE`。
- 平台开通时应同步确保有默认 retention policy，避免详情页第一次打开时依赖只读接口隐式补建。
- 平台审计新增事件：
  - `platform.tenant.create`
- 审计内容至少包含：
  - `orgId`
  - 操作平台账号
  - 是否复用既有账号
  - 开通备注
- 严禁记录：
  - 明文 `initialPassword`
  - 密码 hash
  - 非必要敏感正文

## 接口与数据影响

- 前端从“单页双栏”改为“两级页面”：
  - `/platform/tenants`
  - `/platform/tenants/:orgId`
- 后端新增 `POST /platform/tenants`。
- `GET /platform/tenants` 返回形状可继续沿用当前列表结构。
- 单租户详情首版可继续复用现有 `GET /platform/tenants/{orgId}/retention` 聚合响应。
- `org` 表本轮无需强制新增字段；如后续需要平台侧销售归属、支持负责人、开通来源等平台扩展属性，再单开规格评估。

## 验收标准

- 进入 `/platform/tenants` 后默认只展示租户列表，不再同屏展示完整详情。
- 从列表点击某个租户后，可以进入独立 `/platform/tenants/:orgId` 生命周期管理页。
- 列表页右上角存在“开通新租户”按钮。
- 填写必要信息后可以直接开通新租户，并自动跳转到新租户详情页。
- 新创建租户的 `orgId` 满足：
  - 长度 20
  - 前缀 `org`
  - 后缀 17 位 `[a-z0-9]`
- 新规则对所有后续新增租户入口一致生效，历史租户 ID 不迁移。
- 平台审计记录新租户开通事件，且不泄露密码明文。

## 风险与回滚

- 风险：平台手动开通与 `/auth/register`、`/auth/organizations` 创建逻辑分叉。
  - 缓解：统一共享 ID 生成器与组织创建能力。
- 风险：平台误复用已有手机号账号，造成归属误解。
  - 缓解：提交前明确提示，并在响应里返回 `reusedExistingAccount`。
- 风险：列表拆详情后切换路径变长。
  - 缓解：列表页保持高密度扫描，详情页提供清晰返回入口。
- 回滚：
  - 若页面结构反馈不佳，可前端临时回退为双栏结构，但保留新平台开通接口与共享 ID 生成器。

## 实现进展

- 当前状态：in_progress
- 已完成项：
  - 已梳理需求并收口本规格
  - 已明确与 FEAT-024、FEAT-010、当前 `/platform/tenants` 实现的关系
  - 已落地 `/platform/tenants` 列表页与 `/platform/tenants/:orgId` 详情页拆分
  - 已落地平台“开通新租户” modal、成功跳转与共享 tenant provisioning 数据流
  - 已落地共享 `org` ID 生成器与平台/认证侧组织创建收口
  - 已完成桌面端截图复核；既有移动端截图与修复仅作为历史证据，后续不再默认追加移动端兼容实现或移动端测试
- 未完成项：
  - 需在当前源码对齐的本地后端运行态下重新跑 FEAT-046 目标集成测试，补齐真实测试证据

## 交接说明

- 本规格是对 FEAT-024 与 FEAT-010 的增量调整，不推翻“全局账号 + 组织成员”模型。
- 实施优先顺序建议：
  1. 抽共享组织 ID 生成器
  2. 落平台 `POST /platform/tenants` 开通接口
  3. 拆分 `/platform/tenants` 列表页与 `/platform/tenants/:orgId` 详情页
  4. 做桌面端截图复核；不追加移动端兼容实现或移动端测试，除非用户单独开单
