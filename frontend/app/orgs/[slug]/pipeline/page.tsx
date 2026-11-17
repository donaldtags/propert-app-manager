"use client";

import Link from "next/link";
import { Suspense, useEffect, useState, type FormEvent } from "react";
import { useSearchParams } from "next/navigation";
import { CloudUpload, Columns3, List, Plus } from "lucide-react";
import { orgListings, pipeline } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { formatCents, parseToCents } from "@/lib/money";
import { enqueueAllAndWait, newClientId, pendingEntries, readThrough, useSyncStatus } from "@/lib/offline/sync";
import type { OutboxEntry, OutboxOp } from "@/lib/offline/types";
import type { OrgListingSummary, PipelineCard, PipelineStage } from "@/lib/types";
import ContactPicker, { type PickedContact } from "@/components/workspace/ContactPicker";
import { useOrgWorkspace, ws } from "@/components/workspace/OrgWorkspace";
import { STAGE_LABEL, STAGES } from "@/components/workspace/labels";

export default function PipelinePage() {
  return (
    <Suspense fallback={<p className="text-muted">Loading…</p>}>
      <Pipeline />
    </Suspense>
  );
}

function Pipeline() {
  const { slug, can } = useOrgWorkspace();
  const { token, user } = useAuth();
  const sync = useSyncStatus();
  const ownerFilter = useSearchParams().get("owner");
  const [cards, setCards] = useState<PipelineCard[] | null>(null);
  const [pending, setPending] = useState<OutboxEntry[]>([]);
  const [listings, setListings] = useState<OrgListingSummary[]>([]);
  const [view, setView] = useState<"board" | "list">("board");
  const [showLost, setShowLost] = useState(false);
  const [adding, setAdding] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    if (!token) return;
    readThrough(`pipeline:${slug}:${user?.id}`, () => pipeline.board(slug, token)).then((r) => setCards(r.data), () => setCards([]));
    readThrough(`listings:${slug}:${user?.id}`, () => orgListings.list(slug, token)).then((r) => setListings(r.data), () => undefined);
    pendingEntries().then((all) => setPending(all.filter((e) => e.orgSlug === slug && (e.op.kind === "createPipelineCard" || e.op.kind === "movePipelineStage"))), () => undefined);
  }, [slug, token, user?.id, sync.pending, reload]);

  if (!can("LISTING_EDIT")) return <p className="text-muted">Your role doesn&apos;t include the pipeline.</p>;

  const move = async (card: PipelineCard, stage: PipelineStage) => {
    let lostReason: string | undefined;
    if (stage === "LOST") {
      lostReason = window.prompt("Why was this deal lost?")?.trim();
      if (!lostReason) return;
    }
    const sent = await enqueueAllAndWait(slug, [
      { kind: "movePipelineStage", card: { id: card.id }, stage, lostReason, label: `${card.title} → ${STAGE_LABEL[stage]}` },
    ]);
    setNotice(sent ? null : "Saved on this device. If the server can't confirm the move (e.g. no mandate yet), it'll show in Sync.");
    setReload((n) => n + 1);
  };

  const visible = (cards ?? []).filter((c) => (!ownerFilter || String(c.ownerContactId) === ownerFilter) && (showLost || c.stage !== "LOST"));
  const pendingMoves = new Map(
    pending.flatMap((p) => (p.op.kind === "movePipelineStage" && "id" in p.op.card ? [[p.op.card.id, p.op.stage] as const] : []))
  );
  const pendingNew = pending.filter((p) => p.op.kind === "createPipelineCard");
  const stages = STAGES.filter((s) => showLost || s.stage !== "LOST");

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold">Pipeline</h1>
          <p className="text-sm text-muted mt-1">
            Prospect to completed sale. Moving a deal forward is checked against the paperwork: a mandate in force, a live listing, an accepted offer, the conveyancer&apos;s confirmation.
          </p>
        </div>
        <div className="flex gap-2">
          <div role="radiogroup" aria-label="View" className="flex rounded-lg border border-line overflow-hidden">
            <button role="radio" aria-checked={view === "board"} aria-label="Board" onClick={() => setView("board")}
              className={`p-2.5 min-w-11 ${view === "board" ? "bg-primary text-on-primary" : "text-muted"}`}><Columns3 className="w-4 h-4" /></button>
            <button role="radio" aria-checked={view === "list"} aria-label="List" onClick={() => setView("list")}
              className={`p-2.5 min-w-11 ${view === "list" ? "bg-primary text-on-primary" : "text-muted"}`}><List className="w-4 h-4" /></button>
          </div>
          <button className={ws.button} onClick={() => setAdding((a) => !a)}><Plus className="w-4 h-4" /> New deal</button>
        </div>
      </div>

      {adding && <NewDealForm slug={slug} listings={listings} onDone={(sent) => {
        setAdding(false);
        setNotice(sent ? "Deal added." : "Saved on this device. It will sync when you're back online.");
        setReload((n) => n + 1);
      }} />}
      {notice && <p role="status" className="text-sm text-warning">{notice}</p>}
      {ownerFilter && <p className="text-sm">Showing one owner&apos;s deals · <Link className="underline" href={`/orgs/${slug}/pipeline`}>show all</Link></p>}
      <label className="flex items-center gap-2 text-sm min-h-11">
        <input type="checkbox" className="h-5 w-5 accent-[var(--ex-primary)]" checked={showLost} onChange={(e) => setShowLost(e.target.checked)} /> Show lost deals
      </label>

      {pendingNew.length > 0 && (
        <ul className="space-y-2">
          {pendingNew.map((p) => (
            <li key={p.seq} className={`${ws.card} p-3 border-dashed text-sm`}>
              <span className="font-medium">{p.op.kind === "createPipelineCard" ? p.op.payload.title : p.op.label}</span>
              <span className="text-xs text-muted ml-2 inline-flex items-center gap-1"><CloudUpload className="w-3.5 h-3.5" /> {p.lastError ?? "Waiting to sync"}</span>
            </li>
          ))}
        </ul>
      )}

      {!cards ? (
        <p className="text-muted">Loading…</p>
      ) : view === "board" ? (
        <div className="flex gap-3 overflow-x-auto pb-3 -mx-4 px-4 snap-x" aria-label="Pipeline board">
          {stages.map(({ stage, label }) => {
            const inStage = visible.filter((c) => c.stage === stage);
            const value = inStage.reduce((sum, c) => sum + (c.estimatedValueCents ?? 0), 0);
            return (
              <section key={stage} className="snap-start shrink-0 w-[78vw] sm:w-64 rounded-2xl bg-surface border border-line p-2" aria-label={label}>
                <header className="px-1 py-1.5 flex items-baseline justify-between gap-2">
                  <h2 className="text-sm font-semibold">{label}</h2>
                  <span className="text-xs text-muted">{inStage.length}{value > 0 ? ` · ${formatCents(value)}` : ""}</span>
                </header>
                <ul className="space-y-2">
                  {inStage.map((c) => <DealCard key={c.id} card={c} slug={slug} pendingStage={pendingMoves.get(c.id)} onMove={move} />)}
                </ul>
              </section>
            );
          })}
        </div>
      ) : (
        <ul className="space-y-2">
          {visible.map((c) => <DealCard key={c.id} card={c} slug={slug} pendingStage={pendingMoves.get(c.id)} onMove={move} wide />)}
          {visible.length === 0 && <p className="text-muted">No deals yet.</p>}
        </ul>
      )}
    </div>
  );
}

function DealCard({ card: c, slug, pendingStage, onMove, wide }: {
  card: PipelineCard; slug: string; pendingStage?: PipelineStage; onMove: (c: PipelineCard, s: PipelineStage) => void; wide?: boolean;
}) {
  return (
    <li className={`${ws.card} p-3 ${wide ? "" : "text-sm"}`}>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="font-medium leading-snug">{c.title}</p>
          <p className="text-xs text-muted mt-0.5">
            <Link className="underline" href={`/orgs/${slug}/contacts/${c.ownerContactId}`}>{c.ownerName ?? "Owner"}</Link>
            {c.estimatedValueCents ? ` · ${formatCents(c.estimatedValueCents, c.currency)}` : ""}
            {` · ${c.daysInStage}d in stage`}
          </p>
          {c.propertyId && <p className="text-xs mt-0.5"><Link className="underline" href={`/orgs/${slug}/listings/${c.propertyId}`}>{c.propertyTitle ?? "Listing"}</Link></p>}
          {c.nextAction && <p className="text-xs mt-0.5">Next: {c.nextAction}{c.nextActionOn ? ` (${c.nextActionOn})` : ""}</p>}
          {c.stage === "LOST" && c.lostReason && <p className="text-xs text-danger mt-0.5">Lost: {c.lostReason}</p>}
          {wide && <span className={`${ws.chip} mt-1`}>{STAGE_LABEL[c.stage]}</span>}
        </div>
      </div>
      {pendingStage ? (
        <p className="mt-2 text-xs text-warning inline-flex items-center gap-1"><CloudUpload className="w-3.5 h-3.5" /> Moving to {STAGE_LABEL[pendingStage]}…</p>
      ) : c.stage !== "COMPLETED" && (
        <label className="mt-2 block">
          <span className="sr-only">Move {c.title} to</span>
          <select className={`${ws.input} !min-h-9 !py-1.5 text-xs`} value="" onChange={(e) => e.target.value && onMove(c, e.target.value as PipelineStage)}>
            <option value="">Move to…</option>
            {STAGES.filter((s) => s.stage !== c.stage).map((s) => <option key={s.stage} value={s.stage}>{s.label}</option>)}
          </select>
        </label>
      )}
    </li>
  );
}

function NewDealForm({ slug, listings, onDone }: { slug: string; listings: OrgListingSummary[]; onDone: (sent: boolean) => void }) {
  const [owner, setOwner] = useState<PickedContact | null>(null);
  const [form, setForm] = useState({ title: "", value: "", propertyId: "", nextAction: "", nextActionOn: "" });
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!owner) return setError("Choose or add the owner.");
    const value = form.value ? parseToCents(form.value) : null;
    if (form.value && !value) return setError("Enter a valid estimated value.");
    setSaving(true);
    const ops: OutboxOp[] = [];
    if (owner.createOp) ops.push(owner.createOp);
    ops.push({
      kind: "createPipelineCard",
      clientId: newClientId(),
      owner: owner.ref,
      label: `New deal: ${form.title.trim()}`,
      payload: {
        title: form.title.trim(),
        propertyId: form.propertyId ? Number(form.propertyId) : undefined,
        estimatedValueCents: value ?? undefined,
        nextAction: form.nextAction.trim() || undefined,
        nextActionOn: form.nextActionOn || undefined,
      },
    });
    onDone(await enqueueAllAndWait(slug, ops));
  };

  return (
    <form onSubmit={submit} className={`${ws.card} p-4 grid gap-3 sm:grid-cols-2`}>
      <label className="block sm:col-span-2"><span className={ws.label}>Deal</span>
        <input className={ws.input} required maxLength={200} value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder="e.g. Moyo family home, Greendale" /></label>
      <div className="sm:col-span-2"><ContactPicker slug={slug} label="Owner" onPick={setOwner} /></div>
      <label className="block"><span className={ws.label}>Estimated value (USD)</span>
        <input className={ws.input} inputMode="decimal" value={form.value} onChange={(e) => setForm({ ...form, value: e.target.value })} /></label>
      <label className="block"><span className={ws.label}>Listing (if already created)</span>
        <select className={ws.input} value={form.propertyId} onChange={(e) => setForm({ ...form, propertyId: e.target.value })}>
          <option value="">None yet</option>
          {listings.map((l) => <option key={l.propertyId} value={l.propertyId}>{l.title}</option>)}
        </select></label>
      <label className="block"><span className={ws.label}>Next action</span>
        <input className={ws.input} value={form.nextAction} onChange={(e) => setForm({ ...form, nextAction: e.target.value })} placeholder="Book valuation meeting" /></label>
      <label className="block"><span className={ws.label}>By</span>
        <input type="date" className={ws.input} value={form.nextActionOn} onChange={(e) => setForm({ ...form, nextActionOn: e.target.value })} /></label>
      {error && <p className="sm:col-span-2 text-sm text-danger">{error}</p>}
      <button className={`${ws.button} sm:col-span-2`} disabled={saving}>{saving ? "Saving…" : "Add deal"}</button>
    </form>
  );
}
