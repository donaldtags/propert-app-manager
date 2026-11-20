"use client";

import { useEffect, useState } from "react";
import { ShieldCheck, Smartphone, MessageSquare, KeyRound } from "lucide-react";
import { auth as authApi } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { cleanCode } from "@/lib/device";
import type { TwoFactorChallenge, TwoFactorMethod, User } from "@/lib/types";

/** Second sign-in step: a code from the authenticator app, a text message, or a backup code. */
export default function TwoFactorStep({
  challenge,
  onDone,
  onCancel,
}: {
  challenge: TwoFactorChallenge;
  onDone: (user: User) => void;
  onCancel: () => void;
}) {
  const { completeTwoFactor } = useAuth();
  const hasApp = challenge.methods.includes("TOTP");
  const hasSms = challenge.methods.includes("SMS");
  const [method, setMethod] = useState<TwoFactorMethod>(hasApp ? "TOTP" : hasSms ? "SMS" : "BACKUP");
  const [code, setCode] = useState("");
  const [error, setError] = useState("");
  const [info, setInfo] = useState(
    !hasApp && hasSms
      ? challenge.codeSent
        ? `We texted a 6-digit code to ${challenge.maskedPhone ?? "your phone"}.`
        : `Use the code we texted to ${challenge.maskedPhone ?? "your phone"}.`
      : ""
  );
  const [busy, setBusy] = useState(false);
  const [resendIn, setResendIn] = useState(!hasApp && hasSms && challenge.codeSent ? 60 : 0);

  useEffect(() => {
    if (resendIn <= 0) return;
    const t = setTimeout(() => setResendIn((s) => s - 1), 1000);
    return () => clearTimeout(t);
  }, [resendIn]);

  const switchTo = (m: TwoFactorMethod) => {
    setMethod(m);
    setCode("");
    setError("");
    setInfo("");
  };

  const textMeACode = async () => {
    setError("");
    setBusy(true);
    try {
      const r = await authApi.sendLoginCode(challenge.challengeToken);
      switchTo("SMS");
      setInfo(r.message.replace("Code sent to", "We texted a 6-digit code to") + ".");
      setResendIn(60);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "We couldn't send a text. Try again in a minute.");
    } finally {
      setBusy(false);
    }
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      onDone(await completeTwoFactor(challenge.challengeToken, cleanCode(code, method), method));
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "That code didn't work.");
      setCode("");
    } finally {
      setBusy(false);
    }
  };

  const heading =
    method === "TOTP" ? "Enter the code from your authenticator app" : method === "SMS" ? "Enter the code we texted you" : "Enter a backup code";

  return (
    <div>
      <div className="flex items-center gap-2 mb-2 text-brand-700">
        <ShieldCheck className="w-5 h-5" aria-hidden />
        <p className="text-sm font-semibold">Two-step sign-in</p>
      </div>
      <h2 className="text-lg font-bold text-gray-900 mb-1">{heading}</h2>
      <p className="text-sm text-gray-500 mb-4">
        {method === "TOTP"
          ? "Open Google Authenticator (or the app you set up) and type the 6-digit code shown for EstateX."
          : method === "SMS"
            ? info || "Type the 6-digit code from the text message."
            : "Each backup code works once. You saved them when you turned on two-step sign-in."}
      </p>

      {error && (
        <div role="alert" className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-4">
          {error}
        </div>
      )}

      <form onSubmit={submit} className="space-y-4">
        <label htmlFor="two-factor-code" className="block text-sm font-medium text-gray-700">
          {method === "BACKUP" ? "Backup code" : "6-digit code"}
        </label>
        <input
          id="two-factor-code"
          value={code}
          onChange={(e) => setCode(e.target.value)}
          inputMode={method === "BACKUP" ? "text" : "numeric"}
          autoComplete="one-time-code"
          autoFocus
          maxLength={method === "BACKUP" ? 14 : 8}
          placeholder={method === "BACKUP" ? "ABCDE-12345" : "123456"}
          className="w-full border border-gray-200 rounded-xl px-4 py-3 text-center text-xl tracking-[0.3em] font-semibold text-gray-900 outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100"
          required
        />
        <button
          type="submit"
          disabled={busy || cleanCode(code, method).length < (method === "BACKUP" ? 10 : 6)}
          className="w-full bg-brand-600 hover:bg-brand-700 disabled:opacity-60 text-white font-bold py-3 rounded-xl transition-colors"
        >
          {busy ? "Checking..." : "Verify and sign in"}
        </button>
      </form>

      <div className="mt-5 space-y-1 text-sm">
        {hasSms && method === "SMS" && (
          <button type="button" onClick={textMeACode} disabled={busy || resendIn > 0}
            className="flex items-center gap-2 text-brand-600 hover:underline disabled:text-gray-400 disabled:no-underline py-2">
            <MessageSquare className="w-4 h-4" aria-hidden />
            {resendIn > 0 ? `Send a new code in ${resendIn}s` : "Send me a new code"}
          </button>
        )}
        {hasSms && method !== "SMS" && (
          <button type="button" onClick={textMeACode} disabled={busy}
            className="flex items-center gap-2 text-brand-600 hover:underline py-2">
            <MessageSquare className="w-4 h-4" aria-hidden /> Text me a code instead
          </button>
        )}
        {hasApp && method !== "TOTP" && (
          <button type="button" onClick={() => switchTo("TOTP")} className="flex items-center gap-2 text-brand-600 hover:underline py-2">
            <Smartphone className="w-4 h-4" aria-hidden /> Use my authenticator app
          </button>
        )}
        {method !== "BACKUP" && (
          <button type="button" onClick={() => switchTo("BACKUP")} className="flex items-center gap-2 text-brand-600 hover:underline py-2">
            <KeyRound className="w-4 h-4" aria-hidden /> Use a backup code
          </button>
        )}
        <button type="button" onClick={onCancel} className="text-gray-500 hover:underline py-2">
          Start again
        </button>
      </div>
    </div>
  );
}
