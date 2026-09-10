"use client";

import { useEffect, useState, use as usePromise } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/lib/auth";
import { construction as constructionApi } from "@/lib/api";
import type { ConstructionMilestone, ConstructionProject, ConstructionStage, ConstructionStatus, ConstructionUpdate } from "@/lib/types";
import { settingsRoleUrl } from "@/lib/roleGate";
import StatusBadge from "@/components/StatusBadge";
import EmptyState from "@/components/EmptyState";
import StatTile from "@/components/StatTile";
import AlertBanner from "@/components/AlertBanner";
import ProgressBar from "@/components/ProgressBar";
import {
  AlertCircle,
  DollarSign,
  Wallet,
  HardHat,
  MapPin,
  Phone,
  Plus,
  MessageSquare,
} from "lucide-react";

const STAGES: ConstructionStage[] = [
  "PLANNING", "FOUNDATION", "STRUCTURE", "ROOFING", "ELECTRICAL_PLUMBING", "PLASTERING", "FINISHES", "COMPLETED",
];
const PROJECT_STATUSES: ConstructionStatus[] = ["ACTIVE", "DELAYED", "PAUSED", "COMPLETED"];

export default function ConstructionProjectDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = usePromise(params);
  const projectId = Number(id);
  const { user, token, loading } = useAuth();
  const router = useRouter();

  const [project, setProject] = useState<ConstructionProject | null>(null);
  const [milestones, setMilestones] = useState<ConstructionMilestone[]>([]);
  const [updates, setUpdates] = useState<ConstructionUpdate[]>([]);
  const [dataLoading, setDataLoading] = useState(true);
  const [error, setError] = useState("");
  const [savingProgress, setSavingProgress] = useState(false);
  const [milestoneActionId, setMilestoneActionId] = useState<number | null>(null);

  const [showMilestoneForm, setShowMilestoneForm] = useState(false);
  const [msTitle, setMsTitle] = useState("");
  const [msAmount, setMsAmount] = useState("");
  const [msTargetDate, setMsTargetDate] = useState("");
  const [addingMilestone, setAddingMilestone] = useState(false);

  const [updateNote, setUpdateNote] = useState("");
  const [postingUpdate, setPostingUpdate] = useState(false);

  useEffect(() => {
    if (!loading && (!user || !user.roles?.includes("DIASPORA"))) {
      router.push(user ? settingsRoleUrl("DIASPORA", "construction monitoring needs the Diaspora role") : "/login?redirect=/construction");
    }
  }, [user, loading, router]);

  useEffect(() => {
    if (!(user && token && user.roles?.includes("DIASPORA") && projectId)) return;
    // Independent panels — one failing (e.g. updates) shouldn't blank out the whole page.
    Promise.allSettled([
      constructionApi.get(projectId, token),
      constructionApi.listMilestones(projectId, token),
      constructionApi.listUpdates(projectId, token),
    ])
      .then(([p, m, u]) => {
        if (p.status === "fulfilled") {
          setProject(p.value);
        } else {
          setError("Failed to load this construction project.");
        }
        if (m.status === "fulfilled") setMilestones(m.value);
        if (u.status === "fulfilled") setUpdates(u.value);
      })
      .finally(() => setDataLoading(false));
  }, [user, token, projectId]);

  const handleProgressSave = async (stage: ConstructionStage, progressPercent: number, status: ConstructionStatus) => {
    if (!token || !project) return;
    setSavingProgress(true);
    setError("");
    try {
      const updated = await constructionApi.update(project.id, { stage, progressPercent, status }, token);
      setProject(updated);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to update progress.");
    } finally {
      setSavingProgress(false);
    }
  };

  const handleAddMilestone = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!token || !project) return;
    setAddingMilestone(true);
    try {
      const milestone = await constructionApi.addMilestone(
        project.id,
        { title: msTitle, amountDue: msAmount ? Number(msAmount) : undefined, targetDate: msTargetDate || undefined },
        token
      );
      setMilestones((prev) => [...prev, milestone]);
      setMsTitle("");
      setMsAmount("");
      setMsTargetDate("");
      setShowMilestoneForm(false);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to add milestone.");
    } finally {
      setAddingMilestone(false);
    }
  };

  const milestoneAction = async (milestoneId: number, action: "submit" | "approve" | "mark-paid") => {
    if (!token) return;
    setMilestoneActionId(milestoneId);
    setError("");
    try {
      const fn =
        action === "submit"
          ? constructionApi.submitMilestone
          : action === "approve"
            ? constructionApi.approveMilestone
            : constructionApi.markMilestonePaid;
      const updated = await fn(milestoneId, token);
      setMilestones((prev) => prev.map((m) => (m.id === updated.id ? updated : m)));
      if (action === "mark-paid" && token && project) {
        constructionApi.get(project.id, token).then(setProject).catch(() => {});
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to update milestone.");
    } finally {
      setMilestoneActionId(null);
    }
  };

  const handlePostUpdate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!token || !project || !updateNote.trim()) return;
    setPostingUpdate(true);
    try {
      const created = await constructionApi.postUpdate(project.id, updateNote.trim(), token);
      setUpdates((prev) => [created, ...prev]);
      setProject((p) => (p ? { ...p, latestUpdateNote: created.note, latestUpdateAt: created.createdAt } : p));
      setUpdateNote("");
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to post update.");
    } finally {
      setPostingUpdate(false);
    }
  };

  if (loading || dataLoading) {
    return (
      <div className="max-w-5xl mx-auto px-4 py-8">
        <div className="h-8 w-64 bg-gray-100 rounded-lg animate-pulse mb-8" />
        <div className="h-36 bg-gray-100 rounded-2xl animate-pulse mb-8" />
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {Array.from({ length: 2 }).map((_, i) => (
            <div key={i} className="h-52 bg-gray-100 rounded-2xl animate-pulse" />
          ))}
        </div>
      </div>
    );
  }

  if (!project) {
    return (
      <div className="text-center py-24 text-gray-500">
        <AlertCircle className="w-12 h-12 mx-auto mb-4 text-red-400" />
        <p className="text-lg font-semibold">{error || "Project not found."}</p>
        <Link href="/construction" className="mt-4 inline-block text-forest-600 hover:underline text-sm">← Back to projects</Link>
      </div>
    );
  }

  const budget = project.budgetTotal ?? 0;
  const paid = project.amountPaid ?? 0;
  const remaining = project.amountRemaining ?? Math.max(0, budget - paid);

  return (
    <div className="max-w-5xl mx-auto px-4 py-8">
      <Link href="/construction" className="text-xs text-forest-600 hover:underline mb-3 inline-block">← All projects</Link>

      <div className="mb-6 flex items-start justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{project.title}</h1>
          <p className="text-gray-500 mt-1 flex items-center gap-1.5 text-sm">
            <MapPin className="w-3.5 h-3.5" /> {project.city}, {project.country}
          </p>
        </div>
        <StatusBadge status={project.status} />
      </div>

      {error && <AlertBanner variant="error" className="mb-5">{error}</AlertBanner>}

      {/* Progress */}
      <div className="bg-white border border-gray-200 rounded-2xl shadow-sm p-6 mb-6">
        <div className="flex items-center justify-between mb-3">
          <h2 className="font-bold text-gray-900">Construction Progress</h2>
          <span className="text-2xl font-bold text-forest-600">{project.progressPercent}%</span>
        </div>
        <ProgressBar percent={project.progressPercent} />
        {project.latestUpdateNote && (
          <p className="text-xs text-gray-500 mt-3">
            Latest update ({project.latestUpdateAt ? new Date(project.latestUpdateAt).toLocaleDateString() : ""}): &ldquo;{project.latestUpdateNote}&rdquo;
          </p>
        )}

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mt-5 pt-5 border-t border-gray-100">
          <div>
            <label className="block text-xs font-medium text-gray-700 mb-1">Current stage</label>
            <select
              value={project.stage}
              onChange={(e) => handleProgressSave(e.target.value as ConstructionStage, project.progressPercent, project.status)}
              disabled={savingProgress}
              className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm outline-none focus:border-forest-500 disabled:opacity-60"
            >
              {STAGES.map((s) => (
                <option key={s} value={s}>{s.replaceAll("_", " ")}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-700 mb-1">Progress %</label>
            <input
              key={project.progressPercent}
              type="number"
              min={0}
              max={100}
              defaultValue={project.progressPercent}
              onBlur={(e) => {
                const val = Number(e.target.value);
                if (val !== project.progressPercent) handleProgressSave(project.stage, val, project.status);
              }}
              disabled={savingProgress}
              className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm outline-none focus:border-forest-500 disabled:opacity-60"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-700 mb-1">Status</label>
            <select
              value={project.status}
              onChange={(e) => handleProgressSave(project.stage, project.progressPercent, e.target.value as ConstructionStatus)}
              disabled={savingProgress}
              className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm outline-none focus:border-forest-500 disabled:opacity-60"
            >
              {PROJECT_STATUSES.map((s) => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
          </div>
        </div>
      </div>

      {/* Budget + contractor */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-6">
        <StatTile icon={Wallet} label="Budget" value={`$${budget.toLocaleString()}`} tone="forest" />
        <StatTile icon={DollarSign} label="Paid So Far" value={`$${paid.toLocaleString()}`} tone="gold" />
        <StatTile icon={DollarSign} label="Remaining" value={`$${remaining.toLocaleString()}`} tone="terracotta" />
        <StatTile icon={HardHat} label="Contractor" value={project.contractorName || "Not set"} tone="gray" />
      </div>

      {project.contractorPhone && (
        <p className="text-xs text-gray-500 flex items-center gap-1.5 mb-6 -mt-3">
          <Phone className="w-3 h-3" /> {project.contractorPhone}
          {project.contractorCompany ? ` · ${project.contractorCompany}` : ""}
        </p>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Milestones */}
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-bold text-gray-900">Milestones</h2>
            <button
              onClick={() => setShowMilestoneForm((s) => !s)}
              className="flex items-center gap-1 text-xs font-semibold text-forest-600 hover:text-forest-700"
            >
              <Plus className="w-3.5 h-3.5" /> Add
            </button>
          </div>

          {showMilestoneForm && (
            <form onSubmit={handleAddMilestone} className="bg-gray-50 rounded-xl p-3 mb-4 space-y-2">
              <input
                required
                value={msTitle}
                onChange={(e) => setMsTitle(e.target.value)}
                placeholder="e.g. Roofing complete"
                className="w-full text-xs border border-gray-200 rounded-lg px-2.5 py-1.5 outline-none focus:border-forest-500"
              />
              <div className="flex gap-2">
                <input
                  type="number"
                  min="0"
                  value={msAmount}
                  onChange={(e) => setMsAmount(e.target.value)}
                  placeholder="Amount due ($)"
                  className="flex-1 text-xs border border-gray-200 rounded-lg px-2.5 py-1.5 outline-none focus:border-forest-500"
                />
                <input
                  type="date"
                  value={msTargetDate}
                  onChange={(e) => setMsTargetDate(e.target.value)}
                  className="flex-1 text-xs border border-gray-200 rounded-lg px-2.5 py-1.5 outline-none focus:border-forest-500"
                />
              </div>
              <button
                type="submit"
                disabled={addingMilestone}
                className="text-xs font-semibold bg-forest-600 hover:bg-forest-700 disabled:opacity-60 text-white px-3 py-1.5 rounded-lg"
              >
                {addingMilestone ? "Adding…" : "Add Milestone"}
              </button>
            </form>
          )}

          {milestones.length === 0 ? (
            <EmptyState icon={HardHat} title="No milestones yet" compact />
          ) : (
            <div className="space-y-3">
              {milestones.map((m) => (
                <div key={m.id} className="p-3 bg-gray-50 rounded-xl">
                  <div className="flex items-center justify-between mb-1">
                    <p className="text-sm font-semibold text-gray-900">{m.title}</p>
                    <StatusBadge status={m.status} />
                  </div>
                  <p className="text-xs text-gray-500">
                    {m.amountDue != null ? `$${m.amountDue.toLocaleString()}` : "No amount set"}
                    {m.targetDate ? ` · due ${m.targetDate}` : ""}
                  </p>
                  <div className="flex gap-2 mt-2">
                    {m.status === "PENDING" && (
                      <button
                        onClick={() => milestoneAction(m.id, "submit")}
                        disabled={milestoneActionId === m.id}
                        className="text-xs font-semibold bg-white border border-gray-200 hover:bg-gray-100 disabled:opacity-60 text-gray-700 px-3 py-1.5 rounded-lg"
                      >
                        Mark Submitted
                      </button>
                    )}
                    {m.status === "SUBMITTED" && (
                      <button
                        onClick={() => milestoneAction(m.id, "approve")}
                        disabled={milestoneActionId === m.id}
                        className="text-xs font-semibold bg-forest-600 hover:bg-forest-700 disabled:opacity-60 text-white px-3 py-1.5 rounded-lg"
                      >
                        Approve
                      </button>
                    )}
                    {m.status === "APPROVED" && (
                      <button
                        onClick={() => milestoneAction(m.id, "mark-paid")}
                        disabled={milestoneActionId === m.id}
                        className="text-xs font-semibold bg-gold-600 hover:bg-gold-700 disabled:opacity-60 text-white px-3 py-1.5 rounded-lg"
                      >
                        Mark Paid
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Updates feed */}
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
          <h2 className="font-bold text-gray-900 mb-4 flex items-center gap-2">
            <MessageSquare className="w-4 h-4 text-gray-500" /> Updates
          </h2>
          <form onSubmit={handlePostUpdate} className="flex gap-2 mb-4">
            <input
              value={updateNote}
              onChange={(e) => setUpdateNote(e.target.value)}
              placeholder="Post a progress note…"
              className="flex-1 text-xs border border-gray-200 rounded-lg px-2.5 py-1.5 outline-none focus:border-forest-500"
            />
            <button
              type="submit"
              disabled={postingUpdate || !updateNote.trim()}
              className="text-xs font-semibold bg-forest-600 hover:bg-forest-700 disabled:opacity-60 text-white px-3 py-1.5 rounded-lg"
            >
              Post
            </button>
          </form>
          {updates.length === 0 ? (
            <EmptyState icon={MessageSquare} title="No updates posted yet" compact />
          ) : (
            <div className="space-y-3">
              {updates.map((u) => (
                <div key={u.id} className="p-3 bg-gray-50 rounded-xl">
                  <p className="text-sm text-gray-700">{u.note}</p>
                  <p className="text-xs text-gray-400 mt-1">
                    {u.postedByName || "Update"} · {new Date(u.createdAt).toLocaleDateString()}
                  </p>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
