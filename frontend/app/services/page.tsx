"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { vendors as vendorsApi, serviceBookings as serviceBookingsApi } from "@/lib/api";
import type { Vendor, VendorCategory, ServiceBooking } from "@/lib/types";
import {
  Wrench,
  Truck,
  Sparkles,
  Zap,
  Shield,
  Scale,
  Sun,
  Plug,
  Sofa,
  MoreHorizontal,
  Star,
  Phone,
  Mail,
  MapPin,
  AlertCircle,
  CheckCircle,
  X,
} from "lucide-react";

const CATEGORY_META: Record<VendorCategory, { label: string; icon: typeof Wrench }> = {
  MOVING: { label: "Movers", icon: Truck },
  CLEANING: { label: "Cleaning", icon: Sparkles },
  PLUMBING: { label: "Plumbing", icon: Wrench },
  ELECTRICAL: { label: "Electrical", icon: Zap },
  INSURANCE: { label: "Insurance", icon: Shield },
  LEGAL: { label: "Legal Services", icon: Scale },
  SOLAR: { label: "Solar", icon: Sun },
  UTILITIES: { label: "Utilities", icon: Plug },
  FURNITURE: { label: "Furniture", icon: Sofa },
  OTHER: { label: "Other", icon: MoreHorizontal },
};

function ServicesMarketplaceContent() {
  const { user, token } = useAuth();
  const searchParams = useSearchParams();
  const [category, setCategory] = useState<VendorCategory | "">(
    (searchParams.get("category") as VendorCategory) ?? ""
  );
  const [vendorList, setVendorList] = useState<Vendor[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [bookingVendor, setBookingVendor] = useState<Vendor | null>(null);
  const [bookingDate, setBookingDate] = useState("");
  const [bookingNotes, setBookingNotes] = useState("");
  const [bookingSubmitting, setBookingSubmitting] = useState(false);
  const [bookingError, setBookingError] = useState("");
  const [bookingSuccess, setBookingSuccess] = useState(false);

  const [myBookings, setMyBookings] = useState<ServiceBooking[]>([]);

  useEffect(() => {
    setLoading(true);
    vendorsApi.list(category ? { category } : {})
      .then(setVendorList)
      .catch(() => setError("Failed to load service providers."))
      .finally(() => setLoading(false));
  }, [category]);

  useEffect(() => {
    if (user && token) {
      serviceBookingsApi.listByRequester(user.id, token).then(setMyBookings).catch(() => {});
    }
  }, [user, token]);

  const openBooking = (vendor: Vendor) => {
    setBookingVendor(vendor);
    setBookingDate("");
    setBookingNotes("");
    setBookingError("");
    setBookingSuccess(false);
  };

  const submitBooking = async () => {
    if (!bookingVendor || !token) return;
    setBookingSubmitting(true);
    setBookingError("");
    try {
      const booking = await serviceBookingsApi.create(
        { vendorId: bookingVendor.id, preferredDate: bookingDate || undefined, notes: bookingNotes || undefined },
        token
      );
      setMyBookings((b) => [booking, ...b]);
      setBookingSuccess(true);
    } catch (err: unknown) {
      setBookingError(err instanceof Error ? err.message : "Failed to book this service.");
    } finally {
      setBookingSubmitting(false);
    }
  };

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
          <Wrench className="w-7 h-7 text-blue-600" /> Home Services Marketplace
        </h1>
        <p className="text-gray-500 mt-1">
          Homestead-vetted movers, cleaners, tradespeople, insurers, lawyers, and more — book directly.
        </p>
      </div>

      {/* Category filter */}
      <div className="flex flex-wrap gap-2 mb-6">
        <button
          onClick={() => setCategory("")}
          className={`text-sm font-medium px-3 py-1.5 rounded-full border transition-colors ${
            category === "" ? "bg-blue-600 text-white border-blue-600" : "border-gray-200 text-gray-600 hover:border-gray-400"
          }`}
        >
          All
        </button>
        {(Object.keys(CATEGORY_META) as VendorCategory[]).map((c) => {
          const meta = CATEGORY_META[c];
          const Icon = meta.icon;
          return (
            <button
              key={c}
              onClick={() => setCategory(c)}
              className={`flex items-center gap-1.5 text-sm font-medium px-3 py-1.5 rounded-full border transition-colors ${
                category === c ? "bg-blue-600 text-white border-blue-600" : "border-gray-200 text-gray-600 hover:border-gray-400"
              }`}
            >
              <Icon className="w-3.5 h-3.5" /> {meta.label}
            </button>
          );
        })}
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-4 flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0" /> {error}
        </div>
      )}

      {loading ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="h-40 bg-gray-100 rounded-2xl animate-pulse" />
          ))}
        </div>
      ) : vendorList.length === 0 ? (
        <div className="text-center py-16 text-gray-500">
          <Wrench className="w-12 h-12 mx-auto mb-4 opacity-30" />
          <p className="text-lg font-medium">No service providers listed yet</p>
          <p className="text-sm mt-1">Check back soon, or try a different category.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {vendorList.map((vendor) => {
            const meta = CATEGORY_META[vendor.category];
            const Icon = meta.icon;
            return (
              <div key={vendor.id} className="bg-white border border-gray-200 rounded-2xl p-5 shadow-sm flex flex-col">
                <div className="flex items-start justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <div className="w-9 h-9 bg-blue-50 rounded-lg flex items-center justify-center">
                      <Icon className="w-4.5 h-4.5 text-blue-600" />
                    </div>
                    <div>
                      <p className="font-bold text-gray-900 text-sm leading-tight">{vendor.businessName}</p>
                      <p className="text-xs text-gray-500">{meta.label}</p>
                    </div>
                  </div>
                  {vendor.verified && (
                    <span className="flex items-center gap-1 bg-green-50 text-green-700 text-xs font-bold px-2 py-1 rounded-full">
                      <CheckCircle className="w-3 h-3" /> Verified
                    </span>
                  )}
                </div>

                {vendor.description && <p className="text-sm text-gray-600 mb-3 flex-1">{vendor.description}</p>}

                <div className="space-y-1 mb-3 text-xs text-gray-500">
                  {vendor.city && (
                    <p className="flex items-center gap-1.5"><MapPin className="w-3 h-3" /> {vendor.city}</p>
                  )}
                  {vendor.phone && (
                    <p className="flex items-center gap-1.5"><Phone className="w-3 h-3" /> {vendor.phone}</p>
                  )}
                  {vendor.email && (
                    <p className="flex items-center gap-1.5"><Mail className="w-3 h-3" /> {vendor.email}</p>
                  )}
                  {vendor.averageRating != null && (
                    <p className="flex items-center gap-1.5 text-amber-600">
                      <Star className="w-3 h-3 fill-current" /> {vendor.averageRating.toFixed(1)} ({vendor.ratingCount} review{vendor.ratingCount === 1 ? "" : "s"})
                    </p>
                  )}
                </div>

                <button
                  onClick={() => openBooking(vendor)}
                  className="w-full bg-blue-600 hover:bg-blue-700 text-white text-sm font-semibold py-2 rounded-xl transition-colors"
                >
                  Book Now
                </button>
              </div>
            );
          })}
        </div>
      )}

      {/* Booking modal */}
      {bookingVendor && (
        <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl">
            {bookingSuccess ? (
              <div className="text-center">
                <CheckCircle className="w-12 h-12 text-green-500 mx-auto mb-3" />
                <p className="font-bold text-gray-900 text-lg mb-1">Booking requested!</p>
                <p className="text-sm text-gray-500 mb-4">{bookingVendor.businessName} will follow up with you shortly.</p>
                <button
                  onClick={() => setBookingVendor(null)}
                  className="w-full bg-blue-600 hover:bg-blue-700 text-white font-semibold py-2.5 rounded-xl transition-colors"
                >
                  Done
                </button>
              </div>
            ) : (
              <>
                <div className="flex items-center justify-between mb-4">
                  <h3 className="font-bold text-gray-900 text-lg">Book {bookingVendor.businessName}</h3>
                  <button onClick={() => setBookingVendor(null)}>
                    <X className="w-5 h-5 text-gray-400" />
                  </button>
                </div>
                {!user ? (
                  <p className="text-sm text-gray-500">
                    Please <a href="/login?redirect=/services" className="text-blue-600 underline">sign in</a> to book a service.
                  </p>
                ) : (
                  <div className="space-y-3">
                    <input
                      type="date"
                      value={bookingDate}
                      onChange={(e) => setBookingDate(e.target.value)}
                      className="w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm outline-none focus:border-blue-500"
                    />
                    <textarea
                      value={bookingNotes}
                      onChange={(e) => setBookingNotes(e.target.value)}
                      rows={3}
                      placeholder="Describe what you need (optional)"
                      className="w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm outline-none focus:border-blue-500 resize-none"
                    />
                    {bookingError && <p className="text-xs text-red-600">{bookingError}</p>}
                    <button
                      onClick={submitBooking}
                      disabled={bookingSubmitting}
                      className="w-full bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white font-semibold py-2.5 rounded-xl transition-colors"
                    >
                      {bookingSubmitting ? "Booking…" : "Request Booking"}
                    </button>
                  </div>
                )}
              </>
            )}
          </div>
        </div>
      )}

      {/* My bookings */}
      {user && myBookings.length > 0 && (
        <div className="mt-10">
          <h2 className="text-lg font-bold text-gray-900 mb-4">My Bookings</h2>
          <div className="space-y-3">
            {myBookings.map((b) => (
              <div key={b.id} className="bg-white border border-gray-200 rounded-xl p-4 flex items-center justify-between">
                <div>
                  <p className="text-sm font-semibold text-gray-900">{b.vendorBusinessName}</p>
                  <p className="text-xs text-gray-500">{CATEGORY_META[b.vendorCategory].label}{b.preferredDate ? ` · ${b.preferredDate}` : ""}</p>
                </div>
                <span className="text-xs font-semibold px-2 py-0.5 rounded-full bg-gray-100 text-gray-600">{b.status}</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

export default function ServicesMarketplacePage() {
  return (
    <Suspense fallback={<div className="flex-1 flex items-center justify-center text-gray-400 py-16">Loading...</div>}>
      <ServicesMarketplaceContent />
    </Suspense>
  );
}
