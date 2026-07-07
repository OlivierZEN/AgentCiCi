<template>
  <div class="customer-workbench-embed">
    <header>
      <div>
        <span>AI 应用</span>
        <h2>客户互动工作台</h2>
        <p>从客户互动事实进入新客户推进、老客户经营和 CRM 落地建议。</p>
      </div>
      <button type="button" @click="openWorkspace">打开工作台</button>
    </header>
    <section class="customer-workbench-embed__body">
      <iframe
        v-if="embedded"
        :src="workspaceUrl"
        title="客户互动工作台"
        frameborder="0"
      ></iframe>
      <div v-else class="customer-workbench-embed__placeholder">
        <strong>已准备在 CRM 中嵌入 AgentCiCi 工作台</strong>
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
export default {
  props: {
    elePropObj: {
      type: Object,
      default: () => ({})
    }
  },
  data() {
    return {
      embedded: false,
      componentInfo: {
        component: "component-customer-workbench",
        compName: "客户互动",
        compDesc: "嵌入 AgentCiCi 客户互动工作台",
        loadModel: "lazy"
      },
      propObj: {
        workspaceUrl: "https://x.agentcici.com/app?aiApp=customer-workbench"
      },
      propOption: {
        workspaceUrl: {
          lable: "工作台地址",
          type: "input"
        }
      }
    };
  },
  computed: {
    workspaceUrl() {
      return this.elePropObj.workspaceUrl || this.propObj.workspaceUrl;
    }
  },
  methods: {
    openWorkspace() {
      this.embedded = true;
      if (window.$CCDK && window.$CCDK.CCLog) {
        window.$CCDK.CCLog.reportInfoLog({
          infoType: "info",
          serviceName: "customer-workbench",
          infoMessage: "open customer interaction workbench"
        });
      }
    }
  }
};
</script>

<style lang="scss" scoped>
.customer-workbench-embed {
  min-height: 620px;
  background: #fffdf8;
  border: 1px solid #ded2bb;
  border-radius: 8px;
  color: #2b2217;
  overflow: hidden;
}

.customer-workbench-embed header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
  border-bottom: 1px solid #ded2bb;
  background: #faf4e8;
}

.customer-workbench-embed span {
  color: #876223;
  font-size: 12px;
  font-weight: 700;
}

.customer-workbench-embed h2 {
  margin: 4px 0 6px;
  font-size: 22px;
  color: #2b2217;
}

.customer-workbench-embed p,
.customer-workbench-embed li {
  color: #5f523f;
  font-size: 13px;
  line-height: 1.7;
}

.customer-workbench-embed button {
  height: 34px;
  border: 1px solid #a67c2f;
  border-radius: 8px;
  background: #a67c2f;
  color: #fffdf8;
  padding: 0 14px;
  font-weight: 700;
}

.customer-workbench-embed__body {
  min-height: 540px;
}

.customer-workbench-embed iframe {
  width: 100%;
  height: 620px;
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
