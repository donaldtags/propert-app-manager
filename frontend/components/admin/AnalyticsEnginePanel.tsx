"use client";

import { useCallback, useEffect, useState } from "react";
import {
  Activity, AlertTriangle, CheckCircle2, ChevronDown, ChevronRight, Clock, Loader2, PlayCircle, XCircle,
} from "lucide-react";
import { useAuth } from "@/lib/auth";
import {
  analyticsEngine, type AnalyticsEngineStatus, type AnalyticsRun, type AnalyticsRunStep,
} from "@/lib/api";
import AlertBanner from "@/components/AlertBanner";

const STATE: Record<AnalyticsEngineStatus["state"], { label: string; tone: string; Icon: typeof CheckCircle2; help: string }> = {
  UP_TO_DATE: { label: "Up to date", tone: "text-brand-700 bg-brand-50 border-brand-200", Icon: CheckCircle2,
    help: "Figures were refreshed within the last two hours." },
  RUNNING: { label: "Updating now", tone: "text-sky-700 bg-sky-50 border-sky-200", Icon: Loader2,
    help: "A run is in progress. The figures on screen are from the last finished run." },
  STALE: { label: "Stale", tone: "text-amber-800 bg-amber-50 border-amber-200", Icon: AlertTriangle,
    help: "No successful update for over two hours. Figures shown are older than they should be." },
  FAILED: { label: "Last run failed", tone: "text-red-700 bg-red-50 border-red-200", Icon: XCircle,
    help: "The latest run failed. The last good figures are still shown, and the next run will try again." },
  NEVER_RUN: { label: "Not run yet", tone: "text-gray-700 bg-gray-50 border-gray-200", Icon: Clock,
    help: "The engine hasn't produced any figures yet. It runs at 5 past every hour." },
};

const STEP_LABEL: Record<string, string> = {
  collect_changes: "Collect changes",
  validate_and_deduplicate: "Validate & find duplicates",
  check_verification: "Check verification",
  calculate_metrics: "Calculate figures",
  significance_tests: "Significance tests",
  market_intelligence: "Market intelligence",
  alerts: "Alerts",
  publish: "Publish",
};

function when(iso?: string) {
  if (!iso) return "—";
  return new Date(iso).toLocaleString(undefined, { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });
}

function ago(iso?: string) {
  if (!iso) return "";
  const mins = Math.round((Date.now() - new Date(iso).getTime()) / 60000);
  if (mins < 1) return "just now";
  if (mins < 60) return `${mins} min ago`;
  const h = Math.round(mins / 60);
  return h < 48 ? `${h} h ago` : `${Math.round(h / 24)} days ago`;
}

function duration(ms?: number) {
  if (ms == null) return "—";
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1)} s`;
}

function RunStatus({ status }: { status: AnalyticsRun["status"] }) {
  const map = {
    SUCCEEDED: { cls: "bg-brand-100 text-brand-700", Icon: CheckCircle2, label: "Succeeded" },
    FAILED: { cls: "bg-red-100 text-red-700", Icon: XCircle, label: "Failed" },
    RUNNING: { cls: "bg-sky-100 text-sky-700", Icon: Loader2, label: "Running" },
    ABANDONED: { cls: "bg-gray-100 text-gray-600", Icon: AlertTriangle, label: "Abandoned" },
  }[status];
  return (
    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-semibold ${map.cls}`}>
      <map.Icon className={`w-3.5 h-3.5 ${status === "RUNNING" ? "animate-spin" : ""}`} aria-hidden /> {map.label}
    </span>
  );
}

function Steps({ steps }: { steps: AnalyticsRunStep[] }) {
  return (
    <ol className="space-y-1.5">
      {steps.map((s) => (
        <li key={s.name} className="flex items-start gap-2 text-sm">
          {s.status === "OK" && <CheckCircle2 className="w-4 h-4 mt-0.5 text-brand-600 shrink-0" aria-label="Done" />}
          {s.status === "FAILED" && <XCircle className="w-4 h-4 mt-0.5 text-red-600 shrink-0" aria-label="Failed" />}
          {s.status === "SKIPPED" && <span className="w-4 h-4 mt-0.5 shrink-0 text-gray-400 text-center" aria-label="Skipped">–</span>}
          <span className="font-medium text-gray-800 w-48 shrink-0">{STEP_LABEL[s.name] ?? s.name}</span>
          <span className="text-gray-500 flex-1">{s.detail}</span>
          <span className="text-gray-400 tabular-nums">{s.status === "SKIPPED" ? "" : duration(s.ms)}</span>
        </li>
      ))}
    </ol>
  );
}

/**
 * Where the dashboard's numbers come from and how fresh they are: the hourly engine's status,
 * data-quality warnings and run history. Every other analytics page links its freshness badge here.
 */
export default function AnalyticsEnginePanel() {
  const { token, user } = useAuth();
  const [status, setStatus] = useState<AnalyticsEngineStatus | null>(null);
  const [runs, setRuns] = useState<AnalyticsRun[] | null>(null);
  const [open, setOpen] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const canConfigure = user?.roles?.includes("ADMIN");

  const load = useCallback(() => {
    if (!token) return;
    Promise.all([analyticsEngine.status(token), analyticsEngine.runs(token, 25)])
      .then(([s, r]) => { setStatus(s); setRuns(r); setError(""); })
      .catch((e) => setError(e instanceof Error ? e.message : "Couldn't load the analytics engine status."));
  }, [token]);
  useEffect(load, [load]);

  const runNow = async () => {
    if (!token) return;
    setBusy(true);
    setNotice("");
    try {
      const out = await analyticsEngine.runNow(token);
      setNotice(out.status === "SUCCEEDED" ? `Figures updated: ${out.detail}.`
        : out.status === "SKIPPED" ? `Nothing to do: ${out.detail}.` : `The run failed: ${out.detail}`);
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Couldn't start a run.");
    } finally {
      setBusy(false);
    }
  };

  if (!status && !error) {
    return <div className="flex items-center gap-2 text-gray-500"><Loader2 className="w-4 h-4 animate-spin" /> Loading engine status…</div>;
  }

  const state = status ? STATE[status.state] : null;
  const warnings = status?.lastSuccess?.warnings ?? [];

  return (
    <section aria-labelledby="engine-title" className="space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 id="engine-title" className="text-lg font-bold text-gray-900 flex items-center gap-2">
            <Activity className="w-5 h-5 text-brand-600" aria-hidden /> Analytics engine
          </h2>
          <p className="text-sm text-gray-500 mt-1 max-w-2xl">
            Every hour the engine reads what changed on EstateX, checks the data and recalculates the market figures.
            If a run fails, the last good figures stay on screen and are marked as stale.
          </p>
        </div>
        {canConfigure && (
          <button
            onClick={runNow}
            disabled={busy}
            className="inline-flex items-center gap-2 bg-brand-600 hover:bg-brand-700 disabled:opacity-60 text-white text-sm font-semibold px-4 py-2 rounded-lg"
          >
            {busy ? <Loader2 className="w-4 h-4 animate-spin" aria-hidden /> : <PlayCircle className="w-4 h-4" aria-hidden />}
            {busy ? "Updating…" : "Update now"}
          </button>
        )}
      </header>

      {error && <AlertBanner variant="error">{error}</AlertBanner>}
      {notice && <AlertBanner variant="success">{notice}</AlertBanner>}

      {status && state && (
        <>
          <div className={`rounded-2xl border p-5 ${state.tone}`}>
            <div className="flex items-center gap-2 font-semibold">
              <state.Icon className={`w-5 h-5 ${status.state === "RUNNING" ? "animate-spin" : ""}`} aria-hidden />
              {state.label}
            </div>
            <p className="text-sm mt-1 opacity-90">{state.help}</p>
            {status.lastRun?.status === "FAILED" && status.lastRun.error && (
              <p className="text-sm mt-2 font-mono bg-white/60 rounded px-2 py-1">{status.lastRun.error}</p>
            )}
          </div>

          <dl className="grid grid-cols-2 lg:grid-cols-4 gap-3">
            {[
              { k: "Last successful update", v: when(status.lastSuccessAt), sub: ago(status.lastSuccessAt) },
              { k: "Next scheduled update", v: when(status.nextScheduledAt), sub: "Runs at 5 past every hour" },
              { k: "Records processed (last run)", v: (status.lastSuccess?.recordsRead ?? 0).toLocaleString(),
                sub: `${(status.lastSuccess?.metricsWritten ?? 0).toLocaleString()} figures published` },
              { k: "Left out of price figures", v: (status.lastSuccess?.recordsRejected ?? 0).toLocaleString(),
                sub: "No price or another currency" },
            ].map((t) => (
              <div key={t.k} className="bg-white border border-gray-200 rounded-xl p-4">
                <dt className="text-xs font-medium text-gray-500">{t.k}</dt>
                <dd className="text-lg font-semibold text-gray-900 mt-1 tabular-nums">{t.v}</dd>
                <dd className="text-xs text-gray-400 mt-0.5">{t.sub}</dd>
              </div>
            ))}
          </dl>

          {status.historyStartsAt && (
            <p className="text-xs text-gray-500">
              Listing history starts on {new Date(status.historyStartsAt).toLocaleDateString(undefined, { day: "numeric", month: "long", year: "numeric" })}.
              Trends before that date can&apos;t be shown, because earlier prices weren&apos;t recorded.
            </p>
          )}

          <div className="bg-white border border-gray-200 rounded-2xl">
            <h3 className="px-5 pt-4 pb-2 font-semibold text-gray-900">Data quality</h3>
            {warnings.length === 0 ? (
              <p className="px-5 pb-5 text-sm text-gray-500">No problems found in the last run.</p>
            ) : (
              <ul className="divide-y divide-gray-100">
                {warnings.map((w) => (
                  <li key={w.code} className="px-5 py-3 flex items-start gap-3 text-sm">
                    <AlertTriangle className="w-4 h-4 text-amber-600 mt-0.5 shrink-0" aria-hidden />
                    <span className="text-gray-700 flex-1">{w.message}</span>
                    {w.sampleIds && w.sampleIds.length > 0 && (
                      <span className="text-xs text-gray-400">e.g. listing {w.sampleIds.slice(0, 3).map((id) => `#${id}`).join(", ")}</span>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </div>
        </>
      )}

      {runs && (
        <div className="bg-white border border-gray-200 rounded-2xl overflow-hidden">
          <h3 className="px-5 pt-4 pb-2 font-semibold text-gray-900">Run history</h3>
          {runs.length === 0 ? (
            <p className="px-5 pb-5 text-sm text-gray-500">No runs yet.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="text-left text-xs text-gray-500 border-b border-gray-100">
                  <tr>
                    <th className="px-5 py-2 font-medium" scope="col"><span className="sr-only">Details</span></th>
                    <th className="px-2 py-2 font-medium" scope="col">Started</th>
                    <th className="px-2 py-2 font-medium" scope="col">Job</th>
                    <th className="px-2 py-2 font-medium" scope="col">Status</th>
                    <th className="px-2 py-2 font-medium text-right" scope="col">Records</th>
                    <th className="px-2 py-2 font-medium text-right" scope="col">Figures</th>
                    <th className="px-2 py-2 font-medium text-right" scope="col">Retries</th>
                    <th className="px-5 py-2 font-medium text-right" scope="col">Took</th>
                  </tr>
                </thead>
                <tbody>
                  {runs.map((r) => (
                    <FragmentRow key={r.id} run={r} open={open === r.id} onToggle={() => setOpen(open === r.id ? null : r.id)} />
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </section>
  );
}

function FragmentRow({ run, open, onToggle }: { run: AnalyticsRun; open: boolean; onToggle: () => void }) {
  return (
    <>
      <tr className="border-b border-gray-50 hover:bg-gray-50">
        <td className="px-5 py-2">
          <button onClick={onToggle} aria-expanded={open} aria-label={open ? "Hide run details" : "Show run details"}
            className="text-gray-400 hover:text-gray-700">
            {open ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
          </button>
        </td>
        <td className="px-2 py-2 text-gray-700 whitespace-nowrap">{when(run.startedAt)}</td>
        <td className="px-2 py-2 text-gray-600 capitalize">{run.job}{run.triggeredBy === "MANUAL" ? " (manual)" : ""}</td>
        <td className="px-2 py-2"><RunStatus status={run.status} /></td>
        <td className="px-2 py-2 text-right tabular-nums">{run.recordsRead.toLocaleString()}</td>
        <td className="px-2 py-2 text-right tabular-nums">{run.metricsWritten.toLocaleString()}</td>
        <td className="px-2 py-2 text-right tabular-nums">{run.stepRetries}</td>
        <td className="px-5 py-2 text-right tabular-nums text-gray-500">{duration(run.durationMs)}</td>
      </tr>
      {open && (
        <tr className="bg-gray-50/60">
          <td colSpan={8} className="px-5 py-4 space-y-3">
            <p className="text-xs text-gray-500">
              Window {when(run.windowStart)} → {when(run.windowEnd)} · attempt {run.attempt}
            </p>
            {run.error && <p className="text-sm text-red-700 font-mono">{run.error}</p>}
            {run.steps.length > 0 && <Steps steps={run.steps} />}
          </td>
        </tr>
      )}
    </>
  );
}
