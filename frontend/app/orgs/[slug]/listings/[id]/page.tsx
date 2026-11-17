"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { ArrowLeft, CheckCircle2, CircleDashed, ExternalLink, FileText, ShieldCheck, Upload } from "lucide-react";
import { orgListings } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { addDays, formatDate, harareToday } from "@/lib/dates";
import { formatCents, parseToCents } from "@/lib/money";
import type { Mandate, MandateType, OrgListingDetail, TitleType, TitleVerificationStatus } from "@/lib/types";
import CessionBadge from "@/components/CessionBadge";
import { useOrgWorkspace, ws } from "@/components/workspace/OrgWorkspace";
import {
  MANDATE_STATUS_LABEL,
  STATUS_LABEL,
  TITLE_TYPES,
  TITLE_VERIFICATION_LABEL,
} from "@/components/workspace/listingLabels";

type Run = (label: string, action: () => Promise<OrgListingDetail | unknown>) => Promise<boolean>;

export default function OrgListingDetailPage() {
  const { id } = useParams<{ id: string }>();
  const propertyId = Number(id);
  const { slug, can } = useOrgWorkspace();
  const { token } = useAuth();
  const [detail, setDetail] = useState<OrgListingDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    if (!token) return;
    orgListings.get(slug, propertyId, token).then(setDetail, (e: unknown) =>
      setError(e instanceof Error ? e.message : "Could not load the listing")
    );
  }, [slug, propertyId, token]);

  /** Runs an action, then refreshes from the server so the checklist always reflects backend truth. */
  const run: Run = async (label, action) => {
    if (!token) return false;
    setBusy(label);
    setError(null);
    setNotice(null);
    try {
      await action();
      setDetail(await orgListings.get(slug, propertyId, token));
      return true;
    } catch (e) {
      setError(e instanceof Error ? e.message : "Action failed");
      return false;
    } finally {
      setBusy(null);
    }
  };

  if (!detail) {
    return error ? <p className="text-danger">{error}</p> : <p className="text-muted">Loading…</p>;
  }

  const { property: p, compliance: c, mandates } = detail;
  const live = p.status === "AVAILABLE";
  const approved = c.checks.find((x) => x.key === "PREA_APPROVAL")?.met ?? false;

  return (
    <div className="space-y-6 max-w-3xl">
      <Link href={`/orgs/${slug}/listings`} className="inline-flex items-center gap-1 text-sm text-muted hover:text-ink">
        <ArrowLeft className="w-4 h-4" /> Listings
      </Link>

      <header className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h1 className="text-xl sm:text-2xl font-semibold">{p.title}</h1>
          <p className="text-sm text-muted mt-1">
            {p.suburb}, {p.city} · {p.bedrooms} bed · {p.bathrooms} bath ·{" "}
            {formatCents(Math.round(Number(p.price) * 100), p.currency)}
            {p.listingType === "RENT" && " / month"}
          </p>
          {c.negotiatorName && <p className="text-xs text-muted mt-1">Negotiator: {c.negotiatorName}</p>}
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {p.riskFlags?.includes("CESSION") && <CessionBadge size="md" />}
          <span className={`${ws.chip} ${live ? "text-success" : ""}`}>{STATUS_LABEL[p.status] ?? p.status}</span>
          {live && (
            <Link href={`/properties/${p.id}`} className={`${ws.chip} gap-1 hover:text-ink`} target="_blank">
              Public page <ExternalLink className="w-3 h-3" />
            </Link>
          )}
        </div>
      </header>

      {error && (
        <p role="alert" className="rounded-xl border border-danger p-3 text-sm text-danger">
          {error}
        </p>
      )}
      {notice && (
        <p role="status" className="text-sm text-success">
          {notice}
        </p>
      )}

      {/* Publication checklist */}
      <section className={`${ws.card} p-4 sm:p-5`} aria-labelledby="checklist">
        <h2 id="checklist" className="font-semibold">
          Ready to publish?
        </h2>
        <ol className="mt-3 space-y-3">
          {c.checks.map((check) => (
            <li key={check.key} className="flex gap-3">
              {check.met ? (
                <CheckCircle2 className="w-5 h-5 text-success shrink-0" aria-label="Met" />
              ) : (
                <CircleDashed className="w-5 h-5 text-warning shrink-0" aria-label="Not met" />
              )}
              <div className="min-w-0">
                <p className="text-sm font-medium">{check.label}</p>
                <p className="text-xs text-muted">{check.detail}</p>
              </div>
            </li>
          ))}
        </ol>
        <div className="mt-4 flex flex-wrap gap-2">
          {can("LISTING_APPROVE_PUBLISH") && !approved && (
            <button
              className={ws.button}
              disabled={!c.readyForApproval || !!busy}
              title={c.readyForApproval ? undefined : "Every other check must be met first"}
              onClick={() => run("approve", () => orgListings.approve(slug, p.id, token!))}
            >
              <ShieldCheck className="w-4 h-4" /> Approve as PREA
            </button>
          )}
          {can("LISTING_EDIT") &&
            (live ? (
              <button
                className={ws.buttonGhost}
                disabled={!!busy}
                onClick={() => run("unpublish", () => orgListings.unpublish(slug, p.id, undefined, token!))}
              >
                Unpublish
              </button>
            ) : (
              <button
                className={ws.button}
                disabled={!c.publishable || !!busy}
                onClick={async () => {
                  if (await run("publish", () => orgListings.publish(slug, p.id, token!))) setNotice("Published. The listing is live.");
                }}
              >
                Publish
              </button>
            ))}
        </div>
        {!can("LISTING_APPROVE_PUBLISH") && !approved && c.readyForApproval && (
          <p className="mt-3 text-xs text-muted">Waiting for the Principal Registered Estate Agent to approve.</p>
        )}
      </section>

      <TitleSection detail={detail} run={run} busy={!!busy} />
      <MandateSection detail={detail} mandates={mandates} run={run} busy={!!busy} />
      <ConsentSection detail={detail} run={run} busy={!!busy} />
    </div>
  );
}

function TitleSection({ detail, run, busy }: { detail: OrgListingDetail; run: Run; busy: boolean }) {
  const { slug, can } = useOrgWorkspace();
  const { token } = useAuth();
  const { property: p, compliance: c } = detail;
  const [titleType, setTitleType] = useState<TitleType | "">(p.titleType ?? "");
  const [reference, setReference] = useState(c.titleReference ?? "");
  const [notes, setNotes] = useState("");
  const editable = can("LISTING_EDIT");

  const save = (e: FormEvent) => {
    e.preventDefault();
    if (!titleType) return;
    void run("title", () => orgListings.setTitle(slug, p.id, { titleType, titleReference: reference.trim() || undefined }, token!));
  };
  const verify = (status: TitleVerificationStatus) =>
    run("verify", () => orgListings.verifyTitle(slug, p.id, { status, notes: notes.trim() || undefined }, token!));

  return (
    <section className={`${ws.card} p-4 sm:p-5`} aria-labelledby="title-h">
      <h2 id="title-h" className="font-semibold">
        Title and ownership
      </h2>
      <p className="text-xs text-muted mt-1">
        Changing the title type after approval withdraws the PREA&apos;s approval and takes a live listing down.
      </p>
      <form onSubmit={save} className="mt-3 grid gap-3 sm:grid-cols-[1fr,1fr,auto] items-end">
        <label className="block">
          <span className={ws.label}>Title type</span>
          <select className={ws.input} value={titleType} disabled={!editable || busy} onChange={(e) => setTitleType(e.target.value as TitleType)}>
            <option value="" disabled>
              Choose…
            </option>
            {TITLE_TYPES.map((t) => (
              <option key={t.value} value={t.value}>
                {t.label}
              </option>
            ))}
          </select>
        </label>
        <label className="block">
          <span className={ws.label}>Deed / title / cession no.</span>
          <input className={ws.input} value={reference} disabled={!editable || busy} onChange={(e) => setReference(e.target.value)} />
        </label>
        {editable && (
          <button className={ws.buttonGhost} disabled={busy || !titleType}>
            Save
          </button>
        )}
      </form>
      {titleType === "CESSION" && (
        <p className="mt-2 text-xs text-warning">Cession listings show a risk badge to buyers on every page.</p>
      )}

      <div className="mt-4 border-t border-line pt-4">
        <p className="text-sm">
          Verification: <strong>{TITLE_VERIFICATION_LABEL[c.titleVerificationStatus]}</strong>
          {c.titleVerifiedByName && (
            <span className="text-muted">
              {" "}
              by {c.titleVerifiedByName}, {formatDate(c.titleVerifiedAt)}
            </span>
          )}
        </p>
        {c.titleVerificationNotes && <p className="text-xs text-muted mt-1">{c.titleVerificationNotes}</p>}
        {can("TITLE_VERIFY") && (
          <div className="mt-3 space-y-2">
            <input className={ws.input} placeholder="Notes (e.g. Deeds Office search done 12 Nov)" value={notes} onChange={(e) => setNotes(e.target.value)} />
            <div className="flex flex-wrap gap-2">
              <button className={ws.button} disabled={busy || !p.titleType || !c.titleReference} onClick={() => verify("VERIFIED")}>
                Mark verified
              </button>
              <button className={ws.buttonGhost} disabled={busy} onClick={() => verify("IN_PROGRESS")}>
                In progress
              </button>
              <button className={ws.buttonGhost} disabled={busy} onClick={() => verify("FAILED")}>
                Failed
              </button>
            </div>
            {(!p.titleType || !c.titleReference) && (
              <p className="text-xs text-muted">Save the title type and reference number before verifying.</p>
            )}
          </div>
        )}
      </div>
    </section>
  );
}

function MandateSection({ detail, mandates, run, busy }: { detail: OrgListingDetail; mandates: Mandate[]; run: Run; busy: boolean }) {
  const { slug, can } = useOrgWorkspace();
  const { token } = useAuth();
  const open = mandates.some((m) => ["DRAFT", "AWAITING_SIGN_OFF", "ACTIVE"].includes(m.status));
  const [showForm, setShowForm] = useState(false);

  return (
    <section className={`${ws.card} p-4 sm:p-5`} aria-labelledby="mandate-h">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="mandate-h" className="font-semibold">
          Mandate
        </h2>
        {can("LISTING_EDIT") && !showForm && (
          <button className={ws.buttonGhost} disabled={busy} onClick={() => setShowForm(true)}>
            {open ? "New mandate (renewal)" : "Add mandate"}
          </button>
        )}
      </div>
      <p className="text-xs text-muted mt-1">
        The owner signs, you attach the signed copy, then the PREA signs it off. The listing comes down automatically
        the morning after the mandate expires.
      </p>

      {showForm && (
        <MandateForm
          detail={detail}
          busy={busy}
          onCancel={() => setShowForm(false)}
          onSubmit={async (data) => {
            if (await run("mandate", () => orgListings.createMandate(slug, detail.property.id, data, token!))) setShowForm(false);
          }}
        />
      )}

      {mandates.length === 0 ? (
        <p className="mt-4 text-sm text-muted">No mandate yet.</p>
      ) : (
        <ul className="mt-4 space-y-3">
          {mandates.map((m) => (
            <MandateRow key={m.id} mandate={m} run={run} busy={busy} />
          ))}
        </ul>
      )}
    </section>
  );
}

function MandateRow({ mandate: m, run, busy }: { mandate: Mandate; run: Run; busy: boolean }) {
  const { slug, can } = useOrgWorkspace();
  const { token } = useAuth();
  const [file, setFile] = useState<File | null>(null);
  const [signedOn, setSignedOn] = useState(harareToday());
  const awaitingDoc = m.status === "DRAFT" || m.status === "AWAITING_SIGN_OFF";

  return (
    <li className="rounded-xl border border-line p-3">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <p className="text-sm font-medium">
            {m.type === "SOLE" ? "Sole" : "Open"} mandate · {m.ownerName}
          </p>
          <p className="text-xs text-muted">
            {formatDate(m.startDate)} to {formatDate(m.expiryDate)}
            {m.minimumPriceCents != null && <> · Authority down to {formatCents(m.minimumPriceCents, m.currency)}</>}
            {m.commissionBps != null && <> · {m.commissionBps / 100}% commission</>}
          </p>
          {m.preaSignedOffByName && (
            <p className="text-xs text-muted">
              Signed off by {m.preaSignedOffByName}, {formatDate(m.preaSignedOffAt)}
            </p>
          )}
        </div>
        <span className={`${ws.chip} ${m.status === "ACTIVE" ? "text-success" : m.status === "EXPIRED" ? "text-danger" : ""}`}>
          {MANDATE_STATUS_LABEL[m.status]}
        </span>
      </div>

      <div className="mt-3 flex flex-wrap gap-2 items-center">
        {m.hasSignedDocument && (
          <button className={ws.buttonGhost} onClick={() => token && orgListings.openSignedMandate(slug, m.id, token)}>
            <FileText className="w-4 h-4" /> {m.documentFileName ?? "Signed mandate"}
          </button>
        )}
        {m.status === "AWAITING_SIGN_OFF" && can("MANDATE_SIGN_OFF") && (
          <button className={ws.button} disabled={busy} onClick={() => run("signoff", () => orgListings.signOffMandate(slug, m.id, token!))}>
            <ShieldCheck className="w-4 h-4" /> Sign off as PREA
          </button>
        )}
        {m.status === "AWAITING_SIGN_OFF" && !can("MANDATE_SIGN_OFF") && (
          <span className="text-xs text-muted">Waiting for the PREA to sign off.</span>
        )}
        {["DRAFT", "AWAITING_SIGN_OFF", "ACTIVE"].includes(m.status) && can("LISTING_EDIT") && (
          <button
            className={ws.buttonGhost}
            disabled={busy}
            onClick={() => {
              if (window.confirm("Cancel this mandate? A live listing relying on it will be unpublished.")) {
                void run("cancel", () => orgListings.cancelMandate(slug, m.id, undefined, token!));
              }
            }}
          >
            Cancel mandate
          </button>
        )}
      </div>

      {awaitingDoc && can("LISTING_EDIT") && (
        <div className="mt-3 grid gap-2 sm:grid-cols-[1fr,auto,auto] items-end">
          <label className="block">
            <span className={ws.label}>Signed copy (PDF or photo)</span>
            <input
              type="file"
              accept="application/pdf,image/jpeg,image/png,image/webp"
              capture="environment"
              className="block w-full text-sm file:mr-3 file:rounded-lg file:border-0 file:bg-primary file:text-on-primary file:px-3 file:py-2"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
          </label>
          <label className="block">
            <span className={ws.label}>Owner signed on</span>
            <input type="date" className={ws.input} max={harareToday()} value={signedOn} onChange={(e) => setSignedOn(e.target.value)} />
          </label>
          <button
            className={ws.button}
            disabled={busy || !file}
            onClick={() => file && run("upload", () => orgListings.attachSignedMandate(slug, m.id, file, signedOn, token!))}
          >
            <Upload className="w-4 h-4" /> Attach
          </button>
        </div>
      )}
    </li>
  );
}

function MandateForm({
  detail,
  busy,
  onSubmit,
  onCancel,
}: {
  detail: OrgListingDetail;
  busy: boolean;
  onSubmit: (data: Parameters<typeof orgListings.createMandate>[2]) => void;
  onCancel: () => void;
}) {
  const { context } = useOrgWorkspace();
  const today = harareToday();
  const isSale = detail.property.listingType === "SALE";
  const [form, setForm] = useState({
    type: "SOLE" as MandateType,
    startDate: today,
    expiryDate: addDays(today, 90),
    ownerName: "",
    ownerPhone: "",
    ownerEmail: "",
    minimumPrice: "",
    commissionPct: isSale && context.organisation.saleCommissionBps != null ? String(context.organisation.saleCommissionBps / 100) : "",
    offerAuthority: "",
  });
  const [error, setError] = useState<string | null>(null);
  const set = (k: keyof typeof form, v: string) => setForm((f) => ({ ...f, [k]: v }));

  return (
    <form
      className="mt-4 space-y-3 rounded-xl border border-line p-3"
      onSubmit={(e) => {
        e.preventDefault();
        const minimumPriceCents = form.minimumPrice ? parseToCents(form.minimumPrice) : undefined;
        if (form.minimumPrice && !minimumPriceCents) {
          setError("Enter a valid minimum price");
          return;
        }
        setError(null);
        onSubmit({
          clientId: crypto.randomUUID(),
          type: form.type,
          startDate: form.startDate,
          expiryDate: form.expiryDate,
          ownerName: form.ownerName.trim(),
          ownerPhone: form.ownerPhone.trim() || undefined,
          ownerEmail: form.ownerEmail.trim() || undefined,
          minimumPriceCents: minimumPriceCents ?? undefined,
          commissionBps: form.commissionPct ? Math.round(parseFloat(form.commissionPct) * 100) : undefined,
          offerAuthority: form.offerAuthority.trim() || undefined,
        });
      }}
    >
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block">
          <span className={ws.label}>Type</span>
          <select className={ws.input} value={form.type} onChange={(e) => set("type", e.target.value)}>
            <option value="SOLE">Sole mandate</option>
            <option value="OPEN">Open mandate</option>
          </select>
        </label>
        <label className="block">
          <span className={ws.label}>Owner name</span>
          <input className={ws.input} required value={form.ownerName} onChange={(e) => set("ownerName", e.target.value)} />
        </label>
        <label className="block">
          <span className={ws.label}>Starts</span>
          <input type="date" className={ws.input} required value={form.startDate} onChange={(e) => set("startDate", e.target.value)} />
        </label>
        <label className="block">
          <span className={ws.label}>Expires (last day)</span>
          <input type="date" className={ws.input} required min={today} value={form.expiryDate} onChange={(e) => set("expiryDate", e.target.value)} />
        </label>
        <label className="block">
          <span className={ws.label}>Owner phone / WhatsApp</span>
          <input className={ws.input} inputMode="tel" value={form.ownerPhone} onChange={(e) => set("ownerPhone", e.target.value)} />
        </label>
        <label className="block">
          <span className={ws.label}>Owner email</span>
          <input type="email" className={ws.input} value={form.ownerEmail} onChange={(e) => set("ownerEmail", e.target.value)} />
        </label>
        <label className="block">
          <span className={ws.label}>{isSale ? "Lowest acceptable price" : "Lowest acceptable rent"}</span>
          <input className={ws.input} inputMode="decimal" value={form.minimumPrice} onChange={(e) => set("minimumPrice", e.target.value)} />
        </label>
        {isSale && (
          <label className="block">
            <span className={ws.label}>Commission (%)</span>
            <input className={ws.input} type="number" step="0.25" min="0" max="20" value={form.commissionPct} onChange={(e) => set("commissionPct", e.target.value)} />
          </label>
        )}
        <label className="block sm:col-span-2">
          <span className={ws.label}>Offer authority</span>
          <textarea
            className={`${ws.input} min-h-20`}
            placeholder="What the agency may agree without referring back to the owner"
            value={form.offerAuthority}
            onChange={(e) => set("offerAuthority", e.target.value)}
          />
        </label>
      </div>
      {error && <p className="text-sm text-danger">{error}</p>}
      <div className="flex gap-2">
        <button className={ws.button} disabled={busy}>
          Save mandate
        </button>
        <button type="button" className={ws.buttonGhost} onClick={onCancel}>
          Cancel
        </button>
      </div>
    </form>
  );
}

function ConsentSection({ detail, run, busy }: { detail: OrgListingDetail; run: Run; busy: boolean }) {
  const { slug, can } = useOrgWorkspace();
  const { token } = useAuth();
  const { compliance: c } = detail;
  const [photos, setPhotos] = useState(c.ownerConsentPhotos);
  const [address, setAddress] = useState(c.ownerConsentAddress);
  const changed = photos !== c.ownerConsentPhotos || address !== c.ownerConsentAddress;
  const editable = can("LISTING_EDIT");

  return (
    <section className={`${ws.card} p-4 sm:p-5`} aria-labelledby="consent-h">
      <h2 id="consent-h" className="font-semibold">
        Owner consent
      </h2>
      <p className="text-xs text-muted mt-1">
        Record what the owner agreed to. Withdrawing consent takes a live listing down immediately.
      </p>
      <div className="mt-3 space-y-2">
        {[
          { label: "Owner consents to photos being shown", value: photos, set: setPhotos },
          { label: "Owner consents to the address being shown", value: address, set: setAddress },
        ].map((item) => (
          <label key={item.label} className="flex items-center gap-3 min-h-11 text-sm">
            <input
              type="checkbox"
              className="h-5 w-5 accent-[var(--ex-primary)]"
              checked={item.value}
              disabled={!editable || busy}
              onChange={(e) => item.set(e.target.checked)}
            />
            {item.label}
          </label>
        ))}
      </div>
      {c.consentRecordedAt && <p className="text-xs text-muted mt-1">Last recorded {formatDate(c.consentRecordedAt)}</p>}
      {editable && (
        <button
          className={`${ws.buttonGhost} mt-3`}
          disabled={!changed || busy}
          onClick={() => run("consent", () => orgListings.setConsent(slug, detail.property.id, { photos, address }, token!))}
        >
          Save consent
        </button>
      )}
    </section>
  );
}
