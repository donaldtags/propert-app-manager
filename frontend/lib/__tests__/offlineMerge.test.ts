import { describe, expect, it } from "vitest";
import { threeWayMerge } from "../offline/merge";

describe("threeWayMerge (conflict rule)", () => {
  const base = { titleType: "TITLE_DEED", titleReference: "DT 1" };

  it("applies my change when the server hasn't touched the field", () => {
    const r = threeWayMerge(base, { ...base, titleReference: "DT 2" }, base);
    expect(r.merged.titleReference).toBe("DT 2");
    expect(r.applied).toEqual(["titleReference"]);
    expect(r.conflicts).toEqual([]);
  });

  it("server wins when both changed the same field, and records my value", () => {
    const r = threeWayMerge(base, { ...base, titleReference: "MINE" }, { ...base, titleReference: "THEIRS" });
    expect(r.merged.titleReference).toBe("THEIRS");
    expect(r.applied).toEqual([]);
    expect(r.conflicts).toEqual([{ field: "titleReference", base: "DT 1", mine: "MINE", theirs: "THEIRS" }]);
  });

  it("merges different fields changed on each side", () => {
    const r = threeWayMerge(base, { ...base, titleReference: "MINE" }, { ...base, titleType: "CESSION" });
    expect(r.merged).toEqual({ titleType: "CESSION", titleReference: "MINE" });
    expect(r.conflicts).toEqual([]);
  });

  it("is a no-op when the server already has my value", () => {
    const r = threeWayMerge(base, { ...base, titleReference: "SAME" }, { ...base, titleReference: "SAME" });
    expect(r.applied).toEqual([]);
    expect(r.conflicts).toEqual([]);
  });

  it("treats null and undefined as equal", () => {
    const r = threeWayMerge({ a: null }, { a: "x" }, { a: undefined } as unknown as { a: string | null });
    expect(r.applied).toEqual(["a"]);
  });
});
