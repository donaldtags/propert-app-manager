import { test, expect } from "../../src/fixtures";

test.describe("Publication gates", () => {
  test("an agency listing stays private until every gate is met, and only the PREA approves", async ({ api, agency: a }) => {
    const { listingId } = await api.readyForApproval(a.slug, a, { titleType: "CESSION" });

    // Draft: invisible to the public and to outsiders.
    expect((await api.call("GET", `/properties/${listingId}`)).status()).toBe(404);
    expect((await api.call("GET", `/properties/${listingId}`, { token: a.outsider.token })).status()).toBe(404);

    // Can't publish without PREA approval; can't bypass via the general endpoint.
    const blocked = await api.call("POST", `/orgs/${a.slug}/listings/${listingId}/publish`, { token: a.negotiator.token });
    expect(blocked.status()).toBe(409);
    expect(await blocked.text()).toContain("PREA approval");
    expect((await api.call("PATCH", `/properties/${listingId}`, { token: a.negotiator.token, data: { status: "AVAILABLE" } })).status()).toBe(409);

    // Only the PREA can approve: not the workspace admin, not a platform admin.
    for (const who of [a.orgAdmin, a.platformAdmin, a.negotiator]) {
      expect((await api.call("POST", `/orgs/${a.slug}/listings/${listingId}/approve`, { token: who.token })).status()).toBe(403);
    }
    await api.ok("POST", `/orgs/${a.slug}/listings/${listingId}/approve`, { token: a.prea.token });
    await api.ok("POST", `/orgs/${a.slug}/listings/${listingId}/publish`, { token: a.negotiator.token });

    const pub = await api.ok("GET", `/properties/${listingId}`);
    expect(pub.status).toBe("AVAILABLE");
    expect(pub.riskFlags).toContain("CESSION");
  });

  test("title can't be verified until the checklist for its title type is complete", async ({ api, agency: a }) => {
    const id = await api.draftListing(a.slug, a.negotiator, { titleType: "CESSION" });
    await api.ok("PUT", `/orgs/${a.slug}/listings/${id}/title`, { token: a.negotiator.token, data: { titleType: "CESSION", titleReference: "CES-1" } });
    const res = await api.call("POST", `/orgs/${a.slug}/listings/${id}/title-verification`, { token: a.prea.token, data: { status: "VERIFIED" } });
    expect(res.status()).toBe(409);
    expect(await res.text()).toContain("chain of cessions");
  });

  test("changing the price takes a live listing down until the PREA re-approves", async ({ api, agency: a }) => {
    const id = await api.publishedListing(a.slug, a);
    await api.ok("PATCH", `/properties/${id}`, { token: a.negotiator.token, data: { price: 199_000 } });
    const d = await api.ok("GET", `/orgs/${a.slug}/listings/${id}`, { token: a.negotiator.token });
    expect(d.property.status).toBe("DRAFT");
    expect(d.compliance.preaApprovedAt).toBeUndefined();
  });
});
