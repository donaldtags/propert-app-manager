"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { AlertTriangle, Info, SearchX } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { demandAnalytics, type DemandCount, type DemandData, type DemandListing } from "@/lib/api";
import { useZimbabweLocations } from "@/lib/locations";
import AlertBanner from "@/components/AlertBanner";
import { formatValue } from "./format";

const RANGES = [
  { key: 7, label: "7 days" }, { key: 30, label: "30 days" }, { key: 90, label: "90 days" }, { key: 365, label: "12 months" },
];
/** Validated ordinal blue steps for funnel stages (lightest still clears 2:1 on white). */
const STAGES = ["#84b9f3", "#519ced", "#338ced", "#257de0", "#1b64be", "#0f4a96"];

function iso(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

/**
 * How people use the marketplace: what they search for, what they open, save and enquire
 * about, and where interest runs ahead of supply. All of it is interest, not confirmed demand.
 */
export default function DemandAnalytics() {
  const { token } = useAuth();
  const locations = useZimbabweLocations();
  const [days, setDays] = useState(30);
  const [city, setCity] = useState("");
  const [data, setData] = useState<DemandData | null>(null);
  const [error, setError] = useState("");

  const load = useCallback(() => {
    if (!token) return;
    const to = new Date();
    const from = new Date(to);
    from.setDate(from.getDate() - (days - 1));
    demandAnalytics.get(token, { from: iso(from), to: iso(to), city: city || undefined })
      .then((d) => { setData(d); setError(""); })
      .catch((e) => setError(e instanceof Error ? e.message : "Couldn't load demand figures."));
  }, [token, days, city]);
  useEffect(load, [load]);

  const abandoned = data?.ledToViewPercent == null ? null : Math.round((100 - data.ledToViewPercent) * 10) / 10;
  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-semibold text-gray-900 tracking-tight">Customer demand</h2>
        <p className="text-sm text-gray-500 mt-0.5">What people look for, what they open and save, and where interest runs ahead of supply.</p>
      </div>
      <div className="flex flex-wrap items-center gap-2 bg-white border border-gray-200 rounded-xl p-2">
        <div role="radiogroup" aria-label="Period" className="flex rounded-lg bg-gray-100 p-0.5">
          {RANGES.map((r) => (
            <button key={r.key} type="button" role="radio" aria-checked={days === r.key} onClick={() => setDays(r.key)}
              className={`px-3 py-1.5 text-sm rounded-md ${days === r.key ? "bg-white shadow-sm font-medium text-gray-900" : "text-gray-600"}`}>
              Last {r.label}
            </button>
          ))}
        </div>
        <label className="sr-only" htmlFor="demand-city">City</label>
        <select id="demand-city" value={city} onChange={(e) => setCity(e.target.value)} className="border border-gray-200 rounded-lg px-2.5 py-1.5 bg-white text-sm ml-auto">
          <option value="">All locations</option>
          {locations.cities.map((c) => <option key={c} value={c}>{c}</option>)}
        </select>
      </div>
      {error && <AlertBanner variant="error">{error}</AlertBanner>}
      {data?.notes.map((n) => <p key={n} className="text-xs text-gray-500 -mt-3 flex items-start gap-1.5"><Info className="w-3.5 h-3.5 mt-0.5 shrink-0" aria-hidden /> {n}</p>)}

      {data && (
        <>
          <section aria-label="Key figures" className="grid grid-cols-2 lg:grid-cols-6 gap-3">
            <Tile label="Searches" value={data.searches.toLocaleString()} note={data.searchesAnyType ? `${data.searchesAnyType} without a rent/sale choice` : undefined} />
            <Tile label="Found nothing" value={data.noResultsPercent == null ? "—" : `${data.noResultsPercent}%`} note="of searches returned no listings" />
            <Tile label="Search abandonment" value={abandoned == null ? "—" : `${abandoned}%`} note="of searches weren't followed by opening a listing within 30 minutes" />
            <Tile label="Listing views" value={data.views.toLocaleString()} />
            <Tile label="Saves" value={data.saves.toLocaleString()} />
            <Tile label="Searches per live listing" value={`${data.rentSearchesPerListing ?? "—"} rent · ${data.saleSearchesPerListing ?? "—"} sale`} />
          </section>

          <div className="grid lg:grid-cols-2 gap-4">
            {data.funnels.map((f) => (
              <Card key={f.type} title={f.type === "RENT" ? "Rental funnel" : "Buyer funnel"}
                subtitle="How many of each step happened in the period, and each step as a share of the one before. Not the same people moving through.">
                <ol className="space-y-2">
                  {f.steps.map((s, i) => {
                    const max = Math.max(1, f.steps[0].count, ...f.steps.map((x) => x.count));
                    return (
                      <li key={s.key} className="grid grid-cols-[9rem_1fr_auto] items-center gap-3 text-sm">
                        <span className="text-gray-700">{s.label}</span>
                        <span className="h-5 rounded-r" style={{ width: `${Math.max(1, (s.count / max) * 100)}%`, background: STAGES[i] }} aria-hidden />
                        <span className="tabular-nums text-gray-900 text-right w-28">
                          {s.count.toLocaleString()}
                          {s.fromPrevious != null && <span className="text-xs text-gray-500"> · {s.fromPrevious}%</span>}
                        </span>
                      </li>
                    );
                  })}
                </ol>
              </Card>
            ))}
          </div>

          <Card title="Possible supply gaps" subtitle={`Searches for a specific kind of home (10 or more in the period) where nothing matching is live, or where searches per listing are at least twice the market's. Search activity shows interest, not confirmed demand.`}>
            {data.gaps.length === 0 ? (
              <p className="text-sm text-gray-500">No gaps with enough searches to be meaningful in this period.</p>
            ) : (
              <ul className="space-y-2">
                {data.gaps.slice(0, 12).map((g, i) => (
                  <li key={i} className="flex items-start gap-2 text-sm border border-amber-200 bg-amber-50/60 rounded-xl px-3 py-2">
                    <AlertTriangle className="w-4 h-4 text-amber-700 mt-0.5 shrink-0" aria-hidden />
                    <span className="text-gray-800">{g.explanation}{g.noResults > 0 && <span className="text-gray-500"> {g.noResults} of those searches found nothing.</span>}</span>
                  </li>
                ))}
              </ul>
            )}
          </Card>

          <div className="grid lg:grid-cols-2 gap-4">
            <CountTable title="Most searched places" rows={data.topSuburbs} showSupply supplyLabel="Live listings (all types)" />
            <CountTable title="Most searched property types" rows={data.topCategories} showSupply supplyLabel="Live listings" />
            <CountTable title="Bedrooms searched for" rows={data.bedrooms} showSupply supplyLabel="Live listings" />
            <div className="space-y-4">
              <CountTable title="Rent searched for (monthly)" rows={data.rentPriceBands} />
              <CountTable title="Price searched for (sale)" rows={data.salePriceBands} />
            </div>
            <ListingTable title="Most viewed listings" rows={data.mostViewed} />
            <ListingTable title="Most saved listings" rows={data.mostSaved} />
          </div>

          <Card title="Response times" subtitle="Median time from a request to an answer, for requests made in the period.">
            <dl className="grid sm:grid-cols-2 lg:grid-cols-4 gap-3">
              {data.responseTimes.map((r) => (
                <div key={r.key} className="border border-gray-200 rounded-xl p-3">
                  <dt className="text-xs text-gray-500">{r.label}</dt>
                  <dd className="text-lg font-semibold text-gray-900 tabular-nums">{r.medianHours == null ? "—" : r.medianHours < 48 ? `${r.medianHours} h` : `${Math.round(r.medianHours / 24 * 10) / 10} days`}</dd>
                  <dd className="text-[11px] text-gray-400">{r.n > 0 ? `n = ${r.n}` : ""}{r.note ? `${r.n > 0 ? " · " : ""}${r.note}` : ""}</dd>
                </div>
              ))}
            </dl>
          </Card>
        </>
      )}
    </div>
  );
}

function Tile({ label, value, note }: { label: string; value: string; note?: string }) {
  return (
    <article className="bg-white border border-gray-200 rounded-2xl p-4" aria-label={label}>
      <h3 className="text-xs font-medium text-gray-500">{label}</h3>
      <p className="text-2xl font-semibold text-gray-900 tabular-nums mt-1">{value}</p>
      {note && <p className="text-[11px] text-gray-400 mt-1">{note}</p>}
    </article>
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

function CountTable({ title, rows, showSupply, supplyLabel }: { title: string; rows: DemandCount[]; showSupply?: boolean; supplyLabel?: string }) {
  const max = Math.max(1, ...rows.map((r) => r.searches));
  return (
    <Card title={title}>
      {rows.length === 0 ? <p className="text-sm text-gray-400">No searches with this detail in the period.</p> : (
        <table className="w-full text-sm">
          <thead className="text-xs text-gray-500 text-left">
            <tr>
              <th scope="col" className="py-1.5 font-medium">{title.includes("place") ? "Place" : "Searched for"}</th>
              <th scope="col" className="py-1.5 px-2 font-medium w-1/3">Searches</th>
              <th scope="col" className="py-1.5 px-2 font-medium text-right"><SearchX className="w-3.5 h-3.5 inline" aria-label="Found nothing" /></th>
              {showSupply && <th scope="col" className="py-1.5 px-2 font-medium text-right">{supplyLabel}</th>}
            </tr>
          </thead>
          <tbody>
            {rows.slice(0, 12).map((r) => (
              <tr key={r.key} className="border-t border-gray-100">
                <td className="py-1.5">{r.label}{r.city && <span className="text-xs text-gray-400"> · {r.city}</span>}</td>
                <td className="py-1.5 px-2">
                  <span className="flex items-center gap-2">
                    <span className="h-2 rounded-r-sm" style={{ width: `${(r.searches / max) * 100}%`, background: "#257de0" }} aria-hidden />
                    <span className="tabular-nums text-xs text-gray-700">{r.searches}</span>
                  </span>
                </td>
                <td className="py-1.5 px-2 text-right tabular-nums text-xs text-gray-600">{r.noResults}</td>
                {showSupply && <td className="py-1.5 px-2 text-right tabular-nums">{r.liveListings ?? "—"}</td>}
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Card>
  );
}

function ListingTable({ title, rows }: { title: string; rows: DemandListing[] }) {
  return (
    <Card title={title}>
      {rows.length === 0 ? <p className="text-sm text-gray-400">None in the period.</p> : (
        <table className="w-full text-sm">
          <thead className="text-xs text-gray-500 text-left">
            <tr><th scope="col" className="py-1.5 font-medium">Listing</th><th scope="col" className="py-1.5 px-2 font-medium text-right">Views</th>
              <th scope="col" className="py-1.5 px-2 font-medium text-right">Saves</th><th scope="col" className="py-1.5 pl-2 font-medium text-right">Enquiries</th></tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id} className="border-t border-gray-100">
                <td className="py-1.5">
                  <Link href={`/properties/${r.id}`} className="hover:text-brand-600">{r.title}</Link>
                  <span className="block text-xs text-gray-400">{[r.suburb, r.city].filter(Boolean).join(", ")}{r.price != null ? ` · ${formatValue(r.price, "USD")}` : ""}</span>
                </td>
                <td className="py-1.5 px-2 text-right tabular-nums">{r.views}</td>
                <td className="py-1.5 px-2 text-right tabular-nums">{r.saves}</td>
                <td className="py-1.5 pl-2 text-right tabular-nums">{r.enquiries}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Card>
  );
}
