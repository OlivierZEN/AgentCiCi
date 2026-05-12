# button API 文档

本文档描述 `button` 模块接口，聚焦 HTTP API，不包含 CLI 说明。

## 通用约定

- 服务：`setupSvc`
- 鉴权：请求头 `accessToken`
- 成功标识：响应体 `result === true`
- 失败信息：优先 `returnInfo`，其次 `message`

## 1) 查询按钮列表

- 方法：`POST`
- 路径：`/api/buttonlink/listButton`
- Content-Type：`application/json`

### 请求参数

- `prefix`（string，必填）：对象前缀（例如 `a75`）。

### 请求体

```json
{
  "prefix": "a75"
}
```

### 返回结构

- `objname`（string）：对象名称。
- `objid`（string）：对象 ID。
- `standbutton`（array）：标准按钮列表。
- `custbutton`（array）：自定义按钮列表。

按钮项字段（`standbutton[]` / `custbutton[]`）：

- `id`（string）：按钮 ID。
- `label`（string）：按钮显示名称。
- `name`（string）：按钮 API 名称。
- `btnType`（string）：按钮显示类型，`detailBtn` / `listBtn`。
- `category`（string）：`StandardButton` / `CustomButton`。
- `event`（string）：事件类型（如 `URL`、`lightning`、`lightning-script`、`lightning-url`）。
- `behavior`（string）：打开方式（当前实现固定为 `self`）。
- `url`（string|null）：URL 类按钮地址。
- `functionCode`（string|null）：脚本或 URL 主内容。
- `mobileurl`（string）：移动端 URL。

### 返回示例

```json
{
  "objname": "测试对象",
  "objid": "202646FC67ACF24D39sG",
  "standbutton": [
    {
      "id": "adc20262A1F4957UmT0l",
      "label": "提交待审批",
      "name": "Submit",
      "btnType": "detailBtn",
      "category": "StandardButton",
      "event": "URL",
      "behavior": "self",
      "url": null,
      "functionCode": null,
      "mobileurl": ""
    }
  ],
  "custbutton": []
}
```

---

## 2) 新建自定义按钮

- 方法：`POST`
- 路径：`/api/buttonlink/saveButton`
- Content-Type：`application/json`

### 请求参数（业务入参）

- `objid`（string，必填）：所属对象 ID。
- `label`（string，必填）：按钮标签。
- `name`（string，可选）：按钮 API 名称；默认等于 `label`。
- `btnType`（string，可选）：`detailBtn` / `listBtn`；默认 `detailBtn`。
- `eventType`（string，可选）：`template` / `lightning-script` / `lightning-url` / `url`；默认 `template`。
- `eventContent`（string，可选）：事件内容（脚本代码或 URL）。

### 事件映射与规则

- `template` -> `event: "lightning"`
- `lightning-script` -> `event: "lightning-script"`
- `lightning-url` -> `event: "lightning-url"`
- `url` -> `event: "URL"`

补充规则：

- `template`：`functionCode` 默认 `alert('hello world')`
- `lightning-script`：`functionCode` 与 `h5FunctionCode` 同时设置，默认 `alert('hello world')`
- `url`：`functionCode` 默认 `www.cloudcc.com`
- `lightning-url`：`functionCode` 默认 `www.cloudcc.com`，并尝试自动生成 `mobileurl` 与 `custompageId`
- 当事件类型为 `url` / `lightning-url` 且传入 `eventContent` 时，必须以 `http://` 或 `https://` 开头

### 请求体字段（完整）

```json
{
  "objid": "202646FC67ACF24D39sG",
  "tpSysButtonVO": {
    "label": "我的按钮",
    "name": "myButton",
    "btnType": "detailBtn",
    "event": "lightning",
    "behavior": "self",
    "menubar": true,
    "functionCode": "alert('hello world')",
    "remark": "",
    "mobileurl": "",
    "h5FunctionCode": "",
    "category": "CustomButton",
    "custompageId": "",
    "iscustom": "",
    "visible": "1"
  }
}
```

### 返回说明

- 成功：`result === true`
- 失败：`result !== true`，错误信息取 `returnInfo`/`message`

---

## 3) 更新自定义按钮

- 方法：`POST`
- 路径：`/api/buttonlink/saveButton`
- Content-Type：`application/json`

### 请求参数（业务入参）

- `id`（string，必填）：按钮 ID。
- `objid`（string，必填）：所属对象 ID。
- `label`（string，必填）：按钮标签。
- `name`（string，可选）：按钮 API 名称；默认等于 `label`。
- `btnType`（string，可选）：`detailBtn` / `listBtn`；默认 `detailBtn`。
- `eventType`（string，可选）：`template` / `lightning-script` / `lightning-url` / `url`；默认 `template`。
- `eventContent`（string，可选）：事件内容（脚本代码或 URL）。

### 请求体字段（完整）

```json
{
  "objid": "202646FC67ACF24D39sG",
  "tpSysButtonVO": {
    "id": "adc202642BBED6BFlu6D",
    "label": "我的按钮",
    "name": "myButton",
    "btnType": "detailBtn",
    "event": "lightning",
    "behavior": "self",
    "menubar": true,
    "functionCode": "alert('hello world')",
    "remark": "",
    "mobileurl": "",
    "h5FunctionCode": "",
    "category": "CustomButton",
    "custompageId": "",
    "iscustom": "",
    "visible": "1"
  }
}
```

### 更新规则

- 与新建共用相同事件映射规则
- `lightning-url` 会尝试自动刷新 `mobileurl` / `custompageId`
- URL 类型校验规则与新建一致

### 返回说明

- 成功：`result === true`
- 失败：`result !== true`，错误信息取 `returnInfo`/`message`

---

## 4) 删除自定义按钮

- 方法：`POST`
- 路径：`/api/buttonlink/deleteButton`
- Content-Type：`application/json`

### 请求参数

- `id`（string，必填）：自定义按钮 ID。

### 请求体

```json
{
  "id": "adc202642BBED6BFlu6D"
}
```

### 返回说明

- 成功：`result === true`
- 失败：`result !== true`，错误信息取 `returnInfo`/`message`

---

## 5) 依赖接口（lightning-url 自动绑定移动页）

当事件类型为 `lightning-url` 时，会额外调用自定义页面列表接口以获取第一条页面用于移动端 URL 拼装。

- 方法：`POST`
- 路径：`/devconsole/custom/pc/1.0/post/pageCustomPage`
- Content-Type：`application/json; charset=utf-8`

### 请求体

```json
{
  "head": {
    "appType": "lightning-setup",
    "appVersion": "0.0.1",
    "accessToken": "<accessToken>",
    "source": "lightning-setup",
    "version": "public"
  },
  "body": {
    "pageNo": 1,
    "pageSize": 2000000,
    "condition": {
      "pageLabel": "",
      "dtBegin": "",
      "dtEnd": ""
    }
  }
}
```

### 返回关键字段

- `data.list[0].id`：用于 `custompageId`
- `data.list[0].pageApi`：用于生成 `mobileurl`
