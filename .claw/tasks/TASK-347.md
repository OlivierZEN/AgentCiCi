---
kind: task-status
task_id: TASK-347
feature_id: FEAT-204
status: in_progress
priority: critical
owner_role: fullstack-agent
claimed_by: codex
spec_path: docs/specs/FEAT-204-web-widget-publish-channel.md
updated_at: 2026-09-01T06:12:03Z
updated_by: codex
---

# TASK-347 - Web 浮窗真实流式与首字延迟修复

## 已验证故障

- UAT 售前浮窗一次普通咨询总耗时 `60.328s`，其中无工具结果的模型规划耗时 `26.980s`，最终生成耗时 `31.498s`。
- 模型供应商增量只追加到服务端内存，完整回答经过整段门禁后才按 `18 字 / 18ms` 人工分片发送；当前属于 buffered response over SSE，不是真实端到端流式。
- Nginx 已关闭 `/embed/v1/` buffering，Embed 页面也逐事件更新，根因位于服务端编排和输出门禁。

## 范围

- 普通咨询在没有外部事实或明确工具意图时由确定性路由进入 DIRECT，跳过无效工具规划模型调用。
- 模型最终生成通过安全增量管道按完整行/句实时发送；租户自定义整段安全规则或研发交付写回执门禁存在时明确降级为 buffered，不模拟打字。
- Trace/SSE phase 记录输出模式、供应商首增量和客户端首 delta 时延。
- SSE/Trace 保留工具判断、检索、生成和整段校验阶段；受信 `page` 嵌入可展示阶段，公开 Website 浮窗只显示无文字等待动效，不能向访客暴露内部编排信息。

## 完成条件

- [x] 普通售前咨询只调用一次模型，供应商首增量在模型完成前进入 SSE。
- [x] 真实增量拼接、持久化正文和安全处理后的最终正文一致，`done` 仅在全部 delta 之后发送一次。
- [x] 自定义整段规则和研发交付写回执路径保持失败关闭，使用 `buffered` phase 且不使用延时模拟分片。
- [x] 后端/前端聚焦测试、全量前端测试、production build、backend package 和本地正式入口通过。
- [x] 任务提交独立进入本地 `main`；UAT 只在新候选、备份和回滚门禁完整后发布。

## 完成证据

- 实现 `19080005f847` 已进入本地 `main`：普通咨询确定性跳过无结果工具规划；最终模型 delta 经安全句段门禁直接进入 SSE；整段规则与研发写回执显式降级为单 delta `buffered`；删除固定字数和 sleep 模拟分片。
- 后端 7 个聚焦测试类、backend package、前端 60 文件/333 项、production build 和 `git diff --check` 通过；默认数据库不可用的 `AdminBillingIntegrationTest` 未记为通过。
- 本地 backend/frontend 运行 `2.8.68-dev.1908000 / 19080005f847`，两容器 healthy/restart=0；真实 website Trace 为单模型、零工具、`outputMode=streaming`，供应商/客户端首 delta 为 `8.246s / 8.320s`，总耗时 `10.385s`。
- 浏览器真实浮窗在发送后约 `285ms` 显示“正在理解问题”；本轮首段 `14.749s`、完成 `18.616s`，中间约 `3.867s` 多次增长，console error/warning=0。

## 用户复核反馈

- 2026-09-01：用户截图确认 Website 浮窗把“正在选择所需工具”等内部 phase 当作助手消息展示；窄浮窗内文案逐字换行，既暴露内部执行细节又破坏对话布局。
- 过滤修复聚焦测试 `8/8`、前端全量 `60 files / 334 tests`、production build 与 `git diff --check` 通过；build 仅保留既有大 chunk warning。

## 下一步

- 提交本地 `main`，从该提交构建并最小替换 local-stack frontend；完成 `cici.localhost` 浮窗 DOM、截图、健康、restart 和版本指纹复测。UAT 不在本次授权范围。
