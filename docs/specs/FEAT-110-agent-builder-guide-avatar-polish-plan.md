# 智能体构建说明与头像交互精修 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 去除智能体构建说明的嵌套卡片感，并把两个常驻头像按钮收敛为可访问的头像点击菜单。

**Architecture:** 保留 `AgentBuilderShell` 现有草稿、文件读取和裁剪链路，仅新增一个局部菜单状态、三个 DOM ref 与菜单开关事件。头像动作可见性由纯函数 `resolveAgentAvatarMenuActions` 决定，服务器渲染测试验证初始可访问性标记，CSS 源码测试守住说明区无框和控件无阴影约束。

**Tech Stack:** React 19、TypeScript 5.9、Vitest 3、React DOM Server、Vite 7、原生 CSS。

## Global Constraints

- 页面属于认证后 `product` register，必须保持“鎏金账房”的暖象牙底、墨色文字、紧凑密度和香槟金结构线。
- 不改变头像文件类型、裁剪规则、`draft.avatarBase64`、保存 API 或发布逻辑。
- 不改变页面整体双栏结构、策略开关行为、主题 token 或跨页面组件语汇。
- 不新增移动端布局、移动端截图或移动端自动化测试。
- 所有 hover、focus、active 和 opened 状态不得缩放、位移、发光或增加阴影。
- 只编辑 TASK-204 assignment 允许的 `frontend/src/assistant/**`、本规格/计划和任务状态文件。

---

## File Structure

- `frontend/src/assistant/AgentBuilderShell.tsx`：新增头像菜单动作解析、局部菜单状态、关闭事件与 JSX；继续复用现有 `beginAvatarCrop` 和 `updateDraft`。
- `frontend/src/assistant/AgentBuilderShell.test.ts`：验证动作解析、初始服务器渲染可访问性和旧常驻按钮移除。
- `frontend/src/assistant/cici-ui.css`：去框化说明区，并定义头像入口、覆盖层、隐藏文件输入和紧凑菜单的全部状态。
- `docs/specs/FEAT-110-agent-builder-guide-avatar-polish.md`：记录实际实现与验收结果。
- `.claw/tasks/TASK-204.md`：记录进度、变更文件、验证证据与交接。
- `.claw/test-report.md`：只在真实命令执行后写入本次验证结果。

---

### Task 1: 头像菜单行为与可访问入口

**Files:**
- Modify: `frontend/src/assistant/AgentBuilderShell.test.ts`
- Modify: `frontend/src/assistant/AgentBuilderShell.tsx`
- Modify: `frontend/src/assistant/cici-ui.css`

**Interfaces:**
- Consumes: `draft.avatarBase64: string`、`draft.name: string`、`beginAvatarCrop(file: File): Promise<void>`、`updateDraft("avatarBase64", value)`、`canEditSelectedAgent: boolean`。
- Produces: `resolveAgentAvatarMenuActions(avatarBase64: string): { primaryLabel: "上传头像" | "更换头像"; canRemove: boolean }`；头像按钮 `aria-haspopup="menu"`、`aria-expanded`；菜单 `role="menu"`；菜单项 `role="menuitem"`。

- [ ] **Step 1: 写入会失败的动作解析和服务器渲染测试**

在 `AgentBuilderShell.test.ts` 增加 React 服务器渲染依赖，并先通过动态模块属性读取尚不存在的纯函数，使测试以断言失败而不是模块解析错误结束：

```ts
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import AgentBuilderShell from "./AgentBuilderShell";

it("resolves upload and remove actions from the current avatar draft", async () => {
  const agentBuilderModule = await import("./AgentBuilderShell") as unknown as {
    resolveAgentAvatarMenuActions?: (avatarBase64: string) => {
      primaryLabel: string;
      canRemove: boolean;
    };
  };

  expect(typeof agentBuilderModule.resolveAgentAvatarMenuActions).toBe("function");
  expect(agentBuilderModule.resolveAgentAvatarMenuActions?.("")).toEqual({
    primaryLabel: "上传头像",
    canRemove: false,
  });
  expect(agentBuilderModule.resolveAgentAvatarMenuActions?.("data:image/png;base64,avatar")).toEqual({
    primaryLabel: "更换头像",
    canRemove: true,
  });
});

it("renders the avatar itself as the only persistent edit entry", () => {
  const html = renderToStaticMarkup(createElement(AgentBuilderShell, {
    kbs: [],
    orgId: "org-test",
    token: "",
  }));

  expect(html).toContain('aria-haspopup="menu"');
  expect(html).toContain('aria-expanded="false"');
  expect(html).toContain('aria-label="编辑 Agent 头像"');
  expect(html).not.toContain("上传图片");
  expect(html).not.toContain("清除头像");
});
```

- [ ] **Step 2: 运行聚焦测试并确认 RED**

Run: `cd frontend && npm test -- src/assistant/AgentBuilderShell.test.ts`

Expected: FAIL；第一项显示 `resolveAgentAvatarMenuActions` 为 `undefined`，第二项显示 HTML 缺少 `aria-haspopup="menu"`。

- [ ] **Step 3: 实现最小头像动作解析和菜单状态**

在 `AgentBuilderShell.tsx` 导出纯函数：

```ts
export function resolveAgentAvatarMenuActions(avatarBase64: string): {
  primaryLabel: "上传头像" | "更换头像";
  canRemove: boolean;
} {
  const canRemove = Boolean(avatarBase64.trim());
  return {
    primaryLabel: canRemove ? "更换头像" : "上传头像",
    canRemove,
  };
}
```

在组件状态区新增：

```ts
const [avatarMenuOpen, setAvatarMenuOpen] = useState(false);
const avatarMenuRef = useRef<HTMLDivElement | null>(null);
const avatarTriggerRef = useRef<HTMLButtonElement | null>(null);
const avatarFileInputRef = useRef<HTMLInputElement | null>(null);
```

在 effect 区新增外部点击和 Escape 关闭逻辑：

```ts
useEffect(() => {
  if (!avatarMenuOpen) return;
  const handlePointerDown = (event: MouseEvent) => {
    if (!avatarMenuRef.current?.contains(event.target as Node)) {
      setAvatarMenuOpen(false);
    }
  };
  const handleKeyDown = (event: KeyboardEvent) => {
    if (event.key !== "Escape") return;
    event.preventDefault();
    setAvatarMenuOpen(false);
    avatarTriggerRef.current?.focus();
  };
  document.addEventListener("mousedown", handlePointerDown);
  document.addEventListener("keydown", handleKeyDown);
  return () => {
    document.removeEventListener("mousedown", handlePointerDown);
    document.removeEventListener("keydown", handleKeyDown);
  };
}, [avatarMenuOpen]);
```

- [ ] **Step 4: 用头像按钮和紧凑菜单替换两个常驻按钮**

在渲染前解析动作：

```ts
const avatarMenuActions = resolveAgentAvatarMenuActions(draft.avatarBase64);
```

用以下结构替换 `.cici-builder-avatar-actions`：

```tsx
<div className="cici-builder-avatar-menu" ref={avatarMenuRef}>
  <button
    ref={avatarTriggerRef}
    type="button"
    className="cici-builder-avatar-trigger"
    aria-label={`编辑 ${draft.name || "Agent"} 头像`}
    aria-haspopup="menu"
    aria-expanded={avatarMenuOpen}
    disabled={!canEditSelectedAgent}
    onClick={() => setAvatarMenuOpen((current) => !current)}
  >
    <AvatarView
      src={draft.avatarBase64}
      fallback={getDisplayInitial(draft.name || "A", "A")}
      className="cici-builder-avatar-preview"
      alt={`${draft.name || "Agent"} 头像`}
    />
    <span className="cici-builder-avatar-trigger__overlay" aria-hidden>
      <svg viewBox="0 0 24 24"><path d="M8.5 7 10 5h4l1.5 2H18a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h2.5Z" /><circle cx="12" cy="13" r="3" /></svg>
      <span>编辑</span>
    </span>
  </button>
  <input
    ref={avatarFileInputRef}
    className="cici-builder-avatar-file-input"
    type="file"
    accept="image/png,image/jpeg,image/webp"
    tabIndex={-1}
    onChange={(event) => {
      const file = event.target.files?.[0];
      event.currentTarget.value = "";
      setAvatarMenuOpen(false);
      if (!file) return;
      void beginAvatarCrop(file);
    }}
  />
  {avatarMenuOpen ? (
    <div className="cici-builder-avatar-popover" role="menu" aria-label="头像操作">
      <button type="button" role="menuitem" onClick={() => {
        setAvatarMenuOpen(false);
        avatarFileInputRef.current?.click();
      }}>
        {avatarMenuActions.primaryLabel}
      </button>
      {avatarMenuActions.canRemove ? (
        <button type="button" role="menuitem" className="is-danger" onClick={() => {
          updateDraft("avatarBase64", "");
          setAvatarMenuOpen(false);
          window.requestAnimationFrame(() => avatarTriggerRef.current?.focus());
        }}>
          移除头像
        </button>
      ) : null}
    </div>
  ) : null}
</div>
```

- [ ] **Step 5: 定义头像控件全部视觉状态**

在 `cici-ui.css` 的头像样式区新增 `.cici-builder-avatar-menu`、`.cici-builder-avatar-trigger`、`.cici-builder-avatar-trigger__overlay`、`.cici-builder-avatar-file-input`、`.cici-builder-avatar-popover` 及菜单项规则。关键值固定为：头像 `56px`；菜单宽 `156px`；菜单行最小高度 `30px`；菜单圆角 `10px`；边框 `#ded2bb`；表面 `#fffdf8`；hover/focus 不使用 transform 或 box-shadow；危险项只使用危险文字色。

- [ ] **Step 6: 运行聚焦测试并确认 GREEN**

Run: `cd frontend && npm test -- src/assistant/AgentBuilderShell.test.ts`

Expected: PASS；原有 14 项与新增 2 项全部通过，0 failure。

- [ ] **Step 7: 提交头像菜单增量**

```bash
git add frontend/src/assistant/AgentBuilderShell.tsx frontend/src/assistant/AgentBuilderShell.test.ts frontend/src/assistant/cici-ui.css
git commit -m "feat: refine agent avatar editing"
```

---

### Task 2: 构建说明去框与间距收紧

**Files:**
- Modify: `frontend/src/assistant/AgentBuilderShell.test.ts`
- Modify: `frontend/src/assistant/cici-ui.css`

**Interfaces:**
- Consumes: `.cici-builder__guide`、`.cici-builder__guide-title` 与现有三段说明结构。
- Produces: 每个 `.cici-builder__guide` CSS 声明块均为透明、无边框、无圆角；基础块使用 `margin: 0 0 6px` 与 `padding: 2px 4px 6px`。

- [ ] **Step 1: 写入会失败的说明区视觉约束测试**

在 `AgentBuilderShell.test.ts` 增加 Node 文件读取并断言基础块及主题覆盖块：

```ts
// @ts-expect-error Vitest executes this test in Node; production sources do not depend on Node types.
import { readFileSync } from "node:fs";

const assistantCss = readFileSync(new URL("./cici-ui.css", import.meta.url), "utf8");

it("keeps the builder guide frameless with a compact page inset", () => {
  const guideBlocks = Array.from(assistantCss.matchAll(/\.cici-builder__guide\s*\{([^}]*)\}/g), (match) => match[1]);
  expect(guideBlocks.length).toBeGreaterThanOrEqual(2);
  expect(guideBlocks[0]).toContain("margin: 0 0 6px");
  expect(guideBlocks[0]).toContain("padding: 2px 4px 6px");
  expect(guideBlocks.every((block) => block.includes("background: transparent"))).toBe(true);
  expect(guideBlocks.every((block) => block.includes("border: 0"))).toBe(true);
  expect(guideBlocks.every((block) => block.includes("border-radius: 0"))).toBe(true);
});
```

- [ ] **Step 2: 运行聚焦测试并确认 RED**

Run: `cd frontend && npm test -- src/assistant/AgentBuilderShell.test.ts`

Expected: FAIL；当前基础块仍含 `padding: 10px 12px`，主题覆盖仍含 `background: #faf4e8`。

- [ ] **Step 3: 最小化修改基础说明区样式**

将基础规则改为：

```css
.cici-builder__guide {
  margin: 0 0 6px;
  padding: 2px 4px 6px;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: #2a3f7d;
}
```

- [ ] **Step 4: 清理“鎏金账房”主题覆盖**

将后置主题规则改为：

```css
.cici-builder__guide {
  border: 0;
  border-radius: 0;
  background: transparent;
  color: #5f523f;
}
```

- [ ] **Step 5: 运行聚焦测试并确认 GREEN**

Run: `cd frontend && npm test -- src/assistant/AgentBuilderShell.test.ts`

Expected: PASS；全部测试 0 failure。

- [ ] **Step 6: 提交说明区增量**

```bash
git add frontend/src/assistant/AgentBuilderShell.test.ts frontend/src/assistant/cici-ui.css
git commit -m "style: simplify agent builder guide"
```

---

### Task 3: 回归、浏览器验收与项目状态

**Files:**
- Modify: `docs/specs/FEAT-110-agent-builder-guide-avatar-polish.md`
- Modify: `.claw/tasks/TASK-204.md`
- Modify: `.claw/test-report.md`

**Interfaces:**
- Consumes: TASK-204 实现提交、FEAT-110 验收标准、项目桌面端页面质量流程。
- Produces: 真实命令证据、桌面端截图、交互状态记录和最终交接。

- [ ] **Step 1: 运行前端聚焦测试**

Run: `cd frontend && npm test -- src/assistant/AgentBuilderShell.test.ts`

Expected: PASS，0 failure。

- [ ] **Step 2: 运行前端全量测试**

Run: `cd frontend && npm test`

Expected: 所有测试文件与测试项 PASS，0 failure。

- [ ] **Step 3: 运行生产构建**

Run: `cd frontend && npm run build`

Expected: TypeScript 与 Vite build exit 0；既有 chunk warning 可记录但不得出现新 error。

- [ ] **Step 4: 运行差异卫生检查**

Run: `git diff --check`

Expected: exit 0，无空白错误。

- [ ] **Step 5: 启动本地产品并完成真实桌面端验收**

按 `.claw/devops.md` 的已验证本地启动方式运行前后端，在真实桌面端浏览器打开智能体构建页，至少截取：默认完整页、头像 hover/focus、头像菜单展开。检查说明区无框且外层距离收紧、上传进入裁剪、移除回退首字、Escape/外部点击关闭、无横向溢出、console 0 error，并检查头像按钮 computed style 的 `transform: none` 与 `box-shadow: none`。

- [ ] **Step 6: 记录真实验证结果**

只把 Step 1 至 Step 5 实际执行结果写入 `.claw/test-report.md`，把 FEAT-110 状态更新为 `implemented`，把 TASK-204 更新为真实完成或阻塞状态；未执行的浏览器或命令不得写成通过。

- [ ] **Step 7: 提交状态与验证证据**

```bash
git add docs/specs/FEAT-110-agent-builder-guide-avatar-polish.md .claw/tasks/TASK-204.md .claw/test-report.md
git commit -m "docs: record TASK-204 verification"
```

---

## Plan Self-Review

- Spec coverage: 说明区去框/间距、头像唯一入口、条件移除、裁剪复用、外部点击、Escape、权限禁用、桌面端截图与非移动端范围均有对应步骤。
- Placeholder scan: 无 TBD、TODO、“稍后实现”或未定义接口。
- Type consistency: `resolveAgentAvatarMenuActions`、`avatarMenuOpen`、三个 ref、`beginAvatarCrop`、`updateDraft` 的名称在测试与实现步骤中一致。
