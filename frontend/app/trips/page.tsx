"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { CalendarDays, CreditCard, Landmark, Loader2, MapPin, Phone, Mail, Users } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { stays as staysApi, ApiError } from "@/lib/api";
import type { StayBooking } from "@/lib/types";
import { formatStayDate } from "@/lib/stayDates";
import PageLoader from "@/components/PageLoader";
import StayStatusBadge, { stayMoney } from "@/components/stays/StayStatusBadge";

const ACTIVE = new Set(["PENDING", "AWAITING_PAYMENT", "CONFIRMED"]);

export default function TripsPage() {
  const { user, token, loading } = useAuth();
  const router = useRouter();
  const [trips, setTrips] = useState<StayBooking[] | null>(null);
  const [error, setError] = useState("");
  const [busyId, setBusyId] = useState<number | null>(null);

  useEffect(() => {
    if (!loading && !user) router.push("/login?redirect=/trips");
  }, [user, loading, router]);

  const load = useCallback(() => {
    if (!token) return;
    staysApi.mine(token).then(setTrips).catch(() => setError("Couldn't load your trips. Check your connection and refresh."));
  }, [token]);
  useEffect(load, [load]);

  const act = async (id: number, fn: () => Promise<unknown>) => {
    setBusyId(id);
    setError("");
    try {
      await fn();
      load();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Something went wrong. Please try again.");
    } finally {
      setBusyId(null);
    }
  };

  const pay = (b: StayBooking, provider: "paynow" | "stripe") =>
    act(b.id, async () => {
      const checkout = await staysApi.pay(b.id, provider, token!);
      window.location.href = checkout.redirectUrl;
    });

  if (loading || !user || trips === null) return error ? <p className="p-6 text-red-600">{error}</p> : <PageLoader />;

  const upcoming = trips.filter((t) => ACTIVE.has(t.status));
  const past = trips.filter((t) => !ACTIVE.has(t.status));

  return (
    <div className="max-w-3xl mx-auto w-full px-4 py-8">
      <h1 className="text-2xl font-bold text-gray-900">My trips</h1>
      <p className="text-sm text-gray-500 mt-1">Your short-stay bookings.</p>
      {error && <p className="mt-4 text-sm text-red-600" role="alert">{error}</p>}

      {trips.length === 0 && (
        <div className="mt-10 text-center text-gray-500">
          <CalendarDays className="w-10 h-10 mx-auto opacity-40" />
          <p className="mt-3 font-medium">No trips yet</p>
          <Link href="/properties?listingType=SHORT_STAY" className="mt-3 inline-block text-brand-700 font-semibold underline">
            Find a place to stay
          </Link>
        </div>
      )}

      {[{ title: "Upcoming", list: upcoming }, { title: "Past and cancelled", list: past }].map(
        (section) =>
          section.list.length > 0 && (
            <section key={section.title} className="mt-8">
              <h2 className="text-sm font-semibold uppercase tracking-wide text-gray-500 mb-3">{section.title}</h2>
              <div className="space-y-4">
                {section.list.map((b) => (
                  <article key={b.id} className="bg-white border border-gray-200 rounded-2xl overflow-hidden shadow-sm sm:flex">
                    <Link href={`/properties/${b.propertyId}`} className="relative block sm:w-48 h-40 sm:h-auto bg-gray-100 shrink-0">
                      {b.coverPhotoUrl && <Image src={b.coverPhotoUrl} alt="" fill className="object-cover" sizes="(max-width: 640px) 100vw, 192px" />}
                    </Link>
                    <div className="p-4 flex-1 min-w-0">
                      <div className="flex items-start justify-between gap-2">
                        <Link href={`/properties/${b.propertyId}`} className="font-semibold text-gray-900 hover:underline">
                          {b.propertyTitle}
                        </Link>
                        <StayStatusBadge status={b.status} />
                      </div>
                      <p className="text-xs text-gray-500 flex items-center gap-1 mt-0.5">
                        <MapPin className="w-3 h-3" />
                        {[b.propertySuburb, b.propertyCity].filter(Boolean).join(", ")} · Ref {b.reference}
                      </p>
                      <p className="text-sm text-gray-800 mt-2 flex flex-wrap gap-x-3 gap-y-1">
                        <span className="flex items-center gap-1">
                          <CalendarDays className="w-4 h-4 text-gray-400" />
                          {formatStayDate(b.checkIn, { weekday: "short", day: "numeric", month: "short" })} ({b.checkInTime}) –{" "}
                          {formatStayDate(b.checkOut, { weekday: "short", day: "numeric", month: "short" })} ({b.checkOutTime})
                        </span>
                        <span className="flex items-center gap-1"><Users className="w-4 h-4 text-gray-400" />{b.guests}</span>
                        <span className="font-semibold">{stayMoney(b.total, b.currency)}</span>
                      </p>
                      {b.hostNote && <p className="mt-2 text-sm text-gray-600 italic">Host: “{b.hostNote}”</p>}
                      {b.status === "PENDING" && <p className="mt-2 text-xs text-amber-700">The host has until {new Date(b.holdExpiresAt!).toLocaleString("en-GB", { weekday: "short", hour: "2-digit", minute: "2-digit" })} to reply.</p>}
                      {b.status === "CONFIRMED" && (b.host.phone || b.host.email) && (
                        <p className="mt-2 text-sm text-gray-700 flex flex-wrap gap-3">
                          <span className="font-medium">{b.host.fullName}</span>
                          {b.host.phone && <a href={`tel:${b.host.phone}`} className="flex items-center gap-1 text-brand-700"><Phone className="w-3.5 h-3.5" />{b.host.phone}</a>}
                          {b.host.email && <a href={`mailto:${b.host.email}`} className="flex items-center gap-1 text-brand-700"><Mail className="w-3.5 h-3.5" />{b.host.email}</a>}
                        </p>
                      )}
                      <div className="mt-3 flex flex-wrap gap-2">
                        {b.status === "AWAITING_PAYMENT" && (
                          <>
                            <button onClick={() => pay(b, "paynow")} disabled={busyId === b.id} className="flex items-center gap-1.5 bg-brand-600 hover:bg-brand-700 disabled:opacity-50 text-white text-sm font-semibold px-3 py-2 rounded-lg">
                              {busyId === b.id ? <Loader2 className="w-4 h-4 animate-spin" /> : <Landmark className="w-4 h-4" />} Pay with Paynow / EcoCash
                            </button>
                            <button onClick={() => pay(b, "stripe")} disabled={busyId === b.id} className="flex items-center gap-1.5 border border-gray-300 hover:bg-gray-50 disabled:opacity-50 text-sm font-semibold px-3 py-2 rounded-lg">
                              <CreditCard className="w-4 h-4" /> Pay by card
                            </button>
                          </>
                        )}
                        {ACTIVE.has(b.status) && (
                          <button
                            onClick={() => {
                              if (window.confirm(b.status === "CONFIRMED" ? "Cancel this paid stay? Refunds are arranged with the host." : "Cancel this booking?")) {
                                act(b.id, () => staysApi.cancel(b.id, undefined, token!));
                              }
                            }}
                            disabled={busyId === b.id}
                            className="text-sm font-medium text-gray-600 hover:text-red-700 px-2 py-2"
                          >
                            Cancel booking
                          </button>
                        )}
                      </div>
                    </div>
                  </article>
                ))}
              </div>
            </section>
          )
      )}
    </div>
  );
}
