# TASK-339 裸图标按钮透明背景 QA

## 根因与设计决定

- 用户截图中的浅蓝框不是浏览器默认样式：Sisi 输入工具栏 hover 把 CRM 蓝主题的 `--sisi-panel=#e8f0fa` 设为话筒背景；公共裸图标按钮也仍允许浅色 hover/focus 背景。
- 设计事实源已改为跨公开页、前台、后台和平台页的统一规则：裸图标按钮所有指针、状态与录音态保持透明，hover 只改变图标颜色；键盘 `focus-visible` 保留克制轮廓。
- 共享 `cici-product-icon-button` 使用收口后的透明背景不变量；已审计的 Sisi、前台助手、客户工作台、Agent Builder、Admin、Platform、AI 表格和技能依赖图均接入或清除局部不透明覆盖。

## 浏览器结果

- 本地正式入口：`https://cici.localhost/`，`1280 × 720`，官网售前浮窗展开。
- 默认态：话筒 `background-color=rgba(0,0,0,0)`、`background-image=none`、`box-shadow=none`，图标 `rgb(23,35,61)`。
- hover：浏览器确认 `hovered=true`；背景、背景图和阴影保持透明/none，图标变为 CRM 蓝 `rgb(22,119,210)`，未出现圆角浅蓝背景块。
- 发送按钮、已有非空回复和浮窗布局未回归；页面 console `0 error / 0 warning`。

final result: passed

---

# TASK-337 官网 Web 浮窗视觉 QA

## Comparison setup

- source visual truth: 用户提供的完整浮窗截图与关闭按钮 hover 局部截图。
- implementation screenshot: 本地正式入口 `https://cici.localhost/`，`1280 × 720` 桌面视口，官网浮窗展开且完成真实回复。
- combined comparison: 本轮把两张用户参考图与实现截图合成为同一比较输入后复核。
- state: CRM 标准蓝、历史会话、输入框空态、发送禁用态、关闭按钮 hover。

## Findings

没有待处理的 P0 / P1 / P2 问题。

- 主题：通过。根节点 `data-theme=crm-blue`；画布、文本、结构线、强调色分别回读 `#f3f7fc / #17233d / #cbd8e8 / #1677d2`，启动器也使用相同蓝色令牌。
- 输入区：通过。附件按钮数量为 `0`，语音按钮保持左侧；发送按钮固定在右侧，空态和可用态均为 `33 × 33`，可用态为 `#1677d2` 白色箭头。
- hover：通过。鼠标置于关闭按钮时背景与边框均保持透明，只将图标改为 `#1677d2`；键盘 `focus-visible` 轮廓仍保留。
- 功能：通过。浮窗发送“请用一句话确认Web浮窗发送功能正常”后返回非空正文“Web浮窗发送功能运行正常。”，没有回归为空回复。
- 浏览器运行：通过。标题、展开、关闭、语音、发送语义控件存在；页面 console `0 error / 0 warning`。
- 兼容边界：通过。只在 `float` 隐藏附件入口，受信 `page` 模式仍保留附件上传组件和既有权限契约。

final result: passed

---

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
- 生产 `2.7.2`：同尺寸默认态的背景 URL 为带 hash 的 `platform-login-reference-1672x941` 资产，根尺寸/背景尺寸/透明覆盖层契约与本地一致；线上拼接图为 `output/playwright/task209-reference-production-comparison-2.7.2.png`，默认截图为 `output/playwright/task209-reference-production-2.7.2-1672x941.jpg`，交互态截图为 `output/playwright/task209-reference-production-engaged-2.7.2-1672x941.jpg`。

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
