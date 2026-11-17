/*
 * EstateX service worker: keeps the app shell usable offline.
 *  - /_next/static/*  (hashed, immutable)     cache-first
 *  - page navigations                           network-first, fall back to the cached page,
 *                                               then to /offline.html
 *  - other same-origin GETs (icons, fonts, logos) stale-while-revalidate
 * API calls go to the backend origin and aren't touched here: the app caches API reads in
 * IndexedDB and queues writes in its outbox (lib/offline).
 */
const VERSION = "v1";
const STATIC = `ex-static-${VERSION}`;
const PAGES = `ex-pages-${VERSION}`;
const ASSETS = `ex-assets-${VERSION}`;
const OFFLINE_URL = "/offline.html";

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(PAGES).then((c) => c.add(OFFLINE_URL)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((k) => k.startsWith("ex-") && ![STATIC, PAGES, ASSETS].includes(k)).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

/** The app asks for its key workspace pages to be cached right after sign-in. */
self.addEventListener("message", (event) => {
  if (event.data && event.data.type === "warm" && Array.isArray(event.data.urls)) {
    event.waitUntil(
      caches.open(PAGES).then((cache) =>
        Promise.all(
          event.data.urls.map((url) =>
            fetch(url, { credentials: "same-origin" })
              .then((res) => (res.ok ? cache.put(url, res) : undefined))
              .catch(() => undefined)
          )
        )
      )
    );
  }
});

self.addEventListener("fetch", (event) => {
  const { request } = event;
  if (request.method !== "GET") return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;

  if (url.pathname.startsWith("/_next/static/")) {
    event.respondWith(
      caches.open(STATIC).then(async (cache) => {
        const hit = await cache.match(request);
        if (hit) return hit;
        const res = await fetch(request);
        if (res.ok) cache.put(request, res.clone());
        return res;
      })
    );
    return;
  }

  if (request.mode === "navigate") {
    event.respondWith(
      (async () => {
        const cache = await caches.open(PAGES);
        try {
          const res = await fetch(request);
          if (res.ok) cache.put(url.pathname, res.clone());
          return res;
        } catch {
          return (await cache.match(url.pathname)) || (await cache.match(OFFLINE_URL));
        }
      })()
    );
    return;
  }

  // Skip framework data requests (RSC payloads, HMR); they're not useful to replay stale.
  if (url.searchParams.has("_rsc") || request.headers.get("RSC") || url.pathname.startsWith("/_next/")) return;

  event.respondWith(
    caches.open(ASSETS).then(async (cache) => {
      const hit = await cache.match(request);
      const network = fetch(request)
        .then((res) => {
          if (res.ok) cache.put(request, res.clone());
          return res;
        })
        .catch(() => hit);
      return hit || network;
    })
  );
});
