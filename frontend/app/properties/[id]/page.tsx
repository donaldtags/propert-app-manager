"use client";

import { useEffect, useState, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import Image from "next/image";
import Link from "next/link";
import dynamic from "next/dynamic";
import {
  Bed,
  Bath,
  MapPin,
  Shield,
  Globe,
  CheckCircle,
  ChevronLeft,
  ChevronRight,
  Star,
  Phone,
  MessageCircle,
  Heart,
  Share2,
  Calendar,
  User,
  Send,
} from "lucide-react";
import { properties as propertiesApi, ratings as ratingsApi } from "@/lib/api";
import type { Property, Rating } from "@/lib/types";
import { useAuth } from "@/lib/auth";
import { logEvent } from "@/lib/api";

const PropertyMap = dynamic(() => import("@/components/PropertyMap"), {
  ssr: false,
  loading: () => <div className="w-full h-64 bg-gray-100 rounded-xl animate-pulse" />,
});

function formatPrice(price: number, currency: string, listingType: string) {
  const formatted = new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: 0,
  }).format(price);
  if (listingType === "RENT") return `${formatted}/mo`;
  if (listingType === "SHORT_STAY") return `${formatted}/night`;
  return formatted;
}

function getPhotos(property: Property): string[] {
  const photos = [
    ...(property.photoUrls ?? []),
    ...(property.imageUrls ?? []),
    ...(property.photos ?? []),
  ];
  return [...new Set(photos)].filter(Boolean);
}

export default function PropertyDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const { user, token } = useAuth();

  const [property, setProperty] = useState<Property | null>(null);
  const [ratings, setRatings] = useState<Rating[]>([]);
  const [loading, setLoading] = useState(true);
  const [photoIndex, setPhotoIndex] = useState(0);
  const [tab, setTab] = useState<"overview" | "details" | "map">("overview");
  const [saved, setSaved] = useState(false);

  // Inquiry form state
  const [inquiryName, setInquiryName] = useState("");
  const [inquiryEmail, setInquiryEmail] = useState("");
  const [inquiryPhone, setInquiryPhone] = useState("");
  const [inquiryMessage, setInquiryMessage] = useState("");
  const [inquiryLoading, setInquiryLoading] = useState(false);
  const [inquirySent, setInquirySent] = useState(false);
  const [inquiryError, setInquiryError] = useState("");

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      const [prop, ratingData] = await Promise.allSettled([
        propertiesApi.get(Number(id)),
        ratingsApi.listByLandlord(0), // will filter by landlordId once we have it
      ]);
      if (prop.status === "fulfilled") {
        setProperty(prop.value);
        // Now load ratings for this landlord
        const r = await ratingsApi.listByLandlord(prop.value.landlordId);
        setRatings(r);
        logEvent("info", "property_viewed", `/properties/${id}`, "User opened property details", user?.id, { propertyId: Number(id) });
      }
    } catch {
      // handled by empty state
    } finally {
      setLoading(false);
    }
  }, [id, user?.id]);

  useEffect(() => {
    load();
  }, [load]);

  const handleInquiry = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!property) return;
    setInquiryError("");
    setInquiryLoading(true);
    try {
      await propertiesApi.submitInquiry(property.id, {
        name: inquiryName,
        email: inquiryEmail,
        phone: inquiryPhone || undefined,
        message: inquiryMessage,
      });
      setInquirySent(true);
    } catch {
      setInquiryError("Failed to send inquiry. Please try again.");
    } finally {
      setInquiryLoading(false);
    }
  };

  const photos = property ? getPhotos(property) : [];
  const avgRating =
    ratings.length > 0 ? ratings.reduce((s, r) => s + r.rating, 0) / ratings.length : null;

  const handleApply = () => {
    if (!user) {
      router.push(`/login?redirect=/properties/${id}`);
    } else {
      // redirect to lease creation or escrow flow
      router.push(`/escrow?propertyId=${id}`);
    }
  };

  const handleBookViewing = () => {
    if (!user) {
      router.push(`/login?redirect=/properties/${id}`);
    } else {
      router.push(`/messages?propertyId=${id}`);
    }
  };

  if (loading) {
    return (
      <div className="max-w-6xl mx-auto px-4 py-8">
        <div className="h-96 bg-gray-100 rounded-2xl animate-pulse mb-6" />
        <div className="grid grid-cols-3 gap-6">
          <div className="col-span-2 space-y-4">
            <div className="h-8 bg-gray-100 rounded animate-pulse w-3/4" />
            <div className="h-4 bg-gray-100 rounded animate-pulse w-1/2" />
            <div className="h-40 bg-gray-100 rounded animate-pulse" />
          </div>
          <div className="h-64 bg-gray-100 rounded-xl animate-pulse" />
        </div>
      </div>
    );
  }

  if (!property) {
    return (
      <div className="text-center py-32 text-gray-500">
        <MapPin className="w-12 h-12 mx-auto mb-4 opacity-30" />
        <p className="text-xl font-semibold">Property not found</p>
        <Link href="/properties" className="mt-4 inline-block text-blue-600 hover:underline">
          ← Back to listings
        </Link>
      </div>
    );
  }

  return (
    <div className="bg-white min-h-screen">
      {/* Photo gallery */}
      <div className="relative bg-gray-900 h-[50vh] max-h-[560px] group">
        {photos.length > 0 ? (
          <>
            <Image
              src={photos[photoIndex]}
              alt={`${property.title} photo ${photoIndex + 1}`}
              fill
              className="object-cover"
              priority
              unoptimized
            />
            {photos.length > 1 && (
              <>
                <button
                  onClick={() => setPhotoIndex((i) => (i - 1 + photos.length) % photos.length)}
                  className="absolute left-4 top-1/2 -translate-y-1/2 w-10 h-10 bg-white/80 hover:bg-white rounded-full flex items-center justify-center shadow opacity-0 group-hover:opacity-100 transition-opacity"
                >
                  <ChevronLeft className="w-5 h-5" />
                </button>
                <button
                  onClick={() => setPhotoIndex((i) => (i + 1) % photos.length)}
                  className="absolute right-4 top-1/2 -translate-y-1/2 w-10 h-10 bg-white/80 hover:bg-white rounded-full flex items-center justify-center shadow opacity-0 group-hover:opacity-100 transition-opacity"
                >
                  <ChevronRight className="w-5 h-5" />
                </button>
                <div className="absolute bottom-4 left-1/2 -translate-x-1/2 flex gap-1.5">
                  {photos.map((_, i) => (
                    <button
                      key={i}
                      onClick={() => setPhotoIndex(i)}
                      className={`w-2 h-2 rounded-full transition-colors ${i === photoIndex ? "bg-white" : "bg-white/50"}`}
                    />
                  ))}
                </div>
              </>
            )}
          </>
        ) : (
          <div className="absolute inset-0 flex items-center justify-center text-white/30">
            <MapPin className="w-16 h-16" />
          </div>
        )}

        {/* Top actions */}
        <div className="absolute top-4 left-4">
          <button
            onClick={() => router.back()}
            className="flex items-center gap-1 bg-white/80 hover:bg-white px-3 py-2 rounded-lg text-sm font-medium shadow transition-colors"
          >
            <ChevronLeft className="w-4 h-4" /> Back
          </button>
        </div>
        <div className="absolute top-4 right-4 flex gap-2">
          <button
            onClick={() => setSaved(!saved)}
            className={`w-10 h-10 rounded-full flex items-center justify-center shadow transition-colors ${saved ? "bg-red-500 text-white" : "bg-white/80 hover:bg-white text-gray-700"}`}
          >
            <Heart className={`w-5 h-5 ${saved ? "fill-current" : ""}`} />
          </button>
          <button className="w-10 h-10 bg-white/80 hover:bg-white rounded-full flex items-center justify-center shadow text-gray-700 transition-colors">
            <Share2 className="w-5 h-5" />
          </button>
        </div>

        {/* Photo count */}
        {photos.length > 1 && (
          <div className="absolute bottom-4 right-4 bg-black/50 text-white text-xs px-2 py-1 rounded">
            {photoIndex + 1}/{photos.length}
          </div>
        )}
      </div>

      {/* Content */}
      <div className="max-w-6xl mx-auto px-4 py-6">
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Main info */}
          <div className="lg:col-span-2">
            {/* Price and key stats */}
            <div className="flex flex-wrap items-start justify-between gap-4 mb-4">
              <div>
                <p className="text-3xl font-bold text-gray-900">
                  {formatPrice(property.price, property.currency, property.listingType)}
                </p>
                <div className="flex items-center gap-3 mt-1 text-gray-600 text-sm">
                  <span className="flex items-center gap-1"><Bed className="w-4 h-4" /> {property.bedrooms} bed</span>
                  <span className="text-gray-300">·</span>
                  <span className="flex items-center gap-1"><Bath className="w-4 h-4" /> {property.bathrooms} bath</span>
                  {avgRating != null && (
                    <>
                      <span className="text-gray-300">·</span>
                      <span className="flex items-center gap-1 text-amber-500">
                        <Star className="w-4 h-4 fill-current" />
                        {avgRating.toFixed(1)} ({ratings.length})
                      </span>
                    </>
                  )}
                </div>
              </div>
              <div className="flex flex-wrap gap-2">
                {property.verificationStatus === "VERIFIED" && (
                  <span className="flex items-center gap-1 bg-green-50 text-green-700 text-xs font-bold px-3 py-1.5 rounded-full border border-green-200">
                    <CheckCircle className="w-3.5 h-3.5" /> Verified
                  </span>
                )}
                {property.escrowRequired && (
                  <span className="flex items-center gap-1 bg-blue-50 text-blue-700 text-xs font-bold px-3 py-1.5 rounded-full border border-blue-200">
                    <Shield className="w-3.5 h-3.5" /> Escrow Protected
                  </span>
                )}
                {property.diasporaFriendly && (
                  <span className="flex items-center gap-1 bg-amber-50 text-amber-700 text-xs font-bold px-3 py-1.5 rounded-full border border-amber-200">
                    <Globe className="w-3.5 h-3.5" /> Diaspora Friendly
                  </span>
                )}
              </div>
            </div>

            <p className="text-lg font-semibold text-gray-900">{property.title}</p>
            <p className="text-gray-500 text-sm flex items-center gap-1 mt-1">
              <MapPin className="w-4 h-4 shrink-0" />
              {[property.address, property.suburb, property.city, property.country]
                .filter(Boolean)
                .join(", ")}
            </p>

            {/* Tabs */}
            <div className="flex border-b border-gray-200 mt-6 mb-4">
              {(["overview", "details", "map"] as const).map((t) => (
                <button
                  key={t}
                  onClick={() => setTab(t)}
                  className={`px-4 py-3 text-sm font-semibold capitalize transition-colors ${
                    tab === t
                      ? "text-blue-600 border-b-2 border-blue-600"
                      : "text-gray-500 hover:text-gray-700"
                  }`}
                >
                  {t}
                </button>
              ))}
            </div>

            {tab === "overview" && (
              <div>
                {property.description ? (
                  <p className="text-gray-700 leading-relaxed">{property.description}</p>
                ) : (
                  <p className="text-gray-400 italic">No description provided.</p>
                )}

                {/* Ratings section */}
                {ratings.length > 0 && (
                  <div className="mt-8">
                    <h3 className="text-lg font-semibold text-gray-900 mb-4 flex items-center gap-2">
                      <Star className="w-5 h-5 text-amber-500 fill-current" />
                      Landlord Ratings ({ratings.length})
                    </h3>
                    <div className="space-y-4">
                      {ratings.slice(0, 4).map((r) => (
                        <div key={r.id} className="border border-gray-100 rounded-xl p-4">
                          <div className="flex items-center gap-1 mb-2">
                            {Array.from({ length: 5 }).map((_, i) => (
                              <Star
                                key={i}
                                className={`w-4 h-4 ${i < r.rating ? "text-amber-500 fill-current" : "text-gray-200"}`}
                              />
                            ))}
                          </div>
                          {r.comment && <p className="text-gray-700 text-sm">{r.comment}</p>}
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}

            {tab === "details" && (
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
                {[
                  { label: "Listing Type", value: property.listingType },
                  { label: "Bedrooms", value: property.bedrooms },
                  { label: "Bathrooms", value: property.bathrooms },
                  { label: "City", value: property.city },
                  { label: "Suburb", value: property.suburb },
                  { label: "Country", value: property.country },
                  { label: "Currency", value: property.currency },
                  { label: "Verification", value: property.verificationStatus },
                  { label: "Status", value: property.status },
                ].map(({ label, value }) => (
                  <div key={label} className="bg-gray-50 rounded-xl p-4">
                    <p className="text-xs text-gray-500 uppercase tracking-wide mb-1">{label}</p>
                    <p className="text-sm font-semibold text-gray-900">{String(value)}</p>
                  </div>
                ))}
              </div>
            )}

            {tab === "map" && (
              <div className="h-72 rounded-xl overflow-hidden border border-gray-200">
                {property.latitude && property.longitude ? (
                  <PropertyMap
                    properties={[property]}
                    center={[property.latitude, property.longitude]}
                    zoom={15}
                  />
                ) : (
                  <div className="h-full flex items-center justify-center text-gray-400">
                    <div className="text-center">
                      <MapPin className="w-8 h-8 mx-auto mb-2 opacity-40" />
                      <p className="text-sm">Location coordinates not available</p>
                      <p className="text-xs mt-1">{property.suburb}, {property.city}</p>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Sidebar */}
          <div className="lg:col-span-1 space-y-4">
            {/* Price + actions card */}
            <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm sticky top-20">
              <p className="text-2xl font-bold text-gray-900 mb-1">
                {formatPrice(property.price, property.currency, property.listingType)}
              </p>
              <p className="text-sm text-gray-500 mb-5">
                {property.suburb}, {property.city}
              </p>

              {property.listingType === "RENT" && (
                <button
                  onClick={handleApply}
                  className="w-full bg-blue-600 hover:bg-blue-700 text-white font-bold py-3 rounded-xl transition-colors mb-3"
                >
                  Apply Now
                </button>
              )}
              {property.listingType === "SALE" && (
                <button
                  onClick={handleApply}
                  className="w-full bg-green-600 hover:bg-green-700 text-white font-bold py-3 rounded-xl transition-colors mb-3"
                >
                  Make an Offer
                </button>
              )}

              <button
                onClick={handleBookViewing}
                className="w-full border border-blue-600 text-blue-600 hover:bg-blue-50 font-bold py-3 rounded-xl transition-colors mb-3 flex items-center justify-center gap-2"
              >
                <Calendar className="w-4 h-4" />
                Book Viewing
              </button>

              {user && (
                <button
                  onClick={handleBookViewing}
                  className="w-full border border-gray-200 text-gray-700 hover:bg-gray-50 font-medium py-3 rounded-xl transition-colors flex items-center justify-center gap-2"
                >
                  <MessageCircle className="w-4 h-4" />
                  Message via App
                </button>
              )}

              {/* Trust signals */}
              <div className="mt-5 pt-5 border-t border-gray-100 space-y-3">
                {property.escrowRequired && (
                  <div className="flex items-start gap-3 text-sm text-gray-600">
                    <Shield className="w-5 h-5 text-blue-600 shrink-0 mt-0.5" />
                    <div>
                      <p className="font-semibold text-gray-900">Deposit Escrow</p>
                      <p className="text-xs text-gray-500">Your deposit is held safely until you receive the keys.</p>
                    </div>
                  </div>
                )}
                {property.verificationStatus === "VERIFIED" && (
                  <div className="flex items-start gap-3 text-sm text-gray-600">
                    <CheckCircle className="w-5 h-5 text-green-600 shrink-0 mt-0.5" />
                    <div>
                      <p className="font-semibold text-gray-900">Verified Property</p>
                      <p className="text-xs text-gray-500">Inspected and verified by a certified agent.</p>
                    </div>
                  </div>
                )}
                {property.diasporaFriendly && (
                  <div className="flex items-start gap-3 text-sm text-gray-600">
                    <Globe className="w-5 h-5 text-amber-600 shrink-0 mt-0.5" />
                    <div>
                      <p className="font-semibold text-gray-900">Diaspora Friendly</p>
                      <p className="text-xs text-gray-500">Can be managed and rented remotely.</p>
                    </div>
                  </div>
                )}
              </div>
            </div>

            {/* Contact card — visible to everyone */}
            <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
              <div className="flex items-center gap-2 mb-4">
                <User className="w-5 h-5 text-blue-600" />
                <h3 className="font-semibold text-gray-900 text-sm">
                  {property.agentName ? "Listed by Agent" : "Listed by Owner"}
                </h3>
              </div>

              <p className="text-base font-bold text-gray-900 mb-1">
                {property.agentName ?? property.landlordName ?? "PrimeNest Listing"}
              </p>

              {property.agentPhone && (
                <a
                  href={`tel:${property.agentPhone}`}
                  className="flex items-center gap-2 text-sm text-blue-600 hover:text-blue-700 font-medium mb-4"
                >
                  <Phone className="w-4 h-4" />
                  {property.agentPhone}
                </a>
              )}

              {/* Inquiry form for unauthenticated users */}
              {!user && (
                <div className="mt-2">
                  <p className="text-xs text-gray-500 mb-3">Send a message directly — no login required</p>
                  {inquirySent ? (
                    <div className="bg-green-50 border border-green-200 rounded-xl p-4 text-center">
                      <CheckCircle className="w-6 h-6 text-green-600 mx-auto mb-2" />
                      <p className="text-sm font-semibold text-green-800">Inquiry sent!</p>
                      <p className="text-xs text-green-600 mt-1">The agent will get back to you shortly.</p>
                    </div>
                  ) : (
                    <form onSubmit={handleInquiry} className="space-y-3">
                      {inquiryError && (
                        <p className="text-xs text-red-600 bg-red-50 px-3 py-2 rounded-lg">{inquiryError}</p>
                      )}
                      <input
                        type="text"
                        required
                        placeholder="Your name"
                        value={inquiryName}
                        onChange={(e) => setInquiryName(e.target.value)}
                        className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100 transition-colors"
                      />
                      <input
                        type="email"
                        required
                        placeholder="Your email"
                        value={inquiryEmail}
                        onChange={(e) => setInquiryEmail(e.target.value)}
                        className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100 transition-colors"
                      />
                      <input
                        type="tel"
                        placeholder="Phone (optional)"
                        value={inquiryPhone}
                        onChange={(e) => setInquiryPhone(e.target.value)}
                        className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100 transition-colors"
                      />
                      <textarea
                        required
                        rows={3}
                        placeholder={`Hi, I'm interested in this property. Is it still available?`}
                        value={inquiryMessage}
                        onChange={(e) => setInquiryMessage(e.target.value)}
                        className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100 transition-colors resize-none"
                      />
                      <button
                        type="submit"
                        disabled={inquiryLoading}
                        className="w-full bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white font-semibold py-2.5 rounded-xl transition-colors flex items-center justify-center gap-2"
                      >
                        <Send className="w-4 h-4" />
                        {inquiryLoading ? "Sending…" : "Send Inquiry"}
                      </button>
                    </form>
                  )}
                </div>
              )}

              {/* Authenticated users see message button */}
              {user && (
                <button
                  onClick={handleBookViewing}
                  className="w-full mt-2 bg-blue-50 hover:bg-blue-100 text-blue-700 font-semibold py-2.5 rounded-xl transition-colors flex items-center justify-center gap-2 text-sm"
                >
                  <MessageCircle className="w-4 h-4" />
                  Message {property.agentName ?? property.landlordName ?? "Agent"}
                </button>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
