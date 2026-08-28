---
kind: task-status
task_id: TASK-343
feature_id: FEAT-204
status: review
priority: high
owner_role: fullstack-agent
claimed_by: codex
spec_path: docs/specs/FEAT-204-web-widget-publish-channel.md
updated_at: 2026-08-28T14:11:32Z
updated_by: codex
---

# TASK-343 - Web 浮窗使用系统智能体头像

## 范围

- 公开 Web 配置和 website 会话只从已发布智能体定义读取 `avatarBase64`，不接受 SDK、页面或访客覆盖 Agent 身份。
- 官网启动器、浮窗标题、欢迎态和智能体消息统一显示系统智能体头像；图片缺失或加载失败时保留文字回退。
- Agent Builder 预览和生成的安装代码使用同一公开配置链路。
- 受信 `page` 嵌入继续使用既有固定思思身份，不扩大本次视觉变化。

## 完成条件

- [x] demo 租户 `org3gxskla32gln3bvop` 的 `sales-agent` 公开配置返回系统内已保存头像。
- [x] Base64 图片不进入短时 JWT；会话端按 Token 已绑定的公司与 Agent 服务端回读。
- [x] 启动器、浮窗标题和智能体消息显示同一头像，图片失败时回退标识可见。
- [x] 后端聚焦测试、前端聚焦/全量测试与 production build 通过。
- [x] 实现提交进入本地 `main`，本地开发环境 backend/frontend 可追溯到同一代码提交。
- [x] `https://cici.localhost/` 真实官网浮窗视觉、公开配置、会话、健康和版本指纹通过。

## 当前证据

- 实现提交 `9191e5a3eacf` 已进入本地 `main`：公开配置从已发布 Agent Definition 投影头像；website 会话按 Token 绑定的公司与 Agent 服务端回读；SDK、Embed 和 Builder 预览统一消费该字段；`source=cloudcc` 固定思思身份保持不变。
- 数据与安全：demo `sales-agent / 客服-Mary` 公开配置返回 11,707 字符 WebP data URL，不返回 `companyId/runAsUserId`；自动化证明短时 JWT 不包含 Base64 头像。
- 自动化：后端 `PublicWebWidgetServiceTest,SisiEmbedRuntimeServiceTest`、package，前端聚焦 3 文件/34 项与最终聚焦 2 文件/30 项、全量 60 文件/328 项、production build、两个 SDK 语法、环境域名、diff 门禁均通过；build 仅有既有大 chunk warning。
- 本地运行：backend/frontend 均运行 `2.8.68-dev.9191e5a / 9191e5a3eacf`，最终镜像 ID 分别为 `sha256:a62be82f3008` / `sha256:d528b5cf6cd8`，healthy/restart=0；health UP，Nginx 有效，官网、float embed、稳定 SDK 与公开 widget 路由均为 200。
- 浏览器：官网启动器加载 1 张 256×256 WebP 系统头像；展开浮窗后标题栏和 3 条智能体消息共 4 张头像均为同一 256×256 WebP，智能体名称为 `Mary`，没有退回通用 `Ci/M` 图片。
- 边界：第一次受管 release Docker 构建因 Docker Hub 拉取 `node:22-alpine` 连接重置失败，未替换容器；随后从同一本地 main 主机构建产物，覆入既有已验证 JRE/Nginx 基础镜像并重标记，只最小重建 backend/frontend。远程、UAT、生产、ACR 与 tag 未修改。
