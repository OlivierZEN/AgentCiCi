# pagelayout API 文档

本文档描述 `pagelayout` 模块远程 API，仅保留接口视角。

## 通用约定

- 服务：`setupSvc`
- 鉴权：请求头 `accessToken`
- 成功标识：`result === true`
- 失败信息：优先 `returnInfo`，其次 `message`

## 1) 查询页面布局列表

- 方法：`POST`
- 路径：`/api/layout/queryPageLayout`
- Content-Type：`application/json`

### 请求参数

- `prefix`（string，必填）：对象前缀

### 请求体

```json
{
  "prefix": "001"
}
```

### 返回结构

- `data.layouts`（array）：页面布局列表

---

## 2) 查询对象可复制布局（创建时依赖）

- 方法：`POST`
- 路径：`/api/layout/newpage`
- Content-Type：`application/json`

### 请求参数

- `objid`（string，必填）：对象 ID

### 请求体

```json
{
  "objid": "obj001"
}
```

### 返回结构

- `data`（array）：可用布局列表
- 当创建未传 `sourceLayoutId` 时，模块默认取 `data[0].id` 作为复制源

---

## 3) 创建页面布局（复制）

- 方法：`POST`
- 路径：`/api/layout/cloneLayout`
- Content-Type：`application/json`

### 请求参数

- `layoutId`（string，必填）：源布局 ID
- `layoutName`（string，必填）：新布局名称
- `objid`（string，必填）：对象 ID
- `isCloneDynamic`（string，可选）：是否复制动态布局规则，默认 `"true"`

### 请求体

```json
{
  "layoutId": "layout_source_001",
  "layoutName": "新布局",
  "objid": "obj001",
  "isCloneDynamic": "true"
}
```

---

## 4) 删除页面布局

- 方法：`POST`
- 路径：`/api/layout/deleteButton`
- Content-Type：`application/json`

### 请求参数

- `id`（string，必填）：布局 ID

### 请求体

```json
{
  "id": "layout001"
}
```
