/** Money is integer cents end to end; these helpers are the only place cents meet text. */

export function formatCents(cents: number | null | undefined, currency = "USD"): string {
  if (cents == null) return "—";
  const code = currency === "ZWG" ? "ZWG" : currency;
  try {
    return new Intl.NumberFormat("en-ZW", { style: "currency", currency: code, maximumFractionDigits: cents % 100 === 0 ? 0 : 2 }).format(cents / 100);
  } catch {
    return `${code} ${(cents / 100).toLocaleString("en-ZW")}`;
  }
}

/**
 * Parses what someone typed ("250000", "250,000.50", "US$ 1 200") into cents without floating
 * point error. Returns null for anything that isn't a positive amount with at most two decimals.
 */
export function parseToCents(input: string): number | null {
  const cleaned = input.replace(/[^\d.]/g, "");
  if (!/^\d+(\.\d{1,2})?$/.test(cleaned)) return null;
  const [whole, fraction = ""] = cleaned.split(".");
  const cents = Number(whole) * 100 + Number(fraction.padEnd(2, "0"));
  return Number.isSafeInteger(cents) && cents > 0 ? cents : null;
}
