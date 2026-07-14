# 平台登录原图像素锁定复刻 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将用户提供的 1672×941 原图作为 `/platform/login` 默认态整页背景，同时保留可访问且功能不变的平台账号登录表单。

**Architecture:** 原图作为受控的 Vite 静态模块资产，由 `PlatformLogin` 根节点传入 CSS 自定义属性。默认态仅显示该资产，语义表单以透明、坐标锁定的交互层覆盖对应控件区域；focus、输入、notice 等非默认状态才显示可读覆盖层。认证函数、请求路径和跳转维持现状。

**Tech Stack:** React、TypeScript、Vite、Vitest、CSS、浏览器截图对比、ACR Docker Compose 发布。

## Global Constraints

- 原图是唯一默认视觉真值，原始尺寸 `1672 × 941` 下默认截图不允许重绘任何可见视觉元素。
- 保留 `POST /auth/platform/password/login`、`identifier.trim()`、`password`、平台角色校验、`LS_PLATFORM_TOKEN` 和 `/platform` 跳转。
- 不新增移动端布局、后端代码、主题偏好或外部依赖。
- 真实测试、构建、截图对比和线上验收结果才可写入状态文件。

---

### Task 1: 原图契约和失败测试

**Files:**
- Modify: `frontend/src/platform/PlatformLogin.test.tsx`
- Modify: `docs/specs/FEAT-115-platform-login-cosmic-visual-refresh.md`

**Interfaces:**
- Consumes: `PlatformLogin`、`PLATFORM_LOGIN_ENDPOINT`、`buildPlatformLoginRequest`。
- Produces: `platform-login--reference` 与 `platform-login__reference-control-layer` 的渲染契约。

- [x] **Step 1: 写失败测试**

```tsx
expect(markup).toContain("platform-login--reference");
expect(markup).toContain("platform-login__reference-control-layer");
expect(markup).not.toContain("platform-login__orbit-scene");
```

- [x] **Step 2: 运行聚焦测试并确认失败**

Run: `npm test -- --run src/platform/PlatformLogin.test.tsx`

Expected: FAIL，因为当前页面仍渲染 SVG 轨道场，且没有原图背景/透明交互层 class。

### Task 2: 原图资产和透明交互层

**Files:**
- Create: `frontend/src/assets/platform-login-reference-1672x941.png`
- Modify: `frontend/src/platform/PlatformLogin.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Consumes: 用户原图、既有 `login()`、`identifier`、`password`、`notice`。
- Produces: 默认原图背景、基于原图百分比坐标的真实输入和按钮覆盖层。

- [x] **Step 1: 无损复制用户原图至受控资产目录**

Run: `cp /var/folders/ld/pqvgd4g52h555q74hhmy47ch0000gn/T/codex-clipboard-fe3f07a0-c764-4a22-9731-739b7212a088.png frontend/src/assets/platform-login-reference-1672x941.png`

- [x] **Step 2: 最小实现**

```tsx
<main className="login-root login-root--admin platform-login platform-login--reference" style={{ "--platform-login-reference": `url(${platformLoginReference})` } as CSSProperties}>
  <section className="platform-login__reference-control-layer" aria-labelledby="platform-login-title">
    {/* 现有 label/input/button/login notice，默认透明，focus 或输入后可读 */}
  </section>
</main>
```

- [x] **Step 3: 最小 CSS 实现**

```css
.platform-login--reference {
  background: #050607 var(--platform-login-reference) center / 100% 100% no-repeat;
}
.platform-login__reference-control-layer { position: absolute; inset: 0; }
.platform-login__form { left: 52.99%; top: 40.8%; width: 39.2%; }
```

- [x] **Step 4: 运行聚焦测试并确认通过**

Run: `npm test -- --run src/platform/PlatformLogin.test.tsx`

Expected: PASS，认证请求契约与新的默认视觉结构同时存在。

### Task 3: 原图尺寸设计 QA 与质量门

**Files:**
- Create or Modify: `design-qa.md`
- Modify: `.claw/test-report.md`

**Interfaces:**
- Consumes: 原图、`1672 × 941` 本地截图、浏览器 DOM 与控制台结果。
- Produces: `design-qa.md` 的 `final result: passed`。

- [x] **Step 1: 启动本地页面并以 1672×941 截图默认态**

Run: `npm run dev -- --host 127.0.0.1`

Expected: `/platform/login` 截图可与原图同尺寸比较。

- [x] **Step 2: 对比原图与实现截图**

检查背景、裁切、标题、轨道、分割线、表单和按钮默认态；若有 P0/P1/P2，修复 CSS/坐标后重新截图。

- [x] **Step 3: 验证交互**

填入本地假账号和密码但不提交，断言按钮由禁用变可用、DOM 无横向溢出、控制台 error/warning 为零。

- [x] **Step 4: 运行工程质量门**

Run: `npm test -- --run && npm run build`

Expected: 全量 Vitest 和 Vite production build 成功。

### Task 4: 2.7.2 生产发布和回读

**Files:**
- Modify: `.claw/devops.md`
- Modify: `.claw/current-status.md`
- Modify: `.claw/task-board.md`
- Modify: `.claw/tasks/TASK-209.md`
- Modify: `.claw/test-report.md`

**Interfaces:**
- Consumes: 已验证 commit、`scripts/release-acr.sh`、生产 runbook。
- Produces: 生产 `2.7.2`、ACR digest、备份目录、健康与浏览器回读证据。

- [ ] **Step 1: 运行 dry-run**

Run: `./scripts/release-acr.sh --dry-run --version 2.7.2`

Expected: backend/frontend 镜像、Git tag 和全部版本变量均为 `2.7.2`。

- [ ] **Step 2: 备份并正式发布**

Run: `ALLOW_DIRTY_RELEASE=true ./scripts/release-acr.sh --version 2.7.2`

Expected: ACR inspect 与 Git tag push 成功，然后按 `docs/production-release-runbook.md` 更新线上 backend/frontend。

- [ ] **Step 3: 生产验收**

检查 `/actuator/health`、`/system/version`、x/onechat HTTPS 与 `1672 × 941` 浏览器默认态；更新发布记录。

## Self-Review

- FEAT-115 的原图、透明层、认证契约、QA 与发布门均有对应任务。
- 计划未引入后端、移动端或第二套视觉资产。
- 测试先行，原图默认态和真实交互层分别有可验证步骤。
