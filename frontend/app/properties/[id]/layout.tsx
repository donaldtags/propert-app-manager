import type { Metadata } from "next";
import { cache } from "react";

const API = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8081/api/v1";

type Lookup = { found: true; title: string; city?: string } | { found: false } | null;

/**
 * Looks the listing up once per request for the tab title and the 404 status. A short timeout keeps
 * a slow or sleeping API from holding the page: on timeout we return null and the page loads as usual.
 */
const lookup = cache(async (id: string): Promise<Lookup> => {
  if (!/^\d+$/.test(id)) return { found: false };
  try {
    const res = await fetch(`${API}/properties/${id}`, { signal: AbortSignal.timeout(3000), next: { revalidate: 60 } });
    if (res.status === 404) return { found: false };
    if (!res.ok) return null;
    const p = (await res.json()) as { title?: string; city?: string };
    return { found: true, title: (p.title ?? "").trim(), city: p.city };
  } catch {
    return null;
  }
});

type Props = { params: Promise<{ id: string }>; children: React.ReactNode };

export async function generateMetadata({ params }: Omit<Props, "children">): Promise<Metadata> {
  const found = await lookup((await params).id);
  // Not visible to the public: missing, or hidden (draft, unpaid listing fee, withdrawn). Keep it out
  // of search engines, but still render the page: its owner, signed in, can open and pay for it.
  if (found?.found === false) return { title: "Property", robots: { index: false, follow: false } };
  if (!found?.found || !found.title) return { title: "Property" };
  return { title: found.city ? `${found.title}, ${found.city}` : found.title };
}

/**
 * This lookup runs on the server without the visitor's sign-in, so a 404 here can't tell "doesn't
 * exist" from "hidden from the public but yours". Calling notFound() on it locked owners out of their
 * own unpublished listings (including every new listing waiting for its fee), so the page itself
 * decides, with the visitor's token, and shows "Property not found" when it really is.
 */
export default async function PropertyLayout({ children }: Props) {
  return children;
}
