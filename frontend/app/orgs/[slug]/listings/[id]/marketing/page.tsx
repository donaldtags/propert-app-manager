"use client";

import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { Copy, Download, FileText, Mail, MessageCircle, Rocket } from "lucide-react";
import { campaigns, orgListings, sellerReports } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { formatCents } from "@/lib/money";
import { formatDate } from "@/lib/dates";
import { enqueueAndWait, readThrough, useSyncStatus } from "@/lib/offline/sync";
import type { Campaign, OrgListingDetail, SellerReportSummary } from "@/lib/types";
import { useOrgWorkspace, ws } from "@/components/workspace/OrgWorkspace";

const CHANNEL_LABEL: Record<string, string> = {
  PORTAL: "Portals", WHATSAPP: "WhatsApp", INSTAGRAM: "Instagram", FACEBOOK: "Facebook", EMAIL: "Email",
  NETWORK: "Calls & network", SIGNBOARD: "Signboard", OPEN_HOUSE: "Open house", REPORTING: "Owner report",
  PHONE: "Call", WALK_IN: "Walk-in", WEBSITE: "Website", REFERRAL: "Referral", OTHER: "Other",
};
const ADVICE_LABEL = { REVIEW_DOWN: "Price review", REFRESH: "Refresh", ABOVE_MARKET: "Above market", HOLD: "Hold" } as const;

export default function MarketingPage() {
  const { id } = useParams<{ id: string }>();
  const propertyId = Number(id);
  const { slug, context, can } = useOrgWorkspace();
  const { token, user } = useAuth();
  const sync = useSyncStatus();
  const org = context.organisation;
  const [detail, setDetail] = useState<OrgListingDetail | null>(null);
  const [campaign, setCampaign] = useState<Campaign | null | undefined>(undefined);
  const [reports, setReports] = useState<SellerReportSummary[]>([]);
  const [imageUrl, setImageUrl] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [reload, setReload] = useState(0);
  const editable = can("LISTING_EDIT");

  useEffect(() => {
    if (!token) return;
    readThrough(`listing:${slug}:${propertyId}:${user?.id}`, () => orgListings.get(slug, propertyId, token)).then((r) => setDetail(r.data), () => undefined);
    readThrough(`campaign:${slug}:${propertyId}:${user?.id}`, async () => (await campaigns.current(slug, propertyId, token)) ?? null)
      .then((r) => setCampaign(r.data), () => setCampaign(null));
    if (editable) sellerReports.list(slug, propertyId, token).then(setReports, () => undefined);
  }, [slug, propertyId, token, user?.id, editable, sync.pending, reload]);

  useEffect(() => () => { if (imageUrl) URL.revokeObjectURL(imageUrl); }, [imageUrl]);

  if (!detail) return <p className="text-muted">Loading…</p>;
  const p = detail.property;
  const live = p.status === "AVAILABLE" || p.status === "UNDER_OFFER";
  const listingUrl = typeof window !== "undefined" ? `${window.location.origin}/properties/${p.id}` : `/properties/${p.id}`;
  const price = formatCents(Math.round(Number(p.price) * 100), p.currency) + (p.listingType === "RENT" ? "/month" : "");
  const pitch = `${p.title} · ${p.bedrooms} bed, ${p.bathrooms} bath · ${p.suburb}, ${p.city} · ${price}`;
  const enquiryLink = org.whatsappNumber ? `https://wa.me/${org.whatsappNumber}?text=${encodeURIComponent(`Hi ${org.name}, I'm interested in ${p.title} (${p.suburb}). ${listingUrl}`)}` : null;
  const shareToBuyer = `https://wa.me/?text=${encodeURIComponent(`${pitch}\n${listingUrl}${enquiryLink ? `\nWhatsApp us: wa.me/${org.whatsappNumber}` : ""}`)}`;
  const activeMandate = detail.mandates.find((m) => m.status === "ACTIVE");
  const ownerDigits = activeMandate?.ownerPhone?.replace(/\D/g, "").replace(/^0/, "263");

  const startCampaign = async () => {
    if (!token) return;
    setBusy(true);
    setError(null);
    try {
      setCampaign(await campaigns.start(slug, propertyId, token));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not start");
    } finally {
      setBusy(false);
    }
  };

  const toggle = async (taskId: number, done: boolean) => {
    if (campaign) setCampaign({ ...campaign, tasks: campaign.tasks.map((t) => (t.id === taskId ? { ...t, done } : t)), tasksDone: campaign.tasksDone + (done ? 1 : -1) });
    const sent = await enqueueAndWait(slug, { kind: "setCampaignTask", taskId, done, label: `Campaign task ${done ? "done" : "reopened"}` });
    if (!sent) setMessage("Saved on this device. It will sync when you're back online.");
    setReload((n) => n + 1);
  };

  const generateReport = async () => {
    if (!token) return;
    setBusy(true);
    setError(null);
    try {
      const r = await sellerReports.generate(slug, propertyId, token);
      setReports((list) => [r, ...list]);
      setMessage("Report ready. Send it to the owner below.");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not create the report");
    } finally {
      setBusy(false);
    }
  };

  const loadImage = async () => {
    if (!token) return;
    try {
      setImageUrl(await sellerReports.shareImageUrl(slug, propertyId, token));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not create the image");
    }
  };

  const copy = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setMessage("Copied.");
    } catch {
      setMessage(text);
    }
  };

  const byDay = new Map<number, Campaign["tasks"]>();
  campaign?.tasks.forEach((t) => byDay.set(t.day, [...(byDay.get(t.day) ?? []), t]));

  return (
    <div className="max-w-3xl space-y-6">
      {message && <p role="status" className="text-sm text-success break-all">{message}</p>}
      {error && <p role="alert" className="text-sm text-danger">{error}</p>}

      <section className={`${ws.card} p-4 sm:p-5`} aria-labelledby="share-h">
        <h2 id="share-h" className="font-semibold">Share</h2>
        {!live && <p className="text-xs text-warning mt-1">Not live yet. Links will show “not found” to the public until it&apos;s published.</p>}
        <div className="mt-3 flex flex-wrap gap-2">
          <a className={ws.button} href={shareToBuyer} target="_blank" rel="noopener noreferrer"><MessageCircle className="w-4 h-4" /> Send to a buyer on WhatsApp</a>
          {enquiryLink && <button className={ws.buttonGhost} onClick={() => copy(enquiryLink)}><Copy className="w-4 h-4" /> Copy “WhatsApp us” link</button>}
          <button className={ws.buttonGhost} onClick={() => copy(listingUrl)}><Copy className="w-4 h-4" /> Copy listing link</button>
        </div>
        <div className="mt-4">
          <p className="text-sm font-medium">Instagram square</p>
          <p className="text-xs text-muted">1080 × 1080 with photo, price, beds, baths, suburb and the {org.name} name. Uses the listing photo only if the owner consented to photos.</p>
          {!imageUrl ? (
            <button className={`${ws.buttonGhost} mt-2`} disabled={!sync.online} onClick={loadImage}>Create image</button>
          ) : (
            <div className="mt-2 space-y-2">
              {/* eslint-disable-next-line @next/next/no-img-element -- generated blob */}
              <img src={imageUrl} alt={`Instagram image for ${p.title}`} className="w-full max-w-xs rounded-xl border border-line" />
              <a className={ws.buttonGhost} href={imageUrl} download={`${p.title.replace(/[^a-z0-9]+/gi, "-").toLowerCase()}-instagram.png`}><Download className="w-4 h-4" /> Download</a>
            </div>
          )}
        </div>
      </section>

      <section className={`${ws.card} p-4 sm:p-5`} aria-labelledby="camp-h">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 id="camp-h" className="font-semibold">21-day campaign</h2>
          {campaign && <span className="text-sm text-muted">Day {campaign.today} of 21 · {campaign.tasksDone}/{campaign.tasksTotal} done</span>}
        </div>
        {campaign === undefined ? <p className="text-muted mt-2">Loading…</p> : !campaign ? (
          <div className="mt-2">
            <p className="text-sm text-muted">Start once the listing is live. Today becomes day 1.</p>
            {editable && <button className={`${ws.button} mt-2`} disabled={!live || busy || !sync.online} onClick={startCampaign}><Rocket className="w-4 h-4" /> Start campaign</button>}
          </div>
        ) : (
          <>
            <div className="mt-3">
              <p className="text-sm font-medium">Enquiries since launch: {campaign.enquiriesTotal} · viewings: {campaign.viewingsTotal}</p>
              <ul className="mt-2 flex flex-wrap gap-2">
                {Object.entries(campaign.enquiriesByChannel).map(([ch, n]) => <li key={ch} className={ws.chip}>{CHANNEL_LABEL[ch] ?? ch}: {n}</li>)}
                {Object.keys(campaign.enquiriesByChannel).length === 0 && <li className="text-xs text-muted">No enquiries on this listing yet. Tag enquiries to the listing to count them here.</li>}
              </ul>
            </div>
            <ol className="mt-4 space-y-3">
              {[...byDay.entries()].map(([day, tasks]) => (
                <li key={day} className={day === campaign.today ? "rounded-xl border border-accent p-2 -mx-2" : ""}>
                  <p className="text-xs font-semibold text-muted">Day {day} · {formatDate(tasks[0].dueOn)}{day === campaign.today ? " · today" : ""}</p>
                  <ul className="mt-1 space-y-1">
                    {tasks.map((t) => (
                      <li key={t.id}>
                        <label className="flex items-start gap-3 min-h-11 text-sm py-1">
                          <input type="checkbox" className="mt-0.5 h-5 w-5 shrink-0 accent-[var(--ex-primary)]" checked={t.done} disabled={!editable}
                            onChange={(e) => toggle(t.id, e.target.checked)} />
                          <span className={t.done ? "line-through text-muted" : t.overdue ? "text-danger" : ""}>
                            {t.title} <span className="text-xs text-muted">· {CHANNEL_LABEL[t.channel] ?? t.channel}{t.overdue && !t.done ? " · overdue" : ""}</span>
                          </span>
                        </label>
                      </li>
                    ))}
                  </ul>
                </li>
              ))}
            </ol>
          </>
        )}
      </section>

      {editable && (
        <section className={`${ws.card} p-4 sm:p-5`} aria-labelledby="rep-h">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 id="rep-h" className="font-semibold">Weekly seller report</h2>
            <button className={ws.button} disabled={busy || !sync.online} onClick={generateReport}><FileText className="w-4 h-4" /> Create this week&apos;s report</button>
          </div>
          <p className="text-xs text-muted mt-1">PDF in {org.name} branding: enquiries by channel, viewings and feedback, offers, campaign progress and price advice. The owner opens it from a private link that expires after 30 days.</p>
          <ul className="mt-3 space-y-2">
            {reports.map((r) => {
              const url = sellerReports.shareUrl(r.shareToken);
              const text = `Hi ${activeMandate?.ownerName ?? ""}, here's this week's update on ${p.title} from ${org.name}: ${url}`;
              return (
                <li key={r.id} className="rounded-xl border border-line p-3">
                  <p className="text-sm font-medium">Week ending {formatDate(r.weekEnding)} · <span className={ws.chip}>{ADVICE_LABEL[r.adviceKind]}</span></p>
                  <p className="text-xs text-muted mt-1">{r.adviceText}</p>
                  <div className="mt-2 flex flex-wrap gap-2">
                    <a className={ws.buttonGhost} href={url} target="_blank" rel="noopener noreferrer"><FileText className="w-4 h-4" /> Open PDF</a>
                    <a className={ws.buttonGhost} href={`https://wa.me/${ownerDigits ?? ""}?text=${encodeURIComponent(text)}`} target="_blank" rel="noopener noreferrer">
                      <MessageCircle className="w-4 h-4" /> WhatsApp owner
                    </a>
                    <a className={ws.buttonGhost} href={`mailto:${activeMandate?.ownerEmail ?? ""}?subject=${encodeURIComponent(`${p.title}: weekly update`)}&body=${encodeURIComponent(text)}`}>
                      <Mail className="w-4 h-4" /> Email owner
                    </a>
                  </div>
                </li>
              );
            })}
            {reports.length === 0 && <li className="text-sm text-muted">No reports yet.</li>}
          </ul>
        </section>
      )}
    </div>
  );
}
