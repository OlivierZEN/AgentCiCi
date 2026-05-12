# application API 文档

本文档为 `application` 模块完整 API 说明，仅描述 HTTP 接口本身，不包含 CLI 调用说明。

## 通用约定

- 服务：`setupSvc`
- 鉴权：请求头 `accessToken`
- 接口前缀：`{setupSvc}/api/appProgram`
- 成功返回通常包含：`result: true`
- 常见失败字段：`returnInfo` 或 `message`

## 1) 新增应用

- 方法：`POST`
- 路径：`/api/appProgram/tabSetDone`
- Content-Type：`application/json`

### 请求参数（业务入参）

- `p1`（string，必填）：应用名称。
- `p2`（string，必填）：应用编码（内部名称）。
- `duel1`（string，可选）：菜单 ID 列表，逗号分隔。
  - 默认值：`acf000001`
  - 约束：服务端提交前会强制补齐 `acf000001`。

### 请求体字段（完整）

- `visited_0`（string，固定）：`"1"`。
- `visited_1`（string，固定）：`"1"`。
- `setupid`（string，固定）：`"TabSet"`。
- `apptype`（string，固定）：`"app"`。
- `p1`（string，必填）：应用名称。
- `p2`（string，必填）：应用编码。
- `p3`（string，固定）：`""`。
- `navigationStyle`（string，固定）：`"1"`。
- `jump`（string，固定）：`"1"`。
- `p4`（string，固定）：`""`。
- `appstyle`（number，固定）：`21`。
- `p8`（string，固定）：`"home"`。
- `duel1`（string，必填）：最终菜单 ID 列表。
- `visited_2`（string，固定）：`"1"`。
- `visited_3`（string，固定）：`"1"`。
- `p12`（string，必填）：角色 ID 列表，逗号分隔（由角色列表接口汇总生成）。

### 请求示例

```json
{
  "visited_0": "1",
  "visited_1": "1",
  "setupid": "TabSet",
  "apptype": "app",
  "p1": "销售管理",
  "p2": "SalesApp",
  "p3": "",
  "navigationStyle": "1",
  "jump": "1",
  "p4": "",
  "appstyle": 21,
  "p8": "home",
  "duel1": "acf000001,acf000123",
  "visited_2": "1",
  "visited_3": "1",
  "p12": "00e001,00e002"
}
```

### 返回说明

- 成功判定：`result === true`
- 失败判定：`result !== true`，优先读取 `returnInfo`

---

## 2) 查询应用列表

- 方法：`POST`
- 路径：`/api/appProgram/queryAppList`
- Content-Type：`application/json`

### 请求参数

- 条件对象（object，可选）：不传时默认 `{}`。

### 请求体

- 默认：`{}`
- 传条件时：直接使用条件对象作为 body

### 请求示例

```json
{}
```

```json
{
  "keyword": "Sales",
  "pageNo": 1,
  "pageSize": 20
}
```

### 返回说明

- 成功判定：`result === true`
- 列表提取规则：
  - 若 `data` 是数组，返回 `data`
  - 若 `data.list` 是数组，返回 `data.list`
  - 其他情况返回空数组 `[]`
- 失败判定：抛错信息优先取 `returnInfo`，其次 `message`

---

## 3) 更新应用

- 方法：`POST`
- 路径：`/api/appProgram/modifyApp`
- Content-Type：`multipart/form-data`

### 请求参数（业务入参）

- `appId`（string，必填）：应用 ID。
- `appLabel`（string，必填）：应用显示名称。
- `appName`（string，必填）：应用编码（内部名称）。
- `duel1`（string，可选）：菜单 ID 列表，逗号分隔。
  - 默认值：`acf000001`
  - 约束：会强制补齐 `acf000001`。
- `options`（object，可选）：覆盖请求体默认字段。
  - 同名字段覆盖默认值（展开优先级最高）。

### multipart 字段（完整）

- `file`（file，必填）：即使无上传内容，也必须传空文件占位。
- `fugai`（string，默认）：`"0"`。
- `ismobile`（string，默认）：`"false"`。
- `deftab`（string，默认）：`"home"`。
- `selbgcolor`（string，默认）：`""`。
- `duel0`（string，默认）：`""`（可被 `options.duel0` 覆盖）。
- `duel1`（string，必填）：最终菜单 ID 列表。
- `layouttype`（string，默认）：`""`。
- `hightnum`（string，默认）：`""`。
- `apptype`（string，默认）：`"app"`。
- `appstyle`（string，默认）：`"21"`。
- `mobileimg`（string，默认）：`"cloudcc01"`。
- `selected_in_profile`（string，必填）：角色 ID 列表，逗号分隔。
- `default_for_profile`（string，默认）：`""`。
- `appname`（string，必填）：应用编码。
- `applabel`（string，必填）：应用显示名称。
- `description`（string，默认）：`""`。
- `defaultLaunchTabId`（string，默认）：`"home"`。
- `id`（string，必填）：应用 ID。
- `tabarray`（string，默认）：`""`。
- `parenttab`（string，默认）：`""`。
- `vcpagearray`（string，默认）：`""`。
- `navigationStyle`（string，默认）：`"1"`。
- `...options`（mixed，可选）：动态覆盖字段，最终全部转 string 后提交。

### 请求示例（multipart 业务字段视角）

```json
{
  "fugai": "0",
  "ismobile": "false",
  "deftab": "home",
  "duel0": "",
  "duel1": "acf000001,acf000123",
  "apptype": "app",
  "appstyle": "21",
  "mobileimg": "cloudcc01",
  "selected_in_profile": "00e001,00e002",
  "appname": "SalesApp",
  "applabel": "销售管理",
  "defaultLaunchTabId": "home",
  "id": "app001",
  "navigationStyle": "1"
}
```

### 返回说明

- HTTP 失败：`status` 非 2xx，错误优先取 `returnInfo`/`message`，否则 `HTTP {status}`。
- 业务成功满足任一条件：
  - 返回体为 `null`
  - `result === true`
  - `returnCode === "1"`
- 其余情况视为失败，错误优先取 `returnInfo`/`message`。

---

## 4) 删除应用

- 方法：`POST`
- 路径：`/api/appProgram/deleteApp`
- Content-Type：`application/json`

### 请求参数

- `appId`（string，必填）：应用 ID。

### 请求体

```json
{
  "id": "app001"
}
```

### 返回说明

- 成功判定：`result === true`
- 失败判定：优先读取 `returnInfo`，其次 `message`

---

## 5) 依赖接口（角色数据来源）

Create 与 Update 会先调用角色接口构造角色参数，非 application 主 CRUD，但属于当前 API 链路依赖。

- 方法：`POST`
- 路径：`/api/customObject/newPage`
- 请求体：

```json
{
  "id": ""
}
```

- 返回关键结构：
  - `data.objList[]`：角色列表
  - `data.objList[].id`：角色 ID
- 产物映射：
  - Create：拼成 `p12`
  - Update：拼成 `selected_in_profile`
