# object API 文档

本文档描述 `object` 模块远程 API，仅保留接口视角。

## 通用约定

- 服务：`setupSvc`
- 鉴权：请求头 `accessToken`
- 成功标识：`result === true`
- 失败信息：优先 `returnInfo`，其次 `message`

## 1) 查询对象列表

`get object` 根据 `type` 组合调用不同接口。

### 1.1 标准对象列表

- 方法：`POST`
- 路径：`/api/customObject/standardObjList`
- 请求体：`{}`

返回项关键字段：

- `id`
- `label`
- `objname`
- `objprefix`

### 1.2 自定义对象列表

- 方法：`POST`
- 路径：`/api/customObject/list`
- 请求体：`{}`

返回项关键字段（来自 `data.objList[]`）：

- `id`
- `objLabel`
- `schemetableName`
- `schemetable_name`
- `prefix`

### 1.3 触发器可选对象列表

- 方法：`POST`
- 路径：`/api/trigger/newobjtrigger`
- 请求体：`{}`

返回项关键字段（来自 `data.targetObjList[]`）：

- `id`
- `label`
- `schemetable_name`
- `schemetableName`

### 1.4 `type` 与调用关系

- `type=standard`：仅调 `/standardObjList`
- `type=custom`：仅调 `/list`
- `type=trigger`：仅调 `/newobjtrigger`
- `type=chat`：调 `/standardObjList` + `/list` 并合并
- 其他/空：调 `/standardObjList` + `/list` 并合并

---

## 2) 创建自定义对象

- 方法：`POST`
- 路径：`/api/customObject/saveButton`
- Content-Type：`application/json`

### 请求参数（业务入参）

- `label`（string，必填）：对象显示名称。
- `nameLabel`（string，可选）：对象 API 名；未传时自动按 label 生成 `{slug}_custom_object`。
- `remarkSuffix`（string，必填）：业务功能描述。

### 请求体结构

- `iscreatperm`：固定 `"true"`
- `profileFieldArr`（array）：简档权限数组，结构：
  - `profileid`（string）
  - `permission`（string）
- `nameLabel`（string）：对象 API 名
- `obj`（object）：
  - `label`
  - `schemetableName`
  - `dataType` 固定 `"V"`
  - `showFormat` 固定 `"{yyyy}{mm}{dd}{000}"`
  - `beginIndex` 固定 `"0"`
  - `isquickcreated` 固定 `"false"`
  - `islbs` 固定 `"false"`
  - `isreportcreated` 固定 `"false"`
  - `remark`

### 权限数组生成规则

- 来源：角色列表接口（`brief/get`）
- 第一条角色：`permission = "1,1,1,1,1,1"`
- 其余角色：`permission = "1,1,1,1,0,0"`

---

## 3) 删除自定义对象

- 方法：`POST`
- 路径：`/api/customObject/deleteLogic`
- Content-Type：`application/json`

### 请求参数

- `objid`（string，必填）：对象 ID

### 请求体

```json
{
  "objid": "obj001"
}
```
