"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  AlertOctagon, AlertTriangle, ArrowDownRight, ArrowUpRight, Bell, CheckCircle2, Info, Lightbulb, ShieldAlert, Sparkles, TrendingUp,
} from "lucide-react";
import { useAuth } from "@/lib/auth";
import {
  insightsApi, type AiInsightItem, type AlertPreferenceView, type AlertRuleView, type AlertSeverity, type AnalyticsAlertView,
  type InsightsData, type MarketObservationView,
} from "@/lib/api";

type Grain = "WEEK" | "MONTH";

const METHOD: Record<MarketObservationView["method"], string> = {
  POISSON_RATE: "Poisson rate test",
  MEDIAN_SHIFT: "Median z-test (approximate)",
  SHARE_GAP: "Share-gap z-test",
  THRESHOLD: "Threshold — not a significance test",
  DESCRIPTIVE: "Plain count — no test needed",
};

const SECTION: Record<AiInsightItem["section"], { title: string; Icon: typeof TrendingUp }> = {
  CHANGE: { title: "What changed", Icon: TrendingUp },
  OPPORTUNITY: { title: "Opportunities", Icon: Lightbulb },
  RISK: { title: "Risks to watch", Icon: ShieldAlert },
};

const SEVERITY: Record<AlertSeverity, { label: string; Icon: typeof Info; cls: string }> = {
  INFO: { label: "Info", Icon: Info, cls: "text-sky-800 bg-sky-50 border-sky-200" },
  WARNING: { label: "Warning", Icon: AlertTriangle, cls: "text-amber-900 bg-amber-50 border-amber-200" },
  CRITICAL: { label: "Critical", Icon: AlertOctagon, cls: "text-red-800 bg-red-50 border-red-200" },
};

function when(iso?: string | null) {
  return iso ? new Date(iso).toLocaleString(undefined, { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }) : "—";
}

function periodLabel(start: string | null | undefined, grain: Grain) {
  if (!start) return "—";
  const d = new Date(start);
  return grain === "WEEK"
    ? `Week of ${d.toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" })}`
    : d.toLocaleDateString(undefined, { month: "long", year: "numeric" });
}

/** The change, always with a sign and an arrow, so direction never rests on colour alone. */
function Change({ o }: { o: MarketObservationView }) {
  if (o.method === "DESCRIPTIVE") {
    return <span className="text-sm font-semibold text-gray-900">{o.changePct != null ? `${Math.round(o.changePct)}% of supply` : ""}</span>;
  }
  if (o.method === "SHARE_GAP") {
    return (
      <span className="text-sm font-semibold text-gray-900">
        {Math.round(o.currentValue ?? 0)}% of demand · {Math.round(o.previousValue ?? 0)}% of supply
      </span>
    );
  }
  if (o.changePct == null) return <span className="text-sm font-semibold text-gray-900">New since {o.grain === "WEEK" ? "last week" : "last month"}</span>;
  const up = o.changePct > 0;
  const Arrow = up ? ArrowUpRight : ArrowDownRight;
  const unit = o.kind.startsWith("YIELD_") ? " pts" : "%";
  return (
    <span className="inline-flex items-center gap-1 text-sm font-semibold text-gray-900">
      <Arrow className="w-4 h-4" aria-hidden />
      {up ? "+" : "−"}{Math.abs(o.changePct).toLocaleString(undefined, { maximumFractionDigits: Math.abs(o.changePct) >= 10 ? 0 : 1 })}{unit}
    </span>
  );
}

function ObservationCard({ o, highlighted }: { o: MarketObservationView; highlighted: boolean }) {
  const sample = o.comparisonStart && o.method !== "SHARE_GAP"
    ? `${o.sampleCurrent.toLocaleString()} vs ${o.samplePrevious.toLocaleString()}`
    : o.method === "SHARE_GAP" ? `${o.sampleCurrent.toLocaleString()} enquiries, ${o.samplePrevious.toLocaleString()} listings`
      : o.sampleCurrent.toLocaleString();
  return (
    <li id={`obs-${o.id}`} tabIndex={-1}
      className={`bg-white border rounded-xl p-4 scroll-mt-24 outline-none transition-shadow ${highlighted ? "border-brand-500 ring-2 ring-brand-100" : "border-gray-200"}`}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="inline-flex items-center gap-1 text-[11px] font-semibold uppercase tracking-wide text-gray-700 bg-gray-100 rounded px-1.5 py-0.5">
          <CheckCircle2 className="w-3 h-3" aria-hidden /> Observed
        </span>
        <Change o={o} />
      </div>
      <p className="mt-2 text-sm text-gray-900 leading-relaxed">{o.statement}</p>
      <dl className="mt-3 grid grid-cols-2 sm:grid-cols-3 gap-x-4 gap-y-2 text-xs">
        <div><dt className="text-gray-500">Location</dt><dd className="text-gray-800">{o.location}</dd></div>
        <div><dt className="text-gray-500">Period</dt><dd className="text-gray-800">{periodLabel(o.periodStart, o.grain)}</dd></div>
        <div><dt className="text-gray-500">Compared with</dt><dd className="text-gray-800">{o.comparisonStart ? periodLabel(o.comparisonStart, o.grain) : "Same period"}</dd></div>
        <div><dt className="text-gray-500">Sample (n)</dt><dd className="text-gray-800 tabular-nums">{sample}</dd></div>
        <div>
          <dt className="text-gray-500">Confidence</dt>
          <dd className="text-gray-800">{o.confidence.charAt(0) + o.confidence.slice(1).toLowerCase()}</dd>
        </div>
        <div>
          <dt className="text-gray-500">Test</dt>
          <dd className="text-gray-800" title={o.pValue != null ? `p = ${o.pValue}, adjusted q = ${o.qValue}` : undefined}>
            {METHOD[o.method]}{o.qValue != null ? ` · q = ${o.qValue < 0.001 ? "<0.001" : o.qValue.toFixed(3)}` : ""}
          </dd>
        </div>
      </dl>
    </li>
  );
}

/** Insights & alerts: tested observations, Claude's interpretation of them, and the alert centre. */
export default function InsightsPanel({ canConfigure }: { canConfigure: boolean }) {
  const { token } = useAuth();
  const [grain, setGrain] = useState<Grain>("WEEK");
  const [data, setData] = useState<InsightsData | null>(null);
  const [error, setError] = useState("");
  const [loadedKey, setLoadedKey] = useState("");
  const [highlight, setHighlight] = useState<number | null>(null);
  const [alerts, setAlerts] = useState<AnalyticsAlertView[]>([]);
  const [openCount, setOpenCount] = useState(0);
  const [openOnly, setOpenOnly] = useState(true);
  const [rules, setRules] = useState<AlertRuleView[]>([]);
  const [canEdit, setCanEdit] = useState(false);
  const [prefs, setPrefs] = useState<AlertPreferenceView[]>([]);
  const [msg, setMsg] = useState("");

  const load = useCallback(() => {
    if (!token) return;
    insightsApi.get(token, grain)
      .then((d) => { setData(d); setError(""); })
      .catch((e) => setError(e instanceof Error ? e.message : "Couldn't load insights."))
      .finally(() => setLoadedKey(grain));
  }, [token, grain]);
  useEffect(load, [load]);
  const loading = loadedKey !== grain;

  const loadAlerts = useCallback(() => {
    if (!token) return;
    insightsApi.alerts(token, openOnly).then((r) => { setAlerts(r.alerts); setOpenCount(r.openCount); }).catch(() => {});
  }, [token, openOnly]);
  useEffect(loadAlerts, [loadAlerts]);

  const loadSettings = useCallback(() => {
    if (!token) return;
    insightsApi.rules(token).then((r) => { setRules(r.rules); setCanEdit(r.canEdit); }).catch(() => {});
    insightsApi.preferences(token).then(setPrefs).catch(() => {});
  }, [token]);
  useEffect(loadSettings, [loadSettings]);

  const byId = useMemo(() => new Map((data?.observations ?? []).map((o) => [o.id, o])), [data]);

  const jumpTo = (id: number) => {
    setHighlight(id);
    const el = document.getElementById(`obs-${id}`);
    if (el) { el.scrollIntoView({ behavior: "smooth", block: "center" }); el.focus({ preventScroll: true }); }
  };

  const toggleAi = async () => {
    if (!token || !data) return;
    try {
      await insightsApi.setAi(token, !data.ai.enabled);
      load();
    } catch (e) {
      setMsg(e instanceof Error ? e.message : "Couldn't change the setting.");
    }
  };

  const acknowledge = async (id: number) => {
    if (!token) return;
    await insightsApi.acknowledge(token, id).catch(() => {});
    loadAlerts();
  };

  const saveRule = async (r: AlertRuleView) => {
    if (!token) return;
    try {
      await insightsApi.saveRule(token, r.key, r);
      setMsg(`Saved “${r.label}”.`);
      loadSettings();
    } catch (e) {
      setMsg(e instanceof Error ? e.message : "Couldn't save the rule.");
    }
  };

  const savePref = async (p: AlertPreferenceView) => {
    if (!token) return;
    setPrefs((all) => all.map((x) => (x.key === p.key ? p : x)));
    await insightsApi.savePreference(token, p.key, { inApp: p.inApp, email: p.email }).catch(() => loadSettings());
  };

  const cov = data?.coverage;
  const ai = data?.ai;
  const sections = (["CHANGE", "OPPORTUNITY", "RISK"] as const)
    .map((s) => ({ s, items: (ai?.items ?? []).filter((i) => i.section === s) }))
    .filter((x) => x.items.length > 0);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-xl font-semibold text-gray-900 tracking-tight">Insights &amp; alerts</h2>
          <p className="text-sm text-gray-500 mt-0.5">
            Changes that passed a statistical test, an AI reading of them, and the alerts they raised.
          </p>
        </div>
        <div role="radiogroup" aria-label="Compare" className="flex rounded-lg bg-gray-100 p-0.5">
          {(["WEEK", "MONTH"] as const).map((g) => (
            <button key={g} type="button" role="radio" aria-checked={grain === g} onClick={() => setGrain(g)}
              className={`px-3 py-1.5 text-sm rounded-md ${grain === g ? "bg-white shadow-sm font-medium text-gray-900" : "text-gray-600"}`}>
              {g === "WEEK" ? "Week on week" : "Month on month"}
            </button>
          ))}
        </div>
      </div>

      {error && <p role="alert" className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2">{error}</p>}
      {msg && <p role="status" className="text-sm text-gray-700 bg-gray-50 border border-gray-200 rounded-lg px-3 py-2">{msg}</p>}

      {cov && (
        <p className="text-sm text-gray-600">
          <span className="font-medium text-gray-900">{periodLabel(cov.periodStart, grain)}</span>
          {cov.comparisonStart ? <> compared with <span className="font-medium text-gray-900">{periodLabel(cov.comparisonStart, grain)}</span></> : " (no earlier period to compare)"}
          {" · "}{cov.tested} comparison{cov.tested === 1 ? "" : "s"} tested, {cov.reported} reported
          {cov.belowMinimum > 0 && <>, {cov.belowMinimum} below the minimum sample</>}
          {cov.historyIncomplete > 0 && <>, {cov.historyIncomplete} with history that started part-way</>}
          {" · "}updated {when(cov.computedAt)}
        </p>
      )}

      {/* AI interpretation: visibly different from the observed facts. */}
      {ai && (
        <section aria-labelledby="ai-title" className="rounded-2xl border border-dashed border-indigo-300 bg-indigo-50/50 p-5">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h3 id="ai-title" className="inline-flex items-center gap-2 text-sm font-semibold text-indigo-900">
              <Sparkles className="w-4 h-4" aria-hidden /> AI interpretation
            </h3>
            <div className="flex items-center gap-3 text-xs text-indigo-900/80">
              {ai.createdAt && <span>Written {when(ai.createdAt)}{ai.model ? ` by ${ai.model}` : ""}</span>}
              {canConfigure && (
                <button type="button" role="switch" aria-checked={ai.enabled} onClick={toggleAi}
                  className="inline-flex items-center gap-2 rounded-full border border-indigo-200 bg-white px-2.5 py-1 font-medium text-indigo-900">
                  <span className={`h-3.5 w-6 rounded-full p-0.5 transition-colors ${ai.enabled ? "bg-indigo-600" : "bg-gray-300"}`}>
                    <span className={`block h-2.5 w-2.5 rounded-full bg-white transition-transform ${ai.enabled ? "translate-x-2.5" : ""}`} />
                  </span>
                  {ai.enabled ? "On" : "Off"}
                </button>
              )}
            </div>
          </div>
          <p className="mt-1 text-xs text-indigo-900/70">
            Claude reads only the observations below and must cite them. It suggests what they may mean; it doesn&apos;t establish causes.
          </p>
          {ai.headline && <p className="mt-3 text-base font-medium text-gray-900">{ai.headline}</p>}
          {ai.note && <p className="mt-2 text-sm text-indigo-950/80">{ai.note}</p>}
          {sections.length > 0 && (
            <div className="mt-4 grid gap-4 md:grid-cols-3">
              {sections.map(({ s, items }) => {
                const { title, Icon } = SECTION[s];
                return (
                  <div key={s}>
                    <h4 className="inline-flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-indigo-900">
                      <Icon className="w-3.5 h-3.5" aria-hidden /> {title}
                    </h4>
                    <ul className="mt-2 space-y-3">
                      {items.map((i, n) => (
                        <li key={n} className="bg-white/80 border border-indigo-100 rounded-xl p-3">
                          <p className="text-sm font-medium text-gray-900">{i.title}</p>
                          <p className="mt-1 text-sm text-gray-700">{i.interpretation}</p>
                          {i.implication && <p className="mt-1 text-sm text-gray-700"><span className="font-medium">Implication:</span> {i.implication}</p>}
                          <p className="mt-2 flex flex-wrap gap-1 text-xs text-gray-600">
                            Based on
                            {i.observationIds.map((id) => (
                              <button key={id} type="button" onClick={() => jumpTo(id)} disabled={!byId.has(id)}
                                title={byId.get(id)?.statement ?? "Observation from the other comparison (week/month)"}
                                className="underline decoration-dotted underline-offset-2 hover:text-gray-900 disabled:no-underline">
                                observation {byId.has(id) ? `#${id}` : `#${id} (${grain === "WEEK" ? "monthly" : "weekly"})`}
                              </button>
                            ))}
                          </p>
                        </li>
                      ))}
                    </ul>
                  </div>
                );
              })}
            </div>
          )}
          {ai.hiddenItems > 0 && (
            <p className="mt-3 text-xs text-indigo-900/70">{ai.hiddenItems} item(s) rest on data your role can&apos;t see and are hidden.</p>
          )}
        </section>
      )}

      {/* Analyst questions, each answered with evidence or an explicit "no reliable conclusion". */}
      {data && (
        <section aria-labelledby="q-title" className="bg-white border border-gray-200 rounded-2xl p-5">
          <h3 id="q-title" className="text-sm font-semibold text-gray-900">Questions management asks</h3>
          <ul className="mt-3 divide-y divide-gray-100">
            {data.questions.map((q) => (
              <li key={q.key} className="py-3">
                <p className="text-sm font-medium text-gray-900">{q.question}</p>
                {q.observationIds.length > 0 && (
                  <ul className="mt-1 space-y-1">
                    {q.observationIds.map((id) => byId.get(id) && (
                      <li key={id} className="text-sm text-gray-700">
                        <button type="button" onClick={() => jumpTo(id)} className="text-left hover:underline">{byId.get(id)!.statement}</button>
                      </li>
                    ))}
                  </ul>
                )}
                {q.answer && (
                  <p className="mt-1 text-sm text-gray-600 inline-flex items-start gap-1.5">
                    <Info className="w-4 h-4 mt-0.5 shrink-0" aria-hidden /> {q.answer}
                  </p>
                )}
              </li>
            ))}
          </ul>
        </section>
      )}

      {/* Observed facts. */}
      <section aria-labelledby="obs-title">
        <h3 id="obs-title" className="text-sm font-semibold text-gray-900">Observed changes</h3>
        <p className="text-xs text-gray-500 mt-0.5">
          Shown only when a change passes its test after correcting for the many comparisons made each run (adjusted q ≤ {data?.minimums.maxQValue ?? 0.05})
          and is large enough to matter. Minimums: {data?.minimums.events ?? 20} events across both periods for counts, {data?.minimums.medianSample ?? 10} prices
          in each period for medians, {data?.minimums.stock ?? 20} listings for supply.
        </p>
        {loading && !data ? (
          <div className="mt-3 grid gap-3 md:grid-cols-2">{[0, 1, 2, 3].map((i) => <div key={i} className="h-40 rounded-xl bg-gray-100 animate-pulse" />)}</div>
        ) : data && data.observations.length === 0 ? (
          <p className="mt-3 text-sm text-gray-600 bg-white border border-gray-200 rounded-xl p-4">
            No change passed the tests for this comparison, so no reliable conclusion can be drawn. That usually means too little activity
            yet, or a genuinely steady market.
          </p>
        ) : (
          <ul className={`mt-3 grid gap-3 md:grid-cols-2 ${loading ? "opacity-60" : ""}`}>
            {data?.observations.map((o) => <ObservationCard key={o.id} o={o} highlighted={highlight === o.id} />)}
          </ul>
        )}
      </section>

      {/* Alert centre. */}
      <section aria-labelledby="alerts-title" className="bg-white border border-gray-200 rounded-2xl p-5">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h3 id="alerts-title" className="inline-flex items-center gap-2 text-sm font-semibold text-gray-900">
            <Bell className="w-4 h-4" aria-hidden /> Alerts
            <span className="text-xs font-medium text-gray-600 bg-gray-100 rounded-full px-2 py-0.5">{openCount} open</span>
          </h3>
          <label className="inline-flex items-center gap-2 text-sm text-gray-700">
            <input type="checkbox" checked={openOnly} onChange={(e) => setOpenOnly(e.target.checked)} /> Open only
          </label>
        </div>
        {alerts.length === 0 ? (
          <p className="mt-3 text-sm text-gray-600">{openOnly ? "No open alerts." : "No alerts yet."}</p>
        ) : (
          <ul className="mt-3 space-y-2">
            {alerts.map((a) => {
              const s = SEVERITY[a.severity];
              return (
                <li key={a.id} className="flex flex-wrap items-start justify-between gap-3 border border-gray-200 rounded-xl p-3">
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className={`inline-flex items-center gap-1 text-xs font-medium border rounded px-1.5 py-0.5 ${s.cls}`}>
                        <s.Icon className="w-3.5 h-3.5" aria-hidden /> {s.label}
                      </span>
                      <span className="text-sm font-medium text-gray-900">{a.title}</span>
                    </div>
                    <p className="mt-1 text-sm text-gray-700">{a.message}</p>
                    <p className="mt-1 text-xs text-gray-500">
                      {when(a.createdAt)} · sent to {a.recipients} staff member{a.recipients === 1 ? "" : "s"}
                      {a.acknowledgedAt && <> · acknowledged {when(a.acknowledgedAt)}</>}
                    </p>
                  </div>
                  {!a.acknowledgedAt && (
                    <button type="button" onClick={() => acknowledge(a.id)}
                      className="text-sm font-medium text-gray-700 border border-gray-200 rounded-lg px-3 py-1.5 hover:bg-gray-50">
                      Acknowledge
                    </button>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </section>

      {/* Thresholds and cooldowns (admins edit; others read). */}
      {rules.length > 0 && <RulesTable rules={rules} canEdit={canEdit} onSave={saveRule} />}

      {/* My notification preferences. */}
      {prefs.length > 0 && (
        <section aria-labelledby="prefs-title" className="bg-white border border-gray-200 rounded-2xl p-5">
          <h3 id="prefs-title" className="text-sm font-semibold text-gray-900">How I hear about alerts</h3>
          <p className="text-xs text-gray-500 mt-0.5">Only alerts your role can see are listed. In-app alerts appear under the bell.</p>
          <table className="mt-3 w-full text-sm">
            <thead><tr className="text-left text-xs text-gray-500"><th className="py-1 font-medium">Alert</th><th className="py-1 font-medium w-20">In-app</th><th className="py-1 font-medium w-20">Email</th></tr></thead>
            <tbody>
              {prefs.map((p) => (
                <tr key={p.key} className="border-t border-gray-100">
                  <td className="py-2 text-gray-800">{p.label}</td>
                  <td className="py-2"><input type="checkbox" aria-label={`${p.label}: in-app`} checked={p.inApp} onChange={(e) => savePref({ ...p, inApp: e.target.checked })} /></td>
                  <td className="py-2"><input type="checkbox" aria-label={`${p.label}: email`} checked={p.email} onChange={(e) => savePref({ ...p, email: e.target.checked })} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
    </div>
  );
}

function RulesTable({ rules, canEdit, onSave }: { rules: AlertRuleView[]; canEdit: boolean; onSave: (r: AlertRuleView) => void }) {
  const [draft, setDraft] = useState<Record<string, AlertRuleView>>({});
  const value = (r: AlertRuleView) => draft[r.key] ?? r;
  const edit = (r: AlertRuleView, patch: Partial<AlertRuleView>) => setDraft((d) => ({ ...d, [r.key]: { ...value(r), ...patch } }));
  const unit = (r: AlertRuleView) => (r.unit === "PERCENT" ? "% change" : r.unit === "HOURS" ? "hours old" : "count");
  const input = "w-20 border border-gray-200 rounded-md px-2 py-1 text-sm tabular-nums disabled:bg-gray-50 disabled:text-gray-600";
  return (
    <section aria-labelledby="rules-title" className="bg-white border border-gray-200 rounded-2xl p-5">
      <h3 id="rules-title" className="text-sm font-semibold text-gray-900">Alert rules</h3>
      <p className="text-xs text-gray-500 mt-0.5">
        Market rules only consider observations that already passed their test. The cooldown keeps a rule quiet for the same place after it fires.
        {!canEdit && " Only administrators can change these."}
      </p>
      <div className="mt-3 overflow-x-auto">
        <table className="w-full text-sm min-w-[760px]">
          <thead>
            <tr className="text-left text-xs text-gray-500">
              <th className="py-1 font-medium">Rule</th><th className="py-1 font-medium">On</th><th className="py-1 font-medium">Threshold</th>
              <th className="py-1 font-medium">Min n</th><th className="py-1 font-medium">Max q</th><th className="py-1 font-medium">Cooldown (h)</th>
              <th className="py-1 font-medium">Severity</th>{canEdit && <th />}
            </tr>
          </thead>
          <tbody>
            {rules.map((r) => {
              const v = value(r);
              const dirty = !!draft[r.key];
              return (
                <tr key={r.key} className="border-t border-gray-100 align-middle">
                  <td className="py-2 pr-3 text-gray-800">{r.label}{r.customised && <span className="ml-1 text-xs text-gray-500">(customised)</span>}</td>
                  <td className="py-2"><input type="checkbox" aria-label={`${r.label}: on`} disabled={!canEdit} checked={v.enabled} onChange={(e) => edit(r, { enabled: e.target.checked })} /></td>
                  <td className="py-2"><input type="number" min={0} aria-label={`${r.label}: threshold (${unit(r)})`} disabled={!canEdit} className={input}
                    value={v.threshold} onChange={(e) => edit(r, { threshold: Number(e.target.value) })} /> <span className="text-xs text-gray-500">{unit(r)}</span></td>
                  <td className="py-2"><input type="number" min={1} aria-label={`${r.label}: minimum sample`} disabled={!canEdit || !r.marketRule} className={input}
                    value={v.minSample} onChange={(e) => edit(r, { minSample: Number(e.target.value) })} /></td>
                  <td className="py-2"><input type="number" step={0.01} min={0.001} max={0.1} aria-label={`${r.label}: maximum adjusted p-value`} disabled={!canEdit || !r.marketRule}
                    className={input} value={r.marketRule ? v.maxQValue : ""} placeholder="—" onChange={(e) => edit(r, { maxQValue: Number(e.target.value) })} /></td>
                  <td className="py-2"><input type="number" min={1} aria-label={`${r.label}: cooldown hours`} disabled={!canEdit} className={input}
                    value={v.cooldownHours} onChange={(e) => edit(r, { cooldownHours: Number(e.target.value) })} /></td>
                  <td className="py-2">
                    <select aria-label={`${r.label}: severity`} disabled={!canEdit} value={v.severity} onChange={(e) => edit(r, { severity: e.target.value as AlertSeverity })}
                      className="border border-gray-200 rounded-md px-1.5 py-1 text-sm bg-white disabled:bg-gray-50">
                      <option value="INFO">Info</option><option value="WARNING">Warning</option><option value="CRITICAL">Critical</option>
                    </select>
                  </td>
                  {canEdit && (
                    <td className="py-2 text-right">
                      <button type="button" disabled={!dirty}
                        onClick={() => { onSave(v); setDraft((d) => { const n = { ...d }; delete n[r.key]; return n; }); }}
                        className="text-sm font-medium text-white bg-brand-600 rounded-lg px-3 py-1 disabled:opacity-40">Save</button>
                    </td>
                  )}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </section>
  );
}
