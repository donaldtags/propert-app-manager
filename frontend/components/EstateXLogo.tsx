/**
 * PLACEHOLDER EstateX mark until the official artwork lands in public/orgs/estatex/.
 * A navy tile with a silver "X" whose upper strokes meet like a roof line. Swap this component's
 * SVG for the real logo once the asset is provided (silver master for dark backgrounds, navy
 * version for light).
 */
export default function EstateXLogo({
  size = 40,
  wordmark = true,
  tone = "onLight",
  wordmarkClassName = "",
  className = "",
}: {
  size?: number;
  wordmark?: boolean;
  /** onLight: navy tile + navy wordmark. onDark: silver tile + ivory wordmark. */
  tone?: "onLight" | "onDark";
  wordmarkClassName?: string;
  className?: string;
}) {
  const tile = tone === "onLight" ? "#0B1D32" : "#C9CED4";
  const mark = tone === "onLight" ? "#C9CED4" : "#0B1D32";
  const text = tone === "onLight" ? "#0B1D32" : "#F5F2EA";
  return (
    <span className={`inline-flex items-center gap-2.5 ${className}`} style={{ height: size }}>
      <svg viewBox="0 0 64 64" style={{ height: size, width: size }} role="img" aria-label="EstateX" className="shrink-0">
        <rect width="64" height="64" rx="12" fill={tile} />
        <path d="M18 46 L46 18 M18 18 L32 32" stroke={mark} strokeWidth="6" strokeLinecap="round" />
        <path d="M36 36 L46 46" stroke={mark} strokeWidth="6" strokeLinecap="round" />
        <path d="M14 24 L32 10 L50 24" fill="none" stroke={mark} strokeWidth="3" strokeLinejoin="round" opacity="0.55" />
      </svg>
      {wordmark && (
        <span
          className={`font-heading font-semibold tracking-[0.18em] ${wordmarkClassName}`}
          style={{ fontSize: size * 0.42, color: text }}
        >
          ESTATEX
        </span>
      )}
    </span>
  );
}
