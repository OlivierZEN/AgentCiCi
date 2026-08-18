---
kind: feature-spec
feature_id: FEAT-193
title: 应用中心在线接入指南
status: implemented
owner_role: frontend-agent
task_ids: TASK-316
related_decisions: DEC-055
related_issues: none
updated_at: 2026-08-18T02:41:39Z
updated_by: codex
---

# FEAT-193 - 应用中心在线接入指南

## 背景与目标

应用中心已经具备应用登记、运行连接、版本依赖、发布和租户生命周期控制面，但应用开发者仍需阅读实现代码或依赖口头说明才能完成 Provider 接入。此信息缺口容易造成开发者把真实服务地址写入版本、把 Secret 原文录入平台、遗漏幂等处理，或在运行连接尚未测试启用时提前发布版本。

本功能在运营平台内提供一份可在线查阅的详细接入指南。指南按真实控制面流程解释每一步“做什么、为什么、完成标志是什么”，并提供可复制的 Provider 请求、响应、HMAC 签名和发布前检查示例，让应用开发者与平台管理员能够使用同一事实源完成联调。

## 用户与场景

- 应用开发者：实现健康检查和租户生命周期 Provider，完成鉴权、幂等、响应状态和错误处理。
- 平台管理员：登记应用、配置受管运行连接、测试并启用修订、创建版本与依赖、验证并发布。
- 联调负责人：依据请求示例、验收清单和错误码定位 Provider 与控制面问题。

## 范围

### In Scope

- 新增认证后的独立路由 `/platform/internal-applications/integration-guide`。
- 应用中心列表页和应用详情页提供“接入指南”入口；应用详情携带来源应用上下文，指南可返回原应用。
- 运行连接空态提供“查看连接配置步骤”入口并定位到相应章节。
- 指南覆盖架构边界、前置条件、应用登记、Provider 契约、鉴权与 Secret 引用、运行连接、版本初始化步骤、应用依赖、验证发布、租户开通、生命周期运维、错误排查和发布前检查。
- 示例使用保留测试域名，不包含任何本地、UAT、生产或客户私有化环境地址。
- 代码示例支持复制，并提供可访问的复制结果反馈。

### Out of Scope

- 不修改 Provider 生命周期后端契约、数据库模型或平台权限。
- 不自动创建应用、运行连接、版本或租户 activation。
- 不提供真实 Secret、环境变量值或部署地址。
- 不修改 Semattice、DevAutopilot 或其他子仓。
- 不新增移动端布局或移动端验收。

## 信息架构

页面采用紧凑的双栏技术手册结构：左侧为粘性章节目录和接入阶段摘要，右侧为连续正文。正文不使用营销式 Hero 或卡片网格，以编号章节、1px 结构线、事实表、代码块和检查项构建阅读层级。

章节顺序：

1. 接入全景与职责边界。
2. 接入前准备。
3. 登记应用。
4. 实现 Provider 生命周期接口。
5. 配置鉴权与 Secret 引用。
6. 创建、测试并启用运行连接。
7. 创建版本和初始化步骤。
8. 声明应用依赖。
9. 验证并发布版本。
10. 租户开通与运行观测。
11. 暂停、恢复、校准与升级。
12. 常见问题和发布前检查。

## 契约事实

- 浏览器只调用 AgentCiCi 同源平台 API；Provider 调用由平台后端发起。
- `PUBLIC_HTTPS` 必须使用 HTTPS 且不能解析到私网、回环、链路本地或元数据地址；`PLATFORM_INTERNAL` 才允许内部 HTTP。
- 生命周期请求包含 `operationId`、`idempotencyKey`、`operationType`、`companyId`、`appCode`、`applicationVersion`、`contractVersion`、`dependencies`、`stepCode` 和 `capability`。
- 请求头包含 `Idempotency-Key: <operationId>:<stepCode>` 与 `X-Correlation-Id: <operationId>`。
- Provider 必须返回 JSON，`status` 仅接受 `SUCCEEDED`、`ACTIVE` 或 `SUSPENDED`；响应上限 1 MiB。
- HMAC canonical string 为 `agentcici\nMETHOD\nPATH\nTIMESTAMP\nNONCE\nSHA256_HEX(BODY)`，签名为 HMAC-SHA256 小写十六进制。
- Secret 原文只能由运行环境按 `app.platform.provider-secrets.<secret_ref>` 解析；控制面只保存引用。
- 已发布版本不可修改，运行连接修订不可原地覆盖；改变环境或网络范围需使用新的 `binding_key`。

## 交互与可访问性

- 页面标题和章节使用语义化 heading；目录使用锚点并允许键盘操作。
- 代码块提供明确的复制按钮，成功后使用 `aria-live` 反馈。
- 返回动作保留来源应用上下文；无来源时返回应用中心。
- 所有图标按钮均有可见文字或无障碍名称。

## 验收标准

1. 已登录平台管理员可从应用中心列表和任一应用详情打开在线接入指南。
2. 连接空态可直接跳转到运行连接章节。
3. 指南完整覆盖应用开发者与平台管理员的端到端接入步骤，并准确反映当前代码契约。
4. 示例不包含真实环境域名、Token、Secret 或私钥；Secret 原文边界有明确说明。
5. 所有示例代码可复制，复制失败不会阻断阅读。
6. 页面保持鎏金账房产品视觉、桌面端紧凑密度和清晰阅读层级。
7. 定向测试、前端全量测试和 production build 通过。
8. 从本地 `main` 构建前端并更新 `https://cici.localhost/`，回读路由、容器健康、重启次数和版本指纹。

## 回滚

- 回滚前端路由、入口和页面文件即可；不会改变任何应用、连接、版本或租户数据。
- 若指南内容暂时不可用，现有应用登记、连接、版本和生命周期操作继续保持原行为。
