import { Plus, Save, Trash2 } from "lucide-react";
import {
  createStableOntologyKey,
  removeConceptProperty,
  type OntologySelection,
} from "./ontologyModel";
import type {
  OntologyAction,
  OntologyConcept,
  OntologyDataType,
  OntologyDocument,
  OntologyMetric,
  OntologyProperty,
  OntologyRelation,
} from "./ontologyTypes";

export interface OntologyInspectorProps {
  document: OntologyDocument;
  selection: OntologySelection;
  busy: boolean;
  onChange: (document: OntologyDocument) => void;
  onSave: (document: OntologyDocument) => void | Promise<void>;
  onDelete: (selection: NonNullable<OntologySelection>) => void;
}

const DATA_TYPE_OPTIONS: Array<{ value: OntologyDataType; label: string }> = [
  { value: "TEXT", label: "短文本" },
  { value: "LONG_TEXT", label: "长文本" },
  { value: "INTEGER", label: "整数" },
  { value: "DECIMAL", label: "小数" },
  { value: "BOOLEAN", label: "是 / 否" },
  { value: "DATE", label: "日期" },
  { value: "DATETIME", label: "日期时间" },
  { value: "ENUM", label: "选项" },
  { value: "REFERENCE", label: "对象引用" },
];

function CheckboxField({
  checked,
  label,
  disabled,
  onChange,
}: {
  checked: boolean;
  label: string;
  disabled: boolean;
  onChange: (checked: boolean) => void;
}) {
  return (
    <label className="ontology-check">
      <input
        type="checkbox"
        checked={checked}
        disabled={disabled}
        onChange={(event) => onChange(event.target.checked)}
      />
      <span>{label}</span>
    </label>
  );
}

export default function OntologyInspector({
  document,
  selection,
  busy,
  onChange,
  onSave,
  onDelete,
}: OntologyInspectorProps) {
  const concept = selection?.kind === "concept"
    ? document.concepts.find((item) => item.key === selection.key)
    : undefined;
  const relation = selection?.kind === "relation"
    ? document.relations.find((item) => item.key === selection.key)
    : undefined;
  const metric = selection?.kind === "metric"
    ? document.metrics.find((item) => item.key === selection.key)
    : undefined;
  const action = selection?.kind === "action"
    ? document.actions.find((item) => item.key === selection.key)
    : undefined;

  const updateConcept = (patch: Partial<OntologyConcept>) => {
    if (!concept) return;
    onChange({
      ...document,
      concepts: document.concepts.map((item) => item.key === concept.key ? { ...item, ...patch } : item),
    });
  };

  const updateProperty = (propertyKey: string, patch: Partial<OntologyProperty>) => {
    if (!concept) return;
    updateConcept({
      properties: concept.properties.map((property) => property.key === propertyKey
        ? { ...property, ...patch }
        : property),
    });
  };

  const addProperty = () => {
    if (!concept) return;
    const key = createStableOntologyKey("property", concept.properties.map((property) => property.key));
    updateConcept({
      properties: [...concept.properties, {
        key,
        name: `新业务属性 ${concept.properties.length + 1}`,
        description: null,
        dataType: "TEXT",
        required: false,
        multiple: false,
        sensitive: false,
        queryable: true,
        enumValues: [],
      }],
    });
  };

  const removeProperty = (propertyKey: string) => {
    if (!concept) return;
    onChange(removeConceptProperty(document, concept.key, propertyKey));
  };

  const updateRelation = (patch: Partial<OntologyRelation>) => {
    if (!relation) return;
    onChange({
      ...document,
      relations: document.relations.map((item) => item.key === relation.key ? { ...item, ...patch } : item),
    });
  };

  const updateMetric = (patch: Partial<OntologyMetric>) => {
    if (!metric) return;
    onChange({
      ...document,
      metrics: document.metrics.map((item) => item.key === metric.key ? { ...item, ...patch } : item),
    });
  };

  const updateAction = (patch: Partial<OntologyAction>) => {
    if (!action) return;
    onChange({
      ...document,
      actions: document.actions.map((item) => item.key === action.key ? { ...item, ...patch } : item),
    });
  };

  const renderFooter = () => selection && (
    <div className="ontology-inspector__footer">
      <button
        type="button"
        className="ontology-text-action ontology-text-action--danger"
        disabled={busy}
        onClick={() => onDelete(selection)}
      >
        <Trash2 size={14} aria-hidden /> 删除当前定义
      </button>
      <button
        type="button"
        className="cici-btn cici-btn--primary ontology-inspector__save"
        disabled={busy}
        onClick={() => void onSave(document)}
      >
        <Save size={15} aria-hidden /> {busy ? "正在保存" : "保存业务定义"}
      </button>
    </div>
  );

  return (
    <aside className="ontology-inspector" aria-label="业务检查器">
      <div className="ontology-panel-heading">
        <div>
          <span>业务检查器</span>
          <strong>{concept?.name || relation?.name || metric?.name || action?.name || "请选择一项"}</strong>
        </div>
      </div>

      {!selection && (
        <div className="ontology-inspector__empty" role="status">
          <strong>选择一个业务定义</strong>
          <p>从左侧目录或中间画布选择业务对象、关系、指标或动作，然后在这里编辑。</p>
        </div>
      )}

      {concept && (
        <div className="ontology-inspector__body">
          <div className="ontology-form-grid">
            <label>
              <span>业务名称</span>
              <input value={concept.name} disabled={busy} onChange={(event) => updateConcept({ name: event.target.value })} />
            </label>
            <label>
              <span>集合名称</span>
              <input value={concept.pluralName ?? ""} disabled={busy} onChange={(event) => updateConcept({ pluralName: event.target.value || null })} />
            </label>
            <label className="ontology-form-grid__full">
              <span>业务说明</span>
              <textarea rows={3} value={concept.description ?? ""} disabled={busy} onChange={(event) => updateConcept({ description: event.target.value || null })} />
            </label>
            <label>
              <span>对象类型</span>
              <select value={concept.conceptType} disabled={busy} onChange={(event) => updateConcept({ conceptType: event.target.value as OntologyConcept["conceptType"] })}>
                <option value="ENTITY">业务对象</option>
                <option value="EVENT">业务事件</option>
              </select>
            </label>
            <label>
              <span>主要显示属性</span>
              <select value={concept.displayPropertyKey ?? ""} disabled={busy} onChange={(event) => updateConcept({ displayPropertyKey: event.target.value || null })}>
                <option value="">暂不设置</option>
                {concept.properties.map((property) => <option key={property.key} value={property.key}>{property.name}</option>)}
              </select>
            </label>
          </div>
          <div className="ontology-inline-checks">
            <CheckboxField checked={concept.enabled} label="启用此对象" disabled={busy} onChange={(enabled) => updateConcept({ enabled })} />
            <CheckboxField checked={concept.queryable} label="允许业务查询" disabled={busy} onChange={(queryable) => updateConcept({ queryable })} />
          </div>

          <section className="ontology-inspector__section" aria-labelledby="ontology-property-heading">
            <div className="ontology-inspector__section-head">
              <h3 id="ontology-property-heading">业务属性</h3>
              <button type="button" className="ontology-text-action" disabled={busy} onClick={addProperty}>
                <Plus size={14} aria-hidden /> 添加属性
              </button>
            </div>
            {concept.properties.length === 0 && <p className="ontology-inline-empty">还没有属性，先添加业务人员识别这个对象所需的信息。</p>}
            {concept.properties.map((property) => (
              <div className="ontology-property-row" key={property.key}>
                <div className="ontology-property-row__main">
                  <label>
                    <span>属性名称</span>
                    <input value={property.name} disabled={busy} onChange={(event) => updateProperty(property.key, { name: event.target.value })} />
                  </label>
                  <label>
                    <span>信息类型</span>
                    <select value={property.dataType} disabled={busy} onChange={(event) => updateProperty(property.key, { dataType: event.target.value as OntologyDataType })}>
                      {DATA_TYPE_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                    </select>
                  </label>
                </div>
                <div className="ontology-property-row__flags">
                  <CheckboxField checked={property.required} label="必填" disabled={busy} onChange={(required) => updateProperty(property.key, { required })} />
                  <CheckboxField checked={property.queryable} label="可查询" disabled={busy || property.sensitive} onChange={(queryable) => updateProperty(property.key, { queryable })} />
                  <CheckboxField checked={property.multiple} label="可多选" disabled={busy} onChange={(multiple) => updateProperty(property.key, { multiple })} />
                  <CheckboxField checked={property.sensitive} label="敏感信息" disabled={busy} onChange={(sensitive) => updateProperty(property.key, { sensitive, queryable: sensitive ? false : property.queryable })} />
                  <button type="button" className="ontology-icon-action" aria-label={`删除属性${property.name}`} disabled={busy} onClick={() => removeProperty(property.key)}>
                    <Trash2 size={14} aria-hidden />
                  </button>
                </div>
                {property.dataType === "ENUM" && (
                  <label className="ontology-property-row__enum">
                    <span>选项值（用逗号分隔）</span>
                    <input
                      value={property.enumValues.join("，")}
                      disabled={busy}
                      onChange={(event) => updateProperty(property.key, {
                        enumValues: event.target.value.split(/[，,]/).map((value) => value.trim()).filter(Boolean),
                      })}
                    />
                  </label>
                )}
              </div>
            ))}
          </section>
          {renderFooter()}
        </div>
      )}

      {relation && (
        <div className="ontology-inspector__body">
          <div className="ontology-form-grid">
            <label className="ontology-form-grid__full">
              <span>关系名称</span>
              <input value={relation.name} disabled={busy} onChange={(event) => updateRelation({ name: event.target.value })} />
            </label>
            <label>
              <span>起始对象</span>
              <select value={relation.sourceConceptKey} disabled={busy} onChange={(event) => updateRelation({ sourceConceptKey: event.target.value })}>
                {document.concepts.map((item) => <option key={item.key} value={item.key}>{item.name}</option>)}
              </select>
            </label>
            <label>
              <span>目标对象</span>
              <select value={relation.targetConceptKey} disabled={busy} onChange={(event) => updateRelation({ targetConceptKey: event.target.value })}>
                {document.concepts.map((item) => <option key={item.key} value={item.key}>{item.name}</option>)}
              </select>
            </label>
            <label>
              <span>业务数量关系</span>
              <select value={relation.cardinality} disabled={busy} onChange={(event) => updateRelation({ cardinality: event.target.value as OntologyRelation["cardinality"] })}>
                <option value="ONE_TO_ONE">一对一</option>
                <option value="ONE_TO_MANY">一对多</option>
                <option value="MANY_TO_ONE">多对一</option>
                <option value="MANY_TO_MANY">多对多</option>
              </select>
            </label>
            <label>
              <span>正向读法</span>
              <input value={relation.forwardLabel ?? ""} disabled={busy} onChange={(event) => updateRelation({ forwardLabel: event.target.value || null })} />
            </label>
            <label>
              <span>反向读法</span>
              <input value={relation.reverseLabel ?? ""} disabled={busy} onChange={(event) => updateRelation({ reverseLabel: event.target.value || null })} />
            </label>
            <label className="ontology-form-grid__full">
              <span>关系说明</span>
              <textarea rows={3} value={relation.description ?? ""} disabled={busy} onChange={(event) => updateRelation({ description: event.target.value || null })} />
            </label>
          </div>
          <div className="ontology-inline-checks">
            <CheckboxField checked={relation.enabled} label="启用此关系" disabled={busy} onChange={(enabled) => updateRelation({ enabled })} />
            <CheckboxField checked={relation.queryable} label="允许关系查询" disabled={busy} onChange={(queryable) => updateRelation({ queryable })} />
          </div>
          {renderFooter()}
        </div>
      )}

      {metric && (
        <div className="ontology-inspector__body">
          <div className="ontology-form-grid">
            <label className="ontology-form-grid__full">
              <span>指标名称</span>
              <input value={metric.name} disabled={busy} onChange={(event) => updateMetric({ name: event.target.value })} />
            </label>
            <label>
              <span>适用业务对象</span>
              <select value={metric.conceptKey} disabled={busy} onChange={(event) => updateMetric({ conceptKey: event.target.value, measurePropertyKey: null, timePropertyKey: null })}>
                {document.concepts.map((item) => <option key={item.key} value={item.key}>{item.name}</option>)}
              </select>
            </label>
            <label>
              <span>统计方式</span>
              <select value={metric.aggregation} disabled={busy} onChange={(event) => updateMetric({ aggregation: event.target.value as OntologyMetric["aggregation"] })}>
                <option value="COUNT">数量</option>
                <option value="SUM">求和</option>
                <option value="AVG">平均值</option>
                <option value="MIN">最小值</option>
                <option value="MAX">最大值</option>
              </select>
            </label>
            <label>
              <span>度量属性</span>
              <select value={metric.measurePropertyKey ?? ""} disabled={busy} onChange={(event) => updateMetric({ measurePropertyKey: event.target.value || null })}>
                <option value="">仅统计数量</option>
                {document.concepts.find((item) => item.key === metric.conceptKey)?.properties.map((property) => <option key={property.key} value={property.key}>{property.name}</option>)}
              </select>
            </label>
            <label>
              <span>时间属性</span>
              <select value={metric.timePropertyKey ?? ""} disabled={busy} onChange={(event) => updateMetric({ timePropertyKey: event.target.value || null })}>
                <option value="">不限定时间属性</option>
                {document.concepts.find((item) => item.key === metric.conceptKey)?.properties.map((property) => <option key={property.key} value={property.key}>{property.name}</option>)}
              </select>
            </label>
          </div>
          {renderFooter()}
        </div>
      )}

      {action && (
        <div className="ontology-inspector__body">
          <div className="ontology-form-grid">
            <label className="ontology-form-grid__full">
              <span>动作名称</span>
              <input value={action.name} disabled={busy} onChange={(event) => updateAction({ name: event.target.value })} />
            </label>
            <label className="ontology-form-grid__full">
              <span>适用业务对象</span>
              <select value={action.conceptKey} disabled={busy} onChange={(event) => updateAction({ conceptKey: event.target.value })}>
                {document.concepts.map((item) => <option key={item.key} value={item.key}>{item.name}</option>)}
              </select>
            </label>
            <label className="ontology-form-grid__full">
              <span>业务用途</span>
              <textarea rows={4} value={action.description ?? ""} disabled={busy} onChange={(event) => updateAction({ description: event.target.value || null })} />
            </label>
          </div>
          <section className="ontology-inspector__section" aria-labelledby="ontology-action-parameter-heading">
            <div className="ontology-inspector__section-head">
              <h3 id="ontology-action-parameter-heading">所需信息</h3>
              <button
                type="button"
                className="ontology-text-action"
                disabled={busy}
                onClick={() => {
                  const key = createStableOntologyKey("parameter", action.parameters.map((item) => item.key));
                  updateAction({ parameters: [...action.parameters, { key, name: `输入信息 ${action.parameters.length + 1}`, dataType: "TEXT", required: false }] });
                }}
              >
                <Plus size={14} aria-hidden /> 添加信息
              </button>
            </div>
            {action.parameters.map((parameter) => (
              <div className="ontology-parameter-row" key={parameter.key}>
                <input
                  aria-label="参数名称"
                  value={parameter.name}
                  disabled={busy}
                  onChange={(event) => updateAction({ parameters: action.parameters.map((item) => item.key === parameter.key ? { ...item, name: event.target.value } : item) })}
                />
                <select
                  aria-label="参数类型"
                  value={parameter.dataType}
                  disabled={busy}
                  onChange={(event) => updateAction({ parameters: action.parameters.map((item) => item.key === parameter.key ? { ...item, dataType: event.target.value as OntologyDataType } : item) })}
                >
                  {DATA_TYPE_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                </select>
                <CheckboxField
                  checked={parameter.required}
                  label="必填"
                  disabled={busy}
                  onChange={(required) => updateAction({ parameters: action.parameters.map((item) => item.key === parameter.key ? { ...item, required } : item) })}
                />
                <button
                  type="button"
                  className="ontology-icon-action"
                  aria-label={`删除所需信息${parameter.name}`}
                  disabled={busy}
                  onClick={() => updateAction({ parameters: action.parameters.filter((item) => item.key !== parameter.key) })}
                >
                  <Trash2 size={14} aria-hidden />
                </button>
              </div>
            ))}
            <p className="ontology-inline-note">V1 只生成动作契约，不会向外部系统执行写入。</p>
          </section>
          {renderFooter()}
        </div>
      )}
    </aside>
  );
}
