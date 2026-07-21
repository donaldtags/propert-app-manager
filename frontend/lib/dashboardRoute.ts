import type { UserRole } from "./types";

const DASHBOARD_PRIORITY: { role: UserRole; path: string }[] = [
  { role: "LANDLORD", path: "/landlord" },
  { role: "AGENT", path: "/landlord" },
  { role: "DEVELOPER", path: "/landlord" },
  { role: "PRIVATE", path: "/landlord" },
  { role: "INVESTOR", path: "/investments" },
  { role: "TENANT", path: "/tenant" },
  { role: "ADMIN", path: "/admin" },
];

export function dashboardPathFor(roles: UserRole[] | undefined): string {
  if (!roles) return "/";
  for (const { role, path } of DASHBOARD_PRIORITY) {
    if (roles.includes(role)) return path;
  }
  return "/";
}
