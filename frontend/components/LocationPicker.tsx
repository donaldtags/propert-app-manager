"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import type * as Leaflet from "leaflet";
import { Crosshair, Loader2, MapPin, Search } from "lucide-react";
import { properties as propertiesApi } from "@/lib/api";
import { addEsriBasemaps, currentPosition, DEFAULT_CENTER, ensureStylesheets, housePinIcon } from "@/lib/maps";

export interface PickedLocation {
  latitude: number;
  longitude: number;
}

interface Props {
  value: PickedLocation | null;
  onChange: (location: PickedLocation | null) => void;
  address: string;
  suburb: string;
  city: string;
  country?: string;
  token: string | null;
}

// How far to zoom for each ArcGIS match precision, and how to describe it to the lister.
const PRECISION: Record<string, { zoom: number; label: string }> = {
  PointAddress: { zoom: 19, label: "the exact address" },
  Subaddress: { zoom: 19, label: "the exact address" },
  StreetAddress: { zoom: 18, label: "the right part of the street" },
  StreetInt: { zoom: 18, label: "the street intersection" },
  StreetName: { zoom: 17, label: "the street" },
  POI: { zoom: 18, label: "a nearby landmark" },
};
const AREA_LEVEL = { zoom: 15, label: "the suburb" };

const round = (n: number) => Math.round(n * 1e6) / 1e6;

export default function LocationPicker({ value, onChange, address, suburb, city, country, token }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<Leaflet.Map | null>(null);
  const markerRef = useRef<Leaflet.Marker | null>(null);
  const leafletRef = useRef<typeof Leaflet | null>(null);
  // Once the lister places the pin themselves we stop auto-moving it from the address.
  const pinnedByUser = useRef(value != null);
  const onChangeRef = useRef(onChange);
  useEffect(() => {
    onChangeRef.current = onChange;
  }, [onChange]);

  const [ready, setReady] = useState(false);
  const [searching, setSearching] = useState(false);
  const [locating, setLocating] = useState(false);
  const [message, setMessage] = useState<{ tone: "info" | "warn"; text: string } | null>(null);

  const placePin = useCallback((lat: number, lng: number, zoom?: number) => {
    const L = leafletRef.current;
    const map = mapRef.current;
    if (!L || !map) return;
    if (!markerRef.current) {
      const marker = L.marker([lat, lng], { draggable: true, icon: housePinIcon(L), autoPan: true });
      marker.on("dragend", () => {
        const pos = marker.getLatLng();
        pinnedByUser.current = true;
        onChangeRef.current({ latitude: round(pos.lat), longitude: round(pos.lng) });
      });
      marker.addTo(map);
      markerRef.current = marker;
    } else {
      markerRef.current.setLatLng([lat, lng]);
    }
    if (zoom != null) map.setView([lat, lng], zoom);
  }, []);

  useEffect(() => {
    let cancelled = false;
    import("leaflet").then((L) => {
      if (cancelled || !containerRef.current || mapRef.current) return;
      ensureStylesheets();
      leafletRef.current = L;
      const map = L.map(containerRef.current, {
        center: value ? [value.latitude, value.longitude] : DEFAULT_CENTER,
        zoom: value ? 18 : 12,
      });
      // Satellite makes it easy to spot the actual roof of the house.
      addEsriBasemaps(L, map, "Satellite");
      map.on("click", (e: Leaflet.LeafletMouseEvent) => {
        pinnedByUser.current = true;
        placePin(e.latlng.lat, e.latlng.lng);
        onChangeRef.current({ latitude: round(e.latlng.lat), longitude: round(e.latlng.lng) });
        setMessage(null);
      });
      mapRef.current = map;
      if (value) placePin(value.latitude, value.longitude);
      setReady(true);
    });
    return () => {
      cancelled = true;
      mapRef.current?.remove();
      mapRef.current = null;
      markerRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const findAddress = useCallback(
    async (manual: boolean) => {
      if (!token || (!city && !suburb)) return;
      setSearching(true);
      if (manual) setMessage(null);
      try {
        const [best] = await propertiesApi.geocode({ address, suburb, city, country }, token);
        if (!best) {
          if (manual) setMessage({ tone: "warn", text: "We couldn't find that address. Tap the map on the house to drop the pin." });
          return;
        }
        const level = PRECISION[best.precision] ?? AREA_LEVEL;
        placePin(best.latitude, best.longitude, level.zoom);
        onChangeRef.current({ latitude: round(best.latitude), longitude: round(best.longitude) });
        if (manual) pinnedByUser.current = false;
        setMessage({
          tone: level.zoom >= 19 ? "info" : "warn",
          text:
            level.zoom >= 19
              ? `Found ${best.matchedAddress}. Check the pin sits on the house.`
              : `Found ${level.label} (${best.matchedAddress}). Drag the pin, or tap the map, onto the exact house.`,
        });
      } catch {
        if (manual) setMessage({ tone: "warn", text: "Address lookup is unavailable right now. Tap the map on the house to drop the pin." });
      } finally {
        setSearching(false);
      }
    },
    [address, suburb, city, country, token, placePin]
  );

  // Follow the typed address until the lister places the pin themselves.
  useEffect(() => {
    if (!ready || pinnedByUser.current) return;
    const timer = setTimeout(() => findAddress(false), 900);
    return () => clearTimeout(timer);
  }, [ready, findAddress]);

  const useMyLocation = async () => {
    setLocating(true);
    setMessage(null);
    try {
      const [lat, lng] = await currentPosition();
      pinnedByUser.current = true;
      placePin(lat, lng, 19);
      onChangeRef.current({ latitude: round(lat), longitude: round(lng) });
      setMessage({ tone: "info", text: "Pinned to where you are now. Nudge it onto the house if needed." });
    } catch (err) {
      setMessage({ tone: "warn", text: err instanceof Error ? err.message : "Couldn't get your location." });
    } finally {
      setLocating(false);
    }
  };

  const clearPin = () => {
    markerRef.current?.remove();
    markerRef.current = null;
    pinnedByUser.current = false;
    onChangeRef.current(null);
    setMessage(null);
  };

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          onClick={() => findAddress(true)}
          disabled={searching || !token || (!city && !suburb)}
          className="flex items-center gap-1.5 bg-brand-600 hover:bg-brand-700 disabled:opacity-50 text-white text-sm font-semibold px-4 py-2 rounded-xl"
        >
          {searching ? <Loader2 className="w-4 h-4 animate-spin" /> : <Search className="w-4 h-4" />}
          Find address on map
        </button>
        <button
          type="button"
          onClick={useMyLocation}
          disabled={locating}
          className="flex items-center gap-1.5 border border-gray-200 hover:border-brand-500 disabled:opacity-50 text-gray-700 text-sm font-semibold px-4 py-2 rounded-xl bg-white"
        >
          {locating ? <Loader2 className="w-4 h-4 animate-spin" /> : <Crosshair className="w-4 h-4" />}
          I&apos;m at the property
        </button>
        {value && (
          <button type="button" onClick={clearPin} className="text-sm text-gray-500 hover:text-red-600 px-2">
            Clear pin
          </button>
        )}
      </div>

      <div className="relative h-80 rounded-xl overflow-hidden border border-gray-200">
        <div ref={containerRef} className="w-full h-full" />
        {!ready && <div className="absolute inset-0 bg-gray-100 animate-pulse" />}
      </div>

      {message && (
        <p className={`text-xs rounded-lg px-3 py-2 ${message.tone === "info" ? "bg-brand-50 text-brand-800" : "bg-amber-50 text-amber-800"}`}>
          {message.text}
        </p>
      )}
      <p className="text-xs text-gray-500 flex items-start gap-1.5">
        <MapPin className="w-3.5 h-3.5 mt-0.5 shrink-0" />
        {value
          ? `Pin set at ${value.latitude.toFixed(6)}, ${value.longitude.toFixed(6)}. Buyers and tenants get directions to this exact spot.`
          : "Tap the map on the house, drag the pin, or use “I'm at the property” when you're on site. Zimbabwe street data rarely includes house numbers, so a pin is the only way to get people to the right gate."}
      </p>
    </div>
  );
}
