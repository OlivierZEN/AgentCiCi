---
name: CiCi Product Surfaces
description: Shared product-surface design baseline for assistant, admin, and platform workflows.
colors:
  ink-900: "#2b2217"
  ink-700: "#5f523f"
  ink-500: "#7c6d59"
  line-soft: "#ded2bb"
  line-strong: "#b99652"
  canvas: "#f7f3eb"
  surface: "#fffdf8"
  surface-muted: "#faf4e8"
  surface-tint: "#f3e8d3"
  accent-primary: "#a67c2f"
  accent-primary-soft: "#f4e7c7"
  accent-primary-strong: "#876223"
  success-soft: "#f1f7ea"
  success-ink: "#166534"
  danger-soft: "#fff3ef"
  danger-ink: "#b42318"
typography:
  display:
    fontFamily: "-apple-system, BlinkMacSystemFont, \"Segoe UI\", \"PingFang SC\", sans-serif"
    fontSize: "28px"
    fontWeight: 650
    lineHeight: 1.15
    letterSpacing: "-0.02em"
  headline:
    fontFamily: "-apple-system, BlinkMacSystemFont, \"Segoe UI\", \"PingFang SC\", sans-serif"
    fontSize: "18px"
    fontWeight: 650
    lineHeight: 1.3
  title:
    fontFamily: "-apple-system, BlinkMacSystemFont, \"Segoe UI\", \"PingFang SC\", sans-serif"
    fontSize: "14px"
    fontWeight: 600
    lineHeight: 1.4
  body:
    fontFamily: "-apple-system, BlinkMacSystemFont, \"Segoe UI\", \"PingFang SC\", sans-serif"
    fontSize: "13px"
    fontWeight: 500
    lineHeight: 1.5
  label:
    fontFamily: "-apple-system, BlinkMacSystemFont, \"Segoe UI\", \"PingFang SC\", sans-serif"
    fontSize: "11px"
    fontWeight: 700
    lineHeight: 1.4
    letterSpacing: "0.08em"
rounded:
  sm: "10px"
  md: "14px"
  lg: "18px"
spacing:
  xs: "6px"
  sm: "10px"
  md: "14px"
  lg: "20px"
  xl: "28px"
components:
  button-primary:
    backgroundColor: "{colors.accent-primary}"
    textColor: "{colors.surface}"
    rounded: "{rounded.sm}"
    padding: "9px 14px"
  button-primary-hover:
    backgroundColor: "{colors.accent-primary-strong}"
    textColor: "{colors.surface}"
    rounded: "{rounded.sm}"
    padding: "9px 14px"
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink-900}"
    rounded: "{rounded.sm}"
    padding: "9px 14px"
  panel:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink-900}"
    rounded: "{rounded.md}"
    padding: "16px"
  input:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink-900}"
    rounded: "{rounded.sm}"
    padding: "10px 12px"
---

# Design System: CiCi Product Surfaces

## Overview

**Creative North Star: "The Gilded Ledger"**

**Style Name: "鎏金账房"**

This system is the default baseline for all authenticated product surfaces in this repository, including the assistant workbench, admin console, and platform control plane. It treats those surfaces like a disciplined operations ledger: calm, bright, exact, and built for repeated daily use. Trust comes from orderly hierarchy, familiar controls, compact spacing, and strong information rhythm rather than decorative flourish.

The visual language should feel close to a mature SaaS control plane with a restrained luxury finish. Surfaces stay warm and light, thin champagne-gold linework provides distinction, and accent color is reserved for selection, confirmation, and primary actions. The system explicitly rejects glass cards, dark command-center theatrics, loud gradients, and any pattern that reads like a marketing page or an AI-generated dashboard template.

**Key Characteristics:**
- High-density, scan-friendly data layouts
- Light neutral surfaces with restrained gold emphasis
- Stable split-pane workflows for list plus detail editing
- Compact controls with clear focus and selection states
- Familiar enterprise product behavior over novelty

**Named Style Guidance:**
- **鎏金账房** means warm ivory surfaces, ink-heavy typography, and brighter champagne-gold linework used as structural emphasis.
- This style is appropriate for internal control planes, premium admin surfaces, and governance-heavy dashboards where the product should feel more valuable without becoming flashy.
- Reuse this style by preserving three anchors together: warm neutral canvas, compact layout density, and bright gold edge contrast on panels, fields, active states, and buttons.

## Project-wide Application

This file is the default design baseline for `/`, `/admin/*`, and `/platform/*`. Unless a route-specific spec explicitly says otherwise, all authenticated product pages inherit this file's palette discipline, typography hierarchy, spacing density, component vocabulary, and interaction rules.

### Surface tuning

- **Assistant workbench (`/`)**: keep conversation areas calmer and more breathable, but preserve the same neutral base, focus styling, and control language.
- **Admin (`/admin/*`)**: prefer table + detail, form + feedback, and compact CRUD structure with restrained borders and clear empty/error states.
- **Platform (`/platform/*`)**: use the strongest governance density and the clearest gold structural linework, because these pages carry versioning, policy, and audit operations.

### Exception rule

- If a new brand or marketing surface appears, do not inherit this file by default. Run `impeccable shape`, get user confirmation, and record the exception in `docs/specs/` before implementation.
- If code changes visual tokens, component vocabulary, or cross-page interaction patterns, update `DESIGN.md` and `DESIGN.json` in the same session.

## Colors

The palette is restrained and operational: warm neutrals carry the surface, while a single champagne-gold accent marks intent, selection, and high-value structure.

### Primary
- **Champagne Gold** (`#a67c2f`): Used for primary actions, active navigation, focused form controls, and selected workspace states.

### Neutral
- **Ledger Ink** (`#2b2217`): Primary text and strong data labels.
- **Warm Bronze Secondary** (`#5f523f`): Section labels, subheadings, and medium-emphasis content.
- **Quiet Metadata** (`#7c6d59`): Timestamps, helper text, empty-state support copy.
- **Gold Mist Line** (`#ded2bb`): Table rules, panel borders, separators.
- **Pressed Gold Line** (`#b99652`): Hovered or selected boundaries, stronger field edges.
- **Ivory Canvas** (`#f7f3eb`): App background.
- **Porcelain Surface** (`#fffdf8`): Main panels and form surfaces.
- **Champagne Shelf** (`#f3e8d3`): Side navigation, inactive work surfaces, compact stat tiles.

### Named Rules
**The One Accent Rule.** Gold belongs to intent, focus, and linework. It is not decorative fill.

## Typography

**Display Font:** `-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", sans-serif`
**Body Font:** `-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", sans-serif`
**Label/Mono Font:** `ui-monospace, "SFMono-Regular", Menlo, Consolas, monospace` for codes, ids, and timestamps

**Character:** Typography should feel native, compact, and quietly authoritative. Large text is rare and purposeful; most of the system lives in a disciplined small-scale hierarchy designed for tables, forms, and metadata.

### Hierarchy
- **Display** (650, 28px, 1.15): Page titles and login hero headline only.
- **Headline** (650, 18px, 1.3): Panel titles and key workspace headings.
- **Title** (600, 14px, 1.4): Section subheadings and emphasized row labels.
- **Body** (500, 13px, 1.5): Default copy, table content, form values.
- **Label** (700, 11px, 0.08em tracking): Eyebrows, field labels, table headers, compact status markers.

### Named Rules
**The No-Drama Scale Rule.** Product UI hierarchy comes from discipline, not giant jumps in size.

## Elevation

Depth is conveyed primarily through tonal separation and crisp borders. Shadows are present but quiet, used only to separate persistent work surfaces from the canvas or to emphasize one active workspace region.

### Shadow Vocabulary
- **Shell Lift** (`box-shadow: 0 12px 32px rgba(15, 23, 42, 0.06)`): For the main app shell and login card.
- **Panel Lift** (`box-shadow: 0 8px 20px rgba(15, 23, 42, 0.04)`): For secondary panels and stat tiles when they need separation from the canvas.

### Named Rules
**The Border-First Rule.** If a panel can be separated with border and tone, do that before adding shadow.

## Components

### Buttons
- **Shape:** Rounded rectangle, calm corners (`10px`)
- **Primary:** Solid bronze-gold fill with warm white text, medium weight, compact horizontal padding
- **Hover / Focus:** Darker bronze on hover, visible warm-gold focus ring, no flashy gradient
- **Secondary / Ghost:** White or tinted neutral background with line border and dark text
- **Default discipline:** Buttons on product pages must use the shared primary / secondary / danger vocabulary. Do not let page-local buttons fall back to legacy blue, teal, black, gradient, or unscoped global button styles.
- **Dialog actions:** Modal footers use secondary for cancel / close actions and primary gold for confirm / save actions. Both buttons must share height, radius, padding, weight, and hover / focus behavior.

### Modals
- **Default behavior:** Any popup, chooser, editor, confirmation, import preview, or publish dialog is a modal window by default unless the feature spec explicitly says it should be a popover, drawer, toast, or inline disclosure.
- **Structure:** Modal windows use a blocking overlay, `role="dialog"`, `aria-modal="true"`, a labelled heading, an opaque surface, and a fixed footer when actions are present.
- **Visual language:** Modal surfaces inherit `鎏金账房`: warm ivory background, thin gold-tinted borders, compact density, ink typography, and restrained shadows. Close controls are quiet icon buttons, not bordered button blocks.
- **Exception rule:** Non-modal popups require a task-specific reason and must be documented in the relevant spec or design note before implementation.

### Cards / Containers
- **Corner Style:** Soft but not pill-like (`14px` to `18px`)
- **Background:** White or muted neutral only
- **Shadow Strategy:** Minimal lift, mostly border-defined
- **Border:** Thin gold-tinted line, slightly stronger on active states
- **Internal Padding:** 14px to 20px, tighter in data-dense panels

### Inputs / Fields
- **Style:** White fill, crisp border, compact height, dark text
- **Focus:** Strong warm-gold outline or ring plus subtle border shift
- **Error / Disabled:** Error uses pale red background with strong text; disabled stays low-contrast but readable

### Navigation
- **Style:** Tinted neutral sidebar, compact vertical rhythm, active item shown with gold-tinted background and strong bronze label color. Mobile collapses to a top-first stack rather than preserving a tall fixed rail.**

### Tables and Workspace Rows
- **Style:** Fixed header feel, compact row padding, muted header strip, selected rows use champagne tint instead of saturated fill**

### Admin CRUD Lists

Admin list pages must be stable before they are decorative. Lists, filters, search fields, and row actions are repeated work surfaces, so they must not shift, stretch, or change component vocabulary when data, filters, focus, or hover state changes.

**Layout and density rules:**
- Keep list pages compact. Page title, toolbar, filters, and table should use content-height rows (`auto`) and `align-content: start`; do not let CSS grid distribute extra vertical space across controls.
- Any full-width search or input in a toolbar must use `box-sizing: border-box`, `max-width: 100%`, and an explicit compact height. Focus rings must not change layout size.
- Page, toolbar, table wrapper, and table elements must use `min-width: 0` inside grid or flex parents so child content cannot widen the whole page.
- Empty states must not create oversized blank panels unless the page intentionally needs a full empty-state composition. CRUD tables should keep the same compact table frame with a concise empty row or note.

**Table implementation rules:**
- Never apply `display: flex`, `display: grid`, `display: block`, or `display: -webkit-box` directly to real `table`, `thead`, `tbody`, `tr`, `th`, or `td` elements. This breaks column alignment.
- If a cell needs clamping, wrapping, chips, or flex actions, put an inner element inside the `td` and style that inner element.
- Use `table-layout: fixed` plus an explicit `colgroup` or equivalent column contract for dense admin tables. Long text should truncate or wrap inside the cell, not widen the table.
- Avoid horizontal scroll in normal admin CRUD tables. If a route truly needs horizontal scroll, document it in the feature spec and keep it local to that table.

**Toolbar buttons and filters:**
- Buttons in the same toolbar must share the same component family, height, border radius, padding, font weight, and hover/focus behavior. Do not mix button classes from compose pages, modal footers, and list pages in one toolbar.
- Secondary toolbar buttons on admin lists use warm white surfaces, gold-tinted borders, ink or bronze text, and restrained hover. Gold fill is reserved for high-value primary actions, not every list action.
- Filter categories that only switch list scope should look like text tabs, not large pill buttons. Active state should use the gold family (`#876223` text with `#b99652` underline) on `鎏金账房` pages. Do not use teal or green active states in gold-led admin surfaces unless the state is explicitly semantic success.

**Row action rules:**
- Do not show multiple row action buttons permanently in dense tables. Use a hover/focus "more" trigger for secondary row actions when the actions are not the main task of the page.
- Row action menus must be opaque, use the same menu-item styling for links and buttons, and avoid button chrome inside the menu. Dangerous actions may use danger text and hover background, but should still align with normal menu item sizing.
- Do not expose actions that violate the page's responsibility. For example, if publishing must happen in an editor or governance flow, do not add publish buttons to a list page.
- Standard or read-only rows should not show edit/view affordances if the product decision is that users cannot inspect or change them from that surface.

## Do's and Don'ts

### Do:
- **Do** keep page content compact, with obvious alignment between titles, metrics, tables, and edit panels.
- **Do** use restrained neutral layers and thin borders to separate navigation, content, and detail workspaces.
- **Do** treat brighter gold lines as structure, not decoration: panel edges, selected rows, active nav, focused inputs, and premium primary actions.
- **Do** align numbers and timestamps for fast scanning.
- **Do** keep success and error feedback inline, quiet, and close to the action that triggered it.
- **Do** preserve standard enterprise patterns for side navigation, data tables, and form layouts.
- **Do** protect list pages against layout shifts caused by filtering, searching, empty results, long text, focus rings, hover menus, or conditional actions.

### Don't:
- **Don't** use decorative gradients, dark-mode hero backgrounds, or glassmorphism in authenticated product surfaces.
- **Don't** flood large surfaces with gold; keep it on edges, emphasis, and controlled highlights.
- **Don't** turn overview data into oversized hero metrics or marketing-style value statements.
- **Don't** use heavy saturated fills for inactive states.
- **Don't** make cards oversized or airy enough to reduce operational density.
- **Don't** ship controls that look different from page to page without a functional reason.
- **Don't** style table cells themselves as flex/grid/clamped boxes, and don't allow a search field or empty result state to stretch the page.
