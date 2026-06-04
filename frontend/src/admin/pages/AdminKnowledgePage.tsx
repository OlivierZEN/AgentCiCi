import { useEffect, useState, useRef, useCallback } from "react";
import { useAdminToken } from "../useAdminToken";

type KnowledgeBase = {
  id: number;
  name: string;
  description: string;
  status: string;
  documentCount?: number;
  createdAt?: string;
  updatedAt?: string;
  chunkSize?: number;
  chunkOverlap?: number;
  chunkDelimiter?: string;
  retrievalStrategy?: string;
  topK?: number;
  scoreThreshold?: number;
  embeddingProvider?: string;
  embeddingModel?: string;
  embeddingDimension?: number;
};
type KbDocument = {
  id: number;
  name: string;
  contentType: string;
  status: string;
  createdAt: string;
  updatedAt?: string;
  indexedAt?: string;
  errorMessage?: string;
  fileSize?: number;
  enabled?: boolean;
  archived?: boolean;
  wordCount?: number;
  chunkCount?: number;
};

type ViewMode = "grid" | "detail";

type RetrievalHit = {
  vectorId: string;
  chunkId: number | null;
  documentId: number | null;
  chunkIndex: number | null;
  score: number;
  content: string;
  source: string;
};

type KbChunk = {
  id: number;
  documentId: number | null;
  chunkIndex: number | null;
  content: string;
  status: string;
  enabled: boolean;
};

type BatchFeedback = {
  tone: "success" | "warning";
  title: string;
  detail: string;
};

type UploadPolicy = {
  maxFileSizeBytes: number;
  maxFilesPerUpload: number;
  allowedExtensions: string[];
  allowedContentTypes?: string[];
  supportedParserLabels: string[];
  unsupportedParserLabels: string[];
  pdfPolicy: string;
};

type VectorAudit = {
  status: string;
  success: boolean;
  scannedCount: number;
  registeredCount: number;
  orphanCount: number;
  message?: string;
};

type KbDialog = {
  title: string;
  description?: string;
  confirmLabel: string;
  cancelLabel?: string;
  tone?: "default" | "danger";
  inputKind?: "text" | "textarea";
  inputLabel?: string;
  allowBlank?: boolean;
  value?: string;
  action: (value: string) => void | false | Promise<void | false>;
};

type EmbeddingModelOption = {
  providerCode: string;
  providerName: string;
  modelName: string;
  displayLabel: string;
  defaultDimension: number;
  dimensionChoices: number[];
  supportsCustomDimension: boolean;
};

const STATUS_LABEL: Record<string, string> = {
  UPLOADED: "待发布",
  INDEXING: "索引中",
  PUBLISHED: "可用",
  UNPUBLISHED: "已下线",
  DELETING: "清理中",
  DELETED: "已删除",
  CLEANUP_FAILED: "清理失败",
  FAILED: "失败",
};

const FILE_ICON: Record<string, string> = {
  "text/markdown": "📝",
  "text/plain": "📄",
  "text/csv": "📊",
  "application/json": "{}",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document": "W",
  default: "📎",
};

function fileIcon(ct: string) {
  return FILE_ICON[ct] || FILE_ICON.default;
}

function formatDate(iso?: string) {
  if (!iso) return "-";
  const d = new Date(iso);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

export default function AdminKnowledgePage() {
  const token = useAdminToken();
  const [notice, setNotice] = useState("");
  const [kbs, setKbs] = useState<KnowledgeBase[]>([]);
  const [docs, setDocs] = useState<KbDocument[]>([]);
  const [selectedKb, setSelectedKb] = useState<KnowledgeBase | null>(null);
  const [viewMode, setViewMode] = useState<ViewMode>("grid");
  const [search, setSearch] = useState("");
  const [docSearch, setDocSearch] = useState("");
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [kbName, setKbName] = useState("");
  const [kbDescription, setKbDescription] = useState("");
  const [editingKbId, setEditingKbId] = useState<number | null>(null);
  const [detailTab, setDetailTab] = useState<"documents" | "settings">("documents");
  const [chunkSize, setChunkSize] = useState(280);
  const [chunkOverlap, setChunkOverlap] = useState(40);
  const [chunkDelimiter, setChunkDelimiter] = useState("\\n");
  const [topK, setTopK] = useState(5);
  const [scoreThreshold, setScoreThreshold] = useState(0);
  const [embeddingProvider, setEmbeddingProvider] = useState("local");
  const [embeddingModel, setEmbeddingModel] = useState("local-hash");
  const [embeddingDimension, setEmbeddingDimension] = useState(1024);
  const [embeddingOptions, setEmbeddingOptions] = useState<EmbeddingModelOption[]>([]);
  const [previewText, setPreviewText] = useState("");
  const [previewChunks, setPreviewChunks] = useState<Array<{ index: number; length: number; content: string }>>([]);
  const [previewExecuted, setPreviewExecuted] = useState(false);
  const [retrievalQuery, setRetrievalQuery] = useState("");
  const [retrievalHits, setRetrievalHits] = useState<RetrievalHit[]>([]);
  const [retrievalExecuted, setRetrievalExecuted] = useState(false);
  const [retrievalLogs, setRetrievalLogs] = useState<Array<{ id: number; query: string; hitCount: number; createdAt: string }>>([]);
  const [retrievalMetadataFilterText, setRetrievalMetadataFilterText] = useState("");
  const [metadataFields, setMetadataFields] = useState<Array<{ id: number; fieldKey: string; fieldName: string; valueType: string }>>([]);
  const [newFieldKey, setNewFieldKey] = useState("");
  const [newFieldName, setNewFieldName] = useState("");
  const [chunkDocName, setChunkDocName] = useState("");
  const [chunkDocId, setChunkDocId] = useState<number | null>(null);
  const [chunkRows, setChunkRows] = useState<KbChunk[]>([]);
  const [selectedDocIds, setSelectedDocIds] = useState<Set<number>>(new Set());
  const [selectedChunkIds, setSelectedChunkIds] = useState<Set<number>>(new Set());
  const [docBatchFeedback, setDocBatchFeedback] = useState<BatchFeedback | null>(null);
  const [chunkBatchFeedback, setChunkBatchFeedback] = useState<BatchFeedback | null>(null);
  const [openDocActionMenuId, setOpenDocActionMenuId] = useState<number | null>(null);
  const [uploadPolicy, setUploadPolicy] = useState<UploadPolicy | null>(null);
  const [vectorAudit, setVectorAudit] = useState<VectorAudit | null>(null);
  const [kbDialog, setKbDialog] = useState<KbDialog | null>(null);
  const [dialogValue, setDialogValue] = useState("");
  const fileInputRef = useRef<HTMLInputElement>(null);

  const flash = (msg: string) => {
    setNotice(msg);
    setTimeout(() => setNotice(""), 3000);
  };

  const headers = useCallback(
    () => ({ Authorization: `Bearer ${token}` }),
    [token],
  );

  const openDialog = (dialog: KbDialog) => {
    setDialogValue(dialog.value ?? "");
    setKbDialog(dialog);
  };

  const closeDialog = () => {
    setKbDialog(null);
    setDialogValue("");
  };

  const confirmDialog = async () => {
    if (!kbDialog) return;
    const result = await kbDialog.action(kbDialog.inputKind ? dialogValue : "");
    if (result === false) return;
    closeDialog();
  };

  const listKnowledgeBases = useCallback(async () => {
    const res = await fetch("/kb", { headers: headers() });
    const json = await res.json();
    setKbs((json.data ?? []) as KnowledgeBase[]);
  }, [headers]);

  const listDocuments = useCallback(
    async (kbId: number) => {
      const res = await fetch(`/kb/${kbId}/documents`, { headers: headers() });
      const json = await res.json();
      setDocs((json.data ?? []) as KbDocument[]);
      setSelectedDocIds(new Set());
    },
    [headers],
  );

  const loadKbSettings = useCallback(
    async (kbId: number) => {
      const res = await fetch(`/kb/${kbId}/settings`, { headers: headers() });
      const json = await res.json();
      if (!json.success) return;
      const data = (json.data ?? {}) as Record<string, unknown>;
      setChunkSize(Number(data.chunkSize ?? 280));
      setChunkOverlap(Number(data.chunkOverlap ?? 40));
      const delimiter = String(data.chunkDelimiter ?? "\\n");
      setChunkDelimiter(delimiter === "\n" ? "\\n" : delimiter);
      setTopK(Number(data.topK ?? 5));
      setScoreThreshold(Number(data.scoreThreshold ?? 0));
      setEmbeddingProvider(String(data.embeddingProvider ?? "local"));
      setEmbeddingModel(String(data.embeddingModel ?? "local-hash"));
      setEmbeddingDimension(Number(data.embeddingDimension ?? 1024));
    },
    [headers],
  );

  const loadEmbeddingOptions = useCallback(async () => {
    const res = await fetch("/kb/embedding-models", { headers: headers() });
    const json = await res.json();
    if (!json.success) return;
    setEmbeddingOptions((json.data ?? []) as EmbeddingModelOption[]);
  }, [headers]);

  const loadUploadPolicy = useCallback(async () => {
    const res = await fetch("/kb/upload-policy", { headers: headers() });
    const json = await res.json();
    if (!json.success) return;
    setUploadPolicy(json.data as UploadPolicy);
  }, [headers]);

  const runVectorAudit = async () => {
    const res = await fetch("/kb/vector-store/audit", { headers: headers() });
    const json = await res.json();
    if (!json.success) {
      flash(`向量审计失败：${json.message}`);
      return;
    }
    const data = json.data as VectorAudit;
    setVectorAudit(data);
    flash(data.status === "OK" ? "向量审计通过" : `向量审计完成：${data.status}`);
  };

  const createOrUpdateKb = async () => {
    if (!kbName.trim()) return;
    const isEdit = editingKbId !== null;
    const url = isEdit ? `/kb/${editingKbId}` : "/kb";
    const method = isEdit ? "PUT" : "POST";
    const res = await fetch(url, {
      method,
      headers: { "Content-Type": "application/json", ...headers() },
      body: JSON.stringify({ name: kbName, description: kbDescription }),
    });
    const json = await res.json();
    if (json.success) {
      flash(isEdit ? "知识库已更新" : "知识库创建成功");
      setShowCreateModal(false);
      setKbName("");
      setKbDescription("");
      setEditingKbId(null);
      await listKnowledgeBases();
    } else {
      flash(`操作失败：${json.message}`);
    }
  };

  const saveKbSettings = async () => {
    if (!selectedKb) return;
    const res = await fetch(`/kb/${selectedKb.id}/settings`, {
      method: "PUT",
      headers: { "Content-Type": "application/json", ...headers() },
      body: JSON.stringify({
        chunkSize,
        chunkOverlap,
        chunkDelimiter,
        retrievalStrategy: "VECTOR",
        topK,
        scoreThreshold,
        embeddingProvider,
        embeddingModel,
        embeddingDimension,
      }),
    });
    const json = await res.json();
    if (!json.success) {
      flash(`保存设置失败：${json.message}`);
      return;
    }
    flash("知识库参数已保存");
    await listKnowledgeBases();
  };

  const runChunkPreview = async () => {
    if (!selectedKb) return;
    if (!previewText.trim()) {
      flash("请先输入预览文本");
      return;
    }
    const res = await fetch(`/kb/${selectedKb.id}/chunking/preview`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...headers() },
      body: JSON.stringify({
        text: previewText,
        chunkSize,
        chunkOverlap,
        chunkDelimiter,
        maxChunks: 10,
      }),
    });
    const json = await res.json();
    if (!json.success) {
      flash(`预览失败：${json.message}`);
      return;
    }
    setPreviewChunks((json.data?.previewChunks ?? []) as Array<{ index: number; length: number; content: string }>);
    setPreviewExecuted(true);
  };

  type MetadataFilterParseResult = {
    filters: Record<string, string>;
    invalidParts: string[];
  };

  const parseMetadataFilters = (text: string): MetadataFilterParseResult => {
    const filters: Record<string, string> = {};
    const invalidParts: string[] = [];
    const trimmed = text.trim();
    if (!trimmed) return { filters, invalidParts };
    trimmed.split(",").forEach((part) => {
      const rawPart = part.trim();
      if (!rawPart) return;
      const [k, ...v] = rawPart.split("=");
      const key = (k ?? "").trim();
      const value = v.join("=").trim();
      if (!key || !value) {
        invalidParts.push(rawPart);
        return;
      }
      filters[key] = value;
    });
    return { filters, invalidParts };
  };

  const runRetrievalTest = async () => {
    if (!selectedKb) return;
    if (!retrievalQuery.trim()) {
      flash("请先输入测试问题");
      return;
    }
    const parsedMetadata = parseMetadataFilters(retrievalMetadataFilterText);
    if (parsedMetadata.invalidParts.length > 0) {
      flash(`metadata 过滤格式错误：${parsedMetadata.invalidParts.join("，")}（格式：key=value）`);
      return;
    }
    const metadataKeys = Object.keys(parsedMetadata.filters);
    if (metadataKeys.length > 0) {
      const allowedKeys = new Set(metadataFields.map((field) => field.fieldKey));
      const unknownKeys = metadataKeys.filter((key) => !allowedKeys.has(key));
      if (unknownKeys.length > 0) {
        flash(
          `metadata 字段不存在：${unknownKeys.join("，")}。可用字段：${metadataFields.length === 0 ? "暂无（请先创建字段）" : metadataFields.map((field) => field.fieldKey).join("，")}`,
        );
        return;
      }
    }
    const res = await fetch(`/kb/${selectedKb.id}/retrieval/test`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...headers() },
      body: JSON.stringify({
        query: retrievalQuery,
        topK,
        scoreThreshold,
        retrievalStrategy: "VECTOR",
        metadataFilters: parsedMetadata.filters,
      }),
    });
    const json = await res.json();
    if (!json.success) {
      flash(`检索测试失败：${json.message}`);
      return;
    }
    setRetrievalHits((json.data?.hits ?? []) as RetrievalHit[]);
    setRetrievalExecuted(true);
    await loadRetrievalLogs(selectedKb.id);
  };

  const loadRetrievalLogs = useCallback(
    async (kbId: number) => {
      const res = await fetch(`/kb/${kbId}/retrieval/logs?limit=10`, { headers: headers() });
      const json = await res.json();
      if (!json.success) return;
      setRetrievalLogs((json.data ?? []) as Array<{ id: number; query: string; hitCount: number; createdAt: string }>);
    },
    [headers],
  );

  const loadMetadataFields = useCallback(
    async (kbId: number) => {
      const res = await fetch(`/kb/${kbId}/metadata/fields`, { headers: headers() });
      const json = await res.json();
      if (!json.success) return;
      setMetadataFields((json.data ?? []) as Array<{ id: number; fieldKey: string; fieldName: string; valueType: string }>);
    },
    [headers],
  );

  const createMetadataField = async () => {
    if (!selectedKb || !newFieldKey.trim()) return;
    const res = await fetch(`/kb/${selectedKb.id}/metadata/fields`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...headers() },
      body: JSON.stringify({
        fieldKey: newFieldKey.trim(),
        fieldName: newFieldName.trim() || newFieldKey.trim(),
        valueType: "string",
      }),
    });
    const json = await res.json();
    if (!json.success) {
      flash(`新增元数据字段失败：${json.message}`);
      return;
    }
    setNewFieldKey("");
    setNewFieldName("");
    await loadMetadataFields(selectedKb.id);
    flash("元数据字段已新增");
  };

  const toggleDocSelection = (id: number, checked: boolean) => {
    setSelectedDocIds((prev) => {
      const next = new Set(prev);
      if (checked) next.add(id);
      else next.delete(id);
      return next;
    });
  };

  const toggleChunkSelection = (id: number, checked: boolean) => {
    setSelectedChunkIds((prev) => {
      const next = new Set(prev);
      if (checked) next.add(id);
      else next.delete(id);
      return next;
    });
  };

  const buildBatchFeedback = (actionLabel: string, data: Record<string, unknown>): BatchFeedback => {
    const successCount = Number(data.successCount ?? 0);
    const failedCount = Number(data.failedCount ?? 0);
    if (failedCount <= 0) {
      return {
        tone: "success",
        title: `${actionLabel}完成`,
        detail: `成功 ${successCount} 条，失败 0 条。`,
      };
    }
    const failedItems = (data.failedItems ?? []) as Array<{ id: number; message: string }>;
    const sample = failedItems
      .slice(0, 3)
      .map((x) => `#${x.id} ${x.message}`)
      .join("；");
    return {
      tone: "warning",
      title: `${actionLabel}部分完成`,
      detail: `成功 ${successCount} 条，失败 ${failedCount} 条${sample ? `。失败样本：${sample}` : "。"}`,
    };
  };

  const deleteKnowledgeBase = async (id: number) => {
    const target = kbs.find((kb) => kb.id === id);
    openDialog({
      title: `删除知识库${target ? `「${target.name}」` : ""}`,
      description: "系统会同步清理文档、切片、向量索引和 Agent 绑定。该操作不可恢复。",
      confirmLabel: "删除知识库",
      tone: "danger",
      action: async () => {
        const res = await fetch(`/kb/${id}`, { method: "DELETE", headers: headers() });
        const json = await res.json();
        if (json.success) {
          flash("知识库已删除");
          if (selectedKb?.id === id) {
            setSelectedKb(null);
            setViewMode("grid");
            setDocs([]);
          }
          await listKnowledgeBases();
        } else {
          flash(`删除失败：${json.message}`);
        }
      },
    });
  };

  const uploadDocument = async (file: File) => {
    if (!selectedKb) return;
    const ext = file.name.split(".").pop()?.toLowerCase() ?? "";
    if (uploadPolicy) {
      if (file.size > uploadPolicy.maxFileSizeBytes) {
        flash(`上传失败：文件超过 ${Math.round(uploadPolicy.maxFileSizeBytes / 1024 / 1024)} MB 限制`);
        return;
      }
      if (ext === "pdf") {
        flash(uploadPolicy.pdfPolicy || "PDF 暂不支持索引，请先提取文本后上传。");
        return;
      }
      if (!uploadPolicy.allowedExtensions.includes(ext)) {
        flash(`上传失败：仅支持 ${uploadPolicy.allowedExtensions.join(" / ")}`);
        return;
      }
    }
    const form = new FormData();
    form.append("knowledgeBaseId", String(selectedKb.id));
    form.append("file", file);
    const res = await fetch("/kb/documents/upload", {
      method: "POST",
      headers: headers(),
      body: form,
    });
    const json = await res.json();
    if (!json.success) {
      flash(`上传失败：${json.message}`);
      return;
    }
    flash("文档上传成功");
    await listDocuments(selectedKb.id);
    const docId = json.data?.id;
    if (docId) {
      await publishDocument(docId);
    }
  };

  const publishDocument = async (id: number) => {
    if (!selectedKb) return;
    const res = await fetch(`/kb/documents/${id}/publish`, {
      method: "POST",
      headers: headers(),
    });
    const json = await res.json();
    if (json.success) {
      flash(json.data?.status === "PUBLISHED" ? "文档索引已更新" : "文档已提交索引");
      await listDocuments(selectedKb.id);
      await listKnowledgeBases();
    } else {
      flash(`发布失败：${json.message}`);
    }
  };

  const reindexDocument = async (id: number) => {
    if (!selectedKb) return;
    const res = await fetch(`/kb/documents/${id}/reindex`, {
      method: "POST",
      headers: headers(),
    });
    const json = await res.json();
    if (json.success) {
      flash(json.data?.status === "PUBLISHED" ? "文档已重建索引" : "文档已提交重建");
      await listDocuments(selectedKb.id);
      await listKnowledgeBases();
    } else {
      flash(`重建失败：${json.message}`);
    }
  };

  const unpublishDocument = async (id: number) => {
    if (!selectedKb) return;
    const res = await fetch(`/kb/documents/${id}/unpublish`, {
      method: "POST",
      headers: headers(),
    });
    const json = await res.json();
    if (json.success) {
      flash(json.data?.cleanupStatus === "COMPLETED" ? "文档已下线并清理索引" : "文档下线清理失败");
      await listDocuments(selectedKb.id);
      await listKnowledgeBases();
    } else {
      flash(`下线失败：${json.message}`);
    }
  };

  const renameDocument = async (doc: KbDocument) => {
    if (!selectedKb) return;
    openDialog({
      title: "重命名文档",
      inputKind: "text",
      inputLabel: "文档名称",
      value: doc.name,
      confirmLabel: "保存名称",
      action: async (name) => {
        if (!name.trim()) return false;
        const res = await fetch(`/kb/documents/${doc.id}/rename`, {
          method: "PUT",
          headers: { "Content-Type": "application/json", ...headers() },
          body: JSON.stringify({ name: name.trim() }),
        });
        const json = await res.json();
        if (!json.success) {
          flash(`重命名失败：${json.message}`);
          return false;
        }
        flash("文档已重命名");
        await listDocuments(selectedKb.id);
      },
    });
  };

  const setDocumentEnabled = async (doc: KbDocument, enabled: boolean) => {
    if (!selectedKb) return;
    const endpoint = enabled ? "enable" : "disable";
    const res = await fetch(`/kb/documents/${doc.id}/${endpoint}`, {
      method: "POST",
      headers: headers(),
    });
    const json = await res.json();
    if (!json.success) {
      flash(`${enabled ? "启用" : "停用"}失败：${json.message}`);
      return;
    }
    flash(enabled ? "文档已启用" : "文档已停用");
    await listDocuments(selectedKb.id);
  };

  const setDocumentArchived = async (doc: KbDocument, archived: boolean) => {
    if (!selectedKb) return;
    const endpoint = archived ? "archive" : "unarchive";
    const res = await fetch(`/kb/documents/${doc.id}/${endpoint}`, {
      method: "POST",
      headers: headers(),
    });
    const json = await res.json();
    if (!json.success) {
      flash(`${archived ? "归档" : "取消归档"}失败：${json.message}`);
      return;
    }
    flash(archived ? "文档已归档" : "文档已取消归档");
    await listDocuments(selectedKb.id);
  };

  const openChunkPanel = async (doc: KbDocument) => {
    const res = await fetch(`/kb/documents/${doc.id}/chunks`, { headers: headers() });
    const json = await res.json();
    if (!json.success) {
      flash(`加载切片失败：${json.message}`);
      return;
    }
    setChunkDocName(doc.name);
    setChunkDocId(doc.id);
    setChunkRows((json.data ?? []) as KbChunk[]);
    setSelectedChunkIds(new Set());
  };

  const closeChunkPanel = () => {
    setChunkDocId(null);
    setChunkDocName("");
    setChunkRows([]);
    setSelectedChunkIds(new Set());
    setChunkBatchFeedback(null);
  };

  const refreshChunkPanel = async () => {
    if (!chunkDocId) return;
    const res = await fetch(`/kb/documents/${chunkDocId}/chunks`, { headers: headers() });
    const json = await res.json();
    if (!json.success) return;
    setChunkRows((json.data ?? []) as KbChunk[]);
    setSelectedChunkIds(new Set());
  };

  const toggleChunkEnabled = async (chunk: KbChunk, enabled: boolean) => {
    const endpoint = enabled ? "enable" : "disable";
    const res = await fetch(`/kb/chunks/${chunk.id}/${endpoint}`, {
      method: "POST",
      headers: headers(),
    });
    const json = await res.json();
    if (!json.success) {
      flash(`${enabled ? "启用" : "停用"}切片失败：${json.message}`);
      return;
    }
    await refreshChunkPanel();
  };

  const editChunkContent = async (chunk: KbChunk) => {
    openDialog({
      title: "编辑切片内容",
      description: "保存后会重新生成向量索引。",
      inputKind: "textarea",
      inputLabel: "切片内容",
      value: chunk.content,
      confirmLabel: "保存切片",
      action: async (content) => {
        if (!content.trim()) return false;
        const res = await fetch(`/kb/chunks/${chunk.id}`, {
          method: "PUT",
          headers: { "Content-Type": "application/json", ...headers() },
          body: JSON.stringify({ content: content.trim() }),
        });
        const json = await res.json();
        if (!json.success) {
          flash(`更新切片失败：${json.message}`);
          return false;
        }
        await refreshChunkPanel();
      },
    });
  };

  const deleteChunk = async (chunk: KbChunk) => {
    openDialog({
      title: "删除切片",
      description: "系统会同步删除该切片对应的向量索引。",
      confirmLabel: "删除切片",
      tone: "danger",
      action: async () => {
        const res = await fetch(`/kb/chunks/${chunk.id}`, {
          method: "DELETE",
          headers: headers(),
        });
        const json = await res.json();
        if (!json.success) {
          flash(`删除切片失败：${json.message}`);
          return;
        }
        await refreshChunkPanel();
      },
    });
  };

  const updateDocumentMetadata = async (doc: KbDocument) => {
    if (!selectedKb) return;
    const existingRes = await fetch(`/kb/documents/${doc.id}/metadata`, { headers: headers() });
    const existingJson = await existingRes.json();
    const existing = (existingJson.data ?? []) as Array<{ fieldKey: string; value: string }>;
    const defaults = existing.map((x) => `${x.fieldKey}=${x.value}`).join(", ");
    openDialog({
      title: "编辑文档 Metadata",
      description: `格式：key=value,key2=value2。可用字段：${metadataFields.length === 0 ? "暂无" : metadataFields.map((field) => field.fieldKey).join("，")}`,
      inputKind: "textarea",
      inputLabel: "Metadata",
      allowBlank: true,
      value: defaults,
      confirmLabel: "保存 Metadata",
      action: async (input) => {
        const parsed = parseMetadataFilters(input);
        if (parsed.invalidParts.length > 0) {
          flash(`元数据格式错误：${parsed.invalidParts.join("，")}（格式：key=value）`);
          return false;
        }
        const metadataKeys = Object.keys(parsed.filters);
        if (metadataKeys.length > 0) {
          const allowedKeys = new Set(metadataFields.map((field) => field.fieldKey));
          const unknownKeys = metadataKeys.filter((key) => !allowedKeys.has(key));
          if (unknownKeys.length > 0) {
            flash(`存在未定义字段：${unknownKeys.join("，")}。请先在「Metadata 字段」中创建后再保存。`);
            return false;
          }
        }
        const res = await fetch(`/kb/documents/${doc.id}/metadata`, {
          method: "PUT",
          headers: { "Content-Type": "application/json", ...headers() },
          body: JSON.stringify(parsed.filters),
        });
        const json = await res.json();
        if (!json.success) {
          flash(`保存元数据失败：${json.message}`);
          return false;
        }
        flash("文档元数据已更新");
      },
    });
  };

  const runDocumentBatchAction = async (
    action: "enable" | "disable" | "archive" | "unarchive" | "delete",
    label: string,
  ) => {
    if (!selectedKb) return;
    const ids = Array.from(selectedDocIds);
    if (ids.length === 0) {
      flash("请先选择文档");
      return;
    }
    if (action === "delete") {
      openDialog({
        title: `批量删除 ${ids.length} 个文档`,
        description: "系统会同步删除这些文档对应的切片和向量索引。",
        confirmLabel: "批量删除",
        tone: "danger",
        action: async () => runDocumentBatchRequest(action, label, ids),
      });
      return;
    }
    await runDocumentBatchRequest(action, label, ids);
  };

  const runDocumentBatchRequest = async (
    action: "enable" | "disable" | "archive" | "unarchive" | "delete",
    label: string,
    ids: number[],
  ) => {
    if (!selectedKb) return;
    const res = await fetch(`/kb/documents/batch/${action}`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...headers() },
      body: JSON.stringify({ ids }),
    });
    const json = await res.json();
    if (!json.success) {
      flash(`${label}失败：${json.message}`);
      setDocBatchFeedback({
        tone: "warning",
        title: `${label}失败`,
        detail: String(json.message ?? "请求失败"),
      });
      return;
    }
    const feedback = buildBatchFeedback(label, (json.data ?? {}) as Record<string, unknown>);
    setDocBatchFeedback(feedback);
    flash(feedback.detail);
    await listDocuments(selectedKb.id);
    await listKnowledgeBases();
  };

  const runChunkBatchAction = async (
    action: "enable" | "disable" | "delete",
    label: string,
  ) => {
    const ids = Array.from(selectedChunkIds);
    if (ids.length === 0) {
      flash("请先选择切片");
      return;
    }
    if (action === "delete") {
      openDialog({
        title: `批量删除 ${ids.length} 个切片`,
        description: "系统会同步删除这些切片对应的向量索引。",
        confirmLabel: "批量删除",
        tone: "danger",
        action: async () => runChunkBatchRequest(action, label, ids),
      });
      return;
    }
    await runChunkBatchRequest(action, label, ids);
  };

  const runChunkBatchRequest = async (
    action: "enable" | "disable" | "delete",
    label: string,
    ids: number[],
  ) => {
    const res = await fetch(`/kb/chunks/batch/${action}`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...headers() },
      body: JSON.stringify({ ids }),
    });
    const json = await res.json();
    if (!json.success) {
      flash(`${label}失败：${json.message}`);
      setChunkBatchFeedback({
        tone: "warning",
        title: `${label}失败`,
        detail: String(json.message ?? "请求失败"),
      });
      return;
    }
    const feedback = buildBatchFeedback(label, (json.data ?? {}) as Record<string, unknown>);
    setChunkBatchFeedback(feedback);
    flash(feedback.detail);
    await refreshChunkPanel();
    if (selectedKb) {
      await listDocuments(selectedKb.id);
    }
  };

  const deleteDocument = async (id: number) => {
    if (!selectedKb) return;
    const target = docs.find((doc) => doc.id === id);
    openDialog({
      title: `删除文档${target ? `「${target.name}」` : ""}`,
      description: "系统会同步删除对应切片和向量索引。该操作不可恢复。",
      confirmLabel: "删除文档",
      tone: "danger",
      action: async () => {
        const res = await fetch(`/kb/documents/${id}`, {
          method: "DELETE",
          headers: headers(),
        });
        const json = await res.json();
        if (json.success) {
          flash(json.data?.cleanupStatus === "COMPLETED" ? "文档已删除并清理索引" : "文档删除清理失败");
          await listDocuments(selectedKb.id);
          await listKnowledgeBases();
        } else {
          flash(`删除失败：${json.message}`);
        }
      },
    });
  };

  const toggleDocStatus = async (doc: KbDocument) => {
    if (doc.status === "PUBLISHED") {
      await unpublishDocument(doc.id);
    } else if (doc.status === "UPLOADED" || doc.status === "FAILED" || doc.status === "UNPUBLISHED") {
      await publishDocument(doc.id);
    }
  };

  const openKbDetail = (kb: KnowledgeBase) => {
    setSelectedKb(kb);
    setViewMode("detail");
    setDetailTab("documents");
    setDocSearch("");
    setPreviewChunks([]);
    setPreviewExecuted(false);
    setRetrievalHits([]);
    setRetrievalExecuted(false);
    setDocBatchFeedback(null);
    setChunkBatchFeedback(null);
    void listDocuments(kb.id);
    void loadKbSettings(kb.id);
    void loadEmbeddingOptions();
    void loadUploadPolicy();
    void loadRetrievalLogs(kb.id);
    void loadMetadataFields(kb.id);
    setVectorAudit(null);
  };

  useEffect(() => {
    void listKnowledgeBases();
    void loadEmbeddingOptions();
    void loadUploadPolicy();
  }, [listKnowledgeBases, loadEmbeddingOptions, loadUploadPolicy]);

  useEffect(() => {
    if (!selectedKb) return;
    const hasIndexing = docs.some((d) => d.status === "INDEXING" || d.status === "DELETING");
    if (!hasIndexing) return;
    const t = window.setInterval(() => void listDocuments(selectedKb.id), 2000);
    return () => window.clearInterval(t);
  }, [selectedKb, docs, listDocuments]);

  useEffect(() => {
    if (!selectedKb || detailTab !== "settings") return;
    void loadKbSettings(selectedKb.id);
    void loadEmbeddingOptions();
    void loadRetrievalLogs(selectedKb.id);
    void loadMetadataFields(selectedKb.id);
  }, [selectedKb, detailTab, loadKbSettings, loadEmbeddingOptions, loadRetrievalLogs, loadMetadataFields]);

  useEffect(() => {
    const closeOnOutsideClick = (event: MouseEvent) => {
      const target = event.target;
      if (target instanceof Element && target.closest("[data-admin-row-menu]")) return;
      setOpenDocActionMenuId(null);
    };
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpenDocActionMenuId(null);
    };
    document.addEventListener("mousedown", closeOnOutsideClick);
    document.addEventListener("keydown", closeOnEscape);
    return () => {
      document.removeEventListener("mousedown", closeOnOutsideClick);
      document.removeEventListener("keydown", closeOnEscape);
    };
  }, []);

  useEffect(() => {
    if (!kbDialog) return;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") closeDialog();
    };
    document.addEventListener("keydown", closeOnEscape);
    return () => document.removeEventListener("keydown", closeOnEscape);
  }, [kbDialog]);

  useEffect(() => {
    setPreviewExecuted(false);
  }, [previewText, chunkSize, chunkOverlap, chunkDelimiter, selectedKb?.id]);

  useEffect(() => {
    setRetrievalExecuted(false);
  }, [retrievalQuery, retrievalMetadataFilterText, topK, scoreThreshold, selectedKb?.id]);

  const filteredKbs = kbs.filter(
    (kb) =>
      kb.name.toLowerCase().includes(search.toLowerCase()) ||
      (kb.description ?? "").toLowerCase().includes(search.toLowerCase()),
  );

  const filteredDocs = docs.filter((d) =>
    d.name.toLowerCase().includes(docSearch.toLowerCase()),
  );
  const selectedDocCount = selectedDocIds.size;
  const selectedChunkCount = selectedChunkIds.size;
  const selectedEmbeddingValue = `${embeddingProvider}:::${embeddingModel}`;
  const selectedEmbeddingOption = embeddingOptions.find(
    (option) => option.providerCode === embeddingProvider && option.modelName === embeddingModel,
  );
  const visibleEmbeddingOptions = selectedEmbeddingOption || embeddingProvider === "local"
    ? embeddingOptions
    : [
        {
          providerCode: embeddingProvider,
          providerName: embeddingProvider,
          modelName: embeddingModel,
          displayLabel: `${embeddingModel} · ${embeddingProvider}`,
          defaultDimension: embeddingDimension,
          dimensionChoices: [embeddingDimension],
          supportsCustomDimension: false,
        },
        ...embeddingOptions,
      ];
  const embeddingDimensionChoices = selectedEmbeddingOption?.dimensionChoices?.length
    ? selectedEmbeddingOption.dimensionChoices
    : [embeddingDimension];
  const uploadAccept = uploadPolicy?.allowedExtensions?.length
    ? uploadPolicy.allowedExtensions.map((ext) => `.${ext}`).join(",")
    : ".txt,.md,.markdown,.csv,.json,.docx";
  const uploadLimitLabel = uploadPolicy
    ? `${Math.round(uploadPolicy.maxFileSizeBytes / 1024 / 1024)} MB`
    : "25 MB";
  const kbActionDialog = kbDialog ? (
    <div className="cici-modal-overlay" onClick={closeDialog}>
      <div
        className={`cici-modal cici-modal--kb-action ${kbDialog.tone === "danger" ? "cici-modal--danger" : ""}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby="kb-action-dialog-title"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="cici-modal__head">
          <h2 className="cici-modal__title" id="kb-action-dialog-title">
            {kbDialog.title}
          </h2>
          <button type="button" className="cici-modal__close" aria-label="关闭" onClick={closeDialog}>
            ×
          </button>
        </div>
        <div className="cici-modal__body">
          {kbDialog.description && <p className="cici-modal__description">{kbDialog.description}</p>}
          {kbDialog.inputKind && (
            <label className="cici-field">
              {kbDialog.inputLabel && <span className="cici-field__label">{kbDialog.inputLabel}</span>}
              {kbDialog.inputKind === "textarea" ? (
                <textarea
                  className="cici-field__textarea"
                  rows={5}
                  value={dialogValue}
                  onChange={(e) => setDialogValue(e.target.value)}
                  autoFocus
                />
              ) : (
                <input
                  className="cici-field__input"
                  value={dialogValue}
                  onChange={(e) => setDialogValue(e.target.value)}
                  autoFocus
                />
              )}
            </label>
          )}
        </div>
        <div className="cici-modal__actions">
          <button type="button" className="cici-btn cici-btn--ghost" onClick={closeDialog}>
            {kbDialog.cancelLabel ?? "取消"}
          </button>
          <button
            type="button"
            className={`cici-btn ${kbDialog.tone === "danger" ? "cici-btn--danger" : "cici-btn--primary"}`}
            disabled={Boolean(kbDialog.inputKind && !kbDialog.allowBlank && !dialogValue.trim())}
            onClick={() => void confirmDialog()}
          >
            {kbDialog.confirmLabel}
          </button>
        </div>
      </div>
    </div>
  ) : null;

  /* ─── KB Grid View ─── */
  if (viewMode === "grid") {
    return (
      <div className="cici-kb-page">
        {notice && <div className="cici-toast">{notice}</div>}

        {/* top bar */}
        <div className="cici-kb-topbar">
          <div className="cici-kb-topbar__left">
            <h1 className="cici-kb-topbar__title">知识库</h1>
          </div>
          <div className="cici-kb-topbar__right">
            <div className="cici-search">
              <svg className="cici-search__icon" viewBox="0 0 20 20" fill="none">
                <circle cx="9" cy="9" r="6" stroke="currentColor" strokeWidth="1.6" />
                <path d="M13.5 13.5 17 17" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
              </svg>
              <input
                className="cici-search__input"
                placeholder="搜索知识库..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>
          </div>
        </div>

        {/* card grid */}
        <div className="cici-kb-grid">
          {/* create card */}
          <button
            type="button"
            className="cici-kb-card cici-kb-card--create"
            onClick={() => {
              setEditingKbId(null);
              setKbName("");
              setKbDescription("");
              setShowCreateModal(true);
            }}
          >
            <span className="cici-kb-card__plus">+</span>
            <span className="cici-kb-card__create-label">创建知识库</span>
          </button>

          {filteredKbs.map((kb) => (
            <div
              key={kb.id}
              className="cici-kb-card"
              onClick={() => openKbDetail(kb)}
            >
              <div className="cici-kb-card__header">
                <div className="cici-kb-card__icon">
                  <svg viewBox="0 0 24 24" fill="none" width="28" height="28">
                    <rect x="3" y="3" width="18" height="18" rx="4" stroke="currentColor" strokeWidth="1.5" />
                    <path d="M7 8h10M7 12h6M7 16h8" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
                  </svg>
                </div>
                <button
                  type="button"
                  className="cici-kb-card__menu"
                  onClick={(e) => {
                    e.stopPropagation();
                    void deleteKnowledgeBase(kb.id);
                  }}
                  title="删除"
                >
                  ···
                </button>
              </div>
              <h3 className="cici-kb-card__name">{kb.name}</h3>
              <p className="cici-kb-card__desc">
                {kb.description || "暂无描述"}
              </p>
              <div className="cici-kb-card__meta">
                <span className="cici-kb-card__tag">通用</span>
                <span className="cici-kb-card__tag">{kb.embeddingModel ?? "向量检索"}</span>
              </div>
              <div className="cici-kb-card__footer">
                <span className="cici-kb-card__stat">
                  <svg viewBox="0 0 16 16" width="14" height="14" fill="none">
                    <path d="M3 3h10v10H3z" stroke="currentColor" strokeWidth="1.2" rx="1.5" />
                    <path d="M5.5 6.5h5M5.5 9h3" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
                  </svg>
                  {kb.documentCount ?? docs.length ?? 0}
                </span>
                <span className="cici-kb-card__time">
                  {kb.updatedAt ? formatDate(kb.updatedAt) : kb.createdAt ? formatDate(kb.createdAt) : ""}
                </span>
              </div>
            </div>
          ))}
        </div>

        {/* create/edit modal */}
        {showCreateModal && (
          <div className="cici-modal-overlay" onClick={() => setShowCreateModal(false)}>
            <div className="cici-modal" onClick={(e) => e.stopPropagation()}>
              <h2 className="cici-modal__title">
                {editingKbId ? "编辑知识库" : "创建知识库"}
              </h2>
              <div className="cici-modal__body">
              <label className="cici-field">
                <span className="cici-field__label">知识库名称</span>
                <input
                  className="cici-field__input"
                  value={kbName}
                  onChange={(e) => setKbName(e.target.value)}
                  placeholder="输入知识库名称"
                  autoFocus
                />
              </label>
              <label className="cici-field">
                <span className="cici-field__label">描述</span>
                <textarea
                  className="cici-field__textarea"
                  value={kbDescription}
                  onChange={(e) => setKbDescription(e.target.value)}
                  placeholder="描述知识库的用途和内容范围"
                  rows={3}
                />
              </label>
              </div>
              <div className="cici-modal__actions">
                <button
                  type="button"
                  className="cici-btn cici-btn--ghost"
                  onClick={() => setShowCreateModal(false)}
                >
                  取消
                </button>
                <button
                  type="button"
                  className="cici-btn cici-btn--primary"
                  onClick={createOrUpdateKb}
                >
                  {editingKbId ? "保存" : "创建"}
                </button>
              </div>
            </div>
          </div>
        )}
        {kbActionDialog}
      </div>
    );
  }

  /* ─── KB Detail View ─── */
  return (
    <div className="cici-kb-page">
      {notice && <div className="cici-toast">{notice}</div>}

      <div className="cici-kb-detail">
        {/* sidebar */}
        <aside className="cici-kb-sidebar">
          <div className="cici-kb-sidebar__head">
            <div className="cici-kb-sidebar__icon">
              <svg viewBox="0 0 24 24" fill="none" width="32" height="32">
                <rect x="3" y="3" width="18" height="18" rx="4" stroke="currentColor" strokeWidth="1.5" />
                <path d="M7 8h10M7 12h6M7 16h8" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
              </svg>
            </div>
            <h2 className="cici-kb-sidebar__name">{selectedKb?.name}</h2>
            <p className="cici-kb-sidebar__desc">
              {selectedKb?.description || "暂无描述"}
            </p>
          </div>
          <nav className="cici-kb-sidebar__nav">
            <button
              type="button"
              className={`cici-kb-sidebar__link ${detailTab === "documents" ? "active" : ""}`}
              onClick={() => setDetailTab("documents")}
            >
              <svg viewBox="0 0 20 20" width="18" height="18" fill="none">
                <rect x="3" y="2" width="14" height="16" rx="2" stroke="currentColor" strokeWidth="1.4" />
                <path d="M6 6h8M6 9.5h5M6 13h6" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" />
              </svg>
              文档
            </button>
            <button
              type="button"
              className={`cici-kb-sidebar__link ${detailTab === "settings" ? "active" : ""}`}
              onClick={() => setDetailTab("settings")}
            >
              <svg viewBox="0 0 20 20" width="18" height="18" fill="none">
                <circle cx="10" cy="10" r="2.5" stroke="currentColor" strokeWidth="1.4" />
                <path d="M10 2v2.5M10 15.5V18M2 10h2.5M15.5 10H18M4.2 4.2l1.8 1.8M14 14l1.8 1.8M15.8 4.2 14 6M6 14l-1.8 1.8" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
              </svg>
              设置
            </button>
          </nav>
          <div className="cici-kb-sidebar__footer">
            <button
              type="button"
              className="cici-btn cici-btn--ghost cici-btn--sm cici-btn--full"
              onClick={() => {
                setViewMode("grid");
                setSelectedKb(null);
                setDocs([]);
              }}
            >
              ← 返回知识库列表
            </button>
          </div>
        </aside>

        {/* main */}
        <main className="cici-kb-main">
          {detailTab === "documents" && (
            <>
              <div className="cici-kb-main__header">
                <div>
                  <h2 className="cici-kb-main__title">文档</h2>
                  <p className="cici-kb-main__subtitle">
                    知识库的所有文件都在这里显示，上传后自动发布索引。
                  </p>
                </div>
                <div className="cici-kb-main__actions">
                  <div className="cici-search cici-search--sm">
                    <svg className="cici-search__icon" viewBox="0 0 20 20" fill="none">
                      <circle cx="9" cy="9" r="6" stroke="currentColor" strokeWidth="1.6" />
                      <path d="M13.5 13.5 17 17" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
                    </svg>
                    <input
                      className="cici-search__input"
                      placeholder="搜索文档..."
                      value={docSearch}
                      onChange={(e) => setDocSearch(e.target.value)}
                    />
                  </div>
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept={uploadAccept}
                    hidden
                    onChange={(e) => {
                      const f = e.target.files?.[0];
                      if (f) void uploadDocument(f);
                      e.target.value = "";
                    }}
                  />
                  <button
                    type="button"
                    className="cici-btn cici-btn--primary"
                    onClick={() => fileInputRef.current?.click()}
                  >
                    + 添加文件
                  </button>
                </div>
              </div>
              <div className="cici-kb-upload-policy">
                <span>支持 {uploadPolicy?.supportedParserLabels?.join(" / ") ?? "TXT / Markdown / CSV / JSON / DOCX"}</span>
                <span>单文件上限 {uploadLimitLabel}</span>
                <span>{uploadPolicy?.pdfPolicy ?? "PDF 暂不支持索引，请先提取文本后上传。"}</span>
              </div>

              <div className="cici-kb-settings__actions cici-kb-settings__actions--batch">
                <span className="subtle">已选 {selectedDocCount} 项</span>
                <button
                  type="button"
                  className="cici-btn cici-btn--ghost cici-btn--sm"
                  disabled={selectedDocCount === 0}
                  onClick={() => setSelectedDocIds(new Set())}
                >
                  清空选择
                </button>
                <button type="button" className="cici-btn cici-btn--ghost cici-btn--sm" disabled={selectedDocCount === 0} onClick={() => void runDocumentBatchAction("enable", "批量启用文档")}>
                  批量启用
                </button>
                <button type="button" className="cici-btn cici-btn--ghost cici-btn--sm" disabled={selectedDocCount === 0} onClick={() => void runDocumentBatchAction("disable", "批量停用文档")}>
                  批量停用
                </button>
                <button type="button" className="cici-btn cici-btn--ghost cici-btn--sm" disabled={selectedDocCount === 0} onClick={() => void runDocumentBatchAction("archive", "批量归档文档")}>
                  批量归档
                </button>
                <button type="button" className="cici-btn cici-btn--ghost cici-btn--sm" disabled={selectedDocCount === 0} onClick={() => void runDocumentBatchAction("unarchive", "批量取消归档文档")}>
                  批量取消归档
                </button>
                <button type="button" className="cici-btn cici-btn--danger cici-btn--sm" disabled={selectedDocCount === 0} onClick={() => void runDocumentBatchAction("delete", "批量删除文档")}>
                  批量删除
                </button>
              </div>
              {docBatchFeedback && (
                <div className={`cici-inline-feedback cici-inline-feedback--${docBatchFeedback.tone}`}>
                  <div className="cici-inline-feedback__main">
                    <strong>{docBatchFeedback.title}</strong>
                    <span>{docBatchFeedback.detail}</span>
                  </div>
                  <button
                    type="button"
                    className="cici-btn cici-btn--text cici-btn--xs"
                    onClick={() => setDocBatchFeedback(null)}
                  >
                    关闭
                  </button>
                </div>
              )}

              {/* document table */}
              <div className="cici-doc-table-wrap">
                <table className="cici-doc-table">
                  <thead>
                    <tr>
                      <th>
                        <input
                          type="checkbox"
                          checked={filteredDocs.length > 0 && filteredDocs.every((doc) => selectedDocIds.has(doc.id))}
                          onChange={(e) => {
                            if (e.target.checked) {
                              setSelectedDocIds(new Set(filteredDocs.map((doc) => doc.id)));
                            } else {
                              setSelectedDocIds(new Set());
                            }
                          }}
                        />
                      </th>
                      <th className="cici-doc-table__th--num">#</th>
                      <th>名称</th>
                      <th>类型</th>
                      <th>切片</th>
                      <th>上传时间</th>
                      <th>状态</th>
                      <th className="cici-doc-table__th--actions">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredDocs.length === 0 && (
                      <tr>
                        <td colSpan={8} className="cici-doc-table__empty">
                          暂无文档，点击「+ 添加文件」上传
                        </td>
                      </tr>
                    )}
                    {filteredDocs.map((doc, i) => (
                      <tr key={doc.id} className={openDocActionMenuId === doc.id ? "is-action-menu-open" : ""}>
                        <td>
                          <input
                            type="checkbox"
                            checked={selectedDocIds.has(doc.id)}
                            onChange={(e) => toggleDocSelection(doc.id, e.target.checked)}
                          />
                        </td>
                        <td className="cici-doc-table__num">{i + 1}</td>
                        <td>
                          <div className="cici-doc-name">
                            <span className="cici-doc-name__icon">{fileIcon(doc.contentType)}</span>
                            <span className="cici-doc-name__text">{doc.name}</span>
                          </div>
                        </td>
                        <td className="cici-doc-table__type">{doc.contentType?.split("/")[1] ?? "-"}</td>
                        <td className="cici-doc-table__type">{doc.chunkCount ?? 0}</td>
                        <td className="cici-doc-table__time">{formatDate(doc.createdAt)}</td>
                        <td>
                          <div className="cici-doc-status">
                            <button
                              type="button"
                              className={`cici-toggle ${doc.status === "PUBLISHED" ? "cici-toggle--on" : ""} ${doc.status === "INDEXING" || doc.status === "DELETING" ? "cici-toggle--loading" : ""}`}
                              onClick={() => void toggleDocStatus(doc)}
                              disabled={doc.status === "INDEXING" || doc.status === "DELETING" || doc.status === "CLEANUP_FAILED"}
                            />
                            <span
                              className={`cici-doc-badge cici-doc-badge--${doc.status.toLowerCase()}`}
                            >
                              {(doc.status === "INDEXING" || doc.status === "DELETING") && (
                                <span className="cici-spinner" />
                              )}
                              {STATUS_LABEL[doc.status] ?? doc.status}
                            </span>
                          </div>
                          {(doc.status === "FAILED" || doc.status === "CLEANUP_FAILED") && doc.errorMessage && (
                            <div className="cici-doc-status__error">{doc.errorMessage}</div>
                          )}
                        </td>
                        <td className="cici-doc-table__actions">
                          <div className={`admin-row-menu${openDocActionMenuId === doc.id ? " is-open" : ""}`} data-admin-row-menu>
                            <button
                              type="button"
                              className="admin-row-menu__trigger"
                              aria-haspopup="menu"
                              aria-expanded={openDocActionMenuId === doc.id}
                              aria-label={`打开「${doc.name}」操作菜单`}
                              onClick={() => setOpenDocActionMenuId((current) => (current === doc.id ? null : doc.id))}
                            >
                              <span aria-hidden="true">•••</span>
                            </button>
                            {openDocActionMenuId === doc.id ? (
                              <div className="admin-row-menu__panel" role="menu" aria-label={`${doc.name}操作`}>
                                {(doc.status === "UPLOADED" || doc.status === "FAILED" || doc.status === "UNPUBLISHED") && (
                                  <button
                                    type="button"
                                    className="admin-row-menu__item"
                                    role="menuitem"
                                    onClick={() => {
                                      setOpenDocActionMenuId(null);
                                      void publishDocument(doc.id);
                                    }}
                                  >
                                    {doc.status === "FAILED" ? "重试" : "发布"}
                                  </button>
                                )}
                                {doc.status === "PUBLISHED" && (
                                  <>
                                    <button
                                      type="button"
                                      className="admin-row-menu__item"
                                      role="menuitem"
                                      onClick={() => {
                                        setOpenDocActionMenuId(null);
                                        void reindexDocument(doc.id);
                                      }}
                                    >
                                      重建
                                    </button>
                                    <button
                                      type="button"
                                      className="admin-row-menu__item"
                                      role="menuitem"
                                      onClick={() => {
                                        setOpenDocActionMenuId(null);
                                        void unpublishDocument(doc.id);
                                      }}
                                    >
                                      下线
                                    </button>
                                  </>
                                )}
                                <button
                                  type="button"
                                  className="admin-row-menu__item"
                                  role="menuitem"
                                  onClick={() => {
                                    setOpenDocActionMenuId(null);
                                    void renameDocument(doc);
                                  }}
                                >
                                  重命名
                                </button>
                                <button
                                  type="button"
                                  className="admin-row-menu__item"
                                  role="menuitem"
                                  onClick={() => {
                                    setOpenDocActionMenuId(null);
                                    void setDocumentEnabled(doc, !(doc.enabled ?? true));
                                  }}
                                >
                                  {doc.enabled === false ? "启用文档" : "停用文档"}
                                </button>
                                <button
                                  type="button"
                                  className="admin-row-menu__item"
                                  role="menuitem"
                                  onClick={() => {
                                    setOpenDocActionMenuId(null);
                                    void setDocumentArchived(doc, !(doc.archived ?? false));
                                  }}
                                >
                                  {doc.archived ? "取消归档" : "归档"}
                                </button>
                                <button
                                  type="button"
                                  className="admin-row-menu__item"
                                  role="menuitem"
                                  onClick={() => {
                                    setOpenDocActionMenuId(null);
                                    void openChunkPanel(doc);
                                  }}
                                >
                                  切片
                                </button>
                                <button
                                  type="button"
                                  className="admin-row-menu__item"
                                  role="menuitem"
                                  onClick={() => {
                                    setOpenDocActionMenuId(null);
                                    void updateDocumentMetadata(doc);
                                  }}
                                >
                                  元数据
                                </button>
                                <button
                                  type="button"
                                  className="admin-row-menu__item admin-row-menu__item--danger"
                                  role="menuitem"
                                  onClick={() => {
                                    setOpenDocActionMenuId(null);
                                    void deleteDocument(doc.id);
                                  }}
                                >
                                  删除
                                </button>
                              </div>
                            ) : null}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <div className="cici-kb-main__footer">
                <span>{filteredDocs.length} 文档</span>
              </div>
            </>
          )}

          {detailTab === "settings" && selectedKb && (
            <div className="cici-kb-settings">
              <h2 className="cici-kb-main__title">知识库设置</h2>
              <label className="cici-field">
                <span className="cici-field__label">名称</span>
                <input
                  className="cici-field__input"
                  defaultValue={selectedKb.name}
                  onBlur={(e) => setKbName(e.target.value)}
                  onFocus={(e) => {
                    setKbName(e.target.value);
                    setEditingKbId(selectedKb.id);
                  }}
                />
              </label>
              <label className="cici-field">
                <span className="cici-field__label">描述</span>
                <textarea
                  className="cici-field__textarea"
                  defaultValue={selectedKb.description}
                  rows={3}
                  onBlur={(e) => setKbDescription(e.target.value)}
                  onFocus={(e) => {
                    setKbDescription(e.target.value);
                    setEditingKbId(selectedKb.id);
                  }}
                />
              </label>
              <div className="cici-kb-settings__actions">
                <button
                  type="button"
                  className="cici-btn cici-btn--primary"
                  onClick={async () => {
                    setEditingKbId(selectedKb.id);
                    await createOrUpdateKb();
                    await listKnowledgeBases();
                    const updated = kbs.find((k) => k.id === selectedKb.id);
                    if (updated) setSelectedKb({ ...updated, name: kbName || updated.name, description: kbDescription || updated.description });
                  }}
                >
                  保存设置
                </button>
                <button
                  type="button"
                  className="cici-btn cici-btn--danger"
                  onClick={() => void deleteKnowledgeBase(selectedKb.id)}
                >
                  删除知识库
                </button>
              </div>

              <h3 className="cici-kb-main__title cici-kb-main__title--section">运行状态</h3>
              <div className="cici-kb-ops-panel">
                <div className="cici-kb-ops-panel__item">
                  <span className="cici-kb-ops-panel__label">上传策略</span>
                  <strong>{uploadPolicy?.supportedParserLabels?.join(" / ") ?? "TXT / Markdown / CSV / JSON / DOCX"}</strong>
                  <span className="subtle">单文件 {uploadLimitLabel}，PDF 不进入索引流水线。</span>
                </div>
                <div className="cici-kb-ops-panel__item">
                  <span className="cici-kb-ops-panel__label">向量库审计</span>
                  <strong>{vectorAudit ? vectorAudit.status : "未运行"}</strong>
                  <span className="subtle">
                    {vectorAudit
                      ? `登记 ${vectorAudit.registeredCount}，扫描 ${vectorAudit.scannedCount}，孤儿 ${vectorAudit.orphanCount}`
                      : "检查当前组织向量库是否存在未登记向量。"}
                  </span>
                </div>
                <button type="button" className="cici-btn cici-btn--ghost cici-btn--sm" onClick={() => void runVectorAudit()}>
                  运行审计
                </button>
              </div>
              {vectorAudit?.message && (
                <div className={`cici-inline-feedback ${vectorAudit.success ? "cici-inline-feedback--success" : "cici-inline-feedback--warning"}`}>
                  <div className="cici-inline-feedback__main">
                    <strong>{vectorAudit.success ? "审计完成" : "审计异常"}</strong>
                    <span>{vectorAudit.message}</span>
                  </div>
                </div>
              )}

              <h3 className="cici-kb-main__title cici-kb-main__title--section">切片参数</h3>
              <div className="cici-kb-settings__grid cici-kb-settings__grid--triple">
                <label className="cici-field">
                  <span className="cici-field__label">chunkSize</span>
                  <input className="cici-field__input" type="number" min={80} max={1200} value={chunkSize} onChange={(e) => setChunkSize(Number(e.target.value))} />
                </label>
                <label className="cici-field">
                  <span className="cici-field__label">overlap</span>
                  <input className="cici-field__input" type="number" min={0} max={1199} value={chunkOverlap} onChange={(e) => setChunkOverlap(Number(e.target.value))} />
                </label>
                <label className="cici-field">
                  <span className="cici-field__label">delimiter</span>
                  <input className="cici-field__input" value={chunkDelimiter} onChange={(e) => setChunkDelimiter(e.target.value)} />
                </label>
              </div>
              <div className="cici-kb-settings__actions">
                <button type="button" className="cici-btn cici-btn--primary" onClick={() => void saveKbSettings()}>
                  保存切片/检索参数
                </button>
              </div>

              <h3 className="cici-kb-main__title cici-kb-main__title--section">向量模型</h3>
              <div className="cici-kb-embedding-note">
                <span>使用已启用模型厂商的 embedding 模型生成文档与问题向量。更换模型或维度后，需要重建已发布文档索引。</span>
              </div>
              <div className="cici-kb-settings__grid cici-kb-settings__grid--double">
                <label className="cici-field">
                  <span className="cici-field__label">embedding 模型</span>
                  <select
                    className="cici-field__input"
                    value={selectedEmbeddingValue}
                    onChange={(e) => {
                      const [providerCode, modelName] = e.target.value.split(":::");
                      const option = embeddingOptions.find((item) => item.providerCode === providerCode && item.modelName === modelName);
                      setEmbeddingProvider(providerCode);
                      setEmbeddingModel(modelName);
                      setEmbeddingDimension(option?.defaultDimension ?? 1024);
                    }}
                  >
                    {embeddingProvider === "local" && (
                      <option value="local:::local-hash">local-hash · 本地测试向量</option>
                    )}
                    {visibleEmbeddingOptions.map((option) => (
                      <option key={`${option.providerCode}-${option.modelName}`} value={`${option.providerCode}:::${option.modelName}`}>
                        {option.displayLabel}
                      </option>
                    ))}
                  </select>
                  <span className="subtle">
                    {embeddingOptions.length === 0 ? "请联系平台运营启用可提供 embedding 的模型厂商。" : "当前列表来自运营平台统一启用的模型厂商。"}
                  </span>
                </label>
                <label className="cici-field">
                  <span className="cici-field__label">向量维度</span>
                  <select
                    className="cici-field__input"
                    value={embeddingDimension}
                    onChange={(e) => setEmbeddingDimension(Number(e.target.value))}
                    disabled={embeddingProvider === "local"}
                  >
                    {embeddingDimensionChoices.map((dimension) => (
                      <option key={dimension} value={dimension}>{dimension}</option>
                    ))}
                  </select>
                  <span className="subtle">Qdrant collection 维度必须与这里一致。</span>
                </label>
              </div>

              <h3 className="cici-kb-main__title cici-kb-main__title--section">检索参数</h3>
              <div className="cici-kb-settings__grid cici-kb-settings__grid--double">
                <label className="cici-field">
                  <span className="cici-field__label">topK</span>
                  <input className="cici-field__input" type="number" min={1} max={20} value={topK} onChange={(e) => setTopK(Number(e.target.value))} />
                </label>
                <label className="cici-field">
                  <span className="cici-field__label">scoreThreshold(0-1)</span>
                  <input className="cici-field__input" type="number" min={0} max={1} step={0.01} value={scoreThreshold} onChange={(e) => setScoreThreshold(Number(e.target.value))} />
                </label>
              </div>

              <h3 className="cici-kb-main__title cici-kb-main__title--section">切片预览</h3>
              <label className="cici-field">
                <span className="cici-field__label">输入示例文本</span>
                <textarea
                  className="cici-field__textarea"
                  rows={4}
                  value={previewText}
                  onChange={(e) => setPreviewText(e.target.value)}
                  placeholder="输入一段文本，预览当前 chunk 参数下的切片效果"
                />
              </label>
              <div className="cici-kb-settings__actions">
                <button
                  type="button"
                  className="cici-btn cici-btn--primary"
                  disabled={!previewText.trim()}
                  onClick={() => void runChunkPreview()}
                >
                  预览切片
                </button>
              </div>
              {previewChunks.length > 0 && (
                <div className="cici-doc-table-wrap">
                  <table className="cici-doc-table">
                    <thead>
                      <tr>
                        <th>#</th>
                        <th>长度</th>
                        <th>内容</th>
                      </tr>
                    </thead>
                    <tbody>
                      {previewChunks.map((item) => (
                        <tr key={`${item.index}-${item.length}`}>
                          <td>{item.index + 1}</td>
                          <td>{item.length}</td>
                          <td>{item.content}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              {previewExecuted && previewChunks.length === 0 && (
                <div className="cici-kb-empty-tip">当前参数下未生成可预览切片。</div>
              )}

              <h3 className="cici-kb-main__title cici-kb-main__title--section">检索测试</h3>
              <label className="cici-field">
                <span className="cici-field__label">测试问题</span>
                <input
                  className="cici-field__input"
                  value={retrievalQuery}
                  onChange={(e) => setRetrievalQuery(e.target.value)}
                  placeholder="输入问题查看命中切片"
                />
              </label>
              <label className="cici-field">
                <span className="cici-field__label">metadata 过滤（可选）</span>
                <input
                  className="cici-field__input"
                  value={retrievalMetadataFilterText}
                  onChange={(e) => setRetrievalMetadataFilterText(e.target.value)}
                  placeholder="例如 region=east,product=crm"
                />
                <span className="subtle">
                  可用字段：{metadataFields.length === 0 ? "-" : metadataFields.map((field) => field.fieldKey).join(", ")}
                </span>
              </label>
              <div className="cici-kb-settings__actions">
                <button
                  type="button"
                  className="cici-btn cici-btn--primary"
                  disabled={!retrievalQuery.trim()}
                  onClick={() => void runRetrievalTest()}
                >
                  运行检索测试
                </button>
              </div>
              {retrievalHits.length > 0 && (
                <div className="cici-doc-table-wrap">
                  <table className="cici-doc-table">
                    <thead>
                      <tr>
                        <th>来源</th>
                        <th>score</th>
                        <th>chunk</th>
                        <th>内容</th>
                      </tr>
                    </thead>
                    <tbody>
                      {retrievalHits.map((hit) => (
                        <tr key={`${hit.vectorId}-${hit.chunkId ?? "na"}`}>
                          <td>{hit.source}</td>
                          <td>{hit.score.toFixed(4)}</td>
                          <td>{hit.chunkId ?? "-"}</td>
                          <td>{hit.content}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              {retrievalExecuted && retrievalHits.length === 0 && (
                <div className="cici-kb-empty-tip">本次检索命中 0 条，请调整问题或 metadata 过滤条件后重试。</div>
              )}

              <h3 className="cici-kb-main__title cici-kb-main__title--section">最近检索记录</h3>
              <div className="cici-doc-table-wrap">
                <table className="cici-doc-table">
                  <thead>
                    <tr>
                      <th>时间</th>
                      <th>问题</th>
                      <th>命中数</th>
                    </tr>
                  </thead>
                  <tbody>
                    {retrievalLogs.length === 0 && (
                      <tr>
                        <td colSpan={3} className="cici-doc-table__empty">暂无记录</td>
                      </tr>
                    )}
                    {retrievalLogs.map((item) => (
                      <tr key={item.id}>
                        <td>{formatDate(item.createdAt)}</td>
                        <td>{item.query}</td>
                        <td>{item.hitCount}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <h3 className="cici-kb-main__title cici-kb-main__title--section">Metadata 字段</h3>
              <div className="cici-kb-settings__grid cici-kb-settings__grid--metadata">
                <label className="cici-field">
                  <span className="cici-field__label">fieldKey</span>
                  <input className="cici-field__input" value={newFieldKey} onChange={(e) => setNewFieldKey(e.target.value)} placeholder="region" />
                </label>
                <label className="cici-field">
                  <span className="cici-field__label">显示名</span>
                  <input className="cici-field__input" value={newFieldName} onChange={(e) => setNewFieldName(e.target.value)} placeholder="区域" />
                </label>
                <div className="cici-kb-settings__field-action">
                  <button type="button" className="cici-btn cici-btn--primary" onClick={() => void createMetadataField()}>
                    新增字段
                  </button>
                </div>
              </div>
              <div className="cici-doc-table-wrap">
                <table className="cici-doc-table">
                  <thead>
                    <tr>
                      <th>key</th>
                      <th>名称</th>
                      <th>类型</th>
                    </tr>
                  </thead>
                  <tbody>
                    {metadataFields.length === 0 && (
                      <tr>
                        <td colSpan={3} className="cici-doc-table__empty">暂无 metadata 字段</td>
                      </tr>
                    )}
                    {metadataFields.map((field) => (
                      <tr key={field.id}>
                        <td>{field.fieldKey}</td>
                        <td>{field.fieldName}</td>
                        <td>{field.valueType}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </main>
      </div>

      {kbActionDialog}

      {chunkDocId !== null && (
        <div className="cici-modal-overlay" onClick={closeChunkPanel}>
          <div className="cici-modal cici-modal--kb-chunks" onClick={(e) => e.stopPropagation()}>
            <h2 className="cici-modal__title">切片管理 · {chunkDocName}</h2>
            <div className="cici-kb-settings__actions cici-kb-settings__actions--batch">
              <span className="subtle">已选 {selectedChunkCount} 项</span>
              <button
                type="button"
                className="cici-btn cici-btn--ghost cici-btn--sm"
                disabled={selectedChunkCount === 0}
                onClick={() => setSelectedChunkIds(new Set())}
              >
                清空选择
              </button>
              <button type="button" className="cici-btn cici-btn--ghost cici-btn--sm" disabled={selectedChunkCount === 0} onClick={() => void runChunkBatchAction("enable", "批量启用切片")}>
                批量启用
              </button>
              <button type="button" className="cici-btn cici-btn--ghost cici-btn--sm" disabled={selectedChunkCount === 0} onClick={() => void runChunkBatchAction("disable", "批量停用切片")}>
                批量停用
              </button>
              <button type="button" className="cici-btn cici-btn--danger cici-btn--sm" disabled={selectedChunkCount === 0} onClick={() => void runChunkBatchAction("delete", "批量删除切片")}>
                批量删除
              </button>
            </div>
            {chunkBatchFeedback && (
              <div className={`cici-inline-feedback cici-inline-feedback--${chunkBatchFeedback.tone}`}>
                <div className="cici-inline-feedback__main">
                  <strong>{chunkBatchFeedback.title}</strong>
                  <span>{chunkBatchFeedback.detail}</span>
                </div>
                <button
                  type="button"
                  className="cici-btn cici-btn--text cici-btn--xs"
                  onClick={() => setChunkBatchFeedback(null)}
                >
                  关闭
                </button>
              </div>
            )}
            <div className="cici-doc-table-wrap">
              <table className="cici-doc-table">
                <thead>
                  <tr>
                    <th>
                      <input
                        type="checkbox"
                        checked={chunkRows.length > 0 && chunkRows.every((chunk) => selectedChunkIds.has(chunk.id))}
                        onChange={(e) => {
                          if (e.target.checked) {
                            setSelectedChunkIds(new Set(chunkRows.map((chunk) => chunk.id)));
                          } else {
                            setSelectedChunkIds(new Set());
                          }
                        }}
                      />
                    </th>
                    <th>#</th>
                    <th>状态</th>
                    <th>内容</th>
                    <th className="cici-doc-table__th--actions">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {chunkRows.length === 0 && (
                    <tr>
                      <td colSpan={5} className="cici-doc-table__empty">暂无切片</td>
                    </tr>
                  )}
                  {chunkRows.map((chunk) => (
                    <tr key={chunk.id}>
                      <td>
                        <input
                          type="checkbox"
                          checked={selectedChunkIds.has(chunk.id)}
                          onChange={(e) => toggleChunkSelection(chunk.id, e.target.checked)}
                        />
                      </td>
                      <td>{(chunk.chunkIndex ?? 0) + 1}</td>
                      <td>{chunk.status}</td>
                      <td>{chunk.content}</td>
                      <td className="cici-doc-table__actions">
                        <button type="button" className="cici-btn cici-btn--text cici-btn--xs" onClick={() => void editChunkContent(chunk)}>
                          编辑
                        </button>
                        <button
                          type="button"
                          className="cici-btn cici-btn--text cici-btn--xs"
                          onClick={() => void toggleChunkEnabled(chunk, !chunk.enabled)}
                        >
                          {chunk.enabled ? "停用" : "启用"}
                        </button>
                        <button
                          type="button"
                          className="cici-btn cici-btn--text cici-btn--xs cici-btn--danger"
                          onClick={() => void deleteChunk(chunk)}
                        >
                          删除
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="cici-modal__actions">
              <button type="button" className="cici-btn cici-btn--ghost" onClick={closeChunkPanel}>
                关闭
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
