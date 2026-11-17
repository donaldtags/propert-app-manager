"use client";

import { useEffect, useState } from "react";
import { CloudUpload, Mail, MessageCircle, Phone } from "lucide-react";
import { enquiries as enquiriesApi } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { formatCents } from "@/lib/money";
import { pendingEntries, readThrough, useSyncStatus } from "@/lib/offline/sync";
import type { OutboxEntry } from "@/lib/offline/types";
import type { Enquiry, EnquiryStatus } from "@/lib/types";
import { useOrgWorkspace, ws } from "@/components/workspace/OrgWorkspace";

const CHANNEL_LABEL: Record<string, string> = {
  WHATSAPP: "WhatsApp", PHONE: "Call", WALK_IN: "Walk-in", INSTAGRAM: "Instagram", FACEBOOK: "Facebook",
  PORTAL: "Portal", WEBSITE: "Website", EMAIL: "Email", REFERRAL: "Referral", SIGNBOARD: "Board", OTHER: "Other",
};
const STATUSES: EnquiryStatus[] = ["NEW", "CONTACTED", "QUALIFIED", "CLOSED"];
const when = new Intl.DateTimeFormat("en-GB", { dateStyle: "medium", timeStyle: "short", timeZone: "Africa/Harare" });

export default function EnquiriesPage() {
  const { slug, can } = useOrgWorkspace();
  const { token, user } = useAuth();
  const sync = useSyncStatus();
  const [items, setItems] = useState<Enquiry[] | null>(null);
  const [pending, setPending] = useState<OutboxEntry[]>([]);
  const [fromCache, setFromCache] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    if (!token) return;
    readThrough(`enquiries:${slug}:${user?.id}`, () => enquiriesApi.list(slug, token)).then(
      (r) => {
        setItems(r.data);
        setFromCache(r.fromCache);
      },
      (e: unknown) => setError(e instanceof Error ? e.message : "Could not load enquiries")
    );
    pendingEntries().then((all) => setPending(all.filter((e) => e.orgSlug === slug && e.op.kind === "captureEnquiry")), () => undefined);
  }, [slug, token, user?.id, sync.pending, reload]);

  if (!can("ENQUIRY_CAPTURE")) return <p className="text-muted">Your role doesn&apos;t include enquiries.</p>;

  const setStatus = async (e: Enquiry, status: EnquiryStatus) => {
    if (!token) return;
    try {
      await enquiriesApi.setStatus(slug, e.id, status, token);
      setReload((n) => n + 1);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not update");
    }
  };

  return (
    <div>
      <h1 className="text-xl font-semibold">Enquiries</h1>
      <p className="text-sm text-muted mt-1">
        Every enquiry from every channel. Tap <strong>Enquiry</strong> (bottom right) to capture one, even offline.
      </p>
      {fromCache && <p className="mt-2 text-xs text-warning">Showing the copy saved on this device.</p>}
      {error && <p className="mt-4 text-danger">{error}</p>}

      <ul className="mt-6 space-y-3">
        {pending.map((p) =>
          p.op.kind === "captureEnquiry" ? (
            <li key={`p${p.seq}`} className={`${ws.card} p-4 border-dashed`}>
              <p className="font-semibold">{p.op.payload.name}</p>
              <p className="text-xs text-muted mt-1 flex items-center gap-1.5">
                <CloudUpload className="w-3.5 h-3.5" /> Waiting to sync · {CHANNEL_LABEL[p.op.payload.channel] ?? p.op.payload.channel}
              </p>
            </li>
          ) : null
        )}
        {items?.map((e) => (
          <li key={e.id} className={`${ws.card} p-4`}>
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div className="min-w-0">
                <p className="font-semibold">{e.name}</p>
                <p className="text-xs text-muted">
                  {CHANNEL_LABEL[e.channel] ?? e.channel}
                  {e.intent && <> · wants to {e.intent.toLowerCase()}</>}
                  {e.budgetCents != null && <> · budget {formatCents(e.budgetCents, e.currency)}</>} · {when.format(new Date(e.capturedAt))}
                </p>
                {e.propertyTitle && <p className="text-xs text-muted">Re: {e.propertyTitle}</p>}
              </div>
              <select
                aria-label={`Status of enquiry from ${e.name}`}
                className={`${ws.input} !w-auto`}
                value={e.status}
                disabled={!sync.online}
                onChange={(ev) => setStatus(e, ev.target.value as EnquiryStatus)}
              >
                {STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {s.charAt(0) + s.slice(1).toLowerCase()}
                  </option>
                ))}
              </select>
            </div>
            {e.message && <p className="mt-2 text-sm">{e.message}</p>}
            <div className="mt-3 flex flex-wrap gap-2">
              {e.phone && (
                <>
                  <a className={ws.buttonGhost} href={`tel:${e.phone.replace(/\s+/g, "")}`}>
                    <Phone className="w-4 h-4" /> Call
                  </a>
                  <a className={ws.buttonGhost} href={`https://wa.me/${e.phone.replace(/\D/g, "")}`} target="_blank" rel="noopener noreferrer">
                    <MessageCircle className="w-4 h-4" /> WhatsApp
                  </a>
                </>
              )}
              {e.email && (
                <a className={ws.buttonGhost} href={`mailto:${e.email}`}>
                  <Mail className="w-4 h-4" /> Email
                </a>
              )}
            </div>
          </li>
        ))}
      </ul>
      {items && items.length === 0 && pending.length === 0 && <p className="mt-8 text-muted">No enquiries yet.</p>}
    </div>
  );
}
