"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { users } from "@/lib/api";
import {
  User,
  Shield,
  Globe,
  TrendingUp,
  CheckCircle,
  Edit3,
  Save,
  X,
  Star,
} from "lucide-react";
import type { UserRole } from "@/lib/types";

const ROLE_COLORS: Record<UserRole, string> = {
  TENANT: "bg-blue-100 text-blue-700",
  LANDLORD: "bg-green-100 text-green-700",
  AGENT: "bg-purple-100 text-purple-700",
  DIASPORA: "bg-amber-100 text-amber-700",
  INVESTOR: "bg-indigo-100 text-indigo-700",
  ADMIN: "bg-red-100 text-red-700",
  DEVELOPER: "bg-orange-100 text-orange-700",
  PRIVATE: "bg-teal-100 text-teal-700",
};

export default function ProfilePage() {
  const { user, token, setUser, loading } = useAuth();
  const router = useRouter();
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const [form, setForm] = useState({
    fullName: "",
    phone: "",
    city: "",
    country: "",
    bio: "",
    occupation: "",
    diasporaLocation: "",
    preferredCurrency: "USD",
    emergencyContactName: "",
    emergencyContactPhone: "",
    emailNotifications: true,
    smsNotifications: true,
  });

  useEffect(() => {
    if (!loading && !user) router.push("/login?redirect=/profile");
  }, [user, loading, router]);

  useEffect(() => {
    if (user) {
      setForm({
        fullName: user.fullName ?? "",
        phone: user.phone ?? "",
        city: user.city ?? "",
        country: user.country ?? "",
        bio: user.bio ?? "",
        occupation: user.occupation ?? "",
        diasporaLocation: user.diasporaLocation ?? "",
        preferredCurrency: user.preferredCurrency ?? "USD",
        emergencyContactName: user.emergencyContactName ?? "",
        emergencyContactPhone: user.emergencyContactPhone ?? "",
        emailNotifications: user.emailNotifications ?? true,
        smsNotifications: user.smsNotifications ?? true,
      });
    }
  }, [user]);

  const handleSave = async () => {
    if (!user || !token) return;
    setSaving(true);
    setError("");
    try {
      const updated = await users.updateProfile(user.id, form, token);
      setUser(updated);
      setEditing(false);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to save profile.");
    } finally {
      setSaving(false);
    }
  };

  if (loading || !user) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-12 space-y-4">
        <div className="h-32 bg-gray-100 rounded-2xl animate-pulse" />
        <div className="h-64 bg-gray-100 rounded-2xl animate-pulse" />
      </div>
    );
  }

  return (
    <div className="max-w-3xl mx-auto px-4 py-8">
      {/* Header card */}
      <div className="bg-gradient-to-br from-blue-600 to-blue-700 rounded-2xl p-6 text-white mb-6">
        <div className="flex items-start justify-between gap-4">
          <div className="flex items-center gap-4">
            <div className="w-16 h-16 bg-white/20 rounded-full flex items-center justify-center text-2xl font-bold">
              {user.fullName?.charAt(0).toUpperCase()}
            </div>
            <div>
              <h1 className="text-xl font-bold">{user.fullName}</h1>
              <p className="text-blue-100 text-sm">{user.email}</p>
              <div className="flex flex-wrap gap-1.5 mt-2">
                {user.roles?.map((role) => (
                  <span key={role} className="text-xs bg-white/20 text-white px-2 py-0.5 rounded-full font-medium">
                    {role}
                  </span>
                ))}
              </div>
            </div>
          </div>
          <div className="text-right">
            {user.trustScore != null && (
              <div className="flex items-center gap-1 text-white">
                <Star className="w-4 h-4 fill-current text-amber-300" />
                <span className="font-bold">{user.trustScore}</span>
                <span className="text-blue-200 text-xs">trust score</span>
              </div>
            )}
            {user.profileCompletion != null && (
              <div className="mt-1">
                <div className="text-xs text-blue-200 mb-1">{user.profileCompletion}% complete</div>
                <div className="w-24 h-1.5 bg-white/20 rounded-full">
                  <div
                    className="h-full bg-white rounded-full"
                    style={{ width: `${user.profileCompletion}%` }}
                  />
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Trust badges */}
        <div className="flex flex-wrap gap-2 mt-4">
          {user.verified && (
            <span className="flex items-center gap-1 bg-white/20 text-white text-xs px-3 py-1 rounded-full">
              <CheckCircle className="w-3 h-3" /> Verified
            </span>
          )}
          {user.identityVerified && (
            <span className="flex items-center gap-1 bg-white/20 text-white text-xs px-3 py-1 rounded-full">
              <Shield className="w-3 h-3" /> Identity Verified
            </span>
          )}
        </div>
      </div>

      {/* Profile form */}
      <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-lg font-bold text-gray-900">Profile Information</h2>
          {editing ? (
            <div className="flex gap-2">
              <button
                onClick={() => setEditing(false)}
                className="flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 px-3 py-1.5 border border-gray-200 rounded-lg"
              >
                <X className="w-4 h-4" /> Cancel
              </button>
              <button
                onClick={handleSave}
                disabled={saving}
                className="flex items-center gap-1 text-sm bg-blue-600 text-white px-3 py-1.5 rounded-lg hover:bg-blue-700 disabled:opacity-60"
              >
                <Save className="w-4 h-4" />
                {saving ? "Saving..." : "Save"}
              </button>
            </div>
          ) : (
            <button
              onClick={() => setEditing(true)}
              className="flex items-center gap-1 text-sm text-blue-600 hover:text-blue-700 px-3 py-1.5 border border-blue-200 rounded-lg"
            >
              <Edit3 className="w-4 h-4" /> Edit
            </button>
          )}
        </div>

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-5">
            {error}
          </div>
        )}

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
          {[
            { label: "Full Name", key: "fullName" as const },
            { label: "Phone", key: "phone" as const },
            { label: "City", key: "city" as const },
            { label: "Country", key: "country" as const },
            { label: "Occupation", key: "occupation" as const },
            { label: "Preferred Currency", key: "preferredCurrency" as const },
            { label: "Diaspora Location", key: "diasporaLocation" as const },
            { label: "Emergency Contact Name", key: "emergencyContactName" as const },
            { label: "Emergency Contact Phone", key: "emergencyContactPhone" as const },
          ].map(({ label, key }) => (
            <div key={key}>
              <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">
                {label}
              </label>
              {editing ? (
                <input
                  type="text"
                  value={form[key]}
                  onChange={(e) => setForm((f) => ({ ...f, [key]: e.target.value }))}
                  className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                />
              ) : (
                <p className="text-sm text-gray-900">{form[key] || <span className="text-gray-400">—</span>}</p>
              )}
            </div>
          ))}

          <div className="sm:col-span-2">
            <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">Bio</label>
            {editing ? (
              <textarea
                value={form.bio}
                onChange={(e) => setForm((f) => ({ ...f, bio: e.target.value }))}
                rows={3}
                className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100 resize-none"
              />
            ) : (
              <p className="text-sm text-gray-900">{form.bio || <span className="text-gray-400">—</span>}</p>
            )}
          </div>
        </div>

        {editing && (
          <div className="mt-5 pt-5 border-t border-gray-100 flex flex-wrap gap-4">
            <label className="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
              <input
                type="checkbox"
                checked={form.emailNotifications}
                onChange={(e) => setForm((f) => ({ ...f, emailNotifications: e.target.checked }))}
                className="w-4 h-4 text-blue-600"
              />
              Email notifications
            </label>
            <label className="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
              <input
                type="checkbox"
                checked={form.smsNotifications}
                onChange={(e) => setForm((f) => ({ ...f, smsNotifications: e.target.checked }))}
                className="w-4 h-4 text-blue-600"
              />
              SMS notifications
            </label>
          </div>
        )}
      </div>

      {/* Roles */}
      <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm mt-5">
        <h2 className="text-lg font-bold text-gray-900 mb-4">My Roles</h2>
        <div className="flex flex-wrap gap-2">
          {user.roles?.map((role) => (
            <span key={role} className={`px-4 py-2 rounded-xl text-sm font-semibold ${ROLE_COLORS[role] ?? "bg-gray-100 text-gray-700"}`}>
              {role}
            </span>
          ))}
        </div>
        <p className="text-xs text-gray-500 mt-3">
          You can add more roles from Settings. Admin access requires approval.
        </p>
      </div>
    </div>
  );
}
