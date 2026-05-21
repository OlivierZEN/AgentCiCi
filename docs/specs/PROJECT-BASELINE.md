---
kind: project-baseline
title: Project baseline
version: 3
updated_at: 2026-05-17T02:16:38Z
updated_by: ai
status: active_reference
owner_role: shared
baseline_type: brownfield
---

# Project Baseline

## Scope

- Repo: `cc-cici-assistant`
- Product shape: AgentCiCi 企业级多组织智能体运行与治理平台，当前已同时覆盖助手端、管理端、平台治理控制面、Agent Builder、Skill、知识库、工具治理、外部系统/渠道集成与部分个人工作流能力。
- Brand stance: AgentCiCi 是独立产品品牌，`agentcici.com` 为品牌域名；CloudCC、Salesforce、企业微信、飞书等是集成对象，不作为主品牌标识。
- Baseline intent: 为后续 brownfield 变更提供共享事实底稿；这里只记录当前系统形态、活跃交付面、关键入口与需要继续验证的点，不回填全部历史。

## Verified Architecture

- 仓库采用双应用结构：
  - `backend/`: Java 21 + Spring Boot 3 模块化单体
  - `frontend/`: React + Vite
- 状态协作目录使用 `.claw/`，不是 `.ai-dev/`。
- 前端当前至少有三类主要入口：
  - `/` 助手工作台
  - `/admin/*` 管理后台
  - `/platform/*` 平台治理控制面
- 平台关键能力已在代码中存在：
  - 多租户鉴权与 `ORG_ADMIN` / `ORG_USER` 权限分层
  - 聊天会话、SSE、RAG、知识库与工具管理
  - Agent Builder 的自然语言 Spec、编译、预览、版本治理骨架
  - Skill registry / binding / authoring 主链路
  - 用户记忆、个人 workflow、系统 MCP server 缓存
  - 平台 Skill 模板、PolicyBundle、平台工具治理与平台审计
- Agent / Skill / Tool 运行时权限语义（直接绑定 vs Skill 声明、运行时并集、分层 API 字段）见 `docs/agent-skill-tool-permission-model.md` §12；交付追踪见 `.claw/task-board.md` `TASK-019`。

## Verified Delivery State

- 最近活跃主线集中在三类工作：
  - Agent Builder / runtime capability 收口
  - Skill Authoring 与 skill 治理
  - MCP/工具/外部集成能力增强
- 最近已完成并验证的结果以 `.claw/test-report.md` 为准，其中 2026-04-29 到 2026-04-30 已明确覆盖：
  - FEAT-009 Skill 分层治理、PolicyBundle、已发布 Agent skill pin 与 runtime governance 摘要
  - FEAT-010 平台 Skill / Tool 治理控制面、平台审计与运行时紧急禁用
  - Agent Builder 调试入口已切到后端真实运行优先，前端仅保留接口失败时的模拟兜底
  - FEAT-008 知识库生命周期第二阶段能力已基本落地，但当前按用户要求暂停在人工回归前
  - 项目状态协议已按 `cc-aidev-guidelines-common` `3.7.0` 刷新并验证：`README.md`、`AGENTS.md` 托管声明块存在，`.claw/` 八个核心状态文件保留，`.claw/integration-queue.md`、`.claw/team-status.md`、异步并行目录骨架以及既有团队身份记录均已保留。

## Verified Current Gaps

- CloudCC 外部集成当前仍缺最后一轮真实工具闭环：MCP health 与缓存、组织网关解析都已成功，但真实绑定用户 token 换取仍失败。
- `/ai/chat` 的 CloudCC 查询 smoke 还受本地 Aliyun API key 缺失阻塞，导致模型链路在工具调用前终止。
- `PlatformPolicyBundle` 当前已支持独立草稿、发布、回滚和调试摘要，但仍是全局即时生效；如继续推进，需要补 rollout group 编排与更细粒度灰度策略。
- 知识库生命周期 P0 第一阶段已闭环；第二阶段能力已大体落地，但暂停在人工回归与失败态 UX 收尾前。
- SaaS 计费仍停留在设计层，尚未落地 `usage_meter_event`、套餐/订阅、账单与超额策略实体。

## Inferred Legacy Understanding

- 该仓库前一阶段长期把 `current-status.md` 当作“状态 + 历史日志”混合文件使用，因此新会话需要先依赖 `.claw/task-board.md` 和本 baseline 才能避免上下文漂移。
- 多份设计文档已覆盖不同子系统，但 feature spec 的完备度仍不均衡；对于“实现中但还未独立成 spec”的运维/集成问题，当前仍需通过 task card + issue list 组合承接。

## Pending Verification

- 轮换 `13800000001/哪吒` 的 `cc_username/cc_safetymark` 后，CloudCC `api/cauth/token` 是否恢复成功。
- 本地补齐可用 Aliyun API key 后，`sales-agent` 的 CloudCC 查询类问题是否会真实触发 CloudCC 工具。
- 若继续推进平台治理，PolicyBundle 发布/回滚后的 runtime governance 摘要在 `/ai/chat`、`/agents/{agentId}/debug` 与平台页是否仍保持一致。

## Legacy Hotspots

- `backend` CloudCC 相关集成与配置链路：当前真实阻塞集中在外部凭证与模型入口配置，修复时要区分“配置问题”和“代码问题”。
- `frontend` 平台控制面及其 `/api/platform/**` 代理：本轮已修复路由/接口前缀冲突，后续改动需谨慎保持 dev proxy 与页面路由分离。
- `.claw/current-status.md`：已收口为快照，但历史上内容膨胀过；后续不要再把详细测试日志和任务流水写回这里。

## Key Entry Points

- Code entry:
  - `backend/src/main/java`
  - `frontend/src`
- Runtime / integration entry:
  - `backend/src/main/resources/application-local.yml`
  - `/admin/integrations`
  - `/mcp-servers`
- Test entry:
  - `.claw/test-report.md`
  - `backend` 集成测试：`OrchestratorIntegrationTest`、`PlatformGovernanceIntegrationTest`、`SkillGovernanceIntegrationTest`、`KnowledgeBaseLifecycleIntegrationTest`
- Ops entry:
  - `.claw/devops.md`
  - `docker-compose.yml`
  - `scripts/`

## Active Delivery Surface

- `TASK-023`：CloudCC runtime smoke unblock，当前最高优先级。
- `TASK-020`：FEAT-008 暂停，待用户恢复后再继续人工回归。
- `TASK-007`：SaaS 计费仍处于设计阶段，暂未进入实现。

## Adoption Plan

- 继续以 `.claw/` 作为唯一状态目录，不启用 `.ai-dev/` 双写。
- 每次会话默认先读 `.claw/current-status.md`，再按需读 `.claw/task-board.md`、`.claw/issue-list.md`、`.claw/test-report.md`。
- 非平凡新功能继续优先补独立 feature spec；运维/集成型任务先通过 task card + issue list 承接。

## Handoff Notes

- 新接手者优先看 `.claw/current-status.md`、`.claw/task-board.md`、`.claw/issue-list.md`。
- CloudCC 相关排障先区分“用户凭证问题”和“模型入口配置问题”，不要在两者都未修复时直接判断工具编排有 bug。
- 测试、smoke、运维结论只记录真实执行结果；长命令与长日志沉淀到 `.claw/test-report.md`，不要回流到快照文件。
