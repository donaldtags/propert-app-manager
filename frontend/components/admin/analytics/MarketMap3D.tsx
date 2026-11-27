"use client";

import { useEffect, useRef } from "react";
import type * as MapLibre from "maplibre-gl";
import { ensureStylesheets, esriTileUrl, ESRI_ATTRIBUTION, DEFAULT_CENTER } from "@/lib/maps";
import type { LngLat } from "@/lib/suburbAreas";

export interface Area3D {
  id: string;
  suburb: string;
  ring: LngLat[];
  /** The measure's value, or null when there isn't enough data (drawn low and grey). */
  value: number | null;
  color: string | null;
  label: string;
}

const MAPLIBRE_CSS = "https://unpkg.com/maplibre-gl@4.7.1/dist/maplibre-gl.css";
const MAX_HEIGHT_M = 1800;

/**
 * The market map in 3D: each suburb's area is raised in proportion to the chosen measure and
 * coloured the same way as the 2D map. Drag to pan, right-drag (or Ctrl-drag) to rotate and tilt.
 * Heights are relative to the highest suburb shown, so they compare suburbs, not absolute amounts.
 */
export default function MarketMap3D({ areas, onPick }: { areas: Area3D[]; onPick: (id: string) => void }) {
  const el = useRef<HTMLDivElement>(null);
  const mapRef = useRef<MapLibre.Map | null>(null);
  const pickRef = useRef(onPick);
  const areasRef = useRef(areas);
  useEffect(() => {
    pickRef.current = onPick;
    areasRef.current = areas;
  });

  // Create the map once.
  useEffect(() => {
    let alive = true;
    let popup: MapLibre.Popup | null = null;
    ensureStylesheets([MAPLIBRE_CSS]);
    import("maplibre-gl").then((ml) => {
      if (!alive || !el.current || mapRef.current) return;
      const map = new ml.Map({
        container: el.current,
        style: {
          version: 8,
          sources: {
            esri: { type: "raster", tiles: [esriTileUrl("World_Street_Map")], tileSize: 256, attribution: ESRI_ATTRIBUTION, maxzoom: 19 },
          },
          layers: [{ id: "esri", type: "raster", source: "esri" }],
        },
        center: [DEFAULT_CENTER[1], DEFAULT_CENTER[0]],
        zoom: 11.5,
        pitch: 55,
        bearing: -20,
      });
      map.addControl(new ml.NavigationControl({ visualizePitch: true }), "top-left");
      mapRef.current = map;
      map.on("load", () => {
        map.addSource("areas", { type: "geojson", data: { type: "FeatureCollection", features: [] } });
        map.addLayer({
          id: "areas", type: "fill-extrusion", source: "areas",
          paint: {
            "fill-extrusion-color": ["get", "color"],
            "fill-extrusion-height": ["get", "height"],
            "fill-extrusion-base": 0,
            "fill-extrusion-opacity": 0.85,
          },
        });
        map.on("click", "areas", (e) => {
          const id = e.features?.[0]?.properties?.id;
          if (id) pickRef.current(String(id));
        });
        map.on("mousemove", "areas", (e) => {
          map.getCanvas().style.cursor = "pointer";
          const label = e.features?.[0]?.properties?.label;
          if (!label) return;
          popup?.remove();
          popup = new ml.Popup({ closeButton: false, closeOnClick: false, offset: 8 }).setLngLat(e.lngLat).setHTML(String(label)).addTo(map);
        });
        map.on("mouseleave", "areas", () => {
          map.getCanvas().style.cursor = "";
          popup?.remove();
        });
        draw(map, areasRef.current);
      });
    });
    return () => {
      alive = false;
      popup?.remove();
      mapRef.current?.remove();
      mapRef.current = null;
    };
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    if (map && map.isStyleLoaded() && map.getSource("areas")) draw(map, areas);
  }, [areas]);

  return <div ref={el} className="w-full" style={{ height: 520 }} role="region"
    aria-label="3D market map: suburb height and colour show the chosen measure. The same figures are in the table below." />;
}

function draw(map: MapLibre.Map, areas: Area3D[]) {
  // Height = size of the value (for price change, how big the change is; colour shows direction).
  const max = Math.max(0, ...areas.map((a) => (a.value == null ? 0 : Math.abs(a.value))));
  const features = areas.map((a) => ({
    type: "Feature" as const,
    properties: {
      id: a.id,
      label: a.label,
      color: a.color ?? "#d1d5db",
      height: a.value == null || max === 0 ? 30 : 60 + (MAX_HEIGHT_M - 60) * Math.abs(a.value) / max,
    },
    geometry: { type: "Polygon" as const, coordinates: [a.ring] },
  }));
  (map.getSource("areas") as MapLibre.GeoJSONSource).setData({ type: "FeatureCollection", features });
  const all = areas.flatMap((a) => a.ring);
  if (all.length > 0) {
    const lngs = all.map((p) => p[0]), lats = all.map((p) => p[1]);
    map.fitBounds([[Math.min(...lngs), Math.min(...lats)], [Math.max(...lngs), Math.max(...lats)]], { padding: 60, pitch: 55, bearing: -20, duration: 0, maxZoom: 14 });
  }
}
