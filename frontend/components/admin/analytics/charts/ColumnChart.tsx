"use client";

import { useState } from "react";
import { formatValue } from "../format";
import { niceTicks, SERIES_COLORS, useWidth } from "./useWidth";

const M = { top: 12, bottom: 30, left: 44, right: 12 };

/** Counts per period: thin columns (≤24px, rounded top) from one baseline, with a hover tooltip. */
export default function ColumnChart({ points, valueKey, label, height = 180, title }: {
  points: { label: string; values: Record<string, number | undefined> }[];
  valueKey: string;
  label: string;
  height?: number;
  title: string;
}) {
  const [ref, width] = useWidth<HTMLDivElement>();
  const [hover, setHover] = useState<number | null>(null);
  const vals = points.map((p) => p.values[valueKey] ?? 0);
  const plotW = Math.max(40, width - M.left - M.right);
  const plotH = height - M.top - M.bottom;
  // Counts: at least 0–4 so the axis steps are whole numbers.
  const ticks = niceTicks(Math.max(4, ...vals));
  const top = ticks[ticks.length - 1];
  const slot = plotW / Math.max(1, points.length);
  const bw = Math.max(2, Math.min(24, slot * 0.6));
  const y = (v: number) => M.top + plotH - (v / top) * plotH;
  const every = Math.max(1, Math.ceil(points.length / Math.max(2, Math.floor(plotW / 72))));
  const total = vals.reduce((a, b) => a + b, 0);
  if (points.length === 0) return <div ref={ref} />;

  return (
    <div ref={ref} className="relative">
      <svg width={width} height={height} role="img" aria-label={`${title}: ${total} in total`} className="block">
        {ticks.map((t) => (
          <g key={t}>
            <line x1={M.left} x2={M.left + plotW} y1={y(t)} y2={y(t)} stroke="#e5e7eb" strokeWidth={1} />
            <text x={M.left - 8} y={y(t)} textAnchor="end" dominantBaseline="middle" className="fill-gray-400 text-[11px] tabular-nums">{t}</text>
          </g>
        ))}
        {points.map((p, i) => {
          const v = vals[i];
          const cx = M.left + slot * i + slot / 2;
          const h = Math.max(0, (v / top) * plotH);
          const r = Math.min(4, bw / 2, h);
          const x0 = cx - bw / 2;
          const yb = M.top + plotH;
          const d = h === 0 ? "" : `M${x0},${yb} V${yb - h + r} Q${x0},${yb - h} ${x0 + r},${yb - h} H${x0 + bw - r} Q${x0 + bw},${yb - h} ${x0 + bw},${yb - h + r} V${yb} Z`;
          return (
            <g key={i} onPointerEnter={() => setHover(i)} onPointerLeave={() => setHover(null)}>
              <rect x={cx - slot / 2} y={M.top} width={slot} height={plotH} fill="transparent" />
              {d && <path d={d} fill={SERIES_COLORS[0]} opacity={hover == null || hover === i ? 1 : 0.55} />}
              {(i === points.length - 1 || (i % every === 0 && slot * (points.length - 1 - i) > points[points.length - 1].label.length * 6 + 12)) && (
                <text x={i === points.length - 1 && points.length > 1 ? Math.min(cx + slot / 2, width - 2) : cx} y={height - 8}
                  textAnchor={i === points.length - 1 && points.length > 1 ? "end" : "middle"} className="fill-gray-400 text-[11px]">{p.label}</text>
              )}
            </g>
          );
        })}
      </svg>
      {hover != null && (
        <div className="absolute pointer-events-none z-10 rounded-lg bg-gray-900 text-white text-xs px-3 py-1.5 shadow-lg tabular-nums"
          style={{ left: Math.min(Math.max(M.left + slot * hover + slot / 2 - 60, 0), width - 140), top: 0 }}>
          <p className="font-semibold">{points[hover].label}</p>
          <p>{label}: {formatValue(vals[hover], "COUNT")}</p>
        </div>
      )}
      <table className="sr-only">
        <caption>{title}</caption>
        <tbody>{points.map((p, i) => <tr key={p.label}><td>{p.label}</td><td>{vals[i]}</td></tr>)}</tbody>
      </table>
    </div>
  );
}
