import type { Page } from "@playwright/test";
import { test, expect } from "../../src/fixtures";

/**
 * Every main screen at real device widths, from small Android phones to wide desktops.
 * Fails if anything makes the page scroll sideways or pokes off the right edge (things inside
 * deliberate horizontal scrollers, like chip rows, are fine).
 */
const VIEWPORTS = [
  { name: "320 small phone", width: 320, height: 640 },
  { name: "360 Android", width: 360, height: 780 },
  { name: "390 iPhone", width: 390, height: 844 },
  { name: "768 tablet", width: 768, height: 1024 },
  { name: "1024 tablet landscape", width: 1024, height: 768 },
  { name: "1366 laptop", width: 1366, height: 768 },
  { name: "1920 desktop", width: 1920, height: 1080 },
];

async function overflowProblems(page: Page): Promise<string[]> {
  return page.evaluate(() => {
    const vw = document.documentElement.clientWidth;
    const problems: string[] = [];
    const docOverflow = document.documentElement.scrollWidth - vw;
    if (docOverflow > 1) problems.push(`page scrolls sideways by ${docOverflow}px`);
    const clipped = (el: Element) => {
      for (let p = el.parentElement; p && p !== document.body; p = p.parentElement) {
        const ox = getComputedStyle(p).overflowX;
        if (ox === "auto" || ox === "scroll" || ox === "hidden" || ox === "clip") return true;
      }
      return false;
    };
    for (const el of Array.from(document.body.querySelectorAll("*"))) {
      const r = el.getBoundingClientRect();
      if (r.width === 0 || r.height === 0) continue;
      const style = getComputedStyle(el);
      if (style.position === "fixed" || style.visibility === "hidden") continue;
      if (r.right > vw + 1 && !clipped(el)) {
        const id = el.id ? `#${el.id}` : "";
        const cls = typeof el.className === "string" ? "." + el.className.trim().split(/\s+/).slice(0, 3).join(".") : "";
        const text = (el.textContent ?? "").trim().slice(0, 30);
        problems.push(`${el.tagName.toLowerCase()}${id}${cls} sticks out ${Math.round(r.right - vw)}px "${text}"`);
        if (problems.length > 6) break;
      }
    }
    return problems;
  });
}

test.describe("Responsive layout", () => {
  test.describe.configure({ mode: "parallel" });

  for (const vp of VIEWPORTS) {
    test(`every screen fits at ${vp.name}`, async ({ page, api, signIn, agency }) => {
      test.setTimeout(180_000);
      const stay = await api.shortStay();
      const guest = await api.register("QA Guest", "TENANT");
      await api.ok("POST", `/stays/${stay.id}/bookings`, {
        token: guest.token,
        data: { checkIn: new Date(Date.now() + 20 * 864e5).toISOString().slice(0, 10), checkOut: new Date(Date.now() + 23 * 864e5).toISOString().slice(0, 10), guests: 1 },
      });
      await page.setViewportSize({ width: vp.width, height: vp.height });

      const publicPages = ["/", "/properties", "/properties?listingType=SHORT_STAY", `/properties/${stay.id}`, "/login", "/register",
        "/forgot-password", "/investments", "/services", "/ai", "/diaspora"];
      const guestPages = ["/trips", "/tenant", "/profile", "/payments", "/messages", "/settings/security"];
      const hostPages = ["/hosting", "/landlord"];
      const orgPages = [`/orgs/${agency.slug}`, `/orgs/${agency.slug}/listings`, `/orgs/${agency.slug}/listings/new`,
        `/orgs/${agency.slug}/enquiries`, `/orgs/${agency.slug}/contacts`, `/orgs/${agency.slug}/pipeline`,
        `/orgs/${agency.slug}/viewings`, `/orgs/${agency.slug}/sync`];

      const failures: string[] = [];
      const check = async (path: string) => {
        await page.goto(path);
        await page.waitForLoadState("networkidle").catch(() => {});
        await page.waitForTimeout(300);
        for (const p of await overflowProblems(page)) failures.push(`${path}: ${p}`);
      };
      for (const p of publicPages) await check(p);
      const ctxGuest = page;
      await signIn(ctxGuest, guest);
      for (const p of guestPages) await check(p);
      await signIn(page, stay.host);
      for (const p of hostPages) await check(p);
      await signIn(page, agency.orgAdmin);
      for (const p of orgPages) await check(p);

      expect(failures, failures.join("\n")).toEqual([]);
    });
  }
});
