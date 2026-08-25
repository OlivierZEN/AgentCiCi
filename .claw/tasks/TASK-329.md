---
kind: task-status
task_id: TASK-329
feature_id: FEAT-014
status: in_progress
priority: high
owner_role: fullstack-agent
claimed_by: codex
updated_at: 2026-08-25T09:08:47Z
updated_by: codex
---

# TASK-329 - 修复管理后台技能导出

## 范围

- 修复租户自定义已发布技能导出时，模型返回的 `manifest.json` 格式字段漂移导致服务端校验失败、无法下载的问题。
- 由服务端规范化技能包固定身份与格式字段，继续对最终八文件技能包执行 JSON、格式和敏感信息校验。
- 为技能列表导出补齐进行中状态以及网络、非 JSON 和下载异常反馈，避免点击后静默无结果。
- 补充后端模型 manifest 规范化与前端响应处理回归测试。

## 完成条件

- 模型输出把 `format` 写成非标准值时，服务端仍生成固定 `universal-skill-package@1.0` manifest，且不允许模型改写 `packageId`、技能版本或导出身份字段。
- 模型输出的其他 JSON、文件结构或敏感信息不合规时仍失败关闭，不能绕过最终包校验。
- 技能列表导出期间显示明确状态；创建任务、解析响应和下载任一阶段失败时均显示可读错误。
- 后端聚焦测试、前端聚焦测试、前端生产构建、后端 package、diff check 和状态校验完成。
- 实现提交进入 AgentCiCi 本地 `main`；本地全栈只能从该 `main` 提交构建并回读版本/健康/下载证据。

## 当前证据

- UAT `2.8.66-beta.2 / 525f0f610926` 公开 smoke 全部通过；本次只读浏览器复现没有修改 UAT 配置或部署。
- `POST /skills/137/exports` 到达 UAT backend 并返回 HTTP 400；页面捕获的业务错误为 `Export package validation failed: manifest format mismatch`，没有下载事件。
- 当前 `tryStandardizeByModel` 只检查模型字段非空，直到最终 `validateExportPackage` 才验证固定格式；因此可解析但格式漂移的模型 manifest 不会被规范化。
- 服务端已覆盖模型 manifest 的固定格式、包身份、技能版本、发布状态和导出身份字段；非对象 manifest 仍失败关闭，最终包校验保持不变。
- `SkillPackageServiceTest` 2 项、前端全量 56 文件/308 项、前端 production build、backend package 和 `git diff --check` 通过。
- `SkillGovernanceIntegrationTest` 被本机 `localhost:5432` 连接拒绝阻断在 Spring Context/Flyway 初始化，6 个方法均未进入断言，未报告为通过。

## 下一步

- 运行状态校验并提交到本地 `main`，从该提交构建 backend/frontend 本地镜像，完成运行指纹与真实导出下载验证。
