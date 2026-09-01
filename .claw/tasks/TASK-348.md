---
kind: task-status
task_id: TASK-348
feature_id: FEAT-207
status: done
priority: critical
owner_role: fullstack-agent
claimed_by: codex
spec_path: docs/specs/FEAT-207-public-presales-visitor-lifecycle.md
updated_at: 2026-09-01T11:46:20Z
updated_by: codex
---

# TASK-348 - 对外售前智能体重构与 Mary 演示落地

## 范围

- Website 访客档案、独立访问会话、摘要续聊选择和历史隔离。
- 售前限定、售中售后工单重定向、轮次上限、联系方式识别、线索幂等与礼貌收口。
- Embed 浮窗对应状态、继续/新咨询、工单入口和关闭态交互。
- 目标租户 `org3gxskla32gln3bvop` 的 Mary 通过正式 API/UI 完成演示配置。
- 自动化测试、治理门禁、本地 `main`、同提交双制品和 `cici.localhost` 验收。

## 完成条件

- [x] 首访、再访继续、再访新咨询三个链路符合 FEAT-207。
- [x] 售中售后不调用模型或业务查询工具，只引导登录 CloudCC 提交在线工单。
- [x] 第 6/8 轮与联系方式完成态在服务端强制执行，前端不可绕过。
- [x] 联系方式租户隔离、幂等、最小化保存且日志不泄露原值。
- [x] 后端/前端测试、构建、迁移、域名扫描和差异检查通过。
- [x] 实现提交进入本地 `main`，本地双制品、健康、重启、版本和资源指纹一致。
- [x] Mary 的演示配置由正式产品链路写入并回读，未直写数据库。

## 当前进展

- 已完成生命周期、服务端门禁、Embed 浮窗和自动化测试；目标租户 Mary 已通过正式 API 发布 v4，回读为 Website-only、零工具、零知识库、零可选技能。
- 首访售前、售中售后固定重定向、联系方式加密/脱敏、再访继续/新咨询均完成真实本地链路验证；浏览器确认再访选择态不会误显示完成卡片，选择前输入框保持禁用并给出明确提示。
- 实现与视觉修复提交已进入本地 `main@6fa12d731b84`；backend/frontend 运行 `2.8.68-dev.6fa12d7`，镜像 revision 一致、healthy/restart=0，Flyway V129 成功。
- 本地演示继续沿用既有 ACTIVE Owner 作为 `runAsUserId` 映射，但 Website 服务端强制关闭工具、用户记忆和附件；生产启用仍需专用最小权限身份。

## 下一步

- 本地技术交付完成；如需推广到 UAT/生产，先配置专用最小权限运行身份和环境侧 CloudCC 工单 URL，再单独执行发布及 HUMAN 业务验收。
