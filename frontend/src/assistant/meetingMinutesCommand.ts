const MEETING_MINUTES_ACTION_PATTERN =
  /^(?:请|帮我|麻烦你?|思思)?(?:开始|开启|启动|打开|进入|发起)(?:进行|做|写|整理|生成|一场|一个|一下|下)?(?:实时)?(?:会议纪要|会议记录|会议听记|会议转写)$/;

export function isMeetingMinutesStartCommand(value: string): boolean {
  const normalized = value
    .trim()
    .replace(/[，。！？、；：,.!?;:\s]/g, "")
    .replace(/[吧啦了呢呀啊哦～~]+$/g, "");

  return MEETING_MINUTES_ACTION_PATTERN.test(normalized);
}
