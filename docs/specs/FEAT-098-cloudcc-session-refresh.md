---
kind: feature-spec
feature_id: FEAT-098
title: CloudCC 用户会话自动恢复
status: ready
owner_role: fullstack-agent
task_ids: TASK-190
related_decisions: FEAT-081
related_issues: none
updated_at: 2026-07-11T14:40:42Z
updated_by: MANAGER-001
---

# FEAT-098 - CloudCC 用户会话自动恢复

## 问题

生产 CRM 嵌入页以已映射的 `CCAdmin` 进入客户互动工作台后，CloudCC `/openApi/common` 以 HTTP 200 返回 `result=false` 和“登录失败，请再次尝试重新登录”。当前客户端只在 HTTP 401 时刷新 Token，因此失效 Token 会继续留在缓存；页面并发初始化还可能在同一组织/用户缓存未命中时并发申请多个 Token。

## 目标

- 同一组织/用户的 Token 获取采用单次并发刷新，避免并发申请相互覆盖。
- 同时识别 HTTP 401 和 HTTP 200 业务体中的登录失效信号，清除缓存并刷新一次。
- 刷新失败时返回明确、可操作的 CloudCC 会话错误，不再显示 `Unexpected server error`。
- 保持身份边界：AgentCiCi Token 只用于本平台登录，CloudCC OpenAPI Token 仍由当前映射用户的 CloudCC 凭据生成。

## 验收标准

- 并发调用同一用户会话时只向 CloudCC Token 端点请求一次。
- 首次 OpenAPI 返回登录失效后自动换取新 Token 并重试成功。
- 第二次仍失败时停止重试并返回明确错误。
- 真实 CRM 嵌入页恢复“CloudCC CRM 已连接”，客户队列和详情可读取，浏览器无 `Unexpected server error`。

