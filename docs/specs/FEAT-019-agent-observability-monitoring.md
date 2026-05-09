---
kind: feature-spec
feature_id: FEAT-019
title: Agent Observability Monitoring
status: trace_timing_hardened
owner_role: frontend-backend-observability
task_ids: TASK-057
related_decisions: none
related_issues: none
updated_at: 2026-05-07T16:10:00+08:00
updated_by: ai
---

# FEAT-019 - 智能体监控与运行链路追踪

## 背景与目标

当前智能体监控能力已经从偏深色概念大屏收回为 `鎏金账房` 产品观测台，但入口仍放在前台一级菜单，容易把员工日常对话流和管理员排障流混在一起。

本功能要把监控能力归入组织管理端运维体系，形成产品级可用的智能体运行观测台。管理员可以看到组织内智能体运行状态，并查看最近 7 天的运行日志。日志需要覆盖每次会话和任务执行的完整链路追踪，包括与大模型交互明细、工具调用、技能命中、知识库检索、耗时、错误和审计信息。

目标不是做“监控大屏”，而是让管理员或运营人员在工作时间内快速判断：哪个智能体正在运行，哪次会话出了问题，问题发生在模型、工具、技能、知识库还是业务接口。

## 范围

### In Scope

- 将智能体监控页从深色赛博风改为 `鎏金账房` 产品工作台风格。
- 从前台一级菜单移除“智能体监控”，避免普通员工工作台暴露组织级排障入口。
- 将组织级智能体运行观测整合进管理端 `/admin/ops`，与成本用量、审计日志共同构成运维入口。
- 展示当前组织智能体的运行状态。
- 支持最近 7 天运行日志查询，默认按最近活动时间倒序。
- 日志覆盖会话、任务、模型调用、工具调用、技能、知识库、错误和耗时。
- 提供按智能体、状态、时间、日志类型和关键词筛选。
- 提供单次运行链路详情视图，支持逐段查看 trace 节点。
- 明确敏感信息脱敏策略，避免泄露 token、密钥、用户隐私和模型原始内部推理。

### Out Of Scope

- 本轮设计不实现告警规则、短信或飞书主动通知。
- 本轮不做跨租户平台级全局监控大盘。
- 本轮不把组织级监控放到平台端 `/platform/*`，平台端仍聚焦平台技能、内置工具、策略版本和平台审计。
- 本轮不展示模型原始 chain-of-thought，只展示可审计的过程摘要、输入输出摘要和工具结果。
- 本轮不做日志长期归档查询，超过 7 天的检索需要后续归档方案。

## 用户场景

- 组织管理员发现某个智能体回复变慢，需要确认最近是否频繁卡在知识库检索或外部工具调用。
- 运营人员接到用户反馈“技能执行失败”，需要定位某次会话中模型选择了哪个技能，调用了哪些工具，错误发生在哪里。
- 平台实施人员发布新技能后，需要观察最近 7 天是否有运行异常、参数缺失、知识库无命中或模型空响应。
- 普通员工不再在前台一级菜单看到“智能体监控”。如后续需要透明度，可在单条会话或回复旁提供“查看本次执行详情”，仅展示本人会话 trace。

## 现状与约束

- 旧页面入口在 `frontend/src/assistant/AssistantApp.tsx` 的 `workspaceTab === "monitor"` 分支，本轮迁移到管理端 `/admin/ops`。
- 当前样式在 `frontend/src/styles.css` 的 `.cici-monitor*`，存在深色渐变、发光动画、紫蓝青色体系，与 `DESIGN.md` 规则冲突。
- 已有工作台状态源包括 `workbenchRuntimeByAgent`、会话列表、工作台消息和后端 SSE phase。
- 近 7 天链路日志需要后端提供统一查询接口或复用已有消息、工具审计、RAG 检索和技能运行记录后聚合。
- 日志详情必须限制展示原文长度，并按权限与脱敏策略输出。

## 设计原则

视觉场景：组织管理员在白天的办公显示器上排查智能体执行问题，需要长时间阅读表格、时间线和调用明细，界面应明亮、克制、结构清楚。

- 产品寄存器：该页属于 `/admin/*` 认证后组织管理产品面，设计服务排障和运维任务，不做营销页或概念大屏。
- 信息优先：首屏先展示状态、异常、最近运行，再进入单条 trace 详情。
- 三层结构：左侧智能体列表，中间运行日志，右侧链路详情。
- 稳定密度：使用 13px 默认正文、11 到 12px metadata、32 到 34px 紧凑控件。
- 金线结构：香槟金用于边框、active tab、focus 和主操作，不做大面积装饰填充。
- 可审计但不暴露内部推理：展示模型输入输出摘要、参数、工具结果和错误，不展示原始 chain-of-thought。

## 入口归属与页面信息架构

### 导航归属

- 前台 `/`：保留员工日常工作入口，如会话工作台、客户会话、CRM 和个人设置；移除“智能体监控”一级菜单。
- 前台会话上下文：未来可保留轻量“本次执行详情”，只看当前用户自己的 trace，不展示组织级日志列表。
- 管理端 `/admin/ops`：承载组织级“观测与运维”，默认进入智能体运行观测，并通过文本 tab 切换成本用量与审计日志。
- 智能体构建 `/admin/agent-builder/:id`：保留单个智能体的执行记录，用于发布验证和构建治理。
- 平台端 `/platform/*`：只做平台能力治理，不混入普通组织智能体运行日志。

### 管理端运维 tabs

- `智能体运行`：组织级智能体状态、最近 7 天运行日志、链路追踪详情。
- `成本用量`：组织级调用次数和成本估算。
- `审计日志`：组织管理员可见的近期审计事件。

## 智能体运行页结构

### 顶部状态栏

- 页面标题：`智能体监控`
- 支持文案：`查看当前运行状态与最近 7 天链路日志`
- 紧凑指标：
  - 在线智能体
  - 运行中
  - 异常或待确认
  - 7 天会话数
  - 平均响应耗时
- 工具区：
  - 时间范围：默认近 7 天，不允许超过 7 天
  - 刷新按钮
  - 搜索输入

### 左侧智能体状态列表

每个智能体行展示：

- 头像和名称
- 状态：待命、运行中、等待确认、异常、已完成
- 当前任务摘要
- 近 7 天会话数、失败数、平均耗时
- 最后活动时间

交互：

- 点击智能体过滤中间日志列表。
- active 状态使用文本强化和金色细边，不使用大色块。
- 异常状态只用语义红文本和浅红底小标签。

### 中间运行日志列表

默认展示最近 7 天所有运行记录，按最近更新时间倒序。

每条日志展示：

- 会话或任务标题
- 运行状态
- trace id 短码
- 触发渠道：Web、飞书、企微、钉钉或定时任务
- 关键链路摘要：模型、技能、工具、知识库
- 开始时间、结束时间、总耗时
- 未读、失败、待确认标记

列表上方提供文本 tab：

- 全部
- 运行中
- 异常
- 待确认
- 工具调用
- 知识库检索

### 右侧链路详情

选中一条日志后展示完整 trace：

- Trace 概览：trace id、agent、session、用户、渠道、总耗时、状态。
- 链路时间线：
  - 用户输入
  - 意图识别
  - 技能选择
  - 知识库检索
  - 模型请求
  - 工具调用
  - 模型总结
  - 消息落库或任务完成
- 明细分组：
  - 大模型交互：模型供应商、模型名、温度、输入摘要、输出摘要、token、耗时、错误。
  - 工具调用：工具名、参数摘要、结果摘要、耗时、状态、错误。
  - 技能：命中技能、技能版本、触发方式、输出契约检查。
  - 知识库：知识库名称、命中片段数、fallback、检索耗时。

## 数据模型建议

### AgentRuntimeSnapshot

```ts
type AgentRuntimeSnapshot = {
  agentId: string;
  agentName: string;
  avatarBase64?: string;
  status: "IDLE" | "RUNNING" | "WAITING_CONFIRMATION" | "FAILED" | "COMPLETED";
  currentTask?: string;
  activeSessionCount: number;
  sevenDaySessionCount: number;
  sevenDayFailureCount: number;
  avgLatencyMs: number;
  lastActiveAt?: string;
};
```

### AgentRunLog

```ts
type AgentRunLog = {
  traceId: string;
  sessionId: string;
  agentId: string;
  title: string;
  channel: "web" | "feishu" | "wecom" | "dingtalk" | "scheduled";
  status: "RUNNING" | "WAITING_CONFIRMATION" | "FAILED" | "COMPLETED";
  startedAt: string;
  endedAt?: string;
  elapsedMs: number;
  modelCallCount: number;
  toolCallCount: number;
  activatedSkillCodes: string[];
  boundSkillCodes: string[];
  knowledgeBaseNames: string[];
  summary: string;
};
```

### AgentTraceDetail

```ts
type AgentTraceDetail = {
  traceId: string;
  sessionId: string;
  agentId: string;
  status: string;
  elapsedMs: number;
  nodes: Array<{
    id: string;
    type: "USER_MESSAGE" | "SKILL_RESOLVE" | "SKILL" | "RAG" | "TOOL_SCHEMA" | "MODEL" | "TOOL" | "WORKFLOW" | "PERSISTENCE";
    title: string;
    status: "PENDING" | "RUNNING" | "SUCCESS" | "FAILED" | "SKIPPED";
    startedAt: string;
    endedAt?: string;
    elapsedMs?: number;
    summary: string;
    metadata?: Record<string, unknown>;
  }>;
};
```

### Trace Timing Semantics

- `elapsedMs` at run-log level is total wall-clock duration from request entry to trace persistence.
- Timeline nodes use their own measured stage duration. `MODEL` nodes must not reuse total run duration.
- Model calls are split by phase:
  - `tool_planning`: model call used to decide whether tools are needed.
  - `tool_planning_stop`: model call that stops requesting tools before final generation.
  - `final_stream`: final streamed assistant response.
  - `final_completion`: final non-stream assistant response.
- Tool nodes use per-tool execution duration captured around `toolOrchestratorService.executeTool`.
- Skill observability distinguishes:
  - `boundSkillCodes`: skills bound or pinned on the current Agent/runtime and available as candidates.
  - `activatedSkillCodes`: skills actually activated this turn, either by active skill context or by a tool call mapped to a skill whitelist.
  - `skillNames` remains an API compatibility alias for `activatedSkillCodes`.
- The UI must label bound skills as candidates or bindings, not as “命中技能”.

## 接口建议

- `GET /me/agents/runtime-snapshots`
  - 返回当前用户可见智能体状态。
- `GET /me/agents/run-logs?from=&to=&agentId=&status=&type=&q=&cursor=`
  - 返回最近 7 天运行日志。
- `GET /me/agents/run-logs/{traceId}`
  - 返回单次运行链路详情。
- `GET /admin/agents/run-logs?from=&to=&agentId=&status=&type=&q=&limit=`
  - 返回当前组织最近 7 天运行日志，需 `ORG_ADMIN`。
- `GET /admin/agents/run-logs/{traceId}`
  - 返回当前组织单次运行链路详情，需 `ORG_ADMIN`。

管理端使用 `/admin/agents/run-logs` 作为组织级事实源；前台如果后续恢复“本次执行详情”，仍使用 `/me/agents/run-logs/{traceId}` 并保持用户级权限边界。

## 脱敏与安全

- 默认隐藏 token、api key、Authorization、accessToken、refreshToken、cookie、密码和手机号中间位。
- 模型输入输出展示摘要和可展开片段，单段默认不超过 800 字。
- 工具参数默认展示结构化摘要，高风险字段替换为 `[redacted]`。
- 知识库命中展示知识库名、文档名和片段摘要，不直接暴露不可见知识库全文。
- 不展示原始 chain-of-thought，只展示系统可审计事件摘要。

## 效果图说明

静态效果图文件：

- HTML mockup: `docs/specs/mockups/agent-observability-monitoring.html`
- PNG screenshot: `docs/specs/mockups/agent-observability-monitoring.png`

效果图体现：

- 顶部紧凑状态和过滤工具。
- 左侧智能体状态列表。
- 中间最近 7 天运行日志。
- 右侧选中 trace 的模型、工具、技能、知识库明细。
- 全部使用 `鎏金账房` 的暖象牙、墨色文字和金色结构线。

## 任务拆分

- `TASK-057A`: 前端重构监控页布局与 `鎏金账房` 样式。
- `TASK-057B`: 后端运行日志聚合接口和 7 天查询范围。
- `TASK-057C`: trace detail schema、脱敏策略和前端详情面板。
- `TASK-057D`: SSE phase、RAG、工具调用和技能运行记录的 trace id 串联。
- `TASK-057E`: 单元测试、接口集成测试、前端构建和视觉验收。

## 验收标准

- 页面不再出现深色赛博背景、蓝紫渐变、发光大屏和英文概念标题。
- 首屏可以看到所有可见智能体当前状态和核心运行指标。
- 默认可以查询最近 7 天运行日志，超出 7 天范围时前端提示并修正。
- 选中任意日志后，右侧能看到完整链路节点和模型、工具、技能、知识库明细。
- 日志详情不泄露敏感 token、密钥或不可见知识库全文。
- 移动端降级为单列：状态列表、日志列表、详情分段依次排列。
- `frontend npm run build` 通过。
- 后端新增接口有权限、时间范围和脱敏相关测试。

## 风险与回滚

- 风险：链路日志散落在消息、工具审计、RAG 和技能运行记录中，首版可能无法补齐历史 trace。
  - 缓解：首版只保证新运行记录完整，历史记录显示可用字段和“链路不完整”提示。
- 风险：日志详情内容过长导致页面卡顿。
  - 缓解：节点懒加载或详情摘要优先，原始片段折叠展示。
- 风险：权限边界复杂。
  - 缓解：先实现 `/me` 范围，管理员全组织视角后续单独扩展。
- 回滚：保留原 `workspaceTab === "monitor"` 入口，前端可通过 feature flag 切回旧组件，但默认视觉应以本设计为准。

## 实现进展

- 已完成设计文档。
- 已完成静态效果图 mockup。
- 已按效果图实现正式前台监控页：
  - `frontend/src/assistant/AssistantApp.tsx` 的 `workspaceTab === "monitor"` 分支已改为顶部指标、筛选工具、左侧智能体状态、中间 7 天运行日志、右侧链路追踪三栏结构。
  - `frontend/src/styles.css` 的 `.cici-monitor*` 已从深色赛博风替换为 `鎏金账房`：暖象牙表面、墨色文字、香槟金结构线、文本 tab、紧凑状态标签和响应式单列降级。
  - 页面支持左侧智能体筛选、日志搜索、日志选中后更新右侧 trace 详情，以及刷新状态采样。
- 已按用户反馈移除前端伪造链路字段：不再合成 trace id、模型名、RAG、工具、技能、节点数、随机耗时或模拟时间线。
- 已实现真实运行日志聚合接口：
  - 新增 `agent_run_trace` 表与 `AgentRunTraceService`，普通与流式聊天完成后写入统一 trace。
  - 新增 `GET /me/agents/run-logs` 与 `GET /me/agents/run-logs/{traceId}`，返回当前用户可见的最近 7 天运行日志与详情。
  - trace 节点覆盖用户输入、RAG、技能上下文、工具调用、模型生成、技能/工作流治理和消息落库。
- 已优化流式工具链路的模型耗时：
  - 单个只读查询工具成功返回、用户意图仍为查询/汇总且结果不要求继续工具时，后端会跳过第二次模型工具规划收口并记录 `tool_planning_stop_skipped`。
  - 未跳过的收口判断使用短输出提示，只让模型判断是否继续工具；最终流式生成不再携带 tools schema，避免工具定义重复进入最终回答上下文。
- 监控页已改读 `/me/agents/run-logs`；选中日志后读取 trace detail 展示模型、工具、技能、知识库和节点摘要。
- 历史会话在没有细粒度 trace 时会通过 `chat_session` / `chat_message` 回填为 message-only 记录，不展示未验证的工具/RAG/耗时。
- 已按用户最严格 UI 反馈移除内部背景框和框套框：搜索框内部、tab、日志行、选中态、状态文字、链路详情分组、空态均改为透明背景和最小必要线条。
- 已同步项目 UI 规范到 `DESIGN.md` / `DESIGN.json` / `AGENTS.md` / `README.md`，明确产品面板内部不得再加背景框。
- 本轮验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` 成功；`frontend npm run build` 成功，保留既有 Vite chunk-size warning；`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=AgentRunTraceIntegrationTest test` 成功；`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatOrchestratorServiceModelIdentityTest test` 成功；`git diff --check` 成功。

## 交接说明

- 下一位接手者先看本文件和 `docs/specs/mockups/agent-observability-monitoring.html`。
- 前端页面已按效果图落地，并已接入真实后端运行日志聚合接口。
- 新 trace 已有逐工具耗时字段；本次性能优化尚未跑真实 DashScope 单工具查询 smoke，后续需确认 trace 出现 `模型工具规划收口跳过` 且最终生成输入 token 下降。
