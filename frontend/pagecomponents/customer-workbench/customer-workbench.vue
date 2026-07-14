<template>
  <div class="customer-workbench-embed" :style="{ '--customer-workbench-embed-height': embedHeight }">
    <section class="customer-workbench-embed__body">
      <iframe
        v-if="embedded"
        :src="iframeUrl"
        title="客户互动工作台"
        frameborder="0"
        allow="microphone; clipboard-write"
      ></iframe>
      <p v-if="embedded && ssoMessage" class="customer-workbench-embed__status">{{ ssoMessage }}</p>
      <div v-else class="customer-workbench-embed__placeholder">
        <strong>已准备在 CRM 中嵌入客户互动工作台</strong>
        <p>建议把该组件挂载到客户主页或销售应用菜单。用户点击后将进入客户互动工作台，并在 AgentCiCi 内完成 AI 分析和确认式 CRM 落地。</p>
        <ul>
          <li>新客户推进：需求、预算、决策链、商机动作。</li>
          <li>老客户经营：续约、增购、服务风险、关系维护。</li>
          <li>AI 客户助理：总结互动、生成建议、切换客户。</li>
        </ul>
      </div>
    </section>
  </div>
</template>

<script>
const SSO_RETRY_DELAYS = [0, 800, 2000, 4000];
const SSO_RECOVERY_DELAY = 5000;
const SSO_MAX_RECOVERY_CYCLES = 1;

export default {
  props: {
    elePropObj: {
      type: Object,
      default: () => ({})
    }
  },
  data() {
    return {
      embedded: true,
      componentInfo: {
        component: "component-customer-workbench",
        compName: "客户互动",
        compDesc: "嵌入 AgentCiCi 客户互动工作台",
        loadModel: "lazy"
      },
      propObj: {
        workspaceUrl: "https://x.agentcici.com/app?aiApp=customer-workbench&embed=crm",
        agentOrgId: "org2sva14i4udjmi2t4s",
        enableSso: true
      },
      propOption: {
        workspaceUrl: {
          lable: "工作台地址",
          type: "input"
        },
        agentOrgId: {
          lable: "AgentCiCi 组织 ID",
          type: "input"
        }
      },
      resolvedWorkspaceUrl: "",
      ssoStarted: false,
      ssoMessage: "",
      ssoRetryTimer: null,
      ssoRecoveryCycles: 0,
      ssoDestroyed: false,
      embedHeight: "100vh",
      previousDocumentOverflow: null,
      previousBodyOverflow: null
    };
  },
  computed: {
    workspaceUrl() {
      return this.elePropObj.workspaceUrl || this.propObj.workspaceUrl;
    },
    iframeUrl() {
      return this.resolvedWorkspaceUrl || this.workspaceUrl;
    },
    agentOrgId() {
      return this.elePropObj.agentOrgId || this.propObj.agentOrgId;
    },
    enableSso() {
      return this.elePropObj.enableSso !== false;
    },
    ssoEndpoint() {
      if (this.elePropObj.ssoEndpoint) {
        return this.elePropObj.ssoEndpoint;
      }
      try {
        return new URL(this.workspaceUrl, window.location.href).origin + "/auth/cloudcc-sso/ticket";
      } catch (error) {
        return "https://x.agentcici.com/auth/cloudcc-sso/ticket";
      }
    }
  },
  mounted() {
    if (this.embedded) {
      this.bootstrapSso();
    }
    this.syncEmbedHeight();
    window.addEventListener("resize", this.syncEmbedHeight, { passive: true });
  },
  beforeDestroy() {
    this.ssoDestroyed = true;
    this.clearSsoRetryTimer();
    window.removeEventListener("resize", this.syncEmbedHeight);
    if (this.previousDocumentOverflow !== null && document.documentElement) {
      document.documentElement.style.overflow = this.previousDocumentOverflow;
    }
    if (this.previousBodyOverflow !== null && document.body) {
      document.body.style.overflow = this.previousBodyOverflow;
    }
  },
  methods: {
    openWorkspace() {
      this.embedded = true;
      this.bootstrapSso();
      if (window.$CCDK && window.$CCDK.CCLog) {
        window.$CCDK.CCLog.reportInfoLog({
          infoType: "info",
          serviceName: "customer-workbench",
          infoMessage: "open customer interaction workbench"
        });
      }
    },
    bootstrapSso() {
      if (!this.enableSso || this.ssoStarted) {
        return;
      }
      if (typeof fetch !== "function") {
        this.ssoMessage = "当前浏览器暂不支持 CloudCC 身份同步，请重新打开工作台。";
        return;
      }
      this.ssoDestroyed = false;
      this.ssoStarted = true;
      this.runSsoAttempt(0);
    },
    async runSsoAttempt(attemptIndex) {
      try {
        const delay = SSO_RETRY_DELAYS[attemptIndex] || 0;
        this.ssoMessage = attemptIndex === 0
          ? "正在同步 CloudCC 当前用户身份..."
          : `CloudCC 身份服务暂时不可用，正在重新同步（${attemptIndex + 1}/${SSO_RETRY_DELAYS.length}）...`;
        if (delay > 0) {
          await this.waitForSsoDelay(delay);
        }
        if (this.ssoDestroyed) {
          return;
        }
        const token = await this.readCloudccRuntimeToken();
        const user = await this.readCloudccUserInfo(token);
        if (!token || !user) {
          return this.retryOrFinishSso(attemptIndex, true, 0);
        }
        const response = await fetch(this.ssoEndpoint, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            agentOrgId: this.agentOrgId,
            cloudccAccessToken: token,
            cloudccUser: user,
            parentOrigin: window.location.origin,
            targetPath: "/app?aiApp=customer-workbench&embed=crm"
          })
        });
        const body = await response.json().catch(() => null);
        if (!response.ok || !body || !body.success || !body.data || !body.data.ticket) {
          return this.retryOrFinishSso(
            attemptIndex,
            response.ok ? true : this.isRetryableSsoStatus(response.status),
            response.status
          );
        }
        this.clearSsoRetryTimer();
        this.ssoStarted = false;
        this.ssoRecoveryCycles = 0;
        this.resolvedWorkspaceUrl = this.appendQuery(this.workspaceUrl, "ssoTicket", body.data.ticket);
        this.ssoMessage = "";
      } catch (error) {
        return this.retryOrFinishSso(attemptIndex, true, 0);
      }
    },
    retryOrFinishSso(attemptIndex, retryable, status) {
      if (retryable && attemptIndex + 1 < SSO_RETRY_DELAYS.length) {
        return this.runSsoAttempt(attemptIndex + 1);
      }
      this.finishSsoFailure(retryable, status);
      return undefined;
    },
    finishSsoFailure(retryable, status) {
      this.clearSsoRetryTimer();
      this.ssoStarted = false;
      if (status === 401 || status === 403) {
        this.ssoMessage = "CloudCC 当前用户与 AgentCiCi 账号未正确映射，请联系管理员检查账号绑定。";
      } else if (status === 400) {
        this.ssoMessage = "CloudCC 身份信息不完整，请从 CRM 重新打开客户互动工作台。";
      } else {
        this.ssoMessage = "CloudCC 身份服务暂时不可用，将在几秒后自动重试。";
      }
      if (retryable && !this.ssoDestroyed && this.ssoRecoveryCycles < SSO_MAX_RECOVERY_CYCLES) {
        this.ssoRecoveryCycles += 1;
        this.ssoRetryTimer = window.setTimeout(() => {
          this.ssoRetryTimer = null;
          this.bootstrapSso();
        }, SSO_RECOVERY_DELAY);
      }
    },
    isRetryableSsoStatus(status) {
      return !status || status === 408 || status === 425 || status === 429 || status >= 500;
    },
    waitForSsoDelay(delay) {
      this.clearSsoRetryTimer();
      return new Promise((resolve) => {
        this.ssoRetryTimer = window.setTimeout(() => {
          this.ssoRetryTimer = null;
          resolve();
        }, delay);
      });
    },
    clearSsoRetryTimer() {
      if (this.ssoRetryTimer !== null) {
        window.clearTimeout(this.ssoRetryTimer);
        this.ssoRetryTimer = null;
      }
    },
    syncEmbedHeight() {
      const root = this.$el;
      if (!root || typeof window === "undefined") {
        return;
      }
      const viewportHeight = window.innerHeight || document.documentElement.clientHeight || 720;
      const rect = typeof root.getBoundingClientRect === "function" ? root.getBoundingClientRect() : { top: 0 };
      const top = Math.max(0, rect.top || 0);
      const availableHeight = Math.max(0, Math.floor(viewportHeight - top - 1));
      this.embedHeight = `${availableHeight}px`;
      if (root.parentElement && root.parentElement.style) {
        root.parentElement.style.height = this.embedHeight;
        root.parentElement.style.minHeight = "0";
        root.parentElement.style.overflow = "hidden";
      }
      if (document.documentElement) {
        if (this.previousDocumentOverflow === null) {
          this.previousDocumentOverflow = document.documentElement.style.overflow || "";
        }
        document.documentElement.style.overflow = "hidden";
      }
      if (document.body) {
        if (this.previousBodyOverflow === null) {
          this.previousBodyOverflow = document.body.style.overflow || "";
        }
        document.body.style.overflow = "hidden";
      }
    },
    async readCloudccRuntimeToken() {
      const tokenApi = window.$CCDK && window.$CCDK.CCToken;
      if (!tokenApi) {
        return "";
      }
      const openApiTokenValue = await this.callCcdk((done) => {
        if (typeof tokenApi.getOpenApiToken === "function") {
          return tokenApi.getOpenApiToken(done);
        }
        return "";
      });
      const openApiToken = this.firstString(openApiTokenValue, [
        "accessToken",
        "token",
        "data.accessToken",
        "data.token",
        "result.accessToken",
        "result.token"
      ]);
      if (openApiToken) {
        return openApiToken;
      }
      const runtimeTokenValue = await this.callCcdk((done) => {
        if (typeof tokenApi.getToken === "function") {
          return tokenApi.getToken(done);
        }
        return "";
      });
      return this.firstString(runtimeTokenValue, [
        "accessToken",
        "token",
        "data.accessToken",
        "data.token",
        "result.accessToken",
        "result.token"
      ]);
    },
    async readCloudccUserInfo(token) {
      const userApi = window.$CCDK && window.$CCDK.CCUser;
      let user = null;
      if (userApi && typeof userApi.getUserInfo === "function") {
        user = await this.callCcdk((done) => userApi.getUserInfo(done));
      }
      if (user && typeof user === "object") {
        return user;
      }
      return this.decodeJwtPayload(token);
    },
    callCcdk(invoker) {
      return new Promise((resolve) => {
        let settled = false;
        const done = (value) => {
          if (!settled) {
            settled = true;
            resolve(value);
          }
        };
        try {
          const result = invoker(done);
          if (result && typeof result.then === "function") {
            result.then(done).catch(() => done(null));
          } else if (result !== undefined) {
            done(result);
          }
        } catch (error) {
          done(null);
        }
        window.setTimeout(() => done(null), 3000);
      });
    },
    firstString(source, paths) {
      if (!source) {
        return "";
      }
      if (typeof source === "string") {
        return source.trim();
      }
      for (const path of paths) {
        const value = this.nestedValue(source, path);
        if (value !== undefined && value !== null && String(value).trim()) {
          return String(value).trim();
        }
      }
      return "";
    },
    nestedValue(source, path) {
      return path.split(".").reduce((current, part) => {
        if (!current || typeof current !== "object") {
          return undefined;
        }
        return current[part];
      }, source);
    },
    decodeJwtPayload(token) {
      try {
        const payload = String(token || "").split(".")[1];
        if (!payload) {
          return null;
        }
        const json = decodeURIComponent(Array.prototype.map.call(atob(payload.replace(/-/g, "+").replace(/_/g, "/")), (char) => {
          return "%" + ("00" + char.charCodeAt(0).toString(16)).slice(-2);
        }).join(""));
        return JSON.parse(json);
      } catch (error) {
        return null;
      }
    },
    appendQuery(url, key, value) {
      try {
        const target = new URL(url, window.location.href);
        target.searchParams.set(key, value);
        return target.toString();
      } catch (error) {
        const separator = url.indexOf("?") >= 0 ? "&" : "?";
        return url + separator + encodeURIComponent(key) + "=" + encodeURIComponent(value);
      }
    }
  }
};
</script>

<style lang="scss" scoped>
.customer-workbench-embed {
  position: relative;
  height: var(--customer-workbench-embed-height, 100vh);
  min-height: 0;
  background: #fffdf8;
  color: #2b2217;
  overflow: hidden;
}

.customer-workbench-embed p,
.customer-workbench-embed li {
  color: #5f523f;
  font-size: 13px;
  line-height: 1.7;
}

.customer-workbench-embed__body {
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.customer-workbench-embed iframe {
  width: 100%;
  height: 100%;
  min-height: 0;
  border: 0;
  display: block;
}

.customer-workbench-embed__status {
  position: absolute;
  left: 20px;
  right: 20px;
  top: 16px;
  margin: 0;
  padding: 10px 12px;
  border: 1px solid #d7c299;
  border-radius: 8px;
  background: rgba(255, 253, 248, 0.92);
}

.customer-workbench-embed__placeholder {
  max-width: 720px;
  padding: 28px 32px;
}

.customer-workbench-embed__placeholder strong {
  display: block;
  margin-bottom: 10px;
  font-size: 16px;
}

.customer-workbench-embed__placeholder ul {
  margin: 18px 0 0;
  padding-left: 18px;
}
</style>
