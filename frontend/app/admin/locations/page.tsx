"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { ArrowLeft, Check, X } from "lucide-react";
import { locations } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import type { DensityTier, LocationNode } from "@/lib/types";
import PageLoader from "@/components/PageLoader";

const TIERS: { value: DensityTier; label: string }[] = [
  { value: "HIGH_DENSITY", label: "High density" },
  { value: "MEDIUM_DENSITY", label: "Medium density" },
  { value: "LOW_DENSITY", label: "Low density" },
  { value: "PERI_URBAN", label: "Peri-urban" },
  { value: "INDUSTRIAL", label: "Industrial" },
  { value: "CBD", label: "CBD" },
];

/** Admin review queue for suburbs users typed that weren't in the reference list. */
export default function LocationSuggestionsPage() {
  const { user, token, loading } = useAuth();
  const router = useRouter();
  const [items, setItems] = useState<LocationNode[] | null>(null);
  const [tiers, setTiers] = useState<Record<number, DensityTier>>({});
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    if (loading) return;
    if (!user || !token || !user.roles.includes("ADMIN")) {
      router.replace("/login?redirect=%2Fadmin%2Flocations");
      return;
    }
    locations.pending(token).then(setItems, (e: unknown) => setError(e instanceof Error ? e.message : "Failed to load"));
  }, [loading, user, token, router, reloadKey]);

  const act = async (fn: () => Promise<unknown>) => {
    setError(null);
    try {
      await fn();
      setReloadKey((k) => k + 1);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Action failed");
    }
  };

  if (!items) return <PageLoader />;

  return (
    <div className="max-w-3xl mx-auto w-full px-4 py-8">
      <Link href="/admin" className="inline-flex items-center gap-1 text-sm text-brand-600 hover:underline">
        <ArrowLeft className="w-4 h-4" /> Admin portal
      </Link>
      <h1 className="mt-3 text-2xl font-bold text-gray-900">Suburb suggestions</h1>
      <p className="text-sm text-gray-600 mt-1">
        Suburbs people typed that aren&apos;t in the reference list yet. Approve them to make them selectable for everyone.
      </p>
      {error && <p className="mt-4 text-sm text-red-700">{error}</p>}
      {items.length === 0 ? (
        <p className="mt-8 text-gray-500">No suggestions waiting.</p>
      ) : (
        <ul className="mt-6 space-y-3">
          {items.map((l) => (
            <li key={l.id} className="rounded-2xl border border-gray-200 bg-white p-4 flex flex-wrap items-center gap-3">
              <div className="min-w-0 flex-1">
                <p className="font-semibold text-gray-900">{l.name}</p>
                <p className="text-sm text-gray-500">
                  {l.city}, {l.province}
                </p>
              </div>
              <select
                aria-label={`Density tier for ${l.name}`}
                className="rounded-lg border border-gray-300 px-2 py-2 text-sm"
                value={tiers[l.id] ?? ""}
                onChange={(e) => setTiers((t) => ({ ...t, [l.id]: e.target.value as DensityTier }))}
              >
                <option value="">Tier (optional)</option>
                {TIERS.map((t) => (
                  <option key={t.value} value={t.value}>
                    {t.label}
                  </option>
                ))}
              </select>
              <button
                onClick={() => token && act(() => locations.update(l.id, { verified: true, densityTier: tiers[l.id] }, token))}
                className="inline-flex items-center gap-1 rounded-lg bg-brand-600 text-white px-3 py-2 text-sm font-medium hover:bg-brand-700"
              >
                <Check className="w-4 h-4" /> Approve
              </button>
              <button
                onClick={() => token && act(() => locations.reject(l.id, token))}
                className="inline-flex items-center gap-1 rounded-lg border border-gray-300 px-3 py-2 text-sm font-medium hover:bg-gray-50"
              >
                <X className="w-4 h-4" /> Reject
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
