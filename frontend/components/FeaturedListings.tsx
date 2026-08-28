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

export default async function FeaturedListings() {
  const featured = await properties
    .list({ listingType: "RENT" })
    .then((data) => data.slice(0, 6))
    .catch(() => []);

  if (featured.length === 0) {
    return (
      <div className="text-center py-16 text-gray-500">
        <p className="text-lg font-medium">No listings yet</p>
        <p className="text-sm mt-1">Be the first to list a property</p>
        <Link
          href="/register"
          className="mt-4 inline-block bg-forest-600 text-white px-6 py-2.5 rounded-xl font-semibold hover:bg-forest-700 transition-colors"
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
