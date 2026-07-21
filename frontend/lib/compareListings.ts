const STORAGE_KEY = "homestead_compare_properties";
export const MAX_COMPARE = 4;

function readIds(): number[] {
  if (typeof window === "undefined") return [];
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as number[]) : [];
  } catch {
    return [];
  }
}

function writeIds(ids: number[]) {
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(ids));
  window.dispatchEvent(new Event("compare-listings-changed"));
}

export function isComparing(propertyId: number): boolean {
  return readIds().includes(propertyId);
}

export function toggleCompare(propertyId: number): boolean {
  const ids = readIds();
  const index = ids.indexOf(propertyId);
  if (index === -1) {
    if (ids.length >= MAX_COMPARE) return false;
    ids.push(propertyId);
    writeIds(ids);
    return true;
  }
  ids.splice(index, 1);
  writeIds(ids);
  return false;
}

export function removeFromCompare(propertyId: number) {
  writeIds(readIds().filter((id) => id !== propertyId));
}

export function clearCompare() {
  writeIds([]);
}

export function getCompareIds(): number[] {
  return readIds();
}
