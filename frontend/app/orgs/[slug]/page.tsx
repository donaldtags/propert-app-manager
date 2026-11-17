"use client";

import Link from "next/link";
import { AlertTriangle, AtSign, MessageCircle, Phone, ShieldCheck } from "lucide-react";
import { DIVISION_LABELS, ROLE_LABELS, useOrgWorkspace, ws } from "@/components/workspace/OrgWorkspace";
import type { Division } from "@/lib/types";

const ALL_DIVISIONS: Division[] = ["PROPERTIES", "MANAGEMENT", "INTELLIGENCE", "DEVELOPMENTS", "INVESTMENTS"];

export default function OrgHomePage() {
  const { slug, context, can } = useOrgWorkspace();
  const org = context.organisation;
  const isStaff = context.roles.some((r) => ["ORG_ADMIN", "PREA", "NEGOTIATOR", "PROPERTY_MANAGER", "ANALYST"].includes(r));
  const visibleDivisions = ALL_DIVISIONS.filter(
    (d) => org.divisions.includes(d) && (context.divisions.length === 0 || context.divisions.includes(d))
  );

  return (
    <div className="space-y-6">
      <section>
        <h1 className="text-2xl sm:text-3xl font-bold">{org.name}</h1>
        {org.slogan && <p className="mt-1 text-muted">{org.slogan}</p>}
        {context.roles.length > 0 && (
          <div className="mt-3 flex flex-wrap gap-2">
            {context.roles.map((r) => (
              <span key={r} className={ws.chip}>
                {ROLE_LABELS[r] ?? r}
              </span>
            ))}
          </div>
        )}
      </section>

      {(isStaff || context.platformOverride) && !org.preaName && (
        <div role="alert" className="flex gap-3 rounded-2xl border border-warning p-4 text-sm">
          <AlertTriangle className="w-5 h-5 text-warning shrink-0" />
          <p>
            No Principal Registered Estate Agent is assigned. Listings can&apos;t be approved for publication and mandates
            can&apos;t be signed off until one is.
            {can("MEMBERS_MANAGE") && (
              <>
                {" "}
                <Link href={`/orgs/${slug}/settings?tab=members`} className="font-semibold underline">
                  Assign a PREA
                </Link>
              </>
            )}
          </p>
        </div>
      )}

      <section aria-labelledby="divisions">
        <h2 id="divisions" className="text-lg font-semibold mb-3">
          Divisions
        </h2>
        <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {visibleDivisions.map((d) => (
            <li key={d} className={`${ws.card} p-4`}>
              <div className="flex items-start justify-between gap-2">
                <h3 className="font-semibold">{DIVISION_LABELS[d].name}</h3>
                <span className={`${ws.chip} text-muted`}>{DIVISION_LABELS[d].phase}</span>
              </div>
              <p className="mt-2 text-sm text-muted">{DIVISION_LABELS[d].blurb}</p>
            </li>
          ))}
        </ul>
      </section>

      <section className="grid gap-3 md:grid-cols-2">
        <div className={`${ws.card} p-4`}>
          <h2 className="font-semibold mb-3">Contact</h2>
          <ul className="space-y-3 text-sm">
            {org.whatsappNumber && (
              <li>
                <a
                  href={`https://wa.me/${org.whatsappNumber}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex items-center gap-2 underline-offset-2 hover:underline"
                >
                  <MessageCircle className="w-4 h-4 text-muted" /> WhatsApp {org.phone ?? `+${org.whatsappNumber}`}
                </a>
              </li>
            )}
            {org.phone && (
              <li>
                <a href={`tel:${org.phone.replace(/\s+/g, "")}`} className="flex items-center gap-2 hover:underline">
                  <Phone className="w-4 h-4 text-muted" /> {org.phone}
                </a>
              </li>
            )}
            {org.instagramHandle && (
              <li>
                <a
                  href={`https://instagram.com/${org.instagramHandle}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex items-center gap-2 hover:underline"
                >
                  <AtSign className="w-4 h-4 text-muted" /> @{org.instagramHandle}
                </a>
              </li>
            )}
          </ul>
        </div>
        <div className={`${ws.card} p-4`}>
          <h2 className="font-semibold mb-3 flex items-center gap-2">
            <ShieldCheck className="w-4 h-4" /> Estate Agents Council
          </h2>
          <dl className="text-sm grid grid-cols-[auto,1fr] gap-x-4 gap-y-2">
            <dt className="text-muted">Principal (PREA)</dt>
            <dd>{org.preaName ?? "Not assigned"}</dd>
            <dt className="text-muted">Registration no.</dt>
            <dd>{org.eacRegistrationNumber ?? "Not recorded"}</dd>
            {org.trustAccountReference !== undefined && (
              <>
                <dt className="text-muted">Trust account</dt>
                <dd>{org.trustAccountReference || "Not recorded"}</dd>
              </>
            )}
          </dl>
        </div>
      </section>
    </div>
  );
}
