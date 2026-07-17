---
kind: task-status
task_id: TASK-213
status: in_progress
updated_at: 2026-07-17T06:25:43Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-213.yaml
spec_path: docs/specs/FEAT-118-general-ontology-modeling-platform.md
---

# TASK-213 - 通用本体建模与语义查询平台 V1

## Scope

- 交付领域无关本体内核、业务可视化画布、AI 草稿副驾驶、映射目录、确定性契约编译和受限只读语义查询。
- 用项目交付 `INLINE_SAMPLE` 与 CloudCC CRM 两个领域/适配器验证通用性。
- 完成租户隔离、版本治理、自动化测试、桌面产品验收和生产发布。

## Current State

- 用户已批准 FEAT-118 的推荐设计、AI/人工权限边界与只读 V1 范围，并明确要求无需再次确认，直接实现和发布生产。
- 通用本体内核、V82 持久化、参考业务包、CloudCC/INLINE_SAMPLE 适配、受限语义查询、业务可视化工作台和 AI 草稿提案均已在 `codex/TASK-213-general-ontology-v1` 实现；当前仍是本地分支状态，尚未合并或发布生产。
- 发布阻塞审查指出的身份旧响应、页面卸载异步回写、组织切换重载、未保存映射覆盖、技术预览修订竞态、提案实现键泄漏、警告对比度和 tab IDREF 问题均已按 TDD 修复。
- 后续正式审计新发现一项工作区创建 Important：POST 结果未知或并发唯一键竞争时可能盲目重试、误接管或返回 500。当前已按 TDD 修复：前端只接受当前组织内 `key + name + description + createdBy` 与同一管理员原请求完全一致的权威列表记录，无法确认时锁定并要求刷新；后端只把 V82 目标唯一约束翻译为稳定 409，其他完整性异常不误标。
- 同一审计意见覆盖的参考包直装路径也已按 TDD 补齐：摘要从实际包文档返回 `workspaceIdentity`，结果未知或精确 `ONTOLOGY_KEY_CONFLICT` 时只恢复当前管理员创建且身份完全一致的权威工作区；展示标题、其他管理员或手工创建的同 key 工作区均不能被接管，无法确认则锁定安装直到列表成功刷新，其他错误不吞没。
- 本轮前端 26 个文件 / 177 项测试与生产构建通过；新建且随后删除的隔离 PostgreSQL 上，本体平台 14 项集成测试、管理服务 4 项测试、参考包服务 3 项测试全部通过，后端生产打包通过。此前真实浏览器退出/卸载时序、`expectedRevision` 请求绑定、完整 ARIA 面板关系和 1600×1000 桌面视图证据继续有效。
- 原 10 项问题仍保持关闭；工作区创建与参考包安装增量修复等待最终独立复审，因此当前不得沿用“Critical 0 / Important 0”的最终结论。此前 mounted router 延迟测试债仍为 Minor。
- 生产发布仍必须在签名提交、合并和 runbook 全部门禁通过后执行。

## Next Action

- 完成工作区创建与参考包安装修复的最终独立复审后交接主执行者；随后合并并严格按 `docs/production-release-runbook.md` 完成不可变发布和线上验收。

## Changed Files

- `backend/src/main/java/com/codehouse/ciciassistant/ontology/api/AdminOntologyController.java`
- `backend/src/main/java/com/codehouse/ciciassistant/ontology/service/OntologyManagementService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/ontology/service/OntologyReferencePackageService.java`
- `backend/src/test/java/com/codehouse/ciciassistant/ontology/OntologyPlatformIntegrationTest.java`
- `frontend/src/admin/AdminShell.tsx`、`frontend/src/admin/adminAuthScope.ts` 及对应测试
- `frontend/src/admin/pages/AdminOntologyPage.tsx`、`frontend/src/admin/ontology/**`、`frontend/src/styles/admin-ontology.css`
- `frontend/src/App.tsx`、`frontend/src/admin/adminNavigationGuard.ts`、`frontend/src/admin/useAdminToken.ts`
- `docs/specs/FEAT-118-general-ontology-modeling-platform*.md`、`DESIGN.md`、`DESIGN.json`
- `.claw/tasks/TASK-213.md`、`.claw/task-board.md`、`.claw/current-status.md`、`.claw/test-report.md`

## Handoff

- 分支：`codex/TASK-213-general-ontology-v1`。
- 本轮使用的隔离 PostgreSQL 验证库已在测试通过后删除，不留本地测试状态。
- 严格遵循 `docs/production-release-runbook.md`，未完成真实验证不得标记 done 或声称已上线。
