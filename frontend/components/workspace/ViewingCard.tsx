"use client";

import Link from "next/link";
import { useState } from "react";
import { MessageCircle, Phone } from "lucide-react";
import { enqueueAndWait } from "@/lib/offline/sync";
import type { BuyerInterest, PriceOpinion, Showing, ShowingStatus } from "@/lib/types";
import { ws } from "./OrgWorkspace";

const when = new Intl.DateTimeFormat("en-GB", { weekday: "short", day: "numeric", month: "short", hour: "2-digit", minute: "2-digit", timeZone: "Africa/Harare" });
const STATUS_LABEL: Record<ShowingStatus, string> = { SCHEDULED: "Scheduled", COMPLETED: "Done", NO_SHOW: "No-show", CANCELLED: "Cancelled" };

/** A viewing with its outcome form. Outcomes save offline and sync later. */
export default function ViewingCard({ slug, viewing: v, onSaved, showListing }: { slug: string; viewing: Showing; onSaved: (msg: string) => void; showListing?: boolean }) {
  const [open, setOpen] = useState(false);
  const [interest, setInterest] = useState<BuyerInterest | "">(v.interest ?? "");
  const [price, setPrice] = useState<PriceOpinion | "">(v.priceOpinion ?? "");
  const [feedback, setFeedback] = useState(v.feedback ?? "");
  const digits = v.contactPhone?.replace(/\D/g, "").replace(/^0/, "263");

  const save = async (status: ShowingStatus) => {
    const sent = await enqueueAndWait(slug, {
      kind: "viewingOutcome",
      viewing: { id: v.id },
      status,
      interest: interest || undefined,
      priceOpinion: price || undefined,
      feedback: feedback.trim() || undefined,
      label: `Viewing outcome: ${v.contactName ?? "viewer"}`,
    });
    setOpen(false);
    onSaved(sent ? "Viewing updated." : "Saved on this device. It will sync when you're back online.");
  };

  return (
    <li className={`${ws.card} p-3`}>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="font-medium">{when.format(new Date(v.scheduledAt))} · {v.contactName ?? "Viewer"}</p>
          {showListing && <p className="text-xs"><Link className="underline" href={`/orgs/${slug}/listings/${v.propertyId}/activity`}>{v.propertyTitle}</Link>{v.propertySuburb ? `, ${v.propertySuburb}` : ""}</p>}
          {v.status === "COMPLETED" && (
            <p className="text-xs text-muted mt-0.5">{[v.interest && `${v.interest.toLowerCase()} interest`, v.priceOpinion && v.priceOpinion.replace("_", " ").toLowerCase(), v.feedback].filter(Boolean).join(" · ")}</p>
          )}
        </div>
        <span className={`${ws.chip} ${v.status === "COMPLETED" ? "text-success" : v.status === "NO_SHOW" ? "text-danger" : ""}`}>{STATUS_LABEL[v.status]}</span>
      </div>
      <div className="mt-2 flex flex-wrap gap-2">
        {v.contactPhone && <a className={ws.buttonGhost} href={`tel:${v.contactPhone}`}><Phone className="w-4 h-4" /> Call</a>}
        {digits && <a className={ws.buttonGhost} href={`https://wa.me/${digits}`} target="_blank" rel="noopener noreferrer"><MessageCircle className="w-4 h-4" /> WhatsApp</a>}
        {v.status === "SCHEDULED" && <button className={ws.button} onClick={() => setOpen((o) => !o)}>Record outcome</button>}
      </div>
      {open && (
        <div className="mt-3 space-y-3 border-t border-line pt-3">
          <fieldset><legend className={ws.label}>Interest</legend>
            <div className="flex gap-2">{(["HOT", "WARM", "COLD"] as BuyerInterest[]).map((i) => (
              <button key={i} type="button" aria-pressed={interest === i} onClick={() => setInterest(i)}
                className={`${ws.chip} min-h-10 px-3 ${interest === i ? "bg-primary text-on-primary border-transparent" : ""}`}>{i.charAt(0) + i.slice(1).toLowerCase()}</button>
            ))}</div></fieldset>
          <fieldset><legend className={ws.label}>Price feels</legend>
            <div className="flex flex-wrap gap-2">{([["TOO_HIGH", "Too high"], ["FAIR", "Fair"], ["GOOD_VALUE", "Good value"]] as [PriceOpinion, string][]).map(([p, l]) => (
              <button key={p} type="button" aria-pressed={price === p} onClick={() => setPrice(p)}
                className={`${ws.chip} min-h-10 px-3 ${price === p ? "bg-primary text-on-primary border-transparent" : ""}`}>{l}</button>
            ))}</div></fieldset>
          <label className="block"><span className={ws.label}>What they said</span>
            <textarea className={`${ws.input} min-h-16`} maxLength={2000} value={feedback} onChange={(e) => setFeedback(e.target.value)} /></label>
          <div className="flex flex-wrap gap-2">
            <button className={ws.button} onClick={() => save("COMPLETED")}>Viewing done</button>
            <button className={ws.buttonGhost} onClick={() => save("NO_SHOW")}>No-show</button>
            <button className={ws.buttonGhost} onClick={() => save("CANCELLED")}>Cancelled</button>
          </div>
        </div>
      )}
    </li>
  );
}
