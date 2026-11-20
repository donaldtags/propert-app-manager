"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { BadgeCheck, Building2, Flag, ExternalLink } from "lucide-react";
import {
  agentLicences,
  companies,
  listingReports,
  REPORT_REASONS,
  type AgentLicence,
  type CompanyProfile,
  type ListingReport,
} from "@/lib/trust";

function useQueue<T>(load: () => Promise<T[]>) {
  const [items, setItems] = useState<T[] | null>(null);
  const [error, setError] = useState("");
  const reload = useCallback(() => {
    load().then(setItems).catch((e) => setError(e instanceof Error ? e.message : "Couldn't load"));
  }, [load]);
  useEffect(() => {
    reload();
  }, [reload]);
  return { items, error, setError, reload };
}

const btn = "text-xs font-semibold px-3 py-2 rounded-lg";

function Empty({ text }: { text: string }) {
  return <p className="text-center text-gray-500 py-12">{text}</p>;
}

/** Agents waiting for their Estate Agents Council licence to be checked. */
export function AgentLicenceQueue({ token }: { token: string }) {
  const load = useCallback(() => agentLicences.pending(token), [token]);
  const { items, error, setError, reload } = useQueue<AgentLicence>(load);
  const decide = async (l: AgentLicence, approved: boolean) => {
    const note = approved ? undefined : window.prompt(`Why is ${l.agentName}'s licence being rejected? They will see this.`);
    if (!approved && !note?.trim()) return;
    try {
      await agentLicences.review(l.id, approved, note?.trim(), token);
      reload();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed");
    }
  };
  return (
    <section>
      <h2 className="text-xl font-bold text-gray-900 flex items-center gap-2 mb-1">
        <BadgeCheck className="w-5 h-5 text-brand-600" /> Agent licences
      </h2>
      <p className="text-sm text-gray-500 mb-4">
        Check the registration number against the Estate Agents Council register and the certificate. Approving publishes the
        agent&apos;s waiting listings.
      </p>
      {error && <p className="text-sm text-red-600 mb-3" role="alert">{error}</p>}
      {!items ? <div className="h-24 bg-gray-100 rounded-xl animate-pulse" /> : items.length === 0 ? <Empty text="No licences waiting." /> : (
        <ul className="space-y-3">
          {items.map((l) => (
            <li key={l.id} className="bg-white border border-gray-200 rounded-xl p-4 flex flex-wrap gap-3 items-center justify-between">
              <div className="min-w-0">
                <p className="font-semibold text-gray-900">{l.agentName}</p>
                <p className="text-sm text-gray-600 break-all">{l.agentEmail}</p>
                <p className="text-sm text-gray-800 mt-1">EAC no. <strong>{l.registrationNumber}</strong></p>
                <a
                  href={agentLicences.certificateUrl(l.id)}
                  onClick={async (e) => {
                    e.preventDefault();
                    const res = await fetch(agentLicences.certificateUrl(l.id), { headers: { Authorization: `Bearer ${token}` } });
                    if (res.ok) window.open(URL.createObjectURL(await res.blob()), "_blank", "noopener");
                  }}
                  className="inline-flex items-center gap-1 text-sm text-brand-600 font-medium mt-1 py-1"
                >
                  View certificate <ExternalLink className="w-3.5 h-3.5" />
                </a>
                <p className="text-[11px] text-gray-400 font-mono break-all">SHA-256 {l.documentSha256}</p>
              </div>
              <div className="flex gap-2">
                <button onClick={() => decide(l, true)} className={`${btn} bg-brand-600 text-white`}>Approve</button>
                <button onClick={() => decide(l, false)} className={`${btn} bg-red-50 text-red-700`}>Reject</button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

/** Agencies that registered themselves and wait for approval. */
export function CompanyQueue({ token }: { token: string }) {
  const load = useCallback(() => companies.pendingReview(token), [token]);
  const { items, error, setError, reload } = useQueue<CompanyProfile>(load);
  const decide = async (c: CompanyProfile, approved: boolean) => {
    const note = approved ? undefined : window.prompt(`Why is ${c.name} being rejected? The company will see this.`);
    if (!approved && !note?.trim()) return;
    try {
      await companies.decide(c.slug, approved, note?.trim(), token);
      reload();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed");
    }
  };
  return (
    <section>
      <h2 className="text-xl font-bold text-gray-900 flex items-center gap-2 mb-1">
        <Building2 className="w-5 h-5 text-brand-600" /> Companies waiting for approval
      </h2>
      <p className="text-sm text-gray-500 mb-4">Confirm the firm&apos;s EAC registration before approving. Approved companies get a public page and can publish.</p>
      {error && <p className="text-sm text-red-600 mb-3" role="alert">{error}</p>}
      {!items ? <div className="h-24 bg-gray-100 rounded-xl animate-pulse" /> : items.length === 0 ? <Empty text="No companies waiting." /> : (
        <ul className="space-y-3">
          {items.map((c) => (
            <li key={c.slug} className="bg-white border border-gray-200 rounded-xl p-4 flex flex-wrap gap-3 items-center justify-between">
              <div className="min-w-0">
                <p className="font-semibold text-gray-900">{c.name}</p>
                <p className="text-sm text-gray-800">EAC no. <strong>{c.eacRegistrationNumber ?? "—"}</strong></p>
                <p className="text-sm text-gray-600">{[c.phone, c.email, c.address].filter(Boolean).join(" · ")}</p>
              </div>
              <div className="flex gap-2">
                <button onClick={() => decide(c, true)} className={`${btn} bg-brand-600 text-white`}>Approve</button>
                <button onClick={() => decide(c, false)} className={`${btn} bg-red-50 text-red-700`}>Reject</button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

/** Visitors' "Report this listing" submissions. */
export function ReportQueue({ token }: { token: string }) {
  const load = useCallback(() => listingReports.open(token), [token]);
  const { items, error, setError, reload } = useQueue<ListingReport>(load);
  const resolve = async (r: ListingReport, action: "DISMISS" | "TAKE_DOWN") => {
    const note = action === "TAKE_DOWN"
      ? window.prompt(`Why is "${r.propertyTitle}" being taken down? The owner will see this.`)
      : undefined;
    if (action === "TAKE_DOWN" && !note?.trim()) return;
    try {
      await listingReports.resolve(r.id, action, note?.trim(), token);
      reload();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed");
    }
  };
  return (
    <section>
      <h2 className="text-xl font-bold text-gray-900 flex items-center gap-2 mb-1">
        <Flag className="w-5 h-5 text-red-600" /> Reported listings
      </h2>
      <p className="text-sm text-gray-500 mb-4">
        Three different people reporting one listing sends it back to Property Verification automatically. Taking a listing down
        hides it and tells the owner why.
      </p>
      {error && <p className="text-sm text-red-600 mb-3" role="alert">{error}</p>}
      {!items ? <div className="h-24 bg-gray-100 rounded-xl animate-pulse" /> : items.length === 0 ? <Empty text="No open reports." /> : (
        <ul className="space-y-3">
          {items.map((r) => (
            <li key={r.id} className="bg-white border border-gray-200 rounded-xl p-4 flex flex-wrap gap-3 items-center justify-between">
              <div className="min-w-0">
                <Link href={`/properties/${r.propertyId}`} className="font-semibold text-gray-900 hover:underline">{r.propertyTitle}</Link>
                <p className="text-sm text-red-700">{REPORT_REASONS.find((x) => x.value === r.reason)?.label ?? r.reason}</p>
                {r.details && <p className="text-sm text-gray-700 mt-1">&ldquo;{r.details}&rdquo;</p>}
                <p className="text-xs text-gray-500 mt-1">
                  {new Date(r.createdAt).toLocaleString()} · {r.signedInReporter ? "signed-in user" : "visitor"}
                </p>
              </div>
              <div className="flex gap-2">
                <button onClick={() => resolve(r, "DISMISS")} className={`${btn} border border-gray-200 text-gray-700`}>Dismiss</button>
                <button onClick={() => resolve(r, "TAKE_DOWN")} className={`${btn} bg-red-600 text-white`}>Take down</button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
