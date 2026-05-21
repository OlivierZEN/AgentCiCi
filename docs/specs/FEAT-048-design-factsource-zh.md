---
title: FEAT-048 设计事实源中文化与 README 引用收口
status: active
updated_at: 2026-05-21T06:09:17Z
updated_by: ai
---

# FEAT-048 设计事实源中文化与 README 引用收口

## 背景

上一轮已经把 `AGENTS.md`、`PRODUCT.md`、`DESIGN.md` 收口为中文精简版，但两个问题还留着：

- `README.md` 仍引用旧版 `DESIGN.md` 的细粒度英文章节名和规则描述。
- `DESIGN.json` 作为详细设计事实源，内部大部分人类可读文案仍是英文。

这会造成顶层文档已经中文化，但详细事实源和索引入口仍存在明显语言割裂。

## 目标

- 把 `DESIGN.json` 中的人类可读文案翻译为中文。
- 保持 JSON 键名、结构、数值 token 和事实源职责不变。
- 将 `README.md` 对设计规则的引用收口到新的职责分工，不再依赖旧版 `DESIGN.md` 的章节堆叠。

## 范围

- 更新 `DESIGN.json` 的标题、purpose、rules、narrative 等说明性字符串。
- 更新 `README.md` 的 UI 设计治理段落，使其与新的 `DESIGN.md` / `DESIGN.json` 分工一致。

## 不在本次范围

- 不修改 `DESIGN.json` 的字段名、层级结构、颜色值、字号值和其他 token 数值。
- 不新增新的设计规则，只做翻译和表达收口。
- 不批量改动所有历史 spec 中引用 `DESIGN.md` 的旧措辞。

## 验收标准

- `DESIGN.json` 的人类可读字符串全部改为中文。
- `README.md` 不再强依赖旧版 `DESIGN.md` 的章节名作为事实源入口。
- 结构化消费者可继续按原键名读取 `DESIGN.json`。

## 交接说明

- 后续若新增跨页面设计规则，优先在 `DESIGN.json` 增补中文说明，再按需在 `DESIGN.md` 补一条摘要。
- 如果未来存在外部工具必须读取英文文案，再单独设计多语言字段，不要回退这次中文化。
