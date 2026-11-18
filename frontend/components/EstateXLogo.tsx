/**
 * EstateX logo from the official artwork (colour-adjusted crops in public/brand/).
 *
 * Variants:
 *  - horizontal (default): emblem beside ESTATEX with the tagline underneath — navbar
 *  - full: the complete stacked logo — emblem, ESTATEX, tagline and services line
 *  - emblem: just the buildings mark — loaders, tight spaces
 *
 * Tones: onLight (navy/steel lettering, gold accent — readable on white) and onDark (silver,
 * bright gold accent — for navy/black backgrounds).
 *
 * `size` is the rendered height in px. Pass a className with a height (e.g. "h-10 sm:h-14")
 * to size it responsively instead; the image keeps its aspect ratio and can shrink to fit.
 */
const ASSETS = {
  horizontal: { file: "estatex-logo-horizontal", w: 1781, h: 300 },
  full: { file: "estatex-logo-full", w: 1400, h: 767 },
  emblem: { file: "estatex-icon", w: 320, h: 180 },
} as const;

export default function EstateXLogo({
  size = 40,
  variant = "horizontal",
  wordmark = true,
  tone = "onLight",
  className = "",
}: {
  size?: number;
  variant?: keyof typeof ASSETS;
  /** Legacy: wordmark={false} is the same as variant="emblem". */
  wordmark?: boolean;
  tone?: "onLight" | "onDark";
  className?: string;
}) {
  const v = wordmark ? variant : "emblem";
  const asset = ASSETS[v];
  const colour = tone === "onLight" ? "navy" : "silver";
  const responsive = /\bh-/.test(className);
  return (
    // eslint-disable-next-line @next/next/no-img-element -- static brand asset, sized by CSS
    <img
      src={`/brand/${asset.file}-${colour}.png`}
      alt={v === "emblem" ? "EstateX" : "EstateX — More Than Property. Building Possibilities."}
      width={Math.round((size * asset.w) / asset.h)}
      height={size}
      style={responsive ? undefined : { height: size, width: "auto" }}
      className={`block shrink min-w-0 max-w-full object-contain object-left select-none ${responsive ? "w-auto" : ""} ${className}`}
      draggable={false}
    />
  );
}
