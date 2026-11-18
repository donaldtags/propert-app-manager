/**
 * Reads a localStorage value saved under the current key, falling back to (and moving) a value
 * saved under the platform's former PrimeNest key, so people keep their saved homes, comparisons
 * and checklists after the rebrand.
 */
export function readMigrated(key: string, legacyKey: string): string | null {
  const current = window.localStorage.getItem(key);
  if (current !== null) return current;
  const legacy = window.localStorage.getItem(legacyKey);
  if (legacy !== null) {
    try {
      window.localStorage.setItem(key, legacy);
      window.localStorage.removeItem(legacyKey);
    } catch {
      // storage full or blocked: still return the legacy value for this read
    }
  }
  return legacy;
}
