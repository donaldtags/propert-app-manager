"use client";

import { useEffect, useRef } from "react";
import type { Property } from "@/lib/types";

interface Props {
  properties: Property[];
  highlightedId?: number | null;
  onMarkerClick?: (id: number) => void;
  center?: [number, number];
  zoom?: number;
}

function formatPrice(price: number, currency: string) {
  if (price >= 1_000_000) return `$${(price / 1_000_000).toFixed(1)}M`;
  if (price >= 1_000) return `$${(price / 1_000).toFixed(0)}K`;
  return `$${price}`;
}

// Default center: Harare, Zimbabwe
const DEFAULT_CENTER: [number, number] = [-17.8252, 31.0335];
const DEFAULT_ZOOM = 12;

export default function PropertyMap({
  properties,
  highlightedId,
  onMarkerClick,
  center,
  zoom,
}: Props) {
  const mapRef = useRef<HTMLDivElement>(null);
  const leafletMap = useRef<unknown>(null);
  const markersRef = useRef<Map<number, unknown>>(new Map());

  useEffect(() => {
    if (typeof window === "undefined" || !mapRef.current) return;

    let isMounted = true;

    import("leaflet").then((L) => {
      if (!isMounted || !mapRef.current) return;

      // Fix default icon paths
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      delete (L.Icon.Default.prototype as any)._getIconUrl;
      L.Icon.Default.mergeOptions({
        iconRetinaUrl: "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon-2x.png",
        iconUrl: "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon.png",
        shadowUrl: "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png",
      });

      // Import CSS
      const link = document.createElement("link");
      link.rel = "stylesheet";
      link.href = "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css";
      document.head.appendChild(link);

      const mapCenter = center ?? DEFAULT_CENTER;
      const mapZoom = zoom ?? DEFAULT_ZOOM;

      if (!leafletMap.current) {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const map = L.map(mapRef.current!, {
          center: mapCenter,
          zoom: mapZoom,
          zoomControl: true,
        });

        L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
          attribution: "© OpenStreetMap contributors",
          maxZoom: 19,
        }).addTo(map);

        leafletMap.current = map;
      }

      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const map = leafletMap.current as any;

      // Clear existing markers
      markersRef.current.forEach((m) => (m as any).remove());
      markersRef.current.clear();

      // Add property markers
      const validProperties = properties.filter(
        (p) => p.latitude != null && p.longitude != null
      );

      validProperties.forEach((property) => {
        const isActive = property.id === highlightedId;
        const icon = L.divIcon({
          className: "",
          html: `<div class="price-marker${isActive ? " active" : ""}">${formatPrice(property.price, property.currency)}</div>`,
          iconAnchor: [0, 0],
        });

        const marker = L.marker([property.latitude!, property.longitude!], { icon })
          .addTo(map)
          .on("click", () => {
            onMarkerClick?.(property.id);
          });

        marker.bindPopup(
          `<div style="min-width:160px">
            <strong style="font-size:13px">${property.title}</strong><br/>
            <span style="font-size:12px;color:#666">${property.suburb}, ${property.city}</span><br/>
            <strong style="color:#006aff">${formatPrice(property.price, property.currency)}${property.listingType === "RENT" ? "/mo" : ""}</strong><br/>
            <a href="/properties/${property.id}" style="font-size:12px;color:#006aff">View Details →</a>
          </div>`
        );

        markersRef.current.set(property.id, marker);
      });

      // If we have valid properties, fit the map to show them
      if (validProperties.length > 0 && !center) {
        const latLngs = validProperties.map((p) => [p.latitude!, p.longitude!] as [number, number]);
        map.fitBounds(L.latLngBounds(latLngs), { padding: [40, 40], maxZoom: 14 });
      }
    });

    return () => {
      isMounted = false;
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [properties, center, zoom]);

  // Update highlighted marker when highlightedId changes
  useEffect(() => {
    if (typeof window === "undefined") return;
    import("leaflet").then((L) => {
      markersRef.current.forEach((marker, id) => {
        const property = properties.find((p) => p.id === id);
        if (!property) return;
        const isActive = id === highlightedId;
        const icon = L.divIcon({
          className: "",
          html: `<div class="price-marker${isActive ? " active" : ""}">${formatPrice(property.price, property.currency)}</div>`,
          iconAnchor: [0, 0],
        });
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        (marker as any).setIcon(icon);
      });
    });
  }, [highlightedId, properties]);

  useEffect(() => {
    return () => {
      if (leafletMap.current) {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        (leafletMap.current as any).remove();
        leafletMap.current = null;
      }
    };
  }, []);

  return (
    <div ref={mapRef} className="w-full h-full" />
  );
}
