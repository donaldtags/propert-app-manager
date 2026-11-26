"use client";

import { useCallback, useEffect, useState } from "react";
import { AlertTriangle, Gift, Loader2 } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { referralsApi, type ReferralAdminView } from "@/lib/api";
import AlertBanner from "@/components/AlertBanner";
import StatusBadge from "@/components/StatusBadge";

function money(cents?: number | null) {
  return cents == null ? "—" : `$${(cents / 100).toLocaleString(undefined, { minimumFractionDigits: 2 })}`;
}

const STATUS_HELP: Record<string, string> = {
  REGISTERED: "Signed up; discount waiting for their first payment",
  BLOCKED: "Looked like self-referral: no discount, no reward",
  REWARDED: "Paid; referrer credited",
  CAPPED: "Paid; referrer was over this month's limit",
  REVERSED: "Reward taken back",
  REVERSAL_PENDING: "Reward should be taken back, but the wallet can't cover it",
};

/**
 * The referral programme for administrators: switch it on, set the discount, reward and monthly
 * limit, and see what it has cost. Finance staff can view; only administrators change anything.
 */
export default function ReferralsPanel({ canEdit }: { canEdit: boolean }) {
  const { token } = useAuth();
  const [view, setView] = useState<ReferralAdminView | null>(null);
  const [form, setForm] = useState<{ enabled: boolean; discountPercent: string; reward: string; monthlyCap: string } | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [saving, setSaving] = useState(false);

  const load = useCallback(() => {
    if (!token) return;
    referralsApi.admin(token)
      .then((v) => {
        setView(v);
        setForm({ enabled: v.settings.enabled, discountPercent: String(v.settings.discountPercent),
          reward: (v.settings.rewardCents / 100).toFixed(2), monthlyCap: String(v.settings.monthlyCap) });
      })
      .catch((e) => setError(e instanceof Error ? e.message : "Couldn't load the referral programme."));
  }, [token]);
  useEffect(load, [load]);

  const save = async () => {
    if (!token || !form) return;
    setSaving(true);
    setError("");
    try {
      await referralsApi.saveSettings(token, {
        enabled: form.enabled, discountPercent: Number(form.discountPercent),
        rewardCents: Math.round(Number(form.reward) * 100), monthlyCap: Number(form.monthlyCap),
      });
      setNotice("Saved. Changes apply to checkouts and rewards from now on.");
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Couldn't save.");
    } finally {
      setSaving(false);
    }
  };

  const reverse = async (id: number) => {
    if (!token) return;
    const reason = window.prompt("Why is this reward being taken back? (e.g. 'Payment refunded by EFT on 3 Dec')");
    if (!reason) return;
    try {
      setView(await referralsApi.reverse(token, id, reason));
      setNotice("Reward reversed. If the wallet couldn't cover it, it's marked for follow-up.");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Couldn't reverse the reward.");
    }
  };

  if (!view || !form) {
    return error ? <AlertBanner variant="error">{error}</AlertBanner>
      : <p className="flex items-center gap-2 text-gray-500"><Loader2 className="w-4 h-4 animate-spin" /> Loading…</p>;
  }
  const input = "w-24 border border-gray-200 rounded-lg px-2.5 py-1.5 text-sm disabled:bg-gray-50";
  return (
    <section className="space-y-5" aria-labelledby="ref-title">
      <div>
        <h2 id="ref-title" className="text-lg font-bold text-gray-900 flex items-center gap-2"><Gift className="w-5 h-5 text-brand-600" aria-hidden /> Referral programme</h2>
        <p className="text-sm text-gray-500 mt-1 max-w-2xl">
          A friend who signs up with someone&apos;s link gets a discount on their first subscription payment; the person who invited them gets wallet
          credit when that payment succeeds. Self-referrals are blocked, rewards are capped per month, and every change is audited.
        </p>
      </div>
      {error && <AlertBanner variant="error">{error}</AlertBanner>}
      {notice && <AlertBanner variant="success">{notice}</AlertBanner>}

      <div className="bg-white border border-gray-200 rounded-2xl p-4 flex flex-wrap items-end gap-4">
        <label className="flex items-center gap-2 text-sm font-medium text-gray-800">
          <input type="checkbox" checked={form.enabled} disabled={!canEdit} onChange={(e) => setForm({ ...form, enabled: e.target.checked })}
            className="w-4 h-4 accent-brand-600" /> Programme on
        </label>
        <label className="text-sm text-gray-600">Discount for the friend (%)<br />
          <input className={input} inputMode="numeric" value={form.discountPercent} disabled={!canEdit}
            onChange={(e) => setForm({ ...form, discountPercent: e.target.value })} />
        </label>
        <label className="text-sm text-gray-600">Reward for the referrer (USD)<br />
          <input className={input} inputMode="decimal" value={form.reward} disabled={!canEdit}
            onChange={(e) => setForm({ ...form, reward: e.target.value })} />
        </label>
        <label className="text-sm text-gray-600">Rewards per referrer per month<br />
          <input className={input} inputMode="numeric" value={form.monthlyCap} disabled={!canEdit}
            onChange={(e) => setForm({ ...form, monthlyCap: e.target.value })} />
        </label>
        {canEdit && (
          <button type="button" onClick={save} disabled={saving}
            className="bg-brand-600 hover:bg-brand-700 disabled:opacity-60 text-white text-sm font-semibold px-4 py-2 rounded-lg">
            {saving ? "Saving…" : "Save"}
          </button>
        )}
      </div>

      <dl className="grid grid-cols-2 lg:grid-cols-6 gap-3">
        {[["Referred sign-ups", String(view.total)], ["Blocked (self-referral)", String(view.blocked)], ["Rewarded", String(view.rewarded)],
          ["Rewards paid", money(view.rewardsPaidCents)], ["Discounts given", money(view.discountsGivenCents)],
          ["Reversals to follow up", String(view.pendingReversals)]].map(([k, v]) => (
          <div key={k} className="bg-white border border-gray-200 rounded-xl p-3">
            <dt className="text-xs text-gray-500">{k}</dt>
            <dd className="text-lg font-semibold text-gray-900 tabular-nums flex items-center gap-1">
              {k.startsWith("Reversals") && view.pendingReversals > 0 && <AlertTriangle className="w-4 h-4 text-amber-600" aria-hidden />}{v}
            </dd>
          </div>
        ))}
      </dl>

      <div className="bg-white border border-gray-200 rounded-2xl overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="text-xs text-gray-500 text-left border-b border-gray-100">
            <tr>
              <th scope="col" className="px-4 py-2 font-medium">Signed up</th><th scope="col" className="px-2 py-2 font-medium">Invited by</th>
              <th scope="col" className="px-2 py-2 font-medium">New member</th><th scope="col" className="px-2 py-2 font-medium">Status</th>
              <th scope="col" className="px-2 py-2 font-medium text-right">Discount</th><th scope="col" className="px-2 py-2 font-medium text-right">Reward</th>
              <th scope="col" className="px-4 py-2"><span className="sr-only">Actions</span></th>
            </tr>
          </thead>
          <tbody>
            {view.recent.length === 0 && <tr><td colSpan={7} className="px-4 py-4 text-gray-400">No referrals yet.</td></tr>}
            {view.recent.map((r) => (
              <tr key={r.id} className="border-b border-gray-50 align-top">
                <td className="px-4 py-2 whitespace-nowrap text-gray-600">{new Date(r.registeredAt).toLocaleDateString()}</td>
                <td className="px-2 py-2">{r.referrerName ?? `#${r.referrerUserId}`}<span className="block text-xs text-gray-400 font-mono">{r.code}</span></td>
                <td className="px-2 py-2">{r.refereeName ?? `#${r.refereeUserId}`}</td>
                <td className="px-2 py-2">
                  <StatusBadge status={r.status} />
                  <span className="block text-xs text-gray-500 mt-0.5">{r.blockReason ?? r.reversalReason ?? STATUS_HELP[r.status]}</span>
                </td>
                <td className="px-2 py-2 text-right tabular-nums">{money(r.discountCents)}</td>
                <td className="px-2 py-2 text-right tabular-nums">{money(r.rewardCents)}</td>
                <td className="px-4 py-2 text-right">
                  {canEdit && (r.status === "REWARDED" || r.status === "REVERSAL_PENDING") && (
                    <button type="button" onClick={() => reverse(r.id)} className="text-xs text-red-700 hover:underline">
                      {r.status === "REVERSAL_PENDING" ? "Try again" : "Reverse"}
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
