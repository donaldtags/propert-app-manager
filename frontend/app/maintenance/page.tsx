"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { maintenance as maintenanceApi } from "@/lib/api";
import type { MaintenanceRequest } from "@/lib/types";
import { Wrench, AlertCircle, CheckCircle, Plus } from "lucide-react";

function StatusBadge({ status }: { status: string }) {
  const colors: Record<string, string> = {
    OPEN: "bg-red-100 text-red-700",
    IN_PROGRESS: "bg-amber-100 text-amber-700",
    RESOLVED: "bg-green-100 text-green-700",
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
    propertyId: "",
    category: "Plumbing",
    priority: "NORMAL",
    description: "",
  });

  useEffect(() => {
    if (!loading && !user) router.push("/login?redirect=/maintenance");
  }, [user, loading, router]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user || !token) return;
    setError(""); setSuccess(""); setCreating(true);
    try {
      await maintenanceApi.create({
        propertyId: Number(form.propertyId),
        requestedById: user.id,
        category: form.category,
        priority: form.priority,
        description: form.description,
      }, token);
      setSuccess("Maintenance request submitted!");
      // Reload requests for this property
      if (form.propertyId) {
        const list = await maintenanceApi.list(Number(form.propertyId), token);
        setRequests(list);
      }
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

  const loadForProperty = async () => {
    if (!form.propertyId || !token) return;
    setReqLoading(true);
    try {
      const list = await maintenanceApi.list(Number(form.propertyId), token);
      setRequests(list);
    } catch {}
    setReqLoading(false);
  };

  if (loading) return null;

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
        <div className="bg-green-50 border border-green-200 text-green-700 text-sm px-4 py-3 rounded-xl mb-5 flex items-center gap-2">
          <CheckCircle className="w-4 h-4 shrink-0" /> {success}
        </div>
      )}

      {/* Create form */}
      <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm mb-6">
        <h2 className="font-bold text-gray-900 mb-4 flex items-center gap-2">
          <Plus className="w-5 h-5 text-amber-600" /> Report an Issue
        </h2>
        <form onSubmit={handleCreate} className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">Property ID</label>
            <input
              type="number"
              value={form.propertyId}
              onChange={(e) => setForm((f) => ({ ...f, propertyId: e.target.value }))}
              required
              className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
              placeholder="e.g. 1"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">Category</label>
            <select
              value={form.category}
              onChange={(e) => setForm((f) => ({ ...f, category: e.target.value }))}
              className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-blue-500 bg-white"
            >
              {["Plumbing", "Electrical", "Roofing", "Security", "Appliances", "Pest Control", "Other"].map((c) => (
                <option key={c} value={c}>{c}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">Priority</label>
            <select
              value={form.priority}
              onChange={(e) => setForm((f) => ({ ...f, priority: e.target.value }))}
              className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-blue-500 bg-white"
            >
              {["LOW", "NORMAL", "HIGH", "URGENT"].map((p) => (
                <option key={p} value={p}>{p}</option>
              ))}
            </select>
          </div>
          <div className="sm:col-span-2">
            <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">Description</label>
            <textarea
              value={form.description}
              onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              rows={3}
              required
              className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100 resize-none"
              placeholder="Describe the issue..."
            />
          </div>
          <div className="sm:col-span-2 flex gap-3">
            <button
              type="submit"
              disabled={creating}
              className="bg-amber-600 hover:bg-amber-700 disabled:opacity-60 text-white font-semibold px-6 py-2.5 rounded-xl text-sm transition-colors"
            >
              {creating ? "Submitting..." : "Submit Request"}
            </button>
            <button
              type="button"
              onClick={loadForProperty}
              className="border border-gray-200 text-gray-700 hover:bg-gray-50 font-medium px-6 py-2.5 rounded-xl text-sm transition-colors"
            >
              Load Requests
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
          <p className="text-sm">No maintenance requests. Enter a property ID and click Load Requests.</p>
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
              </div>
              {req.status !== "RESOLVED" && user?.roles?.includes("LANDLORD") && (
                <div className="flex gap-2">
                  {req.status === "OPEN" && (
                    <button
                      onClick={() => handleStatusUpdate(req.id, "IN_PROGRESS")}
                      className="text-xs bg-amber-50 hover:bg-amber-100 text-amber-700 px-3 py-1.5 rounded-xl font-medium transition-colors"
                    >
                      Start
                    </button>
                  )}
                  <button
                    onClick={() => handleStatusUpdate(req.id, "RESOLVED")}
                    className="text-xs bg-green-50 hover:bg-green-100 text-green-700 px-3 py-1.5 rounded-xl font-medium transition-colors"
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
