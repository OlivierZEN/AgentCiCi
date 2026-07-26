---
kind: feature-spec
feature_id: FEAT-144
title: 全局用户公共编号
status: implemented
owner_role: platform-governance-agent
task_ids: TASK-251
related_decisions: none
related_issues: none
updated_at: 2026-07-26T13:05:00Z
updated_by: MANAGER-001
---

# FEAT-144 - 全局用户公共编号

## 背景与目标

AgentCiCi 的全局身份主键 `user_account.id` 是 UUID v4，适合内部关联，但不适合运营人员在目录、工单和跨应用排查中快速识别。用户已确认新增一个稳定、短且可展示的公共编号，不改变现有 UUID、邮箱/手机号登录标识或 Keycloak `sub` 身份绑定。

## 已确认规则

- 格式固定为 `UYYYYXXXXXXXX`，例如 `U2026A7K29MXQ`。
- `U` 表示全局用户，`YYYY` 是该账户的创建年份，后 8 位由大写英文字母和数字随机生成。
- 正则为 `^U[0-9]{4}[A-Z0-9]{8}$`，总长 13。
- 编号创建后不可变，不因邮箱、手机号、昵称、组织关系或后续年份变化而改写。
- 历史账户按其现有 `created_at` 年份回填；新账户按创建时的年份生成。
- `public_id` 是人工识别和受控 API 展示 ID，不是密码、Bearer Token、授权依据或 Keycloak `sub` 的替代品。

## 范围

### In Scope

- 在 `user_account` 增加不可空、全局唯一的 `public_id`。
- 用可重复执行的 Flyway 正向迁移回填全部已有账户，并在新账户插入时由数据库触发器自动生成编号。
- 以数据库格式约束、唯一约束和冲突重试保证编号有效且唯一。
- 将公共编号加入平台受保护的 `GET /platform/registered-users` 响应，并在既有“注册用户”表格的用户信息下显示。
- 为迁移、生成规则、服务响应和现有桌面表格补充定向回归。

### Out Of Scope

- 不改写已有 `user_account.id`、`account_external_identity`、Keycloak 用户、JWT/OACT claim、密码、登录流程或成员授权。
- 不把公共编号作为邮箱/手机号的替代登录名，不新增注册、邀请、编辑或删除用户界面。
- 不进行生产发布、历史迁移 repair、移动端适配或外部系统数据写入。

## 数据与生成设计

`user_account` 新增：

```text
public_id varchar(13) not null unique
```

Flyway V97 的顺序：

1. 增加可空列和格式检查约束。
2. 创建数据库函数，按传入的创建时间构造前缀 `UYYYY`，并逐位从 `A-Z0-9` 中随机选择 8 位后缀。
3. 对历史记录调用该函数并检查唯一性后回填。
4. 创建 `BEFORE INSERT` 触发器，确保所有既有 Java 创建路径均自动获得编号。
5. 将列设为 `NOT NULL` 并创建唯一约束。

唯一约束是最终裁决。发生极低概率冲突时，函数重试生成候选；迁移必须失败而不是产生重复或空编号。

## 接口与界面

`GET /platform/registered-users` 的每个 item 新增：

```json
{ "publicId": "U2026A7K29MXQ" }
```

现有平台“注册用户”目录在用户名称下先显示公共编号，UUID 保留为次级技术信息。页面沿用既有主题 token、表格、无新增控件、无新路由、无移动端范围。

## 验收标准

- 新旧所有 `user_account` 均有符合格式的 `public_id`，且全表唯一。
- 历史账户的 `UYYYY` 与其 `created_at` 年份一致；新插入账户自动取得当前创建年份前缀。
- 任何已有创建全局账户的 Java 路径不需要手工传入 `public_id`。
- 既有 UUID 主键、邮箱/手机号、Keycloak `issuer + sub` 映射和组织成员关系不变。
- 平台注册用户 API 返回 `publicId`，桌面表格在用户名称下展示该值。
- 后端定向测试、迁移集成验证、前端定向测试、前端生产构建、桌面截图检查和 `git diff --check` 通过。

## 风险与回滚

- 该迁移只新增数据，不删除或重写现有身份键。应用代码可回滚，但已分配的公共编号应保留。
- 生产发布前必须在全新 PostgreSQL 库验证 V1→V97，并先备份数据库。Flyway 正向迁移不自动回滚。

## 实现进展

- 已新增 V97：对既有账户按 `created_at` 回填、对后续插入通过触发器自动生成，并以格式检查、唯一约束和不可变触发器保护编号。
- `UserAccountEntity`、平台目录响应和既有用户信息行已加入 `publicId`。UUID 仍作为 API 内部键返回，并作为编号的悬停辅助信息保留，不再作为人工主识别值。
- 定向后端测试、全新 PostgreSQL 16 的 V1→V96→V97 回填/插入/不可变验证、前端定向测试、生产构建、模拟受权平台目录的 1280px 桌面截图和 `git diff --check` 均通过。
- 未发布生产，因此真实生产账户会在下一次受权发布执行 Flyway V97 后自动完成回填。
