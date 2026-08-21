import { useEffect, useState } from "react";
import type { ReactNode } from "react";
import {
  BookOpen,
  Check,
  CheckCircle2,
  ClipboardCheck,
  Container,
  Copy,
  Database,
  ExternalLink,
  FileKey2,
  PackageCheck,
  RotateCcw,
  ServerCog,
  ShieldCheck,
} from "lucide-react";

export const DEPLOYMENT_INSTALLATION_SECTIONS = [
  { id: "overview", number: "01", label: "部署全景" },
  { id: "artifacts", number: "02", label: "制品来源" },
  { id: "prerequisites", number: "03", label: "安装前准备" },
  { id: "keycloak", number: "04", label: "安装 Keycloak" },
  { id: "agentcici", number: "05", label: "安装 AgentCiCi" },
  { id: "semattice", number: "06", label: "安装 Semattice" },
  { id: "integration", number: "07", label: "集成与启动" },
  { id: "acceptance", number: "08", label: "验收与回滚" },
] as const;

export const DEPLOYMENT_INSTALLATION_AGENT_GUIDE_PATH = "/agent-docs/operations/deployment-installation.md";

export const ARTIFACT_COORDINATES_EXAMPLE = `# 客户 release-manifest.env 中的脱敏坐标模板
REGISTRY_HOST=<authorized-registry-host>
REGISTRY_NAMESPACE=<authorized-namespace>
AGENTCICI_VERSION=<immutable-version>

AGENTCICI_BACKEND_IMAGE=\${REGISTRY_HOST}/\${REGISTRY_NAMESPACE}/cici-backend:\${AGENTCICI_VERSION}
AGENTCICI_FRONTEND_IMAGE=\${REGISTRY_HOST}/\${REGISTRY_NAMESPACE}/cici-frontend:\${AGENTCICI_VERSION}
KEYCLOAK_IMAGE=quay.io/keycloak/keycloak:<approved-version>

# Semattice 当前受管交付不是 OCI 镜像
SEMATTICE_RELEASE_ARCHIVE=<authorized-linux-amd64-release-archive>
SEMATTICE_RELEASE_SHA256=<sha256-from-release-manifest>`;

export const DELIVERY_LAYOUT_EXAMPLE = `delivery/
├── release-manifest.env       # 版本和制品坐标，不含 Secret
├── image-lock.txt             # OCI digest 锁定
├── checksums.sha256           # 二进制与静态资源校验
├── compose/                   # 受管 Compose 模板
├── systemd/                   # Semattice unit 模板
├── nginx/                     # 当前环境 edge 模板
├── migrations/               # 与冻结版本匹配的迁移集合
└── rollback.md                # 上一版本和恢复步骤`;

export const KEYCLOAK_INSTALL_EXAMPLE = `docker pull "\${KEYCLOAK_IMAGE}"
docker image inspect "\${KEYCLOAK_IMAGE}" --format '{{json .RepoDigests}}'

# 管理员口令、数据库 URL 和 Client Secret 只从受管 Secret 文件注入
docker compose --env-file <root-only-secret-file> config
docker compose --env-file <root-only-secret-file> up -d keycloak`;

export const AGENTCICI_INSTALL_EXAMPLE = `docker login "\${REGISTRY_HOST}"
docker pull "\${AGENTCICI_BACKEND_IMAGE}"
docker pull "\${AGENTCICI_FRONTEND_IMAGE}"

# 先核对渲染结果，再启动；不得把 Secret 写进 Compose
docker compose --env-file <root-only-secret-file> config
docker compose --env-file <root-only-secret-file> up -d postgres redis rabbitmq qdrant
docker compose --env-file <root-only-secret-file> up -d backend frontend`;

export const SEMATTICE_INSTALL_EXAMPLE = `sha256sum -c checksums.sha256
install -d -m 0755 <release-root>/<release-id>
install -m 0755 semattice-linux-amd64 <release-root>/<release-id>/semattice

# 静态资源、迁移和 unit 使用同一冻结 release；Secret 由受管环境文件注入
ln -sfn <release-root>/<release-id> <current-link>
systemctl daemon-reload
systemctl restart semattice`;

export const ACCEPTANCE_COMMANDS_EXAMPLE = `# 每个产品都记录：版本、commit、digest 或 SHA-256、迁移、健康和回滚点
docker compose ps
docker inspect <container> --format '{{.Image}} {{.RestartCount}} {{.State.Health.Status}}'
systemctl is-active keycloak semattice
systemctl show semattice -p NRestarts

# 使用交付清单声明的健康、版本、OIDC discovery 和匿名鉴权探针
# 预期 401/403 是受保护 API 的安全证据；HTML 200 不是 API 健康证据`;

type CodeBlockProps = {
  title: string;
  language?: string;
  value: string;
};

function CodeBlock({ title, language = "shell", value }: CodeBlockProps) {
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

function GuideNote({ icon, title, children }: { icon: ReactNode; title: string; children: ReactNode }) {
  return <div className="deployment-guide-note">{icon}<div><strong>{title}</strong><p>{children}</p></div></div>;
}

export default function PlatformDeploymentInstallationPage() {
  useEffect(() => {
    const scrollLockClass = "application-guide-scroll-lock";
    document.documentElement.classList.add(scrollLockClass);
    document.body.classList.add(scrollLockClass);
    window.scrollTo({ top: 0, left: 0 });
    const anchorFrame = window.requestAnimationFrame(() => {
      const targetId = decodeURIComponent(window.location.hash.slice(1));
      if (!DEPLOYMENT_INSTALLATION_SECTIONS.some((section) => section.id === targetId)) return;
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
    <div className="admin-page platform-page application-integration-guide deployment-installation-guide">
      <header className="platform-page-head application-guide-head">
        <div className="platform-page-head__main">
          <p className="platform-section-label">运维中心 · 私有化交付</p>
          <h1 className="skills-catalog__title">部署安装</h1>
          <p className="subtle skills-catalog__subtitle">AgentCiCi、Semattice 与 Keycloak 的制品获取、安装顺序、验收和回滚手册。</p>
        </div>
        <div className="application-guide-head__aside">
          <a
            className="platform-button application-guide-agent-link"
            href={DEPLOYMENT_INSTALLATION_AGENT_GUIDE_PATH}
            target="_blank"
            rel="noreferrer"
          >
            Agent 版 Markdown
            <ExternalLink size={14} aria-hidden />
          </a>
          <div className="application-guide-head__meta" aria-label="指南适用范围">
            <BookOpen size={18} aria-hidden />
            <span><strong>适用于</strong>受管私有化安装</span>
            <span><strong>文档版本</strong>deployment-installation/v1</span>
          </div>
        </div>
      </header>

      <div className="deployment-guide-flow" aria-label="部署顺序">
        <span>基础设施</span><span aria-hidden>→</span>
        <span>Keycloak</span><span aria-hidden>→</span>
        <span>Semattice</span><span aria-hidden>→</span>
        <span>AgentCiCi</span><span aria-hidden>→</span>
        <span>技术验收</span><span aria-hidden>→</span>
        <span>业务验收</span>
      </div>

      <div className="application-guide-layout">
        <aside className="application-guide-toc" aria-label="部署安装指南目录">
          <p>本页目录</p>
          <nav>
            {DEPLOYMENT_INSTALLATION_SECTIONS.map((section) => (
              <a key={section.id} href={`#${section.id}`}><span>{section.number}</span>{section.label}</a>
            ))}
          </nav>
          <div className="application-guide-toc__note">
            <ShieldCheck size={17} aria-hidden />
            <p><strong>安全边界</strong>页面不提供任何真实 Secret。制品凭据只通过授权交付渠道和受管 Secret 文件使用。</p>
          </div>
        </aside>

        <main className="application-guide-content">
          <section id="overview" className="application-guide-section">
            <div className="application-guide-section__number">01</div>
            <div className="application-guide-section__body">
              <p className="platform-section-label">先确认产品边界</p>
              <h2>部署全景</h2>
              <p>这不是一个可以用同一版本号整体覆盖的单体系统。AgentCiCi、Semattice 和 Keycloak 分别拥有制品、数据、配置、版本与回滚点；安装时可以编排启动，但必须独立记录和验收。</p>
              <div className="application-guide-principles">
                <Principle title="AgentCiCi">企业智能体入口与治理控制面。以 backend、frontend 两个 OCI 镜像交付，并依赖独立 PostgreSQL、Redis、RabbitMQ 和 Qdrant。</Principle>
                <Principle title="Semattice">业务语义与运行数据提供方。当前受管交付是 Linux amd64 二进制、静态资源和迁移集合，通过不可变 release 原子切换。</Principle>
                <Principle title="Keycloak">统一认证基础设施。使用官方 OCI 镜像，独立数据库、Realm 与受管 Client 配置，不随应用版本静默覆盖。</Principle>
              </div>
              <GuideNote icon={<ShieldCheck size={18} aria-hidden />} title="版本不是联动开关">
                三个产品不得共享一个“平台版本”。任何升级或回滚只作用于目标产品；跨产品契约按照提供方先就绪、消费方后启用的顺序验证。
              </GuideNote>
            </div>
          </section>

          <section id="artifacts" className="application-guide-section">
            <div className="application-guide-section__number">02</div>
            <div className="application-guide-section__body">
              <p className="platform-section-label">先拿到可信制品</p>
              <h2>安装镜像与制品从哪里获取</h2>
              <p>每次交付都应附带 release manifest、digest 或 SHA-256 和回滚版本。没有授权清单时不要猜测 registry、namespace、版本或 Semattice 镜像名称。</p>
              <div className="deployment-guide-artifact-table" role="table" aria-label="安装制品来源">
                <div role="row"><strong role="columnheader">产品</strong><strong role="columnheader">制品</strong><strong role="columnheader">取得方式</strong><strong role="columnheader">必须锁定</strong></div>
                <div role="row"><strong>AgentCiCi</strong><span><code>cici-backend</code>、<code>cici-frontend</code> OCI 镜像</span><span>从 CloudCC 授权的私有镜像仓库和客户 release manifest 获取</span><span>版本 tag + OCI digest</span></div>
                <div role="row"><strong>Semattice</strong><span>Linux amd64 二进制、静态资源、migration</span><span>从 Semattice 拥有方的授权 release bundle 获取；当前不假设存在 OCI 镜像</span><span>版本 + Git commit + SHA-256</span></div>
                <div role="row"><strong>Keycloak</strong><span>官方 OCI 镜像</span><span><code>quay.io/keycloak/keycloak</code>，精确版本由交付清单批准</span><span>精确版本 + OCI digest</span></div>
              </div>
              <CodeBlock title="制品坐标模板" language="env" value={ARTIFACT_COORDINATES_EXAMPLE} />
              <CodeBlock title="建议交付包结构" language="text" value={DELIVERY_LAYOUT_EXAMPLE} />
              <StepResult>所有制品都能映射到交付清单中的版本、commit、digest 或 SHA-256；没有使用 <code>latest</code>、未知版本或未授权下载地址。</StepResult>
            </div>
          </section>

          <section id="prerequisites" className="application-guide-section">
            <div className="application-guide-section__number">03</div>
            <div className="application-guide-section__body">
              <p className="platform-section-label">主机与数据边界</p>
              <h2>安装前准备</h2>
              <p>先完成容量、时钟、DNS/TLS、备份目录、日志保留和最小权限设计，再解压或拉取制品。配置模板只能引用变量，真实值由目标环境注入。</p>
              <ul className="application-guide-checklist">
                <li><Check size={14} aria-hidden /><span>准备受支持的 Linux 主机、Docker/Compose 或 systemd，并校准时钟；架构与交付制品一致。</span></li>
                <li><Check size={14} aria-hidden /><span>为 AgentCiCi、Semattice、Keycloak 分配独立 PostgreSQL database 和 role，不共用连接串。</span></li>
                <li><Check size={14} aria-hidden /><span>为 AgentCiCi 准备独占 Redis、RabbitMQ、Qdrant 数据卷，并明确备份与恢复位置。</span></li>
                <li><Check size={14} aria-hidden /><span>准备 root-only Secret 文件或正式 Secret 管理系统；管理员口令、数据库 URL、Client Secret 不进入仓库。</span></li>
                <li><Check size={14} aria-hidden /><span>准备受校验的环境配置生成公网 Origin、OIDC issuer 和跨应用入口；浏览器前端不自行拼接地址。</span></li>
                <li><Check size={14} aria-hidden /><span>记录当前环境、目标版本、维护窗口、上一版本、备份路径、回滚负责人和验收人。</span></li>
              </ul>
              <GuideNote icon={<Database size={18} aria-hidden />} title="数据库迁移只向前执行">
                发布前先验证可恢复备份。历史 migration 不因应用回滚自动反向执行；需要数据恢复时必须使用已批准的恢复方案。
              </GuideNote>
            </div>
          </section>

          <section id="keycloak" className="application-guide-section">
            <div className="application-guide-section__number">04</div>
            <div className="application-guide-section__body">
              <p className="platform-section-label">统一认证先就绪</p>
              <h2>安装 Keycloak</h2>
              <p>Keycloak 应先于业务应用就绪。使用官方镜像和独立数据库，以 production 模式启动；Realm、Client、redirect URI、Web Origin、SMTP 和主题由受管配置导入或脚本幂等创建。</p>
              <ol className="application-guide-steps">
                <li><span>1</span><div><strong>拉取并核对镜像</strong><p>只使用交付清单批准的精确版本，回读 digest 并写入 image lock。</p></div></li>
                <li><span>2</span><div><strong>注入数据库和 bootstrap Secret</strong><p>真实值只存在于目标主机受管文件；Compose 渲染输出不得包含在工单或截图中。</p></div></li>
                <li><span>3</span><div><strong>启动并创建 Realm</strong><p>以幂等脚本建立 AgentCiCi 使用的 Realm、HUMAN/服务 Client、scope、redirect URI 和主题。</p></div></li>
                <li><span>4</span><div><strong>验证 OIDC 边界</strong><p>检查 readiness、discovery、JWKS、授权码登录和错误 redirect；管理端口不对公网暴露。</p></div></li>
              </ol>
              <CodeBlock title="Keycloak 安装命令模板" value={KEYCLOAK_INSTALL_EXAMPLE} />
              <StepResult>Keycloak 健康，OIDC discovery 与 JWKS 可读，受保护管理入口保持关闭，Realm/Client 配置可重复执行且未输出 Secret。</StepResult>
            </div>
          </section>

          <section id="agentcici" className="application-guide-section">
            <div className="application-guide-section__number">05</div>
            <div className="application-guide-section__body">
              <p className="platform-section-label">双镜像应用</p>
              <h2>安装 AgentCiCi</h2>
              <p>AgentCiCi 的 backend 与 frontend 必须来自同一冻结 commit 和版本。先启动四项状态服务并确认健康，再启动 backend 让 migration 完成，最后启动 frontend/edge。</p>
              <ol className="application-guide-steps">
                <li><span>1</span><div><strong>登录授权私有仓库</strong><p>使用 pull-only 机器人账号完成登录，认证文件保持 root-only；不要复制 Docker auth 内容。</p></div></li>
                <li><span>2</span><div><strong>拉取双镜像并锁定 digest</strong><p>backend/frontend 的版本、revision label 与交付清单必须一致，禁止一新一旧。</p></div></li>
                <li><span>3</span><div><strong>启动状态服务</strong><p>PostgreSQL、Redis、RabbitMQ、Qdrant 使用独立持久卷，先完成健康检查和备份基线。</p></div></li>
                <li><span>4</span><div><strong>启动 backend</strong><p>注入 Keycloak/OIDC、数据库、消息队列、向量库和加密配置，等待 migration 与应用健康通过。</p></div></li>
                <li><span>5</span><div><strong>启动 frontend/edge</strong><p>前端只调用同源 API；公网域名、TLS 和跨应用跳转由环境配置及后端生成。</p></div></li>
              </ol>
              <CodeBlock title="AgentCiCi 安装命令模板" value={AGENTCICI_INSTALL_EXAMPLE} />
              <GuideNote icon={<Container size={18} aria-hidden />} title="应用升级不重建状态服务">
                常规 AgentCiCi 发布只替换 backend/frontend。PostgreSQL、Redis、RabbitMQ 和 Qdrant 只有在自身维护任务中才允许重建或恢复。
              </GuideNote>
              <StepResult>backend/frontend 版本、commit 和镜像指纹一致；容器健康、restart=0；migration 成功；匿名访问受保护 API 返回结构化 401。</StepResult>
            </div>
          </section>

          <section id="semattice" className="application-guide-section">
            <div className="application-guide-section__number">06</div>
            <div className="application-guide-section__body">
              <p className="platform-section-label">不可变二进制 release</p>
              <h2>安装 Semattice</h2>
              <p>Semattice 当前受管交付使用 Linux amd64 二进制和静态资源，不应从 AgentCiCi 私有仓库猜测同名 OCI 镜像。每次安装创建新的不可变 release 目录，经校验后原子切换 <code>current</code>。</p>
              <ol className="application-guide-steps">
                <li><span>1</span><div><strong>验证 release bundle</strong><p>核对版本、Git commit、二进制/静态资源 SHA-256 和 migration 清单。</p></div></li>
                <li><span>2</span><div><strong>准备三类数据库身份</strong><p>migrator、control、runtime 权限分离；runtime/control 不拥有业务数据，也不能绕过行级隔离。</p></div></li>
                <li><span>3</span><div><strong>执行受管 migration</strong><p>只使用当前冻结 release 携带的迁移集合，记录 schema version 与 checksum。</p></div></li>
                <li><span>4</span><div><strong>安装新 release 并切换</strong><p>写入新目录、核对 checksum、更新 <code>current</code>、重启 systemd；旧 release 保留。</p></div></li>
                <li><span>5</span><div><strong>验证提供方契约</strong><p>检查 health/version、匿名 401/403、OIDC 登录和目标服务身份探测；SPA HTML 不能作为 API 成功。</p></div></li>
              </ol>
              <CodeBlock title="Semattice 安装命令模板" value={SEMATTICE_INSTALL_EXAMPLE} />
              <StepResult>systemd active、NRestarts=0；版本/commit/SHA-256 一致；schema migration 完整；匿名边界和实际服务身份探测符合交付矩阵。</StepResult>
            </div>
          </section>

          <section id="integration" className="application-guide-section">
            <div className="application-guide-section__number">07</div>
            <div className="application-guide-section__body">
              <p className="platform-section-label">逐层放行</p>
              <h2>集成配置与启动顺序</h2>
              <p>每一层先健康、可追溯、鉴权边界正确，再启动消费方。失败时只停止或回滚当前产品，不把局部故障扩大成全栈重装。</p>
              <div className="application-guide-runtime-flow deployment-guide-runtime-flow">
                <div><span>1</span><strong>数据库与依赖</strong><p>持久卷、备份、连接隔离</p></div>
                <div><span>2</span><strong>Keycloak</strong><p>discovery、JWKS、Realm</p></div>
                <div><span>3</span><strong>Semattice</strong><p>health、version、契约边界</p></div>
                <div><span>4</span><strong>AgentCiCi</strong><p>migration、health、同源入口</p></div>
                <div><span>5</span><strong>集成探测</strong><p>真实 SERVICE 身份</p></div>
                <div><span>6</span><strong>业务验收</strong><p>受权 HUMAN 场景</p></div>
              </div>
              <dl className="application-guide-definition-list">
                <div><dt>OIDC 信任</dt><dd>AgentCiCi 与 Semattice 只信任受校验的 issuer、audience、JWKS 和 authorized party；浏览器会话不能替代 SERVICE 身份。</dd></div>
                <div><dt>跨产品契约</dt><dd>新增、变更、启用或切换的契约先验证提供方远程主线与运行制品，再由真实消费方身份完成成功探测。</dd></div>
                <div><dt>入口与 TLS</dt><dd>环境地址只存在于受管部署配置。先校验 edge 配置，再 reload；不要让 API 路径落入 SPA fallback。</dd></div>
                <div><dt>稳定窗口</dt><dd>记录健康、重启数和错误日志稳定窗口。一次 HTTP 200 或页面可见不能替代版本与制品回读。</dd></div>
              </dl>
              <StepResult>提供方与消费方的版本、契约和身份矩阵完整；跨系统探测有 correlation ID 和可回读结果，未用 HUMAN 页面或伪造签名替代。</StepResult>
            </div>
          </section>

          <section id="acceptance" className="application-guide-section">
            <div className="application-guide-section__number">08</div>
            <div className="application-guide-section__body">
              <p className="platform-section-label">未验证不算成功</p>
              <h2>验收、备份与回滚</h2>
              <p>安装完成要分层报告：制品与配置、技术健康、安全边界、真实业务。每层只记录实际执行证据，未运行的 HUMAN 验收必须明确标记待完成。</p>
              <div className="deployment-guide-acceptance-table" role="table" aria-label="部署验收层级">
                <div role="row"><strong role="columnheader">证据层</strong><strong role="columnheader">至少回读</strong><strong role="columnheader">不能替代</strong></div>
                <div role="row"><strong>制品</strong><span>版本、commit、digest/SHA-256、配置修订、migration</span><span>页面角标或 tag 名</span></div>
                <div role="row"><strong>运行</strong><span>健康、restart/NRestarts、错误日志、依赖状态、稳定窗口</span><span>单次 HTTP 200</span></div>
                <div role="row"><strong>安全</strong><span>匿名 401/403、OIDC discovery/JWKS、最小 SERVICE 探测</span><span>HUMAN 登录页面</span></div>
                <div role="row"><strong>业务</strong><span>指定租户、真实登录态、关键业务结果、跨租户负向</span><span>技术 smoke 或 mock</span></div>
              </div>
              <CodeBlock title="部署后证据模板" value={ACCEPTANCE_COMMANDS_EXAMPLE} />
              <div className="application-guide-release-check">
                <div><ClipboardCheck size={20} aria-hidden /><span><strong>回滚前必须已经具备</strong><small>没有回滚点时停止安装或升级</small></span></div>
                <ul>
                  <li><Check size={14} aria-hidden />上一运行版本与制品仍可取得</li>
                  <li><Check size={14} aria-hidden />数据库和状态目录备份非空</li>
                  <li><Check size={14} aria-hidden />受管配置与权限已备份</li>
                  <li><Check size={14} aria-hidden />回滚命令经过只读核对</li>
                  <li><Check size={14} aria-hidden />migration 恢复策略已批准</li>
                  <li><Check size={14} aria-hidden />回滚后健康探针已定义</li>
                </ul>
              </div>
              <GuideNote icon={<RotateCcw size={18} aria-hidden />} title="回滚只作用于失败产品">
                AgentCiCi 恢复旧双镜像且保留状态服务；Semattice 将 current 切回上一不可变 release；Keycloak 恢复受控配置与数据库备份。不要删除数据目录或联动回滚其他产品。
              </GuideNote>
              <div className="deployment-guide-complete">
                <PackageCheck size={20} aria-hidden />
                <div><strong>交付完成定义</strong><p>制品可追溯、运行健康、安全边界通过、回滚可执行；真实业务验收是否完成单独列明。</p></div>
              </div>
            </div>
          </section>

          <footer className="application-guide-footer deployment-guide-footer">
            <ServerCog size={16} aria-hidden />
            <span>文档契约：<code>deployment-installation/v1</code></span>
            <FileKey2 size={15} aria-hidden />
            <span>Secret 原文：<code>never-in-document</code></span>
          </footer>
        </main>
      </div>
    </div>
  );
}
