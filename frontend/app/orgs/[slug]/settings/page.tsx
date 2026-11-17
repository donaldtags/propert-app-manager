"use client";

import { Suspense, useEffect, useState, type FormEvent } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { AlertTriangle, Check, UserPlus } from "lucide-react";
import { organisations } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { contrastIssues, themeStyle, withDefaults } from "@/lib/orgTheme";
import type {
  Division,
  Organisation,
  OrgMember,
  OrgRole,
  OrgThemeTokens,
  UpdateOrganisationRequest,
} from "@/lib/types";
import { DIVISION_LABELS, ROLE_LABELS, useOrgWorkspace, ws } from "@/components/workspace/OrgWorkspace";

const TABS = [
  { key: "profile", label: "Profile" },
  { key: "compliance", label: "Compliance" },
  { key: "fees", label: "Fees" },
  { key: "brand", label: "Brand" },
  { key: "members", label: "Members" },
] as const;
type TabKey = (typeof TABS)[number]["key"];

const STAFF_ROLES: OrgRole[] = ["ORG_ADMIN", "PREA", "NEGOTIATOR", "PROPERTY_MANAGER", "ANALYST"];
const EXTERNAL_ROLES: OrgRole[] = ["OWNER", "TENANT", "BUYER", "INVESTOR", "LANDOWNER"];
const DIVISIONS: Division[] = ["PROPERTIES", "MANAGEMENT", "INTELLIGENCE", "DEVELOPMENTS", "INVESTMENTS"];
const FONTS = ["Montserrat", "Inter"];

const COLOR_FIELDS: { key: keyof OrgThemeTokens; label: string }[] = [
  { key: "lightPrimary", label: "Primary" },
  { key: "lightSurface", label: "Surface" },
  { key: "lightText", label: "Text" },
  { key: "lightMuted", label: "Secondary text" },
  { key: "lightAccent", label: "Accent" },
  { key: "darkPrimary", label: "Primary" },
  { key: "darkSurface", label: "Surface" },
  { key: "darkText", label: "Text" },
  { key: "darkMuted", label: "Secondary text" },
  { key: "darkAccent", label: "Accent" },
];

interface FormState {
  name: string;
  slogan: string;
  phone: string;
  whatsappNumber: string;
  instagramHandle: string;
  email: string;
  address: string;
  website: string;
  divisions: Division[];
  eacRegistrationNumber: string;
  trustAccountReference: string;
  trustAccountBank: string;
  defaultCurrency: "USD" | "ZWG";
  saleCommissionPct: string;
  managementFeePct: string;
  lettingFeeMonths: string;
  theme: Required<OrgThemeTokens>;
}

function toForm(o: Organisation): FormState {
  return {
    name: o.name,
    slogan: o.slogan ?? "",
    phone: o.phone ?? "",
    whatsappNumber: o.whatsappNumber ?? "",
    instagramHandle: o.instagramHandle ?? "",
    email: o.email ?? "",
    address: o.address ?? "",
    website: o.website ?? "",
    divisions: o.divisions,
    eacRegistrationNumber: o.eacRegistrationNumber ?? "",
    trustAccountReference: o.trustAccountReference ?? "",
    trustAccountBank: o.trustAccountBank ?? "",
    defaultCurrency: o.defaultCurrency,
    saleCommissionPct: o.saleCommissionBps != null ? String(o.saleCommissionBps / 100) : "",
    managementFeePct: o.managementFeeBps != null ? String(o.managementFeeBps / 100) : "",
    lettingFeeMonths: o.lettingFeeMonthsX100 != null ? String(o.lettingFeeMonthsX100 / 100) : "",
    theme: withDefaults(o.theme),
  };
}

/** Only the fields that changed, so a save never overwrites a field someone else edited. */
function diff(o: Organisation, f: FormState): UpdateOrganisationRequest {
  const out: UpdateOrganisationRequest = { version: o.version };
  const text = (k: keyof FormState & keyof Organisation, v: string) => {
    if ((o[k] ?? "") !== v) (out as Record<string, unknown>)[k] = v;
  };
  text("name", f.name);
  text("slogan", f.slogan);
  text("phone", f.phone);
  text("whatsappNumber", f.whatsappNumber);
  text("instagramHandle", f.instagramHandle);
  text("email", f.email);
  text("address", f.address);
  text("website", f.website);
  text("eacRegistrationNumber", f.eacRegistrationNumber);
  text("trustAccountReference", f.trustAccountReference);
  text("trustAccountBank", f.trustAccountBank);
  if (f.defaultCurrency !== o.defaultCurrency) out.defaultCurrency = f.defaultCurrency;
  if ([...f.divisions].sort().join() !== [...o.divisions].sort().join()) out.divisions = f.divisions;
  const bps = (pct: string) => Math.round(parseFloat(pct) * 100);
  if (f.saleCommissionPct && bps(f.saleCommissionPct) !== o.saleCommissionBps) out.saleCommissionBps = bps(f.saleCommissionPct);
  if (f.managementFeePct && bps(f.managementFeePct) !== o.managementFeeBps) out.managementFeeBps = bps(f.managementFeePct);
  if (f.lettingFeeMonths && bps(f.lettingFeeMonths) !== o.lettingFeeMonthsX100) out.lettingFeeMonthsX100 = bps(f.lettingFeeMonths);
  const current = withDefaults(o.theme);
  const themeChanges = Object.fromEntries(
    Object.entries(f.theme).filter(([k, v]) => current[k as keyof OrgThemeTokens] !== v)
  ) as OrgThemeTokens;
  if (Object.keys(themeChanges).length) out.theme = themeChanges;
  return out;
}

export default function OrgSettingsPage() {
  return (
    <Suspense fallback={<p className="text-muted">Loading…</p>}>
      <OrgSettings />
    </Suspense>
  );
}

function OrgSettings() {
  const { slug, context, can, refresh } = useOrgWorkspace();
  const { token } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const requested = params.get("tab") as TabKey | null;
  const tab: TabKey = TABS.some((t) => t.key === requested) ? (requested as TabKey) : "profile";

  const org = context.organisation;
  const editable = can("ORG_SETTINGS_EDIT");
  const [form, setForm] = useState<FormState>(() => toForm(org));
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) => setForm((f) => ({ ...f, [key]: value }));

  const save = async (e: FormEvent) => {
    e.preventDefault();
    if (!token) return;
    const changes = diff(org, form);
    if (Object.keys(changes).length <= 1) {
      setMessage({ ok: true, text: "Nothing to save." });
      return;
    }
    setSaving(true);
    setMessage(null);
    try {
      const updated = await organisations.update(slug, changes, token);
      setForm(toForm(updated));
      await refresh();
      setMessage({ ok: true, text: "Saved." });
    } catch (err) {
      setMessage({ ok: false, text: err instanceof Error ? err.message : "Could not save" });
    } finally {
      setSaving(false);
    }
  };

  const visibleTabs = TABS.filter((t) => (t.key === "members" ? can("MEMBERS_VIEW") : true));

  return (
    <div>
      <h1 className="text-xl font-semibold">Workspace settings</h1>
      {!editable && <p className="text-sm text-muted mt-1">Read only. Only workspace admins can change these.</p>}

      <div role="tablist" aria-label="Settings sections" className="mt-4 flex gap-1 overflow-x-auto border-b border-line">
        {visibleTabs.map((t) => (
          <button
            key={t.key}
            role="tab"
            aria-selected={tab === t.key}
            onClick={() => router.replace(`${pathname}?tab=${t.key}`, { scroll: false })}
            className={`px-3 py-2.5 text-sm font-medium whitespace-nowrap border-b-2 -mb-px ${
              tab === t.key ? "border-primary text-ink" : "border-transparent text-muted hover:text-ink"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === "members" ? (
        <MembersPanel />
      ) : (
        <form onSubmit={save} className="mt-6 space-y-6">
          <fieldset disabled={!editable || saving} className="space-y-6">
            {tab === "profile" && <ProfileFields form={form} set={set} />}
            {tab === "compliance" && <ComplianceFields form={form} set={set} preaName={org.preaName} />}
            {tab === "fees" && <FeeFields form={form} set={set} />}
            {tab === "brand" && <BrandFields form={form} set={set} />}
          </fieldset>
          {editable && (
            <div className="flex flex-wrap items-center gap-3">
              <button type="submit" className={ws.button} disabled={saving}>
                {saving ? "Saving…" : "Save changes"}
              </button>
              {message && (
                <p role="status" className={`text-sm ${message.ok ? "text-success" : "text-danger"}`}>
                  {message.text}
                </p>
              )}
            </div>
          )}
        </form>
      )}
    </div>
  );
}

type FieldProps = { form: FormState; set: <K extends keyof FormState>(key: K, value: FormState[K]) => void };

function Text({
  label,
  value,
  onChange,
  hint,
  ...rest
}: { label: string; value: string; onChange: (v: string) => void; hint?: string } & Omit<
  React.InputHTMLAttributes<HTMLInputElement>,
  "value" | "onChange"
>) {
  return (
    <label className="block">
      <span className={ws.label}>{label}</span>
      <input className={ws.input} value={value} onChange={(e) => onChange(e.target.value)} {...rest} />
      {hint && <span className="mt-1 block text-xs text-muted">{hint}</span>}
    </label>
  );
}

function ProfileFields({ form, set }: FieldProps) {
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <Text label="Name" value={form.name} onChange={(v) => set("name", v)} required maxLength={160} />
      <Text label="Slogan" value={form.slogan} onChange={(v) => set("slogan", v)} maxLength={200} />
      <Text label="Phone" value={form.phone} onChange={(v) => set("phone", v)} inputMode="tel" />
      <Text
        label="WhatsApp number"
        value={form.whatsappNumber}
        onChange={(v) => set("whatsappNumber", v.replace(/\D/g, ""))}
        inputMode="numeric"
        hint="Digits only, with country code, e.g. 263784542297"
      />
      <Text
        label="Instagram"
        value={form.instagramHandle}
        onChange={(v) => set("instagramHandle", v.replace(/^@/, ""))}
        hint="Without the @"
      />
      <Text label="Email" type="email" value={form.email} onChange={(v) => set("email", v)} />
      <Text label="Address" value={form.address} onChange={(v) => set("address", v)} />
      <Text label="Website" value={form.website} onChange={(v) => set("website", v)} />
      <div className="sm:col-span-2">
        <span className={ws.label}>Divisions</span>
        <div className="flex flex-wrap gap-2">
          {DIVISIONS.map((d) => {
            const on = form.divisions.includes(d);
            return (
              <button
                key={d}
                type="button"
                aria-pressed={on}
                onClick={() => set("divisions", on ? form.divisions.filter((x) => x !== d) : [...form.divisions, d])}
                className={`${ws.chip} min-h-9 px-3 ${on ? "bg-primary text-on-primary border-transparent" : ""}`}
              >
                {on && <Check className="w-3.5 h-3.5 mr-1" />}
                {DIVISION_LABELS[d].name}
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}

function ComplianceFields({ form, set, preaName }: FieldProps & { preaName?: string }) {
  return (
    <div className="space-y-4">
      <div className={`${ws.card} p-4 text-sm`}>
        <p>
          <span className="text-muted">Principal Registered Estate Agent: </span>
          <strong>{preaName ?? "Not assigned"}</strong>
        </p>
        <p className="mt-1 text-muted">
          Set by giving one member the PREA role on the Members tab. Only the PREA can approve listings for publication
          and sign off mandates.
        </p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <Text
          label="EAC registration number"
          value={form.eacRegistrationNumber}
          onChange={(v) => set("eacRegistrationNumber", v)}
        />
        <Text label="Trust account bank" value={form.trustAccountBank} onChange={(v) => set("trustAccountBank", v)} />
        <Text
          label="Trust account reference"
          value={form.trustAccountReference}
          onChange={(v) => set("trustAccountReference", v)}
          hint="Client money (deposits, rent) is always recorded against this account, never operating income."
        />
        <label className="block">
          <span className={ws.label}>Default currency</span>
          <select
            className={ws.input}
            value={form.defaultCurrency}
            onChange={(e) => set("defaultCurrency", e.target.value as "USD" | "ZWG")}
          >
            <option value="USD">USD (US dollar)</option>
            <option value="ZWG">ZWG (ZiG)</option>
          </select>
        </label>
      </div>
    </div>
  );
}

function FeeFields({ form, set }: FieldProps) {
  return (
    <div className="space-y-4">
      <p className="text-sm text-muted">
        Defaults for new mandates and management agreements. Each mandate can still record its own agreed fee. Please
        have your PREA confirm these.
      </p>
      <div className="grid gap-4 sm:grid-cols-3">
        <Text
          label="Sale commission (%)"
          type="number"
          step="0.25"
          min="0"
          max="20"
          value={form.saleCommissionPct}
          onChange={(v) => set("saleCommissionPct", v)}
        />
        <Text
          label="Management fee (% of rent collected)"
          type="number"
          step="0.5"
          min="0"
          max="30"
          value={form.managementFeePct}
          onChange={(v) => set("managementFeePct", v)}
        />
        <Text
          label="Letting fee (months of rent)"
          type="number"
          step="0.25"
          min="0"
          max="12"
          value={form.lettingFeeMonths}
          onChange={(v) => set("lettingFeeMonths", v)}
        />
      </div>
    </div>
  );
}

function BrandFields({ form, set }: FieldProps) {
  const issues = contrastIssues(form.theme);
  const setColor = (key: keyof OrgThemeTokens, value: string) => set("theme", { ...form.theme, [key]: value.toUpperCase() });

  return (
    <div className="space-y-6">
      {(["light", "dark"] as const).map((mode) => (
        <fieldset key={mode}>
          <legend className="font-semibold mb-3">{mode === "light" ? "Light mode" : "Dark mode"}</legend>
          <div className="grid gap-3 grid-cols-2 sm:grid-cols-5">
            {COLOR_FIELDS.filter((c) => c.key.startsWith(mode)).map(({ key, label }) => (
              <label key={key} className="block">
                <span className={ws.label}>{label}</span>
                <span className="flex items-center gap-2">
                  <input
                    type="color"
                    aria-label={`${mode} ${label} colour`}
                    value={form.theme[key]}
                    onChange={(e) => setColor(key, e.target.value)}
                    className="h-11 w-11 shrink-0 rounded-lg border border-line bg-transparent"
                  />
                  <input
                    className={`${ws.input} font-mono text-xs`}
                    value={form.theme[key]}
                    onChange={(e) => setColor(key, e.target.value)}
                    pattern="#[0-9A-Fa-f]{6}"
                    aria-label={`${mode} ${label} hex`}
                  />
                </span>
              </label>
            ))}
          </div>
        </fieldset>
      ))}

      <div className="grid gap-4 sm:grid-cols-2">
        {(["headingFont", "bodyFont"] as const).map((k) => (
          <label key={k} className="block">
            <span className={ws.label}>{k === "headingFont" ? "Heading font" : "Body font"}</span>
            <select className={ws.input} value={form.theme[k]} onChange={(e) => set("theme", { ...form.theme, [k]: e.target.value })}>
              {FONTS.map((f) => (
                <option key={f}>{f}</option>
              ))}
            </select>
          </label>
        ))}
      </div>

      {issues.length > 0 && (
        <div role="alert" className="flex gap-3 rounded-2xl border border-warning p-4 text-sm">
          <AlertTriangle className="w-5 h-5 text-warning shrink-0" />
          <ul className="space-y-1">
            {issues.map((i) => (
              <li key={i.mode + i.pair}>
                {i.mode === "light" ? "Light" : "Dark"} mode: {i.pair} is {i.ratio}:1; text needs at least {i.required}:1 to
                be readable.
              </li>
            ))}
          </ul>
        </div>
      )}

      <div>
        <p className={ws.label}>Preview</p>
        <div className="grid gap-3 sm:grid-cols-2">
          {(["light", "dark"] as const).map((mode) => (
            <div
              key={mode}
              className="ex-theme rounded-2xl border border-line p-4"
              data-mode={mode}
              style={themeStyle(form.theme)}
            >
              <p className="font-heading font-semibold text-lg">Borrowdale, 4 bed</p>
              <p className="text-sm text-muted">US$450,000 · Title deed · 2,000 m²</p>
              <div className="mt-3 flex gap-2">
                <span className="rounded-lg bg-primary text-on-primary px-3 py-1.5 text-sm font-semibold">Book viewing</span>
                <span className="rounded-lg border border-accent px-3 py-1.5 text-sm">Share</span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function MembersPanel() {
  const { slug, context, can, refresh } = useOrgWorkspace();
  const { token } = useAuth();
  const manage = can("MEMBERS_MANAGE");
  const enabledDivisions = context.organisation.divisions;
  const [members, setMembers] = useState<OrgMember[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [email, setEmail] = useState("");
  const [roles, setRoles] = useState<OrgRole[]>(["NEGOTIATOR"]);
  const [busy, setBusy] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    if (!token) return;
    organisations.members(slug, token).then(setMembers, (e: unknown) =>
      setError(e instanceof Error ? e.message : "Could not load members")
    );
  }, [slug, token, reloadKey]);

  const run = async (action: () => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    try {
      await action();
      setReloadKey((k) => k + 1);
      await refresh();
      return true;
    } catch (e) {
      setError(e instanceof Error ? e.message : "Something went wrong");
      return false;
    } finally {
      setBusy(false);
    }
  };

  const add = async (e: FormEvent) => {
    e.preventDefault();
    if (!token || roles.length === 0) return;
    if (await run(() => organisations.addMember(slug, { email: email.trim(), roles }, token))) setEmail("");
  };

  const toggleRole = (m: OrgMember, role: OrgRole) => {
    if (!token) return;
    const next = m.roles.includes(role) ? m.roles.filter((r) => r !== role) : [...m.roles, role];
    void run(() => organisations.updateMember(slug, m.id, { roles: next }, token));
  };

  return (
    <div className="mt-6 space-y-6">
      {manage && (
        <form onSubmit={add} className={`${ws.card} p-4 space-y-3`}>
          <h2 className="font-semibold flex items-center gap-2">
            <UserPlus className="w-4 h-4" /> Add a member
          </h2>
          <p className="text-sm text-muted">They need an EstateX account first. Use the email they registered with.</p>
          <input
            className={ws.input}
            type="email"
            required
            placeholder="name@example.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            aria-label="Member email"
          />
          <RolePicker selected={roles} onToggle={(r) => setRoles((s) => (s.includes(r) ? s.filter((x) => x !== r) : [...s, r]))} />
          <button className={ws.button} disabled={busy || roles.length === 0}>
            Add member
          </button>
        </form>
      )}

      {error && (
        <p role="alert" className="text-sm text-danger">
          {error}
        </p>
      )}

      {!members ? (
        <p className="text-muted">Loading…</p>
      ) : (
        <ul className="space-y-3">
          {members.map((m) => (
            <li key={m.id} className={`${ws.card} p-4 ${m.active ? "" : "opacity-60"}`}>
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div className="min-w-0">
                  <p className="font-semibold truncate">
                    {m.fullName} {!m.active && <span className="text-xs font-normal text-muted">(inactive)</span>}
                  </p>
                  <p className="text-sm text-muted break-all">{m.email}</p>
                </div>
                {manage && (
                  <button
                    type="button"
                    className={ws.buttonGhost}
                    disabled={busy}
                    onClick={() => token && run(() => organisations.updateMember(slug, m.id, { active: !m.active }, token))}
                  >
                    {m.active ? "Deactivate" : "Reactivate"}
                  </button>
                )}
              </div>
              {manage ? (
                <div className="mt-3">
                  <RolePicker selected={m.roles} onToggle={(r) => toggleRole(m, r)} disabled={busy || !m.active} />
                </div>
              ) : (
                <div className="mt-2 flex flex-wrap gap-2">
                  {m.roles.map((r) => (
                    <span key={r} className={ws.chip}>
                      {ROLE_LABELS[r]}
                    </span>
                  ))}
                </div>
              )}
              {m.divisions.length > 0 && (
                <p className="mt-2 text-xs text-muted">
                  Divisions: {m.divisions.filter((d) => enabledDivisions.includes(d)).map((d) => DIVISION_LABELS[d].name).join(", ")}
                </p>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function RolePicker({
  selected,
  onToggle,
  disabled,
}: {
  selected: OrgRole[];
  onToggle: (role: OrgRole) => void;
  disabled?: boolean;
}) {
  return (
    <div className="space-y-2">
      {[
        { title: "Staff", roles: STAFF_ROLES },
        { title: "Clients (portal access)", roles: EXTERNAL_ROLES },
      ].map((group) => (
        <div key={group.title}>
          <p className="text-xs text-muted mb-1">{group.title}</p>
          <div className="flex flex-wrap gap-2">
            {group.roles.map((r) => {
              const on = selected.includes(r);
              return (
                <button
                  key={r}
                  type="button"
                  aria-pressed={on}
                  disabled={disabled}
                  onClick={() => onToggle(r)}
                  className={`${ws.chip} min-h-9 px-3 disabled:opacity-50 ${on ? "bg-primary text-on-primary border-transparent" : ""}`}
                >
                  {on && <Check className="w-3.5 h-3.5 mr-1" />}
                  {ROLE_LABELS[r]}
                </button>
              );
            })}
          </div>
        </div>
      ))}
    </div>
  );
}
