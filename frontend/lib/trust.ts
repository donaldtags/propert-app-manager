/**
 * Client for trust & safety features: agent licences, company (agency) sign-up and approval,
 * "Report this listing", and the REIT fee quote.
 */
import { BASE, ApiError, request } from "./api";
import type { Organisation, Property } from "./types";

export type ApprovalStatus = "PENDING" | "APPROVED" | "REJECTED";

export interface AgentLicence {
  id: number;
  userId: number;
  agentName: string;
  agentEmail?: string | null;
  registrationNumber: string;
  status: ApprovalStatus;
  documentFileName?: string | null;
  documentSha256: string;
  submittedAt: string;
  reviewedAt?: string | null;
  reviewNote?: string | null;
}

export type ReportReason = "FAKE_OR_SCAM" | "NOT_AVAILABLE" | "WRONG_DETAILS" | "STOLEN_PHOTOS" | "OFFENSIVE" | "OTHER";

export const REPORT_REASONS: { value: ReportReason; label: string }[] = [
  { value: "FAKE_OR_SCAM", label: "It looks fake or like a scam" },
  { value: "NOT_AVAILABLE", label: "It's no longer available" },
  { value: "STOLEN_PHOTOS", label: "The photos belong to a different property" },
  { value: "WRONG_DETAILS", label: "The price or details are wrong" },
  { value: "OFFENSIVE", label: "It's offensive or inappropriate" },
  { value: "OTHER", label: "Something else" },
];

export interface ListingReport {
  id: number;
  propertyId: number;
  propertyTitle: string;
  reason: ReportReason;
  details?: string | null;
  status: "OPEN" | "DISMISSED" | "TAKEN_DOWN";
  createdAt: string;
  signedInReporter: boolean;
}

export interface FeeQuote {
  gross: number;
  fee: number;
  /** Buying: what the investor pays. Selling: what the investor receives. */
  investorAmount: number;
  feePercent: number;
}

export type CompanyProfile = Organisation & { approvalStatus?: ApprovalStatus; approvalNote?: string | null };

async function upload<T>(path: string, form: FormData, token: string): Promise<T> {
  const res = await fetch(`${BASE}${path}`, { method: "POST", headers: { Authorization: `Bearer ${token}` }, body: form });
  if (!res.ok) {
    let message = res.statusText;
    try {
      const body = await res.json();
      message = body.message ?? body.error ?? message;
    } catch {}
    throw new ApiError(res.status, message);
  }
  return res.json() as Promise<T>;
}

export const agentLicences = {
  mine: (token: string) => request<AgentLicence | undefined>("/agents/licence/me", {}, token),
  submit: (registrationNumber: string, certificate: File, token: string) => {
    const form = new FormData();
    form.append("registrationNumber", registrationNumber);
    form.append("certificate", certificate);
    return upload<AgentLicence>("/agents/licence", form, token);
  },
  pending: (token: string) => request<AgentLicence[]>("/agents/licences/pending", {}, token),
  review: (id: number, approved: boolean, note: string | undefined, token: string) =>
    request<AgentLicence>(`/agents/licences/${id}`, { method: "PATCH", body: JSON.stringify({ approved, note }) }, token),
  certificateUrl: (id: number) => `${BASE}/agents/licences/${id}/certificate`,
};

export const companies = {
  directory: () => request<CompanyProfile[]>("/orgs"),
  get: (slug: string, token?: string | null) =>
    request<CompanyProfile>(`/orgs/${encodeURIComponent(slug)}`, {}, token ?? undefined),
  listings: (slug: string) => request<Property[]>(`/orgs/${encodeURIComponent(slug)}/public-listings`),
  register: (
    data: { slug: string; name: string; eacRegistrationNumber: string; phone?: string; email?: string; address?: string },
    token: string
  ) => request<CompanyProfile>("/orgs", { method: "POST", body: JSON.stringify(data) }, token),
  pendingReview: (token: string) => request<CompanyProfile[]>("/orgs/pending-review", {}, token),
  decide: (slug: string, approved: boolean, note: string | undefined, token: string) =>
    request<CompanyProfile>(`/orgs/${encodeURIComponent(slug)}/approval`, {
      method: "PATCH",
      body: JSON.stringify({ approved, note }),
    }, token),
};

export const listingReports = {
  report: (propertyId: number, reason: ReportReason, details: string | undefined, token?: string | null) =>
    request<ListingReport>(`/properties/${propertyId}/reports`, {
      method: "POST",
      body: JSON.stringify({ reason, details }),
    }, token ?? undefined),
  open: (token: string) => request<ListingReport[]>("/listing-reports", {}, token),
  resolve: (id: number, action: "DISMISS" | "TAKE_DOWN", note: string | undefined, token: string) =>
    request<ListingReport>(`/listing-reports/${id}`, { method: "PATCH", body: JSON.stringify({ action, note }) }, token),
};

export const reitFees = {
  quote: (reitId: number, units: number, side: "BUY" | "SELL") =>
    request<FeeQuote>(`/investments/fee-quote?reitId=${reitId}&units=${units}&side=${side}`),
};

/** "acme-realty" from "Acme Realty (Pvt) Ltd". */
export function slugify(name: string): string {
  return name
    .toLowerCase()
    .replace(/\(pvt\)|\bltd\b|\bpvt\b/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 60);
}
