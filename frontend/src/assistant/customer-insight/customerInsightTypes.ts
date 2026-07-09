export type CustomerInsightProject = {
  projectId: string;
  customerName: string;
  customerExternalId: string;
  customerObjectApiName: string;
  industry: string;
  sourceType: "MANUAL" | "CLOUDCC" | "MIXED" | string;
  status: string;
  completenessScore: number;
  latestSummary: string;
  generatedSectionCount: number;
  sectionCount: number;
  createdAt: string;
  updatedAt: string;
  sections?: CustomerInsightSection[];
  sources?: CustomerInsightSource[];
  jobs?: CustomerInsightJob[];
  catalog?: CustomerInsightSectionCatalogItem[];
};

export type CustomerInsightSection = {
  sectionCode: string;
  sectionGroup: string;
  groupLabel: string;
  title: string;
  description: string;
  input: Record<string, unknown>;
  output: Record<string, unknown>;
  markdown: string;
  status: string;
  aiGenerated: boolean;
  modelProvider: string;
  modelName: string;
  skillCode: string;
  traceId: string;
  errorMessage: string;
  updatedAt: string;
};

export type CustomerInsightSectionCatalogItem = {
  sectionGroup: string;
  groupLabel: string;
  sectionCode: string;
  title: string;
  description: string;
};

export type CustomerInsightSource = {
  id: number;
  sourceType: string;
  sourceKey: string;
  sourceLabel: string;
  snapshot: Record<string, unknown>;
  collectedAt: string;
};

export type CustomerInsightJob = {
  id: number;
  sectionCode: string;
  jobType: string;
  status: string;
  requestSummary: string;
  resultSummary: string;
  traceId: string;
  createdAt: string;
  completedAt: string;
};

export type CustomerInsightGenerateResult = {
  success: boolean;
  section: CustomerInsightSection;
  job: CustomerInsightJob;
  project: CustomerInsightProject;
  error?: string;
};

export type CustomerInsightDashboard = {
  sourceMode: "REAL_CRM_DEMO" | "REAL_AGGREGATE" | "MOCK" | string;
  sourceLabel: string;
  sourceDescription: string;
  updatedAt: string;
  summary: {
    totalCustomers: number;
    totalLeads: number;
    openOpportunities: number;
    pipelineAmount: number;
    contractAmount: number;
    orderAmount: number;
    winRate: number;
    avgHealth: number;
    riskCustomers: number;
    interactionCount: number;
    recommendationCount: number;
    highConfidenceRecommendationCount: number;
  };
  funnel: Array<{ code: string; label: string; value: number }>;
  segments: Array<{ code: string; label: string; value: number; color: string }>;
  trend: Array<{ month: string; pipeline: number; contract: number; order: number; interactions: number }>;
  accounts: Array<{
    accountId: string;
    accountName: string;
    industry: string;
    segment: string;
    segmentLabel: string;
    owner: string;
    stage: string;
    healthScore: number;
    progressScore: number;
    riskCount: number;
    nextActionCount: number;
    pipelineAmount: number;
    contractAmount: number;
    orderAmount: number;
    summary: string;
  }>;
  risks: Array<{
    accountId: string;
    accountName: string;
    riskLevel: "LOW" | "MEDIUM" | "HIGH" | string;
    riskCount: number;
    healthScore: number;
    nextActionCount: number;
    summary: string;
  }>;
  recommendations: Array<{
    title: string;
    accountId?: string;
    accountName?: string;
    type: string;
    confidence: number;
    status: string;
    updatedAt?: string;
  }>;
};
