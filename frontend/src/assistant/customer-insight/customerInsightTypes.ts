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
