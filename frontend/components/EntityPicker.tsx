"use client";

import { useEffect, useRef, useState } from "react";
import { ChevronDown, Search } from "lucide-react";

export interface PickerOption {
  id: number;
  label: string;
  sublabel?: string;
}

interface EntityPickerProps {
  label: string;
  options: PickerOption[];
  value: number | null;
  onChange: (id: number) => void;
  placeholder?: string;
  loading?: boolean;
  emptyMessage?: string;
  required?: boolean;
  disabled?: boolean;
}

export default function EntityPicker({
  label,
  options,
  value,
  onChange,
  placeholder = "Select...",
  loading = false,
  emptyMessage = "No matches found",
  required = false,
  disabled = false,
}: EntityPickerProps) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
        setQuery("");
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const selected = options.find((o) => o.id === value) ?? null;
  const filtered = query.trim()
    ? options.filter((o) =>
        `${o.label} ${o.sublabel ?? ""}`.toLowerCase().includes(query.trim().toLowerCase())
      )
    : options;

  return (
    <div ref={containerRef} className="relative">
      <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">
        {label}
      </label>
      <button
        type="button"
        disabled={disabled || loading}
        onClick={() => setOpen((o) => !o)}
        className="w-full flex items-center justify-between border border-gray-200 rounded-xl px-4 py-2.5 text-sm text-left outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100 bg-white disabled:bg-gray-50 disabled:text-gray-400 transition-colors"
      >
        <span className={`truncate ${selected ? "text-gray-900" : "text-gray-400"}`}>
          {loading ? "Loading..." : selected ? selected.label : placeholder}
        </span>
        <ChevronDown className={`w-4 h-4 text-gray-400 shrink-0 transition-transform ${open ? "rotate-180" : ""}`} />
      </button>

      {/* Keep a real input in the form tree so HTML5 `required` validation still fires */}
      <input type="hidden" required={required} value={value ?? ""} onChange={() => {}} />

      {open && !disabled && !loading && (
        <div className="absolute z-30 mt-1 w-full bg-white border border-gray-200 rounded-xl shadow-lg max-h-72 overflow-hidden flex flex-col">
          <div className="p-2 border-b border-gray-100">
            <div className="relative">
              <Search className="w-3.5 h-3.5 text-gray-400 absolute left-2.5 top-1/2 -translate-y-1/2" />
              <input
                autoFocus
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search..."
                className="w-full pl-8 pr-3 py-1.5 text-sm outline-none border border-gray-200 rounded-lg focus:border-blue-500"
              />
            </div>
          </div>
          <div className="overflow-y-auto">
            {filtered.length === 0 ? (
              <p className="text-sm text-gray-400 px-4 py-3">{emptyMessage}</p>
            ) : (
              filtered.map((o) => (
                <button
                  key={o.id}
                  type="button"
                  onClick={() => {
                    onChange(o.id);
                    setOpen(false);
                    setQuery("");
                  }}
                  className={`w-full text-left px-4 py-2 text-sm transition-colors hover:bg-blue-50 ${
                    o.id === value ? "bg-blue-50 text-blue-700 font-medium" : "text-gray-700"
                  }`}
                >
                  <div className="truncate">{o.label}</div>
                  {o.sublabel && <div className="text-xs text-gray-400 truncate">{o.sublabel}</div>}
                </button>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
}
