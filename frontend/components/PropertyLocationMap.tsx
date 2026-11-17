"use client";

import { useEffect, useRef, useState } from "react";
import type * as Leaflet from "leaflet";
import { Clock, ExternalLink, Loader2, Navigation, Route as RouteIcon, X } from "lucide-react";
import {
  addEsriBasemaps,
  currentPosition,
  ensureStylesheets,
  externalDirectionsUrl,
  fetchRoute,
  housePinIcon,
  type Route,
} from "@/lib/maps";

interface Props {
  latitude: number;
  longitude: number;
  title: string;
  addressLine: string;
}

function formatDuration(minutes: number) {
  if (minutes < 60) return `${Math.max(1, Math.round(minutes))} min`;
  const h = Math.floor(minutes / 60);
  const m = Math.round(minutes % 60);
  return m ? `${h} h ${m} min` : `${h} h`;
}

const escapeHtml = (s: string) =>
  s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!);

/** One property, pinned at the exact house, with in-app directions from the viewer's location. */
export default function PropertyLocationMap({ latitude, longitude, title, addressLine }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<Leaflet.Map | null>(null);
  const leafletRef = useRef<typeof Leaflet | null>(null);
  const routeLayerRef = useRef<Leaflet.LayerGroup | null>(null);

  const [route, setRoute] = useState<Route | null>(null);
  const [loadingRoute, setLoadingRoute] = useState(false);
  const [error, setError] = useState("");
  const [showSteps, setShowSteps] = useState(false);

  useEffect(() => {
    let cancelled = false;
    import("leaflet").then((L) => {
      if (cancelled || !containerRef.current || mapRef.current) return;
      ensureStylesheets();
      leafletRef.current = L;
      const map = L.map(containerRef.current, { center: [latitude, longitude], zoom: 17 });
      addEsriBasemaps(L, map);
      L.marker([latitude, longitude], { icon: housePinIcon(L) })
        .bindPopup(`<strong>${escapeHtml(title)}</strong><br/><span style="font-size:12px;color:#666">${escapeHtml(addressLine)}</span>`)
        .addTo(map);
      routeLayerRef.current = L.layerGroup().addTo(map);
      mapRef.current = map;
    });
    return () => {
      cancelled = true;
      mapRef.current?.remove();
      mapRef.current = null;
    };
  }, [latitude, longitude, title, addressLine]);

  const showDirections = async () => {
    const L = leafletRef.current;
    const map = mapRef.current;
    if (!L || !map) return;
    setError("");
    setLoadingRoute(true);
    try {
      const origin = await currentPosition();
      const result = await fetchRoute(origin, [latitude, longitude]);
      const layer = routeLayerRef.current!;
      layer.clearLayers();
      L.polyline(result.path, { color: "#ffffff", weight: 9, opacity: 0.9 }).addTo(layer);
      const line = L.polyline(result.path, { color: "#0b1d32", weight: 5 }).addTo(layer);
      L.marker(origin, {
        icon: L.divIcon({ className: "", html: '<div class="you-are-here"></div>', iconSize: [16, 16], iconAnchor: [8, 8] }),
      })
        .bindTooltip("You are here")
        .addTo(layer);
      map.fitBounds(line.getBounds(), { padding: [40, 40] });
      setRoute(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Couldn't work out directions.");
    } finally {
      setLoadingRoute(false);
    }
  };

  const clearRoute = () => {
    routeLayerRef.current?.clearLayers();
    mapRef.current?.setView([latitude, longitude], 17);
    setRoute(null);
    setShowSteps(false);
  };

  return (
    <div className="space-y-3">
      <div className="relative h-80 sm:h-96 rounded-xl overflow-hidden border border-gray-200">
        <div ref={containerRef} className="w-full h-full" />
      </div>

      <div className="flex flex-wrap gap-2">
        {!route ? (
          <button
            type="button"
            onClick={showDirections}
            disabled={loadingRoute}
            className="flex items-center gap-1.5 bg-brand-600 hover:bg-brand-700 disabled:opacity-60 text-white text-sm font-semibold px-4 py-2.5 rounded-xl"
          >
            {loadingRoute ? <Loader2 className="w-4 h-4 animate-spin" /> : <RouteIcon className="w-4 h-4" />}
            Show route from my location
          </button>
        ) : (
          <button
            type="button"
            onClick={clearRoute}
            className="flex items-center gap-1.5 border border-gray-200 text-gray-700 text-sm font-semibold px-4 py-2.5 rounded-xl bg-white"
          >
            <X className="w-4 h-4" /> Clear route
          </button>
        )}
        <a
          href={externalDirectionsUrl(latitude, longitude)}
          target="_blank"
          rel="noopener noreferrer"
          className="flex items-center gap-1.5 border border-gray-200 hover:border-brand-500 text-gray-700 text-sm font-semibold px-4 py-2.5 rounded-xl bg-white"
        >
          <Navigation className="w-4 h-4" /> Navigate <ExternalLink className="w-3 h-3 opacity-60" />
        </a>
      </div>

      {error && <p className="text-xs bg-amber-50 text-amber-800 rounded-lg px-3 py-2">{error}</p>}

      {route && (
        <div className="bg-gray-50 rounded-xl p-4">
          <div className="flex items-center gap-4 text-sm">
            <span className="font-bold text-gray-900">{route.distanceKm.toFixed(1)} km</span>
            <span className="flex items-center gap-1 text-gray-600">
              <Clock className="w-4 h-4" /> {formatDuration(route.durationMin)} by car
            </span>
            {route.steps.length > 0 && (
              <button
                type="button"
                onClick={() => setShowSteps((s) => !s)}
                className="ml-auto text-xs font-semibold text-brand-700 hover:underline"
              >
                {showSteps ? "Hide steps" : `Show ${route.steps.length} steps`}
              </button>
            )}
          </div>
          {showSteps && (
            <ol className="mt-3 space-y-1.5 text-sm text-gray-700 list-decimal list-inside max-h-64 overflow-y-auto">
              {route.steps.map((step, i) => (
                <li key={i}>{step}</li>
              ))}
            </ol>
          )}
        </div>
      )}
    </div>
  );
}
