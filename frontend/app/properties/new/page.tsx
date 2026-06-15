"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/lib/auth";
import { properties as propertiesApi } from "@/lib/api";
import { Home, Plus, CheckCircle, AlertCircle, Image as ImageIcon, X } from "lucide-react";

const LISTING_TYPES = [
  { value: "RENT", label: "For Rent", desc: "Monthly rental property" },
  { value: "SALE", label: "For Sale", desc: "Buy outright" },
  { value: "SHORT_STAY", label: "Short Stay", desc: "Nightly / holiday rental" },
] as const;

const ZIMBABWE_SUBURBS: Record<string, string[]> = {
  Harare: ["Borrowdale", "Avondale", "Mount Pleasant", "Highlands", "Greendale", "Hatfield", "Mabelreign", "Strathaven", "Msasa", "Dzivarasekwa"],
  Bulawayo: ["Suburbs", "Hillside", "Burnside", "Selborne Park", "Matsheumhlope"],
  Mutare: ["Greenside", "Palmerstone", "Morningside"],
  Gweru: ["Ridgemont", "Nehosho", "Mkoba"],
};

export default function NewPropertyPage() {
  const { user, token, loading } = useAuth();
  const router = useRouter();

  const [form, setForm] = useState({
    title: "",
    description: "",
    listingType: "RENT" as "RENT" | "SALE" | "SHORT_STAY",
    city: "Harare",
    suburb: "",
    address: "",
    country: "Zimbabwe",
    bedrooms: 2,
    bathrooms: 1,
    price: "",
    currency: "USD",
    latitude: "",
    longitude: "",
    diasporaFriendly: false,
    escrowRequired: true,
    photoUrls: [] as string[],
  });

  const [photoInput, setPhotoInput] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState(false);
  const [createdId, setCreatedId] = useState<number | null>(null);

  const suburbs = ZIMBABWE_SUBURBS[form.city] ?? [];

  const addPhoto = () => {
    const url = photoInput.trim();
    if (!url) return;
    setForm((f) => ({ ...f, photoUrls: [...f.photoUrls, url] }));
    setPhotoInput("");
  };

  const removePhoto = (i: number) => {
    setForm((f) => ({ ...f, photoUrls: f.photoUrls.filter((_, idx) => idx !== i) }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user || !token) {
      router.push("/login?redirect=/properties/new");
      return;
    }
    if (!form.title || !form.city || !form.price) {
      setError("Title, city, and price are required.");
      return;
    }
    setError("");
    setSubmitting(true);
    try {
      const property = await propertiesApi.create(
        {
          title: form.title,
          description: form.description || undefined,
          listingType: form.listingType,
          city: form.city,
          suburb: form.suburb,
          address: form.address || undefined,
          country: form.country,
          bedrooms: form.bedrooms,
          bathrooms: form.bathrooms,
          price: Number(form.price),
          currency: form.currency,
          latitude: form.latitude ? Number(form.latitude) : undefined,
          longitude: form.longitude ? Number(form.longitude) : undefined,
          diasporaFriendly: form.diasporaFriendly,
          escrowRequired: form.escrowRequired,
          landlordId: user.id,
          photoUrls: form.photoUrls.length > 0 ? form.photoUrls : undefined,
        },
        token
      );
      setCreatedId(property.id);
      setSuccess(true);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to create listing.");
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return <div className="flex-1 flex items-center justify-center text-gray-400">Loading...</div>;
  }

  if (success && createdId) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
        <div className="bg-white rounded-2xl border border-gray-200 p-10 shadow-sm text-center max-w-md">
          <CheckCircle className="w-16 h-16 text-green-500 mx-auto mb-4" />
          <h2 className="text-2xl font-bold text-gray-900 mb-2">Property Listed!</h2>
          <p className="text-gray-500 mb-6">
            Your property has been created. An agent or admin can verify it to give it a Verified badge.
          </p>
          <div className="flex flex-col sm:flex-row gap-3 justify-center">
            <Link
              href={`/properties/${createdId}`}
              className="bg-blue-600 hover:bg-blue-700 text-white font-bold px-6 py-3 rounded-xl transition-colors"
            >
              View Listing
            </Link>
            <button
              onClick={() => { setSuccess(false); setCreatedId(null); setForm({ title: "", description: "", listingType: "RENT", city: "Harare", suburb: "", address: "", country: "Zimbabwe", bedrooms: 2, bathrooms: 1, price: "", currency: "USD", latitude: "", longitude: "", diasporaFriendly: false, escrowRequired: true, photoUrls: [] }); }}
              className="border border-gray-200 text-gray-700 hover:bg-gray-50 font-semibold px-6 py-3 rounded-xl transition-colors"
            >
              List Another
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-3xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="mb-8">
        <Link href="/properties" className="text-sm text-blue-600 hover:underline flex items-center gap-1 mb-4">
          ← Back to listings
        </Link>
        <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
          <Plus className="w-7 h-7 text-blue-600" /> List a Property
        </h1>
        <p className="text-gray-500 mt-1">Fill in the details below. Your listing goes live immediately.</p>
      </div>

      {!user && (
        <div className="bg-amber-50 border border-amber-200 rounded-2xl p-5 mb-6">
          <p className="text-amber-800 text-sm font-medium">
            You need to be signed in to list a property.{" "}
            <Link href="/login?redirect=/properties/new" className="font-bold underline">Sign in</Link>
            {" "}or{" "}
            <Link href="/register?redirect=/properties/new" className="font-bold underline">create an account</Link>.
          </p>
        </div>
      )}

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-5 flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0" /> {error}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-6">
        {/* Listing type */}
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
          <h2 className="font-bold text-gray-900 mb-4">Listing Type</h2>
          <div className="grid grid-cols-3 gap-3">
            {LISTING_TYPES.map((t) => (
              <button
                key={t.value}
                type="button"
                onClick={() => setForm((f) => ({ ...f, listingType: t.value }))}
                className={`p-4 rounded-xl border-2 text-left transition-all ${
                  form.listingType === t.value
                    ? "border-blue-600 bg-blue-50"
                    : "border-gray-200 hover:border-gray-300"
                }`}
              >
                <p className={`font-bold text-sm ${form.listingType === t.value ? "text-blue-700" : "text-gray-900"}`}>{t.label}</p>
                <p className="text-xs text-gray-500 mt-0.5">{t.desc}</p>
              </button>
            ))}
          </div>
        </div>

        {/* Basic info */}
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm space-y-4">
          <h2 className="font-bold text-gray-900">Property Details</h2>

          <div>
            <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5">Title *</label>
            <input
              type="text"
              value={form.title}
              onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
              required
              placeholder="e.g. Borrowdale Garden Apartment"
              className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5">Description</label>
            <textarea
              value={form.description}
              onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              rows={4}
              placeholder="Describe the property, features, nearby amenities..."
              className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100 resize-none"
            />
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <div>
              <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5">Bedrooms</label>
              <select
                value={form.bedrooms}
                onChange={(e) => setForm((f) => ({ ...f, bedrooms: Number(e.target.value) }))}
                className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm outline-none focus:border-blue-500 bg-white"
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
                className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm outline-none focus:border-blue-500 bg-white"
              >
                {[0, 1, 2, 3, 4, 5].map((n) => (
                  <option key={n} value={n}>{n}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5">
                Price ({form.listingType === "RENT" ? "/mo" : form.listingType === "SHORT_STAY" ? "/night" : ""}) *
              </label>
              <input
                type="number"
                min={0}
                value={form.price}
                onChange={(e) => setForm((f) => ({ ...f, price: e.target.value }))}
                required
                placeholder="e.g. 550"
                className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5">Currency</label>
              <select
                value={form.currency}
                onChange={(e) => setForm((f) => ({ ...f, currency: e.target.value }))}
                className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm outline-none focus:border-blue-500 bg-white"
              >
                <option value="USD">USD</option>
                <option value="ZWL">ZWL</option>
                <option value="GBP">GBP</option>
                <option value="ZAR">ZAR</option>
              </select>
            </div>
          </div>
        </div>

        {/* Location */}
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm space-y-4">
          <h2 className="font-bold text-gray-900">Location</h2>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5">City *</label>
              <select
                value={form.city}
                onChange={(e) => setForm((f) => ({ ...f, city: e.target.value, suburb: "" }))}
                className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm outline-none focus:border-blue-500 bg-white"
              >
                {Object.keys(ZIMBABWE_SUBURBS).map((c) => (
                  <option key={c} value={c}>{c}</option>
                ))}
                <option value="Other">Other</option>
              </select>
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5">Suburb *</label>
              {suburbs.length > 0 ? (
                <select
                  value={form.suburb}
                  onChange={(e) => setForm((f) => ({ ...f, suburb: e.target.value }))}
                  required
                  className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm outline-none focus:border-blue-500 bg-white"
                >
                  <option value="">Select suburb...</option>
                  {suburbs.map((s) => <option key={s} value={s}>{s}</option>)}
                  <option value="Other">Other</option>
                </select>
              ) : (
                <input
                  type="text"
                  value={form.suburb}
                  onChange={(e) => setForm((f) => ({ ...f, suburb: e.target.value }))}
                  required
                  placeholder="Suburb name"
                  className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                />
              )}
            </div>
          </div>
          <div>
            <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5">Street Address</label>
            <input
              type="text"
              value={form.address}
              onChange={(e) => setForm((f) => ({ ...f, address: e.target.value }))}
              placeholder="e.g. 15 Borrowdale Road"
              className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5">Latitude (optional)</label>
              <input
                type="number"
                step="any"
                value={form.latitude}
                onChange={(e) => setForm((f) => ({ ...f, latitude: e.target.value }))}
                placeholder="e.g. -17.7834"
                className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5">Longitude (optional)</label>
              <input
                type="number"
                step="any"
                value={form.longitude}
                onChange={(e) => setForm((f) => ({ ...f, longitude: e.target.value }))}
                placeholder="e.g. 31.0672"
                className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
              />
            </div>
          </div>
          <p className="text-xs text-gray-400">
            If you leave coordinates blank, the backend auto-fills common Zimbabwe suburb coordinates.
          </p>
        </div>

        {/* Photos */}
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
          <h2 className="font-bold text-gray-900 mb-4 flex items-center gap-2">
            <ImageIcon className="w-5 h-5 text-blue-600" /> Photos
          </h2>
          <p className="text-xs text-gray-500 mb-3">Paste image URLs (Unsplash, Imgur, etc.). Photos make your listing stand out.</p>

          <div className="flex gap-2 mb-4">
            <input
              type="url"
              value={photoInput}
              onChange={(e) => setPhotoInput(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); addPhoto(); } }}
              placeholder="https://images.unsplash.com/photo-..."
              className="flex-1 border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
            />
            <button
              type="button"
              onClick={addPhoto}
              className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2.5 rounded-xl text-sm font-semibold transition-colors"
            >
              Add
            </button>
          </div>

          {form.photoUrls.length > 0 && (
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
              {form.photoUrls.map((url, i) => (
                <div key={i} className="relative group aspect-video bg-gray-100 rounded-xl overflow-hidden">
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img src={url} alt="" className="w-full h-full object-cover" />
                  <button
                    type="button"
                    onClick={() => removePhoto(i)}
                    className="absolute top-1 right-1 w-6 h-6 bg-black/60 hover:bg-black/80 text-white rounded-full flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity"
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                </div>
              ))}
            </div>
          )}

          <div className="mt-4">
            <p className="text-xs text-gray-400 mb-2">Quick picks from Unsplash:</p>
            <div className="flex flex-wrap gap-2">
              {[
                { label: "Apartment", url: "https://images.unsplash.com/photo-1560448204-e02f11c3d0e2?w=800" },
                { label: "House", url: "https://images.unsplash.com/photo-1564013799919-ab600027ffc6?w=800" },
                { label: "Villa", url: "https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?w=800" },
                { label: "Office", url: "https://images.unsplash.com/photo-1497366754035-f200968a6e72?w=800" },
              ].map(({ label, url }) => (
                <button
                  key={label}
                  type="button"
                  onClick={() => setForm((f) => ({ ...f, photoUrls: [...f.photoUrls, url] }))}
                  className="text-xs bg-gray-100 hover:bg-gray-200 text-gray-700 px-3 py-1.5 rounded-lg transition-colors"
                >
                  + {label}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Features */}
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
          <h2 className="font-bold text-gray-900 mb-4">Features</h2>
          <div className="flex flex-wrap gap-4">
            <label className="flex items-center gap-3 cursor-pointer group">
              <div
                onClick={() => setForm((f) => ({ ...f, escrowRequired: !f.escrowRequired }))}
                className={`w-12 h-6 rounded-full transition-colors relative ${form.escrowRequired ? "bg-blue-600" : "bg-gray-200"}`}
              >
                <div className={`absolute top-1 w-4 h-4 bg-white rounded-full shadow transition-transform ${form.escrowRequired ? "translate-x-7" : "translate-x-1"}`} />
              </div>
              <span className="text-sm font-medium text-gray-700">Escrow Protected Deposit</span>
            </label>
            <label className="flex items-center gap-3 cursor-pointer group">
              <div
                onClick={() => setForm((f) => ({ ...f, diasporaFriendly: !f.diasporaFriendly }))}
                className={`w-12 h-6 rounded-full transition-colors relative ${form.diasporaFriendly ? "bg-amber-500" : "bg-gray-200"}`}
              >
                <div className={`absolute top-1 w-4 h-4 bg-white rounded-full shadow transition-transform ${form.diasporaFriendly ? "translate-x-7" : "translate-x-1"}`} />
              </div>
              <span className="text-sm font-medium text-gray-700">Diaspora Friendly (remotely manageable)</span>
            </label>
          </div>
        </div>

        {/* Submit */}
        <div className="flex gap-4">
          <button
            type="submit"
            disabled={submitting || !user}
            className="flex-1 sm:flex-none bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white font-bold py-3.5 px-10 rounded-xl transition-colors flex items-center justify-center gap-2"
          >
            <Home className="w-5 h-5" />
            {submitting ? "Publishing..." : "Publish Listing"}
          </button>
          <Link
            href="/properties"
            className="border border-gray-200 text-gray-700 hover:bg-gray-50 font-semibold py-3.5 px-6 rounded-xl transition-colors"
          >
            Cancel
          </Link>
        </div>
      </form>
    </div>
  );
}
