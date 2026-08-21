import { describe, expect, it } from "vitest";
import deploymentGuideMarkdown from "../../../public/agent-docs/operations/deployment-installation.md?raw";
import {
  ACCEPTANCE_COMMANDS_EXAMPLE,
  AGENTCICI_INSTALL_EXAMPLE,
  ARTIFACT_COORDINATES_EXAMPLE,
  DEPLOYMENT_INSTALLATION_AGENT_GUIDE_PATH,
  DEPLOYMENT_INSTALLATION_SECTIONS,
  KEYCLOAK_INSTALL_EXAMPLE,
  SEMATTICE_INSTALL_EXAMPLE,
} from "./PlatformDeploymentInstallationPage";

describe("platform deployment installation guide", () => {
  it("covers the installation path in a stable order", () => {
    expect(DEPLOYMENT_INSTALLATION_SECTIONS.map((section) => section.id)).toEqual([
      "overview",
      "artifacts",
      "prerequisites",
      "keycloak",
      "agentcici",
      "semattice",
      "integration",
      "acceptance",
    ]);
  });

  it("uses governed artifact sources without inventing a Semattice image", () => {
    expect(ARTIFACT_COORDINATES_EXAMPLE).toContain("cici-backend:${AGENTCICI_VERSION}");
    expect(ARTIFACT_COORDINATES_EXAMPLE).toContain("cici-frontend:${AGENTCICI_VERSION}");
    expect(ARTIFACT_COORDINATES_EXAMPLE).toContain("quay.io/keycloak/keycloak:<approved-version>");
    expect(ARTIFACT_COORDINATES_EXAMPLE).toContain("Semattice 当前受管交付不是 OCI 镜像");
    expect(ARTIFACT_COORDINATES_EXAMPLE).not.toMatch(/semattice:\$\{|semattice:<|\/semattice:/);
  });

  it("keeps commands templated and secret-safe", () => {
    const commands = [
      KEYCLOAK_INSTALL_EXAMPLE,
      AGENTCICI_INSTALL_EXAMPLE,
      SEMATTICE_INSTALL_EXAMPLE,
      ACCEPTANCE_COMMANDS_EXAMPLE,
    ].join("\n");
    expect(commands).toContain("<root-only-secret-file>");
    expect(commands).not.toMatch(/BEGIN (?:RSA |OPENSSH )?PRIVATE KEY|password\s*=\s*[^<$\s]+/i);
    expect(commands).not.toMatch(/https?:\/\/(?:[^\s/]+\.)?agentcici\.com|cici\.localhost|127\.0\.0\.1|\b(?:\d{1,3}\.){3}\d{1,3}\b/);
  });

  it("publishes the same eight sections for agents", () => {
    expect(DEPLOYMENT_INSTALLATION_AGENT_GUIDE_PATH).toBe("/agent-docs/operations/deployment-installation.md");
    expect(deploymentGuideMarkdown).toContain("document_id: agentcici.deployment-installation.v1");
    expect(deploymentGuideMarkdown).toContain("canonical_ui_path: /platform/operations/deployment-installation");
    expect(deploymentGuideMarkdown).toContain("Semattice 当前受管生产交付不是 OCI 镜像");
    for (const section of DEPLOYMENT_INSTALLATION_SECTIONS) {
      expect(deploymentGuideMarkdown).toContain(`## ${section.number} ${section.label}`);
    }
  });

  it("keeps the public guide free of real environment coordinates and reusable secrets", () => {
    expect(deploymentGuideMarkdown).not.toMatch(/https?:\/\/(?:[^\s/]+\.)?agentcici\.com|cici\.localhost|127\.0\.0\.1|\b(?:\d{1,3}\.){3}\d{1,3}\b/);
    expect(deploymentGuideMarkdown).not.toMatch(/BEGIN (?:RSA |OPENSSH )?PRIVATE KEY|client_secret\s*[:=]\s*[^<$\s]+/i);
  });
});
