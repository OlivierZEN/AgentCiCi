export type MeetingTranscriptSegmentLike = {
  id: string;
  speakerId: string;
  speakerName: string;
  text: string;
  time: string;
  startMs?: number;
  endMs?: number;
};

export function speakerDisplayName(speakerId: string): string {
  const id = speakerId.trim();
  if (!id) {
    return "发言人 1";
  }
  if (/^\d+$/.test(id)) {
    return `发言人 ${id === "0" ? "1" : id}`;
  }
  return `发言人 ${id}`;
}

export function appendMeetingTranscriptSegment<T extends MeetingTranscriptSegmentLike>(segments: T[], next: T): T[] {
  const trimmedText = next.text.trim();
  if (!trimmedText) {
    return segments;
  }
  const normalizedNext = { ...next, text: trimmedText };
  const previous = segments.at(-1);
  if (!previous || previous.speakerId !== normalizedNext.speakerId) {
    return [...segments, normalizedNext];
  }
  return [
    ...segments.slice(0, -1),
    {
      ...previous,
      speakerName: normalizedNext.speakerName || previous.speakerName,
      text: joinTranscriptText(previous.text, normalizedNext.text),
      endMs: normalizedNext.endMs ?? previous.endMs,
    },
  ];
}

function joinTranscriptText(left: string, right: string): string {
  const a = left.trim();
  const b = right.trim();
  if (!a) return b;
  if (!b) return a;
  if (shouldJoinWithoutSpace(a, b)) {
    return `${a}${b}`;
  }
  return `${a} ${b}`;
}

function shouldJoinWithoutSpace(left: string, right: string): boolean {
  if (/^[，。！？；：、,.!?;:%）】》」』”’]/.test(right)) {
    return true;
  }
  if (/[（【《「『“‘]$/.test(left)) {
    return true;
  }
  const leftTail = left.slice(-1);
  const rightHead = right.slice(0, 1);
  return /[\u4e00-\u9fff]/.test(leftTail) || /[\u4e00-\u9fff]/.test(rightHead);
}
