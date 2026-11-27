"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type * as Leaflet from "leaflet";
import { AlertTriangle, Info, Layers, X } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { geoAnalytics, marketDashboard, type GeoData, type GeoSuburb, type MarketDashboardData, type MarketType } from "@/lib/api";
import { useZimbabweLocations } from "@/lib/locations";
import { addEsriBasemaps, DEFAULT_CENTER, ensureStylesheets, LEAFLET_STYLESHEETS } from "@/lib/maps";
import { CATEGORY_OPTIONS } from "@/components/workspace/listingLabels";
import AlertBanner from "@/components/AlertBanner";
import LineChart from "./charts/LineChart";
import { formatValue } from "./format";
import { breaks, rampFor } from "@/lib/mapClasses";
import { approximateAreas, loadBoundaryFile, suburbKey, type LngLat } from "@/lib/suburbAreas";
import MarketMap3D, { type Area3D } from "./MarketMap3D";

type View = "circles" | "areas" | "3d";
const VIEWS: { key: View; label: string }[] = [
  { key: "circles", label: "Circles" },
  { key: "areas", label: "Areas" },
  { key: "3d", label: "3D" },
];
const areaId = (s: GeoSuburb) => `${s.suburb}|${s.city}`;

/** Diverging for price change: red (falling) – grey (flat) – blue (rising). */
const DIV = [
  { max: -10, color: "#c62f30", label: "Down 10%+" },
  { max: -2, color: "#f19495", label: "Down 2–10%" },
  { max: 2, color: "#c4c4c4", label: "Flat (±2%)" },
  { max: 10, color: "#84b9f3", label: "Up 2–10%" },
  { max: Infinity, color: "#1b64be", label: "Up 10%+" },
];

type Measure = "active" | "askingMedian" | "growthPercent" | "grossYield" | "demandPerListing" | "searches" | "transactions";
const MEASURES: { key: Measure; label: string; unit: "COUNT" | "USD" | "PERCENT"; demand?: boolean; help: string }[] = [
  { key: "active", label: "Supply (live listings)", unit: "COUNT", help: "How many listings are live in each suburb." },
  { key: "askingMedian", label: "Median asking price", unit: "USD", help: "Middle asking price of live listings." },
  { key: "growthPercent", label: "Price change (90 days)", unit: "PERCENT", help: "Change in median asking price over about 90 days. Shown only with 5+ listings at both dates." },
  { key: "grossYield", label: "Estimated gross yield", unit: "PERCENT", help: "Median rent × 12 ÷ median sale price, where 5+ of each are listed." },
  { key: "demandPerListing", label: "Enquiries + viewings per listing", unit: "COUNT", demand: true, help: "Enquiries and viewing requests in the last 90 days per live listing." },
  { key: "searches", label: "Searches", unit: "COUNT", demand: true, help: "Searches naming the suburb in the last 90 days. Interest, not confirmed demand." },
  { key: "transactions", label: "Transactions (90 days)", unit: "COUNT", help: "Leases signed (rent) or verified sales (sale) in the last 90 days." },
];

const value = (s: GeoSuburb, m: Measure) => (s[m] ?? null) as number | null;

/**
 * The market by suburb on a map, in three views:
 *  - Circles at each suburb's centre (size = live listings, colour = the chosen measure).
 *  - Areas: shaded suburb areas. Real boundaries if a GeoJSON file is on the server
 *    (public/geo/suburb-boundaries.geojson); otherwise approximate areas drawn from the suburb
 *    centres, labelled as approximate.
 *  - 3D: the same areas raised in proportion to the measure (MapLibre).
 * Colour is never the only signal: the legend, hover labels, side panel and the table below all
 * carry the numbers.
 */
export default function MarketMap() {
  const { token } = useAuth();
  const locations = useZimbabweLocations();
  const [type, setType] = useState<MarketType>("rent");
  const [city, setCity] = useState("Harare");
  const [category, setCategory] = useState("");
  const [bedrooms, setBedrooms] = useState("");
  const [measure, setMeasure] = useState<Measure>("active");
  const [showPins, setShowPins] = useState(false);
  const [view, setView] = useState<View>("circles");
  const [boundaries, setBoundaries] = useState<Map<string, LngLat[]> | null>(null);
  const [data, setData] = useState<GeoData | null>(null);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState<GeoSuburb | null>(null);
  const mapEl = useRef<HTMLDivElement>(null);
  const mapRef = useRef<Leaflet.Map | null>(null);
  const layerRef = useRef<Leaflet.LayerGroup | null>(null);
  const pinsRef = useRef<Leaflet.Layer | null>(null);
  const LRef = useRef<typeof Leaflet | null>(null);

  const load = useCallback(() => {
    if (!token) return;
    geoAnalytics.get(token, type, { city: city || undefined, category: category || undefined, bedrooms: bedrooms === "" ? undefined : Number(bedrooms) })
      .then((d) => { setData(d); setError(""); })
      .catch((e) => setError(e instanceof Error ? e.message : "Couldn't load the map figures."));
  }, [token, type, city, category, bedrooms]);
  useEffect(load, [load]);

  const m = MEASURES.find((x) => x.key === measure)!;
  const withValue = useMemo(() => (data?.suburbs ?? []).filter((s) => value(s, measure) != null), [data, measure]);
  const cuts = useMemo(() => breaks(withValue.map((s) => value(s, measure)!)), [withValue, measure]);
  const colorFor = useCallback((v: number | null) => {
    if (v == null) return null;
    if (measure === "growthPercent") return DIV.find((d) => v <= d.max)!.color;
    let i = 0;
    while (i < cuts.length && v > cuts[i]) i++;
    return rampFor(cuts.length + 1)[i];
  }, [measure, cuts]);

  // Real suburb boundaries, if a file has been supplied; otherwise areas are approximate.
  useEffect(() => { loadBoundaryFile().then(setBoundaries); }, []);
  const placedSuburbs = useMemo(() => (data?.suburbs ?? []).filter((s) => s.latitude != null && s.longitude != null), [data]);
  const approx = useMemo(() => approximateAreas(placedSuburbs.map((s) => ({ key: areaId(s), lat: s.latitude!, lng: s.longitude! }))), [placedSuburbs]);
  const ringFor = useCallback((s: GeoSuburb) => boundaries?.get(suburbKey(s.suburb)) ?? approx.get(areaId(s)) ?? null, [boundaries, approx]);
  const usingRealBoundaries = !!boundaries && placedSuburbs.some((s) => boundaries.has(suburbKey(s.suburb)));
  const tooltip = useCallback((s: GeoSuburb) => {
    const v = value(s, measure);
    return `<strong>${s.suburb}</strong><br>${m.label}: ${v == null ? "not enough data" : formatValue(v, m.unit)}<br>${s.active} live listing${s.active === 1 ? "" : "s"}`;
  }, [measure, m]);
  const areas3d: Area3D[] = useMemo(() => placedSuburbs.flatMap((s) => {
    const ring = ringFor(s);
    return ring ? [{ id: areaId(s), suburb: s.suburb, ring, value: value(s, measure), color: colorFor(value(s, measure)), label: tooltip(s) }] : [];
  }), [placedSuburbs, ringFor, measure, colorFor, tooltip]);

  // Create the map once.
  useEffect(() => {
    let alive = true;
    import("leaflet").then(async (L) => {
      await import("leaflet.markercluster");
      if (!alive || !mapEl.current || mapRef.current) return;
      ensureStylesheets([...LEAFLET_STYLESHEETS,
        "https://cdnjs.cloudflare.com/ajax/libs/leaflet.markercluster/1.5.3/MarkerCluster.css",
        "https://cdnjs.cloudflare.com/ajax/libs/leaflet.markercluster/1.5.3/MarkerCluster.Default.css"]);
      LRef.current = L;
      const map = L.map(mapEl.current, { center: DEFAULT_CENTER, zoom: 12 });
      addEsriBasemaps(L, map);
      mapRef.current = map;
      layerRef.current = L.layerGroup().addTo(map);
      setData((d) => (d ? { ...d } : d)); // trigger a draw if data arrived first
    });
    return () => {
      alive = false;
      mapRef.current?.remove();
      mapRef.current = null;
    };
  }, []);

  // Draw suburbs (and optionally listing pins) whenever the data or the measure changes.
  useEffect(() => {
    const L = LRef.current;
    const map = mapRef.current;
    if (!L || !map || !layerRef.current || !data) return;
    layerRef.current.clearLayers();
    const placed = placedSuburbs;
    const maxActive = Math.max(1, ...placed.map((s) => s.active));
    placed.forEach((s) => {
      const v = value(s, measure);
      const fill = colorFor(v);
      const ring = view === "areas" ? ringFor(s) : null;
      const shape = ring
        ? L.polygon(ring.map(([lng, lat]) => [lat, lng] as [number, number]), {
          weight: 1.5, color: "#ffffff", fillColor: fill ?? "#e5e7eb", fillOpacity: fill ? 0.7 : 0.45,
          dashArray: fill ? undefined : "3 3",
        })
        : L.circleMarker([s.latitude!, s.longitude!], {
          radius: 7 + 21 * Math.sqrt(s.active / maxActive), weight: 2, color: "#ffffff", fillColor: fill ?? "#ffffff",
          fillOpacity: fill ? 0.85 : 0.6, dashArray: fill ? undefined : "3 3",
        });
      if (!fill) shape.setStyle({ color: "#6b7280" });
      shape.bindTooltip(tooltip(s), { direction: "top", sticky: !!ring });
      shape.on("click", () => setSelected(s));
      shape.addTo(layerRef.current!);
    });
    if (pinsRef.current) { map.removeLayer(pinsRef.current); pinsRef.current = null; }
    if (showPins) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const group = (window as any).L.markerClusterGroup({ maxClusterRadius: 40, showCoverageOnHover: false });
      data.points.forEach((p) => group.addLayer(L.circleMarker([p.latitude, p.longitude], { radius: 4, color: "#ffffff", weight: 1, fillColor: "#111827", fillOpacity: 0.8 })
        .bindTooltip(`${p.suburb ?? ""} ${p.price != null ? formatValue(p.price, "USD") : ""}`)));
      group.addTo(map);
      pinsRef.current = group;
    }
    if (placed.length > 0) {
      map.fitBounds(L.latLngBounds(placed.map((s) => [s.latitude!, s.longitude!] as [number, number])).pad(0.25), { maxZoom: 13 });
    }
  }, [data, measure, colorFor, showPins, m, view, placedSuburbs, ringFor, tooltip]);

  // Leaflet needs to re-measure its box when it's shown again after the 3D view.
  useEffect(() => {
    if (view !== "3d") setTimeout(() => mapRef.current?.invalidateSize(), 0);
  }, [view]);

  const legend = measure === "growthPercent"
    ? DIV.map((d) => ({ color: d.color, label: d.label }))
    : rampFor(cuts.length + 1).map((c, i) => ({
      color: c,
      label: cuts.length === 0 ? (withValue.length ? `all ${formatValue(value(withValue[0], measure), m.unit)}` : "") : i === 0 ? `up to ${formatValue(cuts[0], m.unit)}` : i === cuts.length ? `over ${formatValue(cuts[i - 1], m.unit)}`
        : `${formatValue(cuts[i - 1], m.unit)}–${formatValue(cuts[i], m.unit)}`,
    }));
  const cov = data?.coverage;
  const sel = "border border-gray-200 rounded-lg px-2.5 py-1.5 bg-white text-sm";

  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-xl font-semibold text-gray-900 tracking-tight">Market map</h2>
        <p className="text-sm text-gray-500 mt-0.5">Prices, supply, demand and yields by suburb. Click a suburb for its figures and trend.</p>
      </div>
      <div className="flex flex-wrap items-center gap-2 bg-white border border-gray-200 rounded-xl p-2">
        <div role="radiogroup" aria-label="Market" className="flex rounded-lg bg-gray-100 p-0.5">
          {(["rent", "sale"] as MarketType[]).map((t) => (
            <button key={t} type="button" role="radio" aria-checked={type === t} onClick={() => { setType(t); setSelected(null); }}
              className={`px-3 py-1.5 text-sm rounded-md ${type === t ? "bg-white shadow-sm font-medium text-gray-900" : "text-gray-600"}`}>
              {t === "rent" ? "Rentals" : "Sales"}
            </button>
          ))}
        </div>
        <div role="radiogroup" aria-label="Map view" className="flex rounded-lg bg-gray-100 p-0.5">
          {VIEWS.map((v) => (
            <button key={v.key} type="button" role="radio" aria-checked={view === v.key} onClick={() => setView(v.key)}
              className={`px-3 py-1.5 text-sm rounded-md ${view === v.key ? "bg-white shadow-sm font-medium text-gray-900" : "text-gray-600"}`}>
              {v.label}
            </button>
          ))}
        </div>
        <label className="sr-only" htmlFor="map-measure">Colour suburbs by</label>
        <select id="map-measure" value={measure} onChange={(e) => setMeasure(e.target.value as Measure)} className={sel}>
          {MEASURES.filter((x) => !x.demand || data?.demandIncluded).map((x) => <option key={x.key} value={x.key}>Colour: {x.label}</option>)}
        </select>
        <label className="sr-only" htmlFor="map-city">City</label>
        <select id="map-city" value={city} onChange={(e) => { setCity(e.target.value); setSelected(null); }} className={sel}>
          <option value="">All locations</option>
          {locations.cities.map((c) => <option key={c} value={c}>{c}</option>)}
        </select>
        <label className="sr-only" htmlFor="map-cat">Property type</label>
        <select id="map-cat" value={category} onChange={(e) => setCategory(e.target.value)} className={sel}>
          <option value="">Any type</option>
          {CATEGORY_OPTIONS.filter((c) => c.value !== "OTHER").map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
        </select>
        <label className="sr-only" htmlFor="map-beds">Bedrooms</label>
        <select id="map-beds" value={bedrooms} onChange={(e) => setBedrooms(e.target.value)} className={sel}>
          <option value="">Any bedrooms</option>
          {[0, 1, 2, 3, 4, 5].map((b) => <option key={b} value={b}>{b === 0 ? "Studio" : b === 5 ? "5+ beds" : `${b} bed${b > 1 ? "s" : ""}`}</option>)}
        </select>
        <label className="ml-auto flex items-center gap-1.5 text-sm text-gray-600">
          <input type="checkbox" checked={showPins} onChange={(e) => setShowPins(e.target.checked)} className="accent-brand-600" />
          <Layers className="w-4 h-4" aria-hidden /> Listing pins
        </label>
      </div>

      {error && <AlertBanner variant="error">{error}</AlertBanner>}
      {cov && (
        <p className="text-xs text-gray-600 flex items-start gap-1.5">
          <Info className="w-3.5 h-3.5 mt-0.5 shrink-0" aria-hidden />
          <span>
            {cov.placedOnMap} of {cov.liveListings} live listings are on the map.
            {cov.inUnplacedSuburbs > 0 && ` ${cov.inUnplacedSuburbs} are in suburbs whose location isn't known (listed in the table below).`}
            {cov.withoutSuburb > 0 && ` ${cov.withoutSuburb} have no suburb.`}
            {" "}{data?.notes.filter((n) => !n.includes("live listing(s) are in suburbs")).join(" ")}
          </span>
        </p>
      )}

      <div className="relative bg-white border border-gray-200 rounded-2xl overflow-hidden">
        <div ref={mapEl} className="w-full" style={{ height: 520, display: view === "3d" ? "none" : undefined }} role="region" aria-label="Market map. The same figures are in the table below." />
        {view === "3d" && (
          <MarketMap3D areas={areas3d} onPick={(id) => setSelected(placedSuburbs.find((s) => areaId(s) === id) ?? null)} />
        )}
        <div style={{ zIndex: 500 }} className="absolute left-3 bottom-3 bg-white/95 rounded-xl shadow border border-gray-200 px-3 py-2 text-xs text-gray-700 max-w-60">
          <p className="font-semibold mb-1">{m.label}</p>
          <ul className="space-y-0.5">
            {legend.map((l, i) => <li key={i} className="flex items-center gap-2"><span className="w-3 h-3 rounded-full" style={{ background: l.color }} aria-hidden />{l.label}</li>)}
            <li className="flex items-center gap-2"><span className="w-3 h-3 rounded-full border border-dashed border-gray-500" aria-hidden />Not enough data</li>
          </ul>
          {view === "circles" && <p className="mt-1 text-gray-500">Circle size = live listings</p>}
          {view === "3d" && <p className="mt-1 text-gray-500">Height = {measure === "growthPercent" ? "size of the change" : "the measure"}, relative to the highest suburb shown. Right-drag to tilt and turn.</p>}
          {view !== "circles" && (
            <p className={`mt-1 ${usingRealBoundaries ? "text-gray-500" : "text-amber-800"}`}>
              {usingRealBoundaries
                ? "Suburb boundaries from the boundary file."
                : "Approximate areas: drawn around each suburb's centre (within 2 km), not official boundaries."}
            </p>
          )}
        </div>
        {selected && <SuburbPanel s={selected} type={type} onClose={() => setSelected(null)} filters={{ category, bedrooms }} />}
      </div>

      {data && <SuburbTable rows={data.suburbs} measure={m} onPick={setSelected} />}
    </div>
  );
}

function SuburbPanel({ s, type, onClose, filters }: { s: GeoSuburb; type: MarketType; onClose: () => void; filters: { category: string; bedrooms: string } }) {
  const { token } = useAuth();
  const [trend, setTrend] = useState<MarketDashboardData | null>(null);
  useEffect(() => {
    if (!token) return;
    marketDashboard.get(token, type, { city: s.city, suburb: s.suburb, grain: "MONTH", category: filters.category || undefined,
      bedrooms: filters.bedrooms === "" ? undefined : Number(filters.bedrooms) }).then(setTrend).catch(() => setTrend(null));
  }, [token, type, s, filters]);
  const conf = { HIGH: "20+ listings", MEDIUM: "5–19 listings", LOW: "fewer than 5 listings" }[s.confidence];
  const rows: [string, string][] = [
    ["Live listings", String(s.active)],
    ["Median asking", formatValue(s.askingMedian, "USD")],
    ["Average asking", formatValue(s.askingMean, "USD")],
    ["Price change (90 days)", s.growthPercent == null ? "Not enough data" : `${s.growthPercent > 0 ? "+" : ""}${s.growthPercent}%`],
    ["Estimated gross yield", s.grossYield == null ? "Not enough data" : `${s.grossYield}%`],
    ["New listings (90 days)", String(s.newListings ?? 0)],
    [type === "rent" ? "Leases signed (90 days)" : "Verified sales (90 days)", String(s.transactions)],
  ];
  if (s.demand != null) rows.push(["Enquiries + viewings (90 days)", String(s.demand)], ["Searches (90 days)", String(s.searches ?? 0)]);
  return (
    <aside style={{ zIndex: 600 }} className="absolute top-3 right-3 bottom-3 w-80 bg-white rounded-2xl shadow-xl border border-gray-200 flex flex-col" aria-label={`${s.suburb} figures`}>
      <header className="px-4 py-3 border-b border-gray-100 flex items-start justify-between">
        <div>
          <h3 className="font-semibold text-gray-900">{s.suburb}</h3>
          <p className="text-xs text-gray-500">{s.city} · {type === "rent" ? "rentals" : "sales"}</p>
        </div>
        <button onClick={onClose} aria-label="Close suburb panel" className="text-gray-400 hover:text-gray-700"><X className="w-4 h-4" /></button>
      </header>
      <div className="flex-1 overflow-y-auto px-4 py-3 space-y-3 text-sm">
        <p className={`text-xs inline-flex items-center gap-1 rounded-full px-2 py-0.5 ${s.confidence === "LOW" ? "bg-amber-50 text-amber-800" : "bg-gray-100 text-gray-700"}`}>
          {s.confidence === "LOW" && <AlertTriangle className="w-3 h-3" aria-hidden />} Confidence: {s.confidence.toLowerCase()} ({conf})
        </p>
        <dl className="divide-y divide-gray-100">
          {rows.map(([k, v]) => <div key={k} className="flex justify-between py-1.5"><dt className="text-gray-500">{k}</dt><dd className="tabular-nums text-gray-900">{v}</dd></div>)}
        </dl>
        <p className="text-[11px] text-gray-400">Placed at the {s.placedBy ?? "—"}. n = {s.askingN} priced listings.</p>
        <div>
          <p className="text-xs font-semibold text-gray-700 mb-1">Median asking price, last 12 months</p>
          {trend ? <LineChart title={`${s.suburb} median asking price`} unit="USD" height={160}
            points={trend.series.map((p) => ({ label: p.label.replace(" (part)", ""), values: p.values }))} series={[{ key: "askingMedian", label: "Asking" }]} />
            : <p className="text-xs text-gray-400">Loading…</p>}
          {trend?.figuresCalculatedAt && <p className="text-[11px] text-gray-400 mt-1">Last updated {new Date(trend.figuresCalculatedAt).toLocaleString()}</p>}
        </div>
      </div>
    </aside>
  );
}

function SuburbTable({ rows, measure, onPick }: { rows: GeoSuburb[]; measure: (typeof MEASURES)[number]; onPick: (s: GeoSuburb) => void }) {
  const sorted = [...rows].sort((a, b) => (value(b, measure.key) ?? -Infinity) - (value(a, measure.key) ?? -Infinity));
  return (
    <section className="bg-white border border-gray-200 rounded-2xl p-4">
      <h3 className="text-sm font-semibold text-gray-900">Suburbs by {measure.label.toLowerCase()}</h3>
      <p className="text-xs text-gray-500 mb-2">{measure.help} Includes suburbs that couldn&apos;t be placed on the map.</p>
      {sorted.length === 0 ? <p className="text-sm text-gray-400">No listings match these filters.</p> : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="text-xs text-gray-500 text-left">
              <tr><th scope="col" className="py-1.5 font-medium">Suburb</th><th scope="col" className="py-1.5 px-2 font-medium text-right">{measure.label}</th>
                {measure.key !== "active" && <th scope="col" className="py-1.5 px-2 font-medium text-right">Live</th>}
                {measure.key !== "askingMedian" && <th scope="col" className="py-1.5 px-2 font-medium text-right">Median asking</th>}
                <th scope="col" className="py-1.5 px-2 font-medium">Confidence</th><th scope="col" className="py-1.5 pl-2 font-medium">On map</th></tr>
            </thead>
            <tbody>
              {sorted.slice(0, 40).map((s) => (
                <tr key={`${s.suburb}-${s.city}`} className="border-t border-gray-100">
                  <td className="py-1.5"><button type="button" onClick={() => onPick(s)} className="hover:text-brand-600 text-left">{s.suburb}</button><span className="text-xs text-gray-400"> · {s.city}</span></td>
                  <td className="py-1.5 px-2 text-right tabular-nums">{value(s, measure.key) == null ? "—" : formatValue(value(s, measure.key), measure.unit)}</td>
                  {measure.key !== "active" && <td className="py-1.5 px-2 text-right tabular-nums">{s.active}</td>}
                  {measure.key !== "askingMedian" && <td className="py-1.5 px-2 text-right tabular-nums">{formatValue(s.askingMedian, "USD")}</td>}
                  <td className="py-1.5 px-2 text-xs text-gray-600">{s.confidence.toLowerCase()}</td>
                  <td className="py-1.5 pl-2 text-xs text-gray-600">{s.latitude != null ? "Yes" : "No location"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
