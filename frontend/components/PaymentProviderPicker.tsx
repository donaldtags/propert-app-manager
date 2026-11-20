"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Landmark, CreditCard, Wallet } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { wallet as walletApi } from "@/lib/api";
import type { CheckoutProvider } from "@/lib/types";

const PROVIDERS = [
  { id: "paynow" as const, label: "Paynow", sublabel: "EcoCash, OneMoney, bank transfer & card", icon: Landmark },
  { id: "stripe" as const, label: "Card (Stripe)", sublabel: "International card payment", icon: CreditCard },
];

/**
 * How to pay. Pass `walletAmount` (what this payment costs) to also offer "Pay from wallet";
 * it shows the current balance and is disabled, with a top-up link, if there isn't enough.
 */
export default function PaymentProviderPicker({
  onChoose,
  loadingProvider,
  walletAmount,
}: {
  onChoose: (provider: CheckoutProvider) => void;
  loadingProvider?: string | null;
  walletAmount?: number;
}) {
  const { token } = useAuth();
  const [balance, setBalance] = useState<number | null>(null);
  const offerWallet = walletAmount !== undefined && !!token;

  useEffect(() => {
    if (!offerWallet || !token) return;
    let cancelled = false;
    walletApi
      .get(token, 0, 1)
      .then((w) => !cancelled && setBalance(w.balance))
      .catch(() => !cancelled && setBalance(0));
    return () => {
      cancelled = true;
    };
  }, [offerWallet, token]);

  const enough = balance !== null && walletAmount !== undefined && balance >= walletAmount;

  return (
    <div className="space-y-2">
      {offerWallet && (
        <div>
          <button
            type="button"
            onClick={() => onChoose("wallet")}
            disabled={!!loadingProvider || !enough}
            className="w-full flex items-center gap-3 border border-gray-200 hover:border-brand-400 rounded-xl px-4 py-3 text-left transition-colors disabled:opacity-60"
          >
            <Wallet className="w-5 h-5 text-brand-600 shrink-0" />
            <div className="flex-1 min-w-0">
              <p className="text-sm font-semibold text-gray-900">Pay from wallet</p>
              <p className="text-xs text-gray-500">
                {balance === null ? "Checking your balance…" : `Balance $${balance.toFixed(2)}`}
              </p>
            </div>
            {loadingProvider === "wallet" && <span className="text-xs text-gray-400 shrink-0">Paying…</span>}
          </button>
          {balance !== null && !enough && (
            <p className="text-xs text-gray-500 mt-1 px-1">
              Not enough in your wallet.{" "}
              <Link href="/wallet" className="text-brand-600 font-medium hover:underline">
                Top up
              </Link>
            </p>
          )}
        </div>
      )}
      {PROVIDERS.map((p) => {
        const Icon = p.icon;
        const loading = loadingProvider === p.id;
        return (
          <button
            key={p.id}
            type="button"
            onClick={() => onChoose(p.id)}
            disabled={!!loadingProvider}
            className="w-full flex items-center gap-3 border border-gray-200 hover:border-brand-400 rounded-xl px-4 py-3 text-left transition-colors disabled:opacity-60"
          >
            <Icon className="w-5 h-5 text-brand-600 shrink-0" />
            <div className="flex-1 min-w-0">
              <p className="text-sm font-semibold text-gray-900">{p.label}</p>
              <p className="text-xs text-gray-500">{p.sublabel}</p>
            </div>
            {loading && <span className="text-xs text-gray-400 shrink-0">Redirecting…</span>}
          </button>
        );
      })}
    </div>
  );
}
