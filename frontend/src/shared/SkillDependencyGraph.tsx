import type { CSSProperties } from "react";
import { useCallback, useEffect, useId, useMemo, useRef, useState } from "react";
import {
  AlertTriangle,
  BookOpen,
  Bot,
  Boxes,
  GitBranch,
  Maximize2,
  RotateCcw,
  Workflow,
  Wrench,
  ZoomIn,
  ZoomOut,
} from "lucide-react";
import type {
  SkillDependencyGraphEdge,
  SkillDependencyGraphNode,
  SkillDependencyGraphView,
  SkillDependencyNodeType,
} from "./skill-dag";
import "./skill-dependency-graph.css";

export type {
  SkillDependencyGraphEdge,
  SkillDependencyGraphNode,
  SkillDependencyGraphScope,
  SkillDependencyGraphSummary,
  SkillDependencyGraphView,
  SkillDependencyNodeType,
} from "./skill-dag";

const NODE_WIDTH = 184;
const NODE_HEIGHT = 74;
const LAYER_GAP = 80;
const NODE_GAP = 20;
const CANVAS_PADDING = 36;
const MIN_CANVAS_HEIGHT = 300;
const MIN_SCALE = 0.45;
const MAX_SCALE = 1.6;

const NODE_TYPE_ORDER: Record<SkillDependencyNodeType, number> = {
  AGENT: 0,
  WORKFLOW_VERSION: 1,
  SKILL: 2,
  SKILL_VERSION: 3,
  TOOL: 4,
  KNOWLEDGE_BASE: 5,
};

const NODE_TYPE_LABEL: Record<SkillDependencyNodeType, string> = {
  AGENT: "Agent",
  WORKFLOW_VERSION: "工作流版本",
  SKILL: "Skill",
  SKILL_VERSION: "Skill 版本",
  TOOL: "Tool",
  KNOWLEDGE_BASE: "知识库",
};

const METADATA_LABELS: Record<string, string> = {
  activationMode: "激活方式",
  builtin: "内置",
  priority: "优先级",
  referenceMode: "引用来源",
  riskLevel: "风险等级",
  templateCode: "模板代码",
  templateVersionNo: "模板版本",
  versionLabel: "版本标识",
  versionNo: "版本号",
};

export type PositionedSkillDependencyNode = SkillDependencyGraphNode & {
  x: number;
  y: number;
  width: number;
  height: number;
};

export type PositionedSkillDependencyEdge = SkillDependencyGraphEdge & {
  path: string;
  labelX: number;
  labelY: number;
};

export type SkillDependencyGraphLayout = {
  width: number;
  height: number;
  nodes: PositionedSkillDependencyNode[];
  edges: PositionedSkillDependencyEdge[];
};

type SkillDependencyGraphProps = {
  graph: SkillDependencyGraphView | null;
  loading?: boolean;
  error?: string;
  ariaLabel?: string;
  emptyMessage?: string;
  className?: string;
  onRetry?: () => void;
  onNodeSelect?: (node: SkillDependencyGraphNode) => void;
};

type SkillDependencyViewportSize = {
  width: number;
  height: number;
};

export function shouldRefitSkillDependencyViewport(
  previous: SkillDependencyViewportSize | null,
  current: SkillDependencyViewportSize,
): boolean {
  if (!previous) return true;
  return Math.abs(previous.width - current.width) >= 0.5
    || Math.abs(previous.height - current.height) >= 0.5;
}

function compareNodes(a: SkillDependencyGraphNode, b: SkillDependencyGraphNode): number {
  return a.layer - b.layer || NODE_TYPE_ORDER[a.type] - NODE_TYPE_ORDER[b.type] || a.id.localeCompare(b.id);
}

function compareEdges(a: SkillDependencyGraphEdge, b: SkillDependencyGraphEdge): number {
  return a.source.localeCompare(b.source) || a.target.localeCompare(b.target) || a.type.localeCompare(b.type) || a.id.localeCompare(b.id);
}

function orderNodesByRelations(
  rawNodes: SkillDependencyGraphNode[],
  rawEdges: SkillDependencyGraphEdge[],
): SkillDependencyGraphNode[] {
  const nodes = [...rawNodes].sort(compareNodes);
  const nodeById = new Map(nodes.map((node) => [node.id, node]));
  const layers = [...new Set(nodes.map((node) => node.layer))].sort((a, b) => a - b);
  const orderById = new Map<string, number>();
  const ordered: SkillDependencyGraphNode[] = [];

  layers.forEach((layer) => {
    const layerNodes = nodes.filter((node) => node.layer === layer);
    const barycenter = (node: SkillDependencyGraphNode): number | null => {
      const connectedIndexes = rawEdges.flatMap((edge) => {
        const counterpartId = edge.source === node.id
          ? edge.target
          : edge.target === node.id ? edge.source : null;
        if (!counterpartId) return [];
        const counterpart = nodeById.get(counterpartId);
        const order = orderById.get(counterpartId);
        return counterpart && counterpart.layer < layer && order != null ? [order] : [];
      });
      if (connectedIndexes.length === 0) return null;
      return connectedIndexes.reduce((sum, value) => sum + value, 0) / connectedIndexes.length;
    };
    layerNodes.sort((a, b) => {
      const aCenter = barycenter(a);
      const bCenter = barycenter(b);
      if (aCenter != null && bCenter != null && aCenter !== bCenter) return aCenter - bCenter;
      if (aCenter != null && bCenter == null) return -1;
      if (aCenter == null && bCenter != null) return 1;
      return compareNodes(a, b);
    });
    layerNodes.forEach((node, index) => orderById.set(node.id, index));
    ordered.push(...layerNodes);
  });

  return ordered;
}

export function prepareSkillDependencyGraph(graph: SkillDependencyGraphView): SkillDependencyGraphView {
  const uniqueNodes = new Map<string, SkillDependencyGraphNode>();
  graph.nodes.forEach((node) => {
    if (node.id && !uniqueNodes.has(node.id)) uniqueNodes.set(node.id, node);
  });
  const nodes = [...uniqueNodes.values()].sort(compareNodes);
  const nodeIds = new Set(nodes.map((node) => node.id));
  const warnings = new Set((graph.warnings ?? []).filter(Boolean));
  const uniqueEdges = new Map<string, SkillDependencyGraphEdge>();

  graph.edges.forEach((edge) => {
    if (!nodeIds.has(edge.source) || !nodeIds.has(edge.target)) {
      warnings.add(`依赖关系 ${edge.id} 的端点不存在，已忽略。`);
      return;
    }
    if (!uniqueEdges.has(edge.id)) uniqueEdges.set(edge.id, edge);
  });

  return {
    ...graph,
    nodes,
    edges: [...uniqueEdges.values()].sort(compareEdges),
    warnings: [...warnings],
  };
}

export function buildSkillDependencyLayout(
  rawNodes: SkillDependencyGraphNode[],
  rawEdges: SkillDependencyGraphEdge[],
): SkillDependencyGraphLayout {
  const nodes = orderNodesByRelations(rawNodes, rawEdges);
  const layers = [...new Set(nodes.map((node) => node.layer))].sort((a, b) => a - b);
  const grouped = new Map<number, SkillDependencyGraphNode[]>();
  layers.forEach((layer) => grouped.set(layer, nodes.filter((node) => node.layer === layer)));
  const maxLayerSize = Math.max(1, ...layers.map((layer) => grouped.get(layer)?.length ?? 0));
  const contentHeight = maxLayerSize * NODE_HEIGHT + Math.max(0, maxLayerSize - 1) * NODE_GAP;
  const height = Math.max(MIN_CANVAS_HEIGHT, contentHeight + CANVAS_PADDING * 2);
  const width = Math.max(
    NODE_WIDTH + CANVAS_PADDING * 2,
    layers.length * NODE_WIDTH + Math.max(0, layers.length - 1) * LAYER_GAP + CANVAS_PADDING * 2,
  );

  const positionedNodes = layers.flatMap((layer, layerIndex) => {
    const layerNodes = grouped.get(layer) ?? [];
    const layerHeight = layerNodes.length * NODE_HEIGHT + Math.max(0, layerNodes.length - 1) * NODE_GAP;
    const startY = (height - layerHeight) / 2;
    return layerNodes.map((node, nodeIndex) => ({
      ...node,
      x: CANVAS_PADDING + layerIndex * (NODE_WIDTH + LAYER_GAP),
      y: startY + nodeIndex * (NODE_HEIGHT + NODE_GAP),
      width: NODE_WIDTH,
      height: NODE_HEIGHT,
    }));
  });
  const nodeById = new Map(positionedNodes.map((node) => [node.id, node]));
  const positionedEdges = [...rawEdges].sort(compareEdges).flatMap((edge) => {
    const source = nodeById.get(edge.source);
    const target = nodeById.get(edge.target);
    if (!source || !target) return [];
    const forward = target.x >= source.x;
    const sourceX = forward ? source.x + source.width : source.x;
    const targetX = forward ? target.x : target.x + target.width;
    const sourceY = source.y + source.height / 2;
    const targetY = target.y + target.height / 2;
    const middleX = (sourceX + targetX) / 2;
    return [{
      ...edge,
      path: `M ${sourceX} ${sourceY} C ${middleX} ${sourceY}, ${middleX} ${targetY}, ${targetX} ${targetY}`,
      labelX: middleX,
      labelY: (sourceY + targetY) / 2 - 7,
    }];
  });

  return { width, height, nodes: positionedNodes, edges: positionedEdges };
}

export function calculateSkillDependencyFitScale(
  viewportWidth: number,
  viewportHeight: number,
  contentWidth: number,
  contentHeight: number,
): number {
  if (viewportWidth <= 0 || viewportHeight <= 0 || contentWidth <= 0 || contentHeight <= 0) return 1;
  const widthRatio = Math.max(0, viewportWidth - 32) / contentWidth;
  const heightRatio = Math.max(0, viewportHeight - 24) / contentHeight;
  return Math.min(1, Math.max(MIN_SCALE, Math.min(widthRatio, heightRatio)));
}

function GraphNodeIcon({ type }: { type: SkillDependencyNodeType }) {
  const iconProps = { size: 17, strokeWidth: 1.8, "aria-hidden": true as const };
  switch (type) {
    case "AGENT":
      return <Bot {...iconProps} />;
    case "WORKFLOW_VERSION":
      return <Workflow {...iconProps} />;
    case "SKILL":
      return <Boxes {...iconProps} />;
    case "SKILL_VERSION":
      return <GitBranch {...iconProps} />;
    case "TOOL":
      return <Wrench {...iconProps} />;
    case "KNOWLEDGE_BASE":
      return <BookOpen {...iconProps} />;
  }
}

function statusLabel(value: string): string {
  const labels: Record<string, string> = {
    ACTIVE: "生效中",
    ARCHIVED: "已归档",
    DISABLED: "已停用",
    DRAFT: "草稿",
    ENABLED: "已启用",
    MISSING: "引用缺失",
    PUBLISHED: "已发布",
    SUPERSEDED: "已替换",
    UNKNOWN: "元数据不可用",
  };
  return labels[value] ?? value;
}

function metadataValue(value: unknown): string {
  if (typeof value === "boolean") return value ? "是" : "否";
  if (value == null || value === "") return "—";
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

function visibleMetadata(node: SkillDependencyGraphNode): Array<[string, string]> {
  return Object.entries(node.metadata ?? {})
    .filter(([key]) => Boolean(METADATA_LABELS[key]))
    .map(([key, value]) => [METADATA_LABELS[key], metadataValue(value)]);
}

function graphSummary(graph: SkillDependencyGraphView): string[] {
  const entries: Array<[number, string]> = [
    [graph.summary.agentCount, "Agent"],
    [graph.summary.workflowVersionCount, "工作流版本"],
    [graph.summary.skillCount, "Skill"],
    [graph.summary.skillVersionCount, "Skill 版本"],
    [graph.summary.toolCount, "Tool"],
    [graph.summary.knowledgeBaseCount, "知识库"],
  ];
  return entries.filter(([count]) => count > 0).map(([count, label]) => `${count} 个 ${label}`);
}

export default function SkillDependencyGraph({
  graph,
  loading = false,
  error = "",
  ariaLabel = "Skill 依赖图",
  emptyMessage = "当前没有可展示的 Skill 依赖。",
  className = "",
  onRetry,
  onNodeSelect,
}: SkillDependencyGraphProps) {
  const prepared = useMemo(() => (graph ? prepareSkillDependencyGraph(graph) : null), [graph]);
  const layout = useMemo(
    () => (prepared ? buildSkillDependencyLayout(prepared.nodes, prepared.edges) : null),
    [prepared],
  );
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(() => prepared?.nodes[0]?.id ?? null);
  const [scale, setScale] = useState(1);
  const viewportRef = useRef<HTMLDivElement>(null);
  const rawMarkerId = useId();
  const markerId = `skill-dag-arrow-${rawMarkerId.replace(/:/g, "")}`;

  useEffect(() => {
    if (!prepared?.nodes.length) {
      setSelectedNodeId(null);
      return;
    }
    setSelectedNodeId((current) => prepared.nodes.some((node) => node.id === current) ? current : prepared.nodes[0].id);
  }, [prepared]);

  const fitGraph = useCallback(() => {
    if (!layout || !viewportRef.current) return;
    setScale(calculateSkillDependencyFitScale(
      viewportRef.current.clientWidth,
      viewportRef.current.clientHeight,
      layout.width,
      layout.height,
    ));
    viewportRef.current.scrollTo({ top: 0, left: 0 });
  }, [layout]);

  useEffect(() => {
    if (!layout || !viewportRef.current) return;
    const viewport = viewportRef.current;
    let previousSize: SkillDependencyViewportSize | null = null;
    const refitWhenBorderBoxChanges = () => {
      const bounds = viewport.getBoundingClientRect();
      const currentSize = { width: bounds.width, height: bounds.height };
      if (!shouldRefitSkillDependencyViewport(previousSize, currentSize)) return;
      previousSize = currentSize;
      fitGraph();
    };
    refitWhenBorderBoxChanges();
    if (typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(refitWhenBorderBoxChanges);
    observer.observe(viewport);
    return () => observer.disconnect();
  }, [fitGraph, layout]);

  const selectedNode = prepared?.nodes.find((node) => node.id === selectedNodeId) ?? prepared?.nodes[0] ?? null;
  const selectedMetadata = selectedNode ? visibleMetadata(selectedNode) : [];
  const selectedRelations = selectedNode && prepared
    ? prepared.edges.filter((edge) => edge.source === selectedNode.id || edge.target === selectedNode.id)
    : [];
  const selectedRelationLabels = selectedNode && prepared
    ? selectedRelations.map((edge) => {
      const outgoing = edge.source === selectedNode.id;
      const counterpartId = outgoing ? edge.target : edge.source;
      const counterpart = prepared.nodes.find((node) => node.id === counterpartId);
      return `${outgoing ? "出向" : "入向"}：${edge.label}（${edge.type}）· ${counterpart?.label ?? counterpartId}`;
    })
    : [];
  const rootClassName = `skill-dag${className ? ` ${className}` : ""}`;

  if (loading) {
    return (
      <section className={rootClassName} aria-label={ariaLabel} aria-busy="true">
        <div className="skill-dag__state" role="status">
          <div className="skill-dag__skeleton" aria-hidden="true"><span /><span /><span /></div>
          <strong>正在加载 Skill 依赖…</strong>
        </div>
      </section>
    );
  }

  if (error) {
    return (
      <section className={rootClassName} aria-label={ariaLabel}>
        <div className="skill-dag__state skill-dag__state--error" role="alert">
          <AlertTriangle size={20} aria-hidden="true" />
          <strong>{error}</strong>
          {onRetry ? <button type="button" onClick={onRetry}>重试</button> : null}
        </div>
      </section>
    );
  }

  if (!prepared || prepared.nodes.length === 0 || !layout) {
    return (
      <section className={rootClassName} aria-label={ariaLabel}>
        <div className="skill-dag__state" role="status">
          <GitBranch size={20} aria-hidden="true" />
          <strong>{emptyMessage}</strong>
          {prepared && prepared.warnings.length > 0 ? (
            <div className="skill-dag__empty-warnings" role="alert">
              {prepared.warnings.slice(0, 3).map((warning) => <p key={warning}>{warning}</p>)}
              {prepared.warnings.length > 3 ? <p>另有 {prepared.warnings.length - 3} 条数据告警。</p> : null}
            </div>
          ) : null}
        </div>
      </section>
    );
  }

  return (
    <section className={rootClassName} aria-label={ariaLabel}>
      <header className="skill-dag__header">
        <div className="skill-dag__summary" aria-label="依赖图摘要">
          {graphSummary(prepared).map((item) => <span key={item}>{item}</span>)}
        </div>
        <div className="skill-dag__toolbar" aria-label="依赖图视图控制">
          <button
            type="button"
            className="cici-product-icon-button skill-dag__icon-button"
            aria-label="缩小依赖图"
            title="缩小"
            disabled={scale <= MIN_SCALE}
            onClick={() => setScale((value) => Math.max(MIN_SCALE, Number((value - 0.15).toFixed(2))))}
          >
            <ZoomOut size={16} aria-hidden="true" />
          </button>
          <button
            type="button"
            className="cici-product-icon-button skill-dag__icon-button"
            aria-label="放大依赖图"
            title="放大"
            disabled={scale >= MAX_SCALE}
            onClick={() => setScale((value) => Math.min(MAX_SCALE, Number((value + 0.15).toFixed(2))))}
          >
            <ZoomIn size={16} aria-hidden="true" />
          </button>
          <button
            type="button"
            className="cici-product-icon-button skill-dag__icon-button"
            aria-label="适配依赖图"
            title="适配视图"
            onClick={fitGraph}
          >
            <Maximize2 size={16} aria-hidden="true" />
          </button>
          <button
            type="button"
            className="cici-product-icon-button skill-dag__icon-button"
            aria-label="重置依赖图缩放"
            title="重置缩放"
            onClick={() => setScale(1)}
          >
            <RotateCcw size={16} aria-hidden="true" />
          </button>
          <span className="skill-dag__scale" aria-live="polite">{Math.round(scale * 100)}%</span>
        </div>
      </header>

      {prepared.warnings.length > 0 ? (
        <div className="skill-dag__warnings" role="status">
          <AlertTriangle size={16} aria-hidden="true" />
          <div>
            {prepared.warnings.slice(0, 3).map((warning) => <p key={warning}>{warning}</p>)}
            {prepared.warnings.length > 3 ? <p>另有 {prepared.warnings.length - 3} 条数据告警。</p> : null}
          </div>
        </div>
      ) : null}

      <div className="skill-dag__viewport" ref={viewportRef} role="group" aria-label="依赖图画布">
        <div
          className="skill-dag__scaled-stage"
          style={{ width: layout.width * scale, height: layout.height * scale }}
        >
          <div
            className="skill-dag__stage"
            style={{
              width: layout.width,
              height: layout.height,
              transform: `scale(${scale})`,
            }}
          >
            <svg
              className="skill-dag__edges"
              width={layout.width}
              height={layout.height}
              viewBox={`0 0 ${layout.width} ${layout.height}`}
              aria-hidden="true"
            >
              <defs>
                <marker id={markerId} markerWidth="7" markerHeight="7" refX="6" refY="3.5" orient="auto">
                  <path d="M 0 0 L 7 3.5 L 0 7 z" />
                </marker>
              </defs>
              {layout.edges.map((edge) => (
                <g key={edge.id} className="skill-dag__edge">
                  <path d={edge.path} markerEnd={`url(#${markerId})`} />
                  {layout.edges.length <= 24 ? <text x={edge.labelX} y={edge.labelY} textAnchor="middle">{edge.label}</text> : null}
                </g>
              ))}
            </svg>

            {layout.nodes.map((node) => {
              const selected = node.id === selectedNode?.id;
              return (
                <button
                  type="button"
                  key={node.id}
                  className={`skill-dag__node${selected ? " is-selected" : ""}`}
                  data-node-type={node.type}
                  aria-pressed={selected}
                  aria-label={`${NODE_TYPE_LABEL[node.type]}：${node.label}${node.detail ? `，${node.detail}` : ""}`}
                  title={`${node.label}${node.detail ? ` · ${node.detail}` : ""}`}
                  style={{
                    left: node.x,
                    top: node.y,
                    width: node.width,
                    height: node.height,
                  } as CSSProperties}
                  onClick={() => {
                    setSelectedNodeId(node.id);
                    onNodeSelect?.(node);
                  }}
                >
                  <span className="skill-dag__node-icon"><GraphNodeIcon type={node.type} /></span>
                  <span className="skill-dag__node-copy">
                    <small>{NODE_TYPE_LABEL[node.type]}</small>
                    <strong>{node.label}</strong>
                    <span>{node.detail || statusLabel(node.status)}</span>
                  </span>
                </button>
              );
            })}
          </div>
        </div>
      </div>

      {selectedNode ? (
        <aside className="skill-dag__detail" aria-live="polite" aria-label="节点详情">
          <div className="skill-dag__detail-heading" data-node-type={selectedNode.type}>
            <span className="skill-dag__detail-icon" data-node-type={selectedNode.type}>
              <GraphNodeIcon type={selectedNode.type} />
            </span>
            <div>
              <small>{NODE_TYPE_LABEL[selectedNode.type]}</small>
              <strong>{selectedNode.label}</strong>
              <span>{selectedNode.detail || "暂无补充说明"}</span>
            </div>
          </div>
          <dl className="skill-dag__detail-grid">
            <div><dt>状态</dt><dd>{statusLabel(selectedNode.status) || "—"}</dd></div>
            {selectedMetadata.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}
            <div className="skill-dag__detail-relations">
              <dt>直接关系</dt>
              <dd>
                {selectedRelationLabels.length > 0 ? (
                  <ul className="skill-dag__relation-list">
                    {selectedRelationLabels.map((relation) => <li key={relation}>{relation}</li>)}
                  </ul>
                ) : "暂无"}
              </dd>
            </div>
          </dl>
        </aside>
      ) : null}
    </section>
  );
}
