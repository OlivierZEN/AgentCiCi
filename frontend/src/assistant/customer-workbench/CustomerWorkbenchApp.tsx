import { useEffect, useMemo, useRef, useState } from "react";
import { useAsrVoiceInput } from "../../shared/useAsrVoiceInput";
import {
  acceptCustomerRecommendation,
  applyCustomerRecommendation,
  askCustomerWorkbenchAssistant,
  confirmCustomerRecommendation,
  dismissCustomerRecommendation,
  getCustomerWorkbenchIntegrationStatus,
  getCustomerAssistantHistory,
  getCustomerWorkbenchNotifications,
  getCustomerWorkbenchSupervisorSummary,
  getCustomerWorkbenchQueue,
  getCustomerWorkbenchDetail,
  setCustomerFollowed,
  saveCustomerInteraction,
  submitCustomerRecommendationFeedback,
  updateCustomerRecommendation,
  type CustomerAssistantResult,
  type CustomerRecommendation,
  type CustomerWorkbenchAccount,
  type CustomerWorkbenchDetail,
} from "./customerWorkbenchApi";

type ChatMessage = {
  role: "user" | "assistant";
  text: string;
  time: string;
};

type WorkbenchMode = "new" | "existing";
type DetailTab =
  | "overview"
  | "timeline"
  | "signals"
  | "recommendations"
  | "actions"
  | "service"
  | "value"
  | "renewal"
  | "relationship";
type RecommendationAction = "accept" | "edit" | "dismiss" | "confirm" | "apply";

export function isCurrentVoiceSession(sessionId: number, currentSessionId: number): boolean {
  return sessionId === currentSessionId;
}

export function scrollConversationToLatest(element: Pick<HTMLElement, "scrollTop" | "scrollHeight"> | null): void {
  if (element) element.scrollTop = element.scrollHeight;
}

type IconName =
  | "alert"
  | "bot"
  | "calendar"
  | "check"
  | "clipboard"
  | "close"
  | "document"
  | "edit"
  | "external"
  | "inbox"
  | "info"
  | "keyboard"
  | "list"
  | "message"
  | "mic"
  | "people"
  | "phone"
  | "pin"
  | "search"
  | "send"
  | "sliders"
  | "swap"
  | "task"
  | "wechat";

const segmentLabels: Record<string, string> = {
  NEW: "新客户",
  EXISTING: "老客户",
  STRATEGIC: "战略客户",
  RISK: "风险客户",
};

const modeFilterOptions: Record<WorkbenchMode, Array<[string, string]>> = {
  new: [
    ["focus", "重点推进"],
    ["follow", "待跟进"],
    ["risk", "风险客户"],
    ["recommendations", "待确认建议"],
  ],
  existing: [
    ["renewal", "续约90天"],
    ["health", "健康下降"],
    ["service", "服务异常"],
    ["expansion", "增购信号"],
  ],
};

const modeTabs: Record<WorkbenchMode, Array<[DetailTab, string]>> = {
  new: [
    ["overview", "推进概览"],
    ["timeline", "互动时间线"],
    ["signals", "推进信号"],
    ["recommendations", "CRM 落地建议"],
    ["actions", "下一步行动"],
  ],
  existing: [
    ["overview", "经营概览"],
    ["timeline", "互动时间线"],
    ["service", "服务问题"],
    ["value", "价值兑现"],
    ["renewal", "续约增购"],
    ["relationship", "关系地图"],
  ],
};

export function defaultCustomerQueueFilter(mode: WorkbenchMode) {
  return mode === "new" ? "focus" : "";
}

function nowTime() {
  const now = new Date();
  return `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}`;
}

function chatTime(value: string) {
  if (!value) return nowTime();
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return nowTime();
  return `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}

function segmentLabel(segment: string) {
  return segmentLabels[segment] ?? segment;
}

function roleLabel(role: string) {
  return ({ OWNER: "组织负责人", ORG_ADMIN: "组织管理员", ORG_USER: "业务用户" } as Record<string, string>)[role] ?? role;
}

function shortDate(value: string) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const now = new Date();
  const dayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const itemDayStart = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
  const time = `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
  if (itemDayStart === dayStart) return `今天 ${time}`;
  if (itemDayStart === dayStart - 24 * 60 * 60 * 1000) return `昨天 ${time}`;
  return `${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")} ${time}`;
}

function formatConfidence(value: number) {
  const normalized = Number(value);
  if (!Number.isFinite(normalized)) return "—";
  const percent = normalized > 1 ? normalized : normalized * 100;
  return `${Math.round(percent)}%`;
}

function metricValue(detail: CustomerWorkbenchDetail | null, key: string, fallback: number) {
  const value = Number(detail?.metrics?.[key]?.value);
  return Number.isFinite(value) ? value : fallback;
}

function queueStatus(account: CustomerWorkbenchAccount) {
  if (account.segment === "RISK") return "风险";
  if (account.pendingRecommendationCount > 0) return "关注";
  if (account.segment === "EXISTING" || account.segment === "STRATEGIC") return "健康";
  return "待跟进";
}

function queueStatusClass(account: CustomerWorkbenchAccount) {
  const status = queueStatus(account);
  if (status === "风险") return "is-risk";
  if (status === "健康") return "is-healthy";
  if (status === "待跟进") return "is-pending";
  return "is-focus";
}

function lifecycleSourceLabel(value: string) {
  if (!value) return "客户互动";
  const normalized = value.toUpperCase();
  if (value.includes("微信") || normalized.includes("WECHAT")) return "微信";
  if (value.includes("电话") || normalized.includes("PHONE")) return "通话录音";
  if (value.includes("会议") || normalized.includes("MEETING") || normalized.includes("EVENT")) return "会议纪要";
  return value;
}

function sourceIconName(value: string): IconName {
  const label = lifecycleSourceLabel(value);
  if (label === "微信") return "wechat";
  if (label === "通话录音") return "phone";
  if (label === "会议纪要") return "calendar";
  return "message";
}

function metricIconName(label: string): IconName {
  if (label.includes("风险")) return "alert";
  if (label.includes("任务")) return "calendar";
  if (label.includes("互动")) return "message";
  return "clipboard";
}

function recommendationIconName(type: string, index: number): IconName {
  const normalized = type.toUpperCase();
  if (normalized.includes("RISK")) return "alert";
  if (normalized.includes("CONTACT")) return "people";
  if (normalized.includes("DEMAND")) return "document";
  return ["task", "alert", "people", "document"][index % 4] as IconName;
}

function evidenceLabel(value: unknown) {
  if (typeof value === "string") return value;
  if (!value || typeof value !== "object") return "CRM 事实";
  const item = value as Record<string, unknown>;
  return String(item.title || item.detail || item.subject || item.source || "CRM 事实");
}

function Icon({ name, className = "" }: { name: IconName; className?: string }) {
  const baseClass = `customer-workbench-icon${className ? ` ${className}` : ""}`;

  // Icon path data is sourced from Bootstrap Icons v1.13.1 (MIT).
  switch (name) {
    case "alert":
      return <svg className={baseClass} viewBox="0 0 16 16" aria-hidden><path fill="currentColor" d="M8.982 1.566a1.13 1.13 0 0 0-1.96 0L.165 13.233c-.457.778.091 1.767.98 1.767h13.713c.889 0 1.438-.99.98-1.767zM8 5c.535 0 .954.462.9.995l-.35 3.507a.552.552 0 0 1-1.1 0L7.1 5.995A.905.905 0 0 1 8 5m.002 6a1 1 0 1 1 0 2 1 1 0 0 1 0-2" /></svg>;
    case "bot":
      return <svg className={baseClass} viewBox="0 0 16 16" aria-hidden><path fill="currentColor" d="M6 12.5a.5.5 0 0 1 .5-.5h3a.5.5 0 0 1 0 1h-3a.5.5 0 0 1-.5-.5M3 8.062C3 6.76 4.235 5.765 5.53 5.886a26.6 26.6 0 0 0 4.94 0C11.765 5.765 13 6.76 13 8.062v1.157a.93.93 0 0 1-.765.935c-.845.147-2.34.346-4.235.346s-3.39-.2-4.235-.346A.93.93 0 0 1 3 9.219zm4.542-.827a.25.25 0 0 0-.217.068l-.92.9a25 25 0 0 1-1.871-.183.25.25 0 0 0-.068.495c.55.076 1.232.149 2.02.193a.25.25 0 0 0 .189-.071l.754-.736.847 1.71a.25.25 0 0 0 .404.062l.932-.97a25 25 0 0 0 1.922-.188.25.25 0 0 0-.068-.495c-.538.074-1.207.145-1.98.189a.25.25 0 0 0-.166.076l-.754.785-.842-1.7a.25.25 0 0 0-.182-.135" /><path fill="currentColor" d="M8.5 1.866a1 1 0 1 0-1 0V3h-2A4.5 4.5 0 0 0 1 7.5V8a1 1 0 0 0-1 1v2a1 1 0 0 0 1 1v1a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-1a1 1 0 0 0 1-1V9a1 1 0 0 0-1-1v-.5A4.5 4.5 0 0 0 10.5 3h-2zM14 7.5V13a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V7.5A3.5 3.5 0 0 1 5.5 4h5A3.5 3.5 0 0 1 14 7.5" /></svg>;
    case "calendar":
      return <svg className={baseClass} viewBox="0 0 16 16" aria-hidden><path fill="currentColor" d="M4 .5a.5.5 0 0 0-1 0V1H2a2 2 0 0 0-2 2v1h16V3a2 2 0 0 0-2-2h-1V.5a.5.5 0 0 0-1 0V1H4zM16 14V5H0v9a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2m-5.146-5.146-3 3a.5.5 0 0 1-.708 0l-1.5-1.5a.5.5 0 0 1 .708-.708L7.5 10.793l2.646-2.647a.5.5 0 0 1 .708.708" /></svg>;
    case "check":
      return <svg className={baseClass} viewBox="0 0 16 16" aria-hidden><path fill="currentColor" d="M12.736 3.97a.733.733 0 0 1 1.047 0c.286.289.29.756.01 1.05L7.88 12.01a.733.733 0 0 1-1.065.02L3.217 8.384a.757.757 0 0 1 0-1.06.733.733 0 0 1 1.047 0l3.052 3.093 5.4-6.425z" /></svg>;
    case "clipboard":
      return <svg className={baseClass} viewBox="0 0 16 16" aria-hidden><path fill="currentColor" d="M6.5 0A1.5 1.5 0 0 0 5 1.5v1A1.5 1.5 0 0 0 6.5 4h3A1.5 1.5 0 0 0 11 2.5v-1A1.5 1.5 0 0 0 9.5 0zm3 1a.5.5 0 0 1 .5.5v1a.5.5 0 0 1-.5.5h-3a.5.5 0 0 1-.5-.5v-1a.5.5 0 0 1 .5-.5z" /><path fill="currentColor" d="M4 1.5H3a2 2 0 0 0-2 2V14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V3.5a2 2 0 0 0-2-2h-1v1A2.5 2.5 0 0 1 9.5 5h-3A2.5 2.5 0 0 1 4 2.5zm6.854 7.354-3 3a.5.5 0 0 1-.708 0l-1.5-1.5a.5.5 0 0 1 .708-.708L7.5 10.793l2.646-2.647a.5.5 0 0 1 .708.708" /></svg>;
    case "close":
      return <svg className={baseClass} viewBox="0 0 16 16" aria-hidden><path fill="currentColor" d="M2.146 2.854a.5.5 0 1 1 .708-.708L8 7.293l5.146-5.147a.5.5 0 0 1 .708.708L8.707 8l5.147 5.146a.5.5 0 0 1-.708.708L8 8.707l-5.146 5.147a.5.5 0 0 1-.708-.708L7.293 8z" /></svg>;
    case "document":
      return <svg className={baseClass} viewBox="0 0 16 16" aria-hidden><path fill="currentColor" d="M9.293 0H4a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V4.707A1 1 0 0 0 13.707 4L10 .293A1 1 0 0 0 9.293 0M9.5 3.5v-2l3 3h-2a1 1 0 0 1-1-1M4.5 9a.5.5 0 0 1 0-1h7a.5.5 0 0 1 0 1zM4 10.5a.5.5 0 0 1 .5-.5h7a.5.5 0 0 1 0 1h-7a.5.5 0 0 1-.5-.5m.5 2.5a.5.5 0 0 1 0-1h4a.5.5 0 0 1 0 1z" /></svg>;
    case "edit":
      return <svg className={baseClass} viewBox="0 0 16 16" aria-hidden><path fill="currentColor" d="M12.146.146a.5.5 0 0 1 .708 0l3 3a.5.5 0 0 1 0 .708l-10 10a.5.5 0 0 1-.168.11l-5 2a.5.5 0 0 1-.65-.65l2-5a.5.5 0 0 1 .11-.168zM11.207 2.5 13.5 4.793 14.793 3.5 12.5 1.207zm1.586 3L10.5 3.207 4 9.707V10h.5a.5.5 0 0 1 .5.5v.5h.5a.5.5 0 0 1 .5.5v.5h.293zm-9.761 5.175-.106.106-1.528 3.821 3.821-1.528.106-.106A.5.5 0 0 1 5 12.5V12h-.5a.5.5 0 0 1-.5-.5V11h-.5a.5.5 0 0 1-.468-.325" /></svg>;
    case "external":
      return <svg className={baseClass} viewBox="0 0 16 16" aria-hidden><path fill="currentColor" fillRule="evenodd" d="M8.636 3.5a.5.5 0 0 0-.5-.5H1.5A1.5 1.5 0 0 0 0 4.5v10A1.5 1.5 0 0 0 1.5 16h10a1.5 1.5 0 0 0 1.5-1.5V7.864a.5.5 0 0 0-1 0V14.5a.5.5 0 0 1-.5.5h-10a.5.5 0 0 1-.5-.5v-10a.5.5 0 0 1 .5-.5h6.636a.5.5 0 0 0 .5-.5" /><path fill="currentColor" fillRule="evenodd" d="M16 .5a.5.5 0 0 0-.5-.5h-5a.5.5 0 0 0 0 1h3.793L6.146 9.146a.5.5 0 1 0 .708.708L15 1.707V5.5a.5.5 0 0 0 1 0z" /></svg>;
    case "inbox":
      return <svg className={baseClass} viewBox="0 0 16 16" aria-hidden><path fill="currentColor" d="M4.98 4a.5.5 0 0 0-.39.188L1.54 8H6a.5.5 0 0 1 .5.5 1.5 1.5 0 1 0 3 0A.5.5 0 0 1 10 8h4.46l-3.05-3.812A.5.5 0 0 0 11.02 4zm9.954 5H10.45a2.5 2.5 0 0 1-4.9 0H1.066l.32 2.562a.5.5 0 0 0 .497.438h12.234a.5.5 0 0 0 .496-.438zM3.809 3.563A1.5 1.5 0 0 1 4.981 3h6.038a1.5 1.5 0 0 1 1.172.563l3.7 4.625a.5.5 0 0 1 .105.374l-.39 3.124A1.5 1.5 0 0 1 14.117 13H1.883a1.5 1.5 0 0 1-1.489-1.314l-.39-3.124a.5.5 0 0 1 .106-.374z" /></svg>;
    case "info":
      return <svg className={baseClass} viewBox="0 0 16 16" aria-hidden><path fill="currentColor" d="M8 15A7 7 0 1 1 8 1a7 7 0 0 1 0 14m0 1A8 8 0 1 0 8 0a8 8 0 0 0 0 16" /><path fill="currentColor" d="m8.93 6.588-2.29.287-.082.38.45.083c.294.07.352.176.288.469l-.738 3.468c-.194.897.105 1.319.808 1.319.545 0 1.178-.252 1.465-.598l.088-.416c-.2.176-.492.246-.686.246-.275 0-.375-.193-.304-.533zM9 4.5a1 1 0 1 1-2 0 1 1 0 0 1 2 0" /></svg>;
    case "keyboard":
      return <svg className={baseClass} viewBox="0 0 16 16" aria-hidden><path fill="currentColor" d="M14 5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1H2a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1zM2 4a2 2 0 0 0-2 2v5a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2z" /><path fill="currentColor" d="M13 10.25a.25.25 0 0 1 .25-.25h.5a.25.25 0 0 1 .25.25v.5a.25.25 0 0 1-.25.25h-.5a.25.25 0 0 1-.25-.25zm0-2a.25.25 0 0 1 .25-.25h.5a.25.25 0 0 1 .25.25v.5a.25.25 0 0 1-.25.25h-.5a.25.25 0 0 1-.25-.25zm-5 0A.25.25 0 0 1 8.25 8h.5a.25.25 0 0 1 .25.25v.5a.25.25 0 0 1-.25.25h-.5A.25.25 0 0 1 8 8.75zm2 0a.25.25 0 0 1 .25-.25h1.5a.25.25 0 0 1 .25.25v.5a.25.25 0 0 1-.25.25h-1.5a.25.25 0 0 1-.25-.25zm1 2a.25.25 0 0 1 .25-.25h.5a.25.25 0 0 1 .25.25v.5a.25.25 0 0 1-.25.25h-.5a.25.25 0 0 1-.25-.25zm-5-2A.25.25 0 0 1 6.25 8h.5a.25.25 0 0 1 .25.25v.5a.25.25 0 0 1-.25.25h-.5A.25.25 0 0 1 6 8.75zm-2 0A.25.25 0 0 1 4.25 8h.5a.25.25 0 0 1 .25.25v.5a.25.25 0 0 1-.25.25h-.5A.25.25 0 0 1 4 8.75zm-2 0A.25.25 0 0 1 2.25 8h.5a.25.25 0 0 1 .25.25v.5a.25.25 0 0 1-.25.25h-.5A.25.25 0 0 1 2 8.75zm11-2a.25.25 0 0 1 .25-.25h.5a.25.25 0 0 1 .25.25v.5a.25.25 0 0 1-.25.25h-.5a.25.25 0 0 1-.25-.25zm-2 0a.25.25 0 0 1 .25-.25h.5a.25.25 0 0 1 .25.25v.5a.25.25 0 0 1-.25.25h-.5a.25.25 0 0 1-.25-.25zm-2 0A.25.25 0 0 1 9.25 6h.5a.25.25 0 0 1 .25.25v.5a.25.25 0 0 1-.25.25h-.5A.25.25 0 0 1 9 6.75zm-2 0A.25.25 0 0 1 7.25 6h.5a.25.25 0 0 1 .25.25v.5a.25.25 0 0 1-.25.25h-.5A.25.25 0 0 1 7 6.75zm-2 0A.25.25 0 0 1 5.25 6h.5a.25.25 0 0 1 .25.25v.5a.25.25 0 0 1-.25.25h-.5A.25.25 0 0 1 5 6.75zm-3 0A.25.25 0 0 1 2.25 6h1.5a.25.25 0 0 1 .25.25v.5a.25.25 0 0 1-.25.25h-1.5A.25.25 0 0 1 2 6.75zm0 4a.25.25 0 0 1 .25-.25h.5a.25.25 0 0 1 .25.25v.5a.25.25 0 0 1-.25.25h-.5a.25.25 0 0 1-.25-.25zm2 0a.25.25 0 0 1 .25-.25h5.5a.25.25 0 0 1 .25.25v.5a.25.25 0 0 1-.25.25h-5.5a.25.25 0 0 1-.25-.25z" /></svg>;
    case "list":
      return <svg className={baseClass} viewBox="0 0 16 16" aria-hidden><path fill="currentColor" fillRule="evenodd" d="M2.5 12a.5.5 0 0 1 .5-.5h10a.5.5 0 0 1 0 1H3a.5.5 0 0 1-.5-.5m0-4a.5.5 0 0 1 .5-.5h10a.5.5 0 0 1 0 1H3a.5.5 0 0 1-.5-.5m0-4a.5.5 0 0 1 .5-.5h10a.5.5 0 0 1 0 1H3a.5.5 0 0 1-.5-.5" /></svg>;
    case "message":
      return <svg className={baseClass} viewBox="0 0 16 16" aria-hidden><path fill="currentColor" d="M16 8c0 3.866-3.582 7-8 7a9 9 0 0 1-2.347-.306c-.584.296-1.925.864-4.181 1.234-.2.032-.352-.176-.273-.362.354-.836.674-1.95.77-2.966C.744 11.37 0 9.76 0 8c0-3.866 3.582-7 8-7s8 3.134 8 7M5 8a1 1 0 1 0-2 0 1 1 0 0 0 2 0m4 0a1 1 0 1 0-2 0 1 1 0 0 0 2 0m3 1a1 1 0 1 0 0-2 1 1 0 0 0 0 2" /></svg>;
    case "mic":
      return <svg className={baseClass} viewBox="0 0 16 16" aria-hidden><path fill="currentColor" d="M5 3a3 3 0 0 1 6 0v5a3 3 0 0 1-6 0z" /><path fill="currentColor" d="M3.5 6.5A.5.5 0 0 1 4 7v1a4 4 0 0 0 8 0V7a.5.5 0 0 1 1 0v1a5 5 0 0 1-4.5 4.975V15h3a.5.5 0 0 1 0 1h-7a.5.5 0 0 1 0-1h3v-2.025A5 5 0 0 1 3 8V7a.5.5 0 0 1 .5-.5" /></svg>;
    case "people":
      return <svg className={baseClass} viewBox="0 0 16 16" aria-hidden><path fill="currentColor" d="M7 14s-1 0-1-1 1-4 5-4 5 3 5 4-1 1-1 1zm4-6a3 3 0 1 0 0-6 3 3 0 0 0 0 6m-5.784 6A2.24 2.24 0 0 1 5 13c0-1.355.68-2.75 1.936-3.72A6.3 6.3 0 0 0 5 9c-4 0-5 3-5 4s1 1 1 1zM4.5 8a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5" /></svg>;
    case "phone":
      return <svg className={baseClass} viewBox="0 0 16 16" aria-hidden><path fill="currentColor" fillRule="evenodd" d="M1.885.511a1.745 1.745 0 0 1 2.61.163L6.29 2.98c.329.423.445.974.315 1.494l-.547 2.19a.68.68 0 0 0 .178.643l2.457 2.457a.68.68 0 0 0 .644.178l2.189-.547a1.75 1.75 0 0 1 1.494.315l2.306 1.794c.829.645.905 1.87.163 2.611l-1.034 1.034c-.74.74-1.846 1.065-2.877.702a18.6 18.6 0 0 1-7.01-4.42 18.6 18.6 0 0 1-4.42-7.009c-.362-1.03-.037-2.137.703-2.877z" /></svg>;
    case "pin":
      return <svg className={baseClass} viewBox="0 0 16 16" aria-hidden><path fill="currentColor" d="M9.828.722a.5.5 0 0 1 .354.146l4.95 4.95a.5.5 0 0 1 0 .707c-.48.48-1.072.588-1.503.588-.177 0-.335-.018-.46-.039l-3.134 3.134a6 6 0 0 1 .16 1.013c.046.702-.032 1.687-.72 2.375a.5.5 0 0 1-.707 0l-2.829-2.828-3.182 3.182c-.195.195-1.219.902-1.414.707s.512-1.22.707-1.414l3.182-3.182-2.828-2.829a.5.5 0 0 1 0-.707c.688-.688 1.673-.767 2.375-.72a6 6 0 0 1 1.013.16l3.134-3.133a3 3 0 0 1-.04-.461c0-.43.108-1.022.589-1.503a.5.5 0 0 1 .353-.146m.122 2.112v-.002zm0-.002v.002a.5.5 0 0 1-.122.51L6.293 6.878a.5.5 0 0 1-.511.12H5.78l-.014-.004a5 5 0 0 0-.288-.076 5 5 0 0 0-.765-.116c-.422-.028-.836.008-1.175.15l5.51 5.509c.141-.34.177-.753.149-1.175a5 5 0 0 0-.192-1.054l-.004-.013v-.001a.5.5 0 0 1 .12-.512l3.536-3.535a.5.5 0 0 1 .532-.115l.096.022c.087.017.208.034.344.034q.172.002.343-.04L9.927 2.028q-.042.172-.04.343a1.8 1.8 0 0 0 .062.46z" /></svg>;
    case "search":
      return <svg className={baseClass} viewBox="0 0 16 16" aria-hidden><path fill="currentColor" d="M11.742 10.344a6.5 6.5 0 1 0-1.397 1.398h-.001q.044.06.098.115l3.85 3.85a1 1 0 0 0 1.415-1.414l-3.85-3.85a1 1 0 0 0-.115-.1zM12 6.5a5.5 5.5 0 1 1-11 0 5.5 5.5 0 0 1 11 0" /></svg>;
    case "send":
      return <svg className={baseClass} viewBox="0 0 16 16" aria-hidden><path fill="currentColor" d="M15.964.686a.5.5 0 0 0-.65-.65L.767 5.855H.766l-.452.18a.5.5 0 0 0-.082.887l.41.26.001.002 4.995 3.178 3.178 4.995.002.002.26.41a.5.5 0 0 0 .886-.083zm-1.833 1.89L6.637 10.07l-.215-.338a.5.5 0 0 0-.154-.154l-.338-.215 7.494-7.494 1.178-.471z" /></svg>;
    case "sliders":
      return <svg className={baseClass} viewBox="0 0 16 16" aria-hidden><path fill="currentColor" fillRule="evenodd" d="M11.5 2a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3M9.05 3a2.5 2.5 0 0 1 4.9 0H16v1h-2.05a2.5 2.5 0 0 1-4.9 0H0V3zM4.5 7a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3M2.05 8a2.5 2.5 0 0 1 4.9 0H16v1H6.95a2.5 2.5 0 0 1-4.9 0H0V8zm9.45 4a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3m-2.45 1a2.5 2.5 0 0 1 4.9 0H16v1h-2.05a2.5 2.5 0 0 1-4.9 0H0v-1z" /></svg>;
    case "swap":
      return <svg className={baseClass} viewBox="0 0 16 16" aria-hidden><path fill="currentColor" fillRule="evenodd" d="M1 11.5a.5.5 0 0 0 .5.5h11.793l-3.147 3.146a.5.5 0 0 0 .708.708l4-4a.5.5 0 0 0 0-.708l-4-4a.5.5 0 0 0-.708.708L13.293 11H1.5a.5.5 0 0 0-.5.5m14-7a.5.5 0 0 1-.5.5H2.707l3.147 3.146a.5.5 0 1 1-.708.708l-4-4a.5.5 0 0 1 0-.708l4-4a.5.5 0 1 1 .708.708L2.707 4H14.5a.5.5 0 0 1 .5.5" /></svg>;
    case "task":
      return <svg className={baseClass} viewBox="0 0 16 16" aria-hidden><path fill="currentColor" d="M6.5 0A1.5 1.5 0 0 0 5 1.5v1A1.5 1.5 0 0 0 6.5 4h3A1.5 1.5 0 0 0 11 2.5v-1A1.5 1.5 0 0 0 9.5 0zm3 1a.5.5 0 0 1 .5.5v1a.5.5 0 0 1-.5.5h-3a.5.5 0 0 1-.5-.5v-1a.5.5 0 0 1 .5-.5z" /><path fill="currentColor" d="M4 1.5H3a2 2 0 0 0-2 2V14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V3.5a2 2 0 0 0-2-2h-1v1A2.5 2.5 0 0 1 9.5 5h-3A2.5 2.5 0 0 1 4 2.5zm6.854 7.354-3 3a.5.5 0 0 1-.708 0l-1.5-1.5a.5.5 0 0 1 .708-.708L7.5 10.793l2.646-2.647a.5.5 0 0 1 .708.708" /></svg>;
    case "wechat":
      return <svg className={baseClass} viewBox="0 0 16 16" aria-hidden><path fill="currentColor" d="M11.176 14.429c-2.665 0-4.826-1.8-4.826-4.018 0-2.22 2.159-4.02 4.824-4.02S16 8.191 16 10.411c0 1.21-.65 2.301-1.666 3.036a.32.32 0 0 0-.12.366l.218.81a.6.6 0 0 1 .029.117.166.166 0 0 1-.162.162.2.2 0 0 1-.092-.03l-1.057-.61a.5.5 0 0 0-.256-.074.5.5 0 0 0-.142.021 5.7 5.7 0 0 1-1.576.22M9.064 9.542a.647.647 0 1 0 .557-1 .645.645 0 0 0-.646.647.6.6 0 0 0 .09.353Zm3.232.001a.646.646 0 1 0 .546-1 .645.645 0 0 0-.644.644.63.63 0 0 0 .098.356" /><path fill="currentColor" d="M0 6.826c0 1.455.781 2.765 2.001 3.656a.385.385 0 0 1 .143.439l-.161.6-.1.373a.5.5 0 0 0-.032.14.19.19 0 0 0 .193.193q.06 0 .111-.029l1.268-.733a.6.6 0 0 1 .308-.088q.088 0 .171.025a6.8 6.8 0 0 0 1.625.26 4.5 4.5 0 0 1-.177-1.251c0-2.936 2.785-5.02 5.824-5.02l.15.002C10.587 3.429 8.392 2 5.796 2 2.596 2 0 4.16 0 6.826m4.632-1.555a.77.77 0 1 1-1.54 0 .77.77 0 0 1 1.54 0m3.875 0a.77.77 0 1 1-1.54 0 .77.77 0 0 1 1.54 0" /></svg>;
    default:
      return null;
  }
}

type CustomerWorkbenchAppProps = {
  token: string;
  embedded?: boolean;
  userName?: string;
  userRole?: string;
};

export function CustomerWorkbenchApp({ token, embedded = false, userName = "我", userRole = "当前用户" }: CustomerWorkbenchAppProps) {
  const initialParams = new URLSearchParams(window.location.search);
  const initialAccountId = initialParams.get("accountId")?.trim() || "";
  const initialMode: WorkbenchMode = initialParams.get("mode") === "existing" ? "existing" : "new";
  const [accounts, setAccounts] = useState<CustomerWorkbenchAccount[]>([]);
  const [workbenchMode, setWorkbenchMode] = useState<WorkbenchMode>(initialMode);
  const [activeAccountId, setActiveAccountId] = useState(initialAccountId);
  const [detail, setDetail] = useState<CustomerWorkbenchDetail | null>(null);
  const [activeTab, setActiveTab] = useState<DetailTab>("overview");
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState(defaultCustomerQueueFilter(initialMode));
  const [sort, setSort] = useState("priority");
  const [page, setPage] = useState(1);
  const [queueMeta, setQueueMeta] = useState({ totalElements: 0, totalPages: 0, filterCounts: {} as Record<string, number>, dataAsOf: "" });
  const [integration, setIntegration] = useState<{ ready: boolean; label: string; baseUrl?: string; message?: string }>({ ready: false, label: "正在连接 CRM" });
  const [notifications, setNotifications] = useState<Array<{ accountId: string; accountName: string; title: string; customerMode?: string }>>([]);
  const [supervisorSummary, setSupervisorSummary] = useState<{ visibleAccounts: number; riskAccounts: number; pendingRecommendations: number; writeSuccessRate: number } | null>(null);
  const [showNotifications, setShowNotifications] = useState(false);
  const [showQueueSettings, setShowQueueSettings] = useState(false);
  const [compactQueue, setCompactQueue] = useState(false);
  const [pageSize, setPageSize] = useState(12);
  const [assistantOpen, setAssistantOpen] = useState(true);
  const [assistantPinned, setAssistantPinned] = useState(true);
  const [editingRecommendation, setEditingRecommendation] = useState<CustomerRecommendation | null>(null);
  const [interactionEditorOpen, setInteractionEditorOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [notice, setNotice] = useState("");
  const [assistantInput, setAssistantInput] = useState("");
  const [assistantMessages, setAssistantMessages] = useState<ChatMessage[]>([
    { role: "assistant", text: "我可以根据当前工作台数据总结互动、查看风险、切换客户，或形成待确认的 CRM 落地建议。", time: nowTime() },
  ]);
  const recommendationRef = useRef<HTMLDivElement | null>(null);
  const composerInputRef = useRef<HTMLTextAreaElement | null>(null);
  const assistantChatRef = useRef<HTMLDivElement | null>(null);
  const searchInputRef = useRef<HTMLInputElement | null>(null);
  const deepLinkedAccountIdRef = useRef(initialAccountId);
  const previousAssistantAccountRef = useRef("");
  const voiceSessionIdRef = useRef(0);
  const { listening, speechSupported, start: startAsrSession, stop: stopAsrSession, abort: abortAsrSession } = useAsrVoiceInput();

  useEffect(() => {
    if (!token) return;
    let ignore = false;
    const timer = window.setTimeout(() => {
      setLoading(true);
      getCustomerWorkbenchQueue(token, { mode: workbenchMode, filter, sort, direction: "desc", query, page, size: pageSize })
        .then((result) => {
          if (ignore) return;
          setAccounts(result.items);
          setQueueMeta({ totalElements: result.totalElements, totalPages: result.totalPages, filterCounts: result.filterCounts, dataAsOf: result.dataAsOf || "" });
          setActiveAccountId((current) => {
            if (deepLinkedAccountIdRef.current && current === deepLinkedAccountIdRef.current) return current;
            return result.items.some((item) => item.accountId === current) ? current : result.items[0]?.accountId || "";
          });
        })
        .catch((error) => setNotice(error instanceof Error ? error.message : String(error)))
        .finally(() => !ignore && setLoading(false));
    }, 220);
    return () => {
      ignore = true;
      window.clearTimeout(timer);
    };
  }, [filter, page, pageSize, query, sort, token, workbenchMode]);

  useEffect(() => {
    if (!token) return;
    Promise.all([getCustomerWorkbenchIntegrationStatus(token), getCustomerWorkbenchNotifications(token)])
      .then(([status, items]) => { setIntegration(status); setNotifications(items); })
      .catch((error) => setIntegration({ ready: false, label: "CRM 连接异常", message: error instanceof Error ? error.message : String(error) }));
    getCustomerWorkbenchSupervisorSummary(token).then(setSupervisorSummary).catch(() => setSupervisorSummary(null));
  }, [token]);

  useEffect(() => {
    if (!token || !activeAccountId) {
      setDetail(null);
      return;
    }
    let ignore = false;
    setDetail(null);
    getCustomerWorkbenchDetail(token, activeAccountId)
      .then((item) => {
        if (!ignore) {
          setDetail(item);
          if (deepLinkedAccountIdRef.current === item.accountId) deepLinkedAccountIdRef.current = "";
        }
      })
      .catch((error) => {
        deepLinkedAccountIdRef.current = "";
        setNotice(error instanceof Error ? error.message : String(error));
      });
    return () => {
      ignore = true;
    };
  }, [token, activeAccountId]);

  const activeAccount = useMemo(() => accounts.find((item) => item.accountId === activeAccountId) ?? accounts[0], [accounts, activeAccountId]);

  useEffect(() => {
    const previous = previousAssistantAccountRef.current;
    previousAssistantAccountRef.current = activeAccountId;
    if (previous && previous !== activeAccountId && !assistantPinned) setAssistantOpen(false);
  }, [activeAccountId, assistantPinned]);

  useEffect(() => {
    if (!token || !activeAccountId) return;
    let ignore = false;
    const accountName = activeAccount?.name || "当前客户";
    setAssistantMessages([{ role: "assistant", text: `已进入${accountName}。可以查询互动、风险和 CRM 建议。`, time: nowTime() }]);
    getCustomerAssistantHistory(token, activeAccountId)
      .then((items) => {
        if (ignore || !items.length) return;
        setAssistantMessages(items.map((item) => ({
          role: item.role === "user" ? "user" : "assistant",
          text: item.content,
          time: chatTime(item.createdAt),
        })));
      })
      .catch(() => undefined);
    return () => { ignore = true; };
  }, [activeAccount?.name, activeAccountId, token]);

  useEffect(() => {
    const frame = window.requestAnimationFrame(() => scrollConversationToLatest(assistantChatRef.current));
    return () => window.cancelAnimationFrame(frame);
  }, [assistantMessages]);

  const switchMode = (mode: WorkbenchMode) => {
    setWorkbenchMode(mode);
    setFilter(defaultCustomerQueueFilter(mode));
    setPage(1);
    setActiveTab("overview");
    deepLinkedAccountIdRef.current = "";
    setActiveAccountId("");
  };

  const reloadDetail = async () => {
    if (!token || !activeAccountId) return;
    setDetail(await getCustomerWorkbenchDetail(token, activeAccountId));
    const result = await getCustomerWorkbenchQueue(token, { mode: workbenchMode, filter, sort, direction: "desc", query, page, size: pageSize, refresh: true });
    setAccounts(result.items);
    setQueueMeta({ totalElements: result.totalElements, totalPages: result.totalPages, filterCounts: result.filterCounts, dataAsOf: result.dataAsOf || "" });
  };

  const handleRecommendation = async (item: CustomerRecommendation, action: RecommendationAction) => {
    if (!token) return;
    try {
      if (action === "edit") {
        setEditingRecommendation(item);
        return;
      } else if (action === "accept") {
        await acceptCustomerRecommendation(token, item.recommendationId);
        setNotice("建议已采纳，请核对字段后确认执行。");
      } else if (action === "confirm") {
        await confirmCustomerRecommendation(token, item.recommendationId);
        setNotice("建议已确认，现在可以写入 CRM。");
      } else if (action === "dismiss") {
        await dismissCustomerRecommendation(token, item.recommendationId, "用户在客户互动工作台选择忽略");
        setNotice("建议已忽略，未写入 CRM。");
      } else {
        const result = await applyCustomerRecommendation(token, item.recommendationId);
        setNotice(result.message || "CRM 落地动作已完成。");
      }
      await reloadDetail();
    } catch (error) {
      setNotice(error instanceof Error ? error.message : String(error));
    }
  };

  const saveRecommendationEdit = async (draft: Partial<CustomerRecommendation>) => {
    if (!token || !editingRecommendation) return;
    try {
      await updateCustomerRecommendation(token, editingRecommendation.recommendationId, draft);
      setEditingRecommendation(null);
      setNotice("建议已更新，请重新采纳并确认后执行。");
      await reloadDetail();
    } catch (error) {
      setNotice(error instanceof Error ? error.message : String(error));
    }
  };

  const handleRecommendationFeedback = async (item: CustomerRecommendation, rating: "HELPFUL" | "NOT_HELPFUL") => {
    try {
      await submitCustomerRecommendationFeedback(token, item.recommendationId, rating);
      setNotice(rating === "HELPFUL" ? "已记录：该建议有帮助。" : "已记录：该建议需要改进。");
      await reloadDetail();
    } catch (error) {
      setNotice(error instanceof Error ? error.message : String(error));
    }
  };

  const handleAssistantResult = (result: CustomerAssistantResult) => {
    if (result.action === "SWITCH_ACCOUNT" && result.actionPayload?.accountId) {
      setActiveAccountId(result.actionPayload.accountId);
      setActiveTab("overview");
    }
    if (result.action === "FOCUS_RECOMMENDATIONS") {
      setActiveTab("recommendations");
      window.setTimeout(() => recommendationRef.current?.scrollIntoView({ behavior: "smooth", block: "start" }), 120);
    }
    if (result.action === "SWITCH_MODE" && result.actionPayload?.mode) switchMode(result.actionPayload.mode);
    if (result.action === "OPEN_TAB" && result.actionPayload?.tab) setActiveTab(result.actionPayload.tab as DetailTab);
    if (result.action === "SELECT_NEXT_ACCOUNT" && accounts.length) {
      const index = accounts.findIndex((item) => item.accountId === activeAccountId);
      setActiveAccountId(accounts[(index + 1) % accounts.length].accountId);
    }
    if (result.action === "PROPOSE_RECOMMENDATION") setActiveTab("recommendations");
  };

  const submitAssistant = async (preset?: string) => {
    const message = (preset ?? assistantInput).trim();
    if (!message || !token) return;
    voiceSessionIdRef.current += 1;
    abortAsrSession();
    setAssistantInput("");
    setAssistantMessages((prev) => [...prev, { role: "user", text: message, time: nowTime() }]);
    try {
      const result = await askCustomerWorkbenchAssistant(token, { accountId: activeAccountId, message });
      setAssistantMessages((prev) => [...prev, { role: "assistant", text: result.reply, time: nowTime() }]);
      handleAssistantResult(result);
    } catch (error) {
      setAssistantMessages((prev) => [...prev, { role: "assistant", text: error instanceof Error ? error.message : String(error), time: nowTime() }]);
    }
  };

  const startVoice = async () => {
    if (listening) {
      stopAsrSession();
      setNotice("正在结束语音录入...");
      return;
    }
    if (!speechSupported) {
      setNotice("当前浏览器不支持录音，可直接输入指令。");
      return;
    }
    const prefixBeforeSpeech = assistantInput;
    const voiceSessionId = voiceSessionIdRef.current + 1;
    voiceSessionIdRef.current = voiceSessionId;
    await startAsrSession({
      token,
      provider: "aliyun",
      speakerDiarization: false,
      getPrefix: () => prefixBeforeSpeech,
      onLiveText: (text) => {
        if (isCurrentVoiceSession(voiceSessionId, voiceSessionIdRef.current)) setAssistantInput(text);
      },
      onNotice: setNotice,
      onFinished: async ({ asrText, fullText }) => {
        if (!isCurrentVoiceSession(voiceSessionId, voiceSessionIdRef.current)) return;
        if (asrText) {
          setAssistantInput(fullText);
          setNotice("语音录入完成，内容已生成到输入框。");
        } else {
          setNotice("未识别到有效语音内容。");
        }
        window.setTimeout(() => composerInputRef.current?.focus(), 0);
      },
      autoStopAfterNoSpeechMs: 5000,
    });
  };

  if (!token) {
    return <section className="customer-workbench-empty">请先登录后使用客户互动工作台。</section>;
  }

  return (
    <section className={`customer-workbench${embedded ? " customer-workbench--embedded" : ""}`}>
      <header className="customer-workbench__topbar">
        <div className="customer-workbench__brand">
          <span className="customer-workbench__brand-mark" aria-hidden />
          <strong>AgentCiCi</strong>
          <em>AI 应用</em>
          <span>/</span>
          <b>客户互动工作台</b>
        </div>
        <div className="customer-workbench__top-actions">
          <div className="customer-workbench__mode-switch" aria-label="客户互动工作台模式">
            <button type="button" className={workbenchMode === "new" ? "is-active" : ""} onClick={() => switchMode("new")}>新客户推进</button>
            <button type="button" className={workbenchMode === "existing" ? "is-active" : ""} onClick={() => switchMode("existing")}>老客户经营</button>
          </div>
          <button type="button" className="customer-workbench__crm-state" onClick={() => void reloadDetail()} title={integration.message || "刷新 CRM 数据"}>
            <span aria-hidden className={integration.ready ? "is-ready" : "is-error"} />{integration.label}
          </button>
          <div className="customer-workbench__notification-wrap">
            <button type="button" className="customer-workbench__icon-button customer-workbench__icon-button--bell" aria-label="通知" onClick={() => setShowNotifications((value) => !value)} />
            {notifications.length ? <b className="customer-workbench__notification-count">{notifications.length}</b> : null}
            {showNotifications ? (
              <div className="customer-workbench__notification-popover">
                <strong>客户提醒</strong>
                {supervisorSummary ? <div className="customer-workbench__supervisor-summary">
                  <span>可见客户<b>{supervisorSummary.visibleAccounts}</b></span>
                  <span>风险客户<b>{supervisorSummary.riskAccounts}</b></span>
                  <span>待处理建议<b>{supervisorSummary.pendingRecommendations}</b></span>
                  <span>写回成功率<b>{supervisorSummary.writeSuccessRate}%</b></span>
                </div> : null}
                {notifications.length ? notifications.map((item) => (
                  <button key={`${item.accountId}-${item.title}`} type="button" onClick={() => {
                    const targetMode: WorkbenchMode = item.customerMode === "EXISTING" ? "existing" : "new";
                    if (targetMode !== workbenchMode) switchMode(targetMode);
                    deepLinkedAccountIdRef.current = item.accountId;
                    setActiveAccountId(item.accountId);
                    setShowNotifications(false);
                  }}>
                    <span>{item.accountName}</span><small>{item.title}</small>
                  </button>
                )) : <p>暂无待处理提醒</p>}
              </div>
            ) : null}
          </div>
          <button type="button" className="customer-workbench__icon-button customer-workbench__icon-button--help" aria-label="帮助" onClick={() => setNotice("数据来自当前用户有权访问的 CloudCC CRM；任何写回都需先确认。")}/>
          <div className="customer-workbench__profile">
            <i aria-hidden>{userName.trim().slice(0, 1) || "我"}</i>
            <span>{userName}</span>
            <small>{roleLabel(userRole)}</small>
          </div>
        </div>
      </header>

      <div className={`customer-workbench__body${assistantOpen ? "" : " is-assistant-closed"}`}>
        <aside className="customer-workbench__queue" aria-label={workbenchMode === "new" ? "新客户推进队列" : "老客户经营队列"}>
          <header>
            <div className="customer-workbench__queue-title">
              <small>CRM · {workbenchMode === "new" ? "新客户" : "存量客户"}</small>
              <strong>{workbenchMode === "new" ? "新客户推进队列" : "老客户经营队列"}</strong>
            </div>
            <div className="customer-workbench__queue-tools" aria-label="队列工具">
              <button type="button" aria-label="筛选" onClick={() => searchInputRef.current?.focus()}><Icon name="sliders" /></button>
              <button type="button" aria-label="列表设置" className={showQueueSettings ? "is-active" : ""} onClick={() => setShowQueueSettings((value) => !value)}><Icon name="list" /></button>
            </div>
          </header>
          {showQueueSettings ? (
            <div className="customer-workbench__queue-settings">
              <label><input type="checkbox" checked={compactQueue} onChange={(event) => setCompactQueue(event.target.checked)} />紧凑列表</label>
              <label>每页<select value={pageSize} onChange={(event) => { setPageSize(Number(event.target.value)); setPage(1); }}><option value="8">8</option><option value="12">12</option><option value="20">20</option></select></label>
              <button type="button" onClick={() => void reloadDetail()}>刷新 CRM 数据</button>
            </div>
          ) : null}
          <label className="customer-workbench__search">
            <span aria-hidden><Icon name="search" /></span>
            <input
              ref={searchInputRef}
              value={query}
              onChange={(event) => { setQuery(event.target.value); setPage(1); }}
              placeholder="搜索客户名称 / 负责人 / 关键字"
              aria-label="搜索客户"
            />
          </label>
          <nav aria-label="客户筛选">
            {modeFilterOptions[workbenchMode].map(([key, label]) => (
              <button key={key} type="button" className={filter === key ? "is-active" : ""} onClick={() => { setFilter(key); setPage(1); }}>
                {label}{queueMeta.filterCounts[key] !== undefined ? <small>{queueMeta.filterCounts[key]}</small> : null}
              </button>
            ))}
          </nav>
          <div className="customer-workbench__sortline">
            <select value={sort} onChange={(event) => { setSort(event.target.value); setPage(1); }} aria-label="客户排序">
              {workbenchMode === "new" ? <option value="priority">推进优先</option> : <option value="risk">风险优先</option>}
              <option value="interaction">最近互动</option>
              <option value="health">健康度</option>
              {workbenchMode === "existing" ? <option value="renewal">续约日期</option> : null}
            </select>
            <span>共 {queueMeta.totalElements} 位客户</span>
            <button type="button" aria-label="列表密度"><Icon name="list" /></button>
          </div>
          <div className={`customer-workbench__accounts${compactQueue ? " is-compact" : ""}`}>
            {accounts.map((item) => (
              <button
                key={item.accountId}
                type="button"
                className={`customer-workbench-account${item.accountId === activeAccountId ? " is-active" : ""}`}
                onClick={() => setActiveAccountId(item.accountId)}
              >
                <span className={`customer-workbench-account__dot is-${item.segment.toLowerCase()}`} />
                <span className="customer-workbench-account__body">
                  <span className="customer-workbench-account__title">
                    <strong>{item.name}</strong>
                    <em className={queueStatusClass(item)}>{queueStatus(item)}</em>
                  </span>
                  <span className="customer-workbench-account__meta">
                    <small>{item.owner} · {item.stage}</small>
                    <time>{shortDate(item.updatedAt || "") || "今天 09:30"}</time>
                  </span>
                  <span className="customer-workbench-account__badges">
                    {workbenchMode === "new" ? <em>商机 {item.opportunityCount ?? 0}</em> : <em>健康 {item.healthScore}</em>}
                    {item.riskCount ? <em className="is-risk">{workbenchMode === "new" ? "风险信号" : "关系风险"} {item.riskCount}</em> : null}
                    {item.pendingRecommendationCount ? <em className="is-warn">{workbenchMode === "new" ? "未确认建议" : "经营动作"} {item.pendingRecommendationCount}</em> : null}
                  </span>
                  {item.lastInteraction ? <span className="customer-workbench-account__last">{item.lastInteraction}</span> : null}
                </span>
              </button>
            ))}
            {loading ? <p className="customer-workbench__muted">正在加载客户...</p> : null}
          </div>
          <footer className="customer-workbench__pager">
            <button type="button" disabled={page <= 1} onClick={() => setPage((value) => Math.max(1, value - 1))}>‹</button>
            <span>{queueMeta.totalPages ? page : 0} / {queueMeta.totalPages}</span>
            <button type="button" disabled={page >= queueMeta.totalPages} onClick={() => setPage((value) => value + 1)}>›</button>
          </footer>
        </aside>

        <main className="customer-workbench__main">
          <header className="customer-workbench__head">
            <div>
              <h2>{detail?.name || activeAccount?.name || "客户互动工作台"} <button type="button" className="customer-workbench__more-menu" aria-label="客户更多操作" onClick={async () => {
                const link = new URL(window.location.href);
                if (!embedded) link.searchParams.set("aiApp", "customer-workbench");
                link.searchParams.set("accountId", activeAccountId);
                link.searchParams.set("mode", workbenchMode);
                await navigator.clipboard.writeText(link.toString());
                setNotice("客户工作台链接已复制。");
              }}>···</button></h2>
              <p className="customer-workbench__entity-line">
                <em>Account</em>
                <button type="button" onClick={() => setActiveTab(workbenchMode === "new" ? "signals" : "renewal")}>Opportunity <b>{detail?.opportunityCount ?? activeAccount?.opportunityCount ?? 0}</b></button>
                <span>{detail?.owner || activeAccount?.owner || "负责人"}</span>
                <button type="button" className={detail?.followed ? "is-followed" : ""} onClick={async () => {
                  if (!activeAccountId) return;
                  try { await setCustomerFollowed(token, activeAccountId, !detail?.followed); await reloadDetail(); }
                  catch (error) { setNotice(error instanceof Error ? error.message : String(error)); }
                }}>{detail?.followed ? "已关注" : "关注"}</button>
                <span>最近互动：{shortDate(detail?.updatedAt || activeAccount?.updatedAt || "")}（{lifecycleSourceLabel(detail?.lastInteractionType || "CRM")}）</span>
              </p>
            </div>
            <button type="button" className="customer-workbench__open-crm" disabled={!integration.baseUrl || !activeAccountId} onClick={() => {
              if (!integration.baseUrl || !activeAccountId) return;
              window.open(`${integration.baseUrl.replace(/\/$/, "")}/#/commonObjects/detail/${encodeURIComponent(activeAccountId)}/DETAIL`, "_blank", "noopener,noreferrer");
            }}>打开 CRM 客户主页 <Icon name="external" /></button>
            {!assistantOpen ? <button type="button" onClick={() => setAssistantOpen(true)}>打开 AI 助理</button> : null}
          </header>

        {notice ? <div className="customer-workbench__notice">{notice}</div> : null}

        <section className="customer-workbench__metrics" aria-label="客户指标">
          {workbenchMode === "new" ? (
            <>
              <Metric label="未确认建议" value={metricValue(detail, "pendingRecommendations", detail?.pendingRecommendationCount ?? 0)} suffix="" onClick={() => setActiveTab("recommendations")} />
              <Metric label="风险信号" value={metricValue(detail, "risks", detail?.riskCount ?? 0)} suffix="" onClick={() => setActiveTab("signals")} />
              <Metric label="下一步任务" value={metricValue(detail, "nextActions", detail?.nextActionCount ?? 0)} suffix="" onClick={() => setActiveTab("actions")} />
              <Metric label="最近互动" value={metricValue(detail, "interactions", detail?.timeline?.length ?? 0)} suffix="" onClick={() => setActiveTab("timeline")} />
            </>
          ) : (
            <>
              <Metric label="客户健康度" value={metricValue(detail, "health", detail?.healthScore ?? 0)} suffix="" onClick={() => setActiveTab("overview")} />
              <Metric label="续约倒计时" value={metricValue(detail, "renewalDays", detail?.renewalDays ?? -1) < 0 ? "待确认" : metricValue(detail, "renewalDays", detail?.renewalDays ?? -1)} suffix={metricValue(detail, "renewalDays", detail?.renewalDays ?? -1) < 0 ? "" : "天"} onClick={() => setActiveTab("renewal")} />
              <Metric label="未闭环问题" value={metricValue(detail, "openIssues", 0)} suffix="" onClick={() => setActiveTab("service")} />
              <Metric label="增购信号" value={metricValue(detail, "expansionSignals", 0)} suffix="" onClick={() => setActiveTab("renewal")} />
            </>
          )}
        </section>

        <nav className="customer-workbench__tabs" aria-label="客户详情视图">
          {modeTabs[workbenchMode].map(([key, label]) => (
            <button key={key} type="button" className={activeTab === key ? "is-active" : ""} onClick={() => setActiveTab(key)}>
              {label}
            </button>
          ))}
        </nav>

        <section className="customer-workbench__content">
          {activeTab === "overview" ? <Overview detail={detail} mode={workbenchMode} onAction={handleRecommendation} onFeedback={handleRecommendationFeedback} onNotice={setNotice} onOpenTab={setActiveTab} /> : null}
          {activeTab === "timeline" ? <Timeline detail={detail} /> : null}
          {activeTab === "signals" ? <NewCustomerPanel detail={detail} /> : null}
          {activeTab === "service" ? <ExistingCustomerPanel detail={detail} focus="service" /> : null}
          {activeTab === "value" ? <ExistingCustomerPanel detail={detail} focus="value" /> : null}
          {activeTab === "renewal" ? <ExistingCustomerPanel detail={detail} focus="renewal" /> : null}
          {activeTab === "relationship" ? <ExistingCustomerPanel detail={detail} focus="relationship" /> : null}
          {activeTab === "recommendations" ? (
            <div ref={recommendationRef}>
              <Recommendations detail={detail} onAction={handleRecommendation} onFeedback={handleRecommendationFeedback} onNotice={setNotice} />
            </div>
          ) : null}
          {activeTab === "actions" ? <NextActionPanel detail={detail} onAction={handleRecommendation} /> : null}
        </section>
      </main>

      {assistantOpen ? <aside className="customer-workbench__assistant" aria-label="AI 客户助理">
        <header>
          <div>
            <strong>AI 客户助理</strong>
            <Icon name="info" />
          </div>
          <div className="customer-workbench__assistant-tools">
            <button type="button" aria-label={assistantPinned ? "取消固定" : "固定"} className={assistantPinned ? "is-active" : ""} onClick={() => setAssistantPinned((value) => !value)}><Icon name="pin" /></button>
            <button type="button" aria-label="关闭" onClick={() => setAssistantOpen(false)}><Icon name="close" /></button>
          </div>
        </header>
        <div className="customer-workbench__chat" ref={assistantChatRef}>
          <div className="customer-workbench__dayline">今天</div>
          {assistantMessages.map((message, index) => (
            <div key={`${message.time}-${index}`} className={`customer-workbench-message is-${message.role}`}>
              {message.role === "assistant" ? <span className="customer-workbench-message__avatar" aria-hidden><Icon name="bot" /></span> : null}
              <div className="customer-workbench-message__body">
                <p>{message.text}</p>
                <span>{message.time}</span>
              </div>
              {message.role === "user" ? <span className="customer-workbench-message__avatar is-user" aria-hidden>{userName.trim().slice(0, 1) || "我"}</span> : null}
            </div>
          ))}
        </div>
        <div className="customer-workbench__quick-actions">
          <button type="button" onClick={() => void submitAssistant("为当前客户生成跟进任务建议")}>生成跟进任务</button>
          <button type="button" onClick={() => void submitAssistant("查看当前客户的风险信号")}>查看风险</button>
          <button type="button" onClick={() => setInteractionEditorOpen(true)}>整理互动记录</button>
          <button type="button" onClick={() => void submitAssistant("切换到下一个客户")}>切换下个客户</button>
        </div>
        <div className="customer-workbench__composer">
          <textarea ref={composerInputRef} value={assistantInput} onChange={(event) => setAssistantInput(event.target.value)} placeholder="输入问题或指令..." />
          <div className="customer-workbench__composer-actions">
            <button
              type="button"
              className={`customer-workbench__composer-icon${listening ? " is-recording" : ""}`}
              onClick={() => void startVoice()}
              aria-label={listening ? "停止语音输入" : "语音输入"}
              disabled={!speechSupported}
            >
              <Icon name="mic" />
            </button>
            <button type="button" className="customer-workbench__send" onClick={() => void submitAssistant()} aria-label="发送"><Icon name="send" /></button>
          </div>
        </div>
        <p className="customer-workbench__ai-note">AI 生成内容仅供参考，请结合实际情况判断</p>
      </aside> : null}
      </div>
      {editingRecommendation ? (
        <RecommendationEditor key={editingRecommendation.recommendationId} item={editingRecommendation}
          onClose={() => setEditingRecommendation(null)} onSave={saveRecommendationEdit} />
      ) : null}
      {interactionEditorOpen ? (
        <InteractionEditor onClose={() => setInteractionEditorOpen(false)} onSave={async (draft) => {
          if (!activeAccountId) return;
          try {
            const saved = await saveCustomerInteraction(token, activeAccountId, draft);
            setInteractionEditorOpen(false);
            setNotice(saved.deduplicated ? "该互动记录已存在，未重复保存。" : "互动记录已确认并进入时间线。");
            await reloadDetail();
            setActiveTab("timeline");
          } catch (error) {
            setNotice(error instanceof Error ? error.message : String(error));
          }
        }} />
      ) : null}
    </section>
  );
}

function Metric({ label, value, suffix, onClick }: { label: string; value: number | string; suffix: string; onClick: () => void }) {
  return (
    <button type="button" onClick={onClick}>
      <i aria-hidden><Icon name={metricIconName(label)} /></i>
      <span>{label}</span>
      <strong>{value}<small>{suffix}</small></strong>
      <em aria-hidden>›</em>
    </button>
  );
}

function Overview({
  detail,
  mode,
  onAction,
  onFeedback,
  onNotice,
  onOpenTab,
}: {
  detail: CustomerWorkbenchDetail | null;
  mode: WorkbenchMode;
  onAction: (item: CustomerRecommendation, action: RecommendationAction) => void;
  onFeedback: (item: CustomerRecommendation, rating: "HELPFUL" | "NOT_HELPFUL") => void;
  onNotice: (message: string) => void;
  onOpenTab: (tab: DetailTab) => void;
}) {
  return (
    <div className="customer-workbench-overview-wrap">
      <div className="customer-workbench-overview">
        <section className="customer-workbench-panel customer-workbench-panel--timeline">
          <header>
            <h3>{mode === "new" ? "新客户互动时间线" : "老客户互动时间线"}</h3>
            <button type="button" onClick={() => onOpenTab("timeline")}>全部类型⌄</button>
          </header>
          <TimelineCards detail={detail} compact />
          <button type="button" className="customer-workbench__more" onClick={() => onOpenTab("timeline")}>查看全部互动记录 ›</button>
        </section>
        <section className="customer-workbench-panel customer-workbench-panel--recommendations">
          <header>
            <h3>{mode === "new" ? "CRM 落地建议" : "老客户经营动作"}（{detail?.recommendations?.length ?? 0}）</h3>
            <button type="button" onClick={() => onOpenTab("recommendations")}>{mode === "new" ? "全部建议" : "按影响排序"}⌄</button>
          </header>
          <Recommendations detail={detail} onAction={onAction} onFeedback={onFeedback} onNotice={onNotice} compact />
          <button type="button" className="customer-workbench__more" onClick={() => onOpenTab("recommendations")}>{mode === "new" ? "查看全部建议" : "查看全部经营动作"} ›</button>
        </section>
      </div>
      <WorkbenchBottomPanel detail={detail} mode={mode} />
    </div>
  );
}

function Timeline({ detail }: { detail: CustomerWorkbenchDetail | null }) {
  const [source, setSource] = useState("all");
  const events = detail?.timeline ?? [];
  return (
    <div className="customer-workbench-timeline-page">
      <header><h3>互动时间线</h3><select aria-label="互动来源" value={source} onChange={(event) => setSource(event.target.value)}>
        <option value="all">全部类型</option>
        <option value="wechat">微信</option><option value="phone">电话</option><option value="meeting">会议</option><option value="task">CRM 任务</option>
      </select></header>
      <TimelineCards detail={{ ...detail, timeline: source === "all" ? events : events.filter((item) => {
        const normalized = item.sourceType.toUpperCase();
        if (source === "wechat") return normalized.includes("WECHAT");
        if (source === "phone") return normalized.includes("PHONE");
        if (source === "meeting") return normalized.includes("MEETING") || normalized.includes("EVENT");
        return normalized.includes("TASK");
      }) } as CustomerWorkbenchDetail} />
    </div>
  );
}

function TimelineCards({ detail, compact = false }: { detail: CustomerWorkbenchDetail | null; compact?: boolean }) {
  return (
    <div className={`customer-workbench-timeline${compact ? " is-compact" : ""}`}>
      {(detail?.timeline ?? []).slice(0, compact ? 5 : undefined).map((item) => (
        <article key={item.eventId}>
          <time>{shortDate(item.occurredAt)}</time>
          <span className={`customer-workbench-timeline__icon is-${lifecycleSourceLabel(item.sourceType)}`} aria-hidden>
            <Icon name={sourceIconName(item.sourceType)} />
          </span>
          <div>
            <strong>{item.subject}</strong>
            <p>{item.summary}</p>
            <span>来源：{lifecycleSourceLabel(item.sourceType)} · {item.lifecycleArea}</span>
            {item.intentTags?.[0] ? <em>{item.intentTags[0]}</em> : null}
          </div>
        </article>
      ))}
    </div>
  );
}

function NewCustomerPanel({ detail }: { detail: CustomerWorkbenchDetail | null }) {
  const score = detail?.progressScore ?? 0;
  const signals = detail?.newCustomerSignals ?? [];
  const actions = detail?.nextActions ?? [];
  const gaps = (detail?.signals ?? []).filter((item) => item.type.includes("GAP") || item.type.includes("OVERDUE"));
  return (
    <div className="customer-workbench-signals">
      <header>
        <div>
          <h3>新客户推进</h3>
          <p>围绕需求、预算、决策链和商机动作判断推进质量。</p>
        </div>
        <strong>{score}<small>分</small></strong>
      </header>
      <div className="customer-workbench-stage">
        {["初步接触", "需求确认", "方案沟通", "评估决策", "商机推进"].map((stage, index) => (
          <span key={stage} className={index <= Math.min(3, Math.floor(score / 24)) ? "is-done" : ""}>{stage}</span>
        ))}
      </div>
      <div className="customer-workbench-signal-grid">
        <section>
          <h4>推进信号</h4>
          <List items={signals} empty="暂无明确信号" />
        </section>
        <section>
          <h4>建议动作</h4>
          <List items={actions} empty="暂无建议动作" />
        </section>
        <section>
          <h4>CRM 补齐项</h4>
          <List items={gaps.map((item) => item.detail)} empty="当前 CRM 关键字段无明显缺口" />
        </section>
      </div>
    </div>
  );
}

function ExistingCustomerPanel({
  detail,
  focus = "service",
}: {
  detail: CustomerWorkbenchDetail | null;
  focus?: "service" | "value" | "renewal" | "relationship";
}) {
  const score = detail?.healthScore ?? 0;
  const focusCopy = {
    service: ["服务问题", "聚焦未闭环工单、服务压力和异常反馈。"],
    value: ["价值兑现", "对照客户承诺、使用反馈和业务收益沉淀复盘材料。"],
    renewal: ["续约增购", "关注续约倒计时、合同风险、增购触发信号。"],
    relationship: ["关系地图", "检查关键人覆盖、角色缺口和沟通频率。"],
  }[focus];
  const focusItems: Array<{ title: string; detail: string; meta: string }> = focus === "service"
    ? (detail?.serviceIssues ?? []).map((item) => ({ title: item.title || item.number, detail: item.description || "CRM 个案未填写问题描述", meta: `${item.status || "待处理"} · ${item.priority || "普通"}` }))
    : focus === "value"
      ? (detail?.valueItems ?? []).map((item) => ({ title: item.title, detail: `金额 ${Number(item.amount || 0).toLocaleString("zh-CN")}`, meta: `${item.source} · ${item.status || "状态待确认"}` }))
      : focus === "renewal"
        ? [...(detail?.renewal?.contracts ?? []).map((item) => ({ title: item.title, detail: `合同状态：${item.status || "待确认"}`, meta: `距最近到期 ${detail?.renewal?.days ?? -1} 天` })),
            ...(detail?.renewal?.opportunities ?? []).map((item) => ({ title: String(item.name || "业务机会"), detail: String(item.nextStep || "下一步待补齐"), meta: String(item.stage || "阶段待确认") }))]
        : (detail?.relationshipMap ?? []).map((item) => ({ title: item.name, detail: `${item.title || "职务待补"} · ${item.role || "角色待补"}`, meta: item.lastContactAt ? `最近联系 ${shortDate(item.lastContactAt)}` : "最近联系待补" }));
  return (
    <div className="customer-workbench-signals">
      <header>
        <div>
          <h3>{focusCopy[0]}</h3>
          <p>{focusCopy[1]}</p>
        </div>
        <strong>{score}<small>分</small></strong>
      </header>
      <div className="customer-workbench-health-grid">
        <strong>健康度<small>{metricValue(detail, "health", score)} 分</small></strong>
        <strong>未闭环服务<small>{metricValue(detail, "openIssues", 0)} 个</small></strong>
        <strong>增购机会<small>{metricValue(detail, "expansionSignals", 0)} 个</small></strong>
        <strong>关系覆盖<small>{detail?.relationshipMap?.length ?? 0} 人</small></strong>
      </div>
      <div className="customer-workbench-record-grid">
        {focusItems.map((item, index) => <article key={`${item.title}-${index}`}><strong>{item.title}</strong><p>{item.detail}</p><span>{item.meta}</span></article>)}
        {!focusItems.length ? <p className="customer-workbench__muted">当前用户可见的 CRM 数据中暂无相关记录。</p> : null}
      </div>
    </div>
  );
}

function WorkbenchBottomPanel({ detail, mode }: { detail: CustomerWorkbenchDetail | null; mode: WorkbenchMode }) {
  const items = (detail?.signals ?? []).filter((item) => mode === "new" ? item.mode === "NEW" : item.mode === "EXISTING").slice(0, 3);
  return (
    <section className="customer-workbench-bottom-panel" aria-label={mode === "new" ? "推进关键项" : "服务与关系预警"}>
      <header>
        <h3>{mode === "new" ? "推进关键项" : "服务与关系预警"}</h3>
        <span>AI 从工单、会议、微信和 CRM 更新中提取</span>
      </header>
      <div>
        {items.map((item) => (
          <article key={item.type}>
            <strong>{item.title}</strong>
            <p>{item.detail}</p>
            <em>{item.severity === "HIGH" ? "高优先级" : item.severity === "MEDIUM" ? "需关注" : "信息"}</em>
          </article>
        ))}
        {!items.length ? <p className="customer-workbench__muted">当前没有需要提示的关键项。</p> : null}
      </div>
      {detail?.summary ? <p className="customer-workbench-bottom-panel__summary">{detail.summary}</p> : null}
    </section>
  );
}

function NextActionPanel({ detail, onAction }: { detail: CustomerWorkbenchDetail | null; onAction: (item: CustomerRecommendation, action: RecommendationAction) => void }) {
  const taskRecommendation = detail?.recommendations?.find((item) => item.type === "CREATE_TASK" && item.status !== "APPLIED" && item.status !== "DISMISSED");
  return (
    <div className="customer-workbench-actions">
      {(detail?.nextActions ?? []).map((item, index) => (
        <article key={`${item}-${index}`}>
          <strong>{item}</strong>
          <p>建议负责人在 24 小时内确认并同步到 CRM 任务。</p>
          <button type="button" disabled={!taskRecommendation} onClick={() => taskRecommendation && onAction(taskRecommendation, "accept")}>形成待确认任务</button>
        </article>
      ))}
      {detail?.nextActions?.length ? null : <p className="customer-workbench__muted">暂无下一步行动。</p>}
    </div>
  );
}

function Recommendations({
  detail,
  onAction,
  onFeedback,
  onNotice,
  compact = false,
}: {
  detail: CustomerWorkbenchDetail | null;
  onAction: (item: CustomerRecommendation, action: RecommendationAction) => void;
  onFeedback: (item: CustomerRecommendation, rating: "HELPFUL" | "NOT_HELPFUL") => void;
  onNotice: (message: string) => void;
  compact?: boolean;
}) {
  const items = detail?.recommendations ?? [];
  const [expandedEvidenceId, setExpandedEvidenceId] = useState("");
  return (
    <div className={`customer-workbench-recommendations${compact ? " is-compact" : ""}`}>
      {items.slice(0, compact ? 4 : undefined).map((item, index) => (
        <article key={item.recommendationId}>
          <header>
            <i className={`is-${recommendationIconName(item.type, index)}`} aria-hidden>
              <Icon name={recommendationIconName(item.type, index)} />
            </i>
            <div>
              <strong>{item.title}</strong>
              <span>{item.rationale}</span>
            </div>
          </header>
          <p><b>置信度</b>{formatConfidence(item.confidence)} <span>依据：{item.evidence?.length ? String(item.evidence.length) + " 条 CRM 事实" : "当前客户 CRM 数据"}</span></p>
          {!compact && item.evidence?.length ? <div className="customer-workbench-recommendation__evidence">
            <button type="button" onClick={() => setExpandedEvidenceId((current) => current === item.recommendationId ? "" : item.recommendationId)}>
              {expandedEvidenceId === item.recommendationId ? "收起依据" : `查看依据 (${item.evidence.length})`}
            </button>
            {expandedEvidenceId === item.recommendationId ? <ul>{item.evidence.map((evidence, evidenceIndex) => <li key={`${item.recommendationId}-${evidenceIndex}`}>{evidenceLabel(evidence)}</li>)}</ul> : null}
          </div> : null}
          {item.lastErrorMessage ? <p className="customer-workbench-recommendation__error">上次执行失败：{item.lastErrorMessage}</p> : null}
          <footer>
            {item.status === "PENDING" ? <button type="button" onClick={() => onAction(item, "accept")}><Icon name="check" />采纳</button> : null}
            {item.status === "ACCEPTED" ? <button type="button" onClick={() => onAction(item, "confirm")}><Icon name="check" />确认</button> : null}
            {item.status === "CONFIRMED" || item.status === "FAILED" ? <button type="button" onClick={() => onAction(item, "apply")}><Icon name="check" />{item.status === "FAILED" ? "重试" : "写入 CRM"}</button> : null}
            {item.status === "APPLYING" ? <button type="button" disabled><Icon name="check" />执行中</button> : null}
            {item.status === "APPLIED" ? <button type="button" disabled><Icon name="check" />已写入</button> : null}
            {item.status !== "APPLIED" && item.status !== "APPLYING" && item.status !== "DISMISSED" ? <button type="button" onClick={() => onAction(item, "edit")}><Icon name="edit" />修改</button> : null}
            {item.status !== "APPLIED" && item.status !== "APPLYING" && item.status !== "DISMISSED" ? <button type="button" onClick={() => onAction(item, "dismiss")}><Icon name="close" />忽略</button> : null}
            {item.status === "DISMISSED" ? <span>已忽略</span> : null}
          </footer>
          {!compact ? <div className="customer-workbench-recommendation__feedback">
            <span>这条建议是否有帮助？</span>
            <button type="button" className={item.feedback?.rating === "HELPFUL" ? "is-active" : ""} onClick={() => onFeedback(item, "HELPFUL")}>有帮助</button>
            <button type="button" className={item.feedback?.rating === "NOT_HELPFUL" ? "is-active" : ""} onClick={() => onFeedback(item, "NOT_HELPFUL")}>需改进</button>
          </div> : null}
        </article>
      ))}
      {!items.length ? <p className="customer-workbench__muted">当前没有 CRM 落地建议。</p> : null}
    </div>
  );
}

function RecommendationEditor({ item, onClose, onSave }: {
  item: CustomerRecommendation;
  onClose: () => void;
  onSave: (draft: Partial<CustomerRecommendation>) => Promise<void>;
}) {
  const [title, setTitle] = useState(item.title);
  const [rationale, setRationale] = useState(item.rationale);
  const targetObject = item.targetObject || (item.type.includes("OPPORTUNITY") ? "Opportunity" : "Task");
  const [recordName, setRecordName] = useState(String(item.crmPayload?.name || item.crmPayload?.subject || item.title));
  const [expiredate, setExpiredate] = useState(String(item.crmPayload?.expiredate || ""));
  const [stage, setStage] = useState(String(item.crmPayload?.jieduan || "1-发现机会"));
  const [nextStep, setNextStep] = useState(String(item.crmPayload?.xyb || item.rationale));
  return (
    <div className="customer-workbench-dialog" role="dialog" aria-modal="true" aria-labelledby="recommendation-editor-title">
      <form onSubmit={(event) => {
        event.preventDefault();
        void onSave({
          title,
          rationale,
          targetObject: item.targetObject,
          targetRecordId: item.targetRecordId,
          crmPayload: targetObject === "Opportunity"
            ? { ...item.crmPayload, name: recordName, jieduan: stage, xyb: nextStep }
            : { ...item.crmPayload, subject: recordName, ...(expiredate ? { expiredate } : {}) },
        });
      }}>
        <header>
          <h3 id="recommendation-editor-title">修改 CRM 落地建议</h3>
          <button type="button" onClick={onClose} aria-label="关闭"><Icon name="close" /></button>
        </header>
        <label>建议标题<input value={title} onChange={(event) => setTitle(event.target.value)} required /></label>
        <label>建议依据<textarea value={rationale} onChange={(event) => setRationale(event.target.value)} required /></label>
        <label>{targetObject === "Opportunity" ? "业务机会名称" : "CRM 任务主题"}<input value={recordName} onChange={(event) => setRecordName(event.target.value)} required /></label>
        {targetObject === "Opportunity" ? <>
          <label>业务机会阶段<input value={stage} onChange={(event) => setStage(event.target.value)} required /></label>
          <label>下一步<textarea value={nextStep} onChange={(event) => setNextStep(event.target.value)} required /></label>
        </> : <label>到期日期<input type="date" value={expiredate} onChange={(event) => setExpiredate(event.target.value)} /></label>}
        <footer><button type="button" onClick={onClose}>取消</button><button type="submit">保存修改</button></footer>
      </form>
    </div>
  );
}

function InteractionEditor({ onClose, onSave }: {
  onClose: () => void;
  onSave: (draft: { sourceType: "WECHAT" | "PHONE" | "MEETING" | "CUSTOMER_FEEDBACK"; subject: string; content: string; occurredAt: string }) => Promise<void>;
}) {
  const [sourceType, setSourceType] = useState<"WECHAT" | "PHONE" | "MEETING" | "CUSTOMER_FEEDBACK">("WECHAT");
  const [subject, setSubject] = useState("");
  const [content, setContent] = useState("");
  const [occurredAt, setOccurredAt] = useState(() => {
    const now = new Date();
    now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
    return now.toISOString().slice(0, 16);
  });
  return (
    <div className="customer-workbench-dialog" role="dialog" aria-modal="true" aria-labelledby="interaction-editor-title">
      <form onSubmit={(event) => {
        event.preventDefault();
        void onSave({ sourceType, subject, content, occurredAt: new Date(occurredAt).toISOString() });
      }}>
        <header><h3 id="interaction-editor-title">整理互动记录</h3><button type="button" onClick={onClose} aria-label="关闭"><Icon name="close" /></button></header>
        <label>互动来源<select value={sourceType} onChange={(event) => setSourceType(event.target.value as typeof sourceType)}>
          <option value="WECHAT">微信</option><option value="PHONE">电话</option><option value="MEETING">会议</option><option value="CUSTOMER_FEEDBACK">客户反馈</option>
        </select></label>
        <label>发生时间<input type="datetime-local" value={occurredAt} onChange={(event) => setOccurredAt(event.target.value)} required /></label>
        <label>主题<input value={subject} onChange={(event) => setSubject(event.target.value)} placeholder="可留空，由系统按来源生成" /></label>
        <label>互动内容<textarea value={content} onChange={(event) => setContent(event.target.value)} minLength={10} maxLength={10000} required placeholder="粘贴微信聊天、电话纪要、会议摘要或客户反馈" /></label>
        <footer><button type="button" onClick={onClose}>取消</button><button type="submit">确认保存</button></footer>
      </form>
    </div>
  );
}

function List({ items, empty }: { items: string[]; empty: string }) {
  if (!items.length) return <p className="customer-workbench__muted">{empty}</p>;
  return (
    <ul>
      {items.map((item) => <li key={item}>{item}</li>)}
    </ul>
  );
}
