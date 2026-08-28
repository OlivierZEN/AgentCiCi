import { describe, expect, it } from "vitest";
// @ts-expect-error Vitest executes this test in Node; production sources do not depend on Node types.
import { readFileSync } from "node:fs";

const embedSource = readFileSync(new URL("./SisiEmbedPage.tsx", import.meta.url), "utf8");
const embedCss = readFileSync(new URL("./sisi-embed.css", import.meta.url), "utf8");
const websiteSource = readFileSync(new URL("../suite/WebsiteSalesWidget.tsx", import.meta.url), "utf8");
const builderSource = readFileSync(new URL("../assistant/AgentBuilderShell.tsx", import.meta.url), "utf8");
const sdkSource = readFileSync(new URL("../../public/sdk/sisi@1.1.0.js", import.meta.url), "utf8");
const sdkAliasSource = readFileSync(new URL("../../public/sdk/sisi.js", import.meta.url), "utf8");

describe("web widget agent avatar contract", () => {
  it("renders the website session avatar in the header and assistant message surfaces", () => {
    expect(embedSource).toContain('session?.source === "website"');
    expect(embedSource).toContain("session?.agentAvatarBase64");
    expect(embedSource.match(/<SisiAgentAvatar/g)?.length).toBeGreaterThanOrEqual(3);
    expect(embedCss).toMatch(/\.sisi-seal img\s*\{[^}]*object-fit:\s*cover;/s);
  });

  it("passes the authoritative public avatar into the website launcher", () => {
    expect(websiteSource).toContain("launcherAvatar: config.agentAvatarBase64");
    expect(websiteSource).not.toContain('launcherInitial: "Ci"');
    expect(sdkSource).toContain("var launcherAvatar = avatar(config.launcherAvatar)");
    expect(sdkSource).toContain('avatarImage.addEventListener("error"');
    expect(() => new Function(sdkSource)).not.toThrow();
    expect(sdkAliasSource).toContain("var launcherAvatar = avatar(config.launcherAvatar)");
    expect(sdkAliasSource).toContain('avatarImage.addEventListener("error"');
    expect(() => new Function(sdkAliasSource)).not.toThrow();
  });

  it("keeps generated install code and Agent Builder preview on the same avatar source", () => {
    expect(builderSource).toContain('serviceOrigin + "/public/web-widgets/" + widgetKey');
    expect(builderSource).toContain("launcherAvatar: widgetConfig.agentAvatarBase64");
    expect(builderSource.match(/draft\.avatarBase64 \? <img/g)?.length).toBe(2);
  });
});
