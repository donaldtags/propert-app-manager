"use client";

import { AlertTriangle, ArrowDownRight, ArrowUpRight, Info, ListTree, Minus, Radio, Sparkles } from "lucide-react";
import type { OverviewGrain, OverviewKpi } from "@/lib/api";
import Sparkline from "./Sparkline";
import { formatValue, signed, tone } from "./format";

const TONE = {
  good: "text-emerald-700 bg-emerald-50",
  bad: "text-red-700 bg-red-50",
  neutral: "text-gray-600 bg-gray-100",
} as const;

/**
 * One KPI: value, change against the comparison period (arrow + sign + words, never colour
 * alone), the previous value, a trend line, sample size and any caveat. Unavailable figures say
 * why instead of showing a zero.
 */
export default function KpiTile({ kpi, grain, comparisonShort, headline = false, locationChosen, onDrill }: {
  kpi: OverviewKpi;
  grain: OverviewGrain;
  comparisonShort: string;
  headline?: boolean;
  locationChosen: boolean;
  onDrill?: (kpi: OverviewKpi) => void;
}) {
  const t = tone(kpi);
  const Arrow = kpi.direction === "UP" || kpi.direction === "NEW" ? ArrowUpRight : kpi.direction === "DOWN" ? ArrowDownRight : Minus;

  return (
    <article
      className={`bg-white border rounded-2xl p-4 flex flex-col gap-2 min-w-0 ${headline ? "border-brand-200 shadow-sm" : "border-gray-200"} ${!kpi.available ? "bg-gray-50/60" : ""}`}
      aria-label={kpi.label}
    >
      <header className="flex items-start justify-between gap-2">
        <h3 className="text-xs font-medium text-gray-500 leading-snug">{kpi.label}</h3>
        <span className="relative group shrink-0">
          <button type="button" className="text-gray-300 hover:text-gray-500 focus:text-gray-500" aria-label={`About ${kpi.label}: ${kpi.help}`}>
            <Info className="w-3.5 h-3.5" aria-hidden />
          </button>
          <span role="tooltip" className="invisible group-hover:visible group-focus-within:visible absolute right-0 top-5 z-20 w-64 rounded-lg bg-gray-900 text-white text-xs leading-relaxed p-3 shadow-lg">
            {kpi.help}
          </span>
        </span>
      </header>

      {!kpi.available ? (
        <p className="text-sm text-gray-500 leading-snug">{kpi.note}</p>
      ) : (
        <>
          <p className={`font-semibold text-gray-900 tabular-nums tracking-tight ${headline ? "text-3xl" : "text-2xl"}`}>
            {formatValue(kpi.value, kpi.unit)}
          </p>

          {kpi.live ? (
            <p className="inline-flex items-center gap-1 text-xs text-sky-700"><Radio className="w-3.5 h-3.5" aria-hidden /> Live count</p>
          ) : kpi.comparable && kpi.change != null ? (
            <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs">
              <span className={`inline-flex items-center gap-0.5 rounded-full px-1.5 py-0.5 font-semibold tabular-nums ${TONE[t]}`}>
                <Arrow className="w-3.5 h-3.5" aria-hidden />
                {kpi.direction === "NEW" ? "New" : kpi.direction === "FLAT" ? "No change"
                  : `${signed(kpi.change, kpi.unit)}${kpi.changePercent != null ? ` (${kpi.changePercent > 0 ? "+" : ""}${kpi.changePercent}%)` : ""}`}
              </span>
              <span className="text-gray-400 tabular-nums">vs {formatValue(kpi.previous, kpi.unit)} {comparisonShort}</span>
            </p>
          ) : (
            <p className="text-xs text-gray-400 leading-snug">{kpi.note ?? "No comparison available."}</p>
          )}

          {!kpi.live && <Sparkline points={kpi.trend} grain={grain} unit={kpi.unit} label={kpi.label} />}

          <footer className="mt-auto flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-gray-400">
            {kpi.sampleSize != null && kpi.unit !== "COUNT" && <span className="tabular-nums">n = {kpi.sampleSize.toLocaleString()}</span>}
            {kpi.lowSample && (
              <span className="inline-flex items-center gap-1 text-amber-700"><AlertTriangle className="w-3 h-3" aria-hidden /> Small sample</span>
            )}
            {locationChosen && !kpi.locationApplies && <span>All locations</span>}
            {kpi.direction === "NEW" && kpi.comparable && <span className="inline-flex items-center gap-1"><Sparkles className="w-3 h-3" aria-hidden /> Up from zero</span>}
            {kpi.drillable && kpi.value != null && onDrill && (
              <button type="button" onClick={() => onDrill(kpi)} className="ml-auto inline-flex items-center gap-1 text-brand-600 hover:text-brand-700 font-medium">
                <ListTree className="w-3.5 h-3.5" aria-hidden /> Records
              </button>
            )}
          </footer>
        </>
      )}
    </article>
  );
}
