import { type FormEvent, useEffect, useMemo, useState } from "react";
import { useLocation } from "react-router-dom";
import { AUTOSERVICE_COPY, type AutoServiceSite } from "./autoservice-copy";
import "./autoservice-site.css";

type DemoFormState = {
  companyName: string;
  contactName: string;
  mobile: string;
  email: string;
  roleTitle: string;
  scenario: string;
};

const emptyDemoForm: DemoFormState = {
  companyName: "",
  contactName: "",
  mobile: "",
  email: "",
  roleTitle: "",
  scenario: "",
};

export default function AutoServiceLanding() {
  const location = useLocation();
  const site: AutoServiceSite = location.pathname.endsWith("/cn") || location.pathname.endsWith("/zh") ? "china" : "global";
  const copy = AUTOSERVICE_COPY[site];
  const [menuOpen, setMenuOpen] = useState(false);
  const [activeFlow, setActiveFlow] = useState(0);
  const [activeIntegration, setActiveIntegration] = useState(0);
  const [activeEngine, setActiveEngine] = useState(0);
  const [demoOpen, setDemoOpen] = useState(false);
  const [demoForm, setDemoForm] = useState<DemoFormState>(emptyDemoForm);
  const [demoSubmitting, setDemoSubmitting] = useState(false);
  const [demoSubmitted, setDemoSubmitted] = useState(false);
  const [demoNotice, setDemoNotice] = useState("");

  useEffect(() => {
    document.title = copy.seo.title;
    upsertMeta("description", copy.seo.description);
    upsertMeta("og:title", copy.seo.ogTitle, "property");
    upsertMeta("og:description", copy.seo.ogDescription, "property");
  }, [copy]);

  useEffect(() => {
    setMenuOpen(false);
    setActiveFlow(0);
    setActiveIntegration(0);
    setActiveEngine(0);
    setDemoOpen(false);
    setDemoForm(emptyDemoForm);
    setDemoSubmitted(false);
    setDemoNotice("");
  }, [site]);

  useEffect(() => {
    if (!demoOpen) return undefined;
    const originalRootOverflow = document.documentElement.style.overflow;
    const originalOverflow = document.body.style.overflow;
    document.documentElement.style.overflow = "hidden";
    document.body.style.overflow = "hidden";
    return () => {
      document.documentElement.style.overflow = originalRootOverflow;
      document.body.style.overflow = originalOverflow;
    };
  }, [demoOpen]);

  const activeEngineStep = copy.engine.steps[activeEngine];
  const matrixHeaders = useMemo(() => copy.workflows.headers.slice(1), [copy.workflows.headers]);
  const openDemoForm = () => {
    setMenuOpen(false);
    setDemoSubmitted(false);
    setDemoNotice("");
    setDemoOpen(true);
  };
  const closeDemoForm = () => {
    if (demoSubmitting) return;
    setDemoOpen(false);
  };
  const updateDemoField = (field: keyof DemoFormState, value: string) => {
    setDemoForm((current) => ({ ...current, [field]: value }));
  };
  const submitDemoRequest = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setDemoSubmitting(true);
    setDemoNotice("");
    try {
      const response = await fetch("/api/autoservice/demo-requests", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          ...demoForm,
          site,
          locale: copy.htmlLang,
          sourcePath: `${location.pathname}${location.hash}`,
        }),
      });
      const json = await response.json().catch(() => null);
      if (!response.ok || !json?.success) {
        throw new Error(json?.message || copy.demoForm.error);
      }
      setDemoForm(emptyDemoForm);
      setDemoSubmitted(true);
    } catch (error) {
      setDemoNotice(error instanceof Error ? error.message : copy.demoForm.error);
    } finally {
      setDemoSubmitting(false);
    }
  };

  return (
    <main className="autoservice-site" lang={copy.htmlLang} data-site={site}>
      <header className="as-header" aria-label="AutoService">
        <a className="as-logo" href={copy.siteHref} aria-label="AutoService home">
          <img className="as-logo__mark-img" src="/autoservice-logo-mark.png?v=20260509-1532" alt="" aria-hidden="true" />
          <img className="as-logo__word-img" src="/autoservice-logo-word.png?v=20260509-1532" alt="" aria-hidden="true" />
        </a>
        <nav className="as-nav" aria-label="Primary navigation">
          {copy.nav.map((item) => (
            <a key={item.href} href={item.href}>
              {item.label}
            </a>
          ))}
        </nav>
        <div className="as-header__actions">
          <a className="as-login-link" href="/">
            {copy.hero.loginCta}
          </a>
          <button className="as-button as-button--primary as-header__cta" type="button" onClick={openDemoForm}>
            {copy.hero.primaryCta}
          </button>
        </div>
        <button
          className="as-menu-button"
          type="button"
          aria-label="Toggle navigation"
          aria-expanded={menuOpen}
          onClick={() => setMenuOpen((current) => !current)}
        >
          <span />
          <span />
        </button>
      </header>

      <div className={`as-mobile-menu${menuOpen ? " is-open" : ""}`}>
        {copy.nav.map((item) => (
          <a key={item.href} href={item.href} onClick={() => setMenuOpen(false)}>
            {item.label}
          </a>
        ))}
        <a className="as-login-link" href="/" onClick={() => setMenuOpen(false)}>
          {copy.hero.loginCta}
        </a>
        <button className="as-button as-button--primary" type="button" onClick={openDemoForm}>
          {copy.hero.primaryCta}
        </button>
      </div>

      <section id="top" className="as-hero">
        <div className="as-hero__copy">
          <p className="as-kicker">{copy.hero.kicker}</p>
          <h1>{copy.hero.title}</h1>
          <p className="as-hero__lead">{copy.hero.lead}</p>
          <div className="as-hero__actions">
            <button className="as-button as-button--primary" type="button" onClick={openDemoForm}>
              {copy.hero.primaryCta}
            </button>
            <a className="as-button as-button--secondary" href="#how-it-works">
              {copy.hero.secondaryCta}
            </a>
          </div>
        </div>
        <HeroVisual copy={copy} />
      </section>

      <section className="as-trust" aria-label="Integration proof">
        <p>{copy.trustLabel}</p>
        <div className="as-logo-rail" aria-label="Supported systems">
          {copy.trustLogos.map((logo) => (
            <span key={logo}>{logo}</span>
          ))}
        </div>
      </section>

      <section id="product" className="as-section as-problem">
        <div className="as-section__head">
          <p className="as-kicker">{copy.problem.kicker}</p>
          <h2>{copy.problem.title}</h2>
        </div>
        <div className="as-fracture" aria-label="Fragmented support diagram">
          {copy.problem.columns.map((item, index) => (
            <div className="as-fracture__column" key={item.title}>
              <span className="as-fracture__node">{String(index + 1).padStart(2, "0")}</span>
              <h3>{item.title}</h3>
              <p>{item.copy}</p>
            </div>
          ))}
        </div>
        <p className="as-problem__bridge">{copy.problem.bridge}</p>
      </section>

      <section id="how-it-works" className="as-section as-flow">
        <div className="as-section__head as-section__head--split">
          <div>
            <p className="as-kicker">{copy.flow.kicker}</p>
            <h2>{copy.flow.title}</h2>
          </div>
          <p>{copy.flow.intro}</p>
        </div>
        <div className="as-flow__rail" role="tablist" aria-label="AutoService workflow">
          {copy.flow.steps.map((item, index) => (
            <button
              className={`as-flow__step${activeFlow === index ? " is-active" : ""}`}
              key={item.title}
              type="button"
              role="tab"
              aria-selected={activeFlow === index}
              onClick={() => setActiveFlow(index)}
            >
              <span>{item.step}</span>
              <strong>{item.title}</strong>
            </button>
          ))}
        </div>
        <div className="as-flow__detail" role="tabpanel">
          <strong>{copy.flow.steps[activeFlow].title}</strong>
          <p>{copy.flow.steps[activeFlow].copy}</p>
          <span>{copy.flow.steps[activeFlow].example}</span>
        </div>
      </section>

      <section id="channels" className="as-section as-workflows">
        <div className="as-section__head">
          <p className="as-kicker">{copy.workflows.kicker}</p>
          <h2>{copy.workflows.title}</h2>
        </div>
        <div className="as-journey-board" aria-label="After-sales workflow matrix">
          {copy.workflows.rows.map((workflow, workflowIndex) => (
            <article className="as-journey-card" key={workflow[0]}>
              <div className="as-journey-card__title">
                <span>{String(workflowIndex + 1).padStart(2, "0")}</span>
                <strong>{workflow[0]}</strong>
              </div>
              <div className="as-journey-card__steps">
                {workflow.slice(1).map((value, index) => (
                  <div className="as-journey-step" data-label={matrixHeaders[index]} key={`${workflow[0]}-${matrixHeaders[index]}`}>
                    <span>{matrixHeaders[index]}</span>
                    <strong>{value}</strong>
                  </div>
                ))}
              </div>
            </article>
          ))}
        </div>
      </section>

      <section id="integrations" className="as-section as-integrations">
        <div className="as-section__head as-section__head--split">
          <div>
            <p className="as-kicker">{copy.integrations.kicker}</p>
            <h2>{copy.integrations.title}</h2>
          </div>
          <p>{copy.integrations.intro}</p>
        </div>
        <div className="as-integration-map">
          <div className="as-integration-map__diagram" aria-hidden="false">
            <div className="as-integration-map__hub">
              <span>{copy.integrations.hubTitle}</span>
              <small>{copy.integrations.hubSubtitle}</small>
            </div>
            <div className="as-integration-map__spokes" role="tablist" aria-label="Integration categories">
              {copy.integrations.groups.map((group, index) => (
                <button
                  className={`as-spoke as-spoke--${index + 1}${activeIntegration === index ? " is-active" : ""}`}
                  key={group.name}
                  type="button"
                  role="tab"
                  aria-selected={activeIntegration === index}
                  onClick={() => setActiveIntegration(index)}
                >
                  <span>{String(index + 1).padStart(2, "0")}</span>
                  <strong>{group.name}</strong>
                </button>
              ))}
            </div>
          </div>
          <div className="as-integration-map__detail" role="tabpanel">
            <h3>{copy.integrations.groups[activeIntegration].name}</h3>
            <p>{copy.integrations.groups[activeIntegration].capability}</p>
            <div>
              {copy.integrations.groups[activeIntegration].items.map((item) => (
                <span key={item}>{item}</span>
              ))}
            </div>
          </div>
        </div>
      </section>

      <section className="as-section as-playbook">
        <div className="as-playbook__copy">
          <p className="as-kicker">{copy.playbook.kicker}</p>
          <h2>{copy.playbook.title}</h2>
          <p>{copy.playbook.copy}</p>
        </div>
        <div className="as-playbook__preview" aria-label="Return eligibility playbook preview">
          <div>
            <span>{copy.playbook.label}</span>
            {copy.playbook.rules.map((rule) => (
              <code key={rule}>{rule}</code>
            ))}
          </div>
          <div className="as-playbook__output">
            <span>{copy.playbook.outputLabel}</span>
            <strong>{copy.playbook.outputTitle}</strong>
            <p>{copy.playbook.outputCopy}</p>
          </div>
        </div>
      </section>

      <section className="as-section as-handoff">
        <div className="as-section__head">
          <p className="as-kicker">{copy.handoff.kicker}</p>
          <h2>{copy.handoff.title}</h2>
        </div>
        <div className="as-handoff__summary">
          <div className="as-handoff__reason">
            <span>{copy.handoff.reasonLabel}</span>
            <strong>{copy.handoff.reason}</strong>
          </div>
          <dl>
            {copy.handoff.fields.map((field) => (
              <div key={field.label}>
                <dt>{field.label}</dt>
                <dd>{field.value}</dd>
              </div>
            ))}
          </dl>
        </div>
      </section>

      <section id="ai-engine" className="as-section as-engine">
        <div className="as-section__head as-section__head--split">
          <div>
            <p className="as-kicker">{copy.engine.kicker}</p>
            <h2>{copy.engine.title}</h2>
          </div>
          <p>{copy.engine.intro}</p>
        </div>
        <div className="as-engine__layout">
          <div className="as-engine__stack" role="tablist" aria-label="AI Engine pipeline">
            {copy.engine.steps.map((step, index) => (
              <button
                className={`as-engine__step${activeEngine === index ? " is-active" : ""}`}
                key={step[0]}
                type="button"
                role="tab"
                aria-selected={activeEngine === index}
                onClick={() => setActiveEngine(index)}
              >
                <span>{String(index + 1).padStart(2, "0")}</span>
                {step[0]}
              </button>
            ))}
          </div>
          <div className="as-engine__detail" role="tabpanel">
            {copy.engine.labels.map((label, index) => (
              <div key={label}>
                <span>{label}</span>
                <p>{activeEngineStep[index + 1]}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section id="resources" className="as-section as-evidence">
        <div className="as-section__head">
          <p className="as-kicker">{copy.evidence.kicker}</p>
          <h2>{copy.evidence.title}</h2>
        </div>
        <div className="as-evidence__dashboard" aria-label="Monitoring dashboard preview">
          {copy.evidence.cards.map((card) => (
            <div key={card[0]}>
              <span>{card[0]}</span>
              <strong>{card[1]}</strong>
              <p>{card[2]}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="as-section as-security">
        <div>
          <p className="as-kicker">{copy.security.kicker}</p>
          <h2>{copy.security.title}</h2>
          <p>{copy.security.copy}</p>
        </div>
        <ul>
          {copy.security.items.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      </section>

      <section id="demo" className="as-final-cta">
        <p className="as-kicker">{copy.finalCta.kicker}</p>
        <h2>{copy.finalCta.title}</h2>
        <p>{copy.finalCta.copy}</p>
        <div>
          <button className="as-button as-button--primary" type="button" onClick={openDemoForm}>
            {copy.finalCta.primary}
          </button>
          <a className="as-button as-button--secondary" href={`mailto:sales@agentcici.com?subject=${encodeURIComponent(copy.expertSubject)}`}>
            {copy.finalCta.secondary}
          </a>
        </div>
      </section>

      {demoOpen && (
        <div className="as-modal-overlay" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && closeDemoForm()}>
          <section className="as-demo-modal" role="dialog" aria-modal="true" aria-labelledby="as-demo-title">
            <button className="as-demo-modal__close" type="button" aria-label="Close" onClick={closeDemoForm}>
              ×
            </button>
            <header className="as-demo-modal__head">
              <h2 id="as-demo-title">{copy.demoForm.title}</h2>
            </header>
            {demoSubmitted ? (
              <div className="as-demo-success" role="status">
                <div className="as-demo-success__mark" aria-hidden="true">
                  ✓
                </div>
                <h3>{copy.demoForm.successTitle}</h3>
                <p>{copy.demoForm.success}</p>
                <button className="as-button as-button--primary" type="button" onClick={closeDemoForm}>
                  {copy.demoForm.close}
                </button>
              </div>
            ) : (
              <form className="as-demo-form" onSubmit={submitDemoRequest}>
                <label>
                  <span>{copy.demoForm.company}</span>
                  <input
                    value={demoForm.companyName}
                    onChange={(event) => updateDemoField("companyName", event.target.value)}
                    placeholder={copy.demoForm.companyPlaceholder}
                    required
                    maxLength={128}
                  />
                </label>
                <label>
                  <span>{copy.demoForm.contact}</span>
                  <input
                    value={demoForm.contactName}
                    onChange={(event) => updateDemoField("contactName", event.target.value)}
                    placeholder={copy.demoForm.contactPlaceholder}
                    required
                    maxLength={64}
                  />
                </label>
                <label>
                  <span>{copy.demoForm.mobile}</span>
                  <input
                    value={demoForm.mobile}
                    onChange={(event) => updateDemoField("mobile", event.target.value)}
                    placeholder={copy.demoForm.mobilePlaceholder}
                    required
                    maxLength={64}
                    inputMode="tel"
                  />
                </label>
                <label>
                  <span>{copy.demoForm.email}</span>
                  <input
                    value={demoForm.email}
                    onChange={(event) => updateDemoField("email", event.target.value)}
                    placeholder={copy.demoForm.emailPlaceholder}
                    required
                    maxLength={128}
                    type="email"
                  />
                </label>
                <label>
                  <span>{copy.demoForm.role}</span>
                  <input
                    value={demoForm.roleTitle}
                    onChange={(event) => updateDemoField("roleTitle", event.target.value)}
                    placeholder={copy.demoForm.rolePlaceholder}
                    maxLength={128}
                  />
                </label>
                <label className="as-demo-form__wide">
                  <span>{copy.demoForm.scenario}</span>
                  <textarea
                    value={demoForm.scenario}
                    onChange={(event) => updateDemoField("scenario", event.target.value)}
                    placeholder={copy.demoForm.scenarioPlaceholder}
                    maxLength={2000}
                    rows={4}
                  />
                </label>
                {demoNotice && <p className="as-demo-form__notice">{demoNotice}</p>}
                <footer className="as-demo-form__actions">
                  <button className="as-button as-button--secondary" type="button" onClick={closeDemoForm} disabled={demoSubmitting}>
                    {copy.demoForm.cancel}
                  </button>
                  <button className="as-button as-button--primary" type="submit" disabled={demoSubmitting}>
                    {demoSubmitting ? copy.demoForm.submitting : copy.demoForm.submit}
                  </button>
                </footer>
              </form>
            )}
          </section>
        </div>
      )}
    </main>
  );
}

function HeroVisual({ copy }: { copy: (typeof AUTOSERVICE_COPY)[AutoServiceSite] }) {
  return (
    <div className="as-hero-visual" aria-label="AutoService product workflow visual">
      <svg className="as-route-svg" viewBox="0 0 720 520" aria-hidden="true">
        <path className="as-route as-route--one" d="M78 80 C 220 76, 226 188, 356 196 S 498 258, 640 242" />
        <path className="as-route as-route--two" d="M80 252 C 210 242, 260 332, 368 314 S 514 386, 638 356" />
        <path className="as-route as-route--three" d="M92 420 C 232 404, 254 282, 358 282 S 528 152, 638 156" />
      </svg>
      <div className="as-visual-panel as-visual-panel--channels">
        <span>{copy.visual.channelsLabel}</span>
        <div>
          {copy.channels.map((channel) => (
            <em key={channel}>{channel}</em>
          ))}
        </div>
      </div>
      <div className="as-visual-panel as-visual-panel--trace">
        <span>{copy.visual.traceLabel}</span>
        {copy.traceSteps.map((step) => (
          <div className="as-trace-row" key={step.label}>
            <small>{step.label}</small>
            <strong>{step.value}</strong>
          </div>
        ))}
      </div>
      <div className="as-visual-panel as-visual-panel--outcome">
        <span>{copy.visual.outcomeLabel}</span>
        <strong>{copy.visual.outcomeTitle}</strong>
        <p>{copy.visual.outcomeCopy}</p>
      </div>
    </div>
  );
}

function upsertMeta(name: string, content: string, attr: "name" | "property" = "name") {
  let el = document.head.querySelector<HTMLMetaElement>(`meta[${attr}="${name}"]`);
  if (!el) {
    el = document.createElement("meta");
    el.setAttribute(attr, name);
    document.head.appendChild(el);
  }
  el.content = content;
}
