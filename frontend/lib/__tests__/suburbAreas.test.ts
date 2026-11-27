import { describe, expect, it } from "vitest";
import { approximateAreas, clipConvex } from "../suburbAreas";

const metres = (a: [number, number], b: [number, number]) => {
  const kx = Math.cos((a[1] * Math.PI) / 180) * 111_320;
  return Math.hypot((a[0] - b[0]) * kx, (a[1] - b[1]) * 110_540);
};
const pointInRing = ([x, y]: [number, number], ring: [number, number][]) => {
  let inside = false;
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const [xi, yi] = ring[i], [xj, yj] = ring[j];
    if ((yi > y) !== (yj > y) && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) inside = !inside;
  }
  return inside;
};

describe("approximate suburb areas", () => {
  const centres = [
    { key: "avondale", lat: -17.795, lng: 31.035 },
    { key: "belgravia", lat: -17.807, lng: 31.045 },
    { key: "borrowdale", lat: -17.755, lng: 31.095 },
  ];

  it("gives every suburb a closed ring that contains its own centre", () => {
    const areas = approximateAreas(centres);
    expect(areas.size).toBe(3);
    for (const c of centres) {
      const ring = areas.get(c.key)!;
      expect(ring[0]).toEqual(ring[ring.length - 1]);
      expect(pointInRing([c.lng, c.lat], ring)).toBe(true);
    }
  });

  it("never reaches further than the radius from the centre", () => {
    const areas = approximateAreas(centres, 2000);
    for (const c of centres) {
      for (const p of areas.get(c.key)!) expect(metres(p, [c.lng, c.lat])).toBeLessThanOrEqual(2001);
    }
  });

  it("neighbouring areas don't overlap: a centre is never inside another suburb's area", () => {
    const areas = approximateAreas(centres);
    for (const c of centres) {
      for (const other of centres.filter((o) => o.key !== c.key)) {
        expect(pointInRing([c.lng, c.lat], areas.get(other.key)!)).toBe(false);
      }
    }
  });

  it("a single suburb is a circle, and two at the same point still each get one", () => {
    const solo = approximateAreas([{ key: "solo", lat: -17.8, lng: 31 }], 2000).get("solo")!;
    const distances = solo.map((p) => metres(p, [31, -17.8]));
    expect(Math.min(...distances)).toBeGreaterThan(1990); // every point on the 2 km circle
    expect(Math.max(...distances)).toBeLessThanOrEqual(2001);
    const twins = approximateAreas([{ key: "a", lat: -17.8, lng: 31 }, { key: "b", lat: -17.8, lng: 31 }]);
    expect(twins.get("a")).toBeDefined();
    expect(twins.get("b")).toBeDefined();
  });

  it("clips a square to a smaller convex window", () => {
    const square: [number, number][] = [[0, 0], [10, 0], [10, 10], [0, 10]];
    const window: [number, number][] = [[5, 5], [15, 5], [15, 15], [5, 15]];
    const clipped = clipConvex(square, window);
    const xs = clipped.map((p) => p[0]), ys = clipped.map((p) => p[1]);
    expect([Math.min(...xs), Math.max(...xs), Math.min(...ys), Math.max(...ys)]).toEqual([5, 10, 5, 10]);
  });
});
