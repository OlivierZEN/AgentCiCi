import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import OntologyProposalPanel from "./OntologyProposalPanel";

describe("OntologyProposalPanel", () => {
  it("shows a locked composer without pretending an AI request is still running", () => {
    const markup = renderToStaticMarkup(
      <OntologyProposalPanel
        sources={[]}
        catalog={null}
        proposals={[]}
        activeProposal={null}
        loading={false}
        busy={false}
        locked
        error="提案已应用，重新加载失败；请重新加载草稿，勿重复应用。"
        generateDisabledReason="草稿修订已变化，请先重新加载。"
        applyDisabledReason="草稿修订已变化，请先重新加载。"
        onReload={() => undefined}
        onSelect={() => undefined}
        onGenerate={() => undefined}
        onApply={() => undefined}
        onContinueManually={() => undefined}
      />,
    );

    expect(markup).toContain('<textarea rows="4" disabled=""');
    expect(markup).toContain("AI 操作需要处理");
    expect(markup).toContain("生成可审阅提案");
    expect(markup).not.toContain("正在生成");
  });
});
