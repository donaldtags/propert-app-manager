"use client";

import { useParams } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { AlertTriangle, CalendarPlus, HandCoins, MessageCircle } from "lucide-react";
import { contacts as contactsApi, offers as offersApi, showings } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { formatCents, parseToCents } from "@/lib/money";
import { enqueueAllAndWait, newClientId, readThrough, useSyncStatus } from "@/lib/offline/sync";
import type { OutboxOp } from "@/lib/offline/types";
import type { Financing, Offer, Showing } from "@/lib/types";
import ContactPicker, { type PickedContact } from "@/components/workspace/ContactPicker";
import { useOrgWorkspace, ws } from "@/components/workspace/OrgWorkspace";
import ViewingCard from "@/components/workspace/ViewingCard";
import { FINANCING_LABEL } from "@/components/workspace/labels";

const STATUS_LABEL: Record<Offer["status"], string> = {
  RECEIVED: "Received", PRESENTED: "With the owner", COUNTERED: "Owner countered", ACCEPTED: "Accepted", REJECTED: "Rejected", WITHDRAWN: "Withdrawn",
};

export default function ActivityPage() {
  const { id } = useParams<{ id: string }>();
  const propertyId = Number(id);
  const { slug } = useOrgWorkspace();
  const { token, user } = useAuth();
  const sync = useSyncStatus();
  const [viewings, setViewings] = useState<Showing[] | null>(null);
  const [offerList, setOfferList] = useState<Offer[] | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    if (!token) return;
    readThrough(`listing-viewings:${slug}:${propertyId}:${user?.id}`, () => showings.forListing(slug, propertyId, token)).then((r) => setViewings(r.data), () => setViewings([]));
    if (sync.online) offersApi.forListing(slug, propertyId, token).then(setOfferList, () => setOfferList([]));
  }, [slug, propertyId, token, user?.id, sync.online, sync.pending, reload]);

  const refresh = (m: string) => { setMessage(m); setReload((n) => n + 1); };

  return (
    <div className="max-w-3xl space-y-6">
      {message && <p role="status" className="text-sm text-success">{message}</p>}
      <ScheduleViewing slug={slug} propertyId={propertyId} onDone={refresh} />
      <section aria-labelledby="v-h">
        <h2 id="v-h" className="font-semibold">Viewings</h2>
        {!viewings ? <p className="text-muted mt-2">Loading…</p> : viewings.length === 0 ? <p className="text-sm text-muted mt-2">None yet.</p> : (
          <ul className="mt-3 space-y-2">{viewings.map((v) => <ViewingCard key={v.id} slug={slug} viewing={v} onSaved={refresh} />)}</ul>
        )}
      </section>

      <section aria-labelledby="o-h" className="space-y-3">
        <h2 id="o-h" className="font-semibold">Offers</h2>
        {!sync.online ? <p className="text-sm text-muted">Offers need a connection: every step is checked against the mandate&apos;s authority on the server.</p> : (
          <>
            <RecordOffer slug={slug} propertyId={propertyId} onDone={refresh} />
            {!offerList ? <p className="text-muted">Loading…</p> : offerList.length === 0 ? <p className="text-sm text-muted">No offers yet.</p> : (
              <ul className="space-y-3">{offerList.map((o) => <OfferCard key={o.id} slug={slug} offer={o} onDone={refresh} />)}</ul>
            )}
          </>
        )}
      </section>
    </div>
  );
}

function ScheduleViewing({ slug, propertyId, onDone }: { slug: string; propertyId: number; onDone: (m: string) => void }) {
  const [open, setOpen] = useState(false);
  const [contact, setContact] = useState<PickedContact | null>(null);
  const [at, setAt] = useState("");
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!contact || !at) return setError("Choose who and when.");
    const ops: OutboxOp[] = [];
    if (contact.createOp) ops.push(contact.createOp);
    ops.push({
      kind: "scheduleViewing",
      clientId: newClientId(),
      propertyId,
      contact: contact.ref,
      scheduledAt: new Date(at).toISOString(),
      label: `Viewing with ${contact.name}`,
    });
    const sent = await enqueueAllAndWait(slug, ops);
    setOpen(false);
    setContact(null);
    setAt("");
    onDone(sent ? "Viewing booked." : "Saved on this device. It will sync when you're back online (the listing needs a mandate in force on that day).");
  };

  if (!open) return <button className={ws.button} onClick={() => setOpen(true)}><CalendarPlus className="w-4 h-4" /> Book a viewing</button>;
  return (
    <form onSubmit={submit} className={`${ws.card} p-4 space-y-3`}>
      <ContactPicker slug={slug} label="Who's viewing" onPick={setContact} />
      <label className="block"><span className={ws.label}>When</span>
        <input type="datetime-local" className={ws.input} required value={at} onChange={(e) => setAt(e.target.value)} /></label>
      {error && <p className="text-sm text-danger">{error}</p>}
      <div className="flex gap-2"><button className={ws.button}>Book</button><button type="button" className={ws.buttonGhost} onClick={() => setOpen(false)}>Cancel</button></div>
    </form>
  );
}

function RecordOffer({ slug, propertyId, onDone }: { slug: string; propertyId: number; onDone: (m: string) => void }) {
  const { token } = useAuth();
  const [open, setOpen] = useState(false);
  const [buyer, setBuyer] = useState<PickedContact | null>(null);
  const [form, setForm] = useState({ amount: "", deposit: "", financing: "" as Financing | "", conditions: "", expiresOn: "" });
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!token || !buyer) return setError("Choose the buyer.");
    const amountCents = parseToCents(form.amount);
    if (!amountCents) return setError("Enter the offer amount.");
    try {
      let buyerId: number;
      if ("id" in buyer.ref) buyerId = buyer.ref.id;
      else if (buyer.createOp?.kind === "createContact") buyerId = (await contactsApi.create(slug, { ...buyer.createOp.payload, clientId: buyer.createOp.clientId }, token)).id;
      else return setError("Choose the buyer.");
      const o = await offersApi.record(slug, propertyId, {
        clientId: newClientId(),
        buyerContactId: buyerId,
        amountCents,
        depositCents: form.deposit ? parseToCents(form.deposit) ?? undefined : undefined,
        financing: form.financing || undefined,
        conditions: form.conditions.trim() || undefined,
        expiresOn: form.expiresOn || undefined,
      }, token);
      setOpen(false);
      onDone(o.withinAuthority ? "Offer recorded." : "Offer recorded. It's below the owner's minimum in the mandate, so only the owner can accept it.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not record the offer");
    }
  };

  if (!open) return <button className={ws.button} onClick={() => setOpen(true)}><HandCoins className="w-4 h-4" /> Record an offer</button>;
  return (
    <form onSubmit={submit} className={`${ws.card} p-4 grid gap-3 sm:grid-cols-2`}>
      <div className="sm:col-span-2"><ContactPicker slug={slug} label="Buyer" onPick={setBuyer} /></div>
      <label className="block"><span className={ws.label}>Offer (USD)</span><input className={ws.input} inputMode="decimal" required value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} /></label>
      <label className="block"><span className={ws.label}>Deposit (USD)</span><input className={ws.input} inputMode="decimal" value={form.deposit} onChange={(e) => setForm({ ...form, deposit: e.target.value })} /></label>
      <label className="block"><span className={ws.label}>Financing</span>
        <select className={ws.input} value={form.financing} onChange={(e) => setForm({ ...form, financing: e.target.value as Financing })}>
          <option value="">Not stated</option>
          {(Object.keys(FINANCING_LABEL) as Financing[]).map((f) => <option key={f} value={f}>{FINANCING_LABEL[f]}</option>)}
        </select></label>
      <label className="block"><span className={ws.label}>Valid until</span><input type="date" className={ws.input} value={form.expiresOn} onChange={(e) => setForm({ ...form, expiresOn: e.target.value })} /></label>
      <label className="block sm:col-span-2"><span className={ws.label}>Conditions</span>
        <textarea className={`${ws.input} min-h-16`} value={form.conditions} onChange={(e) => setForm({ ...form, conditions: e.target.value })} placeholder="e.g. subject to bond approval within 30 days" /></label>
      {error && <p className="sm:col-span-2 text-sm text-danger">{error}</p>}
      <div className="sm:col-span-2 flex gap-2"><button className={ws.button}>Record offer</button><button type="button" className={ws.buttonGhost} onClick={() => setOpen(false)}>Cancel</button></div>
    </form>
  );
}

function OfferCard({ slug, offer: o, onDone }: { slug: string; offer: Offer; onDone: (m: string) => void }) {
  const { token } = useAuth();
  const [mode, setMode] = useState<"" | "ACCEPT" | "COUNTER" | "REJECT">("");
  const [counter, setCounter] = useState("");
  const [instruction, setInstruction] = useState("");
  const [error, setError] = useState<string | null>(null);
  const open = o.status === "RECEIVED" || o.status === "PRESENTED" || o.status === "COUNTERED";
  const needsInstruction = mode === "ACCEPT" ? !o.withinAuthority
    : mode === "COUNTER" ? (o.mandateMinimumCents != null && (parseToCents(counter) ?? 0) < o.mandateMinimumCents) : false;
  const summary = `Offer on your property: ${formatCents(o.amountCents, o.currency)} from ${o.buyerName ?? "a buyer"}`
    + `${o.financing ? ` (${FINANCING_LABEL[o.financing].toLowerCase()})` : ""}${o.conditions ? `. Conditions: ${o.conditions}` : ""}. Please reply ACCEPT, COUNTER or DECLINE.`;

  const act = async (fn: () => Promise<unknown>, done: string) => {
    if (!token) return;
    setError(null);
    try {
      await fn();
      setMode("");
      onDone(done);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not save");
    }
  };

  return (
    <li className={`${ws.card} p-4`}>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <p className="font-semibold">{formatCents(o.amountCents, o.currency)} · {o.buyerName}</p>
          <p className="text-xs text-muted">
            {[o.financing && FINANCING_LABEL[o.financing], o.depositCents && `deposit ${formatCents(o.depositCents, o.currency)}`, o.expiresOn && `valid until ${o.expiresOn}`, o.conditions].filter(Boolean).join(" · ")}
          </p>
          {o.counterAmountCents && <p className="text-xs">Owner countered at {formatCents(o.counterAmountCents, o.currency)}</p>}
          {!o.withinAuthority && (
            <p className="text-xs text-warning mt-1 inline-flex items-center gap-1"><AlertTriangle className="w-3.5 h-3.5" /> Below the owner&apos;s minimum ({formatCents(o.mandateMinimumCents, o.currency)}). Only the owner can accept it.</p>
          )}
          {o.ownerInstructionReference && <p className="text-xs text-muted">Owner&apos;s instruction: {o.ownerInstructionReference}</p>}
        </div>
        <span className={`${ws.chip} ${o.status === "ACCEPTED" ? "text-success" : o.status === "REJECTED" || o.status === "WITHDRAWN" ? "text-muted" : ""}`}>{STATUS_LABEL[o.status]}</span>
      </div>

      {open && (
        <div className="mt-3 flex flex-wrap gap-2">
          {(o.status === "RECEIVED" || o.status === "COUNTERED") && (
            <a className={ws.button} href={`https://wa.me/${o.ownerWhatsapp ?? ""}?text=${encodeURIComponent(summary)}`} target="_blank" rel="noopener noreferrer"
              onClick={() => token && void act(() => offersApi.present(slug, o.id, "WHATSAPP", token), "Marked as sent to the owner.")}>
              <MessageCircle className="w-4 h-4" /> Send to {o.ownerName ?? "owner"}
            </a>
          )}
          <button className={ws.buttonGhost} onClick={() => setMode("ACCEPT")}>Owner accepts</button>
          <button className={ws.buttonGhost} onClick={() => setMode("COUNTER")}>Owner counters</button>
          <button className={ws.buttonGhost} onClick={() => setMode("REJECT")}>Owner declines</button>
          <button className={ws.buttonGhost} onClick={() => token && act(() => offersApi.withdraw(slug, o.id, undefined, token), "Offer withdrawn.")}>Buyer withdrew</button>
        </div>
      )}
      {o.status === "ACCEPTED" && (
        <button className={`${ws.buttonGhost} mt-3`} onClick={() => token && act(() => offersApi.withdraw(slug, o.id, "Deal fell through", token), "Offer withdrawn; the listing is back on the market if it still qualifies.")}>
          Deal fell through
        </button>
      )}

      {mode && (
        <div className="mt-3 space-y-2 border-t border-line pt-3">
          {mode === "COUNTER" && (
            <label className="block"><span className={ws.label}>Owner&apos;s counter (USD)</span>
              <input className={ws.input} inputMode="decimal" value={counter} onChange={(e) => setCounter(e.target.value)} /></label>
          )}
          {needsInstruction && (
            <label className="block"><span className={ws.label}>Owner&apos;s written instruction (required below the mandate minimum)</span>
              <input className={ws.input} value={instruction} onChange={(e) => setInstruction(e.target.value)} placeholder="e.g. WhatsApp from owner, 17 Nov 14:02" /></label>
          )}
          <div className="flex gap-2">
            <button className={ws.button} onClick={() => token && act(() => offersApi.respond(slug, o.id, {
              decision: mode,
              counterAmountCents: mode === "COUNTER" ? parseToCents(counter) ?? undefined : undefined,
              ownerInstructionReference: instruction.trim() || undefined,
            }, token), mode === "ACCEPT" ? "Accepted. The listing is now reserved." : mode === "COUNTER" ? "Counter-offer recorded." : "Offer declined.")}>
              Confirm
            </button>
            <button className={ws.buttonGhost} onClick={() => setMode("")}>Cancel</button>
          </div>
        </div>
      )}
      {error && <p role="alert" className="mt-2 text-sm text-danger">{error}</p>}

      {o.events.length > 0 && (
        <details className="mt-3">
          <summary className="text-xs cursor-pointer text-muted">History ({o.events.length})</summary>
          <ol className="mt-1 text-xs space-y-0.5">
            {o.events.map((e, i) => <li key={i}>{new Date(e.at).toLocaleString("en-GB", { timeZone: "Africa/Harare" })} · {e.type.toLowerCase()}{e.amountCents ? ` · ${formatCents(e.amountCents, o.currency)}` : ""}{e.note ? ` · ${e.note}` : ""}</li>)}
          </ol>
        </details>
      )}
    </li>
  );
}
