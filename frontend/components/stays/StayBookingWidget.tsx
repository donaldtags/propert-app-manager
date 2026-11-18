"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { CalendarCheck, Clock, Loader2, Minus, Plus, ShieldCheck, Zap } from "lucide-react";
import { stays as staysApi, ApiError } from "@/lib/api";
import type { BusyRange, StayBooking, StayQuote, StaySettings } from "@/lib/types";
import { useAuth } from "@/lib/auth";
import { addDays, formatStayDate, nightsBetween, toISO, type Selection } from "@/lib/stayDates";
import AvailabilityCalendar from "./AvailabilityCalendar";

function money(amount: number, currency: string) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency, maximumFractionDigits: amount % 1 ? 2 : 0 }).format(amount);
}

/** Booking panel for a SHORT_STAY listing: calendar, guests, live price and request/book. */
export default function StayBookingWidget({ propertyId, isHost }: { propertyId: number; isHost: boolean }) {
  const { user, token } = useAuth();
  const router = useRouter();
  const today = useMemo(() => toISO(new Date()), []);
  const [settings, setSettings] = useState<StaySettings | null>(null);
  const [busy, setBusy] = useState<BusyRange[]>([]);
  const [selection, setSelection] = useState<Selection>({ checkIn: null, checkOut: null });
  const [guests, setGuests] = useState(1);
  const [message, setMessage] = useState("");
  const [quote, setQuote] = useState<StayQuote | null>(null);
  const [quoting, setQuoting] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [booked, setBooked] = useState<StayBooking | null>(null);
  const [loadError, setLoadError] = useState(false);

  useEffect(() => {
    let alive = true;
    Promise.all([staysApi.settings(propertyId), staysApi.availability(propertyId, today, addDays(today, 365))])
      .then(([s, b]) => {
        if (!alive) return;
        setSettings(s);
        setBusy(b);
      })
      .catch(() => alive && setLoadError(true));
    return () => {
      alive = false;
    };
  }, [propertyId, today]);

  const { checkIn, checkOut } = selection;
  useEffect(() => {
    if (!checkIn || !checkOut) return;
    let alive = true;
    const t = setTimeout(() => {
      setQuoting(true);
      staysApi
        .quote(propertyId, checkIn, checkOut, guests)
        .then((q) => alive && setQuote(q))
        .catch(() => alive && setQuote(null))
        .finally(() => alive && setQuoting(false));
    }, 150);
    return () => {
      alive = false;
      clearTimeout(t);
    };
  }, [propertyId, checkIn, checkOut, guests]);

  if (loadError) {
    return <p className="text-sm text-red-600">Couldn&apos;t load this stay&apos;s calendar. Please refresh.</p>;
  }
  if (!settings) {
    return (
      <div className="flex items-center gap-2 text-sm text-gray-500 py-6 justify-center">
        <Loader2 className="w-4 h-4 animate-spin" /> Loading availability…
      </div>
    );
  }

  if (booked) {
    const awaiting = booked.status === "AWAITING_PAYMENT";
    return (
      <div className="rounded-xl border border-brand-200 bg-brand-50 p-4 text-sm" role="status">
        <p className="font-semibold text-brand-800 flex items-center gap-2">
          <CalendarCheck className="w-4 h-4" />
          {awaiting ? "Booked: pay to confirm" : "Request sent"}
        </p>
        <p className="text-brand-800 mt-1">
          {formatStayDate(booked.checkIn)} – {formatStayDate(booked.checkOut)} · {booked.nights} night{booked.nights === 1 ? "" : "s"} ·{" "}
          {money(booked.total, booked.currency)}
        </p>
        <p className="text-brand-700 mt-1 text-xs">
          Reference {booked.reference}.{" "}
          {awaiting
            ? "Your dates are held for 24 hours while you pay."
            : "The host has 24 hours to accept. We'll notify you."}
        </p>
        <Link href="/trips" className="mt-3 inline-block font-semibold text-brand-700 underline">
          {awaiting ? "Pay now in My trips" : "View in My trips"}
        </Link>
      </div>
    );
  }

  const nights = checkIn && checkOut ? nightsBetween(checkIn, checkOut) : 0;

  const submit = async () => {
    if (!checkIn || !checkOut) return;
    if (!user || !token) {
      router.push(`/login?redirect=${encodeURIComponent(`/properties/${propertyId}`)}`);
      return;
    }
    setSubmitting(true);
    setError("");
    try {
      const b = await staysApi.book(propertyId, { checkIn, checkOut, guests, message: message.trim() || undefined }, token);
      setBooked(b);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Couldn't send the booking. Check your connection and try again.");
      if (e instanceof ApiError && e.status === 409) {
        staysApi.availability(propertyId, today, addDays(today, 365)).then(setBusy).catch(() => {});
        setSelection({ checkIn: null, checkOut: null });
        setQuote(null);
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-2 text-xs">
        {settings.instantBook && (
          <span className="inline-flex items-center gap-1 rounded-full bg-amber-50 text-amber-800 px-2.5 py-1 font-medium">
            <Zap className="w-3.5 h-3.5" /> Instant book
          </span>
        )}
        <span className="inline-flex items-center gap-1 rounded-full bg-gray-100 text-gray-700 px-2.5 py-1">
          <Clock className="w-3.5 h-3.5" /> In {settings.checkInTime} · Out {settings.checkOutTime}
        </span>
        <span className="rounded-full bg-gray-100 text-gray-700 px-2.5 py-1">
          {settings.minNights > 1 ? `Min ${settings.minNights} nights · ` : ""}Up to {settings.maxGuests} guest{settings.maxGuests === 1 ? "" : "s"}
        </span>
      </div>

      <AvailabilityCalendar
        busy={busy}
        today={today}
        value={selection}
        onChange={(s) => {
          setSelection(s);
          setError("");
          if (!s.checkOut) setQuote(null);
        }}
      />

      <div className="grid grid-cols-2 gap-2 text-sm">
        <div className="rounded-lg border border-gray-200 px-3 py-2">
          <p className="text-[11px] font-semibold uppercase text-gray-500">Check-in</p>
          <p className="text-gray-900">{checkIn ? formatStayDate(checkIn, { weekday: "short", day: "numeric", month: "short" }) : "Add date"}</p>
        </div>
        <div className="rounded-lg border border-gray-200 px-3 py-2">
          <p className="text-[11px] font-semibold uppercase text-gray-500">Check-out</p>
          <p className="text-gray-900">{checkOut ? formatStayDate(checkOut, { weekday: "short", day: "numeric", month: "short" }) : "Add date"}</p>
        </div>
      </div>

      <div className="flex items-center justify-between rounded-lg border border-gray-200 px-3 py-2">
        <span className="text-sm text-gray-700">Guests</span>
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => setGuests((g) => Math.max(1, g - 1))}
            disabled={guests <= 1}
            aria-label="Fewer guests"
            className="w-8 h-8 rounded-full border border-gray-300 flex items-center justify-center disabled:opacity-30"
          >
            <Minus className="w-3.5 h-3.5" />
          </button>
          <span className="w-4 text-center text-sm font-semibold" aria-live="polite">{guests}</span>
          <button
            type="button"
            onClick={() => setGuests((g) => Math.min(settings.maxGuests, g + 1))}
            disabled={guests >= settings.maxGuests}
            aria-label="More guests"
            className="w-8 h-8 rounded-full border border-gray-300 flex items-center justify-center disabled:opacity-30"
          >
            <Plus className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {nights > 0 && (
        <div className="rounded-lg bg-gray-50 p-3 text-sm space-y-1" aria-live="polite">
          {quoting && !quote ? (
            <p className="text-gray-500 flex items-center gap-2"><Loader2 className="w-3.5 h-3.5 animate-spin" /> Pricing…</p>
          ) : quote ? (
            quote.available ? (
              <>
                <div className="flex justify-between text-gray-700">
                  <span>{money(quote.nightlyRate, quote.currency)} × {quote.nights} night{quote.nights === 1 ? "" : "s"}</span>
                  <span>{money(quote.accommodation, quote.currency)}</span>
                </div>
                {quote.cleaningFee > 0 && (
                  <div className="flex justify-between text-gray-700"><span>Cleaning fee</span><span>{money(quote.cleaningFee, quote.currency)}</span></div>
                )}
                {quote.serviceFee > 0 && (
                  <div className="flex justify-between text-gray-700"><span>Service fee</span><span>{money(quote.serviceFee, quote.currency)}</span></div>
                )}
                <div className="flex justify-between font-semibold text-gray-900 pt-1 border-t border-gray-200">
                  <span>Total</span><span>{money(quote.total, quote.currency)}</span>
                </div>
              </>
            ) : (
              <p className="text-amber-700">{quote.unavailableReason}</p>
            )
          ) : null}
        </div>
      )}

      {nights > 0 && !settings.instantBook && (
        <textarea
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          maxLength={1000}
          rows={2}
          placeholder="Say hello and tell the host about your trip (optional)"
          className="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm outline-none focus:border-brand-400"
        />
      )}

      {error && <p className="text-sm text-red-600" role="alert">{error}</p>}

      {isHost ? (
        <Link href="/hosting" className="block w-full text-center border border-brand-600 text-brand-700 font-semibold py-3 rounded-xl">
          Manage bookings & calendar
        </Link>
      ) : (
        <button
          type="button"
          onClick={submit}
          disabled={!quote?.available || submitting || quoting}
          className="w-full bg-brand-600 hover:bg-brand-700 disabled:opacity-50 text-white font-bold py-3 rounded-xl transition-colors flex items-center justify-center gap-2"
        >
          {submitting && <Loader2 className="w-4 h-4 animate-spin" />}
          {!checkIn || !checkOut
            ? "Select your dates"
            : !user
              ? "Sign in to book"
              : settings.instantBook
                ? "Book now"
                : "Request to book"}
        </button>
      )}
      <p className="text-[11px] text-gray-500 flex items-start gap-1.5">
        <ShieldCheck className="w-3.5 h-3.5 shrink-0 mt-px" />
        {settings.instantBook
          ? "You won't be charged until you pay in My trips. Host contact details are shared once paid."
          : "You won't be charged yet. The host replies within 24 hours; contact details are shared once paid."}
      </p>
      {settings.houseRules && (
        <details className="text-sm text-gray-700">
          <summary className="cursor-pointer font-medium">House rules</summary>
          <p className="mt-1 whitespace-pre-line text-gray-600">{settings.houseRules}</p>
        </details>
      )}
    </div>
  );
}
