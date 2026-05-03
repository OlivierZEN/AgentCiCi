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
- The shared baseline across product pages is `鎏金账房`: warm ivory surfaces, compact density, ink-heavy typography, and champagne-gold structural linework. Gold is for borders, focus, active states, and premium actions, not decorative fill.
- 项目产品页共享基线是 `鎏金账房`：暖象牙底、紧凑密度、墨色文字、香槟金结构线。金色只用于边框、焦点、激活态和高价值操作，不用于大面积装饰性铺色。
- All page work must follow `impeccable` shared design laws and product-register rules, including restrained color strategy, clear type hierarchy, purposeful motion, stable component vocabulary, and the explicit anti-slop bans from the skill.
- 所有页面工作都必须遵循 `impeccable` 的共享设计法则与 product register 规则，包括克制的色彩策略、清晰的字阶层级、有意义的动效、稳定的组件词汇，以及技能中明确列出的反 AI 套板禁令。
- Buttons on product pages must use the shared `鎏金账房` primary / secondary / danger vocabulary. Cancel and secondary actions use warm white surfaces with gold-tinted borders; confirm, save, publish, and other primary actions use champagne-gold fill. Do not let page-local buttons fall back to legacy blue, teal, black, gradient, or unscoped global button styles.
- 产品页按钮必须统一遵守 `鎏金账房` 的 primary / secondary / danger 组件语汇。取消和次级操作使用暖白底、金色系边框；确认、保存、发布等主操作使用香槟金实心按钮。不得让页面局部按钮回退到旧蓝色、青绿色、黑色、渐变或未限定作用域的全局按钮样式。
- Unless a feature spec explicitly says otherwise, every popup, picker, confirmation, editor, import preview, and publish dialog must be implemented as a modal window with blocking overlay, `role="dialog"`, `aria-modal="true"`, labelled heading, opaque surface, and unified footer actions.
- 除非功能规格明确说明例外，所有弹出框、选择器、确认框、编辑框、导入预览和发布弹框都必须实现为模式窗口：带遮罩、`role="dialog"`、`aria-modal="true"`、可关联标题、不透明面板和统一页脚按钮。
- Admin CRUD list pages must follow `DESIGN.md` > `Admin CRUD Lists`: keep list layouts compact, prevent search/filter/empty-result layout shifts, avoid horizontal scrolling, keep toolbar buttons visually unified, and use gold-family text-tab active states instead of green/teal unless the state is semantic success.
- 管理端 CRUD 列表页必须遵循 `DESIGN.md` 的 `Admin CRUD Lists`：列表保持紧凑，搜索、筛选、空结果不能撑开页面，不应出现横向滚动条；同一工具栏按钮必须统一；筛选选中态使用金色体系文本 tab，不再使用绿色/青绿色，除非表达语义成功。
- Do not apply flex/grid/block/clamp display styles directly to native table elements (`table`, `thead`, `tbody`, `tr`, `th`, `td`). Put truncation, flex actions, chips, and menus inside child elements so table alignment remains native and stable.
- 严禁直接把真实表格元素（`table`、`thead`、`tbody`、`tr`、`th`、`td`）改成 flex/grid/block/clamp display。截断、flex 操作区、标签和菜单必须放到单元格内部元素上，避免再次出现列错位。
- Dense row actions should use a hover/focus more menu with opaque vertical menu items. Do not expose actions on list pages that the product flow forbids, such as standard-skill view/edit or list-page publishing when publishing belongs to an editor/governance flow.
- 高密度列表的行操作应使用 hover/focus 三点菜单，弹层必须不透明且菜单项样式一致。列表页不得暴露产品流程不允许的动作，例如标准技能查看/编辑，或把应在编辑/治理流程完成的发布动作放到列表页。
- Explicitly banned across this repo: decorative gradient text, thick side-stripe accent borders, default glassmorphism, oversized hero metrics in product pages, identical card grids, and modals as the first interaction pattern.
- 本仓库显式禁止：装饰性渐变文字、厚侧边强调线、默认玻璃拟态、产品页里的夸张 hero 指标块、无差别重复卡片宫格，以及把 modal 当作第一反应。
- Any intentional exception, new brand/marketing surface, or route-level visual language departure must be shaped first, user-confirmed, and documented in `docs/specs/` before implementation.
- 任何有意例外、全新品牌/营销页面，或路由级视觉语言偏离，都必须先完成 shape、得到用户确认，并在 `docs/specs/` 落文后再实现。
