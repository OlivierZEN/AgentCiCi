---
kind: feature-spec
feature_id: FEAT-186
title: 研发交付记录的受治理精确删除路由
status: implemented
primary_project: agentcici
task_ids: TASK-306
related_integrations: INT-021
updated_at: 2026-08-14T04:05:00Z
updated_by: codex
---

# FEAT-186 - 研发交付记录的受治理精确删除路由

## 问题

`semattice-project-delivery-management` 已声明删除能力，删除 Service 也会调用 Semattice `runtime.record.delete`，但聊天编排器没有为精确确认指令建立确定性路由，Tool 编排器也未把 `semattice_project_delivery_delete` 分发到本地 Service。结果是产品经理只能查询记录，不能完成已确认的可恢复删除。

## 规则

- 只接受 `确认删除<对象>：<精确记录引用>`；对象类型与引用分别解析，不能由 UUID 猜测对象类型。
- 精确确认由服务端确定性路由，不交给模型自由决定是否调用删除 Tool。
- 实际执行主体必须是当前 Agent 绑定且状态有效的产品经理 SERVICE；HUMAN 只提供确认上下文。
- `runtime.record.delete` 只允许出现在 DevAutopilot `product_manager` SERVICE 的可选范围中；developer 等其他机器主体即使直接调用授权接口也必须被拒绝。
- 平台 SERVICE 最大许可清单负责令牌签发上限，DevAutopilot 产品经理模板负责初始权限；两者都必须显式包含 delete，且不能据此扩大其他角色权限。
- 删除只调用 Semattice `runtime.record.delete`，语义为移入回收站，30 天内可恢复；禁止数据库直写和物理删除。
- 只有 Semattice 返回相同 `record_id`、`lifecycle_state=trashed`、递增 revision、关联号并完成回读，才允许回复成功。
- 查不到、同名多条、revision 冲突、scope/PDP 拒绝或回执不完整时失败关闭，不继续处理后续记录。

## 验收

1. `确认删除任务：<UUID>` 保留 `task` 对象类型和完整 UUID。
2. Tool 编排器把删除调用交给当前 Agent 的受治理删除 Service。
3. 无完整 Semattice 回收站回读时，成功声明守卫继续阻止“已删除”。
4. 产品经理授权弹窗可选择 `runtime.record.delete`，开发者授权弹窗不显示该项且后端拒绝越权提交。
5. 聚焦测试、backend package、本地真实任务删除、回收站回读及完整 stack verify 通过。

## 回滚

回滚确定性路由和本地分发后，删除重新失败关闭；已移入回收站的记录仍由 Semattice 管理，可在 30 天内通过正式恢复能力恢复，不直接写数据库。
