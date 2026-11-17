import { describe, expect, it, vi } from "vitest";

vi.mock("../api", () => ({ locations: { all: vi.fn() } }));

import { buildIndex, resolveLocation } from "../locations";
import type { LocationNode } from "../types";

const nodes: LocationNode[] = [
  { id: 1, type: "PROVINCE", name: "Harare Metropolitan", verified: true },
  { id: 2, type: "CITY", name: "Harare", parentId: 1, verified: true },
  { id: 3, type: "TOWN", name: "Ruwa", parentId: 1, verified: true },
  { id: 4, type: "PROVINCE", name: "Bulawayo Metropolitan", verified: true },
  { id: 5, type: "CITY", name: "Bulawayo", parentId: 4, verified: true },
  { id: 10, type: "SUBURB", name: "Mabelreign", parentId: 2, aliases: ["Malbereign"], verified: true },
  { id: 11, type: "SUBURB", name: "Borrowdale", parentId: 2, verified: true },
  { id: 12, type: "SUBURB", name: "Damofalls", parentId: 3, verified: true },
  { id: 13, type: "SUBURB", name: "Burnside", parentId: 5, verified: true },
];

describe("buildIndex", () => {
  const index = buildIndex(nodes);

  it("orders major cities first, then towns", () => {
    expect(index.cities).toEqual(["Harare", "Bulawayo", "Ruwa"]);
  });

  it("groups suburbs under their city, alphabetically", () => {
    expect(index.suburbs.Harare).toEqual(["Borrowdale", "Mabelreign"]);
    expect(index.suburbs.Ruwa).toEqual(["Damofalls"]);
    expect(index.cityIds.Bulawayo).toBe(5);
  });

  it("does not treat provinces as selectable cities", () => {
    expect(index.cities).not.toContain("Harare Metropolitan");
  });
});

describe("resolveLocation", () => {
  const index = buildIndex(nodes);

  it("resolves cities, suburbs, aliases and prefixes", () => {
    expect(resolveLocation(index, "harare")).toEqual({ city: "Harare", suburb: "" });
    expect(resolveLocation(index, "Burnside")).toEqual({ city: "Bulawayo", suburb: "Burnside" });
    expect(resolveLocation(index, "malbereign")).toEqual({ city: "Harare", suburb: "Mabelreign" });
    expect(resolveLocation(index, "borrow")).toEqual({ city: "Harare", suburb: "Borrowdale" });
    expect(resolveLocation(index, "nowhere")).toBeNull();
  });
});
