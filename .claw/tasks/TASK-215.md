---
kind: task-status
task_id: TASK-215
status: done
updated_at: 2026-07-21T10:15:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-215.yaml
spec_path: docs/specs/FEAT-120-trace-full-detail-expansion.md
---

# TASK-215 - 链路追踪全文查看与复制

## Scope

- 为组织管理员在 Trace 节点提供按需展开、复制脱敏详情的能力。
- 区分新 Trace 的受控完整详情与旧 Trace 的历史截断提示。
- 完成后端/前端测试和桌面端浏览器验收。

## Current State

- 新 Trace 仍保留 220 字节点摘要，但在管理员详情中保存最多 12,000 字的脱敏可查看文本；密码和手机号在写入前已脱敏。
- 管理端节点现支持原位展开、收起和复制；旧 Trace 明确提示其既有详情可能已经截断。
- 后端单测、前端 17 个测试文件/88 项、Vite production build、桌面端模拟管理员浏览器展开/复制与控制台检查均通过。

## Next Action

- 无；已发布生产 `2.7.11 / 281f35b2cb2f`，后续仅监控管理员详情访问与脱敏边界。

## Changed Files

- `backend/src/main/java/com/codehouse/ciciassistant/ai/service/AgentRunTraceService.java`
- `backend/src/test/java/com/codehouse/ciciassistant/ai/service/AgentRunTraceServiceTest.java`
- `frontend/src/admin/pages/AdminAgentRunMonitor.tsx`
- `frontend/src/admin/pages/AdminAgentRunMonitor.test.tsx`
- `frontend/src/styles.css`
- `docs/specs/FEAT-120-trace-full-detail-expansion.md`

## Verification

- `mvn -q -Dtest=AgentRunTraceServiceTest test` -> passed.
- `npm test` -> 17 files / 88 tests passed; `npm run build` -> passed (existing large-chunk advisory only).
- 本地 `1280 × 720` 管理员浏览器用受控 Trace 响应验证摘要、展开、收起、复制成功反馈、无横向溢出和 console error/warning=0；截图 `.playwright-cli/page-2026-07-21T09-35-01-950Z.png`。
- 生产发布已完成：ACR backend/frontend 镜像通过 inspect，发布前四类备份非空，六服务 healthy，`/actuator/health=UP`，`/system/version=2.7.11 / 281f35b2cb2f`，Nginx 有效，x HTTPS 与生产-IP-resolved onechat HTTPS 均为 200。生产浏览器到达独立管理员登录页且 console 无 error/warning；本会话没有管理员凭据，因此未重复受保护 Trace 展开/复制。

## Handoff

- 完整需求、权限边界、交互和验收要求见 `docs/specs/FEAT-120-trace-full-detail-expansion.md`。
- 不读取、不修改、不提交当前工作区中的 TASK-207/TASK-208、`diagrams/` 或其他无关改动。
