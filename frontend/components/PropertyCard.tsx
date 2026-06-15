"use client";

import Link from "next/link";
import Image from "next/image";
import { Bed, Bath, MapPin, Shield, Globe, CheckCircle } from "lucide-react";
import type { Property } from "@/lib/types";

interface Props {
  property: Property;
  highlighted?: boolean;
  onMouseEnter?: () => void;
  onMouseLeave?: () => void;
  compact?: boolean;
}

function formatPrice(price: number, currency: string, listingType: string) {
  const formatted = new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: currency === "USD" ? "USD" : "USD",
    maximumFractionDigits: 0,
  }).format(price);
  if (listingType === "RENT") return `${formatted}/mo`;
  if (listingType === "SHORT_STAY") return `${formatted}/night`;
  return formatted;
}

function getPrimaryPhoto(property: Property): string | null {
  const sources = [property.photoUrls, property.imageUrls, property.photos];
  for (const src of sources) {
    if (src && src.length > 0 && src[0]) return src[0];
  }
  return null;
}

export default function PropertyCard({
  property,
  highlighted,
  onMouseEnter,
  onMouseLeave,
  compact,
}: Props) {
  const photo = getPrimaryPhoto(property);
  const isVerified = property.verificationStatus === "VERIFIED";

  return (
    <Link
      href={`/properties/${property.id}`}
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
      className={`block bg-white rounded-xl overflow-hidden border transition-all duration-200 hover:shadow-lg ${
        highlighted
          ? "border-blue-500 shadow-lg ring-2 ring-blue-200"
          : "border-gray-200 shadow-sm"
      }`}
    >
      {/* Photo */}
      <div className={`relative bg-gray-100 ${compact ? "h-40" : "h-52"}`}>
        {photo ? (
          <Image
            src={photo}
            alt={property.title}
            fill
            className="object-cover"
            sizes="(max-width: 768px) 100vw, 400px"
            unoptimized
          />
        ) : (
          <div className="absolute inset-0 flex items-center justify-center text-gray-400">
            <MapPin className="w-8 h-8" />
          </div>
        )}

        {/* Type badge */}
        <div className="absolute top-3 left-3">
          <span
            className={`text-xs font-bold px-2 py-1 rounded ${
              property.listingType === "RENT"
                ? "bg-blue-600 text-white"
                : property.listingType === "SALE"
                ? "bg-green-600 text-white"
                : "bg-purple-600 text-white"
            }`}
          >
            {property.listingType === "SHORT_STAY" ? "SHORT STAY" : property.listingType}
          </span>
        </div>

        {/* Verified badge */}
        {isVerified && (
          <div className="absolute top-3 right-3">
            <span className="bg-white text-green-600 text-xs font-bold px-2 py-1 rounded flex items-center gap-1 shadow">
              <CheckCircle className="w-3 h-3" />
              Verified
            </span>
          </div>
        )}
      </div>

      {/* Content */}
      <div className="p-4">
        <p className="text-xl font-bold text-gray-900">
          {formatPrice(property.price, property.currency, property.listingType)}
        </p>

        <div className="flex items-center gap-3 mt-1 text-sm text-gray-600">
          <span className="flex items-center gap-1">
            <Bed className="w-4 h-4" /> {property.bedrooms} bd
          </span>
          <span className="text-gray-300">|</span>
          <span className="flex items-center gap-1">
            <Bath className="w-4 h-4" /> {property.bathrooms} ba
          </span>
        </div>

        <p className="mt-1 text-sm text-gray-700 font-medium truncate">{property.title}</p>
        <p className="text-xs text-gray-500 flex items-center gap-1 mt-0.5">
          <MapPin className="w-3 h-3 shrink-0" />
          {property.suburb}, {property.city}
        </p>

        {/* Feature badges */}
        <div className="flex flex-wrap gap-1.5 mt-2">
          {property.escrowRequired && (
            <span className="text-xs bg-blue-50 text-blue-700 px-2 py-0.5 rounded-full flex items-center gap-1">
              <Shield className="w-3 h-3" /> Escrow Protected
            </span>
          )}
          {property.diasporaFriendly && (
            <span className="text-xs bg-amber-50 text-amber-700 px-2 py-0.5 rounded-full flex items-center gap-1">
              <Globe className="w-3 h-3" /> Diaspora Friendly
            </span>
          )}
        </div>
      </div>
    </Link>
  );
}
