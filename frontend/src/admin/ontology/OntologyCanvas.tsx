import {
  useEffect,
  useMemo,
  useRef,
  type KeyboardEvent,
  type PointerEvent,
} from "react";
import { Boxes, CalendarClock, Move, Network } from "lucide-react";
import {
  moveConcept,
  moveConceptByKeyboard,
  relationLine,
  type OntologySelection,
} from "./ontologyModel";
import type { OntologyDocument } from "./ontologyTypes";

const NODE_WIDTH = 196;
const NODE_HEIGHT = 104;
const CANVAS_GUTTER = 56;

type DragState = {
  pointerId: number;
  conceptKey: string;
  startClientX: number;
  startClientY: number;
  startX: number;
  startY: number;
  moved: boolean;
};

export interface OntologyCanvasProps {
  document: OntologyDocument;
  selection: OntologySelection;
  busy: boolean;
  onSelect: (selection: OntologySelection) => void;
  onChange: (document: OntologyDocument) => void;
  onCommit: (document: OntologyDocument) => void | Promise<void>;
}

function conceptTypeLabel(type: string): string {
  return type === "EVENT" ? "业务事件" : "业务对象";
}

export default function OntologyCanvas({
  document,
  selection,
  busy,
  onSelect,
  onChange,
  onCommit,
}: OntologyCanvasProps) {
  const dragRef = useRef<DragState | null>(null);
  const currentDocumentRef = useRef(document);
  const keyboardCommitTimerRef = useRef<number | null>(null);

  useEffect(() => {
    currentDocumentRef.current = document;
  }, [document]);

  useEffect(() => () => {
    if (keyboardCommitTimerRef.current !== null) {
      window.clearTimeout(keyboardCommitTimerRef.current);
    }
  }, []);

  const clearKeyboardCommit = () => {
    if (keyboardCommitTimerRef.current === null) return;
    window.clearTimeout(keyboardCommitTimerRef.current);
    keyboardCommitTimerRef.current = null;
  };

  const scheduleKeyboardCommit = () => {
    clearKeyboardCommit();
    keyboardCommitTimerRef.current = window.setTimeout(() => {
      keyboardCommitTimerRef.current = null;
      void onCommit(currentDocumentRef.current);
    }, 240);
  };

  const stageSize = useMemo(() => ({
    width: Math.max(760, ...document.concepts.map((concept) => concept.positionX + NODE_WIDTH + CANVAS_GUTTER)),
    height: Math.max(520, ...document.concepts.map((concept) => concept.positionY + NODE_HEIGHT + CANVAS_GUTTER)),
  }), [document.concepts]);

  const relationGeometry = useMemo(() => document.relations.flatMap((relation) => {
    const source = document.concepts.find((concept) => concept.key === relation.sourceConceptKey);
    const target = document.concepts.find((concept) => concept.key === relation.targetConceptKey);
    if (!source || !target) return [];
    return [{
      relation,
      line: relationLine(
        { x: source.positionX, y: source.positionY, width: NODE_WIDTH, height: NODE_HEIGHT },
        { x: target.positionX, y: target.positionY, width: NODE_WIDTH, height: NODE_HEIGHT },
      ),
    }];
  }), [document.concepts, document.relations]);

  const handlePointerDown = (event: PointerEvent<HTMLButtonElement>, conceptKey: string) => {
    if (busy || event.button !== 0) return;
    clearKeyboardCommit();
    const concept = currentDocumentRef.current.concepts.find((item) => item.key === conceptKey);
    if (!concept) return;
    event.currentTarget.setPointerCapture(event.pointerId);
    dragRef.current = {
      pointerId: event.pointerId,
      conceptKey,
      startClientX: event.clientX,
      startClientY: event.clientY,
      startX: concept.positionX,
      startY: concept.positionY,
      moved: false,
    };
    onSelect({ kind: "concept", key: conceptKey });
  };

  const handlePointerMove = (event: PointerEvent<HTMLButtonElement>) => {
    const drag = dragRef.current;
    if (!drag || drag.pointerId !== event.pointerId || busy) return;
    const deltaX = event.clientX - drag.startClientX;
    const deltaY = event.clientY - drag.startClientY;
    if (Math.abs(deltaX) + Math.abs(deltaY) < 2) return;
    drag.moved = true;
    const next = moveConcept(currentDocumentRef.current, drag.conceptKey, {
      x: Math.max(16, Math.round(drag.startX + deltaX)),
      y: Math.max(16, Math.round(drag.startY + deltaY)),
    });
    currentDocumentRef.current = next;
    onChange(next);
  };

  const finishPointerMove = (event: PointerEvent<HTMLButtonElement>) => {
    const drag = dragRef.current;
    if (!drag || drag.pointerId !== event.pointerId) return;
    dragRef.current = null;
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
    if (drag.moved) void onCommit(currentDocumentRef.current);
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLButtonElement>, conceptKey: string) => {
    if (busy || !event.key.startsWith("Arrow")) return;
    event.preventDefault();
    const next = moveConceptByKeyboard(currentDocumentRef.current, conceptKey, event.key);
    const concept = next.concepts.find((item) => item.key === conceptKey);
    const bounded = concept && (concept.positionX < 16 || concept.positionY < 16)
      ? moveConcept(next, conceptKey, {
        x: Math.max(16, concept.positionX),
        y: Math.max(16, concept.positionY),
      })
      : next;
    currentDocumentRef.current = bounded;
    onChange(bounded);
    scheduleKeyboardCommit();
  };

  return (
    <section className="ontology-canvas" aria-label="业务对象关系画布">
      <div className="ontology-canvas__toolbar">
        <span><Network size={15} aria-hidden /> 关系画布</span>
        <span className="ontology-canvas__hint"><Move size={14} aria-hidden /> 拖动节点，或用方向键每次移动 8px</span>
      </div>
      <div className="ontology-canvas__viewport" data-testid="ontology-canvas-viewport">
        <div
          className="ontology-canvas__stage"
          style={{ width: stageSize.width, height: stageSize.height }}
        >
          <svg
            className="ontology-canvas__relations"
            width={stageSize.width}
            height={stageSize.height}
            viewBox={`0 0 ${stageSize.width} ${stageSize.height}`}
            aria-hidden="true"
          >
            <defs>
              <marker id="ontology-relation-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
                <path d="M 0 0 L 10 5 L 0 10 z" />
              </marker>
            </defs>
            {relationGeometry.map(({ relation, line }) => {
              const bend = Math.max(42, Math.abs(line.x2 - line.x1) * 0.42);
              const direction = line.x2 >= line.x1 ? 1 : -1;
              const path = `M ${line.x1} ${line.y1} C ${line.x1 + bend * direction} ${line.y1}, ${line.x2 - bend * direction} ${line.y2}, ${line.x2} ${line.y2}`;
              return (
                <g key={relation.key}>
                  <path d={path} markerEnd="url(#ontology-relation-arrow)" />
                  <text x={(line.x1 + line.x2) / 2} y={(line.y1 + line.y2) / 2 - 8} textAnchor="middle">
                    {relation.forwardLabel || relation.name}
                  </text>
                </g>
              );
            })}
          </svg>

          {document.concepts.length === 0 && (
            <div className="ontology-canvas__empty" role="status">
              <Boxes size={24} aria-hidden />
              <strong>从第一个业务对象开始</strong>
              <span>使用左侧“添加业务对象”，再在右侧补充业务属性。</span>
            </div>
          )}

          {document.concepts.map((concept) => {
            const selected = selection?.kind === "concept" && selection.key === concept.key;
            return (
              <button
                key={concept.key}
                type="button"
                className={`ontology-node${selected ? " is-selected" : ""}`}
                style={{ left: concept.positionX, top: concept.positionY }}
                aria-pressed={selected}
                aria-disabled={busy}
                aria-label={`${concept.name}，${conceptTypeLabel(concept.conceptType)}，${concept.properties.length} 个业务属性`}
                onClick={() => { if (!busy) onSelect({ kind: "concept", key: concept.key }); }}
                onKeyDown={(event) => handleKeyDown(event, concept.key)}
                onPointerDown={(event) => handlePointerDown(event, concept.key)}
                onPointerMove={handlePointerMove}
                onPointerUp={finishPointerMove}
                onPointerCancel={finishPointerMove}
              >
                <span className="ontology-node__topline">
                  {concept.conceptType === "EVENT" ? <CalendarClock size={15} aria-hidden /> : <Boxes size={15} aria-hidden />}
                  <span>{conceptTypeLabel(concept.conceptType)}</span>
                  <span>{concept.enabled && concept.queryable ? "可查询" : "草稿"}</span>
                </span>
                <strong>{concept.name}</strong>
                <span className="ontology-node__properties">
                  {concept.properties.slice(0, 3).map((property) => property.name).join(" · ") || "尚未添加业务属性"}
                </span>
                <span className="ontology-node__meta">{concept.properties.length} 个属性</span>
              </button>
            );
          })}
        </div>
      </div>
    </section>
  );
}
