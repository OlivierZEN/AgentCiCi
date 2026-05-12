# permission API 文档

本文档描述 `permission` 模块远程 API，仅保留接口视角。

## 通用约定

- 服务：`setupSvc`
- 鉴权：请求头 `accessToken`
- 成功标识：`result === true`
- 失败信息：优先 `returnInfo`，其次 `message`

## 1) 查询权限集视图信息

## 1.1 查询权限集视图配置

- 方法：`POST`
- 路径：`/api/permissionGroup/queryPermsetsList`
- 请求体：`{}`

用途：

- 获取 `viewId`
- 若无 `viewId`，取 `data.viewList[0].id`

返回关键字段：

- `data.viewId`（string，可选）：默认视图 ID
- `data.viewList[]`（array，可选）：视图列表
  - `id`（string）：视图 ID
  - `label`（string）：视图名称

## 1.2 查询权限集列表

- 方法：`POST`
- 路径：`/api/permissionGroup/listAJAX`
- Content-Type：`application/json`

### 请求参数

- `viewId`（string，必填）：视图 ID
- `page`（number，可选）：页码，默认 `1`
- `pageSize`（number，可选）：每页，默认 `100000`
- `searchKeyWord`（string，可选）：关键字

### 请求体示例

```json
{
  "viewId": "view001",
  "page": 1,
  "pageSize": 100000,
  "searchKeyWord": ""
}
```

### 返回结构

- `data.list[]`：权限集列表，常用字段：
  - `id`
  - `name`
  - `licence`
  - `sysadmin`

---

## 2) 查询权限集已分配用户

- 方法：`POST`
- 路径：`/api/permissionGroup/queryUserlistBypermsetsid`
- Content-Type：`application/json`

### 请求参数

- `id`（string，必填）：权限集 ID

### 请求体

```json
{
  "id": "permset001"
}
```

### 返回结构

- `data.userlist[]`：已分配用户
- `data.permsets`：权限集信息

---

## 3) 查询用户列表（用于可分配用户）

- 方法：`POST`
- 路径：`/api/usermange/queryUserList`
- Content-Type：`application/json`

### 请求参数

- `start`（number，固定）：`0`
- `limit`（number，固定）：`10000`
- `viewId`（string，必填）
- `keyword`（string，可选）
- `notisusering`（string，固定）：`"true"`

### 请求体示例

```json
{
  "start": 0,
  "limit": 10000,
  "viewId": "view001",
  "keyword": "",
  "notisusering": "true"
}
```

---

## 4) 添加权限集用户分配

- 方法：`POST`
- 路径：`/api/permissionGroup/addUsersetup`
- Content-Type：`application/json`

### 请求参数

- `id`（string，必填）：权限集 ID
- `ids`（string，必填）：用户 ID 列表，逗号分隔

### 请求体

```json
{
  "id": "permset001",
  "ids": "user001,user002"
}
```

---

## 5) 删除权限集用户分配

- 方法：`POST`
- 路径：`/api/permissionGroup/deleteUsersetup`
- Content-Type：`application/json`

### 请求参数

- `id`（string，必填）：权限集 ID
- `ids`（string，必填）：用户 ID 列表，逗号分隔

### 请求体

```json
{
  "id": "permset001",
  "ids": "user001,user002"
}
```

---

## 6) 依赖接口：用户视图信息

- 方法：`POST`
- 路径：`/api/usermange/queryUser`
- 请求体：`{}`

用途：

- 获取 `viewId`
- 若无 `viewId`，取 `data.viewList[0].id`

返回关键字段：

- `data.viewId`（string，可选）：默认用户视图 ID
- `data.viewList[]`（array，可选）：用户视图列表
