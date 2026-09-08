"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/lib/auth";
import { vendors as vendorsApi } from "@/lib/api";
import type { Vendor, VendorCategory } from "@/lib/types";
import {
  Building2,
  AlertCircle,
  CheckCircle,
  Clock,
  ArrowRight,
  Save,
} from "lucide-react";
import PageLoader from "@/components/PageLoader";

const CATEGORY_OPTIONS: { value: VendorCategory; label: string }[] = [
  { value: "MOVING", label: "Moving" },
  { value: "CLEANING", label: "Cleaning" },
  { value: "PLUMBING", label: "Plumbing" },
  { value: "ELECTRICAL", label: "Electrical" },
  { value: "INSURANCE", label: "Insurance" },
  { value: "LEGAL", label: "Legal Services" },
  { value: "SOLAR", label: "Solar" },
  { value: "UTILITIES", label: "Utilities" },
  { value: "FURNITURE", label: "Furniture" },
  { value: "OTHER", label: "Other" },
];

const NOT_REGISTERED_MESSAGE = "You don't have a service provider profile yet";

export default function ProviderPage() {
  const { user, token, loading: authLoading } = useAuth();
  const router = useRouter();

  const [vendor, setVendor] = useState<Vendor | null>(null);
  const [checking, setChecking] = useState(true);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState(false);

  const [form, setForm] = useState({
    businessName: "",
    category: "OTHER" as VendorCategory,
    description: "",
    phone: "",
    city: "",
  });

  useEffect(() => {
    if (!authLoading && !user) router.push("/login?redirect=/provider");
  }, [user, authLoading, router]);

  useEffect(() => {
    if (!user || !token) return;
    if (!user.roles?.includes("SERVICE_PROVIDER")) {
      setChecking(false);
      return;
    }
    vendorsApi
      .mine(token)
      .then((v) => {
        setVendor(v);
        setForm({
          businessName: v.businessName,
          category: v.category,
          description: v.description ?? "",
          phone: v.phone ?? "",
          city: v.city ?? "",
        });
      })
      .catch((err: unknown) => {
        const message = err instanceof Error ? err.message : "";
        if (!message.includes(NOT_REGISTERED_MESSAGE)) setError(message || "Failed to load your service provider profile.");
      })
      .finally(() => setChecking(false));
  }, [user, token]);

  const handleRegister = async () => {
    if (!token) return;
    if (!form.businessName.trim()) { setError("Business name is required."); return; }
    setError(""); setSuccess(""); setSaving(true);
    try {
      const v = await vendorsApi.registerSelf(
        {
          businessName: form.businessName.trim(),
          category: form.category,
          description: form.description || undefined,
          phone: form.phone || undefined,
          city: form.city || undefined,
        },
        token
      );
      setVendor(v);
      setSuccess("Your company details were submitted. An admin will review them shortly.");
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to submit your details.");
    } finally {
      setSaving(false);
    }
  };

  const handleUpdate = async () => {
    if (!token) return;
    setError(""); setSuccess(""); setSaving(true);
    try {
      const v = await vendorsApi.updateMine(
        {
          businessName: form.businessName.trim(),
          category: form.category,
          description: form.description || undefined,
          phone: form.phone || undefined,
          city: form.city || undefined,
        },
        token
      );
      setVendor(v);
      setEditing(false);
      setSuccess("Your details were updated.");
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to update your details.");
    } finally {
      setSaving(false);
    }
  };

  if (authLoading || checking || !user) return <PageLoader />;

  const hasRole = user.roles?.includes("SERVICE_PROVIDER");

  return (
    <div className="max-w-2xl mx-auto px-4 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
          <Building2 className="w-7 h-7 text-forest-600" /> Service Provider
        </h1>
        <p className="text-gray-500 mt-1">
          Register your business and get verified to be listed in the Home Services Marketplace.
        </p>
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

      {!hasRole ? (
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm text-center">
          <p className="text-gray-700 mb-4">
            Add the Service Provider role to your account first, then come back here to submit your company details.
          </p>
          <Link
            href="/settings/security?needRole=SERVICE_PROVIDER&reason=you%20want%20to%20list%20your%20business"
            className="inline-flex items-center gap-2 bg-forest-600 hover:bg-forest-700 text-white font-semibold px-5 py-2.5 rounded-xl transition-colors"
          >
            Add Service Provider role <ArrowRight className="w-4 h-4" />
          </Link>
        </div>
      ) : vendor && !editing ? (
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
          <div className="flex items-start justify-between mb-4">
            <div>
              <p className="font-bold text-gray-900 text-lg">{vendor.businessName}</p>
              <p className="text-sm text-gray-500">{CATEGORY_OPTIONS.find((c) => c.value === vendor.category)?.label}</p>
            </div>
            {vendor.verified ? (
              <span className="flex items-center gap-1 bg-forest-50 text-forest-700 text-xs font-bold px-2.5 py-1 rounded-full">
                <CheckCircle className="w-3.5 h-3.5" /> Verified
              </span>
            ) : (
              <span className="flex items-center gap-1 bg-amber-50 text-amber-700 text-xs font-bold px-2.5 py-1 rounded-full">
                <Clock className="w-3.5 h-3.5" /> Pending review
              </span>
            )}
          </div>
          {!vendor.verified && (
            <p className="text-sm text-gray-500 mb-4">
              Your company details are awaiting admin verification. You&apos;ll be listed in the Services Marketplace once approved.
            </p>
          )}
          <div className="space-y-2 text-sm text-gray-600 mb-5">
            {vendor.description && <p>{vendor.description}</p>}
            {vendor.city && <p>City: {vendor.city}</p>}
            {vendor.phone && <p>Phone: {vendor.phone}</p>}
            {vendor.email && <p>Email: {vendor.email}</p>}
          </div>
          <button
            onClick={() => setEditing(true)}
            className="text-sm font-medium text-forest-600 hover:underline"
          >
            Edit details
          </button>
        </div>
      ) : (
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Business Name</label>
            <input
              type="text"
              value={form.businessName}
              onChange={(e) => setForm((f) => ({ ...f, businessName: e.target.value }))}
              className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-forest-500 focus:ring-2 focus:ring-forest-100"
              placeholder="e.g. Moyo Plumbing Services"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Category</label>
            <select
              value={form.category}
              onChange={(e) => setForm((f) => ({ ...f, category: e.target.value as VendorCategory }))}
              className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-forest-500 bg-white"
            >
              {CATEGORY_OPTIONS.map((c) => (
                <option key={c.value} value={c.value}>{c.label}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Description</label>
            <textarea
              value={form.description}
              onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              rows={3}
              className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-forest-500 resize-none"
              placeholder="What services do you offer?"
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">Phone</label>
              <input
                type="tel"
                value={form.phone}
                onChange={(e) => setForm((f) => ({ ...f, phone: e.target.value }))}
                className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-forest-500"
                placeholder="+263771000001"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">City</label>
              <input
                type="text"
                value={form.city}
                onChange={(e) => setForm((f) => ({ ...f, city: e.target.value }))}
                className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-forest-500"
                placeholder="Harare"
              />
            </div>
          </div>
          <div className="flex items-center gap-3 pt-2">
            <button
              onClick={vendor ? handleUpdate : handleRegister}
              disabled={saving}
              className="flex items-center gap-2 bg-forest-600 hover:bg-forest-700 disabled:opacity-60 text-white font-semibold px-5 py-2.5 rounded-xl transition-colors"
            >
              <Save className="w-4 h-4" /> {saving ? "Saving..." : vendor ? "Save changes" : "Submit for verification"}
            </button>
            {vendor && (
              <button
                onClick={() => setEditing(false)}
                className="text-sm font-medium text-gray-500 hover:text-gray-700"
              >
                Cancel
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
