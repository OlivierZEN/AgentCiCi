---
kind: feature-spec
feature_id: FEAT-046
title: Platform tenant manual provisioning and lifecycle entry split
status: planned
owner_role: platform-product-lifecycle
task_ids: TASK-124
related_decisions: none
related_issues: none
updated_at: 2026-05-21T04:35:00Z
updated_by: ai
---

# FEAT-046 - Platform tenant manual provisioning and lifecycle entry split

## 背景与目标

- 当前 `/platform/tenants` 已具备租户列表、保留策略、导出、销毁预演和真实销毁流程，但页面是“同屏列表 + 同屏详情”的治理工作台结构。
- 新需求要求平台运营先进入租户列表，再从列表点击进入单个租户的生命周期管理页；同时在租户列表页右上角新增“开通新租户”入口，支持平台手动开通新租户。
- 同步调整租户 ID 规则：所有后续新租户 ID 由系统自动生成，长度固定 20 位，前三位固定为 `org`，后 17 位为数字与字母混合随机串。

本规格的目标是先把页面入口结构、手动开通流程、接口边界和 ID 生成规则落成统一事实源，作为下一步前后端实现依据。

## 范围

### In Scope

- 平台租户生命周期入口结构调整：
  - `/platform/tenants` 只承接租户列表与新租户开通入口。
  - `/platform/tenants/:orgId` 承接单租户生命周期管理。
- 平台手动开通新租户能力：
  - 列表页右上角新增“开通新租户”按钮。
  - 通过 modal 填写必要租户信息并直接开通。
  - 开通成功后刷新列表并进入新租户详情页。
- 新租户 ID 生成规则与共享生成器。
- 平台审计日志补充租户开通事件。
- 与当前平台租户生命周期页面相关的前端路由、页面组织和后端平台接口调整。

### Out Of Scope

- 计费套餐、订阅、合同或发票流程。
- 历史租户 ID 批量迁移或回填。
- 组织管理员侧 `/admin/*` 的组织信息或成员管理重构。
- SSO、MFA、密码重置、租户自助注册流程重写。
- 真实销毁、导出、legal hold 既有业务规则重写。

## 用户场景

- 平台运营进入租户生命周期模块，先看到全量租户列表，快速扫描状态、成员规模和最近生命周期动作。
- 平台运营点击某一租户行，进入该租户独立的生命周期管理页，继续处理冻结、恢复、导出、预演和销毁动作。
- 平台运营需要为新客户手动开通租户，在列表页点击“开通新租户”，填写租户名称和首位 Owner 信息后直接创建租户。
- 如果首位 Owner 手机号对应已有全局账号，系统应复用该账号并新增该租户的 `OWNER` 成员关系，而不是重复创建账号。

## 现状与约束

- 当前前端 `frontend/src/platform/pages/PlatformTenantsPage.tsx` 采用单页双栏结构：左侧租户列表，右侧同页详情。
- 当前后端 `PlatformTenantLifecycleController` 仅支持列表、保留策略、冻结/恢复、待销毁、导出、预演和真实销毁，不支持平台手动创建租户。
- 当前 `AuthService.createOrg(...)` 的组织 ID 规则为 `org-` + 12 位 UUID 子串，这与新需求不一致。
- 当前 `org` 表只持有 `id / name / status` 三个主字段；首位 Owner、手机号、邮箱等信息分别落在 `user_account` 与 `organization_member`。
- FEAT-024 已确立“全局账号 + 组织成员关系”的主模型；本次手动开通必须复用该模型，不能重新引入“租户内独立账号表”。
- `orgId` 是稳定租户标识，已有历史租户和演示租户（如 `demo-org`）不得因为新规则被重写。

## 方案设计

### 1. 页面与路由结构

#### 1.1 租户列表页

- 路由：`/platform/tenants`
- 职责：只承担“看列表、筛选/检索、进入详情、开通新租户”。
- 页面头部：
  - 保留当前页面标题“租户生命周期”。
  - 右上角保留现有租户总数/冻结数摘要。
  - 在摘要区域加入主操作按钮“开通新租户”。
- 列表区：
  - 延续现有紧凑表格基线，不再在同页右侧展开详情。
  - 默认列维持当前信息密度：租户名称、租户 ID、状态、成员数、最近生命周期记录。
  - 点击整行或按 Enter 进入 `/platform/tenants/:orgId`。

#### 1.2 单租户生命周期页

- 路由：`/platform/tenants/:orgId`
- 职责：承接当前详情面板中的全部生命周期治理能力。
- 页面顶部：
  - 展示租户名称、租户 ID、状态摘要。
  - 提供返回租户列表入口。
  - 延续当前冻结、恢复、标记待销毁、导出资料、生成销毁预演、真实销毁等动作。
- 内容区：
  - 直接复用当前详情面板已有信息架构：租户摘要、保留策略、导出记录、销毁记录、预演 manifest。
  - 不再与其他租户列表并排显示，避免平台运营在长页面中同时处理多租户信息导致焦点漂移。

### 2. 手动开通新租户

#### 2.1 交互形态

- 列表页右上角“开通新租户”点击后，打开 modal。
- modal 必须遵守 `鎏金账房` 和仓库弹窗规则：
  - `role="dialog"`
  - `aria-modal="true"`
  - 带遮罩
  - 有明确标题
  - 页脚统一放置取消 / 确认按钮

#### 2.2 表单字段

Phase 1 以“直接可开通、与现有账号模型兼容”为目标，字段定义如下：

- 必填：
  - `tenantName`：租户名称，对应 `org.name`
  - `ownerMobile`：首位 Owner 手机号，沿用现有 11 位中国大陆手机号校验
  - `initialPassword`：当手机号尚未绑定全局账号时，用于创建首个登录密码；若手机号已存在，全局账号复用且不覆盖原密码
- 选填：
  - `ownerDisplayName`：首位 Owner 显示名称，写入 `user_account.display_name`，无值时允许后续由用户自行补齐
  - `ownerEmail`：首位 Owner 邮箱，写入 `user_account.email`
  - `provisionNote`：平台开通备注，仅用于平台审计摘要，不落业务展示主字段

#### 2.3 成功结果

- 创建成功后：
  - modal 关闭
  - 列表刷新
  - 页面自动跳转到 `/platform/tenants/:orgId`
  - 顶部成功提示展示“新租户已开通”
- 返回结果中至少应包含：
  - `orgId`
  - `orgName`
  - `status`
  - `ownerMemberId`
  - `ownerAccountId`
  - `reusedExistingAccount` 布尔值，供前端提示当前 Owner 是否复用了既有全局账号

### 3. 租户 ID 新规则

#### 3.1 规则定义

- 新租户 ID 长度固定为 20。
- 前三位固定为小写 `org`。
- 后 17 位使用字母数字混合随机串。
- Phase 1 明确采用小写字母与数字字符集：`[a-z0-9]`。
- 格式正则：`^org[a-z0-9]{17}$`

#### 3.2 适用范围

新规则适用于“所有后续新增租户”，包括但不限于：

- 平台手动开通新租户
- `/auth/register` 首次注册自动创建组织
- 登录态 `/auth/organizations` 创建新组织

不在范围内：

- 历史已有租户 ID 回写
- 既有 `demo-org`、测试固化数据或平台 bootstrap 租户重命名

#### 3.3 实现边界

- 将现有 `AuthService.createOrg(...)` 中的 ID 生成逻辑抽到共享生成器，例如 `OrganizationIdGenerator`。
- 新平台手动开通接口与现有注册/创建组织逻辑必须共用同一生成器，避免不同入口产出不同格式的 `orgId`。
- 发生碰撞时重试生成，直到唯一；唯一性仍以数据库主键约束和 `existsById` 为最终兜底。

### 4. 后端接口与服务边界

#### 4.1 新增平台开通接口

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

返回示例：

```json
{
  "success": true,
  "data": {
    "tenant": {
      "orgId": "org0a7b5d1f9k2m4n6p8",
      "name": "华东售后中心",
      "status": "ACTIVE",
      "memberCount": 1
    },
    "ownerAccountId": "uuid",
    "ownerMemberId": "uuid",
    "reusedExistingAccount": false
  }
}
```

#### 4.2 服务职责

- `PlatformTenantLifecycleService` 新增 `createTenant(...)` 或引入专门的 `PlatformTenantProvisioningService`。
- `AuthService.createOrg(...)` 改为只负责“组织创建共用能力”，平台接口不复制组织 ID 生成和账号复用逻辑。
- 推荐把“查找/创建全局账号 + 建立 `OWNER` 成员 + 写入组织基本资料”收敛成共享服务，避免 `/auth/register` 与平台开通各写一套分叉规则。

#### 4.3 账号复用规则

- 若 `ownerMobile` 已绑定现有 `user_account`：
  - 复用该账号
  - 不重置既有密码
  - 为新租户创建一条 `organization_member(role=OWNER)`
- 若 `ownerMobile` 未绑定现有账号：
  - 创建新的 `user_account`
  - 创建 `MOBILE` 登录标识
  - 使用 `initialPassword` 建立首个密码凭证
  - 创建 `organization_member(role=OWNER)`

### 5. 生命周期默认值与审计

- 新租户默认状态为 `ACTIVE`。
- 平台开通时应同步确保存在一条默认 retention policy 记录，避免详情页首次打开依赖“只读接口顺带建默认值”的隐式副作用。
- 新增平台审计事件：
  - `platform.tenant.create`
- 审计详情至少包含：
  - 新租户 `orgId`
  - 操作平台账号
  - 是否复用既有账号
  - 开通备注
- 绝不能记录：
  - 明文 `initialPassword`
  - 密码 hash
  - 非必要的敏感个人信息正文

## 接口与数据影响

- 前端路由从“单页双栏”改为“两级页面”：
  - `/platform/tenants`
  - `/platform/tenants/:orgId`
- 后端新增 `POST /platform/tenants`。
- `GET /platform/tenants` 可继续复用当前列表返回形状。
- 单租户详情页首版可继续复用现有 `GET /platform/tenants/{orgId}/retention` 聚合响应，不强制本轮新增独立 summary API。
- `org` 表无需新增字段；如后续平台确需承载销售归属、支持负责人、开通来源等平台扩展属性，应另行评估是否引入 `platform_tenant_profile`，不在本轮强行扩表。

## 任务拆分

- `TASK-124`: 为租户生命周期列表先行、单租户详情页、平台手动开通入口和新租户 ID 规则落地设计文档与实现准备。

## 验收标准

- 平台进入 `/platform/tenants` 后，默认只显示租户列表，不再同页显示某个租户的完整详情面板。
- 平台从租户列表点击某个租户后，可以进入独立的 `/platform/tenants/:orgId` 生命周期管理页。
- 租户列表页右上角存在“开通新租户”按钮。
- 平台运营填写必要信息后，可以直接开通新租户，并自动跳转到新租户生命周期管理页。
- 新创建租户的 `orgId` 满足：
  - 长度 20
  - 前缀 `org`
  - 后缀 17 位 `[a-z0-9]`
- 新 ID 规则对所有后续新增租户入口一致生效；历史租户 ID 不被迁移。
- 平台审计能记录新租户开通事件，且不泄露密码明文。

## 风险与回滚

- 风险：平台手动开通与 `/auth/register`、`/auth/organizations` 的组织创建逻辑分叉，导致不同入口生成不同格式的 `orgId`。
  - 缓解：抽共享 ID 生成器和共用组织创建能力。
- 风险：平台误把已有手机号用户绑定为新租户 Owner，引发账号归属误会。
  - 缓解：提交前对手机号归属做明确提示，成功后返回 `reusedExistingAccount`。
- 风险：列表拆详情后，平台运营在多租户之间切换的路径变长。
  - 缓解：列表页保留紧凑扫描能力，详情页提供清晰返回入口；不把动作继续塞回同屏双栏。
- 回滚：
  - 若新页面结构上线后反馈不佳，可前端临时回退为现有双栏结构，但保留新的平台开通接口和 ID 生成器。
  - 若平台开通流程需要延后，可先保留列表/详情拆分和 ID 生成器，按钮置灰或隐藏。

## 实现进展

- 当前状态：planned。
- 已完成项：
  - 已核对 FEAT-024、FEAT-010、现有 `/platform/tenants` 前后端实现和 `AuthService.createOrg(...)` 的旧 ID 规则。
  - 已将本次需求收口为独立增量规格 FEAT-046。
- 未完成项：
  - 前后端实现尚未开始。
  - 视觉 QA、路由改造、平台开通 modal 和新 ID 生成器尚未落地。

## 交接说明

- 本规格是对 FEAT-024 与 FEAT-010 的增量调整，不推翻“全局账号 + 组织成员”模型。
- 下一步实现时，优先顺序应为：
  1. 抽共享组织 ID 生成器。
  2. 落平台 `POST /platform/tenants` 开通接口与审计。
  3. 拆分 `/platform/tenants` 列表页和 `/platform/tenants/:orgId` 详情页。
  4. 做桌面端与移动端截图复核。
- 若后续产品希望把“必要租户信息”扩展到套餐、销售归属、试用截止等平台字段，应另开规格，不要把本轮直开租户最小闭环继续膨胀。
