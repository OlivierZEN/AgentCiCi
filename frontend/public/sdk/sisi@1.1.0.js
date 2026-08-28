(function (global) {
  "use strict";
  var SOURCE = "agentcici-sisi-embed";
  var current = document.currentScript;
  var sdkUrl = current && current.src ? new URL(current.src, global.location.href) : null;
  if (!sdkUrl) throw new Error("AgentCiCi SDK 必须通过 script src 加载");
  var serviceOrigin = sdkUrl.origin;

  function element(value) {
    if (typeof value === "string") return document.querySelector(value);
    return value instanceof HTMLElement ? value : null;
  }

  function text(value, fallback) {
    var normalized = typeof value === "string" ? value.trim() : "";
    return normalized || fallback;
  }

  function instance(options) {
    var config = Object.assign({ mode: "float", width: 408, height: 680, zIndex: 2147483000 }, options || {});
    var mode = config.mode === "page" ? "page" : "float";
    var destroyed = false;
    var opened = mode === "page";
    var token = typeof config.token === "string" ? config.token : "";
    var assistantName = text(config.assistantName, "AgentCiCi");
    var launcherLabel = text(config.launcherLabel, "咨询智能体");
    var launcherInitial = text(config.launcherInitial, "Ci").slice(0, 2);
    var host = document.createElement("div");
    var iframe = document.createElement("iframe");
    var launcher = null;
    var handlers = new Map();

    host.dataset.agentciciSisi = "1";
    iframe.src = serviceOrigin + "/embed/sisi?mode=" + mode;
    iframe.title = assistantName;
    iframe.allow = "microphone";
    iframe.referrerPolicy = "strict-origin-when-cross-origin";
    iframe.style.cssText = "width:100%;height:100%;border:0;background:transparent;display:block;";

    if (mode === "page") {
      var container = element(config.container);
      if (!container) throw new Error("page 模式必须提供有效的 container");
      host.style.cssText = "width:100%;height:100%;min-height:" + (config.minHeight || 620) + "px;overflow:hidden;";
      host.appendChild(iframe);
      container.appendChild(host);
    } else {
      host.style.cssText = [
        "position:fixed", "right:" + (config.right || 24) + "px", "bottom:" + (config.bottom || 88) + "px",
        "width:min(" + config.width + "px,calc(100vw - 24px))", "height:min(" + config.height + "px,calc(100vh - 112px))",
        "z-index:" + config.zIndex, "border-radius:18px", "overflow:hidden", "opacity:0", "pointer-events:none",
        "transform:translateY(10px) scale(.985)", "transition:opacity .16s ease,transform .16s ease"
      ].join(";");
      host.appendChild(iframe);
      document.body.appendChild(host);
      launcher = document.createElement("button");
      launcher.type = "button";
      launcher.setAttribute("aria-label", "打开" + assistantName);
      launcher.setAttribute("aria-expanded", "false");
      var mark = document.createElement("span");
      mark.setAttribute("aria-hidden", "true");
      mark.textContent = launcherInitial;
      var label = document.createElement("span");
      label.textContent = launcherLabel;
      launcher.appendChild(mark);
      launcher.appendChild(label);
      launcher.style.cssText = [
        "position:fixed", "right:" + (config.right || 24) + "px", "bottom:" + (config.bottom || 24) + "px",
        "z-index:" + config.zIndex, "height:48px", "padding:0 16px 0 9px", "display:flex", "align-items:center", "gap:9px",
        "border:1px solid rgba(92,67,25,.3)", "border-radius:24px", "color:#242019", "background:#fffaf0",
        "box-shadow:0 8px 28px rgba(43,33,19,.2)", "font:600 14px system-ui,sans-serif", "cursor:pointer"
      ].join(";");
      mark.style.cssText = "width:32px;height:32px;display:grid;place-items:center;border-radius:9px;color:#fff8eb;background:#9b4b38;font:700 15px system-ui,sans-serif;";
      launcher.addEventListener("click", function () { opened ? api.close() : api.open(); });
      document.body.appendChild(launcher);
    }

    function emit(name, payload) {
      var set = handlers.get(name);
      if (set) set.forEach(function (callback) { try { callback(payload); } catch (_) {} });
      if (typeof config.onEvent === "function") config.onEvent(name, payload);
    }

    function resolveToken(force) {
      if (!force && token) return Promise.resolve(token);
      if (typeof config.tokenProvider !== "function") return Promise.resolve(token);
      return Promise.resolve(config.tokenProvider(config.context || {})).then(function (next) {
        if (typeof next !== "string" || !next.trim()) throw new Error("tokenProvider 未返回 Embed Token");
        token = next.trim();
        return token;
      });
    }

    function send(type, payload, forceToken) {
      if (destroyed || !iframe.contentWindow) return Promise.resolve();
      return resolveToken(forceToken).then(function (next) {
        iframe.contentWindow.postMessage({ source: SOURCE, type: type, token: next, payload: payload || {} }, serviceOrigin);
      });
    }

    function onMessage(event) {
      if (event.origin !== serviceOrigin || event.source !== iframe.contentWindow) return;
      var message = event.data;
      if (!message || message.source !== SOURCE || typeof message.type !== "string") return;
      if (message.type === "embed:frame-ready") send("host:init", { mode: mode });
      if (message.type === "embed:token-required") {
        token = "";
        send("host:update-token", {}, true).catch(function (error) { emit("error", { message: error.message }); });
      }
      if (message.type === "embed:close" && mode === "float") api.close();
      if (message.type === "embed:expand" && mode === "float") {
        host.style.width = "min(720px,calc(100vw - 24px))";
        host.style.height = "min(820px,calc(100vh - 24px))";
        host.style.bottom = "12px";
      }
      emit(message.type, message.payload || {});
    }
    global.addEventListener("message", onMessage);

    var api = {
      open: function () {
        if (destroyed) return;
        opened = true;
        if (mode === "float") {
          host.style.opacity = "1"; host.style.pointerEvents = "auto"; host.style.transform = "none";
          launcher.setAttribute("aria-expanded", "true");
        }
        send("host:focus");
      },
      close: function () {
        if (destroyed || mode === "page") return;
        opened = false;
        host.style.opacity = "0"; host.style.pointerEvents = "none"; host.style.transform = "translateY(10px) scale(.985)";
        launcher.setAttribute("aria-expanded", "false");
      },
      focus: function () { send("host:focus"); },
      updateToken: function (next) {
        token = typeof next === "string" ? next.trim() : "";
        return send("host:update-token", {});
      },
      updateContext: function (context) {
        config.context = context || {};
        token = "";
        return send("host:update-token", { context: config.context }, true);
      },
      on: function (name, callback) {
        if (!handlers.has(name)) handlers.set(name, new Set());
        handlers.get(name).add(callback);
        return function () { handlers.get(name).delete(callback); };
      },
      destroy: function () {
        if (destroyed) return;
        destroyed = true;
        global.removeEventListener("message", onMessage);
        host.remove();
        if (launcher) launcher.remove();
        handlers.clear();
        token = "";
      },
      frame: iframe
    };

    iframe.addEventListener("load", function () { send("host:init", { mode: mode }); });
    if (mode === "float" && config.open !== false) api.open();
    return api;
  }

  global.AgentCiCiSisi = Object.freeze({
    version: "1.1.0",
    create: instance,
    open: function (options) { return instance(Object.assign({}, options, { mode: "float", open: true })); }
  });
})(window);
