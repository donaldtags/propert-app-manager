"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import Image from "next/image";
import { useAuth } from "@/lib/auth";
import { users as usersApi, dashboards, kyc as kycApi, leases as leasesApi } from "@/lib/api";
import type { TenantPassport, VerificationLevel, KycSubmission, Lease, TenantDashboard } from "@/lib/types";
import {
  IdCard,
  Mail,
  Phone,
  ShieldCheck,
  ShieldAlert,
  Star,
  FileText,
  Wrench,
  Home,
  AlertCircle,
  BadgeCheck,
} from "lucide-react";

function VerificationRow({ label, verified }: { label: string; verified: boolean }) {
  return (
    <div className="flex items-center justify-between text-sm py-1.5">
      <span className="text-gray-600">{label}</span>
      {verified ? (
        <span className="flex items-center gap-1 text-green-700 font-medium">
          <ShieldCheck className="w-4 h-4" /> Verified
        </span>
      ) : (
        <span className="flex items-center gap-1 text-gray-400">
          <ShieldAlert className="w-4 h-4" /> Not verified
        </span>
      )}
    </div>
  );
}

function StatTile({ label, value, sub }: { label: string; value: string | number; sub?: string }) {
  return (
    <div className="bg-white border border-gray-200 rounded-2xl p-4 shadow-sm">
      <p className="text-2xl font-bold text-gray-900">{value}</p>
      <p className="text-xs text-gray-500 mt-0.5">{label}</p>
      {sub && <p className="text-[11px] text-gray-400 mt-0.5">{sub}</p>}
    </div>
  );
}

export default function TenantPassportPage() {
  const { user, token, loading } = useAuth();
  const router = useRouter();

  const [passport, setPassport] = useState<TenantPassport | null>(null);
  const [verificationLevel, setVerificationLevel] = useState<VerificationLevel | null>(null);
  const [dashboard, setDashboard] = useState<TenantDashboard | null>(null);
  const [kycSubmissions, setKycSubmissions] = useState<KycSubmission[]>([]);
  const [leaseList, setLeaseList] = useState<Lease[]>([]);
  const [pageLoading, setPageLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!loading && (!user || !user.roles?.includes("TENANT"))) {
      router.push(user ? "/profile" : "/login?redirect=/passport");
    }
  }, [user, loading, router]);

  useEffect(() => {
    if (!user || !token || !user.roles?.includes("TENANT")) return;
    Promise.allSettled([
      usersApi.tenantPassport(user.id, token),
      usersApi.verificationLevel(user.id, token),
      dashboards.tenant(user.id, token),
      kycApi.myList(token),
      leasesApi.listByTenant(user.id, token),
    ])
      .then(([p, v, d, k, l]) => {
        if (p.status === "fulfilled") setPassport(p.value);
        if (v.status === "fulfilled") setVerificationLevel(v.value);
        if (d.status === "fulfilled") setDashboard(d.value);
        if (k.status === "fulfilled") setKycSubmissions(k.value);
        if (l.status === "fulfilled") setLeaseList(l.value);
        if (p.status === "rejected") setError("Failed to load your tenant passport.");
      })
      .finally(() => setPageLoading(false));
  }, [user, token]);

  if (loading || pageLoading) {
    return (
      <div className="max-w-4xl mx-auto px-4 py-8 grid grid-cols-2 sm:grid-cols-4 gap-4">
        {Array.from({ length: 8 }).map((_, i) => (
          <div key={i} className="h-24 bg-gray-100 rounded-2xl animate-pulse" />
        ))}
      </div>
    );
  }

  if (error && !passport) {
    return (
      <div className="text-center py-24 text-gray-500">
        <AlertCircle className="w-12 h-12 mx-auto mb-4 text-red-400" />
        <p className="text-lg font-semibold">{error}</p>
      </div>
    );
  }

  const maintenanceRequests = dashboard?.maintenanceRequests ?? [];
  const resolvedMaintenance = maintenanceRequests.filter((m) => m.status === "RESOLVED").length;

  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm mb-6 flex items-center gap-5">
        <div className="w-20 h-20 rounded-full bg-gray-100 overflow-hidden shrink-0 relative">
          {user?.avatarUrl ? (
            <Image src={user.avatarUrl} alt={user.fullName} fill className="object-cover" unoptimized />
          ) : (
            <div className="w-full h-full flex items-center justify-center text-2xl font-bold text-gray-400">
              {user?.fullName?.[0] ?? "?"}
            </div>
          )}
        </div>
        <div className="flex-1">
          <div className="flex items-center gap-2">
            <h1 className="text-xl font-bold text-gray-900">{user?.fullName}</h1>
            {user?.identityVerified && <BadgeCheck className="w-5 h-5 text-green-600" />}
          </div>
          <p className="text-sm text-gray-500">Tenant Passport</p>
          <p className="text-xs text-gray-400 mt-1">
            {passport?.yearsOnPlatform ?? 0} year{(passport?.yearsOnPlatform ?? 0) === 1 ? "" : "s"} on Homestead
          </p>
        </div>
        <div className="text-right">
          <p className="text-3xl font-bold text-gray-900">
            {passport?.trustScore ?? user?.trustScore ?? "—"}
            <span className="text-sm text-gray-400">/100</span>
          </p>
          <p className="text-xs text-gray-500">Trust Score</p>
        </div>
      </div>

      {error && (
        <div className="bg-amber-50 border border-amber-200 text-amber-700 text-sm px-4 py-3 rounded-xl mb-6">
          Some passport data could not be loaded.
        </div>
      )}

      {/* Verification */}
      <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm mb-6">
        <h2 className="font-bold text-gray-900 mb-3 flex items-center gap-2">
          <IdCard className="w-5 h-5 text-blue-600" /> Identity & Verification
        </h2>
        <div className="divide-y divide-gray-100">
          <VerificationRow label="Email" verified={verificationLevel?.emailVerified ?? false} />
          <VerificationRow label="Phone" verified={verificationLevel?.phoneVerified ?? false} />
          <VerificationRow label="Identity Document" verified={verificationLevel?.identityVerified ?? false} />
          <VerificationRow label="Face Match" verified={verificationLevel?.faceVerified ?? false} />
        </div>
        {!verificationLevel?.identityVerified && (
          <Link href="/verification" className="mt-3 inline-block text-sm text-blue-600 hover:underline">
            Complete identity verification →
          </Link>
        )}
      </div>

      {/* Rental history + payment reliability */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-6">
        <StatTile label="Active Leases" value={passport?.activeLeaseCount ?? 0} />
        <StatTile label="Completed Leases" value={passport?.completedLeaseCount ?? 0} />
        <StatTile
          label="On-Time Payments"
          value={passport ? `${passport.onTimeRentInvoices}/${passport.totalRentInvoices}` : "—"}
          sub={passport?.onTimePaymentRatePercent != null ? `${passport.onTimePaymentRatePercent.toFixed(0)}% on time` : undefined}
        />
        <StatTile
          label="Maintenance Resolved"
          value={`${resolvedMaintenance}/${maintenanceRequests.length}`}
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Lease history */}
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
          <h2 className="font-bold text-gray-900 mb-4 flex items-center gap-2">
            <Home className="w-5 h-5 text-blue-600" /> Lease History
          </h2>
          {leaseList.length === 0 ? (
            <p className="text-sm text-gray-400 text-center py-6">No leases yet</p>
          ) : (
            <div className="space-y-3">
              {leaseList.map((lease) => (
                <div key={lease.id} className="flex items-center justify-between p-3 bg-gray-50 rounded-xl">
                  <div>
                    <Link href={`/properties/${lease.propertyId}`} className="text-sm font-medium text-gray-900 hover:text-blue-600">
                      Property #{lease.propertyId}
                    </Link>
                    <p className="text-xs text-gray-500">{lease.startDate} → {lease.endDate}</p>
                  </div>
                  <span className="text-xs font-semibold px-2 py-0.5 rounded-full bg-white border border-gray-200 text-gray-600">
                    {lease.status}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Reviews given + documents */}
        <div className="space-y-6">
          <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
            <h2 className="font-bold text-gray-900 mb-3 flex items-center gap-2">
              <Star className="w-5 h-5 text-amber-500" /> Landlord Reviews Given
            </h2>
            {passport && passport.ratingsGivenCount > 0 ? (
              <p className="text-sm text-gray-700">
                You've submitted {passport.ratingsGivenCount} review{passport.ratingsGivenCount === 1 ? "" : "s"}
                {passport.averageLandlordRatingGiven != null && ` averaging ${passport.averageLandlordRatingGiven.toFixed(1)}/5`}.
              </p>
            ) : (
              <p className="text-sm text-gray-400">No reviews submitted yet.</p>
            )}
          </div>

          <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
            <h2 className="font-bold text-gray-900 mb-3 flex items-center gap-2">
              <FileText className="w-5 h-5 text-gray-500" /> Verification Documents
            </h2>
            {kycSubmissions.length === 0 ? (
              <p className="text-sm text-gray-400">No verification documents submitted yet.</p>
            ) : (
              <div className="space-y-2">
                {kycSubmissions.map((s) => (
                  <div key={s.id} className="flex items-center justify-between text-sm">
                    <span className="text-gray-600">{new Date(s.submittedAt).toLocaleDateString()}</span>
                    <span
                      className={`text-xs font-semibold px-2 py-0.5 rounded-full ${
                        s.status === "APPROVED"
                          ? "bg-green-100 text-green-700"
                          : s.status === "REJECTED"
                          ? "bg-red-100 text-red-700"
                          : "bg-amber-100 text-amber-700"
                      }`}
                    >
                      {s.status}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
            <h2 className="font-bold text-gray-900 mb-3 flex items-center gap-2">
              <Wrench className="w-5 h-5 text-amber-600" /> Maintenance History
            </h2>
            {maintenanceRequests.length === 0 ? (
              <p className="text-sm text-gray-400">No maintenance requests on file.</p>
            ) : (
              <p className="text-sm text-gray-700">
                {resolvedMaintenance} of {maintenanceRequests.length} request{maintenanceRequests.length === 1 ? "" : "s"} resolved.
              </p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
