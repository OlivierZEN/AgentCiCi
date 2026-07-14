---
kind: feature-spec
feature_id: FEAT-111
title: CRM 经营分析 Skill 与高仿真产品销售演示数据
status: approved
owner_role: project-manager
task_ids: TASK-205
related_decisions: FEAT-015,FEAT-028,FEAT-031,FEAT-081,FEAT-084,FEAT-109
related_issues: none
updated_at: 2026-07-14T09:43:14Z
updated_by: MANAGER-001
---

# FEAT-111 - CRM 经营分析 Skill 与高仿真产品销售演示数据

## 背景与目标

用户询问“看一下最近哪个产品销售得比较好”或“销量最好的产品有哪些”时，现有模型可能临场组合 CloudCC 对象发现、字段发现和分页查询工具，并把对象匹配失败暴露给用户。产品、订单、订单产品、合同和业务机会产品之间的关系、销售事实口径、状态过滤和聚合规则不应由通用模型每轮重新猜测。

本功能建设一个平台标准 `CRM 经营分析` Skill 和一个确定性的高阶只读工具。通用模型只负责识别业务意图、时间范围、指标和筛选条件；高阶工具负责租户语义映射、CloudCC 当前用户权限查询、跨对象关联、过滤、聚合、校验和证据封装；最终回复只能基于结构化工具结果生成。同时在智能体演示平台已绑定的 CloudCC CRM 演示组织中建设丰富、高仿真的产品销售数据，确保真实询问能够稳定返回可解释、可回读的结果。

## 用户已批准的设计决策

- 当前阶段不新建独立销售分析智能体；复用 `cici-system` 通用助手。
- 使用 `CRM 经营分析` Skill 固化触发语义、指标口径、工具边界、输出合同和兜底规则。
- 不把多个底层 MCP/OpenAPI 工具直接暴露给模型自由编排；新增一个业务级只读工具封装完整查询。
- MCP/OpenAPI 对象发现与字段发现只用于租户映射建立、刷新和故障诊断，不进入普通用户高频问答热路径。
- CRM 演示数据必须写入已绑定的真实 CloudCC 演示组织，不用 AgentCiCi 本地 Mock 冒充 CRM 事实。

## CloudCC 平台依据

已通过 `cc-customization-expert-msapi` v2.1.279 读取并采用：

- `platform/overview introduction`
- `platform/capabilityMap introduction`
- `platform/standardCapabilities introduction`
- `methodology/integrationDesign devguide`

已执行目标租户只读 `standard-catalog` 扫描。扫描确认租户存在 192 个对象、4,854 个字段，且 `product`、`cloudccorder`、`cloudccorderitem`、`contract`、`Opportunity`、`opportunitypdt`、`Account` 均已存在，无需创建同义自定义对象。

## 能力路径

```text
standard-catalog
  -> object / fields / reference semantics
  -> product + cloudccorderitem + cloudccorder
  -> contract / Opportunity / opportunitypdt auxiliary facts
  -> current-user CloudCC OpenAPI
  -> deterministic CRM business analysis service
  -> crm_product_sales_rank tool
  -> CRM business analysis Skill output contract
  -> evaluation suite and real conversation acceptance
```

## 范围

### 本期范围

- 新增平台标准 `CRM 经营分析` 文件型 Skill，并使 `cici-system` 可用。
- 新增只读高阶工具 `crm_product_sales_rank`。
- 支持按销量、销售额、订单数和客户数对产品排名。
- 支持最近 7/30/90 天、本月、上月、本季度和明确起止日期。
- 默认返回 Top 5，同时给出销量、销售额、订单数、客户数、环比、数据时间和事实口径。
- 使用当前 AgentCiCi 用户映射的 CloudCC 身份查询，继承对象、字段和记录级权限。
- 在目标 CloudCC 演示组织建设产品、订单、订单产品、合同、业务机会产品和关联客户样例数据。
- 提供幂等种子脚本、dry-run、创建后回读验证和稳定的预期排名。
- 把该问题加入智能体评测，至少重复执行 5 次验证意图、工具、参数、结果和回答要点稳定。

### 非目标

- 不创建独立销售分析智能体或独立前端工作台。
- 不做自由 SQL、任意对象联查或通用 BI 查询生成器。
- 不修改 CloudCC 标准对象结构；如真实字段无法承载才另行提出 MetadataService 变更计划。
- 不自动写 CRM 业务数据；只有项目经理显式执行演示数据种子脚本时才创建带批次标记的样例记录。
- 不把未关闭业务机会金额称为实际销售额。
- 不新增移动端适配或移动端验收。

## 全局对象地图

| 业务域 | CloudCC 对象 | 关键职责 | 本期策略 |
|---|---|---|---|
| 产品主数据 | `product` | 产品名称、代码、系列、单位、标准价格、启用状态 | 复用并创建演示产品 |
| 实际销售单据 | `cloudccorder` | 客户、订单日期、状态、金额、回款、合同和商机关联 | 作为实际销售主事实 |
| 实际销售明细 | `cloudccorderitem` | 订单、产品、数量、单价、折扣、总价、激活状态 | 作为产品销量与销售额事实 |
| 合同 | `contract` | 客户、签约日期、状态、合同金额、订单关联 | 作为签约和收入兑现辅助证据 |
| 预测销售 | `Opportunity` | 客户、阶段、预计签约日期、金额 | 仅作为销售管道事实 |
| 预测销售明细 | `opportunitypdt` | 业务机会、产品、数量、价格、总价 | 仅作为预计产品需求事实 |
| 客户 | `Account` | 客户名称、行业、分级、所有人 | 作为客户数和下钻维度 |

## 对象关系矩阵

| 主对象 | 关系对象 | 已扫描字段 | 用途 |
|---|---|---|---|
| `cloudccorder` | `Account` | `accountid` | 统计购买客户数、客户贡献 |
| `cloudccorderitem` | `cloudccorder` | `orderid` | 将产品明细归属到订单日期和状态 |
| `cloudccorderitem` | `product` | `product2id` | 按产品聚合数量和金额 |
| `cloudccorder` | `contract` | `contractid` | 解释订单与合同关系 |
| `cloudccorder` | `Opportunity` | `opportunityid` | 从实际订单回溯商机 |
| `opportunitypdt` | `Opportunity` | `opportunity` | 预计产品数量和金额 |
| `opportunitypdt` | `product` | `product2` | 产品销售管道分析 |

## 全局对象字段字典

| 语义 | 对象与字段 | 类型 | 使用规则 |
|---|---|---|---|
| 产品 ID/名称/代码 | `product.id/name/cpdm` | 文本 | 稳定聚合键、展示名称和幂等业务键 |
| 产品系列/单位 | `product.cpxl/unit` | 普通选项 | 排名分组；数量结果必须展示单位 |
| 标准价格/启用 | `product.productprice/yqy` | 币种/布尔 | 演示定价参考；停用产品只保留历史 |
| 订单 ID/编号 | `cloudccorder.id/name` | 文本/自动编号 | 主单关联和审计 |
| 订单客户/日期 | `cloudccorder.accountid/podate` | 引用/日期 | 客户数与时间范围主口径 |
| 订单状态 | `cloudccorder.status` | 普通选项 | 只纳入有效销售状态 |
| 订单金额/回款状态 | `cloudccorder.totalamount/paymentstatus` | 币种/普通选项 | 主单对账和辅助解释 |
| 明细订单/产品 | `cloudccorderitem.orderid/product2id` | 主从引用/引用 | 必须存在 |
| 产品数量 | `cloudccorderitem.quantity` | 数字 | `SALES_QUANTITY` 排名值 |
| 产品单价/总价 | `cloudccorderitem.unitprice/totalprice` | 币种 | 成交单价与 `SALES_AMOUNT` 排名值 |
| 明细状态 | `cloudccorderitem.status` | 普通选项 | 排除未激活/取消明细 |
| 合同金额/状态 | `contract.htje/zhuangtai` | 币种/普通选项 | 签约辅助口径 |
| 商机阶段 | `Opportunity.jieduan` | 普通选项 | 区分开放、赢单、输单 |
| 商机产品数量/金额 | `opportunitypdt.quantity/totalprice` | 数字/币种 | 预计事实，不并入实际销售 |

## 全局选项列表清单

本期不创建全局选项列表。目标字段均为现有标准对象普通选项或布尔字段，真实可用值必须在写入前读取字段元数据或用最小 dry-run 验证，不硬编码未经目标租户确认的选项值。

| 对象字段 | 类型 | 业务归一化 |
|---|---|---|
| `cloudccorder.status` | 普通选项 | `VALID / DRAFT / CANCELED / UNKNOWN` |
| `cloudccorderitem.status` | 普通选项 | `ACTIVE / INACTIVE / UNKNOWN` |
| `cloudccorder.paymentstatus` | 普通选项 | `UNPAID / PARTIAL / PAID / UNKNOWN` |
| `contract.zhuangtai` | 普通选项 | `DRAFT / ACTIVE / EXPIRED / CANCELED / UNKNOWN` |
| `Opportunity.jieduan` | 普通选项 | `OPEN / WON / LOST / UNKNOWN` |

## 指标语义

| 用户表达 | 指标 | 默认口径 |
|---|---|---|
| “销量最好”“卖得最多”“出货量最高” | `SALES_QUANTITY` | 有效订单明细数量之和，按单位展示 |
| “销售额最高”“收入最高” | `SALES_AMOUNT` | 有效订单明细总价之和 |
| “订单最多” | `ORDER_COUNT` | 去重有效订单数 |
| “客户覆盖最多”“买的客户最多” | `CUSTOMER_COUNT` | 去重购买客户数 |
| “销售得比较好”“热销产品” | `SALES_AMOUNT` | 销售额主排序，同时展示销量、订单数、客户数 |

时间表达缺失时默认最近 30 天；结果中必须显式说明时间范围和指标。用户明确说“销量”时不得按销售额排序。

## 高阶工具合同

工具名：`crm_product_sales_rank`。

```json
{
  "metric": "SALES_QUANTITY",
  "timePreset": "LAST_30_DAYS",
  "dateFrom": null,
  "dateTo": null,
  "topN": 5,
  "productSeries": null,
  "ownerId": null,
  "accountId": null,
  "comparePreviousPeriod": true
}
```

返回值必须包含 `ok/status/metric/metricLabel/dateFrom/dateTo/dataAsOf/sourceObjects/rows/coverage/warnings`。每行包含 `rank/productId/productName/productCode/unit/salesQuantity/salesAmount/orderCount/customerCount/previousValue/changeRate`。金额和数量由后端计算，模型不得修改。

## 确定性处理链路

1. Skill 根据触发词激活，仅向模型暴露 `crm_product_sales_rank`。
2. 模型输出工具 JSON 参数；后端执行 JSON Schema 校验、枚举归一化和边界限制。
3. 后端用当前用户身份查询 `cloudccorder`、`cloudccorderitem` 和 `product`。
4. 订单日期和状态先过滤，订单明细再按 `orderid`、`product2id` 关联。
5. 数量、金额、订单数和客户数由 Java 服务确定性聚合，不由模型计算。
6. 环比使用等长上一周期，上一周期为空时返回 `changeRate=null`。
7. 返回覆盖度、缺失关联和未知状态告警；关键关联缺失时不生成伪排名。
8. 最终模型只解释结果，不能修改排名值、时间范围和指标口径。

## 状态机矩阵

| 状态 | 条件 | 对话行为 |
|---|---|---|
| `SUCCESS` | 有有效订单明细和产品映射 | 返回 Top N、口径、趋势和建议下钻 |
| `EMPTY` | 查询成功但时间范围无有效销售 | 明确无有效订单销售记录，建议扩大范围 |
| `CRM_NOT_CONNECTED` | 当前用户无 CloudCC 绑定 | 提示连接 CRM，不返回 Mock 排名 |
| `PERMISSION_DENIED` | 当前用户无对象/字段/记录权限 | 说明受影响对象，不扩大权限 |
| `SCHEMA_UNSUPPORTED` | 租户缺少必需对象或字段 | 指出缺失语义，建议管理员刷新映射 |
| `PARTIAL` | 有结果但存在未知状态或缺失产品关联 | 返回可验证部分并显式告警覆盖度 |
| `UPSTREAM_ERROR` | CloudCC 查询超时或失败 | 保留问题并允许重试，不编造结果 |

## 权限与安全矩阵

| 层级 | 约束 |
|---|---|
| role | 继续由 CloudCC 角色层级决定记录可见范围，不做额外放大 |
| profile | 当前用户必须具备产品、订单、订单产品的读取与必要字段可见权限 |
| permission / permission set | 仅在演示账号缺少标准对象读权限时由管理员补充，不给分析工具写权限 |
| sharingRule | 演示数据应归 SalesA/Owen 可见范围；必要时用正规共享规则扩展 |

- CloudCC runtime token 只用于身份换票；AgentCiCi 后端生成的 accessToken 才用于 OpenAPI。
- token、secret、cookie、原始凭据不得进入模型 prompt、工具结果、trace、日志或项目文件。
- 工具固定为只读，不提供创建、更新、删除能力。

## 高仿真演示数据设计

### 数据规模

- 12 个产品，覆盖硬件、软件订阅、实施服务和数据服务四条产品线。
- 复用 FEAT-109 的 16 个核心客户，形成行业、客户分级、区域和所有人差异。
- 最近 180 天内至少 48 张订单，包含有效、草稿、取消、部分回款和已回款。
- 至少 120 条订单产品明细，每张订单 1–4 个产品，包含折扣、组合销售和重复购买。
- 至少 16 份合同，覆盖有效、即将到期、已到期和取消状态。
- 至少 24 个业务机会、60 条业务机会产品，形成实际销售与未来管道差异。
- 所有记录包含稳定批次标记 `TASK-205-CRM-ANALYTICS-DEMO-V1`。

### 产品样例

| 产品代码 | 产品名称 | 系列 | 单位 | 标准价格 | 经营特征 |
|---|---|---|---|---:|---|
| `DEMO-X1` | 智能巡检终端 X1 | 智能硬件 | 台 | 12,000 | 最近 30 天销量第一，多客户复购 |
| `DEMO-G5` | 边缘采集网关 G5 | 智能硬件 | 台 | 18,000 | 销售额高、数量第二 |
| `DEMO-S2` | 安全监测传感器 S2 | 智能硬件 | 套 | 6,800 | 数量高、客单价较低 |
| `DEMO-MP` | 移动巡检专业版 | 软件订阅 | 席位 | 3,600 | 客户覆盖广、续费明显 |
| `DEMO-PA` | 预测维护分析包 | 数据服务 | 套 | 88,000 | 数量低但销售额高 |
| `DEMO-FS` | 现场服务协同版 | 软件订阅 | 席位 | 5,200 | 与硬件组合销售 |
| `DEMO-VI` | 视觉识别节点 | 智能硬件 | 台 | 25,000 | 新品增长快 |
| `DEMO-DH` | 设备健康数据包 | 数据服务 | 套 | 46,000 | 老客户增购 |
| `DEMO-IM` | 巡检实施服务 | 实施服务 | 项 | 120,000 | 金额高但不应主导销量解释 |
| `DEMO-TR` | 运维培训服务 | 实施服务 | 场 | 18,000 | 订单多、数量低 |
| `DEMO-API` | 设备连接 API 包 | 数据服务 | 万次 | 9,800 | 用量单位不同，结果需展示单位 |
| `DEMO-BK` | 备件保障包 | 实施服务 | 年 | 36,000 | 合同型持续收入 |

### 稳定验收排名

以验收日向前 30 天、`SALES_QUANTITY`、有效订单口径为准，种子脚本必须构造并回读以下前三名：

1. 智能巡检终端 X1
2. 边缘采集网关 G5
3. 安全监测传感器 S2

取消订单、草稿订单和未激活明细中可以包含更高数量，用于验证过滤规则不会污染排名。销售额排名应与销量排名至少有一处不同，以体现指标语义价值。

## 演示数据执行与回滚

- 脚本默认 `--dry-run`，只有显式 `--execute` 才写入 CloudCC。
- 写入前读取对象字段与可用选项；缺少必需字段或选项映射时失败关闭。
- 以产品代码、批次和稳定业务键幂等创建或更新，不重复堆积。
- 创建顺序：产品 → 客户复用 → 商机 → 商机产品 → 合同 → 订单 → 订单产品。
- 每阶段创建后用当前演示用户回读 ID、关键字段和记录数量。
- 不执行未授权批量删除；如需清理，先生成批次记录删除清单并要求单独确认。
- 本期没有 MetadataService 元数据变更，不产生 planId/operationId；如后续必须补字段，另行执行 `plan → review → apply → changes → rollback-plan`。

## 错误处理

- CloudCC 单页上限不足时服务端自动分页，设置最大页数和最大记录数防止无界查询。
- 主单成功但明细失败时返回 `UPSTREAM_ERROR`，不能用主单金额猜产品排名。
- 明细缺少产品引用时计入 `missingProductItems`；全部缺失时返回 `SCHEMA_UNSUPPORTED`。
- 未知订单状态默认不计入有效销售并产生 warning，避免把草稿误算为销量。
- 混合单位排名必须展示单位，并提示可按产品系列下钻。
- 回答不得出现“让我换个方式查找”后停止；重试失败后必须给出明确原因和下一步。

## 测试与验收

### 单元与集成测试

- 指标和时间范围归一化。
- 订单/明细状态过滤、多订单多客户聚合与去重、等长周期环比。
- 缺失产品、未知状态、空数据、上游失败和当前用户权限分支。
- Tool whitelist、Skill 激活、JSON Schema 和输出合同。

### CRM 数据验收

- 12 个产品、48 张以上订单、120 条以上订单明细可按批次回读。
- 产品、订单、订单产品、合同、商机产品关联完整。
- 有效与无效订单状态均有样例，过滤后稳定排名符合规格。
- Owen/SalesA 能读取全部核心演示排名事实，其他用户只看到各自权限范围数据。

### 对话稳定性验收

对同一演示用户连续新建 5 个会话询问“销量最好的产品有哪些”：

- 5 次均调用 `crm_product_sales_rank`。
- 5 次参数均为 `SALES_QUANTITY + LAST_30_DAYS + topN=5`。
- 5 次前三名和数值完全一致。
- 回答均说明最近 30 天、有效订单销量、数据时间和单位。
- 不出现对象名猜测、内部重试承诺、Mock 数据或未经工具返回的数字。

继续追问“按销售额呢”“过去 90 天呢”“哪个客户买得最多”时，复用会话上下文并只改变明确参数。

## 完成定义

- `CRM 经营分析` Skill 已发布并可由 `cici-system` 稳定激活。
- `crm_product_sales_rank` 只读工具通过单元、集成和权限测试。
- 高仿真演示数据已真实写入绑定的 CloudCC CRM 并完成回读。
- 真实对话 5 次稳定性验收通过，答案与 CRM 聚合结果一致。
- 运行 trace 能看到 Skill、工具、参数摘要、覆盖度和最终回答。
- `.claw/test-report.md` 记录全部真实验证命令和结果。

