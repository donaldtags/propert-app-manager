"use client";

import { useMemo, useState } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import type { BusyRange } from "@/lib/types";
import { fromISO, isNightBusy, monthGrid, selectDay, type Selection } from "@/lib/stayDates";

const WEEKDAYS = ["Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"];

/** Month calendar for picking a stay: taken nights are struck through, the chosen nights are
 *  highlighted. First tap = check-in, second tap = check-out. */
export default function AvailabilityCalendar({
  busy,
  today,
  value,
  onChange,
}: {
  busy: BusyRange[];
  today: string;
  value: Selection;
  onChange: (next: Selection) => void;
}) {
  const start = value.checkIn ? fromISO(value.checkIn) : fromISO(today);
  const [cursor, setCursor] = useState({ y: start.getFullYear(), m: start.getMonth() });
  const weeks = useMemo(() => monthGrid(cursor.y, cursor.m), [cursor]);
  const label = new Date(cursor.y, cursor.m, 1).toLocaleDateString("en-GB", { month: "long", year: "numeric" });
  const todayDate = fromISO(today);
  const atFirstMonth = cursor.y === todayDate.getFullYear() && cursor.m === todayDate.getMonth();

  const move = (delta: number) =>
    setCursor(({ y, m }) => {
      const d = new Date(y, m + delta, 1);
      return { y: d.getFullYear(), m: d.getMonth() };
    });

  return (
    <div className="select-none" aria-label="Availability calendar">
      <div className="flex items-center justify-between mb-2">
        <button
          type="button"
          onClick={() => move(-1)}
          disabled={atFirstMonth}
          aria-label="Previous month"
          className="w-10 h-10 flex items-center justify-center rounded-lg hover:bg-gray-100 disabled:opacity-30"
        >
          <ChevronLeft className="w-5 h-5" />
        </button>
        <p className="text-sm font-semibold text-gray-900">{label}</p>
        <button type="button" onClick={() => move(1)} aria-label="Next month" className="w-10 h-10 flex items-center justify-center rounded-lg hover:bg-gray-100">
          <ChevronRight className="w-5 h-5" />
        </button>
      </div>
      <div className="grid grid-cols-7 text-center text-xs font-medium text-gray-400 mb-1">
        {WEEKDAYS.map((d) => (
          <span key={d}>{d}</span>
        ))}
      </div>
      <div className="grid grid-cols-7 gap-y-1">
        {weeks.flat().map((day, i) => {
          if (!day) return <span key={`pad-${i}`} />;
          const past = day < today;
          const taken = isNightBusy(day, busy);
          const isIn = day === value.checkIn;
          const isOut = day === value.checkOut;
          const inside = value.checkIn && value.checkOut && day > value.checkIn && day < value.checkOut;
          // A taken night can still be a checkout morning, so only past days are fully disabled.
          const cls = isIn || isOut
            ? "bg-brand-600 text-white font-semibold"
            : inside
              ? "bg-brand-50 text-brand-800"
              : past
                ? "text-gray-300"
                : taken
                  ? "text-gray-400 line-through decoration-gray-400"
                  : "text-gray-800 hover:bg-gray-100";
          return (
            <button
              key={day}
              type="button"
              disabled={past}
              onClick={() => onChange(selectDay(value, day, busy, today))}
              aria-label={`${fromISO(day).toLocaleDateString("en-GB", { weekday: "long", day: "numeric", month: "long" })}${taken ? ", booked" : ""}`}
              aria-pressed={isIn || isOut}
              className={`h-9 rounded-lg text-sm transition-colors ${cls}`}
            >
              {Number(day.slice(8))}
            </button>
          );
        })}
      </div>
      <p className="mt-2 text-xs text-gray-500">
        <span className="line-through">12</span> = booked · tap your check-in, then your check-out day
      </p>
    </div>
  );
}
