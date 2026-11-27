"use client";

import { useState } from "react";
import { formatValue } from "../format";
import { niceTicks, SERIES_COLORS, useWidth } from "./useWidth";

export interface LinePoint { label: string; values: Record<string, number | undefined> }
export interface LineSeries { key: string; label: string }
type Unit = "COUNT" | "USD" | "PERCENT" | "DAYS";

const M = { top: 14, bottom: 30, left: 56 };

/**
 * One measure over time, one axis. Up to two series (validated blue/orange), legend plus end
 * labels when there are two. An optional band shows the middle 50% of prices around the first
 * series. Periods without data leave a gap; they are never drawn as zero.
 */
export default function LineChart({ points, series, unit, band, height = 220, title }: {
  points: LinePoint[];
  series: LineSeries[];
  unit: Unit;
  band?: { low: string; high: string };
  height?: number;
  title: string;
}) {
  const [ref, width] = useWidth<HTMLDivElement>();
  const [hover, setHover] = useState<number | null>(null);
  const two = series.length > 1;
  const right = two ? 96 : 16;
  const plotW = Math.max(40, width - M.left - right);
  const plotH = height - M.top - M.bottom;
  const all: number[] = [];
  points.forEach((p) => {
    series.forEach((s) => { const v = p.values[s.key]; if (v != null) all.push(v); });
    if (band) { const h = p.values[band.high]; if (h != null) all.push(h); }
  });
  if (all.length === 0) {
    return <div ref={ref} className="h-40 flex items-center justify-center text-sm text-gray-400 border border-dashed border-gray-200 rounded-xl">No data for these filters yet</div>;
  }
  const ticks = niceTicks(Math.max(...all));
  const top = ticks[ticks.length - 1] || 1;
  const n = points.length;
  const x = (i: number) => M.left + (n === 1 ? plotW / 2 : (i * plotW) / (n - 1));
  const y = (v: number) => M.top + plotH - (v / top) * plotH;
  const every = Math.max(1, Math.ceil(n / Math.max(2, Math.floor(plotW / 72))));

  const pathFor = (key: string) => {
    let d = "";
    let pen = false;
    points.forEach((p, i) => {
      const v = p.values[key];
      if (v == null) { pen = false; return; }
      d += `${pen ? "L" : "M"}${x(i).toFixed(1)},${y(v).toFixed(1)}`;
      pen = true;
    });
    return d;
  };
  const bandPath = () => {
    if (!band) return "";
    const idx = points.map((p, i) => (p.values[band.low] != null && p.values[band.high] != null ? i : -1)).filter((i) => i >= 0);
    if (idx.length === 0) return "";
    const upper = idx.map((i, k) => `${k ? "L" : "M"}${x(i).toFixed(1)},${y(points[i].values[band.high]!).toFixed(1)}`).join("");
    const lower = [...idx].reverse().map((i) => `L${x(i).toFixed(1)},${y(points[i].values[band.low]!).toFixed(1)}`).join("");
    return `${upper}${lower}Z`;
  };
  // End labels: last value of each series, nudged apart if they would overlap.
  const ends = series.map((s, si) => {
    let i = n - 1;
    while (i >= 0 && points[i].values[s.key] == null) i--;
    return i < 0 ? null : { si, i, y: y(points[i].values[s.key]!) };
  }).filter((e): e is { si: number; i: number; y: number } => e !== null);
  if (ends.length === 2 && Math.abs(ends[0].y - ends[1].y) < 14) {
    const mid = (ends[0].y + ends[1].y) / 2;
    const up = ends[0].y <= ends[1].y ? 0 : 1;
    ends[up].y = mid - 7;
    ends[1 - up].y = mid + 7;
  }

  const onMove = (e: React.PointerEvent<SVGRectElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const rel = e.clientX - rect.left;
    setHover(n === 1 ? 0 : Math.max(0, Math.min(n - 1, Math.round((rel / rect.width) * (n - 1)))));
  };

  return (
    <div ref={ref} className="relative">
      {two && (
        <ul className="flex flex-wrap gap-4 mb-2 text-xs text-gray-600" aria-label="Legend">
          {series.map((s, i) => (
            <li key={s.key} className="flex items-center gap-1.5">
              <span className="w-3 h-0.5 rounded-full" style={{ background: SERIES_COLORS[i] }} aria-hidden /> {s.label}
            </li>
          ))}
          {band && <li className="flex items-center gap-1.5"><span className="w-3 h-2.5 rounded-sm" style={{ background: SERIES_COLORS[0], opacity: 0.12 }} aria-hidden /> Middle 50% of asking prices</li>}
        </ul>
      )}
      {!two && band && (
        <p className="mb-2 text-xs text-gray-500 flex items-center gap-1.5">
          <span className="w-3 h-2.5 rounded-sm" style={{ background: SERIES_COLORS[0], opacity: 0.12 }} aria-hidden /> Shaded: middle 50% of asking prices
        </p>
      )}
      <svg width={width} height={height} role="img" aria-label={title} className="block">
        {ticks.map((t) => (
          <g key={t}>
            <line x1={M.left} x2={M.left + plotW} y1={y(t)} y2={y(t)} stroke="#e5e7eb" strokeWidth={1} />
            <text x={M.left - 8} y={y(t)} textAnchor="end" dominantBaseline="middle" className="fill-gray-400 text-[11px] tabular-nums">
              {formatValue(t, unit)}
            </text>
          </g>
        ))}
        {points.map((p, i) => (i === n - 1 || (i % every === 0 && x(n - 1) - x(i) > points[n - 1].label.length * 6 + 12)) && (
          <text key={i} x={i === n - 1 && n > 1 ? Math.min(x(i) + 4, width - 2) : x(i)} y={height - 8}
            textAnchor={i === n - 1 && n > 1 ? "end" : "middle"} className="fill-gray-400 text-[11px]">{p.label}</text>
        ))}
        {band && <path d={bandPath()} fill={SERIES_COLORS[0]} opacity={0.12} />}
        {series.map((s, si) => (
          <path key={s.key} d={pathFor(s.key)} fill="none" stroke={SERIES_COLORS[si]} strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" />
        ))}
        {series.map((s, si) => points.map((p, i) => {
          const v = p.values[s.key];
          const lonely = v != null && (i === 0 || points[i - 1].values[s.key] == null) && (i === n - 1 || points[i + 1].values[s.key] == null);
          return lonely ? <circle key={`${s.key}-${i}`} cx={x(i)} cy={y(v!)} r={4} fill={SERIES_COLORS[si]} stroke="white" strokeWidth={2} /> : null;
        }))}
        {two && ends.map((e) => (
          <text key={e.si} x={M.left + plotW + 8} y={e.y} dominantBaseline="middle" className="fill-gray-600 text-[11px]">
            {series[e.si].label}
          </text>
        ))}
        {hover != null && (
          <g>
            <line x1={x(hover)} x2={x(hover)} y1={M.top} y2={M.top + plotH} stroke="#d1d5db" strokeWidth={1} />
            {series.map((s, si) => {
              const v = points[hover].values[s.key];
              return v == null ? null : <circle key={s.key} cx={x(hover)} cy={y(v)} r={4.5} fill={SERIES_COLORS[si]} stroke="white" strokeWidth={2} />;
            })}
          </g>
        )}
        <rect x={M.left} y={M.top} width={plotW} height={plotH} fill="transparent" onPointerMove={onMove} onPointerLeave={() => setHover(null)} />
      </svg>
      {hover != null && (
        <div className="absolute pointer-events-none z-10 rounded-lg bg-gray-900 text-white text-xs px-3 py-2 shadow-lg tabular-nums"
          style={{ left: Math.min(Math.max(x(hover) - 70, 0), width - 160), top: two ? 28 : 4 }}>
          <p className="font-semibold mb-0.5">{points[hover].label}</p>
          {series.map((s, si) => (
            <p key={s.key} className="flex items-center gap-1.5">
              <span className="w-2 h-2 rounded-full" style={{ background: SERIES_COLORS[si] }} aria-hidden />
              {s.label}: {formatValue(points[hover].values[s.key], unit)}
            </p>
          ))}
          {band && points[hover].values[band.low] != null && (
            <p className="text-gray-300">Middle 50%: {formatValue(points[hover].values[band.low], unit)}–{formatValue(points[hover].values[band.high], unit)}</p>
          )}
        </div>
      )}
      <table className="sr-only">
        <caption>{title}</caption>
        <thead><tr><th>Period</th>{series.map((s) => <th key={s.key}>{s.label}</th>)}</tr></thead>
        <tbody>{points.map((p) => <tr key={p.label}><td>{p.label}</td>{series.map((s) => <td key={s.key}>{formatValue(p.values[s.key], unit)}</td>)}</tr>)}</tbody>
      </table>
    </div>
  );
}
