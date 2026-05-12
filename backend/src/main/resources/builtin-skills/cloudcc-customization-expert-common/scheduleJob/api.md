# scheduleJob API 文档

本文档描述 `scheduleJob` 模块远程 API，仅保留接口视角。

## 通用约定

- 服务：`setupSvc`
- 鉴权：请求头 `accessToken`
- 成功标识：`result === true`
- 失败信息：优先 `returnInfo`，其次 `message`

## 1) 查询定时作业列表

- 方法：`POST`
- 路径：`/api/schedulAbleprg/list`
- Content-Type：`application/json`

### 请求参数

- 条件对象（object，可选），默认 `{}`。

常见筛选字段（按实际返回结构与页面行为）：

- `name`（string，可选）：作业名称关键字
- `prgid`（string，可选）：作业程序 ID
- `frequency`（string，可选）：`weekly` / `monthly`
- `startdate`（string，可选）：开始日期
- `enddate`（string，可选）：结束日期

### 返回结构

- `data`（array）：作业列表

列表项常见字段：

- `id`（string）：作业 ID
- `name`（string）：作业名称
- `prgid`（string）：作业程序 ID
- `prgname`（string）：作业程序名称
- `frequency`（string）：频率
- `startdate`（string）：开始日期
- `enddate`（string）：结束日期
- `executetime`（string）：执行时间（展示值）

---

## 2) 查询作业程序列表（下拉数据）

- 方法：`POST`
- 路径：`/api/lookup/getLookupData`
- Content-Type：`application/json`

### 请求参数

- `prefix`（string，固定）：`"ccp"`
- `searchKeyWord`（string，可选）：源码当前固定传 `""`
- `page`（number，固定）：`1`
- `pageSize`（number，固定）：`9999`

### 请求体

```json
{
  "prefix": "ccp",
  "searchKeyWord": "",
  "page": 1,
  "pageSize": 9999
}
```

### 返回结构

- `data.dataList`（array）：作业程序列表（用于选择 `prgid`）

列表项常见字段：

- `id`（string）：作业程序 ID（保存作业时作为 `prgid`）
- `name`（string）：作业程序名称
- `sortfieldsql`（number|string）：排序字段

---

## 3) 查询定时作业详情

- 方法：`POST`
- 路径：`/api/schedulAbleprg/edit`
- Content-Type：`application/json`

### 请求参数

- `id`（string，必填）：作业 ID

### 请求体

```json
{
  "id": "job001"
}
```

### 返回结构

- `data`（object）：作业详情

详情对象常见字段：

- `id`（string）：作业 ID
- `name`（string）：作业名称
- `prgid`（string）：作业程序 ID
- `frequency`（string）：`weekly` / `monthly`
- `weeks`（string）：周频星期列表（逗号分隔）
- `monthtype`（string）：月频类型（`day` / `week`）
- `days`（string）：月频按日
- `weeknum`（string）：月频按周序号
- `mweek`（string）：月频星期值
- `startdate`（string）：开始日期（`yyyy-MM-dd`）
- `enddate`（string）：结束日期（`yyyy-MM-dd`）
- `executetime`（string）：执行时间（小时字符串）

---

## 4) 保存定时作业（新建/编辑）

- 方法：`POST`
- 路径：`/api/schedulAbleprg/save`
- Content-Type：`application/json`

### 请求参数

- 提交完整作业对象（模块直接透传 `body`）

### 必填字段（新建/编辑通用）

- `name`（string，必填）：作业名称
- `prgid`（string，必填）：作业程序 ID（由 `getList` 获取）
- `frequency`（string，必填）：`weekly` / `monthly`
- `startdate`（string，必填）：开始日期，格式 `yyyy-MM-dd`
- `enddate`（string，必填）：结束日期，格式 `yyyy-MM-dd`
- `executetime`（string，必填）：执行小时，取值 `"0"`~`"23"`

### 条件必填字段

- 当 `frequency = "weekly"`：
  - `weeks`（string，必填）：星期列表，逗号分隔（示例：`Mon,Wed,Fri`）
- 当 `frequency = "monthly"`：
  - `monthtype`（string，必填）：`day` / `week`
  - 若 `monthtype = "day"`：`days`（string，必填，`1~31` 或 `last`）
  - 若 `monthtype = "week"`：`weeknum` 与 `mweek`（string，必填）

### 新建/编辑字段差异

- 新建：`id` 传空字符串 `""`
- 编辑：`id` 传已有作业 ID

### 字段归一化规则（保存前）

- `frequency = "monthly"`：
  - 固定清空 `weeks = ""`
  - `monthtype = "week"`：清空 `days = ""`，保留 `weeknum`、`mweek`
  - `monthtype = "day"`：保留 `days`，清空 `weeknum = ""`、`mweek = ""`
- `frequency = "weekly"`：
  - 保留 `weeks`
  - 固定清空 `monthtype = ""`、`days = ""`、`weeknum = ""`、`mweek = ""`

### 请求体示例（周频）

```json
{
  "id": "",
  "name": "每周同步作业",
  "prgid": "ccp202689F3015BpWah8",
  "frequency": "weekly",
  "weeks": "Mon,Tue,Wed,Thu,Fri,Sat,Sun",
  "monthtype": "",
  "days": "",
  "weeknum": "",
  "mweek": "",
  "startdate": "2026-04-16",
  "enddate": "2037-12-31",
  "executetime": "10"
}
```

### 请求体示例（月频-按日）

```json
{
  "id": "",
  "name": "每月对账作业",
  "prgid": "ccp202689F3015BpWah8",
  "frequency": "monthly",
  "weeks": "",
  "monthtype": "day",
  "days": "last",
  "weeknum": "",
  "mweek": "",
  "startdate": "2026-05-01",
  "enddate": "2037-12-31",
  "executetime": "2"
}
```

---

## 5) 删除定时作业

- 方法：`POST`
- 路径：`/api/schedulAbleprg/delete`
- Content-Type：`application/json`

### 请求参数

- `id`（string，必填）：作业 ID

### 请求体

```json
{
  "id": "job001"
}
```
