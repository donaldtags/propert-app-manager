"use client";

import { useEffect, useState, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { escrow as escrowApi } from "@/lib/api";
import type { Escrow } from "@/lib/types";
import { Shield, AlertCircle, CheckCircle, Plus } from "lucide-react";

function StatusBadge({ status }: { status: string }) {
  const colors: Record<string, string> = {
    PENDING: "bg-amber-100 text-amber-700",
    FUNDED: "bg-blue-100 text-blue-700",
    RELEASED: "bg-green-100 text-green-700",
    DISPUTED: "bg-red-100 text-red-700",
  };
  return (
    <span className={`text-xs font-semibold px-2 py-0.5 rounded-full ${colors[status] ?? "bg-gray-100 text-gray-700"}`}>
      {status}
    </span>
  );
}

function EscrowContent() {
  const { user, token, loading } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();
  const propertyIdParam = searchParams.get("propertyId");

  const [escrows, setEscrows] = useState<Escrow[]>([]);
  const [escrowLoading, setEscrowLoading] = useState(true);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({
    propertyId: propertyIdParam ?? "",
    amount: "",
    description: "Security deposit",
  });

  useEffect(() => {
    if (!loading && !user) router.push("/login?redirect=/escrow");
  }, [user, loading, router]);

  useEffect(() => {
    if (user && token) {
      escrowApi.list(user.id, token)
        .then(setEscrows)
        .catch(() => {})
        .finally(() => setEscrowLoading(false));
    }
  }, [user, token]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user || !token) return;
    setError(""); setSuccess("");
    setCreating(true);
    try {
      await escrowApi.create({
        propertyId: Number(form.propertyId),
        payerId: user.id,
        amount: Number(form.amount),
        currency: "USD",
        description: form.description,
      }, token);
      setSuccess("Escrow created! Your funds will be held securely until you receive the keys.");
      const updated = await escrowApi.list(user.id, token);
      setEscrows(updated);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to create escrow.");
    } finally {
      setCreating(false);
    }
  };

  const handleAction = async (id: number, action: "fund" | "release" | "dispute") => {
    if (!token) return;
    setError(""); setSuccess("");
    try {
      if (action === "fund") await escrowApi.fund(id, token);
      else if (action === "release") await escrowApi.release(id, token);
      else await escrowApi.dispute(id, token);
      setSuccess(`Escrow ${action}ed successfully.`);
      const updated = await escrowApi.list(user!.id, token);
      setEscrows(updated);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : `Failed to ${action} escrow.`);
    }
  };

  if (loading) return null;

  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
          <Shield className="w-7 h-7 text-blue-600" /> Escrow Transactions
        </h1>
        <p className="text-gray-500 mt-1">Your deposits are held securely until conditions are met</p>
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-5 flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0" /> {error}
        </div>
      )}
      {success && (
        <div className="bg-green-50 border border-green-200 text-green-700 text-sm px-4 py-3 rounded-xl mb-5 flex items-center gap-2">
          <CheckCircle className="w-4 h-4 shrink-0" /> {success}
        </div>
      )}

      {/* Create form */}
      <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm mb-6">
        <h2 className="font-bold text-gray-900 mb-4 flex items-center gap-2">
          <Plus className="w-5 h-5 text-blue-600" /> Create New Escrow
        </h2>
        <form onSubmit={handleCreate} className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div>
            <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">Property ID</label>
            <input
              type="number"
              value={form.propertyId}
              onChange={(e) => setForm((f) => ({ ...f, propertyId: e.target.value }))}
              required
              className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
              placeholder="e.g. 1"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">Amount (USD)</label>
            <input
              type="number"
              value={form.amount}
              onChange={(e) => setForm((f) => ({ ...f, amount: e.target.value }))}
              required
              min={1}
              className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
              placeholder="e.g. 550"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">Description</label>
            <input
              type="text"
              value={form.description}
              onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
            />
          </div>
          <div className="sm:col-span-3">
            <button
              type="submit"
              disabled={creating}
              className="bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white font-semibold px-6 py-2.5 rounded-xl text-sm transition-colors"
            >
              {creating ? "Creating..." : "Create Escrow"}
            </button>
          </div>
        </form>
      </div>

      {/* Escrow list */}
      {escrowLoading ? (
        <div className="space-y-4">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="h-24 bg-gray-100 rounded-2xl animate-pulse" />
          ))}
        </div>
      ) : escrows.length === 0 ? (
        <div className="text-center py-16 text-gray-500">
          <Shield className="w-12 h-12 mx-auto mb-4 opacity-30" />
          <p className="text-lg font-medium">No escrow transactions yet</p>
        </div>
      ) : (
        <div className="space-y-4">
          {escrows.map((e) => (
            <div key={e.id} className="bg-white border border-gray-200 rounded-2xl p-5 shadow-sm flex flex-wrap items-center justify-between gap-4">
              <div>
                <div className="flex items-center gap-2 mb-1">
                  <p className="font-semibold text-gray-900">Escrow #{e.id}</p>
                  <StatusBadge status={e.status} />
                </div>
                <p className="text-sm text-gray-500">{e.description || "Deposit"}</p>
                <p className="text-lg font-bold text-gray-900 mt-1">${e.amount} {e.currency}</p>
                <p className="text-xs text-gray-400">Property #{e.propertyId} · {new Date(e.createdAt).toLocaleDateString()}</p>
              </div>
              <div className="flex gap-2 flex-wrap">
                {e.status === "PENDING" && (
                  <button
                    onClick={() => handleAction(e.id, "fund")}
                    className="bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold px-4 py-2 rounded-xl transition-colors"
                  >
                    Fund
                  </button>
                )}
                {e.status === "FUNDED" && (
                  <>
                    <button
                      onClick={() => handleAction(e.id, "release")}
                      className="bg-green-600 hover:bg-green-700 text-white text-xs font-semibold px-4 py-2 rounded-xl transition-colors"
                    >
                      Release
                    </button>
                    <button
                      onClick={() => handleAction(e.id, "dispute")}
                      className="bg-red-50 hover:bg-red-100 text-red-700 text-xs font-semibold px-4 py-2 rounded-xl transition-colors"
                    >
                      Dispute
                    </button>
                  </>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default function EscrowPage() {
  return (
    <Suspense>
      <EscrowContent />
    </Suspense>
  );
}
