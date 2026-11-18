"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { CalendarDays, Check, Loader2, Mail, Phone, Plus, Settings2, Trash2, Users, X } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { stays as staysApi, ApiError } from "@/lib/api";
import type { HostedStay, HostingOverview, StayBooking } from "@/lib/types";
import { formatStayDate, toISO } from "@/lib/stayDates";
import PageLoader from "@/components/PageLoader";
import StayStatusBadge, { stayMoney } from "@/components/stays/StayStatusBadge";

const PAID_METHODS = ["Cash", "EcoCash", "Bank transfer", "Other"];
type Tab = "requests" | "upcoming" | "history";

export default function HostingPage() {
  const { user, token, loading } = useAuth();
  const router = useRouter();
  const [data, setData] = useState<HostingOverview | null>(null);
  const [tab, setTab] = useState<Tab>("requests");
  const [error, setError] = useState("");
  const [busyId, setBusyId] = useState<number | null>(null);

  useEffect(() => {
    if (!loading && !user) router.push("/login?redirect=/hosting");
  }, [user, loading, router]);

  const load = useCallback(() => {
    if (!token) return;
    staysApi.hosting(token).then(setData).catch(() => setError("Couldn't load your bookings. Check your connection and refresh."));
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

  const groups = useMemo(() => {
    const b = data?.bookings ?? [];
    return {
      requests: b.filter((x) => x.status === "PENDING"),
      upcoming: b.filter((x) => x.status === "AWAITING_PAYMENT" || x.status === "CONFIRMED").sort((a, c) => a.checkIn.localeCompare(c.checkIn)),
      history: b.filter((x) => !["PENDING", "AWAITING_PAYMENT", "CONFIRMED"].includes(x.status)),
    };
  }, [data]);

  if (loading || !user || data === null) return error ? <p className="p-6 text-red-600">{error}</p> : <PageLoader />;

  return (
    <div className="max-w-4xl mx-auto w-full px-4 py-8">
      <h1 className="text-2xl font-bold text-gray-900">Hosting</h1>
      <p className="text-sm text-gray-500 mt-1">Booking requests, guests and calendars for your short stays.</p>
      {error && <p className="mt-4 text-sm text-red-600" role="alert">{error}</p>}

      {data.stays.length === 0 ? (
        <div className="mt-8 rounded-2xl border border-dashed border-gray-300 p-8 text-center text-gray-600">
          <p className="font-medium">You don&apos;t have any short-stay listings yet.</p>
          <p className="text-sm mt-1">List a property with the <strong>Short Stay</strong> type and set its price per night.</p>
          <Link href="/properties/new" className="mt-4 inline-block bg-brand-600 text-white font-semibold px-4 py-2 rounded-lg">List a stay</Link>
        </div>
      ) : (
        <>
          <div className="mt-6 flex gap-1 border-b border-gray-200 overflow-x-auto" role="tablist">
            {([
              ["requests", `Requests (${groups.requests.length})`],
              ["upcoming", `Upcoming (${groups.upcoming.length})`],
              ["history", "History"],
            ] as [Tab, string][]).map(([key, label]) => (
              <button
                key={key}
                role="tab"
                aria-selected={tab === key}
                onClick={() => setTab(key)}
                className={`whitespace-nowrap px-3 sm:px-4 py-2.5 text-sm font-semibold ${tab === key ? "text-brand-700 border-b-2 border-brand-600" : "text-gray-500"}`}
              >
                {label}
              </button>
            ))}
          </div>
          <div className="mt-4 space-y-3">
            {groups[tab].length === 0 && <p className="text-sm text-gray-500 py-6 text-center">Nothing here right now.</p>}
            {groups[tab].map((b) => (
              <HostBookingCard key={b.id} b={b} busy={busyId === b.id} token={token!} act={act} />
            ))}
          </div>

          <h2 className="mt-10 text-lg font-bold text-gray-900">Your stays</h2>
          <div className="mt-3 space-y-4">
            {data.stays.map((s) => (
              <StaySetup key={s.propertyId} stay={s} token={token!} onChanged={load} />
            ))}
          </div>
        </>
      )}
    </div>
  );
}

function HostBookingCard({
  b,
  busy,
  token,
  act,
}: {
  b: StayBooking;
  busy: boolean;
  token: string;
  act: (id: number, fn: () => Promise<unknown>) => Promise<void>;
}) {
  const [method, setMethod] = useState(PAID_METHODS[0]);
  return (
    <article className="bg-white border border-gray-200 rounded-xl p-4 shadow-sm">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="font-semibold text-gray-900">{b.guest.fullName}</p>
          <p className="text-xs text-gray-500 truncate">
            <Link href={`/properties/${b.propertyId}`} className="hover:underline">{b.propertyTitle}</Link> · Ref {b.reference}
          </p>
        </div>
        <StayStatusBadge status={b.status} />
      </div>
      <p className="text-sm text-gray-800 mt-2 flex flex-wrap gap-x-3 gap-y-1">
        <span className="flex items-center gap-1">
          <CalendarDays className="w-4 h-4 text-gray-400" />
          {formatStayDate(b.checkIn, { weekday: "short", day: "numeric", month: "short" })} – {formatStayDate(b.checkOut, { weekday: "short", day: "numeric", month: "short" })} · {b.nights} night{b.nights === 1 ? "" : "s"}
        </span>
        <span className="flex items-center gap-1"><Users className="w-4 h-4 text-gray-400" />{b.guests}</span>
        <span className="font-semibold">{stayMoney(b.total, b.currency)}</span>
        {b.paidVia && <span className="text-emerald-700">Paid · {b.paidVia}</span>}
      </p>
      {b.guestMessage && <p className="mt-2 text-sm text-gray-600 italic">“{b.guestMessage}”</p>}
      {(b.guest.phone || b.guest.email) && (
        <p className="mt-2 text-sm flex flex-wrap gap-3">
          {b.guest.phone && <a href={`tel:${b.guest.phone}`} className="flex items-center gap-1 text-brand-700"><Phone className="w-3.5 h-3.5" />{b.guest.phone}</a>}
          {b.guest.email && <a href={`mailto:${b.guest.email}`} className="flex items-center gap-1 text-brand-700"><Mail className="w-3.5 h-3.5" />{b.guest.email}</a>}
        </p>
      )}
      <div className="mt-3 flex flex-wrap items-center gap-2">
        {b.status === "PENDING" && (
          <>
            <button onClick={() => act(b.id, () => staysApi.accept(b.id, undefined, token))} disabled={busy} className="flex items-center gap-1.5 bg-brand-600 hover:bg-brand-700 disabled:opacity-50 text-white text-sm font-semibold px-3 py-2 rounded-lg">
              {busy ? <Loader2 className="w-4 h-4 animate-spin" /> : <Check className="w-4 h-4" />} Accept
            </button>
            <button
              onClick={() => {
                const note = window.prompt("Optional message to the guest:") ?? undefined;
                act(b.id, () => staysApi.decline(b.id, note, token));
              }}
              disabled={busy}
              className="flex items-center gap-1.5 border border-gray-300 hover:bg-gray-50 text-sm font-semibold px-3 py-2 rounded-lg"
            >
              <X className="w-4 h-4" /> Decline
            </button>
          </>
        )}
        {(b.status === "PENDING" || b.status === "AWAITING_PAYMENT") && (
          <span className="flex items-center gap-1.5">
            <label className="sr-only" htmlFor={`paid-${b.id}`}>Payment method</label>
            <select id={`paid-${b.id}`} value={method} onChange={(e) => setMethod(e.target.value)} className="border border-gray-300 rounded-lg px-2 py-2 text-sm bg-white">
              {PAID_METHODS.map((m) => <option key={m}>{m}</option>)}
            </select>
            <button onClick={() => act(b.id, () => staysApi.markPaid(b.id, method, token))} disabled={busy} className="border border-emerald-600 text-emerald-700 hover:bg-emerald-50 text-sm font-semibold px-3 py-2 rounded-lg">
              Mark as paid
            </button>
          </span>
        )}
        {["PENDING", "AWAITING_PAYMENT", "CONFIRMED"].includes(b.status) && b.status !== "PENDING" && (
          <button
            onClick={() => window.confirm("Cancel this guest's booking? They'll be notified.") && act(b.id, () => staysApi.cancel(b.id, undefined, token))}
            disabled={busy}
            className="text-sm font-medium text-gray-600 hover:text-red-700 px-2 py-2"
          >
            Cancel booking
          </button>
        )}
      </div>
    </article>
  );
}

function StaySetup({ stay, token, onChanged }: { stay: HostedStay; token: string; onChanged: () => void }) {
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ ...stay.settings, houseRules: stay.settings.houseRules ?? "" });
  const [block, setBlock] = useState({ start: toISO(new Date()), end: "", note: "" });
  const [saving, setSaving] = useState(false);
  const [msg, setMsg] = useState("");

  const run = async (fn: () => Promise<unknown>, ok: string) => {
    setSaving(true);
    setMsg("");
    try {
      await fn();
      setMsg(ok);
      onChanged();
    } catch (e) {
      setMsg(e instanceof ApiError ? e.message : "Couldn't save. Please try again.");
    } finally {
      setSaving(false);
    }
  };

  const num = (k: "maxGuests" | "minNights" | "maxNights" | "cleaningFee") => (
    <input
      type="number"
      min={0}
      value={form[k]}
      onChange={(e) => setForm({ ...form, [k]: Number(e.target.value) })}
      className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm"
    />
  );

  return (
    <section className="bg-white border border-gray-200 rounded-xl">
      <button onClick={() => setOpen((o) => !o)} aria-expanded={open} className="w-full flex items-center justify-between gap-3 p-4 text-left">
        <span className="min-w-0">
          <span className="block font-semibold text-gray-900 truncate">{stay.title}</span>
          <span className="block text-xs text-gray-500">
            {stay.city} · {stayMoney(stay.nightlyRate, stay.currency)}/night · up to {stay.settings.maxGuests} guests
            {stay.settings.instantBook ? " · instant book" : ""} · {stay.blocks.length} blocked period{stay.blocks.length === 1 ? "" : "s"}
          </span>
        </span>
        <Settings2 className="w-5 h-5 text-gray-400 shrink-0" />
      </button>
      {open && (
        <div className="border-t border-gray-100 p-4 space-y-5">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              run(() => staysApi.updateSettings(stay.propertyId, { ...form, houseRules: form.houseRules || null }, token), "Saved");
            }}
            className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-sm"
          >
            <label className="space-y-1"><span className="text-gray-600">Max guests</span>{num("maxGuests")}</label>
            <label className="space-y-1"><span className="text-gray-600">Min nights</span>{num("minNights")}</label>
            <label className="space-y-1"><span className="text-gray-600">Max nights</span>{num("maxNights")}</label>
            <label className="space-y-1"><span className="text-gray-600">Cleaning fee ({stay.currency})</span>{num("cleaningFee")}</label>
            <label className="space-y-1"><span className="text-gray-600">Check-in from</span>
              <input type="time" value={form.checkInTime} onChange={(e) => setForm({ ...form, checkInTime: e.target.value })} className="w-full border border-gray-200 rounded-lg px-3 py-2" />
            </label>
            <label className="space-y-1"><span className="text-gray-600">Check-out by</span>
              <input type="time" value={form.checkOutTime} onChange={(e) => setForm({ ...form, checkOutTime: e.target.value })} className="w-full border border-gray-200 rounded-lg px-3 py-2" />
            </label>
            <label className="col-span-2 flex items-center gap-2 self-end pb-2">
              <input type="checkbox" checked={form.instantBook} onChange={(e) => setForm({ ...form, instantBook: e.target.checked })} className="w-4 h-4" />
              <span>Instant book (guests book without waiting for you)</span>
            </label>
            <label className="col-span-2 sm:col-span-4 space-y-1"><span className="text-gray-600">House rules</span>
              <textarea value={form.houseRules} onChange={(e) => setForm({ ...form, houseRules: e.target.value })} rows={2} maxLength={2000} className="w-full border border-gray-200 rounded-lg px-3 py-2" />
            </label>
            <div className="col-span-2 sm:col-span-4 flex items-center gap-3">
              <button type="submit" disabled={saving} className="bg-brand-600 hover:bg-brand-700 disabled:opacity-50 text-white font-semibold px-4 py-2 rounded-lg">Save settings</button>
              <span className="text-xs text-gray-500">The nightly price is the listing price — change it on the listing.</span>
            </div>
          </form>

          <div>
            <h3 className="text-sm font-semibold text-gray-900 mb-2">Blocked dates</h3>
            {stay.blocks.length === 0 && <p className="text-sm text-gray-500">None. Block dates you&apos;re using yourself or booked elsewhere.</p>}
            <ul className="space-y-1">
              {stay.blocks.map((bl) => (
                <li key={bl.id} className="flex items-center justify-between text-sm bg-gray-50 rounded-lg px-3 py-2">
                  <span>{formatStayDate(bl.start)} – {formatStayDate(bl.end)}{bl.note ? ` · ${bl.note}` : ""}</span>
                  <button onClick={() => run(() => staysApi.unblock(stay.propertyId, bl.id, token), "Dates reopened")} aria-label="Remove block" className="text-gray-500 hover:text-red-600">
                    <Trash2 className="w-4 h-4" />
                  </button>
                </li>
              ))}
            </ul>
            <form
              onSubmit={(e) => {
                e.preventDefault();
                run(() => staysApi.block(stay.propertyId, { start: block.start, end: block.end, note: block.note || undefined }, token), "Dates blocked");
              }}
              className="mt-3 flex flex-wrap items-end gap-2 text-sm"
            >
              <label className="space-y-1"><span className="block text-gray-600">From</span>
                <input type="date" required value={block.start} onChange={(e) => setBlock({ ...block, start: e.target.value })} className="border border-gray-200 rounded-lg px-3 py-2" />
              </label>
              <label className="space-y-1"><span className="block text-gray-600">Until (checkout day)</span>
                <input type="date" required min={block.start} value={block.end} onChange={(e) => setBlock({ ...block, end: e.target.value })} className="border border-gray-200 rounded-lg px-3 py-2" />
              </label>
              <input placeholder="Note (optional)" value={block.note} onChange={(e) => setBlock({ ...block, note: e.target.value })} className="border border-gray-200 rounded-lg px-3 py-2 flex-1 min-w-32" />
              <button type="submit" disabled={saving} className="flex items-center gap-1 border border-gray-300 hover:bg-gray-50 font-semibold px-3 py-2 rounded-lg">
                <Plus className="w-4 h-4" /> Block
              </button>
            </form>
          </div>
          {msg && <p className="text-sm text-gray-700" role="status">{msg}</p>}
        </div>
      )}
    </section>
  );
}
