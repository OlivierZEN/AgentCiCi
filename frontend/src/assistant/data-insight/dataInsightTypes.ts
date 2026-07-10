export type DataInsightDashboard = {
  sourceMode: "REAL_CRM_DEMO" | "REAL_AGGREGATE" | "MOCK" | string;
  sourceLabel: string;
  sourceDescription: string;
  updatedAt: string;
  context: {
    userName: string;
    orgName: string;
    currency: string;
    dashboardName: string;
  };
  summary: {
    totalCustomers: number;
    totalLeads: number;
    openOpportunities: number;
    pipelineAmount: number;
    contractAmount: number;
    paidAmount: number;
    paymentTargetAmount: number;
    orderAmount: number;
    orderCount: number;
    winRate: number;
    paymentAchievementRate: number;
    avgHealth: number;
    riskCustomers: number;
    interactionCount: number;
    recommendationCount: number;
    highConfidenceRecommendationCount: number;
  };
  funnel: Array<{ code: string; label: string; value: number; amount: number }>;
  segments: Array<{ code: string; label: string; value: number; color: string }>;
  trend: Array<{ month: string; pipeline: number; contract: number; order: number; paid: number; interactions: number }>;
  rankings: {
    customerCount: Array<{ label: string; value: number }>;
    contractAmount: Array<{ label: string; value: number }>;
    orderAmount: Array<{ label: string; value: number }>;
    opportunityAmount: Array<{ label: string; value: number }>;
  };
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
    paidAmount: number;
    orderAmount: number;
    orderCount: number;
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
  geoDistribution: Array<{ region: string; value: number; amount: number; tone: string }>;
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

