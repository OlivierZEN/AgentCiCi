---
kind: feature-spec
feature_id: FEAT-044
title: Platform console production polish and internal-info cleanup
status: completed
owner_role: frontend-product-platform
task_ids: TASK-122
related_decisions: none
related_issues: none
updated_at: 2026-05-21T03:56:34Z
updated_by: ai
---

# FEAT-044 - Platform console production polish and internal-info cleanup

## 背景与目标

- 当前 `/platform/*` 已具备平台治理骨架，但多处页面仍混入构建期或实现期表达，例如英文模块名、内部代码、原始策略术语、技术状态枚举和过度暴露的支持信息。
- 这些信息会削弱平台运营人员的扫描效率，也让界面看起来像研发工作台而不是可交付的生产控制面。
- 本次交付要把平台控制面收口为面向运营的生产界面，只保留运营真正需要的信息，并统一布局、密度、层级和响应式表现。

## 范围

### In Scope

- `/platform/login`
- `/platform`
- `/platform/skills`
- `/platform/tools`
- `/platform/tenants`
- `/platform/website-leads`
- `/platform/audit`
- `frontend/src/platform/*` 与平台主题相关样式
- 去除或弱化构建/内部实现信息，统一页面文案与视觉层级

### Out Of Scope

- 新增平台业务模块、路由或后端接口
- 改写平台权限模型、数据结构或治理流程
- 扩散改动到 `/admin/*`、`/help/*`、`/suite/*`

## 用户场景

- 平台运营人员在白天办公环境中连续处理平台治理、版本发布、风险工具收口、租户生命周期和审计追踪。
- 他们需要快速扫读状态、切换工作页、定位重点信息，而不是阅读模板实现痕迹或技术命名。
- 遇到异常或高风险动作时，界面要强调确认与事实，不要制造视觉噪音或误导。

## 现状与约束

- 项目设计事实源为 `PRODUCT.md`、`DESIGN.md`、`DESIGN.json`，默认 register 为 `product`，风格基线是 `鎏金账房`。
- `AGENTS.md` 要求页面实现遵循 `impeccable` 工作流，并在同一会话完成桌面端与移动端截图复核。
- 当前仓库存在未提交的并行改动，本次必须严格收敛在平台前端与必要状态文件，不触碰无关脏工作区内容。

## 方案设计

- 以“生产运营控制面”为目标重写平台页的可见文案层级，移除纯构建语汇、代码感标签和不必要的原始枚举暴露。
- 保留必要的支持信息，但降级为辅助层，不作为页面主标题、主统计或高显著区块内容。
- 统一平台壳层、页头、统计、表格、表单、文本操作和 modal 的视觉词汇，严格遵守 `鎏金账房` 的紧凑产品页规则。
- 检查桌面和移动断点，消除布局松散、信息块失衡、按钮风格漂移、横向溢出和过强卡片感。

## 接口与数据影响

- 不新增后端 API。
- 允许前端对现有返回字段做更克制的呈现、映射和排序。
- 若某些技术字段只适合作为支持信息，可降低可见性而不删除底层数据读取。

## 任务拆分

- `TASK-122`: 平台控制面生产化清理与视觉收口，由 `MANAGER-001` 执行。

## 验收标准

- `/platform/*` 页面不再出现明显的构建期/实现期信息泄漏，如无必要的英文模块口号、内部代码主显、技术占位语和面向研发的提示。
- 页面文案、布局、组件状态和密度统一符合 `鎏金账房` 的平台治理控制面基线。
- 桌面端与移动端无横向滚动，无明显失衡的空白、表格错位或按钮风格漂移。
- `frontend npm run build` 通过。
- 完成桌面端与移动端逐页截图复核并记录结果。

## 风险与回滚

- 过度隐藏技术字段可能影响支持排障，因此需要区分“运营主视图”与“低显著支持信息”。
- 平台页复用部分通用表格和按钮样式，修改时必须避免误伤 `/admin/*`。
- 如视觉收口导致信息缺失，可回退到保留低显著辅助字段，而不是重新暴露为页面主信息。

## 实现进展

- 当前状态：已完成。
- 已完成项：完成设计上下文加载、平台路由盘点、任务分支与身份门禁；完成 `/platform/login`、`/platform`、`/platform/skills`、`/platform/tools`、`/platform/tenants`、`/platform/website-leads`、`/platform/audit` 的生产化文案与视觉收口；完成桌面端与移动端截图复核并回写状态。
- 未完成项：无。

## 交接说明

- 先看 `AGENTS.md` 中平台控制面与 `impeccable` 的强制规则，再看本 spec。
- 平台页当前问题重点不是功能缺失，而是“生产界面里混入实现信息”和视觉收口不彻底。
- 后续实现必须保留现有业务能力与接口语义，不把本任务扩成新功能重设计。
