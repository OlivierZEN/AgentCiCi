# script API 文档

本文档描述 `script` 模块远程 API，仅保留接口视角。

## 通用约定

- 默认域名：`https://developer.apis.cloudcc.cn`
- 鉴权与环境：使用 devconsole 配置（含 `accessToken`、`baseUrl`）
- 成功标识：`result === true`
- 失败信息：优先 `returnInfo`，其次 `message`

## 1) 查询客户端脚本列表

- 方法：`POST`
- 路径：`/devconsole/script/pageClientScript`
- Content-Type：`application/json`

### 请求参数

模块直接透传查询条件对象（`configContent`）。

常见结构：

- `pageSize`（number）
- `pageNo`（number）
- `condition`（object）：
  - `id`
  - `pageLabel`
  - `objName`
  - `scriptName`

### 请求体示例

```json
{
  "pageSize": 20,
  "pageNo": 1,
  "condition": {
    "id": "",
    "pageLabel": "",
    "objName": ""
  }
}
```

### 返回结构（模块输出口径）

- 输出 `data.list[]` 的精简结构：
  - `apiname`（`pageLabel`）
  - `id`
  - `name`（`scriptName`）

---

## 2) 保存客户端脚本（发布）

- 方法：`POST`
- 路径：`/devconsole/script/saveClientScript`
- Content-Type：`application/json`

### 请求参数

- 模块以本地 `config.json` 为基础拼装请求体，并追加：
  - `scriptContent`（string，必填）：脚本函数体
  - `id`（string，可选）：已发布脚本 ID；无 ID 时表示新建发布

### 请求体常见字段（来自脚本配置）

- `scriptName`（string，必填）：脚本名称
- `scriptDesc`（string，可选）：脚本描述
- `objId`（string，必填）：所属对象 ID
- `objName`（string，必填）：所属对象 API 名
- `recordTypeId`（string，可选）：记录类型 ID
- `recordTypeName`（string，可选）：记录类型名称
- `pageId`（string，可选）：页面 ID
- `eventType`（string，可选）：事件类型
- `event`（string，可选）：事件名
- `fieldId`（string，可选）：字段 ID
- `schemetableName`（string，可选）：对象表名
- `usageScenario`（string，可选）：使用场景

### 脚本内容规则

- 本地源码必须存在函数签名：`function main($CCDK, obj){...}`
- 发布时只提取 `main` 的函数体作为 `scriptContent`

### 返回结构

- 新建发布成功时，`data` 返回脚本 ID（用于后续持久化）

---

## 3) 拉取客户端脚本内容

- 方法：`POST`
- 路径：`/devconsole/script/pageClientScript`
- Content-Type：`application/json`

### 请求参数

- `condition.id`（string，必填）：脚本 ID

### 请求体示例

```json
{
  "pageSize": 20,
  "pageNo": 1,
  "condition": {
    "id": "script001",
    "pageLabel": "",
    "objName": ""
  }
}
```

### 返回结构

- `data.list[0].scriptContent`：脚本函数体内容

---

## 4) 按 ID 拉取脚本详情（列表检索）

- 方法：`POST`
- 路径：`/devconsole/script/pageClientScript`
- Content-Type：`application/json`

### 请求参数

- `condition.id`（string，必填）：脚本 ID

### 返回结构

- `data.list[0]`：完整脚本详情对象（常见字段）
  - `id`
  - `scriptName`
  - `objName`
  - `scriptContent`
  - 事件绑定与对象相关配置字段

---

## 5) 删除客户端脚本

- 方法：`POST`
- 路径：`/devconsole/script/deleteClientScript`
- Content-Type：`application/json`

### 请求参数

- `id`（string，必填）：脚本 ID

### 请求体

```json
{
  "id": "script001"
}
```

### 兼容说明

- 删除流程支持先用 `objName/scriptName` 组合查询脚本，再解析出 `id` 调用删除接口。
