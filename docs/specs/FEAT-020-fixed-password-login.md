---
kind: feature-spec
feature_id: FEAT-020
title: Fixed password login
status: implemented
owner_role: frontend-backend-auth
task_ids: TASK-058
related_decisions: none
related_issues: none
updated_at: 2026-05-07T00:00:00+08:00
updated_by: ai
---

# FEAT-020 - Fixed password login

## 背景与目标

- 三端登录不再使用短信验证码。
- 本阶段使用数据库初始化的统一固定密码 `szyd1234` 完成登录。
- 助手端、组织管理端、平台端登录入口保持原有手机号、组织和角色分层逻辑，只替换认证凭证。

## 范围

### In Scope

- 新增数据库表保存固定密码凭证，使用 PBKDF2 哈希，不保存明文。
- 新增 `POST /auth/password/login`。
- 三端登录页改为手机号 + 固定密码。
- 本地 E2E 脚本和后端测试 helper 改走密码登录。
- 短信验证码登录 API 返回禁用提示。

### Out Of Scope

- 本次不做每用户独立密码、密码修改、找回密码、SSO 或 MFA。
- 本次不迁移 CloudCC 用户绑定凭证，也不改变角色白名单规则。

## 用户场景

- 员工在助手端输入组织 ID、手机号和固定密码后进入工作台。
- 组织管理员使用管理员手机号和固定密码进入 `/admin/*`。
- 平台运营人员使用带平台角色的手机号和固定密码进入 `/platform/*`。
- 输入错误密码时返回明确登录失败，不创建 token。

## 现状与约束

- 角色仍由用户记录 `role_code` 与 `app.auth.*-mobiles` 白名单共同决定。
- 首次登录手机号不存在时仍会自动创建用户；命中 bootstrap 管理员手机号时会创建或提升为 `ORG_ADMIN`。
- 固定密码是本地/内部阶段方案，生产前需要替换为每用户密码或 SSO。

## 方案设计

- `auth_password` 表保存一条 `default` 凭证，字段包含算法、salt、iterations 和 hash。
- 后端登录时从数据库读取固定凭证，用 `PBKDF2WithHmacSHA256` 派生结果并做常量时间比较。
- 认证通过后复用原登录发 token、创建用户和角色提升逻辑。
- 前端沿用现有登录卡片和 `鎏金账房` 样式，只把验证码输入与发送按钮替换为密码输入与单一登录动作。

## 接口与数据影响

- 新增 `backend/src/main/resources/db/migration/V40__fixed_password_login.sql`。
- 新增 `POST /auth/password/login` 请求：

```json
{
  "orgId": "demo-org",
  "mobile": "13900009999",
  "password": "szyd1234"
}
```

- `/auth/sms/send` 与 `/auth/sms/login` 保留路由但返回 `SMS verification login is disabled`。

## 验收标准

- 三端页面不再出现验证码输入、获取验证码按钮或 `devCode` 文案。
- 使用 `szyd1234` 可登录；错误密码失败。
- 后端编译、Auth 集成测试、前端构建通过。
- `scripts/e2e-local-business.sh` 使用 `/auth/password/login`。

## 风险与回滚

- 风险：固定密码是共享凭证，不适合生产公网环境。
- 回滚：恢复前端验证码字段和 `/auth/sms/*` 登录行为，或新增 per-user password/SSO 后替换本方案。

## 实现进展

- 当前状态：已实现，待验证命令记录。
- 已完成项：后端固定密码表、密码登录接口、三端 UI、测试 helper 与脚本文档更新。
- 未完成项：生产级密码治理或 SSO。

## 交接说明

- 后续若进入生产化，优先把 `auth_password` 的单行固定凭证升级为每用户密码表或外部身份源。
- 不要重新暴露 `devCode` 或短信验证码入口，除非有新的安全方案和用户确认。
