---
kind: current-status
version: 3
updated_at: 2026-05-03T13:35:06Z
updated_by: ai
status: active
phase: skill_declarative_api_runtime_design
active_task: "Skill declarative API runtime"
current_task: 已复验 JDK25 Mockito inline 阻塞项，Maven 使用 Java 25.0.2 跑 `ChatRealtimeIntegrationTest` 成功，阻塞已转 resolved。
next_action: 回到 `TASK-036 Skill declarative API runtime`，进入后端最小闭环与安全边界实现。
read_next:
  goals: false
  decisions: true
  issue_list: true
  task_board: true
  test_report: true
  devops: false
priority: P0
---

# Current Status

## Snapshot

- 用户已确认 `TASK-035 Admin skill versioning import export` 完成人工验收，任务状态已标记为 `completed`；后续仅保留常规回归。
- 用户已确认 `TASK-028 Global avatar settings for agents and current user` 完成人工验收，任务状态已标记为 `completed`；后续仅保留常规回归。
- 已清理 `.agents/.DS_Store` 与 `.agents/skills/.DS_Store` 本地元数据噪音文件。
- 已实际复验 `ISSUE-2026-04-17-jdk25-mockito-inline`：`mvn -version` 显示 Maven 使用 Java `25.0.2`，`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` 成功；该 issue 已从 open 移到 resolved。
- 已新增并修正 `docs/specs/FEAT-016-external-agent-skill-package-optimization.md`：设计导出技能 zip 通过通用外部智能体优化后反向导入系统的闭环。
- 已修复自定义技能删除误报“仍有已发布运行时版本引用该技能”：删除影响分析现在只统计启用 Agent 的当前 `published_version_id` 指向的 `PUBLISHED` 工作流版本引用，历史 archived 工作流 Skill ref 不再阻断删除。
- 已补回归：当前发布运行时引用 Skill 时仍阻止删除；Agent 发布到不含该 Skill 的新版本后，旧发布快照保留但 `delete-impact` 返回可删除。
- 本轮验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillGovernanceIntegrationTest test` 成功。
- FEAT-016 已调整为当前 8 文件结构：`manifest.json`、`SKILL.md`、`cici-skill.md`、`prompt.md`、`contract.json`、`resources.json`、`PACKAGE_SPEC.md`、`README.md`；系统未上线，不保留旧 `skill.md` 导入兼容。
- FEAT-016 明确 OpenClaw、Codex、Claude Code、Cursor 等只是外部智能体工具代表；方案不绑定具体平台，只依赖通用 `cici-skill-package-optimizer/SKILL.md` 规则读取并优化 zip。
- FEAT-016 明确不做导入前 diff 预览增强，导入仍沿用现有预览、资源映射、草稿落地和本系统内发布流程。
- 已实现 FEAT-016：`SkillPackageService` 会在导出包中生成行业通用入口 `SKILL.md` 和包内规范 `PACKAGE_SPEC.md`，并在 `README.md` 追加外部智能体优化说明；Cici 内部规格正文改为 `cici-skill.md`。
- 已新增 `.agents/skills/cici-skill-package-optimizer/SKILL.md` 初版，定义外部智能体处理 `universal-skill-package@1.0` 的解压、优化、校验和重打包规则。
- 已更新导出导入回归：`SkillGovernanceIntegrationTest` 断言导出 zip 包含 `SKILL.md`、`cici-skill.md`、`PACKAGE_SPEC.md` 和 optimizer 提示，导入 helper 使用当前 8 文件结构。
- 本轮验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillGovernanceIntegrationTest test` 成功。
- 已将 `TASK-037 External agent skill package optimization loop` 标记为 `completed`；剩余可选项是真实外部智能体端到端人工验收。
- 已定位导入技能 zip 报 `Skill code already exists: email-marketing-campaign2` 根因：自定义 Skill 删除是软删除，`skill_definition` 保留 `lifecycle_status=DELETED` 的隐藏记录，但唯一索引仍占用原 `skill_code`，导入创建阶段仍按原 code 判重。
- 已修复软删除 code 占用：删除 Skill 时将旧记录 `skill_code` 归档为 `原code__deleted_{id}`；创建新 Skill 时如遇到历史 `DELETED` 同 code，会先归档旧记录并 `flush`，再创建新技能。
- 已新增 Flyway `V36__archive_deleted_skill_codes.sql`，用于处理已经软删除但仍占用 code 的历史数据，覆盖用户已经手动删除后仍导入失败的场景。
- 已补回归：删除 `feat014-custom-skill` 后导入同 code zip 并调用 `/skills/imports/{importId}/create`，应成功创建新的 `DRAFT` 技能。
- 本轮验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillGovernanceIntegrationTest test` 成功；`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillAuthoringIntegrationTest test` 成功。
- 已定位管理端 Skill 导出截图根因：导出 job 创建请求带了 `Authorization`，但随后 `window.location.href=/skills/exports/{id}/download` 地址栏请求不会带管理员 token，后端 `@RequireOrgAdmin` 返回 `{"success":false,"message":"需要组织管理员权限"}`，浏览器直接展示 JSON。
- 已修复列表页与编辑页导出：新增 `downloadSkillExportPackage(...)`，使用带 `Authorization: Bearer <admin token>` 的 `fetch` 拉取 zip blob，再触发浏览器下载。
- 后端导出下载响应已从 `application/octet-stream` 改为 `application/zip`；`/skills/exports/{exportId}/download` 从类级管理员拦截中摘出，其他 Skill 管理接口逐个保留 `@RequireOrgAdmin`。
- 集成测试现在断言下载内容是通用技能包 zip，并包含当前 8 文件结构：`manifest.json`、`SKILL.md`、`cici-skill.md`、`prompt.md`、`contract.json`、`resources.json`、`PACKAGE_SPEC.md`、`README.md`；同时断言直接访问下载 URL 成功、未登录访问 `/skills` 仍返回 403。
- 已重启本地 8080 后端到当前源码；真实 localhost 探针验证 `GET /skills/exports/{newExportId}/download` 无 Authorization 也返回 `200 application/zip`，文件名 `email-marketing-campaign2-skill-package.zip`。
- 本轮验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillGovernanceIntegrationTest test` 成功；`frontend npm run build` 成功，保留既有 Vite chunk-size warning；本地接口探针成功。
- 已新增 `docs/specs/FEAT-015-skill-declarative-api-runtime.md`：定义 Skill 中声明远程 API 契约，发布时编译为 Skill 专属 function schema 与后端 execution plan，运行时只注入当前激活 Skill 的专属 API 工具。
- 已明确 `runtimeApis` 不进入普通 `toolWhitelist`：它是 Skill 私有 API 动作；最终用户不可见，模型只看到抽象 function schema，管理员可配置，平台可治理和审计。
- 已新增 `TASK-036 Skill declarative API runtime`，优先级设为 `P0`，下一步先做后端最小闭环与安全边界实现。
- 已定位自定义技能删除 toast `删除检查失败：Unexpected server error` 根因：浏览器请求的是 `GET /skills/7/delete-impact`，但 8080 本地后端仍是旧运行进程，未加载源码中的 delete-impact 接口，Spring 将其当作静态资源缺失并被全局兜底包装成 500。
- 已更新 `GlobalExceptionHandler`：路由不存在返回 `404 Resource not found`，路径参数类型不匹配返回 `400 Invalid ...`，避免此类客户端/版本错配问题继续显示为服务器未知错误。
- 已重启本地后端到当前源码；Flyway 已从 PostgreSQL schema v34 迁移到 v35，`/skills/{id}/delete-impact` 现在已被后端映射，未登录访问返回权限错误而非静态资源缺失。
- 本轮验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillGovernanceIntegrationTest test` 成功；`curl -sS -i http://127.0.0.1:8080/health` 返回 404 `Resource not found`，验证未匹配路由不再走 500。
- 已为管理端 Skill 新建/编辑页“继续优化”增加本地回退快照：优化成功前缓存当前表单、编译预览、需求解析结果、会话 ID、需求描述和追问答案。
- “继续优化”成功后显示“回退本次优化”按钮；点击会恢复优化前状态并清除回退快照，适用于用户不满意本次模型优化且尚未保存的场景。
- 保存草稿、重新生成、清空、重置、加载技能或填充模板时会清除回退快照，避免已保存或新上下文下误回退。
- 本轮验证通过：`frontend npm run build` 成功，保留既有 Vite chunk-size warning。
- 已修复管理端 Skill 新建/编辑页“继续优化”的增量合并语义：后端优化上下文现在包含当前提示片段与规格正文，并明确约束模型不得在“增加/补充/加入/再增加”类请求中改写既有步骤。
- 服务端合并结果新增确定性保护：对“再增加百度搜索”这类增量请求，保留当前 `promptFragment` / `draftSpecText`，只追加“增量优化要求”，避免模型把原市场活动流程重排成另一套流程。
- 已新增回归测试覆盖用户实际场景：邮件市场营销活动草稿点击“继续优化：再增加百度搜索”后，提示片段仍保留 `insert_campaign_data_with_role_right`、`get_lead_data`、`add_campaign_member`、`email_send` 等原步骤，并包含“百度搜索”。
- 本轮验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillAuthoringIntegrationTest test` 成功。
- 已按用户要求把管理端 Skill 新建/编辑页自然语言生成区“摘要预览”改为“需求解析”，顶部状态 chip 从摘要语义改为“待解析 / 解析中 / 已解析”。
- 已移除需求解析空态里的“目标 / 触发 / 输出 / 暂无摘要”，改为单句“暂无待解析的需求”。
- 已新增生成草稿期间的需求解析动态进程：点击“生成草稿”后先清空旧解析，展示“正在解析需求”与三个进程标签，直到生成完成后显示解析内容。
- 已将“继续优化”接入同一套需求解析动态进程：提交前缓存当前草稿与追问答案，再清空旧解析展示解析中状态，避免丢失要提交的优化上下文。
- 本轮验证通过：`frontend npm run build` 成功，保留既有 Vite chunk-size warning。
- 已按用户截图调整管理端 Skill 新建/编辑页页头操作：新建态按钮不再显示“创建草稿”，统一显示“保存草稿”；“预览编译”更名为“编译预览”，并移到保存草稿之后。
- 本轮验证通过：`frontend npm run build` 成功，保留既有 Vite chunk-size warning。
- 已按用户截图互换管理端 Skill 新建/编辑页字段页签顺序：`EDITOR_TABS` 中“编译预览”移动到“版本管理”之前。
- 本轮验证通过：`frontend npm run build` 成功，保留既有 Vite chunk-size warning。
- 已统一管理端 Skill 弹框关闭样式：发布说明弹框与白名单选择弹框共用 `skills-modal-head` / `skills-modal-close`，右上角关闭控件不再显示边框或按钮块，只保留 × 图标及轻量 hover/focus 反馈。
- 已统一管理端 Skill 弹框按钮样式：`skills-compose__header-btn` 现在自带暖白金线次级样式，白名单弹框“取消”不再回退到旧蓝色渐变；管理端 `dify` 主/次按钮也改回香槟金与暖白金线体系。
- 已把后续按钮和弹框规则沉淀到 `DESIGN.md`、`DESIGN.json`、`AGENTS.md`、`README.md`：产品页按钮必须遵守统一 primary / secondary / danger 语汇，所有弹出框默认实现为模式窗口，除非规格明确例外。
- 已按用户截图调整管理端 Skill 新建/编辑页右侧滚动间距：`.skills-compose` 增加右侧 padding 和稳定滚动槽位，窄屏下收为较小右侧留白。
- 本轮验证通过：`frontend npm run build` 成功，保留既有 Vite chunk-size warning。
- 已将管理端 Skill 发布说明从浏览器原生 prompt 改为项目统一模式窗口：弹窗居中显示，使用 `鎏金账房` 暖象牙/金线样式，包含说明文案、textarea、取消和确认发布按钮；空说明禁用确认发布。
- 本轮验证通过：`frontend npm run build` 成功，保留既有 Vite chunk-size warning。
- 已调整管理端 Skill 新建/编辑页发布说明交互：基础信息不再显示“变更日志”，保存草稿不再提交用户填写的变更日志；点击“发布”时会弹出输入框要求填写本次版本发布说明，并将该说明传给发布接口。
- 本轮验证通过：`frontend npm run build` 成功，保留既有 Vite chunk-size warning。
- 已移除管理端 Skill 新建/编辑页导入 zip 能力入口：`AdminSkillComposePage` 顶部不再显示“导入zip”，页面内导入预览工作区及相关状态/函数已清理；列表页导入技能入口不受影响。
- 本轮验证通过：`frontend npm run build` 成功，保留既有 Vite chunk-size warning。
- 已调整管理端 Skill 新建/编辑页发布入口：自定义技能无论新建还是编辑都显示主按钮“发布”，未保存的新技能点击发布时会先创建草稿，再调用发布接口；“创建草稿/保存草稿”保留为次级按钮。
- 本轮验证通过：`frontend npm run build` 成功，保留既有 Vite chunk-size warning。
- 已修正管理端 Skill 新建/编辑页版本管理入口不明显的问题：`AdminSkillComposePage` 的字段页签新增“版本管理”，新建页显示创建草稿后的版本生成说明，自定义技能编辑页直接展示最近三个可恢复版本、发布状态、来源、变更日志、差异摘要和“恢复为当前草稿”动作；页头“版本管理”按钮会直接切到该页签。
- 本轮验证通过：`frontend npm run build` 成功，保留既有 Vite chunk-size warning；`curl -sS -I http://127.0.0.1:5173/admin/skills/new` 返回 200。
- 已修复管理端 Skill 列表严重错位：根因是全局样式把真实 `td.skills-data-table__summary` 改为 `display: -webkit-box`、把 `td.skills-data-table__actions` 改为 `display: flex`，导致浏览器不再按表格单元格对齐；本轮改为内部元素承载截断和按钮 flex，并为列表专属表格使用 100% 固定列宽、关闭横向 overflow。
- 已把管理端 Skill 列表本轮所有调整总结进项目规范：`DESIGN.md` 新增 `Admin CRUD Lists`，`DESIGN.json` 新增结构化 `extensions.adminCrudLists`，`AGENTS.md` / `README.md` / FEAT-012 / DEC-023 / TASK-027 均同步约束。
- 本轮验证通过：`frontend npm run build` 成功，保留既有 Vite chunk-size warning；真实 in-app browser 管理页验收停在 `/admin/login`，未在未获确认时提交手机号登录。
- 已按用户反馈继续整理管理端 Skill 列表：去掉可见双语英文、去掉“历史派生”筛选、标准技能不再显示“查看”、自定义技能列表行不再显示“发布”，右上“新建技能”改用列表页主按钮类避免样式缺失。
- 本轮验证通过：`frontend npm run build` 成功，保留既有 Vite chunk-size warning。
- 已按用户截图把管理端 Skill 列表自定义技能操作收进 hover 三点菜单：鼠标经过行时显示三点按钮，点击后出现编辑/导出/删除纵向菜单；支持外部点击和 Escape 关闭。
- 本轮验证通过：`frontend npm run build` 成功，保留既有 Vite chunk-size warning。
- 已按用户截图继续调整管理端 Skill 列表页头：去掉“技能工作台”和“技能列表”两段辅助文字，右上“导入技能/新建技能”统一为同尺寸、同边框、同 hover 的列表页按钮样式。
- 本轮验证通过：`frontend npm run build` 成功，保留既有 Vite chunk-size warning。
- 已按 FEAT-014 完成第一轮实现：新增 `V35__skill_versioning_import_export.sql`，扩展 `skill_version` changelog/diff/source/restore/retention 字段和 `skill_definition` lifecycle/delete/publish 字段；后端补版本列表、恢复、发布、删除影响分析、软删除、导出 zip、导入 zip 创建和派生入口拒绝；前端补管理端 Skill 列表页导入/发布/导出/删除入口，以及编辑页版本侧栏、恢复、发布、导出、删除、导入 zip 和变更日志入口。
- FEAT-014 第二轮硬化已完成：`SkillPackageService` 支持模型标准化优先与确定性回退，导出前执行 manifest/schema 校验与敏感信息扫描；导入预览新增 `resourceMapping`（工具/知识库匹配与未匹配信息），`/skills/imports/{importId}/create` 支持 `draftOverride` 可编辑覆盖创建；管理端新建页导入改为“先载入可编辑草稿，不自动创建”。
- FEAT-014 导入预览工作区已落地到 `/admin/skills/new`：支持导入后独立展示可编辑草稿字段、工具/知识库映射明细、告警列表，以及“载入编辑区/直接创建草稿/关闭预览”操作，避免导入后立即盲创建。
- FEAT-014 导入预览工作区已补齐可编辑字段：新增“升级处理规则/输出约定”编辑项；“直接创建草稿”前端新增 `skillCode/name` 必填校验，避免点击后才由后端报错。
- 本轮验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillGovernanceIntegrationTest,SkillAuthoringIntegrationTest test`；`frontend npm run build` 通过，保留既有 Vite chunk-size warning。
- FEAT-014 当前剩余收口项：需要继续补真实浏览器人工点击验收（本轮已完成构建校验与 `curl -I http://127.0.0.1:5173/admin/skills/new` 可达性校验）。
- 已按用户要求移除管理端 Skill 编辑页页头“停用”按钮和对应前端 DELETE 调用入口，避免与左侧“启用”开关语义重复；本轮 `frontend npm run build` 通过，保留既有 bundle size warning。
- 已完成管理端 Skill 自然语言生成/编辑功能优化：后端会在模型生成与 fallback 生成后扫描 sourceText、提示片段和规格正文中的候选工具/知识库引用，自动补入 `toolWhitelist` / `kbWhitelist`；自定义工具描述也可作为显式工具引用匹配。
- 已修复已有 Skill 编辑页“继续优化”套用结果会丢失当前 `id` 的问题；生成/优化结果现在保留当前技能身份、启用状态和治理字段，保存时更新原技能而不是误创建新技能；平台标准等不可编辑技能的自然语言正文优化入口已禁用并提示先派生。
- 本轮验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillAuthoringIntegrationTest test`；`frontend npm run build` 通过，保留既有 bundle size warning。
- 已按用户截图将管理端 Skill 新建/编辑页页头“启用”开关移到按钮条最左侧，并去掉启用旁问号；本轮 `frontend npm run build` 通过，`/admin/skills/new` 返回 200。
- 已按用户截图去掉管理端 Skill 新建/编辑页“编译预览”框内重复的“编译预览”标题和问号，改为直接显示原问号说明文案；本轮 `frontend npm run build` 通过，`/admin/skills/new` 返回 200。
- 已按用户要求将管理端 Skill 新建/编辑页“启用”开关从基础信息字段区移到页面顶部按钮条最右侧，基础信息区不再显示启用字段；本轮 `frontend npm run build` 通过，`/admin/skills/new` 返回 200。
- 已按用户要求去掉管理端 Skill 新建/编辑页“基础信息”中启用开关外层字段框，改为标题在上、开关在下的无框字段节奏；本轮 `frontend npm run build` 通过，`/admin/skills/new` 返回 200。
- 已按用户要求将管理端 Skill 新建/编辑页“基础信息”中的启用控件从原生 checkbox 改为 `role="switch"` 的按钮式开关，沿用 `enabled` 字段保存契约；本轮 `frontend npm run build` 通过，`/admin/skills/new` 返回 200。
- 已按用户最新截图继续调整管理端 Skill 新建/编辑页“边界规则”：升级处理规则与输出约定各自独占上方一整行，知识库白名单和工具白名单并排放在下方；本轮 `frontend npm run build` 通过，前端 dev server 已重启在 `http://127.0.0.1:5173/`，`/admin/skills/new` 返回 200。
- 已按用户截图继续调整管理端 Skill 新建/编辑页“边界规则”：升级处理规则与输出约定收窄到左侧，右侧改为知识库白名单和工具白名单资源面板，并接入与智能体构建相同的添加选择器交互（含 MCP 工具分组）；本轮 `frontend npm run build` 通过，`/admin/skills/new` 本地 dev server 返回 200。
- 已将管理端 Skill 新建/编辑页“提示片段”输入框改用与“规格正文”相同的大文本框高度规则；本轮 `frontend npm run build` 通过。
- 已按用户截图将管理端 Skill 新建/编辑页的原“执行提示”页签拆成“提示片段”和“规格正文”两个独立 tab，并让每个 tab 独占单个大文本编辑区；本轮 `frontend npm run build` 通过。
- 已继续按截图反馈修正管理端 Skill 新建/编辑页：需求描述标题拆出为与摘要预览相同结构，左右内容框统一高度；问号提示图标缩到 10px；自然语言生成区下方横线已移除；页面由固定满屏局部滚动改为整体页面可滚动；本轮 `frontend npm run build` 通过。
- 已继续按截图反馈微调管理端 Skill 新建/编辑页：需求描述与摘要预览采用相同标题行高度和内容区行高，去掉下方基础信息编辑区与状态摘要之间的竖向分割线，问号提示图标从 18px 收到 14px；本轮 `frontend npm run build` 通过。
- 已按最新截图调整管理端 Skill 新建/编辑页：右侧基础信息等页签编辑区移动到自然语言生成下方并占满下方屏幕；自然语言生成内的需求描述与摘要预览改为横排双栏；本轮 `frontend npm run build` 通过。
- 已完成智能体当前日期上下文修复：新增 `RuntimeContextPromptService`，每次对话以 `Asia/Shanghai` 注入当前日期、中文日期、星期、时间和时区，并要求模型按该上下文解释“今天/明天/昨天/本周”等相对日期；`/ai/chat` 响应新增 `runtimeContext` 便于排查。
- 已按截图反馈修复管理端 Skill 编辑页左侧重叠：自然语言生成标题的问号 tooltip 改为向右展开，不再盖住标题；左侧区域允许局部滚动，需求描述输入区高度调整为 `clamp(190px, 23vh, 260px)`，生成/优化/清空按钮不再与输入框或摘要预览重叠；本轮 `frontend npm run build` 通过。
- 已按截图反馈继续微调管理端 Skill 新建/编辑页：左侧标题改为“自然语言生成”，左侧宽度从最多 320px 调到最多 396px（约 +2cm），需求描述输入区固定为 `clamp(210px, 26vh, 300px)`，下方生成/优化/清空按钮不再被遮住；本轮 `frontend npm run build` 通过。
- 已继续完成管理端 Skill 新建/编辑页页签化调整：顶部按钮统一为导入/重置/预览编译/创建或保存草稿，右侧字段区改为基础信息、执行提示、边界规则、编译预览页签；长文本编辑集中在执行提示页签的大文本区，`转人工规则` 已改名为 `升级处理规则` 并在问号说明中解释适用场景；本轮 `frontend npm run build` 通过。
- 已完成管理端 Skill 新建/编辑页视觉优化：页头改为面包屑、状态 chips 与右侧操作区；Authoring 区改为“需求描述 + 摘要预览”的工作台；管理端 shell 和该页样式已切到暖象牙、墨色、香槟金线条基线；说明性文字已收进标题旁小问号 tooltip；Skill fields 已去掉外层和内部分组背景、分组标题与总标题；页头横向背景块已移除；字段标题 tooltip 靠左裁切问题已通过右展开定位修复。
- 已完成工作台主模型名展示：后端 `/ai/chat/stream` 的 `phase` SSE 会携带当前 `modelName`；前端把模型名绑定到当前助手消息，并在工作台消息 meta 中用小标签展示，仅显示模型名。
- 已完成智能体模型身份误报修复：数据库确认 `Anthropic` provider 处于 disabled 且无 key，当前 `demo-org` 聊天路由为 `aliyun-bailian / deepseek-v4-pro`，`cici-system` 智能体 model 也是 `deepseek-v4-pro`；新增运行模型上下文 prompt，要求模型按服务端真实 provider/model 回答模型身份问题。
- 已修复会话工作台流式消息消失问题：工作台提交后本地助手占位/部分 delta 不再被“仅包含用户 turn 的服务端旧历史”覆盖；如果占位已被覆盖，后续 delta 会自动补回助手消息继续追加；状态机 thought 变化不再触发工作台历史重拉。
- 已完成会话工作台输入体验修复：工作台多行输入框支持 `Enter` 发送、`Shift+Enter` 换行；语音识别结束后不再自动发送，只把识别内容回填输入框；工作台 ASR 5 秒无语音会自动停止并保留已识别内容；已追加修复语音结束后焦点仍在麦克风按钮导致回车重新开启语音、清空转写内容的问题。
- 已完成会话工作台对话过程微交互收口：`ChatMarkdown` 在 busy 且内容为空时渲染无可见文字的三点动态状态，普通空响应仍保留“本次未返回文字内容。”兜底；`frontend npm run build` 通过，前端 dev server 已启动在 `http://127.0.0.1:5173/`。
- 已完成 `TASK-028` 第一轮代码实现：新增 `V34__agent_definition_avatar_base64.sql`、智能体头像读写字段、`PUT /auth/me/avatar`、前台个人头像设置入口、Agent Builder 智能体头像设置和全局头像展示替换。
- 本轮编译验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` 与 `frontend npm run build` 均成功；已追加修复前台左上角 rail 头像被全局 button padding 挤压成窄条的问题，并新增“上传后缩放裁剪再保存头像”能力，复跑 `frontend npm run build` 成功。
- 已修复头像裁剪预览与最终保存不一致问题：裁剪预览改为与导出同一坐标模型，解决“应用裁剪后取景偏移”的误差。
- 已完成头像裁剪第二轮几何对齐：预览层去除 transform 矩阵依赖，改为显式 `left/top/width/height` 布局并与导出公式共用同一 display scale，进一步收敛取景误差。
- 已完成头像裁剪第三轮范围对齐：裁剪画布改为完整圆形头像预览，移除“方形区域里较小圆孔”的误导显示，让可见范围与最终保存头像一致。
- 本仓库已按 `cc-aidev-guidelines-common` `3.4.0` 补齐项目级声明：`.claw/` 继续作为 canonical state directory，`README.md` 与 `AGENTS.md` 已加入受管声明块。
- 已新增项目级页面设计治理：`impeccable` 现在是所有页面分析、设计、改版和 UI 实现的强制技能，设计事实源固定为根目录 `PRODUCT.md`、`DESIGN.md`、`DESIGN.json`。
- 根 `PRODUCT.md` 已从单独平台页上下文升级为全项目认证产品面的战略上下文；`DESIGN.md` / `DESIGN.json` 已升级为 assistant、admin、platform 共用的产品面设计基线。
- 已新增 `docs/specs/FEAT-012-project-design-governance.md` 与 `DEC-023`，用于沉淀本轮设计治理规则与后续例外处理方式。
- FEAT-011 已继续收口为暖白、香槟金、墨色的简约金线风格：`/platform/login`、概览、平台技能、内置工具、平台审计在保留紧凑控制台结构的前提下，增加了金线边框与更强的质感表达。
- 前台会话工作台已完成一轮侧栏层级调整：左侧顶部状态机移除，右侧改为“顶部精简状态机 + 下方概览衔接会话历史”的结构。
- 前台会话工作台头像尺度已整体下调一档：顶部智能体切换头像、右侧状态机头像和消息区头像都已缩小，且 `frontend npm run build` 通过。
- 前台会话工作台左侧 rail 已完成一轮重排和配色统一：头像移到上方、logo 移到底部、tooltip 去重，且整体视觉已和主页面对齐。
- 当前最高优先级阻塞是 `TASK-023`：CloudCC 真实工具 smoke 仍卡在“用户绑定凭证失效 + 聊天入口缺少 Aliyun API key”两处。
- `TASK-020`（FEAT-008）按用户要求继续暂停；`TASK-007`（SaaS 计费）仍停留在设计态。

## Active Task Index

- `TASK-037`：External agent skill package optimization loop（completed，P1）
- `TASK-036`：Skill declarative API runtime（in_progress，P0）
- `TASK-035`：Admin skill versioning import export（completed）
- `TASK-034`：Admin skill authoring resource whitelist and edit refinement（completed）
- `TASK-028`：Global avatar settings for agents and current user（completed）
- `TASK-033`：Admin skill editor visual refresh（completed）
- `TASK-032`：Assistant workbench model label display（completed）
- `TASK-031`：Assistant model identity hallucination fix（completed）
- `TASK-030`：Assistant workbench streaming message preservation（completed）
- `TASK-029`：Assistant workbench enter send and ASR finish behavior（completed）
- `TASK-027`：Project-wide impeccable design governance（completed）
- `TASK-026`：Assistant workbench rail cleanup and reorder（completed）
- `TASK-025`：Assistant workbench sidebar state layout refinement（completed）
- `TASK-024`：Platform console visual refresh（completed）
- `TASK-023`：CloudCC runtime smoke unblock
- `TASK-020`：Knowledge base lifecycle completion（paused）
- `TASK-007`：SaaS billing and packaging design（pending）

## Verified Facts

- `frontend/src/admin/pages/AdminSkillComposePage.tsx` 新增 `authoringParsing` 状态，`generateSkillDraft()` 发起请求时显示需求解析动态进程，请求完成后关闭并显示 `authoringResult`。
- `frontend/src/admin/pages/AdminSkillComposePage.tsx` 的 `refineSkillDraft()` 也会显示需求解析动态进程，并在清空旧解析前缓存 `currentSkillSpec` 与 `clarificationAnswers`。
- `frontend/src/styles.css` 新增 `.skills-authoring-readonly-loading*` 样式和 `skills-parse-dot` / `skills-parse-step` 动画，并兼容 `prefers-reduced-motion`。
- `frontend/src/admin/pages/AdminSkillComposePage.tsx` 的页头自定义技能保存按钮统一显示“保存草稿”，编译按钮显示“编译预览”，新建页常见顺序为保存草稿、编译预览、发布。
- `frontend/src/admin/pages/AdminSkillComposePage.tsx` 的 `EDITOR_TABS` 顺序现在是基础信息、提示片段、规格正文、边界规则、编译预览、版本管理。
- `frontend/src/admin/pages/AdminSkillComposePage.tsx` 的发布说明弹框和白名单选择弹框均使用 `skills-modal-head` 与 `skills-modal-close`。
- `frontend/src/styles.css` 已移除发布弹框和白名单弹框各自的有边框关闭按钮样式，改为统一无边框图标关闭控件。
- `frontend/src/admin/pages/AdminSkillComposePage.tsx` 新增 `publishDialogOpen` 与 `publishChangeLog` 状态，点击“发布”打开 `skills-publish-modal`，确认后将 textarea 内容作为 `changeLog` 发布。
- `frontend/src/styles.css` 新增 `.skills-publish-modal*` 样式，复用 `dify-modal-overlay`，让发布说明弹窗居中并保持管理端 `鎏金账房` 风格。
- `frontend/src/admin/pages/AdminSkillComposePage.tsx` 的基础信息页签只保留技能代码、显示名称、风险等级和摘要说明；“变更日志”表单项已移除。
- `frontend/src/admin/pages/AdminSkillComposePage.tsx` 的 `publishSkill()` 现在在发起发布前通过输入框收集版本发布说明；用户取消或空输入时不会调用发布接口。
- `frontend/src/admin/pages/AdminSkillComposePage.tsx` 的草稿保存请求现在固定提交空 `changeLog`，发布说明只由发布动作收集。
- `frontend/src/admin/pages/AdminSkillComposePage.tsx` 已不再包含 `importZip`、`importPreview`、`SkillImport*`、`creatingImportedSkill` 等新建/编辑页导入相关代码。
- `frontend/src/styles.css` 已清理新建/编辑页专用 `.skills-import-preview*` 与 `.skills-compose__file-btn` 样式。
- `frontend/src/admin/pages/AdminSkillComposePage.tsx` 的 `publishSkill()` 现在支持无 `form.id` 的新建场景：先调用保存逻辑创建草稿，成功后继续 `POST /skills/{id}/publish`，发布完成后跳转到对应编辑页。
- 管理端 Skill 新建/编辑页顶部按钮区中，自定义技能的“创建草稿/保存草稿”已降级为 secondary 样式，“发布”保持 primary 样式。
- `frontend/src/admin/pages/AdminSkillComposePage.tsx` 现在将 `versions` 纳入 `SkillEditorTab`，编辑区页签中固定显示“版本管理”；新建页、只读技能、自定义技能编辑页分别显示对应说明或最近三版恢复列表。
- `frontend/src/styles.css` 新增嵌入式版本管理面板样式，让版本摘要、版本行、恢复按钮在页签内按 `鎏金账房` 的边框与紧凑密度展示。
- `frontend/src/admin/pages/AdminSkillsListPage.tsx` 现在给技能列表表格加了专属 `colgroup`，摘要内容放入 `.skills-data-table__summary-text`，操作按钮放入内部 `.skills-data-table__actions`，避免直接改变 `<td>` 的 display。
- `frontend/src/admin/pages/AdminSkillsListPage.tsx` 的可见文案已改为中文；筛选范围去掉 `derived`；行级操作仅对 `TENANT_CUSTOM + EDITABLE` 显示编辑、导出、删除，不再在列表页显示标准技能查看或自定义技能发布。
- `frontend/src/admin/pages/AdminSkillsListPage.tsx` 的自定义技能行级操作现在使用 `openActionMenuId` 控制三点菜单，菜单项为编辑、导出、删除，点击外部或按 Escape 会关闭菜单。
- `frontend/src/styles.css` 现在仅对 `.skills-data-table--catalog` 使用 `table-layout: fixed` 与 `min-width: 0`，`.skills-table-wrap--catalog` 关闭横向滚动；全局 `.skills-data-table__summary` 不再把使用者改成非 table-cell，`td.skills-data-table__actions` 也显式恢复为 table-cell。
- `ChatOrchestratorService.buildInitialMessages(...)` 现在会把运行时日期上下文放在 system prompt 顶部，覆盖非流式和流式聊天路径；`OrchestratorIntegrationTest` 已断言 `/ai/chat` 返回 `runtimeContext.currentDate` 与 `runtimeContext.timezone=Asia/Shanghai`。
- `ChatOrchestratorService.chatStream(...)` 现在会在 SSE `phase` 事件中携带 `modelName`；`frontend/src/assistant/AssistantApp.tsx` 会把它标记到当前助手消息，`chatMessageState.ts` 会在远端历史替换时保留该模型标签。
- `ChatOrchestratorService.buildInitialMessages(...)` 现在会额外注入运行模型上下文：当前服务端模型供应商与模型名称；`buildModelIdentityPromptBlock(...)` 已通过单元测试覆盖 `aliyun-bailian / deepseek-v4-pro` 不应被回答为 Claude 的规则。
- `frontend/src/assistant/chatMessageState.ts` 已抽出流式消息保护逻辑：本地最后一条为助手占位/部分流式内容、远端历史还没有有效助手内容时保留本地；delta 到达时若最后一条不是助手，会补助手气泡继续追加；`chatMessageState.test.ts` 覆盖该竞态。
- `frontend/src/assistant/AssistantApp.tsx` 已为工作台 textarea 增加 `Enter` 提交处理，带组合输入或 `Shift/Alt/Ctrl/Meta+Enter` 时不触发发送；语音结束后会把焦点送回当前 composer 输入框，重新开始语音会用现有输入作为前缀而不是先清空；`frontend/src/shared/useAsrVoiceInput.ts` 新增可选静默自动停止配置，当前仅工作台使用 5000ms。
- `frontend/src/components/ChatMarkdown.tsx` 的 busy 空内容分支不再输出可见文字，改为 `role="status"` 的三个点状态；`frontend/src/styles.css` 已新增错峰缩放动画与 `prefers-reduced-motion` 兼容。
- `docs/specs/FEAT-013-global-avatar-settings.md` 已记录全局头像设置设计，确认智能体头像只能由管理员设置、当前用户头像只能由本人设置、第一版采用上传图片 + 前端裁剪压缩 + 字母兜底，并新增展示覆盖矩阵。
- 现状检查确认 `app_user.avatar_base64` 已存在，管理端用户页已有 256x256 WebP 压缩逻辑；`agent_definition` 当前尚无头像字段，需新增。
- 当前实现已补齐 `agent_definition.avatar_base64` 与 `AgentDefinition` API payload 的 `avatarBase64`；旧前端不传该字段时不会误清空头像（`null` 视为不替换，空串视为清除）。
- 当前实现已补齐 `/auth/me/avatar` 自助更新接口，且仅允许当前登录用户更新本人头像。
- 前台会话与工作台主要头像位已接入统一 `AvatarView`：智能体优先 `agent.avatarBase64`，当前用户优先 `me.avatarBase64`，外部参与人优先 `thread.avatarUrl`。
- 前台左上角 rail 个人头像入口已改为 `AvatarView` 渲染，并重置头像 button 的 padding/box model，避免继承全局 `button` padding 后把图片内容区压缩成窄条。
- 当前用户头像和智能体头像上传入口均已支持“选图后缩放 + 拖动裁剪 + 应用后再保存”，裁剪结果统一输出 256x256 WebP data URL。
- `AGENTS.md`、`README.md` 已加入 `impeccable` 项目级设计治理规则，后续页面工作默认必须先加载根 `PRODUCT.md` / `DESIGN.md` 上下文。
- 根 `PRODUCT.md` 已明确 `/`、`/admin/*`、`/platform/*` 默认全部按 `product` register 处理，不再把项目级上下文限定为单一路由。
- 根 `DESIGN.md` / `DESIGN.json` 已明确 `鎏金账房` 是 assistant、admin、platform 共用的默认产品面设计基线，并记录了 route-level tuning 与例外机制。
- 根 `DESIGN.md` / `DESIGN.json` 已新增 Admin CRUD Lists 规范：真实表格元素不得改 display；搜索、筛选、空态、焦点和 hover 菜单不得撑开页面；默认无横向滚动；筛选为金色文本 tab；工具栏按钮统一；行级编辑/导出/删除等次级动作走不透明三点菜单。
- 已新增 `docs/specs/FEAT-012-project-design-governance.md` 与 `.claw/decisions.md` `DEC-023`，作为本轮设计治理落地的可追溯文档。
- `POST /mcp-servers/1/health` 返回 `status=connected`、`toolCount=43`；`GET /mcp-servers/1/tools` 返回 `cacheStatus=ready`，说明 CloudCC MCP server 与缓存快照可用。
- CloudCC 组织网关解析成功：`orgapi_address=https://szyd.apis.cloudcc.cn/lightningapi`。
- 真实绑定用户 `13800000001/哪吒` 当前换取 CloudCC token 仍返回 `Please check your username and password.`，阻塞点已收敛到用户绑定凭证而非组织级配置。
- `POST /ai/chat` 使用 `sales-agent` 发起 CloudCC 查询时返回 `Aliyun API key is not configured.`；同次响应里 `effectiveToolNames` 已包含 CloudCC 相关工具，说明工具暴露面正常，失败发生在模型调用前。
- 已新增 `PRODUCT.md`、`DESIGN.md`、`DESIGN.json` 与 `docs/specs/FEAT-011-platform-console-visual-refresh.md`，作为平台控制面视觉重构的设计与交付事实源。
- 平台控制面 `/platform/login`、`/platform`、`/platform/skills`、`/platform/tools`、`/platform/audit` 已完成简约金线主题微调，`DESIGN.md` / `DESIGN.json` 已同步到暖白 + 香槟金方向，且 `frontend npm run build` 通过。
- 前台会话工作台 `frontend/src/assistant/AssistantApp.tsx` / `frontend/src/assistant/cici-ui.css` 已完成右侧精简状态机和概览下移衔接历史的布局调整，且 `frontend npm run build` 通过。
- 前台会话工作台左侧 rail 已完成 tooltip 去重、头像/logo 重排和暖白金线风格统一，且 `frontend npm run build` 通过。

## Open Blockers

- `ISSUE-2026-04-08-cloudcc-token-invalid-credential`
- `ISSUE-2026-04-30-chat-smoke-blocked-by-aliyun-api-key`

## Read Next

- `docs/specs/FEAT-015-skill-declarative-api-runtime.md`：看 Skill 内嵌声明式 API 运行时、发布期编译、运行期注入、执行计划、安全策略和 P0 验收标准。
- `docs/specs/FEAT-014-skill-versioning-import-export.md`：看管理端技能版本控制、导入导出、通用 zip 包格式、大模型映射流程和页面原型。
- `docs/specs/FEAT-013-global-avatar-settings.md`：看全局头像设置的设计、权限边界、API 与验收标准。
- `AGENTS.md`、`PRODUCT.md`、`DESIGN.md`：看新的项目级页面设计治理入口与设计事实源。
- `docs/specs/FEAT-012-project-design-governance.md`：看本轮规范的范围、例外机制和后续执行方式。
- `.claw/task-board.md`：看 active task、owner_role 和 handoff。
- `.claw/decisions.md`：看 `DEC-023` 设计治理决策与已有架构决策。
- `.claw/issue-list.md`：如回到项目主线，再看 CloudCC 凭证与聊天入口配置阻塞。
- `docs/specs/FEAT-011-platform-console-visual-refresh.md`：看本轮平台控制面视觉重构范围与验收标准。
- `docs/specs/PROJECT-BASELINE.md`：看 brownfield 基线、关键入口和活跃交付面。

## Maintenance Notes

- 本文件只保留快照，不再回填长历史日志。
- 详细任务推进写入 `.claw/task-board.md`。
- 详细验证命令与结果写入 `.claw/test-report.md`。
- 详细问题根因与状态写入 `.claw/issue-list.md`。
