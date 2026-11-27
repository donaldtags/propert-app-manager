"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { AlertTriangle, Download, Info, Trash2 } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { revenueAnalytics, type MarketGrain, type RevenueData, type RevenueKpi, type RevenueSlice } from "@/lib/api";
import AlertBanner from "@/components/AlertBanner";
import LineChart from "./charts/LineChart";
import { formatValue } from "./format";

const RANGES = [
  { key: "month", label: "This month" },
  { key: "3m", label: "3 months" },
  { key: "12m", label: "12 months" },
  { key: "ytd", label: "This year" },
] as const;

const GROUPS: { key: RevenueKpi["group"]; title: string; subtitle: string }[] = [
  { key: "CASH", title: "Cash collected", subtitle: "Money received, by payment date" },
  { key: "RECOGNISED", title: "Recognised revenue", subtitle: "Earned over what was paid for" },
  { key: "OUTSTANDING", title: "Outstanding", subtitle: "Owed or not collected" },
  { key: "ESTIMATED", title: "Estimated", subtitle: "Forward-looking; not cash" },
  { key: "UNIT", title: "Unit economics", subtitle: "Churn, lifetime value, acquisition cost" },
];

function iso(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function rangeDates(key: string) {
  const to = new Date();
  const from = new Date(to.getFullYear(), to.getMonth(), 1);
  if (key === "3m") from.setMonth(from.getMonth() - 2);
  if (key === "12m") from.setMonth(from.getMonth() - 11);
  if (key === "ytd") from.setMonth(0, 1);
  return { from: iso(from), to: iso(to) };
}

/** A signed MRR movement; zero is shown plainly (no "+$0"), and a missing period as a dash. */
function movement(v: number | undefined | null, sign: "+" | "−") {
  if (v == null) return "—";
  return v === 0 ? "$0" : `${sign}${formatValue(v, "USD")}`;
}

function show(k: RevenueKpi) {
  if (k.value == null) return "—";
  if (k.unit === "MONTHS") return `${k.value} months`;
  return formatValue(k.value, k.unit);
}

/**
 * EstateX's own revenue. Cash, recognised revenue, what's outstanding and what's estimated are
 * always shown apart, never added together; client money passing through is never revenue.
 */
export default function RevenueDashboard({ canConfigure }: { canConfigure: boolean }) {
  const { token } = useAuth();
  const [range, setRange] = useState<string>("12m");
  const [grain, setGrain] = useState<MarketGrain | "">("");
  const [data, setData] = useState<RevenueData | null>(null);
  const [error, setError] = useState("");
  const [loadedKey, setLoadedKey] = useState("");
  const [spend, setSpend] = useState({ month: iso(new Date()).slice(0, 7), channel: "", amount: "", note: "" });
  const [share, setShare] = useState("");
  const [msg, setMsg] = useState("");

  const params = useMemo(() => ({ ...rangeDates(range), grain: grain || undefined }), [range, grain]);
  const key = JSON.stringify(params);
  const load = useCallback(() => {
    if (!token) return;
    revenueAnalytics.get(token, params)
      .then((d) => { setData(d); setError(""); })
      .catch((e) => setError(e instanceof Error ? e.message : "Couldn't load revenue."))
      .finally(() => setLoadedKey(key));
  }, [token, params, key]);
  useEffect(load, [load]);
  const loading = loadedKey !== key;

  const addSpend = async () => {
    if (!token) return;
    const amount = Number(spend.amount);
    if (!spend.channel.trim() || !(amount >= 0)) { setMsg("Give a channel and an amount."); return; }
    try {
      await revenueAnalytics.addSpend(token, { month: `${spend.month}-01`, channel: spend.channel.trim(), amount, note: spend.note || undefined });
      setSpend({ ...spend, channel: "", amount: "", note: "" });
      setMsg("Spend recorded.");
      load();
    } catch (e) {
      setMsg(e instanceof Error ? e.message : "Couldn't save.");
    }
  };
  const removeSpend = async (id: number) => {
    if (!token || !window.confirm("Delete this spend entry?")) return;
    await revenueAnalytics.deleteSpend(token, id).catch(() => {});
    load();
  };
  const saveShare = async () => {
    if (!token) return;
    try {
      await revenueAnalytics.setCommissionShare(token, Number(share));
      setMsg("Commission share saved.");
      setShare("");
      load();
    } catch (e) {
      setMsg(e instanceof Error ? e.message : "Couldn't save.");
    }
  };

  const exportCsv = () => {
    if (!data) return;
    const esc = (v: unknown) => { const s = v == null ? "" : String(v); return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s; };
    const cols = Array.from(new Set(data.series.flatMap((p) => Object.keys(p.values))));
    const lines: unknown[][] = [["EstateX revenue"], ["Generated", new Date().toISOString()], ["Range", data.from, data.to, data.grain],
      ["Notes", ...data.notes], [], ["Group", "KPI", "Value", "Note", "Definition"],
      ...data.kpis.map((k) => [k.group, k.label, k.value, k.note, k.help]), [], ["Period", ...cols],
      ...data.series.map((p) => [p.label, ...cols.map((c) => p.values[c])]), [], ["Product", "Cash", "Recognised", "Payments"],
      ...data.byProduct.map((s) => [s.label, s.cash, s.recognised, s.payments])];
    const blob = new Blob([lines.map((l) => l.map(esc).join(",")).join("\n")], { type: "text/csv;charset=utf-8" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = `estatex-revenue-${iso(new Date())}.csv`;
    a.click();
    URL.revokeObjectURL(a.href);
  };

  const warning = data?.notes.find((n) => n.includes("without collecting money"));
  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-xl font-semibold text-gray-900 tracking-tight">Revenue</h2>
          <p className="text-sm text-gray-500 mt-0.5">What EstateX earns from its own products. Rent, deposits and other client money are never counted.</p>
        </div>
        <button type="button" onClick={exportCsv} disabled={!data}
          className="inline-flex items-center gap-1.5 text-sm font-medium text-gray-700 border border-gray-200 bg-white rounded-lg px-3 py-2 disabled:opacity-50">
          <Download className="w-4 h-4" aria-hidden /> Export
        </button>
      </div>
      <div className="flex flex-wrap items-center gap-2 bg-white border border-gray-200 rounded-xl p-2">
        <div role="radiogroup" aria-label="Date range" className="flex rounded-lg bg-gray-100 p-0.5">
          {RANGES.map((r) => (
            <button key={r.key} type="button" role="radio" aria-checked={range === r.key} onClick={() => setRange(r.key)}
              className={`px-3 py-1.5 text-sm rounded-md ${range === r.key ? "bg-white shadow-sm font-medium text-gray-900" : "text-gray-600"}`}>
              {r.label}
            </button>
          ))}
        </div>
        <label className="sr-only" htmlFor="rev-grain">Group by</label>
        <select id="rev-grain" value={grain} onChange={(e) => setGrain(e.target.value as MarketGrain | "")}
          className="border border-gray-200 rounded-lg px-2.5 py-1.5 bg-white text-sm">
          <option value="">Group automatically</option><option value="WEEK">By week</option><option value="MONTH">By month</option>
        </select>
      </div>
      {error && <AlertBanner variant="error">{error}</AlertBanner>}
      {warning && <AlertBanner variant="warning">{warning}</AlertBanner>}
      {msg && <AlertBanner variant="success">{msg}</AlertBanner>}

      {data && (
        <div className={`space-y-6 ${loading ? "opacity-60" : ""}`}>
          <div className="grid lg:grid-cols-4 gap-3">
            {GROUPS.slice(0, 4).map((g) => <KpiGroup key={g.key} group={g} kpis={data.kpis.filter((k) => k.group === g.key)} />)}
          </div>
          <KpiGroup group={GROUPS[4]} kpis={data.kpis.filter((k) => k.group === "UNIT")} wide />

          <div className="grid lg:grid-cols-2 gap-4">
            <Card title="Cash collected and revenue recognised" subtitle="Cash is when money arrived; recognised is when it was earned. A subscription paid on the 20th is earned over the next 30 days.">
              <LineChart title="Cash and recognised revenue" unit="USD" points={data.series.map((p) => ({ label: p.label, values: p.values }))}
                series={[{ key: "cash", label: "Cash collected" }, { key: "recognised", label: "Recognised" }]} />
            </Card>
            <Card title="Monthly recurring revenue" subtitle="Monthly price of active paid subscriptions at the end of each period. Blank before subscription history began.">
              <LineChart title="MRR" unit="USD" points={data.series.map((p) => ({ label: p.label, values: p.values }))} series={[{ key: "mrr", label: "MRR" }]} />
            </Card>
          </div>

          <Card title="MRR movements" subtitle="Why MRR changed in each period: new subscribers, upgrades, downgrades and cancellations.">
            <div className="overflow-x-auto">
              <table className="w-full text-sm tabular-nums">
                <thead className="text-xs text-gray-500 text-left"><tr>
                  {["Period", "New", "Upgrades", "Downgrades", "Cancelled", "Net change", "Paying", "Churn"].map((h, i) => (
                    <th key={h} scope="col" className={`py-1.5 font-medium ${i ? "text-right px-2" : ""}`}>{h}</th>
                  ))}
                </tr></thead>
                <tbody>
                  {data.series.map((p) => {
                    const v = p.values;
                    const net = v.mrr == null ? null : (v.newMrr ?? 0) + (v.expansionMrr ?? 0) - (v.contractionMrr ?? 0) - (v.churnedMrr ?? 0);
                    return (
                      <tr key={p.label} className="border-t border-gray-100">
                        <td className="py-1.5">{p.label}</td>
                        <td className="py-1.5 px-2 text-right">{movement(v.newMrr, "+")}</td>
                        <td className="py-1.5 px-2 text-right">{movement(v.expansionMrr, "+")}</td>
                        <td className="py-1.5 px-2 text-right">{movement(v.contractionMrr, "−")}</td>
                        <td className="py-1.5 px-2 text-right">{movement(v.churnedMrr, "−")}</td>
                        <td className="py-1.5 px-2 text-right font-medium">{net == null ? "—" : movement(Math.abs(net), net >= 0 ? "+" : "−")}</td>
                        <td className="py-1.5 px-2 text-right">{v.payingSubscribers ?? "—"}</td>
                        <td className="py-1.5 px-2 text-right">{v.churnPercent != null ? `${v.churnPercent}%` : "—"}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </Card>

          <div className="grid lg:grid-cols-3 gap-4">
            <Slices title="By product" rows={data.byProduct} />
            <Slices title="By customer" rows={data.byCategory} />
            <Slices title="By location" rows={data.byLocation} note="Featured listings by the listing's city; subscriptions by the subscriber's city." />
          </div>

          <div className="grid lg:grid-cols-2 gap-4">
            <Card title="Marketing spend" subtitle="EstateX has no other record of marketing spend, so acquisition cost uses what's entered here.">
              <div className="flex flex-wrap items-end gap-2 mb-3 text-sm">
                <label className="text-gray-600">Month<br /><input type="month" value={spend.month} onChange={(e) => setSpend({ ...spend, month: e.target.value })}
                  className="border border-gray-200 rounded-lg px-2 py-1.5" /></label>
                <label className="text-gray-600">Channel<br /><input value={spend.channel} onChange={(e) => setSpend({ ...spend, channel: e.target.value })}
                  placeholder="e.g. Facebook ads" className="border border-gray-200 rounded-lg px-2 py-1.5 w-36" /></label>
                <label className="text-gray-600">Amount (USD)<br /><input inputMode="decimal" value={spend.amount} onChange={(e) => setSpend({ ...spend, amount: e.target.value })}
                  className="border border-gray-200 rounded-lg px-2 py-1.5 w-24" /></label>
                <button type="button" onClick={addSpend} className="bg-brand-600 hover:bg-brand-700 text-white font-semibold px-3 py-1.5 rounded-lg">Add</button>
              </div>
              {data.spend.length === 0 ? <p className="text-sm text-gray-400">No spend entered for this range.</p> : (
                <table className="w-full text-sm">
                  <tbody>
                    {data.spend.map((s) => (
                      <tr key={s.id} className="border-t border-gray-100">
                        <td className="py-1.5">{new Date(s.month).toLocaleDateString(undefined, { month: "short", year: "numeric" })}</td>
                        <td className="py-1.5 px-2">{s.channel}</td>
                        <td className="py-1.5 px-2 text-right tabular-nums">{formatValue(s.amount, "USD")}</td>
                        <td className="py-1.5 pl-2 text-right">
                          <button type="button" onClick={() => removeSpend(s.id)} aria-label={`Delete ${s.channel} spend`} className="text-gray-400 hover:text-red-600">
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </Card>
            <Card title="Commission share" subtitle="An optional EstateX share of the commission agencies receive. Shown as an estimate only: agencies aren't billed for it.">
              <p className="text-sm text-gray-700">
                Current rate: <strong>{data.commissionSharePercent}%</strong>{Number(data.commissionSharePercent) === 0 && " (off)"}
              </p>
              {canConfigure && (
                <div className="flex items-end gap-2 mt-3 text-sm">
                  <label className="text-gray-600">New rate (%)<br />
                    <input inputMode="decimal" value={share} onChange={(e) => setShare(e.target.value)} className="border border-gray-200 rounded-lg px-2 py-1.5 w-20" />
                  </label>
                  <button type="button" onClick={saveShare} disabled={share === ""} className="bg-brand-600 text-white font-semibold px-3 py-1.5 rounded-lg disabled:opacity-50">Save</button>
                </div>
              )}
              <p className="text-xs text-gray-500 mt-3 flex items-start gap-1.5"><AlertTriangle className="w-3.5 h-3.5 mt-0.5 text-amber-600" aria-hidden />
                Charging agencies a share is a commercial change they need to agree to before it&apos;s billed.</p>
            </Card>
          </div>

          <ul className="space-y-1">
            {data.notes.filter((n) => n !== warning).map((n) => (
              <li key={n} className="text-xs text-gray-500 flex items-start gap-1.5"><Info className="w-3.5 h-3.5 mt-0.5 shrink-0" aria-hidden /> {n}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

function KpiGroup({ group, kpis, wide }: { group: (typeof GROUPS)[number]; kpis: RevenueKpi[]; wide?: boolean }) {
  return (
    <section className="bg-white border border-gray-200 rounded-2xl p-4" aria-label={group.title}>
      <h3 className="text-sm font-semibold text-gray-900">{group.title}</h3>
      <p className="text-xs text-gray-500 mb-3">{group.subtitle}</p>
      <dl className={wide ? "grid sm:grid-cols-2 lg:grid-cols-4 gap-4" : "space-y-3"}>
        {kpis.map((k) => (
          <div key={k.key}>
            <dt className="text-xs text-gray-500 flex items-center gap-1">
              {k.label}
              <span className="relative group">
                <button type="button" aria-label={`About ${k.label}: ${k.help}`} className="text-gray-300 hover:text-gray-500"><Info className="w-3 h-3" /></button>
                <span role="tooltip" className="invisible group-hover:visible group-focus-within:visible absolute left-0 top-4 z-20 w-60 rounded-lg bg-gray-900 text-white text-xs p-2.5">{k.help}</span>
              </span>
            </dt>
            <dd className="text-xl font-semibold text-gray-900 tabular-nums">{show(k)}</dd>
            {k.note && <dd className="text-[11px] text-gray-400">{k.note}</dd>}
          </div>
        ))}
      </dl>
    </section>
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

function Slices({ title, rows, note }: { title: string; rows: RevenueSlice[]; note?: string }) {
  const max = Math.max(1, ...rows.map((r) => r.recognised));
  return (
    <Card title={title} subtitle={note}>
      {rows.length === 0 ? <p className="text-sm text-gray-400">No revenue in this range.</p> : (
        <table className="w-full text-sm">
          <thead className="text-xs text-gray-500 text-left"><tr>
            <th scope="col" className="py-1.5 font-medium">{title.replace("By ", "")}</th>
            <th scope="col" className="py-1.5 px-2 font-medium w-2/5">Recognised</th>
            <th scope="col" className="py-1.5 pl-2 font-medium text-right">Cash</th>
          </tr></thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.key} className="border-t border-gray-100">
                <td className="py-1.5">{r.label}</td>
                <td className="py-1.5 px-2">
                  <span className="flex items-center gap-2">
                    <span className="h-2 rounded-r-sm" style={{ width: `${(r.recognised / max) * 100}%`, minWidth: r.recognised ? 3 : 0, background: "#257de0" }} aria-hidden />
                    <span className="tabular-nums text-xs">{formatValue(r.recognised, "USD")}</span>
                  </span>
                </td>
                <td className="py-1.5 pl-2 text-right tabular-nums">{formatValue(r.cash, "USD")}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Card>
  );
}
