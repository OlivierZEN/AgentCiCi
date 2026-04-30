import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useAsrVoiceInput } from "../../shared/useAsrVoiceInput";
import { useAdminToken } from "../useAdminToken";
import {
  CRM_TEMPLATES,
  EMPTY_FORM,
  type GeneratedSkillSpec,
  joinCsv,
  type Skill,
  type SkillAuthoringCreateResult,
  type SkillAuthoringResult,
  type SkillForm,
  type SkillPreview,
  type SkillTemplate,
  riskBadgeClass,
  riskLabel,
  skillSourceLabel,
  splitCsv,
} from "../skills/skillStudioShared";

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
        <span className="skill-bilingual-field__en">{props.titleEn}</span>
      </span>
      <span className="skill-bilingual-field__hints subtle">
        <span>{props.hintZh}</span>
        <span className="skill-bilingual-field__hint-en">{props.hintEn}</span>
      </span>
      {props.children}
    </label>
  );
}

type BaseModelOption = {
  value: string; // providerCode::modelName
  label: string;
};

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
  const [authoringSessionId, setAuthoringSessionId] = useState<string | null>(null);
  const [clarificationAnswersByQuestion, setClarificationAnswersByQuestion] = useState<Record<string, string>>({});
  const [modelOptions, setModelOptions] = useState<BaseModelOption[]>([]);
  const [selectedModel, setSelectedModel] = useState("");
  const [busy, setBusy] = useState(false);
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
    if (!libraryModalOpen) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setLibraryModalOpen(false);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [libraryModalOpen]);

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
        promptFragment: skill.promptFragment ?? "",
        draftSpecText: skill.draftSpecText ?? "",
        toolWhitelistText: joinCsv(skill.toolWhitelist),
        kbWhitelistText: joinCsv(skill.kbWhitelist),
        handoffRule: skill.handoffRule ?? "",
        outputContract: skill.outputContract ?? "",
        builtin: skill.builtin,
      });
      setPreview(null);
      setAuthoringResult(null);
      setAuthoringSessionId(null);
      setClarificationAnswersByQuestion({});
    } finally {
      setBusy(false);
      setSkillLoaded(true);
    }
  }, [skillId, token]);

  useEffect(() => {
    if (Number.isFinite(skillId) && skillId >= 1) {
      void loadSkill();
    } else {
      setSkillLoaded(true);
      setForm(EMPTY_FORM);
      setPreview(null);
      setAuthoringResult(null);
      setAuthoringSessionId(null);
      setClarificationAnswersByQuestion({});
      setLoadError("");
    }
  }, [skillId, loadSkill]);

  const resetForm = () => {
    setForm(EMPTY_FORM);
    setPreview(null);
    setAuthoringResult(null);
    setAuthoringSessionId(null);
    setClarificationAnswersByQuestion({});
    if (Number.isFinite(skillId) && skillId >= 1) {
      void loadSkill();
    } else {
      nav("/admin/skills/new", { replace: true });
    }
  };

  const saveSkill = async (draft: SkillForm = form) => {
    if (!draft.skillCode.trim() || !draft.name.trim()) {
      flash("skillCode 与 name 必填 · skillCode and name are required");
      return;
    }
    setBusy(true);
    try {
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
        handoffRule: draft.handoffRule.trim(),
        outputContract: draft.outputContract.trim(),
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
        return;
      }
      flash(isUpdate ? "已保存 · Saved" : "已创建 · Created");
      const saved = json.data as Skill;
      await loadExistingCodes();
      if (!isUpdate && saved?.id) {
        nav(`/admin/skills/${saved.id}/edit`, { replace: true });
      }
    } finally {
      setBusy(false);
    }
  };

  const deriveSkill = async () => {
    if (!form.id) return;
    const nextCode = `${form.skillCode}-derived`;
    const nextName = `${form.name}（派生）`;
    setBusy(true);
    try {
      const res = await fetch(`/skills/${form.id}/derive`, {
        method: "POST",
        headers: authHeaders,
        body: JSON.stringify({
          skillCode: nextCode,
          name: nextName,
          description: form.description.trim(),
        }),
      });
      const json = await res.json();
      if (!res.ok || !json.success) {
        flash(`派生失败：${json.message ?? `HTTP ${res.status}`}`);
        return;
      }
      const saved = json.data as Skill;
      await loadExistingCodes();
      flash("已派生租户技能 · Derived");
      nav(`/admin/skills/${saved.id}/edit`, { replace: true });
    } finally {
      setBusy(false);
    }
  };

  const disableSkill = async () => {
    if (!form.id) return;
    if (!window.confirm(`停用技能 ${form.skillCode} ?`)) return;
    setBusy(true);
    try {
      const res = await fetch(`/skills/${form.id}`, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${token}` },
      });
      const json = await res.json();
      if (!res.ok || !json.success) {
        flash(`停用失败：${json.message ?? `HTTP ${res.status}`}`);
        return;
      }
      flash("已停用 · Disabled");
      nav("/admin/skills");
    } finally {
      setBusy(false);
    }
  };

  const previewSkill = async () => {
    if (!form.skillCode.trim() || !form.name.trim()) {
      flash("请先填写 skillCode 与 name · Fill skillCode and name first");
      return;
    }
    setBusy(true);
    try {
      const body = {
        skillCode: form.skillCode.trim(),
        name: form.name.trim(),
        specText: form.draftSpecText.trim(),
        promptFragment: form.promptFragment.trim(),
        toolWhitelist: splitCsv(form.toolWhitelistText),
        kbWhitelist: splitCsv(form.kbWhitelistText),
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
    flash(`已填充 · ${template.title}`);
  };

  const createFromTemplate = async (template: SkillTemplate) => {
    if (existingCodes.has(template.form.skillCode)) {
      flash(`已存在 skillCode，请从列表进入编辑 · Code exists`);
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
      flash("请先登录 · Please sign in");
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
          flash("实时转写完成 · Transcription done");
        } else {
          flash("未识别到有效语音内容 · No speech detected");
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
      form.handoffRule.trim() ||
      form.outputContract.trim(),
  );

  const applyGeneratedSkill = (result: SkillAuthoringResult) => {
      setForm({
        ...EMPTY_FORM,
        skillCode: result.skillSpec.skillCode,
        name: result.skillSpec.name,
      description: result.skillSpec.description ?? "",
      riskLevel: result.skillSpec.riskLevel,
      promptFragment: result.skillSpec.promptFragment ?? "",
      draftSpecText: result.skillSpec.draftSpecText ?? "",
      toolWhitelistText: joinCsv(result.skillSpec.toolWhitelist),
        kbWhitelistText: joinCsv(result.skillSpec.kbWhitelist),
        handoffRule: result.skillSpec.handoffRule ?? "",
        outputContract: result.skillSpec.outputContract ?? "",
        enabled: true,
      });
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

  const generateSkillDraft = async () => {
    if (!authoringPrompt.trim()) {
      flash("请输入需求描述 · Enter requirements");
      return;
    }
    if (hasDraftContent) {
      const confirmed = window.confirm("将覆盖当前草稿 · Overwrite current draft?");
      if (!confirmed) return;
    }
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
      const json = await res.json();
      if (!res.ok || !json.success) {
        flash(`生成失败：${json.message ?? `HTTP ${res.status}`}`);
        return;
      }
      const result = (json.data ?? null) as SkillAuthoringResult | null;
      if (!result) {
        flash("未返回草稿 · No draft returned");
        return;
      }
      applyGeneratedSkill(result);
      flash("草稿已生成 · Draft generated");
    } finally {
      setBusy(false);
    }
  };

  const refineSkillDraft = async () => {
    if (!authoringPrompt.trim() && !hasClarificationAnswers()) {
      flash("填写优化说明或下方追问答案 · Add refine text or answers");
      return;
    }
    if (!form.skillCode.trim() || !form.name.trim()) {
      flash("请先生成草稿 · Generate a draft first");
      return;
    }
    setBusy(true);
    try {
      const preferred = parsePreferredModel();
      const res = await fetch("/skills/authoring/refine", {
        method: "POST",
        headers: authHeaders,
        body: JSON.stringify({
          sessionId: authoringSessionId ?? undefined,
          sourceText: authoringPrompt.trim(),
          currentSkillSpec: buildCurrentSkillSpec(),
          clarificationAnswers: buildClarificationAnswersPayload(),
          preferredModel: preferred.preferredModel,
          preferredProvider: preferred.preferredProvider,
        }),
      });
      const json = await res.json();
      if (!res.ok || !json.success) {
        flash(`优化失败：${json.message ?? `HTTP ${res.status}`}`);
        return;
      }
      const result = (json.data ?? null) as SkillAuthoringResult | null;
      if (!result) {
        flash("未返回草稿 · No draft returned");
        return;
      }
      applyGeneratedSkill(result);
      flash("已优化 · Refined");
    } finally {
      setBusy(false);
    }
  };

  const createFromAuthoring = async () => {
    if (!form.skillCode.trim() || !form.name.trim()) {
      flash("请完善草稿 · Complete draft first");
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
        flash("未返回结果 · No result");
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
        handoffRule: result.skillSpec.handoffRule ?? "",
        outputContract: result.skillSpec.outputContract ?? "",
        sourceType: result.createdSkill.sourceType,
        visibility: result.createdSkill.visibility,
        editPolicy: result.createdSkill.editPolicy,
        bindingPolicy: result.createdSkill.bindingPolicy,
        updatePolicy: result.createdSkill.updatePolicy,
        templateCode: result.createdSkill.templateCode ?? "",
        baseTemplateVersion: result.createdSkill.baseTemplateVersion,
        enabled: true,
      }));
      setPreview(result.preview);
      setAuthoringResult({
        sourceText: result.sourceText,
        skillSpec: result.skillSpec,
        preview: result.preview,
      });
      setAuthoringSessionId(null);
      setClarificationAnswersByQuestion({});
      flash("已创建 · Created");
      nav(`/admin/skills/${result.createdSkill.id}/edit`, { replace: true });
    } finally {
      setBusy(false);
    }
  };

  const pageTitle = form.id ? "编辑技能 · Edit skill" : "新建技能 · New skill";
  const tenantEditable = form.editPolicy === "EDITABLE";
  const tenantConfigurable = form.editPolicy === "CONFIGURABLE";
  const canChangeContent = tenantEditable;
  const canDisable = Boolean(form.id && (form.sourceType === "TENANT_CUSTOM" || form.sourceType === "TENANT_DERIVED"));
  const canDerive = Boolean(form.id && form.sourceType === "PLATFORM_STANDARD");
  const isPlatformManaged = form.sourceType === "PLATFORM_STANDARD";

  if (Number.isFinite(skillId) && skillId >= 1 && loadError) {
    return (
      <div className="admin-page skills-compose">
        <Link to="/admin/skills" className="text-link">
          ← 返回列表 · Back
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
          ← 技能列表 · Skills
        </Link>
        <p className="subtle admin-skills-compose__status">
          加载中… · Loading
        </p>
      </div>
    );
  }

  return (
    <div className="admin-page skills-compose">
      {notice ? <div className="dify-toast">{notice}</div> : null}

      <div className="skills-compose__top">
        <Link to="/admin/skills" className="text-link">
          ← 技能列表 · Skills
        </Link>
        <h1 className="skills-compose__title">{pageTitle}</h1>
        <p className="subtle skills-compose__lede">
          自然语言生成，或通过技能库导入初始化表单；生成后可在下方字段中直接修改 · Use NL authoring or import a library
          template to seed the form, then fine-tune fields below
        </p>
      </div>

      <div className="skills-compose__library-bar">
        <button
          type="button"
          className="secondary"
          onClick={() => setLibraryModalOpen(true)}
          disabled={busy || !tenantEditable}
        >
          从技能库导入 · Import from skill library
        </button>
        {!tenantEditable ? (
          <span className="subtle skills-compose__library-note">平台标准技能当前仅支持启停；请先派生再编辑正文。</span>
        ) : null}
      </div>

      <section className="skills-authoring-strip skills-authoring-strip--compose">
        <div className="skills-authoring-toolbar">
          <h3 className="skills-compose__h3 skills-authoring-toolbar__title">自然语言 · Authoring</h3>
          <div className="skills-authoring-toolbar__model">
            <label className="skills-authoring-toolbar__model-label" htmlFor="skills-authoring-model-select">
              模型
            </label>
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
          </div>
          <div className="skills-authoring-toolbar__actions">
            <button
              type="button"
              className="skills-authoring-btn skills-authoring-btn--primary"
              onClick={() => void generateSkillDraft()}
              disabled={busy}
            >
              AI 生成草稿
            </button>
            <button type="button" className="secondary skills-authoring-btn" onClick={() => void refineSkillDraft()} disabled={busy}>
              继续优化
            </button>
            <button type="button" className="secondary skills-authoring-btn" onClick={() => void createFromAuthoring()} disabled={busy}>
              按草稿创建
            </button>
            <button
              type="button"
              className="secondary skills-authoring-btn"
              disabled={busy}
              onClick={() => {
                setAuthoringPrompt("");
                setAuthoringResult(null);
                setAuthoringSessionId(null);
                setClarificationAnswersByQuestion({});
              }}
            >
              清空描述
            </button>
          </div>
        </div>

        <div className="skills-authoring-grid skills-authoring-grid--compose">
          <div className="skills-authoring-col skills-authoring-col--input">
            <BilingualField
              className="skill-bilingual-field--compact skills-authoring-field--paired"
              titleZh="需求描述"
              titleEn="Requirements"
              hintZh="说明目标、触发场景、输出与风险边界。"
              hintEn="Goals, triggers, outputs, and risk boundaries."
            >
              <div className="skills-authoring-input-wrap">
                <textarea
                  className="skills-authoring-main-input"
                  rows={10}
                  value={authoringPrompt}
                  onChange={(e) => setAuthoringPrompt(e.target.value)}
                  aria-label="需求描述 · Requirements"
                />
                <div className="skills-authoring-input__actions">
                  <button
                    type="button"
                    className={`skills-authoring-mic${asrListening ? " skills-authoring-mic--on" : ""}`}
                    onClick={() => void toggleAuthoringSpeech()}
                    disabled={busy || !speechSupported || !token}
                    title={
                      !token
                        ? "请先登录 · Sign in required"
                        : !speechSupported
                          ? "当前浏览器不支持录音 · Recording unsupported"
                          : asrListening
                            ? "停止并写入 · Stop"
                            : "语音录入（工作台同款实时 ASR）· Voice"
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
            </BilingualField>
            {!speechSupported ? (
              <p className="skills-authoring-mic-hint subtle">
                语音录入与助手工作台一致，走服务端实时 ASR；需浏览器支持录音与 WebSocket · Same ASR as workbench; needs mic + WebSocket
              </p>
            ) : null}
          </div>

          <div className="skills-authoring-col skills-authoring-col--summary">
            <div className="skills-authoring-readonly-head">
              <span className="skills-authoring-readonly-head__zh">摘要</span>
              <span className="skills-authoring-readonly-head__en">Summary</span>
              <span className="skills-authoring-readonly-head__badge">只读 · Read-only</span>
              <span className="skills-authoring-readonly-head__hint subtle">
                由 AI 生成后展示，不可直接编辑 · Filled after generate, not editable here
              </span>
            </div>
            <div className="skills-authoring-readonly-body">
              {authoringResult ? (
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
                      <span>{authoringResult.skillSpec.toolWhitelist.length} tools</span>
                      <span>{authoringResult.skillSpec.kbWhitelist.length} KB</span>
                    </div>
                    {authoringResult.skillSpec.triggerHints.length ? (
                      <>
                        <h5>触发 · Triggers</h5>
                        <div className="skills-authoring-chips">
                          {authoringResult.skillSpec.triggerHints.map((item) => (
                            <span key={item}>{item}</span>
                          ))}
                        </div>
                      </>
                    ) : null}
                    {authoringResult.skillSpec.warnings.length ? (
                      <>
                        <h5>待补充 · Gaps</h5>
                        <ul className="skills-authoring-list">
                          {authoringResult.skillSpec.warnings.map((item) => (
                            <li key={item}>{item}</li>
                          ))}
                        </ul>
                      </>
                    ) : null}
                    {authoringResult.skillSpec.clarificationQuestions.length ? (
                      <>
                        <h5>追问 · Clarifications</h5>
                        <p className="subtle admin-skills-compose__inline-subtle">
                          填写后点「继续优化」合并进草稿。
                          Fill answers, then Refine to merge.
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
                        session: <code>{authoringSessionId}</code>
                      </p>
                    ) : null}
                  </div>
                </div>
              ) : (
                <div className="skills-authoring-readonly-empty">
                  <p className="skills-authoring-readonly-empty__text">生成后展示摘要 · Summary appears after generate</p>
                </div>
              )}
            </div>
          </div>
        </div>
      </section>

      <section className="skills-panel skills-compose__form">
        <div className="skills-template-strip__head">
          <h3 className="skills-compose__h3">技能字段 · Skill fields</h3>
        </div>
        <div className="skills-compose__meta-row">
          <span className="skills-pill">{skillSourceLabel(form.sourceType)}</span>
          <span className="skills-pill">{form.editPolicy}</span>
          <span className="skills-pill">{form.bindingPolicy}</span>
          {form.templateCode ? <span className="skills-pill">{form.templateCode}</span> : null}
          {form.baseTemplateVersion ? <span className="skills-pill">base v{form.baseTemplateVersion}</span> : null}
        </div>
        {isPlatformManaged ? (
          <p className="subtle skills-compose__library-note">
            平台标准技能正文由平台维护；租户侧本阶段只支持启停和派生，不直接改写内容。
          </p>
        ) : null}
        <div className="skills-form-grid">
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
            <input value={form.name} disabled={!canChangeContent} onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))} />
          </BilingualField>
          <BilingualField
            titleZh="风险等级"
            titleEn="riskLevel"
            hintZh="影响编译与执行侧策略，高等级需更严格人工兜底。"
            hintEn="Affects policy; HIGH needs stronger human guardrails."
          >
            <select
              value={form.riskLevel}
              disabled={!canChangeContent}
              onChange={(e) => setForm((prev) => ({ ...prev, riskLevel: e.target.value as SkillForm["riskLevel"] }))}
            >
              <option value="LOW">LOW</option>
              <option value="MEDIUM">MEDIUM</option>
              <option value="HIGH">HIGH</option>
            </select>
          </BilingualField>
          <label className="skills-checkbox-field skills-compose__enabled">
            <span>
              <span className="admin-skills-compose__enabled-title">启用 · enabled</span>
              <span className="subtle admin-skills-compose__enabled-hint">
                关闭后不会被调度 · Off = not scheduled
              </span>
            </span>
            <input
              type="checkbox"
              checked={form.enabled}
              disabled={busy || (!tenantEditable && !tenantConfigurable)}
              onChange={(e) => setForm((prev) => ({ ...prev, enabled: e.target.checked }))}
            />
          </label>
        </div>

        <BilingualField
          titleZh="摘要说明"
          titleEn="description"
          hintZh="一句话说明技能做什么，供列表与选择器展示。"
          hintEn="One-liner for lists and pickers."
        >
          <textarea
            rows={2}
            value={form.description}
            disabled={!canChangeContent}
            onChange={(e) => setForm((prev) => ({ ...prev, description: e.target.value }))}
          />
        </BilingualField>
        <BilingualField
          titleZh="提示片段"
          titleEn="promptFragment"
          hintZh="给模型的执行指令片段，与规格文本共同构成运行时提示。"
          hintEn="Model-facing instruction chunk; combined with spec at runtime."
        >
          <textarea
            rows={4}
            value={form.promptFragment}
            disabled={!canChangeContent}
            onChange={(e) => setForm((prev) => ({ ...prev, promptFragment: e.target.value }))}
          />
        </BilingualField>
        <BilingualField
          titleZh="规格正文"
          titleEn="draftSpecText"
          hintZh="分步骤描述抽取、判断与输出结构；用于预览编译与对齐团队口径。"
          hintEn="Stepwise spec for compile preview and team alignment."
        >
          <textarea
            rows={6}
            value={form.draftSpecText}
            disabled={!canChangeContent}
            onChange={(e) => setForm((prev) => ({ ...prev, draftSpecText: e.target.value }))}
          />
        </BilingualField>
        <div className="skills-form-grid">
          <BilingualField
            titleZh="工具白名单"
            titleEn="toolWhitelist"
            hintZh="逗号分隔的工具名；仅列出的工具可被该技能调用。"
            hintEn="Comma-separated tool names allowed for this skill."
          >
            <input
              value={form.toolWhitelistText}
              disabled={!canChangeContent}
              onChange={(e) => setForm((prev) => ({ ...prev, toolWhitelistText: e.target.value }))}
            />
          </BilingualField>
          <BilingualField
            titleZh="知识库白名单"
            titleEn="kbWhitelist"
            hintZh="逗号分隔的知识库 ID；控制检索范围。"
            hintEn="Comma-separated KB ids to scope retrieval."
          >
            <input
              value={form.kbWhitelistText}
              disabled={!canChangeContent}
              onChange={(e) => setForm((prev) => ({ ...prev, kbWhitelistText: e.target.value }))}
            />
          </BilingualField>
        </div>
        <BilingualField
          titleZh="转人工规则"
          titleEn="handoffRule"
          hintZh="描述何时必须转人工，例如价格、合同或合规场景。"
          hintEn="When to escalate to humans (pricing, legal, etc.)."
        >
          <input
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
          <input
            value={form.outputContract}
            disabled={!canChangeContent}
            onChange={(e) => setForm((prev) => ({ ...prev, outputContract: e.target.value }))}
          />
        </BilingualField>

        <div className="row skills-editor-actions">
          <button type="button" onClick={() => void saveSkill()} disabled={busy}>
            {form.id ? "保存 · Save" : "创建 · Create"}
          </button>
          <button type="button" className="secondary" onClick={() => void previewSkill()} disabled={busy}>
            预览编译 · Preview
          </button>
          <button type="button" className="secondary" onClick={resetForm}>
            重置 · Reset
          </button>
          {canDerive ? (
            <button type="button" className="secondary" onClick={() => void deriveSkill()} disabled={busy}>
              派生 · Derive
            </button>
          ) : null}
          {canDisable ? (
            <button type="button" className="secondary" onClick={() => void disableSkill()} disabled={busy}>
              停用 · Disable
            </button>
          ) : null}
        </div>

        <div className="skills-preview-box">
          <h4>编译预览 · Compile preview</h4>
          {!preview ? <p className="subtle">保存前可预览。</p> : null}
          {preview ? (
            <>
              <p className="subtle">riskLevel: {preview.riskLevel}</p>
              <p className="subtle">tools: {preview.effectiveToolNames.join(", ") || "-"}</p>
              <p className="subtle">kb: {preview.effectiveKnowledgeBaseIds.join(", ") || "-"}</p>
              <h5>summary</h5>
              <pre>{JSON.stringify(preview.compileSummary, null, 2)}</pre>
              <h5>warnings</h5>
              <pre>{JSON.stringify(preview.warnings, null, 2)}</pre>
            </>
          ) : null}
        </div>
      </section>

      {libraryModalOpen ? (
        <div className="dify-modal-overlay" role="presentation" onClick={() => setLibraryModalOpen(false)}>
          <div
            className="dify-modal skills-library-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="skills-library-modal-title"
            onClick={(e) => e.stopPropagation()}
          >
            <h2 id="skills-library-modal-title" className="dify-modal__title">
              技能库 · Skill library
            </h2>
            <div className="dify-modal__body skills-library-modal__body">
              <p className="skills-library-modal__hint">
                选模板填充表单并关闭，或一键落库 · Pick a template to fill the form, or create in one step
              </p>
              <div className="skills-library-modal__grid">
                {CRM_TEMPLATES.map((template) => (
                  <article key={template.key} className="skills-lib-card">
                    <div className="skills-lib-card__top">
                      <div className="skills-lib-card__icon" aria-hidden>
                        {(template.scene.trim().charAt(0) || "?").toUpperCase()}
                      </div>
                      <div className="skills-lib-card__head-text">
                        <span className="skills-lib-card__tag">{template.scene}</span>
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
                        导入初始化 · Import
                      </button>
                      <button
                        type="button"
                        className="skills-lib-card__action-btn skills-lib-card__action-btn--primary"
                        onClick={() => void pickTemplateForCreate(template)}
                        disabled={busy}
                      >
                        一键创建 · Create
                      </button>
                    </div>
                  </article>
                ))}
              </div>
            </div>
            <div className="dify-modal__actions">
              <button type="button" className="dify-btn dify-btn--ghost" onClick={() => setLibraryModalOpen(false)}>
                关闭 · Close
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
