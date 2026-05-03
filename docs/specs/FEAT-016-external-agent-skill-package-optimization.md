---
kind: feature-spec
feature_id: FEAT-016
title: External Agent Skill Package Optimization Loop
status: implemented
owner_role: product-admin-skill-governance
task_ids: TASK-037
related_decisions: FEAT-014
related_issues: none
updated_at: 2026-05-03T00:52:59Z
updated_by: ai
---

# FEAT-016 - 通用外部智能体技能包优化闭环

## 背景与目标

管理后台当前已支持把租户自定义技能导出为 `universal-skill-package@1.0` zip 包，再导入回系统形成可编辑草稿。系统尚未上线，因此本轮直接把导出包结构调整为更贴近外部智能体生态的 8 文件结构：`manifest.json`、`SKILL.md`、`cici-skill.md`、`prompt.md`、`contract.json`、`resources.json`、`PACKAGE_SPEC.md`、`README.md`。

下一步希望借助 OpenClaw、Codex、Claude Code、Cursor 等具备技能/规则文件能力的外部智能体工具，对导出的技能包进行进一步优化，再反向导入本系统。这类工具通常可以通过项目级或工作区级 `SKILL.md`/规则文件理解某类任务的操作规程；因此方案不绑定某一个具体平台，而是提供一个通用优化器规则 `cici-skill-package-optimizer/SKILL.md`。导出的技能包内再携带机器/人都可读的 `PACKAGE_SPEC.md`，让任意外部智能体都能按本系统规范修改技能包。

本设计目标是形成稳定闭环：

- 本系统导出标准技能 zip。
- zip 内包含 `PACKAGE_SPEC.md`，说明包结构、字段语义、可改范围和安全约束。
- 外部智能体通过 `cici-skill-package-optimizer/SKILL.md` 读取并优化该 zip。
- 外部智能体输出仍符合 `universal-skill-package@1.0` 的 zip。
- 本系统按现有导入流程解析并创建/覆盖为自定义技能草稿。

## 范围

### In Scope

- 导出包新增行业通用入口 `SKILL.md`，用于让外部智能体直接理解并使用该业务技能包。
- 原 Cici 内部规格正文从 `skill.md` 更名为 `cici-skill.md`，导入时映射到 `draftSpecText`。
- 导出包新增 `PACKAGE_SPEC.md` 文件，用于描述 `universal-skill-package@1.0` 的构建规范。
- 后端导出/导入安全白名单允许 `SKILL.md`、`cici-skill.md`、`PACKAGE_SPEC.md`。
- 导出包 `README.md` 增加简短说明：本包可交给具备技能/规则文件能力的外部智能体优化，并要求其遵守 `PACKAGE_SPEC.md` 与 `cici-skill-package-optimizer/SKILL.md`。
- 设计通用外部智能体优化器 `cici-skill-package-optimizer/SKILL.md` 的职责、输入、输出和约束。
- 明确外部智能体只能作为离线优化器，不作为本系统运行时技能执行环境。
- 保留现有导入预览和资源映射流程。

### Out Of Scope

- 不做导入前 diff 预览增强。
- 不做任意外部智能体平台的内置集成或自动调用。
- 不要求外部智能体直接运行本系统导出的业务技能。
- 不做多技能批量优化。
- 不在技能包中携带工具密钥、知识库内容、API token 或连接串。
- 不改变管理端技能列表、编辑器和发布弹框的视觉交互。

## 用户场景

- 组织管理员导出一个“邮件营销活动”技能 zip，交给外部智能体优化提示片段和规格正文，再导入回本系统形成新草稿。
- 技能运营人员希望外部 Agent 在不接触内部数据库和密钥的前提下，优化 `prompt.md` 的执行步骤、补充 `SKILL.md` 的外部使用指引、完善 `cici-skill.md` 的 Cici 规格正文和 `contract.json` 的输出约定。
- 外部顾问收到 zip 后，先阅读 `README.md` 和 `PACKAGE_SPEC.md`，知道哪些文件能改、哪些字段不能改，优化后重新打包交回。
- 导入包中依赖的工具或知识库在当前组织不存在，系统仍按现有资源映射流程给出未匹配提示。

## 现状与约束

### Verified Facts

- 当前导出包格式为 `universal-skill-package@1.0`。
- 当前后端 zip 解析有文件白名单，未知文件会被拒绝。
- 调整后导入逻辑从 `manifest.json` 读取包身份和名称，从 `prompt.md` 读取提示片段，从 `cici-skill.md` 读取 Cici 规格正文，从 `contract.json` 读取输出契约和风险等级，从 `resources.json` 映射工具/知识库。
- 外部智能体工具可通过 `SKILL.md` 或同等规则文件理解任务规范；本方案统一以 `cici-skill-package-optimizer/SKILL.md` 作为优化规则载体。

### Key Constraints

- `PACKAGE_SPEC.md` 是本系统技能包规范说明，不是外部智能体的优化器规则文件。
- `cici-skill-package-optimizer/SKILL.md` 是外部智能体侧的工具型规则，不应被打包进每个业务技能 zip。
- zip 包内不得包含任何密钥、token、密码、连接串或私有 API 鉴权信息。
- `manifest.json.format` 与 `manifest.json.formatVersion` 是导入兼容关键字段，优化器不得擅自改动。
- 反向导入系统后仍以“草稿”落地，发布仍需要管理员在本系统内完成。

## 方案设计

### 1. 技能包结构升级

新导出的 zip 文件结构：

```text
<skill-code>-skill-package.zip
├── manifest.json
├── SKILL.md
├── cici-skill.md
├── prompt.md
├── contract.json
├── resources.json
├── PACKAGE_SPEC.md
└── README.md
```

其中 `SKILL.md` 是外部智能体通用入口，`cici-skill.md` 是 Cici 导入事实源。

文件职责：

| 文件 | 职责 | 优化器是否可改 |
|---|---|---|
| `manifest.json` | 包身份、格式版本、技能名称、描述、来源和版本元数据 | 仅允许改 `name`、`description` 等展示字段；禁止改 `format`、`formatVersion`、`packageId`，除非用户明确要求新建另一个技能 |
| `SKILL.md` | 行业通用外部智能体入口，说明如何理解和使用该业务技能包 | 可优化 |
| `cici-skill.md` | Cici 系统内部导入用技能规格正文，导入时映射为 `draftSpecText` | 可优化 |
| `prompt.md` | 运行时提示片段，优化重点 | 可优化 |
| `contract.json` | 输出契约、风险等级、触发提示和用户意图示例 | 可优化，但必须保持合法 JSON |
| `resources.json` | 工具和知识库依赖清单 | 仅允许补充资源名称、展示名、是否必需和匹配策略；禁止写密钥或内部配置 |
| `PACKAGE_SPEC.md` | 本格式的构建规范、字段约束、优化规则 | 不应改动，除非格式版本升级 |
| `README.md` | 给人看的包说明、优化和导入说明 | 可轻微更新说明，但不作为运行时输入 |

### 2. `PACKAGE_SPEC.md` 内容要求

`PACKAGE_SPEC.md` 应由系统生成，内容保持稳定、简洁、可被外部 Agent 直接遵守。

建议结构：

```markdown
# universal-skill-package@1.0

## Purpose

This package is an exchange format for Cici Assistant tenant custom skills.
It is intended for review, optimization, and re-import into Cici Assistant.

## Files

- manifest.json: package identity and metadata.
- SKILL.md: external-agent entrypoint for understanding and using this business skill package.
- cici-skill.md: Cici Assistant skill specification imported as draftSpecText.
- prompt.md: runtime prompt fragment.
- contract.json: output contract, risk level, trigger hints, and examples.
- resources.json: external resource dependencies by name only.
- README.md: human-facing package instructions.

## Editing Rules

- Preserve manifest.format = "universal-skill-package".
- Preserve manifest.formatVersion = "1.0".
- Preserve manifest.packageId unless intentionally creating a different skill.
- Keep all JSON files valid UTF-8 JSON.
- Optimize prompt.md for clarity, sequencing, tool-use discipline, and safety.
- Optimize SKILL.md for external-agent readability and package navigation.
- Optimize cici-skill.md for Cici capability boundaries and operator readability.
- Optimize contract.json for clear output expectations and trigger examples.
- Do not add secrets, tokens, passwords, API keys, connection strings, or private credentials.
- Do not place tool runtime configuration in resources.json; only list resource names and matching hints.

## Re-import Contract

After optimization, zip the files at package root and import the zip into Cici Assistant.
Cici Assistant will map tools and knowledge bases by name in the importing organization.
Missing resources must be resolved inside Cici Assistant after import.
```

中文说明可以保留在同一文件中，便于国内团队人工查看；但关键约束建议同时提供英文短句，便于外部 Agent 更稳定遵守。

### 3. `README.md` 调整

`README.md` 继续面向人类阅读，不承载完整 schema。

建议包含：

- 技能名称、技能代码、导出时间。
- 本包格式：`universal-skill-package@1.0`。
- “如需使用外部智能体优化，请使用 `cici-skill-package-optimizer/SKILL.md`，并要求其遵守 `PACKAGE_SPEC.md`。”
- “优化后保持文件位于 zip 根目录，再导入 Cici Assistant。”
- 安全提醒：不要把密钥、token、密码写入任何文件。

### 4. 外部智能体侧 `cici-skill-package-optimizer`

外部智能体侧需要创建一个专门的优化器规则目录：

```text
skills/cici-skill-package-optimizer/
└── SKILL.md
```

该 skill 不是业务技能，而是优化器规则。它的职责是指导外部智能体如何处理 Cici Assistant 导出的技能包。只要目标工具支持加载类似 `SKILL.md` 的项目规则，就可以复用这套说明；目录位置由具体工具决定。

`SKILL.md` 核心要求：

- 识别 `universal-skill-package@1.0`。
- 解压 zip，并确认根目录包含必要文件。
- 读取 `PACKAGE_SPEC.md` 作为最高优先级包内规范。
- 优化重点顺序：
  1. `prompt.md`
  2. `SKILL.md`
  3. `cici-skill.md`
  4. `contract.json`
  5. `resources.json`
  6. `README.md`
- 默认不修改 `manifest.json` 的身份字段。
- JSON 修改后必须重新解析确认合法。
- 不得输出任何密钥、token、密码、连接串。
- 完成后重新打包为 zip，根目录保持原文件结构。
- 输出一份简短优化摘要，说明改了哪些文件和为什么。

建议 `SKILL.md` frontmatter：

```markdown
---
name: cici-skill-package-optimizer
description: Optimize Cici Assistant universal-skill-package@1.0 zip packages and repackage them for re-import.
---
```

正文应明确：

- 当用户提供 Cici Assistant 技能 zip、要求优化 Cici 技能包、或提到 `universal-skill-package@1.0` 时使用。
- 优化时优先提升执行步骤清晰度、工具调用边界、输出格式稳定性、风险升级规则和资源依赖可移植性。
- 如果缺少 `PACKAGE_SPEC.md`，补充一个符合当前 8 文件结构的 `PACKAGE_SPEC.md`。

### 5. 外部优化闭环

```mermaid
flowchart LR
  A["Cici Assistant 导出技能 zip"] --> B["zip 内包含 PACKAGE_SPEC.md"]
  B --> C["外部智能体加载 cici-skill-package-optimizer/SKILL.md"]
  C --> D["解压并读取 manifest / SKILL / cici-skill / PACKAGE_SPEC / prompt / contract / resources"]
  D --> E["优化 prompt.md、SKILL.md、cici-skill.md、contract.json 等文件"]
  E --> F["校验 JSON 与敏感信息"]
  F --> G["重新打包 universal-skill-package@1.0 zip"]
  G --> H["Cici Assistant 导入 zip"]
  H --> I["导入预览与资源映射"]
  I --> J["创建或保存为租户自定义技能草稿"]
```

关键边界：

- 外部智能体只做离线优化，不获得本系统管理员权限。
- 外部智能体不直接发布技能。
- 外部智能体不负责解决当前组织内资源映射，资源匹配仍由本系统导入流程完成。
- 本系统导入后仍走草稿/发布分离，发布说明和编译预览继续在本系统内完成。

### 6. 安全与校验

导出阶段：

- 生成 `PACKAGE_SPEC.md`。
- 所有文件继续执行敏感信息扫描。
- `resources.json` 只输出工具名、展示名、必需性、匹配策略，不输出工具配置或凭据。

外部智能体优化阶段：

- 优化器必须按 `PACKAGE_SPEC.md` 执行。
- JSON 文件修改后必须解析校验。
- 若发现疑似密钥、token、password、connection string，必须移除并在优化摘要中说明。
- 不允许把外部平台的运行配置写进 `resources.json`。

导入阶段：

- 允许 `SKILL.md`、`cici-skill.md`、`PACKAGE_SPEC.md` 出现在 zip 中。
- `cici-skill.md` 是导入规格正文的唯一事实源；不再导入旧 `skill.md`。
- 继续校验 `manifest.json.format=universal-skill-package` 和 `formatVersion=1.0`。
- 继续执行敏感信息扫描。
- 继续执行资源映射预览。
- 不新增导入前 diff 预览。

## 接口与数据影响

### 后端影响

- `SkillPackageService.ALLOWED_FILES` 使用当前 8 文件结构：`manifest.json`、`SKILL.md`、`cici-skill.md`、`prompt.md`、`contract.json`、`resources.json`、`PACKAGE_SPEC.md`、`README.md`。
- 导出构建逻辑新增 `SKILL.md` 与 `PACKAGE_SPEC.md` 生成。
- 模型标准化导出路径和确定性回退路径都必须包含 `SKILL.md`、`cici-skill.md`、`PACKAGE_SPEC.md`。
- 导入解析要求 `manifest.json` 和 `cici-skill.md`。
- 导入创建逻辑不需要读取 `PACKAGE_SPEC.md` 生成业务字段。
- 敏感信息扫描覆盖 `PACKAGE_SPEC.md`。

### 前端影响

- 管理端导出入口无新增交互。
- 导入入口无新增 diff 预览。
- 若导入预览返回包格式信息，可在现有预览说明中展示“包含 PACKAGE_SPEC.md / 外部优化包”这一事实，但不是第一版必需。

### 文档影响

- 更新 FEAT-014 或其后续实现说明，记录导出包文件从 6 个扩展到 7 个。
- 可在 `README.md` 或管理端帮助文案中补一句：技能 zip 可交由外部智能体按 `cici-skill-package-optimizer/SKILL.md` 优化后再导入。
- `cici-skill-package-optimizer/SKILL.md` 可独立维护，不进入业务技能 zip。

## 任务拆分

- `TASK-037`: 通用外部智能体技能包优化闭环。

建议拆分：

- 后端：新增 `PACKAGE_SPEC.md` 生成、白名单兼容和回归测试。
- 文档：固化 `PACKAGE_SPEC.md` 模板内容。
- 外部工具：创建通用 `cici-skill-package-optimizer/SKILL.md`。
- 验证：导出新包、用优化器修改、重新导入创建草稿。

## 验收标准

- 新导出的技能 zip 包含 `manifest.json`、`SKILL.md`、`cici-skill.md`、`prompt.md`、`contract.json`、`resources.json`、`PACKAGE_SPEC.md`、`README.md`。
- `PACKAGE_SPEC.md` 清楚说明文件职责、可编辑范围、安全限制和反向导入方式。
- 导入时使用 `cici-skill.md` 作为 Cici 技能规格正文。
- 未知文件仍会被拒绝，避免 zip 被塞入任意内容。
- 外部智能体在加载 `cici-skill-package-optimizer/SKILL.md` 后，能根据 `PACKAGE_SPEC.md` 优化技能包，并输出仍可导入的 zip。
- 优化后的 zip 导入本系统后，落为租户自定义技能草稿。
- 导入阶段不新增 diff 预览。

## 风险与回滚

### 风险

- 外部 Agent 可能错误修改 `manifest.json.packageId`，导致导入后变成新技能或 code 冲突。
- 外部 Agent 可能生成非法 JSON。
- 外部 Agent 可能把工具配置、API key 或连接串写入资源文件。
- `PACKAGE_SPEC.md` 如果过长，可能让优化器忽略关键规则。

### 缓解

- `PACKAGE_SPEC.md` 采用短规则、强约束、明确禁止项。
- 导入时继续做 JSON 校验、格式校验和敏感信息扫描。
- `cici-skill-package-optimizer` 明确要求优化后自检。
- 导入仍落草稿，由管理员在本系统内编译预览和发布。

### 回滚

- 如果新增文件导致外部兼容问题，可在未上线前继续调整文件结构。
- 外部智能体优化器是增强能力，停用不会影响本系统现有导入导出。

## 实现进展

- 当前状态：已实现。
- 已完成项：
  - 确认采用 `PACKAGE_SPEC.md` 作为包内规范文件。
  - 确认依赖通用 `cici-skill-package-optimizer/SKILL.md` 作为稳定优化器规则。
  - 确认不做导入前 diff 预览增强。
  - 后端导出包白名单已调整为 8 文件结构：`manifest.json`、`SKILL.md`、`cici-skill.md`、`prompt.md`、`contract.json`、`resources.json`、`PACKAGE_SPEC.md`、`README.md`。
  - `SkillPackageService` 在模型标准化路径和确定性回退路径之后统一补齐 `SKILL.md` 与 `PACKAGE_SPEC.md`，并在 `README.md` 追加外部智能体优化说明。
  - 已新增 `.agents/skills/cici-skill-package-optimizer/SKILL.md` 初版，作为通用外部智能体优化规则。
  - 已更新 `SkillGovernanceIntegrationTest`：导出 zip 必须包含 `SKILL.md`、`cici-skill.md`、`PACKAGE_SPEC.md`，且 `PACKAGE_SPEC.md` / `README.md` 均包含 optimizer 规则提示。
  - 本轮验证通过：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillGovernanceIntegrationTest test`。
- 未完成项：
  - 尚未用真实外部智能体执行一次“优化 zip -> 重新打包 -> 导入”的人工端到端验收。

## 交接说明

下一位接手者先看：

- `backend/src/main/java/com/codehouse/ciciassistant/skill/service/SkillPackageService.java`
- `backend/src/test/java/com/codehouse/ciciassistant/skill/SkillGovernanceIntegrationTest.java`
- `docs/specs/FEAT-014-skill-versioning-import-export.md`

继续推进前需要确认：

- `PACKAGE_SPEC.md` 文件名已按大写落地。
- `cici-skill-package-optimizer/SKILL.md` 已先放在项目 `.agents/skills/cici-skill-package-optimizer/`，后续若要跨项目复用，可再抽为可分发规则包。
- 是否需要在管理端导出成功提示中加入“可用外部智能体优化”的帮助入口。
