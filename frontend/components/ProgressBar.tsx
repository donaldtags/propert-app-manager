export default function ProgressBar({ percent, tone = "forest" }: { percent: number; tone?: "forest" | "gold" }) {
  const clamped = Math.max(0, Math.min(100, percent));
  const fill = tone === "gold" ? "bg-gold-500" : "bg-forest-600";
  return (
    <div className="w-full h-2 bg-gray-100 rounded-full overflow-hidden" role="progressbar" aria-valuenow={clamped} aria-valuemin={0} aria-valuemax={100}>
      <div className={`h-full ${fill} rounded-full transition-all`} style={{ width: `${clamped}%` }} />
    </div>
  );
}
