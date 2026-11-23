import Link from "next/link";
import { properties } from "@/lib/api";
import PropertyCard from "@/components/PropertyCard";

export function FeaturedListingsSkeleton() {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
      {Array.from({ length: 6 }).map((_, i) => (
        <div key={i} className="h-72 bg-gray-100 rounded-xl animate-pulse" />
      ))}
    </div>
  );
}

/**
 * How long the home page waits for the API. The page is pre-rendered at build time
 * (revalidate = 60), and an API that hangs, e.g. a sleeping or failed backend on Render,
 * used to hold the whole `next build` past its 60 s per-page limit. Now it renders the
 * empty state and ISR fills listings in on the next revalidation.
 */
const FEATURED_TIMEOUT_MS = 8_000;

function withTimeout<T>(promise: Promise<T>, ms: number, fallback: T): Promise<T> {
  return new Promise((resolve) => {
    const timer = setTimeout(() => resolve(fallback), ms);
    promise.then(
      (value) => {
        clearTimeout(timer);
        resolve(value);
      },
      () => {
        clearTimeout(timer);
        resolve(fallback);
      }
    );
  });
}

export default async function FeaturedListings() {
  const featured = await withTimeout(
    // The newest six of every type (rent, sale, stays); verified and featured ones first.
    properties.searchPaged({ page: 0, size: 6 }).then((r) => r.items),
    FEATURED_TIMEOUT_MS,
    [] as Awaited<ReturnType<typeof properties.list>>
  );

  if (featured.length === 0) {
    return (
      <div className="text-center py-16 text-gray-500">
        <p className="text-lg font-medium">No listings yet</p>
        <p className="text-sm mt-1">Be the first to list a property</p>
        <Link
          href="/register"
          className="mt-4 inline-block bg-brand-600 text-white px-6 py-2.5 rounded-xl font-semibold hover:bg-brand-700 transition-colors"
        >
          Get Started
        </Link>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
      {featured.map((property) => (
        <PropertyCard key={property.id} property={property} />
      ))}
    </div>
  );
}
