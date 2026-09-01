---
kind: task-status
task_id: TASK-352
assignee: codex
owner_role: fullstack-agent
status: in_progress
branch: main
pr_url: n/a
spec_path: docs/specs/FEAT-062-platform-model-provider-governance.md
updated_at: 2026-09-01T14:51:11Z
updated_by: codex
---

# TASK-352 - 实时听写厂商路由与结束状态收敛

## Goal

修复两条独立实时听写链路：对话框保持阿里云实时 ASR，AI 听记使用讯飞实时 ASR 并区分发言人；同时确保上游就绪、实时结果和结束状态可确定收敛。

## Scope

- 兼容平台历史保存的讯飞官方主机根 URL，运行时自动补齐协议路径。
- 修复讯飞最后帧解析、异常关闭和结束事件转发。
- 将两家实时 ASR 共用的上游 WebSocket 客户端锁定为 HTTP/1.1 Upgrade，避免协议升级前失败。
- 修复共享前端 hook 的 ready 门禁与一次性完成回调。
- 将平台场景拆分为对话 `voice-asr` 与 AI 听记 `meeting-realtime-asr`，历史讯飞选择自动迁移。
- AI 听记和嵌入听记显式请求讯飞且开启发言人区分；普通对话及其他语音输入固定请求阿里云。
- 完成自动化、构建、本地 `main` 制品和正式入口验证。

## Done When

- [x] 官方讯飞根 URL 在保存、读取、校验和运行时规范化。
- [x] 收到讯飞上游 `started` 后才申请麦克风和发送音频。
- [x] 官方 `data.ls=true` 空最后帧仍触发一次 `finished`。
- [x] 错误、上游关闭和停止超时均不永久停留在 recording/stopping。
- [x] 对话听写不再被讯飞场景路由覆盖，AI 听记以独立治理场景进入讯飞并携带 `role_type=2`。
- [x] 历史 `voice-asr=iflytek_asr` 路由迁移 SQL 在本地数据库事务中验证通过且已回滚测试事务。
- [x] 实时 ASR 上游 WebSocket 显式使用 HTTP/1.1 Upgrade。
- [x] 后端聚焦测试/package、前端聚焦/全量/build 通过。
- [ ] 修复提交进入本地 `main`，backend/frontend 从该提交运行且健康。
- [ ] 使用合成音频完成真实讯飞上游转写与结束技术探测。
- [ ] HUMAN 使用真实麦克风确认 AI 听记和对话框实时听写。

## Handoff

- 当前数据库的 `iflytek_asr.realtimeUrl` 是官方主机根路径；不直接改数据库，运行时和治理读写层负责向后兼容。
- 真实麦克风涉及浏览器权限和用户语音，仅在用户明确确认后执行；技术探测优先使用不含敏感内容的合成音频。
- 对话听写的阿里云凭据继续由既有部署配置提供，本任务不复制到新的模型路由记录。
