"use client";

import { Suspense, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import EstateXLogo from "@/components/EstateXLogo";
import { CheckCircle, Eye, EyeOff } from "lucide-react";
import { auth } from "@/lib/api";
import { cleanCode, looksLikePhone } from "@/lib/device";
import PageLoader from "@/components/PageLoader";

const field =
  "w-full border border-gray-200 rounded-xl px-4 py-3 text-base sm:text-sm text-gray-900 outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 transition-colors";

/**
 * Email address -> we email a link (it comes back here with ?token=...).
 * Phone number  -> we text a 6-digit code, typed in below with the new password.
 */
function ForgotPassword() {
  const token = useSearchParams().get("token");
  const [identifier, setIdentifier] = useState("");
  const [stage, setStage] = useState<"ask" | "emailSent" | "code" | "done">("ask");
  const [code, setCode] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [showPw, setShowPw] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const request = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      await auth.forgotPassword(identifier.trim());
    } catch {
      // The answer is the same whether or not the account exists.
    }
    setLoading(false);
    setStage(looksLikePhone(identifier) ? "code" : "emailSent");
  };

  const reset = async (e: React.FormEvent) => {
    e.preventDefault();
    if (password !== confirm) {
      setError("The two passwords don't match.");
      return;
    }
    setError("");
    setLoading(true);
    try {
      await auth.resetPassword(token ? { token, password } : { identifier: identifier.trim(), code: cleanCode(code, "SMS"), password });
      setStage("done");
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "We couldn't reset your password. Try again.");
    } finally {
      setLoading(false);
    }
  };

  const passwordFields = (
    <>
      <div>
        <label htmlFor="new-password" className="block text-sm font-medium text-gray-700 mb-1.5">New password</label>
        <div className="relative">
          <input id="new-password" type={showPw ? "text" : "password"} value={password} onChange={(e) => setPassword(e.target.value)}
            required autoComplete="new-password" className={`${field} pr-12`} />
          <button type="button" onClick={() => setShowPw(!showPw)} aria-label={showPw ? "Hide password" : "Show password"}
            className="absolute right-1 top-1/2 -translate-y-1/2 w-11 h-11 flex items-center justify-center text-gray-400 hover:text-gray-600">
            {showPw ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
          </button>
        </div>
        <p className="text-xs text-gray-500 mt-1.5">At least 10 characters, with a capital letter, a number and a symbol (e.g. ! or #).</p>
      </div>
      <div>
        <label htmlFor="confirm-password" className="block text-sm font-medium text-gray-700 mb-1.5">Type it again</label>
        <input id="confirm-password" type={showPw ? "text" : "password"} value={confirm} onChange={(e) => setConfirm(e.target.value)}
          required autoComplete="new-password" className={field} />
      </div>
    </>
  );

  const showNewPasswordForm = token || stage === "code";
  const subtitle = token
    ? "Choose a new password"
    : stage === "code"
      ? "Enter the code we texted you"
      : "We'll email you a link, or text you a code";

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4 py-10">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <Link href="/" className="inline-flex items-center mb-6">
            <EstateXLogo variant="full" className="h-32 sm:h-40 mx-auto" />
          </Link>
          <h1 className="text-2xl font-bold text-gray-900">Reset your password</h1>
          <p className="text-gray-500 mt-1">{subtitle}</p>
        </div>

        <div className="bg-white rounded-2xl shadow-sm border border-gray-200 p-6 sm:p-8">
          {error && (
            <div role="alert" className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-5">{error}</div>
          )}

          {stage === "done" ? (
            <div className="text-center py-4">
              <CheckCircle className="w-14 h-14 text-brand-500 mx-auto mb-4" />
              <p className="text-gray-900 font-semibold">Your password has been changed</p>
              <p className="text-gray-500 text-sm mt-2">For your safety, every device was signed out. Sign in with your new password.</p>
              <Link href="/login" className="mt-6 inline-block bg-brand-600 hover:bg-brand-700 text-white font-semibold px-6 py-3 rounded-xl text-sm">
                Sign in
              </Link>
            </div>
          ) : stage === "emailSent" ? (
            <div className="text-center py-4">
              <CheckCircle className="w-14 h-14 text-brand-500 mx-auto mb-4" />
              <p className="text-gray-900 font-semibold">Check your email</p>
              <p className="text-gray-500 text-sm mt-2">
                If an account uses this email, we&apos;ve sent a link to reset your password. It works for 30 minutes.
              </p>
              <Link href="/login" className="mt-6 inline-block text-brand-600 font-semibold hover:underline text-sm">Back to Sign In</Link>
            </div>
          ) : showNewPasswordForm ? (
            <form onSubmit={reset} className="space-y-5">
              {!token && (
                <div>
                  <label htmlFor="reset-code" className="block text-sm font-medium text-gray-700 mb-1.5">6-digit code</label>
                  <input id="reset-code" value={code} onChange={(e) => setCode(e.target.value)} inputMode="numeric"
                    autoComplete="one-time-code" required placeholder="123456"
                    className={`${field} text-center text-xl tracking-[0.3em] font-semibold`} />
                  <p className="text-xs text-gray-500 mt-1.5">
                    If an account uses this number, we&apos;ve texted it a code. It works for 5 minutes.{" "}
                    <button type="button" onClick={() => setStage("ask")} className="text-brand-600 hover:underline">Change number</button>
                  </p>
                </div>
              )}
              {passwordFields}
              <button type="submit" disabled={loading}
                className="w-full bg-brand-600 hover:bg-brand-700 disabled:opacity-60 text-white font-bold py-3 rounded-xl transition-colors">
                {loading ? "Saving..." : "Save new password"}
              </button>
            </form>
          ) : (
            <>
              <form onSubmit={request} className="space-y-5">
                <div>
                  <label htmlFor="reset-identifier" className="block text-sm font-medium text-gray-700 mb-1.5">Email or phone number</label>
                  <input id="reset-identifier" type="text" value={identifier} onChange={(e) => setIdentifier(e.target.value)}
                    required autoComplete="username" className={field} placeholder="you@example.com or +263 77 123 4567" />
                </div>
                <button type="submit" disabled={loading}
                  className="w-full bg-brand-600 hover:bg-brand-700 disabled:opacity-60 text-white font-bold py-3 rounded-xl transition-colors">
                  {loading ? "Sending..." : looksLikePhone(identifier) ? "Text me a code" : "Send reset link"}
                </button>
              </form>
              <p className="text-center text-sm text-gray-500 mt-5">
                <Link href="/login" className="text-brand-600 font-semibold hover:underline">Back to Sign In</Link>
              </p>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

export default function ForgotPasswordPage() {
  return (
    <Suspense fallback={<PageLoader />}>
      <ForgotPassword />
    </Suspense>
  );
}
