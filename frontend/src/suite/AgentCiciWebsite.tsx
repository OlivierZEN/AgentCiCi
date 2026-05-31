import { type FormEvent, useEffect, useMemo, useState } from "react";
import { useLocation } from "react-router-dom";
import "./agentcici-website.css";

type Locale = "zh" | "en";
type PageKey = "solutions" | "skillshub" | "pricing" | "docs" | "community";

type NavItem = {
  key: PageKey;
  label: string;
  href: string;
};

type Solution = {
  name: string;
  label: string;
  summary: string;
  trigger: string;
  connects: string;
  output: string;
  proof: string[];
};

type PricingPlan = {
  name: string;
  badge: string;
  price: string;
  period: string;
  description: string;
  credits: string;
  knowledgeStorage: string;
  builderSeats: string;
  teamAccess: string;
  concurrency: string;
  cta: string;
  highlighted?: boolean;
  features: string[];
};

type SiteCopy = {
  locale: Locale;
  nav: NavItem[];
  brandLine: string;
  languageLabel: string;
  languageHref: string;
  demoCta: string;
  seo: Record<PageKey, { title: string; description: string }>;
  shared: {
    homeHref: string;
    eyebrow: string;
    loginLabel: string;
    loginHref: string;
    adminLoginLabel: string;
    adminLoginHref: string;
    platformLoginLabel: string;
    platformLoginHref: string;
    footerLine: string;
    demoTitle: string;
    demoIntro: string;
    demoFields: {
      company: string;
      contact: string;
      email: string;
      focus: string;
      note: string;
      submit: string;
      submitted: string;
    };
  };
  solutions: {
    heroTitle: string;
    heroLead: string;
    heroPoints: string[];
    visualTitle: string;
    visualNodes: string[];
    sectionTitle: string;
    sectionIntro: string;
    items: Solution[];
    runtimeTitle: string;
    runtimeIntro: string;
    runtimeItems: Array<{ title: string; copy: string }>;
    beforeAfter: {
      title: string;
      before: string[];
      after: string[];
    };
  };
  skillshub: {
    title: string;
    lead: string;
    lanes: Array<{ title: string; copy: string; tags: string[] }>;
    workflow: Array<{ title: string; copy: string }>;
  };
  pricing: {
    title: string;
    lead: string;
    creditNote: string;
    plans: PricingPlan[];
    addOns: Array<{ title: string; copy: string }>;
  };
  docs: {
    title: string;
    lead: string;
    sections: Array<{ title: string; copy: string; links: string[] }>;
  };
  community: {
    title: string;
    lead: string;
    status: string;
    areas: Array<{ title: string; copy: string }>;
  };
};

const COPY: Record<Locale, SiteCopy> = {
  zh: {
    locale: "zh",
    brandLine: "企业级智能体平台",
    languageLabel: "English",
    languageHref: "/global",
    demoCta: "预约演示",
    nav: [
      { key: "solutions", label: "Solutions", href: "/solutions" },
      { key: "skillshub", label: "SkillsHub", href: "/skill-hub" },
      { key: "pricing", label: "Pricing", href: "/pricing" },
      { key: "docs", label: "Docs", href: "/docs" },
      { key: "community", label: "Community", href: "/community" },
    ],
    seo: {
      solutions: {
        title: "AgentCiCi | 企业级智能体平台",
        description: "AgentCiCi 连接业务系统、知识库、渠道和智能体治理，内置 AutoService、AI 听记、客户洞察等企业智能体能力。",
      },
      skillshub: {
        title: "SkillsHub | AgentCiCi 技能交换市场",
        description: "SkillsHub 支持 AgentCiCi 技能包导入、导出、审核、版本、回滚和企业内部分发。",
      },
      pricing: {
        title: "Pricing | AgentCiCi 计价与报价模式",
        description: "AgentCiCi 提供标准版、专业版和企业版报价，包含初始化 Credits、知识库容量、文档处理量、构建席位、并发运行、治理功能和上线服务。",
      },
      docs: {
        title: "Docs | AgentCiCi 用户指南",
        description: "AgentCiCi Docs 提供快速开始、管理员配置、Solutions、SkillsHub、Open API 和私有化部署指南。",
      },
      community: {
        title: "Community | AgentCiCi 智能体社区",
        description: "AgentCiCi Community 将面向智能体模板、技能贡献、行业场景和企业智能体实践开放。",
      },
    },
    shared: {
      homeHref: "/",
      eyebrow: "AgentCiCi Platform",
      loginLabel: "登录",
      loginHref: "/login",
      adminLoginLabel: "管理后台",
      adminLoginHref: "/admin/login",
      platformLoginLabel: "运营平台",
      platformLoginHref: "/platform/login",
      footerLine: "AgentCiCi 将企业业务系统、知识、渠道和智能体运行治理连接成可执行闭环。",
      demoTitle: "预约一次企业智能体平台演示。",
      demoIntro: "告诉我们你的业务系统、首个场景和部署偏好，我们会按 Solutions、SkillsHub、Pricing 和治理链路准备演示。",
      demoFields: {
        company: "公司名称",
        contact: "联系人",
        email: "邮箱或手机号",
        focus: "重点场景",
        note: "补充说明",
        submit: "提交演示需求",
        submitted: "已记录你的演示需求。下一步可以补充业务系统、知识库和部署模式。",
      },
    },
    solutions: {
      heroTitle: "让企业智能体接住真实业务，不只停在聊天窗口。",
      heroLead:
        "AgentCiCi 是企业级智能体平台，连接 CRM、工单、知识库、会议、Open API 和协作渠道，让智能体能执行、能追踪、能治理、能复用。",
      heroPoints: ["业务系统集成", "权限与审计", "智能体评测", "技能复用", "工作量计量"],
      visualTitle: "从客户问题到业务处理结果",
      visualNodes: ["客户请求", "业务知识", "智能处理", "人工确认", "服务回写"],
      sectionTitle: "从售后、会议到客户经营，先解决高频业务。",
      sectionIntro: "每个方案都不是孤立页面，而是一组可配置的智能体、技能、知识、工具和治理策略。",
      items: [
        {
          name: "AutoService",
          label: "售后智能体",
          summary: "自动回答售后问题，查询订单、物流、工单和保修，复杂事项带上下文转人工。",
          trigger: "客户从企业微信微信客服、门户或 Open API 发起问题。",
          connects: "售后知识库、CRM、订单、物流、工单、保修和人工客服。",
          output: "服务答复、只读查询结果、接管摘要和服务记录。",
          proof: ["知识问答", "业务只读查询", "人工接管摘要", "服务记录回写"],
        },
        {
          name: "AI 听记",
          label: "会议与音频智能体",
          summary: "把会议、录音和客户沟通转成逐字稿、摘要、待办、章节和可沉淀知识。",
          trigger: "实时会议、上传音频或业务沟通录音进入转写流程。",
          connects: "本地 ASR、会议记录、知识库、任务、CRM 和文档。",
          output: "转写文本、会议纪要、行动项、章节、客户事实和知识沉淀。",
          proof: ["实时转写", "上传音频", "自动纪要", "待办提取"],
        },
        {
          name: "客户洞察",
          label: "客户经营智能体",
          summary: "整合客户记录、沟通、工单、订单和智能体运行轨迹，生成风险、机会和下一步建议。",
          trigger: "销售、客服或管理者需要快速理解客户现状。",
          connects: "CRM、活动、工单、订单、服务历史、知识库和处理记录。",
          output: "客户画像、风险信号、机会摘要、关系图谱和推荐动作。",
          proof: ["客户画像", "风险识别", "机会摘要", "下一步建议"],
        },
      ],
      runtimeTitle: "让智能体稳定进入企业流程。",
      runtimeIntro: "统一管理权限、记录、发布、审核和用量，让每次自动处理都有边界、能复盘、可交接。",
      runtimeItems: [
        { title: "边界清楚", copy: "按组织、角色、渠道和关键动作设置使用范围。" },
        { title: "过程留痕", copy: "记录知识来源、处理过程、人工接管和失败原因。" },
        { title: "可复用技能", copy: "技能包可导入、导出、审核、版本化，并在企业内部分发。" },
        { title: "用量清晰", copy: "看清自动处理量、人工接管量、预算和报价依据。" },
      ],
      beforeAfter: {
        title: "从分散人工流程，到可持续自动处理。",
        before: ["人工翻找知识和客户记录", "客服、销售、会议纪要分散在不同工具", "问题升级时缺少上下文", "上线后难以审计质量和成本"],
        after: ["智能体读取授权数据并处理", "知识、技能和业务系统统一协作", "人工接管带完整摘要", "质量、审计、用量和发布统一管理"],
      },
    },
    skillshub: {
      title: "SkillsHub 让企业智能体能力可以交换、审核和复用。",
      lead:
        "把一次项目交付沉淀成可导入、可导出、可版本化的技能包。企业可以建立内部技能库，也可以为未来公共市场准备标准包。",
      lanes: [
        { title: "导入与导出", copy: "把 Skill、工具声明、参数说明、示例和权限边界打包迁移。", tags: ["Skill Package", "Import", "Export"] },
        { title: "审核与版本", copy: "上线前审核技能能力、依赖、风险动作和变更记录，必要时回滚。", tags: ["Review", "Version", "Rollback"] },
        { title: "企业内部分发", copy: "平台运营可把标准技能分发给组织，组织管理员再绑定到 Agent。", tags: ["Private Hub", "Governance", "Agent Binding"] },
      ],
      workflow: [
        { title: "打包", copy: "从项目经验沉淀出技能、描述、输入输出、示例和风险边界。" },
        { title: "审核", copy: "平台侧检查权限、依赖、动作风险和版本说明。" },
        { title: "分发", copy: "进入企业技能库，按组织、场景或 Agent 授权使用。" },
        { title: "复用", copy: "被新的智能体、自动流程、开放接口和业务场景复用。" },
      ],
    },
    pricing: {
      title: "选择适合当前阶段的 AgentCiCi 版本。",
      lead:
        "所有版本都包含平台运行、基础治理、按月发放的 Credits 和知识库存储。对话、检索、文档解析、OCR、向量化、转写、摘要、工具调用和洞察任务统一折算为 Credits。",
      creditNote: "Credits 按自然月发放，当月有效，超额后购买 Credits 包。知识库容量包含原文、向量索引、元数据和日志保留，容量超出后购买容量包；文档处理不再单独作为处理包计费。",
      plans: [
        {
          name: "标准版",
          badge: "起步版本",
          price: "¥1,999",
          period: "/ 月",
          description: "适合售后或会议场景的首个智能体上线。",
          credits: "8,000 Credits / 月",
          knowledgeStorage: "5 GB 知识库容量",
          builderSeats: "1 个构建席位",
          teamAccess: "最多 20 个团队成员",
          concurrency: "2 路并发智能体运行",
          cta: "预约演示",
          features: [
            "3 个生产智能体，含 AutoService 基础能力",
            "知识库问答与基础文档导入",
            "AI 听记轻量使用，支持上传音频生成摘要",
            "SkillsHub 技能导入与企业内复用",
            "7 天运行记录与基础用量看板",
            "标准在线支持",
          ],
        },
        {
          name: "专业版",
          badge: "推荐版本",
          price: "¥6,999",
          period: "/ 月",
          description: "适合客服、销售、运营多部门共同使用。",
          credits: "35,000 Credits / 月",
          knowledgeStorage: "30 GB 知识库容量",
          builderSeats: "2 个构建席位",
          teamAccess: "最多 100 个团队成员",
          concurrency: "10 路并发智能体运行",
          cta: "预约演示",
          highlighted: true,
          features: [
            "10 个生产智能体，覆盖 AutoService、AI 听记、客户洞察",
            "CRM、工单、知识库和协作渠道连接",
            "Open API、Webhook 和基础自动流程",
            "SkillsHub 审核、版本和回滚",
            "30 天运行记录、处理摘要和成本归因",
            "权限分组、操作审计和优先支持",
          ],
        },
        {
          name: "企业版",
          badge: "规模化",
          price: "¥18,800",
          period: "/ 月起",
          description: "适合大型公司、严格治理和大用量场景。",
          credits: "100,000 Credits / 月起",
          knowledgeStorage: "100 GB 起知识库容量",
          builderSeats: "5 个构建席位",
          teamAccess: "最多 500 个团队成员起",
          concurrency: "50 路并发智能体运行起",
          cta: "联系销售",
          features: [
            "50 个生产智能体，可按组织和业务线隔离",
            "高级客户洞察、发布评测和高风险动作确认",
            "SSO、细粒度权限、审计导出和日志保留",
            "专属连接器配额、Open API 并发和回写治理",
            "90 天运行记录、预算预警和用量复盘",
            "专属客户成功、上线陪跑和 SLA",
          ],
        },
        {
          name: "Custom 定制版",
          badge: "超大规模",
          price: "Custom",
          period: "",
          description: "适合超大规模、本地化部署和专属治理场景。",
          credits: "按合同配置",
          knowledgeStorage: "专属容量与本地化存储",
          builderSeats: "按项目配置",
          teamAccess: "按组织规模配置",
          concurrency: "专属并发与资源池",
          cta: "联系销售",
          features: [
            "生产智能体、Credits 和并发按业务规模定制",
            "本地化部署、专属网络、数据驻留和安全审查",
            "专属模型、向量库、连接器和业务系统集成",
            "集团级权限、审计、发布治理和合规报表",
            "专属上线项目组、迁移计划和验收流程",
            "企业级 SLA、专属支持和年度成功复盘",
          ],
        },
      ],
      addOns: [
        { title: "Credits 包", copy: "超出套餐后按 ¥999 / 10,000 Credits 起购，覆盖对话、检索、文档处理、OCR、转写、摘要、工具调用和洞察任务。" },
        { title: "知识库容量包", copy: "用于原文存储、向量索引、元数据、日志和备份保留，扩容从 ¥299 / 100 GB / 月起。" },
        { title: "并发与构建扩展", copy: "可增加并发运行数、构建席位和团队成员上限，适合更多业务团队参与配置和运营。" },
        { title: "上线服务", copy: "包含场景梳理、知识库初始化、连接器配置、技能整理、培训和验收支持。" },
      ],
    },
    docs: {
      title: "Docs 是企业用户从试点到治理的操作入口。",
      lead: "先帮助用户完成第一个智能体场景，再逐步进入管理员配置、技能复用、Open API 和私有化部署。",
      sections: [
        { title: "快速开始", copy: "创建组织、选择 Solution、接入知识库并运行第一个 Agent。", links: ["创建第一个 Agent", "连接知识库", "邀请团队成员"] },
        { title: "管理员配置", copy: "模型、工具、用户、权限、渠道、审计和组织策略。", links: ["模型配置", "工具治理", "权限与审计"] },
        { title: "SkillsHub", copy: "导入、导出、审核、版本化和分发企业技能包。", links: ["技能包结构", "导入导出", "审核与回滚"] },
        { title: "Open API 与集成", copy: "把 AgentCiCi 接入企业门户、CRM、工单和自有系统。", links: ["API Key", "Chat Messages", "Webhook"] },
        { title: "私有化部署", copy: "部署模式、模型资源责任、日志保留、备份和升级治理。", links: ["部署准备", "本地模型", "升级策略"] },
      ],
    },
    community: {
      title: "Community 将成为企业智能体实践的交流场。",
      lead: "这里会承载智能体模板、行业场景、技能贡献、实施经验和管理员讨论。",
      status: "即将开放",
      areas: [
        { title: "智能体模板", copy: "围绕售后、会议、客户经营、工单和 Open API 场景沉淀模板。" },
        { title: "技能贡献", copy: "开发者和实施团队可以分享可复用技能包和最佳实践。" },
        { title: "行业讨论", copy: "按制造、软件、服务、出海和集团治理组织案例。" },
        { title: "治理交流", copy: "管理员讨论评测、审计、发布、权限和成本治理方法。" },
      ],
    },
  },
  en: {
    locale: "en",
    brandLine: "Enterprise Agent Platform",
    languageLabel: "中文",
    languageHref: "/",
    demoCta: "Book demo",
    nav: [
      { key: "solutions", label: "Solutions", href: "/global/solutions" },
      { key: "skillshub", label: "SkillsHub", href: "/global/skill-hub" },
      { key: "pricing", label: "Pricing", href: "/global/pricing" },
      { key: "docs", label: "Docs", href: "/global/docs" },
      { key: "community", label: "Community", href: "/global/community" },
    ],
    seo: {
      solutions: {
        title: "AgentCiCi | Enterprise Agent Platform",
        description: "AgentCiCi connects business systems, knowledge, channels, and governed agent runtime for enterprise workflows.",
      },
      skillshub: {
        title: "SkillsHub | AgentCiCi Skill Exchange",
        description: "SkillsHub supports skill package import, export, review, versioning, rollback, and enterprise distribution.",
      },
      pricing: {
        title: "Pricing | AgentCiCi Commercial Model",
        description: "AgentCiCi pricing includes Starter, Professional, and Enterprise plans with initialized Credits, knowledge capacity, document processing, builder seats, concurrency, governance, and launch support.",
      },
      docs: {
        title: "Docs | AgentCiCi User Guides",
        description: "AgentCiCi Docs covers quick start, admin setup, Solutions, SkillsHub, Open API, and private deployment.",
      },
      community: {
        title: "Community | AgentCiCi Agent Community",
        description: "AgentCiCi Community will host agent templates, skill contributions, industry practices, and governance discussions.",
      },
    },
    shared: {
      homeHref: "/global",
      eyebrow: "AgentCiCi Platform",
      loginLabel: "Sign in",
      loginHref: "/login",
      adminLoginLabel: "Admin",
      adminLoginHref: "/admin/login",
      platformLoginLabel: "Platform",
      platformLoginHref: "/platform/login",
      footerLine: "AgentCiCi connects business systems, knowledge, channels, and governed agent runtime into executable loops.",
      demoTitle: "Book an enterprise agent platform demo.",
      demoIntro: "Tell us your systems, first workflow, and deployment preference. We will shape a walkthrough across Solutions, SkillsHub, Pricing, and governance.",
      demoFields: {
        company: "Company",
        contact: "Contact",
        email: "Work email",
        focus: "Priority workflow",
        note: "Context",
        submit: "Submit demo request",
        submitted: "Your request is noted. Add your systems, knowledge sources, and deployment preference for a sharper demo.",
      },
    },
    solutions: {
      heroTitle: "Enterprise agents that work inside real business systems.",
      heroLead:
        "AgentCiCi connects CRM, tickets, knowledge, meetings, Open API, and collaboration channels so agents can execute, trace, govern, and be reused.",
      heroPoints: ["Business integration", "Permission and audit", "Agent evaluation", "Skill reuse", "Workload metering"],
      visualTitle: "From business input to governed output",
      visualNodes: ["Channel request", "Knowledge and data", "Agent execution", "Human approval", "Business writeback"],
      sectionTitle: "Built-in Solutions start with high-frequency enterprise workflows.",
      sectionIntro: "Each Solution is a configurable bundle of agents, skills, knowledge, tools, and governance policies.",
      items: [
        {
          name: "AutoService",
          label: "After-sales agent",
          summary: "Answer service questions, retrieve orders, logistics, tickets, and warranty status, then hand off with context.",
          trigger: "A customer asks through service chat, portal, or Open API.",
          connects: "Service knowledge, CRM, orders, logistics, tickets, warranty, and human support.",
          output: "Answer, read-only result, handoff summary, Case record, and runtime trace.",
          proof: ["Knowledge answers", "Read-only lookup", "Handoff summary", "Case writeback"],
        },
        {
          name: "AI Minutes",
          label: "Meeting and audio agent",
          summary: "Turn meetings, recordings, and customer conversations into transcripts, summaries, tasks, chapters, and reusable knowledge.",
          trigger: "Live meeting, uploaded audio, or business call recording enters the workflow.",
          connects: "Local ASR, meeting records, knowledge, tasks, CRM, and documents.",
          output: "Transcript, meeting notes, action items, chapters, customer facts, and knowledge updates.",
          proof: ["Realtime ASR", "Audio upload", "Meeting notes", "Action items"],
        },
        {
          name: "Customer Insight",
          label: "Customer intelligence agent",
          summary: "Combine CRM records, communications, tickets, orders, and runtime traces into risk, opportunity, and next actions.",
          trigger: "Sales, service, or managers need fast customer context.",
          connects: "CRM, activities, tickets, orders, service history, knowledge, and agent traces.",
          output: "Customer profile, risk signals, opportunity summary, relationship map, and recommended actions.",
          proof: ["Profile", "Risk signals", "Opportunity summary", "Next actions"],
        },
      ],
      runtimeTitle: "Enterprise agents need governed runtime, not one-off generation.",
      runtimeIntro: "The platform manages agents, skills, workflows, tools, Open API, evaluation, releases, audit, and workload metering.",
      runtimeItems: [
        { title: "Controlled execution", copy: "Configure boundaries by organization, role, run-as user, channel, and high-risk action." },
        { title: "Traceable runtime", copy: "Capture traces, tool calls, knowledge hits, human handoff, failure reasons, and cost attribution." },
        { title: "Reusable skills", copy: "Import, export, review, version, and distribute skill packages across the enterprise." },
        { title: "Operational metering", copy: "Use Work Credits to explain workload, budgets, quotas, overage, and pricing." },
      ],
      beforeAfter: {
        title: "From scattered manual work to executable agent loops.",
        before: ["Teams search knowledge and records manually", "Service, sales, and meeting notes live in separate tools", "Escalations lose context", "Quality and cost are hard to audit"],
        after: ["Agents read authorized data and execute", "Knowledge, skills, and business systems are orchestrated", "Human handoff includes summary and trace", "Evaluation, audit, metering, and release governance stay together"],
      },
    },
    skillshub: {
      title: "SkillsHub makes enterprise agent capabilities portable and governed.",
      lead:
        "Turn project delivery into importable, exportable, versioned skill packages. Teams can run a private hub today and prepare for a public marketplace later.",
      lanes: [
        { title: "Import and export", copy: "Move skills, tool declarations, parameter docs, examples, and permission boundaries.", tags: ["Skill Package", "Import", "Export"] },
        { title: "Review and version", copy: "Check capability, dependencies, risky actions, and changelog before release. Roll back when needed.", tags: ["Review", "Version", "Rollback"] },
        { title: "Enterprise distribution", copy: "Platform operators distribute standard skills, then admins bind them to agents.", tags: ["Private Hub", "Governance", "Agent Binding"] },
      ],
      workflow: [
        { title: "Package", copy: "Capture skills, description, input, output, examples, and risk boundaries." },
        { title: "Review", copy: "Check permissions, dependencies, action risk, and version notes." },
        { title: "Distribute", copy: "Publish to a private skill library by organization, scenario, or agent." },
        { title: "Reuse", copy: "Reuse inside new agents, workflows, Open API, and business solutions." },
      ],
    },
    pricing: {
      title: "Choose the AgentCiCi plan for your current stage.",
      lead:
        "Every plan includes platform runtime, baseline governance, monthly Credits, and knowledge storage. Conversations, retrieval, document parsing, OCR, vectorization, transcription, summaries, tool calls, and insight tasks are unified into Credits.",
      creditNote: "Credits are issued monthly and valid for the current month. Buy Credits packs for overage. Knowledge capacity covers original files, vector indexes, metadata, and retention; buy capacity packs only when storage grows beyond the plan. There is no separate document-processing pack.",
      plans: [
        {
          name: "Starter",
          badge: "Entry plan",
          price: "¥1,999",
          period: "/ mo",
          description: "For launching the first service or meeting agent.",
          credits: "8,000 Credits / mo",
          knowledgeStorage: "5 GB knowledge capacity",
          builderSeats: "1 builder seat",
          teamAccess: "Up to 20 team members",
          concurrency: "2 concurrent agent runs",
          cta: "Book demo",
          features: [
            "3 production agents with AutoService basics",
            "Knowledge Q&A and basic document import",
            "Light AI Minutes usage with uploaded audio summaries",
            "SkillsHub import and internal reuse",
            "7-day runtime history and usage dashboard",
            "Standard online support",
          ],
        },
        {
          name: "Professional",
          badge: "Recommended",
          price: "¥6,999",
          period: "/ mo",
          description: "For service, sales, and operations teams running together.",
          credits: "35,000 Credits / mo",
          knowledgeStorage: "30 GB knowledge capacity",
          builderSeats: "2 builder seats",
          teamAccess: "Up to 100 team members",
          concurrency: "10 concurrent agent runs",
          cta: "Book demo",
          highlighted: true,
          features: [
            "10 production agents across AutoService, AI Minutes, and Customer Insight",
            "CRM, ticketing, knowledge, and collaboration connectors",
            "Open API, Webhook, and basic workflows",
            "SkillsHub review, versioning, and rollback",
            "30-day runtime history, summaries, and cost attribution",
            "Permission groups, audit events, and priority support",
          ],
        },
        {
          name: "Enterprise",
          badge: "Scale",
          price: "¥18,800",
          period: "/ mo+",
          description: "For large companies, strict governance, and higher volume.",
          credits: "100,000+ Credits / mo",
          knowledgeStorage: "100 GB+ knowledge capacity",
          builderSeats: "5 builder seats",
          teamAccess: "500+ team members",
          concurrency: "50+ concurrent agent runs",
          cta: "Contact sales",
          features: [
            "50 production agents with org and business-line separation",
            "Advanced customer insight, release evaluation, and high-risk approval",
            "SSO, granular permissions, audit export, and log retention",
            "Dedicated connector quota, Open API concurrency, and writeback governance",
            "90-day runtime history, budget alerts, and usage reviews",
            "Named customer success, launch enablement, and SLA",
          ],
        },
        {
          name: "Custom",
          badge: "Ultra scale",
          price: "Custom",
          period: "",
          description: "For ultra-scale, localized deployment, and dedicated governance.",
          credits: "Contract allocation",
          knowledgeStorage: "Dedicated local capacity",
          builderSeats: "Project allocation",
          teamAccess: "Configured by org scale",
          concurrency: "Dedicated resource pool",
          cta: "Contact sales",
          features: [
            "Production agents, Credits, and concurrency sized to your rollout",
            "Localized deployment, private network, data residency, and security review",
            "Dedicated models, vector stores, connectors, and business integrations",
            "Group-level permissions, audit, release governance, and compliance reports",
            "Dedicated launch team, migration plan, and acceptance process",
            "Enterprise SLA, dedicated support, and annual success review",
          ],
        },
      ],
      addOns: [
        { title: "Credits packs", copy: "Buy from ¥999 / 10,000 Credits for conversations, retrieval, document processing, OCR, transcription, summaries, tool calls, and insight tasks." },
        { title: "Knowledge capacity packs", copy: "For original files, vector indexes, metadata, logs, and backup retention. Expansion starts from ¥299 / 100 GB / mo." },
        { title: "Concurrency and builder expansion", copy: "Add concurrency, builder seats, and team-member capacity when more business teams join configuration and operations." },
        { title: "Launch services", copy: "Workflow discovery, knowledge initialization, connector setup, skill cleanup, training, and acceptance support." },
      ],
    },
    docs: {
      title: "Docs help teams move from pilot to governed rollout.",
      lead: "Start with the first agent workflow, then move into admin setup, skill reuse, Open API, and private deployment.",
      sections: [
        { title: "Quick start", copy: "Create an organization, choose a Solution, connect knowledge, and run the first agent.", links: ["First agent", "Knowledge connection", "Invite teammates"] },
        { title: "Admin setup", copy: "Models, tools, users, permissions, channels, audit, and organization policy.", links: ["Models", "Tool governance", "Permissions and audit"] },
        { title: "SkillsHub", copy: "Import, export, review, version, and distribute enterprise skill packages.", links: ["Package format", "Import export", "Review and rollback"] },
        { title: "Open API and integrations", copy: "Embed AgentCiCi into portals, CRM, ticketing, and custom systems.", links: ["API Key", "Chat Messages", "Webhook"] },
        { title: "Private deployment", copy: "Deployment mode, model resource responsibility, retention, backup, and upgrade governance.", links: ["Readiness", "Local models", "Upgrade policy"] },
      ],
    },
    community: {
      title: "Community will become the exchange for enterprise agent practice.",
      lead: "It will host agent templates, industry scenarios, skill contributions, implementation notes, and admin discussions.",
      status: "Coming soon",
      areas: [
        { title: "Agent templates", copy: "Templates for service, meetings, customer intelligence, tickets, and Open API scenarios." },
        { title: "Skill contributions", copy: "Developers and delivery teams share reusable packages and patterns." },
        { title: "Industry discussion", copy: "Cases for manufacturing, software, service operations, global teams, and group governance." },
        { title: "Governance exchange", copy: "Admins discuss evaluation, audit, release, permission, and cost governance." },
      ],
    },
  },
};

export default function AgentCiciWebsite() {
  const location = useLocation();
  const { locale, page } = resolveRoute(location.pathname);
  const copy = COPY[locale];
  const pageSeo = copy.seo[page];
  const [menuOpen, setMenuOpen] = useState(false);
  const [submitted, setSubmitted] = useState(false);

  useEffect(() => {
    document.title = pageSeo.title;
    upsertMeta("description", pageSeo.description);
    upsertMeta("og:title", pageSeo.title, "property");
    upsertMeta("og:description", pageSeo.description, "property");
  }, [pageSeo]);

  useEffect(() => {
    setMenuOpen(false);
    setSubmitted(false);
  }, [locale, page]);

  const activeNav = useMemo(() => copy.nav.find((item) => item.key === page) ?? copy.nav[0], [copy.nav, page]);

  const submitDemo = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitted(true);
  };

  return (
    <main className="acw-site" data-locale={locale}>
      <header className="acw-header">
        <a className="acw-brand" href={copy.shared.homeHref} aria-label="AgentCiCi home">
          <span className="acw-brand__mark">Ci</span>
          <span>
            <strong>AgentCiCi</strong>
            <small>{copy.brandLine}</small>
          </span>
        </a>
        <nav className="acw-nav" aria-label="Website navigation">
          {copy.nav.map((item) => (
            <a key={item.key} className={item.key === activeNav.key ? "is-active" : ""} href={item.href}>
              {item.label}
            </a>
          ))}
        </nav>
        <div className="acw-header__actions">
          <a className="acw-language" href={copy.languageHref}>
            {copy.languageLabel}
          </a>
          <a className="acw-button acw-button--secondary acw-button--login" href={copy.shared.loginHref}>
            {copy.shared.loginLabel}
          </a>
          <a className="acw-button acw-button--primary" href="#demo">
            {copy.demoCta}
          </a>
        </div>
        <button className="acw-menu" type="button" aria-label="Toggle navigation" aria-expanded={menuOpen} onClick={() => setMenuOpen((value) => !value)}>
          <span />
          <span />
        </button>
      </header>

      <div className={`acw-mobile-menu${menuOpen ? " is-open" : ""}`}>
        {copy.nav.map((item) => (
          <a key={item.key} href={item.href}>
            {item.label}
          </a>
        ))}
        <a href={copy.languageHref}>{copy.languageLabel}</a>
        <a className="acw-mobile-menu__login" href={copy.shared.loginHref}>
          {copy.shared.loginLabel}
        </a>
        <a href={copy.shared.adminLoginHref}>{copy.shared.adminLoginLabel}</a>
        <a href={copy.shared.platformLoginHref}>{copy.shared.platformLoginLabel}</a>
      </div>

      {page === "solutions" ? <SolutionsPage copy={copy} /> : null}
      {page === "skillshub" ? <SkillsHubPage copy={copy} /> : null}
      {page === "pricing" ? <PricingPage copy={copy} /> : null}
      {page === "docs" ? <DocsPage copy={copy} /> : null}
      {page === "community" ? <CommunityPage copy={copy} /> : null}

      <DemoSection copy={copy} submitted={submitted} onSubmit={submitDemo} />

      <footer className="acw-footer">
        <a className="acw-brand" href={copy.shared.homeHref} aria-label="AgentCiCi home">
          <span className="acw-brand__mark">Ci</span>
          <span>
            <strong>AgentCiCi</strong>
            <small>{copy.brandLine}</small>
          </span>
        </a>
        <p>{copy.shared.footerLine}</p>
        <div>
          {copy.nav.map((item) => (
            <a key={item.key} href={item.href}>
              {item.label}
            </a>
          ))}
          <a href={copy.shared.loginHref}>{copy.shared.loginLabel}</a>
          <a href={copy.shared.adminLoginHref}>{copy.shared.adminLoginLabel}</a>
          <a href={copy.shared.platformLoginHref}>{copy.shared.platformLoginLabel}</a>
        </div>
      </footer>
    </main>
  );
}

function SolutionsPage({ copy }: { copy: SiteCopy }) {
  return (
    <>
      <section className="acw-hero acw-section">
        <div className="acw-hero__copy">
          <h1>{copy.solutions.heroTitle}</h1>
          <p>{copy.solutions.heroLead}</p>
          <div className="acw-hero__actions">
            <a className="acw-button acw-button--primary" href="#demo">
              {copy.demoCta}
            </a>
            <a className="acw-button acw-button--secondary" href={copy.nav[1].href}>
              SkillsHub
            </a>
            <a className="acw-button acw-button--secondary acw-button--login" href={copy.shared.loginHref}>
              {copy.shared.loginLabel}
            </a>
          </div>
          <div className="acw-proof">
            {copy.solutions.heroPoints.map((point) => (
              <span key={point}>{point}</span>
            ))}
          </div>
        </div>
        <RuntimeVisual title={copy.solutions.visualTitle} nodes={copy.solutions.visualNodes} />
      </section>

      <section className="acw-section acw-solutions">
        <SectionHead title={copy.solutions.sectionTitle} intro={copy.solutions.sectionIntro} />
        <div className="acw-solution-stack">
          {copy.solutions.items.map((solution, index) => (
            <article key={solution.name} className="acw-solution">
              <div className="acw-solution__index">{String(index + 1).padStart(2, "0")}</div>
              <div>
                <span>{solution.label}</span>
                <h3>{solution.name}</h3>
                <p>{solution.summary}</p>
              </div>
              <dl>
                <div>
                  <dt>{copy.locale === "zh" ? "触发" : "Trigger"}</dt>
                  <dd>{solution.trigger}</dd>
                </div>
                <div>
                  <dt>{copy.locale === "zh" ? "连接" : "Connects"}</dt>
                  <dd>{solution.connects}</dd>
                </div>
                <div>
                  <dt>{copy.locale === "zh" ? "交付" : "Output"}</dt>
                  <dd>{solution.output}</dd>
                </div>
              </dl>
              <div className="acw-tags">
                {solution.proof.map((item) => (
                  <span key={item}>{item}</span>
                ))}
              </div>
            </article>
          ))}
        </div>
      </section>

      <section className="acw-section acw-runtime">
        <SectionHead title={copy.solutions.runtimeTitle} intro={copy.solutions.runtimeIntro} />
        <div className="acw-runtime__grid">
          {copy.solutions.runtimeItems.map((item) => (
            <article key={item.title}>
              <h3>{item.title}</h3>
              <p>{item.copy}</p>
            </article>
          ))}
        </div>
      </section>

      <BeforeAfter copy={copy} />
    </>
  );
}

function SkillsHubPage({ copy }: { copy: SiteCopy }) {
  return (
    <section className="acw-section acw-page">
      <PageHero title={copy.skillshub.title} lead={copy.skillshub.lead} />
      <div className="acw-lanes">
        {copy.skillshub.lanes.map((lane) => (
          <article key={lane.title}>
            <h2>{lane.title}</h2>
            <p>{lane.copy}</p>
            <div className="acw-tags">
              {lane.tags.map((tag) => (
                <span key={tag}>{tag}</span>
              ))}
            </div>
          </article>
        ))}
      </div>
      <ProcessRail items={copy.skillshub.workflow} />
    </section>
  );
}

function PricingPage({ copy }: { copy: SiteCopy }) {
  return (
    <section className="acw-section acw-page acw-pricing-page">
      <PageHero title={copy.pricing.title} lead={copy.pricing.lead} />
      <div className="acw-credit-note">
        <div>
          <span>{copy.locale === "zh" ? "计费说明" : "Billing note"}</span>
          <p>{copy.pricing.creditNote}</p>
        </div>
        <a className="acw-button acw-button--primary" href="#demo">
          {copy.demoCta}
        </a>
      </div>
      <PricingPlans plans={copy.pricing.plans} />
      <div className="acw-addon-row acw-addon-row--pricing">
        {copy.pricing.addOns.map((item) => (
          <article key={item.title}>
            <h3>{item.title}</h3>
            <p>{item.copy}</p>
          </article>
        ))}
      </div>
    </section>
  );
}

function DocsPage({ copy }: { copy: SiteCopy }) {
  return (
    <section className="acw-section acw-page">
      <PageHero title={copy.docs.title} lead={copy.docs.lead} />
      <div className="acw-docs-list">
        {copy.docs.sections.map((section, index) => (
          <article key={section.title}>
            <span>{String(index + 1).padStart(2, "0")}</span>
            <div>
              <h2>{section.title}</h2>
              <p>{section.copy}</p>
            </div>
            <ul>
              {section.links.map((link) => (
                <li key={link}>{link}</li>
              ))}
            </ul>
          </article>
        ))}
      </div>
    </section>
  );
}

function CommunityPage({ copy }: { copy: SiteCopy }) {
  return (
    <section className="acw-section acw-page">
      <PageHero title={copy.community.title} lead={copy.community.lead} />
      <div className="acw-community-grid">
        {copy.community.areas.map((area) => (
          <article key={area.title}>
            <h2>{area.title}</h2>
            <p>{area.copy}</p>
          </article>
        ))}
      </div>
    </section>
  );
}

function RuntimeVisual({ title, nodes }: { title: string; nodes: string[] }) {
  return (
    <aside className="acw-runtime-visual" aria-label={title}>
      <div className="acw-runtime-visual__top">
        <div>
          <span>{title}</span>
          <strong>{nodes[0]}</strong>
        </div>
      </div>
      <div className="acw-runtime-visual__flow">
        {nodes.map((node, index) => (
          <div className="acw-runtime-node" key={node}>
            <span>{String(index + 1).padStart(2, "0")}</span>
            <strong>{node}</strong>
          </div>
        ))}
      </div>
      <div className="acw-runtime-visual__console">
        <div>
          <span>{nodes[1]}</span>
          <strong>{nodes[1]}</strong>
          <small>{title}</small>
        </div>
        <div>
          <span>{nodes[2]}</span>
          <strong>{nodes[2]}</strong>
          <small>{nodes[3]}</small>
        </div>
        <div>
          <span>{nodes[4]}</span>
          <strong>{nodes[4]}</strong>
          <small>{nodes[3]}</small>
        </div>
      </div>
      <div className="acw-runtime-visual__ledger">
        <span>{nodes[3]}</span>
        <strong>{nodes[4]}</strong>
      </div>
    </aside>
  );
}

function BeforeAfter({ copy }: { copy: SiteCopy }) {
  return (
    <section className="acw-section acw-before-after">
      <h2>{copy.solutions.beforeAfter.title}</h2>
      <div>
        <article>
          <span>{copy.locale === "zh" ? "改造前" : "Before"}</span>
          <ul>
            {copy.solutions.beforeAfter.before.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </article>
        <article>
          <span>{copy.locale === "zh" ? "改造后" : "After"}</span>
          <ul>
            {copy.solutions.beforeAfter.after.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </article>
      </div>
    </section>
  );
}

function PricingPlans({ plans }: { plans: PricingPlan[] }) {
  return (
    <section className="acw-pricing-section" aria-label="Pricing plans">
      <div className="acw-plan-grid">
        {plans.map((plan) => (
          <article key={plan.name} className={plan.highlighted ? "acw-plan-card is-highlighted" : "acw-plan-card"}>
            <div className="acw-plan-card__head">
              <span className="acw-plan-card__badge">{plan.badge}</span>
              <h2>{plan.name}</h2>
              <p>{plan.description}</p>
            </div>
            <div className="acw-plan-card__price">
              <strong>{plan.price}</strong>
              <small>{plan.period}</small>
            </div>
            <dl className="acw-plan-card__quota">
              <div>
                <dt>Credits</dt>
                <dd>{plan.credits}</dd>
              </div>
              <div>
                <dt>{plan.knowledgeStorage.includes("knowledge") ? "Knowledge" : "知识库"}</dt>
                <dd>{plan.knowledgeStorage}</dd>
              </div>
              <div>
                <dt>{plan.builderSeats.includes("builder") ? "Builders" : "构建"}</dt>
                <dd>{plan.builderSeats}</dd>
              </div>
              <div>
                <dt>{plan.teamAccess.includes("team") || plan.teamAccess.includes("Team") ? "Team access" : "团队成员"}</dt>
                <dd>{plan.teamAccess}</dd>
              </div>
              <div>
                <dt>{plan.concurrency.includes("concurrent") ? "Concurrency" : "并发运行"}</dt>
                <dd>{plan.concurrency}</dd>
              </div>
            </dl>
            <ul>
              {plan.features.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
            <a className={plan.highlighted ? "acw-button acw-button--primary" : "acw-button acw-button--secondary"} href="#demo">
              {plan.cta}
            </a>
          </article>
        ))}
      </div>
    </section>
  );
}

function ProcessRail({ items }: { items: Array<{ title: string; copy: string }> }) {
  return (
    <div className="acw-process">
      {items.map((item, index) => (
        <article key={item.title}>
          <span>{String(index + 1).padStart(2, "0")}</span>
          <h2>{item.title}</h2>
          <p>{item.copy}</p>
        </article>
      ))}
    </div>
  );
}

function PageHero({ title, lead }: { title: string; lead: string }) {
  return (
    <header className="acw-page-hero">
      <h1>{title}</h1>
      <p>{lead}</p>
    </header>
  );
}

function SectionHead({ title, intro }: { title: string; intro: string }) {
  return (
    <div className="acw-section-head">
      <div>
        <h2>{title}</h2>
      </div>
      <p>{intro}</p>
    </div>
  );
}

function DemoSection({ copy, submitted, onSubmit }: { copy: SiteCopy; submitted: boolean; onSubmit: (event: FormEvent<HTMLFormElement>) => void }) {
  return (
    <section id="demo" className="acw-section acw-demo">
      <div className="acw-demo__copy">
        <h2>{copy.shared.demoTitle}</h2>
        <p>{copy.shared.demoIntro}</p>
      </div>
      <form className="acw-demo__form" onSubmit={onSubmit}>
        <label>
          <span>{copy.shared.demoFields.company}</span>
          <input name="company" autoComplete="organization" required />
        </label>
        <label>
          <span>{copy.shared.demoFields.contact}</span>
          <input name="contact" autoComplete="name" required />
        </label>
        <label>
          <span>{copy.shared.demoFields.email}</span>
          <input name="email" autoComplete="email" required />
        </label>
        <label>
          <span>{copy.shared.demoFields.focus}</span>
          <select name="focus" required defaultValue="">
            <option value="" disabled>
              {copy.shared.demoFields.focus}
            </option>
            {copy.nav.map((item) => (
              <option key={item.key} value={item.label}>
                {item.label}
              </option>
            ))}
          </select>
        </label>
        <label className="acw-demo__wide">
          <span>{copy.shared.demoFields.note}</span>
          <textarea name="note" rows={4} />
        </label>
        <button className="acw-button acw-button--primary" type="submit">
          {copy.shared.demoFields.submit}
        </button>
        {submitted ? <p className="acw-demo__notice">{copy.shared.demoFields.submitted}</p> : null}
      </form>
    </section>
  );
}

function resolveRoute(pathname: string): { locale: Locale; page: PageKey } {
  const parts = pathname.split("/").filter(Boolean);
  const isGlobal = parts[0] === "global";
  const locale: Locale = isGlobal ? "en" : "zh";
  const slug = isGlobal ? parts[1] : parts[0];
  if (slug === "skill-hub") return { locale, page: "skillshub" };
  if (slug === "pricing") return { locale, page: "pricing" };
  if (slug === "docs") return { locale, page: "docs" };
  if (slug === "community") return { locale, page: "community" };
  return { locale, page: "solutions" };
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
