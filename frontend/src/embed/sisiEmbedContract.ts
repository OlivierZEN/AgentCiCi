export function exactConfirmation(content: string) {
  const patterns = [
    /(?:请|需要你)(?:明确)?回复[“\"]([^”\"]{2,80})[”\"](?:后|以确认|进行确认)?/,
    /(?:确认口令|确认短语)[：:]\s*[“\"]([^”\"]{2,80})[”\"]/
  ];
  for (const pattern of patterns) {
    const match = content.match(pattern);
    if (match?.[1]) return match[1].trim();
  }
  return "";
}
