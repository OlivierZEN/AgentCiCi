---
kind: task-status
task_id: TASK-288
status: review
updated_at: 2026-08-12T05:38:00Z
updated_by: codex
assignee: codex
owner_role: backend-agent
spec_path: docs/specs/FEAT-171-user-friendly-delivery-intake.md
related_issues: ISSUE-005
---

# TASK-288 - 产品经理可见缺陷草案确认恢复

## 问题

产品经理已展示完整“缺陷受理草稿”，但模型漏写不可见 `DEV_AUTOPILOT_INTAKE_V1` 标记；既有兜底仅识别“缺陷创建草案”和冒号字段，无法从 Markdown 表格恢复确认意图，导致短确认未调用 Semattice 创建工具。

## 范围

- 识别需求、缺陷、变更的“受理草稿/受理摘要”表述。
- 同时解析 Markdown 表格和冒号字段，并从关联项目说明中优先提取真实项目编号。
- 保留用户原始描述逐字内容和可信写后回读门禁。
- 使用本次故障同构草案完成回归测试和本地真实会话复测。

## 完成条件

- 对现有失败会话再次发送“确认提交缺陷”可创建 Semattice 记录并返回真实记录 ID、revision、correlation ID。
- 无草案、缺核心字段或无真实写入回读时仍不得声明成功。

## 验证结果

- 生产故障同构回归已恢复为 `create_defect`，父项目解析为 `DAS-A2AFD106`，并逐字保留用户原始描述。
- 编排层已把“缺陷/需求/变更受理草稿”纳入待处理草稿识别，确认与补充字段不再丢失上下文。
- 定向测试、后端打包、静态差异检查通过；修复镜像已更新到本地开发环境，backend healthy、restart=0、stack verify 通过。
- 本地现有手机号与 Keycloak 测试凭据不一致，真实浏览器业务提交未绕过身份门禁，待有效本地 HUMAN 登录后补充最终 Semattice 记录回读。
