import { describe, expect, it, vi } from "vitest";
import {
  createOntologyApi,
  isOntologyRevisionConflict,
  normalizeOntologyApiError,
  OntologyApiError,
  toDraftSaveRequest,
} from "./ontologyApi";
import {
  applyProposal,
  connectConcepts,
  createStableOntologyKey,
  createOntologyMutationLane,
  moveConcept,
  moveConceptByKeyboard,
  previewProposal,
  relationLine,
  selectOntologyItem,
} from "./ontologyModel";
import type {
  OntologyDocument,
  OntologyDraftView,
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
