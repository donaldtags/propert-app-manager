import { CheckCircle } from "lucide-react";
import { JOURNEY_STAGES, type JourneyStage } from "@/lib/journeyStage";

export default function JourneyStepper({ stage, label }: { stage: JourneyStage; label?: string }) {
  const currentIndex = JOURNEY_STAGES.findIndex((s) => s.stage === stage);

  return (
    <div>
      {label && <p className="text-xs font-medium text-gray-700 mb-2 truncate">{label}</p>}
      <div className="flex items-start gap-0.5 overflow-x-auto pb-1">
        {JOURNEY_STAGES.map((s, i) => {
          const done = i < currentIndex;
          const active = i === currentIndex;
          return (
            <div key={s.stage} className="flex items-start shrink-0">
              <div className="flex flex-col items-center w-14">
                <div
                  className={`w-5 h-5 rounded-full flex items-center justify-center text-[10px] font-bold ${
                    active
                      ? "bg-forest-600 text-white ring-2 ring-forest-200"
                      : done
                        ? "bg-forest-600 text-white"
                        : "bg-gray-100 text-gray-400"
                  }`}
                >
                  {done ? <CheckCircle className="w-3 h-3" /> : i + 1}
                </div>
                <span className={`text-[9px] mt-1 text-center leading-tight ${active ? "text-forest-700 font-semibold" : "text-gray-400"}`}>
                  {s.label}
                </span>
              </div>
              {i < JOURNEY_STAGES.length - 1 && (
                <div className={`h-0.5 w-3 mt-2.5 ${done ? "bg-forest-600" : "bg-gray-200"}`} />
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
