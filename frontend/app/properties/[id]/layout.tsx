import type { Metadata } from "next";
import { notFound } from "next/navigation";
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
  if (!found?.found || !found.title) return { title: "Property" };
  return { title: found.city ? `${found.title}, ${found.city}` : found.title };
}

export default async function PropertyLayout({ params, children }: Props) {
  // A listing that doesn't exist answers 404, so search engines and uptime checks see it as missing.
  if ((await lookup((await params).id))?.found === false) notFound();
  return children;
}
