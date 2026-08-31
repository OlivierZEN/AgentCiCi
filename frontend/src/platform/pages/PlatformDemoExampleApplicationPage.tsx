import { useEffect, useMemo, useState } from "react";
import { AppWindow, ArrowLeft, CheckCircle2, CircleOff, Database, ShieldCheck } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { PLATFORM_API_BASE } from "../../constants";
import { safeFetchJson } from "../../utils/http";
import { readPlatformToken } from "./platformTenantsShared";

type DemoDependency = {
  appCode: string;
  versionConstraint: string;
  dependencyType: string;
  activationPolicy: string;
};

type DemoVersion = {
  version: string;
  manifestSchemaVersion: string;
  providerBindingKey?: string | null;
  initializationEngine: string;
  manifest: { schemaVersion?: string; initializationEngine?: string; steps?: unknown[] };
  manifestDigest: string;
  status: string;
  dependencies: DemoDependency[];
};

export type DemoApplicationDetail = {
  application: {
    appCode: string;
    displayName: string;
    summary: string;
    iconKey: string;
    ownerTeam: string;
    tenantMode: string;
    catalogStatus: string;
    trustedAppCode?: string | null;
    launchMode: string;
    launchRouteKey?: string | null;
    defaultVersion?: string | null;
  };
  versions: DemoVersion[];
};

export type DemoParameterRow = {
  group: string;
  parameter: string;
  value: string;
  meaning: string;
};

export type DemoProviderConnectionRevision = {
  id: string;
  revisionNumber: number;
  baseUrl: string;
  contractVersion: string;
  authType: string;
  secretRef?: string | null;
  healthPath: string;
  activatePath?: string | null;
  reconcilePath?: string | null;
  suspendPath?: string | null;
  resumePath?: string | null;
  upgradePath?: string | null;
  timeoutMs: number;
  maxAttempts: number;
  testStatus: string;
};

export type DemoProviderConnection = {
  bindingKey: string;
  appCode: string;
  displayName: string;
  environmentKey: string;
  networkScope: string;
  status: string;
  activeRevisionId?: string | null;
  revisions: DemoProviderConnectionRevision[];
};

export function demoProviderConnectionRows(connection: DemoProviderConnection): DemoParameterRow[] {
  const revision = connection.revisions[0];
  return [
    { group: "连接身份", parameter: "displayName", value: connection.displayName, meaning: "运营人员可读名称" },
    { group: "连接身份", parameter: "bindingKey", value: connection.bindingKey, meaning: "应用版本引用的稳定逻辑键" },
    { group: "连接身份", parameter: "environmentKey", value: connection.environmentKey, meaning: "部署环境分区，不代表域名" },
    { group: "连接身份", parameter: "networkScope", value: connection.networkScope, meaning: "公网 HTTPS 或平台内部网络" },
    { group: "连接状态", parameter: "status", value: connection.status, meaning: "连接治理状态" },
    { group: "连接状态", parameter: "activeRevisionId", value: connection.activeRevisionId ?? "未启用", meaning: "只有测试通过后才能启用修订" },
    { group: "连接状态", parameter: "revisionNumber", value: String(revision?.revisionNumber ?? 0), meaning: "当前最新修订序号" },
    { group: "连接状态", parameter: "testStatus", value: revision?.testStatus ?? "无修订", meaning: "从未外呼测试，不伪造通过状态" },
    { group: "服务契约", parameter: "baseUrl", value: revision?.baseUrl ?? "无", meaning: "保留测试域名，不会被浏览器直连" },
    { group: "服务契约", parameter: "contractVersion", value: revision?.contractVersion ?? "无", meaning: "生命周期契约版本" },
    { group: "服务契约", parameter: "healthPath", value: revision?.healthPath ?? "无", meaning: "连接测试入口" },
    { group: "服务契约", parameter: "activatePath", value: revision?.activatePath ?? "无", meaning: "租户开通回调" },
    { group: "服务契约", parameter: "reconcilePath", value: revision?.reconcilePath ?? "无", meaning: "状态协调回调" },
    { group: "服务契约", parameter: "suspendPath", value: revision?.suspendPath ?? "无", meaning: "暂停回调" },
    { group: "服务契约", parameter: "resumePath", value: revision?.resumePath ?? "无", meaning: "恢复回调" },
    { group: "服务契约", parameter: "upgradePath", value: revision?.upgradePath ?? "无", meaning: "版本升级回调" },
    { group: "鉴权可靠性", parameter: "authType", value: revision?.authType ?? "无", meaning: "连接鉴权方式" },
    { group: "鉴权可靠性", parameter: "secretRef", value: revision?.secretRef ?? "无", meaning: "只保存 Secret 引用名" },
    { group: "鉴权可靠性", parameter: "timeoutMs", value: String(revision?.timeoutMs ?? 0), meaning: "单次请求超时，允许 1000 至 60000" },
    { group: "鉴权可靠性", parameter: "maxAttempts", value: String(revision?.maxAttempts ?? 0), meaning: "最大尝试次数，允许 1 至 5" },
  ];
}

export function demoEffectiveParameterRows(detail: DemoApplicationDetail): DemoParameterRow[] {
  const application = detail.application;
  const version = detail.versions.find((item) => item.version === application.defaultVersion)
    ?? detail.versions[0];
  const dependency = version?.dependencies[0];
  const stepCount = Array.isArray(version?.manifest?.steps) ? version.manifest.steps.length : 0;
  return [
    { group: "应用治理", parameter: "appCode", value: application.appCode, meaning: "发布后不可修改的应用代码" },
    { group: "应用治理", parameter: "displayName", value: application.displayName, meaning: "应用中心展示名称" },
    { group: "应用治理", parameter: "summary", value: application.summary, meaning: "一句话用途说明" },
    { group: "应用治理", parameter: "iconKey", value: application.iconKey, meaning: "受控图标语义" },
    { group: "应用治理", parameter: "ownerTeam", value: application.ownerTeam, meaning: "责任团队" },
    { group: "应用治理", parameter: "tenantMode", value: application.tenantMode, meaning: "平台基础应用，无租户 Provider 初始化" },
    { group: "应用治理", parameter: "catalogStatus", value: application.catalogStatus, meaning: "当前目录状态" },
    { group: "应用治理", parameter: "trustedAppCode", value: application.trustedAppCode ?? "未关联", meaning: "仅独立 Keycloak Client 需要" },
    { group: "应用治理", parameter: "launchMode", value: application.launchMode, meaning: "服务端解析的同源平台路由" },
    { group: "应用治理", parameter: "launchRouteKey", value: application.launchRouteKey ?? "无", meaning: "逻辑入口键，不是 URL" },
    { group: "不可变版本", parameter: "version", value: version?.version ?? "未发布", meaning: "语义版本" },
    { group: "不可变版本", parameter: "defaultVersion", value: application.defaultVersion ?? "未发布", meaning: "新租户读取的默认版本" },
    { group: "不可变版本", parameter: "manifestSchemaVersion", value: version?.manifestSchemaVersion ?? "无", meaning: "清单 schema" },
    { group: "不可变版本", parameter: "initializationEngine", value: version?.initializationEngine ?? "无", meaning: "本示例无需初始化" },
    { group: "不可变版本", parameter: "providerBindingKey", value: version?.providerBindingKey ?? "未绑定", meaning: "未测试连接不能绑定已发布版本" },
    { group: "不可变版本", parameter: "steps", value: String(stepCount), meaning: "初始化步骤数量" },
    { group: "不可变版本", parameter: "manifestDigest", value: version?.manifestDigest ?? "无", meaning: "规范化清单 SHA-256" },
    { group: "不可变版本", parameter: "status", value: version?.status ?? "无", meaning: "不可变版本状态" },
    { group: "应用依赖", parameter: "appCode", value: dependency?.appCode ?? "无", meaning: "依赖应用代码" },
    { group: "应用依赖", parameter: "versionConstraint", value: dependency?.versionConstraint ?? "无", meaning: "允许的发布版本" },
    { group: "应用依赖", parameter: "dependencyType", value: dependency?.dependencyType ?? "无", meaning: "本示例为可选依赖" },
    { group: "应用依赖", parameter: "activationPolicy", value: dependency?.activationPolicy ?? "无", meaning: "允许在计划中联动开通" },
  ];
}

export default function PlatformDemoExampleApplicationPage() {
  const navigate = useNavigate();
  const token = readPlatformToken();
  const [detail, setDetail] = useState<DemoApplicationDetail | null>(null);
  const [connections, setConnections] = useState<DemoProviderConnection[]>([]);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    async function load() {
      try {
        const headers = { Authorization: `Bearer ${token}` };
        const [detailResponse, connectionsResponse] = await Promise.all([
          fetch(`${PLATFORM_API_BASE}/internal-applications/demo-example`, { headers }),
          fetch(`${PLATFORM_API_BASE}/internal-applications/demo-example/connections`, { headers }),
        ]);
        const [{ body: detailBody }, { body: connectionsBody }] = await Promise.all([
          safeFetchJson(detailResponse),
          safeFetchJson(connectionsResponse),
        ]);
        if (!detailResponse.ok || !detailBody?.success) throw new Error(detailBody?.message ?? `HTTP ${detailResponse.status}`);
        if (!connectionsResponse.ok || !connectionsBody?.success || !Array.isArray(connectionsBody.data)) {
          throw new Error(connectionsBody?.message ?? `HTTP ${connectionsResponse.status}`);
        }
        if (active) {
          setDetail(detailBody.data as DemoApplicationDetail);
          setConnections(connectionsBody.data as DemoProviderConnection[]);
        }
      } catch (reason) {
        if (active) setError(reason instanceof Error ? reason.message : "DEMO 示例读取失败");
      }
    }
    void load();
    return () => { active = false; };
  }, [token]);

  const effectiveRows = useMemo(() => detail ? demoEffectiveParameterRows(detail) : [], [detail]);
  const connection = connections.find((item) => item.bindingKey === "demo-example.lifecycle");
  const connectionRows = useMemo(() => connection ? demoProviderConnectionRows(connection) : [], [connection]);

  return (
    <div className="admin-page skills-catalog platform-page demo-example-page">
      <header className="platform-page-head">
        <div>
          <button type="button" className="system-api-back" onClick={() => navigate("/platform/internal-applications/demo-example")}>
            <ArrowLeft size={15} /> DEMO示例应用
          </button>
          <p className="platform-section-label">单页 · 单对象 · 完整连接</p>
          <h1 className="platform-page-title">DEMO配置总览</h1>
          <p className="platform-page-subtitle">一个可发布的最小应用，用目录与连接回读解释每个参数。</p>
        </div>
        <div className="platform-page-head__aside">
          <span className="demo-example-status"><CheckCircle2 size={15} />{detail ? `${detail.application.catalogStatus === "PUBLISHED" ? "已发布" : detail.application.catalogStatus} · ${detail.application.defaultVersion ?? "无默认版本"}` : "正在回读"}</span>
        </div>
      </header>

      {error ? <div className="platform-console__error" role="alert">{error}</div> : null}
      {!detail && !error ? <section className="platform-console__panel demo-example-loading">正在读取应用配置对象…</section> : null}
      {detail ? (
        <section className="platform-console__panel demo-example-object" aria-labelledby="demo-example-object-title">
          <div className="demo-example-object__identity">
            <span aria-hidden="true"><Database size={20} /></span>
            <div>
              <p className="platform-section-label">唯一业务对象</p>
              <h2 id="demo-example-object-title">ApplicationConfiguration</h2>
              <p>对象记录：<code>{detail.application.appCode}</code>，数据源为应用中心目录 API。</p>
            </div>
            <span className="demo-example-object__count">1 个对象 · 1 条记录</span>
          </div>

          <div className="demo-example-section-head">
            <div>
              <h3>实际生效参数</h3>
              <p>这些值来自服务端读回，不在页面维护第二份事实。</p>
            </div>
            <span><ShieldCheck size={15} />已写入目录</span>
          </div>
          <ParameterTable rows={effectiveRows} label="DEMO 示例实际生效参数" />

          <div className="demo-example-section-head demo-example-section-head--reference">
            <div>
              <h3>运行连接实际配置</h3>
              <p>参数来自受管连接 API；记录已登记，但没有执行网络测试或启用。</p>
            </div>
            <span><CircleOff size={15} />{connection ? `${connection.status === "DRAFT" ? "草稿" : connection.status} · ${connection.revisions[0]?.testStatus === "NOT_TESTED" ? "未测试" : connection.revisions[0]?.testStatus ?? "无修订"}` : "连接缺失"}</span>
          </div>
          {connection ? <ParameterTable rows={connectionRows} label="DEMO 运行连接实际参数" /> : <div className="platform-console__error" role="alert">DEMO 运行连接尚未登记。</div>}

          <footer className="demo-example-footnote">
            <AppWindow size={16} />
            <p><strong>状态边界：</strong>示例已经登记连接和完整修订，但发布版本仍为 <code>NONE</code> 初始化且不绑定连接。只有替换为真实 Provider 地址、完成测试并启用后，才能创建引用该连接的 <code>SAGA_V1</code> 版本。</p>
          </footer>
        </section>
      ) : null}
    </div>
  );
}

function ParameterTable({ rows, label, reference = false }: { rows: DemoParameterRow[]; label: string; reference?: boolean }) {
  return (
    <table className={`demo-example-parameters${reference ? " demo-example-parameters--reference" : ""}`} aria-label={label}>
      <thead><tr><th>分组</th><th>参数</th><th>示例值</th><th>说明</th></tr></thead>
      <tbody>{rows.map((row) => (
        <tr key={`${row.group}-${row.parameter}`}>
          <td>{row.group}</td>
          <td><code>{row.parameter}</code></td>
          <td><code>{row.value}</code></td>
          <td>{row.meaning}</td>
        </tr>
      ))}</tbody>
    </table>
  );
}
