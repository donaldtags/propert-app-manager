"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Bookmark, Trash2, Users } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { savedViewsApi, type SavedViewDto, type SavedViewFilters } from "@/lib/api";

/**
 * Save the current filters of a dashboard under a name, and re-apply saved ones. Views are
 * personal unless shared, in which case other staff can apply (but not change) them.
 */
export default function SavedViews({ page, current, onApply }: {
  page: string;
  current: SavedViewFilters;
  onApply: (filters: SavedViewFilters) => void;
}) {
  const { token } = useAuth();
  const [open, setOpen] = useState(false);
  const [views, setViews] = useState<SavedViewDto[]>([]);
  const [name, setName] = useState("");
  const [shared, setShared] = useState(false);
  const [msg, setMsg] = useState("");
  const box = useRef<HTMLDivElement>(null);

  const load = useCallback(() => {
    if (!token) return;
    savedViewsApi.list(token, page).then(setViews).catch(() => {});
  }, [token, page]);
  useEffect(load, [load]);

  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => { if (box.current && !box.current.contains(e.target as Node)) setOpen(false); };
    const esc = (e: KeyboardEvent) => { if (e.key === "Escape") setOpen(false); };
    document.addEventListener("mousedown", close);
    document.addEventListener("keydown", esc);
    return () => { document.removeEventListener("mousedown", close); document.removeEventListener("keydown", esc); };
  }, [open]);

  const save = async () => {
    if (!token || !name.trim()) { setMsg("Give the view a name."); return; }
    const existing = views.find((v) => v.mine && v.name.toLowerCase() === name.trim().toLowerCase());
    try {
      const body = { name: name.trim(), page, filters: current, shared };
      if (existing) await savedViewsApi.update(token, existing.id, body);
      else await savedViewsApi.create(token, body);
      setMsg(existing ? `Updated “${name.trim()}”.` : `Saved “${name.trim()}”.`);
      setName("");
      load();
    } catch (e) {
      setMsg(e instanceof Error ? e.message : "Couldn't save the view.");
    }
  };

  const remove = async (v: SavedViewDto) => {
    if (!token || !window.confirm(`Delete the saved view “${v.name}”?`)) return;
    await savedViewsApi.remove(token, v.id).catch(() => {});
    load();
  };

  return (
    <div className="relative" ref={box}>
      <button type="button" aria-expanded={open} aria-haspopup="dialog" onClick={() => { setOpen(!open); setMsg(""); }}
        className="inline-flex items-center gap-1.5 text-sm font-medium text-gray-700 border border-gray-200 hover:border-gray-300 bg-white rounded-lg px-3 py-2">
        <Bookmark className="w-4 h-4" aria-hidden /> Saved views{views.length > 0 && <span className="text-gray-500">({views.length})</span>}
      </button>
      {open && (
        <div role="dialog" aria-label="Saved views" className="absolute right-0 z-30 mt-2 w-80 max-w-[calc(100vw-2rem)] bg-white border border-gray-200 rounded-xl shadow-lg p-3">
          {views.length === 0 ? (
            <p className="text-sm text-gray-500 px-1 py-2">No saved views for this page yet.</p>
          ) : (
            <ul className="max-h-64 overflow-auto divide-y divide-gray-100">
              {views.map((v) => (
                <li key={v.id} className="flex items-center gap-2 py-1.5">
                  <button type="button" onClick={() => { onApply(v.filters); setOpen(false); }}
                    className="flex-1 min-w-0 text-left text-sm text-gray-800 hover:text-gray-950 hover:underline truncate">
                    {v.name}
                  </button>
                  {v.shared && (
                    <span className="inline-flex items-center gap-1 text-[11px] text-gray-500" title={v.mine ? "Shared with other staff" : `Shared by ${v.owner}`}>
                      <Users className="w-3 h-3" aria-hidden /> {v.mine ? "shared" : v.owner}
                    </span>
                  )}
                  {v.mine && (
                    <button type="button" onClick={() => remove(v)} aria-label={`Delete ${v.name}`} className="p-1 text-gray-400 hover:text-red-700">
                      <Trash2 className="w-3.5 h-3.5" aria-hidden />
                    </button>
                  )}
                </li>
              ))}
            </ul>
          )}
          <div className="mt-2 pt-2 border-t border-gray-100 space-y-2">
            <label className="block text-xs font-medium text-gray-600" htmlFor={`sv-name-${page}`}>Save the current filters as</label>
            <div className="flex gap-2">
              <input id={`sv-name-${page}`} value={name} maxLength={80} onChange={(e) => setName(e.target.value)}
                onKeyDown={(e) => { if (e.key === "Enter") save(); }} placeholder="e.g. Harare 2-bed rentals"
                className="flex-1 min-w-0 border border-gray-200 rounded-lg px-2.5 py-1.5 text-sm" />
              <button type="button" onClick={save} className="text-sm font-medium text-white bg-brand-600 hover:bg-brand-700 rounded-lg px-3 py-1.5">Save</button>
            </div>
            <label className="inline-flex items-center gap-2 text-xs text-gray-600">
              <input type="checkbox" checked={shared} onChange={(e) => setShared(e.target.checked)} /> Share with other staff
            </label>
            {msg && <p role="status" className="text-xs text-gray-600">{msg}</p>}
          </div>
        </div>
      )}
    </div>
  );
}
