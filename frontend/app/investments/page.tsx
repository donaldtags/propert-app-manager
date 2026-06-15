"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { investments as investmentsApi } from "@/lib/api";
import type { Reit, Investment } from "@/lib/types";
import { TrendingUp, DollarSign, AlertCircle, CheckCircle } from "lucide-react";

export default function InvestmentsPage() {
  const { user, token, loading } = useAuth();
  const router = useRouter();

  const [reits, setReits] = useState<Reit[]>([]);
  const [myInvestments, setMyInvestments] = useState<Investment[]>([]);
  const [reitsLoading, setReitsLoading] = useState(true);
  const [error, setError] = useState("");
  const [investing, setInvesting] = useState<number | null>(null);
  const [units, setUnits] = useState<Record<number, string>>({});
  const [success, setSuccess] = useState("");

  useEffect(() => {
    investmentsApi.listReits()
      .then((data) => { setReits(data); })
      .catch(() => { /* backend offline — show empty state without error */ })
      .finally(() => setReitsLoading(false));
  }, []);

  useEffect(() => {
    if (user && token && user.roles?.includes("INVESTOR")) {
      investmentsApi.listByInvestor(user.id, token).then(setMyInvestments).catch(() => {});
    }
  }, [user, token]);

  const handleInvest = async (reit: Reit) => {
    if (!user || !token) { router.push("/login?redirect=/investments"); return; }
    if (!user.roles?.includes("INVESTOR")) {
      setError("You need an Investor role to invest. Add it from your profile.");
      return;
    }
    const n = Number(units[reit.id] || 1);
    if (n < 1) { setError("Enter at least 1 unit."); return; }
    setInvesting(reit.id);
    setError("");
    setSuccess("");
    try {
      await investmentsApi.invest({ investorId: user.id, reitId: reit.id, units: n, currency: "USD" }, token);
      setSuccess(`Invested ${n} unit${n > 1 ? "s" : ""} in ${reit.name}!`);
      const updated = await investmentsApi.listByInvestor(user.id, token);
      setMyInvestments(updated);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Investment failed.");
    } finally {
      setInvesting(null);
    }
  };

  const totalInvested = myInvestments.reduce((sum, inv) => {
    const reit = reits.find((r) => r.id === inv.reitId);
    return sum + (reit ? reit.unitPrice * inv.units : 0);
  }, 0);

  return (
    <div className="max-w-5xl mx-auto px-4 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
          <TrendingUp className="w-7 h-7 text-blue-600" /> REIT Investments
        </h1>
        <p className="text-gray-500 mt-1">Invest in verified African real estate portfolios</p>
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

      {/* My portfolio summary */}
      {myInvestments.length > 0 && (
        <div className="bg-gradient-to-br from-blue-600 to-blue-700 rounded-2xl p-6 text-white mb-8">
          <h2 className="font-bold mb-4">My Portfolio</h2>
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
            <div>
              <p className="text-3xl font-bold">${totalInvested.toLocaleString()}</p>
              <p className="text-blue-200 text-sm">Total Invested</p>
            </div>
            <div>
              <p className="text-3xl font-bold">{myInvestments.length}</p>
              <p className="text-blue-200 text-sm">Positions</p>
            </div>
            <div>
              <p className="text-3xl font-bold">
                {myInvestments.reduce((s, i) => s + i.units, 0)}
              </p>
              <p className="text-blue-200 text-sm">Total Units</p>
            </div>
          </div>
        </div>
      )}

      {/* REIT cards */}
      {reitsLoading ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="h-48 bg-gray-100 rounded-2xl animate-pulse" />
          ))}
        </div>
      ) : reits.length === 0 ? (
        <div className="text-center py-16 text-gray-500">
          <TrendingUp className="w-12 h-12 mx-auto mb-4 opacity-30" />
          <p className="text-lg font-medium">No REITs available yet</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
          {reits.map((reit) => {
            const myPos = myInvestments.filter((i) => i.reitId === reit.id);
            const totalUnitsOwned = myPos.reduce((s, i) => s + i.units, 0);
            return (
              <div key={reit.id} className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
                <div className="flex items-start justify-between mb-3">
                  <div>
                    <h3 className="font-bold text-gray-900">{reit.name}</h3>
                    <p className="text-xs text-gray-500">{reit.country}</p>
                  </div>
                  <span className={`text-xs font-medium px-2 py-1 rounded-full ${
                    reit.riskLevel === "LOW" ? "bg-green-100 text-green-700" :
                    reit.riskLevel === "MEDIUM" ? "bg-amber-100 text-amber-700" :
                    "bg-red-100 text-red-700"
                  }`}>
                    {reit.riskLevel} risk
                  </span>
                </div>

                {reit.description && <p className="text-sm text-gray-600 mb-4">{reit.description}</p>}

                <div className="grid grid-cols-2 gap-3 mb-4">
                  <div className="bg-gray-50 rounded-xl p-3">
                    <p className="text-xs text-gray-500">Unit Price</p>
                    <p className="text-lg font-bold text-gray-900">${reit.unitPrice}</p>
                  </div>
                  <div className="bg-gray-50 rounded-xl p-3">
                    <p className="text-xs text-gray-500">Projected Yield</p>
                    <p className="text-lg font-bold text-green-600">{reit.projectedYield}%</p>
                  </div>
                </div>

                {totalUnitsOwned > 0 && (
                  <div className="bg-blue-50 border border-blue-100 rounded-xl p-3 mb-4">
                    <p className="text-xs text-blue-700 font-medium">
                      You own {totalUnitsOwned} unit{totalUnitsOwned > 1 ? "s" : ""} · ${(totalUnitsOwned * reit.unitPrice).toLocaleString()} value
                    </p>
                  </div>
                )}

                <div className="flex gap-2">
                  <input
                    type="number"
                    min={1}
                    value={units[reit.id] ?? "1"}
                    onChange={(e) => setUnits((u) => ({ ...u, [reit.id]: e.target.value }))}
                    className="w-20 border border-gray-200 rounded-xl px-3 py-2 text-sm outline-none focus:border-blue-500"
                    placeholder="Units"
                  />
                  <button
                    onClick={() => handleInvest(reit)}
                    disabled={investing === reit.id}
                    className="flex-1 bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white font-semibold py-2 rounded-xl text-sm transition-colors flex items-center justify-center gap-2"
                  >
                    <DollarSign className="w-4 h-4" />
                    {investing === reit.id ? "Processing..." : "Buy Units"}
                  </button>
                </div>
                <p className="text-xs text-gray-400 mt-2">
                  Total: ${(Number(units[reit.id] || 1) * reit.unitPrice).toFixed(2)} USD
                </p>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
