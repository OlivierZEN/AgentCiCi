import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import OntologyMappingPanel from "./OntologyMappingPanel";
import type { OntologyEditableMapping } from "./ontologyModel";
import type { OntologyDocument } from "./ontologyTypes";

describe("OntologyMappingPanel", () => {
  it("renders the page-owned mapping draft after a tab remount", () => {
    const document: OntologyDocument = {
      key: "delivery",
      name: "交付",
      description: null,
      concepts: [{
        key: "project",
        name: "项目",
        pluralName: "项目",
        description: null,
        conceptType: "ENTITY",
        displayPropertyKey: "name",
        positionX: 80,
        positionY: 80,
        queryable: true,
        enabled: true,
        properties: [{ key: "name", name: "项目名称", description: null, dataType: "TEXT", required: true, multiple: false, sensitive: false, queryable: true, enumValues: [] }],
      }],
      relations: [],
      metrics: [],
      actions: [],
      dataSources: [],
      mappings: [],
    };
    const mappingRows: OntologyEditableMapping[] = [{
      clientKey: "mapping-9",
      targetType: "PROPERTY",
      targetKey: "project.name",
      dataSourceId: 4,
      physicalObjectKey: "projects",
      physicalFieldKey: "project_name",
      relationTargetFieldKey: null,
      transform: null,
      confidence: 1,
      validationStatus: "STALE",
    }];
    const props = {
      document,
      sources: [{ id: 4, key: "delivery", name: "交付系统", type: "CONNECTOR" as const, adapterKey: null, status: "ACTIVE", lastValidatedAt: null, sample: { objectCount: 1, rowCount: 1, fieldValueCount: 1 } }],
      catalog: {
        revision: 3,
        objects: [{
          id: 3,
          dataSourceId: 4,
          key: "projects",
          name: "项目",
          type: "OBJECT",
          discoveredAt: "2026-07-17T01:00:00Z",
          fields: [{ id: 8, key: "project_name", name: "项目名称", dataType: "TEXT", nullable: false, multiple: false, discoveredAt: "2026-07-17T01:00:00Z" }],
        }],
      },
      mappingRows,
      mappingDirty: true,
      loading: false,
      busy: false,
      error: "",
      onMappingRowsChange: () => undefined,
      onReload: () => undefined,
      onCreateSource: () => undefined,
      onDiscoverObjects: () => undefined,
      onDiscoverFields: () => undefined,
      onSaveMappings: () => undefined,
      onValidateMappings: () => undefined,
    };

    const firstMount = renderToStaticMarkup(<OntologyMappingPanel {...props} />);
    const remount = renderToStaticMarkup(<OntologyMappingPanel {...props} />);

    expect(firstMount).toContain('value="project_name" selected=""');
    expect(firstMount).toContain("需重新验证");
    expect(remount).toBe(firstMount);
    expect(firstMount).not.toContain("连接器标识");
    expect(firstMount).not.toContain("ACTIVE");
    expect(firstMount).toMatch(/<button type="submit"[^>]*disabled=""[^>]*>[\s\S]*?创建数据来源<\/button>/);
    expect(firstMount).toMatch(/<button type="button" class="ontology-text-action" disabled=""[^>]*>发现数据对象<\/button>/);
    expect(firstMount).toMatch(/<button type="button" class="ontology-text-action" disabled=""[^>]*>发现字段<\/button>/);
    expect(firstMount.match(/<button[^>]*>保存映射<\/button>/)?.[0]).not.toContain("disabled");
  });
});
