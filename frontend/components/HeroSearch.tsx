"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Search } from "lucide-react";
import { useZimbabweLocations, locationFilters } from "@/lib/locations";
import { addDays, toISO } from "@/lib/stayDates";

const HERO_CITIES = ["Harare", "Bulawayo", "Mutare", "Gweru", "Victoria Falls"];

type Tab = "RENT" | "SALE" | "SHORT_STAY";

const TABS: { value: Tab; label: string; placeholder: string }[] = [
  { value: "RENT", label: "Rent", placeholder: "City or suburb, e.g. Borrowdale" },
  { value: "SALE", label: "Buy", placeholder: "City or suburb, e.g. Harare" },
  { value: "SHORT_STAY", label: "Stays", placeholder: "Where to? e.g. Victoria Falls" },
];

export default function HeroSearch() {
  const router = useRouter();
  const { resolve } = useZimbabweLocations();
  const [activeTab, setActiveTab] = useState<Tab>("RENT");
  const [query, setQuery] = useState("");
  const [checkIn, setCheckIn] = useState("");
  const [checkOut, setCheckOut] = useState("");
  const today = toISO(new Date());
  const isStay = activeTab === "SHORT_STAY";

  const go = (text: string) => {
    const params = new URLSearchParams({ listingType: activeTab });
    const { city, suburb } = locationFilters(text, resolve);
    if (city) params.set("city", city);
    if (suburb) params.set("suburb", suburb);
    if (isStay && checkIn && checkOut && checkOut > checkIn) {
      params.set("checkIn", checkIn);
      params.set("checkOut", checkOut);
    }
    router.push(`/properties?${params.toString()}`);
  };

  const tab = TABS.find((t) => t.value === activeTab)!;

  return (
    <>
      {/* Search card */}
      <div className="bg-white rounded-2xl shadow-2xl overflow-hidden text-left">
        {/* Tabs */}
        <div className="flex border-b border-gray-200" role="tablist" aria-label="What are you looking for?">
          {TABS.map((t) => (
            <button
              key={t.value}
              type="button"
              role="tab"
              aria-selected={activeTab === t.value}
              onClick={() => setActiveTab(t.value)}
              className={`flex-1 py-3.5 text-sm font-semibold transition-colors ${
                activeTab === t.value
                  ? "text-brand-600 border-b-2 border-brand-600"
                  : "text-gray-500 hover:text-gray-700"
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>

        <form
          onSubmit={(e) => {
            e.preventDefault();
            go(query);
          }}
          className="p-3 sm:p-4"
        >
          <div className="flex items-center gap-2 sm:gap-3">
            <Search className="w-5 h-5 text-gray-400 shrink-0" aria-hidden />
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder={tab.placeholder}
              aria-label="City or suburb"
              className="flex-1 min-w-0 text-gray-900 text-base outline-none placeholder-gray-400"
            />
            <button
              type="submit"
              className="bg-brand-600 hover:bg-brand-700 text-white font-semibold px-4 sm:px-6 py-2.5 rounded-xl transition-colors shrink-0"
            >
              Search
            </button>
          </div>

          {isStay && (
            <div className="grid grid-cols-2 gap-2 mt-3 pt-3 border-t border-gray-100">
              <label className="text-xs font-medium text-gray-500">
                Check-in
                <input
                  type="date"
                  min={today}
                  value={checkIn}
                  onChange={(e) => {
                    setCheckIn(e.target.value);
                    if (!checkOut || checkOut <= e.target.value) {
                      setCheckOut(addDays(e.target.value, 1));
                    }
                  }}
                  className="mt-1 block w-full border border-gray-200 rounded-lg px-2 py-2 text-sm text-gray-900"
                />
              </label>
              <label className="text-xs font-medium text-gray-500">
                Check-out
                <input
                  type="date"
                  min={checkIn || today}
                  value={checkOut}
                  onChange={(e) => setCheckOut(e.target.value)}
                  className="mt-1 block w-full border border-gray-200 rounded-lg px-2 py-2 text-sm text-gray-900"
                />
              </label>
            </div>
          )}
        </form>
      </div>

      {/* Quick city links */}
      <div className="flex flex-wrap justify-center items-center gap-x-3 gap-y-2 mt-4">
        <span className="text-white/70 text-sm">Popular:</span>
        {HERO_CITIES.map((city) => (
          <button
            key={city}
            type="button"
            onClick={() => go(city)}
            className="text-white/90 hover:text-white text-sm underline underline-offset-2 transition-colors"
          >
            {city}
          </button>
        ))}
      </div>
    </>
  );
}
