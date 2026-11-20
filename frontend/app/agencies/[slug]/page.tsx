"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { Building2, BadgeCheck, Phone, Mail, MapPin, Globe } from "lucide-react";
import PropertyCard from "@/components/PropertyCard";
import { companies, type CompanyProfile } from "@/lib/trust";
import type { Property } from "@/lib/types";

/** An approved agency's public page: who they are and everything they have on the market. */
export default function AgencyPage() {
  const { slug } = useParams<{ slug: string }>();
  const [company, setCompany] = useState<CompanyProfile | null>(null);
  const [listings, setListings] = useState<Property[]>([]);
  const [missing, setMissing] = useState(false);

  useEffect(() => {
    if (!slug) return;
    Promise.all([companies.get(slug), companies.listings(slug)])
      .then(([c, l]) => {
        setCompany(c);
        setListings(l);
      })
      .catch(() => setMissing(true));
  }, [slug]);

  if (missing) {
    return (
      <div className="max-w-xl mx-auto px-4 py-16 text-center">
        <h1 className="text-xl font-bold text-gray-900">Agency not found</h1>
        <p className="text-gray-600 mt-2">It may still be waiting for approval.</p>
        <Link href="/agencies" className="inline-block mt-4 text-brand-600 font-semibold py-2">See all agencies</Link>
      </div>
    );
  }
  if (!company) return <div className="max-w-5xl mx-auto px-4 py-8"><div className="h-40 bg-gray-100 rounded-2xl animate-pulse" /></div>;

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      <div className="bg-white border border-gray-200 rounded-2xl p-5 sm:p-6 flex flex-col sm:flex-row gap-5">
        {company.logoOnLightUrl ? (
          // eslint-disable-next-line @next/next/no-img-element -- agency logo
          <img src={company.logoOnLightUrl} alt={`${company.name} logo`} className="w-24 h-24 object-contain rounded-xl bg-gray-50" />
        ) : (
          <span className="w-24 h-24 rounded-xl bg-brand-50 text-brand-600 flex items-center justify-center shrink-0">
            <Building2 className="w-10 h-10" />
          </span>
        )}
        <div className="min-w-0">
          <h1 className="text-2xl font-bold text-gray-900">{company.name}</h1>
          {company.slogan && <p className="text-gray-600">{company.slogan}</p>}
          <p className="mt-2 inline-flex items-center gap-1.5 text-sm font-semibold text-brand-700 bg-brand-50 px-2.5 py-1 rounded-full">
            <BadgeCheck className="w-4 h-4" /> Approved by EstateX
            {company.eacRegistrationNumber && <> · EAC {company.eacRegistrationNumber}</>}
          </p>
          <div className="mt-3 flex flex-wrap gap-x-5 gap-y-2 text-sm text-gray-700">
            {company.phone && <a href={`tel:${company.phone}`} className="flex items-center gap-1.5 py-1"><Phone className="w-4 h-4" />{company.phone}</a>}
            {company.email && <a href={`mailto:${company.email}`} className="flex items-center gap-1.5 py-1 break-all"><Mail className="w-4 h-4" />{company.email}</a>}
            {company.address && <span className="flex items-center gap-1.5 py-1"><MapPin className="w-4 h-4" />{company.address}</span>}
            {company.website && <a href={company.website} target="_blank" rel="noopener noreferrer" className="flex items-center gap-1.5 py-1"><Globe className="w-4 h-4" />Website</a>}
          </div>
          {company.preaName && <p className="text-sm text-gray-500 mt-2">Principal agent: {company.preaName}</p>}
        </div>
      </div>

      <h2 className="text-xl font-bold text-gray-900 mt-8 mb-4">
        {listings.length > 0 ? `${listings.length} propert${listings.length === 1 ? "y" : "ies"} on the market` : "No properties on the market right now"}
      </h2>
      <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-5">
        {listings.map((p) => <PropertyCard key={p.id} property={p} />)}
      </div>
    </div>
  );
}
