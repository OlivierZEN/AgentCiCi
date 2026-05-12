# profile API 文档

本文档描述 `profile` 模块远程 API，仅保留接口视角。

## 通用约定

- 服务：`setupSvc`
- 鉴权：请求头 `accessToken`
- 成功标识：`result === true`
- 失败信息：优先 `returnInfo`，其次 `message`

## 1) 查询简档列表

- 方法：`POST`
- 路径：`/api/profile/listAll`
- Content-Type：`application/json`

### 请求参数

- 条件对象（object，可选），默认 `{}`。

### 返回结构

接口返回兼容三种结构：

- `data.list` 数组
- `data` 直接数组
- `data` 分组对象（多个 `*list`）

分组对象场景下，模块会拍平并追加：

- `__group`（string）：来源分组名

---

## 2) 创建简档（复制现有简档）

创建流程包含 2 次远程调用：

## 2.1 查询可复制来源

- 方法：`POST`
- 路径：`/api/profile/copyProfile`
- 请求体：`{}`

返回关键结构：

- `data.profileList[]`：可复制简档列表

来源选择策略：

- 优先选择名称为“系统管理员”的简档
- 未找到则使用列表第一项

## 2.2 提交创建

- 方法：`POST`
- 路径：`/api/profile/newProfile`
- Content-Type：`application/json`

### 请求参数

- `newProfileName`（string，必填）：新简档名称
- `type`（string，固定）：`""`
- `copyFromId`（string，必填）：复制来源简档 ID

### 请求体

```json
{
  "newProfileName": "销售经理简档",
  "type": "",
  "copyFromId": "profile_source_001"
}
```

---

## 3) 删除简档

- 方法：`POST`
- 路径：`/api/profile/delProfile`
- Content-Type：`application/json`

### 请求参数

- `id`（string，必填）：简档 ID

### 请求体

```json
{
  "id": "profile001"
}
```
