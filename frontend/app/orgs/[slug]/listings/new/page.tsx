"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { Camera } from "lucide-react";
import { useZimbabweLocations } from "@/lib/locations";
import { parseToCents } from "@/lib/money";
import { addDays, harareToday } from "@/lib/dates";
import { compressForUpload } from "@/lib/offline/compress";
import { enqueueAllAndWait, lookupServerId, newClientId, useSyncStatus } from "@/lib/offline/sync";
import type { OutboxOp } from "@/lib/offline/types";
import type { ListingType, MandateType, TitleType } from "@/lib/types";
import { useOrgWorkspace, ws } from "@/components/workspace/OrgWorkspace";
import { TITLE_TYPES } from "@/components/workspace/listingLabels";

/**
 * Listing intake. Works offline: everything entered here (listing, title, owner consent, mandate
 * and a photo of the signed copy) is saved on the device as one ordered batch and synced when
 * there's a connection. Approval and publishing happen later, online, on the listing page.
 */
export default function NewOrgListingPage() {
  const { slug, context, can } = useOrgWorkspace();
  const router = useRouter();
  const sync = useSyncStatus();
  const { cities, suburbs } = useZimbabweLocations();
  const today = harareToday();
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
    titleReference: "",
    consentPhotos: false,
    consentAddress: false,
    addMandate: false,
    mandateType: "SOLE" as MandateType,
    ownerName: "",
    ownerPhone: "",
    startDate: today,
    expiryDate: addDays(today, 90),
    minimumPrice: "",
  });
  const [signedCopy, setSignedCopy] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const set = <K extends keyof typeof form>(k: K, v: (typeof form)[K]) => setForm((f) => ({ ...f, [k]: v }));

  if (!can("LISTING_CREATE")) return <p className="text-muted">Your role can&apos;t create listings.</p>;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const priceCents = parseToCents(form.price);
    if (!priceCents) return setError("Enter a valid price, e.g. 250000 or 650.50");
    const minimumPriceCents = form.minimumPrice ? parseToCents(form.minimumPrice) : null;
    if (form.addMandate) {
      if (!form.ownerName.trim()) return setError("Add the owner's name for the mandate.");
      if (form.expiryDate < form.startDate) return setError("The mandate can't expire before it starts.");
      if (form.minimumPrice && !minimumPriceCents) return setError("Enter a valid minimum price.");
    }
    setSaving(true);
    setError(null);

    const listingId = newClientId();
    const title = form.title.trim();
    const ops: OutboxOp[] = [
      {
        kind: "createListing",
        clientId: listingId,
        label: `New listing: ${title}`,
        payload: {
          title,
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
      },
    ];
    if (form.titleType && form.titleReference.trim()) {
      ops.push({
        kind: "setTitle",
        listing: { clientId: listingId },
        base: { titleType: form.titleType, titleReference: null },
        mine: { titleType: form.titleType, titleReference: form.titleReference.trim() },
        label: `Title reference for ${title}`,
      });
    }
    if (form.consentPhotos || form.consentAddress) {
      ops.push({
        kind: "setConsent",
        listing: { clientId: listingId },
        base: { photos: false, address: false },
        mine: { photos: form.consentPhotos, address: form.consentAddress },
        label: `Owner consent for ${title}`,
      });
    }
    if (form.addMandate) {
      const mandateId = newClientId();
      ops.push({
        kind: "createMandate",
        clientId: mandateId,
        listing: { clientId: listingId },
        label: `Mandate for ${title}`,
        payload: {
          type: form.mandateType,
          startDate: form.startDate,
          expiryDate: form.expiryDate,
          ownerName: form.ownerName.trim(),
          ownerPhone: form.ownerPhone.trim() || undefined,
          minimumPriceCents: minimumPriceCents ?? undefined,
        },
      });
      if (signedCopy) {
        const { blob, name } = await compressForUpload(signedCopy);
        ops.push({
          kind: "attachMandateDocument",
          mandate: { clientId: mandateId },
          file: blob,
          fileName: name,
          ownerSignedOn: today,
          label: `Signed mandate for ${title}`,
        });
      }
    }

    try {
      const allSent = await enqueueAllAndWait(slug, ops);
      const serverId = await lookupServerId(listingId);
      if (allSent && serverId) {
        router.push(`/orgs/${slug}/listings/${serverId}`);
      } else {
        router.push(`/orgs/${slug}/listings?saved=offline`);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save on this device");
      setSaving(false);
    }
  };

  return (
    <form onSubmit={submit} className="max-w-2xl space-y-6">
      <div>
        <h1 className="text-xl font-semibold">New listing</h1>
        <p className="text-sm text-muted mt-1">
          Saved as a private draft{sync.online ? "" : " on this device until you're back online"}. It goes live only after the
          PREA approves it.
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

      <section className="space-y-4">
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
            <select className={ws.input} value={form.currency} onChange={(e) => set("currency", e.target.value as "USD" | "ZWG")}>
              <option value="USD">USD</option>
              <option value="ZWG">ZiG (ZWG)</option>
            </select>
          </label>
          <label className="block sm:col-span-2">
            <span className={ws.label}>Description</span>
            <textarea className={`${ws.input} min-h-24`} maxLength={4000} value={form.description} onChange={(e) => set("description", e.target.value)} />
          </label>
        </div>
      </section>

      <section className={`${ws.card} p-4 space-y-3`}>
        <h2 className="font-semibold">Title</h2>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block">
            <span className={ws.label}>Title type</span>
            <select className={ws.input} value={form.titleType} onChange={(e) => set("titleType", e.target.value as TitleType | "")}>
              <option value="">Not known yet</option>
              {TITLE_TYPES.map((t) => (
                <option key={t.value} value={t.value}>
                  {t.label}
                </option>
              ))}
            </select>
          </label>
          <label className="block">
            <span className={ws.label}>Deed / title / cession no.</span>
            <input className={ws.input} value={form.titleReference} disabled={!form.titleType} onChange={(e) => set("titleReference", e.target.value)} />
          </label>
        </div>
        {form.titleType === "CESSION" && <p className="text-xs text-warning">Cession listings show a risk badge to buyers.</p>}
      </section>

      <section className={`${ws.card} p-4 space-y-2`}>
        <h2 className="font-semibold">Owner consent</h2>
        {[
          { key: "consentPhotos" as const, label: "Owner consents to photos being shown" },
          { key: "consentAddress" as const, label: "Owner consents to the address being shown" },
        ].map((c) => (
          <label key={c.key} className="flex items-center gap-3 min-h-11 text-sm">
            <input type="checkbox" className="h-5 w-5 accent-[var(--ex-primary)]" checked={form[c.key]} onChange={(e) => set(c.key, e.target.checked)} />
            {c.label}
          </label>
        ))}
      </section>

      <section className={`${ws.card} p-4 space-y-3`}>
        <label className="flex items-center gap-3 min-h-11 font-semibold">
          <input type="checkbox" className="h-5 w-5 accent-[var(--ex-primary)]" checked={form.addMandate} onChange={(e) => set("addMandate", e.target.checked)} />
          The owner signed a mandate
        </label>
        {form.addMandate && (
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="block">
              <span className={ws.label}>Type</span>
              <select className={ws.input} value={form.mandateType} onChange={(e) => set("mandateType", e.target.value as MandateType)}>
                <option value="SOLE">Sole mandate</option>
                <option value="OPEN">Open mandate</option>
              </select>
            </label>
            <label className="block">
              <span className={ws.label}>Owner name</span>
              <input className={ws.input} value={form.ownerName} onChange={(e) => set("ownerName", e.target.value)} />
            </label>
            <label className="block">
              <span className={ws.label}>Owner phone / WhatsApp</span>
              <input className={ws.input} inputMode="tel" value={form.ownerPhone} onChange={(e) => set("ownerPhone", e.target.value)} />
            </label>
            <label className="block">
              <span className={ws.label}>{form.listingType === "SALE" ? "Lowest acceptable price" : "Lowest acceptable rent"}</span>
              <input className={ws.input} inputMode="decimal" value={form.minimumPrice} onChange={(e) => set("minimumPrice", e.target.value)} />
            </label>
            <label className="block">
              <span className={ws.label}>Starts</span>
              <input type="date" className={ws.input} value={form.startDate} onChange={(e) => set("startDate", e.target.value)} />
            </label>
            <label className="block">
              <span className={ws.label}>Expires (last day)</span>
              <input type="date" className={ws.input} min={today} value={form.expiryDate} onChange={(e) => set("expiryDate", e.target.value)} />
            </label>
            <label className="block sm:col-span-2">
              <span className={ws.label}>Photo or PDF of the signed mandate</span>
              <span className={`${ws.buttonGhost} w-full cursor-pointer`}>
                <Camera className="w-4 h-4" /> {signedCopy ? signedCopy.name : "Take photo / choose file"}
                <input
                  type="file"
                  accept="image/*,application/pdf"
                  capture="environment"
                  className="sr-only"
                  onChange={(e) => setSignedCopy(e.target.files?.[0] ?? null)}
                />
              </span>
              <span className="mt-1 block text-xs text-muted">Photos are compressed before saving. The PREA signs off once it&apos;s synced.</span>
            </label>
          </div>
        )}
      </section>

      {error && <p role="alert" className="text-sm text-danger">{error}</p>}
      <button className={`${ws.button} w-full sm:w-auto`} disabled={saving}>
        {saving ? "Saving…" : sync.online ? "Save draft listing" : "Save on this device"}
      </button>
    </form>
  );
}
