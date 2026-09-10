import type { UserRole } from "./types";

const DASHBOARD_PRIORITY: { role: UserRole; path: string }[] = [
  // A diaspora user's home should win over whatever other role they also hold —
  // the diaspora journey is meant to be their primary experience of the platform.
  { role: "DIASPORA", path: "/diaspora" },
  { role: "LANDLORD", path: "/landlord" },
  { role: "AGENT", path: "/landlord" },
  { role: "DEVELOPER", path: "/landlord" },
  { role: "PRIVATE", path: "/landlord" },
  { role: "INVESTOR", path: "/investments" },
  { role: "TENANT", path: "/tenant" },
  { role: "ADMIN", path: "/admin" },
  { role: "SERVICE_PROVIDER", path: "/provider" },
];

export function dashboardPathFor(roles: UserRole[] | undefined): string {
  if (!roles) return "/";
  for (const { role, path } of DASHBOARD_PRIORITY) {
    if (roles.includes(role)) return path;
  }
  return "/";
}
