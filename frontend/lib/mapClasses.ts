/** Sequential blue (validated ordinal steps 250→650 of the dataviz palette) for magnitudes on the market map. */
export const SEQ = ["#84b9f3", "#519ced", "#257de0", "#1b64be", "#0f4a96"];

/** Quantile class breaks over the suburbs that have a value; repeated breaks (tied values) are merged. */
export function breaks(vals: number[]): number[] {
  const sorted = [...vals].sort((a, b) => a - b);
  if (sorted.length === 0) return [];
  const cuts = [0.2, 0.4, 0.6, 0.8].map((q) => sorted[Math.min(sorted.length - 1, Math.floor(q * sorted.length))]);
  return [...new Set(cuts)].filter((c) => c < sorted[sorted.length - 1]);
}

/** Colours for k classes, spread across the ramp so neighbouring classes stay distinguishable. */
export function rampFor(k: number): string[] {
  if (k <= 1) return [SEQ[2]];
  return Array.from({ length: k }, (_, i) => SEQ[Math.round((i * (SEQ.length - 1)) / (k - 1))]);
}
