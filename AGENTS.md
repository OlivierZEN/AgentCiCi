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
- Explicitly banned across this repo: decorative gradient text, thick side-stripe accent borders, default glassmorphism, oversized hero metrics in product pages, identical card grids, and modals as the first interaction pattern.
- 本仓库显式禁止：装饰性渐变文字、厚侧边强调线、默认玻璃拟态、产品页里的夸张 hero 指标块、无差别重复卡片宫格，以及把 modal 当作第一反应。
- Any intentional exception, new brand/marketing surface, or route-level visual language departure must be shaped first, user-confirmed, and documented in `docs/specs/` before implementation.
- 任何有意例外、全新品牌/营销页面，或路由级视觉语言偏离，都必须先完成 shape、得到用户确认，并在 `docs/specs/` 落文后再实现。
