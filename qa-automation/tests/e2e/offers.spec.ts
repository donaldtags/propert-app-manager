import { test, expect } from "../../src/fixtures";

test("accepting an offer below the owner's minimum asks for the owner's written instruction", async ({ page, api, agency: a, signIn }) => {
  const id = await api.publishedListing(a.slug, a, { minimumCents: 23_000_000 });
  const buyer = await api.contact(a.slug, a.negotiator, "QA UI Buyer");
  await api.ok("POST", `/orgs/${a.slug}/listings/${id}/offers`, { token: a.negotiator.token, data: { buyerContactId: buyer, amountCents: 21_000_000 } });

  await signIn(page, a.negotiator);
  await page.goto(`/orgs/${a.slug}/listings/${id}/activity`);
  const card = page.locator("li").filter({ hasText: "QA UI Buyer" }).first();
  await expect(card.getByText(/Below the owner's minimum/)).toBeVisible();
  await card.getByRole("button", { name: "Owner accepts" }).click();
  const instruction = card.getByLabel(/Owner's written instruction/);
  await expect(instruction).toBeVisible();
  await instruction.fill("WhatsApp from owner, QA");
  await card.getByRole("button", { name: "Confirm" }).click();
  await expect(card.getByText("Accepted", { exact: true })).toBeVisible();
  expect((await api.ok("GET", `/properties/${id}`, { token: a.negotiator.token })).status).toBe("RESERVED");
});
