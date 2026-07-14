# crm_product_sales_rank

只读高阶工具，固定完成跨对象读取、日期和状态过滤、聚合、排序及上期比较。

## 输入

- `metric`：`SALES_QUANTITY`、`SALES_AMOUNT`、`ORDER_COUNT`、`CUSTOMER_COUNT`。
- `startDate`、`endDate`：可选，格式 `YYYY-MM-DD`；不传时为最近 30 天。
- `topN`：可选，1 到 20，默认 5。
- `comparePrevious`：可选，默认 true。

确定性路由仅在参数能够可靠解析时强制调用：

- “近/最近/过去/前 N 天”按包含今天在内的 N 个自然日传入 `startDate/endDate`。
- “本月”从当月 1 日统计到今天；“上月”使用上一自然月完整范围；“本季度”从当前季度首日统计到今天。
- `YYYY-MM-DD` 或 `YYYY年M月D日` 的明确起止范围直接传入对应日期。
- 未出现明确时间时省略日期参数，由工具使用最近 30 天；未出现 Top N 时传入 5。
- 中文或阿拉伯数字 Top N 必须完整解析且只接受 1 到 20，不能把“一百”等越界数截断成前缀小数。
- 同一问题出现多个不同指标，或要求最少、最低、升序等非降序方向时，不强制选择某一指标，应退出强制路由并请求澄清。
- 多个时间表达只有解析为同一范围时才可继续；范围不一致、日期倒置或无法可靠解析的显式时间不得改写为默认值，应退出强制路由并请求澄清。

## 输出

- `status`：`SUCCESS`、`PARTIAL`、`EMPTY`、`DATA_ACCESS_INCOMPLETE`、`DATA_QUALITY_BLOCKED`、`CRM_NOT_CONNECTED`、`PERMISSION_DENIED`、`SCHEMA_UNSUPPORTED` 或 `UPSTREAM_ERROR`。
- `metric`、`startDate`、`endDate`、`dataAsOf`、`sourceObjects`。
- `rows`：保留原有排行、产品、销量、销售额、订单数、客户数、上期值和变化率，并新增数量/金额贡献率、实现均价、Top1/Top3 客户集中度、数量/金额排名、产品商机管道和合同信号。
- `summary`：Top N 截断前的全量销量、订单销售额、订单/客户数、币种可比性与量/值冠军。
- `insights`：由可验证事实触发的确定性经营诊断，每条含结论、证据和建议动作。
- `coverage`：扫描和计入的订单、明细数量。
- `warnings`：服务内部的数据缺失、权限覆盖或上游异常提示；用户侧只显示安全的自然语言摘要，不回显原始载荷。

`PARTIAL` 不会丢弃已验证的订单销售事实。`DATA_ACCESS_INCOMPLETE` 表示可见明细与订单主表或产品主数据的权限范围不一致，不能解读为无销售。
