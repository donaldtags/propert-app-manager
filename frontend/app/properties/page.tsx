"use client";

import { useState, useEffect, useCallback, Suspense, type FormEvent } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import dynamic from "next/dynamic";
import { Search, SlidersHorizontal, X, Map, List, ChevronDown, ArrowUpDown } from "lucide-react";
import { properties as propertiesApi } from "@/lib/api";
import type { Property, ListingType } from "@/lib/types";
import PropertyCard from "@/components/PropertyCard";
import CompareBar from "@/components/CompareBar";
import { ZIMBABWE_CITIES, ZIMBABWE_SUBURBS, ZIMBABWE_SUBURB_ENTRIES, resolveLocationQuery } from "@/lib/zimbabweLocations";

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

const SORT_OPTIONS = [
  { value: "newest", label: "Newest" },
  { value: "price_asc", label: "Price: Low to High" },
  { value: "price_desc", label: "Price: High to Low" },
  { value: "beds_desc", label: "Most Bedrooms" },
] as const;
type SortOption = (typeof SORT_OPTIONS)[number]["value"];
type WaterSourceFilter = "" | "MUNICIPAL" | "BOREHOLE" | "WELL" | "TANKER" | "OTHER";

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
  const [minPrice, setMinPrice] = useState<number | undefined>(
    searchParams.get("minPrice") ? Number(searchParams.get("minPrice")) : undefined
  );
  const [maxPrice, setMaxPrice] = useState<number | undefined>(
    searchParams.get("maxPrice") ? Number(searchParams.get("maxPrice")) : undefined
  );
  const [bathrooms, setBathrooms] = useState<number | undefined>(
    searchParams.get("bathrooms") ? Number(searchParams.get("bathrooms")) : undefined
  );
  const [diasporaFriendly, setDiasporaFriendly] = useState(searchParams.get("diasporaFriendly") === "true");
  const [solarInstalled, setSolarInstalled] = useState(searchParams.get("solarInstalled") === "true");
  const [backupPower, setBackupPower] = useState(searchParams.get("backupPower") === "true");
  const [furnished, setFurnished] = useState(searchParams.get("furnished") === "true");
  const [internetAvailable, setInternetAvailable] = useState(searchParams.get("internetAvailable") === "true");
  const [securityFeatures, setSecurityFeatures] = useState(searchParams.get("securityFeatures") === "true");
  const [parkingAvailable, setParkingAvailable] = useState(searchParams.get("parkingAvailable") === "true");
  const [petsAllowed, setPetsAllowed] = useState(searchParams.get("petsAllowed") === "true");
  const [verifiedOnly, setVerifiedOnly] = useState(searchParams.get("verifiedOnly") === "true");
  const [escrowAvailable, setEscrowAvailable] = useState(searchParams.get("escrowAvailable") === "true");

  const [results, setResults] = useState<Property[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [page, setPage] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [highlightedId, setHighlightedId] = useState<number | null>(null);
  const [mobileView, setMobileView] = useState<"list" | "map">("list");
  const [sortBy, setSortBy] = useState<SortOption>(
    (SORT_OPTIONS.find((o) => o.value === searchParams.get("sort"))?.value as SortOption) ?? "newest"
  );
  const [sortMenuOpen, setSortMenuOpen] = useState(false);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState(searchParams.get("city") ?? "");

  // Draft values edited inside the filter panel; only applied to the real
  // filters (and thus the search) when the user clicks "Apply filters".
  const [draftMinPrice, setDraftMinPrice] = useState<string>("");
  const [draftMaxPrice, setDraftMaxPrice] = useState<string>("");
  const [draftBedrooms, setDraftBedrooms] = useState<string>("");
  const [draftBathrooms, setDraftBathrooms] = useState<string>("");
  const [draftDiasporaFriendly, setDraftDiasporaFriendly] = useState(false);
  const [draftSolarInstalled, setDraftSolarInstalled] = useState(false);
  const [draftBackupPower, setDraftBackupPower] = useState(false);
  const [draftFurnished, setDraftFurnished] = useState(false);
  const [draftInternetAvailable, setDraftInternetAvailable] = useState(false);
  const [draftSecurityFeatures, setDraftSecurityFeatures] = useState(false);
  const [draftParkingAvailable, setDraftParkingAvailable] = useState(false);
  const [draftPetsAllowed, setDraftPetsAllowed] = useState(false);
  const [draftVerifiedOnly, setDraftVerifiedOnly] = useState(false);
  const [draftEscrowAvailable, setDraftEscrowAvailable] = useState(false);
  const [draftWaterSource, setDraftWaterSource] = useState<WaterSourceFilter>("");
  const [waterSource, setWaterSource] = useState<WaterSourceFilter>(
    (searchParams.get("waterSource") as WaterSourceFilter) ?? ""
  );

  const openFilters = () => {
    setDraftMinPrice(minPrice != null ? String(minPrice) : "");
    setDraftMaxPrice(maxPrice != null ? String(maxPrice) : "");
    setDraftBedrooms(bedrooms != null ? String(bedrooms) : "");
    setDraftBathrooms(bathrooms != null ? String(bathrooms) : "");
    setDraftDiasporaFriendly(diasporaFriendly);
    setDraftSolarInstalled(solarInstalled);
    setDraftBackupPower(backupPower);
    setDraftFurnished(furnished);
    setDraftInternetAvailable(internetAvailable);
    setDraftSecurityFeatures(securityFeatures);
    setDraftParkingAvailable(parkingAvailable);
    setDraftPetsAllowed(petsAllowed);
    setDraftVerifiedOnly(verifiedOnly);
    setDraftEscrowAvailable(escrowAvailable);
    setDraftWaterSource(waterSource);
    setFiltersOpen(true);
  };

  const applyFilters = () => {
    setMinPrice(draftMinPrice ? Number(draftMinPrice) : undefined);
    setMaxPrice(draftMaxPrice ? Number(draftMaxPrice) : undefined);
    setBedrooms(draftBedrooms ? Number(draftBedrooms) : undefined);
    setBathrooms(draftBathrooms ? Number(draftBathrooms) : undefined);
    setDiasporaFriendly(draftDiasporaFriendly);
    setSolarInstalled(draftSolarInstalled);
    setBackupPower(draftBackupPower);
    setFurnished(draftFurnished);
    setInternetAvailable(draftInternetAvailable);
    setSecurityFeatures(draftSecurityFeatures);
    setParkingAvailable(draftParkingAvailable);
    setPetsAllowed(draftPetsAllowed);
    setVerifiedOnly(draftVerifiedOnly);
    setEscrowAvailable(draftEscrowAvailable);
    setWaterSource(draftWaterSource);
    setFiltersOpen(false);
  };

  const clearFilters = () => {
    setDraftMinPrice("");
    setDraftMaxPrice("");
    setDraftBedrooms("");
    setDraftBathrooms("");
    setDraftDiasporaFriendly(false);
    setDraftSolarInstalled(false);
    setDraftBackupPower(false);
    setDraftFurnished(false);
    setDraftInternetAvailable(false);
    setDraftSecurityFeatures(false);
    setDraftParkingAvailable(false);
    setDraftPetsAllowed(false);
    setDraftVerifiedOnly(false);
    setDraftEscrowAvailable(false);
    setDraftWaterSource("");
    setMinPrice(undefined);
    setMaxPrice(undefined);
    setBedrooms(undefined);
    setBathrooms(undefined);
    setDiasporaFriendly(false);
    setSolarInstalled(false);
    setBackupPower(false);
    setFurnished(false);
    setInternetAvailable(false);
    setSecurityFeatures(false);
    setParkingAvailable(false);
    setPetsAllowed(false);
    setVerifiedOnly(false);
    setEscrowAvailable(false);
    setWaterSource("");
    setFiltersOpen(false);
  };

  const activeFilterCount = [
    minPrice,
    maxPrice,
    bedrooms,
    bathrooms,
    diasporaFriendly || undefined,
    solarInstalled || undefined,
    backupPower || undefined,
    furnished || undefined,
    internetAvailable || undefined,
    securityFeatures || undefined,
    parkingAvailable || undefined,
    petsAllowed || undefined,
    verifiedOnly || undefined,
    escrowAvailable || undefined,
    waterSource || undefined,
  ].filter((v) => v !== undefined).length;

  const PAGE_SIZE = 20;

  const fetchPage = useCallback(async (pageToLoad: number, append: boolean) => {
    if (append) setLoadingMore(true);
    else setLoading(true);
    setError("");
    try {
      const { items, totalCount: count } = await propertiesApi.searchPaged({
        listingType,
        city: city || undefined,
        suburb: suburb || undefined,
        bedrooms,
        bathrooms,
        minPrice,
        maxPrice,
        diasporaFriendly: diasporaFriendly || undefined,
        solarInstalled: solarInstalled || undefined,
        backupPower: backupPower || undefined,
        waterSource: waterSource || undefined,
        furnished: furnished || undefined,
        internetAvailable: internetAvailable || undefined,
        securityFeatures: securityFeatures || undefined,
        parkingAvailable: parkingAvailable || undefined,
        petsAllowed: petsAllowed || undefined,
        verifiedOnly: verifiedOnly || undefined,
        escrowAvailable: escrowAvailable || undefined,
        page: pageToLoad,
        size: PAGE_SIZE,
      });
      setResults((prev) => (append ? [...prev, ...items] : items));
      setTotalCount(count);
      setPage(pageToLoad);
    } catch {
      setError("Failed to load properties. Is the backend running?");
    } finally {
      setLoading(false);
      setLoadingMore(false);
    }
  }, [
    listingType, city, suburb, bedrooms, bathrooms, minPrice, maxPrice, diasporaFriendly,
    solarInstalled, backupPower, waterSource, furnished, internetAvailable, securityFeatures,
    parkingAvailable, petsAllowed, verifiedOnly, escrowAvailable,
  ]);

  // Any filter change starts a fresh search from page 0.
  useEffect(() => {
    fetchPage(0, false);
  }, [fetchPage]);

  const handleLoadMore = () => fetchPage(page + 1, true);

  // Keep the URL in sync with the active search so results are shareable and the
  // browser back/forward buttons move between prior searches.
  useEffect(() => {
    const q = new URLSearchParams();
    q.set("listingType", listingType);
    if (city) q.set("city", city);
    if (suburb) q.set("suburb", suburb);
    if (bedrooms != null) q.set("bedrooms", String(bedrooms));
    if (bathrooms != null) q.set("bathrooms", String(bathrooms));
    if (minPrice != null) q.set("minPrice", String(minPrice));
    if (maxPrice != null) q.set("maxPrice", String(maxPrice));
    if (diasporaFriendly) q.set("diasporaFriendly", "true");
    if (solarInstalled) q.set("solarInstalled", "true");
    if (backupPower) q.set("backupPower", "true");
    if (waterSource) q.set("waterSource", waterSource);
    if (furnished) q.set("furnished", "true");
    if (internetAvailable) q.set("internetAvailable", "true");
    if (securityFeatures) q.set("securityFeatures", "true");
    if (parkingAvailable) q.set("parkingAvailable", "true");
    if (petsAllowed) q.set("petsAllowed", "true");
    if (verifiedOnly) q.set("verifiedOnly", "true");
    if (escrowAvailable) q.set("escrowAvailable", "true");
    if (sortBy !== "newest") q.set("sort", sortBy);
    router.replace(`/properties?${q.toString()}`, { scroll: false });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    listingType, city, suburb, bedrooms, bathrooms, minPrice, maxPrice, diasporaFriendly,
    solarInstalled, backupPower, waterSource, furnished, internetAvailable, securityFeatures,
    parkingAvailable, petsAllowed, verifiedOnly, escrowAvailable, sortBy,
  ]);

  const handleSearch = (e: FormEvent) => {
    e.preventDefault();
    const parts = searchQuery.split(",").map((s) => s.trim());
    if (parts.length >= 2) {
      setSuburb(parts[0]);
      setCity(parts[1]);
      return;
    }
    const resolved = resolveLocationQuery(parts[0]);
    if (resolved) {
      setCity(resolved.city);
      setSuburb(resolved.suburb);
      return;
    }
    // Unrecognized text: city filtering is an exact match on the backend, so a
    // free-text guess is far more likely to find something as a suburb filter
    // (partial match) than as a city filter.
    setCity("");
    setSuburb(parts[0]);
  };

  const priceRanges = listingType === "RENT" ? PRICE_RANGES_RENT : PRICE_RANGES_SALE;
  const suburbOptions = city ? ZIMBABWE_SUBURBS[city] ?? [] : [];

  const sortedResults = [...results].sort((a, b) => {
    switch (sortBy) {
      case "price_asc":
        return a.price - b.price;
      case "price_desc":
        return b.price - a.price;
      case "beds_desc":
        return b.bedrooms - a.bedrooms;
      default:
        return b.id - a.id;
    }
  });

  const locationLabel = suburb ? `${suburb}, ${city}` : city || "Zimbabwe";
  const listingTypeLabel = listingType === "SHORT_STAY" ? "short stays" : listingType === "SALE" ? "homes for sale" : "rentals";

  const activeChips: { label: string; onRemove: () => void }[] = [
    ...(city ? [{ label: suburb ? `${suburb}, ${city}` : city, onRemove: () => { setCity(""); setSuburb(""); setSearchQuery(""); } }] : []),
    ...(minPrice != null || maxPrice != null
      ? [{
          label: `$${minPrice ?? 0}${maxPrice != null ? ` – $${maxPrice}` : "+"}`,
          onRemove: () => { setMinPrice(undefined); setMaxPrice(undefined); },
        }]
      : []),
    ...(bedrooms != null ? [{ label: `${bedrooms}+ beds`, onRemove: () => setBedrooms(undefined) }] : []),
    ...(bathrooms != null ? [{ label: `${bathrooms}+ baths`, onRemove: () => setBathrooms(undefined) }] : []),
    ...(diasporaFriendly ? [{ label: "Diaspora friendly", onRemove: () => setDiasporaFriendly(false) }] : []),
    ...(solarInstalled ? [{ label: "Solar", onRemove: () => setSolarInstalled(false) }] : []),
    ...(backupPower ? [{ label: "Backup power", onRemove: () => setBackupPower(false) }] : []),
    ...(furnished ? [{ label: "Furnished", onRemove: () => setFurnished(false) }] : []),
    ...(internetAvailable ? [{ label: "Internet / Fibre", onRemove: () => setInternetAvailable(false) }] : []),
    ...(securityFeatures ? [{ label: "Security", onRemove: () => setSecurityFeatures(false) }] : []),
    ...(parkingAvailable ? [{ label: "Parking", onRemove: () => setParkingAvailable(false) }] : []),
    ...(petsAllowed ? [{ label: "Pets allowed", onRemove: () => setPetsAllowed(false) }] : []),
    ...(verifiedOnly ? [{ label: "Verified only", onRemove: () => setVerifiedOnly(false) }] : []),
    ...(escrowAvailable ? [{ label: "Escrow available", onRemove: () => setEscrowAvailable(false) }] : []),
    ...(waterSource ? [{ label: waterSource.charAt(0) + waterSource.slice(1).toLowerCase(), onRemove: () => setWaterSource("") }] : []),
  ];

  return (
    <div className="flex flex-col flex-1" style={{ height: "calc(100vh - 80px)" }}>
      <CompareBar />

      {/* Page heading */}
      <div className="px-4 pt-4 pb-1 bg-white border-b border-gray-100">
        <h1 className="text-lg font-bold text-gray-900">
          {loading ? "Searching…" : `${totalCount} ${listingTypeLabel} in ${locationLabel}`}
        </h1>
      </div>

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
        <form onSubmit={handleSearch} className="flex items-center gap-2 min-w-48 flex-1 max-w-96">
          <div className="flex items-center gap-2 border border-gray-200 rounded-lg px-3 py-2 bg-white flex-1">
            <Search className="w-4 h-4 text-gray-400 shrink-0" />
            <input
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="City or suburb..."
              list="top-search-location-options"
              className="text-sm text-gray-900 outline-none flex-1 placeholder-gray-400"
            />
            <datalist id="top-search-location-options">
              {ZIMBABWE_CITIES.map((c) => (
                <option key={c} value={c} />
              ))}
              {ZIMBABWE_SUBURB_ENTRIES.map(({ suburb: s, city: c }) => (
                <option key={`${s}-${c}`} value={s}>{`${s}, ${c}`}</option>
              ))}
            </datalist>
            {searchQuery && (
              <button type="button" onClick={() => { setSearchQuery(""); setCity(""); setSuburb(""); }}>
                <X className="w-4 h-4 text-gray-400" />
              </button>
            )}
          </div>
          <button
            type="submit"
            className="shrink-0 bg-blue-600 hover:bg-blue-700 text-white text-sm font-semibold px-4 py-2 rounded-lg transition-colors"
          >
            Search
          </button>
        </form>

        {/* Filters */}
        <div className="relative">
          <button
            onClick={() => (filtersOpen ? setFiltersOpen(false) : openFilters())}
            className={`flex items-center gap-2 border rounded-lg px-4 py-2 text-sm font-medium transition-colors ${
              activeFilterCount > 0
                ? "border-blue-500 bg-blue-50 text-blue-700"
                : "border-gray-200 text-gray-700 bg-white hover:bg-gray-50"
            }`}
          >
            <SlidersHorizontal className="w-4 h-4" />
            Filters
            {activeFilterCount > 0 && (
              <span className="bg-blue-600 text-white text-xs w-5 h-5 rounded-full flex items-center justify-center">
                {activeFilterCount}
              </span>
            )}
            <ChevronDown className="w-4 h-4" />
          </button>
          {filtersOpen && (
            <div className="absolute top-full mt-2 left-0 bg-white border border-gray-200 rounded-xl shadow-xl p-4 z-30 w-80">
              {/* Price */}
              <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">
                Price range ({listingType === "RENT" ? "per month" : "total"})
              </p>
              <div className="flex flex-wrap gap-1.5 mb-2">
                {priceRanges.map((r) => (
                  <button
                    key={r.label}
                    type="button"
                    onClick={() => {
                      setDraftMinPrice(r.min != null ? String(r.min) : "");
                      setDraftMaxPrice(r.max != null ? String(r.max) : "");
                    }}
                    className={`px-2.5 py-1 rounded-full text-xs font-medium border transition-colors ${
                      Number(draftMinPrice) === (r.min ?? 0) &&
                      (draftMaxPrice ? Number(draftMaxPrice) : undefined) === r.max
                        ? "bg-blue-600 text-white border-blue-600"
                        : "border-gray-200 text-gray-600 hover:border-gray-400"
                    }`}
                  >
                    {r.label}
                  </button>
                ))}
              </div>
              <div className="flex items-center gap-2 mb-4">
                <input
                  type="number"
                  min={0}
                  inputMode="numeric"
                  placeholder="Min $"
                  value={draftMinPrice}
                  onChange={(e) => setDraftMinPrice(e.target.value)}
                  className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm text-gray-900 outline-none focus:border-blue-400"
                />
                <span className="text-gray-400 shrink-0">–</span>
                <input
                  type="number"
                  min={0}
                  inputMode="numeric"
                  placeholder="Max $"
                  value={draftMaxPrice}
                  onChange={(e) => setDraftMaxPrice(e.target.value)}
                  className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm text-gray-900 outline-none focus:border-blue-400"
                />
              </div>

              {/* Bedrooms */}
              <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">Bedrooms (min)</p>
              <div className="flex items-center gap-2 mb-4">
                <div className="flex gap-1">
                  {BED_OPTIONS.map((n) => (
                    <button
                      key={n}
                      type="button"
                      onClick={() => setDraftBedrooms(String(n))}
                      className={`w-8 h-8 rounded text-sm font-medium transition-colors ${
                        Number(draftBedrooms) === n ? "bg-blue-600 text-white" : "text-gray-600 hover:bg-gray-100 border border-gray-200"
                      }`}
                    >
                      {n}+
                    </button>
                  ))}
                </div>
                <input
                  type="number"
                  min={0}
                  inputMode="numeric"
                  placeholder="Any"
                  value={draftBedrooms}
                  onChange={(e) => setDraftBedrooms(e.target.value)}
                  className="w-16 border border-gray-200 rounded-lg px-2 py-1.5 text-sm text-gray-900 outline-none focus:border-blue-400"
                />
              </div>

              {/* Bathrooms */}
              <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">Bathrooms (min)</p>
              <input
                type="number"
                min={0}
                inputMode="numeric"
                placeholder="Any"
                value={draftBathrooms}
                onChange={(e) => setDraftBathrooms(e.target.value)}
                className="w-20 border border-gray-200 rounded-lg px-2 py-1.5 text-sm text-gray-900 outline-none focus:border-blue-400 mb-4"
              />

              {/* Amenities */}
              <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">Amenities</p>
              <div className="grid grid-cols-2 gap-x-3 gap-y-2 mb-4">
                {([
                  ["Diaspora friendly", draftDiasporaFriendly, setDraftDiasporaFriendly],
                  ["Solar", draftSolarInstalled, setDraftSolarInstalled],
                  ["Backup power", draftBackupPower, setDraftBackupPower],
                  ["Furnished", draftFurnished, setDraftFurnished],
                  ["Internet / Fibre", draftInternetAvailable, setDraftInternetAvailable],
                  ["Security", draftSecurityFeatures, setDraftSecurityFeatures],
                  ["Parking", draftParkingAvailable, setDraftParkingAvailable],
                  ["Pets allowed", draftPetsAllowed, setDraftPetsAllowed],
                  ["Verified only", draftVerifiedOnly, setDraftVerifiedOnly],
                  ["Escrow available", draftEscrowAvailable, setDraftEscrowAvailable],
                ] as [string, boolean, (v: boolean) => void][]).map(([label, checked, setChecked]) => (
                  <label key={label} className="flex items-center gap-2 cursor-pointer select-none">
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={(e) => setChecked(e.target.checked)}
                      className="w-4 h-4 rounded border-gray-300 text-blue-600 focus:ring-blue-400"
                    />
                    <span className="text-sm text-gray-700">{label}</span>
                  </label>
                ))}
              </div>

              {/* Water source */}
              <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">Water Source</p>
              <select
                value={draftWaterSource}
                onChange={(e) => setDraftWaterSource(e.target.value as typeof draftWaterSource)}
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm text-gray-900 outline-none focus:border-blue-400 bg-white mb-4"
              >
                <option value="">Any</option>
                <option value="MUNICIPAL">Municipal</option>
                <option value="BOREHOLE">Borehole</option>
                <option value="WELL">Well</option>
                <option value="TANKER">Tanker delivery</option>
                <option value="OTHER">Other</option>
              </select>

              <div className="flex items-center justify-between gap-2 pt-2 border-t border-gray-100">
                <button
                  type="button"
                  onClick={clearFilters}
                  className="text-sm font-medium text-gray-500 hover:text-gray-700"
                >
                  Clear all
                </button>
                <button
                  type="button"
                  onClick={applyFilters}
                  className="bg-blue-600 hover:bg-blue-700 text-white text-sm font-semibold px-4 py-2 rounded-lg transition-colors"
                >
                  Apply filters
                </button>
              </div>
            </div>
          )}
        </div>

        {/* Result count */}
        <span className="text-sm text-gray-500 ml-auto hidden sm:block">
          {loading ? "Loading..." : `${totalCount} result${totalCount !== 1 ? "s" : ""}`}
        </span>

        {/* Sort */}
        <div className="relative hidden sm:block">
          <button
            onClick={() => setSortMenuOpen((o) => !o)}
            className="flex items-center gap-2 border border-gray-200 rounded-lg px-3 py-2 text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 transition-colors"
          >
            <ArrowUpDown className="w-3.5 h-3.5" />
            {SORT_OPTIONS.find((o) => o.value === sortBy)?.label}
            <ChevronDown className="w-4 h-4" />
          </button>
          {sortMenuOpen && (
            <div className="absolute top-full right-0 mt-2 bg-white border border-gray-200 rounded-xl shadow-xl py-1 z-30 w-48">
              {SORT_OPTIONS.map((opt) => (
                <button
                  key={opt.value}
                  onClick={() => { setSortBy(opt.value); setSortMenuOpen(false); }}
                  className={`w-full text-left px-4 py-2 text-sm transition-colors ${
                    sortBy === opt.value ? "text-blue-600 font-semibold bg-blue-50" : "text-gray-700 hover:bg-gray-50"
                  }`}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          )}
        </div>

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

      {/* Active filter chips */}
      {activeChips.length > 0 && (
        <div className="px-4 py-2 flex flex-wrap items-center gap-2 border-b border-gray-100 bg-gray-50">
          {activeChips.map((chip) => (
            <span
              key={chip.label}
              className="flex items-center gap-1.5 bg-white border border-gray-200 text-gray-700 text-xs font-medium px-3 py-1.5 rounded-full"
            >
              {chip.label}
              <button onClick={chip.onRemove} className="text-gray-400 hover:text-gray-600">
                <X className="w-3 h-3" />
              </button>
            </span>
          ))}
          <button onClick={clearFilters} className="text-xs font-medium text-blue-600 hover:underline ml-1">
            Clear all
          </button>
        </div>
      )}

      {/* Split layout */}
      <div className="flex flex-1 overflow-hidden">
        {/* Left: listings */}
        <div
          className={`${
            mobileView === "map" ? "hidden" : "flex"
          } sm:flex flex-col w-full sm:w-[62%] lg:w-[65%] overflow-y-auto`}
        >
          {/* City + suburb quick filters */}
          <div className="px-4 py-3 flex items-center gap-2 overflow-x-auto border-b border-gray-100 shrink-0">
            {CITIES.map((c) => (
              <button
                key={c}
                onClick={() => { setCity(city === c ? "" : c); setSuburb(""); }}
                className={`shrink-0 text-xs font-medium px-3 py-1.5 rounded-full border transition-colors ${
                  city === c ? "bg-blue-600 text-white border-blue-600" : "border-gray-200 text-gray-600 hover:border-gray-400"
                }`}
              >
                {c}
              </button>
            ))}
            {city && (
              <>
                <input
                  type="text"
                  list="browse-suburb-options"
                  value={suburb}
                  onChange={(e) => setSuburb(e.target.value)}
                  placeholder={`All ${city} suburbs`}
                  className="shrink-0 w-40 text-xs font-medium border border-gray-200 rounded-full px-3 py-1.5 bg-white text-gray-600 outline-none focus:border-blue-400 placeholder-gray-500"
                />
                <datalist id="browse-suburb-options">
                  {suburbOptions.map((s) => (
                    <option key={s} value={s} />
                  ))}
                </datalist>
              </>
            )}
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
            ) : sortedResults.length === 0 ? (
              <div className="text-center py-16 text-gray-500">
                <Map className="w-12 h-12 mx-auto mb-4 opacity-30" />
                <p className="text-lg font-medium">No properties found</p>
                <p className="text-sm mt-1">Try adjusting your filters</p>
                {activeChips.length > 0 && (
                  <button onClick={clearFilters} className="text-sm text-blue-600 hover:underline mt-3 inline-block">
                    Clear all filters
                  </button>
                )}
              </div>
            ) : (
              <>
                <div className="grid grid-cols-1 gap-4">
                  {sortedResults.map((property) => (
                    <PropertyCard
                      key={property.id}
                      property={property}
                      highlighted={highlightedId === property.id}
                      onMouseEnter={() => setHighlightedId(property.id)}
                      onMouseLeave={() => setHighlightedId(null)}
                    />
                  ))}
                </div>
                {results.length < totalCount && (
                  <div className="flex justify-center mt-6">
                    <button
                      onClick={handleLoadMore}
                      disabled={loadingMore}
                      className="border border-gray-200 hover:border-gray-300 disabled:opacity-60 text-gray-700 font-semibold text-sm px-6 py-2.5 rounded-xl transition-colors"
                    >
                      {loadingMore ? "Loading…" : `Load more (${results.length} of ${totalCount})`}
                    </button>
                  </div>
                )}
              </>
            )}
          </div>
        </div>

        {/* Right: map */}
        <div
          className={`${
            mobileView === "list" ? "hidden" : "flex"
          } sm:flex flex-1 relative sticky top-0`}
          style={{ height: "calc(100vh - 80px - 57px)" }}
        >
          <PropertyMap
            properties={sortedResults}
            highlightedId={highlightedId}
            onMarkerClick={(id) => {
              setHighlightedId(id);
              // On mobile, switch to list view and scroll to card
              setMobileView("list");
            }}
          />
        </div>
      </div>

      {/* Click outside to close filters/sort */}
      {(filtersOpen || sortMenuOpen) && (
        <div className="fixed inset-0 z-20" onClick={() => { setFiltersOpen(false); setSortMenuOpen(false); }} />
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
