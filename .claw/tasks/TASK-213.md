---
kind: task-status
task_id: TASK-213
status: in_progress
updated_at: 2026-07-17T07:31:02Z
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
- 参考包直装路径已完成正向 V83 provenance 加固：普通工作区落 `MANUAL/NULL/NULL`，参考包工作区落 `REFERENCE_PACKAGE/包 ID/原始 classpath bytes SHA-256`；管理 API 返回这三项，前端恢复必须再精确匹配业务身份与当前管理员。同一管理员手工创建的完全同元数据工作区、错误包 ID 或错误指纹均不会被接管。V82 checksum 保持不变，V83 不新增表。
- 全新隔离 PostgreSQL 从零应用 79 个迁移到 V83；扩大后的本体与平台相关后端回归 127/127、前端 26 个文件 / 177 项、前端生产构建和后端打包全部通过。数据库仍为 13 张 ontology 表，V82 checksum 不变，V83 伪造来源、空包 ID、短指纹和大写指纹均被 CHECK 拒绝；临时库删除后回读为 0。
- 全新 1600×1000 真实浏览器复验了列表、向导、画布、映射脏状态跨页签保留、AI 禁止带脏映射生成/应用、离开取消/确认、技术预览 `expectedRevision=4`、全部 tab IDREF、发布门与默认焦点“取消”、版本历史；验收会话 console error/warning 和 document/body 横向溢出均为 0。
- 安全与规格终局独立复审均为 Approved，Critical 0 / Important 0。仅保留 mounted RouterProvider + deferred Promise 自动化以及更广参数化跨租户 404 覆盖两项 Minor，均不阻塞发布。
- 生产发布仍必须在签名提交、合并和 runbook 全部门禁通过后执行。

## Next Action

- 推送已复审分支、创建并合并 PR；同步干净 main 后，严格按 `docs/production-release-runbook.md` 执行 2.7.10 dry-run、备份、不可变发布和线上 API/浏览器验收。

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
- `backend/src/main/resources/db/migration/V83__ontology_workspace_provenance.sql`

## Handoff

- 分支：`codex/TASK-213-general-ontology-v1`。
- 本轮使用的隔离 PostgreSQL 验证库已在测试通过后删除，不留本地测试状态。
- 严格遵循 `docs/production-release-runbook.md`，未完成真实验证不得标记 done 或声称已上线。
