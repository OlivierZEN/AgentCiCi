# FEAT-139 - 租户详情路由标识兼容修复

## 背景

平台租户目录点击某一行后，浏览器会进入 `/platform/tenants/undefined`。租户应用页因此以字符串 `undefined` 请求详情与 Semattice 开通状态，后者的公司标识格式校验返回 `Validation failure`，页面无法展示。

## 根因

租户列表前端已按 `companyId` 生成详情路由，但仍存在返回旧字段 `orgId` 的服务响应或缓存响应。JSON 在运行时未做边界归一化，导致 `tenant.companyId` 为 `undefined`。

## 目标与范围

- 在平台租户 API 的前端边界将旧 `orgId` 兼容归一为 `companyId`。
- 租户列表、开通结果和详情数据只在归一后的有效公司标识上生成 URL。
- `/platform/tenants/undefined`、空参数或非法参数不发起详情请求，直接返回租户列表。
- 不修改后端 API、数据模型、页面视觉、移动端实现或生产发布。

## 设计与实现

- 保持页面内部领域模型使用 `companyId`，不在组件树传播双字段。
- 在 `platformTenantsShared` 解析响应后统一转换，兼容 `companyId` 与迁移期 `orgId`。
- 页面路由参数采用公司 ID 既有格式 `^org[a-z0-9]{17}$` 校验；无效地址用 replace 导回 `/platform/tenants`。
- 对不能归一出有效标识的租户行，不渲染为可导航目标，避免再次产生错误 URL。

## 验收标准

- 模拟 `/platform/tenants` 返回仅含 `orgId` 的租户时，点击后 URL 使用对应公司标识，不包含 `undefined`。
- 直接访问 `/platform/tenants/undefined` 不调用租户详情或 Semattice API，并返回租户列表。
- 正常 `companyId` 响应与现有开通后跳转保持可用。
- 前端构建和目标单元测试通过。
