/** Business dates are Africa/Harare calendar days, as ISO yyyy-mm-dd strings. */
const harare = new Intl.DateTimeFormat("en-CA", { timeZone: "Africa/Harare", year: "numeric", month: "2-digit", day: "2-digit" });

export function harareToday(now: Date = new Date()): string {
  return harare.format(now);
}

export function addDays(isoDate: string, days: number): string {
  const d = new Date(`${isoDate}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

export function formatDate(isoDate?: string | null): string {
  if (!isoDate) return "—";
  return new Intl.DateTimeFormat("en-GB", { dateStyle: "medium", timeZone: "UTC" }).format(new Date(`${isoDate.slice(0, 10)}T12:00:00Z`));
}
