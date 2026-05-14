(function (window, document) {
  "use strict";

  var VERSION = "1.0.0";
  var SDK_SOURCE = "agentcici-meeting-sdk";
  var EMBED_SOURCE = "agentcici-meeting-embed";
  var STYLE_ID = "agentcici-meeting-sdk-style";
  var INSTANCE_ATTR = "data-agentcici-meeting-instance";
  var idSeed = 0;

  function currentScriptOrigin() {
    var script = document.currentScript;
    if (script && script.src) {
      try {
        return new URL(script.src, window.location.href).origin;
      } catch (error) {
        return window.location.origin;
      }
    }
    return window.location.origin;
  }

  function resolveContainer(container) {
    if (!container) {
      return null;
    }
    if (typeof container === "string") {
      return document.querySelector(container);
    }
    if (container.nodeType === 1) {
      return container;
    }
    return null;
  }

  function clampDrawerWidth(value) {
    var requested = Number(value) || 960;
    var max = Math.min(1120, Math.max(320, window.innerWidth - 32));
    var min = Math.min(720, max);
    return Math.max(min, Math.min(requested, max));
  }

  function ensureStyles() {
    if (document.getElementById(STYLE_ID)) {
      return;
    }
    var style = document.createElement("style");
    style.id = STYLE_ID;
    style.textContent = [
      ".agentcici-meeting-backdrop{position:fixed;inset:0;z-index:2147483000;background:rgba(43,34,23,.18);}",
      ".agentcici-meeting-shell{position:fixed;top:0;right:0;bottom:0;z-index:2147483001;background:#fffdf8;color:#2b2217;border-left:1px solid #b99652;box-shadow:0 18px 34px rgba(43,34,23,.14);}",
      ".agentcici-meeting-frame{display:block;width:100%;height:100%;border:0;background:#fffdf8;}",
      ".agentcici-meeting-inline{position:relative;width:100%;height:100%;min-height:560px;background:#fffdf8;border:1px solid #ded2bb;}",
      ".agentcici-meeting-inline .agentcici-meeting-frame{position:absolute;inset:0;}",
      "@media (max-width:760px){.agentcici-meeting-shell{left:0;width:100%!important;border-left:0;}.agentcici-meeting-backdrop{background:rgba(43,34,23,.22);}}"
    ].join("");
    document.head.appendChild(style);
  }

  function buildEmbedUrl(origin, options) {
    var url = new URL("/embed/meeting-minutes", origin);
    url.searchParams.set("token", options.token);
    url.searchParams.set("sdkVersion", VERSION);
    url.searchParams.set("mode", options.mode);
    if (options.locale) {
      url.searchParams.set("locale", options.locale);
    }
    if (options.theme) {
      url.searchParams.set("theme", options.theme);
    }
    return url.toString();
  }

  function callbackNameFor(type) {
    return {
      "embed:ready": "onReady",
      "embed:meeting-started": "onMeetingStarted",
      "embed:transcript-final": "onTranscriptFinal",
      "embed:summary-generated": "onSummaryGenerated",
      "embed:writeback-preview": "onWritebackPreview",
      "embed:writeback-success": "onWritebackSuccess",
      "embed:error": "onError",
      "embed:close": "onClose"
    }[type];
  }

  function createMessage(type, instance, payload) {
    return {
      source: SDK_SOURCE,
      type: type,
      requestId: "req_" + Date.now().toString(36) + "_" + Math.random().toString(36).slice(2),
      sessionId: instance.sessionId || "",
      payload: payload || {},
      timestamp: new Date().toISOString()
    };
  }

  function MeetingInstance(options, sdkOrigin) {
    this.id = "agentcici_meeting_" + (++idSeed);
    this.sdkOrigin = sdkOrigin;
    this.options = options;
    this.callbacks = options.callbacks || {};
    this.context = options.context || {};
    this.sessionId = "";
    this.destroyed = false;
    this.iframe = null;
    this.root = null;
    this.backdrop = null;
    this._onMessage = this._onMessage.bind(this);
  }

  MeetingInstance.prototype._call = function (type, event) {
    var name = callbackNameFor(type);
    var fn = name ? this.callbacks[name] : null;
    if (typeof fn === "function") {
      fn(event);
    }
  };

  MeetingInstance.prototype._onMessage = function (event) {
    if (this.destroyed || event.origin !== this.sdkOrigin) {
      return;
    }
    var message = event.data;
    if (!message || typeof message !== "object" || message.source !== EMBED_SOURCE) {
      return;
    }
    if (message.sessionId) {
      this.sessionId = message.sessionId;
    }
    this._call(message.type, message);
    if (message.type === "embed:close") {
      this.destroy();
    }
  };

  MeetingInstance.prototype._createIframe = function () {
    var iframe = document.createElement("iframe");
    iframe.className = "agentcici-meeting-frame";
    iframe.title = "AgentCiCi 会议纪要";
    iframe.allow = "microphone";
    iframe.src = buildEmbedUrl(this.sdkOrigin, this.options);
    iframe.onload = this.postMessage.bind(this, "host:init", {
      sdkVersion: VERSION,
      mode: this.options.mode,
      locale: this.options.locale,
      theme: this.options.theme,
      context: this.context
    });
    this.iframe = iframe;
    return iframe;
  };

  MeetingInstance.prototype.mount = function () {
    ensureStyles();
    window.addEventListener("message", this._onMessage);

    if (this.options.mode === "inline") {
      var container = resolveContainer(this.options.container);
      if (!container) {
        throw new Error("AgentCiCiMeeting inline mode requires a valid container.");
      }
      var inlineRoot = document.createElement("div");
      inlineRoot.className = "agentcici-meeting-inline";
      inlineRoot.setAttribute(INSTANCE_ATTR, this.id);
      inlineRoot.appendChild(this._createIframe());
      container.appendChild(inlineRoot);
      this.root = inlineRoot;
      return this;
    }

    var width = clampDrawerWidth(this.options.width);
    var backdrop = document.createElement("div");
    backdrop.className = "agentcici-meeting-backdrop";
    backdrop.setAttribute(INSTANCE_ATTR, this.id);

    var shell = document.createElement("aside");
    shell.className = "agentcici-meeting-shell";
    shell.style.width = width + "px";
    shell.setAttribute(INSTANCE_ATTR, this.id);
    shell.setAttribute("aria-label", "AgentCiCi 会议纪要");

    shell.appendChild(this._createIframe());
    document.body.appendChild(backdrop);
    document.body.appendChild(shell);
    this.backdrop = backdrop;
    this.root = shell;

    window.setTimeout(this.postMessage.bind(this, "host:focus", {}), 0);
    return this;
  };

  MeetingInstance.prototype.postMessage = function (type, payload) {
    if (this.destroyed || !this.iframe || !this.iframe.contentWindow) {
      return;
    }
    this.iframe.contentWindow.postMessage(createMessage(type, this, payload), this.sdkOrigin);
  };

  MeetingInstance.prototype.updateContext = function (nextContext) {
    this.context = Object.assign({}, this.context, nextContext || {});
    this.postMessage("host:update-context", { context: this.context });
  };

  MeetingInstance.prototype.close = function () {
    this.postMessage("host:request-close", {});
  };

  MeetingInstance.prototype.destroy = function () {
    if (this.destroyed) {
      return;
    }
    this.destroyed = true;
    window.removeEventListener("message", this._onMessage);
    if (this.backdrop && this.backdrop.parentNode) {
      this.backdrop.parentNode.removeChild(this.backdrop);
    }
    if (this.root && this.root.parentNode) {
      this.root.parentNode.removeChild(this.root);
    }
  };

  function normalizeOptions(options) {
    var next = Object.assign({
      mode: "drawer",
      locale: "zh-CN",
      theme: "gilded-ledger",
      callbacks: {}
    }, options || {});
    if (!next.token || typeof next.token !== "string") {
      throw new Error("AgentCiCiMeeting.open requires a short-lived embed token.");
    }
    if (next.mode !== "drawer" && next.mode !== "inline") {
      throw new Error("AgentCiCiMeeting mode must be either drawer or inline.");
    }
    return next;
  }

  function open(options) {
    var sdkOrigin = currentScriptOrigin();
    var instance = new MeetingInstance(normalizeOptions(options), sdkOrigin);
    return instance.mount();
  }

  window.AgentCiCiMeeting = {
    version: VERSION,
    open: open
  };
})(window, document);
