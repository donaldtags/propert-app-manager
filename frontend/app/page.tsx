import { Suspense } from "react";
import ReactDOM from "react-dom";
import Link from "next/link";
import { Shield, Globe, Zap, TrendingUp, CheckCircle } from "lucide-react";
import HeroSearch from "@/components/HeroSearch";
import FeaturedListings, { FeaturedListingsSkeleton } from "@/components/FeaturedListings";

const HERO_IMAGE = "https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?w=1400&q=60&auto=format&fit=crop";

// Serve the homepage as a static, CDN-cacheable page that revalidates in the
// background — instant loads without freezing featured listings at build time.
export const revalidate = 60;

export default function HomePage() {
  ReactDOM.preload(HERO_IMAGE, { as: "image", fetchPriority: "high" });
  ReactDOM.preconnect("https://images.unsplash.com");
  ReactDOM.preconnect(new URL(process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8081/api/v1").origin);

  return (
    <div className="flex flex-col">
      {/* Hero */}
      <section
        className="relative min-h-[560px] flex items-center justify-center bg-cover bg-center"
        style={{
          backgroundImage: `linear-gradient(to bottom, rgba(0,0,0,0.45) 0%, rgba(0,0,0,0.6) 100%), url('${HERO_IMAGE}')`,
        }}
      >
        <div className="relative z-10 w-full max-w-3xl px-4 text-center">
          <h1 className="text-3xl sm:text-5xl font-bold text-white mb-3 leading-tight">
            Find Your Home Across Africa
          </h1>
          <p className="text-white/80 text-base sm:text-lg mb-8">
            Verified listings. Escrow-protected deposits. Digital leases.
          </p>

          <HeroSearch />
        </div>
      </section>

      {/* Trust features */}
      <section className="bg-gray-50 border-b border-gray-200 py-10 px-4">
        <div className="max-w-5xl mx-auto grid grid-cols-2 sm:grid-cols-4 gap-6 text-center">
          {[
            { icon: CheckCircle, color: "text-forest-600", label: "Verified Listings", desc: "Every property vetted by certified agents" },
            { icon: Shield, color: "text-forest-600", label: "Escrow Protected", desc: "Deposits held safely until keys are received" },
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
        <div className="flex flex-wrap items-center justify-between gap-2 mb-6">
          <div>
            <h2 className="text-2xl font-bold text-gray-900">Featured Rentals</h2>
            <p className="text-gray-500 text-sm mt-1">Verified properties ready to move in</p>
          </div>
          <Link
            href="/properties?listingType=RENT"
            className="text-forest-600 text-sm font-semibold hover:underline flex items-center gap-1"
          >
            View all <span aria-hidden>→</span>
          </Link>
        </div>

        <Suspense fallback={<FeaturedListingsSkeleton />}>
          <FeaturedListings />
        </Suspense>
      </section>

      {/* Invest CTA */}
      <section className="bg-forest-600 py-16 px-4 text-center text-white">
        <TrendingUp className="w-12 h-12 mx-auto mb-4 opacity-80" />
        <h2 className="text-3xl font-bold mb-3">Invest in African Real Estate</h2>
        <p className="text-forest-100 max-w-xl mx-auto mb-6">
          Buy REIT units starting from $10. Earn projected yields and grow your
          portfolio across Zimbabwe and beyond.
        </p>
        <Link
          href="/investments"
          className="bg-white text-forest-600 font-bold px-8 py-3 rounded-xl hover:bg-forest-50 transition-colors inline-block"
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
