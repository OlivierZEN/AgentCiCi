---
kind: feature-spec
feature_id: FEAT-066
title: Agent Builder production readiness closure
status: in_implementation
owner_role: fullstack-agent
task_ids: TASK-156
related_decisions: FEAT-004, FEAT-021, FEAT-031, FEAT-036, FEAT-042
updated_at: 2026-06-20T16:10:12Z
updated_by: MANAGER-001
---

# FEAT-066 - Agent Builder 生产闭环收口

## 背景与目标

Agent Builder 当前已经具备 Agent 定义、自然语言 Spec、编译、版本、发布、回滚、Skill 绑定、知识库绑定、Open API、触发器摘要、执行记录、访问控制和部分运行观测。现在的缺口不再是“能否创建 Agent”，而是能否把一个 Agent 可靠地带到生产运行。

本特性目标是把 Agent Builder 收口为生产闭环：

- 创建和编辑：草稿、Spec、模型、知识库、Skill、Tool、渠道和权限配置可稳定保存。
- 编译和调试：每次编译生成可追溯版本，调试运行可见 runtime governance、RAG、工具、错误和输出。
- 发布和回滚：发布前有 readiness gate，发布后运行只使用已发布快照，回滚可追溯。
- 接入和运行：Web、Open API、渠道绑定、触发器和计划任务都能从同一发布版本运行。
- 观测和审计：运行日志、调用日志、权限变更、发布事件、失败原因和计量可定位到版本。
- 评测和回归：发布前可运行评测集，关键安全和 P0 用例失败时阻止发布。

## 当前已具备

- `AgentDefinitionService` 已支持 Agent 创建、更新、Spec 保存、绑定、版本发布和回滚。
- `AgentRuntimeController` 已提供 runtime triggers、executions、schedule sync 和 run-now。
- `AgentAccessControlService` 与 FEAT-042 已接入 Agent 列表、详情、编辑、发布、Open API 和日志读取权限。
- FEAT-036 已实现 Open API 会话服务增强，外部调用可复用运行时。
- FEAT-004 已把触发器和执行记录接到编译/版本语境。

## 生产缺口

| 缺口 | 影响 | 优先级 |
|---|---|---|
| 发布前无统一 readiness gate | 未编译完整、未绑定模型、未配置入口、评测失败或权限不完整时仍可能发布 | P0 |
| FEAT-031 评测系统仍停留在设计态 | 不能证明新版 Agent 比旧版稳定，也不能阻止安全回归 | P0 |
| 发布响应缺少生产就绪摘要 | 前端和外部运维难以判断发布阻塞原因、最近评测、入口状态和风险 | P0 |
| Open API / 渠道 / schedule readiness 分散 | 创建 Key、启用渠道、发布版本和触发器状态没有统一检查面 | P1 |
| 版本快照证据不完整 | 发布时需要固定 skill refs、policy bundle、KB 设置摘要、模型路由和入口配置摘要 | P1 |
| 真实 trace 回流评测未落地 | 线上失败无法低成本沉淀成回归用例 | P1 |

## 设计方案

### 1. 发布 readiness gate

新增 Agent 发布前检查服务，发布前至少检查：

- 目标版本存在，且编译产物包含 workflow code、manifest 和 preview graph。
- Agent 启用，当前用户有 `PUBLISH` 权限。
- 至少有一个可用运行入口：助手端、Open API、渠道或 schedule。
- 模型路由可用，不是占位模型。
- 绑定知识库处于 `ACTIVE`，已绑定文档无清理失败。
- 绑定 Skill / Tool 当前可用，且运行时策略未禁用。
- Open API channel 启用时，至少一个 active API key 或明确允许发布后再创建。
- 若启用评测门禁，最近一次评测必须满足 gate policy。

发布接口应返回：

```json
{
  "published": false,
  "blocked": true,
  "readiness": {
    "status": "blocked",
    "checks": [
      {"code": "evaluation_gate", "status": "failed", "severity": "blocker"}
    ]
  }
}
```

### 2. 最小评测闭环

首阶段不追求复杂报表，先落地发布门禁需要的最小能力：

- 评测集、用例、运行、结果表。
- 手动运行评测，复用 `ChatOrchestratorService`。
- 确定性断言：答案包含/不包含、RAG 命中、工具调用/禁止调用、安全拒答、人工接管关键词。
- 发布门禁：P0 或 safety 用例失败时阻止发布；普通用例可按阈值阻止或警告。
- 运行 trace 标记 `runMode=EVALUATION`。

### 3. 版本和运行证据

发布版本需要形成统一证据：

- version id / version no / published at / actor。
- pinned skill refs 和 policy bundle version。
- KB 绑定摘要：KB id、名称、检索策略、embedding 模型、更新时间。
- Tool 和 channel 摘要。
- 最近评测 run id、pass rate、安全通过率。
- Open API、schedule 和渠道 readiness。

### 4. 前端收口

Agent Builder 需要把现有分散信息汇总为同一生产闭环：

- 发布按钮旁展示 readiness 状态。
- 发布阻塞时打开检查清单，而不是只 toast。
- 新增或增强“评测”入口：评测集、用例、运行结果、失败原因。
- 发布成功后展示生产入口摘要：Open API、渠道、触发器、执行记录。

## 任务拆分

- `TASK-156A`：发布 readiness gate 和后端摘要 API。
- `TASK-156B`：最小评测数据模型、API、断言引擎和发布门禁。
- `TASK-156C`：Agent Builder 前端 readiness / evaluation / publish UX。
- `TASK-156D`：Open API、schedule、channel、trace、billing 的发布证据串联。
- `TASK-156E`：集成测试、前端 build、桌面截图、生产 smoke 手册。

## 验收标准

- 一个新建 Agent 可以完成：创建 -> 保存 Spec -> 绑定 KB/Skill/Tool -> 编译 -> 调试 -> 运行评测 -> 通过 gate -> 发布 -> Open API 或渠道调用 -> 查看执行记录 -> 回滚。
- 发布前 readiness gate 对缺模型、缺版本、无入口、绑定资源失效、评测失败给出明确阻塞原因。
- 评测系统支持至少一组 Agent 评测集，P0/safety 用例失败时发布被阻止。
- 发布版本可追溯到 pinned skill refs、policy bundle、KB 摘要、模型路由和评测 run。
- 运行日志和 Open API 调用日志能定位到 agentId、versionNo、traceId、requestId 和失败原因。
- 验证至少包含后端集成测试、`frontend npm run build`、`git diff --check` 和 Agent Builder 桌面端截图。

## 非目标

- 不做复杂团队审批流。
- 不做跨租户模板市场发布流程。
- 不做完整 ML 实验平台或人工标注系统。
- 不新增移动端兼容实现或移动端验收。

## 风险与回滚

- 风险：一次性实现全部 FEAT-031 会扩大范围。缓解：先做发布门禁所需的最小评测闭环。
- 风险：发布 gate 误阻塞现有客户。缓解：先支持 `warnOnly`，但 production-ready 验收必须有阻塞模式。
- 回滚：readiness gate 可配置为 warn-only；新增评测表保留，不影响现有 Agent 运行。

## 实现进展

- 2026-06-20T16:10:12Z：
  - 新增 `AgentProductionReadinessService`，基于现有版本、模型路由、KB 绑定、Tool 绑定、渠道、schedule 和 Open API Key 生成生产就绪检查。
  - `GET /agents/{agentId}/readiness` 已返回 readiness 状态、检查项和摘要。
  - `POST /agents/{agentId}/publish` 发布前调用 readiness gate；存在 blocker 时返回 `409 Conflict`，发布成功响应携带 readiness 摘要。
  - 新增 `AgentProductionReadinessIntegrationTest` 覆盖无生产入口阻止发布和 Web 入口发布响应携带 readiness；当前本地真实执行被 PostgreSQL/Docker 未启动阻塞，测试代码编译已通过。
