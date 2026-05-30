import { useEffect, useMemo, useState } from "react";
import { LS_PLATFORM_TOKEN, PLATFORM_API_BASE } from "../../constants";

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
};

type FetchModelDetailPayload = {
  modelName: string;
  capabilities?: string[];
};

type CapabilityKey = "text" | "tool" | "search" | "reasoning" | "vision";

const PROVIDER_ORDER = ["aliyun-bailian", "deepseek", "ollama-local", "lmstudio-local", "anthropic", "openai"];
const providerRank = new Map(PROVIDER_ORDER.map((code, idx) => [code, idx]));

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
  text: { label: "文本", icon: "T", tone: "text" },
  tool: { label: "工具", icon: "W", tone: "tool" },
  search: { label: "搜索", icon: "S", tone: "search" },
  reasoning: { label: "推理", icon: "R", tone: "reasoning" },
  vision: { label: "视觉", icon: "V", tone: "vision" },
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
  if (v.includes("search") || v.includes("web") || v.includes("internet")) return "search";
  if (v.includes("reason") || v.includes("thinking") || v.includes("logic")) return "reasoning";
  if (v.includes("vision") || v.includes("image") || v.includes("multimodal") || v.includes("video") || v.includes("audio")) return "vision";
  if (v.includes("text") || v.includes("chat")) return "text";
  return null;
}

function inferCapabilities(modelName: string): CapabilityKey[] {
  const lower = modelName.toLowerCase();
  const caps: CapabilityKey[] = ["text", "tool"];
  if (lower.includes("reason") || lower.includes("r1") || lower.includes("o1") || lower.includes("thinking")) caps.push("reasoning");
  if (lower.includes("vision") || lower.includes("vl") || lower.includes("4o") || lower.includes("omni")) caps.push("vision");
  if (lower.includes("search") || lower.includes("web")) caps.push("search");
  return [...new Set(caps)];
}

function selectedModelKey(providerCode: string, modelName: string) {
  return `${providerCode}::${modelName}`;
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
  const [capabilityMap, setCapabilityMap] = useState<Record<string, CapabilityKey[]>>({});
  const [allModelsOpen, setAllModelsOpen] = useState(false);
  const [allModelsLoading, setAllModelsLoading] = useState(false);
  const [allModelsSearch, setAllModelsSearch] = useState("");
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const authHeaders = useMemo(() => ({ Authorization: `Bearer ${token}` }), [token]);
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
      setCapabilityMap((prev) => {
        const next = { ...prev };
        merged.forEach((name) => {
          const key = selectedModelKey(providerCode, name);
          if (!next[key]) next[key] = inferCapabilities(name);
        });
        return next;
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "加载模型列表失败");
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
        headers: authHeaders,
      });
      const json = await res.json();
      if (!res.ok || !json.success) throw new Error(json.message || "检测失败");
      setNotice(`检测成功，可用模型 ${Number(json.data?.modelCount ?? 0)} 个。`);
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
    try {
      const res = await fetch(`${PLATFORM_API_BASE}/models/providers/${encodeURIComponent(selected.providerCode)}/models/fetch`, {
        method: "POST",
        headers: authHeaders,
      });
      const json = await res.json();
      if (!res.ok || !json.success) throw new Error(json.message || "拉取模型列表失败");
      const models = dedupeModels((json.data?.models ?? []) as string[]).sort((a, b) => a.localeCompare(b));
      const rawDetails = (json.data?.modelDetails ?? []) as FetchModelDetailPayload[];
      const detailsMap = new Map<string, CapabilityKey[]>();
      rawDetails.forEach((item) => {
        const caps = (item.capabilities ?? [])
          .map((cap) => normalizeCapability(cap))
          .filter((cap): cap is CapabilityKey => cap !== null);
        detailsMap.set(item.modelName, caps.length > 0 ? [...new Set(caps)] : inferCapabilities(item.modelName));
      });
      setProviderModels(models);
      setCapabilityMap((prev) => {
        const next = { ...prev };
        models.forEach((name) => {
          next[selectedModelKey(selected.providerCode, name)] = detailsMap.get(name) ?? inferCapabilities(name);
        });
        return next;
      });
      setNotice(`已拉取 ${models.length} 个模型。`);
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

  useEffect(() => {
    void loadProviders();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  useEffect(() => {
    if (!selected) return;
    setApiBaseUrl(selected.apiBaseUrl || selected.defaultBaseUrl);
    setApiKey("");
    setProviderEnabled(Boolean(selected.enabled));
    void loadProviderModels(selected.providerCode);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selected?.providerCode, selected?.enabled]);

  return (
    <div className="admin-page platform-page platform-models-page">
      <header className="skills-catalog__header">
        <div className="platform-page-head__main">
          <h1 className="skills-catalog__title">模型厂商治理</h1>
          <p className="subtle skills-catalog__subtitle">统一控制模型厂商、凭据、可用模型和运行时模型目录。</p>
        </div>
        <div className="platform-page-head__aside">
          <span className="platform-inline-stat">厂商 {providers.length}</span>
          <span className="platform-inline-stat">已启用 {providers.filter((provider) => provider.enabled).length}</span>
          <span className="platform-inline-stat">已选模型 {selectedModelsForCurrentProvider.length}</span>
        </div>
      </header>

      {error ? <div className="platform-console__banner platform-console__banner--error">{error}</div> : null}
      {notice ? <div className="platform-console__banner platform-console__banner--success">{notice}</div> : null}

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
                    <button type="button" onClick={() => void checkProvider()} disabled={busy}>
                      检测
                    </button>
                    <button type="button" onClick={() => void saveProvider()} disabled={busy}>
                      保存
                    </button>
                    <button type="button" onClick={() => void fetchModels()} disabled={busy}>
                      全部模型
                    </button>
                  </div>
                </div>
              </div>

              <div className="model-section model-section--target">
                <div className="model-section__head">
                  <h4>平台已选模型</h4>
                  <span className="model-count-badge">已选 {selectedModelsForCurrentProvider.length} 个</span>
                </div>
                {selectedModelsForCurrentProvider.length === 0 ? (
                  <p className="subtle">暂无已选模型。点击“全部模型”后把允许运行的模型加入目录。</p>
                ) : (
                  <div className="provider-model-board">
                    {selectedModelsForCurrentProvider.map((name) => {
                      const caps = capabilityMap[selectedModelKey(selected.providerCode, name)] ?? inferCapabilities(name);
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
                            </div>
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
              <input
                className="all-models-search"
                value={allModelsSearch}
                onChange={(event) => setAllModelsSearch(event.target.value)}
                placeholder="搜索模型名称"
              />
              <span className="all-models-count">
                共 {filteredModels.length} / {providerModels.length} 个
              </span>
            </div>
            <div className="all-models-modal__body">
              {allModelsLoading ? (
                <div className="all-models-empty">正在拉取模型列表...</div>
              ) : filteredModels.length === 0 ? (
                <div className="all-models-empty">没有匹配的模型</div>
              ) : (
                <div className="all-models-list">
                  {filteredModels.map((name) => {
                    const selectedNow = selectedModels.has(selectedModelKey(selected.providerCode, name));
                    const caps = capabilityMap[selectedModelKey(selected.providerCode, name)] ?? inferCapabilities(name);
                    return (
                      <div key={name} className="all-models-item">
                        <div className="all-models-item__name">{name}</div>
                        <div className="all-models-item__caps">
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
    </div>
  );
}
