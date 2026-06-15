"use client";

import { useState, useEffect, useCallback, Suspense } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import dynamic from "next/dynamic";
import { Search, SlidersHorizontal, X, Map, List, ChevronDown } from "lucide-react";
import { properties as propertiesApi } from "@/lib/api";
import type { Property, ListingType } from "@/lib/types";
import PropertyCard from "@/components/PropertyCard";

const PropertyMap = dynamic(() => import("@/components/PropertyMap"), {
  ssr: false,
  loading: () => (
    <div className="w-full h-full bg-gray-100 flex items-center justify-center">
      <p className="text-gray-400">Loading map...</p>
    </div>
  ),
});

const CITIES = ["Harare", "Bulawayo", "Mutare", "Gweru", "Masvingo"];
const BED_OPTIONS = [1, 2, 3, 4, 5];
const PRICE_RANGES_RENT = [
  { label: "Any", min: undefined, max: undefined },
  { label: "Under $300", min: undefined, max: 300 },
  { label: "$300–$600", min: 300, max: 600 },
  { label: "$600–$1000", min: 600, max: 1000 },
  { label: "$1000+", min: 1000, max: undefined },
];
const PRICE_RANGES_SALE = [
  { label: "Any", min: undefined, max: undefined },
  { label: "Under $50K", min: undefined, max: 50000 },
  { label: "$50K–$150K", min: 50000, max: 150000 },
  { label: "$150K–$500K", min: 150000, max: 500000 },
  { label: "$500K+", min: 500000, max: undefined },
];

function PropertiesContent() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const [listingType, setListingType] = useState<ListingType>(
    (searchParams.get("listingType") as ListingType) ?? "RENT"
  );
  const [city, setCity] = useState(searchParams.get("city") ?? "");
  const [suburb, setSuburb] = useState(searchParams.get("suburb") ?? "");
  const [bedrooms, setBedrooms] = useState<number | undefined>(
    searchParams.get("bedrooms") ? Number(searchParams.get("bedrooms")) : undefined
  );
  const [minPrice, setMinPrice] = useState<number | undefined>(undefined);
  const [maxPrice, setMaxPrice] = useState<number | undefined>(undefined);
  const [diasporaFriendly, setDiasporaFriendly] = useState(false);

  const [results, setResults] = useState<Property[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [highlightedId, setHighlightedId] = useState<number | null>(null);
  const [mobileView, setMobileView] = useState<"list" | "map">("list");
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState(searchParams.get("city") ?? "");

  const fetchProperties = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const data = await propertiesApi.list({
        listingType,
        city: city || undefined,
        suburb: suburb || undefined,
        bedrooms,
        minPrice,
        maxPrice,
        diasporaFriendly: diasporaFriendly || undefined,
      });
      setResults(data);
    } catch {
      setError("Failed to load properties. Is the backend running?");
    } finally {
      setLoading(false);
    }
  }, [listingType, city, suburb, bedrooms, minPrice, maxPrice, diasporaFriendly]);

  useEffect(() => {
    fetchProperties();
  }, [fetchProperties]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    const parts = searchQuery.split(",").map((s) => s.trim());
    if (parts.length >= 2) {
      setSuburb(parts[0]);
      setCity(parts[1]);
    } else {
      setCity(parts[0]);
      setSuburb("");
    }
  };

  const priceRanges = listingType === "RENT" ? PRICE_RANGES_RENT : PRICE_RANGES_SALE;

  const activePriceLabel = priceRanges.find(
    (r) => r.min === minPrice && r.max === maxPrice
  )?.label ?? "Price";

  return (
    <div className="flex flex-col flex-1" style={{ height: "calc(100vh - 64px)" }}>
      {/* Top filter bar */}
      <div className="border-b border-gray-200 bg-white px-4 py-3 flex flex-wrap items-center gap-3 z-20">
        {/* Type tabs */}
        <div className="flex rounded-lg border border-gray-200 overflow-hidden text-sm font-medium">
          {(["RENT", "SALE", "SHORT_STAY"] as ListingType[]).map((t) => (
            <button
              key={t}
              onClick={() => setListingType(t)}
              className={`px-4 py-2 transition-colors ${
                listingType === t ? "bg-blue-600 text-white" : "bg-white text-gray-600 hover:bg-gray-50"
              }`}
            >
              {t === "SHORT_STAY" ? "Short Stay" : t === "RENT" ? "Rent" : "Buy"}
            </button>
          ))}
        </div>

        {/* Search input */}
        <form onSubmit={handleSearch} className="flex items-center gap-2 border border-gray-200 rounded-lg px-3 py-2 bg-white min-w-48 flex-1 max-w-64">
          <Search className="w-4 h-4 text-gray-400 shrink-0" />
          <input
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="City or suburb..."
            className="text-sm text-gray-900 outline-none flex-1 placeholder-gray-400"
          />
          {searchQuery && (
            <button type="button" onClick={() => { setSearchQuery(""); setCity(""); setSuburb(""); }}>
              <X className="w-4 h-4 text-gray-400" />
            </button>
          )}
        </form>

        {/* Price filter */}
        <div className="relative">
          <button
            onClick={() => setFiltersOpen(filtersOpen === true ? false : true)}
            className="flex items-center gap-2 border border-gray-200 rounded-lg px-4 py-2 text-sm font-medium text-gray-700 bg-white hover:bg-gray-50"
          >
            {activePriceLabel}
            <ChevronDown className="w-4 h-4" />
          </button>
          {filtersOpen && (
            <div className="absolute top-full mt-2 left-0 bg-white border border-gray-200 rounded-xl shadow-xl p-4 z-30 w-64">
              <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-3">Price Range</p>
              <div className="flex flex-col gap-1.5">
                {priceRanges.map((r) => (
                  <button
                    key={r.label}
                    onClick={() => {
                      setMinPrice(r.min);
                      setMaxPrice(r.max);
                      setFiltersOpen(false);
                    }}
                    className={`text-left px-3 py-2 rounded-lg text-sm transition-colors ${
                      r.min === minPrice && r.max === maxPrice
                        ? "bg-blue-50 text-blue-700 font-semibold"
                        : "text-gray-700 hover:bg-gray-50"
                    }`}
                  >
                    {r.label}
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Bedrooms filter */}
        <div className="flex items-center gap-1 border border-gray-200 rounded-lg px-3 py-2 bg-white">
          <span className="text-sm text-gray-500 mr-1">Beds</span>
          {BED_OPTIONS.map((n) => (
            <button
              key={n}
              onClick={() => setBedrooms(bedrooms === n ? undefined : n)}
              className={`w-7 h-7 rounded text-sm font-medium transition-colors ${
                bedrooms === n ? "bg-blue-600 text-white" : "text-gray-600 hover:bg-gray-100"
              }`}
            >
              {n}+
            </button>
          ))}
        </div>

        {/* More filters */}
        <button
          onClick={() => setDiasporaFriendly(!diasporaFriendly)}
          className={`flex items-center gap-2 border rounded-lg px-4 py-2 text-sm font-medium transition-colors ${
            diasporaFriendly ? "border-blue-500 bg-blue-50 text-blue-700" : "border-gray-200 text-gray-600 hover:bg-gray-50"
          }`}
        >
          <SlidersHorizontal className="w-4 h-4" />
          Diaspora
        </button>

        {/* Result count */}
        <span className="text-sm text-gray-500 ml-auto hidden sm:block">
          {loading ? "Loading..." : `${results.length} result${results.length !== 1 ? "s" : ""}`}
        </span>

        {/* Mobile: list/map toggle */}
        <div className="flex rounded-lg border border-gray-200 overflow-hidden sm:hidden">
          <button
            onClick={() => setMobileView("list")}
            className={`px-3 py-2 ${mobileView === "list" ? "bg-blue-600 text-white" : "text-gray-600"}`}
          >
            <List className="w-4 h-4" />
          </button>
          <button
            onClick={() => setMobileView("map")}
            className={`px-3 py-2 ${mobileView === "map" ? "bg-blue-600 text-white" : "text-gray-600"}`}
          >
            <Map className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Split layout */}
      <div className="flex flex-1 overflow-hidden">
        {/* Left: listings */}
        <div
          className={`${
            mobileView === "map" ? "hidden" : "flex"
          } sm:flex flex-col w-full sm:w-[45%] lg:w-[40%] overflow-y-auto`}
        >
          {/* City quick filters */}
          <div className="px-4 py-3 flex gap-2 overflow-x-auto border-b border-gray-100 shrink-0">
            {CITIES.map((c) => (
              <button
                key={c}
                onClick={() => setCity(city === c ? "" : c)}
                className={`shrink-0 text-xs font-medium px-3 py-1.5 rounded-full border transition-colors ${
                  city === c ? "bg-blue-600 text-white border-blue-600" : "border-gray-200 text-gray-600 hover:border-gray-400"
                }`}
              >
                {c}
              </button>
            ))}
          </div>

          <div className="p-4 flex-1">
            {error && (
              <div className="bg-red-50 border border-red-200 rounded-xl p-4 mb-4 text-sm text-red-700">
                {error}
              </div>
            )}

            {loading ? (
              <div className="grid grid-cols-1 gap-4">
                {Array.from({ length: 4 }).map((_, i) => (
                  <div key={i} className="h-60 bg-gray-100 rounded-xl animate-pulse" />
                ))}
              </div>
            ) : results.length === 0 ? (
              <div className="text-center py-16 text-gray-500">
                <Map className="w-12 h-12 mx-auto mb-4 opacity-30" />
                <p className="text-lg font-medium">No properties found</p>
                <p className="text-sm mt-1">Try adjusting your filters</p>
              </div>
            ) : (
              <div className="grid grid-cols-1 gap-4">
                {results.map((property) => (
                  <PropertyCard
                    key={property.id}
                    property={property}
                    highlighted={highlightedId === property.id}
                    onMouseEnter={() => setHighlightedId(property.id)}
                    onMouseLeave={() => setHighlightedId(null)}
                  />
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Right: map */}
        <div
          className={`${
            mobileView === "list" ? "hidden" : "flex"
          } sm:flex flex-1 relative sticky top-0`}
          style={{ height: "calc(100vh - 64px - 57px)" }}
        >
          <PropertyMap
            properties={results}
            highlightedId={highlightedId}
            onMarkerClick={(id) => {
              setHighlightedId(id);
              // On mobile, switch to list view and scroll to card
              setMobileView("list");
            }}
          />
        </div>
      </div>

      {/* Click outside to close filters */}
      {filtersOpen && (
        <div className="fixed inset-0 z-20" onClick={() => setFiltersOpen(false)} />
      )}
    </div>
  );
}

export default function PropertiesPage() {
  return (
    <Suspense fallback={<div className="flex-1 flex items-center justify-center text-gray-400">Loading...</div>}>
      <PropertiesContent />
    </Suspense>
  );
}
