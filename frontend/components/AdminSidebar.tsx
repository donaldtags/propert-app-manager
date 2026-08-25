"use client";

import { useState } from "react";
import { Menu, X, ShieldCheck } from "lucide-react";
import type { LucideIcon } from "lucide-react";

export interface AdminSidebarItem<T extends string> {
  key: T;
  label: string;
  icon: LucideIcon;
  count?: number;
}

interface AdminSidebarProps<T extends string> {
  items: AdminSidebarItem<T>[];
  activeKey: T;
  onSelect: (key: T) => void;
}

export default function AdminSidebar<T extends string>({ items, activeKey, onSelect }: AdminSidebarProps<T>) {
  const [mobileOpen, setMobileOpen] = useState(false);

  const renderItems = (onItemClick: () => void) => (
    <nav className="flex-1 overflow-y-auto py-4 px-3 space-y-1">
      {items.map((item) => {
        const active = item.key === activeKey;
        const Icon = item.icon;
        return (
          <button
            key={item.key}
            onClick={() => {
              onSelect(item.key);
              onItemClick();
            }}
            className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-colors ${
              active ? "bg-forest-50 text-forest-700" : "text-gray-600 hover:bg-gray-50 hover:text-gray-900"
            }`}
          >
            <Icon className={`w-4 h-4 shrink-0 ${active ? "text-forest-600" : "text-gray-400"}`} />
            <span className="flex-1 text-left truncate">{item.label}</span>
            {!!item.count && (
              <span
                className={`text-[10px] font-semibold rounded-full px-1.5 py-0.5 ${
                  active ? "bg-forest-100 text-forest-700" : "bg-gray-100 text-gray-600"
                }`}
              >
                {item.count}
              </span>
            )}
          </button>
        );
      })}
    </nav>
  );

  return (
    <>
      {/* Mobile top bar */}
      <div className="md:hidden flex items-center gap-2 px-4 py-3 border-b border-gray-200 bg-white shrink-0">
        <button onClick={() => setMobileOpen(true)} className="p-1.5 -ml-1.5 text-gray-600">
          <Menu className="w-5 h-5" />
        </button>
        <span className="text-sm font-semibold text-gray-900 flex items-center gap-1.5">
          <ShieldCheck className="w-4 h-4 text-forest-600" /> Admin Portal
        </span>
      </div>

      {/* Desktop sidebar */}
      <aside className="hidden md:flex md:flex-col w-64 shrink-0 border-r border-gray-200 bg-white">
        <div className="px-4 py-4 border-b border-gray-100">
          <span className="text-sm font-bold text-gray-900 flex items-center gap-2">
            <ShieldCheck className="w-5 h-5 text-forest-600" /> Admin Portal
          </span>
        </div>
        {renderItems(() => {})}
      </aside>

      {/* Mobile drawer */}
      {mobileOpen && (
        <div className="md:hidden fixed inset-0 z-40">
          <div className="absolute inset-0 bg-black/30" onClick={() => setMobileOpen(false)} />
          <aside className="absolute left-0 top-0 bottom-0 w-72 bg-white border-r border-gray-200 flex flex-col">
            <div className="px-4 py-4 border-b border-gray-100 flex items-center justify-between">
              <span className="text-sm font-bold text-gray-900 flex items-center gap-2">
                <ShieldCheck className="w-5 h-5 text-forest-600" /> Admin Portal
              </span>
              <button onClick={() => setMobileOpen(false)} className="p-1 text-gray-500">
                <X className="w-5 h-5" />
              </button>
            </div>
            {renderItems(() => setMobileOpen(false))}
          </aside>
        </div>
      )}
    </>
  );
}
