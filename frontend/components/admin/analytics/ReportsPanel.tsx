"use client";

import { useCallback, useEffect, useState } from "react";
import { CalendarClock, CheckCircle2, FileSpreadsheet, FileText, Mail, Send, Trash2, XCircle } from "lucide-react";
import { useAuth } from "@/lib/auth";
import {
  reportsApi, type ReportFilters, type ReportFormat, type ReportFrequency, type ReportRecipient, type ReportRunView,
  type ReportScheduleInput, type ReportScheduleView, type ReportTypeView,
} from "@/lib/api";

const FORMATS: { key: ReportFormat; label: string; Icon: typeof FileText }[] = [
  { key: "PDF", label: "PDF", Icon: FileText },
  { key: "XLSX", label: "Excel", Icon: FileSpreadsheet },
  { key: "CSV", label: "CSV", Icon: FileSpreadsheet },
];

const FREQUENCIES: { key: ReportFrequency; label: string; when: string }[] = [
  { key: "DAY", label: "Daily", when: "every day at 06:00, covering the day before" },
  { key: "WEEK", label: "Weekly", when: "Mondays at 06:00, covering the week before" },
  { key: "MONTH", label: "Monthly", when: "on the 1st at 06:00, covering the month before" },
  { key: "QUARTER", label: "Quarterly", when: "on the first day of each quarter, covering the quarter before" },
  { key: "YEAR", label: "Yearly", when: "on 1 January, covering the year before" },
];

const CATEGORIES = ["HOUSE", "FLAT", "TOWNHOUSE", "COTTAGE", "ROOM", "STAND", "COMMERCIAL", "INDUSTRIAL", "FARM"];

function day(iso?: string | null) {
  return iso ? new Date(iso).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" }) : "—";
}

function when(iso?: string | null) {
  return iso ? new Date(iso).toLocaleString(undefined, { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }) : "—";
}

function nice(s: string) {
  return s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, " ");
}

/** Reports: generate any report as PDF, Excel or CSV, schedule email delivery, and see what was sent. */
export default function ReportsPanel() {
  const { token } = useAuth();
  const [types, setTypes] = useState<ReportTypeView[]>([]);
  const [canSchedule, setCanSchedule] = useState(false);
  const [selected, setSelected] = useState<string>("");
  const [filters, setFilters] = useState<ReportFilters>({});
  const [busy, setBusy] = useState<ReportFormat | null>(null);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [runs, setRuns] = useState<ReportRunView[]>([]);
  const [schedules, setSchedules] = useState<ReportScheduleView[]>([]);
  const [loaded, setLoaded] = useState(false);

  const loadRuns = useCallback(() => {
    if (!token) return;
    reportsApi.runs(token).then(setRuns).catch(() => {});
  }, [token]);

  const loadSchedules = useCallback(() => {
    if (!token) return;
    reportsApi.schedules(token).then(setSchedules).catch(() => {});
  }, [token]);

  useEffect(() => {
    if (!token) return;
    reportsApi.catalogue(token)
      .then((c) => {
        setTypes(c.types);
        setCanSchedule(c.canSchedule);
        if (c.canSchedule) reportsApi.schedules(token).then(setSchedules).catch(() => {});
      })
      .catch((e) => setMsg({ ok: false, text: e instanceof Error ? e.message : "Couldn't load reports." }))
      .finally(() => setLoaded(true));
    reportsApi.runs(token).then(setRuns).catch(() => {});
  }, [token]);

  const type = types.find((t) => t.key === selected) ?? types[0];

  const download = async (format: ReportFormat) => {
    if (!token || !type) return;
    setBusy(format);
    setMsg(null);
    try {
      const { name, blob } = await reportsApi.download(token, type.key, format, filters);
      const a = document.createElement("a");
      a.href = URL.createObjectURL(blob);
      a.download = name;
      a.click();
      URL.revokeObjectURL(a.href);
      setMsg({ ok: true, text: `Downloaded ${name}.` });
      loadRuns();
    } catch (e) {
      setMsg({ ok: false, text: e instanceof Error ? e.message : "Couldn't create the report." });
    } finally {
      setBusy(null);
    }
  };

  const input = "border border-gray-200 rounded-lg px-2.5 py-1.5 bg-white text-sm";
  if (loaded && types.length === 0) {
    return <p className="text-sm text-gray-600 bg-white border border-gray-200 rounded-xl p-4">{msg?.text ?? "Your role can't export reports."}</p>;
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-semibold text-gray-900 tracking-tight">Reports</h2>
        <p className="text-sm text-gray-500 mt-0.5">
          Every report states when it was generated, the period it covers, the filters, its data coverage and how the figures are worked out.
        </p>
      </div>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)]">
        <section aria-labelledby="types-title" className="bg-white border border-gray-200 rounded-2xl p-2">
          <h3 id="types-title" className="sr-only">Choose a report</h3>
          <div role="radiogroup" aria-label="Report" className="divide-y divide-gray-100">
            {types.map((t) => {
              const on = type?.key === t.key;
              return (
                <button key={t.key} type="button" role="radio" aria-checked={on} onClick={() => { setSelected(t.key); setFilters({}); setMsg(null); }}
                  className={`w-full text-left px-3 py-2.5 rounded-xl ${on ? "bg-brand-50 ring-1 ring-brand-200" : "hover:bg-gray-50"}`}>
                  <span className="block text-sm font-medium text-gray-900">{t.label}</span>
                  <span className="block text-xs text-gray-500 mt-0.5">{t.description}</span>
                </button>
              );
            })}
          </div>
        </section>

        {type && (
          <section aria-labelledby="gen-title" className="bg-white border border-gray-200 rounded-2xl p-5 self-start">
            <h3 id="gen-title" className="text-base font-semibold text-gray-900">{type.label}</h3>
            <p className="text-sm text-gray-500 mt-0.5">{type.description}</p>
            <div className="mt-4 grid grid-cols-2 gap-3">
              <label className="text-xs font-medium text-gray-600">
                From
                <input type="date" className={`${input} mt-1 w-full`} value={filters.from ?? type.defaultFrom}
                  onChange={(e) => setFilters({ ...filters, from: e.target.value })} />
              </label>
              <label className="text-xs font-medium text-gray-600">
                To
                <input type="date" className={`${input} mt-1 w-full`} value={filters.to ?? type.defaultTo} disabled={type.key === "DAILY_PLATFORM"}
                  onChange={(e) => setFilters({ ...filters, to: e.target.value })} />
              </label>
              <label className="text-xs font-medium text-gray-600">
                City
                <input className={`${input} mt-1 w-full`} placeholder="All cities" value={filters.city ?? ""} maxLength={80}
                  onChange={(e) => setFilters({ ...filters, city: e.target.value, suburb: e.target.value ? filters.suburb : "" })} />
              </label>
              <label className="text-xs font-medium text-gray-600">
                Suburb
                <input className={`${input} mt-1 w-full`} placeholder={filters.city ? "All suburbs" : "Choose a city first"} disabled={!filters.city}
                  value={filters.suburb ?? ""} maxLength={80} onChange={(e) => setFilters({ ...filters, suburb: e.target.value })} />
              </label>
              <label className="text-xs font-medium text-gray-600 col-span-2">
                Property category
                <select className={`${input} mt-1 w-full`} value={filters.category ?? ""} onChange={(e) => setFilters({ ...filters, category: e.target.value })}>
                  <option value="">All categories</option>
                  {CATEGORIES.map((c) => <option key={c} value={c}>{nice(c)}</option>)}
                </select>
              </label>
            </div>
            <p className="mt-2 text-xs text-gray-500">
              Default: the last completed {type.grain.toLowerCase()} ({day(type.defaultFrom)}{type.defaultFrom !== type.defaultTo ? ` to ${day(type.defaultTo)}` : ""}).
              {type.key === "GEOGRAPHIC" && " This report always describes the latest snapshot."}
            </p>
            <div className="mt-4 flex flex-wrap gap-2">
              {FORMATS.map(({ key, label, Icon }) => (
                <button key={key} type="button" onClick={() => download(key)} disabled={busy !== null}
                  className={`inline-flex items-center gap-1.5 text-sm font-medium rounded-lg px-3.5 py-2 disabled:opacity-60 ${key === "PDF"
                    ? "text-white bg-brand-600 hover:bg-brand-700" : "text-gray-700 border border-gray-200 hover:border-gray-300 bg-white"}`}>
                  {busy === key ? <span className="w-4 h-4 rounded-full border-2 border-current border-t-transparent animate-spin" aria-hidden /> : <Icon className="w-4 h-4" aria-hidden />}
                  {busy === key ? "Preparing…" : label}
                </button>
              ))}
            </div>
            {msg && (
              <p role={msg.ok ? "status" : "alert"} className={`mt-3 text-sm inline-flex items-center gap-1.5 ${msg.ok ? "text-gray-700" : "text-red-700"}`}>
                {msg.ok ? <CheckCircle2 className="w-4 h-4" aria-hidden /> : <XCircle className="w-4 h-4" aria-hidden />} {msg.text}
              </p>
            )}
          </section>
        )}
      </div>

      {canSchedule && <Schedules types={types} schedules={schedules} reload={() => { loadSchedules(); loadRuns(); }} />}

      <section aria-labelledby="runs-title" className="bg-white border border-gray-200 rounded-2xl p-5">
        <h3 id="runs-title" className="text-sm font-semibold text-gray-900">Recent reports</h3>
        {runs.length === 0 ? (
          <p className="mt-2 text-sm text-gray-500">No reports produced yet.</p>
        ) : (
          <div className="mt-3 overflow-x-auto">
            <table className="w-full text-sm min-w-[640px]">
              <thead>
                <tr className="text-left text-xs text-gray-500">
                  <th className="py-1 font-medium">When</th><th className="py-1 font-medium">Report</th><th className="py-1 font-medium">Period</th>
                  <th className="py-1 font-medium">How</th><th className="py-1 font-medium">Outcome</th>
                </tr>
              </thead>
              <tbody>
                {runs.map((r) => (
                  <tr key={r.id} className="border-t border-gray-100 align-top">
                    <td className="py-2 pr-3 whitespace-nowrap text-gray-700">{when(r.generatedAt)}</td>
                    <td className="py-2 pr-3 text-gray-900">{r.reportLabel} <span className="text-gray-500">· {r.format === "XLSX" ? "Excel" : r.format}</span></td>
                    <td className="py-2 pr-3 whitespace-nowrap text-gray-700">{day(r.periodFrom)}{r.periodTo !== r.periodFrom ? ` – ${day(r.periodTo)}` : ""}</td>
                    <td className="py-2 pr-3 text-gray-700">
                      {r.trigger === "DOWNLOAD" ? `Downloaded by ${r.requestedBy ?? "—"}` : `${r.trigger === "SCHEDULE" ? "Scheduled" : "Sent now"} to ${r.recipients}`}
                    </td>
                    <td className="py-2 text-gray-700">
                      <span className="inline-flex items-center gap-1">
                        {r.status === "OK" ? <CheckCircle2 className="w-4 h-4" aria-hidden /> : <XCircle className="w-4 h-4" aria-hidden />}
                        {r.status === "OK" ? "Done" : nice(r.status)}
                      </span>
                      {r.error && <span className="block text-xs text-gray-500">{r.error}</span>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}

function Schedules({ types, schedules, reload }: { types: ReportTypeView[]; schedules: ReportScheduleView[]; reload: () => void }) {
  const { token } = useAuth();
  const blank: ReportScheduleInput = { name: "", reportType: types[0]?.key ?? "", format: "PDF", frequency: "WEEK", recipientIds: [], enabled: true };
  const [editing, setEditing] = useState<{ id: number | null; form: ReportScheduleInput } | null>(null);
  const [recipients, setRecipients] = useState<ReportRecipient[]>([]);
  const [msg, setMsg] = useState("");

  const reportType = editing?.form.reportType;
  useEffect(() => {
    if (!token || !reportType) return;
    let live = true;
    reportsApi.recipients(token, reportType).then((r) => { if (live) setRecipients(r); }).catch(() => {});
    return () => { live = false; };
  }, [token, reportType]);

  const save = async () => {
    if (!token || !editing) return;
    try {
      if (editing.id == null) await reportsApi.createSchedule(token, editing.form);
      else await reportsApi.updateSchedule(token, editing.id, editing.form);
      setMsg(`Saved “${editing.form.name}”.`);
      setEditing(null);
      reload();
    } catch (e) {
      setMsg(e instanceof Error ? e.message : "Couldn't save the schedule.");
    }
  };

  const toForm = (s: ReportScheduleView): ReportScheduleInput => ({
    name: s.name, reportType: s.reportType, format: s.format, frequency: s.frequency, city: s.city ?? "", suburb: s.suburb ?? "",
    category: s.category ?? "", recipientIds: s.recipients.map((r) => r.id), enabled: s.enabled,
  });

  const sendNow = async (s: ReportScheduleView) => {
    if (!token) return;
    setMsg(`Sending “${s.name}”…`);
    try {
      const r = await reportsApi.sendNow(token, s.id);
      setMsg(r.status === "OK" ? `Sent “${s.name}” to ${r.recipients} recipient(s).` : `${nice(r.status)}: ${r.detail ?? ""}`);
    } catch (e) {
      setMsg(e instanceof Error ? e.message : "Couldn't send.");
    }
    reload();
  };

  const remove = async (s: ReportScheduleView) => {
    if (!token || !window.confirm(`Stop and delete the schedule “${s.name}”?`)) return;
    await reportsApi.deleteSchedule(token, s.id).catch(() => {});
    reload();
  };

  const input = "border border-gray-200 rounded-lg px-2.5 py-1.5 bg-white text-sm";
  const f = editing?.form;
  const set = (patch: Partial<ReportScheduleInput>) => setEditing((e) => (e ? { ...e, form: { ...e.form, ...patch } } : e));

  return (
    <section aria-labelledby="sched-title" className="bg-white border border-gray-200 rounded-2xl p-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 id="sched-title" className="inline-flex items-center gap-2 text-sm font-semibold text-gray-900">
          <CalendarClock className="w-4 h-4" aria-hidden /> Scheduled delivery
        </h3>
        {!editing && (
          <button type="button" onClick={() => { setEditing({ id: null, form: blank }); setMsg(""); }}
            className="text-sm font-medium text-white bg-brand-600 hover:bg-brand-700 rounded-lg px-3 py-1.5">New schedule</button>
        )}
      </div>
      <p className="text-xs text-gray-500 mt-0.5">
        Reports are emailed as attachments at 06:00 (Harare) after each period ends. They only go to staff accounts allowed to see them,
        checked again at every delivery.
      </p>
      {msg && <p role="status" className="mt-2 text-sm text-gray-700">{msg}</p>}

      {editing && f && (
        <div className="mt-4 border border-gray-200 rounded-xl p-4 space-y-3">
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="text-xs font-medium text-gray-600">Name
              <input className={`${input} mt-1 w-full`} value={f.name} maxLength={80} onChange={(e) => set({ name: e.target.value })} placeholder="e.g. Monday rentals for analysts" />
            </label>
            <label className="text-xs font-medium text-gray-600">Report
              <select className={`${input} mt-1 w-full`} value={f.reportType} onChange={(e) => set({ reportType: e.target.value, recipientIds: [] })}>
                {types.map((t) => <option key={t.key} value={t.key}>{t.label}</option>)}
              </select>
            </label>
            <label className="text-xs font-medium text-gray-600">How often
              <select className={`${input} mt-1 w-full`} value={f.frequency} onChange={(e) => set({ frequency: e.target.value as ReportFrequency })}>
                {FREQUENCIES.map((x) => <option key={x.key} value={x.key}>{x.label}</option>)}
              </select>
              <span className="block mt-1 font-normal text-gray-500">Sent {FREQUENCIES.find((x) => x.key === f.frequency)?.when}.</span>
            </label>
            <label className="text-xs font-medium text-gray-600">Format
              <select className={`${input} mt-1 w-full`} value={f.format} onChange={(e) => set({ format: e.target.value as ReportFormat })}>
                {FORMATS.map((x) => <option key={x.key} value={x.key}>{x.label}</option>)}
              </select>
            </label>
            <label className="text-xs font-medium text-gray-600">City (optional)
              <input className={`${input} mt-1 w-full`} value={f.city ?? ""} maxLength={80} onChange={(e) => set({ city: e.target.value })} placeholder="All cities" />
            </label>
            <label className="text-xs font-medium text-gray-600">Category (optional)
              <select className={`${input} mt-1 w-full`} value={f.category ?? ""} onChange={(e) => set({ category: e.target.value })}>
                <option value="">All categories</option>
                {CATEGORIES.map((c) => <option key={c} value={c}>{nice(c)}</option>)}
              </select>
            </label>
          </div>
          <fieldset>
            <legend className="text-xs font-medium text-gray-600">Recipients (staff allowed to see this report)</legend>
            {recipients.length === 0 ? (
              <p className="mt-1 text-sm text-gray-500">No staff account can receive this report yet.</p>
            ) : (
              <div className="mt-1 grid gap-1 sm:grid-cols-2">
                {recipients.map((r) => (
                  <label key={r.id} className="inline-flex items-center gap-2 text-sm text-gray-800">
                    <input type="checkbox" checked={f.recipientIds.includes(r.id)}
                      onChange={(e) => set({ recipientIds: e.target.checked ? [...f.recipientIds, r.id] : f.recipientIds.filter((x) => x !== r.id) })} />
                    <span className="truncate">{r.name} <span className="text-gray-500">({r.email})</span></span>
                  </label>
                ))}
              </div>
            )}
          </fieldset>
          <label className="inline-flex items-center gap-2 text-sm text-gray-700">
            <input type="checkbox" checked={f.enabled} onChange={(e) => set({ enabled: e.target.checked })} /> Active
          </label>
          <div className="flex gap-2">
            <button type="button" onClick={save} disabled={!f.name.trim() || f.recipientIds.length === 0}
              className="text-sm font-medium text-white bg-brand-600 hover:bg-brand-700 rounded-lg px-3 py-1.5 disabled:opacity-50">Save schedule</button>
            <button type="button" onClick={() => setEditing(null)} className="text-sm font-medium text-gray-700 border border-gray-200 rounded-lg px-3 py-1.5">Cancel</button>
          </div>
        </div>
      )}

      {schedules.length === 0 && !editing ? (
        <p className="mt-3 text-sm text-gray-500">No scheduled reports.</p>
      ) : (
        <ul className="mt-3 space-y-2">
          {schedules.map((s) => (
            <li key={s.id} className="border border-gray-200 rounded-xl p-3 flex flex-wrap items-start justify-between gap-3">
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-gray-900">
                  {s.name} {!s.enabled && <span className="text-xs font-normal text-gray-500">(paused)</span>}
                </p>
                <p className="text-xs text-gray-600 mt-0.5">
                  {s.reportLabel} · {FREQUENCIES.find((x) => x.key === s.frequency)?.label} · {s.format === "XLSX" ? "Excel" : s.format}
                  {s.city && ` · ${s.city}`}{s.category && ` · ${nice(s.category)}`}
                </p>
                <p className="text-xs text-gray-500 mt-0.5 inline-flex flex-wrap items-center gap-1">
                  <Mail className="w-3 h-3" aria-hidden /> {s.recipients.map((r) => r.name).join(", ") || "no recipients"}
                  {" · "}next {s.enabled ? when(s.nextRunAt) : "—"}
                  {s.lastRunAt && <> · last {when(s.lastRunAt)}: {s.lastStatus === "OK" ? "sent" : (s.lastStatus ?? "").toLowerCase()}</>}
                </p>
                {s.lastError && <p className="text-xs text-gray-500">{s.lastError}</p>}
              </div>
              <div className="flex gap-1.5">
                <button type="button" onClick={() => sendNow(s)} className="inline-flex items-center gap-1 text-xs font-medium text-gray-700 border border-gray-200 rounded-lg px-2.5 py-1.5">
                  <Send className="w-3.5 h-3.5" aria-hidden /> Send now
                </button>
                <button type="button" onClick={() => { setEditing({ id: s.id, form: toForm(s) }); setMsg(""); }}
                  className="text-xs font-medium text-gray-700 border border-gray-200 rounded-lg px-2.5 py-1.5">Edit</button>
                <button type="button" onClick={() => remove(s)} aria-label={`Delete ${s.name}`} className="p-1.5 text-gray-400 hover:text-red-700">
                  <Trash2 className="w-4 h-4" aria-hidden />
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

