# FEAT-086 数据洞察与客户洞察解耦热修复

## 背景

TASK-174 错误地把“数据洞察”落在既有 `customer-insight` 应用上，导致客户洞察入口、文案和组件职责被污染。用户明确指出：

- “客户洞察”永远是独立 AI 应用。
- “数据洞察”必须是新增 AI 应用。
- 数据洞察展示的是仪表盘，不得和客户洞察报告/一客一策编辑流程混淆。
- 必须立刻修复错误耦合。

## 目标

1. 恢复“客户洞察”AI 应用入口、文案和报告编辑工作流。
2. 新增独立“数据洞察”AI 应用入口，使用独立应用 code、独立前端模块和独立 API。
3. 数据洞察页面只呈现 CRM 经营仪表盘，不包含客户洞察项目列表、客户洞察章节、客户洞察报告预览或客户洞察生成动作。
4. 生产发布热修复版本，覆盖错误发布。

## 非目标

- 不删除客户洞察既有能力。
- 不把数据洞察写入 `customer-insight` 前端目录或 `/ai/customer-insights/*` API。
- 不新增 CloudCC 元数据或低代码对象。
- 不记录任何密码、token、cookie、secret 或可复用凭据。

## 设计约束

- AI 应用列表必须同时存在“客户洞察”和“数据洞察”。
- 客户洞察入口 code 保持 `customer-insight`，展示客户洞察项目/章节/报告编辑体验。
- 数据洞察入口 code 使用 `data-insight`，展示 CRM 总览仪表盘。
- 数据洞察 API 使用 `/ai/data-insights/dashboard`。
- 数据洞察前端模块使用 `frontend/src/assistant/data-insight/**`。
- 后端数据洞察模块使用 `com.codehouse.ciciassistant.datainsight` 包。

## 验收标准

- `/app?aiApp=customer-insight` 显示“客户洞察”，不出现数据洞察仪表盘。
- `/app?aiApp=data-insight` 显示“数据洞察”，并渲染 CRM 仪表盘。
- `/ai/customer-insights/dashboard` 不再作为客户洞察 API 存在。
- `/ai/data-insights/dashboard` 可返回演示组织真实 CRM 聚合数据或无数据 Mock fallback。
- 后端聚焦测试、前端构建、桌面端浏览器检查通过。

