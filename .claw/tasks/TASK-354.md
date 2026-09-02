---
kind: task-status
task_id: TASK-354
status: review
priority: high
owner_role: frontend-agent
claimed_by: codex
updated_at: 2026-09-02T02:15:16Z
updated_by: codex
---

# TASK-354 - 登录后当前用户头像可见性修复

## 范围

- 修复主题全局按钮规则移除渐变背景后，侧栏当前用户头像变成透明、白色首字母几乎不可见的问题。
- 保持头像 `38px` 圆形几何、个人简档入口和图片头像渲染逻辑不变。
- 使用当前主题的强调色与表面色提供首字母回退，不增加新布局或视觉语言。

## 完成条件

- [x] 登录态真实页面回读证明故障态为透明背景、白色首字母，且按钮和用户数据均存在。
- [x] 头像不再依赖会被主题规则移除的 `background-image`。
- [x] 聚焦测试、前端全量测试、production build 与 `git diff --check` 通过。
- [x] 修复提交进入本地 `main`，本地 frontend 可追溯到该提交。
- [x] 登录态页面回读头像背景、文字、圆形尺寸、版本指纹和控制台通过。
- [x] 仅将本任务变更从远程基线移植到独立候选，未包含本地其他 ahead 提交。
- [ ] 用户目视确认当前页面显示符合预期。

## 当前证据

- 原始本地实现提交：`8131ca0deed7781747eafaafc672525eb512d3f1`。
- 远程候选实现提交：`a589167d`，从 `origin/main@5e697daf` 独立移植。
- 自动化：本地 `main` 主题聚焦 `13/13`、前端全量 `62 files / 342 tests`；远程隔离候选主题聚焦 `13/13`、前端全量 `61 files / 335 tests`；两者 production build 均通过。
- 治理边界：状态校验器仍被远程基线既有的历史规格/任务归档债务阻断；本任务新增状态文件未出现在错误列表中。
- 本地运行：frontend `2.8.68-dev.8131ca0 / 8131ca0d`，healthy/restart=0；正式 `/app` 资源带同版本指纹。
- 浏览器：计算样式 `background-color=rgb(135,98,35)`、`color=rgb(255,253,248)`、`38x38`、圆形；console error/warning=0。
