# FEAT-073 - 邮件 ID 刷新重试与语音后续可用性修复

## 背景

线上 `2.1.6` 中，用户确认“是的，看下正文”后系统确实调用了 `email_get_message`，但 POP3 `messageId` 已不在服务器当前可见范围，工具返回“没有找到 messageId，建议先用 email_list_inbox 获取最新 id”。随后会话仍停止在工具摘要，未自动刷新邮件 ID 再读取正文。截图中语音入口也提示“请等待当前回答结束后再开始语音”，说明未完成工具链让前端仍处于生成或等待确认状态，影响语音继续输入。

## 目标

- `email_search` 唯一命中时，除 `messageId` 外保存主题和发件人等可用于刷新 ID 的上下文。
- `email_get_message` 返回 ID 过期/未找到时，后端自动按保存的主题/发件人重新搜索最近邮件，拿到最新 `messageId` 后再次读取正文。
- `email_get_message` 失败时不清除待展开邮件状态；只有正文读取成功后才清除。
- 保持语音入口在回答真正完成后可用；减少因未完成工具链导致的“请等待当前回答结束后再开始语音”。

## 范围

- 后端会话状态：保存 `pending_email_subject`、`pending_email_from`，失败时保留待处理邮件。
- 后端编排：确认后先读旧 ID；如果 ID 失效，则自动 `email_search` 刷新 ID，再 `email_get_message`。
- 前端：如发现完成态/加载态卡住，补充兜底释放。
- 验证：focused backend tests、frontend build 或相关测试、backend compile、static check。

## 非目标

- 不修改 POP3/SMTP 协议和账号配置。
- 不主动在自动化验证中读取生产真实邮件正文。
- 不改聊天页面视觉样式。

## 验收标准

- 用户确认查看正文时，即使上一轮 `messageId` 失效，系统会重新搜索该主题邮件并用最新 ID 读取正文。
- 如果刷新后仍找不到唯一邮件，回复明确说明无法读取，而不是停在“让我读取”或工具摘要。
- 语音按钮在回答结束后可以再次使用。

## 实现摘要

- `email_search` 单封命中时，会话状态保存 `pending_email_message_id`、`pending_email_subject`、`pending_email_from` 与 `pending_email_action=read_body`。
- `email_get_message` 返回 POP3 `messageId` 不可见/未找到时不再清除待处理邮件，只把下一步标记为刷新搜索后再读取。
- 用户确认继续查看正文时，编排层自动执行旧 ID 的 `email_get_message`；若结果显示 ID 失效且状态中有主题或发件人，则自动执行 `email_search` 刷新 ID，并在同一轮继续执行第二次 `email_get_message`。
- 前端在流式回答返回后先释放 `chatLoading`，再刷新历史消息，避免历史刷新慢导致麦克风继续被禁用；同时设置 180 秒兜底释放。

## 验证记录

- `backend/`: `mvn test -Dtest=ChatOrchestratorServiceModelIdentityTest` -> 25 tests passed。
- `frontend/`: `npm run build` -> success；保留既有 Vite large chunk warning。
- 未进行生产真实邮件正文读取自动化 smoke，避免访问真实邮件内容。
