"use client";

import CessionBadge, { CESSION_EXPLANATION } from "@/components/CessionBadge";
import { useEffect, useState, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import Image from "next/image";
import Link from "next/link";
import ReportListing from "@/components/ReportListing";
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
  Building2,
  X,
  Grid2x2,
  Download,
  FileText,
  BadgeCheck,
  Sun,
  BatteryCharging,
  Droplets,
  Wrench,
  ShieldCheck,
  Sparkles,
} from "lucide-react";
import { properties as propertiesApi, ratings as ratingsApi, messages as messagesApi, ai as aiApi, viewings as viewingsApi, applications as applicationsApi } from "@/lib/api";
import type { Property, PropertyPassport, Rating, Viewing, RentalApplication } from "@/lib/types";
import { useAuth } from "@/lib/auth";
import StayBookingWidget from "@/components/stays/StayBookingWidget";
import { logEvent } from "@/lib/api";
import { isSaved, toggleSaved } from "@/lib/savedListings";
import { settingsRoleUrl } from "@/lib/roleGate";

const PropertyLocationMap = dynamic(() => import("@/components/PropertyLocationMap"), {
  ssr: false,
  loading: () => <div className="w-full h-80 bg-gray-100 rounded-xl animate-pulse" />,
});

function formatPrice(price: number, currency: string, listingType: string) {
  const formatted = new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: currency || "USD",
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

function toEmbedUrl(url: string): string {
  const youtubeMatch = url.match(/(?:youtube\.com\/watch\?v=|youtu\.be\/)([\w-]+)/);
  if (youtubeMatch) return `https://www.youtube.com/embed/${youtubeMatch[1]}`;
  const vimeoMatch = url.match(/vimeo\.com\/(\d+)/);
  if (vimeoMatch) return `https://player.vimeo.com/video/${vimeoMatch[1]}`;
  return url;
}

export default function PropertyDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const { user, token } = useAuth();

  const [property, setProperty] = useState<Property | null>(null);
  const [ratings, setRatings] = useState<Rating[]>([]);
  const [loading, setLoading] = useState(true);
  const [photoIndex, setPhotoIndex] = useState(0);
  const [shareNote, setShareNote] = useState("");
  const [lightboxOpen, setLightboxOpen] = useState(false);
  const [tab, setTab] = useState<"overview" | "details" | "map" | "passport" | "tour">("overview");
  const [passport, setPassport] = useState<PropertyPassport | null>(null);
  const [passportLoading, setPassportLoading] = useState(false);
  const [saved, setSaved] = useState(false);

  // Affordability calculator state
  const [affordabilityOpen, setAffordabilityOpen] = useState(false);
  const [monthlyIncome, setMonthlyIncome] = useState("");
  const [monthlyDebt, setMonthlyDebt] = useState("");
  const [affordabilityResult, setAffordabilityResult] = useState<{
    recommendedMaxRent: number;
    fitsRecommendedBudget?: boolean;
    note: string;
  } | null>(null);
  const [affordabilityLoading, setAffordabilityLoading] = useState(false);
  const [affordabilityError, setAffordabilityError] = useState("");

  const handleCheckAffordability = async () => {
    if (!property || !monthlyIncome) return;
    setAffordabilityError("");
    setAffordabilityLoading(true);
    setAffordabilityResult(null);
    try {
      const result = await aiApi.affordability({
        grossMonthlyIncome: Number(monthlyIncome),
        existingMonthlyDebt: monthlyDebt ? Number(monthlyDebt) : undefined,
        propertyId: property.id,
      });
      setAffordabilityResult(result);
    } catch (err: unknown) {
      setAffordabilityError(err instanceof Error ? err.message : "Could not calculate affordability.");
    } finally {
      setAffordabilityLoading(false);
    }
  };

  // Inquiry form state
  // null = untouched: signed-in visitors see their own name and email filled in.
  const [inquiryNameInput, setInquiryName] = useState<string | null>(null);
  const [inquiryEmailInput, setInquiryEmail] = useState<string | null>(null);
  const inquiryName = inquiryNameInput ?? user?.fullName ?? "";
  const inquiryEmail = inquiryEmailInput ?? user?.email ?? "";
  const [inquiryPhone, setInquiryPhone] = useState("");
  const [inquiryMessage, setInquiryMessage] = useState("Hi, I'm interested in this property. Is it still available?");
  const [inquiryLoading, setInquiryLoading] = useState(false);
  const [inquirySent, setInquirySent] = useState(false);
  const [inquiryError, setInquiryError] = useState("");

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      const prop = await propertiesApi.get(Number(id), token);
      setProperty(prop);
      setSaved(isSaved(prop.id));
      const r = await ratingsApi.listByLandlord(prop.landlordId);
      setRatings(r);
      logEvent("info", "property_viewed", `/properties/${id}`, "User opened property details", user?.id, { propertyId: Number(id) });
    } catch {
      // handled by empty state
    } finally {
      setLoading(false);
    }
  }, [id, user?.id, token]);

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

  const [applicationModalOpen, setApplicationModalOpen] = useState(false);
  const [applicationMoveInDate, setApplicationMoveInDate] = useState("");
  const [applicationIncome, setApplicationIncome] = useState("");
  const [applicationMessage, setApplicationMessage] = useState("");
  const [applicationSubmitting, setApplicationSubmitting] = useState(false);
  const [applicationError, setApplicationError] = useState("");
  const [applicationResult, setApplicationResult] = useState<RentalApplication | null>(null);

  const handleApply = () => {
    if (!user) {
      router.push(`/login?redirect=${encodeURIComponent(`/properties/${id}?action=apply`)}`);
      return;
    }
    if (property?.listingType === "SALE") {
      router.push(`/escrow?propertyId=${id}`);
      return;
    }
    if (!user.roles?.some((r) => r === "TENANT" || r === "DIASPORA")) {
      router.push(settingsRoleUrl(["TENANT", "DIASPORA"], "applying for a rental needs the Tenant role"));
      return;
    }
    setLightboxOpen(false);
    setViewingModalOpen(false);
    setApplicationResult(null);
    setApplicationError("");
    setApplicationModalOpen(true);
  };

  const submitApplication = async () => {
    if (!property || !token) return;
    setApplicationError("");
    setApplicationSubmitting(true);
    try {
      const application = await applicationsApi.create(
        {
          propertyId: property.id,
          desiredMoveInDate: applicationMoveInDate || undefined,
          monthlyIncome: applicationIncome ? Number(applicationIncome) : undefined,
          message: applicationMessage || undefined,
        },
        token
      );
      setApplicationResult(application);
    } catch (err: unknown) {
      setApplicationError(err instanceof Error ? err.message : "Failed to submit application.");
    } finally {
      setApplicationSubmitting(false);
    }
  };

  const [messagingAgent, setMessagingAgent] = useState(false);
  const [messageError, setMessageError] = useState("");

  const [viewingModalOpen, setViewingModalOpen] = useState(false);
  const [viewingMode, setViewingMode] = useState<"IN_PERSON" | "VIDEO_CALL">("IN_PERSON");
  const [viewingDate, setViewingDate] = useState("");
  const [viewingTime, setViewingTime] = useState("");
  const [viewingNotes, setViewingNotes] = useState("");
  const [viewingSubmitting, setViewingSubmitting] = useState(false);
  const [viewingError, setViewingError] = useState("");
  const [viewingResult, setViewingResult] = useState<Viewing | null>(null);

  const handleBookViewing = () => {
    if (!user) {
      router.push(`/login?redirect=${encodeURIComponent(`/properties/${id}?action=book-viewing`)}`);
      return;
    }
    setLightboxOpen(false);
    setApplicationModalOpen(false);
    setViewingResult(null);
    setViewingError("");
    setViewingModalOpen(true);
  };

  const submitViewingRequest = async () => {
    if (!property || !token) return;
    setViewingError("");
    setViewingSubmitting(true);
    try {
      const viewing = await viewingsApi.create(
        {
          propertyId: property.id,
          mode: viewingMode,
          preferredDate: viewingDate || undefined,
          preferredTime: viewingTime || undefined,
          notes: viewingNotes || undefined,
        },
        token
      );
      setViewingResult(viewing);
    } catch (err: unknown) {
      setViewingError(err instanceof Error ? err.message : "Failed to request a viewing.");
    } finally {
      setViewingSubmitting(false);
    }
  };

  const handleMessageAgent = () => {
    if (!user) {
      router.push(`/login?redirect=${encodeURIComponent(`/properties/${id}?action=message-agent`)}`);
      return;
    }
    startAgentConversation(
      `Inquiry: ${property?.title ?? "Property"}`,
      `Hi, I'm interested in "${property?.title}". Is it still available?`
    );
  };

  const startAgentConversation = async (subject: string, content: string) => {
    if (!property || !token) return;
    const recipientId = property.agentId ?? property.landlordId;
    if (!recipientId) return;
    setMessageError("");
    setMessagingAgent(true);
    try {
      const conversation = await messagesApi.startConversation(
        { recipientId, subject, content, messageType: "GENERAL", propertyId: property.id },
        token
      );
      router.push(`/messages?conversationId=${conversation.id}`);
    } catch (err: unknown) {
      setMessageError(err instanceof Error ? err.message : "Failed to start conversation.");
    } finally {
      setMessagingAgent(false);
    }
  };

  // Resumes the action (apply / book a viewing / message the agent) a visitor
  // was trying to take before being sent to log in, once they land back here.
  useEffect(() => {
    if (loading || !property || !user) return;
    const params = new URLSearchParams(window.location.search);
    const action = params.get("action");
    if (!action) return;
    params.delete("action");
    const query = params.toString();
    router.replace(`/properties/${id}${query ? `?${query}` : ""}`, { scroll: false });
    if (action === "apply") handleApply();
    else if (action === "book-viewing") handleBookViewing();
    else if (action === "message-agent") handleMessageAgent();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loading, property, user]);

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
        <Link href="/properties" className="mt-4 inline-block text-brand-600 hover:underline">
          ← Back to listings
        </Link>
      </div>
    );
  }

  const shareListing = async () => {
    const url = window.location.href;
    try {
      if (navigator.share) {
        await navigator.share({ title: property.title, url });
        return;
      }
      await navigator.clipboard.writeText(url);
      setShareNote("Link copied");
      setTimeout(() => setShareNote(""), 2500);
    } catch (e) {
      // Closing the share sheet is not an error worth showing.
      if (e instanceof DOMException && e.name === "AbortError") return;
      setShareNote("Couldn't share. Copy the address from your browser bar.");
      setTimeout(() => setShareNote(""), 4000);
    }
  };

  const openLightbox = (index: number) => {
    setApplicationModalOpen(false);
    setViewingModalOpen(false);
    setPhotoIndex(index);
    setLightboxOpen(true);
  };

  // Signed-out visitors have no id and most listings have no agent: compare only real ids, or
  // `undefined === undefined` would treat every visitor as the owner.
  const isListingOwner =
    user?.id != null &&
    (user.id === property.landlordId || (property.agentId != null && user.id === property.agentId));

  return (
    <div className="bg-white min-h-screen">
      {/* Photo gallery */}
      <div className="relative group">
        {photos.length > 0 ? (
          <>
            {/* Mobile: swipeable single photo */}
            <div className="lg:hidden relative bg-gray-900 h-72">
              <Image
                src={photos[photoIndex]}
                alt={`${property.title} photo ${photoIndex + 1}`}
                fill
                className="object-cover"
                sizes="100vw"
                priority
                onClick={() => openLightbox(photoIndex)}
              />
              {photos.length > 1 && (
                <>
                  <button
                    onClick={() => setPhotoIndex((i) => (i - 1 + photos.length) % photos.length)}
                    className="absolute left-3 top-1/2 -translate-y-1/2 w-9 h-9 bg-white/80 hover:bg-white rounded-full flex items-center justify-center shadow"
                  >
                    <ChevronLeft className="w-5 h-5" />
                  </button>
                  <button
                    onClick={() => setPhotoIndex((i) => (i + 1) % photos.length)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 w-9 h-9 bg-white/80 hover:bg-white rounded-full flex items-center justify-center shadow"
                  >
                    <ChevronRight className="w-5 h-5" />
                  </button>
                  <div className="absolute bottom-3 right-3 bg-black/50 text-white text-xs px-2 py-1 rounded">
                    {photoIndex + 1}/{photos.length}
                  </div>
                </>
              )}
            </div>

            {/* Desktop: Zillow-style hero + thumbnail grid */}
            <div className="hidden lg:grid grid-cols-4 grid-rows-2 gap-1.5 h-[440px] bg-gray-100">
              <button
                type="button"
                onClick={() => openLightbox(0)}
                aria-label={`Open photo 1 of ${photos.length}`}
                className={`${photos.length === 1 ? "col-span-4" : "col-span-2"} row-span-2 relative overflow-hidden`}
              >
                <Image
                  src={photos[0]}
                  alt={`${property.title} photo 1`}
                  fill
                  className="object-cover hover:brightness-95 transition-[filter]"
                  priority
                  sizes={photos.length === 1 ? "100vw" : "50vw"}
                />
              </button>
              {/* A single photo spans the whole row instead of sitting beside four empty tiles. */}
              {photos.length > 1 && Array.from({ length: 4 }).map((_, i) => {
                const photoAt = i + 1;
                const isLastTile = i === 3;
                const remaining = photos.length - 5;
                if (photos[photoAt]) {
                  return (
                    <button
                      key={photoAt}
                      type="button"
                      onClick={() => openLightbox(photoAt)}
                      aria-label={`Open photo ${photoAt + 1} of ${photos.length}`}
                      className="relative overflow-hidden"
                    >
                      <Image
                        src={photos[photoAt]}
                        alt={`${property.title} photo ${photoAt + 1}`}
                        fill
                        className="object-cover hover:brightness-95 transition-[filter]"
                        sizes="25vw"
                      />
                      {isLastTile && remaining > 0 && (
                        <div className="absolute inset-0 bg-black/50 flex items-center justify-center text-white font-semibold text-sm">
                          +{remaining} photo{remaining === 1 ? "" : "s"}
                        </div>
                      )}
                    </button>
                  );
                }
                return <div key={photoAt} className="bg-gray-200" />;
              })}
            </div>

            {photos.length > 1 && (
              <button
                onClick={() => openLightbox(0)}
                className="hidden lg:flex absolute bottom-4 right-4 items-center gap-1.5 bg-white hover:bg-gray-50 text-gray-800 text-sm font-semibold px-3.5 py-2 rounded-lg shadow border border-gray-200"
              >
                <Grid2x2 className="w-4 h-4" />
                See all {photos.length} photos
              </button>
            )}
          </>
        ) : (
          <div className="h-72 lg:h-[440px] bg-gray-900 flex items-center justify-center text-white/30">
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
            onClick={() => setSaved(toggleSaved(property.id))}
            aria-label={saved ? "Remove from saved homes" : "Save this home"}
            aria-pressed={saved}
            className={`w-10 h-10 rounded-full flex items-center justify-center shadow transition-colors ${saved ? "bg-red-500 text-white" : "bg-white/80 hover:bg-white text-gray-700"}`}
          >
            <Heart className={`w-5 h-5 ${saved ? "fill-current" : ""}`} />
          </button>
          <button
            type="button"
            onClick={shareListing}
            aria-label="Share this listing"
            className="w-10 h-10 bg-white/80 hover:bg-white rounded-full flex items-center justify-center shadow text-gray-700 transition-colors"
          >
            <Share2 className="w-5 h-5" />
          </button>
          <span role="status" aria-live="polite" className={shareNote ? "absolute top-12 right-0 whitespace-nowrap bg-gray-900 text-white text-xs font-medium px-2.5 py-1.5 rounded-lg shadow" : "sr-only"}>
            {shareNote}
          </span>
        </div>
      </div>

      {/* Lightbox */}
      {lightboxOpen && photos.length > 0 && (
        <div className="fixed inset-0 z-50 bg-black/95 flex flex-col">
          <div className="flex items-center justify-between px-4 py-3 text-white shrink-0">
            <p className="text-sm font-medium">{photoIndex + 1} / {photos.length}</p>
            <button
              onClick={() => setLightboxOpen(false)}
              className="w-9 h-9 rounded-full bg-white/10 hover:bg-white/20 flex items-center justify-center"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
          <div className="relative flex-1 min-h-0">
            <Image
              src={photos[photoIndex]}
              alt={`${property.title} photo ${photoIndex + 1}`}
              fill
              className="object-contain"
              sizes="100vw"
            />
            {photos.length > 1 && (
              <>
                <button
                  onClick={() => setPhotoIndex((i) => (i - 1 + photos.length) % photos.length)}
                  className="absolute left-4 top-1/2 -translate-y-1/2 w-11 h-11 bg-white/10 hover:bg-white/20 rounded-full flex items-center justify-center text-white"
                >
                  <ChevronLeft className="w-6 h-6" />
                </button>
                <button
                  onClick={() => setPhotoIndex((i) => (i + 1) % photos.length)}
                  className="absolute right-4 top-1/2 -translate-y-1/2 w-11 h-11 bg-white/10 hover:bg-white/20 rounded-full flex items-center justify-center text-white"
                >
                  <ChevronRight className="w-6 h-6" />
                </button>
              </>
            )}
          </div>
          {photos.length > 1 && (
            <div className="flex gap-2 overflow-x-auto px-4 py-3 shrink-0">
              {photos.map((p, i) => (
                <button
                  key={i}
                  onClick={() => setPhotoIndex(i)}
                  className={`relative shrink-0 w-20 h-14 rounded-lg overflow-hidden border-2 transition-colors ${
                    i === photoIndex ? "border-white" : "border-transparent opacity-60 hover:opacity-100"
                  }`}
                >
                  <Image src={p} alt="" fill className="object-cover" sizes="120px" />
                </button>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Smart Viewing booking modal */}
      {viewingModalOpen && (
        <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl">
            {viewingResult ? (
              <div className="text-center">
                <CheckCircle className="w-12 h-12 text-brand-500 mx-auto mb-3" />
                <p className="font-bold text-gray-900 text-lg mb-1">Viewing requested!</p>
                <p className="text-sm text-gray-500 mb-4">
                  The {property.agentName ? "agent" : "landlord"} will confirm your {viewingResult.mode === "VIDEO_CALL" ? "video" : "in-person"} viewing.
                </p>
                {viewingResult.checkInCode && (
                  <div className="bg-gray-50 rounded-xl p-4 mb-4">
                    <p className="text-xs text-gray-500 mb-1">Your check-in code (present this at the viewing)</p>
                    <p className="text-2xl font-mono font-bold tracking-widest text-gray-900">{viewingResult.checkInCode}</p>
                  </div>
                )}
                <button
                  onClick={() => setViewingModalOpen(false)}
                  className="w-full bg-brand-600 hover:bg-brand-700 text-white font-semibold py-2.5 rounded-xl transition-colors"
                >
                  Done
                </button>
              </div>
            ) : (
              <>
                <div className="flex items-center justify-between mb-4">
                  <h3 className="font-bold text-gray-900 text-lg">Book a Viewing</h3>
                  <button onClick={() => setViewingModalOpen(false)}>
                    <X className="w-5 h-5 text-gray-400" />
                  </button>
                </div>
                <div className="space-y-3">
                  <div className="flex gap-2">
                    {(["IN_PERSON", "VIDEO_CALL"] as const).map((m) => (
                      <button
                        key={m}
                        type="button"
                        onClick={() => setViewingMode(m)}
                        className={`flex-1 text-sm font-semibold py-2.5 rounded-xl border transition-colors ${
                          viewingMode === m ? "bg-brand-600 text-white border-brand-600" : "border-gray-200 text-gray-600 hover:border-gray-300"
                        }`}
                      >
                        {m === "IN_PERSON" ? "In-Person" : "Video Call"}
                      </button>
                    ))}
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <input
                      type="date"
                      value={viewingDate}
                      onChange={(e) => setViewingDate(e.target.value)}
                      className="w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm outline-none focus:border-brand-500"
                    />
                    <input
                      type="time"
                      value={viewingTime}
                      onChange={(e) => setViewingTime(e.target.value)}
                      className="w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm outline-none focus:border-brand-500"
                    />
                  </div>
                  <textarea
                    value={viewingNotes}
                    onChange={(e) => setViewingNotes(e.target.value)}
                    rows={2}
                    placeholder="Anything else the landlord/agent should know? (optional)"
                    className="w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm outline-none focus:border-brand-500 resize-none"
                  />
                  {viewingError && <p className="text-xs text-red-600">{viewingError}</p>}
                  <button
                    onClick={submitViewingRequest}
                    disabled={viewingSubmitting}
                    className="w-full bg-brand-600 hover:bg-brand-700 disabled:opacity-60 text-white font-semibold py-2.5 rounded-xl transition-colors"
                  >
                    {viewingSubmitting ? "Requesting…" : "Request Viewing"}
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}

      {/* Rental application modal */}
      {applicationModalOpen && (
        <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl">
            {applicationResult ? (
              <div className="text-center">
                <CheckCircle className="w-12 h-12 text-brand-500 mx-auto mb-3" />
                <p className="font-bold text-gray-900 text-lg mb-1">Application submitted!</p>
                <p className="text-sm text-gray-500 mb-4">
                  {applicationResult.status === "VERIFICATION_REQUIRED"
                    ? "Verify your identity to move your application into review."
                    : "The landlord will review your application shortly."}
                </p>
                <div className="flex gap-2">
                  {applicationResult.status === "VERIFICATION_REQUIRED" && (
                    <Link
                      href="/verification"
                      className="flex-1 bg-brand-600 hover:bg-brand-700 text-white font-semibold py-2.5 rounded-xl transition-colors text-center"
                    >
                      Verify Identity
                    </Link>
                  )}
                  <Link
                    href="/applications"
                    className="flex-1 border border-gray-200 hover:bg-gray-50 text-gray-700 font-semibold py-2.5 rounded-xl transition-colors text-center"
                  >
                    View My Applications
                  </Link>
                </div>
              </div>
            ) : (
              <>
                <div className="flex items-center justify-between mb-4">
                  <h3 className="font-bold text-gray-900 text-lg">Apply for this Home</h3>
                  <button onClick={() => setApplicationModalOpen(false)}>
                    <X className="w-5 h-5 text-gray-400" />
                  </button>
                </div>
                <div className="space-y-3">
                  <div>
                    <label className="text-xs text-gray-500 mb-1 block">Desired move-in date</label>
                    <input
                      type="date"
                      value={applicationMoveInDate}
                      onChange={(e) => setApplicationMoveInDate(e.target.value)}
                      className="w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm outline-none focus:border-brand-500"
                    />
                  </div>
                  <div>
                    <label className="text-xs text-gray-500 mb-1 block">Gross monthly income (optional)</label>
                    <input
                      type="number"
                      min={0}
                      value={applicationIncome}
                      onChange={(e) => setApplicationIncome(e.target.value)}
                      className="w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm outline-none focus:border-brand-500"
                    />
                  </div>
                  <textarea
                    value={applicationMessage}
                    onChange={(e) => setApplicationMessage(e.target.value)}
                    rows={3}
                    placeholder="Tell the landlord why you'd be a great tenant (optional)"
                    className="w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm outline-none focus:border-brand-500 resize-none"
                  />
                  {applicationError && <p className="text-xs text-red-600">{applicationError}</p>}
                  <button
                    onClick={submitApplication}
                    disabled={applicationSubmitting}
                    className="w-full bg-brand-600 hover:bg-brand-700 disabled:opacity-60 text-white font-semibold py-2.5 rounded-xl transition-colors"
                  >
                    {applicationSubmitting ? "Submitting…" : "Submit Application"}
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}

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
                {property.riskFlags?.includes("CESSION") && (
                  <>
                    <CessionBadge size="md" />
                    <p className="basis-full order-last text-xs text-amber-800">{CESSION_EXPLANATION}</p>
                  </>
                )}
                {property.verificationStatus === "VERIFIED" ? (
                  <span className="flex items-center gap-1 bg-brand-50 text-brand-700 text-xs font-bold px-3 py-1.5 rounded-full border border-brand-200">
                    <CheckCircle className="w-3.5 h-3.5" /> Verified
                  </span>
                ) : (
                  <span
                    className="flex items-center gap-1 bg-gray-100 text-gray-700 text-xs font-bold px-3 py-1.5 rounded-full border border-gray-200"
                    title="EstateX hasn't checked this listing yet. Never pay before you've viewed the property."
                  >
                    Not yet verified
                  </span>
                )}
                {property.escrowRequired && (
                  <span className="flex items-center gap-1 bg-brand-50 text-brand-700 text-xs font-bold px-3 py-1.5 rounded-full border border-brand-200">
                    <Shield className="w-3.5 h-3.5" /> Deposit protected
                  </span>
                )}
                {property.diasporaFriendly && (
                  <span className="flex items-center gap-1 bg-amber-50 text-amber-700 text-xs font-bold px-3 py-1.5 rounded-full border border-amber-200">
                    <Globe className="w-3.5 h-3.5" /> Diaspora Friendly
                  </span>
                )}
                {property.solarInstalled && (
                  <span className="flex items-center gap-1 bg-amber-50 text-amber-700 text-xs font-bold px-3 py-1.5 rounded-full border border-amber-200">
                    <Sun className="w-3.5 h-3.5" /> Solar
                  </span>
                )}
                {property.backupPower && (
                  <span className="flex items-center gap-1 bg-purple-50 text-purple-700 text-xs font-bold px-3 py-1.5 rounded-full border border-purple-200">
                    <BatteryCharging className="w-3.5 h-3.5" /> Backup Power
                  </span>
                )}
                {property.waterSource && (
                  <span className="flex items-center gap-1 bg-cyan-50 text-cyan-700 text-xs font-bold px-3 py-1.5 rounded-full border border-cyan-200">
                    <Droplets className="w-3.5 h-3.5" /> {property.waterSource.charAt(0) + property.waterSource.slice(1).toLowerCase()}
                  </span>
                )}
                {property.furnished && (
                  <span className="flex items-center gap-1 bg-gray-50 text-gray-700 text-xs font-bold px-3 py-1.5 rounded-full border border-gray-200">
                    Furnished
                  </span>
                )}
                {property.internetAvailable && (
                  <span className="flex items-center gap-1 bg-gray-50 text-gray-700 text-xs font-bold px-3 py-1.5 rounded-full border border-gray-200">
                    Internet / Fibre
                  </span>
                )}
                {property.securityFeatures && (
                  <span className="flex items-center gap-1 bg-gray-50 text-gray-700 text-xs font-bold px-3 py-1.5 rounded-full border border-gray-200">
                    <ShieldCheck className="w-3.5 h-3.5" /> Security
                  </span>
                )}
                {property.parkingAvailable && (
                  <span className="flex items-center gap-1 bg-gray-50 text-gray-700 text-xs font-bold px-3 py-1.5 rounded-full border border-gray-200">
                    Parking
                  </span>
                )}
                {property.petsAllowed && (
                  <span className="flex items-center gap-1 bg-gray-50 text-gray-700 text-xs font-bold px-3 py-1.5 rounded-full border border-gray-200">
                    Pets Allowed
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
            <div className="flex border-b border-gray-200 mt-6 mb-4 overflow-x-auto [scrollbar-width:none]">
              {(["overview", "details", "passport", "map", ...(property.virtualTourUrl ? (["tour"] as const) : [])] as const).map((t) => (
                <button
                  key={t}
                  onClick={() => {
                    setTab(t);
                    if (t === "passport" && !passport && !passportLoading) {
                      setPassportLoading(true);
                      propertiesApi.getPassport(property.id)
                        .then(setPassport)
                        .catch(() => {})
                        .finally(() => setPassportLoading(false));
                    }
                  }}
                  className={`shrink-0 whitespace-nowrap px-3 sm:px-4 py-3 text-sm font-semibold capitalize transition-colors ${
                    tab === t
                      ? "text-brand-600 border-b-2 border-brand-600"
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
              property.latitude != null && property.longitude != null ? (
                <PropertyLocationMap
                  latitude={property.latitude}
                  longitude={property.longitude}
                  title={property.title}
                  addressLine={[property.address, property.suburb, property.city].filter(Boolean).join(", ")}
                />
              ) : (
                <div className="h-56 rounded-xl border border-gray-200 flex items-center justify-center text-gray-400">
                  <div className="text-center">
                    <MapPin className="w-8 h-8 mx-auto mb-2 opacity-40" />
                    <p className="text-sm">The owner hasn&apos;t pinned this property on the map yet</p>
                    <p className="text-xs mt-1">{property.suburb}, {property.city}</p>
                  </div>
                </div>
              )
            )}

            {tab === "tour" && property.virtualTourUrl && (
              <div className="aspect-video rounded-xl overflow-hidden border border-gray-200 bg-black">
                <iframe
                  src={toEmbedUrl(property.virtualTourUrl)}
                  title="Virtual tour"
                  className="w-full h-full"
                  allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                  allowFullScreen
                />
              </div>
            )}

            {tab === "passport" && (
              <div>
                {passportLoading && (
                  <div className="space-y-3">
                    {Array.from({ length: 3 }).map((_, i) => (
                      <div key={i} className="h-16 bg-gray-100 rounded-xl animate-pulse" />
                    ))}
                  </div>
                )}
                {!passportLoading && passport && (
                  <div className="space-y-6">
                    {/* Digital Home Timeline */}
                    {passport.timeline.length > 0 && (
                      <div>
                        <h3 className="text-sm font-bold text-gray-900 uppercase tracking-wide mb-3">Digital Home Timeline</h3>
                        <div className="relative pl-5 space-y-4">
                          <div className="absolute left-1.5 top-1 bottom-1 w-px bg-gray-200" />
                          {passport.timeline.map((event, i) => (
                            <div key={i} className="relative">
                              <div className="absolute -left-5 top-1 w-3 h-3 rounded-full bg-brand-500 ring-4 ring-white" />
                              <p className="text-sm text-gray-900 font-medium">{event.label}</p>
                              <p className="text-xs text-gray-400">{new Date(event.occurredAt).toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" })}</p>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* Health score */}
                    <div>
                      <div className="flex items-center justify-between mb-3">
                        <h3 className="text-sm font-bold text-gray-900 uppercase tracking-wide">Property Health Score</h3>
                        <span className="text-2xl font-bold text-brand-600">{passport.healthScore.overall}/100</span>
                      </div>
                      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                        {[
                          { label: "Verification", value: passport.healthScore.verification },
                          { label: "Maintenance", value: passport.healthScore.maintenance },
                          { label: "Transaction Safety", value: passport.healthScore.transactionSafety },
                          { label: "Occupancy", value: passport.healthScore.occupancy },
                          { label: "Solar", value: passport.healthScore.solar },
                          { label: "Water", value: passport.healthScore.water },
                          { label: "Neighbourhood", value: passport.healthScore.neighbourhood },
                        ].filter((s) => s.value != null).map((s) => (
                          <div key={s.label} className="bg-gray-50 rounded-xl p-3">
                            <p className="text-xs text-gray-500 mb-1">{s.label}</p>
                            <p className="text-lg font-bold text-gray-900">{s.value}</p>
                          </div>
                        ))}
                      </div>
                    </div>

                    {/* Feature badges */}
                    <div className="flex flex-wrap gap-2">
                      {passport.solarInstalled && (
                        <span className="flex items-center gap-1.5 bg-amber-50 text-amber-700 text-xs font-semibold px-3 py-1.5 rounded-full border border-amber-200">
                          <Sun className="w-3.5 h-3.5" /> Solar Installed
                        </span>
                      )}
                      {passport.backupPower && (
                        <span className="flex items-center gap-1.5 bg-purple-50 text-purple-700 text-xs font-semibold px-3 py-1.5 rounded-full border border-purple-200">
                          <BatteryCharging className="w-3.5 h-3.5" /> Backup Power
                        </span>
                      )}
                      {passport.waterSource && (
                        <span className="flex items-center gap-1.5 bg-brand-50 text-brand-700 text-xs font-semibold px-3 py-1.5 rounded-full border border-brand-200">
                          <Droplets className="w-3.5 h-3.5" /> Water: {passport.waterSource.charAt(0) + passport.waterSource.slice(1).toLowerCase()}
                        </span>
                      )}
                      {passport.verificationStatus === "VERIFIED" && (
                        <span className="flex items-center gap-1.5 bg-brand-50 text-brand-700 text-xs font-semibold px-3 py-1.5 rounded-full border border-brand-200">
                          <ShieldCheck className="w-3.5 h-3.5" /> Verified {passport.verifiedAt ? new Date(passport.verifiedAt).toLocaleDateString() : ""}
                        </span>
                      )}
                    </div>

                    {/* Lease history */}
                    <div>
                      <h3 className="text-sm font-bold text-gray-900 uppercase tracking-wide mb-2">Lease History</h3>
                      {passport.leaseHistory.length === 0 ? (
                        <p className="text-sm text-gray-400">No leases recorded yet.</p>
                      ) : (
                        <div className="space-y-2">
                          {passport.leaseHistory.map((l, i) => (
                            <div key={i} className="flex items-center justify-between text-sm bg-gray-50 rounded-lg px-3 py-2">
                              <span className="text-gray-700">{l.startDate} → {l.endDate}</span>
                              <span className="text-gray-500">{l.monthlyRent} {l.currency}/mo · {l.status}</span>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>

                    {/* Maintenance history */}
                    <div>
                      <h3 className="text-sm font-bold text-gray-900 uppercase tracking-wide mb-2 flex items-center gap-1.5">
                        <Wrench className="w-4 h-4" /> Maintenance History
                      </h3>
                      {passport.maintenanceHistory.length === 0 ? (
                        <p className="text-sm text-gray-400">No maintenance requests recorded.</p>
                      ) : (
                        <div className="space-y-2">
                          {passport.maintenanceHistory.map((m, i) => (
                            <div key={i} className="flex items-center justify-between text-sm bg-gray-50 rounded-lg px-3 py-2">
                              <span className="text-gray-700 capitalize">{m.category}</span>
                              <span className="text-gray-500">{m.status} · {new Date(m.createdAt).toLocaleDateString()}</span>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>

                    {/* Escrow history */}
                    <div>
                      <h3 className="text-sm font-bold text-gray-900 uppercase tracking-wide mb-2">Escrow Activity</h3>
                      {passport.escrowHistory.length === 0 ? (
                        <p className="text-sm text-gray-400">No escrow transactions recorded.</p>
                      ) : (
                        <div className="space-y-2">
                          {passport.escrowHistory.map((e, i) => (
                            <div key={i} className="flex items-center justify-between text-sm bg-gray-50 rounded-lg px-3 py-2">
                              <span className="text-gray-700">{e.amount} {e.currency}</span>
                              <span className="text-gray-500">{e.status} · {new Date(e.createdAt).toLocaleDateString()}</span>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>

                    {passport.ratingCount > 0 && (
                      <div>
                        <h3 className="text-sm font-bold text-gray-900 uppercase tracking-wide mb-2">Ratings</h3>
                        <div className="flex items-center gap-1.5 text-sm text-gray-700">
                          <Star className="w-4 h-4 text-amber-500 fill-current" />
                          {passport.averageRating?.toFixed(1)} average ({passport.ratingCount} rating{passport.ratingCount === 1 ? "" : "s"})
                        </div>
                      </div>
                    )}

                    {/* Neighbourhood facts (admin-curated) */}
                    {passport.neighbourhood && (
                      <div>
                        <h3 className="text-sm font-bold text-gray-900 uppercase tracking-wide mb-2">
                          Neighbourhood — {passport.neighbourhood.suburb}
                        </h3>
                        <div className="space-y-2">
                          {passport.neighbourhood.schoolsNote && (
                            <div className="bg-gray-50 rounded-xl p-3">
                              <p className="text-xs font-semibold text-gray-500 mb-0.5">Schools</p>
                              <p className="text-sm text-gray-700">{passport.neighbourhood.schoolsNote}</p>
                            </div>
                          )}
                          {passport.neighbourhood.hospitalsNote && (
                            <div className="bg-gray-50 rounded-xl p-3">
                              <p className="text-xs font-semibold text-gray-500 mb-0.5">Hospitals & Clinics</p>
                              <p className="text-sm text-gray-700">{passport.neighbourhood.hospitalsNote}</p>
                            </div>
                          )}
                          {passport.neighbourhood.transportNote && (
                            <div className="bg-gray-50 rounded-xl p-3">
                              <p className="text-xs font-semibold text-gray-500 mb-0.5">Transport</p>
                              <p className="text-sm text-gray-700">{passport.neighbourhood.transportNote}</p>
                            </div>
                          )}
                          {passport.neighbourhood.shoppingNote && (
                            <div className="bg-gray-50 rounded-xl p-3">
                              <p className="text-xs font-semibold text-gray-500 mb-0.5">Shopping</p>
                              <p className="text-sm text-gray-700">{passport.neighbourhood.shoppingNote}</p>
                            </div>
                          )}
                          {passport.neighbourhood.generalNote && (
                            <div className="bg-gray-50 rounded-xl p-3">
                              <p className="text-xs font-semibold text-gray-500 mb-0.5">General</p>
                              <p className="text-sm text-gray-700">{passport.neighbourhood.generalNote}</p>
                            </div>
                          )}
                        </div>
                        <p className="text-xs text-gray-400 mt-2">Curated by the EstateX team, not automated.</p>
                      </div>
                    )}
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Sidebar — sticks as one unit on desktop so the contact card stays under the price card
              instead of sliding beneath it; scrolls internally if taller than the screen. */}
          <div className="lg:col-span-1 space-y-4 lg:sticky lg:top-24 lg:self-start lg:max-h-[calc(100vh-7rem)] lg:overflow-y-auto lg:overscroll-contain">
            {/* Price + actions card */}
            <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
              {(isListingOwner || user?.roles?.includes("ADMIN")) && (
                <Link
                  href={`/properties/${property.id}/edit`}
                  className="block w-full text-center border border-gray-200 text-gray-700 hover:bg-gray-50 font-semibold py-2 rounded-xl transition-colors mb-4 text-sm"
                >
                  Edit Listing
                </Link>
              )}
              <p className="text-2xl font-bold text-gray-900 mb-1">
                {formatPrice(property.price, property.currency, property.listingType)}
              </p>
              <p className="text-sm text-gray-500 mb-5">
                {property.suburb}, {property.city}
              </p>

              {property.listingType === "SHORT_STAY" && (
                <div className="mb-3">
                  <StayBookingWidget
                    propertyId={property.id}
                    isHost={isListingOwner}
                  />
                </div>
              )}
              {property.listingType === "RENT" && (
                <button
                  onClick={handleApply}
                  className="w-full bg-brand-600 hover:bg-brand-700 text-white font-bold py-3 rounded-xl transition-colors mb-3"
                >
                  Apply Now
                </button>
              )}
              {property.listingType === "SALE" && (
                <button
                  onClick={handleApply}
                  className="w-full bg-brand-600 hover:bg-brand-700 text-white font-bold py-3 rounded-xl transition-colors mb-3"
                >
                  Make an Offer
                </button>
              )}

              {property.listingType !== "SHORT_STAY" && (
                <button
                  onClick={handleBookViewing}
                  className="w-full border border-brand-600 text-brand-600 hover:bg-brand-50 font-bold py-3 rounded-xl transition-colors mb-3 flex items-center justify-center gap-2"
                >
                  <Calendar className="w-4 h-4" />
                  Book Viewing
                </button>
              )}

              {property.listingType === "RENT" && (
                <div className="mb-3">
                  <button
                    type="button"
                    onClick={() => setAffordabilityOpen(!affordabilityOpen)}
                    className="w-full flex items-center justify-center gap-1.5 text-sm font-semibold text-purple-600 hover:text-purple-700 py-2"
                  >
                    <Sparkles className="w-4 h-4" />
                    Can I afford this?
                  </button>
                  {affordabilityOpen && (
                    <div className="bg-purple-50 border border-purple-100 rounded-xl p-3 space-y-2">
                      <input
                        type="number"
                        min={0}
                        placeholder="Your gross monthly income"
                        value={monthlyIncome}
                        onChange={(e) => setMonthlyIncome(e.target.value)}
                        className="w-full border border-purple-200 rounded-lg px-3 py-2 text-sm outline-none focus:border-purple-400 bg-white"
                      />
                      <input
                        type="number"
                        min={0}
                        placeholder="Existing monthly debt (optional)"
                        value={monthlyDebt}
                        onChange={(e) => setMonthlyDebt(e.target.value)}
                        className="w-full border border-purple-200 rounded-lg px-3 py-2 text-sm outline-none focus:border-purple-400 bg-white"
                      />
                      <button
                        type="button"
                        onClick={handleCheckAffordability}
                        disabled={affordabilityLoading || !monthlyIncome}
                        className="w-full bg-purple-600 hover:bg-purple-700 disabled:opacity-60 text-white text-sm font-semibold py-2 rounded-lg transition-colors"
                      >
                        {affordabilityLoading ? "Calculating…" : "Check affordability"}
                      </button>
                      {affordabilityError && <p className="text-xs text-red-600">{affordabilityError}</p>}
                      {affordabilityResult && (
                        <div
                          className={`text-xs rounded-lg px-3 py-2 ${
                            affordabilityResult.fitsRecommendedBudget === false
                              ? "bg-red-50 text-red-700"
                              : "bg-brand-50 text-brand-700"
                          }`}
                        >
                          <p className="font-semibold">
                            Recommended max rent: ${affordabilityResult.recommendedMaxRent}/mo
                          </p>
                          <p className="mt-1">{affordabilityResult.note}</p>
                        </div>
                      )}
                    </div>
                  )}
                </div>
              )}

              {user && (
                <button
                  onClick={handleMessageAgent}
                  disabled={messagingAgent}
                  className="w-full border border-gray-200 text-gray-700 hover:bg-gray-50 disabled:opacity-60 font-medium py-3 rounded-xl transition-colors flex items-center justify-center gap-2"
                >
                  <MessageCircle className="w-4 h-4" />
                  {messagingAgent ? "Sending..." : "Message via App"}
                </button>
              )}

              {messageError && (
                <p className="text-xs text-red-600 bg-red-50 px-3 py-2 rounded-lg mt-2">{messageError}</p>
              )}

              {/* Trust signals */}
              <div className="mt-5 pt-5 border-t border-gray-100 space-y-3">
                {property.escrowRequired && (
                  <div className="flex items-start gap-3 text-sm text-gray-600">
                    <Shield className="w-5 h-5 text-brand-600 shrink-0 mt-0.5" />
                    <div>
                      <p className="font-semibold text-gray-900">Protected deposit</p>
                      <p className="text-xs text-gray-500">Your deposit is held safely until you receive the keys.</p>
                    </div>
                  </div>
                )}
                {property.verificationStatus === "VERIFIED" && (
                  <div className="flex items-start gap-3 text-sm text-gray-600">
                    <CheckCircle className="w-5 h-5 text-brand-600 shrink-0 mt-0.5" />
                    <div>
                      <p className="font-semibold text-gray-900">Verified Property</p>
                      <p className="text-xs text-gray-500">Checked by the EstateX verification team.</p>
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
                <User className="w-5 h-5 text-brand-600" />
                <h3 className="font-semibold text-gray-900 text-sm">
                  {property.listedBy === "AGENCY"
                    ? "Listed by an estate agency"
                    : property.listedBy === "AGENT"
                      ? "Listed by an independent agent"
                      : "Listed by the owner"}
                </h3>
              </div>

              <p className="text-base font-bold text-gray-900 mb-1">
                {property.agentName ?? property.landlordName ?? "EstateX Listing"}
              </p>

              {(() => {
                const trustScore = property.agentName ? property.agentTrustScore : property.landlordTrustScore;
                if (trustScore == null) return null;
                const tier =
                  trustScore >= 80 ? "text-brand-700 bg-brand-50" : trustScore >= 50 ? "text-amber-700 bg-amber-50" : "text-gray-600 bg-gray-50";
                return (
                  <div className={`inline-flex items-center gap-1.5 text-xs font-semibold px-2.5 py-1 rounded-full mb-2 ${tier}`}>
                    <BadgeCheck className="w-3.5 h-3.5" />
                    Trust Score {trustScore}/100
                  </div>
                );
              })()}

              {property.listedBy === "AGENCY" && property.organisationName && (
                <Link
                  href={`/agencies/${property.organisationSlug}`}
                  className="flex items-center gap-2 text-sm text-brand-700 font-semibold mb-2 hover:underline"
                >
                  {property.organisationLogoUrl ? (
                    // eslint-disable-next-line @next/next/no-img-element -- company logo from their profile
                    <img src={property.organisationLogoUrl} alt="" className="h-6 w-auto max-w-20 object-contain" />
                  ) : (
                    <Building2 className="w-4 h-4" />
                  )}
                  Agent at {property.organisationName}
                </Link>
              )}
              {property.listedBy === "AGENT" && (
                <p className="text-sm text-gray-600 mb-2">Independent agent (not part of a company on EstateX)</p>
              )}
              {property.listedBy === "OWNER" && (
                <p className="text-sm text-gray-600 mb-2">Private owner, no agent involved</p>
              )}
              {property.listedBy !== "OWNER" && property.listerLicensed != null && (
                <div
                  className={`inline-flex items-center gap-1.5 text-xs font-semibold px-2.5 py-1 rounded-full mb-3 ${
                    property.listerLicensed ? "text-brand-700 bg-brand-50" : "text-gray-600 bg-gray-100"
                  }`}
                >
                  <BadgeCheck className="w-3.5 h-3.5" />
                  {property.listerLicensed ? "Licensed agent (checked by EstateX)" : "Agent licence not checked yet"}
                </div>
              )}
              {property.listerIdVerified && (
                <div className="inline-flex items-center gap-1.5 text-xs font-semibold px-2.5 py-1 rounded-full mb-3 ml-1 text-brand-700 bg-brand-50">
                  <BadgeCheck className="w-3.5 h-3.5" /> ID checked by EstateX
                </div>
              )}

              {(property.organisationPhone ?? property.agentPhone) && (
                <a
                  href={`tel:${property.organisationPhone ?? property.agentPhone}`}
                  className="flex items-center gap-2 text-sm text-brand-600 hover:text-brand-700 font-medium mb-4 py-1"
                >
                  <Phone className="w-4 h-4" />
                  {property.organisationPhone ?? property.agentPhone}
                </a>
              )}

              {/* Inquiry form for unauthenticated users */}
              {!user && (
                <div className="mt-2">
                  <p className="text-xs text-gray-500 mb-3">Send a message directly — no login required</p>
                  {inquirySent ? (
                    <div className="bg-brand-50 border border-brand-200 rounded-xl p-4 text-center">
                      <CheckCircle className="w-6 h-6 text-brand-600 mx-auto mb-2" />
                      <p className="text-sm font-semibold text-brand-800">Inquiry sent!</p>
                      <p className="text-xs text-brand-600 mt-1">The agent will get back to you shortly.</p>
                    </div>
                  ) : (
                    <form onSubmit={handleInquiry} className="space-y-3">
                      {inquiryError && (
                        <p className="text-xs text-red-600 bg-red-50 px-3 py-2 rounded-lg">{inquiryError}</p>
                      )}
                      <label className="block text-sm font-medium text-gray-700">
                        Your name
                        <input
                          type="text"
                          required
                          autoComplete="name"
                          placeholder="e.g. Tariro Moyo"
                          value={inquiryName}
                          onChange={(e) => setInquiryName(e.target.value)}
                          className="mt-1 w-full border border-gray-200 rounded-xl px-4 py-2.5 text-base sm:text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 transition-colors"
                        />
                      </label>
                      <label className="block text-sm font-medium text-gray-700">
                        Your email
                        <input
                          type="email"
                          required
                          autoComplete="email"
                          inputMode="email"
                          placeholder="e.g. tariro@gmail.com"
                          value={inquiryEmail}
                          onChange={(e) => setInquiryEmail(e.target.value)}
                          className="mt-1 w-full border border-gray-200 rounded-xl px-4 py-2.5 text-base sm:text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 transition-colors"
                        />
                      </label>
                      <label className="block text-sm font-medium text-gray-700">
                        Phone or WhatsApp <span className="font-normal text-gray-500">(optional)</span>
                        <input
                          type="tel"
                          autoComplete="tel"
                          placeholder="e.g. +263 77 123 4567"
                          value={inquiryPhone}
                          onChange={(e) => setInquiryPhone(e.target.value)}
                          className="mt-1 w-full border border-gray-200 rounded-xl px-4 py-2.5 text-base sm:text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 transition-colors"
                        />
                      </label>
                      <label className="block text-sm font-medium text-gray-700">
                        Your message
                        <textarea
                          required
                          rows={3}
                          value={inquiryMessage}
                          onChange={(e) => setInquiryMessage(e.target.value)}
                          className="mt-1 w-full border border-gray-200 rounded-xl px-4 py-2.5 text-base sm:text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 transition-colors resize-none"
                        />
                      </label>
                      <button
                        type="submit"
                        disabled={inquiryLoading}
                        className="w-full bg-brand-600 hover:bg-brand-700 disabled:opacity-60 text-white font-semibold py-2.5 rounded-xl transition-colors flex items-center justify-center gap-2"
                      >
                        <Send className="w-4 h-4" />
                        {inquiryLoading ? "Sending…" : "Send message"}
                      </button>
                    </form>
                  )}
                </div>
              )}

              {/* Authenticated users see message button */}
              {user && (
                <button
                  onClick={handleMessageAgent}
                  disabled={messagingAgent}
                  className="w-full mt-2 bg-brand-50 hover:bg-brand-100 disabled:opacity-60 text-brand-700 font-semibold py-2.5 rounded-xl transition-colors flex items-center justify-center gap-2 text-sm"
                >
                  <MessageCircle className="w-4 h-4" />
                  {messagingAgent ? "Sending..." : `Message ${property.agentName ?? property.landlordName ?? "Agent"}`}
                </button>
              )}
            </div>

            {!isListingOwner && <ReportListing propertyId={property.id} token={token} />}

            {/* Legal document templates — sale transactions only */}
            {property.listingType === "SALE" && (
              <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
                <div className="flex items-center gap-2 mb-3">
                  <FileText className="w-5 h-5 text-brand-600" />
                  <h3 className="font-semibold text-gray-900 text-sm">Legal Documents</h3>
                </div>
                <p className="text-xs text-gray-500 mb-4">
                  Blank templates for this sale transaction. Have a legal practitioner and registered conveyancer review before use.
                </p>
                <div className="space-y-2">
                  <a
                    href="/templates/EstateX_Agreement_of_Sale.docx"
                    download
                    className="flex items-center justify-between gap-2 text-sm font-medium text-gray-700 hover:text-brand-700 bg-gray-50 hover:bg-brand-50 px-3.5 py-2.5 rounded-xl transition-colors"
                  >
                    <span>Agreement of Sale</span>
                    <Download className="w-4 h-4 shrink-0" />
                  </a>
                  <a
                    href="/templates/EstateX_Mandate_to_Sell.docx"
                    download
                    className="flex items-center justify-between gap-2 text-sm font-medium text-gray-700 hover:text-brand-700 bg-gray-50 hover:bg-brand-50 px-3.5 py-2.5 rounded-xl transition-colors"
                  >
                    <span>Mandate to Sell</span>
                    <Download className="w-4 h-4 shrink-0" />
                  </a>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
