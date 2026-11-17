"use client";

import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { Camera, CloudUpload } from "lucide-react";
import { intake } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { formatCents, parseToCents } from "@/lib/money";
import { compressForUpload } from "@/lib/offline/compress";
import { enqueueAndWait, newClientId, pendingEntries, readThrough, useSyncStatus } from "@/lib/offline/sync";
import type { OutboxEntry } from "@/lib/offline/types";
import type { Inspection, InspectionCondition, InspectionItem, PricingSuggestion } from "@/lib/types";
import { useOrgWorkspace, ws } from "@/components/workspace/OrgWorkspace";

const CONDITIONS: { value: InspectionCondition; label: string; tone: string }[] = [
  { value: "GOOD", label: "Good", tone: "text-success" },
  { value: "FAIR", label: "Fair", tone: "text-warning" },
  { value: "POOR", label: "Poor", tone: "text-danger" },
  { value: "NOT_APPLICABLE", label: "N/A", tone: "text-muted" },
];

export default function IntakePage() {
  const { id } = useParams<{ id: string }>();
  const propertyId = Number(id);
  const { slug, can } = useOrgWorkspace();
  const { token, user } = useAuth();
  const sync = useSyncStatus();
  const [inspection, setInspection] = useState<Inspection | null>(null);
  const [items, setItems] = useState<InspectionItem[]>([]);
  const [sizes, setSizes] = useState({ stand: "", floor: "", notes: "" });
  const [pendingPhotos, setPendingPhotos] = useState<OutboxEntry[]>([]);
  const [message, setMessage] = useState<string | null>(null);
  const [reload, setReload] = useState(0);
  const editable = can("LISTING_EDIT");

  useEffect(() => {
    if (!token) return;
    readThrough(`inspection:${slug}:${propertyId}:${user?.id}`, () => intake.inspection(slug, propertyId, token)).then((r) => {
      setInspection(r.data);
      setItems(r.data.items);
      setSizes({ stand: r.data.standSizeM2?.toString() ?? "", floor: r.data.floorAreaM2?.toString() ?? "", notes: r.data.notes ?? "" });
    }, () => undefined);
    pendingEntries().then((all) => setPendingPhotos(all.filter((e) => e.op.kind === "uploadInspectionPhoto")), () => undefined);
  }, [slug, propertyId, token, user?.id, reload]);

  const setItem = (key: string, patch: Partial<InspectionItem>) => setItems((list) => list.map((i) => (i.key === key ? { ...i, ...patch } : i)));

  const save = async () => {
    const sent = await enqueueAndWait(slug, {
      kind: "saveInspection",
      listing: { id: propertyId },
      baseVersion: inspection?.version,
      items,
      standSizeM2: sizes.stand ? Number(sizes.stand) : undefined,
      floorAreaM2: sizes.floor ? Number(sizes.floor) : undefined,
      notes: sizes.notes || undefined,
      label: "Inspection",
    });
    setMessage(sent ? "Inspection saved." : "Saved on this device. It will sync when you're back online.");
    setReload((n) => n + 1);
  };

  const addPhoto = async (file: File, itemKey?: string) => {
    const { blob, name } = await compressForUpload(file);
    const sent = await enqueueAndWait(slug, {
      kind: "uploadInspectionPhoto",
      clientId: newClientId(),
      listing: { id: propertyId },
      file: blob,
      fileName: name,
      itemKey,
      label: `Inspection photo${itemKey ? ` (${itemKey.toLowerCase()})` : ""}`,
    });
    setMessage(sent ? "Photo uploaded." : "Photo saved on this device. It will upload when you're back online.");
    setReload((n) => n + 1);
  };

  return (
    <div className="max-w-3xl space-y-6">
      {message && <p role="status" className="text-sm text-success">{message}</p>}

      <section className={`${ws.card} p-4 sm:p-5`} aria-labelledby="insp-h">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 id="insp-h" className="font-semibold">Inspection</h2>
          {inspection?.inspectedOn && <span className="text-xs text-muted">Last saved {inspection.inspectedOn} · {inspection.ratedItems} rated · {inspection.poorItems} poor</span>}
        </div>
        <p className="text-xs text-muted mt-1">Works offline. Photos here are internal evidence and are never shown publicly.</p>
        {!inspection ? <p className="text-muted mt-3">Loading…</p> : (
          <>
            <ul className="mt-3 divide-y divide-[var(--ex-line)]">
              {items.map((i) => (
                <li key={i.key} className="py-3">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <span className="text-sm font-medium">{i.label ?? i.key}</span>
                    <div className="flex gap-1" role="radiogroup" aria-label={`${i.label} condition`}>
                      {CONDITIONS.map((c) => (
                        <button key={c.value} type="button" role="radio" aria-checked={i.condition === c.value} disabled={!editable}
                          onClick={() => setItem(i.key, { condition: c.value })}
                          className={`${ws.chip} min-h-9 px-2.5 ${i.condition === c.value ? "bg-primary text-on-primary border-transparent" : c.tone}`}>
                          {c.label}
                        </button>
                      ))}
                    </div>
                  </div>
                  {(i.condition === "POOR" || i.condition === "FAIR" || i.note) && (
                    <input className={`${ws.input} mt-2`} placeholder="What's wrong?" value={i.note ?? ""} disabled={!editable}
                      onChange={(e) => setItem(i.key, { note: e.target.value })} aria-label={`${i.label} note`} />
                  )}
                </li>
              ))}
            </ul>
            <div className="mt-3 grid gap-3 sm:grid-cols-2">
              <label className="block"><span className={ws.label}>Stand size (m²)</span>
                <input className={ws.input} type="number" min={0} value={sizes.stand} disabled={!editable} onChange={(e) => setSizes({ ...sizes, stand: e.target.value })} /></label>
              <label className="block"><span className={ws.label}>Floor area (m²)</span>
                <input className={ws.input} type="number" min={0} value={sizes.floor} disabled={!editable} onChange={(e) => setSizes({ ...sizes, floor: e.target.value })} /></label>
              <label className="block sm:col-span-2"><span className={ws.label}>Notes</span>
                <textarea className={`${ws.input} min-h-20`} value={sizes.notes} disabled={!editable} onChange={(e) => setSizes({ ...sizes, notes: e.target.value })} /></label>
            </div>
            {editable && <button className={`${ws.button} mt-3`} onClick={save}>Save inspection</button>}
          </>
        )}
      </section>

      <section className={`${ws.card} p-4 sm:p-5`} aria-labelledby="photos-h">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 id="photos-h" className="font-semibold">Inspection photos</h2>
          {editable && (
            <label className={`${ws.button} cursor-pointer`}>
              <Camera className="w-4 h-4" /> Add photo
              <input type="file" accept="image/*" capture="environment" className="sr-only"
                onChange={(e) => { const f = e.target.files?.[0]; if (f) void addPhoto(f); e.target.value = ""; }} />
            </label>
          )}
        </div>
        {pendingPhotos.length > 0 && (
          <p className="mt-2 text-xs text-warning inline-flex items-center gap-1"><CloudUpload className="w-3.5 h-3.5" /> {pendingPhotos.length} photo(s) waiting to upload</p>
        )}
        <ul className="mt-3 grid grid-cols-3 sm:grid-cols-4 gap-2">
          {inspection?.photos.map((p) => <PhotoThumb key={p.id} slug={slug} photoId={p.id} caption={p.itemKey ?? p.caption} />)}
        </ul>
        {inspection && inspection.photos.length === 0 && pendingPhotos.length === 0 && <p className="text-sm text-muted mt-2">No photos yet.</p>}
      </section>

      <PricingSection propertyId={propertyId} online={sync.online} editable={editable} />
    </div>
  );
}

function PhotoThumb({ slug, photoId, caption }: { slug: string; photoId: number; caption?: string }) {
  const { token } = useAuth();
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    if (!token) return;
    let revoke: string | null = null;
    intake.photoUrl(slug, photoId, token).then((u) => { revoke = u; setUrl(u); }, () => setUrl(null));
    return () => { if (revoke) URL.revokeObjectURL(revoke); };
  }, [slug, photoId, token]);
  return (
    <li className="aspect-square rounded-lg overflow-hidden bg-surface border border-line relative">
      {/* eslint-disable-next-line @next/next/no-img-element -- authenticated blob URL */}
      {url && <img src={url} alt={caption ? `Inspection: ${caption}` : "Inspection photo"} className="w-full h-full object-cover" />}
      {caption && <span className="absolute bottom-0 inset-x-0 bg-black/50 text-white text-[10px] px-1 truncate">{caption}</span>}
    </li>
  );
}

function PricingSection({ propertyId, online, editable }: { propertyId: number; online: boolean; editable: boolean }) {
  const { slug } = useOrgWorkspace();
  const { token } = useAuth();
  const [data, setData] = useState<PricingSuggestion | null>(null);
  const [price, setPrice] = useState("");
  const [rationale, setRationale] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    if (!token || !online) return;
    intake.pricing(slug, propertyId, token).then((d) => {
      setData(d);
      setPrice(((d.latest?.recommendedCents ?? d.suggestedCents ?? d.askingCents) / 100).toString());
    }, (e: unknown) => setError(e instanceof Error ? e.message : "Could not load pricing"));
  }, [slug, propertyId, token, online, reload]);

  const save = async () => {
    const cents = parseToCents(price);
    if (!cents || !token) return setError("Enter a valid price");
    try {
      await intake.saveRecommendation(slug, propertyId, cents, rationale.trim() || undefined, token);
      setRationale("");
      setReload((n) => n + 1);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not save");
    }
  };

  return (
    <section className={`${ws.card} p-4 sm:p-5`} aria-labelledby="price-h">
      <h2 id="price-h" className="font-semibold">Pricing recommendation</h2>
      {!online ? <p className="text-sm text-muted mt-2">Needs a connection: comparables are worked out on the server.</p> : !data ? <p className="text-sm text-muted mt-2">{error ?? "Loading…"}</p> : (
        <>
          <p className="text-xs text-muted mt-1">{data.basis}</p>
          <dl className="mt-3 grid grid-cols-2 sm:grid-cols-4 gap-2 text-center">
            {[
              ["Asking", data.askingCents],
              ["Low (25%)", data.lowCents],
              ["Median", data.medianCents],
              ["High (75%)", data.highCents],
            ].map(([label, v]) => (
              <div key={label as string} className="rounded-xl border border-line p-2">
                <dt className="text-xs text-muted">{label}</dt>
                <dd className="font-semibold">{formatCents(v as number | undefined, data.currency)}</dd>
              </div>
            ))}
          </dl>
          {data.suggestedCents && <p className="mt-3 text-sm">Suggested from comparables: <strong>{formatCents(data.suggestedCents, data.currency)}</strong></p>}
          {data.latest && (
            <p className="mt-1 text-sm">Saved recommendation: <strong>{formatCents(data.latest.recommendedCents, data.currency)}</strong>{data.latest.rationale ? ` · ${data.latest.rationale}` : ""}</p>
          )}
          {data.comparables.length > 0 && (
            <details className="mt-3">
              <summary className="text-sm cursor-pointer">Comparables ({data.comparables.length})</summary>
              <ul className="mt-2 space-y-1 text-sm">
                {data.comparables.map((c) => (
                  <li key={c.propertyId} className="flex justify-between gap-2 border-b border-line py-1">
                    <span className="truncate">{c.title} · {c.suburb} · {c.bedrooms} bed{c.titleType === "CESSION" ? " · cession" : ""}</span>
                    <span className="shrink-0">{formatCents(c.priceCents, c.currency)}</span>
                  </li>
                ))}
              </ul>
            </details>
          )}
          {editable && (
            <div className="mt-4 grid gap-2 sm:grid-cols-[10rem,1fr,auto] items-end">
              <label className="block"><span className={ws.label}>Recommend (USD)</span>
                <input className={ws.input} inputMode="decimal" value={price} onChange={(e) => setPrice(e.target.value)} /></label>
              <label className="block"><span className={ws.label}>Why</span>
                <input className={ws.input} value={rationale} onChange={(e) => setRationale(e.target.value)} placeholder="e.g. borehole and solar justify upper range" /></label>
              <button className={ws.button} onClick={save}>Save</button>
            </div>
          )}
          {error && <p className="mt-2 text-sm text-danger">{error}</p>}
        </>
      )}
    </section>
  );
}
