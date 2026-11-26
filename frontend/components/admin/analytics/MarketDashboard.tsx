"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { AlertTriangle, Download, Info, Loader2, MapPin, X } from "lucide-react";
import { useAuth } from "@/lib/auth";
import {
  marketDashboard, type MarketDashboardData, type MarketGrain, type MarketGroup, type MarketKpi, type MarketListing,
  type MarketParams, type MarketType,
} from "@/lib/api";
import { useZimbabweLocations } from "@/lib/locations";
import { CATEGORY_OPTIONS } from "@/components/workspace/listingLabels";
import AlertBanner from "@/components/AlertBanner";
import LineChart from "./charts/LineChart";
import ColumnChart from "./charts/ColumnChart";
import { formatValue } from "./format";

const RANGES = [
  { key: "3m", label: "3 months", months: 3 },
  { key: "6m", label: "6 months", months: 6 },
  { key: "12m", label: "12 months", months: 12 },
  { key: "ytd", label: "This year", months: 0 },
] as const;

function iso(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function rangeDates(key: string): { from: string; to: string } {
  const to = new Date();
  const from = new Date(to);
  const r = RANGES.find((x) => x.key === key)!;
  if (r.months === 0) { from.setMonth(0, 1); } else { from.setMonth(from.getMonth() - r.months); from.setDate(from.getDate() + 1); }
  return { from: iso(from), to: iso(to) };
}

/**
 * The rental or sales market for any place, property type, bedroom count, price range and dates.
 * One set of filters drives every figure, chart and table on the page.
 */
export default function MarketDashboard({ type, canConfigure }: { type: MarketType; canConfigure: boolean }) {
  const { token } = useAuth();
  const locations = useZimbabweLocations();
  const rent = type === "rent";
  const [range, setRange] = useState<string>("12m");
  const [grain, setGrain] = useState<MarketGrain | "">("");
  const [city, setCity] = useState("");
  const [suburb, setSuburb] = useState("");
  const [category, setCategory] = useState("");
  const [bedrooms, setBedrooms] = useState("");
  const [minPrice, setMinPrice] = useState("");
  const [maxPrice, setMaxPrice] = useState("");
  const [data, setData] = useState<MarketDashboardData | null>(null);
  const [other, setOther] = useState<MarketDashboardData | null>(null);
  const [error, setError] = useState("");
  const [loadedKey, setLoadedKey] = useState("");
  const [drawer, setDrawer] = useState<{ title: string; params: MarketParams } | null>(null);
  const [ratioDraft, setRatioDraft] = useState("");
  const [ratioMsg, setRatioMsg] = useState("");

  const params: MarketParams = useMemo(() => ({
    ...rangeDates(range), grain: grain || undefined, city: city || undefined, suburb: city && suburb ? suburb : undefined,
    category: category || undefined, bedrooms: bedrooms === "" ? undefined : Number(bedrooms),
    minPrice: minPrice ? Number(minPrice) : undefined, maxPrice: maxPrice ? Number(maxPrice) : undefined,
  }), [range, grain, city, suburb, category, bedrooms, minPrice, maxPrice]);
  const key = JSON.stringify(params);
  const loading = loadedKey !== key;

  const load = useCallback(() => {
    if (!token) return;
    const mine = marketDashboard.get(token, type, params);
    // The sales page compares with rentals for the same place and kind of home (price filter left out).
    const theirs = rent ? Promise.resolve(null)
      : marketDashboard.get(token, "rent", { ...params, minPrice: undefined, maxPrice: undefined }).catch(() => null);
    Promise.all([mine, theirs])
      .then(([d, o]) => { setData(d); setOther(o); setError(""); })
      .catch((e) => setError(e instanceof Error ? e.message : "Couldn't load the market figures."))
      .finally(() => setLoadedKey(key));
  }, [token, type, params, rent, key]);
  useEffect(load, [load]);

  const suburbs = city ? locations.suburbs[city] ?? [] : [];
  const points = data?.series ?? [];

  const exportCsv = () => {
    if (!data) return;
    const esc = (v: unknown) => { const s = v == null ? "" : String(v); return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s; };
    const lines: unknown[][] = [
      [`EstateX ${rent ? "rental" : "sales"} market`], ["Generated", new Date().toISOString()],
      ["Range", data.from, data.to, data.grain], ["Filters", JSON.stringify(params)],
      ["Figures calculated", data.figuresCalculatedAt ?? ""], ["Notes", ...data.notes], [],
      ["KPI", "Value", "Previous", "Change %", "Sample size", "Small sample", "Note", "Definition"],
      ...data.kpis.map((k) => [k.label, k.value, k.previous, k.changePercent, k.sampleSize, k.lowSample, k.note, k.help]), [],
      ["Period", ...Array.from(new Set(points.flatMap((p) => Object.keys(p.values))))],
    ];
    const cols = lines[lines.length - 1].slice(1) as string[];
    points.forEach((p) => lines.push([p.label, ...cols.map((c) => p.values[c])]));
    lines.push([], ["Suburb", "City", "Live listings", "Median asking", "n", "New", "Demand", rent ? "Median agreed rent" : "Median verified price", "Transactions"]);
    data.bySuburb.forEach((g) => lines.push([g.label, g.city, g.active, g.askingMedian, g.askingN, g.newListings, g.demand, g.transactionMedian, g.transactions]));
    const blob = new Blob([lines.map((l) => l.map(esc).join(",")).join("\n")], { type: "text/csv;charset=utf-8" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = `estatex-${rent ? "rental" : "sales"}-market-${iso(new Date())}.csv`;
    a.click();
    URL.revokeObjectURL(a.href);
  };

  const saveRatio = async () => {
    if (!token) return;
    const pct = Number(ratioDraft);
    if (!(pct >= 0 && pct <= 90)) { setRatioMsg("Enter a percentage between 0 and 90."); return; }
    try {
      await marketDashboard.setCostRatio(token, pct / 100);
      setRatioMsg("Saved. Net yields now use this assumption.");
      setLoadedKey("");
      load();
    } catch (e) {
      setRatioMsg(e instanceof Error ? e.message : "Couldn't save.");
    }
  };

  const selectCls = "border border-gray-200 rounded-lg px-2.5 py-1.5 bg-white text-sm";
  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-xl font-semibold text-gray-900 tracking-tight">{rent ? "Rental market" : "Sales market"}</h2>
          <p className="text-sm text-gray-500 mt-0.5">
            {rent ? "Supply, asking and agreed rents, demand and yields" : "Supply, asking and verified prices, transactions and demand"}
            {data?.figuresCalculatedAt && <span className="text-gray-400"> · figures from the update at {new Date(data.figuresCalculatedAt).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" })}</span>}
          </p>
        </div>
        <button type="button" onClick={exportCsv} disabled={!data}
          className="inline-flex items-center gap-1.5 text-sm font-medium text-gray-700 border border-gray-200 hover:border-gray-300 bg-white rounded-lg px-3 py-2 disabled:opacity-50">
          <Download className="w-4 h-4" aria-hidden /> Export
        </button>
      </div>

      {/* One row of filters, above everything they affect. */}
      <div className="flex flex-wrap items-center gap-2 bg-white border border-gray-200 rounded-xl p-2">
        <div role="radiogroup" aria-label="Date range" className="flex rounded-lg bg-gray-100 p-0.5">
          {RANGES.map((r) => (
            <button key={r.key} type="button" role="radio" aria-checked={range === r.key} onClick={() => setRange(r.key)}
              className={`px-3 py-1.5 text-sm rounded-md ${range === r.key ? "bg-white shadow-sm text-gray-900 font-medium" : "text-gray-600 hover:text-gray-900"}`}>
              {r.label}
            </button>
          ))}
        </div>
        <label className="sr-only" htmlFor={`${type}-grain`}>Group by</label>
        <select id={`${type}-grain`} value={grain} onChange={(e) => setGrain(e.target.value as MarketGrain | "")} className={selectCls}>
          <option value="">Group automatically</option><option value="WEEK">By week</option><option value="MONTH">By month</option><option value="QUARTER">By quarter</option>
        </select>
        <span className="flex items-center gap-1.5 ml-auto">
          <MapPin className="w-4 h-4 text-gray-400" aria-hidden />
          <label className="sr-only" htmlFor={`${type}-city`}>City</label>
          <select id={`${type}-city`} value={city} onChange={(e) => { setCity(e.target.value); setSuburb(""); }} className={`${selectCls} max-w-36`}>
            <option value="">All locations</option>
            {locations.cities.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
          <label className="sr-only" htmlFor={`${type}-suburb`}>Suburb</label>
          <select id={`${type}-suburb`} value={suburb} onChange={(e) => setSuburb(e.target.value)} disabled={!city} className={`${selectCls} max-w-40 disabled:opacity-50`}>
            <option value="">{city ? "All suburbs" : "Suburb"}</option>
            {suburbs.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
        </span>
        <label className="sr-only" htmlFor={`${type}-cat`}>Property type</label>
        <select id={`${type}-cat`} value={category} onChange={(e) => setCategory(e.target.value)} className={selectCls}>
          <option value="">Any type</option>
          {CATEGORY_OPTIONS.filter((c) => c.value !== "OTHER").map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
        </select>
        <label className="sr-only" htmlFor={`${type}-beds`}>Bedrooms</label>
        <select id={`${type}-beds`} value={bedrooms} onChange={(e) => setBedrooms(e.target.value)} className={selectCls}>
          <option value="">Any bedrooms</option>
          {[0, 1, 2, 3, 4, 5].map((b) => <option key={b} value={b}>{b === 0 ? "Studio" : b === 5 ? "5+ beds" : `${b} bed${b > 1 ? "s" : ""}`}</option>)}
        </select>
        <PriceInput key={`min-${minPrice}`} label="Min $" value={minPrice} onCommit={setMinPrice} />
        <PriceInput key={`max-${maxPrice}`} label="Max $" value={maxPrice} onCommit={setMaxPrice} />
      </div>

      {error && <AlertBanner variant="error">{error}</AlertBanner>}
      {data?.notes.map((n) => <p key={n} className="text-xs text-gray-500 -mt-3 flex items-center gap-1.5"><Info className="w-3.5 h-3.5" aria-hidden /> {n}</p>)}

      {!data && loading && (
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3" aria-busy>
          {Array.from({ length: 8 }).map((_, i) => <div key={i} className="h-28 rounded-2xl bg-gray-100 animate-pulse" />)}
        </div>
      )}

      {data && (
        <div className={`space-y-6 transition-opacity ${loading ? "opacity-60" : ""}`}>
          <section aria-label="Key figures" className="grid grid-cols-2 lg:grid-cols-4 gap-3">
            {data.kpis.map((k) => <Stat key={k.key} k={k} />)}
          </section>

          <div className="grid lg:grid-cols-2 gap-4">
            <Card title={rent ? "Asking rent and agreed rent" : "Asking price and verified sale price"}
              subtitle={rent ? "Median monthly rent. Asking = what landlords list; agreed = what leases were signed for."
                : "Median price. Asking = listed price; verified = sales EstateX confirmed from conveyancer or Deeds Registry evidence."}>
              <LineChart title={rent ? "Median asking and agreed rent" : "Median asking and verified price"} unit="USD" points={points}
                series={[{ key: "askingMedian", label: rent ? "Asking rent" : "Asking price" },
                  { key: rent ? "contractedMedian" : "verifiedMedian", label: rent ? "Agreed rent" : "Verified sale price" }]}
                band={{ low: "askingP25", high: "askingP75" }} />
            </Card>
            <Card title={rent ? "Rental listings available" : "Properties for sale"} subtitle="Listings live at the end of each period.">
              <LineChart title="Listings available" unit="COUNT" points={points} series={[{ key: "active", label: "Live listings" }]} />
            </Card>
            <Card title="New listings" subtitle="Listings that went live for the first time in each period.">
              <ColumnChart title="New listings" points={points} valueKey="newListings" label="New listings" />
            </Card>
            {rent ? (
              <Card title="Rental agreements signed" subtitle="Leases signed in each period.">
                <ColumnChart title="Leases signed" points={points} valueKey="leasesSigned" label="Leases signed" />
              </Card>
            ) : (
              <Card title="Transactions" subtitle="Sales reported by owners or agents, and sales EstateX has verified.">
                <LineChart title="Reported and verified sales" unit="COUNT" points={points}
                  series={[{ key: "salesReported", label: "Reported" }, { key: "verifiedSales", label: "Verified" }]} />
              </Card>
            )}
            {data.demandIncluded && (
              <Card title={rent ? "Enquiries per available rental" : "Enquiries per listing for sale"}
                subtitle="Enquiries in each period divided by listings live at its end. Rising means interest is outpacing supply.">
                <LineChart title="Enquiries per available listing" unit="COUNT" points={points}
                  series={[{ key: "enquiriesPerListing", label: "Enquiries per listing" }]} />
              </Card>
            )}
            {rent && data.demandIncluded ? (
              <Card title="Rental applications" subtitle="Applications submitted in each period, and how many were approved.">
                <LineChart title="Applications and approvals" unit="COUNT" points={points}
                  series={[{ key: "applications", label: "Submitted" }, { key: "applicationsApproved", label: "Approved" }]} />
              </Card>
            ) : !rent ? (
              <Card title="Price reductions" subtitle="Asking-price cuts in each period.">
                <ColumnChart title="Price reductions" points={points} valueKey="priceCuts" label="Price cuts" />
              </Card>
            ) : null}
            {!rent && other && (
              <Card title="Sales and rental activity" subtitle="New listings in each period, for the same place and kind of home.">
                <LineChart title="New sale and rental listings" unit="COUNT"
                  points={points.map((p, i) => ({ label: p.label, values: { sale: p.values.newListings, rent: other.series[i]?.values.newListings } }))}
                  series={[{ key: "sale", label: "For sale" }, { key: "rent", label: "To rent" }]} />
              </Card>
            )}
          </div>

          <Groups title="By suburb" subtitle={city ? "Click a suburb to focus on it." : "Choose a city to compare its suburbs."} rows={data.bySuburb} rent={rent}
            demand={data.demandIncluded} onPick={(g) => city && setSuburb(g.label)}
            onList={(g) => setDrawer({ title: `${g.label}${g.city ? `, ${g.city}` : ""}`, params: { ...params, city: g.city ?? params.city, suburb: g.label } })} />
          <div className="grid lg:grid-cols-2 gap-4">
            <Groups title={rent ? "Supply by property type" : "By property type"} rows={data.byCategory} rent={rent} demand={data.demandIncluded} compact
              onList={(g) => setDrawer({ title: g.label, params: { ...params, category: g.key === "null" ? undefined : g.key } })} />
            <Groups title="By bedrooms" rows={data.byBedrooms} rent={rent} demand={data.demandIncluded} compact
              onList={(g) => setDrawer({ title: g.label, params: { ...params, bedrooms: g.key === "?" ? undefined : g.key === "5+" ? 5 : Number(g.key) } })} />
          </div>
          {!rent && data.byPriceBand.length > 0 && (
            <Groups title="By price band" subtitle="Live listings by asking price; sales by verified price." rows={data.byPriceBand} rent={false}
              demand={data.demandIncluded} compact />
          )}

          <Card title="Estimated rental yield" subtitle={`Median asking rent × 12 ÷ median asking sale price, for the same place and kind of home.
            Shown only where at least 5 rentals and 5 sales are listed. Based on asking prices, so treat it as an estimate.`}>
            <YieldTable data={data} />
            <div className="mt-3 text-xs text-gray-500 flex flex-wrap items-center gap-2">
              {data.yields.costRatioConfirmed
                ? <span>Net yield assumes {Math.round(data.yields.costRatio * 100)}% of rent goes on costs (rates, upkeep, management, empty months).</span>
                : <span className="inline-flex items-center gap-1 text-amber-700"><AlertTriangle className="w-3.5 h-3.5" aria-hidden /> Net yield is hidden until an administrator sets the cost assumption.</span>}
              {canConfigure && (
                <span className="inline-flex items-center gap-1.5 ml-auto">
                  <label htmlFor={`${type}-ratio`}>Costs as % of rent</label>
                  <input id={`${type}-ratio`} inputMode="decimal" value={ratioDraft} onChange={(e) => setRatioDraft(e.target.value)}
                    placeholder={String(Math.round(data.yields.costRatio * 100))} className="w-16 border border-gray-200 rounded-md px-2 py-1" />
                  <button type="button" onClick={saveRatio} className="text-brand-600 font-medium">Save</button>
                  {ratioMsg && <span className="text-gray-600">{ratioMsg}</span>}
                </span>
              )}
            </div>
          </Card>
        </div>
      )}

      {drawer && <ListingsDrawer type={type} title={drawer.title} params={drawer.params} onClose={() => setDrawer(null)} />}
    </div>
  );
}

function PriceInput({ label, value, onCommit }: { label: string; value: string; onCommit: (v: string) => void }) {
  // Remounted (via key) when the committed value changes, so the draft starts from it.
  const [draft, setDraft] = useState(value);
  return (
    <label className="flex items-center gap-1 border border-gray-200 rounded-lg px-2 py-1 bg-white text-sm">
      <span className="text-gray-400 text-xs">{label}</span>
      <input inputMode="numeric" value={draft} onChange={(e) => setDraft(e.target.value.replace(/[^0-9]/g, ""))}
        onBlur={() => onCommit(draft)} onKeyDown={(e) => e.key === "Enter" && onCommit(draft)} className="w-20 outline-none" aria-label={label} />
    </label>
  );
}

function Card({ title, subtitle, children }: { title: string; subtitle?: string; children: React.ReactNode }) {
  return (
    <section className="bg-white border border-gray-200 rounded-2xl p-4">
      <h3 className="text-sm font-semibold text-gray-900">{title}</h3>
      {subtitle && <p className="text-xs text-gray-500 mt-0.5 mb-3 leading-relaxed">{subtitle}</p>}
      {children}
    </section>
  );
}

function Stat({ k }: { k: MarketKpi }) {
  const up = (k.changePercent ?? 0) > 0;
  return (
    <article className="bg-white border border-gray-200 rounded-2xl p-4 flex flex-col gap-1 min-w-0" aria-label={k.label}>
      <header className="flex items-start justify-between gap-2">
        <h3 className="text-xs font-medium text-gray-500">{k.label}</h3>
        <span className="relative group">
          <button type="button" aria-label={`About ${k.label}: ${k.help}`} className="text-gray-300 hover:text-gray-500"><Info className="w-3.5 h-3.5" /></button>
          <span role="tooltip" className="invisible group-hover:visible group-focus-within:visible absolute right-0 top-5 z-20 w-60 rounded-lg bg-gray-900 text-white text-xs p-3">{k.help}</span>
        </span>
      </header>
      <p className="text-2xl font-semibold text-gray-900 tabular-nums">{formatValue(k.value, k.unit)}</p>
      {k.changePercent != null && (
        <p className="text-xs tabular-nums text-gray-600">
          <span className="font-semibold">{up ? "▲ +" : k.changePercent < 0 ? "▼ " : ""}{k.changePercent}%</span>
          <span className="text-gray-400"> since {k.note?.replace(/^vs /, "") ?? "the start"} ({formatValue(k.previous, k.unit)})</span>
        </p>
      )}
      <p className="text-[11px] text-gray-400 flex flex-wrap gap-x-2">
        {k.sampleSize != null && k.unit !== "COUNT" && k.unit !== "PERCENT" && <span>n = {k.sampleSize}</span>}
        {k.lowSample && k.value != null && <span className="text-amber-700 inline-flex items-center gap-1"><AlertTriangle className="w-3 h-3" aria-hidden /> Small sample</span>}
        {k.note && k.changePercent == null && <span>{k.note}</span>}
      </p>
    </article>
  );
}

function Groups({ title, subtitle, rows, rent, demand, compact, onPick, onList }: {
  title: string; subtitle?: string; rows: MarketGroup[]; rent: boolean; demand: boolean; compact?: boolean;
  onPick?: (g: MarketGroup) => void; onList?: (g: MarketGroup) => void;
}) {
  const max = Math.max(1, ...rows.map((r) => r.active));
  return (
    <Card title={title} subtitle={subtitle}>
      {rows.length === 0 ? <p className="text-sm text-gray-400">Nothing matches these filters.</p> : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="text-xs text-gray-500 text-left">
              <tr>
                <th scope="col" className="py-1.5 pr-2 font-medium">{title.replace("By ", "").replace("Supply by ", "")}</th>
                <th scope="col" className="py-1.5 px-2 font-medium w-1/4">Live listings</th>
                <th scope="col" className="py-1.5 px-2 font-medium text-right">Median asking</th>
                {!compact && <th scope="col" className="py-1.5 px-2 font-medium text-right">New</th>}
                {demand && !compact && <th scope="col" className="py-1.5 px-2 font-medium text-right">Enquiries + viewings per listing</th>}
                <th scope="col" className="py-1.5 px-2 font-medium text-right">{rent ? "Median agreed rent" : "Verified sales"}</th>
                {onList && <th scope="col" className="py-1.5 pl-2"><span className="sr-only">Listings</span></th>}
              </tr>
            </thead>
            <tbody>
              {rows.map((g) => (
                <tr key={g.key} className="border-t border-gray-100">
                  <td className="py-1.5 pr-2">
                    {onPick ? <button type="button" onClick={() => onPick(g)} className="text-gray-900 hover:text-brand-600 text-left">{g.label}</button>
                      : <span className="text-gray-900">{g.label}</span>}
                    {g.city && !compact && <span className="text-gray-400 text-xs"> · {g.city}</span>}
                  </td>
                  <td className="py-1.5 px-2">
                    <span className="flex items-center gap-2">
                      <span className="h-2 rounded-r-sm" style={{ width: `${(g.active / max) * 100}%`, minWidth: g.active ? 3 : 0, background: "#257de0" }} aria-hidden />
                      <span className="tabular-nums text-gray-700 text-xs">{g.active}</span>
                    </span>
                  </td>
                  <td className="py-1.5 px-2 text-right tabular-nums">
                    {formatValue(g.askingMedian, "USD")}
                    {g.askingN > 0 && g.lowSample && <span className="ml-1 text-amber-700" title={`Only ${g.askingN} listing(s)`}>*</span>}
                  </td>
                  {!compact && <td className="py-1.5 px-2 text-right tabular-nums">{g.newListings}</td>}
                  {demand && !compact && <td className="py-1.5 px-2 text-right tabular-nums">{g.demandPerListing ?? "—"}</td>}
                  <td className="py-1.5 px-2 text-right tabular-nums">
                    {rent ? (g.transactionN ? `${formatValue(g.transactionMedian, "USD")} (${g.transactionN})` : "—")
                      : (g.transactions ? `${g.transactions} · ${formatValue(g.transactionMedian, "USD")}` : "—")}
                  </td>
                  {onList && <td className="py-1.5 pl-2 text-right"><button type="button" onClick={() => onList(g)} className="text-xs text-brand-600 hover:underline">Listings</button></td>}
                </tr>
              ))}
            </tbody>
          </table>
          {rows.some((r) => r.lowSample && r.askingN > 0) && <p className="text-[11px] text-gray-400 mt-2">* Fewer than 5 listings: treat the median with care.</p>}
        </div>
      )}
    </Card>
  );
}

function YieldTable({ data }: { data: MarketDashboardData }) {
  const rows = data.yields.bySuburb;
  const withYield = rows.filter((r) => r.grossYield != null);
  const max = Math.max(1, ...withYield.map((r) => r.grossYield ?? 0));
  const o = data.yields.overall;
  return (
    <div className="space-y-3">
      <p className="text-sm text-gray-700">
        Overall: {o.grossYield != null ? <strong className="tabular-nums">{o.grossYield}% gross{o.netYield != null ? ` · ${o.netYield}% net` : ""}</strong>
          : <span className="text-gray-500">{o.note}</span>}
        <span className="text-xs text-gray-400"> (rent {formatValue(o.rentMedian, "USD")}, n={o.rentN}; sale {formatValue(o.saleMedian, "USD")}, n={o.saleN})</span>
      </p>
      {rows.length === 0 ? <p className="text-sm text-gray-400">No suburb has both rentals and sales listed with these filters.</p> : (
        <table className="w-full text-sm">
          <thead className="text-xs text-gray-500 text-left">
            <tr><th scope="col" className="py-1.5 font-medium">Suburb</th><th scope="col" className="py-1.5 px-2 font-medium w-1/3">Gross yield</th>
              <th scope="col" className="py-1.5 px-2 font-medium text-right">Net</th><th scope="col" className="py-1.5 px-2 font-medium text-right">Median rent (n)</th>
              <th scope="col" className="py-1.5 pl-2 font-medium text-right">Median sale price (n)</th></tr>
          </thead>
          <tbody>
            {rows.slice(0, 20).map((r) => (
              <tr key={`${r.suburb}-${r.city}`} className="border-t border-gray-100">
                <td className="py-1.5">{r.suburb}<span className="text-xs text-gray-400"> · {r.city}</span></td>
                <td className="py-1.5 px-2">
                  {r.grossYield != null ? (
                    <span className="flex items-center gap-2">
                      <span className="h-2 rounded-r-sm" style={{ width: `${(r.grossYield / max) * 100}%`, background: "#257de0" }} aria-hidden />
                      <span className="tabular-nums text-xs">{r.grossYield}%</span>
                    </span>
                  ) : <span className="text-xs text-gray-400">{r.note}</span>}
                </td>
                <td className="py-1.5 px-2 text-right tabular-nums">{r.netYield != null ? `${r.netYield}%` : "—"}</td>
                <td className="py-1.5 px-2 text-right tabular-nums">{formatValue(r.rentMedian, "USD")} ({r.rentN})</td>
                <td className="py-1.5 pl-2 text-right tabular-nums">{formatValue(r.saleMedian, "USD")} ({r.saleN})</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

function ListingsDrawer({ type, title, params, onClose }: { type: MarketType; title: string; params: MarketParams; onClose: () => void }) {
  const { token } = useAuth();
  const [rows, setRows] = useState<MarketListing[] | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    if (!token) return;
    marketDashboard.listings(token, type, params).then(setRows).catch((e) => setError(e instanceof Error ? e.message : "Couldn't load."));
  }, [token, type, params]);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);
  return (
    <div className="fixed inset-0 z-50 flex justify-end" role="dialog" aria-modal="true" aria-label={`Live listings: ${title}`}>
      <button className="absolute inset-0 bg-gray-900/30" aria-label="Close" onClick={onClose} />
      <aside className="relative w-full max-w-lg h-full bg-white shadow-2xl flex flex-col">
        <header className="px-5 py-4 border-b border-gray-200 flex items-start justify-between">
          <div><h2 className="font-semibold text-gray-900">Live listings: {title}</h2><p className="text-xs text-gray-500">As of the latest daily snapshot, with the page&apos;s filters.</p></div>
          <button onClick={onClose} aria-label="Close listings" className="text-gray-400 hover:text-gray-700"><X className="w-5 h-5" /></button>
        </header>
        <div className="flex-1 overflow-y-auto p-5">
          {error && <p className="text-sm text-red-700">{error}</p>}
          {!rows && !error && <p className="flex items-center gap-2 text-sm text-gray-500"><Loader2 className="w-4 h-4 animate-spin" /> Loading…</p>}
          {rows && rows.length === 0 && <p className="text-sm text-gray-500">No live listings.</p>}
          {rows && rows.length > 0 && (
            <ul className="divide-y divide-gray-100 border border-gray-200 rounded-xl">
              {rows.map((r) => (
                <li key={r.id} className="px-4 py-2.5 flex justify-between gap-3 text-sm">
                  <div className="min-w-0">
                    <Link href={`/properties/${r.id}`} className="font-medium text-gray-900 hover:text-brand-600 truncate block">{r.title ?? `Listing #${r.id}`}</Link>
                    <p className="text-xs text-gray-500">{r.suburb}, {r.city}{r.bedrooms != null ? ` · ${r.bedrooms} bed` : ""}{r.verification === "VERIFIED" ? " · verified" : ""}</p>
                  </div>
                  <span className="tabular-nums shrink-0">{formatValue(r.price, "USD")}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </aside>
    </div>
  );
}
