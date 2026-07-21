"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Bell, DollarSign, FileText, Wrench, ShieldAlert } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { notifications as notificationsApi } from "@/lib/api";
import type { AppNotification, NotificationType } from "@/lib/types";

const TYPE_ICONS: Record<NotificationType, typeof Bell> = {
  RENT_REMINDER: DollarSign,
  LEASE_EXPIRY: FileText,
  MAINTENANCE_UPDATE: Wrench,
  SECURITY_ALERT: ShieldAlert,
};

const TYPE_ROUTES: Record<NotificationType, string> = {
  RENT_REMINDER: "/payments",
  LEASE_EXPIRY: "/leases",
  MAINTENANCE_UPDATE: "/maintenance",
  SECURITY_ALERT: "/verification",
};

const POLL_INTERVAL_MS = 60_000;

export default function NotificationBell() {
  const { user, token } = useAuth();
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<AppNotification[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    if (!user || !token) return;
    const refreshCount = () => {
      notificationsApi.unreadCount(token).then((r) => setUnreadCount(r.unreadCount)).catch(() => {});
    };
    refreshCount();
    const interval = setInterval(refreshCount, POLL_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [user, token]);

  const toggleOpen = () => {
    const next = !open;
    setOpen(next);
    if (next && token && !loaded) {
      notificationsApi.mine(token).then(setItems).catch(() => {}).finally(() => setLoaded(true));
    }
  };

  const handleClick = async (n: AppNotification) => {
    if (!token) return;
    if (!n.read) {
      try {
        await notificationsApi.markRead(n.id, token);
        setItems((prev) => prev.map((i) => (i.id === n.id ? { ...i, read: true } : i)));
        setUnreadCount((c) => Math.max(0, c - 1));
      } catch {}
    }
    setOpen(false);
    router.push(TYPE_ROUTES[n.type]);
  };

  const handleMarkAllRead = async () => {
    if (!token) return;
    try {
      await notificationsApi.markAllRead(token);
      setItems((prev) => prev.map((i) => ({ ...i, read: true })));
      setUnreadCount(0);
    } catch {}
  };

  if (!user) return null;

  return (
    <div className="relative">
      <button onClick={toggleOpen} className="relative p-2 text-gray-600 hover:text-blue-600 transition-colors">
        <Bell className="w-5 h-5" />
        {unreadCount > 0 && (
          <span className="absolute -top-0.5 -right-0.5 bg-red-500 text-white text-[10px] font-bold rounded-full min-w-[16px] h-4 px-1 flex items-center justify-center">
            {unreadCount > 9 ? "9+" : unreadCount}
          </span>
        )}
      </button>
      {open && (
        <div className="absolute right-0 top-full mt-2 w-80 bg-white rounded-lg shadow-lg border border-gray-200 py-1 z-50 max-h-96 overflow-y-auto">
          <div className="flex items-center justify-between px-4 py-2 border-b border-gray-100">
            <p className="text-sm font-semibold text-gray-900">Notifications</p>
            {unreadCount > 0 && (
              <button onClick={handleMarkAllRead} className="text-xs text-blue-600 hover:underline">
                Mark all read
              </button>
            )}
          </div>
          {items.length === 0 ? (
            <p className="text-sm text-gray-400 text-center py-8">You're all caught up.</p>
          ) : (
            items.map((n) => {
              const Icon = TYPE_ICONS[n.type];
              return (
                <button
                  key={n.id}
                  onClick={() => handleClick(n)}
                  className={`w-full text-left flex items-start gap-3 px-4 py-3 hover:bg-gray-50 transition-colors ${
                    n.read ? "" : "bg-blue-50/50"
                  }`}
                >
                  <div className="w-8 h-8 rounded-full bg-gray-100 flex items-center justify-center shrink-0">
                    <Icon className="w-4 h-4 text-gray-500" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className={`text-sm ${n.read ? "text-gray-700" : "font-semibold text-gray-900"}`}>{n.title}</p>
                    <p className="text-xs text-gray-500 mt-0.5 line-clamp-2">{n.message}</p>
                    <p className="text-[11px] text-gray-400 mt-1">{new Date(n.createdAt).toLocaleDateString()}</p>
                  </div>
                  {!n.read && <div className="w-2 h-2 rounded-full bg-blue-600 shrink-0 mt-1.5" />}
                </button>
              );
            })
          )}
        </div>
      )}
    </div>
  );
}
