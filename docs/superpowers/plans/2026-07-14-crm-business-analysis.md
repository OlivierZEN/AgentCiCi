# CRM 经营分析能力实施计划

> **执行要求：** 按 `superpowers:test-driven-development` 逐项先写失败测试再实现；出现非预期失败时切换到 `superpowers:systematic-debugging`；交付声明前执行 `superpowers:verification-before-completion`。

**目标：** 为 AgentCiCi 提供稳定的 CRM 产品销售排行能力，在绑定的 CloudCC CRM 租户中建立可重复、高仿真的经营样例数据，并使“销量最好的产品有哪些”稳定返回可追溯的真实结果。

**架构：** 使用平台标准文件型 Skill 约束意图、口径与回答格式；以一个确定性的高阶只读工具 `crm_product_sales_rank` 完成 `product → cloudccorderitem → cloudccorder` 的跨对象查询、过滤、聚合与排序。合同、商机及商机产品用于经营背景和漏斗演示，不混入实际销量。演示数据通过独立、幂等、默认 dry-run 的脚本写入当前绑定租户。

**技术栈：** Java 21、Spring Boot、Jackson、JUnit 5、Mockito、CloudCC OpenAPI、Python 3。

## 全局约束

- 复用 CloudCC 标准对象和字段，不新增元数据。
- 工具只读，并沿用当前用户的租户、令牌和数据权限。
- “销量”默认按有效订单明细数量统计；“销售得好”默认按销售额统计；默认最近 30 天、Top 5。
- 演示数据批次为 `TASK-205-CRM-ANALYTICS-DEMO-V1`，脚本可重复执行且不得删除非本批次记录。
- 目标排行固定可验证：`DEMO-X1`、`DEMO-G5`、`DEMO-S2` 为最近 30 天销量前三；销售额排行至少有一处不同。
- 仅做桌面端产品质量门，不新增移动端实现或测试。

---

## 任务 1：扩展文件型标准 Skill 的运行时策略契约

**文件：**

- 修改：`backend/src/main/java/com/codehouse/ciciassistant/skill/service/FileBackedBuiltinSkillCatalog.java`
- 修改：`backend/src/main/java/com/codehouse/ciciassistant/skill/service/FileBackedBuiltinSkillSyncService.java`
- 测试：`backend/src/test/java/com/codehouse/ciciassistant/skill/FileBackedBuiltinSkillIntegrationTest.java`

**步骤：**

1. 先增加集成测试，断言 manifest 中的 `toolWhitelist`、`kbWhitelist`、`handoffRule`、`outputContract` 能进入 Skill 定义和版本。
2. 运行 `cd backend && mvn -Dtest=FileBackedBuiltinSkillIntegrationTest test`，确认测试因字段未映射而失败。
3. 扩展 manifest record，给新增集合字段提供空列表兼容；同步服务将策略写入模板版本、Skill 定义与 Skill 版本。
4. 重跑同一测试，确认新旧文件型 Skill 均兼容。

## 任务 2：实现确定性的产品销售排行领域服务

**文件：**

- 新增：`backend/src/main/java/com/codehouse/ciciassistant/crmanalysis/service/CrmProductSalesAnalysisService.java`
- 新增：`backend/src/test/java/com/codehouse/ciciassistant/crmanalysis/service/CrmProductSalesAnalysisServiceTest.java`

**步骤：**

1. 先写单元测试覆盖：默认最近 30 天、有效/取消/草稿过滤、数量与金额两种排行、客户数去重、同分稳定排序、上期同比、空数据和部分异常。
2. 运行 `cd backend && mvn -Dtest=CrmProductSalesAnalysisServiceTest test`，确认类不存在或断言失败。
3. 用 `CloudccOpenApiService.queryAllRecords` 读取产品、订单、订单明细；本地再次执行日期与状态防御性过滤。
4. 对查找字段、数值和日期实施兼容解析，按产品聚合并输出数据来源、覆盖率、告警和 `dataAsOf`。
5. 重跑测试并补足边界用例。

## 任务 3：注册和调度高阶只读工具

**文件：**

- 新增：`backend/src/main/java/com/codehouse/ciciassistant/crmanalysis/service/CrmProductSalesAnalysisToolService.java`
- 修改：`backend/src/main/java/com/codehouse/ciciassistant/ai/service/ToolOrchestratorService.java`
- 修改：`backend/src/main/java/com/codehouse/ciciassistant/tool/service/BuiltinToolCatalog.java`
- 测试：`backend/src/test/java/com/codehouse/ciciassistant/crmanalysis/service/CrmProductSalesAnalysisToolServiceTest.java`
- 测试：`backend/src/test/java/com/codehouse/ciciassistant/ai/service/ToolOrchestratorServiceTest.java`

**步骤：**

1. 先写测试断言工具名称、JSON Schema、参数默认值、权限透传和调度优先级。
2. 运行对应测试，确认工具尚未注册。
3. 实现 `crm_product_sales_rank`，仅接受指标、时间范围、Top N、比较开关等有限参数，并序列化统一状态结果。
4. 在内建工具目录与调度器中注册，保持 MCP 与其他工具路由不变。
5. 重跑相关单元测试。

## 任务 4：提供 CRM 经营分析标准 Skill 并默认绑定 CiCi

**文件：**

- 新增：`backend/src/main/resources/builtin-skills/crm-business-analysis/manifest.json`
- 新增：`backend/src/main/resources/builtin-skills/crm-business-analysis/SKILL.md`
- 新增：`backend/src/main/resources/builtin-skills/crm-business-analysis/product-sales/introduction.md`
- 新增：`backend/src/main/resources/builtin-skills/crm-business-analysis/product-sales/api.md`
- 修改：`backend/src/main/java/com/codehouse/ciciassistant/skill/service/SkillDefinitionService.java`
- 测试：`backend/src/test/java/com/codehouse/ciciassistant/skill/FileBackedBuiltinSkillIntegrationTest.java`

**步骤：**

1. 先增加测试，断言标准 Skill 可加载、仅声明高阶工具，并以 `always-on` 方式绑定 `cici-system`。
2. 运行测试，确认资源和默认绑定缺失。
3. 编写窄范围 Skill 指令：意图映射、默认口径、必须调用工具、禁止用模型自行计算、空数据与异常话术、可追溯输出模板。
4. 增加默认绑定；重新初始化测试租户并断言运行时工具清单包含 `crm_product_sales_rank`。
5. 重跑 Skill 集成测试。

## 任务 5：建立幂等的 CloudCC CRM 高仿真演示数据

**文件：**

- 新增：`scripts/seed-crm-analytics-demo.py`
- 新增：`backend/src/test/java/com/codehouse/ciciassistant/crmanalysis/CrmAnalyticsDemoDatasetContractTest.java`

**步骤：**

1. 先写数据合同测试，校验 12 个产品、时间分布、有效与无效订单、目标数量排行、差异化金额排行、合同和商机覆盖。
2. 实现数据生成器与命令行：默认 `--dry-run`；仅 `--execute` 写入；以批次标识和外部业务键查询后新增或更新。
3. 先运行 `python3 scripts/seed-crm-analytics-demo.py --dry-run`，检查对象、记录数、日期、金额和目标排行。
4. 查询绑定租户的字段选项和现有账号，确认必填字段、合法状态值与查找关系。
5. 运行 `python3 scripts/seed-crm-analytics-demo.py --execute`，写入产品、订单、订单明细、合同、商机和商机产品。
6. 再运行一次 dry-run，断言待新增为 0 且仅显示幂等更新或无变化。

## 任务 6：端到端验证、对话稳定性评估与交付

**文件：**

- 修改：`.claw/tasks/TASK-205.md`
- 修改：`.claw/current-status.md`
- 修改：`.claw/test-report.md`
- 修改：`docs/specs/FEAT-111-crm-business-analysis-skill.md`（仅在实现决策变化时）

**步骤：**

1. 在绑定租户直接运行高阶工具，验证最近 30 天数量前三为 X1、G5、S2，并核对数量、金额、订单数和客户数。
2. 用 5 个新会话重复提问“销量最好的产品有哪些”，记录工具调用参数与回答排行的一致性。
3. 执行相关单测及 `cd backend && mvn test`；若全量测试存在历史失败，区分本任务回归与既有问题并保留证据。
4. 执行项目身份、assignment、状态与 diff 检查；将真实结果写入 `.claw/test-report.md`。
5. 更新任务和热状态为完成，提交并推送 `codex/TASK-205-crm-business-analysis`。

