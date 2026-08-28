"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Search } from "lucide-react";

const HERO_CITIES = ["Harare", "Bulawayo", "Mutare", "Gweru"];

export default function HeroSearch() {
  const router = useRouter();
  const [activeTab, setActiveTab] = useState<"RENT" | "SALE">("RENT");
  const [query, setQuery] = useState("");

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    const params = new URLSearchParams();
    params.set("listingType", activeTab);
    if (query.trim()) params.set("city", query.trim());
    router.push(`/properties?${params.toString()}`);
  };

  return (
    <>
      {/* Search card */}
      <div className="bg-white rounded-2xl shadow-2xl overflow-hidden">
        {/* Tabs */}
        <div className="flex border-b border-gray-200">
          {(["RENT", "SALE"] as const).map((tab) => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={`flex-1 py-3.5 text-sm font-semibold transition-colors ${
                activeTab === tab
                  ? "text-forest-600 border-b-2 border-forest-600"
                  : "text-gray-500 hover:text-gray-700"
              }`}
            >
              {tab === "RENT" ? "For Rent" : "For Sale"}
            </button>
          ))}
        </div>

        {/* Search input */}
        <form onSubmit={handleSearch} className="flex items-center p-3 sm:p-4 gap-2 sm:gap-3">
          <Search className="w-5 h-5 text-gray-400 shrink-0" />
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Enter city, suburb, or address..."
            className="flex-1 min-w-0 text-gray-900 text-base outline-none placeholder-gray-400"
          />
          <button
            type="submit"
            className="bg-forest-600 hover:bg-forest-700 text-white font-semibold px-4 sm:px-6 py-2.5 rounded-xl transition-colors shrink-0"
          >
            Search
          </button>
        </form>
      </div>

      {/* Quick city links */}
      <div className="flex flex-wrap justify-center gap-2 mt-4">
        {HERO_CITIES.map((city) => (
          <button
            key={city}
            onClick={() => {
              const params = new URLSearchParams({ listingType: activeTab, city });
              router.push(`/properties?${params.toString()}`);
            }}
            className="text-white/80 hover:text-white text-sm underline underline-offset-2 transition-colors"
          >
            {city}
          </button>
        ))}
      </div>
    </>
  );
}
