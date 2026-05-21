---
kind: feature-spec
feature_id: FEAT-040
title: Admin organization profile and self-service settings
status: draft
owner_role: product-admin
task_ids: TASK-118
related_decisions:
  - DEC-024
  - DEC-025
related_issues: none
updated_at: 2026-05-19T08:38:37Z
updated_by: ai
---

# FEAT-040 - Admin organization profile and self-service settings

## 背景与目标

AgentCiCi 当前组织管理后台已经覆盖知识库、模型、工具、技能、智能体构建、集成、微信客服、观测运维和用户管理，但缺少一个清晰的组织资料维护入口。后台左侧身份区当前容易把 `orgId` 当成组织可见名称展示，这会混淆两个完全不同的概念：

- `org.id` / `orgId` 是系统自动生成的组织唯一标识，稳定、不可编辑，用于租户隔离、外键关联、API、审计、日志和平台运维。
- `org.name` 或后续 `organization_profile.display_name` 是用户定义的组织名称，可编辑，用于产品界面展示，例如公司名、团队名、门店名或项目组名。

本功能目标是补齐 `/admin/organization` 组织设置页，让组织管理员可以维护本组织的展示名称和基础资料，同时保护系统级组织标识不可变，避免把租户标识误当成企业名称。

完成后应达到：

- 组织管理员可以在管理端编辑组织名称和基础资料。
- 普通产品界面优先展示用户定义的组织名称，而不是 `orgId`。
- `orgId` 只在必要的技术、审计和支持场景中以只读辅助信息出现。
- 平台侧 `/platform/tenants` 继续负责跨租户生命周期、冻结、恢复、销毁、平台审计和运营治理。

## 范围

### In Scope

- 新增管理端路由 `/admin/organization`，作为组织设置入口。
- 在管理端导航中增加「组织简档」菜单项。
- 提供当前组织基础资料读取与保存。
- 明确 `orgId` 不可编辑，并在 UI 中标注为「组织 ID」或「系统标识」。
- 允许编辑组织名称、简称、联系人、联系电话、联系邮箱、官网、行业、规模、时区和备注。
- 在左侧导航身份区、后台 header 或相关组织上下文中优先展示组织名称。
- 组织名称变更写入组织审计。
- 在组织设置页展示组织状态、Owner、成员数、创建时间等只读摘要。
- 组织简档入口默认展示为只读组织信息页；页面提供「编辑」按钮，点击后以阻塞 modal 打开组织信息编辑表单。
- 在组织信息下方展示当前组织使用情况汇总看板，包括用户、知识库、知识文档、技能、智能体、已发布智能体和数据导出等统计。
- 复用现有组织导出能力，展示组织数据导出入口和最近导出记录。
- 遵守 `PRODUCT.md` 和 `DESIGN.md` 的 product register 与 `鎏金账房` 管理端视觉规则。

### Out Of Scope

- 不允许组织管理员编辑 `orgId`。
- 不在本功能中实现组织创建流程或公开注册流程。
- 不在本功能中实现组织销毁、强制冻结、恢复或 purge 执行。
- 不在本功能中实现完整账单中心、订阅管理或发票信息维护。
- 不在本功能中实现 SSO、SCIM、企业通讯录同步或 OAuth 组织绑定。
- 不把平台 `/platform/tenants` 的跨租户治理能力复制到 `/admin/organization`。
- 不引入新的视觉语言，不新增营销化页面或大面积装饰素材。

## 用户场景

- 组织 Owner 初次进入后台，希望把系统默认组织名称改成真实公司名称。
- 团队管理员维护公司联系人、联系电话和联系邮箱，便于后续支持、账单或通知使用。
- 管理员需要复制组织 ID 给平台支持人员排查问题，但不希望日常界面把这串 ID 当作组织名。
- 企业改名、团队更名或门店迁移后，管理员需要自行更新组织展示名称。
- 管理员需要确认本组织状态、Owner、成员数量和最近数据导出记录。
- 支持人员让客户在后台复制「组织 ID」，用于平台侧 `/platform/tenants` 检索同一个租户。

## 现状与约束

- 当前 `org` 表已有 `id`、`name`、`status` 字段，见 `backend/src/main/resources/db/migration/V1__init_auth_tables.sql`。
- 当前 `OrgEntity` 暴露 `id/name/status`，但只有 `setStatus`，组织名称还没有显式编辑服务。
- 当前管理端 `AdminShell` 身份区显示 `auth.orgId`，容易让用户误以为这是组织名称。
- FEAT-024 已明确组织与全局账号、组织成员关系的长期模型，组织是企业、团队或租户空间。
- FEAT-010 已明确平台控制面 `/platform/**` 与组织管理端 `/admin/**` 是两个不同控制面。
- 已存在 `/admin/organization/export-jobs` 组织导出接口，可作为组织设置页的数据导出子能力入口。
- 产品页必须使用 `鎏金账房`：暖象牙表面、紧凑密度、墨色文字、香槟金结构线，不使用营销 hero、厚按钮、chip 化 tab、内层背景框或装饰渐变。

## 核心术语与硬规则

### `orgId`

`orgId` 是系统主键和租户边界，不是组织名称。

规则：

- 系统自动生成。
- 用户不可编辑。
- API、审计、日志、外键、数据隔离和平台支持使用。
- 普通管理端只在只读技术信息中展示。
- UI 文案必须叫「组织 ID」或「系统标识」，不能叫「组织名称」。
- 复制按钮只复制 `orgId`，不能触发编辑。

### 组织名称

组织名称是用户可编辑的显示名称。

规则：

- P0 优先复用 `org.name` 作为组织展示名称。
- 后续如果需要法律主体名、品牌名、工作台显示名等多套名称，再通过 `organization_profile` 增加字段。
- 组织名称允许重复，因为不同客户可能同名；唯一性依赖 `orgId`。
- 组织名称不能为空，长度建议 2 到 128 字符。
- 修改组织名称必须写审计，记录旧值、新值、操作者、时间和组织 ID。

### 平台租户名称

平台侧 `/platform/tenants` 列表应同时支持组织名称和 `orgId` 搜索。

规则：

- 平台运营人员可以看到 `orgId`，但主列仍应优先显示组织名称。
- 生命周期状态、数据保留策略和 purge 仍由平台侧治理。
- 平台人员查看或操作租户详情时继续写 `platform_audit_log`。

## 方案设计

### 功能入口

新增管理端导航项：

```text
/admin/organization 组织简档
```

建议放在「用户」附近，作为组织级基础设置入口：

```text
观测运维
用户
组织简档
```

如果后续管理端菜单较长，可以把「用户」和「组织设置」合并到一个「组织」分组，但 P0 不要求重构导航。

### 页面信息架构

页面当前使用只读组织简档，不使用 modal 作为首屏交互。组织信息编辑由只读信息面板右上角的「编辑」按钮触发，弹出阻塞 modal 完成资料维护。

```text
组织简档
├── 组织信息（只读）
│   ├── 组织名称 / 简称
│   ├── 组织 ID / 当前状态
│   ├── Owner / 联系人 / 电话 / 邮箱
│   ├── 官网 / 行业 / 规模 / 时区
│   ├── 资料创建 / 最近更新 / 更新人
│   ├── 备注
│   └── 编辑按钮 → 组织信息编辑 modal
└── 使用情况汇总
    ├── 已创建用户 / 活跃用户
    ├── 知识库 / 知识文档
    ├── 技能
    ├── 智能体 / 已发布智能体
    ├── 组织成员
    └── 数据导出 / 最近导出记录
```

右侧摘要不是嵌套卡片堆叠。外层已经是页面面板时，内部用字段标签、文本层级和 1px 分隔线表达，不使用背景块、chip、row shadow 或 hover fill。

### P0 字段

可编辑字段：

| 字段 | 建议列名 | 必填 | 说明 |
|---|---|---:|---|
| 组织名称 | `org.name` | 是 | 用户定义的公司名、团队名或空间名 |
| 组织简称 | `organization_profile.short_name` | 否 | 用于窄空间展示，可为空 |
| 联系人 | `organization_profile.contact_name` | 否 | 默认业务联系人 |
| 联系电话 | `organization_profile.contact_phone` | 否 | 不作为登录标识 |
| 联系邮箱 | `organization_profile.contact_email` | 否 | 不作为账号邮箱登录标识 |
| 官网 | `organization_profile.website` | 否 | 企业官网或团队主页 |
| 行业 | `organization_profile.industry` | 否 | 首版可用自由文本或固定枚举 |
| 组织规模 | `organization_profile.organization_size` | 否 | 首版建议枚举 |
| 时区 | `organization_profile.timezone` | 否 | 默认 `Asia/Shanghai` |
| 备注 | `organization_profile.notes` | 否 | 管理员内部备注 |

只读字段：

| 字段 | 来源 | 说明 |
|---|---|---|
| 组织 ID | `org.id` | 系统生成，不可编辑 |
| 当前状态 | `org.status` | 平台侧生命周期控制 |
| Owner | `organization_member.role_code = OWNER` | 展示姓名或手机号 |
| 成员数 | `organization_member` | 当前组织成员数 |
| 创建时间 | `org.created_at` 或扩展字段 | 如现有表没有，P0 可显示为空或后续补充 |
| 最近数据导出 | `organization_export_job` | 复用现有导出任务 |

### P1 字段

- 企业主体名称。
- 统一社会信用代码。
- 账单联系人。
- 账单邮箱。
- 默认工作台品牌名。
- 组织 Logo 或方形头像。

P1 字段不应阻塞 P0。账单字段需与 FEAT-037 对齐，避免在组织设置里提前实现半套 billing。

### P2 字段

- SSO 域名声明。
- 企业通讯录同步状态。
- 默认语言、默认地区和通知偏好。
- 数据保留策略的只读摘要。
- 支持授权开关或工单授权入口。

## 数据模型

### P0 推荐

P0 可直接允许编辑 `org.name`，并新增组织扩展表保存补充资料。

```sql
CREATE TABLE organization_profile (
    org_id VARCHAR(64) PRIMARY KEY,
    short_name VARCHAR(64),
    contact_name VARCHAR(128),
    contact_phone VARCHAR(64),
    contact_email VARCHAR(256),
    website VARCHAR(256),
    industry VARCHAR(128),
    organization_size VARCHAR(64),
    timezone VARCHAR(64),
    notes TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    updated_by VARCHAR(64),
    CONSTRAINT fk_organization_profile_org FOREIGN KEY (org_id) REFERENCES org(id)
);
```

如果实现时需要展示创建时间，建议给 `org` 补 `created_at`、`updated_at`，或在 `organization_profile.created_at` 中表示资料记录创建时间。不要为了展示创建时间伪造组织创建时间。

### 为什么不把所有字段塞进 `org`

- `org` 是租户边界核心表，字段应保持稳定。
- 组织资料字段会继续扩展，直接扩张 `org` 会让核心身份表变成杂项表。
- 平台租户扩展、计费订阅、生命周期策略已经有各自领域表，组织资料也应独立。

### 兼容策略

- 现有 `org.name` 作为 P0 组织名称事实源。
- 如果 `organization_profile.short_name` 为空，窄空间展示回退 `org.name`。
- 如果 `org.name` 为空或历史数据异常，接口返回 `org.id` 作为 fallback，但 UI 必须标注为缺失组织名称，不得悄悄把 `orgId` 当名称。

## API 设计

### 获取当前组织资料

```http
GET /admin/organization/profile
Authorization: Bearer <admin-token>
```

响应：

```json
{
  "success": true,
  "data": {
    "orgId": "org_xxx",
    "name": "示例科技有限公司",
    "shortName": "示例科技",
    "status": "ACTIVE",
    "contactName": "王女士",
    "contactPhone": "13800000000",
    "contactEmail": "ops@example.com",
    "website": "https://example.com",
    "industry": "企业服务",
    "organizationSize": "51-200",
    "timezone": "Asia/Shanghai",
    "notes": "",
    "owner": {
      "memberId": "member_xxx",
      "displayName": "Owen",
      "mobile": "13900009999"
    },
    "memberCount": 12,
    "createdAt": "2026-05-19T00:00:00Z",
    "updatedAt": "2026-05-19T05:47:39Z"
  }
}
```

### 更新当前组织资料

```http
PATCH /admin/organization/profile
Authorization: Bearer <admin-token>
Content-Type: application/json
```

请求：

```json
{
  "name": "示例科技有限公司",
  "shortName": "示例科技",
  "contactName": "王女士",
  "contactPhone": "13800000000",
  "contactEmail": "ops@example.com",
  "website": "https://example.com",
  "industry": "企业服务",
  "organizationSize": "51-200",
  "timezone": "Asia/Shanghai",
  "notes": ""
}
```

要求：

- 请求体不接受 `orgId` 修改。
- 如果请求体包含 `orgId`，后端忽略或返回 400。推荐返回 400，避免调用方误以为可以改。
- 只允许 `OWNER` 和 `ORG_ADMIN` 修改。
- 保存后写组织审计，例如 `organization.profile.update`。
- 如果组织名称变化，审计详情必须包含旧名称和新名称。

### 导出任务入口

沿用现有接口：

```http
GET /admin/organization/export-jobs
POST /admin/organization/export-jobs
GET /admin/organization/export-jobs/{jobId}
GET /admin/organization/export-jobs/{jobId}/download
```

组织设置页只展示最近记录和创建导出任务入口。导出下载仍需使用现有权限和文件安全规则。

## 后端实现建议

### 新增领域对象

- `OrganizationProfileEntity`
- `OrganizationProfileRepository`
- `AdminOrganizationProfileService`
- `AdminOrganizationProfileController`

### 服务职责

`AdminOrganizationProfileService` 负责：

- 根据 `TenantContext.requireOrgId()` 读取当前组织。
- 读取或初始化 `organization_profile`。
- 校验 `name`、邮箱、电话、URL、时区和长度。
- 更新 `org.name` 与 `organization_profile`。
- 查询 Owner 和成员数。
- 写入组织审计。

### 审计建议

使用现有组织审计能力，事件名建议：

```text
organization.profile.update
organization.name.update
organization.export.request
```

审计详情示例：

```text
name: old="Demo Org", new="示例科技有限公司"; contactEmail changed
```

敏感字段原则：

- 联系电话和邮箱可以记录是否变化，不必完整记录旧值。
- 组织名称不是密钥，可以记录旧值和新值。
- 不在审计中写入备注全文，避免管理员把敏感内容放进备注后被二次扩散。

## 前端实现建议

### 新页面

新增：

```text
frontend/src/admin/pages/AdminOrganizationPage.tsx
```

接入：

```text
frontend/src/App.tsx
frontend/src/admin/AdminShell.tsx
frontend/src/styles.css
```

### 布局

桌面：

- 页面标题为「组织简档」。
- 首屏保持只读组织信息 + 使用情况汇总，不直接铺开编辑表单。
- 组织信息面板提供「编辑」按钮，使用共享金色 primary 按钮。
- 点击「编辑」后打开阻塞 modal，包含组织名称、简称、联系人、联系电话、联系邮箱、官网、行业、规模、时区和备注。
- modal 内展示只读「组织 ID」，不能作为可编辑字段。
- modal 底部固定操作行：取消、保存。
- 保存按钮使用共享金色 primary，取消使用 warm white secondary。

移动：

- 先显示组织名称和保存状态。
- 页面信息保持单列，编辑按钮不撑开页面。
- modal 表单单列。
- 操作按钮保持同一行或按宽度换行，不能溢出。

### 交互状态

- 加载：使用骨架行，不使用居中大 spinner。
- 打开编辑：使用 blocking overlay、`role="dialog"`、`aria-modal="true"` 和可关联标题。
- 关闭编辑：右上角 `×` 是无边框纯 glyph，取消和遮罩点击关闭；保存中不允许关闭。
- 保存中：保存按钮进入 loading/disabled。
- 保存成功：关闭 modal，在页面 header 动作区显示短反馈。
- 保存失败：在 modal 表单顶部显示错误。
- 有未保存改动：离开页面时可使用浏览器 confirm 或路由拦截，具体实现按现有项目模式；不要默认弹大型 modal。
- 复制组织 ID：使用小型文本动作或图标按钮，复制后 inline 提示。

### 视觉约束

- 严格遵守 `DESIGN.md` 的 Product UI Scale。
- 默认控件 13px，辅助信息 11 到 12px。
- 不把只读摘要做成一组内层卡片。
- 不使用 chip 背景展示组织状态，状态可以用文本颜色和字段层级表达。
- 不使用选中背景、hover 背景、row shadow 或内层 box-shadow。
- 不使用蓝色、青绿色、黑色、渐变或营销式 hero。

## 与现有模块关系

### 与用户管理

用户管理继续负责成员邀请、角色调整、停用恢复和所有权转让。组织设置只读取 Owner 和成员数，不替代用户管理。

### 与平台租户管理

平台 `/platform/tenants` 继续负责：

- 租户列表和跨租户检索。
- 组织生命周期状态。
- retention policy。
- export/purge job 管理。
- 平台审计。

组织 `/admin/organization` 负责：

- 当前组织资料维护。
- 当前组织导出请求。
- 当前组织可见的只读状态摘要。

### 与计费

FEAT-037 负责套餐、订阅、用量和 credits。组织设置可以预留账单联系人字段，但不要提前实现账单业务逻辑。

### 与账号体系

FEAT-024 负责全局账号、登录标识和组织成员关系。组织设置不改变 `user_account`、`account_login_identifier` 或 `organization_member` 的核心归属。

## 任务拆分

### TASK-118 Admin organization profile design and implementation

建议拆为四步：

1. 规格与数据模型
   - 落地本规格。
   - 增加 `organization_profile` migration。
   - 决定是否给 `org` 增加 `created_at/updated_at`。

2. 后端 API
   - 新增 profile service/controller/repository。
   - 实现读取、保存、校验、Owner 摘要、成员数和审计。
   - 为 `orgId` 不可编辑增加测试。

3. 前端页面
   - 新增 `/admin/organization` 页面和「组织简档」导航。
   - AdminShell 优先展示组织名称。
   - 接入导出任务摘要。
   - 按用户继续调整，将可见页面改成只读组织信息 + 使用情况汇总看板。
   - 在只读组织信息面板增加「编辑」按钮，点击后弹出组织信息编辑 modal 并复用 `PATCH /admin/organization/profile` 保存。

4. 验证与视觉 QA
   - 后端集成测试。
   - 前端 build。
   - 桌面和 390px 移动截图。
   - 检查无横向溢出、文本不遮挡、按钮和字段状态符合 `鎏金账房`。

## 验收标准

### 用户可见

- 管理端导航出现「组织简档」。
- 组织管理员进入 `/admin/organization` 后看到当前组织名称和完整只读组织信息，而不是只看到 `orgId`。
- 页面上半部分展示组织信息，下半部分展示当前组织使用情况汇总看板。
- 汇总看板至少覆盖已创建用户、知识库、技能、智能体等统计。
- `orgId` 只读展示，可复制，不可编辑。
- 点击「编辑」后弹出组织信息编辑 modal，可以维护组织名称、简称、联系人、电话、邮箱、官网、行业、规模、时区和备注。
- 组织信息保存成功后，modal 关闭，页面只读信息和左侧组织名称同步更新。
- 左侧导航身份区优先显示组织名称，辅助显示组织 ID。
- 当前入口首屏不直接展示编辑表单或创建导出任务按钮。
- 最近数据导出记录可以查看。

### 技术验收

- `PATCH /admin/organization/profile` 不能修改 `orgId`。
- `GET /admin/organization/profile` 返回 `usageSummary`，至少包含用户、知识库、知识文档、技能、智能体、已发布智能体和导出任务计数。
- 非当前组织不能越权读取或修改其他组织资料。
- `ORG_USER` 不能修改组织资料。
- `OWNER` 和 `ORG_ADMIN` 可以修改组织资料。
- 组织名称修改写入审计。
- `org.name` 与 `organization_profile` 更新在同一事务内完成。
- 现有 `/platform/tenants` 不因本功能回退。

### 建议测试

后端：

```bash
mvn -q -Dmaven.repo.local=.m2 -Dtest=AdminOrganizationProfileIntegrationTest test
```

前端：

```bash
npm run build
```

浏览器验证：

- `/admin/organization` 桌面截图。
- `/admin/organization` 390px 移动截图。
- 保存组织名称后，导航身份区同步更新。
- 验证 `document.documentElement.scrollWidth === window.innerWidth`。

## 风险与回滚

### 风险

- 历史数据中 `org.name` 可能已经被填成类似 `demo-org` 的系统值，需要在 UI 中允许用户纠正。
- 如果多处前端仍直接展示 `auth.orgId`，用户体验会不一致。
- 如果审计记录保存完整电话、邮箱或备注，可能造成隐私扩散。
- 如果平台租户列表只显示名称不显示 `orgId`，平台支持检索会变慢。

### 回滚

- 若组织资料表上线后出现问题，可保留 `org.name` 编辑，隐藏扩展字段。
- 若前端页面异常，可临时移除导航入口，后端 API 保持不影响已有功能。
- migration 应只新增表和可选字段，不破坏现有 `org` 主键和组织成员关系。

## 实现进展

- 2026-05-19: 已完成设计文档，明确 `orgId` 与可编辑组织名称的边界。
- 2026-05-19: 已完成 P0 实现。新增 `organization_profile` 表、`GET/PATCH /admin/organization/profile`、组织资料服务与审计、`/admin/organization` 管理端页面、导航组织名称同步、最近导出摘要和直达路由代理修正。P0 继续以 `org.name` 作为组织名称事实源，未给 `org` 增加 `created_at/updated_at`；页面创建时间使用 profile 记录创建时间。
- 验证通过：`AdminOrganizationProfileIntegrationTest`、`frontend npm run build`、`git diff --check`、Playwright 桌面/390px 移动截图与移动无横向溢出检查。

## 交接说明

接手实现前先读：

- `docs/specs/FEAT-024-account-tenant-lifecycle-and-data-retention.md`
- `docs/specs/FEAT-010-platform-operations-console.md`
- `DESIGN.md`
- `PRODUCT.md`
- `frontend/src/admin/AdminShell.tsx`
- `backend/src/main/java/com/codehouse/ciciassistant/auth/domain/OrgEntity.java`
- `backend/src/main/java/com/codehouse/ciciassistant/platform/api/AdminOrganizationLifecycleController.java`

后续 P1/P2 可继续推进：

- 企业主体、账单联系人、Logo/头像、SSO/SCIM 等扩展字段。
- 平台 `/platform/tenants` 侧的组织名称与 `orgId` 双字段检索优化。
- 更多组织上下文展示点从 `orgId` 迁移到组织名称或简称。
