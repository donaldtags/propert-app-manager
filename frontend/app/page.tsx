"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Search, Shield, Globe, Zap, TrendingUp, CheckCircle } from "lucide-react";
import { properties } from "@/lib/api";
import type { Property } from "@/lib/types";
import PropertyCard from "@/components/PropertyCard";

const HERO_CITIES = ["Harare", "Bulawayo", "Mutare", "Gweru"];

export default function HomePage() {
  const router = useRouter();
  const [activeTab, setActiveTab] = useState<"RENT" | "SALE">("RENT");
  const [query, setQuery] = useState("");
  const [featured, setFeatured] = useState<Property[]>([]);
  const [loadingFeatured, setLoadingFeatured] = useState(true);

  useEffect(() => {
    properties
      .list({ listingType: "RENT" })
      .then((data) => setFeatured(data.slice(0, 6)))
      .catch(() => setFeatured([]))
      .finally(() => setLoadingFeatured(false));
  }, []);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    const params = new URLSearchParams();
    params.set("listingType", activeTab);
    if (query.trim()) params.set("city", query.trim());
    router.push(`/properties?${params.toString()}`);
  };

  return (
    <div className="flex flex-col">
      {/* Hero */}
      <section
        className="relative min-h-[560px] flex items-center justify-center bg-cover bg-center"
        style={{
          backgroundImage:
            "linear-gradient(to bottom, rgba(0,0,0,0.45) 0%, rgba(0,0,0,0.6) 100%), url('https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?w=1600&q=80')",
        }}
      >
        <div className="relative z-10 w-full max-w-3xl px-4 text-center">
          <h1 className="text-4xl sm:text-5xl font-bold text-white mb-3 leading-tight">
            Find Your Home Across Africa
          </h1>
          <p className="text-white/80 text-lg mb-8">
            Verified listings. Escrow-protected deposits. Digital leases.
          </p>

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
                      ? "text-blue-600 border-b-2 border-blue-600"
                      : "text-gray-500 hover:text-gray-700"
                  }`}
                >
                  {tab === "RENT" ? "For Rent" : "For Sale"}
                </button>
              ))}
            </div>

            {/* Search input */}
            <form onSubmit={handleSearch} className="flex items-center p-4 gap-3">
              <Search className="w-5 h-5 text-gray-400 shrink-0" />
              <input
                type="text"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Enter city, suburb, or address..."
                className="flex-1 text-gray-900 text-base outline-none placeholder-gray-400"
              />
              <button
                type="submit"
                className="bg-blue-600 hover:bg-blue-700 text-white font-semibold px-6 py-2.5 rounded-xl transition-colors"
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
        </div>
      </section>

      {/* Trust features */}
      <section className="bg-gray-50 border-b border-gray-200 py-10 px-4">
        <div className="max-w-5xl mx-auto grid grid-cols-2 sm:grid-cols-4 gap-6 text-center">
          {[
            { icon: CheckCircle, color: "text-green-600", label: "Verified Listings", desc: "Every property vetted by certified agents" },
            { icon: Shield, color: "text-blue-600", label: "Escrow Protected", desc: "Deposits held safely until keys are received" },
            { icon: Globe, color: "text-amber-600", label: "Diaspora Friendly", desc: "Manage property remotely from abroad" },
            { icon: Zap, color: "text-purple-600", label: "Digital Leases", desc: "Sign legally-binding leases online" },
          ].map(({ icon: Icon, color, label, desc }) => (
            <div key={label} className="flex flex-col items-center gap-2">
              <Icon className={`w-8 h-8 ${color}`} />
              <p className="font-semibold text-gray-900 text-sm">{label}</p>
              <p className="text-gray-500 text-xs leading-snug">{desc}</p>
            </div>
          ))}
        </div>
      </section>

      {/* Featured listings */}
      <section className="py-12 px-4 max-w-screen-xl mx-auto w-full">
        <div className="flex items-center justify-between mb-6">
          <div>
            <h2 className="text-2xl font-bold text-gray-900">Featured Rentals</h2>
            <p className="text-gray-500 text-sm mt-1">Verified properties ready to move in</p>
          </div>
          <Link
            href="/properties?listingType=RENT"
            className="text-blue-600 text-sm font-semibold hover:underline flex items-center gap-1"
          >
            View all <span aria-hidden>→</span>
          </Link>
        </div>

        {loadingFeatured ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
            {Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className="h-72 bg-gray-100 rounded-xl animate-pulse" />
            ))}
          </div>
        ) : featured.length === 0 ? (
          <div className="text-center py-16 text-gray-500">
            <p className="text-lg font-medium">No listings yet</p>
            <p className="text-sm mt-1">Be the first to list a property</p>
            <Link
              href="/register"
              className="mt-4 inline-block bg-blue-600 text-white px-6 py-2.5 rounded-xl font-semibold hover:bg-blue-700 transition-colors"
            >
              Get Started
            </Link>
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
            {featured.map((property) => (
              <PropertyCard key={property.id} property={property} />
            ))}
          </div>
        )}
      </section>

      {/* Invest CTA */}
      <section className="bg-blue-600 py-16 px-4 text-center text-white">
        <TrendingUp className="w-12 h-12 mx-auto mb-4 opacity-80" />
        <h2 className="text-3xl font-bold mb-3">Invest in African Real Estate</h2>
        <p className="text-blue-100 max-w-xl mx-auto mb-6">
          Buy REIT units starting from $10. Earn projected yields and grow your
          portfolio across Zimbabwe and beyond.
        </p>
        <Link
          href="/investments"
          className="bg-white text-blue-600 font-bold px-8 py-3 rounded-xl hover:bg-blue-50 transition-colors inline-block"
        >
          Explore REITs
        </Link>
      </section>

      {/* Footer */}
      <footer className="bg-gray-900 text-gray-400 text-sm py-8 px-4">
        <div className="max-w-screen-xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-4">
          <p>© {new Date().getFullYear()} PrimeNest. All rights reserved.</p>
          <div className="flex gap-6">
            <Link href="/properties" className="hover:text-white">Browse</Link>
            <Link href="/investments" className="hover:text-white">Invest</Link>
            <Link href="/ai" className="hover:text-white">AI Search</Link>
            <Link href="/register" className="hover:text-white">Sign Up</Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
