"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/lib/auth";
import { leases as leasesApi } from "@/lib/api";
import type { Lease, LeaseActionRequest } from "@/lib/types";
import { getChecklistState, toggleChecklistTask } from "@/lib/movingChecklist";
import {
  Truck,
  Home,
  FileText,
  Sparkles,
  Zap,
  UserCog,
  CheckCircle2,
  Circle,
  AlertCircle,
} from "lucide-react";

interface ChecklistItem {
  id: string;
  icon: typeof Truck;
  title: string;
  description: string;
  actionLabel: string;
  actionHref: string;
}

const CHECKLIST: ChecklistItem[] = [
  {
    id: "find-new-home",
    icon: Home,
    title: "Find your next home",
    description: "Browse verified listings that match your budget and preferred area.",
    actionLabel: "Browse Properties",
    actionHref: "/properties",
  },
  {
    id: "book-movers",
    icon: Truck,
    title: "Book a moving company",
    description: "Compare moving services in the marketplace and book one that fits your move date.",
    actionLabel: "Find Movers",
    actionHref: "/services?category=MOVING",
  },
  {
    id: "setup-utilities",
    icon: Zap,
    title: "Set up utilities at your new home",
    description: "Connect with utility providers so power and water are ready when you arrive.",
    actionLabel: "Find Utility Providers",
    actionHref: "/services?category=UTILITIES",
  },
  {
    id: "transfer-history",
    icon: FileText,
    title: "Carry your rental history with you",
    description: "Your Tenant Passport — trust score, payment history, and references — moves with you automatically.",
    actionLabel: "View Tenant Passport",
    actionHref: "/passport",
  },
  {
    id: "update-address",
    icon: UserCog,
    title: "Update your address",
    description: "Keep your profile current so landlords and services can reach you at your new home.",
    actionLabel: "Update Profile",
    actionHref: "/profile",
  },
];

export default function MovingAssistantPage() {
  const { user, token, loading } = useAuth();
  const router = useRouter();

  const [activeLeases, setActiveLeases] = useState<Lease[]>([]);
  const [leaseActions, setLeaseActions] = useState<LeaseActionRequest[]>([]);
  const [pageLoading, setPageLoading] = useState(true);
  const [checklist, setChecklist] = useState(() => getChecklistState());
  const [requestingLeaseId, setRequestingLeaseId] = useState<number | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!loading && !user) router.push("/login?redirect=/moving");
  }, [user, loading, router]);

  useEffect(() => {
    if (!user || !token) return;
    Promise.allSettled([
      leasesApi.listByTenant(user.id, token),
      leasesApi.myActions(token),
    ])
      .then(([l, a]) => {
        if (l.status === "fulfilled") setActiveLeases(l.value.filter((lease) => lease.status === "ACTIVE" || lease.status === "SIGNED"));
        if (a.status === "fulfilled") setLeaseActions(a.value);
      })
      .finally(() => setPageLoading(false));
  }, [user, token]);

  const handleToggle = (taskId: string) => {
    toggleChecklistTask(taskId);
    setChecklist(getChecklistState());
  };

  const handleRequestMoveOut = async (leaseId: number) => {
    if (!token) return;
    setError("");
    setRequestingLeaseId(leaseId);
    try {
      const created = await leasesApi.requestAction(leaseId, { type: "TERMINATION" }, token);
      setLeaseActions((prev) => [created, ...prev]);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to notify your landlord.");
    } finally {
      setRequestingLeaseId(null);
    }
  };

  const terminationFor = (leaseId: number) => leaseActions.find((a) => a.leaseId === leaseId && a.type === "TERMINATION");

  if (loading || pageLoading) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-8 space-y-4">
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="h-20 bg-gray-100 rounded-2xl animate-pulse" />
        ))}
      </div>
    );
  }

  return (
    <div className="max-w-3xl mx-auto px-4 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
          <Truck className="w-7 h-7 text-blue-600" /> Moving Assistant
        </h1>
        <p className="text-gray-500 mt-1">We don't lose you when you move — here's everything to make it smooth</p>
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-5 flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0" /> {error}
        </div>
      )}

      {/* Notify landlord */}
      {activeLeases.length > 0 && (
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm mb-6">
          <h2 className="font-bold text-gray-900 mb-3">Notify Your Landlord</h2>
          <div className="space-y-3">
            {activeLeases.map((lease) => {
              const termination = terminationFor(lease.id);
              return (
                <div key={lease.id} className="flex items-center justify-between p-3 bg-gray-50 rounded-xl">
                  <div>
                    <p className="text-sm font-medium text-gray-900">Lease #{lease.id}</p>
                    <p className="text-xs text-gray-500">Property #{lease.propertyId}</p>
                  </div>
                  {termination ? (
                    <span className="text-xs font-semibold px-2 py-1 rounded-full bg-amber-100 text-amber-700">
                      Termination {termination.status.toLowerCase()}
                    </span>
                  ) : (
                    <button
                      onClick={() => handleRequestMoveOut(lease.id)}
                      disabled={requestingLeaseId === lease.id}
                      className="text-xs font-semibold bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white px-3 py-1.5 rounded-lg transition-colors"
                    >
                      {requestingLeaseId === lease.id ? "Sending…" : "Request Move-Out"}
                    </button>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Checklist */}
      <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
        <h2 className="font-bold text-gray-900 mb-4 flex items-center gap-2">
          <Sparkles className="w-5 h-5 text-purple-600" /> Your Moving Checklist
        </h2>
        <div className="space-y-3">
          {CHECKLIST.map((item) => {
            const Icon = item.icon;
            const done = !!checklist[item.id];
            return (
              <div key={item.id} className="flex items-start gap-3 p-3 bg-gray-50 rounded-xl">
                <button onClick={() => handleToggle(item.id)} className="shrink-0 mt-0.5">
                  {done ? (
                    <CheckCircle2 className="w-5 h-5 text-green-600" />
                  ) : (
                    <Circle className="w-5 h-5 text-gray-300" />
                  )}
                </button>
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <Icon className="w-4 h-4 text-gray-400" />
                    <p className={`text-sm font-semibold ${done ? "text-gray-400 line-through" : "text-gray-900"}`}>
                      {item.title}
                    </p>
                  </div>
                  <p className="text-xs text-gray-500 mt-0.5">{item.description}</p>
                  <Link href={item.actionHref} className="text-xs text-blue-600 hover:underline mt-1 inline-block">
                    {item.actionLabel} →
                  </Link>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
