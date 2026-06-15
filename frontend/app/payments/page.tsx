"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { payments as paymentsApi } from "@/lib/api";
import type { Payment } from "@/lib/types";
import { DollarSign, AlertCircle, CheckCircle, Plus } from "lucide-react";

function StatusBadge({ status }: { status: string }) {
  const colors: Record<string, string> = {
    PENDING: "bg-amber-100 text-amber-700",
    SUCCESS: "bg-green-100 text-green-700",
    FAILED: "bg-red-100 text-red-700",
  };
  return (
    <span className={`text-xs font-semibold px-2 py-0.5 rounded-full ${colors[status] ?? "bg-gray-100 text-gray-700"}`}>
      {status}
    </span>
  );
}

export default function PaymentsPage() {
  const { user, token, loading } = useAuth();
  const router = useRouter();

  const [paymentList, setPaymentList] = useState<Payment[]>([]);
  const [paymentsLoading, setPaymentsLoading] = useState(true);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({
    payeeId: "",
    propertyId: "",
    leaseId: "",
    amount: "",
    provider: "manual",
    description: "Rent payment",
  });

  useEffect(() => {
    if (!loading && !user) router.push("/login?redirect=/payments");
  }, [user, loading, router]);

  useEffect(() => {
    if (user && token) {
      paymentsApi.list(user.id, token)
        .then(setPaymentList)
        .catch(() => {})
        .finally(() => setPaymentsLoading(false));
    }
  }, [user, token]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user || !token) return;
    setError(""); setSuccess(""); setCreating(true);
    try {
      const p = await paymentsApi.create({
        payerId: user.id,
        payeeId: Number(form.payeeId),
        propertyId: Number(form.propertyId),
        leaseId: form.leaseId ? Number(form.leaseId) : undefined,
        amount: Number(form.amount),
        currency: "USD",
        provider: form.provider,
        description: form.description,
      }, token);
      // Mark success immediately for demo
      await paymentsApi.markSuccess(p.id, token);
      setSuccess("Payment recorded successfully!");
      const updated = await paymentsApi.list(user.id, token);
      setPaymentList(updated);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Payment failed.");
    } finally {
      setCreating(false);
    }
  };

  if (loading) return null;

  const totalPaid = paymentList.filter((p) => p.status === "SUCCESS" && p.payerId === user?.id).reduce((s, p) => s + p.amount, 0);
  const totalReceived = paymentList.filter((p) => p.status === "SUCCESS" && p.payeeId === user?.id).reduce((s, p) => s + p.amount, 0);

  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
          <DollarSign className="w-7 h-7 text-green-600" /> Payments
        </h1>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 gap-4 mb-6">
        <div className="bg-white border border-gray-200 rounded-2xl p-5 shadow-sm">
          <p className="text-xs text-gray-500 uppercase tracking-wide mb-1">Total Paid</p>
          <p className="text-2xl font-bold text-red-600">${totalPaid.toLocaleString()}</p>
        </div>
        <div className="bg-white border border-gray-200 rounded-2xl p-5 shadow-sm">
          <p className="text-xs text-gray-500 uppercase tracking-wide mb-1">Total Received</p>
          <p className="text-2xl font-bold text-green-600">${totalReceived.toLocaleString()}</p>
        </div>
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

      {/* Create payment */}
      <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm mb-6">
        <h2 className="font-bold text-gray-900 mb-4 flex items-center gap-2">
          <Plus className="w-5 h-5 text-green-600" /> Make a Payment
        </h2>
        <form onSubmit={handleCreate} className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          {[
            { label: "Payee (Landlord) ID", key: "payeeId" as const, placeholder: "e.g. 1" },
            { label: "Property ID", key: "propertyId" as const, placeholder: "e.g. 1" },
            { label: "Lease ID (optional)", key: "leaseId" as const, placeholder: "e.g. 1" },
            { label: "Amount (USD)", key: "amount" as const, placeholder: "e.g. 550" },
            { label: "Provider", key: "provider" as const, placeholder: "manual / paynow / ecocash" },
            { label: "Description", key: "description" as const, placeholder: "Rent payment" },
          ].map(({ label, key, placeholder }) => (
            <div key={key}>
              <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">{label}</label>
              <input
                type={key === "amount" || key === "payeeId" || key === "propertyId" || key === "leaseId" ? "number" : "text"}
                value={form[key]}
                onChange={(e) => setForm((f) => ({ ...f, [key]: e.target.value }))}
                required={key !== "leaseId"}
                placeholder={placeholder}
                className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
              />
            </div>
          ))}
          <div className="sm:col-span-3">
            <button
              type="submit"
              disabled={creating}
              className="bg-green-600 hover:bg-green-700 disabled:opacity-60 text-white font-semibold px-6 py-2.5 rounded-xl text-sm transition-colors"
            >
              {creating ? "Processing..." : "Submit Payment"}
            </button>
          </div>
        </form>
      </div>

      {/* List */}
      {paymentsLoading ? (
        <div className="space-y-3">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="h-16 bg-gray-100 rounded-xl animate-pulse" />
          ))}
        </div>
      ) : paymentList.length === 0 ? (
        <div className="text-center py-16 text-gray-500">
          <DollarSign className="w-12 h-12 mx-auto mb-4 opacity-30" />
          <p className="text-lg font-medium">No payments yet</p>
        </div>
      ) : (
        <div className="bg-white border border-gray-200 rounded-2xl shadow-sm overflow-hidden">
          {paymentList.map((p, i) => (
            <div key={p.id} className={`flex items-center justify-between px-5 py-4 ${i > 0 ? "border-t border-gray-100" : ""}`}>
              <div>
                <p className="text-sm font-medium text-gray-900">{p.description || "Payment"}</p>
                <p className="text-xs text-gray-500 mt-0.5">
                  {p.payerId === user?.id ? `→ Landlord #${p.payeeId}` : `← Tenant #${p.payerId}`} · {new Date(p.createdAt).toLocaleDateString()}
                </p>
              </div>
              <div className="text-right">
                <p className={`text-sm font-bold ${p.payerId === user?.id ? "text-red-600" : "text-green-600"}`}>
                  {p.payerId === user?.id ? "-" : "+"}${p.amount}
                </p>
                <StatusBadge status={p.status} />
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
