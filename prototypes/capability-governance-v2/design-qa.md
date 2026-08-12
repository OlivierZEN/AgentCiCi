# FEAT-172 V5 Design QA

## Comparison setup

- source visual truth: `screenshots/v4-02-core-policy-home.png`（V4 单一策略对象页）以及用户确认的多策略包预设方向
- implementation screenshot: `screenshots/v5-01-policy-package-list.jpg`
- combined comparison: `screenshots/v5-policy-list-comparison.jpg`
- browser viewport: `1280 × 720` CSS px
- source pixels: `1265 × 712`，implementation pixels: `1265 × 712`
- density normalization: 两侧均为 `1x`，相同壳层、主题、路由和桌面状态
- state: 技能治理首页的“核心策略包”入口

## Findings

没有待处理的 P0 / P1 / P2 问题。

- 信息架构：通过。V5 将单一策略摘要替换为正式策略包表格，保留“技能列表 / 核心策略包”同层入口和单一“技能治理”侧栏菜单。
- 功能真实性：通过。只有“平台核心安全策略”显示 `v1 / 当前生效 / 管理`；数据出境、模型调用、工具执行策略统一显示“规划中”，版本和更新时间为空，不提供编辑、创建或发布动作。
- 渐进交互：通过。生效记录进入原有独立策略编辑页；规划记录只显示未启用说明并保持列表路由，没有让尚不存在的能力看起来已经可配置。
- 字体与层级：通过。列表标题、主记录、辅助说明、表头和状态沿用现有 14 / 13 / 11 px 产品层级；规划行的弱化仍保持可读。
- 间距与布局节奏：通过。策略包列表复用技能目录的表格节奏，完整利用主区域且没有新增卡片宫格；`clientWidth=scrollWidth=1265`，无外层横向溢出。
- 颜色与视觉 token：通过。继续使用 CRM 标准蓝、浅色画布、1px 结构线和克制的成功/中性状态色；规划行不使用装饰色、阴影或虚假强调。
- 图片与资产：通过。页面没有新增业务位图、插图或品牌资产需求；未使用占位图、CSS 绘图或手工 SVG。
- 文案与产品语义：通过。“当前只启用”“规划中”“当前功能边界”明确区分现有能力和未来方向；策略包名称与其治理边界一致。
- 可访问性与状态：通过。当前策略记录支持键盘 Enter；管理与说明均为语义按钮；规划项说明产生可读 `role=status` 反馈。
- 浏览器运行：通过。4 个策略包行、3 个规划态；规划说明保持 `#skills/policies`，当前策略进入 `#policy/edit` 并保留五类字段和保存草稿动作；console `0 error / 0 warning`。

## Full-view comparison evidence

- `v5-policy-list-comparison.jpg` 左侧是 V4 单对象页，右侧是 V5 列表页；两者使用相同视口和壳层。
- V5 在不改变导航和编辑路径的前提下，为未来多策略包提供稳定的信息列和状态语义，主区域密度仍符合平台运营工作台。

## Focused region comparison

聚焦比较核心策略内容区：V4 的当前版本、状态、适用技能和最近更新被完整映射到 V5 第一行；V4 的独立“编辑核心策略”动作映射为第一行“管理”。新增三行只包含规划名称、治理范围和适用对象，不生成虚假版本、更新时间或发布入口。

## Comparison history

- V4 风险：单对象首页无法预留未来多个策略包，后续增加新治理域可能再次改动信息架构。
- V5 修复：改为 7 列策略包表格，首行承接现有 `core-default`，另三行以规划态预设扩展方向。
- 复核结果：同屏对比未发现导航漂移、信息遗漏、拥挤、溢出或误导性动作。

## Implementation checklist

- [x] 核心策略包改为列表结构
- [x] 当前核心安全策略保持可管理
- [x] 数据出境、模型调用、工具执行三类规划态预设
- [x] 规划态不提供编辑、创建或发布动作
- [x] 现有独立策略编辑页与全部字段保持不变
- [x] 规划说明、键盘入口、横向溢出与 console 检查
- [x] 构建和 Sites worker 测试通过

## Follow-up polish

- P3：正式业务支持多策略包后，可把“说明”升级为只读详情抽屉，并由真实 API 返回适用范围、组合优先级与冲突规则；当前原型不应提前模拟这些行为。

final result: passed
