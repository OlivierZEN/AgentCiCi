# FEAT-112 CloudCC 嵌入身份同步自动恢复

## 背景

CloudCC CRM 自定义页通过 `component-customer-workbench` pagecomponent 读取当前 CloudCC OpenAPI token 和用户信息，再向 AgentCiCi `/auth/cloudcc-sso/ticket` 交换一次性登录票据。2026-07-14 真实页面出现“CloudCC 身份同步失败，已切换为普通工作台入口”。

## 已验证事实

- 截图生成于 2026-07-14 18:26:30（Asia/Shanghai）。
- 线上 pagecomponent V11 与 customPage V5 使用同一组件 ID `6a526349e4b0a577cbba1fba`，嵌入引用和工作台 URL 正确。
- 相同 CloudCC 测试用户在 18:31 重新登录后，`ticket -> consume -> CRM 数据读取` 全链路返回 HTTP 200，进入 CCAdmin 对应的 AgentCiCi 成员并读取真实客户数据。
- 当前 pagecomponent 在首次失败前设置 `ssoStarted=true`，失败后不释放、不重试，也不重新读取 CloudCC token；一次临时网络、网关、旧 token 或服务重启即可把当前页面永久留在失败状态。

## 目标

- 保持 CloudCC token 用户、页面用户、AgentCiCi 成员三者必须一致的安全规则。
- 身份同步遇到临时失败时自动恢复，不要求用户关闭并重新打开 CRM 菜单。
- 最终失败后释放运行锁，允许用户刷新、组件重新挂载或显式重试。
- 页面提示区分“正在重试”和“最终失败”，但不得展示 token、内部堆栈或敏感身份数据。

## 设计

### 客户端重试

- 每次尝试都重新读取 CloudCC runtime/OpenAPI token 与用户信息。
- 最多执行 4 次，退避间隔为 0、800、2000、4000 毫秒。
- 网络异常、HTTP 408/425/429/5xx、空响应、CloudCC SDK 暂未就绪属于可重试错误。
- 明确的 HTTP 400/401/403 身份或绑定拒绝不循环重试，直接显示服务端安全文案。
- 成功后立即写入 `ssoTicket` iframe URL、清空提示和重试计时器。
- 最终失败必须把 `ssoStarted` 恢复为 false，并在 5 秒后进行一次新的恢复轮次；页面销毁时清理计时器。

### 双实现一致性

CloudCC 运行时可能走 Vue 挂载，也可能走 UMD DOM fallback。源码与预构建 bundle 必须使用相同重试次数、退避策略、失败释放和提示规则，避免两条路径行为漂移。

### 后端边界

本次不修改 `CloudccSsoService` 的身份一致性、成员绑定、当前成员 CloudCC accessToken 生成和一次性 ticket 校验。CRM token 与 AgentCiCi token 继续不互换；AgentCiCi 仅使用当前映射成员生成的 CloudCC token 调用 CRM/MCP。

## 验收标准

1. 首次 ticket 请求模拟 503、第二次成功时，页面自动进入带 `ssoTicket` 的工作台。
2. CloudCC SDK 首次未返回 token、后续返回时，页面自动恢复。
3. HTTP 401/403 不重复轰击接口，提示用户重新登录或联系管理员完成映射。
4. 所有失败路径都会释放 `ssoStarted`，组件重新挂载可再次尝试。
5. Vue 源码与 UMD fallback 的重试常量和状态行为一致。
6. `cloudcc package pagecomponent --dry-run`、发布、customPage 绑定验证与真实 CRM 浏览器复验通过。
7. 生产发布后健康检查、Nginx、错误日志和 SSO ticket/consume 请求均通过。

## 引用的实施技能文档

- `platform/overview introduction`
- `platform/security introduction`
- `platform/integrationPatterns introduction`
- `platform/pagecomponent introduction`

## 实施与验收结果

- Vue 组件与 UMD fallback 已使用同一重试常量、错误分类、锁释放和页面销毁清理规则。
- 故障注入验证第一次 ticket 请求返回 503、第二次返回成功时，无需重开 CRM 页面即可写入 `ssoTicket` 并清空错误提示。
- `cloudcc package pagecomponent customer-workbench . --dry-run` 通过；pagecomponent V13 已发布为 `6a561531e4b0a577cbba2080`。
- `customer_interaction_workbench` 已更新为 customPage V7，并精确引用 V13 组件 ID、嵌入模式和生产工作台 URL。
- CloudCC 验证接口未返回 runtime version，因此版本字段校验仍报告已知的 `stale_component_reference`；在线组件查询确认该 ID 为 V13，真实 CRM 运行时首次加载和连续刷新均成功完成身份同步并展示客户数据。
