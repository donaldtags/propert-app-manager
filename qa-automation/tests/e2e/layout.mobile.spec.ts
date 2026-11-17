import { test, expectNoHorizontalScroll, expect } from "../../src/fixtures";

const PAGES = ["", "/pipeline", "/listings", "/contacts", "/enquiries", "/viewings", "/settings"];

for (const mode of ["light", "dark"] as const) {
  test(`workspace screens fit a phone without sideways scrolling (${mode})`, async ({ page, api, agency: a, signIn }) => {
    const listing = await api.publishedListing(a.slug, a);
    await signIn(page, a.orgAdmin, { mode });
    for (const path of [...PAGES, `/listings/${listing}`, `/listings/${listing}/intake`, `/listings/${listing}/marketing`, `/listings/${listing}/activity`]) {
      await page.goto(`/orgs/${a.slug}${path}`);
      await expect(page.locator(".ex-theme")).toHaveAttribute("data-mode", mode);
      await expect(page.locator("main")).toBeVisible();
      await expectNoHorizontalScroll(page);
    }
  });
}
