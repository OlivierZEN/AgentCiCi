import { type FormEvent, useEffect, useMemo, useState } from "react";
import { useLocation } from "react-router-dom";
import "./suite-site.css";

type SuiteSite = "china" | "global";

type LifecycleStage = {
  key: string;
  label: string;
  product: string;
  title: string;
  copy: string;
  inputs: string[];
  actions: string[];
  output: string;
};

type ProductPanel = {
  name: string;
  role: string;
  summary: string;
  startsWith: string;
  produces: string;
  bestFor: string[];
  href: string;
  tone: string;
};

type SuiteCopy = {
  site: SuiteSite;
  htmlLang: string;
  seo: {
    title: string;
    description: string;
    ogTitle: string;
    ogDescription: string;
  };
  header: {
    marketLabel: string;
    marketHref: string;
    marketName: string;
    nav: Array<{ href: string; label: string }>;
    cta: string;
  };
  hero: {
    kicker: string;
    title: string;
    lead: string;
    primaryCta: string;
    secondaryCta: string;
    proof: string[];
    visualTitle: string;
    visualCaption: string;
    nodes: string[];
  };
  lifecycle: {
    kicker: string;
    title: string;
    intro: string;
    stages: LifecycleStage[];
  };
  matrix: {
    kicker: string;
    title: string;
    intro: string;
    products: ProductPanel[];
  };
  platform: {
    kicker: string;
    title: string;
    intro: string;
    pillars: Array<{ title: string; copy: string; items: string[] }>;
  };
  crm: {
    kicker: string;
    title: string;
    intro: string;
    records: Array<{ label: string; copy: string }>;
  };
  market: {
    kicker: string;
    title: string;
    intro: string;
    lanes: Array<{ title: string; copy: string; tags: string[] }>;
  };
  rollout: {
    kicker: string;
    title: string;
    steps: Array<{ title: string; copy: string }>;
  };
  cta: {
    kicker: string;
    title: string;
    intro: string;
    fields: {
      company: string;
      contact: string;
      email: string;
      focus: string;
      note: string;
      submit: string;
      submitted: string;
    };
    focusOptions: string[];
  };
  footer: {
    tagline: string;
    links: Array<{ href: string; label: string }>;
  };
};

const SUITE_COPY: Record<SuiteSite, SuiteCopy> = {
  china: {
    site: "china",
    htmlLang: "zh-CN",
    seo: {
      title: "AI 治理平台 | 企业 AI 客户运营套件",
      description:
        "AI 治理平台将 AutoReachAI、FollowUpAI、AutoService、CiCi 智能体平台与 CloudCC CRM 连接成完整客户运营闭环，覆盖获客、跟进、售后与智能体治理。",
      ogTitle: "AI 治理平台, 企业 AI 客户运营套件",
      ogDescription: "面向企业增长、销售跟进与售后服务的 AI 客户运营闭环，连接智能体治理与 CRM 数据沉淀。",
    },
    header: {
      marketLabel: "",
      marketHref: "/suite/global",
      marketName: "Global",
      nav: [
        { href: "#lifecycle", label: "完整业务闭环" },
        { href: "#products", label: "产品矩阵" },
        { href: "#platform", label: "AI 治理平台" },
        { href: "#market", label: "GTM" },
      ],
      cta: "预约闭环演示",
    },
    hero: {
      kicker: "AI 治理平台",
      title: "让 AI 员工跑完整个客户生命周期。",
      lead:
        "AutoReachAI 负责获客触达，FollowUpAI 负责销售跟进，AutoService 负责售后服务。CiCi 统一运行与治理智能体，CloudCC CRM 沉淀客户、商机与服务数据。",
      primaryCta: "预约闭环演示",
      secondaryCta: "查看产品矩阵",
      proof: ["企业微信与微信客服", "飞书/钉钉协同", "CloudCC CRM 原生闭环", "私有化与混合部署", "智能体评测与审计"],
      visualTitle: "全链路客户运营闭环",
      visualCaption: "从市场线索到售后服务，所有客户事实回到 CRM，所有 AI 员工由 CiCi 统一治理。",
      nodes: ["获客触达", "销售跟进", "CRM 沉淀", "售后服务", "运行治理"],
    },
    lifecycle: {
      kicker: "客户生命周期",
      title: "不是三个工具，而是一条能交付的业务闭环。",
      intro: "企业客户更关心系统能不能接得上、流程能不能管得住、出问题能不能查得清。主站要先回答这些落地问题。",
      stages: [
        {
          key: "reach",
          label: "获客",
          product: "AutoReachAI",
          title: "从行业与产品出发，找到高匹配潜客。",
          copy: "分析官网、产品资料和目标市场，生成 ICP、潜客名单和个性化触达内容，适合出海团队、B2B 制造和渠道拓展。",
          inputs: ["产品介绍", "目标行业", "客户画像"],
          actions: ["生成 ICP", "发现潜客", "个性化邮件"],
          output: "Lead、来源、意向评分写入 CloudCC CRM。",
        },
        {
          key: "follow",
          label: "跟进",
          product: "FollowUpAI",
          title: "把线索回复、企微沟通和销售动作接起来。",
          copy: "围绕微信、邮件、WhatsApp 和 CRM 活动持续跟进，提醒销售下一步，避免线索在多人协作和长周期成交中掉线。",
          inputs: ["新线索", "客户回复", "销售任务"],
          actions: ["自动跟进", "生成回复", "同步活动"],
          output: "Activity、下一步、商机信号回到 CloudCC CRM。",
        },
        {
          key: "service",
          label: "服务",
          product: "AutoService",
          title: "售后问题先自动解决，复杂事项带上下文转人工。",
          copy: "接入企业微信微信客服、知识库、订单、工单和保修等系统，让 AI 先回答、查询、归纳，再把完整摘要交给人工。",
          inputs: ["客户问题", "售后知识", "业务记录"],
          actions: ["知识问答", "只读查询", "接管摘要"],
          output: "Case、服务备注、客户问题沉淀回 CRM。",
        },
      ],
    },
    matrix: {
      kicker: "应用矩阵",
      title: "三类 AI 员工，各自负责一个高价值场景。",
      intro: "先从最痛的场景上线，再把客户数据、知识和操作策略逐步接入完整闭环。",
      products: [
        {
          name: "AutoReachAI",
          role: "AI 获客员工",
          summary: "面向出海获客和 B2B 销售线索发现。",
          startsWith: "官网、产品资料、目标市场、行业名单",
          produces: "潜客、个性化触达、意向评分",
          bestFor: ["外贸团队", "B2B 制造", "代理商拓展"],
          href: "https://autoreachai.ai/",
          tone: "growth",
        },
        {
          name: "FollowUpAI",
          role: "AI 销售跟进员工",
          summary: "把销售线索的回复、提醒、下一步和 CRM 活动持续串起来。",
          startsWith: "客户回复、销售任务、CRM 线索",
          produces: "跟进记录、演示意向、下一步动作",
          bestFor: ["销售团队", "客服转销售", "私域跟进"],
          href: "https://followupai.ai/",
          tone: "sales",
        },
        {
          name: "AutoService",
          role: "AI 售后服务员工",
          summary: "在微信客服、知识库和业务系统之间自动处理售后问题。",
          startsWith: "客户咨询、知识库、订单/工单/保修数据",
          produces: "服务答复、查询结果、人工接管摘要",
          bestFor: ["售后团队", "客户成功", "服务运营"],
          href: "/autoservice/cn",
          tone: "service",
        },
      ],
    },
    platform: {
      kicker: "CiCi 平台底座",
      title: "每个 AI 员工都需要运行、权限、评测和观测。",
      intro: "企业不是只要一个聊天窗口。真正落地时，需要知道哪个 Agent 在答、用了哪些知识、调用了哪些工具、失败在哪里、谁有权限发布。",
      pillars: [
        {
          title: "运行与编排",
          copy: "统一管理 Agent、知识库、工具、工作流和多渠道入口。",
          items: ["Agent Runtime", "RAG 知识库", "MCP / API 工具", "流程编排"],
        },
        {
          title: "治理与安全",
          copy: "按组织、角色、run-as 用户和渠道控制权限，高风险动作进入人工确认。",
          items: ["权限边界", "版本审批", "发布回滚", "审计记录"],
        },
        {
          title: "评测与观测",
          copy: "上线前用题集回归，上线后看 trace、工具命中、人工接管和失败归因。",
          items: ["评测集", "运行轨迹", "质量回归", "成本计量"],
        },
      ],
    },
    crm: {
      kicker: "CloudCC CRM 闭环",
      title: "客户事实回到 CRM，下一次沟通才会更准。",
      intro: "CloudCC CRM 承接客户、线索、商机、联系人、工单和服务记录，让三个 AI 员工共享同一份业务事实。",
      records: [
        { label: "Lead", copy: "AutoReachAI 创建线索、来源、行业和意向评分。" },
        { label: "Activity", copy: "FollowUpAI 写入跟进记录、回复状态和下一步建议。" },
        { label: "Case", copy: "AutoService 沉淀问题类型、处理结果和人工接管摘要。" },
        { label: "Insight", copy: "CiCi 根据运行轨迹和 CRM 结果帮助运营优化知识与策略。" },
      ],
    },
    market: {
      kicker: "落地治理特点",
      title: "围绕渠道、组织权限和交付治理设计，而不是只讲 AI 能力。",
      intro: "企业通常要同时处理微信生态、协同工具、CRM 权限、私有化部署和服务团队接管。页面需要把这些业务现实说清楚。",
      lanes: [
        {
          title: "渠道优先接入",
          copy: "围绕企业微信、微信客服、飞书、钉钉、邮件和自有门户，把客户入口接入同一套智能体运行层。",
          tags: ["企业微信", "微信客服", "飞书", "钉钉"],
        },
        {
          title: "业务系统可控连接",
          copy: "CloudCC CRM 是首要闭环底座，后续可接订单、工单、ERP、物流和企业自有 API。",
          tags: ["CloudCC CRM", "订单", "工单", "自有 API"],
        },
        {
          title: "可交付可审计",
          copy: "支持组织权限、run-as 用户、知识边界、版本发布、人工接管和运行追踪，适合企业项目交付。",
          tags: ["权限", "审计", "评测", "私有化"],
        },
      ],
    },
    rollout: {
      kicker: "落地路径",
      title: "先上线一个 AI 员工，再扩展到完整客户运营闭环。",
      steps: [
        { title: "选场景", copy: "从获客、跟进或售后里选择最痛的一段，明确可量化目标。" },
        { title: "接系统", copy: "连接 CloudCC CRM、渠道账号、知识库和必要的只读业务接口。" },
        { title: "建策略", copy: "配置 Agent prompt、知识范围、工具权限、人工接管和评测题集。" },
        { title: "灰度上线", copy: "先跑小范围真实会话，通过 trace 和 CRM 结果调整策略。" },
        { title: "扩闭环", copy: "把下一个生命周期阶段接入同一套 CiCi 平台和 CRM 数据。" },
      ],
    },
    cta: {
      kicker: "闭环演示",
      title: "看一次从获客到售后的完整 AI 员工协作。",
      intro: "留下你的重点场景，我们会按你的行业、渠道和 CRM 现状准备演示路径。",
      fields: {
        company: "公司名称",
        contact: "联系人",
        email: "邮箱或手机号",
        focus: "最想先落地的场景",
        note: "补充说明",
        submit: "提交演示需求",
        submitted: "已记录你的需求。下一步可以把真实渠道和 CRM 现状补给销售同事。",
      },
      focusOptions: ["获客触达", "销售跟进", "售后服务", "完整闭环", "还不确定"],
    },
    footer: {
      tagline: "AI 客户运营套件，连接获客、跟进、售后、智能体治理与 CRM 数据闭环。",
      links: [
        { href: "https://autoreachai.ai/", label: "AutoReachAI" },
        { href: "https://followupai.ai/", label: "FollowUpAI" },
        { href: "/autoservice/cn", label: "AutoService" },
        { href: "/suite/global", label: "Global site" },
      ],
    },
  },
  global: {
    site: "global",
    htmlLang: "en",
    seo: {
      title: "SalesMost AI Suite | AI Employees for the Full Customer Lifecycle",
      description:
        "SalesMost AI Suite connects AutoReachAI, FollowUpAI, AutoService, CiCi Agent Platform, and CloudCC CRM into a governed AI customer operations loop for outreach, sales follow-up, and after-sales service.",
      ogTitle: "SalesMost AI Suite, AI employees for the full customer lifecycle",
      ogDescription: "A governed AI customer operations loop for global outreach, follow-up, service, and CRM-connected agent governance.",
    },
    header: {
      marketLabel: "Market edition",
      marketHref: "/suite/cn",
      marketName: "China",
      nav: [
        { href: "#lifecycle", label: "Lifecycle" },
        { href: "#products", label: "Products" },
        { href: "#platform", label: "Platform" },
        { href: "#market", label: "Global GTM" },
      ],
      cta: "Request demo",
    },
    hero: {
      kicker: "SalesMost AI Suite",
      title: "AI employees for the full customer lifecycle.",
      lead:
        "Find the right prospects with AutoReachAI, follow up across WhatsApp and email with FollowUpAI, resolve after-sales requests with AutoService, and govern every agent on CiCi with CRM data from CloudCC.",
      primaryCta: "Request a lifecycle demo",
      secondaryCta: "Explore the products",
      proof: ["Global outbound", "WhatsApp and email follow-up", "Salesforce and CloudCC CRM", "After-sales automation", "Agent governance"],
      visualTitle: "Global customer lifecycle loop",
      visualCaption: "Three AI employees run the customer journey while CiCi governs the agents and CRM keeps the source of truth.",
      nodes: ["Prospect", "Engage", "Pipeline", "Resolve", "Govern"],
    },
    lifecycle: {
      kicker: "Lifecycle system",
      title: "One governed loop from first touch to service resolution.",
      intro: "Global teams care about speed, channel coverage, CRM hygiene, and trustworthy automation. The international site leads with cross-border growth and enterprise control.",
      stages: [
        {
          key: "reach",
          label: "Attract",
          product: "AutoReachAI",
          title: "Turn your market narrative into qualified outbound.",
          copy: "AutoReachAI studies your company, market, and ICP, then discovers prospects and writes personalized sequences for global outbound campaigns.",
          inputs: ["Website", "ICP", "Target regions"],
          actions: ["Profile business", "Find prospects", "Write sequences"],
          output: "Leads, source, campaign history, and intent score sync to CRM.",
        },
        {
          key: "follow",
          label: "Convert",
          product: "FollowUpAI",
          title: "Keep every lead warm until a human should step in.",
          copy: "FollowUpAI responds and follows up across WhatsApp, email, and CRM tasks, keeping activity clean while sales teams focus on live opportunities.",
          inputs: ["Replies", "New leads", "CRM tasks"],
          actions: ["Auto follow-up", "Draft responses", "Book next steps"],
          output: "Activities, next steps, meeting signals, and opportunity notes update CRM.",
        },
        {
          key: "service",
          label: "Serve",
          product: "AutoService",
          title: "Resolve customer requests across service channels.",
          copy: "AutoService answers questions, retrieves customer context, follows after-sales policies, and prepares concise handoff notes when a human team should take over.",
          inputs: ["Tickets", "Knowledge", "Order data"],
          actions: ["Answer", "Retrieve", "Escalate"],
          output: "Cases, service notes, reason codes, and customer context return to CRM.",
        },
      ],
    },
    matrix: {
      kicker: "Application matrix",
      title: "Three AI employees, each built for a commercial moment.",
      intro: "The suite can start with one focused workflow and expand into a lifecycle operating model without replacing your CRM.",
      products: [
        {
          name: "AutoReachAI",
          role: "AI outbound employee",
          summary: "Automated lead generation and personalized outreach for global expansion.",
          startsWith: "Market, website, ICP, territories",
          produces: "Prospects, sequences, intent signals",
          bestFor: ["B2B SaaS", "Agencies", "Export teams"],
          href: "https://autoreachai.ai/",
          tone: "growth",
        },
        {
          name: "FollowUpAI",
          role: "AI sales follow-up employee",
          summary: "24/7 follow-up across WhatsApp, email, and CRM activity.",
          startsWith: "Replies, leads, CRM tasks",
          produces: "Meetings, next steps, clean activity",
          bestFor: ["Sales teams", "RevOps", "High-volume inbound"],
          href: "https://followupai.ai/",
          tone: "sales",
        },
        {
          name: "AutoService",
          role: "AI after-sales service employee",
          summary: "Customer service agents that connect knowledge, CRM context, and business systems.",
          startsWith: "Customer questions, help center, CRM records",
          produces: "Resolved requests, handoff summaries, service notes",
          bestFor: ["Support teams", "Customer success", "Operations"],
          href: "/autoservice/global",
          tone: "service",
        },
      ],
    },
    platform: {
      kicker: "CiCi platform layer",
      title: "A governed runtime underneath every AI employee.",
      intro: "Global deployments need more than a model prompt. They need permissions, evaluation, observability, version control, and a clear boundary between AI actions and human approval.",
      pillars: [
        {
          title: "Runtime",
          copy: "Agents, knowledge, tools, workflows, and channels run from one operational layer.",
          items: ["Agent runtime", "RAG knowledge", "MCP / API tools", "Workflow orchestration"],
        },
        {
          title: "Governance",
          copy: "Control run-as users, release flow, audit logs, and escalation policy before AI reaches production.",
          items: ["Permission model", "Release control", "Rollback", "Audit trail"],
        },
        {
          title: "Quality",
          copy: "Test agents before launch and watch real traces after launch, from answer quality to tool failures.",
          items: ["Evaluation sets", "Trace", "Regression", "Work credits"],
        },
      ],
    },
    crm: {
      kicker: "CRM-connected loop",
      title: "Your CRM stays the source of truth.",
      intro: "CloudCC CRM anchors the suite, while connectors can extend to Salesforce, HubSpot, Zendesk, Intercom, ServiceNow, and custom systems.",
      records: [
        { label: "Lead", copy: "AutoReachAI creates prospect records, campaigns, and intent signals." },
        { label: "Activity", copy: "FollowUpAI records replies, next steps, and sales motion." },
        { label: "Case", copy: "AutoService writes service context, resolution notes, and handoff reasons." },
        { label: "Insight", copy: "CiCi uses traces and outcomes to improve agents, policies, and knowledge." },
      ],
    },
    market: {
      kicker: "Global go-to-market",
      title: "Built for cross-border growth teams and CRM-centered operations.",
      intro: "The global site should speak to teams selling across markets, using WhatsApp and email, and coordinating CRM workflows across sales and service.",
      lanes: [
        {
          title: "Outbound across regions",
          copy: "AutoReachAI helps teams turn ICP and market positioning into prospect lists and personalized outreach for international expansion.",
          tags: ["North America", "Japan", "EMEA", "APAC"],
        },
        {
          title: "Conversational follow-up",
          copy: "FollowUpAI keeps replies moving across WhatsApp, email, and CRM tasks without losing context between sales reps and AI agents.",
          tags: ["WhatsApp", "Email", "HubSpot", "Salesforce"],
        },
        {
          title: "Service without migration",
          copy: "AutoService works above CRM, helpdesk, commerce, and custom APIs so teams can automate resolution without rebuilding their support stack.",
          tags: ["Zendesk", "Intercom", "ServiceNow", "Custom API"],
        },
      ],
    },
    rollout: {
      kicker: "Deployment path",
      title: "Start narrow. Prove value. Expand the loop.",
      steps: [
        { title: "Choose one workflow", copy: "Pick outbound, follow-up, or after-sales based on the fastest measurable ROI." },
        { title: "Connect systems", copy: "Connect CRM, channels, knowledge, and the read-only business data the agent needs." },
        { title: "Define controls", copy: "Set prompts, policy boundaries, escalation rules, and evaluation cases." },
        { title: "Launch with traces", copy: "Watch real interactions, tool calls, latency, failure reasons, and human handoffs." },
        { title: "Expand lifecycle", copy: "Add the next AI employee while reusing the same runtime and CRM truth." },
      ],
    },
    cta: {
      kicker: "Lifecycle demo",
      title: "See the loop from prospecting to after-sales.",
      intro: "Tell us your first market, CRM stack, and priority workflow. We will shape a lifecycle demo around your team.",
      fields: {
        company: "Company",
        contact: "Contact",
        email: "Work email",
        focus: "First workflow",
        note: "Context",
        submit: "Request demo",
        submitted: "Your demo request is noted. Add your CRM stack and first market before the sales call for a sharper walkthrough.",
      },
      focusOptions: ["Outbound", "Sales follow-up", "After-sales service", "Full lifecycle", "Not sure yet"],
    },
    footer: {
      tagline: "AI customer operations suite for outreach, follow-up, service, agent governance, and CRM-connected truth.",
      links: [
        { href: "https://autoreachai.ai/", label: "AutoReachAI" },
        { href: "https://followupai.ai/", label: "FollowUpAI" },
        { href: "/autoservice/global", label: "AutoService" },
        { href: "/suite/cn", label: "Lifecycle loop" },
      ],
    },
  },
};

type SuiteLandingProps = {
  siteOverride?: SuiteSite;
};

const toneLabels: Record<string, string> = {
  growth: "01",
  sales: "02",
  service: "03",
};

export default function SuiteLanding({ siteOverride }: SuiteLandingProps) {
  const location = useLocation();
  const site: SuiteSite = siteOverride ?? (location.pathname.endsWith("/global") ? "global" : "china");
  const copy = SUITE_COPY[site];
  const [menuOpen, setMenuOpen] = useState(false);
  const [activeStage, setActiveStage] = useState(0);
  const [submitted, setSubmitted] = useState(false);
  const active = copy.lifecycle.stages[activeStage];

  useEffect(() => {
    document.title = copy.seo.title;
    upsertMeta("description", copy.seo.description);
    upsertMeta("og:title", copy.seo.ogTitle, "property");
    upsertMeta("og:description", copy.seo.ogDescription, "property");
  }, [copy]);

  useEffect(() => {
    setMenuOpen(false);
    setActiveStage(0);
    setSubmitted(false);
  }, [site]);

  const heroNodes = useMemo(() => copy.hero.nodes, [copy.hero.nodes]);

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitted(true);
  };

  return (
    <main className="suite-site" data-suite-site={site} lang={copy.htmlLang}>
      <header className="suite-header" aria-label="SalesMost AI Suite">
        <a className="suite-brand" href={site === "global" ? "/suite/global" : "/suite/cn"} aria-label="SalesMost AI Suite home">
          <span className="suite-brand__mark">SM</span>
          <span>
            <strong>SalesMost AI</strong>
            <small>{site === "global" ? "Customer Operations Suite" : "企业客户运营套件"}</small>
          </span>
        </a>
        <nav className="suite-nav" aria-label="Primary navigation">
          {copy.header.nav.map((item) => (
            <a key={item.href} href={item.href}>
              {item.label}
            </a>
          ))}
        </nav>
        <div className="suite-header__actions">
          <a className="suite-market-link" href={copy.header.marketHref} aria-label={copy.header.marketLabel ? `${copy.header.marketLabel}: ${copy.header.marketName}` : copy.header.marketName}>
            {copy.header.marketLabel ? <span>{copy.header.marketLabel}</span> : null}
            <strong>{copy.header.marketName}</strong>
          </a>
          <a className="suite-button suite-button--primary" href="#demo">
            {copy.header.cta}
          </a>
        </div>
        <button className="suite-menu" type="button" aria-label="Toggle navigation" aria-expanded={menuOpen} onClick={() => setMenuOpen((value) => !value)}>
          <span />
          <span />
        </button>
      </header>

      <div className={`suite-mobile-menu${menuOpen ? " is-open" : ""}`}>
        {copy.header.nav.map((item) => (
          <a key={item.href} href={item.href} onClick={() => setMenuOpen(false)}>
            {item.label}
          </a>
        ))}
        <a href={copy.header.marketHref} onClick={() => setMenuOpen(false)}>
          {copy.header.marketLabel ? `${copy.header.marketLabel}: ${copy.header.marketName}` : copy.header.marketName}
        </a>
        <a href="#demo" onClick={() => setMenuOpen(false)}>
          {copy.header.cta}
        </a>
      </div>

      <section className="suite-hero" id="top">
        <div className="suite-hero__copy">
          <p className="suite-kicker">{copy.hero.kicker}</p>
          <h1 className={site === "china" ? "suite-hero-title suite-hero-title--cn" : "suite-hero-title"}>
            {site === "china" ? (
              <>
                <span>让 AI 员工</span>
                <span>跑完整个</span>
                <span>客户生命周期。</span>
              </>
            ) : (
              copy.hero.title
            )}
          </h1>
          <p className="suite-hero__lead">{copy.hero.lead}</p>
          <div className="suite-hero__actions">
            <a className="suite-button suite-button--primary" href="#demo">
              {copy.hero.primaryCta}
            </a>
            <a className="suite-button suite-button--secondary" href="#products">
              {copy.hero.secondaryCta}
            </a>
          </div>
          <div className="suite-proof" aria-label="Suite proof points">
            {copy.hero.proof.map((item) => (
              <span key={item}>{item}</span>
            ))}
          </div>
        </div>
        <HeroShowcase site={site} title={copy.hero.visualTitle} caption={copy.hero.visualCaption} nodes={heroNodes} />
      </section>

      <section id="lifecycle" className="suite-section suite-lifecycle">
        <div className="suite-section__head suite-section__head--split">
          <div>
            <p className="suite-kicker">{copy.lifecycle.kicker}</p>
            <h2>{copy.lifecycle.title}</h2>
          </div>
          <p>{copy.lifecycle.intro}</p>
        </div>
        <div className="suite-lifecycle__board">
          <div className="suite-lifecycle__tabs" role="tablist" aria-label={copy.lifecycle.title}>
            {copy.lifecycle.stages.map((stage, index) => (
              <button
                className={`suite-stage-tab${activeStage === index ? " is-active" : ""}`}
                key={stage.key}
                type="button"
                role="tab"
                aria-selected={activeStage === index}
                onClick={() => setActiveStage(index)}
              >
                <span>{stage.label}</span>
                <strong>{stage.product}</strong>
              </button>
            ))}
          </div>
          <article className="suite-stage-detail" role="tabpanel">
            <div>
              <span className="suite-stage-detail__eyebrow">{active.product}</span>
              <h3>{active.title}</h3>
              <p>{active.copy}</p>
            </div>
            <div className="suite-stage-detail__grid">
              <ListBlock title={site === "global" ? "Inputs" : "输入"} items={active.inputs} />
              <ListBlock title={site === "global" ? "Agent actions" : "智能体动作"} items={active.actions} />
              <div className="suite-output">
                <span>{site === "global" ? "CRM output" : "回写 CRM"}</span>
                <strong>{active.output}</strong>
              </div>
            </div>
          </article>
        </div>
      </section>

      <section id="products" className="suite-section suite-products">
        <div className="suite-section__head">
          <p className="suite-kicker">{copy.matrix.kicker}</p>
          <h2>{copy.matrix.title}</h2>
          <p>{copy.matrix.intro}</p>
        </div>
        <div className="suite-product-matrix">
          {copy.matrix.products.map((product) => (
            <article className={`suite-product suite-product--${product.tone}`} key={product.name}>
              <div className="suite-product__topline">
                <div className={`suite-product__avatar suite-product__avatar--${product.tone}`} aria-hidden="true">
                  <span />
                </div>
                <div className="suite-product__index">{toneLabels[product.tone]}</div>
              </div>
              <div>
                <p>{product.role}</p>
                <h3>{product.name}</h3>
                <span>{product.summary}</span>
              </div>
              <dl>
                <div>
                  <dt>{site === "global" ? "Starts with" : "输入起点"}</dt>
                  <dd>{product.startsWith}</dd>
                </div>
                <div>
                  <dt>{site === "global" ? "Produces" : "输出结果"}</dt>
                  <dd>{product.produces}</dd>
                </div>
              </dl>
              <div className="suite-product__tags">
                {product.bestFor.map((item) => (
                  <span key={item}>{item}</span>
                ))}
              </div>
              <a href={product.href}>{site === "global" ? `Visit ${product.name}` : `进入 ${product.name}`}</a>
            </article>
          ))}
        </div>
      </section>

      <section id="platform" className="suite-section suite-platform">
        <div className="suite-section__head suite-section__head--split">
          <div>
            <p className="suite-kicker">{copy.platform.kicker}</p>
            <h2>{copy.platform.title}</h2>
          </div>
          <p>{copy.platform.intro}</p>
        </div>
        <div className="suite-platform__console">
          <div className="suite-console__top">
            <span>CiCi Agent Platform</span>
            <span>{site === "global" ? "Runtime healthy" : "运行状态正常"}</span>
          </div>
          <div className="suite-console__grid">
            {copy.platform.pillars.map((pillar) => (
              <article key={pillar.title}>
                <h3>{pillar.title}</h3>
                <p>{pillar.copy}</p>
                <ul>
                  {pillar.items.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section id="crm" className="suite-section suite-crm">
        <div className="suite-section__head">
          <p className="suite-kicker">{copy.crm.kicker}</p>
          <h2>{copy.crm.title}</h2>
          <p>{copy.crm.intro}</p>
        </div>
        <div className="suite-crm-loop" aria-label={copy.crm.title}>
          {copy.crm.records.map((record, index) => (
            <article key={record.label}>
              <span>{String(index + 1).padStart(2, "0")}</span>
              <strong>{record.label}</strong>
              <p>{record.copy}</p>
            </article>
          ))}
        </div>
      </section>

      <section id="market" className="suite-section suite-market">
        <div className="suite-section__head suite-section__head--split">
          <div>
            <p className="suite-kicker">{copy.market.kicker}</p>
            <h2>{copy.market.title}</h2>
          </div>
          <p>{copy.market.intro}</p>
        </div>
        <div className="suite-market__lanes">
          {copy.market.lanes.map((lane) => (
            <article key={lane.title}>
              <h3>{lane.title}</h3>
              <p>{lane.copy}</p>
              <div>
                {lane.tags.map((tag) => (
                  <span key={tag}>{tag}</span>
                ))}
              </div>
            </article>
          ))}
        </div>
      </section>

      <section className="suite-section suite-rollout">
        <div className="suite-section__head">
          <p className="suite-kicker">{copy.rollout.kicker}</p>
          <h2>{copy.rollout.title}</h2>
        </div>
        <div className="suite-rollout__steps">
          {copy.rollout.steps.map((step, index) => (
            <article key={step.title}>
              <span>{String(index + 1).padStart(2, "0")}</span>
              <h3>{step.title}</h3>
              <p>{step.copy}</p>
            </article>
          ))}
        </div>
      </section>

      <section id="demo" className="suite-section suite-demo">
        <div className="suite-demo__copy">
          <p className="suite-kicker">{copy.cta.kicker}</p>
          <h2>{copy.cta.title}</h2>
          <p>{copy.cta.intro}</p>
        </div>
        <form className="suite-demo__form" onSubmit={handleSubmit}>
          <label>
            <span>{copy.cta.fields.company}</span>
            <input name="company" autoComplete="organization" required />
          </label>
          <label>
            <span>{copy.cta.fields.contact}</span>
            <input name="contact" autoComplete="name" required />
          </label>
          <label>
            <span>{copy.cta.fields.email}</span>
            <input name="email" autoComplete="email" required />
          </label>
          <label>
            <span>{copy.cta.fields.focus}</span>
            <select name="focus" required defaultValue="">
              <option value="" disabled>
                {copy.cta.fields.focus}
              </option>
              {copy.cta.focusOptions.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </label>
          <label className="suite-demo__wide">
            <span>{copy.cta.fields.note}</span>
            <textarea name="note" rows={4} />
          </label>
          <button className="suite-button suite-button--primary" type="submit">
            {copy.cta.fields.submit}
          </button>
          {submitted ? <p className="suite-demo__notice">{copy.cta.fields.submitted}</p> : null}
        </form>
      </section>

      <footer className="suite-footer">
        <a className="suite-brand" href="#top" aria-label="SalesMost AI Suite">
          <span className="suite-brand__mark">SM</span>
          <span>
            <strong>SalesMost AI</strong>
            <small>{site === "global" ? "Lifecycle AI employees" : "客户生命周期 AI 员工"}</small>
          </span>
        </a>
        <p>{copy.footer.tagline}</p>
        <div>
          {copy.footer.links.map((link) => (
            <a key={link.href} href={link.href}>
              {link.label}
            </a>
          ))}
        </div>
      </footer>
    </main>
  );
}

function HeroShowcase({ site, title, caption, nodes }: { site: SuiteSite; title: string; caption: string; nodes: string[] }) {
  const prompt = site === "global"
    ? "Find expansion accounts, follow up on replies, and prep service handoff notes before Monday pipeline review."
    : "从线索触达开始，持续跟进客户回复，并在售后问题升级前整理好 CRM 上下文。";
  const status = site === "global" ? "Building lifecycle agents..." : "正在编排客户运营智能体...";
  const steps = site === "global"
    ? ["Choosing the right AI employee", "Connecting CRM context", "Writing the first workflow"]
    : ["选择合适的 AI 员工", "连接 CRM 客户事实", "生成第一条业务流程"];

  return (
    <aside className="suite-hero-showcase" aria-label={title}>
      <div className="suite-prompt-card">
        <span>{site === "global" ? "Prompt to lifecycle" : "一句话到业务闭环"}</span>
        <strong>{prompt}</strong>
      </div>
      <div className="suite-agent-stack" aria-label={title}>
        {nodes.slice(0, 3).map((node, index) => (
          <article className="suite-agent-card" key={node}>
            <div className={`suite-agent-avatar suite-agent-avatar--${index}`} aria-hidden="true">
              <span />
            </div>
            <div>
              <span>{String(index + 1).padStart(2, "0")}</span>
              <strong>{node}</strong>
            </div>
          </article>
        ))}
      </div>
      <div className="suite-build-card">
        <div className="suite-build-card__head">
          <span>{status}</span>
          <strong>{site === "global" ? "Done in minutes" : "几分钟内完成"}</strong>
        </div>
        <ul>
          {steps.map((step) => (
            <li key={step}>{step}</li>
          ))}
        </ul>
      </div>
      <div className="suite-showcase-footer">
        <strong>{title}</strong>
        <p>{caption}</p>
      </div>
    </aside>
  );
}

function ListBlock({ title, items }: { title: string; items: string[] }) {
  return (
    <div className="suite-list-block">
      <span>{title}</span>
      <ul>
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </div>
  );
}

function upsertMeta(name: string, content: string, attr: "name" | "property" = "name") {
  let el = document.head.querySelector<HTMLMetaElement>(`meta[${attr}="${name}"]`);
  if (!el) {
    el = document.createElement("meta");
    el.setAttribute(attr, name);
    document.head.appendChild(el);
  }
  el.content = content;
}
