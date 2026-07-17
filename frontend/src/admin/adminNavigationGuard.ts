export type AdminNavigationGuard = {
  id: number;
  message: string;
};

export function confirmAdminNavigation(
  guard: AdminNavigationGuard | null,
  confirm: (message: string) => boolean,
): boolean {
  return guard === null || confirm(guard.message);
}

export function shouldBlockAdminRouteNavigation(
  active: boolean,
  currentPathname: string,
  targetPathname: string,
): boolean {
  return active
    && currentPathname !== targetPathname;
}
