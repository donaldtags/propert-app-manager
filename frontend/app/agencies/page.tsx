"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Building2, BadgeCheck, Plus } from "lucide-react";
import { companies, type CompanyProfile } from "@/lib/trust";

/** Public directory of estate agencies EstateX has approved. */
export default function AgenciesPage() {
  const [list, setList] = useState<CompanyProfile[] | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    companies.directory().then(setList).catch(() => setError("Couldn't load agencies. Please refresh."));
  }, []);

  return (
    <div className="max-w-5xl mx-auto px-4 py-8">
      <div className="flex flex-wrap items-end justify-between gap-4 mb-6">
        <div>
          <h1 className="text-2xl sm:text-3xl font-bold text-gray-900">Estate agencies</h1>
          <p className="text-gray-600 mt-1">
            Every agency here is registered with the Estate Agents Council and approved by EstateX.
          </p>
        </div>
        <Link
          href="/agencies/register"
          className="inline-flex items-center gap-2 bg-brand-600 hover:bg-brand-700 text-white font-semibold px-4 py-2.5 rounded-xl"
        >
          <Plus className="w-4 h-4" /> Register your agency
        </Link>
      </div>

      {error && <p className="text-red-600" role="alert">{error}</p>}
      {!list && !error && (
        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {Array.from({ length: 6 }).map((_, i) => <div key={i} className="h-28 bg-gray-100 rounded-2xl animate-pulse" />)}
        </div>
      )}
      {list && list.length === 0 && <p className="text-gray-600">No agencies yet. Be the first to register yours.</p>}
      {list && list.length > 0 && (
        <ul className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {list.map((c) => (
            <li key={c.slug}>
              <Link
                href={`/agencies/${c.slug}`}
                className="flex gap-3 items-center bg-white border border-gray-200 rounded-2xl p-4 hover:border-brand-400 hover:shadow-sm transition"
              >
                {c.logoOnLightUrl ? (
                  // eslint-disable-next-line @next/next/no-img-element -- agency logo
                  <img src={c.logoOnLightUrl} alt="" className="w-14 h-14 object-contain rounded-lg bg-gray-50" />
                ) : (
                  <span className="w-14 h-14 rounded-lg bg-brand-50 text-brand-600 flex items-center justify-center shrink-0">
                    <Building2 className="w-6 h-6" />
                  </span>
                )}
                <span className="min-w-0">
                  <span className="block font-semibold text-gray-900 truncate">{c.name}</span>
                  {c.slogan && <span className="block text-sm text-gray-500 truncate">{c.slogan}</span>}
                  {c.eacRegistrationNumber && (
                    <span className="mt-1 inline-flex items-center gap-1 text-xs text-brand-700">
                      <BadgeCheck className="w-3.5 h-3.5" /> EAC {c.eacRegistrationNumber}
                    </span>
                  )}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
