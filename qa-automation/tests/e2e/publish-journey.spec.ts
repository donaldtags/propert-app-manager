import { test, expect } from "../../src/fixtures";

test("PREA approves and the negotiator publishes from the listing page", async ({ browser, api, agency: a, signIn }) => {
  const { listingId } = await api.readyForApproval(a.slug, a);
  const url = `/orgs/${a.slug}/listings/${listingId}`;

  const preaCtx = await browser.newContext();
  const prea = await preaCtx.newPage();
  await signIn(prea, a.prea);
  await prea.goto(url);
  await expect(prea.getByRole("button", { name: "Publish" })).toBeDisabled();
  await prea.getByRole("button", { name: "Approve as PREA" }).click();
  await expect(prea.getByRole("button", { name: "Approve as PREA" })).toHaveCount(0);
  await preaCtx.close();

  const negCtx = await browser.newContext();
  const neg = await negCtx.newPage();
  await signIn(neg, a.negotiator);
  await neg.goto(url);
  await expect(neg.getByRole("button", { name: "Approve as PREA" })).toHaveCount(0); // negotiators can't approve
  await neg.getByRole("button", { name: "Publish", exact: true }).click();
  await expect(neg.getByText("Published. The listing is live.")).toBeVisible();
  await negCtx.close();

  expect((await api.ok("GET", `/properties/${listingId}`)).status).toBe("AVAILABLE");
});
