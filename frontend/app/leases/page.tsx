"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { leases as leasesApi } from "@/lib/api";
import type { Lease } from "@/lib/types";
import { FileText, AlertCircle, CheckCircle, Plus } from "lucide-react";

function StatusBadge({ status }: { status: string }) {
  const colors: Record<string, string> = {
    ACTIVE: "bg-green-100 text-green-700",
    SIGNED: "bg-blue-100 text-blue-700",
    DRAFT: "bg-gray-100 text-gray-700",
    COMPLETED: "bg-purple-100 text-purple-700",
    ENDED: "bg-red-100 text-red-700",
  };
  return (
    <span className={`text-xs font-semibold px-2 py-0.5 rounded-full ${colors[status] ?? "bg-gray-100 text-gray-700"}`}>
      {status}
    </span>
  );
}

export default function LeasesPage() {
  const { user, token, loading } = useAuth();
  const router = useRouter();

  const [leaseList, setLeaseList] = useState<Lease[]>([]);
  const [leasesLoading, setLeasesLoading] = useState(true);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({
    propertyId: "",
    tenantId: "",
    landlordId: "",
    startDate: "",
    endDate: "",
    monthlyRent: "",
    depositAmount: "",
    notes: "",
  });

  useEffect(() => {
    if (!loading && !user) router.push("/login?redirect=/leases");
  }, [user, loading, router]);

  const loadLeases = async () => {
    if (!user || !token) return;
    setLeasesLoading(true);
    try {
      const isTenant = user.roles?.includes("TENANT");
      const isLandlord = user.roles?.includes("LANDLORD");
      const results = await Promise.allSettled([
        isTenant ? leasesApi.listByTenant(user.id, token) : Promise.resolve([] as Lease[]),
        isLandlord ? leasesApi.listByLandlord(user.id, token) : Promise.resolve([] as Lease[]),
      ]);
      const all: Lease[] = [];
      results.forEach((r) => { if (r.status === "fulfilled") all.push(...r.value); });
      const unique = all.filter((l, i, arr) => arr.findIndex((x) => x.id === l.id) === i);
      setLeaseList(unique);
    } catch {}
    setLeasesLoading(false);
  };

  useEffect(() => { loadLeases(); }, [user, token]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user || !token) return;
    setError(""); setSuccess(""); setCreating(true);
    try {
      await leasesApi.create({
        propertyId: Number(form.propertyId),
        tenantId: Number(form.tenantId),
        landlordId: Number(form.landlordId),
        startDate: form.startDate,
        endDate: form.endDate,
        monthlyRent: Number(form.monthlyRent),
        depositAmount: Number(form.depositAmount),
        currency: "USD",
        notes: form.notes,
      }, token);
      setSuccess("Lease created successfully!");
      await loadLeases();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to create lease.");
    } finally {
      setCreating(false);
    }
  };

  const handleSign = async (leaseId: number) => {
    if (!user || !token) return;
    setError(""); setSuccess("");
    try {
      await leasesApi.sign(leaseId, user.id, token);
      setSuccess("Lease signed!");
      await loadLeases();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to sign lease.");
    }
  };

  if (loading) return null;

  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
          <FileText className="w-7 h-7 text-blue-600" /> Leases
        </h1>
        <p className="text-gray-500 mt-1">Manage your digital lease agreements</p>
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

      {/* Create form (landlords only) */}
      {user?.roles?.includes("LANDLORD") && (
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm mb-6">
          <h2 className="font-bold text-gray-900 mb-4 flex items-center gap-2">
            <Plus className="w-5 h-5 text-blue-600" /> Create New Lease
          </h2>
          <form onSubmit={handleCreate} className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {[
              { label: "Property ID", key: "propertyId" as const, type: "number" },
              { label: "Tenant ID", key: "tenantId" as const, type: "number" },
              { label: "Landlord ID", key: "landlordId" as const, type: "number" },
              { label: "Start Date", key: "startDate" as const, type: "date" },
              { label: "End Date", key: "endDate" as const, type: "date" },
              { label: "Monthly Rent (USD)", key: "monthlyRent" as const, type: "number" },
              { label: "Deposit Amount (USD)", key: "depositAmount" as const, type: "number" },
            ].map(({ label, key, type }) => (
              <div key={key}>
                <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">{label}</label>
                <input
                  type={type}
                  value={form[key]}
                  onChange={(e) => setForm((f) => ({ ...f, [key]: e.target.value }))}
                  required
                  className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                />
              </div>
            ))}
            <div className="sm:col-span-2">
              <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">Notes</label>
              <input
                type="text"
                value={form.notes}
                onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))}
                className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                placeholder="Optional terms or notes"
              />
            </div>
            <div className="sm:col-span-2">
              <button
                type="submit"
                disabled={creating}
                className="bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white font-semibold px-6 py-2.5 rounded-xl text-sm transition-colors"
              >
                {creating ? "Creating..." : "Create Lease"}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Lease list */}
      {leasesLoading ? (
        <div className="space-y-4">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="h-28 bg-gray-100 rounded-2xl animate-pulse" />
          ))}
        </div>
      ) : leaseList.length === 0 ? (
        <div className="text-center py-16 text-gray-500">
          <FileText className="w-12 h-12 mx-auto mb-4 opacity-30" />
          <p className="text-lg font-medium">No leases yet</p>
        </div>
      ) : (
        <div className="space-y-4">
          {leaseList.map((lease) => {
            const canSign =
              (user?.roles?.includes("TENANT") && !lease.signedByTenant) ||
              (user?.roles?.includes("LANDLORD") && !lease.signedByLandlord);
            return (
              <div key={lease.id} className="bg-white border border-gray-200 rounded-2xl p-5 shadow-sm">
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div>
                    <div className="flex items-center gap-2 mb-1">
                      <p className="font-semibold text-gray-900">Lease #{lease.id}</p>
                      <StatusBadge status={lease.status} />
                    </div>
                    <p className="text-sm text-gray-500">
                      Property #{lease.propertyId} · {lease.startDate} → {lease.endDate}
                    </p>
                    <p className="text-lg font-bold text-blue-600 mt-1">${lease.monthlyRent}/mo</p>
                    <p className="text-xs text-gray-400 mt-0.5">Deposit: ${lease.depositAmount}</p>
                  </div>
                  <div className="flex flex-col gap-2 items-end">
                    <div className="flex gap-2 text-xs text-gray-500">
                      {lease.signedByTenant && <span className="text-green-600 font-medium">✓ Tenant signed</span>}
                      {lease.signedByLandlord && <span className="text-green-600 font-medium">✓ Landlord signed</span>}
                    </div>
                    {canSign && (
                      <button
                        onClick={() => handleSign(lease.id)}
                        className="bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold px-4 py-2 rounded-xl transition-colors"
                      >
                        Sign Lease
                      </button>
                    )}
                  </div>
                </div>
                {lease.notes && (
                  <p className="text-sm text-gray-600 mt-3 pt-3 border-t border-gray-100">{lease.notes}</p>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
