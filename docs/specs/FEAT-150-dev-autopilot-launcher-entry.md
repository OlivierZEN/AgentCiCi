---
kind: feature-spec
feature_id: FEAT-150
title: DEV Autopilot AI 应用入口
status: active
owner_role: frontend-agent
task_ids: TASK-257
updated_at: 2026-07-31T00:25:00Z
updated_by: MANAGER-001
---

# FEAT-150 - DEV Autopilot AI 应用入口

## 目标

在 AgentCiCi 助手工作台左侧“AI应用”启动器中增加“DEV Autopilot”入口。用户点击该入口后，在当前浏览器直接跳转至独立部署的研发交付自驱系统：`https://x.agentcici.com/devautopilot/`。

## 设计

- 入口位于现有 AI 应用弹出菜单，与“AI 听记、客户互动工作台、客户洞察、数据洞察、知微画像”同级。
- 名称显示为“DEV Autopilot”，辅助标签为“研发交付”，图标标识为“研”。
- 复用现有紧凑菜单行、主题 token、键盘焦点和无阴影交互规则；不新建产品页、不新增视觉 token、不使用渐变或图像素材。
- 点击使用同页导航，不打开新窗口，也不在 AgentCiCi 中复制 DEV Autopilot 的业务数据或 UI。
- 独立应用继续读取当前 AgentCiCi 已登录会话，完成身份与 Semattice 委派链路。

## 验收标准

1. AI 应用启动器显示“DEV Autopilot / 研发交付”入口。
2. 点击入口后当前页面跳转至 `https://x.agentcici.com/devautopilot/`。
3. 既有内置应用仍在当前工作台内切换，未受影响。
4. 定向前端测试、生产构建和桌面端交互验证通过。

## 实施记录

- 启动器数据中新增 `dev-autopilot` 外部应用，使用既有菜单项和主题样式，未新增独立页面或样式分支。
- 外部项有明确的 `externalUrl`；点击时关闭菜单并在当前页跳转，查询参数不会把它误识别为 AgentCiCi 内置应用。
- 定向测试覆盖入口名称、辅助标签、标识和目标 URL；全量前端回归与生产构建已通过。生产验收将确认已登录桌面会话中的真实菜单与跳转。
