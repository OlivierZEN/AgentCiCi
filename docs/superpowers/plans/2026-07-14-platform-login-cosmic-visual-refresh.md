# Platform Login Cosmic Visual Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `/platform/login` 重构为用户批准的宇宙黑 Agent 运营平台登录入口，同时完整保留现有平台账号认证行为。

**Architecture:** 保留 `PlatformLogin.tsx` 中的状态、请求、角色校验、token 写入和路由跳转；只扩展语义结构，新增左侧装饰性 SVG 轨道场和可访问的微标签。将所有新视觉规则收敛到 `.platform-login` 前缀下的 `styles.css`，避免影响平台内页与其他登录页。

**Tech Stack:** React 19, TypeScript, Vite, Vitest, CSS, inline SVG.

## Global Constraints

- 只修改 `/platform/login`，不修改平台认证 API、请求体、token key、角色校验或跳转逻辑。
- 按 `docs/specs/FEAT-115-platform-login-cosmic-visual-refresh.md` 的 cosmic-black / deep-space / dusk-blue / nebula-teal / stardust-gold 视觉事实源实现。
- 仅做桌面端产品质量门，不新增移动端兼容实现、移动端截图或移动端自动化测试。
- 保留 `diagrams/` 未跟踪文件，不读取、不修改、不提交。

---

### Task 1: Lock the visual contract and test boundary

**Files:**
- Create: `docs/specs/FEAT-115-platform-login-cosmic-visual-refresh.md`
- Create: `.claw/tasks/TASK-209.md`
- Create: `.claw/assignments/TASK-209.yaml`
- Create: `docs/superpowers/plans/2026-07-14-platform-login-cosmic-visual-refresh.md`
- Modify: `DESIGN.md`
- Modify: `DESIGN.json`
- Modify: `.claw/task-board.md`
- Modify: `.claw/current-status.md`

**Interfaces:**
- Produces the approved route-level visual exception, task boundary, palette, layout contract, and acceptance criteria used by implementation.

- [x] **Step 1: Record the confirmed direction**

  Record the 45/55 split, orbit scene, cosmic palette, form states, and authentication invariants in FEAT-115. The approved north-star is a visual reference only; semantic text and controls stay in React.

- [x] **Step 2: Record assignment and state**

  Add TASK-209 with `frontend/src/**`, `docs/specs/**`, `docs/superpowers/plans/**`, `.claw/tasks/**`, `.claw/assignments/**`, and exact design/state paths. Keep backend, scripts, migrations, and unrelated untracked files outside scope.

### Task 2: Add the failing component contract test

**Files:**
- Create: `frontend/src/platform/PlatformLogin.test.tsx`

**Interfaces:**
- Consumes: current `PlatformLogin` component and its mocked `useNavigate` dependency.
- Produces: a server-rendered structure contract for `main`, the visual field, `AGENT OPERATIONS`, associated labels, the `aria-live` notice, and the preserved login endpoint/request identifiers.

- [ ] **Step 1: Write the failing test**

```tsx
import { readFileSync } from "node:fs";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import PlatformLogin from "./PlatformLogin";

vi.mock("react-router-dom", () => ({ useNavigate: () => () => undefined }));

describe("PlatformLogin cosmic surface", () => {
  it("renders the approved visual structure without changing the auth contract", () => {
    const markup = renderToStaticMarkup(<PlatformLogin />);
    const source = readFileSync(new URL("./PlatformLogin.tsx", import.meta.url), "utf8");

    expect(markup).toContain('aria-label="运营平台安全登录"');
    expect(markup).toContain("platform-login__visual");
    expect(markup).toContain("AGENT OPERATIONS");
    expect(markup).toContain('aria-live="polite"');
    expect(source).toContain('"/auth/platform/password/login"');
    expect(source).toContain("identifier: identifier.trim()");
    expect(source).toContain("password,");
  });
});
```

- [ ] **Step 2: Run the focused test to verify it fails**

  Run: `npm test -- --run src/platform/PlatformLogin.test.tsx`

  Expected: FAIL because the current component has no `aria-label="运营平台安全登录"`, no `platform-login__visual`, and no `AGENT OPERATIONS` visual field.

### Task 3: Implement the semantic cosmic login surface

**Files:**
- Modify: `frontend/src/platform/PlatformLogin.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Consumes: FEAT-115 route visual contract and the failing structure test.
- Produces: a visually faithful, accessible `/platform/login` surface with unchanged `login()` behavior.

- [ ] **Step 1: Add only presentational structure**

  Preserve the existing `login()` body exactly. Wrap the page in `main aria-label="运营平台安全登录"`, add a presentational `.platform-login__visual` with `aria-hidden="true"` SVG orbital lines and micro-labels, keep the right form labels and placeholders, add `name="identifier"` and `name="password"`, and retain the existing notice and button disabled expression.

- [ ] **Step 2: Add route-scoped tokens and layout**

  Implement `.platform-login` as a full-height 45/55 grid with `#050607`-family surfaces, a low-contrast divider, and no legacy ivory login card treatment. Use explicit 1px borders, 6px to 10px radii, semantic spacing tokens, and no global selector overrides.

- [ ] **Step 3: Add the orbit scene and state styling**

  Implement SVG orbit rings, signal dots, and a central core in the visual field. Style default, hover, focus-visible, disabled, loading notice, success notice, error notice, and `prefers-reduced-motion: reduce` without animating layout properties.

- [ ] **Step 4: Run the focused test to verify it passes**

  Run: `npm test -- --run src/platform/PlatformLogin.test.tsx`

  Expected: PASS with 1 test passed and the preserved auth endpoint/request assertions intact.

### Task 4: Verify build and browser fidelity

**Files:**
- Modify: `.claw/test-report.md`
- Modify: `.claw/tasks/TASK-209.md`
- Modify: `.claw/current-status.md`

**Interfaces:**
- Consumes: implemented `/platform/login` surface.
- Produces: fresh build/test/browser evidence and a concise handoff.

- [ ] **Step 1: Run the full frontend test suite**

  Run: `npm test -- --run`

  Expected: all existing frontend tests plus the PlatformLogin contract test pass with exit code 0.

- [ ] **Step 2: Run the production build**

  Run: `npm run build`

  Expected: TypeScript build and Vite build complete with exit code 0.

- [ ] **Step 3: Inspect the desktop route in a real browser**

  Start the frontend with `npm run dev -- --host 127.0.0.1`, open `http://127.0.0.1:5173/platform/login`, and capture a 1440×840 or wider desktop screenshot. Check the default page, focus-visible input, disabled button, loading notice, and error notice without submitting real credentials.

- [ ] **Step 4: Apply the critique-and-fix pass**

  Compare the screenshot against FEAT-115: background tone, 45/55 composition, orbital scene prominence, divider position, form width, title rhythm, label contrast, input/border geometry, button treatment, and overflow. Fix every material mismatch and recapture the screenshot.

- [ ] **Step 5: Record evidence and handoff**

  Append only the latest verified commands and screenshot path to `.claw/test-report.md`, update TASK-209 to `review` or `done` only after fresh evidence, and update `current-status.md` to the next truthful action.
