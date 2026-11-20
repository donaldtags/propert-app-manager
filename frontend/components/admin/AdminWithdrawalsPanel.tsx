"use client";

import { useCallback, useEffect, useState } from "react";
import { Banknote, Loader2 } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { wallet as walletApi } from "@/lib/api";
import type { WalletWithdrawal, WithdrawalStatus } from "@/lib/types";

const FILTERS: { value: WithdrawalStatus | ""; label: string }[] = [
  { value: "REQUESTED", label: "To pay" },
  { value: "PAID", label: "Paid" },
  { value: "REJECTED", label: "Rejected" },
  { value: "", label: "All" },
];

/**
 * Finance queue: send each requested withdrawal by EcoCash or bank transfer outside EstateX,
 * then record the reference here. Rejecting returns the money to the user's wallet.
 */
export default function AdminWithdrawalsPanel() {
  const { token } = useAuth();
  const [filter, setFilter] = useState<WithdrawalStatus | "">("REQUESTED");
  const [items, setItems] = useState<WalletWithdrawal[] | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const load = useCallback(() => {
    if (!token) return;
    walletApi.admin
      .withdrawals(token, filter || undefined)
      .then(setItems)
      .catch((e) => setError(e instanceof Error ? e.message : "Couldn't load withdrawals."));
  }, [token, filter]);
  useEffect(load, [load]);

  const decide = async (w: WalletWithdrawal, action: "PAID" | "REJECTED") => {
    if (!token) return;
    const input =
      action === "PAID"
        ? window.prompt(`Sent $${w.amount.toFixed(2)} to ${destination(w)}? Enter the EcoCash or bank reference:`)
        : window.prompt("Why is this withdrawal rejected? The user sees this, and the money goes back to their wallet.");
    if (!input || !input.trim()) return;
    setBusyId(w.id);
    setError("");
    setSuccess("");
    try {
      await walletApi.admin.decide(w.id, action === "PAID" ? { action, reference: input.trim() } : { action, reason: input.trim() }, token);
      setSuccess(action === "PAID" ? "Marked as paid." : "Rejected; the money is back in the user's wallet.");
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Couldn't update the withdrawal.");
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-lg font-semibold text-gray-900 flex items-center gap-2">
          <Banknote className="w-5 h-5 text-brand-600" /> Wallet withdrawals
        </h2>
        <p className="text-sm text-gray-500 mt-1">
          Send the money by EcoCash or bank transfer, then record the reference. Rejecting returns the money to the user.
        </p>
      </div>

      <div className="flex flex-wrap gap-2">
        {FILTERS.map((f) => (
          <button
            key={f.label}
            onClick={() => setFilter(f.value)}
            className={`text-sm rounded-full px-3 py-1.5 border ${filter === f.value ? "bg-brand-600 border-brand-600 text-white" : "border-gray-200 text-gray-700"}`}
          >
            {f.label}
          </button>
        ))}
      </div>

      {error && <p className="text-sm text-red-600" role="alert">{error}</p>}
      {success && <p className="text-sm text-green-700" role="status">{success}</p>}

      <div className="bg-white border border-gray-200 rounded-2xl shadow-sm overflow-x-auto">
        {items === null ? (
          <div className="p-6 text-sm text-gray-500">Loading…</div>
        ) : items.length === 0 ? (
          <div className="p-10 text-center text-sm text-gray-500">No withdrawals here.</div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-gray-500 uppercase tracking-wide bg-gray-50">
                <th className="px-4 py-2.5 font-medium">Requested</th>
                <th className="px-4 py-2.5 font-medium">User</th>
                <th className="px-4 py-2.5 font-medium">Send to</th>
                <th className="px-4 py-2.5 font-medium text-right">Amount</th>
                <th className="px-4 py-2.5 font-medium text-right">Status / actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {items.map((w) => (
                <tr key={w.id}>
                  <td className="px-4 py-2.5 text-gray-600 whitespace-nowrap">{new Date(w.requestedAt).toLocaleString("en-GB")}</td>
                  <td className="px-4 py-2.5">
                    <p className="font-medium text-gray-900">{w.userName}</p>
                    <p className="text-xs text-gray-500">{w.userEmail}</p>
                  </td>
                  <td className="px-4 py-2.5 text-gray-700">
                    {destination(w)}
                    {w.method === "BANK" && w.bankAccountName && <p className="text-xs text-gray-500">{w.bankAccountName}{w.bankBranch ? ` · ${w.bankBranch}` : ""}</p>}
                  </td>
                  <td className="px-4 py-2.5 text-right font-semibold whitespace-nowrap">${w.amount.toFixed(2)}</td>
                  <td className="px-4 py-2.5 text-right whitespace-nowrap">
                    {w.status === "REQUESTED" ? (
                      busyId === w.id ? (
                        <Loader2 className="w-4 h-4 animate-spin inline" />
                      ) : (
                        <>
                          <button onClick={() => decide(w, "PAID")} className="text-xs font-medium text-brand-600 hover:text-brand-700 px-2 py-1">Mark paid</button>
                          <button onClick={() => decide(w, "REJECTED")} className="text-xs font-medium text-red-600 hover:text-red-700 px-2 py-1">Reject</button>
                        </>
                      )
                    ) : (
                      <span className="text-xs text-gray-600">
                        {w.status === "PAID" ? `Paid · ${w.payoutReference}` : `Rejected · ${w.rejectReason}`}
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

function destination(w: WalletWithdrawal) {
  return w.method === "ECOCASH"
    ? `EcoCash ${w.ecocashPhone}`
    : `${w.bankName} ${w.bankAccountNumber}`;
}
