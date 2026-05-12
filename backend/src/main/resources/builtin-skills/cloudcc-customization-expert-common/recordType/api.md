# recordType API 文档

本文档描述 `recordType` 模块远程 API，仅保留接口视角。

## 通用约定

- 服务：`setupSvc`（部分查询接口使用 `apiSvc`）
- 鉴权：请求头 `accessToken`
- 成功标识：`result === true`
- 失败信息：优先 `returnInfo`，其次 `message`

## 1) 查询记录类型（按对象前缀）

- 方法：`POST`
- 路径：`/api/batch/getRecordType`（服务：`apiSvc`）
- Content-Type：`application/json`

### 请求参数

- `prefix`（string，必填）：对象前缀

### 请求体

```json
{
  "prefix": "a75"
}
```

### 返回结构

- `data.recordTypeList`（array）：记录类型列表

---

## 2) 查询对象的记录类型列表

- 方法：`POST`
- 路径：`/api/recordType/getRecordTypeList`
- Content-Type：`application/json`

### 请求参数

- `objid`（string，必填）：对象 ID

### 请求体

```json
{
  "id": "",
  "profileId": "",
  "objid": "obj001"
}
```

### 返回结构

- `data`（object）：记录类型列表及关联信息

---

## 3) 获取新建记录类型初始化信息

- 方法：`POST`
- 路径：`/api/recordType/newRecordType`
- Content-Type：`application/json`

### 请求参数

- `objid`（string，必填）：对象 ID

### 请求体

```json
{
  "objid": "obj001"
}
```

### 返回结构（模块输出口径）

- `existsRectypeList`（array）：现有记录类型
- `profilemap`（object）：简档映射
- `layouts`（array）：页面布局
- `profileRecordtypes`（object）：简档与记录类型关系
- `slist`（array）：其他辅助数据

---

## 4) 创建记录类型

- 方法：`POST`
- 路径：`/api/recordType/saveRecordType`
- Content-Type：`application/json`

### 业务入参

- `objid`（string，必填）：对象 ID
- `name`（string，必填）：记录类型标签
- `apiCode`（string，必填）：唯一名称

### 请求体结构

- `objid`
- `obj`：
  - `existsRectype`（固定 `""`）
  - `description`（固定 `""`）
  - `isenable`（固定 `""`）
  - `name`
  - `apiCode`
- `existobj`（固定 `""`）
- `pslist`（array）：简档配置
  - `profileid`
  - `visibleRecordtypes`
  - `isActive`（固定 `true`）
  - `isDefault`（第一条为 `true`）
  - `layoutid`（默认取 layouts 第一条 id）
  - `profile.profilename`

---

## 5) 获取编辑回显信息

- 方法：`POST`
- 路径：`/api/recordType/editRecordType`
- Content-Type：`application/json`

### 请求参数

- `id`（string，必填）：记录类型 ID
- `objid`（string，必填）：对象 ID

### 请求体

```json
{
  "id": "rt001",
  "objid": "obj001"
}
```

### 返回结构

- `data`（object）：编辑页完整回显数据

---

## 6) 保存编辑

- 方法：`POST`
- 路径：`/api/recordType/editSave`
- Content-Type：`application/json`

### 请求参数

- 直接提交编辑回显对象（需包含 `id`）
- 业务上可修改字段：
  - `name`
  - `description`
  - `apiCode`
  - `isenable`

---

## 7) 删除前校验

- 方法：`POST`
- 路径：`/api/recordType/validDelete`
- Content-Type：`application/json`

### 请求参数

- `id`（string，必填）：待删除记录类型 ID
- `objid`（string，必填）：对象 ID

### 请求体

```json
{
  "id": "rt001",
  "objid": "obj001"
}
```

### 执行链路

1. 先调用 `/api/recordType/editRecordType` 获取当前记录类型；
2. 若当前启用，则先调用 `/api/recordType/editSave` 自动禁用；
3. 再调用 `/api/recordType/validDelete` 获取可替换记录类型。

### 返回结构（模块输出口径）

- `obj`：待删除记录类型信息
- `recordtypes`：可替换目标列表（含 `"--无--"`）

---

## 8) 删除记录类型

- 方法：`POST`
- 路径：`/api/recordType/deleteObj`
- Content-Type：`application/json`

### 请求参数

- `id`（string，必填）：待删除记录类型 ID
- `objid`（string，必填）：对象 ID
- `replaceId`（string，必填）：替换记录类型 ID；不替换传空字符串 `""`

### 请求体

```json
{
  "id": "rt001",
  "objid": "obj001",
  "replaceId": "rt002"
}
```
