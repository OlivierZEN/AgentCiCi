---
kind: task-status
task_id: TASK-225
status: done
updated_at: 2026-07-22T10:29:33+08:00
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-225.yaml
spec_path: docs/specs/FEAT-130-forced-skill-execution-context.md
---

# TASK-225 - 对话技能选择的强制执行上下文与可观测性

## Scope

- 让工作台选择技能成为本轮强制业务上下文，不再只是工具授权。
- 在 Trace 与两个监控界面明确展示选择、有效上下文、实际激活和未采纳原因。

## Current State

- 所选技能现在只注入自身的业务流程和输出契约；未选择时继续注入原有多技能业务上下文。
- 文件型技能参考文档在选择有效时仅解析所选技能；平台安全策略、Agent 直接工具和手动/意图技能工具授权保持不变。
- Trace 已保存用户请求、有效上下文、选择状态/原因、实际激活与候选绑定技能；工作台与管理端监控按相同顺序显示。

## Verification

- `mvn -q -Dtest=AgentRunTraceServiceTest,SkillPromptAssemblerTest test` 通过。
- `mvn -q -DskipTests compile`、`npm test`（28 文件/187 断言）、`npm run build`、`git diff --check` 通过。
- 本地桌面浏览器已验证应用可加载且 console error 为 0；当前无已授权的组织用户会话，受保护的工作台/Trace 实际交互留待已登录会话复核，未伪造结果。
- 已发布生产 `2.8.4 / 2f2f1a013ec2`；线上 Trace/工作台功能随 backend/frontend 同步更新，六服务健康、版本接口与公网 HTTPS smoke 通过。无授权组织会话，未创建真实业务对话验证选择态。
