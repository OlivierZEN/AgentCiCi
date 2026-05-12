# view API 文档

本文档描述 `view` 模块远程 API，仅保留接口视角。

## 通用约定

- 服务：`setupSvc`
- 鉴权：请求头 `accessToken`
- 成功判定兼容：
  - `result === true`
  - `returnCode === "1"`
  - `returnCode === "200"`
- 失败信息：优先 `returnInfo`，其次 `message`

## 1) 查询对象视图列表

- 方法：`POST`
- 路径：`/api/view/list/getViewList`
- Content-Type：`application/json`

### 请求参数

- `objId`（string，必填）：对象 ID（例如 `user` 或业务对象 ID）

### 请求体

```json
{
  "objId": "user"
}
```

### 返回结构

- `data`（array）：视图列表
- 常见字段：
  - `id`
  - `label`
  - `name`
  - `viewName`

---

## 2) 保存“全部视图”字段配置

- 方法：`POST`
- 路径：`/api/view/saveFieldSetup`
- Content-Type：`application/json`

### 请求参数

- `objId`（string，必填）：对象 ID
- `viewId`（string，必填）：目标视图 ID（通常为“全部”视图）
- `fieldIds`（string，必填）：字段 ID 列表，逗号分隔

### 请求体

```json
{
  "objId": "obj001",
  "viewId": "view_all_001",
  "fieldIds": "field001,field002,field003"
}
```

### 字段参数说明

- `fieldIds` 输入支持两种形式（模块会归一化为逗号串）：
  - 逗号分隔字符串：`"a,b,c"`
  - JSON 数组：`["a","b","c"]`

---

## 3) “全部视图”ID 自动解析逻辑（更新时依赖）

当调用方未显式传入 `viewId` 时，模块会先调用：

- `POST /api/view/list/getViewList`，请求体 `{ objId }`

并按以下规则选取目标视图：

1. 优先匹配 `label === "全部"`
2. 未匹配到则报错，要求显式传入 `viewId`
