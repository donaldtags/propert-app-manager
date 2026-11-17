"use client";

import Link from "next/link";
import { useEffect, useState, type FormEvent } from "react";
import { CheckCircle2, CloudUpload, Search, UserPlus } from "lucide-react";
import { contacts as contactsApi } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { enqueueAndWait, newClientId, pendingEntries, readThrough, useSyncStatus } from "@/lib/offline/sync";
import type { OutboxEntry } from "@/lib/offline/types";
import type { Contact, ContactSource, ContactType } from "@/lib/types";
import { useOrgWorkspace, ws } from "@/components/workspace/OrgWorkspace";
import { CONTACT_SOURCE_LABEL, CONTACT_TYPE_LABEL } from "@/components/workspace/labels";

const TYPES = Object.keys(CONTACT_TYPE_LABEL) as ContactType[];

export default function ContactsPage() {
  const { slug, can } = useOrgWorkspace();
  const { token, user } = useAuth();
  const sync = useSyncStatus();
  const [items, setItems] = useState<Contact[] | null>(null);
  const [pending, setPending] = useState<OutboxEntry[]>([]);
  const [q, setQ] = useState("");
  const [type, setType] = useState<ContactType | null>(null);
  const [adding, setAdding] = useState(false);
  const [form, setForm] = useState({ fullName: "", phone: "", email: "", source: "NETWORK" as ContactSource, types: [] as ContactType[], tags: "" });
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) return;
    readThrough(`contacts:${slug}:${user?.id}`, () => contactsApi.search(slug, token)).then(
      (r) => setItems(r.data),
      (e: unknown) => setError(e instanceof Error ? e.message : "Could not load contacts")
    );
    pendingEntries().then((all) => setPending(all.filter((e) => e.orgSlug === slug && e.op.kind === "createContact")), () => undefined);
  }, [slug, token, user?.id, sync.pending]);

  if (!can("CONTACTS_MANAGE")) return <p className="text-muted">Your role doesn&apos;t include contacts.</p>;

  const term = q.trim().toLowerCase();
  const digits = term.replace(/\D/g, "").replace(/^0/, "");
  const shown = (items ?? []).filter(
    (c) =>
      (!type || c.types.includes(type)) &&
      (!term ||
        c.fullName.toLowerCase().includes(term) ||
        (digits.length >= 3 && (c.phoneNormalised ?? "").includes(digits)) ||
        (c.email ?? "").toLowerCase().includes(term) ||
        c.tags.some((t) => t.includes(term)))
  );

  const add = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    const sent = await enqueueAndWait(slug, {
      kind: "createContact",
      clientId: newClientId(),
      label: `New contact: ${form.fullName.trim()}`,
      payload: {
        fullName: form.fullName.trim(),
        phone: form.phone.trim() || undefined,
        email: form.email.trim() || undefined,
        source: form.source,
        types: form.types,
        tags: form.tags.split(",").map((t) => t.trim()).filter(Boolean),
      },
    });
    setForm({ fullName: "", phone: "", email: "", source: "NETWORK", types: [], tags: "" });
    setAdding(false);
    setMessage(sent ? "Contact saved." : "Saved on this device. It will sync when you're back online (or appear in Sync if the number already exists).");
  };

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold">Contacts</h1>
          <p className="text-sm text-muted mt-1">Owners, buyers, tenants, landowners, investors and referral partners. Enquiries link here automatically by phone number.</p>
        </div>
        <button className={ws.button} onClick={() => setAdding((a) => !a)}>
          <UserPlus className="w-4 h-4" /> Add contact
        </button>
      </div>

      {adding && (
        <form onSubmit={add} className={`${ws.card} p-4 grid gap-3 sm:grid-cols-2`}>
          <label className="block sm:col-span-2">
            <span className={ws.label}>Full name</span>
            <input className={ws.input} required maxLength={160} value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} />
          </label>
          <label className="block">
            <span className={ws.label}>Phone / WhatsApp</span>
            <input className={ws.input} inputMode="tel" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} placeholder="0772 123 456" />
          </label>
          <label className="block">
            <span className={ws.label}>Email</span>
            <input className={ws.input} type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
          </label>
          <label className="block">
            <span className={ws.label}>How we met</span>
            <select className={ws.input} value={form.source} onChange={(e) => setForm({ ...form, source: e.target.value as ContactSource })}>
              {(Object.keys(CONTACT_SOURCE_LABEL) as ContactSource[]).map((s) => (
                <option key={s} value={s}>{CONTACT_SOURCE_LABEL[s]}</option>
              ))}
            </select>
          </label>
          <label className="block">
            <span className={ws.label}>Tags (comma separated)</span>
            <input className={ws.input} value={form.tags} onChange={(e) => setForm({ ...form, tags: e.target.value })} placeholder="diaspora, uk, cash" />
          </label>
          <fieldset className="sm:col-span-2">
            <legend className={ws.label}>Is a…</legend>
            <div className="flex flex-wrap gap-2">
              {TYPES.map((t) => {
                const on = form.types.includes(t);
                return (
                  <button key={t} type="button" aria-pressed={on}
                    onClick={() => setForm({ ...form, types: on ? form.types.filter((x) => x !== t) : [...form.types, t] })}
                    className={`${ws.chip} min-h-10 px-3 ${on ? "bg-primary text-on-primary border-transparent" : ""}`}>
                    {CONTACT_TYPE_LABEL[t]}
                  </button>
                );
              })}
            </div>
          </fieldset>
          <button className={`${ws.button} sm:col-span-2`}>Save contact</button>
        </form>
      )}
      {message && <p role="status" className="text-sm text-success">{message}</p>}
      {error && <p className="text-sm text-danger">{error}</p>}

      <div className="space-y-2">
        <label className="relative block">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted" aria-hidden />
          <input className={`${ws.input} pl-9`} placeholder="Search name, phone, email or tag" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search contacts" />
        </label>
        <div className="flex gap-2 overflow-x-auto pb-1">
          <button className={`${ws.chip} min-h-9 px-3 ${type === null ? "bg-primary text-on-primary border-transparent" : ""}`} onClick={() => setType(null)}>All</button>
          {TYPES.map((t) => (
            <button key={t} className={`${ws.chip} min-h-9 px-3 whitespace-nowrap ${type === t ? "bg-primary text-on-primary border-transparent" : ""}`} onClick={() => setType(t)}>
              {CONTACT_TYPE_LABEL[t]}
            </button>
          ))}
        </div>
      </div>

      <ul className="space-y-2">
        {pending.map((p) =>
          p.op.kind === "createContact" ? (
            <li key={`p${p.seq}`} className={`${ws.card} p-3 border-dashed`}>
              <p className="font-medium">{p.op.payload.fullName}</p>
              <p className="text-xs text-muted flex items-center gap-1"><CloudUpload className="w-3.5 h-3.5" /> {p.lastError ?? "Waiting to sync"}</p>
            </li>
          ) : null
        )}
        {shown.map((c) => (
          <li key={c.id}>
            <Link href={`/orgs/${slug}/contacts/${c.id}`} className={`${ws.card} block p-3 hover:border-accent`}>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="font-medium">
                  {c.fullName} {c.qualified && <CheckCircle2 className="inline w-4 h-4 text-success" aria-label="Qualified" />}
                </p>
                <div className="flex flex-wrap gap-1">
                  {c.types.map((t) => <span key={t} className={ws.chip}>{CONTACT_TYPE_LABEL[t]}</span>)}
                </div>
              </div>
              <p className="text-xs text-muted mt-1">
                {[c.phone, c.email, c.source && CONTACT_SOURCE_LABEL[c.source], ...c.tags.map((t) => `#${t}`)].filter(Boolean).join(" · ")}
              </p>
            </Link>
          </li>
        ))}
      </ul>
      {items && shown.length === 0 && pending.length === 0 && <p className="text-muted">No contacts match.</p>}
    </div>
  );
}
