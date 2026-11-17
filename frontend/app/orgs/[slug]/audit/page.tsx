"use client";

import { useEffect, useState } from "react";
import { organisations } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import type { OrgAuditEntry } from "@/lib/types";
import { useOrgWorkspace, ws } from "@/components/workspace/OrgWorkspace";

const formatter = new Intl.DateTimeFormat("en-ZW", {
  dateStyle: "medium",
  timeStyle: "short",
  timeZone: "Africa/Harare",
});

function humanise(action: string) {
  return action
    .replace(/^ORG_/, "")
    .toLowerCase()
    .replace(/_/g, " ")
    .replace(/^./, (c) => c.toUpperCase());
}

function money(cents?: number, currency?: string) {
  if (cents == null) return null;
  return new Intl.NumberFormat("en-ZW", { style: "currency", currency: currency ?? "USD" }).format(cents / 100);
}

export default function OrgAuditPage() {
  const { slug, can } = useOrgWorkspace();
  const { token } = useAuth();
  const [entries, setEntries] = useState<OrgAuditEntry[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const allowed = can("AUDIT_VIEW");

  useEffect(() => {
    if (!token || !allowed) return;
    organisations.audit(slug, token, 200).then(setEntries, (e: unknown) =>
      setError(e instanceof Error ? e.message : "Could not load the audit log")
    );
  }, [slug, token, allowed]);

  if (!allowed) return <p className="text-muted">Your role doesn&apos;t include the audit log.</p>;
  if (error) return <p className="text-danger">{error}</p>;
  if (!entries) return <p className="text-muted">Loading…</p>;

  return (
    <div>
      <h1 className="text-xl font-semibold">Audit log</h1>
      <p className="text-sm text-muted mt-1">
        Approvals, role changes, status changes and money movements in this workspace. Entries can&apos;t be edited or deleted.
      </p>
      {entries.length === 0 ? (
        <p className="mt-6 text-muted">Nothing recorded yet.</p>
      ) : (
        <ol className="mt-6 space-y-2">
          {entries.map((e) => (
            <li key={e.id} className={`${ws.card} p-3 sm:p-4`}>
              <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
                <p className="font-medium">{humanise(e.action)}</p>
                <time className="text-xs text-muted" dateTime={e.createdAt}>
                  {formatter.format(new Date(e.createdAt))}
                </time>
              </div>
              <p className="text-sm text-muted mt-1 break-words">
                {e.actorName ?? "System"} · {e.entityType}
                {e.entityId != null && ` #${e.entityId}`}
                {(e.fromState || e.toState) && (
                  <>
                    {" "}
                    · {e.fromState ?? "∅"} → {e.toState ?? "∅"}
                  </>
                )}
                {e.amountCents != null && <> · {money(e.amountCents, e.currency)}</>}
              </p>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}
