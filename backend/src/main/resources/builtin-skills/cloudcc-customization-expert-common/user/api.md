# user API 文档

本文档描述 `user` 模块远程 API，仅保留接口视角。

## 通用约定

- 服务：`setupSvc`
- 鉴权：请求头 `accessToken`
- 成功标识：`result === true`
- 失败信息：优先 `returnInfo`，其次 `message`

## 1) 查询用户视图列表（依赖）

- 方法：`POST`
- 路径：`/api/view/list/getViewList`
- Content-Type：`application/json`

### 请求参数

- `objId`（string，固定）：`"user"`

### 请求体

```json
{
  "objId": "user"
}
```

### 用途

- 查询用户列表前，若未指定 `viewId`，先通过此接口自动选择“全部/All”视图，找不到则使用第一条视图。

---

## 2) 查询用户列表

- 方法：`POST`
- 路径：`/api/usermange/queryUserList`
- Content-Type：`application/json`

### 请求参数

- 默认基础参数：
  - `start`：`0`
  - `limit`：`30`
  - `keyword`：`""`
- 可覆盖参数（来自条件对象）：
  - `viewId`
  - `keyword`
  - `start`
  - `limit`
  - 其他平台支持的筛选参数

### 请求体示例

```json
{
  "start": 0,
  "limit": 30,
  "keyword": "",
  "viewId": "view001"
}
```

### 返回结构

兼容三种结构：

- `data` 直接数组
- `data.list` 数组
- `data` 分组对象（多个 `*list`）

分组对象场景会拍平并追加：

- `__group`（string）：来源分组名

---

## 3) 查看用户详情

- 方法：`POST`
- 路径：`/api/usermange/viewUser`
- Content-Type：`application/json`

### 请求参数

- `userId`（string，必填）：用户 ID

### 请求体

```json
{
  "userId": "005xxxxxxxxxxxx"
}
```

### 返回结构

- `data`（object）：用户详情对象

---

## 4) 获取用户创建字段配置

- 方法：`POST`
- 路径：`/api/usermange/addUserQuery`
- Content-Type：`application/json`

### 请求体

```json
{}
```

### 返回结构

- `data.fieldOrder`（array，可选）：分区字段定义
- `data.fieldReltaion`（array，可选）：字段关系定义

### 用途

- 创建用户时动态构建可编辑字段（包含必填、只读、默认值、类型等）。

---

## 5) 保存新用户

- 方法：`POST`
- 路径：`/api/usermange/saveUser`
- Content-Type：`application/json`

### 请求参数

- `dataJson`（string，必填）：用户对象 JSON 字符串

### 常见用户字段

- `name`
- `loginName` / `loginname`
- `email`
- `profileId`
- `isusing`
- 以及 `addUserQuery` 返回中允许编辑的扩展字段

### 请求体示例

```json
{
  "dataJson": "{\"name\":\"张三\",\"loginName\":\"zhangsan@example.com\",\"profileId\":\"aaa001\",\"isusing\":\"true\"}"
}
```

---

## 6) 更新用户

- 方法：`POST`
- 路径：`/api/usermange/editandsave`
- Content-Type：`application/json`

### 请求参数

- `dataJson`（string，必填）：用户对象 JSON 字符串

### 关键校验字段

- `id`（必填）
- `loginName`（必填）

### 请求体示例

```json
{
  "dataJson": "{\"id\":\"005xxxxxxxxxxxx\",\"loginName\":\"zhangsan@example.com\",\"isusing\":\"false\"}"
}
```

---

## 7) 删除用户

- 方法：`POST`
- 路径：`/api/user/deleteUser`
- Content-Type：`application/json`

### 请求参数

- `id`（string，必填）：用户 ID

### 请求体

```json
{
  "id": "005xxxxxxxxxxxx"
}
```

---

## 8) 创建用户的查找字段依赖接口

创建用户时，针对查找字段会按 `lookupObj` 调用以下接口加载候选项：

- 角色（`lookupObj = "role"`）：
  - `POST /api/role/queryRoleList`
  - 请求体：`{ "start": 0, "limit": 1000 }`
- 简档（`lookupObj = "aaa"`）：
  - `POST /api/profile/query`
  - 请求体：`{ "start": 0, "limit": 1000 }`
- 用户（`lookupObj = "005"`）：
  - `POST /api/usermange/queryUserList`
  - 请求体：`{ "start": 0, "limit": 1000, "keyword": "" }`
- 客户（`lookupObj = "003"`）：
  - `POST /api/account/query`
  - 请求体：`{ "start": 0, "limit": 1000 }`
