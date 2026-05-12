# classes API 文档

本文档描述 `classes` 模块接口能力，聚焦模块 JS 中实际调用的服务端 API 与输入输出结构，不包含 CLI 用法说明。

## 通用约定

- 服务：`setupSvc`
- 鉴权：请求头 `accessToken`
- 接口前缀：`{setupSvc}/api/ccfag`
- 成功标识：响应体 `result === true`
- 失败信息：优先 `returnInfo`，其次 `message`

## 1) 查询自定义类列表

- 方法：`POST`
- 路径：`/api/ccfag/list`
- Content-Type：`application/json`

### 请求参数

- `shownum`（string|number，可选）：每页数量，默认 `"2000"`。
- `showpage`（string|number，可选）：页码，默认 `"1"`。
- `fid`（string，可选）：文件夹 ID，默认 `""`。
- `sname`（string，可选）：类名模糊查询关键字，默认 `""`。
- `rptcond`（string，可选）：扩展筛选条件，默认 `""`。
- `rptorder`（string，可选）：排序规则，默认 `""`。

### 请求体示例

```json
{
  "shownum": "2000",
  "showpage": "1",
  "fid": "",
  "sname": "",
  "rptcond": "",
  "rptorder": ""
}
```

### 返回结构

- `data.list[]`（array）：类列表
  - `id`（string）：类 ID
  - `name`（string）：类名
  - `apiname`（string）：类 API 名

---

## 2) 查询自定义类详情

- 方法：`POST`
- 路径：`/api/ccfag/detail`
- Content-Type：`application/json`

### 请求参数

- `id`（string，必填）：类 ID。

### 请求体

```json
{
  "id": "wgdxxxxxxxxxxxx"
}
```

### 返回结构

- `data.trigger`（object）：类详情对象（模块直接透传）
- 关键字段（按模块使用）：
  - `id`（string）：类 ID
  - `name`（string）：类名
  - `source`（string）：Java 业务源码

---

## 3) 保存（发布）自定义类

- 方法：`POST`
- 路径：`/api/ccfag/save`
- Content-Type：`application/json`

### 请求参数

- `id`（string，可选）：类 ID；为空表示新增发布，非空表示更新发布。
- `name`（string，必填）：类名。
- `source`（string，必填）：源码内容（URL 编码后的源码字符串）。
- `version`（string，必填）：扩展版本，默认 `"2"`。
- `folderId`（string，必填）：目录 ID，当前实现固定 `"wgd"`。

### 请求体示例

```json
{
  "id": "",
  "name": "OrderSyncService",
  "source": "%2F%2F%20SOURCE...",
  "version": "2",
  "folderId": "wgd"
}
```

### 返回说明

- 首次发布成功时，`data` 为新建类 ID（用于写回本地配置）
- 更新发布成功时，`data` 由服务端返回，模块仅按 `result` 判定成功

---

## 4) 删除自定义类

- 方法：`POST`
- 路径：`/api/ccfag/delete`
- Content-Type：`application/json`

### 请求参数

- `id`（string，必填）：类 ID。

### 请求体

```json
{
  "id": "wgdxxxxxxxxxxxx"
}
```

### 返回说明

- 成功：`result === true`
- 失败：`result !== true`，错误取 `returnInfo`/`message`

