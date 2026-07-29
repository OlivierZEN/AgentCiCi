---
kind: feature-spec
feature_id: FEAT-147
title: company_id 迁移完整性审计与遗留修复
status: active
owner_role: backend-agent
task_ids: TASK-254
related_decisions: FEAT-135 company_id 统一
related_issues: UserEntity.org JPQL 与运维脚本仍引用 org_id/orgId
updated_at: 2026-07-29T12:10:00Z
updated_by: MANAGER-001
---

# FEAT-147 - company_id 迁移完整性审计与遗留修复

## 背景与目标

- 计费用量页暴露出 `UserEntity.org` 已迁移为 `company` 后仍残留的 JPQL 属性路径。
- 对当前源码、测试、脚本和部署入口审计后，发现本地 E2E 登录、Qdrant smoke 和生产演示数据 SQL 仍使用顶层企业的 `orgId` 或 `org_id`。
- 统一当前可执行代码与运维脚本使用 `companyId`/`company_id`，避免上线后在非页面路径再次触发迁移遗漏。

## 范围

### In Scope

- 修复计费构建者席位统计到 `UserEntity.company.id`。
- 将本地 E2E 登录脚本请求及其变量统一为 `companyId`/`E2E_COMPANY_ID`。
- 将 Qdrant smoke 的 payload/filter 统一为运行时客户端使用的 `company_id`。
- 将生产演示数据脚本的变量和 SQL 列从 `AGENT_ORG_ID`/`org_id` 统一为 `AGENT_COMPANY_ID`/`company_id`。
- 使用源码扫描、脚本语法校验、Python 编译和后端编译验证。

### Out Of Scope

- 不改写已应用的 Flyway 历史迁移；V94 必须继续负责生产旧 schema 的正向列名转换。
- 不删除前端对历史平台响应 `orgId` 的只读兼容归一逻辑。
- 不重命名仅用于局部语义、文案、历史 ID 值（例如现有 `org...` 的企业 ID 值）或不承载顶层企业字段的标识。
- 不修改数据库数据、计费规则、前端页面、Semattice、主线或生产环境。

## 审计结论与方案

| 分类 | 结论 | 处理 |
| --- | --- | --- |
| Java 实体、Repository、运行时 SQL | 当前仅发现账单 JPQL `member.org.id` | 改为 `member.company.id` |
| 本地 E2E 登录 | 后端 DTO 已要求 `companyId`，脚本仍发送 `orgId` | 统一请求字段与环境变量 |
| Qdrant smoke | 运行时客户端按 `company_id` 过滤，脚本仍写入 `org_id` | 同步 payload 与 filter |
| 生产演示数据 | V94 后 schema 已为 `company_id`，脚本仍写 `org_id` | 同步所有 SQL 与变量 |
| Flyway 历史/迁移测试/前端兼容 | 属于正向迁移证据或旧响应兼容 | 保留并明确排除 |

## 验收标准

- 当前可执行 Java、shell、Python 源码中不再存在未标注的顶层企业 `org_id`/`orgId` 引用。
- 账单席位统计保留公司、ACTIVE 与 OWNER/ORG_ADMIN 过滤条件，且不再访问 `UserEntity.org`。
- shell 语法、Python 编译、后端编译和静态扫描通过。
- 有 PostgreSQL 测试库时，组织管理员账单总览集成测试可通过；当前不可用时如实记录，不修改数据库环境。

## 风险与回滚

- 该任务不含迁移或数据写入。回退功能提交即可恢复旧脚本/查询；不应回退 V94 数据库迁移。

## 实现进展

- 当前状态：已完成范围审计和任务授权，待实现。
