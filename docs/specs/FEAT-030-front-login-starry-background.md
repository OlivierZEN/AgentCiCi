---
kind: feature-spec
version: 1
status: implemented
updated_at: 2026-05-12T02:21:42Z
owner_role: frontend-product-login
task_id: TASK-082
---

# FEAT-030 Front Login Starry Background

## Requirement

按用户确认的效果图调整前台登录页（`/` 未登录态，`login_mode2`）视觉：页面内容、旋转立方体、表单结构、居中布局和登录交互保持不变，只将背景与表单色彩改为深蓝星空版本。

## Design Direction

- 背景使用深午夜蓝、蓝黑与少量靛蓝深度，叠加稀疏细星点与中心弱蓝色空间感。
- 表单区域改为不透明深蓝面板，使用冷蓝灰边框和柔和暗色阴影，与星空背景形成清晰对比。
- 输入框、标签、链接、提示和主按钮统一为冷蓝灰体系，避免金色、香槟色、玻璃拟态和霓虹科幻感。
- 保持产品登录页的克制可信，不新增营销文案、装饰区块或新 UI 元素。

## Implementation Notes

- 只修改 `frontend/src/styles.css` 中 `login-mode2` 相关样式。
- 未修改 `frontend/src/assistant/AssistantApp.tsx` 结构。
- 立方体尺寸、旋转动画、表单 DOM、字段、按钮和链接位置保持原实现。

## Acceptance

- 桌面登录页显示星空背景，表单与背景有足够对比，文字可读。
- 移动端 390px 宽度无横向溢出，立方体和表单仍居中。
- 前端构建通过，允许保留既有 Vite chunk-size warning。
