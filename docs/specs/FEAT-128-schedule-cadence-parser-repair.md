---
kind: feature-spec
feature_id: FEAT-128
title: 定时任务周期解析越界修复
status: in_progress
owner_role: fullstack-agent
task_ids: TASK-223
related_decisions: none
related_issues: ISSUE-032
updated_at: 2026-07-22T00:40:00+08:00
updated_by: MANAGER-001
---

# FEAT-128 - 定时任务周期解析越界修复

## 背景与目标

- 用户在会话中输入“每天 09:00”确认定时任务周期后，服务端返回 `IndexOutOfBoundsException`，任务未创建。
- 根因待以回归测试确认：周期时钟正则没有定义捕获组，但解析代码读取了小时和分钟捕获组，导致合法时间文本被解析时越界。
- 修复后，明确的每日定时请求必须创建当前用户、当前智能体范围内的真实 workflow trigger，且返回下一次执行时间。

## 范围

### In Scope

- 修复自然语言时钟周期（至少“每天 09:00”）到六段 cron 的解析，保留秒、分、时、日、月、周字段语义。
- 保证下一次执行时间计算与 cron 的字段位置一致，并对非法 cron 结构或数值安全失败，不产生越界异常。
- 添加覆盖“每天 09:00”真实创建链路的后端回归测试，并保留缺少周期时不创建任务的既有边界。

### Out Of Scope

- 不改变个人 workflow 的权限边界、调度器、通知目标或已有 trigger。
- 不扩展新的自然语言周期类型，不新增移动端适配，也不改管理端视觉样式。

## 用户流程

1. 用户提出创建定时任务，并补充“每天 09:00”。
2. 系统把周期解析为有效 cron，发布当前用户/当前智能体的 workflow 版本并物化 trigger。
3. 系统返回 routine key、trigger id 与非空的 nextFireAt；不得出现 `IndexOutOfBoundsException`。

## 验收标准

- “每天 09:00 搜索美国 K12 教育机构”能生成 `0 0 9 * * *`，并创建 trigger 与 nextFireAt。
- 时钟表达式缺少或无效时返回明确校验错误，不创建 trigger，也不抛出数组/捕获组越界异常。
- 定向后端测试与编译通过；真实结果记录到 `.claw/test-report.md`。

## 风险与回滚

- 风险：更改捕获组后误解析上午/下午语义。通过每日、上午、下午样例测试锁定 24 小时制转换。
- 回滚：回退该后端解析修复即可恢复上一版本；不会删除既有 workflow 与执行审计。
