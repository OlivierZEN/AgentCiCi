import { useEffect, useState } from "react";
import type { ReactNode } from "react";
import {
  ArrowLeft,
  ArrowRight,
  BookOpen,
  Check,
  CheckCircle2,
  ClipboardCheck,
  Code2,
  Copy,
  ExternalLink,
  KeyRound,
  Link2,
  Network,
  Route,
  ShieldCheck,
  Wrench,
} from "lucide-react";
import { useNavigate, useSearchParams } from "react-router-dom";

export const APPLICATION_INTEGRATION_GUIDE_SECTIONS = [
  { id: "overview", number: "01", label: "接入全景" },
  { id: "prerequisites", number: "02", label: "接入前准备" },
  { id: "registration", number: "03", label: "登记应用" },
  { id: "provider-contract", number: "04", label: "Provider 契约" },
  { id: "authentication", number: "05", label: "鉴权与 Secret" },
  { id: "connection", number: "06", label: "运行连接" },
  { id: "version", number: "07", label: "版本与初始化" },
  { id: "dependencies", number: "08", label: "应用依赖" },
  { id: "publication", number: "09", label: "验证与发布" },
  { id: "activation", number: "10", label: "租户开通" },
  { id: "operations", number: "11", label: "运行期运维" },
  { id: "troubleshooting", number: "12", label: "排错与检查" },
] as const;

export const APPLICATION_INTEGRATION_AGENT_GUIDE_PATH = "/agent-docs/internal-applications/integration-guide.md";

export const PROVIDER_LIFECYCLE_REQUEST_EXAMPLE = `{
  "operationId": "8bf2d8aa-9ec4-4f54-9462-4b3d5f55d3fd",
  "idempotencyKey": "tenant-onboarding-20260818-001",
  "operationType": "ACTIVATE",
  "companyId": "org-example-001",
  "appCode": "sales-workbench",
  "applicationVersion": "1.0.0",
  "contractVersion": "v1",
  "dependencies": [
    {
      "appCode": "semattice",
      "versionConstraint": ">=1.0.0",
      "dependencyType": "REQUIRED_RUNTIME"
    }
  ],
  "stepCode": "tenant-bootstrap",
  "capability": "tenant.activate"
}`;

export const PROVIDER_RESPONSE_EXAMPLE = `HTTP/1.1 200 OK
Content-Type: application/json

{
  "status": "ACTIVE",
  "resourceId": "tenant-resource-001",
  "providerRevision": "42"
}`;

export const HMAC_CANONICAL_EXAMPLE = `agentcici
POST
/internal/tenant-lifecycle/v1/activations
1787018400
4f913f26d34b452cb68d12310d76b7c9
<SHA256_HEX_OF_REQUEST_BODY>`;

const HEALTH_RESPONSE_EXAMPLE = `HTTP/1.1 200 OK
Content-Type: application/json

{
  "status": "UP",
  "contractVersion": "v1"
}`;

const CONNECTION_EXAMPLE = `Base URL       https://provider.example.test
健康检查路径   /internal/tenant-lifecycle/v1/health
开通路径       /internal/tenant-lifecycle/v1/activations
校准路径       /internal/tenant-lifecycle/v1/reconciliations
暂停路径       /internal/tenant-lifecycle/v1/suspensions
恢复路径       /internal/tenant-lifecycle/v1/resumptions
升级路径       /internal/tenant-lifecycle/v1/upgrades`;

const VERSION_EXAMPLE = `版本                1.0.0
初始化引擎          SAGA_V1
Provider 连接       sales-workbench.lifecycle

步骤代码            tenant-bootstrap
步骤类型            PROVIDER_CALLBACK
能力                tenant.activate
契约版本            v1`;

type CodeBlockProps = {
  title: string;
  language?: string;
  value: string;
};

function CodeBlock({ title, language = "text", value }: CodeBlockProps) {
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      globalThis.setTimeout(() => setCopied(false), 1800);
    } catch {
      setCopied(false);
    }
  }

  return (
    <div className="application-guide-code">
      <div className="application-guide-code__head">
        <span>{title}</span>
        <span className="application-guide-code__language">{language}</span>
        <button type="button" onClick={() => void copy()} aria-label={`复制${title}`}>
          {copied ? <Check size={14} aria-hidden /> : <Copy size={14} aria-hidden />}
          {copied ? "已复制" : "复制"}
        </button>
      </div>
      <pre><code>{value}</code></pre>
      <span className="sr-only" aria-live="polite">{copied ? `${title}已复制` : ""}</span>
    </div>
  );
}

function Principle({ title, children }: { title: string; children: ReactNode }) {
  return <div className="application-guide-principle"><strong>{title}</strong><p>{children}</p></div>;
}

function StepResult({ children }: { children: ReactNode }) {
  return <p className="application-guide-result"><CheckCircle2 size={15} aria-hidden /><span><strong>完成标志：</strong>{children}</span></p>;
}

export default function PlatformApplicationIntegrationGuidePage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const sourceApp = searchParams.get("app")?.trim() ?? "";
  const safeSourceApp = /^[a-z][a-z0-9-]{1,63}$/.test(sourceApp) ? sourceApp : "";
  const returnPath = safeSourceApp
    ? `/platform/internal-applications/${encodeURIComponent(safeSourceApp)}`
    : "/platform/internal-applications";

  useEffect(() => {
    const scrollLockClass = "application-guide-scroll-lock";
    document.documentElement.classList.add(scrollLockClass);
    document.body.classList.add(scrollLockClass);
    window.scrollTo({ top: 0, left: 0 });
    const anchorFrame = window.requestAnimationFrame(() => {
      const targetId = decodeURIComponent(window.location.hash.slice(1));
      if (!APPLICATION_INTEGRATION_GUIDE_SECTIONS.some((section) => section.id === targetId)) return;
      const target = document.getElementById(targetId);
      const scrollContainer = target?.closest<HTMLElement>(".platform-main");
      if (!target || !scrollContainer) return;
      const targetTop = target.getBoundingClientRect().top
        - scrollContainer.getBoundingClientRect().top
        + scrollContainer.scrollTop
        - 20;
      scrollContainer.scrollTo({ top: targetTop, left: 0 });
    });
    return () => {
      window.cancelAnimationFrame(anchorFrame);
      document.documentElement.classList.remove(scrollLockClass);
      document.body.classList.remove(scrollLockClass);
    };
  }, []);

  return (
    <div className="admin-page platform-page application-integration-guide">
      <header className="platform-page-head application-guide-head">
        <div className="platform-page-head__main">
          <button type="button" className="system-api-back" onClick={() => navigate(returnPath)}>
            <ArrowLeft size={15} aria-hidden />
            {safeSourceApp ? "返回应用详情" : "返回应用中心"}
          </button>
          <p className="platform-section-label">开发者手册 · tenant-application/v1</p>
          <h1 className="skills-catalog__title">内部应用接入指南</h1>
          <p className="subtle skills-catalog__subtitle">从实现 Provider 到租户开通的完整路径。每一步都说明操作、原理与完成标志。</p>
        </div>
        <div className="application-guide-head__aside">
          <a
            className="platform-button application-guide-agent-link"
            href={APPLICATION_INTEGRATION_AGENT_GUIDE_PATH}
            target="_blank"
            rel="noreferrer"
          >
            智能体版 Markdown
            <ExternalLink size={14} aria-hidden />
          </a>
          <div className="application-guide-head__meta" aria-label="指南适用范围">
            <BookOpen size={18} aria-hidden />
            <span><strong>适用于</strong>平台受控内部应用</span>
            <span><strong>预计联调</strong>约 1–2 个工作日</span>
          </div>
        </div>
      </header>

      <div className="application-guide-flow" aria-label="接入阶段">
        <span>应用登记</span><ArrowRight size={14} aria-hidden />
        <span>Provider 实现</span><ArrowRight size={14} aria-hidden />
        <span>连接启用</span><ArrowRight size={14} aria-hidden />
        <span>版本发布</span><ArrowRight size={14} aria-hidden />
        <span>租户开通</span>
      </div>

      <div className="application-guide-layout">
        <aside className="application-guide-toc" aria-label="接入指南目录">
          <p>本页目录</p>
          <nav>
            {APPLICATION_INTEGRATION_GUIDE_SECTIONS.map((section) => (
              <a key={section.id} href={`#${section.id}`}><span>{section.number}</span>{section.label}</a>
            ))}
          </nav>
          <div className="application-guide-toc__note">
            <ShieldCheck size={17} aria-hidden />
            <p><strong>安全边界</strong>平台只保存 Secret 引用。Token、密钥和私钥原文不能录入表单。</p>
          </div>
        </aside>

        <main className="application-guide-content">
          <section id="overview" className="application-guide-section">
            <div className="application-guide-section__number">01</div>
            <div className="application-guide-section__body">
              <p className="platform-section-label">先理解控制面</p>
              <h2>接入全景与职责边界</h2>
              <p>应用中心管理“某个应用如何被租户安全开通”，不是服务部署工具。应用版本保存稳定的逻辑声明，运行连接保存当前环境的真实拓扑；平台后端在租户操作时解析二者并调用 Provider。</p>
              <div className="application-guide-principles">
                <Principle title="应用开发者">实现健康检查和生命周期接口，保证幂等、鉴权、租户隔离与可观测性。</Principle>
                <Principle title="平台管理员">登记应用，配置并启用运行连接，创建版本、依赖并发布。</Principle>
                <Principle title="AgentCiCi 平台">解析固定版本和连接修订，执行回调、记录 operation/step 审计，成功后更新租户状态。</Principle>
              </div>
              <div className="application-guide-boundary"><Network size={18} aria-hidden /><div><strong>调用方向</strong><code>浏览器 → AgentCiCi 同源 API → Provider</code><p>浏览器不会直接跨域调用 Provider，也不会持有 Provider Secret。</p></div></div>
            </div>
          </section>

          <section id="prerequisites" className="application-guide-section">
            <div className="application-guide-section__number">02</div>
            <div className="application-guide-section__body">
              <p className="platform-section-label">准备清单</p>
              <h2>接入前准备</h2>
              <p>开始配置前，应用团队应先确定以下事实。部署地址可以后补，但应用代码、租户隔离方式和生命周期语义一旦被租户使用就不应随意改变。</p>
              <ul className="application-guide-checklist">
                <li><Check size={14} aria-hidden /><span>稳定的应用代码：小写字母开头，仅含小写字母、数字和连字符，长度 2–64。</span></li>
                <li><Check size={14} aria-hidden /><span>明确责任团队和告警联系人，并确认应用按 <code>companyId</code> 隔离租户资源。</span></li>
                <li><Check size={14} aria-hidden /><span>确定是否提供平台内逻辑入口；入口只登记逻辑路由键，不登记环境域名。</span></li>
                <li><Check size={14} aria-hidden /><span>列出必须先开通或运行时必须可用的应用依赖，以及最低版本。</span></li>
                <li><Check size={14} aria-hidden /><span>准备可重复执行的初始化逻辑；相同幂等键不能创建重复资源。</span></li>
              </ul>
              <StepResult>应用代码、责任团队、租户模型、依赖清单和 Provider 负责人均已确认。</StepResult>
            </div>
          </section>

          <section id="registration" className="application-guide-section">
            <div className="application-guide-section__number">03</div>
            <div className="application-guide-section__body">
              <p className="platform-section-label">平台操作</p>
              <h2>登记应用</h2>
              <ol className="application-guide-steps">
                <li><span>1</span><div><strong>进入“能力治理 → 应用中心”</strong><p>点击“登记应用”，先创建目录草稿。草稿不会出现在租户应用中心。</p></div></li>
                <li><span>2</span><div><strong>填写稳定治理字段</strong><p><code>appCode</code> 是跨版本主键；租户模式通常选择共享运行时、租户隔离。只有应用提供受管入口时才配置入口方式和逻辑入口。</p></div></li>
                <li><span>3</span><div><strong>核对责任与边界</strong><p>简介应说明租户获得什么能力；责任团队用于后续故障归属。不要在名称、简介或入口中写服务地址。</p></div></li>
              </ol>
              <StepResult>应用详情页显示“草稿”，应用代码、责任团队和租户模式正确。</StepResult>
            </div>
          </section>

          <section id="provider-contract" className="application-guide-section">
            <div className="application-guide-section__number">04</div>
            <div className="application-guide-section__body">
              <p className="platform-section-label">应用开发</p>
              <h2>实现 Provider 生命周期接口</h2>
              <p>Provider 是应用向 AgentCiCi 暴露的服务端生命周期适配层。至少实现健康检查和开通接口；如果应用支持运行期治理，再实现校准、暂停、恢复和升级。</p>
              <div className="application-guide-contract-table" role="table" aria-label="生命周期接口语义">
                <div role="row"><strong role="columnheader">动作</strong><strong role="columnheader">应用侧语义</strong><strong role="columnheader">成功状态</strong></div>
                <div role="row"><code>ACTIVATE</code><span>创建或复用该租户的应用资源</span><code>ACTIVE / SUCCEEDED</code></div>
                <div role="row"><code>RECONCILE</code><span>校准缺失或漂移的受管资源</span><code>ACTIVE / SUCCEEDED</code></div>
                <div role="row"><code>SUSPEND</code><span>停止租户入口或执行能力，不删除数据</span><code>SUSPENDED / SUCCEEDED</code></div>
                <div role="row"><code>RESUME</code><span>恢复既有租户资源</span><code>ACTIVE / SUCCEEDED</code></div>
                <div role="row"><code>UPGRADE</code><span>把租户资源迁移到目标应用版本</span><code>ACTIVE / SUCCEEDED</code></div>
              </div>
              <CodeBlock title="健康检查响应" language="HTTP" value={HEALTH_RESPONSE_EXAMPLE} />
              <CodeBlock title="生命周期请求体" language="JSON" value={PROVIDER_LIFECYCLE_REQUEST_EXAMPLE} />
              <CodeBlock title="生命周期成功响应" language="HTTP" value={PROVIDER_RESPONSE_EXAMPLE} />
              <div className="application-guide-callout application-guide-callout--important">
                <ClipboardCheck size={18} aria-hidden />
                <div><strong>幂等是强制要求</strong><p>业务幂等使用请求体的 <code>idempotencyKey</code>；单步骤重试使用请求头 <code>Idempotency-Key: operationId:stepCode</code>。Provider 应保存处理结果并对重复请求返回同一业务结果。</p></div>
              </div>
              <p className="application-guide-detail">每次请求还包含 <code>X-Correlation-Id: operationId</code>。Provider 响应必须是 JSON，<code>status</code> 只接受 <code>SUCCEEDED</code>、<code>ACTIVE</code> 或 <code>SUSPENDED</code>，响应体不得超过 1 MiB。非 2xx、无效 JSON 或其他状态都会让本次步骤失败。</p>
              <StepResult>使用固定测试租户重复调用两次开通接口，不产生重复资源，且可按 operationId 查到完整日志。</StepResult>
            </div>
          </section>

          <section id="authentication" className="application-guide-section">
            <div className="application-guide-section__number">05</div>
            <div className="application-guide-section__body">
              <p className="platform-section-label">安全契约</p>
              <h2>配置鉴权与 Secret 引用</h2>
              <p>运行连接支持三种鉴权类型。生产级接入优先使用 HMAC；平台只保存 Secret 引用，真正的 Secret 由目标环境注入。</p>
              <dl className="application-guide-definition-list">
                <div><dt><code>NONE</code></dt><dd>仅适合由强网络边界保护且已经过安全评审的内部端点。</dd></div>
                <div><dt><code>BEARER_SECRET_REF</code></dt><dd>平台把环境中解析出的 Secret 作为 Bearer Token 发送。</dd></div>
                <div><dt><code>HMAC_SHA256_SECRET_REF</code></dt><dd>平台发送服务、应用、时间戳、随机数与签名头；Provider 应校验时间窗、Nonce 防重放和恒定时间签名比较。</dd></div>
              </dl>
              <CodeBlock title="HMAC canonical string" language="text" value={HMAC_CANONICAL_EXAMPLE} />
              <p className="application-guide-detail">对 canonical string 使用共享 Secret 计算 HMAC-SHA256，并输出小写十六进制。相关请求头为 <code>X-Internal-Service</code>、<code>X-Internal-App</code>、<code>X-Internal-Timestamp</code>、<code>X-Internal-Nonce</code> 和 <code>X-Internal-Signature</code>。</p>
              <div className="application-guide-callout"><KeyRound size={18} aria-hidden /><div><strong>Secret 如何进入环境</strong><p>连接表单只填写如 <code>sales-workbench-hmac</code> 的引用名。运维侧把真实值注入 <code>app.platform.provider-secrets.&lt;secret_ref&gt;</code>；真实值不得出现在应用版本、前端、审计或截图中。</p></div></div>
              <StepResult>Provider 能拒绝过期时间戳、重复 Nonce、错误应用代码和错误签名，正确签名可通过。</StepResult>
            </div>
          </section>

          <section id="connection" className="application-guide-section">
            <div className="application-guide-section__number">06</div>
            <div className="application-guide-section__body">
              <p className="platform-section-label">平台操作</p>
              <h2>创建、测试并启用运行连接</h2>
              <p>运行连接是部署拓扑控制面。真实 Base URL 在这里可视化配置是允许的，但不会进入应用版本；版本只引用稳定的 <code>bindingKey</code>。</p>
              <ol className="application-guide-steps">
                <li><span>1</span><div><strong>在应用详情点击“新建连接”</strong><p>绑定键建议使用 <code>&lt;app-code&gt;.lifecycle</code>。环境键和网络范围创建后不可在同一绑定键下改变。</p></div></li>
                <li><span>2</span><div><strong>选择网络范围</strong><p><code>PUBLIC_HTTPS</code> 强制 HTTPS 且不能解析到私网；只有平台可达的内部服务才选择 <code>PLATFORM_INTERNAL</code>。</p></div></li>
                <li><span>3</span><div><strong>填写 Base URL 与相对路径</strong><p>路径必须以单个 <code>/</code> 开头，不能包含域名、查询串、片段或 <code>..</code>。超时范围 1–60 秒，最多重试 1–5 次。</p></div></li>
                <li><span>4</span><div><strong>测试连接</strong><p>平台后端解析地址、应用鉴权并请求健康接口；测试结果会记录 HTTP 状态、延迟和安全错误码。</p></div></li>
                <li><span>5</span><div><strong>启用已通过测试的修订</strong><p>启用会把该修订原子设为活动修订。后续修改会产生新修订，旧版本不会被覆盖。</p></div></li>
              </ol>
              <CodeBlock title="连接配置示例" value={CONNECTION_EXAMPLE} />
              <StepResult>连接状态为“已启用”，活动修订号正确，最新测试为“通过”。</StepResult>
            </div>
          </section>

          <section id="version" className="application-guide-section">
            <div className="application-guide-section__number">07</div>
            <div className="application-guide-section__body">
              <p className="platform-section-label">不可变发布单元</p>
              <h2>创建版本和初始化步骤</h2>
              <p>版本描述“所有环境都相同”的应用能力：语义版本、连接逻辑键、初始化步骤和依赖。包含 Provider 回调时选择 <code>SAGA_V1</code>，并从已启用连接中选择 Provider。</p>
              <CodeBlock title="版本配置示例" value={VERSION_EXAMPLE} />
              <dl className="application-guide-definition-list">
                <div><dt>步骤代码</dt><dd>版本内唯一的稳定标识，会进入步骤审计和幂等请求头。</dd></div>
                <div><dt>步骤类型</dt><dd>新应用的通用执行器当前执行 <code>PROVIDER_CALLBACK</code>；平台和依赖能力类型用于受管内置能力。</dd></div>
                <div><dt>能力</dt><dd>受限逻辑标识，如 <code>tenant.activate</code>，不能填写 URL、脚本或文件路径。</dd></div>
                <div><dt>契约版本</dt><dd>必须与活动连接修订的契约版本一致。</dd></div>
              </dl>
              <StepResult>版本草稿显示正确的 Provider 绑定、SAGA_V1 和初始化步骤。</StepResult>
            </div>
          </section>

          <section id="dependencies" className="application-guide-section">
            <div className="application-guide-section__number">08</div>
            <div className="application-guide-section__body">
              <p className="platform-section-label">依赖治理</p>
              <h2>声明应用依赖</h2>
              <p>依赖必须从已发布应用中选择。平台在版本验证时检查版本约束和依赖环，在租户开通时检查强依赖的实际运行状态。</p>
              <div className="application-guide-contract-table application-guide-contract-table--dependencies" role="table" aria-label="依赖策略">
                <div role="row"><strong role="columnheader">配置</strong><strong role="columnheader">含义</strong><strong role="columnheader">建议</strong></div>
                <div role="row"><code>REQUIRED_ACTIVATION</code><span>开通过程必须依赖</span><span>初始化需要依赖资源时使用</span></div>
                <div role="row"><code>REQUIRED_RUNTIME</code><span>应用运行期持续依赖</span><span>数据底座、授权或执行底座使用</span></div>
                <div role="row"><code>OPTIONAL</code><span>缺失不阻断开通</span><span>只影响增强能力时使用</span></div>
                <div role="row"><code>REQUIRE_EXISTING</code><span>要求租户已经开通依赖</span><span>首选，影响最清晰</span></div>
                <div role="row"><code>AUTO_PROVISION_ALLOWED</code><span>声明允许编排器自动联动</span><span>仍需运营明确确认影响计划</span></div>
              </div>
              <p className="application-guide-detail">版本约束首期支持精确版本 <code>x.y.z</code>、显式等于 <code>=x.y.z</code>、最低版本 <code>&gt;=x.y.z</code> 和任意版本 <code>*</code>。应用不能依赖自身，同一依赖不能重复声明。</p>
              <StepResult>每个强依赖都有明确类型、最低版本和开通策略，且依赖图无环。</StepResult>
            </div>
          </section>

          <section id="publication" className="application-guide-section">
            <div className="application-guide-section__number">09</div>
            <div className="application-guide-section__body">
              <p className="platform-section-label">发布门禁</p>
              <h2>验证并发布版本</h2>
              <ol className="application-guide-steps">
                <li><span>1</span><div><strong>点击“验证”</strong><p>平台检查清单 schema、标识符、Provider 连接、契约版本、依赖版本和依赖环。验证通过后版本进入“已验证”。</p></div></li>
                <li><span>2</span><div><strong>确认并发布</strong><p>发布后版本不可修改，并成为新租户使用的默认版本；已有租户不会被静默升级。</p></div></li>
                <li><span>3</span><div><strong>确认目录状态</strong><p>首个版本发布后应用目录进入“已发布”，租户应用中心才能看到它。</p></div></li>
              </ol>
              <div className="application-guide-callout application-guide-callout--important"><ShieldCheck size={18} aria-hidden /><div><strong>先发布不等于已开通</strong><p>发布只开放目录和固定默认版本，不会调用 Provider，也不会为任何租户创建资源。</p></div></div>
              <StepResult>版本和应用目录均为“已发布”，默认版本号正确。</StepResult>
            </div>
          </section>

          <section id="activation" className="application-guide-section">
            <div className="application-guide-section__number">10</div>
            <div className="application-guide-section__body">
              <p className="platform-section-label">业务验收</p>
              <h2>为测试租户开通并观测</h2>
              <p>进入“租户目录 → 测试租户 → 应用中心”，核对依赖状态后执行开通。平台会固定默认版本和当前活动连接修订，再按步骤调用 Provider。</p>
              <div className="application-guide-runtime-flow">
                <div><span>1</span><strong>解析目录与版本</strong><p>固定发布版本</p></div>
                <div><span>2</span><strong>检查强依赖</strong><p>缺失即失败关闭</p></div>
                <div><span>3</span><strong>执行 Provider 步骤</strong><p>按修订和幂等键调用</p></div>
                <div><span>4</span><strong>更新租户投影</strong><p>仅成功后写 ACTIVE</p></div>
              </div>
              <p className="application-guide-detail">联调时同时观察三处：Provider 按 <code>X-Correlation-Id</code> 检索的日志、平台 operation/step 的状态与尝试次数、租户应用卡片的实际状态。任何一步失败都不能把租户标记为 ACTIVE。</p>
              <StepResult>测试租户为 ACTIVE；Provider 资源唯一；operation 和全部 step 为 SUCCEEDED；再次提交同一幂等请求不会重复创建资源。</StepResult>
            </div>
          </section>

          <section id="operations" className="application-guide-section">
            <div className="application-guide-section__number">11</div>
            <div className="application-guide-section__body">
              <p className="platform-section-label">生命周期</p>
              <h2>暂停、恢复、校准与升级</h2>
              <dl className="application-guide-definition-list application-guide-definition-list--operations">
                <div><dt><code>SUSPEND</code></dt><dd>暂停租户应用入口或执行能力，不删除 Provider 业务数据；成功后平台状态为 SUSPENDED。</dd></div>
                <div><dt><code>RESUME</code></dt><dd>恢复同一租户的既有资源；不能把恢复实现为重新创建一套资源。</dd></div>
                <div><dt><code>RECONCILE</code></dt><dd>校准受管资源漂移。应安全补齐缺失项，不覆盖租户不受管数据。</dd></div>
                <div><dt><code>UPGRADE</code></dt><dd>先发布新版本，再对目标租户显式升级；Provider 根据目标版本执行可重试迁移。</dd></div>
              </dl>
              <p className="application-guide-detail">修改服务地址、路径、鉴权引用或超时策略时创建并测试新连接修订，再切换活动修订。改变环境键或网络范围必须新建绑定键，并由新应用版本显式引用。</p>
              <StepResult>每个支持的运行期动作都有幂等实现、可回滚方案、审计记录和负向测试。</StepResult>
            </div>
          </section>

          <section id="troubleshooting" className="application-guide-section">
            <div className="application-guide-section__number">12</div>
            <div className="application-guide-section__body">
              <p className="platform-section-label">联调手册</p>
              <h2>常见问题与发布前检查</h2>
              <div className="application-guide-troubleshooting">
                <details><summary><code>PROVIDER_HEALTH_ADDRESS_REJECTED</code><span>地址或网络范围不符合安全策略</span></summary><p>检查 PUBLIC_HTTPS 是否使用 HTTPS、DNS 是否解析到私网/回环/链路本地地址，以及路径是否为安全相对路径。</p></details>
                <details><summary><code>PROVIDER_HEALTH_SECRET_UNAVAILABLE</code><span>Secret 引用在当前环境未解析</span></summary><p>确认表单填写的是引用名，并由运维在当前环境配置对应的 <code>app.platform.provider-secrets.&lt;secret_ref&gt;</code>。</p></details>
                <details><summary><code>PROVIDER_TIMEOUT / UNREACHABLE</code><span>超时、路由或防火墙问题</span></summary><p>从平台后端所在网络检查 DNS、路由和端口；不要用浏览器能访问作为平台可访问的证明。</p></details>
                <details><summary><code>PROVIDER_REJECTED</code><span>响应状态不符合契约</span></summary><p>确保返回 2xx JSON，且 status 为 SUCCEEDED、ACTIVE 或 SUSPENDED。业务失败应使用非 2xx 并保留 Provider 内部关联号。</p></details>
                <details><summary>版本验证提示连接或依赖不满足</summary><p>确认连接已测试并启用、契约版本一致；依赖应用存在满足约束的已发布版本，且没有自依赖或依赖环。</p></details>
              </div>
              <div className="application-guide-release-check">
                <div><ClipboardCheck size={20} aria-hidden /><span><strong>发布前 8 项检查</strong><small>全部满足后再安排真实租户开通</small></span></div>
                <ul>
                  <li><Check size={14} aria-hidden />健康检查从平台网络可达</li>
                  <li><Check size={14} aria-hidden />鉴权正向与负向用例通过</li>
                  <li><Check size={14} aria-hidden />重复请求不产生重复资源</li>
                  <li><Check size={14} aria-hidden />租户数据按 companyId 隔离</li>
                  <li><Check size={14} aria-hidden />暂停不删除业务数据</li>
                  <li><Check size={14} aria-hidden />依赖和版本约束已确认</li>
                  <li><Check size={14} aria-hidden />日志可按 operationId 检索</li>
                  <li><Check size={14} aria-hidden />失败路径和回滚方案已演练</li>
                </ul>
              </div>
              <div className="application-guide-next">
                <div><Wrench size={19} aria-hidden /><span><strong>准备开始配置？</strong><p>返回应用中心登记应用，或回到当前应用继续创建运行连接。</p></span></div>
                <button type="button" className="platform-button platform-button--primary" onClick={() => navigate(returnPath)}>{safeSourceApp ? "返回应用详情" : "前往应用中心"}<Route size={15} aria-hidden /></button>
              </div>
            </div>
          </section>

          <footer className="application-guide-footer">
            <Code2 size={16} aria-hidden />
            <span>契约版本：<code>tenant-application/v1</code></span>
            <Link2 size={15} aria-hidden />
            <span>Provider 生命周期：<code>v1</code></span>
          </footer>
        </main>
      </div>
    </div>
  );
}
