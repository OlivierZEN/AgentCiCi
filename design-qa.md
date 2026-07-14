# TASK-207 前台主题一致性与视觉对齐设计 QA

## 验收范围

- 桌面端视口：`1600 × 1000`。
- 主题矩阵：八套主题均检查设置页、外层画布和轨道；`gilded`、`sakura`、`galaxy` 进一步覆盖智能体工作区、AI 应用菜单、数据洞察、客户洞察、知微画像、客户互动工作台、互动整理弹窗、专属记忆和个人设置。
- 组织入口：真实组织 `CloudCC 智能体应用DEMO` 显示首字母 `C`，不再显示固定 `CB`。

## 发现与修正

1. 数据洞察第二、三行只占三列，右侧形成大面积空白。排名和风险卡改为跨两列，四列栅格在桌面端完整闭合，同一行顶部与高度一致。
2. 数据图、智能体头像、会话对象头像和监控头像仍使用固定蓝、紫、橙、绿。新增四个主题序列 token，并按稳定身份映射到当前主题。
3. 知微画像原生复选框保留浏览器蓝色。复选框与单选框统一使用当前主题强调色。
4. AI 应用菜单、组织菜单、客户洞察、数据洞察、知微画像、监控、专属记忆、客户互动弹窗和智能体工作区仍有固定鎏金或固定浅色。改为受控主题画布、表面、文字、结构线、状态和序列色。
5. 智能体卡片历史玻璃拟态、蓝色阴影和悬停位移与产品视觉不一致。改为不透明主题表面、结构线和无位移选中态。

## 浏览器结果

- 八主题即时预览均正确切换 `data-theme`、画布、设置面板和轨道表面；`galaxy` 为唯一深色主题。
- `gilded`、`sakura`、`galaxy` 的重点业务表面均读取各自主题 token，没有旧主题白底或固定图表色残留。
- 数据洞察卡片行高分别在各行保持一致，四列栅格无右侧断档。
- AI 应用菜单五个条目均为 `44px` 高并共享同一左锚点；互动整理弹窗左右栏同顶、同高。
- 当前页面 `document/body scrollWidth == clientWidth == 1600`；控制台 error/warning 为 `0`。
- 八主题验收结束后已恢复 `gilded`。

## 证据

- `output/playwright/task207-workbench-gilded.png`
- `output/playwright/task207-agent-workspace-gilded.png`
- `output/playwright/task207-agent-workspace-galaxy.png`
- `output/playwright/task207-data-insight-gilded.png`
- `output/playwright/task207-data-insight-sakura.png`
- `output/playwright/task207-data-insight-galaxy.png`
- `output/playwright/task207-customer-insight-gilded.png`
- `output/playwright/task207-customer-workbench-gilded.png`
- `output/playwright/task207-ingestion-modal-gilded.png`
- `output/playwright/task207-zhiwei-gilded.png`
- `output/playwright/task207-zhiwei-galaxy.png`
- `output/playwright/task207-memory-sakura.png`
- `output/playwright/task207-ai-menu-sakura.png`
- `output/playwright/task207-settings-galaxy.png`
- `output/playwright/task207-settings-sakura.png`

final result: passed
