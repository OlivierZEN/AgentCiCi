---
kind: feature-spec
feature_id: FEAT-158
title: AgentCiCi AI表格业务对象实时列表
status: implementation
owner_role: fullstack-agent
task_ids: TASK-266
related_decisions: none
related_issues: none
updated_at: 2026-08-04T16:10:00Z
updated_by: MANAGER-001
---

# FEAT-158 - AgentCiCi AI表格业务对象实时列表

## 背景与目标

AgentCiCi 左侧入口已命名为“AI表格”。用户已确认高保真桌面视觉方向，并明确授权把预览升级为生产可用能力、发布线上。AI表格不再跳转 CloudCC CRM 登录页；它在当前用户会话内展示当前公司已开通的数据平台中、已发布应用模型的业务对象与真实只读记录。

## 范围

### In Scope

- 保持已确认的“对象目录 + 数据列表 + 详情抽屉”桌面结构，默认鎏金账房并兼容现有所有产品主题切换。
- 从已发布元数据读取当前租户的对象、字段标签、描述与字段类型；不再保留或展示演示记录。
- 通过当前会话成员的短期官方访问令牌读取真实记录，权限由数据平台对象、字段与记录范围策略执行。
- 支持刷新、加载、空结果、无权限、上游不可用和对象未发布等明确状态。
- 支持服务端游标分页。前端保存已访问游标栈，展示真实页码和“上一页/下一页”，不伪造总记录数。
- 支持对已建立索引的文本字段做前缀关键词查询；对象未配置可查询文本索引时，明确提示管理员配置，而不退化为不完整的本地筛选。
- 支持自定义显示字段；按当前公司、当前成员和对象 API 名写入浏览器本地存储，跨刷新保留且不跨租户泄漏。
- 行详情显示 API 返回且成员有字段读取权限的字段值；不在前端推断或补全受限字段。

### Out Of Scope

- 新增、编辑、删除、批量操作、导入、导出、排序、聚合统计和移动端适配。
- 修改数据平台的元数据、对象权限、字段权限、记录范围或底层记录 API。
- 把 OACT、tenant_id、company_id 或任何服务间凭据发到浏览器。

## 用户场景

- 企业成员点击 AI表格，直接看到当前租户已发布的业务对象，选择对象后读取自己有权限的数据。
- 成员按对象已索引的名称/编号前缀检索，并在下一页继续浏览同一结果集。
- 成员隐藏不关心的字段，刷新或再次进入仍看到自己的表头配置。
- 无数据、无读权限、尚未发布模型或上游不可用时，成员得到可操作且不泄露跨租户信息的提示。

## 安全与权限边界

- 浏览器仅调用同源 AgentCiCi API，使用既有登录 Cookie；不能传入公司、租户、用户或 OACT。
- 后端从 `TenantContext` 取得当前公司和成员，调用 `AuthService.issueSematticeOfficialAccess` 取得 60–600 秒短期 OACT。
- `GET /ai-table/catalog` 调用 `metadata.version.get-current`。`GET /ai-table/objects/{objectApiName}/records` 先读取目录验证对象与字段，再调用 `runtime.record.query`。
- 后端不接受任意上游 capability、字段或过滤表达式；`objectApiName` 仅允许当前已发布目录中的 API 名，查询只会使用后端从元数据选定的已索引文本字段，`limit` 限制为 1–100，`after` 仅透传受约束的游标。
- 数据平台仍是对象、字段与记录范围授权的唯一裁决者。任何上游拒绝按安全错误映射为前端状态，不使用“返回空数据”掩盖权限失败。

## API 契约

### `GET /ai-table/catalog`

返回：`companyName`、`source`、`retrievedAt` 和 `objects[]`。对象包含 `apiName`、`label`、`description`、`fields[]`（`apiName`、`label`、`dataType`、`indexed`、`defaultVisible`）及可选 `searchFieldApiName`。不返回 OACT、Tenant ID 或上游内部请求数据。

### `GET /ai-table/objects/{objectApiName}/records`

查询参数：`limit`（默认 25，最大 100）、`after`（不透明游标）、`query`（最多 128 字符）。

返回：`objectApiName`、`records[]`、`nextCursor`、`retrievedAt`、`queryFieldLabel`、`searchSupported`。每条记录含 `id`、`revision` 与已授权 `data`。无总量字段，因为底层 API 不提供总计数；界面只展示当前页和已加载行数。

## 交互与视觉

- 页面继续使用当前主题 token，不使用固定厂商色、独立色板、玻璃拟态或营销 hero。
- 删除演示“新增对象”主操作，工具栏仅保留已授权范围内的表头设置和刷新。
- 搜索框 placeholder 根据实际 `queryFieldLabel` 生成；无可查询字段时禁用搜索并显示配置原因。
- 筛选 tabs、虚构对象统计和前端静态记录全部移除，避免把演示状态误导成真实业务状态。
- 表格使用原生 table 语义；加载、空、异常及权限状态均占据表格主体，详情抽屉可在任意真实行打开。

## 验收标准

- AI表格不再渲染 CloudCC 登录 iframe，也不展示模拟业务记录。
- 目录和记录均来自当前用户的已发布租户元数据/记录能力；切换公司后不会复用另一公司的目录、列配置或记录。
- 关键词查询、游标分页、刷新、表头配置与详情均对真实 API 结果生效。
- 服务端测试证明：请求不信任浏览器租户/令牌；目录先于记录验证对象；上游请求携带当前用户 OACT；查询只使用已索引文本字段；上游错误不被误映射为数据。
- 前端构建、定向/全量测试、桌面浏览器验证、`git diff --check` 与发布 runbook 检查通过。
- 生产发布使用 `scripts/release-acr.sh`，先完成 dry-run、备份、不可变镜像和健康/版本/公网 smoke；只在受权会话可用时做真实业务数据回读，不伪造用户数据或凭据。

## 风险与回滚

- 若某对象没有已索引文本字段，保留浏览与分页，搜索明确提示需配置索引；不进行误导性的当前页本地查询。
- 元数据/记录 API 的上游故障会显示可重试状态，不降级到演示数据。
- 发布失败则按 runbook 保持上一不可变镜像或回退前一已验证 Git/镜像版本；不修改生产数据。

## 实施进展

- 已完成：高保真桌面交互与多主题视觉方向；用户已确认形态。
- 进行中：真实元数据、记录查询、会话 OACT 边界、游标分页、列偏好与生产发布。
