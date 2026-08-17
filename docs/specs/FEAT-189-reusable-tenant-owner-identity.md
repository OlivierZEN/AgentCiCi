---
kind: feature-spec
feature_id: FEAT-189
title: 新租户 Owner 全局身份复用
status: approved
owner_role: fullstack-agent
task_ids: TASK-310
related_decisions: FEAT-024, FEAT-145, FEAT-165
related_issues: none
updated_at: 2026-08-17T02:27:24Z
updated_by: codex
---

# FEAT-189 - 新租户 Owner 全局身份复用

## 背景与目标

AgentCiCi 的身份模型是“一个自然人对应一个全局账号，一个全局账号可加入多个租户，角色和状态属于租户成员关系”。当前平台“开通新租户”虽然会按手机号复用账号，但没有先把手机号、邮箱和公共编号解析为一个明确的全局身份；手机号未命中时仍尝试创建账号并绑定邮箱，导致已注册用户、标识漂移或手机号/邮箱冲突时表现为笼统的开通失败。

本功能把“确认 Owner 全局身份”提升为租户开通的正式前置步骤：平台运营可以明确选择已有用户或创建新用户，服务端同时解析手机号、邮箱和公共编号；已有用户只新增新租户的 Owner 成员关系，不创建重复账号、Keycloak 用户或密码凭据。

## 范围

### In Scope

- 新增受平台角色保护的 Owner 身份解析接口，返回脱敏结果、解析状态、统一身份状态和能否继续。
- 租户开通请求显式区分 `EXISTING`、`NEW` 与旧调用兼容的 `AUTO` 模式。
- 已有用户通过不可变公共编号复用；新用户必须在手机号和邮箱均未占用时创建。
- 手机号、邮箱指向不同账号时失败关闭，并返回可执行的冲突提示。
- 单次开通使用幂等键，重复提交返回同一租户；同一键携带不同请求时拒绝。
- 平台开通弹窗改为“租户信息 → Owner 身份 → 确认摘要”的渐进式桌面流程。
- 统一认证环境隐藏本地兼容密码；只有后端声明兼容模式时才显示。
- 补充后端、前端测试及 `cici.localhost` 桌面端验收。

### Out Of Scope

- 不自动合并两个全局账号。
- 不修改既有账号的手机号、邮箱、密码、Keycloak `sub` 或统一身份绑定。
- 不改变既有租户的 Owner，不替代 Owner recovery / identity reconciliation。
- 不新增移动端布局、移动端截图或移动端自动化测试。
- 不发布 UAT 或生产。

## 用户场景

1. 平台运营选择“已有用户”，输入手机号、邮箱或公共编号精确查找；确认脱敏身份卡片后，将该账号设为新租户 Owner。
2. 平台运营选择“新用户”，填写手机号、邮箱和显示名称；系统预检无占用后允许进入确认页并创建待激活 Owner。
3. 新用户表单命中已有账号时，页面不报笼统错误，而显示“检测到已有用户”，提供“改为复用此用户”。
4. 手机号和邮箱分别属于不同账号时，页面明确提示冲突并阻止开通；运营人员只能修正输入或重新选择用户。
5. 已激活已有用户成为新租户 Owner 时直接为 `ACTIVE`，不重发激活邮件或重置密码；待激活用户保持 `PENDING_ACTIVATION`。

## 现状与约束

- `PlatformTenantLifecycleService.createTenant` 只先调用 `findMobileAccount`；未命中时创建账号并同步邮箱。
- `account_login_identifier` 对类型、规范化值和状态有唯一约束，必须保留“一标识只归属一个全局账号”。
- Keycloak 身份恢复只能使用不可变公共编号和既有受管 ownership 校验，不能按未验证手机号或邮箱弱绑定。
- 外部 Keycloak 写入不受本地数据库事务天然回滚，复用已激活身份时不应重复调用创建或激活动作。
- 页面属于 `/platform/*` product register，保持“鎏金账房”受控主题、紧凑治理密度和标准 modal 语汇。

## 方案设计

### 身份解析状态机

| 状态 | 条件 | 行为 |
|---|---|---|
| `NEW_ACCOUNT` | 手机号、邮箱、公共编号均未命中 | 允许以 `NEW` 模式创建 |
| `EXISTING_ACCOUNT` | 所有已命中的标识都属于同一个账号 | 返回脱敏账号卡片，可改为 `EXISTING` 模式 |
| `IDENTIFIER_CONFLICT` | 手机号、邮箱或公共编号命中不同账号 | 阻止开通，不自动合并 |
| `ACCOUNT_BLOCKED` | 命中账号不是 `ACTIVE` | 阻止开通，要求先恢复账号 |

只命中一个标识时返回 `EXISTING_ACCOUNT`，但不把其他未命中输入写入该账号；运营人员必须显式点击“复用此用户”，最终请求只携带公共编号。

### 渐进式交互

- 步骤一“租户信息”：租户名称、平台备注。
- 步骤二“Owner 身份”：默认“选择已有用户”，可切换“创建新用户”。
- 已有用户使用单一精确检索框；结果显示名称、公共编号、脱敏手机号/邮箱、统一身份状态和已加入租户数。
- 新用户填写手机号、邮箱和显示名称，点击“检查身份”；命中已有账号时展示同一结果卡片和“改为复用此用户”。
- 步骤三“确认开通”：汇总租户、Owner、复用/新建方式、激活结果和通知行为；最终按钮才产生写入。
- 加载、空结果、冲突、停用、统一身份待协调和服务错误均有独立文案，不依赖按钮禁用表达原因。

### 服务边界

- 新增 `PlatformTenantOwnerResolutionService`，统一解析 MOBILE、EMAIL、public ID、账号状态、统一身份绑定和 ACTIVE 成员数量。
- `PlatformTenantLifecycleService` 只消费解析结果：
  - `EXISTING`：按公共编号重新解析并复用账号；不接受手机号/邮箱覆盖。
  - `NEW`：服务端重新预检，必须为 `NEW_ACCOUNT` 才能创建。
  - `AUTO`：兼容旧调用，使用手机号和邮箱解析；冲突时失败关闭。
- 已有激活统一身份只校验本地受管绑定并创建成员关系；无绑定或待激活身份继续进入既有 `ensureHumanIdentity` 安全协调路径。
- 使用受管幂等记录保存请求指纹、结果租户和状态；同键同请求返回原结果，同键异请求返回冲突。

## 接口与数据影响

新增：

```http
POST /platform/tenants/owner-resolutions
```

请求可包含 `ownerMobile`、`ownerEmail` 或 `ownerPublicId`，至少一项非空。响应返回 `resolution`、`canProceed`、`accountPublicId`、脱敏展示信息、`identityStatus`、`activeTenantCount`、`unifiedIdentityEnabled` 和用户可读 `message`。

扩展：

```http
POST /platform/tenants
```

- 新增 `ownerMode`：`EXISTING | NEW | AUTO`，缺省为 `AUTO` 兼容旧调用。
- 新增 `ownerAccountPublicId`：`EXISTING` 必填。
- 新增 `idempotencyKey`：新页面必填；旧调用缺省时由服务端按请求生成一次兼容键，不对跨请求重放作承诺。
- 响应新增 `ownerResolution`，保留 `reusedExistingAccount` 和 `ownerActivationRequired`。
- 新增数据库迁移保存租户开通幂等记录，不改变既有身份、成员或租户表结构。

## 任务拆分

- `TASK-310`：规格、后端身份解析与幂等、平台渐进式 UI、测试、本地 main 和开发环境验收。

## 验收标准

- 同一手机号和邮箱命中同一账号时可开通新租户，账号数量不增加，只新增 Owner 成员关系。
- 仅手机号或仅邮箱命中时必须显式切换复用，且不覆盖账号另一标识。
- 手机号和邮箱属于不同账号时返回结构化冲突，数据库和 Keycloak 均无写入。
- 已激活账号不创建 Keycloak 用户、不发送激活邮件、不修改密码；成员为 `ACTIVE`。
- 新账号创建后成员为 `PENDING_ACTIVATION`，仍走受管 HUMAN provisioning。
- 相同幂等键和相同请求返回同一租户；相同键不同请求返回 409。
- 前端定向测试、完整前端测试和 production build 通过；后端定向测试与 package 通过。
- 从本地 `main` 构建受影响服务并更新 `https://cici.localhost/`；目标路由、版本指纹、容器健康、重启次数和完整 stack verify 通过。
- 受权桌面浏览器验证已有用户复用、新用户预检、冲突提示、确认摘要和最终开通主路径；没有受权会话时明确保留该验收边界。

## 风险与回滚

- 身份解析结果可能在预检后变化，最终创建必须在服务端重新解析并依赖数据库唯一约束。
- 不允许把标识冲突降级为自动选择或自动合并；账号合并需独立高风险规格。
- 回滚应用代码和本次幂等表不会删除已创建租户；测试租户使用正式租户生命周期处理，不直接删表。

## 实现进展

- 2026-08-17：用户确认“先确认全局身份，再创建租户成员关系”的设计，规格进入实现。

## 交接说明

- 先读本规格、FEAT-024、FEAT-145、FEAT-165、TASK-310。
- 实现不得弱化 Keycloak ownership 校验，不得把平台账号与租户 HUMAN 账号合并。
