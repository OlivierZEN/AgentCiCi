import { describe, expect, it } from "vitest";
import integrationGuideMarkdown from "../../../public/agent-docs/internal-applications/integration-guide.md?raw";
import {
  APPLICATION_INTEGRATION_AGENT_GUIDE_PATH,
  APPLICATION_INTEGRATION_GUIDE_SECTIONS,
  HMAC_CANONICAL_EXAMPLE,
  PROVIDER_LIFECYCLE_REQUEST_EXAMPLE,
  PROVIDER_RESPONSE_EXAMPLE,
} from "./PlatformApplicationIntegrationGuidePage";

describe("internal application online integration guide", () => {
  it("covers the governed onboarding path in an actionable order", () => {
    expect(APPLICATION_INTEGRATION_GUIDE_SECTIONS.map((section) => section.id)).toEqual([
      "overview",
      "prerequisites",
      "registration",
      "provider-contract",
      "authentication",
      "connection",
      "version",
      "dependencies",
      "publication",
      "activation",
      "operations",
      "troubleshooting",
    ]);
  });

  it("documents the actual lifecycle request and accepted response", () => {
    const request = JSON.parse(PROVIDER_LIFECYCLE_REQUEST_EXAMPLE) as Record<string, unknown>;
    expect(request).toMatchObject({
      operationType: "ACTIVATE",
      appCode: "sales-workbench",
      applicationVersion: "1.0.0",
      contractVersion: "v1",
      stepCode: "tenant-bootstrap",
      capability: "tenant.activate",
    });
    expect(request.dependencies).toEqual([
      expect.objectContaining({ appCode: "semattice", dependencyType: "REQUIRED_RUNTIME" }),
    ]);
    expect(PROVIDER_RESPONSE_EXAMPLE).toContain('"status": "ACTIVE"');
  });

  it("keeps the HMAC canonical order aligned with the provider signer", () => {
    expect(HMAC_CANONICAL_EXAMPLE.split("\n").slice(0, 3)).toEqual([
      "agentcici",
      "POST",
      "/internal/tenant-lifecycle/v1/activations",
    ]);
    expect(HMAC_CANONICAL_EXAMPLE).toContain("<SHA256_HEX_OF_REQUEST_BODY>");
  });

  it("uses only a reserved example domain in developer-facing snippets", () => {
    expect(PROVIDER_LIFECYCLE_REQUEST_EXAMPLE).not.toMatch(/agentcici\.com|cici\.localhost|127\.0\.0\.1/);
  });

  it("publishes a no-JavaScript Markdown guide for agents with the same governed contract", () => {
    expect(APPLICATION_INTEGRATION_AGENT_GUIDE_PATH).toBe("/agent-docs/internal-applications/integration-guide.md");
    expect(integrationGuideMarkdown).toContain("document_id: agentcici.internal-application-integration.v1");
    expect(integrationGuideMarkdown).toContain("Idempotency-Key: <operationId>:<stepCode>");
    expect(integrationGuideMarkdown).toContain("app.platform.provider-secrets.<secret_ref>");
    for (const section of APPLICATION_INTEGRATION_GUIDE_SECTIONS) {
      expect(integrationGuideMarkdown).toContain(`## ${section.number} ${section.label}`);
    }
    expect(integrationGuideMarkdown).not.toMatch(/https?:\/\/(?:[^\s/]+\.)?agentcici\.com|cici\.localhost|127\.0\.0\.1/);
  });
});
