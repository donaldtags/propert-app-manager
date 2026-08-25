"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/lib/auth";
import { applications as applicationsApi } from "@/lib/api";
import type { ApplicationStatus, RentalApplication } from "@/lib/types";
import { ClipboardList, AlertCircle, CheckCircle, BadgeCheck, ShieldAlert } from "lucide-react";

const BUSINESS_ROLES = ["LANDLORD", "AGENT", "DEVELOPER", "PRIVATE"] as const;

const STATUS_COLORS: Record<ApplicationStatus, string> = {
  DRAFT: "bg-gray-100 text-gray-700",
  SUBMITTED: "bg-forest-100 text-forest-700",
  VERIFICATION_REQUIRED: "bg-amber-100 text-amber-700",
  UNDER_REVIEW: "bg-indigo-100 text-indigo-700",
  APPROVED: "bg-forest-100 text-forest-700",
  REJECTED: "bg-red-100 text-red-700",
  LEASE_PREPARATION: "bg-purple-100 text-purple-700",
};

const STATUS_LABELS: Record<ApplicationStatus, string> = {
  DRAFT: "Draft",
  SUBMITTED: "Submitted",
  VERIFICATION_REQUIRED: "Verification Required",
  UNDER_REVIEW: "Under Review",
  APPROVED: "Approved",
  REJECTED: "Rejected",
  LEASE_PREPARATION: "Lease Preparation",
};

const STATUS_NEXT_STEP: Record<ApplicationStatus, string> = {
  DRAFT: "Finish and submit your application.",
  SUBMITTED: "Waiting for the landlord to start reviewing.",
  VERIFICATION_REQUIRED: "Verify your identity so the landlord can review your application.",
  UNDER_REVIEW: "The landlord is reviewing your application.",
  APPROVED: "Your application was approved — expect a lease shortly.",
  REJECTED: "This application was not successful.",
  LEASE_PREPARATION: "The landlord is preparing your lease.",
};

function StatusBadge({ status }: { status: ApplicationStatus }) {
  return (
    <span className={`text-xs font-semibold px-2 py-0.5 rounded-full ${STATUS_COLORS[status]}`}>
      {STATUS_LABELS[status]}
    </span>
  );
}

export default function ApplicationsPage() {
  const { user, token, loading } = useAuth();
  const router = useRouter();

  const [mine, setMine] = useState<RentalApplication[]>([]);
  const [received, setReceived] = useState<RentalApplication[]>([]);
  const [dataLoading, setDataLoading] = useState(true);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [actioningId, setActioningId] = useState<number | null>(null);

  const isTenant = user?.roles?.includes("TENANT") || user?.roles?.includes("DIASPORA");
  const isBusiness = !!user?.roles?.some((r) => BUSINESS_ROLES.includes(r as typeof BUSINESS_ROLES[number]));

  useEffect(() => {
    if (!loading && !user) router.push("/login?redirect=/applications");
  }, [user, loading, router]);

  useEffect(() => {
    if (!user || !token) return;
    setDataLoading(true);
    Promise.allSettled([
      isTenant ? applicationsApi.mine(token) : Promise.resolve([] as RentalApplication[]),
      isBusiness ? applicationsApi.received(token) : Promise.resolve([] as RentalApplication[]),
    ])
      .then(([mineResult, receivedResult]) => {
        if (mineResult.status === "fulfilled") setMine(mineResult.value);
        if (receivedResult.status === "fulfilled") setReceived(receivedResult.value);
      })
      .finally(() => setDataLoading(false));
  }, [user, token, isTenant, isBusiness]);

  const handleReview = async (id: number, status: ApplicationStatus) => {
    if (!token) return;
    setError("");
    setSuccess("");
    setActioningId(id);
    try {
      const updated = await applicationsApi.review(id, { status }, token);
      setReceived((prev) => prev.map((a) => (a.id === id ? updated : a)));
      setSuccess(`Application ${STATUS_LABELS[status].toLowerCase()}.`);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to update application.");
    } finally {
      setActioningId(null);
    }
  };

  if (loading) return null;

  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
          <ClipboardList className="w-7 h-7 text-forest-600" /> Applications
        </h1>
        <p className="text-gray-500 mt-1">Track and manage rental applications</p>
      </div>

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

      {dataLoading ? (
        <div className="space-y-4">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="h-28 bg-gray-100 rounded-2xl animate-pulse" />
          ))}
        </div>
      ) : (
        <>
          {isTenant && (
            <div className="mb-10">
              <h2 className="font-bold text-gray-900 mb-4">My Applications</h2>
              {mine.length === 0 ? (
                <div className="text-center py-12 text-gray-500 bg-white border border-gray-200 rounded-2xl">
                  <ClipboardList className="w-10 h-10 mx-auto mb-3 opacity-30" />
                  <p className="text-sm">No applications yet.</p>
                  <Link href="/properties" className="text-sm text-forest-600 hover:underline mt-1 inline-block">
                    Browse properties
                  </Link>
                </div>
              ) : (
                <div className="space-y-4">
                  {mine.map((app) => (
                    <div key={app.id} className="bg-white border border-gray-200 rounded-2xl p-5 shadow-sm">
                      <div className="flex flex-wrap items-start justify-between gap-3">
                        <div>
                          <div className="flex items-center gap-2 mb-1">
                            <Link href={`/properties/${app.propertyId}`} className="font-semibold text-gray-900 hover:text-forest-600">
                              {app.propertyTitle}
                            </Link>
                            <StatusBadge status={app.status} />
                          </div>
                          {app.desiredMoveInDate && (
                            <p className="text-sm text-gray-500">Move-in: {app.desiredMoveInDate}</p>
                          )}
                          <p className="text-xs text-gray-400 mt-1">
                            Applied {new Date(app.createdAt).toLocaleDateString()}
                          </p>
                        </div>
                        {!app.identityVerified && app.status === "VERIFICATION_REQUIRED" && (
                          <Link
                            href="/verification"
                            className="flex items-center gap-1.5 text-xs font-semibold text-amber-700 bg-amber-50 hover:bg-amber-100 px-3 py-1.5 rounded-full transition-colors"
                          >
                            <ShieldAlert className="w-3.5 h-3.5" /> Verify Identity
                          </Link>
                        )}
                      </div>
                      <p className="text-sm text-gray-600 mt-3 pt-3 border-t border-gray-100">
                        {STATUS_NEXT_STEP[app.status]}
                      </p>
                      {app.reviewNote && (
                        <p className="text-sm text-gray-500 mt-2 italic">Landlord note: &ldquo;{app.reviewNote}&rdquo;</p>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {isBusiness && (
            <div>
              <h2 className="font-bold text-gray-900 mb-4">Applications Received</h2>
              {received.length === 0 ? (
                <div className="text-center py-12 text-gray-500 bg-white border border-gray-200 rounded-2xl">
                  <ClipboardList className="w-10 h-10 mx-auto mb-3 opacity-30" />
                  <p className="text-sm">No applications received yet.</p>
                </div>
              ) : (
                <div className="space-y-4">
                  {received.map((app) => (
                    <div key={app.id} className="bg-white border border-gray-200 rounded-2xl p-5 shadow-sm">
                      <div className="flex flex-wrap items-start justify-between gap-3">
                        <div>
                          <div className="flex items-center gap-2 mb-1">
                            <p className="font-semibold text-gray-900">{app.applicantName}</p>
                            <StatusBadge status={app.status} />
                            {app.identityVerified && (
                              <span className="flex items-center gap-1 text-xs text-forest-700">
                                <BadgeCheck className="w-3.5 h-3.5" /> Verified
                              </span>
                            )}
                          </div>
                          <Link href={`/properties/${app.propertyId}`} className="text-sm text-forest-600 hover:underline">
                            {app.propertyTitle}
                          </Link>
                          {app.desiredMoveInDate && (
                            <p className="text-sm text-gray-500 mt-1">Move-in: {app.desiredMoveInDate}</p>
                          )}
                          {app.monthlyIncome != null && (
                            <p className="text-sm text-gray-500">Gross monthly income: ${app.monthlyIncome}</p>
                          )}
                          {app.message && <p className="text-sm text-gray-600 mt-2 italic">&ldquo;{app.message}&rdquo;</p>}
                        </div>
                      </div>
                      {(app.status === "UNDER_REVIEW" || app.status === "VERIFICATION_REQUIRED") && (
                        <div className="flex flex-wrap gap-2 mt-3 pt-3 border-t border-gray-100">
                          <button
                            onClick={() => handleReview(app.id, "APPROVED")}
                            disabled={actioningId === app.id}
                            className="text-xs font-semibold bg-forest-600 hover:bg-forest-700 disabled:opacity-60 text-white px-4 py-2 rounded-lg transition-colors"
                          >
                            Approve
                          </button>
                          <button
                            onClick={() => handleReview(app.id, "LEASE_PREPARATION")}
                            disabled={actioningId === app.id}
                            className="text-xs font-semibold bg-purple-600 hover:bg-purple-700 disabled:opacity-60 text-white px-4 py-2 rounded-lg transition-colors"
                          >
                            Move to Lease Preparation
                          </button>
                          <button
                            onClick={() => handleReview(app.id, "REJECTED")}
                            disabled={actioningId === app.id}
                            className="text-xs font-semibold border border-gray-200 hover:bg-gray-100 disabled:opacity-60 text-gray-600 px-4 py-2 rounded-lg transition-colors"
                          >
                            Reject
                          </button>
                        </div>
                      )}
                      {app.status === "LEASE_PREPARATION" && (
                        <div className="mt-3 pt-3 border-t border-gray-100">
                          <Link href="/leases" className="text-xs font-semibold text-purple-700 hover:underline">
                            Create their lease →
                          </Link>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </>
      )}
    </div>
  );
}
