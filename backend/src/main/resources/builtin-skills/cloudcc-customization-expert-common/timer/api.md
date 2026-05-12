# timer API 文档

本文档描述 `timer` 模块远程 API，仅保留接口视角。

## 通用约定

- 服务：`setupSvc`
- 鉴权：请求头 `accessToken`
- 成功标识：`result === true`
- 失败信息：优先 `returnInfo`，其次 `message`

## 1) 查询定时类列表

- 方法：`POST`
- 路径：`/api/ccPeak/list`
- Content-Type：`application/json`

### 请求参数

- 查询对象（object，可选），默认：

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

### 返回结构（模块输出口径）

- 从 `data.list[]` 映射精简字段：
  - `apiname`
  - `id`
  - `name`

---

## 2) 发布定时类

- 方法：`POST`
- 路径：`/api/ccPeak/save`
- Content-Type：`application/json`

### 请求参数

- `id`（string，可选）：已发布定时类 ID；为空表示新建发布
- `name`（string，必填）：定时类名称
- `version`（string，必填）：版本，默认 `"2"`
- `source`（string，必填）：URL 编码后的源码片段
- `folderId`（string，必填）：固定 `"wgd"`

### 请求体示例

```json
{
  "id": "",
  "name": "DailySyncTimer",
  "version": "2",
  "source": "%2F%2F%20timer%20source...",
  "folderId": "wgd"
}
```

### 返回说明

- 新建发布成功时，`data` 为新 ID（用于回写配置）

---

## 3) 查询定时类详情

- 方法：`POST`
- 路径：`/api/ccPeak/detail`
- Content-Type：`application/json`

### 请求参数

- `id`（string，必填）：定时类 ID

### 请求体

```json
{
  "id": "ccp001"
}
```

### 返回结构

- `data.trigger`（object）：定时类详情对象
- 常见字段：
  - `id`
  - `name`
  - `source`

---

## 4) 删除定时类

- 方法：`POST`
- 路径：`/api/ccPeak/delete`
- Content-Type：`application/json`

### 请求参数

- `id`（string，必填）：定时类 ID

### 请求体

```json
{
  "id": "ccp001"
}
```
