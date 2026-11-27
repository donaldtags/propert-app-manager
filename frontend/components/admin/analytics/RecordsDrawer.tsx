"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { Loader2, X } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { analyticsOverview, type DrillRecords, type OverviewGrain, type OverviewKpi } from "@/lib/api";
import StatusBadge from "@/components/StatusBadge";

/** The records behind one KPI, in a side panel. */
export default function RecordsDrawer({ kpi, grain, periodStart, periodLabel, publishedAt, city, suburb, onClose }: {
  kpi: OverviewKpi;
  grain: OverviewGrain;
  periodStart: string;
  periodLabel: string;
  publishedAt?: string;
  city?: string;
  suburb?: string;
  onClose: () => void;
}) {
  const { token } = useAuth();
  const [data, setData] = useState<DrillRecords | null>(null);
  const [error, setError] = useState("");
  const closeRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    closeRef.current?.focus();
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  useEffect(() => {
    if (!token || !kpi.metric) return;
    analyticsOverview.records(token, {
      metric: kpi.metric, grain, periodStart, listingType: kpi.listingType,
      city: kpi.locationApplies ? city : undefined, suburb: kpi.locationApplies ? suburb : undefined,
    }).then(setData).catch((e) => setError(e instanceof Error ? e.message : "Couldn't load the records."));
  }, [token, kpi, grain, periodStart, city, suburb]);

  return (
    <div className="fixed inset-0 z-50 flex justify-end" role="dialog" aria-modal="true" aria-labelledby="drill-title">
      <button className="absolute inset-0 bg-gray-900/30" aria-label="Close" onClick={onClose} />
      <aside className="relative w-full max-w-xl h-full bg-white shadow-2xl flex flex-col">
        <header className="px-5 py-4 border-b border-gray-200 flex items-start justify-between gap-4">
          <div>
            <h2 id="drill-title" className="font-semibold text-gray-900">{kpi.label}</h2>
            <p className="text-xs text-gray-500 mt-0.5">
              {periodLabel}{kpi.locationApplies && city ? ` · ${suburb ? `${suburb}, ` : ""}${city}` : ""}
            </p>
          </div>
          <button ref={closeRef} onClick={onClose} className="text-gray-400 hover:text-gray-700" aria-label="Close records">
            <X className="w-5 h-5" />
          </button>
        </header>
        <div className="flex-1 overflow-y-auto px-5 py-4 space-y-3">
          {error && <p className="text-sm text-red-700">{error}</p>}
          {!data && !error && <p className="flex items-center gap-2 text-sm text-gray-500"><Loader2 className="w-4 h-4 animate-spin" /> Loading records…</p>}
          {data && (
            <>
              <p className="text-xs text-gray-500">
                {data.note ?? <>
                  {data.total.toLocaleString()} record{data.total === 1 ? "" : "s"} as the data stands now
                  {publishedAt ? `. The figure on the dashboard was calculated at ${new Date(publishedAt).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" })}, so very recent changes can make the two differ slightly` : ""}.
                  {data.truncated ? " Showing the first 500." : ""}
                </>}
              </p>
              {data.records.length > 0 && (
                <ul className="divide-y divide-gray-100 border border-gray-200 rounded-xl">
                  {data.records.map((r) => (
                    <li key={`${r.kind}-${r.id}`} className="px-4 py-3 flex items-start gap-3 text-sm">
                      <div className="min-w-0 flex-1">
                        {r.href ? (
                          <Link href={r.href} className="font-medium text-gray-900 hover:text-brand-600 truncate block">{r.title}</Link>
                        ) : (
                          <p className="font-medium text-gray-900 truncate">{r.title}</p>
                        )}
                        <p className="text-xs text-gray-500 truncate">
                          #{r.id}{r.detail ? ` · ${r.detail}` : ""}{r.at ? ` · ${new Date(r.at).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" })}` : ""}
                        </p>
                      </div>
                      <div className="text-right shrink-0 space-y-1">
                        {r.amount != null && (
                          <p className="tabular-nums text-gray-900">{r.currency === "USD" || !r.currency ? "$" : `${r.currency} `}{Number(r.amount).toLocaleString()}</p>
                        )}
                        {r.status && <StatusBadge status={r.status} />}
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </>
          )}
        </div>
      </aside>
    </div>
  );
}
