# triggers API 文档

本文档描述 `triggers` 模块远程 API，仅保留接口视角。

## 通用约定

- 服务：`setupSvc`
- 鉴权：请求头 `accessToken`
- 成功标识：`result === true`
- 失败信息：优先 `returnInfo`，其次 `message`

## 1) 查询触发器列表

- 方法：`POST`
- 路径：`/api/triggerSetup/getTriggerByCondition`
- Content-Type：`application/json`

### 请求参数

- 查询对象（object，必填），支持字段：
  - `shownum`：每页条数
  - `showpage`：页码
  - `sname`：触发器名称关键字
  - `objId`：作用对象 ID（对象筛选优先使用）
  - `rptcond`：扩展筛选条件
  - `rptorder`：排序条件

### 请求体示例

```json
{
  "shownum": 2000,
  "showpage": 1,
  "sname": "",
  "objId": "",
  "rptcond": "",
  "rptorder": ""
}
```

### 返回结构（模块输出口径）

- 从 `data.list[]` 映射精简字段：
  - `apiname`
  - `id`
  - `name`

---

## 2) 发布触发器

- 方法：`POST`
- 路径：`/api/triggerSetup/saveTrigger`
- Content-Type：`application/json`

### 请求参数

- `id`（string，可选）：已发布触发器 ID；为空表示新建发布
- `apiname`（string，必填）：触发器 API 名
- `isactive`（boolean，必填）：是否启用
- `targetObjectId`（string，必填）：目标对象 ID
- `triggerTime`（string，必填）：触发时机
- `name`（string，必填）：触发器名称
- `version`（string，必填）：版本，默认 `"2"`
- `triggerSource`（string，必填）：URL 编码后的触发器源码片段
- `folderId`（string，必填）：固定 `"wgd"`

### 请求体示例

```json
{
  "id": "",
  "apiname": "AccountAfterUpdateTrigger",
  "isactive": true,
  "targetObjectId": "obj001",
  "triggerTime": "after update",
  "name": "AccountAfterUpdateTrigger",
  "version": "2",
  "triggerSource": "%2F%2F%20trigger%20source...",
  "folderId": "wgd"
}
```

### 返回说明

- 新建发布成功时，`data.id` 与 `data.apiname` 用于回写配置

---

## 3) 查询触发器详情

- 方法：`POST`
- 路径：`/api/trigger/newobjtrigger`
- Content-Type：`application/json`

### 请求参数

- `id`（string，必填）：触发器 ID

### 请求体

```json
{
  "id": "trg001"
}
```

### 返回结构

- `data.trigger`（object）：触发器详情
- `data.targetObj`（object，可选）：目标对象信息

`data.trigger` 常见字段：

- `id`
- `name`
- `apiname`
- `targetObjectId`
- `triggerTime`
- `isactive`
- `triggerSource`

---

## 4) 删除触发器

- 方法：`POST`
- 路径：`/api/triggerSetup/deleteTrigger`
- Content-Type：`application/json`

### 请求参数

- `id`（string，必填）：触发器 ID

### 请求体

```json
{
  "id": "trg001"
}
```
