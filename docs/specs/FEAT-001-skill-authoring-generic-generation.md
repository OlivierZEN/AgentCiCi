---
updated_at: 2026-04-24T07:50:00Z
status: active
feature_id: FEAT-001
---

# FEAT-001 Skill Authoring Generic Generation

## Goal

- 把“自然语言创建技能”从“依赖内置场景模板猜行业”改为“依赖模型通用理解 + sourceText 结构化提取”。
- 即使模型不可用，fallback 也应尽量保留用户原文中的目标、步骤、工具名、边界和输出要求，而不是套到 CRM/审批等固定样例。

## Problem

- 当前链路在 `skill-authoring` 没有可用模型时，会退回启发式生成。
- 原启发式强依赖少量内置业务模板，容易把跨行业或新场景需求错配到最近的内置分类。
- 这种设计与“管理员自然语言描述任意行业技能”的产品目标不一致。

## Design

- 模型路径：
  - system prompt 明确要求优先忠实保留 sourceText 中的事实，不允许按已有样例强行套行业模板。
  - 示例只用于 JSON 结构说明，不作为场景先验。
- fallback 路径：
  - 不再先做固定行业分类再生成技能。
  - 优先提取：
    - 显式工具名
    - 编号步骤
    - 明确写出的输出要求
    - 明确写出的风险/人工确认边界
    - 首句/首段中的技能主题
  - 生成通用 skill 草稿，不额外注入 CRM/审批/合同等预置场景词。

## Acceptance

- 对任意行业需求，只要用户写得足够明确，生成草稿应优先反映用户原文，而不是被内置样例“带偏”。
- 当 sourceText 中存在显式工具名时，`toolWhitelist` 必须优先保留这些工具。
- 当 sourceText 或生成后的规格/提示中明确引用候选工具、MCP 工具或知识库名称时，系统必须自动补入 `toolWhitelist` / `kbWhitelist`，避免只在文本中提到资源但保存后未授权。
- 当 sourceText 中存在编号步骤时，`draftSpecText` 和 `promptFragment` 应保留这些步骤顺序。
- 现有 Skill 编辑页应允许管理员输入增量自然语言说明继续优化当前技能；优化结果必须保留当前 Skill 的 `id`、启用状态和治理字段，保存时更新原技能而不是误创建新技能。
- 对“增加 / 补充 / 加入 / 再增加”类继续优化请求，系统必须保留当前 `promptFragment` 与 `draftSpecText` 的既有步骤顺序、工具调用链、兜底规则和输出结构，只追加增量要求；除非管理员明确要求删除、替换、重写或改顺序，不得让模型整段改写旧草稿。
- 继续优化成功后，页面必须提供“回退本次优化”入口；在用户保存前可恢复优化前的表单草稿、编译预览、需求解析结果、会话 ID 与追问答案，避免不满意的模型结果只能手工修复。
- 没有模型时，fallback 仍应生成可编辑、可预览、可创建的结构化草稿。

## Verification

- `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillAuthoringIntegrationTest test`
- `frontend`: `npm run build`
