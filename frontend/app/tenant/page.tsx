"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/lib/auth";
import { dashboards, users as usersApi, leases as leasesApi, ai as aiApi } from "@/lib/api";
import type { TenantDashboard, LandlordProfile, VerificationLevel, Lease, Escrow, LeaseActionRequest } from "@/lib/types";
import { settingsRoleUrl } from "@/lib/roleGate";
import HorizontalBarChart from "@/components/HorizontalBarChart";
import {
  Home,
  DollarSign,
  Wrench,
  Shield,
  FileText,
  AlertCircle,
  CheckCircle,
  Star,
  BadgeCheck,
  MessageCircle,
  RefreshCw,
  XCircle,
  Globe,
  Sparkles,
} from "lucide-react";

function StatusBadge({ status }: { status: string }) {
  const colors: Record<string, string> = {
    ACTIVE: "bg-green-100 text-green-700",
    SIGNED: "bg-blue-100 text-blue-700",
    DRAFT: "bg-gray-100 text-gray-700",
    CREATED: "bg-amber-100 text-amber-700",
    INITIATED: "bg-amber-100 text-amber-700",
    FUNDED: "bg-blue-100 text-blue-700",
    RELEASED: "bg-green-100 text-green-700",
    SUCCESSFUL: "bg-green-100 text-green-700",
    OPEN: "bg-red-100 text-red-700",
    RESOLVED: "bg-green-100 text-green-700",
    IN_PROGRESS: "bg-blue-100 text-blue-700",
    DISPUTED: "bg-red-100 text-red-700",
    REFUNDED: "bg-purple-100 text-purple-700",
  };
  return (
    <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${colors[status] ?? "bg-gray-100 text-gray-700"}`}>
      {status.replace("_", " ")}
    </span>
  );
}

function ordinal(day: number): string {
  const suffixes = ["th", "st", "nd", "rd"];
  const v = day % 100;
  return day + (suffixes[(v - 20) % 10] ?? suffixes[v] ?? suffixes[0]);
}

function EscrowTimeline({ escrow }: { escrow: Escrow }) {
  const isDisputed = escrow.status === "DISPUTED";
  const isRefunded = escrow.status === "REFUNDED";
  const isCancelled = escrow.status === "CANCELLED";
  const funded = ["FUNDED", "RELEASED", "DISPUTED", "REFUNDED"].includes(escrow.status);
  const finalStepDone = ["RELEASED", "DISPUTED", "REFUNDED"].includes(escrow.status);
  const finalStepLabel = isDisputed ? "Dispute Started" : isRefunded ? "Deposit Refunded" : "Deposit Released";

  const steps = [
    { label: "Deposit Paid", done: true },
    { label: "Escrow Protected", done: funded },
    { label: finalStepLabel, done: finalStepDone },
  ];

  return (
    <div className="flex items-center gap-1">
      {steps.map((step, i) => (
        <div key={step.label} className="flex items-center gap-1 flex-1">
          <div className="flex flex-col items-center flex-1">
            <div
              className={`w-5 h-5 rounded-full flex items-center justify-center text-[10px] font-bold ${
                step.done
                  ? isDisputed && i === 2
                    ? "bg-red-600 text-white"
                    : "bg-green-600 text-white"
                  : "bg-gray-100 text-gray-400"
              }`}
            >
              {step.done ? (i === 2 && isDisputed ? <XCircle className="w-3 h-3" /> : <CheckCircle className="w-3 h-3" />) : i + 1}
            </div>
            <span className="text-[10px] text-gray-500 mt-1 text-center">{step.label}</span>
          </div>
          {i < steps.length - 1 && <div className={`h-0.5 flex-1 -mt-4 ${step.done ? "bg-green-600" : "bg-gray-200"}`} />}
        </div>
      ))}
      {isCancelled && <span className="text-[10px] text-gray-400 ml-2">Cancelled</span>}
    </div>
  );
}

export default function TenantDashboardPage() {
  const { user, token, loading } = useAuth();
  const router = useRouter();
  const [dashboard, setDashboard] = useState<TenantDashboard | null>(null);
  const [dashLoading, setDashLoading] = useState(true);
  const [error, setError] = useState("");
  const [actionSuccess, setActionSuccess] = useState("");
  const [landlordProfile, setLandlordProfile] = useState<LandlordProfile | null>(null);
  const [verificationLevel, setVerificationLevel] = useState<VerificationLevel | null>(null);
  const [sendingActionLeaseId, setSendingActionLeaseId] = useState<number | null>(null);
  const [leaseActions, setLeaseActions] = useState<LeaseActionRequest[]>([]);
  const [explainingLeaseId, setExplainingLeaseId] = useState<number | null>(null);
  const [leaseExplanations, setLeaseExplanations] = useState<Record<number, string>>({});

  useEffect(() => {
    if (!loading && (!user || !user.roles?.includes("TENANT"))) {
      router.push(user ? settingsRoleUrl("TENANT", "the Tenant Dashboard needs the Tenant role") : "/login?redirect=/tenant");
    }
  }, [user, loading, router]);

  useEffect(() => {
    if (user && token && user.roles?.includes("TENANT")) {
      dashboards.tenant(user.id, token)
        .then(setDashboard)
        .catch(() => setError("Failed to load dashboard."))
        .finally(() => setDashLoading(false));
      usersApi.verificationLevel(user.id, token).then(setVerificationLevel).catch(() => {});
      leasesApi.myActions(token).then(setLeaseActions).catch(() => {});
    }
  }, [user, token]);

  const activeLeases = useMemo(() => dashboard?.activeLeases ?? [], [dashboard]);
  const recentPayments = dashboard?.recentPayments ?? [];
  const maintenance = dashboard?.maintenanceRequests ?? [];
  const escrows = dashboard?.escrows ?? [];

  const primaryLease: Lease | null = useMemo(() => {
    return (
      activeLeases.find((l) => l.status === "ACTIVE") ??
      activeLeases.find((l) => l.status === "SIGNED") ??
      activeLeases[0] ??
      null
    );
  }, [activeLeases]);

  const primaryEscrow: Escrow | null = useMemo(() => {
    if (!primaryLease) return escrows[0] ?? null;
    return escrows.find((e) => e.propertyId === primaryLease.propertyId) ?? escrows[0] ?? null;
  }, [escrows, primaryLease]);

  useEffect(() => {
    if (primaryLease && token) {
      usersApi.landlordProfile(primaryLease.landlordId, token).then(setLandlordProfile).catch(() => {});
    }
  }, [primaryLease, token]);

  const trustBullets = useMemo(() => {
    const bullets: string[] = [];
    if (user?.identityVerified) bullets.push("Verified identity");
    if (user?.faceVerified) bullets.push("Face verified");
    if (escrows.length > 0 && !escrows.some((e) => e.status === "DISPUTED")) bullets.push("No escrow disputes");
    if (recentPayments.length > 0) {
      const successRatio = recentPayments.filter((p) => p.status === "SUCCESSFUL").length / recentPayments.length;
      if (successRatio === 1) bullets.push("Excellent recent payment history");
      else if (successRatio >= 0.5) bullets.push("Good recent payment history");
    }
    const completedLeases = activeLeases.filter((l) => l.status === "ENDED").length;
    if (completedLeases > 0) bullets.push(`Completed ${completedLeases} previous lease${completedLeases > 1 ? "s" : ""}`);
    if ((user?.profileCompletion ?? 0) >= 80) bullets.push("Profile mostly complete");
    return bullets;
  }, [user, escrows, recentPayments, activeLeases]);

  const handleLeaseAction = async (lease: Lease, type: "RENEWAL" | "TERMINATION") => {
    if (!token) return;
    setError("");
    setActionSuccess("");
    setSendingActionLeaseId(lease.id);
    try {
      const created = await leasesApi.requestAction(lease.id, { type }, token);
      setLeaseActions((prev) => [created, ...prev]);
      setActionSuccess(`${type === "RENEWAL" ? "Renewal" : "Termination"} request sent to your landlord.`);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to send request.");
    } finally {
      setSendingActionLeaseId(null);
    }
  };

  const pendingActionFor = (leaseId: number, type: "RENEWAL" | "TERMINATION") =>
    leaseActions.find((a) => a.leaseId === leaseId && a.type === type && a.status === "PENDING");

  const handleExplainLease = async (lease: Lease) => {
    if (!token || leaseExplanations[lease.id]) return;
    setExplainingLeaseId(lease.id);
    try {
      const result = await aiApi.leaseExplanation(lease.id, token);
      setLeaseExplanations((prev) => ({ ...prev, [lease.id]: result.answer }));
    } catch (err: unknown) {
      setLeaseExplanations((prev) => ({
        ...prev,
        [lease.id]: err instanceof Error ? err.message : "Could not explain this lease right now.",
      }));
    } finally {
      setExplainingLeaseId(null);
    }
  };

  if (loading || dashLoading) {
    return (
      <div className="max-w-5xl mx-auto px-4 py-8 grid grid-cols-2 sm:grid-cols-4 gap-4">
        {Array.from({ length: 8 }).map((_, i) => (
          <div key={i} className="h-28 bg-gray-100 rounded-2xl animate-pulse" />
        ))}
      </div>
    );
  }

  if (error && !dashboard) {
    return (
      <div className="text-center py-24 text-gray-500">
        <AlertCircle className="w-12 h-12 mx-auto mb-4 text-red-400" />
        <p className="text-lg font-semibold">{error}</p>
        <Link href="/" className="mt-4 inline-block text-blue-600 hover:underline text-sm">← Home</Link>
      </div>
    );
  }

  return (
    <div className="max-w-5xl mx-auto px-4 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">Tenant HomeHub</h1>
        <p className="text-gray-500 mt-1">Welcome back, {user?.fullName?.split(" ")[0]}</p>
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
      {actionSuccess && (
        <div className="bg-green-50 border border-green-200 text-green-700 text-sm px-4 py-3 rounded-xl mb-5 flex items-center gap-2">
          <CheckCircle className="w-4 h-4 shrink-0" /> {actionSuccess}
        </div>
      )}

      {/* Home Overview */}
      {primaryLease ? (
        <div className="bg-gradient-to-br from-blue-600 to-blue-700 rounded-2xl p-6 text-white mb-8">
          <div className="flex items-start justify-between gap-4 flex-wrap">
            <div>
              <p className="text-blue-100 text-xs uppercase tracking-wide">Current Home</p>
              <Link href={`/properties/${primaryLease.propertyId}`} className="text-lg font-bold hover:underline">
                Property #{primaryLease.propertyId}
              </Link>
              <div className="flex items-center gap-2 mt-2">
                <StatusBadge status={primaryLease.status} />
                {primaryEscrow && <StatusBadge status={primaryEscrow.status} />}
              </div>
            </div>
            <div className="text-right">
              <p className="text-2xl font-bold">${primaryLease.monthlyRent}<span className="text-sm font-normal text-blue-200">/mo</span></p>
              <p className="text-xs text-blue-200 mt-1">
                Rent typically due around the {ordinal(new Date(primaryLease.startDate).getDate())} of each month
              </p>
            </div>
          </div>
          {landlordProfile && (
            <div className="mt-4 pt-4 border-t border-white/20 flex items-center justify-between flex-wrap gap-2">
              <div className="flex items-center gap-2 text-sm">
                <span className="text-blue-100">Landlord:</span>
                <span className="font-semibold">{landlordProfile.fullName}</span>
                {landlordProfile.verified && <BadgeCheck className="w-4 h-4 text-white" />}
              </div>
              <Link href="/messages" className="text-xs bg-white/20 hover:bg-white/30 px-3 py-1.5 rounded-lg transition-colors">
                Message Landlord
              </Link>
            </div>
          )}
        </div>
      ) : (
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm mb-8 text-center">
          <Home className="w-8 h-8 mx-auto mb-2 text-gray-300" />
          <p className="text-sm text-gray-500">No active lease yet.</p>
          <Link href="/properties" className="text-sm text-blue-600 hover:underline mt-1 inline-block">Browse properties</Link>
        </div>
      )}

      {/* Stat cards */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-8">
        {[
          { icon: Home, label: "Active Leases", value: activeLeases.length, color: "text-blue-600" },
          { icon: DollarSign, label: "Payments Made", value: recentPayments.filter(p => p.status === "SUCCESSFUL").length, color: "text-green-600" },
          { icon: Wrench, label: "Maintenance", value: maintenance.filter(m => m.status === "OPEN").length, color: "text-amber-600" },
          { icon: Shield, label: "Escrows", value: escrows.length, color: "text-indigo-600" },
        ].map(({ icon: Icon, label, value, color }) => (
          <div key={label} className="bg-white border border-gray-200 rounded-2xl p-5 shadow-sm">
            <Icon className={`w-6 h-6 ${color} mb-2`} />
            <p className="text-2xl font-bold text-gray-900">{value}</p>
            <p className="text-xs text-gray-500 mt-0.5">{label}</p>
          </div>
        ))}
      </div>

      {/* Trust score + Landlord profile */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
          <div className="flex items-center justify-between mb-3">
            <h2 className="font-bold text-gray-900 flex items-center gap-2">
              <Star className="w-5 h-5 text-amber-500" /> Tenant Trust Score
            </h2>
            <span className="text-2xl font-bold text-gray-900">{user?.trustScore ?? "—"}<span className="text-sm text-gray-400">/100</span></span>
          </div>
          {verificationLevel && (
            <p className="text-xs text-gray-500 mb-3">Level: {verificationLevel.label}</p>
          )}
          {trustBullets.length > 0 ? (
            <ul className="space-y-1.5">
              {trustBullets.map((b) => (
                <li key={b} className="text-sm text-gray-700 flex items-center gap-2">
                  <CheckCircle className="w-3.5 h-3.5 text-green-600 shrink-0" /> {b}
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-sm text-gray-400">Complete your profile and verification to build your trust score.</p>
          )}
          {!user?.identityVerified && (
            <Link href="/verification" className="mt-3 inline-block text-sm text-blue-600 hover:underline">
              Verify your identity →
            </Link>
          )}
          <Link href="/passport" className="mt-3 block text-sm text-blue-600 hover:underline">
            View my Tenant Passport →
          </Link>
        </div>

        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
          <h2 className="font-bold text-gray-900 mb-3 flex items-center gap-2">
            <Home className="w-5 h-5 text-blue-600" /> Landlord Relationship
          </h2>
          {landlordProfile ? (
            <div className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-gray-500">Name</span>
                <span className="font-medium text-gray-900 flex items-center gap-1">
                  {landlordProfile.fullName} {landlordProfile.verified && <BadgeCheck className="w-3.5 h-3.5 text-green-600" />}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500">Trust Score</span>
                <span className="font-medium text-gray-900">{landlordProfile.trustScore}/100</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500">Rating</span>
                <span className="font-medium text-gray-900">
                  {landlordProfile.ratingCount > 0 && landlordProfile.averageRating != null
                    ? `${landlordProfile.averageRating.toFixed(1)}/5 (${landlordProfile.ratingCount})`
                    : "No ratings yet"}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500">Properties</span>
                <span className="font-medium text-gray-900">{landlordProfile.propertyCount}</span>
              </div>
              <Link href="/messages" className="mt-2 inline-flex items-center gap-1 text-sm text-blue-600 hover:underline">
                <MessageCircle className="w-3.5 h-3.5" /> Message Landlord
              </Link>
            </div>
          ) : (
            <p className="text-sm text-gray-400 text-center py-6">No landlord to show yet</p>
          )}
        </div>
      </div>

      {/* Payment history */}
      <div className="bg-white border border-gray-200 rounded-2xl shadow-sm p-4 mb-8">
        <h3 className="font-bold text-gray-900 text-sm mb-4">Payment History (Last 6 Months)</h3>
        {!dashboard?.paymentTrend || dashboard.paymentTrend.every((m) => m.amount === 0) ? (
          <p className="text-sm text-gray-400 py-4 text-center">No payments recorded yet</p>
        ) : (
          <HorizontalBarChart data={dashboard.paymentTrend.map((m) => ({ label: m.month, value: m.amount }))} />
        )}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Leases */}
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-bold text-gray-900 flex items-center gap-2">
              <FileText className="w-5 h-5 text-blue-600" /> Leases
            </h2>
            <Link href="/leases" className="text-xs text-blue-600 hover:underline">View all</Link>
          </div>
          {activeLeases.length === 0 ? (
            <p className="text-sm text-gray-400 text-center py-6">No active leases</p>
          ) : (
            <div className="space-y-3">
              {activeLeases.map((lease) => (
                <div key={lease.id} className="p-3 bg-gray-50 rounded-xl">
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="text-sm font-medium text-gray-900">Lease #{lease.id}</p>
                      <p className="text-xs text-gray-500">{lease.startDate} → {lease.endDate}</p>
                      <p className="text-xs font-semibold text-blue-600 mt-0.5">${lease.monthlyRent}/mo</p>
                    </div>
                    <StatusBadge status={lease.status} />
                  </div>
                  {(lease.status === "ACTIVE" || lease.status === "SIGNED") && (
                    <div className="flex flex-wrap gap-3 mt-2 pt-2 border-t border-gray-100">
                      {pendingActionFor(lease.id, "RENEWAL") ? (
                        <span className="text-xs font-medium text-amber-600">Renewal request pending</span>
                      ) : (
                        <button
                          onClick={() => handleLeaseAction(lease, "RENEWAL")}
                          disabled={sendingActionLeaseId === lease.id}
                          className="flex items-center gap-1 text-xs font-medium text-blue-600 hover:text-blue-700 disabled:opacity-50"
                        >
                          <RefreshCw className="w-3 h-3" /> Request Renewal
                        </button>
                      )}
                      {pendingActionFor(lease.id, "TERMINATION") ? (
                        <span className="text-xs font-medium text-amber-600">Termination request pending</span>
                      ) : (
                        <button
                          onClick={() => handleLeaseAction(lease, "TERMINATION")}
                          disabled={sendingActionLeaseId === lease.id}
                          className="flex items-center gap-1 text-xs font-medium text-red-600 hover:text-red-700 disabled:opacity-50"
                        >
                          <XCircle className="w-3 h-3" /> Request Termination
                        </button>
                      )}
                      <button
                        onClick={() => handleExplainLease(lease)}
                        disabled={explainingLeaseId === lease.id}
                        className="flex items-center gap-1 text-xs font-medium text-purple-600 hover:text-purple-700 disabled:opacity-50"
                      >
                        <Sparkles className="w-3 h-3" /> {explainingLeaseId === lease.id ? "Explaining…" : "Explain My Lease"}
                      </button>
                    </div>
                  )}
                  {leaseExplanations[lease.id] && (
                    <p className="text-xs text-gray-600 mt-2 pt-2 border-t border-gray-100 leading-relaxed">
                      {leaseExplanations[lease.id]}
                    </p>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Payments */}
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-bold text-gray-900 flex items-center gap-2">
              <DollarSign className="w-5 h-5 text-green-600" /> Payments
            </h2>
            <Link href="/payments" className="text-xs text-blue-600 hover:underline">View all</Link>
          </div>
          {recentPayments.length === 0 ? (
            <p className="text-sm text-gray-400 text-center py-6">No payments yet</p>
          ) : (
            <div className="space-y-3">
              {recentPayments.slice(0, 5).map((payment) => (
                <div key={payment.id} className="flex items-center justify-between p-3 bg-gray-50 rounded-xl">
                  <div>
                    <p className="text-sm font-medium text-gray-900">{payment.purpose || "Payment"}</p>
                    <p className="text-xs text-gray-500">{new Date(payment.createdAt).toLocaleDateString()}</p>
                  </div>
                  <div className="text-right">
                    <p className="text-sm font-bold text-gray-900">${payment.amount}</p>
                    <StatusBadge status={payment.status} />
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Maintenance */}
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-bold text-gray-900 flex items-center gap-2">
              <Wrench className="w-5 h-5 text-amber-600" /> Maintenance
            </h2>
            <Link href="/maintenance" className="text-xs text-blue-600 hover:underline">View all</Link>
          </div>
          {maintenance.length === 0 ? (
            <p className="text-sm text-gray-400 text-center py-6">No maintenance requests</p>
          ) : (
            <div className="space-y-3">
              {maintenance.slice(0, 4).map((req) => (
                <div key={req.id} className="flex items-center justify-between p-3 bg-gray-50 rounded-xl">
                  <div>
                    <p className="text-sm font-medium text-gray-900">{req.category}</p>
                    <p className="text-xs text-gray-500">{req.description?.slice(0, 50)}...</p>
                  </div>
                  <StatusBadge status={req.status} />
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Escrow with deposit protection timeline */}
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-bold text-gray-900 flex items-center gap-2">
              <Shield className="w-5 h-5 text-indigo-600" /> Deposit Protection
            </h2>
            <Link href="/escrow" className="text-xs text-blue-600 hover:underline">View all</Link>
          </div>
          {escrows.length === 0 ? (
            <p className="text-sm text-gray-400 text-center py-6">No escrow transactions</p>
          ) : (
            <div className="space-y-4">
              {escrows.slice(0, 3).map((e) => (
                <div key={e.id} className="p-3 bg-gray-50 rounded-xl">
                  <div className="flex items-center justify-between mb-3">
                    <p className="text-sm font-medium text-gray-900">{e.purpose || "Escrow"} — ${e.amount} {e.currency}</p>
                  </div>
                  <EscrowTimeline escrow={e} />
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Quick actions */}
      <div className="mt-8 bg-blue-50 border border-blue-100 rounded-2xl p-6">
        <h2 className="font-bold text-gray-900 mb-4">Quick Actions</h2>
        <div className="flex flex-wrap gap-3">
          <Link href="/payments" className="bg-blue-600 text-white px-5 py-2.5 rounded-xl text-sm font-semibold hover:bg-blue-700 transition-colors">
            Pay Rent
          </Link>
          <Link href="/leases" className="bg-white text-gray-700 border border-gray-200 px-5 py-2.5 rounded-xl text-sm font-semibold hover:bg-gray-50 transition-colors">
            View Lease
          </Link>
          <Link href="/applications" className="bg-white text-gray-700 border border-gray-200 px-5 py-2.5 rounded-xl text-sm font-semibold hover:bg-gray-50 transition-colors">
            My Applications
          </Link>
          <Link href="/maintenance" className="bg-white text-gray-700 border border-gray-200 px-5 py-2.5 rounded-xl text-sm font-semibold hover:bg-gray-50 transition-colors">
            Report Maintenance
          </Link>
          <Link href="/messages" className="bg-white text-gray-700 border border-gray-200 px-5 py-2.5 rounded-xl text-sm font-semibold hover:bg-gray-50 transition-colors">
            Message Landlord
          </Link>
          <Link href="/ai" className="bg-white text-gray-700 border border-gray-200 px-5 py-2.5 rounded-xl text-sm font-semibold hover:bg-gray-50 transition-colors">
            Ask Home Assistant
          </Link>
          <Link href="/timeline" className="bg-white text-gray-700 border border-gray-200 px-5 py-2.5 rounded-xl text-sm font-semibold hover:bg-gray-50 transition-colors">
            Home Timeline
          </Link>
          <Link href="/ownership-journey" className="bg-white text-gray-700 border border-gray-200 px-5 py-2.5 rounded-xl text-sm font-semibold hover:bg-gray-50 transition-colors">
            Ownership Journey
          </Link>
          <Link href="/moving" className="bg-white text-gray-700 border border-gray-200 px-5 py-2.5 rounded-xl text-sm font-semibold hover:bg-gray-50 transition-colors">
            Moving Assistant
          </Link>
          <Link href="/properties" className="bg-white text-gray-700 border border-gray-200 px-5 py-2.5 rounded-xl text-sm font-semibold hover:bg-gray-50 transition-colors">
            Find New Home
          </Link>
          <Link href="/leases" className="bg-white text-gray-700 border border-gray-200 px-5 py-2.5 rounded-xl text-sm font-semibold hover:bg-gray-50 transition-colors">
            Download Documents
          </Link>
        </div>
      </div>
    </div>
  );
}
