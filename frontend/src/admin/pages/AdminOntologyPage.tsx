import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type FormEvent,
  type KeyboardEvent as ReactKeyboardEvent,
  type MouseEvent as ReactMouseEvent,
} from "react";
import {
  AlertTriangle,
  ArrowLeft,
  Bot,
  Boxes,
  Check,
  Clipboard,
  Code2,
  History,
  Link2,
  PackageOpen,
  Plus,
  RefreshCw,
  Save,
  ShieldCheck,
  Sparkles,
  X,
} from "lucide-react";
import { useBlocker } from "react-router-dom";
import { useAdminAuthScope, useAdminNavigationGuard } from "../useAdminToken";
import { shouldBlockAdminRouteNavigation } from "../adminNavigationGuard";
import {
  createOntologyApi,
  isOntologyReferencePackageInstallReconciliationError,
  isOntologyReferencePackageInstallUnconfirmedError,
  isOntologyProposalApplyOutcomeUnknownError,
  isOntologyProposalAppliedReloadError,
  isOntologyPublishOutcomeUnknownError,
  isOntologyRevisionConflict,
  isOntologyWorkspaceCreateReconciliationError,
  isOntologyWorkspaceCreateUnconfirmedError,
  OntologyApiError,
  OntologyReferencePackageInstallUnconfirmedError,
  OntologyWorkspaceCreateUnconfirmedError,
} from "../ontology/ontologyApi";
import OntologyCanvas from "../ontology/OntologyCanvas";
import OntologyInspector from "../ontology/OntologyInspector";
import OntologyMappingPanel from "../ontology/OntologyMappingPanel";
import OntologyProposalPanel from "../ontology/OntologyProposalPanel";
import {
  createOntologyAuthScopeKey,
  createOntologyCompilePreviewBinding,
  createStableOntologyKey,
  findOntologySourceByIdentity,
  findOntologyVersionForDraftRevision,
  findOntologyWorkspaceByReferencePackageIdentity,
  findOntologyWorkspaceByCreateIdentity,
  hasUnvalidatedOntologyMappings,
  isOntologyAsyncScopeCurrent,
  isOntologyCompilePreviewBindingCurrent,
  isOntologyCompilePreviewResponseBound,
  isOntologyOperationContextCurrent,
  nextOntologyTabIndex,
  selectOntologyItem,
  shouldConfirmOntologyDraftDiscard,
  toEditableOntologyMappings,
  type OntologyCompilePreviewBinding,
  type OntologyEditableMapping,
  type OntologySelection,
} from "../ontology/ontologyModel";
import {
  ontologyAggregationLabel,
  presentOntologyAiDiagnostic,
  presentOntologyValidationIssue,
  SUPPORTED_ONTOLOGY_CONNECTORS,
} from "../ontology/ontologyPresentation";
import type {
  OntologyCatalogView,
  OntologyCompilePreview,
  OntologyDataSourceMutationInput,
  OntologyDocument,
  OntologyDraftView,
  OntologyMappingInput,
  OntologyMappingView,
  OntologyProposalRecord,
  OntologyProposalRequest,
  OntologyReferencePackageSummary,
  OntologySourceType,
  OntologyValidationIssue,
  OntologyVersionDetail,
  OntologyVersionSummary,
  OntologyWorkspaceView,
} from "../ontology/ontologyTypes";

type LoadStatus = "idle" | "loading" | "ready" | "error";
type PageMode = "list" | "wizard" | "workspace";
type WizardMode = "domain" | "source";
type WorkspaceTab = "model" | "mapping" | "proposal" | "validation" | "versions" | "technical";
type TechnicalTab = "schema" | "graphql" | "query";

const WORKSPACE_TABS: Array<{ id: WorkspaceTab; label: string }> = [
  { id: "model", label: "业务建模" },
  { id: "mapping", label: "数据映射" },
  { id: "proposal", label: "AI 提案" },
  { id: "validation", label: "校验与发布" },
  { id: "versions", label: "版本历史" },
  { id: "technical", label: "技术预览" },
];
const WIZARD_TABS: Array<{ id: WizardMode; label: string }> = [
  { id: "domain", label: "先描述业务领域" },
  { id: "source", label: "从数据源发现" },
];
const TECHNICAL_TABS: Array<{ id: TechnicalTab; label: string }> = [
  { id: "schema", label: "JSON Schema" },
  { id: "graphql", label: "GraphQL SDL" },
  { id: "query", label: "semantic-query 示例" },
];

const ONTOLOGY_NAVIGATION_GUARD_MESSAGE = "当前本体草稿尚未安全落库。离开将丢失本地改动；如处于冲突状态，请先取消并复制本地草稿。确认离开吗？";

function workspaceStatusLabel(status: OntologyWorkspaceView["status"]): string {
  if (status === "PUBLISHED") return "已发布";
  if (status === "ARCHIVED") return "已归档";
  return "草稿";
}

function formatDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value || "-";
  return new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(date);
}

export function formatOntologyError(error: unknown): string {
  if (isOntologyProposalApplyOutcomeUnknownError(error)) {
    return "提案应用结果未知；请重新加载草稿核对，勿重复应用。";
  }
  if (isOntologyProposalAppliedReloadError(error)) {
    return "提案已应用，重新加载失败；请重新加载草稿，勿重复应用。";
  }
  if (isOntologyPublishOutcomeUnknownError(error)) {
    return "发布结果未知；请重新加载并检查版本历史，勿重复发布。";
  }
  if (isOntologyReferencePackageInstallUnconfirmedError(error)) {
    return "参考包安装结果尚未确认，请先刷新业务本体列表核对，勿重复安装。";
  }
  if (isOntologyWorkspaceCreateUnconfirmedError(error)) {
    return "创建结果尚未确认，请先返回列表并刷新核对，勿重复创建。";
  }
  if (isOntologyRevisionConflict(error)) return "草稿已被更新，请重新加载";
  if (error instanceof OntologyApiError) {
    const message = error.message.trim();
    return /[\u3400-\u9fff]/u.test(message)
      ? message
      : "业务本体服务暂时不可用，请稍后重试。";
  }
  return "业务本体服务暂时不可用，请稍后重试。";
}

function buildDomainInstruction(name: string, purpose: string, objects: string, questions: string): string {
  return [
    `业务领域：${name.trim()}`,
    `用途：${purpose.trim()}`,
    `核心业务对象：${objects.trim()}`,
    `常见业务问题：${questions.trim()}`,
  ].join("\n");
}

function applyRevision(draft: OntologyDraftView, revision: number): OntologyDraftView {
  return {
    ...draft,
    draftRevision: revision,
    workspace: { ...draft.workspace, draftRevision: revision },
  };
}

export default function AdminOntologyPage() {
  const { token, companyId, userId } = useAdminAuthScope();
  const authScopeKey = useMemo(() => createOntologyAuthScopeKey(companyId, token), [companyId, token]);
  const api = useMemo(() => createOntologyApi(token), [token]);
  const listRequestId = useRef(0);
  const packageRequestId = useRef(0);
  const draftRequestId = useRef(0);
  const mappingRequestId = useRef(0);
  const proposalRequestId = useRef(0);
  const versionRequestId = useRef(0);
  const versionDetailRequestId = useRef(0);
  const compileRequestId = useRef(0);
  const workspaceDataGenerationRef = useRef(0);
  const activeWorkspaceIdRef = useRef<number | null>(null);
  const busyRef = useRef<string | null>(null);
  const busyOperationIdRef = useRef(0);
  const contextEpochRef = useRef(0);
  const previousAuthScopeRef = useRef(authScopeKey);
  const draftRef = useRef<OntologyDraftView | null>(null);
  const revisionLockedRef = useRef(false);
  const pendingSaveRef = useRef<OntologyDocument | null>(null);
  const saveLoopRef = useRef<Promise<void> | null>(null);
  const publishTriggerRef = useRef<HTMLButtonElement | null>(null);
  const publishDialogRef = useRef<HTMLElement | null>(null);

  const [pageMode, setPageMode] = useState<PageMode>("list");
  const [workspaces, setWorkspaces] = useState<OntologyWorkspaceView[]>([]);
  const [listStatus, setListStatus] = useState<LoadStatus>("idle");
  const [listError, setListError] = useState("");
  const [packages, setPackages] = useState<OntologyReferencePackageSummary[]>([]);
  const [packageStatus, setPackageStatus] = useState<LoadStatus>("idle");
  const [packageError, setPackageError] = useState("");

  const [selectedWorkspace, setSelectedWorkspace] = useState<OntologyWorkspaceView | null>(null);
  const [draft, setDraft] = useState<OntologyDraftView | null>(null);
  const [draftStatus, setDraftStatus] = useState<LoadStatus>("idle");
  const [draftError, setDraftError] = useState("");
  const [selection, setSelection] = useState<OntologySelection>(null);
  const [activeTab, setActiveTab] = useState<WorkspaceTab>("model");
  const [dirty, setDirty] = useState(false);
  const [busyAction, setBusyAction] = useState<string | null>(null);
  const [operationError, setOperationError] = useState("");
  const [operationNotice, setOperationNotice] = useState("");
  const [revisionLocked, setRevisionLocked] = useState(false);
  const [workspaceCreateLocked, setWorkspaceCreateLocked] = useState(false);
  const [referencePackageInstallLocked, setReferencePackageInstallLocked] = useState(false);

  const [catalog, setCatalog] = useState<OntologyCatalogView | null>(null);
  const [mappings, setMappings] = useState<OntologyMappingView[]>([]);
  const [mappingRows, setMappingRows] = useState<OntologyEditableMapping[]>([]);
  const [mappingDirty, setMappingDirty] = useState(false);
  const [mappingLoaded, setMappingLoaded] = useState(false);
  const [mappingLoading, setMappingLoading] = useState(false);
  const [mappingError, setMappingError] = useState("");

  const [proposals, setProposals] = useState<OntologyProposalRecord[]>([]);
  const [activeProposal, setActiveProposal] = useState<OntologyProposalRecord | null>(null);
  const [proposalLoading, setProposalLoading] = useState(false);
  const [aiError, setAiError] = useState("");

  const [validationIssues, setValidationIssues] = useState<OntologyValidationIssue[]>([]);
  const [validationChecked, setValidationChecked] = useState(false);
  const [versions, setVersions] = useState<OntologyVersionSummary[]>([]);
  const [versionDetail, setVersionDetail] = useState<OntologyVersionDetail | null>(null);
  const [versionsLoading, setVersionsLoading] = useState(false);
  const [compilePreview, setCompilePreview] = useState<OntologyCompilePreview | null>(null);
  const [compilePreviewBinding, setCompilePreviewBinding] = useState<OntologyCompilePreviewBinding | null>(null);
  const [technicalLoading, setTechnicalLoading] = useState(false);
  const [technicalTab, setTechnicalTab] = useState<TechnicalTab>("schema");
  const [copiedLabel, setCopiedLabel] = useState("");
  const [publishConfirmOpen, setPublishConfirmOpen] = useState(false);

  const [wizardMode, setWizardMode] = useState<WizardMode>("domain");
  const [domainName, setDomainName] = useState("");
  const [domainPurpose, setDomainPurpose] = useState("");
  const [domainObjects, setDomainObjects] = useState("");
  const [domainQuestions, setDomainQuestions] = useState("");
  const [wizardSourceName, setWizardSourceName] = useState("");
  const [wizardSourceType, setWizardSourceType] = useState<OntologySourceType>("INLINE_SAMPLE");
  const [wizardAdapterKey, setWizardAdapterKey] = useState<string>(SUPPORTED_ONTOLOGY_CONNECTORS[0].value);
  const [wizardSampleData, setWizardSampleData] = useState('{"projects":[{"name":"语义平台建设","status":"进行中"}]}');

  const navigationGuardActive = previousAuthScopeRef.current === authScopeKey
    && shouldConfirmOntologyDraftDiscard(dirty, revisionLocked, mappingDirty);

  useAdminNavigationGuard(
    navigationGuardActive,
    ONTOLOGY_NAVIGATION_GUARD_MESSAGE,
  );

  const routeBlocker = useBlocker(({ currentLocation, nextLocation }) => (
    shouldBlockAdminRouteNavigation(
      navigationGuardActive,
      currentLocation.pathname,
      nextLocation.pathname,
    )
  ));

  useEffect(() => {
    if (routeBlocker.state !== "blocked") return;
    if (window.confirm(ONTOLOGY_NAVIGATION_GUARD_MESSAGE)) routeBlocker.proceed();
    else routeBlocker.reset();
  }, [routeBlocker]);

  const lockEditingForReload = useCallback(() => {
    revisionLockedRef.current = true;
    pendingSaveRef.current = null;
    setRevisionLocked(true);
  }, []);

  const runBusy = useCallback(async (
    label: string,
    operation: () => Promise<void>,
    onFailure?: (message: string) => void,
  ) => {
    if (busyRef.current) return;
    const context = {
      epoch: contextEpochRef.current,
      operationId: ++busyOperationIdRef.current,
    };
    const isCurrent = () => isOntologyOperationContextCurrent(
      context,
      contextEpochRef.current,
      busyOperationIdRef.current,
    );
    busyRef.current = label;
    setBusyAction(label);
    setOperationError("");
    setOperationNotice("");
    try {
      await operation();
    } catch (error) {
      if (!isCurrent()) return;
      const message = formatOntologyError(error);
      if (
        isOntologyRevisionConflict(error)
        || isOntologyProposalApplyOutcomeUnknownError(error)
        || isOntologyProposalAppliedReloadError(error)
        || isOntologyPublishOutcomeUnknownError(error)
      ) {
        lockEditingForReload();
      }
      if (onFailure) onFailure(message);
      else setOperationError(message);
    } finally {
      if (isCurrent()) {
        busyRef.current = null;
        setBusyAction(null);
      }
    }
  }, [lockEditingForReload]);

  const closePublishConfirm = useCallback(() => {
    setPublishConfirmOpen(false);
    window.setTimeout(() => publishTriggerRef.current?.focus(), 0);
  }, []);

  const openPublishConfirm = (event: ReactMouseEvent<HTMLButtonElement>) => {
    publishTriggerRef.current = event.currentTarget;
    setPublishConfirmOpen(true);
  };

  useEffect(() => {
    if (!publishConfirmOpen) return undefined;
    const dialog = publishDialogRef.current;
    const focusableSelector = "button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex='-1'])";
    const initial = dialog?.querySelector<HTMLElement>("[data-dialog-initial-focus]")
      ?? dialog?.querySelector<HTMLElement>(focusableSelector);
    initial?.focus();

    const handleDialogKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !busyRef.current) {
        event.preventDefault();
        closePublishConfirm();
        return;
      }
      if (event.key !== "Tab" || !dialog) return;
      const focusable = Array.from(dialog.querySelectorAll<HTMLElement>(focusableSelector));
      if (focusable.length === 0) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener("keydown", handleDialogKeyDown);
    return () => document.removeEventListener("keydown", handleDialogKeyDown);
  }, [closePublishConfirm, publishConfirmOpen]);

  const loadWorkspaces = useCallback(async () => {
    const requestId = ++listRequestId.current;
    const epoch = contextEpochRef.current;
    setListStatus("loading");
    setListError("");
    try {
      const next = await api.listWorkspaces();
      if (epoch !== contextEpochRef.current || requestId !== listRequestId.current) return;
      setWorkspaces(next);
      setListStatus("ready");
      setWorkspaceCreateLocked(false);
      setReferencePackageInstallLocked(false);
      setOperationError("");
    } catch (error) {
      if (epoch !== contextEpochRef.current || requestId !== listRequestId.current) return;
      setListError(formatOntologyError(error));
      setListStatus("error");
    }
  }, [api]);

  const loadReferencePackages = useCallback(async () => {
    const requestId = ++packageRequestId.current;
    const epoch = contextEpochRef.current;
    setPackageStatus("loading");
    setPackageError("");
    try {
      const next = await api.listReferencePackages();
      if (epoch !== contextEpochRef.current || requestId !== packageRequestId.current) return;
      setPackages(next);
      setPackageStatus("ready");
    } catch (error) {
      if (epoch !== contextEpochRef.current || requestId !== packageRequestId.current) return;
      setPackageError(formatOntologyError(error));
      setPackageStatus("error");
    }
  }, [api]);

  const invalidateCompilePreview = useCallback(() => {
    compileRequestId.current += 1;
    setCompilePreview(null);
    setCompilePreviewBinding(null);
    setTechnicalLoading(false);
    setCopiedLabel("");
  }, []);

  const invalidateOntologyAsyncContext = useCallback(() => {
    contextEpochRef.current += 1;
    busyOperationIdRef.current += 1;
    listRequestId.current += 1;
    packageRequestId.current += 1;
    draftRequestId.current += 1;
    mappingRequestId.current += 1;
    proposalRequestId.current += 1;
    versionRequestId.current += 1;
    versionDetailRequestId.current += 1;
    compileRequestId.current += 1;
    workspaceDataGenerationRef.current += 1;
    activeWorkspaceIdRef.current = null;
    busyRef.current = null;
    pendingSaveRef.current = null;
    saveLoopRef.current = null;
  }, []);

  const invalidateWorkspaceReads = useCallback(() => {
    workspaceDataGenerationRef.current += 1;
    mappingRequestId.current += 1;
    proposalRequestId.current += 1;
    versionRequestId.current += 1;
    versionDetailRequestId.current += 1;
    setMappingLoading(false);
    setProposalLoading(false);
    setVersionsLoading(false);
    invalidateCompilePreview();
  }, [invalidateCompilePreview]);

  const resetWizardForm = useCallback(() => {
    setWizardMode("domain");
    setDomainName("");
    setDomainPurpose("");
    setDomainObjects("");
    setDomainQuestions("");
    setWizardSourceName("");
    setWizardSourceType("INLINE_SAMPLE");
    setWizardAdapterKey(SUPPORTED_ONTOLOGY_CONNECTORS[0].value);
    setWizardSampleData('{"projects":[{"name":"语义平台建设","status":"进行中"}]}');
  }, []);

  const resetWorkspacePanels = useCallback(() => {
    setCatalog(null);
    setMappings([]);
    setMappingRows([]);
    setMappingDirty(false);
    setMappingLoaded(false);
    setMappingLoading(false);
    setMappingError("");
    setProposals([]);
    setActiveProposal(null);
    setProposalLoading(false);
    setAiError("");
    setValidationIssues([]);
    setValidationChecked(false);
    setVersions([]);
    setVersionDetail(null);
    setVersionsLoading(false);
    invalidateCompilePreview();
    setPublishConfirmOpen(false);
    setOperationError("");
    setOperationNotice("");
    setDirty(false);
    setRevisionLocked(false);
    revisionLockedRef.current = false;
    pendingSaveRef.current = null;
  }, [invalidateCompilePreview]);

  const acceptDraft = useCallback((next: OntologyDraftView) => {
    draftRef.current = next;
    setDraft(next);
    setSelectedWorkspace(next.workspace);
    setWorkspaces((current) => current.map((item) => item.id === next.workspace.id ? next.workspace : item));
    setMappingLoaded(false);
    setDirty(false);
    setRevisionLocked(false);
    revisionLockedRef.current = false;
    invalidateCompilePreview();
  }, [invalidateCompilePreview]);

  const installReferencePackageRecoverably = useCallback(async (
    referencePackage: OntologyReferencePackageSummary,
    isCurrent: () => boolean,
  ): Promise<{ workspace: OntologyWorkspaceView; reconciled: boolean } | null> => {
    try {
      const workspace = await api.installReferencePackage(referencePackage.id);
      return isCurrent() ? { workspace, reconciled: false } : null;
    } catch (error) {
      if (!isCurrent()) return null;
      if (!isOntologyReferencePackageInstallReconciliationError(error)) throw error;
      try {
        const authoritative = await api.listWorkspaces();
        if (!isCurrent()) return null;
        listRequestId.current += 1;
        setWorkspaces(authoritative);
        setListStatus("ready");
        const matched = findOntologyWorkspaceByReferencePackageIdentity(
          authoritative,
          referencePackage,
          userId,
        );
        if (matched) return { workspace: matched, reconciled: true };
      } catch {
        if (!isCurrent()) return null;
      }
      setReferencePackageInstallLocked(true);
      throw new OntologyReferencePackageInstallUnconfirmedError();
    }
  }, [api, userId]);

  const createWorkspaceRecoverably = useCallback(async (
    input: { key: string; name: string; description: string | null },
    isCurrent: () => boolean,
  ): Promise<{ workspace: OntologyWorkspaceView; reconciled: boolean } | null> => {
    try {
      const workspace = await api.createWorkspace(input);
      return isCurrent() ? { workspace, reconciled: false } : null;
    } catch (error) {
      if (!isCurrent()) return null;
      if (!isOntologyWorkspaceCreateReconciliationError(error)) throw error;
      try {
        const authoritative = await api.listWorkspaces();
        if (!isCurrent()) return null;
        listRequestId.current += 1;
        setWorkspaces(authoritative);
        setListStatus("ready");
        const matched = findOntologyWorkspaceByCreateIdentity(authoritative, {
          ...input,
          createdBy: userId,
        });
        if (matched) return { workspace: matched, reconciled: true };
      } catch {
        if (!isCurrent()) return null;
      }
      setWorkspaceCreateLocked(true);
      throw new OntologyWorkspaceCreateUnconfirmedError();
    }
  }, [api, userId]);

  const createDataSourceRecoverably = useCallback(async (
    workspaceId: number,
    expectedRevision: number,
    input: OntologyDataSourceMutationInput,
    isCurrent: () => boolean,
  ): Promise<{ draft: OntologyDraftView; reconciled: boolean } | { draft: null; reconciled: false } | null> => {
    try {
      const next = await api.createDataSource(workspaceId, expectedRevision, input);
      return isCurrent() ? { draft: next, reconciled: false } : null;
    } catch (error) {
      if (!isCurrent()) return null;
      const canReconcile = isOntologyRevisionConflict(error)
        || (error instanceof OntologyApiError && error.outcomeUnknown);
      if (!canReconcile) throw error;
      try {
        const latest = await api.getDraft(workspaceId);
        if (!isCurrent()) return null;
        const matched = findOntologySourceByIdentity(latest.sources, input);
        if (matched) return { draft: latest, reconciled: true };
      } catch {
        if (!isCurrent()) return null;
      }
      if (error instanceof OntologyApiError && error.outcomeUnknown) {
        lockEditingForReload();
        return { draft: null, reconciled: false };
      }
      throw error;
    }
  }, [api, lockEditingForReload]);

  useEffect(() => {
    draftRef.current = draft;
  }, [draft]);

  useLayoutEffect(() => () => {
    invalidateOntologyAsyncContext();
  }, [invalidateOntologyAsyncContext]);

  useLayoutEffect(() => {
    if (previousAuthScopeRef.current === authScopeKey) return;
    previousAuthScopeRef.current = authScopeKey;
    invalidateOntologyAsyncContext();
    setBusyAction(null);
    setWorkspaceCreateLocked(false);
    setReferencePackageInstallLocked(false);
    setPageMode("list");
    setWorkspaces([]);
    setListStatus("idle");
    setListError("");
    setPackages([]);
    setPackageStatus("idle");
    setPackageError("");
    setSelectedWorkspace(null);
    draftRef.current = null;
    setDraft(null);
    setSelection(null);
    setDraftStatus("idle");
    setDraftError("");
    resetWorkspacePanels();
    resetWizardForm();
  }, [authScopeKey, invalidateOntologyAsyncContext, resetWizardForm, resetWorkspacePanels]);

  useEffect(() => {
    if (!shouldConfirmOntologyDraftDiscard(dirty, revisionLocked, mappingDirty)) return undefined;
    const protectLocalDraft = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", protectLocalDraft);
    return () => window.removeEventListener("beforeunload", protectLocalDraft);
  }, [dirty, mappingDirty, revisionLocked]);

  useEffect(() => {
    void loadWorkspaces();
    void loadReferencePackages();
  }, [authScopeKey, loadReferencePackages, loadWorkspaces]);

  const openWorkspace = useCallback(async (workspace: OntologyWorkspaceView) => {
    const requestId = ++draftRequestId.current;
    const epoch = contextEpochRef.current;
    activeWorkspaceIdRef.current = workspace.id;
    setPageMode("workspace");
    setSelectedWorkspace(workspace);
    setDraft(null);
    setDraftStatus("loading");
    setDraftError("");
    setActiveTab("model");
    invalidateWorkspaceReads();
    resetWorkspacePanels();
    try {
      const next = await api.getDraft(workspace.id);
      if (
        epoch !== contextEpochRef.current
        || requestId !== draftRequestId.current
        || activeWorkspaceIdRef.current !== workspace.id
      ) return;
      acceptDraft(next);
      setSelection(next.document.concepts[0] ? { kind: "concept", key: next.document.concepts[0].key } : null);
      setDraftStatus("ready");
    } catch (error) {
      if (epoch !== contextEpochRef.current || requestId !== draftRequestId.current) return;
      setDraftError(formatOntologyError(error));
      setDraftStatus("error");
    }
  }, [acceptDraft, api, invalidateWorkspaceReads, resetWorkspacePanels]);

  const closeWorkspace = () => {
    if (shouldConfirmOntologyDraftDiscard(dirty, revisionLocked, mappingDirty) && !window.confirm(
      "当前草稿包含未保存或冲突中的本地修改。离开将丢弃这些修改；可先复制本地草稿。仍要继续吗？",
    )) return;
    draftRequestId.current += 1;
    invalidateWorkspaceReads();
    activeWorkspaceIdRef.current = null;
    setSelectedWorkspace(null);
    draftRef.current = null;
    setDraft(null);
    setSelection(null);
    setDraftStatus("idle");
    setPageMode("list");
    resetWorkspacePanels();
    void loadWorkspaces();
  };

  const reloadCurrentDraft = async () => {
    if (!selectedWorkspace) return;
    if (shouldConfirmOntologyDraftDiscard(dirty, revisionLocked, mappingDirty) && !window.confirm(
      "重新加载会丢弃本地未保存修改。建议先复制本地草稿。仍要重新加载吗？",
    )) return;
    await openWorkspace(selectedWorkspace);
  };

  const loadMappings = useCallback(async (allowDirtyOverwrite = false) => {
    if (mappingDirty && !allowDirtyOverwrite) {
      setMappingError("请先保存数据映射；当前未保存内容不会被后台刷新覆盖。");
      return;
    }
    const workspaceId = activeWorkspaceIdRef.current;
    if (!workspaceId) return;
    const requestId = ++mappingRequestId.current;
    const scope = {
      epoch: contextEpochRef.current,
      workspaceId,
      generation: workspaceDataGenerationRef.current,
    };
    setMappingLoading(true);
    setMappingError("");
    try {
      const [nextCatalog, nextMappings] = await Promise.all([
        api.getCatalog(workspaceId),
        api.listMappings(workspaceId),
      ]);
      if (
        requestId !== mappingRequestId.current
        || !isOntologyAsyncScopeCurrent(scope, contextEpochRef.current, activeWorkspaceIdRef.current, workspaceDataGenerationRef.current)
      ) return;
      setCatalog(nextCatalog);
      setMappings(nextMappings);
      setMappingRows(toEditableOntologyMappings(nextMappings));
      setMappingDirty(false);
      setMappingLoaded(true);
      invalidateCompilePreview();
    } catch (error) {
      if (
        requestId !== mappingRequestId.current
        || !isOntologyAsyncScopeCurrent(scope, contextEpochRef.current, activeWorkspaceIdRef.current, workspaceDataGenerationRef.current)
      ) return;
      setMappingError(formatOntologyError(error));
    } finally {
      if (
        requestId === mappingRequestId.current
        && isOntologyAsyncScopeCurrent(scope, contextEpochRef.current, activeWorkspaceIdRef.current, workspaceDataGenerationRef.current)
      ) setMappingLoading(false);
    }
  }, [api, invalidateCompilePreview, mappingDirty]);

  const reloadMappings = useCallback(async () => {
    if (mappingDirty && !window.confirm("重新载入会丢弃尚未保存的数据映射。仍要继续吗？")) return;
    await loadMappings(true);
  }, [loadMappings, mappingDirty]);

  const loadProposals = useCallback(async () => {
    const workspaceId = activeWorkspaceIdRef.current;
    if (!workspaceId) return;
    const requestId = ++proposalRequestId.current;
    const scope = {
      epoch: contextEpochRef.current,
      workspaceId,
      generation: workspaceDataGenerationRef.current,
    };
    setProposalLoading(true);
    try {
      const next = await api.listProposals(workspaceId);
      if (
        requestId !== proposalRequestId.current
        || !isOntologyAsyncScopeCurrent(scope, contextEpochRef.current, activeWorkspaceIdRef.current, workspaceDataGenerationRef.current)
      ) return;
      setProposals(next);
      setActiveProposal((current) => next.find((proposal) => proposal.id === current?.id) ?? next[0] ?? null);
    } catch (error) {
      if (
        requestId === proposalRequestId.current
        && isOntologyAsyncScopeCurrent(scope, contextEpochRef.current, activeWorkspaceIdRef.current, workspaceDataGenerationRef.current)
      ) setAiError(formatOntologyError(error));
    } finally {
      if (
        requestId === proposalRequestId.current
        && isOntologyAsyncScopeCurrent(scope, contextEpochRef.current, activeWorkspaceIdRef.current, workspaceDataGenerationRef.current)
      ) setProposalLoading(false);
    }
  }, [api]);

  const loadVersions = useCallback(async () => {
    const workspaceId = activeWorkspaceIdRef.current;
    if (!workspaceId) return;
    const requestId = ++versionRequestId.current;
    const detailRequestId = ++versionDetailRequestId.current;
    const scope = {
      epoch: contextEpochRef.current,
      workspaceId,
      generation: workspaceDataGenerationRef.current,
    };
    setVersionsLoading(true);
    try {
      const next = await api.listVersions(workspaceId);
      if (
        requestId !== versionRequestId.current
        || !isOntologyAsyncScopeCurrent(scope, contextEpochRef.current, activeWorkspaceIdRef.current, workspaceDataGenerationRef.current)
      ) return;
      setVersions(next);
      if (detailRequestId !== versionDetailRequestId.current) return;
      if (next[0]) {
        const detail = await api.getVersion(workspaceId, next[0].version);
        if (
          requestId === versionRequestId.current
          && detailRequestId === versionDetailRequestId.current
          && isOntologyAsyncScopeCurrent(scope, contextEpochRef.current, activeWorkspaceIdRef.current, workspaceDataGenerationRef.current)
        ) setVersionDetail(detail);
      } else {
        setVersionDetail(null);
      }
    } catch (error) {
      if (
        requestId === versionRequestId.current
        && detailRequestId === versionDetailRequestId.current
        && isOntologyAsyncScopeCurrent(scope, contextEpochRef.current, activeWorkspaceIdRef.current, workspaceDataGenerationRef.current)
      ) setOperationError(formatOntologyError(error));
    } finally {
      if (
        requestId === versionRequestId.current
        && detailRequestId === versionDetailRequestId.current
        && isOntologyAsyncScopeCurrent(scope, contextEpochRef.current, activeWorkspaceIdRef.current, workspaceDataGenerationRef.current)
      ) setVersionsLoading(false);
    }
  }, [api]);

  const loadVersionDetail = useCallback(async (version: number) => {
    const workspaceId = activeWorkspaceIdRef.current;
    if (!workspaceId) return;
    const requestId = ++versionDetailRequestId.current;
    const scope = {
      epoch: contextEpochRef.current,
      workspaceId,
      generation: workspaceDataGenerationRef.current,
    };
    setVersionsLoading(true);
    try {
      const detail = await api.getVersion(workspaceId, version);
      if (
        requestId !== versionDetailRequestId.current
        || !isOntologyAsyncScopeCurrent(scope, contextEpochRef.current, activeWorkspaceIdRef.current, workspaceDataGenerationRef.current)
      ) return;
      setVersionDetail(detail);
    } catch (error) {
      if (
        requestId === versionDetailRequestId.current
        && isOntologyAsyncScopeCurrent(scope, contextEpochRef.current, activeWorkspaceIdRef.current, workspaceDataGenerationRef.current)
      ) setOperationError(formatOntologyError(error));
    } finally {
      if (
        requestId === versionDetailRequestId.current
        && isOntologyAsyncScopeCurrent(scope, contextEpochRef.current, activeWorkspaceIdRef.current, workspaceDataGenerationRef.current)
      ) setVersionsLoading(false);
    }
  }, [api]);

  const loadCompilePreview = useCallback(async () => {
    const workspaceId = activeWorkspaceIdRef.current;
    const current = draftRef.current;
    if (!workspaceId || !current || revisionLockedRef.current) return;
    if (dirty || mappingDirty) {
      invalidateCompilePreview();
      setOperationError("请先保存业务定义和数据映射，再生成技术预览。");
      return;
    }
    const requestId = ++compileRequestId.current;
    const scope = {
      epoch: contextEpochRef.current,
      workspaceId,
      generation: workspaceDataGenerationRef.current,
    };
    mappingRequestId.current += 1;
    setMappingLoading(false);
    setTechnicalLoading(true);
    try {
      const authoritativeMappings = await api.listMappings(workspaceId);
      if (
        requestId !== compileRequestId.current
        || !isOntologyAsyncScopeCurrent(scope, contextEpochRef.current, activeWorkspaceIdRef.current, workspaceDataGenerationRef.current)
      ) return;
      const latestDraft = draftRef.current;
      if (!latestDraft || latestDraft.draftRevision !== current.draftRevision || latestDraft.publishedVersion !== current.publishedVersion) return;
      const binding = createOntologyCompilePreviewBinding(
        latestDraft.draftRevision,
        authoritativeMappings,
        latestDraft.publishedVersion,
      );
      setMappings(authoritativeMappings);
      setMappingRows(toEditableOntologyMappings(authoritativeMappings));
      setMappingDirty(false);
      const next = await api.compilePreview(workspaceId, binding.draftRevision);
      if (
        requestId !== compileRequestId.current
        || !isOntologyAsyncScopeCurrent(scope, contextEpochRef.current, activeWorkspaceIdRef.current, workspaceDataGenerationRef.current)
        || draftRef.current?.draftRevision !== binding.draftRevision
        || draftRef.current?.publishedVersion !== binding.publishedVersion
      ) return;
      if (!isOntologyCompilePreviewResponseBound(
        binding,
        next.sourceDraftRevision,
        next.version,
      )) {
        lockEditingForReload();
        setOperationError("技术预览对应的草稿或线上版本已变化，请重新加载工作区。");
        return;
      }
      setCompilePreview(next);
      setCompilePreviewBinding(binding);
    } catch (error) {
      if (
        requestId === compileRequestId.current
        && isOntologyAsyncScopeCurrent(scope, contextEpochRef.current, activeWorkspaceIdRef.current, workspaceDataGenerationRef.current)
      ) {
        if (isOntologyRevisionConflict(error)) lockEditingForReload();
        setOperationError(formatOntologyError(error));
      }
    } finally {
      if (
        requestId === compileRequestId.current
        && isOntologyAsyncScopeCurrent(scope, contextEpochRef.current, activeWorkspaceIdRef.current, workspaceDataGenerationRef.current)
      ) setTechnicalLoading(false);
    }
  }, [api, dirty, invalidateCompilePreview, lockEditingForReload, mappingDirty]);

  const switchWorkspaceTab = (tab: WorkspaceTab) => {
    setActiveTab(tab);
    setOperationError("");
    setOperationNotice("");
    if (tab === "mapping" && !mappingLoaded && !mappingDirty) void loadMappings();
    if (tab === "proposal") {
      void loadProposals();
      if (!catalog && !mappingDirty) void loadMappings();
    }
    if (tab === "versions") void loadVersions();
    if (tab === "technical") {
      const current = draftRef.current;
      const previewIsCurrent = Boolean(current && isOntologyCompilePreviewBindingCurrent(
        compilePreviewBinding,
        current.draftRevision,
        mappings,
        dirty,
        mappingDirty,
        current.publishedVersion,
      ));
      if (!previewIsCurrent) void loadCompilePreview();
    }
  };

  const handleWorkspaceTabKeyDown = (
    event: ReactKeyboardEvent<HTMLButtonElement>,
    index: number,
  ) => {
    const nextIndex = nextOntologyTabIndex(index, WORKSPACE_TABS.length, event.key);
    if (nextIndex === null) return;
    event.preventDefault();
    const nextTab = WORKSPACE_TABS[nextIndex];
    const tabs = event.currentTarget.parentElement?.querySelectorAll<HTMLButtonElement>("[role='tab']");
    tabs?.[nextIndex]?.focus();
    switchWorkspaceTab(nextTab.id);
  };

  const handleWizardTabKeyDown = (
    event: ReactKeyboardEvent<HTMLButtonElement>,
    index: number,
  ) => {
    const nextIndex = nextOntologyTabIndex(index, WIZARD_TABS.length, event.key);
    if (nextIndex === null) return;
    event.preventDefault();
    const tabs = event.currentTarget.parentElement?.querySelectorAll<HTMLButtonElement>("[role='tab']");
    tabs?.[nextIndex]?.focus();
    setWizardMode(WIZARD_TABS[nextIndex].id);
  };

  const handleTechnicalTabKeyDown = (
    event: ReactKeyboardEvent<HTMLButtonElement>,
    index: number,
  ) => {
    const nextIndex = nextOntologyTabIndex(index, TECHNICAL_TABS.length, event.key);
    if (nextIndex === null) return;
    event.preventDefault();
    const tabs = event.currentTarget.parentElement?.querySelectorAll<HTMLButtonElement>("[role='tab']");
    tabs?.[nextIndex]?.focus();
    setTechnicalTab(TECHNICAL_TABS[nextIndex].id);
  };

  const handleMappingRowsChange = (
    rows: OntologyEditableMapping[],
    nextDirty: boolean,
  ) => {
    mappingRequestId.current += 1;
    setMappingLoading(false);
    setMappingRows(rows);
    setMappingDirty(nextDirty);
    setValidationChecked(false);
    setOperationNotice("");
    invalidateCompilePreview();
  };

  const changeDocument = (next: OntologyDocument) => {
    const current = draftRef.current;
    if (!current || revisionLockedRef.current || busyRef.current) return;
    const nextDraft = { ...current, document: next };
    draftRef.current = nextDraft;
    setDraft(nextDraft);
    setDirty(true);
    setValidationChecked(false);
    setOperationNotice("");
    invalidateCompilePreview();
  };

  const saveDocument = async (next: OntologyDocument) => {
    if (revisionLockedRef.current) return;
    pendingSaveRef.current = next;
    if (saveLoopRef.current) return saveLoopRef.current;
    if (busyRef.current) return;

    const epoch = contextEpochRef.current;
    invalidateWorkspaceReads();
    busyRef.current = "保存草稿";
    setBusyAction("保存草稿");
    setOperationError("");
    setOperationNotice("");

    const operation = (async () => {
      try {
        while (pendingSaveRef.current && !revisionLockedRef.current) {
          const candidate = pendingSaveRef.current;
          pendingSaveRef.current = null;
          const current = draftRef.current;
          const workspaceId = activeWorkspaceIdRef.current;
          if (!current || !workspaceId || contextEpochRef.current !== epoch) return;
          const saved = await api.saveDraft(workspaceId, current.draftRevision, candidate);
          if (contextEpochRef.current !== epoch || activeWorkspaceIdRef.current !== workspaceId) return;
          invalidateWorkspaceReads();
          acceptDraft(saved);
          setOperationNotice("草稿已保存");
        }
      } catch (error) {
        if (contextEpochRef.current !== epoch) return;
        if (isOntologyRevisionConflict(error)) lockEditingForReload();
        setOperationError(formatOntologyError(error));
      } finally {
        if (contextEpochRef.current === epoch && busyRef.current === "保存草稿") {
          busyRef.current = null;
          setBusyAction(null);
        }
      }
    })();
    const tracked = operation.finally(() => {
      if (saveLoopRef.current === tracked) saveLoopRef.current = null;
    });
    saveLoopRef.current = tracked;
    return tracked;
  };

  const addConcept = () => {
    if (!draft) return;
    const concepts = draft.document.concepts;
    const key = createStableOntologyKey("concept", concepts.map((concept) => concept.key));
    const index = concepts.length;
    const next: OntologyDocument = {
      ...draft.document,
      concepts: [...concepts, {
        key,
        name: `新业务对象 ${index + 1}`,
        pluralName: null,
        description: null,
        conceptType: "ENTITY",
        displayPropertyKey: "name",
        positionX: 48 + (index % 3) * 240,
        positionY: 56 + Math.floor(index / 3) * 152,
        queryable: true,
        enabled: true,
        properties: [{
          key: "name",
          name: "名称",
          description: null,
          dataType: "TEXT",
          required: true,
          multiple: false,
          sensitive: false,
          queryable: true,
          enumValues: [],
        }],
      }],
    };
    changeDocument(next);
    setSelection(selectOntologyItem(selection, { kind: "concept", key }));
  };

  const addRelation = () => {
    if (!draft) return;
    if (draft.document.concepts.length < 2) {
      setOperationError("至少需要两个业务对象才能添加关系。");
      return;
    }
    const key = createStableOntologyKey("relation", draft.document.relations.map((relation) => relation.key));
    const [source, target] = draft.document.concepts;
    const next: OntologyDocument = {
      ...draft.document,
      relations: [...draft.document.relations, {
        key,
        name: "新业务关系",
        description: null,
        sourceConceptKey: source.key,
        targetConceptKey: target.key,
        cardinality: "ONE_TO_MANY",
        forwardLabel: "关联",
        reverseLabel: "属于",
        queryable: true,
        enabled: true,
      }],
    };
    changeDocument(next);
    setSelection(selectOntologyItem(selection, { kind: "relation", key }));
  };

  const addMetric = () => {
    if (!draft?.document.concepts[0]) {
      setOperationError("先添加业务对象，再定义业务指标。");
      return;
    }
    const key = createStableOntologyKey("metric", draft.document.metrics.map((metric) => metric.key));
    const next: OntologyDocument = {
      ...draft.document,
      metrics: [...draft.document.metrics, {
        key,
        name: "新业务指标",
        conceptKey: draft.document.concepts[0].key,
        aggregation: "COUNT",
        measurePropertyKey: null,
        groupByPropertyKeys: [],
        timePropertyKey: null,
        filters: [],
      }],
    };
    changeDocument(next);
    setSelection(selectOntologyItem(selection, { kind: "metric", key }));
  };

  const addAction = () => {
    if (!draft?.document.concepts[0]) {
      setOperationError("先添加业务对象，再定义可执行动作。");
      return;
    }
    const key = createStableOntologyKey("action", draft.document.actions.map((action) => action.key));
    const next: OntologyDocument = {
      ...draft.document,
      actions: [...draft.document.actions, {
        key,
        name: "新业务动作",
        conceptKey: draft.document.concepts[0].key,
        description: "V1 仅生成动作契约，不执行外部写入。",
        parameters: [],
      }],
    };
    changeDocument(next);
    setSelection(selectOntologyItem(selection, { kind: "action", key }));
  };

  const deleteSelection = (target: NonNullable<OntologySelection>) => {
    if (!draft) return;
    const document = draft.document;
    let next = document;
    if (target.kind === "concept") {
      next = {
        ...document,
        concepts: document.concepts.filter((concept) => concept.key !== target.key),
        relations: document.relations.filter((relation) => relation.sourceConceptKey !== target.key && relation.targetConceptKey !== target.key),
        metrics: document.metrics.filter((metric) => metric.conceptKey !== target.key),
        actions: document.actions.filter((action) => action.conceptKey !== target.key),
        mappings: document.mappings.filter((mapping) => mapping.targetKey !== target.key && !mapping.targetKey.startsWith(`${target.key}.`)),
      };
    }
    if (target.kind === "relation") next = {
      ...document,
      relations: document.relations.filter((relation) => relation.key !== target.key),
      mappings: document.mappings.filter((mapping) => mapping.targetKey !== target.key),
    };
    if (target.kind === "metric") next = {
      ...document,
      metrics: document.metrics.filter((metric) => metric.key !== target.key),
      mappings: document.mappings.filter((mapping) => mapping.targetKey !== target.key),
    };
    if (target.kind === "action") next = {
      ...document,
      actions: document.actions.filter((action) => action.key !== target.key),
      mappings: document.mappings.filter((mapping) => mapping.targetKey !== target.key),
    };
    changeDocument(next);
    setSelection(null);
  };

  const runValidation = async () => {
    const workspaceId = activeWorkspaceIdRef.current;
    const epoch = contextEpochRef.current;
    if (!workspaceId || revisionLockedRef.current || dirty) return;
    if (mappingDirty) {
      setOperationError("请先保存数据映射，再运行发布前校验。");
      setActiveTab("mapping");
      return;
    }
    await runBusy("校验草稿", async () => {
      invalidateWorkspaceReads();
      const generation = workspaceDataGenerationRef.current;
      const [issues, latestMappings] = await Promise.all([
        api.validateDraft(workspaceId),
        api.listMappings(workspaceId),
      ]);
      if (
        contextEpochRef.current !== epoch
        || activeWorkspaceIdRef.current !== workspaceId
        || workspaceDataGenerationRef.current !== generation
      ) return;
      invalidateWorkspaceReads();
      setValidationIssues(issues);
      setMappings(latestMappings);
      setMappingRows(toEditableOntologyMappings(latestMappings));
      setMappingDirty(false);
      setMappingLoaded(true);
      setValidationChecked(true);
      setActiveTab("validation");
      const errorCount = issues.filter((issue) => issue.severity === "ERROR").length;
      setOperationNotice(errorCount === 0 ? "校验完成，可以进入人工发布确认。" : `校验发现 ${errorCount} 个错误。`);
    });
  };

  const createSource = async (input: OntologyDataSourceMutationInput) => {
    const current = draftRef.current;
    const workspaceId = activeWorkspaceIdRef.current;
    const epoch = contextEpochRef.current;
    if (!current || !workspaceId || revisionLockedRef.current || dirty) return;
    if (mappingDirty) {
      setMappingError("请先保存数据映射，再新增数据来源。");
      return;
    }
    await runBusy("创建数据来源", async () => {
      invalidateWorkspaceReads();
      const result = await createDataSourceRecoverably(
        workspaceId,
        current.draftRevision,
        input,
        () => contextEpochRef.current === epoch && activeWorkspaceIdRef.current === workspaceId,
      );
      if (contextEpochRef.current !== epoch || activeWorkspaceIdRef.current !== workspaceId) return;
      if (!result) return;
      if (!result.draft) {
        setMappingError("数据来源创建结果未知，请重新加载核对，勿重复创建。");
        return;
      }
      invalidateWorkspaceReads();
      acceptDraft(result.draft);
      setValidationChecked(false);
      setOperationNotice(result.reconciled
        ? "已核对到之前创建的数据来源，正在刷新可映射的数据目录。"
        : "数据来源已创建，正在刷新可映射的数据目录。");
      await loadMappings();
    }, setMappingError);
  };

  const discoverObjects = async (sourceId: number) => {
    const current = draftRef.current;
    const workspaceId = activeWorkspaceIdRef.current;
    const epoch = contextEpochRef.current;
    if (!current || !workspaceId || revisionLockedRef.current || dirty) return;
    if (mappingDirty) {
      setMappingError("请先保存数据映射，再发现数据对象。");
      return;
    }
    await runBusy("发现数据对象", async () => {
      invalidateWorkspaceReads();
      const result = await api.discoverObjects(workspaceId, sourceId, current.draftRevision);
      if (contextEpochRef.current !== epoch || activeWorkspaceIdRef.current !== workspaceId) return;
      invalidateWorkspaceReads();
      acceptDraft(applyRevision(current, result.revision));
      setValidationChecked(false);
      setOperationNotice("数据对象发现已保存，正在刷新数据目录。");
      await loadMappings();
    }, setMappingError);
  };

  const discoverFields = async (sourceId: number, objectKey: string) => {
    const current = draftRef.current;
    const workspaceId = activeWorkspaceIdRef.current;
    const epoch = contextEpochRef.current;
    if (!current || !workspaceId || revisionLockedRef.current || dirty) return;
    if (mappingDirty) {
      setMappingError("请先保存数据映射，再发现数据字段。");
      return;
    }
    await runBusy("发现数据字段", async () => {
      invalidateWorkspaceReads();
      const result = await api.discoverFields(workspaceId, sourceId, objectKey, current.draftRevision);
      if (contextEpochRef.current !== epoch || activeWorkspaceIdRef.current !== workspaceId) return;
      invalidateWorkspaceReads();
      acceptDraft(applyRevision(current, result.revision));
      setValidationChecked(false);
      setOperationNotice("数据字段发现已保存，正在刷新数据目录。");
      await loadMappings();
    }, setMappingError);
  };

  const saveMappings = async (nextMappings: OntologyMappingInput[]) => {
    const current = draftRef.current;
    const workspaceId = activeWorkspaceIdRef.current;
    const epoch = contextEpochRef.current;
    if (!current || !workspaceId || revisionLockedRef.current || dirty) return;
    await runBusy("保存映射", async () => {
      invalidateWorkspaceReads();
      setMappingLoaded(false);
      const next = await api.replaceMappings(workspaceId, current.draftRevision, nextMappings);
      if (contextEpochRef.current !== epoch || activeWorkspaceIdRef.current !== workspaceId) return;
      invalidateWorkspaceReads();
      acceptDraft(next);
      setValidationChecked(false);
      const latestMappings = await api.listMappings(workspaceId);
      if (contextEpochRef.current !== epoch || activeWorkspaceIdRef.current !== workspaceId) return;
      setMappings(latestMappings);
      setMappingRows(toEditableOntologyMappings(latestMappings));
      setMappingDirty(false);
      setMappingLoaded(true);
      setOperationNotice("数据映射已保存。");
    }, setMappingError);
  };

  const validateMappings = async (nextMappings: OntologyMappingInput[]) => {
    const current = draftRef.current;
    const workspaceId = activeWorkspaceIdRef.current;
    const epoch = contextEpochRef.current;
    if (!current || !workspaceId || revisionLockedRef.current || dirty) return;
    await runBusy("验证映射", async () => {
      invalidateWorkspaceReads();
      setMappingLoaded(false);
      const result = await api.validateMappings(workspaceId, current.draftRevision, nextMappings.map((mapping) => ({
        targetType: mapping.targetType,
        targetKey: mapping.targetKey,
        dataSourceId: mapping.dataSourceId,
      })));
      if (contextEpochRef.current !== epoch || activeWorkspaceIdRef.current !== workspaceId) return;
      invalidateWorkspaceReads();
      acceptDraft(applyRevision(current, result.revision));
      setMappingDirty(false);
      setValidationChecked(false);
      const [nextDraft, latestMappings] = await Promise.all([
        api.getDraft(workspaceId),
        api.listMappings(workspaceId),
      ]);
      if (contextEpochRef.current !== epoch || activeWorkspaceIdRef.current !== workspaceId) return;
      acceptDraft(nextDraft);
      setMappings(latestMappings);
      setMappingRows(toEditableOntologyMappings(latestMappings));
      setMappingDirty(false);
      setMappingLoaded(true);
      const invalidCount = result.results.filter((item) => !item.valid).length;
      setOperationNotice(invalidCount === 0 ? "映射已全部验证。" : `${invalidCount} 条映射需要调整。`);
    }, setMappingError);
  };

  const generateProposal = async (request: OntologyProposalRequest) => {
    const workspaceId = activeWorkspaceIdRef.current;
    const epoch = contextEpochRef.current;
    if (!workspaceId || revisionLockedRef.current) return;
    if (dirty) {
      setAiError("请先保存当前业务定义，再生成基于最新草稿的 AI 提案。");
      return;
    }
    if (mappingDirty) {
      setAiError("请先保存当前数据映射，再生成基于最新映射的 AI 提案。");
      return;
    }
    setAiError("");
    await runBusy("生成 AI 提案", async () => {
      invalidateWorkspaceReads();
      const proposal = await api.createProposal(workspaceId, request);
      if (contextEpochRef.current !== epoch || activeWorkspaceIdRef.current !== workspaceId) return;
      invalidateWorkspaceReads();
      setProposals((current) => [proposal, ...current.filter((item) => item.id !== proposal.id)]);
      setActiveProposal(proposal);
      if (proposal.status === "FAILED") {
        setAiError(presentOntologyAiDiagnostic(proposal.diagnosticCode, proposal.diagnosticMessage));
      }
    }, setAiError);
  };

  const applyAiProposal = async (proposalId: number) => {
    const current = draftRef.current;
    const workspaceId = activeWorkspaceIdRef.current;
    const epoch = contextEpochRef.current;
    const proposal = proposals.find((item) => item.id === proposalId);
    if (!current || !workspaceId || !proposal || revisionLockedRef.current) return;
    if (dirty) {
      setAiError("请先保存当前业务定义，再应用 AI 提案。");
      return;
    }
    if (mappingDirty) {
      setAiError("请先保存当前数据映射，再应用 AI 提案；未保存映射不会被覆盖。");
      return;
    }
    if (!proposal.diff || proposal.diff.baseRevision !== current.draftRevision) {
      setAiError("这条提案基于较早的草稿修订，请刷新提案后重新生成，不能直接应用。");
      return;
    }
    await runBusy("应用 AI 提案", async () => {
      invalidateWorkspaceReads();
      let next: OntologyDraftView;
      let reconciledProposal: OntologyProposalRecord | null = null;
      try {
        next = await api.applyProposal(workspaceId, proposalId, current.draftRevision);
      } catch (error) {
        if (contextEpochRef.current !== epoch || activeWorkspaceIdRef.current !== workspaceId) return;
        if (!isOntologyProposalApplyOutcomeUnknownError(error) && !isOntologyProposalAppliedReloadError(error)) {
          throw error;
        }
        try {
          const [proposalState, reloadedDraft] = await Promise.all([
            api.getProposal(workspaceId, proposalId),
            api.getDraft(workspaceId),
          ]);
          if (contextEpochRef.current !== epoch || activeWorkspaceIdRef.current !== workspaceId) return;
          if (proposalState.status !== "APPLIED") throw error;
          reconciledProposal = proposalState;
          next = reloadedDraft;
        } catch {
          throw error;
        }
      }
      if (contextEpochRef.current !== epoch || activeWorkspaceIdRef.current !== workspaceId) return;
      invalidateWorkspaceReads();
      acceptDraft(next);
      setValidationChecked(false);
      setAiError("");
      if (reconciledProposal) {
        setProposals((items) => [reconciledProposal, ...items.filter((item) => item.id !== proposalId)]);
        setActiveProposal(reconciledProposal);
        setOperationNotice("提案应用状态已核对，最新草稿已重新加载；线上版本未改变。");
      } else {
        setOperationNotice("提案已应用到草稿，线上版本未改变。");
        await loadProposals();
      }
    }, setAiError);
  };

  const publishDraft = async () => {
    const current = draftRef.current;
    const workspaceId = activeWorkspaceIdRef.current;
    const epoch = contextEpochRef.current;
    if (!current || !workspaceId || revisionLockedRef.current || dirty || mappingDirty) return;
    await runBusy("发布本体", async () => {
      invalidateWorkspaceReads();
      const baselineVersions = await api.listVersions(workspaceId);
      if (contextEpochRef.current !== epoch || activeWorkspaceIdRef.current !== workspaceId) return;
      const baselineVersionNumbers = new Set(baselineVersions.map((version) => version.version));
      const alreadyPublished = findOntologyVersionForDraftRevision(
        baselineVersions,
        current.draftRevision,
      );
      if (alreadyPublished) {
        setVersions(baselineVersions);
        closePublishConfirm();
        try {
          const next = await api.getDraft(workspaceId);
          if (contextEpochRef.current !== epoch || activeWorkspaceIdRef.current !== workspaceId) return;
          acceptDraft(next);
          setOperationNotice(`当前草稿修订已发布为版本 ${alreadyPublished.version}，未重复创建版本。`);
        } catch {
          if (contextEpochRef.current !== epoch || activeWorkspaceIdRef.current !== workspaceId) return;
          lockEditingForReload();
          setOperationError(`版本 ${alreadyPublished.version} 已存在，但重新加载失败；请重新加载工作区。`);
        }
        return;
      }
      let latestVersions = baselineVersions;
      let published: OntologyVersionSummary;
      let reconciledFromHistory = false;
      try {
        published = await api.publish(workspaceId, current.draftRevision);
      } catch (error) {
        if (contextEpochRef.current !== epoch || activeWorkspaceIdRef.current !== workspaceId) return;
        if (!isOntologyPublishOutcomeUnknownError(error)) throw error;
        try {
          latestVersions = await api.listVersions(workspaceId);
          if (contextEpochRef.current !== epoch || activeWorkspaceIdRef.current !== workspaceId) return;
          const reconciled = findOntologyVersionForDraftRevision(
            latestVersions,
            current.draftRevision,
            baselineVersionNumbers,
          );
          if (!reconciled) throw error;
          published = reconciled;
          reconciledFromHistory = true;
        } catch {
          throw error;
        }
      }
      if (contextEpochRef.current !== epoch || activeWorkspaceIdRef.current !== workspaceId) return;
      invalidateWorkspaceReads();
      setVersions(reconciledFromHistory
        ? latestVersions
        : [published, ...baselineVersions.filter((item) => item.version !== published.version)]);
      closePublishConfirm();
      try {
        const next = await api.getDraft(workspaceId);
        if (contextEpochRef.current !== epoch || activeWorkspaceIdRef.current !== workspaceId) return;
        acceptDraft(next);
        setOperationNotice(reconciledFromHistory
          ? `版本 ${published.version} 已通过版本历史核对并重新加载。`
          : `版本 ${published.version} 已由当前用户人工发布。`);
      } catch {
        if (contextEpochRef.current !== epoch || activeWorkspaceIdRef.current !== workspaceId) return;
        lockEditingForReload();
        setOperationError(`版本 ${published.version} 已发布，但重新加载失败；请重新加载工作区。`);
      }
    }, (message) => {
      closePublishConfirm();
      setOperationError(message);
    });
  };

  const installPackage = async (referencePackage: OntologyReferencePackageSummary) => {
    if (referencePackageInstallLocked) {
      setOperationError("参考包安装结果尚未确认，请先刷新业务本体列表核对，勿重复安装。");
      return;
    }
    const epoch = contextEpochRef.current;
    await runBusy("安装参考包", async () => {
      const installResult = await installReferencePackageRecoverably(
        referencePackage,
        () => contextEpochRef.current === epoch,
      );
      if (!installResult || contextEpochRef.current !== epoch) return;
      const { workspace } = installResult;
      listRequestId.current += 1;
      setWorkspaces((current) => [workspace, ...current.filter((item) => item.id !== workspace.id)]);
      await openWorkspace(workspace);
      if (contextEpochRef.current !== epoch) return;
      setOperationNotice(installResult.reconciled
        ? "已核对到当前管理员此前安装的同一参考包工作区。"
        : "参考包已安装为可编辑草稿。");
    });
  };

  const submitWizard = async (event: FormEvent) => {
    event.preventDefault();
    if (workspaceCreateLocked) {
      setOperationError("创建结果尚未确认，请先返回列表并刷新核对，勿重复创建。");
      return;
    }
    const name = domainName.trim();
    const purpose = domainPurpose.trim();
    const objectsDescription = domainObjects.trim();
    const questions = domainQuestions.trim();
    const mode = wizardMode;
    const sourceName = wizardSourceName.trim();
    const sourceType = wizardSourceType;
    const adapterKey = wizardAdapterKey.trim();
    const sampleDataJson = wizardSampleData;
    if (!name || !purpose || !objectsDescription || !questions) return;
    if (mode === "source" && sourceType === "INLINE_SAMPLE") {
      try {
        JSON.parse(sampleDataJson);
      } catch {
        setOperationError("示例记录不是有效的 JSON，请修正后再创建。");
        return;
      }
    }
    const key = createStableOntologyKey(name, workspaces.map((workspace) => workspace.key), "business-domain");
    const epoch = contextEpochRef.current;
    let createdWorkspace: OntologyWorkspaceView | null = null;
    await runBusy("创建业务本体", async () => {
      const createResult = await createWorkspaceRecoverably(
        { key, name, description: purpose },
        () => contextEpochRef.current === epoch,
      );
      if (!createResult || contextEpochRef.current !== epoch) return;
      const { workspace } = createResult;
      createdWorkspace = workspace;
      listRequestId.current += 1;
      draftRequestId.current += 1;
      invalidateWorkspaceReads();
      activeWorkspaceIdRef.current = workspace.id;
      setWorkspaces((current) => [workspace, ...current.filter((item) => item.id !== workspace.id)]);
      setSelectedWorkspace(workspace);
      setPageMode("workspace");
      setActiveTab("model");
      setDraft(null);
      draftRef.current = null;
      setDraftStatus("loading");
      setDraftError("");
      resetWorkspacePanels();

      const isCurrent = () => contextEpochRef.current === epoch
        && activeWorkspaceIdRef.current === workspace.id;
      let nextDraft: OntologyDraftView;
      try {
        nextDraft = await api.getDraft(workspace.id);
      } catch (error) {
        if (!isCurrent()) return;
        setDraftStatus("error");
        setDraftError("工作区已经创建，但初始化草稿载入失败。请直接在此工作区重新加载，勿重复创建。");
        throw error;
      }
      if (!isCurrent()) return;
      invalidateWorkspaceReads();
      acceptDraft(nextDraft);
      setDraftStatus("ready");
      setSelection(nextDraft.document.concepts[0]
        ? { kind: "concept", key: nextDraft.document.concepts[0].key }
        : null);
      resetWizardForm();
      setOperationNotice(createResult.reconciled
        ? "已核对到当前管理员此前创建的同一工作区，后续初始化可在当前工作区继续。"
        : "工作区已创建，后续初始化可在当前工作区继续。");

      let sourceSelection: OntologyProposalRequest["selectedSources"] = [];
      let wizardAiError = "";

      if (mode === "source" && sourceName) {
        const sourceInput: OntologyDataSourceMutationInput = {
          id: null,
          key: createStableOntologyKey(sourceName, [], "data-source"),
          name: sourceName,
          type: sourceType,
          configJson: sourceType === "CONNECTOR" && adapterKey
            ? JSON.stringify({ adapterKey })
            : null,
          sampleDataJson: sourceType === "INLINE_SAMPLE" ? sampleDataJson : null,
        };
        invalidateWorkspaceReads();
        const sourceResult = await createDataSourceRecoverably(
          workspace.id,
          nextDraft.draftRevision,
          sourceInput,
          isCurrent,
        );
        if (!isCurrent()) return;
        if (!sourceResult) return;
        if (!sourceResult.draft) {
          setOperationError("工作区已创建，但数据来源创建结果未知；请重新加载核对，勿重复创建。");
          return;
        }
        nextDraft = sourceResult.draft;
        invalidateWorkspaceReads();
        acceptDraft(nextDraft);
        setDraftStatus("ready");
        setOperationNotice(sourceResult.reconciled
          ? "工作区已创建，并已核对到之前创建的数据来源。"
          : "工作区和数据来源已创建，发现步骤可在当前工作区继续。");
        const source = findOntologySourceByIdentity(nextDraft.sources, sourceInput);
        if (source) {
          invalidateWorkspaceReads();
          const objects = await api.discoverObjects(workspace.id, source.id, nextDraft.draftRevision);
          if (!isCurrent()) return;
          invalidateWorkspaceReads();
          nextDraft = applyRevision(nextDraft, objects.revision);
          acceptDraft(nextDraft);
          const firstObject = objects.items[0];
          if (firstObject) {
            invalidateWorkspaceReads();
            const fields = await api.discoverFields(workspace.id, source.id, firstObject.key, nextDraft.draftRevision);
            if (!isCurrent()) return;
            invalidateWorkspaceReads();
            nextDraft = applyRevision(nextDraft, fields.revision);
            acceptDraft(nextDraft);
            sourceSelection = [{ dataSourceId: source.id, objectKey: firstObject.key, fieldKeys: fields.items.map((field) => field.key) }];
          }
        }
      }

      let proposal: OntologyProposalRecord | null = null;
      try {
        invalidateWorkspaceReads();
        proposal = await api.createProposal(workspace.id, {
          instruction: buildDomainInstruction(name, purpose, objectsDescription, questions),
          mode: mode === "source" ? "DATA_SOURCE_FIRST" : "DOMAIN_FIRST",
          selectedSources: sourceSelection,
        });
      } catch (error) {
        if (!isCurrent()) return;
        wizardAiError = formatOntologyError(error);
      }
      if (!isCurrent()) return;
      invalidateWorkspaceReads();
      setActiveTab(proposal ? "proposal" : "model");
      setAiError(wizardAiError);
      if (proposal) {
        setProposals([proposal]);
        setActiveProposal(proposal);
      }
      setOperationNotice(proposal ? "AI 已生成一条待审阅提案，草稿尚未改变。" : "工作区已创建，可继续手工建模。");
    }, (message) => {
      setOperationError(createdWorkspace
        ? `工作区已创建，后续初始化未完成；请在当前工作区继续。${message}`
        : message);
    });
  };

  const showWizard = () => {
    if (workspaceCreateLocked) {
      setOperationError("创建结果尚未确认，请先刷新列表核对后再创建。");
      return;
    }
    setPageMode("wizard");
    setOperationError("");
    setOperationNotice("");
  };

  const hasUnvalidatedMappings = hasUnvalidatedOntologyMappings(draft?.document.mappings ?? [], mappings);
  const validationHasErrors = validationIssues.some((issue) => issue.severity === "ERROR");
  const canPublish = Boolean(
    draft
    && !dirty
    && !mappingDirty
    && validationChecked
    && !validationHasErrors
    && !hasUnvalidatedMappings
    && draft.document.concepts.length > 0
    && draft.status !== "ARCHIVED"
    && !revisionLocked
    && !busyAction,
  );
  const nextVersion = (draft?.publishedVersion ?? 0) + 1;
  const publishConfirmation = `发布版本 ${nextVersion}；当前线上版本不会被 AI 自动替换`;
  const compilePreviewCurrent = Boolean(
    draft
    && isOntologyCompilePreviewBindingCurrent(
      compilePreviewBinding,
      draft.draftRevision,
      mappings,
      dirty,
      mappingDirty,
      draft.publishedVersion,
    ),
  );
  const currentCompilePreview = compilePreviewCurrent ? compilePreview : null;

  const copyTechnical = async (text: string, label: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopiedLabel(label);
      window.setTimeout(() => setCopiedLabel(""), 1800);
    } catch {
      setOperationError("复制失败，请手工选择内容复制。");
    }
  };

  const copyLocalDraft = async () => {
    const current = draftRef.current;
    if (!current) return;
    try {
      await navigator.clipboard.writeText(JSON.stringify({
        workspaceKey: current.workspace.key,
        draftRevision: current.draftRevision,
        copiedAt: new Date().toISOString(),
        document: current.document,
        mappingDirty,
        mappings: mappingRows.map((mapping) => ({
          targetType: mapping.targetType,
          targetKey: mapping.targetKey,
          dataSourceId: mapping.dataSourceId,
          physicalObjectKey: mapping.physicalObjectKey,
          physicalFieldKey: mapping.physicalFieldKey,
          relationTargetFieldKey: mapping.relationTargetFieldKey,
          transform: mapping.transform,
          confidence: mapping.confidence,
        })),
      }, null, 2));
      setCopiedLabel("local-draft");
      setOperationNotice("本地草稿已复制，可在重新加载后用于人工比对。");
      window.setTimeout(() => setCopiedLabel(""), 1800);
    } catch {
      setOperationError("本地草稿复制失败，请暂勿离开并联系管理员。");
    }
  };

  if (previousAuthScopeRef.current !== authScopeKey) {
    return <div className="admin-page ontology-page" role="status" aria-label="正在切换组织上下文" />;
  }

  if (pageMode === "wizard") {
    return (
      <div className="admin-page ontology-page ontology-page--wizard" aria-busy={Boolean(busyAction)}>
        <header className="ontology-page-header">
          <div>
            <button type="button" className="ontology-back-action" disabled={Boolean(busyAction)} onClick={() => setPageMode("list")}>
              <ArrowLeft size={15} aria-hidden /> 返回业务本体
            </button>
            <h1>创建业务本体</h1>
            <p>先描述业务领域，AI 只生成待审阅提案；模型不可用时仍可继续手工建模。</p>
          </div>
        </header>

        {operationError && <div className="ontology-inline-alert" role="alert">{operationError}</div>}

        <div className="ontology-wizard">
          <div className="ontology-tabs" role="tablist" aria-label="创建方式">
            {WIZARD_TABS.map((tab, index) => (
              <button
                key={tab.id}
                id={`ontology-wizard-tab-${tab.id}`}
                type="button"
                role="tab"
                aria-selected={wizardMode === tab.id}
                aria-controls={`ontology-wizard-panel-${tab.id}`}
                tabIndex={wizardMode === tab.id ? 0 : -1}
                className={wizardMode === tab.id ? "is-active" : ""}
                onClick={() => setWizardMode(tab.id)}
                onKeyDown={(event) => handleWizardTabKeyDown(event, index)}
              >
                {tab.label}
              </button>
            ))}
          </div>
          {WIZARD_TABS.filter((tab) => tab.id !== wizardMode).map((tab) => (
            <div
              key={tab.id}
              id={`ontology-wizard-panel-${tab.id}`}
              role="tabpanel"
              aria-labelledby={`ontology-wizard-tab-${tab.id}`}
              hidden
            />
          ))}
          <form
            id={`ontology-wizard-panel-${wizardMode}`}
            role="tabpanel"
            aria-labelledby={`ontology-wizard-tab-${wizardMode}`}
            onSubmit={submitWizard}
          >
            <div className="ontology-wizard__intro">
              <span>第 1 步</span>
              <h2>{wizardMode === "domain" ? "告诉我们业务人员如何理解这个领域" : "先连接数据，再补充业务含义"}</h2>
              <p>{wizardMode === "domain" ? "这些回答会成为 AI 提案的业务约束，不会直接发布。" : "数据源发现是次级入口，最终仍会生成同样的业务本体草稿。"}</p>
            </div>
            <div className="ontology-wizard__form-grid">
              <label>
                <span>领域名称</span>
                <input value={domainName} disabled={Boolean(busyAction)} placeholder="例如：项目交付" onChange={(event) => setDomainName(event.target.value)} />
              </label>
              <label>
                <span>这个领域用来做什么？</span>
                <input value={domainPurpose} disabled={Boolean(busyAction)} placeholder="例如：统一项目、任务和负责人语义" onChange={(event) => setDomainPurpose(event.target.value)} />
              </label>
              <label className="ontology-wizard__full">
                <span>核心业务对象</span>
                <textarea rows={3} value={domainObjects} disabled={Boolean(busyAction)} placeholder="例如：项目、交付任务、负责人、里程碑" onChange={(event) => setDomainObjects(event.target.value)} />
              </label>
              <label className="ontology-wizard__full">
                <span>业务人员经常会问什么？</span>
                <textarea rows={3} value={domainQuestions} disabled={Boolean(busyAction)} placeholder="例如：哪些任务延期？每个项目当前由谁负责？" onChange={(event) => setDomainQuestions(event.target.value)} />
              </label>
            </div>

            {wizardMode === "source" && (
              <div className="ontology-wizard__source-step">
                <div className="ontology-subsection-heading">
                  <Link2 size={16} aria-hidden />
                  <div><strong>数据来源</strong><span>连接配置只通过专用数据源请求保存</span></div>
                </div>
                <div className="ontology-wizard__form-grid">
                  <label>
                    <span>来源名称</span>
                    <input value={wizardSourceName} disabled={Boolean(busyAction)} placeholder="例如：项目交付示例数据" onChange={(event) => setWizardSourceName(event.target.value)} />
                  </label>
                  <label>
                    <span>来源方式</span>
                    <select value={wizardSourceType} disabled={Boolean(busyAction)} onChange={(event) => setWizardSourceType(event.target.value as OntologySourceType)}>
                      <option value="INLINE_SAMPLE">内置示例数据</option>
                      <option value="CONNECTOR">组织连接器</option>
                    </select>
                  </label>
                  {wizardSourceType === "CONNECTOR" ? (
                    <label className="ontology-wizard__full">
                      <span>已配置连接器</span>
                      <select value={wizardAdapterKey} disabled={Boolean(busyAction)} onChange={(event) => setWizardAdapterKey(event.target.value)}>
                        {SUPPORTED_ONTOLOGY_CONNECTORS.map((connector) => (
                          <option key={connector.value} value={connector.value}>{connector.label}</option>
                        ))}
                      </select>
                    </label>
                  ) : (
                    <label className="ontology-wizard__full">
                      <span>示例记录（JSON）</span>
                      <textarea rows={5} value={wizardSampleData} disabled={Boolean(busyAction)} spellCheck={false} onChange={(event) => setWizardSampleData(event.target.value)} />
                    </label>
                  )}
                </div>
              </div>
            )}

            <div className="ontology-wizard__actions">
              <span>AI 不可用也会保留新工作区，业务对象可手工添加。</span>
              <button
                type="submit"
                className="cici-btn cici-btn--primary"
                disabled={workspaceCreateLocked || Boolean(busyAction) || !domainName.trim() || !domainPurpose.trim() || !domainObjects.trim() || !domainQuestions.trim() || (wizardMode === "source" && !wizardSourceName.trim())}
              >
                <Sparkles size={15} aria-hidden /> {busyAction || "创建并生成提案"}
              </button>
            </div>
          </form>
        </div>
      </div>
    );
  }

  if (pageMode === "workspace" && selectedWorkspace) {
    const busy = Boolean(busyAction);
    const editingLocked = busy || revisionLocked;
    const proposalGenerateDisabledReason = revisionLocked
      ? "草稿修订已变化，请先重新加载。"
      : dirty
        ? "请先保存当前业务定义，再生成基于最新草稿的提案。"
        : mappingDirty
          ? "请先保存当前数据映射，再生成基于最新映射的提案。"
        : "";
    const proposalApplyDisabledReason = revisionLocked
      ? "草稿修订已变化，请先重新加载。"
      : dirty
        ? "请先保存当前业务定义，再应用提案。"
        : mappingDirty
          ? "请先保存当前数据映射，再应用提案。"
        : activeProposal?.diff && draft && activeProposal.diff.baseRevision !== draft.draftRevision
          ? `提案基于修订 ${activeProposal.diff.baseRevision}，当前草稿为修订 ${draft.draftRevision}；请重新生成提案。`
          : "";
    const technicalContent = technicalTab === "schema"
      ? currentCompilePreview?.jsonSchema ?? ""
      : technicalTab === "graphql"
        ? currentCompilePreview?.graphqlSdl ?? ""
        : currentCompilePreview?.queryContractJson ?? "";
    const technicalLabel = technicalTab === "schema" ? "JSON Schema" : technicalTab === "graphql" ? "GraphQL SDL" : "semantic-query 示例";

    return (
      <div className="admin-page ontology-page ontology-page--workspace" aria-busy={busy || draftStatus === "loading"}>
        <header className="ontology-workspace-header">
          <div className="ontology-workspace-header__title">
            <button type="button" className="ontology-back-action" disabled={busy} onClick={closeWorkspace}>
              <ArrowLeft size={15} aria-hidden /> 本体列表
            </button>
            <div>
              <h1>{selectedWorkspace.name}</h1>
              <p>
                草稿修订 {draft?.draftRevision ?? selectedWorkspace.draftRevision}
                {selectedWorkspace.publishedVersion == null ? " · 尚未发布" : ` · 线上版本 ${selectedWorkspace.publishedVersion}`}
                {dirty || mappingDirty ? " · 有未保存修改" : " · 已保存"}
              </p>
            </div>
          </div>
          <div className="ontology-workspace-header__actions" aria-label="草稿操作">
            <button type="button" className="ontology-text-action" disabled={busy || draftStatus !== "ready"} onClick={() => void reloadCurrentDraft()}>
              <RefreshCw size={14} aria-hidden /> 重新加载
            </button>
            <button type="button" className="cici-btn cici-btn--ghost" disabled={editingLocked || !draft || !dirty} onClick={() => draft && void saveDocument(draft.document)}>
              <Save size={15} aria-hidden /> 保存草稿
            </button>
            <button type="button" className="cici-btn cici-btn--ghost" disabled={editingLocked || !draft || dirty || mappingDirty} onClick={() => void runValidation()}>
              <ShieldCheck size={15} aria-hidden /> 运行校验
            </button>
            <button type="button" className="cici-btn cici-btn--primary" disabled={!canPublish} onClick={openPublishConfirm}>
              发布版本 {nextVersion}
            </button>
          </div>
        </header>

        <div className="ontology-workspace-status" aria-live="polite">
          {busyAction && <span className="is-busy">{busyAction}...</span>}
          {revisionLocked && (
            <span className="is-error" role="alert">
              <AlertTriangle size={14} aria-hidden />
              编辑已锁定，请先复制本地草稿，再重新加载核对。
              <button type="button" className="ontology-text-action" onClick={() => void copyLocalDraft()}>
                <Clipboard size={13} aria-hidden /> {copiedLabel === "local-draft" ? "已复制" : "复制本地草稿"}
              </button>
            </span>
          )}
          {operationNotice && <span className="is-success"><Check size={14} aria-hidden /> {operationNotice}</span>}
          {operationError && <span className="is-error" role="alert"><AlertTriangle size={14} aria-hidden /> {operationError}</span>}
        </div>

        <div className="ontology-tabs ontology-workspace-tabs" role="tablist" aria-label="本体工作区">
          {WORKSPACE_TABS.map((tab, index) => (
            <button
              key={tab.id}
              type="button"
              role="tab"
              id={`ontology-tab-${tab.id}`}
              aria-selected={activeTab === tab.id}
              aria-controls={`ontology-panel-${tab.id}`}
              tabIndex={activeTab === tab.id ? 0 : -1}
              className={activeTab === tab.id ? "is-active" : ""}
              onClick={() => switchWorkspaceTab(tab.id)}
              onKeyDown={(event) => handleWorkspaceTabKeyDown(event, index)}
            >
              {tab.label}
              {tab.id === "validation" && validationChecked && validationHasErrors && <span aria-label="存在校验错误">!</span>}
            </button>
          ))}
        </div>
        {WORKSPACE_TABS.filter((tab) => draftStatus !== "ready" || !draft || tab.id !== activeTab).map((tab) => (
          <div
            key={tab.id}
            id={`ontology-panel-${tab.id}`}
            role="tabpanel"
            aria-labelledby={`ontology-tab-${tab.id}`}
            hidden={tab.id !== activeTab}
          />
        ))}

        {draftStatus === "loading" && (
          <div className="ontology-workspace-loading" role="status">
            <span />
            <span />
            <span />
          </div>
        )}
        {draftStatus === "error" && (
          <div className="ontology-workspace-error" role="alert">
            <AlertTriangle size={22} aria-hidden />
            <strong>工作区载入失败</strong>
            <p>{draftError}</p>
            <button type="button" className="cici-btn cici-btn--ghost" onClick={() => void reloadCurrentDraft()}>重试</button>
          </div>
        )}

        {draftStatus === "ready" && draft && activeTab === "model" && (
          <div id="ontology-panel-model" role="tabpanel" aria-labelledby="ontology-tab-model" className="ontology-modeling-workbench">
            <aside className="ontology-catalog" aria-label="业务定义目录">
              <div className="ontology-panel-heading">
                <div><span>业务目录</span><strong>{draft.document.concepts.length + draft.document.relations.length + draft.document.metrics.length + draft.document.actions.length} 项定义</strong></div>
              </div>
              <div className="ontology-catalog__actions">
                <button type="button" className="cici-btn cici-btn--primary" disabled={editingLocked} onClick={addConcept}><Plus size={15} aria-hidden /> 添加业务对象</button>
                <button type="button" className="cici-btn cici-btn--ghost" disabled={editingLocked || draft.document.concepts.length < 2} onClick={addRelation}><Link2 size={15} aria-hidden /> 添加关系</button>
              </div>
              <section className="ontology-catalog__group">
                <div><h2>业务对象</h2><button type="button" className="ontology-icon-action" aria-label="添加业务对象" disabled={editingLocked} onClick={addConcept}><Plus size={14} aria-hidden /></button></div>
                {draft.document.concepts.length === 0 && <p>还没有业务对象</p>}
                {draft.document.concepts.map((concept) => (
                  <button key={concept.key} type="button" className={selection?.kind === "concept" && selection.key === concept.key ? "is-selected" : ""} onClick={() => setSelection(selectOntologyItem(selection, { kind: "concept", key: concept.key }))}>
                    <span>{concept.name}</span><small>{concept.properties.length} 个属性</small>
                  </button>
                ))}
              </section>
              <section className="ontology-catalog__group">
                <div><h2>关系</h2><button type="button" className="ontology-icon-action" aria-label="添加关系" disabled={editingLocked || draft.document.concepts.length < 2} onClick={addRelation}><Plus size={14} aria-hidden /></button></div>
                {draft.document.relations.map((relation) => (
                  <button key={relation.key} type="button" className={selection?.kind === "relation" && selection.key === relation.key ? "is-selected" : ""} onClick={() => setSelection(selectOntologyItem(selection, { kind: "relation", key: relation.key }))}>
                    <span>{relation.name}</span><small>{relation.forwardLabel || "未设置读法"}</small>
                  </button>
                ))}
              </section>
              <section className="ontology-catalog__group">
                <div><h2>业务指标</h2><button type="button" className="ontology-icon-action" aria-label="添加业务指标" disabled={editingLocked} onClick={addMetric}><Plus size={14} aria-hidden /></button></div>
                {draft.document.metrics.map((metric) => (
                  <button key={metric.key} type="button" className={selection?.kind === "metric" && selection.key === metric.key ? "is-selected" : ""} onClick={() => setSelection(selectOntologyItem(selection, { kind: "metric", key: metric.key }))}>
                    <span>{metric.name}</span><small>{ontologyAggregationLabel(metric.aggregation)}</small>
                  </button>
                ))}
              </section>
              <section className="ontology-catalog__group">
                <div><h2>可执行动作</h2><button type="button" className="ontology-icon-action" aria-label="添加可执行动作" disabled={editingLocked} onClick={addAction}><Plus size={14} aria-hidden /></button></div>
                {draft.document.actions.map((action) => (
                  <button key={action.key} type="button" className={selection?.kind === "action" && selection.key === action.key ? "is-selected" : ""} onClick={() => setSelection(selectOntologyItem(selection, { kind: "action", key: action.key }))}>
                    <span>{action.name}</span><small>只生成契约</small>
                  </button>
                ))}
              </section>
            </aside>
            <OntologyCanvas
              document={draft.document}
              selection={selection}
              busy={editingLocked}
              onSelect={(next) => setSelection(selectOntologyItem(selection, next))}
              onChange={changeDocument}
              onCommit={saveDocument}
            />
            <OntologyInspector
              document={draft.document}
              selection={selection}
              busy={editingLocked}
              onChange={changeDocument}
              onSave={saveDocument}
              onDelete={deleteSelection}
            />
          </div>
        )}

        {draftStatus === "ready" && draft && activeTab === "mapping" && (
          <div id="ontology-panel-mapping" role="tabpanel" aria-labelledby="ontology-tab-mapping">
            <OntologyMappingPanel
              document={draft.document}
              sources={draft.sources}
              catalog={catalog}
              mappingRows={mappingRows}
              mappingDirty={mappingDirty}
              loading={mappingLoading}
              busy={editingLocked || dirty}
              error={mappingError}
              onMappingRowsChange={handleMappingRowsChange}
              onReload={reloadMappings}
              onCreateSource={createSource}
              onDiscoverObjects={discoverObjects}
              onDiscoverFields={discoverFields}
              onSaveMappings={saveMappings}
              onValidateMappings={validateMappings}
            />
          </div>
        )}

        {draftStatus === "ready" && draft && activeTab === "proposal" && (
          <div id="ontology-panel-proposal" role="tabpanel" aria-labelledby="ontology-tab-proposal">
            <OntologyProposalPanel
              currentDocument={draft.document}
              sources={draft.sources}
              catalog={catalog}
              proposals={proposals}
              activeProposal={activeProposal}
              loading={proposalLoading}
              busy={busy}
              locked={revisionLocked}
              error={aiError}
              generateDisabledReason={proposalGenerateDisabledReason}
              applyDisabledReason={proposalApplyDisabledReason}
              onReload={loadProposals}
              onSelect={setActiveProposal}
              onGenerate={generateProposal}
              onApply={applyAiProposal}
              onContinueManually={() => { setAiError(""); setActiveTab("model"); }}
            />
          </div>
        )}

        {draftStatus === "ready" && draft && activeTab === "validation" && (
          <section id="ontology-panel-validation" role="tabpanel" aria-labelledby="ontology-tab-validation" className="ontology-validation">
            <header className="ontology-section-header">
              <div>
                <span>发布前检查</span>
                <h2>校验草稿和数据映射</h2>
                <p>存在错误或未验证映射时，人工发布入口保持禁用。</p>
              </div>
              <button type="button" className="cici-btn cici-btn--primary" disabled={editingLocked || dirty || mappingDirty} onClick={() => void runValidation()}>
                <ShieldCheck size={15} aria-hidden /> {validationChecked ? "重新校验" : "运行校验"}
              </button>
            </header>
            {!validationChecked && (
              <div className="ontology-validation__empty" role="status">
                <ShieldCheck size={22} aria-hidden />
                <strong>尚未运行本次修订校验</strong>
                <span>先保存草稿，再检查对象、关系、指标与映射。</span>
              </div>
            )}
            {validationChecked && validationIssues.length === 0 && (
              <div className="ontology-validation__success" role="status"><Check size={18} aria-hidden /><strong>没有发现校验问题</strong><span>发布仍需要人工确认。</span></div>
            )}
            {validationChecked && validationIssues.length > 0 && (
              <div className="ontology-issue-list">
                <div className="ontology-issue-list__head"><span>级别</span><span>业务定位</span><span>问题说明</span></div>
                {validationIssues.map((issue, index) => {
                  const businessIssue = presentOntologyValidationIssue(issue, draft.document);
                  return (
                    <div className={`ontology-issue-row is-${issue.severity.toLowerCase()}`} key={`${issue.severity}-${index}`}>
                      <span>{businessIssue.severityLabel}</span>
                      <span>{businessIssue.location}</span>
                      <p>{businessIssue.message}</p>
                    </div>
                  );
                })}
              </div>
            )}
            <footer className="ontology-validation__publish">
              <div>
                <strong>{publishConfirmation}</strong>
                <span>{mappingDirty ? "请先保存数据映射。" : hasUnvalidatedMappings ? "仍有映射未验证。" : validationHasErrors ? "请先修复错误。" : dirty ? "请先保存草稿。" : validationChecked ? "所有发布门已检查。" : "需要先运行校验。"}</span>
              </div>
              <button type="button" className="cici-btn cici-btn--primary" disabled={!canPublish} onClick={openPublishConfirm}>进入人工确认</button>
            </footer>
          </section>
        )}

        {draftStatus === "ready" && activeTab === "versions" && (
          <section id="ontology-panel-versions" role="tabpanel" aria-labelledby="ontology-tab-versions" className="ontology-versions">
            <header className="ontology-section-header">
              <div><span>不可变版本</span><h2>版本历史</h2><p>查看版本号、内容哈希、发布人和发布时间。</p></div>
              <button type="button" className="ontology-text-action" disabled={versionsLoading} onClick={() => void loadVersions()}><RefreshCw size={14} aria-hidden /> 刷新</button>
            </header>
            {versionsLoading && <div className="ontology-panel-loading" role="status"><span /><span /><span /></div>}
            {!versionsLoading && versions.length === 0 && <div className="ontology-versions__empty" role="status"><History size={22} aria-hidden /><strong>还没有已发布版本</strong><span>完成校验并人工发布后，这里会保留不可变历史。</span></div>}
            {!versionsLoading && versions.length > 0 && (
              <div className="ontology-versions__layout">
                <nav aria-label="本体版本">
                  {versions.map((version) => (
                    <button
                      type="button"
                      key={version.version}
                      className={versionDetail?.summary.version === version.version ? "is-selected" : ""}
                      onClick={() => void loadVersionDetail(version.version)}
                    >
                      <strong>版本 {version.version}</strong>
                      <span>{formatDateTime(version.publishedAt)}</span>
                    </button>
                  ))}
                </nav>
                {versionDetail && (
                  <div className="ontology-version-detail">
                    <div><span>版本号</span><strong>{versionDetail.summary.version}</strong></div>
                    <div><span>内容哈希</span><code>{versionDetail.summary.contentHash}</code></div>
                    <div><span>发布人</span><strong>{versionDetail.summary.publishedBy}</strong></div>
                    <div><span>发布时间</span><strong>{formatDateTime(versionDetail.summary.publishedAt)}</strong></div>
                    <div><span>来源草稿修订</span><strong>{versionDetail.summary.sourceDraftRevision}</strong></div>
                  </div>
                )}
              </div>
            )}
          </section>
        )}

        {draftStatus === "ready" && activeTab === "technical" && (
          <section id="ontology-panel-technical" role="tabpanel" aria-labelledby="ontology-tab-technical" className="ontology-technical">
            <header className="ontology-section-header">
              <div><span>只读契约</span><h2>技术预览</h2><p>业务建模不要求阅读这些内容，技术团队可在这里复制确定性契约。</p></div>
              <button type="button" className="ontology-text-action" disabled={technicalLoading || dirty || mappingDirty || revisionLocked} onClick={() => void loadCompilePreview()}><RefreshCw size={14} aria-hidden /> 重新生成</button>
            </header>
            <div className="ontology-tabs ontology-technical__tabs" role="tablist" aria-label="契约类型">
              {TECHNICAL_TABS.map((tab, index) => (
                <button
                  key={tab.id}
                  id={`ontology-technical-tab-${tab.id}`}
                  type="button"
                  role="tab"
                  aria-selected={technicalTab === tab.id}
                  aria-controls={`ontology-technical-panel-${tab.id}`}
                  tabIndex={technicalTab === tab.id ? 0 : -1}
                  className={technicalTab === tab.id ? "is-active" : ""}
                  onClick={() => setTechnicalTab(tab.id)}
                  onKeyDown={(event) => handleTechnicalTabKeyDown(event, index)}
                >
                  {tab.label}
                </button>
              ))}
            </div>
            {TECHNICAL_TABS.filter((tab) => tab.id !== technicalTab).map((tab) => (
              <div
                key={tab.id}
                id={`ontology-technical-panel-${tab.id}`}
                role="tabpanel"
                aria-labelledby={`ontology-technical-tab-${tab.id}`}
                hidden
              />
            ))}
            <div id={`ontology-technical-panel-${technicalTab}`} role="tabpanel" aria-labelledby={`ontology-technical-tab-${technicalTab}`}>
              {technicalLoading && <div className="ontology-panel-loading" role="status"><span /><span /><span /></div>}
              {!technicalLoading && !currentCompilePreview && <div className="ontology-technical__empty" role="status"><Code2 size={22} aria-hidden /><strong>暂未生成技术预览</strong><span>{dirty || mappingDirty ? "请先保存业务定义和数据映射，再生成最新契约。" : "选择“重新生成”，不会修改草稿或发布版本。"}</span></div>}
              {!technicalLoading && currentCompilePreview && (
                <div className="ontology-code-preview">
                  <div><span>{technicalLabel} · 版本候选 {currentCompilePreview.version} · {currentCompilePreview.contentHash.slice(0, 12)}</span><button type="button" className="ontology-text-action" onClick={() => void copyTechnical(technicalContent, technicalLabel)}><Clipboard size={14} aria-hidden /> {copiedLabel === technicalLabel ? "已复制" : "复制"}</button></div>
                  <pre tabIndex={0} aria-label={`${technicalLabel}只读内容`}><code>{technicalContent}</code></pre>
                </div>
              )}
            </div>
          </section>
        )}

        {publishConfirmOpen && (
          <div className="ontology-modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) closePublishConfirm(); }}>
            <section ref={publishDialogRef} className="ontology-modal" role="dialog" aria-modal="true" aria-labelledby="ontology-publish-dialog-title" aria-describedby="ontology-publish-dialog-description">
              <header>
                <div><span>人工发布确认</span><h2 id="ontology-publish-dialog-title">确认不可变版本</h2></div>
                <button type="button" className="ontology-icon-action" aria-label="关闭发布确认" disabled={busy} onClick={closePublishConfirm}><X size={16} aria-hidden /></button>
              </header>
              <div className="ontology-modal__body">
                <AlertTriangle size={22} aria-hidden />
                <strong>{publishConfirmation}</strong>
                <p id="ontology-publish-dialog-description">AI 无权执行此操作。确认后，当前草稿将生成新的不可变线上快照。</p>
              </div>
              <footer>
                <button type="button" className="cici-btn cici-btn--ghost" data-dialog-initial-focus disabled={busy} onClick={closePublishConfirm}>取消</button>
                <button type="button" className="cici-btn cici-btn--primary" disabled={busy || revisionLocked} onClick={() => void publishDraft()}>{busy ? "正在发布" : "确认人工发布"}</button>
              </footer>
            </section>
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="admin-page ontology-page ontology-page--list" aria-busy={listStatus === "loading" || Boolean(busyAction)}>
      <header className="ontology-page-header ontology-page-header--list">
        <div>
          <span>企业业务语义</span>
          <h1>业务本体</h1>
          <p>用统一业务语言组织对象、关系、指标与数据来源，再由人工发布不可变版本。</p>
        </div>
        <div>
          <button type="button" className="ontology-text-action" disabled={listStatus === "loading" || Boolean(busyAction)} onClick={() => void loadWorkspaces()}><RefreshCw size={14} aria-hidden /> 刷新</button>
          <button type="button" className="cici-btn cici-btn--primary" disabled={workspaceCreateLocked || Boolean(busyAction)} onClick={showWizard}><Plus size={15} aria-hidden /> 创建业务本体</button>
        </div>
      </header>

      {operationError && <div className="ontology-inline-alert" role="alert">{operationError}</div>}
      {busyAction && <div className="ontology-inline-status" role="status">{busyAction}...</div>}

      <section className="ontology-list-section" aria-labelledby="ontology-list-heading">
        <div className="ontology-list-section__head"><div><h2 id="ontology-list-heading">本体工作区</h2><span>{workspaces.length} 个业务领域</span></div></div>
        {listStatus === "loading" && <div className="ontology-list-skeleton" role="status" aria-label="正在加载业务本体"><span /><span /><span /></div>}
        {listStatus === "error" && (
          <div className="ontology-list-error" role="alert"><AlertTriangle size={20} aria-hidden /><div><strong>业务本体加载失败</strong><p>{listError}</p><button type="button" className="ontology-text-action" onClick={() => void loadWorkspaces()}>重试</button></div></div>
        )}
        {listStatus === "ready" && workspaces.length === 0 && (
          <div className="ontology-list-empty" role="status"><Boxes size={24} aria-hidden /><div><strong>还没有业务本体</strong><p>从业务领域描述开始，AI 会生成可审阅提案；也可以完全手工建模。</p><button type="button" className="cici-btn cici-btn--primary" disabled={workspaceCreateLocked} onClick={showWizard}>创建第一个业务本体</button></div></div>
        )}
        {listStatus === "ready" && workspaces.length > 0 && (
          <div className="ontology-list-table-wrap">
            <table className="ontology-list-table">
              <thead><tr><th>业务领域</th><th>状态</th><th>草稿修订</th><th>线上版本</th><th>最近更新</th><th aria-label="操作" /></tr></thead>
              <tbody>
                {workspaces.map((workspace) => (
                  <tr key={workspace.id}>
                    <td><strong>{workspace.name}</strong><span>{workspace.description || "暂无说明"}</span></td>
                    <td><span className={`ontology-workspace-state is-${workspace.status.toLowerCase()}`}>{workspaceStatusLabel(workspace.status)}</span></td>
                    <td>{workspace.draftRevision}</td>
                    <td>{workspace.publishedVersion ?? "尚未发布"}</td>
                    <td>{formatDateTime(workspace.updatedAt)}</td>
                    <td><button type="button" className="ontology-text-action" disabled={Boolean(busyAction)} onClick={() => void openWorkspace(workspace)}>进入工作台</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="ontology-package-section" aria-labelledby="ontology-package-heading">
        <div className="ontology-list-section__head"><div><h2 id="ontology-package-heading">参考业务包</h2><span>安装后仍是可编辑草稿</span></div></div>
        {packageStatus === "loading" && <div className="ontology-package-skeleton" role="status"><span /><span /></div>}
        {packageStatus === "error" && <div className="ontology-list-error" role="alert"><AlertTriangle size={18} aria-hidden /><div><strong>参考包加载失败</strong><p>{packageError}</p><button type="button" className="ontology-text-action" onClick={() => void loadReferencePackages()}>重试</button></div></div>}
        {packageStatus === "ready" && packages.length === 0 && <p className="ontology-inline-empty">当前没有可安装的参考业务包。</p>}
        {packageStatus === "ready" && packages.map((item) => (
          <div className="ontology-package-row" key={item.id}>
            <PackageOpen size={18} aria-hidden />
            <div><strong>{item.title}</strong><p>{item.description}</p><span>{item.conceptCount} 个业务对象 · {item.dataSourceCount} 个数据来源</span></div>
            <button type="button" className="cici-btn cici-btn--ghost" disabled={referencePackageInstallLocked || Boolean(busyAction)} onClick={() => void installPackage(item)}>安装参考包</button>
          </div>
        ))}
      </section>

      <aside className="ontology-list-assurance" aria-label="发布与 AI 权限说明">
        <Bot size={17} aria-hidden />
        <span><strong>AI 只参与草稿提案</strong>，校验、发布和线上版本替换始终由有权限的业务人员确认。</span>
      </aside>
    </div>
  );
}
