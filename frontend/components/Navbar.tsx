"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";
import { useAuth } from "@/lib/auth";
import { Home, Menu, X, ChevronDown } from "lucide-react";

export default function Navbar() {
  const { user, logout, loading } = useAuth();
  const [menuOpen, setMenuOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const pathname = usePathname();
  const router = useRouter();

  const handleLogout = async () => {
    await logout();
    router.push("/");
  };

  const navLinks = [
    { href: "/properties?listingType=SALE", label: "Buy" },
    { href: "/properties?listingType=RENT", label: "Rent" },
    { href: "/investments", label: "Invest" },
    { href: "/ai", label: "AI Search" },
  ];

  return (
    <nav className="sticky top-0 z-50 bg-white border-b border-gray-200 shadow-sm">
      <div className="max-w-screen-2xl mx-auto px-4 h-16 flex items-center justify-between gap-4">
        {/* Logo */}
        <Link href="/" className="flex items-center gap-2 shrink-0">
          <div className="w-8 h-8 bg-blue-600 rounded flex items-center justify-center">
            <Home className="w-4 h-4 text-white" />
          </div>
          <span className="text-xl font-bold text-blue-600 hidden sm:block">PrimeNest</span>
        </Link>

        {/* Desktop nav links */}
        <div className="hidden md:flex items-center gap-6 text-sm font-medium text-gray-700">
          {navLinks.map((l) => (
            <Link
              key={l.href}
              href={l.href}
              className={`hover:text-blue-600 transition-colors ${pathname === l.href.split("?")[0] ? "text-blue-600" : ""}`}
            >
              {l.label}
            </Link>
          ))}
        </div>

        {/* Auth area */}
        <div className="flex items-center gap-3 ml-auto">
          <Link
            href="/properties/new"
            className="hidden sm:flex items-center gap-1 text-sm font-medium text-gray-700 hover:text-blue-600 border border-gray-200 hover:border-blue-300 px-3 py-2 rounded-lg transition-colors"
          >
            + List Property
          </Link>

        {loading ? (
            <div className="w-24 h-8 bg-gray-100 rounded animate-pulse" />
          ) : user ? (
            <div className="relative">
              <button
                onClick={() => setProfileOpen(!profileOpen)}
                className="flex items-center gap-2 text-sm font-medium text-gray-700 hover:text-blue-600 transition-colors"
              >
                <div className="w-8 h-8 rounded-full bg-blue-100 text-blue-700 flex items-center justify-center font-bold text-xs">
                  {user.fullName?.charAt(0).toUpperCase()}
                </div>
                <span className="hidden sm:block max-w-28 truncate">{user.fullName}</span>
                <ChevronDown className="w-4 h-4" />
              </button>
              {profileOpen && (
                <div className="absolute right-0 top-full mt-2 w-52 bg-white rounded-lg shadow-lg border border-gray-200 py-1 z-50">
                  <Link href="/profile" onClick={() => setProfileOpen(false)} className="block px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">My Profile</Link>
                  {user.roles?.includes("TENANT") && (
                    <Link href="/tenant" onClick={() => setProfileOpen(false)} className="block px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">Tenant Dashboard</Link>
                  )}
                  {(user.roles?.includes("LANDLORD") || user.roles?.includes("PRIVATE")) && (
                    <Link href="/landlord" onClick={() => setProfileOpen(false)} className="block px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">Landlord Dashboard</Link>
                  )}
                  {user.roles?.includes("AGENT") && (
                    <Link href="/landlord" onClick={() => setProfileOpen(false)} className="block px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">Agent Dashboard</Link>
                  )}
                  {user.roles?.includes("DEVELOPER") && (
                    <Link href="/landlord" onClick={() => setProfileOpen(false)} className="block px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">Developer Dashboard</Link>
                  )}
                  {user.roles?.includes("INVESTOR") && (
                    <Link href="/investments" onClick={() => setProfileOpen(false)} className="block px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">My Investments</Link>
                  )}
                  <Link href="/messages" onClick={() => setProfileOpen(false)} className="block px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">Messages</Link>
                  <Link href="/settings/security" onClick={() => setProfileOpen(false)} className="block px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">Settings</Link>
                  <hr className="my-1 border-gray-100" />
                  <button onClick={handleLogout} className="block w-full text-left px-4 py-2 text-sm text-red-600 hover:bg-red-50">
                    Sign Out
                  </button>
                </div>
              )}
            </div>
          ) : (
            <>
              <Link href="/login" className="text-sm font-medium text-gray-700 hover:text-blue-600 transition-colors hidden sm:block">
                Sign In
              </Link>
              <Link
                href="/register"
                className="text-sm font-semibold bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-lg transition-colors"
              >
                Register
              </Link>
            </>
          )}

          {/* Mobile menu toggle */}
          <button
            className="md:hidden p-2 text-gray-600"
            onClick={() => setMenuOpen(!menuOpen)}
          >
            {menuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
          </button>
        </div>
      </div>

      {/* Mobile menu */}
      {menuOpen && (
        <div className="md:hidden border-t border-gray-200 bg-white py-3 px-4">
          {navLinks.map((l) => (
            <Link
              key={l.href}
              href={l.href}
              onClick={() => setMenuOpen(false)}
              className="block py-2 text-sm font-medium text-gray-700 hover:text-blue-600"
            >
              {l.label}
            </Link>
          ))}
          {!user && (
            <Link href="/login" onClick={() => setMenuOpen(false)} className="block py-2 text-sm font-medium text-gray-700">
              Sign In
            </Link>
          )}
        </div>
      )}
    </nav>
  );
}
