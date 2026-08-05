"use client";

import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/lib/auth";
import { properties as propertiesApi, ai as aiApi } from "@/lib/api";
import { Home, Plus, CheckCircle, AlertCircle, Image as ImageIcon, Upload, X, Sparkles } from "lucide-react";
import { settingsRoleUrl } from "@/lib/roleGate";
import type { UserRole } from "@/lib/types";
import { ZIMBABWE_SUBURBS } from "@/lib/zimbabweLocations";

const LISTING_TYPES = [
  { value: "RENT", label: "For Rent", desc: "Monthly rental property" },
  { value: "SALE", label: "For Sale", desc: "Buy outright" },
  { value: "SHORT_STAY", label: "Short Stay (BnB)", desc: "Nightly / holiday rental" },
] as const;

const LISTING_ROLES: UserRole[] = ["LANDLORD", "AGENT", "DEVELOPER", "PRIVATE"];

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
    solarInstalled: false,
    backupPower: false,
    waterSource: "" as "" | "MUNICIPAL" | "BOREHOLE" | "WELL" | "TANKER" | "OTHER",
    furnished: false,
    internetAvailable: false,
    securityFeatures: false,
    parkingAvailable: false,
    petsAllowed: false,
    virtualTourUrl: "",
    photoUrls: [] as string[],
  });

  const [rentSuggestion, setRentSuggestion] = useState<{
    suggestedPrice: number | null;
    priceRangeLow: number | null;
    priceRangeHigh: number | null;
    comparableCount: number;
    basis: string;
  } | null>(null);
  const [suggestionLoading, setSuggestionLoading] = useState(false);
  const [suggestionError, setSuggestionError] = useState("");

  const handleGetPriceSuggestion = async () => {
    if (!form.city || !form.bedrooms || !token) return;
    setSuggestionError("");
    setSuggestionLoading(true);
    setRentSuggestion(null);
    try {
      const result = await aiApi.rentSuggestion({
        listingType: form.listingType,
        city: form.city,
        suburb: form.suburb || undefined,
        bedrooms: form.bedrooms,
      }, token);
      setRentSuggestion(result);
    } catch (err: unknown) {
      setSuggestionError(err instanceof Error ? err.message : "Could not compute a pricing suggestion.");
    } finally {
      setSuggestionLoading(false);
    }
  };

  const [photoInput, setPhotoInput] = useState("");
  const [photoFiles, setPhotoFiles] = useState<File[]>([]);
  const fileInputRef = useRef<HTMLInputElement>(null);
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

  const addFiles = (files: FileList | null) => {
    if (!files || files.length === 0) return;
    setPhotoFiles((prev) => [...prev, ...Array.from(files)]);
  };

  const removeFile = (i: number) => {
    setPhotoFiles((prev) => prev.filter((_, idx) => idx !== i));
  };

  const filePreviews = useMemo(
    () => photoFiles.map((file) => URL.createObjectURL(file)),
    [photoFiles]
  );

  useEffect(() => {
    return () => filePreviews.forEach((url) => URL.revokeObjectURL(url));
  }, [filePreviews]);

  useEffect(() => {
    if (loading) return;
    if (!user) {
      router.push("/login?redirect=/properties/new");
      return;
    }
    if (!user.roles?.some((r) => LISTING_ROLES.includes(r))) {
      router.push(settingsRoleUrl(LISTING_ROLES, "listing a property needs one of these roles"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user, loading]);

  const handleSubmit = async (e: FormEvent) => {
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
          solarInstalled: form.solarInstalled,
          backupPower: form.backupPower,
          waterSource: form.waterSource || undefined,
          furnished: form.furnished,
          internetAvailable: form.internetAvailable,
          securityFeatures: form.securityFeatures,
          parkingAvailable: form.parkingAvailable,
          petsAllowed: form.petsAllowed,
          virtualTourUrl: form.virtualTourUrl || undefined,
          landlordId: user.id,
          photoUrls: form.photoUrls.length > 0 ? form.photoUrls : undefined,
        },
        token
      );

      if (photoFiles.length > 0) {
        const formData = new FormData();
        photoFiles.forEach((file) => formData.append("files", file));
        await propertiesApi.uploadPhotos(property.id, formData, token);
      }

      setCreatedId(property.id);
      setSuccess(true);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to create listing.");
    } finally {
      setSubmitting(false);
    }
  };

  if (loading || !user || !user.roles?.some((r) => LISTING_ROLES.includes(r))) {
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
              onClick={() => { setSuccess(false); setCreatedId(null); setPhotoFiles([]); setForm({ title: "", description: "", listingType: "RENT", city: "Harare", suburb: "", address: "", country: "Zimbabwe", bedrooms: 2, bathrooms: 1, price: "", currency: "USD", latitude: "", longitude: "", diasporaFriendly: false, escrowRequired: true, solarInstalled: false, backupPower: false, waterSource: "", furnished: false, internetAvailable: false, securityFeatures: false, parkingAvailable: false, petsAllowed: false, virtualTourUrl: "", photoUrls: [] }); }}
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
            <div className="sm:col-span-2">
              <div className="flex items-center justify-between mb-1.5">
                <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide">
                  Price ({form.listingType === "RENT" ? "/mo" : form.listingType === "SHORT_STAY" ? "/night" : ""}) *
                </label>
                <button
                  type="button"
                  onClick={handleGetPriceSuggestion}
                  disabled={suggestionLoading || !form.city}
                  className="flex items-center gap-1 text-xs font-semibold text-purple-600 hover:text-purple-700 disabled:opacity-50"
                >
                  <Sparkles className="w-3.5 h-3.5" />
                  {suggestionLoading ? "Checking market…" : "Suggest a price"}
                </button>
              </div>
              <input
                type="number"
                min={0}
                value={form.price}
                onChange={(e) => setForm((f) => ({ ...f, price: e.target.value }))}
                required
                placeholder="e.g. 550"
                className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
              />
              {suggestionError && <p className="text-xs text-red-600 mt-1.5">{suggestionError}</p>}
              {rentSuggestion && (
                <div className="mt-2 bg-purple-50 border border-purple-100 rounded-xl px-3.5 py-2.5 text-xs text-purple-900">
                  {rentSuggestion.suggestedPrice != null ? (
                    <>
                      <p className="font-semibold">
                        Suggested: {form.currency} {rentSuggestion.suggestedPrice}
                        {" "}(range {rentSuggestion.priceRangeLow}–{rentSuggestion.priceRangeHigh})
                      </p>
                      <p className="text-purple-700 mt-0.5">
                        Based on {rentSuggestion.comparableCount} comparable listing{rentSuggestion.comparableCount === 1 ? "" : "s"}: {rentSuggestion.basis}
                      </p>
                      <button
                        type="button"
                        onClick={() => setForm((f) => ({ ...f, price: String(rentSuggestion.suggestedPrice) }))}
                        className="mt-1.5 text-purple-700 font-semibold underline"
                      >
                        Use this price
                      </button>
                    </>
                  ) : (
                    <p>{rentSuggestion.basis}</p>
                  )}
                </div>
              )}
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
          <p className="text-xs text-gray-500 mb-3">Upload photos from your device, or paste image URLs. Photos make your listing stand out.</p>

          {/* Upload from device */}
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
            className="w-full flex flex-col items-center justify-center gap-1.5 border-2 border-dashed border-gray-300 hover:border-blue-400 hover:bg-blue-50/50 rounded-xl px-4 py-6 text-sm font-semibold text-gray-600 hover:text-blue-700 transition-colors mb-4"
          >
            <Upload className="w-5 h-5" />
            Upload photos
            <span className="text-xs font-normal text-gray-400">PNG, JPEG or WEBP — click to choose files</span>
          </button>

          {photoFiles.length > 0 && (
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 mb-4">
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
                    {file.name}
                  </span>
                </div>
              ))}
            </div>
          )}

          {/* Or paste a URL */}
          <div className="flex gap-2 mb-4">
            <input
              type="url"
              value={photoInput}
              onChange={(e) => setPhotoInput(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); addPhoto(); } }}
              placeholder="Or paste an image URL..."
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
            <label className="flex items-center gap-3 cursor-pointer group">
              <div
                onClick={() => setForm((f) => ({ ...f, solarInstalled: !f.solarInstalled }))}
                className={`w-12 h-6 rounded-full transition-colors relative ${form.solarInstalled ? "bg-green-600" : "bg-gray-200"}`}
              >
                <div className={`absolute top-1 w-4 h-4 bg-white rounded-full shadow transition-transform ${form.solarInstalled ? "translate-x-7" : "translate-x-1"}`} />
              </div>
              <span className="text-sm font-medium text-gray-700">Solar Installed</span>
            </label>
            <label className="flex items-center gap-3 cursor-pointer group">
              <div
                onClick={() => setForm((f) => ({ ...f, backupPower: !f.backupPower }))}
                className={`w-12 h-6 rounded-full transition-colors relative ${form.backupPower ? "bg-green-600" : "bg-gray-200"}`}
              >
                <div className={`absolute top-1 w-4 h-4 bg-white rounded-full shadow transition-transform ${form.backupPower ? "translate-x-7" : "translate-x-1"}`} />
              </div>
              <span className="text-sm font-medium text-gray-700">Backup Power</span>
            </label>
            <label className="flex items-center gap-3 cursor-pointer group">
              <div
                onClick={() => setForm((f) => ({ ...f, furnished: !f.furnished }))}
                className={`w-12 h-6 rounded-full transition-colors relative ${form.furnished ? "bg-blue-600" : "bg-gray-200"}`}
              >
                <div className={`absolute top-1 w-4 h-4 bg-white rounded-full shadow transition-transform ${form.furnished ? "translate-x-7" : "translate-x-1"}`} />
              </div>
              <span className="text-sm font-medium text-gray-700">Furnished</span>
            </label>
            <label className="flex items-center gap-3 cursor-pointer group">
              <div
                onClick={() => setForm((f) => ({ ...f, internetAvailable: !f.internetAvailable }))}
                className={`w-12 h-6 rounded-full transition-colors relative ${form.internetAvailable ? "bg-blue-600" : "bg-gray-200"}`}
              >
                <div className={`absolute top-1 w-4 h-4 bg-white rounded-full shadow transition-transform ${form.internetAvailable ? "translate-x-7" : "translate-x-1"}`} />
              </div>
              <span className="text-sm font-medium text-gray-700">Internet / Fibre</span>
            </label>
            <label className="flex items-center gap-3 cursor-pointer group">
              <div
                onClick={() => setForm((f) => ({ ...f, securityFeatures: !f.securityFeatures }))}
                className={`w-12 h-6 rounded-full transition-colors relative ${form.securityFeatures ? "bg-blue-600" : "bg-gray-200"}`}
              >
                <div className={`absolute top-1 w-4 h-4 bg-white rounded-full shadow transition-transform ${form.securityFeatures ? "translate-x-7" : "translate-x-1"}`} />
              </div>
              <span className="text-sm font-medium text-gray-700">Security (guarded/gated)</span>
            </label>
            <label className="flex items-center gap-3 cursor-pointer group">
              <div
                onClick={() => setForm((f) => ({ ...f, parkingAvailable: !f.parkingAvailable }))}
                className={`w-12 h-6 rounded-full transition-colors relative ${form.parkingAvailable ? "bg-blue-600" : "bg-gray-200"}`}
              >
                <div className={`absolute top-1 w-4 h-4 bg-white rounded-full shadow transition-transform ${form.parkingAvailable ? "translate-x-7" : "translate-x-1"}`} />
              </div>
              <span className="text-sm font-medium text-gray-700">Parking</span>
            </label>
            <label className="flex items-center gap-3 cursor-pointer group">
              <div
                onClick={() => setForm((f) => ({ ...f, petsAllowed: !f.petsAllowed }))}
                className={`w-12 h-6 rounded-full transition-colors relative ${form.petsAllowed ? "bg-blue-600" : "bg-gray-200"}`}
              >
                <div className={`absolute top-1 w-4 h-4 bg-white rounded-full shadow transition-transform ${form.petsAllowed ? "translate-x-7" : "translate-x-1"}`} />
              </div>
              <span className="text-sm font-medium text-gray-700">Pets Allowed</span>
            </label>
          </div>

          <div className="mt-4">
            <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5">Water Source</label>
            <select
              value={form.waterSource}
              onChange={(e) => setForm((f) => ({ ...f, waterSource: e.target.value as typeof f.waterSource }))}
              className="w-full sm:w-64 border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-blue-500 bg-white"
            >
              <option value="">Not specified</option>
              <option value="MUNICIPAL">Municipal</option>
              <option value="BOREHOLE">Borehole</option>
              <option value="WELL">Well</option>
              <option value="TANKER">Tanker delivery</option>
              <option value="OTHER">Other</option>
            </select>
          </div>

          <div className="mt-4">
            <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5">Virtual Tour URL (optional)</label>
            <input
              type="url"
              value={form.virtualTourUrl}
              onChange={(e) => setForm((f) => ({ ...f, virtualTourUrl: e.target.value }))}
              placeholder="YouTube, Vimeo, or Matterport embed link"
              className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-blue-500"
            />
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
