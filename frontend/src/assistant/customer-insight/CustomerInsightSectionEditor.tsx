import ChatMarkdown from "../../components/ChatMarkdown";
import { compactDate, statusLabel, statusTone } from "./customerInsightSections";
import type { CustomerInsightSection } from "./customerInsightTypes";

type Props = {
  section: CustomerInsightSection;
  inputText: string;
  saving: boolean;
  generating: boolean;
  onInputChange: (value: string) => void;
  onSave: () => void;
  onGenerate: () => void;
};

export function CustomerInsightSectionEditor({
  section,
  inputText,
  saving,
  generating,
  onInputChange,
  onSave,
  onGenerate,
}: Props) {
  const generated = section.aiGenerated && section.markdown.trim();
  return (
    <section className="cici-customer-insight__editor" aria-label={`${section.title}编辑区`}>
      <header className="cici-customer-insight__section-head">
        <div>
          <h3>{section.title}</h3>
          <span>{section.description}</span>
        </div>
        <span className={`cici-customer-insight__status is-${statusTone(section.status)}`}>
          {statusLabel(section.status)}
        </span>
      </header>

      <div className="cici-customer-insight__facts">
        <label htmlFor={`customer-insight-input-${section.sectionCode}`}>
          人工事实与补充
        </label>
        <textarea
          id={`customer-insight-input-${section.sectionCode}`}
          value={inputText}
          onChange={(event) => onInputChange(event.target.value)}
          placeholder="记录已验证事实、当前商机、关键联系人、已签合同、订单履约、客户服务记录或待确认问题。"
        />
      </div>

      <div className="cici-customer-insight__editor-actions">
        <button type="button" className="cici-customer-insight__secondary" onClick={onSave} disabled={saving || generating}>
          {saving ? "保存中" : "保存草稿"}
        </button>
        <button type="button" className="cici-customer-insight__primary" onClick={onGenerate} disabled={saving || generating}>
          {generating ? "生成中" : "生成当前模块"}
        </button>
      </div>

      <section className="cici-customer-insight__analysis" aria-label="AI分析结果">
        <div className="cici-customer-insight__analysis-head">
          <div>
            <h4>分析结果</h4>
            <span>{section.traceId ? `Trace ${section.traceId.slice(0, 8)}` : "生成后写入运行 trace"}</span>
          </div>
          <small>{section.updatedAt ? compactDate(section.updatedAt) : ""}</small>
        </div>
        {section.errorMessage ? <p className="cici-customer-insight__error">{section.errorMessage}</p> : null}
        {generated ? (
          <ChatMarkdown content={section.markdown} />
        ) : (
          <p className="cici-customer-insight__empty">
            补充事实后生成模块分析，AI 输出会标记待人工确认。
          </p>
        )}
      </section>
    </section>
  );
}
