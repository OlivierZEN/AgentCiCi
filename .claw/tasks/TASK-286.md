---
kind: task-status
task_id: TASK-286
status: completed
updated_at: 2026-08-11T17:17:17Z
updated_by: codex
assignee: codex
owner_role: backend-developer
spec_path: docs/specs/FEAT-173-semattice-console-handoff.md
depends_on: INT-013
---

# TASK-286 - Semattice 管理端单次交接票据提供方

## 范围

- 签发租户和成员绑定的短时单次 ticket，数据库只保存摘要。
- 以既有 Semattice HMAC 内部认证提供兑换端点，兑换时原子消费并签发 OACT。
- 管理端前端只调用同源入口并接受后端跳转结果，移除环境 Host 白名单。
- 后端严格校验 Semattice Console Public Origin；禁止在业务源码或测试中写入真实环境域名。

## 验收

- 正向签发/兑换成功；重放、过期、无权限、错误签名失败关闭。
- 浏览器响应不包含 OACT；日志不记录 ticket 或 OACT。
- 相关 backend/frontend 测试及域名扫描通过。

## 发布与回滚

- 新版本只提供单次 ticket 协议，删除旧 `#oact` 和浏览器 Bearer 入口，不保留兼容分支。
- 回滚应用即可停止新 ticket 签发；数据库表和自然过期记录可安全保留。

## 验证证据

- `AuthControllerTest`、`SematticeConsoleLocationTest`、`SematticeConsoleHandoffServiceTest`、`TenantContextFilterTest` 及 Keycloak 回通道定向测试通过。
- 前端 44 个测试文件、244 项测试及生产构建通过。
- 本地真实兑换、单次消费和 HMAC 入口验证通过。
