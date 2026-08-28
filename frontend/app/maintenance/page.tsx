"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { maintenance as maintenanceApi, properties as propertiesApi, leases as leasesApi, vendors as vendorsApi } from "@/lib/api";
import type { MaintenanceRequest, Property, Vendor } from "@/lib/types";
import { Wrench, AlertCircle, CheckCircle, Plus, Camera, Sparkles } from "lucide-react";
import EntityPicker from "@/components/EntityPicker";
import PageLoader from "@/components/PageLoader";

function StatusBadge({ status }: { status: string }) {
  const colors: Record<string, string> = {
    OPEN: "bg-red-100 text-red-700",
    ASSIGNED: "bg-forest-100 text-forest-700",
    IN_PROGRESS: "bg-amber-100 text-amber-700",
    RESOLVED: "bg-forest-100 text-forest-700",
    CANCELLED: "bg-gray-100 text-gray-500",
  };
  return (
    <span className={`text-xs font-semibold px-2 py-0.5 rounded-full ${colors[status] ?? "bg-gray-100 text-gray-700"}`}>
      {status.replace("_", " ")}
    </span>
  );
}

export default function MaintenancePage() {
  const { user, token, loading } = useAuth();
  const router = useRouter();

  const [requests, setRequests] = useState<MaintenanceRequest[]>([]);
  const [reqLoading, setReqLoading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({
    propertyId: null as number | null,
    category: "Plumbing",
    priority: "",
    description: "",
  });

  const [myProperties, setMyProperties] = useState<Property[]>([]);
  const [pickerLoading, setPickerLoading] = useState(true);
  const [photoFiles, setPhotoFiles] = useState<File[]>([]);
  const [vendorList, setVendorList] = useState<Vendor[]>([]);
  const [selectedVendor, setSelectedVendor] = useState<Record<number, number | undefined>>({});
  const [assigningId, setAssigningId] = useState<number | null>(null);

  useEffect(() => {
    if (!loading && !user) router.push("/login?redirect=/maintenance");
  }, [user, loading, router]);

  useEffect(() => {
    if (!user || !token) return;
    setPickerLoading(true);
    propertiesApi.list()
      .then(async (all) => {
        if (user.roles?.includes("LANDLORD")) {
          setMyProperties(all.filter((p) => p.landlordId === user.id));
        } else {
          const tenantLeases = await leasesApi.listByTenant(user.id, token).catch(() => []);
          const propertyIds = new Set(tenantLeases.map((l) => l.propertyId));
          setMyProperties(all.filter((p) => propertyIds.has(p.id)));
        }
      })
      .catch(() => {})
      .finally(() => setPickerLoading(false));
    if (user.roles?.includes("LANDLORD")) {
      vendorsApi.list().then(setVendorList).catch(() => {});
    }
  }, [user, token]);

  const loadForProperty = async (propertyId: number) => {
    if (!token) return;
    setReqLoading(true);
    try {
      const list = await maintenanceApi.list(propertyId, token);
      setRequests(list);
    } catch {}
    setReqLoading(false);
  };

  useEffect(() => {
    if (form.propertyId) loadForProperty(form.propertyId);
  }, [form.propertyId]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user || !token || !form.propertyId) return;
    setError(""); setSuccess(""); setCreating(true);
    try {
      const created = await maintenanceApi.create({
        propertyId: form.propertyId,
        category: form.category,
        priority: form.priority || undefined,
        description: form.description,
      }, token);
      if (photoFiles.length > 0) {
        const formData = new FormData();
        photoFiles.forEach((file) => formData.append("files", file));
        await maintenanceApi.uploadPhotos(created.id, formData, token);
      }
      setSuccess(`Maintenance request submitted! AI classified this as ${created.priority} priority.`);
      setForm((f) => ({ ...f, description: "" }));
      setPhotoFiles([]);
      await loadForProperty(form.propertyId);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to submit request.");
    } finally {
      setCreating(false);
    }
  };

  const handleStatusUpdate = async (id: number, status: string) => {
    if (!token) return;
    setError(""); setSuccess("");
    try {
      await maintenanceApi.updateStatus(id, status, token);
      setSuccess("Status updated!");
      setRequests((prev) => prev.map((r) => r.id === id ? { ...r, status: status as MaintenanceRequest["status"] } : r));
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to update status.");
    }
  };

  const handleAssignVendor = async (id: number) => {
    const vendorId = selectedVendor[id];
    if (!token || !vendorId) return;
    setError(""); setSuccess("");
    setAssigningId(id);
    try {
      const updated = await maintenanceApi.assignVendor(id, vendorId, token);
      setRequests((prev) => prev.map((r) => (r.id === id ? updated : r)));
      setSuccess("Vendor assigned!");
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to assign vendor.");
    } finally {
      setAssigningId(null);
    }
  };

  if (loading) return <PageLoader />;

  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
          <Wrench className="w-7 h-7 text-amber-600" /> Maintenance
        </h1>
        <p className="text-gray-500 mt-1">Report and track property maintenance issues</p>
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

      {/* Create form */}
      <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm mb-6">
        <h2 className="font-bold text-gray-900 mb-4 flex items-center gap-2">
          <Plus className="w-5 h-5 text-amber-600" /> Report an Issue
        </h2>
        <form onSubmit={handleCreate} className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div className="sm:col-span-2">
            <EntityPicker
              label="Property"
              loading={pickerLoading}
              options={myProperties.map((p) => ({ id: p.id, label: p.title, sublabel: `${p.suburb}, ${p.city}` }))}
              value={form.propertyId}
              onChange={(id) => setForm((f) => ({ ...f, propertyId: id }))}
              placeholder="Choose a property"
              emptyMessage="No properties found"
              required
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">Category</label>
            <select
              value={form.category}
              onChange={(e) => setForm((f) => ({ ...f, category: e.target.value }))}
              className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-forest-500 bg-white"
            >
              {["Plumbing", "Electrical", "Roofing", "Security", "Appliances", "Pest Control", "Other"].map((c) => (
                <option key={c} value={c}>{c}</option>
              ))}
            </select>
            {(form.category === "Plumbing" || form.category === "Electrical") && (
              <a
                href={`/services?category=${form.category === "Plumbing" ? "PLUMBING" : "ELECTRICAL"}`}
                target="_blank"
                rel="noreferrer"
                className="text-xs text-forest-600 hover:underline mt-1.5 inline-block"
              >
                Need it fixed sooner? Find local {form.category.toLowerCase()} services →
              </a>
            )}
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">Priority</label>
            <select
              value={form.priority}
              onChange={(e) => setForm((f) => ({ ...f, priority: e.target.value }))}
              className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-forest-500 bg-white"
            >
              <option value="">Auto (AI-detected)</option>
              {["LOW", "NORMAL", "HIGH", "URGENT"].map((p) => (
                <option key={p} value={p}>{p}</option>
              ))}
            </select>
            <p className="text-xs text-gray-400 mt-1.5 flex items-center gap-1">
              <Sparkles className="w-3 h-3" /> Leave as Auto and our AI will triage urgency for you
            </p>
          </div>
          <div className="sm:col-span-2">
            <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">Description</label>
            <textarea
              value={form.description}
              onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              rows={3}
              required
              className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-forest-500 focus:ring-2 focus:ring-forest-100 resize-none"
              placeholder="Describe the issue..."
            />
          </div>
          <div className="sm:col-span-2">
            <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5 flex items-center gap-1.5">
              <Camera className="w-3.5 h-3.5" /> Photos (optional)
            </label>
            <input
              type="file"
              multiple
              accept="image/png,image/jpeg,image/webp"
              onChange={(e) => setPhotoFiles(Array.from(e.target.files ?? []))}
              className="text-sm text-gray-600"
            />
            {photoFiles.length > 0 && (
              <p className="text-xs text-gray-500 mt-1.5">{photoFiles.length} photo(s) selected</p>
            )}
          </div>
          <div className="sm:col-span-2">
            <button
              type="submit"
              disabled={creating || !form.propertyId}
              className="bg-amber-600 hover:bg-amber-700 disabled:opacity-60 text-white font-semibold px-6 py-2.5 rounded-xl text-sm transition-colors"
            >
              {creating ? "Submitting..." : "Submit Request"}
            </button>
          </div>
        </form>
      </div>

      {/* Request list */}
      {reqLoading ? (
        <div className="space-y-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="h-20 bg-gray-100 rounded-xl animate-pulse" />
          ))}
        </div>
      ) : requests.length === 0 ? (
        <div className="text-center py-12 text-gray-400">
          <Wrench className="w-10 h-10 mx-auto mb-3 opacity-30" />
          <p className="text-sm">
            {form.propertyId ? "No maintenance requests for this property yet." : "Choose a property above to see its maintenance history."}
          </p>
        </div>
      ) : (
        <div className="space-y-4">
          {requests.map((req) => (
            <div key={req.id} className="bg-white border border-gray-200 rounded-2xl p-5 shadow-sm flex flex-wrap items-start justify-between gap-4">
              <div className="flex-1">
                <div className="flex items-center gap-2 mb-1">
                  <p className="font-semibold text-gray-900">{req.category}</p>
                  <StatusBadge status={req.status} />
                  <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${
                    req.priority === "URGENT" ? "bg-red-100 text-red-700" :
                    req.priority === "HIGH" ? "bg-amber-100 text-amber-700" :
                    "bg-gray-100 text-gray-600"
                  }`}>
                    {req.priority}
                  </span>
                </div>
                <p className="text-sm text-gray-600">{req.description}</p>
                <p className="text-xs text-gray-400 mt-1">{new Date(req.createdAt).toLocaleDateString()}</p>
                {req.assignedVendorName && (
                  <p className="text-xs text-forest-700 font-medium mt-1">Assigned to {req.assignedVendorName}</p>
                )}
                {req.photos.length > 0 && (
                  <div className="flex flex-wrap gap-2 mt-2">
                    {req.photos.map((photo) => (
                      <a key={photo.id} href={photo.photoUrl} target="_blank" rel="noreferrer">
                        <img
                          src={photo.photoUrl}
                          alt="Maintenance issue"
                          className="w-16 h-16 object-cover rounded-lg border border-gray-200"
                        />
                      </a>
                    ))}
                  </div>
                )}
                {user?.roles?.includes("LANDLORD") && req.status !== "RESOLVED" && req.status !== "CANCELLED" && (
                  <div className="flex items-center gap-2 mt-2">
                    <select
                      value={selectedVendor[req.id] ?? ""}
                      onChange={(e) => setSelectedVendor((s) => ({ ...s, [req.id]: e.target.value ? Number(e.target.value) : undefined }))}
                      className="text-xs border border-gray-200 rounded-lg px-2 py-1.5 bg-white outline-none focus:border-forest-500"
                    >
                      <option value="">{req.assignedVendorName ? "Reassign vendor…" : "Assign a vendor…"}</option>
                      {vendorList.map((v) => (
                        <option key={v.id} value={v.id}>{v.businessName} ({v.category})</option>
                      ))}
                    </select>
                    <button
                      onClick={() => handleAssignVendor(req.id)}
                      disabled={!selectedVendor[req.id] || assigningId === req.id}
                      className="text-xs font-semibold bg-forest-600 hover:bg-forest-700 disabled:opacity-50 text-white px-3 py-1.5 rounded-lg transition-colors"
                    >
                      {assigningId === req.id ? "Assigning…" : "Assign"}
                    </button>
                  </div>
                )}
              </div>
              {req.status !== "RESOLVED" && user?.roles?.includes("LANDLORD") && (
                <div className="flex gap-2">
                  {(req.status === "OPEN" || req.status === "ASSIGNED") && (
                    <button
                      onClick={() => handleStatusUpdate(req.id, "IN_PROGRESS")}
                      className="text-xs bg-amber-50 hover:bg-amber-100 text-amber-700 px-3 py-1.5 rounded-xl font-medium transition-colors"
                    >
                      Start
                    </button>
                  )}
                  <button
                    onClick={() => handleStatusUpdate(req.id, "RESOLVED")}
                    className="text-xs bg-forest-50 hover:bg-forest-100 text-forest-700 px-3 py-1.5 rounded-xl font-medium transition-colors"
                  >
                    Resolve
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
