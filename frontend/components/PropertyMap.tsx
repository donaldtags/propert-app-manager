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

const STYLESHEETS = [
  "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css",
  "https://cdnjs.cloudflare.com/ajax/libs/leaflet.markercluster/1.5.3/MarkerCluster.css",
  "https://cdnjs.cloudflare.com/ajax/libs/leaflet.markercluster/1.5.3/MarkerCluster.Default.css",
];

function ensureStylesheets() {
  STYLESHEETS.forEach((href) => {
    if (document.querySelector(`link[href="${href}"]`)) return;
    const link = document.createElement("link");
    link.rel = "stylesheet";
    link.href = href;
    document.head.appendChild(link);
  });
}

export default function PropertyMap({
  properties,
  highlightedId,
  onMarkerClick,
  center,
  zoom,
}: Props) {
  const mapRef = useRef<HTMLDivElement>(null);
  const leafletMap = useRef<unknown>(null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const clusterGroupRef = useRef<any>(null);
  const markersRef = useRef<Map<number, unknown>>(new Map());

  useEffect(() => {
    if (typeof window === "undefined" || !mapRef.current) return;

    let isMounted = true;

    // leaflet.markercluster patches the global `L` that leaflet's UMD build
    // attaches to window, so it must load strictly after leaflet resolves.
    import("leaflet").then(async (L) => {
      await import("leaflet.markercluster");
      if (!isMounted || !mapRef.current) return;

      // leaflet.markercluster attaches markerClusterGroup to the live
      // window.L object, not to the namespace object import() returned.
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const markerClusterGroup = (window as any).L.markerClusterGroup as (
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        options: any
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
      ) => any;

      // Fix default icon paths
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      delete (L.Icon.Default.prototype as any)._getIconUrl;
      L.Icon.Default.mergeOptions({
        iconRetinaUrl: "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon-2x.png",
        iconUrl: "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon.png",
        shadowUrl: "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png",
      });

      ensureStylesheets();

      const mapCenter = center ?? DEFAULT_CENTER;
      const mapZoom = zoom ?? DEFAULT_ZOOM;

      if (!leafletMap.current) {
        const map = L.map(mapRef.current!, {
          center: mapCenter,
          zoom: mapZoom,
          zoomControl: true,
        });

        L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
          attribution: "© OpenStreetMap contributors",
          maxZoom: 19,
        }).addTo(map);

        const clusterGroup = markerClusterGroup({
          maxClusterRadius: 60,
          spiderfyOnMaxZoom: true,
          showCoverageOnHover: false,
          zoomToBoundsOnClick: true,
          iconCreateFunction: (cluster: { getChildCount: () => number }) => {
            const count = cluster.getChildCount();
            const size = count < 10 ? 36 : count < 50 ? 44 : 52;
            return L.divIcon({
              html: `<div class="cluster-marker" style="width:${size}px;height:${size}px">${count}</div>`,
              className: "",
              iconSize: [size, size],
            });
          },
        });
        clusterGroup.addTo(map);

        leafletMap.current = map;
        clusterGroupRef.current = clusterGroup;
      } else if (center) {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        (leafletMap.current as any).setView(mapCenter, mapZoom);
      }

      const clusterGroup = clusterGroupRef.current;

      // Clear existing markers
      clusterGroup.clearLayers();
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

        const marker = L.marker([property.latitude!, property.longitude!], { icon }).on(
          "click",
          () => onMarkerClick?.(property.id)
        );

        marker.bindPopup(
          `<div style="min-width:160px">
            <strong style="font-size:13px">${property.title}</strong><br/>
            <span style="font-size:12px;color:#666">${property.suburb}, ${property.city}</span><br/>
            <strong style="color:#1f5d42">${formatPrice(property.price, property.currency)}${property.listingType === "RENT" ? "/mo" : ""}</strong><br/>
            <a href="/properties/${property.id}" style="font-size:12px;color:#1f5d42">View Details →</a>
          </div>`
        );

        clusterGroup.addLayer(marker);
        markersRef.current.set(property.id, marker);
      });

      // If we have valid properties, fit the map to show them
      if (validProperties.length > 0 && !center) {
        const latLngs = validProperties.map((p) => [p.latitude!, p.longitude!] as [number, number]);
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        (leafletMap.current as any).fitBounds(L.latLngBounds(latLngs), { padding: [40, 40], maxZoom: 14 });
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
        clusterGroupRef.current = null;
      }
    };
  }, []);

  return (
    <div ref={mapRef} className="w-full h-full" />
  );
}