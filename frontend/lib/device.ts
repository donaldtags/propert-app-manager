/**
 * A random id this browser keeps, sent at sign-in so the server can tell a familiar device from
 * a new one (and warn the account owner about the new one). It identifies the browser, not the
 * person, and is never shared with anyone else.
 */
const DEVICE_KEY = "estatex_device_id";

function newId(): string {
  try {
    if (typeof crypto !== "undefined" && "randomUUID" in crypto) return crypto.randomUUID();
  } catch {
    // fall through
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`;
}

export function getDeviceId(): string | undefined {
  try {
    let id = localStorage.getItem(DEVICE_KEY);
    if (!id) {
      id = newId();
      localStorage.setItem(DEVICE_KEY, id);
    }
    return id;
  } catch {
    // Private mode / storage blocked: the server falls back to the browser's user agent.
    return undefined;
  }
}

/** Normalises what someone typed in a code box: keeps digits (and letters for backup codes). */
export function cleanCode(raw: string, method: "TOTP" | "SMS" | "BACKUP"): string {
  if (method === "BACKUP") return raw.toUpperCase().replace(/[^A-Z0-9]/g, "");
  return raw.replace(/\D/g, "").slice(0, 6);
}

/** True when the text looks like a phone number rather than an email address. */
export function looksLikePhone(identifier: string): boolean {
  const t = identifier.trim();
  return t.length > 0 && !t.includes("@") && /^[+\d\s().-]+$/.test(t) && t.replace(/\D/g, "").length >= 7;
}
