"use client";

import { useId, useState } from "react";
import type { OverviewGrain, OverviewKpi, OverviewPoint } from "@/lib/api";
import { formatValue, pointLabel } from "./format";

const W = 160;
const H = 40;
const PAD = 4;

/**
 * A single-series trend for a KPI tile: 2px line, faint area, a dot on the latest point and a
 * hover crosshair with the period's value. No axes - the tile's number is the headline; this
 * only shows the shape. Periods recorded before history began are simply absent.
 */
export default function Sparkline({ points, grain, unit, label }: {
  points: OverviewPoint[];
  grain: OverviewGrain;
  unit: OverviewKpi["unit"];
  label: string;
}) {
  const id = useId();
  const [hover, setHover] = useState<number | null>(null);
  if (points.length < 2) {
    return <p className="text-[11px] text-gray-400 h-10 flex items-end">Trend appears after two periods of history</p>;
  }
  const values = points.map((p) => p.value);
  const min = Math.min(...values, 0);
  const max = Math.max(...values);
  const span = max - min || 1;
  const x = (i: number) => PAD + (i * (W - 2 * PAD)) / (points.length - 1);
  const y = (v: number) => H - PAD - ((v - min) / span) * (H - 2 * PAD);
  const line = points.map((p, i) => `${i ? "L" : "M"}${x(i).toFixed(1)},${y(p.value).toFixed(1)}`).join(" ");
  const area = `${line} L${x(points.length - 1).toFixed(1)},${H - PAD} L${x(0).toFixed(1)},${H - PAD} Z`;
  const last = points.length - 1;
  const shown = hover ?? last;

  const onMove = (e: React.PointerEvent<SVGSVGElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const rel = ((e.clientX - rect.left) / rect.width) * W;
    const i = Math.round(((rel - PAD) / (W - 2 * PAD)) * (points.length - 1));
    setHover(Math.max(0, Math.min(last, i)));
  };

  return (
    <figure className="relative">
      <svg
        viewBox={`0 0 ${W} ${H}`}
        className="w-full h-10 overflow-visible touch-none"
        role="img"
        aria-label={`${label}: trend over the last ${points.length} periods, from ${formatValue(values[0], unit)} to ${formatValue(values[last], unit)}`}
        onPointerMove={onMove}
        onPointerLeave={() => setHover(null)}
      >
        <defs>
          <linearGradient id={`${id}-fill`} x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor="var(--color-brand-500)" stopOpacity="0.16" />
            <stop offset="100%" stopColor="var(--color-brand-500)" stopOpacity="0" />
          </linearGradient>
        </defs>
        <path d={area} fill={`url(#${id}-fill)`} />
        <path d={line} fill="none" stroke="var(--color-brand-500)" strokeWidth={2} strokeLinejoin="round" strokeLinecap="round"
          vectorEffect="non-scaling-stroke" />
        {hover != null && (
          <line x1={x(hover)} x2={x(hover)} y1={PAD} y2={H - PAD} stroke="var(--color-brand-300)" strokeWidth={1}
            vectorEffect="non-scaling-stroke" />
        )}
        <circle cx={x(shown)} cy={y(points[shown].value)} r={3.5} fill="var(--color-brand-600)" stroke="white" strokeWidth={2}
          vectorEffect="non-scaling-stroke" />
      </svg>
      {hover != null && (
        <figcaption className="absolute -top-7 right-0 rounded-md bg-gray-900 text-white text-[11px] px-2 py-0.5 whitespace-nowrap tabular-nums pointer-events-none">
          {pointLabel(points[hover].start, grain)}: {formatValue(points[hover].value, unit)}
        </figcaption>
      )}
    </figure>
  );
}
