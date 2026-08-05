"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { Bed, Bath, MapPin, Shield, Globe, CheckCircle, Building2, Camera, Scale, Star } from "lucide-react";
import type { Property } from "@/lib/types";
import { isComparing, toggleCompare, MAX_COMPARE } from "@/lib/compareListings";

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
    currency: currency || "USD",
    maximumFractionDigits: 0,
  }).format(price);
  if (listingType === "RENT") return `${formatted}/mo`;
  if (listingType === "SHORT_STAY") return `${formatted}/night`;
  return formatted;
}

function getPhotos(property: Property): string[] {
  const photos = [
    ...(property.photoUrls ?? []),
    ...(property.imageUrls ?? []),
    ...(property.photos ?? []),
  ];
  return [...new Set(photos)].filter(Boolean);
}

export default function PropertyCard({
  property,
  highlighted,
  onMouseEnter,
  onMouseLeave,
  compact,
}: Props) {
  const photos = getPhotos(property);
  const photo = photos[0] ?? null;
  const isVerified = property.verificationStatus === "VERIFIED";
  const isFeatured = property.featured && (!property.featuredUntil || new Date(property.featuredUntil).getTime() > Date.now());
  const companyName = property.agentName
    ? property.agentCompanyName
    : property.landlordCompanyName;

  const [comparing, setComparing] = useState(false);
  useEffect(() => {
    setComparing(isComparing(property.id));
  }, [property.id]);

  const handleToggleCompare = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setComparing(toggleCompare(property.id));
  };

  return (
    <Link
      href={`/properties/${property.id}`}
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
      className={`group block bg-white rounded-xl overflow-hidden border transition-all duration-200 hover:shadow-lg ${
        highlighted
          ? "border-blue-500 shadow-lg ring-2 ring-blue-200"
          : isFeatured
            ? "border-amber-300 shadow-md ring-1 ring-amber-200"
            : "border-gray-200 shadow-sm"
      }`}
    >
      {/* Photo */}
      <div className={`relative bg-gray-100 overflow-hidden ${compact ? "h-40" : "h-52"}`}>
        {photo ? (
          <Image
            src={photo}
            alt={property.title}
            fill
            className="object-cover transition-transform duration-300 ease-out group-hover:scale-105"
            sizes="(max-width: 768px) 100vw, 400px"
            unoptimized
          />
        ) : (
          <div className="absolute inset-0 flex items-center justify-center text-gray-400">
            <MapPin className="w-8 h-8" />
          </div>
        )}

        {isFeatured && (
          <div className="absolute top-0 left-0">
            <span className="bg-amber-500 text-white text-xs font-bold pl-2 pr-3 py-1 rounded-br-lg flex items-center gap-1 shadow">
              <Star className="w-3 h-3 fill-white" /> Featured
            </span>
          </div>
        )}

        {/* Type badge */}
        <div className={`absolute left-3 ${isFeatured ? "top-9" : "top-3"}`}>
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

        {/* Photo count */}
        {photos.length > 1 && (
          <div className="absolute bottom-3 right-3">
            <span className="bg-black/60 text-white text-xs font-medium px-2 py-1 rounded flex items-center gap-1">
              <Camera className="w-3 h-3" />
              {photos.length}
            </span>
          </div>
        )}

        {/* Compare toggle */}
        <button
          type="button"
          onClick={handleToggleCompare}
          title={comparing ? "Remove from comparison" : `Add to comparison (max ${MAX_COMPARE})`}
          className={`absolute bottom-3 left-3 w-7 h-7 rounded-full flex items-center justify-center shadow transition-colors ${
            comparing ? "bg-blue-600 text-white" : "bg-white/90 text-gray-600 hover:bg-white"
          }`}
        >
          <Scale className="w-3.5 h-3.5" />
        </button>
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

        {companyName && (
          <p className="text-xs text-blue-700 font-medium flex items-center gap-1 mt-0.5">
            <Building2 className="w-3 h-3 shrink-0" />
            {companyName}
          </p>
        )}

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
