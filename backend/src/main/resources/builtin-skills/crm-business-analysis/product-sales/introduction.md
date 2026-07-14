# 产品销售排行口径

本模块把标准产品、订单和订单明细组织成确定性的经营指标。实际销售事实来自 `cloudccorderitem`，订单日期、状态和客户来自 `cloudccorder`，产品名称、编码和单位来自 `product`。

- `SALES_QUANTITY`：有效订单明细的 `quantity` 合计。
- `SALES_AMOUNT`：有效订单明细的 `totalprice` 合计；缺失时使用数量乘单价。
- `ORDER_COUNT`：每个产品关联的有效订单去重数。
- `CUSTOMER_COUNT`：每个产品关联的有效订单客户去重数。

草稿、取消、作废、退回、关闭等状态不计入。合同、商机和商机产品用于经营背景，不计入已实现销量。
