"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { timeline as timelineApi } from "@/lib/api";
import type { TimelineEvent, TimelineEventType } from "@/lib/types";
import PageLoader from "@/components/PageLoader";
import {
  History,
  AlertCircle,
  DollarSign,
  Wrench,
  Shield,
  FileText,
  Calendar,
  MessageCircle,
  ClipboardList,
} from "lucide-react";

const TYPE_ICONS: Record<TimelineEventType, typeof History> = {
  LEASE: FileText,
  PAYMENT: DollarSign,
  MAINTENANCE: Wrench,
  ESCROW: Shield,
  DOCUMENT: FileText,
  VIEWING: Calendar,
  MESSAGE: MessageCircle,
  APPLICATION: ClipboardList,
};

const TYPE_COLORS: Record<TimelineEventType, string> = {
  LEASE: "bg-forest-100 text-forest-600",
  PAYMENT: "bg-forest-100 text-forest-600",
  MAINTENANCE: "bg-amber-100 text-amber-600",
  ESCROW: "bg-indigo-100 text-indigo-600",
  DOCUMENT: "bg-gray-100 text-gray-600",
  VIEWING: "bg-purple-100 text-purple-600",
  MESSAGE: "bg-pink-100 text-pink-600",
  APPLICATION: "bg-teal-100 text-teal-600",
};

const TYPE_LABELS: Record<TimelineEventType, string> = {
  LEASE: "Lease",
  PAYMENT: "Payment",
  MAINTENANCE: "Maintenance",
  ESCROW: "Deposit Protection",
  DOCUMENT: "Document",
  VIEWING: "Viewing",
  MESSAGE: "Message",
  APPLICATION: "Application",
};

const FILTERS: (TimelineEventType | "ALL")[] = [
  "ALL",
  "LEASE",
  "PAYMENT",
  "MAINTENANCE",
  "ESCROW",
  "DOCUMENT",
  "VIEWING",
  "MESSAGE",
  "APPLICATION",
];

export default function HomeTimelinePage() {
  const { user, token, loading } = useAuth();
  const router = useRouter();
  const [events, setEvents] = useState<TimelineEvent[]>([]);
  const [eventsLoading, setEventsLoading] = useState(true);
  const [error, setError] = useState("");
  const [filter, setFilter] = useState<TimelineEventType | "ALL">("ALL");

  useEffect(() => {
    if (!loading && !user) router.push("/login?redirect=/timeline");
  }, [user, loading, router]);

  useEffect(() => {
    if (!user || !token) return;
    timelineApi
      .mine(token)
      .then(setEvents)
      .catch(() => setError("Failed to load your home timeline."))
      .finally(() => setEventsLoading(false));
  }, [user, token]);

  const visibleEvents = filter === "ALL" ? events : events.filter((e) => e.type === filter);

  if (loading) return <PageLoader />;

  return (
    <div className="max-w-3xl mx-auto px-4 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
          <History className="w-7 h-7 text-forest-600" /> Home Timeline
        </h1>
        <p className="text-gray-500 mt-1">Every payment, maintenance update, document, and milestone in your home journey</p>
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-5 flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0" /> {error}
        </div>
      )}

      <div className="flex flex-wrap gap-2 mb-6">
        {FILTERS.map((f) => (
          <button
            key={f}
            onClick={() => setFilter(f)}
            className={`text-xs font-semibold px-3 py-1.5 rounded-full border transition-colors ${
              filter === f ? "bg-forest-600 text-white border-forest-600" : "border-gray-200 text-gray-600 hover:border-gray-300"
            }`}
          >
            {f === "ALL" ? "All" : TYPE_LABELS[f]}
          </button>
        ))}
      </div>

      {eventsLoading ? (
        <div className="space-y-4">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="h-16 bg-gray-100 rounded-2xl animate-pulse" />
          ))}
        </div>
      ) : visibleEvents.length === 0 ? (
        <div className="text-center py-16 text-gray-500">
          <History className="w-12 h-12 mx-auto mb-4 opacity-30" />
          <p className="text-lg font-medium">Nothing here yet</p>
          <p className="text-sm text-gray-400 mt-1">Your home journey will show up here as it happens.</p>
        </div>
      ) : (
        <div className="relative pl-6">
          <div className="absolute left-[11px] top-2 bottom-2 w-0.5 bg-gray-100" />
          <div className="space-y-5">
            {visibleEvents.map((event, i) => {
              const Icon = TYPE_ICONS[event.type];
              return (
                <div key={`${event.type}-${event.relatedId}-${i}`} className="relative flex gap-4">
                  <div
                    className={`absolute -left-6 w-6 h-6 rounded-full flex items-center justify-center shrink-0 ${TYPE_COLORS[event.type]}`}
                  >
                    <Icon className="w-3.5 h-3.5" />
                  </div>
                  <div className="bg-white border border-gray-200 rounded-2xl p-4 shadow-sm flex-1 ml-4">
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <p className="text-sm font-semibold text-gray-900">{event.title}</p>
                        {event.description && <p className="text-xs text-gray-500 mt-0.5">{event.description}</p>}
                      </div>
                      <p className="text-xs text-gray-400 shrink-0 whitespace-nowrap">
                        {new Date(event.occurredAt).toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" })}
                      </p>
                    </div>
                    {event.status && (
                      <span className="inline-block mt-2 text-[11px] font-medium px-2 py-0.5 rounded-full bg-gray-50 text-gray-600 border border-gray-100">
                        {event.status.replace(/_/g, " ")}
                      </span>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
