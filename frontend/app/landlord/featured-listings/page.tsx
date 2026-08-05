"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/lib/auth";
import { dashboards, properties as propertiesApi, featuredListings } from "@/lib/api";
import type { LandlordDashboard, FeaturedListingSettings } from "@/lib/types";
import { settingsRoleUrl } from "@/lib/roleGate";
import {
  Star,
  AlertCircle,
  CheckCircle,
  MapPin,
  ArrowLeft,
  Receipt,
} from "lucide-react";

const BUSINESS_ROLES = ["LANDLORD", "AGENT", "DEVELOPER", "PRIVATE"] as const;

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
}

export default function FeaturedListingsPage() {
  const { user, token, loading } = useAuth();
  const router = useRouter();

  const [dashboard, setDashboard] = useState<LandlordDashboard | null>(null);
  const [dashLoading, setDashLoading] = useState(true);
  const [settings, setSettings] = useState<FeaturedListingSettings | null>(null);
  const [featuringId, setFeaturingId] = useState<number | null>(null);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const hasBusinessRole = (roles: string[] | undefined) =>
    !!roles?.some((r) => BUSINESS_ROLES.includes(r as typeof BUSINESS_ROLES[number]));

  useEffect(() => {
    if (!loading && (!user || !hasBusinessRole(user.roles))) {
      router.push(
        user
          ? settingsRoleUrl(
              ["LANDLORD", "AGENT", "DEVELOPER", "PRIVATE"],
              "featuring a listing needs a Landlord, Agent, Developer, or Private Seller role"
            )
          : "/login?redirect=/landlord/featured-listings"
      );
    }
  }, [user, loading, router]);

  useEffect(() => {
    featuredListings.settings().then(setSettings).catch(() => {});
  }, []);

  useEffect(() => {
    if (user && token && hasBusinessRole(user.roles)) {
      dashboards.landlord(user.id, token)
        .then(setDashboard)
        .catch(() => setError("Failed to load your properties."))
        .finally(() => setDashLoading(false));
    }
  }, [user, token]);

  const ownProperties = dashboard?.properties ?? [];

  const handleFeature = async (propertyId: number, title: string) => {
    if (!token) return;
    setFeaturingId(propertyId);
    setError("");
    setSuccess("");
    try {
      const result = await propertiesApi.feature(propertyId, token);
      setSuccess(
        `${title} is now featured until ${formatDate(result.featuredUntil)} — charged $${result.amountCharged.toFixed(2)} ${result.currency}.`
      );
      setDashboard((prev) =>
        prev
          ? {
              ...prev,
              properties: prev.properties.map((p) =>
                p.id === propertyId ? { ...p, featured: true, featuredUntil: result.featuredUntil } : p
              ),
            }
          : prev
      );
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not feature this listing.");
    } finally {
      setFeaturingId(null);
    }
  };

  if (loading || dashLoading) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-8 space-y-3">
        {Array.from({ length: 4 }).map((_, i) => (
          <div key={i} className="h-24 bg-gray-100 rounded-2xl animate-pulse" />
        ))}
      </div>
    );
  }

  return (
    <div className="max-w-3xl mx-auto px-4 py-8">
      <Link href="/landlord" className="text-sm text-gray-500 hover:text-gray-700 inline-flex items-center gap-1 mb-4">
        <ArrowLeft className="w-4 h-4" /> Back to dashboard
      </Link>

      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
          <Star className="w-6 h-6 text-amber-500" /> Featured Listings
        </h1>
        <p className="text-gray-500 mt-1">Pay to boost one of your listings to the top of search results.</p>
      </div>

      {settings && (
        <div className="bg-amber-50 border border-amber-100 rounded-2xl p-5 mb-6 flex items-start gap-3">
          <Receipt className="w-5 h-5 text-amber-600 shrink-0 mt-0.5" />
          <div>
            <p className="text-sm font-semibold text-amber-900">
              Featuring a listing costs ${settings.price.toFixed(2)} {settings.currency} for {settings.durationDays} days
            </p>
            <p className="text-xs text-amber-700 mt-1">
              This price is set by PrimeNest admin and can change. Charging happens instantly when you feature a
              listing, and the charge appears in your <Link href="/payments" className="underline">billing history</Link>.
            </p>
          </div>
        </div>
      )}

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-5 flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0" /> {error}
        </div>
      )}
      {success && (
        <div className="bg-green-50 border border-green-200 text-green-700 text-sm px-4 py-3 rounded-xl mb-5 flex items-center gap-2">
          <CheckCircle className="w-4 h-4 shrink-0" /> {success}
        </div>
      )}

      {ownProperties.length === 0 ? (
        <div className="text-center py-16 text-gray-500">
          <Star className="w-12 h-12 mx-auto mb-4 opacity-30" />
          <p className="text-lg font-medium">No listings yet</p>
          <Link href="/properties/new" className="mt-3 inline-block text-sm text-blue-600 hover:underline">+ Add your first property</Link>
        </div>
      ) : (
        <div className="space-y-3">
          {ownProperties.map((prop) => {
            const isFeaturedActive = prop.featured && prop.featuredUntil && new Date(prop.featuredUntil).getTime() > Date.now();
            return (
              <div key={prop.id} className="bg-white border border-gray-200 rounded-2xl p-4 flex items-center justify-between gap-4">
                <div className="min-w-0">
                  <Link href={`/properties/${prop.id}`} className="text-sm font-semibold text-gray-900 hover:text-blue-600 transition-colors truncate block">
                    {prop.title}
                  </Link>
                  <p className="text-xs text-gray-500 flex items-center gap-1 mt-0.5">
                    <MapPin className="w-3 h-3" /> {prop.suburb}, {prop.city}
                  </p>
                  {isFeaturedActive && prop.featuredUntil && (
                    <p className="text-xs font-medium text-amber-700 mt-1">★ Featured until {formatDate(prop.featuredUntil)}</p>
                  )}
                </div>
                <button
                  onClick={() => handleFeature(prop.id, prop.title)}
                  disabled={featuringId === prop.id || !!isFeaturedActive}
                  className="shrink-0 bg-amber-500 hover:bg-amber-600 disabled:opacity-50 disabled:cursor-not-allowed text-white font-semibold py-2 px-4 rounded-xl text-sm transition-colors flex items-center gap-2"
                >
                  <Star className="w-4 h-4" />
                  {featuringId === prop.id
                    ? "Processing..."
                    : isFeaturedActive
                      ? "Featured"
                      : settings
                        ? `Feature for $${settings.price.toFixed(2)}`
                        : "Feature this listing"}
                </button>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}