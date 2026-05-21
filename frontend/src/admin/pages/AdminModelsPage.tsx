import { useEffect, useMemo, useState } from "react";
import { useAdminToken } from "../useAdminToken";

type ModelConfig = { sceneCode: string; provider: string; modelName: string };
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
  configuredModels: { sceneCode: string; modelName: string }[];
  recommendedModels: string[];
  selectedModels?: string[];
};

type FetchModelDetailPayload = {
  modelName: string;
  capabilities?: string[];
};

type ProviderModelCatalog = {
  providerCode: string;
  providerName: string;
  models: {
    modelName: string;
    scenes: string[];
    configured: boolean;
  }[];
};

type CapabilityKey = "text" | "tool" | "search" | "reasoning" | "vision";
type ModelUiSetting = {
  displayName: string;
  groupName: string;
  capabilities: CapabilityKey[];
  batchTextOutput: boolean;
  currency: "CNY" | "USD";
  inputPrice: string;
  outputPrice: string;
};

type EditingModelState = {
  providerCode: string;
  providerName: string;
  modelName: string;
};

const PROVIDER_ORDER = ["aliyun-bailian", "deepseek", "ollama-local", "lmstudio-local", "anthropic", "openai"];
const MODEL_UI_STORAGE_KEY = "admin-model-ui-settings-v1";

const PROVIDER_ICON_URLS: Record<string, string> = {
  "aliyun-bailian": "/provider-logos/aliyun-bailian.svg",
  deepseek: "/provider-logos/deepseek.svg",
  "ollama-local": "/provider-logos/ollama.svg",
  "lmstudio-local": "/provider-logos/lmstudio.webp",
  anthropic: "/provider-logos/anthropic.svg",
  openai: "/provider-logos/openai.svg",
};

const DEFAULT_PROVIDER_ICON_URL = "/provider-logos/aliyun-bailian.svg";

const CAPABILITY_META: Record<CapabilityKey, { label: string; icon: string; tone: string }> = {
  text: { label: "文本", icon: "✎", tone: "text" },
  tool: { label: "工具", icon: "🔧", tone: "tool" },
  search: { label: "搜索", icon: "⌕", tone: "search" },
  reasoning: { label: "推理", icon: "☼", tone: "reasoning" },
  vision: { label: "视觉", icon: "◉", tone: "vision" },
};

const providerRank = new Map(PROVIDER_ORDER.map((code, idx) => [code, idx]));

const modelSettingKey = (providerCode: string, modelName: string) => `${providerCode}::${modelName}`;
const selectedModelKey = (providerCode: string, modelName: string) => `${providerCode}::${modelName}`;

const dedupeModels = (names: string[]) => [...new Set(names.filter(Boolean))];
const normalizeCapability = (cap: string): CapabilityKey | null => {
  const v = cap.trim().toLowerCase();
  if (!v) return null;
  if (v.includes("tool") || v.includes("function")) return "tool";
  if (v.includes("search") || v.includes("web") || v.includes("internet")) return "search";
  if (v.includes("reason") || v.includes("thinking") || v.includes("logic")) return "reasoning";
  if (v.includes("vision") || v.includes("image") || v.includes("multimodal") || v.includes("video") || v.includes("audio")) return "vision";
  if (v.includes("text") || v.includes("chat")) return "text";
  return null;
};

const inferCapabilities = (modelName: string): CapabilityKey[] => {
  const lower = modelName.toLowerCase();
  const caps: CapabilityKey[] = ["text"];

  if (lower.includes("reason") || lower.includes("r1") || lower.includes("o1") || lower.includes("thinking")) {
    caps.push("reasoning");
  }
  if (
    lower.includes("vision") ||
    lower.includes("vl") ||
    lower.includes("4o") ||
    lower.includes("omni") ||
    lower.includes("gpt-4.1")
  ) {
    caps.push("vision");
  }
  if (lower.includes("search") || lower.includes("联网") || lower.includes("web")) {
    caps.push("search");
  }

  caps.push("tool");

  return dedupeModels(caps) as CapabilityKey[];
};

const buildDefaultModelSetting = (providerName: string, modelName: string): ModelUiSetting => ({
  displayName: modelName,
  groupName: providerName,
  capabilities: inferCapabilities(modelName),
  batchTextOutput: false,
  currency: "CNY",
  inputPrice: "0.00",
  outputPrice: "0.00",
});

const normalizeModelSetting = (raw: Partial<ModelUiSetting>, providerName: string, modelName: string): ModelUiSetting => {
  const defaults = buildDefaultModelSetting(providerName, modelName);
  const inputCaps = Array.isArray(raw.capabilities) ? raw.capabilities : defaults.capabilities;
  const validCaps = inputCaps.filter((c): c is CapabilityKey => c in CAPABILITY_META);
  return {
    displayName: raw.displayName?.trim() || defaults.displayName,
    groupName: raw.groupName?.trim() || defaults.groupName,
    capabilities: validCaps.length > 0 ? validCaps : defaults.capabilities,
    batchTextOutput: Boolean(raw.batchTextOutput),
    currency: raw.currency === "USD" ? "USD" : "CNY",
    inputPrice: String(raw.inputPrice ?? defaults.inputPrice),
    outputPrice: String(raw.outputPrice ?? defaults.outputPrice),
  };
};

export default function AdminModelsPage() {
  const token = useAdminToken();

  const [notice, setNotice] = useState("");
  const [providers, setProviders] = useState<ProviderConfig[]>([]);
  const [selectedProvider, setSelectedProvider] = useState("aliyun-bailian");

  const [showThinking, setShowThinking] = useState(false);
  const [models, setModels] = useState<ModelConfig[]>([]);

  const [apiBaseUrl, setApiBaseUrl] = useState("");
  const [apiKey, setApiKey] = useState("");
  const [providerEnabled, setProviderEnabled] = useState(true);
  const [showApiKey, setShowApiKey] = useState(false);

  const [sceneCode, setSceneCode] = useState("chat");
  const [routeProvider, setRouteProvider] = useState("aliyun-bailian");
  const [modelName, setModelName] = useState("");
  const [providerModels, setProviderModels] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);

  const [providerCatalogMap, setProviderCatalogMap] = useState<Record<string, ProviderModelCatalog>>({});
  const [collapsedProviders, setCollapsedProviders] = useState<Set<string>>(new Set());
  const [modelSettings, setModelSettings] = useState<Record<string, ModelUiSetting>>({});
  const [showAllModelsModal, setShowAllModelsModal] = useState(false);
  const [allModelsLoading, setAllModelsLoading] = useState(false);
  const [allModelsProviderName, setAllModelsProviderName] = useState("");
  const [allModelsData, setAllModelsData] = useState<{ modelName: string; capabilities: CapabilityKey[] }[]>([]);
  const [allModelsSearch, setAllModelsSearch] = useState("");
  const [selectedModels, setSelectedModels] = useState<Set<string>>(new Set());
  const [capabilityMap, setCapabilityMap] = useState<Record<string, CapabilityKey[]>>({});

  const [editingModel, setEditingModel] = useState<EditingModelState | null>(null);
  const [editingForm, setEditingForm] = useState<ModelUiSetting | null>(null);
  const [showAdvanced, setShowAdvanced] = useState(true);

  const selected = useMemo(
    () => providers.find((p) => p.providerCode === selectedProvider) ?? null,
    [providers, selectedProvider],
  );

  const orderedProviders = useMemo(() => {
    return [...providers].sort((a, b) => (providerRank.get(a.providerCode) ?? 99) - (providerRank.get(b.providerCode) ?? 99));
  }, [providers]);

  const providerCatalogList = useMemo(() => {
    return orderedProviders
      .map((provider) => providerCatalogMap[provider.providerCode])
      .filter((catalog): catalog is ProviderModelCatalog => Boolean(catalog));
  }, [orderedProviders, providerCatalogMap]);

  const totalCatalogModels = useMemo(
    () => providerCatalogList.reduce((sum, provider) => sum + provider.models.length, 0),
    [providerCatalogList],
  );
  const selectedModelsForCurrentProvider = useMemo(() => {
    if (!selected) return [];
    const prefix = `${selected.providerCode}::`;
    const picked = Array.from(selectedModels)
      .filter((k) => k.startsWith(prefix))
      .map((k) => k.slice(prefix.length))
      .filter(Boolean)
      .sort((a, b) => a.localeCompare(b));
    return [...new Set(picked)];
  }, [selected, selectedModels]);
  const routeModelOptions = useMemo(() => {
    if (!routeProvider) return [];
    const prefix = `${routeProvider}::`;
    const picked = Array.from(selectedModels)
      .filter((k) => k.startsWith(prefix))
      .map((k) => k.slice(prefix.length))
      .filter(Boolean)
      .sort((a, b) => a.localeCompare(b));
    return [...new Set(picked)];
  }, [routeProvider, selectedModels]);
  const filteredAllModels = useMemo(() => {
    const kw = allModelsSearch.trim().toLowerCase();
    if (!kw) return allModelsData;
    return allModelsData.filter((item) => item.modelName.toLowerCase().includes(kw));
  }, [allModelsData, allModelsSearch]);

  const authHeaders = useMemo(() => ({ Authorization: `Bearer ${token}` }), [token]);

  const setSelectedModelsForProvider = (providerCode: string, modelNames: string[]) => {
    const nextNames = dedupeModels(modelNames);
    setSelectedModels((prev) => {
      const next = new Set(Array.from(prev).filter((k) => !k.startsWith(`${providerCode}::`)));
      nextNames.forEach((name) => next.add(selectedModelKey(providerCode, name)));
      return next;
    });
  };

  const selectedModelNamesOfProvider = (providerCode: string, setRef?: Set<string>) => {
    const source = setRef ?? selectedModels;
    const prefix = `${providerCode}::`;
    return Array.from(source)
      .filter((k) => k.startsWith(prefix))
      .map((k) => k.slice(prefix.length))
      .filter(Boolean);
  };

  const saveSelectedModels = async (providerCode: string, modelNames: string[]) => {
    const res = await fetch(`/models/providers/${encodeURIComponent(providerCode)}/selected-models`, {
      method: "PUT",
      headers: { "Content-Type": "application/json", ...authHeaders },
      body: JSON.stringify({ selectedModels: dedupeModels(modelNames) }),
    });
    const json = await res.json();
    if (!res.ok || !json.success) {
      throw new Error(json.message ?? `HTTP ${res.status}`);
    }
  };

  const persistModelSettings = (next: Record<string, ModelUiSetting>) => {
    try {
      window.localStorage.setItem(MODEL_UI_STORAGE_KEY, JSON.stringify(next));
    } catch {
      // ignore persistence errors
    }
  };

  const getModelSetting = (providerCode: string, providerName: string, rawModelName: string): ModelUiSetting => {
    const key = modelSettingKey(providerCode, rawModelName);
    const existing = modelSettings[key];
    return existing ? normalizeModelSetting(existing, providerName, rawModelName) : buildDefaultModelSetting(providerName, rawModelName);
  };

  const listModels = async () => {
    const res = await fetch("/models", { headers: authHeaders });
    const json = await res.json();
    if (!res.ok || !json.success) {
      setNotice(json.message ?? "模型场景配置加载失败");
      return;
    }
    setModels((json.data ?? []) as ModelConfig[]);
  };

  const loadThinkingSetting = async () => {
    const res = await fetch("/models/settings/thinking", { headers: authHeaders });
    const json = await res.json();
    if (res.ok && json.success) {
      setShowThinking(Boolean(json.data?.enabled));
    }
  };

  const loadProviders = async () => {
    const res = await fetch("/models/providers", { headers: authHeaders });
    const json = await res.json();
    if (!res.ok || !json.success) {
      setNotice(json.message ?? "模型厂商配置加载失败");
      return;
    }
    const next = (json.data ?? []) as ProviderConfig[];
    setProviders(next);
    if (!next.some((p) => p.providerCode === selectedProvider) && next.length > 0) {
      setSelectedProvider(next[0].providerCode);
    }
    if (!next.some((p) => p.providerCode === routeProvider) && next.length > 0) {
      setRouteProvider(next[0].providerCode);
    }
  };

  const updateProviderCatalogFromPayload = (
    providerCode: string,
    providerName: string,
    configuredModels: { sceneCode: string; modelName: string }[],
    modelNames: string[],
  ) => {
    const configuredMap = new Map<string, string[]>();
    configuredModels.forEach((item) => {
      if (!configuredMap.has(item.modelName)) configuredMap.set(item.modelName, []);
      configuredMap.get(item.modelName)!.push(item.sceneCode);
    });

    const mergedModelNames = dedupeModels(modelNames).sort((a, b) => a.localeCompare(b));

    setProviderCatalogMap((prev) => ({
      ...prev,
      [providerCode]: {
        providerCode,
        providerName,
        models: mergedModelNames.map((name) => ({
          modelName: name,
          scenes: configuredMap.get(name) ?? [],
          configured: configuredMap.has(name),
        })),
      },
    }));
  };

  const loadProviderModels = async (providerCode: string, updateSelectedList = false) => {
    const res = await fetch(`/models/providers/${encodeURIComponent(providerCode)}/models`, { headers: authHeaders });
    const json = await res.json();
    if (!res.ok || !json.success) {
      if (updateSelectedList) setProviderModels([]);
      setNotice(json.message ?? "厂商模型列表加载失败");
      return;
    }

    const data = (json.data ?? {}) as ProviderModelsPayload;
    const merged = dedupeModels([
      ...(data.configuredModels ?? []).map((m) => m.modelName),
      ...(data.recommendedModels ?? []),
    ]);
    const selectedFromServer = dedupeModels(
      (data.selectedModels && data.selectedModels.length > 0)
        ? data.selectedModels
        : (data.configuredModels ?? []).map((m) => m.modelName),
    );
    setSelectedModelsForProvider(providerCode, selectedFromServer);
    setCapabilityMap((prev) => {
      const next = { ...prev };
      merged.forEach((name) => {
        const key = selectedModelKey(providerCode, name);
        if (!next[key] || next[key].length === 0) {
          next[key] = inferCapabilities(name);
        }
      });
      return next;
    });

    updateProviderCatalogFromPayload(providerCode, data.providerName, data.configuredModels ?? [], merged);

    if (updateSelectedList) {
      setProviderModels(merged);
    }
  };

  const loadAllProviderCatalogs = async () => {
    await Promise.all(orderedProviders.map((provider) => loadProviderModels(provider.providerCode, provider.providerCode === selectedProvider)));
  };

  const handleThinkingChange = (enabled: boolean) => {
    setShowThinking(enabled);
    void fetch("/models/settings/thinking", {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeaders },
      body: JSON.stringify({ enabled }),
    });
  };

  const saveProvider = async (enabledOverride?: boolean) => {
    if (!selected) return false;
    const finalEnabled = typeof enabledOverride === "boolean" ? enabledOverride : providerEnabled;
    setBusy(true);
    try {
      const payload: { enabled: boolean; apiBaseUrl?: string; apiKey?: string } = {
        enabled: finalEnabled,
      };
      if (apiBaseUrl.trim()) {
        payload.apiBaseUrl = apiBaseUrl.trim();
      }
      // only update apiKey when user actually types a new one
      if (apiKey.trim()) {
        payload.apiKey = apiKey.trim();
      }
      const res = await fetch(`/models/providers/${encodeURIComponent(selected.providerCode)}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json", ...authHeaders },
        body: JSON.stringify(payload),
      });
      const json = await res.json();
      if (!res.ok || !json.success) {
        setNotice(`保存失败：${json.message ?? "unknown error"}`);
        return false;
      }
      setNotice("厂商配置已保存");
      setApiKey("");
      setProviderEnabled(finalEnabled);
      await loadProviders();
      await loadProviderModels(selected.providerCode, true);
      return true;
    } finally {
      setBusy(false);
    }
  };

  const checkProvider = async () => {
    if (!selected) return;
    setBusy(true);
    try {
      const res = await fetch(`/models/providers/${encodeURIComponent(selected.providerCode)}/check`, {
        method: "POST",
        headers: authHeaders,
      });
      const json = await res.json();
      if (!res.ok || !json.success) {
        setNotice(`检测失败：${json.message ?? "unknown error"}`);
        return;
      }
      const count = Number(json.data?.modelCount ?? 0);
      setNotice(`检测成功，可用模型 ${count} 个`);
    } finally {
      setBusy(false);
    }
  };

  const fetchModels = async () => {
    if (!selected) return;
    setBusy(true);
    setAllModelsLoading(true);
    setShowAllModelsModal(true);
    setAllModelsProviderName(selected.providerName);
    setAllModelsSearch("");
    try {
      if (selected.providerCode === "aliyun-bailian") {
        const shouldSyncApiBase = apiBaseUrl.trim() && apiBaseUrl.trim() !== selected.apiBaseUrl;
        const shouldSyncApiKey = apiKey.trim().length > 0;
        if (shouldSyncApiBase || shouldSyncApiKey) {
          await fetch(`/models/providers/${encodeURIComponent(selected.providerCode)}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json", ...authHeaders },
            body: JSON.stringify({
              enabled: providerEnabled,
              apiBaseUrl: shouldSyncApiBase ? apiBaseUrl.trim() : undefined,
              apiKey: shouldSyncApiKey ? apiKey.trim() : undefined,
            }),
          });
        }
      }

      const res = await fetch(`/models/providers/${encodeURIComponent(selected.providerCode)}/models/fetch`, {
        method: "POST",
        headers: authHeaders,
      });
      const json = await res.json();
      if (!res.ok || !json.success) {
        setNotice(`获取模型列表失败：${json.message ?? "unknown error"}`);
        return;
      }
      const fetched = (json.data?.models ?? []) as string[];
      const rawDetails = (json.data?.modelDetails ?? []) as FetchModelDetailPayload[];
      const detailsMap = new Map<string, CapabilityKey[]>();
      rawDetails.forEach((item) => {
        const caps = (item.capabilities ?? [])
          .map((c) => normalizeCapability(c))
          .filter((c): c is CapabilityKey => c !== null);
        detailsMap.set(item.modelName, caps.length > 0 ? Array.from(new Set(caps)) : inferCapabilities(item.modelName));
      });
      const configured = models
        .filter((m) => m.provider === selected.providerCode)
        .map((m) => ({ sceneCode: m.sceneCode, modelName: m.modelName }));

      const sorted = [...new Set(fetched)].sort((a, b) => a.localeCompare(b));
      setProviderModels(sorted);
      setAllModelsData(
        sorted.map((name) => ({
          modelName: name,
          capabilities: detailsMap.get(name) ?? inferCapabilities(name),
        })),
      );
      setCapabilityMap((prev) => {
        const next = { ...prev };
        sorted.forEach((name) => {
          const key = selectedModelKey(selected.providerCode, name);
          next[key] = detailsMap.get(name) ?? inferCapabilities(name);
        });
        return next;
      });
      updateProviderCatalogFromPayload(selected.providerCode, selected.providerName, configured, sorted);
      setNotice(`已拉取 ${sorted.length} 个模型`);
    } finally {
      setAllModelsLoading(false);
      setBusy(false);
    }
  };

  const saveSceneModel = async () => {
    if (!sceneCode.trim() || !routeProvider || !modelName.trim()) {
      setNotice("请先选择厂商、模型并填写场景码");
      return;
    }
    const res = await fetch("/models", {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeaders },
      body: JSON.stringify({ sceneCode: sceneCode.trim(), provider: routeProvider, modelName: modelName.trim() }),
    });
    const json = await res.json();
    if (!res.ok || !json.success) {
      setNotice(`保存失败：${json.message ?? "unknown error"}`);
      return;
    }
    setNotice("场景模型映射已保存");
    await listModels();
    await loadAllProviderCatalogs();
  };

  const deleteModel = async (scene: string) => {
    const res = await fetch(`/models?sceneCode=${encodeURIComponent(scene)}`, {
      method: "DELETE",
      headers: authHeaders,
    });
    const json = await res.json();
    if (!res.ok || !json.success) {
      setNotice(`删除失败：${json.message ?? "unknown error"}`);
      return;
    }
    setNotice("场景模型映射已删除");
    await listModels();
    await loadAllProviderCatalogs();
  };

  const deleteFirstSceneByModel = async (providerCode: string, targetModelName: string) => {
    const first = models.find((m) => m.provider === providerCode && m.modelName === targetModelName);
    if (!first) {
      setNotice("该模型当前没有场景映射可删除");
      return;
    }
    await deleteModel(first.sceneCode);
  };

  const toggleProviderGroup = (providerCode: string) => {
    setCollapsedProviders((prev) => {
      const next = new Set(prev);
      if (next.has(providerCode)) next.delete(providerCode);
      else next.add(providerCode);
      return next;
    });
  };

  const openModelEditor = (providerCode: string, providerName: string, rawModelName: string) => {
    const setting = getModelSetting(providerCode, providerName, rawModelName);
    setEditingModel({ providerCode, providerName, modelName: rawModelName });
    setEditingForm(setting);
    setShowAdvanced(true);
  };

  const closeModelEditor = () => {
    setEditingModel(null);
    setEditingForm(null);
  };

  const saveModelEditor = () => {
    if (!editingModel || !editingForm) return;
    const key = modelSettingKey(editingModel.providerCode, editingModel.modelName);
    const next = {
      ...modelSettings,
      [key]: normalizeModelSetting(editingForm, editingModel.providerName, editingModel.modelName),
    };
    setModelSettings(next);
    persistModelSettings(next);
    setNotice(`模型设置已保存：${editingModel.modelName}`);
    closeModelEditor();
  };

  const toggleCapability = (cap: CapabilityKey) => {
    setEditingForm((prev) => {
      if (!prev) return prev;
      const has = prev.capabilities.includes(cap);
      const nextCaps = has ? prev.capabilities.filter((c) => c !== cap) : [...prev.capabilities, cap];
      return {
        ...prev,
        capabilities: nextCaps.length > 0 ? nextCaps : ["text"],
      };
    });
  };

  const isSelectedModel = (providerCode: string, name: string) => selectedModels.has(selectedModelKey(providerCode, name));

  const toggleSelectedModel = async (providerCode: string, name: string) => {
    const current = new Set(selectedModelNamesOfProvider(providerCode));
    const wasSelected = current.has(name);
    if (wasSelected) {
      current.delete(name);
    } else {
      current.add(name);
      setRouteProvider(providerCode);
      setModelName(name);
    }
    const nextNames = Array.from(current);
    setSelectedModelsForProvider(providerCode, nextNames);
    try {
      await saveSelectedModels(providerCode, nextNames);
      setNotice(!wasSelected ? `已加入已选模型：${name}` : `已移除已选模型：${name}`);
      await loadProviderModels(providerCode, providerCode === selectedProvider);
    } catch (error) {
      setNotice(`保存已选模型失败：${error instanceof Error ? error.message : String(error)}`);
      await loadProviderModels(providerCode, providerCode === selectedProvider);
    }
  };

  const removeSelectedModel = async (providerCode: string, modelName: string) => {
    const nextNames = selectedModelNamesOfProvider(providerCode).filter((name) => name !== modelName);
    setSelectedModelsForProvider(providerCode, nextNames);
    try {
      await saveSelectedModels(providerCode, nextNames);
      setNotice(`已从已选模型移除：${modelName}`);
      await loadProviderModels(providerCode, providerCode === selectedProvider);
    } catch (error) {
      setNotice(`移除已选模型失败：${error instanceof Error ? error.message : String(error)}`);
      await loadProviderModels(providerCode, providerCode === selectedProvider);
    }
  };

  useEffect(() => {
    void listModels();
    void loadThinkingSetting();
    void loadProviders();

    try {
      const raw = window.localStorage.getItem(MODEL_UI_STORAGE_KEY);
      if (raw) {
        const parsed = JSON.parse(raw) as Record<string, Partial<ModelUiSetting>>;
        const normalized: Record<string, ModelUiSetting> = {};
        Object.entries(parsed).forEach(([k, v]) => {
          const [providerCode, rawModelName] = k.split("::");
          const providerName = providers.find((p) => p.providerCode === providerCode)?.providerName ?? providerCode;
          normalized[k] = normalizeModelSetting(v, providerName, rawModelName || "model");
        });
        setModelSettings(normalized);
      }
    } catch {
      // ignore parse error
    }
  }, [token]);

  useEffect(() => {
    if (!selected) return;
    setApiBaseUrl(selected.apiBaseUrl || selected.defaultBaseUrl);
    setApiKey("");
    setProviderEnabled(Boolean(selected.enabled));
    void loadProviderModels(selected.providerCode, true);
  }, [selected?.providerCode, selected?.enabled]);

  useEffect(() => {
    if (orderedProviders.length === 0) return;
    void loadAllProviderCatalogs();
  }, [orderedProviders.map((p) => p.providerCode).join("|")]);

  useEffect(() => {
    if (!editingModel || !editingForm) return;
    const onEsc = (e: KeyboardEvent) => {
      if (e.key === "Escape") closeModelEditor();
    };
    window.addEventListener("keydown", onEsc);
    return () => window.removeEventListener("keydown", onEsc);
  }, [editingModel, editingForm]);

  useEffect(() => {
    if (!showAllModelsModal) return;
    const onEsc = (e: KeyboardEvent) => {
      if (e.key === "Escape") setShowAllModelsModal(false);
    };
    window.addEventListener("keydown", onEsc);
    return () => window.removeEventListener("keydown", onEsc);
  }, [showAllModelsModal]);

  return (
    <div className="admin-page">
      <div className="thinking-toggle-bar">
        <label className="kb-check">
          <input type="checkbox" checked={showThinking} onChange={(e) => handleThinkingChange(e.target.checked)} />
          <span>显示思考过程</span>
        </label>
      </div>

      {notice && <p className="notice">{notice}</p>}

      <div className="model-center">
        <aside className="model-provider-list">
          <div className="model-provider-list__title">模型厂商</div>
          {orderedProviders.map((p) => (
            <button
              key={p.providerCode}
              type="button"
              className={`model-provider-item${selectedProvider === p.providerCode ? " is-active" : ""}`}
              onClick={() => setSelectedProvider(p.providerCode)}
            >
              <span className="model-provider-item__icon">
                <img
                  src={PROVIDER_ICON_URLS[p.providerCode] || DEFAULT_PROVIDER_ICON_URL}
                  alt={p.providerName}
                  className="model-provider-item__img"
                />
              </span>
              <span className="model-provider-item__name">{p.providerName}</span>
              <span className={`model-provider-item__status ${p.enabled ? "on" : "off"}`}>{p.enabled ? "ON" : "OFF"}</span>
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
                      onChange={(e) => setApiKey(e.target.value)}
                      placeholder={
                        selected.apiKeySet
                          ? `已配置：${selected.apiKeyMasked}`
                          : selected.apiKeyRequired === false
                            ? "本地服务通常无需 API Key"
                            : "请输入 API Key"
                      }
                    />
                    <button type="button" className="cici-btn cici-btn--ghost" onClick={() => setShowApiKey((v) => !v)}>
                      {showApiKey ? "隐藏" : "显示"}
                    </button>
                    <button type="button" className="cici-btn cici-btn--ghost" onClick={() => setApiKey("")}>重置</button>
                  </div>
                </label>

                <label className="cici-field">
                  <span className="cici-field__label">API 地址</span>
                  <input
                    className="cici-field__input"
                    value={apiBaseUrl}
                    onChange={(e) => setApiBaseUrl(e.target.value)}
                    placeholder={selected.defaultBaseUrl}
                  />
                </label>

                <div className="model-actions-row">
                  <label className="kb-check">
                    <input
                      type="checkbox"
                      checked={providerEnabled}
                      onChange={(e) => {
                        const nextEnabled = e.target.checked;
                        setProviderEnabled(nextEnabled);
                        setProviders((prev) =>
                          prev.map((x) => (x.providerCode === selected.providerCode ? { ...x, enabled: nextEnabled } : x)),
                        );
                        void saveProvider(nextEnabled);
                      }}
                    />
                    <span>启用厂商</span>
                  </label>

                  <div className="row">
                    <button type="button" onClick={() => void checkProvider()} disabled={busy}>检测</button>
                    <button type="button" onClick={() => void saveProvider()} disabled={busy}>保存</button>
                    <button type="button" onClick={() => void fetchModels()} disabled={busy}>全部模型</button>
                  </div>
                </div>
              </div>

              <div className="model-section model-section--target">
                <div className="model-section__head">
                  <h4>已选模型</h4>
                  <span className="model-count-badge">已选 {selectedModelsForCurrentProvider.length} 个</span>
                </div>

                {selectedModelsForCurrentProvider.length === 0 ? (
                  <p className="subtle">暂无已选模型，请先在“全部模型”弹窗中点 + 加入。</p>
                ) : (
                  <div className="provider-model-board">
                    {selectedModelsForCurrentProvider.map((name) => {
                      const caps = capabilityMap[selectedModelKey(selected.providerCode, name)] ?? inferCapabilities(name);
                      const scenes = models
                        .filter((m) => m.provider === selected.providerCode && m.modelName === name)
                        .map((m) => m.sceneCode);
                      const setting = getModelSetting(selected.providerCode, selected.providerName, name);
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
                              <div className="provider-model-row__name">{setting.displayName}</div>
                              {scenes.length > 0 && (
                                <div className="provider-model-row__hint">已映射场景：{scenes.join(" / ")}</div>
                              )}
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
                            </div>

                            <div className="provider-model-row__actions">
                              <button
                                type="button"
                                className="model-row-icon-btn"
                                title="编辑模型设置"
                                onClick={() => openModelEditor(selected.providerCode, selected.providerName, name)}
                              >
                                ⚙
                              </button>
                              <button
                                type="button"
                                className="model-row-icon-btn"
                                title="用于场景映射"
                                onClick={() => {
                                  setRouteProvider(selected.providerCode);
                                  setModelName(name);
                                }}
                              >
                                ↗
                              </button>
                              <button
                                type="button"
                                className="model-row-icon-btn"
                                title="从已选模型移除"
                                onClick={() => void removeSelectedModel(selected.providerCode, name)}
                              >
                                −
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
            <p className="subtle">暂无可用厂商配置。</p>
          )}
        </section>
      </div>

      <section className="model-routing-panel" aria-labelledby="model-routing-title">
        <div className="model-routing-panel__head">
          <h3 id="model-routing-title">模型路由</h3>
          <span>按业务场景指定调用模型</span>
        </div>

        <div className="model-scene-row">
          <label>
            场景码
            <input value={sceneCode} onChange={(e) => setSceneCode(e.target.value)} />
          </label>
          <label>
            厂商
            <select
              value={routeProvider}
              onChange={(e) => {
                setRouteProvider(e.target.value);
                setModelName("");
              }}
            >
              {orderedProviders.map((provider) => (
                <option key={provider.providerCode} value={provider.providerCode}>
                  {provider.providerName}
                </option>
              ))}
            </select>
          </label>
          <label>
            模型名
            <select value={modelName} onChange={(e) => setModelName(e.target.value)}>
              <option value="">选择已选模型</option>
              {modelName && !routeModelOptions.includes(modelName) && (
                <option value={modelName}>{modelName}</option>
              )}
              {routeModelOptions.map((name) => (
                <option key={name} value={name}>
                  {name}
                </option>
              ))}
            </select>
          </label>
          <button type="button" onClick={() => void saveSceneModel()}>保存映射</button>
        </div>

        <div className="model-routing-list">
          {models.length === 0 ? (
            <p className="subtle">暂无场景模型映射。</p>
          ) : (
            models.map((m) => {
              const provider = providers.find((p) => p.providerCode === m.provider);
              return (
                <div key={m.sceneCode} className="model-routing-row">
                  <div>
                    <strong>{m.sceneCode}</strong>
                    <div className="subtle">{provider?.providerName ?? m.provider} / {m.modelName}</div>
                  </div>
                  <div className="model-routing-row__actions">
                    <button
                      type="button"
                      onClick={() => {
                        setSceneCode(m.sceneCode);
                        setRouteProvider(m.provider);
                        setModelName(m.modelName);
                      }}
                    >
                      编辑
                    </button>
                    <button type="button" className="is-danger" onClick={() => void deleteModel(m.sceneCode)}>
                      删除
                    </button>
                  </div>
                </div>
              );
            })
          )}
        </div>
      </section>

      {showAllModelsModal && (
        <div className="all-models-overlay" onClick={() => setShowAllModelsModal(false)}>
          <div className="all-models-modal" onClick={(e) => e.stopPropagation()}>
            <div className="all-models-modal__head">
              <h3>全部模型 · {allModelsProviderName}</h3>
              <button type="button" className="all-models-close" onClick={() => setShowAllModelsModal(false)}>✕</button>
            </div>

            <div className="all-models-modal__toolbar">
              <input
                className="all-models-search"
                value={allModelsSearch}
                onChange={(e) => setAllModelsSearch(e.target.value)}
                placeholder="搜索模型名称"
              />
              <span className="all-models-count">共 {filteredAllModels.length} / {allModelsData.length} 个</span>
            </div>

            <div className="all-models-modal__body">
              {allModelsLoading ? (
                <div className="all-models-empty">正在拉取模型列表...</div>
              ) : filteredAllModels.length === 0 ? (
                <div className="all-models-empty">没有匹配的模型</div>
              ) : (
                <div className="all-models-list">
                  {filteredAllModels.map((item) => {
                    const selectedNow = selected ? isSelectedModel(selected.providerCode, item.modelName) : false;
                    return (
                      <div
                        key={item.modelName}
                        className="all-models-item"
                        onClick={() => {
                          if (selected) setRouteProvider(selected.providerCode);
                          setModelName(item.modelName);
                        }}
                        title="点击填充到模型名"
                      >
                        <div className="all-models-item__name">{item.modelName}</div>
                        <div className="all-models-item__caps">
                          {item.capabilities.map((cap) => {
                            const meta = CAPABILITY_META[cap];
                            return (
                              <span key={`${item.modelName}-${cap}`} className={`model-cap-pill model-cap-pill--${meta.tone}`}>
                                <span className="model-cap-pill__icon">{meta.icon}</span>
                                <span>{meta.label}</span>
                              </span>
                            );
                          })}
                        </div>
                        {selected && (
                          <button
                            type="button"
                            className="all-models-item__action"
                            title={selectedNow ? "移出已选模型" : "加入已选模型"}
                            onClick={(e) => {
                              e.stopPropagation();
                              void toggleSelectedModel(selected.providerCode, item.modelName);
                            }}
                          >
                            {selectedNow ? "−" : "+"}
                          </button>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {editingModel && editingForm && (
        <div className="model-edit-overlay" onClick={closeModelEditor}>
          <div className="model-edit-modal" onClick={(e) => e.stopPropagation()}>
            <div className="model-edit-modal__head">
              <h3>编辑模型</h3>
              <button type="button" className="model-edit-close" onClick={closeModelEditor}>✕</button>
            </div>

            <div className="model-edit-modal__body">
              <label className="model-edit-field">
                <span className="model-edit-field__label">模型 ID</span>
                <div className="model-edit-id-row">
                  <input
                    value={`${editingModel.providerName}/${editingModel.modelName}`}
                    disabled
                    className="model-edit-field__input"
                  />
                  <button
                    type="button"
                    className="model-row-icon-btn"
                    onClick={() => navigator.clipboard.writeText(`${editingModel.providerName}/${editingModel.modelName}`)}
                    title="复制模型 ID"
                  >
                    ⧉
                  </button>
                </div>
              </label>

              <label className="model-edit-field">
                <span className="model-edit-field__label">模型名称</span>
                <input
                  className="model-edit-field__input"
                  value={editingForm.displayName}
                  onChange={(e) => setEditingForm((prev) => (prev ? { ...prev, displayName: e.target.value } : prev))}
                />
              </label>

              <label className="model-edit-field">
                <span className="model-edit-field__label">分组名称</span>
                <input
                  className="model-edit-field__input"
                  value={editingForm.groupName}
                  onChange={(e) => setEditingForm((prev) => (prev ? { ...prev, groupName: e.target.value } : prev))}
                />
              </label>

              <div className="model-edit-toolbar">
                <button type="button" className="model-edit-more-btn" onClick={() => setShowAdvanced((v) => !v)}>
                  {showAdvanced ? "收起设置" : "更多设置"}
                </button>
                <button type="button" className="cici-btn cici-btn--primary" onClick={saveModelEditor}>保存</button>
              </div>

              {showAdvanced && (
                <>
                  <div className="model-edit-divider" />

                  <div className="model-edit-block">
                    <div className="model-edit-field__label">模型类型</div>
                    <div className="model-edit-cap-grid">
                      {(Object.keys(CAPABILITY_META) as CapabilityKey[]).map((cap) => {
                        const meta = CAPABILITY_META[cap];
                        const active = editingForm.capabilities.includes(cap);
                        return (
                          <button
                            key={cap}
                            type="button"
                            className={`model-edit-cap-btn ${active ? "is-active" : ""}`}
                            onClick={() => toggleCapability(cap)}
                          >
                            <span className="model-edit-cap-btn__icon">{meta.icon}</span>
                            <span>{meta.label}</span>
                          </button>
                        );
                      })}
                    </div>
                  </div>

                  <div className="model-edit-divider" />

                  <div className="model-edit-switch-row">
                    <span>支持增量文本输出</span>
                    <label className="model-edit-switch">
                      <input
                        type="checkbox"
                        checked={editingForm.batchTextOutput}
                        onChange={(e) => setEditingForm((prev) => (prev ? { ...prev, batchTextOutput: e.target.checked } : prev))}
                      />
                      <span />
                    </label>
                  </div>

                  <div className="model-edit-price-grid">
                    <label className="model-edit-field">
                      <span className="model-edit-field__label">币种</span>
                      <select
                        className="model-edit-field__input"
                        value={editingForm.currency}
                        onChange={(e) =>
                          setEditingForm((prev) =>
                            prev ? { ...prev, currency: e.target.value === "USD" ? "USD" : "CNY" } : prev,
                          )
                        }
                      >
                        <option value="CNY">¥ 人民币</option>
                        <option value="USD">$ 美元</option>
                      </select>
                    </label>

                    <label className="model-edit-field">
                      <span className="model-edit-field__label">输入价格</span>
                      <div className="model-edit-unit-row">
                        <input
                          className="model-edit-field__input"
                          value={editingForm.inputPrice}
                          onChange={(e) => setEditingForm((prev) => (prev ? { ...prev, inputPrice: e.target.value } : prev))}
                        />
                        <span>{editingForm.currency === "USD" ? "$" : "¥"} / 百万 Token</span>
                      </div>
                    </label>

                    <label className="model-edit-field">
                      <span className="model-edit-field__label">输出价格</span>
                      <div className="model-edit-unit-row">
                        <input
                          className="model-edit-field__input"
                          value={editingForm.outputPrice}
                          onChange={(e) => setEditingForm((prev) => (prev ? { ...prev, outputPrice: e.target.value } : prev))}
                        />
                        <span>{editingForm.currency === "USD" ? "$" : "¥"} / 百万 Token</span>
                      </div>
                    </label>
                  </div>
                </>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
