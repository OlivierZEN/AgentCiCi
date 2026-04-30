export type Skill = {
  id: number;
  skillCode: string;
  name: string;
  description?: string;
  builtin: boolean;
  enabled: boolean;
  riskLevel: "LOW" | "MEDIUM" | "HIGH";
  sourceType: "PLATFORM_STANDARD" | "TENANT_DERIVED" | "TENANT_CUSTOM";
  visibility: "VISIBLE" | "HIDDEN";
  editPolicy: "LOCKED" | "CONFIGURABLE" | "EDITABLE";
  bindingPolicy: "MANDATORY" | "DEFAULT_ON" | "OPTIONAL" | "INTERNAL_ONLY";
  updatePolicy: "AUTO" | "MANUAL" | "PINNED";
  templateCode?: string;
  baseTemplateVersion?: number;
  currentPublishedVersionId?: number;
  latestDraftVersionId?: number;
  promptFragment?: string;
  draftSpecText?: string;
  toolWhitelist: string[];
  kbWhitelist: string[];
  handoffRule?: string;
  outputContract?: string;
  createdAt?: string;
  updatedAt?: string;
  latestVersionNo?: number;
  latestVersionPublishStatus?: string;
  latestVersionCreatedAt?: string;
  lastPublishedAt?: string;
};

export type SkillPreview = {
  promptPreview: string;
  effectiveToolNames: string[];
  effectiveKnowledgeBaseIds: string[];
  riskLevel: string;
  warnings: string[];
  compileSummary: string[];
};

export type GeneratedSkillSpec = {
  skillCode: string;
  name: string;
  description?: string;
  promptFragment?: string;
  draftSpecText?: string;
  toolWhitelist: string[];
  kbWhitelist: string[];
  handoffRule?: string;
  outputContract?: string;
  riskLevel: "LOW" | "MEDIUM" | "HIGH";
  triggerHints: string[];
  userIntentExamples: string[];
  clarificationQuestions: string[];
  warnings: string[];
};

export type SkillAuthoringResult = {
  sourceText: string;
  sessionId?: string;
  skillSpec: GeneratedSkillSpec;
  preview: SkillPreview;
};

export type SkillAuthoringCreateResult = {
  sourceText: string;
  sessionId?: string;
  skillSpec: GeneratedSkillSpec;
  createdSkill: Skill;
  preview: SkillPreview;
};

export type SkillForm = {
  id?: number;
  skillCode: string;
  name: string;
  description: string;
  enabled: boolean;
  riskLevel: "LOW" | "MEDIUM" | "HIGH";
  sourceType: Skill["sourceType"];
  visibility: Skill["visibility"];
  editPolicy: Skill["editPolicy"];
  bindingPolicy: Skill["bindingPolicy"];
  updatePolicy: Skill["updatePolicy"];
  templateCode: string;
  baseTemplateVersion?: number;
  promptFragment: string;
  draftSpecText: string;
  toolWhitelistText: string;
  kbWhitelistText: string;
  handoffRule: string;
  outputContract: string;
  builtin: boolean;
};

export type SkillTemplate = {
  key: string;
  title: string;
  scene: string;
  summary: string;
  form: SkillForm;
};

const CRM_TOOLS = [
  "cloudcc_getStandardObjects",
  "cloudcc_getCustomObjects",
  "cloudcc_getObjectFields",
  "cloudcc_pageQuery",
].join(", ");

export const EMPTY_FORM: SkillForm = {
  skillCode: "",
  name: "",
  description: "",
  enabled: true,
  riskLevel: "MEDIUM",
  sourceType: "TENANT_CUSTOM",
  visibility: "VISIBLE",
  editPolicy: "EDITABLE",
  bindingPolicy: "OPTIONAL",
  updatePolicy: "MANUAL",
  templateCode: "",
  promptFragment: "",
  draftSpecText: "",
  toolWhitelistText: "",
  kbWhitelistText: "",
  handoffRule: "",
  outputContract: "",
  builtin: false,
};

export const CRM_TEMPLATES: SkillTemplate[] = [
  {
    key: "lead-intake",
    title: "线索分诊助手",
    scene: "Lead Qualification",
    summary: "首轮识别客户画像、预算和决策节奏，输出线索等级与跟进建议。",
    form: {
      ...EMPTY_FORM,
      skillCode: "crm-lead-intake",
      name: "CRM 线索分诊",
      description: "识别客户背景、预算、需求紧迫度并给出分层跟进动作。",
      promptFragment:
        "Classify inbound lead quality into A/B/C tiers. Extract industry, role, budget signal, and expected launch timeline. Suggest next best action for SDR.",
      draftSpecText: [
        "1. 提取客户公司、角色、行业、地域与对接渠道。",
        "2. 识别预算信号、紧急程度和采购决策链条。",
        "3. 按 A/B/C 输出线索等级，并标注判定依据。",
        "4. 给出下一步动作：约演示、补信息、进入培育池。",
      ].join("\n"),
      toolWhitelistText: CRM_TOOLS,
      outputContract: "输出包含线索等级、证据字段、推荐动作、建议负责人。",
      handoffRule: "涉及价格承诺、合同条款或跨部门资源调度时，转人工销售确认。",
    },
  },
  {
    key: "opportunity-health",
    title: "商机健康扫描",
    scene: "Pipeline Health",
    summary: "从阶段停留时长、活动频次、风险信号判断商机健康度。",
    form: {
      ...EMPTY_FORM,
      skillCode: "crm-opportunity-health",
      name: "CRM 商机健康扫描",
      description: "分析商机推进效率与流失风险，输出优先干预列表。",
      riskLevel: "MEDIUM",
      promptFragment:
        "Analyze opportunity progress based on stage aging, meeting frequency, and stakeholder engagement. Provide red/amber/green health label and recovery playbook.",
      draftSpecText: [
        "1. 拉取商机阶段、最近活动时间、关键人互动记录。",
        "2. 计算阶段停留时长与推进速度偏差。",
        "3. 识别风险信号：关键人失联、预算下调、竞品进入。",
        "4. 输出 RAG 健康标记和三条修复动作。",
      ].join("\n"),
      toolWhitelistText: CRM_TOOLS,
      outputContract: "输出包含健康等级、风险项、建议动作、建议完成时点。",
      handoffRule: "涉及价格打折、交付承诺或法务条款的建议必须转人工。",
    },
  },
  {
    key: "followup-orchestrator",
    title: "跟进节奏编排",
    scene: "Follow-up Rhythm",
    summary: "根据客户阶段自动产出多触点跟进计划与话术重点。",
    form: {
      ...EMPTY_FORM,
      skillCode: "crm-followup-orchestrator",
      name: "CRM 跟进节奏编排",
      description: "生成未来 14 天客户跟进节奏和触达建议。",
      promptFragment:
        "Create a 14-day follow-up cadence with channel mix (call, IM, email), objective per touchpoint, and expected outcome.",
      draftSpecText: [
        "1. 读取客户当前阶段、上次触达时间、历史互动偏好。",
        "2. 规划未来 14 天跟进节奏（电话/IM/邮件）。",
        "3. 每次触达附上目标、关键问句和预期结果。",
        "4. 若连续两次无响应，自动降级并提醒人工介入。",
      ].join("\n"),
      toolWhitelistText: CRM_TOOLS,
      outputContract: "输出包含跟进日程、触达方式、话术重点、升级条件。",
      handoffRule: "客户提出商务条款谈判时直接转人工客户经理。",
    },
  },
  {
    key: "renewal-guard",
    title: "续约风险预警",
    scene: "Renewal Defense",
    summary: "在续约窗口前识别流失风险并制定保留动作。",
    form: {
      ...EMPTY_FORM,
      skillCode: "crm-renewal-guard",
      name: "CRM 续约预警",
      description: "识别续约风险账户，输出保留优先级和升级方案。",
      riskLevel: "HIGH",
      promptFragment:
        "Detect renewal churn risk from usage trend, support sentiment, and unresolved blockers. Recommend retention actions and escalation owner.",
      draftSpecText: [
        "1. 读取合同到期时间、使用活跃度、工单满意度。",
        "2. 识别流失风险并打分（高/中/低）。",
        "3. 对高风险客户输出 72 小时保留动作清单。",
        "4. 涉及价格让利或合同改签时仅给建议并转人工审批。",
      ].join("\n"),
      toolWhitelistText: CRM_TOOLS,
      outputContract: "输出包含风险等级、风险证据、保留动作、负责人。",
      handoffRule: "涉及续费报价、商务让利、合同条款变更必须转人工审批。",
    },
  },
];

export function splitCsv(raw: string): string[] {
  return raw
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

export function joinCsv(items: string[] | undefined): string {
  return (items ?? []).join(", ");
}

export function riskBadgeClass(risk: Skill["riskLevel"]): string {
  if (risk === "HIGH") return "skills-risk skills-risk--high";
  if (risk === "LOW") return "skills-risk skills-risk--low";
  return "skills-risk skills-risk--medium";
}

export function riskLabel(risk: Skill["riskLevel"]): string {
  if (risk === "HIGH") return "高风险";
  if (risk === "LOW") return "低风险";
  return "中风险";
}

export function skillSourceLabel(sourceType: Skill["sourceType"]): string {
  if (sourceType === "PLATFORM_STANDARD") return "平台标准";
  if (sourceType === "TENANT_DERIVED") return "租户派生";
  return "租户自定义";
}
