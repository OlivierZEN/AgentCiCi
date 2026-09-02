---
kind: feature-spec
feature_id: FEAT-054
title: AI minutes local audio upload and speaker diarization
status: in_implementation
owner_role: fullstack-agent
task_ids: TASK-134
related_decisions: none
related_issues: none
updated_at: 2026-09-02T01:25:52Z
updated_by: codex
---

# FEAT-054 - AI Minutes Local Audio Upload And Speaker Diarization

## Background And Goal

- AI 听记当前以浏览器麦克风实时听记为主，用户无法把已有会议录音直接导入。
- 企业会议常见来源包括本地录音、视频会议导出和客户现场录音，格式不统一，且需要区分不同发言人后再生成会议纪要。
- 目标是在 AI 听记中增加本地音频/视频文件上传入口，后端调用阿里云百炼语音识别能力解析为多发言人文本，并复用现有 AI 纪要生成链路。

## Scope

### In Scope

- AI 听记前端增加本地文件上传入口，支持上传并展示解析状态、错误和解析后的多发言人 transcript。
- 后端新增会议听记文件转写 API，接收 multipart 文件并返回 `speakerId`、`speakerName`、`text`、时间信息和文件元数据。
- 后端服务封装百炼语音识别文件转写请求，优先启用说话人分离能力。
- 支持格式白名单：`aac`, `amr`, `avi`, `flac`, `flv`, `m4a`, `mkv`, `mov`, `mp3`, `mp4`, `mpeg`, `ogg`, `opus`, `wav`, `webm`, `wma`, `wmv`。
- 上传解析成功后，前端可继续编辑发言人名称，并调用现有 `/ai/meeting-minutes/summary` 生成结构化纪要。

### Out Of Scope

- 新增数据库持久化、历史文件管理、录音下载或对象存储归档。
- 客户端视频预览、音频波形、剪辑编辑或字幕导出。
- 新增移动端布局、移动端截图或移动端自动化测试。
- 生产百炼账号、密钥或模型配额配置。

## API Contract

- `POST /ai/meeting-minutes/transcribe-file`
  - multipart field: `file`
  - response `data.transcript`: array of transcript segments with speaker and timing metadata.
  - response `data.file`: parsed file name, extension, content type and size.
  - response `data.model`: provider/model identifiers used for the transcription run.
- Unsupported extension returns a validation error before any model call.
- Empty model response or unparseable response returns a user-facing failure instead of silently producing an empty transcript.
- Implementation uses 百炼 temporary OSS upload to obtain an `oss://` URL, then submits a Fun-ASR asynchronous transcription task with `diarization_enabled=true`.
- 百炼 result `transcription_url` is a pre-signed OSS URL and must be downloaded verbatim; do not route it through URI builders that may re-encode the query string and invalidate the signature.
- The temporary URL path is suitable for local upload enablement and low-volume use. Production/high-concurrency storage should move to owned OSS because 百炼 temporary upload URLs expire and the upload policy endpoint is rate-limited.
- Application multipart upload size is capped at 256MB for this delivery; this is an application safety cap, not the upstream 百炼 model maximum.

## Long Audio Handling

- 百炼 Fun-ASR file transcription allows longer files, but speaker diarization is recommended for recordings no longer than 2 hours; longer diarized recordings may fail or time out at the provider.
- This delivery does not automatically split files by duration. If an uploaded file is over the practical diarization window but under the application size cap, the current behavior is to submit it as one task and surface provider failure/timeout back to the user.
- Automatic splitting is feasible as a follow-up, but should be designed as a separate slice because it requires media probing/splitting, per-chunk task orchestration, timestamp offset merging, and speaker identity reconciliation across chunks.

## UX Notes

- The upload control lives inside the existing AI 听记 drawer, near the transcript header, as a compact secondary action.
- While a file is parsing, transcript controls stay stable and the primary meeting record button is disabled only for conflicting actions.
- Error copy should name the accepted formats or the parsing failure plainly, without implementation codes.

## Acceptance Criteria

- Users can upload any whitelisted extension from AI 听记 and receive a multi-speaker transcript when the model returns speaker segments.
- Unsupported file types are blocked on both frontend and backend.
- Parsed transcript segments populate the same speaker editing and summary generation flow used by realtime listening.
- Backend unit tests cover supported extension validation, unsupported extension rejection, and response parsing for speaker diarization output.
- Frontend build succeeds and desktop route QA verifies the upload control renders without breaking the existing AI 听记 drawer layout.

## Handoff

- 2026-05-25: Opened for implementation from user request to add local audio upload and 百炼 multi-speaker recognition to AI 听记.
- 2026-05-25: Real provider smoke proved the upload/ASR chain on 60-second chunks from the local recording, returning Fun-ASR speaker transcript segments. Whole-file 7.1MB upload and one chunk still showed intermittent `Connection reset` on this local network, so automatic chunking/retry remains a follow-up rather than part of this delivery.
- 2026-09-02 TASK-355: 平台治理中的 `file-asr` 当前选择 `qwen-audio-3.0-asr-flash` 专属同步模型，而旧实现无条件走 Filetrans/Fun-ASR 异步任务，真实上传因此返回 `403 current user api does not support asynchronous calls`。实现按模型协议分流：同步 Flash 走 `/api/v1/services/aigc/multimodal-generation/generation` 并回填单发言人 transcript；`*-filetrans` / Fun-ASR 继续走异步任务和说话人分离。同步模型本身不提供说话人分离，若产品必须对上传文件区分发言人，平台须选择支持异步和 diarization 的 Filetrans/Fun-ASR 模型及相应 API 身份。
