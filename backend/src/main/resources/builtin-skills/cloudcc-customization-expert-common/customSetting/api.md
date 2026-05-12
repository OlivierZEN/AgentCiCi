# customSetting API 文档

本文档描述 `customSetting` 模块的远程 API，仅保留接口视角。

## 通用约定

- 服务：`setupSvc`
- 鉴权：请求头 `accessToken`
- 接口前缀：`{setupSvc}/api/customsetting`
- 成功标识：`result === true`
- 失败信息：优先 `returnInfo`，其次 `message`

## 1) 查询自定义设置列表

- 方法：`POST`
- 路径：`/api/customsetting/list`
- Content-Type：`application/json`

### 请求参数

- 条件对象（object，可选），默认 `{}`。

### 请求体示例

```json
{}
```

### 返回结构

- `data.objList`（array）：自定义设置列表

---

## 2) 新建/保存自定义设置主对象

- 方法：`POST`
- 路径：`/api/customsetting/save`
- Content-Type：`application/json`

### 请求参数

- `tpSysObjectVO`（object，必填）：主对象数据。

### `tpSysObjectVO` 常见字段

- `id`（string，可选）：更新时传，新增可空。
- `label`（string，必填）：设置名称。
- `schemetableName`（string，必填）：对象 API 名/表名。
- `dataType`（string，可选）：设置类型。
- `accessable`（string|number，可选）：可见性/访问级别。
- `remark`（string，可选）：备注。

### 请求体示例

```json
{
  "tpSysObjectVO": {
    "id": "",
    "label": "系统参数",
    "schemetableName": "SystemConfig__c",
    "dataType": "list",
    "accessable": "1",
    "remark": "配置项"
  }
}
```

---

## 3) 查询自定义设置详情

- 方法：`POST`
- 路径：`/api/customsetting/detail`
- Content-Type：`application/json`

### 请求参数

- `objid`（string，必填）：自定义设置 ID。

### 请求体

```json
{
  "objid": "obj001"
}
```

### 返回结构

- `data`（object）：详情对象（包含主对象信息与字段列表）

---

## 4) 编辑前回显主对象

- 方法：`POST`
- 路径：`/api/customsetting/modify`
- Content-Type：`application/json`

### 请求参数

- `objid`（string，必填）：自定义设置 ID。

### 请求体

```json
{
  "objid": "obj001"
}
```

### 返回结构

- `data`（object）：编辑页回显数据

---

## 5) 删除自定义设置主对象

- 方法：`POST`
- 路径：`/api/customsetting/deleteobj`
- Content-Type：`application/json`

### 请求参数

- `objid`（string，必填）：自定义设置 ID。

### 请求体

```json
{
  "objid": "obj001"
}
```

---

## 6) 保存自定义设置字段（新增/编辑）

- 方法：`POST`
- 路径：`/api/customsetting/saveField`
- Content-Type：`application/json`

### 请求参数

- `fdtype`（string，必填）：字段类型。
- `tpSysSchemetableVO`（object，必填）：字段定义对象。

### `tpSysSchemetableVO` 常用字段

- `schemetableId`（string，必填）：所属设置对象 ID。
- `id`（string，可选）：字段 ID（更新时传）。
- `nameLabel`（string，必填）：字段显示名。
- `apiname`（string，必填）：字段 API 名。
- `edittype`（string，可选）：编辑器类型。
- `schemefieldLength`（string|number，可选）：长度。
- `defaultValue`（string，可选）：默认值。
- `datafieldRef`（string，可选）：字段引用信息。
- `fieldState`（string，可选）：字段状态。
- `schemefieldType`（string，可选）：字段类型代码。
- `schemefieldName`（string，可选）：字段 API 名（兼容字段）。

### 请求体示例

```json
{
  "fdtype": "S",
  "tpSysSchemetableVO": {
    "schemetableId": "obj001",
    "id": "",
    "nameLabel": "参数编码",
    "apiname": "paramCode",
    "schemefieldLength": "255",
    "defaultValue": ""
  }
}
```

---

## 7) 获取字段编辑数据

- 方法：`POST`
- 路径：`/api/customsetting/editfile`
- Content-Type：`application/json`

### 请求参数

- `queryrecordid`（string，必填）：字段记录 ID。

### 请求体

```json
{
  "queryrecordid": "field001"
}
```

### 返回结构

- `data`（object）：字段编辑页回显数据

---

## 8) 删除自定义设置字段

- 方法：`POST`
- 路径：`/api/customsetting/deletefield`
- Content-Type：`application/json`

### 请求参数

- `id`（string，必填）：字段 ID。
- `objid`（string，必填）：所属自定义设置 ID。

### 请求体

```json
{
  "id": "field001",
  "objid": "obj001"
}
```
