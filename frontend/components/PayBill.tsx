"use client";

import { useState } from "react";
import { CreditCard } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { payments as paymentsApi } from "@/lib/api";

const PROVIDERS = [
  { value: "paynow", label: "Paynow (EcoCash, OneMoney, bank & card)" },
  { value: "stripe", label: "Card (Stripe)" },
  { value: "wallet", label: "My EstateX wallet" },
] as const;

/**
 * Pays an open EstateX bill (listing fee, plan renewal) through the normal checkout: Paynow or a
 * card redirect to the gateway, the wallet completes on the spot. Nothing is marked paid until the
 * money has actually arrived.
 */
export default function PayBill({ paymentId, amountLabel, label = "Pay now" }: {
  paymentId: number;
  amountLabel: string;
  label?: string;
}) {
  const { token } = useAuth();
  const [provider, setProvider] = useState<string>("paynow");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const pay = async () => {
    if (!token) return;
    setBusy(true);
    setError("");
    try {
      const checkout = await paymentsApi.checkout(paymentId, provider, token);
      window.location.assign(checkout.redirectUrl);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Couldn't start the payment.");
      setBusy(false);
    }
  };

  return (
    <div className="flex flex-col sm:flex-row sm:items-center gap-2">
      <label className="sr-only" htmlFor={`pay-${paymentId}`}>Payment method</label>
      <select id={`pay-${paymentId}`} value={provider} onChange={(e) => setProvider(e.target.value)}
        className="border border-gray-200 rounded-lg px-2.5 py-1.5 text-sm bg-white">
        {PROVIDERS.map((p) => <option key={p.value} value={p.value}>{p.label}</option>)}
      </select>
      <button type="button" onClick={pay} disabled={busy}
        className="inline-flex items-center justify-center gap-1.5 bg-brand-600 hover:bg-brand-700 disabled:opacity-60 text-white text-sm font-semibold px-3 py-1.5 rounded-lg">
        <CreditCard className="w-4 h-4" aria-hidden /> {busy ? "Opening checkout…" : `${label} · ${amountLabel}`}
      </button>
      {error && <p role="alert" className="text-xs text-red-700">{error}</p>}
    </div>
  );
}
