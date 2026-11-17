import { test, expect } from "../../src/fixtures";

test.describe("Access control", () => {
  test("outsiders and anonymous callers can't reach agency data", async ({ api, agency: a }) => {
    const paths = ["/members", "/listings", "/contacts", "/pipeline", "/enquiries", "/audit", "/trust-ledger"];
    for (const p of paths) {
      expect((await api.call("GET", `/orgs/${a.slug}${p}`)).status(), `anonymous ${p}`).toBe(401);
      expect((await api.call("GET", `/orgs/${a.slug}${p}`, { token: a.outsider.token })).status(), `outsider ${p}`).toBe(403);
    }
  });

  test("negotiators can't change workspace settings, read the audit log or write the trust ledger", async ({ api, agency: a }) => {
    const t = a.negotiator.token;
    expect((await api.call("PATCH", `/orgs/${a.slug}`, { token: t, data: { slogan: "x" } })).status()).toBe(403);
    expect((await api.call("GET", `/orgs/${a.slug}/audit`, { token: t })).status()).toBe(403);
    expect((await api.call("POST", `/orgs/${a.slug}/trust-ledger`, { token: t, data: { type: "DEPOSIT_RECEIVED", amountCents: 100, clientName: "x" } })).status()).toBe(403);
  });

  test("public profile hides internal fields; seller-report links can't be guessed", async ({ api, agency: a }) => {
    const pub = await api.ok("GET", `/orgs/${a.slug}`);
    expect(pub.trustAccountReference).toBeUndefined();
    expect(pub.saleCommissionBps).toBeUndefined();
    expect((await api.call("GET", "/shared/seller-reports/not-a-real-token")).status()).toBe(404);
  });

  test("a stranger can't add photos to someone else's listing", async ({ api, agency: a }) => {
    const id = await api.draftListing(a.slug, a.negotiator);
    const res = await api.call("POST", `/properties/${id}/photos`, {
      token: a.outsider.token,
      multipart: { files: { name: "x.jpg", mimeType: "image/jpeg", buffer: Buffer.from([0xff, 0xd8, 0xff]) } },
    });
    expect(res.status()).toBe(403);
  });
});
