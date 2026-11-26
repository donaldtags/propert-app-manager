import type { OverviewGrain, OverviewKpi } from "@/lib/api";

export function formatValue(v: number | null | undefined, unit: OverviewKpi["unit"]): string {
  if (v == null) return "—";
  if (unit === "USD") {
    const abs = Math.abs(v);
    if (abs >= 1_000_000) return `$${(v / 1_000_000).toFixed(abs >= 10_000_000 ? 0 : 1)}m`;
    if (abs >= 100_000) return `$${Math.round(v / 1000)}k`;
    return `$${v.toLocaleString(undefined, { maximumFractionDigits: abs < 100 ? 2 : 0 })}`;
  }
  if (unit === "PERCENT") return `${v.toFixed(1)}%`;
  return Math.round(v).toLocaleString();
}

export function signed(v: number, unit: OverviewKpi["unit"]): string {
  const s = formatValue(Math.abs(v), unit);
  return v > 0 ? `+${s}` : v < 0 ? `−${s}` : s;
}

/** Good, bad or neutral, from the direction of change and which way is better for this KPI. */
export function tone(k: OverviewKpi): "good" | "bad" | "neutral" {
  if (!k.comparable || k.better === "NEUTRAL" || k.direction === "FLAT" || k.direction === "NONE") return "neutral";
  const up = k.direction === "UP" || k.direction === "NEW";
  return (up && k.better === "UP") || (!up && k.better === "DOWN") ? "good" : "bad";
}

export function pointLabel(iso: string, grain: OverviewGrain): string {
  const d = new Date(iso);
  switch (grain) {
    case "DAY": return d.toLocaleDateString(undefined, { day: "numeric", month: "short" });
    case "WEEK": return `w/c ${d.toLocaleDateString(undefined, { day: "numeric", month: "short" })}`;
    case "MONTH": return d.toLocaleDateString(undefined, { month: "short", year: "numeric" });
    case "QUARTER": return `Q${Math.floor(d.getMonth() / 3) + 1} ${d.getFullYear()}`;
    case "YEAR": return String(d.getFullYear());
  }
}

export function time(iso?: string): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });
}
