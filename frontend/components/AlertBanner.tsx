import { AlertCircle, CheckCircle, Info, TriangleAlert, type LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

const VARIANTS = {
  error: { classes: "bg-red-50 border-red-200 text-red-700", Icon: AlertCircle },
  success: { classes: "bg-forest-50 border-forest-200 text-forest-700", Icon: CheckCircle },
  info: { classes: "bg-gold-50 border-gold-100 text-gold-700", Icon: Info },
  warning: { classes: "bg-amber-50 border-amber-200 text-amber-700", Icon: TriangleAlert },
} as const;

export default function AlertBanner({
  variant,
  icon,
  children,
  className = "",
}: {
  variant: keyof typeof VARIANTS;
  /** Override the variant's default icon (e.g. Globe for a diaspora notice). */
  icon?: LucideIcon;
  children: ReactNode;
  className?: string;
}) {
  const { classes, Icon: DefaultIcon } = VARIANTS[variant];
  const Icon = icon ?? DefaultIcon;
  return (
    <div className={`border rounded-xl px-4 py-3 text-sm flex items-center gap-2 ${classes} ${className}`}>
      <Icon className="w-4 h-4 shrink-0" />
      <span>{children}</span>
    </div>
  );
}
