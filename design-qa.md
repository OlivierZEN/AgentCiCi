# TASK-209 运营平台登录页原图像素锁定复刻设计 QA

## 验收范围

- 路由：`/platform/login`。
- 默认桌面视口：`1672 × 941`，与用户批准原图尺寸一致。
- 视觉真值：`frontend/src/assets/platform-login-reference-1672x941.png`，SHA-256 为 `1b119105b2079248e91492e1ef44c32cd0cfba3b1d7f3917e452c50d270b37e9`。
- 默认态只验收原图背景；focus、输入和提示属于功能态，单独检查可读性与可用性。

## 默认态比对

- 左右拼接比较图：`output/playwright/task209-reference-comparison.png`；左侧为受控原图，右侧为 `1672 × 941` 本地浏览器默认态截图。
- 浏览器读取到根元素背景为受控原图 URL、`background-size: 100% 100%`、`background-position: 50% 50%`，根元素尺寸精确为 `1672 × 941`。
- 默认态 `is-engaged=false`；输入框的背景、文字、边框均透明/`0px`，按钮背景、文字、边框也均透明/`0px`。因此没有可见 HTML/CSS 重绘层覆盖原图。
- 截图通道返回 JPEG（即便调用方使用 `.png` 文件名），无法把其压缩后的二进制与 PNG 原件作无损文件比较；视觉拼接、背景引用和所有覆盖层透明的运行时样式共同作为像素锁定证据。

## 交互状态

- 两个 label、输入框和提交按钮均唯一存在；初始按钮禁用。
- 填入本地假凭据 `pixel-lock@example.com` / 本地测试密码后，按钮变为可用；账号、掩码密码、焦点边框和单一按钮文案可读，未提交假凭据。
- `document.documentElement.scrollWidth <= window.innerWidth`，控制台 `error/warning=0`。
- 交互图：`output/playwright/task209-reference-engaged-1672x941.png`。

## 结论

- P0：无。
- P1：无。
- P2：无。
- 默认状态通过原图资产与透明语义层实现，满足用户指定的原图高保真复刻方式；认证逻辑不变。

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
