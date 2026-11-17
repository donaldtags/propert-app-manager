"use client";

import { createContext, useContext } from "react";
import type { Division, OrgContext, OrgPermission } from "@/lib/types";

export interface OrgWorkspaceValue {
  slug: string;
  context: OrgContext;
  can: (permission: OrgPermission) => boolean;
  refresh: () => Promise<void>;
}

const OrgWorkspaceContext = createContext<OrgWorkspaceValue | null>(null);

export const OrgWorkspaceProvider = OrgWorkspaceContext.Provider;

/** The active organisation workspace. Only usable under app/orgs/[slug]/layout.tsx. */
export function useOrgWorkspace(): OrgWorkspaceValue {
  const value = useContext(OrgWorkspaceContext);
  if (!value) throw new Error("useOrgWorkspace must be used inside an organisation workspace");
  return value;
}

export const DIVISION_LABELS: Record<Division, { name: string; blurb: string; phase: string }> = {
  PROPERTIES: { name: "Properties", blurb: "Sales and rentals: contacts, mandates, marketing, viewings, offers.", phase: "Phase 2" },
  MANAGEMENT: { name: "Management", blurb: "Landlords, tenants, rent ledger, maintenance and owner statements.", phase: "Phase 3" },
  INTELLIGENCE: { name: "Intelligence", blurb: "Asking and achieved prices, suburb dashboards, yield calculator, Property Pulse.", phase: "Phase 4" },
  DEVELOPMENTS: { name: "Developments", blurb: "Landowner joint ventures from first site visit to sales.", phase: "Phase 5" },
  INVESTMENTS: { name: "Investments", blurb: "Investor profiles, briefing events and listing matches.", phase: "Phase 5" },
};

export const ROLE_LABELS: Record<string, string> = {
  ORG_ADMIN: "Admin",
  PREA: "Principal Registered Estate Agent",
  NEGOTIATOR: "Negotiator",
  PROPERTY_MANAGER: "Property Manager",
  ANALYST: "Analyst",
  OWNER: "Owner / Landlord",
  TENANT: "Tenant",
  BUYER: "Buyer",
  INVESTOR: "Investor",
  LANDOWNER: "Landowner",
};

/** Shared class strings so every workspace screen uses the same semantic tokens. */
export const ws = {
  card: "bg-raised border border-line rounded-2xl",
  button:
    "inline-flex items-center justify-center gap-2 rounded-lg bg-primary text-on-primary px-4 py-2.5 text-sm font-semibold hover:opacity-90 disabled:opacity-50 min-h-11",
  buttonGhost:
    "inline-flex items-center justify-center gap-2 rounded-lg border border-line px-4 py-2.5 text-sm font-medium hover:bg-surface disabled:opacity-50 min-h-11",
  input:
    "w-full rounded-lg border border-line bg-raised text-ink px-3 py-2.5 text-sm placeholder:text-muted focus:outline-none focus:ring-2 focus:ring-accent min-h-11",
  label: "block text-xs font-semibold uppercase tracking-wide text-muted mb-1.5",
  chip: "inline-flex items-center rounded-full border border-line px-2.5 py-0.5 text-xs font-medium",
};
