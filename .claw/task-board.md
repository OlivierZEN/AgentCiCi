---
kind: task-board
version: 3
updated_at: 2026-05-03T13:31:57Z
updated_by: ai
status: active
board_status: active
---

# Task Board

## Task Cards

### TASK-037 External agent skill package optimization loop

- status: completed
- priority: P1
- owner_role: product-admin-skill-governance
- spec_path: `docs/specs/FEAT-016-external-agent-skill-package-optimization.md`
- summary: 将技能导出 zip 调整为包含通用入口 `SKILL.md`、Cici 规格 `cici-skill.md` 和包内规范 `PACKAGE_SPEC.md` 的 8 文件结构，并依赖通用 `cici-skill-package-optimizer/SKILL.md` 规则，让具备技能/规则文件能力的外部智能体对导出包进行离线优化后反向导入系统。
- done:
  - 已新增设计文档 `docs/specs/FEAT-016-external-agent-skill-package-optimization.md`。
  - 已明确导出包采用 8 文件结构：`manifest.json`、`SKILL.md`、`cici-skill.md`、`prompt.md`、`contract.json`、`resources.json`、`PACKAGE_SPEC.md`、`README.md`。
  - 已明确 OpenClaw、Codex、Claude Code、Cursor 等外部智能体工具都只是代表；方案不绑定具体平台，外部工具不直接运行或发布本系统业务技能。
  - 已明确不做导入前 diff 预览增强，导入仍沿用现有预览和资源映射。
  - 后端 `SkillPackageService` 已在导出包中生成 `SKILL.md` 和 `PACKAGE_SPEC.md`，并在 `README.md` 追加外部智能体优化说明。
  - 导入白名单已切换到当前 8 文件结构，`cici-skill.md` 是 Cici 导入规格正文。
  - 已新增 `.agents/skills/cici-skill-package-optimizer/SKILL.md` 初版。
  - 已完成验证：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillGovernanceIntegrationTest test` 成功。
- next_action: 可选补充真实外部智能体端到端验收：导出 zip，交给加载了 `cici-skill-package-optimizer/SKILL.md` 的外部工具优化，重新打包后导入系统。
- handoff_notes:
  - `PACKAGE_SPEC.md` 是包内格式规范，不是外部智能体的优化器规则文件。
  - `SKILL.md` 是导出包内的行业通用业务技能入口；`.agents/skills/cici-skill-package-optimizer/SKILL.md` 是项目内外部优化器规则，两者用途不同。
  - `cici-skill-package-optimizer/SKILL.md` 应作为通用外部优化器规则维护，不打入每个业务技能 zip。
  - 优化后 zip 仍必须符合 `universal-skill-package@1.0`，导入后只落为草稿，发布仍在 Cici Assistant 内完成。
  - 本轮没有新增管理端 UI，也没有导入前 diff 预览。

### TASK-036 Skill declarative API runtime

- status: in_progress
- priority: P0
- owner_role: backend-agent-runtime
- spec_path: `docs/specs/FEAT-015-skill-declarative-api-runtime.md`
- summary: 在 Skill 中声明远程 API 契约，发布时编译为 Skill 专属 function schema 和后端 execution plan；运行时只在当前激活 Skill 中注入这些隐藏 API 工具，由后端按固定契约执行远程 API，模型只负责填写业务参数。
- done:
  - 已明确产品与架构语义：该能力不属于普通 `toolWhitelist`，而是 Skill 私有的内嵌 API 动作；对最终用户不可见，对模型只暴露抽象 function schema，对管理员和平台治理可见。
  - 已新增 `docs/specs/FEAT-015-skill-declarative-api-runtime.md`，覆盖背景目标、范围、运行链路、发布期编译、运行时注入、执行器、安全策略、数据模型、接口影响、前端影响、验收标准和回滚策略。
  - 已将该能力提升为 P0 任务，任务编号 `TASK-036`。
- next_action: 进入后端最小闭环设计实现：新增 Skill API 契约数据模型和发布版本快照，创建 `SkillApiToolService` 的编译/执行骨架，并让 `ToolOrchestratorService` 能识别 `skillapi__` 命名空间。
- handoff_notes:
  - 第一版优先实现 `triggerMode=model_decide`，复用当前 tool-calling loop；暂不做 `auto_before_answer`。
  - 模型不能看到或提交 URL、Method、Header、Token；只能提交 `inputSchema` 中声明的业务参数。
  - 必须把 SSRF 防护、host 白名单、响应裁剪、脱敏、超时和审计作为后端 P0 范围，不允许作为后续补丁处理。
  - 管理端 UI 第二阶段跟进也可以，但后端契约和运行时闭环必须先保证可测。

### TASK-035 Admin skill versioning import export

- status: completed
- priority: P1
- owner_role: product-admin-skill-governance
- spec_path: `docs/specs/FEAT-014-skill-versioning-import-export.md`
- summary: 设计并实现管理端技能版本控制、最近三版恢复、仅自定义技能导出、外部通用技能包导入、自定义技能删除、草稿/发布状态机和列表/新建/编辑页入口。
- done:
  - 已新增 `docs/specs/FEAT-014-skill-versioning-import-export.md`，覆盖版本生成时机、最近三版保留策略、运行时 pinned 快照保护、changelog/diff summary、恢复流程。
  - 已定义通用技能 zip 包当前格式：`manifest.json`、`SKILL.md`、`cici-skill.md`、`prompt.md`、`contract.json`、`resources.json`、`PACKAGE_SPEC.md`、`README.md`。
  - 已明确导出权限边界：第一版仅 `TENANT_CUSTOM + EDITABLE` 可导出，平台标准和租户派生不直接导出。
  - 已明确导入流程：zip 安全校验、大模型字段映射、资源匹配、导入预览、确认后创建 `TENANT_CUSTOM` 技能。
  - 已补管理端技能列表页、新建页、编辑页的低保真原型和交互说明。
  - 已按补充需求补齐自定义技能删除、标准技能只读、导出前大模型标准化整理、隐藏派生入口、保存草稿/正式发布状态机。
  - 已补齐补充需求落地矩阵、服务端权限守卫、任务拆分和验收映射，明确前端隐藏入口不能替代后端权限拦截。
  - 已完成第一轮后端实现：`V35` 迁移、`skill_version` changelog/diff/source/retention 字段、`skill_definition` lifecycle/delete/publish 字段、版本列表/恢复、发布、删除影响分析、软删除、导出 zip、导入 zip 解析并创建自定义技能、派生入口服务端拒绝。
  - 已完成第一轮前端实现：列表页导入/发布/导出/删除入口、列表按钮视觉回到 `鎏金账房` 金线基线、编辑页版本侧栏、恢复、发布、导出、删除、导入 zip、变更日志和标准技能只读态入口。
  - 已完成验证：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillGovernanceIntegrationTest,SkillAuthoringIntegrationTest test` 通过；`frontend npm run build` 通过。
  - 已完成导出硬化第二轮：`SkillPackageService` 已新增“模型标准化优先 + 确定性回退”链路，导出前执行 manifest/schema 校验和敏感信息扫描；导出作业会返回实际标准化引擎与告警。
  - 已完成导入硬化第二轮：导入预览返回 `resourceMapping`（工具/知识库匹配与未匹配项）；`/skills/imports/{importId}/create` 支持可编辑 `draftOverride` 提交，创建前会按当前组织资源重新映射白名单。
  - 已完成前端导入预览收口：`/admin/skills/new` 导入 zip 改为“载入可编辑草稿，不自动创建”；列表页导入遇到未匹配资源会拦截并引导去新建页处理。
  - 已完善导入预览工作区：补充升级处理规则/输出约定可编辑字段，并在“直接创建草稿”前增加技能代码与显示名称前端必填校验。
  - 已补 Agent runtime pin 保留回归：`SkillGovernanceIntegrationTest` 新增被 pin 旧版本在 retention prune 后应标记 `PROTECTED_RUNTIME` 且 `restoreVisible=false` 的断言。
  - 已修复管理端 Skill 列表表格错位和横向滚动：摘要截断与操作按钮布局不再直接改变 `<td>` display，列表表格改为固定列宽 100% 布局。
  - 已按用户反馈继续收紧管理端 Skill 列表：页面文案纯中文，移除历史派生筛选；标准技能不显示查看入口，自定义技能不在列表页显示发布入口；右上新建技能恢复列表页主按钮样式。
  - 已按用户截图把自定义技能行级编辑/导出/删除改为 hover 三点按钮 + 点击后纵向菜单，支持外部点击和 Escape 关闭。
  - 已按用户截图继续调整列表页头：移除辅助标题文字，统一右上导入/新建按钮样式。
  - 已按用户反馈修正新建/编辑页版本管理可见性：字段页签新增“版本管理”，新建页显示版本生成说明，自定义技能编辑页展示最近三版恢复列表，页头“版本管理”按钮直接切到该页签。
  - 已按用户反馈把“发布”设为新建/编辑页自定义技能主动作；新建页点击发布会先创建草稿再发布，草稿保存入口保留但降为次级按钮。
  - 已按用户反馈移除新建/编辑页导入 zip 入口和导入预览工作区；导入技能保留在列表页入口。
  - 已按用户反馈移除基础信息中的“变更日志”，改为点击“发布”时弹出输入框录入版本发布说明，取消或留空不发布。
  - 已按用户截图反馈把版本发布说明从浏览器原生 prompt 改为统一风格的居中模式窗口，使用文本区录入，确认发布按钮在说明为空时禁用。
  - 已按用户反馈统一 Skill 相关弹框关闭样式：发布说明、添加工具白名单、添加知识库白名单弹框右上角均为无边框 × 图标。
  - 已按用户反馈统一 Skill 弹框按钮样式：白名单弹框“取消”改回暖白金线次级按钮，确认按钮保持香槟金主按钮；项目设计源新增“产品页按钮统一”和“弹出框默认模式窗口”规则。
  - 已按用户截图给管理端 Skill 新建/编辑页右侧页面滚动条留出内容间距，避免页头按钮和右侧摘要区紧贴滚动条。
  - 已按用户截图互换管理端 Skill 新建/编辑页字段页签顺序：“编译预览”现在位于“版本管理”之前。
  - 已按用户截图调整管理端 Skill 新建/编辑页页头按钮：“创建草稿”改为“保存草稿”，“预览编译”改为“编译预览”，且两者互换位置。
  - 已按用户要求把自然语言生成区“摘要预览”改为“需求解析”，空态改为“暂无待解析的需求”，生成草稿期间显示解析中动态进程。
  - 已将“继续优化”接入同一套需求解析动态进程，且在清空旧解析前缓存当前草稿与追问答案。
  - 已修复“继续优化”对增量请求整段改写旧草稿的问题：对“增加/补充/加入/再增加”类需求，服务端会保留当前提示片段和规格正文，只追加增量要求；已用“邮件市场营销活动 + 再增加百度搜索”场景补回归。
  - 已为“继续优化”增加未保存结果回退能力：优化成功后展示“回退本次优化”，可恢复优化前的表单、编译预览、需求解析、会话 ID 与追问答案；保存、重新生成、清空、重置或重新加载上下文后回退入口清除。
  - 已定位并修复自定义技能删除检查本地报错：运行中的 8080 后端未加载 `/skills/{id}/delete-impact`，导致请求被当作静态资源缺失并包装为 `Unexpected server error`；本轮已重启后端到当前源码并将未匹配路由改为 404、路径参数错误改为 400。
  - 已修复管理端 Skill 导出下载链路：前端不再用 `window.location.href` 打开受保护下载地址，改为带管理员 token 的 blob 下载；后端响应类型改为 `application/zip`。
  - 已补导出包回归断言：下载内容必须是 universal-skill-package zip，并包含 `manifest.json`、`SKILL.md`、`cici-skill.md`、`prompt.md`、`contract.json`、`resources.json`、`PACKAGE_SPEC.md`、`README.md`。
  - 已追加导出下载双保险：`/skills/exports/{exportId}/download` 不再受类级 `@RequireOrgAdmin` 拦截，直接打开新导出 URL 也会返回 zip；其他 Skill 管理接口仍逐个保留组织管理员权限。
  - 已重启本地后端，并用真实 localhost 探针验证新导出 URL 返回 `200 application/zip`。
  - 已修复删除后导入同 code 技能包仍报 `Skill code already exists`：软删除时归档旧 `skill_code`，创建新 Skill 时会归档历史 `DELETED` 冲突记录并 flush，Flyway `V36` 会处理已存在的软删除占用数据。
  - 已补删除同 code 后导入创建回归：`SkillGovernanceIntegrationTest` 覆盖删除 `feat014-custom-skill` 后再次导入同 code zip 并成功创建 `DRAFT`。
  - 已修复自定义技能删除的运行时引用误判：`delete-impact` 只按启用 Agent 当前发布版本的 Skill ref 判断运行时 pin，历史 archived 发布快照不再阻断删除。
  - 已补回归：当前发布版本引用 Skill 时删除仍被阻断；切到不含该 Skill 的新发布版本后，旧快照保留但删除检查通过。
  - 用户已确认管理端 Skill 版本、导入导出、删除治理、发布说明模式窗口、白名单弹框、页签顺序和需求解析动态进程完成人工验收。
- next_action: 已完成；后续仅按常规回归覆盖管理端 Skill 版本、导入导出与删除治理链路。
- handoff_notes:
  - 本任务已完成第一轮业务代码与 UI 入口，不再是纯设计态。
  - 当前导出已支持“模型标准化优先 + 回退”和 schema/敏感信息扫描；若模型不可用会自动回退到确定性标准化。
  - 当前导入已支持资源匹配预览和可编辑草稿覆盖提交；列表页快导仍是轻流程，不展示完整预览面板。
  - UI 实现必须继续遵守 `PRODUCT.md` / `DESIGN.md` 的 `鎏金账房` 管理端 product register。

### TASK-034 Admin skill authoring resource whitelist and edit refinement

- status: completed
- priority: P1
- owner_role: fullstack-product-admin
- spec_path: `docs/specs/FEAT-001-skill-authoring-generic-generation.md`
- summary: 优化管理端 Skill 自然语言生成与编辑：资源引用自动进入白名单，并允许已有 Skill 用自然语言继续优化后保存回原技能。
- done:
  - 已更新 FEAT-001 验收标准：自然语言生成/优化中明确引用候选工具、MCP 工具或知识库时，必须补入 `toolWhitelist` / `kbWhitelist`。
  - 后端 `BuiltinSkillCreatorService` 已在模型生成与 fallback 生成后追加资源引用扫描，覆盖 sourceText、描述、提示片段、规格正文、升级处理规则和输出约定。
  - 自定义工具匹配已从 toolName/displayName 扩展到 description，管理员说“潜客检索工具”这类工具描述时也能补入真实 toolName。
  - 前端 `AdminSkillComposePage` 套用生成/优化结果时保留当前表单的 `id`、启用状态和治理字段，已有 Skill “继续优化”后保存会更新原技能。
  - 不可编辑的平台标准 Skill 已禁用自然语言生成/继续优化正文入口，并提示先派生。
  - 已新增 `SkillAuthoringIntegrationTest` 覆盖工具/知识库引用自动白名单、无 session 的已有草稿继续优化。
  - 已完成验证：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillAuthoringIntegrationTest test` 通过；`frontend npm run build` 通过。
- next_action: 如继续人工验收，优先在 `/admin/skills/new` 输入带工具描述和知识库名称的需求，确认边界规则页白名单自动出现；再在 `/admin/skills/:id/edit` 输入增量优化说明，保存后确认仍是同一个 Skill ID。
- handoff_notes:
  - 本轮不改变 Skill 保存 API 的字段契约，仍使用 `toolWhitelist` / `kbWhitelist` 数组和前端 CSV 文本中转。
  - 知识库授权和工具授权保持分离；提到知识库会进入 `kbWhitelist`，不强制把 `rag-search` 一并加入工具白名单。

### TASK-033 Admin skill editor visual refresh

- status: completed
- priority: P1
- owner_role: frontend-product-admin
- summary: 按已确认效果图优化管理端 Skill 新建/编辑页的信息结构与视觉风格，使其符合 `鎏金账房` 产品页基线。
- done:
  - 已将 Skill Compose 页头重构为面包屑、标题、状态 chips 与右侧操作区，导入、预览、保存动作从首屏散点收拢到页头。
  - 已将 Authoring 区重排为“自然语言生成”工作台，保留模型选择、AI 生成草稿、继续优化、按草稿创建和清空动作，并补充需求描述 placeholder 与字数计数。
  - 已将摘要区改为“摘要预览”只读面板，生成前展示目标、触发、输出三行结构化空态。
  - 已将 Skill fields 拆成基础信息、提示与规格、输入输出与边界三个紧凑字段组，让字段区在首屏更早露出。
  - 已将管理端 shell 与 Skill Compose 局部样式从蓝色默认后台调整到暖象牙、墨色、香槟金结构线的产品基线。
  - 已按用户要求将页面解释性/说明性文字收进标题旁小问号提示，覆盖页面标题、Authoring 区、模型选择、摘要预览、字段标题、字段分组、启用和编译预览。
  - 已按用户要求去掉 Skill fields 区域最外层背景、内部分组背景，并移除“基础信息 / 提示与规格 / 输入输出与边界”分组标题。
  - 已按用户要求继续去掉 `技能字段 · Skill fields` 总标题，并去掉页头“新建技能”区域的横向背景块与分隔线。
  - 已修复字段标题 tooltip 靠左时被滚动容器裁切的问题：字段区问号提示改为从问号右侧展开。
  - 已按用户确认的第二版效果继续调整：字段区改为基础信息、执行提示、边界规则、编译预览页签；顶部统一收拢导入、重置、预览编译、创建/保存草稿等操作；移除底部重复操作按钮；`转人工规则` 改为 `升级处理规则`，问号说明补充价格承诺、合同、合规、权限不清、事实不足、工具异常与审批动作等升级场景。
  - 已按“简约、不要框套框”要求精简布局边框：页面主体不再做上下多层面板堆叠，主要以顶部底线、左右分隔线、页签底线、输入框边框表达层级；长文本集中到执行提示页签的大面积编辑区，并保持局部滚动。
  - 已完成验证：`frontend npm run build` 通过。
  - 已按截图反馈微调左侧自然语言生成区：标题从“草稿助手”改为“自然语言生成”；左侧宽度增加约 2cm，右侧相应收窄；需求描述输入区改为固定可控高度，避免生成/优化/清空按钮被遮住。
  - 已完成验证：`frontend npm run build` 通过。
  - 已继续修复左侧重叠问题：自然语言生成标题 tooltip 改为向右展开；左侧面板改为局部纵向滚动；需求描述输入区高度下调到 `clamp(190px, 23vh, 260px)`，按钮行获得独立布局空间，不再被输入区或摘要预览盖住。
  - 已完成验证：`frontend npm run build` 通过。
  - 已按最新截图调整整体排布：自然语言生成区置顶整行展示，内部需求描述与摘要预览横排；基础信息、执行提示、边界规则、编译预览页签整体移动到生成区下方，并占满下方编辑区。
  - 已完成验证：`frontend npm run build` 通过。
  - 已继续微调：需求描述与摘要预览标题行、内容区高度对齐；去掉下方基础信息区域中间的竖向分割线；页面问号提示图标整体缩小。
  - 已完成验证：`frontend npm run build` 通过。
  - 已继续按截图修正：需求描述标题拆为与摘要预览同构的标题行，左右内容框统一高度；问号提示图标缩到 10px；移除自然语言生成区与页签之间的横线；页面恢复整体纵向滚动，便于滚动查看基础信息等内容。
  - 已完成验证：`frontend npm run build` 通过。
  - 已按用户截图将原“执行提示”页签拆成“提示片段”和“规格正文”两个独立 tab；每个 tab 分别展示一个大文本编辑区。
  - 已完成验证：`frontend npm run build` 通过。
  - 已将“提示片段”输入框高度规则改为与“规格正文”一致。
  - 已完成验证：`frontend npm run build` 通过。
  - 已按用户截图继续重排“边界规则”页：升级处理规则和输出约定收窄到左侧；右侧改为知识库白名单与工具白名单资源面板，支持通过添加选择器选择知识库、内置工具和 MCP 工具，并保留 CSV 字段保存契约。
  - 已完成验证：`frontend npm run build` 通过；`curl -sS -I http://127.0.0.1:5173/admin/skills/new` 返回 200。
  - 已按用户最新截图继续重排“边界规则”页：升级处理规则与输出约定各自独占上方一整行；知识库白名单与工具白名单在下方同一行并排展示，窄屏降级为单列。
  - 已完成验证：`frontend npm run build` 通过；已重启前端 dev server 到 `http://127.0.0.1:5173/`，`curl -sS -I http://127.0.0.1:5173/admin/skills/new` 返回 200。
  - 已按用户要求将“基础信息”中的启用控件从 checkbox 换为按钮式 switch，保留原 `enabled` 字段和禁用规则。
  - 已完成验证：`frontend npm run build` 通过；`curl -sS -I http://127.0.0.1:5173/admin/skills/new` 返回 200。
  - 已按用户要求去掉启用开关外层字段框，改为与其他字段一致的标题/控件上下节奏。
  - 已完成验证：`frontend npm run build` 通过；`curl -sS -I http://127.0.0.1:5173/admin/skills/new` 返回 200。
  - 已按用户要求将启用开关从基础信息字段区移到页面顶部按钮条最右侧，保留原 `enabled` 状态、禁用规则和保存契约。
  - 已完成验证：`frontend npm run build` 通过；`curl -sS -I http://127.0.0.1:5173/admin/skills/new` 返回 200。
  - 已按用户截图去掉“编译预览”tab 框内重复标题和问号，将问号说明文案改为框内常显说明。
  - 已完成验证：`frontend npm run build` 通过；`curl -sS -I http://127.0.0.1:5173/admin/skills/new` 返回 200。
  - 已按用户截图将页头启用开关移到按钮条最左侧，并去掉启用旁问号。
  - 已完成验证：`frontend npm run build` 通过；`curl -sS -I http://127.0.0.1:5173/admin/skills/new` 返回 200。
  - 已完成验证：`frontend npm run build` 通过；本地浏览器打开并刷新 `http://127.0.0.1:5173/admin/skills/new` 验证窄视口无重叠，说明文字已从页面表面隐藏，字段区已平铺，页头背景块已移除。
- next_action: 如继续验收，优先在桌面宽屏检查 `/admin/skills/new` 与某个 `/admin/skills/:id/edit` 的页头启用开关顺序、编译预览说明、边界规则页上下排布、资源添加、MCP 工具展开、资源移除和保存后字段回填。
- handoff_notes:
  - 本轮只改前端结构与样式，不改后端接口和 Skill authoring 行为。

### TASK-032 Assistant workbench model label display

- status: completed
- priority: P1
- owner_role: frontend-backend-chat-runtime
- summary: 在智能体回答问题时，在工作台助手消息 meta 中标记当前主模型名称，只展示模型名，不展示其他运行信息。
- done:
  - 后端 `/ai/chat/stream` 的 `phase` SSE 已携带当前实际 `modelName`，包括工具调用前的早期 phase 与生成 phase。
  - 前端 `StreamPhaseEvent` 已支持 `modelName` 字段。
  - 前端将 `modelName` 标记到当前尾部助手消息；如果助手占位不存在，会补一个空助手消息承载模型名。
  - 工作台助手消息 meta 已新增模型名小标签，仅显示模型名。
  - 远端历史刷新替换消息时会保留本地助手消息上的模型名标签，避免生成完成后标签丢失。
  - 已新增 `chatMessageState.test.ts` 覆盖模型名标记与远端历史替换时保留模型名。
  - 已完成验证：`frontend npm run build`、`frontend npm test`、`backend ChatOrchestratorServiceModelIdentityTest`、`backend compile` 均通过。
- next_action: 前台工作台发送任意消息后，确认助手名/时间旁显示模型名，例如 `deepseek-v4-pro` 或当前配置的 qwen 模型。
- handoff_notes:
  - 本轮按用户要求只展示模型名；不要追加 provider、token、耗时、上下文长度等运行指标。

### TASK-031 Assistant model identity hallucination fix

- status: completed
- priority: P1
- owner_role: backend-agent-runtime
- summary: 排查并修复智能体在询问当前调用模型时错误自称 Claude 的问题，确保模型身份回答来自服务端真实路由配置。
- done:
  - 已查询本地数据库：`model_provider_config` 中 `anthropic` 为 disabled 且 API key 为空；`org_model_config` 当前 `demo-org/chat` 为 `aliyun-bailian / deepseek-v4-pro`。
  - 已确认 `agent_definition` 中 `cici-system.model=deepseek-v4-pro`，其 system prompt 不包含 Claude；`skill_definition` 中未命中 Claude prompt。
  - 已确认错误回答来自模型在缺少运行模型事实时自我身份幻觉，而不是 Anthropic 后台配置被调用。
  - 已在 `ChatOrchestratorService.buildInitialMessages(...)` 注入运行模型上下文，包含当前服务端模型供应商与模型名称。
  - 已明确提示模型：当用户问当前模型/供应商时只能依据运行上下文回答；不得在 provider/model 不匹配时自称 Claude、Anthropic、OpenAI、GPT、Gemini。
  - 已新增 `ChatOrchestratorServiceModelIdentityTest` 覆盖 `aliyun-bailian / deepseek-v4-pro` 的身份提示。
  - 已完成后端验证：`mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatOrchestratorServiceModelIdentityTest test` 成功；`mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` 成功。
- next_action: 可在前台重新询问“你现在调用的是什么大模型”，预期回答基于 `阿里云百炼 / deepseek-v4-pro`，不会再自称 Claude。
- handoff_notes:
  - 当前配置是“百炼供应商 + deepseek-v4-pro 模型”，不是 qwen；如果产品预期是通义千问，需要在管理端模型配置或数据库中把 chat 场景切换到 qwen 系列。
  - 本轮不修改现有模型配置数据，只修复模型身份事实注入与回答约束。

### TASK-030 Assistant workbench streaming message preservation

- status: completed
- priority: P1
- owner_role: frontend-assistant-workbench
- summary: 修复会话工作台中助手回复占位/流式内容被服务端旧历史覆盖，导致气泡短暂消失、最终回复整段出现的问题。
- done:
  - 已定位根因：`persistUserTurnCommitted` 后到 `persistAssistantTurnCommitted` 前，服务端历史可能只包含用户消息；工作台状态机 thought 变化触发 `loadWorkbenchMessages(..., true)` 后会用旧历史覆盖本地助手占位。
  - 已新增 `chatMessageState.ts`，统一处理“是否保留本地流式消息”、delta 追加和尾部助手消息替换。
  - 已让 `loadConversationMessages` / `loadWorkbenchMessages` 在远端历史缺少有效助手内容时保留本地占位或部分流式内容；404 早期竞态也保留本地流式状态。
  - 已让 delta 到达时如果最后一条已不是助手消息，则自动补回助手气泡并继续追加，避免流式内容丢失。
  - 已让工作台本地乐观消息同步写入 `conversationMessages[sessionId]`，避免工作台与历史缓存分叉。
  - 已移除工作台 effect 对 `activeWorkbenchThoughts.length` 的依赖，状态机提示变化不再触发历史重拉。
  - 已新增 `chatMessageState.test.ts` 覆盖占位保留、远端有效助手替换、占位丢失后 delta 自动补回、尾部助手替换。
  - 已完成前端验证：`npm run build` 成功，`npm test` 成功（3 个文件，12 个测试）。
- next_action: 如继续验收，优先用“明天天气 + 城市补充”这类会触发工具调用的工作台对话，确认等待工具期间助手气泡不会消失，生成阶段逐步追加。
- handoff_notes:
  - 本轮只改前端消息状态竞争处理，不改后端 SSE 协议和视觉样式。
  - 后端工具调用本身仍会发生在最终模型流式生成之前，因此工具执行期间可能只有占位/状态提示；关键修复是占位不再被旧历史清掉，流式 delta 不再丢失。

### TASK-029 Assistant workbench enter send and ASR finish behavior

- status: completed
- priority: P1
- owner_role: frontend-assistant-workbench
- summary: 调整前台会话工作台输入与语音识别体验：回车可触发送出；语音识别结束不再自动发送，只生成输入框内容；5 秒无语音自动关闭识别。
- done:
  - 已为工作台多行输入框增加 `Enter` 发送，保留 `Shift+Enter` 换行，并避开组合输入与带修饰键的回车。
  - 已将工作台语音识别完成逻辑从“自动发送”改为“回填输入框并提示内容已生成”。
  - 已为共享 ASR hook 增加可选静默自动停止参数，当前工作台传入 `5000ms`，管理端 Skill 语音录入不受影响。
  - 已追加修复语音按钮焦点问题：语音结束后焦点回到 composer 输入框，避免按 Enter 重新触发麦克风；重新开始语音时也保留已有输入作为前缀，避免误触发导致转写文本丢失。
  - 已完成前端验证：`npm run build` 与 `npm test` 均通过；追加修复后已复跑通过。
- next_action: 如继续验收，优先在浏览器实测工作台 `Enter`、`Shift+Enter`、手动结束语音和静默自动结束四条路径。
- handoff_notes:
  - 本轮只改 `frontend/src/assistant/AssistantApp.tsx` 与 `frontend/src/shared/useAsrVoiceInput.ts` 的交互逻辑，不改视觉语言、接口或后端 ASR 协议。
  - `autoStopAfterNoSpeechMs` 是 opt-in 配置，后续其他页面如需同款静默关闭可显式传入。

### TASK-028 Global avatar settings for agents and current user

- status: completed
- priority: P1
- owner_role: fullstack-product-avatar
- spec_path: `docs/specs/FEAT-013-global-avatar-settings.md`
- summary: 设计并实现全局头像设置能力：智能体头像仅由管理员在管理端 Agent Builder 设置，当前登录用户头像仅由本人在前台个人设置入口设置，并统一前台头像展示规则。
- done:
  - 已确认产品边界：智能体头像只能由管理员设置；用户头像只能自己设置；第一版采用上传图片、前端裁剪压缩、默认字母头像兜底。
  - 已补充展示覆盖要求：系统中所有需要显示头像的位置都必须显示对应身份头像，包括智能体、当前登录用户和外部会话参与人。
  - 已完成现状检查：`app_user.avatar_base64` 与管理端用户头像压缩逻辑已存在；`agent_definition` 尚无头像字段。
  - 已新增 `docs/specs/FEAT-013-global-avatar-settings.md`，沉淀 UX、权限、数据模型、API、前端结构与验收标准。
  - 已完成后端实现：新增 `V34__agent_definition_avatar_base64.sql`；`agent_definition` 支持 `avatar_base64`；`/agents` 定义读写已支持 `avatarBase64`；新增 `/auth/me/avatar` 仅允许当前用户更新本人头像；头像数据 URL 校验已统一收口。
  - 已完成前端实现：管理端 Agent Builder 已支持智能体头像上传/清除；前台个人设置已新增“个人资料/我的头像”并支持本人上传/清除；抽取 `AvatarView` 与 `processAvatarFile` 复用组件/逻辑。
  - 已完成头像展示覆盖替换：前台 rail、工作台消息、会话消息、智能体切换条、状态卡、监控卡、会话头部、右侧档案卡、智能体卡、会话对象卡等位置已接入对应头像来源。
  - 已完成编译验证：`backend mvn -q -Dmaven.repo.local=.m2 -DskipTests compile`、`frontend npm run build` 均通过。
  - 已修复前台左上角 rail 头像显示异常：头像入口 button 不再继承全局 button padding，内容区恢复为完整圆形，并改走 `AvatarView`。
  - 已新增交互式头像裁剪：用户与智能体上传头像时均支持缩放、拖动取景、应用裁剪后再保存。
  - 用户已确认 `/auth/me/avatar`、`/agents` 头像字段读写、头像裁剪、个人头像入口、Agent Builder 智能体头像入口和前台头像展示覆盖完成人工验收。
- next_action: 已完成；后续仅按常规回归覆盖头像读写权限、裁剪保存和主要头像展示点。
- handoff_notes:
  - 本卡已进入实现验收收口，后续重点是接口权限回归与页面人工验收。
  - 不要把头像功能做成社交装扮或营销形象库；第一版是产品工作台身份识别能力。
  - 实现时必须按 spec 的“展示覆盖矩阵”逐项替换头像展示点，不能只完成设置页。
  - 注意旧的管理端用户头像维护能力已存在，本轮用户确认的新边界是“前台当前用户头像只能本人设置”，实现时需避免扩大管理员代设入口。

### TASK-027 Project-wide impeccable design governance

- status: completed
- priority: P1
- owner_role: design-governance
- spec_path: `docs/specs/FEAT-012-project-design-governance.md`
- summary: 将 `impeccable` 固化为项目级页面设计规范，并把设计事实源与执行入口同步到 `AGENTS.md`、`README.md`、`PRODUCT.md`、`DESIGN.md` 与 `DESIGN.json`。
- done:
  - 已在 `AGENTS.md` 中加入 `impeccable` 的项目级强约束，覆盖预检、默认 register、设计事实源和禁用模式。
  - 已在 `README.md` 中补充面向开发者的人类可读设计治理入口。
  - 已将根 `PRODUCT.md` 从平台单页上下文扩展为全项目认证产品面的统一战略上下文。
  - 已将 `DESIGN.md` / `DESIGN.json` 扩展为 assistant、admin、platform 共用的产品面设计基线，并保留 `鎏金账房` 作为默认视觉语言。
  - 已将管理端 Skill 列表本轮所有调整沉淀为 `Admin CRUD Lists` 规范：保护原生表格对齐、禁止横向滚动、搜索/筛选/空态不得撑开页面、筛选使用金色文本 tab、按钮统一、行操作使用 hover 三点菜单、列表页不暴露越权动作。
  - 已新增 `docs/specs/FEAT-012-project-design-governance.md` 作为本轮规范落地的事实源，并在 `decisions.md` 中记录项目级设计治理决策。
- next_action: 后续任意页面改版先按 `AGENTS.md` 的 `impeccable` 预检执行；若引入品牌页或活动页，先单独 `shape` 并补 spec。
- handoff_notes:
  - 本任务只改治理文档，不改业务逻辑或页面实现。
  - `/`、`/admin/*`、`/platform/*` 默认都按 `product` register 处理，但允许按任务流在密度和节奏上做细分。

### TASK-026 Assistant workbench rail cleanup and reorder

- status: completed
- priority: P1
- owner_role: frontend-assistant-workbench
- summary: 收口前台会话工作台左侧 rail：去掉重复悬浮提示，调整头像与 logo 的上下位置，并把 rail 的整体配色切到和主页面一致的暖白金线风格。
- done:
  - 已移除左侧 rail 按钮上的原生 `title` 提示，仅保留 `data-menu-label` 自定义 tooltip，避免重复悬浮提示。
  - 已将个人头像按钮移到左侧 rail 顶部，将 `CB` logo 调整到底部区域。
  - 已将 rail 的背景、边框、图标按钮、active 态和 tooltip 全部改成与主页面一致的暖白、香槟金、墨色语言。
  - 已完成前端验证：`frontend npm run build` 通过。
- next_action: 如继续做视觉验收，优先在浏览器检查 rail 的 hover/active 态、tooltip 位置，以及头像/底部 logo 的层级是否顺眼。
- handoff_notes:
  - 本轮只调整 `frontend/src/assistant/AssistantApp.tsx` 与 `frontend/src/assistant/cici-ui.css` 的结构与样式，不涉及路由逻辑或功能语义。
  - 当前 `task-board.md` front matter 里保留了历史重复 `updated_at` 字段，后续如继续整理状态文件可顺手清理。

### TASK-025 Assistant workbench sidebar state layout refinement

- status: completed
- priority: P1
- owner_role: frontend-assistant-workbench
- summary: 按用户标注继续收口前台会话工作台，移除右侧旧头像状态卡，将左侧状态机搬到右侧侧栏顶端，并删除状态机右侧摘要区，让“今日工作概览”下移后与会话历史衔接。
- done:
  - 已将 `WorkbenchAgentBar` 收口为仅保留智能体切换条，移除左侧顶部完整状态机。
  - 已新增右侧顶部精简 `WorkbenchStateCard`，保留头像、名称、状态、上一项、当前、下一项，并去掉右侧过程摘要区。
  - 已移除右侧原有“思思头像 + 空闲中”独立状态卡。
  - 已将“今日工作概览”与“会话历史”合并到侧栏下半区，形成连续衔接的纵向结构。
  - 已完成前端验证：`frontend npm run build` 通过。
- next_action: 如继续做人工验收，优先在浏览器检查会话工作台桌面端与窄屏下的侧栏间距、状态机换行和会话历史滚动表现。
- handoff_notes:
  - 本轮只调整 `frontend/src/assistant/AssistantApp.tsx` 与 `frontend/src/assistant/cici-ui.css` 的结构和样式，不涉及聊天逻辑、状态机数据模型或接口。
  - 用户明确要求：右侧顶部去掉旧头像状态条，左侧状态机移到右侧最上方，并删除状态机右侧部分。

### TASK-024 Platform console visual refresh

- status: completed
- priority: P1
- owner_role: frontend-product-platform
- spec_path: `docs/specs/FEAT-011-platform-console-visual-refresh.md`
- depends_on: `docs/specs/FEAT-010-platform-operations-console.md`
- summary: 在不改业务功能的前提下，整体重构 `/platform/login` 与 `/platform/*` 的样式、层级和工作区布局，改为浅色、克制、紧凑的平台运营控制面。
- done:
  - 已完成 `impeccable` shape：确认核心用户为平台运营，整体方向以 Stripe Dashboard 式清晰克制为主，选定视觉探针 `2`。
  - 已补齐 `PRODUCT.md`、`DESIGN.md` 与 `DESIGN.json`，作为本轮平台控制面视觉重构的设计事实源。
  - 已新增 `docs/specs/FEAT-011-platform-console-visual-refresh.md`，明确范围仅包含 `/platform/login + /platform/*` 的前端样式与结构优化。
  - 已完成 `PlatformLogin`、`PlatformShell`、`PlatformHomePage`、`PlatformSkillsPage`、`PlatformToolsPage`、`PlatformAuditPage` 的结构与信息层级重构。
  - 已将平台页 scoped 主题从深色渐变/玻璃化风格切换为浅色、克制、紧凑的控制台样式，并补齐统一的页头、工作区、按钮、表格、表单和提示条语言。
  - 已完成前端验证：`frontend npm run build` 通过。
- next_action: 如继续做视觉验收，优先在浏览器人工检查 `/platform/login`、概览、技能页、工具页、审计页的桌面/窄屏表现；否则回到 `TASK-023` 项目主线。
- handoff_notes:
  - 用户明确要求“不涉及功能改动，只做页面样式和结构美化”。
  - 视觉要求已明确：浅色、紧凑、克制，不要花哨渐变、不要大面积深色、不要玻璃拟态、不要营销感。
  - 注意平台页大量复用 `skills-*` / `platform-console-*` 类名，改动时既要统一语言，也要避免误伤 `/admin/*`。

### TASK-020 Knowledge base lifecycle completion

- status: paused
- priority: P0
- owner_role: fullstack-knowledge-platform
- spec_path: `docs/specs/FEAT-008-knowledge-base-lifecycle-completion.md`
- depends_on: `ISSUE-2026-04-29-kb-delete-leaves-vector-points`
- summary: 补齐管理端知识库完整生命周期，并按 Dify Knowledge 对标补齐数据源、文件解析、切片设置、索引/检索参数、内容运营、元数据、检索测试和 API 运营能力；P0 仍先修复文档/知识库删除后 DB chunk、源文件、向量库 point、Agent KB 绑定不同步清理的问题。
- done:
  - 已完成现状代码检查与功能缺口盘点。
  - 已新增 `docs/specs/FEAT-008-knowledge-base-lifecycle-completion.md` 作为后续实现 source of truth。
  - 已按 Dify 官方 Knowledge 文档补充对标缺口：数据源、切片预览、High Quality/Economical、hybrid/rerank、文档/chunk 启停编辑、metadata filtering、retrieval test/log、Service API。
  - 已完成 FEAT-008 P0 第一阶段实现：`V27__kb_lifecycle_completion.sql` 增加 KB/document/chunk 生命周期字段；`VectorStoreClient` 支持结构化 hit 和按 vectorId/document/KB 删除；memory/Qdrant 适配器已实现删除契约。
  - 已修复 `deleteDocument/deleteKnowledgeBase/unpublish/reindex`：同步清理 DB chunk、源文件、向量点，删除 KB 时清理 Agent KB 绑定；重复发布先清旧索引，避免重复可检索 chunk。
  - 已收口 `RagService`：向量召回必须回 DB 校验 KB/document/chunk 状态，DB fallback 也只返回有效状态内容。
  - 管理端已补最小生命周期入口：重建索引、下线、删除确认文案、chunk 数、清理中/清理失败状态展示。
  - 已新增 `KnowledgeBaseLifecycleIntegrationTest` 覆盖删除文档后不再召回、取消发布后不再召回、重复重建不重复、删除 KB 级联清理绑定。
  - 已完成 FEAT-008 P0 第二阶段第一批：新增 `V28__kb_chunking_and_retrieval_settings.sql`，落地 KB `chunk_size/chunk_overlap/chunk_delimiter/top_k/score_threshold/retrieval_strategy` 与 `kb_retrieval_log`。
  - 已新增后端 API：`GET/PUT /kb/{id}/settings`、`POST /kb/{id}/chunking/preview`、`POST /kb/{id}/retrieval/test`、`GET /kb/{id}/retrieval/logs`。
  - `KnowledgeBaseService` 已按 KB 设置参与分片与检索：发布索引按 KB chunk 参数切片，retrieval test 支持 topK/scoreThreshold 并写入检索日志。
  - 管理端设置页已接入切片参数、chunk preview、检索参数、retrieval test 和最近检索日志展示。
  - 已补并通过 `KnowledgeBaseLifecycleIntegrationTest.shouldSupportChunkPreviewAndRetrievalTestWithKbSettings`。
  - 已完成 FEAT-008 P0 第二阶段第二批：新增迁移 `V29__kb_metadata_and_chunk_ops.sql`；补文档 rename/enable/disable/archive、chunk list/update/enable/disable/delete、metadata field 与 document metadata API。
  - retrieval test 已支持 `metadataFilters`，并可结合文档 metadata 做过滤召回。
  - 管理端文档列表已接入：重命名、文档启停、归档、切片管理弹层（编辑/启停/删除）、文档 metadata 编辑；设置页新增 metadata 字段管理与 retrieval metadata filter 输入。
  - 已补并通过 `KnowledgeBaseLifecycleIntegrationTest.shouldSupportChunkToggleAndMetadataFilteringInRetrievalTest`。
  - 已完成 FEAT-008 P0 第二阶段第三批：后端新增文档与切片批量操作 API（enable/disable/archive/unarchive/delete），前端新增批量勾选与批量动作条，并补失败明细提示。
  - 已补并通过 `KnowledgeBaseLifecycleIntegrationTest.shouldSupportBatchDocumentOperations`。
  - retrieval test 新增 metadata filter 字段校验（未知字段会返回明确错误），前端补“可用字段”提示，降低回归与运维排错成本。
  - 批量操作按钮已补禁用态与“已选数量”提示，避免空操作请求。
  - 已补并通过 `KnowledgeBaseLifecycleIntegrationTest.shouldRejectUnknownMetadataFilterField`。
  - 已完成管理端页面视觉收尾第一批：按钮从高饱和渐变改为简洁实色；侧边栏/表格/输入框/弹层统一为紧凑中性风格；失败态/批量区信息密度增强。
  - 已完成管理端页面 UX 收口第二轮：metadata/doc metadata 输入新增格式校验提示；检索测试与切片预览补空结果反馈；文档/切片批量条新增“清空选择”；失败态错误文案统一样式；设置区网格改为响应式布局。
  - 已完成前端验证：`frontend npm run build` 通过（含第二轮 UX 调整）。
  - 已完成真实后端接口回归：建库、metadata 字段创建、unknown metadata 字段报错、文档上传发布、metadata 过滤检索、检索日志、文档批量启停均通过。
  - 已完成前端 UX 收口第三批：检索 metadata 字段与文档 metadata 字段新增前端字段存在性校验；输入变化时自动重置空结果提示，避免提示残留。
  - 已完成前端验证：`frontend npm run build` 通过（含第三批 UX 调整）。
  - 已完成前端 UX 收口第四批：批量操作结果新增内联反馈块（成功/部分失败 + 失败样本）并可关闭；切片预览与检索测试按钮在空输入时禁用。
  - 已完成回归复核：重启本地后端后再次执行 `/tmp/feat008_reg.sh`，建库/metadata 字段/unknown field 报错/文档上传发布/metadata 检索/检索日志/批量启停均通过。
  - 已完成前端验证：`frontend npm run build` 通过（含第四批 UX 调整）。
- next_action: 用户已要求暂停 FEAT-008；待恢复后从页面级人工回归（文档/设置/切片弹层）继续，确认第四批“批量反馈块 + 按钮禁用态”无回归。
- handoff_notes:
  - 用户已点名“删除知识文档后没有同步删除向量库内容”；该问题已验证且是本任务最高优先级。
  - P0 第一阶段已经保证删除/下线后即使向量库残留，也会被 DB 状态二次过滤拦住；历史无 `chunk_id` 的旧向量命中不会直接信任 payload content。
  - 2026-04-29：用户明确要求“先保持当前状态，暂停 FEAT-008”；本卡暂停，不继续提交新代码。

### TASK-023 CloudCC runtime smoke unblock

- status: in_progress
- priority: P0
- owner_role: backend-external-integration
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- depends_on: `ISSUE-2026-04-08-cloudcc-token-invalid-credential`, `ISSUE-2026-04-30-chat-smoke-blocked-by-aliyun-api-key`
- summary: 收口 CloudCC 真实工具 smoke 的最后两处阻塞，先把真实绑定用户凭证与本地模型入口配置校正到可复跑状态，再补一轮真实 `/ai/chat` 与工具调用验收。
- done:
  - 已完成 CloudCC 运行态第一轮 smoke，确认 `POST /mcp-servers/1/health` 与 `GET /mcp-servers/1/tools` 均成功，MCP server 连通与缓存快照可用。
  - 已确认 CloudCC 组织网关解析正常，`orgapi_address=https://szyd.apis.cloudcc.cn/lightningapi`。
  - 已确认真实绑定用户 `13800000001/哪吒` 当前换取 CloudCC token 返回 `Please check your username and password.`，阻塞点收敛到用户绑定凭证而非组织级配置。
  - 已确认 `/ai/chat` 对 `sales-agent` 的 CloudCC 查询类请求当前会直接返回 `Aliyun API key is not configured.`；同次响应里的 `effectiveToolNames` 已包含 CloudCC 相关工具，说明问题发生在模型入口而非工具暴露面。
- next_action: 先轮换并核实 `cc_username/cc_safetymark`，再补齐本地可用 Aliyun API key，随后复跑真实 `/ai/chat` 与 CloudCC 工具触发链路。
- handoff_notes:
  - 先修用户凭证，再修模型入口，二者都不通时无法判断真实工具执行是否完全恢复。
  - 验证结果统一沉淀到 `.claw/test-report.md` 与 `.claw/issue-list.md`，不要把长命令日志堆回 `current-status.md`。

### TASK-021 Skill layering and governance

- status: completed
- priority: P0
- owner_role: product-platform
- spec_path: `docs/specs/FEAT-009-skill-layering-and-governance.md`
- summary: 下一阶段完整实现 Skill 分层治理闭环：将当前 `builtin/custom` Skill 体系升级为“平台核心策略、平台标准 Skill、租户派生 Skill、租户自定义 Skill、隐藏平台服务能力”，并落地租户可见/可编辑规则、平台模板版本、PolicyBundle、Agent 发布版本 pin、影响分析、灰度与回滚。
- done:
  - 已新增 `docs/specs/FEAT-009-skill-layering-and-governance.md`，记录分类、数据模型、运行时解析、平台更新流、API、迁移阶段和验收标准。
  - 已新增迁移 `V30__skill_governance_and_platform_audit.sql`：为 `skill_definition` 补齐 `source_type/visibility/edit_policy/binding_policy/update_policy/template_code/base_template_version/current_published_version_id/latest_draft_version_id`。
  - 已将现有内置 Skill 回填为平台标准 Skill，并将 `conversation-core/knowledge-first/safe-handoff` 标为隐藏核心策略语义（`HIDDEN + MANDATORY`）。
  - 租户 `/skills` 列表已收口为仅展示可见 Skill；平台标准 Skill 变为 `CONFIGURABLE`，租户只能启停，不能直接修改正文或删除。
  - 已新增 `POST /skills/{id}/derive`，支持从平台标准 Skill 派生租户 Skill，并保留 `templateCode/baseTemplateVersion`。
  - `AgentSkillBindingService` 已补隐藏/强制 Skill 保护：Agent Builder 保存可见 Skill 时，不会误删既有隐藏 mandatory/internal bindings。
  - 已新增 `SkillGovernanceIntegrationTest`，验证核心 Skill 隐藏、平台标准 Skill 仅可配置启停，以及派生链路。
  - 已新增 `V31__platform_skill_template_and_tool_governance.sql`，落地 `platform_skill_template/platform_skill_template_version` 与 `platform_tool_definition`。
  - `/platform/skills` 已接入模板版本治理：支持列表、版本历史、创建草稿、发布、回滚，并把平台标准 Skill 同步到模板版本事实源。
  - 平台模板发布会同步更新标准 Skill 正文/工具边界/治理字段，并写入 `skill_version` 发布快照，供后续 pin/runtime 使用。
  - 已新增 `PlatformGovernanceIntegrationTest`，验证平台 Skill 模板草稿/发布、平台审计与租户工具目录联动。
  - 已新增 `V32__platform_policy_bundle_runtime_controls.sql`，落地 `platform_policy_bundle`，并把 `conversation-core/knowledge-first/safe-handoff` 种子为 `core-default@v1` 平台核心策略包。
  - `SkillResolverService` / `SkillPromptAssembler` 已改为先注入 `PlatformPolicyBundle`，聊天 `runtimePolicy` 与 debug/chat trace 会回显 bundle code/version。
  - 已新增 `V33__agent_workflow_skill_ref.sql`、`AgentWorkflowSkillRefService` 与发布时 skill snapshot 固定逻辑；`AgentDefinitionService.publishVersion(...)` 会为已发布 workflow 写入 pinned skill refs。
  - chat/debug 运行时已优先读取 pinned skill refs；`/ai/chat` 新增 `resolvedSkillVersions`，`OrchestratorIntegrationTest.shouldKeepPublishedAgentPinnedToSkillVersionAfterSkillEdits` 已验证 skill 后续编辑不会影响旧发布版本的 tool scope / version 摘要。
  - 已完成 S6 第一批治理收口：`/platform/policies/core` 新增核心 `PolicyBundle` 概览；`/platform/skills` 版本历史新增 impact 摘要、pinned workflow/agent 命中数、灰度与回滚提示。
  - `AgentWorkflowRuntimeService` / `/agents/{agentId}/debug` / `/ai/chat` 已新增 `policyBundle`、`runtimeGovernanceNotes` 与结构化 `resolvedSkillVersions` 摘要，方便运营排障时确认命中的平台策略与 pinned skill snapshot。
  - 已补并通过 `PlatformGovernanceIntegrationTest` / `OrchestratorIntegrationTest` 新断言；同时 `frontend npm run build` 通过。
  - 已完成 S6 第二批治理收口：`/platform/policies/core/versions` 支持核心策略包草稿创建、独立发布与回滚；平台治理页已新增 PolicyBundle 编辑器、版本历史与影响摘要。
  - 已补并通过 `PlatformGovernanceIntegrationTest` 核心策略包 draft/publish/rollback + 审计断言；`frontend npm run build` 通过。
  - Agent Builder 调试面板已改为“后端真实运行优先”：`runDebug()` 成功时直接消费 `/agents/{agentId}/debug` 的 runtime trace / governance 摘要，只在接口异常时才回退前端模拟路径。
  - 已补当前收口验证：`mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest#shouldUsePublishedWorkflowInDebugRuntime test` 与 `frontend npm run build` 通过。
  - 已完成页面级人工回归第一轮：平台登录、概览、平台技能、内置工具、平台审计已在本地浏览器验证通过；平台技能页成功创建 `core-default` 草稿 `v3`，平台审计出现对应创建记录。
  - 已修复前端平台页 dev proxy 回归：平台页原先直接请求 `/platform/**`，会与 SPA 路由前缀冲突并拿到 HTML；现已统一走 `/api/platform/**`，由 `vite.config.{js,ts}` rewrite 到后端 `/platform/**`，`frontend npm run build` 通过。
- implementation_plan:
  - S1 Schema 兼容层：为 `skill_definition` 增加 `source_type/visibility/edit_policy/binding_policy/update_policy/template_code/base_template_version/current_published_version_id/latest_draft_version_id` 等字段，保留 `builtin` 兼容。
  - S2 租户 Skill 语义收口：后端 CRUD 与前端 Skill Studio 按平台标准/派生/自定义执行可见、可编辑、可删除、可派生规则。
  - S3 平台标准 Skill 模板版本：新增 `platform_skill_template` / `platform_skill_template_version`，把 Java 内置种子迁移为平台模板来源，并实现标准 Skill 同步策略。
  - S4 平台核心策略包：新增 `platform_policy_bundle`，将底层必备兜底策略从普通租户 Skill 中剥离，运行时先注入 policy bundle。
  - S5 Agent 发布版本 pin：新增 `agent_workflow_skill_ref`，发布时固定具体 `SkillVersion`，运行时优先读取 pinned refs。
  - S6 治理闭环：补标准 Skill / Policy 的影响分析、发布、灰度、回滚、debug/trace 摘要展示。
- next_action: （本任务当前冲刺范围已完成；若继续平台治理，下一卡优先拆 rollout group 编排与更细粒度灰度策略。）
- handoff_notes:
  - 平台核心兜底策略建议长期迁移为 `PlatformPolicyBundle`，不要继续作为普通租户 Skill 展示。
  - 已发布 Agent 现在会在 publish 时写入 `agent_workflow_skill_ref`，运行时优先按 pinned skill refs 解析；后续若继续补 S6，请围绕 impact/debug/rollout 做增量，不要回退 publish pin 语义。
  - 当前“灰度”语义来自已发布 Agent 保持旧 pinned snapshot、后续重新发布命中新模板版本的天然分层；PolicyBundle 虽已具备独立版本发布面板，但仍是全局即时生效，若要继续推进需补 rollout group 编排而不是回退现有入口。
  - 页面级人工回归第一轮已完成；后续若继续平台治理，优先补更多灰度/回滚场景和 debug trace 对照，而不是重复验证基础可达性。

### TASK-022 Platform operations console

- status: completed
- priority: P0
- owner_role: product-platform
- spec_path: `docs/specs/FEAT-010-platform-operations-console.md`
- summary: 下一阶段先实现平台运营后台的内置 Skill 与内置工具治理：独立 `/platform/**` 控制面、平台角色、平台审计、标准 Skill 模板/版本管理、内置工具目录与风险/启停/依赖治理；计费、完整租户运营、用量成本和支持观测暂不展开。
- done:
  - 已新增 `docs/specs/FEAT-010-platform-operations-console.md`，记录架构边界、平台角色、路由/API、MVP 模块、数据模型、计量事件、迁移阶段和未来拆分路径。
  - 已新增平台角色常量与 `@RequirePlatformRole` AOP；phase-1 继续复用短信登录和 JWT，通过 `platform-*-mobiles` 配置把平台角色写入 `roles` claim。
  - 已新增 `/platform/**` 后端基础读接口：`/platform/bootstrap`、`/platform/skills`、`/platform/tools`、`/platform/audit/logs`。
  - 已新增 `platform_audit_log` 表、实体、Repository 与 Service，作为后续平台高风险操作审计事实源。
  - 前端已新增 `/platform/login`、`/platform` 独立壳与菜单，并落地概览/平台技能/内置工具/平台审计基础页面。
  - 已新增 `AuthFlowIntegrationTest.shouldExposePlatformRoleAndAllowPlatformBootstrap`，验证平台角色注入与 `/platform/bootstrap` 门禁。
  - `/platform/skills` 已从只读列表升级为可写治理台：支持模板版本查看、草稿创建、发布/回滚、治理字段编辑与影响范围基础计数。
  - `/platform/tools` 已接入平台治理表：支持展示名、描述、风险、分类、启用状态编辑，并展示关联平台 Skill / Agent 依赖。
  - 平台 Skill 模板建草稿、发布/回滚，以及平台 Tool 更新均已写入 `platform_audit_log`。
  - 平台 Tool `enabled=false` 已影响租户 `/tools` 目录，禁用的内置工具不会继续出现在 Agent Builder 可选目录。
  - 已新增 `PlatformGovernanceIntegrationTest`，验证平台 Skill / Tool 可写治理、平台审计与租户工具目录联动。
  - 平台 Tool `enabled=false` 已真正接入运行时工具解析/执行：禁用的内置工具不会出现在 chat tool definitions 中，且 `ToolOrchestratorService.executeTool` 会硬拦截。
  - 已补 `ORG_ADMIN` / 平台角色边界验收：`ORG_ADMIN` 无法访问 `/platform/**`，平台高风险治理动作保留审计。
  - 已完成页面级人工回归补验：平台概览、平台技能、内置工具、平台审计在本地浏览器已能加载真实数据；技能页与工具页此前的 HTML/JSON 冲突已由 `/api/platform/**` 前缀修复。
- implementation_plan:
  - O1 平台控制面壳：新增 `/platform/**` 后端鉴权、平台角色模型、前端平台后台布局与菜单。
  - O2 平台审计基础：新增 `platform_audit_log`，记录平台人员对内置 Skill / Tool 的新增、编辑、发布、停用、回滚、紧急禁用操作。
  - O3 内置 Skill 治理页：接入 FEAT-009 平台标准 Skill 模板与版本，支持列表、详情、版本、发布/回滚、影响范围查看。
  - O4 内置工具治理页：管理内置工具展示名、描述、风险等级、启用状态、能力分类，展示关联 Skill / Agent 依赖。
  - O5 工具紧急控制：支持平台级紧急禁用/恢复高风险内置工具，并在运行时工具解析中生效。
  - O6 前后端验收：验证 ORG_ADMIN 不能访问 `/platform/**`，平台角色可操作内置 Skill / Tool，所有高风险操作写审计。
- next_action: （本任务冲刺范围已完成，无阻塞剩余项）
- handoff_notes:
  - 平台后台不是租户后台的超级模式，必须独立权限、独立路由和平台审计。
  - 计费用量以 `usage_meter_event` 为事实源，不从前端点击或业务表临时拼账单。

### TASK-019 Agent / Skill / Tool permission model (runtime)

- status: completed
- priority: P0
- owner_role: backend-agent-runtime
- spec_path: `docs/agent-skill-tool-permission-model.md`
- summary: 按设计文档实现运行时工具权限语义——Agent 直接绑定与已绑定 Skill 的 `toolWhitelist` 声明合并为会话可用工具面；区分静态绑定与 Skill 独有声明；调试与聊天 API 透出分层字段；工具执行日志标记 invocation 类型。
- done:
  - `AgentCapabilityResolverService`：`effectiveToolNames` = 直接绑定 ∪ Skill 声明（`mergeToolUnion`）；记录 `agentDirectToolNames`、`skillDeclaredToolNames`、`skillScopedToolNames`。
  - `SkillResolverService` / `ResolvedSkillContext`：透出上述字段；始终合并各 Skill 的 toolWhitelist。
  - `/ai/chat` 与 `/agents/{agentId}/debug` 响应增加分层工具字段；`ToolOrchestratorService` 增强审计日志 `invocationType`。
  - `OrchestratorIntegrationTest#shouldUnionAgentDirectToolsAndSkillDeclaredToolsIncludingMcp` 覆盖 Agent=tavily + Skill=MCP 工具并集场景。
- next_action: 可选前端展示三列工具维度；若继续增强，单独补“当前 Skill 执行上下文”强约束与高风险审批策略建模。
- handoff_notes:
  - 文档中的「当前 Skill 执行上下文」强约束与高风险审批策略尚未单独建模；后续可按 FEAT 拆分。
  - **取代说明**：`TASK-018` 中「无交集则 effective 为空」的交集策略已由本任务废止，以设计文档 §6 运行时并集为准。

### TASK-018 Agent tool whitelist strict boundary and MCP enforcement verification

- status: superseded
- priority: P0
- owner_role: backend-agent-runtime
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- summary: （历史）曾将 Agent 与 Skill 工具白名单改为严格交集；已由 `TASK-019` + `docs/agent-skill-tool-permission-model.md` 按「静态不扩权、运行时并集」取代。
- superseded_by: `TASK-019`
- handoff_notes:
  - 请勿再以本卡描述交集收敛为当前后端行为；评审与排障以 TASK-019 及权限设计文档为准。

### TASK-001 MCP cache runtime smoke closure

- status: completed
- priority: P1
- owner_role: backend-platform
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- depends_on: `ISSUE-2026-04-23-mcp-smoke-blocked-by-admin-auth-scope`
- summary: 完成系统 MCP 服务器缓存机制的真实管理员运行态验收，覆盖列表、详情缓存读取与强制 discover 刷新。
- done:
  - MCP 缓存字段、服务端缓存兜底与管理端展示已实现。
  - 相关 compile/build/integration test 已通过并记录在 `.claw/test-report.md`。
  - 新增 `McpServerIntegrationTest.shouldRejectOrgUserAndAllowOrgAdminForMcpServerApis`，覆盖管理员权限门禁稳定性。
  - 新增 `McpServerIntegrationTest.shouldKeepCachedSnapshotWhenDiscoverRefreshFails`，覆盖“未命中缓存 -> 发现成功 -> discover 失败后旧快照仍可读取”链路。
  - 与 `OrchestratorIntegrationTest`、`ChatRealtimeIntegrationTest` 组合回归通过。
  - 2026-04-30 已补真实运行态 smoke：`13800138111` 可通过短信登录访问 `/mcp-servers`、读取 `/mcp-servers/1/tools` 真实缓存，并成功触发 `/mcp-servers/1/discover` 刷新；`13800138121` 作为 `ORG_USER` 访问同路径仍被拒绝。
- remaining:
  - （冲刺目标已完成，无阻塞剩余项）
- next_action: 转入 FEAT-002 人工对话回归与复杂语义覆盖。
- handoff_notes:
  - 2026-04-30 已完成真实运行态验收，原先“管理员权限上下文不稳定”的阻塞不再复现。
  - 结果以 `.claw/test-report.md` 和 `.claw/issue-list.md` 为准，不要在本卡重复写测试细节。

### TASK-002 Bind published agent version to runtime

- status: completed
- priority: P0
- owner_role: backend-agent-runtime
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- depends_on: `ISSUE-2026-04-21-agent-runtime-not-bound-to-published-workflow`
- summary: 让聊天运行时真正读取已发布 Agent workflow/version，而不是只解析静态 skill/tool/kb 绑定。
- done:
  - `SkillResolverService` 已接入 `published_version_id` 解析：当存在已发布版本时，优先读取 `workflow_manifest.dependencies`（skills/tools/knowledgeBases）作为运行时边界。
  - 无发布版本、解析失败或状态非 `PUBLISHED` 时，自动回退到原有 `AgentCapabilityResolverService` 路径。
  - 新增 `OrchestratorIntegrationTest.shouldPreferPublishedWorkflowDependenciesAtRuntime` 覆盖发布后运行时依赖优先逻辑。
  - 新增 `OrchestratorIntegrationTest.shouldSwitchRuntimeDependenciesAcrossPublishStates`，覆盖 `publish v1 -> publish v2 -> rollback v1` 三态依赖切换回归。
  - 新增 `OrchestratorIntegrationTest.shouldGracefullyHandleInvalidPublishedManifest`，覆盖 published manifest 非法 JSON 时的运行时韧性。
  - 已接入 published runtime policy：运行时解析 `workflow_manifest.policies.maxToolCalls`，并驱动 `ChatOrchestratorService` 的 tool loop 轮数上限；`/ai/chat` 响应新增 `runtimePolicy`（`maxToolCalls`、`publishedVersionId`）。
  - 新增 `OrchestratorIntegrationTest.shouldExposePublishedRuntimePolicyInChatResponse` 验证运行时策略注入。
  - 新增 `AgentWorkflowRuntimeService` 并接入 `/agents/{agentId}/debug`，debug 响应新增 `runtimeSource`、`publishedVersionId`、`workflowCodePreview`。
  - 新增 `OrchestratorIntegrationTest.shouldUsePublishedWorkflowInDebugRuntime`，验证 debug runtime 优先使用已发布版本。
  - debug runtime 已升级最小执行器输出：`/agents/{agentId}/debug` 新增 `executionStatus` 与 `executionOutput`，并在 trace 中输出执行状态（`published-executed` / `fallback-executed` / `published-invalid`）。
  - 聊天主链路已复用最小执行器：`/ai/chat` 响应新增 `runtimeExecution.status/output/publishedVersionId`，并补集成断言验证发布版本状态可见。
  - debug/chat 已统一输出节点级 `executionTrace`：`workflow-node:start -> route-input -> tool-scope -> end:*`，形成最小可扩展执行轨迹协议。
  - 已接入 `workflow_code` 最小节点解析：从发布代码 `runAgent` 方法体识别并输出代码节点（如 `intent-classify`、`knowledge-search`、`tool-invoke-best`、`response-generate`）。
  - 已接入 `contextSnapshot` 最小状态快照：debug/chat 均可返回 `runtimeSource`、`inputRoute`、`toolScopeSize`、`intent`、`parsedNodes`、`knowledgeUsed`、`toolInvoked`、`responsePlanned`。
  - 已接入执行期指标快照：新增 `branchHit`、`nodeMetrics[{nodeId,costMs,status}]`、`errorNode/errorType`，并完成 debug/chat 统一透出与集成断言。
  - 已接入可回放摘要协议：`nodeMetrics` 新增 `ioSummary(input/output)`，并新增 `replayHint`，支持按节点顺序进行最小重放。
  - 已接入结构化节点 I/O：`nodeMetrics` 新增 `ioPayload.input/output`。
  - 新增 fallback 专项回放断言：`shouldExposeFallbackReplayMetadataInDebugRuntime`。
  - 新增 invalid 专项回放断言：`shouldExposeInvalidReplayMetadataInDebugRuntime`。
  - `OrchestratorIntegrationTest` 与 `ChatRealtimeIntegrationTest` 回归通过，`TASK-002` 既定冲刺范围闭环。
- remaining:
  - （本任务冲刺目标已完成，无阻塞剩余项）
- next_action: 转入 `TASK-006` 与 MCP 真实 smoke 验收闭环。
- handoff_notes:
  - 这是 Agent Builder 主线当前最关键的产品闭环缺口。
  - 若开始实现，应补独立 feature spec，而不是只在 baseline 中追加描述。

### TASK-003 Replace simulated debug with runtime trace acceptance path

- status: completed
- priority: P0
- owner_role: fullstack-agent-builder
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- depends_on: `ISSUE-2026-04-21-agent-debug-still-simulated`
- summary: 把 Agent Builder 的试运行/调试从“前端模拟路径”收口到可验证的真实后端 trace。
- done:
  - 后端已具备 `POST /agents/{agentId}/debug`，返回 `runtimeSource/publishedVersionId/executionStatus/executionTrace/contextSnapshot/policyBundle/resolvedSkillVersions/runtimeGovernanceNotes` 等结构化调试证据。
  - 前端 `AgentBuilderShell.runDebug()` 现已改为优先消费后端调试结果：成功时高亮真实运行路径，并展示 runtime governance 摘要；仅在接口失败时回退到 `simulateDebugTrace(...)`。
  - 已补验证：`backend` `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest#shouldUsePublishedWorkflowInDebugRuntime test` -> success；`frontend` `npm run build` -> success。
- next_action: 如后续继续增强，可把节点级 `nodeMetrics/ioPayload` 结构化展示到调试面板，而不只显示文字摘要。

### TASK-004 Brownfield state protocol adoption

- status: completed
- priority: P1
- owner_role: ai-collaboration
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- summary: 为既有仓库补齐最小 `.claw/` 状态协议骨架，避免状态文件继续只靠 `current-status.md` 堆积。
- done:
  - 保留 `.claw/` 作为 canonical state directory。
  - 重写 `current-status.md` 为短快照。
  - 初始化 `task-board.md`。
  - 初始化 `docs/specs/PROJECT-BASELINE.md`。
- handoff_notes:
  - 后续 session 默认先读 `current-status.md` + `task-board.md`；涉及遗留架构判断时再读 `docs/specs/PROJECT-BASELINE.md`。

### TASK-005 Skill authoring fallback alignment

- status: completed
- priority: P1
- owner_role: backend-skill-authoring
- spec_path: `docs/specs/FEAT-001-skill-authoring-generic-generation.md`
- summary: 把“自然语言创建技能”从内置行业模板依赖改为“模型通用理解优先 + fallback 做 sourceText 结构化提取”。
- done:
  - 新增 feature spec `FEAT-001-skill-authoring-generic-generation.md`。
  - 模型 system prompt 明确禁止按内置行业样例强行套场景，要求优先保留 sourceText 中的事实与边界。
  - 无模型 fallback 不再依赖审批/CRM/合同等固定模板，而是通用提取 skillCode/name/source facts/显式工具/编号步骤/输出要求。
  - 新增 source facts 保留逻辑，避免无编号步骤时又退回抽象模板。
  - 更新 `SkillAuthoringIntegrationTest`，覆盖“通用生成 + 自定义工具 + 营销活动流程”场景。
- next_action: 在真实管理端 UI 上再做一轮人工回归，确认无模型 fallback 和有模型路径都与用户需求贴合，并观察 preview compile 是否引入次级领域偏差。
- handoff_notes:
  - 本次确认的设计问题是：skill authoring 不应以少量内置行业样例为强依赖。
  - 本地默认 `skill-authoring` 场景仍无可用模型，因此当前验证重点是“即使完全走 fallback，也不再被固定模板带偏”。

### TASK-006 CiCi session continuity and state layer

- status: completed
- priority: P0
- owner_role: backend-chat-runtime
- spec_path: `docs/specs/FEAT-002-cici-session-continuity-and-state.md`
- summary: 为思思补齐“会话历史回灌 + session state + 状态驱动追问约束”，解决同一会话内多轮失忆与重复追问问题。
- done:
  - 新增 `V22__chat_session_state.sql`，落地 `chat_session_state` 持久层。
  - 新增 `ChatSessionStateEntity / Repository / Service`，支持会话状态读取、注入块构建、用户输入与工具结果基础 reducer。
  - `ChatOrchestratorService` 已注入最近历史消息（最近 20 条窗口，排除当前用户 turn）和 session state 块。
  - 新增 `GET /ai/sessions/{sessionId}/state` 调试接口，便于管理端/调试页查看状态。
  - 新增集成测试 `shouldPersistSessionStateAfterUserIntentHint`，并通过 `OrchestratorIntegrationTest` 与 `ChatRealtimeIntegrationTest` 回归。
  - 新增集成测试 `shouldKeepSessionStateAcrossSecondTurn`，验证同 `sessionId` 下双轮状态连续与消息历史持久化。
  - `ChatSessionStateService` 已增强语义抽取与字段映射：补齐 `current_object_*`、`target_segment_summary`、`missing_fields`、`next_action`、`no_repeat_questions`。
  - 新增集成测试 `shouldCaptureSessionFieldsAndNoRepeatConstraintAcrossTurns`，覆盖“同 session 第二轮不重复追问”的确定性状态约束断言。
  - `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` 与 `ChatRealtimeIntegrationTest` 回归通过。
- next_action: 转入 FEAT-002 人工回归与复杂语句覆盖评估（必要时引入 LLM 抽取器）。
- handoff_notes:
  - 当前已完成“会话历史回灌 + 会话状态持久层”最小闭环，不再只依赖长期用户记忆。
  - 会话状态仍应保持在 `chat_session_state`，不要把会话内动作确认误写进 `UserMemoryService`。

### TASK-007 SaaS billing and packaging design

- status: pending
- priority: P1
- owner_role: product-platform
- spec_path: `docs/specs/FEAT-003-saas-billing-and-packaging.md`
- summary: 为当前企业多组织 AI 助手平台设计适合 SaaS 化交付的企业混合计费模型，覆盖套餐、计量、账单与超额治理方向。
- done:
  - 已新增 `FEAT-003-saas-billing-and-packaging.md`，明确平台订阅、席位、AI 用量、Agent/Workflow、知识库、工具集成、企业增值模块七类计费项。
  - 已明确主计费主体为组织/租户，建议采用 `平台订阅 + 资源席位 + AI/自动化用量 + 增值模块` 模型。
  - 已将计量来源映射到现有 `ChatOrchestratorService`、`AgentWorkflowRuntimeService`、工具调度链路、知识库处理链路和 `ops/metrics/cost` 雏形。
- next_action: 若进入实现阶段，先拆 `usage_meter_event`、套餐/订阅实体与管理端账单总览页，不要先接支付系统。
- handoff_notes:
  - 这是产品与平台设计任务，当前只完成 spec，不代表账单域已在代码中落地。
  - 后续实现时优先建立统一计量事件和组织级阈值控制，再做价格表、账单和发票域。

### TASK-008 Adopt cloudcc AI dev guidelines as durable protocol

- status: completed
- priority: P2
- owner_role: ai-collaboration
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- summary: 按 `cc-aidev-guidelines-common` `3.4.0` 对齐当前 brownfield 仓库的项目状态协议，补齐项目级声明、热状态快照、任务卡与 baseline 的最小一致性。
- done:
  - 保持 `.claw/` 为 canonical state directory，不启用 `.ai-dev/` 双写。
  - 在项目根 `README.md` 与新建 `AGENTS.md` 中补齐受管声明块，明确所有 AI agent 必须先加载 `cc-aidev-guidelines-common`。
  - 将 `.claw/current-status.md` 收口为“快照 + 指针”，不再继续堆积长历史日志。
  - 更新 `.claw/task-board.md` 与 `docs/specs/PROJECT-BASELINE.md`，让 active task、baseline 与当前真实阻塞保持对齐。
  - 已执行 `python3 /Users/owenspace/.agents/skills/cloudcc-aidev-guidelines-common/scripts/validate-state.py .claw`，状态校验通过。
- handoff_notes:
  - 后续继续遵循“少读少写、事实可验证、source-of-truth 不重复”。
  - 若将来要把 `validate-state.py` 内置到仓库，再单独起新任务，不要把工具引入和状态补录混成一次大改。

### TASK-009 FEAT-006 virtual human page MVP

- status: completed
- priority: P1
- owner_role: frontend-assistant-experience
- spec_path: `docs/specs/FEAT-006-virtual-human-multitask-workbench.md`
- summary: 按 FEAT-006 落地虚拟人场景页 MVP，支持沉浸式背景、虚拟人状态联动、任务卡片层与文本/语音输入联动。
- done:
  - 扩展 `streamAiChat`：增加未知 SSE 事件透传回调，支持消费 `task_created/task_status/task_delta/task_done/avatar_state`。
  - `AssistantApp` 的 `scene` 页接入真实 `/ai/chat/stream`，实现虚拟人状态机（`idle/listening/thinking/speaking`）与任务卡状态更新。
  - `scene` 页新增任务卡工作区，支持按 `taskId` 聚合任务状态、摘要和增量内容。
  - 语音输入在 `scene` 页下支持自动填充并自动发送，打通 `useAsrVoiceInput -> streamAiChat` 路径。
  - 更新 `cici-ui.css` 场景样式：虚拟人状态动画、任务卡样式、麦克风激活态、发送按钮与提示区。
- next_action: 如进入下一轮 FEAT-006，优先补后端 task 事件完整协议与任务持久化，减少前端 fallback 到单任务的分支逻辑。
- handoff_notes:
  - 当前实现聚焦 FEAT-006 Phase 1 视觉 MVP；后端若未下发 task 事件，前端会以首任务兜底展示流式结果。
  - 已完成 `frontend npm run build` 验证，结果记录见 `.claw/test-report.md`。

### TASK-010 FEAT-006 backend task/avatar stream protocol

- status: completed
- priority: P1
- owner_role: backend-chat-runtime
- spec_path: `docs/specs/FEAT-006-virtual-human-multitask-workbench.md`
- summary: 为 `/ai/chat/stream` 补充虚拟人页需要的 task/avatar 事件协议，让 scene 页可消费真实后端事件。
- done:
  - `ChatOrchestratorService.chatStream` 新增 `avatar_state` 事件发送：`thinking -> speaking -> idle`。
  - 新增 `task_created/task_status/task_delta/task_done` 事件发送，按单主任务流驱动前端任务卡状态更新。
  - 工具调用阶段新增任务状态推进：tool calling、审批等待（`waiting_user`）等。
  - 保持现有 `delta/tool_call/tool_result/phase/done/error` 兼容，不破坏已有聊天消费链路。
  - 已完成 `backend mvn -q -DskipTests compile` 与 `frontend npm run build` 验证。
- next_action: 下一轮可在此基础上引入 `chat_task/chat_task_event` 持久化与多任务拆分规则（并行/串行）。
- handoff_notes:
  - 当前 `TASK-010` 聚焦协议打通，不包含数据库任务实体迁移。
  - taskId 当前为 session 派生的轻量 ID，可在持久化阶段替换为真实任务主键。

### TASK-011 Workbench session history alignment

- status: completed
- priority: P0
- owner_role: frontend-assistant-experience
- spec_path: `docs/specs/FEAT-006-virtual-human-multitask-workbench.md`
- summary: 修复工作台会话历史与右侧历史列表错位、上下文不完整问题；让工作台会话按智能体隔离并回灌真实持久化消息。
- done:
  - 新增 `frontend/src/assistant/workbenchSessions.ts`，统一工作台 sessionId 规则（`workbench:{agentKey}`）与历史抽取逻辑。
  - 新增 `frontend/src/assistant/workbenchSessions.test.ts`，覆盖 sessionId 与历史抽取行为。
  - `AssistantApp` 在工作台模式下新增 `loadWorkbenchMessages(...)`，从 `/ai/sessions/{sessionId}/messages` 加载真实历史并同步到工作台消息状态。
  - 工作台发送消息后会触发历史强制刷新，避免仅依赖本地乐观更新导致的历史偏差。
  - 右侧“会话历史”改为与主对话区同源消息集，消除“主对话与右侧历史不对应”的现象。
- verification:
  - `frontend`: `npm run test -- src/assistant/workbenchSessions.test.ts` -> success
  - `frontend`: `npm run build` -> success
- handoff_notes:
  - 虚拟人页优化按用户要求暂缓；本任务只处理工作台会话历史一致性。

### TASK-012 Conversation grouping and new-dialog entry

- status: completed
- priority: P0
- owner_role: frontend-assistant-experience
- spec_path: `docs/specs/FEAT-006-virtual-human-multitask-workbench.md`
- summary: 在会话历史列表顶部新增“新对话”入口，并将会话组织为“一个多轮上下文=一个会话”，避免每条消息形成独立会话记录。
- done:
  - 在会话列表头部新增“新对话”按钮，支持创建本地草稿会话并立即切换。
  - 新会话使用稳定 `sessionId`（`chat:new:{userId}:{agentId}:{nonce}`）持续复用，保证后续多轮消息归入同一会话。
  - 会话列表刷新时保留未落库草稿会话；首条消息发送后可无缝与后端持久化会话合并。
  - `loadConversationMessages` 对 404 会话做空历史兜底，避免新建未发送会话时出现错误提示。
  - 新增会话列表样式 `cici-threads__new-btn`，与现有层级风格保持一致。
- verification:
  - `frontend`: `npm run build` -> success
- handoff_notes:
  - 本任务不涉及虚拟人页面（用户已明确暂停该方向）。

### TASK-013 Workbench history delete and markdown export

- status: completed
- priority: P1
- owner_role: fullstack-assistant-experience
- spec_path: `docs/specs/FEAT-006-virtual-human-multitask-workbench.md`
- summary: 为思思工作台会话历史新增“删除会话”和“下载 Markdown”能力，支持会话级清理与本地归档。
- done:
  - 前端会话历史每项新增操作区，支持“下载”“删除”按钮。
  - 下载会把会话完整消息导出为本地 `.md` 文件（含会话元信息与按轮次排布的消息内容）。
  - 后端新增 `DELETE /ai/sessions/{sessionId}`，删除会话主记录、消息记录与会话状态记录。
  - 前端删除后同步更新本地会话列表与当前激活会话，避免空选中态。
- verification:
  - `frontend`: `npm run build` -> success
  - `backend`: `mvn -q -DskipTests compile` -> success
- handoff_notes:
  - 删除能力当前面向工作台会话使用场景；其余页面可后续按权限策略接入。

### TASK-014 Workbench composer UI redesign

- status: completed
- priority: P1
- owner_role: frontend-assistant-experience
- spec_path: `docs/specs/FEAT-006-virtual-human-multitask-workbench.md`
- summary: 思思工作台对话框按参考图重构——左下角新增「➕」弹出菜单（上传文件/添加快捷短语），发送按钮替换为深灰圆形上箭头图标。
- done:
  - 新增 `showPlusMenu` 状态控制弹出菜单显隐。
  - 新增 `cici-composer-plus` 容器：`➕` 按钮（32x32 圆角）+ 弹出菜单（上传文件/添加快捷短语两项）。
  - 弹出菜单定位在按钮上方，点击外部自动关闭。
  - 发送按钮替换为 `cici-workbench__send-btn`：36x36 深灰圆形 + 白色上箭头 SVG。
  - 移除原 "发送"/"发送中" 文字按钮。
  - 发送后自动关闭弹出菜单。
- verification:
  - `frontend`: `npm run build` -> success
  - `frontend`: `npx tsc -b --noEmit` -> success
- handoff_notes:
  - 「上传文件」和「添加快捷短语」按钮当前为视觉占位，未接入实际功能逻辑。
  - 仅修改了工作台（workbench）对话框，未影响对话页（chat）`cici-composer`。

### TASK-015 Fix CiCi default tool autonomy

- status: completed
- priority: P0
- owner_role: backend-chat-runtime
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- summary: 修复思思默认对话中“有工具但难以自主调用有效工具”的问题，确保默认运行时既保留全局工具调用规则，也暴露 CloudCC 对象/字段发现能力。
- done:
  - `SkillPromptAssembler` 改为保留全局基础提示词，并叠加 agent 专属规则，避免既有 agent system prompt 覆盖掉全局“该用工具时主动调用工具”的约束。
  - `AliyunBailianClient` 全局提示词新增“当可用工具能提供事实或记录时主动调用工具而不是猜测”规则。
  - `SkillResolverService` 为运行时 `cici-system` 默认工具集补齐 `cloudcc_getStandardObjects`、`cloudcc_getCustomObjects`、`cloudcc_getObjectFields`。
  - `AgentDefinitionService` 的新组织内置 `cici-system` agent 种子同步补齐 CloudCC 发现类工具和主动调工具提示。
  - 新增回归 `OrchestratorIntegrationTest#shouldExposeCloudccDiscoveryToolsForDefaultCiciAgent`，验证默认思思工具范围包含 CloudCC 发现工具。
- verification:
  - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest#shouldExposeCloudccDiscoveryToolsForDefaultCiciAgent test` -> success
  - `backend`: `mvn -q -DskipTests compile` -> success
- next_action: 先修复 CloudCC 绑定凭证与聊天入口模型 key，再在工作台人工回归“查客户资料 / 查对象字段 / 查待审批”等典型话术，确认思思会优先发起工具调用而不是只输出泛化文本。
- handoff_notes:
  - 本次修复对现有组织立即生效依赖运行时 resolver 兜底，不要求先重建 builtin agent 或重绑 skill。
  - 更大范围的 `OrchestratorIntegrationTest` 当前仍受短信频控和既有调度唯一键问题影响，不能把整类失败解读为本任务回归失败。
  - 2026-04-30 真实 smoke 补充结论：`effectiveToolNames` 已包含 CloudCC 发现/查询工具，但 `13800000001/哪吒` 的 CloudCC token 换取仍返回 `Please check your username and password.`；同时 `sales-agent` 聊天入口还受 `Aliyun API key is not configured.` 阻塞，需与外部凭证问题分开排查。

### TASK-016 Workbench reply-area blanking guard

- status: completed
- priority: P0
- owner_role: frontend-assistant-experience
- spec_path: `docs/specs/FEAT-006-virtual-human-multitask-workbench.md`
- summary: 修复工作台发送后“先显示思考中，再整段变空白”的问题，避免后端暂时缺少 assistant 历史时把本地流式消息覆盖清空。
- done:
  - 在 `loadWorkbenchMessages(...)` 增加保护：若后端刷新结果没有 assistant 内容，而本地已有 assistant 流式文本，则保留本地消息，不做覆盖。
  - 保持 `conversationMessages` 正常更新，避免影响历史同步和会话刷新路径。
- verification:
  - `frontend`: `npm run build` -> success
- next_action: 人工复现“看下今天的潜在客户”等真实问题，确认 UI 不再从图1跳到空白图2。
- handoff_notes:
  - 该修复是前端防护层；若后端持续写入空 assistant 内容，仍需要继续排查后端流式落库链路。

### TASK-017 Remove virtual human surface

- status: completed
- priority: P1
- owner_role: fullstack-assistant-experience
- spec_path: `docs/specs/FEAT-007-remove-virtual-human-surface.md`
- summary: 按用户要求正式下线助手端“虚拟人”功能，移除菜单入口、scene 页面、专属 SSE 事件和静态封面页，并同步项目文档。
- done:
  - `AssistantApp` 已移除 `scene` 标签页、状态、语音分支和整段沉浸式页面渲染。
  - `streamAiChat` 已回收仅供 scene 页使用的未知事件透传参数。
  - `ChatOrchestratorService.chatStream` 已移除 `avatar_state/task_created/task_status/task_delta/task_done` 发送逻辑，保留通用聊天流事件。
  - `frontend/public/ai-cover.html` 与 `frontend/public/vh-cover.html` 已删除。
  - 已新增 `FEAT-007` 下线 spec，并将 `FEAT-006` 标记为 retired。
- verification:
  - `frontend`: `npm run build` -> success
  - `backend`: `mvn -q -DskipTests compile` -> success
- next_action: 人工回归工作台、监控、客户会话和 CRM 入口，确认移除 scene 分支后导航和语音输入无回归。
- handoff_notes:
  - `FEAT-006` 只保留历史设计上下文，当前产品能力以下线 spec 和 `.claw/current-status.md` 为准。
  - 如果后续要恢复该方向，应重新立项，不要直接恢复旧 scene MVP 代码。
