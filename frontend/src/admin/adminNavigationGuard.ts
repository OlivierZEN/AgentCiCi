export type AdminNavigationGuard = {
  id: number;
  message: string;
};

export type AdminNavigationClick = {
  button: number;
  metaKey: boolean;
  ctrlKey: boolean;
  shiftKey: boolean;
  altKey: boolean;
};

export function confirmAdminNavigation(
  guard: AdminNavigationGuard | null,
  confirm: (message: string) => boolean,
): boolean {
  return guard === null || confirm(guard.message);
}

export function shouldGuardAdminNavigationClick(
  click: AdminNavigationClick,
  currentPathname: string,
  targetPathname: string,
): boolean {
  return currentPathname !== targetPathname
    && click.button === 0
    && !click.metaKey
    && !click.ctrlKey
    && !click.shiftKey
    && !click.altKey;
}
