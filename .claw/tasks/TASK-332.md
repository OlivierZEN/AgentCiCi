---
kind: task-status
task_id: TASK-332
feature_id: FEAT-202
status: done
priority: critical
owner_role: fullstack-agent
claimed_by: codex
updated_at: 2026-08-27T11:54:55Z
updated_by: codex
---

# TASK-332 - 思思嵌入式智能应用生产落地

## 范围

- 落地 `FEAT-202` 的 CloudCC 身份换票、会话隔离、真实 Agent 对话、附件、语音、证据、确认与回执。
- 提供 `/embed/sisi`、稳定/版本 SDK、页面/浮窗两种模式和组织管理配置。
- 完成定向测试、构建、安全门禁、桌面浏览器截图、批评修复、本地 `main` 合并与开发环境回读。

## 完成条件

- `docs/specs/FEAT-202-sisi-embedded-agent.md` 的技术验收项通过。
- 任务提交只包含 AgentCiCi 子仓文件，进入本地 `main`；不自动推送远端，不发布 UAT/生产。
- 本地开发全栈运行版本可追溯到本地 `main`，backend/frontend 联合回读一致。

## 完成证据

- 产品实现：已完成 shape brief、视觉探针、north-star 确认，以及 CloudCC 组织/用户名绑定换票、会话隔离、Agent 运行时、文档/图片附件、ASR Scope、SSE、SDK、页面/浮窗和组织管理配置。
- 自动化：PostgreSQL 16.9 空 schema 迁移至 V124，`EmbedAppIntegrationTest` 3 项通过；后端附件/租户上下文 16 项、模型身份聚焦测试与 production package 通过；最终本地 `main` 前端全量 58 文件/314 项通过，production build、SDK `node --check`/稳定版一致性和 `git diff --check` 通过。
- 本地主线与制品：实现提交 `935728872674de7cdcb0178ad976f22150a2c66d` 已进入本地 `main`。backend/frontend 从该提交构建为 `2.8.67-dev.9357288`，镜像 ID 为 `sha256:a2471ce5fe9e6cf7fd9b5ef255dee308a428faf195eccd156418f0fda38698ed` / `sha256:020a61966c716fdd5f157126dfb1cd1d65b9835a997559c4df2afea8a7a48ce8`。
- 本地运行：只替换 backend/frontend；两容器 healthy/restart=0，版本 API、镜像标签、容器环境和前端版本资源一致。V124、`sisi / 思思 / ENABLED / 1.0.0`、会话表、两个 SDK 200 且哈希一致；受管完整技术验证脚本通过，稳定窗口 backend severe=0、frontend 5xx/severe=0。
- 浏览器：正式 `https://cici.localhost/embed/sisi` 在无 Token 时正确停留在身份校验边界，页面三栏、固定“思”形象和安全接入文案可见，控制台 0 error/warning；此前页面、408px 浮窗和发送交互也已通过 Chromium 验证。
- 边界：本地代码与技术环境达到候选状态；真实 CloudCC 宿主的服务端换票、已登录用户业务对话和高风险回执仍需 HUMAN 集成验收。远程 `main`、UAT、生产、ACR 和 Git tag 未修改。
