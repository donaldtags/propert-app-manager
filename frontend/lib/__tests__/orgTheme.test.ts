import { describe, expect, it } from "vitest";
import { contrastIssues, contrastRatio, ESTATEX_THEME, onColor, themeStyle, withDefaults } from "../orgTheme";

describe("contrastRatio", () => {
  it("matches the WCAG reference values", () => {
    expect(contrastRatio("#000000", "#FFFFFF")).toBeCloseTo(21, 1);
    expect(contrastRatio("#FFFFFF", "#FFFFFF")).toBeCloseTo(1, 5);
  });
});

describe("EstateX brand theme", () => {
  it("passes WCAG AA for body text in light and dark mode", () => {
    expect(contrastIssues(ESTATEX_THEME)).toEqual([]);
  });

  it("uses ivory text on navy buttons and navy text on silver buttons", () => {
    expect(onColor("#0B1D32")).toBe("#F5F2EA");
    expect(onColor("#C9CED4")).toBe("#0B1D32");
  });

  it("documents why brand slate grey is not used for small muted text on ivory", () => {
    expect(contrastRatio("#788391", "#F5F2EA")).toBeLessThan(4.5);
    expect(contrastRatio(ESTATEX_THEME.lightMuted, "#F5F2EA")).toBeGreaterThanOrEqual(4.5);
  });

  it("contains no gold", () => {
    const gold = /^#(C9A227|D4AF37|FFD700)$/i;
    expect(Object.values(ESTATEX_THEME).some((v) => gold.test(v))).toBe(false);
  });
});

describe("withDefaults / themeStyle", () => {
  it("fills unset tokens from the EstateX theme and keeps org overrides", () => {
    const t = withDefaults({ lightPrimary: "#123456" });
    expect(t.lightPrimary).toBe("#123456");
    expect(t.darkSurface).toBe(ESTATEX_THEME.darkSurface);
  });

  it("flags an unreadable org theme", () => {
    const issues = contrastIssues({ lightText: "#DDDDDD", lightSurface: "#FFFFFF" });
    expect(issues.map((i) => `${i.mode}:${i.pair}`)).toContain("light:text on surface");
  });

  it("maps unknown fonts to the bundled defaults", () => {
    const style = themeStyle({ headingFont: "Comic Sans" }) as Record<string, string>;
    expect(style["--org-heading-font"]).toBe("var(--font-montserrat)");
  });
});
