import type { UserRole } from "./types";

/** Builds a link to the role-management settings page, pre-filling which role(s)
 * unlock the action the user just tried, and why. */
export function settingsRoleUrl(roles: UserRole | UserRole[], reason: string): string {
  const roleList = Array.isArray(roles) ? roles : [roles];
  const params = new URLSearchParams({ needRole: roleList.join(","), reason });
  return `/settings/security?${params.toString()}`;
}

export function hasAnyRole(userRoles: UserRole[] | undefined, roles: UserRole[]): boolean {
  return !!userRoles?.some((r) => roles.includes(r));
}
