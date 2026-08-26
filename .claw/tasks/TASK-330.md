---
kind: task-status
task_id: TASK-330
status: blocked
priority: critical
owner_role: release-agent
claimed_by: codex
updated_at: 2026-08-26T01:24:34Z
updated_by: codex
---

# TASK-330 - AgentCiCi 2.8.66 生产晋级

## 范围

- 将 UAT `2.8.66-beta.3 / e805c0ef7142` 按同一冻结提交晋级为生产 `2.8.66`。
- 生产发布只允许替换 AgentCiCi backend/frontend，保留 PostgreSQL、Redis、RabbitMQ、Qdrant、Semattice、DevAutopilot、Keycloak 和 Nginx 的独立发布边界。
- 冻结正式 tag、不可变镜像 digest、完整备份、回滚点、迁移、健康、匿名授权边界和稳定窗口证据。

## 当前证据

- 用户已确认 TASK-329 技能导出 UAT HUMAN 测试通过。
- 生产只读预检通过：AgentCiCi 当前为 `2.8.65 / 784ccd23e933`，六容器 healthy/restart=0，backend health UP、Flyway V122、frontend Nginx 有效，公开六项 smoke 通过；当前应用回滚点为 `2.8.65` 发布前备份与运行中的 `2.8.65` 制品。
- 候选 `2.8.66-beta.3` 冻结提交为 `e805c0ef7142`；远程 `main@90d317cb` 只比冻结提交多发布记录，正式制品不得改用该文档提交。
- `2.8.66` 同时包含 INT-025：AgentCiCi 严格要求 `devautopilot.standard.v1` 为 7 对象/87 字段，并显式拒绝 7×86。生产 Semattice 当前为 `1.0.6 / 6579ded320ad`，其已验证模板仍为 7×86；7×87 只在 UAT `1.0.7-beta.5 / 54f2ab93558f` 完成 SERVICE 技术探测。
- 同一候选内 TASK-326 缺陷业务链路、TASK-327 真实微信客服链路和 TASK-328 登录态运维文档交互仍在项目事实源中标记为 HUMAN pending；本轮用户确认只承接此前明确待验收的 TASK-329 技能导出。
- 本轮未创建 `2.8.66` tag、未构建或推送正式镜像、未创建生产备份、未修改生产配置或容器。

## 阻塞与下一步

- 阻塞 1：生产 Semattice 尚未提供 7×87；这是候选明确启用且不兼容 7×86 的跨项目契约，不能把 AgentCiCi 消费方先行切换并宣称完整交付。
- 阻塞 2：候选内 TASK-326/327/328 的 HUMAN 验收仍未确认通过。
- 需要用户明确确认上述三项 HUMAN 验收，并授权把 Semattice `1.0.7` 作为独立产品先行完成生产晋级；随后重新执行提供方 SERVICE 探测，再发布 AgentCiCi `2.8.66`。
