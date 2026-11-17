"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { ArrowLeft, Mail, MessageCircle, Phone } from "lucide-react";
import { contacts as contactsApi } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { formatCents, parseToCents } from "@/lib/money";
import { readThrough, useSyncStatus } from "@/lib/offline/sync";
import type { BuyingTimeline, ContactDetail, Financing } from "@/lib/types";
import { useOrgWorkspace, ws } from "@/components/workspace/OrgWorkspace";
import { CONTACT_SOURCE_LABEL, CONTACT_TYPE_LABEL, FINANCING_LABEL, TIMELINE_LABEL } from "@/components/workspace/labels";

const when = new Intl.DateTimeFormat("en-GB", { dateStyle: "medium", timeStyle: "short", timeZone: "Africa/Harare" });
const KIND_LABEL = { ENQUIRY: "Enquiry", VIEWING: "Viewing", OFFER: "Offer", DEAL: "Deal" } as const;

export default function ContactDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { slug } = useOrgWorkspace();
  const { token, user } = useAuth();
  const sync = useSyncStatus();
  const [detail, setDetail] = useState<ContactDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState<string | null>(null);
  const [q, setQ] = useState({ budget: "", timeline: "" as BuyingTimeline | "", financing: "" as Financing | "", areas: "", beds: "", decisionMaker: "" as "" | "yes" | "no", note: "" });

  const key = `contact:${slug}:${id}:${user?.id}`;
  useEffect(() => {
    if (!token) return;
    readThrough(key, () => contactsApi.get(slug, Number(id), token)).then(
      (r) => {
        setDetail(r.data);
        const c = r.data.contact;
        setQ({
          budget: c.budgetCents ? String(c.budgetCents / 100) : "",
          timeline: c.timeline ?? "",
          financing: c.financing ?? "",
          areas: c.preferredAreas ?? "",
          beds: c.minBedrooms != null ? String(c.minBedrooms) : "",
          decisionMaker: c.decisionMaker == null ? "" : c.decisionMaker ? "yes" : "no",
          note: c.decisionMakerNote ?? "",
        });
      },
      (e: unknown) => setError(e instanceof Error ? e.message : "Could not load the contact")
    );
  }, [slug, id, token, key]);

  if (error) return <p className="text-danger">{error}</p>;
  if (!detail) return <p className="text-muted">Loading…</p>;
  const c = detail.contact;

  const saveQualification = async (e: FormEvent) => {
    e.preventDefault();
    if (!token) return;
    setError(null);
    try {
      const budgetCents = q.budget ? parseToCents(q.budget) ?? undefined : undefined;
      const updated = await contactsApi.qualify(slug, c.id, {
        budgetCents,
        timeline: q.timeline || undefined,
        financing: q.financing || undefined,
        preferredAreas: q.areas,
        minBedrooms: q.beds ? Number(q.beds) : undefined,
        decisionMaker: q.decisionMaker === "" ? undefined : q.decisionMaker === "yes",
        decisionMakerNote: q.note,
      }, token);
      setDetail({ ...detail, contact: updated });
      setSaved(updated.qualified ? "Qualified." : "Saved. Answer all five questions to mark them qualified.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save");
    }
  };

  return (
    <div className="max-w-3xl space-y-5">
      <Link href={`/orgs/${slug}/contacts`} className="inline-flex items-center gap-1 text-sm text-muted hover:text-ink"><ArrowLeft className="w-4 h-4" /> Contacts</Link>
      <header>
        <h1 className="text-xl sm:text-2xl font-semibold">{c.fullName}</h1>
        <div className="mt-2 flex flex-wrap gap-1">
          {c.types.map((t) => <span key={t} className={ws.chip}>{CONTACT_TYPE_LABEL[t]}</span>)}
          {c.qualified && <span className={`${ws.chip} text-success`}>Qualified</span>}
        </div>
        <p className="text-sm text-muted mt-2">
          {[c.source && `Met via ${CONTACT_SOURCE_LABEL[c.source]}`, ...c.tags.map((t) => `#${t}`)].filter(Boolean).join(" · ")}
        </p>
        <div className="mt-3 flex flex-wrap gap-2">
          {c.phone && <a className={ws.buttonGhost} href={`tel:${c.phoneNormalised ?? c.phone}`}><Phone className="w-4 h-4" /> Call</a>}
          {c.whatsapp && <a className={ws.buttonGhost} href={`https://wa.me/${c.whatsapp}`} target="_blank" rel="noopener noreferrer"><MessageCircle className="w-4 h-4" /> WhatsApp</a>}
          {c.email && <a className={ws.buttonGhost} href={`mailto:${c.email}`}><Mail className="w-4 h-4" /> Email</a>}
          {c.types.includes("OWNER") && <Link className={ws.buttonGhost} href={`/orgs/${slug}/pipeline?owner=${c.id}`}>Deals</Link>}
        </div>
      </header>

      <section className={`${ws.card} p-4`} aria-labelledby="qual-h">
        <h2 id="qual-h" className="font-semibold">Buyer / tenant qualification</h2>
        <p className="text-xs text-muted mt-1">Budget, timeline, financing, areas and who decides. Needs a connection to save.</p>
        <form onSubmit={saveQualification} className="mt-3 grid gap-3 sm:grid-cols-2">
          <label className="block"><span className={ws.label}>Budget (USD)</span>
            <input className={ws.input} inputMode="decimal" value={q.budget} onChange={(e) => setQ({ ...q, budget: e.target.value })} /></label>
          <label className="block"><span className={ws.label}>Timeline</span>
            <select className={ws.input} value={q.timeline} onChange={(e) => setQ({ ...q, timeline: e.target.value as BuyingTimeline })}>
              <option value="">Not asked</option>
              {(Object.keys(TIMELINE_LABEL) as BuyingTimeline[]).map((t) => <option key={t} value={t}>{TIMELINE_LABEL[t]}</option>)}
            </select></label>
          <label className="block"><span className={ws.label}>Financing</span>
            <select className={ws.input} value={q.financing} onChange={(e) => setQ({ ...q, financing: e.target.value as Financing })}>
              <option value="">Not asked</option>
              {(Object.keys(FINANCING_LABEL) as Financing[]).map((t) => <option key={t} value={t}>{FINANCING_LABEL[t]}</option>)}
            </select></label>
          <label className="block"><span className={ws.label}>Minimum bedrooms</span>
            <input className={ws.input} type="number" min={0} max={20} value={q.beds} onChange={(e) => setQ({ ...q, beds: e.target.value })} /></label>
          <label className="block sm:col-span-2"><span className={ws.label}>Areas</span>
            <input className={ws.input} value={q.areas} onChange={(e) => setQ({ ...q, areas: e.target.value })} placeholder="Greendale, Highlands, Borrowdale" /></label>
          <label className="block"><span className={ws.label}>Makes the decision?</span>
            <select className={ws.input} value={q.decisionMaker} onChange={(e) => setQ({ ...q, decisionMaker: e.target.value as "" | "yes" | "no" })}>
              <option value="">Not asked</option><option value="yes">Yes</option><option value="no">No, someone else</option>
            </select></label>
          <label className="block"><span className={ws.label}>Who else decides</span>
            <input className={ws.input} value={q.note} onChange={(e) => setQ({ ...q, note: e.target.value })} placeholder="e.g. spouse in the UK" /></label>
          <div className="sm:col-span-2 flex items-center gap-3">
            <button className={ws.button} disabled={!sync.online}>Save qualification</button>
            {saved && <span role="status" className="text-sm text-success">{saved}</span>}
          </div>
          {c.budgetCents != null && <p className="sm:col-span-2 text-xs text-muted">Current budget {formatCents(c.budgetCents, c.budgetCurrency)}</p>}
        </form>
      </section>

      <section aria-labelledby="act-h">
        <h2 id="act-h" className="font-semibold">History</h2>
        {detail.activity.length === 0 ? (
          <p className="text-sm text-muted mt-2">Nothing yet.</p>
        ) : (
          <ol className="mt-3 space-y-2">
            {detail.activity.map((a) => (
              <li key={`${a.kind}${a.id}`} className={`${ws.card} p-3`}>
                <p className="text-sm"><span className="font-medium">{KIND_LABEL[a.kind]}</span> · {a.summary}</p>
                <p className="text-xs text-muted mt-0.5">
                  {a.at && when.format(new Date(a.at))}
                  {a.propertyId && (
                    <> · <Link className="underline" href={`/orgs/${slug}/listings/${a.propertyId}`}>{a.propertyTitle ?? `Listing #${a.propertyId}`}</Link></>
                  )}
                </p>
              </li>
            ))}
          </ol>
        )}
      </section>
    </div>
  );
}
