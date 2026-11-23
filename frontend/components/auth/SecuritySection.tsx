"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { ShieldCheck, Smartphone, MessageSquare, KeyRound, LogOut, Copy, Check } from "lucide-react";
import { auth as authApi } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { cleanCode } from "@/lib/device";
import type { TotpSetup, TwoFactorMethod, TwoFactorStatus } from "@/lib/types";

const field =
  "w-full border border-gray-200 rounded-xl px-4 py-3 text-base sm:text-sm text-gray-900 outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100";
const primary = "bg-brand-600 hover:bg-brand-700 disabled:opacity-60 text-white font-semibold px-4 py-2.5 rounded-xl text-sm";
const secondary = "border border-gray-200 hover:border-gray-300 text-gray-700 font-semibold px-4 py-2.5 rounded-xl text-sm";

/** Settings > Security: two-step sign-in, password, and signing out other devices. */
export default function SecuritySection() {
  const { user, token, logout } = useAuth();
  const router = useRouter();
  const [status, setStatus] = useState<TwoFactorStatus | null>(null);
  const [mode, setMode] = useState<"idle" | "app" | "sms" | "disable">("idle");
  const [setup, setSetup] = useState<TotpSetup | null>(null);
  const [code, setCode] = useState("");
  const [backupCodes, setBackupCodes] = useState<string[] | null>(null);
  const [copied, setCopied] = useState(false);
  const [disableMethod, setDisableMethod] = useState<TwoFactorMethod>("TOTP");
  const [disablePassword, setDisablePassword] = useState("");
  const [pw, setPw] = useState({ current: "", next: "", confirm: "" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  const load = useCallback(async () => {
    if (!token) return;
    try {
      setStatus(await authApi.twoFactor.status(token));
    } catch {
      // Leave the panel hidden if it can't load.
    }
  }, [token]);

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    authApi.twoFactor.status(token).then((s) => {
      if (!cancelled) setStatus(s);
    }, () => {});
    return () => {
      cancelled = true;
    };
  }, [token]);

  const run = async (fn: () => Promise<void>) => {
    setError("");
    setMessage("");
    setBusy(true);
    try {
      await fn();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "Something went wrong. Try again.");
    } finally {
      setBusy(false);
    }
  };

  const reset = () => {
    setMode("idle");
    setSetup(null);
    setCode("");
    setDisablePassword("");
  };

  if (!token || !user) return null;

  const startApp = () =>
    run(async () => {
      setSetup(await authApi.twoFactor.startApp(token));
      setMode("app");
      setCode("");
    });

  const confirmApp = () =>
    run(async () => {
      const r = await authApi.twoFactor.confirmApp(cleanCode(code, "TOTP"), token);
      if (r.backupCodes.length) setBackupCodes(r.backupCodes);
      reset();
      setMessage("Authenticator app connected. You'll be asked for a code when you sign in.");
      await load();
    });

  const startSms = () =>
    run(async () => {
      const r = await authApi.twoFactor.startSms(token);
      setMode("sms");
      setCode("");
      setMessage(r.message.replace("Code sent to", "We texted a 6-digit code to") + ".");
    });

  const confirmSms = () =>
    run(async () => {
      const r = await authApi.twoFactor.confirmSms(cleanCode(code, "SMS"), token);
      if (r.backupCodes.length) setBackupCodes(r.backupCodes);
      reset();
      setMessage("Text-message codes are on. You'll be asked for a code when you sign in.");
      await load();
    });

  const sendDisableCode = () =>
    run(async () => {
      const r = await authApi.twoFactor.sendDisableCode(token);
      setMessage(r.message.replace("Code sent to", "We texted a code to") + ".");
    });

  const disable = () =>
    run(async () => {
      await authApi.twoFactor.disable({ password: disablePassword, code: cleanCode(code, disableMethod), method: disableMethod }, token);
      reset();
      setBackupCodes(null);
      setMessage("Two-step sign-in is off.");
      await load();
    });

  const changePassword = (e: React.FormEvent) => {
    e.preventDefault();
    if (pw.next !== pw.confirm) {
      setError("The two new passwords don't match.");
      return;
    }
    run(async () => {
      const r = await authApi.changePassword({ currentPassword: pw.current, newPassword: pw.next }, token);
      setPw({ current: "", next: "", confirm: "" });
      setMessage(r.message);
    });
  };

  const signOutEverywhere = () => {
    if (!window.confirm("Sign out of EstateX on every phone and computer, including this one?")) return;
    run(async () => {
      await authApi.logoutAll(token);
      await logout();
      router.push("/login");
    });
  };

  const copyCodes = async () => {
    if (!backupCodes) return;
    try {
      await navigator.clipboard.writeText(backupCodes.join("\n"));
      setCopied(true);
    } catch {
      setCopied(false);
    }
  };

  const codeInput = (method: TwoFactorMethod, id: string) => (
    <div>
      <label htmlFor={id} className="block text-sm font-medium text-gray-700 mb-1.5">
        {method === "BACKUP" ? "Backup code" : "6-digit code"}
      </label>
      <input id={id} value={code} onChange={(e) => setCode(e.target.value)} inputMode={method === "BACKUP" ? "text" : "numeric"}
        autoComplete="one-time-code" placeholder={method === "BACKUP" ? "ABCDE-12345" : "123456"}
        className={`${field} text-center text-lg tracking-[0.25em] font-semibold max-w-xs`} />
    </div>
  );

  return (
    <div className="space-y-6 mb-6">
      {(error || message) && (
        <div role={error ? "alert" : "status"}
          className={`text-sm px-4 py-3 rounded-xl border ${error ? "bg-red-50 border-red-200 text-red-700" : "bg-brand-50 border-brand-200 text-brand-800"}`}>
          {error || message}
        </div>
      )}

      {/* Backup codes: shown once */}
      {backupCodes && (
        <section className="bg-amber-50 border border-amber-200 rounded-2xl p-6" aria-labelledby="backup-codes-title">
          <h2 id="backup-codes-title" className="font-bold text-gray-900 mb-1 flex items-center gap-2">
            <KeyRound className="w-5 h-5 text-amber-700" aria-hidden /> Save your backup codes
          </h2>
          <p className="text-sm text-gray-700 mb-4">
            If you lose your phone, each of these codes lets you sign in once. Write them down or save them somewhere safe -
            this is the only time we&apos;ll show them.
          </p>
          <ul className="grid grid-cols-2 gap-2 font-mono text-sm mb-4">
            {backupCodes.map((c) => (
              <li key={c} className="bg-white border border-amber-200 rounded-lg px-3 py-2 text-center">{c}</li>
            ))}
          </ul>
          <div className="flex flex-wrap gap-2">
            <button type="button" onClick={copyCodes} className={secondary + " bg-white flex items-center gap-2"}>
              {copied ? <Check className="w-4 h-4" aria-hidden /> : <Copy className="w-4 h-4" aria-hidden />}
              {copied ? "Copied" : "Copy codes"}
            </button>
            <button type="button" onClick={() => setBackupCodes(null)} className={primary}>I&apos;ve saved them</button>
          </div>
        </section>
      )}

      {/* Two-step sign-in */}
      {status && (
        <section className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm" aria-labelledby="two-step-title">
          <div className="flex flex-wrap items-start justify-between gap-3 mb-2">
            <h2 id="two-step-title" className="font-bold text-gray-900 flex items-center gap-2">
              <ShieldCheck className="w-5 h-5 text-brand-600" aria-hidden /> Two-step sign-in
            </h2>
            <span className={`text-xs font-semibold px-3 py-1 rounded-full ${status.enabled ? "bg-brand-100 text-brand-700" : "bg-gray-100 text-gray-600"}`}>
              {status.enabled ? "On" : "Off"}
            </span>
          </div>
          <p className="text-sm text-gray-600 mb-5">
            After your password, EstateX asks for a code from your phone. Even if someone learns your password, they can&apos;t get in
            without your phone.
          </p>

          {status.enabled && mode === "idle" && (
            <ul className="text-sm text-gray-700 space-y-2 mb-5">
              {status.authenticatorApp && <li className="flex items-center gap-2"><Smartphone className="w-4 h-4 text-brand-600" aria-hidden /> Authenticator app</li>}
              {status.sms && <li className="flex items-center gap-2"><MessageSquare className="w-4 h-4 text-brand-600" aria-hidden /> Text messages to {status.maskedPhone}</li>}
              <li className="flex items-center gap-2"><KeyRound className="w-4 h-4 text-brand-600" aria-hidden /> {status.backupCodesRemaining} backup code{status.backupCodesRemaining === 1 ? "" : "s"} left</li>
            </ul>
          )}

          {mode === "idle" && (
            <div className="flex flex-wrap gap-2">
              {!status.authenticatorApp && (
                <button type="button" onClick={startApp} disabled={busy} className={primary + " flex items-center gap-2"}>
                  <Smartphone className="w-4 h-4" aria-hidden /> {status.enabled ? "Add an authenticator app" : "Use an authenticator app"}
                </button>
              )}
              {!status.enabled && status.smsAvailable && user.phone && (
                <button type="button" onClick={startSms} disabled={busy} className={secondary + " flex items-center gap-2"}>
                  <MessageSquare className="w-4 h-4" aria-hidden /> Use text messages
                </button>
              )}
              {status.enabled && (
                <button type="button" onClick={() => { setMode("disable"); setDisableMethod(status.authenticatorApp ? "TOTP" : status.sms ? "SMS" : "BACKUP"); setCode(""); }}
                  className="text-sm font-semibold text-red-600 hover:underline px-2 py-2.5">
                  Turn off
                </button>
              )}
            </div>
          )}
          {mode === "idle" && !status.enabled && status.smsAvailable && !user.phone && (
            <p className="text-xs text-gray-500 mt-3">
              Want codes by text message instead? <Link href="/profile" className="text-brand-600 hover:underline">Add your phone number</Link> first.
            </p>
          )}

          {mode === "app" && setup && (
            <div className="space-y-4">
              <ol className="list-decimal pl-5 text-sm text-gray-700 space-y-1">
                <li>Install <strong>Google Authenticator</strong> or <strong>Microsoft Authenticator</strong> on your phone (free).</li>
                <li>In the app, tap <strong>+</strong> and scan this code.</li>
                <li>Type the 6-digit code the app shows.</li>
              </ol>
              {/* eslint-disable-next-line @next/next/no-img-element -- generated data URL */}
              <img src={setup.qrDataUrl} alt="QR code to scan with your authenticator app" width={200} height={200}
                className="border border-gray-200 rounded-xl" />
              <p className="text-xs text-gray-500">
                Can&apos;t scan? Enter this key in the app instead:{" "}
                <span className="font-mono text-gray-800 break-all select-all">{setup.secret.replace(/(.{4})/g, "$1 ").trim()}</span>
              </p>
              {codeInput("TOTP", "app-code")}
              <div className="flex flex-wrap gap-2">
                <button type="button" onClick={confirmApp} disabled={busy || cleanCode(code, "TOTP").length < 6} className={primary}>
                  {busy ? "Checking..." : "Turn on"}
                </button>
                <button type="button" onClick={reset} className={secondary}>Cancel</button>
              </div>
            </div>
          )}

          {mode === "sms" && (
            <div className="space-y-4">
              {codeInput("SMS", "sms-code")}
              <div className="flex flex-wrap gap-2">
                <button type="button" onClick={confirmSms} disabled={busy || cleanCode(code, "SMS").length < 6} className={primary}>
                  {busy ? "Checking..." : "Turn on"}
                </button>
                <button type="button" onClick={reset} className={secondary}>Cancel</button>
              </div>
            </div>
          )}

          {mode === "disable" && (
            <div className="space-y-4">
              <p className="text-sm text-gray-700">To turn it off, confirm your password and a current code.</p>
              <div>
                <label htmlFor="disable-password" className="block text-sm font-medium text-gray-700 mb-1.5">Password</label>
                <input id="disable-password" type="password" value={disablePassword} onChange={(e) => setDisablePassword(e.target.value)}
                  autoComplete="current-password" className={`${field} max-w-xs`} />
              </div>
              <fieldset>
                <legend className="text-sm font-medium text-gray-700 mb-1.5">Code from</legend>
                <div className="flex flex-wrap gap-2">
                  {(["TOTP", "SMS", "BACKUP"] as TwoFactorMethod[])
                    .filter((m) => (m === "TOTP" ? status.authenticatorApp : m === "SMS" ? status.sms : true))
                    .map((m) => (
                      <button key={m} type="button" aria-pressed={disableMethod === m}
                        onClick={() => { setDisableMethod(m); setCode(""); }}
                        className={`text-sm px-3 py-2 rounded-xl border ${disableMethod === m ? "border-brand-600 bg-brand-50 text-brand-700 font-semibold" : "border-gray-200 text-gray-600"}`}>
                        {m === "TOTP" ? "Authenticator app" : m === "SMS" ? "Text message" : "Backup code"}
                      </button>
                    ))}
                </div>
              </fieldset>
              {disableMethod === "SMS" && (
                <button type="button" onClick={sendDisableCode} disabled={busy} className="text-sm text-brand-600 hover:underline">
                  Text me a code
                </button>
              )}
              {codeInput(disableMethod, "disable-code")}
              <div className="flex flex-wrap gap-2">
                <button type="button" onClick={disable} disabled={busy || !disablePassword || !code}
                  className="bg-red-600 hover:bg-red-700 disabled:opacity-60 text-white font-semibold px-4 py-2.5 rounded-xl text-sm">
                  {busy ? "Turning off..." : "Turn off two-step sign-in"}
                </button>
                <button type="button" onClick={reset} className={secondary}>Cancel</button>
              </div>
            </div>
          )}
        </section>
      )}

      {/* Password */}
      <section className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm" aria-labelledby="password-title">
        <h2 id="password-title" className="font-bold text-gray-900 mb-1">Change password</h2>
        <p className="text-sm text-gray-600 mb-4">Other phones and computers signed in to your account will be signed out.</p>
        <form onSubmit={changePassword} className="space-y-4 max-w-sm">
          <div>
            <label htmlFor="current-password" className="block text-sm font-medium text-gray-700 mb-1.5">Current password</label>
            <input id="current-password" type="password" value={pw.current} onChange={(e) => setPw({ ...pw, current: e.target.value })}
              required autoComplete="current-password" className={field} />
          </div>
          <div>
            <label htmlFor="next-password" className="block text-sm font-medium text-gray-700 mb-1.5">New password</label>
            <input id="next-password" type="password" value={pw.next} onChange={(e) => setPw({ ...pw, next: e.target.value })}
              required autoComplete="new-password" className={field} />
            <p className="text-xs text-gray-500 mt-1.5">At least 10 characters, with a capital letter, a number and a symbol.</p>
          </div>
          <div>
            <label htmlFor="confirm-next-password" className="block text-sm font-medium text-gray-700 mb-1.5">Type the new password again</label>
            <input id="confirm-next-password" type="password" value={pw.confirm} onChange={(e) => setPw({ ...pw, confirm: e.target.value })}
              required autoComplete="new-password" className={field} />
          </div>
          <button type="submit" disabled={busy} className={primary}>{busy ? "Saving..." : "Change password"}</button>
        </form>
      </section>

      {/* Devices */}
      <section className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm" aria-labelledby="devices-title">
        <h2 id="devices-title" className="font-bold text-gray-900 mb-1">Signed-in devices</h2>
        <p className="text-sm text-gray-600 mb-4">
          Lost a phone, or signed in on someone else&apos;s computer? Sign out everywhere at once. We also alert you when your account is
          used on a new device.
        </p>
        <button type="button" onClick={signOutEverywhere} disabled={busy}
          className="flex items-center gap-2 border border-red-200 text-red-700 hover:bg-red-50 font-semibold px-4 py-2.5 rounded-xl text-sm">
          <LogOut className="w-4 h-4" aria-hidden /> Sign out of all devices
        </button>
      </section>
    </div>
  );
}
