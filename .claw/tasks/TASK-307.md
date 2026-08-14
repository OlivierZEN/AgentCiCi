---
kind: task-status
task_id: TASK-307
feature_id: FEAT-187
integration_id: INT-022
status: review
updated_at: 2026-08-14T06:38:00Z
updated_by: codex
owner_role: integration-agent
spec_path: docs/specs/FEAT-187-devautopilot-name-based-task-transfer.md
---

# TASK-307 - 产品经理自动识别开发者并转派任务

## 当前证据

- 截图实证：精确确认 `确认将鲁班的任务转交给哪吒` 已进入 transfer Tool，但旧编排随后落入通用查询/模型回复，造成重复的人员 ID 提示；该次没有修改 Semattice owner。
- 提交 `d2047edf` 移除精确确认对通用运行模式的错误抑制；确认现在始终走确定性服务端转派。服务端将 scope 拒绝明确表述为 `runtime.record.transfer` 缺失和“未修改任务”，成功必须带 Semattice owner/revision 回读收据；收据门禁不接受无回读的“已转交”。
- `mvn -q -Dtest=SematticeProjectDeliveryTransferToolServiceTest,DeliveryWriteReceiptGuardTest test`、`mvn -q -DskipTests package`、`git diff --check` 通过；本地 backend 为 `2.8.61-dev.d2047ed`，Semattice `1.0.3-dev.81685db`，均 healthy/restart=0，完整 `cc-local-stack ./stack verify` 通过。
- 浏览器控制端没有可用已登录会话，未重放最终确认，任务 owner 保持鲁班。产品经理 SERVICE 必须具备已同步的 `runtime.record.transfer`；开发者 SERVICE 不授予该 scope。UAT/生产未修改。
