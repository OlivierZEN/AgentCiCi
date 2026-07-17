export function createAdminAuthScopeKey(orgId: string, token: string): string {
  return JSON.stringify([orgId.trim(), token.trim()]);
}

export function isAdminAsyncRequestCurrent(
  requestScope: string,
  requestId: number,
  currentScope: string,
  currentRequestId: number,
): boolean {
  return requestScope === currentScope && requestId === currentRequestId;
}
