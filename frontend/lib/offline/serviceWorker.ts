"use client";

let registered = false;

/**
 * Registers /sw.js (production builds only; in `next dev` a service worker would fight hot
 * reloading) and asks it to cache the given workspace pages so they open offline later.
 */
export function registerServiceWorker(warmUrls: string[] = []): void {
  if (typeof window === "undefined" || !("serviceWorker" in navigator)) return;
  if (process.env.NODE_ENV !== "production") return;
  const warm = (sw: ServiceWorker | null | undefined) => {
    if (sw && warmUrls.length) sw.postMessage({ type: "warm", urls: warmUrls });
  };
  if (registered) {
    void navigator.serviceWorker.ready.then((r) => warm(r.active));
    return;
  }
  registered = true;
  navigator.serviceWorker
    .register("/sw.js", { scope: "/" })
    .then(() => navigator.serviceWorker.ready)
    .then((r) => warm(r.active))
    .catch(() => {
      registered = false; // e.g. insecure origin; the app still works online
    });
}
