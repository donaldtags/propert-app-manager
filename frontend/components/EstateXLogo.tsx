/**
 * EstateX logo, built from the official artwork (public/brand/, colour-enhanced crops of
 * ESTATEX_logo_transparent.png).
 *  - onLight: navy/steel version so the metal shading stays visible on white
 *  - onDark:  the original silver
 * Also available for larger placements: estatex-lockup-{navy,silver}.png (emblem over the
 * wordmark) and estatex-logo-full-silver.png (with tagline, for dark backgrounds).
 */
const ICON_RATIO = 320 / 180;
const WORDMARK_RATIO = 900 / 111;

export default function EstateXLogo({
  size = 40,
  wordmark = true,
  tone = "onLight",
  wordmarkClassName = "",
  className = "",
}: {
  /** Height of the building emblem in px. */
  size?: number;
  wordmark?: boolean;
  tone?: "onLight" | "onDark";
  wordmarkClassName?: string;
  className?: string;
}) {
  const variant = tone === "onLight" ? "navy" : "silver";
  const wordmarkHeight = Math.round(size * 0.42);
  return (
    <span className={`inline-flex items-center gap-2.5 ${className}`} style={{ height: size }}>
      {/* eslint-disable-next-line @next/next/no-img-element -- tiny static brand asset */}
      <img
        src={`/brand/estatex-icon-${variant}.png`}
        alt="EstateX"
        width={Math.round(size * ICON_RATIO)}
        height={size}
        className="shrink-0 select-none"
        draggable={false}
      />
      {wordmark && (
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={`/brand/estatex-wordmark-${variant}.png`}
          alt=""
          width={Math.round(wordmarkHeight * WORDMARK_RATIO)}
          height={wordmarkHeight}
          className={`shrink-0 select-none ${wordmarkClassName}`}
          draggable={false}
        />
      )}
    </span>
  );
}
