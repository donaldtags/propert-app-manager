"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/lib/auth";
import { dashboards } from "@/lib/api";
import type { LandlordDashboard } from "@/lib/types";
import { Home, DollarSign, Wrench, FileText, Plus, AlertCircle } from "lucide-react";

function StatusBadge({ status }: { status: string }) {
  const colors: Record<string, string> = {
    AVAILABLE: "bg-green-100 text-green-700",
    RENTED: "bg-blue-100 text-blue-700",
    DRAFT: "bg-gray-100 text-gray-700",
    SOLD: "bg-purple-100 text-purple-700",
    ACTIVE: "bg-green-100 text-green-700",
    SIGNED: "bg-blue-100 text-blue-700",
    OPEN: "bg-red-100 text-red-700",
    RESOLVED: "bg-green-100 text-green-700",
    IN_PROGRESS: "bg-amber-100 text-amber-700",
    SUCCESS: "bg-green-100 text-green-700",
    PENDING: "bg-amber-100 text-amber-700",
    VERIFIED: "bg-green-100 text-green-700",
    UNVERIFIED: "bg-gray-100 text-gray-700",
  };
  return (
    <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${colors[status] ?? "bg-gray-100 text-gray-700"}`}>
      {status.replace("_", " ")}
    </span>
  );
}

export default function LandlordDashboardPage() {
  const { user, token, loading } = useAuth();
  const router = useRouter();
  const [dashboard, setDashboard] = useState<LandlordDashboard | null>(null);
  const [dashLoading, setDashLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!loading && (!user || !user.roles?.includes("LANDLORD"))) {
      router.push(user ? "/profile" : "/login?redirect=/landlord");
    }
  }, [user, loading, router]);

  useEffect(() => {
    if (user && token && user.roles?.includes("LANDLORD")) {
      dashboards.landlord(user.id, token)
        .then(setDashboard)
        .catch(() => setError("Failed to load dashboard."))
        .finally(() => setDashLoading(false));
    }
  }, [user, token]);

  if (loading || dashLoading) {
    return (
      <div className="max-w-5xl mx-auto px-4 py-8 grid grid-cols-2 sm:grid-cols-4 gap-4">
        {Array.from({ length: 8 }).map((_, i) => (
          <div key={i} className="h-28 bg-gray-100 rounded-2xl animate-pulse" />
        ))}
      </div>
    );
  }

  const ownProperties = dashboard?.properties ?? [];
  const leases = dashboard?.activeLeases ?? [];
  const payments = dashboard?.recentPayments ?? [];
  const maintenance = dashboard?.maintenanceRequests ?? [];
  const totalIncome = dashboard?.totalRentIncome ?? 0;

  return (
    <div className="max-w-5xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Landlord Dashboard</h1>
          <p className="text-gray-500 mt-1">Manage your properties and tenants</p>
        </div>
        <Link
          href="/properties/new"
          className="flex items-center gap-2 bg-blue-600 hover:bg-blue-700 text-white px-5 py-2.5 rounded-xl font-semibold text-sm transition-colors"
        >
          <Plus className="w-4 h-4" /> Add Property
        </Link>
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-6 flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0" /> {error}
        </div>
      )}

      {/* Stats */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-8">
        {[
          { icon: Home, label: "Properties", value: ownProperties.length, color: "text-blue-600" },
          { icon: FileText, label: "Active Leases", value: leases.length, color: "text-green-600" },
          { icon: DollarSign, label: "Total Income", value: `$${totalIncome.toLocaleString()}`, color: "text-indigo-600" },
          { icon: Wrench, label: "Open Issues", value: maintenance.filter(m => m.status === "OPEN").length, color: "text-amber-600" },
        ].map(({ icon: Icon, label, value, color }) => (
          <div key={label} className="bg-white border border-gray-200 rounded-2xl p-5 shadow-sm">
            <Icon className={`w-6 h-6 ${color} mb-2`} />
            <p className="text-2xl font-bold text-gray-900">{value}</p>
            <p className="text-xs text-gray-500 mt-0.5">{label}</p>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Properties */}
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-bold text-gray-900">My Properties</h2>
            <Link href="/properties" className="text-xs text-blue-600 hover:underline">Browse all</Link>
          </div>
          {ownProperties.length === 0 ? (
            <div className="text-center py-8">
              <Home className="w-10 h-10 mx-auto mb-3 text-gray-300" />
              <p className="text-sm text-gray-400">No properties yet</p>
              <Link href="/properties/new" className="mt-3 inline-block text-sm text-blue-600 hover:underline">+ Add your first property</Link>
            </div>
          ) : (
            <div className="space-y-3">
              {ownProperties.slice(0, 5).map((prop) => (
                <div key={prop.id} className="flex items-center justify-between p-3 bg-gray-50 rounded-xl">
                  <div>
                    <Link href={`/properties/${prop.id}`} className="text-sm font-medium text-gray-900 hover:text-blue-600 transition-colors">
                      {prop.title}
                    </Link>
                    <p className="text-xs text-gray-500">{prop.suburb}, {prop.city}</p>
                    <p className="text-xs font-semibold text-green-600 mt-0.5">${prop.price}/mo</p>
                  </div>
                  <div className="text-right flex flex-col gap-1 items-end">
                    <StatusBadge status={prop.status} />
                    <StatusBadge status={prop.verificationStatus} />
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Active leases */}
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-bold text-gray-900">Active Leases</h2>
            <Link href="/leases" className="text-xs text-blue-600 hover:underline">View all</Link>
          </div>
          {leases.length === 0 ? (
            <p className="text-sm text-gray-400 text-center py-6">No active leases</p>
          ) : (
            <div className="space-y-3">
              {leases.map((lease) => (
                <div key={lease.id} className="flex items-center justify-between p-3 bg-gray-50 rounded-xl">
                  <div>
                    <p className="text-sm font-medium text-gray-900">Lease #{lease.id}</p>
                    <p className="text-xs text-gray-500">Tenant #{lease.tenantId}</p>
                    <p className="text-xs font-semibold text-blue-600">${lease.monthlyRent}/mo</p>
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
            <h2 className="font-bold text-gray-900">Recent Payments</h2>
            <Link href="/payments" className="text-xs text-blue-600 hover:underline">View all</Link>
          </div>
          {payments.length === 0 ? (
            <p className="text-sm text-gray-400 text-center py-6">No payments yet</p>
          ) : (
            <div className="space-y-3">
              {payments.slice(0, 5).map((p) => (
                <div key={p.id} className="flex items-center justify-between p-3 bg-gray-50 rounded-xl">
                  <div>
                    <p className="text-sm font-medium text-gray-900">{p.description || "Rent Payment"}</p>
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

        {/* Maintenance */}
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-bold text-gray-900">Maintenance Requests</h2>
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
                    <p className="text-xs text-gray-500">{req.description?.slice(0, 50)}</p>
                  </div>
                  <StatusBadge status={req.status} />
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
