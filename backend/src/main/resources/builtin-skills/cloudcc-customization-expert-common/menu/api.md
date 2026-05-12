# menu API 文档

本文档描述 `menu` 模块远程 API，仅保留接口视角。

## 通用约定

- 服务：`setupSvc`
- 鉴权：请求头 `accessToken`
- 成功标识：`result === true`
- 失败信息：优先 `returnInfo`，其次 `message`

## 1) 查询菜单列表

- 方法：`POST`
- 路径：`/api/customTab/queryTabList`
- Content-Type：`application/json`

### 请求参数

- 条件对象（object，可选），默认 `{}`。

### 返回结构

接口返回兼容三种结构：

- `data` 直接是数组
- `data.list` 为数组
- `data` 为分组对象：`botlist/objectlist/objlist/pagelist/scriptlist/...`

分组对象场景下，模块会将各 `*list` 拍平，并添加：

- `__group`（string）：来源分组名

---

## 2) 创建菜单（统一入口）

- 方法：`POST`
- 路径：`/api/customTab/tabSetDone`
- Content-Type：`application/json`

支持类型：`object`、`page`、`script`、`site`。

### 公共请求字段

- `ed`：固定 `"ed"`
- `visited_0`：固定 `"1"`
- `visited_1`：固定 `"1"`
- `visited_2`：固定 `"1"`
- `allOrSome`：固定 `"some"`
- `type`：菜单类型
- `tabName`：菜单显示名称
- `tabStyle`：PC 图标（默认 `cloudtab145`）
- `mobileimg`：移动端图标（默认 `cloudcc01`）
- `cloudccservicetab`：服务图标（默认 `cloudccservicetab_1`）
- `profileIds`：角色可见列表，格式 `["<profileId>_show", ...]`
- `remark`：固定 `"auto created by cloudcc-cli ai agent"`

`profileIds` 来源依赖接口：`brief/get`（角色列表）。

### 2.1 object 菜单

- 额外字段：
  - `p1`（string，必填）：对象 ID

### 2.2 page 菜单

- 额外字段：
  - `pageType`：固定 `"customPage"`
  - `lightningPage`（string，必填）：页面 API，格式 `{pageApi}#lightning`
  - `mobileurl`（string，可选）
  - `pname`（string，必填）：菜单内部标识

### 2.3 script 菜单

- 额外字段：
  - `pname`（string，必填）：脚本菜单标识
  - `pname_origin`（object）：脚本名称字段元信息
  - `functioncode`（string，必填）：脚本内容，默认 `console.log("hello")`
  - `functioncode_origin`（object）：脚本内容字段元信息

### 2.4 site 菜单

- 额外字段：
  - `p1`（string，必填）：站点 ID
  - `p1_origin`（object）：站点选择字段元信息

---

## 3) 更新 page 菜单

## 3.1 读取待更新详情

- 方法：`POST`
- 路径：`/api/customTab/updatetab`
- Content-Type：`application/json`

### 请求体

```json
{
  "id": "tab001",
  "type": "page"
}
```

### 返回用途

- 提取可编辑基础字段：
  - `tabName`
  - `pname`
  - `tabStyle`
  - `mobileimg`
  - `cloudccservicetab`
  - `p3`
  - `mobileurl`
  - `isopenfieldservice`

## 3.2 提交更新

- 方法：`POST`
- 路径：`/api/customTab/updatesavetab`
- Content-Type：`application/json`

### 请求参数

- `id`（string，必填）：菜单 ID
- `type`（string，固定）：`"page"`
- `lightningPage`（string，可选）：默认由 `pageApi` 自动补 `#lightning`
- 可覆盖字段（可选）：
  - `tabName`
  - `pname`
  - `tabStyle`
  - `mobileimg`
  - `cloudccservicetab`
  - `mobileurl`
  - `p3`
  - `isopenfieldservice`

### 约束

- `pname` 校验规则：
  - 非空
  - 长度 `2~20`
  - 正则 `^[a-zA-Z_]([a-zA-Z0-9_]+)?$`
- `lightningPage` 与 `mobileurl` 至少一个非空
- 成功判定：满足任一条件
  - 返回体为 `null`
  - `result === true`
  - `returnCode === "1"`

---

## 4) 删除菜单

- 方法：`POST`
- 路径：`/api/customTab/deleteTab`
- Content-Type：`application/json`

### 请求参数

- `id`（string，必填）：菜单 ID

### 请求体

```json
{
  "id": "tab001"
}
```

---

## 5) 依赖规则：pname 唯一性校验

创建 `page/script` 菜单时会执行 pname 校验（逻辑在 `validator.js`）：

- 长度：`1~50`
- 以字母开头
- 仅字母/数字/下划线
- 不含中文
- 通过查询现有菜单列表检查唯一性：
  - 同名且 `type !== currentType` 视为冲突
