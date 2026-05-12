---
kind: feature-spec
version: 1
updated_at: 2026-05-12T02:22:42Z
updated_by: ai
status: implemented
owner_role: fullstack-product-assistant
---

# FEAT-029 - 智能体会议纪要实时听记

## Goal

当员工在助手工作台对智能体说“开始会议纪要”时，当前会话右侧滑出实时听记面板，浏览器自动请求麦克风权限，后端通过讯飞实时语音转写能力接收 16k PCM 音频流并开启说话人分离。面板实时显示不同发言人的发言，会议结束后生成结构化会议纪要。

## Scope

- 前台助手工作台 `/`：
  - 识别用户输入“开始会议纪要”或“开始会议记录”等同义触发语。
  - 打开右侧侧滑听记面板，自动启动麦克风。
  - 展示录音状态、实时转写、发言人标签、错误状态和会议纪要。
  - 提供“结束并生成纪要”动作。
- 后端：
  - 扩展 `/ws/asr`，在会议模式下可选择讯飞 provider。
  - 讯飞 provider 支持 `role_type=2` 角色分离参数，凭证优先来自组织级集成应用配置。
  - 新增会议纪要生成 API，基于转写文本输出固定结构的纪要。
  - 会议结束生成纪要时必须显式激活平台标准技能 `ai-meeting-notetaker`（AI 听记），模型 prompt 由技能体系装配后再生成纪要。
- 管理后台 `/admin/integrations`：
  - 新增内置“讯飞实时转写”集成应用。
  - 组织管理员可配置 App ID、Access Key ID、Access Key Secret、Realtime URL、语言和领域参数。
  - Access Key Secret 必须服务端加密存储，前端只展示遮罩。
- 保留现有普通语音输入链路兼容性，默认仍可使用现有阿里云实时识别。

## Out Of Scope

- 第一版不保存完整会议音频。
- 第一版不做声纹注册和跨会议发言人身份绑定，讯飞返回的角色只映射为“发言人 1/2/3”。
- 第一版不实现多人客户端同步编辑、会议日程绑定或导出到飞书/邮件。
- 第一版不修改全局设计 token。

## Product UX

工作台是长时间使用的产品界面，听记面板必须沿用 `鎏金账房`：暖象牙表面、墨色文字、香槟金结构线、13px 产品文本。面板内部不使用行卡片、chip 背景、选中背景或阴影。不同发言人通过文本标签、编号和必要的 1px 分隔线区分。

触发后：

1. 当前输入不发送到普通聊天模型。
2. 工作台消息区补一条用户触发气泡和一条助手确认气泡。
3. 右侧滑出听记面板并自动开始录音。
4. 转写过程中按发言人实时追加内容。
5. 用户点击结束后，停止音频流并调用纪要生成接口。
6. 纪要生成完成后显示日期、参与人、主题、摘要、行动项、决策和开放问题。

## Backend Contract

### WebSocket

`/ws/asr?token=<jwt>&provider=iflytek&speakerDiarization=true`

Client text messages:

- `{"type":"start","sampleRate":16000,"provider":"iflytek","speakerDiarization":true}`
- `{"type":"stop"}`

Client binary messages:

- 16kHz, 16bit, mono, little-endian PCM frames.

Server events:

- `status`: connection or provider status.
- `partial`: interim transcript.
- `final`: stable transcript segment.
- `finished`: provider finished.
- `error`: provider or configuration error.

When provider returns speaker or role information, events include `speakerId` and `speakerName`.

### Summary API

`POST /ai/meeting-minutes/summary`

Request:

```json
{
  "title": "会议纪要",
  "transcript": [
    { "speakerId": "1", "speakerName": "发言人 1", "text": "今天先看售后 Agent...", "startMs": 0, "endMs": 3200 }
  ]
}
```

Response:

```json
{
  "summary": "## Meeting Summary\n...",
  "skillCode": "ai-meeting-notetaker",
  "skillName": "AI 听记",
  "segmentCount": 1
}
```

## Configuration

产品配置入口是 `管理后台 -> 集成应用 -> 讯飞实时转写`：

- `appId`
- `accessKeyId`
- `accessKeySecret`
- `realtimeUrl`，默认 `wss://office-api-ast-dx.iflyaisol.com/ast/communicate/v1`
- `lang`，默认 `autodialect`
- `domain`，默认 `com`

服务端仍保留开发和部署兜底配置；运行时优先读取当前组织的 `integration_app(app_code="iflytek_asr")`，组织级集成未保存时才回落到 yml：

```yaml
app:
  voice:
    iflytek:
      enabled: true
      app-id: ""
      access-key-id: ""
      access-key-secret: ""
      realtime-url: "wss://office-api-ast-dx.iflyaisol.com/ast/communicate/v1"
      lang: "autodialect"
      domain: "com"
```

If credentials are missing, the UI must show a clear setup error and keep the panel open.

## Acceptance Criteria

- 输入“开始会议纪要”不会进入普通聊天问答，而是打开听记侧滑面板并启动麦克风。
- 讯飞配置存在时，后端连接讯飞实时转写并请求角色分离。
- 管理员可在“集成应用 -> 讯飞实时转写”维护组织级讯飞凭证，Secret 不明文回显。
- 讯飞结果包含角色时，前端按发言人分组展示；无角色时回退为“发言人 1”。
- 点击结束后调用后端纪要生成 API，并在面板中展示 Markdown 纪要。
- 会议纪要生成 API 必须返回本轮显式调用的 AI 听记技能标识，且模型 system prompt 必须包含该技能的 prompt fragment 与 output contract。
- 普通语音输入按钮不受会议听记改动影响。
- 前端构建通过；后端编译通过。

## Implementation Notes

- 讯飞实时转写大模型文档要求 16k、16bit、mono、pcm，并支持 `role_type=2` 开启实时角色分离。
- WebSocket 结束时向讯飞发送 `{"end": true, "sessionId": "<sessionId>"}`。
- 纪要生成第一版直接调用模型生成固定结构内容，不引入新的持久化表。

## Progress

- 2026-05-11T13:15:23Z 规格创建，进入实现。
- 2026-05-11T13:25:57Z 第一版实现完成：工作台触发语、右侧实时听记面板、讯飞 provider WebSocket 路径、角色分离参数、纪要生成 API、本地编译和桌面/移动触发检查均已完成。真实讯飞云端转写待凭证和麦克风授权后 smoke。
- 2026-05-11T13:42:26Z 新增“讯飞实时转写”组织级集成应用配置入口；后端 ASR 运行时优先读取 `integration_app(app_code="iflytek_asr")`，Access Key Secret 加密存储并遮罩回显；员工侧缺配置错误指向管理后台配置入口。
- 2026-05-11T13:57:15Z 在用户填完讯飞配置后执行真实配置 smoke：配置已启用、Secret 已加密遮罩，但当前保存 URL 为 `wss://spark-api.xf-yun.com/v4.0/chat`，探测返回 `401 Unauthorized`；用 FEAT-029 默认 AST 实时转写地址探测返回 `35010 AccessKeyId Not Exists`。真实云端转写仍未通过，需要换成讯飞 AST 实时转写服务对应的 AccessKey 凭证和地址后复测。
- 2026-05-11T15:11:24Z 用户已将 URL 改为 `wss://office-api-ast-dx.iflyaisol.com/ast/communicate/v1` 后复测：地址、启用状态和 Secret 加密均确认正确；但讯飞 AST 握手对官方最小参数与带 `role_type=2/pd=com` 参数均返回 `35010 AccessKeyId Not Exists`。当前阻塞点从 URL 错误转为 APIKey/AccessKeyId 未被实时语音转写大模型服务接受或该 App 未开通对应服务。
- 2026-05-11T15:18:28Z 用户确认实时语音转写大模型额度已开通后再次复测：本地保存配置未变化且仍正确指向 AST 地址；后端 `/ws/asr` 与原始讯飞握手仍返回相同的 `35010 AccessKeyId Not Exists`。下一步需核对管理后台保存的 APIKey/APISecret 是否来自已开通额度的同一个 App，或等待/联系讯飞确认服务绑定。
- 2026-05-11T15:45:53Z 用户提供正确 AppID/APIKey/APISecret 后，真实讯飞云端转写 smoke 已通过：原始握手 `101 Switching Protocols`，后端 `/ws/asr?provider=iflytek&speakerDiarization=true` 发送生成的 16k PCM 音频后收到 partial/final 转写及 speaker 元数据。同步修复后端等待上游 `started`、`data.cn.st.rt[]` 解析、Java WebSocket backpressure/binary 串行发送和前端 ASR ready gate。
- 2026-05-11T15:58:32Z 修复触发语口语表达漏识别：原实现只接受精确“开始会议纪要/记录/听记”等短语，用户说“开始进行会议纪要”不会打开侧滑面板。前端已新增 `meetingMinutesCommand` helper，支持“开始进行会议纪要”“开始做会议记录”“帮我开始做会议记录吧”“开启实时会议听记”等自然表达，并通过定向 Vitest 覆盖解释型问题不误触发。
- 2026-05-11T16:12:15Z 按用户要求调整会议纪要面板信息结构：桌面端加宽 drawer，并将“实时转写”和“AI 会议纪要”改为左右双栏，左侧承载 live transcript，右侧承载 summary Markdown；窄屏仍回落为上下单列，继续遵守 `鎏金账房` 面板内无背景框、无选中填充、仅 1px 分隔线的产品规则。
- 2026-05-11T16:23:27Z 修复实时转写 speaker 识别和连续段落聚合：后端新增讯飞结果 parser，按 `cn.st.rt[]` 子段分别提取文本和 `rl`，避免外层默认角色覆盖子段角色；前端新增 transcript helper，将同一 speaker 的连续 final 片段合并为一个段落，speaker 变化时才新开段落，并将零基 speaker 友好显示为“发言人 1/2”。验证通过前端定向测试、后端 parser 测试、前端构建、后端编译和目标 diff check。
- 2026-05-11T23:30:23Z 根据用户复测截图继续修正 speaker 语义：讯飞官方文档说明角色分离标识在词级 `data.cn.st.rt.ws.cw.rl`，`rl=0` 表示继续上一说话人，`rl=1/2/3...` 表示切换到对应说话人。后端 parser 已改为词级读取 `cw.rl` 并在 WebSocket 会话内保存 active speaker；前端 speaker 默认值和显示改为符合讯飞 1-based 编号。已完成前后端定向测试、前端构建、后端编译、target diff check，并重启本地 8080/5173 服务。
- 2026-05-11T23:48:39Z 按用户截图补充听记面板交互：实时转写区新增自动滚动到最新段落；发言人标签支持鼠标双击进入内联编辑，键盘 Enter/F2 也可进入，Enter/blur 保存、Escape 取消；保存后同步同一 `speakerId` 的历史段落、当前识别中 partial 和后续转写事件。已完成前端定向测试、前端构建、target diff check，并用 in-app Browser 检查桌面 1280x800 与移动 390x844 drawer 布局；本轮浏览器麦克风权限为 `Permission denied`，未验证真实音频转写行。
- 2026-05-12T01:18:36Z 修复发言人内联编辑只能输入一个字符：编辑框自动 focus/select effect 从依赖整个 `meetingSpeakerEdit` 对象改为只依赖编辑目标 `speakerId` + `lineId`，避免输入过程中每次 `value` 变化都重新全选并覆盖前一个字符。已完成前端定向测试、前端构建和 target diff check。
- 2026-05-12T01:38:54Z 按用户要求将会议结束后的 AI 纪要生成改为显式调用 `ai-meeting-notetaker`（AI 听记）平台标准技能：后端新增该内置技能并默认挂到 `cici-system`，`MeetingMinutesService` 通过 `SkillPromptAssembler` 装配技能上下文后再调用模型，接口响应回传 `skillCode/skillName`；前端生成中与完成提示同步展示 AI 听记技能调用语义。
- 2026-05-12T02:22:42Z 修复 AI 听记技能化纪要生成仍返回 `Aliyun API key is not configured.` 的问题：根因是 `MeetingMinutesService` 虽已装配 AI 听记技能，但模型调用仍使用 `AliyunBailianClient` 构造器环境变量 key，未读取当前组织的模型厂商配置。现已改为先用 `ModelRouterService.route(orgId, "chat")` 取得组织聊天模型，再用 `ModelProviderService.credentialsForProvider` 取得 provider baseUrl/apiKey 后调用模型；本地 `demo-org` 真实 `/ai/meeting-minutes/summary` smoke 已返回 `Meeting Summary`，并包含 `skillCode=ai-meeting-notetaker`、`skillName=AI 听记`。
