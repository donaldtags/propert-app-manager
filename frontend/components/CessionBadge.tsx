import { AlertTriangle } from "lucide-react";

export const CESSION_EXPLANATION =
  "Held under a cession agreement, not a registered title deed. Ask your conveyancer to confirm the chain of cessions and the developer's rights before paying anything.";

/** Risk marker for properties held under cession rather than registered title. */
export default function CessionBadge({ size = "sm", className = "" }: { size?: "sm" | "md"; className?: string }) {
  const sizing = size === "md" ? "text-xs px-3 py-1.5 gap-1" : "text-[11px] px-2 py-1 gap-1";
  return (
    <span
      title={CESSION_EXPLANATION}
      className={`inline-flex items-center font-bold rounded-full bg-amber-50 text-amber-800 border border-amber-300 ${sizing} ${className}`}
    >
      <AlertTriangle className={size === "md" ? "w-3.5 h-3.5" : "w-3 h-3"} aria-hidden />
      Cession
      <span className="sr-only">: {CESSION_EXPLANATION}</span>
    </span>
  );
}
