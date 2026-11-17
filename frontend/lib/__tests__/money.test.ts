import { describe, expect, it } from "vitest";
import { formatCents, parseToCents } from "../money";

describe("parseToCents", () => {
  it("parses plain, grouped and decorated amounts exactly", () => {
    expect(parseToCents("250000")).toBe(25_000_000);
    expect(parseToCents("250,000.50")).toBe(25_000_050);
    expect(parseToCents("US$ 1 200")).toBe(120_000);
    expect(parseToCents("0.1")).toBe(10);
    expect(parseToCents("19.99")).toBe(1999);
  });

  it("rejects empty, zero, malformed and over-precise input", () => {
    expect(parseToCents("")).toBeNull();
    expect(parseToCents("0")).toBeNull();
    expect(parseToCents("1.234")).toBeNull();
    expect(parseToCents("1.2.3")).toBeNull();
  });
});

describe("formatCents", () => {
  it("formats whole and fractional amounts", () => {
    expect(formatCents(25_000_000)).toContain("250,000");
    expect(formatCents(1999)).toContain("19.99");
    expect(formatCents(undefined)).toBe("—");
  });
});
