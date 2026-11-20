"use client";

import { useState } from "react";
import { Flag, CheckCircle } from "lucide-react";
import { listingReports, REPORT_REASONS, type ReportReason } from "@/lib/trust";

/** "Report this listing": a quiet link that opens a two-question form. No account needed. */
export default function ReportListing({ propertyId, token }: { propertyId: number; token?: string | null }) {
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState<ReportReason | "">("");
  const [details, setDetails] = useState("");
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState("");

  if (sent) {
    return (
      <p className="flex items-start gap-2 text-sm text-brand-800 bg-brand-50 border border-brand-200 rounded-xl p-3" role="status">
        <CheckCircle className="w-4 h-4 mt-0.5 shrink-0" />
        Thank you. Our team will look at this listing. If you were asked to pay before a viewing, please don&apos;t.
      </p>
    );
  }

  if (!open) {
    return (
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="flex items-center gap-1.5 text-sm text-gray-500 hover:text-red-600 py-2"
      >
        <Flag className="w-4 h-4" aria-hidden /> Report this listing
      </button>
    );
  }

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!reason) return;
    setSending(true);
    setError("");
    try {
      await listingReports.report(propertyId, reason, details.trim() || undefined, token);
      setSent(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Couldn't send the report. Please try again.");
    } finally {
      setSending(false);
    }
  };

  return (
    <form onSubmit={submit} className="bg-white border border-gray-200 rounded-2xl p-4 space-y-3">
      <p className="font-semibold text-gray-900 text-sm flex items-center gap-2">
        <Flag className="w-4 h-4 text-red-600" aria-hidden /> What&apos;s wrong with this listing?
      </p>
      <fieldset className="space-y-1.5">
        <legend className="sr-only">Reason</legend>
        {REPORT_REASONS.map((r) => (
          <label key={r.value} className="flex items-center gap-2 text-sm text-gray-700 py-1 cursor-pointer">
            <input
              type="radio"
              name="report-reason"
              value={r.value}
              checked={reason === r.value}
              onChange={() => setReason(r.value)}
              className="w-4 h-4"
            />
            {r.label}
          </label>
        ))}
      </fieldset>
      <label className="block text-sm font-medium text-gray-700">
        Anything else we should know? <span className="font-normal text-gray-500">(optional)</span>
        <textarea
          rows={2}
          value={details}
          onChange={(e) => setDetails(e.target.value)}
          maxLength={1000}
          className="mt-1 w-full border border-gray-200 rounded-xl px-3 py-2 text-base sm:text-sm outline-none focus:border-brand-500"
        />
      </label>
      {error && <p className="text-sm text-red-600" role="alert">{error}</p>}
      <div className="flex gap-2">
        <button
          type="submit"
          disabled={!reason || sending}
          className="flex-1 bg-red-600 hover:bg-red-700 disabled:opacity-50 text-white font-semibold py-2.5 rounded-xl text-sm"
        >
          {sending ? "Sending…" : "Send report"}
        </button>
        <button type="button" onClick={() => setOpen(false)} className="px-4 py-2.5 text-sm text-gray-600 rounded-xl border border-gray-200">
          Cancel
        </button>
      </div>
    </form>
  );
}
