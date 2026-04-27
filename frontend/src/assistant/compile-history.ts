export type CompileNoticePayload = {
  changed?: boolean;
  compileMessage?: string;
  draftVersionNo?: number | null;
};

export function buildCompileNotice(payload: CompileNoticePayload): string {
  if (payload.changed === false) {
    return payload.compileMessage ?? "未检测到变化，本次不新增版本。";
  }
  if (payload.draftVersionNo != null) {
    return `编译完成并已写入草稿版本 v${payload.draftVersionNo}。`;
  }
  return payload.compileMessage ?? "编译完成。当前结果来自后端 compile 接口，已经包含流程图预览、代码、manifest、依赖和风险提示。";
}

export function keepRecentVersionHistory<T>(items: T[], limit = 10): T[] {
  const safeLimit = Number.isFinite(limit) && limit > 0 ? Math.floor(limit) : 10;
  return items.slice(0, safeLimit);
}

export function isCompileRequired(
  currentCompileDigest: string,
  lastSuccessfulBackendCompileDigest: string | null,
  loadedAgentBaselineDigest: string | null,
): boolean {
  if (lastSuccessfulBackendCompileDigest != null) {
    return currentCompileDigest !== lastSuccessfulBackendCompileDigest;
  }
  if (loadedAgentBaselineDigest != null) {
    return currentCompileDigest !== loadedAgentBaselineDigest;
  }
  return true;
}
