---
kind: task-status
task_id: TASK-352
assignee: codex
owner_role: fullstack-agent
status: in_progress
branch: main
pr_url: n/a
spec_path: docs/specs/FEAT-062-platform-model-provider-governance.md
updated_at: 2026-09-01T23:32:53Z
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
- 对话 `voice-asr` 恢复固定 `paraformer-realtime-v2` 适配器，但 API Key 从模型厂商治理中的阿里云记录读取，不回退到早期部署文件里的 Key。
- AI 听记和嵌入听记显式请求讯飞且开启发言人区分；普通对话及其他语音输入固定请求阿里云。
- 让实时语音 WebSocket 接受与普通受保护 API 相同的 OACT HUMAN 身份，并把 Bearer 从 URL 查询参数迁移到建连后的认证帧，避免访问日志记录完整用户令牌。
- 完成自动化、构建、本地 `main` 制品和正式入口验证。

## Done When

- [x] 官方讯飞根 URL 在保存、读取、校验和运行时规范化。
- [x] 收到讯飞上游 `started` 后才申请麦克风和发送音频。
- [x] 官方 `data.ls=true` 空最后帧仍触发一次 `finished`。
- [x] 错误、上游关闭和停止超时均不永久停留在 recording/stopping。
- [x] 对话听写不再被讯飞场景路由覆盖，AI 听记以独立治理场景进入讯飞并携带 `role_type=2`。
- [x] 历史 `voice-asr=iflytek_asr` 路由迁移 SQL 在本地数据库事务中验证通过且已回滚测试事务。
- [x] 实时 ASR 上游 WebSocket 显式使用 HTTP/1.1 Upgrade。
- [x] 对话听写使用治理中的阿里云凭据与固定 `paraformer-realtime-v2` 协议，不读早期静态 Key。
- [x] 后端聚焦测试/package、前端聚焦/全量/build 通过。
- [x] 修复提交进入本地 `main`，backend/frontend 从该提交运行且健康。
- [x] 使用合成音频完成阿里云与讯飞真实上游转写、发言人标签与结束技术探测。
- [ ] 真实 OACT 登录会话完成 WebSocket 帧内认证，并分别收到阿里云与讯飞上游 `started`。
- [ ] HUMAN 使用真实麦克风确认 AI 听记和对话框实时听写。

## Handoff

- 当前数据库的 `iflytek_asr.realtimeUrl` 是官方主机根路径；不直接改数据库，运行时和治理读写层负责向后兼容。
- 真实麦克风涉及浏览器权限和用户语音，仅在用户明确确认后执行；技术探测优先使用不含敏感内容的合成音频。
- 对话听写的阿里云凭据从模型厂商治理的 `aliyun-bailian` 记录读取，固定适配器路由不复制 Secret。
- 实现提交 `d32a710fe518 + 80f720730cd3 + 7ced552aa661 + 6f3f5def7ca3` 已进入本地 `main`；backend/frontend 均运行 `2.8.68-dev.6f3f5de / 6f3f5def7ca3`、healthy/restart=0，V130/V131 success。
- 2026-09-02 HUMAN 真实登录截图驳回了此前合成探测：页面 OACT 为 RS256 `ecosystem_user`，而 `/ws/asr` 只调用旧 HS256 `JwtService.parse()`，在厂商路由前即以 `invalid token` 关闭，因此阿里云和讯飞同时失败。任务恢复 `in_progress`，合成探测不再作为浏览器身份链路证据。
