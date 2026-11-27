import { BASE } from "./api";
import { getDeviceId } from "./device";

/**
 * Anonymous view and search counts for EstateX market analytics. The server stores only a salted
 * hash of this browser's random id (never the id itself, an IP or a name), ignores the owner's
 * own views, and counts each listing once per visitor per hour. Failures are silent: analytics
 * must never get in the way of someone browsing.
 */
function send(body: Record<string, unknown>, token?: string | null) {
  const visitorId = getDeviceId();
  if (!visitorId || typeof window === "undefined") return;
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;
  try {
    void fetch(`${BASE}/marketplace-events`, {
      method: "POST",
      headers,
      body: JSON.stringify({ ...body, visitorId }),
      keepalive: true,
    }).catch(() => {});
  } catch {
    // ignore
  }
}

export function trackListingView(propertyId: number, token?: string | null) {
  send({ type: "VIEW", propertyId }, token);
}

export function trackSearch(
  filters: {
    listingType?: string;
    city?: string;
    suburb?: string;
    bedrooms?: number;
    minPrice?: number;
    maxPrice?: number;
  },
  resultCount: number,
  token?: string | null,
) {
  send({ type: "SEARCH", ...filters, resultCount }, token);
}
