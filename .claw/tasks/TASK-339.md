---
kind: task-status
task_id: TASK-339
feature_id: FEAT-204
status: in_progress
priority: high
owner_role: frontend-agent
claimed_by: codex
updated_at: 2026-08-28T11:12:13Z
updated_by: codex
spec_path: docs/specs/FEAT-204-web-widget-publish-channel.md
---

# TASK-339 - 裸图标按钮全局透明交互治理

## 范围

- 移除 Web 浮窗话筒按钮 hover 与 listening 状态的主题色背景块，仅保留图标颜色反馈。
- 将标题栏、输入区、前台助手、客户工作台、Agent Builder、Admin、Platform 和共享依赖图的裸图标按钮统一接入 `cici-product-icon-button`。
- 收紧共享原语与设计事实源：默认、hover、focus、active、selected、listening、recording 均为透明背景，键盘 `focus-visible` 保留克制轮廓。
- 增加静态契约测试，阻止已审计的裸图标控件重新引入不透明背景。

## 完成条件

- [x] 根因定位到 Sisi 局部 hover 主题背景与公共裸图标原语仍允许浅色背景，而不是浏览器默认样式。
- [x] 聚焦契约、Sisi 与主题测试通过；前端全量测试、production build、域名和 diff 门禁通过。
- [ ] 实现提交进入本地 `main`，frontend 从该明确提交构建并更新 `https://cici.localhost/`。
- [ ] 浏览器回读话筒默认/hover 背景透明、图标变色、发送不受影响且 console 无错误。

## 当前证据

- 聚焦 3 文件/22 项通过；前端全量 59 文件/324 项通过；production build 通过，仅有既有大 chunk warning。
- `DESIGN.json`、`DESIGN.md` 与 FEAT-204 已同步跨页面透明背景规则。
- 静态扫描确认已审计裸图标控件没有不透明 background；环境域名门禁与 `git diff --check` 通过。
- 远程、UAT、生产未修改；本地运行与浏览器证据待补。
