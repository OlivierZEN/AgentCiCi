---
kind: task-status
task_id: TASK-202
status: done
updated_at: 2026-07-14T01:30:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-202.yaml
spec_path: docs/specs/FEAT-108-user-selectable-product-themes.md
---

# TASK-202 - 用户级产品主题偏好

## Scope

- 建立八套同构产品主题与共享语义令牌，默认保持鎏金账房。
- 为组织用户和平台用户增加账号级主题字段、读取与当前账号更新 API。
- 在个人设置增加主题选择器，支持即时预览、本地首屏应用和服务端同步。
- 让主应用、管理端、平台端和 CRM 嵌入客户互动工作台使用同一主题偏好。
- 更新设计事实源，完成后端/前端测试、构建和桌面端八主题视觉验收。

## Initial Findings

- 当前没有主题基础设施，`styles.css` 与 `assistant/cici-ui.css` 同时包含根变量和较多历史硬编码颜色。
- 普通用户跨组织身份由 `user_account` 承载，主题应落在该实体而不是组织成员 `user`。
- 平台运营账号使用独立 `platform_account`，需要独立持久化入口才能满足“每个用户”。
- 个人设置现有文本 Tab 和 `/auth/me` 更新事件可复用，不需要在顶栏新增主题按钮。

## Implementation Plan

- 更新 `DESIGN.json` 与 `DESIGN.md`，固化主题目录和同构规则。
- 增加 V80 迁移、实体字段、白名单归一化、当前用户 API 与聚焦测试。
- 建立前端主题模块、首屏初始化、设置面板和跨壳层同步。
- 用语义令牌覆盖关键产品表面，运行测试/构建并完成桌面截图矩阵。

## Verification

- `identity/assignment`: MANAGER-001 的 TASK-202 SSH challenge 与代表文件授权检查均为 allowed。
- `backend`: 独立 PostgreSQL schema 从空库迁移到 V80；`AuthFlowIntegrationTest,PlatformAuthIntegrationTest` 共 22 项，0 failure / 0 error，覆盖普通账号和平台账号默认值、保存、刷新/切换组织持久化及非法主题拒绝。
- `frontend`: Vitest 13 个文件、71 项通过；TypeScript/Vite 生产构建通过，仅保留既有大 chunk 提示；`git diff --check` 通过。
- `browser`: 真实桌面浏览器完成八主题设置页逐项切换；鎏金、CRM 蓝、海洋、樱花、熏衣紫、牛油果、红酒与星河均保持同一布局。主应用、管理端、平台端和客户互动工作台完成跨壳层检查；星河主题的平台卡片硬编码浅色已改为公共语义令牌。
- `persistence`: 普通账号主题在刷新和管理端重载后保持，平台账号星河主题刷新后仍恢复；验收结束后本地演示账号恢复鎏金默认主题。
- `release`: 未执行生产发布；实现将在当前任务提交合入 `origin/main`，等待独立发布指令。

## Handoff

- 目标分支：`codex/TASK-202-user-theme-preferences`。
- 不触碰未跟踪 `diagrams/`。
- 数据库测试必须使用独立 schema；共享 `agentcici_test` 保留历史固定手机号，直接复跑注册套件会受到夹具污染。
- 本任务不执行生产发布，除非用户另行明确要求。
