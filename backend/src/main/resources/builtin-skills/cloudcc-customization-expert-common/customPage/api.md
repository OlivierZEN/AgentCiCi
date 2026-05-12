# customPage API 文档

本文档描述 `customPage` 模块的远程 API 能力，仅保留接口视角，不包含 CLI 说明。

## 通用约定

- 默认域名：`https://developer.apis.cloudcc.cn`
- 服务前缀：`{baseUrl}{devSvcDispatch}`，其中 `devSvcDispatch` 默认 `/devconsole`
- 鉴权头（所有接口共用）：
  - `appType: lightning-devconsole`
  - `accessToken: <token>`
  - `source: lightning-devconsole`
  - `version: public`
- 成功判定：`returnCode == 200`
- 失败信息：优先 `returnInfo`，否则 `Unknown error`

## 1) 查询自定义页面列表

- 方法：`POST`
- 路径：`/custom/pc/1.0/post/pageCustomPage`
- Content-Type：`application/json`

### 请求参数

- `pageNo`（number，可选）：页码，默认 `1`。
- `pageSize`（number，可选）：每页数量，默认 `20`。
- `condition`（object，固定结构）：
  - `pageLabel`（string）：页面名称筛选
  - `dtBegin`（string）：开始时间筛选
  - `dtEnd`（string）：结束时间筛选
  - `pageApi`（string）：页面 API 名筛选

### 请求体示例

```json
{
  "pageNo": 1,
  "pageSize": 20,
  "condition": {
    "pageLabel": "",
    "dtBegin": "",
    "dtEnd": "",
    "pageApi": ""
  }
}
```

### 返回说明

- `data.records` 或 `data.list`：页面列表
- `data.total`：总数（若缺失，模块会按列表长度兜底）

---

## 2) 新建自定义页面

- 方法：`POST`
- 路径：`/custom/pc/1.0/post/insertCustomPage`
- Content-Type：`application/json`

### 请求参数（业务入参）

- `pageLabel`（string，必填）：页面名称
- `pageApi`（string，必填）：页面 API 名
- `pluginIdentifier`（string，必填）：组件标识，可传：
  - 组件 ID
  - `compLabel`
  - `compUniName`

### 请求体字段（完整）

- `id`（string）：新建时传空字符串 `""`
- `pageLabel`（string）：页面名称
- `pageApi`（string）：页面 API 名
- `pageContent`（string）：JSON 字符串，内容为组件渲染配置数组
- `orgId`（string）：组织 ID（优先组件 `orgId`，其次当前配置 `orgId`）
- `compList`（array）：页面组件列表，元素结构：
  - `id`（string）：组件 ID
  - `compUniName`（string）：组件唯一名
- `canvasStyleData`（string）：画布配置 JSON 字符串
- `isTemplate`（number）：固定 `0`

### 请求体示例

```json
{
  "id": "",
  "pageLabel": "客户门户首页",
  "pageApi": "customer_portal_home",
  "pageContent": "[{\"id\":\"abc123\",\"comId\":\"cmp001\",\"name\":\"MyComp\",\"componentInfo\":{\"component\":\"MyComp\",\"compName\":\"我的组件\",\"compDesc\":\"\"},\"propObj\":{\"id\":\"abc123\",\"key\":\"x1y2\",\"pageApi\":\"customer_portal_home\"}}]",
  "orgId": "org001",
  "compList": [
    {
      "id": "cmp001",
      "compUniName": "MyComp"
    }
  ],
  "canvasStyleData": "{\"width\":100,\"height\":100,\"scale\":100,\"unit\":\"%\",\"pageApi\":\"customer_portal_home\"}",
  "isTemplate": 0
}
```

### 返回说明

- 成功：`returnCode == 200`
- 返回 ID：`data.id`（若存在）

---

## 3) 更新自定义页面

- 方法：`POST`
- 路径：`/custom/pc/1.0/post/insertCustomPage`
- Content-Type：`application/json`

> 更新与新建共用同一保存接口，区别是更新时 `id` 必填并传已有页面 ID。

### 请求参数（业务入参）

- `id`（string，必填）：页面 ID
- `pageLabel`（string，必填）：页面名称
- `pageApi`（string，必填）：页面 API 名
- `pluginIdentifier`（string，必填）：组件标识（ID / `compLabel` / `compUniName`）

### 请求体字段

字段与“新建自定义页面”一致，仅 `id` 改为目标页面 ID。

### 返回说明

- 成功：`returnCode == 200`
- 失败：`returnCode != 200`，错误信息取 `returnInfo`

---

## 4) 删除自定义页面

- 方法：`POST`
- 路径：`/custom/pc/1.0/post/deleteCustomPage`
- Content-Type：`application/json`

### 请求参数

- `id`（string，必填）：页面 ID

### 请求体

```json
{
  "id": "page001"
}
```

### 返回说明

- 成功：`returnCode == 200`
- 失败：`returnCode != 200`，错误信息取 `returnInfo`

---

## 5) 依赖接口（组件来源）

创建与更新页面前，会先查询自定义组件列表并按标识匹配目标组件。

- 方法：`POST`
- 路径：`/custom/pc/1.0/post/listCustomComp`
- Content-Type：`application/json`

### 请求体

```json
{
  "orgId": "org001"
}
```

### 返回关键结构

- 组件分组对象中的 `label.dev.bizType.custom`（array）作为自定义组件列表
- 每个组件项至少使用字段：
  - `id`
  - `compLabel`
  - `compUniName`
  - `vueData`
  - `orgId`
