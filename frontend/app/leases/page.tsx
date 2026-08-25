"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { leases as leasesApi, properties as propertiesApi, users as usersApi } from "@/lib/api";
import type { Lease, LeaseDocument, LeaseDocumentType, LeaseExtraction, Property } from "@/lib/types";
import { FileText, AlertCircle, CheckCircle, Plus, Paperclip, Download, Sparkles } from "lucide-react";
import EntityPicker from "@/components/EntityPicker";

function StatusBadge({ status }: { status: string }) {
  const colors: Record<string, string> = {
    ACTIVE: "bg-forest-100 text-forest-700",
    SIGNED: "bg-forest-100 text-forest-700",
    DRAFT: "bg-gray-100 text-gray-700",
    COMPLETED: "bg-purple-100 text-purple-700",
    ENDED: "bg-red-100 text-red-700",
  };
  return (
    <span className={`text-xs font-semibold px-2 py-0.5 rounded-full ${colors[status] ?? "bg-gray-100 text-gray-700"}`}>
      {status}
    </span>
  );
}

const DOCUMENT_TYPE_LABELS: Record<LeaseDocumentType, string> = {
  LEASE_FORM: "Lease Form",
  HAND_FILLED_APPLICATION: "Rental Application Form",
  PAYSLIP: "Payslip",
  PROOF_OF_EMPLOYMENT: "Proof of Employment",
  BANK_STATEMENT: "Bank Statement",
};

// Blank templates tenants/landlords can download, fill in, and re-upload as the matching document type.
const DOCUMENT_TEMPLATES: Partial<Record<LeaseDocumentType, string>> = {
  HAND_FILLED_APPLICATION: "/templates/PrimeNest_Rental_Application_Form.docx",
};

function DocStatusBadge({ status }: { status: string }) {
  const colors: Record<string, string> = {
    SUBMITTED: "bg-amber-100 text-amber-700",
    APPROVED: "bg-forest-100 text-forest-700",
    REJECTED: "bg-red-100 text-red-700",
  };
  return (
    <span className={`text-xs font-semibold px-2 py-0.5 rounded-full ${colors[status] ?? "bg-gray-100 text-gray-700"}`}>
      {status}
    </span>
  );
}

export default function LeasesPage() {
  const { user, token, loading } = useAuth();
  const router = useRouter();

  const [leaseList, setLeaseList] = useState<Lease[]>([]);
  const [leasesLoading, setLeasesLoading] = useState(true);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({
    propertyId: null as number | null,
    tenantId: null as number | null,
    startDate: "",
    endDate: "",
    monthlyRent: "",
    depositAmount: "",
    notes: "",
  });

  const [extracting, setExtracting] = useState(false);
  const [extractError, setExtractError] = useState("");
  const [extractInfo, setExtractInfo] = useState<LeaseExtraction | null>(null);

  const [myProperties, setMyProperties] = useState<Property[]>([]);
  const [tenants, setTenants] = useState<{ id: number; fullName: string; primaryProfile: string }[]>([]);
  const [pickersLoading, setPickersLoading] = useState(true);

  const [expandedLease, setExpandedLease] = useState<number | null>(null);
  const [documents, setDocuments] = useState<Record<number, LeaseDocument[]>>({});
  const [docsLoading, setDocsLoading] = useState<Record<number, boolean>>({});
  const [uploadType, setUploadType] = useState<Record<number, LeaseDocumentType>>({});
  const [uploadFile, setUploadFile] = useState<Record<number, File | null>>({});
  const [uploading, setUploading] = useState<number | null>(null);
  const [downloading, setDownloading] = useState<number | null>(null);

  useEffect(() => {
    if (!loading && !user) router.push("/login?redirect=/leases");
  }, [user, loading, router]);

  const canManageLeases = user?.roles?.includes("LANDLORD") || user?.roles?.includes("AGENT");

  useEffect(() => {
    if (!canManageLeases || !user || !token) return;
    setPickersLoading(true);
    Promise.allSettled([propertiesApi.list(), usersApi.search({ role: "TENANT" }, token)])
      .then(([propertiesResult, tenantsResult]) => {
        if (propertiesResult.status === "fulfilled") {
          setMyProperties(
            propertiesResult.value.filter((p) => p.landlordId === user.id || p.agentId === user.id)
          );
        }
        if (tenantsResult.status === "fulfilled") {
          setTenants(tenantsResult.value);
        }
      })
      .finally(() => setPickersLoading(false));
  }, [user, canManageLeases]);

  const loadLeases = async () => {
    if (!user || !token) return;
    setLeasesLoading(true);
    try {
      const isTenant = user.roles?.includes("TENANT");
      const isLandlord = user.roles?.includes("LANDLORD");
      const isAgent = user.roles?.includes("AGENT");
      const results = await Promise.allSettled([
        isTenant ? leasesApi.listByTenant(user.id, token) : Promise.resolve([] as Lease[]),
        isLandlord ? leasesApi.listByLandlord(user.id, token) : Promise.resolve([] as Lease[]),
        isAgent ? leasesApi.listByAgent(user.id, token) : Promise.resolve([] as Lease[]),
      ]);
      const all: Lease[] = [];
      results.forEach((r) => { if (r.status === "fulfilled") all.push(...r.value); });
      const unique = all.filter((l, i, arr) => arr.findIndex((x) => x.id === l.id) === i);
      setLeaseList(unique);
    } catch {}
    setLeasesLoading(false);
  };

  useEffect(() => { loadLeases(); }, [user, token]);

  const handleAutoFill = async (file: File | undefined) => {
    if (!file || !token) return;
    setExtractError("");
    setExtractInfo(null);
    setExtracting(true);
    try {
      const formData = new FormData();
      formData.append("file", file);
      const extraction = await leasesApi.extract(formData, token);
      setForm((f) => ({
        ...f,
        startDate: extraction.startDate ?? f.startDate,
        endDate: extraction.endDate ?? f.endDate,
        monthlyRent: extraction.monthlyRent != null ? String(extraction.monthlyRent) : f.monthlyRent,
        depositAmount: extraction.depositAmount != null ? String(extraction.depositAmount) : f.depositAmount,
        notes: extraction.notableTerms ?? f.notes,
      }));
      setExtractInfo(extraction);
    } catch (err: unknown) {
      setExtractError(err instanceof Error ? err.message : "Could not read that document.");
    } finally {
      setExtracting(false);
    }
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user || !token) return;
    if (!form.propertyId || !form.tenantId) {
      setError("Please choose a property and a tenant.");
      return;
    }
    setError(""); setSuccess(""); setCreating(true);
    try {
      await leasesApi.create({
        propertyId: form.propertyId,
        tenantId: form.tenantId,
        startDate: form.startDate,
        endDate: form.endDate,
        monthlyRent: Number(form.monthlyRent),
        depositAmount: Number(form.depositAmount),
        currency: "USD",
        terms: form.notes,
      }, token);
      setSuccess("Lease created successfully!");
      setForm((f) => ({ ...f, propertyId: null, tenantId: null, startDate: "", endDate: "", monthlyRent: "", depositAmount: "", notes: "" }));
      setExtractInfo(null);
      await loadLeases();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to create lease.");
    } finally {
      setCreating(false);
    }
  };

  const handleSign = async (leaseId: number) => {
    if (!user || !token) return;
    setError(""); setSuccess("");
    try {
      await leasesApi.sign(leaseId, token);
      setSuccess("Lease signed!");
      await loadLeases();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to sign lease.");
    }
  };

  const toggleAttachments = async (leaseId: number) => {
    if (expandedLease === leaseId) { setExpandedLease(null); return; }
    setExpandedLease(leaseId);
    if (!documents[leaseId] && token) {
      setDocsLoading((d) => ({ ...d, [leaseId]: true }));
      try {
        const docs = await leasesApi.listDocuments(leaseId, token);
        setDocuments((prev) => ({ ...prev, [leaseId]: docs }));
      } catch (err: unknown) {
        setError(err instanceof Error ? err.message : "Failed to load documents.");
      } finally {
        setDocsLoading((d) => ({ ...d, [leaseId]: false }));
      }
    }
  };

  const handleUpload = async (leaseId: number) => {
    if (!token) return;
    const file = uploadFile[leaseId];
    const docType = uploadType[leaseId] ?? "LEASE_FORM";
    if (!file) { setError("Choose a file to upload."); return; }
    setUploading(leaseId);
    setError(""); setSuccess("");
    try {
      const formData = new FormData();
      formData.append("files", file);
      formData.append("documentTypes", docType);
      const newDocs = await leasesApi.uploadDocuments(leaseId, formData, token);
      setDocuments((prev) => ({ ...prev, [leaseId]: [...(prev[leaseId] ?? []), ...newDocs] }));
      setUploadFile((f) => ({ ...f, [leaseId]: null }));
      setSuccess("Document uploaded successfully.");
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to upload document.");
    } finally {
      setUploading(null);
    }
  };

  const handleDownload = async (leaseId: number, doc: LeaseDocument) => {
    if (!token) return;
    setDownloading(doc.id);
    try {
      const blob = await leasesApi.downloadDocument(leaseId, doc.id, token);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = doc.fileName;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to download document.");
    } finally {
      setDownloading(null);
    }
  };

  const handleReview = async (leaseId: number, documentId: number, status: "APPROVED" | "REJECTED") => {
    if (!token) return;
    setError(""); setSuccess("");
    try {
      const updated = await leasesApi.reviewDocument(leaseId, documentId, { status }, token);
      setDocuments((prev) => ({
        ...prev,
        [leaseId]: (prev[leaseId] ?? []).map((d) => (d.id === documentId ? updated : d)),
      }));
      setSuccess(`Document ${status === "APPROVED" ? "approved" : "rejected"}.`);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to review document.");
    }
  };

  if (loading) return null;

  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
          <FileText className="w-7 h-7 text-forest-600" /> Leases
        </h1>
        <p className="text-gray-500 mt-1">Manage your digital lease agreements</p>
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-5 flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0" /> {error}
        </div>
      )}
      {success && (
        <div className="bg-forest-50 border border-forest-200 text-forest-700 text-sm px-4 py-3 rounded-xl mb-5 flex items-center gap-2">
          <CheckCircle className="w-4 h-4 shrink-0" /> {success}
        </div>
      )}

      {/* Create form (landlords and agents representing a property) */}
      {canManageLeases && (
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm mb-6">
          <h2 className="font-bold text-gray-900 mb-4 flex items-center gap-2">
            <Plus className="w-5 h-5 text-forest-600" /> Create New Lease
          </h2>

          {/* AI auto-fill from an existing lease document */}
          <div className="mb-5 bg-purple-50 border border-purple-100 rounded-xl p-4">
            <label className="flex items-start gap-3 cursor-pointer">
              <Sparkles className="w-5 h-5 text-purple-600 shrink-0 mt-0.5" />
              <div className="flex-1">
                <p className="text-sm font-semibold text-gray-900">Auto-fill from an existing lease document</p>
                <p className="text-xs text-gray-500 mt-0.5">
                  Upload a signed lease (PDF, scan, or Word doc) and PrimeNest's AI will read the dates, rent, and
                  deposit into the form below.
                </p>
                <input
                  type="file"
                  accept=".pdf,.docx,.png,.jpg,.jpeg"
                  disabled={extracting}
                  onChange={(e) => handleAutoFill(e.target.files?.[0])}
                  className="mt-2 text-sm text-gray-600"
                />
                {extracting && <p className="text-xs text-purple-700 mt-2">Reading document…</p>}
                {extractError && <p className="text-xs text-red-600 mt-2">{extractError}</p>}
                {extractInfo && !extracting && (
                  <div className="text-xs text-gray-600 mt-2 space-y-0.5">
                    <p className="text-forest-700 font-medium">Filled in what we could find below — check it over.</p>
                    {extractInfo.tenantFullName && <p>Tenant named in document: {extractInfo.tenantFullName}</p>}
                    {extractInfo.propertyAddress && <p>Address in document: {extractInfo.propertyAddress}</p>}
                    {extractInfo.currency && <p>Currency: {extractInfo.currency}</p>}
                  </div>
                )}
              </div>
            </label>
          </div>

          <form onSubmit={handleCreate} className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <EntityPicker
              label="Property"
              loading={pickersLoading}
              options={myProperties.map((p) => ({ id: p.id, label: p.title, sublabel: `${p.suburb}, ${p.city}` }))}
              value={form.propertyId}
              onChange={(id) => setForm((f) => ({ ...f, propertyId: id }))}
              placeholder="Choose one of your properties"
              emptyMessage="You have no listed properties yet"
              required
            />
            <EntityPicker
              label="Tenant"
              loading={pickersLoading}
              options={tenants.map((t) => ({ id: t.id, label: t.fullName }))}
              value={form.tenantId}
              onChange={(id) => setForm((f) => ({ ...f, tenantId: id }))}
              placeholder="Choose a tenant"
              emptyMessage="No tenants found"
              required
            />
            {[
              { label: "Start Date", key: "startDate" as const, type: "date" },
              { label: "End Date", key: "endDate" as const, type: "date" },
              { label: "Monthly Rent (USD)", key: "monthlyRent" as const, type: "number" },
              { label: "Deposit Amount (USD)", key: "depositAmount" as const, type: "number" },
            ].map(({ label, key, type }) => (
              <div key={key}>
                <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">{label}</label>
                <input
                  type={type}
                  value={form[key]}
                  onChange={(e) => setForm((f) => ({ ...f, [key]: e.target.value }))}
                  required
                  className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-forest-500 focus:ring-2 focus:ring-forest-100"
                />
              </div>
            ))}
            <div className="sm:col-span-2">
              <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">Notes</label>
              <input
                type="text"
                value={form.notes}
                onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))}
                className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-forest-500 focus:ring-2 focus:ring-forest-100"
                placeholder="Optional terms or notes"
              />
            </div>
            <div className="sm:col-span-2">
              <button
                type="submit"
                disabled={creating}
                className="bg-forest-600 hover:bg-forest-700 disabled:opacity-60 text-white font-semibold px-6 py-2.5 rounded-xl text-sm transition-colors"
              >
                {creating ? "Creating..." : "Create Lease"}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Lease list */}
      {leasesLoading ? (
        <div className="space-y-4">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="h-28 bg-gray-100 rounded-2xl animate-pulse" />
          ))}
        </div>
      ) : leaseList.length === 0 ? (
        <div className="text-center py-16 text-gray-500">
          <FileText className="w-12 h-12 mx-auto mb-4 opacity-30" />
          <p className="text-lg font-medium">No leases yet</p>
        </div>
      ) : (
        <div className="space-y-4">
          {leaseList.map((lease) => {
            const canSign =
              (user?.roles?.includes("TENANT") && !lease.signedByTenant) ||
              (canManageLeases && !lease.signedByLandlord);
            const isReviewer = canManageLeases || user?.roles?.includes("ADMIN");
            const isExpanded = expandedLease === lease.id;
            const leaseDocs = documents[lease.id] ?? [];
            return (
              <div key={lease.id} className="bg-white border border-gray-200 rounded-2xl p-5 shadow-sm">
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div>
                    <div className="flex items-center gap-2 mb-1">
                      <p className="font-semibold text-gray-900">Lease #{lease.id}</p>
                      <StatusBadge status={lease.status} />
                    </div>
                    <p className="text-sm text-gray-500">
                      Property #{lease.propertyId} · {lease.startDate} → {lease.endDate}
                    </p>
                    <p className="text-lg font-bold text-forest-600 mt-1">${lease.monthlyRent}/mo</p>
                    <p className="text-xs text-gray-400 mt-0.5">Deposit: ${lease.depositAmount}</p>
                  </div>
                  <div className="flex flex-col gap-2 items-end">
                    <div className="flex gap-2 text-xs text-gray-500">
                      {lease.signedByTenant && <span className="text-forest-600 font-medium">✓ Tenant signed</span>}
                      {lease.signedByLandlord && <span className="text-forest-600 font-medium">✓ Landlord signed</span>}
                    </div>
                    <div className="flex gap-2">
                      <button
                        onClick={() => toggleAttachments(lease.id)}
                        className="text-xs font-medium text-gray-600 hover:text-gray-800 px-3 py-2 border border-gray-200 rounded-xl flex items-center gap-1.5 transition-colors"
                      >
                        <Paperclip className="w-3.5 h-3.5" />
                        Attachments{leaseDocs.length > 0 ? ` (${leaseDocs.length})` : ""}
                      </button>
                      {canSign && (
                        <button
                          onClick={() => handleSign(lease.id)}
                          className="bg-forest-600 hover:bg-forest-700 text-white text-xs font-semibold px-4 py-2 rounded-xl transition-colors"
                        >
                          Sign Lease
                        </button>
                      )}
                    </div>
                  </div>
                </div>
                {lease.terms && (
                  <p className="text-sm text-gray-600 mt-3 pt-3 border-t border-gray-100">{lease.terms}</p>
                )}

                {isExpanded && (
                  <div className="mt-4 pt-4 border-t border-gray-100">
                    <p className="text-sm font-semibold text-gray-900 mb-3">Attachments</p>

                    {Object.entries(DOCUMENT_TEMPLATES).length > 0 && (
                      <div className="flex flex-wrap gap-2 mb-4">
                        {Object.entries(DOCUMENT_TEMPLATES).map(([type, href]) => (
                          <a
                            key={type}
                            href={href}
                            download
                            className="inline-flex items-center gap-1.5 text-xs font-medium text-forest-600 hover:text-forest-700 bg-forest-50 hover:bg-forest-100 px-3 py-1.5 rounded-full transition-colors"
                          >
                            <Download className="w-3.5 h-3.5" />
                            Download blank {DOCUMENT_TYPE_LABELS[type as LeaseDocumentType]}
                          </a>
                        ))}
                      </div>
                    )}

                    {docsLoading[lease.id] ? (
                      <div className="space-y-2 mb-4">
                        {Array.from({ length: 2 }).map((_, i) => (
                          <div key={i} className="h-10 bg-gray-100 rounded-lg animate-pulse" />
                        ))}
                      </div>
                    ) : leaseDocs.length === 0 ? (
                      <p className="text-sm text-gray-400 mb-4">No documents uploaded yet.</p>
                    ) : (
                      <div className="overflow-x-auto mb-4 border border-gray-100 rounded-xl">
                        <table className="w-full text-sm">
                          <thead>
                            <tr className="text-left text-xs text-gray-500 uppercase tracking-wide bg-gray-50">
                              <th className="px-3 py-2 font-medium">File</th>
                              <th className="px-3 py-2 font-medium">Type</th>
                              <th className="px-3 py-2 font-medium">Status</th>
                              <th className="px-3 py-2 font-medium">Uploaded</th>
                              <th className="px-3 py-2 font-medium text-right">Actions</th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-gray-100">
                            {leaseDocs.map((doc) => (
                              <tr key={doc.id}>
                                <td className="px-3 py-2 text-gray-900 font-medium truncate max-w-[180px]">{doc.fileName}</td>
                                <td className="px-3 py-2 text-gray-600 whitespace-nowrap">{DOCUMENT_TYPE_LABELS[doc.documentType] ?? doc.documentType}</td>
                                <td className="px-3 py-2"><DocStatusBadge status={doc.status} /></td>
                                <td className="px-3 py-2 text-gray-400 whitespace-nowrap">{new Date(doc.uploadedAt).toLocaleDateString()}</td>
                                <td className="px-3 py-2 text-right whitespace-nowrap">
                                  <button
                                    onClick={() => handleDownload(lease.id, doc)}
                                    disabled={downloading === doc.id}
                                    className="text-forest-600 hover:text-forest-700 font-medium text-xs mr-3 disabled:opacity-50"
                                  >
                                    {downloading === doc.id ? "Downloading…" : "Download"}
                                  </button>
                                  {isReviewer && doc.status === "SUBMITTED" && (
                                    <>
                                      <button
                                        onClick={() => handleReview(lease.id, doc.id, "APPROVED")}
                                        className="text-forest-600 hover:text-forest-700 font-medium text-xs mr-3"
                                      >
                                        Approve
                                      </button>
                                      <button
                                        onClick={() => handleReview(lease.id, doc.id, "REJECTED")}
                                        className="text-red-600 hover:text-red-700 font-medium text-xs"
                                      >
                                        Reject
                                      </button>
                                    </>
                                  )}
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    )}

                    <div className="flex flex-wrap gap-3 items-end bg-gray-50 rounded-xl p-3">
                      <div>
                        <label className="block text-[11px] font-medium text-gray-500 uppercase tracking-wide mb-1">Document type</label>
                        <select
                          value={uploadType[lease.id] ?? "LEASE_FORM"}
                          onChange={(e) => setUploadType((t) => ({ ...t, [lease.id]: e.target.value as LeaseDocumentType }))}
                          className="border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white outline-none focus:border-forest-500"
                        >
                          {Object.entries(DOCUMENT_TYPE_LABELS).map(([value, label]) => (
                            <option key={value} value={value}>{label}</option>
                          ))}
                        </select>
                      </div>
                      <div>
                        <label className="block text-[11px] font-medium text-gray-500 uppercase tracking-wide mb-1">File (PDF, Word, PNG, JPEG)</label>
                        <input
                          type="file"
                          accept=".pdf,.doc,.docx,.png,.jpg,.jpeg"
                          onChange={(e) => setUploadFile((f) => ({ ...f, [lease.id]: e.target.files?.[0] ?? null }))}
                          className="text-sm text-gray-600"
                        />
                      </div>
                      <button
                        onClick={() => handleUpload(lease.id)}
                        disabled={uploading === lease.id}
                        className="bg-forest-600 hover:bg-forest-700 disabled:opacity-60 text-white text-xs font-semibold px-4 py-2.5 rounded-lg transition-colors"
                      >
                        {uploading === lease.id ? "Uploading…" : "Upload"}
                      </button>
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
