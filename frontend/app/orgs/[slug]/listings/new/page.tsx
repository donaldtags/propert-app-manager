"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { orgListings } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { useZimbabweLocations } from "@/lib/locations";
import { parseToCents } from "@/lib/money";
import type { ListingType, TitleType } from "@/lib/types";
import { useOrgWorkspace, ws } from "@/components/workspace/OrgWorkspace";
import { TITLE_TYPES } from "@/components/workspace/listingLabels";


export default function NewOrgListingPage() {
  const { slug, context, can } = useOrgWorkspace();
  const { token } = useAuth();
  const router = useRouter();
  const { cities, suburbs } = useZimbabweLocations();
  const [form, setForm] = useState({
    title: "",
    description: "",
    listingType: "SALE" as ListingType,
    city: "Harare",
    suburb: "",
    address: "",
    bedrooms: "3",
    bathrooms: "2",
    price: "",
    currency: context.organisation.defaultCurrency,
    titleType: "" as TitleType | "",
  });
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const set = (k: keyof typeof form, v: string) => setForm((f) => ({ ...f, [k]: v }));

  if (!can("LISTING_CREATE")) return <p className="text-muted">Your role can&apos;t create listings.</p>;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!token) return;
    const priceCents = parseToCents(form.price);
    if (!priceCents) {
      setError("Enter a valid price, e.g. 250000 or 650.50");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const created = await orgListings.create(
        slug,
        {
          title: form.title.trim(),
          description: form.description.trim() || undefined,
          listingType: form.listingType,
          city: form.city,
          suburb: form.suburb.trim(),
          address: form.address.trim() || undefined,
          bedrooms: Number(form.bedrooms),
          bathrooms: Number(form.bathrooms),
          priceCents,
          currency: form.currency,
          titleType: form.titleType || undefined,
        },
        token
      );
      router.push(`/orgs/${slug}/listings/${created.property.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not create the listing");
      setSaving(false);
    }
  };

  return (
    <form onSubmit={submit} className="max-w-2xl space-y-5">
      <div>
        <h1 className="text-xl font-semibold">New listing</h1>
        <p className="text-sm text-muted mt-1">
          Saved as a draft. It stays private until every compliance check is met and it&apos;s published.
        </p>
      </div>

      <div role="radiogroup" aria-label="Listing type" className="grid grid-cols-2 gap-2">
        {(["SALE", "RENT"] as ListingType[]).map((t) => (
          <button
            key={t}
            type="button"
            role="radio"
            aria-checked={form.listingType === t}
            onClick={() => set("listingType", t)}
            className={`${ws.buttonGhost} ${form.listingType === t ? "!bg-primary !text-on-primary border-transparent" : ""}`}
          >
            {t === "SALE" ? "For sale" : "To let"}
          </button>
        ))}
      </div>

      <label className="block">
        <span className={ws.label}>Title</span>
        <input className={ws.input} required maxLength={200} value={form.title} onChange={(e) => set("title", e.target.value)}
          placeholder="e.g. 4 bed family home with borehole" />
      </label>

      <div className="grid gap-4 sm:grid-cols-2">
        <label className="block">
          <span className={ws.label}>City / town</span>
          <select className={ws.input} value={form.city} onChange={(e) => setForm((f) => ({ ...f, city: e.target.value, suburb: "" }))}>
            {cities.map((c) => (
              <option key={c}>{c}</option>
            ))}
          </select>
        </label>
        <label className="block">
          <span className={ws.label}>Suburb</span>
          <input className={ws.input} required list="org-suburbs" value={form.suburb} onChange={(e) => set("suburb", e.target.value)} />
          <datalist id="org-suburbs">
            {(suburbs[form.city] ?? []).map((s) => (
              <option key={s} value={s} />
            ))}
          </datalist>
        </label>
        <label className="block sm:col-span-2">
          <span className={ws.label}>Street address</span>
          <input className={ws.input} value={form.address} onChange={(e) => set("address", e.target.value)}
            placeholder="Only shown publicly if the owner consents" />
        </label>
        <label className="block">
          <span className={ws.label}>Bedrooms</span>
          <input className={ws.input} type="number" min={0} max={50} value={form.bedrooms} onChange={(e) => set("bedrooms", e.target.value)} />
        </label>
        <label className="block">
          <span className={ws.label}>Bathrooms</span>
          <input className={ws.input} type="number" min={0} max={50} value={form.bathrooms} onChange={(e) => set("bathrooms", e.target.value)} />
        </label>
        <label className="block">
          <span className={ws.label}>{form.listingType === "SALE" ? "Asking price" : "Monthly rent"}</span>
          <input className={ws.input} inputMode="decimal" required value={form.price} onChange={(e) => set("price", e.target.value)} placeholder="250000" />
        </label>
        <label className="block">
          <span className={ws.label}>Currency</span>
          <select className={ws.input} value={form.currency} onChange={(e) => set("currency", e.target.value)}>
            <option value="USD">USD</option>
            <option value="ZWG">ZiG (ZWG)</option>
          </select>
        </label>
        <label className="block sm:col-span-2">
          <span className={ws.label}>Title type</span>
          <select className={ws.input} value={form.titleType} onChange={(e) => set("titleType", e.target.value)}>
            <option value="">Not known yet</option>
            {TITLE_TYPES.map((t) => (
              <option key={t.value} value={t.value}>
                {t.label}
              </option>
            ))}
          </select>
        </label>
        <label className="block sm:col-span-2">
          <span className={ws.label}>Description</span>
          <textarea className={`${ws.input} min-h-28`} maxLength={4000} value={form.description} onChange={(e) => set("description", e.target.value)} />
        </label>
      </div>

      {error && <p role="alert" className="text-sm text-danger">{error}</p>}
      <button className={ws.button} disabled={saving}>{saving ? "Saving…" : "Create draft listing"}</button>
    </form>
  );
}
