import { test, expect } from "../../src/fixtures";

/**
 * Field work on a phone with no signal: intake and enquiries are saved on the device and sync
 * exactly once when the connection comes back. Pages are opened online first (as a negotiator
 * would before driving out); the save itself happens offline.
 */
test.describe("Offline field work", () => {
  test("a full intake and an enquiry saved offline sync exactly once on reconnect", async ({ page, context, api, agency: a, signIn }) => {
    const title = `Offline intake ${Date.now()}`;
    await signIn(page, a.negotiator);
    await page.goto(`/orgs/${a.slug}/listings/new`);
    await expect(page.getByRole("heading", { name: "New listing" })).toBeVisible();

    await context.setOffline(true);
    await page.getByLabel("Title", { exact: true }).fill(title);
    await page.getByLabel("Suburb").fill("Greendale");
    await page.getByLabel("Asking price").fill("185000");
    await page.getByLabel("Title type").selectOption("TITLE_DEED");
    await page.getByLabel("Deed / title / cession no.").fill("DT 900/2010");
    await page.getByLabel("Owner consents to photos being shown").check();
    await page.getByLabel("Owner consents to the address being shown").check();
    await page.getByRole("button", { name: "Save on this device" }).click();
    // Offline it must confirm in place (navigating needs the network and would hang on a spinner).
    await expect(page.getByRole("status").filter({ hasText: title })).toContainText("Saved on this device");
    await expect(page.getByLabel("Title", { exact: true })).toHaveValue(""); // ready for the next one

    await page.getByRole("button", { name: "Capture an enquiry" }).click();
    await page.getByLabel("Name").fill("Offline Enquirer");
    await page.getByLabel("Phone / WhatsApp").fill("0779 123 456");
    await page.getByRole("button", { name: "Save enquiry" }).click();
    await expect(page.getByRole("link", { name: /Offline · \d+ waiting/ })).toBeVisible();

    await context.setOffline(false);
    await expect(page.getByRole("link", { name: "Synced" })).toBeVisible({ timeout: 45_000 });

    const listings = await api.ok("GET", `/orgs/${a.slug}/listings`, { token: a.negotiator.token });
    const mine = listings.filter((l: { title: string }) => l.title === title);
    expect(mine).toHaveLength(1);
    const detail = await api.ok("GET", `/orgs/${a.slug}/listings/${mine[0].propertyId}`, { token: a.negotiator.token });
    expect(detail.compliance.titleReference).toBe("DT 900/2010");
    expect(detail.compliance.ownerConsentPhotos && detail.compliance.ownerConsentAddress).toBe(true);
    const enquiries = await api.ok("GET", `/orgs/${a.slug}/enquiries`, { token: a.negotiator.token });
    expect(enquiries.filter((e: { name: string }) => e.name === "Offline Enquirer")).toHaveLength(1);
  });

  test("a stage move the server can't confirm lands in Sync with the reason", async ({ page, api, agency: a, signIn }) => {
    const owner = await api.contact(a.slug, a.negotiator, "Refused Move Owner");
    const title = `Refused move ${Date.now()}`;
    await api.ok("POST", `/orgs/${a.slug}/pipeline`, { token: a.negotiator.token, data: { title, ownerContactId: owner } });
    await signIn(page, a.negotiator);
    await page.goto(`/orgs/${a.slug}/pipeline`);
    await page.getByRole("radio", { name: "List" }).click();
    await page.getByRole("combobox", { name: new RegExp(`Move ${title}`) }).selectOption("MANDATE_SIGNED");
    await expect(page.getByRole("link", { name: /need attention/ })).toBeVisible({ timeout: 20_000 });
    await page.goto(`/orgs/${a.slug}/sync`);
    await expect(page.getByText("Link the listing to this deal first.")).toBeVisible();
  });
});
