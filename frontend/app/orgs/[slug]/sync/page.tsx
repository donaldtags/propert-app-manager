"use client";

import { useEffect, useState } from "react";
import { AlertTriangle, CloudUpload, RefreshCw, Trash2 } from "lucide-react";
import { conflictsFor, discardOutboxEntry, dismissConflict } from "@/lib/offline/db";
import { currentUserId, pendingEntries, refreshCounts, syncNow, useSyncStatus } from "@/lib/offline/sync";
import type { ConflictEntry, OutboxEntry } from "@/lib/offline/types";
import { useOrgWorkspace, ws } from "@/components/workspace/OrgWorkspace";

const when = new Intl.DateTimeFormat("en-GB", { dateStyle: "medium", timeStyle: "short", timeZone: "Africa/Harare" });
const FIELD_LABEL: Record<string, string> = {
  titleReference: "Title / deed no.",
  titleType: "Title type",
  photos: "Consent to photos",
  address: "Consent to address",
};
const show = (v: unknown) => (v === null || v === undefined || v === "" ? "(empty)" : typeof v === "boolean" ? (v ? "yes" : "no") : String(v));

/** What's waiting on this device, and anything that needs a person to look at it. */
export default function SyncPage() {
  const { slug } = useOrgWorkspace();
  const status = useSyncStatus();
  const [pending, setPending] = useState<OutboxEntry[]>([]);
  const [conflicts, setConflicts] = useState<ConflictEntry[]>([]);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    const userId = currentUserId();
    pendingEntries().then((e) => setPending(e.filter((x) => x.orgSlug === slug)), () => undefined);
    if (userId !== undefined) {
      conflictsFor(userId).then((c) => setConflicts(c.filter((x) => x.orgSlug === slug)), () => undefined);
    }
  }, [slug, status.pending, status.conflicts, status.syncing, reload]);

  return (
    <div className="max-w-3xl space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold">Sync</h1>
          <p className="text-sm text-muted mt-1">
            {status.online ? "Online." : "Offline."} {pending.length} change{pending.length === 1 ? "" : "s"} waiting on this device.
            {status.lastSyncedAt && <> Last synced {when.format(new Date(status.lastSyncedAt))}.</>}
          </p>
        </div>
        <button className={ws.button} disabled={!status.online || status.syncing} onClick={() => void syncNow().then(() => setReload((n) => n + 1))}>
          <RefreshCw className={`w-4 h-4 ${status.syncing ? "animate-spin" : ""}`} /> Sync now
        </button>
      </div>

      <section aria-labelledby="conflicts-h">
        <h2 id="conflicts-h" className="font-semibold flex items-center gap-2">
          <AlertTriangle className="w-4 h-4 text-warning" /> Needs attention ({conflicts.length})
        </h2>
        {conflicts.length === 0 ? (
          <p className="text-sm text-muted mt-2">Nothing. Everything that synced was accepted.</p>
        ) : (
          <ul className="mt-3 space-y-3">
            {conflicts.map((c) => (
              <li key={c.id} className={`${ws.card} p-4`}>
                <p className="font-medium">{c.label}</p>
                <p className="text-sm text-muted mt-1">{c.message}</p>
                {c.fields && (
                  <table className="mt-2 text-xs w-full">
                    <thead>
                      <tr className="text-muted text-left">
                        <th className="font-medium pr-2">Field</th>
                        <th className="font-medium pr-2">Yours (not saved)</th>
                        <th className="font-medium">Kept on server</th>
                      </tr>
                    </thead>
                    <tbody>
                      {c.fields.map((f) => (
                        <tr key={f.field}>
                          <td className="pr-2">{FIELD_LABEL[f.field] ?? f.field}</td>
                          <td className="pr-2">{show(f.mine)}</td>
                          <td>{show(f.theirs)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
                <button className={`${ws.buttonGhost} mt-3`} onClick={() => c.id && dismissConflict(c.id).then(refreshCounts).then(() => setReload((n) => n + 1))}>
                  Dismiss
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section aria-labelledby="pending-h">
        <h2 id="pending-h" className="font-semibold flex items-center gap-2">
          <CloudUpload className="w-4 h-4" /> Waiting to sync ({pending.length})
        </h2>
        {pending.length === 0 ? (
          <p className="text-sm text-muted mt-2">Nothing waiting.</p>
        ) : (
          <ol className="mt-3 space-y-2">
            {pending.map((p) => (
              <li key={p.seq} className={`${ws.card} p-3 flex flex-wrap items-center justify-between gap-2`}>
                <div className="min-w-0">
                  <p className="text-sm font-medium truncate">{p.op.label}</p>
                  <p className="text-xs text-muted">
                    Saved {when.format(new Date(p.createdAt))}
                    {p.lastError && <> · {p.lastError}</>}
                  </p>
                </div>
                <button
                  className={ws.buttonGhost}
                  aria-label={`Discard ${p.op.label}`}
                  onClick={() => {
                    if (window.confirm(`Discard "${p.op.label}"? It hasn't reached the server and will be lost. Anything queued after it that depends on it will fail.`)) {
                      void discardOutboxEntry(p.seq!).then(refreshCounts).then(() => setReload((n) => n + 1));
                    }
                  }}
                >
                  <Trash2 className="w-4 h-4" /> Discard
                </button>
              </li>
            ))}
          </ol>
        )}
      </section>
    </div>
  );
}
