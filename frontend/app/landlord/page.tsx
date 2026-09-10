"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/lib/auth";
import { dashboards, properties as propertiesApi, viewings as viewingsApi, leases as leasesApi } from "@/lib/api";
import type { LandlordDashboard, PropertyInquiry, PropertyStatus, Viewing, LeaseActionRequest } from "@/lib/types";
import { settingsRoleUrl } from "@/lib/roleGate";
import HorizontalBarChart from "@/components/HorizontalBarChart";
import StatusBadge from "@/components/StatusBadge";
import EmptyState from "@/components/EmptyState";
import StatTile from "@/components/StatTile";
import AlertBanner from "@/components/AlertBanner";
import {
  Home,
  DollarSign,
  Wrench,
  FileText,
  Plus,
  AlertCircle,
  MessageCircle,
  Mail,
  Phone,
  Building2,
  TrendingUp,
  Lock,
  Star,
  ThumbsUp,
  Clock,
  Globe,
  RefreshCw,
} from "lucide-react";

const BUSINESS_ROLES = ["LANDLORD", "AGENT", "DEVELOPER", "PRIVATE"] as const;

function workspaceTitle(roles: string[] | undefined): { title: string; subtitle: string } {
  if (!roles?.includes("LANDLORD")) {
    if (roles?.includes("AGENT")) return { title: "Agent Workspace", subtitle: "Manage the properties you represent" };
    if (roles?.includes("DEVELOPER")) return { title: "Developer Workspace", subtitle: "Manage your development projects" };
    if (roles?.includes("PRIVATE")) return { title: "Seller Workspace", subtitle: "Manage your property listings" };
  }
  return { title: "Property Business Hub", subtitle: "Run your property business from one place" };
}

const OCCUPANCY_COLORS: Partial<Record<PropertyStatus, string>> = {
  DRAFT: "bg-gray-300",
  AVAILABLE: "bg-forest-500",
  RESERVED: "bg-amber-500",
  OCCUPIED: "bg-forest-600",
  SOLD: "bg-purple-500",
  INACTIVE: "bg-gray-400",
};

export default function LandlordDashboardPage() {
  const { user, token, loading } = useAuth();
  const router = useRouter();
  const [dashboard, setDashboard] = useState<LandlordDashboard | null>(null);
  const [dashLoading, setDashLoading] = useState(true);
  const [error, setError] = useState("");
  const [inquiries, setInquiries] = useState<PropertyInquiry[]>([]);
  const [viewingRequests, setViewingRequests] = useState<Viewing[]>([]);
  const [viewingActionId, setViewingActionId] = useState<number | null>(null);
  const [checkInCode, setCheckInCode] = useState<Record<number, string>>({});
  const [videoLinkDraft, setVideoLinkDraft] = useState<Record<number, string>>({});
  const [leaseActionRequests, setLeaseActionRequests] = useState<LeaseActionRequest[]>([]);
  const [leaseActionReviewingId, setLeaseActionReviewingId] = useState<number | null>(null);

  const hasBusinessRole = (roles: string[] | undefined) => !!roles?.some((r) => BUSINESS_ROLES.includes(r as typeof BUSINESS_ROLES[number]));

  useEffect(() => {
    if (!loading && (!user || !hasBusinessRole(user.roles))) {
      router.push(
        user
          ? settingsRoleUrl(
              ["LANDLORD", "AGENT", "DEVELOPER", "PRIVATE"],
              "the Property Business Hub needs a Landlord, Agent, Developer, or Private Seller role"
            )
          : "/login?redirect=/landlord"
      );
    }
  }, [user, loading, router]);

  const loadViewings = async () => {
    if (!user || !token) return;
    const isAgent = user.roles?.includes("AGENT");
    const results = await Promise.allSettled([
      viewingsApi.listByLandlord(user.id, token),
      isAgent ? viewingsApi.listByAgent(user.id, token) : Promise.resolve([] as Viewing[]),
    ]);
    const all: Viewing[] = [];
    results.forEach((r) => { if (r.status === "fulfilled") all.push(...r.value); });
    const unique = all.filter((v, i, arr) => arr.findIndex((x) => x.id === v.id) === i);
    setViewingRequests(unique);
  };

  useEffect(() => {
    if (user && token && hasBusinessRole(user.roles)) {
      dashboards.landlord(user.id, token)
        .then(setDashboard)
        .catch(() => setError("Failed to load dashboard."))
        .finally(() => setDashLoading(false));
      propertiesApi.myInquiries(token).then(setInquiries).catch(() => {});
      loadViewings();
      leasesApi.receivedActions(token).then(setLeaseActionRequests).catch(() => {});
    }
  }, [user, token]);

  const handleReviewLeaseAction = async (id: number, status: "APPROVED" | "DECLINED") => {
    if (!token) return;
    setLeaseActionReviewingId(id);
    try {
      const updated = await leasesApi.reviewAction(id, { status }, token);
      setLeaseActionRequests((prev) => prev.map((a) => (a.id === id ? updated : a)));
    } catch {} finally {
      setLeaseActionReviewingId(null);
    }
  };

  const handleConfirmViewing = async (viewingId: number, videoCallLink?: string) => {
    if (!token) return;
    setViewingActionId(viewingId);
    try {
      await viewingsApi.confirm(viewingId, { videoCallLink }, token);
      await loadViewings();
    } catch {} finally {
      setViewingActionId(null);
    }
  };

  const handleDeclineViewing = async (viewingId: number) => {
    if (!token) return;
    setViewingActionId(viewingId);
    try {
      await viewingsApi.decline(viewingId, token);
      await loadViewings();
    } catch {} finally {
      setViewingActionId(null);
    }
  };

  const handleCheckInViewing = async (viewingId: number) => {
    if (!token) return;
    const code = checkInCode[viewingId];
    if (!code) return;
    setViewingActionId(viewingId);
    try {
      await viewingsApi.checkIn(viewingId, code, token);
      await loadViewings();
    } catch {} finally {
      setViewingActionId(null);
    }
  };

  const ownProperties = dashboard?.properties ?? [];
  const leases = dashboard?.activeLeases ?? [];
  const payments = dashboard?.recentPayments ?? [];
  const maintenance = dashboard?.maintenanceRequests ?? [];
  const financials = dashboard?.financials;
  const occupancy = dashboard?.occupancy;
  const escrow = dashboard?.escrow;
  const satisfaction = dashboard?.satisfaction;
  const currency = financials?.currency ?? "USD";

  const incomeTrendData = useMemo(
    () => (dashboard?.incomeTrend ?? []).map((m) => ({ label: m.month, value: m.amount })),
    [dashboard]
  );

  const occupancyBreakdown = useMemo(() => {
    const counts = new Map<string, number>();
    ownProperties.forEach((p) => counts.set(p.status, (counts.get(p.status) ?? 0) + 1));
    return Array.from(counts.entries()).map(([status, value]) => ({
      label: status,
      value,
      color: OCCUPANCY_COLORS[status as PropertyStatus],
    }));
  }, [ownProperties]);

  const upcomingExpiries = useMemo(() => {
    const now = Date.now();
    const in30Days = now + 30 * 24 * 60 * 60 * 1000;
    return leases
      .filter((l) => (l.status === "ACTIVE" || l.status === "SIGNED") && l.endDate)
      .map((l) => ({ lease: l, endTime: new Date(l.endDate).getTime() }))
      .filter((l) => l.endTime >= now && l.endTime <= in30Days)
      .sort((a, b) => a.endTime - b.endTime)
      .map((l) => l.lease);
  }, [leases]);

  if (loading || dashLoading) {
    return (
      <div className="max-w-5xl mx-auto px-4 py-8">
        <div className="h-8 w-64 bg-gray-100 rounded-lg animate-pulse mb-8" />
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-6">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="h-28 bg-gray-100 rounded-2xl animate-pulse" />
          ))}
        </div>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-8">
          {Array.from({ length: 7 }).map((_, i) => (
            <div key={i} className="h-24 bg-gray-100 rounded-2xl animate-pulse" />
          ))}
        </div>
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-8">
          {Array.from({ length: 2 }).map((_, i) => (
            <div key={i} className="h-40 bg-gray-100 rounded-2xl animate-pulse" />
          ))}
        </div>
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="h-48 bg-gray-100 rounded-2xl animate-pulse" />
          ))}
        </div>
      </div>
    );
  }

  // Headline metrics get the emphasized tile treatment; everything else is supporting detail.
  const headlineTiles = [
    {
      icon: DollarSign,
      label: `Monthly Income (${currency})`,
      value: `$${(financials?.monthlyIncome ?? 0).toLocaleString()}`,
      tone: "forest" as const,
    },
    {
      icon: AlertCircle,
      label: "Outstanding Rent",
      value: `$${(financials?.outstandingRent ?? 0).toLocaleString()}`,
      sublabel: financials && financials.overdueInvoiceCount > 0 ? `${financials.overdueInvoiceCount} overdue` : undefined,
      tone: "terracotta" as const,
    },
    {
      icon: TrendingUp,
      label: "Occupancy Rate",
      value: `${(occupancy?.occupancyRatePercent ?? 0).toFixed(1)}%`,
      tone: "forest" as const,
    },
    {
      icon: Lock,
      label: "Escrow Balance",
      value: `$${(escrow?.balance ?? 0).toLocaleString()}`,
      tone: "forest" as const,
    },
  ];

  const secondaryTiles = [
    { icon: Home, label: "Properties", value: ownProperties.length, tone: "forest" as const },
    { icon: Building2, label: "Occupied Units", value: occupancy?.occupiedUnits ?? 0, tone: "forest" as const },
    { icon: Building2, label: "Vacant Units", value: occupancy?.vacantUnits ?? 0, tone: "gray" as const },
    {
      icon: DollarSign,
      label: `Expected Income (${currency})`,
      value: `$${(financials?.expectedMonthlyIncome ?? 0).toLocaleString()}`,
      tone: "gold" as const,
    },
    {
      icon: Home,
      label: "Total Listed Value",
      value: `$${(financials?.portfolioValue ?? 0).toLocaleString()}`,
      tone: "gold" as const,
    },
    { icon: Star, label: "Trust Score", value: dashboard?.landlord.trustScore ?? "—", tone: "gold" as const },
    {
      icon: ThumbsUp,
      label: "Tenant Satisfaction",
      value: satisfaction && satisfaction.ratingCount > 0 && satisfaction.averageRating != null
        ? `${satisfaction.averageRating.toFixed(1)}/5`
        : "No ratings yet",
      tone: "gold" as const,
    },
  ];

  const { title: pageTitle, subtitle: pageSubtitle } = workspaceTitle(user?.roles);

  return (
    <div className="max-w-5xl mx-auto px-4 py-8">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-8">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{pageTitle}</h1>
          <p className="text-gray-500 mt-1">{pageSubtitle}</p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <Link
            href="/landlord/subscription"
            className="flex items-center gap-2 bg-white border border-gray-200 hover:border-forest-300 text-gray-700 px-5 py-2.5 rounded-xl font-semibold text-sm transition-colors"
          >
            Subscription
          </Link>
          <Link
            href="/properties/new"
            className="flex items-center gap-2 bg-forest-600 hover:bg-forest-700 text-white px-5 py-2.5 rounded-xl font-semibold text-sm transition-colors"
          >
            <Plus className="w-4 h-4" /> Add Property
          </Link>
        </div>
      </div>

      {user?.roles?.includes("DIASPORA") && (
        <AlertBanner variant="info" icon={Globe} className="mb-6">
          Managing remotely from {user.diasporaLocation || "abroad"}
        </AlertBanner>
      )}

      {error && <AlertBanner variant="error" className="mb-6">{error}</AlertBanner>}

      {/* Quick actions */}
      <div className="flex flex-wrap gap-3 mb-6">
        <Link
          href="/leases"
          className="flex items-center gap-1.5 text-sm font-medium text-forest-600 hover:text-forest-700 border border-forest-200 hover:border-forest-300 px-4 py-2 rounded-xl transition-colors"
        >
          <Plus className="w-3.5 h-3.5" /> Create Lease
        </Link>
        <Link
          href="/messages"
          className="flex items-center gap-1.5 text-sm font-medium text-gray-700 hover:text-forest-600 border border-gray-200 hover:border-forest-300 px-4 py-2 rounded-xl transition-colors"
        >
          <MessageCircle className="w-3.5 h-3.5" /> Message Tenants
        </Link>
        <Link
          href="/applications"
          className="flex items-center gap-1.5 text-sm font-medium text-gray-700 hover:text-forest-600 border border-gray-200 hover:border-forest-300 px-4 py-2 rounded-xl transition-colors"
        >
          <FileText className="w-3.5 h-3.5" /> Applications Received
        </Link>
      </div>

      {/* Headline KPIs */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-6">
        {headlineTiles.map((tile) => (
          <StatTile key={tile.label} {...tile} emphasis />
        ))}
      </div>

      {/* Supporting metrics */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-8">
        {secondaryTiles.map((tile) => (
          <StatTile key={tile.label} {...tile} />
        ))}
      </div>

      {/* Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-8">
        <div className="bg-white border border-gray-200 rounded-2xl shadow-sm p-4">
          <h3 className="font-bold text-gray-900 text-sm mb-4">Income Trend (Last 6 Months)</h3>
          {incomeTrendData.every((d) => d.value === 0) ? (
            <EmptyState icon={DollarSign} title="No income recorded yet" compact />
          ) : (
            <HorizontalBarChart data={incomeTrendData} />
          )}
        </div>
        <div className="bg-white border border-gray-200 rounded-2xl shadow-sm p-4">
          <h3 className="font-bold text-gray-900 text-sm mb-4">Occupancy Breakdown</h3>
          {occupancyBreakdown.length === 0 ? (
            <EmptyState icon={Building2} title="No properties yet" compact />
          ) : (
            <HorizontalBarChart data={occupancyBreakdown} />
          )}
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Upcoming lease expiries */}
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-bold text-gray-900 flex items-center gap-2">
              <Clock className="w-4 h-4 text-amber-600" /> Upcoming Lease Expiries
            </h2>
            <Link href="/leases" className="text-xs text-forest-600 hover:underline">View all</Link>
          </div>
          {upcomingExpiries.length === 0 ? (
            <EmptyState icon={Clock} title="No leases expiring in the next 30 days" compact />
          ) : (
            <div className="space-y-3">
              {upcomingExpiries.map((lease) => (
                <div key={lease.id} className="flex items-center justify-between p-3 bg-gray-50 rounded-xl">
                  <div>
                    <p className="text-sm font-medium text-gray-900">Lease #{lease.id}</p>
                    <p className="text-xs text-gray-500">Tenant #{lease.tenantId}</p>
                  </div>
                  <p className="text-xs font-semibold text-amber-600">{new Date(lease.endDate).toLocaleDateString()}</p>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Maintenance */}
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-bold text-gray-900 flex items-center gap-2">
              <Wrench className="w-4 h-4 text-gray-500" /> Maintenance Requests
            </h2>
            <Link href="/maintenance" className="text-xs text-forest-600 hover:underline">View all</Link>
          </div>
          {maintenance.length === 0 ? (
            <EmptyState icon={Wrench} title="No maintenance requests" compact />
          ) : (
            <div className="space-y-3">
              {maintenance.slice(0, 4).map((req) => (
                <div key={req.id} className="flex items-center justify-between p-3 bg-gray-50 rounded-xl">
                  <div>
                    <p className="text-sm font-medium text-gray-900">{req.category}</p>
                    <p className="text-xs text-gray-500">
                      {(req.description?.length ?? 0) > 50 ? `${req.description!.slice(0, 50)}...` : req.description}
                    </p>
                  </div>
                  <StatusBadge status={req.status} />
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Viewing Requests */}
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-bold text-gray-900 flex items-center gap-2">
              <Clock className="w-4 h-4 text-gray-500" /> Viewing Requests
            </h2>
          </div>
          {viewingRequests.length === 0 ? (
            <EmptyState icon={Clock} title="No viewing requests" compact />
          ) : (
            <div className="space-y-3">
              {viewingRequests.map((v) => (
                <div key={v.id} className="p-3 bg-gray-50 rounded-xl">
                  <div className="flex items-center justify-between mb-1">
                    <p className="text-sm font-semibold text-gray-900">{v.propertyTitle}</p>
                    <StatusBadge status={v.status} />
                  </div>
                  <p className="text-xs text-gray-500">
                    {v.requesterName} · {v.mode === "VIDEO_CALL" ? "Video call" : "In-person"}
                    {v.preferredDate ? ` · ${v.preferredDate}` : ""}{v.preferredTime ? ` ${v.preferredTime}` : ""}
                  </p>
                  {v.notes && <p className="text-xs text-gray-500 mt-1 italic">&ldquo;{v.notes}&rdquo;</p>}

                  {v.status === "REQUESTED" && (
                    <div className="mt-2">
                      {v.mode === "VIDEO_CALL" && (
                        <input
                          type="text"
                          placeholder="Video call link (optional)"
                          value={videoLinkDraft[v.id] ?? ""}
                          onChange={(e) => setVideoLinkDraft((c) => ({ ...c, [v.id]: e.target.value }))}
                          className="w-full text-xs border border-gray-200 rounded-lg px-2.5 py-1.5 outline-none focus:border-forest-500 mb-2"
                        />
                      )}
                      <div className="flex gap-2">
                        <button
                          onClick={() => handleConfirmViewing(v.id, videoLinkDraft[v.id] || undefined)}
                          disabled={viewingActionId === v.id}
                          className="text-xs font-semibold bg-forest-600 hover:bg-forest-700 disabled:opacity-60 text-white px-3 py-1.5 rounded-lg"
                        >
                          Confirm
                        </button>
                        <button
                          onClick={() => handleDeclineViewing(v.id)}
                          disabled={viewingActionId === v.id}
                          className="text-xs font-semibold border border-gray-200 hover:bg-gray-100 disabled:opacity-60 text-gray-600 px-3 py-1.5 rounded-lg"
                        >
                          Decline
                        </button>
                      </div>
                    </div>
                  )}

                  {v.status === "CONFIRMED" && (
                    <div className="flex items-center gap-2 mt-2">
                      <input
                        type="text"
                        placeholder="Enter check-in code"
                        value={checkInCode[v.id] ?? ""}
                        onChange={(e) => setCheckInCode((c) => ({ ...c, [v.id]: e.target.value }))}
                        className="flex-1 text-xs border border-gray-200 rounded-lg px-2.5 py-1.5 outline-none focus:border-forest-500"
                      />
                      <button
                        onClick={() => handleCheckInViewing(v.id)}
                        disabled={viewingActionId === v.id || !checkInCode[v.id]}
                        className="text-xs font-semibold bg-forest-600 hover:bg-forest-700 disabled:opacity-60 text-white px-3 py-1.5 rounded-lg"
                      >
                        Check In
                      </button>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Lease renewal/termination requests */}
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-bold text-gray-900 flex items-center gap-2">
              <RefreshCw className="w-4 h-4 text-gray-500" /> Lease Requests
            </h2>
          </div>
          {leaseActionRequests.length === 0 ? (
            <EmptyState icon={RefreshCw} title="No renewal or termination requests" compact />
          ) : (
            <div className="space-y-3">
              {leaseActionRequests.map((a) => (
                <div key={a.id} className="p-3 bg-gray-50 rounded-xl">
                  <div className="flex items-center justify-between mb-1">
                    <p className="text-sm font-semibold text-gray-900">
                      {a.type === "RENEWAL" ? "Renewal" : "Termination"} — Lease #{a.leaseId}
                    </p>
                    <StatusBadge status={a.status} />
                  </div>
                  <p className="text-xs text-gray-500">Requested by {a.requestedByName}</p>
                  {a.note && <p className="text-xs text-gray-500 mt-1 italic">&ldquo;{a.note}&rdquo;</p>}
                  {a.status === "PENDING" && (
                    <div className="flex gap-2 mt-2">
                      <button
                        onClick={() => handleReviewLeaseAction(a.id, "APPROVED")}
                        disabled={leaseActionReviewingId === a.id}
                        className="text-xs font-semibold bg-forest-600 hover:bg-forest-700 disabled:opacity-60 text-white px-3 py-1.5 rounded-lg"
                      >
                        Approve
                      </button>
                      <button
                        onClick={() => handleReviewLeaseAction(a.id, "DECLINED")}
                        disabled={leaseActionReviewingId === a.id}
                        className="text-xs font-semibold border border-gray-200 hover:bg-gray-100 disabled:opacity-60 text-gray-600 px-3 py-1.5 rounded-lg"
                      >
                        Decline
                      </button>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Properties */}
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-bold text-gray-900 flex items-center gap-2">
              <FileText className="w-4 h-4 text-gray-500" /> Leases
            </h2>
            <Link href="/leases" className="text-xs text-forest-600 hover:underline">View all</Link>
          </div>
          {leases.length === 0 ? (
            <EmptyState icon={FileText} title="No leases" compact />
          ) : (
            <div className="space-y-3">
              {leases.slice(0, 5).map((lease) => (
                <div key={lease.id} className="flex items-center justify-between p-3 bg-gray-50 rounded-xl">
                  <div>
                    <p className="text-sm font-medium text-gray-900">Lease #{lease.id}</p>
                    <p className="text-xs text-gray-500">Tenant #{lease.tenantId}</p>
                    <p className="text-xs font-semibold text-forest-600">${lease.monthlyRent}/mo</p>
                  </div>
                  <StatusBadge status={lease.status} />
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Payments */}
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-bold text-gray-900 flex items-center gap-2">
              <DollarSign className="w-4 h-4 text-gray-500" /> Recent Payments
            </h2>
            <Link href="/payments" className="text-xs text-forest-600 hover:underline">View all</Link>
          </div>
          {payments.length === 0 ? (
            <EmptyState icon={DollarSign} title="No payments yet" compact />
          ) : (
            <div className="space-y-3">
              {payments.slice(0, 5).map((p) => (
                <div key={p.id} className="flex items-center justify-between p-3 bg-gray-50 rounded-xl">
                  <div>
                    <p className="text-sm font-medium text-gray-900">{p.purpose || "Rent Payment"}</p>
                    <p className="text-xs text-gray-500">From Tenant #{p.payerId}</p>
                  </div>
                  <div className="text-right">
                    <p className="text-sm font-bold">${p.amount}</p>
                    <StatusBadge status={p.status} />
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Properties — full width: the most important business content deserves the room */}
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm lg:col-span-2">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-bold text-gray-900 flex items-center gap-2">
              <Home className="w-4 h-4 text-forest-600" /> My Properties
            </h2>
            <div className="flex items-center gap-3">
              <Link href="/landlord/featured-listings" className="text-xs text-gold-600 hover:underline font-medium">Feature a listing</Link>
              <Link href="/properties" className="text-xs text-forest-600 hover:underline">Browse all</Link>
            </div>
          </div>
          {ownProperties.length === 0 ? (
            <EmptyState icon={Home} title="No properties yet" action={{ label: "+ Add your first property", href: "/properties/new" }} />
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              {ownProperties.slice(0, 6).map((prop) => (
                <div key={prop.id} className="flex items-center justify-between p-3 bg-gray-50 rounded-xl">
                  <div>
                    <Link href={`/properties/${prop.id}`} className="text-sm font-medium text-gray-900 hover:text-forest-600 transition-colors">
                      {prop.title}
                    </Link>
                    <p className="text-xs text-gray-500">{prop.suburb}, {prop.city}</p>
                    <p className="text-xs font-semibold text-forest-600 mt-0.5">${prop.price}/mo</p>
                  </div>
                  <div className="text-right flex flex-col gap-1 items-end">
                    {prop.featured && <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-gold-100 text-gold-700">★ Featured</span>}
                    <StatusBadge status={prop.status} />
                    <StatusBadge status={prop.verificationStatus} />
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Inquiries */}
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm lg:col-span-2">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-bold text-gray-900 flex items-center gap-2">
              <MessageCircle className="w-4 h-4 text-forest-600" /> Recent Inquiries
            </h2>
            <Link href="/messages" className="text-xs text-forest-600 hover:underline">Open messages</Link>
          </div>
          {inquiries.length === 0 ? (
            <EmptyState icon={MessageCircle} title="No inquiries yet" compact />
          ) : (
            <div className="space-y-3">
              {inquiries.slice(0, 6).map((inq) => (
                <div key={inq.id} className="p-3 bg-gray-50 rounded-xl">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="text-sm font-medium text-gray-900">{inq.name}</p>
                      <Link href={`/properties/${inq.propertyId}`} className="text-xs text-forest-600 hover:underline">
                        {inq.propertyTitle}
                      </Link>
                    </div>
                    <p className="text-xs text-gray-400 shrink-0">{new Date(inq.createdAt).toLocaleDateString()}</p>
                  </div>
                  <p className="text-sm text-gray-600 mt-1.5">{inq.message}</p>
                  <div className="flex flex-wrap gap-3 mt-2 text-xs text-gray-500">
                    <a href={`mailto:${inq.email}`} className="flex items-center gap-1 hover:text-forest-600">
                      <Mail className="w-3 h-3" /> {inq.email}
                    </a>
                    {inq.phone && (
                      <a href={`tel:${inq.phone}`} className="flex items-center gap-1 hover:text-forest-600">
                        <Phone className="w-3 h-3" /> {inq.phone}
                      </a>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
