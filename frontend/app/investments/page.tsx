"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import Image from "next/image";
import Link from "next/link";
import { useAuth } from "@/lib/auth";
import { investments as investmentsApi, market as marketApi } from "@/lib/api";
import type { Reit, Investment, MarketSnapshot } from "@/lib/types";
import HorizontalBarChart from "@/components/HorizontalBarChart";
import { settingsRoleUrl } from "@/lib/roleGate";
import {
  TrendingUp,
  TrendingDown,
  DollarSign,
  AlertCircle,
  CheckCircle,
  MapPin,
  ChevronDown,
  Building2,
  ArrowUpRight,
  ArrowDownRight,
  Minus,
  Radio,
  Wallet,
  Layers,
  Globe,
} from "lucide-react";

const RISK_COLORS: Record<string, string> = {
  LOW: "bg-forest-600",
  MEDIUM: "bg-amber-500",
  HIGH: "bg-red-600",
};

const MARKET_POLL_MS = 30_000;

function formatRelativeTime(iso: string | null, now: number) {
  if (!iso) return "never";
  const seconds = Math.max(0, Math.round((now - new Date(iso).getTime()) / 1000));
  if (seconds < 5) return "just now";
  if (seconds < 60) return `${seconds}s ago`;
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.round(minutes / 60);
  return `${hours}h ago`;
}

function ZseReitMarketTable() {
  const [snapshot, setSnapshot] = useState<MarketSnapshot | null>(null);
  const [marketError, setMarketError] = useState(false);
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    let cancelled = false;
    const load = () => {
      marketApi
        .zwReits()
        .then((data) => {
          if (!cancelled) {
            setSnapshot(data);
            setMarketError(false);
          }
        })
        .catch(() => {
          if (!cancelled) setMarketError(true);
        });
    };
    load();
    const poll = setInterval(load, MARKET_POLL_MS);
    return () => {
      cancelled = true;
      clearInterval(poll);
    };
  }, []);

  useEffect(() => {
    const tick = setInterval(() => setNow(Date.now()), 5_000);
    return () => clearInterval(tick);
  }, []);

  const quotes = snapshot?.quotes ?? [];

  return (
    <div className="bg-white border border-gray-200 rounded-2xl shadow-sm mb-8 overflow-hidden">
      <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
        <div>
          <h2 className="font-bold text-gray-900 flex items-center gap-2">
            🇿🇼 Zimbabwe Listed REITs
          </h2>
          <p className="text-xs text-gray-500 mt-0.5">Exchange-traded REITs on the ZSE — market data, updated automatically</p>
        </div>
        <div className="flex items-center gap-1.5 text-xs font-medium">
          <span className={`relative flex h-2 w-2 ${snapshot && !snapshot.stale ? "" : "opacity-40"}`}>
            <span className={`absolute inline-flex h-full w-full rounded-full ${snapshot && !snapshot.stale ? "bg-forest-400 animate-ping" : "bg-gray-400"} opacity-75`} />
            <span className={`relative inline-flex rounded-full h-2 w-2 ${snapshot && !snapshot.stale ? "bg-forest-500" : "bg-gray-400"}`} />
          </span>
          <span className={snapshot && !snapshot.stale ? "text-forest-700" : "text-gray-500"}>
            {snapshot && !snapshot.stale ? "Live" : "Delayed"}
          </span>
          <span className="text-gray-400">· updated {formatRelativeTime(snapshot?.lastUpdated ?? null, now)}</span>
        </div>
      </div>

      {marketError && quotes.length === 0 ? (
        <div className="px-6 py-8 text-center text-sm text-gray-500 flex flex-col items-center gap-2">
          <AlertCircle className="w-5 h-5 text-gray-400" />
          Market data is temporarily unavailable.
        </div>
      ) : quotes.length === 0 ? (
        <div className="px-6 py-8 space-y-2">
          {Array.from({ length: 2 }).map((_, i) => (
            <div key={i} className="h-10 bg-gray-100 rounded-lg animate-pulse" />
          ))}
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-gray-500 uppercase tracking-wide bg-gray-50">
                <th className="px-6 py-2.5 font-medium">Ticker</th>
                <th className="px-6 py-2.5 font-medium">Name</th>
                <th className="px-6 py-2.5 font-medium text-right">Price</th>
                <th className="px-6 py-2.5 font-medium text-right">Change</th>
                <th className="px-6 py-2.5 font-medium text-right">Volume</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {quotes.map((q) => {
                const up = (q.changeAmount ?? 0) > 0;
                const down = (q.changeAmount ?? 0) < 0;
                return (
                  <tr key={q.ticker} className="hover:bg-gray-50 transition-colors">
                    <td className="px-6 py-3">
                      <span className="inline-flex items-center justify-center font-mono text-xs font-bold bg-forest-50 text-forest-700 rounded-md px-2 py-1">
                        {q.ticker}
                      </span>
                    </td>
                    <td className="px-6 py-3 text-gray-900">
                      {q.name}
                      <span className="text-gray-400 font-normal"> · {q.exchange}</span>
                    </td>
                    <td className="px-6 py-3 text-right font-semibold text-gray-900">
                      {q.price != null ? `${q.price.toFixed(4)} ${q.currency}` : "—"}
                    </td>
                    <td className="px-6 py-3 text-right">
                      <span
                        className={`inline-flex items-center justify-end gap-1 font-medium ${
                          up ? "text-forest-600" : down ? "text-red-600" : "text-gray-400"
                        }`}
                      >
                        {up ? <ArrowUpRight className="w-3.5 h-3.5" /> : down ? <ArrowDownRight className="w-3.5 h-3.5" /> : <Minus className="w-3.5 h-3.5" />}
                        {q.changePercent != null ? `${Math.abs(q.changePercent).toFixed(2)}%` : "0.00%"}
                      </span>
                    </td>
                    <td className="px-6 py-3 text-right text-gray-500">
                      {q.volume != null ? q.volume.toLocaleString() : "—"}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      <div className="px-6 py-3 bg-gray-50 border-t border-gray-100 flex items-start gap-2 text-xs text-gray-500">
        <Radio className="w-3.5 h-3.5 shrink-0 mt-0.5" />
        <p>
          Reference prices from the Zimbabwe Stock Exchange, refreshed automatically every 30 seconds. Trading these units
          requires a licensed stockbroker — they&apos;re shown for context and are separate from the app-managed REIT
          products below, which you can invest in directly.
        </p>
      </div>
    </div>
  );
}

const COUNTRY_FLAGS: Record<string, string> = {
  Zimbabwe: "🇿🇼",
  "South Africa": "🇿🇦",
  Zambia: "🇿🇲",
  Botswana: "🇧🇼",
  Namibia: "🇳🇦",
  Mozambique: "🇲🇿",
};

const PROPERTY_TYPE_LABELS: Record<string, string> = {
  RESIDENTIAL: "Residential",
  COMMERCIAL: "Commercial",
  INDUSTRIAL: "Industrial",
  MIXED_USE: "Mixed-Use",
  HOSPITALITY: "Hospitality",
};

function formatMoney(value: number, currency: string) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: currency || "USD", maximumFractionDigits: 0 }).format(value);
}

export default function InvestmentsPage() {
  const { user, token, loading } = useAuth();
  const router = useRouter();

  const [reits, setReits] = useState<Reit[]>([]);
  const [myInvestments, setMyInvestments] = useState<Investment[]>([]);
  const [reitsLoading, setReitsLoading] = useState(true);
  const [error, setError] = useState("");
  const [investing, setInvesting] = useState<number | null>(null);
  const [units, setUnits] = useState<Record<number, string>>({});
  const [selling, setSelling] = useState<number | null>(null);
  const [sellUnits, setSellUnits] = useState<Record<number, string>>({});
  const [success, setSuccess] = useState("");
  const [countryFilter, setCountryFilter] = useState<string>("All");
  const [expandedPortfolio, setExpandedPortfolio] = useState<number | null>(null);

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

  const countries = useMemo(() => {
    const unique = Array.from(new Set(reits.map((r) => r.market)));
    return ["All", ...unique];
  }, [reits]);

  const visibleReits = useMemo(
    () => (countryFilter === "All" ? reits : reits.filter((r) => r.market === countryFilter)),
    [reits, countryFilter]
  );

  const handleInvest = async (reit: Reit) => {
    if (!user || !token) { router.push("/login?redirect=/investments"); return; }
    if (!user.roles?.includes("INVESTOR") && !user.roles?.includes("DIASPORA")) {
      router.push(settingsRoleUrl(["INVESTOR", "DIASPORA"], "investing in a REIT needs the Investor role"));
      return;
    }
    const n = Number(units[reit.id] || 1);
    if (n < 1) { setError("Enter at least 1 unit."); return; }
    if (!reit.active) { setError("This REIT is not currently open for investment."); return; }
    if (reit.availableUnits != null && n > reit.availableUnits) {
      setError(`Only ${reit.availableUnits} units remain available.`);
      return;
    }
    setInvesting(reit.id);
    setError("");
    setSuccess("");
    try {
      await investmentsApi.invest({ reitId: reit.id, units: n, currency: "USD" }, token);
      setSuccess(`Invested ${n} unit${n > 1 ? "s" : ""} in ${reit.name}!`);
      const updated = await investmentsApi.listByInvestor(user.id, token);
      setMyInvestments(updated);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Investment failed.");
    } finally {setInvesting(null);
    }
  };

  const handleSell = async (reit: Reit) => {
    if (!user || !token) { router.push("/login?redirect=/investments"); return; }
    const owned = myInvestments.filter((i) => i.reitId === reit.id).reduce((s, i) => s + i.units, 0);
    const n = Number(sellUnits[reit.id] || 1);
    if (n < 1) { setError("Enter at least 1 unit to sell."); return; }
    if (n > owned) { setError(`You only own ${owned} unit${owned === 1 ? "" : "s"} in ${reit.name}.`); return; }
    setSelling(reit.id);
    setError("");
    setSuccess("");
    try {
      const updated = await investmentsApi.sell({ reitId: reit.id, units: n }, token);
      setSuccess(`Sold ${n} unit${n > 1 ? "s" : ""} of ${reit.name}!`);
      setMyInvestments(updated);
      setSellUnits((u) => ({ ...u, [reit.id]: "1" }));
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Sale failed.");
    } finally {
      setSelling(null);
    }
  };

  const activePositions = useMemo(
    () => myInvestments.filter((i) => i.status === "ACTIVE" && i.currency === "USD"),
    [myInvestments]
  );

  const portfolio = useMemo(() => {
    let costBasis = 0;
    let marketValue = 0;
    let projectedAnnualIncome = 0;
    let totalUnits = 0;
    const byReit = new Map<number, number>();
    const byRisk = new Map<string, number>();
    const reitIds = new Set<number>();

    for (const inv of activePositions) {
      const reit = reits.find((r) => r.id === inv.reitId);
      if (!reit) continue;
      const value = reit.unitPrice * inv.units;
      costBasis += inv.amount;
      marketValue += value;
      projectedAnnualIncome += (value * reit.projectedAnnualYield) / 100;
      totalUnits += inv.units;
      reitIds.add(reit.id);
      byReit.set(reit.id, (byReit.get(reit.id) ?? 0) + value);
      byRisk.set(reit.riskLevel, (byRisk.get(reit.riskLevel) ?? 0) + value);
    }

    const gainLoss = marketValue - costBasis;
    const gainLossPercent = costBasis > 0 ? (gainLoss / costBasis) * 100 : 0;

    const byHoldingChart = Array.from(byReit.entries())
      .map(([reitId, value]) => ({ label: reits.find((r) => r.id === reitId)?.name ?? `REIT #${reitId}`, value }))
      .sort((a, b) => b.value - a.value);

    const byRiskChart = Array.from(byRisk.entries()).map(([risk, value]) => ({
      label: `${risk} risk`,
      value,
      color: RISK_COLORS[risk],
    }));

    return {
      costBasis,
      marketValue,
      gainLoss,
      gainLossPercent,
      projectedAnnualIncome,
      totalUnits,
      holdings: reitIds.size,
      byHoldingChart,
      byRiskChart,
    };
  }, [activePositions, reits]);

  return (
    <div className="max-w-5xl mx-auto px-4 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
          <TrendingUp className="w-7 h-7 text-forest-600" /> REIT Investments
        </h1>
        <p className="text-gray-500 mt-1">Invest in verified real estate portfolios across Zimbabwe and Southern Africa</p>
      </div>

      {user?.roles?.includes("DIASPORA") && (
        <div className="bg-indigo-50 border border-indigo-100 text-indigo-700 text-sm px-4 py-3 rounded-xl mb-5 flex items-center gap-2">
          <Globe className="w-4 h-4 shrink-0" /> Managing remotely from {user.diasporaLocation || "abroad"}
        </div>
      )}

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

      <ZseReitMarketTable />

      {/* My portfolio dashboard */}
      {activePositions.length > 0 && (
        <>
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-4 mb-6">
            <div className="bg-white border border-gray-200 rounded-2xl p-5 shadow-sm">
              <Wallet className="w-6 h-6 text-forest-600 mb-2" />
              <p className="text-2xl font-bold text-gray-900">${portfolio.costBasis.toLocaleString()}</p>
              <p className="text-xs text-gray-500 mt-0.5">Cost Basis (USD)</p>
            </div>
            <div className="bg-white border border-gray-200 rounded-2xl p-5 shadow-sm">
              <DollarSign className="w-6 h-6 text-indigo-600 mb-2" />
              <p className="text-2xl font-bold text-gray-900">${portfolio.marketValue.toLocaleString()}</p>
              <p className="text-xs text-gray-500 mt-0.5">Current Market Value</p>
            </div>
            <div className="bg-white border border-gray-200 rounded-2xl p-5 shadow-sm">
              {portfolio.gainLoss >= 0 ? (
                <TrendingUp className="w-6 h-6 text-forest-600 mb-2" />
              ) : (
                <TrendingDown className="w-6 h-6 text-red-600 mb-2" />
              )}
              <p className={`text-2xl font-bold ${portfolio.gainLoss >= 0 ? "text-forest-600" : "text-red-600"}`}>
                {portfolio.gainLoss >= 0 ? "+" : ""}${portfolio.gainLoss.toLocaleString()}
              </p>
              <p className="text-xs text-gray-500 mt-0.5">
                Unrealized {portfolio.gainLoss >= 0 ? "Gain" : "Loss"} ({portfolio.gainLossPercent.toFixed(1)}%)
              </p>
            </div>
            <div className="bg-white border border-gray-200 rounded-2xl p-5 shadow-sm">
              <Layers className="w-6 h-6 text-purple-600 mb-2" />
              <p className="text-2xl font-bold text-gray-900">{portfolio.holdings}</p>
              <p className="text-xs text-gray-500 mt-0.5">Holdings</p>
            </div>
            <div className="bg-white border border-gray-200 rounded-2xl p-5 shadow-sm">
              <Building2 className="w-6 h-6 text-gray-600 mb-2" />
              <p className="text-2xl font-bold text-gray-900">{portfolio.totalUnits}</p>
              <p className="text-xs text-gray-500 mt-0.5">Total Units</p>
            </div>
            <div className="bg-white border border-gray-200 rounded-2xl p-5 shadow-sm">
              <TrendingUp className="w-6 h-6 text-amber-500 mb-2" />
              <p className="text-2xl font-bold text-gray-900">${portfolio.projectedAnnualIncome.toLocaleString()}</p>
              <p className="text-xs text-gray-500 mt-0.5">Projected Annual Income</p>
              <p className="text-[10px] text-gray-400 mt-1">Projected from stated yields — not a paid dividend</p>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-8">
            <div className="bg-white border border-gray-200 rounded-2xl shadow-sm p-4">
              <h3 className="font-bold text-gray-900 text-sm mb-4">Allocation by Holding</h3>
              <HorizontalBarChart data={portfolio.byHoldingChart} />
            </div>
            <div className="bg-white border border-gray-200 rounded-2xl shadow-sm p-4">
              <h3 className="font-bold text-gray-900 text-sm mb-4">Allocation by Risk Level</h3>
              <HorizontalBarChart data={portfolio.byRiskChart} />
            </div>
          </div>
        </>
      )}

      {/* Country filter */}
      {countries.length > 2 && (
        <div className="flex flex-wrap gap-2 mb-6">
          {countries.map((c) => (
            <button
              key={c}
              onClick={() => setCountryFilter(c)}
              className={`text-sm font-medium px-3.5 py-1.5 rounded-full border transition-colors ${
                countryFilter === c
                  ? "bg-forest-600 border-forest-600 text-white"
                  : "bg-white border-gray-200 text-gray-600 hover:border-forest-300"
              }`}
            >
              {c !== "All" && COUNTRY_FLAGS[c] ? `${COUNTRY_FLAGS[c]} ` : ""}{c}
            </button>
          ))}
        </div>
      )}

      <h2 className="font-bold text-gray-900 mb-4">Invest with PrimeNest</h2>

      {/* REIT cards */}
      {reitsLoading ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="h-80 bg-gray-100 rounded-2xl animate-pulse" />
          ))}
        </div>
      ) : visibleReits.length === 0 ? (
        <div className="text-center py-16 text-gray-500">
          <TrendingUp className="w-12 h-12 mx-auto mb-4 opacity-30" />
          <p className="text-lg font-medium">No REITs available yet</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
          {visibleReits.map((reit) => {
            const myPos = myInvestments.filter((i) => i.reitId === reit.id);
            const totalUnitsOwned = myPos.reduce((s, i) => s + i.units, 0);
            const soldPct = reit.totalUnits ? Math.round(((reit.totalUnits - (reit.availableUnits ?? 0)) / reit.totalUnits) * 100) : null;
            const isExpanded = expandedPortfolio === reit.id;
            return (
              <div key={reit.id} className="bg-white border border-gray-200 rounded-2xl overflow-hidden shadow-sm flex flex-col">
                {/* Cover image */}
                <div className="relative h-40 bg-gray-100 shrink-0">
                  {reit.coverImageUrl ? (
                    <Image
                      src={reit.coverImageUrl}
                      alt={reit.name}
                      fill
                      className="object-cover"
                      sizes="(max-width: 768px) 100vw, 400px"
                      unoptimized
                    />
                  ) : (
                    <div className="absolute inset-0 flex items-center justify-center text-gray-400">
                      <Building2 className="w-8 h-8" />
                    </div>
                  )}
                  <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-black/0 to-black/0" />
                  <div className="absolute bottom-3 left-4 right-4 flex items-end justify-between">
                    <div>
                      <p className="text-white font-bold leading-tight drop-shadow flex items-center gap-1.5">
                        {reit.name}
                        {reit.tickerSymbol && (
                          <span className="font-mono text-[10px] font-bold bg-white/90 text-forest-700 rounded-md px-1.5 py-0.5">
                            {reit.tickerSymbol}
                          </span>
                        )}
                      </p>
                      <p className="text-white/80 text-xs flex items-center gap-1 mt-0.5">
                        <MapPin className="w-3 h-3" /> {COUNTRY_FLAGS[reit.market] ?? ""} {reit.market}
                      </p>
                    </div>
                  </div>
                  <div className="absolute top-3 right-3 flex flex-col items-end gap-1.5">
                    <span className={`text-xs font-medium px-2 py-1 rounded-full ${
                      reit.riskLevel === "LOW" ? "bg-forest-100 text-forest-700" :
                      reit.riskLevel === "MEDIUM" ? "bg-amber-100 text-amber-700" :
                      "bg-red-100 text-red-700"
                    }`}>
                      {reit.riskLevel} risk
                    </span>
                    <span className="text-xs font-medium px-2 py-1 rounded-full bg-white/90 text-gray-700">
                      {PROPERTY_TYPE_LABELS[reit.propertyType] ?? reit.propertyType}
                    </span>
                    {(!reit.active || reit.availableUnits === 0) && (
                      <span className="text-xs font-medium px-2 py-1 rounded-full bg-gray-800/80 text-white">
                        {reit.active ? "Sold out" : "Closed"}
                      </span>
                    )}
                  </div>
                </div>

                <div className="p-6 flex-1 flex flex-col">
                  {reit.description && <p className="text-sm text-gray-600 mb-4">{reit.description}</p>}

                  {reit.tickerSymbol && (
                    <div className="mb-3 rounded-xl border border-gray-200 px-3 py-2.5 flex items-center justify-between">
                      {reit.marketQuote ? (
                        <>
                          <div className="flex items-center gap-2 text-xs text-gray-500">
                            <Radio className="w-3.5 h-3.5 text-forest-600" />
                            <span>Live ZSE: {reit.tickerSymbol}</span>
                          </div>
                          <div className="flex items-center gap-3">
                            <span className="font-semibold text-gray-900 text-sm">
                              {reit.marketQuote.price != null ? `${reit.marketQuote.price.toFixed(4)} ${reit.marketQuote.currency}` : "—"}
                            </span>
                            <span
                              className={`inline-flex items-center gap-1 text-xs font-medium ${
                                (reit.marketQuote.changeAmount ?? 0) > 0
                                  ? "text-forest-600"
                                  : (reit.marketQuote.changeAmount ?? 0) < 0
                                    ? "text-red-600"
                                    : "text-gray-400"
                              }`}
                            >
                              {(reit.marketQuote.changeAmount ?? 0) > 0 ? (
                                <ArrowUpRight className="w-3 h-3" />
                              ) : (reit.marketQuote.changeAmount ?? 0) < 0 ? (
                                <ArrowDownRight className="w-3 h-3" />
                              ) : (
                                <Minus className="w-3 h-3" />
                              )}
                              {reit.marketQuote.changePercent != null ? `${Math.abs(reit.marketQuote.changePercent).toFixed(2)}%` : "0.00%"}
                            </span>
                          </div>
                        </>
                      ) : (
                        <div className="flex items-center gap-2 text-xs text-gray-400">
                          <AlertCircle className="w-3.5 h-3.5" />
                          Live ZSE data for {reit.tickerSymbol} is temporarily unavailable
                        </div>
                      )}
                    </div>
                  )}

                  <div className="grid grid-cols-2 gap-3 mb-3">
                    <div className="bg-gray-50 rounded-xl p-3">
                      <p className="text-xs text-gray-500">Unit Price</p>
                      <p className="text-lg font-bold text-gray-900">${reit.unitPrice}</p>
                    </div>
                    <div className="bg-gray-50 rounded-xl p-3">
                      <p className="text-xs text-gray-500">Projected Yield</p>
                      <p className="text-lg font-bold text-forest-600">{reit.projectedAnnualYield}%</p>
                    </div>
                  </div>

                  {reit.investmentScore && (
                    <div className="mb-3 bg-indigo-50 border border-indigo-100 rounded-xl p-3">
                      <div className="flex items-center justify-between mb-2">
                        <p className="text-xs font-semibold text-indigo-700 uppercase tracking-wide">Investment Score</p>
                        <p className="text-lg font-bold text-indigo-700">{reit.investmentScore.overall}/100</p>
                      </div>
                      <div className="flex flex-wrap gap-3 text-xs text-indigo-900">
                        <span>Yield: <strong>{reit.investmentScore.yieldTier}</strong></span>
                        <span>Risk: <strong>{reit.investmentScore.riskTier}</strong></span>
                        <span>Demand: <strong>{reit.investmentScore.demandTier}</strong></span>
                      </div>
                    </div>
                  )}

                  {reit.availableUnits != null && soldPct != null && (
                    <div className="mb-3">
                      <div className="h-1.5 bg-gray-100 rounded-full overflow-hidden">
                        <div className="h-full bg-forest-500 rounded-full" style={{ width: `${soldPct}%` }} />
                      </div>
                      <p className="text-xs text-gray-400 mt-1">{reit.availableUnits} of {reit.totalUnits} units available · {soldPct}% subscribed</p>
                    </div>
                  )}

                  {reit.properties?.length > 0 && (
                    <button
                      onClick={() => setExpandedPortfolio(isExpanded ? null : reit.id)}
                      className="flex items-center justify-between text-sm font-medium text-forest-600 hover:text-forest-700 mb-3 py-1"
                    >
                      <span>View portfolio ({reit.properties.length} propert{reit.properties.length > 1 ? "ies" : "y"})</span>
                      <ChevronDown className={`w-4 h-4 transition-transform ${isExpanded ? "rotate-180" : ""}`} />
                    </button>
                  )}

                  {isExpanded && (
                    <div className="grid grid-cols-2 gap-2 mb-4">
                      {reit.properties.map((p) => (
                        <Link
                          key={p.id}
                          href={`/properties/${p.id}`}
                          className="group rounded-xl overflow-hidden border border-gray-200 hover:border-forest-300 transition-colors"
                        >
                          <div className="relative h-20 bg-gray-100">
                            {p.coverPhotoUrl ? (
                              <Image src={p.coverPhotoUrl} alt={p.title} fill className="object-cover" sizes="200px" unoptimized />
                            ) : (
                              <div className="absolute inset-0 flex items-center justify-center text-gray-400">
                                <MapPin className="w-5 h-5" />
                              </div>
                            )}
                          </div>
                          <div className="p-2">
                            <p className="text-xs font-medium text-gray-900 truncate group-hover:text-forest-600">{p.title}</p>
                            <p className="text-[11px] text-gray-500 truncate">{p.suburb}, {p.city}</p>
                            <p className="text-[11px] font-semibold text-gray-700 mt-0.5">{formatMoney(p.price, p.currency)}{p.listingType === "RENT" ? "/mo" : ""}</p>
                          </div>
                        </Link>
                      ))}
                    </div>
                  )}

                  {totalUnitsOwned > 0 && (
                    <div className="bg-forest-50 border border-forest-100 rounded-xl p-3 mb-4">
                      <p className="text-xs text-forest-700 font-medium">
                        You own {totalUnitsOwned} unit{totalUnitsOwned > 1 ? "s" : ""} · ${(totalUnitsOwned * reit.unitPrice).toLocaleString()} value
                      </p>
                    </div>
                  )}

                  <div className="mt-auto space-y-3">
                    <div>
                      <div className="flex gap-2">
                        <input
                          type="number"
                          min={1}
                          value={units[reit.id] ?? "1"}
                          onChange={(e) => setUnits((u) => ({ ...u, [reit.id]: e.target.value }))}
                          className="w-20 border border-gray-200 rounded-xl px-3 py-2 text-sm outline-none focus:border-forest-500"
                          placeholder="Units"
                        />
                        <button
                          onClick={() => handleInvest(reit)}
                          disabled={investing === reit.id || !reit.active || reit.availableUnits === 0}
                          title={`Buy units of ${reit.name}`}
                          className="flex-1 bg-forest-600 hover:bg-forest-700 disabled:opacity-60 text-white font-semibold py-2 rounded-xl text-sm transition-colors flex items-center justify-center gap-2"
                        >
                          <DollarSign className="w-4 h-4 shrink-0" />
                          <span className="truncate">
                            {investing === reit.id ? "Processing..." : `Buy ${reit.name} Units`}
                          </span>
                        </button>
                      </div>
                      <p className="text-xs text-gray-400 mt-2">
                        Total: ${(Number(units[reit.id] || 1) * reit.unitPrice).toFixed(2)} USD
                      </p>
                    </div>

                    <div>
                      <div className="flex gap-2">
                        <input
                          type="number"
                          min={1}
                          max={totalUnitsOwned || undefined}
                          value={sellUnits[reit.id] ?? "1"}
                          onChange={(e) => setSellUnits((u) => ({ ...u, [reit.id]: e.target.value }))}
                          disabled={totalUnitsOwned === 0}
                          className="w-20 border border-gray-200 rounded-xl px-3 py-2 text-sm outline-none focus:border-red-400 disabled:opacity-50 disabled:bg-gray-50"
                          placeholder="Units"
                        />
                        <button
                          onClick={() => handleSell(reit)}
                          disabled={selling === reit.id || totalUnitsOwned === 0}
                          title={`Sell units of ${reit.name}`}
                          className="flex-1 bg-white hover:bg-red-50 disabled:opacity-50 text-red-600 font-semibold py-2 rounded-xl text-sm transition-colors flex items-center justify-center gap-2 border border-red-200"
                        >
                          <Minus className="w-4 h-4 shrink-0" />
                          <span className="truncate">
                            {selling === reit.id ? "Processing..." : `Sell ${reit.name} Units`}
                          </span>
                        </button>
                      </div>
                      <p className="text-xs text-gray-400 mt-2">
                        {totalUnitsOwned > 0
                          ? `You can sell up to ${totalUnitsOwned} unit${totalUnitsOwned > 1 ? "s" : ""}`
                          : "You don't own any units to sell yet"}
                      </p>
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
