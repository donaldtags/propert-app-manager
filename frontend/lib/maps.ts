// Shared ArcGIS/Leaflet helpers for every map in the app.
import type * as Leaflet from "leaflet";

// Optional for development, but Esri's terms require an ArcGIS account (and key)
// for production/commercial use. With a key, in-app routing uses ArcGIS too.
export const ARCGIS_API_KEY = process.env.NEXT_PUBLIC_ARCGIS_API_KEY;

// Default center: Harare, Zimbabwe
export const DEFAULT_CENTER: [number, number] = [-17.8252, 31.0335];

const ESRI_ATTRIBUTION = 'Powered by <a href="https://www.esri.com">Esri</a> | Esri, HERE, Garmin, USGS, FAO, NOAA';

export const LEAFLET_STYLESHEETS = [
  "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css",
];

export function ensureStylesheets(hrefs: string[] = LEAFLET_STYLESHEETS) {
  hrefs.forEach((href) => {
    if (document.querySelector(`link[href="${href}"]`)) return;
    const link = document.createElement("link");
    link.rel = "stylesheet";
    link.href = href;
    document.head.appendChild(link);
  });
}

function esriTileUrl(service: string) {
  const url = `https://server.arcgisonline.com/ArcGIS/rest/services/${service}/MapServer/tile/{z}/{y}/{x}`;
  return ARCGIS_API_KEY ? `${url}?token=${ARCGIS_API_KEY}` : url;
}

/** Adds Esri Streets (default) and Satellite basemaps with a switcher. */
export function addEsriBasemaps(L: typeof Leaflet, map: Leaflet.Map, initial: "Streets" | "Satellite" = "Streets") {
  const streets = L.tileLayer(esriTileUrl("World_Street_Map"), {
    attribution: ESRI_ATTRIBUTION,
    maxZoom: 19,
  });
  const satellite = L.layerGroup([
    L.tileLayer(esriTileUrl("World_Imagery"), {
      attribution: `${ESRI_ATTRIBUTION}, Maxar, Earthstar Geographics`,
      maxZoom: 19,
    }),
    L.tileLayer(esriTileUrl("Reference/World_Boundaries_and_Places"), { maxZoom: 19 }),
  ]);
  (initial === "Streets" ? streets : satellite).addTo(map);
  L.control.layers({ Streets: streets, Satellite: satellite }, undefined, { position: "topright" }).addTo(map);
}

/** A teardrop pin for "this exact house" markers. */
export function housePinIcon(L: typeof Leaflet) {
  return L.divIcon({
    className: "",
    html: '<div class="house-pin"><span></span></div>',
    iconSize: [32, 42],
    iconAnchor: [16, 42],
    popupAnchor: [0, -38],
  });
}

export function currentPosition(): Promise<[number, number]> {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error("Your browser can't share your location."));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => resolve([pos.coords.latitude, pos.coords.longitude]),
      (err) =>
        reject(
          new Error(
            err.code === err.PERMISSION_DENIED
              ? "Location permission was denied. Allow location access in your browser to use this."
              : "Couldn't get your location. Try again outdoors or with GPS on."
          )
        ),
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 30000 }
    );
  });
}

/** Turn-by-turn navigation in Google Maps (app on phones, website on desktop). */
export function externalDirectionsUrl(lat: number, lng: number) {
  return `https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}&travelmode=driving`;
}

export interface Route {
  path: [number, number][];
  distanceKm: number;
  durationMin: number;
  steps: string[];
}

/** Driving route from `from` to `to` ([lat, lng]). ArcGIS when keyed, otherwise OSRM. */
export async function fetchRoute(from: [number, number], to: [number, number]): Promise<Route> {
  return ARCGIS_API_KEY ? arcgisRoute(from, to, ARCGIS_API_KEY) : osrmRoute(from, to);
}

async function arcgisRoute(from: [number, number], to: [number, number], token: string): Promise<Route> {
  const params = new URLSearchParams({
    f: "json",
    token,
    stops: `${from[1]},${from[0]};${to[1]},${to[0]}`,
    outSR: "4326",
    returnDirections: "true",
    directionsLengthUnits: "esriNAUKilometers",
  });
  const res = await fetch(
    `https://route-api.arcgis.com/arcgis/rest/services/World/Route/NAServer/Route_World/solve?${params}`
  );
  const body = await res.json();
  const feature = body.routes?.features?.[0];
  if (!res.ok || body.error || !feature) throw new Error(body.error?.message ?? "No route found to this property.");
  const paths: [number, number][][] = feature.geometry.paths;
  return {
    path: paths.flat().map(([x, y]) => [y, x] as [number, number]),
    distanceKm: feature.attributes.Total_Kilometers,
    durationMin: feature.attributes.Total_TravelTime,
    steps: (body.directions?.[0]?.features ?? []).map(
      (f: { attributes: { text: string } }) => f.attributes.text
    ),
  };
}

interface OsrmStep {
  name: string;
  maneuver: { type: string; modifier?: string };
}

function describeOsrmStep(step: OsrmStep) {
  const road = step.name ? ` onto ${step.name}` : "";
  const { type, modifier } = step.maneuver;
  if (type === "depart") return `Head ${modifier ?? "out"}${step.name ? ` on ${step.name}` : ""}`;
  if (type === "arrive") return "Arrive at the property";
  if (type === "roundabout" || type === "rotary") return `Take the roundabout${road}`;
  if (modifier === "straight") return `Continue straight${road}`;
  if (modifier) return `${type === "turn" ? "Turn" : "Keep"} ${modifier}${road}`;
  return `Continue${road}`;
}

async function osrmRoute(from: [number, number], to: [number, number]): Promise<Route> {
  const res = await fetch(
    `https://router.project-osrm.org/route/v1/driving/${from[1]},${from[0]};${to[1]},${to[0]}?overview=full&geometries=geojson&steps=true`
  );
  const body = await res.json();
  const route = body.routes?.[0];
  if (!res.ok || body.code !== "Ok" || !route) throw new Error("No route found to this property.");
  return {
    path: route.geometry.coordinates.map(([lng, lat]: [number, number]) => [lat, lng] as [number, number]),
    distanceKm: route.distance / 1000,
    durationMin: route.duration / 60,
    steps: route.legs[0].steps
      .filter((s: OsrmStep) => s.maneuver.type !== "new name")
      .map(describeOsrmStep),
  };
}
