import { useEffect, useMemo, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import "./help-center.css";
import { featuredSlugs, helpCategories, helpDocs, roleEntrypoints, type HelpDoc } from "./helpContent";

const helpHostnames = new Set(["help.agentcici.com"]);

function isHelpHost() {
  return typeof window !== "undefined" && helpHostnames.has(window.location.hostname);
}

function toDocPath(slug: string) {
  return isHelpHost() ? `/${slug}` : `/help/${slug}`;
}

function normalizeSlug(pathname: string) {
  const trimmed = pathname.replace(/^\/+|\/+$/g, "");
  if (!trimmed || trimmed === "help") return "";
  return trimmed.startsWith("help/") ? trimmed.slice(5) : trimmed;
}

function getDoc(slug: string) {
  return helpDocs.find((doc) => doc.slug === slug);
}

function getCategoryTitle(categoryId: string) {
  return helpCategories.find((category) => category.id === categoryId)?.title ?? categoryId;
}

function getRelatedDocs(doc: HelpDoc) {
  return doc.related.map(getDoc).filter(Boolean) as HelpDoc[];
}

function highlightMatch(text: string, query: string) {
  if (!query.trim()) return text;
  const lower = text.toLowerCase();
  const needle = query.trim().toLowerCase();
  const index = lower.indexOf(needle);
  if (index < 0) return text;
  return (
    <>
      {text.slice(0, index)}
      <mark>{text.slice(index, index + needle.length)}</mark>
      {text.slice(index + needle.length)}
    </>
  );
}

export default function HelpCenterApp() {
  const location = useLocation();
  const navigate = useNavigate();
  const [query, setQuery] = useState("");
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const currentSlug = normalizeSlug(location.pathname);
  const currentDoc = currentSlug ? getDoc(currentSlug) : undefined;
  const activeDoc = currentDoc ?? helpDocs[0];
  const isHome = !currentSlug;

  useEffect(() => {
    setMobileNavOpen(false);
    window.scrollTo({ top: 0, left: 0 });
  }, [location.pathname]);

  const searchResults = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    if (!normalized) return [];
    return helpDocs
      .map((doc) => {
        const haystack = [doc.title, doc.summary, doc.entry, doc.category, ...doc.aliases, ...doc.sections.map((section) => `${section.title} ${section.body ?? ""} ${(section.bullets ?? []).join(" ")} ${(section.steps ?? []).join(" ")}`)]
          .join(" ")
          .toLowerCase();
        const score = doc.title.toLowerCase().includes(normalized) ? 3 : haystack.includes(normalized) ? 1 : 0;
        return { doc, score };
      })
      .filter((item) => item.score > 0)
      .sort((a, b) => b.score - a.score || a.doc.title.localeCompare(b.doc.title, "zh-Hans-CN"))
      .slice(0, 8)
      .map((item) => item.doc);
  }, [query]);

  const docsByCategory = useMemo(
    () =>
      helpCategories.map((category) => ({
        ...category,
        docs: helpDocs.filter((doc) => doc.category === category.id),
      })),
    [],
  );

  const currentIndex = helpDocs.findIndex((doc) => doc.slug === activeDoc.slug);
  const previousDoc = currentIndex > 0 ? helpDocs[currentIndex - 1] : undefined;
  const nextDoc = currentIndex >= 0 && currentIndex < helpDocs.length - 1 ? helpDocs[currentIndex + 1] : undefined;

  function openFirstSearchResult() {
    if (searchResults[0]) {
      navigate(toDocPath(searchResults[0].slug));
    }
  }

  return (
    <div className="help-shell">
      <header className="help-topbar">
        <Link className="help-brand" to={isHelpHost() ? "/" : "/help"}>
          <span className="help-brand__mark">Ci</span>
          <span>
            <strong>AgentCiCi Help Center</strong>
            <small>产品帮助中心</small>
          </span>
        </Link>
        <div className="help-search" role="search">
          <label htmlFor="help-search-input">搜索帮助文档</label>
          <input
            id="help-search-input"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter") openFirstSearchResult();
            }}
            placeholder="搜索知识库、API、401、模型配置..."
          />
          {query.trim() ? (
            <div className="help-search__results" aria-label="搜索结果">
              {searchResults.length ? (
                searchResults.map((doc) => (
                  <Link key={doc.slug} to={toDocPath(doc.slug)}>
                    <span>{highlightMatch(doc.title, query)}</span>
                    <small>{doc.summary}</small>
                  </Link>
                ))
              ) : (
                <p>没有找到匹配文档。换一个产品入口、错误码或功能名试试。</p>
              )}
            </div>
          ) : null}
        </div>
        <button className="help-mobile-nav" type="button" onClick={() => setMobileNavOpen((open) => !open)} aria-expanded={mobileNavOpen} aria-controls="help-navigation">
          目录
        </button>
      </header>

      <div className="help-layout">
        <aside className={`help-sidebar ${mobileNavOpen ? "is-open" : ""}`} id="help-navigation">
          <nav aria-label="帮助中心目录">
            {docsByCategory.map((category) => (
              <section key={category.id}>
                <h2>{category.title}</h2>
                <p>{category.summary}</p>
                {category.docs.map((doc) => (
                  <Link key={doc.slug} className={doc.slug === activeDoc.slug && !isHome ? "is-active" : ""} to={toDocPath(doc.slug)}>
                    {doc.title}
                  </Link>
                ))}
              </section>
            ))}
          </nav>
        </aside>

        <main className="help-main">
          {isHome ? <HelpHome /> : currentDoc ? <HelpArticle doc={activeDoc} previousDoc={previousDoc} nextDoc={nextDoc} /> : <NotFound requestedSlug={currentSlug} />}
        </main>
      </div>
    </div>
  );
}

function HelpHome() {
  const featuredDocs = featuredSlugs.map(getDoc).filter(Boolean) as HelpDoc[];

  return (
    <div className="help-home">
      <section className="help-home__intro">
        <span className="help-eyebrow">AgentCiCi 产品文档</span>
        <h1>按任务找到配置、调用和排障答案。</h1>
        <p>这里服务员工用户、组织管理员、平台运营人员和开发者。内容按真实入口和操作流程组织，不复刻研发规格。</p>
      </section>

      <section className="help-quick" aria-labelledby="help-quick-title">
        <div className="help-section-heading">
          <span>5 分钟上手</span>
          <h2 id="help-quick-title">先确认入口，再完成第一件事</h2>
        </div>
        <div className="help-quick__grid">
          {[
            ["登录 AgentCiCi", "确认你使用的是员工端、管理端还是平台端。", "getting-started/accounts-roles"],
            ["选择知识库并提问", "让已发布文档参与员工工作台回答。", "user-workbench/knowledge-selection"],
            ["配置模型供应商", "为组织对话和智能体运行选择可用模型。", "admin/models/providers"],
            ["创建 Open API Key", "让外部系统安全调用已发布智能体。", "openapi/api-keys"],
          ].map(([title, summary, slug]) => (
            <Link key={slug} to={toDocPath(slug)}>
              <strong>{title}</strong>
              <span>{summary}</span>
            </Link>
          ))}
        </div>
      </section>

      <section className="help-roles" aria-labelledby="help-roles-title">
        <div className="help-section-heading">
          <span>按角色浏览</span>
          <h2 id="help-roles-title">每个入口只展示该角色最常用的任务</h2>
        </div>
        <div className="help-roles__list">
          {roleEntrypoints.map((entry) => (
            <article key={entry.role}>
              <h3>{entry.role}</h3>
              <p>{entry.summary}</p>
              <div>
                {entry.slugs.map((slug) => {
                  const doc = getDoc(slug);
                  return doc ? (
                    <Link key={slug} to={toDocPath(slug)}>
                      {doc.title}
                    </Link>
                  ) : null;
                })}
              </div>
            </article>
          ))}
        </div>
      </section>

      <section className="help-featured" aria-labelledby="help-featured-title">
        <div className="help-section-heading">
          <span>常用任务</span>
          <h2 id="help-featured-title">高频配置和排障入口</h2>
        </div>
        <div className="help-featured__list">
          {featuredDocs.map((doc) => (
            <Link key={doc.slug} to={toDocPath(doc.slug)}>
              <span>{getCategoryTitle(doc.category)}</span>
              <strong>{doc.title}</strong>
              <small>{doc.summary}</small>
            </Link>
          ))}
        </div>
      </section>
    </div>
  );
}

function HelpArticle({ doc, previousDoc, nextDoc }: { doc: HelpDoc; previousDoc?: HelpDoc; nextDoc?: HelpDoc }) {
  const relatedDocs = getRelatedDocs(doc);

  return (
    <article className="help-article">
      <div className="help-breadcrumb">
        <Link to={isHelpHost() ? "/" : "/help"}>帮助中心</Link>
        <span>/</span>
        <span>{getCategoryTitle(doc.category)}</span>
      </div>

      <header className="help-article__header">
        <span className="help-eyebrow">{doc.role.join(" / ")}</span>
        <h1>{doc.title}</h1>
        <p>{doc.summary}</p>
        <dl>
          <div>
            <dt>适用入口</dt>
            <dd>{doc.entry}</dd>
          </div>
          <div>
            <dt>最后更新</dt>
            <dd>{doc.updatedAt}</dd>
          </div>
        </dl>
      </header>

      <div className="help-article__body">
        <aside className="help-toc" aria-label="本文目录">
          <strong>本文目录</strong>
          {doc.sections.map((section) => (
            <a key={section.title} href={`#${encodeURIComponent(section.title)}`}>
              {section.title}
            </a>
          ))}
        </aside>

        <div className="help-prose">
          <section id="%E5%89%8D%E7%BD%AE%E6%9D%A1%E4%BB%B6">
            <h2>前置条件</h2>
            <ul>
              {doc.prerequisites.map((item) => (
                <li key={item}>{formatInlineCode(item)}</li>
              ))}
            </ul>
          </section>

          {doc.sections.map((section) => (
            <section key={section.title} id={encodeURIComponent(section.title)}>
              <h2>{section.title}</h2>
              {section.body ? <p>{formatInlineCode(section.body)}</p> : null}
              {section.bullets ? (
                <ul>
                  {section.bullets.map((item) => (
                    <li key={item}>{formatInlineCode(item)}</li>
                  ))}
                </ul>
              ) : null}
              {section.steps ? (
                <ol>
                  {section.steps.map((item) => (
                    <li key={item}>{formatInlineCode(item)}</li>
                  ))}
                </ol>
              ) : null}
              {section.code ? <pre>{section.code}</pre> : null}
            </section>
          ))}

          {relatedDocs.length ? (
            <section>
              <h2>相关文档</h2>
              <div className="help-related">
                {relatedDocs.map((related) => (
                  <Link key={related.slug} to={toDocPath(related.slug)}>
                    <strong>{related.title}</strong>
                    <span>{related.summary}</span>
                  </Link>
                ))}
              </div>
            </section>
          ) : null}

          <nav className="help-pagination" aria-label="上一篇和下一篇">
            {previousDoc ? (
              <Link to={toDocPath(previousDoc.slug)}>
                <span>上一篇</span>
                <strong>{previousDoc.title}</strong>
              </Link>
            ) : (
              <span />
            )}
            {nextDoc ? (
              <Link to={toDocPath(nextDoc.slug)}>
                <span>下一篇</span>
                <strong>{nextDoc.title}</strong>
              </Link>
            ) : null}
          </nav>
        </div>
      </div>
    </article>
  );
}

function NotFound({ requestedSlug }: { requestedSlug: string }) {
  return (
    <section className="help-not-found">
      <span className="help-eyebrow">未找到文档</span>
      <h1>没有找到这个帮助页面</h1>
      <p>请求路径 `{requestedSlug}` 还没有对应文档。你可以回到首页使用搜索，或从左侧目录进入现有内容。</p>
      <Link to={isHelpHost() ? "/" : "/help"}>回到帮助中心首页</Link>
    </section>
  );
}

function formatInlineCode(text: string) {
  const parts = text.split(/(`[^`]+`)/g);
  return (
    <>
      {parts.map((part, index) =>
        part.startsWith("`") && part.endsWith("`") ? (
          <code key={`${part}-${index}`}>{part.slice(1, -1)}</code>
        ) : (
          <span key={`${part}-${index}`}>{part}</span>
        ),
      )}
    </>
  );
}
