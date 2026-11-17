import { useSyncExternalStore, type CSSProperties } from "react";
import type { OrgThemeTokens } from "./types";

/** EstateX brand tokens. Also the fallback for any token an organisation leaves unset. */
export const ESTATEX_THEME: Required<OrgThemeTokens> = {
  lightPrimary: "#0B1D32",
  lightSurface: "#F5F2EA",
  lightText: "#0B1D32",
  // Brand Slate Grey (#788391) is 3.6:1 on Warm Ivory, below WCAG AA for body text, so muted
  // text uses a darker slate. #788391 is kept for borders/large decorative text.
  lightMuted: "#5E6873",
  lightAccent: "#C9CED4",
  darkPrimary: "#C9CED4",
  darkSurface: "#0B1D32",
  darkText: "#F5F2EA",
  darkMuted: "#A3ABB5",
  darkAccent: "#788391",
  headingFont: "Montserrat",
  bodyFont: "Inter",
};

/** Fonts the app bundles via next/font, mapped to their CSS variables. */
const FONT_VARS: Record<string, string> = {
  Montserrat: "var(--font-montserrat)",
  Inter: "var(--font-inter)",
};

const IVORY = "#F5F2EA";
const NAVY = "#0B1D32";

export function withDefaults(theme?: OrgThemeTokens | null): Required<OrgThemeTokens> {
  const merged = { ...ESTATEX_THEME };
  if (theme) {
    for (const [k, v] of Object.entries(theme) as [keyof OrgThemeTokens, string | undefined][]) {
      if (v) merged[k] = v;
    }
  }
  return merged;
}

function channel(c: number): number {
  const s = c / 255;
  return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
}

export function parseHex(hex: string): [number, number, number] | null {
  const m = /^#?([0-9a-f]{6})$/i.exec(hex.trim());
  if (!m) return null;
  const n = parseInt(m[1], 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

export function relativeLuminance(hex: string): number {
  const rgb = parseHex(hex);
  if (!rgb) return 0;
  const [r, g, b] = rgb.map(channel);
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

/** WCAG 2.x contrast ratio, 1..21. */
export function contrastRatio(a: string, b: string): number {
  const la = relativeLuminance(a);
  const lb = relativeLuminance(b);
  const [hi, lo] = la > lb ? [la, lb] : [lb, la];
  return (hi + 0.05) / (lo + 0.05);
}

/** Text colour for content placed on `background`: ivory or navy, whichever reads better. */
export function onColor(background: string): string {
  return contrastRatio(background, IVORY) >= contrastRatio(background, NAVY) ? IVORY : NAVY;
}

export interface ContrastIssue {
  mode: "light" | "dark";
  pair: string;
  ratio: number;
  required: number;
}

/** Checks the pairs that carry body text against WCAG AA (4.5:1). Empty list = passes. */
export function contrastIssues(theme?: OrgThemeTokens | null): ContrastIssue[] {
  const t = withDefaults(theme);
  const checks: [ContrastIssue["mode"], string, string, string][] = [
    ["light", "text on surface", t.lightText, t.lightSurface],
    ["light", "muted text on surface", t.lightMuted, t.lightSurface],
    ["light", "button text on primary", onColor(t.lightPrimary), t.lightPrimary],
    ["dark", "text on surface", t.darkText, t.darkSurface],
    ["dark", "muted text on surface", t.darkMuted, t.darkSurface],
    ["dark", "button text on primary", onColor(t.darkPrimary), t.darkPrimary],
  ];
  return checks
    .map(([mode, pair, fg, bg]) => ({ mode, pair, ratio: Math.round(contrastRatio(fg, bg) * 100) / 100, required: 4.5 }))
    .filter((c) => c.ratio < c.required);
}

/** CSS custom properties for an `.ex-theme` element (see globals.css). */
export function themeStyle(theme?: OrgThemeTokens | null): CSSProperties {
  const t = withDefaults(theme);
  return {
    "--org-light-primary": t.lightPrimary,
    "--org-light-on-primary": onColor(t.lightPrimary),
    "--org-light-surface": t.lightSurface,
    "--org-light-text": t.lightText,
    "--org-light-muted": t.lightMuted,
    "--org-light-accent": t.lightAccent,
    "--org-dark-primary": t.darkPrimary,
    "--org-dark-on-primary": onColor(t.darkPrimary),
    "--org-dark-surface": t.darkSurface,
    "--org-dark-text": t.darkText,
    "--org-dark-muted": t.darkMuted,
    "--org-dark-accent": t.darkAccent,
    "--org-heading-font": FONT_VARS[t.headingFont] ?? FONT_VARS.Montserrat,
    "--org-body-font": FONT_VARS[t.bodyFont] ?? FONT_VARS.Inter,
  } as CSSProperties;
}

export type ColorMode = "light" | "dark" | "system";
const MODE_KEY = "ex_color_mode";

export function readColorMode(): ColorMode {
  try {
    const v = window.localStorage.getItem(MODE_KEY);
    return v === "light" || v === "dark" || v === "system" ? v : "system";
  } catch {
    return "system";
  }
}

const modeListeners = new Set<() => void>();

export function writeColorMode(mode: ColorMode): void {
  try {
    window.localStorage.setItem(MODE_KEY, mode);
  } catch {
    // storage unavailable (private mode); the choice just won't persist
  }
  modeListeners.forEach((l) => l());
}

function subscribeMode(listener: () => void) {
  modeListeners.add(listener);
  return () => modeListeners.delete(listener);
}

/** Light / dark / system preference for workspace screens, persisted per browser. */
export function useColorMode(): [ColorMode, (mode: ColorMode) => void] {
  const mode = useSyncExternalStore(subscribeMode, readColorMode, () => "system" as ColorMode);
  return [mode, writeColorMode];
}
