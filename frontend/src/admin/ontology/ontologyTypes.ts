export type OntologyWorkspaceStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";
export type OntologyConceptType = "ENTITY" | "EVENT";
export type OntologyDataType =
  | "TEXT"
  | "LONG_TEXT"
  | "INTEGER"
  | "DECIMAL"
  | "BOOLEAN"
  | "DATE"
  | "DATETIME"
  | "ENUM"
  | "REFERENCE";
export type OntologyCardinality = "ONE_TO_ONE" | "ONE_TO_MANY" | "MANY_TO_ONE" | "MANY_TO_MANY";
export type OntologyAggregation = "COUNT" | "SUM" | "AVG" | "MIN" | "MAX";
export type OntologySourceType = "INLINE_SAMPLE" | "CONNECTOR";
export type OntologyOperator =
  | "EQ"
  | "NE"
  | "IN"
  | "CONTAINS"
  | "GT"
  | "GTE"
  | "LT"
  | "LTE"
  | "BETWEEN"
  | "IS_NULL";

export type OntologyApiDetails = Record<string, unknown>;

export interface OntologyApiEnvelope<T> {
  success: boolean;
  data: T | null;
  message: string;
  code?: string | null;
  details?: OntologyApiDetails | null;
}

export interface OntologyWorkspaceView {
  id: number;
  key: string;
  name: string;
  description: string | null;
  createdBy: string;
  status: OntologyWorkspaceStatus;
  draftRevision: number;
  publishedVersion: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface OntologyProperty {
  key: string;
  name: string;
  description: string | null;
  dataType: OntologyDataType;
  required: boolean;
  multiple: boolean;
  sensitive: boolean;
  queryable: boolean;
  enumValues: string[];
}

export interface OntologyConcept {
  key: string;
  name: string;
  pluralName: string | null;
  description: string | null;
  conceptType: OntologyConceptType;
  displayPropertyKey: string | null;
  positionX: number;
  positionY: number;
  queryable: boolean;
  enabled: boolean;
  properties: OntologyProperty[];
}

export interface OntologyRelation {
  key: string;
  name: string;
  description: string | null;
  sourceConceptKey: string;
  targetConceptKey: string;
  cardinality: OntologyCardinality;
  forwardLabel: string | null;
  reverseLabel: string | null;
  queryable: boolean;
  enabled: boolean;
}

export interface OntologyQueryFilter {
  property: string;
  operator: OntologyOperator;
  value: unknown;
}

export interface OntologyMetric {
  key: string;
  name: string;
  conceptKey: string;
  aggregation: OntologyAggregation;
  measurePropertyKey: string | null;
  groupByPropertyKeys: string[];
  timePropertyKey: string | null;
  filters: OntologyQueryFilter[];
}

export interface OntologyActionParameter {
  key: string;
  name: string;
  dataType: OntologyDataType;
  required: boolean;
}

export interface OntologyAction {
  key: string;
  name: string;
  conceptKey: string;
  description: string | null;
  parameters: OntologyActionParameter[];
}

/** Redacted data-source identity returned inside draft documents. */
export interface OntologyDataSource {
  id: number | null;
  key: string;
  name: string;
  type: OntologySourceType;
}

export interface OntologyMapping {
  targetType: string;
  targetKey: string;
  dataSourceId: number;
  physicalObjectKey: string;
  physicalFieldKey: string | null;
  relationTargetFieldKey: string | null;
  transform: string | null;
  confidence: number;
  source?: string | null;
  validationStatus?: string | null;
}

export interface OntologyDocument {
  key: string;
  name: string;
  description: string | null;
  concepts: OntologyConcept[];
  relations: OntologyRelation[];
  metrics: OntologyMetric[];
  actions: OntologyAction[];
  dataSources: OntologyDataSource[];
  mappings: OntologyMapping[];
}

export interface OntologySampleSummary {
  objectCount: number;
  rowCount: number;
  fieldValueCount: number;
}

export interface OntologySourceView {
  id: number;
  key: string;
  name: string;
  type: OntologySourceType;
  adapterKey: string | null;
  status: string;
  lastValidatedAt: string | null;
  sample: OntologySampleSummary;
}

export interface OntologyDraftView {
  workspace: OntologyWorkspaceView;
  id: number;
  key: string;
  status: OntologyWorkspaceStatus;
  draftRevision: number;
  publishedVersion: number | null;
  document: OntologyDocument;
  sources: OntologySourceView[];
}

export interface OntologyDraftDiff {
  draftRevision: number;
  publishedVersion: number | null;
  changed: boolean;
}

export interface OntologyValidationIssue {
  code: string;
  severity: "INFO" | "WARNING" | "ERROR";
  path: string;
  message: string;
}

export interface OntologyProposalDiff {
  baseRevision: number;
  candidateHash: string;
  added: string[];
  changed: string[];
  removed: string[];
}

export type OntologyProposalStatus = "PENDING" | "READY" | "FAILED" | "APPLIED";

export interface OntologyProposalRecord {
  id: number;
  workspaceId: number;
  proposalType: string;
  status: OntologyProposalStatus;
  baseRevision?: number;
  candidate: OntologyDocument | null;
  diff: OntologyProposalDiff | null;
  validation: OntologyValidationIssue[];
  diagnosticCode?: string | null;
  diagnosticMessage?: string | null;
  createdAt: string;
  updatedAt: string;
  appliedAt: string | null;
}

export interface OntologySourceSelection {
  dataSourceId: number;
  objectKey: string;
  fieldKeys: string[];
}

export interface OntologyProposalRequest {
  instruction: string;
  selectedSources: OntologySourceSelection[];
  mode: string;
}

export interface OntologyDraftDataSourceInput {
  id: number;
  key: string;
  name: string;
  type: OntologySourceType;
}

export interface OntologyMappingInput {
  targetType: string;
  targetKey: string;
  dataSourceId: number;
  physicalObjectKey: string;
  physicalFieldKey: string | null;
  relationTargetFieldKey: string | null;
  transform: string | null;
  confidence: number;
}

export interface OntologyDraftDocumentInput {
  key: string;
  name: string;
  description: string | null;
  concepts: OntologyConcept[];
  relations: OntologyRelation[];
  metrics: OntologyMetric[];
  actions: OntologyAction[];
  dataSources: OntologyDraftDataSourceInput[];
  mappings: OntologyMappingInput[];
}

export interface OntologyDraftSaveRequest {
  expectedRevision: number;
  document: OntologyDraftDocumentInput;
}

export interface OntologyDataSourceMutationInput {
  id: number | null;
  key: string;
  name: string;
  type: OntologySourceType;
  configJson: string | null;
  sampleDataJson: string | null;
}

export interface OntologyPhysicalFieldView {
  id: number;
  key: string;
  name: string;
  dataType: string;
  nullable: boolean;
  multiple: boolean;
  discoveredAt: string;
}

export interface OntologyPhysicalObjectView {
  id: number;
  dataSourceId: number;
  key: string;
  name: string;
  type: string;
  discoveredAt: string;
  fields: OntologyPhysicalFieldView[];
}

export interface OntologyCatalogView {
  revision: number;
  objects: OntologyPhysicalObjectView[];
}

export interface OntologyDiscoveredObject {
  key: string;
  name: string;
  type: string;
}

export interface OntologyDiscoveredField {
  objectKey: string;
  key: string;
  name: string;
  dataType: string;
  nullable: boolean;
  multiple: boolean;
}

export interface OntologyCatalogObjectMutation {
  items: OntologyDiscoveredObject[];
  revision: number;
}

export interface OntologyCatalogFieldMutation {
  items: OntologyDiscoveredField[];
  revision: number;
}

export interface OntologyMappingView extends OntologyMapping {
  id: number;
  source: string;
  validationStatus: string;
  lastValidatedAt: string | null;
}

export interface OntologyMappingIdentityInput {
  targetType: string;
  targetKey: string;
  dataSourceId: number;
}

export interface OntologyMappingValidationResult {
  mapping: OntologyMappingIdentityInput;
  valid: boolean;
  code: string;
  message: string;
}

export interface OntologyMappingValidationBatch {
  revision: number;
  results: OntologyMappingValidationResult[];
}

export interface OntologyCompilePreview {
  version: number;
  sourceDraftRevision: number;
  contentHash: string;
  jsonSchema: string;
  graphqlSdl: string;
  queryContractJson: string;
}

export interface OntologyVersionSummary {
  version: number;
  sourceDraftRevision: number;
  contentHash: string;
  publishedBy: string;
  publishedAt: string;
}

export interface OntologyVersionDetail {
  summary: OntologyVersionSummary;
  document: OntologyDocument;
  jsonSchema: string;
  graphqlSdl: string;
  queryContractJson: string;
  validation: unknown[];
}

export interface OntologyReferencePackageSummary {
  id: string;
  title: string;
  description: string;
  workspaceIdentity: Pick<OntologyWorkspaceView, "key" | "name" | "description">;
  conceptCount: number;
  dataSourceCount: number;
}

export interface OntologyReferencePackageView {
  id: string;
  title: string;
  description: string;
  document: OntologyDocument;
  sources: OntologySourceView[];
}

export interface OntologySemanticFilter {
  field: string;
  operator: OntologyOperator;
  value: unknown;
}

export interface OntologySemanticOrder {
  field: string;
  direction: "ASC" | "DESC";
}

export interface OntologySemanticQuery {
  ontologyKey: string;
  version: number;
  concept: string;
  select: string[];
  filters: OntologySemanticFilter[];
  orderBy: OntologySemanticOrder[];
  limit: number;
}

export interface OntologyQueryPlan {
  ontologyKey: string;
  ontologyVersion: number;
  concept: string;
  sourceType: OntologySourceType;
  dataSourceKey: string;
  physicalObjectKey: string;
  fields: Array<{ logicalField: string; physicalField: string }>;
  relations: unknown[];
  filters: Array<{ logicalField: string; operator: string }>;
  orderBy: Array<{ logicalField: string; direction: string }>;
  limit: number;
}

export interface OntologyQueryResult {
  rows: Array<Record<string, unknown>>;
  evidence: {
    sourceType: OntologySourceType;
    dataSourceKey: string;
    ontologyVersion: number;
    mappings: Array<{
      logicalField: string;
      physicalObject: string;
      physicalField: string | null;
      usage: string;
    }>;
    totalCount: number;
    moreAvailable: boolean;
  };
  elapsedMs: number;
}
