import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useAsrVoiceInput } from "../../shared/useAsrVoiceInput";
import { safeFetchJson } from "../../utils/http";
import { useAdminToken } from "../useAdminToken";
import {
  CRM_TEMPLATES,
  EMPTY_FORM,
  downloadSkillExportPackage,
  type GeneratedSkillSpec,
  joinCsv,
  type Skill,
  type SkillAuthoringCreateResult,
  type SkillAuthoringResult,
  type SkillExportJob,
  type SkillForm,
  type SkillPreview,
  type SkillRuntimeApi,
  type SkillRuntimeApiForm,
  type SkillTemplate,
  type SkillVersion,
  riskBadgeClass,
  riskLabel,
  skillSourceLabel,
  splitCsv,
} from "../skills/skillStudioShared";

function HelpTip(props: { text: string; className?: string }) {
  return (
    <span className={`skills-help-tip ${props.className ?? ""}`} tabIndex={0} aria-label={props.text}>
      <span className="skills-help-tip__icon" aria-hidden>
        ?
      </span>
      <span className="skills-help-tip__bubble" role="tooltip">
        {props.text}
      </span>
    </span>
  );
}

function formatTs(iso: string | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString("zh-CN", { dateStyle: "short", timeStyle: "short" });
}

function BilingualField(props: {
  titleZh: string;
  titleEn: string;
  hintZh: string;
  hintEn: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <label className={`skill-bilingual-field ${props.className ?? ""}`}>
      <span className="skill-bilingual-field__titles">
        <span className="skill-bilingual-field__zh">{props.titleZh}</span>
        <HelpTip text={props.hintZh} />
      </span>
      {props.children}
    </label>
  );
}

type BaseModelOption = {
  value: string; // providerCode::modelName
  label: string;
};

type KnowledgeBase = {
  id: number;
  name: string;
  description: string;
  status: string;
};

type ToolCatalogItem = {
  id: string;
  name: string;
  description: string;
  level: string;
};

type McpServerSummary = {
  id: number;
  name: string;
  enabled: boolean;
  toolCacheCount: number;
};

type McpDiscoveredTool = {
  name: string;
  description?: string;
};

type McpToolCachePayload = {
  cacheStatus?: string;
  toolCount?: number;
  cacheUpdatedAt?: string;
  cacheErrorMessage?: string;
  tools?: McpDiscoveredTool[];
};

type McpPickerTool = {
  id: string;
  name: string;
  description: string;
  level: string;
};

type WhitelistPickerType = "tool" | "kb";

type SkillEditorTab = "basic" | "promptFragment" | "draftSpec" | "boundaries" | "runtimeApis" | "versions" | "preview";

type AuthoringRestoreSnapshot = {
  form: SkillForm;
  preview: SkillPreview | null;
  authoringPrompt: string;
  authoringResult: SkillAuthoringResult | null;
  authoringSessionId: string | null;
  clarificationAnswersByQuestion: Record<string, string>;
};

const EDITOR_TABS: Array<{ key: SkillEditorTab; label: string }> = [
  { key: "basic", label: "基础信息" },
  { key: "promptFragment", label: "提示片段" },
  { key: "draftSpec", label: "规格正文" },
  { key: "boundaries", label: "边界规则" },
  { key: "runtimeApis", label: "内嵌 API" },
  { key: "preview", label: "编译预览" },
  { key: "versions", label: "版本管理" },
];

const RUNTIME_API_INPUT_SCHEMA = JSON.stringify({
  type: "object",
  properties: {
    keyword: {
      type: "string",
      description: "搜索关键词",
    },
  },
  required: ["keyword"],
}, null, 2);

const RUNTIME_API_REQUEST = JSON.stringify({
  headers: {
    "Content-Type": "application/json",
  },
  body: {
    q: "{{keyword}}",
  },
}, null, 2);

const RUNTIME_API_RESPONSE = JSON.stringify({
  resultPath: "$.data",
  maxItems: 20,
  maxBytes: 12000,
  redactPaths: ["$..token", "$..password"],
}, null, 2);

function newRuntimeApiForm(): SkillRuntimeApiForm {
  return {
    apiCode: "",
    displayName: "",
    description: "",
    riskLevel: "LOW",
    method: "POST",
    url: "",
    authRef: "",
    timeoutSeconds: "10",
    confirmationRequired: false,
    inputSchemaText: RUNTIME_API_INPUT_SCHEMA,
    requestText: RUNTIME_API_REQUEST,
    responseText: RUNTIME_API_RESPONSE,
  };
}

function stringifyApiSection(value: unknown, fallback: string): string {
  if (!value || typeof value !== "object" || Array.isArray(value)) return fallback;
  return JSON.stringify(value, null, 2);
}

function runtimeApiToForm(api: SkillRuntimeApi): SkillRuntimeApiForm {
  return {
    apiCode: api.apiCode ?? "",
    displayName: api.displayName ?? "",
    description: api.description ?? "",
    riskLevel: ["LOW", "MEDIUM", "HIGH"].includes(String(api.riskLevel)) ? (api.riskLevel as SkillRuntimeApiForm["riskLevel"]) : "LOW",
    method: (api.method ?? "POST").toUpperCase(),
    url: api.url ?? "",
    authRef: api.authRef ?? "",
    timeoutSeconds: api.timeoutSeconds ? String(api.timeoutSeconds) : "10",
    confirmationRequired: Boolean(api.confirmationRequired),
    inputSchemaText: stringifyApiSection(api.inputSchema, RUNTIME_API_INPUT_SCHEMA),
    requestText: stringifyApiSection(api.request, RUNTIME_API_REQUEST),
    responseText: stringifyApiSection(api.response, RUNTIME_API_RESPONSE),
  };
}

function editPolicyLabel(policy: Skill["editPolicy"]): string {
  if (policy === "EDITABLE") return "可编辑";
  if (policy === "CONFIGURABLE") return "仅可配置";
  return "只读";
}

function bindingPolicyLabel(policy: Skill["bindingPolicy"]): string {
  if (policy === "MANDATORY") return "强制绑定";
  if (policy === "DEFAULT_ON") return "默认启用";
  if (policy === "INTERNAL_ONLY") return "内部使用";
  return "可选绑定";
}

function templateSceneLabel(scene: string): string {
  if (scene === "Lead Qualification") return "线索分诊";
  if (scene === "Pipeline Health") return "商机健康";
  if (scene === "Follow-up Rhythm") return "跟进节奏";
  if (scene === "Renewal Defense") return "续约风险";
  return scene;
}

export default function AdminSkillComposePage() {
  const token = useAdminToken();
  const nav = useNavigate();
  const { skillId: skillIdParam } = useParams();
  const skillId = skillIdParam ? Number.parseInt(skillIdParam, 10) : NaN;

  const [notice, setNotice] = useState("");
  const [existingCodes, setExistingCodes] = useState<Set<string>>(new Set());
  const [form, setForm] = useState<SkillForm>(EMPTY_FORM);
  const [preview, setPreview] = useState<SkillPreview | null>(null);
  const [authoringPrompt, setAuthoringPrompt] = useState("");
  const [authoringResult, setAuthoringResult] = useState<SkillAuthoringResult | null>(null);
  const [authoringParsing, setAuthoringParsing] = useState(false);
  const [authoringSessionId, setAuthoringSessionId] = useState<string | null>(null);
  const [clarificationAnswersByQuestion, setClarificationAnswersByQuestion] = useState<Record<string, string>>({});
  const [authoringRestoreSnapshot, setAuthoringRestoreSnapshot] = useState<AuthoringRestoreSnapshot | null>(null);
  const [modelOptions, setModelOptions] = useState<BaseModelOption[]>([]);
  const [selectedModel, setSelectedModel] = useState("");
  const [activeEditorTab, setActiveEditorTab] = useState<SkillEditorTab>("promptFragment");
  const [versions, setVersions] = useState<SkillVersion[]>([]);
  const [kbs, setKbs] = useState<KnowledgeBase[]>([]);
  const [toolCatalog, setToolCatalog] = useState<ToolCatalogItem[]>([]);
  const [mcpServers, setMcpServers] = useState<McpServerSummary[]>([]);
  const [mcpToolsByServer, setMcpToolsByServer] = useState<Record<number, McpPickerTool[]>>({});
  const [mcpServerLoading, setMcpServerLoading] = useState<Record<number, boolean>>({});
  const [expandedMcpServerIds, setExpandedMcpServerIds] = useState<number[]>([]);
  const [whitelistPickerOpen, setWhitelistPickerOpen] = useState<WhitelistPickerType | null>(null);
  const [whitelistPickerSelection, setWhitelistPickerSelection] = useState<string[]>([]);
  const [pickerToolTab, setPickerToolTab] = useState<"tool" | "mcp">("tool");
  const [busy, setBusy] = useState(false);
  const [publishDialogOpen, setPublishDialogOpen] = useState(false);
  const [publishChangeLog, setPublishChangeLog] = useState("");
  const [libraryModalOpen, setLibraryModalOpen] = useState(false);
  const [loadError, setLoadError] = useState("");
  const { listening: asrListening, speechSupported, start: startAsrSession, stop: stopAsrSession } = useAsrVoiceInput();
  const isEditRoute = Number.isFinite(skillId) && skillId >= 1;
  const [skillLoaded, setSkillLoaded] = useState(() => !isEditRoute);

  const parsePreferredModel = () => {
    const [providerCode, ...rest] = selectedModel.split("::");
    const modelName = rest.join("::").trim();
    const provider = providerCode?.trim();
    return {
      preferredModel: modelName || undefined,
      preferredProvider: provider || undefined,
    };
  };

  useEffect(() => {
    if (Number.isFinite(skillId) && skillId >= 1) {
      setSkillLoaded(false);
    } else {
      setSkillLoaded(true);
    }
  }, [skillId]);

  const authHeaders = useMemo(
    () => ({
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    }),
    [token],
  );

  const flash = (msg: string) => {
    setNotice(msg);
    window.setTimeout(() => setNotice(""), 3200);
  };

  const parseRuntimeApiObject = (raw: string, label: string, index: number): Record<string, unknown> | null => {
    try {
      const parsed = JSON.parse(raw || "{}") as unknown;
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
        flash(`第 ${index + 1} 个 API 的${label}必须是 JSON 对象`);
        return null;
      }
      return parsed as Record<string, unknown>;
    } catch {
      flash(`第 ${index + 1} 个 API 的${label}不是合法 JSON`);
      return null;
    }
  };

  const buildRuntimeApisPayload = (draft: SkillForm): SkillRuntimeApi[] | null => {
    const payload: SkillRuntimeApi[] = [];
    for (let index = 0; index < draft.runtimeApis.length; index += 1) {
      const api = draft.runtimeApis[index];
      const hasAnyValue = [
        api.apiCode,
        api.displayName,
        api.description,
        api.url,
        api.authRef,
        api.inputSchemaText,
        api.requestText,
        api.responseText,
      ].some((value) => value.trim());
      if (!hasAnyValue) continue;
      if (!api.apiCode.trim() || !api.displayName.trim() || !api.description.trim() || !api.url.trim()) {
        flash(`第 ${index + 1} 个内嵌 API 缺少编码、名称、描述或 URL`);
        return null;
      }
      const inputSchema = parseRuntimeApiObject(api.inputSchemaText, "参数 schema", index);
      const request = parseRuntimeApiObject(api.requestText, "请求映射", index);
      const response = parseRuntimeApiObject(api.responseText, "返回映射", index);
      if (!inputSchema || !request || !response) return null;
      payload.push({
        apiCode: api.apiCode.trim(),
        displayName: api.displayName.trim(),
        description: api.description.trim(),
        riskLevel: api.riskLevel,
        triggerMode: "model_decide",
        method: api.method.trim().toUpperCase(),
        url: api.url.trim(),
        authRef: api.authRef.trim() || undefined,
        timeoutSeconds: Math.max(1, Math.min(30, Number.parseInt(api.timeoutSeconds, 10) || 10)),
        confirmationRequired: api.riskLevel === "HIGH" ? true : api.confirmationRequired,
        inputSchema,
        request,
        response,
      });
    }
    return payload;
  };

  const loadExistingCodes = useCallback(async () => {
    const res = await fetch("/skills", { headers: { Authorization: `Bearer ${token}` } });
    const json = await res.json();
    if (!res.ok || !json.success) return;
    const list = (json.data ?? []) as Skill[];
    setExistingCodes(new Set(list.map((s) => s.skillCode)));
  }, [token]);

  useEffect(() => {
    void loadExistingCodes();
  }, [loadExistingCodes]);

  useEffect(() => {
    let cancelled = false;
    const loadAgentBaseModels = async () => {
      try {
        const res = await fetch("/models/agent/base-models", { headers: { Authorization: `Bearer ${token}` } });
        const json = await res.json();
        if (!res.ok || !json.success || !Array.isArray(json.data)) {
          if (!cancelled) {
            setModelOptions([]);
            setSelectedModel("");
          }
          return;
        }
        const mapped = (json.data as Array<{ providerCode: string; providerName: string; modelName: string; displayLabel?: string }>)
          .map((item) => ({
            value: `${item.providerCode}::${item.modelName}`,
            label: item.displayLabel || `${item.modelName} · ${item.providerName}`,
          }))
          .filter((item) => item.value && item.label);
        const deduped = Array.from(new Map(mapped.map((item) => [item.value, item])).values());
        if (cancelled) return;
        setModelOptions(deduped);
        setSelectedModel((current) => (deduped.some((option) => option.value === current) ? current : ""));
      } catch {
        if (!cancelled) {
          setModelOptions([]);
          setSelectedModel("");
        }
      }
    };
    void loadAgentBaseModels();
    return () => {
      cancelled = true;
    };
  }, [token]);

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    const loadKbs = async () => {
      try {
        const response = await fetch("/kb", { headers: { Authorization: `Bearer ${token}` } });
        const { body } = await safeFetchJson<KnowledgeBase[]>(response);
        if (!response.ok || !body?.success || !Array.isArray(body.data)) return;
        if (!cancelled) setKbs(body.data);
      } catch {
        if (!cancelled) setKbs([]);
      }
    };
    void loadKbs();
    return () => {
      cancelled = true;
    };
  }, [token]);

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    const loadToolCatalog = async () => {
      try {
        const response = await fetch("/tools", { headers: { Authorization: `Bearer ${token}` } });
        const { body } = await safeFetchJson<Array<{
          toolName: string;
          displayName?: string;
          description?: string;
          riskLevel?: string;
        }>>(response);
        if (!response.ok || !body?.success || !Array.isArray(body.data)) return;
        const next = body.data
          .map((item) => ({
            id: item.toolName,
            name: item.displayName || item.toolName,
            description: item.description || "",
            level: item.riskLevel || "未知风险",
          }))
          .filter((item) => item.id);
        if (!cancelled) setToolCatalog(next);
      } catch {
        if (!cancelled) setToolCatalog([]);
      }
    };
    void loadToolCatalog();
    return () => {
      cancelled = true;
    };
  }, [token]);

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    const loadMcpServers = async () => {
      try {
        const response = await fetch("/mcp-servers", { headers: { Authorization: `Bearer ${token}` } });
        const { body } = await safeFetchJson<Array<{ id: number; name: string; enabled?: boolean; toolCacheCount?: number }>>(response);
        if (!response.ok || !body?.success || !Array.isArray(body.data)) {
          if (!cancelled) setMcpServers([]);
          return;
        }
        if (!cancelled) {
          setMcpServers(body.data.map((item) => ({
            id: item.id,
            name: item.name || `MCP-${item.id}`,
            enabled: item.enabled !== false,
            toolCacheCount: Number.isFinite(item.toolCacheCount) ? Number(item.toolCacheCount) : 0,
          })));
        }
      } catch {
        if (!cancelled) setMcpServers([]);
      }
    };
    void loadMcpServers();
    return () => {
      cancelled = true;
    };
  }, [token]);

  useEffect(() => {
    if (!libraryModalOpen) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setLibraryModalOpen(false);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [libraryModalOpen]);

  useEffect(() => {
    if (!publishDialogOpen) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" && !busy) setPublishDialogOpen(false);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [publishDialogOpen, busy]);

  const loadVersions = useCallback(async (id: number) => {
    const res = await fetch(`/skills/${id}/versions`, { headers: { Authorization: `Bearer ${token}` } });
    const json = await res.json();
    if (!res.ok || !json.success) {
      setVersions([]);
      return;
    }
    setVersions((json.data ?? []) as SkillVersion[]);
  }, [token]);

  const loadSkill = useCallback(async () => {
    if (!Number.isFinite(skillId) || skillId < 1) {
      setLoadError("");
      return;
    }
    setBusy(true);
    setLoadError("");
    try {
      const res = await fetch(`/skills/${skillId}`, { headers: { Authorization: `Bearer ${token}` } });
      const json = await res.json();
      if (!res.ok || !json.success) {
        setLoadError(json.message ?? `HTTP ${res.status}`);
        return;
      }
      const skill = json.data as Skill;
      setForm({
        id: skill.id,
        skillCode: skill.skillCode,
        name: skill.name,
        description: skill.description ?? "",
        enabled: skill.enabled,
        riskLevel: skill.riskLevel,
        sourceType: skill.sourceType,
        visibility: skill.visibility,
        editPolicy: skill.editPolicy,
        bindingPolicy: skill.bindingPolicy,
        updatePolicy: skill.updatePolicy,
        templateCode: skill.templateCode ?? "",
        baseTemplateVersion: skill.baseTemplateVersion,
        lifecycleStatus: skill.lifecycleStatus,
        currentPublishedVersionId: skill.currentPublishedVersionId,
        latestDraftVersionId: skill.latestDraftVersionId,
        promptFragment: skill.promptFragment ?? "",
        draftSpecText: skill.draftSpecText ?? "",
        toolWhitelistText: joinCsv(skill.toolWhitelist),
        kbWhitelistText: joinCsv(skill.kbWhitelist),
        runtimeApis: (skill.runtimeApis ?? []).map(runtimeApiToForm),
        handoffRule: skill.handoffRule ?? "",
        outputContract: skill.outputContract ?? "",
        changeLog: "",
        builtin: skill.builtin,
      });
      setPreview(null);
      setAuthoringResult(null);
      setAuthoringParsing(false);
      setAuthoringSessionId(null);
      setClarificationAnswersByQuestion({});
      setAuthoringRestoreSnapshot(null);
      if (skill.sourceType === "TENANT_CUSTOM") {
        void loadVersions(skill.id);
      }
    } finally {
      setBusy(false);
      setSkillLoaded(true);
    }
  }, [skillId, token, loadVersions]);

  useEffect(() => {
    if (Number.isFinite(skillId) && skillId >= 1) {
      void loadSkill();
    } else {
      setSkillLoaded(true);
      setForm(EMPTY_FORM);
      setPreview(null);
      setAuthoringResult(null);
      setAuthoringParsing(false);
      setAuthoringSessionId(null);
      setClarificationAnswersByQuestion({});
      setAuthoringRestoreSnapshot(null);
      setVersions([]);
      setLoadError("");
    }
  }, [skillId, loadSkill]);

  const resetForm = () => {
    setForm(EMPTY_FORM);
    setPreview(null);
    setAuthoringResult(null);
    setAuthoringParsing(false);
    setAuthoringSessionId(null);
    setClarificationAnswersByQuestion({});
    setAuthoringRestoreSnapshot(null);
    setVersions([]);
    if (Number.isFinite(skillId) && skillId >= 1) {
      void loadSkill();
    } else {
      nav("/admin/skills/new", { replace: true });
    }
  };

  const saveSkill = async (
    draft: SkillForm = form,
    options: { navigateToEdit?: boolean; flashSuccess?: boolean; keepBusy?: boolean } = {},
  ): Promise<Skill | null> => {
    if (!draft.skillCode.trim() || !draft.name.trim()) {
      flash("技能代码与显示名称必填");
      return null;
    }
    const navigateToEdit = options.navigateToEdit !== false;
    const flashSuccess = options.flashSuccess !== false;
    setBusy(true);
    try {
      const runtimeApis = buildRuntimeApisPayload(draft);
      if (runtimeApis === null) return null;
      const body = {
        skillCode: draft.skillCode.trim(),
        name: draft.name.trim(),
        description: draft.description.trim(),
        enabled: draft.enabled,
        riskLevel: draft.riskLevel,
        promptFragment: draft.promptFragment.trim(),
        draftSpecText: draft.draftSpecText.trim(),
        toolWhitelist: splitCsv(draft.toolWhitelistText),
        kbWhitelist: splitCsv(draft.kbWhitelistText),
        runtimeApis,
        handoffRule: draft.handoffRule.trim(),
        outputContract: draft.outputContract.trim(),
        changeLog: "",
      };
      const isUpdate = Boolean(draft.id);
      const res = await fetch(isUpdate ? `/skills/${draft.id}` : "/skills", {
        method: isUpdate ? "PUT" : "POST",
        headers: authHeaders,
        body: JSON.stringify(body),
      });
      const json = await res.json();
      if (!res.ok || !json.success) {
        flash(`保存失败：${json.message ?? `HTTP ${res.status}`}`);
        return null;
      }
      if (flashSuccess) flash(isUpdate ? "已保存" : "已创建");
      const saved = json.data as Skill;
      setForm((prev) => ({
        ...prev,
        id: saved.id,
        lifecycleStatus: saved.lifecycleStatus,
        currentPublishedVersionId: saved.currentPublishedVersionId,
        latestDraftVersionId: saved.latestDraftVersionId,
        changeLog: "",
      }));
      setAuthoringRestoreSnapshot(null);
      if (saved.id) void loadVersions(saved.id);
      await loadExistingCodes();
      if (navigateToEdit && !isUpdate && saved?.id) {
        nav(`/admin/skills/${saved.id}/edit`, { replace: true });
      }
      return saved;
    } finally {
      if (!options.keepBusy) setBusy(false);
    }
  };

  const previewSkill = async () => {
    if (!form.skillCode.trim() || !form.name.trim()) {
      flash("请先填写技能代码与显示名称");
      return;
    }
    setBusy(true);
    try {
      const runtimeApis = buildRuntimeApisPayload(form);
      if (runtimeApis === null) return;
      const body = {
        skillCode: form.skillCode.trim(),
        name: form.name.trim(),
        specText: form.draftSpecText.trim(),
        promptFragment: form.promptFragment.trim(),
        toolWhitelist: splitCsv(form.toolWhitelistText),
        kbWhitelist: splitCsv(form.kbWhitelistText),
        runtimeApis,
        handoffRule: form.handoffRule.trim(),
        outputContract: form.outputContract.trim(),
        riskLevel: form.riskLevel,
      };
      const res = await fetch("/skills/preview", {
        method: "POST",
        headers: authHeaders,
        body: JSON.stringify(body),
      });
      const json = await res.json();
      if (!res.ok || !json.success) {
        flash(`预览失败：${json.message ?? `HTTP ${res.status}`}`);
        return;
      }
      setPreview((json.data ?? null) as SkillPreview | null);
    } finally {
      setBusy(false);
    }
  };

  const applyTemplate = (template: SkillTemplate) => {
    setForm({ ...template.form });
    setPreview(null);
    setAuthoringRestoreSnapshot(null);
    flash(`已填充 ${template.title}`);
  };

  const createFromTemplate = async (template: SkillTemplate) => {
    if (existingCodes.has(template.form.skillCode)) {
      flash("技能代码已存在，请从列表进入编辑");
      nav("/admin/skills");
      return;
    }
    await saveSkill(template.form);
  };

  const pickTemplateForInit = (template: SkillTemplate) => {
    applyTemplate(template);
    setLibraryModalOpen(false);
  };

  const pickTemplateForCreate = async (template: SkillTemplate) => {
    try {
      await createFromTemplate(template);
    } finally {
      setLibraryModalOpen(false);
    }
  };

  const toggleAuthoringSpeech = async () => {
    if (!token) {
      flash("请先登录");
      return;
    }
    if (asrListening) {
      stopAsrSession();
      return;
    }
    await startAsrSession({
      token,
      getPrefix: () => authoringPrompt,
      onLiveText: (full) => setAuthoringPrompt(full),
      onNotice: flash,
      onFinished: async ({ asrText, fullText }) => {
        if (asrText) {
          setAuthoringPrompt(fullText);
          flash("实时转写完成");
        } else {
          flash("未识别到有效语音内容");
        }
      },
    });
  };

  const hasDraftContent = Boolean(
    form.id ||
      form.skillCode.trim() ||
      form.name.trim() ||
      form.description.trim() ||
      form.promptFragment.trim() ||
      form.draftSpecText.trim() ||
      form.toolWhitelistText.trim() ||
      form.kbWhitelistText.trim() ||
      form.runtimeApis.length > 0 ||
      form.handoffRule.trim() ||
      form.outputContract.trim(),
  );

  const applyGeneratedSkill = (result: SkillAuthoringResult) => {
    setForm((prev) => ({
      ...prev,
      skillCode: result.skillSpec.skillCode,
      name: result.skillSpec.name,
      description: result.skillSpec.description ?? "",
      riskLevel: result.skillSpec.riskLevel,
      promptFragment: result.skillSpec.promptFragment ?? "",
      draftSpecText: result.skillSpec.draftSpecText ?? "",
      toolWhitelistText: joinCsv(result.skillSpec.toolWhitelist),
      kbWhitelistText: joinCsv(result.skillSpec.kbWhitelist),
      runtimeApis: prev.runtimeApis,
      handoffRule: result.skillSpec.handoffRule ?? "",
      outputContract: result.skillSpec.outputContract ?? "",
      enabled: prev.id ? prev.enabled : true,
    }));
    setPreview(result.preview);
    setAuthoringResult(result);
    setAuthoringSessionId(result.sessionId ?? null);
    setClarificationAnswersByQuestion({});
  };

  const buildCurrentSkillSpec = (): GeneratedSkillSpec => ({
    skillCode: form.skillCode.trim(),
    name: form.name.trim(),
    description: form.description.trim(),
    promptFragment: form.promptFragment.trim(),
    draftSpecText: form.draftSpecText.trim(),
    toolWhitelist: splitCsv(form.toolWhitelistText),
    kbWhitelist: splitCsv(form.kbWhitelistText),
    handoffRule: form.handoffRule.trim(),
    outputContract: form.outputContract.trim(),
    riskLevel: form.riskLevel,
    triggerHints: authoringResult?.skillSpec.triggerHints ?? [],
    userIntentExamples: authoringResult?.skillSpec.userIntentExamples ?? [],
    clarificationQuestions: authoringResult?.skillSpec.clarificationQuestions ?? [],
    warnings: authoringResult?.skillSpec.warnings ?? [],
  });

  const buildClarificationAnswersPayload = (): { question: string; answer: string }[] => {
    const questions = authoringResult?.skillSpec.clarificationQuestions ?? [];
    return questions
      .map((q) => {
        const answer = (clarificationAnswersByQuestion[q] ?? "").trim();
        return answer ? { question: q, answer } : null;
      })
      .filter((item): item is { question: string; answer: string } => item !== null);
  };

  const hasClarificationAnswers = (): boolean => buildClarificationAnswersPayload().length > 0;

  const restoreLastAuthoringRefine = () => {
    if (!authoringRestoreSnapshot) return;
    setForm(authoringRestoreSnapshot.form);
    setPreview(authoringRestoreSnapshot.preview);
    setAuthoringPrompt(authoringRestoreSnapshot.authoringPrompt);
    setAuthoringResult(authoringRestoreSnapshot.authoringResult);
    setAuthoringSessionId(authoringRestoreSnapshot.authoringSessionId);
    setClarificationAnswersByQuestion(authoringRestoreSnapshot.clarificationAnswersByQuestion);
    setAuthoringParsing(false);
    setAuthoringRestoreSnapshot(null);
    flash("已回退到继续优化前");
  };

  const generateSkillDraft = async () => {
    if (!canChangeContent) {
      flash("当前技能为平台维护，只能查看或启停配置");
      return;
    }
    if (!authoringPrompt.trim()) {
      flash("请输入需求描述");
      return;
    }
    if (hasDraftContent) {
      const confirmed = window.confirm("将覆盖当前草稿，是否继续？");
      if (!confirmed) return;
    }
    setAuthoringResult(null);
    setAuthoringParsing(true);
    setClarificationAnswersByQuestion({});
    setAuthoringRestoreSnapshot(null);
    setBusy(true);
    try {
      const preferred = parsePreferredModel();
      const res = await fetch("/skills/authoring/generate", {
        method: "POST",
        headers: authHeaders,
        body: JSON.stringify({
          sourceText: authoringPrompt.trim(),
          preferredModel: preferred.preferredModel,
          preferredProvider: preferred.preferredProvider,
        }),
      });
      const { body: json } = await safeFetchJson<SkillAuthoringResult>(res);
      if (!res.ok || !json?.success) {
        flash(`生成失败：${json?.message ?? `HTTP ${res.status}`}`);
        return;
      }
      const result = (json.data ?? null) as SkillAuthoringResult | null;
      if (!result) {
        flash("未返回草稿");
        return;
      }
      applyGeneratedSkill(result);
      setAuthoringRestoreSnapshot(null);
      flash("草稿已生成");
    } finally {
      setAuthoringParsing(false);
      setBusy(false);
    }
  };

  const refineSkillDraft = async () => {
    if (!canChangeContent) {
      flash("当前技能为平台维护，只能查看或启停配置");
      return;
    }
    if (!authoringPrompt.trim() && !hasClarificationAnswers()) {
      flash("填写优化说明或下方追问答案");
      return;
    }
    if (!form.skillCode.trim() || !form.name.trim()) {
      flash("请先生成草稿");
      return;
    }
    const currentSkillSpec = buildCurrentSkillSpec();
    const clarificationAnswers = buildClarificationAnswersPayload();
    const restoreSnapshot: AuthoringRestoreSnapshot = {
      form: { ...form },
      preview,
      authoringPrompt,
      authoringResult,
      authoringSessionId,
      clarificationAnswersByQuestion: { ...clarificationAnswersByQuestion },
    };
    setAuthoringRestoreSnapshot(null);
    setAuthoringResult(null);
    setAuthoringParsing(true);
    setClarificationAnswersByQuestion({});
    setBusy(true);
    try {
      const preferred = parsePreferredModel();
      const res = await fetch("/skills/authoring/refine", {
        method: "POST",
        headers: authHeaders,
        body: JSON.stringify({
          sessionId: authoringSessionId ?? undefined,
          sourceText: authoringPrompt.trim(),
          currentSkillSpec,
          clarificationAnswers,
          preferredModel: preferred.preferredModel,
          preferredProvider: preferred.preferredProvider,
        }),
      });
      const { body: json } = await safeFetchJson<SkillAuthoringResult>(res);
      if (!res.ok || !json?.success) {
        flash(`优化失败：${json?.message ?? `HTTP ${res.status}`}`);
        return;
      }
      const result = (json.data ?? null) as SkillAuthoringResult | null;
      if (!result) {
        flash("未返回草稿");
        return;
      }
      setAuthoringRestoreSnapshot(restoreSnapshot);
      applyGeneratedSkill(result);
      flash("已优化");
    } finally {
      setAuthoringParsing(false);
      setBusy(false);
    }
  };

  const createFromAuthoring = async () => {
    if (!form.skillCode.trim() || !form.name.trim()) {
      flash("请完善草稿");
      return;
    }
    setBusy(true);
    try {
      const preferred = parsePreferredModel();
      const res = await fetch("/skills/authoring/create", {
        method: "POST",
        headers: authHeaders,
        body: JSON.stringify({
          sourceText: authoringPrompt.trim() || `创建技能 ${form.name.trim()}`,
          sessionId: authoringSessionId ?? undefined,
          skillSpec: buildCurrentSkillSpec(),
          preferredModel: preferred.preferredModel,
          preferredProvider: preferred.preferredProvider,
        }),
      });
      const json = await res.json();
      if (!res.ok || !json.success) {
        flash(`创建失败：${json.message ?? `HTTP ${res.status}`}`);
        return;
      }
      const result = (json.data ?? null) as SkillAuthoringCreateResult | null;
      if (!result) {
        flash("未返回结果");
        return;
      }
      await loadExistingCodes();
      setForm((prev) => ({
        ...prev,
        id: result.createdSkill.id,
        skillCode: result.skillSpec.skillCode,
        name: result.skillSpec.name,
        description: result.skillSpec.description ?? "",
        riskLevel: result.skillSpec.riskLevel,
        promptFragment: result.skillSpec.promptFragment ?? "",
        draftSpecText: result.skillSpec.draftSpecText ?? "",
        toolWhitelistText: joinCsv(result.skillSpec.toolWhitelist),
        kbWhitelistText: joinCsv(result.skillSpec.kbWhitelist),
        runtimeApis: prev.runtimeApis,
        handoffRule: result.skillSpec.handoffRule ?? "",
        outputContract: result.skillSpec.outputContract ?? "",
        sourceType: result.createdSkill.sourceType,
        visibility: result.createdSkill.visibility,
        editPolicy: result.createdSkill.editPolicy,
        bindingPolicy: result.createdSkill.bindingPolicy,
        updatePolicy: result.createdSkill.updatePolicy,
        templateCode: result.createdSkill.templateCode ?? "",
        baseTemplateVersion: result.createdSkill.baseTemplateVersion,
        lifecycleStatus: result.createdSkill.lifecycleStatus,
        currentPublishedVersionId: result.createdSkill.currentPublishedVersionId,
        latestDraftVersionId: result.createdSkill.latestDraftVersionId,
        changeLog: "",
        enabled: true,
      }));
      void loadVersions(result.createdSkill.id);
      setPreview(result.preview);
      setAuthoringResult({
        sourceText: result.sourceText,
        skillSpec: result.skillSpec,
        preview: result.preview,
      });
      setAuthoringSessionId(null);
      setClarificationAnswersByQuestion({});
      flash("已创建");
      nav(`/admin/skills/${result.createdSkill.id}/edit`, { replace: true });
    } finally {
      setBusy(false);
    }
  };

  const submitSkill = async () => {
    if (!form.id && authoringResult && form.runtimeApis.length === 0) {
      await createFromAuthoring();
      return;
    }
    await saveSkill();
  };

  const openPublishDialog = () => {
    setPublishChangeLog("");
    setPublishDialogOpen(true);
  };

  const publishSkill = async () => {
    const releaseNotes = publishChangeLog.trim();
    if (!releaseNotes) {
      flash("请填写版本发布说明");
      return;
    }
    setBusy(true);
    try {
      let publishSkillId = form.id;
      const wasNewSkill = !publishSkillId;
      if (!publishSkillId) {
        const saved = await saveSkill(form, { navigateToEdit: false, flashSuccess: false, keepBusy: true });
        if (!saved?.id) return;
        publishSkillId = saved.id;
      }
      const res = await fetch(`/skills/${publishSkillId}/publish`, {
        method: "POST",
        headers: authHeaders,
        body: JSON.stringify({ changeLog: releaseNotes }),
      });
      const json = await res.json();
      if (!res.ok || !json.success) {
        flash(`发布失败：${json.message ?? `HTTP ${res.status}`}`);
        return;
      }
      const saved = json.data as Skill;
      setForm((prev) => ({
        ...prev,
        lifecycleStatus: saved.lifecycleStatus,
        currentPublishedVersionId: saved.currentPublishedVersionId,
        latestDraftVersionId: saved.latestDraftVersionId,
        changeLog: "",
      }));
      await loadVersions(publishSkillId);
      if (wasNewSkill) {
        nav(`/admin/skills/${publishSkillId}/edit`, { replace: true });
      }
      flash(wasNewSkill ? "已创建并发布" : "已发布");
      setPublishDialogOpen(false);
      setPublishChangeLog("");
    } finally {
      setBusy(false);
    }
  };

  const restoreVersion = async (version: SkillVersion) => {
    if (!form.id) return;
    const confirmed = window.confirm(`恢复 v${version.versionNo} 为当前草稿？恢复后仍需保存或发布。`);
    if (!confirmed) return;
    setBusy(true);
    try {
      const res = await fetch(`/skills/${form.id}/versions/${version.id}/restore`, {
        method: "POST",
        headers: authHeaders,
        body: JSON.stringify({ changeLog: `恢复自 v${version.versionNo}` }),
      });
      const json = await res.json();
      if (!res.ok || !json.success) {
        flash(`恢复失败：${json.message ?? `HTTP ${res.status}`}`);
        return;
      }
      flash("已恢复为当前草稿");
      await loadSkill();
    } finally {
      setBusy(false);
    }
  };

  const exportSkill = async () => {
    if (!form.id) {
      flash("请先保存草稿");
      return;
    }
    setBusy(true);
    try {
      const res = await fetch(`/skills/${form.id}/exports`, {
        method: "POST",
        headers: authHeaders,
        body: JSON.stringify({ allowDraft: false }),
      });
      const json = await res.json();
      if (!res.ok || !json.success) {
        flash(`导出失败：${json.message ?? `HTTP ${res.status}`}`);
        return;
      }
      const job = json.data as SkillExportJob;
      try {
        await downloadSkillExportPackage(token, job);
        flash("已生成通用技能包");
      } catch (err) {
        flash(`下载失败：${err instanceof Error ? err.message : "未知错误"}`);
      }
    } finally {
      setBusy(false);
    }
  };

  const deleteCurrentSkill = async () => {
    if (!form.id) return;
    const impactRes = await fetch(`/skills/${form.id}/delete-impact`, { headers: { Authorization: `Bearer ${token}` } });
    const impactJson = await impactRes.json();
    if (!impactRes.ok || !impactJson.success) {
      flash(`删除检查失败：${impactJson.message ?? `HTTP ${impactRes.status}`}`);
      return;
    }
    const blockers = impactJson.data?.blockers ?? [];
    if (blockers.length > 0) {
      flash(`不能删除：${blockers.join("；")}`);
      return;
    }
    const confirmed = window.confirm(`删除自定义技能「${form.name}」？普通列表将不再显示。`);
    if (!confirmed) return;
    const reason = window.prompt("删除原因", "测试技能已废弃") ?? "";
    setBusy(true);
    try {
      const res = await fetch(`/skills/${form.id}`, {
        method: "DELETE",
        headers: authHeaders,
        body: JSON.stringify({ reason }),
      });
      const json = await res.json();
      if (!res.ok || !json.success) {
        flash(`删除失败：${json.message ?? `HTTP ${res.status}`}`);
        return;
      }
      flash("已删除");
      nav("/admin/skills", { replace: true });
    } finally {
      setBusy(false);
    }
  };

  const selectedToolNames = splitCsv(form.toolWhitelistText);
  const selectedKbIds = splitCsv(form.kbWhitelistText);
  const allMcpTools = Object.values(mcpToolsByServer).flat();

  const updateWhitelistText = (type: WhitelistPickerType, values: string[]) => {
    const deduped = Array.from(new Set(values.map((value) => value.trim()).filter(Boolean)));
    setForm((prev) => ({
      ...prev,
      [type === "tool" ? "toolWhitelistText" : "kbWhitelistText"]: joinCsv(deduped),
    }));
  };

  const removeWhitelistValue = (type: WhitelistPickerType, value: string) => {
    updateWhitelistText(
      type,
      (type === "tool" ? selectedToolNames : selectedKbIds).filter((item) => item !== value),
    );
  };

  const openWhitelistPicker = (type: WhitelistPickerType) => {
    setWhitelistPickerSelection(type === "tool" ? selectedToolNames : selectedKbIds);
    if (type === "tool") {
      setPickerToolTab("tool");
      setExpandedMcpServerIds([]);
    }
    setWhitelistPickerOpen(type);
  };

  const toggleWhitelistPickerItem = (key: string, checked: boolean) => {
    setWhitelistPickerSelection((prev) => {
      if (checked) return prev.includes(key) ? prev : [...prev, key];
      return prev.filter((item) => item !== key);
    });
  };

  const confirmWhitelistPicker = () => {
    if (!whitelistPickerOpen) return;
    updateWhitelistText(whitelistPickerOpen, whitelistPickerSelection);
    setWhitelistPickerOpen(null);
  };

  const loadMcpTools = async (server: McpServerSummary) => {
    if (!token || !server.enabled || mcpToolsByServer[server.id] || mcpServerLoading[server.id]) return;
    setMcpServerLoading((current) => ({ ...current, [server.id]: true }));
    try {
      const response = await fetch(`/mcp-servers/${server.id}/tools`, { headers: { Authorization: `Bearer ${token}` } });
      const { body } = await safeFetchJson<McpToolCachePayload>(response);
      const toolRows = body?.data?.tools ?? [];
      if (!response.ok || !body?.success || !Array.isArray(toolRows)) {
        setMcpToolsByServer((current) => ({ ...current, [server.id]: [] }));
        return;
      }
      const nextTools = toolRows
        .filter((item) => item.name?.trim())
        .map((item) => {
          const toolName = item.name.trim();
          const catalog = toolCatalog.find((entry) => entry.id === toolName || entry.name === toolName);
          return {
            id: catalog?.id ?? toolName,
            name: catalog?.name ?? toolName,
            description: item.description || catalog?.description || "",
            level: catalog?.level ?? "MCP",
          };
        });
      setMcpToolsByServer((current) => ({ ...current, [server.id]: nextTools }));
    } catch {
      setMcpToolsByServer((current) => ({ ...current, [server.id]: [] }));
    } finally {
      setMcpServerLoading((current) => ({ ...current, [server.id]: false }));
    }
  };

  const toggleMcpServerExpanded = (server: McpServerSummary) => {
    const willExpand = !expandedMcpServerIds.includes(server.id);
    setExpandedMcpServerIds((current) => (
      willExpand ? [...current, server.id] : current.filter((id) => id !== server.id)
    ));
    if (willExpand) void loadMcpTools(server);
  };

  const renderToolGlyph = (level: string) => {
    if (level.toUpperCase() !== "MCP") return "T";
    return (
      <svg viewBox="0 0 24 24" width="16" height="16" fill="none" aria-hidden="true">
        <path
          d="M8.5 9.5 11.8 6.2a2.4 2.4 0 1 1 3.4 3.4l-4.5 4.5a2.6 2.6 0 0 1-3.7-3.7l5.1-5.1"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <path
          d="m15.5 14.5-3.3 3.3a2.4 2.4 0 1 1-3.4-3.4l4.5-4.5a2.6 2.6 0 0 1 3.7 3.7l-5.1 5.1"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
    );
  };

  const addRuntimeApi = () => {
    setForm((prev) => ({
      ...prev,
      runtimeApis: [...prev.runtimeApis, newRuntimeApiForm()],
    }));
    setActiveEditorTab("runtimeApis");
  };

  const updateRuntimeApi = <K extends keyof SkillRuntimeApiForm>(
    index: number,
    key: K,
    value: SkillRuntimeApiForm[K],
  ) => {
    setForm((prev) => ({
      ...prev,
      runtimeApis: prev.runtimeApis.map((item, idx) => (idx === index ? { ...item, [key]: value } : item)),
    }));
  };

  const removeRuntimeApi = (index: number) => {
    setForm((prev) => ({
      ...prev,
      runtimeApis: prev.runtimeApis.filter((_, idx) => idx !== index),
    }));
  };

  const pageTitle = form.id ? "编辑技能" : "新建技能";
  const tenantEditable = form.editPolicy === "EDITABLE";
  const tenantConfigurable = form.editPolicy === "CONFIGURABLE";
  const draftStatusLabel = form.id
    ? (form.currentPublishedVersionId ? "已发布" : form.lifecycleStatus === "DISABLED" ? "已停用" : "仅草稿")
    : "新草稿";
  const summaryStatusLabel = authoringParsing ? "解析中" : authoringResult ? "已解析" : "待解析";
  const contentLockLabel = editPolicyLabel(form.editPolicy);
  const bindingLabel = bindingPolicyLabel(form.bindingPolicy);
  const canChangeContent = tenantEditable;
  const canPublish = Boolean(form.sourceType === "TENANT_CUSTOM" && tenantEditable);
  const canRestoreVersions = Boolean(form.id && form.sourceType === "TENANT_CUSTOM" && tenantEditable);
  const canExport = Boolean(form.id && form.sourceType === "TENANT_CUSTOM" && tenantEditable);
  const canDelete = Boolean(form.id && form.sourceType === "TENANT_CUSTOM" && tenantEditable);
  const isPlatformManaged = form.sourceType === "PLATFORM_STANDARD";
  const fieldCompletionItems = [
    form.skillCode.trim(),
    form.name.trim(),
    form.description.trim(),
    form.promptFragment.trim(),
    form.draftSpecText.trim(),
    form.runtimeApis.length > 0 ? "runtimeApis" : "",
    form.handoffRule.trim(),
    form.outputContract.trim(),
  ];
  const completedFieldCount = fieldCompletionItems.filter(Boolean).length;
  const boundaryFieldCount = [
    form.toolWhitelistText.trim(),
    form.kbWhitelistText.trim(),
    form.handoffRule.trim(),
    form.outputContract.trim(),
  ].filter(Boolean).length;
  const pickerItems: Array<{ key: string; title: string; subtitle?: string; tag?: string }> = whitelistPickerOpen === "kb"
    ? kbs.map((kb) => ({
        key: String(kb.id),
        title: kb.name,
        subtitle: kb.description || "已接入知识库，可作为检索上下文。",
      }))
    : toolCatalog.map((tool) => ({
        key: tool.id,
        title: tool.name,
        subtitle: tool.description,
        tag: tool.level,
      }));
  const enabledSwitch = (
    <div className="skills-compose__header-switch">
      <span className="admin-skills-compose__enabled-title">启用</span>
      <button
        type="button"
        role="switch"
        aria-checked={form.enabled}
        className={`skills-toggle-switch${form.enabled ? " is-on" : ""}`}
        disabled={busy || (!tenantEditable && !tenantConfigurable)}
        onClick={() => setForm((prev) => ({ ...prev, enabled: !prev.enabled }))}
      >
        <span aria-hidden="true" />
      </button>
    </div>
  );

  if (Number.isFinite(skillId) && skillId >= 1 && loadError) {
    return (
      <div className="admin-page skills-compose">
        <Link to="/admin/skills" className="text-link">
          ← 返回列表
        </Link>
        <p className="subtle admin-skills-compose__status">
          加载失败：{loadError}
        </p>
      </div>
    );
  }

  if (isEditRoute && !skillLoaded) {
    return (
      <div className="admin-page skills-compose">
        <Link to="/admin/skills" className="text-link">
          ← 技能列表
        </Link>
        <p className="subtle admin-skills-compose__status">
          加载中… · Loading
        </p>
      </div>
    );
  }

  return (
    <div className="admin-page skills-compose">
      {notice ? <div className="cici-toast">{notice}</div> : null}

      <div className="skills-compose__top">
        <div className="skills-compose__heading">
          <Link to="/admin/skills" className="skills-compose__crumb">
            技能列表 / {pageTitle}
          </Link>
          <div className="skills-compose__title-row">
            <h1 className="skills-compose__title">
              {pageTitle}
              <HelpTip text="描述目标、触发场景、输出与风险边界，系统会生成可编辑草稿；下方字段用于最终校准与发布前检查。" />
            </h1>
            <div className="skills-compose__status-chips" aria-label="技能编辑状态">
              <span>{skillSourceLabel(form.sourceType)}</span>
              <span>{draftStatusLabel}</span>
              <span>{summaryStatusLabel}</span>
              <span>{contentLockLabel}</span>
              <span>{bindingLabel}</span>
            </div>
          </div>
        </div>
        <div className="skills-compose__header-actions">
          {enabledSwitch}
          <button type="button" className="secondary skills-compose__header-btn" onClick={resetForm} disabled={busy}>
            重置
          </button>
          {tenantEditable || tenantConfigurable ? (
            <button
              type="button"
              className={tenantEditable ? "secondary skills-compose__header-btn" : "skills-compose__header-primary"}
              onClick={() => void submitSkill()}
              disabled={busy}
            >
              {tenantConfigurable && !tenantEditable ? "保存配置" : "保存草稿"}
            </button>
          ) : null}
          {canExport ? (
            <button type="button" className="secondary skills-compose__header-btn" onClick={() => void exportSkill()} disabled={busy}>
              导出
            </button>
          ) : null}
          {canRestoreVersions ? (
            <button type="button" className="secondary skills-compose__header-btn" onClick={() => setActiveEditorTab("versions")} disabled={busy}>
              版本管理
            </button>
          ) : null}
          {canDelete ? (
            <button type="button" className="secondary skills-compose__header-btn skills-compose__danger-btn" onClick={() => void deleteCurrentSkill()} disabled={busy}>
              删除
            </button>
          ) : null}
          <button type="button" className="secondary skills-compose__header-btn" onClick={() => void previewSkill()} disabled={busy}>
            编译预览
          </button>
          {canPublish ? (
            <button type="button" className="skills-compose__header-primary" onClick={openPublishDialog} disabled={busy}>
              发布
            </button>
          ) : null}
        </div>
      </div>

      <section className="skills-compose__workspace">
        <aside className="skills-compose__assistant">
          <div className="skills-compose__assistant-bar">
            <div className="skills-compose__assistant-head">
              <h2>
                自然语言生成
                <HelpTip text="用自然语言生成或优化技能草稿；生成结果会填入下方字段，最终仍由顶部按钮保存。" />
              </h2>
            </div>
            <div className="skills-compose__assistant-controls">
              <label className="skills-compose__model-field" htmlFor="skills-authoring-model-select">
                <span>
                  模型
                  <HelpTip text="选择本次生成草稿使用的模型；不选择时使用系统默认路由模型。" />
                </span>
                <select
                  id="skills-authoring-model-select"
                  className="skills-authoring-toolbar__model-select"
                  value={selectedModel}
                  onChange={(e) => setSelectedModel(e.target.value)}
                  disabled={busy || modelOptions.length === 0}
                >
                  <option value="">默认路由模型</option>
                  {modelOptions.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
              <div className="skills-authoring-toolbar__actions skills-compose__assistant-actions">
                <button
                  type="button"
                  className="skills-authoring-btn skills-authoring-btn--primary"
                  onClick={() => void generateSkillDraft()}
                  disabled={busy || !canChangeContent}
                >
                  智能生成草稿
                </button>
                <button type="button" className="secondary skills-authoring-btn" onClick={() => void refineSkillDraft()} disabled={busy || !canChangeContent}>
                  继续优化
                </button>
                {authoringRestoreSnapshot ? (
                  <button
                    type="button"
                    className="secondary skills-authoring-btn skills-authoring-btn--restore"
                    onClick={restoreLastAuthoringRefine}
                    disabled={busy || !canChangeContent}
                  >
                    回退本次优化
                  </button>
                ) : null}
                <button
                  type="button"
                  className="secondary skills-authoring-btn"
                  disabled={busy}
                  onClick={() => {
                    setAuthoringPrompt("");
                    setAuthoringResult(null);
                    setAuthoringParsing(false);
                    setAuthoringSessionId(null);
                    setClarificationAnswersByQuestion({});
                    setAuthoringRestoreSnapshot(null);
                  }}
                >
                  清空
                </button>
              </div>
            </div>
          </div>
          <div className="skills-compose__assistant-grid">
            <div className="skills-compose__assistant-input">
              <div className="skills-authoring-readonly-head skills-compose__authoring-head">
                <span className="skills-authoring-readonly-head__zh">
                  需求描述
                  <HelpTip text="说明目标、触发场景、输出与风险边界。" />
                </span>
              </div>
              <div className="skills-authoring-input-wrap">
                <textarea
                  className="skills-authoring-main-input"
                  rows={10}
                  value={authoringPrompt}
                  onChange={(e) => setAuthoringPrompt(e.target.value)}
                  disabled={!canChangeContent}
                  aria-label="需求描述"
                  placeholder={form.id ? "输入这次想调整的内容，例如补充输出字段、工具边界或升级处理规则" : "请输入需求描述"}
                />
                <span className="skills-authoring-input__count">{authoringPrompt.length} / 3000</span>
                <div className="skills-authoring-input__actions">
                  <button
                    type="button"
                    className={`skills-authoring-mic${asrListening ? " skills-authoring-mic--on" : ""}`}
                    onClick={() => void toggleAuthoringSpeech()}
                    disabled={busy || !speechSupported || !token}
                    title={
                      !token
                        ? "请先登录"
                        : !speechSupported
                          ? "当前浏览器不支持录音"
                          : asrListening
                            ? "停止并写入"
                            : "语音录入"
                    }
                    aria-pressed={asrListening}
                  >
                    <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden fill="none">
                      <path
                        fill="currentColor"
                        d="M12 14a3 3 0 0 0 3-3V6a3 3 0 1 0-6 0v5a3 3 0 0 0 3 3zm5-3a5 5 0 1 1-10 0H5a7 7 0 0 0 6 6.92V20H8v2h8v-2h-3v-2.08A7 7 0 0 0 19 11h-2z"
                      />
                    </svg>
                  </button>
                </div>
              </div>
              {!speechSupported ? (
                <p className="skills-authoring-mic-hint subtle">
                  语音录入需浏览器支持录音与实时连接。
                </p>
              ) : null}
            </div>
            <div className="skills-compose__assistant-summary">
              <div className="skills-authoring-readonly-head">
                <span className="skills-authoring-readonly-head__zh">
                  需求解析
                  <HelpTip text="生成草稿时展示 AI 从需求中解析出的技能、触发场景、风险、资源边界和待补充项。" />
                </span>
                <span className="skills-authoring-readonly-head__badge">只读</span>
              </div>
              <div className="skills-authoring-readonly-body">
                {authoringParsing ? (
                  <div className="skills-authoring-readonly-loading" role="status" aria-live="polite">
                    <div className="skills-authoring-readonly-loading__mark" aria-hidden>
                      <span />
                      <span />
                      <span />
                    </div>
                    <p>正在解析需求</p>
                    <div className="skills-authoring-readonly-loading__steps" aria-hidden>
                      <span>读取需求文本</span>
                      <span>识别风险与边界</span>
                      <span>生成技能草稿</span>
                    </div>
                  </div>
                ) : authoringResult ? (
                  <div className="skills-authoring-readonly-scroll">
                    <div className="skills-authoring-preview__body">
                      <div className="skills-authoring-preview__head">
                        <strong>{authoringResult.skillSpec.name}</strong>
                        <span className={riskBadgeClass(authoringResult.skillSpec.riskLevel)}>
                          {riskLabel(authoringResult.skillSpec.riskLevel)}
                        </span>
                      </div>
                      <p>{authoringResult.skillSpec.description || "—"}</p>
                      <div className="skills-authoring-chips">
                        <span>{authoringResult.skillSpec.skillCode}</span>
                        <span>{authoringResult.skillSpec.toolWhitelist.length} 个工具</span>
                        <span>{authoringResult.skillSpec.kbWhitelist.length} 个知识库</span>
                      </div>
                      {authoringResult.skillSpec.triggerHints.length ? (
                        <>
                          <h5>触发</h5>
                          <div className="skills-authoring-chips">
                            {authoringResult.skillSpec.triggerHints.map((item) => (
                              <span key={item}>{item}</span>
                            ))}
                          </div>
                        </>
                      ) : null}
                      {authoringResult.skillSpec.warnings.length ? (
                        <>
                          <h5>待补充</h5>
                          <ul className="skills-authoring-list">
                            {authoringResult.skillSpec.warnings.map((item) => (
                              <li key={item}>{item}</li>
                            ))}
                          </ul>
                        </>
                      ) : null}
                      {authoringResult.skillSpec.clarificationQuestions.length ? (
                        <>
                          <h5>追问</h5>
                          <p className="subtle admin-skills-compose__inline-subtle">
                            填写后点「继续优化」合并进草稿。
                          </p>
                          <div className="skills-authoring-clarifications">
                            {authoringResult.skillSpec.clarificationQuestions.map((item) => (
                              <label key={item} className="skills-authoring-field">
                                {item}
                                <textarea
                                  rows={2}
                                  value={clarificationAnswersByQuestion[item] ?? ""}
                                  onChange={(e) =>
                                    setClarificationAnswersByQuestion((prev) => ({
                                      ...prev,
                                      [item]: e.target.value,
                                    }))
                                  }
                                />
                              </label>
                            ))}
                          </div>
                        </>
                      ) : null}
                      {authoringSessionId ? (
                        <p className="subtle admin-skills-compose__inline-subtle">
                          会话：<code>{authoringSessionId}</code>
                        </p>
                      ) : null}
                    </div>
                  </div>
                ) : (
                  <div className="skills-authoring-readonly-empty">
                    <p className="skills-authoring-readonly-empty__text">暂无待解析的需求</p>
                  </div>
                )}
              </div>
            </div>
          </div>
        </aside>

        <section className="skills-compose__editor">
          {isPlatformManaged ? <div className="skills-compose__managed-note">平台标准技能正文由平台维护；租户侧本阶段只支持查看与启停配置。</div> : null}
          <div className="skills-compose__tabs" role="tablist" aria-label="技能字段">
            {EDITOR_TABS.map((tab) => (
              <button
                key={tab.key}
                type="button"
                className={activeEditorTab === tab.key ? "is-active" : ""}
                role="tab"
                aria-selected={activeEditorTab === tab.key}
                onClick={() => setActiveEditorTab(tab.key)}
              >
                {tab.label}
              </button>
            ))}
          </div>
          <div className="skills-compose__editor-shell">
            <div className="skills-compose__tab-panel">
              {activeEditorTab === "basic" ? (
                <div className="skills-compose__field-section">
                  <div className="skills-form-grid skills-form-grid--basic">
                    <BilingualField
                      titleZh="技能代码"
                      titleEn="skillCode"
                      hintZh="唯一标识，小写与连字符，创建后内置技能不可改。"
                      hintEn="Unique id; lowercase/kebab; immutable for built-ins."
                    >
                      <input
                        value={form.skillCode}
                        disabled={!canChangeContent}
                        onChange={(e) => setForm((prev) => ({ ...prev, skillCode: e.target.value }))}
                      />
                    </BilingualField>
                    <BilingualField
                      titleZh="显示名称"
                      titleEn="name"
                      hintZh="面向运营与编排侧展示的名称。"
                      hintEn="Display name for console and routing."
                    >
                      <input
                        value={form.name}
                        disabled={!canChangeContent}
                        onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))}
                      />
                    </BilingualField>
                    <BilingualField
                      titleZh="风险等级"
                      titleEn="riskLevel"
                      hintZh="影响编译与执行侧策略，高等级需更严格升级处理规则。"
                      hintEn="Affects policy; HIGH needs stronger escalation guardrails."
                    >
                      <select
                        value={form.riskLevel}
                        disabled={!canChangeContent}
                        onChange={(e) => setForm((prev) => ({ ...prev, riskLevel: e.target.value as SkillForm["riskLevel"] }))}
                      >
                        <option value="LOW">低风险</option>
                        <option value="MEDIUM">中风险</option>
                        <option value="HIGH">高风险</option>
                      </select>
                    </BilingualField>
                  </div>
                  <BilingualField
                    titleZh="摘要说明"
                    titleEn="description"
                    hintZh="一句话说明技能做什么，供列表与选择器展示。"
                    hintEn="One-liner for lists and pickers."
                  >
                    <textarea
                      rows={5}
                      value={form.description}
                      disabled={!canChangeContent}
                      onChange={(e) => setForm((prev) => ({ ...prev, description: e.target.value }))}
                    />
                  </BilingualField>
                </div>
              ) : null}

              {activeEditorTab === "promptFragment" ? (
                <div className="skills-compose__field-section skills-compose__single-text-tab">
                  <BilingualField
                    titleZh="提示片段"
                    titleEn="promptFragment"
                    hintZh="给模型的执行指令片段，与规格文本共同构成运行时提示。"
                    hintEn="Model-facing instruction chunk; combined with spec at runtime."
                  >
                    <textarea
                      className="skills-compose__large-textarea skills-compose__large-textarea--spec skills-compose__large-textarea--prompt"
                      value={form.promptFragment}
                      disabled={!canChangeContent}
                      onChange={(e) => setForm((prev) => ({ ...prev, promptFragment: e.target.value }))}
                    />
                  </BilingualField>
                </div>
              ) : null}

              {activeEditorTab === "draftSpec" ? (
                <div className="skills-compose__field-section skills-compose__single-text-tab">
                  <BilingualField
                    titleZh="规格正文"
                    titleEn="draftSpecText"
                    hintZh="分步骤描述抽取、判断与输出结构；用于预览编译与对齐团队口径。"
                    hintEn="Stepwise spec for compile preview and team alignment."
                  >
                    <textarea
                      className="skills-compose__large-textarea skills-compose__large-textarea--spec"
                      value={form.draftSpecText}
                      disabled={!canChangeContent}
                      onChange={(e) => setForm((prev) => ({ ...prev, draftSpecText: e.target.value }))}
                    />
                  </BilingualField>
                </div>
              ) : null}

              {activeEditorTab === "boundaries" ? (
                <div className="skills-compose__field-section skills-compose__boundary-layout">
                  <div className="skills-compose__boundary-rules">
                    <BilingualField
                      titleZh="升级处理规则"
                      titleEn="handoffRule"
                      hintZh="当技能遇到超出自动处理边界的情况时，按此规则提示用户转交人工确认或升级处理。例如价格承诺、合同条款、合规风险、权限不清、事实不足、工具执行异常、需要审批的动作。该规则会进入运行时提示词，用于约束模型不要替代人工做高风险决策。"
                      hintEn="When to escalate for human confirmation or approval."
                    >
                      <textarea
                        rows={8}
                        value={form.handoffRule}
                        disabled={!canChangeContent}
                        onChange={(e) => setForm((prev) => ({ ...prev, handoffRule: e.target.value }))}
                      />
                    </BilingualField>
                    <BilingualField
                      titleZh="输出约定"
                      titleEn="outputContract"
                      hintZh="说明模型输出应包含的字段或结构，便于质检与下游解析。"
                      hintEn="Expected fields/structure for QA and downstream parsing."
                    >
                      <textarea
                        rows={8}
                        value={form.outputContract}
                        disabled={!canChangeContent}
                        onChange={(e) => setForm((prev) => ({ ...prev, outputContract: e.target.value }))}
                      />
                    </BilingualField>
                  </div>

                  <div className="skills-compose__boundary-resources">
                    <section className="skills-whitelist-panel">
                      <div className="skills-whitelist-panel__head">
                        <h3>
                          知识库白名单
                          <HelpTip text="控制该技能可检索的知识库范围；保存时仍按知识库 ID 写入。未挂载时不限制到指定知识库。" />
                        </h3>
                        <div className="skills-whitelist-panel__meta">
                          <span>已挂载 {selectedKbIds.length}</span>
                          <button
                            type="button"
                            disabled={!canChangeContent}
                            onClick={() => openWhitelistPicker("kb")}
                          >
                            + 添加
                          </button>
                        </div>
                      </div>
                      <div className="skills-whitelist-panel__list">
                        {selectedKbIds.length === 0 ? (
                          <div className="skills-whitelist-panel__empty">尚未挂载知识库。</div>
                        ) : null}
                        {selectedKbIds.map((id) => {
                          const kb = kbs.find((item) => String(item.id) === id);
                          return (
                            <div key={id} className="skills-whitelist-row" title={kb?.description || id}>
                              <div className="skills-whitelist-row__main">
                                <span className="skills-whitelist-row__icon skills-whitelist-row__icon--kb" aria-hidden="true">KB</span>
                                <strong>{kb?.name || id}</strong>
                              </div>
                              <button
                                type="button"
                                disabled={!canChangeContent}
                                className="skills-whitelist-row__remove"
                                onClick={() => removeWhitelistValue("kb", id)}
                                aria-label="移除知识库"
                              >
                                ×
                              </button>
                            </div>
                          );
                        })}
                      </div>
                    </section>

                    <section className="skills-whitelist-panel">
                      <div className="skills-whitelist-panel__head">
                        <h3>
                          工具白名单
                          <HelpTip text="控制该技能允许调用的内置工具或 MCP 工具；保存时写入工具名列表。" />
                        </h3>
                        <div className="skills-whitelist-panel__meta">
                          <span>{selectedToolNames.length}/{toolCatalog.length} 启用</span>
                          <button
                            type="button"
                            disabled={!canChangeContent}
                            onClick={() => openWhitelistPicker("tool")}
                          >
                            + 添加
                          </button>
                        </div>
                      </div>
                      <div className="skills-whitelist-panel__list skills-whitelist-panel__list--two-cols">
                        {selectedToolNames.length === 0 ? (
                          <div className="skills-whitelist-panel__empty">尚未加入工具。</div>
                        ) : null}
                        {selectedToolNames.map((id) => {
                          const tool = toolCatalog.find((item) => item.id === id || item.name === id) ?? allMcpTools.find((item) => item.id === id || item.name === id);
                          const title = tool?.name || id;
                          const level = tool?.level || "MCP";
                          const isMcpTool = level.toUpperCase() === "MCP";
                          return (
                            <div key={id} className="skills-whitelist-row" title={`${title} · ${id}\n${tool?.description ?? ""}`}>
                              <div className="skills-whitelist-row__main">
                                <span
                                  className={`skills-whitelist-row__icon ${isMcpTool ? "skills-whitelist-row__icon--mcp" : "skills-whitelist-row__icon--tool"}`}
                                  aria-hidden="true"
                                >
                                  {renderToolGlyph(level)}
                                </span>
                                <strong>{title}</strong>
                              </div>
                              <div className="skills-whitelist-row__actions">
                                <span className="skills-whitelist-row__badge">{level}</span>
                                <button
                                  type="button"
                                  disabled={!canChangeContent}
                                  className="skills-whitelist-row__remove"
                                  onClick={() => removeWhitelistValue("tool", id)}
                                  aria-label="移除工具"
                                >
                                  ×
                                </button>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    </section>
                  </div>
                </div>
              ) : null}

              {activeEditorTab === "runtimeApis" ? (
                <div className="skills-compose__field-section skills-runtime-api-panel">
                  <div className="skills-runtime-api-panel__head">
                    <div>
                      <h3>
                        内嵌 API
                        <HelpTip text="Skill 私有远程 API，不进入普通工具白名单；模型只看到抽象函数名和参数 schema。" />
                      </h3>
                      <span>{form.runtimeApis.length} 个 API 动作</span>
                    </div>
                    <button
                      type="button"
                      className="secondary skills-compose__header-btn"
                      disabled={!canChangeContent}
                      onClick={addRuntimeApi}
                    >
                      添加 API
                    </button>
                  </div>

                  {form.runtimeApis.length === 0 ? (
                    <div className="skills-runtime-api-empty">
                      暂无内嵌 API。需要让 Skill 调用固定业务接口时，在这里添加动作并通过编译预览校验。
                    </div>
                  ) : null}

                  <div className="skills-runtime-api-list">
                    {form.runtimeApis.map((api, index) => (
                      <article key={index} className="skills-runtime-api-card">
                        <div className="skills-runtime-api-card__head">
                          <div>
                            <strong>{api.displayName.trim() || `API 动作 ${index + 1}`}</strong>
                            <span>{api.apiCode.trim() || "未填写编码"}</span>
                          </div>
                          <button
                            type="button"
                            className="skills-whitelist-row__remove"
                            disabled={!canChangeContent}
                            onClick={() => removeRuntimeApi(index)}
                            aria-label="移除内嵌 API"
                          >
                            ×
                          </button>
                        </div>

                        <div className="skills-form-grid skills-runtime-api-grid">
                          <BilingualField titleZh="API 编码" titleEn="apiCode" hintZh="仅允许小写字母、数字和下划线；发布后会生成 Skill 专属工具名。" hintEn="Lowercase code for generated tool name.">
                            <input
                              value={api.apiCode}
                              disabled={!canChangeContent}
                              onChange={(e) => updateRuntimeApi(index, "apiCode", e.target.value)}
                              placeholder="query_leads"
                            />
                          </BilingualField>
                          <BilingualField titleZh="显示名称" titleEn="displayName" hintZh="管理端识别用名称，不直接暴露给最终用户。" hintEn="Admin-facing label.">
                            <input
                              value={api.displayName}
                              disabled={!canChangeContent}
                              onChange={(e) => updateRuntimeApi(index, "displayName", e.target.value)}
                              placeholder="查询线索"
                            />
                          </BilingualField>
                          <BilingualField titleZh="风险等级" titleEn="riskLevel" hintZh="高风险 API 会强制要求确认标记，运行时未确认前不会执行。" hintEn="HIGH requires confirmation guard.">
                            <select
                              value={api.riskLevel}
                              disabled={!canChangeContent}
                              onChange={(e) => updateRuntimeApi(index, "riskLevel", e.target.value as SkillRuntimeApiForm["riskLevel"])}
                            >
                              <option value="LOW">低风险</option>
                              <option value="MEDIUM">中风险</option>
                              <option value="HIGH">高风险</option>
                            </select>
                          </BilingualField>
                          <BilingualField titleZh="HTTP 方法" titleEn="method" hintZh="第一版支持 GET、POST、PUT、PATCH、DELETE。" hintEn="Supported HTTP method.">
                            <select
                              value={api.method}
                              disabled={!canChangeContent}
                              onChange={(e) => updateRuntimeApi(index, "method", e.target.value)}
                            >
                              <option value="GET">GET</option>
                              <option value="POST">POST</option>
                              <option value="PUT">PUT</option>
                              <option value="PATCH">PATCH</option>
                              <option value="DELETE">DELETE</option>
                            </select>
                          </BilingualField>
                          <BilingualField titleZh="固定 URL" titleEn="url" hintZh="发布时校验协议、host 白名单和内网地址；URL 可引用参数模板。" hintEn="Validated fixed endpoint URL.">
                            <input
                              value={api.url}
                              disabled={!canChangeContent}
                              onChange={(e) => updateRuntimeApi(index, "url", e.target.value)}
                              placeholder="https://api.example.com/leads/search"
                            />
                          </BilingualField>
                          <BilingualField titleZh="鉴权引用" titleEn="authRef" hintZh="引用服务端凭证，不填明文密钥。当前支持 integration:tavily.apiKey、integration:cloudcc.accessToken。" hintEn="Server-side credential reference.">
                            <input
                              value={api.authRef}
                              disabled={!canChangeContent}
                              onChange={(e) => updateRuntimeApi(index, "authRef", e.target.value)}
                              placeholder="integration:cloudcc.accessToken"
                            />
                          </BilingualField>
                          <BilingualField titleZh="超时秒数" titleEn="timeoutSeconds" hintZh="运行时会限制在 1 到 30 秒。" hintEn="Clamped between 1 and 30 seconds.">
                            <input
                              type="number"
                              min={1}
                              max={30}
                              value={api.timeoutSeconds}
                              disabled={!canChangeContent}
                              onChange={(e) => updateRuntimeApi(index, "timeoutSeconds", e.target.value)}
                            />
                          </BilingualField>
                          <label className="skills-runtime-api-check">
                            <input
                              type="checkbox"
                              checked={api.riskLevel === "HIGH" || api.confirmationRequired}
                              disabled={!canChangeContent || api.riskLevel === "HIGH"}
                              onChange={(e) => updateRuntimeApi(index, "confirmationRequired", e.target.checked)}
                            />
                            <span>需要确认</span>
                          </label>
                        </div>

                        <BilingualField titleZh="调用描述" titleEn="description" hintZh="给模型看的调用时机描述，不要写 URL、Header 或密钥。" hintEn="Model-facing invocation description.">
                          <textarea
                            rows={3}
                            value={api.description}
                            disabled={!canChangeContent}
                            onChange={(e) => updateRuntimeApi(index, "description", e.target.value)}
                            placeholder="当用户询问潜在客户、线索或客户列表时调用。"
                          />
                        </BilingualField>

                        <div className="skills-runtime-api-json-grid">
                          <BilingualField titleZh="参数 schema" titleEn="inputSchema" hintZh="模型只能填写这里声明的业务参数。" hintEn="Model-visible parameter schema.">
                            <textarea
                              value={api.inputSchemaText}
                              disabled={!canChangeContent}
                              onChange={(e) => updateRuntimeApi(index, "inputSchemaText", e.target.value)}
                              spellCheck={false}
                            />
                          </BilingualField>
                          <BilingualField titleZh="请求映射" titleEn="request" hintZh="把业务参数映射到 query、headers 和 body，可用 {{field}} 模板。" hintEn="Request template mapping.">
                            <textarea
                              value={api.requestText}
                              disabled={!canChangeContent}
                              onChange={(e) => updateRuntimeApi(index, "requestText", e.target.value)}
                              spellCheck={false}
                            />
                          </BilingualField>
                          <BilingualField titleZh="返回映射" titleEn="response" hintZh="配置结果路径、最大条数、最大字节数与脱敏路径。" hintEn="Response extraction and redaction.">
                            <textarea
                              value={api.responseText}
                              disabled={!canChangeContent}
                              onChange={(e) => updateRuntimeApi(index, "responseText", e.target.value)}
                              spellCheck={false}
                            />
                          </BilingualField>
                        </div>
                      </article>
                    ))}
                  </div>
                </div>
              ) : null}

              {activeEditorTab === "versions" ? (
                <section className="skills-version-panel skills-version-panel--embedded" aria-label="版本管理">
                  <div className="skills-version-panel__head">
                    <div>
                      <h2>版本管理</h2>
                      <p>
                        {canRestoreVersions
                          ? "最近三个可恢复版本，恢复后会写回当前草稿。"
                          : form.id
                            ? "该技能当前不可在租户管理端恢复版本。"
                            : "发布或创建草稿后会自动生成 v1，后续保存和发布会进入这里。"}
                      </p>
                    </div>
                    <div className="skills-version-panel__summary" aria-label="版本摘要">
                      <span>{form.id ? `当前 ${versions.length} 个可恢复版本` : "尚未创建"}</span>
                      <span>{form.currentPublishedVersionId ? "已有发布版本" : "尚未发布"}</span>
                    </div>
                  </div>
                  <div className="skills-version-panel__list">
                    {!form.id ? (
                      <div className="skills-version-panel__empty">
                        先点击顶部「发布」或「创建草稿」。系统会创建第一个版本，之后每次保存草稿或发布都会追加版本记录。
                      </div>
                    ) : null}
                    {form.id && !canRestoreVersions ? (
                      <div className="skills-version-panel__empty">
                        平台标准或只读技能的版本由平台治理侧维护；租户管理端仅展示当前配置，不提供恢复入口。
                      </div>
                    ) : null}
                    {form.id && canRestoreVersions && versions.length === 0 ? (
                      <div className="skills-version-panel__empty">
                        暂无可恢复版本。保存草稿或发布后会在这里显示最近三版。
                      </div>
                    ) : null}
                    {canRestoreVersions ? versions.map((version) => (
                      <article key={version.id} className="skills-version-row">
                        <div>
                          <strong>v{version.versionNo}</strong>
                          <span>{version.publishStatus === "PUBLISHED" ? "已发布" : "草稿"}</span>
                          <span>{version.versionSource ?? "SAVE"}</span>
                          {version.retentionState ? <span>{version.retentionState}</span> : null}
                        </div>
                        <p>{version.changeLog || "保存技能配置"}</p>
                        <small>{version.diffSummary || "暂无差异摘要"} · {formatTs(version.createdAt)}</small>
                        <button type="button" className="secondary skills-compose__header-btn" disabled={busy} onClick={() => void restoreVersion(version)}>
                          恢复为当前草稿
                        </button>
                      </article>
                    )) : null}
                  </div>
                </section>
              ) : null}

              {activeEditorTab === "preview" ? (
                <div className="skills-preview-box">
                  <p className="skills-preview-box__intro">保存前可预览运行时编译结果，包括风险等级、有效工具、知识库和警告信息。</p>
                  {!preview ? <p className="skills-preview-box__empty">暂无预览，请点击顶部「预览编译」。</p> : null}
                  {preview ? (
                    <>
                      <p className="subtle">风险等级：{riskLabel(preview.riskLevel as Skill["riskLevel"])}</p>
                      <p className="subtle">工具：{preview.effectiveToolNames.join(", ") || "-"}</p>
                      <p className="subtle">知识库：{preview.effectiveKnowledgeBaseIds.join(", ") || "-"}</p>
                      <h5>摘要</h5>
                      <pre>{JSON.stringify(preview.compileSummary, null, 2)}</pre>
                      <h5>警告</h5>
                      <pre>{JSON.stringify(preview.warnings, null, 2)}</pre>
                      <h5>内嵌 API</h5>
                      <pre>{JSON.stringify(preview.runtimeApiPreview ?? { toolDefinitions: [], errors: [], warnings: [] }, null, 2)}</pre>
                    </>
                  ) : null}
                </div>
              ) : null}
            </div>

            <aside className="skills-compose__inspector" aria-label="技能状态摘要">
              <div>
                <span>字段完成度</span>
                <strong>{completedFieldCount}/8</strong>
              </div>
              <div>
                <span>风险等级</span>
                <strong>{riskLabel(form.riskLevel)}</strong>
              </div>
              <div>
                <span>工具</span>
                <strong>{splitCsv(form.toolWhitelistText).length}</strong>
              </div>
              <div>
                <span>知识库</span>
                <strong>{splitCsv(form.kbWhitelistText).length}</strong>
              </div>
              <div>
                <span>内嵌 API</span>
                <strong>{form.runtimeApis.length}</strong>
              </div>
              <div>
                <span>边界字段</span>
                <strong>{boundaryFieldCount}/4</strong>
              </div>
              <div>
                <span>最近预览</span>
                <strong>{preview ? "已生成" : "未运行"}</strong>
              </div>
            </aside>
          </div>
        </section>
      </section>

      {publishDialogOpen ? (
        <div
          className="cici-modal-overlay"
          role="presentation"
          onClick={(event) => {
            if (event.target === event.currentTarget && !busy) setPublishDialogOpen(false);
          }}
        >
          <div
            className="cici-modal skills-publish-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="skills-publish-modal-title"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="skills-modal-head skills-publish-modal__head">
              <div>
                <h2 id="skills-publish-modal-title">发布技能版本</h2>
                <p>填写本次版本发布说明，发布后会进入版本管理记录。</p>
              </div>
              <button
                type="button"
                className="skills-modal-close"
                onClick={() => setPublishDialogOpen(false)}
                disabled={busy}
                aria-label="关闭"
              >
                ×
              </button>
            </div>
            <label className="skills-publish-modal__field">
              <span>版本发布说明</span>
              <textarea
                autoFocus
                value={publishChangeLog}
                onChange={(event) => setPublishChangeLog(event.target.value)}
                placeholder="例如：补充合同条款升级规则，收紧工具白名单，优化输出字段。"
                rows={6}
                disabled={busy}
              />
            </label>
            <div className="skills-publish-modal__actions">
              <button type="button" className="secondary skills-compose__header-btn" onClick={() => setPublishDialogOpen(false)} disabled={busy}>
                取消
              </button>
              <button type="button" className="skills-compose__header-primary" onClick={() => void publishSkill()} disabled={busy || !publishChangeLog.trim()}>
                {busy ? "发布中..." : "确认发布"}
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {whitelistPickerOpen ? (
        <div
          className="cici-modal-overlay"
          role="presentation"
          onClick={(event) => {
            if (event.target === event.currentTarget) setWhitelistPickerOpen(null);
          }}
        >
          <div
            className="cici-modal skills-whitelist-picker"
            role="dialog"
            aria-modal="true"
            aria-labelledby="skills-whitelist-picker-title"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="skills-modal-head skills-whitelist-picker__header">
              <h2 id="skills-whitelist-picker-title">
                {whitelistPickerOpen === "kb" ? "挂载知识库" : "添加工具"}
              </h2>
              <button
                type="button"
                className="skills-modal-close"
                onClick={() => setWhitelistPickerOpen(null)}
                aria-label="关闭"
              >
                ×
              </button>
            </div>
            {whitelistPickerOpen === "tool" ? (
              <div className="skills-whitelist-picker__tabs" role="tablist" aria-label="工具来源">
                <button
                  type="button"
                  role="tab"
                  aria-selected={pickerToolTab === "tool"}
                  className={pickerToolTab === "tool" ? "is-active" : ""}
                  onClick={() => setPickerToolTab("tool")}
                >
                  工具
                </button>
                <button
                  type="button"
                  role="tab"
                  aria-selected={pickerToolTab === "mcp"}
                  className={pickerToolTab === "mcp" ? "is-active" : ""}
                  onClick={() => setPickerToolTab("mcp")}
                >
                  MCP
                </button>
              </div>
            ) : null}
            <div className="skills-whitelist-picker__list">
              {whitelistPickerOpen === "tool" && pickerToolTab === "mcp" ? (
                <>
                  {mcpServers.length === 0 ? <div className="skills-whitelist-picker__empty">暂无已配置 MCP 服务器。</div> : null}
                  {mcpServers.map((server) => {
                    const expanded = expandedMcpServerIds.includes(server.id);
                    const loading = Boolean(mcpServerLoading[server.id]);
                    const tools = mcpToolsByServer[server.id] ?? [];
                    const selectedCount = tools.reduce((count, tool) => (
                      whitelistPickerSelection.includes(tool.id) ? count + 1 : count
                    ), 0);
                    const totalCount = server.toolCacheCount > 0 ? server.toolCacheCount : tools.length;
                    return (
                      <section key={server.id} className="skills-whitelist-picker__group">
                        <button
                          type="button"
                          className="skills-whitelist-picker__group-toggle"
                          onClick={() => toggleMcpServerExpanded(server)}
                          aria-expanded={expanded}
                        >
                          <span>{server.name}</span>
                          <span className="skills-whitelist-picker__group-meta">
                            <span>{selectedCount} / {totalCount}</span>
                            <span>{server.enabled ? "已启用" : "未启用"}</span>
                          </span>
                        </button>
                        {expanded ? (
                          <div className="skills-whitelist-picker__group-body">
                            {loading ? <div className="skills-whitelist-picker__empty">正在加载工具…</div> : null}
                            {!loading && tools.length === 0 ? (
                              <div className="skills-whitelist-picker__empty">该服务器暂无缓存工具，请先到 MCP 管理页手动刷新。</div>
                            ) : null}
                            {!loading && tools.map((item) => {
                              const checked = whitelistPickerSelection.includes(item.id);
                              return (
                                <label key={`${server.id}-${item.id}`} className={`skills-whitelist-picker__item${checked ? " is-checked" : ""}`}>
                                  <input
                                    type="checkbox"
                                    checked={checked}
                                    onChange={(event) => toggleWhitelistPickerItem(item.id, event.target.checked)}
                                  />
                                  <span className="skills-whitelist-picker__text">
                                    <strong>{item.name}</strong>
                                    {item.description ? <small>{item.description}</small> : null}
                                  </span>
                                  <span className="skills-whitelist-row__badge">{item.level}</span>
                                </label>
                              );
                            })}
                          </div>
                        ) : null}
                      </section>
                    );
                  })}
                </>
              ) : null}
              {(whitelistPickerOpen !== "tool" || pickerToolTab === "tool") && pickerItems.length === 0 ? (
                <div className="skills-whitelist-picker__empty">
                  {whitelistPickerOpen === "kb" ? "当前组织暂无知识库，可先在管理端完成导入。" : "暂无可选工具。"}
                </div>
              ) : null}
              {(whitelistPickerOpen !== "tool" || pickerToolTab === "tool") && pickerItems.map((item) => {
                const checked = whitelistPickerSelection.includes(item.key);
                return (
                  <label key={item.key} className={`skills-whitelist-picker__item${checked ? " is-checked" : ""}`}>
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={(event) => toggleWhitelistPickerItem(item.key, event.target.checked)}
                    />
                    <span className="skills-whitelist-picker__text">
                      <strong>{item.title}</strong>
                      {item.subtitle ? <small>{item.subtitle}</small> : null}
                    </span>
                    {"tag" in item && item.tag ? <span className="skills-whitelist-row__badge">{item.tag}</span> : null}
                  </label>
                );
              })}
            </div>
            <div className="skills-whitelist-picker__footer">
              <button type="button" className="skills-compose__header-btn" onClick={() => setWhitelistPickerOpen(null)}>
                取消
              </button>
              <button type="button" className="skills-compose__header-primary" onClick={confirmWhitelistPicker}>
                确认 ({whitelistPickerSelection.length})
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {libraryModalOpen ? (
        <div className="cici-modal-overlay" role="presentation" onClick={() => setLibraryModalOpen(false)}>
          <div
            className="cici-modal skills-library-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="skills-library-modal-title"
            onClick={(e) => e.stopPropagation()}
          >
            <h2 id="skills-library-modal-title" className="cici-modal__title">
              技能库
            </h2>
            <div className="cici-modal__body skills-library-modal__body">
              <p className="skills-library-modal__hint">
                选模板填充表单并关闭，或一键落库。
              </p>
              <div className="skills-library-modal__grid">
                {CRM_TEMPLATES.map((template) => (
                  <article key={template.key} className="skills-lib-card">
                    <div className="skills-lib-card__top">
                      <div className="skills-lib-card__icon" aria-hidden>
                        {template.title.trim().charAt(0) || "技"}
                      </div>
                      <div className="skills-lib-card__head-text">
                        <span className="skills-lib-card__tag">{templateSceneLabel(template.scene)}</span>
                        <h3 className="skills-lib-card__title">{template.title}</h3>
                      </div>
                    </div>
                    <p className="skills-lib-card__desc">{template.summary}</p>
                    <div className="skills-lib-card__actions">
                      <button
                        type="button"
                        className="secondary skills-lib-card__action-btn"
                        onClick={() => pickTemplateForInit(template)}
                        disabled={busy}
                      >
                        导入初始化
                      </button>
                      <button
                        type="button"
                        className="skills-lib-card__action-btn skills-lib-card__action-btn--primary"
                        onClick={() => void pickTemplateForCreate(template)}
                        disabled={busy}
                      >
                        一键创建
                      </button>
                    </div>
                  </article>
                ))}
              </div>
            </div>
            <div className="cici-modal__actions">
              <button type="button" className="cici-btn cici-btn--ghost" onClick={() => setLibraryModalOpen(false)}>
                关闭
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
