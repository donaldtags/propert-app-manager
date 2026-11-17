"use client";

import Link from "next/link";
import { AlertTriangle, CloudOff, RefreshCw, Check } from "lucide-react";
import { syncNow, useSyncStatus } from "@/lib/offline/sync";

/** Compact connection + sync state for the workspace header. Links to the sync page. */
export default function SyncIndicator({ slug }: { slug: string }) {
  const s = useSyncStatus();
  const label = !s.online
    ? s.pending > 0
      ? `Offline · ${s.pending} waiting`
      : "Offline"
    : s.syncing
      ? "Syncing…"
      : s.conflicts > 0
        ? `${s.conflicts} need attention`
        : s.pending > 0
          ? `${s.pending} waiting`
          : "Synced";
  const Icon = !s.online ? CloudOff : s.syncing ? RefreshCw : s.conflicts > 0 || s.pending > 0 ? AlertTriangle : Check;
  const tone = !s.online || s.conflicts > 0 ? "text-warning" : s.pending > 0 ? "text-muted" : "text-success";

  return (
    <Link
      href={`/orgs/${slug}/sync`}
      onClick={() => s.online && void syncNow()}
      className={`inline-flex items-center gap-1.5 rounded-lg border border-line px-2.5 min-h-11 text-xs font-medium ${tone}`}
      aria-live="polite"
    >
      <Icon className={`w-4 h-4 ${s.syncing ? "animate-spin" : ""}`} aria-hidden />
      <span>{label}</span>
    </Link>
  );
}
