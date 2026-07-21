"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Scale, X } from "lucide-react";
import { getCompareIds, clearCompare, MAX_COMPARE } from "@/lib/compareListings";

export default function CompareBar() {
  const router = useRouter();
  const [ids, setIds] = useState<number[]>([]);

  useEffect(() => {
    const sync = () => setIds(getCompareIds());
    sync();
    window.addEventListener("compare-listings-changed", sync);
    return () => window.removeEventListener("compare-listings-changed", sync);
  }, []);

  if (ids.length === 0) return null;

  return (
    <div className="fixed bottom-4 left-1/2 -translate-x-1/2 z-40 bg-white border border-gray-200 shadow-xl rounded-2xl px-5 py-3 flex items-center gap-4">
      <div className="flex items-center gap-2 text-sm font-medium text-gray-700">
        <Scale className="w-4 h-4 text-blue-600" />
        {ids.length} of {MAX_COMPARE} selected to compare
      </div>
      <button
        onClick={() => router.push(`/properties/compare?ids=${ids.join(",")}`)}
        disabled={ids.length < 2}
        className="bg-blue-600 hover:bg-blue-700 disabled:opacity-40 text-white text-sm font-semibold px-4 py-2 rounded-xl transition-colors"
      >
        Compare
      </button>
      <button onClick={clearCompare} className="text-gray-400 hover:text-gray-600">
        <X className="w-4 h-4" />
      </button>
    </div>
  );
}
