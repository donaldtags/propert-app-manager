"use client";

import { useRef, useState, Suspense } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import EstateXLogo from "@/components/EstateXLogo";
import { Eye, EyeOff, CheckCircle, XCircle } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { dashboardPathFor } from "@/lib/dashboardRoute";
import { safeRedirect } from "@/lib/safeRedirect";

function passwordStrength(pw: string) {
  const checks = {
    length: pw.length >= 10,
    upper: /[A-Z]/.test(pw),
    number: /[0-9]/.test(pw),
    symbol: /[^A-Za-z0-9]/.test(pw),
  };
  const score = Object.values(checks).filter(Boolean).length;
  return { checks, score };
}

function RegisterForm() {
  const { register } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();
  const redirectParam = safeRedirect(searchParams);
  const redirect = redirectParam ?? "/";

  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [country, setCountry] = useState("Zimbabwe");
  const [password, setPassword] = useState("");
  const [confirmPw, setConfirmPw] = useState("");
  const [role, setRole] = useState<"TENANT" | "LANDLORD" | "AGENT" | "DEVELOPER" | "PRIVATE" | "INVESTOR" | "SERVICE_PROVIDER">("TENANT");
  const [showPw, setShowPw] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const errorRef = useRef<HTMLDivElement>(null);

  const { checks, score } = passwordStrength(password);
  const pwOk = score === 4;
  const pwMatch = password === confirmPw && confirmPw.length > 0;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    // The banner sits at the top of a long form, so also move focus to the field at fault:
    // focusing scrolls it into view, and the hint or checklist next to it says what to change.
    const fail = (message: string, fieldId: string) => {
      setError(message);
      document.getElementById(fieldId)?.focus();
    };
    if (!pwOk) return fail("Password does not meet requirements.", "reg-password");
    if (!pwMatch) return fail("Passwords do not match.", "reg-confirm");
    if (!email.trim() && !phone.trim()) {
      return fail("Enter an email address or a phone number - you'll use it to sign in.", "reg-email");
    }
    setError("");
    setLoading(true);
    try {
      const registeredUser = await register({
        fullName,
        email: email.trim() || undefined,
        phone: phone.trim() || undefined,
        password,
        country,
        roles: [role],
      });
      router.push(redirectParam ?? dashboardPathFor(registeredUser.roles));
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Registration failed. Try again.");
      requestAnimationFrame(() => errorRef.current?.scrollIntoView({ behavior: "smooth", block: "center" }));
    } finally {
      setLoading(false);
    }
  };

  const strengthColor = score < 2 ? "bg-red-400" : score < 4 ? "bg-amber-400" : "bg-brand-500";
  const strengthLabel = score < 2 ? "Weak" : score < 4 ? "Medium" : "Strong";

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4 py-12">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <Link href="/" className="inline-flex items-center mb-6">
            <EstateXLogo variant="full" className="h-32 sm:h-40 mx-auto" />
          </Link>
          <h1 className="text-2xl font-bold text-gray-900">Create your account</h1>
          <p className="text-gray-500 mt-1">Join thousands of verified users</p>
        </div>

        <div className="bg-white rounded-2xl shadow-sm border border-gray-200 p-8">
          {error && (
            <div ref={errorRef} role="alert" className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-5">
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label htmlFor="reg-name" className="block text-sm font-medium text-gray-700 mb-1.5">Full Name</label>
              <input
                id="reg-name"
                type="text"
                autoComplete="name"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                required
                className="w-full border border-gray-200 rounded-xl px-4 py-3 text-base sm:text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 transition-colors"
                placeholder="Tariro Moyo"
              />
            </div>

            <div>
              <label htmlFor="reg-email" className="block text-sm font-medium text-gray-700 mb-1.5">
                Email <span className="font-normal text-gray-500">(optional if you give a phone number)</span>
              </label>
              <input
                id="reg-email"
                type="email"
                inputMode="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="email"
                aria-describedby="reg-email-hint"
                className="w-full border border-gray-200 rounded-xl px-4 py-3 text-base sm:text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 transition-colors"
                placeholder="you@example.com"
              />
              <p id="reg-email-hint" className="text-xs text-gray-500 mt-1.5">
                No email? Use your phone number instead - you&apos;ll sign in with it and we&apos;ll text you codes.
              </p>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label htmlFor="reg-phone" className="block text-sm font-medium text-gray-700 mb-1.5">
                  Phone {!email.trim() && <span className="font-normal text-red-600">*</span>}
                </label>
                <input
                  id="reg-phone"
                  type="tel"
                  inputMode="tel"
                  autoComplete="tel"
                  value={phone}
                  required={!email.trim()}
                  onChange={(e) => setPhone(e.target.value)}
                  className="w-full border border-gray-200 rounded-xl px-4 py-3 text-base sm:text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 transition-colors"
                  placeholder="+263 77 123 4567"
                />
              </div>
              <div>
                <label htmlFor="reg-country" className="block text-sm font-medium text-gray-700 mb-1.5">Country</label>
                <input
                  id="reg-country"
                  type="text"
                  autoComplete="country-name"
                  value={country}
                  onChange={(e) => setCountry(e.target.value)}
                  className="w-full border border-gray-200 rounded-xl px-4 py-3 text-base sm:text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 transition-colors"
                  placeholder="Zimbabwe"
                />
              </div>
            </div>

            <div>
              <p id="reg-role-label" className="block text-sm font-medium text-gray-700 mb-1.5">I am a…</p>
              <div className="grid grid-cols-3 gap-2" role="group" aria-labelledby="reg-role-label">
                {ROLE_CHOICES.map(({ value, label }) => (
                  <button
                    key={value}
                    type="button"
                    aria-pressed={role === value}
                    onClick={() => setRole(value)}
                    className={`min-h-11 py-2 text-sm font-medium rounded-xl border transition-colors ${
                      role === value
                        ? "bg-brand-600 text-white border-brand-600"
                        : "border-gray-200 text-gray-600 hover:border-gray-400"
                    }`}
                  >
                    {label}
                  </button>
                ))}
              </div>
              <p className="text-sm text-gray-600 mt-2" aria-live="polite">
                {ROLE_CHOICES.find((r) => r.value === role)?.hint}
              </p>
            </div>

            <div>
              <label htmlFor="reg-password" className="block text-sm font-medium text-gray-700 mb-1.5">Password</label>
              <div className="relative">
                <input
                  id="reg-password"
                  type={showPw ? "text" : "password"}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  autoComplete="new-password"
                  className="w-full border border-gray-200 rounded-xl px-4 py-3 pr-12 text-base sm:text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 transition-colors"
                  placeholder="••••••••"
                />
                <button
                  type="button"
                  onClick={() => setShowPw(!showPw)}
                  aria-label={showPw ? "Hide password" : "Show password"}
                  className="absolute right-1 top-1/2 -translate-y-1/2 w-11 h-11 flex items-center justify-center text-gray-400 hover:text-gray-600"
                >
                  {showPw ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
                </button>
              </div>

              {password.length === 0 && (
                <p className="text-xs text-gray-500 mt-1.5">
                  At least 10 characters, with a capital letter, a number and a symbol (e.g. ! or #).
                </p>
              )}
              {password.length > 0 && (
                <div className="mt-2">
                  <div className="flex gap-1 mb-2">
                    {Array.from({ length: 4 }).map((_, i) => (
                      <div
                        key={i}
                        className={`h-1 flex-1 rounded-full transition-colors ${i < score ? strengthColor : "bg-gray-200"}`}
                      />
                    ))}
                  </div>
                  <div className="grid grid-cols-2 gap-1 text-xs">
                    {[
                      { label: "10+ characters", ok: checks.length },
                      { label: "Uppercase letter", ok: checks.upper },
                      { label: "Number", ok: checks.number },
                      { label: "Symbol", ok: checks.symbol },
                    ].map(({ label, ok }) => (
                      <span key={label} className={`flex items-center gap-1 ${ok ? "text-brand-600" : "text-gray-400"}`}>
                        {ok ? <CheckCircle className="w-3 h-3" /> : <XCircle className="w-3 h-3" />}
                        {label}
                      </span>
                    ))}
                  </div>
                  <p className="text-xs mt-1 font-medium" style={{ color: score < 2 ? "#f87171" : score < 4 ? "#fbbf24" : "#22c55e" }}>
                    {strengthLabel}
                  </p>
                </div>
              )}
            </div>

            <div>
              <label htmlFor="reg-confirm" className="block text-sm font-medium text-gray-700 mb-1.5">Confirm Password</label>
              <input
                id="reg-confirm"
                type={showPw ? "text" : "password"}
                value={confirmPw}
                onChange={(e) => setConfirmPw(e.target.value)}
                required
                autoComplete="new-password"
                className={`w-full border rounded-xl px-4 py-3 text-base sm:text-sm outline-none transition-colors ${
                  confirmPw.length > 0
                    ? pwMatch
                      ? "border-brand-400 focus:ring-2 focus:ring-brand-100"
                      : "border-red-400 focus:ring-2 focus:ring-red-100"
                    : "border-gray-200 focus:border-brand-500 focus:ring-2 focus:ring-brand-100"
                }`}
                placeholder="••••••••"
                aria-invalid={confirmPw.length > 0 && !pwMatch}
                aria-describedby="reg-confirm-hint"
              />
              <p id="reg-confirm-hint" aria-live="polite" className="text-xs text-red-600 mt-1.5 min-h-0">
                {confirmPw.length > 0 && !pwMatch ? "Passwords do not match." : ""}
              </p>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full bg-brand-600 hover:bg-brand-700 disabled:opacity-60 text-white font-bold py-3 rounded-xl transition-colors mt-2"
            >
              {loading ? "Creating account..." : "Create Account"}
            </button>
          </form>

          <p className="text-center text-sm text-gray-500 mt-6">
            Already have an account?{" "}
            <Link href={`/login?redirect=${encodeURIComponent(redirect)}`} className="text-brand-600 font-semibold hover:underline">
              Sign in
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}

const ROLE_CHOICES = [
  { value: "TENANT", label: "Tenant", hint: "You're looking for a place to rent, buy or stay." },
  { value: "LANDLORD", label: "Landlord", hint: "You own property and want to rent it out." },
  { value: "AGENT", label: "Agent", hint: "You're an estate agent listing homes for clients." },
  { value: "DEVELOPER", label: "Developer", hint: "You build and sell new developments." },
  { value: "PRIVATE", label: "Private Owner", hint: "You want to sell or rent out your own home, without an agent." },
  { value: "INVESTOR", label: "Investor", hint: "You want to invest in property and earn returns." },
  { value: "SERVICE_PROVIDER", label: "Service Provider", hint: "You offer services to homeowners: plumbing, cleaning, legal and more." },
] as const;

export default function RegisterPage() {
  return (
    <Suspense>
      <RegisterForm />
    </Suspense>
  );
}
