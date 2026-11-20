"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Building2, CheckCircle } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { companies, slugify, type CompanyProfile } from "@/lib/trust";

const input =
  "mt-1 w-full border border-gray-200 rounded-xl px-4 py-2.5 text-base sm:text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100";

/** Self sign-up for estate agencies. The company is private until EstateX approves it. */
export default function RegisterAgencyPage() {
  const { user, token, loading } = useAuth();
  const router = useRouter();
  const [name, setName] = useState("");
  const [eac, setEac] = useState("");
  const [phone, setPhone] = useState("");
  const [email, setEmail] = useState("");
  const [address, setAddress] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [created, setCreated] = useState<CompanyProfile | null>(null);

  if (loading) return null;
  if (!user || !token) {
    return (
      <div className="max-w-lg mx-auto px-4 py-16 text-center">
        <Building2 className="w-10 h-10 text-brand-600 mx-auto mb-3" />
        <h1 className="text-2xl font-bold text-gray-900">Register your agency</h1>
        <p className="text-gray-600 mt-2">First sign in or create your own EstateX account. You&apos;ll run your agency from it.</p>
        <div className="flex justify-center gap-3 mt-6">
          <Link href="/login?redirect=/agencies/register" className="bg-brand-600 text-white font-semibold px-5 py-2.5 rounded-xl">Sign in</Link>
          <Link href="/register?redirect=/agencies/register" className="border border-gray-300 font-semibold px-5 py-2.5 rounded-xl">Create account</Link>
        </div>
      </div>
    );
  }

  if (created) {
    return (
      <div className="max-w-lg mx-auto px-4 py-16 text-center">
        <CheckCircle className="w-12 h-12 text-brand-600 mx-auto mb-3" />
        <h1 className="text-2xl font-bold text-gray-900">{created.name} is registered</h1>
        <p className="text-gray-600 mt-2">
          We&apos;re checking your Estate Agents Council registration, usually within one working day. Meanwhile you can set
          up your workspace and add your agents. Your company page and listings go public once you&apos;re approved.
        </p>
        <button
          type="button"
          onClick={() => router.push(`/orgs/${created.slug}`)}
          className="mt-6 bg-brand-600 hover:bg-brand-700 text-white font-semibold px-6 py-3 rounded-xl"
        >
          Open my agency workspace
        </button>
      </div>
    );
  }

  const slug = slugify(name);
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (slug.length < 3) {
      setError("Please enter the full company name.");
      return;
    }
    setSaving(true);
    setError("");
    try {
      setCreated(await companies.register(
        { slug, name: name.trim(), eacRegistrationNumber: eac.trim(), phone: phone.trim() || undefined,
          email: email.trim() || undefined, address: address.trim() || undefined },
        token
      ));
    } catch (err) {
      const msg = err instanceof Error ? err.message : "Couldn't register. Please try again.";
      setError(msg.includes("slug") ? "That company name is already taken on EstateX. Try adding your city." : msg);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="max-w-xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold text-gray-900">Register your agency</h1>
      <p className="text-gray-600 mt-1">
        List your company on EstateX. Your agents appear as &quot;Agent at {name.trim() || "your company"}&quot; on every listing.
      </p>

      <ol className="mt-5 grid sm:grid-cols-3 gap-2 text-sm">
        {["Tell us about your company", "We check your EAC registration", "You go public and list"].map((step, i) => (
          <li key={step} className="bg-gray-50 border border-gray-200 rounded-xl p-3">
            <span className="font-bold text-brand-700">{i + 1}.</span> {step}
          </li>
        ))}
      </ol>

      <form onSubmit={submit} className="mt-6 bg-white border border-gray-200 rounded-2xl p-5 space-y-4">
        <label className="block text-sm font-medium text-gray-700">
          Company name
          <input required value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Acme Realty" className={input} />
          {slug.length >= 3 && <span className="block text-xs text-gray-500 mt-1">Your page: estatex/agencies/{slug}</span>}
        </label>
        <label className="block text-sm font-medium text-gray-700">
          Estate Agents Council registration number
          <input required value={eac} onChange={(e) => setEac(e.target.value)} placeholder="e.g. EAC-F-0099" className={input} />
          <span className="block text-xs text-gray-500 mt-1">The firm&apos;s registration. We confirm it before approving you.</span>
        </label>
        <div className="grid sm:grid-cols-2 gap-4">
          <label className="block text-sm font-medium text-gray-700">
            Office phone
            <input type="tel" autoComplete="tel" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="+263 24 270 0000" className={input} />
          </label>
          <label className="block text-sm font-medium text-gray-700">
            Office email <span className="font-normal text-gray-500">(optional)</span>
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="info@acme.co.zw" className={input} />
          </label>
        </div>
        <label className="block text-sm font-medium text-gray-700">
          Office address <span className="font-normal text-gray-500">(optional)</span>
          <input value={address} onChange={(e) => setAddress(e.target.value)} placeholder="12 Samora Machel Ave, Harare" className={input} />
        </label>
        {error && <p className="text-sm text-red-600" role="alert">{error}</p>}
        <button
          type="submit"
          disabled={saving}
          className="w-full bg-brand-600 hover:bg-brand-700 disabled:opacity-50 text-white font-bold py-3 rounded-xl"
        >
          {saving ? "Registering…" : "Register my agency"}
        </button>
      </form>
    </div>
  );
}
