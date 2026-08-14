---
kind: task-status
task_id: TASK-307
feature_id: FEAT-187
integration_id: INT-022
status: review
updated_at: 2026-08-14T06:52:00Z
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
- 第二次截图/日志实证：`semattice_project_delivery_transfer` 后仍出现 query 与 LLM 流，说明输入未进入精确确认分支而由模型自由调用。确认文本携带终止标点或 Markdown 包裹时，旧正则无法匹配。
- 提交 `107ac044` 会先归一化复制输入的反引号、引号和中英文终止标点；任何已识别的确认在产品经理能力缺失时也直接失败关闭，不会回退到模型或索要 Principal ID。新增该输入形态的定向回归。
- AgentCiCi backend/frontend 已同从本地 `main@107ac044` 构建为 `2.8.61-dev.107ac04`，镜像 label、backend `/system/version` 和首页静态资源一致，均 healthy/restart=0；完整 `cc-local-stack ./stack verify` 通过。用户需在已登录会话重发确认以进行真实业务写入。
