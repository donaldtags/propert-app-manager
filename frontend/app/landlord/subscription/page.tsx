"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/lib/auth";
import { subscriptions as subscriptionsApi } from "@/lib/api";
import type { PlanSettings, SubscriptionInfo, SubscriptionFeature, SubscriptionPlan } from "@/lib/types";
import { settingsRoleUrl } from "@/lib/roleGate";
import {
  CreditCard,
  AlertCircle,
  CheckCircle,
  Check,
  X,
  ArrowLeft,
  Building2,
} from "lucide-react";

const BUSINESS_ROLES = ["LANDLORD", "AGENT", "DEVELOPER", "PRIVATE"] as const;

const FEATURE_LABELS: Record<SubscriptionFeature, string> = {
  ESCROW: "Escrow protection",
  DIGITAL_LEASES: "Digital leases",
  MAINTENANCE_REQUESTS: "Maintenance coordination",
  RENT_REMINDERS: "Rent reminders",
  AI_PRICING: "AI pricing suggestions",
  TENANT_PASSPORT: "Tenant Passport access",
  REPORTS: "Reports",
};

const PLAN_ORDER: SubscriptionPlan[] = ["STARTER", "GROWTH", "PROFESSIONAL"];

function featuresOf(plan: PlanSettings): SubscriptionFeature[] {
  const all: [SubscriptionFeature, boolean][] = [
    ["ESCROW", plan.escrowEnabled],
    ["DIGITAL_LEASES", plan.digitalLeasesEnabled],
    ["MAINTENANCE_REQUESTS", plan.maintenanceCoordinationEnabled],
    ["RENT_REMINDERS", plan.rentRemindersEnabled],
    ["AI_PRICING", plan.aiPricingEnabled],
    ["TENANT_PASSPORT", plan.tenantPassportEnabled],
    ["REPORTS", plan.reportsEnabled],
  ];
  return all.filter(([, enabled]) => enabled).map(([f]) => f);
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
}

export default function SubscriptionPage() {
  const { user, token, loading } = useAuth();
  const router = useRouter();

  const [plans, setPlans] = useState<PlanSettings[]>([]);
  const [mine, setMine] = useState<SubscriptionInfo | null>(null);
  const [dataLoading, setDataLoading] = useState(true);
  const [switchingTo, setSwitchingTo] = useState<SubscriptionPlan | null>(null);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const hasBusinessRole = (roles: string[] | undefined) =>
    !!roles?.some((r) => BUSINESS_ROLES.includes(r as typeof BUSINESS_ROLES[number]));

  useEffect(() => {
    if (!loading && (!user || !hasBusinessRole(user.roles))) {
      router.push(
        user
          ? settingsRoleUrl(
              ["LANDLORD", "AGENT", "DEVELOPER", "PRIVATE"],
              "managing a subscription needs a Landlord, Agent, Developer, or Private Seller role"
            )
          : "/login?redirect=/landlord/subscription"
      );
    }
  }, [user, loading, router]);

  const load = () => {
    subscriptionsApi.plans().then(setPlans).catch(() => {});
    if (token) {
      subscriptionsApi.mine(token).then(setMine).catch(() => {}).finally(() => setDataLoading(false));
    }
  };

  useEffect(() => {
    if (user && token && hasBusinessRole(user.roles)) load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user, token]);

  const orderedPlans = useMemo(
    () => PLAN_ORDER.map((p) => plans.find((pl) => pl.plan === p)).filter((p): p is PlanSettings => !!p),
    [plans]
  );

  const handleSwitch = async (plan: SubscriptionPlan) => {
    if (!token) return;
    setSwitchingTo(plan);
    setError("");
    setSuccess("");
    try {
      const updated = await subscriptionsApi.subscribe(plan, token);
      setMine(updated);
      setSuccess(
        plan === "STARTER"
          ? "Switched to the Starter plan."
          : `Upgraded to ${plan} — charged ${updated.currentPeriodEnd ? "" : ""}renews on ${updated.currentPeriodEnd ? formatDate(updated.currentPeriodEnd) : "next cycle"}.`
      );
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not change plan.");
    } finally {
      setSwitchingTo(null);
    }
  };

  if (loading || dataLoading) {
    return (
      <div className="max-w-5xl mx-auto px-4 py-8 grid grid-cols-1 sm:grid-cols-3 gap-4">
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="h-80 bg-gray-100 rounded-2xl animate-pulse" />
        ))}
      </div>
    );
  }

  return (
    <div className="max-w-5xl mx-auto px-4 py-8">
      <Link href="/landlord" className="text-sm text-gray-500 hover:text-gray-700 inline-flex items-center gap-1 mb-4">
        <ArrowLeft className="w-4 h-4" /> Back to dashboard
      </Link>

      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
          <CreditCard className="w-6 h-6 text-blue-600" /> Subscription Plan
        </h1>
        <p className="text-gray-500 mt-1">Choose the plan that fits your portfolio. Prices are set by PrimeNest.</p>
      </div>

      {mine && (
        <div className="bg-blue-50 border border-blue-100 rounded-2xl p-5 mb-6 flex items-center justify-between flex-wrap gap-3">
          <div className="flex items-center gap-3">
            <Building2 className="w-5 h-5 text-blue-600" />
            <div>
              <p className="text-sm font-semibold text-blue-900">
                Current plan: {mine.plan} · {mine.activePropertyCount}{mine.maxProperties != null ? ` / ${mine.maxProperties}` : ""} properties used
              </p>
              {mine.currentPeriodEnd && (
                <p className="text-xs text-blue-700 mt-0.5">
                  Renews on {formatDate(mine.currentPeriodEnd)} · status {mine.status}
                  {mine.status === "PAST_DUE" && " — renewal failed, will downgrade if not resolved"}
                </p>
              )}
            </div>
          </div>
          <Link href="/payments" className="text-xs text-blue-700 underline">View billing history</Link>
        </div>
      )}

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

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-5">
        {orderedPlans.map((plan) => {
          const isCurrent = mine?.plan === plan.plan;
          const enabled = featuresOf(plan);
          return (
            <div
              key={plan.plan}
              className={`bg-white rounded-2xl p-6 border shadow-sm flex flex-col ${
                isCurrent ? "border-blue-500 ring-2 ring-blue-100" : "border-gray-200"
              }`}
            >
              <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide">{plan.plan}</p>
              <p className="text-3xl font-bold text-gray-900 mt-1">
                {plan.monthlyPrice > 0 ? `$${plan.monthlyPrice.toFixed(0)}` : "Free"}
                {plan.monthlyPrice > 0 && <span className="text-sm font-normal text-gray-400">/mo</span>}
              </p>
              <p className="text-xs text-gray-500 mt-1">
                {plan.maxProperties != null ? `Up to ${plan.maxProperties} properties` : "Unlimited properties"}
              </p>

              <ul className="mt-4 space-y-2 flex-1">
                {(Object.keys(FEATURE_LABELS) as SubscriptionFeature[]).map((feature) => (
                  <li key={feature} className="flex items-center gap-2 text-sm">
                    {enabled.includes(feature) ? (
                      <Check className="w-4 h-4 text-green-600 shrink-0" />
                    ) : (
                      <X className="w-4 h-4 text-gray-300 shrink-0" />
                    )}
                    <span className={enabled.includes(feature) ? "text-gray-700" : "text-gray-400"}>
                      {FEATURE_LABELS[feature]}
                    </span>
                  </li>
                ))}
              </ul>

              <button
                onClick={() => handleSwitch(plan.plan)}
                disabled={isCurrent || switchingTo === plan.plan}
                className={`mt-5 w-full py-2.5 rounded-xl text-sm font-semibold transition-colors ${
                  isCurrent
                    ? "bg-gray-100 text-gray-400 cursor-default"
                    : "bg-blue-600 hover:bg-blue-700 text-white disabled:opacity-60"
                }`}
              >
                {isCurrent ? "Current plan" : switchingTo === plan.plan ? "Processing..." : plan.monthlyPrice > 0 ? "Upgrade" : "Switch to Starter"}
              </button>
            </div>
          );
        })}
      </div>
    </div>
  );
}