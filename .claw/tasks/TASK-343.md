---
kind: task-status
task_id: TASK-343
feature_id: FEAT-204
status: in_progress
priority: high
owner_role: fullstack-agent
claimed_by: codex
spec_path: docs/specs/FEAT-204-web-widget-publish-channel.md
updated_at: 2026-08-28T13:37:48Z
updated_by: codex
---

# TASK-343 - Web 浮窗使用系统智能体头像

## 范围

- 公开 Web 配置和 website 会话只从已发布智能体定义读取 `avatarBase64`，不接受 SDK、页面或访客覆盖 Agent 身份。
- 官网启动器、浮窗标题、欢迎态和智能体消息统一显示系统智能体头像；图片缺失或加载失败时保留文字回退。
- Agent Builder 预览和生成的安装代码使用同一公开配置链路。
- 受信 `page` 嵌入继续使用既有固定思思身份，不扩大本次视觉变化。

## 完成条件

- [ ] demo 租户 `org3gxskla32gln3bvop` 的 `sales-agent` 公开配置返回系统内已保存头像。
- [ ] Base64 图片不进入短时 JWT；会话端按 Token 已绑定的公司与 Agent 服务端回读。
- [ ] 启动器、浮窗标题和智能体消息显示同一头像，图片失败时回退标识可见。
- [ ] 后端聚焦测试、前端聚焦/全量测试与 production build 通过。
- [ ] 实现提交进入本地 `main`，本地开发环境 backend/frontend 可追溯到同一代码提交。
- [ ] `https://cici.localhost/` 真实官网浮窗视觉、公开配置、会话、健康和版本指纹通过。

## 当前证据

- 数据只读回读：demo `sales-agent / 客服-Mary` 已发布且 `avatar_base64` 为 WebP data URL（11,707 字符）；当前缺口是 Web 发布链路未投影该字段，SDK/Embed 仍固定渲染 `Ci/思`。
