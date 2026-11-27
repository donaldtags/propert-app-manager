import { readMigrated } from "./storageKeys";
import { request } from "./api";

const STORAGE_KEY = "estatex_saved_properties";
const LEGACY_KEY = "primenest_saved_properties";

function readIds(): number[] {
  if (typeof window === "undefined") return [];
  try {
    const raw = readMigrated(STORAGE_KEY, LEGACY_KEY);
    return raw ? (JSON.parse(raw) as number[]) : [];
  } catch {
    return [];
  }
}

function writeIds(ids: number[]) {
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(ids));
}

export function isSaved(propertyId: number): boolean {
  return readIds().includes(propertyId);
}

/**
 * Saves or unsaves on this device, and also on the account when signed in, so saved homes follow
 * the person across devices and count towards "most saved" in market analytics. The account
 * copy is best effort: an offline save still works locally.
 */
export function toggleSaved(propertyId: number, token?: string | null): boolean {
  const ids = readIds();
  const index = ids.indexOf(propertyId);
  const nowSaved = index === -1;
  if (nowSaved) ids.push(propertyId);
  else ids.splice(index, 1);
  writeIds(ids);
  if (token) {
    request(`/saved-properties/${propertyId}`, { method: nowSaved ? "PUT" : "DELETE" }, token).catch(() => {});
  }
  return nowSaved;
}

export function getSavedIds(): number[] {
  return readIds();
}