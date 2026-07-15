---
kind: task-status
task_id: TASK-211
status: in_progress
updated_at: 2026-07-15T01:35:39Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: backend-agent
assignment_path: .claw/assignments/TASK-211.yaml
spec_path: docs/specs/FEAT-114-crm-product-sales-analysis-hardening.md
---

# TASK-211 - CRM 确定性回答真实流式输出纠偏

## Scope

- 把 CRM 确定性完整正文从单个 SSE `delta` 改为服务端有节奏的多分片输出。
- 保持 blocking、持久化、Agent OpenAPI、Top 5、五层经营分析和防泄漏事实完全一致。
- 以 TDD 覆盖 CRM SSE 与 OpenAPI streaming 的分片数量、顺序、拼接正文和结束事件。
- 发布新的不可变版本并完成 SalesA 五次真实流式页面验收。

## Current State

- 根因已验证：生产 5 次 CRM SSE 都只有一个 2,383 字符正文 `delta`；OpenAPI streaming 也只有一个正文 `message`。
- 前端逐片渲染和 Nginx buffering 配置正常；缺陷由后端 CRM 确定性分支的一次性 `safeSendDelta` 引入。
- 用户已批准方案 A：复用现有 `safeSendDeltaInChunks`，不恢复最终 LLM，不新增前端模拟打字。
- TDD 已先证明旧实现只有一个 `delta`，再以一行生产代码切换到现有 18 字/18ms 分片 helper；内部 SSE 与 OpenAPI 回归均通过。
- 干净测试库 CRM 定向 135 项、前端 89 项、生产构建、Compose、授权和 diff 门禁通过；任务级与整分支独立审查均批准合并。
- `2.7.6 / 2055947aae07` 生产内部 SSE 已验证 5 次 133 分片与持久化精确一致，但 OpenAPI bridge 对每片调用 `trim()`，使 streaming 比 blocking 丢失 41 个空格/换行，生产验收判定失败。
- 临时 OpenAPI Key 已撤销并验证 401，Agent bindings 已按 fresh 快照精确恢复；应用只重建 backend/frontend 回滚到健康的 `2.7.5 / be80eea665c0`，状态服务 ID 未改变。
- 空白敏感回归已按 TDD 先红后绿；最小修复保留所有非空 delta 的首尾空白和纯空白片段。独立审查无 Critical / Important / Minor，PR #7 已合并为 `e47979167af8`。
- 不可变版本 `2.7.7` 已通过统一脚本 dry-run、构建、推送并上线；backend/frontend 使用新镜像，四个状态服务容器 ID 未变化，六服务健康，Flyway 当前为 V80，`/system/version`、Nginx 和两个公网入口通过。
- SalesA 5 次 fresh SSE 均为 3 个 phase、133 个 delta、最大 18 UTF-16 单元、约 2.4 秒持续到达、唯一尾部 done，并与各自持久化正文逐字一致；blocking、SalesB 和 5 次结果仅归一化动态截止时间后完全相同。
- OpenAPI blocking 与 streaming 均为 2,383 字；streaming 为 133 个 message、3 个脱敏 thought 和唯一尾部 message_end，空格/换行逐字保真，并与 OpenAPI 历史、内部协议正文一致。临时 Key 已撤销并验证 401 `agent_api_key_invalid`，Agent bindings 精确恢复且无新增 ACTIVE Key。
- 9 份用户正文通过工具名、原始 JSON、内部 ID 和敏感字段泄漏扫描；五层经营分析、Top 5、贡献/环比、订单客户覆盖、商机合同、退货口径和收入声明均完整。最终干净日志窗口为 backend ERROR 0、CRM failure 0、异常断连 0、Nginx 5xx 0。
- 唯一未取得的证据是生产桌面 UI 的“同一气泡中间态/最终态”截图与 console 记录：应用内 Browser 当前不可用且可用实例列表为空，按浏览器技能约束未用 Playwright 冒充。接口到达时间已证明服务端真实流式行为，任务保持 active 直至补齐该视觉证据。

## Next Action

- 应用内 Browser 实例恢复后，使用 fresh SalesA 登录补录同一 assistant 气泡的可见中间态、最终态、console error=0 与无横向溢出证据；通过后关闭 TASK-211。生产 `2.7.7` 继续运行并监控。

## Constraints

- 不修改 CRM 数据、CloudCC 元数据、角色、简档、共享规则或演示批次。
- 不修改前端生产代码，不引入第二套打字动画或消息缓冲状态机。
- 不恢复最终 LLM，不泄漏工具事件、原始 JSON、内部 ID 或敏感凭据。
- 不覆盖 `2.7.5`，只允许按发布 runbook 创建后续不可变版本。

## Changed Files

- `backend/src/main/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/openapi/service/AgentOpenApiConversationService.java`
- `backend/src/test/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorServiceModelIdentityTest.java`
- `backend/src/test/java/com/codehouse/ciciassistant/openapi/service/AgentOpenApiConversationServiceTest.java`
- `docs/specs/FEAT-114-crm-product-sales-analysis-hardening.md`
- `docs/superpowers/plans/2026-07-15-crm-streaming-output.md`
- `.claw/tasks/TASK-211.md`
- `.claw/assignments/TASK-211.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/issue-list.md`
- `.claw/test-report.md`
- `.claw/devops.md`

## Handoff

- 分支：`codex/TASK-211-crm-streaming-output`。
- 首轮 PR：`https://github.com/OlivierZEN/CICI/pull/6`；OpenAPI 保真补丁 PR：`https://github.com/OlivierZEN/CICI/pull/7`，已合并。
- 先读 FEAT-114 的“TASK-211 真实流式输出纠偏设计”，再读当前单包路径与已有分块 helper。
- 已审查首轮实现提交 `1e7fcc7a6228c19bad193bb46787fb8fb3bd5b2d` 与空白保真提交 `eb5e1f7e4dc05f53943094e09289c54cd08d0056`；`2.7.6` 已失败回滚，当前生产为 `2.7.7 / e47979167af8`。
