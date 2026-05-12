import { describe, expect, it } from "vitest";
import { isMeetingMinutesStartCommand } from "./meetingMinutesCommand";

describe("meetingMinutesCommand", () => {
  it("recognizes concise and spoken meeting minutes start commands", () => {
    expect(isMeetingMinutesStartCommand("开始会议纪要")).toBe(true);
    expect(isMeetingMinutesStartCommand("开始进行会议纪要")).toBe(true);
    expect(isMeetingMinutesStartCommand("帮我开始做会议记录吧")).toBe(true);
    expect(isMeetingMinutesStartCommand("开启实时会议听记。")).toBe(true);
  });

  it("does not treat explanatory questions as start commands", () => {
    expect(isMeetingMinutesStartCommand("如何开始会议纪要")).toBe(false);
    expect(isMeetingMinutesStartCommand("会议纪要怎么生成")).toBe(false);
  });
});
