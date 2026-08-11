import { describe, expect, it } from "vitest";
import { compactAppVersionLines } from "./appVersion";

describe("本地应用版本显示", () => {
  it("将开发预发布版本拆为可在窄栏完整显示的两行", () => {
    expect(compactAppVersionLines("2.8.62-dev.1")).toEqual({
      baseVersion: "2.8.62",
      qualifier: "dev.1",
    });
  });

  it("保留无预发布后缀的完整版本", () => {
    expect(compactAppVersionLines("2.8.62")).toEqual({
      baseVersion: "2.8.62",
      qualifier: "",
    });
  });
});
