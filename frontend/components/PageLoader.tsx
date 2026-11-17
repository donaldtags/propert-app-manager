import EstateXLogo from "./EstateXLogo";

export default function PageLoader() {
  return (
    <div className="flex-1 min-h-[50vh] flex flex-col items-center justify-center gap-4 py-16">
      <div className="relative flex items-center justify-center w-16 h-16">
        <span className="absolute inset-0 rounded-full border-4 border-brand-100" />
        <span className="absolute inset-0 rounded-full border-4 border-brand-600 border-t-transparent animate-spin" />
        <EstateXLogo size={28} wordmark={false} />
      </div>
      <p className="text-sm font-medium text-gray-500">Finding your next home…</p>
    </div>
  );
}
