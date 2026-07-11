---
kind: feature-spec
feature_id: FEAT-097
title: 客户互动多模态采集与确认归集
status: ready
owner_role: fullstack-agent
task_ids: TASK-189
related_decisions: FEAT-081
related_issues: none
updated_at: 2026-07-11T06:36:11Z
updated_by: MANAGER-001
---

# FEAT-097 - 客户互动多模态采集与确认归集

## 目标

将“整理互动记录”从单一文本录入升级为多模态采集工作区。销售可通过实时语音、录音、沟通截图、文本文档和手工文本提交一次互动的多份材料；系统保留不可篡改的原件，完成 ASR/OCR/文档解析和 AI 分析，用户校对后才归集为正式客户互动记录。

## 用户场景

- 客户经理拜访结束后口述沟通过程，实时转写并补充到本次采集批次。
- 上传电话或会议录音，系统提取逐字稿和可识别的说话人片段。
- 上传多张微信、企业微信或邮件截图，按用户排序进行视觉 OCR。
- 上传 TXT、Markdown、DOCX 或文本型 PDF 纪要，提取原文。
- 将上述材料与手工补充文本合并，生成统一草稿、事实、风险、机会、承诺和下一步行动。

## 关键边界

- 输入方式与互动来源分离。截图是输入方式，互动来源仍由用户选择微信、电话、会议或客户反馈。
- 原始材料不可被 AI 修改或覆盖；提取文本、AI 分析和用户确认稿分别保存。
- AI 不自动写入正式时间线。只有当前用户明确确认后才调用既有互动保存链路。
- 错误材料不阻塞其他材料；批次可以进入“部分成功”，并明确展示失败原因。
- 同一文件按组织、客户和 SHA-256 去重；一次批次最多 12 个文件、单文件最多 50MB、总量最多 200MB。
- 图片支持 PNG/JPEG/WebP；音频支持 MP3/WAV/M4A/AAC/OGG/WebM/MP4；文档支持 TXT/MD/DOCX/PDF。

## 数据模型

### 互动采集批次

- `public_id`、`org_id`、`crm_account_id`、`created_by`
- `source_type`、`occurred_at`、`subject`
- `narration_text`、`pasted_text`
- `status`: `QUEUED / PROCESSING / READY / PARTIAL / FAILED / CONFIRMED`
- `combined_text`: 各材料提取结果的统一草稿
- `analysis_json`: 摘要、事实、诉求、风险、机会、承诺、行动项和待确认项
- `error_message`、`confirmed_event_id`、时间戳

### 原始材料

- `public_id`、`batch_id`、`org_id`
- `input_type`: `LIVE_VOICE / AUDIO / IMAGE / DOCUMENT / PASTED_TEXT`
- 原始文件名、MIME、大小、SHA-256、不可变存储路径、排序号
- `status`: `STORED / PROCESSING / READY / FAILED`
- `extracted_text`、`error_message`

## 处理链路

1. 创建批次，验证当前用户对客户的可见权限。
2. 先安全落盘原始文件，再提交到受控执行器异步处理。
3. 音频复用 Aliyun 文件 ASR；图片使用组织可用的阿里云视觉模型执行 OCR；文档使用结构化解析器读取。
4. 按实时口述、粘贴文本和文件顺序合并，保留材料标题与来源边界。
5. 使用组织的 `customer-insight` 模型路由生成严格 JSON 分析；模型失败时仍保留统一草稿并标记降级。
6. 前端轮询批次状态，允许用户修改来源、时间、主题和确认文本。
7. 用户确认后调用既有幂等互动保存逻辑，将批次关联到正式事件并进入时间线。

## API

- `POST /customer-workbench/accounts/{accountId}/interaction-batches`：multipart 创建批次。
- `GET /customer-workbench/accounts/{accountId}/interaction-batches`：最近批次列表。
- `GET /customer-workbench/interaction-batches/{batchId}`：批次、材料和分析详情。
- `GET /customer-workbench/interaction-batches/{batchId}/assets/{assetId}`：权限校验后读取原件。
- `POST /customer-workbench/interaction-batches/{batchId}/retry`：重试失败材料和分析。
- `POST /customer-workbench/interaction-batches/{batchId}/confirm`：以用户校对稿生成正式互动事件。

## 界面设计

- 保持现有 modal 语义，但扩大为桌面端两栏整理工作区，不做嵌套卡片。
- 左栏为来源、时间、主题、实时语音、粘贴文本和五类输入入口。
- 材料列表显示文件名、类型、大小、处理状态、删除前状态和错误信息；创建批次后原件不可静默替换。
- 右栏显示处理进度、统一草稿和 AI 提取结果；AI 结果按事实、风险、机会、承诺、行动和待确认项分组。
- 操作分为“生成整理草稿”和“确认并归集”，后者仅在处理完成且确认文本达到最小长度时可用。
- 图标使用 Lucide；录音、上传、删除、重试、关闭等图标按钮无浮起、缩放和阴影。

## 安全与审计

- 存储路径按组织/客户/批次隔离，文件名随机化并阻断路径穿越。
- 下载、查询、重试和确认均重新校验组织、用户与客户权限。
- 不记录文件内容、模型密钥或 Token 到日志；错误信息裁剪后返回。
- 图片与录音上传区提示用户确认已获得必要授权；正式归集记录保留批次 ID 以支持追溯。

## 验收标准

- 五种输入方式均可组合成同一批次，原件元数据和 SHA-256 可回读。
- 录音、图片、DOCX/PDF/TXT 至少各有一个自动化或真实验证证据；失败时状态和原因可见。
- AI 分析返回结构化分组，用户修改统一草稿后确认，时间线出现且重复确认不重复创建。
- 关闭并重新打开后可读取最近草稿；失败批次可重试。
- 平台与 CRM 嵌入入口均可完成流程，1920x960 无页面外层滚动和内容遮挡。

