import { ChangeEvent, KeyboardEvent, RefObject, useRef } from "react";
import ChatMarkdown from "../components/ChatMarkdown";

export type MeetingPanelStatus =
  | "idle"
  | "permission"
  | "recording"
  | "transcribing"
  | "transcribed"
  | "stopping"
  | "summarizing"
  | "ready_to_writeback"
  | "writing_back"
  | "done"
  | "error";

export type MeetingPanelTranscriptSegment = {
  id: string;
  speakerId: string;
  speakerName: string;
  text: string;
  time: string;
  startMs?: number;
  endMs?: number;
};

export type MeetingPanelSpeakerEdit = {
  speakerId: string;
  lineId: string;
  value: string;
};

export type MeetingPanelWritebackItem = {
  id: string;
  type?: string;
  label?: string;
  content?: string;
  target?: {
    source?: string;
    objectType?: string;
    objectId?: string;
  };
};

type Props = {
  eyebrow?: string;
  title?: string;
  recordName?: string;
  customerName?: string;
  status: MeetingPanelStatus;
  notice: string;
  transcript: MeetingPanelTranscriptSegment[];
  partial: MeetingPanelTranscriptSegment | null;
  summary: string;
  speakerEdit: MeetingPanelSpeakerEdit | null;
  transcriptScrollRef: RefObject<HTMLDivElement | null>;
  speakerEditInputRef: RefObject<HTMLInputElement | null>;
  onClose?: () => void;
  onPrimaryAction: () => void;
  primaryActionLabel: string;
  primaryActionDisabled?: boolean;
  primaryActionVisible?: boolean;
  onDownloadTranscript?: () => void;
  downloadTranscriptLabel?: string;
  downloadTranscriptDisabled?: boolean;
  secondaryActionLabel?: string;
  onSecondaryAction?: () => void;
  fileUploadAccept?: string;
  fileUploadDisabled?: boolean;
  onFileUpload?: (file: File) => void;
  onSpeakerEditStart: (lineId: string, speakerId: string, speakerName: string) => void;
  onSpeakerEditValueChange: (value: string) => void;
  onSpeakerEditCommit: () => void;
  onSpeakerEditKeyDown: (event: KeyboardEvent<HTMLInputElement>) => void;
  writebackItems?: MeetingPanelWritebackItem[];
  selectedWritebackItemIds?: string[];
  onToggleWritebackItem?: (itemId: string) => void;
  onConfirmWriteback?: () => void;
  confirmWritebackDisabled?: boolean;
  writebackResultMessage?: string;
  hideHeader?: boolean;
};

export function MeetingMinutesPanel({
  eyebrow = "MEETING NOTES",
  title = "实时会议纪要",
  recordName,
  customerName,
  status,
  notice,
  transcript,
  partial,
  summary,
  speakerEdit,
  transcriptScrollRef,
  speakerEditInputRef,
  onClose,
  onPrimaryAction,
  primaryActionLabel,
  primaryActionDisabled,
  primaryActionVisible = true,
  onDownloadTranscript,
  downloadTranscriptLabel = "下载转写",
  downloadTranscriptDisabled,
  secondaryActionLabel = "收起",
  onSecondaryAction,
  fileUploadAccept,
  fileUploadDisabled,
  onFileUpload,
  onSpeakerEditStart,
  onSpeakerEditValueChange,
  onSpeakerEditCommit,
  onSpeakerEditKeyDown,
  writebackItems = [],
  selectedWritebackItemIds = [],
  onToggleWritebackItem,
  onConfirmWriteback,
  confirmWritebackDisabled,
  writebackResultMessage,
  hideHeader = false,
}: Props) {
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const statusLabel = statusText(status);
  const showDownloadTranscriptAction = Boolean(onDownloadTranscript);
  const showWritebackAction = Boolean(onConfirmWriteback && writebackItems.length);
  const showFooter = Boolean(onSecondaryAction || showDownloadTranscriptAction || showWritebackAction || primaryActionVisible);
  const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (file) {
      onFileUpload?.(file);
    }
  };

  return (
    <>
      {!hideHeader ? (
        <header className="cici-meeting-drawer__header">
          <div>
            <p>{eyebrow}</p>
            <h2>{title}</h2>
            {recordName || customerName ? (
              <div className="cici-meeting-drawer__context">
                {recordName ? <span>{recordName}</span> : null}
                {customerName ? <span>{customerName}</span> : null}
              </div>
            ) : null}
          </div>
          {onClose ? (
            <button type="button" className="cici-meeting-drawer__close" onClick={onClose} aria-label="关闭会议纪要">
              ×
            </button>
          ) : null}
        </header>
      ) : null}

      <div className="cici-meeting-drawer__status">
        <span className={`cici-meeting-drawer__dot is-${status}`} aria-hidden />
        <strong>{statusLabel}</strong>
        <span>{notice || "等待开始会议听记。"}</span>
      </div>

      <div className="cici-meeting-drawer__body">
        <section className="cici-meeting-drawer__section">
          <div className="cici-meeting-drawer__section-head">
            <h3>实时转写</h3>
            <div className="cici-meeting-drawer__section-actions">
              {onFileUpload ? (
                <>
                  <input
                    ref={fileInputRef}
                    className="cici-meeting-drawer__file-input"
                    type="file"
                    accept={fileUploadAccept}
                    onChange={handleFileChange}
                    hidden
                  />
                  <button
                    type="button"
                    className="cici-meeting-drawer__text-action"
                    onClick={() => fileInputRef.current?.click()}
                    disabled={fileUploadDisabled}
                  >
                    导入录音
                  </button>
                </>
              ) : null}
              <span>{transcript.length} 段</span>
            </div>
          </div>
          <div className="cici-meeting-drawer__transcript" ref={transcriptScrollRef}>
            {transcript.map((segment) => (
              <div key={segment.id} className="cici-meeting-drawer__line">
                <div className="cici-meeting-drawer__speaker">
                  {speakerEdit?.speakerId === segment.speakerId && speakerEdit.lineId === segment.id ? (
                    <input
                      ref={speakerEditInputRef}
                      className="cici-meeting-drawer__speaker-input"
                      value={speakerEdit.value}
                      onChange={(event) => onSpeakerEditValueChange(event.target.value)}
                      onBlur={onSpeakerEditCommit}
                      onKeyDown={onSpeakerEditKeyDown}
                      aria-label="编辑发言人名称"
                    />
                  ) : (
                    <button
                      type="button"
                      className="cici-meeting-drawer__speaker-name"
                      onDoubleClick={() => onSpeakerEditStart(segment.id, segment.speakerId, segment.speakerName)}
                      onKeyDown={(event) => {
                        if (event.key === "Enter" || event.key === "F2") {
                          event.preventDefault();
                          onSpeakerEditStart(segment.id, segment.speakerId, segment.speakerName);
                        }
                      }}
                      aria-label={`双击编辑${segment.speakerName}`}
                    >
                      {segment.speakerName}
                    </button>
                  )}
                  <span>{segment.time}</span>
                </div>
                <p>{segment.text}</p>
              </div>
            ))}
            {partial ? (
              <div className="cici-meeting-drawer__line is-partial">
                <div className="cici-meeting-drawer__speaker">
                  {speakerEdit?.speakerId === partial.speakerId && speakerEdit.lineId === "partial" ? (
                    <input
                      ref={speakerEditInputRef}
                      className="cici-meeting-drawer__speaker-input"
                      value={speakerEdit.value}
                      onChange={(event) => onSpeakerEditValueChange(event.target.value)}
                      onBlur={onSpeakerEditCommit}
                      onKeyDown={onSpeakerEditKeyDown}
                      aria-label="编辑发言人名称"
                    />
                  ) : (
                    <button
                      type="button"
                      className="cici-meeting-drawer__speaker-name"
                      onDoubleClick={() => onSpeakerEditStart("partial", partial.speakerId, partial.speakerName)}
                      onKeyDown={(event) => {
                        if (event.key === "Enter" || event.key === "F2") {
                          event.preventDefault();
                          onSpeakerEditStart("partial", partial.speakerId, partial.speakerName);
                        }
                      }}
                      aria-label={`双击编辑${partial.speakerName}`}
                    >
                      {partial.speakerName}
                    </button>
                  )}
                  <span>识别中</span>
                </div>
                <p>{partial.text}</p>
              </div>
            ) : null}
            {transcript.length === 0 && !partial ? (
              <div className="cici-meeting-drawer__empty">等待发言内容进入转写。</div>
            ) : null}
          </div>
        </section>

        <section className="cici-meeting-drawer__section cici-meeting-drawer__section--summary">
          <div className="cici-meeting-drawer__section-head">
            <h3>AI 会议纪要</h3>
            {writebackItems.length ? <span>{writebackItems.length} 个写回候选</span> : null}
          </div>
          <div className="cici-meeting-drawer__summary">
            {summary ? <ChatMarkdown content={summary} /> : <div className="cici-meeting-drawer__empty">结束会议后在这里生成结构化纪要。</div>}

            {writebackItems.length ? (
              <div className="cici-meeting-drawer__writeback">
                <h4>写回候选</h4>
                {writebackItems.map((item) => (
                  <label key={item.id} className="cici-meeting-drawer__writeback-item">
                    <input
                      type="checkbox"
                      checked={selectedWritebackItemIds.includes(item.id)}
                      onChange={() => onToggleWritebackItem?.(item.id)}
                    />
                    <span>
                      <strong>{item.label || item.id}</strong>
                      <small>{writebackTargetText(item)}</small>
                      {item.content ? <em>{item.content}</em> : null}
                    </span>
                  </label>
                ))}
              </div>
            ) : null}

            {writebackResultMessage ? <p className="cici-meeting-drawer__result">{writebackResultMessage}</p> : null}
          </div>
        </section>
      </div>

      {showFooter ? (
        <footer className="cici-meeting-drawer__footer">
          {onSecondaryAction ? (
            <button type="button" className="cici-meeting-drawer__btn cici-meeting-drawer__btn--secondary" onClick={onSecondaryAction}>
              {secondaryActionLabel}
            </button>
          ) : <span />}
          <div className="cici-meeting-drawer__footer-actions">
            {showWritebackAction ? (
              <button
                type="button"
                className="cici-meeting-drawer__btn cici-meeting-drawer__btn--secondary"
                onClick={onConfirmWriteback}
                disabled={confirmWritebackDisabled}
              >
                确认写回
              </button>
            ) : null}
            {showDownloadTranscriptAction ? (
              <button
                type="button"
                className="cici-meeting-drawer__btn cici-meeting-drawer__btn--secondary"
                onClick={onDownloadTranscript}
                disabled={downloadTranscriptDisabled}
              >
                {downloadTranscriptLabel}
              </button>
            ) : null}
            {primaryActionVisible ? (
              <button
                type="button"
                className="cici-meeting-drawer__btn cici-meeting-drawer__btn--primary"
                onClick={onPrimaryAction}
                disabled={primaryActionDisabled}
              >
                {primaryActionLabel}
              </button>
            ) : null}
          </div>
        </footer>
      ) : null}
    </>
  );
}

function statusText(status: MeetingPanelStatus): string {
  if (status === "permission") return "请求权限";
  if (status === "recording") return "录音中";
  if (status === "transcribing") return "解析文件";
  if (status === "transcribed") return "可生成纪要";
  if (status === "stopping") return "正在结束";
  if (status === "summarizing") return "生成纪要";
  if (status === "ready_to_writeback") return "待确认写回";
  if (status === "writing_back") return "写回中";
  if (status === "done") return "已完成";
  if (status === "error") return "需要处理";
  return "待开始";
}

function writebackTargetText(item: MeetingPanelWritebackItem) {
  const target = item.target;
  if (!target) {
    return item.type || "候选项";
  }
  return [target.source, target.objectType, target.objectId].filter(Boolean).join(" / ");
}
