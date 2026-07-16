import type {
  OntologyDocument,
  OntologyProposalRecord,
  OntologyRelation,
} from "./ontologyTypes";

export type OntologyPosition = { x: number; y: number };

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
