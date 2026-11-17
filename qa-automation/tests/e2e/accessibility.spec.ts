import AxeBuilder from "@axe-core/playwright";
import { test, expect } from "../../src/fixtures";

/**
 * Automated accessibility (axe-core, WCAG 2.1 A/AA rules). Fails on CRITICAL violations; serious
 * and lower are attached to the report so they're visible without blocking every build.
 */
async function audit(page: import("@playwright/test").Page, name: string, testInfo: import("@playwright/test").TestInfo) {
  const results = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze();
  await testInfo.attach(`axe-${name}.json`, { body: JSON.stringify(results.violations, null, 2), contentType: "application/json" });
  const critical = results.violations.filter((v) => v.impact === "critical");
  expect(critical.map((v) => `${v.id}: ${v.help} (${v.nodes.length})`), `critical a11y issues on ${name}`).toEqual([]);
}

test("public pages have no critical accessibility issues", async ({ page }, testInfo) => {
  for (const [name, path] of [["home", "/"], ["search", "/properties"], ["login", "/login"]]) {
    await page.goto(path);
    await audit(page, name, testInfo);
  }
});

test("workspace pages have no critical accessibility issues", async ({ page, api, agency: a, signIn }, testInfo) => {
  const listing = await api.publishedListing(a.slug, a);
  await signIn(page, a.negotiator);
  for (const [name, path] of [["listings", "/listings"], ["listing", `/listings/${listing}`], ["pipeline", "/pipeline"], ["contacts", "/contacts"]]) {
    await page.goto(`/orgs/${a.slug}${path}`);
    await expect(page.locator("main")).toBeVisible();
    await audit(page, name, testInfo);
  }
});
