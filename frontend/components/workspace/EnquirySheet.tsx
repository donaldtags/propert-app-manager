"use client";

import { useState, type FormEvent } from "react";
import { MessageSquarePlus, X } from "lucide-react";
import { parseToCents } from "@/lib/money";
import { enqueueAndWait, newClientId, useSyncStatus } from "@/lib/offline/sync";
import type { EnquiryChannel, EnquiryIntent } from "@/lib/types";
import { ws } from "./OrgWorkspace";

const CHANNELS: { value: EnquiryChannel; label: string }[] = [
  { value: "WHATSAPP", label: "WhatsApp" },
  { value: "PHONE", label: "Call" },
  { value: "WALK_IN", label: "Walk-in" },
  { value: "INSTAGRAM", label: "Instagram" },
  { value: "FACEBOOK", label: "Facebook" },
  { value: "PORTAL", label: "Portal" },
  { value: "REFERRAL", label: "Referral" },
  { value: "SIGNBOARD", label: "Board" },
  { value: "EMAIL", label: "Email" },
  { value: "OTHER", label: "Other" },
];

const INTENTS: { value: EnquiryIntent; label: string }[] = [
  { value: "BUY", label: "Buy" },
  { value: "RENT", label: "Rent" },
  { value: "SELL", label: "Sell" },
  { value: "LET", label: "Let out" },
  { value: "INVEST", label: "Invest" },
];

/**
 * One-tap enquiry capture from anywhere in the workspace. Always saved to the device first and
 * synced when possible, so it works the same with or without a connection.
 */
export default function EnquirySheet({ slug, onSaved, propertyId }: { slug: string; onSaved?: () => void; propertyId?: number }) {
  const [open, setOpen] = useState(false);
  const status = useSyncStatus();
  const empty = { channel: "WHATSAPP" as EnquiryChannel, intent: "BUY" as EnquiryIntent, name: "", phone: "", email: "", message: "", budget: "" };
  const [form, setForm] = useState(empty);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [toast, setToast] = useState<string | null>(null);
  const set = (k: keyof typeof form, v: string) => setForm((f) => ({ ...f, [k]: v }));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!form.phone.trim() && !form.email.trim()) {
      setError("Add a phone number or email.");
      return;
    }
    const budgetCents = form.budget ? parseToCents(form.budget) : undefined;
    if (form.budget && !budgetCents) {
      setError("Budget should be a number, e.g. 150000");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const sent = await enqueueAndWait(slug, {
        kind: "captureEnquiry",
        clientId: newClientId(),
        label: `Enquiry from ${form.name.trim()}`,
        payload: {
          channel: form.channel,
          intent: form.intent,
          name: form.name.trim(),
          phone: form.phone.trim() || undefined,
          email: form.email.trim() || undefined,
          message: form.message.trim() || undefined,
          budgetCents: budgetCents ?? undefined,
          propertyId,
          capturedAt: new Date().toISOString(),
        },
      });
      setForm(empty);
      setOpen(false);
      setToast(sent ? "Enquiry saved." : "Saved on this device. It will sync when you're back online.");
      setTimeout(() => setToast(null), 4000);
      onSaved?.();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save");
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="fixed z-40 bottom-5 right-5 inline-flex items-center gap-2 rounded-full bg-primary text-on-primary px-5 min-h-14 shadow-lg font-semibold"
        aria-label="Capture an enquiry"
      >
        <MessageSquarePlus className="w-5 h-5" /> Enquiry
      </button>

      {toast && (
        <p role="status" className="fixed z-40 bottom-24 right-5 left-5 sm:left-auto rounded-xl bg-raised border border-line px-4 py-3 text-sm shadow-lg">
          {toast}
        </p>
      )}

      {open && (
        <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/40" role="dialog" aria-modal="true" aria-labelledby="enquiry-title">
          <form onSubmit={submit} className="ex-theme w-full sm:max-w-lg max-h-[92vh] overflow-y-auto rounded-t-2xl sm:rounded-2xl bg-raised p-4 space-y-4">
            <div className="flex items-center justify-between">
              <h2 id="enquiry-title" className="text-lg font-semibold">
                New enquiry
              </h2>
              <button type="button" aria-label="Close" className="p-2 min-w-11 min-h-11" onClick={() => setOpen(false)}>
                <X className="w-5 h-5" />
              </button>
            </div>
            {!status.online && <p className="text-xs text-warning">You&apos;re offline. This will be saved on the device and synced later.</p>}

            <fieldset>
              <legend className={ws.label}>Came in by</legend>
              <div className="flex flex-wrap gap-2">
                {CHANNELS.map((c) => (
                  <button
                    key={c.value}
                    type="button"
                    aria-pressed={form.channel === c.value}
                    onClick={() => set("channel", c.value)}
                    className={`${ws.chip} min-h-10 px-3 ${form.channel === c.value ? "bg-primary text-on-primary border-transparent" : ""}`}
                  >
                    {c.label}
                  </button>
                ))}
              </div>
            </fieldset>
            <fieldset>
              <legend className={ws.label}>Wants to</legend>
              <div className="flex flex-wrap gap-2">
                {INTENTS.map((c) => (
                  <button
                    key={c.value}
                    type="button"
                    aria-pressed={form.intent === c.value}
                    onClick={() => set("intent", c.value)}
                    className={`${ws.chip} min-h-10 px-3 ${form.intent === c.value ? "bg-primary text-on-primary border-transparent" : ""}`}
                  >
                    {c.label}
                  </button>
                ))}
              </div>
            </fieldset>
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="block sm:col-span-2">
                <span className={ws.label}>Name</span>
                <input className={ws.input} required maxLength={160} autoComplete="off" value={form.name} onChange={(e) => set("name", e.target.value)} />
              </label>
              <label className="block">
                <span className={ws.label}>Phone / WhatsApp</span>
                <input className={ws.input} inputMode="tel" value={form.phone} onChange={(e) => set("phone", e.target.value)} placeholder="+263…" />
              </label>
              <label className="block">
                <span className={ws.label}>Email</span>
                <input className={ws.input} type="email" value={form.email} onChange={(e) => set("email", e.target.value)} />
              </label>
              <label className="block">
                <span className={ws.label}>Budget (USD)</span>
                <input className={ws.input} inputMode="decimal" value={form.budget} onChange={(e) => set("budget", e.target.value)} />
              </label>
              <label className="block sm:col-span-2">
                <span className={ws.label}>Notes</span>
                <textarea className={`${ws.input} min-h-20`} maxLength={2000} value={form.message} onChange={(e) => set("message", e.target.value)}
                  placeholder="Area, bedrooms, timeline…" />
              </label>
            </div>
            {error && <p role="alert" className="text-sm text-danger">{error}</p>}
            <button className={`${ws.button} w-full`} disabled={saving}>
              {saving ? "Saving…" : "Save enquiry"}
            </button>
          </form>
        </div>
      )}
    </>
  );
}
