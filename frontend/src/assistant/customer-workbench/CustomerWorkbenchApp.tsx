import { useEffect, useMemo, useRef, useState } from "react";
import { useAsrVoiceInput } from "../../shared/useAsrVoiceInput";
import {
  acceptCustomerRecommendation,
  applyCustomerRecommendation,
  askCustomerWorkbenchAssistant,
  getCustomerWorkbenchDetail,
  listCustomerWorkbenchAccounts,
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
type RecommendationAction = "accept" | "apply";
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
    ["all", "重点推进"],
    ["follow", "待跟进"],
    ["risk", "风险客户"],
    ["recommendations", "待确认建议"],
  ],
  existing: [
    ["all", "续约90天"],
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

function nowTime() {
  const now = new Date();
  return `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}`;
}

function segmentLabel(segment: string) {
  return segmentLabels[segment] ?? segment;
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
  if (value.includes("微信")) return "微信";
  if (value.includes("电话")) return "通话录音";
  if (value.includes("会议")) return "会议纪要";
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
};

export function CustomerWorkbenchApp({ token, embedded = false }: CustomerWorkbenchAppProps) {
  const [accounts, setAccounts] = useState<CustomerWorkbenchAccount[]>([]);
  const [workbenchMode, setWorkbenchMode] = useState<WorkbenchMode>("new");
  const [activeAccountId, setActiveAccountId] = useState("");
  const [detail, setDetail] = useState<CustomerWorkbenchDetail | null>(null);
  const [activeTab, setActiveTab] = useState<DetailTab>("overview");
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState("all");
  const [loading, setLoading] = useState(false);
  const [notice, setNotice] = useState("");
  const [assistantInput, setAssistantInput] = useState("");
  const [assistantMessages, setAssistantMessages] = useState<ChatMessage[]>([
    { role: "user", text: "总结这个客户最近三次沟通", time: "09:35" },
    {
      role: "assistant",
      text: "好的，这是北京智造科技有限公司最近三次沟通的总结：\n\n1. 方案评审会（04-01 14:00）\n技术和采购团队参与，确定方案架构，建议增加数据分层和权限管理。\n\n2. 电话回访（04-02 16:20）\n客户对报价和服务条款有疑问，需内部评估后回复。\n\n3. 微信沟通（04-03 09:30）\n关注实施周期和与现有 MES 系统集成能力。\n\n需要我帮你生成跟进任务或查看风险信号吗？",
      time: "09:35",
    },
  ]);
  const recommendationRef = useRef<HTMLDivElement | null>(null);
  const composerInputRef = useRef<HTMLTextAreaElement | null>(null);
  const { listening, speechSupported, start: startAsrSession, stop: stopAsrSession } = useAsrVoiceInput();

  useEffect(() => {
    if (!token) return;
    let ignore = false;
    setLoading(true);
    listCustomerWorkbenchAccounts(token)
      .then((items) => {
        if (ignore) return;
        setAccounts(items);
        setActiveAccountId((current) => current || items[0]?.accountId || "");
      })
      .catch((error) => setNotice(error instanceof Error ? error.message : String(error)))
      .finally(() => !ignore && setLoading(false));
    return () => {
      ignore = true;
    };
  }, [token]);

  useEffect(() => {
    if (!token || !activeAccountId) return;
    let ignore = false;
    getCustomerWorkbenchDetail(token, activeAccountId)
      .then((item) => {
        if (!ignore) setDetail(item);
      })
      .catch((error) => setNotice(error instanceof Error ? error.message : String(error)));
    return () => {
      ignore = true;
    };
  }, [token, activeAccountId]);

  const modeBaseAccounts = useMemo(() => {
    const matched = accounts.filter((item) => {
      if (workbenchMode === "new") return item.segment === "NEW" || item.segment === "RISK";
      return item.segment === "EXISTING" || item.segment === "STRATEGIC";
    });
    return matched.length ? matched : accounts;
  }, [accounts, workbenchMode]);

  useEffect(() => {
    if (!modeBaseAccounts.length) return;
    if (!modeBaseAccounts.some((item) => item.accountId === activeAccountId)) {
      setActiveAccountId(modeBaseAccounts[0].accountId);
    }
  }, [activeAccountId, modeBaseAccounts]);

  const filteredAccounts = useMemo(() => {
    const text = query.trim().toLowerCase();
    return modeBaseAccounts.filter((item) => {
      if (workbenchMode === "new") {
        if (filter === "follow" && item.nextActionCount < 1) return false;
        if (filter === "risk" && item.segment !== "RISK" && item.riskCount < 1) return false;
        if (filter === "recommendations" && item.pendingRecommendationCount < 1) return false;
      } else {
        if (filter === "health" && item.healthScore >= 78 && item.riskCount < 1) return false;
        if (filter === "service" && item.riskCount < 1) return false;
        if (filter === "expansion" && item.progressScore < 70 && item.pendingRecommendationCount < 1) return false;
      }
      if (!text) return true;
      return `${item.name} ${item.owner} ${item.stage} ${item.tags?.join(" ")}`.toLowerCase().includes(text);
    });
  }, [filter, modeBaseAccounts, query, workbenchMode]);

  const activeAccount = modeBaseAccounts.find((item) => item.accountId === activeAccountId) ?? modeBaseAccounts[0] ?? accounts[0];

  const switchMode = (mode: WorkbenchMode) => {
    setWorkbenchMode(mode);
    setFilter("all");
    setActiveTab("overview");
    const nextAccount = accounts.find((item) => (
      mode === "new"
        ? item.segment === "NEW" || item.segment === "RISK"
        : item.segment === "EXISTING" || item.segment === "STRATEGIC"
    ));
    if (nextAccount) setActiveAccountId(nextAccount.accountId);
  };

  const reloadDetail = async () => {
    if (!token || !activeAccountId) return;
    setDetail(await getCustomerWorkbenchDetail(token, activeAccountId));
    setAccounts(await listCustomerWorkbenchAccounts(token));
  };

  const handleRecommendation = async (item: CustomerRecommendation, action: RecommendationAction) => {
    if (!token) return;
    try {
      if (action === "accept") {
        await acceptCustomerRecommendation(token, item.recommendationId);
        setNotice("建议已采纳，确认后可继续落地到 CRM。");
      } else {
        const result = await applyCustomerRecommendation(token, item.recommendationId);
        setNotice(result.message || "CRM 落地动作已记录。");
      }
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
  };

  const submitAssistant = async (preset?: string) => {
    const message = (preset ?? assistantInput).trim();
    if (!message || !token) return;
    setAssistantMessages((prev) => [...prev, { role: "user", text: message, time: nowTime() }]);
    setAssistantInput("");
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
    await startAsrSession({
      token,
      provider: "aliyun",
      speakerDiarization: false,
      getPrefix: () => prefixBeforeSpeech,
      onLiveText: setAssistantInput,
      onNotice: setNotice,
      onFinished: async ({ asrText, fullText }) => {
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
          <button type="button" className="customer-workbench__crm-state"><span aria-hidden />CloudCC CRM 已连接</button>
          <button type="button" className="customer-workbench__icon-button customer-workbench__icon-button--bell" aria-label="通知" />
          <button type="button" className="customer-workbench__icon-button customer-workbench__icon-button--help" aria-label="帮助" />
          <div className="customer-workbench__profile">
            <i aria-hidden>张</i>
            <span>张伟</span>
            <small>销售主管</small>
          </div>
        </div>
      </header>

      <div className="customer-workbench__body">
        <aside className="customer-workbench__queue" aria-label={workbenchMode === "new" ? "新客户推进队列" : "老客户经营队列"}>
          <header>
            <div className="customer-workbench__queue-title">
              <small>CRM · {workbenchMode === "new" ? "新客户" : "存量客户"}</small>
              <strong>{workbenchMode === "new" ? "新客户推进队列" : "老客户经营队列"}</strong>
            </div>
            <div className="customer-workbench__queue-tools" aria-label="队列工具">
              <button type="button" aria-label="筛选"><Icon name="sliders" /></button>
              <button type="button" aria-label="列表设置"><Icon name="list" /></button>
            </div>
          </header>
          <label className="customer-workbench__search">
            <span aria-hidden><Icon name="search" /></span>
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="搜索客户名称 / 负责人 / 关键字"
              aria-label="搜索客户"
            />
          </label>
          <nav aria-label="客户筛选">
            {modeFilterOptions[workbenchMode].map(([key, label]) => (
              <button key={key} type="button" className={filter === key ? "is-active" : ""} onClick={() => setFilter(key)}>
                {label}
              </button>
            ))}
          </nav>
          <div className="customer-workbench__sortline">
            <button type="button">{workbenchMode === "new" ? "推进优先" : "风险优先"}⌄</button>
            <span>共 {Math.max(modeBaseAccounts.length, workbenchMode === "new" ? 38 : 26)} 位客户</span>
            <button type="button" aria-label="列表密度"><Icon name="list" /></button>
          </div>
          <div className="customer-workbench__accounts">
            {filteredAccounts.map((item) => (
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
                    {workbenchMode === "new" ? <em>商机 {Math.max(1, Math.round(item.progressScore / 28))}</em> : <em>健康 {item.healthScore}</em>}
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
            <button type="button">‹</button>
            <span>1 / 5</span>
            <button type="button">›</button>
          </footer>
        </aside>

        <main className="customer-workbench__main">
          <header className="customer-workbench__head">
          <div>
            <h2>{detail?.name || activeAccount?.name || "客户互动工作台"} <span aria-hidden>···</span></h2>
            <p className="customer-workbench__entity-line">
              <em>Account</em>
              <span>Opportunity <b>{Math.max(1, Math.round((detail?.progressScore ?? activeAccount?.progressScore ?? 0) / 24))}</b></span>
              <span>{detail?.owner || activeAccount?.owner || "负责人"}（销售主管）</span>
              <em>关注</em>
              <span>最近互动：今天 09:30（微信沟通）</span>
            </p>
          </div>
          <button type="button" onClick={() => setNotice("已打开 CRM 客户主页入口，演示环境使用工作台内联详情。")}>
            打开 CRM 客户主页
            <Icon name="external" />
          </button>
        </header>

        {notice ? <div className="customer-workbench__notice">{notice}</div> : null}

        <section className="customer-workbench__metrics" aria-label="客户指标">
          {workbenchMode === "new" ? (
            <>
              <Metric label="未确认建议" value={detail?.pendingRecommendationCount ?? activeAccount?.pendingRecommendationCount ?? 0} suffix="" />
              <Metric label="风险信号" value={detail?.riskCount ?? activeAccount?.riskCount ?? 0} suffix="" />
              <Metric label="下一步任务" value={detail?.nextActionCount ?? activeAccount?.nextActionCount ?? 0} suffix="" />
              <Metric label="最近互动" value={Math.max(12, detail?.timeline?.length ?? 0)} suffix="" />
            </>
          ) : (
            <>
              <Metric label="客户健康度" value={detail?.healthScore ?? activeAccount?.healthScore ?? 0} suffix="" />
              <Metric label="续约倒计时" value={Math.max(21, 90 - (detail?.riskCount ?? activeAccount?.riskCount ?? 0) * 16)} suffix="天" />
              <Metric label="未闭环问题" value={Math.max(1, (detail?.riskCount ?? activeAccount?.riskCount ?? 0) + 3)} suffix="" />
              <Metric label="增购信号" value={Math.max(1, detail?.pendingRecommendationCount ?? activeAccount?.pendingRecommendationCount ?? 0)} suffix="" />
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
          {activeTab === "overview" ? <Overview detail={detail} mode={workbenchMode} onAction={handleRecommendation} onNotice={setNotice} /> : null}
          {activeTab === "timeline" ? <Timeline detail={detail} /> : null}
          {activeTab === "signals" ? <NewCustomerPanel detail={detail} /> : null}
          {activeTab === "service" ? <ExistingCustomerPanel detail={detail} focus="service" /> : null}
          {activeTab === "value" ? <ExistingCustomerPanel detail={detail} focus="value" /> : null}
          {activeTab === "renewal" ? <ExistingCustomerPanel detail={detail} focus="renewal" /> : null}
          {activeTab === "relationship" ? <ExistingCustomerPanel detail={detail} focus="relationship" /> : null}
          {activeTab === "recommendations" ? (
            <div ref={recommendationRef}>
              <Recommendations detail={detail} onAction={handleRecommendation} onNotice={setNotice} />
            </div>
          ) : null}
          {activeTab === "actions" ? <NextActionPanel detail={detail} /> : null}
        </section>
      </main>

      <aside className="customer-workbench__assistant" aria-label="AI 客户助理">
        <header>
          <div>
            <strong>AI 客户助理</strong>
            <Icon name="info" />
          </div>
          <div className="customer-workbench__assistant-tools">
            <button type="button" aria-label="固定"><Icon name="pin" /></button>
            <button type="button" aria-label="关闭"><Icon name="close" /></button>
          </div>
        </header>
        <div className="customer-workbench__chat">
          <div className="customer-workbench__dayline">今天</div>
          {assistantMessages.map((message, index) => (
            <div key={`${message.time}-${index}`} className={`customer-workbench-message is-${message.role}`}>
              {message.role === "assistant" ? <span className="customer-workbench-message__avatar" aria-hidden><Icon name="bot" /></span> : null}
              <div className="customer-workbench-message__body">
                <p>{message.text}</p>
                <span>{message.time}</span>
              </div>
              {message.role === "user" ? <span className="customer-workbench-message__avatar is-user" aria-hidden>张</span> : null}
            </div>
          ))}
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
      </aside>
      </div>
    </section>
  );
}

function Metric({ label, value, suffix }: { label: string; value: number; suffix: string }) {
  return (
    <div>
      <i aria-hidden><Icon name={metricIconName(label)} /></i>
      <span>{label}</span>
      <strong>{value}<small>{suffix}</small></strong>
      <em aria-hidden>›</em>
    </div>
  );
}

function Overview({
  detail,
  mode,
  onAction,
  onNotice,
}: {
  detail: CustomerWorkbenchDetail | null;
  mode: WorkbenchMode;
  onAction: (item: CustomerRecommendation, action: RecommendationAction) => void;
  onNotice: (message: string) => void;
}) {
  return (
    <div className="customer-workbench-overview-wrap">
      <div className="customer-workbench-overview">
        <section className="customer-workbench-panel customer-workbench-panel--timeline">
          <header>
            <h3>{mode === "new" ? "新客户互动时间线" : "老客户互动时间线"}</h3>
            <button type="button">全部类型⌄</button>
          </header>
          <TimelineCards detail={detail} compact />
          <button type="button" className="customer-workbench__more">查看全部互动记录 ›</button>
        </section>
        <section className="customer-workbench-panel customer-workbench-panel--recommendations">
          <header>
            <h3>{mode === "new" ? "CRM 落地建议" : "老客户经营动作"}（{detail?.recommendations?.length ?? 0}）</h3>
            <button type="button">{mode === "new" ? "全部建议" : "按影响排序"}⌄</button>
          </header>
          <Recommendations detail={detail} onAction={onAction} onNotice={onNotice} compact />
          <button type="button" className="customer-workbench__more">{mode === "new" ? "查看全部建议" : "查看全部经营动作"} ›</button>
        </section>
      </div>
      <WorkbenchBottomPanel detail={detail} mode={mode} />
    </div>
  );
}

function Timeline({ detail }: { detail: CustomerWorkbenchDetail | null }) {
  return <TimelineCards detail={detail} />;
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
          <List items={["补齐预算范围", "确认决策人和评审时间", "创建或更新商机阶段"]} empty="暂无补齐项" />
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
  const signals = detail?.existingCustomerSignals ?? [];
  const risks = detail?.risks ?? [];
  const focusCopy = {
    service: ["服务问题", "聚焦未闭环工单、服务压力和异常反馈。"],
    value: ["价值兑现", "对照客户承诺、使用反馈和业务收益沉淀复盘材料。"],
    renewal: ["续约增购", "关注续约倒计时、合同风险、增购触发信号。"],
    relationship: ["关系地图", "检查关键人覆盖、角色缺口和沟通频率。"],
  }[focus];
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
        <strong>续约稳定<small>{Math.max(62, score)}%</small></strong>
        <strong>服务响应<small>{Math.max(58, score - 8)}%</small></strong>
        <strong>增购机会<small>{Math.max(42, Math.round((detail?.progressScore ?? 0) * .9))}%</small></strong>
        <strong>关系覆盖<small>{detail?.contact ? "2 人" : "待补"}</small></strong>
      </div>
      <div className="customer-workbench-signal-grid">
        <section>
          <h4>经营信号</h4>
          <List items={signals} empty="暂无经营信号" />
        </section>
        <section>
          <h4>风险与阻塞</h4>
          <List items={risks} empty="暂无高风险" />
        </section>
        <section>
          <h4>客户价值动作</h4>
          <List items={["安排季度复盘", "补齐关键联系人", "沉淀服务改进任务"]} empty="暂无动作" />
        </section>
      </div>
    </div>
  );
}

function WorkbenchBottomPanel({ detail, mode }: { detail: CustomerWorkbenchDetail | null; mode: WorkbenchMode }) {
  const items = mode === "new"
    ? [
        ["决策链待确认", "技术负责人已参与，但采购负责人和最终审批人仍需确认。", "决策风险"],
        ["预算边界不清", "报价条款存在疑问，需要补充 ROI、实施成本和付款方案。", "预算待定"],
        ["集成方案缺口", "客户重点关注 MES 集成，需要沉淀接口边界和权限方案。", "方案补齐"],
      ]
    : [
        ["关键联系人覆盖不足", "技术负责人参与频繁，但采购负责人近 45 天无互动。", "关系风险"],
        ["服务闭环压力上升", "本月 3 个工单，其中 1 个已升级，2 个临近 SLA。", "服务风险"],
        ["价值证明材料缺口", "客户认可效率提升，但缺少面向管理层的量化报告。", "QBR待补"],
      ];
  return (
    <section className="customer-workbench-bottom-panel" aria-label={mode === "new" ? "推进关键项" : "服务与关系预警"}>
      <header>
        <h3>{mode === "new" ? "推进关键项" : "服务与关系预警"}</h3>
        <span>AI 从工单、会议、微信和 CRM 更新中提取</span>
      </header>
      <div>
        {items.map(([title, text, tag]) => (
          <article key={title}>
            <strong>{title}</strong>
            <p>{text}</p>
            <em>{tag}</em>
          </article>
        ))}
      </div>
      {detail?.summary ? <p className="customer-workbench-bottom-panel__summary">{detail.summary}</p> : null}
    </section>
  );
}

function NextActionPanel({ detail }: { detail: CustomerWorkbenchDetail | null }) {
  return (
    <div className="customer-workbench-actions">
      {(detail?.nextActions ?? []).map((item, index) => (
        <article key={`${item}-${index}`}>
          <strong>{item}</strong>
          <p>建议负责人在 24 小时内确认并同步到 CRM 任务。</p>
          <button type="button">生成跟进任务</button>
        </article>
      ))}
      {detail?.nextActions?.length ? null : <p className="customer-workbench__muted">暂无下一步行动。</p>}
    </div>
  );
}

function Recommendations({
  detail,
  onAction,
  onNotice,
  compact = false,
}: {
  detail: CustomerWorkbenchDetail | null;
  onAction: (item: CustomerRecommendation, action: RecommendationAction) => void;
  onNotice: (message: string) => void;
  compact?: boolean;
}) {
  const items = detail?.recommendations ?? [];
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
          <p><b>置信度</b>{formatConfidence(item.confidence)} <span>依据：通话录音、微信记录（2条）</span></p>
          <footer>
            <button type="button" onClick={() => onAction(item, "accept")} disabled={item.status === "APPLIED"}><Icon name="check" />采纳</button>
            <button type="button" onClick={() => onNotice("已进入建议修改状态，可在后续版本打开内联编辑。")}><Icon name="edit" />修改</button>
            <button type="button" onClick={() => onNotice("已忽略该建议，本次不写入 CRM。")}><Icon name="close" />忽略</button>
          </footer>
        </article>
      ))}
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
