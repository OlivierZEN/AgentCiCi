# cc-customization-expert-msapi 技能缺口复盘：CloudCC CRM 自定义页面嵌入白页

## 2026-07-11 真实复核结论（2.1.276-msapi）

本轮 TASK-182 再次严格使用技能内置 `tools/bin/cloudcc` 对同一租户做真实复核。原报告中的 customPage 读写、pagecomponent 绑定、安全打包和注入页验证命令已经进入 CLI，核心能力不再需要项目脚本绕行。

已通过：

- `scan msapi . online-highcode` 能读取 pagecomponent、HTML 和 customPage；线上 pagecomponent 为 V10，id `6a503defe4b0a577cbba1f8a`。
- `detail customPage . customer_interaction_workbench` 能回读 V4 页面，引用组件 id 与 V10 pagecomponent id 完全一致，`embedded=true`。
- `package pagecomponent customer-workbench . --dry-run` 只采集页面组件目录和预构建 bundle，明确排除配置、环境文件和 token cache。
- OpenAPI 角色权限写入验证已通过：Task、Opportunity 均完成创建、当前用户回读、删除和总数恢复。

仍需反馈给技能维护方的问题：

### GAP-007：同一组件 ID 被误报 stale_component_reference

执行：

```bash
cloudcc verify injectionPage . customer_interaction_workbench \
  --expected-component component-customer-workbench \
  --stale-policy warning
```

事实：

- `expectedComponentId=6a503defe4b0a577cbba1f8a`
- `actualComponentIds=[6a503defe4b0a577cbba1f8a]`
- pagecomponent 当前版本为 10
- customPage 引用结构不提供版本字段，因此 `actualVersions=[]`

当前结果仍为 `warning/stale_component_reference`。当预期与实际组件 ID 完全一致且 customPage 协议本身没有版本字段时，不应仅因 `actualVersions` 为空判定 stale。建议规则改为：组件 ID 一致时版本缺失记为 `version_unavailable` 信息项；只有 ID 不一致，或运行态快照明确加载旧 CDN 版本时才判 stale。

### GAP-008：OpenAPI 写入返回 ID 的结构需要标准化

真实 `insertWithRoleRight` 返回 ID 位于嵌套结构 `data.ids[].id`。调用方若只解析 `data[].id` 会出现“写入成功但没有 ID”，无法回读和审计。建议 CLI 对 create/update/upsert 统一输出顶层 `recordIds`，兼容字符串化 data、`ids`、`records` 和嵌套 data。

### GAP-009：pageQuery expressions 在目标租户行为不一致

使用文档中的复数参数 `expressions` 按记录 ID 查询返回空结果，而不带条件的权限分页查询可以看到该记录；使用单数 `expression` 又被服务端忽略并返回全量。建议：

- CLI 增加基于权限分页并本地精确匹配的 `getById/verifyReadback` helper。
- 在 doctor 或契约测试中检测租户是否正确执行 expressions，并明确输出 `server_filter_unreliable`。
- 写后回读命令优先使用返回 ID，必要时自动降级为分页精确匹配，禁止把空结果直接解释为写入失败。

### GAP-010：online-highcode 的 script 单域 500 不应污染整体结论

本轮 `script` 的 `/devconsole/script/pageClientScript` 返回 HTTP 500，但 pagecomponent、customPage、HTML 等目标域均通过。CLI 已按域报告，建议再提供 `--domains pagecomponent,customPage,html` 或目标链路预设，使发布门能够区分“目标嵌入链路通过”和“不相关高代码域异常”。

## 文档目的

本文记录 TASK-171「客户互动工作台」在 CloudCC CRM 嵌入上线过程中暴露出的 `cc-customization-expert-msapi` 技能缺口，并给出可落地的技能修复建议、CLI 能力增强设计和回归验收用例。

本文不是为了把本次修复沉淀为一次性绕行方案，而是把临时通过 devconsole/setup API 完成的能力回灌到 `cc-customization-expert-msapi`，让后续 CloudCC 页面组件、自定义页面、CRM 菜单和应用绑定交付都能通过技能内置能力完成。

## 背景

「客户互动工作台」是 AgentCiCi 中运行的 AI 应用，需要反向嵌入 CloudCC CRM。目标入口是 CloudCC CRM 顶部菜单「客户互动工作台」，用户点击后进入：

```text
https://ap6.lightning.cloudcc.cn/#/injectionComponent?page=customer_interaction_workbench&button=Home
```

该入口由以下 CloudCC 侧资产组成：

| 层级 | 资产 | 本次状态 |
| --- | --- | --- |
| pagecomponent | `component-customer-workbench` | 用于在 CRM 自定义页面中承载 AgentCiCi iframe |
| customPage | `customer_interaction_workbench` | CRM 自定义页面，引用 pagecomponent |
| tab/menu | `客户互动工作台` | CRM 顶部菜单 tab，指向 customPage |
| application binding | 8 个 CRM 应用 | 将 tab 加入各应用 selectedTabList |
| profile visibility | 6 个简档 | 允许目标用户看到该 tab |

## 现象

菜单已经可见，但点击后页面主体空白。浏览器地址正确，CloudCC shell 正常渲染，顶部菜单仍显示，但内容区没有工作台内容。

真实 Web 自测结论：

- 通过 CloudCC 账号登录成功。
- 打开 `#/injectionComponent?page=customer_interaction_workbench&button=Home` 可稳定复现白页。
- DOM 中存在 `<component-customer-workbench>` 标签，尺寸约为 `1396x852`，但内部为空。
- 页面加载了远端脚本：

```text
https://res.lightning.cloudcc.cn/customjs/org0720f814430017229/component-customer-workbench-V4.0.js
```

- `window.Vue` 存在，版本为 `2.6.14`。
- `window["component-customer-workbench"]` 存在，且是一个 Vue component object。
- `customElements.get("component-customer-workbench")` 不存在。
- 页面没有 iframe。

## 根因

### 1. customPage 指向旧 pagecomponent

CloudCC `detailCustomPage` 回读显示，customPage `customer_interaction_workbench` 的 `pageContent` 仍指向旧组件：

```json
{
  "name": "component-customer-workbench",
  "comId": "6a4d348fe4b0a577cbba1ebf",
  "embedded": false
}
```

这说明后续发布的新 pagecomponent 并没有自动更新 customPage 里的组件引用。CloudCC 自定义页面不是按 component name 动态绑定最新版本，而是依赖 `pageContent.comId` 和 `compList` 中的组件 id。

### 2. 旧 UMD bundle 没有自动挂载到 CRM 注入容器

旧脚本 `component-customer-workbench-V4.0.js` 的行为是暴露：

```js
window["component-customer-workbench"] = { ...VueComponentOptions }
```

但 CRM 注入页面实际生成的是：

```html
<component-customer-workbench class="component"></component-customer-workbench>
```

这不是标准 Web Component 注册，也没有自动执行 `new Vue(...).$mount(...)`。因此脚本加载成功不等于组件渲染成功，结果就是 DOM 有标签但内容为空。

### 3. 页面配置默认 `embedded=false`

组件源码和旧 bundle 的默认状态是：

```js
embedded: false
```

即使组件被正确挂载，也会先展示占位状态，而不是直接嵌入 AgentCiCi 工作台 iframe。这不符合 CRM 菜单入口的使用预期。

### 4. customPage 保存能力无法通过当前技能闭环完成

本次需要更新 customPage 的 `pageContent`、`compList` 和 `canvasStyleData`，但当前 `cc-customization-expert-msapi` 内置 Go CLI 在这条链路上存在缺口：

- `cloudcc get customPage ...` / `cloudcc get custompage ...` 不支持。
- 高代码 customPage 不纳入 MSAPI apply；这是合理边界，但技能缺少同等的 high-code resource 更新命令。
- `cloudcc publish pagecomponent` 能发布组件，但不会联动检查或更新引用它的 customPage。
- `cloudcc scan msapi . online-highcode` 能看到 customPage 数量，但不能直接完成 customPage 更新。

## 本次实际修复

本次修复分两步完成。

### 1. 发布新版 pagecomponent

发布新版 `component-customer-workbench`：

| 字段 | 值 |
| --- | --- |
| pagecomponent id | `6a4db950e4b0a577cbba1eca` |
| apiName | `custc_2026079sRcX7wv` |
| version | `5` |
| component | `component-customer-workbench` |
| workspaceUrl | `https://x.agentcici.com/app?aiApp=customer-workbench` |

新版 bundle 调整：

- 默认 `embedded=true`。
- UMD bundle 增加自动挂载逻辑，发现 `<component-customer-workbench>` 后执行 Vue mount。
- 增加 DOM iframe fallback，避免 Vue mount 失败时继续白页。

### 2. 更新 customPage 到 V2.0

通过 CloudCC devconsole developer-token API 更新自定义页面：

| 字段 | 值 |
| --- | --- |
| pageApi | `customer_interaction_workbench` |
| customPage id | `6a4dbc0ce4b0a577cbba1ecb` |
| renderVersion | `V2.0` |
| pageContent.comId | `6a4db950e4b0a577cbba1eca` |
| pageContent.embedded | `true` |

修复后回读：

```json
{
  "customPageId": "6a4dbc0ce4b0a577cbba1ecb",
  "renderVersion": "V2.0",
  "items": [
    {
      "name": "component-customer-workbench",
      "comId": "6a4db950e4b0a577cbba1eca",
      "embedded": true,
      "workspaceUrl": "https://x.agentcici.com/app?aiApp=customer-workbench"
    }
  ],
  "cdnV5HasAutoMount": true,
  "cdnV5HasEmbeddedTrue": true
}
```

## 关键 API 契约

以下契约应沉淀到 `cc-customization-expert-msapi`，而不是散落在项目脚本中。

### 1. detailCustomPage 读取契约

接口：

```text
POST {baseUrl}/devconsole/custom/pc/1.0/post/detailCustomPage
```

请求体必须为 CloudCC devconsole envelope：

```json
{
  "head": {
    "appType": "lightning-devconsole",
    "accessToken": "<runtime-token>",
    "source": "lightning-devconsole",
    "version": "public"
  },
  "body": {
    "pageApi": "customer_interaction_workbench"
  }
}
```

返回后需要解析：

- `data.pageContent`
- `data.canvasStyleData`
- `data.renderVersion`
- `data.id`

### 2. insertCustomPage 更新契约

接口：

```text
POST {baseUrl}/devconsole/custom/pc/1.0/post/insertCustomPage
```

同一个接口同时用于创建和更新。更新时 body 中带现有 `pageApi`，服务端会生成新记录和新 `renderVersion`。

关键字段：

```json
{
  "id": "<old-or-current-custom-page-id>",
  "pageLabel": "客户互动工作台",
  "pageApi": "customer_interaction_workbench",
  "pageContent": "[...]",
  "canvasStyleData": "{...}",
  "orgId": "<org-id>",
  "renderVersion": "V1.0",
  "compList": [
    {
      "id": "6a4db950e4b0a577cbba1eca",
      "compUniName": "component-customer-workbench"
    }
  ],
  "isTemplate": 0
}
```

注意事项：

- `compList` 必须使用 `{ id, compUniName }`，不能只传 `{ id, compName }`。
- 不能使用 CRM Web shell 的普通 `lightning-main` envelope 保存 customPage；保存需要 `lightning-devconsole` 上下文。
- 不能只更新 pagecomponent；已有 customPage 的 `pageContent.comId` 不会自动跟随最新组件。
- 更新后必须再次 `detailCustomPage` 回读，不应只相信保存接口的 returnCode。

### 3. CRM 注入页运行态验收契约

仅有 customPage 回读成功还不够。应增加真实运行态检查：

- 打开：

```text
/#/injectionComponent?page=<pageApi>&button=Home
```

- 检查 CDN 脚本是否为预期版本。
- 检查 DOM 中是否存在目标 custom element。
- 检查目标元素是否有非空内容。
- 检查 iframe 或组件主内容是否可见。
- 检查浏览器 console 是否有 mount/runtime error。

本次修复后的运行态事实：

```json
{
  "hasElement": true,
  "hasIframe": true,
  "iframeSrc": "https://x.agentcici.com/app?aiApp=customer-workbench",
  "scripts": [
    "https://res.lightning.cloudcc.cn/customjs/org0720f814430017229/component-customer-workbench-V5.0.js"
  ]
}
```

## 技能缺口清单

### GAP-001：customPage high-code resource 命令缺失

当前技能说明中承认 customPage 是 high-code 资源，继续走 CloudCC resource/API 通道，不纳入 MetadataService。但是 Go CLI 未提供完整 customPage 操作命令。

建议新增：

```bash
cloudcc get customPage <projectPath> [pageApi]
cloudcc detail customPage <projectPath> <pageApi|id>
cloudcc create customPage <projectPath> <payload|@file>
cloudcc update customPage <projectPath> <pageApi|id> <payload|@file>
cloudcc delete customPage <projectPath> <pageApi|id>
```

最低可先实现：

```bash
cloudcc detail customPage . customer_interaction_workbench
cloudcc update customPage . customer_interaction_workbench @custom-page.json
```

验收标准：

- 支持 devconsole envelope。
- 支持通过 `CloudCCDev` 解析 token。
- 不输出 token、password、CloudCCDev 原文。
- update 后自动回读并输出 `id`、`pageApi`、`renderVersion`、`componentRefs`。

### GAP-002：pagecomponent publish 后缺少 customPage 引用检查

`cloudcc publish pagecomponent` 当前只负责发布组件。对于已经被 customPage 引用的组件，发布新版本或生成新组件 id 后，旧 customPage 可能仍绑定旧 id。

建议在发布后输出引用检查：

```bash
cloudcc publish pagecomponent customer-workbench .
```

增强输出：

```json
{
  "publishedComponent": {
    "id": "6a4db950e4b0a577cbba1eca",
    "component": "component-customer-workbench",
    "version": 5
  },
  "customPageReferences": [
    {
      "pageApi": "customer_interaction_workbench",
      "currentComId": "6a4d348fe4b0a577cbba1ebf",
      "status": "stale_reference",
      "suggestedCommand": "cloudcc bind pagecomponent . customer_interaction_workbench 6a4db950e4b0a577cbba1eca"
    }
  ]
}
```

验收标准：

- 能发现 customPage 仍引用旧组件 id。
- 能区分 `same_component_latest_version`、`stale_reference`、`not_referenced`。
- 对 stale reference 明确给出下一条命令。

### GAP-003：缺少 pagecomponent -> customPage 绑定命令

建议新增：

```bash
cloudcc bind pagecomponent <projectPath> <pageApi> <componentIdOrName> [--embedded true] [--workspace-url <url>]
```

该命令负责：

- 读取 customPage。
- 读取目标 pagecomponent。
- 用目标 component 的 `vueData` 合成 `pageContent`。
- 更新 `pageContent[].comId`。
- 更新 `componentInfo.id/component/loadModel`。
- 更新 `propObj.workspaceUrl` 等外部传参。
- 更新 `compList` 为 `{ id, compUniName }`。
- 保留 canvasStyleData 中已有页面级信息。
- 保存 customPage。
- 回读 customPage。
- 可选执行 CRM injection runtime smoke。

示例：

```bash
cloudcc bind pagecomponent . customer_interaction_workbench component-customer-workbench \
  --embedded true \
  --workspace-url https://x.agentcici.com/app?aiApp=customer-workbench
```

验收输出：

```json
{
  "pageApi": "customer_interaction_workbench",
  "previous": {
    "customPageId": "6a4d3b831b8c6d0ec6dd22ef",
    "renderVersion": "V1.0",
    "componentId": "6a4d348fe4b0a577cbba1ebf",
    "embedded": false
  },
  "current": {
    "customPageId": "6a4dbc0ce4b0a577cbba1ecb",
    "renderVersion": "V2.0",
    "componentId": "6a4db950e4b0a577cbba1eca",
    "embedded": true
  },
  "status": "updated"
}
```

### GAP-004：运行态验证停留在元数据层，未覆盖白页类问题

本次问题的典型特点是：

- API 发布成功。
- customPage 能读取。
- 菜单能显示。
- CDN 脚本能加载。
- 但组件没有 mount，最终白页。

因此技能需要一个运行态验收命令或 playbook：

```bash
cloudcc verify injectionPage <projectPath> <pageApi> [--url <crm-url>]
```

验收维度：

| 检查项 | 通过标准 |
| --- | --- |
| customPage | `detailCustomPage.returnCode=200` |
| component refs | `pageContent[].comId` 指向预期组件 |
| CDN script | 加载预期版本脚本，HTTP 200 |
| DOM mount | 目标 custom element 内部非空 |
| iframe/content | iframe src 或组件主内容可见 |
| console | 无关键 mount/runtime error |

命令应输出结构化结果，并在失败时给出诊断方向：

- `custom_page_missing`
- `stale_component_reference`
- `cdn_script_missing`
- `component_not_mounted`
- `iframe_missing`
- `iframe_auth_required`

### GAP-005：技能文档未强调 customPage 保存 token/source 差异

本次调试过程中，使用 CRM Web shell 的 `lightning-main` 上下文可以读取 detail，但保存 customPage 失败或返回后端异常。正确保存路径需要 `lightning-devconsole` head。

建议在 `platform/pagecomponent` 或新增 `platform/customPage` devguide 中明确：

- CRM shell token 可以用于运行态读取，不代表可保存 devconsole high-code metadata。
- customPage 保存必须使用 devconsole envelope。
- `head.appType`、`head.source` 和 token 类型不匹配时，应 fail closed 并提示 token/source 不匹配。

### GAP-006：安全发布缺少依赖收集白名单

本项目曾从仓库根目录执行 pagecomponent publish，CLI 依赖收集将不相关配置内容打入 `compContentVue`，存在信息泄露风险。本次后续改用临时最小项目目录发布。

技能应增强：

- 发布 pagecomponent 时只允许读取：
  - `frontend/pagecomponents/<name>/config.json`
  - `frontend/pagecomponents/<name>/<name>.vue`
  - `frontend/build/<bundle>.js`
- 默认拒绝将仓库根配置、环境文件、token cache、`.claw-local`、`.env`、`cloudcc-cli.config.json` 打入组件内容。
- 若用户从仓库根目录发布，CLI 应输出采集文件列表并要求显式确认，或自动创建临时最小发布包。

建议新增：

```bash
cloudcc package pagecomponent <name> <projectPath> --dry-run
cloudcc publish pagecomponent <name> <projectPath> --safe
```

## 建议修复优先级

### P0：必须补齐

1. `cloudcc detail customPage`
2. `cloudcc update customPage`
3. `cloudcc bind pagecomponent`
4. pagecomponent publish 安全文件白名单
5. customPage 保存契约文档化：`lightning-devconsole` envelope + `{ id, compUniName }`

P0 的目标是让本次修复不再依赖项目内临时脚本或人工还原接口。

### P1：应尽快补齐

1. `cloudcc publish pagecomponent` 后自动扫描 stale customPage references。
2. `cloudcc verify injectionPage` 支持真实浏览器或只读 DOM 验证。
3. `cloudcc scan msapi . online-highcode` 输出 customPage 与 pagecomponent 引用关系。
4. pagecomponent UMD 生成模板内置自动 mount/fallback 模式。

### P2：长期增强

1. customPage 版本 diff 与 rollback helper。
2. customPage dependency graph：page -> component -> CDN script -> menu -> application -> profile。
3. HTML component、pagecomponent、customPage、tab、application 的一键交付 plan。
4. CRM embedded SSO/token handoff 的标准方案模板。

## 推荐的技能内置诊断流程

当用户反馈“菜单可见但打开白页”时，技能应按以下顺序诊断：

1. 确认菜单 tab 是否存在。
2. 确认 tab 是否绑定到当前应用 selectedTabList。
3. 确认 tab 指向的 pageApi。
4. `detailCustomPage(pageApi)` 读取 pageContent。
5. 检查 `pageContent[].comId` 是否存在、是否为预期组件。
6. 检查 `embedded`、`propObj` 等运行参数。
7. 检查 `compList` 是否与 pageContent 一致。
8. 查询 pagecomponent detail，确认 version、vueData、loadModel。
9. 打开 CDN JS，确认脚本 HTTP 200 且包含预期 mount 逻辑。
10. 打开 CRM injection route，检查 DOM 是否真实渲染。
11. 如果 iframe 出现登录页，归类为 downstream auth/SSO，而不是 CloudCC 白页。

## 回归测试用例

### CASE-001：customPage 仍指向旧组件

前置：

- 已存在 pagecomponent A 和新版 pagecomponent B。
- customPage pageContent 指向 A。

操作：

```bash
cloudcc bind pagecomponent . customer_interaction_workbench <component-B-id> --embedded true
```

期望：

- customPage 新版本生成。
- pageContent 指向 B。
- compList 指向 B。
- 回读 renderVersion 增加。

### CASE-002：UMD 只暴露组件对象但不自动挂载

前置：

- CDN JS 加载成功。
- DOM 有 `<component-customer-workbench>`。
- DOM 内部为空。

操作：

```bash
cloudcc verify injectionPage . customer_interaction_workbench
```

期望：

- 返回 `component_not_mounted`。
- 建议重新发布带 auto-mount/fallback 的 pagecomponent。

### CASE-003：customPage 保存 payload 的 compList 字段错误

前置：

- payload 使用 `{ id, compName }`。

操作：

```bash
cloudcc update customPage . customer_interaction_workbench @payload.json
```

期望：

- CLI 在本地校验阶段拒绝。
- 提示必须使用 `{ id, compUniName }`。

### CASE-004：使用 lightning-main 保存 customPage

前置：

- 使用 CRM shell token/source。

操作：

```bash
cloudcc update customPage . customer_interaction_workbench @payload.json --source lightning-main
```

期望：

- CLI fail closed。
- 提示 customPage 保存需要 `lightning-devconsole` source。

### CASE-005：菜单显示但 iframe 登录页

前置：

- customPage 与 pagecomponent 均正确。
- iframe 成功渲染 AgentCiCi URL。
- AgentCiCi 未登录。

操作：

```bash
cloudcc verify injectionPage . customer_interaction_workbench
```

期望：

- CloudCC 嵌入页状态为 passed。
- 下游状态为 `iframe_auth_required`。
- 提示后续应实现 SSO/token handoff，而不是继续排查 customPage。

## 对 TASK-171 的结论

本次「客户互动工作台」CRM 白页问题已经修复，但修复过程中暴露出的关键事实是：当前 `cc-customization-expert-msapi` 可以支持 pagecomponent 发布和低代码元数据扫描/计划，但对 `customPage -> pagecomponent -> CRM injection runtime` 这条高代码嵌入链路没有形成完整闭环。

因此后续应把本次临时通过 devconsole API 完成的操作转化为技能能力。理想状态下，同类任务应由以下标准命令完成：

```bash
cloudcc publish pagecomponent customer-workbench .
cloudcc bind pagecomponent . customer_interaction_workbench component-customer-workbench --embedded true --workspace-url https://x.agentcici.com/app?aiApp=customer-workbench
cloudcc verify injectionPage . customer_interaction_workbench
```

这样既保留 `cc-customization-expert-msapi` 的实施边界，也避免每个项目重复从 CloudCC 前端源码或旧 CLI 里反推接口契约。
