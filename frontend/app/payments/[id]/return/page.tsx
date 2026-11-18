"use client";

import { useCallback, useEffect, useState, Suspense } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/lib/auth";
import { payments as paymentsApi } from "@/lib/api";
import type { Payment } from "@/lib/types";
import { CheckCircle, XCircle, Clock, RefreshCw } from "lucide-react";
import PageLoader from "@/components/PageLoader";

function destinationFor(payment: Payment): { label: string; href: string } {
  const purpose = (payment.purpose ?? "").toLowerCase();
  if (purpose.startsWith("stay ")) return { label: "Go to my trips", href: "/trips" };
  if (purpose.includes("escrow")) return { label: "Go to escrow", href: "/escrow" };
  if (purpose.includes("subscription")) return { label: "Go to subscription", href: "/landlord/subscription" };
  if (purpose.includes("featured listing")) return { label: "Go to featured listings", href: "/landlord/featured-listings" };
  if (purpose.includes("investment")) return { label: "Go to investments", href: "/investments" };
  return { label: "View my payments", href: "/payments" };
}

function ReturnContent() {
  const { id } = useParams<{ id: string }>();
  const { user, token, loading: authLoading } = useAuth();
  const router = useRouter();

  const [payment, setPayment] = useState<Payment | null>(null);
  const [checking, setChecking] = useState(true);
  const [error, setError] = useState("");

  const check = useCallback(async () => {
    if (!token) return;
    setChecking(true);
    setError("");
    try {
      const result = await paymentsApi.confirmCheckout(Number(id), token);
      setPayment(result);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not confirm this payment.");
    } finally {
      setChecking(false);
    }
  }, [id, token]);

  useEffect(() => {
    if (!authLoading && !user) {
      router.push(`/login?redirect=${encodeURIComponent(`/payments/${id}/return`)}`);
      return;
    }
    if (user && token) check();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authLoading, user, token]);

  if (authLoading || (checking && !payment)) return <PageLoader />;

  const destination = payment ? destinationFor(payment) : { label: "View my payments", href: "/payments" };

  return (
    <div className="max-w-md mx-auto px-4 py-16 text-center">
      {error && (
        <>
          <XCircle className="w-14 h-14 text-red-500 mx-auto mb-4" />
          <h1 className="text-xl font-bold text-gray-900 mb-2">Couldn&apos;t confirm payment</h1>
          <p className="text-sm text-gray-500 mb-6">{error}</p>
          <button
            onClick={check}
            className="inline-flex items-center gap-1.5 bg-brand-600 hover:bg-brand-700 text-white font-semibold px-5 py-2.5 rounded-xl text-sm transition-colors"
          >
            <RefreshCw className="w-4 h-4" /> Try again
          </button>
        </>
      )}

      {!error && payment?.status === "SUCCESSFUL" && (
        <>
          <CheckCircle className="w-14 h-14 text-brand-600 mx-auto mb-4" />
          <h1 className="text-xl font-bold text-gray-900 mb-2">Payment confirmed</h1>
          <p className="text-sm text-gray-500 mb-6">
            {payment.purpose ?? "Your payment"} for {payment.currency} {payment.amount} was successful.
          </p>
          <Link
            href={destination.href}
            className="inline-block bg-brand-600 hover:bg-brand-700 text-white font-semibold px-5 py-2.5 rounded-xl text-sm transition-colors"
          >
            {destination.label}
          </Link>
        </>
      )}

      {!error && payment?.status === "FAILED" && (
        <>
          <XCircle className="w-14 h-14 text-red-500 mx-auto mb-4" />
          <h1 className="text-xl font-bold text-gray-900 mb-2">Payment didn&apos;t go through</h1>
          <p className="text-sm text-gray-500 mb-6">
            The provider reported this payment as cancelled or failed. No charge was completed.
          </p>
          <Link
            href={destination.href}
            className="inline-block text-brand-600 font-semibold text-sm hover:underline"
          >
            {destination.label}
          </Link>
        </>
      )}

      {!error && payment?.status === "INITIATED" && (
        <>
          <Clock className="w-14 h-14 text-amber-500 mx-auto mb-4" />
          <h1 className="text-xl font-bold text-gray-900 mb-2">Still confirming…</h1>
          <p className="text-sm text-gray-500 mb-6">
            {payment.provider === "paynow"
              ? "Mobile money confirmations can take a minute or two. If you've completed the payment, check again shortly."
              : "We haven't heard back from the payment provider yet. If you completed the payment, check again shortly."}
          </p>
          <button
            onClick={check}
            disabled={checking}
            className="inline-flex items-center gap-1.5 bg-brand-600 hover:bg-brand-700 disabled:opacity-60 text-white font-semibold px-5 py-2.5 rounded-xl text-sm transition-colors"
          >
            <RefreshCw className={`w-4 h-4 ${checking ? "animate-spin" : ""}`} /> Check again
          </button>
        </>
      )}
    </div>
  );
}

export default function PaymentReturnPage() {
  return (
    <Suspense fallback={<PageLoader />}>
      <ReturnContent />
    </Suspense>
  );
}
