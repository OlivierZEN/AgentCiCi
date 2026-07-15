export type SkillDependencyNodeType =
  | "AGENT"
  | "WORKFLOW_VERSION"
  | "SKILL"
  | "SKILL_VERSION"
  | "TOOL"
  | "KNOWLEDGE_BASE";

export type SkillDependencyGraphScope = {
  type: string;
  id: string;
  label: string;
  workflowVersionId?: number | null;
  versionNo?: number | null;
  publishStatus?: string | null;
};

export type SkillDependencyGraphNode = {
  id: string;
  type: SkillDependencyNodeType;
  label: string;
  detail: string;
  status: string;
  layer: number;
  metadata: Record<string, unknown>;
};

export type SkillDependencyGraphEdge = {
  id: string;
  source: string;
  target: string;
  type: string;
  label: string;
};

export type SkillDependencyGraphSummary = {
  agentCount: number;
  workflowVersionCount: number;
  skillCount: number;
  skillVersionCount: number;
  toolCount: number;
  knowledgeBaseCount: number;
};

export type SkillDependencyGraphView = {
  scope: SkillDependencyGraphScope;
  sourceMode: string;
  nodes: SkillDependencyGraphNode[];
  edges: SkillDependencyGraphEdge[];
  summary: SkillDependencyGraphSummary;
  warnings: string[];
};
