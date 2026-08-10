---
kind: feature-spec
feature_id: FEAT-123
title: 厂商模型目录能力边界
status: approved
task_id: TASK-218
source: 用户截图反馈（无外部设计文档链接）
updated_at: 2026-08-10T09:24:12Z
updated_by: codex
---

# 厂商模型目录能力边界

## 背景与目标

运营端 OneKeyToken 当前未开放远程模型枚举。系统却将本应用内置的三个名称作为“预设模型”展示，造成这些名称来自厂商且可用的误解。

本次目标是让模型目录完全按厂商能力工作：厂商可远程枚举时只显示远程返回的模型；厂商不能远程枚举时不注入、展示或回填本地预设模型。

## 范围

- OneKeyToken 的目录能力明确为“未开放远程枚举”。
- `全部模型` 返回空目录而非本地静态模型，前端在现有弹窗中显示明确空态。
- 凭据检测成功只代表 Chat Completions 可调用，不再声称存在本地模型目录。
- 保留已经由运营人员显式保存的已选模型和路由，不做静默清空或数据迁移。

## 服务端设计

- ProviderDef 不再为 OneKeyToken 声明本地默认模型。
- 对不支持远程枚举的厂商，`models/fetch` 成功返回 `count: 0`、空 `models/modelDetails`、`catalogSource: unavailable`、`remoteFetchSupported: false`，不访问或伪造 `/models`。
- OneKeyToken 检测成功的 `modelCount` 为 0，`sampleModels` 为空，目录来源为 `unavailable`。请求中直接验证的稳定别名为 `validatedModel=onekeytoken/auto`；响应中的下游 `routing.model_used` 或 `model` 仅作为 `resolvedModel` 诊断信息，不具备目录或直接可调用证明。
- 对支持远程枚举的厂商，不改变其现有远程请求与解析逻辑。

## 交互与视觉

运营人员在桌面端模型治理页面需要判断“可配置的模型来源”，而不是阅读系统猜测。保持鎏金账房的现有弹窗、密度、按钮和文字层级：不新增页面、卡片或移动端适配。

- 弹窗标题不再使用“预设模型”。
- 无远程枚举时显示“当前厂商未开放远程模型枚举，暂无可选模型”，且数量为 `0 / 0`。
- 已选模型区继续只展示运营人员已经保存的模型；无已选模型时保留既有说明。
- 检测成功时，页面显示已验证路由别名 `onekeytoken/auto`，并可由运营人员显式加入路由目录；网关下游实际模型只显示为诊断信息，不能保存为目录项。检测本身仍不自动保存。

## 验收标准

1. OneKeyToken `/models/fetch` 返回空模型集合与 `catalogSource: unavailable`，不会返回 `onekeytoken/auto`、`deepseek-chat`、`qwen3.5-flash`。
2. OneKeyToken 检测成功的模型计数和样例均为零，不把本地名称作为厂商模型能力反馈。
3. 前端点击 OneKeyToken“全部模型”时显示 0/0 的厂商未开放枚举空态，不出现“预设模型”或三个本地模型名称。
4. 已保存的模型选择不被此次改动删除；远程可枚举厂商行为不回归。
5. 后端集成测试和前端定向测试覆盖上述边界，桌面端检查覆盖空态、加载和既有已选模型显示。
6. `validatedModel` 只有在 `onekeytoken/auto` 真实检测成功后才允许显式加入；`resolvedModel` 不得加入目录、设为场景路由或计入远程目录数量。

## 非目标

- 不新增 OneKeyToken 运行时模型发现协议、计费、路由或自动保存行为。
- 不猜测、硬编码或探测厂商未公开的模型列表。
