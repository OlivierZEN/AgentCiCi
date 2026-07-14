---
kind: task-board
version: 4
updated_at: 2026-07-14T23:22:06Z
updated_by: MANAGER-001
board_status: active
---

# Task Board

`task-board.md` is a compact index. Historical task cards are archived in `.claw/task-archive.md`.

Recommended statuses: `todo` / `ready` / `in_progress` / `blocked` / `review` / `done` / `canceled`
Recommended priorities: `critical` / `high` / `medium` / `low`

## Active Tasks

### TASK-211 - CRM 确定性回答真实流式输出纠偏

- status: `ready`
- priority: `critical`
- owner_role: `backend-agent`
- spec_path: `docs/specs/FEAT-114-crm-product-sales-analysis-hardening.md`
- task_status_path: `.claw/tasks/TASK-211.md`
- assignment_path: `.claw/assignments/TASK-211.yaml`
- blocked_by: `none`
- next_action: Await written FEAT-114 review, then implement server-side multi-delta CRM streaming with TDD and production acceptance.

### TASK-210 - 客户互动工作台标准渠道图标治理

- status: `in_progress`
- priority: `high`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/FEAT-116-customer-workbench-standard-channel-icons.md`
- task_status_path: `.claw/tasks/TASK-210.md`
- assignment_path: `.claw/assignments/TASK-210.yaml`
- blocked_by: `none`
- next_action: Production runs integrated `2.7.5` with TASK-210 preserved; complete its independent AgentCiCi plus CloudCC embed visual evidence and close TASK-210.

### TASK-207 - 前台主题一致性与视觉对齐全量治理

- status: `done`
- priority: `critical`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/FEAT-113-frontend-theme-consistency-and-alignment.md`
- task_status_path: `.claw/tasks/TASK-207.md`
- assignment_path: `.claw/assignments/TASK-207.yaml`
- blocked_by: `none`
- next_action: Done on `codex/TASK-207-frontend-theme-alignment-audit`; merge after review, with production release handled separately.

### TASK-206 - CloudCC 嵌入身份同步自动恢复

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-112-cloudcc-embed-sso-recovery.md`
- task_status_path: `.claw/tasks/TASK-206.md`
- assignment_path: `.claw/assignments/TASK-206.yaml`
- blocked_by: `none`
- next_action: Done in production `2.6.11`; monitor CloudCC session validation, identity mapping rejects and ticket/consume success rates.

### TASK-205 - CRM 经营分析 Skill 与产品销售演示数据

- status: `done`
- priority: `critical`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-111-crm-business-analysis-skill.md`
- task_status_path: `.claw/tasks/TASK-205.md`
- assignment_path: `.claw/assignments/TASK-205.yaml`
- blocked_by: `none`
- next_action: Done in production `2.6.8 / 095094300a25`; monitor the deterministic CRM sales-rank route and expand only through governed high-level analysis tools.

### TASK-204 - 智能体构建说明与头像交互精修

- status: `ready`
- priority: `high`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/FEAT-110-agent-builder-guide-avatar-polish.md`
- task_status_path: `.claw/tasks/TASK-204.md`
- assignment_path: `.claw/assignments/TASK-204.yaml`
- blocked_by: `none`
- next_action: User reviews the written FEAT-110 design, then implementation starts on the assigned branch.

### TASK-203 - 客户互动工作台全场景演示数据

- status: `in_progress`
- priority: `critical`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-109-customer-workbench-comprehensive-demo-scenarios.md`
- task_status_path: `.claw/tasks/TASK-203.md`
- assignment_path: `.claw/assignments/TASK-203.yaml`
- blocked_by: `none`
- next_action: Extend the idempotent V2 seed, back up production, write CRM/AgentCiCi demo facts, and verify every queue and detail scenario as Owen/SalesA.

### TASK-202 - 用户级产品主题偏好

- status: `done`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-108-user-selectable-product-themes.md`
- task_status_path: `.claw/tasks/TASK-202.md`
- assignment_path: `.claw/assignments/TASK-202.yaml`
- blocked_by: `none`
- next_action: Done in production `2.6.6`; monitor theme switching, transparent structural wrappers and fixed avatar geometry.

### TASK-201 - 智能体构建页布局与模型治理收敛

- status: `done`
- priority: `high`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/FEAT-107-agent-builder-layout-and-model-governance.md`
- task_status_path: `.claw/tasks/TASK-201.md`
- assignment_path: `.claw/assignments/TASK-201.yaml`
- blocked_by: `none`
- next_action: Merged to `origin/main`; production release was not requested.

### TASK-200 - 多租户智能体评测控制面生产落地

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-106-multi-tenant-agent-evaluation-control-plane.md`
- task_status_path: `.claw/tasks/TASK-200.md`
- assignment_path: `.claw/assignments/TASK-200.yaml`
- blocked_by: `none`
- next_action: Done in production `2.6.4`; monitor evaluation coverage, P0/security regressions, stale results and release-gate outcomes.

### TASK-199 - 互动驱动的客户经营动作

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-105-interaction-driven-customer-actions.md`
- task_status_path: `.claw/tasks/TASK-199.md`
- assignment_path: `.claw/assignments/TASK-199.yaml`
- blocked_by: `TASK-198`
- next_action: Done in production `2.6.2`; monitor candidate precision, dismissal feedback, deduplication and conversion to confirmed CRM writes.

### TASK-198 - AI 动态客户信号与可解释评分升级

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-104-ai-dynamic-customer-scoring.md`
- task_status_path: `.claw/tasks/TASK-198.md`
- assignment_path: `.claw/assignments/TASK-198.yaml`
- blocked_by: `none`
- next_action: Done in production `2.6.1`; monitor pending-confidence rate, signal quality and score drift.

### TASK-197 - 客户互动档案、动态记忆与按需检索落地

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-103-customer-interaction-archive-memory-retrieval.md`
- task_status_path: `.claw/tasks/TASK-197.md`
- assignment_path: `.claw/assignments/TASK-197.yaml`
- blocked_by: `none`
- next_action: Done in production `2.5.11`; monitor archive growth, ACTIVE memory resolution and assistant evidence quality.

### TASK-191 - CloudCC 嵌入页重复刷新与客户信号并发修复

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-099-cloudcc-embed-remount-signal-idempotency.md`
- task_status_path: `.claw/tasks/TASK-191.md`
- assignment_path: `.claw/assignments/TASK-191.yaml`
- blocked_by: `none`
- next_action: Done in production `2.4.12`; monitor repeated CRM refresh and customer signal write errors.

### TASK-190 - CloudCC 嵌入端会话失效自动恢复

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-098-cloudcc-session-refresh.md`
- task_status_path: `.claw/tasks/TASK-190.md`
- assignment_path: `.claw/assignments/TASK-190.yaml`
- blocked_by: `none`
- next_action: Done in production `2.4.9`; monitor CloudCC session refresh frequency and authentication failures.

### TASK-189 - 客户互动多模态采集与确认归集

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-097-multimodal-interaction-ingestion.md`
- task_status_path: `.claw/tasks/TASK-189.md`
- assignment_path: `.claw/assignments/TASK-189.yaml`
- blocked_by: `none`
- next_action: Done in production `2.4.8`; monitor extraction quality, processing latency and confirmed CRM timeline records.

### TASK-188 - 客户互动工作台标题与静态链接控件修复

- status: `done`
- priority: `high`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/FEAT-096-customer-workbench-title-static-link.md`
- task_status_path: `.claw/tasks/TASK-188.md`
- assignment_path: `.claw/assignments/TASK-188.yaml`
- blocked_by: `none`
- next_action: Done in production `2.4.7`; retain the application title and static pointer states.

### TASK-187 - AI 应用壳层导航稳定性治理

- status: `done`
- priority: `high`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/FEAT-095-ai-app-shell-navigation-stability.md`
- task_status_path: `.claw/tasks/TASK-187.md`
- assignment_path: `.claw/assignments/TASK-187.yaml`
- blocked_by: `none`
- next_action: Done in production `2.4.6`; reuse shell navigation geometry and menu semantics on future pages.

### TASK-186 - 产品控件去框化与客户互动工作台全页治理

- status: `done`
- priority: `high`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/FEAT-094-customer-workbench-control-chrome-cleanup.md`
- task_status_path: `.claw/tasks/TASK-186.md`
- assignment_path: `.claw/assignments/TASK-186.yaml`
- blocked_by: `none`
- next_action: Done in production `2.4.5`; reuse the shared control primitives on future product pages.

### TASK-185 - 客户互动工作台 AI 助理展开模式

- status: `done`
- priority: `critical`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/FEAT-093-customer-assistant-expand-mode.md`
- task_status_path: `.claw/tasks/TASK-185.md`
- assignment_path: `.claw/assignments/TASK-185.yaml`
- blocked_by: `none`
- next_action: Done in production `2.4.4`; monitor expanded assistant usage and long-answer readability.

### TASK-184 - 客户互动工作台左侧队列横向裁切热修

- status: `done`
- priority: `critical`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/FEAT-092-customer-workbench-ui-streaming.md`
- task_status_path: `.claw/tasks/TASK-184.md`
- assignment_path: `.claw/assignments/TASK-184.yaml`
- blocked_by: `none`
- next_action: Done in production `2.4.3`; monitor queue clipping at non-default browser zoom levels.

### TASK-183 - 客户互动工作台界面规范化与流式助理

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-092-customer-workbench-ui-streaming.md`
- task_status_path: `.claw/tasks/TASK-183.md`
- assignment_path: `.claw/assignments/TASK-183.yaml`
- blocked_by: `none`
- next_action: Done in production `2.4.2`; monitor streaming completion, disconnect noise and assistant response quality.

### TASK-182 - 客户互动工作台生产闭环

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-081-customer-interaction-workbench.md`
- task_status_path: `.claw/tasks/TASK-182.md`
- assignment_path: `.claw/assignments/TASK-182.yaml`
- blocked_by: `none`
- next_action: Done in production `2.4.1`; monitor real-user CRM writes, assistant latency and recommendation quality.

### TASK-181 - 客户互动工作台客户列表排版修复

- status: `done`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-091-customer-workbench-account-list-alignment.md`
- task_status_path: `.claw/tasks/TASK-181.md`
- assignment_path: `.claw/assignments/TASK-181.yaml`
- blocked_by: `none`
- next_action: Done in production `2.3.9`; monitor customer workbench account list row alignment and truncation.

### TASK-180 - AI 应用页与客户互动工作台 UI 重构

- status: `done`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-090-ai-apps-workbench-ui-refactor.md`
- task_status_path: `.claw/tasks/TASK-180.md`
- assignment_path: `.claw/assignments/TASK-180.yaml`
- blocked_by: `none`
- next_action: Done in production `2.3.8`; monitor AI 应用悬浮菜单、客户互动工作台外层滚动条和演示组织工作台数据。

### TASK-179 - AI 听记实时发言人分离热修

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-089-ai-minutes-speaker-diarization-hotfix.md`
- task_status_path: `.claw/tasks/TASK-179.md`
- assignment_path: `.claw/assignments/TASK-179.yaml`
- blocked_by: `none`
- next_action: Done in production `2.3.7`; monitor real multi-speaker meetings, configured Iflytek auto-selection, and explicit Aliyun fallback notices for unconfigured organizations.

### TASK-178 - CRM 嵌入客户互动工作台语音输入热修

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-088-crm-workbench-voice-input-hotfix.md`
- task_status_path: `.claw/tasks/TASK-178.md`
- assignment_path: `.claw/assignments/TASK-178.yaml`
- blocked_by: `none`
- next_action: Done in production `2.3.5`; monitor CRM embedded customer workbench microphone permission and ASR startup error reporting.

### TASK-175 - 客户互动工作台外层滚动与 CRM 主页按钮清理

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-085-customer-workbench-scroll-cleanup.md`
- task_status_path: `.claw/tasks/TASK-175.md`
- assignment_path: `.claw/assignments/TASK-175.yaml`
- blocked_by: `none`
- next_action: Done in production `2.3.4`; monitor customer workbench platform/embed outer scrolling and CloudCC custom page `V3.0` component binding.

### TASK-174 - 数据洞察 AI 应用生产发布

- status: `done`
- priority: `critical`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-084-data-insight-ai-app.md`
- task_status_path: `.claw/tasks/TASK-174.md`
- assignment_path: `.claw/assignments/TASK-174.yaml`
- blocked_by: `none`
- next_action: Done in production release `2.3.2`; monitor 数据洞察 dashboard, demo org `REAL_CRM_DEMO` source labeling, and onechat DNS risk.

### TASK-173 - 客户互动工作台真实智能体助理

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-083-customer-workbench-real-agent-assistant.md`
- task_status_path: `.claw/tasks/TASK-173.md`
- assignment_path: `.claw/assignments/TASK-173.yaml`
- blocked_by: `none`
- next_action: Done in production release `2.3.1`; monitor customer workbench real agent assistant, `/ws/asr` microphone path, and demo org `org2sva14i4udjmi2t4s`.

### TASK-172 - 双环境真实演示数据建设

- status: `done`
- priority: `critical`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-082-demo-environment-real-data.md`
- task_status_path: `.claw/tasks/TASK-172.md`
- assignment_path: `.claw/assignments/TASK-172.yaml`
- blocked_by: `none`
- next_action: Done; monitor AgentCiCi `org2sva14i4udjmi2t4s` and CloudCC CRM `org0720f814430017229` demo data before customer demos.

### TASK-171 - 客户互动工作台生产就绪

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-081-customer-interaction-workbench.md`
- task_status_path: `.claw/tasks/TASK-171.md`
- assignment_path: `.claw/assignments/TASK-171.yaml`
- blocked_by: `none`
- next_action: Done through production release `2.2.7` and CloudCC pagecomponent V8 real CRM validation; continue tracking the `cc-customization-expert-msapi` bind pagecomponent write failure as a skill gap.

### TASK-170 - 安全规则平台与输入输出安全网关

- status: `in_progress`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-080-security-rules-platform.md`
- task_status_path: `.claw/tasks/TASK-170.md`
- assignment_path: `.claw/assignments/TASK-170.yaml`
- blocked_by: `none`
- next_action: Resume after TASK-169 production release; validate assignment and implement V71 security rules platform, runtime gateway, admin API, and `/admin/security-rules`.

### TASK-169 - 独立数据清洗与智能标注平台能力

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-079-kb-data-quality-annotation.md`
- task_status_path: `.claw/tasks/TASK-169.md`
- assignment_path: `.claw/assignments/TASK-169.yaml`
- blocked_by: `none`
- next_action: Done in production release `2.2.1`; monitor `/admin/data-quality`, data-quality API, and「知微画像」AI 应用.

### TASK-168 - ASR WebSocket 鉴权与线上语音入口修复

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-078-asr-websocket-auth-hotfix.md`
- task_status_path: `.claw/tasks/TASK-168.md`
- assignment_path: `.claw/assignments/TASK-168.yaml`
- blocked_by: `none`
- next_action: Done in production release `2.1.12`; user should retest AI 听记 and chat microphone from the browser.

### TASK-167 - RAG 检索路由策略化改造

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-077-rag-router-policy.md`
- task_status_path: `.claw/tasks/TASK-167.md`
- assignment_path: `.claw/assignments/TASK-167.yaml`
- blocked_by: `none`
- next_action: Done in production release `2.1.11`; monitor RAG Router trace metadata and KB retrieval behavior.

### TASK-166 - 产品功能类知识库触发与伪工具标签防护

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-076-product-kb-trigger-and-pseudo-tool-guard.md`
- task_status_path: `.claw/tasks/TASK-166.md`
- assignment_path: `.claw/assignments/TASK-166.yaml`
- blocked_by: `none`
- next_action: Done in production release `2.1.10`; monitor product-feature KB retrieval traces.

### TASK-165 - 智能体绑定知识库运行时检索触发修复

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-075-agent-kb-runtime-retrieval.md`
- task_status_path: `.claw/tasks/TASK-165.md`
- assignment_path: `.claw/assignments/TASK-165.yaml`
- blocked_by: `none`
- next_action: Done in production release `2.1.9`; monitor Agent-bound KB retrieval traces.

### TASK-164 - Qdrant 向量维度漂移修复

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-074-qdrant-dimension-repair.md`
- task_status_path: `.claw/tasks/TASK-164.md`
- assignment_path: `.claw/assignments/TASK-164.yaml`
- blocked_by: `none`
- next_action: Done in production release `2.1.8`; monitor KB upload and vector indexing.

### TASK-163 - 邮件 ID 刷新重试与语音后续可用性修复

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-073-email-id-refresh-and-voice-followup.md`
- task_status_path: `.claw/tasks/TASK-163.md`
- assignment_path: `.claw/assignments/TASK-163.yaml`
- blocked_by: `none`
- next_action: Done in production release `2.1.7`; user should retest the same email body continuation and voice input flow.

### TASK-162 - 连续确认后的邮件正文工具续执行

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-072-continuous-tool-execution-confirmation.md`
- task_status_path: `.claw/tasks/TASK-162.md`
- assignment_path: `.claw/assignments/TASK-162.yaml`
- blocked_by: `none`
- next_action: Done in production release `2.1.6`; monitor confirmed email-body continuation behavior.

### TASK-161 - 对话邮件正文展示与语音输入识别修复

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-071-mail-body-and-voice-input-fix.md`
- task_status_path: `.claw/tasks/TASK-161.md`
- assignment_path: `.claw/assignments/TASK-161.yaml`
- blocked_by: `none`
- next_action: Done in production release `2.1.5`; monitor dialog mail-body and voice-input behavior.

### TASK-159 - Chat session state tenant primary key hotfix

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-069-chat-session-state-tenant-key-hotfix.md`
- task_status_path: `.claw/tasks/TASK-159.md`
- assignment_path: `.claw/assignments/TASK-159.yaml`
- blocked_by: `none`
- next_action: Done in production release `2.1.4`; monitor chat logs and follow up on ACR push durability.

### TASK-158 - Agent runtime concurrency hardening

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-068-agent-runtime-concurrency-hardening.md`
- task_status_path: `.claw/tasks/TASK-158.md`
- assignment_path: `.claw/assignments/TASK-158.yaml`
- blocked_by: `none`
- next_action: Included in production release `2.1.3`; monitor runtime concurrency behavior with normal production traffic.

### TASK-156 - Agent Builder production readiness closure

- status: `review`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-066-agent-builder-production-readiness.md`
- task_status_path: `.claw/tasks/TASK-156.md`
- assignment_path: `.claw/assignments/TASK-156.yaml`
- blocked_by: `none`
- next_action: Review TASK-156 production-readiness closure; focused backend integration, frontend build, real-backend desktop validation, and readiness/evaluation gate smoke passed.

### TASK-157 - Enterprise knowledge platform readiness

- status: `review`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-067-enterprise-knowledge-platform-readiness.md`
- task_status_path: `.claw/tasks/TASK-157.md`
- assignment_path: `.claw/assignments/TASK-157.yaml`
- blocked_by: `none`
- next_action: Review TASK-157 enterprise KB closure; focused backend integration, frontend build, real-backend desktop validation, Rabbit/Qdrant smoke, and drift audit evidence passed.

### TASK-155 - 运营端前端页面 UI 整体美化

- status: `review`
- priority: `high`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/FEAT-065-platform-console-ui-polish.md`
- task_status_path: `.claw/tasks/TASK-155.md`
- assignment_path: `.claw/assignments/TASK-155.yaml`
- blocked_by: `none`
- next_action: Review TASK-155 platform UI polish; task gates, frontend build, `git diff --check`, and desktop Playwright screenshots for all platform routes passed, with `/api/platform/audit/logs` still returning backend 500.

### TASK-154 - Credits metering production readiness sweep

- status: `review`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-037-saas-billing-usage-ledger.md`
- task_status_path: `.claw/tasks/TASK-154.md`
- assignment_path: `.claw/assignments/TASK-154.yaml`
- blocked_by: `none`
- next_action: Review TASK-154 Credits metering completion; assignment/login gates, Open API/KB/admin billing focused tests, and `git diff --check` passed.

### TASK-153 - Platform-governed Tavily, Iflytek, and OneKeyToken provider

- status: `review`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-062-platform-model-provider-governance.md`
- task_status_path: `.claw/tasks/TASK-153.md`
- assignment_path: `.claw/assignments/TASK-153.yaml`
- blocked_by: `none`
- next_action: Review TASK-153 changes; focused backend tests, frontend build, local API smoke, local service restart, and `git diff --check` passed.

### TASK-152 - AI 听记 credits and start-timeout hotfix

- status: `in_progress`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-037-saas-billing-usage-ledger.md`
- task_status_path: `.claw/tasks/TASK-152.md`
- assignment_path: `.claw/assignments/TASK-152.yaml`
- blocked_by: `none`
- next_action: Fix local-test defects where AI 听记 start can time out and successful AI 听记 usage does not consume organization credits; then run focused backend validation and local smoke.

### TASK-151 - RBAC and audit production readiness

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-064-rbac-production-readiness.md`
- task_status_path: `.claw/tasks/TASK-151.md`
- assignment_path: `.claw/assignments/TASK-151.yaml`
- blocked_by: `none`
- next_action: Done; release `2.1.2` is deployed and `/platform/audit` loads without the production `text ~~ bytea` error.

### TASK-150 - Knowledge Base production readiness

- status: `review`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-008-knowledge-base-lifecycle-completion.md`
- task_status_path: `.claw/tasks/TASK-150.md`
- assignment_path: `.claw/assignments/TASK-150.yaml`
- blocked_by: `none`
- next_action: Review and merge TASK-150 production-readiness implementation; frontend build, desktop smoke, backend compile, assignment check, KB lifecycle integration, and Qdrant stack smoke all passed.

### TASK-149 - Knowledge Base DOCX upload parser

- status: `review`
- priority: `critical`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-008-knowledge-base-lifecycle-completion.md`
- task_status_path: `.claw/tasks/TASK-149.md`
- assignment_path: `.claw/assignments/TASK-149.yaml`
- blocked_by: `none`
- next_action: Review and merge `codex/TASK-149-kb-docx-upload-parser`; local KB lifecycle regression and static diff checks passed.

### TASK-146 - 观测与运维生产就绪收口

- status: `review`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-019-agent-observability-monitoring.md`
- task_status_path: `.claw/tasks/TASK-146.md`
- assignment_path: `.claw/assignments/TASK-146.yaml`
- blocked_by: `none`
- next_action: Review TASK-146 local changes; merge after normal integration gates. State validation has an existing TASK-143 line-budget blocker outside this task.

### TASK-148 - Production domain cutover

- status: `done`
- priority: `critical`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-063-production-domain-cutover.md`
- task_status_path: `.claw/tasks/TASK-148.md`
- assignment_path: `.claw/assignments/TASK-148.yaml`
- blocked_by: `none`
- next_action: Monitor production traffic and update any external integrations still using retired hostnames.

### TASK-147 - WeCom customer-service connection test

- status: `review`
- priority: `high`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-023-ai-native-after-sales-agent.md`
- task_status_path: `.claw/tasks/TASK-147.md`
- assignment_path: `.claw/assignments/TASK-147.yaml`
- blocked_by: `none`
- next_action: Review TASK-147 changes, then run live WeCom callback smoke once real account details and filed callback domain are available.

### TASK-145 - Platform model provider governance

- status: `review`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-062-platform-model-provider-governance.md`
- task_status_path: `.claw/tasks/TASK-145.md`
- assignment_path: `.claw/assignments/TASK-145.yaml`
- blocked_by: `none`
- next_action: Included in the local `main` integration merge; run focused backend/frontend integration gates before marking done.

### TASK-144 - AgentCiCi public website restructure

- status: `done`
- priority: `high`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/FEAT-061-agentcici-public-website-restructure.md`
- task_status_path: `.claw/tasks/TASK-144.md`
- assignment_path: `.claw/assignments/TASK-144.yaml`
- blocked_by: `none`
- next_action: Released to production in `2.1.3`; monitor website demo booking records.

### TASK-143 - Billing editions configurable in platform operations

- status: `review`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-037-saas-billing-usage-ledger.md`
- task_status_path: `.claw/tasks/TASK-143.md`
- assignment_path: `.claw/assignments/TASK-143.yaml`
- blocked_by: `none`
- next_action: Local `main` MR validation passed after unified Credits billing presentation; publish/update the Codeup MR for review and merge.

### TASK-142 - OpenAPI chat-messages SSE streaming

- status: `ready`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-060-openapi-chat-messages-sse-streaming.md`
- task_status_path: `.claw/tasks/TASK-142.md`
- assignment_path: `.claw/assignments/TASK-142.yaml`
- blocked_by: `TASK-140 may change the final public route shape`
- next_action: `DEV-fengchu` runs task-scoped `dev-login.py` on `codex/TASK-142-openapi-sse-streaming`, then implements true SSE streaming for OpenAPI `chat-messages`.

### TASK-141 - AI 听记本地 FunASR 实时转写

- status: `ready`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-059-ai-minutes-local-asr.md`
- task_status_path: `.claw/tasks/TASK-141.md`
- assignment_path: `.claw/assignments/TASK-141.yaml`
- blocked_by: `none`
- next_action: `DEV-houyi` runs task-scoped `dev-login.py` on `codex/TASK-141-local-funasr-realtime-asr`, then implements the local FunASR realtime ASR sidecar and `/ws/asr?provider=local` integration.

### TASK-140 - Remove Agent ID from public OpenAPI routes

- status: `in_progress`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-058-openapi-agentless-endpoints.md`
- task_status_path: `.claw/tasks/TASK-140.md`
- assignment_path: `.claw/assignments/TASK-140.yaml`
- blocked_by: `none`
- next_action: `DEV-fengchu` completes review fixes / service validation for `codex/TASK-140-openapi-agentless-endpoints`; task status is the progress source.

### TASK-139 - Agent list OpenAPI badge shows only first Agent

- status: `ready`
- priority: `high`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- task_status_path: `.claw/tasks/TASK-139.md`
- assignment_path: `.claw/assignments/TASK-139.yaml`
- blocked_by: `none`
- next_action: `DEV-fengchu` runs task-scoped `dev-login.py` on `codex/TASK-139-agent-list-openapi-badge`, then fixes list channel data and badge rendering.

### TASK-138 - OpenAPI docs copy cleanup

- status: `ready`
- priority: `medium`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/FEAT-057-openapi-docs-copy-cleanup.md`
- task_status_path: `.claw/tasks/TASK-138.md`
- assignment_path: `.claw/assignments/TASK-138.yaml`
- blocked_by: `TASK-140 may change the final route examples`
- next_action: `DEV-fengchu` runs task-scoped `dev-login.py` on `codex/TASK-138-openapi-docs-copy-cleanup`, then updates OpenAPI docs copy.

### TASK-137 - Custom Agent delete action

- status: `review`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-056-custom-agent-delete.md`
- task_status_path: `.claw/tasks/TASK-137.md`
- assignment_path: `.claw/assignments/TASK-137.yaml`
- blocked_by: `none`
- next_action: code is complete on `codex/TASK-137-custom-agent-delete`; review/merge after backend integration rerun when local PostgreSQL is available.

### TASK-136 - Frontend auth token sync across tabs

- status: `ready`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-055-frontend-auth-token-sync.md`
- task_status_path: `.claw/tasks/TASK-136.md`
- assignment_path: `.claw/assignments/TASK-136.yaml`
- blocked_by: `none`
- next_action: `DEV-fengchu` runs task-scoped `dev-login.py` on `codex/TASK-136-frontend-auth-token-sync`, then implements shared token sync.

### TASK-133 - Agent Builder no-model new-Agent model-config redirect

- status: `review`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- task_status_path: `.claw/tasks/TASK-133.md`
- assignment_path: `.claw/assignments/TASK-133.yaml`
- blocked_by: `none`
- next_action: PM/integration owner reviews and merges `codex/TASK-133-agent-builder-new-agent-model-config-fix`; mark `done` after merge verification.

### TASK-132 - Agent Builder focused-agent skill binding refresh bugfix

- status: `review`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- task_status_path: `.claw/tasks/TASK-132.md`
- assignment_path: `.claw/assignments/TASK-132.yaml`
- blocked_by: `none`
- next_action: PM/integration owner reviews and merges `codex/TASK-132-agent-builder-skill-refresh-bugfix`; mark `done` after merge verification.

### TASK-116 - Skill module completion and optimization

- status: `ready`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-038-admin-skill-module-completion.md`
- task_status_path: `.claw/tasks/TASK-116.md`
- assignment_path: `.claw/assignments/TASK-116.yaml`
- blocked_by: `none`
- next_action: `DEV-wolong` runs task-scoped `dev-login.py`, closes P0 security/regression gaps, then continues P1/P2 work.

### TASK-115 - Knowledge base module maintenance

- status: `ready`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-008-knowledge-base-lifecycle-completion.md`
- task_status_path: `.claw/tasks/TASK-115.md`
- assignment_path: `.claw/assignments/TASK-115.yaml`
- blocked_by: `none`
- next_action: `DEV-zhongda` runs task-scoped `dev-login.py`, executes the P0 hardening package, then continues P1/P2 work.

### TASK-114 - FEAT-037 SaaS billing usage ledger

- status: `ready`
- priority: `critical`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-037-saas-billing-usage-ledger.md`
- task_status_path: `.claw/tasks/TASK-114.md`
- assignment_path: `.claw/assignments/TASK-114.yaml`
- blocked_by: `none`
- next_action: `MANAGER-001` runs task-scoped `dev-login.py` and continues the end-to-end billing ledger implementation.

## Backlog / Blocked

### TASK-096 - End-to-end CRM embed verification

- status: `blocked`
- priority: `high`
- owner_role: `qa-agent`
- spec_path: `docs/specs/FEAT-032-meeting-minutes-embed-sdk.md`
- task_status_path: `none`
- assignment_path: `none`
- blocked_by: `CloudCC iframe host smoke and ACR hotfix persistence are still open`
- next_action: Confirm the iframe host on the real CloudCC page, then repair ACR credentials and persist the deployed hotfix image.

### TASK-036 - Skill declarative API runtime

- status: `blocked`
- priority: `critical`
- owner_role: `backend-agent`
- spec_path: `docs/specs/FEAT-015-skill-declarative-api-runtime.md`
- task_status_path: `none`
- assignment_path: `none`
- blocked_by: `Real external API smoke still depends on TASK-023 runtime prerequisites`
- next_action: Close the runtime prerequisites, then finish real external API smoke and browser-level admin verification.

### TASK-023 - CloudCC runtime smoke unblock

- status: `blocked`
- priority: `critical`
- owner_role: `backend-agent`
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- task_status_path: `none`
- assignment_path: `none`
- blocked_by: `CloudCC runtime credentials and local Aliyun API key are not yet verified`
- next_action: Rotate and verify `cc_username/cc_safetymark`, restore a usable local Aliyun API key, then rerun the real `/ai/chat` and CloudCC tool chain smoke.

### TASK-020 - Knowledge base lifecycle completion

- status: `blocked`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-008-knowledge-base-lifecycle-completion.md`
- task_status_path: `none`
- assignment_path: `none`
- blocked_by: `User explicitly paused FEAT-008 continuation`
- next_action: Resume only when requested; restart from page-level regression on document/settings/chunk dialogs.

### TASK-070 - AgentCiCi market positioning and roadmap

- status: `todo`
- priority: `high`
- owner_role: `human`
- spec_path: `docs/specs/FEAT-025-agentcici-market-positioning-and-roadmap.md`
- task_status_path: `none`
- assignment_path: `none`
- blocked_by: `Awaiting a shaped follow-up request`
- next_action: Reuse FEAT-025 as the scope source when the next strategy or packaging task is opened.

### TASK-063 - AI native after-sales agent spec

- status: `todo`
- priority: `high`
- owner_role: `shared`
- spec_path: `docs/specs/FEAT-023-ai-native-after-sales-agent.md`
- task_status_path: `none`
- assignment_path: `none`
- blocked_by: `WeCom customer-service account details and data mapping are not yet confirmed`
- next_action: Confirm `open_kfid`, CorpID/secret, Token/AESKey, run-as service user, and first-wave after-sales data sources before implementation resumes.

### TASK-007 - SaaS billing and packaging design

- status: `todo`
- priority: `medium`
- owner_role: `shared`
- spec_path: `docs/specs/FEAT-003-saas-billing-and-packaging.md`
- task_status_path: `none`
- assignment_path: `none`
- blocked_by: `none`
- next_action: If reopened, start with usage meter events, package/subscription entities, and the admin billing overview before any payment-provider work.

## Completed Tasks

### TASK-208 - CRM 产品销售经营分析稳定性与深度治理

- status: `done`
- priority: `critical`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-114-crm-product-sales-analysis-hardening.md`
- task_status_path: `.claw/tasks/TASK-208.md`
- assignment_path: `.claw/assignments/TASK-208.yaml`
- blocked_by: `none`
- next_action: Done in production `2.7.5 / be80eea665c0`; monitor CRM source-field coverage and keep the governed SalesA demo batch idempotent.

### TASK-209 - 运营平台登录页原图像素锁定复刻

- status: `done`
- priority: `critical`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-115-platform-login-cosmic-visual-refresh.md`
- task_status_path: `.claw/tasks/TASK-209.md`
- assignment_path: `.claw/assignments/TASK-209.yaml`
- blocked_by: `none`
- next_action: Done in production `2.7.2 / ddcda0ef6111`; preserve the released background asset and transparent interaction layer in later releases.

### TASK-196 - 客户互动整理上下文与队列丢失修复

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-102-customer-workbench-context-stability.md`
- task_status_path: `.claw/tasks/TASK-196.md`
- assignment_path: `.claw/assignments/TASK-196.yaml`
- blocked_by: `none`
- next_action: Done in production `2.5.9`; monitor interaction confirmation latency and customer-context stability.

### TASK-195 - 客户互动时间线完整年份显示

- status: `done`
- priority: `high`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/FEAT-081-customer-interaction-workbench.md`
- task_status_path: `.claw/tasks/TASK-195.md`
- assignment_path: `.claw/assignments/TASK-195.yaml`
- blocked_by: `none`
- next_action: `none`; production `2.5.8` shows four-digit years in compact and full customer interaction timelines.

### TASK-194 - 全量客户名称搜索与产品输入焦点治理

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-101-global-customer-search-and-field-focus.md`
- task_status_path: `.claw/tasks/TASK-194.md`
- assignment_path: `.claw/assignments/TASK-194.yaml`
- blocked_by: `none`
- next_action: `none`; production `2.5.6` searches all CloudCC Accounts visible to the current identity and applies the shared single-layer focus rule.

### TASK-193 - 客户队列默认按最近互动倒序

- status: `done`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-100-large-crm-organization-async-sync.md`
- task_status_path: `.claw/tasks/TASK-193.md`
- assignment_path: `.claw/assignments/TASK-193.yaml`
- blocked_by: `none`
- next_action: `none`; production `2.5.3` defaults both queues to recent-interaction descending order.

### TASK-192 - 大数据量 CRM 组织异步初始化与 504 修复

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-100-large-crm-organization-async-sync.md`
- task_status_path: `.claw/tasks/TASK-192.md`
- assignment_path: `.claw/assignments/TASK-192.yaml`
- blocked_by: `none`
- next_action: Monitor sync duration and create a separate incremental-projection task before removing the 10,000-record ceiling.

### TASK-134 - AI minutes local audio upload and speaker diarization

- status: `done`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-054-ai-minutes-local-audio-upload.md`
- task_status_path: `.claw/tasks/TASK-134.md`
- assignment_path: `.claw/assignments/TASK-134.yaml`
- blocked_by: `none`
- next_action: `none`; local upload, 百炼 Fun-ASR speaker diarization, transcript normalization, and Markdown `下载转写` are complete. The remaining whole-file reset was confirmed as a local-network limitation; online environment is normal.

### TASK-124 - FEAT-046 platform tenant manual provisioning and lifecycle split

- status: `done`
- priority: `critical`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-046-platform-tenant-manual-provisioning-and-lifecycle-entry.md`
- task_status_path: `.claw/tasks/TASK-124.md`
- assignment_path: `.claw/assignments/TASK-124.yaml`
- blocked_by: `none`
- next_action: `none`; platform tenant list/detail split, manual provisioning, backend provisioning path, focused tests, and desktop QA are complete.

### TASK-135 - Clear default login account values

- status: `done`
- priority: `high`
- owner_role: `project-manager`
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- task_status_path: `.claw/tasks/TASK-135.md`
- assignment_path: `.claw/assignments/TASK-135.yaml`
- blocked_by: `none`
- next_action: `none`; assistant, admin, and platform login account inputs now start empty and passed static search, frontend build, and browser checks.

### TASK-131 - Platform account orgless auth context

- status: `done`
- priority: `high`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-053-platform-account-orgless-auth-context.md`
- task_status_path: `.claw/tasks/TASK-131.md`
- assignment_path: `.claw/assignments/TASK-131.yaml`
- blocked_by: `none`
- next_action: `none`; Codeup change/6 was merged with post-merge state, frontend, backend compile, script, and diff verification. Rerun `PlatformAuthIntegrationTest` later when local Docker/Postgres is available.

### TASK-130 - ACR release version governance and app version badge

- status: `done`
- priority: `high`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-052-acr-release-version-governance.md`
- task_status_path: `.claw/tasks/TASK-130.md`
- assignment_path: `.claw/assignments/TASK-130.yaml`
- blocked_by: `none`
- next_action: `none`; production releases now use `docs/production-release-runbook.md` and `scripts/release-acr.sh` for one canonical version across ACR tags, Git tag, backend metadata, frontend badge, and deploy env.

### TASK-129 - Admin login organization-selection alignment

- status: `done`
- priority: `high`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-024-account-tenant-lifecycle-and-data-retention.md`
- task_status_path: `.claw/tasks/TASK-129.md`
- assignment_path: `.claw/assignments/TASK-129.yaml`
- blocked_by: `none`
- next_action: `none`; `/admin/login` now removes the orgId field, supports organization selection after account login, and passed desktop/mobile QA.

### TASK-127 - Merge remaining local branches into the current branch

- status: `done`
- priority: `high`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-047-local-branch-integration-pass.md`
- task_status_path: `.claw/tasks/TASK-127.md`
- assignment_path: `.claw/assignments/TASK-127.yaml`
- blocked_by: `none`
- next_action: `none`; remaining local branches are integrated and the dirty worktree has been restored on `codex/TASK-124-feat-046-platform-tenant-provisioning`.

## Maintenance Rules

- Keep each task card under 20 lines.
- Store only index fields here.
- Store current task details in `.claw/tasks/TASK-xxx.md`.
- Store old completed, superseded, and historical task cards in `.claw/task-archive.md`.
