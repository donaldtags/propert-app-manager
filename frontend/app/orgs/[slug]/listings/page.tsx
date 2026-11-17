"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { CheckCircle2, CircleDashed, Plus } from "lucide-react";
import { orgListings } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { formatCents } from "@/lib/money";
import type { OrgListingSummary } from "@/lib/types";
import CessionBadge from "@/components/CessionBadge";
import { useOrgWorkspace, ws } from "@/components/workspace/OrgWorkspace";
import { STATUS_LABEL } from "@/components/workspace/listingLabels";


export default function OrgListingsPage() {
  const { slug, can } = useOrgWorkspace();
  const { token } = useAuth();
  const [items, setItems] = useState<OrgListingSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) return;
    orgListings.list(slug, token).then(setItems, (e: unknown) =>
      setError(e instanceof Error ? e.message : "Could not load listings")
    );
  }, [slug, token]);

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold">Listings</h1>
          <p className="text-sm text-muted mt-1">
            A listing goes live only when title is verified, a signed mandate is in date, the owner has consented and the
            PREA has approved.
          </p>
        </div>
        {can("LISTING_CREATE") && (
          <Link href={`/orgs/${slug}/listings/new`} className={ws.button}>
            <Plus className="w-4 h-4" /> New listing
          </Link>
        )}
      </div>

      {error && <p className="mt-4 text-danger">{error}</p>}
      {!items ? (
        !error && <p className="mt-6 text-muted">Loading…</p>
      ) : items.length === 0 ? (
        <p className="mt-8 text-muted">No listings yet.</p>
      ) : (
        <ul className="mt-6 space-y-3">
          {items.map((l) => (
            <li key={l.propertyId}>
              <Link href={`/orgs/${slug}/listings/${l.propertyId}`} className={`${ws.card} block p-4 hover:border-accent`}>
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="font-semibold truncate">{l.title}</p>
                    <p className="text-sm text-muted">
                      {l.suburb}, {l.city} · {l.listingType === "SALE" ? "For sale" : "To let"} · {formatCents(l.priceCents, l.currency)}
                    </p>
                  </div>
                  <div className="flex flex-wrap gap-2 items-center">
                    {l.riskFlags.includes("CESSION") && <CessionBadge />}
                    <span className={`${ws.chip} ${l.status === "AVAILABLE" ? "text-success" : ""}`}>
                      {STATUS_LABEL[l.status] ?? l.status}
                    </span>
                  </div>
                </div>
                <p className="mt-2 text-xs text-muted flex items-center gap-1.5">
                  {l.publishable ? (
                    <>
                      <CheckCircle2 className="w-3.5 h-3.5 text-success" /> All checks met
                    </>
                  ) : (
                    <>
                      <CircleDashed className="w-3.5 h-3.5 text-warning" /> {l.openChecks} check{l.openChecks === 1 ? "" : "s"} outstanding
                    </>
                  )}
                  {l.negotiatorName && <> · {l.negotiatorName}</>}
                </p>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
