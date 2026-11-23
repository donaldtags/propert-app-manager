import Link from "next/link";
import { MapPin } from "lucide-react";

export default function PropertyNotFound() {
  return (
    <div className="text-center py-32 text-gray-500">
      <MapPin className="w-12 h-12 mx-auto mb-4 opacity-30" />
      <p className="text-xl font-semibold">Property not found</p>
      <p className="mt-1 text-sm">It may have been removed, or the link is wrong.</p>
      <Link href="/properties" className="mt-4 inline-block text-brand-600 hover:underline">
        ← Back to listings
      </Link>
    </div>
  );
}
