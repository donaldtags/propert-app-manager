import { describe, expect, it } from "vitest";
import { breaks, rampFor } from "../mapClasses";

describe("map colour classes", () => {
  it("merges tied breaks so the legend never repeats a range", () => {
    expect(breaks([7, 8, 8, 8, 8, 8, 9])).toEqual([8]);
    expect(breaks([5, 5, 5])).toEqual([]);
  });
  it("uses quantiles when values differ", () => {
    expect(breaks([1, 2, 3, 4, 5, 6, 7, 8, 9, 10])).toEqual([3, 5, 7, 9]);
  });
  it("spreads few classes across the whole ramp", () => {
    expect(rampFor(2)).toEqual(["#84b9f3", "#0f4a96"]);
    expect(rampFor(5)).toHaveLength(5);
    expect(new Set(rampFor(3)).size).toBe(3);
  });
});
