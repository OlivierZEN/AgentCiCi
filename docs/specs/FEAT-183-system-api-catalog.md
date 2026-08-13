---
kind: feature-spec
feature_id: FEAT-183
title: 内部生态系统 API 目录
status: implemented
primary_project: agentcici
task_ids: TASK-302
related_integrations: INT-019
updated_at: 2026-08-13T12:59:30Z
updated_by: codex
---

# FEAT-183 - 内部生态系统 API 目录

## 产品目标

运营平台向内部生态应用开发者提供“可被依赖的系统契约”目录，而不是把所有 Controller 自动生成文档。首版只收录账号可访问公司查询与公司上下文切换、身份与 OACT、租户应用激活与交接、Semattice 能力发现、主体同步、元数据读取和记录运行时等跨应用高频核心 API。

## 信息架构与交互

- 能力治理新增一级菜单“系统 API”，下设 `AgentCiCi` 与 `Semattice` 两个提供方入口。
- 首页说明治理边界并展示提供方摘要；提供方页面使用可搜索、可筛选的紧凑列表。
- 点击记录打开宽抽屉，展示用途、调用约束、鉴权、输入输出和常见错误速览。
- 完整调用说明使用独立路由，承载 Schema、请求/响应示例、兼容与回滚说明。
- 不提供在线执行按钮，避免把目录读取权限误解为业务调用授权。

## 事实源与数据契约

- AgentCiCi 条目由 AgentCiCi 后端维护并投影。
- Semattice 条目由 Semattice Capability Registry 与提供方元数据生成，通过内部 HMAC 目录端点交付；AgentCiCi 不复制其 Schema。
- 聚合响应为 `v1`，提供方包含状态和错误说明；单个提供方不可用时其余目录仍可读取。
- 文档示例只使用环境变量和占位凭据，禁止固化环境域名、Secret 或真实租户标识。

## 首批范围

- AgentCiCi：当前账号可访问公司查询、公司上下文切换、SERVICE Token 交换、DevAutopilot activation 查询、handoff 兑换、Semattice 开通预留、Semattice 控制台 handoff 兑换、OACT JWKS。
- Semattice：能力发现、租户状态、主体同步/目录、当前元数据版本、对象列表、记录查询/读取/创建/更新和授权解释。

## 验收标准

1. 平台角色可读取两个提供方目录；未授权用户维持既有拒绝语义。
2. 提供方、分类、协议、scope、风险、版本与状态可在列表快速扫描。
3. URL 驱动列表、抽屉和独立文档页，可刷新、返回和深链。
4. Semattice 暂时不可用时 AgentCiCi 目录仍显示，并明确标记远端不可用。
5. 页面明确说明“出现在目录中不代表自动获得调用权限”。
6. 本地开发环境从 AgentCiCi 与 Semattice 各自本地 `main` 构建并通过跨项目契约与全栈验证。
7. 公司查询不接受客户端指定账号；公司切换重新校验 ACTIVE 成员关系并返回新的公司上下文令牌，调用文档不得把 HUMAN 令牌误写成 SERVICE OACT。

## 回滚

- 回滚 AgentCiCi 任务提交可移除聚合 API、页面和菜单，不影响目录中任何既有业务 API。
- 回滚 Semattice 任务提交只移除只读目录投影，不改变 Capability Registry 或 invoke 路径。

## 实现与验收状态

- 代码、定向测试、前端全量测试和前后端生产构建已经完成。
- AgentCiCi 与 Semattice 本地 `main` 制品已部署到统一开发环境，并完成版本、健康、鉴权边界和提供方投影回读。
- 当前技术实现进入评审；真实平台登录态下的视觉与交互验收不以匿名路由检查替代。
