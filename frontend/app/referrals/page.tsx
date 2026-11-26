"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Check, Copy, Gift, MessageCircle } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { referralsApi, type MyReferrals } from "@/lib/api";
import PageLoader from "@/components/PageLoader";
import AlertBanner from "@/components/AlertBanner";

function money(cents: number) {
  return `$${(cents / 100).toLocaleString(undefined, { minimumFractionDigits: cents % 100 ? 2 : 0 })}`;
}

/** Invite friends: your link, what they get, what you get, and how it's going. */
export default function ReferralsPage() {
  const { user, token, loading } = useAuth();
  const router = useRouter();
  const [data, setData] = useState<MyReferrals | null>(null);
  const [error, setError] = useState("");
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!loading && !user) router.push("/login?redirect=%2Freferrals");
  }, [loading, user, router]);
  useEffect(() => {
    if (!token) return;
    referralsApi.mine(token).then(setData).catch((e) => setError(e instanceof Error ? e.message : "Couldn't load your referrals."));
  }, [token]);

  if (loading || !user) return <PageLoader />;
  const link = data && typeof window !== "undefined" ? `${window.location.origin}/r/${data.code}` : "";
  const p = data?.programme;
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(link);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setError("Couldn't copy. Select the link and copy it yourself.");
    }
  };
  const message = p?.enabled
    ? `Join me on EstateX. Sign up with my link and get ${p.discountPercent}% off your first subscription: ${link}`
    : `Join me on EstateX: ${link}`;

  return (
    <div className="max-w-2xl mx-auto px-4 py-10 space-y-6">
      <header>
        <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
          <Gift className="w-6 h-6 text-brand-600" aria-hidden /> Invite friends
        </h1>
        {p?.enabled ? (
          <p className="text-gray-600 mt-1">
            Friends who sign up with your link get <strong>{p.discountPercent}% off</strong> their first subscription payment.
            When they pay, you get <strong>{money(p.rewardCents)}</strong> in your EstateX wallet.
          </p>
        ) : (
          <p className="text-gray-600 mt-1">The referral programme isn&apos;t running right now, but your link still records who you invite.</p>
        )}
      </header>
      {error && <AlertBanner variant="error">{error}</AlertBanner>}
      {!data ? (
        <p className="text-sm text-gray-500">Loading…</p>
      ) : (
        <>
          <section className="bg-white border border-gray-200 rounded-2xl p-5 space-y-3">
            <label htmlFor="ref-link" className="text-sm font-medium text-gray-700">Your link</label>
            <div className="flex gap-2">
              <input id="ref-link" readOnly value={link} onFocus={(e) => e.currentTarget.select()}
                className="flex-1 min-w-0 border border-gray-200 rounded-xl px-3 py-2.5 text-sm bg-gray-50" />
              <button type="button" onClick={copy}
                className="inline-flex items-center gap-1.5 bg-brand-600 hover:bg-brand-700 text-white text-sm font-semibold px-4 rounded-xl">
                {copied ? <Check className="w-4 h-4" aria-hidden /> : <Copy className="w-4 h-4" aria-hidden />} {copied ? "Copied" : "Copy"}
              </button>
            </div>
            <p className="text-sm text-gray-500">
              Or share your code: <span className="font-mono font-semibold tracking-widest text-gray-900">{data.code}</span>
            </p>
            <a href={`https://wa.me/?text=${encodeURIComponent(message)}`} target="_blank" rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 text-sm font-medium text-emerald-700 hover:underline">
              <MessageCircle className="w-4 h-4" aria-hidden /> Share on WhatsApp
            </a>
          </section>
          <dl className="grid grid-cols-3 gap-3">
            {([["Signed up", String(data.invited)], ["Subscribed", String(data.subscribed)], ["You've earned", money(data.earnedCents)]] as const).map(([k, v]) => (
              <div key={k} className="bg-white border border-gray-200 rounded-2xl p-4">
                <dt className="text-xs text-gray-500">{k}</dt>
                <dd className="text-2xl font-semibold text-gray-900 tabular-nums">{v}</dd>
              </div>
            ))}
          </dl>
          {data.pending > 0 && (
            <p className="text-sm text-gray-600">
              {data.pending} friend{data.pending === 1 ? " hasn't" : "s haven't"} subscribed yet. You&apos;re rewarded when they make their first payment.
            </p>
          )}
          {data.referredBy?.discountAvailable && (
            <AlertBanner variant="success">You were invited by a friend: {data.referredBy.discountPercent}% off your first subscription payment.</AlertBanner>
          )}
          <p className="text-xs text-gray-400 leading-relaxed">
            Rewards are paid once per friend, on their first subscription payment, up to {p?.monthlyCap ?? "a set number of"} rewards a month.
            Inviting yourself from another account doesn&apos;t count. If a payment is refunded, its reward is taken back.
          </p>
        </>
      )}
    </div>
  );
}
