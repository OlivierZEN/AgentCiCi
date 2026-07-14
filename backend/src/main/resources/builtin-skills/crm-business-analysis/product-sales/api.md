# crm_product_sales_rank

只读高阶工具，固定完成跨对象读取、日期和状态过滤、聚合、排序及上期比较。

## 输入

- `metric`：`SALES_QUANTITY`、`SALES_AMOUNT`、`ORDER_COUNT`、`CUSTOMER_COUNT`。
- `startDate`、`endDate`：可选，格式 `YYYY-MM-DD`；不传时为最近 30 天。
- `topN`：可选，1 到 20，默认 5。
- `comparePrevious`：可选，默认 true。

## 输出

- `status`：`SUCCESS`、`PARTIAL`、`EMPTY`、`DATA_ACCESS_INCOMPLETE`、`DATA_QUALITY_BLOCKED`、`CRM_NOT_CONNECTED`、`PERMISSION_DENIED`、`SCHEMA_UNSUPPORTED` 或 `UPSTREAM_ERROR`。
- `metric`、`startDate`、`endDate`、`dataAsOf`、`sourceObjects`。
- `rows`：保留原有排行、产品、销量、销售额、订单数、客户数、上期值和变化率，并新增数量/金额贡献率、实现均价、Top1/Top3 客户集中度、数量/金额排名、产品商机管道和合同信号。
- `summary`：Top N 截断前的全量销量、订单销售额、订单/客户数、币种可比性与量/值冠军。
- `insights`：由可验证事实触发的确定性经营诊断，每条含结论、证据和建议动作。
- `coverage`：扫描和计入的订单、明细数量。
- `warnings`：服务内部的数据缺失、权限覆盖或上游异常提示；用户侧只显示安全的自然语言摘要，不回显原始载荷。

`PARTIAL` 不会丢弃已验证的订单销售事实。`DATA_ACCESS_INCOMPLETE` 表示可见明细与订单主表或产品主数据的权限范围不一致，不能解读为无销售。
