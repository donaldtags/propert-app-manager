 const STORAGE_KEY = "homestead_saved_properties";

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
}

export function isSaved(propertyId: number): boolean {
  return readIds().includes(propertyId);
}

export function toggleSaved(propertyId: number): boolean {
  const ids = readIds();
  const index = ids.indexOf(propertyId);
  if (index === -1) {
    ids.push(propertyId);
    writeIds(ids);
    return true;
  }
  ids.splice(index, 1);
  writeIds(ids);
  return false;
}

export function getSavedIds(): number[] {
  return readIds();
}