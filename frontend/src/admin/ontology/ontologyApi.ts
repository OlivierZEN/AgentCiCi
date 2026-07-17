import { createOntologyMutationLane, type OntologyMutationLane } from "./ontologyModel";
import type {
  OntologyApiDetails,
  OntologyCatalogFieldMutation,
  OntologyCatalogObjectMutation,
  OntologyCatalogView,
  OntologyCompilePreview,
  OntologyDataSourceMutationInput,
  OntologyDocument,
  OntologyDraftDiff,
  OntologyDraftSaveRequest,
  OntologyDraftView,
  OntologyMappingIdentityInput,
  OntologyMappingInput,
  OntologyMappingValidationBatch,
  OntologyMappingView,
  OntologyProposalRecord,
  OntologyProposalRequest,
  OntologyQueryPlan,
  OntologyQueryResult,
  OntologyReferencePackageSummary,
  OntologyReferencePackageView,
  OntologySemanticQuery,
  OntologySourceView,
  OntologyValidationIssue,
  OntologyVersionDetail,
  OntologyVersionSummary,
  OntologyWorkspaceView,
} from "./ontologyTypes";

const MANAGEMENT_ROOT = "/admin/ontologies";
const QUERY_ROOT = "/semantic-query";
const ERROR_CODE_PATTERN = /^[A-Z][A-Z0-9_]{2,127}$/;
const NETWORK_FAILURE_MESSAGE = "网络请求失败，请稍后重试";

function trimmedString(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function plainRecord(value: unknown): Record<string, unknown> | null {
  if (value === null || typeof value !== "object" || Array.isArray(value)) return null;
  const prototype = Object.getPrototypeOf(value);
  return prototype === Object.prototype || prototype === null
    ? value as Record<string, unknown>
    : null;
}

function parseEnvelope(raw: string): Record<string, unknown> | null {
  if (!raw.trim()) return null;
  try {
    return plainRecord(JSON.parse(raw) as unknown);
  } catch {
    return null;
  }
}

export class OntologyApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details: OntologyApiDetails | null;
  readonly outcomeUnknown: boolean;

  constructor(
    message: string,
    status: number,
    code: string,
    details: OntologyApiDetails | null,
    outcomeUnknown = false,
  ) {
    super(message);
    this.name = "OntologyApiError";
    this.status = status;
    this.code = code;
    this.details = details;
    this.outcomeUnknown = outcomeUnknown;
  }
}

/** The apply POST may have committed, but no authoritative response was received. */
export class OntologyProposalApplyOutcomeUnknownError extends Error {
  constructor() {
    super("提案应用结果未知");
    this.name = "OntologyProposalApplyOutcomeUnknownError";
  }
}

/** The publish POST may have committed, but no authoritative response was received. */
export class OntologyPublishOutcomeUnknownError extends Error {
  constructor() {
    super("发布结果未知");
    this.name = "OntologyPublishOutcomeUnknownError";
  }
}

/** The apply mutation committed, but its mandatory redacted-draft reload failed. */
export class OntologyProposalAppliedReloadError extends Error {
  constructor() {
    super("提案已应用，重新加载失败");
    this.name = "OntologyProposalAppliedReloadError";
  }
}

export function isOntologyProposalAppliedReloadError(
  error: unknown,
): error is OntologyProposalAppliedReloadError {
  return error instanceof OntologyProposalAppliedReloadError;
}

export function isOntologyProposalApplyOutcomeUnknownError(
  error: unknown,
): error is OntologyProposalApplyOutcomeUnknownError {
  return error instanceof OntologyProposalApplyOutcomeUnknownError;
}

export function isOntologyPublishOutcomeUnknownError(
  error: unknown,
): error is OntologyPublishOutcomeUnknownError {
  return error instanceof OntologyPublishOutcomeUnknownError;
}

export function normalizeOntologyApiError(
  status: number,
  statusText: string,
  envelope: unknown,
  outcomeUnknown = false,
): OntologyApiError {
  const fields = plainRecord(envelope);
  const explicitCodeValue = trimmedString(fields?.code);
  const explicitCode = ERROR_CODE_PATTERN.test(explicitCodeValue) ? explicitCodeValue : "";
  const messageCode = trimmedString(fields?.message);
  const code = explicitCode
    || (messageCode && ERROR_CODE_PATTERN.test(messageCode) ? messageCode : "")
    || `HTTP_${status}`;
  const safeStatusText = trimmedString(statusText);
  const fallbackMessage = safeStatusText ? `HTTP ${status} ${safeStatusText}` : `HTTP ${status}`;
  const message = messageCode || explicitCode || fallbackMessage;
  const details = plainRecord(fields?.details) as OntologyApiDetails | null;
  return new OntologyApiError(message, status, code, details, outcomeUnknown);
}

export function isOntologyRevisionConflict(error: unknown): error is OntologyApiError {
  return error instanceof OntologyApiError
    && error.status === 409
    && error.code === "ONTOLOGY_REVISION_CONFLICT";
}

function isUncertainMutationStatus(status: number): boolean {
  return status === 0 || status >= 500 || (status >= 200 && status < 300);
}

function isOntologyApiOutcomeUnknown(error: unknown): error is OntologyApiError {
  return error instanceof OntologyApiError && error.outcomeUnknown;
}

function draftDataSources(document: OntologyDocument) {
  return document.dataSources.map((source) => {
    if (source.id == null) throw new Error("DATA_SOURCE_WRITE_REQUIRES_DEDICATED_ENDPOINT");
    return {
      id: source.id,
      key: source.key,
      name: source.name,
      type: source.type,
    };
  });
}

function draftMappings(document: OntologyDocument): OntologyMappingInput[] {
  return document.mappings.map((mapping) => ({
    targetType: mapping.targetType,
    targetKey: mapping.targetKey,
    dataSourceId: mapping.dataSourceId,
    physicalObjectKey: mapping.physicalObjectKey,
    physicalFieldKey: mapping.physicalFieldKey,
    relationTargetFieldKey: mapping.relationTargetFieldKey,
    transform: mapping.transform,
    confidence: mapping.confidence,
  }));
}

export function toDraftSaveRequest(
  expectedRevision: number,
  document: OntologyDocument,
): OntologyDraftSaveRequest {
  return {
    expectedRevision,
    document: {
      key: document.key,
      name: document.name,
      description: document.description,
      concepts: document.concepts.map((concept) => ({
        key: concept.key,
        name: concept.name,
        pluralName: concept.pluralName,
        description: concept.description,
        conceptType: concept.conceptType,
        displayPropertyKey: concept.displayPropertyKey,
        positionX: concept.positionX,
        positionY: concept.positionY,
        queryable: concept.queryable,
        enabled: concept.enabled,
        properties: concept.properties.map((property) => ({
          key: property.key,
          name: property.name,
          description: property.description,
          dataType: property.dataType,
          required: property.required,
          multiple: property.multiple,
          sensitive: property.sensitive,
          queryable: property.queryable,
          enumValues: [...property.enumValues],
        })),
      })),
      relations: document.relations.map((relation) => ({
        key: relation.key,
        name: relation.name,
        description: relation.description,
        sourceConceptKey: relation.sourceConceptKey,
        targetConceptKey: relation.targetConceptKey,
        cardinality: relation.cardinality,
        forwardLabel: relation.forwardLabel,
        reverseLabel: relation.reverseLabel,
        queryable: relation.queryable,
        enabled: relation.enabled,
      })),
      metrics: document.metrics.map((metric) => ({
        key: metric.key,
        name: metric.name,
        conceptKey: metric.conceptKey,
        aggregation: metric.aggregation,
        measurePropertyKey: metric.measurePropertyKey,
        groupByPropertyKeys: [...metric.groupByPropertyKeys],
        timePropertyKey: metric.timePropertyKey,
        filters: metric.filters.map((filter) => ({ ...filter })),
      })),
      actions: document.actions.map((action) => ({
        key: action.key,
        name: action.name,
        conceptKey: action.conceptKey,
        description: action.description,
        parameters: action.parameters.map((parameter) => ({ ...parameter })),
      })),
      dataSources: draftDataSources(document),
      mappings: draftMappings(document),
    },
  };
}

type OntologyFetch = typeof fetch;

export interface OntologyApiOptions {
  fetch?: OntologyFetch;
  mutationLane?: OntologyMutationLane;
}

export interface OntologyApi {
  listWorkspaces(): Promise<OntologyWorkspaceView[]>;
  createWorkspace(input: { key: string; name: string; description: string | null }): Promise<OntologyWorkspaceView>;
  getWorkspace(workspaceId: number): Promise<OntologyWorkspaceView>;
  updateWorkspace(
    workspaceId: number,
    input: { key?: string; name: string; description: string | null; expectedRevision: number },
  ): Promise<OntologyWorkspaceView>;
  archiveWorkspace(workspaceId: number, expectedRevision: number): Promise<OntologyWorkspaceView>;
  getDraft(workspaceId: number): Promise<OntologyDraftView>;
  saveDraft(workspaceId: number, expectedRevision: number, document: OntologyDocument): Promise<OntologyDraftView>;
  validateDraft(workspaceId: number): Promise<OntologyValidationIssue[]>;
  diffDraft(workspaceId: number): Promise<OntologyDraftDiff>;
  createProposal(workspaceId: number, request: OntologyProposalRequest): Promise<OntologyProposalRecord>;
  listProposals(workspaceId: number): Promise<OntologyProposalRecord[]>;
  getProposal(workspaceId: number, proposalId: number): Promise<OntologyProposalRecord>;
  applyProposal(workspaceId: number, proposalId: number, expectedRevision: number): Promise<OntologyDraftView>;
  listReferencePackages(): Promise<OntologyReferencePackageSummary[]>;
  getReferencePackage(packageId: string): Promise<OntologyReferencePackageView>;
  installReferencePackage(packageId: string): Promise<OntologyWorkspaceView>;
  listDataSources(workspaceId: number): Promise<OntologySourceView[]>;
  createDataSource(
    workspaceId: number,
    expectedRevision: number,
    source: OntologyDataSourceMutationInput,
  ): Promise<OntologyDraftView>;
  updateDataSource(
    workspaceId: number,
    dataSourceId: number,
    expectedRevision: number,
    source: OntologyDataSourceMutationInput,
  ): Promise<OntologyDraftView>;
  discoverObjects(workspaceId: number, dataSourceId: number, expectedRevision: number): Promise<OntologyCatalogObjectMutation>;
  discoverFields(
    workspaceId: number,
    dataSourceId: number,
    objectKey: string,
    expectedRevision: number,
  ): Promise<OntologyCatalogFieldMutation>;
  getCatalog(workspaceId: number): Promise<OntologyCatalogView>;
  listMappings(workspaceId: number): Promise<OntologyMappingView[]>;
  replaceMappings(
    workspaceId: number,
    expectedRevision: number,
    mappings: OntologyMappingInput[],
  ): Promise<OntologyDraftView>;
  validateMappings(
    workspaceId: number,
    expectedRevision: number,
    mappings: OntologyMappingIdentityInput[],
  ): Promise<OntologyMappingValidationBatch>;
  compilePreview(workspaceId: number): Promise<OntologyCompilePreview>;
  publish(workspaceId: number, expectedRevision: number): Promise<OntologyVersionSummary>;
  listVersions(workspaceId: number): Promise<OntologyVersionSummary[]>;
  getVersion(workspaceId: number, version: number): Promise<OntologyVersionDetail>;
  explain(query: OntologySemanticQuery): Promise<OntologyQueryPlan>;
  execute(query: OntologySemanticQuery): Promise<OntologyQueryResult>;
}

function mutationKey(operation: string, body: unknown): string {
  return `${operation}:${JSON.stringify(body)}`;
}

function mappingWriteDto(mapping: OntologyMappingInput): OntologyMappingInput {
  return {
    targetType: mapping.targetType,
    targetKey: mapping.targetKey,
    dataSourceId: mapping.dataSourceId,
    physicalObjectKey: mapping.physicalObjectKey,
    physicalFieldKey: mapping.physicalFieldKey,
    relationTargetFieldKey: mapping.relationTargetFieldKey,
    transform: mapping.transform,
    confidence: mapping.confidence,
  };
}

function mappingIdentityDto(mapping: OntologyMappingIdentityInput): OntologyMappingIdentityInput {
  return {
    targetType: mapping.targetType,
    targetKey: mapping.targetKey,
    dataSourceId: mapping.dataSourceId,
  };
}

export function createOntologyApi(token: string, options: OntologyApiOptions = {}): OntologyApi {
  const fetchImpl = options.fetch ?? globalThis.fetch.bind(globalThis);
  const mutations = options.mutationLane ?? createOntologyMutationLane();
  const authToken = token.trim();

  async function request<T>(path: string, method = "GET", body?: unknown): Promise<T> {
    const headers: Record<string, string> = { Accept: "application/json" };
    if (authToken) headers.Authorization = `Bearer ${authToken}`;
    if (body !== undefined) headers["Content-Type"] = "application/json";
    let response: Response;
    try {
      response = await fetchImpl(path, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    } catch {
      throw new OntologyApiError(NETWORK_FAILURE_MESSAGE, 0, "HTTP_0", null, true);
    }
    let raw: string;
    try {
      raw = await response.text();
    } catch {
      throw normalizeOntologyApiError(
        response.status,
        response.statusText,
        null,
        isUncertainMutationStatus(response.status),
      );
    }
    const envelope = parseEnvelope(raw);
    if (!response.ok || envelope?.success !== true) {
      const outcomeUnknown = envelope?.success !== false && isUncertainMutationStatus(response.status);
      throw normalizeOntologyApiError(
        response.status,
        response.statusText,
        envelope,
        outcomeUnknown,
      );
    }
    return envelope.data as T;
  }

  function revisionMutation<T>(operation: string, body: unknown, action: () => Promise<T>): Promise<T> {
    return mutations.run(mutationKey(operation, body), action);
  }

  const api: OntologyApi = {
    listWorkspaces: () => request<OntologyWorkspaceView[]>(MANAGEMENT_ROOT),
    createWorkspace: (input) => request<OntologyWorkspaceView>(MANAGEMENT_ROOT, "POST", input),
    getWorkspace: (workspaceId) => request<OntologyWorkspaceView>(`${MANAGEMENT_ROOT}/${workspaceId}`),
    updateWorkspace: (workspaceId, input) => revisionMutation(
      `workspace:update:${workspaceId}`,
      input,
      () => request<OntologyWorkspaceView>(`${MANAGEMENT_ROOT}/${workspaceId}`, "PATCH", input),
    ),
    archiveWorkspace: (workspaceId, expectedRevision) => {
      const body = { expectedRevision };
      return revisionMutation(
        `workspace:archive:${workspaceId}`,
        body,
        () => request<OntologyWorkspaceView>(`${MANAGEMENT_ROOT}/${workspaceId}/archive`, "POST", body),
      );
    },
    getDraft: (workspaceId) => request<OntologyDraftView>(`${MANAGEMENT_ROOT}/${workspaceId}/draft`),
    saveDraft: (workspaceId, expectedRevision, document) => {
      const body = toDraftSaveRequest(expectedRevision, document);
      return revisionMutation(
        `draft:save:${workspaceId}`,
        body,
        () => request<OntologyDraftView>(`${MANAGEMENT_ROOT}/${workspaceId}/draft`, "PUT", body),
      );
    },
    validateDraft: (workspaceId) => request<OntologyValidationIssue[]>(
      `${MANAGEMENT_ROOT}/${workspaceId}/draft/validate`,
      "POST",
    ),
    diffDraft: (workspaceId) => request<OntologyDraftDiff>(`${MANAGEMENT_ROOT}/${workspaceId}/draft/diff`),
    createProposal: (workspaceId, body) => request<OntologyProposalRecord>(
      `${MANAGEMENT_ROOT}/${workspaceId}/proposals`,
      "POST",
      body,
    ),
    listProposals: (workspaceId) => request<OntologyProposalRecord[]>(`${MANAGEMENT_ROOT}/${workspaceId}/proposals`),
    getProposal: (workspaceId, proposalId) => request<OntologyProposalRecord>(
      `${MANAGEMENT_ROOT}/${workspaceId}/proposals/${proposalId}`,
    ),
    applyProposal: (workspaceId, proposalId, expectedRevision) => {
      const body = { expectedRevision };
      return revisionMutation(
        `proposal:apply:${workspaceId}:${proposalId}`,
        body,
        async () => {
          try {
            await request<OntologyProposalRecord>(
              `${MANAGEMENT_ROOT}/${workspaceId}/proposals/${proposalId}/apply`,
              "POST",
              body,
            );
          } catch (error) {
            if (isOntologyApiOutcomeUnknown(error)) {
              throw new OntologyProposalApplyOutcomeUnknownError();
            }
            throw error;
          }
          try {
            return await request<OntologyDraftView>(`${MANAGEMENT_ROOT}/${workspaceId}/draft`);
          } catch {
            throw new OntologyProposalAppliedReloadError();
          }
        },
      );
    },
    listReferencePackages: () => request<OntologyReferencePackageSummary[]>(`${MANAGEMENT_ROOT}/reference-packages`),
    getReferencePackage: (packageId) => request<OntologyReferencePackageView>(
      `${MANAGEMENT_ROOT}/reference-packages/${encodeURIComponent(packageId)}`,
    ),
    installReferencePackage: (packageId) => request<OntologyWorkspaceView>(
      `${MANAGEMENT_ROOT}/reference-packages/${encodeURIComponent(packageId)}/install`,
      "POST",
    ),
    listDataSources: (workspaceId) => request<OntologySourceView[]>(`${MANAGEMENT_ROOT}/${workspaceId}/data-sources`),
    createDataSource: (workspaceId, expectedRevision, source) => {
      const body = { expectedRevision, source };
      return revisionMutation(
        `source:create:${workspaceId}`,
        body,
        () => request<OntologyDraftView>(`${MANAGEMENT_ROOT}/${workspaceId}/data-sources`, "POST", body),
      );
    },
    updateDataSource: (workspaceId, dataSourceId, expectedRevision, source) => {
      const body = { expectedRevision, source };
      return revisionMutation(
        `source:update:${workspaceId}:${dataSourceId}`,
        body,
        () => request<OntologyDraftView>(
          `${MANAGEMENT_ROOT}/${workspaceId}/data-sources/${dataSourceId}`,
          "PUT",
          body,
        ),
      );
    },
    discoverObjects: (workspaceId, dataSourceId, expectedRevision) => {
      const body = { expectedRevision };
      return revisionMutation(
        `source:discover-objects:${workspaceId}:${dataSourceId}`,
        body,
        () => request<OntologyCatalogObjectMutation>(
          `${MANAGEMENT_ROOT}/${workspaceId}/data-sources/${dataSourceId}/discover-objects`,
          "POST",
          body,
        ),
      );
    },
    discoverFields: (workspaceId, dataSourceId, objectKey, expectedRevision) => {
      const body = { objectKey, expectedRevision };
      return revisionMutation(
        `source:discover-fields:${workspaceId}:${dataSourceId}`,
        body,
        () => request<OntologyCatalogFieldMutation>(
          `${MANAGEMENT_ROOT}/${workspaceId}/data-sources/${dataSourceId}/discover-fields`,
          "POST",
          body,
        ),
      );
    },
    getCatalog: (workspaceId) => request<OntologyCatalogView>(`${MANAGEMENT_ROOT}/${workspaceId}/catalog`),
    listMappings: (workspaceId) => request<OntologyMappingView[]>(`${MANAGEMENT_ROOT}/${workspaceId}/mappings`),
    replaceMappings: (workspaceId, expectedRevision, mappings) => {
      const body = { expectedRevision, mappings: mappings.map(mappingWriteDto) };
      return revisionMutation(
        `mapping:replace:${workspaceId}`,
        body,
        () => request<OntologyDraftView>(`${MANAGEMENT_ROOT}/${workspaceId}/mappings`, "PUT", body),
      );
    },
    validateMappings: (workspaceId, expectedRevision, mappings) => {
      const body = { expectedRevision, mappings: mappings.map(mappingIdentityDto) };
      return revisionMutation(
        `mapping:validate:${workspaceId}`,
        body,
        () => request<OntologyMappingValidationBatch>(
          `${MANAGEMENT_ROOT}/${workspaceId}/mappings/validate`,
          "POST",
          body,
        ),
      );
    },
    compilePreview: (workspaceId) => request<OntologyCompilePreview>(
      `${MANAGEMENT_ROOT}/${workspaceId}/compile-preview`,
      "POST",
    ),
    publish: (workspaceId, expectedRevision) => {
      const body = { expectedRevision };
      return revisionMutation(
        `workspace:publish:${workspaceId}`,
        body,
        async () => {
          try {
            return await request<OntologyVersionSummary>(`${MANAGEMENT_ROOT}/${workspaceId}/publish`, "POST", body);
          } catch (error) {
            if (isOntologyApiOutcomeUnknown(error)) {
              throw new OntologyPublishOutcomeUnknownError();
            }
            throw error;
          }
        },
      );
    },
    listVersions: (workspaceId) => request<OntologyVersionSummary[]>(`${MANAGEMENT_ROOT}/${workspaceId}/versions`),
    getVersion: (workspaceId, version) => request<OntologyVersionDetail>(
      `${MANAGEMENT_ROOT}/${workspaceId}/versions/${version}`,
    ),
    explain: (query) => request<OntologyQueryPlan>(`${QUERY_ROOT}/explain`, "POST", query),
    execute: (query) => request<OntologyQueryResult>(`${QUERY_ROOT}/execute`, "POST", query),
  };

  return api;
}
