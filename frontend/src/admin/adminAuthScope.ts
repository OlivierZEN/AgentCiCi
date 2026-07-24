export function createAdminAuthScopeKey(companyId: string, token: string): string {
  return JSON.stringify([companyId.trim(), token.trim()]);
}

export function isAdminAsyncRequestCurrent(
  requestScope: string,
  requestId: number,
  currentScope: string,
  currentRequestId: number,
): boolean {
  return requestScope === currentScope && requestId === currentRequestId;
}
