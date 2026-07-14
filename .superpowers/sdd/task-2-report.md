# Task 2 实施报告

## 状态

DONE

## 实施范围

- 扩展 `CrmProductSalesAnalysisService`：核心权限不完整、多币种、全量贡献率、正向实现均价、Top1/Top3 客户集中度、量/值冠军、开放商机产品、活跃/临期合同与续约缺口。
- 新增确定性诊断：核心增长、折扣驱动、客户集中、管道断层、潜在增长、续约风险与高价值产品。
- 新增 `CrmProductSalesAnswerFormatter`：统一生成直接结论、Top 5、经营诊断、前瞻信号、建议动作和口径覆盖；支持结构化对象与 JSON 两种入口。
- 安全边界：不回显任意 warning、原始 JSON、内部 ID 或工具字段；商机/合同不可用时不误报为零。
- 向后兼容：保留原 `SalesRankResult` 构造器和已发布 JSON 字段，新指标仅追加。
- 更新 CRM Tool 描述和内置 Skill v2 契约文档。

## TDD RED 证据

1. `mvn -q -Dtest=CrmProductSalesAnalysisServiceTest#reportsDataAccessIncompleteWhenOrderItemsAreVisibleButOrderMastersAreNot test`
   - 预期失败：生产回归夹具 `0 / 1888` 实际返回 `EMPTY`，期望 `DATA_ACCESS_INCOMPLETE`。
2. 核心指标 RED
   - 预期 `testCompile` 失败：`quantityContributionRate` / `realizedAveragePrice` / 集中度 / `summary` 尚不存在。
3. 前瞻信号 RED
   - 预期 `testCompile` 失败：`pipeline` / `contracts` / `insights` 尚不存在。
4. 状态边界 RED
   - 预期 `testCompile` 失败：币种可比性尚不存在；已启用/审批通过合同和全量不可见产品回归随后均按预期失败。
5. Formatter RED
   - `mvn -q -Dtest=CrmProductSalesAnswerFormatterTest test`
   - 预期 `testCompile` 失败：`CrmProductSalesAnswerFormatter` 尚不存在。
6. 安全语义 RED
   - 部分增强对象缺失时，原实现仍输出非零商机/合同信号，未标记“不可用”。
7. 深度诊断 RED
   - 量增 100%、额增 60%、均价降 20% 及零销售/强管道夹具仅返回旧诊断，缺少 `DISCOUNT_DRIVEN` / `POTENTIAL_GROWTH`。
8. Tool 契约 RED
   - 工具描述缺少确定性、贡献率、商机与合同语义。

## GREEN 证据

- `mvn -q -Dtest='CrmProductSales*Test,CrmAnalyticsDemoDatasetContractTest' test`
  - PASS：`CrmProductSalesAnalysisServiceTest` 12，`CrmProductSalesAnswerFormatterTest` 11，`CrmProductSalesAnalysisToolServiceTest` 3，数据契约 3；共0失败、0错误。
- `mvn -q -DskipTests test-compile`
  - PASS。
- `jq empty backend/src/main/resources/builtin-skills/crm-business-analysis/manifest.json`
  - PASS。
- `git diff --check`
  - PASS。

## 风险与交接

- Task 2 未修改 `ChatOrchestratorService`、Agent OpenAPI、数据脚本或前端；用户协议防泄漏接入由 Task 3 使用本 formatter 完成。
- 商机、商机产品、合同或客户增强查询失败时，排行降级为 `PARTIAL`；核心订单事实仍保留。
- 金额主指标在未折算多币种时进入 `DATA_QUALITY_BLOCKED`；数量主指标保留排行，但金额、均价和基于金额的集中度置为不可比。
- 合同活跃选项已覆盖租户实际的“已启用”与“审批通过”；“审批中”和“草稿”不计为活跃合同。
