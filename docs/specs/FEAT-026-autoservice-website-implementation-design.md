---
kind: feature-spec
feature_id: FEAT-026
title: AutoService Website Implementation Design
status: draft
owner_role: brand-website-design
task_ids: TASK-072
related_decisions: FEAT-023, FEAT-025
related_issues: none
updated_at: 2026-05-09T10:40:09Z
updated_by: ai
---

# FEAT-026 - AutoService 官网实现设计文档

## 1. 项目定位

### 1.1 产品名称

**AutoService**

### 1.2 产品一句话

**AI Agents for Global After-Sales Support**

### 1.3 中文解释

AutoService 是面向全球企业的 AI 原生售后服务 Agent 平台。它支持多渠道客户接入，连接企业现有 CRM、helpdesk、订单、物流、订阅、保修和内部业务系统，让 AI Agent 自动理解售后问题、检索知识、执行售后 Playbook、调用业务系统并在复杂场景下无缝转人工。

### 1.4 官网首屏主文案

```text
AutoService
AI Agents for Global After-Sales Support

Resolve customer issues across every channel with AI agents that understand questions,
follow after-sales policies, connect to your CRM, and take action through your business systems.
```

### 1.5 不能走偏的表达

官网必须突出 AutoService 是 **AI service layer / AI Agent platform**，不是传统客服系统。

不要强调：

- 又一个 helpdesk。
- 又一个 ticketing system。
- 某一个单一渠道，例如企业微信。
- 某一个单一 CRM，例如 CloudCC。
- 只是 chatbot 或 FAQ bot。

必须强调：

- AI-native。
- Global。
- Omnichannel。
- CRM-connected。
- After-sales focused。
- Action-oriented。
- Human handoff。
- Continuous improvement。

## 2. 对标产品参考

本官网风格参考 Ada、Forethought、Decagon、Intercom Fin / Fin.ai，但不要直接复刻任何一家。AutoService 要综合它们的叙事强项，形成自己的全球化售后 Agent 品牌。

### 2.1 Ada

参考 URL：

- `https://www.ada.cx/`
- `https://www.ada.cx/platform/`

可借鉴点：

- 强调 agentic customer experience，而不是单点聊天机器人。
- 首屏明确说 AI agents resolve, act, and continuously improve。
- 强调 omnichannel、multilingual、enterprise scale。
- 用客户 logo、行业场景和结果指标建立信任。
- 以 Measure / Test / Coach / Extend 形成运营闭环。

AutoService 借鉴方式：

- 采用“global after-sales support operating layer”叙事。
- 强调 AI Agent 持续学习和持续优化。
- 使用多渠道、多语言、业务系统动作作为第一屏之后的核心证据。
- 用行业场景说明，不只堆功能卡。

### 2.2 Forethought

参考 URL：

- `https://forethought.ai/`
- `https://forethought.ai/platform`

可借鉴点：

- 多 Agent 系统覆盖 Solve、Triage、Assist、QA、Insights。
- 从历史工单、知识库和真实客服数据学习。
- 不只解决客户问题，还辅助人工、路由工单、做质检和洞察。
- 信息架构按角色、行业、客户时刻展开。

AutoService 借鉴方式：

- 官网需要表达 AutoService 不是单一 Agent，而是一套售后 AI 工作流：
  - Resolution Agent。
  - Triage Agent。
  - Handoff Agent。
  - QA / Insights Agent。
- 加入“learns from tickets and outcomes”的叙事。
- 在页面中明确售后运营闭环：resolve -> classify -> hand off -> learn -> improve。

### 2.3 Decagon

参考 URL：

- `https://decagon.ai/about`
- `https://decagon.ai/product/integrations`

可借鉴点：

- 企业级 AI concierge 定位。
- 强调 voice、chat、email、SMS 等多渠道体验。
- 强调 integrations：CRM、helpdesk、call center、knowledge base、API、MCP。
- 强调 AI 能 retrieve data、take action、handle escalations。
- 有 AOP / workflows、Experiments、Testing & QA、Watchtower 等优化能力。

AutoService 借鉴方式：

- 官网必须将“connect and act”做成核心区块。
- 展示 AutoService 如何连接 CloudCC CRM、Salesforce、Zendesk、HubSpot、Freshdesk、Intercom、ServiceNow、Dynamics、Shopify、Stripe、custom API。
- 用系统动作证明不是聊天机器人：check order、track shipment、verify warranty、create repair case、draft refund recommendation。
- 加入 Agentic Playbooks / SOP 作为关键差异。

### 2.4 Intercom Fin / Fin.ai

参考 URL：

- `https://fin.ai`
- `https://www.intercom.com/fin`

可借鉴点：

- 叙事强，非常直接：#1 AI Agent for all your customer service。
- Train / Test / Deploy / Analyze 四步闭环清晰。
- 强调 works with any helpdesk。
- 强调 AI Engine 的可解释流程：refine query、retrieve content、rerank、generate、validate、optimize。
- 定价口径偏 outcome，而不是 seat。

AutoService 借鉴方式：

- 官网要有一段非常清晰的 AI engine 流程图。
- 采用 Understand / Reason / Act / Improve 四步，比传统 Train/Test 更贴合 AutoService。
- 强调 works with your existing CRM and support stack。
- 后续 pricing 可以考虑 per resolution / per automated case / per AI-handled interaction。

## 3. 品牌策略

### 3.1 品牌性格

三个关键词：

- **Capable**：能处理真实售后问题，不只是会说。
- **Composed**：可靠、清晰、企业级，不浮夸。
- **Global**：多渠道、多语言、多系统，不绑定单一国家或生态。

### 3.2 品牌隐喻

AutoService 不应表现成“机器人客服头像”，而应表现成：

> A global service control layer, with AI agents moving across channels and systems.

视觉隐喻：

- Service routes：客户消息从不同渠道进入同一个 Agent 层。
- Agent cockpit：AI 正在理解、查询、执行、转交。
- System connectors：CRM、order、logistics、warranty、billing 作为可插拔节点。
- Resolution flow：从问题到解决结果的闭环，而不是从问题到 ticket。

### 3.3 语言风格

英文官网主语言应简洁、强势、B2B。

推荐表达：

- Resolve customer issues, not just tickets.
- AI agents that understand, reason, act, and improve.
- Connect to your CRM and business systems.
- Automate after-sales support across every channel.
- Human handoff with full context.
- Built for complex post-purchase journeys.

避免表达：

- Replace your support team。
- Chatbot。
- No humans needed。
- Magical automation。
- One-click full automation。

## 4. 视觉方向

### 4.1 Register

这是 **brand / marketing website**，不是现有 AgentCiCi 后台产品页。不要套用现有 `鎏金账房` product register。

### 4.2 视觉北极星

**Global service command fabric**

解释：

一个明亮、精准、有轻微未来感的全球服务网络。不是暗黑赛博，不是花哨 AI 渐变，也不是传统 SaaS 白底卡片堆。页面应像一套高端企业 AI 操作系统的品牌门面：清晰、快速、带有“真实系统正在运转”的感觉。

### 4.3 色彩策略

采用 **restrained + one electric accent**。

建议 OKLCH 色彩：

```css
:root {
  --as-ink: oklch(18% 0.025 250);
  --as-ink-muted: oklch(42% 0.035 250);
  --as-canvas: oklch(97% 0.008 250);
  --as-surface: oklch(99% 0.006 250);
  --as-line: oklch(88% 0.018 250);
  --as-accent: oklch(62% 0.19 205);
  --as-accent-strong: oklch(52% 0.22 205);
  --as-lime: oklch(78% 0.18 145);
  --as-warn: oklch(72% 0.16 70);
  --as-danger: oklch(60% 0.18 25);
}
```

主背景：

- 95% 以上使用明亮冷白和淡蓝灰。
- Accent 使用 cyan-blue，表示 intelligence / signal / action。
- Lime 只用于成功解决、automation completed。
- 不用紫色渐变，避免普通 AI SaaS 感。

### 4.4 字体策略

字体语气：**precise, international, operational**。

推荐：

- Display / UI：`Aptos`、`Suisse Int'l`、`ABC Favorit`、`Geist` 其中一种。
- Fallback：`ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif`。
- Code / system labels：`Berkeley Mono`、`JetBrains Mono` 或 `ui-monospace`。

如果用 Google Fonts：

- `Geist` 作为主字体。
- 不使用 Inter、Space Grotesk、DM Sans 等过度泛化字体。

### 4.5 图像与视觉资产

本网站不依赖真人客服照片。主视觉应以产品化图形为核心：

- Agent workflow animation。
- Channel stream visualization。
- CRM connector map。
- AI reasoning trace。
- Handoff summary panel。

如果需要品牌图：

- 选择真实办公/全球客服运营场景作为辅助，不做英雄图主视觉。
- 首屏不使用抽象“机器人头像”。

## 5. 网站信息架构

### 5.1 顶部导航

Desktop nav：

```text
AutoService
Product
Channels
Integrations
AI Engine
Resources
Pricing
[Book a demo]
```

移动端：

- Logo。
- Menu icon。
- 展开后使用单列导航。
- CTA 固定在菜单底部。

### 5.2 推荐页面

首版先做单页官网 landing page，后续扩展：

- `/` Home。
- `/integrations` Integrations。
- `/channels` Channels。
- `/ai-engine` AI Engine。
- `/security` Security。
- `/pricing` Pricing。
- `/resources` Blog / guides。

当前文档聚焦 `/` 首页落地。

## 6. 首页完整页面结构

### Section 1: Hero

目的：3 秒内让访客知道 AutoService 是什么。

布局：

- 左侧：主文案 + CTA。
- 右侧：动态 product visual，不是静态插画。
- 背景：浅色渐变，不使用网格纹理；动态感只保留在必要的 service route lines 和业务连接关系上。

文案：

```text
AutoService
AI Agents for Global After-Sales Support

Resolve customer issues across every channel with AI agents that understand questions,
follow after-sales policies, connect to your CRM, and take action through your business systems.

[Book a demo] [See how it works]
```

Hero visual：

一个三层面板：

1. Incoming channels：
   - WhatsApp
   - Email
   - Web chat
   - Voice
   - SMS
   - API

2. AutoService agent trace：
   - Intent detected: return eligibility
   - Customer matched in CRM
   - Order status retrieved
   - Warranty policy checked
   - Action: handoff summary generated

3. Outcome：
   - Resolved automatically
   - Escalated with full context

实现建议：

- 用 CSS/SVG 做线路和状态流动。
- 动画只做轻微 pulse 和 line-progress。
- 不使用视频作为首版必要资源。

### Section 2: Social Proof / Trust Strip

目的：快速建立企业级可信感。

首版如果没有客户 logo，使用 integration proof 替代：

```text
Works with the systems your teams already use
CloudCC CRM · Salesforce · Zendesk · HubSpot · Freshdesk · Intercom · ServiceNow · Dynamics · Shopify · Stripe · Custom API
```

视觉：

- 单行横向 logo rail。
- 可用 text logo 占位，后续替换真实 SVG。
- 不要做彩色 logo 大卡片。

### Section 3: Problem

标题：

```text
After-sales support is fragmented. Your AI agent should not be.
```

三列问题，不做普通卡片网格。采用横向 service fracture diagram：

```text
Customers arrive everywhere
Chat, email, phone, social, marketplace, in-app.

Answers live everywhere
Policies, manuals, help center, CRM notes, historical tickets.

Actions happen everywhere
Orders, logistics, billing, warranty, subscriptions, repair systems.
```

底部转折：

```text
AutoService connects the conversation, the knowledge, and the action layer.
```

### Section 4: How It Works

标题：

```text
From customer message to resolved issue, automatically.
```

流程：

```text
1. Understand
Detect intent, sentiment, language, customer context.

2. Reason
Apply policies, SOPs, eligibility rules, and exception logic.

3. Act
Retrieve data, create cases, update systems, trigger workflows.

4. Improve
Test, monitor, learn from outcomes, surface knowledge gaps.
```

视觉：

- 中间一条 horizontal flow。
- 每一步点击/hover 展开一个真实售后例子。
- Mobile 改为垂直 timeline。

### Section 5: After-Sales Workflows

标题：

```text
Built for real post-purchase journeys
```

不要用一堆相同卡片。改成“售后任务矩阵”。

Rows：

- Order status。
- Shipping delay。
- Return eligibility。
- Warranty check。
- Repair intake。
- Refund pre-check。
- Subscription cancellation。
- Product troubleshooting。
- Complaint escalation。

Columns：

- Knowledge。
- System data。
- Playbook。
- Action。
- Handoff。

示例：

```text
Shipping delay
Knowledge: shipping policy
System data: order + carrier status
Playbook: exception handling
Action: update customer / create case
Handoff: if lost package or compensation required
```

### Section 6: Integrations

标题：

```text
Connect AutoService to your CRM and service stack
```

副标题：

```text
No migration required. AutoService works as the AI service layer above the systems you already trust.
```

分组：

- CRM：CloudCC CRM、Salesforce、HubSpot、Microsoft Dynamics、Custom CRM。
- Helpdesk：Zendesk、Freshdesk、Intercom、ServiceNow、Gorgias。
- Commerce：Shopify、BigCommerce、WooCommerce。
- Payments / subscription：Stripe、Chargebee、Recurly。
- Logistics：FedEx、UPS、DHL、ShipStation、custom carrier API。
- Knowledge：Confluence、Contentful、Notion、Help Center、PDFs。
- Voice / messaging：Voice、Email、Web chat、Messenger、WhatsApp、SMS、Instagram、In-app、Help center、Mobile SDK、API、Custom channels。

视觉：

- 一个中心 AutoService node，周围是 connector spokes。
- 点击每个类别，右侧显示“what AutoService can do with it”。

### Section 7: Agentic Playbooks

标题：

```text
Turn after-sales SOPs into AI-executable playbooks
```

文案：

```text
AutoService follows your policies step by step, asks for missing information,
checks eligibility, and executes approved workflows only within the guardrails you define.
```

展示一个 playbook preview：

```text
Return Eligibility Playbook

IF order delivered within 30 days
AND product category is returnable
AND warranty status is valid
THEN provide return instructions
ELSE explain exception and escalate if needed
```

右侧显示 agent output：

```text
Customer is eligible for return.
Order delivered 12 days ago.
Product category allows returns.
Next step: generate return label request.
```

### Section 8: Human Handoff

标题：

```text
When humans step in, they get the whole story.
```

展示 handoff summary：

```text
Reason for escalation: refund requires approval
Customer: Maria Lopez
Channel: WhatsApp
Issue: delayed shipment, refund requested
Checked: CRM profile, order #A10291, carrier status, refund policy
AI recommendation: approve refund review, carrier missed delivery SLA
Risk: medium
```

说明：

- AutoService 不是“拒绝处理后丢给人工”。
- 它把人工接手前的事实、尝试动作、建议方案全部整理好。

### Section 9: AI Engine

标题：

```text
AI that understands, reasons, acts, and improves
```

这是全站最重要的技术信任区，参考 Fin 的 AI Engine 叙事，但用 AutoService 语言。

六步 engine：

```text
1. Refine the customer request
Normalize language, detect intent, extract entities.

2. Retrieve the right knowledge
Find policies, manuals, articles, prior ticket patterns.

3. Read business context
Pull CRM, order, shipment, warranty, subscription, and ticket data.

4. Execute approved playbooks
Follow after-sales SOPs and determine allowed actions.

5. Validate response and risk
Check confidence, source coverage, policy fit, escalation rules.

6. Learn from every outcome
Surface knowledge gaps, failed workflows, and coaching suggestions.
```

视觉：

- Circular engine diagram 或 stacked pipeline。
- 每一步展开包含“input / operation / output”。
- 不能用普通 6 个图标卡片。

### Section 10: Test, Monitor, Optimize

标题：

```text
Launch with confidence. Improve with evidence.
```

能力：

- Batch test using historical tickets。
- Simulate full customer conversations。
- Inspect answer sources。
- Monitor resolution rate。
- Track handoff reasons。
- Detect knowledge gaps。
- Compare playbook versions。
- Audit every tool call。

这里参考 Forethought 的 QA/Insights 和 Fin 的 Test/Analyze。

视觉：

- 仪表盘片段，不用完整后台截图。
- 展示三类数据：
  - Resolution。
  - Escalation。
  - Knowledge gaps。

### Section 11: Security / Governance

标题：

```text
Enterprise control for AI service operations
```

要点：

- Role-based access。
- Tool permissions。
- Action approvals。
- Audit trails。
- PII redaction。
- Source-grounded responses。
- Human escalation rules。
- Model and workflow monitoring。
- Region-aware data controls，后续支持。

语气：

不要夸“绝对安全”，说“designed for enterprise control”。

### Section 12: Final CTA

标题：

```text
Turn after-sales support into an AI-powered service engine.
```

副标题：

```text
Start with one workflow. Connect one CRM. Automate your first after-sales resolution in weeks, not quarters.
```

CTA：

- Book a demo。
- Talk to an expert。

## 7. 页面组件设计

### 7.1 Header

高度：

- Desktop: 72px。
- Mobile: 60px。

行为：

- 初始透明或浅色。
- Scroll 后增加细线和轻微背景模糊，但不要玻璃拟态。
- CTA 按钮常驻。

### 7.2 Button

Primary：

```css
.as-button-primary {
  background: var(--as-ink);
  color: var(--as-canvas);
  border: 1px solid var(--as-ink);
  border-radius: 999px;
  height: 44px;
  padding: 0 20px;
}
```

Secondary：

```css
.as-button-secondary {
  background: transparent;
  color: var(--as-ink);
  border: 1px solid var(--as-line);
  border-radius: 999px;
  height: 44px;
  padding: 0 20px;
}
```

Hover：

- Primary 微微上移或改变亮度。
- Secondary border 变强。
- 不使用 glow。

### 7.3 Product Visual Panels

样式：

- 细线边框。
- 低圆角 12px。
- 背景为浅色或深墨局部代码面板。
- 有清晰的系统标签，例如 `Intent`, `CRM`, `Policy`, `Action`。

不要：

- 大量渐变卡片。
- 机器人头像。
- 浮夸数字 hero metrics。

### 7.4 Integration Logos

首版可以用文字 logo：

```text
Salesforce
Zendesk
HubSpot
Freshdesk
Intercom
ServiceNow
Dynamics
Shopify
Stripe
Custom API
```

实现时用纯文本或简单 SVG，避免侵权或不合规 logo 使用。正式上线前确认商标使用规范。

## 8. 响应式设计

### Desktop ≥ 1200px

- Hero 两栏。
- 产品视觉宽度 48%。
- 内容最大宽度 1180px。
- Section spacing 112px 到 148px。

### Tablet 768px 到 1199px

- Hero 仍可两栏，但视觉缩小。
- Integrations 由 radial 改为 2 列类别。
- Workflow matrix 支持横向滚动或转为 accordion。

### Mobile < 768px

- Hero 单栏。
- CTA stacked or inline based on width。
- Product visual 放在文案下方。
- How it works 改成 vertical timeline。
- Workflow matrix 改成 accordion。
- Integration map 改成 category list。

移动端重点：

- 首屏 H1 不超过 3 到 4 行。
- CTA 首屏可见。
- 不出现横向溢出。
- 动画减少。

## 9. 动效设计

原则：

- 动效表现“系统在处理”，不是为了装饰。
- 只动画 opacity、transform、stroke-dashoffset。
- 不动画 height、width、top、left。

建议动效：

- Hero route line progress。
- Agent trace row staged reveal。
- Integration node subtle pulse。
- How-it-works step active state on scroll。
- Handoff summary slide-in。

Reduced motion：

```css
@media (prefers-reduced-motion: reduce) {
  * {
    animation-duration: 0.001ms !important;
    transition-duration: 0.001ms !important;
  }
}
```

## 10. 技术实现建议

### 10.1 推荐技术栈

如果作为独立官网：

- Next.js 或 Astro。
- TypeScript。
- CSS Modules 或 vanilla CSS。
- Static generation。
- 简单 CMS 可后续接入。

如果先放进当前 repo 的前端：

- 新增 route：`/autoservice`。
- 不复用产品后台 CSS。
- 独立样式文件：`frontend/src/autoservice/autoservice-site.css`。
- 独立组件目录：`frontend/src/autoservice/`。

推荐文件结构：

```text
frontend/src/autoservice/
├── AutoServiceLanding.tsx
├── AutoServiceHero.tsx
├── AutoServiceHowItWorks.tsx
├── AutoServiceWorkflowMatrix.tsx
├── AutoServiceIntegrations.tsx
├── AutoServiceAiEngine.tsx
├── AutoServiceHandoff.tsx
├── AutoServiceSecurity.tsx
├── autoservice-copy.ts
└── autoservice-site.css
```

### 10.2 Copy 数据结构

建议将页面文案抽为数据：

```ts
export const AUTOSERVICE_CHANNELS = [
  "Web chat",
  "Email",
  "WhatsApp",
  "SMS",
  "Voice",
  "Social",
  "API",
];

export const AUTOSERVICE_WORKFLOWS = [
  {
    name: "Return eligibility",
    knowledge: "Return policy",
    data: "Order status",
    playbook: "Eligibility rules",
    action: "Return request draft",
    handoff: "Refund approval",
  },
];
```

### 10.3 SEO

Meta title：

```text
AutoService | AI Agents for Global After-Sales Support
```

Meta description：

```text
AutoService automates after-sales support across chat, email, WhatsApp, web, voice, and API channels with AI agents that connect to your CRM and business systems.
```

Open Graph：

- `og:title`: AutoService, AI Agents for Global After-Sales Support
- `og:description`: Resolve customer issues across every channel with AI agents that understand, reason, act, and improve.
- `og:image`: 1200x630 brand image showing channel streams -> AutoService -> resolved outcome。

SEO keywords：

- AI after-sales support。
- AI customer service agent。
- Omnichannel support automation。
- CRM AI agent。
- Post-purchase support automation。
- AI helpdesk automation。
- Customer service AI agent。

### 10.4 Analytics

Track events：

- `hero_book_demo_click`
- `hero_see_how_it_works_click`
- `integration_category_click`
- `workflow_row_expand`
- `ai_engine_step_expand`
- `final_cta_click`
- `pricing_click`

Need attribution：

- UTM source。
- Industry。
- Selected integration interest。
- CTA source section。

## 11. 内容版本

### 11.1 Hero 英文终版

```text
AutoService
AI Agents for Global After-Sales Support

Resolve customer issues across every channel with AI agents that understand questions,
follow after-sales policies, connect to your CRM, and take action through your business systems.
```

### 11.2 Hero 中文说明版

```text
AutoService 是面向全球售后场景的 AI Agent 平台，
帮助企业在多渠道中自动响应客户、查询业务系统、执行售后流程，
并在复杂场景下无缝转人工。
```

### 11.3 Value Proposition

```text
Customers reach you everywhere. AutoService answers everywhere.

Not just answers. Real after-sales actions.

AI handles the routine. Humans handle the exceptions.
```

### 11.4 Final CTA

```text
Turn after-sales support into an AI-powered service engine.

Start with one workflow. Connect one CRM. Automate your first after-sales resolution in weeks, not quarters.
```

## 12. 设计验收标准

### 内容验收

- 首屏 3 秒内说明 AutoService 是全球多渠道 AI 售后 Agent。
- 页面不强调 CloudCC 或企业微信。
- 至少出现一次 CRM / helpdesk / business systems integrations。
- 至少出现一次 after-sales workflows / playbooks。
- 至少出现一次 human handoff with full context。
- 至少出现一次 continuous improvement / test / monitor / optimize。

### 视觉验收

- 不使用紫色 AI 渐变主视觉。
- 不使用机器人头像作为主视觉。
- 不使用相同卡片网格铺满全页。
- 不使用传统 SaaS hero metrics 模板。
- 桌面首屏必须有产品化动态视觉，而不是纯文字。
- 移动端首屏 CTA 可见。

### 技术验收

- `npm run build` 成功。
- Lighthouse performance 目标 ≥ 90。
- 无横向滚动。
- 图片或 SVG 有 alt / aria-hidden 处理。
- `prefers-reduced-motion` 生效。
- 所有 CTA 可键盘聚焦。

### 浏览器验收

- Desktop 1440px。
- Laptop 1280px。
- Tablet 834px。
- Mobile 390px。

每个断点截图检查：

- 文案不溢出。
- Hero visual 不压缩变形。
- Integration 区块不挤压。
- Workflow matrix 在移动端转为可读结构。
- CTA 不丢失。

## 13. 首版实施计划

### Phase 1: Static Brand Landing

- 首页完整静态落地。
- 使用 CSS/SVG 实现 hero product visual。
- 只做 Book demo 表单入口或 mailto/外链。
- 无后端依赖。

### Phase 2: Interactive Proof

- Hero trace 自动切换不同售后场景。
- Integrations 分类可交互。
- AI Engine steps 可展开。
- Workflow matrix 可筛选行业。

### Phase 3: Conversion + CMS

- Demo 表单接 CRM。
- Resource / guide 页面。
- Integration detail pages。
- Industry landing pages。
- Pricing 或 outcome-based pricing 页面。

### Phase 4: Product-Led Demo

- 在线模拟对话。
- 选择 channel + CRM + workflow。
- 展示 AutoService 如何查询系统、执行 playbook、转人工。

## 14. 实现风险

- 如果视觉太像 Ada/Fin，会缺少独立品牌识别。
- 如果过度强调集成列表，会像 integration marketplace，而不是 AI Agent。
- 如果文案过度讲“客服系统”，会掉回 Zendesk/Udesk 心智。
- 如果只展示聊天框，会被理解成 chatbot。
- 如果首屏没有“act through business systems”，AI 能力会显得不够强。

## 14.1 原型实现记录

2026-05-09 已在当前前端仓库完成设计原型：

- 路由已从“中英文切换”调整为两个独立站点页面：`/autoservice/global` 为国际站，`/autoservice/cn` 为中国站；`/autoservice` 重定向到国际站，旧 `/autoservice/en` 与 `/autoservice/zh` 仅保留兼容重定向。
- 文件：`frontend/src/autoservice/AutoServiceLanding.tsx`、`frontend/src/autoservice/autoservice-copy.ts`、`frontend/src/autoservice/autoservice-site.css`。
- 国际站内容只面向海外客户与全球服务栈：WhatsApp、Salesforce、Zendesk、HubSpot、Intercom、ServiceNow、Shopify、Stripe、FedEx、DHL 等，不展示企业微信、钉钉、飞书或国内 CRM/物流/电商内容。
- 中国站内容只面向国内客户与国内服务生态：企业微信、微信客服、钉钉、飞书、CloudCC CRM、销售易、纷享销客、Udesk、有赞、顺丰、菜鸟、京东物流、飞书文档、钉钉文档等，不展示 WhatsApp、Salesforce、Zendesk、Stripe、Shopify 等国际服务栈内容。
- 已按评审意见将 logo 改为鎏金色，主 CTA 改为金色按钮，移除首屏、流程详情、Playbook、指标仪表和最终 CTA 中的纯黑背景块。
- 中国站保留 `CloudCC CRM` 作为国内 CRM 集成之一，但页面仍不把 CloudCC 作为单一主品牌或唯一 CRM。
- 国际站渠道集合为 Voice、Email、Web chat、Messenger、WhatsApp、SMS、Instagram、In-app、Help center、Mobile SDK、API、Custom channels；中国站渠道集合为企业微信、微信客服、微信公众号、微信小程序、钉钉、飞书、电话、短信、网页客服、App 内客服、服务 API、自有渠道。
- 已按后续视觉评审意见重构两个低质感解释型图表：workflows 区从普通表格改为带编号、节点和阶段路径的 journey board；integrations 区从普通中心节点图改为 AI service layer 拓扑面板，并配套右侧能力详情与连接器列表。
- 已按最新评审意见将页头 logo 替换为用户提供的新 AutoService 品牌图：导航栏使用同源裁切出的图形标与字标资产，避免直接缩放整张竖版 logo 导致文字不可读。
- 已修正移动端首屏产品视觉面板从绝对定位改为自然堆叠，并放大桌面首屏视觉高度，确保渠道、链路和处理结果面板不互相遮挡。
- 已按最新反馈收敛中文站文案：保留企业微信、微信客服、钉钉、飞书、CloudCC CRM、销售易、纷享销客、Udesk、有赞、顺丰、菜鸟等生态和系统，但页面标题、描述、分区文案不再强调“国内”“中国站”或“面向国内”，改为正常功能描述。
- 已移除 AutoService 页面中的背景网格纹理，包括全页背景、流程详情、journey card、集成面板、AI Engine 详情和最终 CTA 的网格叠层；保留浅色渐变、分隔线和必要的服务路径线。
- 已按最新浏览器批注去掉顶部站点文字入口，不再显示 `International` 或 `企微钉钉飞书版`；中文站信任条只保留企业微信、钉钉、飞书、CloudCC CRM、Udesk、顺丰和自有 API。
- 已从中文站可见内容中移除销售易、纷享销客、用友 YonSuite、金蝶云、有赞；人工接管标题改为“转给人工前，先把情况说清楚。”，资源区标题改为“上线后看得见效果，也知道哪里要改。”。
- 预约演示表单已接入 `autoservice_demo_request` 后端存储；提交入口保持在公开官网，线索跟进入口已迁入平台运营控制面 `/platform/website-leads`，由平台角色通过 `/platform/autoservice/demo-requests` 列表和更新状态。
- 正式上线前仍需补 CRM 归因、UTM、通知流和销售/运营分派策略。

## 15. 当前结论

AutoService 官网首版应该用一个清晰的全球 B2B AI Agent 叙事：

```text
Omnichannel customer messages
  -> AutoService AI agents
  -> CRM + business systems
  -> after-sales playbooks
  -> resolution or human handoff
  -> continuous improvement
```

最终访客应带走一个判断：

> AutoService is not another helpdesk. It is the AI service layer that resolves after-sales issues across channels and systems.
