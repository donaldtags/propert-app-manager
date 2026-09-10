const SUCCESS = new Set([
  "ACTIVE", "SIGNED", "AVAILABLE", "RESOLVED", "RELEASED", "SUCCESSFUL",
  "APPROVED", "VERIFIED", "COMPLETED", "CONFIRMED", "FUNDED", "PAID",
]);
const WARNING = new Set([
  "PENDING", "DRAFT", "SENT", "ASSIGNED", "IN_PROGRESS", "REQUESTED",
  "INITIATED", "CREATED", "UNVERIFIED", "SUBMITTED", "VERIFICATION_REQUIRED",
  "UNDER_REVIEW", "LEASE_PREPARATION", "RESERVED", "DELAYED", "PAUSED",
]);
const DANGER = new Set([
  "CANCELLED", "DISPUTED", "REJECTED", "FAILED", "ENDED", "DECLINED", "INACTIVE", "OPEN",
]);
// Resolved, but not via a straightforward success — kept visually distinct from both.
const NOTABLE = new Set(["REFUNDED", "SOLD", "OCCUPIED", "EXITED"]);

const TONE_CLASSES = {
  success: "bg-forest-100 text-forest-700",
  warning: "bg-amber-100 text-amber-700",
  danger: "bg-red-100 text-red-700",
  notable: "bg-terracotta-500/10 text-terracotta-600",
  neutral: "bg-gray-100 text-gray-600",
} as const;

function toneFor(status: string): keyof typeof TONE_CLASSES {
  const s = status.toUpperCase();
  if (SUCCESS.has(s)) return "success";
  if (WARNING.has(s)) return "warning";
  if (DANGER.has(s)) return "danger";
  if (NOTABLE.has(s)) return "notable";
  return "neutral";
}

function label(status: string): string {
  return status
    .split("_")
    .map((w) => w.charAt(0) + w.slice(1).toLowerCase())
    .join(" ");
}

export default function StatusBadge({ status, className = "" }: { status: string; className?: string }) {
  return (
    <span
      className={`text-xs font-semibold px-2 py-0.5 rounded-full whitespace-nowrap ${TONE_CLASSES[toneFor(status)]} ${className}`}
    >
      {label(status)}
    </span>
  );
}
