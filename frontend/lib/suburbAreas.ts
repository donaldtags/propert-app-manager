import { Delaunay } from "d3-delaunay";

/** [longitude, latitude], GeoJSON order. Rings are closed (first point repeated at the end). */
export type LngLat = [number, number];

export interface Centre {
  key: string;
  lat: number;
  lng: number;
}

/**
 * Approximate suburb areas when no boundary data is on file: each suburb gets the part of the map
 * closer to its centre than to any other suburb's centre (a Voronoi cell), cut to at most
 * `radiusM` metres from the centre so outlying suburbs don't sprawl. These are NOT official
 * boundaries and must be labelled as approximate wherever they're shown.
 */
export function approximateAreas(centres: Centre[], radiusM = 2000, segments = 48): Map<string, LngLat[]> {
  const out = new Map<string, LngLat[]>();
  if (centres.length === 0) return out;
  const lat0 = centres.reduce((s, c) => s + c.lat, 0) / centres.length;
  const lng0 = centres.reduce((s, c) => s + c.lng, 0) / centres.length;
  const kx = Math.cos((lat0 * Math.PI) / 180) * 111_320;
  const ky = 110_540;
  const toXY = (c: Centre): [number, number] => [(c.lng - lng0) * kx, (c.lat - lat0) * ky];
  const toLngLat = ([x, y]: [number, number]): LngLat => [lng0 + x / kx, lat0 + y / ky];

  const xy = centres.map(toXY);
  const xs = xy.map((p) => p[0]);
  const ys = xy.map((p) => p[1]);
  const bounds: [number, number, number, number] = [
    Math.min(...xs) - radiusM, Math.min(...ys) - radiusM, Math.max(...xs) + radiusM, Math.max(...ys) + radiusM,
  ];
  const voronoi = Delaunay.from(xy).voronoi(bounds);

  centres.forEach((c, i) => {
    const circle = circlePolygon(xy[i], radiusM, segments);
    const cell = voronoi.cellPolygon(i) as [number, number][] | null;
    // Two suburbs at exactly the same point share no cell; fall back to the circle.
    const ring = cell && cell.length >= 4 ? clipConvex(cell.slice(0, -1), circle) : circle;
    if (ring.length < 3) return;
    const closed = [...ring, ring[0]].map(toLngLat);
    out.set(c.key, closed);
  });
  return out;
}

function circlePolygon([cx, cy]: [number, number], r: number, n: number): [number, number][] {
  return Array.from({ length: n }, (_, k) => {
    const a = (2 * Math.PI * k) / n; // counter-clockwise
    return [cx + r * Math.cos(a), cy + r * Math.sin(a)] as [number, number];
  });
}

/** Sutherland–Hodgman: `subject` cut by the convex, counter-clockwise `clip` polygon (both open rings). */
export function clipConvex(subject: [number, number][], clip: [number, number][]): [number, number][] {
  let output = orient(subject);
  for (let i = 0; i < clip.length && output.length > 0; i++) {
    const a = clip[i];
    const b = clip[(i + 1) % clip.length];
    const input = output;
    output = [];
    const inside = (p: [number, number]) => (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0]) >= 0;
    for (let j = 0; j < input.length; j++) {
      const cur = input[j];
      const prev = input[(j + input.length - 1) % input.length];
      const curIn = inside(cur);
      const prevIn = inside(prev);
      if (curIn) {
        if (!prevIn) output.push(intersect(prev, cur, a, b));
        output.push(cur);
      } else if (prevIn) {
        output.push(intersect(prev, cur, a, b));
      }
    }
  }
  return output;
}

function orient(ring: [number, number][]): [number, number][] {
  let area = 0;
  for (let i = 0; i < ring.length; i++) {
    const [x1, y1] = ring[i];
    const [x2, y2] = ring[(i + 1) % ring.length];
    area += x1 * y2 - x2 * y1;
  }
  return area < 0 ? [...ring].reverse() : ring;
}

function intersect(p: [number, number], q: [number, number], a: [number, number], b: [number, number]): [number, number] {
  const [x1, y1] = p, [x2, y2] = q, [x3, y3] = a, [x4, y4] = b;
  const d = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
  if (d === 0) return q;
  const t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / d;
  return [x1 + t * (x2 - x1), y1 + t * (y2 - y1)];
}

/** Lower-case suburb name, trimmed, for matching boundary features to analytics rows. */
export const suburbKey = (suburb: string) => suburb.trim().toLowerCase();

/**
 * Real suburb boundaries, if someone has put a GeoJSON file at /geo/suburb-boundaries.geojson
 * (Polygon or MultiPolygon features with a `name` or `suburb` property). Returns null when there
 * is no such file, so callers fall back to approximate areas.
 */
export async function loadBoundaryFile(url = "/geo/suburb-boundaries.geojson"): Promise<Map<string, LngLat[]> | null> {
  try {
    const res = await fetch(url);
    if (!res.ok) return null;
    const fc = await res.json();
    const out = new Map<string, LngLat[]>();
    for (const f of fc?.features ?? []) {
      const name = f?.properties?.name ?? f?.properties?.suburb ?? f?.properties?.NAME;
      const g = f?.geometry;
      if (!name || !g) continue;
      let ring: LngLat[] | undefined;
      if (g.type === "Polygon") ring = g.coordinates[0];
      else if (g.type === "MultiPolygon") {
        // the largest part
        ring = (g.coordinates as LngLat[][][]).map((p) => p[0]).sort((a, b) => b.length - a.length)[0];
      }
      if (ring && ring.length >= 4) out.set(suburbKey(String(name)), ring);
    }
    return out.size > 0 ? out : null;
  } catch {
    return null;
  }
}
