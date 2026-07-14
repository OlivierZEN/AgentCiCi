---
kind: task-status
task_id: TASK-210
status: done
updated_at: 2026-07-14T17:02:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: frontend-agent
assignment_path: .claw/assignments/TASK-210.yaml
spec_path: docs/specs/FEAT-116-customer-workbench-standard-channel-icons.md
---

# TASK-210 - 客户互动工作台标准渠道图标治理

## Scope

- 用公开规范的微信品牌图标替换通用消息气泡。
- 为电话、会议、邮件、CRM 任务、CRM 日程和客户反馈建立标准图标映射。
- 修复来源回退逻辑与动态中文 CSS 类名。
- 补充单元测试、构建和桌面端视觉验证。

## Current State

- 已确认并修复根因：微信曾映射到 Lucide 通用气泡，`CRM_TASK` 又落入默认消息图标。
- 微信现读取 Simple Icons 规范路径；电话、会议、邮件、CRM 任务、CRM 日程和客户反馈均使用独立 Lucide 语义图标。
- 样式类已改为稳定英文业务语义，不再由中文展示文本动态拼接；八主题共用相同形状、尺寸和轴线坐标。
- 完整时间线的重复 CRM event id 已使用组合键隔离，桌面端复验无新增控制台错误。
- Vitest 16 个文件、89 项与生产构建通过。
- 已按统一发布脚本上线 `2.7.4 / 3206fdbc196f`；生产备份、镜像摘要、健康检查和公网 smoke 均通过。线上随后由独立集成流程升级为 `2.7.5 / be80eea665c0`，该提交包含本任务修复并已重新完成 CloudCC 嵌入验收。
- AgentCiCi 真实页面与 CloudCC CRM 注入页均验证微信、电话、CRM 任务和 CRM 日程图标；CloudCC 嵌入页微信图标为单一公开规范 path、`24 × 24` viewBox，容器 `30 × 30`，时间线连续且外层无溢出。
- 两端浏览器控制台 error/warning 均为 0，CloudCC 当前用户身份同步和 CRM 数据读取正常。

## Next Action

- 已完成；持续监控新增 `sourceType` 的中性回退，不允许以自绘路径替换 Simple Icons/Lucide 标准资产。

## Changed Files

- `docs/specs/FEAT-116-customer-workbench-standard-channel-icons.md`
- `.claw/tasks/TASK-210.md`
- `.claw/assignments/TASK-210.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/test-report.md`
- `frontend/package.json`
- `frontend/package-lock.json`
- `frontend/src/assistant/cici-ui.css`
- `frontend/src/assistant/customer-workbench/CustomerWorkbenchApp.tsx`
- `frontend/src/assistant/customer-workbench/CustomerWorkbenchApp.test.ts`

## Handoff

- 分支：`codex/TASK-210-customer-workbench-standard-icons`。
- 不触碰 TASK-208 的 CRM 分析实现范围。
- 本地和生产视觉证据位于 `output/playwright/task210-*.png`，未纳入发布产物。
- CloudCC 运行时验证保留已知 `actualVersions=[]` 元数据 warning；页面组件 ID、customPage V9、嵌入 URL 与真实运行页均匹配，本次无需修改 CloudCC 元数据。
