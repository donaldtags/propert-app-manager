import type { LucideIcon } from "lucide-react";
import Link from "next/link";

export default function EmptyState({
  icon: Icon,
  title,
  hint,
  action,
  compact = false,
}: {
  icon: LucideIcon;
  title: string;
  hint?: string;
  action?: { label: string; href: string };
  /** Tighter padding for use inside an already-small card, vs. a full page-level empty state. */
  compact?: boolean;
}) {
  return (
    <div className={`text-center ${compact ? "py-8" : "py-16"}`}>
      <Icon className={`mx-auto mb-3 text-gray-300 ${compact ? "w-8 h-8" : "w-12 h-12"}`} />
      <p className={`font-medium text-gray-500 ${compact ? "text-sm" : "text-base"}`}>{title}</p>
      {hint && <p className="text-xs text-gray-400 mt-1">{hint}</p>}
      {action && (
        <Link
          href={action.href}
          className="inline-block text-sm font-medium text-forest-600 hover:underline mt-3"
        >
          {action.label}
        </Link>
      )}
    </div>
  );
}
