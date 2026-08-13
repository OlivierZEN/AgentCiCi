---
task_id: TASK-298
feature_id: FEAT-179
status: review
priority: critical
owner_role: fullstack-agent
claimed_by: codex
---

# TASK-298 - 场景模型能力过滤与推荐说明

## 范围

- 厂商目录允许平台运营先选择已拉取模型；能力确认不再依赖厂商文档、HTTPS 地址或证据引用。
- 每个场景只返回能力与协议兼容的已选模型，无法确认能力的模型不进入候选。
- 路由写入在服务端重复校验；路由页面说明适用能力、推荐原则及空状态处置。
- 人工确认只选择能力并保存操作者与时间，写入平台审计；仅人工确认可撤销，撤销后模型立即退出场景候选。

## 验收

- Embedding、OCR、实时/文件 ASR、代码解释器、联网搜索和网页抓取不显示不兼容模型。
- 直接调用路由写接口无法写入不兼容或能力未知的模型。
- 推荐候选与说明来自服务端场景定义，不由前端推断。
- 全部模型可直接加入平台目录，不要求先确认能力；人工确认只要求至少选择一项能力，确认与撤销均有 API/审计测试。
- 定向测试、构建、桌面端路由检查和本地开发环境发布通过；UAT/生产不修改。

## 验证结果

- 已移除厂商文档、HTTPS 校验、证据引用和对应页面展示；目录阶段允许直接选择未确认能力的模型，人工确认只提交模型与能力。
- 截图暴露网关 HTML 被前端直接解析为 JSON；确认和撤销现在显式请求 JSON，并把非 JSON 响应转成版本一致性提示，不再展示 `Unexpected token`。
- 后端 `mvn -q -DskipTests compile` 与 `mvn -q -DskipTests package`、前端定向 6 项与 production build 通过。
- Spring 集成仍在应用上下文创建前被既有 `agentcici_test` Flyway V81 checksum 漂移阻断；未执行 repair 或修改历史迁移。
- backend/frontend 已从本地 `main@1979c6291dbf` 构建并发布为 `2.8.61-dev.1979c62`；两容器 healthy/restart=0，页面制品含“可直接加入平台目录，能力在后续确认”，`/platform/models` 返回 200、匿名模型 API 返回 401，完整 `./stack verify` 通过。
- 本轮仅重建 frontend：`main@4da7a3b0c3f7`，版本 `2.8.61-dev.4da7a3b`；backend 为同一主线已有 `2.8.61-dev.b8bf4d3`，能力 API 匿名回读为 `401 application/json`，两容器 healthy/restart=0，完整 `./stack verify` 通过。

## 下一步

- 使用受权平台会话完成目录先选模型、人工确认/撤销和场景候选失效的业务验收；UAT 仍需确认实际启用模型能力并配置每个场景后验收。
