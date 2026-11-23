import { describe, expect, it } from "vitest";
import { safeRedirect } from "../safeRedirect";

const params = (q: string) => new URLSearchParams(q);

describe("safeRedirect", () => {
  it("keeps same-site paths", () => {
    expect(safeRedirect(params("redirect=%2Forgs%2Festatex%3Ftab%3D1"))).toBe("/orgs/estatex?tab=1");
  });
  it("accepts the older next= parameter", () => {
    expect(safeRedirect(params("next=/admin/locations"))).toBe("/admin/locations");
  });
  it.each(["https://evil.example", "//evil.example", "/\\evil.example", "javascript:alert(1)", ""])(
    "rejects %s",
    (bad) => expect(safeRedirect(params(`redirect=${encodeURIComponent(bad)}`))).toBeNull(),
  );
  it("returns null when absent", () => {
    expect(safeRedirect(params(""))).toBeNull();
  });
});
