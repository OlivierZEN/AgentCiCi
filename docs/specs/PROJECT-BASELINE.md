---
updated_at: 2026-04-24T03:15:00Z
status: active
baseline_type: brownfield
---

# Project Baseline

## Scope

- Repo: `cc-cici-assistant`
- Product shape: 企业多组织 AI 助手平台，当前已同时覆盖助手端、管理端、Agent Builder、Skill、知识库、工具治理与部分个人工作流能力。
- Baseline intent: 为后续 brownfield 变更提供共享事实底稿；这里只记录当前系统形态、活跃缺口与需要继续验证的点，不回填全部历史。

## Verified Architecture

- 仓库采用双应用结构：
  - `backend/`: Java 21 + Spring Boot 3 模块化单体
  - `frontend/`: React + Vite
- 状态协作目录使用 `.claw/`，不是 `.ai-dev/`。
- 前端是双入口：
  - `/` 助手工作台
  - `/admin/*` 管理后台
- 平台关键能力已在代码中存在：
  - 多租户鉴权与 `ORG_ADMIN` / `ORG_USER` 权限分层
  - 聊天会话、SSE、RAG、知识库与工具管理
  - Agent Builder 的自然语言 Spec、编译、预览、版本治理骨架
  - Skill registry / binding / authoring 主链路
  - 用户记忆、个人 workflow、系统 MCP server 缓存

## Verified Delivery State

- 最近活跃主线集中在三类工作：
  - Agent Builder / runtime capability 收口
  - Skill Authoring 与 skill 治理
  - MCP/工具/外部集成能力增强
- 最近已验证的结果以 `.claw/test-report.md` 为准，其中 2026-04-23 到 2026-04-24 已记录：
  - 管理端登录与 bootstrap admin 修复测试通过
  - MCP 工具暴露链路相关 integration test 通过
  - Skill Authoring 相关测试通过
  - 前端 build 通过

## Verified Current Gaps

- 聊天运行时未真正绑定到已发布 Agent workflow/version。
- Agent Builder 调试闭环仍未完全以真实后端执行 trace 为主。
- MCP 系统缓存虽已具备代码与测试验证，但缺少成功的真实管理员 smoke。

## Inferred Legacy Understanding

- 该仓库前一阶段主要依赖 `current-status.md` 承载“当前状态 + 历史日志”，导致热文件显著膨胀，任务队列与长期基线没有分层。
- 多份设计文档已覆盖不同子系统，但尚未形成按 feature spec 管理的统一交付结构；现阶段更适合先维护 `PROJECT-BASELINE.md`，再按活跃特性补 feature spec。

## Pending Verification

- 为什么 `13800138111` 登录后仍可能访问管理接口被判定为非 ORG_ADMIN，需要一次完整的本地运行态排查。
- 当前 `/agents/{agentId}/debug` 与前端调试面板之间还有多少模拟逻辑残留，需要复核代码链路后确认。
- Agent 已发布版本的运行时接入最小改造面仍待验证，不应仅凭设计文档推断。

## Active Constraints

- 所有租户敏感路径必须持续带 `org_id`。
- 测试、smoke、运维结论只能记录已真实执行的结果。
- `.claw/current-status.md` 只保留快照；任务拆分与 handoff 以 `.claw/task-board.md` 为准。

## Next Recommended Docs

- 如果开始实现 Agent 运行时绑定已发布版本，先新增独立 feature spec。
- 如果开始系统性收口 MCP 管理链路，也建议补一个 feature spec，避免继续把实现细节散落在状态文件与设计文档之间。
