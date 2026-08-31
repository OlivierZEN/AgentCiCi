---
kind: task-status
task_id: TASK-347
feature_id: FEAT-204
status: in_progress
priority: critical
owner_role: fullstack-agent
claimed_by: codex
spec_path: docs/specs/FEAT-204-web-widget-publish-channel.md
updated_at: 2026-08-31T04:34:00Z
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
- Embed 忙碌气泡展示工具判断、检索、生成和整段校验阶段，收到首段正文后停止等待文案。

## 完成条件

- [ ] 普通售前咨询只调用一次模型，供应商首增量在模型完成前进入 SSE。
- [ ] 真实增量拼接、持久化正文和安全处理后的最终正文一致，`done` 仅在全部 delta 之后发送一次。
- [ ] 自定义整段规则和研发交付写回执路径保持失败关闭，使用 `buffered` phase 且不使用延时模拟分片。
- [ ] 后端/前端聚焦测试、全量前端测试、production build、backend package 和本地正式入口通过。
- [ ] 任务提交独立进入本地 `main`；UAT 只在新候选、备份和回滚门禁完整后发布。

## 下一步

- 先补失败测试，再实现确定性直答路由、安全增量输出和 Embed 阶段提示。
