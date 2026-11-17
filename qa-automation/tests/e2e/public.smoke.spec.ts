import { test, expect } from "@playwright/test";
import { env } from "../../src/env";

/**
 * @smoke: read-only checks safe against ANY environment (local, CI, Render after a deploy).
 * No data is created and no admin account is needed.
 */
test.describe("Public site @smoke", () => {
  test("home page loads with EstateX branding @smoke", async ({ page }) => {
    const res = await page.goto("/");
    expect(res?.status()).toBe(200);
    await expect(page).toHaveTitle(/EstateX/);
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  });

  test("property search renders results or an empty state @smoke", async ({ page }) => {
    await page.goto("/properties");
    await expect(page.getByRole("heading", { name: /rentals|homes|properties/i }).first()).toBeVisible();
  });

  test("login page is reachable @smoke", async ({ page }) => {
    await page.goto("/login");
    await expect(page.getByRole("button", { name: /sign in|log in/i })).toBeVisible();
  });

  test("API is healthy and serves reference data @smoke", async ({ request }) => {
    const reits = await request.get(`${env.apiUrl}/investments/reits`);
    expect(reits.status()).toBe(200);
    const locations = await request.get(`${env.apiUrl}/locations`);
    expect(locations.status()).toBe(200);
    expect((await locations.json()).length).toBeGreaterThan(400);
    const org = await request.get(`${env.apiUrl}/orgs/${env.orgSlug}`);
    expect(org.status()).toBe(200);
  });

  test("installable PWA manifest is served @smoke", async ({ request }) => {
    const res = await request.get("/manifest.webmanifest");
    expect(res.status()).toBe(200);
    expect((await res.json()).name).toBe("EstateX");
  });

  test("both apps report which commit is deployed @smoke", async ({ request }) => {
    const fe = await request.get("/version.json");
    expect(fe.status()).toBe(200);
    const be = await request.get(`${env.apiUrl}/version`);
    expect(be.status()).toBe(200);
    const expected = process.env.EXPECTED_COMMIT;
    if (expected) {
      expect((await fe.json()).commit, "frontend commit").toBe(expected);
      expect((await be.json()).commit, "backend commit").toBe(expected);
    }
  });
});
