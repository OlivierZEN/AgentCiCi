---
kind: feature-spec
feature_id: FEAT-114
title: CRM 产品销售经营分析稳定性与深度治理
status: approved
owner_role: project-manager
task_ids: TASK-208,TASK-211
related_decisions: FEAT-015,FEAT-021,FEAT-028,FEAT-031,FEAT-109,FEAT-111
related_issues: ISSUE-2026-07-15-crm-deterministic-stream-single-delta
updated_at: 2026-07-15T01:35:39Z
updated_by: MANAGER-001
---

# FEAT-114 - CRM 产品销售经营分析稳定性与深度治理

## 背景与已验证根因

生产截图中的“销量最好的产品有哪些”请求确实命中了 `crm_product_sales_rank`，但以 Owen/SalesA 身份执行时返回 `scannedOrders=0`、`scannedItems=1888`。这不是工具未调用，也不是批次销量值缺失，而是两类缺陷叠加：

1. TASK-205 的 12 个产品、24 个商机、72 个商机产品、16 份合同、48 张订单和 144 条订单明细均由 SalesB 创建并拥有；五次成功验收也使用 SalesB 管理员身份。实际页面用户 SalesA 可以看到订单明细，却看不到用于日期、状态和客户过滤的订单主表，所以 1,888 条明细全部被排除。
2. CRM 强制路由把内部 `tool_result` 作为 SSE 事件下发；模型回答末尾的可选下钻语句“继续查看”又被通用延迟回复正则误判，通用 fallback 不认识 CRM 的 `status/rows/coverage/warnings` 结构，最终把原始 JSON 追加为用户可见文本，并触发错误的“等待确认”状态。

用户于 2026-07-14 明确批准方案 A：保留平台标准 `CRM 经营分析` Skill 与高阶只读工具，以专用确定性分析服务和答案格式化器作为事实与表达主线；修复受控演示批次的所有权和客户关系，不新增宽泛共享规则，也不新增独立通用智能体。

## CloudCC 平台依据与能力扫描

本规格引用技能内置文档：

- `platform/overview introduction`
- `platform/capabilityMap introduction`
- `platform/standardCapabilities introduction`
- `platform/security introduction`
- `platform/security devguide`
- `methodology/moduleDesign devguide`
- `playbooks/manufacturingCrm introduction`

2026-07-14 只读 `standard-catalog` 扫描目标租户得到 192 个对象、4,854 个字段。`Account`、`Opportunity`、`opportunitypdt`、`product`、`cloudccorder`、`cloudccorderitem`、`contract` 均为 `TABLE_TYPE=2` 的标准业务对象，当前需求无需新建对象或字段。

能力路径：

```text
Product -> OrderItem -> Order -> Account       当前销售事实与客户结构
Product -> OpportunityProduct -> Opportunity   未来产品管道
Order -> Contract                              合同与续约信号
```

本任务只修改 AgentCiCi 高代码服务和受控 CloudCC 业务记录，不修改 CloudCC 低代码元数据，因此本期 MetadataService domain、`planId`、`operationId` 均为 `N/A`。如果未来确需多人共享，再另建任务使用 `sharing-rules` domain，严格执行 `plan -> apply -> changes -> rollback-plan/rollback`。

## 目标与非目标

### 目标

- SalesA 在最近 30 天真实查询中稳定得到产品销量 Top 5，而不是误报空数据。
- 流式、阻塞式和 Agent OpenAPI 统一使用同一确定性业务答案，不依赖模型二次计算。
- 用户可见内容不得包含原始工具 JSON、工具内部标识、调用参数、fallback 技术文案或敏感凭据。
- 经营分析从简单排行升级为“销售事实、结构诊断、前瞻信号、可执行动作、口径覆盖”五层输出。
- TASK-205 演示批次与 TASK-203 的 16 个 SalesA V2 客户形成完整、可下钻、可回读的高仿真关系。
- 以 SalesA、SalesB 和未授权普通销售身份完成权限验收，并连续执行至少 5 个新会话验证稳定性。

### 非目标

- 不把订单销售额称为会计收入；没有履约和成本事实时不推断收入确认、毛利或利润。
- 不新增独立销售分析智能体，不允许通用模型自由编排多个原子 CRM/MCP 工具完成本问题。
- 不修改前端页面、主题、移动端实现或 TASK-207 文件。
- 不修改 TASK-203 独占的 `scripts/seed-demo-environment.py`。
- 不新增 CloudCC 对象、字段、简档、权限集、角色或共享规则。
- 不把 Owen 改绑为 SalesB，也不以管理员服务账号绕过当前用户权限。

## 全局对象地图与关系矩阵

| 业务语义 | 标准对象 | 核心关系 | 本期用途 |
|---|---|---|---|
| 产品 | `product` | `cloudccorderitem.product2id`、`opportunitypdt.product2` | 产品名称、编码、系列、单位和价格 |
| 订单明细 | `cloudccorderitem` | `orderid`、`product2id` | 净销量、订单销售额、实现均价 |
| 订单 | `cloudccorder` | `accountid`、`contractid`、`opportunityid` | 日期、状态、客户、合同和商机关联 |
| 客户 | `Account` | `cloudccorder.accountid`、`Opportunity.khmc`、`contract.khmc` | 行业、分级、购买覆盖和集中度 |
| 商机产品 | `opportunitypdt` | `opportunity`、`product2` | 产品管道数量和金额 |
| 商机 | `Opportunity` | `khmc`、`jieduan`、`jsrq` | 开放阶段、预计签约日期和未来需求 |
| 合同 | `contract` | `khmc`、`opportunityid`、订单关联 | 有效合同、临期合同和续约缺口 |

## 全局对象字段字典

| 对象 | 字段 | 类型/语义 | 规则 |
|---|---|---|---|
| `product` | `id,name,cpdm,cpxl,unit,productprice,ownerid` | 产品主数据 | `id` 与编码共同用于稳定识别 |
| `cloudccorderitem` | `id,orderid,product2id,quantity,unitprice,totalprice,status,unit,ownerid` | 销售明细 | 退货若以负数量存在则扣减；无总价时才用数量×单价 |
| `cloudccorder` | `id,accountid,contractid,opportunityid,podate,status,totalamount,paymentstatus,currency,ownerid` | 销售主单 | 先按日期和有效状态过滤，再关联明细 |
| `Account` | `id,name,hangye,fenji,currency,ownerid,beizhu` | 客户画像 | 仅用于当前用户可见的客户结构与下钻 |
| `opportunitypdt` | `opportunity,product2,quantity,totalprice,unit,currency,ownerid` | 产品管道明细 | 独立聚合后再关联产品，禁止与订单产生笛卡尔重复 |
| `Opportunity` | `id,khmc,jieduan,jine,yqsr,jsrq,knx,currency,ownerid` | 商机主表 | 只统计开放阶段；赢单/丢单不作为未来管道 |
| `contract` | `id,khmc,opportunityid,htje,htksrq,htjsrq,zhuangtai,currency,ownerid` | 合同信号 | 活跃、临期和到期状态分开，不凭空计算产品履约率 |

本任务不新增选项值。订单、商机和合同状态继续读取租户现有普通选项；未知值不得默认计入，应形成数据质量告警。

## 权限矩阵

| 维度 | 本期设计 |
|---|---|
| role | SalesA 与 SalesB 现有角色保持不变；不通过角色层级扩大数据访问 |
| profile | SalesA 继续使用销售简档，SalesB 保持系统管理员简档；不修改对象或字段权限 |
| permission / permission set | 不新增补充授权；只有未来确认缺少标准对象读取权限时另建受控任务 |
| sharingRule | 本期不新增；仅迁移 TASK-205 批次所有权并重连 SalesA 可见客户，避免把 SalesB 历史记录整体共享 |

所有 CRM 读取必须使用当前 AgentCiCi 成员映射的 CloudCC 身份。SalesB 管理员只用于回归验收，不能作为普通对话运行身份。

## 指标与诊断口径

### 核心事实指标

| 指标 | 计算规则 |
|---|---|
| 净销售数量 | 有效订单明细 `quantity` 合计；负数退货扣减 |
| 订单销售额 | 有效订单明细 `totalprice` 合计；不称会计收入 |
| 数量/金额贡献率 | 产品值 ÷ 全部有效销售事实值；分母不得只使用 Top N |
| 等长前期增长 | 当前期与紧邻等长前期比较；前期为 0 时显示“无可比基期” |
| 实现均价 | 正向订单销售额 ÷ 正向销量；避免退货扭曲 |
| 订单/客户覆盖 | 去重 `orderId`、`accountId` |
| 客户集中度 | 最大客户与前三客户订单销售额占该产品销售额的比例 |
| 产品管道 | 开放商机产品的数量、金额、商机数和最近预计签约日期 |
| 合同信号 | 活跃合同数、90 天内到期合同数以及是否存在对应续约商机 |

不同计量单位的产品不得仅凭数量进行经济价值比较；答案必须显示单位并同时给出金额指标。存在多币种且无法可靠折算时，不合并金额排行，输出数据质量提示。没有成本数据时不得声称毛利下降。

### 确定性经营诊断规则

- 数量、金额、客户覆盖均领先且增长：标记“核心增长产品”，建议保障交付和推进续约。
- 数量增长但金额增长弱、实现均价下降：标记“可能存在折扣驱动”，建议核查价格和折扣。
- 金额排名显著高于数量排名：标记“高价值型产品”，建议复制高价值客户场景。
- 销售增长但客户数不增长或 Top1 集中度上升：标记“客户集中度上升”。
- 当前销售强而开放商机产品弱：标记“后续订单断层信号”。
- 当前销售弱而开放商机产品强：标记“潜在增长产品”。
- 活跃产品关联合同 90 天内到期且无续约商机：标记“续约风险”。
- 退货净额或退货量明显上升：标记“质量/交付/适配复核”，不得直接断言质量事故。

每条诊断必须携带事实证据和建议动作；不满足规则时不生成模板化结论。

## 结果状态机

| 状态 | 判定 | 用户回答 |
|---|---|---|
| `SUCCESS` | 核心事实完整且存在有效销售 | 输出五层经营分析 |
| `PARTIAL` | 排行可计算，但可选商机/合同/客户增强存在缺失 | 输出排行并明确缺失范围 |
| `EMPTY` | 核心对象可见且统计期确无有效销售 | 明确时间与有效状态口径，建议扩大期间 |
| `DATA_ACCESS_INCOMPLETE` | 有订单明细但订单主表为 0，或核心引用对象不可见 | 明确权限范围不完整，不得声称“没有销售” |
| `DATA_QUALITY_BLOCKED` | 多币种不可合并、核心引用大量缺失或单位不可比较导致主指标失真 | 说明阻断事实与修复动作，不输出伪排行 |
| `CRM_NOT_CONNECTED` | 当前成员无有效 CRM 绑定 | 引导检查绑定 |
| `PERMISSION_DENIED` | 核心对象/字段读取被明确拒绝 | 指出受影响对象，不扩大权限 |
| `SCHEMA_UNSUPPORTED` | 标准对象字段结构不兼容 | 指出缺失能力，禁止模型猜字段 |
| `UPSTREAM_ERROR` | CloudCC 查询暂时失败 | 给出可重试说明，不泄漏异常载荷 |

## 确定性回答合同

同一结构化结果在流式聊天、阻塞式聊天和 Agent OpenAPI 中必须产生相同业务正文：

1. **直接结论**：统计期、销量冠军、订单销售额冠军和一句总体判断。
2. **Top 5 表格**：排名、产品、销量/单位、订单销售额、贡献率、增长、订单数、客户数。
3. **经营诊断**：量价、客户覆盖与集中度，必须带事实依据。
4. **前瞻信号**：开放商机产品、预计签约日期、活跃/临期合同和续约缺口。
5. **建议动作**：具体到产品、客户、商机或合同；不生成无对象的空泛建议。
6. **口径与覆盖**：有效订单状态、退货、币种、数据截止时间、扫描/计入数量和权限告警。

模型可以处理非确定性普通对话，但不得在本路由上重算事实。CRM 专用格式化器输出后直接完成本轮，不再调用最终 LLM。

## 工具结果与协议安全

- CRM 强制路由不得向用户 SSE 发送原始 `tool_result`；完整结构仅保留在服务端内部消息和受控 trace。
- Agent OpenAPI 的 `agent_thought.observation` 不得包含 CRM 原始结果、产品内部 ID、用户 ID、owner ID、调用参数或凭据。
- 可选下钻语句如“如需我可以继续查看客户明细”不属于延迟回复，不得触发 fallback。
- 通用 fallback 最后一道防线：任何未知 JSON 都不得原样输出；最多生成不含字段载荷的安全摘要。
- 用户可见答案不得出现 `{"status"`、`tool_result`、`productId`、`ownerId`、“本轮不会在完成状态后自动追加回复”或“模型本轮未能生成最终自然语言总结”。

## 高仿真数据迁移设计

批次：`TASK-205-CRM-ANALYTICS-DEMO-V1`。

目标所有人：Owen/SalesA，CloudCC 用户 ID `00520264AE58B11bw6gE`。该 ID 属于租户元数据标识，不是凭据；执行前仍需只读回查用户名和有效状态。

目标客户：通过 `Account.beizhu` 中的 `TASK-203-DEMO-V2` 批次标记发现 16 个 SalesA 可见客户，不依赖 TASK-203 脚本导入。脚本必须要求数量恰好为 16、所有人均为 SalesA，并按稳定客户名称排序建立一一映射；条件不满足时失败关闭。

迁移范围：

- 12 个产品、24 个商机、72 个商机产品、16 份合同、48 张订单、144 条订单明细的 `ownerid` 更新为 SalesA。
- 商机、合同和订单按稳定轮转映射重连 16 个 V2 Account；订单明细继续关联原订单和产品。
- 所有更新仅命中批次 marker 或稳定批次产品编码，禁止修改其他租户记录。
- dry-run 输出对象级变更数、关系变化数和验收预期，不输出 token、cookie 或 secret。
- execute 前生成包含记录 ID、原所有人、原客户关联和目标值的回滚清单；失败时不得声称完成。
- execute 后以 SalesA 回读 12/24/72/16/48/144 数量、所有权和全部引用完整性；重复 execute 必须零新增、零重复。

最近 30 天 `SALES_QUANTITY` 基准 Top 5：

1. 智能巡检终端 X1：130
2. 边缘采集网关 G5：110
3. 安全监测传感器 S2：95
4. 制造运营分析平台 MP：75
5. 预测性维护应用 PA：65

金额排行必须至少有一处与数量排行不同，确保分析能够解释“量”和“值”的差异。

## 实施任务

### Task 1：治理、身份门禁与隔离基线

- 建立 TASK-208 / FEAT-114 / assignment，递归授权必要文件并单独推送 `origin/main`。
- 从更新后的 `origin/main` 创建 `codex/TASK-208-crm-analysis-hardening` 独立 worktree。
- 运行 manager SSH challenge、assignment preflight 和相关测试基线。

### Task 2：核心事实、深度指标与确定性格式化（TDD）

- 先为 `DATA_ACCESS_INCOMPLETE`、贡献率、均价、集中度、商机/合同信号和安全格式化编写失败测试并确认预期失败。
- 扩展 CRM 结构化结果、可选对象查询和确定性诊断规则。
- 新增专用答案格式化器，覆盖成功、部分、真实空数据、权限不完整和上游错误。

### Task 3：对话编排与协议防泄漏（TDD）

- 先写流式/阻塞式一致、无原始 `tool_result`、可选下钻不触发 fallback、Agent OpenAPI 不泄漏的失败测试。
- CRM 路由执行后直接使用专用格式化器完成本轮；删除 CRM 原始 SSE 特例。
- 通用未知 JSON fallback 改为安全摘要，作为防御性兜底。

### Task 4：SalesA 数据迁移与脚本回归（TDD）

- 先为 V2 客户发现、owner/account 更新计划、批次边界、幂等和回滚清单编写失败测试。
- 修改 `scripts/seed-crm-analytics-demo.py`，不得修改 TASK-203 脚本。
- 先 dry-run，再生产备份/回滚清单，再 execute，最后进行 SalesA/SalesB 回读。

### Task 5：发布与真实验收

- 运行受影响单测、后端完整测试、状态验证和安全关键字扫描。
- 按 `docs/production-release-runbook.md` 先执行 `./scripts/release-acr.sh --dry-run`，统一版本号后再真实发布。
- 使用 SalesA 新建至少 5 个会话询问“销量最好的产品有哪些”，验证事实、表达、trace 和无泄漏。
- 使用 SalesB 管理员回归相同事实；若存在普通未授权销售 persona，验证其不能访问该批次。
- 更新 `.claw/test-report.md`、TASK-208 状态、FEAT-114 交付记录和主线热状态。

## TDD 与验收矩阵

### 单元/集成测试

- `orders=0 && items>0` 返回 `DATA_ACCESS_INCOMPLETE`，固定使用生产截图的 `0/1888` 回归夹具。
- 真正所有核心列表为空才返回 `EMPTY`。
- Top N 贡献率分母使用全量有效事实；金额、数量、订单和客户排行稳定。
- 不同单位、多币种、缺产品/客户引用和可选对象权限失败进入正确状态。
- 量价、集中度、管道断层、潜在增长和续约风险规则仅在事实满足时生成。
- 成功/部分/空数据/权限不完整/未绑定/无权限/结构不兼容/上游错误均有自然语言输出。
- 已有具体 CRM 结论加可选下钻语句时，不触发延迟 fallback。
- 流式与阻塞式正文一致；SSE 不包含 CRM 原始工具结果。
- Agent OpenAPI observation 不包含 CRM 原始 payload。
- 未知 JSON fallback 不原样输出。
- 数据脚本 dry-run、批次边界、SalesA owner、V2 Account 映射、幂等和回滚清单均有测试。

### CRM 数据验收

- SalesA 可见 16 个 V2 Account、12 个批次产品、48 张批次订单和 144 条批次明细。
- 24 个商机、72 个商机产品、16 份合同、48 张订单全部归属并关联 SalesA 可见客户。
- 所有明细都关联可见订单和产品；孤儿引用为 0。
- SalesA 与 SalesB 最近 30 天均返回基准 Top 5；未授权普通销售不因本任务扩大共享范围。

### 对话稳定性验收

- 至少 5 个全新 SalesA 会话均只命中高阶工具，排名和关键数值一致。
- 每次回答至少包含直接结论、Top 5、一个有事实依据的经营诊断、前瞻信号、建议动作和口径覆盖。
- 最终 SSE、持久化消息、前端正文和 OpenAPI 输出均不包含原始 JSON、内部工具名或 fallback 技术文案。
- 页面不再出现由 fallback 触发的错误“等待确认”。

## 完成定义

- TASK-208 定向自动化测试、交付状态静态检查和代表文件授权校验通过；全仓状态校验的既有历史治理债务不作为本任务完成阻塞，但必须在测试报告中明确披露。
- CloudCC 受控批次迁移、关系回读和多身份权限验收通过。
- 新版本按正式 runbook 发布，后端、前端和状态服务健康。
- 5 次真实 SalesA 问答通过，回答达到本规格的经营分析深度且无内部结果泄漏。
- TASK-208、FEAT-114、`.claw/test-report.md` 和主线热状态已更新并推送；完成审计无缺项。

## 交付记录（2026-07-15）

### 代码与发布

- 方案 A 已按设计落地：保留平台标准 `CRM 经营分析` Skill 和一个高阶只读工具，以确定性分析服务与专用答案格式化器统一流式、阻塞式和 OpenAPI 正文；未新增独立通用智能体。
- 最终集成 PR #4 合入 `origin/main`，生产发布 `2.7.5 / be80eea665c0`。该发布同时保留 TASK-209 登录视觉和 TASK-210 标准渠道图标，不回退并发生产能力。
- 发布前备份为 `/opt/cici/backups/20260715-005545-before-2.7.5-task208-crm-analysis`；env、PostgreSQL、知识库和 Qdrant 四类制品均非空。生产只替换 backend/frontend，四个状态服务容器 ID 未变化。
- 后端定向 143 项、前端 89 项和 TypeScript/Vite 生产构建通过；运行健康 `UP`，版本提交、Nginx、公网路由和最终干净日志窗口通过。

### CloudCC 数据与权限

- 写前 dry-run 精确命中 12 个产品、16 个 V2 客户、24 个商机、72 条商机产品、16 份合同、48 张订单和 144 条订单明细。
- 在生成 316 条受保护回滚记录后，执行 316 条 update-only：316 处 owner 切换为 SalesA、88 处商机/合同/订单 Account 重连，共 404 个字段变化；创建、删除、重复、Account 本体写入、角色、简档、元数据和分享规则变更均为 0。
- 写后二次 live dry-run 为待更新 0、owner 变化 0、Account 变化 0、字段变化 0、创建 0、重复 0；所有订单明细引用均可解析，4 张无效高销量订单继续被排除。
- 当前组织只有 SalesA/Owen 与 SalesB/CCAdmin 两个验收 persona；没有制造或冒用普通销售身份。SalesB 管理员对照保持全局可见性，SalesA 通过记录所有权和客户关联获得正规可见性。

### 经营分析与协议验收

- SalesA 连续 5 个全新 SSE 会话均得到数量 Top 5：智能巡检终端 X1 130、边缘采集网关 G5 110、安全监测传感器 S2 95、制造运营分析平台 MP 75、预测性维护应用 PA 65；金额冠军为制造运营分析平台 MP，销售额 2,850,000。
- 每次回答均包含直接结论、产品 Top 5、数量/金额贡献、环比、订单/客户覆盖、经营诊断、商机与合同前瞻信号、建议动作和口径覆盖。回答明确区分“销量冠军”和“销售额冠军”，避免把订单销售额误称为财务确认收入。
- 5 组持久化消息与 SSE 正文一致；内部 blocking、OpenAPI blocking/streaming 和 SalesB 对照均返回同一事实。OpenAPI observation 只保留脱敏运行状态，不含工具名、参数、记录 ID、owner ID 或原始 payload。
- 生产桌面页面真实新建会话并重新询问后，Top 5 表格和五层分析正常渲染，状态收敛为“已完成本轮处理”；DOM 中不存在 `crm_product_sales_rank`、`tool_call/tool_result`、原始 JSON 或错误“等待确认”，浏览器控制台 error 为 0。
- OpenAPI 验收使用的一次性 Key 已撤销，临时 api channel 已恢复为原渠道集合；生产未遗留额外外部访问凭据或入口。

## TASK-211 真实流式输出纠偏设计（2026-07-15）

### 已验证问题

- 生产 `2.7.5` 的 5 份真实 CRM SSE 证据均为 `phase × 3 → delta × 1 → done × 1`；唯一正文 `delta` 一次承载 2,383 个字符，因此页面表现为等待后整段出现。
- Agent OpenAPI streaming 同样为 `agent_thought × 3 → message × 1 → message_end × 1`，唯一 `message` 也承载完整 2,383 字正文。
- 根因位于 `ChatOrchestratorService` 的 CRM 确定性分支：格式化器生成完整安全正文后只调用一次 `safeSendDelta(emitter, finalText)`。普通模型路径会按上游 piece 多次发送，因此不受影响。
- 前端 `streamChat.ts` 已在每个 `delta` 后立即更新并主动让出宏任务；Nginx `/ai/` 与 `/openapi/` 均已关闭 buffering。问题不在前端、React 或代理缓存。
- 现有测试只校验所有 `delta` 拼接后的正文，单个全文 `delta` 也能通过，未覆盖“真实多分片”契约。

### 方案比较与用户决策

1. **方案 A（已批准）**：保留确定性格式化器，在服务端复用现有 `safeSendDeltaInChunks`，按最多 18 个 Java 字符、相邻分片约 18ms 的节奏发送正文。
2. 方案 B：前端收到完整正文后模拟打字。只能改善网页视觉，OpenAPI 仍是假流式，拒绝采用。
3. 方案 C：恢复最终 LLM 二次生成。会重新引入事实漂移、额外延迟和潜在原始工具结果泄漏，拒绝采用。

用户于 2026-07-15 明确批准方案 A。

### 设计与事件契约

```text
CRM 高阶只读工具
  → 确定性分析与完整安全正文
  → safeSendDeltaInChunks
  → /ai/chat/stream: phase... → delta × N → done
  → OpenAPI bridge: agent_thought... → message × N → message_end
```

- 只把 CRM 确定性正文从单个事件改为多个有序事件；不改变分析、排序、格式化、权限、持久化或计费事实。
- `/ai/chat/stream` 对非空长正文必须产生 `N > 1` 个非空 `delta`，每片不超过 18 个 Java 字符；全部片段拼接必须逐字等于阻塞式正文和持久化正文。
- `done` 只能在最后一个 `delta` 之后发送且仅发送一次。中断或客户端断开继续沿用现有 SSE 错误处理，不补发第二份正文。
- Agent OpenAPI bridge 一对一把内部 `delta` 映射为 `message`，因此 streaming 也必须产生多个 `message`；拼接正文与 blocking 完全一致。
- OpenAPI bridge 不得对单个 `delta` 做 `trim`、`strip` 或其他逐片规范化；分片首尾空格、换行以及纯空白片段都属于正文，必须原样发送并参与最终持久化。只允许忽略真正的空字符串。
- `/ai/chat` blocking 仍一次返回完整正文；数据库仍只保存一条完整 assistant 消息。
- 不恢复最终 LLM，不发送 `tool_call`、`tool_result`、工具名、原始 JSON、内部记录 ID、token、cookie 或凭据。
- 不修改前端生产代码、不修改 CloudCC 数据或元数据，也不改变 TASK-208 已验收的 Top 5 与五层经营分析内容。

### TDD 与验收标准

- 先扩展 CRM 编排回归，使当前单 `delta` 实现因 `deltaCount == 1` 明确失败，再做一处最小生产代码替换。
- CRM SSE 回归必须验证：`deltaCount > 1`、单片上限、分片非空、第一片不是完整正文、片段拼接等于 blocking/persistence、所有 `delta` 位于 `done` 前、无工具或原始数据泄漏、最终 LLM 零调用。
- Agent OpenAPI streaming 回归必须验证：`messageCount > 1`、顺序与拼接正文一致、`message_end` 位于所有正文片段之后、`agent_thought` 仍只含脱敏状态。
- 完整后端定向测试、相关前端现有流式测试与生产构建必须通过；无前端生产代码变更时不新增视觉语言或移动端验收范围。
- 按正式 runbook 发布新的不可变版本，不覆盖 `2.7.5`。发布后使用 SalesA 连续 5 个新会话记录事件数、首片与末片时间、正文拼接哈希和页面中间态；SalesB 做一次对照。
- 真实桌面页面必须能观察到同一助手气泡从首片逐步增长到完整五层分析，最终 Top 5、金额冠军、持久化正文与 TASK-208 基准一致，浏览器 console error 为 0。

### 风险与回滚

- 服务端节奏发送会略微延长单轮连接占用；沿用已有 18 字/18ms 参数，避免新增配置和调度器。若生产长回答耗时超出既有 SSE 600 秒上限或并发指标异常，回滚新版本即可。
- 若分片导致 Markdown 中间态短暂不完整，最终 Markdown 仍由同一完整正文收敛；不为此引入前端双缓冲或另一套打字状态机。
- 回滚只涉及 AgentCiCi 应用版本；本任务没有 CRM 写入、数据库迁移或 CloudCC 元数据变更。

### 本地实现与审查结果（2026-07-15）

- TDD 先在旧实现上得到 `deltaCount == 1` 的预期失败，再以一处生产调用替换复用现有分片 helper；未修改 helper 参数或其他业务路径。
- 内部 SSE 回归验证多片、18 字上限、精确拼接、唯一尾部 `done`、blocking/persistence 一致、防泄漏和最终 LLM 零调用；OpenAPI 回归验证多 `message`、精确持久化和唯一尾部 `message_end`。
- 独立干净数据库 CRM 定向 135 项、前端 89 项、生产构建、Compose、身份/assignment 与 diff 门禁通过；完整后端诊断只重现既有非 TASK-211 基线失败。
- 任务级规格/质量审查与整分支最终审查均批准合并，生产代码无需再调整；剩余项全部是 `2.7.6` 发布后的真实会话、页面中间态、日志和清理门禁。

### 2.7.6 生产验收发现与回滚（2026-07-15）

- `2.7.6 / 2055947aae07` 上线后，内部 SalesA 连续 5 个成功会话均产生 133 个 `delta`，单片最多 18 个 Java 字符，唯一 `done`，逐次正文与持久化完全一致；方案 A 的内部 SSE 行为成立。
- OpenAPI streaming 同样产生 133 个 `message`，但生产对比发现拼接正文为 2,342 字，而 blocking 为 2,383 字。丢失的 41 个字符全部是分片边界的空格或换行，Markdown 最终结构因此不能满足“跨协议正文完全一致”的验收标准。
- 根因是 `OpenApiStreamBridge.deltaText` 复用了会调用 `trim()` 的通用文本规范化函数，并且以 `isBlank()` 丢弃纯空白分片；单全文事件时期该问题不明显，多分片后被稳定放大。
- 验收失败后已撤销临时 OpenAPI Key、验证撤销后返回 401、精确恢复原 Agent channels/toolIds/knowledgeBaseIds，并只重建 backend/frontend 回滚到健康的 `2.7.5 / be80eea665c0`；四个状态服务容器未改变。
- 修复要求：`deltaText` 对 `text` 值只做 null-to-empty，不得 trim；转发条件从“非 blank”改为“非 empty”。回归必须包含尾随空格、纯空白片段和前导换行，并证明外部消息拼接、运行完成入参及持久化正文逐字相等。
- `2.7.6` 保留为失败验收的不可变发布证据，不复用、不覆盖；修复只能发布新的 `2.7.7`。

### 2.7.7 生产协议验收结果（2026-07-15）

- 空白保真提交 `eb5e1f7e4dc05f53943094e09289c54cd08d0056` 经 PR #7 合并为 `e47979167af8`；不可变 tag/image `2.7.7` 通过统一 release dry-run 后发布。部署仅重建 backend/frontend，四个状态服务容器身份不变，六服务健康，V80、版本指纹、Nginx 与公网入口通过。
- SalesA 5 个 fresh SSE 会话均为 3 个 phase、133 个非空 delta、最大 18 UTF-16 单元、唯一尾部 done；每次首末片约跨 2.4 秒，且 SSE 拼接与自身两条持久化消息逐字一致。五次答案、SalesA blocking 与 SalesB SSE 仅归一化动态“数据截止”后完全相同。
- OpenAPI blocking 与 streaming 均返回 2,383 字五层正文；streaming 为 3 个脱敏 thought、133 个 message、最大 18 UTF-16 单元和唯一尾部 message_end。逐片空格、换行及纯空白保留，streaming、blocking、各自 history 和内部协议正文在只归一化截止时间后完全一致。
- 临时 OpenAPI Key 已撤销且复用返回 401 `agent_api_key_invalid`；无新增 ACTIVE Key，channels/toolIds/knowledgeBaseIds 与 fresh 初始快照精确相同。9 份用户正文与脱敏 thought 未发现工具名、原始 JSON、内部 ID 或敏感信息。
- Top 5 仍为 X1 130、G5 110、S2 95、MP 75、PA 65，金额冠军仍为 MP 2,850,000；贡献、环比、订单/客户覆盖、经营诊断、商机、合同、行动、退货与收入声明均保留。最终成功会话日志窗口中 backend ERROR、CRM failure、异常断连和 Nginx 5xx 均为 0。
- 生产桌面视觉契约尚缺一次证据补录：当前应用内 Browser 不可用且实例列表为空，按 Browser 技能不得以 Playwright 冒充，因此没有同一气泡的 partial/final 截图与 console/overflow 记录。接口逐事件到达时间已证明服务端是真流式，但 TASK-211 保持 active，待 Browser 实例恢复后完成该唯一剩余验收项。
- 权限负向测试同时发现既有状态语义问题：SalesB 无法读取 SalesA 会话且响应无数据，但 `ResponseStatusException` 被通用异常处理映射为 HTTP 500，而非 404/403。数据隔离成立；该问题独立登记，不纳入本流式补丁。
