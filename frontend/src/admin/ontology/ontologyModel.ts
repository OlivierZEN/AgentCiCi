import type {
  OntologyDocument,
  OntologyDataSourceMutationInput,
  OntologyMappingInput,
  OntologyMapping,
  OntologyMappingView,
  OntologyProposalRecord,
  OntologyRelation,
  OntologyReferencePackageSummary,
  OntologySourceView,
  OntologyVersionSummary,
  OntologyWorkspaceView,
} from "./ontologyTypes";

export type OntologyPosition = { x: number; y: number };
export type OntologyRect = OntologyPosition & { width: number; height: number };
export type OntologyRelationLine = { x1: number; y1: number; x2: number; y2: number };
export type OntologySelectionKind = "concept" | "relation" | "metric" | "action";
export type OntologySelection = { kind: OntologySelectionKind; key: string } | null;
export type OntologyOperationContext = { epoch: number; operationId: number };
export type OntologyAsyncScope = {
  epoch: number;
  workspaceId: number | null;
  generation?: number;
};
export type OntologyEditableMapping = OntologyMappingInput & {
  clientKey: string;
  validationStatus: string;
};
export type OntologyCompilePreviewBinding = {
  draftRevision: number;
  publishedVersion: number | null;
  mappingSignature: string;
};

const KEYBOARD_MOVE_STEP = 8;

export function isOntologyOperationContextCurrent(
  context: OntologyOperationContext,
  currentEpoch: number,
  currentOperationId: number,
): boolean {
  return context.epoch === currentEpoch && context.operationId === currentOperationId;
}

export function shouldConfirmOntologyDraftDiscard(
  dirty: boolean,
  revisionLocked: boolean,
  mappingDirty = false,
): boolean {
  return dirty || revisionLocked || mappingDirty;
}

export function createOntologyAuthScopeKey(orgId: string, token: string): string {
  return JSON.stringify([orgId.trim(), token.trim()]);
}

export function isOntologyAsyncScopeCurrent(
  scope: OntologyAsyncScope,
  currentEpoch: number,
  currentWorkspaceId: number | null,
  currentGeneration?: number,
): boolean {
  return scope.epoch === currentEpoch
    && scope.workspaceId === currentWorkspaceId
    && (scope.generation === undefined || scope.generation === currentGeneration);
}

export function findOntologySourceByIdentity(
  sources: readonly OntologySourceView[],
  identity: Pick<OntologyDataSourceMutationInput, "key" | "name" | "type">,
): OntologySourceView | undefined {
  return sources.find((source) => (
    source.key === identity.key
    && source.name === identity.name
    && source.type === identity.type
  ));
}

export function findOntologyWorkspaceByCreateIdentity(
  workspaces: readonly OntologyWorkspaceView[],
  identity: Pick<OntologyWorkspaceView, "key" | "name" | "description" | "createdBy">,
): OntologyWorkspaceView | undefined {
  return workspaces.find((workspace) => (
    workspace.key === identity.key
    && workspace.name === identity.name
    && workspace.description === identity.description
    && workspace.createdBy === identity.createdBy
  ));
}

export function findOntologyWorkspaceByReferencePackageIdentity(
  workspaces: readonly OntologyWorkspaceView[],
  referencePackage: OntologyReferencePackageSummary,
  createdBy: string,
): OntologyWorkspaceView | undefined {
  return findOntologyWorkspaceByCreateIdentity(workspaces, {
    ...referencePackage.workspaceIdentity,
    createdBy,
  });
}

export function toEditableOntologyMappings(
  mappings: readonly OntologyMappingView[],
): OntologyEditableMapping[] {
  return mappings.map((mapping, index) => ({
    clientKey: `mapping-${mapping.id ?? index}`,
    targetType: mapping.targetType,
    targetKey: mapping.targetKey,
    dataSourceId: mapping.dataSourceId,
    physicalObjectKey: mapping.physicalObjectKey,
    physicalFieldKey: mapping.physicalFieldKey,
    relationTargetFieldKey: mapping.relationTargetFieldKey,
    transform: mapping.transform,
    confidence: mapping.confidence,
    validationStatus: mapping.validationStatus || "PENDING",
  }));
}

export function toOntologyMappingInputs(
  mappings: readonly OntologyEditableMapping[],
): OntologyMappingInput[] {
  return mappings.map((mapping) => ({
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

export function ontologyMappingSignature(mappings: readonly OntologyMappingView[]): string {
  return JSON.stringify(mappings.map((mapping) => ({
    targetType: mapping.targetType,
    targetKey: mapping.targetKey,
    dataSourceId: mapping.dataSourceId,
    physicalObjectKey: mapping.physicalObjectKey,
    physicalFieldKey: mapping.physicalFieldKey,
    relationTargetFieldKey: mapping.relationTargetFieldKey,
    transform: mapping.transform,
    confidence: mapping.confidence,
    validationStatus: mapping.validationStatus || "PENDING",
  })).sort((first, second) => JSON.stringify(first).localeCompare(JSON.stringify(second))));
}

export function createOntologyCompilePreviewBinding(
  draftRevision: number,
  mappings: readonly OntologyMappingView[],
  publishedVersion: number | null = null,
): OntologyCompilePreviewBinding {
  return {
    draftRevision,
    publishedVersion,
    mappingSignature: ontologyMappingSignature(mappings),
  };
}

export function isOntologyCompilePreviewBindingCurrent(
  binding: OntologyCompilePreviewBinding | null,
  draftRevision: number,
  mappings: readonly OntologyMappingView[],
  dirty: boolean,
  mappingDirty: boolean,
  publishedVersion: number | null = null,
): boolean {
  return binding !== null
    && !dirty
    && !mappingDirty
    && binding.draftRevision === draftRevision
    && binding.publishedVersion === publishedVersion
    && binding.mappingSignature === ontologyMappingSignature(mappings);
}

export function isOntologyCompilePreviewResponseBound(
  binding: OntologyCompilePreviewBinding,
  sourceDraftRevision: number,
  candidateVersion: number,
): boolean {
  return sourceDraftRevision === binding.draftRevision
    && candidateVersion === (binding.publishedVersion ?? 0) + 1;
}

export function nextOntologyTabIndex(
  currentIndex: number,
  tabCount: number,
  key: string,
): number | null {
  if (tabCount <= 0) return null;
  if (key === "ArrowRight") return (currentIndex + 1) % tabCount;
  if (key === "ArrowLeft") return (currentIndex - 1 + tabCount) % tabCount;
  if (key === "Home") return 0;
  if (key === "End") return tabCount - 1;
  return null;
}

export function findOntologyVersionForDraftRevision(
  versions: readonly OntologyVersionSummary[],
  sourceDraftRevision: number,
  excludedVersions: ReadonlySet<number> = new Set<number>(),
): OntologyVersionSummary | undefined {
  return versions.reduce<OntologyVersionSummary | undefined>((latest, version) => {
    if (version.sourceDraftRevision !== sourceDraftRevision || excludedVersions.has(version.version)) return latest;
    return !latest || version.version > latest.version ? version : latest;
  }, undefined);
}

export type OntologyProposalPreview = {
  current: OntologyDocument;
  candidate: OntologyDocument;
  diff: NonNullable<OntologyProposalRecord["diff"]>;
  draftChanged: false;
};

export interface OntologyMutationLane {
  run<T>(key: string, operation: () => Promise<T>): Promise<T>;
}

function cloneUnknown(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(cloneUnknown);
  if (value && typeof value === "object") {
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>).map(([key, entry]) => [key, cloneUnknown(entry)]),
    );
  }
  return value;
}

export function cloneOntologyDocument(document: OntologyDocument): OntologyDocument {
  return {
    ...document,
    concepts: document.concepts.map((concept) => ({
      ...concept,
      properties: concept.properties.map((property) => ({
        ...property,
        enumValues: [...property.enumValues],
      })),
    })),
    relations: document.relations.map((relation) => ({ ...relation })),
    metrics: document.metrics.map((metric) => ({
      ...metric,
      groupByPropertyKeys: [...metric.groupByPropertyKeys],
      filters: metric.filters.map((filter) => ({
        ...filter,
        value: cloneUnknown(filter.value),
      })),
    })),
    actions: document.actions.map((action) => ({
      ...action,
      parameters: action.parameters.map((parameter) => ({ ...parameter })),
    })),
    dataSources: document.dataSources.map((source) => ({ ...source })),
    mappings: document.mappings.map((mapping) => ({ ...mapping })),
  };
}

export function moveConcept(
  document: OntologyDocument,
  conceptKey: string,
  position: OntologyPosition,
): OntologyDocument {
  if (!Number.isFinite(position.x) || !Number.isFinite(position.y)) {
    throw new Error("ONTOLOGY_POSITION_INVALID");
  }
  if (!document.concepts.some((concept) => concept.key === conceptKey)) {
    throw new Error("ONTOLOGY_CONCEPT_NOT_FOUND");
  }
  return {
    ...document,
    concepts: document.concepts.map((concept) => concept.key === conceptKey
      ? { ...concept, positionX: position.x, positionY: position.y }
      : concept),
  };
}

export function moveConceptByKeyboard(
  document: OntologyDocument,
  conceptKey: string,
  key: string,
): OntologyDocument {
  const delta = key === "ArrowLeft"
    ? { x: -KEYBOARD_MOVE_STEP, y: 0 }
    : key === "ArrowRight"
      ? { x: KEYBOARD_MOVE_STEP, y: 0 }
      : key === "ArrowUp"
        ? { x: 0, y: -KEYBOARD_MOVE_STEP }
        : key === "ArrowDown"
          ? { x: 0, y: KEYBOARD_MOVE_STEP }
          : null;
  if (!delta) return document;
  const concept = document.concepts.find((item) => item.key === conceptKey);
  if (!concept) throw new Error("ONTOLOGY_CONCEPT_NOT_FOUND");
  return moveConcept(document, conceptKey, {
    x: concept.positionX + delta.x,
    y: concept.positionY + delta.y,
  });
}

export function relationLine(source: OntologyRect, target: OntologyRect): OntologyRelationLine {
  const sourceCenter = {
    x: source.x + source.width / 2,
    y: source.y + source.height / 2,
  };
  const targetCenter = {
    x: target.x + target.width / 2,
    y: target.y + target.height / 2,
  };
  const horizontal = Math.abs(targetCenter.x - sourceCenter.x) >= Math.abs(targetCenter.y - sourceCenter.y);
  if (horizontal) {
    const targetIsRight = targetCenter.x >= sourceCenter.x;
    return {
      x1: targetIsRight ? source.x + source.width : source.x,
      y1: sourceCenter.y,
      x2: targetIsRight ? target.x : target.x + target.width,
      y2: targetCenter.y,
    };
  }
  const targetIsBelow = targetCenter.y >= sourceCenter.y;
  return {
    x1: sourceCenter.x,
    y1: targetIsBelow ? source.y + source.height : source.y,
    x2: targetCenter.x,
    y2: targetIsBelow ? target.y : target.y + target.height,
  };
}

function normalizeStableKey(value: string): string {
  return value
    .normalize("NFKD")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

export function createStableOntologyKey(
  label: string,
  existingKeys: readonly string[],
  fallback = "item",
): string {
  const base = normalizeStableKey(label) || normalizeStableKey(fallback) || "item";
  const occupied = new Set(existingKeys);
  if (!occupied.has(base)) return base;
  let suffix = 2;
  while (occupied.has(`${base}-${suffix}`)) suffix += 1;
  return `${base}-${suffix}`;
}

export function selectOntologyItem(
  _current: OntologySelection,
  next: OntologySelection,
): OntologySelection {
  return next ? { ...next } : null;
}

export function removeConceptProperty(
  document: OntologyDocument,
  conceptKey: string,
  propertyKey: string,
): OntologyDocument {
  const concept = document.concepts.find((item) => item.key === conceptKey);
  if (!concept) throw new Error("ONTOLOGY_CONCEPT_NOT_FOUND");
  if (!concept.properties.some((property) => property.key === propertyKey)) {
    throw new Error("ONTOLOGY_PROPERTY_NOT_FOUND");
  }

  const mappingTargetKey = `${conceptKey}.${propertyKey}`;
  return {
    ...document,
    concepts: document.concepts.map((item) => item.key === conceptKey
      ? {
        ...item,
        displayPropertyKey: item.displayPropertyKey === propertyKey ? null : item.displayPropertyKey,
        properties: item.properties.filter((property) => property.key !== propertyKey),
      }
      : item),
    metrics: document.metrics.map((metric) => metric.conceptKey === conceptKey
      ? {
        ...metric,
        measurePropertyKey: metric.measurePropertyKey === propertyKey ? null : metric.measurePropertyKey,
        groupByPropertyKeys: metric.groupByPropertyKeys.filter((key) => key !== propertyKey),
        timePropertyKey: metric.timePropertyKey === propertyKey ? null : metric.timePropertyKey,
        filters: metric.filters.filter((filter) => filter.property !== propertyKey),
      }
      : metric),
    mappings: document.mappings.filter((mapping) => mapping.targetKey !== mappingTargetKey),
  };
}

function ontologyMappingIdentity(mapping: Pick<OntologyMapping, "targetType" | "targetKey" | "dataSourceId">): string {
  return `${mapping.targetType}\u0000${mapping.targetKey}\u0000${mapping.dataSourceId}`;
}

export function hasUnvalidatedOntologyMappings(
  draftMappings: readonly OntologyMapping[],
  mappingViews: readonly OntologyMappingView[],
): boolean {
  if (mappingViews.some((mapping) => mapping.validationStatus !== "VALID")) return true;
  if (draftMappings.length === 0) return false;

  const validatedIdentities = new Set(
    mappingViews
      .filter((mapping) => mapping.validationStatus === "VALID")
      .map(ontologyMappingIdentity),
  );
  return draftMappings.some((mapping) => !validatedIdentities.has(ontologyMappingIdentity(mapping)));
}

export function connectConcepts(
  document: OntologyDocument,
  relation: OntologyRelation,
): OntologyDocument {
  if (document.relations.some((item) => item.key === relation.key)) {
    throw new Error("DUPLICATE_RELATION_KEY");
  }
  const conceptKeys = new Set(document.concepts.map((concept) => concept.key));
  if (!conceptKeys.has(relation.sourceConceptKey) || !conceptKeys.has(relation.targetConceptKey)) {
    throw new Error("RELATION_CONCEPT_NOT_FOUND");
  }
  return {
    ...document,
    relations: [...document.relations, { ...relation }],
  };
}

function requireReadyProposal(proposal: OntologyProposalRecord): asserts proposal is OntologyProposalRecord & {
  candidate: OntologyDocument;
  diff: NonNullable<OntologyProposalRecord["diff"]>;
} {
  if (proposal.status !== "READY" || !proposal.candidate || !proposal.diff) {
    throw new Error("AI_PROPOSAL_INVALID");
  }
}

export function previewProposal(
  current: OntologyDocument,
  proposal: OntologyProposalRecord,
): OntologyProposalPreview {
  requireReadyProposal(proposal);
  return {
    current,
    candidate: cloneOntologyDocument(proposal.candidate),
    diff: {
      ...proposal.diff,
      added: [...proposal.diff.added],
      changed: [...proposal.diff.changed],
      removed: [...proposal.diff.removed],
    },
    draftChanged: false,
  };
}

export function applyProposal(
  _current: OntologyDocument,
  proposal: OntologyProposalRecord,
): OntologyDocument {
  requireReadyProposal(proposal);
  return cloneOntologyDocument(proposal.candidate);
}

export function createOntologyMutationLane(): OntologyMutationLane {
  let tail: Promise<void> = Promise.resolve();
  const inFlight = new Map<string, Promise<unknown>>();

  return {
    run<T>(key: string, operation: () => Promise<T>): Promise<T> {
      const existing = inFlight.get(key);
      if (existing) return existing as Promise<T>;

      const scheduled = tail.then(operation, operation);
      inFlight.set(key, scheduled);
      tail = scheduled.then(() => undefined, () => undefined);
      const cleanup = () => {
        if (inFlight.get(key) === scheduled) inFlight.delete(key);
      };
      void scheduled.then(cleanup, cleanup);
      return scheduled;
    },
  };
}
