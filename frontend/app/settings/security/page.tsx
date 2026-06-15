"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { users as usersApi } from "@/lib/api";
import { Shield, AlertCircle, CheckCircle, Plus } from "lucide-react";
import type { UserRole } from "@/lib/types";

const SELF_SERVICE_ROLES: UserRole[] = ["TENANT", "LANDLORD", "AGENT", "DIASPORA", "INVESTOR", "DEVELOPER", "PRIVATE"];
const ROLE_DESCRIPTIONS: Record<UserRole, string> = {
  TENANT: "Browse, apply, and manage rental properties",
  LANDLORD: "List, manage, and rent out your properties",
  AGENT: "Verify properties and support buyers/renters",
  DIASPORA: "Manage properties remotely from abroad",
  INVESTOR: "Invest in REIT portfolios and property funds",
  ADMIN: "Platform administration (requires approval)",
  DEVELOPER: "List and manage property development projects",
  PRIVATE: "List your own property as a private seller or landlord",
};

export default function SecuritySettingsPage() {
  const { user, token, loading, refresh } = useAuth();
  const router = useRouter();

  const [addingRole, setAddingRole] = useState<UserRole | null>(null);
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!loading && !user) router.push("/login?redirect=/settings/security");
  }, [user, loading, router]);

  const handleAddRole = async (role: UserRole) => {
    if (!user || !token) return;
    if (role === "ADMIN") {
      setError(""); setSuccess(""); setSubmitting(true);
      try {
        await usersApi.adminRequest(user.id, token);
        setSuccess("Admin access request submitted. An admin will review it.");
      } catch (err: unknown) {
        setError(err instanceof Error ? err.message : "Request failed.");
      } finally {
        setSubmitting(false);
        setAddingRole(null);
      }
      return;
    }
    if (!password) { setError("Enter your current password."); return; }
    setError(""); setSuccess(""); setSubmitting(true);
    try {
      await usersApi.addRole(user.id, { role, password }, token);
      await refresh();
      setSuccess(`${role} role added successfully!`);
      setAddingRole(null);
      setPassword("");
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to add role.");
    } finally {
      setSubmitting(false);
    }
  };

  if (loading || !user) return null;

  const currentRoles = user.roles ?? [];
  const availableRoles = SELF_SERVICE_ROLES.filter((r) => !currentRoles.includes(r));

  return (
    <div className="max-w-3xl mx-auto px-4 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
          <Shield className="w-7 h-7 text-blue-600" /> Security & Roles
        </h1>
        <p className="text-gray-500 mt-1">Manage your account roles and security settings</p>
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

      {/* Current roles */}
      <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm mb-6">
        <h2 className="font-bold text-gray-900 mb-4">Current Roles</h2>
        <div className="flex flex-wrap gap-2">
          {currentRoles.map((role) => (
            <span key={role} className="flex items-center gap-1 bg-blue-100 text-blue-700 text-sm font-semibold px-4 py-2 rounded-xl">
              <CheckCircle className="w-4 h-4" /> {role}
            </span>
          ))}
        </div>
      </div>

      {/* Add roles */}
      {availableRoles.length > 0 && (
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm mb-6">
          <h2 className="font-bold text-gray-900 mb-4">Add a Role</h2>
          <p className="text-sm text-gray-500 mb-5">
            Each role unlocks a new workspace. Adding a role requires your current password as confirmation.
          </p>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {availableRoles.map((role) => (
              <div key={role} className={`border rounded-2xl p-4 transition-all ${addingRole === role ? "border-blue-500 bg-blue-50" : "border-gray-200 hover:border-gray-300"}`}>
                <div className="flex items-start justify-between gap-2 mb-2">
                  <div>
                    <p className="font-semibold text-gray-900">{role}</p>
                    <p className="text-xs text-gray-500 mt-0.5">{ROLE_DESCRIPTIONS[role]}</p>
                  </div>
                  <button
                    onClick={() => {
                      setAddingRole(addingRole === role ? null : role);
                      setPassword("");
                      setError("");
                    }}
                    className="shrink-0 text-xs bg-blue-600 hover:bg-blue-700 text-white px-3 py-1.5 rounded-xl font-medium transition-colors flex items-center gap-1"
                  >
                    <Plus className="w-3 h-3" /> Add
                  </button>
                </div>

                {addingRole === role && (
                  <div className="mt-3 pt-3 border-t border-blue-200">
                    {role !== "ADMIN" && (
                      <div className="mb-3">
                        <label className="block text-xs font-medium text-gray-600 mb-1">
                          Confirm your password
                        </label>
                        <input
                          type="password"
                          value={password}
                          onChange={(e) => setPassword(e.target.value)}
                          className="w-full border border-blue-200 rounded-xl px-3 py-2 text-sm outline-none focus:border-blue-500"
                          placeholder="Current password"
                        />
                      </div>
                    )}
                    {role === "ADMIN" && (
                      <p className="text-xs text-gray-500 mb-3">This will submit a request. An admin must approve it.</p>
                    )}
                    <button
                      onClick={() => handleAddRole(role)}
                      disabled={submitting}
                      className="w-full bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white text-sm font-semibold py-2 rounded-xl transition-colors"
                    >
                      {submitting ? "Processing..." : role === "ADMIN" ? "Request Admin Access" : `Add ${role} Role`}
                    </button>
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Account info */}
      <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
        <h2 className="font-bold text-gray-900 mb-4">Account Information</h2>
        <div className="space-y-3 text-sm">
          <div className="flex justify-between items-center py-2 border-b border-gray-100">
            <span className="text-gray-500">Email</span>
            <span className="text-gray-900 font-medium">{user.email}</span>
          </div>
          <div className="flex justify-between items-center py-2 border-b border-gray-100">
            <span className="text-gray-500">Verified</span>
            <span className={user.verified ? "text-green-600 font-medium" : "text-gray-400"}>
              {user.verified ? "Yes" : "No"}
            </span>
          </div>
          <div className="flex justify-between items-center py-2 border-b border-gray-100">
            <span className="text-gray-500">Identity Verified</span>
            <span className={user.identityVerified ? "text-green-600 font-medium" : "text-gray-400"}>
              {user.identityVerified ? "Yes" : "No"}
            </span>
          </div>
          <div className="flex justify-between items-center py-2 border-b border-gray-100">
            <span className="text-gray-500">Two-Factor Auth</span>
            <span className="text-gray-400">Coming soon</span>
          </div>
          <div className="pt-2">
            <button
              onClick={() => router.push("/forgot-password")}
              className="text-blue-600 hover:underline text-sm font-medium"
            >
              Change Password
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
