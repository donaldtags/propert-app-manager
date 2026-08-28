/**
 * The terrace mark: a stemmed "P" whose bowl is built from stepped terraces rather than a
 * smooth curve — a nod to terraced land and dry-stone walling (echoing Great Zimbabwe)
 * rendered abstractly, not as a literal illustration.
 */
export default function PrimeNestLogo({
  size = 40,
  wordmark = true,
  wordmarkClassName = "",
  className = "",
}: {
  size?: number;
  wordmark?: boolean;
  wordmarkClassName?: string;
  className?: string;
}) {
  return (
    <span className={`inline-flex items-center gap-2.5 ${className}`} style={{ height: size }}>
      <svg viewBox="0 0 200 200" style={{ height: size, width: size }} aria-hidden="true" className="shrink-0">
        <rect x="60" y="30" width="28" height="150" rx="6" fill="#1F5D42" />
        <rect x="88" y="30" width="70" height="18" rx="3" fill="#C9A227" />
        <rect x="88" y="52" width="58" height="18" rx="3" fill="#C9A227" />
        <rect x="88" y="74" width="44" height="18" rx="3" fill="#C9A227" />
        <rect x="88" y="96" width="28" height="18" rx="3" fill="#C9A227" />
      </svg>
      {wordmark && (
        <span
          className={`font-semibold text-charcoal-900 tracking-tight ${wordmarkClassName}`}
          style={{ fontSize: size * 0.5 }}
        >
          PrimeNest
        </span>
      )}
    </span>
  );
}
