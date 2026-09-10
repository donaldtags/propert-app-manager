"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/lib/auth";
import { construction as constructionApi } from "@/lib/api";
import type { ConstructionProject } from "@/lib/types";
import { settingsRoleUrl } from "@/lib/roleGate";
import StatusBadge from "@/components/StatusBadge";
import EmptyState from "@/components/EmptyState";
import AlertBanner from "@/components/AlertBanner";
import ProgressBar from "@/components/ProgressBar";
import { HardHat, Plus, MapPin } from "lucide-react";

export default function ConstructionProjectsPage() {
  const { user, token, loading } = useAuth();
  const router = useRouter();
  const [projects, setProjects] = useState<ConstructionProject[]>([]);
  const [listLoading, setListLoading] = useState(true);
  const [error, setError] = useState("");
  const [showForm, setShowForm] = useState(false);
  const [saving, setSaving] = useState(false);

  const [title, setTitle] = useState("");
  const [city, setCity] = useState("");
  const [country, setCountry] = useState("Zimbabwe");
  const [budgetTotal, setBudgetTotal] = useState("");
  const [contractorName, setContractorName] = useState("");
  const [contractorPhone, setContractorPhone] = useState("");
  const [startDate, setStartDate] = useState("");
  const [expectedCompletionDate, setExpectedCompletionDate] = useState("");

  useEffect(() => {
    if (!loading && (!user || !user.roles?.includes("DIASPORA"))) {
      router.push(user ? settingsRoleUrl("DIASPORA", "construction monitoring needs the Diaspora role") : "/login?redirect=/construction");
    }
  }, [user, loading, router]);

  const load = () => {
    if (!user || !token) return;
    constructionApi
      .listByOwner(user.id, token)
      .then(setProjects)
      .catch(() => setError("Failed to load your construction projects."))
      .finally(() => setListLoading(false));
  };

  useEffect(() => {
    if (user && token && user.roles?.includes("DIASPORA")) load();
  }, [user, token]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!token) return;
    setError("");
    setSaving(true);
    try {
      const project = await constructionApi.create(
        {
          title,
          city,
          country,
          budgetTotal: budgetTotal ? Number(budgetTotal) : undefined,
          contractorName: contractorName || undefined,
          contractorPhone: contractorPhone || undefined,
          startDate: startDate || undefined,
          expectedCompletionDate: expectedCompletionDate || undefined,
        },
        token
      );
      router.push(`/construction/${project.id}`);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to create project.");
    } finally {
      setSaving(false);
    }
  };

  if (loading || listLoading) {
    return (
      <div className="max-w-5xl mx-auto px-4 py-8">
        <div className="h-8 w-64 bg-gray-100 rounded-lg animate-pulse mb-8" />
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {Array.from({ length: 2 }).map((_, i) => (
            <div key={i} className="h-40 bg-gray-100 rounded-2xl animate-pulse" />
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-5xl mx-auto px-4 py-8">
      <div className="mb-8 flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Construction Monitoring</h1>
          <p className="text-gray-500 mt-1">Watch your build progress remotely, without relying on phone calls home</p>
        </div>
        <button
          onClick={() => setShowForm((s) => !s)}
          className="flex items-center gap-1.5 bg-forest-600 hover:bg-forest-700 text-white px-4 py-2.5 rounded-xl text-sm font-semibold transition-colors"
        >
          <Plus className="w-4 h-4" /> Start a Project
        </button>
      </div>

      {error && <AlertBanner variant="error" className="mb-5">{error}</AlertBanner>}

      {showForm && (
        <form onSubmit={handleCreate} className="bg-white border border-gray-200 rounded-2xl shadow-sm p-6 mb-8 space-y-4">
          <h2 className="font-bold text-gray-900">New Construction Project</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">Project name</label>
              <input
                required
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="My House — Harare"
                className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm outline-none focus:border-forest-500"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">City</label>
              <input
                required
                value={city}
                onChange={(e) => setCity(e.target.value)}
                placeholder="Harare"
                className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm outline-none focus:border-forest-500"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">Country</label>
              <input
                value={country}
                onChange={(e) => setCountry(e.target.value)}
                className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm outline-none focus:border-forest-500"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">Budget (USD)</label>
              <input
                type="number"
                min="0"
                value={budgetTotal}
                onChange={(e) => setBudgetTotal(e.target.value)}
                placeholder="45000"
                className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm outline-none focus:border-forest-500"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">Contractor name</label>
              <input
                value={contractorName}
                onChange={(e) => setContractorName(e.target.value)}
                className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm outline-none focus:border-forest-500"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">Contractor phone</label>
              <input
                value={contractorPhone}
                onChange={(e) => setContractorPhone(e.target.value)}
                className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm outline-none focus:border-forest-500"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">Start date</label>
              <input
                type="date"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm outline-none focus:border-forest-500"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">Expected completion</label>
              <input
                type="date"
                value={expectedCompletionDate}
                onChange={(e) => setExpectedCompletionDate(e.target.value)}
                className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm outline-none focus:border-forest-500"
              />
            </div>
          </div>
          <div className="flex gap-3">
            <button
              type="submit"
              disabled={saving}
              className="bg-forest-600 hover:bg-forest-700 disabled:opacity-60 text-white px-5 py-2.5 rounded-xl text-sm font-semibold transition-colors"
            >
              {saving ? "Creating…" : "Create Project"}
            </button>
            <button
              type="button"
              onClick={() => setShowForm(false)}
              className="text-gray-600 border border-gray-200 hover:bg-gray-50 px-5 py-2.5 rounded-xl text-sm font-semibold transition-colors"
            >
              Cancel
            </button>
          </div>
        </form>
      )}

      {projects.length === 0 ? (
        <div className="bg-white border border-gray-200 rounded-2xl shadow-sm">
          <EmptyState
            icon={HardHat}
            title="No construction projects yet"
            hint="Start one to track budget, milestones and progress remotely"
          />
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {projects.map((p) => (
            <Link
              key={p.id}
              href={`/construction/${p.id}`}
              className="block bg-white border border-gray-200 rounded-2xl shadow-sm p-5 hover:border-forest-300 transition-colors"
            >
              <div className="flex items-start justify-between gap-2 mb-2">
                <h3 className="font-bold text-gray-900">{p.title}</h3>
                <StatusBadge status={p.status} />
              </div>
              <p className="text-xs text-gray-500 flex items-center gap-1 mb-3">
                <MapPin className="w-3 h-3" /> {p.city}, {p.country}
              </p>
              <div className="flex items-center justify-between text-xs text-gray-500 mb-1.5">
                <span>{p.stage.replaceAll("_", " ")}</span>
                <span className="font-semibold text-gray-900">{p.progressPercent}%</span>
              </div>
              <ProgressBar percent={p.progressPercent} />
              {p.latestUpdateNote && (
                <p className="text-xs text-gray-500 mt-3 italic truncate">&ldquo;{p.latestUpdateNote}&rdquo;</p>
              )}
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
