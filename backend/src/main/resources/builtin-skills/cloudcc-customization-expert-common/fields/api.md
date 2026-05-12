# fields API 文档

本文档描述 `fields` 模块远程 API 与字段参数模型，仅保留接口视角。

## 通用约定

- 服务：`setupSvc`
- 鉴权：请求头 `accessToken`
- 成功标识：`result === true`
- 失败信息：优先 `returnInfo`，其次 `message`

## 1) 查询对象字段列表

- 方法：`POST`
- 路径：`/api/fieldSetup/queryField`
- Content-Type：`application/json`

### 请求参数

- `prefix`（string，必填）：对象前缀。

### 请求体

```json
{
  "prefix": "a75"
}
```

### 返回结构（模块输出口径）

- `obj`（object）：对象信息。
- `stdFields`（array）：标准字段（精简）。
- `cusFields`（array）：自定义字段（精简）。

字段项结构：

- `fieldname`（string）：字段名称（labelName）。
- `apiname`（string）：字段 API 名（schemefieldName）。
- `schemefieldType`（string）：字段类型代码。
- `id`（string）：字段 ID。

---

## 2) 创建字段

- 方法：`POST`
- 路径：`/api/fieldSetup/save`
- Content-Type：`application/json`

### 请求参数（业务入参）

- `fieldType`（string，必填）：字段类型代码（见“字段类型参数模型”）。
- `objid`（string，必填）：对象 ID。
- `nameLabel`（string，必填）：字段显示名。
- `remark`（string，可选）：字段备注；默认 `auto created by cloudcc-cli ai agent`。
- 类型专属参数：按 `fieldType` 不同传入。

### 请求体说明

模块通过 `buildFieldData` 组装 payload，并在创建时补充：

- `layoutIds`：自动取 `/api/layout/newpage` 返回的第一个布局 ID（若存在）。
- `obj.remark` 与顶层 `remark`：同步写入备注。

---

## 3) 更新字段

- 方法：`POST`
- 路径：`/api/fieldSetup/saveField`
- Content-Type：`application/json`

### 请求参数（业务入参）

- `fieldId`（string，必填）：字段 ID。
- `fieldType`（string，必填）：字段类型代码。
- `objid`（string，必填）：对象 ID。
- `nameLabel`（string，必填）：字段显示名。
- `remark`（string，可选）：字段备注。

### 更新前依赖调用

- 先调 `/api/fieldSetup/editField` 读取当前字段元数据；
- 合并以下关键字段回写到 `saveField` payload：
  - `obj.datafieldRef`
  - `obj.apiname`（来自 `schemefieldName/apiname`）
  - `profileFieldJson`（优先详情中的 `profileFieldJson`，否则由 `profileList` 转换）

---

## 4) 删除字段（彻底删除）

- 方法：`POST`
- 路径：`/api/fieldSetup/deleteFieldCompletely`
- Content-Type：`application/json`

### 请求参数

- `id`（string，必填）：字段 ID。
- `objid`（string，必填）：对象 ID。

### 请求体

```json
{
  "id": "fld001",
  "objid": "obj001"
}
```

---

## 5) 查询字段详情（编辑态）

- 方法：`POST`
- 路径：`/api/fieldSetup/editField`
- Content-Type：`application/json`

### 请求参数

- `fieldId`（string，必填）：字段 ID。
- `fdtype`（string，必填）：字段类型代码。
- `objid`（string，必填）：对象 ID。

### 请求体

```json
{
  "fieldId": "fld001",
  "fdtype": "S",
  "objid": "obj001"
}
```

### 返回结构

- `fieldObj`（object）：字段定义。
- `profileFieldJson`（array，可选）：简档字段权限。
- `profileList`（array，可选）：简档权限原始结构。

---

## 6) 依赖接口：布局列表（创建字段时）

- 方法：`POST`
- 路径：`/api/layout/newpage`
- Content-Type：`application/json`

### 请求参数

- `objid`（string，必填）：对象 ID。

### 请求体

```json
{
  "objid": "obj001"
}
```

### 用途

- 取返回列表第一条 `id` 作为字段创建请求的 `layoutIds`。

---

## 7) 字段类型参数模型（`fieldType`）

以下为模块支持类型及专属参数（除通用参数 `objid/nameLabel/remark/helps/defaultValue` 外）。

### 7.1 文本/数字常见类型

- `S`：单行文本
  - `schemefieldLength`（默认 `255`）
  - `isrepeat`（默认 `"true"`）
  - `placeholder`（可选）
  - `casesensitive`（仅 `isrepeat=false` 时生效，默认 `"false"`）
- `X`：文本区
  - `schemefieldLength`（1~4000，默认 `255`）
  - `placeholder`
- `J`：长文本
  - `schemefieldLength`（1~32000，默认 `32000`）
  - `placeholder`
- `N`：数字
  - `schemefieldLength`（整数位，默认 `10`）
  - `decimalPlaces`（小数位，默认 `0`）
  - `isrepeat`（默认 `"true"`）
  - `displayThousands`（默认 `"0"`）
  - 约束：`schemefieldLength + decimalPlaces <= 18`
- `P`：百分比
  - `schemefieldLength`（默认 `10`）
  - `decimalPlaces`（默认 `2`）
  - 约束同上
- `C`：货币
  - `schemefieldLength`（默认 `10`）
  - `decimalPlaces`（默认 `2`）
  - 约束同上

### 7.2 时间/布尔/基础类型

- `D` 日期、`F` 日期时间、`T` 时间、`B` 复选框、`A` 自动编号、`H` 电话、`E` 邮箱、`U` URL
  - 主要使用通用参数
  - `U` 额外支持 `edittype`（默认 `"_blank"`）
  - `E` 额外支持 `isrepeat`（默认 `"true"`）

### 7.3 选项类

- `L`：单选下拉
  - `ptext`（选项文本，`useGlobalSelect=0` 时必填）
  - `useGlobalSelect`（`0/1`，默认 `0`）
  - `edittype`（`radio/select`，默认 `select`）
  - `isPicklistSorted`（`0/1`）
  - `defPl`（`0/1`）
  - `globalSelectId`（`useGlobalSelect=1` 时必填）
- `Q`：多选下拉
  - 继承 `L` 所有参数
  - `visibleLines`（1~100，默认 `4`）
  - `showalloptions`（`0/1`）

### 7.4 关联类

- `Y`：查找关系
  - `lookupObj`（必填）
  - `lookupObjDefaultField`（可选）
- `MR`：主从关系
  - `lookupObj`（必填）
- `M`：关联关系
  - `lookupObj`（必填）

### 7.5 文件/图片/位置/加密

- `IMG`：图片
  - `defaultValue`（上传数量上限，1~100，默认 `3`）
  - `formulaType`（`url/input`，默认 `input`）
  - `watermarkstatus`（`0/1`，默认 `0`）
- `FL`：文件
  - `defaultValue`（上传数量上限，1~100，默认 `1`）
- `LT`：经纬度
  - `schemefieldLength`（默认 `8`）
  - `decimalPlaces`（默认 `10`）
  - `displayType`（`1` 数字 / `2` 度分秒，默认 `1`）
  - 约束：`schemefieldLength + decimalPlaces <= 18`
- `ENC` / `ENCD`：加密文本
  - `schemefieldLength`（1~255，默认 `255`）
  - `masktype`（`all/4/card/custom`，默认 `all`）
  - `encrypttype`（`masktype=custom` 时可传规则，默认 `{AAAA}{****}{AAAA}`）
  - `maskcharacter`（`*` 或 `X`，默认 `*`）

### 7.6 其他类型

- `SCORE`：评分
  - `schemefieldLength`（1~100，默认 `10`）
- `AD`：地址
  - 使用通用参数即可
