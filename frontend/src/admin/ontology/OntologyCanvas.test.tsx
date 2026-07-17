import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import OntologyCanvas from "./OntologyCanvas";
import type { OntologyDocument } from "./ontologyTypes";

describe("OntologyCanvas", () => {
  it("renders a business node without stray text in its accessible button", () => {
    const document: OntologyDocument = {
      key: "delivery",
      name: "项目交付",
      description: null,
      concepts: [{
        key: "project",
        name: "项目",
        pluralName: "项目",
        description: null,
        conceptType: "ENTITY",
        displayPropertyKey: null,
        positionX: 80,
        positionY: 80,
        queryable: true,
        enabled: true,
        properties: [],
      }],
      relations: [],
      metrics: [],
      actions: [],
      dataSources: [],
      mappings: [],
    };

    const markup = renderToStaticMarkup(
      <OntologyCanvas
        document={document}
        selection={{ kind: "concept", key: "project" }}
        busy={false}
        onSelect={() => undefined}
        onChange={() => undefined}
        onCommit={() => undefined}
      />,
    );

    expect(markup).toContain("项目，业务对象，0 个业务属性");
    const nodeContent = markup.match(/class="ontology-node is-selected"[^>]*>([\s\S]*?)<\/button>/)?.[1];
    expect(nodeContent).toBeDefined();
    expect(nodeContent).not.toContain("&gt;");
  });
});
