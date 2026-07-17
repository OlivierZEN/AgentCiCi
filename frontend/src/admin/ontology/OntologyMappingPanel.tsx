import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Database, Plus, RefreshCw, ShieldCheck, Trash2 } from "lucide-react";
import { createStableOntologyKey } from "./ontologyModel";
import type {
  OntologyCatalogView,
  OntologyDataSourceMutationInput,
  OntologyDocument,
  OntologyMappingInput,
  OntologyMappingView,
  OntologySourceType,
  OntologySourceView,
} from "./ontologyTypes";

type EditableMapping = OntologyMappingInput & {
  clientKey: string;
  validationStatus: string;
};

type BusinessTerm = {
  targetType: string;
  targetKey: string;
  label: string;
  requiresField: boolean;
};

export interface OntologyMappingPanelProps {
  document: OntologyDocument;
  sources: OntologySourceView[];
  catalog: OntologyCatalogView | null;
  mappings: OntologyMappingView[];
  loading: boolean;
  busy: boolean;
  error: string;
  onReload: () => void | Promise<void>;
  onCreateSource: (input: OntologyDataSourceMutationInput) => void | Promise<void>;
  onDiscoverObjects: (sourceId: number) => void | Promise<void>;
  onDiscoverFields: (sourceId: number, objectKey: string) => void | Promise<void>;
  onSaveMappings: (mappings: OntologyMappingInput[]) => void | Promise<void>;
  onValidateMappings: (mappings: OntologyMappingInput[]) => void | Promise<void>;
}

function mappingStatusLabel(status: string): string {
  if (status === "VALID") return "已验证";
  if (status === "STALE") return "需重新验证";
  return "待验证";
}

function mappingStatusTone(status: string): "valid" | "stale" | "pending" {
  if (status === "VALID") return "valid";
  if (status === "STALE") return "stale";
  return "pending";
}

function toEditable(mapping: OntologyMappingView, index: number): EditableMapping {
  return {
    clientKey: `mapping-${mapping.id ?? index}`,
    targetType: mapping.targetType,
    targetKey: mapping.targetKey,
    dataSourceId: mapping.dataSourceId,
    physicalObjectKey: mapping.physicalObjectKey,
    physicalFieldKey: mapping.physicalFieldKey,
    relationTargetFieldKey: mapping.relationTargetFieldKey,
    transform: mapping.transform,
    confidence: mapping.confidence,
    validationStatus: mapping.validationStatus || "PENDING",
  };
}

function toInput(mapping: EditableMapping): OntologyMappingInput {
  return {
    targetType: mapping.targetType,
    targetKey: mapping.targetKey,
    dataSourceId: mapping.dataSourceId,
    physicalObjectKey: mapping.physicalObjectKey,
    physicalFieldKey: mapping.physicalFieldKey,
    relationTargetFieldKey: mapping.relationTargetFieldKey,
    transform: mapping.transform,
    confidence: mapping.confidence,
  };
}

export default function OntologyMappingPanel({
  document,
  sources,
  catalog,
  mappings,
  loading,
  busy,
  error,
  onReload,
  onCreateSource,
  onDiscoverObjects,
  onDiscoverFields,
  onSaveMappings,
  onValidateMappings,
}: OntologyMappingPanelProps) {
  const [sourceName, setSourceName] = useState("");
  const [sourceType, setSourceType] = useState<OntologySourceType>("INLINE_SAMPLE");
  const [adapterKey, setAdapterKey] = useState("");
  const [sampleData, setSampleData] = useState('{"items":[{"name":"示例记录"}]}');
  const [rows, setRows] = useState<EditableMapping[]>([]);
  const [rowsDirty, setRowsDirty] = useState(false);

  useEffect(() => {
    setRows(mappings.map(toEditable));
    setRowsDirty(false);
  }, [mappings]);

  const terms = useMemo<BusinessTerm[]>(() => [
    ...document.concepts.map((concept) => ({
      targetType: "CONCEPT",
      targetKey: concept.key,
      label: `对象 · ${concept.name}`,
      requiresField: false,
    })),
    ...document.concepts.flatMap((concept) => concept.properties.map((property) => ({
      targetType: "PROPERTY",
      targetKey: `${concept.key}.${property.key}`,
      label: `属性 · ${concept.name} / ${property.name}`,
      requiresField: true,
    }))),
    ...document.relations.map((relation) => ({
      targetType: "RELATION",
      targetKey: relation.key,
      label: `关系 · ${relation.name}`,
      requiresField: true,
    })),
    ...document.metrics.map((metric) => ({
      targetType: "METRIC",
      targetKey: metric.key,
      label: `指标 · ${metric.name}`,
      requiresField: false,
    })),
    ...document.actions.map((action) => ({
      targetType: "ACTION",
      targetKey: action.key,
      label: `动作 · ${action.name}`,
      requiresField: false,
    })),
  ], [document]);

  const updateRow = (clientKey: string, patch: Partial<EditableMapping>) => {
    setRowsDirty(true);
    setRows((current) => current.map((row) => row.clientKey === clientKey
      ? { ...row, ...patch, validationStatus: "STALE" }
      : row));
  };

  const addMapping = () => {
    const term = terms[0];
    const source = sources[0];
    const object = catalog?.objects.find((item) => item.dataSourceId === source?.id) ?? catalog?.objects[0];
    if (!term || !source || !object) return;
    setRowsDirty(true);
    const field = object.fields[0];
    setRows((current) => [...current, {
      clientKey: `new-${Date.now()}-${current.length}`,
      targetType: term.targetType,
      targetKey: term.targetKey,
      dataSourceId: source.id,
      physicalObjectKey: object.key,
      physicalFieldKey: term.requiresField ? field?.key ?? null : null,
      relationTargetFieldKey: null,
      transform: null,
      confidence: 1,
      validationStatus: "PENDING",
    }]);
  };

  const submitSource = (event: FormEvent) => {
    event.preventDefault();
    const name = sourceName.trim();
    if (!name) return;
    const key = createStableOntologyKey(name, sources.map((source) => source.key), "data-source");
    void onCreateSource({
      id: null,
      key,
      name,
      type: sourceType,
      configJson: sourceType === "CONNECTOR" && adapterKey.trim()
        ? JSON.stringify({ adapterKey: adapterKey.trim() })
        : null,
      sampleDataJson: sourceType === "INLINE_SAMPLE" ? sampleData : null,
    });
    setSourceName("");
  };

  if (loading) {
    return (
      <div className="ontology-panel-loading" role="status" aria-label="正在加载数据映射">
        <span />
        <span />
        <span />
      </div>
    );
  }

  return (
    <section className="ontology-mapping" aria-label="数据映射工作台">
      <header className="ontology-section-header">
        <div>
          <span>数据映射</span>
          <h2>把业务术语连接到真实数据</h2>
          <p>连接信息和示例数据只通过专用数据源请求保存，不会写回本体草稿文档。</p>
        </div>
        <button type="button" className="ontology-text-action" disabled={busy} onClick={() => void onReload()}>
          <RefreshCw size={14} aria-hidden /> 重新载入
        </button>
      </header>

      {error && <div className="ontology-inline-alert" role="alert">{error}</div>}

      <div className="ontology-mapping__source-layout">
        <form className="ontology-source-form" onSubmit={submitSource}>
          <div className="ontology-subsection-heading">
            <Database size={16} aria-hidden />
            <div><strong>添加数据来源</strong><span>内置示例或组织连接器</span></div>
          </div>
          <label>
            <span>来源名称</span>
            <input value={sourceName} disabled={busy} placeholder="例如：项目交付示例数据" onChange={(event) => setSourceName(event.target.value)} />
          </label>
          <label>
            <span>来源方式</span>
            <select value={sourceType} disabled={busy} onChange={(event) => setSourceType(event.target.value as OntologySourceType)}>
              <option value="INLINE_SAMPLE">内置示例数据</option>
              <option value="CONNECTOR">组织连接器</option>
            </select>
          </label>
          {sourceType === "CONNECTOR" ? (
            <label>
              <span>连接器标识</span>
              <input value={adapterKey} disabled={busy} placeholder="选择已配置的连接器标识" onChange={(event) => setAdapterKey(event.target.value)} />
            </label>
          ) : (
            <label>
              <span>示例记录（JSON）</span>
              <textarea rows={5} value={sampleData} disabled={busy} spellCheck={false} onChange={(event) => setSampleData(event.target.value)} />
            </label>
          )}
          <button type="submit" className="cici-btn cici-btn--ghost" disabled={busy || !sourceName.trim()}>
            <Plus size={15} aria-hidden /> 创建数据来源
          </button>
        </form>

        <div className="ontology-source-list" aria-label="已连接的数据来源">
          <div className="ontology-subsection-heading">
            <Database size={16} aria-hidden />
            <div><strong>来源与字段目录</strong><span>{sources.length} 个来源，{catalog?.objects.length ?? 0} 个数据对象</span></div>
          </div>
          {sources.length === 0 && <p className="ontology-inline-empty">还没有数据来源。手工建模仍可继续，发布前再完成映射。</p>}
          {sources.map((source) => {
            const objects = catalog?.objects.filter((object) => object.dataSourceId === source.id) ?? [];
            return (
              <div className="ontology-source-row" key={source.id}>
                <div className="ontology-source-row__head">
                  <div>
                    <strong>{source.name}</strong>
                    <span>{source.type === "INLINE_SAMPLE" ? "内置示例" : "组织连接器"} · {source.status}</span>
                  </div>
                  <button type="button" className="ontology-text-action" disabled={busy} onClick={() => void onDiscoverObjects(source.id)}>
                    发现数据对象
                  </button>
                </div>
                {objects.map((object) => (
                  <div className="ontology-catalog-row" key={object.id}>
                    <span><strong>{object.name}</strong><small>{object.fields.length} 个字段</small></span>
                    <button type="button" className="ontology-text-action" disabled={busy} onClick={() => void onDiscoverFields(source.id, object.key)}>
                      发现字段
                    </button>
                  </div>
                ))}
              </div>
            );
          })}
        </div>
      </div>

      <div className="ontology-mapping__rows-head">
        <div>
          <h3>业务术语映射</h3>
          <p>业务术语 → 数据来源 → 数据对象 → 字段</p>
        </div>
        <div>
          <button type="button" className="ontology-text-action" disabled={busy || !terms.length || !sources.length || !catalog?.objects.length} onClick={addMapping}>
            <Plus size={14} aria-hidden /> 添加映射
          </button>
          <button type="button" className="cici-btn cici-btn--ghost" disabled={busy || !rowsDirty} onClick={() => void onSaveMappings(rows.map(toInput))}>
            保存映射
          </button>
          <button
            type="button"
            className="cici-btn cici-btn--primary"
            disabled={busy || rows.length === 0 || rowsDirty}
            title={rowsDirty ? "请先保存映射，再批量验证" : undefined}
            onClick={() => void onValidateMappings(rows.map(toInput))}
          >
            <ShieldCheck size={15} aria-hidden /> 批量验证
          </button>
        </div>
      </div>

      {rows.length === 0 ? (
        <div className="ontology-mapping__empty" role="status">
          <strong>还没有字段映射</strong>
          <span>发现数据对象和字段后，选择“添加映射”。</span>
        </div>
      ) : (
        <div className="ontology-mapping__table-wrap">
          <table className="ontology-mapping__table">
            <thead>
              <tr>
                <th>业务术语</th>
                <th>数据来源</th>
                <th>数据对象</th>
                <th>字段</th>
                <th>状态</th>
                <th aria-label="删除" />
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => {
                const availableObjects = catalog?.objects.filter((object) => object.dataSourceId === row.dataSourceId) ?? [];
                const selectedObject = availableObjects.find((object) => object.key === row.physicalObjectKey);
                const selectedTerm = terms.find((term) => term.targetType === row.targetType && term.targetKey === row.targetKey);
                return (
                  <tr key={row.clientKey}>
                    <td>
                      <select
                        aria-label="业务术语"
                        value={`${row.targetType}:${row.targetKey}`}
                        disabled={busy}
                        onChange={(event) => {
                          const term = terms.find((item) => `${item.targetType}:${item.targetKey}` === event.target.value);
                          if (term) updateRow(row.clientKey, { targetType: term.targetType, targetKey: term.targetKey, physicalFieldKey: term.requiresField ? row.physicalFieldKey : null });
                        }}
                      >
                        {terms.map((term) => <option key={`${term.targetType}:${term.targetKey}`} value={`${term.targetType}:${term.targetKey}`}>{term.label}</option>)}
                      </select>
                    </td>
                    <td>
                      <select
                        aria-label="数据来源"
                        value={row.dataSourceId}
                        disabled={busy}
                        onChange={(event) => {
                          const dataSourceId = Number(event.target.value);
                          const firstObject = catalog?.objects.find((object) => object.dataSourceId === dataSourceId);
                          updateRow(row.clientKey, { dataSourceId, physicalObjectKey: firstObject?.key ?? "", physicalFieldKey: selectedTerm?.requiresField ? firstObject?.fields[0]?.key ?? null : null });
                        }}
                      >
                        {sources.map((source) => <option key={source.id} value={source.id}>{source.name}</option>)}
                      </select>
                    </td>
                    <td>
                      <select
                        aria-label="数据对象"
                        value={row.physicalObjectKey}
                        disabled={busy}
                        onChange={(event) => {
                          const object = availableObjects.find((item) => item.key === event.target.value);
                          updateRow(row.clientKey, { physicalObjectKey: event.target.value, physicalFieldKey: selectedTerm?.requiresField ? object?.fields[0]?.key ?? null : null });
                        }}
                      >
                        {availableObjects.map((object) => <option key={object.key} value={object.key}>{object.name}</option>)}
                      </select>
                    </td>
                    <td>
                      <select
                        aria-label="字段"
                        value={row.physicalFieldKey ?? ""}
                        disabled={busy || !selectedTerm?.requiresField}
                        onChange={(event) => updateRow(row.clientKey, { physicalFieldKey: event.target.value || null })}
                      >
                        <option value="">{selectedTerm?.requiresField ? "选择字段" : "不需要字段"}</option>
                        {selectedObject?.fields.map((field) => <option key={field.key} value={field.key}>{field.name}</option>)}
                      </select>
                    </td>
                    <td><span className={`ontology-mapping-status is-${mappingStatusTone(row.validationStatus)}`}>{mappingStatusLabel(row.validationStatus)}</span></td>
                    <td>
                      <button
                        type="button"
                        className="ontology-icon-action"
                        aria-label="删除映射"
                        disabled={busy}
                        onClick={() => {
                          setRowsDirty(true);
                          setRows((current) => current.filter((item) => item.clientKey !== row.clientKey));
                        }}
                      >
                        <Trash2 size={14} aria-hidden />
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
