---
kind: task-status
task_id: TASK-315
feature_id: FEAT-192
status: review
priority: critical
owner_role: backend-agent
claimed_by: codex
updated_at: 2026-08-18T02:05:50Z
updated_by: codex
---

# TASK-315 - 产品经理结构化语义判定与标准输出

## 范围

- 移除研发交付创建、事实查询和转派草案的自然语言关键词/正则路由。
- 使用当前模型的强制结构化函数调用完成完整语义判定。
- 服务端按固定模板输出创建、删除、转派、取消和澄清结果。
- 固定确认协议、权限、Semattice Tool 和写后回读保持确定性治理。
- 回执门禁不再依赖用户问题关键词。
- 完成定向测试、后端 package、本地 main 和 `cici.localhost` 验证。

## 完成条件

- 显式、隐式和否定表达由结构化模型结果决定，不由用户文本规则决定。
- 同一结构化结果始终生成同一草案和可执行确认口令。
- 无真实回执的成功声明稳定阻断，非交付答案不误伤。
- 缺少结构化标记的旧自由文本草案失败关闭。
- 本地环境和 UAT 候选均可追溯到远程 `main` 冻结提交；生产不修改。

## 当前证据

- 代码审计确认旧创建/查询/转派草案及回执启用条件存在自然语言规则。
- 新结构化判定服务、固定模板和回执门禁已完成初步实现。
- 用户首次页面验收证明思考模型拒绝命名 `tool_choice`，Trace 为 0 input/output Token；已改为唯一工具 `auto` 的思考判定，缺少协议时关闭思考做一次命名工具重试，并只要求动作核心字段。
- 定向 8 个测试类共 78 项已通过，包含隐式创建、否定表达、固定格式、低置信度澄清、思考模型工具兼容与协议重试、字段别名规范化、二次查询工具裁剪、确认写入回归和非研发业务非误伤。
- 后端 production package 与 `git diff --check` 已通过；后端全量套件受本机共享 PostgreSQL 不可用阻断，未声明全量通过。
- 提交 `f121d20c`、`cc4312ed` 已进入本地 `main`；backend/frontend 运行 `2.8.61-dev.cc4312e`，镜像 revision 一致、healthy/restart=0，内部版本与 health 回读、正式入口 200 和完整 `./stack verify` 均通过。
- 真实 Provider 非写入探测中，隐含创建返回 `CREATE_DRAFT/PROJECT/CCSales 智能应用`，否定解释返回 `OTHER`；已登录 Chrome 回读新版本，但未代用户发送页面消息。固定草案格式仍待用户验收，UAT/生产未修改。
- 实现、修复和验证记录已随本地 `main@a25cce7e` 非强制快进推送到 `origin/main`；未创建 tag，未发布 UAT/生产。
- UAT `2.8.61-beta.28 / 242074e72a9e` 已完成技术发布：backend/frontend digest 分别为 `sha256:99851d50ad5f9c6ae72b02edf23a1f2949b60f2842179b91becb1eb0f4801c10`、`sha256:e1231cd5366c6b4528569e665436374d8f28dde185f6ca018d5332d321c04953`；完整备份校验、V121、六容器健康、版本回读、JSON 401、公开 smoke 与稳定日志通过。
- 本轮未代用户发送产品经理对话或确认创建，未新增 Semattice 业务记录；隐含创建、否定表达、固定草案与确认后真实回执仍待已登录 UAT 用户验收。生产未修改。
