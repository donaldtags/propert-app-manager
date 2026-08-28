"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { payments as paymentsApi, properties as propertiesApi, leases as leasesApi } from "@/lib/api";
import type { Payment, Property, Lease } from "@/lib/types";
import { DollarSign, AlertCircle, CheckCircle, Plus, Download, Landmark, Smartphone, CreditCard } from "lucide-react";
import EntityPicker from "@/components/EntityPicker";
import PageLoader from "@/components/PageLoader";

const PAYMENT_PROVIDERS = [
  { value: "bank_transfer", label: "Bank Transfer", icon: Landmark },
  { value: "ecocash", label: "Mobile Money (EcoCash)", icon: Smartphone },
  { value: "card", label: "Card", icon: CreditCard },
  { value: "manual", label: "Other / Manual", icon: DollarSign },
] as const;

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function StatusBadge({ status }: { status: string }) {
  const colors: Record<string, string> = {
    INITIATED: "bg-amber-100 text-amber-700",
    SUCCESSFUL: "bg-forest-100 text-forest-700",
    FAILED: "bg-red-100 text-red-700",
    REFUNDED: "bg-gray-100 text-gray-700",
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
    propertyId: null as number | null,
    leaseId: null as number | null,
    amount: "",
    provider: "bank_transfer",
    description: "Rent payment",
  });
  const [downloadingId, setDownloadingId] = useState<number | "statement" | null>(null);

  const [myProperties, setMyProperties] = useState<Property[]>([]);
  const [myLeases, setMyLeases] = useState<Lease[]>([]);
  const [pickersLoading, setPickersLoading] = useState(true);

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

  useEffect(() => {
    if (!user || !token) return;
    setPickersLoading(true);
    Promise.all([propertiesApi.list(), leasesApi.listByTenant(user.id, token).catch(() => [] as Lease[])])
      .then(([allProperties, tenantLeases]) => {
        setMyProperties(allProperties);
        setMyLeases(tenantLeases);
      })
      .catch(() => {})
      .finally(() => setPickersLoading(false));
  }, [user, token]);

  const selectedProperty = myProperties.find((p) => p.id === form.propertyId) ?? null;

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user || !token) return;
    if (!form.propertyId || !selectedProperty) {
      setError("Please choose a property.");
      return;
    }
    setError(""); setSuccess(""); setCreating(true);
    try {
      await paymentsApi.create({
        payeeId: selectedProperty.landlordId,
        propertyId: form.propertyId,
        leaseId: form.leaseId ?? undefined,
        amount: Number(form.amount),
        currency: "USD",
        provider: form.provider,
        purpose: form.description,
      }, token);
      setSuccess("Payment submitted! It will show as successful once the recipient confirms receipt.");
      setForm((f) => ({ ...f, propertyId: null, leaseId: null, amount: "" }));
      const updated = await paymentsApi.list(user.id, token);
      setPaymentList(updated);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Payment failed.");
    } finally {
      setCreating(false);
    }
  };

  const handleDownloadReceipt = async (payment: Payment) => {
    if (!token) return;
    setDownloadingId(payment.id);
    try {
      const blob = await paymentsApi.receipt(payment.id, token);
      downloadBlob(blob, `receipt-${payment.reference ?? payment.id}.html`);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to download receipt.");
    } finally {
      setDownloadingId(null);
    }
  };

  const handleDownloadStatement = async () => {
    if (!user || !token) return;
    setDownloadingId("statement");
    try {
      const blob = await paymentsApi.statementCsv(user.id, token);
      downloadBlob(blob, `statement-${user.id}.csv`);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to download statement.");
    } finally {
      setDownloadingId(null);
    }
  };

  const handleConfirmReceived = async (id: number) => {
    if (!user || !token) return;
    setError(""); setSuccess("");
    try {
      await paymentsApi.markSuccess(id, token);
      setSuccess("Payment confirmed as received!");
      const updated = await paymentsApi.list(user.id, token);
      setPaymentList(updated);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to confirm payment.");
    }
  };

  if (loading) return <PageLoader />;

  const totalPaid = paymentList.filter((p) => p.status === "SUCCESSFUL" && p.payerId === user?.id).reduce((s, p) => s + p.amount, 0);
  const totalReceived = paymentList.filter((p) => p.status === "SUCCESSFUL" && p.payeeId === user?.id).reduce((s, p) => s + p.amount, 0);

  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      <div className="mb-8 flex items-center justify-between gap-4 flex-wrap">
        <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
          <DollarSign className="w-7 h-7 text-forest-600" /> Payments
        </h1>
        {paymentList.length > 0 && (
          <button
            onClick={handleDownloadStatement}
            disabled={downloadingId === "statement"}
            className="flex items-center gap-1.5 text-sm font-medium text-gray-700 hover:text-forest-600 border border-gray-200 hover:border-forest-300 px-3 py-2 rounded-lg transition-colors disabled:opacity-50"
          >
            <Download className="w-3.5 h-3.5" /> {downloadingId === "statement" ? "Preparing…" : "Download Statement (CSV)"}
          </button>
        )}
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 gap-4 mb-6">
        <div className="bg-white border border-gray-200 rounded-2xl p-5 shadow-sm">
          <p className="text-xs text-gray-500 uppercase tracking-wide mb-1">Total Paid</p>
          <p className="text-2xl font-bold text-red-600">${totalPaid.toLocaleString()}</p>
        </div>
        <div className="bg-white border border-gray-200 rounded-2xl p-5 shadow-sm">
          <p className="text-xs text-gray-500 uppercase tracking-wide mb-1">Total Received</p>
          <p className="text-2xl font-bold text-forest-600">${totalReceived.toLocaleString()}</p>
        </div>
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-5 flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0" /> {error}
        </div>
      )}
      {success && (
        <div className="bg-forest-50 border border-forest-200 text-forest-700 text-sm px-4 py-3 rounded-xl mb-5 flex items-center gap-2">
          <CheckCircle className="w-4 h-4 shrink-0" /> {success}
        </div>
      )}

      {/* Create payment */}
      <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm mb-6">
        <h2 className="font-bold text-gray-900 mb-4 flex items-center gap-2">
          <Plus className="w-5 h-5 text-forest-600" /> Make a Payment
        </h2>
        <form onSubmit={handleCreate} className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <EntityPicker
            label="Property"
            loading={pickersLoading}
            options={myProperties.map((p) => ({ id: p.id, label: p.title, sublabel: `${p.suburb}, ${p.city}` }))}
            value={form.propertyId}
            onChange={(id) => setForm((f) => ({ ...f, propertyId: id, leaseId: null }))}
            placeholder="Choose a property"
            emptyMessage="No properties found"
            required
          />
          <EntityPicker
            label="Lease (optional)"
            loading={pickersLoading}
            disabled={!form.propertyId}
            options={myLeases
              .filter((l) => l.propertyId === form.propertyId)
              .map((l) => ({ id: l.id, label: `Lease #${l.id}`, sublabel: `${l.startDate} → ${l.endDate}` }))}
            value={form.leaseId}
            onChange={(id) => setForm((f) => ({ ...f, leaseId: id }))}
            placeholder={form.propertyId ? "Choose a lease" : "Pick a property first"}
            emptyMessage="No leases found for this property"
          />
          {selectedProperty && (
            <div className="sm:col-span-1">
              <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">Paying To</label>
              <div className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm bg-gray-50 text-gray-700">
                {selectedProperty.landlordName ?? `Landlord #${selectedProperty.landlordId}`}
              </div>
            </div>
          )}
          <div>
            <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">Amount (USD)</label>
            <input
              type="number"
              value={form.amount}
              onChange={(e) => setForm((f) => ({ ...f, amount: e.target.value }))}
              required
              placeholder="e.g. 550"
              className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-forest-500 focus:ring-2 focus:ring-forest-100"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">Payment Method</label>
            <select
              value={form.provider}
              onChange={(e) => setForm((f) => ({ ...f, provider: e.target.value }))}
              className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-forest-500 focus:ring-2 focus:ring-forest-100 bg-white"
            >
              {PAYMENT_PROVIDERS.map((p) => (
                <option key={p.value} value={p.value}>{p.label}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">Description</label>
            <input
              type="text"
              value={form.description}
              onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              placeholder="Rent payment"
              className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-forest-500 focus:ring-2 focus:ring-forest-100"
            />
          </div>
          <div className="sm:col-span-3">
            <button
              type="submit"
              disabled={creating}
              className="bg-forest-600 hover:bg-forest-700 disabled:opacity-60 text-white font-semibold px-6 py-2.5 rounded-xl text-sm transition-colors"
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
                <p className="text-sm font-medium text-gray-900">{p.purpose || "Payment"}</p>
                <p className="text-xs text-gray-500 mt-0.5">
                  {p.payerId === user?.id ? `→ ${p.payeeName ?? `Landlord #${p.payeeId}`}` : `← ${p.payerName ?? `Tenant #${p.payerId}`}`}
                  {" · "}{new Date(p.createdAt).toLocaleDateString()}
                </p>
              </div>
              <div className="flex items-center gap-3">
                {p.status === "SUCCESSFUL" && (
                  <button
                    onClick={() => handleDownloadReceipt(p)}
                    disabled={downloadingId === p.id}
                    className="flex items-center gap-1 text-xs text-gray-500 hover:text-forest-600 disabled:opacity-50"
                  >
                    <Download className="w-3.5 h-3.5" /> Receipt
                  </button>
                )}
                {p.status === "INITIATED" && p.payeeId === user?.id && (
                  <button
                    onClick={() => handleConfirmReceived(p.id)}
                    className="text-xs bg-forest-50 hover:bg-forest-100 text-forest-700 px-3 py-1.5 rounded-xl font-medium transition-colors"
                  >
                    Confirm Received
                  </button>
                )}
                <div className="text-right">
                  <p className={`text-sm font-bold ${p.payerId === user?.id ? "text-red-600" : "text-forest-600"}`}>
                    {p.payerId === user?.id ? "-" : "+"}${p.amount}
                  </p>
                  <StatusBadge status={p.status} />
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
