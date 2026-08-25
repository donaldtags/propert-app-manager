export interface BarDatum {
  label: string;
  value: number;
  color?: string;
}

interface HorizontalBarChartProps {
  data: BarDatum[];
  color?: string;
}

export default function HorizontalBarChart({ data, color = "bg-forest-600" }: HorizontalBarChartProps) {
  const max = Math.max(1, ...data.map((d) => d.value));

  return (
    <div className="space-y-3">
      {data.map((d) => (
        <div key={d.label} title={`${d.label}: ${d.value.toLocaleString()}`}>
          <div className="flex items-center justify-between mb-1">
            <span className="text-xs text-gray-500">{d.label}</span>
            <span className="text-xs font-semibold text-gray-900">{d.value.toLocaleString()}</span>
          </div>
          <div className="h-2.5 bg-gray-100 rounded-full overflow-hidden">
            <div
              className={`h-full rounded-full ${d.color ?? color}`}
              style={{ width: `${Math.max(2, (d.value / max) * 100)}%` }}
            />
          </div>
        </div>
      ))}
    </div>
  );
}
