"use client";

import { useSyncExternalStore } from "react";
import { locations as locationsApi } from "./api";
import type { LocationNode } from "./types";
import { ZIMBABWE_CITIES, ZIMBABWE_SUBURBS } from "./zimbabweLocations";

/**
 * Zimbabwe location reference data from the backend (/locations), with the static lists in
 * zimbabweLocations.ts as the fallback until the first fetch lands (or when offline with no
 * cache). Cached in localStorage so pickers work offline; Phase 1c moves this to IndexedDB.
 */
export interface LocationIndex {
  /** Cities first (largest first), then towns and growth points alphabetically. */
  cities: string[];
  suburbs: Record<string, string[]>;
  entries: { suburb: string; city: string }[];
  cityIds: Record<string, number>;
  aliases: Record<string, { suburb: string; city: string }>;
  fromServer: boolean;
}

const CACHE_KEY = "ex_locations_v1";
const SETTLEMENT_TYPES = new Set(["CITY", "TOWN", "GROWTH_POINT"]);

export function buildIndex(nodes: LocationNode[]): LocationIndex {
  const byId = new Map(nodes.map((n) => [n.id, n]));
  const priority = new Map(ZIMBABWE_CITIES.map((c, i) => [c, i]));
  const settlements = nodes.filter((n) => SETTLEMENT_TYPES.has(n.type));
  settlements.sort((a, b) => {
    const pa = priority.get(a.name) ?? (a.type === "CITY" ? 500 : 1000);
    const pb = priority.get(b.name) ?? (b.type === "CITY" ? 500 : 1000);
    return pa - pb || a.name.localeCompare(b.name);
  });

  const suburbs: Record<string, string[]> = {};
  const aliases: Record<string, { suburb: string; city: string }> = {};
  const cityIds: Record<string, number> = {};
  for (const s of settlements) {
    suburbs[s.name] = [];
    cityIds[s.name] = s.id;
  }
  for (const n of nodes) {
    if (n.type !== "SUBURB" || n.parentId == null) continue;
    const parent = byId.get(n.parentId);
    if (!parent) continue;
    (suburbs[parent.name] ??= []).push(n.name);
    for (const alias of n.aliases ?? []) aliases[alias.toLowerCase()] = { suburb: n.name, city: parent.name };
  }
  for (const list of Object.values(suburbs)) list.sort((a, b) => a.localeCompare(b));

  const cities = settlements.map((s) => s.name);
  return {
    cities,
    suburbs,
    entries: cities.flatMap((city) => suburbs[city].map((suburb) => ({ suburb, city }))),
    cityIds,
    aliases,
    fromServer: true,
  };
}

function staticIndex(): LocationIndex {
  return {
    cities: ZIMBABWE_CITIES,
    suburbs: ZIMBABWE_SUBURBS,
    entries: Object.entries(ZIMBABWE_SUBURBS).flatMap(([city, list]) => list.map((suburb) => ({ suburb, city }))),
    cityIds: {},
    aliases: {},
    fromServer: false,
  };
}

/**
 * Resolves free text to a known city, or a suburb and its city. Cities match exactly
 * (case-insensitive); suburbs match exactly, then by alias, then by prefix.
 */
export function resolveLocation(index: LocationIndex, query: string): { city: string; suburb: string } | null {
  const term = query.trim().toLowerCase();
  if (!term) return null;
  const city = index.cities.find((c) => c.toLowerCase() === term);
  if (city) return { city, suburb: "" };
  const exact = index.entries.find((e) => e.suburb.toLowerCase() === term);
  if (exact) return exact;
  if (index.aliases[term]) return index.aliases[term];
  return index.entries.find((e) => e.suburb.toLowerCase().startsWith(term)) ?? null;
}

// --- tiny external store ------------------------------------------------------------------

const SERVER_SNAPSHOT = staticIndex();
let current: LocationIndex | null = null;
let fetchStarted = false;
const listeners = new Set<() => void>();

function readCache(): LocationIndex | null {
  try {
    const raw = window.localStorage.getItem(CACHE_KEY);
    return raw ? buildIndex(JSON.parse(raw) as LocationNode[]) : null;
  } catch {
    return null;
  }
}

function publish(next: LocationIndex) {
  current = next;
  listeners.forEach((l) => l());
}

function ensureFetched() {
  if (fetchStarted) return;
  fetchStarted = true;
  locationsApi
    .all()
    .then((nodes) => {
      if (!nodes?.length) return;
      try {
        window.localStorage.setItem(CACHE_KEY, JSON.stringify(nodes));
      } catch {
        // quota/private mode: still use the fresh data for this session
      }
      publish(buildIndex(nodes));
    })
    .catch(() => {
      fetchStarted = false; // offline or API down: keep cache/static data, retry next mount
    });
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  ensureFetched();
  return () => listeners.delete(listener);
}

function getSnapshot(): LocationIndex {
  if (!current) current = readCache() ?? SERVER_SNAPSHOT;
  return current;
}

export function useZimbabweLocations(): LocationIndex & {
  resolve: (query: string) => { city: string; suburb: string } | null;
} {
  const index = useSyncExternalStore(subscribe, getSnapshot, () => SERVER_SNAPSHOT);
  return { ...index, resolve: (q: string) => resolveLocation(index, q) };
}

/**
 * Turns whatever someone typed in a search box ("Harare", "Borrowdale", "Avondale, Harare",
 * "somewhere new") into the city/suburb filters the API understands. Shared by the home page
 * and the listings page so the same words always find the same homes.
 */
export function locationFilters(
  query: string,
  resolve: (q: string) => { city: string; suburb: string } | null
): { city: string; suburb: string } {
  const parts = query.split(",").map((s) => s.trim()).filter(Boolean);
  if (parts.length === 0) return { city: "", suburb: "" };
  if (parts.length >= 2) return { city: parts[1], suburb: parts[0] };
  // Unknown text goes in as a suburb: that filter is a partial match, the city filter is exact.
  return resolve(parts[0]) ?? { city: "", suburb: parts[0] };
}
