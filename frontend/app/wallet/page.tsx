"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  ArrowDownLeft, ArrowUpRight, CreditCard, Landmark, Loader2, ShieldCheck, Smartphone, Undo2, Wallet,
} from "lucide-react";
import { useAuth } from "@/lib/auth";
import { wallet as walletApi } from "@/lib/api";
import type { WalletEntry, WalletSummary, WalletWithdrawal, WithdrawalMethod } from "@/lib/types";
import PageLoader from "@/components/PageLoader";

const INPUT =
  "mt-1 w-full border border-gray-200 rounded-xl px-4 py-2.5 text-base sm:text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100";
const LABEL = "block text-sm font-medium text-gray-700";

/** What each kind of movement means, in everyday words. */
const ENTRY_LABEL: Record<WalletEntry["type"], string> = {
  TOP_UP: "Money added",
  PAYMENT: "Paid",
  PAYOUT: "Money received",
  WITHDRAWAL: "Withdrawal",
  WITHDRAWAL_REVERSAL: "Withdrawal returned",
  FEE: "Fee",
  ADJUSTMENT: "Adjustment",
};

const WITHDRAWAL_STATUS: Record<WalletWithdrawal["status"], { label: string; cls: string }> = {
  REQUESTED: { label: "Processing", cls: "bg-amber-50 text-amber-700 border-amber-200" },
  PAID: { label: "Sent", cls: "bg-green-50 text-green-700 border-green-200" },
  REJECTED: { label: "Returned to wallet", cls: "bg-gray-50 text-gray-700 border-gray-200" },
};

const money = (n: number) => `$${Math.abs(n).toFixed(2)}`;
const when = (iso: string) =>
  new Date(iso).toLocaleString("en-GB", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });

export default function WalletPage() {
  const { user, token, loading } = useAuth();
  const router = useRouter();
  const [summary, setSummary] = useState<WalletSummary | null>(null);
  const [withdrawals, setWithdrawals] = useState<WalletWithdrawal[]>([]);
  const [page, setPage] = useState(0);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [panel, setPanel] = useState<"none" | "topup" | "withdraw">("none");

  useEffect(() => {
    if (!loading && !user) router.push("/login?redirect=/wallet");
  }, [user, loading, router]);

  const load = useCallback(
    (p = 0) => {
      if (!token) return;
      walletApi.get(token, p, 20).then((s) => {
        setSummary(s);
        setPage(p);
      }).catch(() => setError("Couldn't load your wallet. Check your connection and refresh."));
      walletApi.withdrawals(token).then(setWithdrawals).catch(() => {});
    },
    [token]
  );
  useEffect(() => load(0), [load]);

  if (loading || !user || (!summary && !error)) return <PageLoader />;

  return (
    <div className="max-w-2xl mx-auto w-full px-4 py-8 space-y-6">
      <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
        <Wallet className="w-6 h-6 text-brand-600" /> My wallet
      </h1>

      {error && <p className="text-sm text-red-600" role="alert">{error}</p>}
      {notice && <p className="text-sm text-green-700 bg-green-50 border border-green-200 rounded-xl px-4 py-3" role="status">{notice}</p>}

      {summary && (
        <section className="bg-brand-600 text-white rounded-2xl p-6 shadow-sm">
          <p className="text-sm text-white/80">Available balance</p>
          <p className="text-4xl font-bold mt-1">{money(summary.balance)}</p>
          {summary.pendingWithdrawals > 0 && (
            <p className="text-sm text-white/80 mt-1">{money(summary.pendingWithdrawals)} on its way to you</p>
          )}
          <div className="grid grid-cols-2 gap-3 mt-5">
            <button
              onClick={() => { setPanel(panel === "topup" ? "none" : "topup"); setNotice(""); }}
              className="flex items-center justify-center gap-2 bg-white text-brand-700 font-semibold rounded-xl py-3 min-h-11"
            >
              <ArrowDownLeft className="w-5 h-5" /> Add money
            </button>
            <button
              onClick={() => { setPanel(panel === "withdraw" ? "none" : "withdraw"); setNotice(""); }}
              className="flex items-center justify-center gap-2 bg-white/15 hover:bg-white/25 border border-white/40 font-semibold rounded-xl py-3 min-h-11"
            >
              <ArrowUpRight className="w-5 h-5" /> Withdraw
            </button>
          </div>
        </section>
      )}

      {panel === "topup" && token && <TopUpForm token={token} onError={setError} />}
      {panel === "withdraw" && token && summary && (
        <WithdrawForm
          token={token}
          balance={summary.balance}
          identityVerified={!!user.identityVerified}
          onDone={(w) => {
            setPanel("none");
            setNotice(`Withdrawal of ${money(w.amount)} requested. We'll send it within 1–2 working days and let you know.`);
            load(0);
          }}
        />
      )}

      {withdrawals.length > 0 && (
        <section>
          <h2 className="text-lg font-semibold text-gray-900 mb-2">Withdrawals</h2>
          <ul className="bg-white border border-gray-200 rounded-2xl divide-y divide-gray-100">
            {withdrawals.slice(0, 5).map((w) => (
              <li key={w.id} className="px-4 py-3 flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="text-sm font-medium text-gray-900">
                    {w.method === "ECOCASH" ? `EcoCash ${w.ecocashPhone}` : `${w.bankName} ••${(w.bankAccountNumber ?? "").slice(-4)}`}
                  </p>
                  <p className="text-xs text-gray-500">{when(w.requestedAt)}</p>
                  {w.payoutReference && <p className="text-xs text-gray-500">Reference: {w.payoutReference}</p>}
                  {w.rejectReason && <p className="text-xs text-gray-600">Reason: {w.rejectReason}</p>}
                </div>
                <div className="text-right shrink-0">
                  <p className="text-sm font-semibold text-gray-900">{money(w.amount)}</p>
                  <span className={`inline-block mt-1 text-xs font-medium border rounded-full px-2 py-0.5 whitespace-nowrap ${WITHDRAWAL_STATUS[w.status].cls}`}>
                    {WITHDRAWAL_STATUS[w.status].label}
                  </span>
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}

      {summary && (
        <section>
          <h2 className="text-lg font-semibold text-gray-900 mb-2">History</h2>
          {summary.entries.length === 0 ? (
            <div className="bg-white border border-gray-200 rounded-2xl p-6 text-center text-sm text-gray-500">
              Nothing here yet. Add money, or receive rent, stay payments and REIT sales straight into your wallet.
            </div>
          ) : (
            <ul className="bg-white border border-gray-200 rounded-2xl divide-y divide-gray-100">
              {summary.entries.map((e) => (
                <li key={e.id} className="px-4 py-3 flex items-start justify-between gap-3">
                  <div className="flex items-start gap-3 min-w-0">
                    <span className={`mt-0.5 w-8 h-8 shrink-0 rounded-full flex items-center justify-center ${e.amount >= 0 ? "bg-green-50 text-green-700" : "bg-gray-100 text-gray-600"}`}>
                      {e.type === "WITHDRAWAL_REVERSAL" ? <Undo2 className="w-4 h-4" /> : e.amount >= 0 ? <ArrowDownLeft className="w-4 h-4" /> : <ArrowUpRight className="w-4 h-4" />}
                    </span>
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-gray-900">{ENTRY_LABEL[e.type] ?? e.type}</p>
                      <p className="text-xs text-gray-500 break-words">{e.description}</p>
                      <p className="text-xs text-gray-400">{when(e.createdAt)}</p>
                    </div>
                  </div>
                  <p className={`text-sm font-semibold shrink-0 ${e.amount >= 0 ? "text-green-700" : "text-gray-900"}`}>
                    {e.amount >= 0 ? "+" : "−"}{money(e.amount)}
                  </p>
                </li>
              ))}
            </ul>
          )}
          {summary.totalEntries > summary.size && (
            <div className="flex justify-between mt-3">
              <button disabled={page === 0} onClick={() => load(page - 1)} className="text-sm font-medium text-brand-600 disabled:text-gray-300 px-3 py-2">
                ← Newer
              </button>
              <button
                disabled={(page + 1) * summary.size >= summary.totalEntries}
                onClick={() => load(page + 1)}
                className="text-sm font-medium text-brand-600 disabled:text-gray-300 px-3 py-2"
              >
                Older →
              </button>
            </div>
          )}
        </section>
      )}

      <p className="text-xs text-gray-500 flex items-start gap-1.5">
        <ShieldCheck className="w-4 h-4 shrink-0" />
        Money in your wallet is held by EstateX for you. Every movement is recorded and can&apos;t be changed afterwards.
      </p>
    </div>
  );
}

function TopUpForm({ token, onError }: { token: string; onError: (m: string) => void }) {
  const [amount, setAmount] = useState("");
  const [busy, setBusy] = useState<"paynow" | "stripe" | null>(null);

  const start = async (provider: "paynow" | "stripe") => {
    const n = Number(amount);
    if (!n || n < 1) {
      onError("Enter how much to add (at least $1).");
      return;
    }
    onError("");
    setBusy(provider);
    try {
      const checkout = await walletApi.topUp(n, provider, token);
      window.location.href = checkout.redirectUrl;
    } catch (e) {
      onError(e instanceof Error ? e.message : "Couldn't start the top-up.");
      setBusy(null);
    }
  };

  return (
    <section className="bg-white border border-gray-200 rounded-2xl p-5 space-y-4">
      <h2 className="text-lg font-semibold text-gray-900">Add money</h2>
      <label className={LABEL}>
        Amount (USD)
        <input
          type="number"
          inputMode="decimal"
          min={1}
          max={5000}
          step="0.01"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          placeholder="e.g. 100"
          className={INPUT}
        />
      </label>
      <div className="flex flex-wrap gap-2">
        {[20, 50, 100, 500].map((v) => (
          <button key={v} type="button" onClick={() => setAmount(String(v))} className="text-sm border border-gray-200 rounded-full px-3 py-1.5 hover:border-brand-400">
            ${v}
          </button>
        ))}
      </div>
      <p className="text-sm text-gray-600">Pay with:</p>
      <div className="grid sm:grid-cols-2 gap-2">
        <button type="button" onClick={() => start("paynow")} disabled={!!busy} className="flex items-center gap-3 border border-gray-200 hover:border-brand-400 rounded-xl px-4 py-3 text-left disabled:opacity-60">
          {busy === "paynow" ? <Loader2 className="w-5 h-5 animate-spin text-brand-600" /> : <Landmark className="w-5 h-5 text-brand-600" />}
          <span>
            <span className="block text-sm font-semibold text-gray-900">Paynow</span>
            <span className="block text-xs text-gray-500">EcoCash, OneMoney, bank</span>
          </span>
        </button>
        <button type="button" onClick={() => start("stripe")} disabled={!!busy} className="flex items-center gap-3 border border-gray-200 hover:border-brand-400 rounded-xl px-4 py-3 text-left disabled:opacity-60">
          {busy === "stripe" ? <Loader2 className="w-5 h-5 animate-spin text-brand-600" /> : <CreditCard className="w-5 h-5 text-brand-600" />}
          <span>
            <span className="block text-sm font-semibold text-gray-900">Card</span>
            <span className="block text-xs text-gray-500">Visa, Mastercard</span>
          </span>
        </button>
      </div>
      <p className="text-xs text-gray-500">Your balance goes up as soon as the payment is confirmed.</p>
    </section>
  );
}

function WithdrawForm({
  token, balance, identityVerified, onDone,
}: {
  token: string;
  balance: number;
  identityVerified: boolean;
  onDone: (w: WalletWithdrawal) => void;
}) {
  const [amount, setAmount] = useState("");
  const [method, setMethod] = useState<WithdrawalMethod>("ECOCASH");
  const [phone, setPhone] = useState("");
  const [accountName, setAccountName] = useState("");
  const [accountNumber, setAccountNumber] = useState("");
  const [bankName, setBankName] = useState("");
  const [branch, setBranch] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  if (!identityVerified) {
    return (
      <section className="bg-amber-50 border border-amber-200 rounded-2xl p-5 text-sm text-amber-900">
        <p className="font-semibold mb-1">Verify your identity first</p>
        <p>To keep your money safe, withdrawals need a verified ID. It takes a few minutes to submit and usually a day to approve.</p>
        <Link href="/verification" className="inline-block mt-3 font-semibold text-brand-700 underline">Verify my identity</Link>
      </section>
    );
  }

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      const w = await walletApi.withdraw({
        amount: Number(amount),
        method,
        ecocashPhone: method === "ECOCASH" ? phone : undefined,
        bankAccountName: method === "BANK" ? accountName : undefined,
        bankAccountNumber: method === "BANK" ? accountNumber : undefined,
        bankName: method === "BANK" ? bankName : undefined,
        bankBranch: method === "BANK" && branch ? branch : undefined,
        password,
      }, token);
      onDone(w);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Couldn't request the withdrawal.");
    } finally {
      setBusy(false);
      setPassword("");
    }
  };

  return (
    <form onSubmit={submit} className="bg-white border border-gray-200 rounded-2xl p-5 space-y-4">
      <h2 className="text-lg font-semibold text-gray-900">Withdraw</h2>
      <label className={LABEL}>
        Amount (USD) <span className="font-normal text-gray-500">· you have {money(balance)}</span>
        <input type="number" inputMode="decimal" min={5} max={balance} step="0.01" required value={amount}
          onChange={(e) => setAmount(e.target.value)} placeholder="Minimum $5" className={INPUT} />
      </label>

      <fieldset>
        <legend className={LABEL}>Send it to</legend>
        <div className="grid grid-cols-2 gap-2 mt-1">
          {([
            { v: "ECOCASH", label: "EcoCash", icon: Smartphone },
            { v: "BANK", label: "Bank account", icon: Landmark },
          ] as const).map(({ v, label, icon: Icon }) => (
            <button key={v} type="button" aria-pressed={method === v} onClick={() => setMethod(v)}
              className={`flex items-center justify-center gap-2 rounded-xl border py-3 min-h-11 text-sm font-medium ${method === v ? "bg-brand-600 border-brand-600 text-white" : "border-gray-200 text-gray-700"}`}>
              <Icon className="w-4 h-4" /> {label}
            </button>
          ))}
        </div>
      </fieldset>

      {method === "ECOCASH" ? (
        <label className={LABEL}>
          EcoCash number
          <input type="tel" autoComplete="tel" required value={phone} onChange={(e) => setPhone(e.target.value)}
            placeholder="e.g. 0771 234 567" className={INPUT} />
        </label>
      ) : (
        <>
          <label className={LABEL}>
            Account holder&apos;s name
            <input type="text" required value={accountName} onChange={(e) => setAccountName(e.target.value)} placeholder="As shown at the bank" className={INPUT} />
          </label>
          <label className={LABEL}>
            Account number
            <input type="text" inputMode="numeric" required value={accountNumber} onChange={(e) => setAccountNumber(e.target.value)} className={INPUT} />
          </label>
          <div className="grid sm:grid-cols-2 gap-4">
            <label className={LABEL}>
              Bank
              <input type="text" required value={bankName} onChange={(e) => setBankName(e.target.value)} placeholder="e.g. CBZ" className={INPUT} />
            </label>
            <label className={LABEL}>
              Branch <span className="font-normal text-gray-500">(optional)</span>
              <input type="text" value={branch} onChange={(e) => setBranch(e.target.value)} className={INPUT} />
            </label>
          </div>
        </>
      )}

      <label className={LABEL}>
        Your EstateX password
        <input type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} className={INPUT} />
        <span className="block text-xs font-normal text-gray-500 mt-1">We ask again to make sure it&apos;s really you.</span>
      </label>

      {error && <p className="text-sm text-red-600" role="alert">{error}</p>}
      <button type="submit" disabled={busy} className="w-full bg-brand-600 hover:bg-brand-700 disabled:opacity-60 text-white font-semibold py-3 rounded-xl flex items-center justify-center gap-2">
        {busy && <Loader2 className="w-4 h-4 animate-spin" />} Request withdrawal
      </button>
      <p className="text-xs text-gray-500">The amount leaves your balance now and is sent within 1–2 working days. If we can&apos;t send it, it comes straight back.</p>
    </form>
  );
}
