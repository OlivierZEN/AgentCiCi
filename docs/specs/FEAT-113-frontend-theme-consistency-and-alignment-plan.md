# 前台主题一致性与视觉对齐全量治理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让认证后普通用户前台的所有主要页面、AI 应用和弹层完整响应八套主题，并统一组织入口首字符与桌面端对齐。

**Architecture:** 保留现有组件树和业务数据流，在 `theme.css` 扩充公共语义 token 与前台根节点范围内的主题映射，在 `cici-ui.css` 修正真正的几何和对齐缺陷。组织首字符由共享纯函数生成，`AssistantApp` 只消费结果。静态 CSS 守卫与纯函数测试先失败，再完成最小实现，最后由真实浏览器逐页验收。

**Tech Stack:** React 19、TypeScript 5.9、Vitest 3、Vite 7、原生 CSS、浏览器桌面端验收。

## Global Constraints

- 只治理认证后普通用户前台 `/app`，不修改管理端、平台端、营销站和登录页结构。
- 八套主题只映射语义颜色，不改变页面结构、密度、控件尺寸、图标、动效和滚动规则。
- 结构 wrapper 保持透明；禁止通过宽泛 `!important` 把页面重新绘制成嵌套卡片。
- 组织名称为空回退“组”；中文取首字；拉丁字母取大写首字。
- 不修改业务 API、权限、CRM 身份同步、数据结构和移动端范围。
- 保留未跟踪 `diagrams/`，不得读取、修改或提交。

---

### Task 1: 组织入口首字符

**Files:**
- Create: `frontend/src/shared/avatar.test.ts`
- Modify: `frontend/src/shared/avatar.ts`
- Modify: `frontend/src/assistant/AssistantApp.tsx`

**Interfaces:**
- Produces: `getOrganizationMonogram(name: string): string`。
- Consumes: `currentOrgName`；不新增组织数据字段。

- [ ] **Step 1: 写失败测试**

```ts
import { describe, expect, it } from "vitest";
import { getOrganizationMonogram } from "./avatar";

describe("getOrganizationMonogram", () => {
  it("uses the first visible organization character", () => {
    expect(getOrganizationMonogram("智能体平台演示环境")).toBe("智");
    expect(getOrganizationMonogram(" demo organization")).toBe("D");
    expect(getOrganizationMonogram("2号组织")).toBe("2");
    expect(getOrganizationMonogram("   ")).toBe("组");
  });
});
```

- [ ] **Step 2: 验证 RED**

Run: `cd frontend && npm test -- src/shared/avatar.test.ts`

Expected: FAIL，提示 `getOrganizationMonogram` 未导出。

- [ ] **Step 3: 最小实现并替换固定 CB**

```ts
export function getOrganizationMonogram(value: string) {
  const first = (value ?? "").trim().slice(0, 1);
  return first ? (/^[a-z]$/i.test(first) ? first.toUpperCase() : first) : "组";
}
```

`AssistantApp` 导入函数，使用 `getOrganizationMonogram(currentOrgName)` 渲染 `.cici-rail__logo-icon`。

- [ ] **Step 4: 验证 GREEN**

Run: `cd frontend && npm test -- src/shared/avatar.test.ts`

Expected: PASS，4 个断言全部通过。

---

### Task 2: 公共主题 token 与静态覆盖守卫

**Files:**
- Modify: `frontend/src/theme/theme.css`
- Modify: `frontend/src/theme/theme.test.ts`
- Modify: `DESIGN.json`

**Interfaces:**
- Produces: `--theme-data-1`、`--theme-data-2`、`--theme-data-3`、`--theme-data-4`。
- Produces: 前台模块根节点主题覆盖清单。

- [ ] **Step 1: 写失败测试**

在 `theme.test.ts` 断言八个主题块都包含四个 `--theme-data-*` token，并断言主题样式明确覆盖：

```ts
for (const token of ["--theme-data-1", "--theme-data-2", "--theme-data-3", "--theme-data-4"]) {
  expect((themeCss.match(new RegExp(token, "g")) ?? []).length).toBeGreaterThanOrEqual(8);
}
for (const selector of [".cici-ai-apps-flyout", ".cici-data-board", ".zhiwei-demo", ".memory-panel", ".customer-workbench-ingestion"]) {
  expect(themeCss).toContain(selector);
}
```

- [ ] **Step 2: 验证 RED**

Run: `cd frontend && npm test -- src/theme/theme.test.ts`

Expected: FAIL，缺少数据序列 token 和重点页面覆盖。

- [ ] **Step 3: 增加八主题数据序列色**

每个主题定义四个可区分序列色；`gilded` 保持金/蓝/绿/橙的克制序列，其他主题以各自 accent 为主并使用同主题邻近色，`galaxy` 使用高可读冷色序列。

- [ ] **Step 4: 建立前台模块局部别名**

在 `.cici-app` 范围内为数据洞察、知微画像、客户工作台、专属记忆、AI 应用菜单和公共 overlay 映射 canvas/surface/text/line/accent/status/data token。结构 wrapper 只设置文字和透明背景。

- [ ] **Step 5: 更新设计事实源**

在 `DESIGN.json.extensions.themePreferences.rules` 补充数据序列色和组织首字符规则，不改变现有主题目录与持久化契约。

- [ ] **Step 6: 验证 GREEN**

Run: `cd frontend && npm test -- src/theme/theme.test.ts`

Expected: PASS。

---

### Task 3: AI 应用菜单、智能体工作区与个人设置

**Files:**
- Modify: `frontend/src/theme/theme.css`
- Modify: `frontend/src/assistant/cici-ui.css`
- Modify: `frontend/src/theme/theme.test.ts`

**Interfaces:**
- Consumes: Task 2 公共 token。
- Produces: 稳定的菜单、智能体卡片、设置页、记忆空态和公共弹层视觉。

- [ ] **Step 1: 扩展失败守卫**

断言 AI 应用 flyout、智能体 chip、设置 tabs/content、memory empty/form/card 和组织 menu 的主题选择器存在；断言重点结构层保持 `background: transparent`。

- [ ] **Step 2: 验证 RED**

Run: `cd frontend && npm test -- src/theme/theme.test.ts`

Expected: FAIL 于新增覆盖断言。

- [ ] **Step 3: 修复菜单和工作区主题**

统一 flyout 表面、分隔线、图标框、文字、hover/focus/active；智能体列表卡片边框、选中态和头像身份色使用 theme token，保持几何不变。

- [ ] **Step 4: 修复设置与记忆页**

让设置页画布、tabs、说明条、memory empty/card/form/overlay 完整消费 theme token；空态改为内容区域居中，移除相对旧画布的偏移。

- [ ] **Step 5: 统一对齐**

工具栏控件高度 32 至 34px；标题、按钮、计数和筛选器使用同一中心线；卡片内容、标签和操作纵向居中。

- [ ] **Step 6: 验证 GREEN**

Run: `cd frontend && npm test -- src/theme/theme.test.ts`

Expected: PASS。

---

### Task 4: 数据洞察与知微画像

**Files:**
- Modify: `frontend/src/theme/theme.css`
- Modify: `frontend/src/assistant/cici-ui.css`
- Modify: `frontend/src/theme/theme.test.ts`

**Interfaces:**
- Consumes: `--theme-data-1..4`。
- Produces: 四类数据洞察视图与五类知微画像视图的同主题表面、图表、状态和对齐。

- [ ] **Step 1: 写失败守卫**

断言 `cici-data-*` 和 `zhiwei-demo-*` 的主题块使用 `var(--theme-data-*)`、`var(--theme-surface*)`、`var(--theme-line*)`；断言不存在主题块内的新固定 hex 色。

- [ ] **Step 2: 验证 RED**

Run: `cd frontend && npm test -- src/theme/theme.test.ts`

Expected: FAIL，数据模块仍依赖历史固定色。

- [ ] **Step 3: 修复数据洞察**

映射指标数字、目标条、仪表盘、排名、地图、趋势、漏斗、风险和表格颜色；统一 card 标题与内容上边界、排名列宽、图表底线和列表行高。

- [ ] **Step 4: 修复知微画像**

映射 shell、side、main、filter、table、panel、badge、avatar、timeline、配置和 dashboard；修正筛选控件、表头/单元格、对象名称与操作列对齐。

- [ ] **Step 5: 验证 GREEN**

Run: `cd frontend && npm test -- src/theme/theme.test.ts`

Expected: PASS。

---

### Task 5: 客户互动工作台与共享弹层

**Files:**
- Modify: `frontend/src/theme/theme.css`
- Modify: `frontend/src/assistant/cici-ui.css`
- Modify: `frontend/src/theme/theme.test.ts`

**Interfaces:**
- Consumes: 公共 canvas/surface/text/line/accent/status token。
- Produces: 新客/老客、详情、AI 助理、互动整理和归档弹层的主题一致性与稳定分栏。

- [ ] **Step 1: 写失败守卫并验证 RED**

覆盖 `customer-workbench__*`、`customer-workbench-ingestion*`、archive、notification popover 和 dialog；运行 `npm test -- src/theme/theme.test.ts` 确认失败。

- [ ] **Step 2: 修复页面主题**

统一 topbar、queue、detail、metrics、tabs、assistant、timeline、recommendation 和状态反馈；保留现有三栏结构与密度。

- [ ] **Step 3: 修复互动整理弹窗**

遮罩使用 `--theme-overlay`；header/body/footer、capture/review 分栏、表单控件、空态和按钮使用主题 token；左右栏标题和表单顶部对齐，分栏线贯穿正文但不进入 footer。

- [ ] **Step 4: 修复共享 overlay**

统一 archive、notification、menu、dialog 的不透明表面、结构线、关闭按钮和焦点状态。

- [ ] **Step 5: 验证 GREEN**

Run: `cd frontend && npm test -- src/theme/theme.test.ts`

Expected: PASS。

---

### Task 6: 全量验证与桌面端逐页验收

**Files:**
- Modify: `design-qa.md`
- Modify: `docs/specs/FEAT-113-frontend-theme-consistency-and-alignment.md`
- Modify: `.claw/tasks/TASK-207.md`
- Modify: `.claw/test-report.md`

- [ ] **Step 1: 聚焦测试**

Run: `cd frontend && npm test -- src/shared/avatar.test.ts src/theme/theme.test.ts`

Expected: PASS。

- [ ] **Step 2: 全量测试与构建**

Run: `cd frontend && npm test -- --run && npm run build`

Expected: 所有测试通过；生产构建 exit 0，只允许既有大 chunk warning。

- [ ] **Step 3: 静态卫生**

Run: `git diff --check`

Expected: exit 0。

- [ ] **Step 4: 本地浏览器矩阵**

启动现有本地前后端，在 2048×1152 桌面端逐页检查 FEAT-113 的七组页面。`gilded`、`sakura`、`galaxy` 覆盖全部页面；八主题覆盖壳层、组织菜单和设置页。检查 computed color/background/border、水平溢出和 console。

- [ ] **Step 5: 交互与对齐验收**

检查菜单 hover/focus/open、智能体 selected、设置 tabs、互动整理 modal、空态、disabled/loading/error；记录每页标题/控件基线、表格列、弹窗分栏、卡片上边界和空态居中。

- [ ] **Step 6: 记录真实结果并完成任务**

只把实际命令和浏览器结果写入 `design-qa.md`、FEAT-113、TASK-207 与 `.claw/test-report.md`；未验证页面不得标记通过。

---

## Plan Self-Review

- Spec coverage：组织首字符、八主题语义色、数据序列色、全部前台模块、弹层、对齐、溢出、交互状态和桌面端矩阵均有对应任务。
- Placeholder scan：无 TBD、TODO、稍后实现或未定义接口。
- Type consistency：全计划统一使用 `getOrganizationMonogram(name: string): string` 与 `--theme-data-1..4`。
- Scope：不触碰后台、平台、业务 API、数据库、移动端和 `diagrams/`。
