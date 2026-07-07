import { useMemo, useState } from "react";

type ViewKey = "objects" | "detail" | "tags" | "config" | "dashboard";
type TagSource = "ai" | "manual";
type TagStatus = "active" | "disabled" | "pending_review";

type PortraitTag = {
  id: string;
  name: string;
  categoryId: string;
  description: string;
  source: TagSource;
  status: TagStatus;
  usageCount: number;
  createdBy: string;
  lastUsedAt: string;
};

type ObjectTag = {
  tagId: string;
  source: TagSource;
  confidence?: number;
  reason?: string;
  evidenceIds?: string[];
  createdAt: string;
  createdBy?: string;
};

type PortraitObject = {
  id: string;
  name: string;
  maskedName: string;
  phone: string;
  region: string;
  source: string;
  topic: string;
  valueRange: string;
  preference: string;
  stage: string;
  stageTone: "info" | "warning" | "success" | "danger";
  owner: string;
  team: string;
  lastTouchAt: string;
  lastTouchResult: string;
  createdAt: string;
  status: "active" | "inactive";
  tags: ObjectTag[];
};

const views: Array<{ key: ViewKey; label: string; detail: string }> = [
  { key: "objects", label: "对象列表", detail: "筛选、批量打标、进入画像" },
  { key: "detail", label: "画像详情", detail: "智能标签、证据、行动建议" },
  { key: "tags", label: "标签库", detail: "标签体系与候选审核" },
  { key: "config", label: "AI 配置", detail: "策略阈值、触发器、提示词" },
  { key: "dashboard", label: "运营看板", detail: "覆盖率、准确率、采纳率" },
];

const categories = [
  { id: "cat-1", name: "对象属性", description: "基础身份、角色与静态特征", color: "#9f7a35" },
  { id: "cat-2", name: "偏好意向", description: "主题偏好、预算与方案关注点", color: "#7d6a42" },
  { id: "cat-3", name: "决策阶段", description: "业务旅程中的推进阶段", color: "#6b7350" },
  { id: "cat-4", name: "风险信号", description: "需要人工关注的异常或流失风险", color: "#9a5f38" },
  { id: "cat-5", name: "机会信号", description: "高价值转化、复购或扩展机会", color: "#5f7468" },
];

const tags: PortraitTag[] = [
  { id: "tag-001", name: "高价值对象", categoryId: "cat-1", description: "对象具备高价值或高优先级特征", source: "manual", status: "active", usageCount: 42, createdBy: "运营管理员", lastUsedAt: "2026-07-05 16:20" },
  { id: "tag-002", name: "首次接触", categoryId: "cat-3", description: "新进入对象池，尚未形成稳定画像", source: "manual", status: "active", usageCount: 68, createdBy: "运营管理员", lastUsedAt: "2026-07-05 11:08" },
  { id: "tag-003", name: "预算敏感", categoryId: "cat-2", description: "对成本、周期或资源投入高度敏感", source: "ai", status: "active", usageCount: 39, createdBy: "系统", lastUsedAt: "2026-07-05 15:40" },
  { id: "tag-004", name: "方案对比中", categoryId: "cat-2", description: "正在比较多个方案，决策仍有摇摆", source: "ai", status: "active", usageCount: 35, createdBy: "系统", lastUsedAt: "2026-07-05 14:10" },
  { id: "tag-005", name: "临近转化", categoryId: "cat-3", description: "已认可方案，接近确认或签收节点", source: "manual", status: "active", usageCount: 18, createdBy: "运营管理员", lastUsedAt: "2026-07-05 10:20" },
  { id: "tag-006", name: "响应放缓", categoryId: "cat-4", description: "近期互动频率下降，需要补充触达", source: "ai", status: "active", usageCount: 21, createdBy: "系统", lastUsedAt: "2026-07-04 18:00" },
  { id: "tag-007", name: "周期窗口明确", categoryId: "cat-5", description: "对象明确表达了推进时间窗口", source: "ai", status: "active", usageCount: 16, createdBy: "系统", lastUsedAt: "2026-07-05 09:30" },
  { id: "tag-008", name: "旧版 VIP", categoryId: "cat-1", description: "已停用，合并至高价值对象", source: "manual", status: "disabled", usageCount: 5, createdBy: "运营管理员", lastUsedAt: "2026-06-16 12:00" },
];

const portraitObjects: PortraitObject[] = [
  {
    id: "OBJ-2026-001",
    name: "王建国",
    maskedName: "王先生",
    phone: "138****5678",
    region: "华北一区",
    source: "官网表单",
    topic: "标准方案 A",
    valueRange: "45-55 万",
    preference: "低月度投入",
    stage: "方案沟通中",
    stageTone: "warning",
    owner: "李文博",
    team: "华北运营组",
    lastTouchAt: "2026-07-05 14:30",
    lastTouchResult: "对象表示月底预算确认后推进，需要更低的分期压力。",
    createdAt: "2026-06-15 10:00",
    status: "active",
    tags: [
      { tagId: "tag-003", source: "ai", confidence: 0.92, reason: "最近沟通中明确提到月度投入最好控制在可承受区间内。", evidenceIds: ["rec-001-03"], createdAt: "2026-07-05 15:00" },
      { tagId: "tag-004", source: "ai", confidence: 0.88, reason: "对象提到正在对比两个替代方案。", evidenceIds: ["rec-001-03"], createdAt: "2026-07-05 15:00" },
      { tagId: "tag-007", source: "ai", confidence: 0.86, reason: "对象把推进窗口锁定在月底预算确认后。", evidenceIds: ["rec-001-03"], createdAt: "2026-07-05 15:00" },
      { tagId: "tag-001", source: "manual", createdBy: "李文博", createdAt: "2026-06-20 16:30" },
    ],
  },
  {
    id: "OBJ-2026-002",
    name: "张丽华",
    maskedName: "张女士",
    phone: "139****3421",
    region: "华东一区",
    source: "渠道推送",
    topic: "标准方案 C",
    valueRange: "30-38 万",
    preference: "材料完整",
    stage: "临近转化",
    stageTone: "success",
    owner: "周雨",
    team: "华东运营组",
    lastTouchAt: "2026-07-05 10:00",
    lastTouchResult: "对象认可当前方案，正在准备确认材料。",
    createdAt: "2026-06-01 09:00",
    status: "active",
    tags: [
      { tagId: "tag-005", source: "ai", confidence: 0.91, reason: "已确认方案并进入材料准备阶段。", evidenceIds: ["rec-002-02"], createdAt: "2026-07-05 10:30" },
      { tagId: "tag-001", source: "ai", confidence: 0.84, reason: "对象历史互动完整，当前价值评分较高。", evidenceIds: ["rec-002-01"], createdAt: "2026-07-02 12:00" },
    ],
  },
  {
    id: "OBJ-2026-003",
    name: "陈志远",
    maskedName: "陈先生",
    phone: "137****8865",
    region: "华南一区",
    source: "热线接入",
    topic: "增强方案 E",
    valueRange: "50-60 万",
    preference: "效率优先",
    stage: "方案沟通中",
    stageTone: "warning",
    owner: "李文博",
    team: "华南运营组",
    lastTouchAt: "2026-07-04 11:00",
    lastTouchResult: "对象关注总成本与交付周期，要求补充对比说明。",
    createdAt: "2026-06-10 14:00",
    status: "active",
    tags: [
      { tagId: "tag-004", source: "ai", confidence: 0.9, reason: "沟通中多次询问替代方案的成本差异。", evidenceIds: ["rec-003-02"], createdAt: "2026-07-04 11:20" },
      { tagId: "tag-003", source: "ai", confidence: 0.86, reason: "主动追问整体投入和回报周期。", evidenceIds: ["rec-003-02"], createdAt: "2026-07-04 11:20" },
    ],
  },
  {
    id: "OBJ-2026-004",
    name: "刘晓芬",
    maskedName: "刘女士",
    phone: "136****1290",
    region: "华南一区",
    source: "活动采集",
    topic: "入门方案 B",
    valueRange: "18-25 万",
    preference: "先看样例",
    stage: "首次接触",
    stageTone: "info",
    owner: "周雨",
    team: "华南运营组",
    lastTouchAt: "2026-07-03 15:00",
    lastTouchResult: "首次沟通完成，对基础能力感兴趣。",
    createdAt: "2026-07-02 16:00",
    status: "active",
    tags: [
      { tagId: "tag-002", source: "ai", confidence: 0.88, reason: "新进入对象池，仅完成首次沟通。", evidenceIds: ["rec-004-01"], createdAt: "2026-07-03 15:30" },
      { tagId: "tag-003", source: "ai", confidence: 0.81, reason: "提到希望先控制投入并验证效果。", evidenceIds: ["rec-004-01"], createdAt: "2026-07-03 15:30" },
    ],
  },
  {
    id: "OBJ-2026-005",
    name: "赵明轩",
    maskedName: "赵先生",
    phone: "135****7788",
    region: "西南一区",
    source: "现场活动",
    topic: "扩展方案 G",
    valueRange: "60-80 万",
    preference: "完整托管",
    stage: "响应放缓",
    stageTone: "danger",
    owner: "何珊",
    team: "西南运营组",
    lastTouchAt: "2026-07-01 09:30",
    lastTouchResult: "近三次触达均未回复，需要调整下一步话术。",
    createdAt: "2026-06-20 11:00",
    status: "active",
    tags: [
      { tagId: "tag-006", source: "ai", confidence: 0.89, reason: "近三次触达无回复，互动频率明显下降。", evidenceIds: ["rec-005-03"], createdAt: "2026-07-01 10:00" },
      { tagId: "tag-001", source: "manual", createdBy: "何珊", createdAt: "2026-06-21 10:30" },
    ],
  },
];

const pendingTags = [
  { id: "pt-001", name: "月底窗口", categoryId: "cat-5", reason: "多条记录提到月底预算确认后再推进", count: 4, firstGeneratedAt: "2026-07-05 15:30", similar: "" },
  { id: "pt-002", name: "方案横评", categoryId: "cat-2", reason: "对象正在主动比较替代方案的费用和周期", count: 5, firstGeneratedAt: "2026-07-04 10:00", similar: "方案对比中 78%" },
  { id: "pt-003", name: "材料待补", categoryId: "cat-3", reason: "材料缺项导致推进动作暂停", count: 2, firstGeneratedAt: "2026-07-03 11:00", similar: "" },
];

const records = [
  { id: "rec-001-03", type: "电话", channel: "语音", time: "2026-07-05 14:30", operator: "李文博", summary: "对象确认仍在比较两个替代方案，希望月底预算确认后再推进。", highlight: true },
  { id: "rec-001-02", type: "企微", channel: "消息", time: "2026-06-28 16:00", operator: "李文博", summary: "发送方案 A 的概要与投入拆分，对象追问月度投入压力。" },
  { id: "rec-001-01", type: "表单", channel: "官网", time: "2026-06-15 10:00", operator: "系统", summary: "对象通过官网表单提交需求，选择标准方案并留下预算范围。" },
];

const topTags = ["预算敏感", "方案对比中", "首次接触", "高价值对象", "临近转化", "响应放缓"];

function tagById(tagId: string) {
  return tags.find((tag) => tag.id === tagId) ?? tags[0];
}

function categoryById(categoryId: string) {
  return categories.find((category) => category.id === categoryId) ?? categories[0];
}

function stageClass(tone: PortraitObject["stageTone"]) {
  return `zhiwei-demo-status is-${tone}`;
}

function metricBars(values: number[]) {
  const max = Math.max(...values);
  return values.map((value) => Math.round((value / max) * 100));
}

export function ZhiweiPortraitDemoApp() {
  const [activeView, setActiveView] = useState<ViewKey>("objects");
  const [selectedObjectId, setSelectedObjectId] = useState(portraitObjects[0].id);
  const [selectedCategory, setSelectedCategory] = useState("all");
  const [tagTab, setTagTab] = useState<"list" | "review" | "category">("list");

  const activeObject = useMemo(
    () => portraitObjects.find((item) => item.id === selectedObjectId) ?? portraitObjects[0],
    [selectedObjectId],
  );
  const filteredTags = selectedCategory === "all" ? tags : tags.filter((tag) => tag.categoryId === selectedCategory);
  const coverageBars = metricBars([72, 76, 81, 84, 88, 91, 93]);

  function openDetail(objectId: string) {
    setSelectedObjectId(objectId);
    setActiveView("detail");
  }

  return (
    <section className="zhiwei-demo" aria-label="知微画像 AI 应用高保真 demo">
      <header className="zhiwei-demo-shell-head">
        <div>
          <p>AI 智能打标与画像引擎</p>
          <h3>知微画像</h3>
          <span>多源事实抽取、智能标签、画像证据和下一步行动建议在一个工作台内闭环。</span>
        </div>
        <div className="zhiwei-demo-head-actions" aria-label="应用操作">
          <button type="button">同步来源</button>
          <button type="button" className="is-primary">批量 AI 打标</button>
        </div>
      </header>

      <div className="zhiwei-demo-layout">
        <nav className="zhiwei-demo-side" aria-label="知微画像模块">
          {views.map((view) => (
            <button
              key={view.key}
              type="button"
              className={activeView === view.key ? "is-active" : ""}
              onClick={() => setActiveView(view.key)}
            >
              <span>{view.label}</span>
              <small>{view.detail}</small>
            </button>
          ))}
        </nav>

        <div className="zhiwei-demo-main">
          {activeView === "objects" ? (
            <ObjectsView activeObjectId={selectedObjectId} onOpenDetail={openDetail} />
          ) : null}
          {activeView === "detail" ? <DetailView object={activeObject} /> : null}
          {activeView === "tags" ? (
            <TagLibraryView
              selectedCategory={selectedCategory}
              onSelectCategory={setSelectedCategory}
              filteredTags={filteredTags}
              tagTab={tagTab}
              onChangeTab={setTagTab}
            />
          ) : null}
          {activeView === "config" ? <ConfigView /> : null}
          {activeView === "dashboard" ? <DashboardView coverageBars={coverageBars} /> : null}
        </div>
      </div>
    </section>
  );
}

function ObjectsView({ activeObjectId, onOpenDetail }: { activeObjectId: string; onOpenDetail: (id: string) => void }) {
  return (
    <div className="zhiwei-demo-page">
      <div className="zhiwei-demo-page-head">
        <div>
          <h4>对象列表</h4>
          <span>管理全部画像对象，支持按来源、阶段、团队和标签组合筛选。</span>
        </div>
        <div className="zhiwei-demo-counter">活跃对象: {portraitObjects.length}</div>
      </div>

      <div className="zhiwei-demo-filter">
        <label>
          <span>搜索</span>
          <input value="王先生 / 预算敏感" readOnly />
        </label>
        <label>
          <span>来源渠道</span>
          <select defaultValue="all">
            <option value="all">全部来源</option>
          </select>
        </label>
        <label>
          <span>对象阶段</span>
          <select defaultValue="all">
            <option value="all">全部阶段</option>
          </select>
        </label>
        <button type="button" className="zhiwei-demo-tag-filter">标签筛选 · 已选 3 个 · 或</button>
      </div>

      <div className="zhiwei-demo-table-wrap">
        <table className="zhiwei-demo-table">
          <thead>
            <tr>
              <th aria-label="选择"><input type="checkbox" aria-label="选择全部对象" defaultChecked /></th>
              <th>画像对象</th>
              <th>来源</th>
              <th>主题</th>
              <th>价值区间</th>
              <th>阶段</th>
              <th>AI 标签</th>
              <th>负责人</th>
              <th>最后触达</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {portraitObjects.map((object) => (
              <tr key={object.id} className={object.id === activeObjectId ? "is-selected" : ""}>
                <td><input type="checkbox" aria-label={`选择${object.maskedName}`} defaultChecked={object.id !== "OBJ-2026-004"} /></td>
                <td>
                  <button type="button" className="zhiwei-demo-object-cell" onClick={() => onOpenDetail(object.id)}>
                    <span className="zhiwei-demo-avatar">{object.name.slice(0, 1)}</span>
                    <span>
                      <strong>{object.maskedName}</strong>
                      <small>{object.phone}</small>
                    </span>
                  </button>
                </td>
                <td>{object.source}</td>
                <td>{object.topic}</td>
                <td>{object.valueRange}</td>
                <td><span className={stageClass(object.stageTone)}>{object.stage}</span></td>
                <td>
                  <div className="zhiwei-demo-tags-inline">
                    {object.tags.slice(0, 3).map((item) => {
                      const tag = tagById(item.tagId);
                      return <span key={item.tagId} className={item.source === "ai" ? "is-ai" : "is-manual"}>{tag.name}</span>;
                    })}
                    {object.tags.length > 3 ? <small>+{object.tags.length - 3}</small> : null}
                  </div>
                </td>
                <td>{object.owner}</td>
                <td><span className="zhiwei-demo-muted">{object.lastTouchAt}</span></td>
                <td><button type="button" className="zhiwei-demo-text-action" onClick={() => onOpenDetail(object.id)}>详情</button></td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="zhiwei-demo-table-foot">
          <span>共 {portraitObjects.length} 条对象记录</span>
          <span>上一页 1 2 3 下一页</span>
        </div>
      </div>

      <div className="zhiwei-demo-batch">
        <div>
          <strong>批量 AI 打标签</strong>
          <span>已选择 4 条对象，系统将分析画像字段、触达记录和历史反馈，生成智能标签并返回依据。</span>
        </div>
        <div className="zhiwei-demo-progress">
          <span style={{ width: "72%" }} />
        </div>
        <button type="button">查看本次结果</button>
      </div>
    </div>
  );
}

function DetailView({ object }: { object: PortraitObject }) {
  return (
    <div className="zhiwei-demo-page">
      <div className="zhiwei-demo-back">返回对象列表 <span>{object.id}</span><span>{object.status === "active" ? "活跃" : "非活跃"}</span></div>
      <section className="zhiwei-demo-info">
        <div className="zhiwei-demo-info-main">
          <span className="zhiwei-demo-avatar is-large">{object.name.slice(0, 1)}</span>
          <div>
            <div className="zhiwei-demo-info-title">
              <h4>{object.maskedName}</h4>
              <span className={stageClass(object.stageTone)}>{object.stage}</span>
              <span>{object.team}</span>
            </div>
            <dl className="zhiwei-demo-info-grid">
              <div><dt>目标主题</dt><dd>{object.topic}</dd></div>
              <div><dt>价值区间</dt><dd>{object.valueRange}</dd></div>
              <div><dt>偏好</dt><dd>{object.preference}</dd></div>
              <div><dt>来源渠道</dt><dd>{object.source}</dd></div>
              <div><dt>区域</dt><dd>{object.region}</dd></div>
              <div><dt>创建时间</dt><dd>{object.createdAt}</dd></div>
            </dl>
          </div>
        </div>
        <aside>
          <span>业务负责人</span><strong>{object.owner}</strong>
          <span>最后触达</span><strong>{object.lastTouchAt}</strong>
          <p>{object.lastTouchResult}</p>
        </aside>
      </section>

      <div className="zhiwei-demo-detail-grid">
        <div className="zhiwei-demo-detail-left">
          <section className="zhiwei-demo-panel">
            <div className="zhiwei-demo-section-title">
              <h4>智能标签</h4>
              <span>{object.tags.length} 个</span>
              <button type="button">重新分析</button>
            </div>
            <div className="zhiwei-demo-tag-bar">
              {object.tags.map((item) => {
                const tag = tagById(item.tagId);
                const category = categoryById(tag.categoryId);
                return (
                  <span key={item.tagId} style={{ borderColor: `${category.color}66`, color: category.color }}>
                    {item.source === "ai" ? "AI" : "人工"} · {tag.name}
                    {item.confidence ? <small>{Math.round(item.confidence * 100)}%</small> : null}
                  </span>
                );
              })}
              <button type="button">添加标签</button>
              <button type="button">3 个待审核</button>
            </div>
            <div className="zhiwei-demo-evidence">
              {object.tags.filter((item) => item.reason).map((item) => (
                <div key={`${item.tagId}-evidence`}>
                  <strong>{tagById(item.tagId).name}</strong>
                  <span>{item.reason}</span>
                  <small>引用记录: {item.evidenceIds?.join("、")} · {item.createdAt}</small>
                </div>
              ))}
            </div>
          </section>

          <section className="zhiwei-demo-panel">
            <div className="zhiwei-demo-section-title">
              <h4>触达历史</h4>
              <span>{records.length} 条</span>
              <button type="button">添加记录</button>
            </div>
            <div className="zhiwei-demo-timeline">
              {records.map((record) => (
                <article key={record.id} className={record.highlight ? "is-highlight" : ""}>
                  <i />
                  <div>
                    <header>
                      <strong>{record.type}</strong>
                      <span>{record.channel}</span>
                      <span>{record.time}</span>
                      <span>操作人: {record.operator}</span>
                    </header>
                    <p>{record.summary}</p>
                    {record.highlight ? <small>本条记录触发了标签更新</small> : null}
                  </div>
                </article>
              ))}
            </div>
          </section>
        </div>

        <aside className="zhiwei-demo-nba">
          <div className="zhiwei-demo-section-title">
            <h4>AI 行动建议</h4>
            <button type="button">重新生成</button>
          </div>
          <div className="zhiwei-demo-primary-suggestion">
            <span>主建议 · 48 小时内</span>
            <h5>发送“低投入分期 + 月底确认”版本的方案摘要</h5>
            <p>理由：对象同时具备预算敏感、方案对比中、周期窗口明确三个标签，当前最需要降低决策摩擦并锁定下一次确认节点。</p>
            <dl>
              <div><dt>置信度</dt><dd>91%</dd></div>
              <div><dt>预计影响</dt><dd>推进概率 +18%</dd></div>
            </dl>
            <div>
              <button type="button" className="is-primary">采纳并创建任务</button>
              <button type="button">忽略</button>
              <button type="button">反馈无用</button>
            </div>
          </div>
          <div className="zhiwei-demo-alt-suggestions">
            <h5>其他建议</h5>
            <button type="button">补充对比表，突出总投入差异</button>
            <button type="button">预约月底前的二次确认触达</button>
          </div>
          <div className="zhiwei-demo-history">
            <h5>历史建议</h5>
            <span>2026-06-28 已发送方案摘要，未采纳</span>
            <span>2026-06-20 已补充偏好问卷，已采纳</span>
          </div>
        </aside>
      </div>
    </div>
  );
}

function TagLibraryView({
  selectedCategory,
  onSelectCategory,
  filteredTags,
  tagTab,
  onChangeTab,
}: {
  selectedCategory: string;
  onSelectCategory: (id: string) => void;
  filteredTags: PortraitTag[];
  tagTab: "list" | "review" | "category";
  onChangeTab: (tab: "list" | "review" | "category") => void;
}) {
  return (
    <div className="zhiwei-demo-page">
      <div className="zhiwei-demo-page-head">
        <div><h4>标签库管理</h4><span>维护标签分类体系，管理 AI 候选标签审核。</span></div>
        <div className="zhiwei-demo-head-actions"><button type="button">批量导入</button><button type="button">导出</button><button type="button" className="is-primary">新增标签</button></div>
      </div>
      <div className="zhiwei-demo-tabs">
        <button type="button" className={tagTab === "list" ? "is-active" : ""} onClick={() => onChangeTab("list")}>标签列表</button>
        <button type="button" className={tagTab === "review" ? "is-active" : ""} onClick={() => onChangeTab("review")}>待审核 {pendingTags.length}</button>
        <button type="button" className={tagTab === "category" ? "is-active" : ""} onClick={() => onChangeTab("category")}>分类管理</button>
      </div>

      {tagTab === "list" ? (
        <div className="zhiwei-demo-tag-layout">
          <aside className="zhiwei-demo-cat-tree">
            <button type="button" className={selectedCategory === "all" ? "is-active" : ""} onClick={() => onSelectCategory("all")}>
              <span>全部分类</span><small>{tags.length}</small>
            </button>
            {categories.map((category) => (
              <button key={category.id} type="button" className={selectedCategory === category.id ? "is-active" : ""} onClick={() => onSelectCategory(category.id)}>
                <i style={{ background: category.color }} /><span>{category.name}</span><small>{tags.filter((tag) => tag.categoryId === category.id).length}</small>
              </button>
            ))}
          </aside>
          <div className="zhiwei-demo-table-wrap">
            <div className="zhiwei-demo-table-tools">
              <input value="搜索标签名称" readOnly />
              <select defaultValue="active"><option value="active">启用</option></select>
              <select defaultValue="all"><option value="all">全部来源</option></select>
              <button type="button">合并选中</button>
            </div>
            <table className="zhiwei-demo-table is-compact">
              <thead><tr><th>标签名</th><th>分类</th><th>描述</th><th>来源</th><th>状态</th><th>使用计数</th><th>最近使用</th><th>操作</th></tr></thead>
              <tbody>
                {filteredTags.map((tag) => (
                  <tr key={tag.id}>
                    <td><strong>{tag.name}</strong>{tag.source === "ai" ? <span className="zhiwei-demo-mini-badge">AI</span> : null}</td>
                    <td>{categoryById(tag.categoryId).name}</td>
                    <td>{tag.description}</td>
                    <td>{tag.source === "ai" ? "AI" : "人工"}</td>
                    <td><span className={tag.status === "disabled" ? "zhiwei-demo-status is-info" : "zhiwei-demo-status is-success"}>{tag.status === "disabled" ? "已停用" : "启用"}</span></td>
                    <td><strong>{tag.usageCount}</strong></td>
                    <td><span className="zhiwei-demo-muted">{tag.lastUsedAt}</span></td>
                    <td><button type="button" className="zhiwei-demo-text-action">编辑</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ) : null}

      {tagTab === "review" ? (
        <div className="zhiwei-demo-table-wrap">
          <div className="zhiwei-demo-table-tools"><strong>AI 候选新标签审核队列</strong><button type="button" className="is-primary">批量通过</button><button type="button">批量拒绝</button></div>
          <table className="zhiwei-demo-table">
            <thead><tr><th>候选标签名</th><th>建议分类</th><th>生成理由</th><th>命中对象数</th><th>首次生成</th><th>操作</th></tr></thead>
            <tbody>
              {pendingTags.map((tag) => (
                <tr key={tag.id}>
                  <td><strong>{tag.name}</strong>{tag.similar ? <span className="zhiwei-demo-mini-badge">有相似</span> : null}</td>
                  <td>{categoryById(tag.categoryId).name}</td>
                  <td>{tag.reason}</td>
                  <td><strong>{tag.count}</strong></td>
                  <td>{tag.firstGeneratedAt}</td>
                  <td><button type="button" className="zhiwei-demo-text-action">通过</button><button type="button" className="zhiwei-demo-text-action">修改后通过</button><button type="button" className="zhiwei-demo-text-action">拒绝</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}

      {tagTab === "category" ? (
        <div className="zhiwei-demo-table-wrap">
          <table className="zhiwei-demo-table">
            <thead><tr><th>分类名称</th><th>说明</th><th>标签数量</th><th>排序</th><th>操作</th></tr></thead>
            <tbody>{categories.map((category, index) => (
              <tr key={category.id}><td><i className="zhiwei-demo-cat-dot" style={{ background: category.color }} /> <strong>{category.name}</strong></td><td>{category.description}</td><td>{tags.filter((tag) => tag.categoryId === category.id).length}</td><td>{index + 1}</td><td><button type="button" className="zhiwei-demo-text-action">编辑</button></td></tr>
            ))}</tbody>
          </table>
        </div>
      ) : null}
    </div>
  );
}

function ConfigView() {
  return (
    <div className="zhiwei-demo-page">
      <div className="zhiwei-demo-page-head">
        <div><h4>AI 配置</h4><span>配置自动打标与行动建议的策略参数、触发方式与提示词模板。</span></div>
        <button type="button" className="zhiwei-demo-save">保存配置</button>
      </div>
      <div className="zhiwei-demo-config-grid">
        <div className="zhiwei-demo-config-main">
          <ConfigSection title="功能总开关" rows={[["自动打标总开关", "开启"], ["行动建议总开关", "开启"]]} />
          <section className="zhiwei-demo-panel">
            <h4>自动打标策略</h4>
            <div className="zhiwei-demo-config-row"><span><strong>新标签治理策略</strong><small>大模型生成的候选新标签如何入库</small></span><b>先审核</b><b>自动入库</b></div>
            <SliderRow label="打标置信度阈值" detail="仅置信度大于等于阈值的标签写回对象" value="0.85" width="78%" />
            <SliderRow label="相似标签判定阈值" detail="超过此值时提示人工确认或归并" value="0.80" width="64%" />
            <div className="zhiwei-demo-config-row"><span><strong>单对象 AI 标签上限</strong><small>超出时保留置信度最高的标签</small></span><b>12</b></div>
          </section>
          <ConfigSection title="触发方式" rows={[["新建对象触发", "开启"], ["触达记录更新触发", "开启"], ["定时兜底扫描", "开启"]]} />
          <section className="zhiwei-demo-panel">
            <h4>AI 提示词模板</h4>
            <div className="zhiwei-demo-prompts">
              <article><strong>智能标签分析提示词</strong><span>v4 · 2026-07-05 12:30</span><pre>读取对象字段、触达历史、已有标签与候选标签库，输出标签、置信度、依据记录...</pre></article>
              <article><strong>行动建议生成提示词</strong><span>v3 · 2026-07-04 17:10</span><pre>基于对象画像、风险信号与阶段状态，生成主建议、备选建议、预计影响...</pre></article>
            </div>
          </section>
        </div>
        <aside className="zhiwei-demo-config-side">
          <section className="zhiwei-demo-panel">
            <h4>模型配置</h4>
            <div className="zhiwei-demo-model-select">通用画像模型 v2 · 推荐</div>
          </section>
          <section className="zhiwei-demo-panel">
            <h4>配置变更记录</h4>
            {["提高打标置信度阈值到 0.85", "新增定时兜底扫描", "更新行动建议提示词 v3"].map((item) => (
              <div key={item} className="zhiwei-demo-log"><i /><span>{item}</span><small>运营管理员 · 2026-07-05</small></div>
            ))}
          </section>
        </aside>
      </div>
    </div>
  );
}

function ConfigSection({ title, rows }: { title: string; rows: Array<[string, string]> }) {
  return (
    <section className="zhiwei-demo-panel">
      <h4>{title}</h4>
      {rows.map(([label, value]) => (
        <div key={label} className="zhiwei-demo-config-row">
          <span><strong>{label}</strong><small>关闭后不再触发对应的 AI 任务</small></span>
          <b>{value}</b>
        </div>
      ))}
    </section>
  );
}

function SliderRow({ label, detail, value, width }: { label: string; detail: string; value: string; width: string }) {
  return (
    <div className="zhiwei-demo-config-row is-slider">
      <span><strong>{label}</strong><small>{detail}</small></span>
      <div className="zhiwei-demo-slider"><i style={{ width }} /></div>
      <b>{value}</b>
    </div>
  );
}

function DashboardView({ coverageBars }: { coverageBars: number[] }) {
  return (
    <div className="zhiwei-demo-page">
      <div className="zhiwei-demo-page-head">
        <div><h4>运营看板</h4><span>监控 AI 打标与行动建议的运营质量与效果。</span></div>
        <button type="button">近 7 天</button>
      </div>
      <div className="zhiwei-demo-metrics">
        <Metric label="打标覆盖率" value="93.4%" trend="+5.8%" />
        <Metric label="标签准确率" value="91.2%" trend="+2.1%" />
        <Metric label="建议采纳率" value="68.7%" trend="+3.5%" />
        <Metric label="待审候选" value="23" trend="-6" />
      </div>
      <div className="zhiwei-demo-dashboard-grid">
        <section className="zhiwei-demo-panel is-wide">
          <h4>覆盖率趋势</h4>
          <div className="zhiwei-demo-line-chart">{coverageBars.map((height, index) => <span key={index} style={{ height: `${height}%` }} />)}</div>
        </section>
        <section className="zhiwei-demo-panel">
          <h4>标签分类分布</h4>
          {categories.map((category, index) => (
            <div key={category.id} className="zhiwei-demo-dist"><span>{category.name}</span><i><b style={{ width: `${82 - index * 10}%`, background: category.color }} /></i></div>
          ))}
        </section>
        <section className="zhiwei-demo-panel">
          <h4>标签使用 TOP 6</h4>
          <div className="zhiwei-demo-top-tags">{topTags.map((tag, index) => <span key={tag}>{index + 1}. {tag}</span>)}</div>
        </section>
        <section className="zhiwei-demo-panel">
          <h4>团队表现</h4>
          <table className="zhiwei-demo-table is-compact">
            <tbody>
              {["华北运营组", "华东运营组", "华南运营组"].map((team, index) => (
                <tr key={team}><td>{team}</td><td>{90 - index * 4}% 覆盖</td><td>{72 - index * 5}% 采纳</td></tr>
              ))}
            </tbody>
          </table>
        </section>
        <section className="zhiwei-demo-panel">
          <h4>任务健康度</h4>
          <div className="zhiwei-demo-health"><strong>18</strong><span>个对象等待增量分析</span><strong>3</strong><span>个候选标签待重点复核</span></div>
        </section>
      </div>
    </div>
  );
}

function Metric({ label, value, trend }: { label: string; value: string; trend: string }) {
  return (
    <div className="zhiwei-demo-metric">
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{trend} 较上周</small>
    </div>
  );
}
