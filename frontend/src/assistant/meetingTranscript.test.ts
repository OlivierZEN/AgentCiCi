import { describe, expect, it } from "vitest";
import { appendMeetingTranscriptSegment, speakerDisplayName, type MeetingTranscriptSegmentLike } from "./meetingTranscript";

const segment = (speakerId: string, text: string): MeetingTranscriptSegmentLike => ({
  id: `s-${speakerId}-${text}`,
  speakerId,
  speakerName: speakerDisplayName(speakerId),
  text,
  time: "10:00",
});

describe("meeting transcript helpers", () => {
  it("merges continuous final segments from the same speaker into one paragraph", () => {
    const merged = appendMeetingTranscriptSegment(
      [segment("1", "2025～2026格局")],
      segment("1", "一晶元代工先进制程核心客户"),
    );

    expect(merged).toHaveLength(1);
    expect(merged[0].text).toBe("2025～2026格局一晶元代工先进制程核心客户");
  });

  it("starts a new paragraph when the speaker changes", () => {
    const merged = appendMeetingTranscriptSegment([segment("1", "台积电")], segment("2", "英伟达"));

    expect(merged).toHaveLength(2);
    expect(merged.map((item) => item.speakerName)).toEqual(["发言人 1", "发言人 2"]);
  });

  it("keeps readable spacing for English continuations", () => {
    const merged = appendMeetingTranscriptSegment(
      [segment("1", "I'm not sure who you mean")],
      segment("1", "And don't worry about your oral English."),
    );

    expect(merged).toHaveLength(1);
    expect(merged[0].text).toBe("I'm not sure who you mean And don't worry about your oral English.");
  });
});
