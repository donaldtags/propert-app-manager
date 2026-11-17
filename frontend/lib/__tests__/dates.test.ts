import { describe, expect, it } from "vitest";
import { addDays, harareToday } from "../dates";

describe("harareToday", () => {
  it("uses the Harare calendar day (UTC+2), not UTC", () => {
    // 23:30 UTC on 17 Nov is already 01:30 on 18 Nov in Harare.
    expect(harareToday(new Date("2026-11-17T23:30:00Z"))).toBe("2026-11-18");
    expect(harareToday(new Date("2026-11-17T21:30:00Z"))).toBe("2026-11-17");
  });
});

describe("addDays", () => {
  it("crosses month and year boundaries", () => {
    expect(addDays("2026-11-17", 90)).toBe("2027-02-15");
    expect(addDays("2026-12-31", 1)).toBe("2027-01-01");
  });
});

describe("formatDate", () => {
  it("renders a readable day month year", async () => {
    const { formatDate } = await import("../dates");
    expect(formatDate("2026-11-17")).toBe("17 Nov 2026");
    expect(formatDate(undefined)).toBe("—");
  });
});
