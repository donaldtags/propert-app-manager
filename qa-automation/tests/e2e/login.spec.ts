import { test, expect } from "../../src/fixtures";
import { PASSWORD } from "../../src/env";

test("a user signs in through the login form and reaches their dashboard", async ({ page, api }) => {
  const user = await api.register("QA Login User", "TENANT");
  await page.goto("/login");
  await page.getByLabel("Email or phone number").fill(user.email);
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await page.getByRole("button", { name: /sign in|log in/i }).click();
  await expect(page).not.toHaveURL(/\/login/, { timeout: 15_000 });
  await expect(page.evaluate(() => localStorage.getItem("pn_token"))).resolves.toBeTruthy();
});

test("wrong password is rejected with a message", async ({ page, api }) => {
  const user = await api.register("QA Wrong Password", "TENANT");
  await page.goto("/login");
  await page.getByLabel("Email or phone number").fill(user.email);
  await page.getByLabel("Password", { exact: true }).fill("not-the-password");
  await page.getByRole("button", { name: /sign in|log in/i }).click();
  await expect(page.getByText(/invalid|incorrect|wrong/i)).toBeVisible();
  await expect(page).toHaveURL(/\/login/);
});
