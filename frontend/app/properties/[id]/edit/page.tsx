"use client";

import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/lib/auth";
import { properties as propertiesApi } from "@/lib/api";
import { Home, CheckCircle, AlertCircle, Image as ImageIcon, Upload, X } from "lucide-react";
import type { Property, PropertyBilling } from "@/lib/types";
import { ZIMBABWE_CITIES, ZIMBABWE_SUBURBS } from "@/lib/zimbabweLocations";

export default function EditPropertyPage() {
  const { id } = useParams<{ id: string }>();
  const { user, token, loading: authLoading } = useAuth();
  const router = useRouter();

  const [property, setProperty] = useState<Property | null>(null);
  const [billing, setBilling] = useState<PropertyBilling | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");

  const [form, setForm] = useState({
    title: "",
    description: "",
    city: "",
    suburb: "",
    address: "",
    bedrooms: 0,
    bathrooms: 0,
    price: "",
    currency: "USD",
    diasporaFriendly: false,
    escrowRequired: false,
    solarInstalled: false,
    backupPower: false,
    furnished: false,
    internetAvailable: false,
    securityFeatures: false,
    parkingAvailable: false,
    petsAllowed: false,
    virtualTourUrl: "",
  });

  const [photoFiles, setPhotoFiles] = useState<File[]>([]);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [deletingPhotoId, setDeletingPhotoId] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState(false);

  useEffect(() => {
    if (authLoading) return;
    if (!user) {
      router.push(`/login?redirect=${encodeURIComponent(`/properties/${id}/edit`)}`);
      return;
    }
    if (!id) return;
    (async () => {
      try {
        const prop = await propertiesApi.get(Number(id));
        const isOwner = user.id === prop.landlordId || user.id === prop.agentId;
        if (!isOwner && !user.roles?.includes("ADMIN")) {
          setLoadError("You don't have permission to edit this listing.");
          return;
        }
        setProperty(prop);
        setForm({
          title: prop.title,
          description: prop.description ?? "",
          city: prop.city,
          suburb: prop.suburb,
          address: prop.address ?? "",
          bedrooms: prop.bedrooms,
          bathrooms: prop.bathrooms,
          price: String(prop.price),
          currency: prop.currency,
          diasporaFriendly: prop.diasporaFriendly,
          escrowRequired: prop.escrowRequired,
          solarInstalled: prop.solarInstalled,
          backupPower: prop.backupPower,
          furnished: prop.furnished,
          internetAvailable: prop.internetAvailable,
          securityFeatures: prop.securityFeatures,
          parkingAvailable: prop.parkingAvailable,
          petsAllowed: prop.petsAllowed,
          virtualTourUrl: prop.virtualTourUrl ?? "",
        });
        if (token) {
          propertiesApi.billing(prop.id, token).then(setBilling).catch(() => {});
        }
      } catch {
        setLoadError("Could not load this listing.");
      } finally {
        setLoading(false);
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authLoading, user, id]);

  const suburbs = form.city ? ZIMBABWE_SUBURBS[form.city] ?? [] : [];

  const addFiles = (files: FileList | null) => {
    if (!files || files.length === 0) return;
    setPhotoFiles((prev) => [...prev, ...Array.from(files)]);
  };

  const removeFile = (i: number) => {
    setPhotoFiles((prev) => prev.filter((_, idx) => idx !== i));
  };

  const filePreviews = useMemo(() => photoFiles.map((f) => URL.createObjectURL(f)), [photoFiles]);
  useEffect(() => {
    return () => filePreviews.forEach((url) => URL.revokeObjectURL(url));
  }, [filePreviews]);

  const handleDeletePhoto = async (photoId: number) => {
    if (!property || !token) return;
    setDeletingPhotoId(photoId);
    try {
      const updated = await propertiesApi.deletePhoto(property.id, photoId, token);
      setProperty(updated);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to remove photo.");
    } finally {
      setDeletingPhotoId(null);
    }
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!property || !token) return;
    if (!form.title || !form.city || !form.suburb || !form.price) {
      setError("Title, city, suburb, and price are required.");
      return;
    }
    setError("");
    setSubmitting(true);
    try {
      const updated = await propertiesApi.update(
        property.id,
        {
          title: form.title,
          description: form.description || undefined,
          city: form.city,
          suburb: form.suburb,
          address: form.address || undefined,
          bedrooms: form.bedrooms,
          bathrooms: form.bathrooms,
          price: Number(form.price),
          currency: form.currency,
          diasporaFriendly: form.diasporaFriendly,
          escrowRequired: form.escrowRequired,
          solarInstalled: form.solarInstalled,
          backupPower: form.backupPower,
          furnished: form.furnished,
          internetAvailable: form.internetAvailable,
          securityFeatures: form.securityFeatures,
          parkingAvailable: form.parkingAvailable,
          petsAllowed: form.petsAllowed,
          virtualTourUrl: form.virtualTourUrl || undefined,
        },
        token
      );
      if (photoFiles.length > 0) {
        const formData = new FormData();
        photoFiles.forEach((file) => formData.append("files", file));
        await propertiesApi.uploadPhotos(property.id, formData, token);
        setPhotoFiles([]);
      }
      setProperty(updated);
      setSuccess(true);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to update listing.");
    } finally {
      setSubmitting(false);
    }
  };

  if (authLoading || loading) {
    return <div className="flex-1 flex items-center justify-center text-gray-400">Loading...</div>;
  }

  if (loadError || !property) {
    return (
      <div className="max-w-2xl mx-auto px-4 py-16 text-center">
        <AlertCircle className="w-10 h-10 text-red-400 mx-auto mb-3" />
        <p className="text-gray-700 font-medium">{loadError || "Listing not found."}</p>
        <Link href="/properties" className="text-forest-600 hover:underline mt-4 inline-block">
          ← Back to listings
        </Link>
      </div>
    );
  }

  return (
    <div className="max-w-3xl mx-auto px-4 py-8">
      <div className="mb-8">
        <Link href={`/properties/${property.id}`} className="text-sm text-forest-600 hover:underline flex items-center gap-1 mb-4">
          ← Back to listing
        </Link>
        <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
          <Home className="w-7 h-7 text-forest-600" /> Edit Listing
        </h1>
        <p className="text-gray-500 mt-1">Update your listing's details or photos.</p>
      </div>

      {billing && (
        <div
          className={`text-sm px-4 py-3 rounded-xl mb-5 flex items-center gap-2 border ${
            billing.status === "PAST_DUE"
              ? "bg-red-50 border-red-200 text-red-700"
              : "bg-gray-50 border-gray-200 text-gray-600"
          }`}
        >
          {billing.status === "PAST_DUE" ? <AlertCircle className="w-4 h-4 shrink-0" /> : <CheckCircle className="w-4 h-4 shrink-0" />}
          {billing.status === "PAST_DUE" ? (
            <span>
              Payment failed for this listing&apos;s ${billing.monthlyFee.toFixed(2)}/month fee. It will be deactivated if not resolved soon.
            </span>
          ) : (
            <span>
              Listing fee: ${billing.monthlyFee.toFixed(2)}/month
              {billing.currentPeriodEnd && ` · next charge ${new Date(billing.currentPeriodEnd).toLocaleDateString()}`}
            </span>
          )}
        </div>
      )}

      {success && (
        <div className="bg-forest-50 border border-forest-200 text-forest-700 text-sm px-4 py-3 rounded-xl mb-5 flex items-center gap-2">
          <CheckCircle className="w-4 h-4 shrink-0" /> Listing updated.
        </div>
      )}
      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-5 flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0" /> {error}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-6">
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm space-y-4">
          <h2 className="font-bold text-gray-900">Property Details</h2>
          <div>
            <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5">Title *</label>
            <input
              type="text"
              value={form.title}
              onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
              required
              className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm outline-none focus:border-forest-500 focus:ring-2 focus:ring-forest-100"
            />
          </div>
          <div>
            <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5">Description</label>
            <textarea
              value={form.description}
              onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              rows={4}
              className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm outline-none focus:border-forest-500 focus:ring-2 focus:ring-forest-100 resize-none"
            />
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <div>
              <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5">Bedrooms</label>
              <select
                value={form.bedrooms}
                onChange={(e) => setForm((f) => ({ ...f, bedrooms: Number(e.target.value) }))}
                className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm outline-none focus:border-forest-500 bg-white"
              >
                {[0, 1, 2, 3, 4, 5, 6].map((n) => (
                  <option key={n} value={n}>{n}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5">Bathrooms</label>
              <select
                value={form.bathrooms}
                onChange={(e) => setForm((f) => ({ ...f, bathrooms: Number(e.target.value) }))}
                className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm outline-none focus:border-forest-500 bg-white"
              >
                {[0, 1, 2, 3, 4, 5].map((n) => (
                  <option key={n} value={n}>{n}</option>
                ))}
              </select>
            </div>
            <div className="sm:col-span-2">
              <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5">
                Price ({property.listingType === "RENT" ? "/mo" : property.listingType === "SHORT_STAY" ? "/night" : ""}) *
              </label>
              <input
                type="number"
                min={0}
                value={form.price}
                onChange={(e) => setForm((f) => ({ ...f, price: e.target.value }))}
                required
                className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm outline-none focus:border-forest-500 focus:ring-2 focus:ring-forest-100"
              />
            </div>
          </div>
        </div>

        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm space-y-4">
          <h2 className="font-bold text-gray-900">Location</h2>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5">City *</label>
              <input
                type="text"
                list="edit-city-options"
                value={form.city}
                onChange={(e) => setForm((f) => ({ ...f, city: e.target.value, suburb: "" }))}
                required
                className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm outline-none focus:border-forest-500 focus:ring-2 focus:ring-forest-100"
              />
              <datalist id="edit-city-options">
                {ZIMBABWE_CITIES.map((c) => (
                  <option key={c} value={c} />
                ))}
              </datalist>
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5">Suburb *</label>
              <input
                type="text"
                list="edit-suburb-options"
                value={form.suburb}
                onChange={(e) => setForm((f) => ({ ...f, suburb: e.target.value }))}
                required
                className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm outline-none focus:border-forest-500 focus:ring-2 focus:ring-forest-100"
              />
              <datalist id="edit-suburb-options">
                {suburbs.map((s) => (
                  <option key={s} value={s} />
                ))}
              </datalist>
            </div>
          </div>
          <div>
            <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5">Street Address</label>
            <input
              type="text"
              value={form.address}
              onChange={(e) => setForm((f) => ({ ...f, address: e.target.value }))}
              className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm outline-none focus:border-forest-500 focus:ring-2 focus:ring-forest-100"
            />
          </div>
        </div>

        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
          <h2 className="font-bold text-gray-900 mb-4 flex items-center gap-2">
            <ImageIcon className="w-5 h-5 text-forest-600" /> Photos
          </h2>

          {property.photoDetails.length > 0 && (
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 mb-4">
              {property.photoDetails.map((photo) => (
                <div key={photo.id} className="relative group aspect-video bg-gray-100 rounded-xl overflow-hidden">
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img src={photo.url} alt="" className="w-full h-full object-cover" />
                  <button
                    type="button"
                    onClick={() => handleDeletePhoto(photo.id)}
                    disabled={deletingPhotoId === photo.id}
                    className="absolute top-1 right-1 w-6 h-6 bg-black/60 hover:bg-black/80 text-white rounded-full flex items-center justify-center disabled:opacity-50"
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                </div>
              ))}
            </div>
          )}

          <input
            ref={fileInputRef}
            type="file"
            multiple
            accept="image/png,image/jpeg,image/webp"
            onChange={(e) => { addFiles(e.target.files); e.target.value = ""; }}
            className="hidden"
          />
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            className="w-full flex flex-col items-center justify-center gap-1.5 border-2 border-dashed border-gray-300 hover:border-forest-400 hover:bg-forest-50/50 rounded-xl px-4 py-6 text-sm font-semibold text-gray-600 hover:text-forest-700 transition-colors"
          >
            <Upload className="w-5 h-5" />
            Add more photos
            <span className="text-xs font-normal text-gray-400">PNG, JPEG or WEBP — click to choose files</span>
          </button>

          {photoFiles.length > 0 && (
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 mt-4">
              {photoFiles.map((file, i) => (
                <div key={i} className="relative group aspect-video bg-gray-100 rounded-xl overflow-hidden">
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img src={filePreviews[i]} alt="" className="w-full h-full object-cover" />
                  <button
                    type="button"
                    onClick={() => removeFile(i)}
                    className="absolute top-1 right-1 w-6 h-6 bg-black/60 hover:bg-black/80 text-white rounded-full flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity"
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                  <span className="absolute bottom-1 left-1 right-1 text-[10px] text-white bg-black/50 rounded px-1.5 py-0.5 truncate">
                    {file.name} (new)
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
          <h2 className="font-bold text-gray-900 mb-4">Features</h2>
          <div className="flex flex-wrap gap-4">
            {([
              ["escrowRequired", "Escrow Protected Deposit"],
              ["diasporaFriendly", "Diaspora Friendly"],
              ["solarInstalled", "Solar Installed"],
              ["backupPower", "Backup Power"],
              ["furnished", "Furnished"],
              ["internetAvailable", "Internet / Fibre"],
              ["securityFeatures", "Security (guarded/gated)"],
              ["parkingAvailable", "Parking"],
              ["petsAllowed", "Pets Allowed"],
            ] as const).map(([key, label]) => (
              <label key={key} className="flex items-center gap-3 cursor-pointer group">
                <div
                  onClick={() => setForm((f) => ({ ...f, [key]: !f[key] }))}
                  className={`w-12 h-6 rounded-full transition-colors relative ${form[key] ? "bg-forest-600" : "bg-gray-200"}`}
                >
                  <div className={`absolute top-1 w-4 h-4 bg-white rounded-full shadow transition-transform ${form[key] ? "translate-x-7" : "translate-x-1"}`} />
                </div>
                <span className="text-sm font-medium text-gray-700">{label}</span>
              </label>
            ))}
          </div>
          <div className="mt-4">
            <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5">Virtual Tour URL (optional)</label>
            <input
              type="url"
              value={form.virtualTourUrl}
              onChange={(e) => setForm((f) => ({ ...f, virtualTourUrl: e.target.value }))}
              className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-forest-500"
            />
          </div>
        </div>

        <div className="flex gap-4">
          <button
            type="submit"
            disabled={submitting}
            className="flex-1 sm:flex-none bg-forest-600 hover:bg-forest-700 disabled:opacity-60 text-white font-bold py-3.5 px-10 rounded-xl transition-colors flex items-center justify-center gap-2"
          >
            {submitting ? "Saving..." : "Save Changes"}
          </button>
          <Link
            href={`/properties/${property.id}`}
            className="border border-gray-200 text-gray-700 hover:bg-gray-50 font-semibold py-3.5 px-6 rounded-xl transition-colors"
          >
            Cancel
          </Link>
        </div>
      </form>
    </div>
  );
}