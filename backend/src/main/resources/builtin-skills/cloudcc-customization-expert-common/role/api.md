# role API 文档

本文档描述 `role` 模块远程 API，仅保留接口视角。

## 通用约定

- 服务：`setupSvc`
- 鉴权：请求头 `accessToken`
- 成功标识：`result === true`
- 失败信息：优先 `returnInfo`，其次 `message`

## 1) 查询角色列表

- 方法：`POST`
- 路径：`/api/role/queryRole`
- Content-Type：`application/json`

### 请求参数

- 条件对象（object，可选），默认：

```json
{
  "display": "list"
}
```

### 返回结构

接口返回兼容：

- `data` 直接数组
- `data.list` 数组
- `data` 分组对象（多个 `*list`）

分组对象场景会拍平并追加：

- `__group`（string）：来源分组名

---

## 2) 获取可选直属上级列表（创建前依赖）

- 方法：`POST`
- 路径：`/api/role/addRole`
- Content-Type：`application/json`

### 请求参数

- `id`（string，必填）：组织 ID（`orgId`）

### 请求体

```json
{
  "id": "org001"
}
```

### 返回结构

- `data.flist[]`：可选直属上级角色列表

---

## 3) 创建角色

- 方法：`POST`
- 路径：`/api/role/saveRole`
- Content-Type：`application/json`

### 请求参数

- `dataJson`（string，必填）：JSON 字符串，结构：
  - `rolename`（string）：角色名称
  - `parentroleId`（string）：直属上级角色 ID

### 请求体示例

```json
{
  "dataJson": "{\"rolename\":\"销售经理\",\"parentroleId\":\"role_parent_001\"}"
}
```

---

## 4) 删除角色

- 方法：`POST`
- 路径：`/api/role/deleteRole`
- Content-Type：`application/json`

### 请求参数

- `roleid`（string，必填）：角色 ID

### 请求体

```json
{
  "roleid": "role001"
}
```
