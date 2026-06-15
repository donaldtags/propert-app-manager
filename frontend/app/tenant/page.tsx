"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/lib/auth";
import { dashboards } from "@/lib/api";
import type { TenantDashboard } from "@/lib/types";
import {
  Home,
  DollarSign,
  Wrench,
  Shield,
  FileText,
  AlertCircle,
  CheckCircle,
  Clock,
} from "lucide-react";

function StatusBadge({ status }: { status: string }) {
  const colors: Record<string, string> = {
    ACTIVE: "bg-green-100 text-green-700",
    SIGNED: "bg-blue-100 text-blue-700",
    DRAFT: "bg-gray-100 text-gray-700",
    PENDING: "bg-amber-100 text-amber-700",
    FUNDED: "bg-blue-100 text-blue-700",
    RELEASED: "bg-green-100 text-green-700",
    SUCCESS: "bg-green-100 text-green-700",
    OPEN: "bg-red-100 text-red-700",
    RESOLVED: "bg-green-100 text-green-700",
    IN_PROGRESS: "bg-blue-100 text-blue-700",
  };
  return (
    <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${colors[status] ?? "bg-gray-100 text-gray-700"}`}>
      {status.replace("_", " ")}
    </span>
  );
}

export default function TenantDashboardPage() {
  const { user, token, loading } = useAuth();
  const router = useRouter();
  const [dashboard, setDashboard] = useState<TenantDashboard | null>(null);
  const [dashLoading, setDashLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!loading && (!user || !user.roles?.includes("TENANT"))) {
      router.push(user ? "/profile" : "/login?redirect=/tenant");
    }
  }, [user, loading, router]);

  useEffect(() => {
    if (user && token && user.roles?.includes("TENANT")) {
      dashboards.tenant(user.id, token)
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

  if (error) {
    return (
      <div className="text-center py-24 text-gray-500">
        <AlertCircle className="w-12 h-12 mx-auto mb-4 text-red-400" />
        <p className="text-lg font-semibold">{error}</p>
        <Link href="/" className="mt-4 inline-block text-blue-600 hover:underline text-sm">← Home</Link>
      </div>
    );
  }

  const activeLeases = dashboard?.activeLeases ?? [];
  const recentPayments = dashboard?.recentPayments ?? [];
  const maintenance = dashboard?.maintenanceRequests ?? [];
  const escrows = dashboard?.escrows ?? [];

  return (
    <div className="max-w-5xl mx-auto px-4 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">Tenant Dashboard</h1>
        <p className="text-gray-500 mt-1">Welcome back, {user?.fullName?.split(" ")[0]}</p>
      </div>

      {/* Stat cards */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-8">
        {[
          { icon: Home, label: "Active Leases", value: activeLeases.length, color: "text-blue-600" },
          { icon: DollarSign, label: "Payments Made", value: recentPayments.filter(p => p.status === "SUCCESS").length, color: "text-green-600" },
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
                <div key={lease.id} className="flex items-center justify-between p-3 bg-gray-50 rounded-xl">
                  <div>
                    <p className="text-sm font-medium text-gray-900">Lease #{lease.id}</p>
                    <p className="text-xs text-gray-500">{lease.startDate} → {lease.endDate}</p>
                    <p className="text-xs font-semibold text-blue-600 mt-0.5">${lease.monthlyRent}/mo</p>
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
                    <p className="text-sm font-medium text-gray-900">{payment.description || "Payment"}</p>
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

        {/* Escrows */}
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-bold text-gray-900 flex items-center gap-2">
              <Shield className="w-5 h-5 text-indigo-600" /> Escrow
            </h2>
            <Link href="/escrow" className="text-xs text-blue-600 hover:underline">View all</Link>
          </div>
          {escrows.length === 0 ? (
            <p className="text-sm text-gray-400 text-center py-6">No escrow transactions</p>
          ) : (
            <div className="space-y-3">
              {escrows.slice(0, 4).map((e) => (
                <div key={e.id} className="flex items-center justify-between p-3 bg-gray-50 rounded-xl">
                  <div>
                    <p className="text-sm font-medium text-gray-900">{e.description || "Escrow"}</p>
                    <p className="text-xs font-semibold text-gray-700">${e.amount} {e.currency}</p>
                  </div>
                  <StatusBadge status={e.status} />
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
          <Link href="/properties" className="bg-blue-600 text-white px-5 py-2.5 rounded-xl text-sm font-semibold hover:bg-blue-700 transition-colors">
            Browse Properties
          </Link>
          <Link href="/maintenance" className="bg-white text-gray-700 border border-gray-200 px-5 py-2.5 rounded-xl text-sm font-semibold hover:bg-gray-50 transition-colors">
            Report Issue
          </Link>
          <Link href="/payments" className="bg-white text-gray-700 border border-gray-200 px-5 py-2.5 rounded-xl text-sm font-semibold hover:bg-gray-50 transition-colors">
            Pay Rent
          </Link>
          <Link href="/messages" className="bg-white text-gray-700 border border-gray-200 px-5 py-2.5 rounded-xl text-sm font-semibold hover:bg-gray-50 transition-colors">
            Messages
          </Link>
        </div>
      </div>
    </div>
  );
}
