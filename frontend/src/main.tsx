import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import AppErrorBoundary from "./components/AppErrorBoundary";
import "./styles.css";
import "./assistant/cici-ui.css";

const rootNode = document.getElementById("root");
if (!rootNode) {
  throw new Error("Root element '#root' not found.");
}
const rootEl: HTMLElement = rootNode;

function renderFatal(message: string, stack: string) {
  rootEl.innerHTML = `
    <main style="min-height:100vh;display:flex;align-items:center;justify-content:center;background:linear-gradient(175deg,#faf8f5 0%,#efe9df 100%);padding:24px;margin:0">
      <section style="width:min(980px,100%);background:rgba(255,252,247,.98);border:1px solid rgba(28,25,23,.09);border-radius:16px;box-shadow:0 20px 48px rgba(28,25,23,.08);padding:22px;font-family:'Plus Jakarta Sans','PingFang SC','Microsoft YaHei',sans-serif">
        <h1 style="margin:0 0 10px;font-size:24px;color:#1c1917;font-family:Fraunces,Georgia,serif">页面初始化失败</h1>
        <p style="margin:0 0 12px;color:#78716c;line-height:1.5">捕获到全局未处理异常，已阻止白屏。请复制信息给开发人员。</p>
        <div style="font-size:13px;color:#44403c;margin-bottom:6px;font-weight:600">错误信息</div>
        <pre style="margin:0;padding:12px;border-radius:10px;border:1px solid rgba(28,25,23,.08);background:#f7f2ea;color:#1c1917;font-size:12px;white-space:pre-wrap;overflow:auto;max-height:320px">${escapeHtml(message + "\\n" + stack)}</pre>
      </section>
    </main>
  `;
}

function escapeHtml(v: string) {
  return String(v)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;");
}

window.addEventListener("error", (ev) => {
  const msg = ev.message || "Unknown global error";
  const stack = (ev.error && ev.error.stack) || "";
  console.error("[GlobalError]", ev.error || msg);
  renderFatal(msg, stack);
});

window.addEventListener("unhandledrejection", (ev) => {
  const reason = ev.reason instanceof Error ? ev.reason : new Error(String(ev.reason));
  console.error("[UnhandledRejection]", reason);
  renderFatal(reason.message || "Unhandled rejection", reason.stack || "");
});

ReactDOM.createRoot(rootEl).render(
  <React.StrictMode>
    <AppErrorBoundary>
      <App />
    </AppErrorBoundary>
  </React.StrictMode>,
);
