import { test, expect } from "../../src/fixtures";

test("campaign, Instagram image and a seller report the owner can open without logging in", async ({ page, request, api, agency: a, signIn }) => {
  const id = await api.publishedListing(a.slug, a);
  await signIn(page, a.negotiator);
  await page.goto(`/orgs/${a.slug}/listings/${id}/marketing`);

  await page.getByRole("button", { name: "Start campaign" }).click();
  await expect(page.getByText(/Day 1 of 21/)).toBeVisible();
  await page.locator('section[aria-labelledby="camp-h"] input[type=checkbox]').first().check();
  await expect(page.getByText(/1\/25 done/)).toBeVisible();

  await page.getByRole("button", { name: "Create image" }).click();
  const img = page.getByRole("img", { name: /Instagram image/ });
  await expect(img).toBeVisible();
  expect(await img.evaluate((i: HTMLImageElement) => [i.naturalWidth, i.naturalHeight])).toEqual([1080, 1080]);

  await page.getByRole("button", { name: /Create this week/ }).click();
  const pdfLink = page.getByRole("link", { name: "Open PDF" }).first();
  await expect(pdfLink).toBeVisible();
  const pdf = await request.get((await pdfLink.getAttribute("href"))!); // no auth header: like the owner
  expect(pdf.status()).toBe(200);
  expect((await pdf.body()).subarray(0, 4).toString()).toBe("%PDF");
  await expect(page.getByRole("link", { name: "WhatsApp owner" }).first()).toHaveAttribute("href", /wa\.me\/263/);
});
