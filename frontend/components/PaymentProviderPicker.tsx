"use client";

import { Landmark, CreditCard } from "lucide-react";

const PROVIDERS = [
  { id: "paynow" as const, label: "Paynow", sublabel: "EcoCash, OneMoney, bank transfer & card", icon: Landmark },
  { id: "stripe" as const, label: "Card (Stripe)", sublabel: "International card payment", icon: CreditCard },
];

export default function PaymentProviderPicker({
  onChoose,
  loadingProvider,
}: {
  onChoose: (provider: "paynow" | "stripe") => void;
  loadingProvider?: string | null;
}) {
  return (
    <div className="space-y-2">
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
