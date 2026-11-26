"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { AlertTriangle, CheckCircle2, Clock, Download, Lock, MapPin, RefreshCw, XCircle } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { analyticsOverview, type ExecutiveOverviewData, type OverviewGrain, type OverviewKpi } from "@/lib/api";
import { useZimbabweLocations } from "@/lib/locations";
import AlertBanner from "@/components/AlertBanner";
import KpiTile from "./KpiTile";
import RecordsDrawer from "./RecordsDrawer";
import { time } from "./format";

const GRAINS: { value: OverviewGrain; label: string; short: string }[] = [
  { value: "DAY", label: "Today", short: "yesterday" },
  { value: "WEEK", label: "This week", short: "last week" },
  { value: "MONTH", label: "This month", short: "last month" },
  { value: "QUARTER", label: "This quarter", short: "last quarter" },
  { value: "YEAR", label: "This year", short: "last year" },
];

/**
 * The admin landing page: how EstateX and its market are doing, for the chosen period and place,
 * compared like-for-like with the previous period. Every number comes from the hourly engine's
 * last successful run; the freshness chip says when that was.
 */
export default function ExecutiveOverview({ onOpenEngine }: { onOpenEngine?: () => void }) {
  const { token } = useAuth();
  const locations = useZimbabweLocations();
  const [grain, setGrain] = useState<OverviewGrain>("MONTH");
  const [compare, setCompare] = useState<"TO_DATE" | "FULL">("TO_DATE");
  const [city, setCity] = useState("");
  const [suburb, setSuburb] = useState("");
  const [data, setData] = useState<ExecutiveOverviewData | null>(null);
  // Loading = the filters on screen aren't the ones the shown figures were fetched for.
  const requestKey = `${grain}|${compare}|${city}|${city ? suburb : ""}`;
  const [loadedKey, setLoadedKey] = useState<string | null>(null);
  const loading = loadedKey !== requestKey;
  const [error, setError] = useState("");
  const [drill, setDrill] = useState<OverviewKpi | null>(null);

  const load = useCallback(() => {
    if (!token) return;
    analyticsOverview.get(token, { grain, compare, city: city || undefined, suburb: city && suburb ? suburb : undefined })
      .then((d) => { setData(d); setError(""); })
      .catch((e) => setError(e instanceof Error ? e.message : "Couldn't load the overview."))
      .finally(() => setLoadedKey(requestKey));
  }, [token, grain, compare, city, suburb, requestKey]);
  useEffect(load, [load]);

  const short = GRAINS.find((g) => g.value === grain)!.short;
  const comparisonShort = data && !data.period.complete && compare === "TO_DATE" ? `same point ${short}` : short;
  const suburbs = useMemo(() => (city ? locations.suburbs[city] ?? [] : []), [city, locations.suburbs]);
  const headline = data?.sections.find((s) => s.key === "headline");
  const rest = data?.sections.filter((s) => s.key !== "headline") ?? [];

  const exportCsv = () => {
    if (!data) return;
    const esc = (v: unknown) => {
      const s = v == null ? "" : String(v);
      return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
    };
    const lines = [
      ["EstateX executive overview"],
      ["Generated", new Date().toISOString()],
      ["Period", data.period.label, data.period.start, data.period.asOf ?? ""],
      ["Comparison", data.comparisonLabel, data.comparison.start, data.comparison.asOf ?? ""],
      ["Location", city ? `${suburb ? `${suburb}, ` : ""}${city}` : "All locations"],
      ["Figures calculated", data.freshness.lastSuccessAt ?? "never"],
      [],
      ["Section", "KPI", "Value", "Previous", "Change", "Change %", "Comparable", "Sample size", "Low sample", "Live", "Applies to location", "Note", "Definition"],
      ...data.sections.flatMap((s) => s.kpis.map((k) => [s.title, k.label, k.value, k.previous, k.change, k.changePercent, k.comparable,
        k.sampleSize, k.lowSample, k.live, k.locationApplies, k.note, k.help])),
    ];
    const blob = new Blob([lines.map((l) => l.map(esc).join(",")).join("\n")], { type: "text/csv;charset=utf-8" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = `estatex-overview-${grain.toLowerCase()}-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(a.href);
  };

  return (
    <div className="space-y-6">
      {/* Top bar */}
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 className="text-xl font-semibold text-gray-900 tracking-tight">Executive overview</h2>
          <p className="text-sm text-gray-500 mt-0.5">
            {data ? data.period.label : " "}
            {data && <span className="text-gray-400"> · {data.comparisonLabel.toLowerCase()}</span>}
          </p>
        </div>
        <div className="flex items-center gap-2">
          {data && <Freshness data={data} onClick={onOpenEngine} />}
          <button type="button" onClick={exportCsv} disabled={!data}
            className="inline-flex items-center gap-1.5 text-sm font-medium text-gray-700 border border-gray-200 hover:border-gray-300 bg-white rounded-lg px-3 py-2 disabled:opacity-50">
            <Download className="w-4 h-4" aria-hidden /> Export
          </button>
        </div>
      </div>

      {/* Filters: one row, above everything they affect */}
      <div className="flex flex-wrap items-center gap-3 bg-white border border-gray-200 rounded-xl p-2">
        <div role="radiogroup" aria-label="Period" className="flex rounded-lg bg-gray-100 p-0.5">
          {GRAINS.map((g) => (
            <button key={g.value} type="button" role="radio" aria-checked={grain === g.value} onClick={() => setGrain(g.value)}
              className={`px-3 py-1.5 text-sm rounded-md transition-colors ${grain === g.value ? "bg-white shadow-sm text-gray-900 font-medium" : "text-gray-600 hover:text-gray-900"}`}>
              {g.label}
            </button>
          ))}
        </div>
        <label className="flex items-center gap-2 text-sm text-gray-600">
          <span className="sr-only">Compare with</span>
          <select value={compare} onChange={(e) => setCompare(e.target.value as "TO_DATE" | "FULL")}
            className="border border-gray-200 rounded-lg px-2.5 py-1.5 bg-white text-sm">
            <option value="TO_DATE">vs same point last period</option>
            <option value="FULL">vs whole last period</option>
          </select>
        </label>
        <div className="flex items-center gap-2 ml-auto">
          <MapPin className="w-4 h-4 text-gray-400" aria-hidden />
          <label className="sr-only" htmlFor="ov-city">City</label>
          <select id="ov-city" value={city} onChange={(e) => { setCity(e.target.value); setSuburb(""); }}
            className="border border-gray-200 rounded-lg px-2.5 py-1.5 bg-white text-sm max-w-40">
            <option value="">All locations</option>
            {locations.cities.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
          <label className="sr-only" htmlFor="ov-suburb">Suburb</label>
          <select id="ov-suburb" value={suburb} onChange={(e) => setSuburb(e.target.value)} disabled={!city}
            className="border border-gray-200 rounded-lg px-2.5 py-1.5 bg-white text-sm max-w-44 disabled:opacity-50">
            <option value="">{city ? "All suburbs" : "Suburb"}</option>
            {suburbs.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
        </div>
      </div>

      {error && <AlertBanner variant="error">{error}</AlertBanner>}
      {data?.freshness.stale && (
        <AlertBanner variant="warning">
          These figures are out of date: the last successful update was {data.freshness.lastSuccessAt
            ? new Date(data.freshness.lastSuccessAt).toLocaleString() : "never"}. They are the last good figures, kept on purpose.
        </AlertBanner>
      )}
      {city && data && (
        <p className="text-xs text-gray-500 -mt-3">
          Showing {suburb ? `${suburb}, ${city}` : city}. Market and demand figures follow the location; platform and revenue figures are always for all locations.
        </p>
      )}

      {loading && !data && (
        <div className="grid grid-cols-2 lg:grid-cols-3 xl:grid-cols-6 gap-3" aria-busy>
          {Array.from({ length: 6 }).map((_, i) => <div key={i} className="h-44 rounded-2xl bg-gray-100 animate-pulse" />)}
        </div>
      )}

      {data && (
        <div className={`space-y-8 transition-opacity ${loading ? "opacity-60" : ""}`}>
          {headline && headline.kpis.length > 0 && (
            <section aria-label="At a glance" className="grid grid-cols-2 lg:grid-cols-3 xl:grid-cols-6 gap-3">
              {headline.kpis.map((k) => (
                <KpiTile key={k.key} kpi={k} grain={grain} comparisonShort={comparisonShort} headline locationChosen={!!city} onDrill={setDrill} />
              ))}
            </section>
          )}

          {rest.map((s) => (
            <section key={s.key} aria-labelledby={`sec-${s.key}`}>
              <div className="flex items-baseline justify-between gap-3 mb-3">
                <h3 id={`sec-${s.key}`} className="text-sm font-semibold text-gray-900">{s.title}</h3>
                {s.subtitle && <p className="text-xs text-gray-500">{s.subtitle}</p>}
              </div>
              {s.restricted ? (
                <p className="flex items-center gap-2 text-sm text-gray-500 bg-gray-50 border border-dashed border-gray-200 rounded-xl px-4 py-3">
                  <Lock className="w-4 h-4" aria-hidden /> Your role doesn&apos;t include {s.title.toLowerCase()} figures.
                </p>
              ) : (
                <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-4 gap-3">
                  {s.kpis.map((k) => (
                    <KpiTile key={`${s.key}-${k.key}`} kpi={k} grain={grain} comparisonShort={comparisonShort} locationChosen={!!city} onDrill={setDrill} />
                  ))}
                </div>
              )}
            </section>
          ))}

          <p className="text-xs text-gray-400 leading-relaxed max-w-3xl">
            Asking prices and listing counts come from listing history, which EstateX started recording on{" "}
            {data.freshness.historyStartsAt ? new Date(data.freshness.historyStartsAt).toLocaleDateString(undefined, { day: "numeric", month: "long", year: "numeric" }) : "—"}.
            Changes against periods before that aren&apos;t shown. Views, searches and enquiries show interest, not confirmed demand.
            Revenue counts only what EstateX earns; rent, deposits and other client money are shown separately.
          </p>
        </div>
      )}

      {drill && data && (
        <RecordsDrawer kpi={drill} grain={grain} periodStart={data.period.start} periodLabel={data.period.label}
          publishedAt={data.freshness.lastSuccessAt} city={city || undefined} suburb={suburb || undefined} onClose={() => setDrill(null)} />
      )}
    </div>
  );
}

function Freshness({ data, onClick }: { data: ExecutiveOverviewData; onClick?: () => void }) {
  const f = data.freshness;
  const map = {
    UP_TO_DATE: { Icon: CheckCircle2, cls: "text-emerald-700 bg-emerald-50 border-emerald-200", text: `Updated ${time(f.lastSuccessAt)}` },
    RUNNING: { Icon: RefreshCw, cls: "text-sky-700 bg-sky-50 border-sky-200", text: "Updating…" },
    STALE: { Icon: AlertTriangle, cls: "text-amber-800 bg-amber-50 border-amber-200", text: `Stale · ${time(f.lastSuccessAt)}` },
    FAILED: { Icon: XCircle, cls: "text-red-700 bg-red-50 border-red-200", text: `Last update failed · showing ${time(f.lastSuccessAt)}` },
    NEVER_RUN: { Icon: Clock, cls: "text-gray-600 bg-gray-50 border-gray-200", text: "Not calculated yet" },
  }[f.state];
  return (
    <button type="button" onClick={onClick} title={`Next update ${time(f.nextScheduledAt)}. Open the analytics engine for details.`}
      className={`inline-flex items-center gap-1.5 text-xs font-medium border rounded-full px-3 py-1.5 ${map.cls}`}>
      <map.Icon className={`w-3.5 h-3.5 ${f.state === "RUNNING" ? "animate-spin" : ""}`} aria-hidden />
      {map.text}
      <span className="text-gray-400 font-normal">· next {time(f.nextScheduledAt)}</span>
    </button>
  );
}

