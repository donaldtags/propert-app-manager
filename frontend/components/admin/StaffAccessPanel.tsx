"use client";

import { useCallback, useEffect, useState } from "react";
import { Loader2, Search, ShieldCheck, UserPlus } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { staffAccess, users as usersApi, type StaffMember, type StaffRole } from "@/lib/api";
import AlertBanner from "@/components/AlertBanner";

const ROLES: { value: StaffRole; label: string; sees: string }[] = [
  { value: "ADMIN", label: "Administrator", sees: "Everything, including staff access" },
  { value: "FINANCE_ADMIN", label: "Finance", sees: "Revenue and payment analytics; exports" },
  { value: "MARKET_ANALYST", label: "Market analyst", sees: "Market and demand analytics; exports" },
  { value: "OPERATIONS_ADMIN", label: "Operations", sees: "Engine status, data quality, users, market and demand" },
  { value: "VERIFICATION_OFFICER", label: "Verification", sees: "Verifying listings and sales; engine status" },
];

/**
 * Who works for EstateX and what each person can see. Staff roles can only be granted here, by
 * an administrator; every change is written to the audit log.
 */
export default function StaffAccessPanel() {
  const { token, user } = useAuth();
  const [staff, setStaff] = useState<StaffMember[] | null>(null);
  const [draft, setDraft] = useState<Record<number, StaffRole[]>>({});
  const [busy, setBusy] = useState<number | null>(null);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<{ id: number; fullName: string; primaryProfile: string }[]>([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const load = useCallback(() => {
    if (!token) return;
    staffAccess.list(token).then((s) => { setStaff(s); setDraft({}); })
      .catch((e) => setError(e instanceof Error ? e.message : "Couldn't load staff."));
  }, [token]);
  useEffect(load, [load]);

  const search = async () => {
    if (!token || query.trim().length < 2) return;
    try {
      setResults((await usersApi.search({ q: query.trim() }, token)).slice(0, 8));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Search failed.");
    }
  };

  const rolesOf = (m: StaffMember) => draft[m.id] ?? m.staffRoles;
  const toggle = (m: StaffMember, r: StaffRole) => {
    const cur = rolesOf(m);
    setDraft({ ...draft, [m.id]: cur.includes(r) ? cur.filter((x) => x !== r) : [...cur, r] });
  };

  const save = async (m: StaffMember) => {
    if (!token) return;
    setBusy(m.id);
    setError("");
    try {
      await staffAccess.set(token, m.id, rolesOf(m));
      setNotice(`Access updated for ${m.fullName}.`);
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Couldn't update access.");
    } finally {
      setBusy(null);
    }
  };

  const add = (r: { id: number; fullName: string }) => {
    if (staff?.some((s) => s.id === r.id)) return;
    setStaff([...(staff ?? []), { id: r.id, fullName: r.fullName, staffRoles: [] }]);
    setDraft({ ...draft, [r.id]: ["MARKET_ANALYST"] });
    setResults([]);
    setQuery("");
  };

  return (
    <section className="space-y-5" aria-labelledby="staff-title">
      <div>
        <h2 id="staff-title" className="text-lg font-bold text-gray-900 flex items-center gap-2">
          <ShieldCheck className="w-5 h-5 text-brand-600" aria-hidden /> Staff access
        </h2>
        <p className="text-sm text-gray-500 mt-1 max-w-2xl">
          Staff roles decide which analytics each person sees. Nobody can give themselves a staff role; changes here are recorded in the audit log.
        </p>
      </div>
      {error && <AlertBanner variant="error">{error}</AlertBanner>}
      {notice && <AlertBanner variant="success">{notice}</AlertBanner>}

      <dl className="grid sm:grid-cols-2 lg:grid-cols-5 gap-2 text-xs">
        {ROLES.map((r) => (
          <div key={r.value} className="bg-white border border-gray-200 rounded-xl p-3">
            <dt className="font-semibold text-gray-800">{r.label}</dt>
            <dd className="text-gray-500 mt-0.5">{r.sees}</dd>
          </div>
        ))}
      </dl>

      <div className="flex gap-2 max-w-lg">
        <label htmlFor="staff-search" className="sr-only">Find a user to add</label>
        <input id="staff-search" value={query} onChange={(e) => setQuery(e.target.value)} onKeyDown={(e) => e.key === "Enter" && search()}
          placeholder="Find a user by name to add" className="flex-1 border border-gray-200 rounded-lg px-3 py-2 text-sm" />
        <button type="button" onClick={search} className="inline-flex items-center gap-1 border border-gray-200 rounded-lg px-3 text-sm">
          <Search className="w-4 h-4" aria-hidden /> Find
        </button>
      </div>
      {results.length > 0 && (
        <ul className="max-w-lg border border-gray-200 rounded-xl divide-y divide-gray-100 bg-white">
          {results.map((r) => (
            <li key={r.id} className="flex items-center justify-between px-3 py-2 text-sm">
              <span>{r.fullName} <span className="text-gray-400">#{r.id}</span></span>
              <button type="button" onClick={() => add(r)} className="inline-flex items-center gap-1 text-brand-600 font-medium">
                <UserPlus className="w-4 h-4" aria-hidden /> Add
              </button>
            </li>
          ))}
        </ul>
      )}

      {!staff ? (
        <p className="flex items-center gap-2 text-sm text-gray-500"><Loader2 className="w-4 h-4 animate-spin" /> Loading…</p>
      ) : (
        <div className="bg-white border border-gray-200 rounded-2xl overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="text-xs text-gray-500 text-left border-b border-gray-100">
              <tr>
                <th scope="col" className="px-4 py-2 font-medium">Person</th>
                {ROLES.map((r) => <th key={r.value} scope="col" className="px-2 py-2 font-medium text-center">{r.label}</th>)}
                <th scope="col" className="px-4 py-2"><span className="sr-only">Save</span></th>
              </tr>
            </thead>
            <tbody>
              {staff.map((m) => {
                const changed = draft[m.id] !== undefined;
                return (
                  <tr key={m.id} className="border-b border-gray-50">
                    <td className="px-4 py-2">
                      <p className="font-medium text-gray-900">{m.fullName}{m.id === user?.id ? " (you)" : ""}</p>
                      <p className="text-xs text-gray-400">{m.email ?? m.phone ?? `#${m.id}`}</p>
                    </td>
                    {ROLES.map((r) => (
                      <td key={r.value} className="px-2 py-2 text-center">
                        <input type="checkbox" className="w-4 h-4 accent-brand-600" checked={rolesOf(m).includes(r.value)}
                          onChange={() => toggle(m, r.value)} aria-label={`${r.label} for ${m.fullName}`}
                          disabled={r.value === "ADMIN" && m.id === user?.id} />
                      </td>
                    ))}
                    <td className="px-4 py-2 text-right">
                      <button type="button" disabled={!changed || busy === m.id} onClick={() => save(m)}
                        className="text-sm font-semibold text-white bg-brand-600 hover:bg-brand-700 rounded-lg px-3 py-1.5 disabled:opacity-40">
                        {busy === m.id ? "Saving…" : "Save"}
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
