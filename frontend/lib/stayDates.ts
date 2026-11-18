import type { BusyRange } from "./types";

/** Calendar maths for short stays. Dates are ISO "YYYY-MM-DD" strings in the guest's local
 *  calendar; a stay [checkIn, checkOut) occupies the nights from check-in up to, but not
 *  including, the checkout morning — so one guest's checkout day can be the next guest's check-in. */

export function toISO(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

export function fromISO(iso: string): Date {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, m - 1, d);
}

export function addDays(iso: string, days: number): string {
  const d = fromISO(iso);
  d.setDate(d.getDate() + days);
  return toISO(d);
}

export function nightsBetween(checkIn: string, checkOut: string): number {
  return Math.round((fromISO(checkOut).getTime() - fromISO(checkIn).getTime()) / 86_400_000);
}

/** Is the night starting on `day` taken? */
export function isNightBusy(day: string, busy: BusyRange[]): boolean {
  return busy.some((r) => r.start <= day && day < r.end);
}

/** Can a stay occupy every night from checkIn up to checkOut? */
export function isRangeFree(checkIn: string, checkOut: string, busy: BusyRange[]): boolean {
  return busy.every((r) => !(r.start < checkOut && r.end > checkIn));
}

export interface Selection {
  checkIn: string | null;
  checkOut: string | null;
}

/** Applies a tap on `day` to the current selection (first tap = check-in, second = check-out). */
export function selectDay(current: Selection, day: string, busy: BusyRange[], today: string): Selection {
  if (day < today) return current;
  const startNew = (): Selection => (isNightBusy(day, busy) ? current : { checkIn: day, checkOut: null });
  if (!current.checkIn || current.checkOut || day <= current.checkIn) return startNew();
  if (!isRangeFree(current.checkIn, day, busy)) return startNew();
  return { checkIn: current.checkIn, checkOut: day };
}

/** Weeks of a month for a Monday-first grid; null pads the leading/trailing cells. */
export function monthGrid(year: number, month: number): (string | null)[][] {
  const first = new Date(year, month, 1);
  const lead = (first.getDay() + 6) % 7;
  const days = new Date(year, month + 1, 0).getDate();
  const cells: (string | null)[] = Array.from({ length: lead }, () => null);
  for (let d = 1; d <= days; d++) cells.push(toISO(new Date(year, month, d)));
  while (cells.length % 7) cells.push(null);
  const weeks: (string | null)[][] = [];
  for (let i = 0; i < cells.length; i += 7) weeks.push(cells.slice(i, i + 7));
  return weeks;
}

export function formatStayDate(iso: string, opts: Intl.DateTimeFormatOptions = { day: "numeric", month: "short" }): string {
  return fromISO(iso).toLocaleDateString("en-GB", opts);
}
