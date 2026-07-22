---
kind: feature-spec
feature_id: FEAT-130
title: 对话技能选择的强制执行上下文与可观测性
status: completed
owner_role: fullstack-agent
task_ids: TASK-225
related_decisions: none
related_issues: ISSUE-033
updated_at: 2026-07-22T10:15:00+08:00
updated_by: MANAGER-001
---

# FEAT-130 - 对话技能选择的强制执行上下文与可观测性

## 背景与目标

- 当前工作台技能选择只授权该技能的专属工具，但系统提示词仍并列注入全部已绑定技能。用户在选择“自动获客任务内容补全”等技能后，无法稳定判断选择是否真正影响了本轮回复；在输入文本中再次声明技能反而更有效。
- 目标是让工作台选择成为本轮明确的业务执行优先级：所选技能的流程、输出契约和文件型参考文档优先且强制生效；平台安全策略、Agent 直接工具和权限边界仍不受绕过。

## 设计

### 运行时语义

1. 工作台发送的 `activeSkillCode` 是本轮请求选择；服务端继续校验它必须是当前 Agent 已绑定且启用的技能。
2. 选择有效时：
   - 系统提示词只注入所选技能的业务流程片段，并明确要求模型不得以其他业务技能替代；
   - 所选技能的输出契约优先；
   - 仅解析所选文件型技能的参考文档；
   - 该技能的手动或意图路由工具继续获得授权。
3. 未选择时，保留现有多技能、always-on 和 Agent 能力解析行为。
4. 选择无效、已解绑或已禁用时，不提升工具权限；Trace 必须说明请求未被采纳的原因。

### 可观测性与界面

- 输入区在选择成功后显示“优先 · 技能名称”，选择项显示“本轮优先执行”；取消后恢复“技能”。
- Trace 的技能数据同时保存 `requestedSkillCode`、`effectiveSkillCode`、`selectionStatus`、`selectionReason`、`activatedSkillCodes` 与 `boundSkillCodes`。
- 用户和管理端监控详情均以“用户选择 / 有效上下文 / 实际激活”顺序展示；无效选择明确显示拒绝原因，不能把候选技能误报为已执行。
- 视觉保持现有认证后工作台的暖象牙、墨色、香槟金结构线、紧凑密度和文本式状态语汇，不新增卡片、渐变、阴影、移动端适配或新图像资产。

## 范围

### In Scope

- 工作台技能选择的本轮强制业务上下文、文件型文档约束与输出契约优先级。
- Trace 数据、工作台监控与管理员运行监控的选择/激活状态表达。
- 后端和前端定向回归及桌面端状态检查。

### Out Of Scope

- 不改变技能绑定、发布、版本钉住、租户授权或平台安全策略。
- 不承诺外部工具、搜索或定时任务必然成功；工具真实执行结果仍以 Trace 为准。
- 不新增移动端实现、路由级视觉重构或外部系统写入。

## 验收标准

- 选择已绑定的手动/意图技能后，所选技能的流程、输出契约及文件型参考文档成为该轮业务上下文，其他业务技能不再替代它。
- 平台安全策略和 Agent 直接工具仍可用，未选择时行为保持兼容。
- Trace 能区分“用户请求选择”“有效强制上下文”“实际激活技能”和“未采纳原因”。
- 输入区、用户监控与管理员监控均清晰呈现状态，桌面端无横向溢出或 console error/warning。

## 风险与回滚

- 风险：强制选择可能压制其他业务技能。仅压制其业务提示词和文件型文档，不绕过平台策略、权限或 Agent 直接工具。
- 风险：旧 Trace 没有新字段。前端对缺失字段使用历史兼容文案。
- 回滚：回退本功能提交即可恢复现有选择语义；不迁移或删除历史 Trace。

## 实现与验证

- `SkillPromptAssembler` 在存在有效选择时只注入所选技能的业务流程，并写入不得被其他业务技能替代的强制指令；所选技能的输出契约成为该轮唯一业务输出约束。
- `BuiltinSkillDocumentService` 在存在有效选择时只解析所选技能的文件型参考文档；未选择时仍保持原有多技能解析。
- `ChatOrchestratorService` 将请求技能码和解析后的有效技能码同时传入 `AgentRunTraceService`；Trace 持久化 `requestedSkillCode`、`effectiveSkillCode`、`selectionStatus`、`selectionReason`、`activatedSkillCodes`、`boundSkillCodes`，并保持旧 `activeSkillCode` 兼容字段。
- 工作台输入按钮显示“优先 · 技能名称”，已选项显示“本轮优先执行”；用户与管理员监控以“用户选择 / 有效上下文 / 实际激活”呈现，并在未采纳时显示原因。
- `mvn -q -Dtest=AgentRunTraceServiceTest,SkillPromptAssemblerTest test`、`mvn -q -DskipTests compile`、`npm test`（28 文件 / 187 断言）、`npm run build` 与 `git diff --check` 均通过。浏览器本地应用加载无 console error；因当前没有已授权组织用户会话，未对受保护工作台和 Trace 页面伪造交互验收。
