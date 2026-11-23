import { Suspense } from "react";
import ReactDOM from "react-dom";
import Link from "next/link";
import {
  Shield, Globe, Zap, TrendingUp, CheckCircle, Home, KeyRound, BedDouble, Building2, Search, CalendarCheck, Handshake,
} from "lucide-react";
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

      {/* Start here: one obvious door for each kind of visitor */}
      <section className="py-10 px-4 max-w-screen-xl mx-auto w-full" aria-labelledby="start-here">
        <h2 id="start-here" className="text-xl sm:text-2xl font-bold text-gray-900 text-center mb-6">
          What would you like to do?
        </h2>
        <div className="grid grid-cols-2 lg:grid-cols-5 gap-3 sm:gap-4">
          {[
            { href: "/properties?listingType=RENT", icon: KeyRound, title: "Rent a home", desc: "Long-term rentals" },
            { href: "/properties?listingType=SALE", icon: Home, title: "Buy a home", desc: "Houses, flats and land" },
            { href: "/properties?listingType=SHORT_STAY", icon: BedDouble, title: "Book a stay", desc: "Nightly BnB stays" },
            { href: "/properties/new", icon: Building2, title: "List my property", desc: "Rent, sell or host" },
            { href: "/investments", icon: TrendingUp, title: "Invest", desc: "Property shares from $10" },
          ].map(({ href, icon: Icon, title, desc }, i) => (
            <Link
              key={href}
              href={href}
              className={`group flex flex-col items-center text-center gap-2 rounded-2xl border border-gray-200 bg-white p-4 sm:p-5 hover:border-brand-400 hover:shadow-md transition ${
                i === 4 ? "col-span-2 lg:col-span-1" : ""
              }`}
            >
              <span className="w-12 h-12 rounded-full bg-brand-50 text-brand-600 flex items-center justify-center group-hover:bg-brand-600 group-hover:text-white transition-colors">
                <Icon className="w-6 h-6" aria-hidden />
              </span>
              <span className="font-semibold text-gray-900 text-sm sm:text-base">{title}</span>
              <span className="text-gray-500 text-xs">{desc}</span>
            </Link>
          ))}
        </div>
      </section>

      {/* Trust features */}
      <section className="bg-gray-50 border-b border-gray-200 py-10 px-4">
        <div className="max-w-5xl mx-auto grid grid-cols-2 sm:grid-cols-4 gap-6 text-center">
          {[
            { icon: CheckCircle, color: "text-brand-600", label: "Verified Listings", desc: "Every property vetted by certified agents" },
            { icon: Shield, color: "text-brand-600", label: "Deposit Protected", desc: "We hold your deposit safely until you get the keys" },
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
            <h2 className="text-2xl font-bold text-gray-900">Latest properties</h2>
            <p className="text-gray-500 text-sm mt-1">Homes to rent, buy or stay in - verified ones first</p>
          </div>
          <Link
            href="/properties"
            className="text-brand-600 text-sm font-semibold hover:underline flex items-center gap-1"
          >
            View all <span aria-hidden>→</span>
          </Link>
        </div>

        <Suspense fallback={<FeaturedListingsSkeleton />}>
          <FeaturedListings />
        </Suspense>
      </section>

      {/* How it works */}
      <section className="bg-gray-50 border-y border-gray-200 py-12 px-4" aria-labelledby="how-it-works">
        <div className="max-w-5xl mx-auto">
          <h2 id="how-it-works" className="text-2xl font-bold text-gray-900 text-center mb-2">How it works</h2>
          <p className="text-gray-500 text-sm text-center mb-8">Three steps, whether you&apos;re renting, buying or booking a stay.</p>
          <ol className="grid sm:grid-cols-3 gap-6">
            {[
              { icon: Search, title: "1. Search", desc: "Pick Rent, Buy or Stays, type a city or suburb, and narrow it down with filters or the map." },
              { icon: CalendarCheck, title: "2. View or book", desc: "Open a listing to see photos and the agent. Book a viewing, make an offer, or pick dates for a stay." },
              { icon: Handshake, title: "3. Move in safely", desc: "Pay through EstateX, sign the lease online, and keep every receipt and message in one place." },
            ].map(({ icon: Icon, title, desc }) => (
              <li key={title} className="bg-white rounded-2xl border border-gray-200 p-5">
                <Icon className="w-7 h-7 text-brand-600 mb-3" aria-hidden />
                <p className="font-semibold text-gray-900 mb-1">{title}</p>
                <p className="text-gray-600 text-sm leading-relaxed">{desc}</p>
              </li>
            ))}
          </ol>
          <p className="text-center text-sm text-gray-600 mt-6">
            Own a property?{" "}
            <Link href="/properties/new" className="text-brand-600 font-semibold hover:underline">
              List it in a few minutes
            </Link>{" "}
            and manage enquiries, viewings and bookings from your dashboard.
          </p>
        </div>
      </section>

      {/* Invest CTA */}
      <section className="bg-brand-600 py-16 px-4 text-center text-white">
        <TrendingUp className="w-12 h-12 mx-auto mb-4 opacity-80" />
        <h2 className="text-3xl font-bold mb-3">Invest in African Real Estate</h2>
        <p className="text-brand-100 max-w-xl mx-auto mb-6">
          Own a share of income-earning buildings from just $10 and earn from the rent they
          collect. (These are REIT units: shares in a professionally managed property fund.)
        </p>
        <Link
          href="/investments"
          className="bg-white text-brand-600 font-bold px-8 py-3 rounded-xl hover:bg-brand-50 transition-colors inline-block"
        >
          Explore REITs
        </Link>
      </section>

      {/* Footer */}
      <footer className="bg-gray-900 text-gray-400 text-sm py-8 px-4">
        <div className="max-w-screen-xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-4">
          <p>© {new Date().getFullYear()} EstateX. All rights reserved.</p>
          <div className="flex flex-wrap justify-center gap-x-6 gap-y-2">
            <Link href="/properties" className="hover:text-white">Browse</Link>
            <Link href="/properties?listingType=SHORT_STAY" className="hover:text-white">Stays</Link>
            <Link href="/investments" className="hover:text-white">Invest</Link>
            <Link href="/ai" className="hover:text-white">AI Search</Link>
            <Link href="/register" className="hover:text-white">Sign Up</Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
