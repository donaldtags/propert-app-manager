"use client";

import { useEffect, useState } from "react";
import { BadgeCheck, Clock, AlertTriangle, Upload } from "lucide-react";
import { agentLicences, type AgentLicence } from "@/lib/trust";

/**
 * Profile card for agents: submit the Estate Agents Council registration and see where the review
 * stands. Until it's approved, the agent's listings are saved but not shown to the public.
 */
export default function AgentLicenceCard({ token }: { token: string }) {
  const [licence, setLicence] = useState<AgentLicence | null | undefined>(undefined);
  const [editing, setEditing] = useState(false);
  const [number, setNumber] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    let alive = true;
    agentLicences
      .mine(token)
      .then((l) => alive && setLicence(l ?? null))
      .catch(() => alive && setLicence(null));
    return () => {
      alive = false;
    };
  }, [token]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!file) return;
    setSaving(true);
    setError("");
    try {
      setLicence(await agentLicences.submit(number.trim(), file, token));
      setEditing(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Couldn't upload. Please try again.");
    } finally {
      setSaving(false);
    }
  };

  if (licence === undefined) return null;
  const showForm = editing || !licence || licence.status === "REJECTED";

  return (
    <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm mb-6">
      <h2 className="text-lg font-bold text-gray-900 mb-1 flex items-center gap-2">
        <BadgeCheck className="w-5 h-5 text-brand-600" /> Agent licence
      </h2>

      {licence?.status === "APPROVED" && !editing && (
        <p className="text-sm text-brand-800 bg-brand-50 border border-brand-200 rounded-xl p-3 mt-3">
          You&apos;re a <strong>Licensed agent</strong> on EstateX (registration {licence.registrationNumber}). Your listings
          go live straight away and show the Licensed agent badge.
        </p>
      )}
      {licence?.status === "PENDING" && !editing && (
        <p className="text-sm text-amber-900 bg-amber-50 border border-amber-200 rounded-xl p-3 mt-3 flex gap-2">
          <Clock className="w-4 h-4 mt-0.5 shrink-0" />
          <span>
            We&apos;re checking registration <strong>{licence.registrationNumber}</strong>. This usually takes one working day.
            Listings you add now are saved and go live as soon as you&apos;re approved.
          </span>
        </p>
      )}
      {licence?.status === "REJECTED" && (
        <p className="text-sm text-red-800 bg-red-50 border border-red-200 rounded-xl p-3 mt-3 flex gap-2">
          <AlertTriangle className="w-4 h-4 mt-0.5 shrink-0" />
          <span>We couldn&apos;t approve your licence: {licence.reviewNote}. Please upload it again below.</span>
        </p>
      )}
      {!licence && (
        <p className="text-sm text-gray-600 mt-2">
          To protect buyers and tenants, agents show their Estate Agents Council registration before their listings go
          public. Add yours below. Until it&apos;s approved, your listings are saved but hidden.
        </p>
      )}

      {showForm ? (
        <form onSubmit={submit} className="mt-4 space-y-3">
          <label className="block text-sm font-medium text-gray-700">
            Estate Agents Council registration number
            <input
              required
              value={number}
              onChange={(e) => setNumber(e.target.value)}
              placeholder="e.g. EAC/2024/0457"
              className="mt-1 w-full border border-gray-200 rounded-xl px-4 py-2.5 text-base sm:text-sm outline-none focus:border-brand-500"
            />
          </label>
          <label className="block text-sm font-medium text-gray-700">
            Registration certificate <span className="font-normal text-gray-500">(PDF or a clear photo)</span>
            <input
              required
              type="file"
              accept="application/pdf,image/jpeg,image/png"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              className="mt-1 block w-full text-sm text-gray-700 file:mr-3 file:py-2 file:px-4 file:rounded-xl file:border-0 file:bg-brand-50 file:text-brand-700 file:font-semibold"
            />
          </label>
          {error && <p className="text-sm text-red-600" role="alert">{error}</p>}
          <button
            type="submit"
            disabled={saving || !file || number.trim().length < 3}
            className="w-full sm:w-auto bg-brand-600 hover:bg-brand-700 disabled:opacity-50 text-white font-semibold px-6 py-2.5 rounded-xl flex items-center justify-center gap-2"
          >
            <Upload className="w-4 h-4" /> {saving ? "Uploading…" : "Send for checking"}
          </button>
        </form>
      ) : (
        <button type="button" onClick={() => setEditing(true)} className="mt-3 text-sm text-brand-600 font-medium py-2">
          Update my licence details
        </button>
      )}
    </div>
  );
}
