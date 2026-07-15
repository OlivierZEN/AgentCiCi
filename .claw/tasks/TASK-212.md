---
kind: task-status
task_id: TASK-212
status: done
updated_at: 2026-07-15T17:22:27Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-212.yaml
spec_path: docs/specs/FEAT-117-skill-dag-governance-phase1.md
---

# TASK-212 - Skill DAG 只读治理闭环 Phase 1

## Scope

- 从现有工作流 Skill 版本钉住、Agent 绑定和资源白名单派生统一只读 DAG。
- 为 Agent Builder、调试解析链路和平台 Skill 影响分析提供生产级查询与桌面端视图。
- 完成后端与前端测试、桌面视觉/交互验收和生产发布。
- 不新增 DAG 编辑、Skill-to-Skill 调用、图持久化表、业务数据回填或移动端范围；允许 V81 并发创建两条只读影响查询索引。

## Current State

- 统一只读图服务、Agent/平台 API、共享 DAG 组件、Agent Builder/平台 Skill 接入和调试 Skill 解析链已完成。
- Agent 图显式隔离平台 token，平台图显式隔离组织 token；编译指纹已纳入 Skill 版本，历史 Manifest 显式版本精确回填。缺失版本运行时仅保留 Agent 直接边界、不可变 Manifest 与钉住版本边界，不再从当前 Skill 恢复 Prompt、Tool、知识库、移交或输出约束；工作流引用与当前绑定影响查询分别限制 1,000 条展示。
- V81 以非事务 `CREATE INDEX CONCURRENTLY` 创建工作流引用与当前绑定两个匹配索引，并在创建前清理同名索引以支持失败重试；独立干净 PostgreSQL 从空库应用 77 个迁移至 V81，重复执行迁移 SQL 后两个索引仍为 `indisvalid=true / indisready=true`。
- Agent 选择详情加载与保存、编译、发布、回滚、调试操作均有同步门禁；操作目标、Draft 与请求序号被冻结，旧操作结果不得写入新选择。
- 前端 18 个文件 / 110 项测试、生产构建、后端聚焦 9 类 / 22 项测试、HTTP 权限集成测试、后端 package、真实 API 权限矩阵和 `1600 x 1000` 浏览器验收均通过。
- 完整后端诊断共运行 341 项，出现 3 failure / 7 error，均落在 TASK-212 之外的既有平台身份、审计夹具、非空字段、模型配置及连接池基线；TASK-212 聚焦测试未失败。
- 最终独立复审确认 Critical / Important / Minor 均为 0，`Ready to merge: Yes`。
- PR #10 已合并为 `4814d2b9534d`，不可变 Git tag 与 backend/frontend 镜像 `2.7.8` 已推送；发布前四项快照备份位于 `/opt/cici/backups/20260716-011129-before-2.7.8-task212-skill-dag`。
- 生产只重建 backend/frontend，database、Redis、RabbitMQ、Qdrant 容器 ID 保持不变；六服务健康，版本接口为 `2.7.8 / 4814d2b9534d`，Flyway V81 成功且两个索引均 `indisvalid=true / indisready=true`。
- 生产 API 验证匿名 Agent 图 401、组织 token Agent 图 200、平台 token Agent 图 403、组织 token 平台图 403、平台 token 平台图 200，显式 Agent `versionNo=50` 返回 200；生产 `1600 x 1000` 两页面、缩放和节点详情通过，外层横向溢出与 console warning/error 均为 0。
- 稳定窗口 backend ERROR 与 Nginx 精确 5xx 均为 0。`x.agentcici.com` HTTP 301 / HTTPS 200；`onechat.agentcici.com` 继续存在既有 DNS 解析风险，显式生产 IP 的 HTTP 301 / HTTPS 200。

## Next Action

- TASK-212 已关闭；持续观察 `2.7.8` 健康、错误率和 DAG 查询时延。
- 后续 Skill-to-Skill 依赖、图编辑或独立子流程执行必须按 Phase 2 单独立项，不在本任务上继续扩张。

## Changed Files

- `backend/src/main/java/com/codehouse/ciciassistant/agent/**`
- `backend/src/main/java/com/codehouse/ciciassistant/skill/domain/SkillVersionRepository.java`
- `backend/src/main/java/com/codehouse/ciciassistant/skill/domain/AgentSkillBindingRepository.java`
- `backend/src/main/java/com/codehouse/ciciassistant/skill/service/SkillResolverService.java`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/db/migration/V81__skill_dag_impact_index.sql*`
- `backend/src/test/java/com/codehouse/ciciassistant/agent/**`
- `backend/src/test/java/com/codehouse/ciciassistant/skill/service/SkillResolverPinnedRuntimeBoundaryTest.java`
- `backend/src/test/resources/application.yml`
- `frontend/src/assistant/AgentBuilderShell*`
- `frontend/src/assistant/cici-ui.css`
- `frontend/src/platform/pages/PlatformSkillsPage*`
- `frontend/src/shared/SkillDependencyGraph*`
- `docs/specs/FEAT-117-skill-dag-governance-phase1.md`
- `.claw/tasks/TASK-212.md`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/test-report.md`
- `.claw/devops.md`

## Handoff

- 分支：`codex/TASK-212-skill-dag-governance`；PR：`https://github.com/OlivierZEN/CICI/pull/10`；合并提交：`4814d2b9534d8ba70d560b1a8a9b9a3dbe390717`。
- MANAGER-001 本机 SSH challenge-response 门禁与 assignment 代表路径检查均已通过。
- 生产发布严格使用 `docs/production-release-runbook.md` 和 `scripts/release-acr.sh`。
- 全量 Maven 的既有基线失败不得误报为 TASK-212 回归或全绿门禁；以聚焦测试、HTTP 权限集成、package 和真实 API/browser 验收作为本任务发布证据。
- 应用即时回滚点为健康 `2.7.7 / e47979167af8`；V81 仅新增索引，可在应用回滚时安全保留。
