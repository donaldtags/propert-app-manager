import type { StayBookingStatus } from "@/lib/types";

const STYLES: Record<StayBookingStatus, { label: string; cls: string }> = {
  PENDING: { label: "Waiting for host", cls: "bg-amber-100 text-amber-800" },
  AWAITING_PAYMENT: { label: "Awaiting payment", cls: "bg-blue-100 text-blue-800" },
  CONFIRMED: { label: "Confirmed", cls: "bg-emerald-100 text-emerald-800" },
  COMPLETED: { label: "Completed", cls: "bg-gray-100 text-gray-700" },
  DECLINED: { label: "Declined", cls: "bg-red-100 text-red-700" },
  CANCELLED: { label: "Cancelled", cls: "bg-gray-100 text-gray-600" },
  EXPIRED: { label: "Expired", cls: "bg-gray-100 text-gray-600" },
};

export default function StayStatusBadge({ status }: { status: StayBookingStatus }) {
  const s = STYLES[status];
  return <span className={`shrink-0 whitespace-nowrap text-xs font-semibold px-2 py-0.5 rounded-full ${s.cls}`}>{s.label}</span>;
}

export function stayMoney(amount: number, currency: string) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency, maximumFractionDigits: amount % 1 ? 2 : 0 }).format(amount);
}
