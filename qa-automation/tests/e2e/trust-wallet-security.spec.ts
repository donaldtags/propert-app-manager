import { createHmac } from "crypto";
import { test, expect } from "../../src/fixtures";
import { uniqueEmail, uniquePhone } from "../../src/api";
import { PASSWORD } from "../../src/env";

/** RFC 6238 code from a base32 secret - what an authenticator app shows. */
function totp(base32: string, at = Date.now()): string {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  let bits = "";
  for (const c of base32.replace(/=+$/, "").toUpperCase()) bits += alphabet.indexOf(c).toString(2).padStart(5, "0");
  const key = Buffer.from(bits.match(/.{8}/g)!.map((b) => parseInt(b, 2)));
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(Math.floor(at / 1000 / 30)));
  const h = createHmac("sha1", key).update(counter).digest();
  const o = h[h.length - 1] & 0x0f;
  const n = ((h[o] & 0x7f) << 24) | (h[o + 1] << 16) | (h[o + 2] << 8) | h[o + 3];
  return String(n % 1_000_000).padStart(6, "0");
}

const PDF = Buffer.from("%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF");

test.describe("Trust, agencies, wallet and sign-in security", () => {
  test("someone without an email signs up with just a phone number", async ({ page }) => {
    const phone = uniquePhone();
    await page.goto("/register");
    await page.getByLabel("Full Name").fill("Tendai Phone-Only");
    await page.locator("#reg-phone").fill(phone);
    await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
    await page.getByLabel("Confirm Password").fill(PASSWORD);
    await page.getByRole("button", { name: "Create Account" }).click();
    await expect(page).not.toHaveURL(/\/register/, { timeout: 20_000 });

    // ...and signs in again with the phone number.
    await page.evaluate(() => localStorage.clear());
    await page.goto("/login");
    await page.getByLabel("Email or phone number").fill(phone);
    await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
    await page.getByRole("button", { name: /sign in/i }).click();
    await expect(page).not.toHaveURL(/\/login/, { timeout: 20_000 });
  });

  test("two-step sign-in asks for the authenticator code after the password", async ({ page, api }) => {
    const email = uniqueEmail("twostep");
    const user = await api.register("Two Step", "TENANT", email);
    const setup = await api.ok<{ secret: string }>("POST", "/auth/2fa/totp/start", { token: user.token });
    await api.ok("POST", "/auth/2fa/totp/confirm", { token: user.token, data: { code: totp(setup.secret) } });

    await page.goto("/login");
    await page.getByLabel("Email or phone number").fill(email);
    await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
    await page.getByRole("button", { name: /sign in/i }).click();

    const code = page.getByLabel("6-digit code");
    await expect(code).toBeVisible();
    await code.fill("000000");
    await page.getByRole("button", { name: "Verify and sign in" }).click();
    await expect(page.getByRole("alert")).toBeVisible(); // wrong code refused
    const verify = page.getByRole("button", { name: "Verify and sign in" });
    await expect(verify).toHaveText("Verify and sign in"); // finished checking the wrong code
    // The current code was just used to switch two-step on, and used codes can't be replayed:
    // like a person waiting for the app to roll over, use the next one (accepted within ±30s).
    await code.fill(totp(setup.secret, Date.now() + 30_000));
    await expect(verify).toBeEnabled();
    await verify.click();
    await expect(page).not.toHaveURL(/\/login/, { timeout: 20_000 });
  });

  test("an agency registers itself and appears publicly once EstateX approves it", async ({ page, api, signIn }) => {
    const founder = await api.register("Agency Founder", "AGENT");
    const admin = await api.platformAdmin();
    const name = `Kopje Realty ${Date.now().toString(36)}`;

    await signIn(page, founder);
    await page.goto("/agencies/register");
    await page.getByLabel("Company name").fill(name);
    await page.getByLabel("Estate Agents Council registration number").fill("EAC-F-0042");
    await page.getByLabel("Office phone").fill("+263242700000");
    await page.getByRole("button", { name: "Register my agency" }).click();
    await expect(page.getByRole("heading", { name: `${name} is registered` })).toBeVisible();

    const pending = await api.ok<{ slug: string; name: string }[]>("GET", "/orgs/pending-review", { token: admin.token });
    const slug = pending.find((c) => c.name === name)!.slug;
    await page.goto(`/agencies/${slug}`);
    await expect(page.getByRole("heading", { name: "Agency not found" })).toBeVisible(); // not public yet

    await api.ok("PATCH", `/orgs/${slug}/approval`, { token: admin.token, data: { approved: true } });
    await page.goto("/agencies");
    await page.getByRole("link", { name: new RegExp(name) }).click();
    await expect(page.getByRole("heading", { name })).toBeVisible();
    await expect(page.getByText("Approved by EstateX")).toBeVisible();
  });

  test("an agent's listing goes live only after their licence is approved, then shows Licensed agent", async ({ page, api, signIn }) => {
    const agent = await api.register("Licence Agent", "AGENT");
    const admin = await api.platformAdmin();
    const listing = await api.ok<{ id: number; status: string }>("POST", "/properties", {
      token: agent.token,
      data: {
        title: `Agent flat ${Date.now()}`, description: "Two rooms", listingType: "RENT", city: "Harare", suburb: "Avondale",
        bedrooms: 2, bathrooms: 1, price: 650, currency: "USD", escrowRequired: false, landlordId: agent.id,
        photoUrls: ["https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?w=1200"],
      },
    });
    expect(listing.status).toBe("DRAFT");

    await signIn(page, agent);
    await page.goto("/profile");
    await page.getByLabel("Estate Agents Council registration number").fill("EAC/2026/0101");
    await page.getByLabel(/Registration certificate/).setInputFiles({ name: "certificate.pdf", mimeType: "application/pdf", buffer: PDF });
    await page.getByRole("button", { name: "Send for checking" }).click();
    await expect(page.getByText(/We're checking registration/)).toBeVisible();

    const pending = await api.ok<{ id: number; userId: number }[]>("GET", "/agents/licences/pending", { token: admin.token });
    await api.ok("PATCH", `/agents/licences/${pending.find((l) => l.userId === agent.id)!.id}`, { token: admin.token, data: { approved: true } });

    await page.evaluate(() => localStorage.clear());
    await page.goto(`/properties/${listing.id}`);
    await expect(page.getByText("Listed by an independent agent")).toBeVisible();
    await expect(page.getByText("Licensed agent (checked by EstateX)")).toBeVisible();
    await expect(page.getByText("Not yet verified").first()).toBeVisible();
  });

  test("a visitor reports a suspicious listing without an account", async ({ page, api }) => {
    const stay = await api.shortStay({ nightly: 30 });
    await page.goto(`/properties/${stay.id}`);
    await page.getByRole("button", { name: "Report this listing" }).click();
    await page.getByLabel("It looks fake or like a scam").check();
    await page.getByLabel(/Anything else we should know/).fill("They asked for money on WhatsApp before a viewing.");
    await page.getByRole("button", { name: "Send report" }).click();
    await expect(page.getByRole("status").filter({ hasText: "Our team will look at this listing" })).toBeVisible();
  });

  test("the wallet and the REIT fee are shown in plain numbers", async ({ page, api, signIn }) => {
    const investor = await api.register("Wallet Investor", "INVESTOR");
    await signIn(page, investor);
    await page.goto("/wallet");
    await expect(page.getByRole("heading", { name: "My wallet" })).toBeVisible();
    await expect(page.getByText("$0.00").first()).toBeVisible();

    await page.goto("/investments");
    await expect(page.getByText(/EstateX fee/).first()).toBeVisible();
  });
});
