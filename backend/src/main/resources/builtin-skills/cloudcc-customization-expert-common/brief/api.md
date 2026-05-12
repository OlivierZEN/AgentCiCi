# brief API 文档

本文档描述 `brief` 模块远程 API，仅保留接口视角。

## 通用约定

- 服务：`setupSvc`
- 鉴权：请求头 `accessToken`
- 成功标识：`result === true`
- 失败信息：`returnInfo`

## 1) 查询简档摘要列表

- 方法：`POST`
- 路径：`/api/customObject/newPage`
- Content-Type：`application/json`

### 请求参数

无业务参数，固定请求体。

### 请求体

```json
{
  "id": ""
}
```

### 返回结构（模块输出口径）

模块在成功时返回：

- `success`（boolean）：固定 `true`
- `data`（array）：简档列表（由 `response.data.objList` 映射）
- `iscreatperm`（mixed）：来源 `response.data.iscreatperm`
- `profileInfo`（mixed）：来源 `response.data.profileInfoSet`

`data[]` 字段结构：

- `id`（string）：简档 ID
- `name`（string）：简档名称（`profilename`）
- `description`（string）：描述
- `isCustom`（string|boolean）：是否自定义
- `type`（string）：简档类型
- `createdate`（string）：创建时间
- `lastmodifydate`（string）：最后修改时间

### 失败返回（模块输出口径）

- `success`（boolean）：`false`
- `message`（string）：`returnInfo`
