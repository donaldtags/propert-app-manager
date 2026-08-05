"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth";
import {
  users as usersApi,
  properties as propertiesApi,
  escrow as escrowApi,
  kyc as kycApi,
  dashboards as dashboardsApi,
  messages as messagesApi,
  admin as adminApi,
  neighbourhoods as neighbourhoodsApi,
  vendors as vendorsApi,
  featuredListings as featuredListingsApi,
  subscriptions as subscriptionsApi,
} from "@/lib/api";
import type {
  User,
  AdminRequest,
  Property,
  Escrow,
  KycSubmission,
  KycDocument,
  AdminDashboardOverview,
  AdminConversation,
  FraudSignal,
  NeighbourhoodProfile,
  Vendor,
  VendorCategory,
  FeaturedListingSettings,
  PlanSettings,
  SubscriptionPlan,
} from "@/lib/types";
import {
  ShieldCheck,
  Users,
  Building2,
  ScrollText,
  AlertCircle,
  CheckCircle,
  Search,
  BadgeCheck,
  UserCog,
  Lock,
  LayoutDashboard,
  IdCard,
  Download,
  MessageSquare,
  ShieldAlert,
  MapPin,
  Wrench,
  Star,
  CreditCard,
} from "lucide-react";
import AdminSidebar from "@/components/AdminSidebar";
import AdminMessagesPanel from "@/components/AdminMessagesPanel";
import HorizontalBarChart from "@/components/HorizontalBarChart";

type Tab = "overview" | "users" | "requests" | "kyc" | "properties" | "escrows" | "messages" | "fraud" | "neighbourhoods" | "vendors" | "pricing" | "subscriptions";

const VENDOR_CATEGORIES: VendorCategory[] = [
  "MOVING", "CLEANING", "PLUMBING", "ELECTRICAL", "INSURANCE", "LEGAL", "SOLAR", "UTILITIES", "FURNITURE", "OTHER",
];

function RoleBadge({ role }: { role: string }) {
  return <span className="text-[11px] font-medium px-2 py-0.5 rounded-full bg-gray-100 text-gray-600">{role}</span>;
}

export default function AdminPortalPage() {
  const { user, token, loading } = useAuth();
  const router = useRouter();

  const [tab, setTab] = useState<Tab>("overview");
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const [allUsers, setAllUsers] = useState<User[]>([]);
  const [usersLoading, setUsersLoading] = useState(true);
  const [userSearch, setUserSearch] = useState("");
  const [roleFilter, setRoleFilter] = useState<string>("ALL");

  const [adminRequests, setAdminRequests] = useState<AdminRequest[]>([]);
  const [requestsLoading, setRequestsLoading] = useState(true);

  const [allProperties, setAllProperties] = useState<Property[]>([]);
  const [propertiesLoading, setPropertiesLoading] = useState(true);

  const [allEscrows, setAllEscrows] = useState<Escrow[]>([]);
  const [escrowsLoading, setEscrowsLoading] = useState(true);

  const [overview, setOverview] = useState<AdminDashboardOverview | null>(null);
  const [overviewLoading, setOverviewLoading] = useState(true);

  const [fraudSignals, setFraudSignals] = useState<FraudSignal[]>([]);
  const [fraudLoading, setFraudLoading] = useState(true);

  const [nCity, setNCity] = useState("Harare");
  const [nSuburb, setNSuburb] = useState("");
  const [nSchools, setNSchools] = useState("");
  const [nHospitals, setNHospitals] = useState("");
  const [nTransport, setNTransport] = useState("");
  const [nShopping, setNShopping] = useState("");
  const [nGeneral, setNGeneral] = useState("");
  const [nLoading, setNLoading] = useState(false);
  const [nSaving, setNSaving] = useState(false);
  const [nError, setNError] = useState("");
  const [nSuccess, setNSuccess] = useState("");
  const [nLoaded, setNLoaded] = useState<NeighbourhoodProfile | null>(null);

  const [planList, setPlanList] = useState<PlanSettings[]>([]);
  const [planDrafts, setPlanDrafts] = useState<Record<string, PlanSettings>>({});
  const [planSaving, setPlanSaving] = useState<SubscriptionPlan | null>(null);
  const [planError, setPlanError] = useState("");
  const [planSuccess, setPlanSuccess] = useState("");

  const updatePlanDraft = (plan: SubscriptionPlan, patch: Partial<PlanSettings>) => {
    setPlanDrafts((prev) => ({ ...prev, [plan]: { ...prev[plan], ...patch } }));
  };

  const handleSavePlan = async (plan: SubscriptionPlan) => {
    const draft = planDrafts[plan];
    if (!token || !draft) return;
    setPlanError(""); setPlanSuccess(""); setPlanSaving(plan);
    try {
      const saved = await subscriptionsApi.updatePlan(plan, {
        monthlyPrice: draft.monthlyPrice,
        currency: draft.currency,
        maxProperties: draft.maxProperties,
        escrowEnabled: draft.escrowEnabled,
        digitalLeasesEnabled: draft.digitalLeasesEnabled,
        maintenanceCoordinationEnabled: draft.maintenanceCoordinationEnabled,
        rentRemindersEnabled: draft.rentRemindersEnabled,
        aiPricingEnabled: draft.aiPricingEnabled,
        tenantPassportEnabled: draft.tenantPassportEnabled,
        reportsEnabled: draft.reportsEnabled,
      }, token);
      setPlanList((prev) => prev.map((p) => (p.plan === plan ? saved : p)));
      setPlanDrafts((prev) => ({ ...prev, [plan]: saved }));
      setPlanSuccess(`${plan} plan updated.`);
    } catch (err: unknown) {
      setPlanError(err instanceof Error ? err.message : "Failed to update plan.");
    } finally {
      setPlanSaving(null);
    }
  };

  const [featuredSettings, setFeaturedSettings] = useState<FeaturedListingSettings | null>(null);
  const [fpPrice, setFpPrice] = useState("15.00");
  const [fpCurrency, setFpCurrency] = useState("USD");
  const [fpDurationDays, setFpDurationDays] = useState("14");
  const [fpSaving, setFpSaving] = useState(false);
  const [fpError, setFpError] = useState("");
  const [fpSuccess, setFpSuccess] = useState("");

  const [vendorList, setVendorList] = useState<Vendor[]>([]);
  const [vendorsLoading, setVendorsLoading] = useState(true);
  const [vName, setVName] = useState("");
  const [vCategory, setVCategory] = useState<VendorCategory>("PLUMBING");
  const [vDescription, setVDescription] = useState("");
  const [vPhone, setVPhone] = useState("");
  const [vEmail, setVEmail] = useState("");
  const [vCity, setVCity] = useState("Harare");
  const [vSaving, setVSaving] = useState(false);
  const [vError, setVError] = useState("");
  const [vSuccess, setVSuccess] = useState("");

  const loadVendors = () => {
    setVendorsLoading(true);
    vendorsApi.list().then(setVendorList).catch(() => {}).finally(() => setVendorsLoading(false));
  };

  const handleCreateVendor = async () => {
    if (!token || !vName) return;
    setVError(""); setVSuccess(""); setVSaving(true);
    try {
      await vendorsApi.create({ businessName: vName, category: vCategory, description: vDescription || undefined, phone: vPhone || undefined, email: vEmail || undefined, city: vCity || undefined }, token);
      setVName(""); setVDescription(""); setVPhone(""); setVEmail("");
      setVSuccess("Vendor added.");
      loadVendors();
    } catch (err: unknown) {
      setVError(err instanceof Error ? err.message : "Failed to add vendor.");
    } finally {
      setVSaving(false);
    }
  };

  const handleVerifyVendor = async (id: number) => {
    if (!token) return;
    await vendorsApi.verify(id, token);
    loadVendors();
  };

  const handleDeactivateVendor = async (id: number) => {
    if (!token) return;
    await vendorsApi.deactivate(id, token);
    loadVendors();
  };

  const handleLookupNeighbourhood = async () => {
    if (!nCity || !nSuburb) return;
    setNError(""); setNSuccess(""); setNLoading(true);
    try {
      const profile = await neighbourhoodsApi.get(nCity, nSuburb);
      setNLoaded(profile);
      setNSchools(profile?.schoolsNote ?? "");
      setNHospitals(profile?.hospitalsNote ?? "");
      setNTransport(profile?.transportNote ?? "");
      setNShopping(profile?.shoppingNote ?? "");
      setNGeneral(profile?.generalNote ?? "");
      if (!profile) setNSuccess("No profile yet for this suburb — fill in the fields below to create one.");
    } catch (err: unknown) {
      setNError(err instanceof Error ? err.message : "Failed to look up neighbourhood.");
    } finally {
      setNLoading(false);
    }
  };

  const handleSaveNeighbourhood = async () => {
    if (!token || !nCity || !nSuburb) return;
    setNError(""); setNSuccess(""); setNSaving(true);
    try {
      const saved = await neighbourhoodsApi.upsert(
        { city: nCity, suburb: nSuburb, schoolsNote: nSchools, hospitalsNote: nHospitals, transportNote: nTransport, shoppingNote: nShopping, generalNote: nGeneral },
        token
      );
      setNLoaded(saved);
      setNSuccess("Neighbourhood profile saved.");
    } catch (err: unknown) {
      setNError(err instanceof Error ? err.message : "Failed to save neighbourhood.");
    } finally {
      setNSaving(false);
    }
  };

  const handleSaveFeaturedPricing = async () => {
    if (!token) return;
    setFpError(""); setFpSuccess(""); setFpSaving(true);
    try {
      const saved = await featuredListingsApi.updateSettings(
        { price: Number(fpPrice), currency: fpCurrency, durationDays: Number(fpDurationDays) },
        token
      );
      setFeaturedSettings(saved);
      setFpSuccess("Featured listing price updated.");
    } catch (err: unknown) {
      setFpError(err instanceof Error ? err.message : "Failed to update pricing.");
    } finally {
      setFpSaving(false);
    }
  };

  const [kycSubmissions, setKycSubmissions] = useState<KycSubmission[]>([]);
  const [kycLoading, setKycLoading] = useState(true);
  const [rejectingId, setRejectingId] = useState<number | null>(null);
  const [rejectReason, setRejectReason] = useState("");
  const [downloadingDoc, setDownloadingDoc] = useState<number | null>(null);

  const [adminConversations, setAdminConversations] = useState<AdminConversation[]>([]);
  const [adminConversationsLoading, setAdminConversationsLoading] = useState(true);

  const isAdmin = user?.roles?.includes("ADMIN");

  useEffect(() => {
    if (!loading && (!user || !isAdmin)) router.push("/");
  }, [user, loading, isAdmin, router]);

  const loadAll = () => {
    if (!token) return;
    setUsersLoading(true);
    usersApi.list(undefined, token).then(setAllUsers).catch(() => {}).finally(() => setUsersLoading(false));

    setRequestsLoading(true);
    usersApi.listAdminRequests(token).then(setAdminRequests).catch(() => {}).finally(() => setRequestsLoading(false));

    setPropertiesLoading(true);
    propertiesApi.list().then(setAllProperties).catch(() => {}).finally(() => setPropertiesLoading(false));

    setEscrowsLoading(true);
    escrowApi.listAllForAdmin(token).then(setAllEscrows).catch(() => {}).finally(() => setEscrowsLoading(false));

    setOverviewLoading(true);
    dashboardsApi.adminOverview(token).then(setOverview).catch(() => {}).finally(() => setOverviewLoading(false));

    setFraudLoading(true);
    adminApi.fraudSignals(token).then(setFraudSignals).catch(() => {}).finally(() => setFraudLoading(false));

    loadVendors();

    featuredListingsApi.settings().then((s) => {
      setFeaturedSettings(s);
      setFpPrice(s.price.toFixed(2));
      setFpCurrency(s.currency);
      setFpDurationDays(String(s.durationDays));
    }).catch(() => {});

    subscriptionsApi.plans().then((list) => {
      setPlanList(list);
      setPlanDrafts(Object.fromEntries(list.map((p) => [p.plan, p])));
    }).catch(() => {});

    setKycLoading(true);
    kycApi.listForAdmin(token).then(setKycSubmissions).catch(() => {}).finally(() => setKycLoading(false));

    setAdminConversationsLoading(true);
    messagesApi.adminListConversations(token).then(setAdminConversations).catch(() => {}).finally(() => setAdminConversationsLoading(false));
  };

  useEffect(() => {
    if (isAdmin && token) loadAll();
  }, [isAdmin, token]);

  const filteredUsers = useMemo(() => {
    const needle = userSearch.trim().toLowerCase();
    return allUsers.filter((u) => {
      if (roleFilter !== "ALL" && !u.roles?.includes(roleFilter as never)) return false;
      if (!needle) return true;
      return u.fullName.toLowerCase().includes(needle) || u.email.toLowerCase().includes(needle);
    });
  }, [allUsers, userSearch, roleFilter]);

  const pendingRequests = adminRequests.filter((r) => r.status === "PENDING");
  const unverifiedProperties = allProperties.filter((p) => p.verificationStatus === "UNVERIFIED");
  const disputedEscrows = allEscrows.filter((e) => e.status === "DISPUTED");
  const pendingKyc = kycSubmissions.filter((s) => s.status === "PENDING");

  const recentUsers = useMemo(() => [...allUsers].sort((a, b) => b.id - a.id).slice(0, 5), [allUsers]);
  const recentProperties = useMemo(
    () => [...allProperties].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()).slice(0, 5),
    [allProperties]
  );
  const recentEscrows = useMemo(
    () => [...allEscrows].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()).slice(0, 5),
    [allEscrows]
  );

  const platformTotals = useMemo(() => {
    if (!overview) return [];
    return [
      { label: "Properties", value: overview.properties.total },
      { label: "Leases", value: overview.leases.total },
      { label: "Escrows", value: allEscrows.length },
      { label: "Users", value: allUsers.length },
      { label: "Maintenance Requests", value: overview.maintenance.total },
      { label: "KYC Submissions", value: kycSubmissions.length },
    ];
  }, [overview, allEscrows.length, allUsers.length, kycSubmissions.length]);

  const ESCROW_STATUS_COLORS: Record<string, string> = {
    CREATED: "bg-gray-400",
    FUNDED: "bg-blue-600",
    RELEASED: "bg-green-600",
    DISPUTED: "bg-red-600",
    REFUNDED: "bg-amber-500",
    CANCELLED: "bg-gray-300",
  };

  const escrowStatusBreakdown = useMemo(() => {
    return Object.keys(ESCROW_STATUS_COLORS)
      .map((status) => ({
        label: status,
        value: allEscrows.filter((e) => e.status === status).length,
        color: ESCROW_STATUS_COLORS[status],
      }))
      .filter((d) => d.value > 0);
  }, [allEscrows]);

  const handleVerifyUser = async (id: number) => {
    if (!token) return;
    setError(""); setSuccess("");
    try {
      await usersApi.verify(id, token);
      setSuccess("User verified.");
      loadAll();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to verify user.");
    }
  };

  const handleDecideRequest = async (id: number, approve: boolean) => {
    if (!token) return;
    setError(""); setSuccess("");
    try {
      await usersApi.decideAdminRequest(id, approve, token);
      setSuccess(approve ? "Admin access granted." : "Request rejected.");
      loadAll();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to decide request.");
    }
  };

  const handleVerifyProperty = async (id: number) => {
    if (!token || !user) return;
    setError(""); setSuccess("");
    try {
      await propertiesApi.verify(id, { verifierId: user.id }, token);
      setSuccess("Property verified.");
      loadAll();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to verify property.");
    }
  };

  const handleReleaseEscrow = async (id: number) => {
    if (!token) return;
    setError(""); setSuccess("");
    try {
      const updated = await escrowApi.release(id, token);
      setSuccess(
        updated.status === "RELEASED"
          ? "Escrow released — second approval received."
          : `Approval recorded (${updated.releaseApprovals}/${updated.releaseApprovalsRequired}). Waiting on another admin.`
      );
      loadAll();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to release escrow.");
    }
  };

  const handleReviewKyc = async (id: number, status: "APPROVED" | "REJECTED", reviewNote?: string) => {
    if (!token) return;
    setError(""); setSuccess("");
    try {
      await kycApi.review(id, { status, reviewNote }, token);
      setSuccess(status === "APPROVED" ? "Identity verification approved." : "Identity verification rejected.");
      setRejectingId(null);
      setRejectReason("");
      loadAll();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to review verification request.");
    }
  };

  const handleDownloadKycDoc = async (submissionId: number, doc: KycDocument) => {
    if (!token) return;
    setDownloadingDoc(doc.id);
    try {
      const blob = await kycApi.downloadDocument(submissionId, doc.id, token);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = doc.fileName;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to download document.");
    } finally {
      setDownloadingDoc(null);
    }
  };

  if (loading || !isAdmin) return null;

  const TABS: { key: Tab; label: string; icon: typeof Users; count?: number }[] = [
    { key: "overview", label: "Overview", icon: LayoutDashboard },
    { key: "users", label: "Users", icon: Users, count: allUsers.length },
    { key: "requests", label: "Admin Requests", icon: UserCog, count: pendingRequests.length },
    { key: "kyc", label: "Verification Queue", icon: IdCard, count: pendingKyc.length },
    { key: "properties", label: "Property Verification", icon: Building2, count: unverifiedProperties.length },
    { key: "escrows", label: "Escrows", icon: ScrollText, count: disputedEscrows.length },
    { key: "messages", label: "Messages", icon: MessageSquare, count: adminConversations.length },
    { key: "fraud", label: "Fraud Signals", icon: ShieldAlert, count: fraudSignals.length },
    { key: "neighbourhoods", label: "Neighbourhoods", icon: MapPin },
    { key: "vendors", label: "Service Vendors", icon: Wrench, count: vendorList.length },
    { key: "pricing", label: "Featured Listing Pricing", icon: Star },
    { key: "subscriptions", label: "Subscription Plans", icon: CreditCard },
  ];

  return (
    <div className="flex flex-col md:flex-row h-[calc(100vh-80px)]">
      <AdminSidebar items={TABS} activeKey={tab} onSelect={setTab} />
      <div className="flex-1 overflow-y-auto">
        <div className="max-w-6xl mx-auto px-4 py-8">
          <div className="mb-6">
            <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
              <ShieldCheck className="w-7 h-7 text-blue-600" /> Admin Portal
            </h1>
            <p className="text-gray-500 mt-1">Manage users, roles, property verification, and escrow oversight</p>
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

      {tab === "overview" && (
        <div>
          {overviewLoading ? (
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
              {Array.from({ length: 8 }).map((_, i) => <div key={i} className="h-20 bg-gray-100 rounded-xl animate-pulse" />)}
            </div>
          ) : !overview ? (
            <div className="text-center py-16 text-gray-500">
              <LayoutDashboard className="w-10 h-10 mx-auto mb-3 opacity-30" />
              <p className="font-medium">Command center data is unavailable right now</p>
            </div>
          ) : (
            <>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-6">
                <div className="bg-white border border-gray-200 rounded-xl p-4">
                  <p className="text-xs text-gray-500">Today&apos;s Revenue ({overview.revenue.currency})</p>
                  <p className="text-lg font-bold text-gray-900">{overview.revenue.todayRevenue.toLocaleString()}</p>
                </div>
                <div className="bg-white border border-gray-200 rounded-xl p-4">
                  <p className="text-xs text-gray-500">Monthly Revenue ({overview.revenue.currency})</p>
                  <p className="text-lg font-bold text-gray-900">{overview.revenue.monthRevenue.toLocaleString()}</p>
                </div>
                <div className="bg-white border border-gray-200 rounded-xl p-4">
                  <p className="text-xs text-gray-500">Escrow Balance ({overview.escrow.currency})</p>
                  <p className="text-lg font-bold text-gray-900">{overview.escrow.totalBalance.toLocaleString()}</p>
                </div>
                <div className="bg-white border border-gray-200 rounded-xl p-4">
                  <p className="text-xs text-gray-500">Active Escrows</p>
                  <p className="text-lg font-bold text-gray-900">{overview.escrow.activeCount}</p>
                </div>
                <div className="bg-white border border-gray-200 rounded-xl p-4">
                  <p className="text-xs text-gray-500">Properties Listed</p>
                  <p className="text-lg font-bold text-gray-900">{overview.properties.listed}</p>
                </div>
                <div className="bg-white border border-gray-200 rounded-xl p-4">
                  <p className="text-xs text-gray-500">Properties Sold</p>
                  <p className="text-lg font-bold text-gray-900">{overview.properties.sold}</p>
                </div>
                <div className="bg-white border border-gray-200 rounded-xl p-4">
                  <p className="text-xs text-gray-500">Properties Rented</p>
                  <p className="text-lg font-bold text-gray-900">{overview.properties.rented}</p>
                </div>
                <div className="bg-white border border-gray-200 rounded-xl p-4">
                  <p className="text-xs text-gray-500">Active Leases</p>
                  <p className="text-lg font-bold text-gray-900">{overview.leases.active}</p>
                </div>
                <div className="bg-white border border-gray-200 rounded-xl p-4">
                  <p className="text-xs text-gray-500">Occupancy Rate</p>
                  <p className="text-lg font-bold text-gray-900">{overview.leases.occupancyRatePercent.toFixed(1)}%</p>
                </div>
                <div className="bg-white border border-gray-200 rounded-xl p-4">
                  <p className="text-xs text-gray-500">Maintenance Open</p>
                  <p className="text-lg font-bold text-gray-900">{overview.maintenance.open}</p>
                </div>
                <div className="bg-white border border-gray-200 rounded-xl p-4">
                  <p className="text-xs text-gray-500">Pending KYC</p>
                  <p className="text-lg font-bold text-amber-600">{overview.verification.pendingKyc}</p>
                </div>
                <div className="bg-white border border-gray-200 rounded-xl p-4">
                  <p className="text-xs text-gray-500">Pending Admin Requests</p>
                  <p className="text-lg font-bold text-amber-600">{overview.verification.pendingAdminRequests}</p>
                </div>
                <div className="bg-white border border-gray-200 rounded-xl p-4">
                  <p className="text-xs text-gray-500">Disputed Escrows</p>
                  <p className="text-lg font-bold text-red-600">{overview.escrow.disputedCount}</p>
                </div>
              </div>

              <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-6">
                <div className="bg-white border border-gray-200 rounded-2xl shadow-sm p-4">
                  <h3 className="font-bold text-gray-900 text-sm mb-4">Platform Totals</h3>
                  {platformTotals.length === 0 ? (
                    <p className="text-sm text-gray-400 py-4 text-center">No data yet</p>
                  ) : (
                    <HorizontalBarChart data={platformTotals} />
                  )}
                </div>
                <div className="bg-white border border-gray-200 rounded-2xl shadow-sm p-4">
                  <h3 className="font-bold text-gray-900 text-sm mb-4">Escrows by Status</h3>
                  {escrowStatusBreakdown.length === 0 ? (
                    <p className="text-sm text-gray-400 py-4 text-center">No escrow transactions yet</p>
                  ) : (
                    <HorizontalBarChart data={escrowStatusBreakdown} />
                  )}
                </div>
              </div>

              <div className="bg-white border border-gray-200 rounded-2xl shadow-sm overflow-hidden">
                <div className="p-4 border-b border-gray-100 font-bold text-gray-900">Recent Activity</div>
                {overview.recentActivity.length === 0 ? (
                  <div className="text-center py-12 text-gray-400 text-sm">No recent activity yet</div>
                ) : (
                  <ul className="divide-y divide-gray-100">
                    {overview.recentActivity.map((item, i) => (
                      <li key={i} className="px-4 py-3 flex items-center justify-between gap-4 text-sm">
                        <span className="text-gray-700">{item.description}</span>
                        <span className="text-gray-400 text-xs shrink-0">{new Date(item.occurredAt).toLocaleString()}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>

              <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 mt-6">
                <div className="bg-white border border-gray-200 rounded-2xl shadow-sm overflow-hidden">
                  <button
                    onClick={() => setTab("users")}
                    className="w-full flex items-center justify-between px-4 py-3 border-b border-gray-100 font-bold text-gray-900 hover:bg-gray-50"
                  >
                    Recent Users <span className="text-xs font-normal text-blue-600">View all</span>
                  </button>
                  {recentUsers.length === 0 ? (
                    <div className="text-center py-8 text-gray-400 text-sm">No users yet</div>
                  ) : (
                    <ul className="divide-y divide-gray-100">
                      {recentUsers.map((u) => (
                        <li key={u.id} className="px-4 py-3">
                          <p className="text-sm font-medium text-gray-900 truncate">{u.fullName}</p>
                          <p className="text-xs text-gray-400 truncate">{u.email}</p>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>

                <div className="bg-white border border-gray-200 rounded-2xl shadow-sm overflow-hidden">
                  <button
                    onClick={() => setTab("properties")}
                    className="w-full flex items-center justify-between px-4 py-3 border-b border-gray-100 font-bold text-gray-900 hover:bg-gray-50"
                  >
                    Recent Properties <span className="text-xs font-normal text-blue-600">View all</span>
                  </button>
                  {recentProperties.length === 0 ? (
                    <div className="text-center py-8 text-gray-400 text-sm">No properties yet</div>
                  ) : (
                    <ul className="divide-y divide-gray-100">
                      {recentProperties.map((p) => (
                        <li key={p.id} className="px-4 py-3">
                          <p className="text-sm font-medium text-gray-900 truncate">{p.title}</p>
                          <p className="text-xs text-gray-400 truncate">{p.suburb}, {p.city} · {p.currency} {p.price.toLocaleString()}</p>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>

                <div className="bg-white border border-gray-200 rounded-2xl shadow-sm overflow-hidden">
                  <button
                    onClick={() => setTab("escrows")}
                    className="w-full flex items-center justify-between px-4 py-3 border-b border-gray-100 font-bold text-gray-900 hover:bg-gray-50"
                  >
                    Recent Escrows <span className="text-xs font-normal text-blue-600">View all</span>
                  </button>
                  {recentEscrows.length === 0 ? (
                    <div className="text-center py-8 text-gray-400 text-sm">No escrow transactions yet</div>
                  ) : (
                    <ul className="divide-y divide-gray-100">
                      {recentEscrows.map((e) => (
                        <li key={e.id} className="px-4 py-3 flex items-center justify-between gap-2">
                          <span className="text-sm text-gray-900">#{e.id} · {e.currency} {e.amount.toLocaleString()}</span>
                          <span className={`text-[10px] font-semibold px-2 py-0.5 rounded-full shrink-0 ${
                            e.status === "DISPUTED" ? "bg-red-100 text-red-700" :
                            e.status === "FUNDED" ? "bg-blue-100 text-blue-700" :
                            e.status === "RELEASED" ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-600"
                          }`}>{e.status}</span>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              </div>
            </>
          )}
        </div>
      )}

      {tab === "users" && (
        <div className="bg-white border border-gray-200 rounded-2xl shadow-sm overflow-hidden">
          <div className="p-4 border-b border-gray-100 flex flex-wrap gap-3 items-center">
            <div className="relative flex-1 min-w-[200px]">
              <Search className="w-4 h-4 text-gray-400 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                value={userSearch}
                onChange={(e) => setUserSearch(e.target.value)}
                placeholder="Search by name or email…"
                className="w-full pl-9 pr-3 py-2 text-sm border border-gray-200 rounded-xl outline-none focus:border-blue-500"
              />
            </div>
            <select
              value={roleFilter}
              onChange={(e) => setRoleFilter(e.target.value)}
              className="border border-gray-200 rounded-xl px-3 py-2 text-sm bg-white outline-none focus:border-blue-500"
            >
              {["ALL", "TENANT", "LANDLORD", "AGENT", "DIASPORA", "INVESTOR", "ADMIN", "DEVELOPER", "PRIVATE"].map((r) => (
                <option key={r} value={r}>{r}</option>
              ))}
            </select>
          </div>
          {usersLoading ? (
            <div className="p-4 space-y-2">
              {Array.from({ length: 4 }).map((_, i) => <div key={i} className="h-10 bg-gray-100 rounded-lg animate-pulse" />)}
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-xs text-gray-500 uppercase tracking-wide bg-gray-50">
                    <th className="px-4 py-2.5 font-medium">Name</th>
                    <th className="px-4 py-2.5 font-medium">Email</th>
                    <th className="px-4 py-2.5 font-medium">Roles</th>
                    <th className="px-4 py-2.5 font-medium">Trust</th>
                    <th className="px-4 py-2.5 font-medium">Verified</th>
                    <th className="px-4 py-2.5 font-medium text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {filteredUsers.map((u) => (
                    <tr key={u.id}>
                      <td className="px-4 py-2.5 font-medium text-gray-900">{u.fullName}</td>
                      <td className="px-4 py-2.5 text-gray-600">{u.email}</td>
                      <td className="px-4 py-2.5">
                        <div className="flex gap-1 flex-wrap">
                          {u.roles?.map((r) => <RoleBadge key={r} role={r} />)}
                        </div>
                      </td>
                      <td className="px-4 py-2.5 text-gray-600">{u.trustScore ?? "—"}</td>
                      <td className="px-4 py-2.5">
                        {u.identityVerified ? (
                          <span className="inline-flex items-center gap-1 text-xs text-green-700"><BadgeCheck className="w-3.5 h-3.5" /> Verified</span>
                        ) : (
                          <span className="text-xs text-gray-400">Unverified</span>
                        )}
                      </td>
                      <td className="px-4 py-2.5 text-right">
                        {!u.identityVerified && (
                          <button onClick={() => handleVerifyUser(u.id)} className="text-xs font-medium text-blue-600 hover:text-blue-700">
                            Verify
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                  {filteredUsers.length === 0 && (
                    <tr><td colSpan={6} className="px-4 py-8 text-center text-gray-400">No users match your filters.</td></tr>
                  )}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {tab === "requests" && (
        <div className="bg-white border border-gray-200 rounded-2xl shadow-sm overflow-hidden">
          {requestsLoading ? (
            <div className="p-4 space-y-2">
              {Array.from({ length: 2 }).map((_, i) => <div key={i} className="h-10 bg-gray-100 rounded-lg animate-pulse" />)}
            </div>
          ) : adminRequests.length === 0 ? (
            <div className="text-center py-16 text-gray-500">
              <UserCog className="w-10 h-10 mx-auto mb-3 opacity-30" />
              <p className="font-medium">No admin access requests</p>
            </div>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs text-gray-500 uppercase tracking-wide bg-gray-50">
                  <th className="px-4 py-2.5 font-medium">Name</th>
                  <th className="px-4 py-2.5 font-medium">Email</th>
                  <th className="px-4 py-2.5 font-medium">Requested</th>
                  <th className="px-4 py-2.5 font-medium">Status</th>
                  <th className="px-4 py-2.5 font-medium text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {adminRequests.map((r) => (
                  <tr key={r.id}>
                    <td className="px-4 py-2.5 font-medium text-gray-900">{r.userFullName}</td>
                    <td className="px-4 py-2.5 text-gray-600">{r.userEmail}</td>
                    <td className="px-4 py-2.5 text-gray-400">{new Date(r.requestedAt).toLocaleDateString()}</td>
                    <td className="px-4 py-2.5">
                      <span className={`text-xs font-semibold px-2 py-0.5 rounded-full ${
                        r.status === "PENDING" ? "bg-amber-100 text-amber-700" :
                        r.status === "APPROVED" ? "bg-green-100 text-green-700" : "bg-red-100 text-red-700"
                      }`}>{r.status}</span>
                    </td>
                    <td className="px-4 py-2.5 text-right">
                      {r.status === "PENDING" && (
                        <div className="flex gap-3 justify-end">
                          <button onClick={() => handleDecideRequest(r.id, true)} className="text-xs font-medium text-green-600 hover:text-green-700">Approve</button>
                          <button onClick={() => handleDecideRequest(r.id, false)} className="text-xs font-medium text-red-600 hover:text-red-700">Reject</button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {tab === "kyc" && (
        <div className="bg-white border border-gray-200 rounded-2xl shadow-sm overflow-hidden">
          {kycLoading ? (
            <div className="p-4 space-y-2">
              {Array.from({ length: 3 }).map((_, i) => <div key={i} className="h-10 bg-gray-100 rounded-lg animate-pulse" />)}
            </div>
          ) : kycSubmissions.length === 0 ? (
            <div className="text-center py-16 text-gray-500">
              <IdCard className="w-10 h-10 mx-auto mb-3 opacity-30" />
              <p className="font-medium">No identity verification requests</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-xs text-gray-500 uppercase tracking-wide bg-gray-50">
                    <th className="px-4 py-2.5 font-medium">User</th>
                    <th className="px-4 py-2.5 font-medium">Submitted</th>
                    <th className="px-4 py-2.5 font-medium">ID Type</th>
                    <th className="px-4 py-2.5 font-medium">Documents</th>
                    <th className="px-4 py-2.5 font-medium">Status</th>
                    <th className="px-4 py-2.5 font-medium text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {kycSubmissions.map((s) => (
                    <tr key={s.id}>
                      <td className="px-4 py-2.5">
                        <p className="font-medium text-gray-900">{s.userFullName}</p>
                        <p className="text-xs text-gray-400">{s.userEmail}</p>
                      </td>
                      <td className="px-4 py-2.5 text-gray-400">{new Date(s.submittedAt).toLocaleDateString()}</td>
                      <td className="px-4 py-2.5 text-gray-600">{s.idDocumentType}</td>
                      <td className="px-4 py-2.5">
                        <div className="flex flex-wrap gap-2">
                          {s.documents.map((doc) => (
                            <button
                              key={doc.id}
                              onClick={() => handleDownloadKycDoc(s.id, doc)}
                              disabled={downloadingDoc === doc.id}
                              className="flex items-center gap-1 text-xs text-blue-600 hover:text-blue-700 disabled:opacity-50"
                            >
                              <Download className="w-3 h-3" /> {doc.documentType}
                            </button>
                          ))}
                        </div>
                      </td>
                      <td className="px-4 py-2.5">
                        <span className={`text-xs font-semibold px-2 py-0.5 rounded-full ${
                          s.status === "PENDING" ? "bg-amber-100 text-amber-700" :
                          s.status === "APPROVED" ? "bg-green-100 text-green-700" : "bg-red-100 text-red-700"
                        }`}>{s.status}</span>
                      </td>
                      <td className="px-4 py-2.5 text-right">
                        {s.status === "PENDING" && (
                          rejectingId === s.id ? (
                            <div className="flex flex-col items-end gap-2">
                              <input
                                value={rejectReason}
                                onChange={(e) => setRejectReason(e.target.value)}
                                placeholder="Reason for rejection"
                                className="w-48 border border-gray-200 rounded-lg px-2 py-1 text-xs outline-none focus:border-red-400"
                              />
                              <div className="flex gap-3">
                                <button
                                  onClick={() => setRejectingId(null)}
                                  className="text-xs font-medium text-gray-500 hover:text-gray-700"
                                >
                                  Cancel
                                </button>
                                <button
                                  onClick={() => handleReviewKyc(s.id, "REJECTED", rejectReason)}
                                  className="text-xs font-medium text-red-600 hover:text-red-700"
                                >
                                  Confirm Reject
                                </button>
                              </div>
                            </div>
                          ) : (
                            <div className="flex gap-3 justify-end">
                              <button onClick={() => handleReviewKyc(s.id, "APPROVED")} className="text-xs font-medium text-green-600 hover:text-green-700">
                                Approve
                              </button>
                              <button
                                onClick={() => { setRejectingId(s.id); setRejectReason(""); }}
                                className="text-xs font-medium text-red-600 hover:text-red-700"
                              >
                                Reject
                              </button>
                            </div>
                          )
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {tab === "properties" && (
        <div className="bg-white border border-gray-200 rounded-2xl shadow-sm overflow-hidden">
          {propertiesLoading ? (
            <div className="p-4 space-y-2">
              {Array.from({ length: 3 }).map((_, i) => <div key={i} className="h-10 bg-gray-100 rounded-lg animate-pulse" />)}
            </div>
          ) : unverifiedProperties.length === 0 ? (
            <div className="text-center py-16 text-gray-500">
              <Building2 className="w-10 h-10 mx-auto mb-3 opacity-30" />
              <p className="font-medium">No properties awaiting verification</p>
            </div>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs text-gray-500 uppercase tracking-wide bg-gray-50">
                  <th className="px-4 py-2.5 font-medium">Title</th>
                  <th className="px-4 py-2.5 font-medium">Location</th>
                  <th className="px-4 py-2.5 font-medium">Price</th>
                  <th className="px-4 py-2.5 font-medium text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {unverifiedProperties.map((p) => (
                  <tr key={p.id}>
                    <td className="px-4 py-2.5 font-medium text-gray-900">{p.title}</td>
                    <td className="px-4 py-2.5 text-gray-600">{p.suburb}, {p.city}</td>
                    <td className="px-4 py-2.5 text-gray-600">{p.currency} {p.price.toLocaleString()}</td>
                    <td className="px-4 py-2.5 text-right">
                      <button onClick={() => handleVerifyProperty(p.id)} className="text-xs font-medium text-blue-600 hover:text-blue-700">
                        Verify
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {tab === "escrows" && (
        <div className="bg-white border border-gray-200 rounded-2xl shadow-sm overflow-hidden">
          {escrowsLoading ? (
            <div className="p-4 space-y-2">
              {Array.from({ length: 3 }).map((_, i) => <div key={i} className="h-10 bg-gray-100 rounded-lg animate-pulse" />)}
            </div>
          ) : allEscrows.length === 0 ? (
            <div className="text-center py-16 text-gray-500">
              <Lock className="w-10 h-10 mx-auto mb-3 opacity-30" />
              <p className="font-medium">No escrow transactions on the platform yet</p>
            </div>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs text-gray-500 uppercase tracking-wide bg-gray-50">
                  <th className="px-4 py-2.5 font-medium">ID</th>
                  <th className="px-4 py-2.5 font-medium">Amount</th>
                  <th className="px-4 py-2.5 font-medium">Status</th>
                  <th className="px-4 py-2.5 font-medium">Funded via</th>
                  <th className="px-4 py-2.5 font-medium text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {allEscrows.map((e) => (
                  <tr key={e.id} className={e.status === "DISPUTED" ? "bg-red-50/40" : ""}>
                    <td className="px-4 py-2.5 font-medium text-gray-900">#{e.id}</td>
                    <td className="px-4 py-2.5 text-gray-600">{e.currency} {e.amount.toLocaleString()}</td>
                    <td className="px-4 py-2.5">
                      <span className={`text-xs font-semibold px-2 py-0.5 rounded-full ${
                        e.status === "DISPUTED" ? "bg-red-100 text-red-700" :
                        e.status === "FUNDED" ? "bg-blue-100 text-blue-700" :
                        e.status === "RELEASED" ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-600"
                      }`}>{e.status}</span>
                    </td>
                    <td className="px-4 py-2.5 text-gray-500">{e.fundingProvider ?? "—"}</td>
                    <td className="px-4 py-2.5 text-right">
                      {e.status === "FUNDED" && (
                        user && e.releaseApprovedByUserIds.includes(user.id) ? (
                          <span className="text-xs text-amber-600 font-medium">
                            Approved ({e.releaseApprovals}/{e.releaseApprovalsRequired}) — waiting on another admin
                          </span>
                        ) : (
                          <button onClick={() => handleReleaseEscrow(e.id)} className="text-xs font-medium text-green-600 hover:text-green-700">
                            Approve release {e.releaseApprovals > 0 ? `(${e.releaseApprovals}/${e.releaseApprovalsRequired})` : ""}
                          </button>
                        )
                      )}
                      {e.status === "DISPUTED" && (
                        <span className="text-xs text-gray-400">Needs manual resolution</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {tab === "messages" && user && token && (
        <AdminMessagesPanel
          token={token}
          currentUserId={user.id}
          allUsers={allUsers}
          conversations={adminConversations}
          loading={adminConversationsLoading}
          onRefresh={loadAll}
        />
      )}

      {tab === "fraud" && (
        <div>
          <div className="mb-6">
            <h2 className="text-lg font-bold text-gray-900 flex items-center gap-2">
              <ShieldAlert className="w-5 h-5 text-red-600" /> Fraud Signals
            </h2>
            <p className="text-sm text-gray-500 mt-1">
              Heuristic signals from Homestead's own listing data — reused photos, address duplicates,
              and price outliers versus comparable listings. Review each before taking action.
            </p>
          </div>
          {fraudLoading ? (
            <div className="space-y-3">
              {Array.from({ length: 4 }).map((_, i) => (
                <div key={i} className="h-16 bg-gray-100 rounded-xl animate-pulse" />
              ))}
            </div>
          ) : fraudSignals.length === 0 ? (
            <div className="text-center py-16 text-gray-500">
              <ShieldAlert className="w-12 h-12 mx-auto mb-4 opacity-30" />
              <p className="text-lg font-medium">No fraud signals detected</p>
            </div>
          ) : (
            <div className="space-y-3">
              {fraudSignals.map((signal, i) => (
                <div key={i} className="bg-white border border-gray-200 rounded-xl p-4 flex items-start justify-between gap-4">
                  <div>
                    <div className="flex items-center gap-2 mb-1">
                      <span
                        className={`text-xs font-bold px-2 py-0.5 rounded-full ${
                          signal.severity === "HIGH"
                            ? "bg-red-100 text-red-700"
                            : signal.severity === "MEDIUM"
                            ? "bg-amber-100 text-amber-700"
                            : "bg-gray-100 text-gray-600"
                        }`}
                      >
                        {signal.severity}
                      </span>
                      <span className="text-xs font-medium text-gray-400">{signal.type.replace(/_/g, " ")}</span>
                    </div>
                    <p className="text-sm font-semibold text-gray-900">{signal.propertyTitle} (#{signal.propertyId})</p>
                    <p className="text-sm text-gray-600 mt-0.5">{signal.description}</p>
                  </div>
                  <a
                    href={`/properties/${signal.propertyId}`}
                    target="_blank"
                    rel="noreferrer"
                    className="shrink-0 text-sm font-semibold text-blue-600 hover:text-blue-700"
                  >
                    View listing
                  </a>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {tab === "neighbourhoods" && (
        <div className="max-w-2xl">
          <div className="mb-6">
            <h2 className="text-lg font-bold text-gray-900 flex items-center gap-2">
              <MapPin className="w-5 h-5 text-blue-600" /> Neighbourhood Facts
            </h2>
            <p className="text-sm text-gray-500 mt-1">
              Curate real, verified facts about a suburb — schools, hospitals, transport, shopping.
              Nothing here is auto-generated; only what you enter is shown to users.
            </p>
          </div>

          {nError && (
            <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-4 flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0" /> {nError}
            </div>
          )}
          {nSuccess && (
            <div className="bg-green-50 border border-green-200 text-green-700 text-sm px-4 py-3 rounded-xl mb-4 flex items-center gap-2">
              <CheckCircle className="w-4 h-4 shrink-0" /> {nSuccess}
            </div>
          )}

          <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm space-y-4">
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">City</label>
                <input
                  value={nCity}
                  onChange={(e) => setNCity(e.target.value)}
                  className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-blue-500"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">Suburb</label>
                <input
                  value={nSuburb}
                  onChange={(e) => setNSuburb(e.target.value)}
                  placeholder="e.g. Borrowdale"
                  className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-blue-500"
                />
              </div>
            </div>
            <button
              onClick={handleLookupNeighbourhood}
              disabled={nLoading || !nCity || !nSuburb}
              className="text-sm font-semibold text-blue-600 hover:text-blue-700 disabled:opacity-50"
            >
              {nLoading ? "Looking up…" : "Look up existing profile"}
            </button>

            {[
              { label: "Schools", value: nSchools, set: setNSchools },
              { label: "Hospitals & Clinics", value: nHospitals, set: setNHospitals },
              { label: "Transport", value: nTransport, set: setNTransport },
              { label: "Shopping", value: nShopping, set: setNShopping },
              { label: "General Notes", value: nGeneral, set: setNGeneral },
            ].map(({ label, value, set }) => (
              <div key={label}>
                <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">{label}</label>
                <textarea
                  value={value}
                  onChange={(e) => set(e.target.value)}
                  rows={2}
                  className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-blue-500 resize-none"
                />
              </div>
            ))}

            <button
              onClick={handleSaveNeighbourhood}
              disabled={nSaving || !nCity || !nSuburb}
              className="bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white font-semibold px-6 py-2.5 rounded-xl text-sm transition-colors"
            >
              {nSaving ? "Saving…" : nLoaded ? "Update Profile" : "Create Profile"}
            </button>
          </div>
        </div>
      )}

      {tab === "pricing" && (
        <div className="max-w-lg">
          <div className="mb-6">
            <h2 className="text-lg font-bold text-gray-900 flex items-center gap-2">
              <Star className="w-5 h-5 text-amber-500" /> Featured Listing Pricing
            </h2>
            <p className="text-sm text-gray-500 mt-1">
              Set what landlords and agents pay to feature one of their listings, and how many days the boost
              lasts. Charges settle instantly and show up as platform revenue in payment records.
            </p>
          </div>

          {fpError && (
            <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-4 flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0" /> {fpError}
            </div>
          )}
          {fpSuccess && (
            <div className="bg-green-50 border border-green-200 text-green-700 text-sm px-4 py-3 rounded-xl mb-4 flex items-center gap-2">
              <CheckCircle className="w-4 h-4 shrink-0" /> {fpSuccess}
            </div>
          )}

          <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm space-y-4">
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">Price</label>
                <input
                  type="number"
                  min="0.01"
                  step="0.01"
                  value={fpPrice}
                  onChange={(e) => setFpPrice(e.target.value)}
                  className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-blue-500"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">Currency</label>
                <input
                  value={fpCurrency}
                  onChange={(e) => setFpCurrency(e.target.value.toUpperCase())}
                  maxLength={3}
                  className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-blue-500"
                />
              </div>
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">Duration (days)</label>
              <input
                type="number"
                min="1"
                value={fpDurationDays}
                onChange={(e) => setFpDurationDays(e.target.value)}
                className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-blue-500"
              />
            </div>

            {featuredSettings && (
              <p className="text-xs text-gray-400">
                Current: ${featuredSettings.price.toFixed(2)} {featuredSettings.currency} for {featuredSettings.durationDays} days
              </p>
            )}

            <button
              onClick={handleSaveFeaturedPricing}
              disabled={fpSaving || !fpPrice || !fpCurrency || !fpDurationDays}
              className="bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white font-semibold px-6 py-2.5 rounded-xl text-sm transition-colors"
            >
              {fpSaving ? "Saving…" : "Save Pricing"}
            </button>
          </div>
        </div>
      )}

      {tab === "subscriptions" && (
        <div className="max-w-4xl">
          <div className="mb-6">
            <h2 className="text-lg font-bold text-gray-900 flex items-center gap-2">
              <CreditCard className="w-5 h-5 text-blue-600" /> Subscription Plans
            </h2>
            <p className="text-sm text-gray-500 mt-1">
              Set the monthly price, property cap, and feature gates for each landlord/agent plan tier.
              Growth+ features are enforced across escrow, digital leases, maintenance coordination, and AI pricing.
            </p>
          </div>

          {planError && (
            <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-4 flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0" /> {planError}
            </div>
          )}
          {planSuccess && (
            <div className="bg-green-50 border border-green-200 text-green-700 text-sm px-4 py-3 rounded-xl mb-4 flex items-center gap-2">
              <CheckCircle className="w-4 h-4 shrink-0" /> {planSuccess}
            </div>
          )}

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-5">
            {planList.map((plan) => {
              const draft = planDrafts[plan.plan] ?? plan;
              const featureFields: { key: keyof PlanSettings; label: string }[] = [
                { key: "escrowEnabled", label: "Escrow" },
                { key: "digitalLeasesEnabled", label: "Digital leases" },
                { key: "maintenanceCoordinationEnabled", label: "Maintenance coordination" },
                { key: "rentRemindersEnabled", label: "Rent reminders" },
                { key: "aiPricingEnabled", label: "AI pricing" },
                { key: "tenantPassportEnabled", label: "Tenant Passport" },
                { key: "reportsEnabled", label: "Reports" },
              ];
              return (
                <div key={plan.plan} className="bg-white border border-gray-200 rounded-2xl p-5 shadow-sm space-y-3">
                  <p className="text-xs font-bold text-gray-500 uppercase tracking-wide">{plan.plan}</p>
                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <label className="block text-[10px] font-medium text-gray-500 uppercase tracking-wide mb-1">Price</label>
                      <input
                        type="number"
                        min="0"
                        step="0.01"
                        value={draft.monthlyPrice}
                        onChange={(e) => updatePlanDraft(plan.plan, { monthlyPrice: Number(e.target.value) })}
                        className="w-full border border-gray-200 rounded-lg px-2.5 py-2 text-sm outline-none focus:border-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-[10px] font-medium text-gray-500 uppercase tracking-wide mb-1">Currency</label>
                      <input
                        value={draft.currency}
                        onChange={(e) => updatePlanDraft(plan.plan, { currency: e.target.value.toUpperCase() })}
                        maxLength={3}
                        className="w-full border border-gray-200 rounded-lg px-2.5 py-2 text-sm outline-none focus:border-blue-500"
                      />
                    </div>
                  </div>
                  <div>
                    <label className="block text-[10px] font-medium text-gray-500 uppercase tracking-wide mb-1">Max properties (blank = unlimited)</label>
                    <input
                      type="number"
                      min="0"
                      value={draft.maxProperties ?? ""}
                      onChange={(e) => updatePlanDraft(plan.plan, { maxProperties: e.target.value === "" ? null : Number(e.target.value) })}
                      className="w-full border border-gray-200 rounded-lg px-2.5 py-2 text-sm outline-none focus:border-blue-500"
                    />
                  </div>

                  <div className="space-y-1.5 pt-1">
                    {featureFields.map(({ key, label }) => (
                      <label key={String(key)} className="flex items-center gap-2 text-sm text-gray-700">
                        <input
                          type="checkbox"
                          checked={Boolean(draft[key])}
                          onChange={(e) => updatePlanDraft(plan.plan, { [key]: e.target.checked } as Partial<PlanSettings>)}
                          className="rounded border-gray-300"
                        />
                        {label}
                      </label>
                    ))}
                  </div>

                  <button
                    onClick={() => handleSavePlan(plan.plan)}
                    disabled={planSaving === plan.plan}
                    className="w-full bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white font-semibold py-2 rounded-xl text-sm transition-colors"
                  >
                    {planSaving === plan.plan ? "Saving…" : "Save"}
                  </button>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {tab === "vendors" && (
        <div>
          <div className="mb-6">
            <h2 className="text-lg font-bold text-gray-900 flex items-center gap-2">
              <Wrench className="w-5 h-5 text-blue-600" /> Service Vendors
            </h2>
            <p className="text-sm text-gray-500 mt-1">
              Add and vet the movers, cleaners, tradespeople, insurers, and lawyers shown in the Services Marketplace.
            </p>
          </div>

          {vError && (
            <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-4 flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0" /> {vError}
            </div>
          )}
          {vSuccess && (
            <div className="bg-green-50 border border-green-200 text-green-700 text-sm px-4 py-3 rounded-xl mb-4 flex items-center gap-2">
              <CheckCircle className="w-4 h-4 shrink-0" /> {vSuccess}
            </div>
          )}

          <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm mb-6 max-w-2xl">
            <h3 className="font-bold text-gray-900 mb-4">Add Vendor</h3>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <input
                value={vName}
                onChange={(e) => setVName(e.target.value)}
                placeholder="Business name"
                className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-blue-500"
              />
              <select
                value={vCategory}
                onChange={(e) => setVCategory(e.target.value as VendorCategory)}
                className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-blue-500 bg-white"
              >
                {VENDOR_CATEGORIES.map((c) => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </select>
              <input
                value={vPhone}
                onChange={(e) => setVPhone(e.target.value)}
                placeholder="Phone"
                className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-blue-500"
              />
              <input
                value={vEmail}
                onChange={(e) => setVEmail(e.target.value)}
                placeholder="Email"
                className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-blue-500"
              />
              <input
                value={vCity}
                onChange={(e) => setVCity(e.target.value)}
                placeholder="City"
                className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-blue-500"
              />
              <textarea
                value={vDescription}
                onChange={(e) => setVDescription(e.target.value)}
                placeholder="Description"
                rows={2}
                className="sm:col-span-2 w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-blue-500 resize-none"
              />
            </div>
            <button
              onClick={handleCreateVendor}
              disabled={vSaving || !vName}
              className="mt-4 bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white font-semibold px-6 py-2.5 rounded-xl text-sm transition-colors"
            >
              {vSaving ? "Adding…" : "Add Vendor"}
            </button>
          </div>

          {vendorsLoading ? (
            <div className="space-y-3">
              {Array.from({ length: 3 }).map((_, i) => (
                <div key={i} className="h-16 bg-gray-100 rounded-xl animate-pulse" />
              ))}
            </div>
          ) : (
            <div className="space-y-3">
              {vendorList.map((v) => (
                <div key={v.id} className="bg-white border border-gray-200 rounded-xl p-4 flex items-center justify-between">
                  <div>
                    <div className="flex items-center gap-2">
                      <p className="text-sm font-semibold text-gray-900">{v.businessName}</p>
                      {v.verified && <BadgeCheck className="w-4 h-4 text-green-600" />}
                    </div>
                    <p className="text-xs text-gray-500">{v.category} · {v.city}</p>
                  </div>
                  <div className="flex gap-2">
                    {!v.verified && (
                      <button
                        onClick={() => handleVerifyVendor(v.id)}
                        className="text-xs font-semibold bg-green-600 hover:bg-green-700 text-white px-3 py-1.5 rounded-lg"
                      >
                        Verify
                      </button>
                    )}
                    <button
                      onClick={() => handleDeactivateVendor(v.id)}
                      className="text-xs font-semibold border border-gray-200 hover:bg-gray-100 text-gray-600 px-3 py-1.5 rounded-lg"
                    >
                      Deactivate
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
        </div>
      </div>
    </div>
  );
}