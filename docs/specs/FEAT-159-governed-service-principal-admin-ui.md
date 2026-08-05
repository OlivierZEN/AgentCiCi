---
kind: feature-spec
feature_id: FEAT-159
title: AgentCiCi 机器主体管理界面
status: implemented
owner_role: fullstack-agent
task_ids: TASK-267
related_decisions: "AgentCiCi 是全局 Principal、Keycloak client 与密钥轮换权威；Semattice 只保存租户投影"
related_issues: none
updated_at: 2026-08-05T05:08:00Z
updated_by: MANAGER-001
---

# FEAT-159 - AgentCiCi 机器主体管理界面

## 背景

当前组织控制台只有人类“用户”管理页；后端已有受 ORG_ADMIN 保护的 `/admin/service-principals` 生命周期与密钥轮换接口，但没有浏览器入口。因此 Oliver 无法自助查看后羿等开发者 SERVICE 的 Client ID 或安全轮换一次性 secret。

## 范围

- 在组织控制台“组织架构”下新增“机器主体”导航和 `/admin/service-principals` 页面。
- 显示当前组织的 SERVICE：显示名、Public ID、Client ID、类型、负责人、允许 scope、状态、创建/轮换时间。
- 支持刷新、选中查看、ACTIVE/SUSPENDED 生命周期切换和密钥轮换。
- 密钥轮换必须有明确确认；成功后只在内存中显示一次，可复制，不写 localStorage、URL、日志、页面列表或任务数据。

## 非范围

- 不在该页面创建无审批的新研发主体；新主体仍按受治理账户创建流程执行。
- 不展示或恢复既有 secret；只能轮换，且轮换使旧 secret 失效。
- 不改变 Semattice Principal、角色、组织、PDP 或 DEV Autopilot 数据。

## 安全与交互

- 页面继承 `AdminGuard` 的 ORG_ADMIN 门禁，浏览器只携带当前组织 Admin Bearer Token。
- 所有动作调用既有 AgentCiCi 后端接口；不直接访问 Keycloak、Semattice 或 DEV Autopilot。
- 轮换确认文案必须说明“旧密钥立即失效”和“新值仅显示一次”。
- 设计沿用组织控制台的紧凑、暖白/鎏金数据管理风格，不新增独立主题或演示数据。

## 验收标准

- 左侧“组织架构”可见“机器主体”，路由可直接打开。
- 后羿、悟空、哪吒和大乔等 SERVICE 能从真实 API 显示 Client ID 与负责人，不显示 secret。
- 暂停/恢复成功后刷新真实状态；暂停主体的轮换操作不可用。
- 轮换行为必须先确认，成功 secret 只存在于一次性安全面板；关闭后不可从页面恢复。
- 前端定向测试、现有后端回归、前端构建与 diff 检查通过；生产发布和受权会话验收等待后续授权。
