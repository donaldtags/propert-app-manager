"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import Image from "next/image";
import { Scale, X, MapPin, BadgeCheck, Sun, BatteryCharging, Droplets, Globe, Shield, Star } from "lucide-react";
import { properties as propertiesApi, ratings as ratingsApi } from "@/lib/api";
import type { Property } from "@/lib/types";
import { removeFromCompare } from "@/lib/compareListings";

function getPhotos(property: Property): string[] {
  const photos = [...(property.photoUrls ?? []), ...(property.imageUrls ?? []), ...(property.photos ?? [])];
  return [...new Set(photos)].filter(Boolean);
}

function formatPrice(price: number, currency: string, listingType: string) {
  const formatted = new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: currency || "USD",
    maximumFractionDigits: 0,
  }).format(price);
  if (listingType === "RENT") return `${formatted}/mo`;
  if (listingType === "SHORT_STAY") return `${formatted}/night`;
  return formatted;
}

interface Row {
  label: string;
  render: (p: Property, rating: { average: number | null; count: number }) => React.ReactNode;
}

const ROWS: Row[] = [
  { label: "Price", render: (p) => <span className="font-bold text-gray-900">{formatPrice(p.price, p.currency, p.listingType)}</span> },
  { label: "Location", render: (p) => `${p.suburb}, ${p.city}` },
  { label: "Bedrooms", render: (p) => p.bedrooms },
  { label: "Bathrooms", render: (p) => p.bathrooms },
  {
    label: "Verification",
    render: (p) =>
      p.verificationStatus === "VERIFIED" ? (
        <span className="inline-flex items-center gap-1 text-forest-700"><BadgeCheck className="w-4 h-4" /> Verified</span>
      ) : (
        <span className="text-gray-400">Unverified</span>
      ),
  },
  {
    label: "Escrow Available",
    render: (p) => (p.escrowRequired ? <span className="inline-flex items-center gap-1 text-forest-700"><Shield className="w-4 h-4" /> Yes</span> : "No"),
  },
  {
    label: "Solar",
    render: (p) => (p.solarInstalled ? <span className="inline-flex items-center gap-1 text-amber-600"><Sun className="w-4 h-4" /> Yes</span> : "No"),
  },
  {
    label: "Backup Power",
    render: (p) => (p.backupPower ? <span className="inline-flex items-center gap-1 text-indigo-600"><BatteryCharging className="w-4 h-4" /> Yes</span> : "No"),
  },
  {
    label: "Water Source",
    render: (p) => (p.waterSource ? <span className="inline-flex items-center gap-1 text-cyan-600"><Droplets className="w-4 h-4" /> {p.waterSource.replace(/_/g, " ")}</span> : "—"),
  },
  {
    label: "Diaspora Friendly",
    render: (p) => (p.diasporaFriendly ? <span className="inline-flex items-center gap-1 text-amber-700"><Globe className="w-4 h-4" /> Yes</span> : "No"),
  },
  { label: "Furnished", render: (p) => (p.furnished ? "Yes" : "No") },
  { label: "Internet / Fibre", render: (p) => (p.internetAvailable ? "Yes" : "No") },
  { label: "Security", render: (p) => (p.securityFeatures ? "Yes" : "No") },
  { label: "Parking", render: (p) => (p.parkingAvailable ? "Yes" : "No") },
  { label: "Pets Allowed", render: (p) => (p.petsAllowed ? "Yes" : "No") },
  {
    label: "Landlord/Agent Trust Score",
    render: (p) => {
      const score = p.agentName ? p.agentTrustScore : p.landlordTrustScore;
      return score != null ? `${score}/100` : "—";
    },
  },
  {
    label: "Landlord/Agent Rating",
    render: (p, rating) =>
      rating.count > 0 && rating.average != null ? (
        <span className="inline-flex items-center gap-1"><Star className="w-4 h-4 text-amber-500 fill-current" /> {rating.average.toFixed(1)}/5 ({rating.count})</span>
      ) : (
        "No ratings yet"
      ),
  },
];

function ComparePageInner() {
  const searchParams = useSearchParams();
  const ids = (searchParams.get("ids") ?? "")
    .split(",")
    .map((v) => Number(v.trim()))
    .filter((v) => Number.isFinite(v) && v > 0);

  const [properties, setProperties] = useState<Property[]>([]);
  const [ratings, setRatings] = useState<Record<number, { average: number | null; count: number }>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    if (ids.length === 0) {
      setLoading(false);
      return;
    }
    setLoading(true);
    Promise.allSettled(ids.map((id) => propertiesApi.get(id)))
      .then(async (results) => {
        const loaded = results
          .filter((r): r is PromiseFulfilledResult<Property> => r.status === "fulfilled")
          .map((r) => r.value);
        setProperties(loaded);

        const ownerIds = [...new Set(loaded.map((p) => p.agentId ?? p.landlordId).filter(Boolean))] as number[];
        const ratingResults = await Promise.allSettled(ownerIds.map((id) => ratingsApi.listByLandlord(id)));
        const map: Record<number, { average: number | null; count: number }> = {};
        loaded.forEach((p) => {
          const ownerId = p.agentId ?? p.landlordId;
          const idx = ownerIds.indexOf(ownerId as number);
          const result = idx >= 0 ? ratingResults[idx] : null;
          if (result && result.status === "fulfilled" && result.value.length > 0) {
            const avg = result.value.reduce((s, r) => s + r.rating, 0) / result.value.length;
            map[p.id] = { average: avg, count: result.value.length };
          } else {
            map[p.id] = { average: null, count: 0 };
          }
        });
        setRatings(map);
      })
      .catch(() => setError("Failed to load properties for comparison."))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams.get("ids")]);

  if (loading) {
    return (
      <div className="max-w-6xl mx-auto px-4 py-8">
        <div className="h-96 bg-gray-100 rounded-2xl animate-pulse" />
      </div>
    );
  }

  if (ids.length < 2 || properties.length < 2) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-16 text-center text-gray-500">
        <Scale className="w-12 h-12 mx-auto mb-4 opacity-30" />
        <p className="text-lg font-medium">Select at least 2 properties to compare</p>
        <Link href="/properties" className="text-sm text-forest-600 hover:underline mt-2 inline-block">
          Browse properties
        </Link>
      </div>
    );
  }

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
          <Scale className="w-7 h-7 text-forest-600" /> Compare Properties
        </h1>
        <p className="text-gray-500 mt-1">Side-by-side comparison of your selected homes</p>
      </div>

      {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-5">{error}</div>}

      <div className="overflow-x-auto">
        <table className="w-full text-sm border-collapse min-w-[640px]">
          <thead>
            <tr>
              <th className="text-left p-3 w-48"></th>
              {properties.map((p) => {
                const photo = getPhotos(p)[0];
                return (
                  <th key={p.id} className="p-3 align-top">
                    <div className="relative bg-white border border-gray-200 rounded-2xl overflow-hidden shadow-sm">
                      <button
                        onClick={() => removeFromCompare(p.id)}
                        className="absolute top-2 right-2 z-10 w-6 h-6 bg-white/90 hover:bg-white rounded-full flex items-center justify-center text-gray-500"
                      >
                        <X className="w-3.5 h-3.5" />
                      </button>
                      <div className="relative h-32 bg-gray-100">
                        {photo ? (
                          <Image src={photo} alt={p.title} fill className="object-cover" unoptimized sizes="200px" />
                        ) : (
                          <div className="absolute inset-0 flex items-center justify-center text-gray-400">
                            <MapPin className="w-6 h-6" />
                          </div>
                        )}
                      </div>
                      <div className="p-3">
                        <Link href={`/properties/${p.id}`} className="text-sm font-semibold text-gray-900 hover:text-forest-600 line-clamp-2">
                          {p.title}
                        </Link>
                      </div>
                    </div>
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody>
            {ROWS.map((row) => (
              <tr key={row.label} className="border-t border-gray-100">
                <td className="p-3 text-xs font-semibold text-gray-500 uppercase tracking-wide align-top">{row.label}</td>
                {properties.map((p) => (
                  <td key={p.id} className="p-3 text-gray-800 align-top">
                    {row.render(p, ratings[p.id] ?? { average: null, count: 0 })}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default function ComparePage() {
  return (
    <Suspense fallback={<div className="max-w-6xl mx-auto px-4 py-8"><div className="h-96 bg-gray-100 rounded-2xl animate-pulse" /></div>}>
      <ComparePageInner />
    </Suspense>
  );
}
