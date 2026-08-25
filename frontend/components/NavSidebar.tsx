"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import type { LucideIcon } from "lucide-react";
import {
  LayoutDashboard,
  MessageCircle,
  IdCard,
  Settings,
  FileText,
  DollarSign,
  Wrench,
  Lock,
  Building2,
  TrendingUp,
  ShieldCheck,
  Menu,
  X,
} from "lucide-react";
import type { UserRole } from "@/lib/types";
import { dashboardPathFor } from "@/lib/dashboardRoute";

interface NavItem {
  href: string;
  label: string;
  icon: LucideIcon;
}

const BUSINESS_ROLES: UserRole[] = ["LANDLORD", "AGENT", "DEVELOPER", "PRIVATE"];

function buildNavItems(roles: UserRole[]): NavItem[] {
  const items: NavItem[] = [{ href: dashboardPathFor(roles), label: "Dashboard", icon: LayoutDashboard }];

  const add = (item: NavItem) => {
    if (!items.some((i) => i.href === item.href)) items.push(item);
  };

  if (roles.includes("TENANT")) {
    add({ href: "/leases", label: "Leases", icon: FileText });
    add({ href: "/payments", label: "Payments", icon: DollarSign });
    add({ href: "/maintenance", label: "Maintenance", icon: Wrench });
    add({ href: "/escrow", label: "Escrow", icon: Lock });
  }

  if (roles.some((r) => BUSINESS_ROLES.includes(r))) {
    add({ href: "/leases", label: "Leases", icon: FileText });
    add({ href: "/payments", label: "Payments", icon: DollarSign });
    add({ href: "/maintenance", label: "Maintenance", icon: Wrench });
    add({ href: "/properties", label: "Browse Properties", icon: Building2 });
  }

  if (roles.includes("INVESTOR")) {
    add({ href: "/investments", label: "My Investments", icon: TrendingUp });
    add({ href: "/properties", label: "Browse Properties", icon: Building2 });
  }

  if (roles.includes("ADMIN")) {
    add({ href: "/admin", label: "Admin Portal", icon: ShieldCheck });
  }

  add({ href: "/messages", label: "Messages", icon: MessageCircle });
  add({ href: "/verification", label: "Verification", icon: IdCard });
  add({ href: "/settings/security", label: "Settings", icon: Settings });

  return items;
}

interface NavSidebarProps {
  roles: UserRole[];
}

export default function NavSidebar({ roles }: NavSidebarProps) {
  const pathname = usePathname();
  const [mobileOpen, setMobileOpen] = useState(false);
  const items = buildNavItems(roles);

  const isActive = (href: string) => pathname === href || pathname.startsWith(`${href}/`);

  const renderItems = (onItemClick: () => void) => (
    <nav className="flex-1 overflow-y-auto py-4 px-3 space-y-1">
      {items.map((item) => {
        const active = isActive(item.href);
        const Icon = item.icon;
        return (
          <Link
            key={item.href}
            href={item.href}
            onClick={onItemClick}
            className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-colors ${
              active ? "bg-forest-50 text-forest-700" : "text-gray-600 hover:bg-gray-50 hover:text-gray-900"
            }`}
          >
            <Icon className={`w-4 h-4 shrink-0 ${active ? "text-forest-600" : "text-gray-400"}`} />
            <span className="truncate">{item.label}</span>
          </Link>
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
        <span className="text-sm font-semibold text-gray-900">Menu</span>
      </div>

      {/* Desktop sidebar */}
      <aside className="hidden md:flex md:flex-col w-64 shrink-0 border-r border-gray-200 bg-white">
        {renderItems(() => {})}
      </aside>

      {/* Mobile drawer */}
      {mobileOpen && (
        <div className="md:hidden fixed inset-0 z-40">
          <div className="absolute inset-0 bg-black/30" onClick={() => setMobileOpen(false)} />
          <aside className="absolute left-0 top-0 bottom-0 w-72 bg-white border-r border-gray-200 flex flex-col">
            <div className="px-4 py-4 border-b border-gray-100 flex items-center justify-between">
              <span className="text-sm font-bold text-gray-900">Menu</span>
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
