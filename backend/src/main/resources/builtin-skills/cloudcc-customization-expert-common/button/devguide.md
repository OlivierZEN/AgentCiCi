# CloudCC 按钮与链接开发指南

## 1. 功能范围

按钮模块支持以下能力：

- 查询对象下标准按钮与自定义按钮列表
- 创建自定义按钮
- 更新自定义按钮
- 删除自定义按钮（仅自定义按钮）

## 2. 按钮类型与行为

### 2.1 显示类型

- `detailBtn`：详情页按钮
- `listBtn`：列表按钮

### 2.2 事件类型

- `template`：PC 端脚本按钮
- `lightning-script`：PC + 移动端脚本按钮
- `lightning-url`：PC + 移动端 URL 按钮
- `url`：PC 端 URL 按钮

事件类型到服务端字段映射：

- `template` -> `lightning`
- `lightning-script` -> `lightning-script`
- `lightning-url` -> `lightning-url`
- `url` -> `URL`

## 3. 默认值与约束

- `name` 未传时默认等于 `label`
- `btnType` 默认 `detailBtn`
- `eventType` 默认 `template`
- `behavior` 固定 `self`
- `category` 固定 `CustomButton`
- `visible` 固定 `"1"`

URL 类型约束：

- 当事件为 `url` 或 `lightning-url` 时，如果传入 URL，必须以 `http://` 或 `https://` 开头

脚本类型默认值：

- `template`/`lightning-script` 默认脚本：`alert('hello world')`
- 若传入普通文本会被包装为 `alert("...")`

URL 类型默认值：

- `url`/`lightning-url` 默认：`www.cloudcc.com`

## 4. 移动端页面自动绑定（lightning-url）

当事件类型为 `lightning-url` 时，会额外请求自定义页面列表，并自动使用第一条页面：

- 生成 `mobileurl`：`__UNI__110007E/#/pages/index/index?pageApi={pageApi}`
- 设置 `custompageId`：页面 ID

如果未获取到页面列表，则 `mobileurl` 为空，不阻断按钮保存。

## 5. 错误处理原则

- 必填参数缺失：直接返回错误
- 事件类型不支持：直接返回错误
- URL 格式非法：直接返回错误
- 接口失败：优先使用 `returnInfo`，其次 `message`
