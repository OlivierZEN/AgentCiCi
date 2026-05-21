---
kind: feature-spec
feature_id: FEAT-043
title: Rename local PostgreSQL database to agentcici
status: completed
owner_role: MANAGER-001
task_ids: TASK-121
related_decisions: DEC-027
related_issues: none
updated_at: 2026-05-21T02:14:53Z
updated_by: ai
---

# FEAT-043 - Rename local PostgreSQL database to agentcici

## 背景与目标

- 当前项目本地和默认部署配置仍把 PostgreSQL 数据库名写成 `cici_assistant`。
- 产品与仓库当前统一使用 `AgentCiCi` / `agentcici` 品牌，继续沿用旧数据库名会增加环境配置、发布脚本和测试库命名的认知成本。
- 本次目标是把项目默认数据库名统一改为 `agentcici`，并同步更新本地开发、测试、演示脚本和部署默认值。

## 范围

### In Scope

- 更新本地 `docker-compose.yml`、Spring 默认数据源、测试默认数据源和相关脚本中的数据库名。
- 更新 ACR 部署默认环境变量和 compose 默认值中的数据库名。
- 更新与当前数据库默认值直接相关的运维/发布文档和项目状态记录。
- 执行本地 PostgreSQL 数据库重命名或等价迁移，使当前代码默认值与本机实际数据库一致。

### Out Of Scope

- 不修改 Java package、前端 `localStorage` 键名、API header/key 前缀等历史 `cici` 技术标识。
- 不重写历史任务中仅用于叙述过去事实的日志性记录，除非它们会误导当前默认配置。
- 不改动业务表结构、Flyway migration version 或现有业务数据内容。

## 用户场景

- 开发者启动本地依赖、运行后端或执行测试时，应默认连接 `agentcici` / `agentcici_test`。
- 部署人员使用示例 env 或 compose 模板时，不需要再手工把 `cici_assistant` 改成新数据库名。
- 运维人员查看当前项目状态或运行脚本时，能明确当前默认数据库名已经切换。

## 现状与约束

- 当前本地 PostgreSQL 由根目录 `docker-compose.yml` 提供，默认数据库名是 `cici_assistant`。
- Spring Boot `application.yml`、`application-local.yml` 和测试配置也直接写死了旧数据库名。
- 仓库里存在大量历史状态记录提到旧数据库名，其中部分是历史事实，不能机械全量替换。
- 当前工作树已有未提交改动，本次数据库名调整必须避免覆盖用户或既有任务变更。

## 方案设计

- 将“当前默认数据库名”统一改为 `agentcici`，测试默认库改为 `agentcici_test`。
- 对 task-scoped 临时测试库沿用同样前缀规则，例如 `agentcici_feat041`。
- 保留容器名、卷名和非数据库技术标识不变，避免扩大变更面。
- 对真实本地数据库执行 rename，并在需要时同步处理测试数据库。

## 接口与数据影响

- 配置层：`jdbc:postgresql://.../cici_assistant` -> `jdbc:postgresql://.../agentcici`
- 配置层：`.../cici_assistant_test` -> `.../agentcici_test`
- 部署层：`POSTGRES_DB` 默认值改为 `agentcici`
- 数据层：执行数据库 rename；不改 schema/table/data
- 回滚层：如需回退，可把配置改回 `cici_assistant` 并将数据库重命名回原名

## 任务拆分

- `TASK-121`：调整代码、脚本、部署默认值、状态文档，并完成本地数据库重命名验证。

## 验收标准

- `rg -n "cici_assistant"` 在代码、脚本、当前默认运维配置中不再出现数据库名旧值。
- 本地 PostgreSQL 存在并可连接 `agentcici`。
- 后端默认配置指向 `agentcici`，测试默认配置指向 `agentcici_test`。
- 至少完成一次针对性校验，证明重命名后的配置与本地数据库一致。

## 风险与回滚

- 风险：本地后端仍在占用旧数据库连接，可能阻塞 rename。
- 风险：测试库如果直接沿用旧库历史，仍可能保留既有 Flyway 漂移。
- 回滚：停止本地服务后，将 `agentcici` 重命名回 `cici_assistant`，并恢复配置默认值。

## 实现进展

- 2026-05-21：开始 FEAT-043，创建 `TASK-121` 承接数据库默认名迁移。
- 2026-05-21：已完成本地 compose、Spring runtime/test、部署默认值和辅助脚本中的数据库名替换；本地 PostgreSQL `cici_assistant` / `cici_assistant_test` 已重命名为 `agentcici` / `agentcici_test`，后端已用新默认值重启成功。

## 交接说明

- 后续接手者优先核对 `docker-compose.yml`、`backend/src/main/resources/*.yml`、`backend/src/test/resources/application.yml` 和部署示例 env。
- 如果本地 rename 被活动连接阻塞，先停掉本地 backend 再操作。
