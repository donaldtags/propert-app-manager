"use client";

import { useCallback, useEffect, useState } from "react";
import { IdCard, CheckCircle2, AlertTriangle, XCircle, Loader2 } from "lucide-react";
import { kyc as kycApi } from "@/lib/api";
import type { KycSubmission } from "@/lib/types";

const MANUAL_CHECKS: { key: string; label: string }[] = [
  { key: "NAME_MATCHES", label: "The name on the ID matches the legal name typed in" },
  { key: "NUMBER_MATCHES", label: "The ID number on the card matches the number typed in" },
  { key: "FACE_MATCHES", label: "The face on the ID matches both selfies" },
  { key: "DOCUMENT_GENUINE", label: "The ID looks genuine, unedited and not expired" },
];

const DOC_LABELS: Record<string, string> = {
  ID_FRONT: "ID front",
  ID_BACK: "ID back",
  PASSPORT: "Passport",
  SELFIE: "Selfie",
  SELFIE_WITH_ID: "Selfie holding the ID",
};

function ResultIcon({ result }: { result: string }) {
  if (result === "PASS") return <CheckCircle2 className="w-4 h-4 text-brand-600 shrink-0" aria-label="Passed" />;
  if (result === "WARN") return <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0" aria-label="Check carefully" />;
  return <XCircle className="w-4 h-4 text-red-600 shrink-0" aria-label="Failed" />;
}

/** Loads the ID images through the authenticated API and shows them inline. */
function DocumentImages({ submission, token }: { submission: KycSubmission; token: string }) {
  const [urls, setUrls] = useState<Record<number, { url: string; type: string }>>({});
  useEffect(() => {
    let alive = true;
    const created: string[] = [];
    (async () => {
      for (const doc of submission.documents) {
        try {
          const blob = await kycApi.downloadDocument(submission.id, doc.id, token);
          const url = URL.createObjectURL(blob);
          created.push(url);
          if (alive) setUrls((u) => ({ ...u, [doc.id]: { url, type: blob.type } }));
        } catch {
          /* shown as "couldn't load" below */
        }
      }
    })();
    return () => {
      alive = false;
      created.forEach((u) => URL.revokeObjectURL(u));
    };
  }, [submission, token]);

  return (
    <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
      {submission.documents.map((doc) => {
        const loaded = urls[doc.id];
        return (
          <figure key={doc.id} className="bg-gray-50 border border-gray-200 rounded-xl p-2">
            <figcaption className="text-xs font-semibold text-gray-700 mb-1">{DOC_LABELS[doc.documentType] ?? doc.documentType}</figcaption>
            {!loaded ? (
              <div className="h-40 flex items-center justify-center text-gray-400"><Loader2 className="w-5 h-5 animate-spin" /></div>
            ) : loaded.type === "application/pdf" ? (
              <a href={loaded.url} target="_blank" rel="noopener noreferrer" className="h-40 flex items-center justify-center text-sm text-brand-600 font-semibold">
                Open PDF
              </a>
            ) : (
              <a href={loaded.url} target="_blank" rel="noopener noreferrer" title="Open full size">
                {/* eslint-disable-next-line @next/next/no-img-element -- private blob from the API */}
                <img src={loaded.url} alt={DOC_LABELS[doc.documentType] ?? doc.documentType} className="h-40 w-full object-contain bg-white rounded-lg" />
              </a>
            )}
          </figure>
        );
      })}
    </div>
  );
}

function ReviewCard({ s, token, onDone }: { s: KycSubmission; token: string; onDone: () => void }) {
  const [ticked, setTicked] = useState<Set<string>>(new Set());
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const checks = s.automatedChecks ?? [];
  const duplicate = checks.some((c) => c.key === "DUPLICATE_ID" && c.result === "FAIL");
  const allTicked = MANUAL_CHECKS.every((c) => ticked.has(c.key));

  const decide = async (approved: boolean) => {
    setBusy(true);
    setError("");
    try {
      await kycApi.review(
        s.id,
        approved
          ? { status: "APPROVED", confirmedChecks: [...ticked] }
          : { status: "REJECTED", reviewNote: reason.trim() },
        token
      );
      onDone();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Couldn't save the decision");
    } finally {
      setBusy(false);
    }
  };

  return (
    <article className="bg-white border border-gray-200 rounded-2xl p-4 sm:p-5 space-y-4">
      <header className="flex flex-wrap justify-between gap-2">
        <div>
          <p className="font-bold text-gray-900">{s.userFullName}</p>
          <p className="text-sm text-gray-500 break-all">{s.userEmail}</p>
        </div>
        <p className="text-xs text-gray-500">Sent {new Date(s.submittedAt).toLocaleString()}</p>
      </header>

      <dl className="grid sm:grid-cols-4 gap-3 text-sm">
        <div><dt className="text-gray-500">Legal name</dt><dd className="font-semibold text-gray-900">{s.legalFullName}</dd></div>
        <div><dt className="text-gray-500">ID number</dt><dd className="font-semibold text-gray-900 font-mono">{s.nationalIdNumber}</dd></div>
        <div><dt className="text-gray-500">Date of birth</dt><dd className="font-semibold text-gray-900">{s.dateOfBirth}</dd></div>
        <div><dt className="text-gray-500">Document</dt><dd className="font-semibold text-gray-900">{s.idDocumentType.replace("_", " ").toLowerCase()} · {s.issuingCountry ?? "ZW"}</dd></div>
      </dl>

      <DocumentImages submission={s} token={token} />

      {checks.length > 0 && (
        <ul className="space-y-1.5">
          {checks.map((c) => (
            <li key={c.key} className="flex gap-2 text-sm">
              <ResultIcon result={c.result} />
              <span><strong className="text-gray-900">{c.label}.</strong> <span className="text-gray-600">{c.detail}</span></span>
            </li>
          ))}
        </ul>
      )}

      {s.registryConsent && (
        <button
          type="button"
          disabled={busy}
          onClick={async () => {
            setBusy(true);
            setError("");
            try {
              await kycApi.recheckRegistry(s.id, token);
              onDone();
            } catch (e) {
              setError(e instanceof Error ? e.message : "Couldn't reach the register");
            } finally {
              setBusy(false);
            }
          }}
          className="text-sm text-brand-600 font-medium py-1"
        >
          Check with the national register again
        </button>
      )}

      {duplicate && (
        <p className="text-sm text-red-800 bg-red-50 border border-red-200 rounded-xl p-3" role="alert">
          This ID number is on another account. If that account is already verified, approval is blocked: one person, one
          verified account.
        </p>
      )}

      <fieldset className="border-t border-gray-100 pt-4">
        <legend className="text-sm font-semibold text-gray-900 mb-2">Compare the photos with the details, then confirm:</legend>
        {MANUAL_CHECKS.map((c) => (
          <label key={c.key} className="flex items-start gap-2 text-sm text-gray-700 py-1.5 cursor-pointer">
            <input
              type="checkbox"
              className="mt-0.5 w-4 h-4"
              checked={ticked.has(c.key)}
              onChange={(e) =>
                setTicked((t) => {
                  const next = new Set(t);
                  if (e.target.checked) next.add(c.key);
                  else next.delete(c.key);
                  return next;
                })
              }
            />
            {c.label}
          </label>
        ))}
      </fieldset>

      {error && <p className="text-sm text-red-600" role="alert">{error}</p>}

      {rejecting ? (
        <div className="space-y-2">
          <label className="block text-sm font-medium text-gray-700">
            Why isn&apos;t it accepted? The person will see this.
            <input
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="e.g. The photo of the ID is blurry - please upload a clearer one"
              className="mt-1 w-full border border-gray-200 rounded-xl px-3 py-2 text-base sm:text-sm"
            />
          </label>
          <div className="flex gap-2">
            <button disabled={busy || !reason.trim()} onClick={() => decide(false)} className="bg-red-600 disabled:opacity-50 text-white font-semibold px-4 py-2 rounded-xl text-sm">
              Reject
            </button>
            <button onClick={() => setRejecting(false)} className="px-4 py-2 rounded-xl text-sm border border-gray-200">Cancel</button>
          </div>
        </div>
      ) : (
        <div className="flex flex-wrap gap-2">
          <button
            disabled={busy || !allTicked}
            onClick={() => decide(true)}
            title={allTicked ? "" : "Tick every check first"}
            className="bg-brand-600 hover:bg-brand-700 disabled:opacity-50 text-white font-semibold px-5 py-2.5 rounded-xl text-sm"
          >
            Approve ID
          </button>
          <button onClick={() => setRejecting(true)} className="px-5 py-2.5 rounded-xl text-sm border border-red-200 text-red-700">
            Reject…
          </button>
        </div>
      )}
    </article>
  );
}

/** Admin "Verification Queue": check each ID side by side with what the person typed. */
export default function IdReviewQueue({ token, onChanged }: { token: string; onChanged?: () => void }) {
  const [items, setItems] = useState<KycSubmission[] | null>(null);
  const [error, setError] = useState("");
  const load = useCallback(() => {
    kycApi.listForAdmin(token).then(setItems).catch((e) => setError(e instanceof Error ? e.message : "Couldn't load"));
  }, [token]);
  useEffect(() => {
    load();
  }, [load]);

  const pending = (items ?? []).filter((s) => s.status === "PENDING");
  const reviewed = (items ?? []).filter((s) => s.status !== "PENDING");

  return (
    <div>
      <div className="mb-6">
        <h2 className="text-lg font-bold text-gray-900 flex items-center gap-2">
          <IdCard className="w-5 h-5 text-brand-600" /> Verification Queue
        </h2>
        <p className="text-sm text-gray-500 mt-1">
          Check each ID against what the person typed. Listings can only be marked Verified, and agent licences approved,
          once the person&apos;s ID has been approved here.
        </p>
      </div>
      {error && <p className="text-sm text-red-600 mb-3" role="alert">{error}</p>}
      {!items ? (
        <div className="h-40 bg-gray-100 rounded-2xl animate-pulse" />
      ) : pending.length === 0 ? (
        <p className="text-center text-gray-500 py-12">No IDs waiting to be checked.</p>
      ) : (
        <div className="space-y-4">
          {pending.map((s) => (
            <ReviewCard key={s.id} s={s} token={token} onDone={() => { load(); onChanged?.(); }} />
          ))}
        </div>
      )}
      {reviewed.length > 0 && (
        <details className="mt-6">
          <summary className="cursor-pointer text-sm font-semibold text-gray-700 py-2">Already reviewed ({reviewed.length})</summary>
          <ul className="mt-2 divide-y divide-gray-100 bg-white border border-gray-200 rounded-xl">
            {reviewed.map((s) => (
              <li key={s.id} className="p-3 text-sm flex flex-wrap justify-between gap-2">
                <span>{s.userFullName} · <span className="font-mono">{s.nationalIdNumber}</span></span>
                <span className={s.status === "APPROVED" ? "text-brand-700 font-semibold" : "text-red-700 font-semibold"}>
                  {s.status === "APPROVED" ? "Approved" : `Rejected: ${s.reviewNote ?? ""}`}
                </span>
              </li>
            ))}
          </ul>
        </details>
      )}
    </div>
  );
}
