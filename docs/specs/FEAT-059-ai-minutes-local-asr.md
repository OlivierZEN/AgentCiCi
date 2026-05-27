---
kind: feature-spec
feature_id: FEAT-059
title: AI minutes local ASR provider
status: draft
owner_role: shared
task_ids: TASK-141
related_decisions: none
related_issues: none
updated_at: 2026-05-27T00:00:00Z
updated_by: MANAGER-001
---

# FEAT-059 - AI 听记本地 ASR 方案

## 背景与目标

- AI 听记当前已有两条云端语音识别链路：实时听记通过讯飞 provider，录音文件上传通过阿里云百炼 Fun-ASR 文件转写。
- 新需求是在不把音频发送到云端模型的前提下，支持企业私有化或本地部署 ASR，用于客户拜访、会议录音和内网敏感场景。
- 本功能目标是在现有 AI 听记中增加 `local` ASR provider，复用现有 transcript、发言人编辑、AI 纪要生成和后续 CRM/待办建议链路。

## 选型结论

默认推荐：`FunASR 本地服务 + Paraformer-zh + fsmn-vad + ct-punc + cam++`。

选择理由：

- FunASR 官方定位包含 ASR、VAD、标点、说话人分离，并给出 `paraformer-zh + fsmn-vad + ct-punc + cam++` 的会议音频 pipeline 示例，输出可直接映射为带 speaker 的句子级 transcript。
- FunASR 文档支持本地 OpenAI-compatible transcription endpoint，可以作为独立 Python/FastAPI ASR sidecar 接入 Java 后端，不需要把模型运行时塞进 Spring Boot。
- 现有百炼文件转写已经使用 Fun-ASR 语义，产品和测试数据可以复用；本地版只替换模型调用位置，用户体验不变。
- Paraformer 对中文会议、客户拜访和中英混杂业务词更合适；SenseVoiceSmall 可作为轻量多语种/快速试跑模型；Whisper 系列作为跨语种兜底，不作为中文会议默认。

备选模型：

| 方案 | 定位 | 优点 | 限制 | 结论 |
| --- | --- | --- | --- | --- |
| FunASR Paraformer-zh + VAD/Punc/CAM++ | 中文会议默认 | 中文表现、标点、说话人分离和热词链路完整；CPU/GPU 都可部署 | Python sidecar 运维、模型包体和首启耗时需要治理 | 第一版默认 |
| FunASR SenseVoiceSmall | 多语种和轻量识别 | 支持中/粤/英/日/韩等语音理解，官方宣称低延迟，并支持情绪/事件能力 | 会议说话人分离仍建议与 VAD/SPK pipeline 组合验证 | 可配置模型 |
| sherpa-onnx Paraformer | 边缘/离线设备 | ONNX、C/C++/Java API 友好，适合无 Python 或低资源部署 | 需要自行拼装标点、说话人分离和服务协议 | 后续企业离线包选项 |
| faster-whisper + WhisperX/pyannote | 多语种转写和字幕 | Whisper 生态成熟，WhisperX 有词级时间戳和 diarization | 中文会议热词/标点不如 Paraformer 直观；diarization 依赖额外模型和授权 | 跨语种兜底 |
| whisper.cpp | 极简本地引擎 | C/C++、CPU/Metal 友好，部署轻 | 说话人分离和中文业务优化要另配；实时能力多为切片模拟 | 桌面演示/轻量 fallback |

参考来源：

- FunASR 官方文档：<https://modelscope.github.io/FunASR/>；production-ready ASR、VAD、punctuation、speaker diarization；支持本地 OpenAI-compatible transcription endpoint。
- SenseVoiceSmall 模型卡：<https://huggingface.co/FunAudioLLM/SenseVoiceSmall>；多语种 ASR、语种识别、情绪识别、音频事件检测，支持本地路径加载。
- faster-whisper 项目：<https://github.com/SYSTRAN/faster-whisper>；基于 CTranslate2 的 Whisper 重实现，支持 CPU/GPU int8；社区集成 WhisperX diarization。
- WhisperX 项目：<https://github.com/m-bain/whisperX>；提供词级时间戳和 diarization 工作流。
- sherpa-onnx 文档：<https://k2-fsa.github.io/sherpa/onnx/>；提供离线 ASR 和 speaker diarization ONNX 模型/API。
- whisper.cpp 项目：<https://github.com/ggml-org/whisper.cpp>；Whisper C/C++ 本地实现，支持 Core ML 等本地加速路径。

## 授权与部署运营成本

### 免费边界

FunASR 对本项目推荐链路的“免费”应拆开理解：

- `FunASR` 代码仓库当前为 MIT License，可免费使用、修改和分发，但需保留版权和许可文本。
- 推荐 pipeline 中的 Hugging Face 模型页显示 `funasr/paraformer-zh`、`funasr/fsmn-vad`、`funasr/ct-punc`、`funasr/campplus` 均为 `apache-2.0`，商用友好，但仍需保留 license/notice 并做第三方开源清单。
- `SenseVoiceSmall` 的 Hugging Face 页面显示为 `model-license`，不是标准 OSI license；若用于商业私有化交付，需单独做法务确认，不作为第一版默认模型。
- “免费”只表示没有按调用付给模型厂商的 API 费用；仍然有服务器、GPU/CPU、电力、存储、监控、升级和故障处理成本。

### 云端按量成本参照

以阿里云百炼 2026-05-27 查询到的公开价格为参照：

| 云端能力 | 中国内地输入单价 | 折合每小时音频 | 国际输入单价 | 折合每小时音频 | 备注 |
| --- | ---: | ---: | ---: | ---: | --- |
| `fun-asr` 录音文件识别 | `$0.000032/秒` | `$0.1152/小时` | `$0.000035/秒` | `$0.126/小时` | 文件转写，按音频秒数计费 |
| `fun-asr-realtime` 实时识别 | `$0.000047/秒` | `$0.1692/小时` | `$0.00009/秒` | `$0.324/小时` | 实时听记，按音频秒数计费 |
| `paraformer-v2` 录音文件识别 | `$0.000012/秒` | `$0.0432/小时` | 不适用 | 不适用 | 仅中国内地部署模式 |
| `paraformer-realtime-v2` 实时识别 | `$0.000035/秒` | `$0.126/小时` | 不适用 | 不适用 | 仅中国内地部署模式 |

说明：

- 云端按量的优势是免维护、弹性、启动快；劣势是音频出域、长期费用线性增长、说话人分离/热词/重试可能增加实际成本。
- 本地部署的优势是音频不出内网、单位边际成本随用量下降、可做专有热词和内网交付；劣势是固定成本、运维成本和容量规划。

### 本地部署成本模型

本地方案的月成本可按下面公式估算：

```text
月总成本 = 机器折旧或云主机租金 + 电力/机房 + 运维人力 + 存储/日志/监控 + 升级测试成本
单位转写成本 = 月总成本 / 月音频处理小时数
```

建议按三档部署：

| 档位 | 推荐场景 | 资源形态 | 成本特征 | 适合能力 |
| --- | --- | --- | --- | --- |
| 轻量 CPU | 开发联调、离线文件 POC | 8C/16G 或 16C/32G CPU 机器 | 若复用已有服务器，新增现金成本低；但不作为实时交付推荐形态 | 文件转写、实时接口冒烟 |
| 单 GPU | 企业常规私有化、实时听记交付 | 1 张 16GB 级别 GPU 或等效推理卡 | 有固定硬件/云 GPU 租金；边际成本低 | 实时听记 + 文件转写 |
| GPU 池 | 多租户或高并发 SaaS 私有云 | 多 GPU + 队列 + 横向扩容 | 运维复杂，需要调度、监控、容量水位 | 多组织并发、实时听记、批量任务 |

粗略判断：

- 月音频量低于 `100-300 小时` 且无强隐私要求时，云端按量通常更省心。
- 月音频量达到 `1000 小时+`，或客户明确要求音频不出内网时，本地 ASR 的经济性和合规价值开始明显。
- 若只是开发联调，可用 CPU-only sidecar 跑通接口，但不能作为客户验收口径。
- 因实时转写是必须能力，客户交付和正式验收应按单 GPU 起步，并用真实客户录音测首字延迟、final 延迟、并发和说话人分离质量。

### 综合对比

| 维度 | 云端讯飞/百炼 | 本地 FunASR CPU | 本地 FunASR GPU | faster-whisper/WhisperX | sherpa-onnx |
| --- | --- | --- | --- | --- | --- |
| 授权/模型费用 | 按量付费 | 代码/默认模型免 API 费 | 代码/默认模型免 API 费 | 多数代码免费，模型和 pyannote 需逐项确认 | Apache-2.0 生态友好 |
| 隐私 | 音频发送云端 | 音频留本地 | 音频留本地 | 音频留本地 | 音频留本地 |
| 初始上线 | 最快 | 中等，需要 sidecar | 中等，需要 GPU/驱动 | 中等，diarization 更复杂 | 较高，需要拼服务 |
| 运维复杂度 | 低 | 中 | 中高 | 中高 | 中 |
| 中文会议效果 | 稳定，取决于厂商 | 好，需实测硬件 | 好，吞吐更稳 | 可用但热词和中文会议不占优 | 取决于模型组合 |
| 说话人分离 | 通常可用，可能额外限制 | 可用但需实测 | 可用且更稳 | 依赖 WhisperX/pyannote | 可用但需拼装 |
| 实时体验 | 最稳 | 有延迟压力 | 可交付 | 多为切片模拟 | 可做，但工程量更大 |
| 规模经济 | 线性成本 | 高用量摊薄 | 高用量摊薄明显 | 高用量摊薄 | 高用量摊薄 |

### 产品决策建议

- 第一版仍推荐 `FunASR Paraformer-zh + VAD/Punc/CAM++`，但交付策略改为“实时听记为 P0，文件转写为同 provider 的复用能力”，不得用文件转写替代实时验收。
- 商业报价时不要写“免费 ASR”，应写“本地开源 ASR，无云端 ASR 调用费；客户需承担部署资源与运维成本”。
- 对 SaaS 标准版，保留云端 ASR 更合适；对私有化/信创/敏感客户，提供本地 ASR 作为增购部署能力。
- 本地 ASR 默认 `fallbackProvider=none`；只有客户明确授权音频可出域时，才允许云端兜底。

## 范围

### In Scope

- 新增本地 ASR provider 配置，支持在组织级或部署级选择 `cloud_iflytek`、`cloud_bailian`、`local_funasr`。
- 本地 ASR sidecar 作为独立服务部署，必须提供健康检查、实时 WebSocket 转写接口和文件转写接口。
- AI 听记文件上传支持走本地 provider，返回和 `/ai/meeting-minutes/transcribe-file` 一致的 speaker transcript 数据结构。
- 实时听记必须在 `/ws/asr` 上通过 `provider=local` 路由到本地 ASR sidecar，并输出连续 `partial/final/status/error/finished` 事件；VAD 切片只能作为流式实现策略，不能把完整录音结束后再转写伪装成实时。
- 保留云端 provider 作为可配置兜底；本地 provider 不可用时前端给出明确错误，不静默切云。
- 复用现有 `/ai/meeting-minutes/summary`，本地 ASR 只负责转写，不改变 AI 纪要和 `ai-meeting-notetaker` 技能调用。

### Out Of Scope

- 第一版不训练或微调自有 ASR 模型。
- 第一版不做跨会议声纹注册、实名识别或发言人身份绑定。
- 第一版不保存完整音频文件、历史听记库或对象存储归档。
- 第一版不新增移动端兼容实现、移动端截图或移动端自动化测试。
- 第一版不替换现有讯飞/百炼 provider，只新增可选本地 provider。

## 用户场景

- 私有化客户希望会议录音不出内网，在部署机房完成 ASR 转写，再由现有 AI 听记生成纪要。
- 销售/实施人员上传客户拜访录音，系统在本地识别发言人、生成 transcript，并继续编辑发言人名称。
- 企业部署时没有云端 ASR 凭证，管理员启用本地 ASR 服务后，员工仍可使用“开始会议纪要”实时听记和“导入录音”。
- 本地 ASR 服务未启动、模型缺失或 GPU/CPU 资源不足时，前端保留面板并展示设置/联系管理员提示。

## 现状与约束

- 现有实时听记入口是 `/ws/asr`，通过 `provider=iflytek` 走讯飞，否则默认阿里云实时 provider。
- 现有文件转写入口是 `POST /ai/meeting-minutes/transcribe-file`，当前直接调用 `AliyunAsrService.transcribeMeetingFile`。
- 前端 `MeetingMinutesPanel` 已有导入录音、transcript 展示、speaker 内联编辑、下载转写和生成纪要能力。
- 本地 ASR 应复用现有 transcript segment：`speakerId`、`speakerName`、`text`、`startMs`、`endMs`。
- 本地模型服务与业务后端分进程部署，避免 Python/模型依赖污染 Spring Boot 打包、启动和内存模型。

## 方案设计

### 总体架构

```text
Browser
  |  /ws/asr?provider=local 或 /ai/meeting-minutes/transcribe-file?provider=local
Spring Boot backend
  |  LocalAsrProviderClient
Local ASR sidecar (FastAPI/FunASR)
  |  local model files
FunASR Paraformer/VAD/Punc/CAM++
```

### 新增基础设施

基于当前系统架构，需要新增一个独立的本地 ASR 推理服务，建议使用 Python 服务，而不是把 FunASR 直接嵌入 Spring Boot。

新增组件：

- `local-asr` Python sidecar
  - 运行 FunASR/Paraformer 推理，提供 `WS /v1/audio/stream` 和 `POST /v1/audio/transcriptions`。
  - 技术形态建议为 FastAPI + Uvicorn，或 FunASR 官方 `funasr-server` 可满足协议时优先复用。
  - 作为独立 Docker image 发布，例如 `cici-local-asr:${CICI_IMAGE_TAG}`。
- 模型目录
  - 只读挂载 `/models/funasr`，包含 `paraformer-zh-streaming`、`paraformer-zh`、`fsmn-vad`、`ct-punc`、`campplus`。
  - 生产部署不应在容器启动时临时从公网下载模型；应在镜像构建或交付包准备阶段完成模型下载和 license 清单。
- ASR 临时工作目录
  - 挂载 `/app/tmp/asr`，存放短生命周期 chunk、上传文件和异步 job 中间结果。
  - 需要定时清理，默认不长期保存音频。
- GPU runtime
  - 实时交付建议启用 NVIDIA Container Runtime 或客户环境等效推理卡 runtime。
  - `docker-compose.acr.yml` 需要新增 `local-asr` 服务，并通过 profile 或环境变量控制 GPU 资源声明。
- 后端配置
  - `APP_VOICE_LOCAL_ENABLED`
  - `APP_VOICE_LOCAL_BASE_URL=http://local-asr:18080`
  - `APP_VOICE_LOCAL_MODEL=paraformer-zh`
  - `APP_VOICE_LOCAL_REALTIME_ENABLED=true`
  - `APP_VOICE_DEFAULT_PROVIDER=local_funasr`
- 健康检查与 readiness
  - `local-asr /health` 必须区分进程存活、模型已加载、GPU 可用、实时 pipeline 已预热。
  - 后端 `/actuator/health` 可新增 local ASR dependency 状态，但不能因 ASR 未启用而拖垮主应用健康。
- 监控与日志
  - 指标至少包含 active sessions、audio seconds processed、first partial latency、final latency、queue depth、GPU memory、error count。
  - 日志不得默认落原始音频；可记录 sessionId、orgId、时长、模型、错误码和延迟。
- 发布与运维
  - `scripts/release-acr.sh` 需要把 `cici-local-asr` 纳入统一版本 tag。
  - `deploy/docker-compose.acr.yml` 和本地 `docker-compose.yml` 需要增加可选 `local-asr` 服务。
  - `docs/production-release-runbook.md` 需要补充本地 ASR 模型准备、GPU 检查、实时 smoke 和回滚步骤。

为什么需要 Python 服务：

- FunASR 生态、模型加载、音频预处理和 GPU 推理主要围绕 Python/PyTorch 运行；直接嵌入 Java 会引入 JNI/进程管理/依赖冲突，风险高。
- 当前 Spring Boot 后端已经承担租户鉴权、WebSocket、配置和业务 API，继续让它做 provider 网关最合适。
- 独立 sidecar 可以单独扩容、单独绑定 GPU、单独预热模型；ASR 崩溃时不会拖垮主业务后端。
- 后续如果替换为 sherpa-onnx、whisper.cpp 或厂商私有 ASR，只要保持 sidecar 协议不变，后端和前端改动会很小。

Spring Boot 增加 provider 抽象：

- `MeetingAsrProvider`：文件转写接口，输出统一 `MeetingFileTranscriptSegment`。
- `RealtimeAsrProvider`：实时音频接口，输出统一 WebSocket event。
- `AliyunMeetingAsrProvider`：包装当前 `AliyunAsrService`。
- `LocalFunasrMeetingAsrProvider`：HTTP 调用本地 sidecar。
- `IflytekRealtimeAsrProvider`：从当前 `AliyunRealtimeAsrWebSocketHandler` 内拆出讯飞实时逻辑。

### 本地 ASR sidecar

服务建议：

- 镜像名：`agentcici-local-asr`
- 默认端口：`127.0.0.1:18080`
- 默认模型：`paraformer-zh`
- 默认 pipeline：`paraformer-zh`、`fsmn-vad`、`ct-punc`、`cam++`
- 模型目录：`/models/funasr`
- 运行模式：`cpu`、`cuda`、`mps/onnx` 视部署包支持分层启用。

接口：

- `GET /health`
  - 返回模型加载状态、device、model、version。
- `POST /v1/audio/transcriptions`
  - OpenAI-compatible 文件转写入口。
  - 请求字段：`file`、`model`、`response_format=verbose_json`、`diarization=true`、`hotwords`。
  - 响应字段：`text`、`segments[]`，每段包含 `speaker`、`start`、`end`、`text`。
- `POST /v1/audio/transcriptions/jobs`
  - 可选异步入口，适合大文件。
- `GET /v1/audio/transcriptions/jobs/{jobId}`
  - 查询异步结果。
- `WS /v1/audio/stream`
  - 必选实时入口，Spring Boot 持续发送 16k PCM frame，sidecar 返回 partial/final/status/error/finished。
  - 流式实现可以使用 FunASR streaming 模型，或采用 VAD + 小窗口 overlap 的增量推理；无论内部策略如何，前端必须在录音进行中持续看到 partial/final。

### 文件转写流程

1. 前端继续调用 `POST /ai/meeting-minutes/transcribe-file`，新增可选 `provider=local`。
2. 后端根据请求参数、组织配置或部署默认值解析 provider。
3. 本地 provider 校验文件格式、大小和 ASR sidecar 健康状态。
4. 小文件同步调用 `/v1/audio/transcriptions`；大文件走 job/poll，避免 HTTP 长连接超时。
5. 后端把 sidecar segments 标准化为现有 transcript segment。
6. 前端继续使用同一套 speaker 编辑、下载转写、AI 纪要生成。

### 实时听记流程

1. 前端 `useAsrVoiceInput` 支持 `provider: "local"`。
2. `/ws/asr` 收到 start 后路由到本地 provider。
3. 浏览器仍上传 16k、16bit、mono PCM。
4. 第一版本地实时必须边录边返回 partial/final；推荐策略是 `paraformer-zh-streaming` 做低延迟识别，VAD/Punc/CAM++ 在 final 阶段补齐分段、标点和 speaker。
5. 若采用 `VAD 切片 + overlap`，切片窗口建议 `1.0-2.0 秒`、重叠 `200-500ms`，首个 partial 目标不超过 `2 秒`；不得等 stop 后才集中返回 transcript。
6. 需要 speaker diarization 时，优先在稳定 final 段落上赋 speaker；partial 可先展示“识别中”而不强绑定 speaker。
7. stop 时 flush 剩余音频，返回 `finished`。

### 管理配置

新增内置集成应用或语音配置项：`local_asr`。

字段：

- `enabled`: 是否启用。
- `baseUrl`: 默认 `http://127.0.0.1:18080`。
- `model`: 默认 `paraformer-zh`。
- `mode`: 第一版固定 `realtime_and_file`；`file_only` 只允许开发或降级排障使用，不能作为产品交付模式。
- `device`: `cpu` / `cuda` / `auto`，只展示实际健康检查结果，不由前端强制控制。
- `hotwords`: 可选业务热词，如产品名、客户名、CRM 字段名。
- `fallbackProvider`: 可选 `none` / `iflytek` / `bailian`；默认 `none`，避免隐私场景误发云端。
- `maxUploadSizeMb`: 默认沿用当前 256MB 应用上限。
- `maxSyncDurationSeconds`: 建议 180 秒，超过走异步 job。

### 部署建议

本地开发：

```yaml
app:
  voice:
    default-provider: local_funasr
    local:
      enabled: true
      base-url: "http://127.0.0.1:18080"
      model: "paraformer-zh"
      realtime-enabled: true
      fallback-provider: "none"
```

Docker Compose：

- `backend` 通过内网地址访问 `local-asr:18080`。
- `local-asr` 挂载只读模型目录和可写临时目录。
- GPU 部署时单独启用 NVIDIA runtime；CPU 部署保留可用但限制并发。

推荐资源：

- CPU-only 只用于开发联调和文件转写 POC；生产实时听记建议 GPU，并在部署前做真实音频并发压测。
- 本地模型首次加载需要预热，部署启动后应调用 `/health` 和一段 5-10 秒样例音频做 readiness。
- 并发控制放在 sidecar 和 Spring Boot 双层：上传转写队列、实时 session 限额、单组织并发限额。

## 接口与数据影响

### 后端 API

`POST /ai/meeting-minutes/transcribe-file?provider=local`

返回保持兼容：

```json
{
  "orgId": "demo-org",
  "transcript": [
    {
      "speakerId": "1",
      "speakerName": "发言人 1",
      "text": "今天先讨论客户拜访计划。",
      "startMs": 0,
      "endMs": 3200
    }
  ],
  "segmentCount": 1,
  "file": {
    "name": "meeting.wav",
    "extension": "wav",
    "contentType": "audio/wav",
    "size": 1024
  },
  "model": {
    "provider": "local-funasr",
    "modelName": "paraformer-zh",
    "taskId": "local-..."
  }
}
```

`/ws/asr?provider=local&speakerDiarization=true`

事件保持现有结构：

- `status`: `connected`、`started`、`flushing`、`finished`
- `partial`: interim transcript，可无 speaker
- `final`: stable transcript，尽量包含 speaker
- `error`: 本地服务不可用、模型未加载、音频格式错误、资源繁忙

### Sidecar API 映射

本地 sidecar 的 segment 标准化规则：

- `speaker` 为空时统一为 `"1"`。
- `start/end` 秒转为毫秒整数。
- 连续同 speaker 且间隔小于 800ms 的 final segment 可在后端合并。
- 标点为空时保留原文，不在业务后端二次大模型润色，避免 ASR 阶段引入语义改写。

### 数据库影响

- 第一版不新增 transcript 持久化表。
- 若沿用 `integration_app` 保存 `local_asr` 配置，需新增内置 app definition 和可选迁移种子。
- 不保存本地模型权重路径以外的敏感内容；本地 ASR 不需要云端 secret。

## 任务拆分

- TASK-A：本地 ASR sidecar 实时最小服务
  - FastAPI/FunASR pipeline、Dockerfile、health、`WS /v1/audio/stream`、partial/final 输出、样例音频实时 smoke。
- TASK-B：后端实时 provider 抽象
  - 抽取现有 `/ws/asr` provider 逻辑、接入 `provider=local`、统一 status/partial/final/error/finished 事件和断线清理。
- TASK-C：文件转写 provider 复用
  - 拆分文件 ASR provider、接入 `provider=local`、统一错误码和 transcript 标准化。
- TASK-D：管理配置与前端选择
  - 管理后台集成应用、AI 听记 provider 状态提示、隐私兜底策略。
- TASK-E：测试与部署文档
  - 单测、集成测试、Docker Compose、本地模型下载/挂载说明、桌面端产品 QA。

## 验收标准

- 管理员启用本地 ASR 后，AI 听记“开始会议纪要”不调用讯飞或百炼，录音过程中能实时出现转写内容。
- 管理员启用本地 ASR 后，AI 听记上传录音不调用讯飞或百炼，仍能得到多发言人 transcript。
- 本地 sidecar 未启动时，用户看到“本地语音识别服务不可用”类错误，音频不自动切到云端。
- `/ai/meeting-minutes/transcribe-file?provider=local` 返回的字段与现有前端兼容，speaker 编辑和生成 AI 纪要不需要分支 UI。
- `/ws/asr?provider=local` 必须完成录音中的实时转写，能持续输出 partial/final/status/error/finished；不得只在 stop 后返回完整 transcript。
- 本地实时听记首个 partial 目标不超过 `2 秒`，稳定 final 段落目标不超过 `5 秒`；具体阈值在真实硬件压测后可收紧或放宽，但必须写入验收报告。
- 单 GPU 交付基线至少支持 `2 路`并发实时会议；更多并发通过部署规格声明，不在默认承诺中暗含。
- 后端测试覆盖 provider 选择、本地响应解析、sidecar 不可用、speaker 为空回退、超时/大文件 job 轮询。
- 前端构建通过；桌面端验证 AI 听记 drawer 的导入、状态提示、转写展示、生成纪要流程。
- 本地部署文档明确模型来源、硬件建议、隐私边界、失败回退和云端 provider 禁用方式。

## 风险与回滚

- 本地 speaker diarization 质量不稳定：第一版允许发言人标签编辑；产品文案避免承诺实名识别。
- CPU-only 实时延迟高：仅允许开发联调或排障使用；生产验收若资源不足，应明确阻塞而不是降级为文件转写。
- Python sidecar 运维复杂：镜像固定依赖版本，模型目录外置挂载，后端只通过 HTTP/WS 协议耦合。
- 大文件转写超时：超过阈值走异步 job；前端展示处理中状态。
- 隐私误回退：默认 `fallbackProvider=none`，只有管理员显式启用云端兜底才允许切换。
- 模型下载和许可风险：部署包只记录模型名称和下载说明，交付前确认客户环境可接受对应开源许可。

回滚方式：

- 将 `app.voice.local.enabled=false` 或组织级 `local_asr.enabled=false`。
- 前端 provider 选择回退到现有讯飞/百炼配置。
- sidecar 可独立下线，不影响主应用启动和现有 AI 纪要生成接口。

## 实现进展

- 2026-05-27：创建本地 ASR 设计草案，完成模型选型和后端/sidecar 集成方案。
- 2026-05-27：根据产品要求调整范围：实时转写为第一版必须交付的 P0 能力，文件转写不能替代实时听记验收。

## 交接说明

- 接手实现前先读 `docs/specs/FEAT-029-meeting-minutes-live-transcription.md`、`docs/specs/FEAT-054-ai-minutes-local-audio-upload.md` 和本文件。
- 实时本地听记是第一阶段 P0 交付；实现前优先完成 sidecar 流式 smoke，再接后端 `/ws/asr` 和前端听记面板。
- 文件转写 provider 是同一 sidecar 的复用能力，可以在实时链路跑通后并行补齐。
