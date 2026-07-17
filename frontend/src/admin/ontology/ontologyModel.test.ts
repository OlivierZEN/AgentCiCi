import { describe, expect, it, vi } from "vitest";
import {
  createOntologyApi,
  isOntologyRevisionConflict,
  OntologyProposalApplyOutcomeUnknownError,
  isOntologyProposalAppliedReloadError,
  normalizeOntologyApiError,
  OntologyApiError,
  OntologyPublishOutcomeUnknownError,
  toDraftSaveRequest,
} from "./ontologyApi";
import {
  applyProposal,
  connectConcepts,
  createStableOntologyKey,
  createOntologyMutationLane,
  findOntologyVersionForDraftRevision,
  hasUnvalidatedOntologyMappings,
  isOntologyOperationContextCurrent,
  moveConcept,
  moveConceptByKeyboard,
  previewProposal,
  relationLine,
  removeConceptProperty,
  shouldConfirmOntologyDraftDiscard,
  selectOntologyItem,
} from "./ontologyModel";
import { formatOntologyError } from "../pages/AdminOntologyPage";
import type {
  OntologyDocument,
  OntologyDraftView,
  OntologyMappingView,
  OntologyProposalRecord,
  OntologyRelation,
} from "./ontologyTypes";

function projectDeliveryDraft(): OntologyDocument {
  return {
    key: "project-delivery",
    name: "项目交付",
    description: "以项目、任务和负责人组织交付语义。",
    concepts: [
      {
        key: "project",
        name: "项目",
        pluralName: "项目",
        description: "需要交付的业务项目。",
        conceptType: "ENTITY",
        displayPropertyKey: "name",
        positionX: 80,
        positionY: 120,
        queryable: true,
        enabled: true,
        properties: [
          {
            key: "name",
            name: "项目名称",
            description: "项目的业务名称。",
            dataType: "TEXT",
            required: true,
            multiple: false,
            sensitive: false,
            queryable: true,
            enumValues: [],
          },
        ],
      },
      {
        key: "task",
        name: "任务",
        pluralName: "任务",
        description: "项目中的交付任务。",
        conceptType: "ENTITY",
        displayPropertyKey: "name",
        positionX: 320,
        positionY: 120,
        queryable: true,
        enabled: true,
        properties: [
          {
            key: "name",
            name: "任务名称",
            description: "任务的业务名称。",
            dataType: "TEXT",
            required: true,
            multiple: false,
            sensitive: false,
            queryable: true,
            enumValues: [],
          },
        ],
      },
    ],
    relations: [relation("project-has-task")],
    metrics: [],
    actions: [],
    dataSources: [
      {
        id: 17,
        key: "delivery-sample",
        name: "交付示例数据",
        type: "INLINE_SAMPLE",
      },
    ],
    mappings: [
      {
        targetType: "CONCEPT",
        targetKey: "project",
        dataSourceId: 17,
        physicalObjectKey: "projects",
        physicalFieldKey: null,
        relationTargetFieldKey: null,
        transform: null,
        confidence: 1,
        source: "MANUAL",
        validationStatus: "VALID",
      },
    ],
  };
}

function relation(key: string): OntologyRelation {
  return {
    key,
    name: "项目包含任务",
    description: "一个项目包含多个交付任务。",
    sourceConceptKey: "project",
    targetConceptKey: "task",
    cardinality: "ONE_TO_MANY",
    forwardLabel: "包含任务",
    reverseLabel: "属于项目",
    queryable: true,
    enabled: true,
  };
}

function proposal(): OntologyProposalRecord {
  const candidate = projectDeliveryDraft();
  candidate.concepts = [
    ...candidate.concepts,
    {
      key: "owner",
      name: "负责人",
      pluralName: "负责人",
      description: "承担交付任务的人员。",
      conceptType: "ENTITY",
      displayPropertyKey: "name",
      positionX: 560,
      positionY: 120,
      queryable: true,
      enabled: true,
      properties: [],
    },
  ];
  return {
    id: 31,
    workspaceId: 7,
    proposalType: "DOMAIN_FIRST",
    status: "READY",
    candidate,
    diff: {
      baseRevision: 4,
      candidateHash: "candidate-hash",
      added: ["concept:owner"],
      changed: [],
      removed: [],
    },
    validation: [],
    createdAt: "2026-07-17T01:00:00Z",
    updatedAt: "2026-07-17T01:00:00Z",
    appliedAt: null,
  };
}

function draftView(document = projectDeliveryDraft()): OntologyDraftView {
  return {
    workspace: {
      id: 7,
      key: document.key,
      name: document.name,
      description: document.description,
      status: "DRAFT",
      draftRevision: 4,
      publishedVersion: null,
      createdAt: "2026-07-17T00:00:00Z",
      updatedAt: "2026-07-17T01:00:00Z",
    },
    id: 7,
    key: document.key,
    status: "DRAFT",
    draftRevision: 4,
    publishedVersion: null,
    document,
    sources: [
      {
        id: 17,
        key: "delivery-sample",
        name: "交付示例数据",
        type: "INLINE_SAMPLE",
        adapterKey: null,
        status: "ACTIVE",
        lastValidatedAt: null,
        sample: { objectCount: 3, rowCount: 6, fieldValueCount: 18 },
      },
    ],
  };
}

function jsonResponse<T>(data: T, status = 200): Response {
  return new Response(JSON.stringify({ success: status < 400, data, message: status < 400 ? "OK" : "失败" }), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

describe("ontology immutable model", () => {
  it("calculates a relation line between node edges", () => {
    expect(relationLine(
      { x: 40, y: 40, width: 220, height: 112 },
      { x: 420, y: 180, width: 220, height: 112 },
    )).toEqual({ x1: 260, y1: 96, x2: 420, y2: 236 });
  });

  it("moves a concept without mutating the previous draft", () => {
    const previous = projectDeliveryDraft();

    const next = moveConcept(previous, "task", { x: 460, y: 180 });

    expect(next.concepts.find((item) => item.key === "task")?.positionX).toBe(460);
    expect(next.concepts.find((item) => item.key === "task")?.positionY).toBe(180);
    expect(previous.concepts.find((item) => item.key === "task")?.positionX).toBe(320);
    expect(next).not.toBe(previous);
  });

  it("moves a focused concept by eight pixels for each arrow key press", () => {
    const previous = projectDeliveryDraft();

    const right = moveConceptByKeyboard(previous, "task", "ArrowRight");
    const down = moveConceptByKeyboard(right, "task", "ArrowDown");

    expect(right.concepts.find((item) => item.key === "task")).toMatchObject({
      positionX: 328,
      positionY: 120,
    });
    expect(down.concepts.find((item) => item.key === "task")).toMatchObject({
      positionX: 328,
      positionY: 128,
    });
    expect(previous.concepts.find((item) => item.key === "task")).toMatchObject({
      positionX: 320,
      positionY: 120,
    });
  });

  it("creates a deterministic unused key from business labels and fallbacks", () => {
    expect(createStableOntologyKey(
      "Business Object",
      ["business-object", "business-object-2"],
    )).toBe("business-object-3");
    expect(createStableOntologyKey(
      "业务对象",
      ["concept", "concept-2"],
      "concept",
    )).toBe("concept-3");
  });

  it("changes selection without mutating the previous selection", () => {
    const previous = { kind: "concept" as const, key: "project" };

    const next = selectOntologyItem(previous, { kind: "relation", key: "project-has-task" });

    expect(next).toEqual({ kind: "relation", key: "project-has-task" });
    expect(next).not.toBe(previous);
    expect(previous).toEqual({ kind: "concept", key: "project" });
    expect(selectOntologyItem(next, null)).toBeNull();
  });

  it("connects existing concepts without mutating the previous relation list", () => {
    const previous = projectDeliveryDraft();
    const nextRelation = relation("task-owned-by-owner");
    nextRelation.sourceConceptKey = "task";
    nextRelation.targetConceptKey = "project";

    const next = connectConcepts(previous, nextRelation);

    expect(next.relations).toHaveLength(2);
    expect(previous.relations).toHaveLength(1);
  });

  it("rejects a second relation with the same stable key", () => {
    expect(() => connectConcepts(projectDeliveryDraft(), relation("project-has-task")))
      .toThrow("DUPLICATE_RELATION_KEY");
  });

  it("previews and applies a ready proposal only after an explicit model action", () => {
    const previous = projectDeliveryDraft();
    const readyProposal = proposal();

    const preview = previewProposal(previous, readyProposal);

    expect(preview.draftChanged).toBe(false);
    expect(preview.current).toBe(previous);
    expect(previous.concepts).toHaveLength(2);

    const applied = applyProposal(previous, readyProposal);
    expect(applied.concepts).toHaveLength(3);
    expect(applied).not.toBe(readyProposal.candidate);
    expect(previous.concepts).toHaveLength(2);
  });

  it("does not apply a failed proposal", () => {
    const failed = { ...proposal(), status: "FAILED" as const };
    expect(() => applyProposal(projectDeliveryDraft(), failed)).toThrow("AI_PROPOSAL_INVALID");
  });

  it("removes every metric, filter and mapping reference with a concept property", () => {
    const previous = projectDeliveryDraft();
    const task = previous.concepts.find((concept) => concept.key === "task");
    if (!task) throw new Error("task fixture is required");
    task.displayPropertyKey = "status";
    task.properties.push({
      key: "status",
      name: "任务状态",
      description: null,
      dataType: "ENUM",
      required: false,
      multiple: false,
      sensitive: false,
      queryable: true,
      enumValues: ["待开始", "进行中", "已完成"],
    });
    previous.metrics.push({
      key: "task-count",
      name: "任务数量",
      conceptKey: "task",
      aggregation: "COUNT",
      measurePropertyKey: "status",
      groupByPropertyKeys: ["status", "name"],
      timePropertyKey: "status",
      filters: [
        { property: "status", operator: "EQ", value: "进行中" },
        { property: "name", operator: "CONTAINS", value: "交付" },
      ],
    });
    previous.mappings.push({
      targetType: "PROPERTY",
      targetKey: "task.status",
      dataSourceId: 17,
      physicalObjectKey: "tasks",
      physicalFieldKey: "status",
      relationTargetFieldKey: null,
      transform: null,
      confidence: 1,
    });

    const next = removeConceptProperty(previous, "task", "status");

    expect(next.concepts.find((concept) => concept.key === "task")).toMatchObject({
      displayPropertyKey: null,
      properties: [{ key: "name" }],
    });
    expect(next.metrics[0]).toMatchObject({
      measurePropertyKey: null,
      groupByPropertyKeys: ["name"],
      timePropertyKey: null,
      filters: [{ property: "name" }],
    });
    expect(next.mappings.some((mapping) => mapping.targetKey === "task.status")).toBe(false);
    expect(previous.concepts.find((concept) => concept.key === "task")?.properties).toHaveLength(2);
  });

  it("uses authoritative mapping views after draft saves strip validation metadata", () => {
    const draftMapping = projectDeliveryDraft().mappings[0];
    const mappingWithoutServerFields = {
      ...draftMapping,
      source: undefined,
      validationStatus: undefined,
    };
    const validView: OntologyMappingView = {
      ...draftMapping,
      id: 301,
      source: "MANUAL",
      validationStatus: "VALID",
      lastValidatedAt: "2026-07-17T01:00:00Z",
    };

    expect(hasUnvalidatedOntologyMappings([mappingWithoutServerFields], [validView])).toBe(false);
    expect(hasUnvalidatedOntologyMappings([mappingWithoutServerFields], [])).toBe(true);
    expect(hasUnvalidatedOntologyMappings([mappingWithoutServerFields], [{
      ...validView,
      validationStatus: "STALE",
    }])).toBe(true);
  });

  it("rejects stale operation completions and protects local drafts before discard", () => {
    expect(isOntologyOperationContextCurrent({ epoch: 3, operationId: 8 }, 3, 8)).toBe(true);
    expect(isOntologyOperationContextCurrent({ epoch: 3, operationId: 8 }, 4, 8)).toBe(false);
    expect(isOntologyOperationContextCurrent({ epoch: 3, operationId: 8 }, 3, 9)).toBe(false);
    expect(shouldConfirmOntologyDraftDiscard(false, false)).toBe(false);
    expect(shouldConfirmOntologyDraftDiscard(true, false)).toBe(true);
    expect(shouldConfirmOntologyDraftDiscard(false, true)).toBe(true);
  });

  it("finds only the newest version created for a draft revision after a baseline", () => {
    const versions = [
      { version: 3, sourceDraftRevision: 4, contentHash: "v3", publishedBy: "u", publishedAt: "2026-07-17T03:00:00Z" },
      { version: 2, sourceDraftRevision: 5, contentHash: "v2", publishedBy: "u", publishedAt: "2026-07-17T02:00:00Z" },
      { version: 1, sourceDraftRevision: 4, contentHash: "v1", publishedBy: "u", publishedAt: "2026-07-17T01:00:00Z" },
    ];

    expect(findOntologyVersionForDraftRevision(versions, 4)?.version).toBe(3);
    expect(findOntologyVersionForDraftRevision(versions, 4, new Set([1, 2]))?.version).toBe(3);
    expect(findOntologyVersionForDraftRevision(versions, 4, new Set([1, 3]))).toBeUndefined();
  });
});

describe("ontology draft transport", () => {
  it("strips server-only and secret-bearing fields from a draft save DTO", () => {
    const document = projectDeliveryDraft();
    const unsafeSource = document.dataSources[0] as typeof document.dataSources[number] & {
      configJson: string;
      sampleDataJson: string;
    };
    unsafeSource.configJson = "{\"adapterKey\":\"private-adapter\"}";
    unsafeSource.sampleDataJson = "{\"projects\":[]}";

    const request = toDraftSaveRequest(4, document);

    expect(request.expectedRevision).toBe(4);
    expect(request.document.dataSources[0]).toEqual({
      id: 17,
      key: "delivery-sample",
      name: "交付示例数据",
      type: "INLINE_SAMPLE",
    });
    expect(request.document.mappings[0]).not.toHaveProperty("source");
    expect(request.document.mappings[0]).not.toHaveProperty("validationStatus");
  });

  it("preserves revision-conflict status, code and details", () => {
    const details = { expectedRevision: 4, actualRevision: 5 };
    const error = normalizeOntologyApiError(409, "Conflict", {
      success: false,
      data: null,
      message: "草稿已由其他编辑者更新",
      code: "ONTOLOGY_REVISION_CONFLICT",
      details,
    });

    expect(error.status).toBe(409);
    expect(error.code).toBe("ONTOLOGY_REVISION_CONFLICT");
    expect(error.details).toEqual(details);
    expect(isOntologyRevisionConflict(error)).toBe(true);
  });

  it("derives an error code from an uppercase message before falling back to HTTP", () => {
    expect(normalizeOntologyApiError(400, "Bad Request", {
      success: false,
      data: null,
      message: "ONTOLOGY_VALIDATION_FAILED",
    }).code).toBe("ONTOLOGY_VALIDATION_FAILED");

    expect(normalizeOntologyApiError(502, "Bad Gateway", null).code).toBe("HTTP_502");
  });

  it.each(["<script>", "lowercase", "HAS SPACES"])(
    "ignores an invalid explicit envelope code: %s",
    (invalidCode) => {
      const error = normalizeOntologyApiError(400, "Bad Request", {
        success: false,
        data: null,
        message: "ONTOLOGY_VALIDATION_FAILED",
        code: invalidCode,
        details: new Date(),
      });

      expect(error.code).toBe("ONTOLOGY_VALIDATION_FAILED");
      expect(error.message).toBe("ONTOLOGY_VALIDATION_FAILED");
      expect(error.message).not.toContain(invalidCode);
      expect(error.details).toBeNull();
    },
  );

  it("normalizes an initial fetch failure without exposing the transport error", async () => {
    const fetchStub = vi.fn(async () => {
      throw new TypeError("Failed to fetch with private request context");
    });
    const api = createOntologyApi("admin-token", { fetch: fetchStub as typeof fetch });

    const error = await api.listWorkspaces().catch((cause: unknown) => cause);

    expect(error).toBeInstanceOf(OntologyApiError);
    expect(error).toMatchObject({
      status: 0,
      code: "HTTP_0",
      message: "网络请求失败，请稍后重试",
      details: null,
    });
    expect((error as Error).message).not.toContain("private request context");
  });

  it("normalizes a response body read failure without exposing the body error", async () => {
    const response = {
      ok: false,
      status: 502,
      statusText: "Bad Gateway",
      text: vi.fn(async () => {
        throw new DOMException("private stream details", "AbortError");
      }),
    } as unknown as Response;
    const fetchStub = vi.fn(async () => response);
    const api = createOntologyApi("admin-token", { fetch: fetchStub as typeof fetch });

    const error = await api.listWorkspaces().catch((cause: unknown) => cause);

    expect(error).toBeInstanceOf(OntologyApiError);
    expect(error).toMatchObject({
      status: 502,
      code: "HTTP_502",
      message: "HTTP 502 Bad Gateway",
      details: null,
    });
    expect((error as Error).message).not.toContain("private stream details");
  });

  it("falls back to HTTP metadata when an error response contains invalid JSON", async () => {
    const fetchStub = vi.fn(async () => new Response("{not-json", {
      status: 500,
      statusText: "Internal Server Error",
    }));
    const api = createOntologyApi("admin-token", { fetch: fetchStub as typeof fetch });

    await expect(api.listWorkspaces()).rejects.toMatchObject({
      status: 500,
      code: "HTTP_500",
      message: "HTTP 500 Internal Server Error",
      details: null,
    });
  });

  it("ignores wrong-shape error fields and does not trust response JSON", async () => {
    const fetchStub = vi.fn(async () => new Response(JSON.stringify({
      success: false,
      data: null,
      message: { private: "do not expose" },
      code: 123,
      details: ["not", "an", "object"],
    }), {
      status: 422,
      statusText: "Unprocessable Entity",
      headers: { "Content-Type": "application/json" },
    }));
    const api = createOntologyApi("admin-token", { fetch: fetchStub as typeof fetch });

    await expect(api.listWorkspaces()).rejects.toMatchObject({
      status: 422,
      code: "HTTP_422",
      message: "HTTP 422 Unprocessable Entity",
      details: null,
    });
  });

  it("returns data from a successful envelope without applying error fallbacks", async () => {
    const fetchStub = vi.fn(async () => new Response(JSON.stringify({
      success: true,
      data: [{ id: 7, name: "项目交付" }],
      message: "OK",
    }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    }));
    const api = createOntologyApi("admin-token", { fetch: fetchStub as typeof fetch });

    await expect(api.listWorkspaces()).resolves.toEqual([{ id: 7, name: "项目交付" }]);
  });

  it("applies a proposal with expectedRevision and reloads the redacted draft", async () => {
    const calls: Array<{ url: string; init?: RequestInit }> = [];
    const candidate = proposal().candidate;
    if (!candidate) throw new Error("test proposal candidate is required");
    const reloaded = draftView(candidate);
    reloaded.draftRevision = 5;
    reloaded.workspace.draftRevision = 5;
    const fetchStub = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      calls.push({ url, init });
      if (url.endsWith("/apply")) return jsonResponse({ ...proposal(), status: "APPLIED" });
      return jsonResponse(reloaded);
    });
    const api = createOntologyApi("admin-token", { fetch: fetchStub as typeof fetch });

    const result = await api.applyProposal(7, 31, 4);

    expect(result.draftRevision).toBe(5);
    expect(calls.map((call) => call.url)).toEqual([
      "/admin/ontologies/7/proposals/31/apply",
      "/admin/ontologies/7/draft",
    ]);
    expect(JSON.parse(String(calls[0].init?.body))).toEqual({ expectedRevision: 4 });
    expect(calls[0].init?.headers).toMatchObject({
      Authorization: "Bearer admin-token",
      "Content-Type": "application/json",
    });
  });

  it("distinguishes an applied proposal when the mandatory draft reload fails", async () => {
    const fetchStub = vi.fn(async (input: RequestInfo | URL) => {
      if (String(input).endsWith("/apply")) {
        return jsonResponse({ ...proposal(), status: "APPLIED" });
      }
      return new Response(JSON.stringify({
        success: false,
        data: null,
        message: "draft reload unavailable",
        code: "HTTP_503",
      }), { status: 503, statusText: "Service Unavailable" });
    });
    const api = createOntologyApi("admin-token", { fetch: fetchStub as typeof fetch });

    const error = await api.applyProposal(7, 31, 4).catch((cause: unknown) => cause);

    expect(isOntologyProposalAppliedReloadError(error)).toBe(true);
    expect(fetchStub).toHaveBeenCalledTimes(2);
  });

  it("locks proposal apply when the POST outcome is unknown", async () => {
    const fetchStub = vi.fn(async () => {
      throw new TypeError("private transport details");
    });
    const api = createOntologyApi("admin-token", { fetch: fetchStub as typeof fetch });

    const error = await api.applyProposal(7, 31, 4).catch((cause: unknown) => cause);

    expect(error).toBeInstanceOf(OntologyProposalApplyOutcomeUnknownError);
    expect((error as Error).message).not.toContain("private transport details");
    expect(fetchStub).toHaveBeenCalledTimes(1);
  });

  it("locks publish when the POST response cannot confirm its outcome", async () => {
    const response = {
      ok: true,
      status: 200,
      statusText: "OK",
      text: vi.fn(async () => {
        throw new DOMException("private stream details", "AbortError");
      }),
    } as unknown as Response;
    const api = createOntologyApi("admin-token", {
      fetch: vi.fn(async () => response) as typeof fetch,
    });

    const error = await api.publish(7, 4).catch((cause: unknown) => cause);

    expect(error).toBeInstanceOf(OntologyPublishOutcomeUnknownError);
    expect((error as Error).message).not.toContain("private stream details");
  });

  it("preserves a confirmed publish conflict instead of treating it as ambiguous", async () => {
    const api = createOntologyApi("admin-token", {
      fetch: vi.fn(async () => new Response(JSON.stringify({
        success: false,
        message: "ONTOLOGY_REVISION_CONFLICT",
        code: "ONTOLOGY_REVISION_CONFLICT",
        details: { expectedRevision: 4, actualRevision: 5 },
      }), { status: 409, headers: { "Content-Type": "application/json" } })) as typeof fetch,
    });

    const error = await api.publish(7, 4).catch((cause: unknown) => cause);

    expect(error).toBeInstanceOf(OntologyApiError);
    expect(error).not.toBeInstanceOf(OntologyPublishOutcomeUnknownError);
    expect(error).toMatchObject({ status: 409, code: "ONTOLOGY_REVISION_CONFLICT" });
  });

  it("rebuilds replace-mapping writes from the exact allowed DTO fields", async () => {
    const calls: RequestInit[] = [];
    const fetchStub = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      calls.push(init ?? {});
      return jsonResponse(draftView());
    });
    const api = createOntologyApi("admin-token", { fetch: fetchStub as typeof fetch });
    const mapping = {
      id: 81,
      targetType: "PROPERTY",
      targetKey: "task.status",
      dataSourceId: 17,
      physicalObjectKey: "tasks",
      physicalFieldKey: "status",
      relationTargetFieldKey: null,
      transform: "TRIM",
      confidence: 0.92,
      source: "AI",
      validationStatus: "VALID",
      lastValidatedAt: "2026-07-17T01:00:00Z",
      unexpected: "must-not-cross-wire",
    } as OntologyMappingView & { unexpected: string };

    await api.replaceMappings(7, 4, [mapping]);

    expect(JSON.parse(String(calls[0].body))).toEqual({
      expectedRevision: 4,
      mappings: [{
        targetType: "PROPERTY",
        targetKey: "task.status",
        dataSourceId: 17,
        physicalObjectKey: "tasks",
        physicalFieldKey: "status",
        relationTargetFieldKey: null,
        transform: "TRIM",
        confidence: 0.92,
      }],
    });
  });

  it("rebuilds mapping-validation writes from exact identity fields", async () => {
    const calls: RequestInit[] = [];
    const fetchStub = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      calls.push(init ?? {});
      return jsonResponse({ revision: 5, results: [] });
    });
    const api = createOntologyApi("admin-token", { fetch: fetchStub as typeof fetch });
    const mapping = {
      id: 81,
      targetType: "PROPERTY",
      targetKey: "task.status",
      dataSourceId: 17,
      physicalObjectKey: "tasks",
      physicalFieldKey: "status",
      relationTargetFieldKey: null,
      transform: "TRIM",
      confidence: 0.92,
      source: "AI",
      validationStatus: "STALE",
      lastValidatedAt: null,
      unexpected: "must-not-cross-wire",
    } as OntologyMappingView & { unexpected: string };

    await api.validateMappings(7, 4, [mapping]);

    expect(JSON.parse(String(calls[0].body))).toEqual({
      expectedRevision: 4,
      mappings: [{
        targetType: "PROPERTY",
        targetKey: "task.status",
        dataSourceId: 17,
      }],
    });
  });
});

describe("ontology presentation errors", () => {
  it("shows the reload instruction only for the exact revision conflict", () => {
    expect(formatOntologyError(new OntologyApiError(
      "草稿修订冲突",
      409,
      "ONTOLOGY_REVISION_CONFLICT",
      { expectedRevision: 4, actualRevision: 5 },
    ))).toBe("草稿已被更新，请重新加载");

    expect(formatOntologyError(new OntologyApiError(
      "工作区状态不允许当前操作",
      409,
      "ONTOLOGY_STATE_CONFLICT",
      null,
    ))).toBe("ONTOLOGY_STATE_CONFLICT：工作区状态不允许当前操作");
  });

  it("explains mutation outcomes that must be reconciled before retry", () => {
    expect(formatOntologyError(new OntologyProposalApplyOutcomeUnknownError())).toBe(
      "提案应用结果未知；请重新加载草稿核对，勿重复应用。",
    );
    expect(formatOntologyError(new OntologyPublishOutcomeUnknownError())).toBe(
      "发布结果未知；请重新加载并检查版本历史，勿重复发布。",
    );
  });
});

describe("ontology mutation lane", () => {
  it("deduplicates the same in-flight mutation for StrictMode-safe autosave", async () => {
    const lane = createOntologyMutationLane();
    let release: (() => void) | undefined;
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    const operation = vi.fn(async () => {
      await gate;
      return "saved";
    });

    const first = lane.run("save:7:4:hash", operation);
    const duplicate = lane.run("save:7:4:hash", operation);

    expect(duplicate).toBe(first);
    await vi.waitFor(() => expect(operation).toHaveBeenCalledTimes(1));
    release?.();
    await expect(first).resolves.toBe("saved");
    await expect(duplicate).resolves.toBe("saved");
  });

  it("serializes different revision-advancing mutations through one lane", async () => {
    const lane = createOntologyMutationLane();
    const order: string[] = [];
    let releaseFirst: (() => void) | undefined;
    const firstGate = new Promise<void>((resolve) => {
      releaseFirst = resolve;
    });

    const first = lane.run("save:7:4:first", async () => {
      order.push("first:start");
      await firstGate;
      order.push("first:end");
    });
    const second = lane.run("publish:7:5", async () => {
      order.push("second:start");
    });

    await vi.waitFor(() => expect(order).toEqual(["first:start"]));
    releaseFirst?.();
    await Promise.all([first, second]);
    expect(order).toEqual(["first:start", "first:end", "second:start"]);
  });
});
