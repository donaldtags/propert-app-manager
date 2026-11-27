import { test, expect } from "../../src/fixtures";

test.describe("Short stays in the browser", () => {
  test("a guest picks dates on the calendar, requests to book and the host accepts", async ({ page, api, signIn }) => {
    const stay = await api.shortStay({ nightly: 55, cleaningFee: 0, minNights: 1 });
    const guest = await api.register("QA Guest", "TENANT");

    await signIn(page, guest);
    await page.goto(`/properties/${stay.id}`);
    await expect(page.getByText(stay.title).filter({ visible: true }).first()).toBeVisible();

    // Two months ahead keeps the test clear of "today" edge cases.
    const calendar = page.getByLabel("Availability calendar");
    await calendar.getByRole("button", { name: "Next month" }).click();
    await calendar.getByRole("button", { name: "Next month" }).click();
    const days = calendar.getByRole("button", { name: /^(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday),? \d+ \w+$/ });
    await days.nth(9).click();
    await days.nth(11).click();
    await expect(page.getByText("$55 × 2 nights")).toBeVisible();
    await expect(page.getByText("Total")).toBeVisible();

    await page.getByRole("button", { name: "Request to book" }).click();
    await expect(page.getByRole("status").filter({ hasText: "Request sent" })).toBeVisible();

    await page.goto("/trips");
    await expect(page.getByText(stay.title)).toBeVisible();
    await expect(page.getByText("Waiting for host")).toBeVisible();

    // Host side.
    await signIn(page, stay.host);
    await page.goto("/hosting");
    await expect(page.getByRole("tab", { name: "Requests (1)" })).toBeVisible();
    await page.getByRole("button", { name: "Accept" }).click();
    await page.getByRole("tab", { name: /Upcoming/ }).click();
    await expect(page.getByText("Awaiting payment")).toBeVisible();
    await page.getByRole("button", { name: "Mark as paid" }).click();
    await expect(page.getByText("Paid · Cash")).toBeVisible();
  });

  test("a signed-out visitor sees the booking button, never the owner's controls", async ({ page, api }) => {
    const stay = await api.shortStay({ nightly: 40, cleaningFee: 0, minNights: 1 });
    await page.goto(`/properties/${stay.id}`);
    await expect(page.getByText(stay.title).filter({ visible: true }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: "Select your dates" })).toBeVisible();
    await expect(page.getByRole("link", { name: "Edit Listing" })).toHaveCount(0);
    await expect(page.getByRole("link", { name: "Manage bookings & calendar" })).toHaveCount(0);

    const calendar = page.getByLabel("Availability calendar");
    await calendar.getByRole("button", { name: "Next month" }).click();
    const days = calendar.getByRole("button", { name: /^(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday),? \d+ \w+$/ });
    await days.nth(4).click();
    await days.nth(6).click();
    await page.getByRole("button", { name: "Sign in to book" }).click();
    await expect(page).toHaveURL(/\/login\?redirect=/);
  });

  test("stays search shows date and guest pickers @smoke", async ({ page }) => {
    await page.goto("/properties?listingType=SHORT_STAY");
    const toggle = page.locator('[aria-controls="search-controls"]');
    if (await toggle.isVisible()) await toggle.click();
    // Scoped to the search controls: a stay card in the results can also say "Check-in".
    const controls = page.locator("#search-controls");
    await expect(controls.getByText("Check-in", { exact: true }).first()).toBeVisible();
    await expect(controls.getByText("Guests", { exact: true }).first()).toBeVisible();
  });
});
