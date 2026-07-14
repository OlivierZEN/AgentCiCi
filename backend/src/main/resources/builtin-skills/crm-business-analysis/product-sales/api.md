# crm_product_sales_rank

只读高阶工具，固定完成跨对象读取、日期和状态过滤、聚合、排序及上期比较。

## 输入

- `metric`：`SALES_QUANTITY`、`SALES_AMOUNT`、`ORDER_COUNT`、`CUSTOMER_COUNT`。
- `startDate`、`endDate`：可选，格式 `YYYY-MM-DD`；不传时为最近 30 天。
- `topN`：可选，1 到 20，默认 5。
- `comparePrevious`：可选，默认 true。

## 输出

- `status`：`SUCCESS`、`EMPTY`、`CRM_NOT_CONNECTED`、`PERMISSION_DENIED`、`SCHEMA_UNSUPPORTED`、`PARTIAL` 或 `UPSTREAM_ERROR`。
- `metric`、`startDate`、`endDate`、`dataAsOf`、`sourceObjects`。
- `rows`：排行、产品、销量、销售额、订单数、客户数、上期值和变化率。
- `coverage`：扫描和计入的订单、明细数量。
- `warnings`：数据缺失、权限覆盖或上游异常提示。
