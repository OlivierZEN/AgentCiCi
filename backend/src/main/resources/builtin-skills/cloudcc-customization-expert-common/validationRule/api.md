# validationRule API 文档

本文档描述 `validationRule` 模块远程 API，仅保留接口视角。

## 通用约定

- 服务：`setupSvc`
- 鉴权：请求头 `accessToken`
- 成功标识：`result === true`
- 失败信息：优先 `returnInfo`，其次 `message`

## 1) 查询验证规则列表

- 方法：`POST`
- 路径：`/api/validateRule/queryByPrefix`
- Content-Type：`application/json`

### 请求参数

- `prefix`（string，必填）：对象前缀（例如 `b00`）

### 请求体

```json
{
  "prefix": "b00"
}
```

### 返回结构

- `data.list`（array）：验证规则列表
- `data.objid`（string，可选）：对象 ID（创建规则时会用到）

---

## 2) 创建验证规则

创建流程由两个接口组成：

## 2.1 按前缀查询对象信息

- 方法：`POST`
- 路径：`/api/validateRule/queryByPrefix`
- 用途：获取 `objid`

## 2.2 保存验证规则

- 方法：`POST`
- 路径：`/api/validateRule/save`
- Content-Type：`application/json`

### 请求参数

- `objid`（string，必填）：对象 ID
- `validate`（object，必填）：
  - `name`（string，必填）：规则名称
  - `description`（string，可选）：描述
  - `isactive`（string，必填）：是否启用，默认 `"true"`
  - `functionCode`（string，必填）：规则表达式
  - `msgLocation`（string，必填）：错误显示位置，默认 `"top"`
  - `errorMessage`（string，必填）：错误提示
  - `objId`（string，必填）：对象 ID

### 请求体示例

```json
{
  "objid": "obj001",
  "validate": {
    "name": "数量校验",
    "description": "",
    "isactive": "true",
    "functionCode": "Batch_Size__c__f==5",
    "msgLocation": "top",
    "errorMessage": "数量必须是5",
    "objId": "obj001"
  }
}
```

### 成功判定

满足任一条件视为成功：

- `result === true`
- `result === "true"`
- `returnCode === "1"`

---

## 3) 删除验证规则

- 方法：`POST`
- 路径：`/api/validateRule/delete`
- Content-Type：`application/json`

### 请求参数

- `id`（string，必填）：规则 ID

### 请求体

```json
{
  "id": "rule001"
}
```
