import { describe, expect, it } from "vitest";
import { addDays, isNightBusy, isRangeFree, monthGrid, nightsBetween, selectDay } from "../stayDates";

const busy = [{ start: "2026-12-10", end: "2026-12-13" }];
const today = "2026-12-01";

describe("stay dates", () => {
  it("counts nights and adds days across month ends", () => {
    expect(nightsBetween("2026-12-30", "2027-01-02")).toBe(3);
    expect(addDays("2026-02-28", 1)).toBe("2026-03-01");
  });

  it("treats the checkout morning as free", () => {
    expect(isNightBusy("2026-12-12", busy)).toBe(true);
    expect(isNightBusy("2026-12-13", busy)).toBe(false);
    expect(isRangeFree("2026-12-08", "2026-12-10", busy)).toBe(true);
    expect(isRangeFree("2026-12-13", "2026-12-15", busy)).toBe(true);
    expect(isRangeFree("2026-12-09", "2026-12-11", busy)).toBe(false);
  });

  it("first tap picks check-in, second picks check-out, crossing a booking restarts", () => {
    let s = selectDay({ checkIn: null, checkOut: null }, "2026-12-05", busy, today);
    expect(s).toEqual({ checkIn: "2026-12-05", checkOut: null });
    s = selectDay(s, "2026-12-08", busy, today);
    expect(s).toEqual({ checkIn: "2026-12-05", checkOut: "2026-12-08" });
    // Third tap starts again.
    s = selectDay(s, "2026-12-06", busy, today);
    expect(s).toEqual({ checkIn: "2026-12-06", checkOut: null });
    // Checking out on the first booked morning is fine...
    expect(selectDay(s, "2026-12-10", busy, today)).toEqual({ checkIn: "2026-12-06", checkOut: "2026-12-10" });
    // ...but spanning the booking is not: the tap becomes a new check-in if that night is free.
    expect(selectDay(s, "2026-12-14", busy, today)).toEqual({ checkIn: "2026-12-14", checkOut: null });
    // Past days and busy nights can't be check-ins.
    expect(selectDay(s, "2026-11-20", busy, today)).toBe(s);
    expect(selectDay({ checkIn: null, checkOut: null }, "2026-12-11", busy, today)).toEqual({ checkIn: null, checkOut: null });
  });

  it("builds Monday-first month grids", () => {
    const weeks = monthGrid(2026, 11); // December 2026 starts on a Tuesday
    expect(weeks[0][0]).toBeNull();
    expect(weeks[0][1]).toBe("2026-12-01");
    expect(weeks.flat().filter(Boolean)).toHaveLength(31);
    expect(weeks.every((w) => w.length === 7)).toBe(true);
  });
});
