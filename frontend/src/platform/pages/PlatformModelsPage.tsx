import { useEffect, useMemo, useState } from "react";
import { LS_PLATFORM_TOKEN, PLATFORM_API_BASE } from "../../constants";
import { safeFetchJson } from "../../utils/http";

type ProviderConfig = {
  id: number;
  providerCode: string;
  providerName: string;
  enabled: boolean;
  apiBaseUrl: string;
  apiKeyMasked: string;
  apiKeySet: boolean;
  apiKeyRequired?: boolean;
  defaultBaseUrl: string;
  docUrl: string;
};

type ProviderModelsPayload = {
  providerCode: string;
  providerName: string;
  recommendedModels: string[];
  selectedModels?: string[];
  modelCapabilities?: Record<string, string[]>;
  modelCapabilityConfirmations?: Record<string, ModelCapabilityConfirmation>;
};

type FetchModelDetailPayload = {
  modelName: string;
  capabilities?: string[];
};

type FetchModelsPayload = {
  models?: string[];
  modelDetails?: FetchModelDetailPayload[];
  modelCapabilities?: Record<string, string[]>;
  modelCapabilityConfirmations?: Record<string, ModelCapabilityConfirmation>;
  remoteFetchSupported?: boolean;
  catalogSource?: string;
};

type ModelCandidate = {
  providerCode: string;
  providerName: string;
  modelName: string;
  displayLabel?: string;
  capabilities?: string[];
};

type ModelCapabilityConfirmation = {
  source: string;
  sourceLabel?: string;
  confirmedAt?: string;
  confirmedBy?: string;
  revocable?: boolean;
};

type CapabilityConfirmationTarget = {
  providerCode: string;
  providerName: string;
  modelName: string;
};

type ModelRoute = {
  sceneCode: string;
  displayName: string;
  description: string;
  providerCode: string;
  providerName: string;
  modelName: string;
  configured: boolean;
  available: boolean;
  requiredCapabilities?: string[];
  recommendation?: string;
  candidates?: ModelCandidate[];
  recommendedCandidates?: ModelCandidate[];
  candidateCount?: number;
  unavailableReason?: string;
};

type CatalogSource = "remote" | "unavailable";

type CapabilityKey = "text" | "tool" | "reasoning" | "vision" | "embedding" | "realtime-asr" | "file-asr" | "code-interpreter" | "web-search" | "web-extractor";
type ModelConfigTab = "providers" | "routes";

const PROVIDER_ORDER = ["aliyun-bailian", "deepseek", "ollama-local", "lmstudio-local", "onekeytoken", "anthropic", "openai"];
const providerRank = new Map(PROVIDER_ORDER.map((code, idx) => [code, idx]));

const PROVIDER_ICON_URLS: Record<string, string> = {
  "aliyun-bailian": "/provider-logos/aliyun-bailian.svg",
  deepseek: "/provider-logos/deepseek.svg",
  "ollama-local": "/provider-logos/ollama.svg",
  "lmstudio-local": "/provider-logos/lmstudio.webp",
  onekeytoken: "/provider-logos/onekeytoken.png",
  anthropic: "/provider-logos/anthropic.svg",
  openai: "/provider-logos/openai.svg",
};

const DEFAULT_PROVIDER_ICON_URL = "/provider-logos/aliyun-bailian.svg";

const CAPABILITY_META: Record<CapabilityKey, { label: string; icon: string; tone: string }> = {
  text: { label: "文本", icon: "T", tone: "text" },
  tool: { label: "工具", icon: "W", tone: "tool" },
  reasoning: { label: "推理", icon: "R", tone: "reasoning" },
  vision: { label: "视觉", icon: "V", tone: "vision" },
  embedding: { label: "向量", icon: "E", tone: "embedding" },
  "realtime-asr": { label: "实时 ASR", icon: "A", tone: "asr" },
  "file-asr": { label: "文件 ASR", icon: "A", tone: "asr" },
  "code-interpreter": { label: "代码解释器", icon: "C", tone: "code" },
  "web-search": { label: "联网搜索", icon: "S", tone: "search" },
  "web-extractor": { label: "网页抓取", icon: "X", tone: "extract" },
};

function readToken(): string {
  const raw = localStorage.getItem(LS_PLATFORM_TOKEN);
  if (!raw) return "";
  try {
    return (JSON.parse(raw) as { token?: string }).token ?? "";
  } catch {
    return "";
  }
}

function dedupeModels(names: string[]) {
  return [...new Set(names.filter(Boolean).map((name) => name.trim()).filter(Boolean))];
}

function normalizeCapability(cap: string): CapabilityKey | null {
  const v = cap.trim().toLowerCase();
  if (!v) return null;
  if (v.includes("tool") || v.includes("function")) return "tool";
  if (v.includes("embedding") || v.includes("embed")) return "embedding";
  if (v.includes("realtime") && (v.includes("asr") || v.includes("transcri"))) return "realtime-asr";
  if ((v.includes("file") || v.includes("batch")) && (v.includes("asr") || v.includes("transcri"))) return "file-asr";
  if (v.includes("asr") || v.includes("transcri")) return "file-asr";
  if (v.includes("code") && v.includes("interpreter")) return "code-interpreter";
  if (v.includes("extract") || v.includes("crawler")) return "web-extractor";
  if (v.includes("search") || v.includes("web") || v.includes("internet")) return "web-search";
  if (v.includes("reason") || v.includes("thinking") || v.includes("logic")) return "reasoning";
  if (v.includes("vision") || v.includes("image") || v.includes("multimodal") || v.includes("video")) return "vision";
  if (v.includes("text") || v.includes("chat")) return "text";
  return null;
}

function selectedModelKey(providerCode: string, modelName: string) {
  return `${providerCode}::${modelName}`;
}

export function buildProviderCheckRequest(enabled: boolean, apiBaseUrl: string, apiKey: string) {
  return {
    enabled,
    apiBaseUrl: apiBaseUrl.trim(),
    apiKey: apiKey.trim(),
  };
}

export function readValidatedModel(data: unknown): string {
  if (!data || typeof data !== "object") return "";
  const value = (data as { validatedModel?: unknown }).validatedModel;
  return typeof value === "string" ? value.trim() : "";
}

export function readResolvedModel(data: unknown): string {
  if (!data || typeof data !== "object") return "";
  const value = (data as { resolvedModel?: unknown }).resolvedModel;
  return typeof value === "string" ? value.trim() : "";
}

export function catalogEmptyMessage(catalogSource: CatalogSource, modelCount: number): string {
  if (catalogSource === "unavailable" && modelCount === 0) {
    return "当前厂商未开放远程模型枚举，暂无可选模型。";
  }
  return "没有匹配的模型";
}

export function capabilityConfirmationError(capabilities: CapabilityKey[]): string {
  return capabilities.length === 0 ? "请至少选择一项模型能力。" : "";
}

export function modelApiFailureMessage(status: number, message: string | undefined, rawText: string, fallback: string): string {
  if (message?.trim()) return message.trim();
  if (rawText.trimStart().startsWith("<")) {
    return `${fallback}：服务未返回预期数据（HTTP ${status}）。请刷新页面并确认前后端版本一致后重试。`;
  }
  return `${fallback}（HTTP ${status}）。请稍后重试。`;
}

export default function PlatformModelsPage() {
  const token = readToken();
  const [providers, setProviders] = useState<ProviderConfig[]>([]);
  const [selectedProvider, setSelectedProvider] = useState("aliyun-bailian");
  const [apiBaseUrl, setApiBaseUrl] = useState("");
  const [apiKey, setApiKey] = useState("");
  const [providerEnabled, setProviderEnabled] = useState(true);
  const [showApiKey, setShowApiKey] = useState(false);
  const [selectedModels, setSelectedModels] = useState<Set<string>>(new Set());
  const [providerModels, setProviderModels] = useState<string[]>([]);
  const [modelRoutes, setModelRoutes] = useState<ModelRoute[]>([]);
  const [activeConfigTab, setActiveConfigTab] = useState<ModelConfigTab>("providers");
  const [capabilityMap, setCapabilityMap] = useState<Record<string, CapabilityKey[]>>({});
  const [capabilityConfirmationMap, setCapabilityConfirmationMap] = useState<Record<string, ModelCapabilityConfirmation>>({});
  const [capabilityConfirmation, setCapabilityConfirmation] = useState<CapabilityConfirmationTarget | null>(null);
  const [capabilityDraft, setCapabilityDraft] = useState<CapabilityKey[]>([]);
  const [capabilityFormError, setCapabilityFormError] = useState("");
  const [capabilityRevokeTarget, setCapabilityRevokeTarget] = useState<CapabilityConfirmationTarget | null>(null);
  const [allModelsOpen, setAllModelsOpen] = useState(false);
  const [allModelsLoading, setAllModelsLoading] = useState(false);
  const [allModelsSearch, setAllModelsSearch] = useState("");
  const [allModelsCatalogSource, setAllModelsCatalogSource] = useState<CatalogSource>("remote");
  const [validatedModel, setValidatedModel] = useState("");
  const [resolvedModel, setResolvedModel] = useState("");
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const authHeaders = useMemo(() => ({ Authorization: `Bearer ${token}`, Accept: "application/json" }), [token]);
  const orderedProviders = useMemo(
    () => [...providers].sort((a, b) => (providerRank.get(a.providerCode) ?? 99) - (providerRank.get(b.providerCode) ?? 99)),
    [providers],
  );
  const selected = useMemo(
    () => providers.find((provider) => provider.providerCode === selectedProvider) ?? null,
    [providers, selectedProvider],
  );
  const selectedModelsForCurrentProvider = useMemo(() => {
    if (!selected) return [];
    const prefix = `${selected.providerCode}::`;
    return Array.from(selectedModels)
      .filter((key) => key.startsWith(prefix))
      .map((key) => key.slice(prefix.length))
      .sort((a, b) => a.localeCompare(b));
  }, [selected, selectedModels]);
  const filteredModels = useMemo(() => {
    const kw = allModelsSearch.trim().toLowerCase();
    if (!kw) return providerModels;
    return providerModels.filter((name) => name.toLowerCase().includes(kw));
  }, [allModelsSearch, providerModels]);
  const configuredRouteCount = useMemo(() => modelRoutes.filter((route) => route.configured).length, [modelRoutes]);

  function setSelectedModelsForProvider(providerCode: string, modelNames: string[]) {
    const nextNames = dedupeModels(modelNames);
    setSelectedModels((prev) => {
      const next = new Set(Array.from(prev).filter((key) => !key.startsWith(`${providerCode}::`)));
      nextNames.forEach((name) => next.add(selectedModelKey(providerCode, name)));
      return next;
    });
  }

  function selectedModelNamesOfProvider(providerCode: string) {
    const prefix = `${providerCode}::`;
    return Array.from(selectedModels)
      .filter((key) => key.startsWith(prefix))
      .map((key) => key.slice(prefix.length))
      .filter(Boolean);
  }

  function applyProviderCapabilityData(
    providerCode: string,
    rawCapabilities: Record<string, string[]> | undefined,
    rawConfirmations: Record<string, ModelCapabilityConfirmation> | undefined,
  ) {
    const prefix = `${providerCode}::`;
    setCapabilityMap((prev) => {
      const next = Object.fromEntries(Object.entries(prev).filter(([key]) => !key.startsWith(prefix))) as Record<string, CapabilityKey[]>;
      Object.entries(rawCapabilities ?? {}).forEach(([name, capabilities]) => {
        next[selectedModelKey(providerCode, name)] = capabilities
          .map((capability) => normalizeCapability(capability))
          .filter((capability): capability is CapabilityKey => capability !== null);
      });
      return next;
    });
    setCapabilityConfirmationMap((prev) => {
      const next = Object.fromEntries(Object.entries(prev).filter(([key]) => !key.startsWith(prefix))) as Record<string, ModelCapabilityConfirmation>;
      Object.entries(rawConfirmations ?? {}).forEach(([name, confirmation]) => {
        next[selectedModelKey(providerCode, name)] = confirmation;
      });
      return next;
    });
  }

  function openCapabilityConfirmation(providerCode: string, providerName: string, modelName: string) {
    setCapabilityConfirmation({ providerCode, providerName, modelName });
    setCapabilityDraft([]);
    setCapabilityFormError("");
  }

  function toggleCapabilityDraft(capability: CapabilityKey) {
    setCapabilityDraft((current) => current.includes(capability)
      ? current.filter((item) => item !== capability)
      : [...current, capability]);
  }

  async function loadProviders() {
    if (!token) return;
    setError("");
    try {
      const res = await fetch(`${PLATFORM_API_BASE}/models/providers`, { headers: authHeaders });
      const json = await res.json();
      if (!res.ok || !json.success) throw new Error(json.message || "加载模型厂商失败");
      const rows = (json.data ?? []) as ProviderConfig[];
      setProviders(rows);
      const nextCode = rows.some((provider) => provider.providerCode === selectedProvider)
        ? selectedProvider
        : rows[0]?.providerCode || "";
      setSelectedProvider(nextCode);
    } catch (err) {
      setError(err instanceof Error ? err.message : "加载模型厂商失败");
    }
  }

  async function loadProviderModels(providerCode: string) {
    if (!token || !providerCode) return;
    try {
      const res = await fetch(`${PLATFORM_API_BASE}/models/providers/${encodeURIComponent(providerCode)}/models`, {
        headers: authHeaders,
      });
      const json = await res.json();
      if (!res.ok || !json.success) throw new Error(json.message || "加载模型列表失败");
      const data = (json.data ?? {}) as ProviderModelsPayload;
      const merged = dedupeModels([...(data.recommendedModels ?? []), ...(data.selectedModels ?? [])]);
      setProviderModels(merged);
      setSelectedModelsForProvider(providerCode, data.selectedModels ?? []);
      applyProviderCapabilityData(providerCode, data.modelCapabilities, data.modelCapabilityConfirmations);
    } catch (err) {
      setError(err instanceof Error ? err.message : "加载模型列表失败");
    }
  }

  async function loadModelRoutes() {
    if (!token) return;
    try {
      const res = await fetch(`${PLATFORM_API_BASE}/models/routes`, { headers: authHeaders });
      const json = await res.json();
      if (!res.ok || !json.success) throw new Error(json.message || "加载场景模型路由失败");
      setModelRoutes((json.data?.routes ?? []) as ModelRoute[]);
    } catch (err) {
      setError(err instanceof Error ? err.message : "加载场景模型路由失败");
    }
  }

  async function saveProvider(enabledOverride?: boolean) {
    if (!selected) return;
    const finalEnabled = typeof enabledOverride === "boolean" ? enabledOverride : providerEnabled;
    setBusy(true);
    setNotice("");
    setError("");
    try {
      const payload: { enabled: boolean; apiBaseUrl?: string; apiKey?: string } = { enabled: finalEnabled };
      if (apiBaseUrl.trim()) payload.apiBaseUrl = apiBaseUrl.trim();
      if (apiKey.trim()) payload.apiKey = apiKey.trim();
      const res = await fetch(`${PLATFORM_API_BASE}/models/providers/${encodeURIComponent(selected.providerCode)}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json", ...authHeaders },
        body: JSON.stringify(payload),
      });
      const json = await res.json();
      if (!res.ok || !json.success) throw new Error(json.message || "保存模型厂商失败");
      setApiKey("");
      setProviderEnabled(finalEnabled);
      setNotice("模型厂商配置已保存。");
      await loadProviders();
      await loadProviderModels(selected.providerCode);
    } catch (err) {
      setError(err instanceof Error ? err.message : "保存模型厂商失败");
    } finally {
      setBusy(false);
    }
  }

  async function checkProvider() {
    if (!selected) return;
    setBusy(true);
    setNotice("");
    setError("");
    try {
      const res = await fetch(`${PLATFORM_API_BASE}/models/providers/${encodeURIComponent(selected.providerCode)}/check`, {
        method: "POST",
        headers: { "Content-Type": "application/json", ...authHeaders },
        body: JSON.stringify(buildProviderCheckRequest(providerEnabled, apiBaseUrl, apiKey)),
      });
      const json = await res.json();
      if (!res.ok || !json.success) throw new Error(json.message || "检测失败");
      const checkedModel = readValidatedModel(json.data);
      const checkedResolvedModel = readResolvedModel(json.data);
      setValidatedModel(checkedModel);
      setResolvedModel(checkedResolvedModel);
      const validatedModelNotice = checkedModel
        ? `，已验证路由模型 ${checkedModel}`
        : "";
      const resolvedModelNotice = checkedResolvedModel && checkedResolvedModel !== checkedModel
        ? `，本次实际路由 ${checkedResolvedModel}`
        : "";
      setNotice(`检测成功${validatedModelNotice}${resolvedModelNotice}，可用模型 ${Number(json.data?.modelCount ?? 0)} 个。`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "检测失败");
    } finally {
      setBusy(false);
    }
  }

  async function fetchModels() {
    if (!selected) return;
    setBusy(true);
    setAllModelsLoading(true);
    setAllModelsOpen(true);
    setNotice("");
    setError("");
    setAllModelsCatalogSource("remote");
    try {
      const res = await fetch(`${PLATFORM_API_BASE}/models/providers/${encodeURIComponent(selected.providerCode)}/models/fetch`, {
        method: "POST",
        headers: authHeaders,
      });
      const json = await res.json();
      if (!res.ok || !json.success) throw new Error(json.message || "拉取模型列表失败");
      const data = (json.data ?? {}) as FetchModelsPayload;
      const models = dedupeModels(data.models ?? []).sort((a, b) => a.localeCompare(b));
      const remoteUnavailable = data.remoteFetchSupported === false || data.catalogSource === "unavailable";
      setProviderModels(models);
      setAllModelsCatalogSource(remoteUnavailable ? "unavailable" : "remote");
      applyProviderCapabilityData(selected.providerCode, data.modelCapabilities, data.modelCapabilityConfirmations);
      if (remoteUnavailable) {
        setNotice("当前厂商未开放远程模型枚举，暂无可选模型。");
      } else {
        setNotice(`已拉取 ${models.length} 个模型。`);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "拉取模型列表失败");
    } finally {
      setAllModelsLoading(false);
      setBusy(false);
    }
  }

  async function saveSelectedModels(providerCode: string, modelNames: string[]) {
    const nextNames = dedupeModels(modelNames);
    const res = await fetch(`${PLATFORM_API_BASE}/models/providers/${encodeURIComponent(providerCode)}/selected-models`, {
      method: "PUT",
      headers: { "Content-Type": "application/json", ...authHeaders },
      body: JSON.stringify({ selectedModels: nextNames }),
    });
    const json = await res.json();
    if (!res.ok || !json.success) throw new Error(json.message || "保存已选模型失败");
    setSelectedModelsForProvider(providerCode, nextNames);
    setNotice("已选模型已更新。");
    await loadModelRoutes();
  }

  async function saveModelRoute(sceneCode: string, value: string) {
    if (!value) {
      await clearModelRoute(sceneCode);
      return;
    }
    const [providerCode, ...rest] = value.split("::");
    const modelName = rest.join("::");
    if (!providerCode || !modelName) return;
    setBusy(true);
    setNotice("");
    setError("");
    try {
      const res = await fetch(`${PLATFORM_API_BASE}/models/routes/${encodeURIComponent(sceneCode)}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json", ...authHeaders },
        body: JSON.stringify({ providerCode, modelName }),
      });
      const json = await res.json();
      if (!res.ok || !json.success) throw new Error(json.message || "保存场景模型路由失败");
      setNotice("场景模型路由已更新。");
      await loadModelRoutes();
    } catch (err) {
      setError(err instanceof Error ? err.message : "保存场景模型路由失败");
    } finally {
      setBusy(false);
    }
  }

  async function clearModelRoute(sceneCode: string) {
    setBusy(true);
    setNotice("");
    setError("");
    try {
      const res = await fetch(`${PLATFORM_API_BASE}/models/routes/${encodeURIComponent(sceneCode)}`, {
        method: "DELETE",
        headers: authHeaders,
      });
      const json = await res.json();
      if (!res.ok || !json.success) throw new Error(json.message || "清除场景模型路由失败");
      setNotice("场景模型路由已清除。");
      await loadModelRoutes();
    } catch (err) {
      setError(err instanceof Error ? err.message : "清除场景模型路由失败");
    } finally {
      setBusy(false);
    }
  }

  async function toggleSelectedModel(modelName: string) {
    if (!selected) return;
    const current = new Set(selectedModelNamesOfProvider(selected.providerCode));
    if (current.has(modelName)) current.delete(modelName);
    else current.add(modelName);
    try {
      await saveSelectedModels(selected.providerCode, Array.from(current));
    } catch (err) {
      setError(err instanceof Error ? err.message : "保存已选模型失败");
    }
  }

  async function addValidatedModel() {
    if (!selected || !validatedModel) return;
    setBusy(true);
    setError("");
    try {
      const nextModels = dedupeModels([...selectedModelNamesOfProvider(selected.providerCode), validatedModel]);
      await saveSelectedModels(selected.providerCode, nextModels);
      setProviderModels((current) => dedupeModels([...current, validatedModel]));
      setNotice(`已将检测确认的路由别名 ${validatedModel} 加入平台模型目录。`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "加入平台模型目录失败");
    } finally {
      setBusy(false);
    }
  }

  async function saveCapabilityConfirmation() {
    if (!capabilityConfirmation) return;
    const validationError = capabilityConfirmationError(capabilityDraft);
    if (validationError) {
      setCapabilityFormError(validationError);
      return;
    }
    setBusy(true);
    setCapabilityFormError("");
    setNotice("");
    setError("");
    try {
      const res = await fetch(
        `${PLATFORM_API_BASE}/models/providers/${encodeURIComponent(capabilityConfirmation.providerCode)}/model-capabilities`,
        {
          method: "PUT",
          headers: { "Content-Type": "application/json", ...authHeaders },
          body: JSON.stringify({
            modelName: capabilityConfirmation.modelName,
            capabilities: capabilityDraft,
          }),
        },
      );
      const { body, rawText } = await safeFetchJson(res);
      if (!res.ok || !body?.success) {
        throw new Error(modelApiFailureMessage(res.status, body?.message, rawText, "确认模型能力失败"));
      }
      const { providerCode, modelName } = capabilityConfirmation;
      setCapabilityConfirmation(null);
      setNotice(`已确认 ${modelName} 的模型能力，可在匹配场景中配置路由。`);
      await Promise.all([loadProviderModels(providerCode), loadModelRoutes()]);
    } catch (err) {
      setCapabilityFormError(err instanceof Error ? err.message : "确认模型能力失败");
    } finally {
      setBusy(false);
    }
  }

  async function revokeCapabilityConfirmation() {
    if (!capabilityRevokeTarget) return;
    setBusy(true);
    setNotice("");
    setError("");
    try {
      const res = await fetch(
        `${PLATFORM_API_BASE}/models/providers/${encodeURIComponent(capabilityRevokeTarget.providerCode)}/model-capabilities?modelName=${encodeURIComponent(capabilityRevokeTarget.modelName)}`,
        { method: "DELETE", headers: authHeaders },
      );
      const { body, rawText } = await safeFetchJson(res);
      if (!res.ok || !body?.success) {
        throw new Error(modelApiFailureMessage(res.status, body?.message, rawText, "撤销模型能力确认失败"));
      }
      const { providerCode, modelName } = capabilityRevokeTarget;
      setCapabilityRevokeTarget(null);
      setNotice(`已撤销 ${modelName} 的人工能力确认；该模型不再可用于场景路由。`);
      await Promise.all([loadProviderModels(providerCode), loadModelRoutes()]);
    } catch (err) {
      setError(err instanceof Error ? err.message : "撤销模型能力确认失败");
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    void loadProviders();
    void loadModelRoutes();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  useEffect(() => {
    if (!selected) return;
    setApiBaseUrl(selected.apiBaseUrl || selected.defaultBaseUrl);
    setApiKey("");
    setProviderEnabled(Boolean(selected.enabled));
    setValidatedModel("");
    setResolvedModel("");
    void loadProviderModels(selected.providerCode);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selected?.providerCode, selected?.enabled]);

  return (
    <div className="admin-page platform-page platform-models-page">
      <header className="skills-catalog__header">
        <div className="platform-page-head__main">
          <h1 className="skills-catalog__title">模型配置</h1>
          <p className="subtle skills-catalog__subtitle">统一控制模型厂商、凭据、可用模型、运行时模型目录和场景路由。</p>
        </div>
        <div className="platform-page-head__aside">
          <span className="platform-inline-stat">厂商 {providers.length}</span>
          <span className="platform-inline-stat">已启用 {providers.filter((provider) => provider.enabled).length}</span>
          <span className="platform-inline-stat">已选模型 {selectedModelsForCurrentProvider.length}</span>
          <span className="platform-inline-stat">路由 {configuredRouteCount}</span>
        </div>
      </header>

      {error ? <div className="platform-console__banner platform-console__banner--error">{error}</div> : null}
      {notice ? <div className="platform-console__banner platform-console__banner--success">{notice}</div> : null}

      <div className="model-config-tabs" role="tablist" aria-label="模型配置">
        <button
          type="button"
          role="tab"
          aria-selected={activeConfigTab === "providers"}
          className={`model-config-tab${activeConfigTab === "providers" ? " is-active" : ""}`}
          onClick={() => setActiveConfigTab("providers")}
        >
          模型厂商治理
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeConfigTab === "routes"}
          className={`model-config-tab${activeConfigTab === "routes" ? " is-active" : ""}`}
          onClick={() => setActiveConfigTab("routes")}
        >
          场景模型路由
        </button>
      </div>

      {activeConfigTab === "providers" ? (
        <div className="model-config-tab-panel">
          <div className="model-center platform-models-center">
            <aside className="model-provider-list">
              <div className="model-provider-list__title">平台模型厂商</div>
              {orderedProviders.map((provider) => (
                <button
                  key={provider.providerCode}
                  type="button"
                  className={`model-provider-item${selectedProvider === provider.providerCode ? " is-active" : ""}`}
                  onClick={() => setSelectedProvider(provider.providerCode)}
                >
                  <span className="model-provider-item__icon">
                    <img
                      src={PROVIDER_ICON_URLS[provider.providerCode] || DEFAULT_PROVIDER_ICON_URL}
                      alt={provider.providerName}
                      className="model-provider-item__img"
                    />
                  </span>
                  <span className="model-provider-item__name">{provider.providerName}</span>
                  <span className={`model-provider-item__status ${provider.enabled ? "on" : "off"}`}>
                    {provider.enabled ? "ON" : "OFF"}
                  </span>
                </button>
              ))}
            </aside>

            <section className="model-provider-main">
              {selected ? (
                <>
                  <div className="model-provider-main__head">
                    <h3>{selected.providerName}</h3>
                    <a href={selected.docUrl} target="_blank" rel="noreferrer" className="text-link">
                      文档
                    </a>
                  </div>

                  <div className="model-form-grid">
                    <label className="cici-field">
                      <span className="cici-field__label">API Key</span>
                      <div className="model-key-row">
                        <input
                          className="cici-field__input"
                          type={showApiKey ? "text" : "password"}
                          value={apiKey}
                          onChange={(event) => setApiKey(event.target.value)}
                          placeholder={
                            selected.apiKeySet
                              ? `已配置：${selected.apiKeyMasked}`
                              : selected.apiKeyRequired === false
                                ? "本地服务通常无需 API Key"
                                : "请输入 API Key"
                          }
                        />
                        <button type="button" className="cici-btn cici-btn--ghost" onClick={() => setShowApiKey((value) => !value)}>
                          {showApiKey ? "隐藏" : "显示"}
                        </button>
                        <button type="button" className="cici-btn cici-btn--ghost" onClick={() => setApiKey("")}>
                          重置
                        </button>
                      </div>
                    </label>

                    <label className="cici-field">
                      <span className="cici-field__label">API 地址</span>
                      <input
                        className="cici-field__input"
                        value={apiBaseUrl}
                        onChange={(event) => setApiBaseUrl(event.target.value)}
                        placeholder={selected.defaultBaseUrl}
                      />
                    </label>

                    <div className="model-actions-row">
                      <label className="kb-check">
                        <input
                          type="checkbox"
                          checked={providerEnabled}
                          onChange={(event) => {
                            const nextEnabled = event.target.checked;
                            setProviderEnabled(nextEnabled);
                            void saveProvider(nextEnabled);
                          }}
                        />
                        <span>启用厂商</span>
                      </label>
                      <div className="row">
                        <button type="button" className="platform-button platform-button--secondary" onClick={() => void checkProvider()} disabled={busy}>
                          检测
                        </button>
                        <button type="button" className="platform-button platform-button--primary" onClick={() => void saveProvider()} disabled={busy}>
                          保存
                        </button>
                        <button type="button" className="platform-button platform-button--secondary" onClick={() => void fetchModels()} disabled={busy}>
                          全部模型
                        </button>
                      </div>
                    </div>
                  </div>

                  {selected.providerCode === "onekeytoken" && validatedModel ? (
                    <div className="platform-console__banner platform-console__banner--success">
                      <span>
                        本次检测已确认可用路由别名：{validatedModel}
                        {resolvedModel && resolvedModel !== validatedModel ? `；本次实际路由：${resolvedModel}` : ""}
                      </span>
                      <button
                        type="button"
                        className="platform-button platform-button--primary"
                        onClick={() => void addValidatedModel()}
                        disabled={busy || selectedModels.has(selectedModelKey(selected.providerCode, validatedModel))}
                      >
                        {selectedModels.has(selectedModelKey(selected.providerCode, validatedModel)) ? "已加入路由目录" : "加入路由目录"}
                      </button>
                    </div>
                  ) : null}

                  <div className="model-section model-section--target">
                    <div className="model-section__head">
                      <div>
                        <h4>平台已选模型</h4>
                        <p className="model-capability-guidance">
                          先将模型加入平台目录，再人工确认可用于场景路由的能力。
                        </p>
                      </div>
                      <span className="model-count-badge">已选 {selectedModelsForCurrentProvider.length} 个</span>
                    </div>
                    {selectedModelsForCurrentProvider.length === 0 ? (
                      <p className="subtle">暂无已选模型。点击“全部模型”后把允许运行的模型加入目录。</p>
                    ) : (
                      <div className="provider-model-board">
                        {selectedModelsForCurrentProvider.map((name) => {
                          const caps = capabilityMap[selectedModelKey(selected.providerCode, name)] ?? [];
                          const confirmation = capabilityConfirmationMap[selectedModelKey(selected.providerCode, name)];
                          return (
                            <div className="provider-model-row" key={`${selected.providerCode}-${name}`}>
                              <div className="provider-model-row__left">
                                <span className="provider-model-row__logo">
                                  <img
                                    src={PROVIDER_ICON_URLS[selected.providerCode] || DEFAULT_PROVIDER_ICON_URL}
                                    alt={selected.providerName}
                                    className="provider-model-row__logo-img"
                                  />
                                </span>
                                <div className="provider-model-row__name-wrap">
                                  <div className="provider-model-row__name">{name}</div>
                                  <div className="provider-model-row__hint">运行时目录由平台统一控制</div>
                                </div>
                              </div>
                              <div className="provider-model-row__right">
                                <div className="provider-model-row__caps">
                                  {caps.map((cap) => {
                                    const meta = CAPABILITY_META[cap];
                                    return (
                                      <span key={`${name}-${cap}`} className={`model-cap-pill model-cap-pill--${meta.tone}`}>
                                        <span className="model-cap-pill__icon">{meta.icon}</span>
                                        <span>{meta.label}</span>
                                      </span>
                                    );
                                  })}
                                  {caps.length === 0 ? <span className="model-capability-unknown">能力未确认，不可用于场景路由</span> : null}
                                </div>
                                <div className="provider-model-row__actions">
                                  {caps.length === 0 ? (
                                    <button
                                      type="button"
                                      className="cici-btn cici-btn--text cici-btn--xs"
                                      onClick={() => openCapabilityConfirmation(selected.providerCode, selected.providerName, name)}
                                    >
                                      确认能力
                                    </button>
                                  ) : confirmation?.revocable ? (
                                    <button
                                      type="button"
                                      className="cici-btn cici-btn--text cici-btn--danger cici-btn--xs"
                                      onClick={() => setCapabilityRevokeTarget({ providerCode: selected.providerCode, providerName: selected.providerName, modelName: name })}
                                    >
                                      撤销确认
                                    </button>
                                  ) : null}
                                  <button
                                    type="button"
                                    className="model-row-icon-btn"
                                    title="从平台已选模型移除"
                                    onClick={() => void toggleSelectedModel(name)}
                                  >
                                    -
                                  </button>
                                </div>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </div>
                </>
              ) : (
                <p className="subtle">暂无可用模型厂商。</p>
              )}
            </section>
          </div>
        </div>
      ) : (
        <section className="model-section model-section--routes model-config-tab-panel">
          <div className="model-section__head">
            <h4>场景模型路由</h4>
            <span className="model-count-badge">已配置 {configuredRouteCount} 个</span>
          </div>
          <div className="model-route-board">
            {modelRoutes.map((route) => {
              const candidates = route.candidates ?? [];
              const value = route.providerCode && route.modelName
                ? selectedModelKey(route.providerCode, route.modelName)
                : "";
              return (
                <div className="model-route-row" key={route.sceneCode}>
                  <div className="model-route-row__main">
                    <div className="model-route-row__title">{route.displayName}</div>
                    <div className="model-route-row__desc">{route.description}</div>
                    <div className="model-route-row__guidance">
                      <span>适用能力：{(route.requiredCapabilities ?? []).join("、") || "未声明"}</span>
                      <span>{route.recommendation || "请仅选择厂商已确认能力的模型。"}</span>
                      <span>已验证候选：{candidates.length} 个</span>
                    </div>
                    {route.configured && !route.available ? (
                      <div className="model-route-row__warning">当前配置的模型已失去能力或协议兼容性，请重新选择。</div>
                    ) : null}
                    {candidates.length === 0 ? <div className="model-route-row__warning">{route.unavailableReason || "暂无可选模型。"}</div> : null}
                  </div>
                  <div className="model-route-row__controls">
                    <select
                      className="cici-field__input model-route-select"
                      value={route.available ? value : ""}
                      disabled={busy || candidates.length === 0}
                      aria-label={`${route.displayName}模型`}
                      onChange={(event) => void saveModelRoute(route.sceneCode, event.target.value)}
                    >
                      <option value="">请选择已验证模型</option>
                      {candidates.map((candidate) => {
                        const key = selectedModelKey(candidate.providerCode, candidate.modelName);
                        return (
                          <option
                            key={`${route.sceneCode}-${key}`}
                            value={key}
                          >
                            {candidate.displayLabel || `${candidate.modelName} · ${candidate.providerName}`}
                          </option>
                        );
                      })}
                    </select>
                    {route.configured ? (
                      <button type="button" className="cici-btn cici-btn--ghost" disabled={busy} onClick={() => void clearModelRoute(route.sceneCode)}>
                        清除
                      </button>
                    ) : null}
                  </div>
                </div>
              );
            })}
          </div>
        </section>
      )}

      {allModelsOpen && selected ? (
        <div className="all-models-overlay" onClick={() => setAllModelsOpen(false)}>
          <div className="all-models-modal" onClick={(event) => event.stopPropagation()}>
            <div className="all-models-modal__head">
              <h3>全部模型 · {selected.providerName}</h3>
              <button type="button" className="all-models-close" onClick={() => setAllModelsOpen(false)}>
                x
              </button>
            </div>
            <div className="all-models-modal__toolbar">
              <div className="all-models-modal__toolbar-main">
                <input
                  className="all-models-search"
                  value={allModelsSearch}
                  onChange={(event) => setAllModelsSearch(event.target.value)}
                  placeholder="搜索模型名称"
                />
                <span className="all-models-hint">可直接加入平台目录，能力在后续确认。</span>
              </div>
              <span className="all-models-count">共 {filteredModels.length} / {providerModels.length} 个</span>
            </div>
            <div className="all-models-modal__body">
              {allModelsLoading ? (
                <div className="all-models-empty">正在拉取模型列表...</div>
              ) : filteredModels.length === 0 ? (
                <div className="all-models-empty">{catalogEmptyMessage(allModelsCatalogSource, providerModels.length)}</div>
              ) : (
                <div className="all-models-list">
                  {filteredModels.map((name) => {
                    const selectedNow = selectedModels.has(selectedModelKey(selected.providerCode, name));
                    return (
                      <div key={name} className="all-models-item">
                        <div className="all-models-item__name">{name}</div>
                        <button
                          type="button"
                          className="all-models-item__action"
                          title={selectedNow ? "移出已选模型" : "加入已选模型"}
                          onClick={() => void toggleSelectedModel(name)}
                        >
                          {selectedNow ? "-" : "+"}
                        </button>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </div>
        </div>
      ) : null}

      {capabilityConfirmation ? (
        <div className="cici-modal-overlay" onClick={() => !busy && setCapabilityConfirmation(null)}>
          <section
            className="cici-modal model-capability-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="model-capability-confirm-title"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="cici-modal__head">
              <h2 id="model-capability-confirm-title" className="cici-modal__title">确认模型能力</h2>
              <button type="button" className="cici-modal__close" aria-label="关闭" onClick={() => setCapabilityConfirmation(null)} disabled={busy}>×</button>
            </div>
            <div className="cici-modal__body model-capability-modal__body">
              <p className="cici-modal__description">
                选择此模型实际可用的能力。保存后，模型只会出现在能力与协议匹配的场景路由中。
              </p>
              <dl className="model-capability-context">
                <div><dt>厂商</dt><dd>{capabilityConfirmation.providerName}</dd></div>
                <div><dt>模型</dt><dd>{capabilityConfirmation.modelName}</dd></div>
              </dl>
              <fieldset className="model-capability-fieldset">
                <legend>确认的能力</legend>
                <div className="model-capability-choice-grid">
                  {(Object.keys(CAPABILITY_META) as CapabilityKey[]).map((capability) => {
                    const meta = CAPABILITY_META[capability];
                    const checked = capabilityDraft.includes(capability);
                    return (
                      <label key={capability} className={`model-capability-choice${checked ? " is-selected" : ""}`}>
                        <input type="checkbox" checked={checked} onChange={() => toggleCapabilityDraft(capability)} />
                        <span>{meta.label}</span>
                      </label>
                    );
                  })}
                </div>
              </fieldset>
              {capabilityFormError ? <p className="model-capability-form-error">{capabilityFormError}</p> : null}
            </div>
            <div className="cici-modal__actions">
              <button type="button" className="cici-btn cici-btn--ghost" onClick={() => setCapabilityConfirmation(null)} disabled={busy}>取消</button>
              <button type="button" className="cici-btn cici-btn--primary" onClick={() => void saveCapabilityConfirmation()} disabled={busy}>保存确认</button>
            </div>
          </section>
        </div>
      ) : null}

      {capabilityRevokeTarget ? (
        <div className="cici-modal-overlay" onClick={() => !busy && setCapabilityRevokeTarget(null)}>
          <section
            className="cici-modal cici-modal--danger model-capability-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="model-capability-revoke-title"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="cici-modal__head">
              <h2 id="model-capability-revoke-title" className="cici-modal__title">撤销模型能力确认</h2>
              <button type="button" className="cici-modal__close" aria-label="关闭" onClick={() => setCapabilityRevokeTarget(null)} disabled={busy}>×</button>
            </div>
            <div className="cici-modal__body model-capability-modal__body">
              <p className="cici-modal__description">
                将撤销 <strong>{capabilityRevokeTarget.modelName}</strong> 的人工能力确认。已配置到该模型的场景路由会立即变为不可用，直到重新确认并配置。
              </p>
              <p className="model-capability-revoke-note">本操作会写入平台审计记录。</p>
            </div>
            <div className="cici-modal__actions">
              <button type="button" className="cici-btn cici-btn--ghost" onClick={() => setCapabilityRevokeTarget(null)} disabled={busy}>取消</button>
              <button type="button" className="cici-btn cici-btn--danger" onClick={() => void revokeCapabilityConfirmation()} disabled={busy}>确认撤销</button>
            </div>
          </section>
        </div>
      ) : null}
    </div>
  );
}
