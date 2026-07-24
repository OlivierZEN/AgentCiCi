import { clearAuthPayload, readAuthPayload, writeAuthPayload } from "../auth/authStorage";
import { LS_ADMIN_TOKEN, LS_ASSISTANT_TOKEN } from "../constants";

export type OrganizationSession = {
  token: string;
  companyId: string;
  companyName?: string;
  userId: string;
  memberId?: string;
  accountId?: string;
  roles: string[];
};

export function hasOrganizationAdminRole(roles: string[] | undefined): boolean {
  return Boolean(roles?.includes("OWNER") || roles?.includes("ORG_ADMIN"));
}

/**
 * The organization console is a view of the signed-in assistant session, never an independent login.
 */
export function readCurrentAdminSession(): OrganizationSession | null {
  const assistant = readAuthPayload<OrganizationSession>(LS_ASSISTANT_TOKEN);
  if (!assistant?.token || !hasOrganizationAdminRole(assistant.roles)) {
    clearAuthPayload(LS_ADMIN_TOKEN);
    return null;
  }
  writeAuthPayload(LS_ADMIN_TOKEN, assistant);
  return assistant;
}

export function beginOrganizationAdminSession(session: OrganizationSession): void {
  writeAuthPayload(LS_ADMIN_TOKEN, session);
}

export function endOrganizationAdminSession(): void {
  clearAuthPayload(LS_ADMIN_TOKEN);
}
