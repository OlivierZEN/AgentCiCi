# AGENTS

<!-- cc-aidev-guidelines-common:begin -->
## Required Skill

- This project follows the `cc-aidev-guidelines-common` skill.
- Every AI agent must automatically load and use this skill before any project work.
- If the skill is not installed in the current environment, install it first from GitHub: https://github.com/CloudCCAI/cloudcc-aidev-guidelines-common
- After the skill is available, follow its greenfield or brownfield workflow to maintain `README.md`, `AGENTS.md`, `.claw/` or `.ai-dev/`, and `docs/specs/`.

## 必须遵守

- 本项目遵循 `cc-aidev-guidelines-common` 技能。
- 所有 AI 智能体在开始任何项目工作前，都必须自动加载并使用此技能。
- 如果当前环境尚未安装该技能，必须先从 GitHub 安装：https://github.com/CloudCCAI/cloudcc-aidev-guidelines-common
- 技能可用后，必须按技能中的 Greenfield 或 Brownfield 流程维护 `README.md`、`AGENTS.md`、`.claw/` 或 `.ai-dev/` 以及 `docs/specs/`。
<!-- cc-aidev-guidelines-common:end -->

## Pull Request Completion Workflow

- When the user asks an AI agent to finish or handle a project PR, the default workflow is: inspect the PR, run the relevant local verification, fix any issues found, and merge the PR once local verification passes.
- Agents do not need to stop for a separate “Ready for review” or manual approval step after local verification succeeds, unless the user explicitly requests that pause.
- If the merge reports conflicts, the agent should inspect the conflicting files and resolve them using the repo’s existing behavior, specs, and project state as the source of truth.
- Only escalate merge conflicts to the user when the correct resolution cannot be determined safely from code, specs, tests, or product rules.
- After a successful merge, the agent must synchronize the local `main` branch with `origin/main`, update `.claw/current-status.md` and `.claw/test-report.md` when meaningful, and push those state updates if they were changed.

## PR 处理默认规则

- 当用户要求 AI 智能体完成或处理项目 PR 时，默认流程是：检查 PR、运行相关本地验证、修复发现的问题，并在本地验证通过后合并 PR。
- 本地验证通过后，不需要额外停下来等待“Ready for review”或人工确认，除非用户明确要求暂停。
- 如果合并时出现冲突，智能体应先读取冲突文件，并根据仓库既有行为、规格文档和项目状态自行判断并解决。
- 只有在无法从代码、规格、测试或产品规则中安全判断冲突解法时，才通知用户介入处理。
- PR 合并成功后，智能体必须同步本地 `main` 到 `origin/main`；如有有意义的状态变化，应更新 `.claw/current-status.md` 与 `.claw/test-report.md`，并推送这些状态记录。

## Design Context

- Default platform visual language name: `鎏金账房` (`The Gilded Ledger`).
- Reference files: `DESIGN.md` and `DESIGN.json`.
- Reuse this style for premium internal control-plane pages that need warm ivory surfaces, compact density, ink-heavy typography, and bright champagne-gold structural linework.
- Do not turn this style into marketing glamour: gold should emphasize borders, active states, focus, and premium actions, not large decorative fills.

## Impeccable Design Governance

- This project adopts the `impeccable` skill as the mandatory design workflow for any page analysis, design, redesign, critique, polish, or UI implementation.
- 本项目将 `impeccable` 设为页面设计的强制技能。凡是页面分析、设计、改版、评审、润色或 UI 实现，都必须先按该技能执行。
- Before editing page structure, styles, tokens, or component visuals, agents must load `PRODUCT.md` and `DESIGN.md` through the installed `impeccable` context workflow and treat them as active design context.
- 在修改页面结构、样式、设计 token 或组件视觉前，必须先通过已安装的 `impeccable` 上下文流程加载 `PRODUCT.md` 与 `DESIGN.md`，并将它们视为当前设计事实源。
- Default register for `/`, `/admin/*`, and `/platform/*` is `product`. Do not treat authenticated product surfaces as marketing or campaign pages unless a separate shaped brief explicitly says so.
- `/`、`/admin/*`、`/platform/*` 的默认 register 一律是 `product`。除非先完成单独的 shape brief 并确认，否则不得把这些认证后的产品页面按营销页、品牌页或活动页来设计。
- `PRODUCT.md`, `DESIGN.md`, and `DESIGN.json` are the project design source of truth. If a page change alters the visual language, tokens, or component vocabulary, update these files in the same session.
- `PRODUCT.md`、`DESIGN.md`、`DESIGN.json` 是项目设计事实源。只要页面改动影响视觉语言、设计 token 或组件表达，就必须在同一会话同步更新这些文件。
- Page implementation must follow `DESIGN.md` > `Page Implementation Quality Workflow`: build the smallest runnable version first, run the local app, capture desktop and mobile full-page screenshots, inspect them with designer/QA judgment, fix issues, reshoot, and only finish after responsive layout, visual hierarchy, text fit, and key interaction feedback are verified.
- 页面实现必须遵循 `DESIGN.md` 的 `Page Implementation Quality Workflow`：先搭建可运行最小版本，再本地运行并截取桌面端与移动端完整页面截图，用设计师与 QA 视角检查，修复后复测截图；只有响应式布局、视觉层级、文本适配和关键交互反馈都确认后才能收尾。
- For critical UI modules, run focused A/B comparisons when there are meaningful alternatives; choose the variant with clearer task flow, better readability, stronger accessibility, and closer alignment with `鎏金账房`, then remove unused experimental code.
- 关键 UI 模块存在有效替代方案时，必须做聚焦 A/B 对比；选择任务流更清晰、可读性更好、可访问性更稳、且更符合 `鎏金账房` 的方案，并移除未采用的实验代码。
- Use image generation or a deliberate asset workflow only when the page genuinely needs bitmap illustrations, icons, empty-state visuals, or brand materials; generated assets must match the product register and must not become decorative clutter.
- 仅当页面确实需要位图插图、图标、空态视觉或品牌素材时，才使用 imagegen 或明确的素材流程；生成素材必须贴合 product register，不得变成装饰噪音。
- The shared baseline across product pages is `鎏金账房`: warm ivory surfaces, compact density, ink-heavy typography, and champagne-gold structural linework. Gold is for borders, focus, active states, and premium actions, not decorative fill.
- 项目产品页共享基线是 `鎏金账房`：暖象牙底、紧凑密度、墨色文字、香槟金结构线。金色只用于边框、焦点、激活态和高价值操作，不用于大面积装饰性铺色。
- Newly added product UI must follow `DESIGN.md` > `Product UI Scale`: default control and menu text is 13px, secondary metadata is 11-12px, compact tools and icon buttons are 32-34px high with 15-16px icons. Do not introduce page-local oversized fonts, fat buttons, or card-like picker rows for isolated features.
- 新增产品 UI 必须遵循 `DESIGN.md` 的 `Product UI Scale`：默认控件和菜单文字为 13px，辅助信息为 11-12px，紧凑工具按钮和图标按钮高度为 32-34px、图标为 15-16px。不得为单个功能新增局部大字号、厚按钮或卡片化的大号选择列表。
- Strict product UI rule: do not add background boxes inside framed product panels. Rows, tabs, search interiors, status labels, trace details, metric groups, and detail summaries must use text hierarchy and the minimum necessary 1px divider lines. No box-in-box, no per-row background blocks, no selected-row fills, no hover fills, no chip backgrounds, no row shadows, and no inner box-shadow focus frames.
- 产品页最严格 UI 规范：已被外层面板框定的区域内部，不得再加背景框。行、tab、搜索框内部、状态文字、链路详情、指标组和摘要块只能使用文字层级与必要的 1px 分隔线；严禁框套框、逐行背景块、选中背景、hover 背景、chip 背景、行阴影和内层 box-shadow 焦点框。
- Strict selected-state rule: selected, active, hover, pressed, focus, and focus-visible states inside product panels must never add `box-shadow`, glow, row shadow, inset shadow, raised-card treatment, or browser-like focus shadow. Prefer text color, font weight, or tab underline; do not add a new border for selection when text hierarchy or an existing divider is enough.
- 产品页选中态硬规则：面板内部的 selected、active、hover、pressed、focus、focus-visible 状态绝对不要加 `box-shadow`、发光、行阴影、内阴影、浮起卡片感或浏览器式焦点阴影。优先用文字颜色、字重或 tab 下划线表达；文字层级或已有分隔线能表达时，不要为选中态新增边框。
- Product-panel tabs, row actions, filter labels, status actions, and inline text commands must never use rounded bordered background button chrome. No curved-border white backgrounds, pill/card backgrounds, hover fills, or shadowed mini-buttons for these controls; use plain text, text color, font weight, underline, or a 1px divider/underline instead.
- 产品面板内部的 tab、行操作、筛选标签、状态操作和内联文字命令禁止使用带弧形边框的背景按钮样式：不得出现圆角白底、胶囊/小卡背景、边框按钮壳、hover 背景填充或阴影；只能用纯文本、文字颜色、字重、下划线或 1px 分隔线表达。
- Product tabs, scope filters, and filter labels implemented as native `button` elements must reset default, hover, active, selected, focus, and focus-visible states to transparent background, 0 radius, no shadow, and no transform. Never rely on global button styles being overridden only in one state.
- 用原生 `button` 实现的产品 tab、范围筛选和筛选标签，必须在默认、hover、active、selected、focus、focus-visible 全状态显式重置为透明背景、0 圆角、无阴影、无 transform。不得只覆盖某一个状态后让全局按钮样式漏出来。
- Lightweight floating menus attached to composer tools, icon buttons, or row actions should use 12px primary text, optional 10-11px metadata only when needed, 13-14px icons, 26-30px rows, 168-220px width, opaque warm ivory surfaces, gold-mist borders, and restrained shadows only when needed. Compact skill/command/picker rows must not use hover backgrounds, selected backgrounds, per-row background blocks, row shadows, or show implementation codes/slugs by default.
- 挂在输入框工具、图标按钮或行操作上的轻量浮层菜单，应使用 12px 主文字、仅在必要时使用 10-11px 辅助信息、13-14px 图标、26-30px 行高、168-220px 宽度、不透明暖象牙表面、浅金边，并仅在必要时使用克制阴影。紧凑技能/指令/选择器行默认不得使用 hover 背景、选中背景、逐行背景块、行阴影，也不得显示实现代码或 slug。
- All page work must follow `impeccable` shared design laws and product-register rules, including restrained color strategy, clear type hierarchy, purposeful motion, stable component vocabulary, and the explicit anti-slop bans from the skill.
- 所有页面工作都必须遵循 `impeccable` 的共享设计法则与 product register 规则，包括克制的色彩策略、清晰的字阶层级、有意义的动效、稳定的组件词汇，以及技能中明确列出的反 AI 套板禁令。
- Buttons on product pages must use the shared `鎏金账房` primary / secondary / danger vocabulary. Cancel and secondary actions use warm white surfaces with gold-tinted borders; confirm, save, publish, and other primary actions use champagne-gold fill. Do not let page-local buttons fall back to legacy blue, teal, black, gradient, or unscoped global button styles.
- 产品页按钮必须统一遵守 `鎏金账房` 的 primary / secondary / danger 组件语汇。取消和次级操作使用暖白底、金色系边框；确认、保存、发布等主操作使用香槟金实心按钮。不得让页面局部按钮回退到旧蓝色、青绿色、黑色、渐变或未限定作用域的全局按钮样式。
- Product-page tabs must follow `DESIGN.md` > `Product Tabs`: text tabs only, no pill/chip/segmented containers and no rounded bordered background buttons; inactive tabs use warm bronze text, active tabs use strong bronze text with a 2px pressed-gold underline.
- 产品页页签必须遵循 `DESIGN.md` 的 `Product Tabs`：只使用文本 tab，不使用胶囊、chip、分段控件、带框小卡或带弧形边框背景按钮；未选中为暖棕文字，选中为深金文字 + 2px 金色下划线。
- Product tab active and focus styles must never use white rectangular backgrounds, selected-row fills, focus cards, shadows, glows, raised transforms, or rounded underline pills; the underline is a straight 2px rule.
- 产品 tab 的激活和焦点态不得出现白色矩形背景、选中填充、焦点小卡、阴影、发光、浮起 transform 或圆角胶囊下划线；下划线就是一条 2px 直线。
- Unless a feature spec explicitly says otherwise, every popup, picker, confirmation, editor, import preview, and publish dialog must be implemented as a modal window with blocking overlay, `role="dialog"`, `aria-modal="true"`, labelled heading, opaque surface, and unified footer actions.
- 除非功能规格明确说明例外，所有弹出框、选择器、确认框、编辑框、导入预览和发布弹框都必须实现为模式窗口：带遮罩、`role="dialog"`、`aria-modal="true"`、可关联标题、不透明面板和统一页脚按钮。
- Every popup/modal top-right close `×` must be a bare borderless icon/glyph. Do not wrap it in a visible bordered square, bordered circle, or button chrome; hover/focus may use only a subtle tinted background.
- 所有弹出框/模式窗口右上角关闭 `×` 必须是无边框的纯图标/字形。不得在外层出现可见方框、圆框或按钮边框；hover/focus 只能使用克制的浅色背景。
- Admin CRUD list pages must follow `DESIGN.md` > `Admin CRUD Lists`: keep list layouts compact, prevent search/filter/empty-result layout shifts, avoid horizontal scrolling, keep toolbar buttons visually unified, and use gold-family text-tab active states instead of green/teal unless the state is semantic success.
- 管理端 CRUD 列表页必须遵循 `DESIGN.md` 的 `Admin CRUD Lists`：列表保持紧凑，搜索、筛选、空结果不能撑开页面，不应出现横向滚动条；同一工具栏按钮必须统一；筛选选中态使用金色体系文本 tab，不再使用绿色/青绿色，除非表达语义成功。
- Do not apply flex/grid/block/clamp display styles directly to native table elements (`table`, `thead`, `tbody`, `tr`, `th`, `td`). Put truncation, flex actions, chips, and menus inside child elements so table alignment remains native and stable.
- 严禁直接把真实表格元素（`table`、`thead`、`tbody`、`tr`、`th`、`td`）改成 flex/grid/block/clamp display。截断、flex 操作区、标签和菜单必须放到单元格内部元素上，避免再次出现列错位。
- Dense row actions must use the unified three-dot hover/focus more menu: trigger and opaque vertical menu inside a child wrapper, preferably the shared `admin-row-menu` class family for new admin tables. Do not expose actions on list pages that the product flow forbids, such as standard-skill view/edit or list-page publishing when publishing belongs to an editor/governance flow.
- 高密度列表的行操作必须使用统一三点 hover/focus 菜单：触发器和不透明纵向菜单放在单元格内部子容器中，新管理端表格优先使用共享 `admin-row-menu` 类族。列表页不得暴露产品流程不允许的动作，例如标准技能查看/编辑，或把应在编辑/治理流程完成的发布动作放到列表页。
- Explicitly banned across this repo: decorative gradient text, thick side-stripe accent borders, default glassmorphism, oversized hero metrics in product pages, identical card grids, and modals as the first interaction pattern.
- 本仓库显式禁止：装饰性渐变文字、厚侧边强调线、默认玻璃拟态、产品页里的夸张 hero 指标块、无差别重复卡片宫格，以及把 modal 当作第一反应。
- Any intentional exception, new brand/marketing surface, or route-level visual language departure must be shaped first, user-confirmed, and documented in `docs/specs/` before implementation.
- 任何有意例外、全新品牌/营销页面，或路由级视觉语言偏离，都必须先完成 shape、得到用户确认，并在 `docs/specs/` 落文后再实现。
