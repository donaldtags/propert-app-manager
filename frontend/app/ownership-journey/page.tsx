"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/lib/auth";
import { users as usersApi, ai as aiApi, investments as investmentsApi } from "@/lib/api";
import type { TenantPassport, Reit, Investment } from "@/lib/types";
import {
  TrendingUp,
  CheckCircle,
  Circle,
  Sparkles,
  Building2,
} from "lucide-react";

interface Stage {
  label: string;
  done: boolean;
  description: string;
}

export default function OwnershipJourneyPage() {
  const { user, token, loading } = useAuth();
  const router = useRouter();

  const [passport, setPassport] = useState<TenantPassport | null>(null);
  const [reits, setReits] = useState<Reit[]>([]);
  const [myInvestments, setMyInvestments] = useState<Investment[]>([]);
  const [pageLoading, setPageLoading] = useState(true);

  const [income, setIncome] = useState("");
  const [debt, setDebt] = useState("");
  const [affordabilityLoading, setAffordabilityLoading] = useState(false);
  const [affordabilityError, setAffordabilityError] = useState("");
  const [affordabilityResult, setAffordabilityResult] = useState<{
    recommendedMaxRent: number;
    maxByRentToIncomeRule: number;
    note: string;
  } | null>(null);

  useEffect(() => {
    if (!loading && (!user || !user.roles?.includes("TENANT"))) {
      router.push(user ? "/profile" : "/login?redirect=/ownership-journey");
    }
  }, [user, loading, router]);

  useEffect(() => {
    if (!user || !token) return;
    Promise.allSettled([
      usersApi.tenantPassport(user.id, token),
      investmentsApi.listReits(),
      investmentsApi.listByInvestor(user.id, token),
    ])
      .then(([p, r, i]) => {
        if (p.status === "fulfilled") setPassport(p.value);
        if (r.status === "fulfilled") setReits(r.value);
        if (i.status === "fulfilled") setMyInvestments(i.value);
      })
      .finally(() => setPageLoading(false));
  }, [user, token]);

  const handleCheckAffordability = async () => {
    if (!income) return;
    setAffordabilityError("");
    setAffordabilityLoading(true);
    setAffordabilityResult(null);
    try {
      const result = await aiApi.affordability({
        grossMonthlyIncome: Number(income),
        existingMonthlyDebt: debt ? Number(debt) : undefined,
      });
      setAffordabilityResult(result);
    } catch (err: unknown) {
      setAffordabilityError(err instanceof Error ? err.message : "Could not calculate affordability.");
    } finally {
      setAffordabilityLoading(false);
    }
  };

  if (loading || pageLoading) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-8 space-y-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <div key={i} className="h-24 bg-gray-100 rounded-2xl animate-pulse" />
        ))}
      </div>
    );
  }

  const isTrustedTenant = (passport?.trustScore ?? 0) >= 70 || (passport?.onTimePaymentRatePercent ?? 0) >= 90;
  const hasRentalHistory = (passport?.completedLeaseCount ?? 0) + (passport?.activeLeaseCount ?? 0) > 0;
  const isInvesting = myInvestments.length > 0;

  const stages: Stage[] = [
    {
      label: "Renter",
      done: hasRentalHistory,
      description: "You have a rental history on PrimeNest.",
    },
    {
      label: "Verified & Trusted Tenant",
      done: !!passport?.identityVerified && isTrustedTenant,
      description: "Verified identity with a strong trust score and payment record.",
    },
    {
      label: "Affordability Assessed",
      done: !!affordabilityResult,
      description: "You know what you can realistically afford to pay each month.",
    },
    {
      label: "Investing",
      done: isInvesting,
      description: "You've started building equity through REITs or property investment.",
    },
  ];

  return (
    <div className="max-w-3xl mx-auto px-4 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
          <TrendingUp className="w-7 h-7 text-forest-600" /> Home Ownership Journey
        </h1>
        <p className="text-gray-500 mt-1">From renting to building real estate wealth</p>
      </div>

      {/* Pathway */}
      <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm mb-6">
        <h2 className="font-bold text-gray-900 mb-4">Your Pathway</h2>
        <div className="space-y-4">
          {stages.map((stage, i) => (
            <div key={stage.label} className="flex items-start gap-3">
              {stage.done ? (
                <CheckCircle className="w-5 h-5 text-forest-600 shrink-0 mt-0.5" />
              ) : (
                <Circle className="w-5 h-5 text-gray-300 shrink-0 mt-0.5" />
              )}
              <div>
                <p className={`text-sm font-semibold ${stage.done ? "text-gray-900" : "text-gray-500"}`}>
                  {i + 1}. {stage.label}
                </p>
                <p className="text-xs text-gray-500 mt-0.5">{stage.description}</p>
              </div>
            </div>
          ))}
        </div>
        {!passport?.identityVerified && (
          <Link href="/verification" className="mt-4 inline-block text-sm text-forest-600 hover:underline">
            Verify your identity to advance →
          </Link>
        )}
      </div>

      {/* Trust score + rental history recap */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-6">
        <div className="bg-white border border-gray-200 rounded-2xl p-4 shadow-sm">
          <p className="text-2xl font-bold text-gray-900">{passport?.trustScore ?? "—"}</p>
          <p className="text-xs text-gray-500 mt-0.5">Trust Score</p>
        </div>
        <div className="bg-white border border-gray-200 rounded-2xl p-4 shadow-sm">
          <p className="text-2xl font-bold text-gray-900">{passport?.completedLeaseCount ?? 0}</p>
          <p className="text-xs text-gray-500 mt-0.5">Completed Leases</p>
        </div>
        <div className="bg-white border border-gray-200 rounded-2xl p-4 shadow-sm">
          <p className="text-2xl font-bold text-gray-900">
            {passport?.onTimePaymentRatePercent != null ? `${passport.onTimePaymentRatePercent.toFixed(0)}%` : "—"}
          </p>
          <p className="text-xs text-gray-500 mt-0.5">On-Time Payments</p>
        </div>
        <div className="bg-white border border-gray-200 rounded-2xl p-4 shadow-sm">
          <p className="text-2xl font-bold text-gray-900">{myInvestments.length}</p>
          <p className="text-xs text-gray-500 mt-0.5">Active Investments</p>
        </div>
      </div>

      {/* Affordability */}
      <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm mb-6">
        <h2 className="font-bold text-gray-900 mb-3 flex items-center gap-2">
          <Sparkles className="w-5 h-5 text-purple-600" /> Estimate Your Affordability
        </h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <input
            type="number"
            min={0}
            placeholder="Your gross monthly income"
            value={income}
            onChange={(e) => setIncome(e.target.value)}
            className="border border-gray-200 rounded-xl px-3 py-2.5 text-sm outline-none focus:border-purple-400"
          />
          <input
            type="number"
            min={0}
            placeholder="Existing monthly debt (optional)"
            value={debt}
            onChange={(e) => setDebt(e.target.value)}
            className="border border-gray-200 rounded-xl px-3 py-2.5 text-sm outline-none focus:border-purple-400"
          />
        </div>
        <button
          onClick={handleCheckAffordability}
          disabled={affordabilityLoading || !income}
          className="mt-3 bg-purple-600 hover:bg-purple-700 disabled:opacity-60 text-white text-sm font-semibold px-5 py-2.5 rounded-xl transition-colors"
        >
          {affordabilityLoading ? "Calculating…" : "Check affordability"}
        </button>
        {affordabilityError && <p className="text-xs text-red-600 mt-2">{affordabilityError}</p>}
        {affordabilityResult && (
          <div className="mt-3 bg-purple-50 border border-purple-100 rounded-xl p-3 text-sm text-purple-800">
            <p className="font-semibold">Recommended max rent/mortgage: ${affordabilityResult.recommendedMaxRent}/mo</p>
            <p className="mt-1 text-xs">{affordabilityResult.note}</p>
          </div>
        )}
      </div>

      {/* Investment opportunities */}
      <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
        <div className="flex items-center justify-between mb-4">
          <h2 className="font-bold text-gray-900 flex items-center gap-2">
            <Building2 className="w-5 h-5 text-forest-600" /> Start Building Equity
          </h2>
          <Link href="/investments" className="text-xs text-forest-600 hover:underline">View all REITs</Link>
        </div>
        {reits.length === 0 ? (
          <p className="text-sm text-gray-400 text-center py-6">No REITs available yet</p>
        ) : (
          <div className="space-y-3">
            {reits.slice(0, 3).map((reit) => (
              <Link
                key={reit.id}
                href="/investments"
                className="flex items-center justify-between p-3 bg-gray-50 hover:bg-gray-100 rounded-xl transition-colors"
              >
                <div>
                  <p className="text-sm font-semibold text-gray-900">{reit.name}</p>
                  <p className="text-xs text-gray-500">{reit.market} · {reit.propertyType}</p>
                </div>
                <div className="text-right">
                  <p className="text-sm font-bold text-forest-600">{reit.projectedAnnualYield}% yield</p>
                  <p className="text-xs text-gray-400">${reit.unitPrice}/unit</p>
                </div>
              </Link>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
