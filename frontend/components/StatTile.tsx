import type { LucideIcon } from "lucide-react";

const TONE_CLASSES = {
  forest: "bg-forest-50 text-forest-600",
  gold: "bg-gold-50 text-gold-600",
  terracotta: "bg-terracotta-500/10 text-terracotta-600",
  red: "bg-red-50 text-red-600",
  gray: "bg-gray-100 text-gray-600",
} as const;

export default function StatTile({
  icon: Icon,
  label,
  value,
  sublabel,
  tone = "forest",
  /** Use for the 3-4 headline metrics on a dashboard so they read as more important than the rest. */
  emphasis = false,
}: {
  icon: LucideIcon;
  label: string;
  value: string | number;
  sublabel?: string;
  tone?: keyof typeof TONE_CLASSES;
  emphasis?: boolean;
}) {
  return (
    <div
      className={`bg-white border rounded-2xl shadow-sm p-5 ${
        emphasis ? "border-forest-200 ring-1 ring-forest-100" : "border-gray-200"
      }`}
    >
      <div className={`w-9 h-9 rounded-lg flex items-center justify-center mb-3 ${TONE_CLASSES[tone]}`}>
        <Icon className="w-4.5 h-4.5" />
      </div>
      <p className={`font-bold text-gray-900 truncate ${emphasis ? "text-3xl" : "text-2xl"}`} title={String(value)}>
        {value}
      </p>
      <p className="text-xs text-gray-500 mt-1">{label}</p>
      {sublabel && <p className="text-xs text-gray-400 mt-0.5">{sublabel}</p>}
    </div>
  );
}
