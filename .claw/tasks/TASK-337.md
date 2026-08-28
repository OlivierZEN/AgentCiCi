---
kind: task-status
task_id: TASK-337
feature_id: FEAT-204
status: in_progress
priority: high
owner_role: frontend-agent
claimed_by: codex
updated_at: 2026-08-28T09:25:13Z
updated_by: codex
spec_path: docs/specs/FEAT-204-web-widget-publish-channel.md
---

# TASK-337 - 官网 Web 浮窗 CRM 标准蓝与输入区修正

## 范围

- 官网 Web 浮窗默认使用既有 `crm-blue` 主题令牌，启动器与对话框保持一致。
- 发送按钮固定在输入工具栏右侧并修正禁用态、尺寸与图标居中。
- 公开网站浮窗不显示附件上传入口；受信 `page` 嵌入继续保留附件能力。
- 标题栏展开/关闭按钮 hover 只改变图标颜色，不显示背景框；保留键盘焦点轮廓。

## 完成条件

- [x] 源码按截图标注完成范围内调整，未修改 Token、会话和附件服务端契约。
- [ ] 聚焦测试、前端全量测试、production build、域名门禁和 diff check 通过。
- [ ] 变更提交进入本地 `main`，backend/frontend 从同一明确提交构建。
- [ ] `https://cici.localhost/` 官网真实浮窗完成视觉、发送和非空回复回归，容器及版本指纹一致。

## 当前证据

- 设计实现复用产品既有 `crm-blue` 主题令牌，没有引入新主题或图片资产。
- 浮窗只隐藏附件入口，`page` 模式仍保留上传组件与既有权限边界。
- 运行验证待完成；远程、UAT 与生产未修改。
